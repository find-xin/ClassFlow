import Foundation
import Darwin

/// Only categorical diagnostics enter UI state; raw client output is never logged or stored.
enum TunnelIssue: String, Sendable {
    case authorization, throttled, network, incompatible
    static func classify(_ text: String) -> TunnelIssue? {
        let value = text.lowercased()
        if value.contains("401") || value.contains("403") || value.contains("unauthorized") || value.contains("forbidden") { return .authorization }
        if value.contains("429") || value.contains("rate limit") { return .throttled }
        if value.contains("unknown flag") || value.contains("unknown command") { return .incompatible }
        if value.contains("connection refused") || value.contains("no such host") || value.contains("network is unreachable") { return .network }
        return nil
    }
    var message: String {
        switch self {
        case .authorization: return "运行密钥或隧道访问权限未通过验证，请检查密钥及 Tunnels Read + Use 权限。"
        case .throttled: return "隧道服务暂时限流，客户端会重试。"
        case .network: return "网络连接暂不可用，客户端正在尝试重连。"
        case .incompatible: return "客户端版本不兼容，请更新官方 tunnel-client。"
        }
    }
}

@MainActor
protocol TunnelChildProcess: AnyObject {
    var isRunning: Bool { get }
    func launch(_ command: TunnelCommand, onExit: @escaping @Sendable (Int32) -> Void,
                onIssue: @escaping @Sendable (TunnelIssue) -> Void) throws
    func terminate()
    func forceKill()
}

@MainActor
final class FoundationTunnelChild: TunnelChildProcess {
    private let process = Process()
    private let output = Pipe()
    var isRunning: Bool { process.isRunning }

    func launch(_ command: TunnelCommand, onExit: @escaping @Sendable (Int32) -> Void,
                onIssue: @escaping @Sendable (TunnelIssue) -> Void) throws {
        process.executableURL = command.executable
        process.arguments = command.arguments
        process.environment = command.environment
        process.standardInput = FileHandle.nullDevice
        process.standardOutput = output
        process.standardError = output
        output.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            if data.isEmpty { handle.readabilityHandler = nil; return }
            if let text = String(data: data, encoding: .utf8), let issue = TunnelIssue.classify(text) { onIssue(issue) }
        }
        process.terminationHandler = { process in onExit(process.terminationStatus) }
        do { try process.run() }
        catch {
            output.fileHandleForReading.readabilityHandler = nil
            throw TunnelError.launchFailed
        }
    }
    func terminate() { if process.isRunning { process.terminate() } }
    func forceKill() { if process.isRunning { Darwin.kill(process.processIdentifier, SIGKILL) } }
}

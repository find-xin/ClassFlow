import Foundation
import AppKit
import Darwin

/// A private, same-user command mailbox. The app is the executor; MCP never races the UI
/// to overwrite the database. Responses acknowledge completion, not merely notification delivery.
enum MCPDesktopBridge {
    static let notification = NSNotification.Name("org.classflow.automationRequest")
    struct Request: Codable {
        let id: UUID
        let tool: String
        let arguments: Data
        let expiresAt: Date
    }
    struct Response: Codable {
        let result: String?
        let error: String?
    }
    static var directory: URL {
        LocalDatabase().fileURL.deletingLastPathComponent().appendingPathComponent("automation", isDirectory: true)
    }

    static func hasLiveExecutor(in root: URL) -> Bool {
        guard let text = try? String(contentsOf: root.appendingPathComponent("executor.pid"), encoding: .utf8),
              let pid = Int32(text.trimmingCharacters(in: .whitespacesAndNewlines)), pid > 0 else { return false }
        return Darwin.kill(pid, 0) == 0
    }

    static func call(tool: String, arguments: [String: Any], timeout: TimeInterval = 90) throws -> String {
        let root = directory
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true,
                                                attributes: [.posixPermissions: 0o700])
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: root.path)
        let id = UUID()
        let requestURL = root.appendingPathComponent("\(id).request.json")
        let responseURL = root.appendingPathComponent("\(id).response.json")
        let request = Request(id: id, tool: tool, arguments: try JSONSerialization.data(withJSONObject: arguments),
                              expiresAt: Date().addingTimeInterval(timeout))
        defer {
            try? FileManager.default.removeItem(at: requestURL)
            try? FileManager.default.removeItem(at: responseURL)
        }
        try JSONEncoder().encode(request).write(to: requestURL, options: .atomic)
        // Serialize cold starts across multiple stdio clients.
        let lock = Darwin.open(root.appendingPathComponent("launch.lock").path, O_CREAT | O_RDWR, 0o600)
        guard lock >= 0 else { throw NSError(domain: "ClassFlowBridge", code: 4) }
        defer { Darwin.close(lock) }
        guard flock(lock, LOCK_EX) == 0 else { throw NSError(domain: "ClassFlowBridge", code: 5) }
        defer { flock(lock, LOCK_UN) }

        // Select the containing .app explicitly: mounted DMGs can have the same bundle ID.
        let executable = URL(fileURLWithPath: CommandLine.arguments[0]).resolvingSymlinksInPath()
        let containingBundle = executable.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let appURL = containingBundle.pathExtension == "app" ? containingBundle : URL(fileURLWithPath: "/Applications/ClassFlow.app")
        if !hasLiveExecutor(in: root) {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
            // A stdio process inside the bundle must not be mistaken for the GUI executor.
            process.arguments = ["-n", "-g", appURL.path]
            process.standardOutput = FileHandle.nullDevice
            process.standardError = FileHandle.nullDevice
            try process.run()
            process.waitUntilExit()
            guard process.terminationStatus == 0 else {
                throw NSError(domain: "ClassFlowBridge", code: 1,
                              userInfo: [NSLocalizedDescriptionKey: "无法启动 ClassFlow，请先安装并打开最新版本。"])
            }
            let startupDeadline = min(request.expiresAt, Date().addingTimeInterval(15))
            while !hasLiveExecutor(in: root), Date() < startupDeadline { Thread.sleep(forTimeInterval: 0.05) }
            guard hasLiveExecutor(in: root) else {
                throw NSError(domain: "ClassFlowBridge", code: 6,
                              userInfo: [NSLocalizedDescriptionKey: "App 执行器尚未就绪，请检查启动状态或系统授权提示。"])
            }
        }
        flock(lock, LOCK_UN)
        DistributedNotificationCenter.default().postNotificationName(notification, object: nil, userInfo: nil, deliverImmediately: true)
        while Date() < request.expiresAt {
            if let data = try? Data(contentsOf: responseURL) {
                let response = try JSONDecoder().decode(Response.self, from: data)
                if let error = response.error {
                    throw NSError(domain: "ClassFlowBridge", code: 2, userInfo: [NSLocalizedDescriptionKey: error])
                }
                return response.result ?? ""
            }
            Thread.sleep(forTimeInterval: 0.05)
        }
        throw NSError(domain: "ClassFlowBridge", code: 3, userInfo: [NSLocalizedDescriptionKey:
            "ClassFlow 未在时限内确认执行结果。请检查 App 版本和运行状态；先查询状态，不要自动重试写操作。"])
    }
}

@MainActor
final class MCPDesktopCommandReceiver {
    private weak var app: AppState?
    private let directory: URL
    private var observer: NSObjectProtocol?
    private var timer: Timer?
    private var inFlight: Set<UUID> = []
    private var isExecuting = false

    init(app: AppState, directory: URL = MCPDesktopBridge.directory) {
        self.app = app
        self.directory = directory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
                                                attributes: [.posixPermissions: 0o700])
        try? String(ProcessInfo.processInfo.processIdentifier).write(to: directory.appendingPathComponent("executor.pid"), atomically: true, encoding: .utf8)
        observer = DistributedNotificationCenter.default().addObserver(forName: MCPDesktopBridge.notification,
            object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.drain() }
            }
        // Also handle cold launch, missed notifications, and requests arriving during a sync.
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.drain() }
        }
        drain()
    }

    func stop() {
        timer?.invalidate()
        if let observer { DistributedNotificationCenter.default().removeObserver(observer) }
    }

    func drain() {
        guard !isExecuting, let app else { return }
        let root = directory
        guard let files = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) else { return }
        for url in files.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) where url.lastPathComponent.hasSuffix(".request.json") {
            guard let data = try? Data(contentsOf: url),
                  let request = try? JSONDecoder().decode(MCPDesktopBridge.Request.self, from: data),
                  url.lastPathComponent == "\(request.id).request.json",
                  !inFlight.contains(request.id) else { continue }
            let responseURL = root.appendingPathComponent("\(request.id).response.json")
            if request.expiresAt < Date() {
                try? FileManager.default.removeItem(at: url)
                continue
            }
            if FileManager.default.fileExists(atPath: responseURL.path) { continue }
            inFlight.insert(request.id)
            isExecuting = true
            Task { @MainActor [weak self] in
                let response: MCPDesktopBridge.Response
                do {
                    let arguments = try JSONSerialization.jsonObject(with: request.arguments) as? [String: Any] ?? [:]
                    let output = try await app.executeAutomationTool(name: request.tool, arguments: arguments)
                    response = .init(result: output, error: nil)
                } catch {
                    response = .init(result: nil, error: error.localizedDescription)
                }
                if let data = try? JSONEncoder().encode(response) { try? data.write(to: responseURL, options: .atomic) }
                self?.inFlight.remove(request.id)
                self?.isExecuting = false
            }
            break
        }
    }
}

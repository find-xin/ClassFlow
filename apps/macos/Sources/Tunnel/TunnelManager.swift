import Foundation
import Observation
import Darwin

@MainActor
@Observable
final class TunnelManager {
    static let shared = TunnelManager()

    enum State: Equatable {
        case stopped, starting, connecting, ready, stopping, failed
        var isActive: Bool { [.starting, .connecting, .ready, .stopping].contains(self) }
        var label: String {
            switch self {
            case .stopped: return "App 管理的连接已停止"
            case .starting: return "正在准备连接"
            case .connecting: return "正在连接隧道"
            case .ready: return "隧道已就绪"
            case .stopping: return "正在停止连接"
            case .failed: return "连接未成功"
            }
        }
    }

    private(set) var configuration: TunnelConfiguration
    private(set) var state: State = .stopped
    private(set) var message: String = ""
    private(set) var hasSavedKey: Bool

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let secrets: any TunnelSecretStore
    @ObservationIgnored private let root: URL
    @ObservationIgnored private let mcpExecutable: URL
    @ObservationIgnored private let makeProcess: @MainActor () -> any TunnelChildProcess
    @ObservationIgnored private let readiness: @Sendable (URL) async -> Bool
    @ObservationIgnored private let pollInterval: Duration
    @ObservationIgnored private var preparation: (any TunnelChildProcess)?
    @ObservationIgnored private var runtime: (any TunnelChildProcess)?
    @ObservationIgnored private var startTask: Task<Void, Never>?
    @ObservationIgnored private var monitorTask: Task<Void, Never>?
    @ObservationIgnored private var stopTask: Task<Void, Never>?
    @ObservationIgnored private var generation = UUID()
    @ObservationIgnored private var healthFile: URL?
    @ObservationIgnored private var lock: Int32 = -1
    @ObservationIgnored private var handledLaunch = false

    init(defaults: UserDefaults = CloudSettings.defaults,
         secrets: any TunnelSecretStore = KeychainTunnelSecrets(),
         root: URL? = nil, mcpExecutable: URL? = nil,
         existingProfile: URL? = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".config/tunnel-client/classflow.yaml"),
         makeProcess: @escaping @MainActor () -> any TunnelChildProcess = { FoundationTunnelChild() },
         readiness: @escaping @Sendable (URL) async -> Bool = { url in await TunnelManager.probe(url) },
         pollInterval: Duration = .seconds(2)) {
        self.defaults = defaults
        self.secrets = secrets
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ClassFlow/Tunnel", isDirectory: true)
        self.mcpExecutable = mcpExecutable ?? (Bundle.main.bundleURL.pathExtension == "app" ? Bundle.main.executableURL : nil)
            ?? URL(fileURLWithPath: "/Applications/ClassFlow.app/Contents/MacOS/ClassFlow")
        self.makeProcess = makeProcess
        self.readiness = readiness
        self.pollInterval = pollInterval
        self.configuration = TunnelConfiguration(
            tunnelID: defaults.string(forKey: "chatgpt_tunnel_id") ?? existingProfile.flatMap { TunnelConfiguration.existingTunnelID(profile: $0) } ?? "",
            clientPath: defaults.string(forKey: "chatgpt_tunnel_client_path") ?? TunnelConfiguration.detectedClientPath(),
            startWithApp: defaults.bool(forKey: "chatgpt_tunnel_start_with_app"))
        self.hasSavedKey = defaults.bool(forKey: "chatgpt_tunnel_key_saved")
        // Never read Keychain or start a process while constructing the app's initial view state.
    }

    @discardableResult
    func save(tunnelID: String, clientPath: String, newKey: String) -> Bool {
        guard !state.isActive else { message = "请先停止连接，再修改连接配置。"; return false }
        var next = configuration
        next.tunnelID = tunnelID.trimmingCharacters(in: .whitespacesAndNewlines)
        next.clientPath = clientPath.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            try next.validate(requireConnection: false)
            if !newKey.isEmpty {
                let key = newKey.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !key.isEmpty, !key.contains("\n"), !key.contains("\r"), secrets.save(key) else { throw TunnelError.saveKeyFailed }
                hasSavedKey = true
                defaults.set(true, forKey: "chatgpt_tunnel_key_saved")
            }
            configuration = next
            persist()
            message = "配置已保存。"
            return true
        } catch { message = error.localizedDescription; return false }
    }

    func setStartWithApp(_ enabled: Bool) {
        configuration.startWithApp = enabled
        persist()
    }

    func forgetKey() {
        guard !state.isActive else { return }
        guard secrets.delete() else { message = TunnelError.removeKeyFailed.localizedDescription; return }
        hasSavedKey = false
        defaults.set(false, forKey: "chatgpt_tunnel_key_saved")
        message = "已移除本机保存的运行密钥。"
    }

    func startOnAppLaunch() {
        guard !handledLaunch else { return }
        handledLaunch = true
        if configuration.startWithApp { start() }
    }

    func start() {
        guard !state.isActive else { return }
        do {
            try configuration.validate(requireConnection: true)
            guard let key = secrets.read(), !key.isEmpty else {
                hasSavedKey = false
                defaults.set(false, forKey: "chatgpt_tunnel_key_saved")
                throw TunnelError.missingKey
            }
            let token = UUID()
            let plan = try TunnelLaunchPlan(configuration: configuration, root: root, mcpExecutable: mcpExecutable, key: key, runID: token)
            try acquireLock()
            generation = token
            healthFile = plan.healthFile
            state = .starting
            message = ""
            startTask = Task { [weak self] in
                guard let self else { return }
                do {
                    let code = try await self.prepare(plan.preparation, token: token)
                    guard self.generation == token, !Task.isCancelled else { return }
                    self.preparation = nil
                    guard code == 0 else { throw TunnelError.preparationFailed }
                    let child = self.makeProcess()
                    self.runtime = child
                    try child.launch(plan.runtime, onExit: { [weak self] code in
                        Task { @MainActor in self?.runtimeExited(code, token: token) }
                    }, onIssue: { [weak self] issue in
                        Task { @MainActor in self?.received(issue, token: token) }
                    })
                    guard self.generation == token else { child.terminate(); return }
                    self.state = .connecting
                    self.monitorTask = Task { [weak self] in await self?.monitor(token: token) }
                } catch {
                    guard self.generation == token else { return }
                    self.finishFailure(error.localizedDescription)
                }
            }
        } catch { finishFailure(error.localizedDescription) }
    }

    func stop() {
        guard state.isActive else { return }
        generation = UUID()
        startTask?.cancel()
        monitorTask?.cancel()
        state = .stopping
        preparation?.terminate()
        runtime?.terminate()
        stopTask = Task { [weak self] in
            guard let self else { return }
            for _ in 0..<30 {
                if self.preparation?.isRunning != true && self.runtime?.isRunning != true { break }
                try? await Task.sleep(for: .milliseconds(100))
            }
            self.preparation?.forceKill()
            self.runtime?.forceKill()
            self.cleanup()
            self.state = .stopped
            self.message = "已停止由 ClassFlow 启动的隧道。"
        }
    }

    /// Bounded synchronous cleanup is used only during application termination.
    func shutdown() {
        generation = UUID()
        startTask?.cancel()
        monitorTask?.cancel()
        stopTask?.cancel()
        preparation?.terminate()
        runtime?.terminate()
        let deadline = Date().addingTimeInterval(1)
        while Date() < deadline && (preparation?.isRunning == true || runtime?.isRunning == true) { Thread.sleep(forTimeInterval: 0.02) }
        preparation?.forceKill()
        runtime?.forceKill()
        cleanup()
        state = .stopped
    }

    private func prepare(_ command: TunnelCommand, token: UUID) async throws -> Int32 {
        let child = makeProcess()
        preparation = child
        return try await withCheckedThrowingContinuation { continuation in
            do {
                try child.launch(command, onExit: { code in continuation.resume(returning: code) }, onIssue: { [weak self] issue in
                    Task { @MainActor in self?.received(issue, token: token) }
                })
            } catch { continuation.resume(throwing: error) }
        }
    }

    private func monitor(token: UUID) async {
        while !Task.isCancelled && generation == token && runtime?.isRunning == true {
            var ready = false
            if let healthFile, let text = try? String(contentsOf: healthFile, encoding: .utf8),
               let url = TunnelLaunchPlan.healthBaseURL(text) { ready = await readiness(url) }
            guard !Task.isCancelled, generation == token else { return }
            if ready { state = .ready; message = "客户端已通过就绪检查，可以在 ChatGPT 中调用 ClassFlow。" }
            else {
                state = .connecting
                if message.isEmpty { message = "正在等待客户端就绪；请确认网络及隧道权限。" }
            }
            try? await Task.sleep(for: pollInterval)
        }
    }

    private func received(_ issue: TunnelIssue, token: UUID) {
        guard generation == token else { return }
        message = issue.message
    }

    private func runtimeExited(_ code: Int32, token: UUID) {
        guard generation == token else { return }
        monitorTask?.cancel()
        runtime = nil
        let details = message
        cleanup()
        state = .failed
        message = details.isEmpty || details.contains("就绪") ? "隧道进程已退出（状态 \(code)），请检查配置后重新连接。" : details
    }

    private func finishFailure(_ details: String) {
        preparation?.terminate()
        runtime?.terminate()
        cleanup()
        state = .failed
        message = details
    }

    private func acquireLock() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: root.path)
        let descriptor = Darwin.open(root.appendingPathComponent("manager.lock").path, O_CREAT | O_RDWR | O_CLOEXEC, 0o600)
        guard descriptor >= 0 else { throw TunnelError.launchFailed }
        guard flock(descriptor, LOCK_EX | LOCK_NB) == 0 else { Darwin.close(descriptor); throw TunnelError.alreadyManaged }
        lock = descriptor
    }

    private func cleanup() {
        preparation = nil
        runtime = nil
        if let healthFile { try? FileManager.default.removeItem(at: healthFile) }
        healthFile = nil
        if lock >= 0 { flock(lock, LOCK_UN); Darwin.close(lock); lock = -1 }
    }

    private func persist() {
        defaults.set(configuration.tunnelID, forKey: "chatgpt_tunnel_id")
        defaults.set(configuration.clientPath, forKey: "chatgpt_tunnel_client_path")
        defaults.set(configuration.startWithApp, forKey: "chatgpt_tunnel_start_with_app")
    }

    nonisolated static func probe(_ baseURL: URL) async -> Bool {
        guard TunnelLaunchPlan.healthBaseURL(baseURL.absoluteString) != nil else { return false }
        let session = URLSession(configuration: .ephemeral)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: baseURL.appendingPathComponent("readyz"))
        request.timeoutInterval = 2
        do {
            let (data, response) = try await session.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200 && String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) == "ready"
        } catch { return false }
    }
}

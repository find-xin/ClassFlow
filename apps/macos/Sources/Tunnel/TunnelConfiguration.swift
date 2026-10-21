import Foundation

/// Device-local connection preferences; deliberately excluded from DatabasePayload and backups.
struct TunnelConfiguration: Equatable, Sendable {
    var tunnelID: String = ""
    var clientPath: String = ""
    var startWithApp: Bool = false

    func validate(requireConnection: Bool) throws {
        if requireConnection || !tunnelID.isEmpty {
            guard tunnelID.range(of: #"^tunnel_[a-z0-9]{32}$"#, options: .regularExpression) != nil,
                  tunnelID != "tunnel_id" else {
                throw TunnelError.invalidTunnelID
            }
        }
    }

    static func existingTunnelID(profile: URL) -> String? {
        guard let text = try? String(contentsOf: profile, encoding: .utf8),
              let regex = try? NSRegularExpression(pattern: #"(?m)^\s*tunnel_id:\s*["']?(tunnel_[a-z0-9]{32})["']?\s*$"#),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        let id = String(text[range])
        guard (try? TunnelConfiguration(tunnelID: id).validate(requireConnection: true)) != nil else { return nil }
        return id
    }

    static func detectedClientPath() -> String {
        let home = FileManager.default.homeDirectoryForCurrentUser
        let candidates = [
            home.appendingPathComponent(".local/bin/tunnel-client").path,
            "/opt/homebrew/bin/tunnel-client", "/usr/local/bin/tunnel-client"
        ]
        return candidates.first { FileManager.default.isExecutableFile(atPath: $0) } ?? candidates[0]
    }
}

enum TunnelError: LocalizedError {
    case invalidTunnelID, missingClient, missingKey, saveKeyFailed, removeKeyFailed, alreadyManaged, preparationFailed, launchFailed

    var errorDescription: String? {
        switch self {
        case .invalidTunnelID: return "请输入有效的 Tunnel ID（tunnel_ 后接 32 位小写字母或数字）。"
        case .missingClient: return "未找到可执行的 tunnel-client，请在高级选项中选择已安装的官方客户端。"
        case .missingKey: return "尚未保存运行密钥，或钥匙串访问未获允许。请在设置中保存密钥后再连接。"
        case .saveKeyFailed: return "运行密钥未能保存到钥匙串，原有配置未更改。"
        case .removeKeyFailed: return "无法从钥匙串移除运行密钥。"
        case .alreadyManaged: return "另一个 ClassFlow 进程正在管理隧道，请先退出另一个 App 副本。"
        case .preparationFailed: return "客户端配置失败，请确认 tunnel-client 版本支持 init 和指定的配置目录。"
        case .launchFailed: return "隧道客户端未能启动，请检查客户端路径及文件权限。"
        }
    }
}

@MainActor
protocol TunnelSecretStore {
    func read() -> String?
    func save(_ value: String) -> Bool
    func delete() -> Bool
}

struct KeychainTunnelSecrets: TunnelSecretStore {
    private let keychain = KeychainService()
    private let account = "openai_tunnel_runtime_key"
    func read() -> String? { keychain.readSecret(account: account) }
    func save(_ value: String) -> Bool { keychain.saveSecret(account: account, secret: value) }
    func delete() -> Bool { keychain.deleteSecret(account: account) }
}

struct TunnelCommand: Sendable {
    let executable: URL
    let arguments: [String]
    let environment: [String: String]
}

/// Uses only supported CLI flags. Runtime credentials occur only in the child's environment.
struct TunnelLaunchPlan: Sendable {
    let preparation: TunnelCommand
    let runtime: TunnelCommand
    let healthFile: URL

    init(configuration: TunnelConfiguration, root: URL, mcpExecutable: URL, key: String,
         inheritedEnvironment: [String: String] = ProcessInfo.processInfo.environment, runID: UUID = UUID()) throws {
        try configuration.validate(requireConnection: true)
        guard FileManager.default.isExecutableFile(atPath: configuration.clientPath),
              FileManager.default.isExecutableFile(atPath: mcpExecutable.path) else { throw TunnelError.missingClient }
        guard !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw TunnelError.missingKey }
        let allowed: Set<String> = ["HOME", "PATH", "TMPDIR", "LANG", "LC_CTYPE", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "SSL_CERT_FILE", "SSL_CERT_DIR"]
        var environment = inheritedEnvironment.filter { allowed.contains($0.key) }
        environment["OPEN_WEB_UI"] = "false"
        environment["ALLOW_REMOTE_UI"] = "false"
        environment["LOG_HTTP_RAW_UNSAFE"] = "false"
        let executable = URL(fileURLWithPath: configuration.clientPath)
        let profiles = root.appendingPathComponent("profiles")
        healthFile = root.appendingPathComponent("health-\(runID.uuidString).url")
        func quote(_ value: String) -> String { "'" + value.replacingOccurrences(of: "'", with: "'\\''") + "'" }
        preparation = TunnelCommand(executable: executable, arguments: [
            "init", "--sample", "sample_mcp_stdio_local", "--profile", "classflow-app",
            "--profile-dir", profiles.path, "--tunnel-id", configuration.tunnelID,
            "--mcp-command", quote(mcpExecutable.path) + " --mcp", "--health-listen-addr", "127.0.0.1:0", "--force"
        ], environment: environment)
        environment["CONTROL_PLANE_API_KEY"] = key
        runtime = TunnelCommand(executable: executable, arguments: [
            "run", "--profile", "classflow-app", "--profile-dir", profiles.path,
            "--health.url-file", healthFile.path
        ], environment: environment)
    }

    static func healthBaseURL(_ value: String) -> URL? {
        guard let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme == "http", components.host == "127.0.0.1",
              let port = components.port, (1...65535).contains(port),
              components.user == nil, components.password == nil, components.query == nil,
              components.fragment == nil, ["", "/"].contains(components.path) else { return nil }
        return url
    }
}

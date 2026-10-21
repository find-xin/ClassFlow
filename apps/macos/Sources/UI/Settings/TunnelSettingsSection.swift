import SwiftUI
import AppKit

struct TunnelSettingsSection: View {
    @Bindable private var manager: TunnelManager = .shared
    @State private var tunnelID = ""
    @State private var clientPath = ""
    @State private var runtimeKey = ""

    var body: some View {
        Section("ChatGPT 聊天接管") {
            VStack(alignment: .leading, spacing: 12) {
                TextField("Tunnel ID", text: $tunnelID, prompt: Text("tunnel_…"))
                    .textFieldStyle(.roundedBorder)
                    .disabled(manager.state.isActive)
                SecureField("运行密钥", text: $runtimeKey,
                            prompt: Text(manager.hasSavedKey ? "已保存；留空保留原密钥" : "粘贴 OpenAI 隧道运行密钥"))
                    .textFieldStyle(.roundedBorder)
                    .privacySensitive()
                    .disabled(manager.state.isActive)
                HStack {
                    Button("保存连接配置") { save() }
                        .disabled(manager.state.isActive)
                    if manager.hasSavedKey {
                        Label("密钥已保存到钥匙串", systemImage: "lock.fill")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Button("移除密钥") { manager.forgetKey() }
                            .disabled(manager.state.isActive)
                    }
                }
                Toggle("打开 ClassFlow 时自动启用", isOn: Binding(
                    get: { manager.configuration.startWithApp },
                    set: { manager.setStartWithApp($0) }))
                HStack {
                    Circle().fill(statusColor).frame(width: 8, height: 8)
                    Text(manager.state.label).font(.callout)
                    Spacer()
                    if manager.state.isActive {
                        Button("停止连接") { manager.stop() }
                            .disabled(manager.state == .stopping)
                    } else {
                        Button("连接 ChatGPT") { if save() { manager.start() } }
                            .buttonStyle(.borderedProminent)
                    }
                }
                if !manager.message.isEmpty {
                    Text(manager.message)
                        .font(.caption)
                        .foregroundColor(manager.state == .failed ? .red : .secondary)
                        .textSelection(.enabled)
                }
                Text("连接在后台运行，无需终端窗口。退出 ClassFlow 时停止；Mac 需保持联网。首次切换时，请在原终端按 Ctrl+C 停止旧连接。")
                    .font(.caption)
                    .foregroundColor(.secondary)
                DisclosureGroup("高级选项") {
                    HStack {
                        TextField("客户端路径", text: $clientPath)
                            .textFieldStyle(.roundedBorder)
                            .disabled(manager.state.isActive)
                        Button("选择…") { selectClient() }
                            .disabled(manager.state.isActive)
                    }
                    Text("使用已安装的官方 tunnel-client；密钥不会写入配置文件或同步数据。")
                        .font(.caption).foregroundColor(.secondary)
                    Link("下载官方客户端", destination: URL(string: "https://github.com/openai/tunnel-client/releases/latest")!)
                }
            }
            .padding(.vertical, 4)
        }
        .onAppear {
            tunnelID = manager.configuration.tunnelID
            clientPath = manager.configuration.clientPath
        }
        .onDisappear { runtimeKey = "" }
    }

    private var statusColor: Color {
        switch manager.state {
        case .ready: return .green
        case .failed: return .red
        case .starting, .connecting, .stopping: return .orange
        case .stopped: return .secondary
        }
    }

    @discardableResult
    private func save() -> Bool {
        guard manager.save(tunnelID: tunnelID, clientPath: clientPath, newKey: runtimeKey) else { return false }
        runtimeKey = ""
        return true
    }

    private func selectClient() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.message = "选择官方 tunnel-client 可执行文件"
        if panel.runModal() == .OK, let url = panel.url { clientPath = url.path }
    }
}

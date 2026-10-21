import SwiftUI
import AppKit
import ClassFlowCore

/// Settings tab for ChatGPT and AI assistant integration via Model Context Protocol (MCP).
/// Provides one-click configuration for ChatGPT Desktop, Claude Desktop, and CLI links.
public struct AISettingsTab: View {
    @Bindable public var appState: AppState

    @State private var copyStatusMessage: String? = nil
    @State private var installStatusMessage: String? = nil
    @State private var isInstalled: Bool = false

    public init(appState: AppState) {
        self.appState = appState
    }

    private var binaryPath: String {
        // Check if inside .app bundle
        let bundlePath = Bundle.main.bundlePath
        if bundlePath.hasSuffix(".app") {
            let mcpPath = (bundlePath as NSString).appendingPathComponent("Contents/MacOS/classflow-mcp")
            if FileManager.default.fileExists(atPath: mcpPath) {
                return mcpPath
            }
            let execPath = (bundlePath as NSString).appendingPathComponent("Contents/MacOS/ClassFlow")
            if FileManager.default.fileExists(atPath: execPath) {
                return execPath
            }
        }
        return "/Applications/ClassFlow.app/Contents/MacOS/classflow-mcp"
    }

    private var chatgptConfigJSON: String {
        """
        {
          "mcpServers": {
            "classflow": {
              "command": "\(binaryPath)",
              "args": []
            }
          }
        }
        """
    }

    private var claudeConfigJSON: String {
        """
        {
          "mcpServers": {
            "classflow": {
              "command": "\(binaryPath)",
              "args": []
            }
          }
        }
        """
    }

    public var body: some View {
        Form {
            // MARK: - Overview & Status
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 10) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 24))
                            .foregroundStyle(LinearGradient(colors: [Color(hex: "#FF5A5F"), .purple], startPoint: .topLeading, endPoint: .bottomTrailing))

                        VStack(alignment: .leading, spacing: 2) {
                            Text("ChatGPT / AI 智能助理接管")
                                .font(.headline)
                            Text("通过 Model Context Protocol (MCP) 原生协议，让 ChatGPT 聊天模式与本地桌面端全权接管 ClassFlow。")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }

                    HStack(spacing: 8) {
                        Label("原生 Swift 编译", systemImage: "bolt.fill")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.green.opacity(0.15))
                            .foregroundColor(.green)
                            .clipShape(Capsule())

                        Label("零额外依赖 (无需 Python/Node)", systemImage: "checkmark.shield.fill")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.blue.opacity(0.15))
                            .foregroundColor(.blue)
                            .clipShape(Capsule())

                        Label("毫秒级双向实时同步", systemImage: "arrow.triangle.2.circlepath")
                            .font(.caption2)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.orange.opacity(0.15))
                            .foregroundColor(.orange)
                            .clipShape(Capsule())
                    }
                    .padding(.top, 4)
                }
                .padding(.vertical, 4)
            }

            // MARK: - One-click Setup
            Section("客户端一键接入") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("MCP 服务执行文件路径")
                                .font(.subheadline)
                                .fontWeight(.medium)
                            Text(binaryPath)
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundColor(.secondary)
                                .textSelection(.enabled)
                        }
                        Spacer()
                    }

                    Divider()

                    HStack(spacing: 12) {
                        Button {
                            copyToClipboard(chatgptConfigJSON, title: "ChatGPT 配置已复制")
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "doc.on.doc")
                                Text("复制 ChatGPT 配置")
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color(hex: "#10A37F")) // OpenAI green

                        Button {
                            copyToClipboard(claudeConfigJSON, title: "Claude 配置已复制")
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "doc.on.doc")
                                Text("复制 Claude 配置")
                            }
                        }
                        .buttonStyle(.bordered)

                        Button {
                            autoInstallConfigs()
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.down.circle")
                                Text("一键写入本机配置")
                            }
                        }
                        .buttonStyle(.bordered)
                    }

                    if let msg = copyStatusMessage {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text(msg)
                                .font(.caption)
                                .foregroundColor(.green)
                        }
                    }

                    if let instMsg = installStatusMessage {
                        HStack(spacing: 6) {
                            Image(systemName: "info.circle.fill")
                                .foregroundColor(.primary)
                            Text(instMsg)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            // MARK: - Supported Commands
            Section("支持的所有 19 项操作 (ChatGPT 可全权执行)") {
                VStack(alignment: .leading, spacing: 8) {
                    Group {
                        featureRow(icon: "calendar", title: "课表管理", desc: "查询今日/指定周课程、添加课程(支持学分/节次/地点/老师/颜色)、修改课程、删除课程、列出所有课程")
                        featureRow(icon: "clock.badge.checkmark", title: "日程规划", desc: "添加事件、删除事件、列出日程提醒、按周次与学期过滤")
                        featureRow(icon: "checklist", title: "待办事项 (番茄ToDo)", desc: "列出待办、创建待办(优先级/预估番茄/关联课程)、完成待办、删除待办")
                        featureRow(icon: "timer", title: "专注时钟", desc: "启动专注倒计时(自动激活UI专注模式)、停止专注并记录时长、查看专注统计报告")
                        featureRow(icon: "arrow.triangle.2.circlepath", title: "云端同步 & 导航", desc: "触发 BYOC 云端同步、切换学期与周次、唤起/定位 ClassFlow 界面")
                    }
                }
                .padding(.vertical, 4)
            }

            // MARK: - Example Prompts
            Section("常用自然语言指令示例 (在 ChatGPT 聊天模式中直接说)") {
                VStack(alignment: .leading, spacing: 6) {
                    promptRow("今天下午有什么课？教室在哪里？")
                    promptRow("帮我添加一门「机器学习」课，周三 3-4 节，理科楼 201，张教授，3 学分，青绿色")
                    promptRow("下周五下午两点有个学院学术研讨会，帮我记到日程里")
                    promptRow("帮我建一个待办任务：完成操作系统实验，高优先级，预计 2 个番茄钟")
                    promptRow("开始 25 分钟的专注计时，任务是完成操作系统实验")
                    promptRow("把这学期所有课程和待办数据同步到云端")
                    promptRow("这周有哪些待办事项还没完成？")
                    promptRow("把周四下午的计算机网络课删掉")
                }
                .padding(.vertical, 4)
            }

            // MARK: - Web ChatGPT / Custom Actions
            Section("Web 端 ChatGPT 自定义 GPTs (OpenAPI 规范)") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("如需在网页端 ChatGPT 或自定义 GPTs 中通过 Actions 调用本地 ClassFlow，可配合 Cloudflare Tunnel / Ngrok 使用。")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Button {
                        exportOpenAPISchema()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.up.doc")
                            Text("导出 OpenAPI 3.1 规范 (JSON)")
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    // MARK: - Helper Views

    private func featureRow(icon: String, title: String, desc: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(Color(hex: "#FF5A5F"))
                .frame(width: 18)
                .padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                Text(desc)
                    .font(.system(size: 11.5))
                    .foregroundColor(.secondary)
            }
        }
    }

    private func promptRow(_ prompt: String) -> some View {
        HStack {
            Text("“\(prompt)”")
                .font(.system(size: 12))
                .foregroundColor(.primary)
            Spacer()
            Button {
                copyToClipboard(prompt, title: "指令已复制")
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .help("复制此指令")
        }
        .padding(.vertical, 3)
    }

    // MARK: - Actions

    private func copyToClipboard(_ string: String, title: String) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(string, forType: .string)
        withAnimation {
            copyStatusMessage = title
        }
        Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            withAnimation {
                if copyStatusMessage == title {
                    copyStatusMessage = nil
                }
            }
        }
    }

    private func autoInstallConfigs() {
        var installedLocations: [String] = []

        // 1. Claude Desktop config: ~/Library/Application Support/Claude/claude_desktop_config.json
        let claudeDir = ("~/Library/Application Support/Claude" as NSString).expandingTildeInPath
        let claudeConfigFile = (claudeDir as NSString).appendingPathComponent("claude_desktop_config.json")
        mergeMCPConfig(filePath: claudeConfigFile, appName: "Claude Desktop", results: &installedLocations)

        // 2. ChatGPT Desktop config location
        let chatgptDir = ("~/Library/Application Support/OpenAI/ChatGPT" as NSString).expandingTildeInPath
        let chatgptConfigFile = (chatgptDir as NSString).appendingPathComponent("mcp.json")
        mergeMCPConfig(filePath: chatgptConfigFile, appName: "ChatGPT Desktop", results: &installedLocations)

        if installedLocations.isEmpty {
            installStatusMessage = "未检测到已安装的桌面端配置目录，已将配置复制到剪贴板，可手动粘贴。"
            copyToClipboard(chatgptConfigJSON, title: "配置已复制")
        } else {
            installStatusMessage = "已成功写入: " + installedLocations.joined(separator: "，") + "。重启客户端即可生效！"
        }
    }

    private func mergeMCPConfig(filePath: String, appName: String, results: inout [String]) {
        let fileManager = FileManager.default
        let dir = (filePath as NSString).deletingLastPathComponent
        do {
            if !fileManager.fileExists(atPath: dir) {
                try fileManager.createDirectory(atPath: dir, withIntermediateDirectories: true)
            }

            var rootObj: [String: Any] = [:]
            if fileManager.fileExists(atPath: filePath),
               let data = try? Data(contentsOf: URL(fileURLWithPath: filePath)),
               let parsed = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                rootObj = parsed
            }

            var mcpServers = (rootObj["mcpServers"] as? [String: Any]) ?? [:]
            mcpServers["classflow"] = [
                "command": binaryPath,
                "args": [] as [String]
            ]
            rootObj["mcpServers"] = mcpServers

            let outputData = try JSONSerialization.data(withJSONObject: rootObj, options: [.prettyPrinted, .sortedKeys])
            try outputData.write(to: URL(fileURLWithPath: filePath))
            results.append(appName)
        } catch {
            print("[AISettingsTab] Failed to write config to \(filePath): \(error)")
        }
    }

    private func exportOpenAPISchema() {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "classflow-openapi.json"
        panel.allowedContentTypes = [.json]
        if panel.runModal() == .OK, let url = panel.url {
            let schemaStr = MCPServer.shared.generateOpenAPISpec()
            try? schemaStr.data(using: .utf8)?.write(to: url)
            copyStatusMessage = "OpenAPI 规范已导出至指定文件"
        }
    }
}

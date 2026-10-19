import SwiftUI
import ClassFlowCore

/// Settings tab for Bring Your Own Cloud (BYOC) credentials and synchronization control,
/// supporting both WebDAV (Jianguoyun 坚果云) and S3/Qiniu Kodo.
public struct SyncSettingsTab: View {
    @Bindable public var appState: AppState

    @State private var providerType: String = "webdav"

    // WebDAV Credentials
    @State private var webdavServerURL: String = "https://dav.jianguoyun.com/dav/"
    @State private var webdavUsername: String = ""
    @State private var webdavPassword: String = ""
    @State private var webdavPathPrefix: String = "ClassFlow/"

    // S3 / Qiniu Credentials
    @State private var endpoint: String = "https://s3.cn-east-1.qiniucs.com"
    @State private var region: String = "cn-east-1"
    @State private var bucket: String = ""
    @State private var accessKey: String = ""
    @State private var secretKey: String = ""
    @State private var pathPrefix: String = "classflow-sync/"

    @State private var isTesting: Bool = false
    @State private var testResult: ConnectionHealth? = nil
    @State private var isSyncing: Bool = false
    @State private var isForcePulling: Bool = false
    @State private var showForcePullConfirm: Bool = false
    @State private var syncStatusMessage: String? = nil

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        Form {
            Section("云同步服务 (BYOC)") {
                Picker("同步驱动", selection: $providerType) {
                    Text("WebDAV (标准协议)").tag("webdav")
                    Text("S3 兼容对象存储").tag("s3")
                }
            }

            if providerType == "webdav" {
                Section("WebDAV 服务器配置") {
                    TextField("服务器地址 (Server URL)", text: $webdavServerURL, prompt: Text("https://dav.jianguoyun.com/dav/"))
                    TextField("同步目录 (Path)", text: $webdavPathPrefix, prompt: Text("ClassFlow/"))
                    TextField("账号 / 邮箱 (Username)", text: $webdavUsername)
                    SecureField("应用授权密码 (App Password)", text: $webdavPassword)
                }
            } else {
                Section("S3 对象存储配置") {
                    TextField("Endpoint 服务地址", text: $endpoint, prompt: Text("https://s3.cn-south-1.qiniucs.com"))
                        .onChange(of: endpoint) { _, newEndpoint in
                            if newEndpoint.contains("cn-south-1") && region == "cn-east-1" {
                                region = "cn-south-1"
                            } else if newEndpoint.contains("cn-north-1") && region == "cn-east-1" {
                                region = "cn-north-1"
                            }
                        }
                    TextField("Region 区域代号", text: $region, prompt: Text("cn-south-1"))
                    TextField("存储桶 Bucket", text: $bucket)
                    TextField("存储路径前缀", text: $pathPrefix, prompt: Text("classflow-sync/"))
                }

                Section("身份凭据 (本地免密保存)") {
                    TextField("Access Key (AK)", text: $accessKey)
                    SecureField("Secret Key (SK)", text: $secretKey)
                }
            }

            Section("连接诊断与授权验证") {
                HStack {
                    Button(action: testConnectionAction) {
                        if isTesting {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Text("测试云端连接")
                        }
                    }
                    .disabled(isTesting || !isConfigInputReady)

                    Spacer()

                    if let result = testResult {
                        HStack(spacing: 4) {
                            Image(systemName: result.isReachable ? "checkmark.circle.fill" : "xmark.circle.fill")
                                .foregroundColor(result.isReachable ? .green : .red)
                            Text(result.message)
                                .font(.caption)
                                .foregroundColor(result.isReachable ? .primary : .red)
                        }
                    }
                }
            }

            Section("云同步策略 (Remote Save 两步选择)") {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("第一步：启动时同步")
                            .font(.system(size: 13, weight: .semibold))
                        Toggle("启动应用时自动进行双向云同步", isOn: $appState.settings.syncOnStartup)
                            .onChange(of: appState.settings.syncOnStartup) { _, _ in
                                appState.save()
                            }
                        Text("开启后，每次冷启动或打开应用时自动在后台检测并与云端双向同步最新数据。")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Divider()

                    VStack(alignment: .leading, spacing: 6) {
                        Text("第二步：定时自动同步")
                            .font(.system(size: 13, weight: .semibold))
                        Picker("自动同步周期", selection: $appState.settings.autoSyncInterval) {
                            Text("关闭").tag("off")
                            Text("每 15 分钟").tag("15m")
                            Text("每 30 分钟").tag("30m")
                            Text("每 1 小时").tag("1h")
                            Text("每 2 小时").tag("2h")
                            Text("每 6 小时").tag("6h")
                        }
                        .pickerStyle(.menu)
                        .onChange(of: appState.settings.autoSyncInterval) { _, _ in
                            appState.save()
                            appState.restartAutoSyncTimer()
                        }
                        Text("应用运行期间，将按指定时间间隔在后台静默发起双向同步。")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Section("多端同步") {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("当前状态")
                                .font(.system(size: 13, weight: .medium))
                            Text(syncStatusMessage ?? "就绪")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        Spacer()

                        HStack(spacing: 8) {
                            Button("从云端全量覆盖本地") {
                                showForcePullConfirm = true
                            }
                            .buttonStyle(.bordered)
                            .disabled(isSyncing || isForcePulling || !isConfigInputReady)

                            Button("立即双向同步") {
                                triggerSync()
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(isSyncing || isForcePulling || !isConfigInputReady)
                        }
                    }

                    Text("提示：若在某端清空了课表并希望重新从另一端拉取完整课表，请点击「从云端全量覆盖本地」。")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }
        }
        .formStyle(.grouped)
        .alert("确定从云端全量覆盖本地？", isPresented: $showForcePullConfirm) {
            Button("取消", role: .cancel) {}
            Button("全量覆盖并恢复", role: .destructive) {
                triggerForcePull()
            }
        } message: {
            Text("本地所有数据将被云端最新快照替代，用于修复由于误删或墓碑冲突导致的不同步。此操作不可逆。")
        }
        .onAppear {
            loadCredentials()
        }
        .onDisappear {
            saveCredentials()
        }
    }

    private var isConfigInputReady: Bool {
        if providerType == "webdav" {
            return !webdavServerURL.isEmpty && !webdavUsername.isEmpty && !webdavPassword.isEmpty
        } else {
            return !bucket.isEmpty && !accessKey.isEmpty && !secretKey.isEmpty
        }
    }

    private func loadCredentials() {
        if let savedType = UserDefaults.standard.string(forKey: "sync_provider_type") {
            providerType = (savedType == "qiniu") ? "s3" : savedType
        }
        if let savedUser = UserDefaults.standard.string(forKey: "webdav_user") {
            webdavUsername = savedUser
        }
        if let savedPwd = UserDefaults.standard.string(forKey: "webdav_pwd") {
            webdavPassword = savedPwd
        }
        if let savedURL = UserDefaults.standard.string(forKey: "webdav_url") {
            webdavServerURL = savedURL
        }
        if let savedWebdavPrefix = UserDefaults.standard.string(forKey: "webdav_path_prefix"), !savedWebdavPrefix.isEmpty {
            webdavPathPrefix = savedWebdavPrefix
        }

        if let savedAK = UserDefaults.standard.string(forKey: "s3_access_key"), !savedAK.isEmpty {
            accessKey = savedAK
        }
        if let savedSK = UserDefaults.standard.string(forKey: "s3_secret_key"), !savedSK.isEmpty {
            secretKey = savedSK
        }
        if let savedEndpoint = UserDefaults.standard.string(forKey: "s3_endpoint"), !savedEndpoint.isEmpty {
            endpoint = savedEndpoint
        }
        if let savedRegion = UserDefaults.standard.string(forKey: "s3_region"), !savedRegion.isEmpty {
            region = savedRegion
        }
        if let savedBucket = UserDefaults.standard.string(forKey: "s3_bucket"), !savedBucket.isEmpty {
            bucket = savedBucket
        }
        if let savedPrefix = UserDefaults.standard.string(forKey: "s3_path_prefix"), !savedPrefix.isEmpty {
            pathPrefix = savedPrefix
        }
    }

    private func saveCredentials() {
        UserDefaults.standard.set(providerType, forKey: "sync_provider_type")
        if providerType == "webdav" {
            UserDefaults.standard.set(webdavUsername, forKey: "webdav_user")
            UserDefaults.standard.set(webdavPassword, forKey: "webdav_pwd")
            UserDefaults.standard.set(webdavServerURL, forKey: "webdav_url")
            UserDefaults.standard.set(webdavPathPrefix, forKey: "webdav_path_prefix")
        } else {
            UserDefaults.standard.set(accessKey, forKey: "s3_access_key")
            UserDefaults.standard.set(secretKey, forKey: "s3_secret_key")
            UserDefaults.standard.set(endpoint, forKey: "s3_endpoint")
            UserDefaults.standard.set(region, forKey: "s3_region")
            UserDefaults.standard.set(bucket, forKey: "s3_bucket")
            UserDefaults.standard.set(pathPrefix, forKey: "s3_path_prefix")
        }
    }

    private func makeStorageProvider() -> any StorageProvider {
        if providerType == "webdav" {
            let config = WebDAVStorageProvider.Configuration(
                serverURL: webdavServerURL,
                username: webdavUsername,
                password: webdavPassword,
                pathPrefix: webdavPathPrefix
            )
            return WebDAVStorageProvider(config: config)
        } else {
            let config = S3StorageProvider.Configuration(
                endpoint: endpoint,
                region: region,
                bucket: bucket,
                accessKey: accessKey,
                secretKey: secretKey,
                pathPrefix: pathPrefix
            )
            return S3StorageProvider(config: config)
        }
    }

    private func testConnectionAction() {
        saveCredentials()
        isTesting = true
        testResult = nil

        let provider = makeStorageProvider()

        Task {
            do {
                let health = try await provider.testConnection()
                await MainActor.run {
                    self.testResult = health
                    self.isTesting = false
                }
            } catch {
                await MainActor.run {
                    self.testResult = ConnectionHealth(
                        isReachable: false,
                        latencyMs: 0,
                        bucketAccessible: false,
                        message: error.localizedDescription
                    )
                    self.isTesting = false
                }
            }
        }
    }

    private func triggerSync() {
        saveCredentials()
        isSyncing = true
        syncStatusMessage = "正在连接并双向同步..."

        let provider = makeStorageProvider()

        Task {
            do {
                let summary = try await appState.executeCloudSync(storage: provider)
                await MainActor.run {
                    self.isSyncing = false
                    let timeStr = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
                    self.syncStatusMessage = "同步完成 (\(timeStr) · 共 \(summary.activeCoursesCount) 门课 · 拉取 \(summary.downloadedPeerOperationsCount) 项)"
                }
            } catch {
                await MainActor.run {
                    self.isSyncing = false
                    self.syncStatusMessage = "同步失败: \(error.localizedDescription)"
                }
            }
        }
    }

    private func triggerForcePull() {
        saveCredentials()
        isForcePulling = true
        syncStatusMessage = "正在从云端拉取全量快照并覆盖本地..."

        let provider = makeStorageProvider()

        Task {
            do {
                let summary = try await appState.executeForceCloudPull(storage: provider)
                await MainActor.run {
                    self.isForcePulling = false
                    let timeStr = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
                    self.syncStatusMessage = "全量覆盖完成 (\(timeStr) · 已恢复 \(summary.activeCoursesCount) 门课程)"
                }
            } catch {
                await MainActor.run {
                    self.isForcePulling = false
                    self.syncStatusMessage = "覆盖拉取失败: \(error.localizedDescription)"
                }
            }
        }
    }
}

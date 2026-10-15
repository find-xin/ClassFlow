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
    @State private var syncStatusMessage: String? = nil

    private let keychain = KeychainService()

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
                    TextField("Endpoint 服务地址", text: $endpoint, prompt: Text("https://s3.cn-east-1.qiniucs.com"))
                    TextField("Region 区域代号", text: $region, prompt: Text("cn-east-1"))
                    TextField("存储桶 Bucket", text: $bucket)
                    TextField("存储路径前缀", text: $pathPrefix, prompt: Text("classflow-sync/"))
                }

                Section("身份凭据 (Keychain 加密保护)") {
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

            Section("多端同步") {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("当前状态")
                            .font(.system(size: 13, weight: .medium))
                        Text(syncStatusMessage ?? "就绪")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button("立即同步课表") {
                        triggerSync()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isSyncing || !isConfigInputReady)
                }
            }
        }
        .formStyle(.grouped)
        .onAppear {
            loadCredentials()
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
        if let savedType = keychain.readSecret(account: "sync_provider_type") {
            providerType = (savedType == "qiniu") ? "s3" : savedType
        }
        if let savedUser = keychain.readSecret(account: "webdav_user") {
            webdavUsername = savedUser
        }
        if let savedPwd = keychain.readSecret(account: "webdav_pwd") {
            webdavPassword = savedPwd
        }
        if let savedURL = keychain.readSecret(account: "webdav_url") {
            webdavServerURL = savedURL
        }
        if let savedAK = keychain.readSecret(account: "qiniu_ak") {
            accessKey = savedAK
        }
        if let savedSK = keychain.readSecret(account: "qiniu_sk") {
            secretKey = savedSK
        }
    }

    private func saveCredentials() {
        keychain.saveSecret(account: "sync_provider_type", secret: providerType)
        if providerType == "webdav" {
            if !webdavUsername.isEmpty { keychain.saveSecret(account: "webdav_user", secret: webdavUsername) }
            if !webdavPassword.isEmpty { keychain.saveSecret(account: "webdav_pwd", secret: webdavPassword) }
            if !webdavServerURL.isEmpty { keychain.saveSecret(account: "webdav_url", secret: webdavServerURL) }
        } else {
            if !accessKey.isEmpty { keychain.saveSecret(account: "qiniu_ak", secret: accessKey) }
            if !secretKey.isEmpty { keychain.saveSecret(account: "qiniu_sk", secret: secretKey) }
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
        let engine = SyncEngine()

        Task {
            do {
                var courses = appState.courses
                var exceptions = appState.exceptions
                let summary = try await engine.executeSync(
                    storage: provider,
                    courses: &courses,
                    exceptions: &exceptions
                )
                await MainActor.run {
                    self.appState.courses = courses
                    self.appState.exceptions = exceptions
                    self.appState.save()
                    self.isSyncing = false
                    self.syncStatusMessage = "同步完成 (上传 \(summary.uploadedOperationsCount) 项变更, 下载 \(summary.downloadedPeerOperationsCount) 项对端变更)"
                }
            } catch {
                await MainActor.run {
                    self.isSyncing = false
                    self.syncStatusMessage = "同步失败: \(error.localizedDescription)"
                }
            }
        }
    }
}

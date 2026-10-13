import SwiftUI
import ClassFlowCore

/// Settings tab for Bring Your Own Cloud (BYOC) credentials and synchronization control.
public struct SyncSettingsTab: View {
    @Bindable public var appState: AppState

    @State private var providerType: String = "qiniu"
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
            Section("云存储服务配置 (BYOC)") {
                Picker("存储类型", selection: $providerType) {
                    Text("七牛云 Kodo (S3 兼容)").tag("qiniu")
                    Text("通用 S3 兼容存储 (MinIO / R2 / AWS)").tag("s3")
                }
                .onChange(of: providerType) { _, newValue in
                    if newValue == "qiniu" {
                        endpoint = "https://s3.cn-east-1.qiniucs.com"
                        region = "cn-east-1"
                    }
                }

                TextField("Endpoint 服务地址", text: $endpoint)
                TextField("Region 区域代号", text: $region)
                TextField("存储桶 Bucket", text: $bucket)
                TextField("存储路径前缀", text: $pathPrefix)
            }

            Section("敏感身份凭证 (已加密存入 macOS Keychain)") {
                TextField("Access Key (AK)", text: $accessKey)
                SecureField("Secret Key (SK)", text: $secretKey)

                HStack {
                    Button(action: testConnectionAction) {
                        if isTesting {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Text("测试连接")
                        }
                    }
                    .disabled(isTesting || bucket.isEmpty || accessKey.isEmpty)

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

            Section("数据同步操作") {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("当前同步状态")
                            .font(.system(size: 13, weight: .medium))
                        Text(syncStatusMessage ?? "就绪")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button("立即同步") {
                        triggerSync()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isSyncing || bucket.isEmpty || accessKey.isEmpty)
                }
            }
        }
        .formStyle(.grouped)
        .onAppear {
            loadCredentials()
        }
    }

    private func loadCredentials() {
        if let savedAK = keychain.readSecret(account: "qiniu_ak") {
            accessKey = savedAK
        }
        if let savedSK = keychain.readSecret(account: "qiniu_sk") {
            secretKey = savedSK
        }
    }

    private func saveCredentials() {
        if !accessKey.isEmpty {
            keychain.saveSecret(account: "qiniu_ak", secret: accessKey)
        }
        if !secretKey.isEmpty {
            keychain.saveSecret(account: "qiniu_sk", secret: secretKey)
        }
    }

    private func testConnectionAction() {
        saveCredentials()
        isTesting = true
        testResult = nil

        let config = S3StorageProvider.Configuration(
            endpoint: endpoint,
            region: region,
            bucket: bucket,
            accessKey: accessKey,
            secretKey: secretKey,
            pathPrefix: pathPrefix
        )
        let provider = S3StorageProvider(config: config)

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
        syncStatusMessage = "正在同步变更..."

        let config = S3StorageProvider.Configuration(
            endpoint: endpoint,
            region: region,
            bucket: bucket,
            accessKey: accessKey,
            secretKey: secretKey,
            pathPrefix: pathPrefix
        )
        let provider = S3StorageProvider(config: config)
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
                    self.syncStatusMessage = "同步成功 (上传 \(summary.uploadedOperationsCount) 项, 下载 \(summary.downloadedPeerOperationsCount) 项)"
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

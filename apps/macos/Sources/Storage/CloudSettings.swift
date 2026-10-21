import Foundation

/// One preferences domain for both the .app and the stdio companion process.
/// Secrets are migrated only after a successful Keychain write.
enum CloudSettings {
    static let applicationDomain = "org.classflow.ClassFlow"

    static var defaults: UserDefaults { preferences() }

    /// Foundation can return nil when the suite is the running application's own domain.
    /// The GUI already owns that domain through .standard; other entrypoints open it as a suite.
    static func preferences(
        bundleIdentifier: String? = Bundle.main.bundleIdentifier,
        makeSuite: (String) -> UserDefaults? = { UserDefaults(suiteName: $0) }
    ) -> UserDefaults {
        if bundleIdentifier == applicationDomain { return .standard }
        return makeSuite(applicationDomain) ?? .standard
    }

    static func secret(_ key: String) -> String? {
        let keychain = KeychainService()
        if let secret = keychain.readSecret(account: key) { return secret }
        guard let legacy = defaults.string(forKey: key) ?? UserDefaults.standard.string(forKey: key), !legacy.isEmpty else { return nil }
        guard keychain.saveSecret(account: key, secret: legacy) else { return nil }
        defaults.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: key)
        return legacy
    }

    static func saveSecret(_ secret: String, key: String) throws {
        guard KeychainService().saveSecret(account: key, secret: secret) else {
            throw NSError(domain: "ClassFlowCredentials", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "无法将云同步凭据保存到系统钥匙串。"])
        }
        defaults.removeObject(forKey: key)
        UserDefaults.standard.removeObject(forKey: key)
    }
}

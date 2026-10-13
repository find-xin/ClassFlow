import Foundation
import Security

/// Secure credential storage managing cloud AccessKey/SecretKey via Apple Keychain Services.
public struct KeychainService: Sendable {
    public let serviceName: String

    public init(serviceName: String = "org.classflow.credentials") {
        self.serviceName = serviceName
    }

    /// Saves or updates a secret in macOS Keychain.
    @discardableResult
    public func saveSecret(account: String, secret: String) -> Bool {
        guard let data = secret.data(using: .utf8) else { return false }

        // Try deleting existing first
        deleteSecret(account: account)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    /// Reads a secret string from macOS Keychain.
    public func readSecret(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    /// Deletes a secret from macOS Keychain.
    @discardableResult
    public func deleteSecret(account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

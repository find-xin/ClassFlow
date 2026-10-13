import Foundation
import CryptoKit

/// Pure implementation of AWS Signature Version 4 (SigV4) for S3 and Qiniu Kodo endpoints.
public struct S3SignatureV4: Sendable {
    public let accessKey: String
    public let secretKey: String
    public let region: String
    public let service: String

    public init(
        accessKey: String,
        secretKey: String,
        region: String = "cn-east-1",
        service: String = "s3"
    ) {
        self.accessKey = accessKey
        self.secretKey = secretKey
        self.region = region
        self.service = service
    }

    public struct SignatureResult: Sendable, Equatable {
        public var authorizationHeader: String
        public var dateHeader: String
        public var payloadHashHeader: String
    }

    /// Computes SigV4 authentication headers for an outgoing HTTP request.
    public func signRequest(
        method: String,
        url: URL,
        headers: [String: String],
        payload: Data?,
        requestDate: Date = Date()
    ) -> SignatureResult {
        let dfTime = DateFormatter()
        dfTime.dateFormat = "yyyyMMdd'T'HHmmss'Z'"
        dfTime.timeZone = TimeZone(secondsFromGMT: 0)
        dfTime.locale = Locale(identifier: "en_US_POSIX")
        let amzDate = dfTime.string(from: requestDate)

        let dfDate = DateFormatter()
        dfDate.dateFormat = "yyyyMMdd"
        dfDate.timeZone = TimeZone(secondsFromGMT: 0)
        dfDate.locale = Locale(identifier: "en_US_POSIX")
        let dateStamp = dfDate.string(from: requestDate)

        // 1. Payload SHA-256 hash
        let payloadData = payload ?? Data()
        let payloadHash = sha256Hex(data: payloadData)

        // 2. Canonical headers
        var allHeaders = headers
        allHeaders["x-amz-date"] = amzDate
        allHeaders["x-amz-content-sha256"] = payloadHash
        if allHeaders["host"] == nil, let host = url.host {
            allHeaders["host"] = host
        }

        let sortedHeaderKeys = allHeaders.keys.map { $0.lowercased() }.sorted()
        let canonicalHeaders = sortedHeaderKeys.map { key in
            "\(key):\(allHeaders.first(where: { $0.key.lowercased() == key })!.value.trimmingCharacters(in: .whitespaces))\n"
        }.joined()
        let signedHeaders = sortedHeaderKeys.joined(separator: ";")

        // 3. Canonical URI & Query string
        let canonicalURI = url.path.isEmpty ? "/" : url.path
        let queryItems = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let canonicalQuery = queryItems
            .sorted { $0.name < $1.name }
            .map { "\($0.name)=\($0.value ?? "")" }
            .joined(separator: "&")

        // 4. Canonical Request
        let canonicalRequest = """
        \(method.uppercased())
        \(canonicalURI)
        \(canonicalQuery)
        \(canonicalHeaders)
        \(signedHeaders)
        \(payloadHash)
        """
        let canonicalRequestHash = sha256Hex(data: canonicalRequest.data(using: .utf8)!)

        // 5. String to Sign
        let credentialScope = "\(dateStamp)/\(region)/\(service)/aws4_request"
        let stringToSign = """
        AWS4-HMAC-SHA256
        \(amzDate)
        \(credentialScope)
        \(canonicalRequestHash)
        """

        // 6. Derive Signing Key: HMAC-SHA256(kSecret, date, region, service, "aws4_request")
        let kDate = hmacSHA256(key: "AWS4\(secretKey)".data(using: .utf8)!, data: dateStamp.data(using: .utf8)!)
        let kRegion = hmacSHA256(key: kDate, data: region.data(using: .utf8)!)
        let kService = hmacSHA256(key: kRegion, data: service.data(using: .utf8)!)
        let kSigning = hmacSHA256(key: kService, data: "aws4_request".data(using: .utf8)!)

        // 7. Calculate Signature
        let signature = hmacSHA256(key: kSigning, data: stringToSign.data(using: .utf8)!)
        let signatureHex = signature.map { String(format: "%02x", $0) }.joined()

        let authHeader = "AWS4-HMAC-SHA256 Credential=\(accessKey)/\(credentialScope), SignedHeaders=\(signedHeaders), Signature=\(signatureHex)"

        return SignatureResult(
            authorizationHeader: authHeader,
            dateHeader: amzDate,
            payloadHashHeader: payloadHash
        )
    }

    private func sha256Hex(data: Data) -> String {
        let digest = SHA256.hash(data: data)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private func hmacSHA256(key: Data, data: Data) -> Data {
        let symKey = SymmetricKey(data: key)
        let mac = HMAC<SHA256>.authenticationCode(for: data, using: symKey)
        return Data(mac)
    }
}

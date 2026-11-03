import Foundation

/// Reapply changes made locally during network suspension, preserving independent peer fields.
enum LocalSyncRebase {
    static func merge<T: Codable & Equatable & Identifiable>(baseline: [T], local: [T], remote: [T]) throws -> [T] {
        let before = Dictionary(baseline.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let current = Dictionary(local.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        var output = remote.filter { before[$0.id] == nil || current[$0.id] != nil }
        let encoder = JSONEncoder(), decoder = JSONDecoder()
        for item in local where before[item.id] != item {
            guard let index = output.firstIndex(where: { $0.id == item.id }) else { output.append(item); continue }
            guard let old = before[item.id] else { output[index] = item; continue }
            let base = try JSONSerialization.jsonObject(with: encoder.encode(old)) as! [String: Any]
            let updated = try JSONSerialization.jsonObject(with: encoder.encode(item)) as! [String: Any]
            var merged = try JSONSerialization.jsonObject(with: encoder.encode(output[index])) as! [String: Any]
            for key in Set(base.keys).union(updated.keys) {
                let same = (base[key].map { normalized($0, key: key) } as? NSObject)?.isEqual(updated[key].map { normalized($0, key: key) }) ?? (updated[key] == nil)
                if key == "excludedDates" {
                    merged[key] = Array(Set((merged[key] as? [String] ?? []) + (updated[key] as? [String] ?? []))).sorted()
                } else if !same { merged[key] = updated[key] }
            }
            output[index] = try decoder.decode(T.self, from: JSONSerialization.data(withJSONObject: merged))
        }
        return output
    }
    private static func normalized(_ value: Any, key: String) -> Any {
        if let object = value as? [String: Any] {
            return object.reduce(into: [String: Any]()) { result, pair in
                result[pair.key] = normalized(pair.value, key: pair.key)
            }
        }
        if let array = value as? [Any] {
            let values = array.map { normalized($0, key: "") }
            if ["customWeeks", "customDaysOfWeek"].contains(key) {
                return values.sorted { String(describing: $0) < String(describing: $1) }
            }
            return values
        }
        return value
    }

}

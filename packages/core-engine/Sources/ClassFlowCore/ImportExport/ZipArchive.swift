import Foundation
import zlib

/// Portable, dependency-free ZIP archive encoder and decoder supporting Store and Deflate formats.
public struct ZipArchive: Sendable {
    public enum ZipError: Error, LocalizedError {
        case invalidArchiveFormat
        case endOfCentralDirectoryNotFound
        case unsupportedCompressionMethod(UInt16)
        case decompressionFailed(Int32)
        case corruptedHeader

        public var errorDescription: String? {
            switch self {
            case .invalidArchiveFormat: return "Invalid ZIP archive format."
            case .endOfCentralDirectoryNotFound: return "End of Central Directory record not found in archive."
            case .unsupportedCompressionMethod(let m): return "Unsupported ZIP compression method: \(m)."
            case .decompressionFailed(let code): return "Zlib decompression failed with error code \(code)."
            case .corruptedHeader: return "Corrupted local or central directory header in ZIP archive."
            }
        }
    }

    public init() {}

    // MARK: - Archive Creation

    /// Encodes a dictionary of [filename: contentData] into a standard PKZip archive.
    public func createArchive(files: [String: Data]) -> Data {
        var archive = Data()
        var centralDirectory = Data()
        var centralDirectoryEntriesCount: UInt16 = 0

        for (filename, fileData) in files {
            guard let nameData = filename.data(using: .utf8) else { continue }

            let localHeaderOffset = UInt32(archive.count)
            let crc = computeCRC32(data: fileData)
            let uncompressedSize = UInt32(fileData.count)
            let compressedSize = uncompressedSize // Method 0 (Store)

            // 1. Local File Header
            archive.appendUInt32(0x04034b50) // Local header signature
            archive.appendUInt16(20)         // Version needed to extract (2.0)
            archive.appendUInt16(0x0800)     // General purpose bit flag (UTF-8)
            archive.appendUInt16(0)          // Compression method: 0 = Store
            archive.appendUInt16(0)          // Last mod file time
            archive.appendUInt16(0)          // Last mod file date
            archive.appendUInt32(crc)        // CRC-32
            archive.appendUInt32(compressedSize)
            archive.appendUInt32(uncompressedSize)
            archive.appendUInt16(UInt16(nameData.count)) // File name length
            archive.appendUInt16(0)          // Extra field length
            archive.append(nameData)
            archive.append(fileData)

            // 2. Central Directory Header
            centralDirectory.appendUInt32(0x02014b50) // Central directory signature
            centralDirectory.appendUInt16(20)         // Version made by
            centralDirectory.appendUInt16(20)         // Version needed to extract
            centralDirectory.appendUInt16(0x0800)     // UTF-8
            centralDirectory.appendUInt16(0)          // Method: 0 = Store
            centralDirectory.appendUInt16(0)          // Time
            centralDirectory.appendUInt16(0)          // Date
            centralDirectory.appendUInt32(crc)
            centralDirectory.appendUInt32(compressedSize)
            centralDirectory.appendUInt32(uncompressedSize)
            centralDirectory.appendUInt16(UInt16(nameData.count))
            centralDirectory.appendUInt16(0)          // Extra field length
            centralDirectory.appendUInt16(0)          // File comment length
            centralDirectory.appendUInt16(0)          // Disk number start
            centralDirectory.appendUInt16(0)          // Internal file attributes
            centralDirectory.appendUInt32(0)          // External file attributes
            centralDirectory.appendUInt32(localHeaderOffset) // Relative offset of local header
            centralDirectory.append(nameData)

            centralDirectoryEntriesCount += 1
        }

        let centralDirectoryOffset = UInt32(archive.count)
        let centralDirectorySize = UInt32(centralDirectory.count)
        archive.append(centralDirectory)

        // 3. End of Central Directory Record (EOCD)
        archive.appendUInt32(0x06054b50)                  // EOCD signature
        archive.appendUInt16(0)                           // Number of this disk
        archive.appendUInt16(0)                           // Disk where central directory starts
        archive.appendUInt16(centralDirectoryEntriesCount)// Number of entries on this disk
        archive.appendUInt16(centralDirectoryEntriesCount)// Total number of entries
        archive.appendUInt32(centralDirectorySize)        // Size of central directory
        archive.appendUInt32(centralDirectoryOffset)      // Offset of central directory
        archive.appendUInt16(0)                           // Comment length

        return archive
    }

    // MARK: - Archive Extraction

    /// Extracts all files from a standard ZIP data buffer into [filename: contentData].
    public func extractArchive(data: Data) throws -> [String: Data] {
        guard data.count >= 22 else { throw ZipError.invalidArchiveFormat }

        // Find End of Central Directory Record (EOCD) by scanning backwards
        var eocdOffset: Int? = nil
        let minOffset = max(0, data.count - 65557) // Max comment length is 65535 + 22 byte header
        for i in stride(from: data.count - 22, through: minOffset, by: -1) {
            if data.readUInt32(at: i) == 0x06054b50 {
                eocdOffset = i
                break
            }
        }

        guard let eocdPos = eocdOffset else {
            throw ZipError.endOfCentralDirectoryNotFound
        }

        let totalEntries = Int(data.readUInt16(at: eocdPos + 10))
        let cdOffset = Int(data.readUInt32(at: eocdPos + 16))

        var results: [String: Data] = [:]
        var currentCDPos = cdOffset

        for _ in 0..<totalEntries {
            guard currentCDPos + 46 <= data.count else { throw ZipError.corruptedHeader }
            guard data.readUInt32(at: currentCDPos) == 0x02014b50 else {
                throw ZipError.corruptedHeader
            }

            let compressionMethod = data.readUInt16(at: currentCDPos + 10)
            let compressedSize = Int(data.readUInt32(at: currentCDPos + 20))
            let uncompressedSize = Int(data.readUInt32(at: currentCDPos + 24))
            let fileNameLength = Int(data.readUInt16(at: currentCDPos + 28))
            let extraLength = Int(data.readUInt16(at: currentCDPos + 30))
            let commentLength = Int(data.readUInt16(at: currentCDPos + 32))
            let localHeaderOffset = Int(data.readUInt32(at: currentCDPos + 42))

            let nameStart = currentCDPos + 46
            guard nameStart + fileNameLength <= data.count else { throw ZipError.corruptedHeader }
            let nameBytes = data.subdata(in: nameStart..<(nameStart + fileNameLength))
            let filename = String(data: nameBytes, encoding: .utf8) ?? String(decoding: nameBytes, as: UTF8.self)

            // Read from local file header
            guard localHeaderOffset + 30 <= data.count else { throw ZipError.corruptedHeader }
            guard data.readUInt32(at: localHeaderOffset) == 0x04034b50 else {
                throw ZipError.corruptedHeader
            }
            let localNameLen = Int(data.readUInt16(at: localHeaderOffset + 26))
            let localExtraLen = Int(data.readUInt16(at: localHeaderOffset + 28))
            let fileDataStart = localHeaderOffset + 30 + localNameLen + localExtraLen

            guard fileDataStart + compressedSize <= data.count else {
                throw ZipError.corruptedHeader
            }
            let rawFileData = data.subdata(in: fileDataStart..<(fileDataStart + compressedSize))

            // Decompress or Store
            let extractedData: Data
            if compressionMethod == 0 {
                extractedData = rawFileData
            } else if compressionMethod == 8 {
                extractedData = try decompressDeflate(rawFileData, uncompressedSize: uncompressedSize)
            } else {
                throw ZipError.unsupportedCompressionMethod(compressionMethod)
            }

            results[filename] = extractedData
            currentCDPos += 46 + fileNameLength + extraLength + commentLength
        }

        return results
    }

    // MARK: - Helper Methods

    private func computeCRC32(data: Data) -> UInt32 {
        data.withUnsafeBytes { buffer in
            guard let baseAddress = buffer.baseAddress else { return 0 }
            let crc = crc32(0, baseAddress.assumingMemoryBound(to: Bytef.self), uInt(buffer.count))
            return UInt32(crc)
        }
    }

    private func decompressDeflate(_ compressed: Data, uncompressedSize: Int) throws -> Data {
        var stream = z_stream()
        var status = inflateInit2_(&stream, -MAX_WBITS, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard status == Z_OK else { throw ZipError.decompressionFailed(status) }
        defer { inflateEnd(&stream) }

        var output = Data(count: uncompressedSize)
        try compressed.withUnsafeBytes { inBuffer in
            try output.withUnsafeMutableBytes { outBuffer in
                stream.next_in = UnsafeMutablePointer(mutating: inBuffer.bindMemory(to: Bytef.self).baseAddress)
                stream.avail_in = uInt(compressed.count)
                stream.next_out = outBuffer.bindMemory(to: Bytef.self).baseAddress
                stream.avail_out = uInt(uncompressedSize)

                status = inflate(&stream, Z_FINISH)
                guard status == Z_STREAM_END || status == Z_OK else {
                    throw ZipError.decompressionFailed(status)
                }
            }
        }
        return output
    }
}

// MARK: - Data Binary Serialization Extensions
extension Data {
    mutating func appendUInt16(_ value: UInt16) {
        var val = value.littleEndian
        Swift.withUnsafeBytes(of: &val) { self.append(contentsOf: $0) }
    }

    mutating func appendUInt32(_ value: UInt32) {
        var val = value.littleEndian
        Swift.withUnsafeBytes(of: &val) { self.append(contentsOf: $0) }
    }

    func readUInt16(at offset: Int) -> UInt16 {
        subdata(in: offset..<(offset + 2)).withUnsafeBytes { $0.load(as: UInt16.self).littleEndian }
    }

    func readUInt32(at offset: Int) -> UInt32 {
        subdata(in: offset..<(offset + 4)).withUnsafeBytes { $0.load(as: UInt32.self).littleEndian }
    }
}

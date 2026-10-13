import Foundation
import Testing
@testable import ClassFlowCore

@Suite("ZipArchiveTests")
struct ZipArchiveTests {
    let zip = ZipArchive()

    @Test("Encode and extract files with CRC32 integrity")
    func testZipRoundtrip() throws {
        let file1 = "hello.txt"
        let data1 = "Hello, ClassFlow Native Timetable!".data(using: .utf8)!

        let file2 = "folder/data.json"
        let data2 = "{\"status\": \"ok\", \"count\": 42}".data(using: .utf8)!

        let file3 = "empty.bin"
        let data3 = Data()

        let archiveData = zip.createArchive(files: [
            file1: data1,
            file2: data2,
            file3: data3
        ])

        #expect(archiveData.count > 0)

        // Extract
        let extracted = try zip.extractArchive(data: archiveData)
        #expect(extracted.count == 3)
        #expect(extracted[file1] == data1)
        #expect(extracted[file2] == data2)
        #expect(extracted[file3] == data3)
    }

    @Test("Detect invalid or corrupted ZIP archive")
    func testCorruptedArchiveThrows() {
        let garbage = Data([0x01, 0x02, 0x03, 0x04])
        #expect(throws: Error.self) {
            try zip.extractArchive(data: garbage)
        }
    }
}

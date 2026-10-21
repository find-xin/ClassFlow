import Foundation
import Testing
@testable import ClassFlow

@MainActor
private final class MemoryTunnelSecrets: TunnelSecretStore {
    var value: String?
    var failSaving = false
    var readCount = 0
    init(_ value: String? = "test-runtime-key") { self.value = value }
    func read() -> String? { readCount += 1; return value }
    func save(_ value: String) -> Bool { if failSaving { return false }; self.value = value; return true }
    func delete() -> Bool { value = nil; return true }
}

@MainActor
private final class FakeTunnelChild: TunnelChildProcess {
    var isRunning = false
    var command: TunnelCommand?
    var preparationStaysRunning = false
    var healthURL = "http://127.0.0.1:54321"
    var exit: (@Sendable (Int32) -> Void)?
    var issue: (@Sendable (TunnelIssue) -> Void)?
    var terminationCount = 0
    func launch(_ command: TunnelCommand, onExit: @escaping @Sendable (Int32) -> Void,
                onIssue: @escaping @Sendable (TunnelIssue) -> Void) throws {
        self.command = command
        exit = onExit
        issue = onIssue
        isRunning = true
        if command.arguments.first == "init" && !preparationStaysRunning {
            isRunning = false
            onExit(0)
        }
        if let i = command.arguments.firstIndex(of: "--health.url-file") {
            try healthURL.write(toFile: command.arguments[i + 1], atomically: true, encoding: .utf8)
        }
    }
    func terminate() { terminationCount += 1; if isRunning { isRunning = false; exit?(0) } }
    func forceKill() { terminate() }
}

@MainActor
private final class TunnelChildFactory {
    var children: [FakeTunnelChild] = []
    var preparationStaysRunning = false
    var healthURL = "http://127.0.0.1:54321"
    func make() -> any TunnelChildProcess {
        let child = FakeTunnelChild()
        child.preparationStaysRunning = preparationStaysRunning
        child.healthURL = healthURL
        children.append(child)
        return child
    }
}

private actor TunnelProbeSpy {
    var reachable = true
    var hits = 0
    func probe(_ url: URL) -> Bool { hits += 1; return reachable }
    func setReachable(_ value: Bool) { reachable = value }
}

@Suite("TunnelManagerTests")
@MainActor
struct TunnelManagerTests {
    private func fixture() throws -> (URL, UserDefaults, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let executable = root.appendingPathComponent("ClassFlow's executable")
        try Data("#!/bin/sh\nexit 0\n".utf8).write(to: executable)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: executable.path)
        let defaults = try #require(UserDefaults(suiteName: "ClassFlowTunnelTests.\(UUID().uuidString)"))
        return (root, defaults, executable)
    }

    private func waitFor(_ predicate: @MainActor () -> Bool) async throws {
        for _ in 0..<100 {
            if predicate() { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        #expect(predicate())
    }

    @Test("Preferences default off, do not read credentials, and import only the existing tunnel ID")
    func defaultsAndImport() throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let profile = root.appendingPathComponent("old.yaml")
        try "control_plane:\n  tunnel_id: \"tunnel_11111111111111111111111111111111\"\n  api_key: env:CONTROL_PLANE_API_KEY\n".write(to: profile, atomically: true, encoding: .utf8)
        let secrets = MemoryTunnelSecrets()
        let manager = TunnelManager(defaults: defaults, secrets: secrets, root: root, mcpExecutable: executable, existingProfile: profile)
        defer { manager.shutdown() }
        #expect(manager.configuration.tunnelID == "tunnel_11111111111111111111111111111111")
        #expect(!manager.configuration.startWithApp)
        manager.startOnAppLaunch()
        #expect(secrets.readCount == 0)
        #expect(manager.state == .stopped)
    }

    @Test("Credentials save to the vault, never to preferences or launch arguments")
    func credentialBoundaries() throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let secrets = MemoryTunnelSecrets(nil)
        let manager = TunnelManager(defaults: defaults, secrets: secrets, root: root, mcpExecutable: executable, existingProfile: nil)
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: "confidential-test-value"))
        #expect(secrets.value == "confidential-test-value")
        #expect(!defaults.dictionaryRepresentation().description.contains("confidential-test-value"))
        let plan = try TunnelLaunchPlan(configuration: manager.configuration, root: root, mcpExecutable: executable, key: "confidential-test-value",
                                        inheritedEnvironment: ["OPENAI_API_KEY": "unrelated-secret", "PATH": "/usr/bin", "CONTROL_PLANE_BASE_URL": "https://untrusted.invalid"])
        #expect(!plan.preparation.arguments.joined().contains("confidential-test-value"))
        #expect(plan.preparation.environment["CONTROL_PLANE_API_KEY"] == nil)
        #expect(plan.runtime.environment["CONTROL_PLANE_API_KEY"] == "confidential-test-value")
        #expect(plan.runtime.environment["OPENAI_API_KEY"] == nil)
        #expect(plan.runtime.environment["CONTROL_PLANE_BASE_URL"] == nil)
        #expect(plan.runtime.arguments.contains("--health.url-file"))
        #expect(plan.preparation.arguments.contains("'" + executable.path.replacingOccurrences(of: "'", with: "'\\''") + "' --mcp"))
        manager.forgetKey()
        #expect(secrets.value == nil)
        #expect(!manager.hasSavedKey)
    }

    @Test("Invalid IDs and failed vault saves do not commit partial configuration")
    func validationAndAtomicSave() throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let secrets = MemoryTunnelSecrets()
        let manager = TunnelManager(defaults: defaults, secrets: secrets, root: root, mcpExecutable: executable, existingProfile: nil)
        defer { manager.shutdown() }
        let original = manager.configuration
        #expect(!manager.save(tunnelID: "https://example.com", clientPath: executable.path, newKey: "new-value"))
        #expect(manager.configuration == original)
        #expect(secrets.value == "test-runtime-key")
        secrets.failSaving = true
        #expect(!manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: "new-value"))
        #expect(manager.configuration == original)
        #expect(defaults.string(forKey: "chatgpt_tunnel_id") == nil)
    }

    @Test("Missing credentials or executable reports an error instead of launching")
    func missingRequirements() throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        let secrets = MemoryTunnelSecrets(nil)
        let manager = TunnelManager(defaults: defaults, secrets: secrets, root: root, mcpExecutable: executable, existingProfile: nil, makeProcess: { factory.make() })
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        manager.start()
        #expect(manager.state == .failed)
        #expect(factory.children.isEmpty)
        secrets.value = "test-key"
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: "/does/not/exist", newKey: ""))
        manager.start()
        #expect(manager.state == .failed)
        #expect(factory.children.isEmpty)
    }

    @Test("Ready state follows health checks; duplicate start and stop are safe")
    func lifecycle() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        let spy = TunnelProbeSpy()
        await spy.setReachable(false)
        let manager = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil,
                                    makeProcess: { factory.make() }, readiness: { await spy.probe($0) }, pollInterval: .milliseconds(10))
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        manager.start()
        manager.start()
        try await waitFor { manager.state == .connecting }
        #expect(factory.children.count == 2)
        await spy.setReachable(true)
        try await waitFor { manager.state == .ready }
        await spy.setReachable(false)
        try await waitFor { manager.state == .connecting }
        manager.stop()
        try await waitFor { manager.state == .stopped }
        #expect(factory.children.allSatisfy { !$0.isRunning })
        manager.stop()
        #expect(manager.state == .stopped)
    }

    @Test("App startup preference persists and starts once per launch")
    func automaticStartup() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let first = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil)
        #expect(first.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        first.setStartWithApp(true)
        first.shutdown()
        let factory = TunnelChildFactory()
        let manager = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil,
                                    makeProcess: { factory.make() }, readiness: { _ in true }, pollInterval: .milliseconds(10))
        defer { manager.shutdown() }
        #expect(manager.configuration.startWithApp)
        manager.startOnAppLaunch()
        manager.startOnAppLaunch()
        try await waitFor { manager.state == .ready }
        #expect(factory.children.count == 2)
    }

    @Test("Stopping during configuration cannot launch a late runtime")
    func stopDuringPreparation() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        factory.preparationStaysRunning = true
        let manager = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil, makeProcess: { factory.make() })
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        manager.start()
        try await waitFor { factory.children.count == 1 }
        manager.stop()
        try await waitFor { manager.state == .stopped }
        #expect(factory.children.count == 1)
        #expect(!factory.children[0].isRunning)
    }

    @Test("Only one App instance may own the private tunnel runtime")
    func exclusiveRuntime() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        let first = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil, makeProcess: { factory.make() }, readiness: { _ in true })
        let second = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil, makeProcess: { factory.make() })
        defer { first.shutdown(); second.shutdown() }
        for manager in [first, second] { #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: "")) }
        first.start()
        try await waitFor { first.state == .ready }
        second.start()
        #expect(second.state == .failed)
        #expect(second.message == TunnelError.alreadyManaged.localizedDescription)
        #expect(factory.children.count == 2)
    }

    @Test("Health files cannot redirect probes to remote hosts or embedded credentials")
    func healthURLValidation() {
        #expect(TunnelLaunchPlan.healthBaseURL("http://127.0.0.1:1234\n") != nil)
        for value in ["https://example.com:443", "http://127.0.0.1:1234@evil.invalid", "http://u:p@127.0.0.1:1234", "http://127.0.0.1:1234/secret", "file:///tmp/a", "http://127.0.0.1:0"] {
            #expect(TunnelLaunchPlan.healthBaseURL(value) == nil)
        }
    }

    @Test("Untrusted health URLs never produce a ready connection")
    func rejectsRemoteHealthFile() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        factory.healthURL = "https://evil.invalid:443"
        let spy = TunnelProbeSpy()
        let manager = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil,
                                    makeProcess: { factory.make() }, readiness: { await spy.probe($0) }, pollInterval: .milliseconds(10))
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        manager.start()
        try await waitFor { manager.state == .connecting }
        try await Task.sleep(for: .milliseconds(30))
        #expect(manager.state != .ready)
        #expect(await spy.hits == 0)
    }

    @Test("Runtime exit clears the connection and raw diagnostics are not displayed")
    func failedRuntime() async throws {
        let (root, defaults, executable) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let factory = TunnelChildFactory()
        let spy = TunnelProbeSpy()
        let manager = TunnelManager(defaults: defaults, secrets: MemoryTunnelSecrets(), root: root, mcpExecutable: executable, existingProfile: nil,
                                    makeProcess: { factory.make() }, readiness: { await spy.probe($0) }, pollInterval: .milliseconds(10))
        defer { manager.shutdown() }
        #expect(manager.save(tunnelID: "tunnel_00000000000000000000000000000000", clientPath: executable.path, newKey: ""))
        manager.start()
        try await waitFor { manager.state == .ready }
        // Keep the readiness monitor from replacing a transient diagnostic before it is asserted.
        await spy.setReachable(false)
        try await waitFor { manager.state == .connecting }
        let child = try #require(factory.children.last)
        let issue = try #require(TunnelIssue.classify("401 Unauthorized token=secret-that-must-not-appear"))
        child.issue?(issue)
        try await waitFor { manager.message == issue.message }
        child.isRunning = false
        child.exit?(1)
        try await waitFor { manager.state == .failed }
        #expect(!manager.message.contains("secret-that-must-not-appear"))
    }

    @Test("Native process runner terminates a real owned subprocess")
    func nativeProcess() async throws {
        let child = FoundationTunnelChild()
        try child.launch(TunnelCommand(executable: URL(fileURLWithPath: "/bin/sleep"), arguments: ["10"], environment: [:]), onExit: { _ in }, onIssue: { _ in })
        defer { child.forceKill() }
        #expect(child.isRunning)
        child.terminate()
        try await waitFor { !child.isRunning }
    }
}

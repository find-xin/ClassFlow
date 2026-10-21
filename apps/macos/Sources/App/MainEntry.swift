import Foundation
import AppKit

@main
struct MainEntry {
    static func main() {
        let args = CommandLine.arguments
        let isMCP = args.contains("--mcp") ||
                    args.contains("-mcp") ||
                    args.contains("--openapi") ||
                    args.first?.hasSuffix("classflow-mcp") == true ||
                    (args.count > 1 && ["tools", "status", "sync", "--help", "-h"].contains(args[1]))

        if isMCP {
            MCPServer.run()
        } else {
            ClassFlowApp.main()
        }
    }
}

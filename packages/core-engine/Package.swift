// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "ClassFlowCore",
    platforms: [
        .macOS(.v15)
    ],
    products: [
        .library(
            name: "ClassFlowCore",
            targets: ["ClassFlowCore"]
        ),
    ],
    targets: [
        .target(
            name: "ClassFlowCore",
            path: "Sources/ClassFlowCore"
        ),
        .testTarget(
            name: "ClassFlowCoreTests",
            dependencies: ["ClassFlowCore"],
            path: "Tests/ClassFlowCoreTests"
        ),
    ]
)

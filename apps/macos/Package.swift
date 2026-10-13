// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "ClassFlowMac",
    platforms: [
        .macOS(.v15)
    ],
    products: [
        .executable(
            name: "ClassFlow",
            targets: ["ClassFlow"]
        ),
    ],
    dependencies: [
        .package(path: "../../packages/core-engine")
    ],
    targets: [
        .executableTarget(
            name: "ClassFlow",
            dependencies: [
                .product(name: "ClassFlowCore", package: "core-engine")
            ],
            path: "Sources"
        ),
        .testTarget(
            name: "ClassFlowMacTests",
            dependencies: [
                "ClassFlow",
                .product(name: "ClassFlowCore", package: "core-engine")
            ],
            path: "Tests"
        )
    ]
)

// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "SupportChat",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "SupportChat", targets: ["SupportChat"]),
    ],
    targets: [
        .target(name: "SupportChat", path: "ios/Sources/SupportChat"),
        .testTarget(name: "SupportChatTests", dependencies: ["SupportChat"], path: "ios/Tests/SupportChatTests"),
    ]
)

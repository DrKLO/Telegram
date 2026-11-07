// swift-tools-version:5.5
import PackageDescription
let package = Package(name: "VerifySolar", platforms: [.macOS(.v12)], dependencies: [.package(name: "twidget", path: "../..")], targets: [.executableTarget(name: "VerifySolar", dependencies: [.product(name: "HTMLScript", package: "twidget"), .product(name: "HTML3D", package: "twidget")])])

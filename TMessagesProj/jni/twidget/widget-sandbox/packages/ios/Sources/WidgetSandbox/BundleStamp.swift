// The stamp packages/core/scripts/build-native-bundles.ts writes on the first line of every
// bundle, from VERSION.json (packages/core/scripts/version.ts): this glue runs only bundles of
// its own protocol, profile and sdk (WidgetSandboxVersion, generated from the same file), the
// way it runs only a `.qbc` compiled from the source next to it. The release itself is not
// compared. Twins: BundleStamp.kt, bundle_stamp.cpp; the format's tests: version.test.ts.

import Foundation

public struct BundleStamp: Equatable, CustomStringConvertible {
    public let version: String
    public let protocolVersion: Int
    public let profileVersion: Int
    public let sdkVersion: Int

    public init(version: String, protocolVersion: Int, profileVersion: Int, sdkVersion: Int) {
        self.version = version
        self.protocolVersion = protocolVersion
        self.profileVersion = profileVersion
        self.sdkVersion = sdkVersion
    }

    /// This glue's own.
    public static let glue = BundleStamp(version: WidgetSandboxVersion.version, protocolVersion: WidgetSandboxVersion.protocolVersion,
                                         profileVersion: WidgetSandboxVersion.profileVersion, sdkVersion: WidgetSandboxVersion.sdkVersion)

    public var description: String { "widget-sandbox \(version) (protocol \(protocolVersion), profile \(profileVersion), sdk \(sdkVersion))" }

    /// The line itself, as build-native-bundles.ts writes it.
    public var line: String { "/* widget-sandbox version=\(version) protocol=\(protocolVersion) profile=\(profileVersion) sdk=\(sdkVersion) */" }

    /// The stamp on the first line of a bundle's source; nil when there is none (a bundle built before
    /// VERSION.json) or it is malformed: the prefix, then `key=value` pairs separated by one space, unknown keys
    /// ignored, `version` non-empty and the three numbers 1 to 6 ASCII digits.
    public static func parse(_ source: String) -> BundleStamp? {
        let end = source.utf8.firstIndex(of: 0x0A) ?? source.endIndex
        let line = source[..<end]
        let prefix = "/* widget-sandbox ", suffix = " */"
        guard line.utf8.count >= prefix.utf8.count + suffix.utf8.count, line.hasPrefix(prefix), line.hasSuffix(suffix) else { return nil }
        let body = line.dropFirst(prefix.count).dropLast(suffix.count)
        var fields: [Substring: Substring] = [:]
        for pair in body.split(separator: " ", omittingEmptySubsequences: false) {
            guard let eq = pair.firstIndex(of: "="), eq != pair.startIndex else { return nil }
            fields[pair[..<eq]] = pair[pair.index(after: eq)...]
        }
        func number(_ key: Substring) -> Int? {
            guard let s = fields[key], (1...6).contains(s.utf8.count), s.utf8.allSatisfy({ $0 >= 0x30 && $0 <= 0x39 }) else { return nil }
            return Int(s)
        }
        guard let version = fields["version"], !version.isEmpty, let p = number("protocol"), let pr = number("profile"), let sdk = number("sdk") else { return nil }
        return BundleStamp(version: String(version), protocolVersion: p, profileVersion: pr, sdkVersion: sdk)
    }

    /// The error a validator's build throws on bundles it refuses.
    public struct Refused: Error, CustomStringConvertible {
        public let reason: String
        public var description: String { reason }
    }

    /// Why this glue refuses a bundle (`bundle-mismatch`), or nil when it runs it. `name` is the bundle's file name.
    public static func refusal(_ source: String, name: String) -> String? {
        let fix = "the bundles and the glue must come from one release (rebuild the bundles with pnpm build:ios)"
        guard let stamp = parse(source) else { return "\(name) has no version stamp, this glue is \(glue): \(fix)" }
        if stamp.protocolVersion == glue.protocolVersion, stamp.profileVersion == glue.profileVersion, stamp.sdkVersion == glue.sdkVersion { return nil }
        return "\(name) is \(stamp), this glue is \(glue): \(fix)"
    }
}

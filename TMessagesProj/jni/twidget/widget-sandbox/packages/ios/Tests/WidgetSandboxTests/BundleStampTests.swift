import XCTest
@testable import WidgetSandbox

/// VERSION.json's stamp on the bundles (BundleStamp.swift): the shipped bundles carry this glue's, and the engine
/// and the validator refuse bundles of another protocol, profile or sdk, or without a stamp, before anything runs.
final class BundleStampTests: XCTestCase {
    private let shared = SandboxResources.shared

    /// `source` with its first line replaced by `line` (nil: removed).
    private func restamped(_ source: String, _ line: String?) -> String {
        let rest = source.utf8.firstIndex(of: 0x0A).map { String(source[source.index(after: $0)...]) } ?? ""
        return line.map { $0 + "\n" + rest } ?? rest
    }

    private func resources(sandbox: String? = nil, validator: String? = nil, renderer: String? = nil) -> SandboxResources {
        SandboxResources(sandboxBundle: sandbox ?? shared.sandboxBundle, rendererBundle: renderer ?? shared.rendererBundle,
                         validatorBundle: validator ?? shared.validatorBundle, sandboxBytecode: shared.sandboxBytecode, validatorBytecode: shared.validatorBytecode)
    }

    private func stamp(protocol p: Int = WidgetSandboxVersion.protocolVersion, profile: Int = WidgetSandboxVersion.profileVersion,
                       sdk: Int = WidgetSandboxVersion.sdkVersion, version: String = "9.9.9") -> String {
        BundleStamp(version: version, protocolVersion: p, profileVersion: profile, sdkVersion: sdk).line
    }

    func testShippedBundlesCarryThisGluesStamp() {
        XCTAssertNil(shared.refusal)
        for (name, source) in [("sandbox", shared.sandboxBundle), ("validator", shared.validatorBundle), ("renderer", shared.rendererBundle)] {
            XCTAssertEqual(BundleStamp.parse(source), BundleStamp.glue, "\(name)-bundle.js is not this release's; run pnpm build:ios")
        }
        XCTAssertEqual(SdkPolicy.profileVersion, WidgetSandboxVersion.profileVersion)
    }

    func testParse() {
        XCTAssertEqual(BundleStamp.parse("/* widget-sandbox version=1.2.3-rc.1 protocol=7 profile=2 sdk=3 later=x */\n\"use strict\";"),
                       BundleStamp(version: "1.2.3-rc.1", protocolVersion: 7, profileVersion: 2, sdkVersion: 3))
        XCTAssertEqual(BundleStamp.parse(BundleStamp.glue.line), BundleStamp.glue)
        for line in [
            "\"use strict\";(()=>{", "/* widget-sandbox */", "/* widget-sandbox version=0.6.0 protocol=1 profile=1 */",
            "/* widget-sandbox version=0.6.0 protocol=x profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0 protocol=-1 profile=1 sdk=1 */",
            "/* widget-sandbox version= protocol=1 profile=1 sdk=1 */", "/* widget-sandbox version=0.6.0  protocol=1 profile=1 sdk=1 */",
            "/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1", "\n/* widget-sandbox version=0.6.0 protocol=1 profile=1 sdk=1 */",
            "/* widget-sandbox version=0.6.0 protocol=1234567 profile=1 sdk=1 */",
        ] {
            XCTAssertNil(BundleStamp.parse(line), line)
        }
    }

    func testRefusalNamesBothSides() throws {
        XCTAssertNil(BundleStamp.refusal(stamp(version: "0.0.1"), name: "x.js"), "another release with the same numbers runs")
        let other = try XCTUnwrap(BundleStamp.refusal(stamp(protocol: WidgetSandboxVersion.protocolVersion + 1), name: "sandbox-bundle.js"))
        XCTAssertTrue(other.hasPrefix("sandbox-bundle.js is widget-sandbox 9.9.9 (protocol \(WidgetSandboxVersion.protocolVersion + 1),"), other)
        XCTAssertTrue(other.contains("this glue is widget-sandbox \(WidgetSandboxVersion.version) (protocol \(WidgetSandboxVersion.protocolVersion),"), other)
        XCTAssertNotNil(BundleStamp.refusal(stamp(profile: WidgetSandboxVersion.profileVersion + 1), name: "x.js"))
        XCTAssertNotNil(BundleStamp.refusal(stamp(sdk: WidgetSandboxVersion.sdkVersion + 1), name: "x.js"))
        let none = try XCTUnwrap(BundleStamp.refusal("\"use strict\";", name: "validator-bundle.js"))
        XCTAssertTrue(none.hasPrefix("validator-bundle.js has no version stamp"), none)
        XCTAssertTrue(resources(renderer: restamped(shared.rendererBundle, stamp(sdk: 99))).refusal?.hasPrefix("renderer-bundle.js is ") == true)
    }

    private func config() -> SandboxConfig {
        var c = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        c.memoryLimitBytes = 64 << 20
        return c
    }

    /// The engine dies at boot with `bundle-mismatch`, before any widget code runs, prewarmed or not.
    func testEngineRefusesBundlesOfAnotherRelease() {
        let cases: [(String, SandboxResources)] = [
            ("protocol", resources(sandbox: restamped(shared.sandboxBundle, stamp(protocol: WidgetSandboxVersion.protocolVersion + 1)))),
            ("no stamp", resources(sandbox: restamped(shared.sandboxBundle, nil))),
            ("validator's profile", resources(validator: restamped(shared.validatorBundle, stamp(profile: WidgetSandboxVersion.profileVersion + 1)))),
            ("renderer's sdk", resources(renderer: restamped(shared.rendererBundle, stamp(sdk: WidgetSandboxVersion.sdkVersion + 1)))),
        ]
        for (label, res) in cases {
            for prewarm in [false, true] {
                let d = RecordingDelegate()
                let engine = SandboxEngine(html: "<p>x</p><script>TelegramWidget.ready(); console.log('widget ran');</script>", config: config(), resources: res)
                engine.delegate = d
                if prewarm {
                    engine.prewarm()
                    engine.queue.sync {}
                    XCTAssertFalse(engine.isPrewarmed, label)
                }
                engine.start()
                wait(for: [d.loaded], timeout: 20)
                engine.queue.sync {}
                d.lock.lock()
                XCTAssertEqual(d.died, "sandbox bundle failed to load", label)
                XCTAssertEqual(d.diags.filter { $0.0 == "bundle-mismatch" }.count, 1, "\(label): \(d.diags)")
                XCTAssertEqual(d.diags.first { $0.0 == "bundle-mismatch" }?.1, res.refusal, label)
                XCTAssertFalse(d.logs.contains { $0.1.contains("widget ran") }, label)
                XCTAssertTrue(d.ops.isEmpty, label)
                d.lock.unlock()
                engine.terminate()
            }
        }
    }

    func testValidatorRefusesBundlesOfAnotherRelease() throws {
        let res = resources(validator: restamped(shared.validatorBundle, stamp(profile: WidgetSandboxVersion.profileVersion + 1)))
        var diags: [(String, String)] = []
        XCTAssertThrowsError(try TrustedValidator(theme: "light", resources: res) { k, d in diags.append((k, d)) }) { error in
            XCTAssertTrue(error is BundleStamp.Refused, "\(error)")
        }
        XCTAssertEqual(diags.map(\.0), ["bundle-mismatch"])
        XCTAssertEqual(diags.first?.1, res.refusal)
        // This release's bundles build.
        XCTAssertNoThrow(try TrustedValidator(theme: "light", resources: shared) { _, _ in })
    }
}

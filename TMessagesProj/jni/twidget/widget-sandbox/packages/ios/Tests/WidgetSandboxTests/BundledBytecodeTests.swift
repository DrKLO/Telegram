import XCTest
@testable import WidgetSandbox

/// The shipped `.qbc` files are exactly what this engine compiles from the shipped
/// sources: a QuickJS bump or a `.js` change without `pnpm build:*` fails here, not on a device.
final class BundledBytecodeTests: XCTestCase {
    private func assertMatches(_ name: String, source: String?, bytecode: Data?, file: StaticString = #filePath, line: UInt = #line) throws {
        let source = try XCTUnwrap(source, name, file: file, line: line)
        let bytecode = try XCTUnwrap(bytecode, name + " bytecode", file: file, line: line)
        let js = QuickJSContext(memoryLimitBytes: 256 << 20, maxStackBytes: JSThread.jsStackLimit)
        let fresh = try js.compileBytecode(source, filename: name)
        XCTAssertEqual(bytecode, fresh, "\(name)'s .qbc doesn't match this engine and source; run pnpm build:ios", file: file, line: line)
    }

    func testShippedBytecodeMatchesTheEngine() throws {
        let r = SandboxResources.shared
        try assertMatches("sandbox-bundle.js", source: r.sandboxBundle, bytecode: r.sandboxBytecode)
        try assertMatches("validator-bundle.js", source: r.validatorBundle, bytecode: r.validatorBytecode)
    }

    /// The DOM wrappers read `arguments` only in strict mode (no mapped arguments object; quickjs-ng#1400).
    func testSandboxShimWrappersDoNotUseMappedArguments() throws {
        let source = SandboxResources.shared.sandboxBundle
        let strict = try NSRegularExpression(pattern: #"function\(\w+,\w+\)\{"use strict";let \w+=arguments\.length"#)
        let sloppy = try NSRegularExpression(pattern: #"function\(\w+,\w+\)\{let \w+=arguments\.length"#)
        let range = NSRange(source.startIndex..., in: source)
        XCTAssertNotNil(strict.firstMatch(in: source, range: range))
        XCTAssertNil(sloppy.firstMatch(in: source, range: range))
    }
}

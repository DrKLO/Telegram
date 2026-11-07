import XCTest
@testable import TwidgetRenderer
import WidgetSandboxProtocol

final class CanvasBatchTests: XCTestCase {
    func testBatchesDecodeLikeTheirCommandArrays() {
        var rng = SystemRandomNumberGenerator()
        let strings = ["fillRect", "fillStyle", "linear", "moveTo", "red", "hsl(1,2%,3%)", "", "arc", "save", "pattern", "M0 0"]
        var decoded = 0
        for _ in 0..<5000 {
            var words: [Double] = []
            for _ in 0..<Int.random(in: 1...12, using: &rng) {
                let kind = Double(Int.random(in: 0..<7, using: &rng))
                let hasName = kind == 0 || kind == 1 || kind == 3 || kind == 6
                words += [kind, hasName ? Double(Int.random(in: 0..<strings.count, using: &rng)) : -1, [1, 2.5, .nan, 7][Int.random(in: 0..<4, using: &rng)]]
                let argc = Int.random(in: 0...5, using: &rng)
                words.append(Double(argc))
                for _ in 0..<argc {
                    let tag = Int.random(in: 0..<8, using: &rng)
                    switch tag {
                    case 1: words += [1, Double(Int.random(in: 0..<strings.count, using: &rng))]
                    case 5: words += [5, 3, 1, .infinity, 2]
                    default: words += [Double(tag), [0, 1, -3.5, .nan, 1e20][Int.random(in: 0..<5, using: &rng)]]
                    }
                }
            }
            let batch = WidgetCanvasBatch(words: words, strings: strings)
            let a = CanvasStream(batch: batch), b = CanvasStream(commands: batch.commands())
            XCTAssertEqual(a.ops.count, b.ops.count)
            for (x, y) in zip(a.ops, b.ops) {
                XCTAssertEqual(x.kind, y.kind)
                XCTAssertEqual(x.name, y.name)
                XCTAssertTrue(x.ident == y.ident || (x.ident.isNaN && y.ident.isNaN), "\(x.ident) \(y.ident)")
                XCTAssertEqual(Array(a.arguments(x)), Array(b.arguments(y)))
            }
            decoded += a.ops.count
        }
        XCTAssertGreaterThan(decoded, 10000)
    }

    func testMalformedBatchesStopCleanly() {
        var rng = SystemRandomNumberGenerator()
        for _ in 0..<20_000 {
            let words = (0..<Int.random(in: 0..<30, using: &rng)).map { _ in [Double.nan, .infinity, -1, 0, 1, 3, 5, 7, 64, 1e9, 0.5][Int.random(in: 0..<11, using: &rng)] }
            let batch = WidgetCanvasBatch(words: words, strings: ["a"])
            _ = CanvasStream(batch: batch)
            _ = batch.commands()
            _ = batch.json(number: { "\($0)" })
        }
    }
}

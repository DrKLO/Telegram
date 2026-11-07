import XCTest
import LiteHTML

final class PaintExclusionCacheTests: XCTestCase {
    private struct Pixels: Equatable {
        let width: Int
        let height: Int
        let bytes: Data
    }

    private enum Kind: CaseIterable, Equatable {
        case paint, text, layer, background, scroll

        func apply(_ values: Set<String>, attribute: String, to document: LHDocument) {
            switch self {
            case .paint: document.setPaintExclusions(values, attribute: attribute)
            case .text: document.setTextPaintExclusions(values, attribute: attribute)
            case .layer: document.setLayerExclusions(values, attribute: attribute)
            case .background: document.setBackgroundImageExclusions(values, attribute: attribute)
            case .scroll: document.setScrollContentExclusions(values, attribute: attribute)
            }
        }
    }

    private let width: CGFloat = 160
    private let rect = CGRect(x: 0, y: 0, width: 160, height: 160)

    private var html: String {
        return "<style>body{margin:0;background:white;font:14px/20px sans-serif}.owner{width:140px;height:60px;margin:4px;overflow:hidden;background-color:#ccc;background-image:linear-gradient(90deg,#ff000080,#0000ff80)}.tall{height:160px;background:#20a060}</style><div class='owner' id='a' data-key='shared' data-alt='other'>Alpha<div id='tall-a' class='tall' style='height:160px;background:#20a060'></div></div><div class='owner' id='b' data-key='shared' data-alt='shared'>Bravo<div id='tall-b' class='tall' style='height:160px;background:#20a060'></div></div>"
    }

    private func page(_ source: String? = nil) -> LHDocument {
        let document = LHDocument(html: source ?? html, css: nil, previous: nil)
        _ = document.layout(maxWidth: width, preferredWidth: width)
        return document
    }

    private func pixels(_ image: CGImage?) throws -> Pixels {
        let image = try XCTUnwrap(image)
        let source = try XCTUnwrap(image.dataProvider?.data) as Data
        let rowBytes = image.width * image.bitsPerPixel / 8
        var bytes = Data()
        bytes.reserveCapacity(rowBytes * image.height)
        for row in 0..<image.height {
            let start = row * image.bytesPerRow
            bytes.append(source[start..<(start + rowBytes)])
        }
        return Pixels(width: image.width, height: image.height, bytes: bytes)
    }

    private func tile(_ document: LHDocument, scale: CGFloat = 2) throws -> Pixels {
        return try pixels(document.copyTileImage(maxWidth: width, rect: rect, scale: scale))
    }

    private func snapshot(_ document: LHDocument, scale: CGFloat = 2) throws -> Pixels {
        return try pixels(document.copyImage(maxWidth: width, rect: rect, scale: scale))
    }

    private func element(_ document: LHDocument, _ id: String, scale: CGFloat = 2) throws -> Pixels {
        var ink = CGRect.zero, box = CGRect.zero
        return try pixels(document.copyImageOfElement(attribute: "id", value: id, scale: scale, inkBounds: &ink, borderBox: &box))
    }

    private func scroll(_ document: LHDocument, _ id: String, scale: CGFloat = 2) throws -> Pixels {
        return try pixels(document.copyScrollContent(attribute: "id", value: id, rect: CGRect(x: 0, y: 0, width: 140, height: 60), scale: scale))
    }

    private func assertUncachedParity(_ document: LHDocument, file: StaticString = #filePath, line: UInt = #line, capture: () throws -> [Pixels]) throws {
        let first = try capture()
        let warm = try capture()
        XCTAssertEqual(warm, first, file: file, line: line)
        document.setPaintExclusionCacheDisabled(true)
        defer { document.setPaintExclusionCacheDisabled(false) }
        let uncached = try capture()
        XCTAssertEqual(warm, uncached, file: file, line: line)
    }

    func testWarmTileCacheReusesResolutionAfterEquivalentSetters() throws {
        let document = page()
        document.setPaintExclusions(["tall-b"], attribute: "id")
        document.setScrollContentExclusions(["a", "b"], attribute: "id")
        let first = try tile(document)
        let initial = document.paintExclusionCacheStats
        XCTAssertEqual(try tile(document), first)
        let warm = document.paintExclusionCacheStats
        XCTAssertGreaterThan(try XCTUnwrap(warm["hits"]).intValue, try XCTUnwrap(initial["hits"]).intValue)
        XCTAssertEqual(warm["misses"], initial["misses"])
        document.setPaintExclusions(Set(["tall-b"]), attribute: String("id"))
        document.setScrollContentExclusions(Set(["b", "a"]), attribute: String("id"))
        XCTAssertEqual(try tile(document), first)
        let repeated = document.paintExclusionCacheStats
        XCTAssertGreaterThan(try XCTUnwrap(repeated["hits"]).intValue, try XCTUnwrap(warm["hits"]).intValue)
        XCTAssertEqual(repeated["misses"], warm["misses"])
        document.setPaintExclusionCacheDisabled(true)
        XCTAssertEqual(try tile(document), first)
        XCTAssertEqual(document.paintExclusionCacheStats["hits"], repeated["hits"])
        XCTAssertEqual(document.paintExclusionCacheStats["entries"]?.intValue, 0)
    }

    func testNormalTilesReuseTheirCacheAcrossFullSnapshots() throws {
        for kind in [Kind.layer, .background] {
            let document = page()
            document.setScrollContentExclusions(["a"], attribute: "id")
            let completeSnapshot = try snapshot(document)
            kind.apply(["shared"], attribute: "data-key", to: document)
            let firstTile = try tile(document)
            let initial = document.paintExclusionCacheStats
            XCTAssertNotEqual(firstTile, completeSnapshot, "\(kind)")
            XCTAssertEqual(try tile(document), firstTile)
            let warmTile = document.paintExclusionCacheStats
            XCTAssertEqual(try XCTUnwrap(warmTile["hits"]).intValue, try XCTUnwrap(initial["hits"]).intValue + 1)
            XCTAssertEqual(warmTile["misses"], initial["misses"])
            XCTAssertEqual(try snapshot(document), completeSnapshot)
            let firstSnapshot = document.paintExclusionCacheStats
            XCTAssertEqual(firstSnapshot["hits"], warmTile["hits"])
            XCTAssertEqual(try XCTUnwrap(firstSnapshot["misses"]).intValue, try XCTUnwrap(warmTile["misses"]).intValue + 1)
            XCTAssertEqual(try tile(document), firstTile)
            let returnedTile = document.paintExclusionCacheStats
            XCTAssertEqual(try XCTUnwrap(returnedTile["hits"]).intValue, try XCTUnwrap(firstSnapshot["hits"]).intValue + 1)
            XCTAssertEqual(returnedTile["misses"], firstSnapshot["misses"])
            XCTAssertEqual(try snapshot(document), completeSnapshot)
            let warmSnapshot = document.paintExclusionCacheStats
            XCTAssertEqual(try XCTUnwrap(warmSnapshot["hits"]).intValue, try XCTUnwrap(returnedTile["hits"]).intValue + 1)
            XCTAssertEqual(warmSnapshot["misses"], returnedTile["misses"])
        }
    }

    func testEveryExclusionKindHandlesDuplicateValuesAndAttributeChanges() throws {
        for kind in Kind.allCases {
            for scale: CGFloat in [1, 2] {
                let document = page()
                let plainTile = try tile(document, scale: scale)
                let plainSnapshot = try snapshot(document, scale: scale)
                kind.apply(["shared"], attribute: "data-key", to: document)
                let excluded = try tile(document, scale: scale)
                XCTAssertNotEqual(excluded, plainTile, "\(kind), scale=\(scale)")
                try assertUncachedParity(document) {
                    return try [tile(document, scale: scale), element(document, "b", scale: scale), scroll(document, "a", scale: scale), element(document, "a", scale: scale), snapshot(document, scale: scale), tile(document, scale: scale)]
                }
                if kind == .layer || kind == .background {
                    XCTAssertEqual(try snapshot(document, scale: scale), plainSnapshot)
                }
                kind.apply(["shared"], attribute: "data-key", to: document)
                XCTAssertEqual(try tile(document, scale: scale), excluded)
                kind.apply(["shared"], attribute: "data-alt", to: document)
                let alternate = try tile(document, scale: scale)
                XCTAssertNotEqual(alternate, excluded, "\(kind), scale=\(scale)")
                try assertUncachedParity(document) { return try [tile(document, scale: scale), scroll(document, "b", scale: scale), tile(document, scale: scale)] }
                kind.apply(["other"], attribute: "data-alt", to: document)
                XCTAssertNotEqual(try tile(document, scale: scale), alternate, "\(kind), scale=\(scale)")
                try assertUncachedParity(document) { return try [tile(document, scale: scale), snapshot(document, scale: scale)] }
                kind.apply([], attribute: "data-alt", to: document)
                XCTAssertEqual(try tile(document, scale: scale), plainTile)
            }
        }
    }

    func testTemporaryPaintExclusionsRestoreBothValuesAndAttributes() throws {
        let document = page()
        document.setPaintExclusions(["tall-a"], attribute: "id")
        try assertUncachedParity(document) {
            let before = try self.snapshot(document)
            let sameAttribute = try self.pixels(document.copyImage(maxWidth: self.width, rect: self.rect, scale: 2, excluding: ["tall-b"], attribute: "id"))
            let afterSame = try self.snapshot(document)
            let differentAttribute = try self.pixels(document.copyImage(maxWidth: self.width, rect: self.rect, scale: 2, excluding: ["shared"], attribute: "data-alt"))
            let afterDifferent = try self.snapshot(document)
            XCTAssertNotEqual(sameAttribute, before)
            XCTAssertNotEqual(differentAttribute, before)
            XCTAssertEqual(afterSame, before)
            XCTAssertEqual(afterDifferent, before)
            return try [before, sameAttribute, afterSame, differentAttribute, afterDifferent, self.element(document, "a"), self.scroll(document, "b")]
        }
    }

    func testScopedScrollAndElementCapturesRestoreCanonicalOwnership() throws {
        let source = "<style>body{margin:0;background:white;font:14px/20px sans-serif}.outer{width:140px;height:60px;overflow:hidden;background:linear-gradient(90deg,#aaa,#eee)}.inner{height:40px;overflow:hidden;background:lime}.row{height:180px;background:red}</style><div id='a' class='outer'>Alpha<div id='nested' class='inner'><div id='red' class='row'></div></div><div id='blue' class='row' style='background:blue'></div></div><div id='b' class='outer'>Bravo<div id='other' class='row'></div></div>"
        let document = page(source)
        document.setScrollContentExclusions(["a", "nested", "b"], attribute: "id")
        document.setPaintExclusions(["blue"], attribute: "id")
        document.setTextPaintExclusions(["a"], attribute: "id")
        document.setLayerExclusions(["other"], attribute: "id")
        document.setBackgroundImageExclusions(["a", "b"], attribute: "id")
        _ = document.setScrollOffset(CGPoint(x: 0, y: 30), attribute: "id", value: "a")
        _ = document.setScrollOffset(CGPoint(x: 0, y: 15), attribute: "id", value: "nested")
        try assertUncachedParity(document) {
            let initial = try self.tile(document)
            var captures = [initial]
            for id in ["a", "b", "nested", "a", "b"] {
                captures.append(try self.scroll(document, id))
                captures.append(try self.element(document, id))
                let after = try self.tile(document)
                XCTAssertEqual(after, initial)
                captures.append(after)
            }
            XCTAssertEqual((document.scrollers(byAttribute: "id")["a"]?["offset"] as? NSValue)?.pointValue.y, 30)
            XCTAssertEqual((document.scrollers(byAttribute: "id")["nested"]?["offset"] as? NSValue)?.pointValue.y, 15)
            return captures
        }
    }

    func testConsumedSourceRejectsAsyncCaptureAndRestoresItsOwnCachedExclusions() throws {
        let original = page()
        original.setPaintExclusions(["tall-b"], attribute: "id")
        original.setTextPaintExclusions(["a"], attribute: "id")
        original.setScrollContentExclusions(["b"], attribute: "id")
        let originalPixels = try tile(original)
        _ = try scroll(original, "a")
        _ = try element(original, "a")
        let patched = try XCTUnwrap(LHDocument(previous: original, styles: ["tall-a": "height:220px;background:blue"], attribute: "id", css: nil))
        XCTAssertTrue(patched.reusedPrevious)
        var shouldContinueCalls = 0
        XCTAssertNil(original.copyScrollContentIfCurrent(attribute: "id", value: "a", rect: CGRect(x: 0, y: 0, width: 140, height: 60), scale: 2, shouldContinue: {
            shouldContinueCalls += 1
            return true
        }))
        XCTAssertEqual(shouldContinueCalls, 0)
        _ = patched.layout(maxWidth: width, preferredWidth: width)
        patched.setPaintExclusions(["tall-a"], attribute: "id")
        patched.setTextPaintExclusions(["b"], attribute: "id")
        patched.setScrollContentExclusions(["a"], attribute: "id")
        try assertUncachedParity(patched) { return try [tile(patched), scroll(patched, "a"), element(patched, "b"), snapshot(patched)] }
        XCTAssertEqual(try tile(original), originalPixels)
        try assertUncachedParity(original) { return try [tile(original), scroll(original, "a"), element(original, "b"), snapshot(original)] }
        XCTAssertNotNil(original.copyScrollContentIfCurrent(attribute: "id", value: "a", rect: CGRect(x: 0, y: 0, width: 140, height: 60), scale: 2, shouldContinue: { true }))
        let changed = LHDocument(html: patched.html.replacingOccurrences(of: "Bravo", with: "Changed text"), css: nil, previous: patched)
        _ = changed.layout(maxWidth: width, preferredWidth: width)
        changed.setTextPaintExclusions(["b"], attribute: "id")
        changed.setPaintExclusions(["tall-a"], attribute: "id")
        try assertUncachedParity(changed) { return try [tile(changed), element(changed, "b"), scroll(changed, "b")] }
    }

    func testPointerRestyleRechecksBackdropScope() throws {
        let source = "<style>body{margin:0;width:160px;height:160px;background:blue}#red{position:absolute;inset:0;background:red}#panel{position:absolute;left:0;top:0;width:100px;height:100px}#glass{width:100px;height:100px;background:#ffffff22}#panel:hover #glass{backdrop-filter:blur(3px)}</style><div id='red'></div><div id='panel'><div id='glass'></div></div>"
        let document = page(source)
        document.setPaintExclusions(["red"], attribute: "id")
        let before = try element(document, "panel")
        _ = try element(document, "panel")
        XCTAssertTrue(document.mouseMoved(CGPoint(x: 50, y: 50)))
        let hovered = try element(document, "panel")
        XCTAssertNotEqual(hovered, before)
        try assertUncachedParity(document) { return try [element(document, "panel"), tile(document), element(document, "panel"), snapshot(document)] }
        _ = document.mouseExited()
        XCTAssertEqual(try element(document, "panel"), before)
        try assertUncachedParity(document) { return try [element(document, "panel"), tile(document)] }
    }

    func testScopedCapturesPreserveRootCacheAcrossRelayout() throws {
        let sections = (0..<80).map { index in
            return "<section id='p\(index)'><div data-key='shared' style='width:50%;height:8px;background:red'></div><div style='height:40px;background:blue'></div></section>"
        }.joined()
        let document = page("<style>body{margin:0}section{width:100%;height:20px;overflow:hidden;background:#e0c040}</style>" + sections)
        document.setPaintExclusions(["shared"], attribute: "data-key")
        document.setScrollContentExclusions(Set((0..<80).map { "p\($0)" }), attribute: "id")
        document.setLayerExclusions(["p0"], attribute: "id")
        for layoutWidth: CGFloat in [160, 220, 160] {
            _ = document.layout(maxWidth: layoutWidth, preferredWidth: layoutWidth)
            let rootRect = CGRect(x: 0, y: 0, width: layoutWidth, height: 160)
            var referenceKeys: [Data]?
            try assertUncachedParity(document) {
                let normal = try self.pixels(document.copyTileImage(maxWidth: layoutWidth, rect: rootRect, scale: 1))
                let snapshot = try self.pixels(document.copyImage(maxWidth: layoutWidth, rect: rootRect, scale: 1))
                XCTAssertNotEqual(normal, snapshot)
                let primed = document.paintExclusionCacheStats
                var captures: [Pixels] = [normal, snapshot]
                var localKeys: [Data] = []
                for index in Array(0..<80) + [0, 79, 1] {
                    captures.append(try self.element(document, "p\(index)", scale: 1))
                    captures.append(try self.scroll(document, "p\(index)", scale: 1))
                    localKeys.append(try XCTUnwrap(document.localPaintKey(attribute: "id", value: "p\(index)", scale: 1)))
                }
                if let referenceKeys = referenceKeys {
                    XCTAssertEqual(localKeys, referenceKeys)
                } else {
                    referenceKeys = localKeys
                }
                let afterScopes = document.paintExclusionCacheStats
                XCTAssertEqual(afterScopes["hits"], primed["hits"])
                XCTAssertEqual(afterScopes["entries"], primed["entries"])
                XCTAssertEqual(afterScopes["pointers"], primed["pointers"])
                XCTAssertLessThanOrEqual(afterScopes["entries"]?.intValue ?? Int.max, 2)
                XCTAssertLessThanOrEqual(afterScopes["pointers"]?.intValue ?? Int.max, 4096)
                let returnedNormal = try self.pixels(document.copyTileImage(maxWidth: layoutWidth, rect: rootRect, scale: 1))
                let returnedSnapshot = try self.pixels(document.copyImage(maxWidth: layoutWidth, rect: rootRect, scale: 1))
                XCTAssertEqual(returnedNormal, normal)
                XCTAssertEqual(returnedSnapshot, snapshot)
                captures.append(contentsOf: [returnedNormal, returnedSnapshot])
                if primed["entries"]?.intValue == 2 {
                    let returned = document.paintExclusionCacheStats
                    XCTAssertEqual(try XCTUnwrap(returned["hits"]).intValue, try XCTUnwrap(afterScopes["hits"]).intValue + 2)
                    XCTAssertEqual(returned["misses"], afterScopes["misses"])
                }
                return captures
            }
        }
    }
}

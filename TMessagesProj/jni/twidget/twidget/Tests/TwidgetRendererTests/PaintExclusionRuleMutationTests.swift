import XCTest
import LiteHTML

final class PaintExclusionRuleMutationTests: XCTestCase {
    private struct Rule {
        var values: Set<String> = []
        var attribute = "id"
    }

    private struct Configuration {
        var paint = Rule()
        var layer = Rule()
        var background = Rule()
        var scroll = Rule()
        var text = Rule()

        static func shared(_ rule: Rule) -> Configuration {
            return Configuration(paint: rule, layer: rule, background: rule, scroll: rule, text: rule)
        }

        func apply(to document: LHDocument, reversed: Bool = false) {
            let setters: [() -> Void] = [
                { document.setPaintExclusions(self.paint.values, attribute: self.paint.attribute) },
                { document.setLayerExclusions(self.layer.values, attribute: self.layer.attribute) },
                { document.setBackgroundImageExclusions(self.background.values, attribute: self.background.attribute) },
                { document.setScrollContentExclusions(self.scroll.values, attribute: self.scroll.attribute) },
                { document.setTextPaintExclusions(self.text.values, attribute: self.text.attribute) },
            ]
            for setter in reversed ? Array(setters.reversed()) : setters {
                setter()
            }
        }
    }

    private struct Pixels: Equatable {
        let width: Int
        let height: Int
        let bytes: Data
    }

    private struct Captures: Equatable {
        let tile: Pixels
        let snapshot: Pixels
        let elements: [Pixels]
        let scrolls: [Pixels]
        let localKeys: [Data?]
        let eligibleLocalKeys: [Data]
    }

    private let width: CGFloat = 180
    private let rect = CGRect(x: 0, y: 0, width: 180, height: 280)

    private var source: String {
        let owners = (0..<3).map { index in
            let a = index == 2 ? "other" : "shared"
            let b = index == 2 ? "shared" : "other"
            return "<section id='owner\(index)' data-a='\(a)' data-b='\(b)'>Owner \(index)<div id='paint\(index)' data-a='leaf' data-b='shared' class='paint'></div><div id='layer\(index)' data-a='shared' data-b='leaf' class='layer'></div><div id='nested\(index)' data-a='nested' data-b='shared' class='nested'><div class='fill'></div></div><div class='tail'></div></section>"
        }.joined()
        return "<style>body{margin:0;background:white;font:14px/20px sans-serif}section{width:160px;height:80px;margin:4px;overflow:hidden;background-color:#ccc;background-image:linear-gradient(90deg,#ff000080,#0000ff80)}.paint{height:12px;background:#e43228}.layer{height:12px;background:#20a060}.nested{height:20px;overflow:hidden;background:#e0c040}.fill{height:100px;background:#1457a8}.tail{height:100px;background:#603090}</style>" + owners
    }

    private func page(_ configuration: Configuration = Configuration()) -> LHDocument {
        let document = LHDocument(html: source, css: nil, previous: nil)
        _ = document.layout(maxWidth: width, preferredWidth: width)
        configuration.apply(to: document)
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

    private func snapshot(_ document: LHDocument) throws -> Pixels {
        return try pixels(document.copyImage(maxWidth: width, rect: rect, scale: 2))
    }

    private func capture(_ document: LHDocument) throws -> Captures {
        let tile = try pixels(document.copyTileImage(maxWidth: width, rect: rect, scale: 2))
        let snapshot = try self.snapshot(document)
        var elements: [Pixels] = []
        var scrolls: [Pixels] = []
        var localKeys: [Data?] = []
        for id in ["owner0", "owner2", "nested1", "owner1", "owner0"] {
            var ink = CGRect.zero, box = CGRect.zero
            elements.append(try pixels(document.copyImageOfElement(attribute: "id", value: id, scale: 2, inkBounds: &ink, borderBox: &box)))
            scrolls.append(try pixels(document.copyScrollContent(attribute: "id", value: id, rect: CGRect(x: 0, y: 0, width: 160, height: 60), scale: 2)))
            localKeys.append(document.localPaintKey(attribute: "id", value: id, scale: 2))
        }
        let eligibleLocalKeys = try ["paint0", "layer1"].map { id in
            return try XCTUnwrap(document.localPaintKey(attribute: "id", value: id, scale: 2), id)
        }
        return Captures(tile: tile, snapshot: snapshot, elements: elements, scrolls: scrolls, localKeys: localKeys, eligibleLocalKeys: eligibleLocalKeys)
    }

    private var configurations: [Configuration] {
        return [
            Configuration(),
            Configuration(paint: Rule(values: ["leaf"], attribute: "data-a"), layer: Rule(values: ["leaf"], attribute: "data-b"), background: Rule(values: ["shared"], attribute: "data-a"), scroll: Rule(values: ["nested"], attribute: "data-a"), text: Rule(values: ["owner1"])),
            .shared(Rule(values: ["shared"], attribute: "data-a")),
            .shared(Rule(values: ["shared"], attribute: "data-b")),
            Configuration(paint: Rule(values: ["shared"], attribute: "data-a"), layer: Rule(values: ["shared"], attribute: "data-b"), background: Rule(values: ["other"], attribute: "data-a"), scroll: Rule(values: ["owner2", "nested0"]), text: Rule(values: ["shared"], attribute: "data-b")),
            Configuration(paint: Rule(values: ["other"], attribute: "data-a"), layer: Rule(values: ["leaf"], attribute: "data-a"), background: Rule(values: ["shared"], attribute: "data-b"), scroll: Rule(values: ["shared"], attribute: "data-a"), text: Rule(values: ["other"], attribute: "data-b")),
            .shared(Rule(values: ["shared"], attribute: "id")),
            .shared(Rule(values: [], attribute: "data-a")),
            Configuration(paint: Rule(values: ["paint0", "paint2"]), layer: Rule(values: ["layer1"]), background: Rule(values: ["owner0", "owner2"]), scroll: Rule(values: ["nested1", "owner1"]), text: Rule(values: ["owner2"])),
            Configuration(),
        ]
    }

    func testRepeatedRuleMutationsMatchFreshDocumentsAcrossEveryCapturePath() throws {
        let document = page()
        let plain = try capture(document)
        XCTAssertNil(plain.localKeys[0])
        XCTAssertTrue(plain.eligibleLocalKeys.allSatisfy { !$0.isEmpty })
        var changedStates = 0
        for (index, configuration) in (configurations + Array(configurations.reversed())).enumerated() {
            configuration.apply(to: document, reversed: index.isMultiple(of: 2))
            let expected = try capture(page(configuration))
            let actual = try capture(document)
            XCTAssertEqual(actual, expected, "configuration \(index)")
            XCTAssertEqual(try capture(document), expected, "warm configuration \(index)")
            if actual != plain {
                changedStates += 1
            }
        }
        XCTAssertGreaterThan(changedStates, 8)
        Configuration().apply(to: document)
        XCTAssertEqual(try capture(document), plain)
    }

    func testTemporaryRulesRestoreCompiledValuesAndAttributesAfterEachMutation() throws {
        let document = page()
        for (index, configuration) in configurations.enumerated() {
            configuration.apply(to: document, reversed: index.isMultiple(of: 2))
            let expected = try capture(page(configuration))
            XCTAssertEqual(try capture(document), expected)
            let temporaryRules = [
                Rule(values: ["shared"], attribute: configuration.paint.attribute),
                Rule(values: ["shared"], attribute: configuration.paint.attribute == "data-a" ? "data-b" : "data-a"),
                Rule(values: ["owner0", "paint2"], attribute: "id"),
                Rule(values: [], attribute: "data-b"),
            ]
            for temporary in temporaryRules {
                var temporaryConfiguration = configuration
                temporaryConfiguration.paint = temporary
                if temporary.attribute == configuration.paint.attribute {
                    temporaryConfiguration.paint.values.formUnion(configuration.paint.values)
                }
                let expectedTemporary = try snapshot(page(temporaryConfiguration))
                let actualTemporary = try pixels(document.copyImage(maxWidth: width, rect: rect, scale: 2, excluding: temporary.values, attribute: temporary.attribute))
                XCTAssertEqual(actualTemporary, expectedTemporary, "configuration \(index), attribute \(temporary.attribute)")
                XCTAssertEqual(try capture(document), expected, "restored configuration \(index), attribute \(temporary.attribute)")
            }
        }
    }
}

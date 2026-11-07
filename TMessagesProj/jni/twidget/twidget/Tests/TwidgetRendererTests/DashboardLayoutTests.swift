import XCTest
import LiteHTML

final class DashboardLayoutTests: XCTestCase {
    func testRightAlignedMessageKeepsItsMeasuredWidthAndHeight() {
        let doc = document("""
        <style>*{box-sizing:border-box}.thread{display:flex;flex-direction:column;gap:2px}.msg{display:flex;align-self:flex-end;flex-direction:row-reverse;max-width:86%;margin-top:6px}.bubble{display:flex;flex-wrap:wrap;align-items:flex-end;column-gap:8px;padding:7px 11px 6px;background:red}.txt{width:210px;height:20px}.time{width:50px;height:12px;margin-left:auto}</style>
        <div class=thread><div class=msg id=a><div class=bubble id=ab><span class=txt></span><span class=time></span></div></div><div class=msg id=b><div class=bubble id=bb><span class=txt></span><span class=time></span></div></div></div>
        """, width: 388)
        let first = doc.bounds(matching: "#a")
        let bubble = doc.bounds(matching: "#ab")
        let second = doc.bounds(matching: "#bb")
        XCTAssertEqual(first.width, 290, accuracy: 0.1)
        XCTAssertEqual(bubble.width, 290, accuracy: 0.1)
        XCTAssertEqual(bubble.height, 33, accuracy: 0.1)
        XCTAssertEqual(first.height, bubble.height, accuracy: 0.1)
        XCTAssertGreaterThanOrEqual(second.minY, bubble.maxY + 8)
    }

    func testColumnItemConstraintsResolveAgainstTheColumn() {
        let cases: [(String, CGFloat)] = [
            ("min-width:60%;max-width:80%", 240),
            ("width:75%;max-width:80%", 300),
            ("width:100px;min-width:60%", 240),
            ("min-width:60%;max-width:20%", 240),
            ("max-width:20%", 80)
        ]
        for align in ["flex-start", "center", "flex-end"] {
            for (constraints, width) in cases {
                let doc = document("""
                <div style='display:flex;flex-direction:column;width:400px'><div id=item style='align-self:\(align);\(constraints);height:20px;background:red'><div style='width:120px;height:10px'></div></div></div>
                """)
                let box = doc.bounds(matching: "#item")
                XCTAssertEqual(box.width, width, accuracy: 0.1, "\(align) \(constraints)")
                XCTAssertEqual(box.minX, align == "flex-start" ? 0 : align == "center" ? (400 - width) / 2 : 400 - width, accuracy: 0.1)
            }
        }
    }

    func testOwnDiscussionMessagesDoNotOverlapAcrossColumnWidths() {
        for width: CGFloat in [260, 320, 388, 480] {
            let doc = document("""
            <style>*{box-sizing:border-box}body{font:15px/1.35 ui-rounded,-apple-system}.thread{display:flex;flex-direction:column;gap:2px}.msg{display:flex;align-items:flex-end;align-self:flex-end;flex-direction:row-reverse;max-width:86%;margin-top:6px}.msg.cont{margin-top:2px}.bubble{display:flex;flex-wrap:wrap;align-items:flex-end;column-gap:8px;padding:7px 11px 6px;min-width:60px}.txt{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.3}.time{font-size:11px;margin-left:auto}</style>
            <div class=thread><div class=msg id=a><div class=bubble id=ab><span class=txt>On it, looks like a keychain race.</span><span class=time>01:03 AM</span></div></div><div class='msg cont' id=b><div class=bubble id=bb><span class=txt>Fix is in review.</span><span class=time>01:05 AM</span></div></div></div>
            """, width: width)
            let first = doc.bounds(matching: "#a")
            let firstBubble = doc.bounds(matching: "#ab")
            let secondBubble = doc.bounds(matching: "#bb")
            XCTAssertEqual(first.height, firstBubble.height, accuracy: 0.1, "\(width)")
            XCTAssertGreaterThanOrEqual(firstBubble.minY, first.minY - 0.1, "\(width)")
            XCTAssertGreaterThanOrEqual(secondBubble.minY, firstBubble.maxY + 3.9, "\(width)")
            XCTAssertLessThanOrEqual(first.width, width * 0.86 + 0.1)
        }
    }

    func testFlexMessagesRespectExplicitAndVisibleMinimumWidths() {
        for style in ["overflow:visible", "overflow:hidden;min-width:300px"] {
            let doc = document("""
            <div style='display:flex;width:200px'><span style='width:54px;flex-shrink:0'>3.2s</span><span id=msg style='white-space:nowrap;\(style)'>1 flaky retry: feed.spec.ts &gt; snapshot-and-drop</span></div>
            """)
            let message = doc.bounds(matching: "#msg")
            XCTAssertGreaterThan(message.maxX, 200)
            if style.contains("min-width") {
                XCTAssertEqual(message.width, 300, accuracy: 0.1)
            }
        }
    }

    func testDeployLogClipsShrinkingFlexMessages() throws {
        for width: CGFloat in [280, 392] {
            for overflow in ["hidden", "auto", "scroll"] {
                let doc = document("""
                <style>
                .card{width:392px;max-width:100%;box-sizing:border-box;padding:12px 14px;border:1px solid black;background:white}
                .log{padding:10px 12px;background:#0f1117;font:11.5px monospace;line-height:1.5}
                .ln{display:flex}.ts{width:54px;flex-shrink:0}.msg{white-space:nowrap;overflow:\(overflow);color:red}
                </style>
                <div class=card><div class=log><div class=ln><span class=ts>3.2s</span><span class=msg>1 flaky retry: feed.spec.ts &gt; snapshot-and-drop</span></div></div></div>
                """, width: width)
                let row = doc.bounds(matching: ".ln")
                let timestamp = doc.bounds(matching: ".ts")
                let message = doc.bounds(matching: ".msg")
                XCTAssertEqual(timestamp.width, 54, accuracy: 0.1)
                XCTAssertEqual(message.minX, timestamp.maxX, accuracy: 0.1)
                XCTAssertEqual(message.maxX, row.maxX, accuracy: 0.1, "\(width) \(overflow)")
                let image = try XCTUnwrap(doc.copyImage(maxWidth: width, rect: CGRect(x: 0, y: 0, width: width + 80, height: 60), scale: 1))
                for y in Int(message.minY)..<Int(message.maxY) {
                    for x in Int(ceil(row.maxX))..<image.width {
                        let color = pixel(image, x: x, y: y)
                        XCTAssertFalse(Int(color[0]) > Int(color[1]) + 30 && Int(color[0]) > Int(color[2]) + 30, "Text escaped at \(x),\(y): \(overflow)")
                    }
                }
            }
        }
    }

    private func document(_ html: String, width: CGFloat = 400) -> LHDocument {
        let doc = LHDocument(html: "<style>body{margin:0}</style>" + html, css: nil)
        _ = doc.layout(maxWidth: width, preferredWidth: width)
        return doc
    }

    private func pixel(_ image: CGImage, x: Int, y: Int) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: 4)
        let ctx = CGContext(data: &bytes, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(image, in: CGRect(x: -x, y: y - image.height + 1, width: image.width, height: image.height))
        return bytes
    }

    func testColorSelectorButtonCentersItsLabelAndArrow() {
        for display in ["flex", "inline-flex", "grid", "inline-grid"] {
            let doc = document("""
            <style>
            .dd{display:\(display);align-items:center;grid-template-columns:1fr auto;width:100%;height:36px;box-sizing:border-box;border:0;border-radius:10px;background:#292929;color:#fff;padding:0 14px;font:600 15px -apple-system}
            .dd span{flex:1 1 0;text-align:left;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            </style>
            <button class=dd id=dd type=button><span id=dl>Red</span><svg id=arrow width=12 height=8 viewBox='0 0 12 8'><path d='M1 1.5l5 5 5-5' fill=none stroke='#8a9199' stroke-width=2 /></svg></button>
            """)
            let button = doc.bounds(matching: "#dd")
            XCTAssertEqual(button.height, 36, accuracy: 0.1)
            XCTAssertEqual(doc.bounds(matching: "#dl").midY, button.midY, accuracy: 0.5, display)
            XCTAssertEqual(doc.bounds(matching: "#arrow").midY, button.midY, accuracy: 0.5, display)
        }
    }

    func testGridCardsFillAColumnFlexSheet() {
        for sizing in ["content-box", "border-box"] {
            for columns in ["1fr 1fr", "repeat(2,minmax(0,1fr))", "repeat(auto-fit,minmax(100px,1fr))"] {
                let doc = document("""
                <style>*{box-sizing:\(sizing)}.sheet{display:flex;flex-direction:column;width:360px;padding:10px}.grid{display:grid;grid-template-columns:\(columns);gap:10px}.card{padding:10px;background:red}</style>
                <div class=sheet><div class=grid><div class=card id=a>One</div><div class=card id=b>Two</div></div></div>
                """)
                let grid = doc.bounds(matching: ".grid")
                let a = doc.bounds(matching: "#a"), b = doc.bounds(matching: "#b")
                XCTAssertEqual(grid.width, sizing == "border-box" ? 340 : 360, accuracy: 0.1, "\(sizing) \(columns)")
                XCTAssertEqual(a.width, (grid.width - 10) / 2, accuracy: 0.1, "\(sizing) \(columns)")
                XCTAssertEqual(b.maxX, grid.maxX, accuracy: 0.1, "\(sizing) \(columns)")
            }
        }
    }

    func testWrappedBubbleNameDoesNotWidenAColumnFlexSheet() {
        let doc = document("""
        <style>*{box-sizing:border-box}.sheet{display:flex;flex-direction:column;width:420px;height:400px}.body{flex:1;display:flex;flex-direction:column;overflow-y:auto;padding:4px 16px}.body>*{flex:none}.title{width:100%;height:20px}.thread{display:flex;flex-direction:column}.msg{display:flex;max-width:86%}.bubble{display:flex;flex-wrap:wrap;column-gap:8px;padding:7px 11px}.who{flex-basis:100%}.time{margin-left:auto}</style>
        <div class=sheet><div class=body><div class=title id=t></div><div class=seg style='display:grid;grid-template-columns:repeat(4,1fr);gap:6px'><button>Urgent</button><button>High</button><button>Normal</button><button id=d>Low</button></div><div class=thread id=c><div class=msg id=m><div class=bubble><b class=who>Alex Kim</b><span>Happens on 19.1 too.</span><span class=time>10:52 AM</span></div></div></div></div></div>
        """, width: 420)
        XCTAssertEqual(doc.bounds(matching: "#t").width, 388, accuracy: 0.5)
        XCTAssertEqual(doc.bounds(matching: "#c").width, 388, accuracy: 0.5)
        XCTAssertEqual(doc.bounds(matching: "#m").width, 388 * 0.86, accuracy: 0.5)
        XCTAssertEqual(doc.bounds(matching: "#d").maxX, 404, accuracy: 0.5)
    }

    func testPaddedGridCardsKeepTheTrackWidth() {
        for sizing in ["content-box", "border-box"] {
            let doc = document("""
            <style>*{box-sizing:\(sizing)}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;width:340px;padding:20px}.card{display:grid;grid-template-columns:1fr auto;padding:12px;border:1px solid}.content{min-width:0}</style>
            <div class=grid><div class=card id=a><div class=content>A long heading for a card</div><div>12</div></div><div class=card id=b><div class=content>Second card</div><div>34</div></div></div>
            """)
            let grid = doc.bounds(matching: ".grid")
            let a = doc.bounds(matching: "#a"), b = doc.bounds(matching: "#b")
            XCTAssertEqual(a.width, (grid.width - 50) / 2, accuracy: 0.1, sizing)
            XCTAssertEqual(b.maxX, grid.maxX - 20, accuracy: 0.1, sizing)
        }
    }

    func testGridCardMinimumWidthOverridesItsLongHeading() {
        for minimum in ["min-width:0", "overflow:hidden"] {
            let doc = document("""
            <div style='display:grid;grid-template-columns:1fr 1fr;gap:10px;width:340px'><div id=a style='\(minimum);padding:10px'>AReallyLongUnbreakableDashboardCardHeadingThatMustNotWidenTheSheet</div><div id=b>Two</div></div>
            """)
            XCTAssertEqual(doc.bounds(matching: "#a").width, 165, accuracy: 0.1, minimum)
            XCTAssertEqual(doc.bounds(matching: "#b").maxX, 340, accuracy: 0.1, minimum)
        }
    }

    func testStickyHeaderScrollsInFlowThenSticksAndRemainsHittable() throws {
        let doc = document("""
        <div id=scroller style='height:100px;overflow:auto'><div style='height:40px'></div><div id=header style='position:sticky;top:5px;height:20px;background:red;z-index:1'></div><div style='height:400px;background:blue'></div></div>
        """, width: 120)
        XCTAssertEqual(doc.bounds(matching: "#header").minY, 40, accuracy: 0.1)
        _ = doc.setScrollOffset(CGPoint(x: 0, y: 80), attribute: "id", value: "scroller")
        XCTAssertEqual(doc.bounds(matching: "#header").minY, 5, accuracy: 0.1)
        let image = try XCTUnwrap(doc.copyImage(maxWidth: 120, rect: CGRect(x: 0, y: 0, width: 120, height: 100), scale: 1))
        XCTAssertEqual(pixel(image, x: 50, y: 10), [255, 0, 0, 255])
        XCTAssertEqual(doc.attribute("id", at: CGPoint(x: 50, y: 10)), "header")
        XCTAssertEqual((doc.scrollers(byAttribute: "id")["scroller"]?["sticky"] as? NSNumber)?.boolValue, true)
    }

    func testStickyHeaderStopsAtItsContainingBlock() {
        let doc = document("""
        <div id=scroller style='height:100px;overflow:auto'><div style='height:120px'><div id=header style='position:sticky;top:0;height:20px'></div></div><div style='height:400px'></div></div>
        """, width: 120)
        _ = doc.setScrollOffset(CGPoint(x: 0, y: 110), attribute: "id", value: "scroller")
        XCTAssertEqual(doc.bounds(matching: "#header").minY, -10, accuracy: 0.1)
    }

    func testClipPathsMaskBackgroundDescendantsAndHitTesting() throws {
        for shape in ["polygon(50% 0,100% 100%,0 100%)", "circle(40% at 50% 50%)", "ellipse(40% 40% at center center)", "inset(10% round 12px)"] {
            let doc = document("<div id=shape style='width:100px;height:100px;background:red;clip-path:\(shape)'><div style='width:100px;height:100px;background:blue'></div></div>", width: 100)
            let image = try XCTUnwrap(doc.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 100), scale: 1))
            XCTAssertEqual(pixel(image, x: 2, y: 2)[3], 0, shape)
            XCTAssertEqual(pixel(image, x: 50, y: 50), [0, 0, 255, 255], shape)
            XCTAssertNil(doc.attribute("id", at: CGPoint(x: 2, y: 2)), shape)
            XCTAssertEqual(doc.attribute("id", at: CGPoint(x: 50, y: 50)), "shape", shape)
        }
    }
    func testHorizontalStickyAndItsChildrenUseScrolledCoordinates() {
        let doc = document("""
        <div id=scroller style='width:100px;height:60px;overflow:auto'><div style='width:400px;height:60px'><div id=header style='position:sticky;left:5px;margin-left:30px;width:20px;height:20px;background:red'><div id=child style='height:10px'></div></div></div></div>
        """, width: 120)
        _ = doc.setScrollOffset(CGPoint(x: 80, y: 0), attribute: "id", value: "scroller")
        XCTAssertEqual(doc.bounds(matching: "#header").minX, 5, accuracy: 0.1)
        XCTAssertEqual(doc.bounds(matching: "#child").minX, 5, accuracy: 0.1)
        XCTAssertEqual(doc.attribute("id", at: CGPoint(x: 10, y: 5)), "child")
    }

    func testClipChangesInvalidateTheirPaintAndClipTheRoot() throws {
        let before = document("<div style='width:100px;height:100px;background:red;clip-path:inset(0 50% 0 0)'></div>", width: 100)
        let after = document("<div style='width:100px;height:100px;background:red;clip-path:inset(0 0 0 50%)'></div>", width: 100)
        XCTAssertFalse(after.displayList(maxWidth: 100, scale: 2).changedRects(since: before.displayList(maxWidth: 100, scale: 2)).isEmpty)
        let root = document("<style>html{clip-path:inset(10px)}</style><div style='height:100px;background:red'></div>", width: 100)
        let image = try XCTUnwrap(root.copyImage(maxWidth: 100, rect: CGRect(x: 0, y: 0, width: 100, height: 100), scale: 1))
        XCTAssertEqual(pixel(image, x: 5, y: 5)[3], 0)
        XCTAssertEqual(pixel(image, x: 50, y: 50)[3], 255)
    }

}

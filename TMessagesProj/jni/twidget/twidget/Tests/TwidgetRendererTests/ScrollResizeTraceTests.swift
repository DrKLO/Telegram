import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class ScrollResizeTraceTests: XCTestCase {
    func testCompletedResizeTraceKeepsTileIdentityOffsetsAndPixels() throws {
        let layer = WidgetScrollLayer(id: 3, geometry: WidgetScrollGeometry(viewport: .zero, content: .zero, radii: []))
        var installed = 0
        for step in 0..<24 {
            let width = CGFloat([80, 133, 300, 513, 133, 80][step % 6])
            let scale = CGFloat(step % 2 + 1)
            let geometry = WidgetScrollGeometry(viewport: CGRect(x: 0.25, y: 0.75, width: width, height: 95), content: CGSize(width: max(width, 320), height: 520), radii: Array(repeating: 7, count: 8))
            let document = LHDocument(html: "<style>body{margin:0}#scroll{width:\(width)px;height:95px;overflow:auto}#content{width:\(max(width, 320))px;height:520px;background:linear-gradient(\(step * 13)deg,#f008,#08f6);font:17px sans-serif;color:#2229;text-shadow:2px 1px 3px #0807}p{margin:13px;padding:7px;background:#fc08}</style><div id='scroll' data-key='3'><div id='content'><p>State \(step)</p><p>Every completed state</p></div></div>", css: nil)
            _ = document.layout(maxWidth: width, preferredWidth: width)
            let offset = CGPoint(x: 0, y: CGFloat((step % 3) * 83) + 0.25)
            layer.apply(geometry)
            layer.show(offset)
            layer.update(signature: step + 1)
            layer.ensureTiles(document: document, scale: scale, attribute: "data-key")
            let target = installed + layer.tileCount
            let deadline = Date().addingTimeInterval(2)
            while layer.rasterCount < target && Date() < deadline {
                RunLoop.main.run(until: Date().addingTimeInterval(0.005))
            }
            XCTAssertEqual(layer.rasterCount, target)
            installed = target
            XCTAssertEqual(layer.offset, offset)
            for tile in layer.content.sublayers ?? [] {
                let contents = try XCTUnwrap(tile.contents)
                let expected = try XCTUnwrap(document.copyScrollContent(attribute: "data-key", value: "3", rect: tile.frame.insetBy(dx: -1 / scale, dy: -1 / scale), scale: scale))
                let image = contents as! CGImage
                XCTAssertEqual(image.dataProvider?.data as Data?, expected.dataProvider?.data as Data?)
                XCTAssertEqual(tile.contentsScale, scale)
                XCTAssertEqual(tile.contentsRect.width, tile.frame.width * scale / CGFloat(expected.width))
                XCTAssertNil(tile.animationKeys())
            }
        }
        XCTAssertGreaterThan(installed, 24)
    }
}

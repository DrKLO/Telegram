import AppKit
import XCTest
import WidgetSandbox
@testable import TwidgetRenderer

final class MediaControlTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.005))
        }
    }

    func testMusicEmojiAndPixelsKeepGeometryAcrossTracksAndQueueChanges() throws {
        _ = NSApplication.shared
        let file = ProcessInfo.processInfo.environment["TW_MUSIC_HTML"] ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../../tools/perflab/widget-glitches/repaired/music-player.html").path
        let html = try String(contentsOfFile: file)
        for width in [320.0, 420.0] {
            for scheme in [WidgetTheme.ColorScheme.light, .dark] {
                let renderer = LiteHTMLWidgetRenderer(theme: scheme == .light ? "light" : "dark", width: width)
                var options = WidgetHost.Options()
                options.asyncValidation = true
                options.sharedValidatorThread = true
                options.startBeforeRendererReady = true
                options.pools = true
                options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
                EnginePool.shared.features = options.features
                let host = try WidgetHost(html: html, theme: WidgetTheme(colorScheme: scheme), width: width, options: options, rendererFactory: { _, _, _ in renderer })
                defer { host.destroy() }
                host.view.frame = CGRect(x: 0, y: 0, width: width, height: 700)
                pump(10) { host.diagnostics.rendered && renderer.document != nil }
                pump(0.35)
                func node(_ id: String) throws -> WidgetNode {
                    try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("id") == id })
                }
                func action(_ name: String) throws -> WidgetNode {
                    try XCTUnwrap(renderer.dom.nodes.values.first { $0.attribute("data-action") == name })
                }
                func rect(_ item: WidgetNode) throws -> CGRect {
                    let document = try XCTUnwrap(renderer.document)
                    return try XCTUnwrap(document.bounds(byAttribute: WidgetDOM.idAttribute)[String(renderer.dom.publicId(item))]).rectValue
                }
                func click(_ item: WidgetNode) {
                    renderer.noteUserInput()
                    renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"click\",\"targetId\":\(renderer.dom.publicId(item)),\"bubbles\":true,\"button\":0}}")
                    pump(0.06)
                }
                func advance(_ start: Double) {
                    for frame in 0..<12 {
                        renderer.postFrame(time: start + Double(frame) * 16.667)
                        pump(0.012)
                    }
                }
                func capture(_ name: String) throws -> NSBitmapImageRep {
                    let image = try XCTUnwrap(renderer.snapshot())
                    let raw = try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil))
                    let context = try XCTUnwrap(CGContext(data: nil, width: raw.width, height: raw.height, bitsPerComponent: 8, bytesPerRow: raw.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
                    context.setFillColor((scheme == .light ? NSColor.white : NSColor(srgbRed: 0.11, green: 0.11, blue: 0.12, alpha: 1)).cgColor)
                    context.fill(CGRect(x: 0, y: 0, width: raw.width, height: raw.height))
                    context.draw(raw, in: CGRect(x: 0, y: 0, width: raw.width, height: raw.height))
                    let representation = NSBitmapImageRep(cgImage: try XCTUnwrap(context.makeImage()))
                    if let path = ProcessInfo.processInfo.environment["TW_MEDIA_CONTROL_OUT"] {
                        let directory = URL(fileURLWithPath: path)
                        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                        try XCTUnwrap(representation.representation(using: .png, properties: [:])).write(to: directory.appendingPathComponent("music-\(Int(width))-\(scheme.rawValue)-\(name).png"))
                    }
                    return representation
                }
                advance(500)
                XCTAssertTrue(host.diagnostics.rendered)
                XCTAssertNil(host.diagnostics.dead)
                let art = try node("art"), title = try node("title"), play = try node("playbtn"), queue = try node("list")
                let artFrame = try rect(art), titleFrame = try rect(title), playFrame = try rect(play)
                XCTAssertEqual(artFrame.height, 64, accuracy: 0.5)
                XCTAssertEqual(titleFrame.height, 20, accuracy: 0.5)
                XCTAssertEqual(playFrame.width, 36, accuracy: 0.5)
                let text = play.children.filter(\.isText).map(\.text).joined()
                XCTAssertEqual(text, "⏸️")
                let pixels = try capture("playing")
                let canvas = try rect(node("eq"))
                let scale = CGFloat(pixels.pixelsWide) / CGFloat(width)
                var colored = 0
                for y in Int(canvas.minY * scale)..<min(pixels.pixelsHigh, Int(canvas.maxY * scale)) {
                    for x in Int(canvas.minX * scale)..<min(pixels.pixelsWide, Int(canvas.maxX * scale)) {
                        if let color = pixels.colorAt(x: x, y: y)?.usingColorSpace(.deviceRGB), color.alphaComponent > 0.5, color.redComponent > color.greenComponent + 0.1, color.redComponent > color.blueComponent + 0.1 { colored += 1 }
                    }
                }
                XCTAssertGreaterThan(colored, Int(canvas.width * scale * 6), "The equalizer must contain animated bars taller than its three-pixel idle baseline")
                let waveRect = CGRect(x: canvas.minX * scale, y: canvas.minY * scale, width: canvas.width * scale, height: canvas.height * scale)
                let firstWave = try XCTUnwrap(pixels.cgImage?.cropping(to: waveRect))
                advance(720)
                let movedPixels = try capture("playing-phase-2")
                let secondWave = try XCTUnwrap(movedPixels.cgImage?.cropping(to: waveRect))
                let firstData = NSBitmapImageRep(cgImage: firstWave).representation(using: .png, properties: [:])
                let secondData = NSBitmapImageRep(cgImage: secondWave).representation(using: .png, properties: [:])
                XCTAssertNotEqual(firstData, secondData, "Equalizer pixels must change while the same track plays")
                click(try action("queue"))
                XCTAssertNil(queue.attribute("hidden"))
                let rows = queue.children.filter { $0.attribute("data-action") == "pick" }
                XCTAssertEqual(rows.count, 5)
                let rowFrames = try rows.map(rect)
                _ = try capture("queue")
                for index in 0..<3 {
                    click(try action("next"))
                    advance(900 + Double(index) * 250)
                    XCTAssertEqual(try rect(art), artFrame)
                    XCTAssertEqual(try rect(title), titleFrame)
                    XCTAssertEqual(try rect(play), playFrame)
                    XCTAssertEqual(try rows.map(rect), rowFrames)
                    XCTAssertTrue(zip(rows, queue.children.filter { $0.attribute("data-action") == "pick" }).allSatisfy { $0.0 === $0.1 })
                    _ = try capture("track-\(index)")
                }
                click(play)
                XCTAssertEqual(play.children.filter(\.isText).map(\.text).joined(), "▶️")
                XCTAssertEqual(try rect(play), playFrame)
                click(try action("queue"))
                XCTAssertNotNil(queue.attribute("hidden"))
                XCTAssertEqual(try rect(art), artFrame)
                _ = try capture("paused-closed")
                XCTAssertEqual(host.diagnostics.logs.filter { $0.level == "error" }.map(\.text), [])
            }
        }
    }
}

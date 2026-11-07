import XCTest
import AppKit
@testable import TwidgetRenderer

final class LayoutPatchRestyleDamageTests: XCTestCase {
    private func run(_ batches: [String]) -> [Int] {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 300), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        var blocks: [() -> Void] = []
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.isOnExecutor = { Thread.isMainThread }
        renderer.executorIsMain = { false }
        renderer.schedule = { blocks.append($0) }
        renderer.scheduleAfter = { delay, block in DispatchQueue.main.asyncAfter(deadline: .now() + delay) { blocks.append(block) } }
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 300)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        defer { renderer.destroy() }
        func pump(_ seconds: TimeInterval, until done: () -> Bool) {
            let deadline = Date().addingTimeInterval(seconds)
            while !done() && Date() < deadline {
                while !blocks.isEmpty { blocks.removeFirst()() }
                RunLoop.main.run(until: Date().addingTimeInterval(0.005))
            }
        }
        var stale: [Int] = []
        for (index, batch) in batches.enumerated() {
            renderer.send("{\"type\":\"ops\",\"ops\":[\(batch)]}")
            pump(3, until: { renderer.tileLayerCount > 0 && !renderer.tilesPreparing && blocks.isEmpty })
            pump(0.05, until: { false })
            if renderer.staleTileCount > 0 {
                stale.append(index)
            }
        }
        return stale
    }

    func testClassFlipsInALayoutPatchAreRepainted() {
        let rows = 6, cols = 4
        var setup = "{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":[],\"adopt\":\"html\"},{\"op\":\"el\",\"id\":3,\"tag\":\"head\",\"attrs\":[],\"adopt\":\"head\"},{\"op\":\"el\",\"id\":4,\"tag\":\"style\",\"attrs\":[]},{\"op\":\"text\",\"id\":5,\"data\":\"body{margin:0;font:11px -apple-system,sans-serif}table{border-collapse:collapse;width:100%}td{padding:2px 4px;text-align:right}.up{color:#34c759}.down{color:#ff3b30}\"},{\"op\":\"ins\",\"parent\":4,\"id\":5,\"before\":null},{\"op\":\"ins\",\"parent\":3,\"id\":4,\"before\":null},{\"op\":\"el\",\"id\":7,\"tag\":\"body\",\"attrs\":[],\"adopt\":\"body\"},{\"op\":\"el\",\"id\":8,\"tag\":\"table\",\"attrs\":[]},{\"op\":\"ins\",\"parent\":7,\"id\":8,\"before\":null},{\"op\":\"el\",\"id\":9,\"tag\":\"tbody\",\"attrs\":[]},{\"op\":\"ins\",\"parent\":8,\"id\":9,\"before\":null}"
        for r in 0..<rows {
            let tr = 100 + r
            setup += ",{\"op\":\"el\",\"id\":\(tr),\"tag\":\"tr\",\"attrs\":[]},{\"op\":\"ins\",\"parent\":9,\"id\":\(tr),\"before\":null}"
            for c in 0..<cols {
                let td = 1000 + r * 2 * cols + c * 2
                setup += ",{\"op\":\"el\",\"id\":\(td),\"tag\":\"td\",\"attrs\":[[\"class\",\"up\"]]},{\"op\":\"text\",\"id\":\(td + 1),\"data\":\"100.00\"},{\"op\":\"ins\",\"parent\":\(td),\"id\":\(td + 1),\"before\":null},{\"op\":\"ins\",\"parent\":\(tr),\"id\":\(td),\"before\":null}"
            }
        }
        var batches = [setup]
        for step in 0..<20 {
            var ops: [String] = []
            for cell in 0..<(rows * cols) {
                let td = 1000 + cell * 2
                ops.append("{\"op\":\"attr\",\"id\":\(td),\"name\":\"class\",\"value\":\"\((cell + step) % 2 == 0 ? "down" : "up")\"}")
                ops.append("{\"op\":\"data\",\"id\":\(td + 1),\"data\":\"\(100 + cell).\(10 + step)\"}")
            }
            batches.append(ops.joined(separator: ","))
        }
        for step in 0..<10 {
            var ops: [String] = ["{\"op\":\"data\",\"id\":1001,\"data\":\"\(step % 2 == 0 ? "99.98" : "100.02")\"}"]
            for cell in 1..<(rows * cols) where (cell + step) % 2 == 0 {
                let td = 1000 + cell * 2
                ops.append("{\"op\":\"attr\",\"id\":\(td),\"name\":\"class\",\"value\":\"\(step % 4 < 2 ? "up" : "down")\"}")
            }
            batches.append(ops.joined(separator: ","))
        }
        XCTAssertEqual(run(batches), [])
    }
}

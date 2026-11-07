import XCTest
import AppKit
@testable import TwidgetRenderer

final class FrameCostBenchTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.001))
        }
    }

    private func json(_ ops: [[String: Any]]) -> String {
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        return String(data: data, encoding: .utf8)!
    }

    func testBusyWidgetFrameCost() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["TWIDGET_BENCH"] != nil)
        _ = NSApplication.shared
        let count = Int(ProcessInfo.processInfo.environment["TWIDGET_BENCH_COUNT"] ?? "") ?? 1000
        let frames = Int(ProcessInfo.processInfo.environment["TWIDGET_BENCH_FRAMES"] ?? "") ?? 300
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        defer { renderer.destroy() }
        var queue: [() -> Void] = []
        renderer.schedule = { queue.append($0) }
        var answers = 0
        renderer.onMessage = { _ in answers += 1 }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: 600)
        window.contentView?.addSubview(renderer.view)
        renderer.load()
        func drain() {
            while !queue.isEmpty {
                let items = queue
                queue = []
                items.forEach { $0() }
            }
        }
        let mode = ProcessInfo.processInfo.environment["TWIDGET_BENCH_MODE"] ?? "mixed"
        if mode == "swarm" {
            try swarm(renderer: renderer, window: window, count: count, frames: frames, queue: { let items = queue; queue = []; return items })
            return
        }
        var setup: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; font: 12px sans-serif; } .box { position: absolute; width: 18px; height: 18px; border-radius: 4px; background: #3a7; } .label { position: absolute; left: 0; color: #222; } canvas { position: absolute; left: 200px; top: 0; width: 200px; height: 200px; }"],
            ["op": "ins", "id": 2, "parent": 1],
        ]
        var next = 100
        var boxes: [Int] = []
        var texts: [Int] = []
        for i in 0..<count {
            let id = next
            next += 1
            setup.append(["op": "el", "id": id, "tag": "div", "attrs": [["class", "box"], ["style", "left:\((i % 20) * 20)px;top:\((i / 20) * 20)px"]]])
            setup.append(["op": "ins", "id": id, "parent": 1])
            boxes.append(id)
        }
        for i in 0..<30 {
            let id = next
            let text = next + 1
            next += 2
            setup.append(["op": "el", "id": id, "tag": "div", "attrs": [["class", "label"], ["style", "top:\(i * 16)px"]]])
            setup.append(["op": "ins", "id": id, "parent": 1])
            setup.append(["op": "text", "id": text, "data": "label \(i)"])
            setup.append(["op": "ins", "id": text, "parent": id])
            texts.append(text)
        }
        let canvas = next
        next += 1
        setup.append(["op": "el", "id": canvas, "tag": "canvas", "attrs": [["width", "200"], ["height", "200"]]])
        setup.append(["op": "ins", "id": canvas, "parent": 1])
        renderer.send(json(setup))
        drain()
        pump(10, until: { drain(); return !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
        var payloads: [String] = []
        for f in 0..<frames {
            var ops: [[String: Any]] = []
            let moving = min(boxes.count, 300)
            for k in 0..<moving {
                let id = boxes[(k * 7 + f) % boxes.count]
                ops.append(["op": "style", "id": id, "name": "transform", "value": "translate(\(f % 13)px, \((f + k) % 9)px) rotate(\((f * 3 + k) % 360)deg)"])
            }
            if mode != "transform" {
                for k in 0..<20 {
                    ops.append(["op": "style", "id": boxes[(k * 31 + f) % boxes.count], "name": "background-color", "value": "rgb(\(f % 255), \(k * 10), 120)"])
                }
                for (k, text) in texts.enumerated() {
                    ops.append(["op": "data", "id": text, "data": "label \(k) frame \(f)"])
                }
                ops.append(["op": "canvas", "id": canvas, "cmds": [["fillStyle", "#f80"], ["fillRect", Double(f % 100), 10.0, 40.0, 40.0]]])
            }
            payloads.append(json(ops))
        }
        let queries = (0..<3).map { "{\"type\":\"query\",\"qid\":\($0),\"query\":{\"q\":\"rect\",\"id\":\(boxes[$0 * 50])}}" }
        var wall = 0.0
        var cpu = 0.0
        var waitCPU = 0.0
        let stagesBefore = renderer.stageMilliseconds
        for f in 0..<frames {
            let t0 = CACurrentMediaTime()
            let c0 = LiteHTMLWidgetRenderer.threadCPUSeconds()
            renderer.send(payloads[f])
            if mode != "transform" {
                for q in queries { renderer.send(q) }
            }
            if let point = Optional(NSPoint(x: Double(f % 400), y: 300)), mode == "mixed" {
                let event = NSEvent.mouseEvent(with: .mouseMoved, location: renderer.view.convert(point, to: nil), modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 0, pressure: 0)
                if let event = event { renderer.view.mouseMoved(with: event) }
            }
            drain()
            wall += CACurrentMediaTime() - t0
            cpu += LiteHTMLWidgetRenderer.threadCPUSeconds() - c0
            let c1 = LiteHTMLWidgetRenderer.threadCPUSeconds()
            pump(2, until: { drain(); return !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
            waitCPU += LiteHTMLWidgetRenderer.threadCPUSeconds() - c1
        }
        var stages: [String] = []
        for (key, value) in renderer.stageMilliseconds.sorted(by: { $0.key < $1.key }) {
            let delta = value - (stagesBefore[key] ?? 0)
            if delta > 0 { stages.append(String(format: "%@=%.1f", key, delta * 1000 / Double(frames))) }
        }
        print(String(format: "BENCH mode=%@ n=%d frames=%d wall=%.1fus cpu=%.1fus tailcpu=%.1fus total=%.1fus answers=%d layouts=%d", mode, count, frames, wall * 1e6 / Double(frames), cpu * 1e6 / Double(frames), waitCPU * 1e6 / Double(frames), (cpu + waitCPU) * 1e6 / Double(frames), answers, renderer.layoutCount))
        print("BENCH stages(us/frame or count*1000/frame) " + stages.joined(separator: " "))
    }

    private func swarm(renderer: LiteHTMLWidgetRenderer, window: NSWindow, count: Int, frames: Int, queue: @escaping () -> [() -> Void]) throws {
        func drain() {
            while true {
                let items = queue()
                if items.isEmpty { return }
                items.forEach { $0() }
            }
        }
        var setup: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 2, "tag": "style", "attrs": []],
            ["op": "css", "id": 2, "text": "body { margin: 0; font: 14px sans-serif; } .card { padding: 14px 16px 16px; background: #fff; overflow: hidden; } .stage { position: relative; height: 260px; border-radius: 14px; overflow: hidden; background: #eef0f6; border: 1px solid rgba(0,0,0,.08); } .t { position: absolute; left: 0; top: 0; width: 9px; height: 9px; border-radius: 3px; } .orb { position: absolute; left: 0; top: 0; width: 92px; height: 92px; border-radius: 46px; background: rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.35); box-shadow: 0 8px 30px rgba(0,0,0,.25); } .log { position: relative; height: 128px; margin-top: 12px; overflow: auto; border-radius: 12px; background: #f2f2f7; } .sh { position: sticky; top: 0; display: flex; justify-content: space-between; padding: 6px 10px; font-size: 11px; font-weight: 700; color: #6e6e73; background: #f2f2f7; } .r { display: flex; justify-content: space-between; padding: 5px 10px; font-size: 12px; border-bottom: 1px solid rgba(0,0,0,.08); }"],
            ["op": "ins", "id": 2, "parent": 1],
            ["op": "el", "id": 3, "tag": "div", "attrs": [["class", "card"]]],
            ["op": "ins", "id": 3, "parent": 1],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["class", "stage"]]],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["class", "log"]]],
            ["op": "ins", "id": 5, "parent": 3],
            ["op": "el", "id": 6, "tag": "div", "attrs": [["class", "sh"]]],
            ["op": "ins", "id": 6, "parent": 5],
            ["op": "text", "id": 7, "data": "Event Frame"],
            ["op": "ins", "id": 7, "parent": 6],
        ]
        var next = 100
        let w = 366.0, h = 260.0
        let cols = Int((Double(count) * w / h).squareRoot().rounded(.up)), rows = (count + cols - 1) / cols
        var tiles: [(id: Int, hx: Double, hy: Double, ph: Double, hue: Int)] = []
        for i in 0..<count {
            let id = next
            next += 1
            let c = i % cols, r = i / cols
            let hue = 360 * i / count
            setup.append(["op": "el", "id": id, "tag": "div", "attrs": [["class", "t"], ["style", "background-color: hsl(\(hue), 80%, 58%)"]]])
            setup.append(["op": "ins", "id": id, "parent": 4])
            tiles.append((id, (Double(c) + 0.5) * w / Double(cols) - 4.5, (Double(r) + 0.5) * h / Double(rows) - 4.5, Double(c) * 0.35 + Double(r) * 0.22, hue))
        }
        var orbs: [Int] = []
        for _ in 0..<3 {
            setup.append(["op": "el", "id": next, "tag": "div", "attrs": [["class", "orb"]]])
            setup.append(["op": "ins", "id": next, "parent": 4])
            orbs.append(next)
            next += 1
        }
        var logTexts: [(Int, Int)] = []
        for i in 0..<40 {
            let row = next, a = next + 1, at = next + 2, b = next + 3, bt = next + 4
            next += 5
            setup += [
                ["op": "el", "id": row, "tag": "div", "attrs": [["class", "r"]]], ["op": "ins", "id": row, "parent": 5],
                ["op": "el", "id": a, "tag": "span", "attrs": []], ["op": "ins", "id": a, "parent": row],
                ["op": "text", "id": at, "data": "tile \(i * 37 % 800) re-coloured"], ["op": "ins", "id": at, "parent": a],
                ["op": "el", "id": b, "tag": "span", "attrs": []], ["op": "ins", "id": b, "parent": row],
                ["op": "text", "id": bt, "data": "#\(i)"], ["op": "ins", "id": bt, "parent": b],
            ]
            logTexts.append((at, bt))
        }
        renderer.send(json(setup))
        drain()
        pump(10, until: { drain(); return !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
        let logMax = 40.0 * 24 + 24 - 128
        var payloads: [String] = []
        var px = tiles.map { _ in 0.0 }, py = tiles.map { _ in 0.0 }
        var hues = tiles.map { $0.hue }
        var orbA = [0.0, 2.1, 4.2]
        for f in 1...frames {
            let t = Double(f) / 60
            var ops: [[String: Any]] = []
            ops.reserveCapacity(count + 32)
            for (i, p) in tiles.enumerated() {
                let x = p.hx + cos(t * 1.3 + p.ph * 0.7) * 6
                let y = p.hy + sin(t * 2.2 + p.ph) * 14
                let s = 0.75 + 0.35 * sin(t * 3 + p.ph), rot = sin(t + p.ph) * 40
                px[i] += (x - px[i]) * 0.35
                py[i] += (y - py[i]) * 0.35
                ops.append(["op": "style", "id": p.id, "name": "transform", "value": String(format: "translate(%.1fpx, %.1fpx) rotate(%.1fdeg) scale(%.2f)", px[i], py[i], rot, s)])
            }
            for k in 0..<16 {
                let i = (f * 16 + k * 97) % tiles.count
                hues[i] = (hues[i] + 47) % 360
                ops.append(["op": "style", "id": tiles[i].id, "name": "background-color", "value": "hsl(\(hues[i]), 80%, 58%)"])
            }
            for (i, id) in orbs.enumerated() {
                orbA[i] += 0.012 * (1 + Double(i) * 0.4)
                ops.append(["op": "style", "id": id, "name": "transform", "value": String(format: "translate(%.1fpx, %.1fpx) scale(%.2f)", w / 2 - 46 + cos(orbA[i]) * w * 0.33, h / 2 - 46 + sin(orbA[i] * 1.3) * 70, 0.8 + 0.25 * sin(orbA[i] * 2))])
            }
            if f % 12 == 0 {
                let row = logTexts[(f / 12) % logTexts.count]
                ops.append(["op": "data", "id": row.0, "data": "tile \((f * 16) % tiles.count) re-coloured"])
                ops.append(["op": "data", "id": row.1, "data": "#\(f)"])
            }
            ops.append(["op": "prop", "id": 5, "name": "scrollTop", "value": (Double(f) * 0.6).truncatingRemainder(dividingBy: logMax)])
            payloads.append(json(ops))
        }
        var wall = 0.0, cpu = 0.0, waitCPU = 0.0
        let stagesBefore = renderer.stageMilliseconds
        let layoutsBefore = renderer.layoutCount
        let dirtyBefore = renderer.dirtyArea
        for f in 0..<frames {
            let t0 = CACurrentMediaTime()
            let c0 = LiteHTMLWidgetRenderer.threadCPUSeconds()
            renderer.send(payloads[f])
            drain()
            wall += CACurrentMediaTime() - t0
            cpu += LiteHTMLWidgetRenderer.threadCPUSeconds() - c0
            let c1 = LiteHTMLWidgetRenderer.threadCPUSeconds()
            pump(2, until: { drain(); return !renderer.isPreparingTiles && renderer.heldOpBatches == 0 })
            waitCPU += LiteHTMLWidgetRenderer.threadCPUSeconds() - c1
        }
        var stages: [String] = []
        for (key, value) in renderer.stageMilliseconds.sorted(by: { $0.key < $1.key }) {
            let delta = value - (stagesBefore[key] ?? 0)
            if delta > 0 { stages.append(String(format: "%@=%.1f", key, delta * 1000 / Double(frames))) }
        }
        print(String(format: "BENCH mode=swarm n=%d frames=%d wall=%.1fus cpu=%.1fus tailcpu=%.1fus total=%.1fus layouts=%d", count, frames, wall * 1e6 / Double(frames), cpu * 1e6 / Double(frames), waitCPU * 1e6 / Double(frames), (cpu + waitCPU) * 1e6 / Double(frames), renderer.layoutCount - layoutsBefore))
        print(String(format: "BENCH swarm dirty=%.0fpt2/frame composited=%d", (renderer.dirtyArea - dirtyBefore) / Double(frames), renderer.compositedCount))
        print("BENCH stages(us/frame or count*1000/frame) " + stages.joined(separator: " "))
    }
}

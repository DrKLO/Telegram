import XCTest
import AppKit
import Darwin
import LiteHTML
@testable import TwidgetRenderer

final class ScrollGeometryBatchTests: XCTestCase {
    private final class CountingDocument: LHDocument {
        private(set) var scrollerReads = 0

        override func scrollers(byAttribute attribute: String) -> [String: [String: Any]] {
            scrollerReads += 1
            return super.scrollers(byAttribute: attribute)
        }
    }

    private struct Position {
        let id: Int
        var left: CGFloat?
        var top: CGFloat?
    }

    private static func scene(count: Int = 2, sticky: Bool = false) -> [[String: Any]] {
        var ops: [[String: Any]] = [["op": "el", "id": 1, "tag": "body", "attrs": [["style", "margin:0;display:grid;grid-template-columns:repeat(3,120px);gap:10px;background:#fff;font:12px sans-serif"]], "adopt": "body"]]
        func element(_ id: Int, _ parent: Int, _ style: String) {
            ops.append(["op": "el", "id": id, "tag": "div", "attrs": [["style", style]]])
            ops.append(["op": "ins", "id": id, "parent": parent])
        }
        for index in 0..<count {
            let id = 10 + index * 20
            element(id, 1, "width:120px;height:112px;overflow:auto;border-radius:10px;background:#eee")
            element(id + 1, id, "position:relative;width:420px;height:380px;background:#2487")
            element(id + 2, id + 1, "position:absolute;left:72.25px;top:64.5px;width:100px;height:84px;overflow:auto;border-radius:12px;background:#ae78")
            element(id + 3, id + 2, "position:relative;width:230px;height:210px;background:#87a9")
            element(id + 4, id + 3, "position:absolute;left:18.5px;top:27.25px;width:75px;height:56px;overflow:hidden;border-radius:9px;background:#000")
            element(id + 5, id + 4, "position:relative;width:170px;height:160px;background:#0f09")
            element(id + 6, id + 5, "position:absolute;left:120px;top:100px;width:20px;height:18px;background:#f00")
            if sticky {
                element(id + 7, id + 1, "position:sticky;left:0;top:0;width:80px;height:20px;overflow:auto;background:#fdd")
                element(id + 8, id + 7, "width:180px;height:80px;background:#ccf")
            }
            ops.append(["op": "text", "id": id + 9, "text": "Panel \(index)"])
            ops.append(["op": "ins", "id": id + 9, "parent": id + 5])
        }
        return ops
    }

    private final class Fixture {
        let dom = WidgetDOM()
        let scrolling = WidgetScrolling()
        var document: CountingDocument

        init(count: Int = 2, sticky: Bool = false, batching: Bool) {
            for op in ScrollGeometryBatchTests.scene(count: count, sticky: sticky) { dom.apply(op) }
            document = CountingDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
            _ = document.layout(maxWidth: 390, preferredWidth: 390)
            scrolling.batchesScrollGeometry = batching
            _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 1)
        }

        func move(_ position: Position) {
            let previous = scrolling.offset(position.id)
            let point = CGPoint(x: position.left ?? previous.x, y: position.top ?? previous.y)
            if scrolling.setOffset(position.id, point, document: document) {
                _ = scrolling.didScroll(position.id, dom: dom, document: document, scale: 1)
            }
        }

        func group(_ positions: [Position]) {
            scrolling.beginScrollUpdates()
            for position in positions { move(position) }
            _ = scrolling.endScrollUpdates(dom: dom, document: document)
        }

        func rebuild(_ ops: [[String: Any]]) {
            for op in ops { dom.apply(op) }
            document = CountingDocument(html: dom.serialize(theme: "light"), css: nil, previous: nil)
            _ = document.layout(maxWidth: 390, preferredWidth: 390)
            _ = scrolling.update(document: document, dom: dom, animated: [], promoted: [], styleKey: 0, scale: 1)
        }

        func pixels() throws -> Data {
            let image = try XCTUnwrap(document.copyImage(maxWidth: 390, rect: CGRect(x: 0, y: 0, width: 390, height: 250), scale: 1))
            return try XCTUnwrap(image.dataProvider?.data) as Data
        }
    }

    private func rect(_ value: CGRect) -> [Double] {
        return [Double(value.minX), Double(value.minY), Double(value.width), Double(value.height)]
    }

    private func state(_ scrolling: WidgetScrolling, document: LHDocument, file: StaticString = #filePath, line: UInt = #line) throws -> Data {
        let source = document.scrollers(byAttribute: WidgetDOM.idAttribute)
        XCTAssertEqual(Set(scrolling.geometries.keys), Set(source.keys.compactMap(Int.init)), file: file, line: line)
        guard Set(scrolling.geometries.keys) == Set(source.keys.compactMap(Int.init)) else { throw NSError(domain: "ScrollGeometryBatchTests", code: 1) }
        var result: [String: Any] = [:]
        for (key, info) in source {
            let id = try XCTUnwrap(Int(key), file: file, line: line)
            let geometry = try XCTUnwrap(scrolling.geometries[id], file: file, line: line)
            let viewport = try XCTUnwrap((info["viewport"] as? NSValue)?.rectValue, file: file, line: line)
            let hit = try XCTUnwrap((info["hitViewport"] as? NSValue)?.rectValue, file: file, line: line)
            let content = try XCTUnwrap((info["content"] as? NSValue)?.sizeValue, file: file, line: line)
            let offset = try XCTUnwrap((info["offset"] as? NSValue)?.pointValue, file: file, line: line)
            let radii = try XCTUnwrap(info["radii"] as? [NSNumber], file: file, line: line).map { CGFloat($0.doubleValue) }
            let wheel = (info["wheel"] as? NSNumber)?.boolValue != false
            XCTAssertEqual(geometry.viewport, viewport, key, file: file, line: line)
            XCTAssertEqual(geometry.content, content, key, file: file, line: line)
            XCTAssertEqual(geometry.radii, radii, key, file: file, line: line)
            XCTAssertEqual(scrolling.hitViewports[id], wheel ? hit : nil, key, file: file, line: line)
            XCTAssertEqual(scrolling.offset(id), offset, key, file: file, line: line)
            guard geometry.viewport == viewport, geometry.content == content, geometry.radii == radii, scrolling.hitViewports[id] == (wheel ? hit : nil), scrolling.offset(id) == offset else { throw NSError(domain: "ScrollGeometryBatchTests", code: 2) }
            result[key] = ["viewport": rect(viewport), "hit_viewport": rect(hit), "content": [Double(content.width), Double(content.height)], "offset": [Double(offset.x), Double(offset.y)], "radii": radii.map(Double.init), "wheel": wheel, "sticky": (info["sticky"] as? NSNumber)?.boolValue ?? false]
        }
        return try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys])
    }

    func testGroupedXYOffsetsMatchImmediateGeometryPixelsAndClamps() throws {
        _ = NSApplication.shared
        let positions = [Position(id: 10, left: 23.25), Position(id: 10, top: 45.75), Position(id: 30, left: 65.5), Position(id: 30, top: 20.25), Position(id: 12, left: 18.5), Position(id: 12, top: 16.25), Position(id: 32, left: 900, top: 900)]
        for sticky in [false, true] {
            let before = Fixture(sticky: sticky, batching: false)
            let after = Fixture(sticky: sticky, batching: true)
            let beforeReads = before.document.scrollerReads, afterReads = after.document.scrollerReads
            before.group(positions)
            after.group(positions)
            XCTAssertEqual(before.document.scrollerReads - beforeReads, positions.count)
            XCTAssertEqual(after.document.scrollerReads - afterReads, 1)
            XCTAssertEqual(after.scrolling.offset(10), CGPoint(x: 23.25, y: 45.75))
            XCTAssertEqual(after.scrolling.offset(32), after.scrolling.geometries[32]?.maxOffset)
            XCTAssertEqual(try state(after.scrolling, document: after.document), try state(before.scrolling, document: before.document))
            XCTAssertEqual(try after.pixels(), try before.pixels())
        }
    }

    func testIndividualScrollsRefreshBeforeTheNextObservation() throws {
        let fixture = Fixture(sticky: true, batching: true)
        for position in [Position(id: 10, top: 18.25), Position(id: 10, left: 29.5), Position(id: 12, top: 14.75)] {
            let reads = fixture.document.scrollerReads
            fixture.move(position)
            XCTAssertEqual(fixture.document.scrollerReads - reads, 1)
            _ = try state(fixture.scrolling, document: fixture.document)
        }
    }

    func testNestedGroupsRefreshOnceAtTheirOuterBoundary() throws {
        let fixture = Fixture(batching: true)
        let reads = fixture.document.scrollerReads
        fixture.scrolling.beginScrollUpdates()
        fixture.move(Position(id: 10, left: 22))
        fixture.scrolling.beginScrollUpdates()
        fixture.move(Position(id: 30, top: 33))
        XCTAssertFalse(fixture.scrolling.endScrollUpdates(dom: fixture.dom, document: fixture.document))
        XCTAssertEqual(fixture.document.scrollerReads, reads)
        XCTAssertTrue(fixture.scrolling.endScrollUpdates(dom: fixture.dom, document: fixture.document))
        XCTAssertEqual(fixture.document.scrollerReads - reads, 1)
        _ = try state(fixture.scrolling, document: fixture.document)
    }

    func testRemovedOwnersAndNewLayoutsDoNotReuseDeferredGeometry() throws {
        let fixture = Fixture(batching: true)
        fixture.scrolling.beginScrollUpdates()
        fixture.move(Position(id: 10, left: 22, top: 31))
        fixture.rebuild([["op": "rm", "id": 10]])
        let reads = fixture.document.scrollerReads
        XCTAssertFalse(fixture.scrolling.endScrollUpdates(dom: fixture.dom, document: fixture.document))
        XCTAssertEqual(fixture.document.scrollerReads, reads)
        XCTAssertNil(fixture.scrolling.geometries[10])
        XCTAssertNil(fixture.scrolling.geometries[12])
        _ = try state(fixture.scrolling, document: fixture.document)
        fixture.scrolling.beginScrollUpdates()
        fixture.move(Position(id: 30, left: 900, top: 900))
        fixture.rebuild([["op": "style", "id": 32, "name": "left", "value": "98.75px"], ["op": "style", "id": 31, "name": "width", "value": "500px"]])
        XCTAssertTrue(fixture.scrolling.endScrollUpdates(dom: fixture.dom, document: fixture.document))
        _ = try state(fixture.scrolling, document: fixture.document)
        fixture.scrolling.beginScrollUpdates()
        XCTAssertFalse(fixture.scrolling.endScrollUpdates(dom: fixture.dom, document: fixture.document))
    }

    private final class RendererHarness {
        let renderer: LiteHTMLWidgetRenderer
        var scheduled: [() -> Void] = []
        var results: [Int: Any] = [:]

        init(batching: Bool, sticky: Bool = false) throws {
            _ = NSApplication.shared
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: 390)
            renderer.view.frame = CGRect(x: 0, y: 0, width: 390, height: 250)
            renderer.scrolling.batchesScrollGeometry = batching
            renderer.schedule = { [weak self] work in
                if Thread.isMainThread {
                    self?.scheduled.append(work)
                } else {
                    DispatchQueue.main.async(execute: work)
                }
            }
            renderer.onMessage = { [weak self] json in
                guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], message["type"] as? String == "result", let id = message["qid"] as? Int else { return }
                self?.results[id] = message["result"]
            }
            renderer.load()
            try send(ScrollGeometryBatchTests.scene(sticky: sticky))
        }

        deinit { renderer.destroy() }

        func send(_ ops: [[String: Any]]) throws {
            renderer.noteUserInput()
            let data = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
            renderer.send(String(decoding: data, as: UTF8.self))
            while !scheduled.isEmpty { scheduled.removeFirst()() }
        }

        func query(_ id: Int, kind: String) throws -> Any {
            results[id] = nil
            let data = try JSONSerialization.data(withJSONObject: ["type": "query", "qid": id, "query": ["q": kind, "id": id]])
            renderer.send(String(decoding: data, as: UTF8.self))
            while !scheduled.isEmpty { scheduled.removeFirst()() }
            return try XCTUnwrap(results[id])
        }
    }

    func testRendererPendingGroupsPreserveRevealOrderAndQueryResults() throws {
        for sticky in [false, true] {
            let before = try RendererHarness(batching: false, sticky: sticky)
            let after = try RendererHarness(batching: true, sticky: sticky)
            let groups: [[[String: Any]]] = [
                [["op": "style", "id": 11, "name": "height", "value": "420px"], ["op": "prop", "id": 10, "name": "scrollLeft", "value": 44.25], ["op": "prop", "id": 10, "name": "scrollTop", "value": 61.5], ["op": "prop", "id": 30, "name": "scrollLeft", "value": 28.75], ["op": "prop", "id": 30, "name": "scrollTop", "value": 37.25]],
                [["op": "style", "id": 31, "name": "height", "value": "440px"], ["op": "prop", "id": 10, "name": "scrollTop", "value": 900], ["op": "call", "id": 16, "method": "scrollIntoView", "args": [1, 1, 1, 0]], ["op": "prop", "id": 30, "name": "scrollLeft", "value": 900], ["op": "call", "id": 30, "method": "scrollBy", "args": [-17.25, 8.5]], ["op": "prop", "id": 10, "name": "scrollTop", "value": -100], ["op": "call", "id": 10, "method": "scrollBy", "args": [3.5, 14.25]]],
                [["op": "rm", "id": 30], ["op": "style", "id": 11, "name": "width", "value": "500px"], ["op": "prop", "id": 10, "name": "scrollLeft", "value": 130.5], ["op": "prop", "id": 10, "name": "scrollTop", "value": 45.25]],
            ]
            for group in groups {
                try before.send(group)
                try after.send(group)
                let a = try XCTUnwrap(before.renderer.document), b = try XCTUnwrap(after.renderer.document)
                XCTAssertEqual(try state(after.renderer.scrolling, document: b), try state(before.renderer.scrolling, document: a))
                XCTAssertEqual(after.renderer.elementBounds(), before.renderer.elementBounds())
                for id in [10, 12, 14, 16] {
                    let first = try JSONSerialization.data(withJSONObject: before.query(id, kind: "box"), options: [.sortedKeys])
                    let second = try JSONSerialization.data(withJSONObject: after.query(id, kind: "box"), options: [.sortedKeys])
                    XCTAssertEqual(second, first, "query \(id)")
                }
                let first = try XCTUnwrap(a.copyImage(maxWidth: 390, rect: CGRect(x: 0, y: 0, width: 390, height: 250), scale: 1))
                let second = try XCTUnwrap(b.copyImage(maxWidth: 390, rect: CGRect(x: 0, y: 0, width: 390, height: 250), scale: 1))
                XCTAssertEqual(first.dataProvider?.data as Data?, second.dataProvider?.data as Data?)
            }
        }
    }

    private func cpuSeconds() -> Double {
        var usage = rusage()
        getrusage(RUSAGE_SELF, &usage)
        return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1_000_000
    }

    private func positions(_ step: Int, count: Int) -> [Position] {
        var result: [Position] = []
        for index in 0..<count {
            let fraction = step % 3 == 0 ? 0 : CGFloat((step + index) % 4) * 0.25
            let x = CGFloat((step * 37 + index * 23) % 360) - 20 + fraction
            let y = CGFloat((step * 29 + index * 31) % 320) - 10 + fraction
            result.append(Position(id: 10 + index * 20, left: x))
            result.append(Position(id: 10 + index * 20, top: y))
        }
        return result
    }

    func testOptInMatchedNestedScrollGeometryWorkload() throws {
        guard ProcessInfo.processInfo.environment["TW_SCROLL_GEOMETRY_WORKLOAD"] == "1" else { throw XCTSkip("set TW_SCROLL_GEOMETRY_WORKLOAD=1") }
        _ = NSApplication.shared
        var references: [Data] = [], reports: [[String: Any]] = []
        for (run, batching) in [false, true, true, false].enumerated() {
            let fixture = Fixture(count: 24, batching: batching)
            for step in 0..<12 { fixture.group(positions(step, count: 24)) }
            var steps: [[String: Any]] = [], cpu = 0.0, wall = 0.0, refreshes = 0
            for index in 0..<60 {
                let step = index + 12, requests = positions(step, count: 24)
                let reads = fixture.document.scrollerReads, beganCPU = cpuSeconds(), began = CACurrentMediaTime()
                fixture.group(requests)
                let elapsed = (CACurrentMediaTime() - began) * 1000, consumed = (cpuSeconds() - beganCPU) * 1000, enumerations = fixture.document.scrollerReads - reads
                cpu += consumed;wall += elapsed;refreshes += enumerations
                let completed = try state(fixture.scrolling, document: fixture.document)
                if run == 0 {
                    references.append(completed)
                } else {
                    XCTAssertEqual(completed, references[index], "run \(run) state \(step)")
                    guard completed == references[index] else { throw NSError(domain: "ScrollGeometryBatchTests", code: 3) }
                }
                XCTAssertEqual(fixture.scrolling.geometries.count, 72)
                if batching { XCTAssertEqual(enumerations, 1) }
                steps.append(["state": step, "cpu_ms": consumed, "wall_ms": elapsed, "scroller_refreshes": enumerations, "position_requests": requests.count])
            }
            let report: [String: Any] = ["run": run + 1, "batching": batching, "completed_states": 60, "cpu_ms": cpu, "wall_ms": wall, "cpu_ms_per_state": cpu / 60, "wall_ms_per_state": wall / 60, "scroller_refreshes": refreshes, "position_requests": 2880, "states": steps]
            reports.append(report)
            let summary = report.filter { $0.key != "states" }
            print("NESTED_SCROLL_GEOMETRY_WORKLOAD " + String(decoding: try JSONSerialization.data(withJSONObject: summary, options: [.sortedKeys]), as: UTF8.self))
        }
        if let output = ProcessInfo.processInfo.environment["TW_SCROLL_GEOMETRY_WORKLOAD_REPORT"] {
            let directory = URL(fileURLWithPath: output, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let report: [String: Any] = ["scope": "LHDocument and WidgetScrolling nested geometry; excludes raster and presentation", "order": "ABBA", "owners": 24, "nested_clips_per_owner": 2, "warmup_states": 12, "completed_states_per_run": 60, "exact_per_state_geometry_parity": true, "runs": reports]
            try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]).write(to: directory.appendingPathComponent("nested-scroll-geometry.json"))
        }
    }
}

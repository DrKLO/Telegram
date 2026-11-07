import XCTest
@testable import WidgetSandbox

final class WidgetBubbleTests: XCTestCase {
    private func wait(_ condition: () -> Bool, timeout: TimeInterval = 5) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    func testHeightIsRememberedAcrossDropAndRecreatedHostCannotShrinkItBeforeRendering() {
        let feed = WidgetFeed()
        var queued: [() -> Void] = []
        feed.schedule = { queued.append($0) }
        feed.delay = { _, block in queued.append(block) }
        var generation = 0
        let bubble = WidgetBubble(html: "<div style='height:120px'>Hello</div>", theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in
            generation += 1
            return ScriptedSizeRenderer(early: generation == 1 ? 120 : 30, settled: 120)
        })
        var heights: [CGFloat] = []
        bubble.onHeightChange = { heights.append(bubble.height) }

        XCTAssertNotNil(bubble.ensureHost())
        wait { bubble.hasContent && !bubble.isAwaitingRender && bubble.host?.diagnostics.rendered == true }
        XCTAssertEqual(bubble.height, 120)

        let space = OneSlotSpace(bubble.slot, rect: CGRect(x: 0, y: 0, width: 300, height: 120))
        feed.attach(space)
        space.origin = 100_000
        feed.update()
        XCTAssertNil(bubble.host)
        XCTAssertFalse(bubble.slot.isLive)
        XCTAssertEqual(bubble.height, 120)

        space.origin = 0
        feed.update()
        XCTAssertNotNil(bubble.host)
        XCTAssertTrue(bubble.slot.isVisible)
        XCTAssertTrue(bubble.isAwaitingRender)
        wait { bubble.host?.diagnostics.rendered == true && bubble.hostHasContent && !bubble.isAwaitingRender }
        XCTAssertEqual(bubble.height, 120)
        XCTAssertFalse(heights.contains(30), "\(heights)")
        withExtendedLifetime(queued) {}
    }

    /// The bubble reports the widget's resize anchor with the height it comes with (`setResizeAnchor`, v0.1.3).
    func testResizeAnchorComesWithTheHeight() {
        let feed = WidgetFeed()
        feed.schedule = { _ in }
        feed.delay = { _, _ in }
        // MarkerSizeRenderer (SdkTests.swift): 100 for the markup's batch, 200 for the script's.
        let html = "<p id='p'>h100</p><script>TelegramWidget.setResizeAnchor('bottom');document.getElementById('p').textContent='h200';</script>"
        let bubble = WidgetBubble(html: html, theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in MarkerSizeRenderer() })
        var changes: [(height: CGFloat, anchor: WidgetResizeAnchor)] = []
        bubble.onHeightChange = { changes.append((bubble.height, bubble.resizeAnchor)) }
        XCTAssertEqual(bubble.resizeAnchor, .top)
        XCTAssertNotNil(bubble.ensureHost())
        wait { bubble.hasContent && bubble.height == 200 }
        XCTAssertEqual(bubble.resizeAnchor, .bottom)
        XCTAssertEqual(changes.last?.height, 200, "\(changes)")
        XCTAssertEqual(changes.last?.anchor, .bottom, "\(changes)")
        XCTAssertFalse(changes.contains { $0.height == 100 && $0.anchor == .bottom }, "\(changes)")
    }

    func testAsyncSnapshotCannotReplaceSnapshotFromNewHost() {
        let feed = WidgetFeed()
        feed.schedule = { _ in }
        feed.delay = { _, _ in }
        let bubble = WidgetBubble(html: "<div>Hi</div>", theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in ScriptedSizeRenderer(early: 40, settled: 40) })
        typealias Capture = ((snapshot: AnyObject, bytes: Int)?) -> Void
        var captures: [Capture] = []
        bubble.captureSnapshotAsync = { captures.append($0) }
        XCTAssertNotNil(bubble.ensureHost())
        wait { bubble.hostHasContent }
        bubble.takeSnapshot()
        bubble.takeSnapshot()
        XCTAssertEqual(captures.count, 1)
        let space = OneSlotSpace(bubble.slot, rect: CGRect(x: 0, y: 0, width: 300, height: 40))
        feed.attach(space)
        space.origin = 100_000
        feed.update()
        XCTAssertNil(bubble.host)
        space.origin = 0
        feed.update()
        wait { bubble.hostHasContent }
        bubble.takeSnapshot()
        XCTAssertEqual(captures.count, 2)
        let fresh = NSObject()
        captures[1]((fresh, 160))
        XCTAssertTrue(bubble.snapshot === fresh)
        captures[0]((NSObject(), 160))
        XCTAssertTrue(bubble.snapshot === fresh)
    }

    private func rebuiltHeight(holds: Bool) -> (CGFloat, [CGFloat]) {
        let feed = WidgetFeed()
        var queued: [() -> Void] = []
        feed.schedule = { queued.append($0) }
        feed.delay = { _, block in queued.append(block) }
        var generation = 0
        let bubble = WidgetBubble(html: "<div>Rows</div>", theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in
            generation += 1
            return generation == 1 ? ScriptedSizeRenderer(early: 120, settled: 120) : ScriptedSizeRenderer(early: 30, settled: 60)
        })
        bubble.holdsHeightOnRebuild = holds
        var heights: [CGFloat] = []
        bubble.onHeightChange = { heights.append(bubble.height) }
        XCTAssertNotNil(bubble.ensureHost())
        wait { bubble.hasContent && bubble.height == 120 }
        let space = OneSlotSpace(bubble.slot, rect: CGRect(x: 0, y: 0, width: 300, height: 120))
        feed.attach(space)
        space.origin = 100_000
        feed.update()
        XCTAssertNil(bubble.host)
        space.origin = 0
        feed.update()
        wait({ bubble.host?.diagnostics.rendered == true && bubble.hostHasContent && !bubble.isAwaitingRender && bubble.host?.diagnostics.height == 60 })
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        withExtendedLifetime(queued) {}
        return (bubble.height, heights)
    }

    func testRebuiltHostKeepsTheDroppedHeightWhenAsked() {
        let (height, heights) = rebuiltHeight(holds: true)
        XCTAssertEqual(height, 120)
        XCTAssertEqual(heights, [120], "\(heights)")
    }

    func testRebuiltHostTakesItsOwnHeightByDefault() {
        let (height, _) = rebuiltHeight(holds: false)
        XCTAssertEqual(height, 60)
    }

    func testVisibilityFollowsTheFeedAndReachesTheHost() {
        let feed = WidgetFeed()
        feed.schedule = { _ in }
        let bubble = WidgetBubble(html: "<div>Hi</div>", theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in ScriptedSizeRenderer(early: 40, settled: 40) })
        var changes: [Bool] = []
        bubble.onVisibilityChange = { changes.append($0) }
        let space = OneSlotSpace(bubble.slot, rect: CGRect(x: 0, y: 0, width: 300, height: 40))
        feed.attach(space)
        feed.update()
        XCTAssertTrue(bubble.isVisible)
        space.origin = 500
        feed.update()
        XCTAssertFalse(bubble.isVisible)
        XCTAssertNotNil(bubble.host)
        XCTAssertEqual(changes, [true, false])
    }

    func testHostAdoptedAfterItsSlotTurnedVisibleIsVisibleAndGetsFrames() throws {
        let feed = WidgetFeed()
        var queued: [() -> Void] = []
        feed.schedule = { queued.append($0) }
        feed.delay = { _, block in queued.append(block) }
        let html = """
        <div style='height:40px'>Hi</div>
        <script>
        var frames = 0;
        (function tick() { frames++; if (frames < 40) requestAnimationFrame(tick); })();
        setTimeout(function () { console.log('probe hidden=' + document.hidden + ' frames=' + frames); }, 400);
        </script>
        """
        var options = WidgetHost.Options()
        options.visible = false
        let queue = WidgetExecutor.makeQueue("WidgetBubbleTests.prepared")
        var prepared: WidgetHost?
        try queue.sync {
            prepared = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in FrameRenderer() }, executor: WidgetExecutor(queue: queue))
        }
        let bubble = WidgetBubble(html: html, theme: WidgetTheme(), width: 300, feed: feed, rendererFactory: { _, _, _ in FrameRenderer() })
        bubble.preparedHost = {
            defer { prepared = nil }
            return prepared
        }
        let space = OneSlotSpace(bubble.slot, rect: CGRect(x: 0, y: 0, width: 300, height: 40))
        feed.attach(space)
        space.origin = 500
        feed.update()
        XCTAssertFalse(bubble.slot.isVisible)
        XCTAssertNil(bubble.host)
        space.origin = 0
        feed.update()
        XCTAssertTrue(bubble.slot.isVisible)
        XCTAssertNil(bubble.host, "the adoption must still be in flight for this race")
        let window = TestWindow.makeVisible()
        defer { window.close() }
        wait({ bubble.host != nil }, timeout: 5)
        let host = try XCTUnwrap(bubble.host)
        window.attach(host.view)
        wait({ bubble.host?.diagnostics.logs.contains { $0.text.hasPrefix("probe ") } == true }, timeout: 5)
        let probe = bubble.host?.diagnostics.logs.last { $0.text.hasPrefix("probe ") }?.text ?? ""
        XCTAssertTrue(probe.contains("hidden=false"), probe)
        let frames = Int(probe.components(separatedBy: "frames=").last ?? "") ?? 0
        XCTAssertGreaterThan(frames, 10, probe)
        withExtendedLifetime(queued) {}
    }

    private func morphProbe(features: Set<String>) -> (applied: Bool?, logs: [String]) {
        let html = "<div id='bar' style='width:30%'>a</div><script>var bar = document.getElementById('bar'); document.addEventListener('widgetupdate', function () { console.log('update same=' + (bar === document.getElementById('bar')) + ' ' + bar.getAttribute('style')); });</script>"
        var options = WidgetHost.Options()
        options.features = features
        let bubble = WidgetBubble(html: html, theme: WidgetTheme(), width: 300, options: options, feed: WidgetFeed(), rendererFactory: { _, _, _ in FrameRenderer() })
        XCTAssertNotNil(bubble.ensureHost())
        wait { bubble.host?.diagnostics.loaded == true }
        var applied: Bool?
        bubble.morph(html: "<div id='bar' style='width:50%'>b</div><script>console.log('reran');</script>") { applied = $0 }
        wait({ applied != nil && (applied == false || bubble.host?.diagnostics.logs.contains { $0.text.hasPrefix("update ") } == true) })
        return (applied, bubble.host?.diagnostics.logs.map { $0.text } ?? [])
    }

    func testMorphPatchesTheLiveDocumentWhenAdvertised() {
        let (applied, logs) = morphProbe(features: ["morphUpdates"])
        XCTAssertEqual(applied, true)
        XCTAssertTrue(logs.contains("update same=true width:50%"), "\(logs)")
        XCTAssertFalse(logs.contains("reran"))
    }

    func testMorphIsRefusedWithoutTheFeature() {
        let (applied, _) = morphProbe(features: [])
        XCTAssertEqual(applied, false)
    }
}

private final class FrameRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 40))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?
    private var time = 0.0

    func load() {
        onMessage?("{\"type\":\"ready\"}")
    }

    func send(_ json: String) {
        guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else {
            return
        }
        switch type {
        case "ops":
            onMessage?("{\"type\":\"size\",\"width\":300,\"height\":40}")
        case "requestFrame":
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.008) { [weak self] in
                guard let self = self else {
                    return
                }
                self.time += 16
                self.onMessage?("{\"type\":\"frame\",\"time\":\(self.time)}")
            }
        case "query":
            if let qid = message["qid"] as? Int {
                onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}")
            }
        default:
            break
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

private final class OneSlotSpace: WidgetFeedSpace {
    let slot: WidgetFeedSlot
    let rect: CGRect
    var origin: CGFloat = 0

    init(_ slot: WidgetFeedSlot, rect: CGRect) {
        self.slot = slot
        self.rect = rect
        slot.space = self
        slot.locate = { rect }
    }

    var feedViewport: CGRect { CGRect(x: 0, y: origin, width: 300, height: 400) }
    var feedOnScreen: Bool { true }
    func feedSlots(in rect: CGRect) -> [WidgetFeedSlot] { rect.intersects(self.rect) ? [slot] : [] }
}

private final class ScriptedSizeRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?
    let early: Int
    let settled: Int

    init(early: Int, settled: Int) {
        self.early = early
        self.settled = settled
    }

    func load() {
        onMessage?("{\"type\":\"ready\"}")
    }

    func send(_ json: String) {
        guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else {
            return
        }
        if type == "ops" {
            onMessage?("{\"type\":\"size\",\"width\":300,\"height\":\(early)}")
        } else if type == "query", let qid = message["qid"] as? Int {
            DispatchQueue.main.asyncAfter(deadline: .now() + (qid < 0 ? 0.1 : 0)) { [weak self] in
                guard let self = self else {
                    return
                }
                if qid < 0 {
                    self.onMessage?("{\"type\":\"size\",\"width\":300,\"height\":\(self.settled)}")
                }
                self.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}")
            }
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

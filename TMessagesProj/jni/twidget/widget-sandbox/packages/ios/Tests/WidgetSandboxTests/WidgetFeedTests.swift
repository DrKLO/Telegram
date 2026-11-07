import XCTest
@testable import WidgetSandbox

final class WidgetFeedTests: XCTestCase {
    private let viewport = CGRect(x: 0, y: 1000, width: 400, height: 500)

    private func rows(_ count: Int, height: CGFloat = 100) -> [CGRect] {
        return (0..<count).map { CGRect(x: 0, y: CGFloat($0) * height, width: 400, height: height) }
    }

    private func entries(_ rects: [CGRect], live: Set<Int> = []) -> [WidgetFeedEntry] {
        return rects.enumerated().map { WidgetFeedEntry(id: $0.offset, rect: $0.element, live: live.contains($0.offset)) }
    }

    func testDistanceIsInScreensAndSigned() {
        XCTAssertEqual(WidgetFeedPlanner.distance(CGRect(x: 0, y: 1100, width: 10, height: 10), viewport), 0)
        XCTAssertEqual(WidgetFeedPlanner.distance(CGRect(x: 0, y: 1750, width: 10, height: 10), viewport), 0.5)
        XCTAssertEqual(WidgetFeedPlanner.distance(CGRect(x: 0, y: 400, width: 10, height: 100), viewport), -1)
    }

    func testVisibleRowsAreAlwaysCreatedRegardlessOfLimits() {
        var policy = WidgetFeedPolicy()
        policy.createsPerTick = 0
        policy.liveBudget = 0
        let plan = WidgetFeedPlanner.plan(entries: entries(rows(40)), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        XCTAssertEqual(Set(plan.create), Set(10...14))
        XCTAssertEqual(plan.visible, Set(10...14))
        XCTAssertTrue(plan.drop.isEmpty)
    }

    func testPrefetchFollowsTravelDirectionOnePerTick() {
        var policy = WidgetFeedPolicy()
        policy.createsPerTick = 1
        let live = Set(10...14)
        let down = WidgetFeedPlanner.plan(entries: entries(rows(40), live: live), viewport: viewport, velocity: 800, onScreen: true, policy: policy)
        XCTAssertEqual(down.create, [15])
        XCTAssertGreaterThan(down.deferred, 0)
        let up = WidgetFeedPlanner.plan(entries: entries(rows(40), live: live), viewport: viewport, velocity: -800, onScreen: true, policy: policy)
        XCTAssertEqual(up.create, [9])
    }

    func testFastScrollReachesFurtherAhead() {
        let policy = WidgetFeedPolicy()
        let slow = WidgetFeedPlanner.prefetchReach(velocity: 100, viewport: viewport, policy: policy)
        let fast = WidgetFeedPlanner.prefetchReach(velocity: 6000, viewport: viewport, policy: policy)
        XCTAssertEqual(slow.ahead, policy.prefetchScreens)
        XCTAssertGreaterThan(fast.ahead, slow.ahead)
        XCTAssertLessThanOrEqual(fast.ahead, policy.maxPrefetchScreens)
    }

    func testNeverDropsVisibleOrKeptRows() {
        var policy = WidgetFeedPolicy()
        policy.keepScreens = 2
        policy.dropHysteresis = 1
        policy.liveBudget = 1000
        let live = Set(0..<60)
        let plan = WidgetFeedPlanner.plan(entries: entries(rows(60), live: live), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        let dropped = Set(plan.drop)
        for id in 0..<60 {
            let d = abs(WidgetFeedPlanner.distance(rows(60)[id], viewport))
            XCTAssertEqual(dropped.contains(id), d > 3, "row \(id) at \(d) screens")
        }
    }

    func testHysteresisDoesNotThrashAtTheBoundary() {
        var policy = WidgetFeedPolicy()
        policy.keepScreens = 2
        policy.dropHysteresis = 1
        policy.prefetchScreens = 1.5
        let rects = rows(60)
        let boundary = rects.firstIndex { WidgetFeedPlanner.distance($0, viewport) > 2.5 }!
        let kept = WidgetFeedPlanner.plan(entries: entries(rects, live: [boundary]), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        XCTAssertFalse(kept.drop.contains(boundary))
        let notCreated = WidgetFeedPlanner.plan(entries: entries(rects), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        XCTAssertFalse(notCreated.create.contains(boundary))
    }

    func testBudgetDropsFarthestFirstOutsidePrefetchReach() {
        var policy = WidgetFeedPolicy()
        policy.keepScreens = 100
        policy.liveBudget = 30
        let live = Set(0..<40)
        let plan = WidgetFeedPlanner.plan(entries: entries(rows(40), live: live), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        XCTAssertEqual(Set(plan.drop), Set(30...39))
        policy.liveBudget = 4
        let tight = WidgetFeedPlanner.plan(entries: entries(rows(40), live: live), viewport: viewport, velocity: 0, onScreen: true, policy: policy)
        XCTAssertTrue(Set(tight.drop).isDisjoint(with: Set(3...22)))
    }

    func testUnplacedLiveEntriesSurviveUnlessOverBudget() {
        var policy = WidgetFeedPolicy()
        policy.liveBudget = 2
        let unplaced = [WidgetFeedEntry(id: 1, rect: nil, live: true)]
        XCTAssertTrue(WidgetFeedPlanner.plan(entries: unplaced, viewport: viewport, velocity: 0, onScreen: true, policy: policy).drop.isEmpty)
        let crowded = unplaced + (2..<6).map { WidgetFeedEntry(id: $0, rect: nil, live: true) }
        XCTAssertEqual(WidgetFeedPlanner.plan(entries: crowded, viewport: viewport, velocity: 0, onScreen: true, policy: policy).drop.count, 3)
    }

    func testHiddenWindowShowsNothingButStillPrefetches() {
        let plan = WidgetFeedPlanner.plan(entries: entries(rows(40)), viewport: viewport, velocity: 0, onScreen: false, policy: WidgetFeedPolicy())
        XCTAssertTrue(plan.visible.isEmpty)
        XCTAssertFalse(plan.create.isEmpty)
    }

    final class ListSpace: WidgetFeedSpace {
        var rects: [CGRect]
        var origin: CGFloat = 0
        var height: CGFloat = 500
        var queried = 0
        lazy var slots: [WidgetFeedSlot?] = Array(repeating: nil, count: rects.count)
        var make: ((Int) -> WidgetFeedSlot)!

        init(_ rects: [CGRect]) { self.rects = rects }

        var feedViewport: CGRect { CGRect(x: 0, y: origin, width: 400, height: height) }
        var feedOnScreen: Bool { true }

        func feedSlots(in rect: CGRect) -> [WidgetFeedSlot] {
            var lo = 0, hi = rects.count
            while lo < hi {
                let mid = (lo + hi) / 2
                if rects[mid].maxY <= rect.minY { lo = mid + 1 } else { hi = mid }
            }
            var result: [WidgetFeedSlot] = []
            var i = lo
            while i < rects.count && rects[i].minY < rect.maxY {
                if slots[i] == nil { slots[i] = make(i) }
                result.append(slots[i]!)
                queried += 1
                i += 1
            }
            return result
        }
    }

    final class Box {
        var created = 0
        var dropped = 0
        var visible: [Bool] = []
    }

    private func drive(_ count: Int, steps: Int, step: CGFloat, policy: WidgetFeedPolicy, check: (ListSpace, WidgetFeed, [Box]) -> Void) {
        let feed = WidgetFeed()
        var queued: [() -> Void] = []
        feed.schedule = { queued.append($0) }
        feed.delay = { _, block in queued.append(block) }
        feed.policy = policy
        var time: CFTimeInterval = 0
        feed.now = { time }
        let space = ListSpace(rows(count))
        let boxes = (0..<count).map { _ in Box() }
        space.make = { index in
            let slot = WidgetFeedSlot()
            let box = boxes[index]
            slot.space = space
            slot.locate = { space.rects[index] }
            slot.create = { box.created += 1; return true }
            slot.drop = { box.dropped += 1 }
            slot.visibilityChanged = { box.visible.append($0) }
            return slot
        }
        feed.attach(space)
        func drain() {
            var rounds = 0
            while !queued.isEmpty && rounds < 1000 {
                let batch = queued
                queued.removeAll()
                batch.forEach { $0() }
                rounds += 1
            }
        }
        drain()
        for _ in 0..<steps {
            space.origin += step
            time += 1.0 / 120
            feed.setNeedsUpdate()
            drain()
            check(space, feed, boxes)
        }
        for (index, box) in boxes.enumerated() {
            let slot = space.slots[index]
            XCTAssertEqual(box.created - box.dropped, (slot?.isLive ?? false) ? 1 : 0)
            if let last = box.visible.last {
                XCTAssertEqual(last, slot?.isVisible)
            }
        }
    }

    func testFeedDrivesSlotsThroughScroll() {
        var policy = WidgetFeedPolicy()
        policy.keepScreens = 1
        policy.dropHysteresis = 0.5
        policy.liveBudget = 12
        var droppedAny = false
        drive(80, steps: 600, step: 12, policy: policy) { space, feed, boxes in
            XCTAssertLessThanOrEqual(feed.liveCount, policy.liveBudget + 6)
            for (index, rect) in space.rects.enumerated() where rect.maxY > space.origin && rect.minY < space.origin + 500 {
                XCTAssertTrue(space.slots[index]?.isLive ?? false, "row \(index) visible but not live")
                XCTAssertTrue(space.slots[index]?.isVisible ?? false)
            }
            droppedAny = droppedAny || feed.dropped > 0
        }
        XCTAssertTrue(droppedAny)
    }

    func testTickCostDoesNotGrowWithTheFeed() {
        let policy = WidgetFeedPolicy()
        var examined: [Int] = []
        drive(5000, steps: 2000, step: 40, policy: policy) { _, feed, _ in
            examined.append(feed.lastExamined)
            XCTAssertLessThanOrEqual(feed.liveCount, policy.liveBudget)
        }
        XCTAssertLessThan(examined.max() ?? 0, 80)
    }

    func testSnapshotsWaitForSlowScrollingOnlyWhenAsked() {
        var policy = WidgetFeedPolicy()
        let list = rows(40).enumerated().map { WidgetFeedEntry(id: $0.offset, rect: $0.element, live: false, hasSnapshot: true) }
        let fast = WidgetFeedPlanner.plan(entries: list, viewport: viewport, velocity: 3000, onScreen: true, policy: policy)
        XCTAssertTrue(Set(10...14).isSubset(of: Set(fast.create)))
        policy.bootSnapshotsOnlyWhenSlow = true
        let held = WidgetFeedPlanner.plan(entries: list, viewport: viewport, velocity: 3000, onScreen: true, policy: policy)
        XCTAssertTrue(held.create.isEmpty)
        XCTAssertGreaterThan(held.deferred, 0)
        let slow = WidgetFeedPlanner.plan(entries: list, viewport: viewport, velocity: 100, onScreen: true, policy: policy)
        XCTAssertTrue(Set(10...14).isSubset(of: Set(slow.create)))
    }

    func testSnapshotBudgetEvictsLeastRecentlyUsed() {
        let feed = WidgetFeed()
        feed.schedule = { _ in }
        feed.policy.snapshotBudgetBytes = 300
        var evicted: [Int] = []
        let slots = (0..<5).map { index -> WidgetFeedSlot in
            let slot = WidgetFeedSlot()
            slot.snapshotEvicted = { evicted.append(index) }
            feed.register(slot)
            return slot
        }
        defer { withExtendedLifetime(slots) {} }
        slots[0].snapshotStored(bytes: 100)
        slots[1].snapshotStored(bytes: 100)
        slots[2].snapshotStored(bytes: 100)
        slots[0].snapshotUsed()
        slots[3].snapshotStored(bytes: 100)
        XCTAssertEqual(evicted, [1])
        XCTAssertEqual(feed.snapshotBytes, 300)
        slots[2].snapshotRemoved()
        XCTAssertEqual(feed.snapshotBytes, 200)
        slots[4].snapshotStored(bytes: 150)
        XCTAssertEqual(evicted, [1, 0])
        XCTAssertLessThanOrEqual(feed.snapshotBytes, 300)
    }

    func testFastScrollPausesOnScreenWidgetsAndSlowScrollResumesThem() {
        let feed = WidgetFeed()
        var queued: [() -> Void] = []
        feed.schedule = { queued.append($0) }
        feed.delay = { _, block in queued.append(block) }
        feed.policy.pauseVelocity = 900
        feed.policy.resumeVelocity = 150
        var time: CFTimeInterval = 0
        feed.now = { time }
        let space = ListSpace(rows(400))
        var changes: [Int: [Bool]] = [:]
        space.make = { index in
            let slot = WidgetFeedSlot()
            slot.space = space
            slot.locate = { space.rects[index] }
            slot.create = { true }
            slot.visibilityChanged = { changes[index, default: []].append($0) }
            return slot
        }
        feed.attach(space)
        func step(_ dy: CGFloat) {
            space.origin += dy
            time += 1.0 / 120
            feed.setNeedsUpdate()
            var rounds = 0
            while !queued.isEmpty && rounds < 50 {
                let batch = queued
                queued.removeAll()
                batch.forEach { $0() }
                rounds += 1
            }
        }
        step(0)
        XCTAssertTrue(space.slots[0]?.isVisible ?? false)
        for _ in 0..<30 { step(30) }
        XCTAssertTrue(feed.isPaused(space))
        let onScreen = space.rects.indices.filter { space.rects[$0].maxY > space.origin && space.rects[$0].minY < space.origin + 500 }
        for index in onScreen {
            XCTAssertTrue(space.slots[index]?.isLive ?? false)
            XCTAssertFalse(space.slots[index]?.isVisible ?? true)
            XCTAssertTrue(space.slots[index]?.isPausedOnScreen ?? false)
        }
        for _ in 0..<60 { step(0.5) }
        XCTAssertFalse(feed.isPaused(space))
        for index in onScreen {
            XCTAssertTrue(space.slots[index]?.isVisible ?? false)
            XCTAssertFalse(space.slots[index]?.isPausedOnScreen ?? true)
        }
        XCTAssertGreaterThan(feed.pauses, 0)
    }
}

import Foundation
import CoreGraphics
import QuartzCore

public struct WidgetFeedPolicy: Equatable {
    public var keepScreens: CGFloat = 3
    public var dropHysteresis: CGFloat = 1
    public var prefetchScreens: CGFloat = 1.5
    public var behindScreens: CGFloat = 0.5
    public var prefetchSeconds: CGFloat = 0.35
    public var maxPrefetchScreens: CGFloat = 4
    public var liveBudget = 24
    public var createsPerTick = 1
    public var snapshotBudgetBytes = 32 << 20
    public var bootSnapshotsOnlyWhenSlow = false
    public var slowVelocity: CGFloat = 400
    public var pauseVelocity: CGFloat = 0
    public var resumeVelocity: CGFloat = 150
    public var prepareMargin: CGFloat = 0.5
    public var pressureLiveBudget = 12
    public var pressureKeepScreens: CGFloat = 2
    public var pressureSeconds: CFTimeInterval = 30
    public init() {}
}

public struct WidgetFeedEntry: Equatable {
    public var id: Int
    public var rect: CGRect?
    public var live: Bool
    public var hasSnapshot: Bool
    public init(id: Int, rect: CGRect?, live: Bool, hasSnapshot: Bool = false) {
        self.id = id
        self.rect = rect
        self.live = live
        self.hasSnapshot = hasSnapshot
    }
}

public struct WidgetFeedPlan: Equatable {
    public var create: [Int] = []
    public var drop: [Int] = []
    public var visible: Set<Int> = []
    public var deferred = 0
    public init() {}
}

public enum WidgetFeedPlanner {
    public static func distance(_ rect: CGRect, _ viewport: CGRect) -> CGFloat {
        let height = max(1, viewport.height)
        if rect.maxY <= viewport.minY {
            return -max(viewport.minY - rect.maxY, 0.001) / height
        }
        if rect.minY >= viewport.maxY {
            return max(rect.minY - viewport.maxY, 0.001) / height
        }
        return 0
    }

    public static func intersects(_ rect: CGRect, _ viewport: CGRect) -> Bool {
        return rect.maxY > viewport.minY && rect.minY < viewport.maxY && rect.height > 0
    }

    public static func prefetchReach(velocity: CGFloat, viewport: CGRect, policy: WidgetFeedPolicy) -> (ahead: CGFloat, behind: CGFloat) {
        let flight = abs(velocity) * policy.prefetchSeconds / max(1, viewport.height)
        let ahead = min(policy.maxPrefetchScreens, max(policy.prefetchScreens, flight))
        return (ahead, policy.behindScreens)
    }

    public static func prepared(_ d: CGFloat, velocity: CGFloat, viewport: CGRect, policy: WidgetFeedPolicy) -> Bool {
        let reach = prefetchReach(velocity: velocity, viewport: viewport, policy: policy)
        return abs(d) <= max(reach.ahead, reach.behind) + policy.prepareMargin
    }

    public static func plan(entries: [WidgetFeedEntry], viewport: CGRect, velocity: CGFloat, onScreen: Bool, policy: WidgetFeedPolicy) -> WidgetFeedPlan {
        var plan = WidgetFeedPlan()
        let reach = prefetchReach(velocity: velocity, viewport: viewport, policy: policy)
        let down = velocity >= 0
        func wanted(_ d: CGFloat) -> Bool {
            if d == 0 { return true }
            let forward = (d > 0) == down
            return abs(d) <= (forward ? reach.ahead : max(reach.behind, velocity == 0 ? reach.ahead : 0))
        }
        func priority(_ d: CGFloat) -> CGFloat {
            if d == 0 { return -1 }
            let forward = (d > 0) == down
            return forward ? abs(d) : abs(d) * 2 + 0.25
        }
        var live: [(id: Int, d: CGFloat)] = []
        var candidates: [(id: Int, d: CGFloat)] = []
        for entry in entries {
            guard let rect = entry.rect else {
                if entry.live { live.append((entry.id, .infinity)) }
                continue
            }
            let d = distance(rect, viewport)
            if d == 0 && onScreen && intersects(rect, viewport) {
                plan.visible.insert(entry.id)
            }
            if entry.live {
                live.append((entry.id, d))
            } else if wanted(d) {
                candidates.append((entry.id, d))
            }
        }
        candidates.sort { priority($0.d) < priority($1.d) || (priority($0.d) == priority($1.d) && $0.id < $1.id) }
        var liveCount = live.count
        var nonVisible = 0
        let holdSnapshots = policy.bootSnapshotsOnlyWhenSlow && abs(velocity) > policy.slowVelocity
        let snapshotted = Set(entries.filter { $0.hasSnapshot }.map { $0.id })
        for candidate in candidates {
            if holdSnapshots && snapshotted.contains(candidate.id) {
                plan.deferred += 1
                continue
            }
            if candidate.d == 0 {
                plan.create.append(candidate.id)
                liveCount += 1
                continue
            }
            if liveCount >= policy.liveBudget {
                continue
            }
            if nonVisible >= policy.createsPerTick {
                plan.deferred += 1
                continue
            }
            plan.create.append(candidate.id)
            nonVisible += 1
            liveCount += 1
        }
        let keep = policy.keepScreens + policy.dropHysteresis
        var dropped = Set<Int>()
        for item in live where abs(item.d) > keep && item.d.isFinite {
            dropped.insert(item.id)
        }
        liveCount -= dropped.count
        if liveCount > policy.liveBudget {
            let protected = max(reach.ahead, reach.behind)
            let extra = live.filter { !dropped.contains($0.id) && abs($0.d) > protected }.sorted { abs($0.d) > abs($1.d) || (abs($0.d) == abs($1.d) && $0.id < $1.id) }
            for item in extra where liveCount > policy.liveBudget {
                dropped.insert(item.id)
                liveCount -= 1
            }
        }
        plan.drop = live.map { $0.id }.filter { dropped.contains($0) }
        return plan
    }
}

public protocol WidgetFeedSpace: AnyObject {
    var feedViewport: CGRect { get }
    var feedOnScreen: Bool { get }
    func feedSlots(in rect: CGRect) -> [WidgetFeedSlot]
}

public final class WidgetFeedSlot {
    public let id: Int
    public weak var space: WidgetFeedSpace?
    public var locate: (() -> CGRect?)?
    public var create: (() -> Bool)?
    public var drop: (() -> Void)?
    public var visibilityChanged: ((Bool) -> Void)?
    public var snapshotEvicted: (() -> Void)?
    public var pauseChanged: ((Bool) -> Void)?
    public var prepareChanged: ((Bool) -> Void)?
    public fileprivate(set) var isPrepared = false
    public fileprivate(set) var snapshotBytes = 0
    fileprivate var snapshotUse = 0
    public fileprivate(set) var isLive = false
    public fileprivate(set) var isVisible = false
    public fileprivate(set) var isPausedOnScreen = false
    fileprivate weak var feed: WidgetFeed?

    private static var nextId = 0

    public init() {
        WidgetFeedSlot.nextId += 1
        id = WidgetFeedSlot.nextId
    }

    public func markLive(_ live: Bool) {
        guard let feed = feed else {
            isLive = live
            return
        }
        feed.setLive(self, live)
    }

    public func setNeedsUpdate() {
        feed?.setNeedsUpdate()
    }

    public func snapshotStored(bytes: Int) {
        feed?.storeSnapshot(self, bytes: bytes)
    }

    public func snapshotUsed() {
        feed?.useSnapshot(self)
    }

    public func snapshotRemoved() {
        feed?.removeSnapshot(self)
    }
}

public final class WidgetFeed {
    public static let shared = WidgetFeed()

    public var policy = WidgetFeedPolicy()
    public var now: () -> CFTimeInterval = { CACurrentMediaTime() }
    public var schedule: ((@escaping () -> Void) -> Void)?
    public var delay: ((TimeInterval, @escaping () -> Void) -> Void)?
    public var followUpInterval: TimeInterval = 1.0 / 120
    public private(set) var created = 0
    public private(set) var dropped = 0
    public private(set) var ticks = 0
    public private(set) var lastExamined = 0

    private final class WeakSlot {
        weak var slot: WidgetFeedSlot?
        init(_ slot: WidgetFeedSlot) { self.slot = slot }
    }

    private final class WeakSpace {
        weak var space: WidgetFeedSpace?
        init(_ space: WidgetFeedSpace) { self.space = space }
    }

    private var spaces: [ObjectIdentifier: WeakSpace] = [:]
    private var live: [Int: WeakSlot] = [:]
    private var visible: [Int: WeakSlot] = [:]
    private var scheduled = false
    private var followUp = false
    private var motion: [ObjectIdentifier: (origin: CGFloat, time: CFTimeInterval, velocity: CGFloat)] = [:]
    private var paused = Set<ObjectIdentifier>()
    private var resumeCheck = false
    public private(set) var pauses = 0

    public init() {}

    private var pressureSource: DispatchSourceMemoryPressure?
    public private(set) var pressureUntil: CFTimeInterval = 0
    public private(set) var pressureEvents = 0

    public func watchMemoryPressure() {
        guard pressureSource == nil else {
            return
        }
        let source = DispatchSource.makeMemoryPressureSource(eventMask: [.warning, .critical], queue: .main)
        source.setEventHandler { [weak self, weak source] in
            self?.memoryPressure(critical: source?.data.contains(.critical) ?? false)
        }
        source.resume()
        pressureSource = source
    }

    public func memoryPressure(critical: Bool) {
        pressureEvents += 1
        pressureUntil = now() + policy.pressureSeconds
        trimSnapshots(to: critical ? 0 : policy.snapshotBudgetBytes / 4)
        setNeedsUpdate()
    }

    public var underPressure: Bool {
        return now() < pressureUntil
    }

    private var effectivePolicy: WidgetFeedPolicy {
        guard underPressure else {
            return policy
        }
        var policy = self.policy
        policy.liveBudget = min(policy.liveBudget, policy.pressureLiveBudget)
        policy.keepScreens = min(policy.keepScreens, policy.pressureKeepScreens)
        policy.dropHysteresis = min(policy.dropHysteresis, 0.5)
        return policy
    }

    public func isPaused(_ space: WidgetFeedSpace) -> Bool {
        return paused.contains(ObjectIdentifier(space))
    }

    public private(set) var snapshotBytes = 0
    private var snapshots: [Int: WeakSlot] = [:]
    private var snapshotClock = 0

    fileprivate func storeSnapshot(_ slot: WidgetFeedSlot, bytes: Int) {
        snapshotBytes += bytes - slot.snapshotBytes
        slot.snapshotBytes = bytes
        snapshotClock += 1
        slot.snapshotUse = snapshotClock
        snapshots[slot.id] = WeakSlot(slot)
        trimSnapshots()
    }

    fileprivate func useSnapshot(_ slot: WidgetFeedSlot) {
        snapshotClock += 1
        slot.snapshotUse = snapshotClock
    }

    fileprivate func removeSnapshot(_ slot: WidgetFeedSlot) {
        guard snapshots.removeValue(forKey: slot.id) != nil else {
            return
        }
        snapshotBytes -= slot.snapshotBytes
        slot.snapshotBytes = 0
    }

    private func trimSnapshots() {
        trimSnapshots(to: underPressure ? policy.snapshotBudgetBytes / 4 : policy.snapshotBudgetBytes)
    }

    private func trimSnapshots(to budget: Int) {
        guard snapshotBytes > budget else {
            return
        }
        var order: [(use: Int, slot: WidgetFeedSlot)] = []
        for (id, weak) in snapshots {
            if let slot = weak.slot {
                order.append((slot.snapshotUse, slot))
            } else {
                snapshots.removeValue(forKey: id)
            }
        }
        order.sort { $0.use < $1.use }
        for item in order where snapshotBytes > budget {
            removeSnapshot(item.slot)
            item.slot.snapshotEvicted?()
        }
    }

    public func register(_ slot: WidgetFeedSlot) {
        slot.feed = self
        if slot.isLive {
            live[slot.id] = WeakSlot(slot)
        }
    }

    public func unregister(_ slot: WidgetFeedSlot) {
        if let weak = snapshots.removeValue(forKey: slot.id) {
            _ = weak
            snapshotBytes -= slot.snapshotBytes
        }
        live.removeValue(forKey: slot.id)
        visible.removeValue(forKey: slot.id)
        if slot.feed === self {
            slot.feed = nil
        }
    }

    public func attach(_ space: WidgetFeedSpace) {
        let key = ObjectIdentifier(space)
        if spaces[key]?.space == nil {
            spaces[key] = WeakSpace(space)
            setNeedsUpdate()
        }
    }

    public func detach(_ space: WidgetFeedSpace) {
        spaces.removeValue(forKey: ObjectIdentifier(space))
        setNeedsUpdate()
    }

    fileprivate func setLive(_ slot: WidgetFeedSlot, _ isLive: Bool) {
        slot.isLive = isLive
        if isLive {
            live[slot.id] = WeakSlot(slot)
        } else {
            live.removeValue(forKey: slot.id)
            if slot.isVisible {
                slot.isVisible = false
                visible.removeValue(forKey: slot.id)
            }
        }
        setNeedsUpdate()
    }

    public var liveCount: Int {
        return live.count
    }

    public func setNeedsUpdate() {
        if scheduled {
            return
        }
        scheduled = true
        let run = { [weak self] in
            guard let self = self else {
                return
            }
            self.scheduled = false
            self.update()
        }
        if let schedule = schedule {
            schedule(run)
        } else {
            WidgetFeed.beforeCommit(run)
        }
    }

    private static func beforeCommit(_ block: @escaping () -> Void) {
        let observer = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeWaiting.rawValue, false, 0) { _, _ in
            block()
        }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
        CFRunLoopWakeUp(CFRunLoopGetMain())
    }

    public func update() {
        ticks += 1
        let time = now()
        var bySlot: [Int: WidgetFeedSlot] = [:]
        var groups: [(key: ObjectIdentifier, viewport: CGRect, onScreen: Bool, velocity: CGFloat, entries: [WidgetFeedEntry])] = []
        var nextMotion: [ObjectIdentifier: (origin: CGFloat, time: CFTimeInterval, velocity: CGFloat)] = [:]
        var groupIndex: [ObjectIdentifier: Int] = [:]
        for (key, weak) in spaces {
            guard let space = weak.space else {
                spaces.removeValue(forKey: key)
                continue
            }
            let viewport = space.feedViewport
            let velocity = self.velocity(key, origin: viewport.minY, time: time, into: &nextMotion)
            let reach = WidgetFeedPlanner.prefetchReach(velocity: velocity, viewport: viewport, policy: policy)
            let span = max(reach.ahead, reach.behind) * viewport.height
            var entries: [WidgetFeedEntry] = []
            for slot in space.feedSlots(in: viewport.insetBy(dx: 0, dy: -span)) where bySlot[slot.id] == nil {
                if slot.feed == nil {
                    register(slot)
                }
                bySlot[slot.id] = slot
                entries.append(WidgetFeedEntry(id: slot.id, rect: slot.locate?(), live: slot.isLive, hasSnapshot: slot.snapshotBytes > 0))
            }
            groupIndex[key] = groups.count
            var onScreen = space.feedOnScreen
            if policy.pauseVelocity > 0 {
                if paused.contains(key) {
                    if abs(velocity) < policy.resumeVelocity {
                        paused.remove(key)
                    }
                } else if abs(velocity) > policy.pauseVelocity {
                    paused.insert(key)
                    pauses += 1
                }
                if paused.contains(key) {
                    onScreen = false
                    scheduleResumeCheck()
                }
            }
            groups.append((key, viewport, onScreen, velocity, entries))
        }
        motion = nextMotion
        var orphans: [WidgetFeedEntry] = []
        for (id, weak) in live.merging(visible, uniquingKeysWith: { a, _ in a }) {
            guard let slot = weak.slot else {
                live.removeValue(forKey: id)
                visible.removeValue(forKey: id)
                continue
            }
            if bySlot[id] != nil {
                continue
            }
            bySlot[id] = slot
            let entry = WidgetFeedEntry(id: id, rect: slot.space == nil ? nil : slot.locate?(), live: slot.isLive)
            if let space = slot.space, let index = groupIndex[ObjectIdentifier(space)], entry.rect != nil {
                groups[index].entries.append(entry)
            } else {
                orphans.append(WidgetFeedEntry(id: id, rect: nil, live: slot.isLive))
            }
        }
        if groups.isEmpty {
            groups.append((ObjectIdentifier(self), .zero, false, 0, []))
        }
        groups[0].entries += orphans
        lastExamined = bySlot.count
        var creates: [Int] = []
        var drops: [Int] = []
        var nowVisible = Set<Int>()
        var pausedOnScreen = Set<Int>()
        var wantsPrepared = Set<Int>()
        var deferred = 0
        let totalLive = live.count
        let base = effectivePolicy
        for group in groups {
            var policy = base
            let groupLive = group.entries.filter { $0.live }.count
            policy.liveBudget = max(0, base.liveBudget - (totalLive - groupLive))
            if paused.contains(group.key) {
                for entry in group.entries where entry.live {
                    if let rect = entry.rect, WidgetFeedPlanner.intersects(rect, group.viewport) {
                        pausedOnScreen.insert(entry.id)
                    }
                }
            }
            for entry in group.entries {
                if let rect = entry.rect, WidgetFeedPlanner.prepared(WidgetFeedPlanner.distance(rect, group.viewport), velocity: group.velocity, viewport: group.viewport, policy: policy) {
                    wantsPrepared.insert(entry.id)
                }
            }
            let plan = WidgetFeedPlanner.plan(entries: group.entries, viewport: group.viewport, velocity: group.velocity, onScreen: group.onScreen, policy: policy)
            creates += plan.create
            drops += plan.drop
            nowVisible.formUnion(plan.visible)
            deferred += plan.deferred
        }
        for id in drops {
            guard let slot = bySlot[id], slot.isLive else {
                continue
            }
            if slot.isVisible {
                slot.isVisible = false
                visible.removeValue(forKey: id)
                slot.visibilityChanged?(false)
            }
            slot.isLive = false
            live.removeValue(forKey: id)
            slot.drop?()
            dropped += 1
        }
        for id in creates {
            guard let slot = bySlot[id], !slot.isLive else {
                continue
            }
            if slot.create?() ?? false {
                slot.isLive = true
                live[id] = WeakSlot(slot)
                created += 1
            }
        }
        for (id, slot) in bySlot {
            let isPaused = slot.isLive && pausedOnScreen.contains(id)
            if isPaused != slot.isPausedOnScreen {
                slot.isPausedOnScreen = isPaused
                slot.pauseChanged?(isPaused)
            }
            let isPrepared = slot.isLive && wantsPrepared.contains(id)
            if isPrepared != slot.isPrepared {
                slot.isPrepared = isPrepared
                slot.prepareChanged?(isPrepared)
            }
        }
        for (id, weak) in visible where !nowVisible.contains(id) || !(weak.slot?.isLive ?? false) {
            visible.removeValue(forKey: id)
            if let slot = weak.slot, slot.isVisible {
                slot.isVisible = false
                slot.visibilityChanged?(false)
            }
        }
        for id in nowVisible {
            guard let slot = bySlot[id], slot.isLive, !slot.isVisible else {
                continue
            }
            slot.isVisible = true
            visible[id] = WeakSlot(slot)
            slot.visibilityChanged?(true)
        }
        if deferred > 0 && !followUp {
            followUp = true
            let run = { [weak self] in
                guard let self = self else {
                    return
                }
                self.followUp = false
                self.setNeedsUpdate()
            }
            if let delay = delay {
                delay(followUpInterval, run)
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + followUpInterval, execute: run)
            }
        }
    }

    private func scheduleResumeCheck() {
        if resumeCheck {
            return
        }
        resumeCheck = true
        let run = { [weak self] in
            guard let self = self else {
                return
            }
            self.resumeCheck = false
            self.setNeedsUpdate()
        }
        if let delay = delay {
            delay(0.12, run)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12, execute: run)
        }
    }

    private func velocity(_ key: ObjectIdentifier, origin: CGFloat, time: CFTimeInterval, into next: inout [ObjectIdentifier: (origin: CGFloat, time: CFTimeInterval, velocity: CGFloat)]) -> CGFloat {
        guard let last = motion[key] else {
            next[key] = (origin, time, 0)
            return 0
        }
        let dt = time - last.time
        var velocity = last.velocity
        if dt > 0.25 {
            velocity = origin == last.origin ? 0 : (origin - last.origin) / CGFloat(dt)
        } else if dt > 0.001 {
            let instant = (origin - last.origin) / CGFloat(dt)
            velocity = last.velocity * 0.5 + instant * 0.5
        }
        if origin == last.origin && dt > 0.1 {
            velocity = 0
        }
        next[key] = (origin, time, velocity)
        return velocity
    }
}

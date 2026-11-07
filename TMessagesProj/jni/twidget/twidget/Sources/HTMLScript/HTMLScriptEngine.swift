import Foundation
import QuartzCore
import HTMLSandbox

final class HTMLScriptEngine {
    static let shared = HTMLScriptEngine()

    static let callLimit: TimeInterval = 0.050
    static let libraryLimit: TimeInterval = 2.0
    static let memoryLimitBytes: UInt64 = 128 << 20

    let thread = JSThread(name: "HTMLScript", qos: .userInitiated)

    private struct TimerKey: Hashable {
        let script: ObjectIdentifier
        let id: Int
    }

    private struct Timer {
        let script: HTMLScriptWeak
        let id: Int
        var due: CFTimeInterval
        let interval: CFTimeInterval
        let repeats: Bool
    }

    private var frameSubscribers: [ObjectIdentifier: HTMLScriptWeak] = [:]
    private lazy var clock = HTMLScriptClock(display: CGMainDisplayID(), deliver: { [thread] block in thread.async(block) }, tick: { [weak self] in
        self?.tick()
    })

    var frameInterval: TimeInterval {
        return clock.interval
    }
    private var idleTicks = 0
    private var timers: [TimerKey: Timer] = [:]
    private var wake: Foundation.Timer?
    private var wakeDue: CFTimeInterval = .infinity
    private var changed: [ObjectIdentifier: HTMLScriptWeak] = [:]
    private var notifyScheduled = false

    func perform(_ block: @escaping () -> Void) {
        thread.async(block)
    }

    func performAndWait(_ block: @escaping () -> Void) {
        thread.sync(block)
    }

    func perform(after delay: TimeInterval, _ block: @escaping () -> Void) {
        _ = thread.after(delay, block)
    }

    private(set) var footprintBaseline: UInt64 = 0
    private var baselineFresh = false

    func setDisplay(_ display: CGDirectDisplayID) {
        assert(thread.isCurrent)
        clock.setDisplay(display)
    }

    func beginCall() {
        if !baselineFresh {
            baselineFresh = true
            footprintBaseline = HTMLScriptEngine.footprint()
            thread.async { [weak self] in
                self?.baselineFresh = false
            }
        }
    }

    func requestFrame(for script: HTMLScript) {
        assert(thread.isCurrent)
        frameSubscribers[ObjectIdentifier(script)] = HTMLScriptWeak(script)
        idleTicks = 0
        if !clock.isRunning {
            clock.start()
            rescheduleWake()
        }
    }

    func cancelFrames(for script: HTMLScript) {
        assert(thread.isCurrent)
        frameSubscribers[ObjectIdentifier(script)] = nil
    }

    func scheduleTimer(for script: HTMLScript, id: Int, interval: CFTimeInterval, repeats: Bool) {
        assert(thread.isCurrent)
        timers[TimerKey(script: ObjectIdentifier(script), id: id)] = Timer(script: HTMLScriptWeak(script), id: id, due: CACurrentMediaTime() + interval, interval: interval, repeats: repeats)
        rescheduleWake()
    }

    func cancelTimer(for script: HTMLScript, id: Int) {
        assert(thread.isCurrent)
        timers[TimerKey(script: ObjectIdentifier(script), id: id)] = nil
    }

    func cancelTimers(for script: HTMLScript) {
        assert(thread.isCurrent)
        let owner = ObjectIdentifier(script)
        timers = timers.filter { $0.key.script != owner }
    }

    func scriptChanged(_ script: HTMLScript) {
        assert(thread.isCurrent)
        changed[ObjectIdentifier(script)] = HTMLScriptWeak(script)
        if notifyScheduled {
            return
        }
        notifyScheduled = true
        thread.async { [weak self] in
            self?.flushChanged()
        }
    }

    private func flushChanged() {
        notifyScheduled = false
        let scripts = changed.values.compactMap { $0.value }
        changed.removeAll(keepingCapacity: true)
        if scripts.isEmpty {
            return
        }
        DispatchQueue.main.async {
            for script in scripts {
                script.deliverChange()
            }
        }
    }

    private func tick() {
        runDueTimers(until: CACurrentMediaTime() + frameInterval / 2)
        let due = frameSubscribers.values.compactMap { $0.value }
        frameSubscribers.removeAll(keepingCapacity: true)
        for script in due {
            script.frameTick()
        }
        if frameSubscribers.isEmpty {
            idleTicks += 1
            if idleTicks > 2 {
                clock.stop()
                rescheduleWake()
            }
        } else {
            idleTicks = 0
        }
    }

    private func runDueTimers(until limit: CFTimeInterval) {
        if timers.isEmpty {
            return
        }
        let due = timers.filter { $0.value.due <= limit }.sorted { $0.value.due < $1.value.due }
        for (key, timer) in due {
            guard let script = timer.script.value else {
                timers[key] = nil
                continue
            }
            if timer.repeats {
                var next = timer
                next.due = max(timer.due + timer.interval, CACurrentMediaTime())
                timers[key] = next
            } else {
                timers[key] = nil
            }
            script.timerFired(timer.id, repeats: timer.repeats)
        }
    }

    private func rescheduleWake() {
        if clock.isRunning || timers.isEmpty {
            wake?.invalidate()
            wake = nil
            wakeDue = .infinity
            return
        }
        var earliest = CFTimeInterval.infinity
        var shortest = CFTimeInterval.infinity
        for timer in timers.values {
            earliest = min(earliest, timer.due)
            shortest = min(shortest, timer.interval)
        }
        if wake != nil, abs(earliest - wakeDue) < 0.001 {
            return
        }
        wake?.invalidate()
        wakeDue = earliest
        let timer = Foundation.Timer(timeInterval: max(0, earliest - CACurrentMediaTime()), repeats: false) { [weak self] _ in
            guard let self = self else {
                return
            }
            self.wake = nil
            self.wakeDue = .infinity
            self.runDueTimers(until: CACurrentMediaTime() + 0.004)
            self.rescheduleWake()
        }
        timer.tolerance = min(shortest * 0.1, 0.05)
        RunLoop.current.add(timer, forMode: .default)
        wake = timer
    }

    static func footprint() -> UInt64 {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        return result == KERN_SUCCESS ? info.phys_footprint : 0
    }
}

final class HTMLScriptWeak {
    weak var value: HTMLScript?
    init(_ value: HTMLScript) {
        self.value = value
    }
}

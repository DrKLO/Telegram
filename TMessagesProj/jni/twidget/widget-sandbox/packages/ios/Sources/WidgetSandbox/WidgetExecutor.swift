import Foundation
import QuartzCore

public final class WidgetExecutor {
    private let lock = NSLock()
    private var queue: DispatchQueue?

    public init(queue: DispatchQueue? = nil) {
        self.queue = queue
    }

    public var isMain: Bool {
        lock.lock()
        defer { lock.unlock() }
        return queue == nil
    }

    private var current: DispatchQueue? {
        lock.lock()
        defer { lock.unlock() }
        return queue
    }

    public var isCurrent: Bool {
        if let queue = current {
            return DispatchQueue.getSpecific(key: WidgetExecutor.key) == ObjectIdentifier(queue)
        }
        return Thread.isMainThread
    }

    private static let key = DispatchSpecificKey<ObjectIdentifier>()

    public static func makeQueue(_ label: String) -> DispatchQueue {
        let queue = DispatchQueue(label: label, qos: .userInitiated)
        queue.setSpecific(key: key, value: ObjectIdentifier(queue))
        return queue
    }

    private static func wrapped(_ block: @escaping () -> Void) -> () -> Void {
        return {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            block()
            CATransaction.commit()
        }
    }

    private static let urgentLock = NSLock()
    private static var urgentByQueue: [ObjectIdentifier: [(WidgetExecutor, () -> Void)]] = [:]

    private static func runUrgent(_ queue: DispatchQueue) {
        urgentLock.lock()
        let items = urgentByQueue.removeValue(forKey: ObjectIdentifier(queue)) ?? []
        urgentLock.unlock()
        for (executor, block) in items {
            if executor.current === queue {
                block()
            } else {
                executor.async(block)
            }
        }
    }

    private func enqueue(_ queue: DispatchQueue, _ block: @escaping () -> Void, moved: @escaping (@escaping () -> Void) -> Void) {
        queue.async {
            if self.current === queue {
                WidgetExecutor.wrapped {
                    WidgetExecutor.runUrgent(queue)
                    block()
                }()
            } else {
                moved(block)
            }
        }
    }

    public func urgent(_ block: @escaping () -> Void) {
        guard let queue = current else {
            return async(block)
        }
        WidgetExecutor.urgentLock.lock()
        WidgetExecutor.urgentByQueue[ObjectIdentifier(queue), default: []].append((self, block))
        WidgetExecutor.urgentLock.unlock()
        queue.async {
            WidgetExecutor.wrapped {
                WidgetExecutor.runUrgent(queue)
            }()
        }
    }

    private var pacedBlocks: [() -> Void] = []
    private var pacedDrainQueued = false

    public func paced(_ block: @escaping () -> Void) {
        guard let queue = current else {
            return MainPacer.shared.async(block)
        }
        lock.lock()
        pacedBlocks.append(block)
        let start = !pacedDrainQueued
        pacedDrainQueued = true
        lock.unlock()
        if start {
            enqueue(queue, { [weak self] in self?.drainPaced() }) { [weak self] _ in DispatchQueue.main.async { self?.drainPaced() } }
        }
    }

    private func drainPaced() {
        lock.lock()
        let blocks = pacedBlocks
        pacedBlocks.removeAll()
        pacedDrainQueued = false
        let moved = queue == nil
        lock.unlock()
        if moved {
            blocks.forEach { MainPacer.shared.async($0) }
            return
        }
        for block in blocks {
            if let queue = current {
                WidgetExecutor.runUrgent(queue)
            }
            block()
        }
    }

    public func async(_ block: @escaping () -> Void) {
        if let queue = current {
            enqueue(queue, block) { self.async($0) }
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    public func after(_ seconds: TimeInterval, _ block: @escaping () -> Void) {
        if let queue = current {
            queue.asyncAfter(deadline: .now() + seconds) { [weak self] in
                self?.async(block)
            }
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: block)
        }
    }

    public static var stayOffMain: Bool = {
        if let value = ProcessInfo.processInfo.environment["TW_STAY_OFFMAIN"] {
            return value != "0"
        }
        if UserDefaults.standard.object(forKey: "TwidgetOffMain") != nil {
            return UserDefaults.standard.bool(forKey: "TwidgetOffMain")
        }
        return true
    }()

    public func moveToMain(_ completion: @escaping () -> Void) {
        if WidgetExecutor.stayOffMain, current != nil {
            DispatchQueue.main.async(execute: completion)
            return
        }
        guard let queue = current else {
            if Thread.isMainThread {
                completion()
            } else {
                DispatchQueue.main.async(execute: completion)
            }
            return
        }
        queue.async {
            self.lock.lock()
            self.queue = nil
            self.lock.unlock()
            DispatchQueue.main.async(execute: completion)
        }
    }
}

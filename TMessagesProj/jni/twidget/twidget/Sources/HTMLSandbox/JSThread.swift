// A QuickJS runtime is pinned to one thread with a known, large stack.
//
// Why not a serial DispatchQueue: GCD runs the queue's blocks on whichever
// pool thread is free (512 KB stack each) and QuickJS measures recursion from
// the stack pointer it saw at creation — later blocks on another thread hit a
// bogus "Maximum call stack size exceeded", and a real deep recursion would
// overrun the small pool stack before QuickJS could report it. The iOS main
// thread (1 MB) is not much better for the validator. So every runtime gets a
// dedicated Thread with `stackSize` bytes, and the QuickJS limit stays well
// under it, which turns any overflow into a catchable RangeError.

import Foundation

public final class JSThread {
    /// Real stack of the thread; `QuickJSContext.maxStackBytes` must stay below it.
    public static let stackSize = 8 << 20
    /// Safe QuickJS limit for a context that only ever runs on a `JSThread`.
    public static let jsStackLimit = 6 << 20

    private let thread: Thread
    private var runLoop: CFRunLoop!
    private let ready = DispatchSemaphore(value: 0)

    public init(name: String, qos: QualityOfService = .userInitiated) {
        var loop: CFRunLoop!
        let ready = self.ready
        thread = Thread {
            loop = CFRunLoopGetCurrent()
            // A port keeps the run loop alive while there is nothing scheduled.
            RunLoop.current.add(Port(), forMode: .default)
            ready.signal()
            CFRunLoopRun()
        }
        thread.name = name
        thread.stackSize = Self.stackSize
        thread.qualityOfService = qos
        thread.start()
        ready.wait()
        runLoop = loop
    }

    public var isCurrent: Bool { Thread.current === thread }

    /// Runs `block` on the thread, after everything queued before it.
    public func async(_ block: @escaping () -> Void) {
        CFRunLoopPerformBlock(runLoop, CFRunLoopMode.defaultMode.rawValue, block)
        CFRunLoopWakeUp(runLoop)
    }

    /// Runs `block` on the thread and waits for it (inline when already there).
    /// `block` is escaping because CFRunLoop releases a performed block only
    /// after it returns — later than our semaphore fires — so a non-escaping
    /// wrapper (`withoutActuallyEscaping`) trips the runtime check at random.
    public func sync<T>(_ block: @escaping () -> T) -> T {
        if isCurrent { return block() }
        let done = DispatchSemaphore(value: 0)
        var result: T?
        async { result = block(); done.signal() }
        done.wait()
        return result!
    }

    public final class Scheduled {
        fileprivate var cancelled = false
        fileprivate weak var timer: Timer?
        fileprivate weak var owner: JSThread?
        /// Safe from any thread; the block will not run after this returns.
        public func cancel() {
            cancelled = true
            guard let owner else { return }
            if owner.isCurrent {
                timer?.invalidate()
            } else {
                owner.async { [weak self] in self?.timer?.invalidate() }
            }
        }
    }

    /// Runs `block` on the thread after `delay` seconds unless cancelled.
    public func after(_ delay: TimeInterval, _ block: @escaping () -> Void) -> Scheduled {
        let handle = Scheduled()
        handle.owner = self
        async { [runLoop] in
            if handle.cancelled { return }
            let t = Timer(timeInterval: max(0, delay), repeats: false) { _ in
                if !handle.cancelled { block() }
            }
            handle.timer = t
            CFRunLoopAddTimer(runLoop, t, CFRunLoopMode.defaultMode)
        }
        return handle
    }

    /// Stops the run loop once queued work has run; the thread then exits.
    public func stop() {
        async { [runLoop] in CFRunLoopStop(runLoop) }
    }
}

import Foundation
import QuartzCore

/// Main-thread delivery for hosts that validate off the main thread: blocks run
/// in the order they were queued, at most `budget` seconds of them per main run
/// loop turn, so a burst of renderer work from many widgets cannot hold the main
/// thread for a whole frame.
final class MainPacer {
    static let shared = MainPacer()

    var budget: CFTimeInterval = 0.004
    private let lock = NSLock()
    private var queue: [() -> Void] = []
    private var head = 0
    private var scheduled = false

    func async(_ block: @escaping () -> Void) {
        lock.lock()
        queue.append(block)
        let schedule = !scheduled
        scheduled = true
        lock.unlock()
        if schedule {
            DispatchQueue.main.async { self.drain() }
        }
    }

    private func drain() {
        let start = CACurrentMediaTime()
        while true {
            lock.lock()
            if head == queue.count {
                queue.removeAll(keepingCapacity: true)
                head = 0
                scheduled = false
                lock.unlock()
                return
            }
            let block = queue[head]
            queue[head] = {}
            head += 1
            lock.unlock()
            block()
            if CACurrentMediaTime() - start > budget { break }
        }
        let timer = Timer(timeInterval: 0, repeats: false) { [weak self] _ in self?.drain() }
        RunLoop.main.add(timer, forMode: .common)
    }
}

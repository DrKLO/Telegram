import Foundation

final class WidgetRasterLifetime {
    private let lock = NSLock()
    private var active = true

    var isActive: Bool {
        lock.lock()
        defer { lock.unlock() }
        return active
    }

    func cancel() {
        lock.lock()
        active = false
        lock.unlock()
    }
}

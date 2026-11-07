import Foundation
import CoreGraphics
import QuartzCore

public final class WidgetPrepared {
    public let host: WidgetHost
    public let width: CGFloat
    public let theme: WidgetTheme
    public let startedAt: CFTimeInterval
    private let lock = NSLock()
    private let done = DispatchSemaphore(value: 0)
    private var signaled = false
    private var _height: CGFloat = 0
    private var _ready = false
    private var _readyAt: CFTimeInterval?
    private var _adopted = false

    init(host: WidgetHost, width: CGFloat, theme: WidgetTheme) {
        self.host = host
        self.width = width
        self.theme = theme
        self.startedAt = CACurrentMediaTime()
    }

    public var height: CGFloat {
        lock.lock()
        defer { lock.unlock() }
        return _height
    }

    public var isReady: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _ready
    }

    public var readyLatency: CFTimeInterval? {
        lock.lock()
        defer { lock.unlock() }
        return _readyAt.map { $0 - startedAt }
    }

    private var _onReady: ((WidgetPrepared) -> Void)?

    /// Called once on the main thread when the widget reached its first laid-out content (immediately if it already has).
    public var onReady: ((WidgetPrepared) -> Void)? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _onReady
        }
        set {
            lock.lock()
            _onReady = newValue
            let fire = _ready && newValue != nil
            lock.unlock()
            if let newValue = newValue, fire {
                DispatchQueue.main.async {
                    newValue(self)
                }
            }
        }
    }

    func update(_ diagnostics: WidgetDiagnostics) {
        lock.lock()
        if diagnostics.height > 0 {
            _height = diagnostics.height
        }
        let ready = !_ready && ((diagnostics.rendered && diagnostics.firstContentMs != nil) || diagnostics.dead != nil)
        if ready {
            _ready = true
            _readyAt = CACurrentMediaTime()
        }
        let signal = ready && !signaled
        if signal {
            signaled = true
        }
        lock.unlock()
        if signal {
            done.signal()
            lock.lock()
            let onReady = _onReady
            lock.unlock()
            if let onReady = onReady {
                DispatchQueue.main.async {
                    onReady(self)
                }
            }
        }
    }

    @discardableResult
    public func wait(timeout: TimeInterval) -> Bool {
        if isReady {
            return true
        }
        if timeout <= 0 {
            return false
        }
        if done.wait(timeout: .now() + timeout) == .success {
            done.signal()
            return true
        }
        return false
    }

    public func adopt() -> WidgetHost? {
        lock.lock()
        defer { lock.unlock() }
        if _adopted {
            return nil
        }
        _adopted = true
        return host
    }

    public var isAdopted: Bool {
        lock.lock()
        defer { lock.unlock() }
        return _adopted
    }

    public func discard() {
        if adopt() != nil {
            host.destroy()
        }
        WidgetPreparer.forget(self)
    }
}

public enum WidgetPreparer {
    public static var queueCount = 2
    public static var outstandingLimit = 16
    private static let lock = NSLock()
    private static var queues: [DispatchQueue] = []
    private static var next = 0
    private static var outstanding: [ObjectIdentifier: WidgetPrepared] = [:]
    private static var order: [ObjectIdentifier] = []
    public private(set) static var started = 0
    public private(set) static var evicted = 0

    static func sharedQueue() -> DispatchQueue {
        lock.lock()
        defer { lock.unlock() }
        return nextQueue()
    }

    private static func nextQueue() -> DispatchQueue {
        if queues.isEmpty {
            queues = (0..<max(1, queueCount)).map { WidgetExecutor.makeQueue("widget.prepare.\($0)") }
        }
        let queue = queues[next % queues.count]
        next += 1
        return queue
    }

    public static var shellWait: TimeInterval = 0.01

    public static func prepare(html: String, theme: WidgetTheme, width: CGFloat, options: WidgetHost.Options, tracked: Bool = true) -> WidgetPrepared? {
        guard let shell = WidgetShellPool.shared.take(width: width, wait: shellWait) else {
            return nil
        }
        lock.lock()
        let queue = nextQueue()
        started += 1
        lock.unlock()
        let executor = WidgetExecutor(queue: queue)
        var options = options
        options.visible = false
        var made: WidgetHost?
        queue.sync {
            CATransaction.begin()
            defer { CATransaction.commit() }
            made = try? WidgetHost(html: html, theme: theme, width: width, options: options, executor: executor, shell: shell)
        }
        guard let host = made else {
            return nil
        }
        let prepared = WidgetPrepared(host: host, width: width, theme: theme)
        queue.async {
            host.onChangeOnExecutor = { [weak prepared] diagnostics in
                prepared?.update(diagnostics)
            }
            prepared.update(host.diagnostics)
        }
        if !tracked {
            return prepared
        }
        var victims: [WidgetPrepared] = []
        lock.lock()
        let key = ObjectIdentifier(prepared)
        outstanding[key] = prepared
        order.append(key)
        while order.count > outstandingLimit {
            let old = order.removeFirst()
            if let victim = outstanding.removeValue(forKey: old) {
                victims.append(victim)
                evicted += 1
            }
        }
        lock.unlock()
        victims.forEach { $0.discard() }
        return prepared
    }

    static func forget(_ prepared: WidgetPrepared) {
        lock.lock()
        let key = ObjectIdentifier(prepared)
        if outstanding.removeValue(forKey: key) != nil {
            order.removeAll { $0 == key }
        }
        lock.unlock()
    }

    public static func adopted(_ prepared: WidgetPrepared) {
        forget(prepared)
    }

    public static var outstandingCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return outstanding.count
    }
}

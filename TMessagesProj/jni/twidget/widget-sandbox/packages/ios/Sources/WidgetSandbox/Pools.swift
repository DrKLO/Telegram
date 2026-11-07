// Pre-warmed sandbox parts for hosts that come and go quickly (a scrolling
// chat): engines whose runtime already evaluated the sandbox bundle, and
// validators (one trusted QuickJS context each, all on the shared validator
// thread) that are recycled with a fresh instance when a host is destroyed.
// Renderers have their own pool next to the WebKit environment
// (`RendererPool`); `WidgetWarmup` fills all three. Main thread only.
//
// An engine is never recycled: once widget code ran in a runtime, the runtime
// dies with the widget. A validator never ran untrusted code, so recycling it
// is `__twValidator.create()` again in the same context.

import Foundation
import QuartzCore

public final class EnginePool {
    public static let shared = EnginePool()

    /// Engines to keep prewarmed; 0 disables the pool.
    public var targetSize = 0 { didSet { refill() } }
    public var resources: SandboxResources = .shared
    public private(set) var created = 0, taken = 0, hits = 0
    /// Features new engines are prewarmed with (`SandboxEngine.runtimeFeatures`); set it to what the hosts advertise. Main thread.
    public var features: Set<String> = [] {
        didSet {
            guard features != oldValue else { return }
            drain()
            refill()
        }
    }
    private var idle: [SandboxEngine] = []
    private let lock = NSLock()

    public var count: Int { lock.lock(); defer { lock.unlock() }; return idle.count }

    public func prewarm(_ n: Int) {
        targetSize = max(targetSize, n)
        refill()
    }

    /// A prewarmed engine (its bundle evaluation may still be running on its own thread), or nil when empty.
    /// Only an engine built with exactly `features` is handed out; anything else would silently lose or gain host functions.
    public func take(features: Set<String> = []) -> SandboxEngine? {
        lock.lock()
        taken += 1
        let e = features == self.features ? idle.popLast() : nil
        if e != nil { hits += 1 }
        lock.unlock()
        scheduleRefill()
        return e
    }

    private var refillScheduled = false
    private func scheduleRefill() {
        lock.lock()
        if refillScheduled || idle.count >= targetSize { lock.unlock(); return }
        refillScheduled = true
        lock.unlock()
        DispatchQueue.main.async { [self] in
            lock.lock()
            refillScheduled = false
            lock.unlock()
            refill()
        }
    }

    private func refill() {
        while count < targetSize {
            let t0 = CACurrentMediaTime()
            let e = SandboxEngine(resources: resources, features: features)
            e.prewarm()
            lock.lock()
            idle.append(e)
            lock.unlock()
            created += 1
            WidgetWarmup.mainThreadWork?("engine-create", (CACurrentMediaTime() - t0) * 1000)
        }
    }

    public func drain() {
        lock.lock()
        let engines = idle
        idle.removeAll()
        lock.unlock()
        engines.forEach { $0.terminate() }
    }
}

public final class ValidatorPool {
    public static let shared = ValidatorPool()

    public var targetSize = 0 { didSet { refill() } }
    public var resources: SandboxResources = .shared
    public private(set) var created = 0, taken = 0, hits = 0, recycled = 0
    private var idle: [TrustedValidator] = []
    private let lock = NSLock()

    public var count: Int { lock.lock(); defer { lock.unlock() }; return idle.count }

    public func prewarm(_ n: Int) {
        targetSize = max(targetSize, n)
        refill()
    }

    public func take(theme: String) -> TrustedValidator? {
        lock.lock()
        taken += 1
        let taken = idle.popLast()
        if taken != nil { hits += 1 }
        lock.unlock()
        guard let v = taken else { scheduleRefill(); return nil }
        if theme != "light" { v.perform { v.reset(theme: theme) } }
        scheduleRefill()
        return v
    }

    private var refillScheduled = false
    private func scheduleRefill() {
        lock.lock()
        if refillScheduled || idle.count >= targetSize { lock.unlock(); return }
        refillScheduled = true
        lock.unlock()
        DispatchQueue.main.async { [self] in
            lock.lock()
            refillScheduled = false
            lock.unlock()
            refill()
        }
    }

    /// Back into the pool with a fresh instance (the previous widget's node model
    /// is gone): cheaper than a new context, so the pool holds up to twice its
    /// target of recycled validators.
    public func recycle(_ validator: TrustedValidator) {
        guard count < max(targetSize * 2, 1) else { return }
        validator.perform { [self] in
            validator.onDiag = { _, _ in }
            validator.reset(theme: "light")
            lock.lock()
            idle.append(validator)
            recycled += 1
            lock.unlock()
        }
    }

    /// New contexts are built on the shared validator thread; the main thread never waits for the bundle.
    private func refill() {
        while count < targetSize {
            let t0 = CACurrentMediaTime()
            let made = TrustedValidator.background(theme: "light", resources: resources, thread: TrustedValidator.sharedThreadForNextValidator(), onDiag: { _, _ in })
            lock.lock()
            idle.append(made)
            lock.unlock()
            created += 1
            WidgetWarmup.mainThreadWork?("validator-create", (CACurrentMediaTime() - t0) * 1000)
        }
    }

    public func drain() { lock.lock(); idle.removeAll(); lock.unlock() }
}

/// Everything a chat screen prewarms before the first bubble: the WebKit
/// environment (rule list, web content process), renderer documents, engines
/// with the sandbox bundle evaluated, validator contexts.
public enum WidgetWarmup {
    /// Main-thread work the pools do (name, ms): a profiler hook for the demo's hitch hunt.
    public static var mainThreadWork: ((String, Double) -> Void)?

    public static func prewarm(renderers: Int, engines: Int, validators: Int, width: CGFloat = 360, parkingView: PlatformView? = nil) {
        _ = SandboxResources.shared
        _ = SandboxEngine.parse("{\"type\":\"ready\"}")
        _ = TrustedValidator.sharedThreadForNextValidator()
        if renderers > 0 { RendererEnvironment.shared.prewarm() }
        RendererPool.shared.width = width
        if let parkingView { RendererPool.shared.parkingView = parkingView }
        RendererPool.shared.prewarm(renderers)
        EnginePool.shared.prewarm(engines)
        ValidatorPool.shared.prewarm(validators)
    }

    /// One line for a diagnostics panel.
    public static var status: String {
        "pools: renderers \(RendererPool.shared.count)/\(RendererPool.shared.targetSize) (hits \(RendererPool.shared.hits)/\(RendererPool.shared.taken)), engines \(EnginePool.shared.count)/\(EnginePool.shared.targetSize) (hits \(EnginePool.shared.hits)/\(EnginePool.shared.taken)), validators \(ValidatorPool.shared.count)/\(ValidatorPool.shared.targetSize) (hits \(ValidatorPool.shared.hits)/\(ValidatorPool.shared.taken), recycled \(ValidatorPool.shared.recycled)), rule list \(RendererEnvironment.shared.ruleListSource)"
    }
}

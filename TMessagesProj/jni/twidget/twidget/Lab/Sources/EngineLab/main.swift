import Foundation
import JavaScriptCore
import HTMLSandbox
import HTMLScript

let labRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let repoRoot = labRoot.deletingLastPathComponent()
let chatsDir = repoRoot.appendingPathComponent("Examples/chats")
let scriptFile = repoRoot.appendingPathComponent("Sources/HTMLScript/HTMLScriptPrelude.swift")

let args = CommandLine.arguments.dropFirst()
func option(_ name: String) -> String? {
    guard let i = args.firstIndex(of: name), args.index(after: i) < args.endIndex else { return nil }
    return args[args.index(after: i)]
}

func threadCPU() -> UInt64 {
    var info = thread_basic_info()
    var count = mach_msg_type_number_t(MemoryLayout<thread_basic_info>.size / MemoryLayout<natural_t>.size)
    let port = mach_thread_self()
    defer { mach_port_deallocate(mach_task_self_, port) }
    _ = withUnsafeMutablePointer(to: &info) {
        $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { thread_info(port, thread_flavor_t(THREAD_BASIC_INFO), $0, &count) }
    }
    return UInt64(info.user_time.seconds + info.system_time.seconds) * 1_000_000_000 + UInt64(info.user_time.microseconds + info.system_time.microseconds) * 1000
}

let prelude: String = {
    let text = try! String(contentsOf: scriptFile)
    let start = text.range(of: "#\"\"\"\n")!.upperBound
    let end = text.range(of: "\n\"\"\"#", range: start ..< text.endIndex)!.lowerBound
    return String(text[start ..< end])
}()

let determinism = """
(function () {
  let seed = 1234567;
  Math.random = function () { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
  const base = 1700000000000;
  let drift = 0;
  Date.now = function () { drift += 0.01; return base + __virtualNow() + drift; };
})();
"""

struct Script {
    let name: String
    let source: String
}

func loadScripts() -> [Script] {
    var out: [Script] = []
    let files = (try? FileManager.default.contentsOfDirectory(atPath: chatsDir.path).filter { $0.hasSuffix(".chat") }.sorted()) ?? []
    let re = try! NSRegularExpression(pattern: "<script\\b[^>]*>([\\s\\S]*?)</script\\s*>", options: [.caseInsensitive])
    for file in files {
        let text = (try? String(contentsOf: chatsDir.appendingPathComponent(file))) ?? ""
        var current: [String]? = nil
        var index = 0
        func flush() {
            if let lines = current {
                let body = lines.joined(separator: "\n")
                let ns = body as NSString
                let sources = re.matches(in: body, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range(at: 1)) }
                if !sources.isEmpty {
                    out.append(Script(name: "\(file.replacingOccurrences(of: ".chat", with: ""))#\(index)", source: sources.joined(separator: "\n")))
                }
                index += 1
            }
        }
        for line in text.components(separatedBy: .newlines) {
            if line.hasPrefix("--- ") || line == "---" {
                flush()
                current = []
                continue
            }
            if current == nil || line.hasPrefix("@on ") { continue }
            current?.append(line)
        }
        flush()
    }
    return out
}

final class Host {
    var now: Double = 0
    var timers: [Int: (due: Double, interval: Double, repeats: Bool)] = [:]
    var frameRequested = false
}

protocol Engine {
    var host: Host { get }
    func fire(_ id: Int) -> Bool
    func frame() -> Bool
    func render() -> String?
    var error: String? { get }
}

typealias ShouldTerminate = @convention(c) (OpaquePointer?, UnsafeMutableRawPointer?) -> Bool
typealias SetLimit = @convention(c) (OpaquePointer?, Double, ShouldTerminate?, UnsafeMutableRawPointer?) -> Void
let setLimit: SetLimit? = dlsym(UnsafeMutableRawPointer(bitPattern: -2), "JSContextGroupSetExecutionTimeLimit").map { unsafeBitCast($0, to: SetLimit.self) }

final class JSCEngine: Engine {
    let host = Host()
    let context: JSContext
    var hooks: JSValue!
    var error: String?

    init(source: String) {
        context = JSContext()
        setLimit?(JSContextGetGroup(context.jsGlobalContextRef), 0.05, { _, _ in true }, nil)
        let host = self.host
        context.exceptionHandler = { [weak self] _, e in self?.error = e?.toString() }
        let native = JSValue(newObjectIn: context)!
        let schedule: @convention(block) (Int, Double, Bool) -> Void = { id, ms, repeats in
            let interval = max(50, ms)
            host.timers[id] = (host.now + interval, interval, repeats)
        }
        let cancel: @convention(block) (Int) -> Void = { id in host.timers[id] = nil }
        let nop: @convention(block) () -> Void = {}
        let requestFrame: @convention(block) () -> Void = { host.frameRequested = true }
        let log: @convention(block) (String, String) -> Void = { _, _ in }
        let one: @convention(block) (String) -> Void = { _ in }
        let virtualNow: @convention(block) () -> Double = { host.now }
        native.setObject(schedule, forKeyedSubscript: "schedule" as NSString)
        native.setObject(cancel, forKeyedSubscript: "cancel" as NSString)
        native.setObject(nop, forKeyedSubscript: "update" as NSString)
        native.setObject(requestFrame, forKeyedSubscript: "requestFrame" as NSString)
        native.setObject(log, forKeyedSubscript: "log" as NSString)
        native.setObject(one, forKeyedSubscript: "transition" as NSString)
        native.setObject(one, forKeyedSubscript: "copy" as NSString)
        context.setObject(virtualNow, forKeyedSubscript: "__virtualNow" as NSString)
        context.evaluateScript(determinism)
        hooks = context.evaluateScript(prelude).call(withArguments: [native, 16, 256 * 1024, ["fade", "slide-left", "slide-right", "slide-up", "slide-down", "pop", "none"]])
        context.evaluateScript(source)
    }

    func fire(_ id: Int) -> Bool { hooks.invokeMethod("fire", withArguments: [id]); return true }
    func frame() -> Bool { hooks.invokeMethod("frame", withArguments: [])?.toBool() ?? false }
    func render() -> String? {
        let v = hooks.invokeMethod("callRender", withArguments: [])
        return v?.isString == true ? v?.toString() : nil
    }
}

final class QJSEngine: Engine {
    let host = Host()
    let js = QuickJSContext(memoryLimitBytes: 64 << 20, maxStackBytes: 4 << 20)
    var error: String?

    func call<T>(_ body: () throws -> T) throws -> T {
        js.deadline = ProcessInfo.processInfo.systemUptime + 0.05
        defer { js.deadline = .infinity }
        return try body()
    }

    init(source: String) {
        let host = self.host
        js.registerFunction("__schedule") { a in
            let id = Int(a[0]) ?? 0, ms = Double(a[1]) ?? 0
            let interval = max(50, ms)
            host.timers[id] = (host.now + interval, interval, a[2] == "1")
            return .undefined
        }
        js.registerFunction("__cancel") { a in host.timers[Int(a[0]) ?? 0] = nil; return .undefined }
        js.registerFunction("__requestFrame") { _ in host.frameRequested = true; return .undefined }
        js.registerFunction("__nop") { _ in .undefined }
        js.registerFunction("__virtualNow") { _ in .number(host.now) }
        do {
            try js.evaluate(determinism, filename: "determinism.js")
            try js.evaluate("""
            globalThis.__hooks = (\(prelude))({
              schedule: (id, ms, repeats) => __schedule(id, ms, repeats ? 1 : 0),
              cancel: id => __cancel(id),
              update: () => __nop(),
              requestFrame: () => __requestFrame(),
              log: () => {}, transition: () => {}, copy: () => {},
            }, 16, 262144, ['fade', 'slide-left', 'slide-right', 'slide-up', 'slide-down', 'pop', 'none']);
            globalThis.__fire = id => __hooks.fire(Number(id));
            globalThis.__frame = () => __hooks.frame() ? 1 : 0;
            globalThis.__render = () => { const h = __hooks.callRender(); return h === null ? undefined : h; };
            """, filename: "prelude.js")
            try call { try js.evaluate(source, filename: "message.js") }
            pump()
        } catch {
            self.error = "\(error)"
        }
    }

    func pump() { js.executePendingJobs { self.error = "\($0)" } }

    func fire(_ id: Int) -> Bool {
        do { try call { try js.call("__fire", [String(id)]) } } catch { self.error = "\(error)" }
        pump()
        return true
    }
    func frame() -> Bool {
        var more = false
        do { more = (try call { try js.call("__frame") }.number ?? 0) != 0 } catch { self.error = "\(error)" }
        pump()
        return more
    }
    func render() -> String? {
        do { return try call { try js.call("__render") }.string } catch { self.error = "\(error)"; return nil }
    }
}

func run(_ engine: Engine, seconds: Double) -> (renders: [String], ms: Double) {
    var renders: [String] = []
    var cpu: UInt64 = 0
    let host = engine.host
    var t0 = threadCPU()
    if let html = engine.render() { renders.append(html) }
    cpu += threadCPU() - t0
    let step = 1000.0 / 60
    var now = 0.0
    while now < seconds * 1000 {
        now += step
        host.now = now
        t0 = threadCPU()
        var changed = false
        let due = host.timers.filter { $0.value.due <= now }.sorted { $0.value.due < $1.value.due }
        for (id, timer) in due {
            if timer.repeats { host.timers[id] = (timer.due + timer.interval, timer.interval, true) } else { host.timers[id] = nil }
            _ = engine.fire(id)
            changed = true
        }
        if host.frameRequested {
            host.frameRequested = false
            if engine.frame() { host.frameRequested = true }
            changed = true
        }
        if changed, let html = engine.render() { renders.append(html) }
        cpu += threadCPU() - t0
    }
    return (renders, Double(cpu) / 1e6)
}

setvbuf(stdout, nil, _IOLBF, 0)
func footprint() -> Double {
    var info = task_vm_info_data_t()
    var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<natural_t>.size)
    _ = withUnsafeMutablePointer(to: &info) { $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count) } }
    return Double(info.phys_footprint) / 1048576
}
if args.contains("--vm-cost") {
    let source = loadScripts().first { $0.name.contains("motion-lab#1") }!.source
    for shared in [true, false] {
        let base = footprint()
        let vm = JSVirtualMachine()!
        var keep: [JSContext] = []
        let t0 = threadCPU()
        for _ in 0 ..< 40 {
            let ctx = JSContext(virtualMachine: shared ? vm : JSVirtualMachine())!
            ctx.evaluateScript("(\(prelude))")?.call(withArguments: [JSValue(newObjectIn: ctx)!, 16, 262144, []])
            ctx.evaluateScript(source)
            keep.append(ctx)
        }
        let ms = Double(threadCPU() - t0) / 1e6 / 40
        print(String(format: "%@: %.2f ms to create one, footprint +%.2f MB per script", shared ? "shared VM" : "VM per script", ms, (footprint() - base) / 40))
        keep.removeAll()
    }
    exit(0)
}
let seconds = Double(option("--seconds") ?? "4") ?? 4
let filter = option("--only")
var totals = (jsc: 0.0, qjs: 0.0, frames: 0, mismatched: 0)
print(String(format: "%-24@ %7@ %9@ %9@ %6@ %@", "script" as NSString, "frames" as NSString, "JSC ms/f" as NSString, "QJS ms/f" as NSString, "ratio" as NSString, "same output" as NSString))
for script in loadScripts() where filter == nil || script.name.contains(filter!) {
    autoreleasepool {
        let a = JSCEngine(source: script.source)
        let ra = run(a, seconds: seconds)
        let b = QJSEngine(source: script.source)
        let rb = run(b, seconds: seconds)
        let frames = max(ra.renders.count, 1)
        let same = ra.renders == rb.renders
        totals.jsc += ra.ms
        totals.qjs += rb.ms
        totals.frames += frames
        if !same { totals.mismatched += 1 }
        let errors = [a.error.map { "JSC: \($0)" }, b.error.map { "QJS: \($0)" }].compactMap { $0 }.joined(separator: "; ")
        print(String(format: "%-24@ %7d %9.3f %9.3f %6.2f %@ %@", script.name as NSString, frames, ra.ms / Double(frames), rb.ms / Double(frames), rb.ms / max(ra.ms, 0.001), (same ? "yes" : "NO (\(ra.renders.count) vs \(rb.renders.count))") as NSString, errors as NSString))
    }
}
print(String(format: "TOTAL %d renders: JSC %.3f ms, QuickJS %.3f ms per render (%.2fx); %d scripts with different output", totals.frames, totals.jsc / Double(max(totals.frames, 1)), totals.qjs / Double(max(totals.frames, 1)), totals.qjs / max(totals.jsc, 0.001), totals.mismatched))

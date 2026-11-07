// Thin Swift wrapper over quickjs-ng (through CQuickJS/qjs_shim). One runtime,
// one context, string-based host functions. All methods must be called from
// the thread/queue that owns the context.

import CQuickJS
import Foundation

public enum QuickJSError: Error, CustomStringConvertible {
    case exception(String)
    case notAFunction(String)

    public var description: String {
        switch self {
        case .exception(let text): return text
        case .notAFunction(let name): return "\(name) is not a function"
        }
    }
}

/// Value returned by a host function into the sandbox.
public enum HostReturn {
    case undefined
    case string(String)
    case number(Double)
    case arrayBuffer(Data)
    case null
}

/// Result of calling a sandbox function from the host.
public enum JSResult {
    case undefined
    case string(String)
    case number(Double)

    public var string: String? { if case .string(let s) = self { return s } else { return nil } }
    public var number: Double? { if case .number(let n) = self { return n } else { return nil } }
}

public final class QuickJSContext {
    let runtime: OpaquePointer
    let context: OpaquePointer
    private var functions: [Int32: ([String]) -> HostReturn] = [:]

    /// Interrupt deadline in `ProcessInfo.systemUptime` seconds; `.infinity` disables it.
    public var deadline: TimeInterval = .infinity
    /// Interrupt deadline in CPU seconds of the thread running JS (`threadCPUTime`); `.infinity` disables it.
    public var cpuDeadline: TimeInterval = .infinity

    /// CPU time consumed by the calling thread, in seconds; unaffected by how busy the machine is.
    public static func threadCPUTime() -> TimeInterval {
        var t = timespec()
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t)
        return TimeInterval(t.tv_sec) + TimeInterval(t.tv_nsec) / 1e9
    }
    /// Set by the interrupt handler when the deadline fired; reset by the caller.
    public internal(set) var interrupted = false

    /// `maxStackBytes` must stay below the real stack of whichever thread enters JS
    /// (GCD pool threads: 512 KB; iOS main thread: 1 MB; JSThread: 8 MB). QuickJS
    /// only *detects* overflow against this limit; the OS kills the process at the
    /// real one. A debug build of quickjs-ng needs ~10 KB of C stack per JS call.
    public init(memoryLimitBytes: Int, maxStackBytes: Int = 384 << 10) {
        runtime = JS_NewRuntime()
        JS_SetMaxStackSize(runtime, maxStackBytes)
        // The limit applies once the context exists: a limit too small for the
        // context itself must fail the first evaluation, not the initializer.
        context = JS_NewContext(runtime)
        JS_SetMemoryLimit(runtime, memoryLimitBytes)
        let opaque = Unmanaged.passUnretained(self).toOpaque()
        JS_SetContextOpaque(context, opaque)
        // No module loader is installed: `import()` of anything fails inside the sandbox.
        JS_SetInterruptHandler(runtime, { _, opaque in
            guard let opaque else { return 0 }
            let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
            if ProcessInfo.processInfo.systemUptime > ctx.deadline || (ctx.cpuDeadline != .infinity && QuickJSContext.threadCPUTime() > ctx.cpuDeadline) {
                ctx.interrupted = true
                return 1
            }
            return 0
        }, opaque)
    }

    private var captured: [String: JSValue] = [:]

    deinit {
        for value in captured.values { qjs_free_value(context, value) }
        JS_FreeContext(context)
        JS_FreeRuntime(runtime)
    }

    public var memoryUsed: Int { qjs_memory_used(runtime) }

    /// A prewarmed runtime adopts the limit of the host that takes it.
    public func setMemoryLimit(_ bytes: Int) { JS_SetMemoryLimit(runtime, bytes) }

    /// `JS_ComputeMemoryUsage` in bytes, for diagnostics and memory tests.
    public var memoryUsage: [String: Int] {
        var u = JSMemoryUsage()
        JS_ComputeMemoryUsage(runtime, &u)
        return ["malloc": Int(u.malloc_size), "used": Int(u.memory_used_size), "atoms": Int(u.atom_size), "strings": Int(u.str_size), "objects": Int(u.obj_size), "properties": Int(u.prop_size), "shapes": Int(u.shape_size), "functions": Int(u.js_func_size), "bytecode": Int(u.js_func_code_size), "pc2line": Int(u.js_func_pc2line_size), "binary": Int(u.binary_object_size)]
    }

    public func collectGarbage() { JS_RunGC(runtime) }

    // MARK: host functions

    /// Exposes `name(...)` to the sandbox. Arguments arrive as strings (numbers are formatted).
    public func registerFunction(_ name: String, _ handler: @escaping ([String]) -> HostReturn) {
        let magic = Int32(functions.count)
        functions[magic] = handler
        let fn = qjs_new_cfunction_magic(context, hostTrampoline, name, 2, magic)
        _ = qjs_set_global_prop(context, name, fn) // takes ownership of fn
    }

    fileprivate func dispatch(magic: Int32, args: [String]) -> HostReturn {
        functions[magic]?(args) ?? .undefined
    }

    fileprivate func toJS(_ value: HostReturn) -> JSValue {
        switch value {
        case .undefined: return qjs_undefined()
        case .null: return qjs_null()
        case .string(let s): return s.withCString { qjs_new_string(context, $0) }
        case .number(let n): return qjs_new_float64(context, n)
        case .arrayBuffer(let data):
            return data.withUnsafeBytes { buf in
                qjs_new_array_buffer_copy(context, buf.bindMemory(to: UInt8.self).baseAddress, data.count)
            }
        }
    }

    // MARK: evaluating and calling

    /// QuickJS measures stack depth from the stack pointer it saw when the runtime
    /// was created; re-read it on every entry so a call from another thread (or
    /// from a different depth of the same one) is measured against the right stack.
    /// Contexts in the product are pinned to a JSThread anyway — this is the belt.
    private func enterJS() { JS_UpdateStackTop(runtime) }

    private func takeException() -> QuickJSError {
        guard let c = qjs_take_exception_description(context) else { return .exception("unknown error") }
        defer { free(c) }
        return .exception(String(cString: c))
    }

    private func readResult(_ v: JSValue) -> JSResult {
        defer { qjs_free_value(context, v) }
        if qjs_is_string(v) {
            var length = 0
            guard let c = JS_ToCStringLen2(context, &length, v, false) else { return .undefined }
            defer { JS_FreeCString(context, c) }
            return .string(String(decoding: UnsafeRawBufferPointer(start: c, count: length), as: UTF8.self))
        }
        if qjs_is_number(v) { return .number(qjs_to_float64(context, v)) }
        return .undefined
    }

    @discardableResult
    public func evaluate(_ code: String, filename: String, asModule: Bool = false) throws -> JSResult {
        enterJS()
        var bytes = Array(code.utf8)
        bytes.append(0)
        let v = bytes.withUnsafeBufferPointer { buf in
            buf.baseAddress!.withMemoryRebound(to: CChar.self, capacity: buf.count) { ptr in
                qjs_eval(context, ptr, buf.count - 1, filename, asModule)
            }
        }
        if qjs_is_exception(v) { throw takeException() }
        return readResult(v)
    }

    /// Runs one of our own precompiled bundles (`.qbc`, see qjs_shim.h), checked
    /// against the source it was built from; throws on any mismatch so the caller
    /// can fall back to `evaluate`. Never for widget code: bytecode is not verified.
    @discardableResult
    public func evaluateBytecode(_ blob: Data, source: String) throws -> JSResult {
        enterJS()
        var source = source
        let v = blob.withUnsafeBytes { b in
            source.withUTF8 { s in
                qjs_eval_bytecode(context, b.bindMemory(to: UInt8.self).baseAddress, blob.count, s.withMemoryRebound(to: CChar.self) { $0.baseAddress }, s.count)
            }
        }
        if qjs_is_exception(v) { throw takeException() }
        return readResult(v)
    }

    /// Compiles `code` into the same container the build ships (tests).
    public func compileBytecode(_ code: String, filename: String) throws -> Data {
        enterJS()
        // JS_Eval reads input[len], which must be NUL; String.withUTF8 does not promise a terminator.
        var bytes = Array(code.utf8)
        bytes.append(0)
        var len = 0
        let blob = bytes.withUnsafeBufferPointer { buf in
            buf.baseAddress!.withMemoryRebound(to: CChar.self, capacity: buf.count) { qjs_compile_bytecode(context, $0, buf.count - 1, filename, &len) }
        }
        guard let blob else { throw takeException() }
        defer { qjs_free_bytecode(context, blob) }
        return Data(bytes: blob, count: len)
    }

    private var wordFunctions: [Int32: (Double, UnsafeBufferPointer<Double>, String) -> Void] = [:]

    /// `name(number, words: Float64Array, text)`: the words are only valid during the call.
    public func registerWordsFunction(_ name: String, _ handler: @escaping (Double, UnsafeBufferPointer<Double>, String) -> Void) {
        let magic = Int32(wordFunctions.count)
        wordFunctions[magic] = handler
        let fn = qjs_new_cfunction_magic(context, wordsTrampoline, name, 3, magic)
        _ = qjs_set_global_prop(context, name, fn)
    }

    private var opsFunctions: [Int32: (UnsafeBufferPointer<Int32>, UnsafeBufferPointer<UInt8>) -> Void] = [:]

    /// `name(words: Int32Array, strings: string)`: the words and the string's UTF-8 are only valid during the call.
    public func registerOpsFunction(_ name: String, _ handler: @escaping (UnsafeBufferPointer<Int32>, UnsafeBufferPointer<UInt8>) -> Void) {
        let magic = Int32(opsFunctions.count)
        opsFunctions[magic] = handler
        let fn = qjs_new_cfunction_magic(context, opsTrampoline, name, 2, magic)
        _ = qjs_set_global_prop(context, name, fn)
    }

    fileprivate func dispatchOps(magic: Int32, words: UnsafeBufferPointer<Int32>, strings: UnsafeBufferPointer<UInt8>) {
        opsFunctions[magic]?(words, strings)
    }

    fileprivate func dispatchWords(magic: Int32, number: Double, words: UnsafeBufferPointer<Double>, text: String) {
        wordFunctions[magic]?(number, words, text)
    }

    /// Keeps a private reference to the global function `name`, which `call(name)` uses from then on even after the global is deleted.
    @discardableResult
    public func captureGlobalFunction(_ name: String) -> Bool {
        enterJS()
        let fn = qjs_get_global_prop(context, name)
        guard qjs_is_function(context, fn) else {
            qjs_free_value(context, fn)
            return false
        }
        if let old = captured[name] { qjs_free_value(context, old) }
        captured[name] = fn
        return true
    }

    /// Calls the global function `name` with string arguments.
    @discardableResult
    public func call(_ name: String, _ args: [String] = []) throws -> JSResult {
        enterJS()
        let own = captured[name] == nil
        let fn = captured[name] ?? qjs_get_global_prop(context, name)
        defer { if own { qjs_free_value(context, fn) } }
        guard qjs_is_function(context, fn) else { throw QuickJSError.notAFunction(name) }
        var argv = args.map { arg -> JSValue in
            var arg = arg
            return arg.withUTF8 { b in b.withMemoryRebound(to: CChar.self) { qjs_new_string_len(context, $0.baseAddress, $0.count) } }
        }
        defer { for v in argv { qjs_free_value(context, v) } }
        let r = argv.withUnsafeMutableBufferPointer { buf in qjs_call(context, fn, Int32(buf.count), buf.baseAddress) }
        if qjs_is_exception(r) { throw takeException() }
        return readResult(r)
    }

    public var hasPendingJob: Bool { qjs_is_job_pending(runtime) }

    /// Runs queued promise jobs. Errors thrown by jobs are reported through `onError` and do not stop the loop.
    public func executePendingJobs(onError: (QuickJSError) -> Void) {
        enterJS()
        for _ in 0..<100 {
            var ctx: OpaquePointer? = nil
            let r = qjs_execute_pending_job(runtime, &ctx)
            if r < 0 { onError(takeException()); continue }
            if r == 0 { break }
        }
    }
}

/// Single C trampoline for every registered host function; `magic` selects the Swift closure.
private func hostTrampoline(_ ctxPtr: OpaquePointer?, _ this: JSValue, _ argc: Int32, _ argv: UnsafeMutablePointer<JSValue>?, _ magic: Int32) -> JSValue {
    guard let ctxPtr, let opaque = JS_GetContextOpaque(ctxPtr) else { return qjs_undefined() }
    let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
    var args: [String] = []
    args.reserveCapacity(Int(argc))
    for i in 0..<Int(argc) {
        let v = argv![i]
        if qjs_is_string(v), let c = qjs_to_cstring(ctxPtr, v) {
            args.append(String(cString: c))
            qjs_free_cstring(ctxPtr, c)
        } else if qjs_is_number(v) {
            let d = qjs_to_float64(ctxPtr, v)
            args.append(d == d.rounded() && abs(d) < 1e15 ? String(Int64(d)) : String(d))
        } else {
            args.append("")
        }
    }
    return ctx.toJS(ctx.dispatch(magic: magic, args: args))
}

private func wordsTrampoline(_ ctxPtr: OpaquePointer?, _ this: JSValue, _ argc: Int32, _ argv: UnsafeMutablePointer<JSValue>?, _ magic: Int32) -> JSValue {
    guard let ctxPtr, let opaque = JS_GetContextOpaque(ctxPtr), argc >= 3, let argv else { return qjs_undefined() }
    let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
    guard qjs_is_number(argv[0]), qjs_is_string(argv[2]) else { return qjs_undefined() }
    var pointer: UnsafeMutablePointer<UInt8>?
    var length = 0
    guard qjs_typed_array_bytes(ctxPtr, argv[1], &pointer, &length) == Int32(JS_TYPED_ARRAY_FLOAT64.rawValue), let pointer, length % 8 == 0 else { return qjs_undefined() }
    var count = 0
    guard let c = JS_ToCStringLen2(ctxPtr, &count, argv[2], false) else { return qjs_undefined() }
    let text = String(decoding: UnsafeRawBufferPointer(start: c, count: count), as: UTF8.self)
    JS_FreeCString(ctxPtr, c)
    let number = qjs_to_float64(ctxPtr, argv[0])
    let words = UnsafeRawPointer(pointer).bindMemory(to: Double.self, capacity: length / 8)
    ctx.dispatchWords(magic: magic, number: number, words: UnsafeBufferPointer(start: words, count: length / 8), text: text)
    return qjs_undefined()
}

private func opsTrampoline(_ ctxPtr: OpaquePointer?, _ this: JSValue, _ argc: Int32, _ argv: UnsafeMutablePointer<JSValue>?, _ magic: Int32) -> JSValue {
    guard let ctxPtr, let opaque = JS_GetContextOpaque(ctxPtr), argc >= 2, let argv, qjs_is_string(argv[1]) else { return qjs_undefined() }
    let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
    var pointer: UnsafeMutablePointer<UInt8>?
    var length = 0
    guard qjs_typed_array_bytes(ctxPtr, argv[0], &pointer, &length) == Int32(JS_TYPED_ARRAY_INT32.rawValue), let pointer, length % 4 == 0 else { return qjs_undefined() }
    var count = 0
    guard let c = JS_ToCStringLen2(ctxPtr, &count, argv[1], false) else { return qjs_undefined() }
    defer { JS_FreeCString(ctxPtr, c) }
    let words = UnsafeRawPointer(pointer).bindMemory(to: Int32.self, capacity: length / 4)
    let bytes = UnsafeRawPointer(c).bindMemory(to: UInt8.self, capacity: count)
    ctx.dispatchOps(magic: magic, words: UnsafeBufferPointer(start: words, count: length / 4), strings: UnsafeBufferPointer(start: bytes, count: count))
    return qjs_undefined()
}

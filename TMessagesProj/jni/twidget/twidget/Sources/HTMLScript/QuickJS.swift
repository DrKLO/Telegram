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

/// Argument of a host function registered with `registerArgumentFunction`. Typed array bytes are only valid during the call.
public enum QuickJSArgument {
    case number(Double)
    case string(String)
    case bytes(UnsafeRawBufferPointer, kind: Int32)
    case other

    public static let float32: Int32 = Int32(JS_TYPED_ARRAY_FLOAT32.rawValue)
    public static let uint16: Int32 = Int32(JS_TYPED_ARRAY_UINT16.rawValue)
    public static let uint32: Int32 = Int32(JS_TYPED_ARRAY_UINT32.rawValue)
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
    private var argumentFunctions: [Int32: ([QuickJSArgument]) -> HostReturn] = [:]

    /// Interrupt deadline in `ProcessInfo.systemUptime` seconds; `.infinity` disables it.
    public var deadline: TimeInterval = .infinity
    /// Set by the interrupt handler when the deadline fired; reset by the caller.
    public internal(set) var interrupted = false

    /// `maxStackBytes` must stay below the real stack of whichever thread enters JS
    /// (GCD pool threads: 512 KB; iOS main thread: 1 MB; JSThread: 8 MB). QuickJS
    /// only *detects* overflow against this limit; the OS kills the process at the
    /// real one. A debug build of quickjs-ng needs ~10 KB of C stack per JS call.
    public init(memoryLimitBytes: Int, maxStackBytes: Int = 384 << 10) {
        runtime = JS_NewRuntime()
        JS_SetMemoryLimit(runtime, memoryLimitBytes)
        JS_SetMaxStackSize(runtime, maxStackBytes)
        context = JS_NewContext(runtime)
        let opaque = Unmanaged.passUnretained(self).toOpaque()
        JS_SetContextOpaque(context, opaque)
        // No module loader is installed: `import()` of anything fails inside the sandbox.
        JS_SetInterruptHandler(runtime, { _, opaque in
            guard let opaque else { return 0 }
            let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
            if ProcessInfo.processInfo.systemUptime > ctx.deadline {
                ctx.interrupted = true
                return 1
            }
            return 0
        }, opaque)
    }

    deinit {
        JS_FreeContext(context)
        JS_FreeRuntime(runtime)
    }

    public var memoryUsed: Int { qjs_memory_used(runtime) }

    /// A prewarmed runtime adopts the limit of the host that takes it.
    public func setMemoryLimit(_ bytes: Int) { JS_SetMemoryLimit(runtime, bytes) }

    // MARK: host functions

    /// Exposes `name(...)` to the sandbox. Arguments arrive as strings (numbers are formatted).
    public func registerFunction(_ name: String, _ handler: @escaping ([String]) -> HostReturn) {
        let magic = Int32(functions.count)
        functions[magic] = handler
        let fn = qjs_new_cfunction_magic(context, hostTrampoline, name, 2, magic)
        _ = qjs_set_global_prop(context, name, fn) // takes ownership of fn
    }

    /// Exposes `name(...)` with numbers, strings and typed arrays passed through as they are.
    public func registerArgumentFunction(_ name: String, _ handler: @escaping ([QuickJSArgument]) -> HostReturn) {
        let magic = Int32(argumentFunctions.count)
        argumentFunctions[magic] = handler
        let fn = qjs_new_cfunction_magic(context, argumentTrampoline, name, 0, magic)
        _ = qjs_set_global_prop(context, name, fn)
    }

    fileprivate func dispatchArguments(magic: Int32, args: [QuickJSArgument]) -> HostReturn {
        argumentFunctions[magic]?(args) ?? .undefined
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
        if qjs_is_string(v), let c = qjs_to_cstring(context, v) {
            defer { qjs_free_cstring(context, c) }
            return .string(String(cString: c))
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
    /// can fall back to `evaluate`. Never for untrusted code: bytecode is not verified.
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
        var code = code
        var len = 0
        let blob = code.withUTF8 { s in
            s.withMemoryRebound(to: CChar.self) { qjs_compile_bytecode(context, $0.baseAddress, $0.count, filename, &len) }
        }
        guard let blob else { throw takeException() }
        defer { qjs_free_bytecode(context, blob) }
        return Data(bytes: blob, count: len)
    }

    /// Calls the global function `name` with string arguments.
    @discardableResult
    public func call(_ name: String, _ args: [String] = []) throws -> JSResult {
        enterJS()
        let fn = qjs_get_global_prop(context, name)
        defer { qjs_free_value(context, fn) }
        guard qjs_is_function(context, fn) else { throw QuickJSError.notAFunction(name) }
        var argv = args.map { $0.withCString { qjs_new_string(context, $0) } }
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

private func argumentTrampoline(_ ctxPtr: OpaquePointer?, _ this: JSValue, _ argc: Int32, _ argv: UnsafeMutablePointer<JSValue>?, _ magic: Int32) -> JSValue {
    guard let ctxPtr, let opaque = JS_GetContextOpaque(ctxPtr) else { return qjs_undefined() }
    let ctx = Unmanaged<QuickJSContext>.fromOpaque(opaque).takeUnretainedValue()
    var args: [QuickJSArgument] = []
    args.reserveCapacity(Int(argc))
    for i in 0..<Int(argc) {
        let v = argv![i]
        if qjs_is_number(v) {
            args.append(.number(qjs_to_float64(ctxPtr, v)))
        } else if qjs_is_string(v), let c = qjs_to_cstring(ctxPtr, v) {
            args.append(.string(String(cString: c)))
            qjs_free_cstring(ctxPtr, c)
        } else {
            var pointer: UnsafeMutablePointer<UInt8>?
            var length = 0
            let kind = qjs_typed_array_bytes(ctxPtr, v, &pointer, &length)
            if kind >= 0, let pointer {
                args.append(.bytes(UnsafeRawBufferPointer(start: pointer, count: length), kind: kind))
            } else {
                args.append(.other)
            }
        }
    }
    return ctx.toJS(ctx.dispatchArguments(magic: magic, args: args))
}

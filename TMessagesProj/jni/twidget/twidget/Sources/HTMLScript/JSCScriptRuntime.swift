import Foundation
import JavaScriptCore

public final class JSCScriptRuntime: HTMLScriptRuntime {
    public static let shared = JSCScriptRuntime()

    private typealias ShouldTerminate = @convention(c) (OpaquePointer?, UnsafeMutableRawPointer?) -> Bool
    private typealias SetExecutionTimeLimit = @convention(c) (OpaquePointer?, Double, ShouldTerminate?, UnsafeMutableRawPointer?) -> Void

    private static let shouldTerminate: ShouldTerminate = { _, _ in
        JSCScriptRuntime.shared.terminated = true
        return true
    }

    private var machineStorage: JSVirtualMachine?
    private var setLimit: SetExecutionTimeLimit?
    private var group: OpaquePointer?
    private var defaultLimit: TimeInterval = 0
    fileprivate var terminated = false

    public var machine: JSVirtualMachine {
        if let machine = machineStorage {
            return machine
        }
        let machine = JSVirtualMachine()!
        let probe = JSContext(virtualMachine: machine)!
        if let symbol = dlsym(UnsafeMutableRawPointer(bitPattern: -2), "JSContextGroupSetExecutionTimeLimit") {
            setLimit = unsafeBitCast(symbol, to: SetExecutionTimeLimit.self)
            group = JSContextGetGroup(probe.jsGlobalContextRef)
        }
        machineStorage = machine
        return machine
    }

    public var supportsTimeLimit: Bool {
        _ = machine
        return setLimit != nil
    }

    public func makeContext(name: String, callLimit: TimeInterval) -> HTMLScriptContext {
        _ = machine
        if callLimit != defaultLimit {
            defaultLimit = callLimit
            setLimit?(group, callLimit, JSCScriptRuntime.shouldTerminate, nil)
        }
        return JSCScriptContext(runtime: self, name: name)
    }

    fileprivate func run<T>(limit: TimeInterval?, _ body: () -> T) -> T {
        terminated = false
        guard let limit = limit, let setLimit = setLimit else {
            return body()
        }
        setLimit(group, limit, JSCScriptRuntime.shouldTerminate, nil)
        defer {
            setLimit(group, defaultLimit, JSCScriptRuntime.shouldTerminate, nil)
        }
        return body()
    }
}

public final class JSCScriptContext: HTMLScriptContext {
    public let context: JSContext
    private let runtime: JSCScriptRuntime
    private var hooks: JSValue?
    private var exception: JSValue?

    init(runtime: JSCScriptRuntime, name: String) {
        self.runtime = runtime
        context = JSContext(virtualMachine: runtime.machine)!
        context.name = name
        context.exceptionHandler = { [weak self] _, exception in
            self?.exception = exception
        }
    }

    public func boot(prelude: String, native names: [String], call: @escaping HTMLScriptNativeCall, arguments: [HTMLScriptValue]) throws {
        let native = JSValue(newObjectIn: context)!
        for name in names {
            let function: @convention(block) () -> Void = {
                let args = (JSContext.currentArguments() as? [JSValue] ?? []).map(JSCScriptContext.value)
                call(name, args)
            }
            native.setObject(function, forKeyedSubscript: name as NSString)
        }
        let context = self.context
        let result = try perform(limit: nil) {
            context.evaluateScript(prelude)?.call(withArguments: [native] + arguments.map { self.jsValue($0) })
        }
        hooks = result
    }

    public func evaluate(_ source: String, name: String, timeLimit: TimeInterval?) throws {
        let context = self.context
        _ = try perform(limit: timeLimit) {
            context.evaluateScript(source, withSourceURL: URL(string: name))
        }
    }

    public func invoke(_ hook: String, _ arguments: [HTMLScriptValue]) throws -> HTMLScriptValue {
        guard let hooks = hooks else {
            return .undefined
        }
        let result = try perform(limit: nil) {
            hooks.invokeMethod(hook, withArguments: arguments.map { self.jsValue($0) })
        }
        return result.map(JSCScriptContext.value) ?? .undefined
    }

    public func callExpression(_ source: String, _ arguments: [Any]) throws {
        let context = self.context
        _ = try perform(limit: nil) {
            context.evaluateScript(source)?.call(withArguments: arguments)
        }
    }

    private func perform(limit: TimeInterval?, _ body: () -> JSValue?) throws -> JSValue? {
        exception = nil
        let result = runtime.run(limit: limit, body)
        let thrown = exception
        exception = nil
        if runtime.terminated {
            throw HTMLScriptError(.timeLimit, "time limit")
        }
        if let thrown = thrown {
            var message = hooks?.invokeMethod("describe", withArguments: [thrown])?.toString() ?? thrown.toString() ?? ""
            if runtime.terminated || exception != nil || message.isEmpty {
                message = "Uncaught exception"
            }
            exception = nil
            throw HTMLScriptError(.exception, message)
        }
        return result
    }

    private func jsValue(_ value: HTMLScriptValue) -> Any {
        switch value {
        case .undefined:
            return JSValue(undefinedIn: context) as Any
        case .null:
            return JSValue(nullIn: context) as Any
        case let .bool(value):
            return value
        case let .number(value):
            return value
        case let .string(value):
            return value
        case let .array(values):
            return values.map { jsValue($0) }
        }
    }

    static func value(_ value: JSValue) -> HTMLScriptValue {
        if value.isUndefined {
            return .undefined
        }
        if value.isNull {
            return .null
        }
        if value.isBoolean {
            return .bool(value.toBool())
        }
        if value.isNumber {
            return .number(value.toDouble())
        }
        if value.isString {
            return .string(value.toString())
        }
        if value.isArray, let items = value.toArray() {
            return .array(items.map { item in
                switch item {
                case let item as String:
                    return .string(item)
                case let item as NSNumber:
                    return CFGetTypeID(item) == CFBooleanGetTypeID() ? .bool(item.boolValue) : .number(item.doubleValue)
                default:
                    return .undefined
                }
            })
        }
        return .string(value.toString() ?? "")
    }
}

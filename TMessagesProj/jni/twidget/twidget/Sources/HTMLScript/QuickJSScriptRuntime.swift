import Foundation
import HTMLSandbox

public final class QuickJSScriptRuntime: HTMLScriptRuntime {
    public let memoryLimitBytes: Int

    public init(memoryLimitBytes: Int = 128 << 20) {
        self.memoryLimitBytes = memoryLimitBytes
    }

    public func makeContext(name: String, callLimit: TimeInterval) -> HTMLScriptContext {
        return QuickJSScriptContext(memoryLimitBytes: memoryLimitBytes, callLimit: callLimit)
    }
}

public final class QuickJSScriptContext: HTMLScriptContext {
    public let context: QuickJSContext
    private let callLimit: TimeInterval
    private var native: HTMLScriptNativeCall?

    init(memoryLimitBytes: Int, callLimit: TimeInterval) {
        context = QuickJSContext(memoryLimitBytes: memoryLimitBytes, maxStackBytes: JSThread.jsStackLimit)
        self.callLimit = callLimit
        context.registerFunction("__lhIntl") { args in
            return .string(IntlBridge.handle(args.first ?? ""))
        }
        _ = try? context.evaluate(IntlBridge.polyfill, filename: "intl.js")
        context.registerFunction("__lhNative") { [weak self] args in
            guard let self = self, args.count == 2, let native = self.native else {
                return .undefined
            }
            if case let .array(values) = QuickJSScriptContext.decode(args[1]) {
                native(args[0], values)
            }
            return .undefined
        }
    }

    public func boot(prelude: String, native names: [String], call: @escaping HTMLScriptNativeCall, arguments: [HTMLScriptValue]) throws {
        native = call
        let bootstrap = """
        (function () {
          const native = {};
          for (const name of \(QuickJSScriptContext.encode(.array(names.map { .string($0) })))) native[name] = (...args) => __lhNative(name, JSON.stringify(args));
          const hooks = (\(prelude))(native, ...\(QuickJSScriptContext.encode(.array(arguments))));
          globalThis.__lhInvoke = (name, json) => {
            try {
              const result = hooks[name](...JSON.parse(json));
              return JSON.stringify({ v: result === undefined ? null : result, u: result === undefined });
            } catch (e) {
              let message;
              try { message = hooks.describe(e); } catch (_) { message = 'Uncaught exception'; }
              return JSON.stringify({ e: String(message || 'Uncaught exception') });
            }
          };
        })();
        """
        try perform(limit: callLimit) {
            try context.evaluate(bootstrap, filename: "prelude.js")
        }
    }

    public func evaluate(_ source: String, name: String, timeLimit: TimeInterval?) throws {
        try perform(limit: timeLimit ?? callLimit) {
            try context.evaluate(source, filename: name)
        }
    }

    public func invoke(_ hook: String, _ arguments: [HTMLScriptValue]) throws -> HTMLScriptValue {
        let encoded = QuickJSScriptContext.encode(.array(arguments))
        let result = try perform(limit: callLimit) {
            try context.call("__lhInvoke", [hook, encoded])
        }
        guard let text = result.string, let data = text.data(using: .utf8), let envelope = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return .undefined
        }
        if let message = envelope["e"] as? String {
            throw QuickJSScriptContext.convert(.exception(message))
        }
        if envelope["u"] as? Bool == true {
            return .undefined
        }
        return QuickJSScriptContext.value(envelope["v"])
    }

    @discardableResult
    private func perform(limit: TimeInterval, _ body: () throws -> JSResult) throws -> JSResult {
        context.interrupted = false
        context.deadline = ProcessInfo.processInfo.systemUptime + limit
        defer {
            context.deadline = .infinity
        }
        do {
            let result = try body()
            var failure: QuickJSError?
            context.executePendingJobs { error in
                failure = failure ?? error
            }
            if context.interrupted {
                throw HTMLScriptError(.timeLimit, "time limit")
            }
            if let failure = failure {
                throw QuickJSScriptContext.convert(failure)
            }
            return result
        } catch let error as QuickJSError {
            if context.interrupted {
                throw HTMLScriptError(.timeLimit, "time limit")
            }
            throw QuickJSScriptContext.convert(error)
        }
    }

    private static func convert(_ error: QuickJSError) -> HTMLScriptError {
        let message = error.description
        if message.contains("out of memory") {
            return HTMLScriptError(.outOfMemory, message)
        }
        return HTMLScriptError(.exception, String(message.prefix(300)))
    }

    static func encode(_ value: HTMLScriptValue) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: object(value), options: [.fragmentsAllowed]) else {
            return "null"
        }
        return String(data: data, encoding: .utf8) ?? "null"
    }

    private static func object(_ value: HTMLScriptValue) -> Any {
        switch value {
        case .undefined, .null:
            return NSNull()
        case let .bool(value):
            return value
        case let .number(value):
            return value.isFinite ? value : NSNull()
        case let .string(value):
            return value
        case let .array(values):
            return values.map(object)
        }
    }

    static func decode(_ text: String) -> HTMLScriptValue {
        guard let data = text.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else {
            return .undefined
        }
        return value(object)
    }

    private static func value(_ object: Any?) -> HTMLScriptValue {
        switch object {
        case nil, is NSNull:
            return .null
        case let number as NSNumber:
            return CFGetTypeID(number) == CFBooleanGetTypeID() ? .bool(number.boolValue) : .number(number.doubleValue)
        case let string as String:
            return .string(string)
        case let array as [Any]:
            return .array(array.map { value($0) })
        default:
            return .undefined
        }
    }
}

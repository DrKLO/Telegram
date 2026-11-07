import Foundation

public indirect enum HTMLScriptValue: Equatable {
    case undefined
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([HTMLScriptValue])

    public var isTruthy: Bool {
        switch self {
        case .undefined, .null:
            return false
        case let .bool(value):
            return value
        case let .number(value):
            return value != 0 && !value.isNaN
        case let .string(value):
            return !value.isEmpty
        case .array:
            return true
        }
    }

    public var stringValue: String? {
        if case let .string(value) = self {
            return value
        }
        return nil
    }

    public var numberValue: Double? {
        switch self {
        case let .number(value):
            return value
        case let .bool(value):
            return value ? 1 : 0
        case let .string(value):
            return Double(value)
        default:
            return nil
        }
    }
}

public struct HTMLScriptError: Error, CustomStringConvertible {
    public enum Kind {
        case exception
        case timeLimit
        case outOfMemory
    }

    public let kind: Kind
    public let message: String

    public init(_ kind: Kind, _ message: String) {
        self.kind = kind
        self.message = message
    }

    public var description: String {
        return message
    }
}

public typealias HTMLScriptNativeCall = (_ name: String, _ arguments: [HTMLScriptValue]) -> Void

public protocol HTMLScriptContext: AnyObject {
    func boot(prelude: String, native: [String], call: @escaping HTMLScriptNativeCall, arguments: [HTMLScriptValue]) throws
    func evaluate(_ source: String, name: String, timeLimit: TimeInterval?) throws
    func invoke(_ hook: String, _ arguments: [HTMLScriptValue]) throws -> HTMLScriptValue
}

public protocol HTMLScriptRuntime: AnyObject {
    func makeContext(name: String, callLimit: TimeInterval) -> HTMLScriptContext
}

public protocol HTMLScriptLibrary: AnyObject {
    func install(in context: HTMLScriptContext, limit: TimeInterval) throws
}

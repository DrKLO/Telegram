import Foundation

public struct WidgetCanvasBatch {
    public static let kinds = ["c", "s", "r", "g", "gs", "pn", "pc"]

    public enum Value {
        case number(Double)
        case string(Int)
        case gradient(Double)
        case path(Double)
        case node(Double)
        case list(Range<Int>)
        case null
        case bool(Bool)
    }

    public struct Command {
        public var kind: Int
        public var name: Int
        public var ident: Double
        public var arguments: Range<Int>
    }

    public let words: [Double]
    public let strings: [String]

    public init(words: [Double], strings: [String]) {
        self.words = words
        self.strings = strings
    }

    public static func hasIdent(_ kind: Int) -> Bool {
        return kind >= 3
    }

    public static func hasName(_ kind: Int) -> Bool {
        return kind == 0 || kind == 1 || kind == 3 || kind == 6
    }

    public func forEach(_ body: (Command, [Value]) -> Bool) -> Bool {
        var i = 0
        var values: [Value] = []
        let n = words.count
        while i < n {
            guard n - i >= 4 else {
                return false
            }
            let kind = words[i], name = words[i + 1], ident = words[i + 2], argc = words[i + 3]
            guard kind >= 0, kind < 7, kind == kind.rounded(), name >= -1, name < Double(strings.count), name == name.rounded(), argc >= 0, argc <= 64, argc == argc.rounded() else {
                return false
            }
            i += 4
            let start = i
            values.removeAll(keepingCapacity: true)
            for _ in 0..<Int(argc) {
                guard i + 1 < n else {
                    return false
                }
                let tag = words[i], payload = words[i + 1]
                i += 2
                switch tag {
                case 0:
                    values.append(.number(payload))
                case 1:
                    guard payload >= 0, payload < Double(strings.count), payload == payload.rounded() else {
                        return false
                    }
                    values.append(.string(Int(payload)))
                case 2:
                    values.append(.gradient(payload))
                case 3:
                    values.append(.path(payload))
                case 4:
                    values.append(.node(payload))
                case 5:
                    guard payload >= 0, payload == payload.rounded(), Double(n - i) >= payload else {
                        return false
                    }
                    values.append(.list(i..<(i + Int(payload))))
                    i += Int(payload)
                case 6:
                    values.append(.null)
                case 7:
                    values.append(.bool(payload != 0))
                default:
                    return false
                }
            }
            if !body(Command(kind: Int(kind), name: Int(name), ident: ident, arguments: start..<i), values) {
                return false
            }
        }
        return true
    }

    public func object(_ value: Value) -> Any {
        switch value {
        case .number(let v):
            return v.isFinite ? NSNumber(value: v) : NSNull()
        case .string(let index):
            return strings[index]
        case .gradient(let id):
            return ["g": NSNumber(value: id)]
        case .path(let id):
            return ["p": NSNumber(value: id)]
        case .node(let id):
            return ["n": NSNumber(value: id)]
        case .list(let range):
            return words[range].map { $0.isFinite ? NSNumber(value: $0) as Any : NSNull() as Any }
        case .null:
            return NSNull()
        case .bool(let b):
            return NSNumber(value: b)
        }
    }

    public func commands() -> [Any] {
        var out: [Any] = []
        _ = forEach { command, values in
            var list: [Any] = [WidgetCanvasBatch.kinds[command.kind]]
            if WidgetCanvasBatch.hasIdent(command.kind) {
                list.append(command.ident.isFinite ? NSNumber(value: command.ident) : NSNull())
            }
            if WidgetCanvasBatch.hasName(command.kind), command.name >= 0 {
                list.append(strings[command.name])
            }
            list.append(contentsOf: values.map(object))
            out.append(list)
            return true
        }
        return out
    }

    public func json(number: (Double) -> String) -> String? {
        var out = "["
        var first = true
        func string(_ s: String) {
            WidgetOp.appendString(s, to: &out)
        }
        func value(_ v: Value) {
            switch v {
            case .number(let d):
                out += d.isFinite ? number(d) : "null"
            case .string(let index):
                string(strings[index])
            case .gradient(let id):
                out += "{\"g\":" + number(id) + "}"
            case .path(let id):
                out += "{\"p\":" + number(id) + "}"
            case .node(let id):
                out += "{\"n\":" + number(id) + "}"
            case .list(let range):
                out += "["
                for (k, d) in words[range].enumerated() {
                    if k > 0 {
                        out += ","
                    }
                    out += d.isFinite ? number(d) : "null"
                }
                out += "]"
            case .null:
                out += "null"
            case .bool(let b):
                out += b ? "true" : "false"
            }
        }
        let ok = forEach { command, values in
            out += first ? "[" : ",["
            first = false
            string(WidgetCanvasBatch.kinds[command.kind])
            if WidgetCanvasBatch.hasIdent(command.kind) {
                out += "," + (command.ident.isFinite ? number(command.ident) : "null")
            }
            if WidgetCanvasBatch.hasName(command.kind), command.name >= 0 {
                out += ","
                string(strings[command.name])
            }
            for v in values {
                out += ","
                value(v)
            }
            out += "]"
            return true
        }
        return ok ? out + "]" : nil
    }
}

import Foundation

public struct WidgetOp {
    public enum Kind: UInt8 {
        case style
        case attribute
        case canvas
        case other
        case element
        case text
        case data
        case insert
        case remove
        case removeAttribute
        case css
    }

    public let kind: Kind
    public let id: Int
    public let name: String
    public let value: String
    public let important: Bool
    public let fields: [String: Any]?
    public let canvas: WidgetCanvasBatch?
    public let parent: Int
    public let before: Int?
    public let attributes: [(name: String, value: String)]

    public init(style id: Int, name: String, value: String, important: Bool) {
        self.init(kind: .style, id: id, name: name, value: value, important: important)
    }

    public init(canvas id: Int, batch: WidgetCanvasBatch) {
        self.init(kind: .canvas, id: id, canvas: batch)
    }

    public init(attribute id: Int, name: String, value: String) {
        self.init(kind: .attribute, id: id, name: name, value: value)
    }

    public init(element id: Int, tag: String, svg: Bool, attributes: [(name: String, value: String)], adopt: String?) {
        self.init(kind: .element, id: id, name: tag, value: adopt ?? "", important: svg, attributes: attributes)
    }

    public init(text id: Int, data: String) {
        self.init(kind: .text, id: id, value: data)
    }

    public init(data id: Int, data: String) {
        self.init(kind: .data, id: id, value: data)
    }

    public init(insert id: Int, parent: Int, before: Int?) {
        self.init(kind: .insert, id: id, parent: parent, before: before)
    }

    public init(remove id: Int) {
        self.init(kind: .remove, id: id)
    }

    public init(removeAttribute id: Int, name: String) {
        self.init(kind: .removeAttribute, id: id, name: name)
    }

    public init(css id: Int, text: String) {
        self.init(kind: .css, id: id, value: text)
    }

    public init(_ fields: [String: Any]) {
        if let typed = WidgetOp.typed(fields) {
            self = typed
            return
        }
        self.init(kind: .other, id: (fields["id"] as? NSNumber)?.intValue ?? 0, fields: fields)
    }

    private init(kind: Kind, id: Int, name: String = "", value: String = "", important: Bool = false, fields: [String: Any]? = nil, canvas: WidgetCanvasBatch? = nil, parent: Int = 0, before: Int? = nil, attributes: [(name: String, value: String)] = []) {
        self.kind = kind
        self.id = id
        self.name = name
        self.value = value
        self.important = important
        self.fields = fields
        self.canvas = canvas
        self.parent = parent
        self.before = before
        self.attributes = attributes
    }

    private static func number(_ value: Any?) -> Int? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else {
            return nil
        }
        let double = number.doubleValue
        guard double == double.rounded(), abs(double) < 9_007_199_254_740_992 else {
            return nil
        }
        return number.intValue
    }

    private static func typed(_ fields: [String: Any]) -> WidgetOp? {
        guard let op = fields["op"] as? String else {
            return nil
        }
        switch op {
        case "style":
            if let id = fields["id"] as? NSNumber, let name = fields["name"] as? String, fields.count <= 5 {
                let value = fields["value"]
                let important = fields["important"]
                if value == nil || value is String, important == nil || important is Bool {
                    return WidgetOp(style: id.intValue, name: name, value: (value as? String) ?? "", important: (important as? Bool) ?? false)
                }
            }
        case "text", "data":
            if fields.count == 3, let id = number(fields["id"]), let data = fields["data"] as? String {
                return op == "text" ? WidgetOp(text: id, data: data) : WidgetOp(data: id, data: data)
            }
        case "rm":
            if fields.count == 2, let id = number(fields["id"]) {
                return WidgetOp(remove: id)
            }
        case "ins":
            if fields.count == 4, let id = number(fields["id"]), let parent = number(fields["parent"]) {
                if fields["before"] is NSNull {
                    return WidgetOp(insert: id, parent: parent, before: nil)
                }
                if let before = number(fields["before"]) {
                    return WidgetOp(insert: id, parent: parent, before: before)
                }
            }
        case "rattr":
            if fields.count == 3, let id = number(fields["id"]), let name = fields["name"] as? String {
                return WidgetOp(removeAttribute: id, name: name)
            }
        case "attr":
            if fields.count == 4, let id = number(fields["id"]), let name = fields["name"] as? String, let value = fields["value"] as? String {
                return WidgetOp(attribute: id, name: name, value: value)
            }
        case "css":
            if fields.count == 3, let id = number(fields["id"]), let text = fields["text"] as? String {
                return WidgetOp(css: id, text: text)
            }
        case "el":
            guard let id = number(fields["id"]), let tag = fields["tag"] as? String, let list = fields["attrs"] as? [Any] else {
                return nil
            }
            var count = 4
            var svg = false
            if let ns = fields["ns"] {
                guard (ns as? String) == "svg" else {
                    return nil
                }
                svg = true
                count += 1
            }
            var adopt: String?
            if let value = fields["adopt"] {
                guard let name = value as? String, name == "html" || name == "head" || name == "body" else {
                    return nil
                }
                adopt = name
                count += 1
            }
            guard fields.count == count else {
                return nil
            }
            var attributes: [(name: String, value: String)] = []
            attributes.reserveCapacity(list.count)
            for item in list {
                guard let pair = item as? [Any], pair.count == 2, let name = pair[0] as? String, let value = pair[1] as? String else {
                    return nil
                }
                attributes.append((name, value))
            }
            return WidgetOp(element: id, tag: tag, svg: svg, attributes: attributes, adopt: adopt)
        default:
            break
        }
        return nil
    }

    public var tag: String {
        return kind == .element ? name : ""
    }

    public var adopt: String? {
        return kind == .element && !value.isEmpty ? value : nil
    }

    public var svg: Bool {
        return kind == .element && important
    }

    public var op: String? {
        switch kind {
        case .style:
            return "style"
        case .attribute:
            return "attr"
        case .canvas:
            return "canvas"
        case .element:
            return "el"
        case .text:
            return "text"
        case .data:
            return "data"
        case .insert:
            return "ins"
        case .remove:
            return "rm"
        case .removeAttribute:
            return "rattr"
        case .css:
            return "css"
        case .other:
            return fields?["op"] as? String
        }
    }

    public subscript(key: String) -> Any? {
        if kind == .other {
            return fields?[key]
        }
        if key == "op" {
            return op
        }
        if key == "id" {
            return NSNumber(value: id)
        }
        switch (kind, key) {
        case (.style, "name"), (.attribute, "name"), (.removeAttribute, "name"):
            return name
        case (.style, "value"), (.attribute, "value"):
            return value
        case (.style, "important"):
            return important
        case (.canvas, "cmds"):
            return canvas?.commands()
        case (.element, "tag"):
            return name
        case (.element, "ns"):
            return important ? "svg" : nil
        case (.element, "adopt"):
            return adopt
        case (.element, "attrs"):
            return attributes.map { [$0.name, $0.value] as [Any] }
        case (.text, "data"), (.data, "data"):
            return value
        case (.css, "text"):
            return value
        case (.insert, "parent"):
            return NSNumber(value: parent)
        case (.insert, "before"):
            return before.map { NSNumber(value: $0) } ?? NSNull()
        default:
            return nil
        }
    }

    public static func json(_ ops: [WidgetOp]) -> String {
        var out = "["
        out.reserveCapacity(ops.count * 64 + 2)
        for (index, op) in ops.enumerated() {
            if index > 0 {
                out += ","
            }
            switch op.kind {
            case .style:
                out += "{\"op\":\"style\",\"id\":\(op.id),\"name\":"
                appendString(op.name, to: &out)
                out += ",\"value\":"
                appendString(op.value, to: &out)
                out += op.important ? ",\"important\":true}" : ",\"important\":false}"
            case .attribute:
                out += "{\"op\":\"attr\",\"id\":\(op.id),\"name\":"
                appendString(op.name, to: &out)
                out += ",\"value\":"
                appendString(op.value, to: &out)
                out += "}"
            case .element:
                out += "{\"op\":\"el\",\"id\":\(op.id),\"tag\":"
                appendString(op.name, to: &out)
                if op.important {
                    out += ",\"ns\":\"svg\""
                }
                out += ",\"attrs\":["
                for (index, pair) in op.attributes.enumerated() {
                    out += index > 0 ? ",[" : "["
                    appendString(pair.name, to: &out)
                    out += ","
                    appendString(pair.value, to: &out)
                    out += "]"
                }
                out += "]"
                if !op.value.isEmpty {
                    out += ",\"adopt\":"
                    appendString(op.value, to: &out)
                }
                out += "}"
            case .text, .data:
                out += op.kind == .text ? "{\"op\":\"text\",\"id\":\(op.id),\"data\":" : "{\"op\":\"data\",\"id\":\(op.id),\"data\":"
                appendString(op.value, to: &out)
                out += "}"
            case .insert:
                out += "{\"op\":\"ins\",\"parent\":\(op.parent),\"id\":\(op.id),\"before\":"
                out += op.before.map { String($0) } ?? "null"
                out += "}"
            case .remove:
                out += "{\"op\":\"rm\",\"id\":\(op.id)}"
            case .removeAttribute:
                out += "{\"op\":\"rattr\",\"id\":\(op.id),\"name\":"
                appendString(op.name, to: &out)
                out += "}"
            case .css:
                out += "{\"op\":\"css\",\"id\":\(op.id),\"text\":"
                appendString(op.value, to: &out)
                out += "}"
            case .canvas:
                out += "{\"op\":\"canvas\",\"id\":\(op.id),\"cmds\":"
                out += op.canvas?.json(number: { $0.isFinite ? "\($0)" : "null" }) ?? "[]"
                out += "}"
            case .other:
                if let fields = op.fields, JSONSerialization.isValidJSONObject(fields), let data = try? JSONSerialization.data(withJSONObject: fields) {
                    out += String(decoding: data, as: UTF8.self)
                } else {
                    out += "null"
                }
            }
        }
        return out + "]"
    }

    static func appendString(_ s: String, to out: inout String) {
        out += "\""
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"":
                out += "\\\""
            case "\\":
                out += "\\\\"
            case "\n":
                out += "\\n"
            case "\r":
                out += "\\r"
            case "\t":
                out += "\\t"
            default:
                if scalar.value < 0x20 || scalar.value == 0x2028 || scalar.value == 0x2029 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        out += "\""
    }
}

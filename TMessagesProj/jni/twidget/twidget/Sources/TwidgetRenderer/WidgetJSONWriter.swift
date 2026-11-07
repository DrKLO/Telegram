import Foundation
import CoreGraphics

struct WidgetJSONWriter {
    private(set) var bytes: [UInt8] = []

    init(capacity: Int = 256) {
        bytes.reserveCapacity(capacity)
    }

    var string: String {
        return String(decoding: bytes, as: UTF8.self)
    }

    static func encode(_ object: Any) -> String? {
        var writer = WidgetJSONWriter()
        guard writer.value(object, depth: 0) else {
            return nil
        }
        return writer.string
    }

    static func frame(time: Double) -> String? {
        var writer = WidgetJSONWriter(capacity: 48)
        writer.raw("{\"type\":\"frame\",\"time\":")
        guard writer.number(time) else {
            return nil
        }
        writer.bytes.append(UInt8(ascii: "}"))
        return writer.string
    }

    mutating func raw(_ text: StaticString) {
        text.withUTF8Buffer { bytes.append(contentsOf: $0) }
    }

    mutating func string(_ text: String) {
        var text = text
        text.withUTF8 { utf8 in
            bytes.append(UInt8(ascii: "\""))
            var start = 0
            var index = 0
            let count = utf8.count
            while index < count {
                let byte = utf8[index]
                if byte >= 0x20 && byte != 0x22 && byte != 0x5C && byte != 0x2F {
                    index += 1
                    continue
                }
                if start < index {
                    bytes.append(contentsOf: UnsafeBufferPointer(rebasing: utf8[start..<index]))
                }
                bytes.append(UInt8(ascii: "\\"))
                switch byte {
                case 0x22: bytes.append(0x22)
                case 0x5C: bytes.append(0x5C)
                case 0x2F: bytes.append(0x2F)
                case 0x08: bytes.append(UInt8(ascii: "b"))
                case 0x0C: bytes.append(UInt8(ascii: "f"))
                case 0x0A: bytes.append(UInt8(ascii: "n"))
                case 0x0D: bytes.append(UInt8(ascii: "r"))
                case 0x09: bytes.append(UInt8(ascii: "t"))
                default:
                    let hex: [UInt8] = Array("0123456789abcdef".utf8)
                    bytes.append(contentsOf: [UInt8(ascii: "u"), 0x30, 0x30, hex[Int(byte >> 4)], hex[Int(byte & 0xF)]])
                }
                index += 1
                start = index
            }
            if start < count {
                bytes.append(contentsOf: UnsafeBufferPointer(rebasing: utf8[start..<count]))
            }
            bytes.append(UInt8(ascii: "\""))
        }
    }

    mutating func number(_ value: Double) -> Bool {
        guard value.isFinite else {
            return false
        }
        if value == value.rounded(), abs(value) < 1e15 {
            if value == 0 && value.sign == .minus {
                raw("-0")
            } else {
                integer(Int64(value))
            }
        } else {
            bytes.append(contentsOf: value.description.utf8)
        }
        return true
    }

    mutating func integer(_ value: Int64) {
        if value >= 0 && value < 10 {
            bytes.append(UInt8(ascii: "0") + UInt8(value))
            return
        }
        bytes.append(contentsOf: String(value).utf8)
    }

    mutating func bool(_ value: Bool) {
        if value {
            raw("true")
        } else {
            raw("false")
        }
    }

    mutating func key(_ key: String, first: inout Bool) {
        if !first {
            bytes.append(UInt8(ascii: ","))
        }
        first = false
        string(key)
        bytes.append(UInt8(ascii: ":"))
    }

    private mutating func nsNumber(_ number: NSNumber) -> Bool {
        if CFGetTypeID(number) == CFBooleanGetTypeID() {
            bool(number.boolValue)
            return true
        }
        switch UInt8(bitPattern: number.objCType.pointee) {
        case UInt8(ascii: "f"), UInt8(ascii: "d"):
            return self.number(number.doubleValue)
        case UInt8(ascii: "Q"), UInt8(ascii: "L"), UInt8(ascii: "I"), UInt8(ascii: "S"), UInt8(ascii: "C"):
            bytes.append(contentsOf: String(number.uint64Value).utf8)
            return true
        case UInt8(ascii: "q"), UInt8(ascii: "l"), UInt8(ascii: "i"), UInt8(ascii: "s"), UInt8(ascii: "c"):
            integer(number.int64Value)
            return true
        default:
            return false
        }
    }

    mutating func value(_ value: Any, depth: Int) -> Bool {
        guard depth < 64 else {
            return false
        }
        if type(of: value) is NSNumber.Type, let number = value as? NSNumber {
            return nsNumber(number)
        }
        switch value {
        case let text as String:
            string(text)
        case let flag as Bool:
            bool(flag)
        case let number as Int:
            integer(Int64(number))
        case let number as Double:
            return self.number(number)
        case let number as CGFloat:
            return self.number(Double(number))
        case let number as Float:
            return self.number(Double(number))
        case let number as Int64:
            integer(number)
        case let number as Int32:
            integer(Int64(number))
        case is NSNull:
            raw("null")
        case let map as [String: Double] where type(of: value) == [String: Double].self:
            bytes.append(UInt8(ascii: "{"))
            var first = true
            for (name, item) in map {
                key(name, first: &first)
                guard number(item) else {
                    return false
                }
            }
            bytes.append(UInt8(ascii: "}"))
        case let map as [String: String] where type(of: value) == [String: String].self:
            bytes.append(UInt8(ascii: "{"))
            var first = true
            for (name, item) in map {
                key(name, first: &first)
                string(item)
            }
            bytes.append(UInt8(ascii: "}"))
        case let map as [String: Any]:
            bytes.append(UInt8(ascii: "{"))
            var first = true
            for (name, item) in map {
                key(name, first: &first)
                guard self.value(item, depth: depth + 1) else {
                    return false
                }
            }
            bytes.append(UInt8(ascii: "}"))
        case let list as [Any]:
            bytes.append(UInt8(ascii: "["))
            var first = true
            for item in list {
                if !first {
                    bytes.append(UInt8(ascii: ","))
                }
                first = false
                guard self.value(item, depth: depth + 1) else {
                    return false
                }
            }
            bytes.append(UInt8(ascii: "]"))
        default:
            return false
        }
        return true
    }
}

import Foundation
import WidgetSandboxProtocol

enum CanvasArg: Equatable {
    case number(Double)
    case string(String)
    case gradient(Int)
    case path(Int)
    case node(Int)
    case pixels(Data, Int, Int)
    case list([Double])
    case other
}

enum CanvasOpKind: UInt8 {
    case call
    case set
    case resize
    case gradient
    case gradientStop
    case pathNew
    case pathCall
}

enum CanvasName: UInt8 {
    case none
    case save, restore, scale, rotate, translate, transform, setTransform, resetTransform
    case clearRect, fillRect, strokeRect, beginPath, closePath, moveTo, lineTo, bezierCurveTo, quadraticCurveTo
    case arc, arcTo, ellipse, rect, roundRect, fill, stroke, clip, fillText, strokeText, setLineDash
    case drawImage, putImageData, addPath
    case fillStyle, strokeStyle, lineWidth, lineCap, lineJoin, miterLimit, lineDashOffset, font, textAlign, textBaseline
    case globalAlpha, globalCompositeOperation, shadowBlur, shadowColor, shadowOffsetX, shadowOffsetY, imageSmoothingEnabled

    static let byText: [String: CanvasName] = {
        var map: [String: CanvasName] = [:]
        let all: [(String, CanvasName)] = [
            ("save", .save), ("restore", .restore), ("scale", .scale), ("rotate", .rotate), ("translate", .translate),
            ("transform", .transform), ("setTransform", .setTransform), ("resetTransform", .resetTransform),
            ("clearRect", .clearRect), ("fillRect", .fillRect), ("strokeRect", .strokeRect), ("beginPath", .beginPath),
            ("closePath", .closePath), ("moveTo", .moveTo), ("lineTo", .lineTo), ("bezierCurveTo", .bezierCurveTo),
            ("quadraticCurveTo", .quadraticCurveTo), ("arc", .arc), ("arcTo", .arcTo), ("ellipse", .ellipse), ("rect", .rect),
            ("roundRect", .roundRect), ("fill", .fill), ("stroke", .stroke), ("clip", .clip), ("fillText", .fillText),
            ("strokeText", .strokeText), ("setLineDash", .setLineDash), ("drawImage", .drawImage), ("putImageData", .putImageData),
            ("addPath", .addPath), ("fillStyle", .fillStyle), ("strokeStyle", .strokeStyle), ("lineWidth", .lineWidth),
            ("lineCap", .lineCap), ("lineJoin", .lineJoin), ("miterLimit", .miterLimit), ("lineDashOffset", .lineDashOffset),
            ("font", .font), ("textAlign", .textAlign), ("textBaseline", .textBaseline), ("globalAlpha", .globalAlpha),
            ("globalCompositeOperation", .globalCompositeOperation), ("shadowBlur", .shadowBlur), ("shadowColor", .shadowColor),
            ("shadowOffsetX", .shadowOffsetX), ("shadowOffsetY", .shadowOffsetY), ("imageSmoothingEnabled", .imageSmoothingEnabled),
        ]
        for (text, name) in all {
            map[text] = name
        }
        return map
    }()

    static func lookup(_ text: String) -> CanvasName {
        return byText[text] ?? .none
    }

    private static let byLength: [[([UInt8], CanvasName)]] = {
        var table = [[([UInt8], CanvasName)]](repeating: [], count: 33)
        for (text, name) in byText where text.utf8.count < 33 {
            table[text.utf8.count].append((Array(text.utf8), name))
        }
        return table
    }()

    static func lookup(_ bytes: UnsafeBufferPointer<UInt8>) -> CanvasName {
        guard bytes.count < byLength.count, let base = bytes.baseAddress else {
            return .none
        }
        for (candidate, name) in byLength[bytes.count] where candidate[0] == base[0] && candidate.withUnsafeBufferPointer({ memcmp($0.baseAddress!, base, bytes.count) == 0 }) {
            return name
        }
        return .none
    }
}

struct CanvasOp {
    var kind: CanvasOpKind
    var name: CanvasName
    var ident: Double
    var start: Int32
    var count: Int32
}

struct CanvasStream {
    static let maxOps = 500_000
    static let maxArgs = 1_000_000

    var ops: [CanvasOp] = []
    var args: [CanvasArg] = []

    var isEmpty: Bool {
        return ops.isEmpty
    }

    func arguments(_ op: CanvasOp) -> ArraySlice<CanvasArg> {
        let start = Int(op.start)
        return args[start..<(start + Int(op.count))]
    }

    static func kind(_ text: String) -> CanvasOpKind? {
        switch text {
        case "c": return .call
        case "s": return .set
        case "r": return .resize
        case "g": return .gradient
        case "gs": return .gradientStop
        case "pn": return .pathNew
        case "pc": return .pathCall
        default: return nil
        }
    }

    static func number(_ value: Any) -> Double {
        return ((value as AnyObject) as? NSNumber)?.doubleValue ?? 0
    }

    static func resolve(_ value: Any) -> CanvasArg {
        let object = value as AnyObject
        if let n = object as? NSNumber {
            return .number(n.doubleValue)
        }
        if let s = object as? NSString {
            return .string(s as String)
        }
        if let a = object as? NSArray {
            return .list(a.map { CanvasStream.number($0) })
        }
        guard let d = value as? [String: Any] else {
            return .other
        }
        if let g = d["g"] {
            return .gradient(WidgetCanvas.int(g))
        }
        if let p = d["p"] {
            return .path(WidgetCanvas.int(p))
        }
        if let n = d["n"] {
            return .node(WidgetCanvas.int(n))
        }
        if let b64 = d["b64"] as? String, let data = Data(base64Encoded: b64) {
            return .pixels(data, WidgetCanvas.int(d["w"] ?? 0), WidgetCanvas.int(d["h"] ?? 0))
        }
        return .other
    }

    init() {}

    init(commands: [Any]) {
        ops.reserveCapacity(min(commands.count, CanvasStream.maxOps))
        for element in commands {
            guard ops.count < CanvasStream.maxOps, args.count < CanvasStream.maxArgs else {
                break
            }
            guard let list = (element as AnyObject) as? NSArray, list.count >= 2, let text = (list[0] as AnyObject) as? NSString, let kind = CanvasStream.kind(text as String) else {
                continue
            }
            let head = list[1] as AnyObject
            var name = CanvasName.none
            var ident = 0.0
            var restStart = 2
            switch kind {
            case .call, .set:
                guard let method = head as? NSString else {
                    continue
                }
                name = CanvasName.lookup(method as String)
            case .pathCall:
                ident = CanvasStream.number(head)
                guard list.count >= 3, let method = (list[2] as AnyObject) as? NSString else {
                    continue
                }
                name = CanvasName.lookup(method as String)
                restStart = 3
            default:
                ident = CanvasStream.number(head)
            }
            let start = args.count
            if list.count > restStart {
                for index in restStart..<list.count {
                    args.append(CanvasStream.resolve(list[index]))
                }
            }
            ops.append(CanvasOp(kind: kind, name: name, ident: ident, start: Int32(start), count: Int32(args.count - start)))
        }
    }

    init(batch: WidgetCanvasBatch) {
        var elements: [CanvasArg] = []
        func arg(_ value: WidgetCanvasBatch.Value) -> CanvasArg {
            switch value {
            case .number(let d):
                return d.isFinite ? .number(d) : .other
            case .string(let index):
                return .string(batch.strings[index])
            case .gradient(let id):
                return .gradient(WidgetCanvas.int(NSNumber(value: id)))
            case .path(let id):
                return .path(WidgetCanvas.int(NSNumber(value: id)))
            case .node(let id):
                return .node(WidgetCanvas.int(NSNumber(value: id)))
            case .list(let range):
                return .list(batch.words[range].map { $0.isFinite ? $0 : 0 })
            case .null:
                return .other
            case .bool(let b):
                return .number(b ? 1 : 0)
            }
        }
        _ = batch.forEach { command, values in
            guard ops.count < CanvasStream.maxOps, args.count < CanvasStream.maxArgs, let kind = CanvasOpKind(rawValue: UInt8(command.kind)) else {
                return false
            }
            elements.removeAll(keepingCapacity: true)
            if WidgetCanvasBatch.hasIdent(command.kind) {
                elements.append(command.ident.isFinite ? .number(command.ident) : .other)
            }
            if WidgetCanvasBatch.hasName(command.kind), command.name >= 0 {
                elements.append(.string(batch.strings[command.name]))
            }
            for value in values {
                elements.append(arg(value))
            }
            guard !elements.isEmpty else {
                return true
            }
            var name = CanvasName.none
            var ident = 0.0
            var rest = 1
            func number(_ a: CanvasArg) -> Double {
                if case .number(let d) = a {
                    return d
                }
                return 0
            }
            switch kind {
            case .call, .set:
                guard case .string(let method) = elements[0] else {
                    return true
                }
                name = CanvasName.lookup(method)
            case .pathCall:
                ident = number(elements[0])
                guard elements.count >= 2, case .string(let method) = elements[1] else {
                    return true
                }
                name = CanvasName.lookup(method)
                rest = 2
            default:
                ident = number(elements[0])
            }
            let start = args.count
            if elements.count > rest {
                args.append(contentsOf: elements[rest...])
            }
            ops.append(CanvasOp(kind: kind, name: name, ident: ident, start: Int32(start), count: Int32(args.count - start)))
            return true
        }
    }

    static func canvasOps(json: String) -> [(id: Int, stream: CanvasStream)]? {
        var text = json
        return text.withUTF8 { bytes -> [(id: Int, stream: CanvasStream)]? in
            var scanner = CanvasJSONScanner(bytes)
            return scanner.message()
        }
    }
}

struct CanvasJSONScanner {
    private let p: UnsafeBufferPointer<UInt8>
    private var i = 0
    private static let pow10: [Double] = (0...308).map { Double("1e\($0)")! }

    init(_ bytes: UnsafeBufferPointer<UInt8>) {
        p = bytes
    }

    private mutating func ws() {
        while i < p.count {
            let c = p[i]
            if c == 0x20 || c == 0x0A || c == 0x0D || c == 0x09 {
                i += 1
            } else {
                return
            }
        }
    }

    private mutating func eat(_ c: UInt8) -> Bool {
        ws()
        if i < p.count && p[i] == c {
            i += 1
            return true
        }
        return false
    }

    private mutating func peek() -> UInt8? {
        ws()
        return i < p.count ? p[i] : nil
    }

    private mutating func rawString() -> UnsafeBufferPointer<UInt8>? {
        guard eat(0x22) else {
            return nil
        }
        let start = i
        while i < p.count {
            let c = p[i]
            if c == 0x22 {
                let slice = UnsafeBufferPointer(rebasing: p[start..<i])
                i += 1
                return slice
            }
            if c == 0x5C || c < 0x20 {
                return nil
            }
            i += 1
        }
        return nil
    }

    private mutating func string() -> String? {
        guard let raw = rawString() else {
            return nil
        }
        return String(decoding: raw, as: UTF8.self)
    }

    private static func equals(_ raw: UnsafeBufferPointer<UInt8>, _ text: StaticString) -> Bool {
        guard raw.count == text.utf8CodeUnitCount else {
            return false
        }
        let other = text.utf8Start
        for k in 0..<raw.count where raw[k] != other[k] {
            return false
        }
        return true
    }

    private mutating func number() -> Double? {
        ws()
        var negative = false
        if i < p.count && p[i] == 0x2D {
            negative = true
            i += 1
        }
        var mantissa: UInt64 = 0
        var digits = 0
        var dropped = 0
        var scale = 0
        var any = false
        while i < p.count, p[i] >= 0x30 && p[i] <= 0x39 {
            any = true
            if digits < 19 {
                mantissa = mantissa * 10 + UInt64(p[i] - 0x30)
                if mantissa != 0 {
                    digits += 1
                }
            } else {
                dropped += 1
            }
            i += 1
        }
        guard any else {
            return nil
        }
        if i < p.count && p[i] == 0x2E {
            i += 1
            var fraction = false
            while i < p.count, p[i] >= 0x30 && p[i] <= 0x39 {
                fraction = true
                if digits < 19 {
                    mantissa = mantissa * 10 + UInt64(p[i] - 0x30)
                    if mantissa != 0 {
                        digits += 1
                    }
                    scale -= 1
                }
                i += 1
            }
            guard fraction else {
                return nil
            }
        }
        if i < p.count && (p[i] == 0x65 || p[i] == 0x45) {
            i += 1
            var exponentNegative = false
            if i < p.count && (p[i] == 0x2B || p[i] == 0x2D) {
                exponentNegative = p[i] == 0x2D
                i += 1
            }
            var exponent = 0
            var any = false
            while i < p.count, p[i] >= 0x30 && p[i] <= 0x39 {
                any = true
                if exponent < 100_000 {
                    exponent = exponent * 10 + Int(p[i] - 0x30)
                }
                i += 1
            }
            guard any else {
                return nil
            }
            scale += exponentNegative ? -exponent : exponent
        }
        scale += dropped
        var value = Double(mantissa)
        if mantissa != 0 {
            if scale > 308 {
                value = .infinity
            } else if scale < -360 {
                value = 0
            } else if scale > 0 {
                value *= CanvasJSONScanner.pow10[scale]
            } else if scale < -308 {
                value /= CanvasJSONScanner.pow10[308]
                value /= CanvasJSONScanner.pow10[-scale - 308]
            } else if scale < 0 {
                value /= CanvasJSONScanner.pow10[-scale]
            }
        }
        return negative ? -value : value
    }

    private mutating func literal(_ text: StaticString) -> Bool {
        ws()
        let count = text.utf8CodeUnitCount
        guard i + count <= p.count else {
            return false
        }
        let other = text.utf8Start
        for k in 0..<count where p[i + k] != other[k] {
            return false
        }
        i += count
        return true
    }

    private mutating func value() -> CanvasArg? {
        guard let c = peek() else {
            return nil
        }
        switch c {
        case 0x22:
            return string().map { .string($0) }
        case 0x74:
            return literal("true") ? .number(1) : nil
        case 0x66:
            return literal("false") ? .number(0) : nil
        case 0x6E:
            return literal("null") ? .other : nil
        case 0x5B:
            i += 1
            var list: [Double] = []
            if eat(0x5D) {
                return .list(list)
            }
            repeat {
                guard list.count < 4096, let v = number() else {
                    return nil
                }
                list.append(v)
            } while eat(0x2C)
            return eat(0x5D) ? .list(list) : nil
        case 0x7B:
            i += 1
            guard let key = rawString(), eat(0x3A), let v = number(), eat(0x7D) else {
                return nil
            }
            let ref = WidgetCanvas.int(v)
            if CanvasJSONScanner.equals(key, "g") {
                return .gradient(ref)
            }
            if CanvasJSONScanner.equals(key, "p") {
                return .path(ref)
            }
            if CanvasJSONScanner.equals(key, "n") {
                return .node(ref)
            }
            return .other
        default:
            return number().map { .number($0) }
        }
    }

    private mutating func command(into stream: inout CanvasStream) -> Bool {
        guard eat(0x5B), let kindText = rawString() else {
            return false
        }
        let kind: CanvasOpKind?
        switch kindText.count {
        case 1:
            switch kindText[0] {
            case 0x63: kind = .call
            case 0x73: kind = .set
            case 0x72: kind = .resize
            case 0x67: kind = .gradient
            default: kind = nil
            }
        case 2:
            if kindText[0] == 0x67 && kindText[1] == 0x73 {
                kind = .gradientStop
            } else if kindText[0] == 0x70 && kindText[1] == 0x6E {
                kind = .pathNew
            } else if kindText[0] == 0x70 && kindText[1] == 0x63 {
                kind = .pathCall
            } else {
                kind = nil
            }
        default:
            kind = nil
        }
        let start = stream.args.count
        var values = 0
        var ident = 0.0
        var name = CanvasName.none
        var named = false
        let wantsName = kind == .call || kind == .set
        while eat(0x2C) {
            values += 1
            if (values == 1 && wantsName) || (values == 2 && kind == .pathCall) {
                if peek() == 0x22 {
                    guard let raw = rawString() else {
                        return false
                    }
                    name = CanvasName.lookup(raw)
                    named = true
                } else if value() == nil {
                    return false
                }
                continue
            }
            if values == 1 {
                guard let v = value() else {
                    return false
                }
                if case let .number(number) = v {
                    ident = number
                }
                continue
            }
            guard let v = value() else {
                return false
            }
            guard stream.args.count < CanvasStream.maxArgs else {
                return false
            }
            stream.args.append(v)
        }
        guard eat(0x5D) else {
            return false
        }
        guard let kind = kind, values >= 1, stream.ops.count < CanvasStream.maxOps, (kind != .call && kind != .set && kind != .pathCall) || named else {
            stream.args.removeSubrange(start...)
            return true
        }
        stream.ops.append(CanvasOp(kind: kind, name: name, ident: ident, start: Int32(start), count: Int32(stream.args.count - start)))
        return true
    }

    private mutating func canvasOp() -> (id: Int, stream: CanvasStream)? {
        guard eat(0x7B) else {
            return nil
        }
        var id: Int?
        var stream: CanvasStream?
        var isCanvas = false
        if eat(0x7D) {
            return nil
        }
        repeat {
            guard let key = rawString(), eat(0x3A) else {
                return nil
            }
            if CanvasJSONScanner.equals(key, "op") {
                guard let kind = rawString(), CanvasJSONScanner.equals(kind, "canvas") else {
                    return nil
                }
                isCanvas = true
            } else if CanvasJSONScanner.equals(key, "id") {
                guard let v = number() else {
                    return nil
                }
                id = WidgetCanvas.int(v)
            } else if CanvasJSONScanner.equals(key, "cmds") {
                guard eat(0x5B) else {
                    return nil
                }
                var made = CanvasStream()
                if !eat(0x5D) {
                    repeat {
                        guard command(into: &made) else {
                            return nil
                        }
                    } while eat(0x2C)
                    guard eat(0x5D) else {
                        return nil
                    }
                }
                stream = made
            } else {
                return nil
            }
        } while eat(0x2C)
        guard eat(0x7D), isCanvas, let found = id else {
            return nil
        }
        return (found, stream ?? CanvasStream())
    }

    mutating func message() -> [(id: Int, stream: CanvasStream)]? {
        guard eat(0x7B) else {
            return nil
        }
        var typeOK = false
        var result: [(id: Int, stream: CanvasStream)]?
        repeat {
            guard let key = rawString(), eat(0x3A) else {
                return nil
            }
            if CanvasJSONScanner.equals(key, "type") {
                guard let type = rawString(), CanvasJSONScanner.equals(type, "ops") else {
                    return nil
                }
                typeOK = true
            } else if CanvasJSONScanner.equals(key, "ops") {
                guard eat(0x5B) else {
                    return nil
                }
                var list: [(id: Int, stream: CanvasStream)] = []
                if !eat(0x5D) {
                    repeat {
                        guard list.count < 4096, let op = canvasOp() else {
                            return nil
                        }
                        list.append(op)
                    } while eat(0x2C)
                    guard eat(0x5D) else {
                        return nil
                    }
                }
                result = list
            } else {
                return nil
            }
        } while eat(0x2C)
        guard eat(0x7D), typeOK, let ops = result, !ops.isEmpty else {
            return nil
        }
        ws()
        return i == p.count ? ops : nil
    }
}

import Foundation
import WidgetSandboxProtocol

enum CanvasWords {
    static let maxWords = 2_000_000
    static let maxStringBytes = 4 << 20
    static let maxStrings = 65_536
    static let maxPayloads = 64
    static let maxListLength = 4096

    static func batch(words: UnsafeBufferPointer<Double>, strings joined: String) -> WidgetCanvasBatch? {
        guard words.count >= 1, words.count <= maxWords, joined.utf8.count <= maxStringBytes else { return nil }
        let m = words[words.count - 1]
        guard m >= 0, m <= Double(min(maxStrings, words.count - 1)), m == m.rounded() else { return nil }
        let count = Int(m)
        let commandWords = words.count - 1 - count
        var strings: [String] = []
        strings.reserveCapacity(count)
        var joined = joined
        let ascii: Bool? = joined.withUTF8 { b in
            var total = 0.0
            for k in 0..<count { total += words[commandWords + k] }
            guard total == Double(b.count), !b.contains(where: { $0 >= 0x80 }) else { return nil }
            var at = 0
            for k in 0..<count {
                let length = words[commandWords + k]
                guard length >= 0, length == length.rounded(), Double(b.count - at) >= length else { return false }
                let end = at + Int(length)
                strings.append(String(decoding: UnsafeBufferPointer(rebasing: b[at..<end]), as: UTF8.self))
                at = end
            }
            return at == b.count
        }
        if ascii == false { return nil }
        if ascii == true { return WidgetCanvasBatch(words: Array(words[0..<commandWords]), strings: strings) }
        let units = Array(joined.utf16)
        var at = 0
        for k in 0..<count {
            let length = words[commandWords + k]
            guard length >= 0, length == length.rounded(), Double(units.count - at) >= length else { return nil }
            let end = at + Int(length)
            strings.append(String(decoding: units[at..<end], as: UTF16.self))
            at = end
        }
        guard at == units.count else { return nil }
        return WidgetCanvasBatch(words: Array(words[0..<commandWords]), strings: strings)
    }

    private static func plain(_ s: String) -> Bool {
        var s = s
        return s.withUTF8 { b in
            if b.count > CanvasBatchFastPath.maxStringBytes { return false }
            for c in b where c < 0x20 || c == 0x22 || c == 0x5c { return false }
            return true
        }
    }

    private static func mentionsIcon(_ s: String) -> Bool {
        var s = s
        return s.withUTF8 { b in
            guard b.count >= 5 else { return false }
            for i in 4..<b.count where b[i] == 0x3a && b[i - 4] | 0x20 == 0x69 && b[i - 3] | 0x20 == 0x63 && b[i - 2] | 0x20 == 0x6f && b[i - 1] | 0x20 == 0x6e { return true }
            return false
        }
    }

    private static func reference(_ id: Double) -> Bool {
        id.isFinite && id == id.rounded() && abs(id) < 1e15
    }

    static func envelope(_ batch: WidgetCanvasBatch) -> Bool {
        var plainStrings = [Bool](repeating: false, count: batch.strings.count)
        for (i, s) in batch.strings.enumerated() {
            plainStrings[i] = plain(s)
            if mentionsIcon(s) { return false }
        }
        var commands = 0
        func scalar(_ v: WidgetCanvasBatch.Value) -> Bool {
            switch v {
            case .number, .null, .bool: return true
            case .string(let i): return plainStrings[i]
            default: return false
            }
        }
        func argument(_ v: WidgetCanvasBatch.Value, lists: Bool) -> Bool {
            switch v {
            case .gradient(let id), .path(let id): return reference(id)
            case .node: return false
            case .list(let range): return lists && range.count <= maxListLength && !batch.words[range].contains { !$0.isFinite }
            default: return scalar(v)
            }
        }
        func name(_ index: Int, in set: CanvasBatchFastPath.WordSet) -> Bool {
            index >= 0 && plainStrings[index] && set.contains(batch.strings[index])
        }
        guard !batch.words.isEmpty else { return false }
        return batch.forEach { command, values in
            commands += 1
            if commands > CanvasBatchFastPath.maxCmdsPerOp { return false }
            switch command.kind {
            case 0:
                return name(command.name, in: CanvasBatchFastPath.callMethods) && values.count <= 14 && values.allSatisfy { argument($0, lists: true) }
            case 1:
                return name(command.name, in: CanvasBatchFastPath.setProperties) && values.count == 1 && argument(values[0], lists: false)
            case 2:
                guard command.name == -1, values.count == 2, case .number(let w) = values[0], case .number(let h) = values[1] else { return false }
                return w.isFinite && h.isFinite && w >= 0 && w <= 16384 && h >= 0 && h <= 16384
            case 3:
                return command.ident.isFinite && name(command.name, in: CanvasBatchFastPath.gradientTypes) && values.count <= 13 && values.allSatisfy { argument($0, lists: true) }
            case 4:
                guard command.ident.isFinite, command.name == -1, values.count == 2, case .number(let offset) = values[0], case .string(let color) = values[1] else { return false }
                return offset.isFinite && plainStrings[color] && batch.strings[color].utf8.count < 256
            case 5:
                guard command.ident.isFinite, command.name == -1, values.count == 1 else { return false }
                if case .null = values[0] { return true }
                if case .string(let d) = values[0] { return plainStrings[d] }
                return false
            case 6:
                return command.ident.isFinite && name(command.name, in: CanvasBatchFastPath.pathMethods) && values.count <= 13 && values.allSatisfy { argument($0, lists: true) }
            default:
                return false
            }
        }
    }

    static func textCharges(_ batch: WidgetCanvasBatch) -> [(range: Range<Int>, work: Int)] {
        guard batch.strings.contains("fillText") || batch.strings.contains("strokeText") else { return [] }
        guard batch.strings.contains(where: NativeValidator.chargesChurn) else { return [] }
        var costs: [Int: Int] = [:]
        var charges: [(range: Range<Int>, work: Int)] = []
        _ = batch.forEach { command, values in
            guard command.kind == 0, command.name >= 0,
                  batch.strings[command.name] == "fillText" || batch.strings[command.name] == "strokeText",
                  let first = values.first, case .string(let index) = first else { return true }
            let work: Int
            if let cached = costs[index] {
                work = cached
            } else {
                let text = batch.strings[index]
                work = NativeValidator.chargesChurn(text) ? NativeValidator.textWork(text) : 0
                costs[index] = work
            }
            if work > 0 { charges.append(((command.arguments.lowerBound - 4)..<command.arguments.upperBound, work)) }
            return true
        }
        return charges
    }

    /// The commands in a payload (the validator's per-send cap, the engine's main-thread cost), nil when it is malformed
    /// (`WidgetCanvasBatch.forEach`'s rules, without building the values).
    static func commandCount(_ batch: WidgetCanvasBatch) -> Int? {
        let w = batch.words
        let strings = Double(batch.strings.count)
        let n = w.count
        var i = 0
        var count = 0
        while i < n {
            guard n - i >= 4 else { return nil }
            let kind = w[i], name = w[i + 1], argc = w[i + 3]
            guard kind >= 0, kind < 7, kind == kind.rounded(), name >= -1, name < strings, name == name.rounded(), argc >= 0, argc <= 64, argc == argc.rounded() else { return nil }
            i += 4
            for _ in 0..<Int(argc) {
                guard i + 1 < n else { return nil }
                let tag = w[i], payload = w[i + 1]
                i += 2
                switch tag {
                case 0, 2, 3, 4, 6, 7:
                    break
                case 1:
                    guard payload >= 0, payload < strings, payload == payload.rounded() else { return nil }
                case 5:
                    guard payload >= 0, payload == payload.rounded(), Double(n - i) >= payload else { return nil }
                    i += Int(payload)
                default:
                    return nil
                }
            }
            count += 1
        }
        return count
    }

    /// The `r` commands of a payload `commandCount` takes that validator.ts `canvasCmd` takes as their JSON
    /// (`["r", w, h, …]`: 2 to 16 items, w and h finite numbers in 0...16384): the command's index, its first word and
    /// the size as sent. Read in place, without building the values: the native model sizes the canvas by them, and
    /// the shortcut to the renderer leaves a payload with one to the validator, which does.
    static func resizes(_ batch: WidgetCanvasBatch) -> [(index: Int, offset: Int, width: Double, height: Double)] {
        let w = batch.words
        let n = w.count
        var out: [(index: Int, offset: Int, width: Double, height: Double)] = []
        var i = 0
        var index = 0
        while n - i >= 4 {
            let start = i
            let kind = w[i], argc = w[i + 3]
            guard argc >= 0, argc <= 64, argc == argc.rounded() else { return out }
            i += 4
            let first = i
            for _ in 0..<Int(argc) {
                guard i + 1 < n else { return out }
                let tag = w[i], payload = w[i + 1]
                i += 2
                if tag == 5 {
                    guard payload >= 0, payload == payload.rounded(), Double(n - i) >= payload else { return out }
                    i += Int(payload)
                }
            }
            if kind == 2, argc >= 2, argc <= 15, w[first] == 0, w[first + 2] == 0 {
                let width = w[first + 1], height = w[first + 3]
                if width.isFinite, height.isFinite, width >= 0, height >= 0, width <= 16384, height <= 16384 { out.append((index, start, width, height)) }
            }
            index += 1
        }
        return out
    }

    static func commandsJSON(_ batch: WidgetCanvasBatch) -> String? {
        batch.json(number: JSNumberText.string)
    }

    private static let prefix = Array("{\"op\":\"canvas\",\"id\":".utf8)
    private static let middle = Array(",\"bin\":".utf8)

    private struct Placeholder {
        var range: Range<Int>
        var id: Int
        var key: Int
    }

    /// Each payload key counts once: a later `{"op":"canvas","id":N,"bin":K}` with the same K stays
    /// in the JSON as it is, without commands, and the validator rejects it, so one payload is
    /// never expanded or validated more than once per batch.
    private static func placeholders(_ b: UnsafeBufferPointer<UInt8>) -> [Placeholder] {
        var out: [Placeholder] = []
        var seen = Set<Int>()
        var i = 0
        let n = b.count
        func digits(_ i: inout Int) -> Int? {
            let start = i
            var v = 0
            while i < n, b[i] >= 0x30, b[i] <= 0x39, i - start < 10 { v = v * 10 + Int(b[i] - 0x30); i += 1 }
            return i > start ? v : nil
        }
        while i + prefix.count < n {
            guard let hit = memchr(b.baseAddress! + i, 0x7b, n - i) else { break }
            let at = b.baseAddress!.distance(to: hit.assumingMemoryBound(to: UInt8.self))
            var j = at
            if n - j > prefix.count, prefix.indices.allSatisfy({ b[j + $0] == prefix[$0] }) {
                j += prefix.count
                if let id = digits(&j), n - j > middle.count, middle.indices.allSatisfy({ b[j + $0] == middle[$0] }) {
                    j += middle.count
                    if let key = digits(&j), j < n, b[j] == 0x7d {
                        if seen.insert(key).inserted { out.append(Placeholder(range: at..<(j + 1), id: id, key: key)) }
                        i = j + 1
                        continue
                    }
                }
            }
            i = at + 1
        }
        return out
    }

    static func only(_ json: String, _ canvas: [Int: WidgetCanvasBatch]) -> [(id: Int, batch: WidgetCanvasBatch)]? {
        var json = json
        return json.withUTF8 { b -> [(id: Int, batch: WidgetCanvasBatch)]? in
            let found = placeholders(b)
            guard !found.isEmpty, b.first == 0x5b, b.last == 0x5d else { return nil }
            var expected = 1
            var out: [(id: Int, batch: WidgetCanvasBatch)] = []
            for (index, p) in found.enumerated() {
                guard p.range.lowerBound == expected, let batch = canvas[p.key] else { return nil }
                expected = p.range.upperBound + 1
                if index == found.count - 1 { guard p.range.upperBound == b.count - 1 else { return nil } } else { guard b[p.range.upperBound] == 0x2c else { return nil } }
                out.append((p.id, batch))
            }
            return out
        }
    }

    static func splice(_ json: String, _ canvas: [Int: WidgetCanvasBatch]) -> String {
        var json = json
        return json.withUTF8 { b -> String in
            let found = placeholders(b)
            if found.isEmpty { return String(decoding: b, as: UTF8.self) }
            var out = ""
            out.reserveCapacity(b.count)
            var last = 0
            for p in found {
                out += String(decoding: b[last..<p.range.lowerBound], as: UTF8.self)
                out += "{\"op\":\"canvas\",\"id\":\(p.id),\"cmds\":" + (canvas[p.key].flatMap(commandsJSON) ?? "null") + "}"
                last = p.range.upperBound
            }
            out += String(decoding: b[last...], as: UTF8.self)
            return out
        }
    }
}

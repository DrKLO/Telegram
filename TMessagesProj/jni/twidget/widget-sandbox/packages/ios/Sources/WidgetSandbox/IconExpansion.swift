import Foundation
import WidgetSandboxProtocol

public typealias WidgetAssetResolver = (String) -> String?

/// `icon:<name>[?query]` tokens → the host resolver's `data:image/` URL. One instance per widget
/// (`TrustedValidator.setAssetResolver`). The prefix is exactly `icon:`, lowercase; only the CSS function name `url(`
/// is ASCII case-insensitive, as in CSS.
///
/// The validator never sees the host's URL: the strict caps (an attribute's length, a text value's, the text budgets, a
/// data: image's pixels) count what the widget sent, and the host's icons are trusted host data. Before validation a
/// token becomes a stand-in, `standInImage` + `#tw` + the widget's nonce + the icon's index (a 2×2 PNG that every
/// validator keeps as it is, in an attribute, an inline style and a stylesheet alike; the fragment is letters and digits,
/// which the CSS sanitizer writes back unchanged), and `restore` puts the host's URL in place of each stand-in in the
/// validated ops. The nonce is random per widget and never reaches the widget (its own document keeps the `icon:`
/// token, the renderer gets the host's URL), so a stand-in a widget writes itself stays the 2×2 image.
final class IconExpansion {
    static let maxURLBytes = 64 * 1024
    static let maxIconsPerBatch = 1024
    /// Per batch: the bytes of the host's URLs its tokens stand for, and the bytes `restore` puts into one batch of
    /// validated ops (the validator can send what it keeps again, a theme change its stylesheets).
    static let maxBytesPerBatch = 2 << 20
    /// Per widget: distinct tokens the resolver is ever asked for, and the bytes of its answers kept for the widget.
    /// Past either, new tokens stay unresolved (blocked like any other non-`data:` URL); known ones keep working.
    static let maxIconsPerWidget = 256
    static let maxResolvedBytesPerWidget = 2 << 20

    /// What the validator sees in place of a host icon: a real image (the URL policy reads its bytes and keeps it as it
    /// is: 2 × 2 pixels of PNG), in the characters an answer may have.
    static let standInImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAE0lEQVR4AWM8Y2z8nwEImBigAAAh4QI1lhEEowAAAABJRU5ErkJggg=="
    /// Digits of an icon's index after the nonce: fixed, so a stand-in followed by a digit still reads back
    /// (`maxIconsPerWidget` < 1000).
    static let indexDigits = 3

    private static let attribute = try! NSRegularExpression(pattern: "(\"(?!data\"|text\")[A-Za-z_:-]{1,32}\":\")(icon:[A-Za-z0-9_.-]{0,96}(?:\\?[A-Za-z0-9=&.%#_-]{0,400})?)(?=\")")
    private static let url = try! NSRegularExpression(pattern: "((?i:url)\\((?:\\\\?[\"'])?\\s*)(icon:[A-Za-z0-9_.-]{0,96}(?:\\?[A-Za-z0-9=&.%#_-]{0,400})?)")
    private static let fieldValue = try! NSRegularExpression(pattern: "\\Aicon:[A-Za-z0-9_.-]{0,96}(?:\\?[A-Za-z0-9=&.%#_-]{0,400})?\\z")
    private static let fieldKey = try! NSRegularExpression(pattern: "\\A[A-Za-z_:-]{1,32}\\z")
    private static let rawURL = try! NSRegularExpression(pattern: "((?i:url)\\((?:[\"'])?\\p{Z}*)(icon:[A-Za-z0-9_.-]{0,96}(?:\\?[A-Za-z0-9=&.%#_-]{0,400})?)")

    private let resolver: WidgetAssetResolver
    /// The profile's image types (the validator's tables), asked for once, at the first answer: an answer of another
    /// type is ignored, as the validator refused it when the host's URL went through it.
    private let imageTypes: () -> Set<String>
    private lazy var allowedTypes = imageTypes()
    private let diag: (String, String) -> Void
    /// `standInImage#tw<nonce>`: a stand-in is this and the icon's index in `indexDigits` digits.
    let standInPrefix: String
    private let marker: [UInt8]
    /// Every token this widget asked for and the index of the answer in `urls` (-1 for none); never evicted, at most
    /// `maxIconsPerWidget`.
    private var cache: [String: Int] = [:]
    /// The host's URLs, by index.
    private var urls: [[UInt8]] = []
    private var resolvedBytes = 0
    private var widgetLimitReported = false
    private var icons = 0
    private var bytes = 0
    private var limitReported = false

    init(resolver: @escaping WidgetAssetResolver, imageTypes: @escaping () -> Set<String>, nonce: UInt64 = .random(in: .min ... .max), diag: @escaping (String, String) -> Void) {
        self.resolver = resolver
        self.imageTypes = imageTypes
        self.diag = diag
        let hex = String(nonce, radix: 16)
        standInPrefix = Self.standInImage + "#tw" + String(repeating: "0", count: 16 - hex.count) + hex
        marker = Array(standInPrefix.utf8)
    }

    func beginBatch() {
        icons = 0
        bytes = 0
        limitReported = false
    }

    static func checked(_ url: String) -> String? {
        let b = Array(url.utf8)
        guard b.count <= maxURLBytes, url.hasPrefix("data:image/"), b.count > 11 else { return nil }
        for c in b {
            let ok = (c >= 0x61 && c <= 0x7a) || (c >= 0x41 && c <= 0x5a) || (c >= 0x30 && c <= 0x39)
                || c == 0x2d || c == 0x2e || c == 0x5f || c == 0x7e || c == 0x2c || c == 0x2f || c == 0x3a || c == 0x3d || c == 0x25 || c == 0x3b || c == 0x2b
            if !ok { return nil }
        }
        return url
    }

    private func isProfileImage(_ url: String) -> Bool {
        guard let type = URLPolicy.dataMime(url) else { return false }
        return allowedTypes.contains(type)
    }

    private func limit(_ detail: String) {
        if limitReported { return }
        limitReported = true
        diag("icon-limit", detail)
    }

    /// The widget's own budget: reported once per widget, and the rest of the batch still expands known tokens.
    private func widgetLimit(_ detail: String) {
        if widgetLimitReported { return }
        widgetLimitReported = true
        diag("icon-limit", detail)
    }

    /// The stand-in for `icon`, or nil: no answer, or past a limit.
    func standIn(_ icon: String) -> String? {
        guard icons < Self.maxIconsPerBatch else {
            limit("more than \(Self.maxIconsPerBatch) icon: URLs in one batch")
            return nil
        }
        icons += 1
        let index: Int
        if let hit = cache[icon] {
            index = hit
        } else {
            guard cache.count < Self.maxIconsPerWidget, resolvedBytes < Self.maxResolvedBytesPerWidget else {
                widgetLimit("more than \(Self.maxIconsPerWidget) distinct icon: tokens or \(Self.maxResolvedBytesPerWidget) bytes of icons in this widget")
                return nil
            }
            let answer = resolver(icon).flatMap(Self.checked).flatMap { isProfileImage($0) ? $0 : nil } ?? ""
            if resolvedBytes + answer.utf8.count > Self.maxResolvedBytesPerWidget {
                cache[icon] = -1
                resolvedBytes = Self.maxResolvedBytesPerWidget
                widgetLimit("more than \(Self.maxResolvedBytesPerWidget) bytes of icons in this widget")
                return nil
            }
            resolvedBytes += answer.utf8.count
            if answer.isEmpty {
                index = -1
            } else {
                index = urls.count
                urls.append(Array(answer.utf8))
            }
            cache[icon] = index
        }
        if index < 0 { return nil }
        let size = urls[index].count
        guard bytes + size <= Self.maxBytesPerBatch else {
            limit("icon: expansion over \(Self.maxBytesPerBatch) bytes in one batch")
            return nil
        }
        bytes += size
        let digits = String(index)
        return standInPrefix + String(repeating: "0", count: Self.indexDigits - digits.count) + digits
    }

    /// The host's URL a stand-in of this widget stands for (tests).
    func hostURL(_ standIn: String) -> String? {
        guard standIn.hasPrefix(standInPrefix), let index = Int(standIn.dropFirst(standInPrefix.count)), standIn.utf8.count == marker.count + Self.indexDigits, urls.indices.contains(index) else { return nil }
        return String(decoding: urls[index], as: UTF8.self)
    }

    func expand(_ json: String) -> String {
        guard Self.mayContainIcon(json) else { return json }
        return replace(Self.url, in: replace(Self.attribute, in: json))
    }

    func expandField(_ value: String, key: String?) -> String? {
        guard value.utf8.count >= 5, Self.mayContainIcon(value) else { return nil }
        var text = value
        if let key, key != "data", key != "text", Self.matches(Self.fieldKey, key), Self.matches(Self.fieldValue, value), let standIn = standIn(value) {
            text = standIn
        }
        let expanded = replace(Self.rawURL, in: text)
        return expanded == value ? nil : expanded
    }

    // MARK: after validation

    /// What one batch of validated ops may still take of the host's URLs (`maxBytesPerBatch`).
    struct Budget {
        var left = IconExpansion.maxBytesPerBatch
        var spent = false
    }

    /// Validated ops (JSON text) with the host's URLs in place of this widget's stand-ins.
    func restore(_ json: String) -> String {
        guard !urls.isEmpty else { return json }
        var budget = Budget()
        let restored = restore(json, &budget) ?? json
        report(budget)
        return restored
    }

    /// Validated ops (typed) with the host's URLs in place of this widget's stand-ins.
    func restore(_ ops: [WidgetOp]) -> [WidgetOp] {
        guard !urls.isEmpty else { return ops }
        var budget = Budget()
        var out = ops
        for (k, op) in ops.enumerated() {
            if let restored = restore(op, &budget) { out[k] = restored }
        }
        report(budget)
        return out
    }

    private func report(_ budget: Budget) {
        if budget.spent { diag("icon-limit", "icon: URLs over \(Self.maxBytesPerBatch) bytes in one batch of validated ops") }
    }

    /// Only where an icon can be: a string a widget wrote, past the validator (canvas word payloads never expand).
    private func restore(_ op: WidgetOp, _ budget: inout Budget) -> WidgetOp? {
        switch op.kind {
        case .style:
            return restore(op.value, &budget).map { WidgetOp(style: op.id, name: op.name, value: $0, important: op.important) }
        case .attribute:
            return restore(op.value, &budget).map { WidgetOp(attribute: op.id, name: op.name, value: $0) }
        case .text:
            return restore(op.value, &budget).map { WidgetOp(text: op.id, data: $0) }
        case .data:
            return restore(op.value, &budget).map { WidgetOp(data: op.id, data: $0) }
        case .css:
            return restore(op.value, &budget).map { WidgetOp(css: op.id, text: $0) }
        case .element:
            var changed = false
            var attributes = op.attributes
            for k in attributes.indices {
                if let value = restore(attributes[k].value, &budget) {
                    attributes[k].value = value
                    changed = true
                }
            }
            return changed ? WidgetOp(element: op.id, tag: op.tag, svg: op.svg, attributes: attributes, adopt: op.adopt) : nil
        case .other:
            guard let fields = op.fields, let restored = restore(any: fields, &budget) as? [String: Any] else { return nil }
            return WidgetOp(restored)
        case .canvas, .insert, .remove, .removeAttribute:
            return nil
        }
    }

    private func restore(any value: Any, _ budget: inout Budget) -> Any? {
        if let text = value as? String { return restore(text, &budget) }
        if let list = value as? [Any] {
            var out = list
            var changed = false
            for k in list.indices {
                if let item = restore(any: list[k], &budget) {
                    out[k] = item
                    changed = true
                }
            }
            return changed ? out : nil
        }
        if let object = value as? [String: Any] {
            var out = object
            var changed = false
            for (key, item) in object {
                if let restored = restore(any: item, &budget) {
                    out[key] = restored
                    changed = true
                }
            }
            return changed ? out : nil
        }
        return nil
    }

    /// `text` with the host's URLs in place of the stand-ins, or nil when it has none.
    private func restore(_ text: String, _ budget: inout Budget) -> String? {
        let width = marker.count + Self.indexDigits
        guard text.utf8.count >= width else { return nil }
        var text = text
        return text.withUTF8 { b -> String? in
            guard let base = b.baseAddress else { return nil }
            var out: [UInt8] = []
            var copied = 0
            var from = 0
            while from + width <= b.count, !budget.spent {
                guard let hit = marker.withUnsafeBufferPointer({ memmem(base + from, b.count - from, $0.baseAddress!, $0.count) }) else { break }
                let at = base.distance(to: hit.assumingMemoryBound(to: UInt8.self))
                from = at + 1
                guard at + width <= b.count, let index = Self.index(b, at + marker.count), index < urls.count else { continue }
                let url = urls[index]
                guard url.count <= budget.left else {
                    budget.spent = true
                    break
                }
                budget.left -= url.count
                if out.isEmpty { out.reserveCapacity(b.count + url.count) }
                out.append(contentsOf: UnsafeBufferPointer(rebasing: b[copied..<at]))
                out.append(contentsOf: url)
                copied = at + width
                from = copied
            }
            if copied == 0 { return nil }
            out.append(contentsOf: UnsafeBufferPointer(rebasing: b[copied..<b.count]))
            return String(decoding: out, as: UTF8.self)
        }
    }

    private static func index(_ b: UnsafeBufferPointer<UInt8>, _ start: Int) -> Int? {
        var n = 0
        for k in start..<(start + indexDigits) {
            let c = b[k]
            guard c >= 0x30 && c <= 0x39 else { return nil }
            n = n * 10 + Int(c - 0x30)
        }
        return n
    }

    // MARK: scanning

    /// A cheap pre-filter (ASCII case-insensitive, so it may say yes for `ICON:`); the patterns above decide.
    static func mayContainIcon(_ json: String) -> Bool {
        let found = json.utf8.withContiguousStorageIfAvailable { mayContainIcon(bytes: $0) }
        return found ?? (json.range(of: "icon:", options: .caseInsensitive) != nil)
    }

    static func mayContainIcon(bytes b: UnsafeBufferPointer<UInt8>) -> Bool {
        guard let base = b.baseAddress, b.count >= 5 else { return false }
        var i = 4
        while i < b.count, let hit = memchr(base + i, 0x3a, b.count - i) {
            let k = base.distance(to: hit.assumingMemoryBound(to: UInt8.self))
            if b[k - 4] | 0x20 == 0x69, b[k - 3] | 0x20 == 0x63, b[k - 2] | 0x20 == 0x6f, b[k - 1] | 0x20 == 0x6e { return true }
            i = k + 1
        }
        return false
    }

    private static func matches(_ regex: NSRegularExpression, _ s: String) -> Bool {
        regex.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
    }

    private func replace(_ regex: NSRegularExpression, in text: String) -> String {
        let ns = text as NSString
        var out = ""
        var last = 0
        regex.enumerateMatches(in: text, range: NSRange(location: 0, length: ns.length)) { match, _, stop in
            guard let match else { return }
            if limitReported {
                stop.pointee = true
                return
            }
            let icon = ns.substring(with: match.range(at: 2))
            guard let standIn = standIn(icon) else { return }
            out += ns.substring(with: NSRange(location: last, length: match.range(at: 2).location - last))
            out += standIn
            last = NSMaxRange(match.range(at: 2))
        }
        if last == 0 { return text }
        return out + ns.substring(from: last)
    }
}

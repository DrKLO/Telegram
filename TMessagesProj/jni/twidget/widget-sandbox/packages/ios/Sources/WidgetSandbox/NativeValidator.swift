import Foundation
import WidgetSandboxProtocol

protocol NativeValidatorOracle: AnyObject {
    func styleProperty(_ name: String, _ value: String) -> String?
    func styleAttribute(_ value: String) -> String
    func cssValue(_ value: String, where: String) -> String?
    func stylesheet(_ text: String) -> String
    func canvasCommands(_ json: String) -> String
    var diagnostics: Int { get }
    /// The reason the script validator stopped the widget (a canvas string past the cap in `canvasCommands`), or nil.
    var limitStop: String? { get }
}

struct NativeValidatorTables {
    var html: Set<String> = []
    var svg: [String: String] = [:]
    var dropped: Set<String> = []
    var attributes: Set<String> = []
    /** SVG attributes (lowercase): their own list since profile 1. */
    var svgAttributes: Set<String> = []
    /** HTML elements outside the profile rendered as another element (`<form>` → `<div>`, `<ruby>` → `<span>`). */
    var replace: [String: String] = [:]
    /** Elements dropped for security; the other dropped ones are outside the profile (a `profile` diagnostic). */
    var securityDropped: Set<String> = []
    var silent: Set<String> = []
    var url: Set<String> = []
    var css: Set<String> = []
    var animationValues: Set<String> = []
    var animationTags: Set<String> = []
    var fragmentTags: Set<String> = []
    var svgCase: [String: String] = [:]
    var props: [String: (type: String, tags: Set<String>?)] = [:]
    var methods: [String: Int] = [:]
    var mime: Set<String> = []
    var placeholder = ""
    var maxNodes = 50_000
    var maxText = 2 * 1024 * 1024
    var maxAttr = 4 * 1024 * 1024
    var maxOps = 200_000
    var maxCanvasCommands = 500_000
    var limbo = 0
    var complexTextBudget = 0
    var textBudget = 0
    /// Nodes in the document, compared once a batch is applied (validator.ts `MAX_DOCUMENT_NODES`); 0 = no cap (compat).
    var maxDocumentNodes = 0
    /// CSS rules and `@keyframes` over the document's stylesheets (validator.ts `countCssRules`); 0 = no cap.
    var maxCssRules = 0
    var maxKeyframes = 0
    var maxLiveAnims = 1024
    /// The widget's canvases together, in bitmap pixels (validator.ts `MAX_CANVAS_PIXELS`); 0 = no cap (compat).
    var maxCanvasPixels = 0
    /// Strict: a counted cap stops the widget (diagnostic `limit`) instead of rejecting the op.
    var stop = false
    /// Attributes whose values count against the strict text budgets (css-sanitizer.ts `isTextAttr`).
    var textAttributes: Set<String> = ["value", "placeholder", "alt", "label", "title"]
    var textAttributePrefixes: [String] = ["data-", "aria-"]

    func isTextAttribute(_ name: String) -> Bool {
        textAttributes.contains(name) || textAttributePrefixes.contains { name.hasPrefix($0) }
    }

    init?(json: String) {
        guard let root = JSONText.parse(json)?.object else { return nil }
        func strings(_ key: String) -> [String]? {
            guard let list = root[key]?.array else { return nil }
            var out: [String] = []
            for item in list {
                guard let s = item.string else { return nil }
                out.append(s)
            }
            return out
        }
        func pairs(_ key: String) -> [String: String]? {
            guard let list = root[key]?.array else { return nil }
            var out: [String: String] = [:]
            for item in list {
                guard let pair = item.array, pair.count == 2, let k = pair[0].string, let v = pair[1].string else { return nil }
                out[k] = v
            }
            return out
        }
        guard let html = strings("html"), let svg = pairs("svg"), let dropped = strings("dropped"), let attributes = strings("attrs"), let silent = strings("silent"),
              let url = strings("url"), let css = strings("css"), let anim = strings("anim"), let animTags = strings("animTags"), let fragTags = strings("fragTags"),
              let svgCase = pairs("svgCase"), let mime = strings("mime"), let placeholder = root["placeholder"]?.string,
              let props = root["props"]?.array, let methods = root["methods"]?.array, let limits = root["limits"]?.array, limits.count == 6 else { return nil }
        self.html = Set(html)
        self.svg = svg
        self.dropped = Set(dropped)
        self.attributes = Set(attributes)
        svgAttributes = Set(strings("svgAttrs") ?? attributes)
        replace = pairs("replace") ?? [:]
        securityDropped = Set(strings("securityDropped") ?? dropped)
        self.silent = Set(silent)
        self.url = Set(url)
        self.css = Set(css)
        animationValues = Set(anim)
        animationTags = Set(animTags)
        fragmentTags = Set(fragTags)
        self.svgCase = svgCase
        self.mime = Set(mime)
        self.placeholder = placeholder
        for item in props {
            guard let p = item.array, p.count == 3, let name = p[0].string, let type = p[1].string else { return nil }
            var tags: Set<String>?
            if let list = p[2].array { tags = Set(list.compactMap { $0.string }) }
            self.props[name] = (type, tags)
        }
        for item in methods {
            guard let p = item.array, p.count == 2, let name = p[0].string, let n = p[1].number else { return nil }
            self.methods[name] = Int(n)
        }
        let numbers = limits.compactMap { $0.number }
        guard numbers.count == 6 else { return nil }
        maxNodes = Int(numbers[0])
        maxText = Int(numbers[1])
        maxAttr = Int(numbers[2])
        maxOps = Int(numbers[3])
        maxCanvasCommands = Int(numbers[4])
        limbo = Int(numbers[5])
        complexTextBudget = Int(root["complexTextBudget"]?.number ?? 0)
        textBudget = Int(root["textBudget"]?.number ?? 0)
        maxDocumentNodes = Int(root["documentNodes"]?.number ?? 0)
        maxCssRules = Int(root["cssRules"]?.number ?? 0)
        maxKeyframes = Int(root["keyframes"]?.number ?? 0)
        maxLiveAnims = Int(root["liveAnims"]?.number ?? 1024)
        maxCanvasPixels = Int(root["canvasPixels"]?.number ?? 0)
        if case .bool(let on)? = root["stop"] { stop = on }
        if let names = strings("textAttrs") { textAttributes = Set(names) }
        if let prefixes = strings("textAttrPrefixes") { textAttributePrefixes = prefixes }
    }
}

final class NativeValidator {
    static let documentId = 1
    static let maxId = 1 << 31

    enum Kind: UInt8 {
        case element
        case text
        case comment
        case document
    }

    final class Node {
        let id: Int
        let kind: Kind
        let tag: String
        let svg: Bool
        var rendered: Bool
        let isStyle: Bool
        let adopted: Bool
        var parent: Int?
        var children: [Int] = []
        var data: String
        var attached = false
        var complex = 0
        var textSlots: [String: (length: Int, complex: Int)] = [:]
        /// A `<style>` element: the rules and `@keyframes` of its sanitized sheet, counted against the document's caps.
        var cssRules = 0
        var cssKeyframes = 0
        /// An HTML `<canvas>`: its bitmap (300 × 150 when made, then its `width` / `height` and the shim's `r`), counted
        /// against the widget's canvas pixels. Doubles, as validator.ts keeps them: a product is rounded as JS rounds it
        /// (2147483647² is 4611686014132420600 there).
        var canvasSize: (width: Double, height: Double)?
        let seq: Int

        init(id: Int, kind: Kind, tag: String = "", svg: Bool = false, rendered: Bool = true, isStyle: Bool = false, adopted: Bool = false, data: String = "", seq: Int) {
            self.id = id
            self.kind = kind
            self.tag = tag
            self.svg = svg
            self.rendered = rendered
            self.isStyle = isStyle
            self.adopted = adopted
            self.data = data
            self.seq = seq
        }
    }

    let tables: NativeValidatorTables
    weak var oracle: NativeValidatorOracle?
    var diag: (String, String) -> Void
    let intern = BinaryInternTable()

    private(set) var nodes: [Int: Node] = [:]
    private var adoptedHTML = false, adoptedHead = false, adoptedBody = false
    private var dirtyStyles: [Int: Int] = [:]
    private var dirtySeq = 0
    private var nodeSeq = 0
    private var out: [WidgetOp] = []
    private var styleCache: [String: [String: String]] = [:]
    private var styleCacheSize = 0
    var liveAnims = Set<Double>()

    init(tables: NativeValidatorTables, oracle: NativeValidatorOracle?, diag: @escaping (String, String) -> Void) {
        self.tables = tables
        self.oracle = oracle
        self.diag = diag
        reset()
    }

    func reset() {
        nodes.removeAll()
        adoptedHTML = false
        adoptedHead = false
        adoptedBody = false
        dirtyStyles.removeAll()
        liveAnims.removeAll()
        churnTokens = Double(Self.complexTextBudget)
        churnAt = nil
        complexText = 0
        totalText = 0
        cssRules = 0
        cssKeyframes = 0
        canvasPixels = 0
        stopReason = nil
        intern.reset()
        nodeSeq = 0
        nodes[Self.documentId] = Node(id: Self.documentId, kind: .document, rendered: false, seq: nextSeq())
    }

    var nodeCount: Int { nodes.count }

    private func nextSeq() -> Int {
        nodeSeq += 1
        return nodeSeq
    }

    func renderedTag(_ id: Int) -> String? {
        guard isId(id), let n = nodes[id], n.kind == .element, n.rendered else { return nil }
        return n.tag
    }

    @inline(__always) private func isId(_ v: Int) -> Bool { v > Self.documentId && v < Self.maxId }

    private func isId(_ v: JSONValue?) -> Int? {
        guard case .number(let d)? = v, d.isFinite, d == d.rounded(.towardZero), d > Double(Self.documentId), d < Double(Self.maxId) else { return nil }
        return Int(d)
    }

    private func isStr(_ v: JSONValue?, _ max: Int) -> String? {
        guard case .string(let s)? = v else { return nil }
        if s.utf8.count > max, s.utf16.count > max { return nil }
        return s
    }

    @inline(__always) private func fits(_ s: BinaryString, _ max: Int) -> Bool { s.units <= max }

    /// UTF-16 length of `s` when it is longer than `max` (validator.ts compares `.length`), else nil.
    private func over(_ s: String, _ max: Int) -> Int? {
        if s.utf8.count <= max { return nil }
        let units = s.utf16.count
        return units > max ? units : nil
    }

    // MARK: strict caps (validator.ts `stop`)

    /// Set once a counted cap was passed under strict: the reason the host stops the widget with, as it does for memory
    /// or CPU. From then on every batch is refused whole, so the renderer keeps the last render that fit.
    private(set) var stopReason: String?
    private var cssRules = 0
    private var cssKeyframes = 0
    /// The pixels of the widget's canvases together (each canvas's in `Node.canvasSize`): the strict cap is on the sum.
    private var canvasPixels = 0.0

    /// A cap passed: strict stops the widget (diagnostic `limit`, the host acts on it); compat only rejects the op.
    @discardableResult
    private func stop(_ reason: String) -> Bool {
        guard tables.stop else { return false }
        if stopReason == nil {
            stopReason = reason
            diag("limit", reason)
        }
        return false
    }

    private func tooLongText(_ length: Int) -> Bool {
        stop("Text limit exceeded: a text value of \(length) characters (max \(tables.maxText))")
    }

    private func tooLongAttribute(_ length: Int) -> Bool {
        stop("Attribute limit exceeded: a value of \(length) characters (max \(tables.maxAttr))")
    }

    /// The document's counted totals (its text, CSS rules, `@keyframes`) after a whole batch, not op by op
    /// (validator.ts `checkTotals`): a subtree replaced in one task or two sheets rewritten in one task pass through a
    /// sum of the old and the new that the document never holds. False when a total is over its cap (the widget stops).
    @discardableResult
    private func checkTotals() -> Bool {
        if tables.maxDocumentNodes > 0, nodes.count - 1 > tables.maxDocumentNodes {
            return stop("Node limit exceeded: \(nodes.count - 1) nodes in the document (max \(tables.maxDocumentNodes))")
        }
        if tables.textBudget > 0, totalText > tables.textBudget {
            return stop("Text limit exceeded: the document's text is over \(tables.textBudget) characters")
        }
        if tables.maxCssRules > 0, cssRules > tables.maxCssRules {
            return stop("CSS limit exceeded: \(cssRules) rules in the document's stylesheets (max \(tables.maxCssRules))")
        }
        if tables.maxKeyframes > 0, cssKeyframes > tables.maxKeyframes {
            return stop("CSS limit exceeded: \(cssKeyframes) @keyframes in the document's stylesheets (max \(tables.maxKeyframes))")
        }
        return true
    }

    private func tooManyCanvasCommands(_ count: Int) -> Bool {
        stop("Canvas limit exceeded: \(count) commands in one send (max \(tables.maxCanvasCommands))")
    }

    // MARK: canvas pixels (validator.ts `sizeCanvas`, `sizeCanvasAttr`)

    /// Canvas `node` takes the bitmap `width` × `height` (an attribute, its removal, the shim's `r`, a new canvas's
    /// 300 × 150); false when the widget's canvases would hold more pixels together than strict allows (the widget stops).
    private func sizeCanvas(_ node: Node, _ width: Double, _ height: Double) -> Bool {
        let total = canvasPixels - (node.canvasSize.map { $0.width * $0.height } ?? 0) + width * height
        // NaN-safe: no size may slip past as a comparison that is false both ways
        if tables.maxCanvasPixels > 0, !(total <= Double(tables.maxCanvasPixels)) { return tooManyCanvasPixels(total) }
        node.canvasSize = (width, height)
        canvasPixels = total
        return true
    }

    private func tooManyCanvasPixels(_ total: Double) -> Bool {
        stop("Canvas limit exceeded: canvases of \(Self.jsNumber(total)) pixels together (max \(tables.maxCanvasPixels))")
    }

    /// A number as a JS template literal prints it (`NaN` and `Infinity` too, should a size ever be one).
    private static func jsNumber(_ value: Double) -> String {
        if value.isNaN { return "NaN" }
        if value.isInfinite { return value < 0 ? "-Infinity" : "Infinity" }
        return JSNumberText.string(value)
    }

    /// A canvas's `width` or `height` attribute set to `value` (nil: removed), by HTML's rules for non-negative integers.
    private func sizeCanvasAttribute(_ node: Node, _ name: String, _ value: String?) -> Bool {
        guard node.tag == "canvas", !node.svg else { return true }
        // `name.toLowerCase()`: no character outside ASCII lowercases to a letter of these two names
        let isWidth: Bool
        if Self.isASCIICaseless(name, "width") { isWidth = true } else if Self.isASCIICaseless(name, "height") { isWidth = false } else { return true }
        // past 2147483647 it is no unsigned long and the canvas takes the default, as the browser does (0 × 1e310 is NaN)
        let n = value.flatMap(Self.canvasDimension).flatMap { $0 <= 2147483647 ? $0 : nil } ?? (isWidth ? 300 : 150)
        let size = node.canvasSize ?? (300, 150)
        return sizeCanvas(node, isWidth ? n : size.width, isWidth ? size.height : n)
    }

    private static func isASCIICaseless(_ name: String, _ lower: StaticString) -> Bool {
        var name = name
        return name.withUTF8 { b in
            guard b.count == lower.utf8CodeUnitCount else { return false }
            let p = lower.utf8Start
            for k in 0..<b.count where (b[k] >= 0x41 && b[k] <= 0x5a ? b[k] | 0x20 : b[k]) != p[k] { return false }
            return true
        }
    }

    /// `/^[\t\n\f\r ]*\+?(\d+)/` and `Number` of its digits, or nil when the value has none there (the default size).
    static func canvasDimension(_ value: String) -> Double? {
        var value = value
        return value.withUTF8 { b -> Double? in
            var i = 0
            while i < b.count, b[i] == 0x09 || b[i] == 0x0a || b[i] == 0x0c || b[i] == 0x0d || b[i] == 0x20 { i += 1 }
            if i < b.count, b[i] == 0x2b { i += 1 }
            let start = i
            while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
            guard i > start else { return nil }
            // strtod rounds as `Number` does, and overflows to infinity as it does
            return Double(String(decoding: UnsafeBufferPointer(rebasing: b[start..<i]), as: UTF8.self))
        }
    }

    /// A send's `r` commands: the command's index and word offset (canvas words), and the size `opCanvas` gives the canvas.
    struct Resize {
        var index: Int
        var offset = 0
        var width: Double
        var height: Double
    }

    /// The `r`s of a send that validator.ts `canvasCmd` takes (`['r', w, h, …]`, 2 to 16 items, w and h finite numbers
    /// in 0...16384), with the size truncated as `opCanvas` does.
    static func resizes(_ cmds: [JSONValue]) -> [Resize] {
        var out: [Resize] = []
        for (index, cmd) in cmds.enumerated() {
            guard case .array(let a) = cmd, a.count >= 3, a.count <= 16, case .string("r") = a[0], case .number(let w) = a[1], case .number(let h) = a[2],
                  w.isFinite, h.isFinite, w >= 0, h >= 0, w <= 16384, h <= 16384 else { continue }
            out.append(Resize(index: index, width: w.rounded(.towardZero), height: h.rounded(.towardZero)))
        }
        return out
    }

    /// validator.ts `opCanvas` sizes the canvas at each `r` as it comes: the first of a send's resizes that takes the
    /// widget's canvases past the strict cap, and the sum it would have made, or nil when they all fit.
    private func overflowingResize(_ node: Node, _ resizes: [Resize]) -> (resize: Resize, total: Double)? {
        guard tables.maxCanvasPixels > 0 else { return nil }
        var sum = canvasPixels, current = node.canvasSize.map { $0.width * $0.height } ?? 0
        for r in resizes {
            let total = sum - current + r.width * r.height
            if !(total <= Double(tables.maxCanvasPixels)) { return (r, total) }
            sum = total
            current = r.width * r.height
        }
        return nil
    }

    /// A send whose resize passes the cap: the commands before it are checked as `opCanvas` checks them (a string past
    /// the cap there stops the widget first, their diagnostics are reported), then the widget stops.
    private func stopAtResize(_ total: Double, before json: String?) -> Bool {
        if let json, checkedCanvasCommands(json) == nil { return false }
        return tooManyCanvasPixels(total)
    }

    /// The resizes of a send that went through, in order (none passes the cap: `overflowingResize` came first).
    private func applyResizes(_ node: Node, _ resizes: [Resize]) -> Bool {
        for r in resizes where !sizeCanvas(node, r.width, r.height) { return false }
        return true
    }

    /// The script validator stopped while checking canvas commands for this model (`canvasCommands`): the same stop,
    /// its `limit` diagnostic already reported.
    private func adoptOracleStop() -> Bool {
        guard stopReason == nil, let reason = oracle?.limitStop else { return stopReason != nil }
        stopReason = reason
        return true
    }

    // MARK: batches

    func process(_ decoded: BinaryOps.Decoded, canvas: [Int: WidgetCanvasBatch]) -> [WidgetOp] {
        out = []
        if stopReason != nil { return [] }
        if decoded.count > tables.maxOps, tables.stop {
            stop("Ops limit exceeded: \(decoded.count) operations in one batch (max \(tables.maxOps))")
            return []
        }
        if decoded.tooMany || decoded.count > tables.maxOps {
            diag("protocol-violation", "ops batch is not an array or too large")
            return []
        }
        for op in decoded.ops {
            if !apply(op, canvas: canvas) {
                if stopReason != nil { break }
                diag("protocol-violation", "rejected op " + JSONText.safe(BinaryOps.json(op, canvas: canvas)))
            }
            if stopReason != nil { break }
        }
        return finish()
    }

    func process(json value: JSONValue) -> [WidgetOp] {
        out = []
        if stopReason != nil { return [] }
        if case .array(let list) = value, list.count > tables.maxOps, tables.stop {
            stop("Ops limit exceeded: \(list.count) operations in one batch (max \(tables.maxOps))")
            return []
        }
        guard case .array(let list) = value, list.count <= tables.maxOps else {
            diag("protocol-violation", "ops batch is not an array or too large")
            return []
        }
        for item in list {
            if case .object(let o) = item, apply(o) {
                if stopReason != nil { break }
                continue
            }
            if stopReason != nil { break }
            var text: [UInt8] = []
            JSONText.write(item, to: &text)
            diag("protocol-violation", "rejected op " + JSONText.safe(text))
        }
        return finish()
    }

    private func finish() -> [WidgetOp] {
        if !dirtyStyles.isEmpty {
            for (id, _) in dirtyStyles.sorted(by: { $0.value < $1.value }) {
                if stopReason != nil { break }
                if let style = nodes[id] { out.append(WidgetOp(css: id, text: styleText(style))) }
            }
            dirtyStyles.removeAll()
        }
        if stopReason == nil { checkTotals() }
        let result = out
        out = []
        // A stopped widget's last batch is refused whole: the renderer keeps the last render that fit.
        return stopReason != nil ? [] : result
    }

    func setTheme() -> [WidgetOp] {
        var styles: [Node] = []
        for n in nodes.values where n.kind == .element && n.isStyle && n.rendered { styles.append(n) }
        styleCache.removeAll()
        styleCacheSize = 0
        let ops = styles.sorted { $0.seq < $1.seq }.map { WidgetOp(css: $0.id, text: styleText($0)) }
        return checkTotals() ? ops : []
    }

    private func styleText(_ style: Node) -> String {
        var css = ""
        for c in style.children { css += nodes[c]?.data ?? "" }
        let text = oracle?.stylesheet(css) ?? ""
        // The raw text is charged as text nodes; the strings it draws are charged decoded (validator.ts styleText).
        if budgeted, !updateTextSlot(style, key: "sheet", text: Self.cssStringText(text)) {
            releaseTextSlot(style, key: "sheet")
            return ""
        }
        // Counted here, compared with the caps by checkTotals once every changed sheet of the batch is counted.
        if tables.maxCssRules > 0 || tables.maxKeyframes > 0 {
            let counted = Self.countCssRules(text)
            cssRules += counted.rules - style.cssRules
            cssKeyframes += counted.keyframes - style.cssKeyframes
            style.cssRules = counted.rules
            style.cssKeyframes = counted.keyframes
        }
        return text
    }

    /// validator.ts `countCssRules`: style rules and `@keyframes` blocks in a sanitized stylesheet, over UTF-16 units; a
    /// rule inside `@media` / `@supports` / `@container` / `@layer` counts, the keyframe selectors inside `@keyframes`
    /// do not; strings, comments and escaped characters are skipped.
    static func countCssRules(_ css: String) -> (rules: Int, keyframes: Int) {
        enum Block { case rule, at, keyframes, frame }
        let u = Array(css.utf16)
        let n = u.count
        var rules = 0, keyframes = 0
        var stack: [Block] = []
        var prelude = 0
        var i = 0
        while i < n {
            let c = u[i]
            if c == 0x22 || c == 0x27 { // a string: skip to its end
                i += 1
                while i < n && u[i] != c {
                    if u[i] == 0x5c { i += 1 }
                    i += 1
                }
            } else if c == 0x5c { // an escape (`.a\{b`, `@keyframes k\}`): the next character is no brace
                i += 1
            } else if c == 0x2f && i + 1 < n && u[i + 1] == 0x2a { // a comment
                var end = i + 2
                while end + 1 < n && !(u[end] == 0x2a && u[end + 1] == 0x2f) { end += 1 }
                i = end + 1 < n ? end + 1 : n
            } else if c == 0x7b { // {
                let kind: Block
                if stack.last == .keyframes {
                    kind = .frame
                } else {
                    let head = Self.cssPreludeHead(u, prelude, i)
                    if Self.isKeyframesPrelude(head) { kind = .keyframes } else if head.first == 0x40 { kind = .at } else { kind = .rule }
                }
                if kind == .keyframes { keyframes += 1 } else if kind == .rule { rules += 1 }
                stack.append(kind)
                prelude = i + 1
            } else if c == 0x7d { // }
                _ = stack.popLast()
                prelude = i + 1
            } else if c == 0x3b { // ;
                prelude = i + 1
            }
            i += 1
        }
        return (rules, keyframes)
    }

    /// The start of `u[from..<to]` with leading whitespace trimmed (String.prototype.trim) and lowercased: enough to
    /// tell `@keyframes` / `@-webkit-keyframes` and other at-rules from style rules.
    private static func cssPreludeHead(_ u: [UInt16], _ from: Int, _ to: Int) -> [UInt16] {
        var start = from
        while start < to, isJSWhitespace(u[start]) { start += 1 }
        guard start < to else { return [] }
        let prefix = String(decoding: u[start..<min(to, start + 24)], as: UTF16.self)
        return Array(prefix.lowercased().utf16)
    }

    /// `/^@(-webkit-)?keyframes\b/` on the lowercased prelude.
    private static func isKeyframesPrelude(_ head: [UInt16]) -> Bool {
        for name in ["@keyframes", "@-webkit-keyframes"] {
            let k = Array(name.utf16)
            guard head.count >= k.count, Array(head[0..<k.count]) == k else { continue }
            guard head.count > k.count else { return true }
            let next = head[k.count]
            let word = (next >= 0x30 && next <= 0x39) || (next >= 0x41 && next <= 0x5a) || (next >= 0x61 && next <= 0x7a) || next == 0x5f
            return !word
        }
        return false
    }

    /// WhiteSpace and LineTerminator of ECMAScript (what `trim()` removes).
    private static func isJSWhitespace(_ c: UInt16) -> Bool {
        switch c {
        case 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000, 0xfeff: return true
        case 0x2000...0x200a: return true
        default: return false
        }
    }

    private var budgeted: Bool { tables.textBudget > 0 || tables.complexTextBudget > 0 }

    /// The text an attribute value can put on screen under the strict budgets, or nil (validator.ts `attrText`).
    private func attrText(_ name: String, _ value: String) -> String? {
        guard budgeted else { return nil }
        if name == "style" { return Self.cssStringText(value) }
        return tables.isTextAttribute(name) ? value : nil
    }

    /// css-sanitizer.ts `cssStringText`: the contents of the string tokens in sanitized CSS, over UTF-16 units.
    static func cssStringText(_ css: String) -> String {
        let u = Array(css.utf16)
        var out: [UInt16] = []
        var i = 0
        while i < u.count {
            let c = u[i]
            if c == 0x5c { i += 2; continue }
            if c != 0x22 && c != 0x27 { i += 1; continue }
            var j = i + 1
            while j < u.count && u[j] != c {
                if u[j] == 0x5c && j + 1 < u.count { j += 1 }
                out.append(u[j])
                j += 1
            }
            i = j + 1
        }
        return String(decoding: out, as: UTF16.self)
    }

    private func markDirty(_ id: Int) {
        if dirtyStyles[id] == nil {
            dirtySeq += 1
            dirtyStyles[id] = dirtySeq
        }
    }

    // MARK: binary front end

    private func apply(_ op: BinaryOp, canvas: [Int: WidgetCanvasBatch]) -> Bool {
        switch op {
        case .element(let id, let tag, let svg, let attributes):
            guard isId(id), fits(tag, 64) else { return false }
            let list = BinaryOps.normalizedAttributes(attributes).map { pair -> (String, String?) in (pair.0.s, pair.1.s) }
            return element(id, tag: tag.s, svg: svg, attributes: list)
        case .text(let id, let data):
            guard isId(id) else { return false }
            if !fits(data, tables.maxText) { return tooLongText(data.units) }
            return characterData(id, data.s, text: true)
        case .comment(let id, let data):
            guard isId(id) else { return false }
            if !fits(data, tables.maxText) { return tooLongText(data.units) }
            return characterData(id, data.s, text: false)
        case .data(let id, let data):
            guard isId(id) else { return false }
            if !fits(data, tables.maxText) { return tooLongText(data.units) }
            return setData(id, data.s)
        case .attribute(let id, let name, let value):
            guard isId(id), fits(name, 128) else { return false }
            if !fits(value, tables.maxAttr) { return tooLongAttribute(value.units) }
            return attribute(id, name.s, value.s)
        case .removeAttribute(let id, let name):
            guard isId(id), fits(name, 128) else { return false }
            return removeAttribute(id, name.s)
        case .insert(let parent, let id, let before):
            guard isId(id), isId(parent) || parent == Self.documentId, before == 0 || isId(before) else { return false }
            return insert(parent: parent, id: id, before: before == 0 ? nil : before)
        case .remove(let id):
            guard isId(id) else { return false }
            return remove(id)
        case .style(let id, let name, let value, let important):
            guard isId(id), fits(name, 128) else { return false }
            if !fits(value, tables.maxAttr) { return tooLongAttribute(value.units) }
            return style(id, name.s, value.s, important: important)
        case .canvas(let id, let key):
            guard let batch = canvas[key] else { return false }
            return canvasWords(id, batch)
        case .json(let value):
            guard case .object(let o) = value else { return false }
            return apply(o)
        }
    }

    // MARK: JSON front end

    private func apply(_ o: JSONObject) -> Bool {
        guard case .string(let kind)? = o["op"] else { return false }
        switch kind {
        case "el":
            guard let id = isId(o["id"]), let tag = isStr(o["tag"], 64) else { return false }
            var svg = false
            if let ns = o["ns"] {
                guard case .string("svg") = ns else { return false }
                svg = true
            }
            var list: [(String, String?)] = []
            if let attrs = o["attrs"] {
                guard case .object(let a) = attrs else { return false }
                list = a.entries.map { ($0.0, $0.1.string) }
            }
            return element(id, tag: tag, svg: svg, attributes: list)
        case "text", "comment":
            guard let id = isId(o["id"]), case .string(let data)? = o["data"] else { return false }
            if let length = over(data, tables.maxText) { return tooLongText(length) }
            return characterData(id, data, text: kind == "text")
        case "attr":
            guard let id = isId(o["id"]), let name = isStr(o["name"], 128), case .string(let value)? = o["value"] else { return false }
            if let length = over(value, tables.maxAttr) { return tooLongAttribute(length) }
            return attribute(id, name, value)
        case "rattr":
            guard let id = isId(o["id"]), let name = isStr(o["name"], 128) else { return false }
            return removeAttribute(id, name)
        case "data":
            guard let id = isId(o["id"]), case .string(let data)? = o["data"] else { return false }
            if let length = over(data, tables.maxText) { return tooLongText(length) }
            return setData(id, data)
        case "ins":
            guard let id = isId(o["id"]) else { return false }
            let parent: Int
            if let p = isId(o["parent"]) { parent = p } else if case .number(let d)? = o["parent"], d == Double(Self.documentId) { parent = Self.documentId } else { return false }
            var before: Int?
            switch o["before"] {
            case .null?: before = nil
            default:
                guard let b = isId(o["before"]) else { return false }
                before = b
            }
            return insert(parent: parent, id: id, before: before)
        case "rm":
            guard let id = isId(o["id"]) else { return false }
            return remove(id)
        case "style":
            guard let id = isId(o["id"]), let name = isStr(o["name"], 128), case .string(let value)? = o["value"] else { return false }
            if let length = over(value, tables.maxAttr) { return tooLongAttribute(length) }
            var important = false
            if case .bool(true)? = o["important"] { important = true }
            return style(id, name, value, important: important)
        case "prop":
            return prop(o)
        case "call":
            return call(o)
        case "canvas":
            guard let id = isId(o["id"]), case .array(let cmds)? = o["cmds"] else { return false }
            if cmds.count > tables.maxCanvasCommands { return tooManyCanvasCommands(cmds.count) }
            guard let node = nodes[id], node.kind == .element, node.tag == "canvas" else { return false }
            func json(_ list: ArraySlice<JSONValue>) -> String {
                var text: [UInt8] = []
                JSONText.write(.array(Array(list)), to: &text)
                return String(decoding: text, as: UTF8.self)
            }
            let resizes = Self.resizes(cmds)
            if let over = overflowingResize(node, resizes) { return stopAtResize(over.total, before: over.resize.index > 0 ? json(cmds[..<over.resize.index]) : nil) }
            return canvasJSON(id, json(cmds[...])) && applyResizes(node, resizes)
        default:
            return false
        }
    }

    // MARK: rules

    private func newNode(_ id: Int, _ make: () -> Node) -> Node? {
        if nodes[id] != nil || nodes.count >= tables.maxNodes { return nil }
        let n = make()
        nodes[id] = n
        return n
    }

    private static func isTagName(_ s: String) -> Bool {
        var s = s
        return s.withUTF8 { b in
            guard let first = b.first, isLetter(first) else { return false }
            for c in b.dropFirst() where !(isWord(c) || c == 0x2e || c == 0x2d) { return false }
            return true
        }
    }

    private static func isAttrName(_ s: String) -> Bool {
        var s = s
        return s.withUTF8 { b in
            guard let first = b.first, isLetter(first) || first == 0x5f else { return false }
            for c in b.dropFirst() where !(isWord(c) || c == 0x2e || c == 0x3a || c == 0x2d) { return false }
            return true
        }
    }

    private static func isStyleName(_ s: String) -> Bool {
        var s = s
        return s.withUTF8 { b in
            var i = 0
            while i < 2, i < b.count, b[i] == 0x2d { i += 1 }
            guard i < b.count, isLetter(b[i]) || b[i] == 0x5f else { return false }
            i += 1
            while i < b.count {
                guard isWord(b[i]) || b[i] == 0x2d else { return false }
                i += 1
            }
            return true
        }
    }

    @inline(__always) private static func isLetter(_ c: UInt8) -> Bool { (c | 0x20) >= 0x61 && (c | 0x20) <= 0x7a }
    @inline(__always) private static func isWord(_ c: UInt8) -> Bool { isLetter(c) || (c >= 0x30 && c <= 0x39) || c == 0x5f }

    private static func asciiLower(_ s: String) -> String {
        var s = s
        let needs = s.withUTF8 { b in b.contains { $0 >= 0x41 && $0 <= 0x5a } }
        return needs ? s.lowercased() : s
    }

    private func element(_ id: Int, tag: String, svg: Bool, attributes: [(String, String?)]) -> Bool {
        guard Self.isTagName(tag) else { return false }
        let lower = Self.asciiLower(tag)
        var outTag: String?
        var extra: [(name: String, value: String)] = []
        let isStyle = lower == "style"
        if svg {
            if isStyle { outTag = "style" } else if tables.dropped.contains(lower) && lower != "title" { outTag = nil } else { outTag = tables.svg[lower] }
            if outTag == nil && !tables.dropped.contains(lower) { diag("tag-dropped", "<svg:\(tag)>") }
        } else if tables.dropped.contains(lower) {
            outTag = nil
            if !tables.securityDropped.contains(lower) { diag("profile", "<\(lower)> dropped") }
            else if lower != "script" && lower != "title" && lower != "meta" { diag("tag-dropped", "<\(lower)>") }
        } else if let replacement = tables.replace[lower] {
            outTag = replacement
            extra = [("data-tg-tag", lower)]
            diag("profile", "<\(lower)> rendered as <\(replacement)>")
        } else if isStyle || tables.html.contains(lower) {
            outTag = lower
        } else {
            outTag = "span"
            extra = [("data-tg-tag", lower)]
            diag("tag-replaced", "<\(lower)> rendered as <span>")
        }
        var adopt: String?
        if !svg && (lower == "html" || lower == "head" || lower == "body") {
            let already = lower == "html" ? adoptedHTML : lower == "head" ? adoptedHead : adoptedBody
            if already {
                outTag = "div"
                extra = [("data-tg-tag", lower)]
            } else {
                if lower == "html" { adoptedHTML = true } else if lower == "head" { adoptedHead = true } else { adoptedBody = true }
                adopt = lower
            }
        }
        let seq = nextSeq()
        guard let node = newNode(id, { Node(id: id, kind: .element, tag: outTag ?? "", svg: svg, rendered: outTag != nil, isStyle: isStyle, adopted: adopt != nil, seq: seq) }) else { return false }
        guard let rendered = outTag else { return true }
        // a canvas is born 300 × 150
        if rendered == "canvas", !svg, !sizeCanvas(node, 300, 150) { return false }
        var attrs = extra
        for (name, value) in attributes {
            guard let value else { continue }
            if let length = over(value, tables.maxAttr) {
                if tables.stop { return tooLongAttribute(length) }
                continue
            }
            if !sizeCanvasAttribute(node, name, value) { return false }
            for pair in sanitizeAttribute(node, name, value) {
                if let text = attrText(pair.name, pair.value), !updateTextSlot(node, key: "attr:" + pair.name, text: text) { continue }
                attrs.append(pair)
            }
        }
        out.append(WidgetOp(element: id, tag: rendered, svg: node.svg, attributes: attrs, adopt: adopt))
        return true
    }

    static let complexTextBudget = 8192
    static let complexChurnPerSecond = 1024.0
    private var complexText = 0
    private var totalText = 0
    private var churnTokens = Double(NativeValidator.complexTextBudget)
    private var churnAt: Double?
    var clock: () -> Double = { Date().timeIntervalSince1970 }

    static let smallText = 256

    static func chargesChurn(_ s: String) -> Bool {
        if s.utf16.count > smallText { return true }
        for c in s.utf16 {
            if (c >= 0x0590 && c < 0x2000) || (c >= 0x2e80 && c <= 0xd7ff) || (c >= 0xf900 && c <= 0xffef) { return true }
        }
        return false
    }

    static func textWork(_ s: String) -> Int {
        var count = 0
        var run = false
        var units = 0
        for scalar in s.unicodeScalars {
            let c = scalar.value
            units += c > 0xffff ? 2 : 1
            if (c >= 0x2e80 && c <= 0xa4cf) || (c >= 0xac00 && c <= 0xd7ff) || c >= 0xf900 {
                if run { count += 1 }
                run = false
                count += 1
            } else if c <= 0x20 || c == 0x2d || c == 0x3f || c == 0x2f || c == 0x200b {
                if run { count += 1 }
                run = false
            } else {
                run = true
            }
        }
        if run { count += 1 }
        return max(count, (units + 1023) / 1024)
    }

    /// Until the host says the widget has loaded, inserted text is not charged to the churn bucket (validator.ts `loading`).
    private var loading = true
    func markLoaded() { loading = false }

    private func takeChurn(_ words: Int) -> Bool {
        if loading { return true }
        let now = clock()
        if let at = churnAt {
            churnTokens = min(Double(Self.complexTextBudget), churnTokens + max(0, now - at) * Self.complexChurnPerSecond)
        }
        churnAt = now
        guard churnTokens >= Double(words) else { return false }
        churnTokens -= Double(words)
        return true
    }

    private func overChurn() -> Bool {
        diag("text-churn", "text changes over \(Int(Self.complexChurnPerSecond)) units per second")
        return false
    }

    static func complexUnits(_ s: String) -> Int {
        var count = 0
        for unit in s.utf16 where unit >= 0x0590 { count += 1 }
        return count
    }

    private func overTextBudget() -> Bool {
        diag("text-budget", "document complex-script text over \(tables.complexTextBudget) units")
        return false
    }

    private func characterData(_ id: Int, _ data: String, text: Bool) -> Bool {
        guard nodes[id] == nil, nodes.count < tables.maxNodes else { return false }
        let length = data.utf16.count
        let units = text ? Self.complexUnits(data) : 0
        if units > 0, tables.complexTextBudget > 0, complexText + units > tables.complexTextBudget { return overTextBudget() }
        if tables.textBudget > 0, Self.chargesChurn(data), !takeChurn(Self.textWork(data)) { return overChurn() }
        let seq = nextSeq()
        guard let node = newNode(id, { Node(id: id, kind: text ? .text : .comment, rendered: text, data: data, seq: seq) }) else { return false }
        node.complex = units
        complexText += units
        totalText += length
        if text { out.append(WidgetOp(text: id, data: data)) }
        return true
    }

    private func setData(_ id: Int, _ data: String) -> Bool {
        guard let node = nodes[id], node.kind != .element, node.kind != .document else { return false }
        let textDelta = data.utf16.count - node.data.utf16.count
        let units = node.kind == .text ? Self.complexUnits(data) : 0
        let delta = units - node.complex
        if delta > 0, tables.complexTextBudget > 0, complexText + delta > tables.complexTextBudget { return overTextBudget() }
        if tables.textBudget > 0, Self.chargesChurn(data), !takeChurn(Self.textWork(data)) { return overChurn() }
        complexText += delta
        totalText += textDelta
        node.complex = units
        node.data = data
        let parent = node.parent.flatMap { nodes[$0] }
        if let parent, parent.isStyle {
            markDirty(parent.id)
        } else if node.rendered {
            out.append(WidgetOp(data: id, data: data))
        }
        return true
    }

    private func insert(parent parentId: Int, id: Int, before: Int?) -> Bool {
        guard let node = nodes[id], let p = nodes[parentId], p.kind != .text, p.kind != .comment else { return false }
        var a: Node? = p
        while let current = a {
            if current === node { return false }
            a = current.parent.flatMap { nodes[$0] }
        }
        var ref: Node?
        if let before {
            guard let r = nodes[before], r.parent == p.id, r !== node else { return false }
            ref = r
        }
        unlink(node)
        node.parent = p.id
        if let ref, let index = p.children.firstIndex(of: ref.id) {
            p.children.insert(id, at: index)
        } else if ref != nil {
            p.children.insert(id, at: max(0, p.children.count - 1))
        } else {
            p.children.append(id)
        }
        if p.isStyle { markDirty(p.id) }
        let renderHere = node.rendered && !node.adopted && p.rendered && !p.isStyle && p.kind == .element
        if !renderHere {
            if p.isStyle && node.rendered && node.kind != .element {
                out.append(WidgetOp(remove: id))
                node.rendered = false
            } else if node.attached {
                out.append(WidgetOp(insert: id, parent: tables.limbo, before: nil))
            }
            node.attached = false
            return true
        }
        var refId: Int?
        if let index = p.children.firstIndex(of: id) {
            var k = index + 1
            while k < p.children.count {
                if let sibling = nodes[p.children[k]], sibling.attached {
                    refId = sibling.id
                    break
                }
                k += 1
            }
        }
        out.append(WidgetOp(insert: id, parent: p.id, before: refId))
        node.attached = true
        return true
    }

    private func unlink(_ node: Node) {
        guard let parentId = node.parent else { return }
        if let old = nodes[parentId] {
            if let i = old.children.firstIndex(of: node.id) { old.children.remove(at: i) }
            if old.isStyle { markDirty(old.id) }
        }
        node.parent = nil
    }

    private func remove(_ id: Int) -> Bool {
        guard let node = nodes[id] else { return false }
        if node.adopted {
            for c in node.children { _ = isId(c) ? remove(c) : false }
            return true
        }
        unlink(node)
        if node.rendered { out.append(WidgetOp(remove: id)) }
        var stack = [node]
        while let n = stack.popLast() {
            complexText -= n.complex
            totalText -= n.data.utf16.count
            cssRules -= n.cssRules
            cssKeyframes -= n.cssKeyframes
            if let size = n.canvasSize { canvasPixels -= size.width * size.height }
            for charge in n.textSlots.values {
                totalText -= charge.length
                complexText -= charge.complex
            }
            nodes[n.id] = nil
            dirtyStyles[n.id] = nil
            for c in n.children {
                if let child = nodes[c] {
                    if child.rendered && !child.attached { out.append(WidgetOp(remove: child.id)) }
                    stack.append(child)
                }
            }
        }
        return true
    }

    private func attribute(_ id: Int, _ name: String, _ value: String) -> Bool {
        guard let node = nodes[id], node.kind == .element else { return false }
        if !node.rendered { return true }
        if !sizeCanvasAttribute(node, name, value) { return false }
        let pairs = sanitizeAttribute(node, name, value)
        if pairs.isEmpty {
            if let canon = canonicalAttributeName(node, name) {
                releaseTextSlot(node, key: "attr:" + canon)
                out.append(WidgetOp(removeAttribute: id, name: canon))
            }
            return true
        }
        for (n, v) in pairs {
            if let text = attrText(n, v), !updateTextSlot(node, key: "attr:" + n, text: text) { return false }
            out.append(WidgetOp(attribute: id, name: n, value: v))
        }
        return true
    }

    private func removeAttribute(_ id: Int, _ name: String) -> Bool {
        guard let node = nodes[id], node.kind == .element else { return false }
        if !node.rendered { return true }
        guard let canon = canonicalAttributeName(node, name) else { return true }
        if !sizeCanvasAttribute(node, canon, nil) { return false }
        releaseTextSlot(node, key: "attr:" + canon)
        out.append(WidgetOp(removeAttribute: id, name: canon))
        if node.tag == "a" && (canon == "href" || canon == "xlink:href") { out.append(WidgetOp(removeAttribute: id, name: "data-tg-link")) }
        return true
    }

    private func canonicalAttributeName(_ node: Node, _ name: String) -> String? {
        guard Self.isAttrName(name) else { return nil }
        let lower = Self.asciiLower(name)
        if lower.hasPrefix("on") { return nil }
        if node.svg { return lower == "xlink:href" ? "href" : (tables.svgCase[lower] ?? lower) }
        return lower
    }

    private static func mentionsURL(_ value: String) -> Bool {
        var value = value
        return value.withUTF8 { b in
            guard b.count >= 3 else { return false }
            for i in 0...(b.count - 3) {
                let c0 = b[i] | 0x20, c1 = b[i + 1] | 0x20, c2 = b[i + 2] | 0x20
                if c0 == 0x73 && c1 == 0x72 && c2 == 0x63 { return true }
                if i + 3 < b.count, c0 == 0x68 && c1 == 0x72 && c2 == 0x65 && (b[i + 3] | 0x20) == 0x66 { return true }
            }
            return false
        }
    }

    private func sanitizeAttribute(_ node: Node, _ rawName: String, _ value: String) -> [(name: String, value: String)] {
        guard let name = canonicalAttributeName(node, rawName) else { return [] }
        let lower = Self.asciiLower(name)
        let tag = node.tag
        if lower == "style" { return [("style", oracle?.styleAttribute(value) ?? "")] }
        if tables.url.contains(lower) { return sanitizeURLAttribute(node, name, lower, value) }
        if lower.hasPrefix("data-tg-") { return [] }
        if lower.hasPrefix("data-") || lower.hasPrefix("aria-") { return [(lower, value)] }
        if tables.silent.contains(lower) { return [] }
        if !(node.svg ? tables.svgAttributes : tables.attributes).contains(lower) {
            diag("attr-dropped", "\(tag)[\(lower)]")
            return []
        }
        if node.svg {
            if tables.animationTags.contains(tag) {
                if lower == "attributename" && Self.mentionsURL(value) { return [] }
                if tables.animationValues.contains(lower) {
                    var parts: [String] = []
                    for part in value.split(separator: ";", omittingEmptySubsequences: false) {
                        guard let clean = oracle?.cssValue(String(part), where: "\(tag)[\(lower)]") else { return [] }
                        parts.append(clean)
                    }
                    return [(name, parts.joined(separator: ";"))]
                }
            }
            if tables.css.contains(lower) {
                guard let clean = oracle?.cssValue(value, where: "\(tag)[\(lower)]") else { return [] }
                return [(name, clean)]
            }
            if lower == "font-family" {
                // Generic families only, as in CSS (D3b).
                guard let clean = oracle?.styleProperty("font-family", value), !clean.isEmpty else { return [] }
                return [(name, clean)]
            }
        }
        return [(name, value)]
    }

    private func sanitizeURLAttribute(_ node: Node, _ name: String, _ lower: String, _ value: String) -> [(name: String, value: String)] {
        let tag = node.tag
        func block() -> [(name: String, value: String)] {
            diag("url-blocked", "\(tag)[\(lower)]=\(JSONText.truncated(value, 80))")
            return []
        }
        let isHref = lower == "href" || lower == "xlink:href"
        if tag == "a" && isHref {
            if URLPolicy.isFragment(value) {
                if let fragment = URLPolicy.check(value, image: false, mime: tables.mime) { return [(name, fragment)] }
                return [(name, "#")]
            }
            return [(name, "#"), ("data-tg-link", "")]
        }
        if node.svg {
            if !isHref { return block() }
            if tag == "image" {
                if let url = URLPolicy.check(value, image: true, mime: tables.mime), !url.hasPrefix("#") { return [(name, url)] }
                return block()
            }
            if tables.fragmentTags.contains(tag) {
                if let url = URLPolicy.check(value, image: tag == "feImage", mime: tables.mime) { return [(name, url)] }
                return block()
            }
            return block()
        }
        if lower == "src" {
            if tag == "img" || tag == "input" {
                if let url = URLPolicy.check(value, image: true, mime: tables.mime), !url.hasPrefix("#") { return [("src", url)] }
                _ = block()
                return tag == "img" ? [("src", tables.placeholder), ("data-tg-placeholder", "")] : []
            }
            return block()
        }
        if lower == "usemap" { return [] }
        return block()
    }

    private func style(_ id: Int, _ name: String, _ value: String, important: Bool) -> Bool {
        guard let node = nodes[id], node.kind == .element else { return false }
        if !node.rendered { return true }
        guard Self.isStyleName(name) else { return false }
        var clean = styleValue(name, value)
        if budgeted, !updateTextSlot(node, key: "style:" + name, text: Self.cssStringText(clean)) {
            releaseTextSlot(node, key: "style:" + name)
            clean = ""
        }
        out.append(WidgetOp(style: id, name: name, value: clean, important: important))
        return true
    }

    private func styleValue(_ name: String, _ value: String) -> String {
        if value.isEmpty { return "" }
        var name = name
        if value.utf8.count <= ElementBatchFastPath.maxValueBytes, name.withUTF8({ ElementBatchFastPath.styleNames.contains($0[...]) }) {
            var bytes: [UInt8] = []
            var copy = value
            let ok = copy.withUTF8 { b -> Bool in
                CSSValueCanon.append(b[...], to: &bytes)
            }
            if ok { return String(decoding: bytes, as: UTF8.self) }
        }
        if let cached = styleCache[name]?[value] { return cached }
        let before = oracle?.diagnostics ?? 0
        let clean = oracle?.styleProperty(name, value) ?? ""
        if styleCacheSize < 4096, value.utf8.count < 512, oracle?.diagnostics == before {
            styleCache[name, default: [:]][value] = clean
            styleCacheSize += 1
        }
        return clean
    }

    private func prop(_ o: JSONObject) -> Bool {
        guard let id = isId(o["id"]), case .string(let name)? = o["name"] else { return false }
        guard let spec = tables.props[name], let node = nodes[id], node.kind == .element else { return false }
        let value = o["value"]
        let type: String
        let any: Any
        switch value {
        case .string(let s)?:
            type = "string"
            any = s
        case .number(let n)?:
            guard n.isFinite else { return false } // validator.ts opProp: 1e999 parses as Infinity
            type = "number"
            any = NSNumber(value: n)
        case .bool(let b)?:
            type = "boolean"
            any = b
        default:
            return false
        }
        guard type == spec.type else { return false }
        if case .string(let s)? = value, let length = over(s, tables.maxText) { return tooLongText(length) }
        if !node.rendered || (spec.tags.map { !$0.contains(node.tag) } ?? false) { return true }
        if case .string(let text)? = value, !updateTextSlot(node, key: "prop:" + name, text: text) { return false }
        out.append(WidgetOp(["op": "prop", "id": id, "name": name, "value": any]))
        return true
    }

    private func updateTextSlot(_ node: Node, key: String, text: String) -> Bool {
        guard tables.textBudget > 0 || tables.complexTextBudget > 0 else { return true }
        let length = text.utf16.count
        if length > tables.maxText { return tooLongText(length) }
        let previous = node.textSlots[key]
        let lengthDelta = length - (previous?.length ?? 0)
        let complex = Self.complexUnits(text)
        let complexDelta = complex - (previous?.complex ?? 0)
        if complexDelta > 0, tables.complexTextBudget > 0, complexText + complexDelta > tables.complexTextBudget { return overTextBudget() }
        if tables.textBudget > 0, Self.chargesChurn(text), !takeChurn(Self.textWork(text)) { return overChurn() }
        totalText += lengthDelta
        complexText += complexDelta
        node.textSlots[key] = length > 0 ? (length, complex) : nil
        return true
    }

    private func releaseTextSlot(_ node: Node, key: String) {
        guard let charge = node.textSlots.removeValue(forKey: key) else { return }
        totalText -= charge.length
        complexText -= charge.complex
    }

    private func call(_ o: JSONObject) -> Bool {
        guard let id = isId(o["id"]), case .string(let method)? = o["method"], let n = tables.methods[method] else { return false }
        guard let node = nodes[id], node.kind == .element else { return false }
        if !node.rendered { return true }
        if method == "scrollIntoView" {
            switch o["args"] {
            case nil:
                out.append(WidgetOp(["op": "call", "id": id, "method": method, "args": [0.0, 3.0, 0.0, 0.0]]))
                return true
            case .array(let list)? where list.isEmpty:
                out.append(WidgetOp(["op": "call", "id": id, "method": method, "args": [0.0, 3.0, 0.0, 0.0]]))
                return true
            default:
                break
            }
        }
        var args: [Double] = []
        if n > 0 {
            guard case .array(let list)? = o["args"] else { return false }
            for k in 0..<n {
                guard k < list.count, case .number(let d) = list[k], d.isFinite else { return false }
                args.append(d)
            }
        }
        if method == "scrollIntoView", zip(args, [3.0, 3.0, 2.0, 1.0]).contains(where: { value, limit in value != value.rounded() || value < 0 || value > limit }) {
            return false
        }
        out.append(WidgetOp(["op": "call", "id": id, "method": method, "args": args]))
        return true
    }

    private func canvasWords(_ id: Int, _ batch: WidgetCanvasBatch) -> Bool {
        guard isId(id) else { return false }
        // As the JSON op the payload stands for (`{"op":"canvas","id":N,"cmds":[...]}`): the count comes before the element.
        guard let count = CanvasWords.commandCount(batch) else { return false }
        if count > tables.maxCanvasCommands { return tooManyCanvasCommands(count) }
        guard let node = nodes[id], node.kind == .element, node.tag == "canvas" else { return false }
        let resizes = CanvasWords.resizes(batch).map { Resize(index: $0.index, offset: $0.offset, width: $0.width.rounded(.towardZero), height: $0.height.rounded(.towardZero)) }
        if let over = overflowingResize(node, resizes) {
            let before = over.resize.index > 0 ? CanvasWords.commandsJSON(WidgetCanvasBatch(words: Array(batch.words[..<over.resize.offset]), strings: batch.strings)) : nil
            return stopAtResize(over.total, before: before)
        }
        if CanvasWords.envelope(batch) {
            if let accepted = limitCanvasText(batch) { out.append(WidgetOp(canvas: id, batch: accepted)) }
            return applyResizes(node, resizes)
        }
        guard let json = CanvasWords.commandsJSON(batch) else { return false }
        return canvasJSON(id, json) && applyResizes(node, resizes)
    }

    /// The commands through the script validator; false when it stopped the widget on one of them (a string past the cap).
    private func canvasJSON(_ id: Int, _ json: String) -> Bool {
        guard let accepted = checkedCanvasCommands(json) else { return false }
        if !accepted.isEmpty { out.append(WidgetOp(["op": "canvas", "id": id, "cmds": accepted])) }
        return true
    }

    /// The commands the script validator keeps, less the text over the strict churn budget; nil when it stopped the
    /// widget on one of them (a string past the cap).
    private func checkedCanvasCommands(_ json: String) -> [Any]? {
        let clean = oracle?.canvasCommands(json)
        if adoptOracleStop() { return nil }
        guard let clean, clean != "[]", let cmds = (try? JSONSerialization.jsonObject(with: Data(clean.utf8))) as? [Any], !cmds.isEmpty else { return [] }
        return cmds.filter { value in
            guard tables.textBudget > 0, let cmd = value as? [Any], cmd.count >= 3, cmd[0] as? String == "c",
                  cmd[1] as? String == "fillText" || cmd[1] as? String == "strokeText",
                  let text = cmd[2] as? String, Self.chargesChurn(text) else { return true }
            return takeChurn(Self.textWork(text)) || overChurn()
        }
    }

    private func limitCanvasText(_ batch: WidgetCanvasBatch) -> WidgetCanvasBatch? {
        guard tables.textBudget > 0 else { return batch }
        var rejected: [Range<Int>] = []
        for charge in CanvasWords.textCharges(batch) where !takeChurn(charge.work) {
            _ = overChurn()
            rejected.append(charge.range)
        }
        guard !rejected.isEmpty else { return batch }
        let acceptedCount = batch.words.count - rejected.reduce(0) { $0 + $1.count }
        guard acceptedCount > 0 else { return nil }
        var words: [Double] = []
        words.reserveCapacity(acceptedCount)
        var start = 0
        for range in rejected {
            words.append(contentsOf: batch.words[start..<range.lowerBound])
            start = range.upperBound
        }
        words.append(contentsOf: batch.words[start...])
        return WidgetCanvasBatch(words: words, strings: batch.strings)
    }
}

enum URLPolicy {
    private static func isJSSpace(_ v: UInt32) -> Bool {
        switch v {
        case 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f, 0x205f, 0x3000, 0xfeff: return true
        case 0x2000...0x200a: return true
        default: return false
        }
    }

    static func normalize(_ raw: String) -> String {
        var scalars = Array(raw.unicodeScalars.filter { $0 != "\t" && $0 != "\n" && $0 != "\r" })
        var start = 0
        while start < scalars.count, scalars[start].value <= 0x20 { start += 1 }
        var end = scalars.count
        while end > start, scalars[end - 1].value <= 0x20 { end -= 1 }
        if start == 0 && end == scalars.count && scalars.count == raw.unicodeScalars.count { return raw }
        scalars = Array(scalars[start..<end])
        var out = String.UnicodeScalarView()
        out.append(contentsOf: scalars)
        return String(out)
    }

    static func isFragment(_ raw: String) -> Bool {
        normalize(raw).hasPrefix("#")
    }

    private static func hasPrefixIgnoringASCIICase(_ s: String, _ prefix: String) -> Bool {
        let a = Array(s.utf8.prefix(prefix.utf8.count)), b = Array(prefix.utf8)
        guard a.count == b.count else { return false }
        for (x, y) in zip(a, b) where (x >= 0x41 && x <= 0x5a ? x | 0x20 : x) != y { return false }
        return true
    }

    static func check(_ raw: String, image: Bool, mime allowed: Set<String>) -> String? {
        let url = normalize(raw)
        if url.hasPrefix("#") {
            for scalar in url.unicodeScalars.dropFirst() {
                if isJSSpace(scalar.value) { return nil }
                switch scalar {
                case "\"", "'", "(", ")", "\\", "<", ">": return nil
                default: continue
                }
            }
            return url
        }
        if !image { return nil }
        if hasPrefixIgnoringASCIICase(url, "data:") {
            guard let mime = dataMime(url), allowed.contains(mime) else { return nil }
            // An image of the profile's formats of a size its bitmap can be held in; bytes of none of them are broken, as
            // the browser would find them — unless a decoder outside the profile takes them, which they never reach
            return checkedImage(url)
        }
        return nil
    }

    static func dataMime(_ url: String) -> String? {
        let scalars = Array(url.unicodeScalars.dropFirst(5))
        var i = 0
        while i < scalars.count, scalars[i] != ",", scalars[i] != ";" { i += 1 }
        guard i < scalars.count else { return nil }
        let mimeEnd = i
        if scalars[i] == ";" {
            guard scalars[(i + 1)...].contains(",") else { return nil }
        }
        var start = 0, end = mimeEnd
        while start < end, isJSSpace(scalars[start].value) || isLineTerminator(scalars[start].value) || isZs(scalars[start]) { start += 1 }
        while end > start, isJSSpace(scalars[end - 1].value) || isLineTerminator(scalars[end - 1].value) || isZs(scalars[end - 1]) { end -= 1 }
        var view = String.UnicodeScalarView()
        view.append(contentsOf: scalars[start..<end])
        return String(view).lowercased()
    }

    private static func isLineTerminator(_ v: UInt32) -> Bool { v == 0x0a || v == 0x0d || v == 0x2028 || v == 0x2029 }
    private static func isZs(_ s: Unicode.Scalar) -> Bool { s.properties.generalCategory == .spaceSeparator }
}

// MARK: data: images (url-policy.ts `dataImageSize`, `MAX_IMAGE_PIXELS`, `BROKEN_IMAGE`)
//
// The same check as the validator bundle's `checkUrl`, step for step, so that the native model and the script give one
// verdict for every URL: an image's size read from its bytes as a decoder would (the browser sniffs the format: the
// declared type does not pick the decoder), more than 1024² pixels refused, bytes of none of the profile's formats
// broken. The body is read as fetch decodes it (without the URL's `#fragment`, percent-decoded, forgiving base64), in
// place and by the same windows, and the scans take the same bounded number of steps.

extension URLPolicy {
    /// The most pixels a data: image may have. A decoder takes the size from the image's header and holds a bitmap
    /// of it whatever the bytes are: a few hundred bytes of PNG can claim 65535² (16 GB as RGBA), and 2 KB of a one-colour
    /// PNG is a real 4096² (64 MB). 1024² is 4 MB, an image across a 320 px bubble at 3× (url-policy.ts `MAX_IMAGE_PIXELS`).
    static let maxImagePixels = 1024 * 1024
    /// An image no decoder takes: it fires `error` and has no size, as a broken one does.
    static let brokenImage = "data:image/png;base64,"
    /// How far into an image what gives its size is looked for: a JPEG's frame header past the segments before it, a
    /// GIF's first image descriptor past its extensions.
    static let headerScanBytes = 64 * 1024
    /// The least of a plain base64 body decoded at once (4096 characters), from where a read leaves the last window.
    static let blockBytes = 3072

    /// The last data: image checked, and the answer: elements in a row often show one image (a list's icon). Validators
    /// on several threads share it, as the bundle's module shares its own.
    private static let lastImageLock = NSLock()
    private static var lastImage: (url: String, checked: String?)?

    /// `checkUrl`'s data: image branch, past the type check: the URL, the broken image, or nil (refused).
    static func checkedImage(_ url: String) -> String? {
        lastImageLock.lock()
        if let last = lastImage, sameCodeUnits(last.url, url) {
            lastImageLock.unlock()
            return last.checked
        }
        lastImageLock.unlock()
        let checked: String?
        if let size = dataImageSize(url) {
            let (pixels, overflow) = size.width.multipliedReportingOverflow(by: size.height)
            checked = !overflow && pixels <= maxImagePixels ? url : nil
        } else {
            checked = brokenImage
        }
        lastImageLock.lock()
        lastImage = (url, checked)
        lastImageLock.unlock()
        return checked
    }

    /// The same characters, as the bundle's `===` compares them: Swift's `==` takes canonically equivalent strings for
    /// equal, whose bytes (and so whose images) differ.
    private static func sameCodeUnits(_ a: String, _ b: String) -> Bool {
        var a = a, b = b
        return a.withUTF8 { x in b.withUTF8 { y in x.count == y.count && (x.isEmpty || memcmp(x.baseAddress!, y.baseAddress!, x.count) == 0) } }
    }

    /// The size a decoder would give a data: image, by its signature, or nil for none of the profile's formats — BMP,
    /// ICO or AVIF bytes behind an image/png type could reach decoders the profile keeps out.
    static func dataImageSize(_ url: String) -> (width: Int, height: Int)? {
        imageSize(DataBytes(url))
    }

    /// `dataImageSize` and how many characters it had decoded as base64 (the bundle's `atob` calls), for the tests.
    static func dataImageScan(_ url: String) -> (size: (width: Int, height: Int)?, decodedCharacters: Int) {
        let bytes = DataBytes(url)
        let size = imageSize(bytes)
        return (size, bytes.decodedCharacters)
    }

    private static let pngSignature: [UInt8] = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
    private static func ascii(_ text: String) -> [UInt8] { Array(text.utf8) }
    private static let ihdr = ascii("IHDR"), gif87a = ascii("GIF87a"), gif89a = ascii("GIF89a"), riff = ascii("RIFF"), webp = ascii("WEBP")
    private static let vp8x = ascii("VP8X"), vp8l = ascii("VP8L"), vp8 = ascii("VP8 ")
    private static let jpegStart: [UInt8] = [0xff, 0xd8]

    private static func imageSize(_ bytes: DataBytes) -> (width: Int, height: Int)? {
        // The scans read the bytes in place, a step a comparison or two whatever the bytes are (64 KB of a JPEG's fill
        // bytes, 32 000 one-byte GIF sub-blocks), and take a bounded number of steps: a check is paid for every element.
        func has(_ from: Int, _ to: Int) -> Bool { bytes.has(from, to) }
        func at(_ i: Int) -> Int { bytes.at(i) }
        func u16be(_ i: Int) -> Int { (at(i) << 8) | at(i + 1) }
        func le16(_ i: Int) -> Int { at(i) | (at(i + 1) << 8) }
        func u24le(_ i: Int) -> Int { at(i) | (at(i + 1) << 8) | (at(i + 2) << 16) }
        /// Whether the bytes at i are those of `magic`.
        func `is`(_ i: Int, _ magic: [UInt8]) -> Bool {
            if !has(i, i + magic.count) { return false }
            for k in 0..<magic.count where at(i + k) != Int(magic[k]) { return false }
            return true
        }
        // PNG: the signature, then IHDR's width and height
        if `is`(0, pngSignature) && `is`(12, ihdr) && has(0, 24) { return (u16be(16) * 65536 + u16be(18), u16be(20) * 65536 + u16be(22)) }
        // GIF: the logical screen, grown to the first frame where that frame reaches past it, as decoders grow the image
        // (Wuffs without its strict-bounds quirk, Chromium's GIF reader before it); later frames are clipped to it
        if (`is`(0, gif87a) || `is`(0, gif89a)) && has(0, 13) {
            let screen = (le16(6), le16(8))
            // past the global colour table, extensions until the first image descriptor (an image has a few: frame
            // control, NETSCAPE looping, a comment), in at most 4096 sub-blocks (an XMP packet, read as sub-blocks, takes
            // one for every 30 to 120 bytes)
            var blocks = 0, extensions = 0
            var i = 13 + (at(10) & 0x80 != 0 ? 3 << ((at(10) & 7) + 1) : 0)
            while i < headerScanBytes && extensions < 256 {
                if !has(i, i + 2) { return nil }
                if at(i) == 0x2c { return has(i, i + 9) ? (max(screen.0, le16(i + 1) + le16(i + 5)), max(screen.1, le16(i + 3) + le16(i + 7))) : nil }
                // an extension (introducer, label, its sub-blocks up to the empty one); anything else, the trailer
                // included: no frame
                if at(i) != 0x21 { return nil }
                i += 2
                while true {
                    if i >= headerScanBytes { return nil }
                    blocks += 1
                    if blocks > 4096 || !has(i, i + 1) { return nil }
                    let n = at(i)
                    i += 1 + n
                    if n == 0 { break }
                }
                extensions += 1
            }
            return nil
        }
        // WebP: its first chunk, the canvas of an extended one
        if `is`(0, riff) && `is`(8, webp) {
            // each chunk's size ends where it ends: a whole lossless 1 × 1 image is 28 bytes
            if `is`(12, vp8x) && has(0, 30) { return (1 + u24le(24), 1 + u24le(27)) }
            if `is`(12, vp8l) && has(0, 25) && at(20) == 0x2f {
                return (1 + (at(21) | ((at(22) & 0x3f) << 8)), 1 + ((at(22) >> 6) | (at(23) << 2) | ((at(24) & 0x0f) << 10)))
            }
            if `is`(12, vp8) && has(0, 30) { return ((at(26) | (at(27) << 8)) & 0x3fff, (at(28) | (at(29) << 8)) & 0x3fff) }
            return nil
        }
        // JPEG: the first frame header (SOFn) past the segments before it, in at most 1024 steps (an image takes tens:
        // its APPn and tables; a fill byte or a standalone marker is a step)
        if `is`(0, jpegStart) && has(0, 4) {
            var i = 2, steps = 0
            while i + 9 <= headerScanBytes && steps < 1024 {
                steps += 1
                if !has(i, i + 4) || at(i) != 0xff { return nil }
                let marker = at(i + 1)
                if marker == 0xff { i += 1; continue }
                if marker == 0x01 || (marker >= 0xd0 && marker <= 0xd8) { i += 2; continue }
                if marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc { return has(i, i + 9) ? (u16be(i + 7), u16be(i + 5)) : nil }
                if marker == 0xd9 || marker == 0xda { return nil }
                i += 2 + u16be(i + 2)
            }
        }
        return nil
    }

    /// The white space fetch's forgiving base64 skips and the `;base64` parameter may carry (`[\t\n\f\r ]`).
    fileprivate static func isBodySpace<T: BinaryInteger>(_ c: T) -> Bool { c == 0x20 || c == 0x09 || c == 0x0a || c == 0x0c || c == 0x0d }
    private static func isHexDigit<T: BinaryInteger>(_ c: T) -> Bool { (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x46) || (c >= 0x61 && c <= 0x66) }
    private static func hexValue(_ c: UInt8) -> UInt8 { c <= 0x39 ? c - 0x30 : (c | 0x20) - 0x61 + 10 }

    /// A data: URL's body as fetch decodes it (percent-decoding, then forgiving base64), read without a copy: a read
    /// inside the bytes decoded last costs a comparison, and those are decoded again only where a read leaves them. A
    /// plain base64 body (the usual one: no escapes, no white space) is decoded by windows, byte i lying in the four
    /// characters of group i / 3, from where the read starts and at least `blockBytes` on, and is only checked to be
    /// plain up to them; any other is decoded once from its start, as far as the reads go (`BodyDecoder`). The body is
    /// kept as UTF-16 code units, as the bundle's string: the windows fall on the same characters.
    final class DataBytes {
        private let body: [UInt16]
        private let base64: Bool
        /// The characters known to be plain base64 from the start; -1 once one is not.
        private var plain: Int
        /// The bytes decoded last, from byte `start`, to the body's end if `toEnd`.
        private var start = 0
        private var text: [UInt8] = []
        private var toEnd = false
        /// Once part of the body is no base64, none of it is: the browser decodes nothing, and each scan that probes the
        /// signatures after the first asks again.
        private var failed = false
        private var decoder: BodyDecoder?
        private var windowCharacters = 0

        /// Characters given to the base64 decoder so far (the bundle's `atob`).
        var decodedCharacters: Int { windowCharacters + (decoder?.decodedCharacters ?? 0) }

        init(_ url: String) {
            var units = Array(url.utf16)
            // the fragment is the URL's, not the body's: fetch decodes the body without it
            if let hash = units.firstIndex(of: 0x23) { units.removeSubrange(hash...) }
            guard let comma = units.firstIndex(of: 0x2c) else {
                body = []
                base64 = false
                plain = -1
                return
            }
            // `;[\t\n\f\r ]*base64` at the end of the type and its parameters (ASCII case-insensitive), white space after
            var end = comma
            while end > 0 && URLPolicy.isBodySpace(units[end - 1]) { end -= 1 }
            var isBase64 = false
            let word = Array("base64".utf16)
            if end >= word.count && (0..<word.count).allSatisfy({ k in
                let c = units[end - word.count + k]
                return (c >= 0x41 && c <= 0x5a ? c | 0x20 : c) == word[k]
            }) {
                var k = end - word.count
                while k > 0 && URLPolicy.isBodySpace(units[k - 1]) { k -= 1 }
                isBase64 = k > 0 && units[k - 1] == 0x3b
            }
            body = Array(units[(comma + 1)...])
            base64 = isBase64
            plain = isBase64 ? 0 : -1
        }

        /// Makes bytes from…to readable by `at`: false past the end (or for a body that is no base64).
        @inline(__always) func has(_ from: Int, _ to: Int) -> Bool {
            (from >= start && to <= start + text.count) || load(from, to)
        }

        @inline(__always) func at(_ i: Int) -> Int {
            let k = i - start
            return k >= 0 && k < text.count ? Int(text[k]) : 0
        }

        /// Decodes the bytes from…to where the last ones do not hold them: whether the body has them.
        private func load(_ from: Int, _ to: Int) -> Bool {
            if failed || (from >= start && toEnd) { return false }
            if plain >= 0 {
                let first = (from / 3) * 3
                let end = min((max(to, first + URLPolicy.blockBytes) + 2) / 3 * 4, body.count)
                if end > plain {
                    plain = body[plain..<end].contains(where: { $0 == 0x25 || URLPolicy.isBodySpace($0) }) ? -1 : end
                }
                if plain >= 0 {
                    // a padding or a character no base64 has on the way (a window starts on a group of four, so it
                    // decodes as the whole body would)
                    let lo = min((first / 3) * 4, end)
                    windowCharacters += end - lo
                    guard let decoded = URLPolicy.forgivingBase64(body[lo..<end]) else {
                        failed = true
                        return false
                    }
                    text = decoded
                    start = first
                    toEnd = end == body.count
                    return to <= start + text.count
                }
            }
            if decoder == nil { decoder = BodyDecoder(body, base64: base64) }
            guard let decoded = decoder!.upTo(to) else {
                failed = true
                return false
            }
            start = 0
            text = decoded
            toEnd = decoded.count < to
            return !toEnd
        }
    }

    /// Decodes a data: URL's body from its start (percent-decoding, then forgiving base64 if `base64`) part by part, into
    /// bytes it keeps: `upTo(n)` gives them with at least the first n bytes if the body has them, nil if the body is no
    /// base64. The parts are those of the bundle's decoder, so a part's edge falls where it falls there.
    final class BodyDecoder {
        private let body: [UInt16]
        private let base64: Bool
        private var decoded: [UInt8] = []
        private var read = 0
        private var carry: [UInt8] = []
        private var failed = false
        private(set) var decodedCharacters = 0

        init(_ body: [UInt16], base64: Bool) {
            self.body = body
            self.base64 = base64
        }

        func upTo(_ n: Int) -> [UInt8]? {
            while !failed && decoded.count < n && read < body.count {
                // parts that grow with what is decoded, so that reading far costs about what is read
                var end = min(read + max(4096, decoded.count), body.count)
                let last = end >= body.count
                // an escape or a surrogate pair cut in two is read with the next part (UTF-8 of half a pair is another
                // byte count)
                if !last { end -= URLPolicy.cutTail(body[read..<end]) }
                let text = URLPolicy.percentDecode(body[read..<end])
                read = end
                if !base64 {
                    decoded += text
                    continue
                }
                var b64 = carry + text.filter { !URLPolicy.isBodySpace($0) }
                // whole groups only, but at the end, where the decoder takes a short last group and the padding as fetch does
                carry = last ? [] : Array(b64.suffix(b64.count % 4))
                b64.removeLast(carry.count)
                decodedCharacters += b64.count
                if let bytes = URLPolicy.forgivingBase64(b64) { decoded += bytes } else { failed = true }
            }
            return failed ? nil : decoded
        }
    }

    /// How many units at the end of `part` start what the next part completes: `%`, `%X`, a high surrogate.
    fileprivate static func cutTail(_ part: ArraySlice<UInt16>) -> Int {
        let last = part[part.endIndex - 1]
        if last == 0x25 || (last >= 0xd800 && last <= 0xdbff) { return 1 }
        return part.count > 1 && part[part.endIndex - 2] == 0x25 && isHexDigit(last) ? 2 : 0
    }

    /// Percent-decoding as the URL standard does it, into bytes: a character past ASCII is its UTF-8 bytes, as the URL
    /// parser encoded it (a lone surrogate as U+FFFD's); `%XX` is a byte, any other `%` (a `%u` too) stays itself.
    fileprivate static func percentDecode(_ part: ArraySlice<UInt16>) -> [UInt8] {
        let bytes: [UInt8] = part.contains(where: { $0 > 0x7f })
            ? Array(String(decoding: part, as: UTF16.self).utf8)
            : part.map { UInt8(truncatingIfNeeded: $0) }
        guard bytes.contains(0x25) else { return bytes }
        var out: [UInt8] = []
        out.reserveCapacity(bytes.count)
        var i = 0
        while i < bytes.count {
            if bytes[i] == 0x25, i + 2 < bytes.count, isHexDigit(bytes[i + 1]), isHexDigit(bytes[i + 2]) {
                out.append(hexValue(bytes[i + 1]) << 4 | hexValue(bytes[i + 2]))
                i += 3
            } else {
                out.append(bytes[i])
                i += 1
            }
        }
        return out
    }

    /// Forgiving base64 (infra.spec.whatwg.org), as `atob` decodes it in V8 and QuickJS: white space skipped, a short
    /// last group with or without its padding, nil for anything else (a character past ASCII, a padding in the middle).
    static func forgivingBase64<C: Collection>(_ text: C) -> [UInt8]? where C.Element: BinaryInteger {
        var out: [UInt8] = []
        out.reserveCapacity(text.count / 4 * 3 + 3)
        var acc: UInt32 = 0, seen = 0
        var i = text.startIndex
        while i != text.endIndex {
            let c = Int(text[i])
            if c > 0x7f { return nil }
            if isBodySpace(c) {
                i = text.index(after: i)
                continue
            }
            let v: Int
            switch c {
            case 0x41...0x5a: v = c - 0x41
            case 0x61...0x7a: v = c - 0x61 + 26
            case 0x30...0x39: v = c - 0x30 + 52
            case 0x2b: v = 62
            case 0x2f: v = 63
            default: v = -1
            }
            if v < 0 { break }
            acc = acc << 6 | UInt32(v)
            seen += 1
            if seen == 4 {
                out.append(UInt8(truncatingIfNeeded: acc >> 16))
                out.append(UInt8(truncatingIfNeeded: acc >> 8))
                out.append(UInt8(truncatingIfNeeded: acc))
                seen = 0
                acc = 0
            }
            i = text.index(after: i)
        }
        if seen != 0 {
            if seen == 3 {
                out.append(UInt8(truncatingIfNeeded: acc >> 10))
                out.append(UInt8(truncatingIfNeeded: acc >> 2))
            } else if seen == 2 {
                out.append(UInt8(truncatingIfNeeded: acc >> 4))
            } else {
                return nil
            }
            var pad = 0
            while i != text.endIndex {
                let c = Int(text[i])
                if pad < 2 && c == 0x3d { pad += 1 } else if !isBodySpace(c) { break }
                i = text.index(after: i)
            }
            if pad != 0 && seen + pad != 4 { return nil }
        }
        return i == text.endIndex ? out : nil
    }
}

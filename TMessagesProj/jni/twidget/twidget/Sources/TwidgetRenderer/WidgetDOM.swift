import WidgetSandboxProtocol
import Foundation
import os

final class WidgetNode {
    let id: Int
    let tag: String
    let svg: Bool
    let isText: Bool
    var synthetic = false
    var text: String {
        didSet {
            cache = nil
            WidgetDOM.bumpRevision()
        }
    }
    var cache: [UInt8]?
    var cacheEpoch = 0
    var attributes: [(name: String, value: String)] = []
    var formValue: String?
    var formChecked: Bool?
    var editing = false
    var declarations: [(name: String, value: String, important: Bool)] = []
    var children: [WidgetNode] = []
    weak var parent: WidgetNode?

    init(id: Int, tag: String, svg: Bool) {
        self.id = id
        self.tag = tag
        self.svg = svg
        isText = false
        text = ""
    }

    init(id: Int, text: String) {
        self.id = id
        tag = "#text"
        svg = false
        isText = true
        self.text = text
    }

    func attribute(_ name: String) -> String? {
        let key = name.lowercased()
        return attributes.first { $0.name.lowercased() == key }?.value
    }

    func setAttribute(_ name: String, _ value: String) {
        cache = nil
        WidgetDOM.bumpRevision()
        if name.lowercased() == "style" {
            declarations = WidgetNode.parseDeclarations(value)
            return
        }
        let key = name.lowercased()
        if let index = attributes.firstIndex(where: { $0.name.lowercased() == key }) {
            attributes[index].value = value
        } else {
            attributes.append((name, value))
        }
    }

    func removeAttribute(_ name: String) {
        cache = nil
        WidgetDOM.bumpRevision()
        if name.lowercased() == "style" {
            declarations.removeAll()
            return
        }
        let key = name.lowercased()
        attributes.removeAll { $0.name.lowercased() == key }
    }

    func setProperty(_ name: String, _ value: String, important: Bool) {
        cache = nil
        WidgetDOM.bumpRevision()
        let key = name.hasPrefix("--") ? name : name.lowercased()
        if !WidgetNames.isProperty(key) {
            return
        }
        if value.isEmpty {
            declarations.removeAll { $0.name == key }
            return
        }
        if let index = declarations.firstIndex(where: { $0.name == key }) {
            declarations[index] = (key, value, important)
        } else {
            declarations.append((key, value, important))
        }
    }

    static func parseDeclarations(_ text: String) -> [(name: String, value: String, important: Bool)] {
        var result: [(name: String, value: String, important: Bool)] = []
        for part in splitTopLevel(text, separator: ";") {
            guard let colon = part.firstIndex(of: ":") else {
                continue
            }
            let name = part[..<colon].trimmingCharacters(in: .whitespacesAndNewlines)
            var value = part[part.index(after: colon)...].trimmingCharacters(in: .whitespacesAndNewlines)
            if name.isEmpty || value.isEmpty {
                continue
            }
            var important = false
            if let range = value.range(of: "!important", options: [.caseInsensitive, .backwards]), value[range.upperBound...].trimmingCharacters(in: .whitespaces).isEmpty {
                important = true
                value = value[..<range.lowerBound].trimmingCharacters(in: .whitespaces)
            }
            let key = name.hasPrefix("--") ? name : name.lowercased()
            if !WidgetNames.isProperty(key) {
                continue
            }
            result.removeAll { $0.name == key }
            result.append((key, value, important))
        }
        return result
    }

    static func splitTopLevel(_ text: String, separator: Character) -> [String] {
        var parts: [String] = []
        var current = ""
        var depth = 0
        var quote: Character?
        var escaped = false
        for ch in text {
            if escaped {
                escaped = false
                current.append(ch)
                continue
            }
            if ch == "\\" {
                escaped = true
                current.append(ch)
                continue
            }
            if let q = quote {
                if ch == q {
                    quote = nil
                }
                current.append(ch)
                continue
            }
            switch ch {
            case "\"", "'":
                quote = ch
            case "(":
                depth += 1
            case ")":
                depth = max(0, depth - 1)
            default:
                break
            }
            if ch == separator && depth == 0 {
                parts.append(current)
                current = ""
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty {
            parts.append(current)
        }
        return parts
    }
}

enum WidgetNames {
    static func isTag(_ name: String) -> Bool {
        return valid(name, maxLength: 64, first: { $0.isASCIILetter }, rest: { $0.isASCIILetter || $0.isASCIIDigit || $0 == "-" })
    }

    static func isAttribute(_ name: String) -> Bool {
        return valid(name, maxLength: 64, first: { $0.isASCIILetter || $0 == "_" || $0 == ":" }, rest: { $0.isASCIILetter || $0.isASCIIDigit || $0 == "_" || $0 == ":" || $0 == "." || $0 == "-" })
    }

    static func isProperty(_ name: String) -> Bool {
        let body = name.hasPrefix("--") ? String(name.dropFirst(2)) : (name.hasPrefix("-") ? String(name.dropFirst()) : name)
        return valid(body, maxLength: 128, first: { $0.isASCIILetter || $0.isASCIIDigit || $0 == "-" || $0 == "_" }, rest: { $0.isASCIILetter || $0.isASCIIDigit || $0 == "-" || $0 == "_" })
    }

    private static func valid(_ name: String, maxLength: Int, first: (Unicode.Scalar) -> Bool, rest: (Unicode.Scalar) -> Bool) -> Bool {
        let scalars = name.unicodeScalars
        guard let head = scalars.first, scalars.count <= maxLength, first(head) else {
            return false
        }
        return scalars.dropFirst().allSatisfy(rest)
    }
}

private extension Unicode.Scalar {
    var isASCIILetter: Bool {
        return (value >= 65 && value <= 90) || (value >= 97 && value <= 122)
    }

    var isASCIIDigit: Bool {
        return value >= 48 && value <= 57
    }
}

final class WidgetDOM {
    static let maxNodes = 20000
    static let maxDepth = 256
    static let maxTextLength = 1 << 20
    static let blockedElements: Set<String> = ["script", "iframe", "frame", "frameset", "object", "embed", "link", "meta", "base", "portal", "noscript", "set", "template", "audio", "video", "source", "track"]
    static let blockedAttributes: Set<String> = ["srcdoc", "srcset", "action", "formaction", "ping", "background", "nonce", "target", "http-equiv"]
    static let idAttribute = "data-lh-id"

    let html: WidgetNode
    let head: WidgetNode
    let body: WidgetNode
    private(set) var nodes: [Int: WidgetNode] = [:]
    private(set) var usesTransitions = false
    var onViolation: ((String) -> Void)?
    var onCanvas: ((WidgetNode, [Any]) -> Void)?
    var onCall: ((WidgetNode, String, [Any]) -> Void)?
    var onProperty: ((WidgetNode, String, Any) -> Void)?
    var onImageSource: ((WidgetNode) -> Void)?

    init() {
        html = WidgetNode(id: -1, tag: "html", svg: false)
        head = WidgetNode(id: -2, tag: "head", svg: false)
        body = WidgetNode(id: -3, tag: "body", svg: false)
        head.parent = html
        body.parent = html
        html.children = [head, body]
    }

    func canvasNode(_ id: Int) -> WidgetNode? {
        WidgetDOM.bumpRevision()
        guard let node = nodes[id], node.tag == "canvas" else {
            return nil
        }
        return node
    }

    func reset() {
        nodes.removeAll()
        rootIds.removeAll()
        resetPatchLog()
        sheets = nil
        usesTransitions = false
        epoch += 1
        lastSize = 16 * 1024
        for root in [html, head, body] {
            root.children.removeAll()
            root.attributes.removeAll()
            root.declarations.removeAll()
            root.text = ""
            root.formValue = nil
            root.formChecked = nil
            root.editing = false
        }
        html.children = [head, body]
    }

    func node(_ id: Int) -> WidgetNode? {
        return nodes[id]
    }

    private var rootIds: [ObjectIdentifier: Int] = [:]

    func publicId(_ node: WidgetNode) -> Int {
        if node.id >= 0 {
            return node.id
        }
        return rootIds[ObjectIdentifier(node)] ?? 0
    }

    private func register(_ id: Int, _ node: WidgetNode) {
        node.cache = nil
        WidgetDOM.bumpRevision()
        nodes[id] = node
        if node.id < 0 {
            rootIds[ObjectIdentifier(node)] = id
        }
    }

    private func unregister(_ node: WidgetNode) {
        WidgetDOM.bumpRevision()
        let key = publicId(node)
        if key != 0, nodes[key] === node {
            nodes[key] = nil
        }
        for child in node.children {
            unregister(child)
        }
    }

    private func isRoot(_ node: WidgetNode) -> Bool {
        return node === html || node === head || node === body
    }

    private func detach(_ node: WidgetNode) {
        guard let parent = node.parent else {
            return
        }
        parent.children.removeAll { $0 === node }
        node.parent = nil
    }

    private func violation(_ detail: String) {
        onViolation?(detail)
    }

    private func setAttribute(_ node: WidgetNode, _ name: String, _ value: String) {
        noteSVG(node)
        if !WidgetNames.isAttribute(name) {
            violation("attribute name")
            return
        }
        let key = name.lowercased()
        if key.hasPrefix("on") || WidgetDOM.blockedAttributes.contains(key) {
            violation("attribute \(name)")
            return
        }
        if key.hasPrefix("data-lh-") {
            return
        }
        if key == "data-transition" {
            usesTransitions = true
        }
        node.setAttribute(name, value)
        if key == "src", !node.svg, node.tag == "img" {
            onImageSource?(node)
        }
    }

    struct PatchLog {
        var valid = true
        var texts: [ObjectIdentifier: (node: WidgetNode, soleText: Bool)] = [:]
        var formTexts: [ObjectIdentifier: WidgetNode] = [:]
        var attributes: [ObjectIdentifier: (node: WidgetNode, names: Set<String>)] = [:]
    }

    private(set) var patchLog = PatchLog()

    func resetPatchLog() {
        patchLog = PatchLog()
    }

    private(set) var rootVariables: [(name: String, value: String)] = []

    func setRootVariables(_ variables: [(name: String, value: String)]) -> Bool {
        if variables.elementsEqual(rootVariables, by: { $0.name == $1.name && $0.value == $1.value }) {
            return false
        }
        rootVariables = variables
        html.cache = nil
        patchLog.valid = false
        WidgetDOM.bumpRevision()
        return true
    }

    func isAttached(_ node: WidgetNode) -> Bool {
        var current: WidgetNode? = node
        while let next = current {
            if next === html {
                return true
            }
            current = next.parent
        }
        return false
    }

    static func hasSoleText(_ node: WidgetNode) -> Bool {
        return node.children.count == 1 && node.children[0].isText && !node.children[0].synthetic
    }

    private func noteChildren(of parent: WidgetNode?, moving child: WidgetNode) {
        guard patchLog.valid, let parent = parent, isAttached(parent) else {
            return
        }
        if !child.isText {
            patchLog.valid = false
            return
        }
        let key = ObjectIdentifier(parent)
        if patchLog.texts[key] == nil {
            patchLog.texts[key] = (parent, WidgetDOM.hasSoleText(parent))
        }
    }

    var svgTouched: Set<Int> = []

    func takeSVGTouched() -> Set<Int> {
        let touched = svgTouched
        svgTouched.removeAll()
        return touched
    }

    private func noteSVG(_ node: WidgetNode) {
        guard node.svg else {
            return
        }
        var root: WidgetNode? = nil
        var current: WidgetNode? = node
        while let candidate = current, candidate.svg {
            if candidate.tag.lowercased() == "svg" {
                root = candidate
            }
            current = candidate.parent
        }
        if let root = root {
            svgTouched.insert(root.id)
        }
    }

    private func noteAttribute(_ node: WidgetNode, _ name: String) {
        guard patchLog.valid, isAttached(node) else {
            return
        }
        if node.isText || !(name == "class" || name == "style" || name == "src" || node.svg) {
            patchLog.valid = false
            return
        }
        let key = ObjectIdentifier(node)
        if patchLog.attributes[key] == nil {
            patchLog.attributes[key] = (node, [])
        }
        patchLog.attributes[key]!.names.insert(name)
    }

    private func notePatch(_ kind: String, _ id: Int, _ op: WidgetOp) {
        guard patchLog.valid else {
            return
        }
        switch kind {
        case "el":
            if op["adopt"] != nil || nodes[id].map(isAttached) == true {
                patchLog.valid = false
            }
        case "text", "comment", "prop", "call", "canvas":
            break
        case "attr":
            if let node = nodes[id], let name = op["name"] as? String {
                noteAttribute(node, name.lowercased())
            }
        case "style":
            if let node = nodes[id] {
                noteAttribute(node, "style")
            }
        case "data":
            if let node = nodes[id], node.isText {
                noteChildren(of: node.parent, moving: node)
            } else if let node = nodes[id], isAttached(node) {
                patchLog.valid = false
            }
        case "ins":
            if let node = nodes[id], !isRoot(node) {
                noteChildren(of: node.parent, moving: node)
                let parentId = (op["parent"] as? NSNumber)?.intValue ?? 0
                if parentId != 0 {
                    noteChildren(of: nodes[parentId], moving: node)
                }
            }
        case "rm":
            if let node = nodes[id], !isRoot(node) {
                noteChildren(of: node.parent, moving: node)
            }
        default:
            if let node = nodes[id], isAttached(node) {
                patchLog.valid = false
            }
        }
    }

    private static func sameOverrides(_ before: [(String, String)]?, _ after: [(String, String)]?) -> Bool {
        let lhs: [(String, String)] = before ?? []
        let rhs: [(String, String)] = after ?? []
        if before == nil || after == nil || lhs.count != rhs.count {
            return before == nil && after == nil
        }
        for index in 0 ..< lhs.count {
            let left: (String, String) = lhs[index]
            let right: (String, String) = rhs[index]
            if left.0 != right.0 || left.1 != right.1 {
                return false
            }
        }
        return true
    }

    func patchChanges(theme: String, overrides: [Int: [(String, String)]], previous: [Int: [(String, String)]]) -> [UInt8]? {
        guard patchLog.valid else {
            return nil
        }
        var changes: [UInt8] = []
        var styled: Set<Int> = []
        func put(_ id: Int, _ name: String, _ value: String) -> Bool {
            if id == 0 || value.utf8.contains(0) {
                return false
            }
            WidgetDOM.append(String(id), to: &changes)
            changes.append(0)
            WidgetDOM.append(name, to: &changes)
            changes.append(0)
            WidgetDOM.append(value, to: &changes)
            changes.append(0)
            return true
        }
        for id in Set(previous.keys).union(overrides.keys) {
            let before = previous[id], after = overrides[id]
            if WidgetDOM.sameOverrides(before, after) {
                continue
            }
            guard let node = nodes[id], let value = styleValue(node, animated: after, theme: theme), put(id, "style", value) else {
                return nil
            }
            styled.insert(id)
        }
        for entry in patchLog.attributes.values {
            let id = publicId(entry.node)
            for name in entry.names where name != "style" || !styled.contains(id) {
                let value = name == "style" ? styleValue(entry.node, animated: overrides[id], theme: theme) : WidgetDOM.patchedAttribute(of: entry.node, name)
                guard let found = value, put(id, name, found) else {
                    return nil
                }
            }
        }
        for (key, entry) in patchLog.texts where patchLog.formTexts[key] == nil {
            guard entry.soleText, let value = WidgetDOM.patchedText(of: entry.node), put(publicId(entry.node), "", value) else {
                return nil
            }
        }
        for node in patchLog.formTexts.values {
            guard let value = node.formValue else { return nil }
            var text: [UInt8] = value.hasPrefix("\n") ? [10] : []
            WidgetDOM.escape(value, into: &text, attribute: false)
            guard put(publicId(node), "", String(decoding: text, as: UTF8.self)) else { return nil }
        }
        return changes
    }

    func apply(_ op: [String: Any]) {
        apply(WidgetOp(op))
    }

    func apply(_ op: WidgetOp) {
        WidgetDOM.bumpRevision()
        switch op.kind {
        case .style:
            guard let node = nodes[op.id] else {
                return
            }
            if patchLog.valid {
                noteAttribute(node, "style")
            }
            node.setProperty(op.name, op.value, important: op.important)
        case .element:
            if patchLog.valid, op.adopt != nil || nodes[op.id].map(isAttached) == true {
                patchLog.valid = false
            }
            if sheets != nil, op.adopt != nil {
                sheets = nil
            }
            createElement(op.id, tag: op.name, svg: op.important, adopt: op.adopt, attributes: op.attributes)
        case .text:
            createText(op.id, op.value)
        case .data:
            guard let node = nodes[op.id] else {
                return
            }
            noteMove(node, data: true, parentId: 0)
            node.text = WidgetDOM.clip(op.value)
        case .insert:
            guard let node = nodes[op.id] else {
                return
            }
            noteMove(node, data: false, parentId: op.parent)
            insert(node, parentId: op.parent, before: op.before.flatMap { nodes[$0] })
        case .remove:
            guard let node = nodes[op.id] else {
                return
            }
            noteMove(node, data: false, parentId: 0)
            remove(node)
        case .removeAttribute:
            guard let node = nodes[op.id] else {
                return
            }
            if patchLog.valid, isAttached(node) {
                patchLog.valid = false
            }
            node.removeAttribute(op.name)
        case .css:
            sheets = nil
            guard let node = nodes[op.id] else {
                return
            }
            if patchLog.valid, isAttached(node) {
                patchLog.valid = false
            }
            setCSS(node, op.value)
        case .attribute, .canvas, .other:
            applyGeneric(op)
        }
    }

    private func noteMove(_ node: WidgetNode, data: Bool, parentId: Int) {
        if patchLog.valid {
            if data {
                if node.isText {
                    noteChildren(of: node.parent, moving: node)
                } else if isAttached(node) {
                    patchLog.valid = false
                }
            } else if !isRoot(node) {
                noteChildren(of: node.parent, moving: node)
                if parentId != 0 {
                    noteChildren(of: nodes[parentId], moving: node)
                }
            }
        }
        if sheets != nil {
            if !node.isText || node.parent?.tag == "style" {
                sheets = nil
            } else if !data, parentId != 0, nodes[parentId]?.tag == "style" {
                sheets = nil
            }
        }
    }

    private func createElement(_ id: Int, tag: String, svg: Bool, adopt: String?, attributes: [(name: String, value: String)]) {
        if !WidgetNames.isTag(tag) || id <= 0 {
            violation("element name")
            return
        }
        if nodes[id] == nil && nodes.count >= WidgetDOM.maxNodes {
            violation("too many nodes")
            return
        }
        if WidgetDOM.blockedElements.contains(tag.lowercased()) {
            violation("element <\(tag)>")
            return
        }
        let node: WidgetNode
        switch adopt {
        case "html":
            node = html
        case "head":
            node = head
        case "body":
            node = body
        default:
            node = WidgetNode(id: id, tag: svg ? tag : tag.lowercased(), svg: svg)
        }
        register(id, node)
        for pair in attributes {
            setAttribute(node, pair.name, pair.value)
        }
    }

    private func createText(_ id: Int, _ data: String) {
        if id <= 0 || (nodes[id] == nil && nodes.count >= WidgetDOM.maxNodes) {
            violation("too many nodes")
            return
        }
        register(id, WidgetNode(id: id, text: WidgetDOM.clip(data)))
    }

    private func insert(_ node: WidgetNode, parentId: Int, before: WidgetNode?) {
        if isRoot(node) {
            return
        }
        if parentId == 0 {
            detach(node)
            return
        }
        guard let parent = nodes[parentId], !parent.isText else {
            return
        }
        var ancestor: WidgetNode? = parent
        while let current = ancestor {
            if current === node {
                return
            }
            ancestor = current.parent
        }
        if WidgetDOM.depth(of: parent) + WidgetDOM.height(of: node) >= WidgetDOM.maxDepth {
            violation("tree too deep")
            return
        }
        detach(node)
        node.parent = parent
        if let before = before, before.parent === parent, let index = parent.children.firstIndex(where: { $0 === before }) {
            parent.children.insert(node, at: index)
        } else {
            parent.children.append(node)
        }
    }

    private func remove(_ node: WidgetNode) {
        if isRoot(node) {
            return
        }
        detach(node)
        unregister(node)
    }

    private func setCSS(_ node: WidgetNode, _ css: String) {
        if node.tag == "style" {
            for child in node.children {
                child.parent = nil
            }
            let text = WidgetNode(id: -4, text: WidgetDOM.clip(css))
            text.synthetic = true
            text.parent = node
            node.children = [text]
        }
    }

    private func applyGeneric(_ op: WidgetOp) {
        guard let kind = op.op else {
            return
        }
        let id = op.kind == .other ? (op["id"] as? NSNumber)?.intValue ?? 0 : op.id
        notePatch(kind, id, op)
        noteSheets(kind, id, op)
        switch kind {
        case "el":
            var attributes: [(name: String, value: String)] = []
            for pair in (op["attrs"] as? [[Any]]) ?? [] where pair.count == 2 {
                if let name = pair[0] as? String, let value = pair[1] as? String {
                    attributes.append((name, value))
                }
            }
            createElement(id, tag: (op["tag"] as? String) ?? "span", svg: (op["ns"] as? String) == "svg", adopt: op["adopt"] as? String, attributes: attributes)
            return
        case "text":
            createText(id, (op["data"] as? String) ?? "")
            return
        case "comment":
            return
        default:
            break
        }
        guard let node = nodes[id] else {
            return
        }
        switch kind {
        case "attr":
            if let name = op["name"] as? String, let value = op["value"] as? String {
                setAttribute(node, name, value)
            }
        case "rattr":
            if let name = op["name"] as? String {
                node.removeAttribute(name)
            }
        case "data":
            node.text = WidgetDOM.clip((op["data"] as? String) ?? "")
        case "ins":
            insert(node, parentId: (op["parent"] as? NSNumber)?.intValue ?? 0, before: (op["before"] as? NSNumber).flatMap { nodes[$0.intValue] })
        case "rm":
            remove(node)
        case "style":
            if let name = op["name"] as? String, node !== html || !WidgetThemeStyle.variableNames.contains(name) {
                node.setProperty(name, (op["value"] as? String) ?? "", important: (op["important"] as? Bool) ?? false)
            }
        case "css":
            setCSS(node, (op["text"] as? String) ?? "")
        case "prop":
            if let name = op["name"] as? String {
                applyFormProperty(node, name, op["value"])
                onProperty?(node, name, op["value"] ?? NSNull())
            }
        case "call":
            if let method = op["method"] as? String {
                onCall?(node, method, (op["args"] as? [Any]) ?? [])
            }
        case "canvas":
            if node.tag == "canvas" {
                onCanvas?(node, (op["cmds"] as? [Any]) ?? [])
            }
        default:
            break
        }
    }

    private func applyFormProperty(_ node: WidgetNode, _ name: String, _ value: Any?) {
        let tag = node.tag.lowercased()
        var changed = false
        if name == "value", tag == "input" || tag == "textarea" {
            let text: String
            if let string = value as? String {
                text = WidgetDOM.clip(string)
            } else if let number = value as? NSNumber {
                text = number.stringValue
            } else {
                text = ""
            }
            if node.formValue != text {
                node.formValue = text
                changed = true
            }
        } else if tag == "select", name == "value" || name == "selectedIndex" {
            let options = WidgetDOM.options(of: node)
            var chosen: Int?
            if name == "selectedIndex" {
                chosen = (value as? NSNumber)?.intValue
            } else {
                let wanted = (value as? String) ?? (value as? NSNumber)?.stringValue ?? ""
                chosen = options.firstIndex { ($0.attribute("value") ?? WidgetDOM.textContent($0)) == wanted }
            }
            for (index, option) in options.enumerated() {
                let selected = index == chosen
                if option.formChecked != selected {
                    option.formChecked = selected
                    option.cache = nil
                    changed = true
                }
            }
        } else if name == "selected", tag == "option" {
            let selected = (value as? NSNumber)?.boolValue ?? false
            if node.formChecked != selected {
                node.formChecked = selected
                changed = true
            }
        } else if name == "checked", tag == "input" {
            let checked = (value as? NSNumber)?.boolValue ?? false
            if node.formChecked != checked {
                node.formChecked = checked
                changed = true
            }
        }
        guard changed else {
            return
        }
        if name == "value", tag == "textarea" {
            node.cache = nil
            WidgetDOM.bumpRevision()
            if patchLog.valid, isAttached(node) {
                patchLog.formTexts[ObjectIdentifier(node)] = node
            }
        } else {
            invalidateForm(node)
        }
    }

    func setFormProperty(_ node: WidgetNode, _ name: String, _ value: Any?) {
        applyFormProperty(node, name, value)
    }

    func setEditing(_ node: WidgetNode, _ editing: Bool) {
        guard node.editing != editing else {
            return
        }
        node.editing = editing
        invalidateForm(node)
    }

    private func invalidateForm(_ node: WidgetNode) {
        node.cache = nil
        WidgetDOM.bumpRevision()
        if patchLog.valid, isAttached(node) {
            patchLog.valid = false
        }
    }

    static func options(of node: WidgetNode) -> [WidgetNode] {
        var options: [WidgetNode] = []
        func collect(_ parent: WidgetNode) {
            for child in parent.children where !child.isText {
                if child.tag.lowercased() == "option" {
                    options.append(child)
                } else if child.tag.lowercased() == "optgroup" {
                    collect(child)
                }
            }
        }
        collect(node)
        return options
    }

    static func selectedIndex(of node: WidgetNode) -> Int {
        let options = WidgetDOM.options(of: node)
        if let index = options.lastIndex(where: { $0.formChecked == true }) {
            return index
        }
        if options.contains(where: { $0.formChecked != nil }) == false, let index = options.lastIndex(where: { $0.attribute("selected") != nil }) {
            return index
        }
        return options.isEmpty ? -1 : 0
    }

    static func textContent(_ node: WidgetNode) -> String {
        if node.isText {
            return node.text
        }
        return node.children.map(textContent).joined().trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static let unpatchableTextParents: Set<String> = ["style", "script", "textarea", "title", "noscript", "xmp", "iframe", "noembed", "noframes", "plaintext", "pre", "listing", "template"]

    static func patchedText(of node: WidgetNode) -> String? {
        guard hasSoleText(node), WidgetNames.isTag(node.tag), !unpatchableTextParents.contains(node.tag.lowercased()), !voidElements.contains(node.tag) || node.svg else {
            return nil
        }
        var bytes: [UInt8] = []
        escape(node.children[0].text, into: &bytes, attribute: false)
        return String(decoding: bytes, as: UTF8.self)
    }

    static func patchedAttribute(of node: WidgetNode, _ name: String) -> String? {
        guard let attribute = node.attributes.first(where: { $0.name.lowercased() == name }), WidgetNames.isAttribute(attribute.name) else {
            return nil
        }
        var bytes: [UInt8] = []
        escape(attribute.value, into: &bytes, attribute: true)
        return String(decoding: bytes, as: UTF8.self)
    }

    static func clip(_ text: String) -> String {
        return text.utf8.count > maxTextLength ? String(text.prefix(maxTextLength / 4)) : text
    }

    static func depth(of node: WidgetNode) -> Int {
        var depth = 0
        var current: WidgetNode? = node
        while let next = current?.parent {
            depth += 1
            current = next
        }
        return depth
    }

    static func height(of node: WidgetNode) -> Int {
        var best = 0
        var stack: [(WidgetNode, Int)] = [(node, 1)]
        while let (current, level) = stack.popLast() {
            best = max(best, level)
            if level > maxDepth {
                return level
            }
            for child in current.children {
                stack.append((child, level + 1))
            }
        }
        return best
    }

    private var epoch = 0
    private var epochTheme = ""
    private static let revisionLock: UnsafeMutablePointer<os_unfair_lock> = {
        let lock = UnsafeMutablePointer<os_unfair_lock>.allocate(capacity: 1)
        lock.initialize(to: os_unfair_lock())
        return lock
    }()
    private static var revisionValue: UInt64 = 0

    static var revision: UInt64 {
        os_unfair_lock_lock(revisionLock)
        defer { os_unfair_lock_unlock(revisionLock) }
        return revisionValue
    }

    static func bumpRevision() {
        os_unfair_lock_lock(revisionLock)
        revisionValue &+= 1
        os_unfair_lock_unlock(revisionLock)
    }

    static func invalidatingCaches(_ dom: WidgetDOM) -> WidgetDOM {
        dom.epoch += 1
        WidgetDOM.bumpRevision()
        return dom
    }

    func serialize(theme: String, overrides: [Int: [(String, String)]] = [:], canvasSource: (WidgetNode) -> String? = { _ in nil }) -> String {
        if theme != epochTheme {
            epochTheme = theme
            epoch += 1
        }
        var out: [UInt8] = []
        out.reserveCapacity(lastSize + 1024)
        out.append(contentsOf: Array("<!DOCTYPE html>".utf8))
        write(html, into: &out, theme: theme, overrides: overrides, canvasSource: canvasSource)
        lastSize = out.count
        return String(decoding: out, as: UTF8.self)
    }

    private var lastSize = 16 * 1024

    private var sheets: [String]?

    private func noteSheets(_ kind: String, _ id: Int, _ op: WidgetOp) {
        guard sheets != nil else {
            return
        }
        switch kind {
        case "css":
            sheets = nil
        case "el":
            if op["adopt"] != nil {
                sheets = nil
            }
        case "ins", "rm", "data":
            guard let node = nodes[id] else {
                return
            }
            if !node.isText || node.parent?.tag == "style" {
                sheets = nil
            } else if kind == "ins", let parentId = (op["parent"] as? NSNumber)?.intValue, parentId != 0, nodes[parentId]?.tag == "style" {
                sheets = nil
            }
        default:
            break
        }
    }

    func styleSheets() -> [String] {
        if let sheets = sheets {
            return sheets
        }
        var texts: [String] = []
        func visit(_ node: WidgetNode) {
            if node.tag == "style" {
                texts.append(node.children.map { $0.text }.joined())
                return
            }
            for child in node.children where !child.isText {
                visit(child)
            }
        }
        visit(html)
        sheets = texts
        return texts
    }

    private static let voidElements: Set<String> = ["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]

    private func write(_ node: WidgetNode, into out: inout [UInt8], theme: String, overrides: [Int: [(String, String)]], canvasSource: (WidgetNode) -> String?) {
        if node.isText {
            if node.cacheEpoch != epoch || node.cache == nil {
                var bytes: [UInt8] = []
                if let parent = node.parent, parent.tag == "style" {
                    bytes.append(contentsOf: Array(CSSTheme.resolve(node.text, theme: theme).replacingOccurrences(of: "</", with: "<\\/").utf8))
                } else {
                    WidgetDOM.escape(node.text, into: &bytes, attribute: false)
                }
                node.cache = bytes
                node.cacheEpoch = epoch
            }
            out.append(contentsOf: node.cache!)
            return
        }
        if !WidgetNames.isTag(node.tag) {
            return
        }
        let id = publicId(node)
        let canvas = node.tag == "canvas" && !node.svg ? canvasSource(node) : nil
        let animated = overrides[id]
        if canvas == nil && animated == nil && node.cacheEpoch == epoch, let cached = node.cache {
            out.append(contentsOf: cached)
        } else {
            var open: [UInt8] = []
            writeOpenTag(node, id: id, canvas: canvas, animated: animated, theme: theme, into: &open)
            if canvas == nil && animated == nil {
                node.cache = open
                node.cacheEpoch = epoch
            } else {
                node.cache = nil
            }
            out.append(contentsOf: open)
        }
        if canvas != nil {
            out.append(contentsOf: Array("</canvas>".utf8))
            return
        }
        if !node.svg && WidgetDOM.voidElements.contains(node.tag) {
            return
        }
        if !node.svg, let value = node.formValue, node.tag.lowercased() == "textarea" {
            if value.hasPrefix("\n") {
                out.append(10)
            }
            WidgetDOM.escape(value, into: &out, attribute: false)
        } else {
            for child in node.children {
                write(child, into: &out, theme: theme, overrides: overrides, canvasSource: canvasSource)
            }
        }
        out.append(contentsOf: [60, 47])
        WidgetDOM.append(node.tag, to: &out)
        out.append(62)
    }

    private func writeOpenTag(_ node: WidgetNode, id: Int, canvas: String?, animated: [(String, String)]?, theme: String, into out: inout [UInt8]) {
        func put(_ text: String) {
            WidgetDOM.append(text, to: &out)
        }
        put("<")
        put(node.tag)
        put(" data-lh-id=\"")
        put(String(id))
        put("\"")
        if let source = canvas {
            put(" src=\"")
            WidgetDOM.escape(source, into: &out, attribute: true)
            put("\"")
        }
        let formInput = !node.svg && node.tag.lowercased() == "input"
        let formOption = !node.svg && node.tag.lowercased() == "option"
        for attribute in node.attributes where (canvas == nil || attribute.name.lowercased() != "src") && WidgetNames.isAttribute(attribute.name) {
            if node.editing, attribute.name.lowercased() == "placeholder" || attribute.name.lowercased() == "value" {
                continue
            }
            if formInput, (node.formValue != nil && attribute.name.lowercased() == "value") || (node.formChecked != nil && attribute.name.lowercased() == "checked") {
                continue
            }
            if formOption, node.formChecked != nil, attribute.name.lowercased() == "selected" {
                continue
            }
            put(" ")
            put(attribute.name)
            put("=\"")
            WidgetDOM.escape(attribute.value, into: &out, attribute: true)
            put("\"")
        }
        if formInput, node.editing {
            put(" value=\"\"")
        } else if formInput, let value = node.formValue {
            put(" value=\"")
            WidgetDOM.escape(value, into: &out, attribute: true)
            put("\"")
        }
        if formInput, node.formChecked == true {
            put(" checked")
        }
        if formOption, node.formChecked == true {
            put(" selected")
        }
        if !node.declarations.isEmpty || animated != nil || node === html && !rootVariables.isEmpty {
            put(" style=\"")
            WidgetDOM.writeStyle(node, animated: animated, theme: theme, root: node === html ? rootVariables : nil, into: &out)
            put("\"")
        }
        put(">")
    }

    func styleValue(_ node: WidgetNode, animated: [(String, String)]?, theme: String) -> String? {
        let root = node === html ? rootVariables : nil
        if node.isText || node.declarations.isEmpty && animated == nil && (root ?? []).isEmpty {
            return nil
        }
        var out: [UInt8] = []
        WidgetDOM.writeStyle(node, animated: animated, theme: theme, root: root, into: &out)
        return String(decoding: out, as: UTF8.self)
    }

    private static func writeStyle(_ node: WidgetNode, animated: [(String, String)]?, theme: String, root: [(name: String, value: String)]?, into out: inout [UInt8]) {
        func put(_ text: String) {
            WidgetDOM.append(text, to: &out)
        }
        for declaration in node.declarations where declaration.name != "-lh-anim" && WidgetNames.isProperty(declaration.name) && (root == nil || !WidgetThemeStyle.variableNames.contains(declaration.name)) {
            put(declaration.name)
            put(":")
            WidgetDOM.escape(CSSTheme.resolve(declaration.value, theme: theme), into: &out, attribute: true)
            if declaration.important {
                put(" !important")
            }
            put(";")
        }
        for variable in root ?? [] {
            put(variable.name)
            put(":")
            WidgetDOM.escape(variable.value, into: &out, attribute: true)
            put(" !important;")
        }
        if let animated = animated {
            put("-lh-anim:1;")
            for (name, value) in animated where WidgetNames.isProperty(name) {
                put(name)
                put(":")
                WidgetDOM.escape(CSSTheme.resolve(value, theme: theme), into: &out, attribute: true)
                put(";")
            }
        }
    }

    static func hidesText(_ node: WidgetNode) -> Bool {
        return node.editing && !node.svg && node.tag.lowercased() == "textarea"
    }

    static func append(_ text: String, to out: inout [UInt8]) {
        var text = text
        text.withUTF8 { bytes in
            out.append(contentsOf: bytes)
        }
    }

    static func escape(_ text: String, into out: inout [UInt8], attribute: Bool) {
        var text = text
        text.withUTF8 { bytes in
            escape(bytes, into: &out, attribute: attribute)
        }
    }

    private static func escape(_ bytes: UnsafeBufferPointer<UInt8>, into out: inout [UInt8], attribute: Bool) {
        if !bytes.contains(where: { $0 == 38 || $0 == 60 || $0 == 62 || ($0 == 34 && attribute) }) {
            out.append(contentsOf: bytes)
            return
        }
        for byte in bytes {
            switch byte {
            case 38:
                out.append(contentsOf: [38, 97, 109, 112, 59])
            case 60:
                out.append(contentsOf: [38, 108, 116, 59])
            case 62:
                out.append(contentsOf: [38, 103, 116, 59])
            case 34 where attribute:
                out.append(contentsOf: [38, 113, 117, 111, 116, 59])
            default:
                out.append(byte)
            }
        }
    }
}

enum CSSTheme {
    private static let token = Array("light-dark(".utf8)

    static func lacksToken(_ css: String) -> Bool {
        var css = css
        return css.withUTF8 { bytes -> Bool in
            let count = bytes.count
            guard count >= token.count else {
                return bytes.allSatisfy { $0 < 0x80 }
            }
            var index = 0
            while index < count {
                let byte = bytes[index]
                if byte >= 0x80 {
                    return false
                }
                if byte == 0x28, index >= token.count - 1 {
                    var matches = true
                    var offset = 0
                    while offset < token.count - 1 {
                        var c = bytes[index - token.count + 1 + offset]
                        if c >= 0x41 && c <= 0x5A {
                            c |= 0x20
                        }
                        if c != token[offset] {
                            matches = false
                            break
                        }
                        offset += 1
                    }
                    if matches {
                        return false
                    }
                }
                index += 1
            }
            return true
        }
    }

    static func resolve(_ css: String, theme: String, depth: Int = 0) -> String {
        guard depth < 8, !lacksToken(css), css.range(of: "light-dark(", options: .caseInsensitive) != nil else {
            return css
        }
        let dark = theme == "dark"
        var out = ""
        var rest = Substring(css)
        while let range = rest.range(of: "light-dark(", options: .caseInsensitive) {
            out += rest[..<range.lowerBound]
            var depth = 1
            var index = range.upperBound
            while index < rest.endIndex, depth > 0 {
                let ch = rest[index]
                if ch == "(" {
                    depth += 1
                } else if ch == ")" {
                    depth -= 1
                }
                if depth > 0 {
                    index = rest.index(after: index)
                }
            }
            let inner = String(rest[range.upperBound..<index])
            let parts = WidgetNode.splitTopLevel(inner, separator: ",")
            let chosen = parts.count == 2 ? parts[dark ? 1 : 0] : (parts.first ?? "")
            out += resolve(chosen.trimmingCharacters(in: .whitespaces), theme: theme, depth: depth + 1)
            rest = index < rest.endIndex ? rest[rest.index(after: index)...] : ""
        }
        out += rest
        return out
    }
}

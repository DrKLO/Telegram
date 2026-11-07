import AppKit

public struct WidgetMenuItem: Equatable {
    public enum Kind: Equatable {
        case option
        case header
        case separator
    }

    public let kind: Kind
    public let title: String
    public let enabled: Bool
    public let selected: Bool

    public init(kind: Kind, title: String, enabled: Bool, selected: Bool) {
        self.kind = kind
        self.title = title
        self.enabled = enabled
        self.selected = selected
    }
}

public typealias WidgetMenuPresenter = (_ items: [WidgetMenuItem], _ anchor: CGRect, _ view: NSView, _ event: NSEvent?, _ completion: @escaping (Int?) -> Void) -> Void

public enum WidgetRendererEnvironment {
    public static var presentMenu: WidgetMenuPresenter?

    static func present(_ items: [WidgetMenuItem], anchor: CGRect, view: NSView, event: NSEvent?, completion: @escaping (Int?) -> Void) {
        if let presentMenu = presentMenu {
            presentMenu(items, anchor, view, event, completion)
        } else {
            WidgetNativeMenu.present(items, anchor: anchor, view: view, completion: completion)
        }
    }
}

final class WidgetNativeMenu: NSObject {
    private var chosen: Int?

    static func present(_ items: [WidgetMenuItem], anchor: CGRect, view: NSView, completion: @escaping (Int?) -> Void) {
        let target = WidgetNativeMenu()
        let menu = NSMenu()
        menu.autoenablesItems = false
        var selected: NSMenuItem?
        for (index, item) in items.enumerated() {
            switch item.kind {
            case .separator:
                menu.addItem(.separator())
            case .header:
                let header = NSMenuItem(title: item.title, action: nil, keyEquivalent: "")
                header.isEnabled = false
                menu.addItem(header)
            case .option:
                let entry = NSMenuItem(title: item.title, action: #selector(WidgetNativeMenu.choose(_:)), keyEquivalent: "")
                entry.target = target
                entry.tag = index
                entry.isEnabled = item.enabled
                entry.state = item.selected ? .on : .off
                menu.addItem(entry)
                if item.selected {
                    selected = entry
                }
            }
        }
        menu.minimumWidth = anchor.width
        let location = view.isFlipped ? CGPoint(x: anchor.minX, y: anchor.minY) : CGPoint(x: anchor.minX, y: anchor.maxY)
        menu.popUp(positioning: selected, at: location, in: view)
        completion(target.chosen)
    }

    @objc private func choose(_ sender: NSMenuItem) {
        chosen = sender.tag
    }
}

struct WidgetFormState {
    var rangeId = 0
    var rangeInitialValue = ""
    var focusedId = 0
    var generation = 0
    var initialValue = ""
    var lastActivation: CFTimeInterval = -.infinity
    var menuOpen = false
    var focusedSelect = 0
    var pointerSelectionId = 0
}

struct WidgetEditorSpec {
    let generation: Int
    let multiline: Bool
    let frame: CGRect
    let clip: CGRect?
    let font: NSFont
    let lineHeight: CGFloat
    let baseline: CGFloat
    let color: NSColor
    let alignment: NSTextAlignment
    let text: String
    let placeholder: String
    let maxLength: Int
    let readOnly: Bool
    let numeric: Bool
    let scrollOffset: CGPoint
}

extension LiteHTMLWidgetRenderer {
    static let editableInputTypes: Set<String> = ["", "text", "search", "number", "email", "url", "tel"]
    static let scriptFocusWindow: CFTimeInterval = 1

    private func formControl(from id: Int) -> WidgetNode? {
        var walk = dom.node(id)
        var depth = 0
        while let node = walk, depth < 6 {
            if !node.isText, !node.svg, ["select", "input", "textarea"].contains(node.tag.lowercased()) {
                return node
            }
            walk = node.parent
            depth += 1
        }
        return nil
    }

    private func isEditable(_ node: WidgetNode) -> Bool {
        guard !node.isText, !node.svg, node.attribute("disabled") == nil else {
            return false
        }
        switch node.tag.lowercased() {
        case "textarea":
            return true
        case "input":
            return LiteHTMLWidgetRenderer.editableInputTypes.contains((node.attribute("type") ?? "").lowercased().trimmingCharacters(in: .whitespaces))
        default:
            return false
        }
    }

    func formMouseDown(_ id: Int, point: CGPoint, event: NSEvent) {
        form.lastActivation = CACurrentMediaTime()
        form.focusedSelect = 0
        form.pointerSelectionId = 0
        guard !frozen, !destroyed, let node = formControl(from: id) else {
            return
        }
        let control = dom.publicId(node)
        if prevented.contains("\(control):mousedown") || prevented.contains("\(control):pointerdown") {
            return
        }
        if node.tag.lowercased() == "select" {
            form.focusedSelect = control
            openSelect(node, event: event)
        } else if node.tag.lowercased() == "input", node.attribute("type")?.lowercased() == "range", node.attribute("disabled") == nil {
            endEditing()
            form.rangeId = control
            form.rangeInitialValue = currentValue(node)
            dragRange(to: point)
        } else if isEditable(node) {
            focusControl(node, pointer: (point, event.clickCount))
            form.pointerSelectionId = control
        }
    }

    func dragEditorSelection(to point: CGPoint, event: NSEvent) {
        guard form.pointerSelectionId != 0, form.pointerSelectionId == form.focusedId else { return }
        let generation = form.generation
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            guard let view = view, let editor = view.formEditor, editor.generation == generation else { return }
            editor.dragSelection(to: point, from: view, event: event)
        }
    }

    func finishEditorSelection() {
        guard form.pointerSelectionId != 0 else { return }
        form.pointerSelectionId = 0
        let generation = form.generation
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            guard let editor = view?.formEditor, editor.generation == generation else { return }
            editor.finishSelection()
        }
    }

    func dragRange(to point: CGPoint) {
        guard form.rangeId != 0, !frozen, !destroyed,
              let node = dom.node(form.rangeId), dom.isAttached(node), node.attribute("disabled") == nil,
              let box = elementBounds()[form.rangeId], box.width > 0 else { return }
        func number(_ name: String, _ fallback: Double) -> Double {
            guard let raw = node.attribute(name), let value = Double(raw), value.isFinite else { return fallback }
            return value
        }
        let minimum = number("min", 0)
        let maximum = max(minimum, number("max", 100))
        let inset = min(12, box.width / 2)
        let fraction = min(1, max(0, (point.x - box.minX - inset) / max(1, box.width - inset * 2)))
        var value = minimum + Double(fraction) * (maximum - minimum)
        if node.attribute("step")?.lowercased() != "any" {
            let step = number("step", 1) > 0 ? number("step", 1) : 1
            let base = number("min", number("value", 0))
            value = base + ((value - base) / step).rounded() * step
            if value > maximum { value -= step }
        }
        value = min(maximum, max(minimum, value))
        guard value.isFinite else { return }
        let text = String(format: "%.12g", locale: Locale(identifier: "en_US_POSIX"), value)
        guard Double(currentValue(node)) != value else { return }
        dom.setFormProperty(node, "value", text)
        setNeedsRender()
        postForm("input", form.rangeId, ["value": text])
    }

    func finishRange(at point: CGPoint) {
        dragRange(to: point)
        let id = form.rangeId
        form.rangeId = 0
        guard id != 0, let node = dom.node(id), dom.isAttached(node), node.attribute("disabled") == nil else { return }
        let value = currentValue(node)
        if value != form.rangeInitialValue { postForm("change", id, ["value": value]) }
    }

    private func postForm(_ type: String, _ id: Int, _ extra: [String: Any] = [:]) {
        guard !frozen, !destroyed else {
            return
        }
        guard ["input", "change", "submit", "focus", "blur"].contains(type) || listened.contains(type) else {
            return
        }
        var ev: [String: Any] = ["type": type, "targetId": id]
        for (key, value) in extra {
            ev[key] = value
        }
        post(["type": "event", "ev": ev])
    }

    func menuItems(for select: WidgetNode) -> (items: [WidgetMenuItem], options: [Int]) {
        let current = WidgetDOM.selectedIndex(of: select)
        var items: [WidgetMenuItem] = []
        var options: [Int] = []
        var index = 0
        func addOption(_ option: WidgetNode, groupDisabled: Bool) {
            let title = option.attribute("label") ?? WidgetDOM.textContent(option)
            items.append(WidgetMenuItem(kind: .option, title: title.isEmpty ? " " : title, enabled: !groupDisabled && option.attribute("disabled") == nil, selected: index == current))
            options.append(index)
            index += 1
        }
        for child in select.children where !child.isText {
            switch child.tag.lowercased() {
            case "option":
                addOption(child, groupDisabled: false)
            case "optgroup":
                if !items.isEmpty {
                    items.append(WidgetMenuItem(kind: .separator, title: "", enabled: false, selected: false))
                    options.append(-1)
                }
                items.append(WidgetMenuItem(kind: .header, title: child.attribute("label") ?? "", enabled: false, selected: false))
                options.append(-1)
                let disabled = child.attribute("disabled") != nil
                for option in child.children where !option.isText && option.tag.lowercased() == "option" {
                    addOption(option, groupDisabled: disabled)
                }
            default:
                break
            }
        }
        return (items, options)
    }

    private func openSelect(_ node: WidgetNode, event: NSEvent?) {
        guard node.attribute("disabled") == nil, !form.menuOpen else {
            return
        }
        endEditing()
        let id = dom.publicId(node)
        let (items, options) = menuItems(for: node)
        guard options.contains(where: { $0 >= 0 }), let laid = elementBounds()[id] else {
            return
        }
        let anchor = visualPlacement(node, laid).0
        form.menuOpen = true
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            guard let view = view, view.window != nil else {
                view?.toOwner { $0.form.menuOpen = false }
                return
            }
            WidgetRendererEnvironment.present(items, anchor: anchor, view: view, event: event) { [weak view] choice in
                let option = choice.flatMap { $0 >= 0 && $0 < options.count ? options[$0] : nil }
                view?.toOwner { owner in
                    owner.form.menuOpen = false
                    if let option = option, option >= 0 {
                        owner.chooseOption(selectId: id, index: option)
                    }
                }
            }
        }
    }

    func keyTarget() -> Int? {
        guard form.focusedSelect != 0, let node = dom.node(form.focusedSelect), dom.isAttached(node) else {
            form.focusedSelect = 0
            return nil
        }
        return form.focusedSelect
    }

    func selectKey(_ type: String, key: String, event: WidgetKeyEvent) {
        guard type == "keydown", key == " " || key == "Enter", !event.isARepeat, let id = keyTarget(), let node = dom.node(id), !frozen, !destroyed else {
            return
        }
        if prevented.contains("\(id):keydown:\(key)") {
            return
        }
        openSelect(node, event: event.menuEvent)
    }

    func chooseOption(selectId id: Int, index: Int) {
        guard !frozen, !destroyed, let node = dom.node(id), node.tag.lowercased() == "select", node.attribute("disabled") == nil else {
            return
        }
        let options = WidgetDOM.options(of: node)
        guard index >= 0, index < options.count, options[index].attribute("disabled") == nil, options[index].parent.map({ $0.tag.lowercased() != "optgroup" || $0.attribute("disabled") == nil }) ?? true else {
            return
        }
        guard WidgetDOM.selectedIndex(of: node) != index else {
            return
        }
        dom.setFormProperty(node, "selectedIndex", NSNumber(value: index))
        setNeedsRender()
        let value = options[index].attribute("value") ?? WidgetDOM.textContent(options[index])
        postForm("input", id, ["selectedIndex": index, "value": value])
        postForm("change", id, ["selectedIndex": index, "value": value])
    }

    private func currentValue(_ node: WidgetNode) -> String {
        if let value = node.formValue {
            return value
        }
        if node.tag.lowercased() == "textarea" {
            return node.children.filter { $0.isText }.map { $0.text }.joined()
        }
        return node.attribute("value") ?? ""
    }

    func focusControl(_ node: WidgetNode, pointer: (CGPoint, Int)? = nil) {
        guard isEditable(node), !frozen, !destroyed, isOnScreen else {
            return
        }
        let id = dom.publicId(node)
        if form.focusedId == id {
            form.generation += 1
            if let spec = editorSpec(node, id: id) {
                let view = documentView
                DispatchQueue.main.async { [weak view] in
                    view?.beginEditing(spec, pointer: pointer)
                }
            }
            return
        }
        endEditing()
        form.focusedSelect = 0
        form.generation += 1
        form.focusedId = id
        form.initialValue = currentValue(node)
        dom.setEditing(node, true)
        setNeedsRender()
        postForm("focus", id)
        postForm("focusin", id)
        guard let spec = editorSpec(node, id: id) else {
            return
        }
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            view?.beginEditing(spec, pointer: pointer)
        }
    }

    func scriptFocus(_ id: Int) {
        guard let node = dom.node(id), CACurrentMediaTime() - form.lastActivation < LiteHTMLWidgetRenderer.scriptFocusWindow else {
            return
        }
        if node.tag.lowercased() == "select", node.attribute("disabled") == nil {
            endEditing()
            form.focusedSelect = id
            return
        }
        focusControl(node)
    }

    func endEditing() {
        guard form.focusedId != 0 else {
            return
        }
        let id = form.focusedId
        form.focusedId = 0
        form.generation += 1
        if let node = dom.node(id) {
            dom.setEditing(node, false)
            if !destroyed {
                setNeedsRender()
            }
            let value = currentValue(node)
            if value != form.initialValue {
                postForm("change", id, ["value": value])
            }
            postForm("blur", id)
            postForm("focusout", id)
        }
        let view = documentView
        if Thread.isMainThread {
            view.endEditing()
        } else {
            DispatchQueue.main.async { [weak view] in
                view?.endEditing()
            }
        }
    }

    func editorChanged(generation: Int, text: String, inputType: String? = nil) {
        guard generation == form.generation, form.focusedId != 0, let node = dom.node(form.focusedId) else {
            return
        }
        form.lastActivation = CACurrentMediaTime()
        let previous = currentValue(node)
        guard previous != text else {
            return
        }
        if node.tag.lowercased() == "textarea" {
            dom.setFormProperty(node, "value", text)
            setNeedsRender()
        } else {
            node.formValue = WidgetDOM.clip(text)
        }
        let inserted = text.count > previous.count && text.hasPrefix(previous) ? String(text.dropFirst(previous.count)) : nil
        var extra: [String: Any] = ["value": node.formValue ?? "", "inputType": inputType ?? (text.count < previous.count ? "deleteContentBackward" : "insertText")]
        extra["data"] = inserted ?? NSNull()
        postForm("input", form.focusedId, extra)
    }

    func editorCommand(generation: Int, command: String) {
        guard generation == form.generation, form.focusedId != 0, let node = dom.node(form.focusedId) else {
            return
        }
        let id = form.focusedId
        form.lastActivation = CACurrentMediaTime()
        switch command {
        case "Enter":
            for type in ["keydown", "keypress", "keyup"] {
                postForm(type, id, ["key": "Enter", "code": "Enter", "repeat": false, "modifiers": 0])
            }
            guard !prevented.contains("\(id):keydown:Enter"), !prevented.contains("\(id):keypress:Enter") else {
                return
            }
            var walk = node.parent
            while let parent = walk {
                if parent.tag.lowercased() == "form" {
                    postForm("submit", dom.publicId(parent))
                    return
                }
                walk = parent.parent
            }
        case "Escape":
            postForm("keydown", id, ["key": "Escape", "code": "Escape", "repeat": false, "modifiers": 0])
            endEditing()
        case "Tab", "ShiftTab":
            var editable: [WidgetNode] = []
            func collect(_ parent: WidgetNode) {
                for child in parent.children where !child.isText {
                    if isEditable(child) {
                        editable.append(child)
                    }
                    collect(child)
                }
            }
            collect(dom.body)
            guard let index = editable.firstIndex(where: { $0 === node }), editable.count > 1 else {
                endEditing()
                return
            }
            let next = editable[(index + (command == "Tab" ? 1 : editable.count - 1)) % editable.count]
            focusControl(next)
        default:
            break
        }
    }

    func editorEnter(generation: Int, token: Int, modifiers: Int) {
        guard generation == form.generation, !frozen, !destroyed, let node = dom.node(form.focusedId), node.tag.lowercased() == "textarea" else {
            return
        }
        form.lastActivation = CACurrentMediaTime()
        post(["type": "event", "ev": ["type": "keydown", "targetId": form.focusedId, "key": "Enter", "code": "Enter", "repeat": false, "modifiers": modifiers, "value": currentValue(node), "editorGeneration": generation, "editorCommand": token]])
    }

    func editorEnterCompleted(generation: Int, modifiers: Int) {
        guard generation == form.generation, form.focusedId != 0 else { return }
        postForm("keyup", form.focusedId, ["key": "Enter", "code": "Enter", "repeat": false, "modifiers": modifiers])
    }

    func completeEditorCommand(_ node: WidgetNode, _ args: [Any]) {
        guard dom.publicId(node) == form.focusedId, args.count == 3,
              let generation = args[0] as? NSNumber, generation.intValue == form.generation,
              let token = args[1] as? NSNumber, let allowed = args[2] as? NSNumber else { return }
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            guard let editor = view?.formEditor, editor.generation == generation.intValue else { return }
            editor.completeEnter(token: token.intValue, allowed: allowed.boolValue)
        }
    }

    func editorScrolled(generation: Int, offset: CGPoint) {
        guard generation == form.generation, form.focusedId != 0,
              dom.node(form.focusedId)?.tag.lowercased() == "textarea" else { return }
        if scrolling.rememberEditorOffset(form.focusedId, offset, document: document) {
            scrolled(form.focusedId)
        }
    }

    func editorEnded(generation: Int) {
        guard generation == form.generation else {
            return
        }
        endEditing()
    }

    func formValueSet(_ node: WidgetNode) {
        guard form.focusedId != 0, dom.publicId(node) == form.focusedId else {
            return
        }
        let text = currentValue(node)
        let generation = form.generation
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            view?.setEditorText(text, generation: generation)
        }
    }

    func syncEditor() {
        guard form.focusedId != 0 else {
            return
        }
        guard let node = dom.node(form.focusedId), dom.isAttached(node), isEditable(node), isOnScreen else {
            endEditing()
            return
        }
        guard let spec = editorSpec(node, id: form.focusedId) else {
            return
        }
        let view = documentView
        DispatchQueue.main.async { [weak view] in
            view?.placeEditor(spec)
        }
    }

    func visualPlacement(_ node: WidgetNode, _ rect: CGRect) -> (CGRect, CGRect?) {
        var scrollers: [Int] = []
        var walk = node.parent
        while let parent = walk {
            let id = dom.publicId(parent)
            if scrolling.geometries[id] != nil {
                scrollers.append(id)
            }
            walk = parent.parent
        }
        guard !scrollers.isEmpty else {
            return (rect, nil)
        }
        var clip: CGRect?
        for id in scrollers {
            if let viewport = scrolling.geometries[id]?.viewport {
                clip = clip.map { $0.intersection(viewport) } ?? viewport
            }
        }
        return (rect, clip)
    }

    private func editorSpec(_ node: WidgetNode, id: Int) -> WidgetEditorSpec? {
        guard let document = document, let laid = elementBounds()[id], laid.width > 0, laid.height > 0 else {
            return nil
        }
        let (box, clip) = visualPlacement(node, laid)
        let style = document.computedStyle(attribute: WidgetDOM.idAttribute, value: String(id))
        func px(_ name: String) -> CGFloat {
            guard let value = style[name], let number = Double(value.replacingOccurrences(of: "px", with: "")), number.isFinite else {
                return 0
            }
            return CGFloat(number)
        }
        let size = max(1, px("font-size"))
        var lineHeight: CGFloat = 0
        var baseline: CGFloat = 0
        let font = document.font(attribute: WidgetDOM.idAttribute, value: String(id), lineHeight: &lineHeight, baseline: &baseline) ?? NSFont.systemFont(ofSize: size)
        let multiline = node.tag.lowercased() == "textarea"
        let type = (node.attribute("type") ?? "").lowercased()
        var content = CGRect(x: box.minX + px("padding-left") + px("border-left-width"), y: box.minY + px("padding-top") + px("border-top-width"), width: box.width - px("padding-left") - px("padding-right") - px("border-left-width") - px("border-right-width"), height: box.height - px("padding-top") - px("padding-bottom") - px("border-top-width") - px("border-bottom-width"))
        if type == "number" {
            content.size.width -= 10
        }
        if !multiline {
            let line = ceil(font.ascender - font.descender + font.leading)
            content.origin.y += (content.height - line) / 2
            content.size.height = line
        }
        let alignment: NSTextAlignment
        switch style["text-align"] ?? "" {
        case "center":
            alignment = .center
        case "right", "end":
            alignment = .right
        default:
            alignment = .left
        }
        let color = WidgetCSSColor.parse(style["color"] ?? "") ?? .textColor
        return WidgetEditorSpec(generation: form.generation, multiline: multiline, frame: content.integral.width > 0 ? content : box, clip: clip, font: font, lineHeight: lineHeight, baseline: baseline, color: color, alignment: alignment, text: currentValue(node), placeholder: node.attribute("placeholder") ?? "", maxLength: Int(node.attribute("maxlength") ?? "") ?? 0, readOnly: node.attribute("readonly") != nil, numeric: type == "number" || ["numeric", "decimal"].contains((node.attribute("inputmode") ?? "").lowercased()), scrollOffset: scrolling.offset(id))
    }
}

enum WidgetCSSColor {
    static func parse(_ text: String) -> NSColor? {
        guard let open = text.firstIndex(of: "("), let close = text.lastIndex(of: ")") else {
            return nil
        }
        let parts = text[text.index(after: open)..<close].split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
        guard parts.count >= 3 else {
            return nil
        }
        return NSColor(srgbRed: CGFloat(parts[0]) / 255, green: CGFloat(parts[1]) / 255, blue: CGFloat(parts[2]) / 255, alpha: parts.count > 3 ? CGFloat(parts[3]) : 1)
    }
}

final class WidgetTextFieldCell: NSTextFieldCell {
    override func setUpFieldEditorAttributes(_ textObj: NSText) -> NSText {
        let editor = super.setUpFieldEditorAttributes(textObj)
        editor.drawsBackground = false
        editor.backgroundColor = .clear
        if let textView = editor as? NSTextView {
            textView.insertionPointColor = textColor ?? .textColor
            if #available(macOS 10.14, *) {
                textView.usesAdaptiveColorMappingForDarkAppearance = false
            }
        }
        return editor
    }
}

final class WidgetTextClipView: NSClipView {
    override func constrainBoundsRect(_ proposedBounds: NSRect) -> NSRect {
        guard let text = documentView as? NSTextView else { return super.constrainBoundsRect(proposedBounds) }
        var height = text.frame.height
        if let layout = text.layoutManager, let container = text.textContainer,
           layout.firstUnlaidCharacterIndex() >= text.string.utf16.count {
            height = max(layout.usedRect(for: container).maxY, layout.extraLineFragmentRect.maxY)
        }
        var bounds = proposedBounds
        bounds.origin.x = min(max(0, bounds.origin.x), max(0, text.frame.width - bounds.width))
        bounds.origin.y = min(max(0, bounds.origin.y), max(0, height - bounds.height))
        return bounds
    }
}

final class WidgetEditorTextView: NSTextView {
    static let maxQueuedKeys = 256
    var waitingForCommand = false
    private(set) var keyModifiers: NSEvent.ModifierFlags?
    private var queuedKeys: [NSEvent] = []

    override func keyDown(with event: NSEvent) {
        if waitingForCommand {
            if queuedKeys.count < WidgetEditorTextView.maxQueuedKeys {
                queuedKeys.append(event)
            }
        } else {
            keyModifiers = event.modifierFlags
            defer { keyModifiers = nil }
            super.keyDown(with: event)
        }
    }

    func resumeKeys() {
        while !waitingForCommand, !queuedKeys.isEmpty, window != nil {
            let event = queuedKeys.removeFirst()
            keyDown(with: event)
        }
    }
}

final class WidgetTextEditor: NSObject, NSTextFieldDelegate, NSTextViewDelegate, NSLayoutManagerDelegate {
    static var enterReplyTimeout: TimeInterval = 0.25
    private struct TextStyle: Equatable {
        let font: NSFont
        let lineHeight: CGFloat
        let baseline: CGFloat
        let color: NSColor
        let alignment: NSTextAlignment
    }

    private var textStyle: TextStyle?
    let generation: Int
    let view: NSView
    let container = WidgetEditorClip()
    let field: NSTextField?
    let textView: NSTextView?
    private let maxLength: Int
    private let numeric: Bool
    private var lastText: String
    private var removing = false
    private var nextCommand = 0
    private var pendingEnter: (token: Int, modifiers: Int)?
    private var insertingLineBreak = false
    private var settingScrollOffset = false
    private var pointerSelection: NSRange?
    private var pointerGranularity: NSSelectionGranularity = .selectByCharacter
    var onChange: ((String, String?) -> Void)?
    var onCommand: ((String) -> Void)?
    var onEnter: ((Int, Int) -> Void)?
    var onEnterCompleted: ((Int) -> Void)?
    var onEnd: (() -> Void)?
    var onScroll: ((CGPoint) -> Void)?

    init(_ spec: WidgetEditorSpec) {
        generation = spec.generation
        maxLength = spec.maxLength
        numeric = spec.numeric
        lastText = spec.text
        if spec.multiline {
            let scroll = NSScrollView(frame: spec.frame)
            scroll.contentView = WidgetTextClipView(frame: scroll.bounds)
            scroll.drawsBackground = false
            scroll.hasVerticalScroller = false
            scroll.hasHorizontalScroller = false
            scroll.borderType = .noBorder
            let text = WidgetEditorTextView(frame: CGRect(origin: .zero, size: spec.frame.size))
            text.drawsBackground = false
            text.backgroundColor = .clear
            if #available(macOS 10.14, *) {
                text.usesAdaptiveColorMappingForDarkAppearance = false
            }
            text.isRichText = false
            text.allowsUndo = true
            text.font = spec.font
            text.textColor = spec.color
            text.insertionPointColor = spec.color
            text.alignment = spec.alignment
            text.textContainerInset = .zero
            text.textContainer?.lineFragmentPadding = 0
            text.textContainer?.widthTracksTextView = true
            text.isVerticallyResizable = true
            text.autoresizingMask = [.width]
            text.isEditable = !spec.readOnly
            text.string = spec.text
            text.isAutomaticQuoteSubstitutionEnabled = false
            text.isAutomaticDashSubstitutionEnabled = false
            scroll.documentView = text
            textView = text
            field = nil
            view = scroll
        } else {
            let input = NSTextField(frame: spec.frame)
            input.cell = WidgetTextFieldCell(textCell: "")
            input.isBordered = false
            input.isBezeled = false
            input.drawsBackground = false
            input.backgroundColor = .clear
            input.focusRingType = .none
            input.font = spec.font
            input.textColor = spec.color
            input.alignment = spec.alignment
            input.isEditable = !spec.readOnly
            input.isSelectable = true
            input.usesSingleLineMode = true
            input.cell?.wraps = false
            input.cell?.isScrollable = true
            input.stringValue = spec.text
            if !spec.placeholder.isEmpty {
                input.placeholderAttributedString = NSAttributedString(string: spec.placeholder, attributes: [.font: spec.font, .foregroundColor: NSColor(srgbRed: 0xa9 / 255.0, green: 0xa9 / 255.0, blue: 0xa9 / 255.0, alpha: 1)])
            }
            field = input
            textView = nil
            view = input
        }
        super.init()
        field?.delegate = self
        textView?.delegate = self
        textView?.layoutManager?.delegate = self
        applyStyle(spec)
        container.addSubview(view)
        place(spec.frame, clip: spec.clip)
        if let scroll = view as? NSScrollView {
            scroll.contentView.postsBoundsChangedNotifications = true
            NotificationCenter.default.addObserver(self, selector: #selector(scrollBoundsChanged), name: NSView.boundsDidChangeNotification, object: scroll.contentView)
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func scrollBoundsChanged() {
        guard !removing, !settingScrollOffset, let scroll = view as? NSScrollView else { return }
        onScroll?(scroll.contentView.bounds.origin)
    }

    func setScrollOffset(_ offset: CGPoint) {
        guard let scroll = view as? NSScrollView, let text = textView else { return }
        settingScrollOffset = true
        defer { settingScrollOffset = false }
        if let layout = text.layoutManager, let container = text.textContainer {
            layout.ensureLayout(for: container)
            let height = max(scroll.contentView.bounds.height, max(layout.usedRect(for: container).maxY, layout.extraLineFragmentRect.maxY))
            text.frame = CGRect(origin: .zero, size: CGSize(width: scroll.contentView.bounds.width, height: height))
        }
        scroll.contentView.scroll(to: offset)
        scroll.reflectScrolledClipView(scroll.contentView)
    }

    private var selectionView: NSTextView? {
        return textView ?? field?.currentEditor() as? NSTextView
    }

    func beginSelection(at point: CGPoint, from parent: NSView, clicks: Int) {
        guard let text = selectionView else { return }
        let index = text.characterIndexForInsertion(at: text.convert(point, from: parent))
        pointerGranularity = clicks >= 3 ? .selectByParagraph : clicks == 2 ? .selectByWord : .selectByCharacter
        let range = text.selectionRange(forProposedRange: NSRange(location: index, length: 0), granularity: pointerGranularity)
        pointerSelection = range
        text.setSelectedRange(range)
    }

    func dragSelection(to point: CGPoint, from parent: NSView, event: NSEvent) {
        guard let anchor = pointerSelection, let text = selectionView else { return }
        _ = text.autoscroll(with: event)
        let index = text.characterIndexForInsertion(at: text.convert(point, from: parent))
        let start = min(anchor.location, index)
        let end = max(NSMaxRange(anchor), index)
        let range = text.selectionRange(forProposedRange: NSRange(location: start, length: end - start), granularity: pointerGranularity)
        text.setSelectedRange(range)
    }

    func finishSelection() {
        pointerSelection = nil
    }

    var responder: NSResponder? {
        return textView ?? field
    }

    func applyStyle(_ spec: WidgetEditorSpec) {
        textView?.isEditable = !spec.readOnly
        field?.isEditable = !spec.readOnly
        let style = TextStyle(font: spec.font, lineHeight: spec.lineHeight, baseline: spec.baseline, color: spec.color, alignment: spec.alignment)
        guard style != textStyle else { return }
        textStyle = style
        if let text = textView {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = spec.alignment
            paragraph.minimumLineHeight = spec.lineHeight
            paragraph.maximumLineHeight = spec.lineHeight
            text.font = spec.font
            text.textColor = spec.color
            text.insertionPointColor = spec.color
            text.defaultParagraphStyle = paragraph
            text.textStorage?.addAttribute(.paragraphStyle, value: paragraph, range: NSRange(location: 0, length: text.string.utf16.count))
            var attributes = text.typingAttributes
            attributes[.font] = spec.font
            attributes[.foregroundColor] = spec.color
            attributes[.paragraphStyle] = paragraph
            text.typingAttributes = attributes
        } else if let field = field {
            field.font = spec.font
            field.textColor = spec.color
            field.alignment = spec.alignment
            if let text = field.currentEditor() as? NSTextView {
                text.font = spec.font
                text.textColor = spec.color
                text.insertionPointColor = spec.color
                text.alignment = spec.alignment
            }
        }
    }

    func layoutManager(_ layoutManager: NSLayoutManager, shouldSetLineFragmentRect lineFragmentRect: UnsafeMutablePointer<NSRect>, lineFragmentUsedRect: UnsafeMutablePointer<NSRect>, baselineOffset: UnsafeMutablePointer<CGFloat>, in textContainer: NSTextContainer, forGlyphRange glyphRange: NSRange) -> Bool {
        guard let style = textStyle else { return false }
        baselineOffset.pointee = style.baseline
        return true
    }

    func place(_ frame: CGRect, clip: CGRect?) {
        let editorFrame = field != nil ? frame.insetBy(dx: -2, dy: 0) : frame
        if let clip = clip {
            container.frame = clip
            container.isHidden = clip.intersection(frame).isEmpty
        } else {
            container.frame = editorFrame
            container.isHidden = false
        }
        view.frame = editorFrame.offsetBy(dx: -container.frame.minX, dy: -container.frame.minY)
    }

    func setText(_ text: String) {
        guard text != current else {
            return
        }
        lastText = text
        if let field = field {
            field.stringValue = text
        } else {
            textView?.string = text
            textView?.setSelectedRange(NSRange(location: text.utf16.count, length: 0))
        }
    }

    func remove() {
        removing = true
        if let window = view.window, let responder = window.firstResponder as? NSView, responder === textView || responder.isDescendant(of: view) || (field?.currentEditor() != nil && responder === field?.currentEditor()) {
            window.makeFirstResponder(container.superview)
        }
        container.removeFromSuperview()
    }

    private var current: String {
        return field?.stringValue ?? textView?.string ?? ""
    }

    private var composing: Bool {
        if let textView = textView {
            return textView.hasMarkedText()
        }
        return (field?.currentEditor() as? NSTextView)?.hasMarkedText() ?? false
    }

    private func accepts(_ text: String) -> Bool {
        if maxLength > 0, text.utf16.count > maxLength {
            return false
        }
        if numeric, !text.allSatisfy({ "0123456789+-.,eE".contains($0) }) {
            return false
        }
        return true
    }

    private func changed() {
        guard !removing, !composing else {
            return
        }
        let text = current
        guard accepts(text) else {
            setText(lastText)
            return
        }
        guard text != lastText else {
            return
        }
        lastText = text
        onChange?(text, insertingLineBreak ? "insertLineBreak" : nil)
    }

    func controlTextDidChange(_ obj: Notification) {
        changed()
    }

    func textDidChange(_ notification: Notification) {
        changed()
    }

    func controlTextDidEndEditing(_ obj: Notification) {
        if !removing {
            onEnd?()
        }
    }

    func textDidEndEditing(_ notification: Notification) {
        if !removing {
            onEnd?()
        }
    }

    private func command(_ selector: Selector, multiline: Bool) -> Bool {
        switch selector {
        case #selector(NSResponder.insertNewline(_:)) where multiline:
            guard !composing, let text = textView as? WidgetEditorTextView, let onEnter = onEnter else { return false }
            let flags = text.keyModifiers ?? NSApp.currentEvent?.modifierFlags ?? []
            let modifiers = (flags.contains(.option) ? 1 : 0) | (flags.contains(.control) ? 2 : 0) | (flags.contains(.command) ? 4 : 0) | (flags.contains(.shift) ? 8 : 0)
            nextCommand += 1
            pendingEnter = (nextCommand, modifiers)
            text.waitingForCommand = true
            let token = nextCommand
            onEnter(token, modifiers)
            DispatchQueue.main.asyncAfter(deadline: .now() + WidgetTextEditor.enterReplyTimeout) { [weak self] in
                self?.completeEnter(token: token, allowed: true)
            }
            return true
        case #selector(NSResponder.insertNewline(_:)) where !multiline:
            onCommand?("Enter")
            return true
        case #selector(NSResponder.cancelOperation(_:)):
            onCommand?("Escape")
            return true
        case #selector(NSResponder.insertTab(_:)):
            onCommand?("Tab")
            return true
        case #selector(NSResponder.insertBacktab(_:)):
            onCommand?("ShiftTab")
            return true
        default:
            return false
        }
    }

    func completeEnter(token: Int, allowed: Bool) {
        guard !removing, let pending = pendingEnter, pending.token == token, let text = textView as? WidgetEditorTextView else { return }
        pendingEnter = nil
        text.waitingForCommand = false
        if allowed, text.isEditable {
            insertingLineBreak = true
            text.insertText("\n", replacementRange: text.selectedRange())
            insertingLineBreak = false
        }
        onEnterCompleted?(pending.modifiers)
        text.resumeKeys()
    }

    func control(_ control: NSControl, textView: NSTextView, doCommandBy commandSelector: Selector) -> Bool {
        return command(commandSelector, multiline: false)
    }

    func textView(_ textView: NSTextView, doCommandBy commandSelector: Selector) -> Bool {
        return command(commandSelector, multiline: true)
    }
}

extension WidgetDocumentView {
    func beginEditing(_ spec: WidgetEditorSpec, pointer: (CGPoint, Int)? = nil) {
        endEditing()
        guard owner != nil, window != nil else {
            toOwner { $0.editorEnded(generation: spec.generation) }
            return
        }
        let editor = WidgetTextEditor(spec)
        let generation = spec.generation
        editor.onChange = { [weak self] text, inputType in
            self?.toOwner { $0.editorChanged(generation: generation, text: text, inputType: inputType) }
        }
        editor.onCommand = { [weak self] command in
            self?.toOwner { $0.editorCommand(generation: generation, command: command) }
        }
        editor.onEnter = { [weak self] token, modifiers in
            self?.toOwner { $0.editorEnter(generation: generation, token: token, modifiers: modifiers) }
        }
        editor.onEnterCompleted = { [weak self] modifiers in
            self?.toOwner { $0.editorEnterCompleted(generation: generation, modifiers: modifiers) }
        }
        editor.onEnd = { [weak self] in
            DispatchQueue.main.async {
                self?.toOwner { $0.editorEnded(generation: generation) }
            }
        }
        formEditor = editor
        addSubview(editor.container)
        if let responder = editor.responder {
            window?.makeFirstResponder(responder)
        }
        if let field = editor.field, field.isSelectable, field.currentEditor() == nil {
            field.selectText(nil)
        }
        editor.setScrollOffset(spec.scrollOffset)
        editor.onScroll = { [weak self] offset in
            self?.toOwner { $0.editorScrolled(generation: generation, offset: offset) }
        }
        if let pointer = pointer {
            editor.beginSelection(at: pointer.0, from: self, clicks: pointer.1)
        }
    }

    func endEditing() {
        formEditor?.remove()
        formEditor = nil
    }

    func placeEditor(_ spec: WidgetEditorSpec) {
        guard let editor = formEditor, editor.generation == spec.generation else {
            return
        }
        editor.place(spec.frame, clip: spec.clip)
        editor.applyStyle(spec)
    }

    func setEditorText(_ text: String, generation: Int) {
        guard let editor = formEditor, editor.generation == generation else {
            return
        }
        editor.setText(text)
    }
}

final class WidgetEditorClip: NSView {
    override var isFlipped: Bool {
        return true
    }

    override func hitTest(_ point: NSPoint) -> NSView? {
        let target = super.hitTest(point)
        return target === self ? nil : target
    }

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.masksToBounds = true
    }

    required init?(coder: NSCoder) {
        return nil
    }
}

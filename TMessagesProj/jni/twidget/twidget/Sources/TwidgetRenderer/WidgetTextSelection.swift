import AppKit
import LiteHTML

final class WidgetTextSelection {
    struct Position: Comparable {
        let fragment: Int
        let offset: Int

        static func < (lhs: Position, rhs: Position) -> Bool {
            return lhs.fragment == rhs.fragment ? lhs.offset < rhs.offset : lhs.fragment < rhs.fragment
        }
    }

    let layer = CAShapeLayer()
    var color: NSColor = NSColor.selectedTextBackgroundColor {
        didSet { updateLayer() }
    }
    var onChange: ((Bool) -> Void)?

    private(set) var fragments: [LHTextFragment] = []
    private var anchor: Position?
    private var focus: Position?
    private var pressPoint: CGPoint?
    private var dragging = false
    private var reported = false

    init() {
        layer.actions = ["path": NSNull(), "bounds": NSNull(), "position": NSNull(), "fillColor": NSNull()]
        layer.zPosition = 10_000
        layer.anchorPoint = .zero
    }

    var isActive: Bool {
        return pressPoint != nil
    }

    var hasSelection: Bool {
        guard let (start, end) = ordered else {
            return false
        }
        return start != end
    }

    func press(at point: CGPoint, clickCount: Int, document: LHDocument?) -> Bool {
        clear(notify: false)
        guard let document = document else {
            notify()
            return false
        }
        let list = document.selectableTextFragments()
        guard let position = WidgetTextSelection.position(at: point, in: list, exact: true) else {
            notify()
            return false
        }
        fragments = list
        if clickCount >= 2 {
            let range = WidgetTextSelection.word(in: list[position.fragment].text as NSString, at: position.offset)
            anchor = Position(fragment: position.fragment, offset: range.location)
            focus = Position(fragment: position.fragment, offset: range.location + range.length)
            updateLayer()
            notify()
            return true
        }
        anchor = position
        focus = position
        pressPoint = point
        return true
    }

    func drag(to point: CGPoint) {
        guard let press = pressPoint, anchor != nil else {
            return
        }
        if !dragging, hypot(point.x - press.x, point.y - press.y) < 4 {
            return
        }
        dragging = true
        if let position = WidgetTextSelection.position(at: point, in: fragments, exact: false) {
            focus = position
            updateLayer()
        }
    }

    func release() {
        pressPoint = nil
        dragging = false
        if !hasSelection {
            clear(notify: false)
        }
        notify()
    }

    func clear(notify shouldNotify: Bool = true) {
        anchor = nil
        focus = nil
        pressPoint = nil
        dragging = false
        fragments = []
        layer.path = nil
        if shouldNotify {
            notify()
        }
    }

    func revalidate(_ document: LHDocument?) {
        guard hasSelection || isActive else {
            return
        }
        let list = document?.selectableTextFragments() ?? []
        let same = list.count == fragments.count && zip(list, fragments).allSatisfy { $0.text == $1.text && $0.frame.equalTo($1.frame) }
        if same {
            fragments = list
        } else {
            clear()
        }
    }

    var text: String? {
        guard let (start, end) = ordered, start != end else {
            return nil
        }
        var result = ""
        var previous: LHTextFragment?
        for index in start.fragment ... end.fragment {
            let fragment = fragments[index]
            let text = fragment.text as NSString
            let from = index == start.fragment ? start.offset : 0
            let to = index == end.fragment ? end.offset : text.length
            if to <= from {
                continue
            }
            if let previous = previous {
                if fragment.frame.minY >= previous.frame.maxY - 1 {
                    result += "\n"
                } else if fragment.frame.minX - previous.frame.maxX > 0.5 {
                    result += " "
                }
            }
            result += text.substring(with: NSRange(location: from, length: to - from))
            previous = fragment
        }
        return result.isEmpty ? nil : result
    }

    var rects: [CGRect] {
        guard let (start, end) = ordered, start != end else {
            return []
        }
        var rects: [CGRect] = []
        for index in start.fragment ... end.fragment {
            let fragment = fragments[index]
            let from = index == start.fragment ? start.offset : 0
            let to = index == end.fragment ? end.offset : (fragment.text as NSString).length
            if to <= from {
                continue
            }
            let minX = fragment.x(forCharacterIndex: UInt(from))
            let maxX = fragment.x(forCharacterIndex: UInt(to))
            let rect = CGRect(x: minX, y: fragment.frame.minY, width: maxX - minX, height: fragment.frame.height)
            if let previous = rects.last, abs(previous.midY - rect.midY) < 2, rect.minX > previous.maxX {
                rects[rects.count - 1].size.width = rect.minX - previous.minX
            }
            rects.append(rect)
        }
        return rects
    }

    private var ordered: (Position, Position)? {
        guard let anchor = anchor, let focus = focus, !fragments.isEmpty else {
            return nil
        }
        return anchor < focus ? (anchor, focus) : (focus, anchor)
    }

    private func notify() {
        let now = hasSelection
        if now != reported {
            reported = now
            onChange?(now)
        }
    }

    private func updateLayer() {
        let path = CGMutablePath()
        for rect in rects {
            path.addRect(rect)
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.fillColor = color.cgColor
        layer.path = path.isEmpty ? nil : path
        CATransaction.commit()
    }

    static func position(at point: CGPoint, in fragments: [LHTextFragment], exact: Bool) -> Position? {
        guard let last = fragments.last else {
            return nil
        }
        var line: [Int] = []
        for (index, fragment) in fragments.enumerated() {
            let frame = fragment.frame
            if point.y >= frame.minY && point.y <= frame.maxY {
                if point.x >= frame.minX && point.x <= frame.maxX {
                    return Position(fragment: index, offset: Int(fragment.characterIndex(atX: point.x)))
                }
                line.append(index)
            }
        }
        if exact {
            return nil
        }
        if !line.isEmpty {
            let before = line.filter { fragments[$0].frame.maxX <= point.x }
            if let index = before.max(by: { fragments[$0].frame.maxX < fragments[$1].frame.maxX }) {
                return Position(fragment: index, offset: (fragments[index].text as NSString).length)
            }
            if let index = line.min(by: { fragments[$0].frame.minX < fragments[$1].frame.minX }) {
                return Position(fragment: index, offset: 0)
            }
        }
        if let index = fragments.firstIndex(where: { $0.frame.minY > point.y }) {
            return Position(fragment: index, offset: 0)
        }
        return Position(fragment: fragments.count - 1, offset: (last.text as NSString).length)
    }

    static func word(in text: NSString, at offset: Int) -> NSRange {
        var found = NSRange(location: min(offset, text.length), length: 0)
        text.enumerateSubstrings(in: NSRange(location: 0, length: text.length), options: .byWords) { _, range, _, stop in
            if offset >= range.location && offset <= NSMaxRange(range) {
                found = range
                stop.pointee = true
            }
        }
        if found.length == 0 {
            return NSRange(location: 0, length: text.length)
        }
        return found
    }
}

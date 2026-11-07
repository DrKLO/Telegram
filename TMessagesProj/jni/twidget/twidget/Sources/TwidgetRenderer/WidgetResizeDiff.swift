import CoreGraphics

public struct WidgetResizeDiff: Equatable {
    public let oldHeight: CGFloat
    public let newHeight: CGFloat
    public let unchangedTop: CGFloat
    public let unchangedBottom: CGFloat
    public let pointerFrom: CGFloat?
    public let pointerTo: CGFloat?
    public let keepsBottom: Bool

    public init(oldHeight: CGFloat, newHeight: CGFloat, unchangedTop: CGFloat, unchangedBottom: CGFloat, pointerFrom: CGFloat? = nil, pointerTo: CGFloat? = nil, keepsBottom: Bool = false) {
        self.oldHeight = oldHeight
        self.newHeight = newHeight
        self.unchangedTop = unchangedTop
        self.unchangedBottom = unchangedBottom
        self.pointerFrom = pointerFrom
        self.pointerTo = pointerTo
        self.keepsBottom = keepsBottom
    }

    static func keepsBottom(oldHeight: CGFloat, newHeight: CGFloat, unchangedTop: CGFloat, unchangedBottom: CGFloat, pointerFrom: CGFloat?, pointerTo: CGFloat?, click: CGFloat?, prefersBottom: Bool, previous: Bool?) -> Bool {
        let delta = newHeight - oldHeight
        if let previous = previous, abs(delta) <= 1.5 || (unchangedTop >= oldHeight && unchangedBottom >= oldHeight) {
            return previous
        }
        if let from = pointerFrom, let to = pointerTo {
            let shift = to - from
            if let previous = previous, abs(shift) < 1, abs(shift - delta) < 1 {
                return previous
            }
            return abs(shift - delta) < abs(shift)
        }
        if let click = click.map({ min(max($0, 0), oldHeight) }) {
            if click <= unchangedTop {
                return false
            }
            if click >= oldHeight - unchangedBottom {
                return true
            }
            return click >= oldHeight / 2
        }
        return prefersBottom || unchangedBottom > unchangedTop
    }

    static let tolerance: CGFloat = 0.5

    public static func compute(old: [Int: CGRect], new: [Int: CGRect], oldHeight: CGFloat, newHeight: CGFloat, exactOldHeight: CGFloat? = nil, exactNewHeight: CGFloat? = nil, pointer: CGPoint? = nil, pointerTarget: Int? = nil, prefersBottom: Bool = false, previousKeepsBottom: Bool? = nil) -> WidgetResizeDiff {
        let reportedOldHeight = oldHeight
        let reportedNewHeight = newHeight
        let oldHeight = exactOldHeight ?? oldHeight
        let newHeight = exactNewHeight ?? newHeight
        var top = oldHeight
        var bottom = oldHeight
        func unstableTop(_ y: CGFloat) {
            top = min(top, max(y, 0))
        }
        func unstableBottom(_ distance: CGFloat) {
            bottom = min(bottom, max(distance, 0))
        }
        for (id, before) in old where !before.isEmpty {
            guard let after = new[id], !after.isEmpty else {
                unstableTop(before.minY)
                unstableBottom(oldHeight - before.maxY)
                continue
            }
            for (a, b) in [(before.minY, after.minY), (before.maxY, after.maxY)] {
                if abs(a - b) > tolerance {
                    unstableTop(min(a, b))
                }
                if abs((oldHeight - a) - (newHeight - b)) > tolerance {
                    unstableBottom(min(oldHeight - a, newHeight - b))
                }
            }
        }
        for (id, after) in new where !after.isEmpty && (old[id] == nil || old[id]!.isEmpty) {
            unstableTop(after.minY)
            unstableBottom(newHeight - after.maxY)
        }
        var pointerFrom: CGFloat?
        var pointerTo: CGFloat?
        if let pointer = pointer, let id = pointerTarget, let before = old[id], let after = new[id], !before.isEmpty, !after.isEmpty {
            let inset = min(max(pointer.y - before.minY, 0), min(before.height, after.height))
            pointerFrom = before.minY + inset
            pointerTo = after.minY + inset
        }
        let unchangedTop = min(top, oldHeight)
        let unchangedBottom = min(bottom, oldHeight)
        let keeps = keepsBottom(oldHeight: oldHeight, newHeight: newHeight, unchangedTop: unchangedTop, unchangedBottom: unchangedBottom, pointerFrom: pointerFrom, pointerTo: pointerTo, click: pointer?.y, prefersBottom: prefersBottom, previous: previousKeepsBottom)
        return WidgetResizeDiff(oldHeight: reportedOldHeight, newHeight: reportedNewHeight, unchangedTop: unchangedTop, unchangedBottom: unchangedBottom, pointerFrom: pointerFrom, pointerTo: pointerTo, keepsBottom: keeps)
    }
}

import CoreGraphics

extension CGContext {
    func twClip(using rule: CGPathFillRule = .winding) {
        if isPathEmpty {
            clip(to: .zero)
            return
        }
        clip(using: rule)
    }
}

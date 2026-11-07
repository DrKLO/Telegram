import CoreGraphics
import QuartzCore

public struct MagicMoveSpring: Equatable {
    public let mass: Double
    public let stiffness: Double
    public let damping: Double

    public static let standard = MagicMoveSpring(mass: 1, stiffness: 320, damping: 30)

    public init(mass: Double, stiffness: Double, damping: Double) {
        self.mass = mass
        self.stiffness = stiffness
        self.damping = damping
    }

    private var naturalFrequency: Double {
        return (stiffness / mass).squareRoot()
    }

    private var dampingRatio: Double {
        return damping / (2 * (stiffness * mass).squareRoot())
    }

    public func value(from: Double, to: Double, velocity: Double, at time: Double) -> Double {
        let state = offset(from - to, velocity: velocity, at: max(0, time))
        if abs(state.value) < MagicMoveSpring.restingOffset && abs(state.velocity) < MagicMoveSpring.restingVelocity {
            return to
        }
        return to + state.value
    }

    static let restingOffset = 0.001
    static let restingVelocity = 0.05

    public func velocity(from: Double, to: Double, velocity: Double, at time: Double) -> Double {
        return offset(from - to, velocity: velocity, at: max(0, time)).velocity
    }

    private func offset(_ start: Double, velocity: Double, at t: Double) -> (value: Double, velocity: Double) {
        let w0 = naturalFrequency
        let zeta = dampingRatio
        if zeta < 1 {
            let wd = w0 * (1 - zeta * zeta).squareRoot()
            let a = start
            let b = (zeta * w0 * a + velocity) / wd
            let decay = exp(-zeta * w0 * t)
            let c = cos(wd * t), s = sin(wd * t)
            let value = decay * (a * c + b * s)
            let rate = decay * ((b * wd - zeta * w0 * a) * c - (a * wd + zeta * w0 * b) * s)
            return (value, rate)
        }
        if zeta == 1 {
            let a = start
            let b = velocity + w0 * a
            let decay = exp(-w0 * t)
            return (decay * (a + b * t), decay * (b - w0 * (a + b * t)))
        }
        let root = w0 * (zeta * zeta - 1).squareRoot()
        let r1 = -zeta * w0 + root, r2 = -zeta * w0 - root
        let c2 = (velocity - r1 * start) / (r2 - r1)
        let c1 = start - c2
        return (c1 * exp(r1 * t) + c2 * exp(r2 * t), c1 * r1 * exp(r1 * t) + c2 * r2 * exp(r2 * t))
    }

    public func normalizedVelocity(_ velocity: Double, from: Double, to: Double) -> Double {
        let distance = to - from
        guard abs(distance) >= MagicMoveSpring.minimumDistance else {
            return 0
        }
        return velocity / distance
    }

    static let minimumDistance = 0.25
}

public struct MagicMoveMotion: Equatable {
    public let from: CGRect
    public let to: CGRect
    public let velocity: [Double]
    public let begin: CFTimeInterval
    public let spring: MagicMoveSpring

    public init(from: CGRect, to: CGRect, velocity: [Double] = [0, 0, 0, 0], begin: CFTimeInterval, spring: MagicMoveSpring = .standard) {
        self.from = from
        self.to = to
        self.velocity = velocity.count == 4 ? velocity : [0, 0, 0, 0]
        self.begin = begin
        self.spring = spring
    }

    static func components(_ rect: CGRect) -> [Double] {
        return [Double(rect.minX), Double(rect.minY), Double(rect.width), Double(rect.height)]
    }

    public func frame(at time: CFTimeInterval) -> CGRect {
        let a = MagicMoveMotion.components(from), b = MagicMoveMotion.components(to)
        let t = time - begin
        let v = (0..<4).map { spring.value(from: a[$0], to: b[$0], velocity: velocity[$0], at: t) }
        return CGRect(x: v[0], y: v[1], width: max(0, v[2]), height: max(0, v[3]))
    }

    public func rates(at time: CFTimeInterval) -> [Double] {
        let a = MagicMoveMotion.components(from), b = MagicMoveMotion.components(to)
        let t = time - begin
        return (0..<4).map { spring.velocity(from: a[$0], to: b[$0], velocity: velocity[$0], at: t) }
    }

    public func retargeted(from presented: CGRect, to target: CGRect, at time: CFTimeInterval, window: CFTimeInterval = MagicMoveMotion.matchWindow) -> MagicMoveMotion {
        guard MagicMoveMotion.matches(presented, before: time, since: begin, window: window, sample: frame(at:)) else {
            return MagicMoveMotion(from: presented, to: target, begin: time, spring: spring)
        }
        return MagicMoveMotion(from: frame(at: time), to: target, velocity: rates(at: time), begin: time, spring: spring)
    }

    public static let matchWindow: CFTimeInterval = 0.25

    static func matches(_ frame: CGRect, before time: CFTimeInterval, since begin: CFTimeInterval, window: CFTimeInterval, sample: (CFTimeInterval) -> CGRect) -> Bool {
        var moment = time
        let earliest = max(begin, time - window)
        while moment >= earliest - 0.0001 {
            if close(sample(moment), frame, tolerance: 1) {
                return true
            }
            moment -= 1.0 / 2000
        }
        return false
    }

    public func normalizedVelocities() -> [Double] {
        let a = MagicMoveMotion.components(from), b = MagicMoveMotion.components(to)
        return (0..<4).map { spring.normalizedVelocity(velocity[$0], from: a[$0], to: b[$0]) }
    }

    static func close(_ a: CGRect, _ b: CGRect, tolerance: CGFloat) -> Bool {
        return abs(a.minX - b.minX) <= tolerance && abs(a.minY - b.minY) <= tolerance && abs(a.width - b.width) <= tolerance && abs(a.height - b.height) <= tolerance
    }
}

public struct MagicMoveCorners: Equatable {
    public let topLeft: CGFloat
    public let topRight: CGFloat
    public let bottomRight: CGFloat
    public let bottomLeft: CGFloat

    public static let zero = MagicMoveCorners(topLeft: 0, topRight: 0, bottomRight: 0, bottomLeft: 0)

    public init(topLeft: CGFloat, topRight: CGFloat, bottomRight: CGFloat, bottomLeft: CGFloat) {
        self.topLeft = max(0, topLeft)
        self.topRight = max(0, topRight)
        self.bottomRight = max(0, bottomRight)
        self.bottomLeft = max(0, bottomLeft)
    }

    public init(radii: [CGFloat]) {
        guard radii.count == 8 else {
            self = .zero
            return
        }
        self.init(topLeft: min(radii[0], radii[1]), topRight: min(radii[2], radii[3]), bottomRight: min(radii[4], radii[5]), bottomLeft: min(radii[6], radii[7]))
    }

    var all: [CGFloat] {
        return [topLeft, topRight, bottomRight, bottomLeft]
    }

    public var clipRadius: CGFloat {
        return all.filter { $0 > 0 }.min() ?? 0
    }

    public var rounded: (topLeft: Bool, topRight: Bool, bottomRight: Bool, bottomLeft: Bool) {
        return (topLeft > 0, topRight > 0, bottomRight > 0, bottomLeft > 0)
    }
}

public struct MagicMoveInsets: Equatable {
    public var top: CGFloat
    public var left: CGFloat
    public var bottom: CGFloat
    public var right: CGFloat

    public static let zero = MagicMoveInsets(top: 0, left: 0, bottom: 0, right: 0)

    public init(top: CGFloat, left: CGFloat, bottom: CGFloat, right: CGFloat) {
        self.top = top
        self.left = left
        self.bottom = bottom
        self.right = right
    }

    public static func overhang(ink: CGRect, border: CGRect) -> MagicMoveInsets {
        func clean(_ value: CGFloat) -> CGFloat {
            return value > 0.01 ? value : 0
        }
        return MagicMoveInsets(top: clean(border.minY - ink.minY), left: clean(border.minX - ink.minX), bottom: clean(ink.maxY - border.maxY), right: clean(ink.maxX - border.maxX))
    }

    public func union(_ other: MagicMoveInsets) -> MagicMoveInsets {
        return MagicMoveInsets(top: max(top, other.top), left: max(left, other.left), bottom: max(bottom, other.bottom), right: max(right, other.right))
    }

    public var maximum: CGFloat {
        return max(top, left, bottom, right)
    }

    public var isZero: Bool {
        return top == 0 && left == 0 && bottom == 0 && right == 0
    }

    public func outset(_ rect: CGRect) -> CGRect {
        return CGRect(x: rect.minX - left, y: rect.minY - top, width: rect.width + left + right, height: rect.height + top + bottom)
    }

    public func inset(_ rect: CGRect) -> CGRect {
        return CGRect(x: rect.minX + left, y: rect.minY + top, width: max(0, rect.width - left - right), height: max(0, rect.height - top - bottom))
    }
}

public enum MagicMoveGeometry {
    public enum Kind: Equatable {
        case translate
        case resize
    }

    public static func kind(from: CGRect, to: CGRect, tolerance: CGFloat = 0.5) -> Kind {
        return abs(from.width - to.width) <= tolerance && abs(from.height - to.height) <= tolerance ? .translate : .resize
    }

    public static func aligned(_ rect: CGRect, to target: CGRect, scale: CGFloat) -> CGRect {
        guard scale > 0 else {
            return rect
        }
        func snap(_ value: CGFloat, _ anchor: CGFloat) -> CGFloat {
            return anchor + ((value - anchor) * scale).rounded() / scale
        }
        return CGRect(x: snap(rect.minX, target.minX), y: snap(rect.minY, target.minY), width: rect.width, height: rect.height)
    }

    public static func maskSlice(corners: MagicMoveCorners, size: CGSize, scale: CGFloat, stretches: (width: Bool, height: Bool)) -> CGSize? {
        let radius = corners.all.max() ?? 0
        guard scale > 0 else {
            return nil
        }
        let wanted = max(1, ceil(radius * scale)) / scale
        func axis(_ length: CGFloat, _ stretched: Bool) -> CGFloat? {
            if length >= wanted * 2 + 1 / scale {
                return wanted
            }
            if stretched {
                return nil
            }
            let fallback = floor((length * scale - 1) / 2) / scale
            return fallback > 0 ? fallback : nil
        }
        guard let width = axis(size.width, stretches.width), let height = axis(size.height, stretches.height) else {
            return nil
        }
        return CGSize(width: width, height: height)
    }

    public static func ninePatch(source: CGSize, slice: CGSize, destination: CGSize) -> [(source: CGRect, destination: CGRect)] {
        guard source.width > slice.width * 2, source.height > slice.height * 2, destination.width > 0, destination.height > 0 else {
            return []
        }
        func bands(_ length: CGFloat, _ slice: CGFloat, _ target: CGFloat) -> [(CGFloat, CGFloat, CGFloat, CGFloat)] {
            let edge = min(slice, target / 2)
            return [(0, slice, 0, edge), (slice, length - slice * 2, edge, target - edge * 2), (length - slice, slice, target - edge, edge)]
        }
        var patches: [(source: CGRect, destination: CGRect)] = []
        for row in bands(source.height, slice.height, destination.height) where row.3 > 0 && row.1 > 0 {
            for column in bands(source.width, slice.width, destination.width) where column.3 > 0 && column.1 > 0 {
                patches.append((CGRect(x: column.0, y: row.0, width: column.1, height: row.1), CGRect(x: column.2, y: row.2, width: column.3, height: row.3)))
            }
        }
        return patches
    }

    public static func pixelAligned(_ rect: CGRect, scale: CGFloat) -> CGRect {
        let minX = floor(rect.minX * scale + 0.001) / scale, minY = floor(rect.minY * scale + 0.001) / scale
        let maxX = ceil(rect.maxX * scale - 0.001) / scale, maxY = ceil(rect.maxY * scale - 0.001) / scale
        return CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
    }

    public static func sharesLeadingEdges(_ a: MagicMoveInsets, _ b: MagicMoveInsets) -> Bool {
        return abs(a.top - b.top) < 0.01 && abs(a.left - b.left) < 0.01
    }

    public static func contentOrigin(ink: CGRect, border: CGRect, overhang: MagicMoveInsets) -> CGPoint {
        return CGPoint(x: ink.minX - border.minX + overhang.left, y: ink.minY - border.minY + overhang.top)
    }

    public static func scaledAboutCenter(_ rect: CGRect, factor: CGFloat) -> CGRect {
        return scaled(rect, factor: factor, about: CGPoint(x: rect.midX, y: rect.midY))
    }

    public static func scaled(_ rect: CGRect, factor: CGFloat, about center: CGPoint) -> CGRect {
        return CGRect(x: center.x + (rect.minX - center.x) * factor, y: center.y + (rect.minY - center.y) * factor, width: rect.width * factor, height: rect.height * factor)
    }
}

public struct MagicMoveFade: Equatable {
    public let from: Float
    public let to: Float
    public let delay: CFTimeInterval
    public let duration: CFTimeInterval
    public let curve: [Double]

    public static let fadeIn = MagicMoveFade(from: 0, to: 1, delay: 0, duration: 0.24, curve: [0.45, 0, 0.55, 1])
    public static let fadeOut = MagicMoveFade(from: 1, to: 0, delay: 0.2, duration: 0.16, curve: [0.45, 0, 0.55, 1])
    public static let enter = MagicMoveFade(from: 0, to: 1, delay: 0, duration: 0.22, curve: [0.33, 1, 0.68, 1])
    public static let dissolveIn = MagicMoveFade(from: 0, to: 1, delay: 0, duration: 0.25, curve: [0.42, 0, 0.58, 1])
    public static let dissolveOut = MagicMoveFade(from: 1, to: 0, delay: 0.05, duration: 0.25, curve: [0.42, 0, 0.58, 1])
    public static let leave = MagicMoveFade(from: 1, to: 0, delay: 0, duration: 0.18, curve: [0.32, 0, 0.67, 0])

    public init(from: Float, to: Float, delay: CFTimeInterval, duration: CFTimeInterval, curve: [Double]) {
        self.from = from
        self.to = to
        self.delay = delay
        self.duration = duration
        self.curve = curve
    }

    public func starting(at value: Float) -> MagicMoveFade {
        return MagicMoveFade(from: value, to: to, delay: delay, duration: duration, curve: curve)
    }

    public func value(at elapsed: CFTimeInterval) -> Float {
        let local = elapsed - delay
        if local <= 0 {
            return from
        }
        if local >= duration {
            return to
        }
        let progress = MagicMoveFade.solve(curve, x: local / duration)
        return from + (to - from) * Float(progress)
    }

    static func solve(_ curve: [Double], x: Double) -> Double {
        guard curve.count == 4 else {
            return x
        }
        let cx = 3 * curve[0], bx = 3 * (curve[2] - curve[0]) - cx, ax = 1 - cx - bx
        let cy = 3 * curve[1], by = 3 * (curve[3] - curve[1]) - cy, ay = 1 - cy - by
        func sampleX(_ t: Double) -> Double { ((ax * t + bx) * t + cx) * t }
        func sampleY(_ t: Double) -> Double { ((ay * t + by) * t + cy) * t }
        func derivativeX(_ t: Double) -> Double { (3 * ax * t + 2 * bx) * t + cx }
        var t = x
        for _ in 0..<8 {
            let error = sampleX(t) - x
            if abs(error) < 1e-7 {
                return sampleY(t)
            }
            let d = derivativeX(t)
            if abs(d) < 1e-6 {
                break
            }
            t -= error / d
        }
        var low = 0.0, high = 1.0
        t = x
        for _ in 0..<40 {
            let value = sampleX(t)
            if abs(value - x) < 1e-7 {
                break
            }
            if value < x {
                low = t
            } else {
                high = t
            }
            t = (low + high) / 2
        }
        return sampleY(t)
    }
}

public struct MagicMoveFill: Equatable {
    public let from: [CGFloat]
    public let to: [CGFloat]
    public let fade: MagicMoveFade

    static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    public init?(from: [CGFloat]?, to: [CGFloat]?, fade: MagicMoveFade = .fadeIn) {
        guard from != nil || to != nil else {
            return nil
        }
        let start = from ?? to.map { [$0[0], $0[1], $0[2], 0] }!
        let end = to ?? [start[0], start[1], start[2], 0]
        self.from = start
        self.to = end
        self.fade = fade
    }

    public func components(at elapsed: CFTimeInterval) -> [CGFloat] {
        let progress = CGFloat(MagicMoveFade(from: 0, to: 1, delay: fade.delay, duration: fade.duration, curve: fade.curve).value(at: elapsed))
        return (0..<4).map { from[$0] + (to[$0] - from[$0]) * progress }
    }

    public func color(at elapsed: CFTimeInterval) -> CGColor? {
        return CGColor(colorSpace: MagicMoveFill.space, components: components(at: elapsed))
    }

    public static func samplePoints(border: CGRect, corners: MagicMoveCorners) -> [CGPoint] {
        let all = corners.all
        func inset(_ radius: CGFloat) -> CGFloat {
            return radius * 0.3 + 2
        }
        let points = [
            CGPoint(x: border.minX + inset(all[0]), y: border.minY + inset(all[0])),
            CGPoint(x: border.maxX - inset(all[1]), y: border.minY + inset(all[1])),
            CGPoint(x: border.maxX - inset(all[2]), y: border.maxY - inset(all[2])),
            CGPoint(x: border.minX + inset(all[3]), y: border.maxY - inset(all[3])),
        ]
        return points.filter { border.insetBy(dx: 0.5, dy: 0.5).contains($0) }
    }

    public static func sample(_ image: CGImage, ink: CGRect, border: CGRect, corners: MagicMoveCorners) -> [CGFloat]? {
        let points = samplePoints(border: border, corners: corners)
        guard points.count == 4, ink.width > 0, ink.height > 0 else {
            return nil
        }
        var pixels = [UInt8](repeating: 0, count: 4)
        var first: [UInt8]?
        let sx = CGFloat(image.width) / ink.width, sy = CGFloat(image.height) / ink.height
        for point in points {
            let px = floor((point.x - ink.minX) * sx), py = floor((point.y - ink.minY) * sy)
            guard px >= 0, py >= 0, px < CGFloat(image.width), py < CGFloat(image.height) else {
                return nil
            }
            let ok: Bool = pixels.withUnsafeMutableBytes { buffer in
                guard let ctx = CGContext(data: buffer.baseAddress, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
                    return false
                }
                ctx.setBlendMode(.copy)
                ctx.interpolationQuality = .none
                ctx.draw(image, in: CGRect(x: -px, y: py - CGFloat(image.height) + 1, width: CGFloat(image.width), height: CGFloat(image.height)))
                return true
            }
            guard ok, pixels[3] == 255 else {
                return nil
            }
            if let first = first {
                if zip(first, pixels).contains(where: { abs(Int($0) - Int($1)) > 2 }) {
                    return nil
                }
            } else {
                first = pixels
            }
        }
        guard let color = first else {
            return nil
        }
        return color.map { CGFloat($0) / 255 }
    }
}

import AppKit

public final class MagicMoveStage {
    public struct Snapshot {
        public let image: CGImage
        public let ink: CGRect
        public let border: CGRect
        public let corners: MagicMoveCorners

        public init(image: CGImage, ink: CGRect, border: CGRect) {
            self.init(image: image, ink: ink, border: border, corners: .zero)
        }

        public init(image: CGImage, ink: CGRect, border: CGRect, corners: MagicMoveCorners) {
            self.image = image
            self.ink = ink
            self.border = border
            self.corners = corners
        }

        var fillColor: [CGFloat]? {
            return MagicMoveFill.sample(image, ink: ink, border: border, corners: corners)
        }

        func flattened(onto components: [CGFloat]) -> Snapshot {
            guard components.count == 4, components[3] >= 1, let color = CGColor(colorSpace: MagicMoveFill.space, components: components), let ctx = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0, space: image.colorSpace ?? MagicMoveFill.space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
                return self
            }
            let full = CGRect(x: 0, y: 0, width: image.width, height: image.height)
            ctx.setFillColor(color)
            ctx.fill(full)
            ctx.draw(image, in: full)
            guard let flat = ctx.makeImage() else {
                return self
            }
            return Snapshot(image: flat, ink: ink, border: border, corners: corners)
        }

        func placed(at border: CGRect) -> Snapshot {
            let ink = self.ink.offsetBy(dx: border.minX - self.border.minX, dy: border.minY - self.border.minY)
            return Snapshot(image: image, ink: ink, border: border, corners: corners)
        }
    }

    public enum Item {
        case move(key: String, from: CGRect, to: Snapshot, previous: Snapshot?)
        case enter(key: String, Snapshot)
        case leave(Snapshot)
    }

    private final class Piece {
        let layer: CALayer
        let snapshot: Snapshot
        let origin: CGPoint
        let fade: MagicMoveFade?
        let begin: CFTimeInterval

        init(layer: CALayer, snapshot: Snapshot, origin: CGPoint, fade: MagicMoveFade?, begin: CFTimeInterval) {
            self.layer = layer
            self.snapshot = snapshot
            self.origin = origin
            self.fade = fade
            self.begin = begin
        }

        func opacity(at time: CFTimeInterval) -> Float {
            return fade?.value(at: time - begin) ?? 1
        }

        func settled(at time: CFTimeInterval) -> Bool {
            guard let fade = fade else {
                return true
            }
            return time - begin >= fade.delay + fade.duration
        }
    }

    private struct Mask {
        let image: CGImage
        let slice: CGSize
        let scale: CGFloat
        let previous: (image: CGImage, slice: CGSize)?
        let switchTime: CFTimeInterval

        func source(at elapsed: CFTimeInterval) -> (image: CGImage, slice: CGSize) {
            if let previous = previous, elapsed < switchTime {
                return previous
            }
            return (image, slice)
        }

        func image(at elapsed: CFTimeInterval, size: CGSize) -> CGImage? {
            let source = self.source(at: elapsed)
            return MagicMoveStage.maskImage(source.image, slice: source.slice, size: size, scale: scale)
        }
    }

    private final class Flight {
        let container: CALayer
        let motion: MagicMoveMotion
        let overhang: MagicMoveInsets
        let clips: Bool
        let fill: MagicMoveFill?
        var mask: Mask?
        var maskInsets: (from: MagicMoveInsets, to: MagicMoveInsets)?
        let corners: (from: MagicMoveCorners, to: MagicMoveCorners)
        let scale: (from: CGFloat, to: CGFloat)
        let pieces: [Piece]

        init(container: CALayer, motion: MagicMoveMotion, overhang: MagicMoveInsets, clips: Bool, fill: MagicMoveFill? = nil, corners: (from: MagicMoveCorners, to: MagicMoveCorners), scale: (from: CGFloat, to: CGFloat), pieces: [Piece]) {
            self.container = container
            self.motion = motion
            self.overhang = overhang
            self.clips = clips
            self.fill = fill
            self.corners = corners
            self.scale = scale
            self.pieces = pieces
        }

        func insets(at time: CFTimeInterval) -> MagicMoveInsets? {
            guard let insets = maskInsets else {
                return nil
            }
            if insets.from == insets.to {
                return insets.to
            }
            let remaining = CGFloat(motion.spring.value(from: 1, to: 0, velocity: 0, at: time - motion.begin))
            return MagicMoveInsets(top: insets.to.top + (insets.from.top - insets.to.top) * remaining, left: insets.to.left + (insets.from.left - insets.to.left) * remaining, bottom: insets.to.bottom + (insets.from.bottom - insets.to.bottom) * remaining, right: insets.to.right + (insets.from.right - insets.to.right) * remaining)
        }

        func outer(at time: CFTimeInterval) -> CGRect {
            let frame = motion.frame(at: time)
            let container = overhang.outset(frame)
            guard let insets = insets(at: time) else {
                return container
            }
            return container.union(insets.outset(frame))
        }

        func border(at time: CFTimeInterval) -> CGRect {
            let frame = motion.frame(at: time)
            let factor = scaleFactor(at: time)
            guard factor != 1 else {
                return frame
            }
            let outer = overhang.outset(frame)
            return MagicMoveGeometry.scaled(frame, factor: factor, about: CGPoint(x: outer.midX, y: outer.midY))
        }

        func scaleFactor(at time: CFTimeInterval) -> CGFloat {
            if scale.from == scale.to {
                return scale.to
            }
            return CGFloat(motion.spring.value(from: Double(scale.from), to: Double(scale.to), velocity: 0, at: time - motion.begin))
        }

        func radius(at time: CFTimeInterval) -> CGFloat {
            let a = corners.from.clipRadius, b = corners.to.clipRadius
            if a == b {
                return b
            }
            return max(0, CGFloat(motion.spring.value(from: Double(a), to: Double(b), velocity: 0, at: time - motion.begin)))
        }

        func matches(_ frame: CGRect, before time: CFTimeInterval) -> Bool {
            return MagicMoveMotion.matches(frame, before: time, since: motion.begin, window: MagicMoveMotion.matchWindow, sample: border(at:))
        }

        func singleOpaquePiece(at time: CFTimeInterval) -> Piece? {
            guard pieces.count == 1, let piece = pieces.first, piece.settled(at: time), piece.opacity(at: time) >= 1, scaleFactor(at: time) == 1, !clips else {
                return nil
            }
            return piece
        }
    }

    private final class Leaving {
        let layer: CALayer
        let piece: Piece
        let spring: MagicMoveSpring
        let shrinks: Bool

        init(layer: CALayer, piece: Piece, spring: MagicMoveSpring, shrinks: Bool) {
            self.layer = layer
            self.piece = piece
            self.spring = spring
            self.shrinks = shrinks
        }

        func scaleFactor(at time: CFTimeInterval) -> CGFloat {
            guard shrinks else {
                return 1
            }
            return CGFloat(spring.value(from: 1, to: Double(MagicMoveStage.enterScale), velocity: 0, at: time - piece.begin))
        }

        func finished(at time: CFTimeInterval) -> Bool {
            return time - piece.begin >= MagicMoveStage.duration
        }
    }

    public static let duration: CFTimeInterval = 0.55
    public static let maxLayers = 48
    public static let maxPixels = 4096 * 4096
    public static let spring = MagicMoveSpring.standard
    public static let timing: [String: Double] = ["duration": duration, "mass": spring.mass, "stiffness": spring.stiffness, "damping": spring.damping]
    static let enterScale: CGFloat = 0.92

    private weak var host: CALayer?
    private var flights: [String: Flight] = [:]
    private var order: [String] = []
    private var leaving: [Leaving] = []
    private var retired: (time: CFTimeInterval, flights: [String: Flight])?
    private var generation = 0
    public var onFinish: (() -> Void)?
    public var clock: () -> CFTimeInterval = CACurrentMediaTime
    public var reducesMotion: () -> Bool = { NSWorkspace.shared.accessibilityDisplayShouldReduceMotion }

    public init(host: CALayer?) {
        self.host = host
    }

    public var isActive: Bool {
        return !flights.isEmpty || !leaving.isEmpty
    }

    public func isInFlight(_ key: String) -> Bool {
        return flights[key] != nil
    }

    public var layerCount: Int {
        return flights.values.reduce(0) { $0 + 1 + $1.pieces.count + ($1.mask != nil ? 2 : 0) } + leaving.count
    }

    public func presentedFrames() -> [String: CGRect] {
        return presentedFramesAt(clock())
    }

    func presentedFramesAt(_ now: CFTimeInterval) -> [String: CGRect] {
        var frames: [String: CGRect] = [:]
        for (key, flight) in flights {
            frames[key] = flight.border(at: now)
        }
        return frames
    }

    public func presentedSnapshots(scale: CGFloat) -> [String: Snapshot] {
        let now = clock()
        var snapshots: [String: Snapshot] = [:]
        for (key, flight) in flights {
            if let snapshot = presentedSnapshot(flight, at: now, scale: scale) {
                snapshots[key] = snapshot
            }
        }
        return snapshots
    }

    public func presentedSnapshot(for key: String, scale: CGFloat) -> Snapshot? {
        guard let flight = flights[key] else {
            return nil
        }
        return presentedSnapshot(flight, at: clock(), scale: scale)
    }

    public func play(_ items: [Item], scale: CGFloat) {
        guard let host = host else {
            return
        }
        let dissolving = reducesMotion()
        let now = clock()
        generation += 1
        let current = generation
        let previousFlights = flights
        let recent = retired.flatMap { now - $0.time <= MagicMoveMotion.matchWindow ? $0.flights : nil } ?? [:]
        retired = nil
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        leaving = leaving.filter { item in
            if item.finished(at: now) {
                item.layer.twRemoveFromSuperlayer()
                return false
            }
            return true
        }
        flights = [:]
        order = []
        var built: [(String, Flight)] = []
        for item in items {
            switch item {
            case let .move(key, from, to, previous) where dissolving:
                let presented = (previousFlights[key] ?? recent[key]).flatMap { presentedSnapshot($0, at: now, scale: scale) }
                if let item = makeLeave(presented ?? previous?.placed(at: from) ?? to.placed(at: from), scale: scale, now: now, dissolve: true) {
                    host.twAddSublayer(item.layer)
                    leaving.append(item)
                }
                if let flight = makeEnter(to, scale: scale, now: now, dissolve: true) {
                    built.append((key, flight))
                }
            case let .move(key, from, to, previous):
                var outgoing = previous
                var motion = MagicMoveMotion(from: MagicMoveGeometry.aligned(from, to: to.border, scale: scale), to: to.border, begin: now, spring: MagicMoveStage.spring)
                if let prior = previousFlights[key] ?? recent[key], prior.matches(from, before: now) {
                    if let piece = prior.singleOpaquePiece(at: now) {
                        outgoing = MagicMoveStage.same(piece.snapshot.image, to.image) ? nil : piece.snapshot.placed(at: prior.border(at: now))
                    } else {
                        outgoing = presentedSnapshot(prior, at: now, scale: scale)
                    }
                    motion = MagicMoveMotion(from: prior.border(at: now), to: to.border, velocity: prior.scale.from == prior.scale.to ? prior.motion.rates(at: now) : [0, 0, 0, 0], begin: now, spring: MagicMoveStage.spring)
                }
                if let flight = makeMove(motion: motion, to: to, outgoing: outgoing, scale: scale, now: now) {
                    built.append((key, flight))
                }
            case let .enter(key, snapshot):
                if let flight = makeEnter(snapshot, scale: scale, now: now, dissolve: dissolving) {
                    built.append((key, flight))
                }
            case let .leave(snapshot):
                if let item = makeLeave(snapshot, scale: scale, now: now, dissolve: dissolving) {
                    host.twAddSublayer(item.layer)
                    leaving.append(item)
                }
            }
        }
        for flight in previousFlights.values {
            flight.container.twRemoveFromSuperlayer()
        }
        for (key, flight) in built {
            host.twAddSublayer(flight.container)
            flights[key] = flight
            order.append(key)
        }
        CATransaction.commit()
        DispatchQueue.main.asyncAfter(deadline: .now() + MagicMoveStage.duration) { [weak self] in
            guard let self = self, self.generation == current else {
                return
            }
            self.onFinish?()
        }
    }

    func freeze(at time: CFTimeInterval) {
        clock = { time }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for layer in flights.values.map({ $0.container }) + leaving.map({ $0.layer }) {
            layer.speed = 0
            layer.timeOffset = time
        }
        CATransaction.commit()
    }

    public func clear() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for flight in flights.values {
            flight.container.twRemoveFromSuperlayer()
        }
        for item in leaving {
            item.layer.twRemoveFromSuperlayer()
        }
        CATransaction.commit()
        flights.removeAll()
        order.removeAll()
        leaving.removeAll()
    }

    public func cancel() {
        generation += 1
        if !flights.isEmpty {
            let current = generation
            retired = (clock(), flights)
            DispatchQueue.main.asyncAfter(deadline: .now() + MagicMoveMotion.matchWindow) { [weak self] in
                if let self = self, self.generation == current {
                    self.retired = nil
                }
            }
        }
        clear()
    }

    public func draw(into ctx: CGContext) {
        draw(into: ctx, at: clock())
    }

    public func draw(into ctx: CGContext, at time: CFTimeInterval) {
        for item in leaving {
            let piece = item.piece
            let border = piece.snapshot.border
            let factor = item.scaleFactor(at: time)
            ctx.saveGState()
            if factor != 1 {
                ctx.translateBy(x: border.midX, y: border.midY)
                ctx.scaleBy(x: factor, y: factor)
                ctx.translateBy(x: -border.midX, y: -border.midY)
            }
            MagicMoveStage.drawImage(piece.snapshot.image, in: piece.snapshot.ink, alpha: piece.opacity(at: time), ctx: ctx)
            ctx.restoreGState()
        }
        for key in order {
            if let flight = flights[key] {
                draw(flight, at: time, into: ctx)
            }
        }
    }

    public static func close(_ a: CGRect, _ b: CGRect) -> Bool {
        return abs(a.minX - b.minX) < 0.5 && abs(a.minY - b.minY) < 0.5 && abs(a.width - b.width) < 0.5 && abs(a.height - b.height) < 0.5
    }

    private func draw(_ flight: Flight, at time: CFTimeInterval, into ctx: CGContext) {
        let frame = flight.motion.frame(at: time)
        let outer = flight.overhang.outset(frame)
        let factor = flight.scaleFactor(at: time)
        let elapsed = time - flight.motion.begin
        ctx.saveGState()
        if factor != 1 {
            ctx.translateBy(x: outer.midX, y: outer.midY)
            ctx.scaleBy(x: factor, y: factor)
            ctx.translateBy(x: -outer.midX, y: -outer.midY)
        }
        ctx.translateBy(x: outer.minX, y: outer.minY)
        let bounds = CGRect(origin: .zero, size: outer.size)
        let masking = flight.insets(at: time).flatMap { insets -> (rect: CGRect, image: CGImage)? in
            let rect = insets.outset(CGRect(origin: .zero, size: frame.size))
            return flight.mask?.image(at: elapsed, size: rect.size).map { (rect, $0) }
        }
        if let masking = masking {
            ctx.beginTransparencyLayer(in: masking.rect, auxiliaryInfo: nil)
            MagicMoveStage.drawImage(masking.image, in: masking.rect, alpha: 1, ctx: ctx)
            if let fill = flight.fill, let color = fill.color(at: elapsed) {
                ctx.setBlendMode(.sourceIn)
                ctx.setFillColor(color)
                ctx.fill(masking.rect)
            }
            ctx.setBlendMode(.sourceAtop)
        } else if flight.clips {
            ctx.addPath(MagicMoveStage.clipPath(bounds, radius: flight.radius(at: time), corners: flight.corners.to))
            ctx.twClip()
        }
        for piece in flight.pieces {
            MagicMoveStage.drawImage(piece.snapshot.image, in: CGRect(origin: piece.origin, size: piece.snapshot.ink.size), alpha: piece.opacity(at: time), ctx: ctx)
        }
        if masking != nil {
            ctx.endTransparencyLayer()
        }
        ctx.restoreGState()
    }

    private func presentedSnapshot(_ flight: Flight, at time: CFTimeInterval, scale: CGFloat) -> Snapshot? {
        let border = flight.border(at: time)
        let outer = flight.outer(at: time)
        let factor = flight.scaleFactor(at: time)
        let ink = MagicMoveGeometry.pixelAligned(factor == 1 ? outer : MagicMoveGeometry.scaledAboutCenter(outer, factor: factor), scale: scale)
        let pw = Int((ink.width * scale).rounded()), ph = Int((ink.height * scale).rounded())
        guard pw > 0, ph > 0, pw * ph <= MagicMoveStage.maxPixels, let ctx = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0, space: flight.pieces.last?.snapshot.image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return nil
        }
        ctx.translateBy(x: 0, y: CGFloat(ph))
        ctx.scaleBy(x: scale, y: -scale)
        ctx.translateBy(x: -ink.minX, y: -ink.minY)
        draw(flight, at: time, into: ctx)
        guard let image = ctx.makeImage() else {
            return nil
        }
        let radius = flight.radius(at: time) * factor
        let rounded = flight.corners.to.rounded
        let corners = MagicMoveCorners(topLeft: rounded.topLeft ? radius : 0, topRight: rounded.topRight ? radius : 0, bottomRight: rounded.bottomRight ? radius : 0, bottomLeft: rounded.bottomLeft ? radius : 0)
        return Snapshot(image: image, ink: ink, border: border, corners: corners)
    }

    private func makeMove(motion: MagicMoveMotion, to: Snapshot, outgoing: Snapshot?, scale: CGFloat, now: CFTimeInterval) -> Flight? {
        guard to.ink.width > 0, to.ink.height > 0 else {
            return nil
        }
        var outgoing = outgoing.flatMap { $0.ink.width > 0 && $0.ink.height > 0 ? $0 : nil }
        var to = to
        let original = (incoming: to, outgoing: outgoing)
        let incomingOverhang = MagicMoveInsets.overhang(ink: to.ink, border: to.border)
        let outgoingOverhang = outgoing.map { MagicMoveInsets.overhang(ink: $0.ink, border: $0.border) } ?? incomingOverhang
        let resized = MagicMoveGeometry.kind(from: motion.from, to: motion.to) == .resize
        let clips = resized || (outgoing.map { MagicMoveGeometry.kind(from: $0.border, to: to.border) == .resize } ?? false)
        let corners = (from: outgoing?.corners ?? to.corners, to: to.corners)
        let stretches = (width: abs(motion.from.width - motion.to.width) > 0.001 || abs((outgoing?.border.width ?? to.border.width) - to.border.width) > 0.001, height: abs(motion.from.height - motion.to.height) > 0.001 || abs((outgoing?.border.height ?? to.border.height) - to.border.height) > 0.001)
        let fillColors = clips && max(incomingOverhang.maximum, outgoingOverhang.maximum) <= 1 ? (from: (outgoing ?? to).fillColor, to: to.fillColor) : nil
        var mask: Mask?
        if let colors = fillColors, colors.from != nil, colors.to != nil, corners.from.clipRadius == corners.to.clipRadius, let slice = MagicMoveGeometry.maskSlice(corners: to.corners, size: to.ink.size, scale: scale, stretches: stretches) {
            let previous = original.outgoing.flatMap { old in MagicMoveGeometry.maskSlice(corners: old.corners, size: old.ink.size, scale: scale, stretches: stretches).map { (image: old.image, slice: $0) } }
            if original.outgoing == nil || previous != nil {
                mask = Mask(image: to.image, slice: slice, scale: scale, previous: previous, switchTime: MagicMoveFade.fadeOut.delay)
            }
        }
        let fill = mask != nil ? fillColors.flatMap { MagicMoveFill(from: $0.from, to: $0.to) } : nil
        let overhang = mask != nil ? .zero : incomingOverhang.union(outgoingOverhang)
        if let fill = fill {
            outgoing = outgoing.map { $0.flattened(onto: fill.from) }
            to = to.flattened(onto: fill.to)
        }
        let container = MagicMoveStage.container(overhang.outset(motion.to))
        var pieces: [Piece] = []
        if let outgoing = outgoing {
            let origin = MagicMoveGeometry.contentOrigin(ink: outgoing.ink, border: outgoing.border, overhang: overhang)
            let piece = Piece(layer: MagicMoveStage.contentLayer(outgoing, origin: origin, scale: scale), snapshot: outgoing, origin: origin, fade: .fadeOut, begin: now)
            MagicMoveStage.apply(piece)
            container.twAddSublayer(piece.layer)
            pieces.append(piece)
        }
        let origin = MagicMoveGeometry.contentOrigin(ink: to.ink, border: to.border, overhang: overhang)
        let incoming = Piece(layer: MagicMoveStage.contentLayer(to, origin: origin, scale: scale), snapshot: to, origin: origin, fade: pieces.isEmpty ? nil : .fadeIn, begin: now)
        MagicMoveStage.apply(incoming)
        container.twAddSublayer(incoming.layer)
        pieces.append(incoming)
        let insets = (from: outgoingOverhang, to: incomingOverhang)
        if let mask = mask {
            let maskLayer = CALayer()
            maskLayer.contents = mask.image
            maskLayer.contentsScale = scale
            maskLayer.contentsGravity = .resize
            maskLayer.contentsCenter = MagicMoveStage.contentsCenter(mask.slice, size: original.incoming.ink.size)
            if let previous = mask.previous, let old = original.outgoing {
                for (key, value) in [("contents", previous.image as AnyObject), ("contentsCenter", NSValue(rect: MagicMoveStage.contentsCenter(previous.slice, size: old.ink.size)))] {
                    let hold = CABasicAnimation(keyPath: key)
                    hold.fromValue = value
                    hold.toValue = value
                    hold.beginTime = now
                    hold.duration = mask.switchTime
                    hold.fillMode = .backwards
                    maskLayer.add(hold, forKey: key)
                }
            }
            let fillLayer = CALayer()
            if let fill = fill {
                fillLayer.backgroundColor = fill.color(at: fill.fade.delay + fill.fade.duration)
                if fill.from != fill.to {
                    let animation = CABasicAnimation(keyPath: "backgroundColor")
                    animation.fromValue = fill.color(at: 0)
                    animation.toValue = fillLayer.backgroundColor
                    animation.duration = fill.fade.duration
                    animation.beginTime = now + fill.fade.delay
                    animation.timingFunction = CAMediaTimingFunction(controlPoints: Float(fill.fade.curve[0]), Float(fill.fade.curve[1]), Float(fill.fade.curve[2]), Float(fill.fade.curve[3]))
                    animation.fillMode = .both
                    fillLayer.add(animation, forKey: "fill")
                }
            }
            for layer in [maskLayer, fillLayer] {
                let target = insets.to.outset(CGRect(origin: .zero, size: motion.to.size))
                layer.anchorPoint = .zero
                layer.position = target.origin
                layer.bounds = CGRect(origin: .zero, size: target.size)
                MagicMoveStage.animateInsets(layer, motion: motion, insets: insets)
            }
            container.twInsertSublayer(fillLayer, at: 0)
            container.mask = maskLayer
        } else if clips {
            container.masksToBounds = true
            container.maskedCorners = MagicMoveStage.maskedCorners(to.corners.rounded)
            container.cornerRadius = corners.to.clipRadius
            if corners.from.clipRadius != corners.to.clipRadius {
                container.add(MagicMoveStage.spring("cornerRadius", from: Double(corners.from.clipRadius), to: Double(corners.to.clipRadius), velocity: 0, begin: now), forKey: "radius")
            }
        }
        MagicMoveStage.animateFrame(container, motion: motion, overhang: overhang)
        let flight = Flight(container: container, motion: motion, overhang: overhang, clips: clips, fill: fill, corners: corners, scale: (1, 1), pieces: pieces)
        flight.mask = mask
        flight.maskInsets = mask != nil ? insets : nil
        return flight
    }

    private func makeEnter(_ snapshot: Snapshot, scale: CGFloat, now: CFTimeInterval, dissolve: Bool) -> Flight? {
        guard snapshot.ink.width > 0, snapshot.ink.height > 0 else {
            return nil
        }
        let overhang = MagicMoveInsets.overhang(ink: snapshot.ink, border: snapshot.border)
        let outer = overhang.outset(snapshot.border)
        let container = MagicMoveStage.container(outer)
        let origin = MagicMoveGeometry.contentOrigin(ink: snapshot.ink, border: snapshot.border, overhang: overhang)
        let piece = Piece(layer: MagicMoveStage.contentLayer(snapshot, origin: origin, scale: scale), snapshot: snapshot, origin: origin, fade: dissolve ? .dissolveIn : .enter, begin: now)
        MagicMoveStage.apply(piece)
        container.twAddSublayer(piece.layer)
        let motion = MagicMoveMotion(from: snapshot.border, to: snapshot.border, begin: now, spring: MagicMoveStage.spring)
        if dissolve {
            return Flight(container: container, motion: motion, overhang: overhang, clips: false, corners: (snapshot.corners, snapshot.corners), scale: (1, 1), pieces: [piece])
        }
        container.add(MagicMoveStage.transformSpring(from: MagicMoveStage.scaling(about: CGPoint(x: outer.width / 2, y: outer.height / 2), factor: MagicMoveStage.enterScale), to: CATransform3DIdentity, begin: now), forKey: "enter")
        return Flight(container: container, motion: motion, overhang: overhang, clips: false, corners: (snapshot.corners, snapshot.corners), scale: (MagicMoveStage.enterScale, 1), pieces: [piece])
    }

    private func makeLeave(_ snapshot: Snapshot, scale: CGFloat, now: CFTimeInterval, dissolve: Bool) -> Leaving? {
        guard snapshot.ink.width > 0, snapshot.ink.height > 0 else {
            return nil
        }
        let layer = MagicMoveStage.contentLayer(snapshot, origin: snapshot.ink.origin, scale: scale)
        layer.zPosition = 1
        let piece = Piece(layer: layer, snapshot: snapshot, origin: snapshot.ink.origin, fade: dissolve ? .dissolveOut : .leave, begin: now)
        MagicMoveStage.apply(piece)
        if dissolve {
            return Leaving(layer: layer, piece: piece, spring: MagicMoveStage.spring, shrinks: false)
        }
        let end = MagicMoveStage.scaling(about: CGPoint(x: snapshot.border.midX - snapshot.ink.minX, y: snapshot.border.midY - snapshot.ink.minY), factor: MagicMoveStage.enterScale)
        layer.transform = end
        layer.add(MagicMoveStage.transformSpring(from: CATransform3DIdentity, to: end, begin: now), forKey: "leave")
        return Leaving(layer: layer, piece: piece, spring: MagicMoveStage.spring, shrinks: true)
    }

    private static func container(_ outer: CGRect) -> CALayer {
        let container = CALayer()
        container.anchorPoint = .zero
        container.zPosition = 2
        container.bounds = CGRect(origin: .zero, size: outer.size)
        container.position = outer.origin
        return container
    }

    private static func contentLayer(_ snapshot: Snapshot, origin: CGPoint, scale: CGFloat) -> CALayer {
        let layer = CALayer()
        layer.contents = snapshot.image
        layer.contentsScale = scale
        layer.contentsGravity = .resize
        layer.anchorPoint = .zero
        layer.bounds = CGRect(origin: .zero, size: snapshot.ink.size)
        layer.position = origin
        return layer
    }

    private static func apply(_ piece: Piece) {
        guard let fade = piece.fade else {
            return
        }
        piece.layer.opacity = fade.to
        let animation = CABasicAnimation(keyPath: "opacity")
        animation.fromValue = fade.from
        animation.toValue = fade.to
        animation.duration = fade.duration
        animation.beginTime = piece.begin + fade.delay
        animation.timingFunction = CAMediaTimingFunction(controlPoints: Float(fade.curve[0]), Float(fade.curve[1]), Float(fade.curve[2]), Float(fade.curve[3]))
        animation.fillMode = .both
        piece.layer.add(animation, forKey: "fade")
    }

    private static func contentsCenter(_ slice: CGSize, size: CGSize) -> CGRect {
        return CGRect(x: slice.width / size.width, y: slice.height / size.height, width: 1 - slice.width * 2 / size.width, height: 1 - slice.height * 2 / size.height)
    }

    private static func animateFrame(_ container: CALayer, motion: MagicMoveMotion, overhang: MagicMoveInsets) {
        let from = overhang.outset(motion.from), to = overhang.outset(motion.to)
        let velocities = motion.normalizedVelocities()
        let channels: [(String, CGFloat, CGFloat)] = [("position.x", from.minX, to.minX), ("position.y", from.minY, to.minY), ("bounds.size.width", from.width, to.width), ("bounds.size.height", from.height, to.height)]
        for (index, channel) in channels.enumerated() where abs(channel.1 - channel.2) > 0.001 {
            container.add(spring(channel.0, from: Double(channel.1), to: Double(channel.2), velocity: velocities[index], begin: motion.begin), forKey: channel.0)
        }
    }

    private static func animateInsets(_ layer: CALayer, motion: MagicMoveMotion, insets: (from: MagicMoveInsets, to: MagicMoveInsets)) {
        let velocities = motion.normalizedVelocities()
        let sizes: [(String, CGFloat, CGFloat, Int)] = [("bounds.size.width", motion.from.width, motion.to.width, 2), ("bounds.size.height", motion.from.height, motion.to.height, 3)]
        for (keyPath, from, to, index) in sizes where abs(from - to) > 0.001 {
            let animation = spring(keyPath, from: Double(from), to: Double(to), velocity: velocities[index], begin: motion.begin)
            animation.isAdditive = true
            animation.fromValue = Double(from - to)
            animation.toValue = 0
            animation.initialVelocity = CGFloat(velocities[index])
            layer.add(animation, forKey: keyPath)
        }
        let shifts: [(String, CGFloat)] = [("position.x", insets.to.left - insets.from.left), ("position.y", insets.to.top - insets.from.top), ("bounds.size.width", insets.from.left + insets.from.right - insets.to.left - insets.to.right), ("bounds.size.height", insets.from.top + insets.from.bottom - insets.to.top - insets.to.bottom)]
        for (keyPath, extra) in shifts where abs(extra) > 0.001 {
            let animation = spring(keyPath, from: Double(extra), to: 0, velocity: 0, begin: motion.begin)
            animation.isAdditive = true
            layer.add(animation, forKey: keyPath + ".insets")
        }
    }

    private static func spring(_ keyPath: String, from: Double, to: Double, velocity: Double, begin: CFTimeInterval) -> CASpringAnimation {
        let animation = CASpringAnimation(keyPath: keyPath)
        animation.fromValue = from
        animation.toValue = to
        configure(animation, begin: begin)
        animation.initialVelocity = CGFloat(velocity)
        return animation
    }

    private static func transformSpring(from: CATransform3D, to: CATransform3D, begin: CFTimeInterval) -> CASpringAnimation {
        let animation = CASpringAnimation(keyPath: "transform")
        animation.fromValue = NSValue(caTransform3D: from)
        animation.toValue = NSValue(caTransform3D: to)
        configure(animation, begin: begin)
        return animation
    }

    public static func springAnimation(keyPath: String, from: Any, to: Any) -> CASpringAnimation {
        let animation = CASpringAnimation(keyPath: keyPath)
        animation.fromValue = from
        animation.toValue = to
        animation.mass = CGFloat(spring.mass)
        animation.stiffness = CGFloat(spring.stiffness)
        animation.damping = CGFloat(spring.damping)
        animation.duration = duration
        return animation
    }

    private static func configure(_ animation: CASpringAnimation, begin: CFTimeInterval) {
        animation.mass = CGFloat(spring.mass)
        animation.stiffness = CGFloat(spring.stiffness)
        animation.damping = CGFloat(spring.damping)
        animation.duration = duration
        animation.beginTime = begin
        animation.fillMode = .both
    }

    private static func scaling(about center: CGPoint, factor: CGFloat) -> CATransform3D {
        var transform = CATransform3DMakeTranslation(-center.x, -center.y, 0)
        transform = CATransform3DConcat(transform, CATransform3DMakeScale(factor, factor, 1))
        return CATransform3DConcat(transform, CATransform3DMakeTranslation(center.x, center.y, 0))
    }

    private static func maskedCorners(_ rounded: (topLeft: Bool, topRight: Bool, bottomRight: Bool, bottomLeft: Bool)) -> CACornerMask {
        var mask: CACornerMask = []
        if rounded.topLeft {
            mask.insert(.layerMinXMinYCorner)
        }
        if rounded.topRight {
            mask.insert(.layerMaxXMinYCorner)
        }
        if rounded.bottomRight {
            mask.insert(.layerMaxXMaxYCorner)
        }
        if rounded.bottomLeft {
            mask.insert(.layerMinXMaxYCorner)
        }
        return mask
    }

    static func maskImage(_ image: CGImage, slice: CGSize, size: CGSize, scale: CGFloat) -> CGImage? {
        let pw = Int(ceil(size.width * scale)), ph = Int(ceil(size.height * scale))
        guard pw > 0, ph > 0, let ctx = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0, space: image.colorSpace ?? MagicMoveFill.space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return nil
        }
        ctx.setBlendMode(.copy)
        let source = CGSize(width: image.width, height: image.height)
        for patch in MagicMoveGeometry.ninePatch(source: source, slice: CGSize(width: slice.width * scale, height: slice.height * scale), destination: CGSize(width: CGFloat(pw), height: CGFloat(ph))) {
            guard let piece = image.cropping(to: patch.source.integral) else {
                continue
            }
            let target = patch.destination
            ctx.draw(piece, in: CGRect(x: target.minX, y: CGFloat(ph) - target.maxY, width: target.width, height: target.height))
        }
        return ctx.makeImage()
    }

    static func clipPath(_ rect: CGRect, radius: CGFloat, corners: MagicMoveCorners) -> CGPath {
        let r = min(radius, rect.width / 2, rect.height / 2)
        guard r > 0 else {
            return CGPath(rect: rect, transform: nil)
        }
        let rounded = corners.rounded
        let path = CGMutablePath()
        path.move(to: CGPoint(x: rect.minX + (rounded.topLeft ? r : 0), y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - (rounded.topRight ? r : 0), y: rect.minY))
        if rounded.topRight {
            path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.minY + r), radius: r)
        }
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - (rounded.bottomRight ? r : 0)))
        if rounded.bottomRight {
            path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.maxY), tangent2End: CGPoint(x: rect.maxX - r, y: rect.maxY), radius: r)
        }
        path.addLine(to: CGPoint(x: rect.minX + (rounded.bottomLeft ? r : 0), y: rect.maxY))
        if rounded.bottomLeft {
            path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.maxY - r), radius: r)
        }
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + (rounded.topLeft ? r : 0)))
        if rounded.topLeft {
            path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.minY), tangent2End: CGPoint(x: rect.minX + r, y: rect.minY), radius: r)
        }
        path.closeSubpath()
        return path
    }

    private static func drawImage(_ image: CGImage, in rect: CGRect, alpha: Float, ctx: CGContext) {
        guard alpha > 0 else {
            return
        }
        ctx.saveGState()
        ctx.setAlpha(CGFloat(alpha))
        ctx.translateBy(x: rect.minX, y: rect.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(origin: .zero, size: rect.size))
        ctx.restoreGState()
    }

    static func same(_ a: CGImage, _ b: CGImage) -> Bool {
        if a === b {
            return true
        }
        guard a.width == b.width, a.height == b.height, a.bytesPerRow == b.bytesPerRow, a.bitmapInfo == b.bitmapInfo, let da = a.dataProvider?.data, let db = b.dataProvider?.data else {
            return false
        }
        return CFEqual(da, db)
    }
}

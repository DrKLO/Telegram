import AppKit
import LiteHTML
import QuartzCore

struct WidgetScrollGeometry: Equatable {
    var viewport: CGRect
    var content: CGSize
    var radii: [CGFloat]

    var maxOffset: CGPoint {
        return CGPoint(x: max(0, content.width - viewport.width), y: max(0, content.height - viewport.height))
    }

    func offsetToReveal(_ target: CGRect, from offset: CGPoint, block: Int, inline: Int) -> CGPoint {
        func delta(_ start: CGFloat, _ end: CGFloat, _ viewStart: CGFloat, _ viewEnd: CGFloat, _ alignment: Int) -> CGFloat {
            switch alignment {
            case 1:
                return (start + end - viewStart - viewEnd) / 2
            case 2:
                return end - viewEnd
            case 3:
                if start < viewStart && end > viewEnd { return 0 }
                let smaller = end - start <= viewEnd - viewStart
                if start < viewStart && smaller || end > viewEnd && !smaller { return start - viewStart }
                if end > viewEnd && smaller || start < viewStart && !smaller { return end - viewEnd }
                return 0
            default:
                return start - viewStart
            }
        }
        return clamp(CGPoint(x: offset.x + delta(target.minX, target.maxX, viewport.minX, viewport.maxX, inline), y: offset.y + delta(target.minY, target.maxY, viewport.minY, viewport.maxY, block)))
    }

    func clamp(_ offset: CGPoint) -> CGPoint {
        let limit = maxOffset
        return CGPoint(x: min(max(0, offset.x), limit.x), y: min(max(0, offset.y), limit.y))
    }
}

final class WidgetScrollLayer {
    let id: Int
    let clip = CALayer()
    let content = CALayer()
    let thumb = CALayer()
    private(set) var geometry: WidgetScrollGeometry
    private let schedule: (@escaping () -> Void) -> Void
    private var tiles: [Int: (layer: CALayer, signature: Int)] = [:]
    private var pending: Set<Int> = []
    private var rasterSource: (document: LHDocument, scale: CGFloat, attribute: String)?
    private var signature = 0
    private var generation = 0
    private var rasterLifetime = WidgetRasterLifetime()
    private var refillsVisibleTiles = false
    private var viewportRasterCapacity = CGSize.zero
    var clipsRasterToViewport = false {
        didSet {
            if clipsRasterToViewport != oldValue {
                viewportRasterCapacity = .zero
                updateViewportRasterCapacity()
                refillsVisibleTiles = true
                invalidate()
            }
        }
    }
    static let tileSize = CGSize(width: 512, height: 256)
    static let queue = DispatchQueue(label: "twidget.scroll.raster", qos: .userInitiated)
    static let batchesCommits = ProcessInfo.processInfo.environment["TW_SCROLL_BATCH_COMMITS"] != "0"
    static let skipsObsoleteRaster = ProcessInfo.processInfo.environment["TW_SCROLL_SKIP_OBSOLETE"] != "0"
    private(set) var rasterCount = 0

    init(id: Int, geometry: WidgetScrollGeometry, schedule: @escaping (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) }) {
        self.id = id
        self.schedule = schedule
        self.geometry = geometry
        clip.anchorPoint = .zero
        clip.masksToBounds = true
        clip.name = "tw.scroll"
        content.anchorPoint = .zero
        content.name = "tw.scroll.content"
        clip.twAddSublayer(content)
        thumb.name = "tw.scroll.thumb"
        thumb.backgroundColor = CGColor(gray: 0, alpha: 0.45)
        thumb.borderColor = CGColor(gray: 1, alpha: 0.3)
        thumb.borderWidth = 0.5
        thumb.cornerRadius = 3
        thumb.opacity = 0
        thumb.actions = ["position": NSNull(), "bounds": NSNull(), "opacity": NSNull()]
        clip.twAddSublayer(thumb)
        apply(geometry)
    }

    deinit {
        rasterLifetime.cancel()
    }

    func apply(_ geometry: WidgetScrollGeometry) {
        let resized = geometry.viewport.size != self.geometry.viewport.size || geometry.content != self.geometry.content
        let previousRasterSize = rasterSize
        self.geometry = geometry
        updateViewportRasterCapacity()
        if clipsRasterToViewport && rasterSize != previousRasterSize {
            refillsVisibleTiles = true
        }
        clip.frame = geometry.viewport
        content.frame = CGRect(origin: .zero, size: geometry.viewport.size)
        if geometry.radii.contains(where: { $0 > 0 }) {
            let mask = (clip.mask as? CAShapeLayer) ?? CAShapeLayer()
            mask.frame = clip.bounds
            mask.path = WidgetCompositor.roundedPath(clip.bounds, geometry.radii)
            clip.mask = mask
        } else {
            clip.mask = nil
        }
        if resized {
            invalidate()
        }
    }

    var offset: CGPoint {
        return content.bounds.origin
    }

    func show(_ offset: CGPoint) {
        content.bounds.origin = offset
    }

    func flashIndicator(_ offset: CGPoint) {
        let viewport = geometry.viewport.size
        let total = max(viewport.height, geometry.content.height)
        guard !thumb.isHidden, geometry.maxOffset.y > 0.5, total > 0 else {
            return
        }
        let inset: CGFloat = 2
        let track = viewport.height - inset * 2
        let length = max(24, track * viewport.height / total)
        let position = inset + (track - length) * min(1, max(0, offset.y / geometry.maxOffset.y))
        thumb.frame = CGRect(x: viewport.width - 6 - inset, y: position, width: 6, height: length)
        thumb.removeAnimation(forKey: "tw.fade")
        thumb.opacity = 1
        let fade = CABasicAnimation(keyPath: "opacity")
        fade.fromValue = 1
        fade.toValue = 0
        fade.beginTime = thumb.convertTime(CACurrentMediaTime(), from: nil) + 0.9
        fade.duration = 0.25
        fade.fillMode = .forwards
        fade.isRemovedOnCompletion = false
        thumb.add(fade, forKey: "tw.fade")
    }

    func invalidate() {
        generation += 1
        rasterLifetime.cancel()
        rasterLifetime = WidgetRasterLifetime()
        for (index, tile) in tiles {
            tiles[index] = (tile.layer, 0)
        }
    }

    func update(signature: Int) {
        if signature != self.signature {
            self.signature = signature
            invalidate()
        }
    }

    private func columns() -> Int {
        return max(1, Int(ceil(rasterSize.width / WidgetScrollLayer.tileSize.width)))
    }

    private var rasterSize: CGSize {
        let full = CGSize(width: max(geometry.viewport.width, geometry.content.width), height: max(geometry.viewport.height, geometry.content.height))
        guard clipsRasterToViewport else { return full }
        return CGSize(width: min(full.width, viewportRasterCapacity.width), height: min(full.height, viewportRasterCapacity.height))
    }

    private func updateViewportRasterCapacity() {
        guard clipsRasterToViewport else { return }
        func capacity(_ required: CGFloat, previous: CGFloat) -> CGFloat {
            guard required > previous || required * 2 < previous else { return previous }
            return ceil(required * 1.25 / 64) * 64
        }
        viewportRasterCapacity = CGSize(width: capacity(geometry.viewport.width, previous: viewportRasterCapacity.width),
                                       height: capacity(geometry.viewport.height, previous: viewportRasterCapacity.height))
    }

    private func rect(_ index: Int) -> CGRect {
        let columns = self.columns()
        let size = WidgetScrollLayer.tileSize
        let width = rasterSize.width, height = rasterSize.height
        let x = CGFloat(index % columns) * size.width, y = CGFloat(index / columns) * size.height
        return CGRect(x: x, y: y, width: min(size.width, width - x), height: min(size.height, height - y))
    }

    func ensureTiles(document: LHDocument, scale: CGFloat, attribute: String, velocity: CGFloat = 0) {
        guard scale.isFinite, scale > 0 else {
            return
        }
        rasterSource = (document, scale, attribute)
        let columns = self.columns()
        let size = WidgetScrollLayer.tileSize
        let visible = CGRect(origin: geometry.clamp(offset), size: geometry.viewport.size)
        let ahead = visible.insetBy(dx: -geometry.viewport.width * 0.5, dy: -max(geometry.viewport.height, 256)).offsetBy(dx: 0, dy: velocity)
        let keep = visible.insetBy(dx: -geometry.viewport.width * 2, dy: -geometry.viewport.height * 3)
        let width = rasterSize.width, height = rasterSize.height
        let rows = max(1, Int(ceil(height / size.height)))
        for index in Array(tiles.keys) {
            let currentRect = rect(index)
            if !currentRect.intersects(keep) || tiles[index]?.layer.frame != currentRect {
                tiles[index]?.layer.twRemoveFromSuperlayer()
                tiles[index] = nil
            }
        }
        let firstFill = tiles.isEmpty || refillsVisibleTiles
        let wanted = ahead.intersection(CGRect(x: 0, y: 0, width: width, height: height))
        guard !wanted.isNull else {
            return
        }
        let firstRow = max(0, Int(floor(wanted.minY / size.height))), lastRow = min(rows - 1, Int(floor((wanted.maxY - 0.001) / size.height)))
        let firstColumn = max(0, Int(floor(wanted.minX / size.width))), lastColumn = min(columns - 1, Int(floor((wanted.maxX - 0.001) / size.width)))
        guard firstRow <= lastRow, firstColumn <= lastColumn else {
            return
        }
        refillsVisibleTiles = false
        var order: [Int] = []
        for row in firstRow...lastRow {
            for column in firstColumn...lastColumn {
                order.append(row * columns + column)
            }
        }
        order.sort { abs(rect($0).midY - visible.midY) < abs(rect($1).midY - visible.midY) }
        for index in order where tiles[index]?.signature != signature {
            let tileRect = rect(index)
            let rasterRect = tileRect.insetBy(dx: -1 / scale, dy: -1 / scale)
            guard tileRect.width > 0, tileRect.height > 0 else {
                continue
            }
            let fillVisibleTile = firstFill && tileRect.intersects(visible)
            guard fillVisibleTile || !pending.contains(index) else {
                continue
            }
            let generation = self.generation, signature = self.signature, value = String(id)
            let lifetime = rasterLifetime
            let layer = tiles[index]?.layer ?? makeTile(tileRect)
            if tiles[index] == nil {
                tiles[index] = (layer, 0)
            }
            if fillVisibleTile {
                install(document.copyScrollContent(attribute: attribute, value: value, rect: rasterRect, scale: scale), in: layer, rect: tileRect, scale: scale)
                tiles[index] = (layer, signature)
                rasterCount += 1
                continue
            }
            pending.insert(index)
            let schedule = self.schedule
            WidgetScrollLayer.queue.async { [weak self] in
                let image: CGImage?
                if Self.skipsObsoleteRaster {
                    image = document.copyScrollContentIfCurrent(attribute: attribute, value: value, rect: rasterRect, scale: scale, shouldContinue: { lifetime.isActive })
                } else {
                    image = lifetime.isActive ? document.copyScrollContent(attribute: attribute, value: value, rect: rasterRect, scale: scale) : nil
                }
                schedule {
                    guard let self = self else {
                        return
                    }
                    self.pending.remove(index)
                    guard generation == self.generation, let tile = self.tiles[index], tile.layer === layer else {
                        if let source = self.rasterSource {
                            self.ensureTiles(document: source.document, scale: source.scale, attribute: source.attribute)
                        }
                        return
                    }
                    guard let image = image else {
                        if let source = self.rasterSource, source.document !== document {
                            self.ensureTiles(document: source.document, scale: source.scale, attribute: source.attribute)
                        }
                        return
                    }
                    self.install(image, in: layer, rect: tileRect, scale: scale, implicit: WidgetScrollLayer.batchesCommits)
                    self.tiles[index] = (layer, signature)
                    self.rasterCount += 1
                }
            }
        }
    }

    func refresh(document: LHDocument, scale: CGFloat, attribute: String) {
        guard rasterSource?.document !== document else { return }
        ensureTiles(document: document, scale: scale, attribute: attribute)
    }

    private func install(_ image: CGImage?, in layer: CALayer, rect: CGRect, scale: CGFloat, implicit: Bool = false) {
        if !implicit {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
        }
        layer.frame = rect
        layer.contentsScale = scale
        if let image = image {
            layer.contentsRect = CGRect(x: 1 / CGFloat(image.width), y: 1 / CGFloat(image.height), width: rect.width * scale / CGFloat(image.width), height: rect.height * scale / CGFloat(image.height))
        }
        layer.contents = image
        if !implicit {
            CATransaction.commit()
        }
    }

    private func makeTile(_ rect: CGRect) -> CALayer {
        let layer = CALayer()
        layer.anchorPoint = .zero
        layer.frame = rect
        layer.contentsGravity = .resize
        layer.allowsEdgeAntialiasing = false
        layer.edgeAntialiasingMask = []
        layer.actions = ["contents": NSNull(), "position": NSNull(), "bounds": NSNull(), "contentsScale": NSNull(), "contentsRect": NSNull()]
        content.twAddSublayer(layer)
        return layer
    }

    var tileCount: Int {
        return tiles.count
    }

    func draw(into ctx: CGContext, document: LHDocument, scale: CGFloat) {
        let shown = geometry.clamp(offset)
        guard let image = document.copyScrollContent(attribute: WidgetDOM.idAttribute, value: String(id), rect: CGRect(origin: shown, size: geometry.viewport.size), scale: scale) else {
            return
        }
        ctx.saveGState()
        ctx.addPath(WidgetCompositor.roundedPath(geometry.viewport, geometry.radii))
        ctx.twClip()
        ctx.translateBy(x: geometry.viewport.minX, y: geometry.viewport.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(origin: .zero, size: geometry.viewport.size))
        ctx.restoreGState()
    }
}

final class WidgetScrolling {
    var schedule: (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) }
    private(set) var layers: [Int: WidgetScrollLayer] = [:]
    private(set) var offsets: [Int: CGPoint] = [:]
    private(set) var geometries: [Int: WidgetScrollGeometry] = [:]
    private var resizedClips = Set<Int>()
    private var depths: [Int: Int] = [:]
    private var scrollUpdateDepth = 0
    private var deferredGeometryRoots = Set<Int>()
    private(set) var hitViewports: [Int: CGRect] = [:]
    private(set) var horizontalWheelIDs: Set<Int> = []
    private(set) var verticalWheelIDs: Set<Int> = []
    private weak var host: CALayer?
    static let enabled = ProcessInfo.processInfo.environment["TW_NO_SCROLL_LAYERS"] == nil
    static let resizeAwareClips = ProcessInfo.processInfo.environment["TW_NO_RESIZE_AWARE_CLIPS"] == nil
    var allowsCompositorTakeover = ProcessInfo.processInfo.environment["TW_SCROLL_COMPOSITOR_TAKEOVER"] == "1"
    var batchesScrollGeometry = ProcessInfo.processInfo.environment["TW_NO_BATCH_SCROLL_GEOMETRY"] == nil
    var clipsUnscrolledRasters = ProcessInfo.processInfo.environment["TW_CLIP_UNSCROLLED_RASTERS"] != "0"

    struct Gesture {
        var id: Int?
        var decided = false
    }

    private var gesture: Gesture?

    var promotedIDs: Set<Int> {
        return Set(layers.keys)
    }

    func compositorBlockedIDs(dom: WidgetDOM, scrollers: [String: [String: Any]]? = nil) -> Set<Int> {
        var blocked = Set<Int>()
        var interactive = Set(hitViewports.keys)
        if let scrollers = scrollers {
            interactive = Set(scrollers.compactMap { key, info in
                guard (info["wheel"] as? NSNumber)?.boolValue != false, let viewport = (info["viewport"] as? NSValue)?.rectValue, let content = (info["content"] as? NSValue)?.sizeValue, content.width > viewport.width + 0.5 || content.height > viewport.height + 0.5 else {
                    return nil
                }
                return Int(key)
            })
        }
        interactive.formUnion(offsets.keys)
        if let id = gesture?.id {
            interactive.insert(id)
        }
        for id in Set(layers.keys).union(interactive) {
            guard let node = dom.node(id) else {
                continue
            }
            if !allowsCompositorTakeover || interactive.contains(id) {
                var pending = node.children
                while let child = pending.popLast() {
                    blocked.insert(dom.publicId(child))
                    pending.append(contentsOf: child.children)
                }
            }
        }
        return blocked
    }

    func attach(_ host: CALayer?) {
        if self.host === host {
            return
        }
        self.host = host
        for layer in layers.values {
            host?.twAddSublayer(layer.clip)
        }
    }

    private func allowsViewportRaster(_ root: WidgetNode?) -> Bool {
        guard let root = root else { return false }
        var remaining = 64
        func visit(_ node: WidgetNode) -> Bool {
            remaining -= 1
            guard remaining >= 0 else { return false }
            for child in node.children where !visit(child) { return false }
            return true
        }
        return visit(root)
    }

    func reset() {
        for layer in layers.values {
            layer.clip.twRemoveFromSuperlayer()
        }
        layers.removeAll()
        resizedClips.removeAll()
        deferredGeometryRoots.removeAll()
    }

    func update(document: LHDocument, dom: WidgetDOM, animated: Set<Int>, promoted: Set<Int>, styleKey: Int, scale: CGFloat, scrollers: [String: [String: Any]]? = nil) -> Bool {
        var found: [Int: WidgetScrollGeometry] = [:]
        var hits: [Int: CGRect] = [:]
        var horizontal: Set<Int> = []
        var vertical: Set<Int> = []
        var sticky = Set<Int>()
        for (key, info) in scrollers ?? document.scrollers(byAttribute: WidgetDOM.idAttribute) {
            guard let id = Int(key), let viewport = (info["viewport"] as? NSValue)?.rectValue, let content = (info["content"] as? NSValue)?.sizeValue else {
                continue
            }
            if (info["wheelX"] as? NSNumber)?.boolValue ?? ((info["wheel"] as? NSNumber)?.boolValue != false) { horizontal.insert(id) }
            if (info["wheelY"] as? NSNumber)?.boolValue ?? ((info["wheel"] as? NSNumber)?.boolValue != false) { vertical.insert(id) }
            if (info["wheel"] as? NSNumber)?.boolValue != false {
                hits[id] = (info["hitViewport"] as? NSValue)?.rectValue ?? viewport
            }
            if (info["sticky"] as? NSNumber)?.boolValue == true {
                sticky.insert(id)
            }
            let radii = ((info["radii"] as? [NSNumber]) ?? []).map { CGFloat($0.doubleValue) }
            found[id] = WidgetScrollGeometry(viewport: viewport, content: content, radii: radii.count == 8 ? radii : [CGFloat](repeating: 0, count: 8))
        }
        resizedClips = resizedClips.intersection(found.keys)
        for (id, geometry) in found where Self.resizeAwareClips && hits[id] == nil && offsets[id] == nil {
            if let previous = geometries[id], previous.viewport.width == geometry.viewport.width, previous.viewport.height != geometry.viewport.height {
                resizedClips.insert(id)
            }
        }
        geometries = found
        hitViewports = hits
        horizontalWheelIDs = horizontal
        verticalWheelIDs = vertical
        depths.removeAll(keepingCapacity: true)
        for id in found.keys {
            var depth = 0
            var walk = dom.node(id)?.parent
            while let parent = walk {
                if found[dom.publicId(parent)] != nil { depth += 1 }
                walk = parent.parent
            }
            depths[id] = depth
        }
        for (id, offset) in offsets {
            if found[id] == nil {
                offsets[id] = nil
            } else {
                offsets[id] = document.setScrollOffset(offset, attribute: WidgetDOM.idAttribute, value: String(id))
            }
        }
        var wanted: [Int: WidgetScrollGeometry] = [:]
        if WidgetScrolling.enabled, let host = host {
            _ = host
            for (id, geometry) in found where geometry.maxOffset.y > 0.5 || geometry.maxOffset.x > 0.5 {
                guard hits[id] != nil || offsets[id] != nil || !resizedClips.contains(id) else {
                    continue
                }
                guard !sticky.contains(id), let node = dom.node(id), node.tag.lowercased() != "textarea", geometry.viewport.width > 0, geometry.viewport.height > 0, geometry.viewport.width * geometry.viewport.height < 4_000_000 else {
                    continue
                }
                var walk = node.parent
                var nested = false
                while let parent = walk {
                    let parentId = dom.publicId(parent)
                    let parentScrolls = found[parentId].map { $0.maxOffset.x > 0.5 || $0.maxOffset.y > 0.5 } ?? false
                    if promoted.contains(parentId) || parentScrolls {
                        nested = true
                        break
                    }
                    walk = parent.parent
                }
                if nested || promoted.contains(id) || animated.contains(where: { WidgetScrolling.isDescendant($0, of: node, dom: dom) }) || promoted.contains(where: { WidgetScrolling.isDescendant($0, of: node, dom: dom) }) {
                    continue
                }
                guard WidgetCompositor.contentSignature(id, dom: dom, box: geometry.viewport, scale: scale, styleKey: styleKey) != nil else {
                    continue
                }
                guard document.isElement(attribute: WidgetDOM.idAttribute, value: String(id), unobscuredIn: geometry.viewport, ignoring: Set(promoted.map { String($0) }), clipped: false) else {
                    continue
                }
                wanted[id] = geometry
            }
        }
        let changed = Set(wanted.keys) != Set(layers.keys)
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (id, layer) in layers where wanted[id] == nil {
            layer.clip.twRemoveFromSuperlayer()
            layers[id] = nil
        }
        for (id, geometry) in wanted {
            let layer = layers[id] ?? WidgetScrollLayer(id: id, geometry: geometry, schedule: schedule)
            if layers[id] == nil {
                layers[id] = layer
                host?.twAddSublayer(layer.clip)
            }
            layer.apply(geometry)
            layer.clipsRasterToViewport = clipsUnscrolledRasters && hits[id] == nil && offsets[id] == nil && allowsViewportRaster(dom.node(id))
            layer.thumb.isHidden = hits[id] == nil
            layer.update(signature: WidgetCompositor.contentSignature(id, dom: dom, box: CGRect(origin: .zero, size: geometry.content), scale: scale, styleKey: styleKey) ?? 0)
            layer.show(offsets[id] ?? .zero)
            layer.ensureTiles(document: document, scale: scale, attribute: WidgetDOM.idAttribute)
        }
        CATransaction.commit()
        return changed
    }

    func refresh(document: LHDocument, scale: CGFloat) {
        for layer in layers.values {
            layer.refresh(document: document, scale: scale, attribute: WidgetDOM.idAttribute)
        }
    }

    func didScroll(_ id: Int, dom: WidgetDOM, document: LHDocument, scale: CGFloat) -> Bool {
        for (ancestor, layer) in layers {
            if let node = dom.node(ancestor), Self.isDescendant(id, of: node, dom: dom) {
                layer.invalidate()
                layer.ensureTiles(document: document, scale: scale, attribute: WidgetDOM.idAttribute)
            }
        }
        if batchesScrollGeometry && scrollUpdateDepth > 0 {
            deferredGeometryRoots.insert(id)
            return false
        }
        return refreshDescendantGeometry(of: [id], dom: dom, document: document)
    }

    func beginScrollUpdates() {
        scrollUpdateDepth += 1
    }

    func endScrollUpdates(dom: WidgetDOM, document: LHDocument?) -> Bool {
        guard scrollUpdateDepth > 0 else { return false }
        scrollUpdateDepth -= 1
        guard scrollUpdateDepth == 0 else { return false }
        let roots = deferredGeometryRoots
        deferredGeometryRoots.removeAll()
        guard let document = document, !roots.isEmpty else { return false }
        return refreshDescendantGeometry(of: roots, dom: dom, document: document)
    }

    private func refreshDescendantGeometry(of roots: Set<Int>, dom: WidgetDOM, document: LHDocument) -> Bool {
        let attached = Set(roots.filter { dom.node($0).map { dom.isAttached($0) } == true })
        guard !attached.isEmpty else { return false }
        let descendants = geometries.keys.filter { id in
            var walk = dom.node(id)?.parent
            while let parent = walk {
                if attached.contains(dom.publicId(parent)) { return true }
                walk = parent.parent
            }
            return false
        }
        guard !descendants.isEmpty else { return false }
        let info = document.scrollers(byAttribute: WidgetDOM.idAttribute)
        for descendant in descendants {
            if let viewport = (info[String(descendant)]?["viewport"] as? NSValue)?.rectValue {
                geometries[descendant]?.viewport = viewport
                let values = info[String(descendant)] ?? [:]
                if (values["wheelX"] as? NSNumber)?.boolValue ?? ((values["wheel"] as? NSNumber)?.boolValue != false) { horizontalWheelIDs.insert(descendant) } else { horizontalWheelIDs.remove(descendant) }
                if (values["wheelY"] as? NSNumber)?.boolValue ?? ((values["wheel"] as? NSNumber)?.boolValue != false) { verticalWheelIDs.insert(descendant) } else { verticalWheelIDs.remove(descendant) }
                if (info[String(descendant)]?["wheel"] as? NSNumber)?.boolValue != false {
                    hitViewports[descendant] = (info[String(descendant)]?["hitViewport"] as? NSValue)?.rectValue ?? viewport
                } else {
                    hitViewports[descendant] = nil
                }
            }
        }
        return true
    }

    private static func isDescendant(_ id: Int, of node: WidgetNode, dom: WidgetDOM) -> Bool {
        guard var walk = dom.node(id) else {
            return false
        }
        while let parent = walk.parent {
            if parent === node {
                return true
            }
            walk = parent
        }
        return false
    }

    func scroller(at point: CGPoint, delta: CGPoint) -> Int? {
        let candidates = geometries.filter { hitViewports[$0.key]?.contains(point) == true && ($0.value.maxOffset.y > 0.5 || $0.value.maxOffset.x > 0.5) }
        for (id, geometry) in candidates.sorted(by: {
            let leftDepth = depths[$0.key] ?? 0, rightDepth = depths[$1.key] ?? 0
            if leftDepth != rightDepth { return leftDepth > rightDepth }
            return $0.value.viewport.width * $0.value.viewport.height < $1.value.viewport.width * $1.value.viewport.height
        }) {
            let offset = offsets[id] ?? .zero
            let vertical = abs(delta.y) >= abs(delta.x)
            if vertical && !verticalWheelIDs.contains(id) || !vertical && !horizontalWheelIDs.contains(id) { continue }
            if vertical {
                if delta.y > 0 && offset.y > 0.5 || delta.y < 0 && offset.y < geometry.maxOffset.y - 0.5 {
                    return id
                }
            } else if delta.x > 0 && offset.x > 0.5 || delta.x < 0 && offset.x < geometry.maxOffset.x - 0.5 {
                return id
            }
        }
        return nil
    }

    func offset(_ id: Int) -> CGPoint {
        return offsets[id] ?? .zero
    }

    func needsLayerForFirstScroll(_ id: Int) -> Bool {
        return WidgetScrolling.enabled && resizedClips.contains(id) && offsets[id] == nil
    }

    enum Outcome {
        case ignored
        case moved(Int)
        case unchanged
    }

    func scroll(event: NSEvent, at point: CGPoint, document: LHDocument?, scale: CGFloat, attribute: String) -> Outcome {
        let precise = event.hasPreciseScrollingDeltas
        let unit: CGFloat = precise ? 1 : 16
        let delta = CGPoint(x: event.scrollingDeltaX * unit, y: event.scrollingDeltaY * unit)
        let phase = event.phase, momentum = event.momentumPhase
        let phased = !phase.isEmpty || !momentum.isEmpty
        if phase.contains(.began) || phase.contains(.mayBegin) {
            gesture = Gesture()
        } else if phased && gesture == nil {
            gesture = Gesture()
        }
        let finished = momentum.contains(.ended) || momentum.contains(.cancelled) || phase.contains(.cancelled)
        defer {
            if finished {
                gesture = nil
            }
        }
        let id: Int
        if phased {
            if gesture?.decided == false {
                guard delta != .zero else {
                    return .unchanged
                }
                let target = scroller(at: point, delta: delta)
                gesture?.decided = true
                gesture?.id = target
            }
            guard let locked = gesture?.id else {
                return .ignored
            }
            id = locked
        } else {
            guard let found = scroller(at: point, delta: delta) else {
                return .ignored
            }
            id = found
        }
        guard let geometry = geometries[id] else {
            gesture?.id = nil
            return .ignored
        }
        let current = offsets[id] ?? .zero
        let clamped = geometry.clamp(CGPoint(x: current.x - delta.x, y: current.y - delta.y))
        offsets[id] = document?.setScrollOffset(clamped, attribute: attribute, value: String(id)) ?? clamped
        if let layer = layers[id] {
            let shown = offsets[id] ?? clamped
            layer.clipsRasterToViewport = false
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            layer.show(shown)
            layer.flashIndicator(shown)
            CATransaction.commit()
            if let document = document {
                layer.ensureTiles(document: document, scale: scale, attribute: attribute, velocity: -delta.y * 8)
            }
        }
        return offsets[id] == current ? .unchanged : .moved(id)
    }

    func rememberEditorOffset(_ id: Int, _ offset: CGPoint, document: LHDocument?) -> Bool {
        guard offset.x.isFinite, offset.y.isFinite else { return false }
        let next = CGPoint(x: max(0, offset.x), y: max(0, offset.y))
        guard offsets[id] != next else { return false }
        offsets[id] = next
        _ = document?.setScrollOffset(next, attribute: WidgetDOM.idAttribute, value: String(id))
        return true
    }

    func setOffset(_ id: Int, _ offset: CGPoint, document: LHDocument?) -> Bool {
        guard let geometry = geometries[id] else {
            return false
        }
        let clamped = geometry.clamp(offset)
        let previous = offsets[id] ?? .zero
        offsets[id] = document?.setScrollOffset(clamped, attribute: WidgetDOM.idAttribute, value: String(id)) ?? clamped
        if let layer = layers[id] {
            layer.clipsRasterToViewport = false
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            layer.show(offsets[id] ?? clamped)
            layer.flashIndicator(offsets[id] ?? clamped)
            CATransaction.commit()
        }
        return previous != offsets[id]
    }

    func draw(into ctx: CGContext, document: LHDocument, scale: CGFloat) {
        for layer in layers.values {
            layer.draw(into: ctx, document: document, scale: scale)
        }
    }
}

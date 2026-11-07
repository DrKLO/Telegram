// The feed's data and widget lifecycle (main thread): which bubbles exist,
// which of them currently own a WidgetHost, their heights (live, and remembered
// across launches), the snapshots of bubbles whose runtime was dropped, and the
// per-widget timings the report is made of. The view controller only decides
// *when* (display, prefetch, trim); the policy of what a bubble needs is here.

#if os(iOS)
import Foundation
import UIKit
import WidgetSandbox

enum FeedMessageKind {
    case text(String)
    case widget(group: String, file: String)
}

struct FeedMessage {
    let id: Int
    let kind: FeedMessageKind
    var path: String? {
        if case .widget(let g, let f) = kind { return "\(g)/\(f)" }
        return nil
    }
}

/// One widget bubble's state over its whole life in the feed (hosts come and go, this stays).
final class FeedWidgetEntry {
    let message: FeedMessage
    var host: WidgetHost?
    var diagnostics = WidgetDiagnostics()
    var createdAt: CFTimeInterval = 0
    var displayedAt: CFTimeInterval?
    var isDisplayed = false
    /// Latest timings of the *first* host (what a reader experienced on the way down); re-creations are counted, not re-timed.
    var rendererReadyAt: CFTimeInterval?
    var firstContentAt: CFTimeInterval?
    var loadedAt: CFTimeInterval?
    var prewarmed = ""
    var dead: String?
    var errors = 0
    var jsMs = 0.0
    var heightJumps = 0
    var cachedHeight: CGFloat?
    var height: CGFloat
    /// The edge that stays put on screen when `height` changes (the widget's `setResizeAnchor`), from the same diagnostics.
    var anchor = WidgetResizeAnchor.top
    var recreated = 0
    var snapshot: UIImage?
    var snapshotData: Data?
    var snapshotOk = false
    var snapshotFailed = false
    /// "t=<ms>:<height><flags>" for the first height reports (d = displayed, l = loaded, r = recreated host, e = empty document, x = ignored): the jump diagnosis.
    var heightTrace: [String] = []
    /// The hostile bubble (corpus/attacks): stopped or contained by a runtime limit, never re-run once killed.
    let hostile: Bool
    /// The current boot (hosts come and go, the report above keeps the first): what the bar's per-setting samples are taken from, like the web feed's overlay.
    var bootAt: CFTimeInterval = 0
    var bootShownAt: CFTimeInterval?
    var bootContentAt: CFTimeInterval?
    var bootPooled = false
    var bootMissCounted = false
    var bootRenderer = DemoRenderer.Kind.webview
    /// What the validator and the shim refused, and how often the CPU quota deferred a task (from the diagnostics' counters).
    var blocked = 0
    var throttled = 0

    init(message: FeedMessage, height: CGFloat, cachedHeight: CGFloat?, hostile: Bool = false) {
        self.message = message
        self.height = height
        self.cachedHeight = cachedHeight
        self.hostile = hostile
    }
}

protocol FeedModelDelegate: AnyObject {
    /// A widget bubble's height changed (a layout pass is due); `displayed` says whether the reader sees it.
    func feedModel(_ model: FeedModel, heightChanged id: Int, from old: CGFloat, to new: CGFloat)
    /// Status caption or snapshot of a bubble changed (the cell redraws its caption).
    func feedModel(_ model: FeedModel, statusChanged id: Int)
}

final class FeedModel {
    /// Per boot, for the bar's overlay: host creation → first content, and bubble shown → first content (negative = ready before the reader got there); one set per pool setting.
    struct Samples { var boot: [Double] = []; var shown: [Double] = []; var misses = 0 }
    private(set) var samples: [Bool: Samples] = [true: Samples(), false: Samples()]
    /// The bar's live switches: the renderer behind the seam and whether new hosts take from the pools. Switching the renderer re-creates every live host; the pool setting applies to hosts created from then on.
    private(set) var renderer: DemoRenderer.Kind
    private(set) var poolsOn: Bool
    private(set) var stopped = 0
    var hostOptions: WidgetHost.Options { var o = config.hostOptions; o.pools = poolsOn; return o }
    let config: FeedConfig
    let messages: [FeedMessage]
    let widgetWidth: CGFloat
    let theme: String
    private(set) var entries: [Int: FeedWidgetEntry] = [:]
    weak var delegate: FeedModelDelegate?
    /// Where prefetched (not yet displayed) widget views live when `config.park` is on.
    weak var parkingView: UIView?
    let startedAt = CACurrentMediaTime()
    private(set) var hostsCreated = 0, hostsDropped = 0, snapshotsTaken = 0, snapshotsFailed = 0
    private var heightCache: [String: CGFloat]
    private static let heightCacheKey = "feed.heights"
    static let defaultHeight: CGFloat = 160

    init(config: FeedConfig, widgetWidth: CGFloat, theme: String) {
        self.config = config
        self.widgetWidth = widgetWidth
        self.theme = theme
        heightCache = (UserDefaults.standard.dictionary(forKey: Self.heightCacheKey) as? [String: Double])?.mapValues { CGFloat($0) } ?? [:]
        renderer = DemoRenderer.Kind(rawValue: config.renderer) ?? DemoRenderer.kind
        poolsOn = config.pools
        messages = Self.makeMessages(count: config.count, corpus: config.corpus, hostile: config.hostile)
        for m in messages {
            guard let path = m.path else { continue }
            let hostile = path.hasPrefix("attacks/")
            let cached = config.heightCache && !hostile ? heightCache[path] : nil
            entries[m.id] = FeedWidgetEntry(message: m, height: cached ?? Self.defaultHeight, cachedHeight: cached, hostile: hostile)
        }
    }

    /// The web feed's batch order and seeds (src/feed.ts `BATCH_ORDER`, `shuffled(…, 7 + i)`): the same files in the same order on both.
    private static let showcaseOrder = ["travel", "commerce", "life", "work"]
    /// The web feed's text bubbles, in its cadence (one before every other widget).
    private static let texts = ["Here, have a look at this one", "can you make it a card?", "ok that works", "and the live one?", "nice", "try scrolling faster",
                                "this is the heavy batch", "same file, other renderer", "what does the dashboard look like now", "ship it", "one more", "let me check the poll"]

    /// Seeded shuffle, the web feed's LCG with the same seed → the same order.
    private static func shuffled<T>(_ xs: [T], seed: UInt32) -> [T] {
        var out = xs
        var s = seed
        guard out.count > 1 else { return out }
        for i in stride(from: out.count - 1, to: 0, by: -1) {
            s = s &* 1664525 &+ 1013904223
            let j = Int(s % UInt32(i + 1))
            out.swapAt(i, j)
        }
        return out
    }

    /// `showcase`: exactly the web feed's order — each batch shuffled with its seed (travel 7, commerce 8, life 9, work 10), then one of each batch in turn,
    /// the web's text bubble before every other widget; `classic`: compat → generated → Telegram demos, every widget announced by a numbered text bubble;
    /// `custom`: the Custom HTML draft in every bubble, announced like `classic`.
    /// Cycled when `count` exceeds the corpus. `hostile` (an attacks/ file) takes the third widget bubble, on the first screen, like the web feed.
    private static func makeMessages(count: Int, corpus: String, hostile: String) -> [FeedMessage] {
        var files: [(String, String)] = []
        let showcase = corpus == "showcase"
        if corpus == "custom" {
            // The Custom HTML screen's draft in every bubble (Corpus.html): a widget under test in a real list.
            files = [("custom", "custom.html")]
        } else if showcase {
            let batches = showcaseOrder.enumerated().map { i, g in shuffled(Corpus.files(in: "showcase/\(g)").map { ("showcase/\(g)", $0) }, seed: UInt32(7 + i)) }
            for i in 0..<(batches.map(\.count).max() ?? 0) { for b in batches where i < b.count { files.append(b[i]) } }
        } else {
            for g in ["compat"] + Corpus.generatedGroups + Corpus.htmlbubblesGroups {
                for f in Corpus.files(in: g) { files.append((g, f)) }
            }
        }
        guard !files.isEmpty else { return [] }
        var out: [FeedMessage] = []
        var id = 0
        for i in 0..<count {
            let (g, f) = i == 2 && hostile != "none" ? ("attacks", hostile + ".html") : files[i % files.count]
            if showcase {
                if i % 2 == 0 { out.append(FeedMessage(id: id, kind: .text(texts[i % texts.count]))); id += 1 }
            } else {
                out.append(FeedMessage(id: id, kind: .text("\(i + 1). \((f as NSString).deletingPathExtension) — \(g.split(separator: "/").last ?? "")"))); id += 1
            }
            out.append(FeedMessage(id: id, kind: .widget(group: g, file: f))); id += 1
        }
        return out
    }

    func entry(_ id: Int) -> FeedWidgetEntry? { entries[id] }

    // MARK: hosts

    /// A host for the bubble, created now when it has none. Returns the entry.
    @discardableResult
    func ensureHost(_ id: Int) -> FeedWidgetEntry? {
        guard let e = entries[id] else { return nil }
        // Killed once (a runtime limit): never re-run — a product keeps the frozen fallback, not a second attempt.
        if e.host != nil || e.dead != nil { return e }
        guard case .widget(let g, let f) = e.message.kind else { return e }
        let html = Corpus.html(group: g, file: f)
        let t0 = CACurrentMediaTime()
        let factory = DemoRenderer.factory(for: renderer)
        guard let host = MainWork.shared.measure("host-create", { try? WidgetHost(html: html, theme: DemoTheme.make(dark: theme == "dark"), width: widgetWidth, options: hostOptions, rendererFactory: factory) }) else { return e }
        hostsCreated += 1
        if e.createdAt == 0 { e.createdAt = t0 } else { e.recreated += 1 }
        let firstHost = e.recreated == 0
        e.host = host
        e.bootAt = t0
        e.bootShownAt = e.isDisplayed ? t0 : nil
        e.bootContentAt = nil
        e.bootMissCounted = false
        e.bootPooled = poolsOn
        e.bootRenderer = renderer
        host.onChange = { [weak self, weak e] d in
            guard let self, let e else { return }
            self.apply(d, to: e, firstHost: firstHost)
        }
        if config.park, !e.isDisplayed, let parkingView {
            host.view.frame = CGRect(x: 0, y: 0, width: widgetWidth, height: e.height)
            parkingView.addSubview(host.view)
        }
        return e
    }

    private func apply(_ d: WidgetDiagnostics, to e: FeedWidgetEntry, firstHost: Bool) {
        e.diagnostics = d
        let now = CACurrentMediaTime()
        if firstHost {
            if e.rendererReadyAt == nil, d.rendererReadyMs != nil { e.rendererReadyAt = now }
            if e.firstContentAt == nil, d.firstContentMs != nil { e.firstContentAt = now }
            if e.loadedAt == nil, d.loaded { e.loadedAt = now; e.prewarmed = d.prewarmed; scheduleSnapshot(e) }
        }
        if d.loaded { e.jsMs = d.stats.jsMs }
        e.errors = d.logs.filter { $0.level == "error" }.count
        e.blocked = d.diags.filter { $0.key.contains("blocked") || $0.key.contains("dropped") }.values.reduce(0) { $0 + $1.count }
        e.throttled = d.diags.filter { $0.key.contains("throttl") || $0.key.contains("defer") || $0.key.contains("cpu") }.values.reduce(0) { $0 + $1.count }
        // The bar's per-setting samples: this boot's host creation → first content, and shown → first content when the bubble was already on screen.
        if e.bootContentAt == nil, d.firstContentMs != nil {
            e.bootContentAt = now
            samples[e.bootPooled]?.boot.append((now - e.bootAt) * 1000)
            if let shown = e.bootShownAt { samples[e.bootPooled]?.shown.append((now - shown) * 1000) }
        }
        if d.loaded, e.bootPooled, !e.bootMissCounted { e.bootMissCounted = true; if d.prewarmed.isEmpty { samples[true]?.misses += 1 } }
        if let dead = d.dead, e.dead == nil { e.dead = dead; stopped += 1; delegate?.feedModel(self, statusChanged: e.message.id) }
        // The empty document reports a height too (a pooled renderer, before any ops): only content counts.
        // With a remembered height the bubble keeps it until the widget is loaded: markup-then-script widgets
        // would otherwise shrink and grow back while their scripts build the content.
        let settled = d.rendered || e.cachedHeight == nil
        if d.height > 0, e.heightTrace.count < 16 {
            let flags = (e.isDisplayed ? "d" : "") + (d.loaded ? "l" : "") + (firstHost ? "" : "r") + (d.firstContentMs == nil ? "e" : "") + (settled ? "" : "x")
            let entry = "t=\(Int((now - startedAt) * 1000)):\(Int(d.height))\(flags)"
            if e.heightTrace.last != entry { e.heightTrace.append(entry) }
        }
        e.anchor = d.resizeAnchor
        if d.height > 0, d.firstContentMs != nil, settled, d.height != e.height {
            let old = e.height
            e.height = d.height
            if e.isDisplayed, e.loadedAt != nil || e.cachedHeight != nil { e.heightJumps += 1 }
            if let path = e.message.path { heightCache[path] = d.height; persistHeightsSoon() }
            delegate?.feedModel(self, heightChanged: e.message.id, from: old, to: d.height)
        }
        delegate?.feedModel(self, statusChanged: e.message.id)
    }

    func markDisplayed(_ id: Int) {
        guard let e = entries[id] else { return }
        e.isDisplayed = true
        let now = CACurrentMediaTime()
        if e.displayedAt == nil { e.displayedAt = now }
        // First sight of this boot: if the content was already there the reader never waited (a negative sample, like the web overlay's).
        if e.bootShownAt == nil, e.host != nil { e.bootShownAt = now; if let c = e.bootContentAt { samples[e.bootPooled]?.shown.append((c - now) * 1000) } }
        e.host?.view.removeFromSuperview()
        e.host?.setVisible(true)
    }

    func markHidden(_ id: Int) {
        guard let e = entries[id] else { return }
        e.isDisplayed = false
        e.host?.setVisible(false)
    }

    /// Drops the hosts of bubbles outside `keep` (indices into `messages`), keeping a snapshot when one can be taken.
    /// `liveSnapshot`: ask the renderer for a fresh picture (not while a flick is in flight: a snapshot is a
    /// round trip to a busy web process); otherwise the one taken after `loaded` stays.
    func trim(keep: ClosedRange<Int>, liveSnapshot: Bool = true) {
        guard config.dropFarAway else { return }
        for (idx, m) in messages.enumerated() where !keep.contains(idx) {
            guard let e = entries[m.id], let host = e.host else { continue }
            drop(e, host: host, liveSnapshot: liveSnapshot)
        }
    }

    private func drop(_ e: FeedWidgetEntry, host: WidgetHost, liveSnapshot: Bool) {
        e.host = nil
        hostsDropped += 1
        host.onChange = nil
        if !liveSnapshot, e.snapshotOk || e.loadedAt == nil {
            host.destroy()
            delegate?.feedModel(self, statusChanged: e.message.id)
            return
        }
        // A live snapshot of the page first (WebKit can usually render a detached view); the earlier one stays otherwise.
        host.snapshot { [weak self, weak e] image in
            guard let self, let e else { host.destroy(); return }
            if let image { self.store(image, in: e) } else if e.snapshot == nil, e.snapshotData == nil { e.snapshotFailed = true; self.snapshotsFailed += 1 }
            host.destroy()
            self.delegate?.feedModel(self, statusChanged: e.message.id)
        }
    }

    /// A snapshot while the widget is on screen and settled, so a later drop has a picture even if WebKit refuses to snapshot a detached view.
    private func scheduleSnapshot(_ e: FeedWidgetEntry) {
        guard config.dropFarAway else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self, weak e] in
            guard let self, let e, let host = e.host, e.isDisplayed else { return }
            host.snapshot { image in
                if let image { self.store(image, in: e) } else { e.snapshotFailed = true; self.snapshotsFailed += 1 }
            }
        }
    }

    private func store(_ image: UIImage, in e: FeedWidgetEntry) {
        snapshotsTaken += 1
        e.snapshotOk = true
        e.snapshot = image
        // Keep the compressed bytes, not the bitmap: 180 bubbles × 4 MB would not fit.
        DispatchQueue.global(qos: .utility).async { [weak e] in
            let data = image.jpegData(compressionQuality: 0.75)
            DispatchQueue.main.async { e?.snapshotData = data; e?.snapshot = nil }
        }
    }

    /// Decoded snapshots of the bubbles near the viewport (a few MB each); the rest stay JPEG.
    private var decoded: [Int: UIImage] = [:]
    private var decodedOrder: [Int] = []
    private var decoding: Set<Int> = []

    /// The decoded snapshot when it is at hand; otherwise nil now and `ready` later from a background decode
    /// (the main thread never decodes a JPEG while scrolling).
    func snapshotImage(_ e: FeedWidgetEntry, ready: @escaping (UIImage) -> Void) -> UIImage? {
        let id = e.message.id
        if let s = e.snapshot { return s }
        if let s = decoded[id] { return s }
        guard let data = e.snapshotData, !decoding.contains(id) else { return nil }
        decoding.insert(id)
        let scale = UIScreen.main.scale
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let image = UIImage(data: data, scale: scale)?.preparingForDisplay()
            DispatchQueue.main.async {
                guard let self else { return }
                self.decoding.remove(id)
                guard let image else { return }
                self.decoded[id] = image
                self.decodedOrder.append(id)
                while self.decodedOrder.count > 8 { self.decoded.removeValue(forKey: self.decodedOrder.removeFirst()) }
                ready(image)
            }
        }
        return nil
    }

    // MARK: height cache

    private var persistScheduled = false
    private func persistHeightsSoon() {
        if persistScheduled { return }
        persistScheduled = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [self] in
            persistScheduled = false
            UserDefaults.standard.set(heightCache.mapValues { Double($0) }, forKey: Self.heightCacheKey)
        }
    }

    static func clearHeightCache() { UserDefaults.standard.removeObject(forKey: heightCacheKey) }
    var cachedHeights: Int { heightCache.count }

    // MARK: report

    func report(hitches: [HitchTracker.Phase], memoryStart: Double, memoryPeak: Double, build: String) -> FeedReport {
        let ms = { (t: CFTimeInterval?) -> Double? in t.map { (($0 - self.startedAt) * 1000).rounded() } }
        var rows: [FeedWidgetRow] = []
        var s = FeedReport.Summary()
        var loaded: [Double] = [], first: [Double] = [], d2c: [Double] = [], d2l: [Double] = [], ready: [Double] = [], firstScreen: [Double] = []
        for m in messages {
            guard let e = entries[m.id], let path = m.path else { continue }
            let created = ms(e.createdAt == 0 ? nil : e.createdAt)
            let displayed = ms(e.displayedAt)
            let readyMs = (e.rendererReadyAt != nil && e.createdAt > 0) ? ((e.rendererReadyAt! - e.createdAt) * 1000).rounded() : nil
            let contentMs = (e.firstContentAt != nil && e.createdAt > 0) ? ((e.firstContentAt! - e.createdAt) * 1000).rounded() : nil
            let loadedMs = (e.loadedAt != nil && e.createdAt > 0) ? ((e.loadedAt! - e.createdAt) * 1000).rounded() : nil
            let d2cMs = (e.firstContentAt != nil && e.displayedAt != nil) ? ((e.firstContentAt! - e.displayedAt!) * 1000).rounded() : nil
            let d2lMs = (e.loadedAt != nil && e.displayedAt != nil) ? ((e.loadedAt! - e.displayedAt!) * 1000).rounded() : nil
            rows.append(FeedWidgetRow(path: path, createdAt: created ?? -1, displayedAt: displayed, rendererReadyMs: readyMs, firstContentMs: contentMs, loadedMs: loadedMs,
                                      displayToContentMs: d2cMs, displayToLoadedMs: d2lMs, prewarmed: e.prewarmed, dead: e.dead, errors: e.errors, jsMs: e.jsMs.rounded(),
                                      heightJumps: e.heightJumps, cachedHeight: e.cachedHeight.map(Double.init), finalHeight: Double(e.height), recreated: e.recreated, snapshot: e.snapshotOk, heightTrace: e.heightTrace))
            s.widgets += 1
            if e.loadedAt != nil { s.loaded += 1 }
            if e.dead != nil { s.dead += 1 }
            if e.errors > 0 { s.withErrors += 1 }
            s.layoutJumps += e.heightJumps
            if let v = loadedMs { loaded.append(v) }
            if let v = contentMs { first.append(v) }
            if let v = readyMs { ready.append(v) }
            if let v = d2cMs { d2c.append(v); if let shown = displayed, shown < 1000 { firstScreen.append(v) } }
            if let v = d2lMs { d2l.append(v) }
        }
        s.hostsCreated = hostsCreated
        s.hostsDropped = hostsDropped
        s.snapshotsTaken = snapshotsTaken
        s.snapshotsFailed = snapshotsFailed
        s.loadedMsMedian = Percentile.median(loaded); s.loadedMsP90 = Percentile.p90(loaded)
        s.firstContentMsMedian = Percentile.median(first); s.firstContentMsP90 = Percentile.p90(first)
        s.displayToContentMsMedian = Percentile.median(d2c); s.displayToContentMsP90 = Percentile.p90(d2c)
        s.displayToLoadedMsMedian = Percentile.median(d2l); s.displayToLoadedMsP90 = Percentile.p90(d2l)
        s.rendererReadyMsMedian = Percentile.median(ready)
        s.firstScreenWidgets = firstScreen.count
        s.firstScreenDisplayToContentMsMedian = Percentile.median(firstScreen)
        s.firstScreenDisplayToContentMsMax = firstScreen.max() ?? 0
        s.memoryStartMB = memoryStart.rounded(); s.memoryPeakMB = memoryPeak.rounded(); s.memoryEndMB = ProcessMemory.footprintMB.rounded()
        s.totalSeconds = ((CACurrentMediaTime() - startedAt) * 10).rounded() / 10
        s.build = build
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm"
        return FeedReport(config: config, device: UIDevice.current.name + " · " + UIDevice.current.systemVersion, date: f.string(from: Date()), summary: s,
                          pools: WidgetWarmup.status, hitches: hitches, mainThreadWork: MainWork.shared.rounded, widgets: rows)
    }

    func destroyAll() {
        for e in entries.values {
            e.host?.onChange = nil
            e.host?.destroy()
            e.host = nil
        }
    }

    // MARK: the bar's switches

    /// Every live bubble swaps its renderer: the same HTML boots again behind the new seam (the caller re-creates the visible ones at once, the rest when scrolled to); the height is remembered so nothing jumps. Killed bubbles stay stopped.
    func switchRenderer(to kind: DemoRenderer.Kind) {
        guard kind != renderer else { return }
        renderer = kind
        for e in entries.values {
            guard let host = e.host else { continue }
            e.host = nil
            hostsDropped += 1
            host.onChange = nil
            host.destroy()
            delegate?.feedModel(self, statusChanged: e.message.id)
        }
    }

    /// Hosts created from now on take from the pools (filled now) or boot everything themselves (the pools are drained).
    func setPools(_ on: Bool, parkingView: UIView?) {
        guard on != poolsOn else { return }
        poolsOn = on
        config.applyGlobals(widgetWidth: widgetWidth, parkingView: parkingView, pools: on)
    }

    var live: Int { entries.values.filter { $0.host != nil }.count }
    var blockedTotal: Int { entries.values.reduce(0) { $0 + $1.blocked } }
    var throttledTotal: Int { entries.values.reduce(0) { $0 + $1.throttled } }
}
#endif

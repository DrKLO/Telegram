// The chat feed itself: a UICollectionView of bubbles (text and widget) with
// the list-side lifecycle a messenger needs — hosts created on display (or on
// prefetch), hidden when scrolled away, dropped beyond a few screens with a
// snapshot left behind, heights laid out from the cache before the widget
// renders and adjusted without moving what the reader is looking at. The
// autorun scrolls the feed like a reader (one screen, pause) or a flick,
// records hitches and timings, and writes feed-report.json.

#if os(iOS)
import UIKit
import WidgetSandbox

final class ChatFeedViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout, UICollectionViewDataSourcePrefetching, FeedModelDelegate {
    /// Changed by the feed bar (corpus, count, hostile bubble: a rebuild; renderer and pools: live, through the model).
    private(set) var config: FeedConfig
    let autorun: Bool
    let dark: Bool
    private(set) var model: FeedModel!
    private var collectionView: UICollectionView!
    private let layout = UICollectionViewFlowLayout()
    private let statusLabel = UILabel()
    private let parkingView = UIView()
    private let hitches = HitchTracker()
    private var memoryStart = 0.0, memoryPeak = 0.0
    private var memoryTimer: Timer?
    private var textHeights: [Int: CGFloat] = [:]
    private var scrollingProgrammatically = false
    private var trimWork: DispatchWorkItem?
    private var lastTrimAt: CFTimeInterval = 0
    private var lastPrefetchAt: CFTimeInterval = 0
    private var lastOffsetY: CGFloat = 0
    /// Height changes since the last layout pass: one invalidation per run-loop turn, whatever the number of bubbles that resized.
    private var pendingHeightDeltas: [Int: CGFloat] = [:]
    private var invalidationScheduled = false
    var onFinished: ((String) -> Void)?
    private(set) var finishedSummary: String?

    static let horizontalInset: CGFloat = 12
    /// The bubble is the widget's canvas, as on a client: no padding of its own (the widget's body margin is the inset),
    /// the radius clips what the widget paints, the colour is the one DemoTheme passes as bg_color.
    static let bubblePadding: CGFloat = 0
    var bubblePadding: CGFloat { Self.bubblePadding }
    static let captionHeight: CGFloat = 14
    static let itemSpacing: CGFloat = 8

    init(config: FeedConfig, autorun: Bool, dark: Bool) {
        self.config = config
        self.autorun = autorun
        self.dark = dark
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError() }

    var bubbleWidth: CGFloat { min(view.bounds.width - 2 * Self.horizontalInset - 40, 360) }
    var widgetWidth: CGFloat { bubbleWidth - 2 * bubblePadding }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = dark ? .black : .secondarySystemBackground
        overrideUserInterfaceStyle = dark ? .dark : .light

        // Parked web views: in the window (WebKit runs layout only for views on screen), but 1 pt large and transparent.
        parkingView.frame = CGRect(x: 0, y: view.bounds.height - 1, width: 1, height: 1)
        parkingView.clipsToBounds = true
        parkingView.alpha = 0.02
        parkingView.isUserInteractionEnabled = false
        parkingView.autoresizingMask = [.flexibleTopMargin]
        view.addSubview(parkingView)

        layout.scrollDirection = .vertical
        layout.minimumLineSpacing = Self.itemSpacing
        layout.sectionInset = UIEdgeInsets(top: 8, left: Self.horizontalInset, bottom: 8, right: Self.horizontalInset)
        collectionView = UICollectionView(frame: view.bounds, collectionViewLayout: layout)
        collectionView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        collectionView.backgroundColor = .clear
        collectionView.register(TextBubbleCell.self, forCellWithReuseIdentifier: "text")
        collectionView.register(WidgetBubbleCell.self, forCellWithReuseIdentifier: "widget")
        collectionView.dataSource = self
        collectionView.delegate = self
        if config.prefetch { collectionView.prefetchDataSource = self }
        collectionView.alwaysBounceVertical = true
        view.addSubview(collectionView)

        statusLabel.font = .monospacedSystemFont(ofSize: 9, weight: .medium)
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .secondaryLabel
        statusLabel.backgroundColor = (dark ? UIColor.black : UIColor.systemBackground).withAlphaComponent(0.9)
        view.addSubview(statusLabel)

        RendererPool.shared.canRefill = { [weak self] in
            guard let self, let cv = collectionView else { return true }
            return !(cv.isDragging || cv.isDecelerating || scrollingProgrammatically)
        }
        buildModel()
        memoryTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            memoryPeak = max(memoryPeak, ProcessMemory.footprintMB)
            if !autorun || finishedSummary == nil { updateStatus() }
        }
        updateStatus()
    }

    /// Globals first (pools fill from the parking view), then the model; the metrics start over.
    private func buildModel() {
        MainWork.shared.reset()
        WidgetWarmup.mainThreadWork = { MainWork.shared.record($0, $1) }
        let w = widgetWidth
        config.applyGlobals(widgetWidth: w, parkingView: parkingView)
        model = FeedModel(config: config, widgetWidth: w, theme: dark ? "dark" : "light")
        model.delegate = self
        model.parkingView = config.park ? parkingView : nil
        memoryStart = ProcessMemory.footprintMB
        memoryPeak = memoryStart
        textHeights = [:]
        finishedSummary = nil
    }

    // MARK: the feed bar (the web feed's switches, src/feed.ts)

    /// Every live bubble swaps its renderer in place; the visible ones boot again at once, parked ones when scrolled to.
    func setRenderer(_ kind: DemoRenderer.Kind) {
        model.switchRenderer(to: kind)
        config.renderer = kind.rawValue
        for case let cell as WidgetBubbleCell in collectionView.visibleCells {
            guard let e = model.ensureHost(cell.messageId) else { continue }
            model.markDisplayed(e.message.id)
            if let host = e.host { cell.attach(host.view) }
            cell.caption.text = caption(e)
        }
        updateStatus()
    }

    /// Hosts created from now on take from the pools or boot cold; the overlay keeps one column per setting.
    func setPools(_ on: Bool) {
        model.setPools(on, parkingView: parkingView)
        config.pools = on
        updateStatus()
    }

    func setHostile(_ name: String) { config.hostile = name; rebuild() }
    func setCorpus(_ corpus: String) { config.corpus = corpus; rebuild() }
    func setCount(_ n: Int) { config.count = n; rebuild() }

    /// Drop every host, clear the metrics, scroll to the top (the renderer and the pool setting stay).
    func rebuild() {
        scrollTask?.cancel()
        scrollTask = nil
        running = false
        hitches.stop()
        model.destroyAll()
        config.pools = model.poolsOn
        config.renderer = model.renderer.rawValue
        buildModel()
        collectionView.reloadData()
        collectionView.setContentOffset(CGPoint(x: 0, y: -collectionView.adjustedContentInset.top), animated: false)
        updateStatus()
    }

    /// The scripted scroll like the autorun: one screen, pause, repeat to the bottom and back; a second tap stops it.
    func toggleAutoScroll() {
        if let t = scrollTask { t.cancel(); scrollTask = nil; running = false; hitches.stop(); updateStatus(); return }
        scrollTask = Task { await run() }
    }
    private var scrollTask: Task<Void, Never>?
    var isAutoScrolling: Bool { scrollTask != nil && running }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let inset = view.safeAreaInsets
        let size = statusLabel.sizeThatFits(CGSize(width: view.bounds.width - 16, height: 200))
        statusLabel.frame = CGRect(x: 8, y: inset.top + 2, width: view.bounds.width - 16, height: size.height + 4)
        collectionView.contentInset = UIEdgeInsets(top: statusLabel.frame.maxY - inset.top + 4, left: 0, bottom: 0, right: 0)
        collectionView.verticalScrollIndicatorInsets = collectionView.contentInset
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if autorun, finishedSummary == nil, !running { Task { await run() } }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        memoryTimer?.invalidate()
        hitches.stop()
    }

    deinit {
        model?.destroyAll()
        RendererPool.shared.canRefill = { true }
    }

    // MARK: data source

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { model.messages.count }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let m = model.messages[indexPath.item]
        switch m.kind {
        case .text(let text):
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "text", for: indexPath) as! TextBubbleCell
            cell.configure(text: text, bubbleWidth: bubbleWidth)
            return cell
        case .widget:
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "widget", for: indexPath) as! WidgetBubbleCell
            cell.messageId = m.id
            if let e = model.entry(m.id) { MainWork.shared.measure("cell-configure") { cell.configure(entry: e, snapshot: snapshot(e, for: cell), bubbleWidth: bubbleWidth, padding: bubblePadding, caption: caption(e)) } }
            return cell
        }
    }

    func collectionView(_ collectionView: UICollectionView, layout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        let m = model.messages[indexPath.item]
        let width = collectionView.bounds.width - 2 * Self.horizontalInset
        switch m.kind {
        case .text(let text):
            if let h = textHeights[m.id] { return CGSize(width: width, height: h) }
            let h = TextBubbleCell.height(for: text, bubbleWidth: bubbleWidth)
            textHeights[m.id] = h
            return CGSize(width: width, height: h)
        case .widget:
            let h = model.entry(m.id)?.height ?? FeedModel.defaultHeight
            return CGSize(width: width, height: WidgetBubbleCell.cellHeight(widgetHeight: h, padding: bubblePadding))
        }
    }

    // MARK: display lifecycle

    func collectionView(_ collectionView: UICollectionView, willDisplay cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let wcell = cell as? WidgetBubbleCell, let e = model.ensureHost(wcell.messageId) else { return }
        model.markDisplayed(e.message.id)
        if let host = e.host { wcell.attach(host.view) } else { wcell.showSnapshot(snapshot(e, for: wcell)) }
        wcell.caption.text = caption(e)
    }

    /// The decoded snapshot now, or a placeholder and the image once decoded off the main thread (if the cell still shows that bubble).
    private func snapshot(_ e: FeedWidgetEntry, for cell: WidgetBubbleCell) -> UIImage? {
        let id = e.message.id
        return model.snapshotImage(e) { [weak cell, weak self] image in
            guard let cell, cell.messageId == id, let e = self?.model.entry(id), e.host == nil else { return }
            cell.showSnapshot(image)
        }
    }

    func collectionView(_ collectionView: UICollectionView, didEndDisplaying cell: UICollectionViewCell, forItemAt indexPath: IndexPath) {
        guard let wcell = cell as? WidgetBubbleCell else { return }
        wcell.detach()
        model.markHidden(wcell.messageId)
        scheduleTrim()
    }

    func collectionView(_ collectionView: UICollectionView, prefetchItemsAt indexPaths: [IndexPath]) {
        for ip in indexPaths where ip.item < model.messages.count {
            if case .widget = model.messages[ip.item].kind { model.ensureHost(model.messages[ip.item].id) }
        }
    }

    /// Hosts further than `keepScreens` screens from the visible rect are dropped (snapshot kept), once scrolling has settled for a moment.
    private func scheduleTrim() {
        guard config.dropFarAway else { return }
        trimWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.trimNow() }
        trimWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4, execute: work)
    }

    private func trimNow() {
        let visible = collectionView.bounds
        let margin = visible.height * config.keepScreens
        let keepRect = visible.insetBy(dx: 0, dy: -margin)
        let attrs = layout.layoutAttributesForElements(in: keepRect) ?? []
        guard let lo = attrs.map(\.indexPath.item).min(), let hi = attrs.map(\.indexPath.item).max() else { return }
        let scrolling = collectionView.isDragging || collectionView.isDecelerating || scrollingProgrammatically
        model.trim(keep: lo...hi, liveSnapshot: !scrolling)
    }

    // MARK: FeedModelDelegate

    func feedModel(_ model: FeedModel, heightChanged id: Int, from old: CGFloat, to new: CGFloat) {
        let delta = WidgetBubbleCell.cellHeight(widgetHeight: new, padding: bubblePadding) - WidgetBubbleCell.cellHeight(widgetHeight: old, padding: bubblePadding)
        pendingHeightDeltas[id, default: 0] += delta
        if invalidationScheduled { return }
        invalidationScheduled = true
        DispatchQueue.main.async { [weak self] in self?.applyPendingHeights() }
    }

    private func applyPendingHeights() {
        invalidationScheduled = false
        let deltas = pendingHeightDeltas
        pendingHeightDeltas = [:]
        guard !deltas.isEmpty else { return }
        let context = UICollectionViewFlowLayoutInvalidationContext()
        context.invalidateFlowLayoutDelegateMetrics = true
        // Bubbles above the reader's viewport grow or shrink without moving what they see. A bubble on screen keeps
        // its anchored edge (the widget's setResizeAnchor, docs/sdk-v1.md §5.6): `bottom` keeps its bottom edge and the
        // content above moves (the offset takes the delta), `top` keeps its top edge and the content below moves.
        var adjustment: CGFloat = 0
        let viewport = collectionView.bounds
        for (id, delta) in deltas {
            guard let item = model.messages.firstIndex(where: { $0.id == id }),
                  let frame = layout.layoutAttributesForItem(at: IndexPath(item: item, section: 0))?.frame else { continue }
            if frame.maxY <= viewport.minY { adjustment += delta }
            else if frame.minY < viewport.maxY, model.entry(id)?.anchor == .bottom { adjustment += delta }
        }
        // Never past the top of the list (a bottom-anchored bubble that shrinks near it): the rest moves instead.
        adjustment = max(adjustment, -collectionView.adjustedContentInset.top - collectionView.contentOffset.y)
        if adjustment != 0 { context.contentOffsetAdjustment = CGPoint(x: 0, y: adjustment) }
        MainWork.shared.measure("layout-invalidate") {
            UIView.performWithoutAnimation {
                layout.invalidateLayout(with: context)
                collectionView.layoutIfNeeded()
            }
        }
        for id in deltas.keys {
            if let item = model.messages.firstIndex(where: { $0.id == id }), let cell = collectionView.cellForItem(at: IndexPath(item: item, section: 0)) as? WidgetBubbleCell { cell.setNeedsLayout() }
        }
    }

    func feedModel(_ model: FeedModel, statusChanged id: Int) {
        guard let item = model.messages.firstIndex(where: { $0.id == id }), let cell = collectionView.cellForItem(at: IndexPath(item: item, section: 0)) as? WidgetBubbleCell, let e = model.entry(id) else { return }
        cell.caption.text = caption(e)
        if e.host == nil { cell.showSnapshot(snapshot(e, for: cell)) } else if cell.hostView !== e.host?.view { cell.attach(e.host!.view) }
    }

    private func caption(_ e: FeedWidgetEntry) -> String {
        if let dead = e.dead { return "✖ stopped: \(dead.split(separator: "\n").first ?? "")" }
        if e.host == nil { return e.snapshotOk ? "snapshot (runtime dropped)" : "runtime dropped" }
        let boot = "\(e.bootRenderer.title) · \(e.bootPooled ? "pooled" : "cold")"
        if e.hostile { return boot + " · hostile" + (e.throttled > 0 ? " · CPU deferred ×\(e.throttled)" : "") + (e.blocked > 0 ? " · blocked ×\(e.blocked)" : "") + (e.bootContentAt == nil ? " · booting" : "") }
        guard let loaded = e.loadedAt, e.createdAt > 0 else { return boot + (e.bootContentAt != nil ? String(format: " · content in %.0f ms", (e.bootContentAt! - e.bootAt) * 1000) : " · booting") }
        var s = String(format: boot + " · loaded in %.0f ms", (loaded - e.createdAt) * 1000)
        if let d = e.displayedAt { s += String(format: " · %.0f ms after display", (loaded - d) * 1000) }
        if let c = e.firstContentAt { s += String(format: " · content at %.0f ms", (c - e.createdAt) * 1000) }
        if !e.prewarmed.isEmpty { s += " · pool: \(e.prewarmed)" }
        if e.recreated > 0 { s += " · recreated ×\(e.recreated)" }
        if e.heightJumps > 0 { s += " · jumps \(e.heightJumps)" }
        if e.anchor == .bottom { s += " · anchored at the bottom" }
        return s
    }

    private var live: Int { model.entries.values.filter { $0.host != nil }.count }

    /// The overlay, like the web feed's: renderer and counts, the pools, one column of samples per pool setting, memory, what was blocked.
    private func updateStatus() {
        let s = model.samples
        let f = { (v: Double) -> String in v.isNaN ? "—" : String(format: "%@%.0f ms", v < 0 ? "−" : "", abs(v)) }
        let q = { (xs: [Double], p: Double) -> Double in xs.isEmpty ? .nan : Percentile.p(xs, p) }
        let on = s[true] ?? .init(), off = s[false] ?? .init()
        var lines = ["corpus/\(config.corpus) · \(model.renderer.title) renderer · \(config.count) bubbles · \(live) live · \(model.hostsDropped) dropped · \(model.stopped) stopped"]
        lines.append(model.poolsOn ? WidgetWarmup.status : "pool off: every bubble boots its own renderer, engine and validator")
        lines.append(String(format: "%-26@ %10@ %10@", "", "pool on", "pool off"))
        lines.append(String(format: "%-26@ %10d %10d", "bubbles booted", on.boot.count, off.boot.count))
        lines.append(String(format: "%-26@ %10@ %10@", "create → content, median", f(q(on.boot, 0.5)), f(q(off.boot, 0.5))))
        lines.append(String(format: "%-26@ %10@ %10@", "create → content, p90", f(q(on.boot, 0.9)), f(q(off.boot, 0.9))))
        lines.append(String(format: "%-26@ %10@ %10@", "shown → content, median", f(q(on.shown, 0.5)), f(q(off.shown, 0.5))))
        lines.append(String(format: "%-26@ %10@ %10@", "shown → content, p90", f(q(on.shown, 0.9)), f(q(off.shown, 0.9))))
        lines.append(String(format: "%-26@ %10d %10@", "pool empty when taken", on.misses, "—"))
        lines.append(String(format: "mem %.0f MB (peak %.0f) · snapshots %d (failed %d) · cached heights %d · blocked by validator/shim ×%d · CPU deferred ×%d", ProcessMemory.footprintMB, memoryPeak, model.snapshotsTaken, model.snapshotsFailed, model.cachedHeights, model.blockedTotal, model.throttledTotal))
        if let finishedSummary { lines.append(finishedSummary) }
        statusLabel.text = lines.joined(separator: "\n")
        view.setNeedsLayout()
    }

    // MARK: scrolling

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) { scrollingProgrammatically = false }

    /// While a long flick is in flight: hosts for the bubbles ahead in the scroll direction (one screen), and a trim behind, every few hundred ms.
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        let now = CACurrentMediaTime()
        let y = scrollView.contentOffset.y
        let down = y >= lastOffsetY
        lastOffsetY = y
        if config.prefetch, now - lastPrefetchAt > 0.1 {
            lastPrefetchAt = now
            let b = scrollView.bounds
            let ahead = down ? CGRect(x: 0, y: b.maxY, width: b.width, height: b.height) : CGRect(x: 0, y: b.minY - b.height, width: b.width, height: b.height)
            for a in layout.layoutAttributesForElements(in: ahead) ?? [] where a.indexPath.item < model.messages.count {
                if case .widget = model.messages[a.indexPath.item].kind { model.ensureHost(model.messages[a.indexPath.item].id) }
            }
        }
        if config.dropFarAway, now - lastTrimAt > 0.5 {
            lastTrimAt = now
            trimNow()
        }
    }

    private var running = false

    /// The reader's session: down through the feed, back up, report.
    @MainActor
    private func run() async {
        running = true
        hitches.begin("warm-up")
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        hitches.begin("scroll-down")
        await scroll(to: .bottom)
        hitches.begin("pause-bottom")
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        hitches.begin("scroll-up")
        await scroll(to: .top)
        hitches.begin("settle")
        try? await Task.sleep(nanoseconds: 1_500_000_000)
        hitches.stop()
        if !Task.isCancelled { finish() }
        running = false
        scrollTask = nil
    }

    private enum Edge { case top, bottom }

    @MainActor
    private func scroll(to edge: Edge) async {
        let cv = collectionView!
        let page = cv.bounds.height - cv.adjustedContentInset.top - cv.adjustedContentInset.bottom
        let minY = -cv.adjustedContentInset.top
        let maxY = max(minY, cv.contentSize.height - cv.bounds.height + cv.adjustedContentInset.bottom)
        if config.scroll == "flick" {
            // 2500 pt/s like a hard flick, driven frame by frame so the hitch tracker sees the real scroll.
            let speed: CGFloat = 2500
            var y = cv.contentOffset.y
            let target = edge == .bottom ? maxY : minY
            let dir: CGFloat = edge == .bottom ? 1 : -1
            var last = CACurrentMediaTime()
            scrollingProgrammatically = true
            defer { scrollingProgrammatically = false }
            while (edge == .bottom && y < maxY - 0.5) || (edge == .top && y > minY + 0.5) {
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: 8_000_000)
                let now = CACurrentMediaTime()
                y += dir * speed * CGFloat(now - last)
                last = now
                y = edge == .bottom ? min(y, target) : max(y, target)
                cv.setContentOffset(CGPoint(x: 0, y: y), animated: false)
                if cv.contentSize.height - cv.bounds.height + cv.adjustedContentInset.bottom < y, edge == .bottom { break }
            }
            return
        }
        // Step: one screen per flick, then a pause as if reading.
        var guardCount = 0
        while guardCount < 400 {
            if Task.isCancelled { return }
            guardCount += 1
            let maxYNow = max(minY, cv.contentSize.height - cv.bounds.height + cv.adjustedContentInset.bottom)
            let y = cv.contentOffset.y
            let target = edge == .bottom ? min(y + page * 0.9, maxYNow) : max(y - page * 0.9, minY)
            if abs(target - y) < 1 { break }
            scrollingProgrammatically = true
            cv.setContentOffset(CGPoint(x: 0, y: target), animated: true)
            try? await Task.sleep(nanoseconds: 350_000_000)
            scrollingProgrammatically = false
            try? await Task.sleep(nanoseconds: 900_000_000)
        }
    }

    private func finish() {
        #if DEBUG
        let build = "debug"
        #else
        let build = "release"
        #endif
        let report = model.report(hitches: hitches.phases, memoryStart: memoryStart, memoryPeak: memoryPeak, build: build)
        let s = report.summary
        let hitchLine = report.hitches.filter { $0.name.hasPrefix("scroll") }.map { String(format: "%@ %.0f ms/s (max %.0f ms, %d freezes)", $0.name, $0.hitchRateMsPerS, $0.maxFrameMs, $0.freezes) }.joined(separator: " · ")
        var line = String(format: "done %d/%d loaded (%d dead) · first screen (%d): display→content median %.0f ms, max %.0f · display→content median %.0f ms, p90 %.0f · loaded median %.0f ms, p90 %.0f · renderer ready median %.0f · jumps %d · ", s.loaded, s.widgets, s.dead, s.firstScreenWidgets, s.firstScreenDisplayToContentMsMedian, s.firstScreenDisplayToContentMsMax, s.displayToContentMsMedian, s.displayToContentMsP90, s.loadedMsMedian, s.loadedMsP90, s.rendererReadyMsMedian, s.layoutJumps)
        line += hitchLine + String(format: " · mem peak %.0f MB", s.memoryPeakMB)
        let work = MainWork.shared.rounded.sorted { $0.value.totalMs > $1.value.totalMs }.prefix(6).map { "\($0.key) ×\($0.value.count) \(Int($0.value.totalMs)) ms (max \($0.value.maxMs))" }
        line += " · main: " + work.joined(separator: ", ")
        line += DemoReport.write("feed-report.json", report)
        finishedSummary = line
        updateStatus()
        onFinished?(line)
        print("FEED REPORT: \(line)")
    }
}

// MARK: - cells

final class TextBubbleCell: UICollectionViewCell {
    static let font = UIFont.systemFont(ofSize: 15)
    private let bubble = UIView()
    private let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        bubble.backgroundColor = .systemBackground
        bubble.layer.cornerRadius = 16
        bubble.layer.cornerCurve = .continuous
        label.font = Self.font
        label.numberOfLines = 0
        contentView.addSubview(bubble)
        bubble.addSubview(label)
    }
    required init?(coder: NSCoder) { fatalError() }

    private var bubbleWidth: CGFloat = 300

    func configure(text: String, bubbleWidth: CGFloat) {
        label.text = text
        self.bubbleWidth = bubbleWidth
        setNeedsLayout()
    }

    static func height(for text: String, bubbleWidth: CGFloat) -> CGFloat {
        let h = (text as NSString).boundingRect(with: CGSize(width: bubbleWidth - 24, height: 1000), options: [.usesLineFragmentOrigin], attributes: [.font: font], context: nil).height
        return ceil(h) + 20
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        label.frame = CGRect(x: 12, y: 10, width: bubbleWidth - 24, height: bounds.height - 20)
        let w = min(bubbleWidth, ceil(label.sizeThatFits(CGSize(width: bubbleWidth - 24, height: 1000)).width) + 24)
        bubble.frame = CGRect(x: 0, y: 0, width: w, height: bounds.height)
    }
}

final class WidgetBubbleCell: UICollectionViewCell {
    var messageId = -1
    let bubble = UIView()
    let content = UIView()
    let snapshotView = UIImageView()
    let caption = UILabel()
    private(set) weak var hostView: UIView?
    private var bubbleWidth: CGFloat = 300
    private var padding: CGFloat = ChatFeedViewController.bubblePadding

    static func cellHeight(widgetHeight: CGFloat, padding: CGFloat) -> CGFloat {
        widgetHeight + 2 * padding + ChatFeedViewController.captionHeight + 2
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        bubble.backgroundColor = .systemBackground
        bubble.layer.cornerRadius = 16
        bubble.layer.cornerCurve = .continuous
        bubble.clipsToBounds = true
        content.clipsToBounds = true
        snapshotView.contentMode = .scaleToFill
        snapshotView.clipsToBounds = true
        snapshotView.isHidden = true
        caption.font = .systemFont(ofSize: 9)
        caption.textColor = .secondaryLabel
        caption.lineBreakMode = .byTruncatingTail
        contentView.addSubview(bubble)
        bubble.addSubview(content)
        content.addSubview(snapshotView)
        contentView.addSubview(caption)
    }
    required init?(coder: NSCoder) { fatalError() }

    func configure(entry: FeedWidgetEntry, snapshot: UIImage?, bubbleWidth: CGFloat, padding: CGFloat, caption text: String) {
        self.bubbleWidth = bubbleWidth
        self.padding = padding
        caption.text = text
        if entry.host == nil { showSnapshot(snapshot) } else { snapshotView.isHidden = true }
        setNeedsLayout()
    }

    /// The widget's view fills the bubble's content area (`willDisplay`); a snapshot shown before is hidden.
    func attach(_ view: UIView) {
        if hostView === view, view.superview === content { return }
        hostView?.removeFromSuperview()
        hostView = view
        view.frame = content.bounds
        view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        content.addSubview(view)
        snapshotView.isHidden = true
    }

    func detach() {
        hostView?.removeFromSuperview()
        hostView = nil
    }

    func showSnapshot(_ image: UIImage?) {
        hostView?.removeFromSuperview()
        hostView = nil
        snapshotView.image = image
        snapshotView.isHidden = image == nil
        snapshotView.backgroundColor = image == nil ? .tertiarySystemFill : .clear
        snapshotView.isHidden = false
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        detach()
        snapshotView.image = nil
        snapshotView.isHidden = true
        messageId = -1
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let p = padding
        let h = bounds.height - ChatFeedViewController.captionHeight - 2
        bubble.frame = CGRect(x: 0, y: 0, width: bubbleWidth, height: h)
        content.frame = bubble.bounds.insetBy(dx: p, dy: p)
        snapshotView.frame = content.bounds
        hostView?.frame = content.bounds
        caption.frame = CGRect(x: 8, y: h + 1, width: bounds.width - 16, height: ChatFeedViewController.captionHeight)
    }
}
#endif

import SwiftUI
import WidgetSandbox
#if os(iOS)
import UIKit.UIGestureRecognizerSubclass
#endif

struct ContentView: View {
    var body: some View {
        NavigationStack {
            switch DemoAutorun.kind {
            case "attacks": AttackRunnerView(autostart: true)
            case "generated": GeneratedRunnerView(autostart: true)
            case "compat": GeneratedRunnerView(groups: ["compat"], reportName: "compat-report.json", autostart: true)
            case "htmlbubbles": GeneratedRunnerView(groups: Corpus.htmlbubblesGroups, reportName: "htmlbubbles-report.json", autostart: true)
            case "common": GeneratedRunnerView(groups: Corpus.commonGroups, reportName: "common-report.json", autostart: true)
            case "showcase": GeneratedRunnerView(groups: Corpus.showcaseGroups, reportName: "showcase-report.json", autostart: true)
            case "compare": CompareRunnerView(autostart: true)
            #if os(iOS)
            case "feed": ChatFeedView(config: FeedConfig.fromLaunchArguments(), autorun: true, dark: UserDefaults.standard.bool(forKey: "demo.dark"))
            #endif
            #if os(macOS)
            case "pause": PauseProbeView()
            #endif
            default: corpusList
            }
        }
    }

    private var corpusList: some View {
        List {
            #if os(iOS)
            NavigationLink("Chat feed: a widget in every bubble") { ChatFeedSetupView() }
            #endif
            NavigationLink("Run all attacks against the canary") { AttackRunnerView() }
            NavigationLink("Run the generated corpus") { GeneratedRunnerView() }
            NavigationLink("Run the compat corpus") { GeneratedRunnerView(groups: ["compat"], reportName: "compat-report.json") }
            NavigationLink("Run the Telegram demos (htmlbubbles-ported)") { GeneratedRunnerView(groups: Corpus.htmlbubblesGroups, reportName: "htmlbubbles-report.json") }
            NavigationLink("Run the common-profile corpus (renderer comparison)") { GeneratedRunnerView(groups: Corpus.commonGroups, reportName: "common-report.json") }
            NavigationLink("Run the showcase (interactive demos)") { GeneratedRunnerView(groups: Corpus.showcaseGroups, reportName: "showcase-report.json") }
            NavigationLink("Sandbox vs reference: speed comparison (dev tooling)") { CompareRunnerView() }
            NavigationLink("Custom HTML…") { CustomHtmlView() }
            ForEach(Corpus.showcaseGroups, id: \.self) { g in
                Section(g) {
                    ForEach(Corpus.files(in: g), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: g, file: f) } }
                }
            }
            Section("compat") {
                ForEach(Corpus.files(in: "compat"), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: "compat", file: f) } }
            }
            ForEach(Corpus.generatedGroups, id: \.self) { g in
                Section(g) {
                    ForEach(Corpus.files(in: g), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: g, file: f) } }
                }
            }
            Section("attacks") {
                ForEach(Corpus.files(in: "attacks"), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: "attacks", file: f) } }
            }
            ForEach(Corpus.commonGroups, id: \.self) { g in
                Section(g) {
                    ForEach(Corpus.files(in: g), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: g, file: f) } }
                }
            }
            ForEach(Corpus.htmlbubblesGroups, id: \.self) { g in
                Section(g) {
                    ForEach(Corpus.files(in: g), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: g, file: f) } }
                }
            }
            ForEach(Corpus.safeAreaGroups, id: \.self) { g in
                Section(g) {
                    ForEach(Corpus.files(in: g), id: \.self) { f in NavigationLink(f) { WidgetScreen(group: g, file: f) } }
                }
            }
        }
        .navigationTitle("Widget sandbox")
    }
}

/// A chat-bubble-sized widget with the diagnostics panel below it.
struct WidgetScreen: View {
    let group: String
    let file: String
    /// Overrides the corpus file (the custom-HTML screen).
    var html: String? = nil
    @State private var model: WidgetModel?
    @State private var error: String?
    @State private var onScreen = true
    @State private var reference: ReferenceModel?
    // The toggles follow the user from widget to widget (and across launches).
    @AppStorage("demo.dark") private var dark = false
    /// Reference mode (dev tooling): the same file rendered natively, without the sandbox. Never for attacks.
    @AppStorage("demo.reference") private var showReference = false
    /// WebView, Blitz or litehtml (dev tooling; the sandbox and validator are the same). See DemoRenderer.
    @AppStorage(DemoRenderer.key) private var rendererRaw = ""
    @Environment(\.scenePhase) private var scenePhase
    private var referenceAllowed: Bool { group != "attacks" }

    var body: some View {
        GeometryReader { viewport in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    #if os(iOS)
                    // The switches live above the bubble, like the web UI's checkbox row: in the iOS 26
                    // navigation bar they lose their captions and squeeze the title out (two bare knobs).
                    HStack(spacing: 18) {
                        if referenceAllowed { LabeledSwitch("Reference", isOn: $showReference) }
                        LabeledSwitch("Dark", isOn: $dark)
                        Spacer()
                    }
                    if DemoRenderer.hasChoice {
                        HStack {
                            RendererPicker(raw: $rendererRaw)
                            Spacer()
                        }
                    }
                    #endif
                    if let model {
                        // Like the web UI: sandbox and reference side by side where the window is wide enough.
                        #if os(macOS)
                        HStack(alignment: .top, spacing: 16) {
                            sandboxColumn(model, viewport: viewport.size).id(ObjectIdentifier(model))
                            if let reference { ReferenceBubble(model: reference).id(ObjectIdentifier(reference)).frame(width: 376) }
                        }
                        #else
                        sandboxColumn(model, viewport: viewport.size).id(ObjectIdentifier(model))
                        if let reference { ReferenceBubble(model: reference).id(ObjectIdentifier(reference)) }
                        #endif
                    } else if let error {
                        Text(error).foregroundStyle(.red)
                    }
                }
                .padding()
            }
            .coordinateSpace(name: "scroll")
        }
        // The demo's Dark switch is the chat's theme, not the system's: the captions and panels follow it too,
        // otherwise they are black on black.
        .environment(\.colorScheme, dark ? .dark : .light)
        .background(dark ? Color.black : DemoColor.screen)
        .navigationTitle(file)
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) { Button("Restart") { load() } }
            #if os(macOS)
            // One toolbar item for both switches: as separate items macOS 26 fuses them into one capsule with no gap.
            ToolbarItem(placement: .primaryAction) {
                HStack(spacing: 18) {
                    RendererPicker(raw: $rendererRaw)
                    if referenceAllowed { LabeledSwitch("Reference", isOn: $showReference) }
                    LabeledSwitch("Dark", isOn: $dark)
                }
                .padding(.horizontal, 6)
            }
            #endif
        }
        .onChange(of: showReference) { _ in loadReference() }
        .onChange(of: rendererRaw) { _ in load() }
        .onChange(of: dark) { model?.setTheme(DemoTheme.make(dark: $0)); reference?.setTheme($0 ? "dark" : "light") }
        .onAppear { load() }
        .onDisappear { model = nil; reference = nil }
        // Off-screen pause: the bubble scrolled out of the screen or the app in the background counts as hidden
        // (the view's window attachment — and on macOS the window's occlusion — is tracked by the host itself).
        .onChange(of: scenePhase) { _ in pushVisibility() }
    }

    private func sandboxColumn(_ model: WidgetModel, viewport: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            ChatBubble(model: model, viewport: viewport, timeOverCorner: Corpus.isSafeArea(group)) { onScreen = $0; pushVisibility() }
            DiagnosticsPanel(model: model)
        }
        #if os(macOS)
        .frame(width: 376)
        #endif
    }

    private func pushVisibility() {
        #if os(iOS)
        model?.host.setVisible(onScreen && scenePhase == .active)
        #else
        // A desktop window that merely lost focus is still watched; minimised / covered windows are the host's job.
        model?.host.setVisible(onScreen)
        #endif
    }

    private func load() {
        // Restart = a new WidgetHost from the same HTML (the previous one is destroyed by WidgetModel.deinit).
        model = nil
        do {
            let storageKey = group + "/" + file
            var options = WidgetModel.defaultOptions
            options.storage = DemoStorage.preload(storageKey)
            // corpus/safe-area: a message with no caption below the widget, the time over its corner (docs/sdk-v1.md §2.17).
            if Corpus.isSafeArea(group) { options.contentSafeArea = Corpus.timeCorner }
            let made = try WidgetModel(html: sourceHtml, theme: DemoTheme.make(dark: dark), width: 360, options: options, rendererFactory: DemoRenderer.factory)
            made.host.onStorageCommit = DemoStorage.commit(storageKey)
            model = made
        } catch {
            self.error = "\(error)"
        }
        loadReference()
    }

    private func loadReference() {
        reference = nil
        guard showReference, referenceAllowed else { return }
        reference = ReferenceModel(html: sourceHtml, theme: dark ? "dark" : "light")
    }

    private var sourceHtml: String { html ?? Corpus.html(group: group, file: file) }
}

/// Paste any HTML and render it like a corpus file (sandbox, and reference if the toggle is on).
struct CustomHtmlView: View {
    @AppStorage(Corpus.customHtmlKey) private var text = "<p>Hello from a pasted widget</p>\n<script>document.querySelector('p').textContent += ' at ' + new Date().toLocaleTimeString();</script>"
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("The HTML goes through the same path as a corpus file; the draft is kept between launches.").font(.caption).foregroundStyle(.secondary)
            TextEditor(text: $text).font(.system(size: 12, design: .monospaced)).frame(minHeight: 200)
            HStack {
                NavigationLink("Render") { WidgetScreen(group: "custom", file: "custom.html", html: text) }
                Button("Clear") { text = "" }
            }
        }
        .padding()
        .navigationTitle("Custom HTML")
    }
}

/// The natively rendered twin of ChatBubble (dev tooling): WebKit runs the widget's own scripts here.
struct ReferenceBubble: View {
    @ObservedObject var model: ReferenceModel
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("reference — native WebKit, no sandbox, dev tooling only").font(.caption2).foregroundStyle(.secondary).padding(.leading, 8)
            ReferenceView(model: model)
                .frame(width: 360, height: max(model.height, 24))
                .background(DemoColor.bubble)
                .clipShape(RoundedRectangle(cornerRadius: 14))
            // Same definition as the sandbox panel's "loaded in": wall clock from creating the web view.
            Text(model.loadedAfterMs.map { String(format: "● loaded in %.0f ms", $0) } ?? "… loading")
                .font(.caption.bold()).foregroundStyle(model.loadedAfterMs == nil ? Color.secondary : Color.green).padding(.leading, 8)
            if let loadMs = model.loadMs {
                Text(String(format: "page's own clock: load event at %.0f ms, DOMContentLoaded at %.0f ms (from navigation start, excludes creating the web view)", loadMs, model.domContentLoadedMs ?? 0))
                    .font(.caption2).foregroundStyle(.secondary).padding(.leading, 8)
            }
            ForEach(Array(model.errors.suffix(5).enumerated()), id: \.offset) { _, e in Text(e).font(.caption2).foregroundStyle(.red).lineLimit(2).padding(.leading, 8) }
        }
    }
}

struct ChatBubble: View {
    @ObservedObject var model: WidgetModel
    /// Size of the enclosing scroll view (coordinate space "scroll"), to tell whether the bubble is on screen.
    var viewport: CGSize = .zero
    /// A message with no caption below the widget: the time over the widget's bottom right corner on a backing (docs/sdk-v1.md §2.17;
    /// the host got the band as `contentSafeArea`); a view over the widget, so its taps are the bubble's.
    var timeOverCorner = false
    /// Called when the bubble enters or leaves the screen (the owner's side of the off-screen pause).
    var onScreenChange: (Bool) -> Void = { _ in }
    @StateObject private var gestures = BubbleGestures()
    /// `setResizeAnchor('bottom')` (docs/sdk-v1.md §5.6): the bubble sits at the bottom of a stage as tall as the
    /// tallest bubble (the 600 pt cap and the caption row) and grows upward, its bottom edge staying put, like the web
    /// dev app's stage; `top` (the default) lays it out from the top, the content below moves.
    private static let anchoredStageHeight: CGFloat = WidgetModel.defaultOptions.maximumHeight + 20
    var body: some View {
        bubble.frame(minHeight: model.diagnostics.resizeAnchor == .bottom ? Self.anchoredStageHeight : nil, alignment: .bottomLeading)
    }

    private var bubble: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(DemoRenderer.label).font(.caption2).foregroundStyle(.secondary)
                Spacer()
                // What the bubble did with a press it took (there is no real menu or reply in the demo).
                if let toast = gestures.toast { Text("bubble: \(toast)").font(.caption2.bold()).foregroundStyle(.blue) }
            }
            .padding(.horizontal, 8)
            // The bubble is the widget's canvas, as on a client: no padding of its own (the widget's body margin is the
            // inset), the colour DemoTheme passes as bg_color, the radius clips what the widget paints.
            WidgetView(host: model.host)
                .frame(width: 360, height: max(model.diagnostics.height, 24))
                .background(DemoColor.bubble)
                .overlay(alignment: .bottomTrailing) {
                    if timeOverCorner {
                        Text("12:00 ✓✓").font(.system(size: 11, weight: .medium)).foregroundStyle(.white)
                            .frame(width: 52, height: 18).background(Capsule().fill(Color.black.opacity(0.35)))
                            .padding(.trailing, 6).padding(.bottom, 3)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .onScreen(viewport: viewport, onScreenChange)
                .onAppear { gestures.attach(to: model.host) }
            // What a product shows under the bubble: the widget stays on screen (frozen) with a one-line reason.
            if let dead = model.diagnostics.dead {
                Text("Widget stopped: \(dead.split(separator: "\n").first.map(String.init) ?? dead)").font(.caption2).foregroundStyle(.red).lineLimit(1).padding(.leading, 8)
            } else if let err = model.diagnostics.lastError {
                Text(err.split(separator: "\n").first.map(String.init) ?? err).font(.caption2).foregroundStyle(.red).lineLimit(1).padding(.leading, 8)
            }
        }
    }
}

// MARK: the bubble's gestures (docs/sdk-v1.md §5.5)

/// The demo bubble's own recognizers over the widget, as a client has them: every press goes to a GestureArbiter
/// with the claim the renderer reports for it (`onGestureClaim`, a few ms after the touch began: the press waits for
/// it up to 150 ms, then counts as claiming nothing). A press the bubble takes is taken from the WebView (the
/// widget gets pointercancel) and named in the caption. A vertical drag is always the list's (v0.1.3), whatever the
/// widget's touch-action: the arbiter gives it to the bubble, the widget gets pointercancel and the list scrolls on
/// with its own recognizer (nothing here holds it back; the renderer lets WebKit pan vertically over every element).
final class BubbleGestures: ObservableObject {
    /// What the bubble did with the last press it took, for a moment.
    @Published private(set) var toast: String?
    /// The platform recognizer's side: take the open press from the renderer view; the press is over.
    fileprivate var take: () -> Void = {}
    fileprivate var release: () -> Void = {}

    private enum Step {
        case down(Double, Double, Double, GesturePolicy.PointerKind, Int)
        case move(Double, Double, Double)
        case up(Double, Double, Double)
    }

    private var arbiter = GestureArbiter()
    private weak var host: WidgetHost?
    private var claim: WidgetGestureClaim?
    /// The press's down and what followed it while its claim is on the way; nil once they went to the arbiter.
    private var waiting: [Step]?
    private var claimTimeout: DispatchWorkItem?
    private var holdTimer: DispatchWorkItem?
    private var toastTimer: DispatchWorkItem?

    func attach(to host: WidgetHost) {
        guard self.host !== host else { return }
        self.host = host
        host.onGestureClaim = { [weak self] in self?.claimed($0) }
        #if os(iOS)
        host.view.addGestureRecognizer(BubbleTouchRecognizer(bubble: self))
        #else
        host.view.addGestureRecognizer(BubbleMouseRecognizer(bubble: self))
        #endif
    }

    /// x from the widget's leading edge, y from its top, points; t in ms since boot (UITouch / NSEvent timestamps).
    fileprivate func began(at t: Double, x: Double, y: Double, pointer: GesturePolicy.PointerKind, button: Int = 0) {
        claim = nil
        waiting = [.down(t, x, y, pointer, button)]
        let timeout = DispatchWorkItem { [weak self] in self?.feedWaiting() }
        claimTimeout?.cancel()
        claimTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: timeout)
    }

    fileprivate func moved(at t: Double, x: Double, y: Double) { step(.move(t, x, y)) }
    fileprivate func ended(at t: Double, x: Double, y: Double) { step(.up(t, x, y)) }

    /// The platform ended the press (another recognizer, the system): no double tap continues it.
    fileprivate func cancelled() {
        claimTimeout?.cancel()
        holdTimer?.cancel()
        waiting = nil
        arbiter.cancel()
    }

    private func claimed(_ claim: WidgetGestureClaim) {
        // Only the first claim after a press began is its own.
        guard waiting != nil, self.claim == nil else { return }
        self.claim = claim
        feedWaiting()
    }

    private func step(_ step: Step) {
        if waiting != nil { waiting?.append(step) } else { feed(step) }
    }

    private func feedWaiting() {
        claimTimeout?.cancel()
        guard let steps = waiting else { return }
        waiting = nil
        steps.forEach(feed)
    }

    private func feed(_ step: Step) {
        let verdict: GestureVerdict?
        switch step {
        case let .down(t, x, y, pointer, button): verdict = arbiter.down(at: t, x: x, y: y, claim: claim ?? GesturePolicy.noClaim, pointer: pointer, button: button)
        case let .move(t, x, y): verdict = arbiter.move(at: t, x: x, y: y)
        case let .up(t, x, y): verdict = arbiter.up(at: t, x: x, y: y)
        }
        if let verdict { act(verdict) }
        if case .up = step { release() }
        scheduleHold()
    }

    private func scheduleHold() {
        holdTimer?.cancel()
        guard let at = arbiter.holdDeadline() else { return }
        let timer = DispatchWorkItem { [weak self] in
            guard let self, let verdict = self.arbiter.tick(at: max(ProcessInfo.processInfo.systemUptime * 1000, at)) else { return }
            self.act(verdict)
        }
        holdTimer = timer
        DispatchQueue.main.asyncAfter(deadline: .now() + max(0, at - ProcessInfo.processInfo.systemUptime * 1000) / 1000, execute: timer)
    }

    private func act(_ verdict: GestureVerdict) {
        guard verdict.cancel, let action = verdict.action else { return }
        take()
        switch action {
        case .menu: show("message menu")
        case .swipe: show("swipe → reply")
        case .back: show("back")
        case .scroll: break
        }
    }

    private func show(_ text: String) {
        toast = text
        toastTimer?.cancel()
        let timer = DispatchWorkItem { [weak self] in self?.toast = nil }
        toastTimer = timer
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: timer)
    }
}

#if os(iOS)
/// Feeds BubbleGestures the first touch over the widget. It never blocks the list or WebKit (it recognizes
/// simultaneously with every other recognizer); when the bubble takes the press it begins (or, at the touch's end,
/// recognizes) and ends the touch for the WebView.
private final class BubbleTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    private let bubble: BubbleGestures
    private var touch: UITouch?
    private var touchEnded = false
    private var pressOpen = false

    init(bubble: BubbleGestures) {
        self.bubble = bubble
        super.init(target: nil, action: nil)
        delegate = self
        bubble.take = { [weak self] in self?.take() }
        bubble.release = { [weak self] in self?.release() }
    }

    private func point(_ touch: UITouch) -> (t: Double, x: Double, y: Double) {
        let p = touch.location(in: view)
        let rtl = view?.effectiveUserInterfaceLayoutDirection == .rightToLeft
        return (touch.timestamp * 1000, rtl ? Double((view?.bounds.width ?? 0) - p.x) : Double(p.x), Double(p.y))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        for t in touches where touch != nil || t !== touches.first { ignore(t, for: event) }
        guard touch == nil, let t = touches.first else { return }
        touch = t
        touchEnded = false
        pressOpen = true
        let p = point(t)
        let pointer: GesturePolicy.PointerKind = t.type == .pencil ? .pen : t.type == .indirectPointer ? .mouse : .touch
        bubble.began(at: p.t, x: p.x, y: p.y, pointer: pointer, button: event.buttonMask.contains(.secondary) ? 2 : 0)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        let p = point(t)
        bubble.moved(at: p.t, x: p.x, y: p.y)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        touchEnded = true
        let p = point(t)
        bubble.ended(at: p.t, x: p.x, y: p.y)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        pressOpen = false
        bubble.cancelled()
        state = state == .possible ? .failed : .cancelled
    }

    override func reset() {
        super.reset()
        if pressOpen { bubble.cancelled() }
        touch = nil
        pressOpen = false
    }

    private func take() {
        if state == .possible { state = touchEnded ? .ended : .began }
        // WKWebView reads touches through recognizers of its own, which no other recognizer can prevent:
        // `cancelsTouchesInView` alone leaves the page its pointerup and click (iOS 26.5 simulator). Switching them
        // off and on cancels them, and WebKit sends the page pointercancel for the touch.
        var views = view?.subviews ?? []
        while let v = views.popLast() {
            views += v.subviews
            for g in v.gestureRecognizers ?? [] where g.isEnabled {
                g.isEnabled = false
                g.isEnabled = true
            }
        }
    }

    private func release() {
        pressOpen = false
        switch state {
        case .possible: state = .failed
        case .began, .changed: state = .ended
        default: break
        }
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
}
#else
/// Feeds BubbleGestures the mouse presses over the widget (a right click; a double click is always the widget's). The
/// WebView has the press before the renderer can say what the widget claims, so the bubble cannot take it back here:
/// it only names what it does (the menu), and the widget sees that press's own pointer events.
private final class BubbleMouseRecognizer: NSGestureRecognizer {
    private let bubble: BubbleGestures
    private var pressOpen = false

    init(bubble: BubbleGestures) {
        self.bubble = bubble
        super.init(target: nil, action: nil)
        delaysPrimaryMouseButtonEvents = false
        delaysSecondaryMouseButtonEvents = false
        bubble.release = { [weak self] in
            self?.pressOpen = false
            self?.state = .failed
        }
    }

    required init?(coder: NSCoder) { nil }

    private func point(_ event: NSEvent) -> (t: Double, x: Double, y: Double) {
        guard let view else { return (event.timestamp * 1000, 0, 0) }
        let p = view.convert(event.locationInWindow, from: nil)
        let x = view.userInterfaceLayoutDirection == .rightToLeft ? view.bounds.width - p.x : p.x
        return (event.timestamp * 1000, Double(x), Double(view.isFlipped ? p.y : view.bounds.height - p.y))
    }

    private func down(_ event: NSEvent, button: Int) {
        pressOpen = true
        let p = point(event)
        bubble.began(at: p.t, x: p.x, y: p.y, pointer: .mouse, button: button)
    }

    private func up(_ event: NSEvent) {
        guard pressOpen else { return }
        let p = point(event)
        bubble.ended(at: p.t, x: p.x, y: p.y)
    }

    override func mouseDown(with event: NSEvent) { down(event, button: 0) }
    override func rightMouseDown(with event: NSEvent) { down(event, button: 2) }
    override func mouseDragged(with event: NSEvent) {
        let p = point(event)
        bubble.moved(at: p.t, x: p.x, y: p.y)
    }
    override func mouseUp(with event: NSEvent) { up(event) }
    override func rightMouseUp(with event: NSEvent) { up(event) }

    override func reset() {
        super.reset()
        if pressOpen { bubble.cancelled() }
        pressOpen = false
    }
}
#endif

/// Reports whether a view's frame intersects the visible part of the enclosing
/// ScrollView (coordinate space "scroll", `viewport` = the scroll view's size);
/// a list of bubbles would use the same thing per cell. Works on both platforms,
/// unlike a comparison with the device screen.
private struct OnScreenKey: PreferenceKey {
    static var defaultValue = true
    static func reduce(value: inout Bool, nextValue: () -> Bool) { value = nextValue() }
}

extension View {
    func onScreen(viewport: CGSize, _ change: @escaping (Bool) -> Void) -> some View {
        background(GeometryReader { g in
            Color.clear.preference(key: OnScreenKey.self, value: viewport == .zero || CGRect(origin: .zero, size: viewport).intersects(g.frame(in: .named("scroll"))))
        })
        .onPreferenceChange(OnScreenKey.self, perform: change)
    }
}

struct DiagnosticsPanel: View {
    @ObservedObject var model: WidgetModel
    var body: some View {
        let d = model.diagnostics
        VStack(alignment: .leading, spacing: 6) {
            Text(statusLine(d))
                .font(.caption.bold()).foregroundStyle(d.dead != nil ? .red : d.loaded ? .green : .secondary)
            Text(String(format: "JS %.0f ms in %d tasks · %.1f MB · %d ops · %d nodes · %d sync queries (%.0f ms, %d cache hits) · missed %d",
                        d.stats.jsMs, d.stats.tasks, Double(d.stats.memoryBytes) / 1048576, d.stats.ops, d.nodes, d.stats.queries, d.stats.queryMs, d.stats.queryCacheHits, d.stats.missedMutations))
                .font(.caption2).foregroundStyle(.secondary)
            Text("bubble \(Int(d.height)) pt · resize anchor \(d.resizeAnchor.rawValue): " + (d.resizeAnchor == .bottom ? "grows and shrinks upward, its bottom stays" : "grows and shrinks downward, its top stays"))
                .font(.caption2).foregroundStyle(.secondary)
            ForEach(d.diags.keys.sorted(), id: \.self) { k in
                let e = d.diags[k]!
                Text("\(k) ×\(e.count): \(e.details.suffix(2).joined(separator: " · "))").font(.caption2).lineLimit(2)
                    .foregroundStyle(["url-blocked", "network-blocked", "navigation-blocked", "protocol-violation", "renderer-violation"].contains(k) ? .red : .orange)
            }
            ForEach(Array(d.actions.suffix(10).enumerated()), id: \.offset) { _, a in Text("action: \(Self.describe(a))").font(.caption2).lineLimit(2) }
            ForEach(Array(d.logs.suffix(30).enumerated()), id: \.offset) { _, l in
                Text("[\(l.level)] \(l.text)").font(.system(size: 10, design: .monospaced)).foregroundStyle(l.level == "error" ? .red : .primary).lineLimit(4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(DemoColor.panel)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    /// What `onAction` got (SDK v1): the demo only lists it; a client confirms links and plays haptics (docs/sdk-v1.md §5.2, §5.3).
    private static func describe(_ action: WidgetAction) -> String {
        switch action {
        case .ready: return "ready"
        case .openLink(let url): return "openLink \(url.absoluteString)"
        case .openTelegramLink(let url): return "openTelegramLink \(url.absoluteString)"
        case .haptic(.impact(let style)): return "haptic impact \(style)"
        case .haptic(.notification(let type)): return "haptic notification \(type)"
        case .haptic(.selection): return "haptic selection"
        }
    }

    /// Wall clock from WidgetHost creation, measured by the host itself (the compare runner's definition):
    /// `loaded` = renderer ready + QuickJS boot + the widget's scripts; the renderer's share is the second number.
    private func statusLine(_ d: WidgetDiagnostics) -> String {
        if let dead = d.dead { return "✖ terminated: \(dead)" }
        if !d.loaded { return "… loading" }
        var line = "● loaded"
        if let ms = d.loadedMs { line += String(format: " in %.0f ms", ms) }
        if let ms = d.rendererReadyMs { line += String(format: " (renderer ready at %.0f ms)", ms) }
        return line
    }
}

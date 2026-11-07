// SwiftUI entry points of the chat feed: a setup screen (preset, size, scroll
// mode) and the feed itself wrapped for the navigation stack. iOS only — the
// feed is UIKit (UICollectionView), the part of the demo closest to a real
// chat; the macOS demo keeps the single-widget screens.

#if os(iOS)
import SwiftUI
import WidgetSandbox

struct ChatFeedSetupView: View {
    @AppStorage("feed.preset") private var preset = "max"
    @AppStorage("feed.count") private var count = 60
    @AppStorage("feed.scroll") private var scroll = "step"
    /// The interactive demos by default on the phone; `classic` is the measured set of docs/chat-feed-ios.md.
    @AppStorage("feed.corpus") private var corpus = "showcase"
    @AppStorage("demo.dark") private var dark = false
    @State private var custom = FeedConfig.max
    @State private var showCustom = false

    private var config: FeedConfig {
        var c = preset == "custom" ? custom : FeedConfig.preset(preset)
        c.count = count
        c.scroll = scroll
        c.corpus = corpus
        return c
    }

    var body: some View {
        Form {
            Section("Feed") {
                Picker("Preset", selection: $preset) {
                    Text("baseline — nothing on").tag("baseline")
                    Text("max — everything on").tag("max")
                    Text("custom").tag("custom")
                }
                Picker("Widgets", selection: $corpus) {
                    Text("showcase — interactive demos").tag("showcase")
                    Text("classic — compat, generated, Telegram demos").tag("classic")
                    Text("custom — the Custom HTML draft in every bubble").tag("custom")
                }
                Stepper("\(count) widgets", value: $count, in: 5...400, step: 5)
                Picker("Autorun scroll", selection: $scroll) {
                    Text("step (read a screen, flick)").tag("step")
                    Text("flick (fast, continuous)").tag("flick")
                }
                LabeledSwitch("Dark", isOn: $dark)
            }
            if preset == "custom" {
                Section("Host") {
                    Toggle("Shared WebKit environment", isOn: $custom.sharedWebKit)
                    Toggle("Shared validator thread", isOn: $custom.sharedValidatorThread)
                    Toggle("Pools (renderers, engines, validators)", isOn: $custom.pools)
                    Toggle("Parallel boot (bundle while the page loads)", isOn: $custom.parallelBoot)
                    Toggle("Async validation (off the main thread)", isOn: $custom.asyncValidation)
                    Toggle("callAsyncJavaScript transport", isOn: $custom.callAsync)
                }
                Section("List") {
                    Toggle("Height cache", isOn: $custom.heightCache)
                    Toggle("Prefetch hosts", isOn: $custom.prefetch)
                    Toggle("Park prefetched views in the window", isOn: $custom.park)
                    Toggle("Drop far-away runtimes (snapshot)", isOn: $custom.dropFarAway)
                }
            }
            Section {
                NavigationLink("Open the feed") { ChatFeedView(config: config, autorun: false, dark: dark) }
                NavigationLink("Autorun (scroll down and up, write feed-report.json)") { ChatFeedView(config: config, autorun: true, dark: dark) }
                Button("Forget cached heights") { FeedModel.clearHeightCache() }
            } footer: {
                Text("The same configuration from a shell: --autorun feed --feed max --feed-count \(count) --feed-scroll \(scroll) --feed-set corpus=\(corpus). The report lands in the app's Documents.")
            }
        }
        .navigationTitle("Chat feed")
        // Like a messenger warming up before any chat is open: the pools of the chosen configuration fill while the reader looks at this screen.
        .onAppear { if config.prewarmAtLaunch { config.applyGlobals(widgetWidth: FeedConfig.defaultWidgetWidth, parkingView: nil) } }
    }
}

/// The feed bar's state, shared by the SwiftUI bar and the UIKit controller (the web feed's bar, src/feed.ts).
final class FeedSwitches: ObservableObject {
    @Published var renderer: DemoRenderer.Kind
    @Published var pools: Bool
    @Published var hostile: String
    @Published var corpus: String
    @Published var count: Int
    @Published var scrolling = false
    weak var controller: ChatFeedViewController?
    /// Hostile bubbles on offer (corpus/attacks); each is stopped or contained by a different runtime limit.
    static let hostiles: [(name: String, title: String)] = [
        ("none", "no hostile bubble"),
        ("fallback-at-boot", "infinite loop in the first script: killed by the boot CPU slice, the markup stays as the frozen fallback"),
        ("cpu-raf-burn", "a 30 ms rAF loop: throttled by the 300 ms/s CPU quota, not killed"),
        ("cpu-greedy-timer", "greedy timers: deferred by the CPU quota"),
        ("timer-flood", "100 000 setTimeout calls: refused past 256 timers"),
        ("api-fetch", "fetch() to the canary: NetworkDisabledError in the sandbox, nothing leaves"),
        ("markup-img-src", "an <img src=http://…>: the validator drops the URL"),
        ("mem-steady-growth", "keeps allocating typed arrays per tick: the engine's memory limit"),
        ("mem-burst-alloc", "one huge allocation"),
    ]
    init(config: FeedConfig) {
        renderer = DemoRenderer.Kind(rawValue: config.renderer) ?? DemoRenderer.kind
        pools = config.pools
        hostile = config.hostile
        corpus = config.corpus
        count = config.count
    }
}

/// The bar above the feed: renderer, pool, hostile bubble, corpus, size, reset, auto-scroll — the same switches as the web feed's bar, applied live.
struct FeedBar: View {
    @ObservedObject var switches: FeedSwitches
    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                if DemoRenderer.hasChoice {
                    Picker("Renderer", selection: $switches.renderer) {
                        ForEach(DemoRenderer.Kind.allCases, id: \.self) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)
                } else {
                    Spacer()
                }
                Toggle("pool", isOn: $switches.pools).toggleStyle(.switch).fixedSize()
            }
            HStack(spacing: 8) {
                Menu {
                    Picker("Widgets", selection: $switches.corpus) {
                        Text("showcase — interactive demos").tag("showcase")
                        Text("classic — compat, generated, Telegram").tag("classic")
                        Text("custom — the Custom HTML draft").tag("custom")
                    }
                } label: { Text(switches.corpus) }
                Menu {
                    Picker("Hostile bubble", selection: $switches.hostile) {
                        ForEach(FeedSwitches.hostiles, id: \.name) { Text($0.name).tag($0.name) }
                    }
                } label: { Text("hostile: \(switches.hostile)") }
                Menu {
                    Picker("Bubbles", selection: $switches.count) {
                        ForEach([12, 24, 48, 72, 120, 200], id: \.self) { Text("\($0) bubbles").tag($0) }
                    }
                } label: { Text("\(switches.count)") }
                Spacer(minLength: 0)
                Button("reset") { switches.controller?.rebuild() }
                Button(switches.scrolling ? "■ stop" : "▶ scroll") {
                    switches.controller?.toggleAutoScroll()
                    switches.scrolling = switches.controller?.isAutoScrolling ?? false
                }
            }
            .font(.caption)
            .buttonStyle(.bordered)
            .controlSize(.mini)
        }
        .font(.caption)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color(uiColor: .systemBackground))
        .onChange(of: switches.renderer) { switches.controller?.setRenderer($0) }
        .onChange(of: switches.pools) { switches.controller?.setPools($0) }
        .onChange(of: switches.hostile) { switches.controller?.setHostile($0) }
        .onChange(of: switches.corpus) { switches.controller?.setCorpus($0) }
        .onChange(of: switches.count) { switches.controller?.setCount($0) }
    }
}

/// The feed with its bar: a SwiftUI bar over the UIKit collection view, one `FeedSwitches` between them.
struct ChatFeedView: View {
    let config: FeedConfig
    let autorun: Bool
    let dark: Bool
    @StateObject private var switches: FeedSwitches

    init(config: FeedConfig, autorun: Bool, dark: Bool) {
        self.config = config
        self.autorun = autorun
        self.dark = dark
        _switches = StateObject(wrappedValue: FeedSwitches(config: config))
    }

    var body: some View {
        VStack(spacing: 0) {
            FeedBar(switches: switches)
            FeedCollection(config: config, autorun: autorun, dark: dark, switches: switches)
        }
        .environment(\.colorScheme, dark ? .dark : .light)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct FeedCollection: UIViewControllerRepresentable {
    let config: FeedConfig
    let autorun: Bool
    let dark: Bool
    let switches: FeedSwitches

    func makeUIViewController(context: Context) -> ChatFeedViewController {
        let vc = ChatFeedViewController(config: config, autorun: autorun, dark: dark)
        switches.controller = vc
        vc.onFinished = { [weak switches] _ in switches?.scrolling = false }
        return vc
    }
    func updateUIViewController(_ uiViewController: ChatFeedViewController, context: Context) {}
}
#endif

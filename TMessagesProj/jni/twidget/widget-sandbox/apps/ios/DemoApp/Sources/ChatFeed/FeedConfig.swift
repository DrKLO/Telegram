// The chat-feed demo (iOS): a scrolling list of bubbles with a widget in each
// one, as close to the messenger case as the demo gets. Every optimisation is
// a switch here so two configurations can be measured in the same build;
// `baseline` is the host exactly as the single-widget screen uses it, `max`
// turns everything on. `--autorun feed --feed max --feed-count 60
// --feed-scroll step --feed-set pools=false,parallelBoot=true` drives it from
// a shell (report: feed-report.json, see DemoReport).

#if os(iOS)
import Foundation
import UIKit
import WidgetSandbox

struct FeedConfig: Codable, Equatable {
    var preset = "baseline"
    /// Widgets in the feed (each preceded by a text bubble); the corpus is cycled when it runs out.
    var count = 60
    /// `step`: one screen at a time with a pause (reading); `flick`: continuous fast scroll to the end and back.
    var scroll = "step"

    // WidgetHost.Options
    var sharedWebKit = false
    var sharedValidatorThread = false
    var pools = false
    var poolRenderers = 3
    var poolEngines = 2
    var poolValidators = 2
    var parallelBoot = false
    var asyncValidation = false
    /// Host → renderer via `callAsyncJavaScript` arguments instead of an escaped `evaluateJavaScript` literal.
    var callAsync = false

    // The list's own lifecycle
    /// Heights remembered from a previous session: a bubble is laid out at its final height before the widget renders.
    var heightCache = false
    /// Create hosts for bubbles the collection view is about to show (UICollectionViewDataSourcePrefetching).
    var prefetch = false
    /// Park prefetched (not yet displayed) widget views in the window so WebKit runs their layout and the height arrives early.
    var park = false
    /// Snapshot and destroy the runtime of a bubble scrolled further than `keepScreens` screens away; recreate on return.
    var dropFarAway = false
    var keepScreens = 2.0
    /// Fill the pools when the app launches (a messenger would, long before a chat with widgets opens), not when the feed screen appears.
    var prewarmAtLaunch = false
    /// `--feed-run-id`: echoed in the report so a shell can tell a fresh report from the previous run's.
    var runId = ""
    /// Which widgets fill the feed: `classic` = compat → generated → Telegram demos (the measured set, docs/chat-feed-ios.md),
    /// `showcase` = corpus/showcase, the interactive demos, `custom` = the Custom HTML screen's draft in every bubble.
    /// Every widget fills its bubble (no padding, clipped at the corner radius).
    var corpus = "classic"
    /// The feed bar's other switches at start (the web feed's bar, src/feed.ts): the renderer ("" = the demo's picker,
    /// `DemoRenderer.kind`) and the hostile bubble — an `attacks/` file name that takes the third widget bubble, "none" for none.
    var renderer = ""
    var hostile = "none"

    static let baseline = FeedConfig()

    static var max: FeedConfig {
        var c = FeedConfig()
        c.preset = "max"
        c.sharedWebKit = true
        c.sharedValidatorThread = true
        c.pools = true
        c.parallelBoot = true
        c.asyncValidation = true
        c.callAsync = true
        c.heightCache = true
        c.prefetch = true
        c.park = true
        c.dropFarAway = true
        c.prewarmAtLaunch = true
        return c
    }

    static func preset(_ name: String) -> FeedConfig {
        switch name {
        case "max": return .max
        default: return .baseline
        }
    }

    /// `--feed <preset> --feed-count N --feed-scroll step|flick --feed-set key=value,key=value`
    static func fromLaunchArguments(_ args: [String] = CommandLine.arguments) -> FeedConfig {
        func value(_ flag: String) -> String? {
            guard let i = args.firstIndex(of: flag), i + 1 < args.count else { return nil }
            return args[i + 1]
        }
        var c = preset(value("--feed") ?? "max")
        if let n = value("--feed-count").flatMap(Int.init) { c.count = n }
        if let s = value("--feed-scroll") { c.scroll = s }
        if let id = value("--feed-run-id") { c.runId = id }
        if let set = value("--feed-set") {
            for pair in set.split(separator: ",") {
                let kv = pair.split(separator: "=", maxSplits: 1).map(String.init)
                guard kv.count == 2 else { continue }
                c.apply(key: kv[0], value: kv[1])
            }
            c.preset += "+set"
        }
        return c
    }

    mutating func apply(key: String, value: String) {
        let b = ["1", "true", "yes", "on"].contains(value.lowercased())
        switch key {
        case "sharedWebKit": sharedWebKit = b
        case "sharedValidatorThread": sharedValidatorThread = b
        case "pools": pools = b
        case "poolRenderers": poolRenderers = Int(value) ?? poolRenderers
        case "poolEngines": poolEngines = Int(value) ?? poolEngines
        case "poolValidators": poolValidators = Int(value) ?? poolValidators
        case "parallelBoot": parallelBoot = b
        case "asyncValidation": asyncValidation = b
        case "callAsync": callAsync = b
        case "heightCache": heightCache = b
        case "prefetch": prefetch = b
        case "park": park = b
        case "dropFarAway": dropFarAway = b
        case "keepScreens": keepScreens = Double(value) ?? keepScreens
        case "prewarmAtLaunch": prewarmAtLaunch = b
        case "count": count = Int(value) ?? count
        case "scroll": scroll = value
        case "corpus": corpus = value
        case "renderer": renderer = value
        case "hostile": hostile = value
        default: break
        }
    }

    var hostOptions: WidgetHost.Options {
        var o = WidgetModel.defaultOptions
        o.sharedWebKit = sharedWebKit
        o.sharedValidatorThread = sharedValidatorThread
        o.pools = pools
        o.parallelBoot = parallelBoot
        o.asyncValidation = asyncValidation
        return o
    }

    /// The widget width the feed will use on this device (the pools park renderers at it before the feed exists).
    static var defaultWidgetWidth: CGFloat {
        let screen = UIScreen.main.bounds.width
        return min(screen - 2 * 12 - 40, 360) - 16
    }

    /// Process-wide knobs (transport, pools) — applied once before the first host; idempotent (a second call only tops the pools up).
    func applyGlobals(widgetWidth: CGFloat, parkingView: PlatformView?, pools poolsOverride: Bool? = nil) {
        RendererWebView.transport = callAsync ? .callAsync : .evaluate
        if poolsOverride ?? pools {
            WidgetWarmup.prewarm(renderers: poolRenderers, engines: poolEngines, validators: poolValidators, width: widgetWidth, parkingView: parkingView)
        } else {
            RendererPool.shared.targetSize = 0
            EnginePool.shared.targetSize = 0
            ValidatorPool.shared.targetSize = 0
            RendererPool.shared.drain()
            EnginePool.shared.drain()
            ValidatorPool.shared.drain()
        }
    }

    var summary: String {
        var on: [String] = []
        if sharedWebKit { on.append("sharedWebKit") }
        if sharedValidatorThread { on.append("sharedValidatorThread") }
        if pools { on.append("pools \(poolRenderers)/\(poolEngines)/\(poolValidators)") }
        if parallelBoot { on.append("parallelBoot") }
        if asyncValidation { on.append("asyncValidation") }
        if callAsync { on.append("callAsync") }
        if heightCache { on.append("heightCache") }
        if prefetch { on.append("prefetch") }
        if park { on.append("park") }
        if dropFarAway { on.append("drop>\(keepScreens)scr") }
        if prewarmAtLaunch { on.append("prewarmAtLaunch") }
        if corpus != "classic" { on.append("corpus \(corpus)") }
        if hostile != "none" { on.append("hostile \(hostile)") }
        return "\(preset): " + (on.isEmpty ? "nothing on" : on.joined(separator: ", "))
    }
}
#endif

import SwiftUI
import WidgetSandbox

/// The chat's theme as the demo passes it (SDK v1 `WidgetTheme`): the colour scheme of the Dark switch and, as
/// `bg_color`, the colour the demo draws its bubbles in (the UA layer paints the widget's root with it); the rest of
/// the palette and the button and text tokens take the framework's defaults (a client maps its own chat theme,
/// docs/sdk-v1.md §5.1).
enum DemoTheme {
    static func make(dark: Bool) -> WidgetTheme {
        WidgetTheme(colorScheme: dark ? .dark : .light, params: ["bg_color": bubbleHex(dark: dark)])
    }

    /// The same bubble colour for the reference mode (dev tooling): after `Corpus/reference.css`, so the native
    /// rendering of a widget sits on the colour the sandboxed one gets.
    static var referenceCss: String {
        ":root{--tg-theme-bg-color:\(bubbleHex(dark: false))}@media (prefers-color-scheme: dark){:root{--tg-theme-bg-color:\(bubbleHex(dark: true))}}"
    }

    /// `DemoColor.bubble` resolved for the scheme, as `#rrggbb`.
    static func bubbleHex(dark: Bool) -> String {
        var r: CGFloat = 1, g: CGFloat = 1, b: CGFloat = 1, a: CGFloat = 1
        #if os(macOS)
        let appearance = NSAppearance(named: dark ? .darkAqua : .aqua) ?? NSAppearance.currentDrawing()
        appearance.performAsCurrentDrawingAppearance {
            NSColor.textBackgroundColor.usingColorSpace(.sRGB)?.getRed(&r, green: &g, blue: &b, alpha: &a)
        }
        #else
        UIColor.systemBackground.resolvedColor(with: UITraitCollection(userInterfaceStyle: dark ? .dark : .light)).getRed(&r, green: &g, blue: &b, alpha: &a)
        #endif
        let byte = { (c: CGFloat) in Int((max(0, min(1, c)) * 255).rounded()) }
        return String(format: "#%02x%02x%02x", byte(r), byte(g), byte(b))
    }
}

/// Web Storage per corpus file for the demo's lifetime, so a Restart keeps it (a client keys it by message, docs/sdk-v1.md §5.4).
enum DemoStorage {
    private static var byFile: [String: WidgetStorage] = [:]

    static func preload(_ key: String) -> WidgetStorage { byFile[key] ?? WidgetStorage() }

    /// `onStorageCommit` for the widget of `key` (main thread).
    static func commit(_ key: String) -> (WidgetStorageArea, [String: String]) -> Void {
        { area, values in
            var stored = byFile[key] ?? WidgetStorage()
            if area == .local { stored.local = values } else { stored.session = values }
            byFile[key] = stored
        }
    }
}

/// The demo sources are shared by the iOS and the macOS app (macos/DemoApp
/// references this directory); the few platform differences live here.
enum DemoColor {
    #if os(macOS)
    static let bubble = Color(nsColor: .textBackgroundColor)
    static let screen = Color(nsColor: .windowBackgroundColor)
    static let panel = Color(nsColor: .controlBackgroundColor)
    #else
    static let bubble = Color(.systemBackground)
    static let screen = Color(.secondarySystemBackground)
    static let panel = Color(.tertiarySystemBackground)
    #endif
}

/// Where the runners write their JSON reports (`generated-report.json`,
/// `compat-report.json`, `attack-report.json`).
enum DemoReport {
    static func url(_ name: String) -> URL? {
        #if os(macOS)
        // Not the user's real ~/Documents on a desktop: Application Support/<bundle id>/.
        guard let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        let dir = base.appendingPathComponent(Bundle.main.bundleIdentifier ?? "TwidgetDemo", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(name)
        #else
        // The simulator's app container: `xcrun simctl get_app_container <udid> <bundle id> data`.
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.appendingPathComponent(name)
        #endif
    }

    /// Writes the report; returns a one-line status suffix.
    static func write<T: Encodable>(_ name: String, _ value: T) -> String {
        guard let url = url(name) else { return "" }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(value), (try? data.write(to: url)) != nil else { return " · report not written" }
        return " · report: \(url.path)"
    }
}

/// `--autorun attacks|generated|compat` (launch argument, like the Android
/// demo's `--es autorun`): the runner starts by itself, writes its report and,
/// on macOS, quits the app — so the corpora can be driven from a shell.
/// `--renderer webview|blitz|litehtml` (or the shorthands `--blitz`, `--litehtml`,
/// `--webview` / `--no-blitz`) picks the renderer before anything loads (like the
/// Android demo's `--es renderer`), so the runners can be driven on any of them.
enum DemoAutorun {
    static let kind: String? = {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--autorun"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }()

    /// `--features binaryOps,canvasWords`: the wire forms the demo's widgets advertise
    /// (`WidgetHost.Options.features`); with `binaryOps` the validator runs the native
    /// node model, which the Playwright suites never exercise.
    static var features: Set<String> = []

    /// The demo's host options: the package defaults plus the launch arguments above.
    static var hostOptions: WidgetHost.Options {
        var o = WidgetModel.defaultOptions
        o.features = features
        return o
    }

    static func applyLaunchArguments() {
        let args = CommandLine.arguments
        if let i = args.firstIndex(of: "--features"), i + 1 < args.count {
            features = Set(args[i + 1].split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })
        }
        #if TW_NATIVE_RENDERERS
        if args.contains("--blitz") { DemoRenderer.kind = .blitz }
        if args.contains("--litehtml") { DemoRenderer.kind = .litehtml }
        #endif
        if args.contains("--webview") || args.contains("--no-blitz") { DemoRenderer.kind = .webview }
        if let i = args.firstIndex(of: "--renderer"), i + 1 < args.count, let k = DemoRenderer.Kind(rawValue: args[i + 1]) { DemoRenderer.kind = k }
    }

    static func finished() {
        #if os(macOS)
        if kind != nil { DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { NSApplication.shared.terminate(nil) } }
        #endif
    }
}

/// The renderer the demo's widgets use (the **Renderer** picker on a widget
/// screen, persisted as `demo.renderer`): the product WKWebView renderer, or
/// one of the native prototypes — Blitz (Rust) or litehtml (C++). The sandbox
/// and the validator are the same either way; the comparison is of renderers
/// only. Dev tooling. The prototypes are frozen (2026-10-30): they exist only in
/// a build with TW_NATIVE_RENDERERS=YES, and without them the picker is hidden.
enum DemoRenderer {
    enum Kind: String, CaseIterable {
        case webview
        #if TW_NATIVE_RENDERERS
        case blitz, litehtml
        #endif
        var title: String {
            switch self {
            case .webview: return "WebView"
            #if TW_NATIVE_RENDERERS
            case .blitz: return "Blitz"
            case .litehtml: return "litehtml"
            #endif
            }
        }
    }
    static let key = "demo.renderer"
    static var kind: Kind {
        get {
            if let raw = UserDefaults.standard.string(forKey: key), let k = Kind(rawValue: raw) { return k }
            #if TW_NATIVE_RENDERERS
            // `demo.blitz` was the Blitz switch before the picker existed.
            if UserDefaults.standard.bool(forKey: "demo.blitz") { return .blitz }
            #endif
            return .webview
        }
        set { UserDefaults.standard.set(newValue.rawValue, forKey: key) }
    }
    /// More than one renderer to pick from: a build with the prototypes.
    static var hasChoice: Bool { Kind.allCases.count > 1 }
    static var factory: RendererFactory? { factory(for: kind) }
    static func factory(for kind: Kind) -> RendererFactory? {
        switch kind {
        case .webview: return nil
        #if TW_NATIVE_RENDERERS
        case .blitz: return BlitzRenderer.factory
        case .litehtml: return LitehtmlRenderer.factory
        #endif
        }
    }
    static var label: String { kind == .webview ? "sandbox" : "sandbox · \(kind.title) renderer" }
}

/// The picker bound to `DemoRenderer.kind`; `onChange` fires through the @AppStorage key. Nothing without the prototypes.
struct RendererPicker: View {
    @Binding var raw: String
    var body: some View {
        if DemoRenderer.hasChoice {
            Picker("Renderer", selection: Binding(get: { DemoRenderer.kind.rawValue }, set: { raw = $0 })) {
                ForEach(DemoRenderer.Kind.allCases, id: \.rawValue) { Text($0.title).tag($0.rawValue) }
            }
            .pickerStyle(.segmented)
            .fixedSize()
        }
    }
}

/// A switch with a visible caption: a toolbar `Toggle` shows only the knob on
/// macOS (and its text is lost on iOS too), so the caption is explicit.
struct LabeledSwitch: View {
    let title: String
    @Binding var isOn: Bool
    init(_ title: String, isOn: Binding<Bool>) { self.title = title; _isOn = isOn }
    var body: some View {
        HStack(spacing: 6) {
            Text(title).font(.callout)
            Toggle(title, isOn: $isOn).labelsHidden().toggleStyle(.switch)
        }
    }
}

extension View {
    /// Inline navigation titles exist only on iOS.
    @ViewBuilder func inlineTitle() -> some View {
        #if os(iOS)
        navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}

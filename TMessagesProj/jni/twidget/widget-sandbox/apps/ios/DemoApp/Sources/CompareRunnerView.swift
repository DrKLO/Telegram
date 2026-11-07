import SwiftUI
import WidgetSandbox

/// Speed comparison, dev tooling: every compat and generated widget is loaded
/// twice — through the sandbox (QuickJS → validator → renderer) and natively
/// in reference mode — and the wall-clock time to "loaded" is recorded for
/// both, plus the sandbox's own JS time. `--autorun compare` writes
/// compare-report.json. Never runs the attack corpus (reference mode has no
/// sandbox).
struct CompareRunnerView: View {
    var groups = ["compat"] + Corpus.generatedGroups
    var autostart = false

    struct Row: Identifiable, Encodable {
        var id: String { group + "/" + file }
        let group: String
        let file: String
        /// Sandbox: WidgetHost creation → `loaded` (wall clock), and the engine's JS time at that point.
        let sandboxWallMs: Double?
        let sandboxJsMs: Double
        let sandboxOk: Bool
        /// Reference: WKWebView creation → `load` event (wall clock), and the page's own `loadEventStart`.
        let referenceWallMs: Double?
        let referenceLoadMs: Double?
        let referenceOk: Bool
        let referenceErrors: [String]
        var ratio: Double? { if let a = sandboxWallMs, let b = referenceWallMs, b > 0 { return a / b } else { return nil } }
    }

    @State private var status = "Loads each widget in the sandbox and in reference mode (native WebKit, no sandbox); wall time to loaded."
    @State private var sandbox: WidgetModel?
    @State private var reference: ReferenceModel?
    @State private var rows: [Row] = []
    @State private var running = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(status).font(.caption)
            HStack {
                Button(running ? "Running…" : "Run all") { Task { await run() } }.disabled(running)
                Spacer()
                if !rows.isEmpty {
                    let s = rows.compactMap(\.sandboxWallMs), r = rows.compactMap(\.referenceWallMs)
                    Text(String(format: "median sandbox %.0f ms · reference %.0f ms", median(s), median(r))).font(.caption.bold())
                }
            }
            HStack(alignment: .top) {
                if let sandbox { WidgetView(host: sandbox.host).id(ObjectIdentifier(sandbox)).frame(width: 300, height: max(sandbox.diagnostics.height, 24)).background(DemoColor.bubble) }
                if let reference { ReferenceView(model: reference).id(ObjectIdentifier(reference)).frame(width: 300, height: max(reference.height, 24)).background(DemoColor.bubble) }
            }
            List {
                ForEach(rows) { row in CompareRowView(row: row) }
            }
        }
        .padding()
        .navigationTitle("Sandbox vs reference")
        .task { if autostart { await run(); DemoAutorun.finished() } }
    }

    struct CompareRowView: View {
        let row: Row
        var body: some View {
            HStack {
                Text(row.file).font(.caption).lineLimit(1)
                Spacer()
                Text(CompareRunnerView.line(row)).font(.caption2).foregroundStyle(row.sandboxOk && row.referenceOk ? Color.secondary : Color.red)
            }
        }
    }

    static func line(_ r: Row) -> String {
        let ms = { (v: Double?) -> String in v.map { String(format: "%.0f ms", $0) } ?? "—" }
        let ratio = r.ratio.map { String(format: "%.1f", $0) } ?? "—"
        return "sandbox \(ms(r.sandboxWallMs)) (JS \(String(format: "%.0f", r.sandboxJsMs))) · reference \(ms(r.referenceWallMs)) · ×\(ratio)"
    }

    private func median(_ xs: [Double]) -> Double {
        let s = xs.sorted()
        return s.isEmpty ? 0 : s[s.count / 2]
    }

    @MainActor
    private func run() async {
        running = true
        rows = []
        let tick: UInt64 = 20_000_000
        for group in groups {
            for file in Corpus.files(in: group) {
                let html = Corpus.html(group: group, file: file)
                status = "sandbox: \(group)/\(file)"
                var sandboxWall: Double?, sandboxJs = 0.0, sandboxOk = false
                if let model = try? WidgetModel(html: html, theme: WidgetTheme(), width: 300, rendererFactory: DemoRenderer.factory) {
                    sandbox = model
                    let t0 = Date()
                    let deadline = t0.addingTimeInterval(10)
                    while Date() < deadline, !model.diagnostics.loaded, model.diagnostics.dead == nil { try? await Task.sleep(nanoseconds: tick) }
                    if model.diagnostics.loaded { sandboxWall = Date().timeIntervalSince(t0) * 1000 }
                    sandboxJs = model.diagnostics.stats.jsMs
                    sandboxOk = model.diagnostics.loaded && model.diagnostics.dead == nil
                    sandbox = nil
                }
                status = "reference: \(group)/\(file)"
                let ref = ReferenceModel(html: html, theme: "light", width: 300)
                reference = ref
                var referenceWall: Double?
                let deadline = Date().addingTimeInterval(10)
                while Date() < deadline, !ref.loaded { try? await Task.sleep(nanoseconds: tick) }
                if ref.loaded { referenceWall = ref.loadedAfterMs }   // measured at the event, not at the poll tick
                rows.append(Row(group: group, file: file, sandboxWallMs: sandboxWall, sandboxJsMs: sandboxJs, sandboxOk: sandboxOk,
                                referenceWallMs: referenceWall, referenceLoadMs: ref.loadMs, referenceOk: ref.loaded && ref.errors.isEmpty, referenceErrors: ref.errors))
                reference = nil
            }
        }
        let s = rows.compactMap(\.sandboxWallMs), r = rows.compactMap(\.referenceWallMs)
        status = String(format: "done — %d widgets; wall time to loaded, median: sandbox %.0f ms, reference %.0f ms", rows.count, median(s), median(r)) + DemoReport.write("compare-report.json", rows)
        running = false
    }
}

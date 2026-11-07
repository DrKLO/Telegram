import SwiftUI
import WidgetSandbox

/// Loads every corpus/attacks file for a few seconds and then asks the canary
/// server on the Mac (127.0.0.1:9999, reachable from the simulator and from
/// the macOS app) how many hits it saw. The app itself talks to the canary;
/// the widgets must not.
struct AttackRunnerView: View {
    var autostart = false
    @State private var status = "Start the canary on the Mac: pnpm canary"
    @State private var current: WidgetModel?
    @State private var results: [(file: String, blocked: Int, dead: String?)] = []
    @State private var running = false
    @State private var hits: String = "—"
    private let canary = URL(string: "http://127.0.0.1:9999")!

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(status).font(.caption)
            Text("canary hits after run: \(hits)").font(.headline).foregroundStyle(hits == "0" ? .green : .red)
            HStack { Button(running ? "Running…" : "Run all") { Task { await run() } }.disabled(running) }
            if let current { WidgetView(host: current.host).id(ObjectIdentifier(current)).frame(width: 360, height: max(current.diagnostics.height, 24)).background(DemoColor.bubble) }
            List(results, id: \.file) { r in
                HStack { Text(r.file).font(.caption); Spacer(); Text("\(r.blocked) blocked" + (r.dead.map { " · dead: \($0)" } ?? "")).font(.caption2).foregroundStyle(.secondary) }
            }
        }
        .padding()
        .navigationTitle("Attack suite")
        .task { if autostart { await run(); DemoAutorun.finished() } }
    }

    struct Report: Encodable {
        struct Row: Encodable { let file: String; let blocked: Int; let dead: String? }
        let hits: Int?
        let status: String
        let results: [Row]
    }

    private func run() async {
        running = true
        results = []
        _ = try? await URLSession.shared.data(from: canary.appendingPathComponent("__reset"))
        for file in Corpus.files(in: "attacks") {
            status = "loading \(file)"
            guard let model = try? WidgetModel(html: Corpus.html(group: "attacks", file: file), theme: WidgetTheme(), width: 360, options: DemoAutorun.hostOptions, rendererFactory: DemoRenderer.factory) else { continue }
            current = model
            // Wait for the widget to load (cold start can take >1 s), then give
            // its scripts and timers a second to try their tricks.
            let deadline = Date().addingTimeInterval(6)
            while Date() < deadline, !model.diagnostics.loaded, model.diagnostics.dead == nil {
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            let d = model.diagnostics
            let blocked = ["url-blocked", "network-blocked", "navigation-blocked", "tag-dropped", "script-skipped", "css-dropped"].reduce(0) { $0 + (d.diags[$1]?.count ?? 0) }
            results.append((file, blocked, d.dead))
            current = nil
        }
        status = "asking the canary"
        var hitCount: Int?
        if let (data, _) = try? await URLSession.shared.data(from: canary.appendingPathComponent("__hits")),
           let list = try? JSONSerialization.jsonObject(with: data) as? [Any] {
            hits = String(list.count)
            hitCount = list.count
            status = list.isEmpty ? "done — no leak reached the canary" : "LEAK: \(String(decoding: data, as: UTF8.self).prefix(300))"
        } else {
            status = "canary not reachable (run `pnpm canary` on the Mac)"
        }
        status += DemoReport.write("attack-report.json", Report(hits: hitCount, status: status, results: results.map { .init(file: $0.file, blocked: $0.blocked, dead: $0.dead) }))
        running = false
    }
}

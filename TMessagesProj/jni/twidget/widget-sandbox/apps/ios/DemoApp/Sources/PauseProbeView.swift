#if os(macOS)
import SwiftUI
import WidgetSandbox

/// `--autorun pause`: the AppKit trigger of the off-screen pause, exercised in
/// a real application (the xctest runner never gets window-server occlusion
/// events, so `WidgetHostTests` cannot cover it on macOS). Loads a
/// requestAnimationFrame widget, minimises the window, expects the task
/// counter to stop, restores the window, expects it to move again, and writes
/// pause-report.json.
struct PauseProbeView: View {
    struct Report: Encodable {
        var visibleTasks = 0, minimisedStart = 0, minimisedEnd = 0, restoredTasks = 0
        var visibility = ""
        var pass: Bool { visibleTasks > 20 && minimisedEnd == minimisedStart && restoredTasks > minimisedEnd + 10 && visibility == "visible,hidden,visible," }
    }

    @State private var model: WidgetModel?
    @State private var status = "running the pause probe"

    private static let html = "<p id=\"s\"></p><script>var n = 0; function step() { n++; requestAnimationFrame(step); } requestAnimationFrame(step);" +
        "function show() { document.getElementById('s').textContent += document.visibilityState + ','; } document.addEventListener('visibilitychange', show); show();</script>"

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(status).font(.caption)
            if let model { WidgetView(host: model.host).id(ObjectIdentifier(model)).frame(width: 360, height: max(model.diagnostics.height, 24)).background(DemoColor.bubble) }
        }
        .padding()
        .navigationTitle("Pause probe")
        .task { await run(); DemoAutorun.finished() }
    }

    @MainActor
    private func run() async {
        guard let model = try? WidgetModel(html: Self.html, theme: WidgetTheme(), width: 360) else { status = "cannot create the widget"; return }
        self.model = model
        var report = Report()
        let sleep = { (s: Double) in try? await Task.sleep(nanoseconds: UInt64(s * 1_000_000_000)) }
        var deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !(model.diagnostics.loaded && model.diagnostics.stats.tasks > 20) { await sleep(0.05) }
        report.visibleTasks = model.diagnostics.stats.tasks

        guard let window = NSApplication.shared.windows.first(where: { $0.isVisible }) else { status = "no window"; return }
        window.miniaturize(nil)
        await sleep(1.0) // the occlusion event, the visibility task and an in-flight frame
        report.minimisedStart = model.diagnostics.stats.tasks
        await sleep(1.5)
        report.minimisedEnd = model.diagnostics.stats.tasks

        window.deminiaturize(nil)
        deadline = Date().addingTimeInterval(5)
        while Date() < deadline, model.diagnostics.stats.tasks < report.minimisedEnd + 10 { await sleep(0.05) }
        report.restoredTasks = model.diagnostics.stats.tasks
        report.visibility = await withCheckedContinuation { c in model.host.evaluateInRenderer("document.getElementById('s').textContent") { c.resume(returning: $0) } }
        status = (report.pass ? "pass" : "FAIL") + " — visible \(report.visibleTasks), minimised \(report.minimisedStart)→\(report.minimisedEnd), restored \(report.restoredTasks), \(report.visibility)"
            + DemoReport.write("pause-report.json", report)
        print(status)
    }
}
#endif

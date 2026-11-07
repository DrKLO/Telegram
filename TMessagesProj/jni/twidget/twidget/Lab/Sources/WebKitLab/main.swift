import Cocoa
import WebKit

let labRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let tracesDir = labRoot.appendingPathComponent("traces")
let resultsDir = labRoot.appendingPathComponent("results")

struct Trace: Codable {
    let name: String
    let frames: [String]
}

struct WebKitResult: Codable {
    let name: String
    let mode: String
    let frames: Int
    let appMs: Double
    let webContentMs: Double
    let gpuMs: Double
    var totalPerFrame: Double { (appMs + webContentMs + gpuMs) / Double(max(frames, 1)) }
}

let args = CommandLine.arguments.dropFirst()
func option(_ name: String) -> String? {
    guard let i = args.firstIndex(of: name), args.index(after: i) < args.endIndex else { return nil }
    return args[args.index(after: i)]
}

private let timebase: Double = {
    var info = mach_timebase_info_data_t()
    mach_timebase_info(&info)
    return Double(info.numer) / Double(info.denom)
}()

func cpuMilliseconds(pid: pid_t) -> Double {
    var info = rusage_info_v2()
    let ok = withUnsafeMutablePointer(to: &info) { ptr in
        ptr.withMemoryRebound(to: rusage_info_t?.self, capacity: 1) { proc_pid_rusage(pid, RUSAGE_INFO_V2, $0) }
    }
    if ok != 0 {
        return 0
    }
    return Double(info.ri_user_time + info.ri_system_time) * timebase / 1e6
}

typealias ResponsibleFn = @convention(c) (pid_t) -> pid_t
let responsible: ResponsibleFn? = {
    guard let sym = dlsym(UnsafeMutableRawPointer(bitPattern: -2), "responsibility_get_pid_responsible_for_pid") else { return nil }
    return unsafeBitCast(sym, to: ResponsibleFn.self)
}()

let labStart = Date().timeIntervalSince1970

func webKitProcesses() -> [(pid_t, String)] {
    let me = getpid()
    var pids = [pid_t](repeating: 0, count: 8192)
    let n = proc_listallpids(&pids, Int32(pids.count * MemoryLayout<pid_t>.size))
    var out: [(pid_t, String)] = []
    for pid in pids.prefix(Int(n)) where pid > 0 {
        var path = [CChar](repeating: 0, count: 4096)
        if proc_pidpath(pid, &path, UInt32(path.count)) <= 0 { continue }
        let s = String(cString: path)
        if !s.contains("com.apple.WebKit.") { continue }
        var mine = responsible?(pid) == me
        if !mine {
            var info = proc_bsdinfo()
            if proc_pidinfo(pid, PROC_PIDTBSDINFO, 0, &info, Int32(MemoryLayout<proc_bsdinfo>.size)) > 0 {
                mine = Double(info.pbi_start_tvsec) >= labStart - 1
            }
        }
        if mine { out.append((pid, (s as NSString).lastPathComponent)) }
    }
    return out
}

let pageHTML = """
<!doctype html><html><head><meta charset="utf-8"><style>
\(bubbleCSS)
html, body { background: white; } body { width: 400px; }
</style></head><body class="lc-light lc-in"></body>
<script>
function morph(from, to) {
  if (from.nodeType !== to.nodeType || from.nodeName !== to.nodeName) { from.replaceWith(to.cloneNode(true)); return; }
  if (from.nodeType === 3 || from.nodeType === 8) { if (from.nodeValue !== to.nodeValue) from.nodeValue = to.nodeValue; return; }
  if (from.nodeType === 1) {
    for (const a of Array.from(from.attributes)) if (!to.hasAttribute(a.name)) from.removeAttribute(a.name);
    for (const a of Array.from(to.attributes)) if (from.getAttribute(a.name) !== a.value) from.setAttribute(a.name, a.value);
  }
  const fc = from.childNodes, tc = to.childNodes;
  let i = 0;
  for (; i < tc.length; i++) {
    if (i < fc.length) morph(fc[i], tc[i]); else from.appendChild(tc[i].cloneNode(true));
  }
  while (fc.length > tc.length) from.removeChild(from.lastChild);
}
const holder = document.createElement('body');
window.setFrame = function(html, mode) {
  if (mode === 0) { document.body.innerHTML = html; return; }
  holder.innerHTML = html;
  morph(document.body, holder);
};
</script></html>
"""

final class Runner: NSObject, WKNavigationDelegate {
    let window: NSWindow
    let webView: WKWebView
    var traces: [Trace]
    let modes: [Int]
    var results: [WebKitResult] = []
    var loaded: (() -> Void)?

    init(traces: [Trace], modes: [Int]) {
        self.traces = traces
        self.modes = modes
        let screen = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1400, height: 900)
        window = NSWindow(contentRect: NSRect(x: screen.maxX - 440, y: screen.minY + 20, width: 420, height: 720), styleMask: [.titled], backing: .buffered, defer: false)
        window.title = "WebKitLab (measuring, don't cover)"
        window.level = .floating
        let config = WKWebViewConfiguration()
        webView = WKWebView(frame: NSRect(x: 0, y: 0, width: 420, height: 720), configuration: config)
        super.init()
        webView.navigationDelegate = self
        window.contentView = webView
        window.orderFrontRegardless()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loaded?()
        loaded = nil
    }

    func load(_ done: @escaping () -> Void) {
        loaded = done
        webView.loadHTMLString(pageHTML, baseURL: nil)
    }

    func snapshot() -> (app: Double, web: Double, gpu: Double) {
        var web = 0.0, gpu = 0.0
        for (pid, name) in webKitProcesses() {
            let ms = cpuMilliseconds(pid: pid)
            if name.contains("GPU") { gpu += ms } else if name.contains("WebContent") { web += ms }
        }
        return (cpuMilliseconds(pid: getpid()), web, gpu)
    }

    func run(index: Int, modeIndex: Int) {
        if index == 0 && modeIndex == 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { print("WebKit processes:", webKitProcesses().map { "\($0.1)(\($0.0))" }) }
        }
        if index >= traces.count {
            if modeIndex + 1 < modes.count {
                run(index: 0, modeIndex: modeIndex + 1)
            } else {
                finish()
            }
            return
        }
        let trace = traces[index]
        let mode = modes[modeIndex]
        load { [self] in
            let first = jsString(trace.frames[0])
            webView.evaluateJavaScript("setFrame(\(first), 0)") { _, _ in
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [self] in
                    let start = snapshot()
                    var i = 1
                    let timer = Timer(timeInterval: 1.0 / 60, repeats: true) { [self] t in
                        if i >= trace.frames.count {
                            t.invalidate()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [self] in
                                let end = snapshot()
                                let r = WebKitResult(name: trace.name, mode: mode == 0 ? "innerHTML" : "morph", frames: max(trace.frames.count - 1, 1),
                                                     appMs: end.app - start.app, webContentMs: end.web - start.web, gpuMs: end.gpu - start.gpu)
                                results.append(r)
                                print(String(format: "%-26@ %-9@ %4d frames  app %6.2f  web %6.2f  gpu %6.2f  = %6.2f ms/frame", r.name as NSString, r.mode as NSString, r.frames,
                                             r.appMs / Double(r.frames), r.webContentMs / Double(r.frames), r.gpuMs / Double(r.frames), r.totalPerFrame))
                                run(index: index + 1, modeIndex: modeIndex)
                            }
                            return
                        }
                        webView.evaluateJavaScript("setFrame(\(jsString(trace.frames[i])), \(mode))", completionHandler: nil)
                        i += 1
                    }
                    RunLoop.main.add(timer, forMode: .common)
                }
            }
        }
    }

    func finish() {
        for mode in ["innerHTML", "morph"] {
            let list = results.filter { $0.mode == mode }
            if list.isEmpty { continue }
            let frames = list.map { $0.frames }.reduce(0, +)
            let app = list.map { $0.appMs }.reduce(0, +), web = list.map { $0.webContentMs }.reduce(0, +), gpu = list.map { $0.gpuMs }.reduce(0, +)
            print(String(format: "TOTAL %@: %d frames, app %.2f + WebContent %.2f + GPU %.2f = %.2f ms CPU per frame", mode, frames, app / Double(frames), web / Double(frames), gpu / Double(frames), (app + web + gpu) / Double(frames)))
        }
        try? FileManager.default.createDirectory(at: resultsDir, withIntermediateDirectories: true)
        try? JSONEncoder().encode(results).write(to: resultsDir.appendingPathComponent("webkit.json"))
        exit(0)
    }
}

func jsString(_ s: String) -> String {
    let data = try! JSONSerialization.data(withJSONObject: [s])
    let json = String(data: data, encoding: .utf8)!
    return String(json.dropFirst().dropLast())
}

let files = ((try? FileManager.default.contentsOfDirectory(atPath: tracesDir.path)) ?? []).filter { $0.hasSuffix(".json") && $0 != "static.json" }.sorted()
let filter = option("--only")
let traces: [Trace] = files.compactMap { file in
    if let filter = filter, !file.contains(filter) { return nil }
    guard let data = try? Data(contentsOf: tracesDir.appendingPathComponent(file)) else { return nil }
    return try? JSONDecoder().decode(Trace.self, from: data)
}.filter { $0.frames.count > 1 }
let modes: [Int] = option("--mode") == "morph" ? [1] : option("--mode") == "innerHTML" ? [0] : [0, 1]

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
print(responsible == nil ? "attributing WebKit processes by start time" : "attributing WebKit processes by responsible pid")
let runner = Runner(traces: traces, modes: modes)
DispatchQueue.main.async {
    runner.run(index: 0, modeIndex: 0)
}
app.run()

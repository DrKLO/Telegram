// What the chat feed measures: per-widget wall clocks (host creation →
// renderer ready → first content → loaded, and the same from the moment the
// bubble was displayed), main-thread hitches while scrolling (CADisplayLink
// frame durations, the metric Xcode's hitch instrument reports as ms/s),
// memory footprint of the app process (the web content process is WebKit's
// and not counted), pool statistics. Written as feed-report.json.

#if os(iOS)
import Foundation
import QuartzCore
import UIKit

/// Frame-pacing on the main thread. A hitch is the time a frame arrived late:
/// `max(0, actual − expected)`; hitch rate = hitch time per second of a phase.
final class HitchTracker {
    struct Phase: Encodable {
        let name: String
        var seconds = 0.0
        var frames = 0
        var hitchMs = 0.0
        var maxFrameMs = 0.0
        /// Frames later than 1.5 expected (a dropped frame) and than 100 ms (a visible freeze).
        var droppedFrames = 0
        var freezes = 0
        var hitchRateMsPerS: Double { seconds > 0 ? hitchMs / seconds : 0 }

        enum CodingKeys: String, CodingKey { case name, seconds, frames, hitchMs, maxFrameMs, droppedFrames, freezes, hitchRateMsPerS }
        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(name, forKey: .name)
            try c.encode((seconds * 100).rounded() / 100, forKey: .seconds)
            try c.encode(frames, forKey: .frames)
            try c.encode(hitchMs.rounded(), forKey: .hitchMs)
            try c.encode(maxFrameMs.rounded(), forKey: .maxFrameMs)
            try c.encode(droppedFrames, forKey: .droppedFrames)
            try c.encode(freezes, forKey: .freezes)
            try c.encode((hitchRateMsPerS * 10).rounded() / 10, forKey: .hitchRateMsPerS)
        }
    }

    private(set) var phases: [Phase] = []
    private var current: Phase?
    private var link: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private var phaseStart: CFTimeInterval = 0

    func begin(_ name: String) {
        end()
        current = Phase(name: name)
        lastTimestamp = 0
        phaseStart = CACurrentMediaTime()
        if link == nil {
            let l = CADisplayLink(target: self, selector: #selector(tick(_:)))
            l.add(to: .main, forMode: .common)
            link = l
        }
    }

    func end() {
        guard var p = current else { return }
        p.seconds = CACurrentMediaTime() - phaseStart
        phases.append(p)
        current = nil
    }

    func stop() {
        end()
        link?.invalidate()
        link = nil
    }

    @objc private func tick(_ link: CADisplayLink) {
        guard current != nil else { return }
        defer { lastTimestamp = link.timestamp }
        guard lastTimestamp > 0 else { return }
        let actual = link.timestamp - lastTimestamp
        let expected = link.targetTimestamp - link.timestamp
        guard expected > 0 else { return }
        current!.frames += 1
        let late = max(0, actual - expected)
        current!.hitchMs += late * 1000
        current!.maxFrameMs = max(current!.maxFrameMs, actual * 1000)
        if actual > expected * 1.5 { current!.droppedFrames += 1 }
        if actual > 0.1 { current!.freezes += 1 }
    }
}

/// Main-thread work by category (count, total, worst): where the hitches come from.
final class MainWork {
    static let shared = MainWork()
    struct Row: Encodable { var count = 0; var totalMs = 0.0; var maxMs = 0.0 }
    private(set) var rows: [String: Row] = [:]

    func record(_ name: String, _ ms: Double) {
        var r = rows[name] ?? Row()
        r.count += 1
        r.totalMs += ms
        r.maxMs = max(r.maxMs, ms)
        rows[name] = r
    }

    func measure<T>(_ name: String, _ block: () -> T) -> T {
        let t0 = CACurrentMediaTime()
        defer { record(name, (CACurrentMediaTime() - t0) * 1000) }
        return block()
    }

    func reset() { rows = [:] }

    var rounded: [String: Row] { rows.mapValues { Row(count: $0.count, totalMs: $0.totalMs.rounded(), maxMs: ($0.maxMs * 10).rounded() / 10) } }
}

enum ProcessMemory {
    /// Physical footprint of this process in MB (what Xcode's memory gauge shows).
    static var footprintMB: Double {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size) / 4
        let kr = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count) }
        }
        return kr == KERN_SUCCESS ? Double(info.phys_footprint) / 1048576 : 0
    }
}

struct FeedWidgetRow: Encodable {
    let path: String
    /// Milliseconds since the run started.
    let createdAt: Double
    let displayedAt: Double?
    /// From host creation (WidgetDiagnostics' definitions).
    let rendererReadyMs: Double?
    let firstContentMs: Double?
    let loadedMs: Double?
    /// From the moment the bubble was shown: what the user waits for. Negative when the widget finished before being displayed (prefetch).
    let displayToContentMs: Double?
    let displayToLoadedMs: Double?
    let prewarmed: String
    let dead: String?
    let errors: Int
    let jsMs: Double
    /// The bubble changed height after it was displayed (a layout jump the reader sees).
    let heightJumps: Int
    let cachedHeight: Double?
    let finalHeight: Double
    let recreated: Int
    let snapshot: Bool
    let heightTrace: [String]
}

struct FeedReport: Encodable {
    struct Summary: Encodable {
        var widgets = 0, loaded = 0, dead = 0, withErrors = 0
        var hostsCreated = 0, hostsDropped = 0, snapshotsTaken = 0, snapshotsFailed = 0, layoutJumps = 0
        var loadedMsMedian = 0.0, loadedMsP90 = 0.0
        var firstContentMsMedian = 0.0, firstContentMsP90 = 0.0
        var displayToContentMsMedian = 0.0, displayToContentMsP90 = 0.0
        var displayToLoadedMsMedian = 0.0, displayToLoadedMsP90 = 0.0
        var rendererReadyMsMedian = 0.0
        /// The bubbles on screen when the chat opened (displayed within the first second): the cold path.
        var firstScreenWidgets = 0, firstScreenDisplayToContentMsMedian = 0.0, firstScreenDisplayToContentMsMax = 0.0
        var memoryPeakMB = 0.0, memoryEndMB = 0.0, memoryStartMB = 0.0
        var totalSeconds = 0.0
        var build = "debug"
    }

    let config: FeedConfig
    let device: String
    let date: String
    let summary: Summary
    let pools: String
    let hitches: [HitchTracker.Phase]
    let mainThreadWork: [String: MainWork.Row]
    let widgets: [FeedWidgetRow]
}

enum Percentile {
    static func median(_ xs: [Double]) -> Double { p(xs, 0.5) }
    static func p90(_ xs: [Double]) -> Double { p(xs, 0.9) }
    static func p(_ xs: [Double], _ q: Double) -> Double {
        let s = xs.sorted()
        guard !s.isEmpty else { return 0 }
        return s[min(s.count - 1, Int(Double(s.count) * q))]
    }
}
#endif

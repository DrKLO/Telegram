import CoreGraphics
import CoreVideo
import Foundation

final class HTMLScriptClock {
    private let lock = NSLock()
    private var link: CVDisplayLink?
    private var pending = false
    private var display: CGDirectDisplayID
    private let deliver: (@escaping () -> Void) -> Void
    private let tick: () -> Void

    private(set) var interval: TimeInterval = 1.0 / 60

    init(display: CGDirectDisplayID, deliver: @escaping (@escaping () -> Void) -> Void, tick: @escaping () -> Void) {
        self.display = display
        self.deliver = deliver
        self.tick = tick
    }

    deinit {
        stop()
    }

    var isRunning: Bool {
        return link != nil
    }

    func start() {
        if link != nil {
            return
        }
        var created: CVDisplayLink?
        guard CVDisplayLinkCreateWithCGDisplay(display, &created) == kCVReturnSuccess, let link = created else {
            return
        }
        let opaque = Unmanaged.passUnretained(self).toOpaque()
        CVDisplayLinkSetOutputCallback(link, { _, _, _, _, _, context in
            guard let context = context else {
                return kCVReturnSuccess
            }
            Unmanaged<HTMLScriptClock>.fromOpaque(context).takeUnretainedValue().fire()
            return kCVReturnSuccess
        }, opaque)
        updateInterval(link)
        self.link = link
        CVDisplayLinkStart(link)
    }

    func stop() {
        guard let link = link else {
            return
        }
        CVDisplayLinkStop(link)
        self.link = nil
        lock.lock()
        pending = false
        lock.unlock()
    }

    func setDisplay(_ display: CGDirectDisplayID) {
        if display == self.display {
            return
        }
        self.display = display
        if let link = link {
            CVDisplayLinkSetCurrentCGDisplay(link, display)
            updateInterval(link)
        }
    }

    private func updateInterval(_ link: CVDisplayLink) {
        let period = CVDisplayLinkGetNominalOutputVideoRefreshPeriod(link)
        if period.timeScale > 0, period.timeValue > 0 {
            interval = max(1.0 / 240, Double(period.timeValue) / Double(period.timeScale))
        } else {
            interval = 1.0 / 60
        }
    }

    private func fire() {
        lock.lock()
        if pending {
            lock.unlock()
            return
        }
        pending = true
        lock.unlock()
        deliver { [weak self] in
            guard let self = self else {
                return
            }
            self.lock.lock()
            self.pending = false
            self.lock.unlock()
            if self.link != nil {
                self.tick()
            }
        }
    }
}

import Foundation
import LiteHTML

public enum WidgetRendererWarmup {
    private static let queue = DispatchQueue(label: "twidget.warmup", qos: .utility)
    private static let lock = NSLock()
    private static var warmed = false

    public static func prewarm(scale: CGFloat = 2, width: CGFloat = 360) {
        lock.lock()
        if warmed {
            lock.unlock()
            return
        }
        warmed = true
        lock.unlock()
        let tile = max(1, Int(ceil(WidgetDocumentView.tileHeight * scale)))
        WidgetSurfacePool.shared.prewarm(width: tile, height: tile)
        queue.async {
            let html = "<html><body style=\"margin:0;font-family:system-ui;font-size:14px\"><div style=\"padding:12px;border-radius:12px;background:#eee\"><b>Warm</b> <i>up</i> 0123456789 <span style=\"font-weight:600\">Text</span><button>OK</button></div></body></html>"
            let document = LHDocument(html: html, css: WidgetThemeStyle.uaStylesheet)
            _ = document.displayList(maxWidth: width, scale: scale)
            _ = document.copyImage(maxWidth: width, rect: CGRect(x: 0, y: 0, width: width, height: 64), scale: scale)
        }
    }
}

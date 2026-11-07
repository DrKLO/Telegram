import XCTest
import CoreGraphics
import LiteHTML

final class DamageBandTests: XCTestCase {
    private struct Bar {
        var height: Double
        var width: Double
        var margin: Double
        var ghost: Double
        var ghostWidth: Double
        var opacity: Double
    }

    private func html(_ bars: [Bar], radius: String) -> String {
        var cols = ""
        for bar in bars {
            cols += "<div class=col><div class=val>12</div><div class=bars>"
            cols += "<div class=b style=\"height:\(bar.height)px;width:\(bar.width)px;margin-right:\(bar.margin)px;background-color:#5856d6\"></div>"
            cols += "<div class=\"b g\" style=\"height:\(bar.ghost)px;width:\(bar.ghostWidth)px;opacity:\(bar.opacity);background-color:#5856d6\"></div>"
            cols += "</div></div>"
        }
        return """
        <style>
        body{margin:0;font-family:-apple-system,sans-serif;font-size:14px}
        .plot{position:relative;height:150px;margin:12px 0 0 34px;border-bottom:1px solid rgba(127,127,127,0.35)}
        .cols{display:flex;position:relative;height:150px}
        .col{flex-grow:1;flex-basis:0;height:150px;overflow:hidden;text-align:center;border-radius:6px 6px 0 0;display:flex;flex-direction:column;justify-content:flex-end}
        .val{height:11px;line-height:11px;margin-bottom:2px;font-size:9px}
        .bars{display:flex;justify-content:center;align-items:flex-end}
        .b{border-radius:\(radius)}
        </style>
        <div style="width:390px"><div class=plot><div class=cols>\(cols)</div></div></div>
        """
    }

    private func pixels(_ image: CGImage) -> [UInt8] {
        let w = image.width, h = image.height
        var data = [UInt8](repeating: 0, count: w * h * 4)
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        data.withUnsafeMutableBytes { buffer in
            let ctx = CGContext(data: buffer.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        }
        return data
    }

    func testGrowingRoundedBarsAcrossTileBoundaryRepaintExactly() {
        XCTAssertEqual(repaintFailures(radius: "4px 4px 0 0", tallest: 110), [])
    }

    func testSquareBarsInRoundedColumnsRepaintExactly() {
        XCTAssertEqual(repaintFailures(radius: "0", tallest: 140), [])
    }

    private func repaintFailures(radius: String, tallest: Double) -> [String] {
        var seed: UInt64 = 0x5eed
        func next() -> Double {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return Double(seed >> 11) / Double(1 << 53)
        }
        let width: CGFloat = 400
        let scale: CGFloat = 2
        var bars = (0..<7).map { _ in Bar(height: 40 + next() * 90, width: 20, margin: 0, ghost: 0, ghostWidth: 0, opacity: 0.32) }
        var previous = LHDocument(html: html(bars, radius: radius), css: nil)
        let size = previous.layout(maxWidth: width, preferredWidth: width)
        let rect = CGRect(x: 0, y: 0, width: width, height: ceil(size.height))
        var previousList = previous.displayList(maxWidth: width, scale: scale)
        var canvas = previous.copyImage(maxWidth: width, rect: rect, scale: scale)!
        var failures: [String] = []
        for step in 0..<240 {
            for i in bars.indices {
                let mode = Int(next() * 6)
                switch mode {
                case 0:
                    bars[i].height = max(0, bars[i].height + (next() - 0.5) * 6)
                case 1:
                    bars[i].height = 20 + next() * tallest
                case 2:
                    let t = next()
                    bars[i].width = 20 - 9 * t
                    bars[i].margin = 2 * t
                    bars[i].ghostWidth = 9 * t
                    bars[i].ghost = bars[i].height * (0.6 + 0.3 * next())
                case 3:
                    bars[i].opacity = 0.32 + next() * 0.4
                case 4:
                    bars[i].width = 20 - next() * 0.7
                default:
                    break
                }
            }
            let document = LHDocument(html: html(bars, radius: radius), css: nil)
            _ = document.layout(maxWidth: width, preferredWidth: width)
            let list = document.displayList(maxWidth: width, scale: scale)
            let rects = list.changedRects(since: previousList).map { $0.rectValue }
            var partial = canvas
            for dirty in rects {
                partial = document.copyImage(maxWidth: width, rect: rect, scale: scale, base: partial, dirty: dirty.insetBy(dx: -1, dy: -1))!
            }
            let fresh = document.copyImage(maxWidth: width, rect: rect, scale: scale)!
            let a = pixels(partial), b = pixels(fresh)
            if a.count == b.count {
                var worst = 0, at = 0
                for k in 0..<a.count {
                    let d = abs(Int(a[k]) - Int(b[k]))
                    if d > worst {
                        worst = d
                        at = k
                    }
                }
                if worst > 2 {
                    failures.append("step \(step) worst \(worst) at x=\((at / 4) % fresh.width) y=\((at / 4) / fresh.width) rects \(rects.count)")
                }
            }
            canvas = fresh
            previous = document
            previousList = list
        }
        _ = previous
        return failures
    }
}

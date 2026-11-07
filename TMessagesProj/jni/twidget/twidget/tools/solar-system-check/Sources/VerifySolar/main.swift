import AppKit
import Foundation
import HTMLScript
import HTML3D

func require(_ condition: Bool, _ message: String) {
    if !condition { fputs("FAIL: \(message)\n", stderr); exit(1) }
}
func settle(_ seconds: Double = 0.65) { RunLoop.main.run(until: Date().addingTimeInterval(seconds)) }
func awaitState(_ predicate: () -> Bool) {
    let deadline = Date().addingTimeInterval(3)
    while !predicate() && Date() < deadline { settle(0.05) }
}
func save(_ pixels: [UInt8], _ path: URL) throws {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 720, pixelsHigh: 480, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 720 * 4, bitsPerPixel: 32)!
    for i in stride(from: 0, to: pixels.count, by: 4) {
        rep.bitmapData![i] = pixels[i + 2]
        rep.bitmapData![i + 1] = pixels[i + 1]
        rep.bitmapData![i + 2] = pixels[i]
        rep.bitmapData![i + 3] = pixels[i + 3]
    }
    try rep.representation(using: .png, properties: [:])!.write(to: path)
}
require(CommandLine.arguments.count >= 3, "usage: VerifySolar input.html output-directory [--jsc]")
HTML3DSession.register()
if CommandLine.arguments.contains("--jsc") { HTMLScript.runtime = JSCScriptRuntime.shared }
let raw = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
require(raw.count <= 32767, "HTML exceeds 32767 bytes")
let source = String(data: raw, encoding: .utf8)!
let output = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true)
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
guard let script = HTMLScript.make(html: source) else { fatalError("missing script") }
_ = script.currentHTML
guard let session = script.scene3D, let layer = session.makeLayer() else { fatalError("missing Metal session") }
layer.frame = CGRect(x: 0, y: 0, width: 720, height: 480)
layer.contentsScale = 1
layer.drawableSize = CGSize(width: 720, height: 480)
session.setPaused(true)
HTMLScript.setDisplay(CGMainDisplayID())
session.attach(layer, size: CGSize(width: 720, height: 480), scale: 1)
script.setVisible(true)
settle(0.4)
script.performAction("pause", value: nil)
awaitState { script.errorText != nil || script.currentHTML.contains(">Play</button>") }
require(script.currentHTML.contains(">Play</button>"), "pause label did not update: \(script.errorText ?? "no script error") \(script.currentHTML)")
func pixels() -> [UInt8] {
    require(script.errorText == nil, script.errorText ?? "script error")
    require(session.stopReason == nil, session.stopReason ?? "native renderer stopped")
    guard let bytes = session.renderOffscreen(width: 720, height: 480) else { fatalError("missing native pixels") }
    require(session.stats.rejected == 0 && session.stats.clipped == 0, "native geometry was rejected or clipped")
    return bytes
}
var previous = pixels()
try save(previous, output.appendingPathComponent("saturn.png"))
for action in ["left", "up", "near", "view"] {
    script.performAction(action, value: nil)
    settle()
    let next = pixels()
    require(next != previous, "\(action) did not change the rendered frame")
    try save(next, output.appendingPathComponent("\(action).png"))
    previous = next
}
script.performAction("next", value: nil)
awaitState { script.errorText != nil || script.currentHTML.contains("Uranus") }
require(script.currentHTML.contains("Uranus"), "next planet did not select Uranus")
_ = pixels()
script.performAction("reset", value: nil)
awaitState { script.errorText != nil || script.currentHTML.contains(">Pause</button>") }
settle()
require(script.currentHTML.contains("Saturn") && script.currentHTML.contains(">Pause</button>"), "reset did not restore Saturn and playback")
_ = pixels()
let stats = session.stats
require(stats.submitted > 0 && stats.triangles > 1000, "no native 3D triangle work")
print("PASS runtime=\(HTMLScript.runtime is JSCScriptRuntime ? "jsc" : "quickjs") submitted=\(stats.submitted) draws=\(stats.draws) triangles=\(stats.triangles) rejected=\(stats.rejected) clipped=\(stats.clipped) htmlBytes=\(raw.count)")
script.setVisible(false)
session.detach(layer)

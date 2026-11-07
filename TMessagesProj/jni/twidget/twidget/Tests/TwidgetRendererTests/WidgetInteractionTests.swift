import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

extension LiteHTMLWidgetRenderer: ScheduledRenderer {}

extension LiteHTMLWidgetRenderer: Renderer {
    public func evaluate(_ script: String, completion: @escaping (String?) -> Void) {
        completion(nil)
    }
}

final class WidgetInteractionTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private final class Harness {
        let window: NSWindow
        let host: WidgetHost
        let renderer: LiteHTMLWidgetRenderer

        init(_ test: WidgetInteractionTests, html: String) throws {
            _ = NSApplication.shared
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            var made: LiteHTMLWidgetRenderer?
            host = try WidgetHost(html: html, theme: "light", width: 400, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: 400, height: 600)
            window.contentView?.addSubview(host.view)
            let host = self.host
            test.pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered })
        }

        func close() {
            host.destroy()
            window.close()
        }

        func element(_ id: String) -> WidgetNode? {
            return renderer.dom.nodes.values.first { $0.attribute("id") == id }
        }

        func text(_ id: String) -> String {
            return element(id)?.children.filter { $0.isText }.map { $0.text }.joined() ?? ""
        }

        func center(_ id: String) throws -> CGPoint {
            let node = try XCTUnwrap(element(id))
            let box = try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
            return CGPoint(x: box.midX, y: box.midY)
        }

        func box(_ id: String) throws -> CGRect {
            let node = try XCTUnwrap(element(id))
            return try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
        }

        func pixel(_ id: String) throws -> [Int] {
            let box = try self.box(id)
            let image = try XCTUnwrap(renderer.snapshot())
            let rep = NSBitmapImageRep(cgImage: try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil)))
            let scale = CGFloat(rep.pixelsWide) / image.size.width
            let color = try XCTUnwrap(rep.colorAt(x: Int((box.minX + 7) * scale), y: Int(box.midY * scale))?.usingColorSpace(.sRGB))
            return [color.redComponent, color.greenComponent, color.blueComponent].map { Int(($0 * 255).rounded()) }
        }

        func presented(_ id: String) throws -> [Int]? {
            let box = try self.box(id)
            let view = renderer.view
            let inWindow = view.convert(CGPoint(x: box.minX + 7, y: box.midY), to: nil)
            guard let image = CGWindowListCreateImage(.null, .optionIncludingWindow, CGWindowID(window.windowNumber), [.boundsIgnoreFraming, .bestResolution]), image.width > 0 else {
                return nil
            }
            let rep = NSBitmapImageRep(cgImage: image)
            let scale = CGFloat(image.width) / window.frame.width
            guard let color = rep.colorAt(x: Int(inWindow.x * scale), y: Int((window.frame.height - inWindow.y) * scale))?.usingColorSpace(.sRGB) else {
                return nil
            }
            return [color.redComponent, color.greenComponent, color.blueComponent].map { Int(($0 * 255).rounded()) }
        }

        func background(_ id: String) -> String? {
            guard let node = element(id) else { return nil }
            return renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(renderer.dom.publicId(node)))["background-color"]
        }

        func send(_ type: NSEvent.EventType, _ point: CGPoint) {
            let view = renderer.view
            let location = view.convert(point, to: nil)
            let event = NSEvent.mouseEvent(with: type, location: location, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: type == .leftMouseDown ? 1 : 0)!
            switch type {
            case .leftMouseDown: view.mouseDown(with: event)
            case .leftMouseUp: view.mouseUp(with: event)
            case .leftMouseDragged: view.mouseDragged(with: event)
            default: view.mouseMoved(with: event)
            }
        }

        var checks: [String: String] {
            guard let json = renderer.dom.html.attribute("data-conformance"), let object = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any], let checks = object["checks"] as? [[Any]] else { return [:] }
            var result: [String: String] = [:]
            for check in checks where check.count > 2 {
                if let name = check[0] as? String, let state = check[2] as? String { result[name] = state }
            }
            return result
        }
    }

    private func assertColor(_ actual: [Int], _ expected: [Int], file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(zip(actual, expected).allSatisfy { abs($0 - $1) <= 20 }, "\(actual) is not close to \(expected)", file: file, line: line)
    }

    func testCoalescedDragKeepsEveryMovementDeltaAndResetsAfterDelivery() throws {
        let html = #"""
        <style>body{margin:0}#drag{height:200px;touch-action:none}</style><div id="drag"></div><span id="result"></span>
        <script>
        var p=[0,0],m=[0,0],n=[0,0],d=document.getElementById('drag');
        d.addEventListener('pointerdown',function(){p=[0,0];m=[0,0];n=[0,0]});
        d.addEventListener('pointermove',function(e){p[0]+=e.movementX;p[1]+=e.movementY;n[0]++});
        d.addEventListener('mousemove',function(e){m[0]+=e.movementX;m[1]+=e.movementY;n[1]++});
        d.addEventListener('pointerup',function(){document.getElementById('result').textContent=p.concat(m,n).join(',')});
        </script>
        """#
        let h = try Harness(self, html: html)
        defer { h.close() }
        for (from, delta, expected) in [(CGPoint(x: 30, y: 30), CGPoint(x: 20, y: 10), "60,30,60,30,1,1"), (CGPoint(x: 120, y: 80), CGPoint(x: -10, y: -5), "-30,-15,-30,-15,1,1")] {
            h.send(.leftMouseDown, from)
            for step in 1...3 {
                h.send(.leftMouseDragged, CGPoint(x: from.x + delta.x * CGFloat(step), y: from.y + delta.y * CGFloat(step)))
            }
            h.send(.leftMouseUp, CGPoint(x: from.x + delta.x * 3, y: from.y + delta.y * 3))
            pump(2, until: { h.text("result") == expected })
            XCTAssertEqual(h.text("result"), expected)
        }
    }

    func testCoalescedPointerAndMouseMovesKeepTheirDispatchOrder() throws {
        let html = #"""
        <style>body{margin:0}#drag{height:200px;touch-action:none}</style><div id="drag"></div><span id="result"></span>
        <script>
        var seen=[],d=document.getElementById('drag');
        d.addEventListener('pointerdown',function(){seen=[]});
        ['pointermove','mousemove'].forEach(function(type){d.addEventListener(type,function(){seen.push(type)})});
        d.addEventListener('pointerup',function(){document.getElementById('result').textContent=seen.join(',')});
        </script>
        """#
        for _ in 0..<12 {
            let h = try Harness(self, html: html)
            defer { h.close() }
            h.send(.leftMouseDown, CGPoint(x: 30, y: 30))
            h.send(.leftMouseDragged, CGPoint(x: 40, y: 40))
            h.send(.leftMouseUp, CGPoint(x: 40, y: 40))
            pump(1, until: { !h.text("result").isEmpty })
            XCTAssertEqual(h.text("result"), "pointermove,mousemove")
        }
    }

    func testPressHoverDetailsTapAndDragReachTheWidget() throws {
        let h = try Harness(self, html: WidgetInteractionTests.html)
        defer { h.close() }
        pump(0.6)

        let count = try h.center("count")
        for _ in 0..<3 {
            h.send(.leftMouseDown, count)
            pump(0.05)
            h.send(.leftMouseUp, count)
            pump(0.1)
        }
        pump(0.3, until: { h.text("ok-count") == "\u{2713}" })
        XCTAssertEqual(h.text("count"), "Tapped 3 times")
        XCTAssertEqual(h.text("ok-count"), "\u{2713}")

        let press = try h.center("press")
        assertColor(try h.pixel("press"), [220, 224, 232])
        h.send(.mouseMoved, press)
        h.send(.leftMouseDown, press)
        pump(0.1)
        assertColor(try h.pixel("press"), [80, 140, 230])
        pump(0.2)
        XCTAssertEqual(h.background("press"), "rgb(80, 140, 230)")
        XCTAssertEqual(h.text("ok-press"), "\u{2713}", "press and hold")
        pump(0.1)
        assertColor(try h.pixel("press"), [80, 140, 230])
        h.send(.leftMouseUp, press)
        pump(0.1)
        XCTAssertEqual(h.background("press"), "rgb(220, 224, 232)")
        assertColor(try h.pixel("press"), [220, 224, 232])

        let hover = try h.center("hov")
        assertColor(try h.pixel("hov"), [221, 221, 221])
        h.send(.mouseMoved, hover)
        pump(0.1)
        assertColor(try h.pixel("hov"), [51, 170, 51])
        pump(0.2)
        XCTAssertEqual(h.background("hov"), "rgb(51, 170, 51)")
        XCTAssertEqual(h.text("ok-hov"), "\u{2713}", "hover")
        pump(0.1)
        assertColor(try h.pixel("hov"), [51, 170, 51])

        let summary = try h.center("sum")
        h.send(.mouseMoved, summary)
        h.send(.leftMouseDown, summary)
        pump(0.05)
        h.send(.leftMouseUp, summary)
        pump(0.5, until: { h.text("ok-det") == "\u{2713}" })
        XCTAssertNotNil(h.element("det")?.attribute("open"))
        XCTAssertEqual(h.text("ok-det"), "\u{2713}", "details")
        XCTAssertGreaterThan(try h.box("det").height, try h.box("sum").height + 10)
        h.send(.leftMouseDown, summary)
        h.send(.leftMouseUp, summary)
        pump(0.3)
        XCTAssertNil(h.element("det")?.attribute("open"))
        XCTAssertEqual(try h.box("det").height, try h.box("sum").height, accuracy: 1)

        let knob = try h.center("knob")
        h.send(.mouseMoved, knob)
        h.send(.leftMouseDown, knob)
        for step in 1...12 {
            h.send(.leftMouseDragged, CGPoint(x: knob.x + CGFloat(step) * 12.5, y: knob.y))
            pump(0.02)
        }
        h.send(.leftMouseUp, CGPoint(x: knob.x + 150, y: knob.y))
        pump(0.5, until: { h.text("ok-drag") == "\u{2713}" })
        XCTAssertEqual(h.text("ok-drag"), "\u{2713}", "drag")

        pump(0.3)
        let checks = h.checks
        XCTAssertEqual(checks["input.tap"], "pass")
        XCTAssertEqual(checks["html.el.details"], "pass")
        XCTAssertEqual(checks["input.drag"], "pass")
    }

    func testHoverAndPressPaintOnScreen() throws {
        let h = try Harness(self, html: WidgetInteractionTests.html)
        defer { h.close() }
        pump(0.6)
        guard let idle = try h.presented("hov"), idle != [0, 0, 0] else {
            throw XCTSkip("no window capture (screen recording permission or locked screen)")
        }
        assertColor(idle, [221, 221, 221])

        h.send(.mouseMoved, try h.center("hov"))
        pump(0.3)
        let hovered = try XCTUnwrap(h.presented("hov"))
        XCTAssertGreaterThan(hovered[1], hovered[0] + 40, "\(hovered) is not green on screen")
        XCTAssertGreaterThan(hovered[1], hovered[2] + 40, "\(hovered) is not green on screen")

        let press = try h.center("press")
        h.send(.mouseMoved, press)
        pump(0.3)
        let rest = try XCTUnwrap(h.presented("press"))
        assertColor(rest, [220, 224, 232])
        assertColor(try XCTUnwrap(h.presented("hov")), idle)
        h.send(.leftMouseDown, press)
        pump(0.3)
        let held = try XCTUnwrap(h.presented("press"))
        XCTAssertGreaterThan(held[2], held[0] + 40, "\(held) is not blue on screen")
        h.send(.leftMouseUp, press)
        pump(0.3)
        assertColor(try XCTUnwrap(h.presented("press")), rest)
    }

    static let html = #"""
<!doctype html>
<html data-test="visual/11-interaction">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Widget profile: interaction</title>
<!-- conformance {"title":"Taps, pressed state, details, drag, hover (desktop), link opening with confirmation","hint":"Do what each row says; the widget shows ✓ when it saw the input.","covers":["input.tap","sel.active","html.el.details","input.drag","sel.hover","tw.open-link","html.el.a-external"],"actions":[{"tap":"count"},{"tap":"count"},{"tap":"count"},{"tap":"sum"},{"drag":"knob","to":[150,0],"by":true,"steps":12},{"hover":"hov"}]} -->
<!-- conformance:harness --><style>html,body{margin:0}body{font:13px/1.35 system-ui,sans-serif;color:#111}@media(prefers-color-scheme:dark){body{color:#e8e8e8}}[data-stage]{position:relative;overflow:hidden;background:#fff;box-sizing:border-box}body{line-height:18px}.v-head{margin:8px 10px 2px;font-weight:600;font-size:15px;line-height:20px}.v-hint{margin:0 10px 6px;font-size:12px;line-height:16px}.v-row{display:flex;align-items:flex-start;margin:6px 10px 10px}.v-cap{margin-left:10px;font-size:12px;line-height:16px}[data-stage]{flex:none;width:150px;height:90px}</style><script>var T=function(){var b=document,M=b.documentElement,T=typeof TelegramWidget!="undefined",O=window.__conformanceMode||(T?"sandbox":"native"),a=[],k={},f=[],c={},d,l,p,u,q,g,x,h,y=t=>typeof t=="string"?b.querySelector(t):t;var S=()=>{h=h||setTimeout(w,0)};function m(t,e,n,s,o){o=o||{};let r=n==null?"skip":n=="pending"?n:(o.xfail?"x":"")+(n?"pass":"fail"),i=k[t+"|"+e]||(k[t+"|"+e]=a[a.length]=[t,e]);return i[2]=r,i[3]=(s?(""+s).slice(0,160):"")+(o.xfail&&r[0]=="x"?" (expected to fail: "+o.xfail+")":""),S(),r}function R(t,e,n){x=()=>{for(let r of a)r[2]=="pending"&&(r[2]="fail",r[3]+=" (timed out)");u=1,w()};let s=(r,i)=>{o.done||(o.done=m(t,e,r,i,n))},o={done:n&&n.sandboxOnly&&!T,pass:r=>s(!0,r),fail:r=>s(!1,r),skip:r=>s(null,r),result:(r,i)=>s(!!r,i)};return m(t,e,o.done?null:"pending",o.done?"sandbox only":n&&n.hint,n),o}var B=(t,e,n)=>getComputedStyle(y(t),n).getPropertyValue(e).trim();function E(t,e,n){let s=B(t,e||"background-color",n),o=/rgba?\(\s*([\d.]+)\D+([\d.]+)\D+([\d.]+)(?:\D+([\d.]+)(%?))?/.exec(s);return o?[Math.round(o[1]),Math.round(o[2]),Math.round(o[3]),o[4]?Math.round(o[4]*(o[5]?1:100))/100:1].join():s}var I=(t,e)=>{c[t]=e,j=()=>{d={};for(let n in c)y(c[n])&&(d[n]=C(y(c[n])))}},j=()=>{},C=t=>{let e=t.getBoundingClientRect();return[e.left,e.top,e.width,e.height].map(n=>Math.round(n*10)/10)};function w(){h=0,p&&!u&&j();let t={pass:0,fail:0,xfail:0,xpass:0,skip:0,pending:0};for(let s of a)t[s[2]]++;let e=p&&!f.length,n=u||e&&!t.pending?"done":e&&q?"awaiting":"running";u=n=="done",M.setAttribute("data-conformance",JSON.stringify({v:1,id:M.getAttribute("data-test"),mode:O,status:n,n:t,checks:a,targets:d,stages:l})),A&&A(n,t)}var A;function D(){if(l||(l={},[].forEach.call(b.querySelectorAll("[data-stage]"),e=>{l[e.getAttribute("data-stage")]=C(e)})),!f.length)return p=1,g&&x&&setTimeout(x,g),S();let t;try{t=f.shift()()}catch(e){m("harness","step",!1,e)}setTimeout(D,t||0)}function F(t){t=t||{},q=t.awaitInput,g=t.awaitInput||t.deadline,w();let e=()=>{e=()=>{},setTimeout(D,t.settle||60)};try{requestAnimationFrame(()=>requestAnimationFrame(()=>e()))}catch(n){}setTimeout(()=>e(),400)}return{color:E,run:F,target:I,wait:R}}();</script><!-- /conformance:harness -->
<style>
.row{display:flex;align-items:center;margin:8px 10px;min-height:36px;font:14px/18px system-ui}
.row .ok{margin-left:auto;width:22px;text-align:center;font-weight:700;color:#1a7f37}
button{font:14px system-ui;height:36px;padding:0 14px;border-radius:10px;border:0;background:rgb(220,224,232);color:#111}
button:active{background:rgb(80,140,230);color:#fff}
#hov{display:inline-block;padding:8px 14px;border-radius:10px;background:#ddd}
#hov:hover{background:#3a3;color:#fff}
#track{position:relative;width:200px;height:36px;border-radius:18px;background:#dde;touch-action:none}
#knob{position:absolute;left:0;top:0;width:36px;height:36px;border-radius:18px;background:#36c;touch-action:none}
a{color:#36c}
</style>
</head>
<body>
<div class="v-head">11 · Interaction</div>
<div class="v-hint">Do what each row says. ✓ appears when the widget saw it.</div>
<div class="row"><button id="count">Tapped 0 times</button><span style="margin-left:8px">tap 3 times</span><span class="ok" id="ok-count"></span></div>
<div class="row"><button id="press">Press and hold</button><span style="margin-left:8px">turns blue while held</span><span class="ok" id="ok-press"></span></div>
<div class="row"><details id="det"><summary id="sum">Tap to open</summary><div style="padding:4px 0">Hidden content is visible now.</div></details><span class="ok" id="ok-det"></span></div>
<div class="row"><div id="track"><div id="knob"></div></div><span style="margin-left:8px">drag to the right</span><span class="ok" id="ok-drag"></span></div>
<div class="row"><span id="hov">hover me (desktop)</span><span style="margin-left:8px">turns green</span><span class="ok" id="ok-hov"></span></div>
<div class="row"><a id="link" href="https://telegram.org/">Open telegram.org</a><span style="margin-left:8px">the client asks before opening, showing the full URL</span></div>
<script>
['count', 'sum', 'knob', 'hov'].forEach(function (n) { T.target(n, '#' + n); });
var n = 0;
var tapW = T.wait('input.tap', 'three taps counted');
document.getElementById('count').addEventListener('click', function () { n++; this.textContent = 'Tapped ' + n + (n === 1 ? ' time' : ' times'); if (n >= 3) { document.getElementById('ok-count').textContent = '✓'; tapW.pass(); } });
document.getElementById('press').addEventListener('pointerdown', function () { var b = this; setTimeout(function () { if (T.color(b) === '80,140,230,1') document.getElementById('ok-press').textContent = '✓'; }, 200); });
var detW = T.wait('html.el.details', 'details opened by a tap');
document.getElementById('det').addEventListener('toggle', function () { if (this.open) { document.getElementById('ok-det').textContent = '✓'; detW.pass(); } });
var knob = document.getElementById('knob'), start = null, x0 = 0;
var dragW = T.wait('input.drag', 'knob dragged to the end');
knob.addEventListener('pointerdown', function (e) { start = e.clientX; x0 = parseFloat(knob.style.left) || 0; try { knob.setPointerCapture(e.pointerId); } catch (err) {} e.preventDefault(); });
knob.addEventListener('pointermove', function (e) { if (start === null || !(e.buttons & 1)) return; knob.style.left = Math.max(0, Math.min(164, x0 + e.clientX - start)) + 'px'; });
knob.addEventListener('pointerup', function () { start = null; if ((parseFloat(knob.style.left) || 0) >= 150) { document.getElementById('ok-drag').textContent = '✓'; dragW.pass(); } });
document.getElementById('hov').addEventListener('pointerover', function () { var h = this; setTimeout(function () { if (T.color(h) === '51,170,51,1') document.getElementById('ok-hov').textContent = '✓'; }, 150); });
T.run({ awaitInput: 60000 });
</script>
</body>
</html>

"""#
}

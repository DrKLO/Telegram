import XCTest
@testable import WidgetSandbox

final class HostOpsAttackTests: XCTestCase {
    func host(_ html: String, renderer: RecordingRenderer) -> WidgetHost {
        let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in renderer })
        let deadline = Date().addingTimeInterval(5)
        while !(host.diagnostics.rendered || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        return host
    }

    static let forbidden = ["javascript:", "http://", "https://", "127.0.0.1", "<script", "onclick", "onerror", "iframe", "srcdoc", "expression(", "@import", "data:text/html", "file:", "vbscript"]

    static func withoutTextData(_ message: String) -> String {
        guard let data = message.data(using: .utf8), var object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let ops = object["ops"] as? [[String: Any]] else { return message }
        object["ops"] = ops.map { op -> [String: Any] in
            var op = op
            if op["op"] as? String == "text" { op["data"] = "" }
            return op
        }
        return String(decoding: try! JSONSerialization.data(withJSONObject: object), as: UTF8.self)
    }

    func leaks(_ renderer: RecordingRenderer) -> [String] {
        var hits: [String] = []
        for message in renderer.messages.dropFirst(2) {
            let lower = Self.withoutTextData(message).lowercased()
            for needle in Self.forbidden where lower.contains(needle) {
                hits.append(needle + " in " + String(message.prefix(300)))
            }
        }
        return hits
    }

    static let craftedJSON: [String] = [
        ##"[{"op":"el","id":50,"tag":"script","attrs":[["src","http://127.0.0.1:9999/hit/s"]]},{"op":"ins","parent":4,"id":50,"before":null}]"##,
        ##"[{"op":"el","id":51,"tag":"a","attrs":[["href","javascript:alert(1)"]]},{"op":"ins","parent":4,"id":51,"before":null}]"##,
        ##"[{"op":"el","id":52,"tag":"a","attrs":[["href","https://127.0.0.1:9999/hit/a"]]},{"op":"ins","parent":4,"id":52,"before":null}]"##,
        ##"[{"op":"attr","id":4,"name":"onclick","value":"x()"}]"##,
        ##"[{"op":"el","id":53,"tag":"img","attrs":[["src","http://127.0.0.1:9999/hit/img"],["srcset","http://127.0.0.1:9999/hit/srcset 2x"]]},{"op":"ins","parent":4,"id":53,"before":null}]"##,
        ##"[{"op":"el","id":54,"tag":"iframe","attrs":[["srcdoc","<b>x</b>"]]},{"op":"ins","parent":4,"id":54,"before":null}]"##,
        ##"[{"op":"style","id":4,"name":"background-image","value":"url(http://127.0.0.1:9999/hit/bg)"}]"##,
        ##"[{"op":"style","id":4,"name":"background","value":"url( 'HTTP://127.0.0.1:9999/hit/bg2' )"}]"##,
        ##"[{"op":"style","id":4,"name":"--x","value":"url(http://127.0.0.1:9999/hit/var)"},{"op":"style","id":4,"name":"background","value":"var(--x)"}]"##,
        ##"[{"op":"el","id":55,"tag":"style","attrs":[]},{"op":"text","id":56,"data":"@import 'http://127.0.0.1:9999/hit/imp'; body{background:url(\\68 ttp://127.0.0.1:9999/hit/esc)}"},{"op":"ins","parent":55,"id":56,"before":null},{"op":"ins","parent":3,"id":55,"before":null}]"##,
        ##"[{"op":"attr","id":4,"name":"style","value":"background:url(http://127.0.0.1:9999/hit/styleattr)"}]"##,
        ##"[{"op":"el","id":57,"tag":"svg","svg":true,"attrs":[]},{"op":"el","id":58,"tag":"image","svg":true,"attrs":[["href","http://127.0.0.1:9999/hit/svgimg"],["xlink:href","http://127.0.0.1:9999/hit/xlink"]]},{"op":"ins","parent":57,"id":58,"before":null},{"op":"ins","parent":4,"id":57,"before":null}]"##,
        ##"[{"op":"el","id":59,"tag":"a","attrs":[["href","#"],["data-tg-link","https://127.0.0.1:9999/hit/tglink"]]},{"op":"ins","parent":4,"id":59,"before":null}]"##,
        ##"[{"op":"el","id":60,"tag":"link","attrs":[["rel","stylesheet"],["href","http://127.0.0.1:9999/hit/link"]]},{"op":"ins","parent":3,"id":60,"before":null}]"##,
        ##"[{"op":"el","id":61,"tag":"img","attrs":[["src","icon:../../../../etc/passwd"]]},{"op":"ins","parent":4,"id":61,"before":null},{"op":"el","id":62,"tag":"img","attrs":[["src","file:///etc/passwd"]]},{"op":"ins","parent":4,"id":62,"before":null}]"##,
        ##"[{"op":"el","id":63,"tag":"img","attrs":[["src","data:text/html,<script>1</script>"]]},{"op":"ins","parent":4,"id":63,"before":null}]"##,
        ##"[{"op":"el","id":64,"tag":"div","attrs":[["style","background:url(http://127.0.0.1:9999/hit/elstyle)"]]},{"op":"ins","parent":4,"id":64,"before":null}]"##,
        ##"[{"op":"el","id":65,"tag":"form","attrs":[["action","http://127.0.0.1:9999/hit/form"]]},{"op":"ins","parent":4,"id":65,"before":null}]"##,
        ##"[{"op":"el","id":66,"tag":"meta","attrs":[["http-equiv","refresh"],["content","0;url=http://127.0.0.1:9999/hit/meta"]]},{"op":"ins","parent":3,"id":66,"before":null}]"##,
        ##"[{"op":"el","id":67,"tag":"base","attrs":[["href","http://127.0.0.1:9999/"]]},{"op":"ins","parent":3,"id":67,"before":null}]"##,
    ]

    func testCraftedJSONOpsDoNotLeak() {
        SandboxEngine.hidesHostFunctions = false
        defer { SandboxEngine.hidesHostFunctions = true }
        var failures: [String] = []
        var rejected = 0
        for (i, ops) in Self.craftedJSON.enumerated() {
            let renderer = RecordingRenderer()
            let hex = ops.utf8.map { String(format: "%02x", $0) }.joined()
            let js = "var h='" + hex + "',s='';for(var k=0;k<h.length;k+=2)s+=String.fromCharCode(parseInt(h.substr(k,2),16));__host('ops',s);"
            let h = host("<div>x</div><script>" + js + "</script>", renderer: renderer)
            let hits = leaks(renderer)
            if !hits.isEmpty { failures.append("#\(i): " + hits.joined(separator: " | ")) }
            if h.diagnostics.diags["protocol-violation"] != nil || renderer.messages.count > 3 { rejected += 1 }
            h.destroy()
        }
        XCTAssertEqual(failures, [], failures.joined(separator: "\n"))
        XCTAssertGreaterThan(rejected, Self.craftedJSON.count / 2)
    }

    func jsString(_ s: String) -> String {
        let data = try! JSONSerialization.data(withJSONObject: [s], options: [])
        let text = String(decoding: data, as: UTF8.self)
        return String(text.dropFirst().dropLast())
    }

    func styleIds() -> [Int] {
        let renderer = RecordingRenderer()
        let h = host("<style>p{color:red}</style><p class=a>x</p>", renderer: renderer)
        defer { h.destroy() }
        var ids: [Int] = []
        for message in renderer.messages {
            guard let data = message.data(using: .utf8), let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let ops = object["ops"] as? [[String: Any]] else { continue }
            for op in ops {
                if op["op"] as? String == "el", op["tag"] as? String == "style", let id = op["id"] as? Int { ids.append(id) }
                if op["op"] as? String == "ins", let parent = op["parent"] as? Int, ids.contains(parent), let id = op["id"] as? Int { ids.append(id) }
            }
        }
        return ids
    }

    func testCraftedOpsCannotRewriteALegitStylesheet() {
        SandboxEngine.hidesHostFunctions = false
        defer { SandboxEngine.hidesHostFunctions = true }
        let ids = styleIds()
        var processed = 0
        XCTAssertFalse(ids.isEmpty)
        var failures: [String] = []
        let payload = "@import 'http://127.0.0.1:9999/hit/imp2'; body{background:url(http://127.0.0.1:9999/hit/bg3)}"
        for id in ids {
            for variant in 0..<3 {
                let ops: String
                switch variant {
                case 0: ops = "[{\"op\":\"text\",\"id\":90,\"data\":\(jsonString(payload))},{\"op\":\"ins\",\"parent\":\(id),\"id\":90,\"before\":null}]"
                case 1: ops = "[{\"op\":\"data\",\"id\":\(id),\"data\":\(jsonString(payload))}]"
                default: ops = "[{\"op\":\"attr\",\"id\":\(id),\"name\":\"style\",\"value\":\(jsonString("background:url(http://127.0.0.1:9999/hit/attr)"))}]"
                }
                let renderer = RecordingRenderer()
                let hex = ops.utf8.map { String(format: "%02x", $0) }.joined()
                let js = "var h='" + hex + "',s='';for(var k=0;k<h.length;k+=2)s+=String.fromCharCode(parseInt(h.substr(k,2),16));__host('ops',s);"
                let h = host("<style>p{color:red}</style><p class=a>x</p><script>" + js + "</script>", renderer: renderer)
                let reached = renderer.messages.dropFirst(2).map(Self.withoutTextData).filter { $0.contains("127.0.0.1") }
                if renderer.messages.count > 3 || h.diagnostics.diags["protocol-violation"] != nil { processed += 1 }
                if !reached.isEmpty { failures.append("id \(id) variant \(variant): " + reached.joined(separator: " || ").prefix(400)) }
                h.destroy()
            }
        }
        XCTAssertEqual(failures, [], failures.joined(separator: "\n"))
        XCTAssertGreaterThan(processed, 0)
    }

    func jsonString(_ s: String) -> String {
        let data = try! JSONSerialization.data(withJSONObject: [s], options: [])
        let text = String(decoding: data, as: UTF8.self)
        return String(text.dropFirst().dropLast())
    }

    func testHostFunctionsAreNotReachableFromWidgetCode() {
        let renderer = RecordingRenderer()
        let h = host("<div id=d>x</div><script>var out=[];['__host','__hostOps','__hostCanvas','__hostBinary','__hostOpsCheck','__twDispatch'].forEach(function(n){var t;try{t=eval('typeof '+n);}catch(e){t='err';}out.push(n+'='+t+'/'+(n in globalThis));});var c=document.createElement('canvas');var x=c.getContext('2d');x.fillRect(0,0,1,1);document.body.appendChild(c);document.getElementById('d').textContent='changed';console.log('HIDDEN '+out.join(' '));</script>", renderer: renderer)
        defer { h.destroy() }
        let line = h.diagnostics.logs.map { $0.text }.first { $0.hasPrefix("HIDDEN") } ?? ""
        for name in SandboxEngine.hostGlobals {
            XCTAssertTrue(line.contains(name + "=undefined/false"), line)
        }
        XCTAssertTrue(renderer.messages.contains { $0.contains("changed") })
        XCTAssertNil(h.diagnostics.dead)
    }

    func testPollutedShimCannotSmuggleOps() {
        let hexOf: (String) -> String = { $0.utf8.map { String(format: "%02x", $0) }.joined() }
        let crafted = hexOf(#"[{"op":"attr","id":5,"name":"onclick","value":"x()"},{"op":"attr","id":5,"name":"href","value":"javascript:alert(1)"},{"op":"style","id":5,"name":"background","value":"url(http://127.0.0.1:9999/hit/poll)"}]"#)
        let url = hexOf("http://127.0.0.1:9999/hit/poll2")
        let js = """
        var dec=function(h){var s='';for(var k=0;k<h.length;k+=2)s+=String.fromCharCode(parseInt(h.substr(k,2),16));return s;};
        var crafted=dec('\(crafted)'), url=dec('\(url)');
        var realStringify=JSON.stringify;
        JSON.stringify=function(v){var r=realStringify.apply(JSON,arguments);if(typeof r==='string'&&r.indexOf('"op"')>=0)return crafted;return r;};
        Object.prototype.href=url;Object.prototype.src=url;Object.prototype.onclick='x()';
        Object.defineProperty(Object.prototype,'attrs',{get:function(){return [['href',url],['onclick','x()']];},set:function(){},configurable:true});
        Array.prototype.push=(function(p){return function(){for(var i=0;i<arguments.length;i++){var a=arguments[i];if(a&&typeof a==='object'&&'op' in a){a.name='onclick';a.value='x()';}}return p.apply(this,arguments);};})(Array.prototype.push);
        String.prototype.toLowerCase=function(){return 'a';};
        String.prototype.trim=function(){return url;};
        var a=document.createElement('a');a.setAttribute('href','#x');a.textContent='p';document.body.appendChild(a);
        var i=document.createElement('img');i.setAttribute('src','app:icon');document.body.appendChild(i);
        a.style.background='red';a.className='z';a.setAttribute('title','t');
        """
        let renderer = RecordingRenderer()
        let h = host("<div id=d>x</div><script>" + js + "</script>", renderer: renderer)
        defer { h.destroy() }
        let out = renderer.messages.dropFirst(2).map { Self.withoutTextData($0).lowercased() }
        XCTAssertTrue(renderer.messages.contains { $0.contains("\"tag\":\"img\"") || $0.contains("#x") || $0.contains("\"z\"") }, renderer.messages.joined(separator: "\n").prefix(1500).description)
        let bad = out.filter { HostOpsFuzzTests.dangerous($0) || $0.contains("127.0.0.1") }
        XCTAssertEqual(bad, [], bad.joined(separator: "\n"))
    }

    func testWidgetClockIsCoarse() {
        let renderer = RecordingRenderer()
        let h = host("<script>var seen={},t0=performance.now(),distinct=0,fine=0;for(var i=0;i<200000;i++){var t=performance.now();if(!seen[t]){seen[t]=1;distinct++;var f=Math.round(t*10000)%1000;if(f!==0)fine++;}}console.log('CLOCK '+distinct+' '+fine+' '+(performance.now()-t0));</script>", renderer: renderer)
        defer { h.destroy() }
        let line = h.diagnostics.logs.map { $0.text }.first { $0.hasPrefix("CLOCK") } ?? ""
        let parts = line.split(separator: " ")
        XCTAssertEqual(parts.count, 4, line)
        if parts.count == 4, let distinct = Int(parts[1]), let elapsed = Double(parts[3]) {
            XCTAssertLessThanOrEqual(Double(distinct), elapsed / SandboxEngine.clockResolutionMs + 2, line)
        }
        XCTAssertEqual(SandboxEngine.coarseNowMs(1234.56789), 1234.5, accuracy: 1e-9)
    }
}

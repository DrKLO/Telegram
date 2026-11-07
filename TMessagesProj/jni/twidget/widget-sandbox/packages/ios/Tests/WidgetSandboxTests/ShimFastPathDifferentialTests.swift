import XCTest
@testable import WidgetSandbox

final class ShimFastPathDifferentialTests: XCTestCase {
    static func ops(_ html: String, features: Set<String>) -> String {
        let d = RecordingDelegate()
        var config = SandboxConfig(innerWidth: 400, innerHeight: 300, devicePixelRatio: 2, theme: "light")
        config.cpuLimitMs = 100_000
        config.cpuQuotaMs = 1_000_000
        config.features = features
        let engine = SandboxEngine(html: html, config: config)
        engine.delegate = d
        engine.start()
        engine.queue.sync {}
        for f in 0 ..< 4 {
            engine.frame(time: Double(f) * 16)
            engine.queue.sync {}
        }
        engine.terminate()
        engine.queue.sync {}
        return d.lock.withLock { d.ops.joined(separator: "\n") }
    }

    private static func rng(_ seed: UInt64) -> (Int) -> Int {
        var state = seed
        return { n in
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return Int((state >> 33) % UInt64(n))
        }
    }

    private static func page(_ body: String, _ steps: [String]) -> String {
        """
        \(body)<pre id="out"></pre><script>
        var c = [], keep = [], lists = [], log = [], frame = 0;
        for (var i = 0; i < 6; i++) c.push(document.getElementById('c' + i));
        function tick() {
          frame++;
          if (frame === 1) { \(steps[0 ..< 30].joined(separator: "\n")) }
          if (frame === 2) { \(steps[30...].joined(separator: "\n")) document.getElementById('out').textContent = log.join('\\n') || '-'; }
          if (frame < 3) requestAnimationFrame(tick);
        }
        requestAnimationFrame(tick);
        </script>
        """
    }

    static func attributeWidget(seed: UInt64) -> String {
        let next = rng(seed)
        let values = ["up", "down", "up down", "up  down", "", "x", "UP"]
        let names = "['class','CLASS','Class','id','data-x','nope','xlink:href','style']"
        var steps: [String] = []
        for k in 0 ..< 60 {
            let c = "c[\(next(6))]"
            let v = "'\(values[next(values.count)])'"
            switch next(16) {
            case 0, 1, 2: steps.append("\(c).className = \(v);")
            case 3: steps.append("\(c).setAttribute('class', \(v));")
            case 4: steps.append("\(c).removeAttribute('class');")
            case 5: steps.append("\(c).classList.add('z');")
            case 6: steps.append("\(c).classList.remove('up');")
            case 7: steps.append("var an = \(c).getAttributeNode('class'); if (an) an.value = \(v);")
            case 8: steps.append("\(c).setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', '#a\(k)');")
            case 9: steps.append("\(c).setAttribute('data-x', 'd\(k)');")
            case 10: steps.append("\(c).removeAttribute('data-x');")
            case 11: steps.append("lists.push(\(c).classList);")
            case 12 where seed % 4 == 0:
                steps.append("if (!window.mo) { window.mo = new MutationObserver(function (rs) { rs.forEach(function (r) { log.push('mo:' + r.type + ':' + r.attributeName + ':' + r.oldValue); }); }); mo.observe(document.body, { attributes: true, attributeOldValue: true, subtree: true }); }")
            default:
                steps.append("log.push(c.map(function (e) { return \(names).map(function (n) { return n + '=' + e.getAttribute(n); }).join(';') + '|' + e.className + '|' + e.classList.length; }).join(',') + '/' + lists.map(function (l) { return l.value; }).join(','));")
            }
        }
        return page("<div><b id=\"c0\" class=\"up\">0</b><b id=\"c1\">1</b><i id=\"c2\" class=\"up down\">2</i><svg><circle id=\"c3\" class=\"up\"/><rect id=\"c4\"/></svg><b id=\"c5\" class=\"UP\" data-x=\"1\">5</b></div>", steps)
    }

    static func textWidget(seed: UInt64) -> String {
        let next = rng(seed)
        var steps: [String] = []
        for k in 0 ..< 60 {
            let c = next(6)
            switch next(10) {
            case 0 ..< 5: steps.append("c[\(c)].textContent = 'v\(seed)_\(k)';")
            case 5: steps.append("keep.push(c[\(c)].firstChild);")
            case 6: steps.append("lists.push(c[\(c)].childNodes);")
            case 7: steps.append("keep.push(c[\(c)].lastChild);")
            case 8: steps.append("c[\(c)].textContent = '';")
            default: steps.append("log.push(keep.map(function (n) { return n ? n.data + ':' + n.isConnected + ':' + (n.parentNode ? n.parentNode.id : '-') : 'null'; }).join(',') + '/' + lists.map(function (l) { return l.length + ':' + (l[0] ? l[0].data : '-'); }).join(','));")
            }
        }
        return page("<div>" + (0 ..< 6).map { "<span id=\"c\($0)\">s\($0)</span>" }.joined() + "</div>", steps)
    }

    static func canvasWidget(seed: UInt64) -> String {
        let next = rng(seed)
        let args = ["1, 2, 3, 4", "0.5, -2, 1e3, 7", "1, 2, 3", "1, 2, 3, 4, 5", "1, 2, 3, undefined", "'1', 2, 3, 4", "1, 2, 3, NaN", "-0, Infinity, 3, 4", "null, 2, 3, 4", "1, 2, {}, 4"]
        let methods = ["fillRect", "strokeRect", "clearRect"]
        var calls: [String] = []
        for _ in 0 ..< 300 {
            switch next(6) {
            case 0: calls.append("x.fillStyle = 'hsl(\(next(360)),90%,60%)';")
            case 1: calls.append("x.save(); x.translate(\(next(9)), 1); x.restore();")
            default: calls.append("x.\(methods[next(3)])(\(args[next(args.count)]));")
            }
        }
        return """
            <canvas id=c width=200 height=120></canvas><script>
            var x = document.getElementById('c').getContext('2d'), frame = 0;
            function f() { frame++; \(calls.joined(separator: "\n")) if (frame < 3) requestAnimationFrame(f); }
            requestAnimationFrame(f);
            </script>
            """
    }

    func testAttributeFastPathsMatchFullPath() {
        for seed in UInt64(1) ... 14 {
            let html = Self.attributeWidget(seed: seed)
            let fast = Self.ops(html, features: [])
            XCTAssertTrue(fast.contains("\"id\":") && fast.contains("class"), "seed \(seed)")
            XCTAssertEqual(fast, Self.ops(html, features: ["noAttrFast"]), "seed \(seed)")
        }
    }

    func testInPlaceTextMatchesReplacingPath() {
        for seed in UInt64(1) ... 12 {
            let html = Self.textWidget(seed: seed)
            let inPlace = Self.ops(html, features: [])
            XCTAssertTrue(inPlace.contains("v\(seed)_"), "seed \(seed): frame work reached the host")
            XCTAssertEqual(inPlace, Self.ops(html, features: ["noInPlaceText"]), "seed \(seed)")
        }
    }

    func testRectFastPathMatchesGenericPath() {
        for seed in [UInt64(99), 7, 12345] {
            let html = Self.canvasWidget(seed: seed)
            let fast = Self.ops(html, features: ["canvasWords"])
            XCTAssertGreaterThan(fast.components(separatedBy: "fillRect").count, 50, "seed \(seed)")
            XCTAssertEqual(fast, Self.ops(html, features: ["canvasWords", "noCanvasRect"]), "seed \(seed)")
            XCTAssertEqual(fast, Self.ops(html, features: []), "seed \(seed): words splice to the JSON path")
        }
    }
}

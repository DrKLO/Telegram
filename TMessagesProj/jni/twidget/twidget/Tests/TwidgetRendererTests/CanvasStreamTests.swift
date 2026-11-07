import XCTest
@testable import TwidgetRenderer

final class CanvasStreamTests: XCTestCase {
    private func foundation(_ json: String) -> [(Int, CanvasStream)] {
        let message = try! JSONSerialization.jsonObject(with: json.data(using: .utf8)!) as! [String: Any]
        return (message["ops"] as! [[String: Any]]).map { op in
            ((op["id"] as! NSNumber).intValue, CanvasStream(commands: op["cmds"] as! [Any]))
        }
    }

    private func same(_ a: CanvasArg, _ b: CanvasArg) -> Bool {
        switch (a, b) {
        case let (.number(x), .number(y)):
            return x == y || abs(x - y) <= abs(y) * 1e-14
        case let (.list(x), .list(y)):
            return x.count == y.count && zip(x, y).allSatisfy { $0 == $1 || abs($0 - $1) <= abs($1) * 1e-14 }
        default:
            return a == b
        }
    }

    private func assertSame(_ json: String, file: StaticString = #file, line: UInt = #line) {
        guard let fast = CanvasStream.canvasOps(json: json) else {
            XCTFail("scanner rejected \(json.prefix(200))", file: file, line: line)
            return
        }
        let slow = foundation(json)
        XCTAssertEqual(fast.count, slow.count, file: file, line: line)
        for (f, s) in zip(fast, slow) {
            XCTAssertEqual(f.id, s.0, file: file, line: line)
            XCTAssertEqual(f.stream.ops.count, s.1.ops.count, file: file, line: line)
            for (a, b) in zip(f.stream.ops, s.1.ops) {
                XCTAssertEqual(a.kind, b.kind, file: file, line: line)
                XCTAssertEqual(a.name, b.name, file: file, line: line)
                XCTAssertTrue(a.ident == b.ident || abs(a.ident - b.ident) <= abs(b.ident) * 1e-14, file: file, line: line)
                let x = f.stream.arguments(a), y = s.1.arguments(b)
                XCTAssertEqual(x.count, y.count, file: file, line: line)
                for (p, q) in zip(x, y) {
                    XCTAssertTrue(same(p, q), "\(p) vs \(q)", file: file, line: line)
                }
            }
        }
    }

    func testScannerMatchesFoundation() {
        assertSame(#"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["s","fillStyle","rgba(11,16,32,0.35)"],["c","fillRect",0,0,800,600],["s","fillStyle","hsl(0,90%,60%)"],["c","fillRect",250.63250558859318,564.146822753673,3,3]]}]}"#)
        assertSame(#"{"type":"ops","ops":[{"op":"canvas","id":3,"cmds":[["c","arc",1e2,-2.5E-3,0.000001,0,6.283185307179586,true],["c","ellipse",1,2,3,4,5,6,7,false],["c","setLineDash",[6,4.5,-0]],["s","fillStyle",{"g":12}],["c","fill",{"p":4},"evenodd"],["c","drawImage",{"n":9},1,2],["c","stroke",null]]}]}"#)
        assertSame(##"{"type":"ops","ops":[{"op":"canvas","id":1,"cmds":[["r",640,480],["g",5,"linear",0,0,300,0],["gs",5,0.5,"#fff"],["pn",7,"M0 0L10 10"],["pc",7,"lineTo",3,4],["pc",7,"closePath"],["x","unknown",1],["c",5,1],["c","nosuchmethod",1,2],["s","globalCompositeOperation","lighter"]]},{"op":"canvas","id":2,"cmds":[]}]}"##)
        assertSame(#"{"type":"ops","ops":[{"op":"canvas","id":4,"cmds":[["c","fillRect",123456789012345678901234567890,0.1234567890123456789012345,1e308,-1e-320,1.7976931348623157e308]]}]}"#)
        assertSame(" {\"type\" : \"ops\" ,\n \"ops\":[ {\"id\":2 , \"op\":\"canvas\", \"cmds\": [ [ \"c\" , \"save\" ] ] } ] } ")
    }

    func testScannerNumbersMatchStrtod() {
        var seed: UInt64 = 0x9E3779B97F4A7C15
        func next() -> UInt64 {
            seed ^= seed << 13
            seed ^= seed >> 7
            seed ^= seed << 17
            return seed
        }
        var texts: [String] = ["0", "-0", "1", "0.5", "1e5", "1E-5", "-123.456e+7", "4.9e-324", "2.2250738585072014e-308", "9007199254740993", "0.30000000000000004"]
        for _ in 0..<2000 {
            let value = Double(bitPattern: next())
            if value.isFinite {
                texts.append("\(value)")
            }
            texts.append("\(Double(next() % 100_000_000) / 1000)")
        }
        for text in texts {
            let json = "{\"type\":\"ops\",\"ops\":[{\"op\":\"canvas\",\"id\":1,\"cmds\":[[\"c\",\"fillRect\",\(text)]]}]}"
            guard let parsed = CanvasStream.canvasOps(json: json), case let .number(v)? = parsed.first?.stream.args.first else {
                XCTFail(text)
                continue
            }
            let expected = Double(text)!
            if expected.isNormal && abs(expected) < 1e300 && abs(expected) > 1e-300 {
                XCTAssertLessThanOrEqual(abs(v - expected), abs(expected) * 4e-16, text)
            } else {
                XCTAssertTrue(v == expected || abs(v - expected) < 1e-300 || v.isInfinite == expected.isInfinite, text)
            }
        }
    }

    func testScannerDeclinesWhatItCannotRepresent() {
        let declined = [
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","fillText","a\"b",1,2]]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[]},{"op":"el","id":9,"tag":"div","attrs":[]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","putImageData",{"w":1,"h":1,"b64":"AAAA"},0,0]]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[]}],"extra":1}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[]}]}x"#,
            #"{"type":"query","ops":[{"op":"canvas","id":8,"cmds":[]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","fillRect",1,2,]]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","fillRect",01.]]}]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","fillRect",[[1]]]]}]}"#,
            #"{"type":"ops","ops":[]}"#,
            #"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["c","fillRect",1"#,
        ]
        for json in declined {
            XCTAssertNil(CanvasStream.canvasOps(json: json), json)
        }
    }

    func testScannerSurvivesMutations() {
        let base = Array(#"{"type":"ops","ops":[{"op":"canvas","id":8,"cmds":[["s","fillStyle","hsl(0,90%,60%)"],["c","fillRect",250.63,564.1e1,3,-3],["c","setLineDash",[1,2]],["s","fillStyle",{"g":1}],["pc",3,"arc",1,2,3,4,5,true]]}]}"#.utf8)
        var seed: UInt64 = 42
        func next() -> Int {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return Int(seed >> 33)
        }
        let alphabet = Array("{}[],:\"0123456789.eE-+ \\tnullfasecgsrpn".utf8)
        for _ in 0..<20000 {
            var bytes = base
            for _ in 0..<(1 + next() % 4) {
                let at = next() % bytes.count
                switch next() % 3 {
                case 0:
                    bytes[at] = alphabet[next() % alphabet.count]
                case 1:
                    bytes.remove(at: at)
                default:
                    bytes.insert(alphabet[next() % alphabet.count], at: at)
                }
            }
            let text = String(decoding: bytes, as: UTF8.self)
            if let parsed = CanvasStream.canvasOps(json: text) {
                for (_, stream) in parsed {
                    for op in stream.ops {
                        XCTAssertLessThanOrEqual(Int(op.start) + Int(op.count), stream.args.count)
                    }
                }
            }
        }
    }

    func testTypedStreamDrawsLikeCommands() {
        let commands: [Any] = [["s", "fillStyle", "#3366cc"], ["c", "fillRect", 10, 10, 50, 30], ["c", "beginPath"], ["c", "arc", 60, 60, 20, 0, 6.283185307179586], ["s", "fillStyle", "rgba(255,0,0,0.5)"], ["c", "fill"]]
        let a = WidgetCanvas(width: 100, height: 100, allowance: { WidgetCanvas.maxPixels })
        a.run(commands)
        let json = "{\"type\":\"ops\",\"ops\":[{\"op\":\"canvas\",\"id\":1,\"cmds\":" + String(data: try! JSONSerialization.data(withJSONObject: commands), encoding: .utf8)! + "}]}"
        let b = WidgetCanvas(width: 100, height: 100, allowance: { WidgetCanvas.maxPixels })
        b.execute(CanvasStream.canvasOps(json: json)!.first!.stream)
        XCTAssertEqual(a.pixels(x: 0, y: 0, w: 100, h: 100), b.pixels(x: 0, y: 0, w: 100, h: 100))
    }
}

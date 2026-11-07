import XCTest
import QuartzCore
@testable import HTMLScript

final class QuickJSEngineTests: XCTestCase {
    private func context() -> QuickJSContext {
        QuickJSContext(memoryLimitBytes: 256 << 20, maxStackBytes: 256 << 10)
    }

    private func elapsed(_ js: QuickJSContext, _ code: String) throws -> (Double, String) {
        let t0 = CACurrentMediaTime()
        let r = try js.evaluate(code, filename: "t.js")
        return (CACurrentMediaTime() - t0, r.string ?? "\(r.number ?? .nan)")
    }

    func testMapKeysKeepSameValueZeroSemantics() throws {
        let js = context()
        let r = try js.evaluate("""
        (() => {
          const o = {}, s = Symbol('s'), m = new Map();
          m.set(0, 'zero'); m.set(NaN, 'nan'); m.set(1.5, 'f'); m.set('1', 'str'); m.set(o, 'obj'); m.set(s, 'sym'); m.set(2n, 'big'); m.set(true, 't');
          const ok = m.get(-0) === 'zero' && m.get(0/0) === 'nan' && m.get(3/2) === 'f' && m.get(1) === undefined && m.get('1') === 'str'
            && m.get(o) === 'obj' && m.get({}) === undefined && m.get(s) === 'sym' && m.get(2n) === 'big' && m.get(1 === 1) === 't';
          const order = [...m.values()].join(',');
          const set = new Set([3, 1, 2, 1, 3.0, -0, 0]);
          return ok + '|' + order + '|' + [...set].join(',');
        })()
        """, filename: "t.js")
        XCTAssertEqual(r.string, "true|zero,nan,f,str,obj,sym,big,t|3,1,2,0")
    }

    func testMapWithSmallIntegerKeysIsNotLinear() throws {
        let js = context()
        let n = 2000, rounds = 50
        let body = "let m = new Map(); for (let i = 0; i < \(n); i++) m.set(K, i); let s = 0; for (let r = 0; r < \(rounds); r++) for (let i = 0; i < \(n); i++) s += m.get(K); return s"
        let (intTime, intSum) = try elapsed(js, "(() => { \(body.replacingOccurrences(of: "K", with: "i")) })()")
        let (strTime, strSum) = try elapsed(js, "(() => { \(body.replacingOccurrences(of: "K", with: "('k' + i)")) })()")
        let objs = try elapsed(js, "(() => { const ks = []; for (let i = 0; i < \(n); i++) ks.push({}); \(body.replacingOccurrences(of: "K", with: "ks[i]")) })()")
        let expected = "\(Double(rounds * n * (n - 1) / 2))"
        XCTAssertEqual(intSum, expected)
        XCTAssertEqual(strSum, expected)
        XCTAssertEqual(objs.1, expected)
        XCTAssertLessThan(intTime, max(strTime, 0.01) * 4, "integer keys \(intTime)s vs string keys \(strTime)s")
        XCTAssertLessThan(objs.0, max(strTime, 0.01) * 4, "object keys \(objs.0)s vs string keys \(strTime)s")
    }

    func testJSONStringifyOutputIsUnchanged() throws {
        let js = context()
        let script = #"""
[
  JSON.stringify([0, -0, 1.5, 0.1, 1e21, 1e-7, 2 ** 53, 5e-324, NaN, -Infinity, 525.7130405604839, -123, 2147483647, -2147483648]),
  JSON.stringify({ op: 'style', id: 13, name: 'transform', value: 'translate(250.5px,91.3px)', important: undefined }),
  JSON.stringify(['a"b\\c\n\t\r\b\f\u0000\u001f\u007f', 'é€😀', '\ud800x\udc00', 'ÿ' + 'abc'.repeat(3)]),
  JSON.stringify({ 'k"ey': 1, 'é': [undefined, () => 1, Symbol('s')], 3: { toJSON(k) { return 'key ' + k; } } }),
  JSON.stringify([{ toJSON(k) { return 'idx ' + k; } }, new Number(2.5), new String('s'), new Boolean(false), [,1]]),
  JSON.stringify([1, [2.25, 'x'], { a: 1 }], (k, v) => typeof v === 'number' ? v * 2 : v),
  JSON.stringify({ a: [1, { b: 2 }], c: 'd' }, null, 2),
  JSON.stringify({ a: 1, b: 2, c: 3 }, ['c', 'a']),
].join('\n')
"""#
        let expected = String(decoding: Data(base64Encoded: "WzAsMCwxLjUsMC4xLDFlKzIxLDFlLTcsOTAwNzE5OTI1NDc0MDk5Miw1ZS0zMjQsbnVsbCxudWxsLDUyNS43MTMwNDA1NjA0ODM5LC0xMjMsMjE0NzQ4MzY0NywtMjE0NzQ4MzY0OF0KeyJvcCI6InN0eWxlIiwiaWQiOjEzLCJuYW1lIjoidHJhbnNmb3JtIiwidmFsdWUiOiJ0cmFuc2xhdGUoMjUwLjVweCw5MS4zcHgpIn0KWyJhXCJiXFxjXG5cdFxyXGJcZlx1MDAwMFx1MDAxZn8iLCLDqeKCrPCfmIAiLCJcdWQ4MDB4XHVkYzAwIiwiw79hYmNhYmNhYmMiXQp7IjMiOiJrZXkgMyIsImtcImV5IjoxLCLDqSI6W251bGwsbnVsbCxudWxsXX0KWyJpZHggMCIsMi41LCJzIixmYWxzZSxbbnVsbCwxXV0KWzIsWzQuNSwieCJdLHsiYSI6Mn1dCnsKICAiYSI6IFsKICAgIDEsCiAgICB7CiAgICAgICJiIjogMgogICAgfQogIF0sCiAgImMiOiAiZCIKfQp7ImMiOjMsImEiOjF9")!, as: UTF8.self)
        XCTAssertEqual(try js.evaluate(script, filename: "t.js").string, expected)
    }

    func testRegExpBytecodeReuseKeepsObjectsIndependent() throws {
        let js = context()
        let r = try js.evaluate(#"""
        (() => {
          const a = new RegExp('a(b)?', 'g'), b = new RegExp('a(b)?', 'g'), c = new RegExp('a(b)?', 'y'), d = new RegExp('a(b)?');
          const s = 'xab ab a';
          const first = [a.exec(s).index, a.lastIndex, b.lastIndex, a.exec(s).index, b.exec(s).index];
          const sticky = [c.test(s), c.lastIndex, d.test(s), d.lastIndex, a !== b];
          const splits = ['a  b c'.split(/\s+/).join('|'), 'a  b c'.split(new RegExp('\\s+')).join('|'), 'A b'.split(new RegExp('a', 'i')).length];
          let errors = 0;
          for (const [p, f] of [['(', ''], ['a', 'gg'], ['a', 'uv']]) { try { new RegExp(p, f); } catch (e) { errors++; } }
          for (let i = 0; i < 40; i++) new RegExp('p' + i, i % 2 ? 'g' : '');
          const after = new RegExp('a(b)?', 'g');
          return JSON.stringify([first, sticky, splits, errors, after.exec(s).index, after.flags, d.flags, c.sticky]);
        })()
        """#, filename: "t.js")
        XCTAssertEqual(r.string, #"[[1,3,0,4,1],[false,0,true,0,true],["a|b|c","a|b|c",2],3,1,"g","",true]"#)
    }
}

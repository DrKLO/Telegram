import XCTest
@testable import TwidgetRenderer

final class CompositorHotPathTests: XCTestCase {
    private struct Rng {
        var state: UInt64
        mutating func next() -> UInt64 {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return state >> 33
        }
        mutating func below(_ n: Int) -> Int {
            return Int(next() % UInt64(max(n, 1)))
        }
    }

    private static func referenceResolve(_ css: String, theme: String, depth: Int = 0) -> String {
        guard depth < 8, css.range(of: "light-dark(", options: .caseInsensitive) != nil else {
            return css
        }
        let dark = theme == "dark"
        var out = ""
        var rest = Substring(css)
        while let range = rest.range(of: "light-dark(", options: .caseInsensitive) {
            out += rest[..<range.lowerBound]
            var depth = 1
            var index = range.upperBound
            while index < rest.endIndex, depth > 0 {
                let ch = rest[index]
                if ch == "(" {
                    depth += 1
                } else if ch == ")" {
                    depth -= 1
                }
                if depth > 0 {
                    index = rest.index(after: index)
                }
            }
            let inner = String(rest[range.upperBound..<index])
            let parts = WidgetNode.splitTopLevel(inner, separator: ",")
            let chosen = parts.count == 2 ? parts[dark ? 1 : 0] : (parts.first ?? "")
            out += referenceResolve(chosen.trimmingCharacters(in: .whitespaces), theme: theme, depth: depth + 1)
            rest = index < rest.endIndex ? rest[rest.index(after: index)...] : ""
        }
        out += rest
        return out
    }

    func testThemeResolveMatchesReference() {
        let pieces = ["light-dark(", "LIGHT-DARK(", "Light-Dark(", "light-dark", "dark(", "light-", "(", ")", ",", " ", "#000", "#fff", "rgb(1,2,3)", "red", "ﬁ", "é", "ı", "\u{212A}", "ligh\u{0130}-dark(", "light-dar\u{212A}(", "LIGHT-DAR\u{212A}(", "😀", "color:", ";", "var(--x)", "\u{0}"]
        var rng = Rng(state: 42)
        let fixed = ["", "l", "light-dark(", "light-dark(a,b)", "xlight-dark(a,b)", "light-dark(light-dark(a,b),c)", "light-dar\u{212A}(a,b)", "lIgHt-DaRk( #000 , #fff )"]
        var inputs = fixed
        for _ in 0..<4000 {
            var text = ""
            for _ in 0..<rng.below(12) {
                text += pieces[rng.below(pieces.count)]
            }
            inputs.append(text)
        }
        for input in inputs {
            for theme in ["light", "dark"] {
                XCTAssertEqual(CSSTheme.resolve(input, theme: theme), CompositorHotPathTests.referenceResolve(input, theme: theme), input.debugDescription)
            }
        }
    }

    func testScriptedConflictsMatchPairwiseAncestorChecks() {
        var rng = Rng(state: 7)
        for _ in 0..<200 {
            let dom = WidgetDOM()
            dom.apply(["op": "el", "id": 1, "tag": "body", "attrs": [], "adopt": "body"])
            var ids = [1]
            for id in 2..<(2 + 10 + rng.below(60)) {
                dom.apply(["op": "el", "id": id, "tag": "div", "attrs": []])
                dom.apply(["op": "ins", "id": id, "parent": ids[rng.below(ids.count)]])
                ids.append(id)
            }
            func ancestors(_ id: Int) -> Set<Int> {
                var result = Set<Int>()
                var node = dom.node(id)
                while let parent = node?.parent {
                    result.insert(dom.publicId(parent))
                    node = parent
                }
                return result
            }
            func isAncestor(_ a: Int, of b: Int) -> Bool {
                return ancestors(b).contains(a)
            }
            let wanted = Set(ids.filter { _ in rng.below(6) == 0 })
            let candidates = Set(ids.filter { _ in rng.below(4) == 0 })
            let related = wanted.union(candidates)
            let union = WidgetCompositor.ancestorUnion(related, ancestors: ancestors)
            for id in candidates where !wanted.contains(id) {
                let reference = wanted.contains(where: { isAncestor(id, of: $0) || isAncestor($0, of: id) }) || candidates.contains(where: { $0 != id && (isAncestor(id, of: $0) || isAncestor($0, of: id)) })
                XCTAssertEqual(WidgetCompositor.conflicts(id, related: related, relatedAncestors: union, ancestors: ancestors(id)), reference)
            }
        }
    }
}

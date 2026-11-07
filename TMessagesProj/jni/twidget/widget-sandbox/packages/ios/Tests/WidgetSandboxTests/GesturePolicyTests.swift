import XCTest
@testable import WidgetSandbox

/// GesturePolicy.swift against the fixture shared with gesture-policy.ts, GesturePolicy.kt and gesture_policy.cpp
/// (tests/fixtures/gesture-policy.json, copied here by `pnpm build:ios`): every section.
final class GesturePolicyTests: XCTestCase {
    private var fixture: [String: Any] = [:]

    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "gesture-policy", withExtension: "json", subdirectory: "Fixtures"))
        fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func list(_ section: String) throws -> [[String: Any]] {
        let list = try XCTUnwrap(fixture[section] as? [[String: Any]], section)
        XCTAssertFalse(list.isEmpty, section)
        return list
    }

    private func number(_ v: Any?, _ note: String) throws -> Double {
        try XCTUnwrap((v as? NSNumber)?.doubleValue, note)
    }

    func testConstants() throws {
        let constants = try XCTUnwrap(fixture["constants"] as? [String: Any])
        let swift = [
            "TOUCH_SLOP_PX": GesturePolicy.touchSlopPx,
            "HOLD_MS": GesturePolicy.holdMs,
            "DOUBLE_TAP_MS": GesturePolicy.doubleTapMs,
            "DOUBLE_TAP_SLOP_PX": GesturePolicy.doubleTapSlopPx,
            "EDGE_STRIP_PX": GesturePolicy.edgeStripPx,
        ]
        XCTAssertEqual(Set(constants.keys), Set(swift.keys))
        for (name, value) in constants {
            XCTAssertEqual(swift[name], try number(value, name), name)
        }
    }

    private func claim(_ v: Any?, _ note: String) throws -> WidgetGestureClaim {
        let o = try XCTUnwrap(v as? [String: Bool], note)
        XCTAssertEqual(Set(o.keys), ["x", "y", "hold"], note)
        return WidgetGestureClaim(x: o["x"]!, y: o["y"]!, hold: o["hold"]!)
    }

    func testClaims() throws {
        for c in try list("claims") {
            let actions = try XCTUnwrap(c["touchActions"] as? [String])
            let prevented = try XCTUnwrap(c["prevented"] as? Bool)
            let note = "\(actions) prevented \(prevented)"
            XCTAssertEqual(GesturePolicy.claimOf(actions, prevented: prevented), try claim(c["expect"], note), note)
        }
    }

    /// A step's `expect`: a verdict (owner, gesture, cancel always; action, event, edge only when set) or JSON null.
    private func verdict(_ v: Any?, _ note: String) throws -> GestureVerdict? {
        if v is NSNull { return nil }
        let o = try XCTUnwrap(v as? [String: Any], note)
        XCTAssertTrue(Set(o.keys).isSubset(of: ["owner", "gesture", "cancel", "action", "event", "edge"]), note)
        let owner = try XCTUnwrap((o["owner"] as? String).flatMap(GestureVerdict.Owner.init(rawValue:)), note)
        let gesture = try XCTUnwrap((o["gesture"] as? String).flatMap(GestureVerdict.Gesture.init(rawValue:)), note)
        let cancel = try XCTUnwrap(o["cancel"] as? Bool, note)
        let action = try o["action"].map { try XCTUnwrap(($0 as? String).flatMap(GestureVerdict.BubbleAction.init(rawValue:)), note) }
        let event = try o["event"].map { try XCTUnwrap(($0 as? String).flatMap(GestureVerdict.Event.init(rawValue:)), note) }
        let edge = try o["edge"].map { try XCTUnwrap($0 as? Bool, note) }
        XCTAssertNotEqual(edge, false, "\(note): edge is only written when true")
        return GestureVerdict(owner: owner, gesture: gesture, cancel: cancel, action: action, event: event, edge: edge ?? false)
    }

    private func point(_ v: Any?, _ note: String) throws -> (t: Double, x: Double, y: Double) {
        let a = try XCTUnwrap(v as? [NSNumber], note)
        XCTAssertEqual(a.count, 3, note)
        return (a[0].doubleValue, a[1].doubleValue, a[2].doubleValue)
    }

    func testCases() throws {
        for c in try list("cases") {
            let name = c["name"] as? String ?? "?"
            var a = GestureArbiter()
            for (i, step) in try XCTUnwrap(c["steps"] as? [[String: Any]], name).enumerated() {
                let note = "\(name): step \(i)"
                if let down = step["down"] {
                    let p = try point(down, note)
                    // `claimed`: a claim object as given (what a client may build by hand), else claimOf([claim]).
                    XCTAssertFalse(step["claimed"] != nil && step["claim"] != nil, "\(note): claim or claimed")
                    let claim = try step["claimed"].map { try self.claim($0, note) } ?? GesturePolicy.claimOf([step["claim"] as? String ?? "auto"])
                    let pointer = try XCTUnwrap(GesturePolicy.PointerKind(rawValue: step["pointer"] as? String ?? "touch"), note)
                    let button = (step["button"] as? NSNumber)?.intValue ?? 0
                    XCTAssertEqual(a.down(at: p.t, x: p.x, y: p.y, claim: claim, pointer: pointer, button: button), try verdict(step["expect"], note), note)
                } else if let move = step["move"] {
                    let p = try point(move, note)
                    XCTAssertEqual(a.move(at: p.t, x: p.x, y: p.y), try verdict(step["expect"], note), note)
                } else if let up = step["up"] {
                    let p = try point(up, note)
                    XCTAssertEqual(a.up(at: p.t, x: p.x, y: p.y), try verdict(step["expect"], note), note)
                } else if let tick = step["tick"] {
                    XCTAssertEqual(a.tick(at: try number(tick, note)), try verdict(step["expect"], note), note)
                } else if step["cancel"] as? Bool == true {
                    a.cancel()
                } else if let deadline = step["holdDeadline"] {
                    XCTAssertEqual(a.holdDeadline(), deadline is NSNull ? nil : try number(deadline, note), note)
                } else {
                    XCTFail("\(note): unknown step")
                }
            }
        }
    }

    /// The fixture reaches every verdict kind the Swift types can express.
    func testFixtureCoversEveryGestureAndAction() throws {
        let text = String(decoding: try JSONSerialization.data(withJSONObject: fixture["cases"] as Any), as: UTF8.self)
        for g in ["press", "tap", "double-tap", "hold", "drag-x", "drag-y", "menu-click"] { XCTAssertNotNil(GestureVerdict.Gesture(rawValue: g)); XCTAssertTrue(text.contains("\"\(g)\""), g) }
        for b in ["menu", "swipe", "scroll", "back"] { XCTAssertNotNil(GestureVerdict.BubbleAction(rawValue: b)); XCTAssertTrue(text.contains("\"\(b)\""), b) }
        for e in ["dblclick", "contextmenu"] { XCTAssertNotNil(GestureVerdict.Event(rawValue: e)); XCTAssertTrue(text.contains("\"\(e)\""), e) }
    }
}

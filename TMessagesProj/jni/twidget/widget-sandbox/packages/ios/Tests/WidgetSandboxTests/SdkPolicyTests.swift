import XCTest
@testable import WidgetSandbox

/// SdkPolicy.swift against the fixture shared with sdk-policy.ts, SdkPolicy.kt and sdk_policy.cpp
/// (tests/fixtures/sdk-policy.json, copied here by `pnpm build:ios`): every section.
final class SdkPolicyTests: XCTestCase {
    private var fixture: [String: Any] = [:]

    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "sdk-policy", withExtension: "json", subdirectory: "Fixtures"))
        fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func cases(_ section: String) throws -> [[String: Any]] {
        let list = try XCTUnwrap(fixture[section] as? [[String: Any]], section)
        XCTAssertFalse(list.isEmpty, section)
        return list
    }

    /// JSON null is no value.
    private func value(_ v: Any?) -> Any? { v is NSNull ? nil : v }

    /// `{ "repeat": [s, n] }` stands for s repeated n times.
    private func expand(_ v: Any?) -> Any? {
        if let a = v as? [Any] { return a.map { expand($0) ?? NSNull() } }
        if let o = v as? [String: Any] {
            if o.count == 1, let r = o["repeat"] as? [Any], r.count == 2, let s = r[0] as? String, let n = (r[1] as? NSNumber)?.intValue {
                return String(repeating: s, count: n)
            }
            return o.mapValues { expand($0) ?? NSNull() }
        }
        return value(v)
    }

    private func short(_ v: Any?) -> String { String(String(describing: v ?? "nil").prefix(80)) }

    func testLinks() throws {
        for c in try cases("links") {
            let method = try XCTUnwrap(c["method"] as? String)
            XCTAssertEqual(SdkPolicy.checkLink(method, value(c["url"])), value(c["expect"]) as? String, "\(method) \(short(c["url"]))")
        }
    }

    private func hapticDict(_ h: SdkPolicy.HapticCall?) -> [String: String]? {
        guard let h else { return nil }
        var d = ["haptic": h.haptic]
        if let v = h.value { d["value"] = v }
        return d
    }

    func testHaptics() throws {
        for c in try cases("haptics") {
            XCTAssertEqual(hapticDict(SdkPolicy.checkHaptic(c["args"])), value(c["expect"]) as? [String: String], short(c["args"]))
        }
    }

    private func actionDict(_ a: SdkPolicy.Action?) -> NSDictionary? {
        switch a {
        case nil: return nil
        case .ready: return ["kind": "ready"]
        case .openLink(let url): return ["kind": "openLink", "url": url]
        case .openTelegramLink(let url): return ["kind": "openTelegramLink", "url": url]
        case .haptic(let h): return hapticDict(h)!.merging(["kind": "haptic"]) { a, _ in a } as NSDictionary
        case .setResizeAnchor(let edge): return ["kind": "setResizeAnchor", "edge": edge.rawValue]
        case .watchPublicData(let on): return ["kind": "watchPublicData", "on": on]
        case .request(let id, let data, let wallet):
            return wallet ? ["kind": "request", "id": id, "data": data, "wallet": true] : ["kind": "request", "id": id, "data": data]
        case .wallet(let id, let call):
            var c: [String: Any] = ["method": call.method, "data": call.data]
            switch call {
            case .sendTransaction(let tx, _): c["transaction"] = tx.json
            case .signData(let payload, _): c["payload"] = payload.json
            }
            return ["kind": "wallet", "id": id, "call": c]
        case .copyText(let text): return ["kind": "copyText", "text": text]
        }
    }

    func testBridgeCalls() throws {
        var anchors = 0, watches = 0, requests = 0, wallets = 0, copies = 0
        for c in try cases("bridge") {
            let checked = SdkPolicy.checkBridgeCall(value(c["method"]), value(c["args"]))
            XCTAssertEqual(actionDict(checked), value(c["expect"]) as? NSDictionary, "\(short(c["method"])) \(short(c["args"]))")
            switch checked {
            case .setResizeAnchor?: anchors += 1
            case .watchPublicData?: watches += 1
            case .request?: requests += 1
            case .wallet?: wallets += 1
            case .copyText?: copies += 1
            default: continue
            }
            XCTAssertNil(WidgetAction(checked!), "the anchor, the watch, a request, a wallet call and a copy are never a WidgetAction")
        }
        XCTAssertEqual(anchors, 2, "the fixture's setResizeAnchor entries for top and bottom")
        XCTAssertEqual(watches, 2, "the fixture's watchPublicData entries for true and false")
        XCTAssertEqual(requests, 9, "the fixture's valid request entries")
        XCTAssertEqual(wallets, 3, "the fixture's valid wallet entries")
        XCTAssertEqual(copies, 4, "the fixture's valid copyText entries")
    }

    /// The single-argument check behind `setResizeAnchor` (the fixture covers it through `bridge`).
    func testResizeAnchor() {
        XCTAssertEqual(SdkPolicy.defaultResizeAnchor, .top)
        XCTAssertEqual(SdkPolicy.checkResizeAnchor("top"), .top)
        XCTAssertEqual(SdkPolicy.checkResizeAnchor("bottom"), .bottom)
        for bad: Any? in [nil, NSNull(), "Top", "bottom ", "", "left", 1, true, ["top"], ["edge": "top"]] {
            XCTAssertNil(SdkPolicy.checkResizeAnchor(bad), short(bad))
        }
    }

    func testHapticGate() throws {
        for c in try cases("hapticGate") {
            let name = c["name"] as? String ?? "?"
            var gate = SdkPolicy.HapticGate()
            for (i, step) in try XCTUnwrap(c["steps"] as? [[String: Any]]).enumerated() {
                let at = try XCTUnwrap((step["at"] as? NSNumber)?.doubleValue)
                switch step["touch"] as? String {
                case "down": gate.press(nowMs: at)
                case "up": gate.release(nowMs: at)
                default:
                    let visible = try XCTUnwrap(step["visible"] as? Bool)
                    XCTAssertEqual(gate.decide(nowMs: at, visible: visible).rawValue, step["expect"] as? String, "\(name), step \(i)")
                }
            }
        }
    }

    func testThemeParams() throws {
        for c in try cases("themeParams") {
            XCTAssertEqual(SdkPolicy.checkThemeParams(value(c["input"])), try XCTUnwrap(c["expect"] as? [String: String]), short(c["input"]))
        }
    }

    func testLocales() throws {
        for c in try cases("locales") {
            XCTAssertEqual(SdkPolicy.checkLocale(value(c["input"])), value(c["expect"]) as? String, short(c["input"]))
        }
    }

    private func commitDict(_ c: SdkPolicy.StorageCommit?) -> [String: [String: String]]? {
        guard let c else { return nil }
        var d: [String: [String: String]] = [:]
        if let local = c.local { d["local"] = local }
        if let session = c.session { d["session"] = session }
        return d
    }

    func testStorage() throws {
        for c in try cases("storage") {
            let note = c["note"] as? String ?? short(c["input"])
            let expected = expand(c["expect"])
            let got = commitDict(SdkPolicy.checkStorageCommit(expand(c["input"])))
            if expected == nil {
                XCTAssertNil(got, note)
            } else {
                XCTAssertEqual(got, try XCTUnwrap(expected as? [String: [String: String]], note), note)
            }
        }
    }

    func testUtf8() throws {
        for c in try cases("utf8") {
            let s = try XCTUnwrap(c["s"] as? String)
            XCTAssertEqual(SdkPolicy.utf8Length(s), (c["bytes"] as? NSNumber)?.intValue, s)
        }
    }

    // MARK: bot data and requests

    private func dataDict(_ d: SdkPolicy.CheckedWidgetData?) -> NSDictionary? {
        guard let d else { return nil }
        return ["chatData": SandboxEngine.orNull(d.chatData), "publicData": SandboxEngine.orNull(d.publicData), "rejected": d.rejected]
    }

    func testWidgetData() throws {
        for c in try cases("widgetData") {
            let note = c["note"] as? String ?? short(c["input"])
            let copy = try XCTUnwrap(c["copy"] as? Bool, note)
            let expected = try XCTUnwrap(expand(c["expect"]) as? NSDictionary, note)
            XCTAssertEqual(dataDict(SdkPolicy.checkWidgetData(expand(c["input"]), copy: copy)), expected, note)
            // The typed form the host takes gives the same.
            let input = try XCTUnwrap(expand(c["input"]) as? [String: Any], note)
            let typed = WidgetData(chatData: input["chatData"] as? String, publicData: input["publicData"] as? String)
            XCTAssertEqual(dataDict(SdkPolicy.checkWidgetData(typed, copy: copy)), expected, note)
        }
    }

    /// The TS shape: `{ viewer, rejected }`, the viewer in the widget's field names or NSNull.
    private func viewerDict(_ c: SdkPolicy.CheckedViewer) -> NSDictionary {
        ["viewer": SandboxEngine.orNull(c.viewer.map { $0.json as NSDictionary }), "rejected": c.rejected]
    }

    func testViewer() throws {
        var typedCases = 0
        for c in try cases("viewer") {
            let note = c["note"] as? String ?? short(c["input"])
            let expected = try XCTUnwrap(expand(c["expect"]) as? NSDictionary, note)
            XCTAssertEqual(viewerDict(SdkPolicy.checkViewer(expand(c["input"]))), expected, note)
            // The typed form the host takes gives the same (the inputs are typed as the native hosts take them).
            guard let input = expand(c["input"]) as? [String: Any], let id = (input["id"] as? NSNumber)?.int64Value else { continue }
            typedCases += 1
            let typed = WidgetViewer(id: id, firstName: input["first_name"] as? String, lastName: input["last_name"] as? String,
                                     username: input["username"] as? String, isPremium: input["is_premium"] as? Bool ?? false)
            XCTAssertEqual(viewerDict(SdkPolicy.checkViewer(typed)), expected, "typed: " + note)
        }
        XCTAssertGreaterThan(typedCases, 20)
    }

    /// What the fixture cannot carry: JSON types a native host never passes, and ids past 2^53 as Int64.
    func testViewerEdges() {
        let none = SdkPolicy.CheckedViewer(viewer: nil, rejected: [])
        for bad: Any? in [["id": "1"], ["id": 1.5], ["id": true], ["id": NSNull()], [:] as [String: Any], 7, "7"] {
            XCTAssertEqual(SdkPolicy.checkViewer(bad), none, short(bad))
        }
        XCTAssertEqual(SdkPolicy.checkViewer(["id": 7, "first_name": 5, "last_name": NSNull(), "username": "artem", "is_premium": 1, "photo_url": "x"] as [String: Any]),
                       SdkPolicy.CheckedViewer(viewer: WidgetViewer(id: 7, username: "artem"), rejected: ["first_name", "is_premium"]),
                       "a number is no name and no boolean, null is absent, other fields are not passed on")
        XCTAssertEqual(SdkPolicy.checkViewer(["id": 7, "is_premium": false] as [String: Any]), SdkPolicy.CheckedViewer(viewer: WidgetViewer(id: 7), rejected: []))
        XCTAssertEqual(SdkPolicy.checkViewer(WidgetViewer(id: 9007199254740993, firstName: "Ann")), none)
        XCTAssertEqual(SdkPolicy.checkViewer(WidgetViewer(id: .max)), none)
        XCTAssertEqual(SdkPolicy.checkViewer(WidgetViewer(id: -1)), none)
        XCTAssertEqual(SdkPolicy.checkViewer(nil), none)
    }

    func testChatType() throws {
        for c in try cases("chatType") {
            XCTAssertEqual(SdkPolicy.checkChatType(value(c["input"]))?.rawValue, value(c["expect"]) as? String, short(c["input"]))
        }
        for type in WidgetChatType.allCases { XCTAssertEqual(SdkPolicy.checkChatType(type), type) }
    }

    private func answerDict(_ a: SdkPolicy.CheckedAnswer?) -> NSDictionary? {
        a.map { ["result": SandboxEngine.orNull($0.result)] }
    }

    func testAnswers() throws {
        for c in try cases("answers") {
            let note = c["note"] as? String ?? short(c["input"])
            let expected = expand(c["expect"]) as? NSDictionary
            XCTAssertEqual(answerDict(SdkPolicy.checkRequestAnswer(expand(c["input"]))), expected, note)
            // The typed form the host takes gives the same.
            let input = try XCTUnwrap(expand(c["input"]) as? [String: Any], note)
            XCTAssertEqual(answerDict(SdkPolicy.checkRequestAnswer(WidgetRequestAnswer(result: input["result"] as? String))), expected, "typed: " + note)
        }
    }

    /// What the fixture cannot carry: JSON types a native host never passes.
    func testAnswerEdges() {
        XCTAssertNil(SdkPolicy.checkRequestAnswer(["result": 5]))
        XCTAssertEqual(SdkPolicy.checkRequestAnswer(nil), SdkPolicy.CheckedAnswer(result: nil))
        XCTAssertEqual(SdkPolicy.checkRequestAnswer(["result": NSNull()]), SdkPolicy.CheckedAnswer(result: nil))
        XCTAssertEqual(SdkPolicy.checkRequestAnswer(["result": "r", "chatData": "ignored"]), SdkPolicy.CheckedAnswer(result: "r"), "an answer carries no data")
        XCTAssertNil(SdkPolicy.checkRequestAnswer(WidgetRequestAnswer(result: "\u{0}")))
        XCTAssertEqual(SdkPolicy.checkWidgetData(["chatData": 5, "publicData": ["x"]] as [String: Any], copy: false).rejected, ["chatData", "publicData"])
        XCTAssertEqual(SdkPolicy.checkWidgetData("chat", copy: false), SdkPolicy.CheckedWidgetData(chatData: nil, publicData: nil, rejected: []))
        XCTAssertNil(SdkPolicy.checkWidgetDataValue("x\u{0}"))
        XCTAssertEqual(SdkPolicy.checkWidgetDataValue("\u{1}\t\r\n"), "\u{1}\t\r\n", "other control characters are text")
    }

    /// The TS checks refuse a lone surrogate; on Apple platforms none reaches them: JSONSerialization refuses the
    /// escape the shim's JSON.stringify would write (the call is `bridge-rejected`), a Foundation string's becomes U+FFFD.
    func testLoneSurrogatesNeverReachTheChecks() {
        XCTAssertNil(SandboxEngine.parse(#"["request",[1,"a\ud800"]]"#))
        XCTAssertNil(SandboxEngine.parse(#"["copyText",["\udc00b"]]"#))
        XCTAssertNotNil(SandboxEngine.parse(#"["copyText",["\ud83d\ude00"]]"#))
        let lone = NSString(characters: [0x61, 0xD800], length: 2) as String
        XCTAssertEqual(Array(lone.utf16), [0x61, 0xFFFD])
        XCTAssertEqual(SdkPolicy.checkCopyText(NSString(characters: [0xD83D, 0xDE00], length: 2) as String), "😀")
    }

    func testRequestGate() throws {
        for c in try cases("requestGate") {
            let name = c["name"] as? String ?? "?"
            var gate = SdkPolicy.RequestGate()
            for (i, step) in try XCTUnwrap(c["steps"] as? [[String: Any]]).enumerated() {
                if step["op"] as? String == "finish" {
                    gate.finish(answeredAtMs: (step["answeredAt"] as? NSNumber)?.doubleValue)
                    XCTAssertFalse(gate.busy, "\(name), step \(i)")
                    continue
                }
                if step["op"] as? String == "decideWallet" {
                    let verdict = gate.decideWallet(
                        nowMs: try XCTUnwrap((step["at"] as? NSNumber)?.doubleValue), gesture: try XCTUnwrap(step["gesture"] as? Bool),
                        visible: try XCTUnwrap(step["visible"] as? Bool), canQuery: try XCTUnwrap(step["canQuery"] as? Bool), wallet: try XCTUnwrap(step["wallet"] as? Bool))
                    XCTAssertEqual(verdict.rawValue, step["expect"] as? String, "\(name), step \(i)")
                    if verdict == .foreground || verdict == .granted { XCTAssertTrue(gate.busy, "\(name), step \(i)") }
                    continue
                }
                if step["op"] as? String == "show" {
                    let busy = gate.busy
                    XCTAssertEqual(gate.show(nowMs: try XCTUnwrap((step["at"] as? NSNumber)?.doubleValue)), try XCTUnwrap(step["expect"] as? Bool), "\(name), step \(i)")
                    XCTAssertEqual(gate.busy, busy, "\(name), step \(i): show changes nothing in flight (a held request stays in flight as it goes out)")
                    continue
                }
                let verdict = gate.decide(
                    nowMs: try XCTUnwrap((step["at"] as? NSNumber)?.doubleValue), gesture: try XCTUnwrap(step["gesture"] as? Bool),
                    visible: try XCTUnwrap(step["visible"] as? Bool), canQuery: try XCTUnwrap(step["canQuery"] as? Bool), backgroundQuery: try XCTUnwrap(step["backgroundQuery"] as? Bool),
                    withWallet: step["withWallet"] as? Bool ?? false, wallet: step["wallet"] as? Bool ?? false)
                XCTAssertEqual(verdict.rawValue, step["expect"] as? String, "\(name), step \(i)")
                if verdict == .foreground || verdict == .background || verdict == .held { XCTAssertTrue(gate.busy, "\(name), step \(i)") }
            }
        }
    }

    func testPreviewMethods() throws {
        let list = try cases("previewMethods")
        for c in list {
            XCTAssertEqual(SdkPolicy.allowedInPreview(value(c["method"])), try XCTUnwrap(c["allowed"] as? Bool), short(c["method"]))
        }
        XCTAssertFalse(SdkPolicy.allowedInPreview(nil))
        XCTAssertFalse(SdkPolicy.allowedInPreview(1))
    }

    func testRequestRefusals() throws {
        let list = try cases("requestRefusals")
        for c in list {
            let verdict = try XCTUnwrap(SdkPolicy.RequestVerdict(rawValue: try XCTUnwrap(c["verdict"] as? String)))
            XCTAssertEqual(SdkPolicy.requestRefusal(verdict)?.rawValue, value(c["expect"]) as? String, verdict.rawValue)
        }
        XCTAssertEqual(list.count, 9, "every verdict")
    }

    func testWalletTransactions() throws {
        for c in try cases("walletTransactions") {
            let note = c["note"] as? String ?? short(c["input"])
            XCTAssertEqual(SdkPolicy.checkWalletTransaction(expand(c["input"])).map { $0.json as NSDictionary }, expand(c["expect"]) as? NSDictionary, note)
        }
    }

    func testSignDataPayloads() throws {
        for c in try cases("signDataPayloads") {
            let note = c["note"] as? String ?? short(c["input"])
            XCTAssertEqual(SdkPolicy.checkSignDataPayload(expand(c["input"])).map { $0.json as NSDictionary }, expand(c["expect"]) as? NSDictionary, note)
        }
    }

    /// The JSON forms a client hands to its TON Connect window: the protocol's field names, absent fields left out.
    func testWalletJson() {
        let tx = WalletTransaction(validUntil: 1900000000, messages: [WalletMessage(address: "0:" + String(repeating: "a", count: 64), amount: "1")])
        XCTAssertEqual(tx.json as NSDictionary, ["valid_until": 1900000000, "messages": [["address": "0:" + String(repeating: "a", count: 64), "amount": "1"]]] as NSDictionary)
        XCTAssertEqual(SignDataPayload(type: .text, text: "x").json as NSDictionary, ["type": "text", "text": "x"] as NSDictionary)
        XCTAssertEqual(WalletCall.signData(SignDataPayload(type: .text, text: "x"), data: "d").method, "signData")
        XCTAssertEqual(WalletCall.sendTransaction(tx, data: "pay").data, "pay")
    }

    /// The constants the fixture does not spell out, as sdk-policy.ts has them.
    func testConstants() {
        XCTAssertEqual(SdkPolicy.themeKeys.count, 15)
        XCTAssertEqual(Set(SdkPolicy.themeKeys).count, 15)
        XCTAssertEqual(SdkPolicy.privilegedMethods, ["openLink", "openTelegramLink", "copyText"])
        XCTAssertEqual(SdkPolicy.previewMethods, ["ready", "setResizeAnchor"])
        XCTAssertEqual([SdkPolicy.hapticBurst, SdkPolicy.hapticPerSecond, SdkPolicy.hapticReportMs], [5, 10, 1000])
        XCTAssertEqual([SdkPolicy.hapticTouchWindowMs, SdkPolicy.hapticSeriesMs, SdkPolicy.hapticSeriesGapMs, SdkPolicy.hapticPressMaxMs], [5000, 1000, 10000, 30000])
        XCTAssertEqual(SdkPolicy.maxWidgetDataBytes, 1024)
        XCTAssertEqual(SdkPolicy.maxRequestDataBytes, 64)
        XCTAssertEqual(SdkPolicy.maxRequestResultBytes, 1024)
        XCTAssertEqual(SdkPolicy.maxCopyTextLength, 256)
        XCTAssertEqual(SdkPolicy.backgroundRequestIntervalMs, 5000)
        XCTAssertEqual(SdkPolicy.maxRequestId, 2147483647)
        XCTAssertEqual(SdkPolicy.maxViewerId, 9007199254740991)
        XCTAssertEqual(SdkPolicy.maxViewerNameLength, 64)
        XCTAssertEqual(Set(SdkPolicy.viewerFieldRules.keys), ["first_name", "last_name", "username", "is_premium"])
        XCTAssertEqual(WidgetChatType.allCases.map(\.rawValue), ["private", "group", "supergroup", "channel"])
        XCTAssertEqual(SdkPolicy.RequestError.allCases.map(\.rawValue), ["NOT_ALLOWED", "BUSY", "RATE_LIMITED", "TIMEOUT", "FAILED", "INVALID_ANSWER", "REJECTED", "BAD_REQUEST"])
        XCTAssertEqual([SdkPolicy.maxWalletMessages, SdkPolicy.maxWalletBocLength, SdkPolicy.maxWalletBocTotal, SdkPolicy.maxSignDataLength, SdkPolicy.maxSignDataSchemaBytes], [255, 8192, 65536, 4096, 1024])
        XCTAssertEqual(SdkPolicy.walletGrantMs, 10000)
        XCTAssertEqual(SdkPolicy.walletNetworks, ["-239", "-3"])
        XCTAssertEqual(SdkPolicy.maxLinkLength, 256)
        XCTAssertEqual(SdkPolicy.storageQuotaBytes, 4096)
        XCTAssertEqual(SdkPolicy.maxLocaleLength, 35)
        XCTAssertEqual(SdkPolicy.defaultLocale, "en-US")
        XCTAssertEqual(SdkPolicy.profileVersion, 1)
        XCTAssertEqual(SandboxEngine.maxStorageCommitChars, 2 * 12 * 4096 + 64)
    }
}

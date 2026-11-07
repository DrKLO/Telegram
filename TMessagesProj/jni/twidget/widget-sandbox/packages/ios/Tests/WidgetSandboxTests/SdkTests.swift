import XCTest
@testable import WidgetSandbox

/// SDK v1 through the real sandbox bundle in QuickJS and the real host glue (docs/sdk-v1.md §2–§4.1):
/// the Swift twin of packages/core/tests/unit/sdk.test.ts. The checks themselves are SdkPolicyTests.
final class SdkTests: XCTestCase {
    typealias Harness = UserActivationTests.Harness

    // MARK: links

    func testTapOpensOneNormalisedLinkAndBlocksTheSecond() {
        let h = Harness("<button id='b'>go</button><script>document.getElementById('b').addEventListener('click',function(){TelegramWidget.openLink('HTTPS://Example.com/поиск');TelegramWidget.openLink('https://example.com/second');});</script>")
        h.click(5)
        h.pump { !h.privileged.isEmpty }
        h.settle()
        XCTAssertEqual(h.actions, [.openLink(URL(string: "https://example.com/%D0%BF%D0%BE%D0%B8%D1%81%D0%BA")!)])
        XCTAssertEqual(h.blocked, 1)
    }

    func testInvalidLinksThrowInTheWidgetAndReachNothing() {
        let h = Harness("<script>var e=[];function t(m,u){try{TelegramWidget[m](u);}catch(x){e.push(m+':'+x.name);}}t('openLink','http://example.com/');t('openLink','https://t.me/x');t('openTelegramLink','tg://resolve?domain=x');t('openLink',42);console.log('errors='+e.join()+' send='+typeof TelegramWidget.sendMessage);</script>")
        h.settle()
        XCTAssertTrue(h.logs.contains("errors=openLink:TypeError,openLink:TypeError,openTelegramLink:TypeError,openLink:TypeError send=undefined"), "\(h.logs)")
        XCTAssertEqual(h.actions, [])
        XCTAssertEqual(h.count("bridge-rejected"), 0, "the shim sends only valid calls")
    }

    /// `window.open` and `<a href>` to a Telegram host go to openTelegramLink.
    func testWindowOpenToTelegramHost() {
        let h = Harness("<button id='b'>o</button><script>var w='?';document.getElementById('b').onclick=function(){w=window.open('https://telegram.me/x');console.log('w='+w);};</script>")
        h.click(5)
        h.pump { !h.privileged.isEmpty }
        XCTAssertEqual(h.actions, [.openTelegramLink(URL(string: "https://telegram.me/x")!)])
        XCTAssertTrue(h.logs.contains("w=null"), "\(h.logs)")
    }

    // MARK: haptics

    func testSevenImpactsInOneTaskGiveFiveAndOneLimit() {
        let h = Harness("<script>for(var i=0;i<7;i++)TelegramWidget.HapticFeedback.impactOccurred('medium');</script>")
        h.pump { h.actions.count >= 5 }
        h.settle()
        XCTAssertEqual(h.actions, Array(repeating: .haptic(.impact("medium")), count: 5), "no tap needed")
        XCTAssertEqual(h.count("haptic-limit"), 1)
    }

    func testHapticKindsAndAHiddenWidget() {
        let h = Harness("<button id='b'>x</button><script>TelegramWidget.HapticFeedback.notificationOccurred('success').selectionChanged();document.getElementById('b').onclick=function(){TelegramWidget.HapticFeedback.impactOccurred('soft');};</script>")
        h.pump { h.actions.count >= 2 }
        XCTAssertEqual(h.actions, [.haptic(.notification("success")), .haptic(.selection)])
        h.host.setVisible(false)
        h.settle(0.1)
        h.click(5)
        h.settle()
        XCTAssertEqual(h.actions.count, 2, "a hidden widget plays nothing")
        XCTAssertEqual(h.count("haptic-limit"), 1)
    }

    /// v0.2.2: haptics need a touch. A timer's flood opens one series: at most 16 play in its first second (the burst
    /// and the refill), none in the next; a click alone is no touch (presses come from the renderer, never the
    /// widget's events), while a click inside the renderer's press (its claim at pointerdown and its release) plays.
    func testHapticsNeedATouch() {
        let flood = Harness("<script>setInterval(function(){TelegramWidget.HapticFeedback.impactOccurred('light');},10);</script>")
        flood.settle(1.3)
        let first = flood.actions.count
        XCTAssertGreaterThanOrEqual(first, 5, "the series' burst plays")
        XCTAssertLessThanOrEqual(first, 16, "at most the burst and a second's refill")
        flood.settle(1.0)
        XCTAssertEqual(flood.actions.count, first, "none after the series' first second")
        XCTAssertEqual(flood.host.diagnostics.diags["haptic-limit"]?.details.first, "haptics over 5 per burst or 10 per second, or outside a touch after a 1 s series, are dropped")

        let tap = Harness("<button id='b'>x</button><script>TelegramWidget.HapticFeedback.impactOccurred('light');" +
            "setTimeout(function(){TelegramWidget.HapticFeedback.impactOccurred('medium');console.log('late');},1200);" +
            "document.getElementById('b').onclick=function(){TelegramWidget.HapticFeedback.impactOccurred('heavy');console.log('clicked');};</script>")
        tap.pump(3) { tap.logs.contains("late") }
        tap.click(5)
        tap.pump { tap.logs.contains("clicked") }
        tap.settle(0.1)
        XCTAssertEqual(tap.actions, [.haptic(.impact("light"))], "the timer's call after the series and a bare click are dropped")
        tap.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":false,\"x\":false,\"y\":false}")
        tap.click(5)
        tap.renderer.onMessage?("{\"type\":\"release\"}")
        tap.pump { tap.actions.count >= 2 }
        tap.settle(0.1)
        XCTAssertEqual(tap.actions, [.haptic(.impact("light")), .haptic(.impact("heavy"))], "a press the renderer saw makes the tap's haptic play")
    }

    // MARK: storage

    func testPreloadIsReadableFromTheFirstLineAndEachTaskCommits() {
        var options = WidgetHost.Options()
        options.storage = WidgetStorage(local: ["count": "41"], session: ["s": "kept"])
        let h = Harness("<button id='b'>b</button><script>console.log('seen='+localStorage.getItem('count')+'/'+sessionStorage.getItem('s')+'/'+localStorage.length);localStorage.setItem('count',String(Number(localStorage.getItem('count'))+1));localStorage.extra=7;document.getElementById('b').onclick=function(){sessionStorage.setItem('s','tapped');localStorage.setItem('count',localStorage.count);localStorage.setItem('w','ж'.repeat(2041));};</script>", options: options)
        h.pump { !h.commits.isEmpty }
        XCTAssertTrue(h.logs.contains("seen=41/kept/1"), "\(h.logs)")
        XCTAssertEqual(h.commits.map(\.area), [.local])
        XCTAssertEqual(h.commits.first?.values, ["count": "42", "extra": "7"])
        h.click(5)
        h.pump { h.commits.count >= 3 }
        h.settle(0.1)
        XCTAssertEqual(h.commits.map(\.area), [.local, .local, .session], "one commit per task, local first; an unchanged value is no change")
        XCTAssertEqual(h.commits[1].values["w"]?.count, 2041, "count=42, extra=7 and w = 13 + 1 + 4082 bytes: exactly the quota, by the shim's count and the glue's")
        XCTAssertEqual(h.commits[2].values, ["s": "tapped"])
        XCTAssertEqual(h.count("storage-rejected"), 0)
    }

    func testPreloadOverTheQuotaIsDroppedWhole() {
        var options = WidgetHost.Options()
        options.storage = WidgetStorage(local: ["a": String(repeating: "x", count: 20000)], session: ["b": "1"])
        let h = Harness("<script>console.log('n='+(localStorage.length+sessionStorage.length));</script>", options: options)
        h.settle(0.1)
        XCTAssertEqual(h.count("storage-rejected"), 1)
        XCTAssertTrue(h.logs.contains("n=0"), "\(h.logs)")
    }

    func testSetItemPastTheQuotaThrowsAndCommitsNothing() {
        let h = Harness("<script>var out=[];localStorage.setItem('k','x'.repeat(4095));try{localStorage.setItem('k2','y');}catch(e){out.push(e.name);}console.log('out='+out.join());</script>")
        h.pump { !h.commits.isEmpty }
        XCTAssertTrue(h.logs.contains("out=QuotaExceededError"), "\(h.logs)")
        XCTAssertEqual(h.commits.count, 1)
        XCTAssertEqual(h.commits.first?.values.keys.sorted(), ["k"])
    }

    // MARK: resize anchor (v0.1.3)

    /// `setResizeAnchor` is state next to the height, not an action: no tap, any visibility, nothing in `actions` or
    /// `bridge`, only the latest value kept (sdk.test.ts "SDK v1: setResizeAnchor").
    func testSetResizeAnchorIsStateNotAnAction() {
        var options = WidgetHost.Options()
        options.visible = false
        let h = Harness("<button id='b'>b</button><script>var errs=[];['left','Bottom',' top','',null,1].forEach(function(v){try{TelegramWidget.setResizeAnchor(v);}catch(e){errs.push(e.name);}});" +
            "console.log('errs='+errs.join());TelegramWidget.setResizeAnchor('top');for(var i=0;i<1000;i++)TelegramWidget.setResizeAnchor('bottom');document.body.appendChild(document.createElement('p')).textContent='grown';" +
            "document.getElementById('b').onclick=function(){TelegramWidget.setResizeAnchor('top');console.log('tapped');};</script>", options: options)
        h.pump { h.host.diagnostics.resizeAnchor == .bottom }
        h.settle(0.1)
        XCTAssertEqual(h.host.diagnostics.resizeAnchor, .bottom, "a hidden widget sets it at boot, no tap")
        XCTAssertTrue(h.logs.contains("errs=" + Array(repeating: "TypeError", count: 6).joined(separator: ",")), "\(h.logs)")
        XCTAssertEqual(h.actions, [])
        XCTAssertTrue(h.host.diagnostics.bridge.isEmpty)
        XCTAssertEqual(h.count("bridge-rejected"), 0)
        XCTAssertEqual(h.count("activation-blocked"), 0)
        h.host.setVisible(true)
        h.click(5)
        h.pump { h.logs.contains("tapped") && h.host.diagnostics.resizeAnchor == .top }
        XCTAssertEqual(h.host.diagnostics.resizeAnchor, .top)
        XCTAssertEqual(h.actions, [])

        let plain = Harness("<p>never anchored</p>")
        plain.settle(0.1)
        XCTAssertEqual(plain.host.diagnostics.resizeAnchor, .top)
    }

    /// The order bridge → ops → renderer → size: the anchor of a task is in the host's state before the size report
    /// of that task's ops, here a renderer that reports the height synchronously while it receives the ops. An equal
    /// value (the glue's own check, the shim sends only changes) changes nothing.
    func testResizeAnchorComesBeforeTheHeightOfItsTask() throws {
        let renderer = MarkerSizeRenderer()
        let html = "<p id='p'>h100</p><script>TelegramWidget.setResizeAnchor('bottom');document.getElementById('p').textContent='h200';" +
            "setTimeout(function(){TelegramWidget.setResizeAnchor('top');document.getElementById('p').textContent='h300';},100);</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        var states: [(height: CGFloat, anchor: WidgetResizeAnchor)] = []
        let lock = NSLock()
        host.onChangeOnExecutor = { d in lock.lock(); states.append((d.height, d.resizeAnchor)); lock.unlock() }
        pump { host.diagnostics.height == 300 }
        pump(0.2) { false }
        lock.lock()
        let seen = states
        lock.unlock()
        XCTAssertEqual(seen.first { $0.height == 200 }?.anchor, .bottom, "\(seen)")
        XCTAssertEqual(seen.first { $0.height == 300 }?.anchor, .top, "\(seen)")
        XCTAssertFalse(seen.contains { $0.height == 300 && $0.anchor == .bottom }, "the second task's anchor before its height: \(seen)")
        XCTAssertEqual(host.diagnostics.resizeAnchor, .top)

        let engine = try XCTUnwrap(Mirror(reflecting: host).children.first { $0.label == "engine" }?.value as? SandboxEngine)
        // Once the host is quiet (the last task's stats arrive after its ops), an equal anchor publishes nothing.
        let count = { () -> Int in lock.lock(); defer { lock.unlock() }; return states.count }
        var quiet = count(), quietSince = Date()
        let limit = Date().addingTimeInterval(5)
        while Date() < limit, Date().timeIntervalSince(quietSince) < 0.5 {
            pump(0.05) { false }
            if count() != quiet { quiet = count(); quietSince = Date() }
        }
        host.engine(engine, resizeAnchor: .top)
        pump(0.3) { false }
        XCTAssertEqual(count(), quiet, "an equal anchor publishes nothing")
        host.engine(engine, resizeAnchor: .bottom)
        pump { host.diagnostics.resizeAnchor == .bottom }
        XCTAssertEqual(host.diagnostics.resizeAnchor, .bottom)
    }

    // MARK: bot data, where the widget is, requests, copyText

    private static let show = "console.log('ctx='+JSON.stringify([TelegramWidget.chatData,TelegramWidget.publicData,TelegramWidget.isForward,TelegramWidget.chatType,TelegramWidget.viewer]));"

    /// The server's flags for each kind of widget message (WIDGET_MESSAGE_KINDS in sdk-policy.ts).
    private static let privateChat = WidgetContext(canQuery: true, backgroundQuery: true, chatType: .private)
    private static let group = WidgetContext(canQuery: true, chatType: .group)
    private static let liveCopy = WidgetContext(isCopy: true, canQuery: true, chatType: .supergroup, viewer: WidgetViewer(id: 42))
    private static let plainCopy = WidgetContext(isCopy: true, chatType: .supergroup, viewer: WidgetViewer(id: 42))

    private func harness(_ html: String, _ context: WidgetContext, data: WidgetData = WidgetData()) -> Harness {
        var options = WidgetHost.Options()
        options.context = context
        options.data = data
        return Harness(html, options: options)
    }

    private func context(_ h: Harness) -> String? {
        h.pump { h.logs.contains { $0.hasPrefix("ctx=") } }
        return h.logs.first { $0.hasPrefix("ctx=") }
    }

    /// sdk.test.ts: chatData, publicData, chatType and viewer come from the client, checked; a copy never has chatData or
    /// chatType, and only a widget that may request knows its viewer (an original sent without allow_queries does not);
    /// a NUL or more than 1024 bytes is no data.
    func testDataAndContextAtBoot() {
        var context = Self.privateChat
        context.viewer = WidgetViewer(id: 123456789012)
        let original = harness("<script>" + Self.show + "console.log('more='+Object.isFrozen(TelegramWidget.viewer)+','+(TelegramWidget.viewer===TelegramWidget.viewer)+','+typeof TelegramWidget.request+','+typeof TelegramWidget.copyText);</script>",
                               context, data: WidgetData(chatData: "{\"turn\":1}", publicData: "score 3:1"))
        XCTAssertEqual(self.context(original), #"ctx=["{\"turn\":1}","score 3:1",false,"private",{"id":123456789012}]"#)
        XCTAssertTrue(original.logs.contains("more=true,true,function,function"), "\(original.logs)")
        XCTAssertEqual(original.count("data-rejected"), 0)

        let data = WidgetData(chatData: "secret", publicData: "score 3:1")
        let live = harness("<script>" + Self.show + "</script>", Self.liveCopy, data: data)
        XCTAssertEqual(self.context(live), #"ctx=[null,"score 3:1",true,null,{"id":42}]"#, "a live copy: no chatData or chatType (supergroup passed), but who looks")
        let copy = harness("<script>" + Self.show + "</script>", Self.plainCopy, data: data)
        XCTAssertEqual(self.context(copy), #"ctx=[null,"score 3:1",true,null,null]"#, "a copy that cannot request knows only publicData")
        let quiet = harness("<script>" + Self.show + "</script>", WidgetContext(chatType: .private, viewer: WidgetViewer(id: 42)), data: WidgetData(chatData: "mine", publicData: "p"))
        XCTAssertEqual(self.context(quiet), #"ctx=["mine","p",false,"private",null]"#, "an original sent without allow_queries knows no viewer")

        let none = harness("<script>" + Self.show + "</script>", WidgetContext(viewer: WidgetViewer(id: 0)), data: WidgetData(chatData: "", publicData: String(repeating: "x", count: 1025)))
        XCTAssertEqual(self.context(none), "ctx=[null,null,false,null,null]")
        XCTAssertEqual(none.host.diagnostics.diags["data-rejected"]?.details, ["publicData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"])

        let nul = harness("<script>" + Self.show + "</script>", WidgetContext(), data: WidgetData(chatData: "a\u{0}b", publicData: "tab\tok"))
        XCTAssertEqual(self.context(nul), #"ctx=[null,"tab\tok",false,null,null]"#, "a NUL is no text, a tab is")
        XCTAssertEqual(nul.host.diagnostics.diags["data-rejected"]?.details, ["chatData: not text of at most 1024 bytes (UTF-8, no NUL); the widget sees null"])
    }

    /// The viewer is Mini Apps' `WebAppUser`, given only with `canQuery`: every field kept and frozen; a field that
    /// breaks its rule left out with a `data-rejected`; none without `canQuery`, names or not (sdk.test.ts).
    func testViewerIsAWebAppUser() {
        let html = "<script>console.log('viewer='+JSON.stringify(TelegramWidget.viewer)+'|'+Object.isFrozen(TelegramWidget.viewer));</script>"
        func viewer(_ h: Harness) -> String? {
            h.pump { h.logs.contains { $0.hasPrefix("viewer=") } }
            return h.logs.first { $0.hasPrefix("viewer=") }
        }
        var context = Self.privateChat
        context.viewer = WidgetViewer(id: 42, firstName: "Ann", lastName: "Lee", username: "ann_lee", isPremium: true)
        let full = harness(html, context)
        XCTAssertEqual(viewer(full), #"viewer={"id":42,"first_name":"Ann","last_name":"Lee","username":"ann_lee","is_premium":true}|true"#)
        XCTAssertNil(full.host.diagnostics.diags["data-rejected"])

        context = Self.group
        context.viewer = WidgetViewer(id: 42, firstName: "Ann", username: "a-b", isPremium: false)
        let partial = harness(html, context)
        XCTAssertEqual(viewer(partial), #"viewer={"id":42,"first_name":"Ann"}|true"#)
        XCTAssertEqual(partial.host.diagnostics.diags["data-rejected"]?.details, ["viewer.username: not 5–32 characters of A–Z, a–z, 0–9 and _; the widget does not see it"])

        let quiet = harness(html, WidgetContext(chatType: .private, viewer: WidgetViewer(id: 42, firstName: "Ann", lastName: "Lee")))
        XCTAssertEqual(viewer(quiet), "viewer=null|true", "an original without canQuery knows no viewer")
        XCTAssertNil(quiet.host.diagnostics.diags["data-rejected"])
    }

    /// `setData` is the full state of both strings: `chatDataChanged`, then `publicDataChanged`, for each field that
    /// changed, nothing for equal data, a field over the limit null; a copy keeps no `chatData`, its `publicData` changes.
    func testSetDataFiresOneEventPerChangedField() {
        let html = "<script>var log=[];function out(s){log.push(s);console.log('data='+log.join(' '));}" +
            "TelegramWidget.onEvent('chatDataChanged',function(){out('chat:'+TelegramWidget.chatData+':'+(this===TelegramWidget));});" +
            "TelegramWidget.onEvent('publicDataChanged',function(){out('public:'+TelegramWidget.publicData);});</script>"
        let h = harness(html, Self.group, data: WidgetData(chatData: "a", publicData: "p"))
        h.host.setData(WidgetData(chatData: "a", publicData: "p"))
        h.host.setData(WidgetData(chatData: "b", publicData: "p"))
        h.host.setData(WidgetData(publicData: "q"))
        h.host.setData(WidgetData(chatData: String(repeating: "x", count: 1025), publicData: "q"))
        h.host.setData(WidgetData(chatData: "c", publicData: "r"))
        h.pump { h.logs.contains { $0.hasSuffix("public:r") } }
        h.settle()
        XCTAssertEqual(h.logs.last { $0.hasPrefix("data=") }, "data=chat:b:true chat:null:true public:q chat:c:true public:r", "equal data fires nothing; chat first")
        XCTAssertEqual(h.count("data-rejected"), 1)

        let copy = harness(html, Self.plainCopy, data: WidgetData(publicData: "p"))
        copy.host.setData(WidgetData(chatData: "c", publicData: "p2"))
        copy.pump { copy.logs.contains { $0.hasPrefix("data=") } }
        copy.settle()
        XCTAssertEqual(copy.logs.filter { $0.hasPrefix("data=") }, ["data=public:p2"], "a copy: only publicData changes")
    }

    /// Data set before the engine starts (the renderer is still loading) is what the widget starts with.
    func testSetDataBeforeTheWidgetStarts() throws {
        let renderer = HeldRenderer()
        var options = WidgetHost.Options()
        options.data = WidgetData(chatData: "old", publicData: "p")
        let host = try WidgetHost(html: "<script>var n=0;['chatDataChanged','publicDataChanged'].forEach(function(t){TelegramWidget.onEvent(t,function(){n++;});});" + Self.show + "setTimeout(function(){console.log('n='+n);},50);</script>", theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        host.setData(WidgetData(chatData: "new"))
        pump(0.2) { false }
        XCTAssertTrue(host.diagnostics.logs.isEmpty, "not started yet")
        renderer.release()
        pump { host.diagnostics.logs.contains { $0.text.hasPrefix("n=") } }
        let logs = host.diagnostics.logs.map(\.text)
        XCTAssertTrue(logs.contains(#"ctx=["new",null,false,null,null]"#), "\(logs)")
        XCTAssertTrue(logs.contains("n=0"), "\(logs)")
    }

    /// The widget of sdk.test.ts: a tap asks twice (the shim forwards both, the glue's gate refuses the second as BUSY); every outcome goes into `log`.
    private static let requester = "<button id='b'>go</button><script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}" +
        "function ask(d){TelegramWidget.request(d).then(function(a){out('ok:'+d+':'+JSON.stringify(a)+':'+Object.isFrozen(a)+':'+TelegramWidget.chatData);},function(e){out('err:'+d+':'+e.name+':'+e.code);});}" +
        "document.getElementById('b').addEventListener('click',function(){ask('tap');ask('tap2');});</script>"
    private static let button = 5

    /// The latest `log=` line, once it has `entries` entries.
    private func log(_ h: Harness, entries: Int) -> String {
        h.pump { (h.logs.last { $0.hasPrefix("log=") }?.split(separator: " ").count ?? 0) >= entries }
        return String(h.logs.last { $0.hasPrefix("log=") }?.dropFirst(4) ?? "")
    }

    /// `log` with each tap's two outcomes sorted: the client's answer comes through the main thread, the gate's BUSY
    /// for the second call from the engine's, in either order.
    private func tapLog(_ h: Harness, entries: Int) -> String {
        let all = log(h, entries: entries).split(separator: " ").map(String.init)
        return stride(from: 0, to: all.count, by: 2).flatMap { all[$0..<min($0 + 2, all.count)].sorted() }.joined(separator: " ")
    }

    /// An original in a group (`canQuery` only) asks on a tap: one request, the answer resolves with `{ result }`, a
    /// second call in the tap is BUSY; after the answer the next tap asks again.
    func testTapSendsOneRequestAndTheAnswerResolvesWithItsResult() {
        let h = harness(Self.requester, Self.group, data: WidgetData(chatData: "v1"))
        var asked: [WidgetRequest] = []
        h.host.onRequest = { request, respond in
            asked.append(request)
            respond(.success(WidgetRequestAnswer(result: "thanks")))
        }
        h.click(Self.button)
        XCTAssertEqual(tapLog(h, entries: 2), #"err:tap2:WidgetRequestError:BUSY ok:tap:{"result":"thanks"}:true:v1"#)
        XCTAssertEqual(asked, [WidgetRequest(data: "tap")])
        XCTAssertEqual(h.host.diagnostics.diags["request-blocked"]?.details, ["request refused (busy): a request is already in flight"], "BUSY comes from the glue's gate")
        XCTAssertEqual(h.actions, [], "a request is no action")
        XCTAssertTrue(h.host.diagnostics.bridge.isEmpty)

        h.click(Self.button)
        XCTAssertEqual(tapLog(h, entries: 4).split(separator: " ").suffix(2).joined(separator: " "), #"err:tap2:WidgetRequestError:BUSY ok:tap:{"result":"thanks"}:true:v1"#)
        XCTAssertEqual(asked.count, 2, "after the answer the next tap asks again")
    }

    /// sdk.test.ts "a request takes the tap: an openLink or copyText after it in the same tap is blocked".
    func testARequestTakesTheTap() {
        let h = harness("<button id='b'>x</button><script>document.getElementById('b').onclick=function(){TelegramWidget.request('r');TelegramWidget.copyText('c');TelegramWidget.openLink('https://example.com/');};</script>", Self.group)
        var asked = 0
        var copied: [String] = []
        h.host.onRequest = { _, respond in asked += 1; respond(.success(WidgetRequestAnswer())) }
        h.host.onCopyText = { copied.append($0) }
        h.click(Self.button)
        h.pump { asked == 1 && h.blocked == 2 }
        h.settle()
        XCTAssertEqual(asked, 1)
        XCTAssertEqual(copied, [])
        XCTAssertEqual(h.privileged, [])
        XCTAssertEqual(h.blocked, 2)
    }

    /// `onRequest` is read on the main thread when a request is delivered, not when the widget asks: a client that sets
    /// it right after creating the host gets a request the widget made before that (a timer at boot, background allowed;
    /// the engine boots on its thread while the main thread is still busy here).
    func testOnRequestSetAfterCreationGetsAnEarlyRequest() throws {
        let renderer = RecordingRenderer()
        var options = WidgetHost.Options()
        options.context = Self.privateChat
        let html = "<script>setTimeout(function(){TelegramWidget.request('early').then(function(a){console.log('early='+a.result);},function(e){console.log('early='+e.code);});},0);</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        Thread.sleep(forTimeInterval: 0.5)
        var asked: [WidgetRequest] = []
        host.onRequest = { request, respond in
            asked.append(request)
            respond(.success(WidgetRequestAnswer(result: "delivered")))
        }
        pump { host.diagnostics.logs.contains { $0.text.hasPrefix("early=") } }
        XCTAssertEqual(asked, [WidgetRequest(data: "early")])
        XCTAssertTrue(host.diagnostics.logs.contains { $0.text == "early=delivered" }, "\(host.diagnostics.logs)")
        XCTAssertNil(host.diagnostics.diags["request-blocked"])
    }

    /// No tap: refused without `backgroundQuery` (a group); with it (the private chat with the bot) one per 5 s
    /// (sdk.test.ts "without a tap …" and "background requests …"); off screen it waits (the tests below).
    func testRequestsWithoutATap() {
        // The pointer moving is no press: the request comes from a timer that it starts.
        let html = "<p>x</p><script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}" +
            "function ask(){TelegramWidget.request('bg').then(function(){out('ok');},function(e){out(e.code+':'+/tap/.test(e.message));});}" +
            "document.addEventListener('pointermove',function(){setTimeout(ask,0);});</script>"
        let inGroup = harness(html, Self.group)
        var asked: [WidgetRequest] = []
        inGroup.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer())) }
        inGroup.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(inGroup, entries: 1), "NOT_ALLOWED:true")
        XCTAssertEqual(asked, [], "onRequest is not called")
        XCTAssertEqual(inGroup.host.diagnostics.diags["request-blocked"]?.details,
                       ["request refused (no-activation): it needs a tap on the visible widget, one request per tap; this message allows no background requests (no background_query)"])

        let h = harness(html, Self.privateChat)
        h.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer())) }
        h.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(h, entries: 1), "ok")
        XCTAssertEqual(asked, [WidgetRequest(data: "bg")])
        h.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(h, entries: 2), "ok RATE_LIMITED:false", "a second background request within 5 s")
        XCTAssertEqual(asked.count, 1)
        XCTAssertEqual(h.host.diagnostics.diags["request-blocked"]?.details.map { $0.components(separatedBy: ":").first ?? "" }, ["request refused (rate-limited)"])
    }

    /// Telegram's clients create the host off screen (`isActive` false at boot): a background request at boot waits
    /// in the glue (no answer, nothing refused) and goes to the client when the widget appears, as that appearance's;
    /// one on `activated` meanwhile is BUSY, the first being in flight (sdk.test.ts "… a request at boot off screen …").
    func testBackgroundRequestAtBootOffScreenGoesOutWhenTheWidgetAppears() {
        var options = WidgetHost.Options()
        options.context = Self.privateChat
        options.visible = false
        let h = Harness(Self.offScreenAsker, options: options)
        var asked: [WidgetRequest] = []
        h.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer(result: "r"))) }
        h.pump { h.logs.contains("active=false") }
        h.settle(0.2)
        XCTAssertEqual(asked, [], "held while off screen")
        XCTAssertFalse(h.logs.contains { $0.hasPrefix("log=") }, "\(h.logs)")
        XCTAssertEqual(h.count("request-blocked"), 0)
        let window = TestWindow.makeVisible()
        defer { window.close() }
        window.attach(h.host.view)
        h.host.setVisible(true)
        XCTAssertEqual(log(h, entries: 2).split(separator: " ").sorted(), ["activated:BUSY", "boot:r"])
        XCTAssertEqual(asked, [WidgetRequest(data: "boot")])
        XCTAssertEqual(h.host.diagnostics.diags["request-blocked"]?.details, ["request refused (busy): a request is already in flight"])
    }

    /// A held request never reaches the client once the host is destroyed, even if it is shown afterwards.
    func testHeldRequestIsDroppedWhenTheHostIsDestroyed() {
        var options = WidgetHost.Options()
        options.context = Self.privateChat
        options.visible = false
        let h = Harness(Self.offScreenAsker, options: options)
        var asked: [WidgetRequest] = []
        h.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer())) }
        h.pump { h.logs.contains("active=false") }
        let window = TestWindow.makeVisible()
        defer { window.close() }
        window.attach(h.host.view)
        h.host.destroy()
        h.host.setVisible(true)
        h.settle(0.3)
        XCTAssertEqual(asked, [])
    }

    private static let offScreenAsker = "<p>x</p><script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}" +
        "function ask(t){TelegramWidget.request(t).then(function(a){out(t+':'+a.result);},function(e){out(t+':'+e.code);});}" +
        "TelegramWidget.onEvent('activated',function(){ask('activated');});ask('boot');console.log('active='+TelegramWidget.isActive);</script>"

    /// A background request, the widget hidden and shown again, a background request at once: delivered (one when the
    /// widget appears on screen), another at once: RATE_LIMITED (then one per 5 s).
    func testBackgroundRequestWhenTheWidgetAppears() {
        let html = "<p>x</p><script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}" +
            "document.addEventListener('pointermove',function(){TelegramWidget.request('bg').then(function(){out('ok');},function(e){out(e.code);});});</script>"
        let h = harness(html, Self.privateChat)
        var asked: [WidgetRequest] = []
        h.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer())) }
        h.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(h, entries: 1), "ok")
        h.host.setVisible(false)
        h.settle(0.1)
        let window = TestWindow.makeVisible()
        defer { window.close() }
        window.attach(h.host.view)
        h.host.setVisible(true)
        h.settle(0.1)
        h.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(h, entries: 2), "ok ok", "the widget appeared on screen: at once")
        h.event(["type": "pointermove", "targetId": 4])
        XCTAssertEqual(log(h, entries: 3), "ok ok RATE_LIMITED")
        XCTAssertEqual(asked, [WidgetRequest(data: "bg"), WidgetRequest(data: "bg")], "onRequest gets only the data")
    }

    /// The server's flags decide who may ask: a live copy (`isCopy`, `canQuery`) asks on a tap, sees who looks but not
    /// where it is, has no chatData; a plain copy (`isCopy` only) and a message without flags never ask (the shim refuses; a
    /// tampered shim meets the glue's `disabled`); a client without `onRequest` at delivery refuses (`unsupported`).
    func testWhoMayRequest() {
        var asked: [WidgetRequest] = []
        let answerX: (WidgetRequest, @escaping (Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void) -> Void = { request, respond in
            asked.append(request)
            respond(.success(WidgetRequestAnswer(result: "x")))
        }
        let live = harness(Self.requester + "<script>" + Self.show + "</script>", Self.liveCopy, data: WidgetData(chatData: "secret", publicData: "p"))
        live.host.onRequest = answerX
        live.click(Self.button)
        XCTAssertEqual(tapLog(live, entries: 2), #"err:tap2:WidgetRequestError:BUSY ok:tap:{"result":"x"}:true:null"#)
        XCTAssertEqual(asked, [WidgetRequest(data: "tap")])
        XCTAssertEqual(context(live), #"ctx=[null,"p",true,null,{"id":42}]"#, "no copy tells where it was forwarded")

        var noRequests = Self.plainCopy
        noRequests.backgroundQuery = true
        for (name, context) in [("plain copy", noRequests), ("no flags", WidgetContext())] {
            let h = harness(Self.requester.replacingOccurrences(of: "e.code);", with: "e.code+'|'+e.message);"), context)
            h.host.onRequest = answerX
            h.click(Self.button)
            h.pump { h.logs.contains { $0.contains("err:tap2:") } }
            let line = h.logs.last { $0.hasPrefix("log=") } ?? ""
            XCTAssertTrue(line.hasPrefix("log=err:tap:WidgetRequestError:NOT_ALLOWED|request: requests are off for this message"), "\(name): \(line)")
            XCTAssertTrue(line.contains(" err:tap2:WidgetRequestError:NOT_ALLOWED|request: requests are off"), "\(name): \(line)")
            XCTAssertNil(h.host.diagnostics.diags["request-blocked"], "\(name): the shim refused")
        }
        XCTAssertEqual(asked.count, 1, "a plain copy and a message without flags never reach onRequest")

        // A shim that sends the request anyway: the glue's gate refuses without canQuery too.
        SandboxEngine.hidesHostFunctions = false
        let tampered = harness("<button id='b'>x</button><script>document.getElementById('b').onclick=function(){__host('bridge',JSON.stringify(['request',[1,'x']]));};</script>", noRequests)
        SandboxEngine.hidesHostFunctions = true
        tampered.host.onRequest = answerX
        tampered.click(Self.button)
        tampered.pump { tampered.count("request-blocked") > 0 }
        XCTAssertEqual(tampered.host.diagnostics.diags["request-blocked"]?.details,
                       ["request refused (disabled): the server allows no requests for this message (no can_query: a widget sent without allow_queries, or a forwarded copy of one sent without fwd_allow_queries)"])
        XCTAssertEqual(asked.count, 1)

        let unsupported = harness(Self.requester.replacingOccurrences(of: "e.code);", with: "e.code+'|'+e.message);"), Self.group)
        unsupported.click(Self.button)
        unsupported.pump { unsupported.logs.contains { $0.contains("err:tap:") && $0.contains("err:tap2:") } }
        let line = unsupported.logs.last { $0.hasPrefix("log=") } ?? ""
        XCTAssertTrue(line.contains("err:tap2:WidgetRequestError:BUSY|request: a request is already in flight; wait for its answer"), line)
        XCTAssertTrue(line.contains("err:tap:WidgetRequestError:NOT_ALLOWED|request: this client does not deliver requests"), line)
        XCTAssertEqual(unsupported.host.diagnostics.diags["request-blocked"]?.details, ["request refused (busy): a request is already in flight", "request refused: the client handles no requests (WidgetHost.onRequest)"])
    }

    /// A failure becomes its code; a result over 1024 bytes or with a NUL is INVALID_ANSWER (`answer-rejected`); the
    /// completion works from any thread, the first call wins, and a call after `destroy()` reaches nothing.
    func testFailuresInvalidAnswersAndTheCompletion() {
        let h = harness(Self.requester, Self.group)
        var outcomes: [Result<WidgetRequestAnswer, WidgetRequestFailure>] = [
            .failure(.timeout), .failure(.rateLimited), .failure(.failed),
            .success(WidgetRequestAnswer(result: String(repeating: "x", count: 1025))), .success(WidgetRequestAnswer(result: "a\u{0}b")),
        ]
        h.host.onRequest = { _, respond in respond(outcomes.removeFirst()) }
        var expected: [String] = []
        for code in ["TIMEOUT", "RATE_LIMITED", "FAILED", "INVALID_ANSWER", "INVALID_ANSWER"] {
            h.click(Self.button)
            expected += ["err:tap2:WidgetRequestError:BUSY", "err:tap:WidgetRequestError:\(code)"]
            XCTAssertEqual(tapLog(h, entries: expected.count), expected.joined(separator: " "))
        }
        XCTAssertEqual(h.host.diagnostics.diags["answer-rejected"]?.details, Array(repeating: "the answer's result is not text of at most 1024 bytes (UTF-8, no NUL); the widget gets INVALID_ANSWER", count: 2))

        // From another thread, three times: the first call wins.
        var stored: ((Result<WidgetRequestAnswer, WidgetRequestFailure>) -> Void)?
        h.host.onRequest = { _, respond in
            stored = respond
            DispatchQueue.global().async {
                respond(.success(WidgetRequestAnswer(result: "first")))
                respond(.success(WidgetRequestAnswer(result: "second")))
                respond(.failure(.failed))
            }
        }
        h.click(Self.button)
        expected += ["err:tap2:WidgetRequestError:BUSY", #"ok:tap:{"result":"first"}:true:null"#]
        XCTAssertEqual(tapLog(h, entries: expected.count), expected.joined(separator: " "))
        h.settle()
        XCTAssertEqual(tapLog(h, entries: expected.count), expected.joined(separator: " "), "later calls are ignored")

        // A completion kept past destroy() reaches nothing.
        h.host.onRequest = { _, respond in stored = respond }
        h.click(Self.button)
        h.pump { (h.logs.last { $0.hasPrefix("log=") }?.split(separator: " ").count ?? 0) > expected.count }
        let logs = h.logs.count
        h.host.destroy()
        stored?(.success(WidgetRequestAnswer(result: "late")))
        h.settle()
        XCTAssertEqual(h.logs.count, logs)
    }

    /// A preview (an inline result not sent yet): its data, no flags, chat type or viewer, empty storage despite a
    /// preload; no input (a tap and a gesture claim reach nothing); `ready` and `setResizeAnchor` work, everything else
    /// is `preview-blocked` and reaches no client callback; storage is never committed; timers run.
    func testAPreviewTakesNoInputAndNoActions() {
        var options = WidgetHost.Options()
        options.context = WidgetContext(canQuery: true, backgroundQuery: true, chatType: .private, viewer: WidgetViewer(id: 42), preview: true)
        options.data = WidgetData(chatData: "c", publicData: "p")
        options.storage = WidgetStorage(local: ["k": "v"])
        let html = "<button id='b'>x</button><script>" + Self.show + "console.log('pv='+TelegramWidget.isPreview+','+localStorage.length);" +
            "TelegramWidget.ready();TelegramWidget.setResizeAnchor('bottom');localStorage.setItem('x','1');" +
            "TelegramWidget.request('r').then(function(){console.log('req=ok');},function(e){console.log('req='+e.code+'|'+e.message);});" +
            "TelegramWidget.openLink('https://example.com/');TelegramWidget.copyText('c');TelegramWidget.HapticFeedback.impactOccurred('medium');" +
            "setTimeout(function(){console.log('timer');},10);" +
            "document.getElementById('b').onclick=function(){console.log('tapped');TelegramWidget.openLink('https://example.com/tap');};</script>"
        let h = Harness(html, options: options)
        var asked = 0
        var copied: [String] = []
        h.host.onRequest = { _, respond in asked += 1; respond(.success(WidgetRequestAnswer())) }
        h.host.onCopyText = { copied.append($0) }
        h.pump { h.logs.contains("timer") && h.logs.contains { $0.hasPrefix("req=") } }
        h.click(Self.button)
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":true,\"x\":true,\"y\":false,\"hold\":true}")
        h.settle()

        XCTAssertEqual(context(h), #"ctx=["c","p",false,null,null]"#)
        XCTAssertTrue(h.logs.contains("pv=true,0"), "\(h.logs)")
        XCTAssertTrue(h.logs.contains("req=NOT_ALLOWED|request: a preview (an inline result not sent yet) cannot ask the bot"), "\(h.logs)")
        XCTAssertTrue(h.logs.contains("timer"))
        XCTAssertFalse(h.logs.contains("tapped"), "a tap is the client's")
        XCTAssertEqual(h.claims, [], "no gesture claims")
        XCTAssertEqual(h.actions, [.ready])
        XCTAssertEqual(h.host.diagnostics.resizeAnchor, .bottom)
        XCTAssertEqual(asked, 0)
        XCTAssertEqual(copied, [])
        XCTAssertTrue(h.commits.isEmpty, "a preview's storage is never committed")
        XCTAssertEqual(h.host.diagnostics.diags["preview-blocked"]?.details.map { $0.components(separatedBy: " ").first ?? "" }, ["openLink", "copyText", "haptic"])
        XCTAssertEqual(h.host.diagnostics.diags["preview-blocked"]?.details.first, "openLink ignored: a preview (an inline result not sent yet) takes no actions")
        XCTAssertEqual(h.count("activation-blocked"), 0, "blocked as a preview before any activation check")
        XCTAssertEqual(h.count("storage-rejected"), 0)

        // A shim that sends the request anyway: the glue blocks it too, and the client never sees it.
        SandboxEngine.hidesHostFunctions = false
        let tampered = Harness("<script>__host('bridge',JSON.stringify(['request',[1,'x']]));</script>", options: options)
        SandboxEngine.hidesHostFunctions = true
        tampered.host.onRequest = { _, respond in asked += 1; respond(.success(WidgetRequestAnswer())) }
        tampered.pump { tampered.count("preview-blocked") > 0 }
        tampered.settle()
        XCTAssertEqual(tampered.host.diagnostics.diags["preview-blocked"]?.details, ["request ignored: a preview (an inline result not sent yet) takes no actions"])
        XCTAssertEqual(asked, 0)
    }

    /// `watchPublicData` is state, like the resize anchor: no tap, never an action; the host keeps the latest and calls
    /// `onWatchPublicData` on a change only (the shim sends only changes too); false for a widget that never calls it;
    /// a preview's call is `preview-blocked`.
    func testWatchPublicDataIsStateReportedOnAChange() throws {
        let renderer = RecordingRenderer()
        let html = "<script>TelegramWidget.watchPublicData();TelegramWidget.watchPublicData();" +
            "setTimeout(function(){TelegramWidget.unwatchPublicData();TelegramWidget.watchPublicData();console.log('done');},50);</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        var changes: [Bool] = []
        var actions: [WidgetAction] = []
        // Set before the main thread delivers anything: the boot call reaches the callback too.
        host.onWatchPublicData = { changes.append($0) }
        host.onAction = { actions.append($0) }
        pump { host.diagnostics.logs.contains { $0.text == "done" } && changes.count >= 3 }
        pump(0.2) { false }
        XCTAssertEqual(changes, [true, false, true])
        XCTAssertTrue(host.diagnostics.watchesPublicData)
        XCTAssertNil(host.diagnostics.diags["bridge-rejected"])
        XCTAssertNil(host.diagnostics.diags["activation-blocked"], "no tap needed")
        XCTAssertEqual(actions, [], "not an action")
        XCTAssertTrue(host.diagnostics.bridge.isEmpty)

        let never = Harness("<p>x</p>")
        var neverChanges: [Bool] = []
        never.host.onWatchPublicData = { neverChanges.append($0) }
        never.settle(0.1)
        XCTAssertFalse(never.host.diagnostics.watchesPublicData)
        XCTAssertEqual(neverChanges, [])

        var options = WidgetHost.Options()
        options.context = WidgetContext(canQuery: true, preview: true)
        let preview = Harness("<script>TelegramWidget.watchPublicData();console.log('asked');</script>", options: options)
        preview.pump { preview.logs.contains("asked") && preview.count("preview-blocked") > 0 }
        preview.settle(0.1)
        XCTAssertFalse(preview.host.diagnostics.watchesPublicData)
        XCTAssertEqual(preview.host.diagnostics.diags["preview-blocked"]?.details, ["watchPublicData ignored: a preview (an inline result not sent yet) takes no actions"])
    }

    /// sdk.test.ts "copyText: one per tap, to the client as given; never without a tap".
    func testCopyTextNeedsATap() {
        let h = Harness("<button id='b'>c</button><script>setTimeout(function(){TelegramWidget.copyText('timer');},0);document.getElementById('b').onclick=function(){TelegramWidget.copyText('PROMO-2026 ✓');TelegramWidget.copyText('second');};</script>")
        var copied: [String] = []
        h.host.onCopyText = { copied.append($0) }
        h.pump { h.blocked >= 1 }
        h.click(Self.button)
        h.pump { !copied.isEmpty && h.blocked >= 2 }
        h.settle()
        XCTAssertEqual(copied, ["PROMO-2026 ✓"])
        XCTAssertEqual(h.blocked, 2, "the timer's and the second one")
        XCTAssertEqual(h.actions, [], "not an action")
        XCTAssertTrue(h.host.diagnostics.bridge.isEmpty, "not in the raw bridge calls")
    }

    // MARK: theme, locale, gestures

    func testThemeParamsReachTheWidgetAndTheRenderer() throws {
        let theme = WidgetTheme(colorScheme: .dark, params: ["bg_color": "#ABCDEF", "text_color": "red", "made_up": "#123456"], button: WidgetButtonTokens(radius: "10px", pressedOpacity: 0.7), text: WidgetTextTokens(fontSize: 17, padding: "6px 10px"))
        let h = Harness("<script>var p=TelegramWidget.themeParams;console.log('p='+TelegramWidget.colorScheme+','+p.bg_color+','+p.text_color+','+('made_up' in p));TelegramWidget.onEvent('themeChanged',function(){var q=TelegramWidget.themeParams;console.log('changed='+TelegramWidget.colorScheme+','+q.bg_color+','+q.link_color);});</script>", theme: theme)
        h.pump { h.logs.contains { $0.hasPrefix("p=") } }
        XCTAssertTrue(h.logs.contains("p=dark,#abcdef,#ffffff,false"), "\(h.logs)")
        let themes = h.renderer.messages.compactMap { Self.object($0) }.filter { $0["type"] as? String == "theme" }
        XCTAssertEqual(themes.count, 1)
        XCTAssertEqual(Self.object(h.renderer.messages[0])?["type"] as? String, "theme", "the variables come before any op")
        let first = try XCTUnwrap(themes.first?["theme"] as? [String: Any])
        XCTAssertEqual(first["colorScheme"] as? String, "dark")
        XCTAssertEqual(first["params"] as? [String: String], ["bg_color": "#abcdef"])
        XCTAssertEqual(first["button"] as? NSDictionary, ["radius": "10px", "pressedOpacity": 0.7] as NSDictionary)
        XCTAssertEqual(first["text"] as? NSDictionary, ["fontSize": 17, "padding": "6px 10px"] as NSDictionary)

        h.host.setTheme(WidgetTheme(colorScheme: .light, params: ["bg_color": "#101010", "link_color": "#00FF00"]))
        h.pump { h.logs.contains { $0.hasPrefix("changed=") } }
        XCTAssertTrue(h.logs.contains("changed=light,#101010,#00ff00"), "\(h.logs)")
        let last = try XCTUnwrap(h.renderer.messages.compactMap { Self.object($0) }.last { $0["type"] as? String == "theme" }?["theme"] as? [String: Any])
        XCTAssertEqual(last["colorScheme"] as? String, "light")
        XCTAssertEqual(last["params"] as? [String: String], ["bg_color": "#101010", "link_color": "#00ff00"])
        XCTAssertNil(last["button"])
        XCTAssertNil(last["text"])
    }

    func testLocale() {
        var options = WidgetHost.Options()
        options.locale = "ru"
        let h = Harness("<script>console.log('l='+navigator.language+'|'+navigator.languages.join()+'|'+(1234.5).toLocaleString()+'|'+new Intl.NumberFormat('en-US').format(1234.5));</script>", options: options)
        h.pump { h.logs.contains { $0.hasPrefix("l=") } }
        let line = h.logs.first { $0.hasPrefix("l=") } ?? ""
        XCTAssertTrue(line.hasPrefix("l=ru|ru|1"), line)
        XCTAssertTrue(line.hasSuffix(",5|1,234.5"), line)
        options.locale = "ru_RU"
        let malformed = Harness("<script>console.log('l='+navigator.language);</script>", options: options)
        malformed.pump { malformed.logs.contains { $0.hasPrefix("l=") } }
        XCTAssertTrue(malformed.logs.contains("l=en-US"), "\(malformed.logs)")
    }

    /// Vertical drags are the chat's (v0.1.3): the claim the client gets has `y` false whatever the renderer sends.
    func testGestureClaimsReachTheClient() {
        let h = Harness("<p>x</p>")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":true,\"x\":false,\"y\":true}")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":true,\"x\":true,\"y\":true}")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":1,\"x\":1,\"y\":\"yes\"}")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":true,\"x\":true,\"y\":true,\"hold\":true}")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":false,\"x\":false,\"y\":false,\"hold\":1}")
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":false,\"x\":true,\"y\":false,\"hold\":true}")
        // A renderer that sends only `wants` (the Blitz and litehtml prototypes) claims nothing.
        h.renderer.onMessage?("{\"type\":\"gesture\",\"wants\":true}")
        h.pump { h.claims.count >= 7 }
        XCTAssertEqual(h.claims, [
            WidgetGestureClaim(x: false, y: false), WidgetGestureClaim(x: true, y: false), WidgetGestureClaim(x: false, y: false),
            WidgetGestureClaim(x: true, y: false, hold: true), WidgetGestureClaim(x: false, y: false),
            WidgetGestureClaim(x: true, y: false, hold: true),
            WidgetGestureClaim(x: false, y: false),
        ])
    }

    /// The claim the WebView renderer computes from `touch-action` at a pointerdown (gesture-policy.ts `claimOf`) reaches
    /// `onGestureClaim` whole: `none` on an ancestor claims horizontal drags and holds, `pan-y` horizontal drags, `auto`
    /// and `pan-x` nothing; no claim has `y` (v0.1.3). WebKit itself gets the widened value (renderer/touch-action.ts:
    /// vertical pans always allowed, so the list scrolls over the widget) and the widget's script still reads its own.
    @MainActor
    func testTouchActionClaimsFromTheWebViewRenderer() async throws {
        let html = "<div id='free'>auto</div><div id='box' style='touch-action:none'><p id='own'>none</p></div><div id='pan' style='touch-action:pan-y'>pan-y</div><div id='px' style='touch-action:pan-x'>pan-x</div>" +
            "<script>setTimeout(function(){console.log('own='+getComputedStyle(document.getElementById('box')).touchAction+'|'+getComputedStyle(document.getElementById('px')).touchAction);},50);</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 300)
        defer { host.destroy() }
        var claims: [WidgetGestureClaim] = []
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        host.onGestureClaim = { claims.append($0) }
        let window = TestWindow.makeVisible()
        window.attach(host.view)
        var deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !latest.rendered { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(latest.rendered, "\(latest.diags)")
        for (n, id) in ["free", "own", "pan", "px"].enumerated() {
            // A synthetic press: the renderer computes the claim at any primary pointerdown (only trusted input activates).
            let script = "var t=document.getElementById('\(id)'),o={bubbles:true,cancelable:true,isPrimary:true,pointerId:\(n + 1),pointerType:'touch',clientX:60,clientY:5};" +
                "t.dispatchEvent(new PointerEvent('pointerdown',o));t.dispatchEvent(new PointerEvent('pointerup',o));'ok'"
            let result = await withCheckedContinuation { c in host.evaluateInRenderer(script) { c.resume(returning: $0) } }
            XCTAssertEqual(result, "ok", id)
            deadline = Date().addingTimeInterval(5)
            while Date() < deadline, claims.count <= n { try await Task.sleep(nanoseconds: 20_000_000) }
        }
        XCTAssertEqual(claims, [
            WidgetGestureClaim(x: false, y: false, hold: false),
            WidgetGestureClaim(x: true, y: false, hold: true),
            WidgetGestureClaim(x: true, y: false, hold: false),
            WidgetGestureClaim(x: false, y: false, hold: false),
        ])
        let engine = await withCheckedContinuation { c in
            host.evaluateInRenderer("getComputedStyle(document.getElementById('own').parentNode).touchAction+'|'+getComputedStyle(document.getElementById('px')).touchAction") { c.resume(returning: $0) }
        }
        XCTAssertEqual(engine, "pan-y|pan-x pan-y", "WebKit always pans vertically")
        deadline = Date().addingTimeInterval(5)
        while Date() < deadline, !latest.logs.contains(where: { $0.text.hasPrefix("own=") }) { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertEqual(latest.logs.first { $0.text.hasPrefix("own=") }?.text, "own=none|pan-x", "the widget reads its own touch-action")
    }

    // MARK: the API before v1

    @available(*, deprecated)
    func testStringThemeAndOnBridgeStillWork() {
        let renderer = RecordingRenderer()
        let host = try! WidgetHost(html: "<script>console.log('scheme='+TelegramWidget.colorScheme);TelegramWidget.onTheme(function(t){console.log('onTheme='+t);});TelegramWidget.ready();</script>", theme: "dark", width: 300, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        var raw: [(String, String)] = []
        var actions: [WidgetAction] = []
        host.onBridge = { raw.append(($0, $1)) }
        host.onAction = { actions.append($0) }
        pump { !raw.isEmpty }
        host.setTheme("light")
        pump { host.diagnostics.logs.contains { $0.text == "onTheme=light" } }
        let logs = host.diagnostics.logs.map(\.text)
        XCTAssertEqual(logs.filter { $0.hasPrefix("scheme=") || $0.hasPrefix("onTheme=") }, ["scheme=dark", "onTheme=dark", "onTheme=light"])
        XCTAssertEqual(actions, [.ready])
        XCTAssertEqual(raw.map(\.0), ["ready"])
        XCTAssertEqual(raw.first?.1, "[]")
        XCTAssertEqual(host.diagnostics.actions, [.ready])
    }

    // MARK: the WebView renderer

    /// The renderer core sets the variables inline and important on the widget's root: the widget's own `:root` rule cannot replace them.
    /// The UA layer draws the root on them: the text tokens' size and the padding token as the body's margin; the root
    /// has no background of its own (the bubble behind the transparent renderer is the canvas).
    @MainActor
    func testThemeVariablesOnTheRendererRoot() async throws {
        let window = TestWindow.makeVisible()
        defer { window.close() }
        let theme = WidgetTheme(colorScheme: .dark, params: ["bg_color": "#123456"], button: WidgetButtonTokens(radius: "10px", font: "15px Helvetica"), text: WidgetTextTokens(fontSize: 17, lineHeight: 9))
        let host = try WidgetHost(html: "<style>:root{--tg-theme-bg-color:#ff0000}</style><p>x</p>", theme: theme, width: 300)
        defer { host.destroy() }
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        window.attach(host.view)
        let deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !latest.rendered { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(latest.rendered, "\(latest.diags)")
        let probe = "(function(){var s=getComputedStyle(document.documentElement);return ['--tg-theme-bg-color','--tg-color-scheme','--tg-button-radius','--tg-button-font','--tg-theme-text-color','--tg-text-font-size','--tg-text-line-height','--tg-text-padding'].map(function(n){return s.getPropertyValue(n).trim();}).concat([s.fontSize,s.backgroundColor,getComputedStyle(document.body).marginLeft,getComputedStyle(document.body).paddingLeft]).join('|');})()"
        let first = await withCheckedContinuation { c in host.evaluateInRenderer(probe) { c.resume(returning: $0) } }
        XCTAssertEqual(first, "#123456|dark|10px||#ffffff|17px|1.35|8px 12px|17px|rgba(0, 0, 0, 0)|12px|0px", "a font that is not a generic family and a line height over 3 are dropped by the renderer")
        host.setTheme(WidgetTheme(colorScheme: .light, params: ["bg_color": "#ABCDEF"]))
        var second = ""
        let changed = Date().addingTimeInterval(5)
        while Date() < changed, second != "#abcdef|light|||#000000|15px|1.35|8px 12px|15px|rgba(0, 0, 0, 0)|12px|0px" {
            try await Task.sleep(nanoseconds: 50_000_000)
            second = await withCheckedContinuation { c in host.evaluateInRenderer(probe) { c.resume(returning: $0) } }
        }
        XCTAssertEqual(second, "#abcdef|light|||#000000|15px|1.35|8px 12px|15px|rgba(0, 0, 0, 0)|12px|0px")
    }

    // MARK: the client's time over the widget (docs/sdk-v1.md §2.17)

    /// The option reaches the renderer right after the theme and the widget from its first line (checked by the shim:
    /// out of range is 0); setContentSafeArea sends the variables and one contentSafeAreaChanged with a fresh frozen
    /// object; the same value fires nothing; NaN reaches nobody as NaN.
    func testContentSafeAreaReachesTheRendererAndTheWidget() throws {
        var options = WidgetHost.Options()
        options.contentSafeArea = WidgetContentSafeArea(top: 99, bottom: 24, cornerRight: 72)
        let h = Harness("<script>var first=TelegramWidget.contentSafeAreaInset;console.log('first='+JSON.stringify(first));TelegramWidget.onEvent('contentSafeAreaChanged',function(){var a=TelegramWidget.contentSafeAreaInset;console.log('changed='+[a.bottom,a.cornerLeft,a.cornerRight,Object.isFrozen(a),a!==first,this===TelegramWidget].join(':'));});</script>", options: options)
        h.pump { h.logs.contains { $0.hasPrefix("first=") } }
        XCTAssertTrue(h.logs.contains("first={\"top\":0,\"right\":0,\"bottom\":24,\"left\":0,\"cornerLeft\":0,\"cornerRight\":72}"), "\(h.logs)")
        let messages = h.renderer.messages.compactMap { Self.object($0) }
        XCTAssertEqual(messages.first?["type"] as? String, "theme")
        XCTAssertEqual(messages.dropFirst().first?["type"] as? String, "safeArea", "right after the theme, before any op")
        let inset = try XCTUnwrap(messages.dropFirst().first?["inset"] as? [String: Double])
        XCTAssertEqual(inset["bottom"], 24)
        XCTAssertEqual(inset["cornerRight"], 72)
        XCTAssertEqual(inset["top"], 99, "passed as given: the renderer core checks the range")
        let rtl = WidgetContentSafeArea(bottom: 26.5, cornerLeft: 80)
        h.host.setContentSafeArea(rtl)
        h.host.setContentSafeArea(rtl)
        h.host.setContentSafeArea(WidgetContentSafeArea(bottom: .nan))
        h.pump { h.logs.filter { $0.hasPrefix("changed=") }.count >= 2 }
        XCTAssertEqual(h.logs.filter { $0.hasPrefix("changed=") }, ["changed=26.5:80:0:true:true:true", "changed=0:0:0:true:true:true"])
        let sent = h.renderer.messages.compactMap { Self.object($0) }.filter { $0["type"] as? String == "safeArea" }
        XCTAssertEqual(sent.count, 4)
        XCTAssertEqual((sent.last?["inset"] as? [String: Double])?["bottom"], 0, "NaN goes out as 0")
    }

    /// The renderer core sets the six variables on the root (the widget's own `:root` rule cannot replace them), the
    /// validator's `env()` resolves to them, and the UA layer pads the body by the band; a change moves all of it.
    @MainActor
    func testContentSafeAreaOnTheRendererRoot() async throws {
        let window = TestWindow.makeVisible()
        defer { window.close() }
        var options = WidgetHost.Options()
        options.contentSafeArea = WidgetContentSafeArea(bottom: 24, cornerRight: 64)
        let host = try WidgetHost(html: "<style>:root{--tg-content-safe-area-inset-bottom:50px}</style><p style=\"margin:0;padding-bottom:env(safe-area-inset-bottom, 99px)\">x</p>", width: 300, options: options)
        defer { host.destroy() }
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        window.attach(host.view)
        let deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !latest.rendered { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(latest.rendered, "\(latest.diags)")
        let probe = "(function(){var s=getComputedStyle(document.documentElement);return ['--tg-content-safe-area-inset-bottom','--tg-content-safe-area-corner-right','--tg-content-safe-area-corner-left'].map(function(n){return s.getPropertyValue(n).trim();}).concat([getComputedStyle(document.body).paddingBottom,getComputedStyle(document.querySelector('p')).paddingBottom]).join('|');})()"
        let first = await withCheckedContinuation { c in host.evaluateInRenderer(probe) { c.resume(returning: $0) } }
        XCTAssertEqual(first, "24px|64px|0px|24px|24px")
        host.setContentSafeArea(WidgetContentSafeArea(bottom: 20, cornerLeft: 70))
        var second = ""
        let changed = Date().addingTimeInterval(5)
        while Date() < changed, second != "20px|0px|70px|20px|20px" {
            try await Task.sleep(nanoseconds: 50_000_000)
            second = await withCheckedContinuation { c in host.evaluateInRenderer(probe) { c.resume(returning: $0) } }
        }
        XCTAssertEqual(second, "20px|0px|70px|20px|20px")
    }

    // MARK: helpers

    private static func object(_ json: String) -> [String: Any]? {
        (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any]
    }

    private func pump(_ timeout: TimeInterval = 5, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.01)) }
    }

    // MARK: the wallet (docs/sdk-v1.md §2.18)

    private static let tx = WalletTransaction(validUntil: 1900000000, messages: [WalletMessage(address: "EQ" + String(repeating: "ab", count: 23), amount: "1500000000")])
    private static let txJson = #"{"valid_until":1900000000,"messages":[{"address":"EQ"# + String(repeating: "ab", count: 23) + #"","amount":"1500000000"}]}"#
    /// sdk.test.ts's payer: a tap asks the bot with the wallet, the answer's transaction goes to `sendTransaction`, every outcome into `log`.
    private static let payer = "<button id='b'>pay</button><script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}" +
        "document.getElementById('b').onclick=function(){TelegramWidget.request('pay:42',{wallet:true})" +
        ".then(function(a){out('tx');return TelegramWidget.wallet.sendTransaction(JSON.parse(a.result),'paid:42');})" +
        ".then(function(b){out('ok:'+b.result);},function(e){out('err:'+e.code);return TelegramWidget.wallet.sendTransaction(JSON.parse('" + txJson + "'),'again').catch(function(e2){out('again:'+e2.code);});});};</script>"

    /// `TelegramWidget.wallet` only where the client has the wallet and the widget may ask the bot; never in a preview.
    func testWalletOnlyWhereTheClientHasItAndTheWidgetMayAsk() {
        let probe = "<script>console.log('w='+(TelegramWidget.wallet===null?'null':typeof TelegramWidget.wallet.sendTransaction+Object.isFrozen(TelegramWidget.wallet)));</script>"
        let cases: [(WidgetContext, String)] = [
            (WidgetContext(canQuery: true, backgroundQuery: true, chatType: .private, wallet: true), "w=functiontrue"),
            (WidgetContext(isCopy: true, canQuery: true, wallet: true), "w=functiontrue"),
            (Self.privateChat, "w=null"),
            (WidgetContext(chatType: .private, wallet: true), "w=null"),
            (WidgetContext(canQuery: true, chatType: .private, preview: true, wallet: true), "w=null"),
        ]
        for (context, expect) in cases {
            let h = harness(probe, context)
            h.pump { h.logs.contains { $0.hasPrefix("w=") } }
            XCTAssertEqual(h.logs.first { $0.hasPrefix("w=") }, expect, "\(context)")
        }
    }

    /// A tap pays: the request asks for the wallet, the bot answers with the transaction, the call on that answer goes
    /// out without a new tap (the grant), the bot's answer to the payment resolves it.
    func testATapPaysOnTheAnswerToItsRequest() {
        var context = Self.privateChat
        context.wallet = true
        let h = harness(Self.payer, context)
        var asked: [WidgetRequest] = []
        var calls: [WalletCall] = []
        h.host.onRequest = { request, respond in asked.append(request); respond(.success(WidgetRequestAnswer(result: Self.txJson))) }
        h.host.onWallet = { call, respond in calls.append(call); respond(.success(WidgetRequestAnswer(result: "thanks"))) }
        h.click(Self.button)
        XCTAssertEqual(log(h, entries: 2), "tx ok:thanks")
        XCTAssertEqual(asked, [WidgetRequest(data: "pay:42", wallet: true)])
        XCTAssertEqual(calls, [.sendTransaction(Self.tx, data: "paid:42")])
        XCTAssertEqual(h.actions, [], "a wallet call is no action")
    }

    /// The user declines: REJECTED, and a refusal brings no grant (the next call needs a tap); a request's failure
    /// `.rejected` is FAILED there; from a timer neither a wallet call nor a request for the wallet goes out.
    func testRefusalsAndTheBackground() {
        let h = harness(Self.payer, WidgetContext(canQuery: true, chatType: .group, wallet: true))
        var calls = 0
        h.host.onRequest = { _, respond in respond(.success(WidgetRequestAnswer(result: Self.txJson))) }
        h.host.onWallet = { _, respond in calls += 1; respond(.failure(.rejected)) }
        h.click(Self.button)
        XCTAssertEqual(log(h, entries: 3), "tx err:REJECTED again:NOT_ALLOWED")
        XCTAssertEqual(calls, 1)
        XCTAssertEqual(h.host.diagnostics.diags["request-blocked"]?.details, ["wallet.sendTransaction refused (no-activation): it needs a tap on the visible widget, or the answer to a call made by a tap within 10 s; one call each"])

        let plain = harness(Self.requester, Self.group)
        plain.host.onRequest = { _, respond in respond(.failure(.rejected)) }
        plain.click(Self.button)
        XCTAssertEqual(tapLog(plain, entries: 2), "err:tap2:WidgetRequestError:BUSY err:tap:WidgetRequestError:FAILED")

        var context = Self.privateChat
        context.wallet = true
        let timer = harness("<script>var log=[];function out(s){log.push(s);console.log('log='+log.join(' '));}setTimeout(function(){" +
            "TelegramWidget.wallet.signData({type:'text',text:'x'},'s').catch(function(e){out('sign:'+e.code);});" +
            "TelegramWidget.request('w',{wallet:true}).catch(function(e){out('rq:'+e.code);});},0);</script>", context)
        timer.host.onRequest = { _, respond in calls += 100; respond(.success(WidgetRequestAnswer())) }
        timer.host.onWallet = { _, respond in calls += 100; respond(.success(WidgetRequestAnswer())) }
        XCTAssertEqual(log(timer, entries: 2).split(separator: " ").sorted(), ["rq:NOT_ALLOWED", "sign:NOT_ALLOWED"])
        XCTAssertEqual(calls, 1, "neither reaches the client")
    }

    /// signData on a tap with its payload as checked; a client without `onWallet` at delivery refuses (`unsupported`).
    func testSignDataOnATapAndNoClientSide() {
        let html = "<button id='b'>s</button><script>document.getElementById('b').onclick=function(){" +
            "TelegramWidget.wallet.signData({type:'text',text:'I agree',network:null},'sign:1').then(function(a){console.log('ok:'+a.result);},function(e){console.log('err:'+e.code);});};</script>"
        let context = WidgetContext(isCopy: true, canQuery: true, wallet: true)
        let h = harness(html, context)
        var calls: [WalletCall] = []
        h.host.onWallet = { call, respond in calls.append(call); respond(.success(WidgetRequestAnswer(result: "signed"))) }
        h.click(Self.button)
        h.pump { h.logs.contains { $0.hasPrefix("ok:") || $0.hasPrefix("err:") } }
        XCTAssertTrue(h.logs.contains("ok:signed"), "\(h.logs)")
        XCTAssertEqual(calls, [.signData(SignDataPayload(type: .text, text: "I agree"), data: "sign:1")])

        let none = harness(html, context)
        none.click(Self.button)
        none.pump { none.logs.contains { $0.hasPrefix("err:") } }
        XCTAssertTrue(none.logs.contains("err:NOT_ALLOWED"), "\(none.logs)")
        XCTAssertEqual(none.host.diagnostics.diags["request-blocked"]?.details, ["wallet call refused: the client handles no wallet calls (WidgetHost.onWallet)"])
    }
}

/// A renderer that announces `ready` only when the test says so (`release`): until then the host has not started the widget.
final class HeldRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?

    func load() {}
    func release() { onMessage?("{\"type\":\"ready\"}") }

    func send(_ json: String) {
        guard let message = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any], message["type"] as? String == "query", let qid = message["qid"] as? Int else { return }
        DispatchQueue.main.async { [weak self] in self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}") }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

/// Reports a size synchronously while it receives an ops batch: 100, 200 or 300 for the batch that carries the text
/// `h100`, `h200` or `h300` (the latest marker in the batch), so a state's height names the task whose ops made it.
final class MarkerSizeRenderer: Renderer {
    let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
    var onMessage: ((String) -> Void)?
    var onDiag: ((String, String) -> Void)?

    func load() { onMessage?("{\"type\":\"ready\"}") }

    func send(_ json: String) {
        guard let message = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any], let type = message["type"] as? String else { return }
        if type == "ops" {
            if let height = [("h300", 300), ("h200", 200), ("h100", 100)].first(where: { json.contains($0.0) })?.1 {
                onMessage?("{\"type\":\"size\",\"width\":300,\"height\":\(height)}")
            }
        } else if type == "query", let qid = message["qid"] as? Int {
            DispatchQueue.main.async { [weak self] in self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}") }
        }
    }

    func setTheme(_ theme: String) {}
    func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
    func destroy() {}
}

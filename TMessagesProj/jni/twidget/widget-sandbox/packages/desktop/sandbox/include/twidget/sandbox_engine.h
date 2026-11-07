// The sandbox engine: the C++ counterpart of src/host/worker.ts and of
// ios SandboxEngine.swift / android SandboxEngine.kt. Owns one QuickJS runtime
// per widget on its own JSThread, exposes exactly one capability to the
// untrusted code (`__host(kind, arg)` + `__hostBinary()`), enforces CPU and
// memory limits, and drives timers / frames / scripts.
//
// Everything that comes out of the context is UNTRUSTED: the delegate must
// pass it through TrustedValidator before it reaches any renderer. SDK v1
// calls (docs/sdk-v1.md §3.2) are checked here with sdk_policy.h before the
// delegate sees them: `onBridgeAction`, `onResizeAnchor`, `onStorage`,
// `onBridgeRequest` and `onBridgeCopyText` get only what passed.
#pragma once

#include "twidget/cpu_budget.h"
#include "twidget/intl.h"
#include "twidget/js_thread.h"
#include "twidget/quickjs_context.h"
#include "twidget/resources.h"
#include "twidget/sdk_policy.h"

#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace twidget {

/// What the client draws over the widget, in CSS px (docs/sdk-v1.md §2.17, §5.12). A widget with no caption below it gets the
/// message time and status over its bottom corner, as on a photo: `bottom` is the height of that band and `cornerRight`
/// (or `cornerLeft` in a right-to-left interface: a physical side) the width of the corner the time takes in it; with a
/// caption everything is 0 (the time is in the caption's line). The widget sees it as `env(safe-area-inset-*)`, the
/// `--tg-content-safe-area-*` variables and `TelegramWidget.contentSafeAreaInset`. Passed as given: the renderer core
/// and the shim keep an inset of 0–64 and a corner of 0–200, anything else is 0.
struct WidgetContentSafeArea {
    double top = 0, right = 0, bottom = 0, left = 0, cornerLeft = 0, cornerRight = 0;
    /// The TS `ContentSafeArea` shape; a value that is not finite is 0 (JSON has no NaN).
    Json json() const;
};

/// Mirrors `SandboxConfig` / `LIMIT_DEFAULTS` in src/shared/protocol.ts (docs/decisions.md, "Strict limits from the start").
struct SandboxConfig {
    size_t memoryLimitBytes = size_t(8) << 20;
    /// Longest continuous run of widget JS in one task before the runtime is killed.
    double cpuLimitMs = 500;
    /// JS time the widget may use per `cpuQuotaWindowMs` (sliding window); beyond it timers and frames are deferred, never killed.
    double cpuQuotaMs = 300;
    double cpuQuotaWindowMs = 1000;
    /// Kill after this long of uninterrupted throttling; 0 disables (default).
    double cpuKillAfterMs = 0;
    /// The host's main-thread work for the widget per `cpuQuotaWindowMs`, apart from the JS quota: the time its layout
    /// queries block the renderer and the modelled cost of the ops it sends (CpuBudget::opsCostMs: 25 µs per op, 2 µs per
    /// canvas command). Beyond it timers and frames are deferred like under the JS quota.
    double mainThreadQuotaMs = 100;
    /// Live timers a widget may hold; the shim throws RangeError beyond it, the engine refuses as a backstop.
    int maxTimers = 64;
    /// Initial visibility (see `setVisible`): hidden widgets get no frames and timers at 1 Hz.
    bool visible = true;
    double innerWidth = 400;
    double innerHeight = 300;
    double devicePixelRatio = 1;
    std::string theme = "light";
    /// The chat's palette (docs/sdk-v1.md §2.3); the engine passes only the 15 theme keys with `#rrggbb`
    /// values (sdk::checkThemeParams), the shim fills the rest from the scheme's defaults.
    std::map<std::string, std::string> themeParams;
    /// The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when malformed.
    std::string locale = std::string(sdk::DEFAULT_LOCALE);
    /// What the client draws over the widget at boot (docs/sdk-v1.md §2.17); later changes go through `contentSafeArea`.
    WidgetContentSafeArea contentSafeArea;
    /// Web Storage of this message as the client keeps it; an area over the quota drops the whole
    /// preload (diagnostic `storage-rejected`, the widget starts empty).
    WidgetStorage storage;
    /// The bot's `chatData` and `publicData` at start (docs/sdk-v1.md §2.13); later edits go through
    /// `setData`. Checked at boot: a field over 1024 bytes, with a NUL or malformed is null (diagnostic `data-rejected`).
    WidgetData data;
    /// The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer
    /// (docs/sdk-v1.md §4.4). All false: no requests.
    WidgetContext context;
    /// "strict" (default): privileged bridge calls need a user gesture, text values are capped at
    /// 32 KB and visible text is budgeted, and the shim refuses at the validator's counted caps (2000 canvas
    /// commands per canvas per task, 64 live script animations). "compat": the behaviour before that hardening,
    /// for tests and tooling. Anything but exactly "compat" is strict (isStrictPolicy); the shim gets it in its boot config.
    std::string policy = "strict";
    /// Opt-in sandbox features of docs/protocol.md, such as "webAnimations"; the renderer must support them.
    /// This runtime honours webAnimations, noInPlaceText, noAttrFast and noCanvasRect and drops the rest.
    std::vector<std::string> features;
};

/// Fail-closed policy parsing, the twin of resolvePolicy in src/shared/protocol.ts.
inline bool isStrictPolicy(const std::string& policy) { return policy != "compat"; }

struct SandboxStats {
    double jsMs = 0;
    int tasks = 0;
    size_t memoryBytes = 0;
    int ops = 0;
    int queries = 0;
    double queryMs = 0;
    int queryCacheHits = 0;
    int missedMutations = 0;
    /// Tasks deferred by the CPU quota or the main-thread window; JS time used in the current quota window at the time of the report.
    int throttledTasks = 0;
    double cpuWindowMs = 0;
    /// The validator's CPU time on this widget's messages (`chargeValidator`), charged to the JS quota next to `jsMs`.
    double validatorMs = 0;
};

struct QueryAnswer {
    std::string json;
    std::optional<std::vector<uint8_t>> binary;
};

class SandboxEngine;

/// How a delegate completes a request (`onBridgeRequest`): call it with the client's outcome, or
/// `unsupported()` when the client handles no requests; once, from any thread. Later calls, and calls after the engine
/// died or was destroyed, are ignored; every copy shares the once. It converts to the client-facing
/// `std::function<void(const WidgetRequestOutcome&)>` (WidgetHost::onRequest's `respond`).
class WidgetRequestResponder {
public:
    void operator()(const WidgetRequestOutcome& outcome) const { settle_(outcome); }
    /// No client side for requests (WidgetHost without `onRequest` when the request is delivered): the widget gets
    /// NOT_ALLOWED with the reason `unsupported`.
    void unsupported() const { settle_(std::nullopt); }

private:
    friend class SandboxEngine;
    explicit WidgetRequestResponder(std::function<void(const std::optional<WidgetRequestOutcome>&)> settle) : settle_(std::move(settle)) {}
    std::function<void(const std::optional<WidgetRequestOutcome>&)> settle_;
};

/// All delegate calls happen on the engine's thread, never on the caller's.
/// `onQuery` is synchronous: it may block the engine thread (and must not block the UI thread).
class SandboxEngineDelegate {
public:
    virtual ~SandboxEngineDelegate() = default;
    virtual void onOps(SandboxEngine& engine, const std::string& json) = 0;
    virtual QueryAnswer onQuery(SandboxEngine& engine, const std::string& json) = 0;
    virtual void onListen(SandboxEngine& engine, const std::string& json) = 0;
    virtual void onPreventDefault(SandboxEngine& engine, const std::string& json) = 0;
    virtual void onRequestFrame(SandboxEngine& engine) = 0;
    virtual void onLog(SandboxEngine& engine, const std::string& level, const std::string& text) = 0;
    virtual void onDiag(SandboxEngine& engine, const std::string& kind, const std::string& detail) = 0;
    /// A bridge call that passed every check (docs/sdk-v1.md §3.2: arguments, user activation, the haptic limit).
    virtual void onBridgeAction(SandboxEngine&, const WidgetAction&) {}
    /// `TelegramWidget.setResizeAnchor(edge)` (docs/sdk-v1.md §2.12, since v0.1.3): "top" or "bottom", the edge of the
    /// bubble that stays put when the widget's height changes. Not an action (never `onBridgeAction` / `onBridge`), no
    /// gesture needed, at any visibility; the shim sends only changes, during the task and so before that task's `onOps`.
    virtual void onResizeAnchor(SandboxEngine&, const std::string& /*edge*/) {}
    /// `TelegramWidget.watchPublicData()` / `unwatchPublicData()` (round 7): whether the widget watches its shared
    /// `public_data`. Not an action, no gesture needed, at any visibility; the shim sends only changes (false at start).
    virtual void onPublicDataWatch(SandboxEngine&, bool /*on*/) {}
    /// The new full content of a Web Storage area ("local" or "session") at the end of a task that changed it;
    /// once per area of a commit, local first. Checked against the quota; strings as in sdk_policy.h.
    virtual void onStorage(SandboxEngine&, const std::string& /*area*/, const StorageArea& /*values*/) {}
    /// `TelegramWidget.request(data)` (docs/sdk-v1.md §2.14) that passed its checks and the request gate (a
    /// press, or the background rules; one in flight per widget): send it to the bot and complete `respond` once, from
    /// any thread, with the answer or the failure, or `respond.unsupported()` when the client handles no requests
    /// (decided here, at delivery). Until then every other request of the widget is BUSY. Not an action (never
    /// `onBridgeAction` / `onBridge`). The default handles none.
    virtual void onBridgeRequest(SandboxEngine&, const WidgetRequest&, WidgetRequestResponder respond) { respond.unsupported(); }
    /// A wallet call (`TelegramWidget.wallet`, docs/sdk-v1.md §2.18) that passed its checks and the request gate (a press
    /// or the grant; one call in flight with the requests): show the client's own wallet window with the bot of the
    /// message, send the transaction (or sign the payload) on the user's confirmation, send the outcome to the bot and
    /// complete `respond` once, from any thread, with the bot's answer or a failure (`Rejected`, `BadRequest`, a
    /// request's), or `respond.unsupported()`. Not an action. The default handles none.
    virtual void onBridgeWallet(SandboxEngine&, const WalletCall&, WidgetRequestResponder respond) { respond.unsupported(); }
    /// `TelegramWidget.copyText(text)`: 1–256 UTF-16 code units, well-formed; a privileged call (one per
    /// press, shared with the links and a request's press; `activation-blocked` without one). Not an action.
    virtual void onBridgeCopyText(SandboxEngine&, const std::string& /*text*/) {}
    /// Deprecated since SDK v1 (`onBridgeAction`): the raw method and arguments JSON of each call `onBridgeAction` gets, right after it.
    virtual void onBridge(SandboxEngine&, const std::string& /*method*/, const std::string& /*argsJson*/) {}
    virtual void onLoaded(SandboxEngine& engine) = 0;
    virtual void onStats(SandboxEngine& engine, const SandboxStats& stats) = 0;
    virtual void onDied(SandboxEngine& engine, const std::string& reason) = 0;
    virtual void onAnim(SandboxEngine&, const std::string&) {}
};

class SandboxEngine {
public:
    SandboxEngine(std::string html, SandboxConfig config, std::shared_ptr<SandboxResources> resources,
                  std::shared_ptr<IntlProvider> intl = nullptr);
    /// Stops the thread; outstanding delegate calls finish first. Safe from any thread but the engine's.
    ~SandboxEngine();
    SandboxEngine(const SandboxEngine&) = delete;
    SandboxEngine& operator=(const SandboxEngine&) = delete;

    /// Set before start(); the pointer must outlive the engine.
    void setDelegate(SandboxEngineDelegate* delegate) { delegate_ = delegate; }
    JSThread& thread() { return thread_; }

    void start();
    void terminate();
    /// The host stops the widget (a counted cap of the validator passed under strict, its diagnostic `limit`): the
    /// runtime dies with `reason` as it does for memory or CPU (`onDied(reason)`). From the engine thread (a delegate
    /// callback such as `onOps`) at once, and the task that is still on the stack is interrupted; from any other
    /// thread asynchronously.
    void stop(const std::string& reason);
    /// The validator's CPU time for this widget's messages (TrustedValidator's CPU sink, `ms` of its thread's clock):
    /// recorded against the JS quota on the engine thread as it arrives (`stats.validatorMs`; with the debt rule a late
    /// record still counts in full). From the engine thread at once, from any other thread asynchronously.
    void chargeValidator(double ms);

    // Inputs from the host (all asynchronous). A preview (`WidgetContext::preview`) takes no input: events are dropped.
    void event(const std::string& json);
    /// A real user input event from the renderer (press, click, key press); every event of one physical gesture carries the same increasing `gesture` number.
    void activationEvent(const std::string& json, int gesture);
    void frame(double time);
    void viewport(double innerWidth, double innerHeight, double dpr);
    void resize(const std::string& entriesJson);
    /// The chat's theme changed: the colour scheme and the palette (checked here, as at boot) in one task;
    /// the shim updates `colorScheme` and `themeParams`, then fires `themeChanged`.
    void theme(const std::string& colorScheme, const std::map<std::string, std::string>& themeParams = {});
    /// What the client draws over the widget changed (docs/sdk-v1.md §2.17): `TelegramWidget.contentSafeAreaInset` and
    /// `contentSafeAreaChanged` in one task; the shim checks the values. Before boot it only replaces the config's.
    void contentSafeArea(const WidgetContentSafeArea& area);
    /// The host knows whether the widget is on screen; the sandbox does not.
    void setVisible(bool visible);
    /// The renderer's primary press began / ended (pointerdown; pointerup or pointercancel), trusted input only, never the
    /// widget's events (v0.2.2): haptics pass while a press is down and 5 s after it is lifted; outside a touch only one
    /// 1 s series (sdk::HapticGate). WidgetHost calls these on the renderer's `gesture` and `release` messages.
    void pressBegan();
    void pressEnded();
    /// The bot's data changed (an edit the client got from the server): the full state of both strings, checked as at
    /// boot (`data-rejected`; a copy keeps no `chatData`, its publicData can change). The widget gets `chatDataChanged`,
    /// then `publicDataChanged`, for each that differs.
    void setData(const WidgetData& data);

    /// Evaluates trusted diagnostic code in the widget context (tests only). Returns the string value or the error text.
    std::string probe(const std::string& code);

private:
    struct Deferred { std::string kind; std::string arg; };
    /// What a request's `respond` reaches the engine through: null once the engine is being destroyed (under `mutex`).
    struct Liveness {
        std::mutex mutex;
        SandboxEngine* engine = nullptr;
    };

    void boot();
    void enqueue(const std::string& kind, const std::string& arg);
    /// The body of `enqueue`, on the engine thread: buffered before boot, a hidden frame dropped, else dispatched.
    void input(const std::string& kind, const std::string& arg);
    bool dispatch(const std::string& kind, const std::string& arg);
    void deferTask(const std::string& kind, const std::string& arg);
    void scheduleWake(double atSeconds);
    void wake();
    void onTimerFired(int id);
    HostReturn hostCall(const std::string& kind, const std::string& arg);
    /// A checked `request` call: the request gate, then the delegate (or held until the widget appears).
    void request(int64_t id, const std::string& data, bool wallet = false);
    /// A checked wallet call: the request gate (a press or the grant), then the delegate.
    void walletCall(int64_t id, const WalletCall& call);
    /// A request the gate let through, to the delegate; its outcome frees the gate (`answerRequest`).
    void deliverRequest(int64_t id, const std::string& data, bool wallet = false);
    /// The responder of request or wallet call `id`: once, from any thread, back to `answerRequest` on the engine thread.
    WidgetRequestResponder responder(int64_t id, bool wallet);
    /// A request's or wallet call's outcome back on the engine thread (nullopt: the client handles none): the gate frees
    /// (an answer brings the grant when the call had a press or the grant), the answer is checked and goes into the widget.
    void answerRequest(int64_t id, const std::optional<WidgetRequestOutcome>& outcome, bool wallet = false);
    /// `request-blocked` (at most 20 per widget) and the `bridge` answer event that refuses request `id`.
    std::string refusal(int64_t id, std::string_view error, std::string_view reason, const std::string& detail);
    /// `{"chatData":…,"publicData":…}` members of `checked` set on `event` (JSON null for none); `data-rejected` when the
    /// client passed a field that broke the rules.
    void putData(Json& event, const sdk::CheckedWidgetData& checked);
    std::string syncQuery(const std::string& json);
    void kill(const std::string& reason);
    void handleError(const std::string& text, const std::string& where);
    void drainJobs();
    std::optional<std::string> callTw(const std::string& kind, const std::string& arg);
    void settle();
    void evalScript(const std::string& code, const std::string& filename, bool asModule = false);
    /// One of our own bundles: the precompiled `.qbc` when shipped and matching the source,
    /// else the source. Returns "bytecode" or "source" for the diag.
    std::string evalBundle(const std::string& source, const std::vector<uint8_t>& bytecode, const std::string& filename);
    void pumpScripts();
    void resetDeadline();
    void task(const std::string& kind, double sliceMs, const std::function<void()>& body);
    void postStats(bool force);
    static double nowMs();
    static double nowSeconds();

    JSThread thread_;
    SandboxEngineDelegate* delegate_ = nullptr;
    std::string html_;
    SandboxConfig config_;
    std::shared_ptr<SandboxResources> resources_;
    std::shared_ptr<IntlProvider> intl_;
    std::unique_ptr<QuickJSContext> js_;
    bool dead_ = false;
    bool started_ = false;
    SandboxStats stats_;
    std::map<int, std::shared_ptr<JSThread::Scheduled>> timers_;
    std::optional<std::vector<uint8_t>> lastBinary_;
    double lastStatsPost_ = 0;
    double lastMemCheck_ = 0;
    bool timerLimitReported_ = false;
    static constexpr double kMaxTimerDelaySeconds = 86400; // MAX_TIMER_DELAY_MS

    // Visibility and the deferred-task queue (mirrors src/host/worker.ts).
    bool visible_;
    bool frameWanted_ = false;
    std::vector<Deferred> deferred_;
    std::shared_ptr<JSThread::Scheduled> wakeTimer_;
    double wakeAt_ = 0;
    double hiddenTickAt_ = 0;

    // CPU quota: decide before a task, record after it.
    CpuBudget budget_;
    /// The main-thread window (`mainThreadQuotaMs`): layout queries and the cost of the ops, apart from the JS quota.
    CpuBudget mainBudget_;
    double currentSliceMs_ = 0;
    std::string currentKind_ = "boot";
    double excludeMs_ = 0;
    /// Wall time this task's layout queries have blocked for, how many the host answered, and which limit they passed
    /// (CpuBudget::queryLimitHit: "count", "time", or empty).
    double taskQueryMs_ = 0;
    int taskQueries_ = 0;
    /// Modelled main-thread cost of the ops this task sent (CpuBudget::opsCostMs).
    double taskOpsCostMs_ = 0;
    std::string_view queryLimitHit_;
    double lastThrottleDiag_ = -1e300;
    std::map<std::string, int> throttledSinceDiag_;
    std::vector<std::pair<std::string, std::string>> pendingBeforeBoot_;
    std::shared_ptr<JSThread::Scheduled> trailingStats_;

    // User activation: the gesture whose event task is running (0 outside one); one gesture unlocks one privileged bridge call.
    int activeGesture_ = 0;
    int consumedGesture_ = 0;
    int blockedBridgeReported_ = 0;
    // SDK v1 (docs/sdk-v1.md §3.2): malformed calls and commits are reported at most 20 times each; haptics are rate-limited.
    int rejectedBridgeReported_ = 0;
    int rejectedStorageReported_ = 0;
    sdk::HapticGate haptics_;
    void rejectStorage(const std::string& detail);
    // Requests to the bot (docs/sdk-v1.md §2.14): one in flight, a press or the background rules; refusals reported at most 20 times.
    sdk::RequestGate requests_;
    /// A background request the widget made off screen: it goes to the delegate when the widget appears (`setVisible`).
    struct HeldRequest {
        int64_t id;
        std::string data;
    };
    std::optional<HeldRequest> heldRequest_;
    int blockedRequestsReported_ = 0;
    // A preview's dropped bridge calls (docs/sdk-v1.md §2.16), reported at most 20 times.
    int previewBlockedReported_ = 0;
    std::shared_ptr<Liveness> liveness_ = std::make_shared<Liveness>();
};

} // namespace twidget

// WidgetHost: one widget = one SandboxEngine (QuickJS on its own JSThread) +
// one TrustedValidator + one Renderer (whatever the factory builds — the
// Blitz prototype here, the Web App window on tdesktop). The only place where
// the three meet; nothing from the engine reaches the renderer without passing
// the validator. The C++ copy of src/host/widget-host.ts / WidgetHost.swift /
// WidgetHost.kt.
//
// Threading. A Qt-free library has no main thread, so the host owns a "host
// thread" that stands in for it: every renderer call, every diagnostics update
// and `onChange` / `onRenderOps` / `onAction` / `onStorageCommit` /
// `onGesture` / `onRequest` / `onCopyText` / `onWatchPublicData` (and the deprecated `onGestureClaim`
// and `onBridgeCall`) run there, in order. Engine
// callbacks hop from the engine thread; a layout query blocks the engine
// thread (≤ 3 s) until the renderer answers on the host thread. A Qt host
// would replace the thread with the UI thread (`crl::on_main`) one for one.
// `destroy()` is called from the owner's thread, never from the host thread.
#pragma once

#include "twidget/gesture_policy.h"
#include "twidget/renderer.h"
#include "twidget/sandbox_engine.h"
#include "twidget/trusted_validator.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace twidget {

/// The client's own `<button>` (docs/sdk-v1.md §2.4); every field optional, the template's defaults stand
/// for the rest. Passed to the renderer as given: the renderer core keeps only values that match their
/// grammar (`#rrggbb`, CSS lengths, a font shorthand with generic families only, opacities 0–1).
struct WidgetButtonTokens {
    std::optional<std::string> bg, text, radius, font, padding, height;
    std::optional<double> pressedOpacity, disabledOpacity;
};

/// The client's message text (docs/sdk-v1.md §2.4); every field optional, the reference set stands for the rest
/// (15, 1.35, "8px 12px"): the root's font size in CSS px (the chat's text-size setting), its unitless line height,
/// the padding between the bubble's edge and the widget's content. The renderer keeps values that match the grammar.
struct WidgetTextTokens {
    std::optional<double> fontSize, lineHeight;
    std::optional<std::string> padding;
};

/// The chat's theme for the bubble the widget sits in (docs/sdk-v1.md §2.3, §2.4, §4.3): the colour scheme
/// ("light" | "dark"; anything but "dark" is light), that bubble's palette under the 15 Mini Apps keys as `#rrggbb`
/// (others are dropped; an outgoing bubble's colours when the user sent or forwarded the widget), the button and
/// text tokens.
struct WidgetTheme {
    std::string colorScheme = "light";
    std::map<std::string, std::string> params;
    WidgetButtonTokens button;
    WidgetTextTokens text;
};

// WidgetStorage, WidgetAction and StorageArea, and WidgetData, WidgetViewer, WidgetContext, WidgetRequest,
// WidgetRequestAnswer, WidgetRequestFailure and WidgetRequestOutcome are in twidget/sdk_policy.h (the engine checks with them).

/// Limits for the widget's QuickJS runtime (see SandboxConfig); mirrors WidgetHost.Options on iOS/Android.
struct WidgetHostOptions {
    size_t memoryLimitBytes = size_t(8) << 20;
    double cpuLimitMs = 500;
    double cpuQuotaMs = 300;
    double cpuQuotaWindowMs = 1000;
    double cpuKillAfterMs = 0;
    /// The host's main-thread work for this widget per window (layout queries + 25 µs per op + 2 µs per canvas command).
    double mainThreadQuotaMs = 100;
    int maxTimers = 64;
    /// Initial visibility; change it later with `setVisible`.
    bool visible = true;
    /// Device pixel ratio the engine boots with; the renderer's `size` report overrides it.
    double devicePixelRatio = 1;
    /// "strict" (default; anything but "compat") or "compat": the gesture gate, the 32 KB text caps and the text budgets,
    /// and the validator's counted caps, past which the widget stops (`diagnostics().dead` with the cap's reason, such
    /// as `Ops limit exceeded: 12000 operations in one batch (max 10000)`; docs/decisions.md, "Strict limits from the start").
    std::string policy = "strict";
    /// Opt-in sandbox features of docs/protocol.md, such as "webAnimations"; the renderer must support them.
    std::vector<std::string> features;
    /// The client's UI language, BCP 47 (`navigator.language`, the Intl default); `en-US` when malformed.
    std::string locale = std::string(sdk::DEFAULT_LOCALE);
    /// What the client draws over the widget (docs/sdk-v1.md §2.17, §5.12): for a widget with no caption below it the band of
    /// the message time at the bottom and its corner; zero (the default) with a caption below it. Pass it here, before the first
    /// render, so the first height already has the band; later only with the bubble's layout (`setContentSafeArea`).
    WidgetContentSafeArea contentSafeArea;
    /// Web Storage of this message as the client keeps it (docs/sdk-v1.md §5.4); an area over the
    /// 4 KB quota drops the whole preload (diagnostic `storage-rejected`, the widget starts empty).
    WidgetStorage storage;
    /// The bot's `chatData` and `publicData` as the message has them now (docs/sdk-v1.md §2.13); later
    /// edits go through `setData`. A field over 1024 bytes, with a NUL or malformed is null for the widget (`data-rejected`).
    WidgetData data;
    /// The message's flags from the server (`isCopy`, `canQuery`, `backgroundQuery`), the chat type and the viewer
    /// (docs/sdk-v1.md §4.4); fixed for the host's life. Without `canQuery` every request is refused. `preview`: an
    /// inline result not sent yet (§2.16): no input (the renderer's events and gesture claims are dropped), only
    /// `ready` and `setResizeAnchor` (`preview-blocked` for the rest), no storage preload or commits.
    WidgetContext context;
};

struct WidgetDiagnostics {
    std::vector<std::pair<std::string, std::string>> logs;
    /// kind → (count, first details)
    std::map<std::string, std::pair<int, std::vector<std::string>>> diags;
    /// The first 1000 actions `onAction` got.
    std::vector<WidgetAction> actions;
    /// Storage commits `onStorageCommit` got (one per area).
    int storageCommits = 0;
    /// The first 1000 raw bridge calls (method, args JSON), as the deprecated `onBridgeCall` gets them.
    std::vector<std::pair<std::string, std::string>> bridge;
    SandboxStats stats;
    int nodes = 0;
    bool loaded = false;
    /// Wall clock from WidgetHost creation to the renderer's `ready` and to `loaded`.
    std::optional<double> rendererReadyMs;
    std::optional<double> loadedMs;
    /// `loaded`: the widget's scripts ran; `rendered`: the renderer applied every op sent up to that point.
    bool rendered = false;
    std::optional<double> renderedMs;
    /// Fatal: the runtime is gone (memory, CPU, or a counted cap of the validator under strict). The renderer keeps the
    /// last state (frozen).
    std::optional<std::string> dead;
    /// Last uncaught widget error (non-fatal), cleared once a later task completes without one.
    std::optional<std::string> lastError;
    /// Document height in CSS px as the renderer reports it (clamped to [50, 600]).
    double height = 0;
    /// The edge of the bubble that stays put on screen when `height` changes (`TelegramWidget.setResizeAnchor`,
    /// docs/sdk-v1.md §2.12, since v0.1.3): "top" (the default: the bubble grows and shrinks downward, the content
    /// below it moves) or "bottom" (it grows upward, its bottom stays, the content above it moves). The widget's latest
    /// call; it is in the state before the size report that follows the same task's ops. No callback of its own: a
    /// change goes through `onChange`, read it there with the height.
    std::string resizeAnchor = std::string(sdk::DEFAULT_RESIZE_ANCHOR);
    /// Whether the widget watches its shared `public_data` (round 7, `TelegramWidget.watchPublicData()` /
    /// `unwatchPublicData()`): its latest call, false until then. Poll a shared widget's `public_data` only while it is on
    /// screen and this is true; a change also reaches `onWatchPublicData`. Not reset when the widget dies.
    bool watchesPublicData = false;
    /// Wall clock to the first `size` report that follows validated ops.
    std::optional<double> firstContentMs;

    int countDiag(const std::string& kind) const { auto it = diags.find(kind); return it == diags.end() ? 0 : it->second.first; }
    bool hasLog(const std::string& text) const { for (auto& l : logs) if (l.second == text) return true; return false; }
};

/// Host side of user activation: one physical pointer press (pointerdown, mousedown, pointerup, mouseup, click, dblclick)
/// is one gesture; keys never are. A click without a click count (detail 0, what Enter or Space on a focused control
/// produces) counts only as the end of an open press. Twin of GestureCounter in UserActivation.kt and user-activation.ts.
class GestureCounter {
public:
    /// A pointer press or release, or a click with a click count (detail > 0).
    static bool isUserActivation(const Json& ev);
    /// The gesture number for this renderer event, 0 when it is not a user activation.
    int gestureFor(const Json& ev);
    /// A primary press began (the renderer's `gesture` claim): what follows is a new gesture (user-activation.ts).
    void pressBegan() { pressOpen_ = false; lastType_.clear(); }

private:
    int number_ = 0;
    bool pressOpen_ = false;
    std::string lastType_;
};

class WidgetHost final : public SandboxEngineDelegate {
public:
    /// Throws JSError if the validator bundle fails to load. Nothing runs until `start()`.
    WidgetHost(std::string html, WidgetTheme theme, double width, WidgetHostOptions options, std::shared_ptr<SandboxResources> resources,
               RendererFactory rendererFactory, std::shared_ptr<IntlProvider> intl = nullptr);
    /// The API before SDK v1: a colour scheme only (the scheme's default palette, no button tokens).
    [[deprecated("pass a WidgetTheme (SDK v1)")]]
    WidgetHost(std::string html, std::string theme, double width, WidgetHostOptions options, std::shared_ptr<SandboxResources> resources,
               RendererFactory rendererFactory, std::shared_ptr<IntlProvider> intl = nullptr)
        : WidgetHost(std::move(html), WidgetTheme{std::move(theme), {}, {}, {}}, width, std::move(options), std::move(resources), std::move(rendererFactory), std::move(intl)) {}
    ~WidgetHost();
    WidgetHost(const WidgetHost&) = delete;
    WidgetHost& operator=(const WidgetHost&) = delete;

    /// Loads the renderer; the engine boots once the renderer announced itself (`ready`).
    void start();
    /// The chat's theme changed: stylesheets are re-sanitised against the scheme, the renderer gets the
    /// scheme and the variables, the widget gets `colorScheme`, `themeParams` and `themeChanged` in one task.
    void setTheme(const WidgetTheme& theme);
    [[deprecated("pass a WidgetTheme (SDK v1)")]]
    void setTheme(const std::string& theme) { setTheme(WidgetTheme{theme, {}, {}, {}}); }
    /// Off-screen pause: no animation frames, timers at 1 Hz, `document.hidden` inside the widget.
    void setVisible(bool visible);
    /// The bot's data changed (an edit the client got from the server): the full state of both strings. The
    /// widget gets `chatDataChanged`, then `publicDataChanged`, for each that differs; a copy keeps no `chatData` (its
    /// publicData can change); a field over the limit is null (`data-rejected`).
    void setData(const WidgetData& data);
    /// What the client draws over the widget changed with the bubble's layout (docs/sdk-v1.md §2.17, §5.12): a caption
    /// added or removed by an edit, an "edited" mark that widens the time. The renderer gets the root variables and the
    /// widget `contentSafeAreaInset` and `contentSafeAreaChanged`, from this one call; never per frame.
    void setContentSafeArea(const WidgetContentSafeArea& area);
    /// A DOM event for the widget that did not come from the renderer (the harness's `--events`).
    void event(const std::string& json);
    /// Trusted diagnostic code evaluated in the widget context (tests / harness only).
    std::string probe(const std::string& code);
    /// Stops the engine and the renderer. Idempotent; not from the host thread. Storage commits already on
    /// their way reach `onStorageCommit` before it returns; no action and no storage commit comes after it.
    void destroy();

    /// The renderer the factory built; its own API (touches, paints) is the owner's business.
    Renderer& renderer() { return *renderer_; }
    /// Snapshot of the diagnostics (any thread).
    WidgetDiagnostics diagnostics() const;
    /// Runs `fn` on the host thread and waits for it (inline when already there).
    void onHostThread(const std::function<void()>& fn) { hostThread_.sync(fn); }

    bool waitLoaded(double timeoutSeconds = 20);
    /// True once the post-load ack query came back from the renderer (or the widget died).
    bool waitRendered(double timeoutSeconds = 5);
    /// Polls `pred` on a diagnostics snapshot until true or timeout.
    bool waitUntil(const std::function<bool(const WidgetDiagnostics&)>& pred, double timeoutSeconds = 3);

    // Observers, all on the host thread.
    std::function<void(const WidgetDiagnostics&)> onChange;
    /// Every validated batch on its way to the renderer (what the harness scans for leaks).
    std::function<void(const std::string& renderOpsJson)> onRenderOps;
    /// What the widget asked the client to do, checked (URL rules, user activation, the haptic rate limit;
    /// docs/sdk-v1.md §3.2, §5.2, §5.3). At most one link action per pointer press.
    std::function<void(const WidgetAction& action)> onAction;
    /// The new full content of a Web Storage area, "local" or "session", at the end of each task that changed it
    /// (docs/sdk-v1.md §5.4). Within the quota; a lone surrogate of the widget's string arrives in its WTF-8 form.
    std::function<void(const std::string& area, const std::map<std::string, std::string>& values)> onStorageCommit;
    /// On every primary pointerdown (the renderer's `gesture` message, which also starts the haptic gate's touch; its
    /// `release` message ends it, v0.2.2), what of this press the widget claims with `touch-action` (docs/sdk-v1.md §5.5):
    /// horizontal drags (`x`) and holds (`hold`: no message menu, the widget gets `contextmenu`); taps and double taps
    /// are always the widget's, vertical drags always the chat's (`y` is always false since v0.1.3, whatever the
    /// renderer sends). Feed it to the GestureArbiter of the client's recognizers over this widget
    /// (twidget/gesture_policy.h). It arrives a few ms after the touch starts. A renderer that reports only `wants`
    /// (the Blitz and litehtml prototypes) claims nothing.
    std::function<void(const WidgetGestureClaim& claim)> onGesture;
    /// A request the widget sends to the bot (`TelegramWidget.request`, docs/sdk-v1.md §2.14), checked: the
    /// data (1–64 bytes), the message's `WidgetContext::canQuery`, a press (one request per press, shared with the links
    /// and copyText) or, without one, `backgroundQuery` (one when the widget appears on screen, then one per 5 s while
    /// visible; one made off screen waits for `setVisible(true)`), one in flight. Send it like a callback button's press and call `respond` once, from any thread, with the
    /// server's answer (`result`) or a failure (`Timeout`, `RateLimited`, `Failed`); later calls, and calls after
    /// `destroy()`, are ignored. Until then the
    /// widget's next request is BUSY. Read on the host thread when a request is delivered: unset then, the widget gets
    /// `NOT_ALLOWED` (reason `unsupported`).
    std::function<void(const WidgetRequest& request, std::function<void(const WidgetRequestOutcome&)> respond)> onRequest;
    /// A wallet call (`TelegramWidget.wallet`, docs/sdk-v1.md §2.18; checked: the transaction or the payload, a press or
    /// the grant, one call in flight with the requests). Only with `WidgetContext::wallet`. Show the client's own wallet
    /// window with the bot of the message; on confirmation send the transaction (or sign for `bot<bot_id>`), send the
    /// outcome to the bot as a query with `call.data` and call `respond` once, from any thread, with the bot's answer or
    /// a failure (`Rejected`: the user declined, `BadRequest`: the wallet refused it, or a request's). Read on the host
    /// thread at delivery: unset then, the widget gets `NOT_ALLOWED` (reason `unsupported`).
    std::function<void(const WalletCall& call, std::function<void(const WidgetRequestOutcome&)> respond)> onWallet;
    /// Copy this text to the clipboard and show a toast that says what was copied (`TelegramWidget.copyText`):
    /// 1–256 UTF-16 code units, well-formed, at most one privileged call per pointer press. Not an action.
    std::function<void(const std::string& text)> onCopyText;
    /// Whether the widget watches its shared `public_data` changed (round 7): poll it only while true and the widget is on
    /// screen. Called only on a change, after `diagnostics().watchesPublicData` took the new value. Not an action.
    std::function<void(bool on)> onWatchPublicData;
    /// Deprecated since v0.1.2 (`onGesture`): the claim's `x` and `y` (always false), right after `onGesture`.
    [[deprecated("use onGesture (v0.1.2)")]]
    std::function<void(bool x, bool y)> onGestureClaim;
    /// Deprecated since SDK v1 (`onAction`): the raw method and arguments JSON of each call `onAction` gets, right after it.
    [[deprecated("use onAction (SDK v1)")]]
    std::function<void(const std::string& method, const std::string& argsJson)> onBridgeCall;
    /// Non-fatal uncaught exception in widget code (also in `diagnostics().logs`).
    std::function<void(const std::string& text)> onError;
    /// Badge text shown by the renderer over a dead widget (user-facing, no error details).
    std::string stoppedLabel = "Widget stopped";

    // SandboxEngineDelegate (engine thread)
    void onOps(SandboxEngine&, const std::string& json) override;
    QueryAnswer onQuery(SandboxEngine&, const std::string& json) override;
    void onListen(SandboxEngine&, const std::string& json) override;
    void onPreventDefault(SandboxEngine&, const std::string& json) override;
    void onAnim(SandboxEngine&, const std::string& json) override;
    void onRequestFrame(SandboxEngine&) override;
    void onLog(SandboxEngine&, const std::string& level, const std::string& text) override;
    void onDiag(SandboxEngine&, const std::string& kind, const std::string& detail) override;
    void onBridgeAction(SandboxEngine&, const WidgetAction& action) override;
    void onResizeAnchor(SandboxEngine&, const std::string& edge) override;
    void onPublicDataWatch(SandboxEngine&, bool on) override;
    void onStorage(SandboxEngine&, const std::string& area, const StorageArea& values) override;
    void onBridgeRequest(SandboxEngine&, const WidgetRequest& request, WidgetRequestResponder respond) override;
    void onBridgeWallet(SandboxEngine&, const WalletCall& call, WidgetRequestResponder respond) override;
    void onBridgeCopyText(SandboxEngine&, const std::string& text) override;
    void onBridge(SandboxEngine&, const std::string& method, const std::string& argsJson) override;
    void onLoaded(SandboxEngine&) override;
    void onStats(SandboxEngine&, const SandboxStats& stats) override;
    void onDied(SandboxEngine&, const std::string& reason) override;

    /// Renderer events that count as a user gesture: clicks and presses of Enter or Space (not a repeat, not a Command shortcut).
    static bool isUserActivation(const Json& ev) { return GestureCounter::isUserActivation(ev); }

private:
    /// Fed only the events the renderer marks `trusted`.
    GestureCounter gestures_;
    struct PendingQuery {
        int id = 0;
        bool done = false;
        std::string json = "null";
        std::optional<std::vector<uint8_t>> binary;
    };

    void onRenderer(const std::string& json);
    void handleRendererMessage(const Json& msg);
    void toRenderer(const std::string& json);
    /// `{"type":"theme","theme":{"colorScheme","params","button"}}` of the current theme (docs/sdk-v1.md §3.4).
    std::string themeMessage() const;
    /// `{"type":"safeArea","inset":{…}}` (docs/sdk-v1.md §3.4).
    static std::string safeAreaMessage(const WidgetContentSafeArea& area);
    void resolveQuery(const std::string& json, std::optional<std::vector<uint8_t>> binary);
    void diag(const std::string& kind, const std::string& detail);
    void changed();
    double elapsedMs() const;
    /// Runs `fn` on the host thread (inline when already there).
    void host(std::function<void()> fn);

    /// qid of the host's own post-load ack query; engine queries count up from 1.
    static constexpr int kRenderAckQid = -1;

    JSThread hostThread_;
    std::string html_;
    /// The colour scheme ("light" | "dark") and the whole theme with its palette checked (host thread after start).
    std::string theme_;
    WidgetTheme widgetTheme_;
    WidgetHostOptions options_;
    SandboxConfig config_;
    std::shared_ptr<SandboxResources> resources_;
    TrustedValidator validator_;
    std::unique_ptr<Renderer> renderer_;
    SandboxEngine engine_;

    mutable std::mutex mutex_;              // diagnostics_, waiters
    std::condition_variable cv_;
    WidgetDiagnostics diagnostics_;

    // Host-thread state.
    bool rendererReady_ = false;
    bool engineStarted_ = false;
    bool started_ = false;
    /// Set at the end of destroy(): storage commits queued before it still reach `onStorageCommit` (the
    /// widget's last state is worth keeping); none reaches the owner once destroy() has returned.
    bool callbacksClosed_ = false;
    std::vector<std::string> pendingToRenderer_;
    double viewportW_, viewportH_, viewportDpr_;
    int qid_ = 0;
    int opsSent_ = 0;
    int tasksAtError_ = 0;

    std::atomic<bool> destroyed_{false};
    std::mutex queryMutex_;
    std::condition_variable queryCv_;
    std::shared_ptr<PendingQuery> pendingQuery_;
    const std::chrono::steady_clock::time_point createdAt_ = std::chrono::steady_clock::now();
};

} // namespace twidget

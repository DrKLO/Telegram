// A fake host for the harness and the tests: records everything the engine
// emits, runs every ops batch through the TrustedValidator (so what it keeps is
// exactly what a renderer would receive), stops the widget as WidgetHost does
// when the validator's counted cap stops it (strict), and answers layout
// queries with the same canned values as scripts/sandbox-node.ts.
#pragma once

#include "twidget/json.h"
#include "twidget/sandbox_engine.h"
#include "twidget/trusted_validator.h"

#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

namespace twidget {

class RecordingHost : public SandboxEngineDelegate {
public:
    /// `policy` is the engine's (SandboxConfig::policy): anything but "compat" validates strict, as WidgetHost does.
    RecordingHost(const std::string& theme, std::shared_ptr<SandboxResources> resources, bool verbose = false, const std::string& policy = "strict")
        : verbose_(verbose), validator_(theme, std::move(resources), [this](const std::string& k, const std::string& d) { diag("validator:" + k, d); }) {
        if (isStrictPolicy(policy)) validator_.setPolicy("strict");
    }

    // Recorded state (read under lock()).
    std::vector<std::string> ops;        // raw batches from the sandbox (untrusted)
    std::vector<std::string> renderOps;  // validated RenderOps per batch (JSON arrays)
    std::vector<std::pair<std::string, std::string>> logs;
    std::vector<std::pair<std::string, std::string>> diags;
    std::vector<std::string> queries;
    /// Raw bridge calls the glue let through (the deprecated onBridge), and the checked actions (onBridgeAction).
    std::vector<std::pair<std::string, std::string>> bridge;
    std::vector<WidgetAction> actions;
    /// Each `setResizeAnchor` edge the glue accepted, in order (the shim sends only changes; `top` until the first).
    std::vector<std::string> resizeAnchors;
    /// Each `watchPublicData` value the glue accepted, in order (the shim sends only changes; false until the first).
    std::vector<bool> publicDataWatches;
    /// Storage commits the glue accepted, one entry per area, in order.
    std::vector<std::pair<std::string, StorageArea>> storageCommits;
    /// The bot's requests: those that passed the glue's checks and gate (`onBridgeRequest`), and the texts `copyText` let
    /// through (`onBridgeCopyText`), in order.
    std::vector<WidgetRequest> requests;
    std::vector<std::string> copiedTexts;
    /// The client's side of a request, on the engine thread, outside the lock: answer through `respond` now or keep it
    /// for later. Unset (the default): the client handles no requests, every one is refused (`unsupported`).
    std::function<void(const WidgetRequest&, std::function<void(const WidgetRequestOutcome&)>)> onRequest;
    /// The wallet calls that passed the glue's checks and gate (`onBridgeWallet`), in order, and the client's side of
    /// them (unset: every one is refused, `unsupported`).
    std::vector<WalletCall> walletCalls;
    std::function<void(const WalletCall&, std::function<void(const WidgetRequestOutcome&)>)> onWallet;
    std::optional<std::string> died;
    int frameRequests = 0;
    bool loaded = false;
    SandboxStats stats;
    /// Custom query answers (clean query → answer JSON); nullopt falls back to the canned ones.
    std::function<std::optional<std::string>(const Json&)> answer;

    std::unique_lock<std::mutex> lock() { return std::unique_lock<std::mutex>(mutex_); }
    TrustedValidator& validator() { return validator_; }

    /// True once `loaded` or `died` was reported.
    bool waitLoaded(double timeoutSeconds = 20) {
        std::unique_lock<std::mutex> l(mutex_);
        return cv_.wait_for(l, std::chrono::duration<double>(timeoutSeconds), [&] { return loaded || died.has_value(); });
    }

    /// Polls `pred` (called under the lock) until true or timeout.
    bool waitUntil(const std::function<bool()>& pred, double timeoutSeconds = 3) {
        auto deadline = std::chrono::steady_clock::now() + std::chrono::duration_cast<std::chrono::steady_clock::duration>(std::chrono::duration<double>(timeoutSeconds));
        for (;;) {
            { std::lock_guard<std::mutex> l(mutex_); if (pred()) return true; }
            if (std::chrono::steady_clock::now() > deadline) return false;
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }

    bool hasLog(const std::string& text) const { for (auto& l : logs) if (l.second == text) return true; return false; }
    bool hasLogPrefix(const std::string& prefix) const { for (auto& l : logs) if (l.second.rfind(prefix, 0) == 0) return true; return false; }
    int countLogPrefix(const std::string& prefix) const { int n = 0; for (auto& l : logs) if (l.second.rfind(prefix, 0) == 0) n++; return n; }
    int countDiag(const std::string& kind) const { int n = 0; for (auto& d : diags) if (d.first == kind) n++; return n; }
    bool hasDiag(const std::string& kind) const { return countDiag(kind) > 0; }
    std::string allOps() const { std::string s; for (auto& o : ops) s += o; return s; }
    std::string allRenderOps() const { std::string s; for (auto& o : renderOps) s += o; return s; }

    // SandboxEngineDelegate (engine thread)
    void onOps(SandboxEngine& engine, const std::string& json) override {
        std::string clean = validator_.processOps(json);
        {
            std::lock_guard<std::mutex> l(mutex_);
            ops.push_back(json);
            renderOps.push_back(clean);
            if (verbose_) fprintf(stderr, "ops → %s\n", clean.size() > 2000 ? (clean.substr(0, 2000) + "…").c_str() : clean.c_str());
        }
        // A counted cap passed under strict: the widget dies with its reason (onDied), as under WidgetHost.
        if (std::optional<std::string> reason = validator_.stopped()) engine.stop(*reason);
    }
    QueryAnswer onQuery(SandboxEngine&, const std::string& json) override {
        { std::lock_guard<std::mutex> l(mutex_); queries.push_back(json); }
        auto clean = validator_.validateQuery(json);
        if (!clean) return {"{\"error\":\"invalid query\"}", std::nullopt};
        auto q = Json::parse(*clean);
        if (!q) return {"{\"error\":\"invalid query\"}", std::nullopt};
        std::optional<std::string> custom = answer ? answer(*q) : std::nullopt;
        std::string out = custom ? *custom : cannedAnswer(*q);
        if (verbose_) fprintf(stderr, "query %s → %s\n", clean->c_str(), out.c_str());
        return {out, std::nullopt};
    }
    void onListen(SandboxEngine&, const std::string& json) override { (void)validator_.validateListen(json); }
    void onPreventDefault(SandboxEngine&, const std::string& json) override { (void)validator_.validatePreventDefault(json); }
    void onRequestFrame(SandboxEngine&) override { std::lock_guard<std::mutex> l(mutex_); frameRequests++; }
    void onLog(SandboxEngine&, const std::string& level, const std::string& text) override {
        std::lock_guard<std::mutex> l(mutex_);
        logs.emplace_back(level, text);
        if (verbose_) fprintf(stderr, "[%s] %s\n", level.c_str(), text.c_str());
    }
    void onDiag(SandboxEngine&, const std::string& kind, const std::string& detail) override { diag(kind, detail); }
    void onBridge(SandboxEngine&, const std::string& method, const std::string& argsJson) override {
        std::lock_guard<std::mutex> l(mutex_);
        bridge.emplace_back(method, argsJson);
        if (verbose_) fprintf(stderr, "[bridge] %s %s\n", method.c_str(), argsJson.c_str());
    }
    void onBridgeAction(SandboxEngine&, const WidgetAction& action) override {
        std::lock_guard<std::mutex> l(mutex_);
        actions.push_back(action);
        if (verbose_) fprintf(stderr, "[action] %s\n", action.toJson().dump().c_str());
    }
    void onResizeAnchor(SandboxEngine&, const std::string& edge) override {
        std::lock_guard<std::mutex> l(mutex_);
        resizeAnchors.push_back(edge);
        if (verbose_) fprintf(stderr, "[resize-anchor] %s\n", edge.c_str());
    }
    void onPublicDataWatch(SandboxEngine&, bool on) override {
        std::lock_guard<std::mutex> l(mutex_);
        publicDataWatches.push_back(on);
        if (verbose_) fprintf(stderr, "[watch-public-data] %s\n", on ? "true" : "false");
    }
    void onStorage(SandboxEngine&, const std::string& area, const StorageArea& values) override {
        std::lock_guard<std::mutex> l(mutex_);
        storageCommits.emplace_back(area, values);
        if (verbose_) fprintf(stderr, "[storage] %s, %zu keys, %zu bytes\n", area.c_str(), values.size(), sdk::storageBytes(values));
    }
    void onBridgeRequest(SandboxEngine&, const WidgetRequest& request, WidgetRequestResponder respond) override {
        {
            std::lock_guard<std::mutex> l(mutex_);
            requests.push_back(request);
            if (verbose_) fprintf(stderr, "[request] %s\n", Json::quote(request.data).c_str());
        }
        if (onRequest) onRequest(request, respond);
        else respond.unsupported();
    }
    void onBridgeWallet(SandboxEngine&, const WalletCall& call, WidgetRequestResponder respond) override {
        {
            std::lock_guard<std::mutex> l(mutex_);
            walletCalls.push_back(call);
            if (verbose_) fprintf(stderr, "[wallet] %s\n", call.toJson().dump().c_str());
        }
        if (onWallet) onWallet(call, respond);
        else respond.unsupported();
    }
    void onBridgeCopyText(SandboxEngine&, const std::string& text) override {
        std::lock_guard<std::mutex> l(mutex_);
        copiedTexts.push_back(text);
        if (verbose_) fprintf(stderr, "[copyText] %s\n", Json::quote(text).c_str());
    }
    void onLoaded(SandboxEngine&) override { { std::lock_guard<std::mutex> l(mutex_); loaded = true; } cv_.notify_all(); }
    void onStats(SandboxEngine&, const SandboxStats& s) override { std::lock_guard<std::mutex> l(mutex_); stats = s; }
    void onDied(SandboxEngine&, const std::string& reason) override {
        { std::lock_guard<std::mutex> l(mutex_); died = reason; if (verbose_) fprintf(stderr, "[dead] %s\n", reason.c_str()); }
        cv_.notify_all();
    }

    static std::string cannedAnswer(const Json& q) {
        const std::string& kind = q["q"].asString();
        if (kind == "rect") return "{\"x\":0,\"y\":0,\"width\":100,\"height\":20,\"top\":0,\"left\":0,\"right\":100,\"bottom\":20}";
        if (kind == "rects") return "[{\"x\":0,\"y\":0,\"width\":100,\"height\":20,\"top\":0,\"left\":0,\"right\":100,\"bottom\":20}]";
        if (kind == "box") return "{\"offsetWidth\":100,\"offsetHeight\":20,\"offsetTop\":0,\"offsetLeft\":0,\"offsetParent\":0,\"clientWidth\":100,\"clientHeight\":20,\"clientTop\":0,\"clientLeft\":0,\"scrollWidth\":100,\"scrollHeight\":20,\"scrollTop\":0,\"scrollLeft\":0}";
        if (kind == "cs") return q["prop"].isString() ? "\"\"" : "{\"display\":\"block\",\"width\":\"100px\",\"height\":\"20px\",\"font-size\":\"16px\"}";
        if (kind == "svg") {
            const std::string& m = q["method"].asString();
            if (m == "getBBox") return "{\"x\":0,\"y\":0,\"width\":50,\"height\":12}";
            if (m.find("CTM") != std::string::npos) return "{\"a\":1,\"b\":0,\"c\":0,\"d\":1,\"e\":0,\"f\":0}";
            if (m == "getPointAtLength") return "{\"x\":0,\"y\":0}";
            return "42";
        }
        if (kind == "measureText") return "{\"width\":" + std::to_string(q["text"].asString().size() * 6) + ",\"actualBoundingBoxAscent\":8,\"actualBoundingBoxDescent\":2}";
        if (kind == "matchMedia") return "{\"matches\":false}";
        if (kind == "viewport") return "{\"innerWidth\":400,\"innerHeight\":300,\"dpr\":1}";
        if (kind == "validity") return "{\"valid\":true,\"message\":\"\"}";
        if (kind == "elementFromPoint") return "0";
        if (kind == "canvasCall") return q["method"].asString() == "toDataURL" ? "\"data:,\"" : "false";
        if (kind == "imageData") return "{\"width\":0,\"height\":0}";
        return "null";
    }

private:
    void diag(const std::string& kind, const std::string& detail) {
        std::lock_guard<std::mutex> l(mutex_);
        diags.emplace_back(kind, detail);
        if (verbose_) fprintf(stderr, "[diag] %s — %s\n", kind.c_str(), detail.c_str());
    }

    bool verbose_;
    std::mutex mutex_;
    std::condition_variable cv_;
    TrustedValidator validator_;
};

} // namespace twidget

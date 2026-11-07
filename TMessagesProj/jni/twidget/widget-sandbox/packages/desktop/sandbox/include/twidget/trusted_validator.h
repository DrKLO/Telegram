// The validator / sanitizer (src/host/validator.ts) compiled into
// validator-bundle.js and run in a TRUSTED QuickJS context — a separate
// runtime that never executes widget code. One instance per widget. Calls are
// synchronous from the caller's point of view but execute on the validator's
// own JSThread (css-tree needs the 8 MB stack). Input and output are JSON.
//
// Diagnostic `limit` is fatal (validator-bundle.ts): a counted cap was passed
// under strict, the batch came back empty and every later one does too. The
// owner stops the widget with `stopped()` as the reason, as it does for memory
// or CPU (WidgetHost::onOps → SandboxEngine::stop).
#pragma once

#include "twidget/js_thread.h"
#include "twidget/quickjs_context.h"
#include "twidget/resources.h"

#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

namespace twidget {

class TrustedValidator {
public:
    using DiagSink = std::function<void(const std::string& kind, const std::string& detail)>;

    /// Throws JSError if the bundle fails to load.
    TrustedValidator(const std::string& theme, std::shared_ptr<SandboxResources> resources, DiagSink onDiag);
    ~TrustedValidator();

    /// Untrusted ops batch (JSON) → validated RenderOps (JSON array; "[]" if nothing survived).
    std::string processOps(const std::string& json);
    /// Untrusted query → clean query JSON, or nullopt if rejected.
    std::optional<std::string> validateQuery(const std::string& json);
    std::string validateListen(const std::string& json);
    std::string validatePreventDefault(const std::string& json);
    void setPolicy(const std::string& policy);
    std::string validateAnim(const std::string& json);
    /// RenderOps re-styling the document for the new theme.
    std::string setTheme(const std::string& theme);
    std::string rewriteMediaQuery(const std::string& query);
    int nodeCount();
    /// The loading tasks are over (their ops are in): text changes are rate-limited from now on (validator.ts `markLoaded`).
    void markLoaded();
    /// Set once a counted cap was passed under strict (the first `limit` diagnostic's detail, e.g. `Text limit exceeded:
    /// a text value of 40000 characters (max 32768)`): the reason the owner stops the widget with. Read it after the call
    /// that passed the cap returns, never from inside the diag sink.
    std::optional<std::string> stopped() const;

    /// Called on the validator's thread with the CPU time (ms, its thread's clock) of each call made for the widget's
    /// messages: `processOps`, `validateQuery`, `rewriteMediaQuery`, `validateListen`, `validatePreventDefault`,
    /// `validateAnim`. The owner records it against the widget's JS quota (WidgetHost → SandboxEngine::chargeValidator;
    /// docs/decisions.md, "The validator's time counts"). Empty: nothing is measured.
    using CpuSink = std::function<void(double ms)>;
    void setCpuSink(CpuSink sink);

private:
    std::string str(const char* name, const std::vector<std::string>& args);
    /// `str` measured for the CPU sink.
    std::string metered(const char* name, const std::vector<std::string>& args);

    JSThread thread_;
    std::unique_ptr<QuickJSContext> js_;
    DiagSink onDiag_;
    mutable std::mutex stoppedMutex_;
    std::optional<std::string> stopped_;
    /// The validator's thread only.
    CpuSink cpuSink_;
};

} // namespace twidget

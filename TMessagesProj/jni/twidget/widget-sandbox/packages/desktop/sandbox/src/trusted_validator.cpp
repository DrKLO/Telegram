#include "twidget/trusted_validator.h"

#include "twidget/bundle_stamp.h"

namespace twidget {

TrustedValidator::TrustedValidator(const std::string& theme, std::shared_ptr<SandboxResources> resources, DiagSink onDiag)
    : thread_("widget.validator"), onDiag_(std::move(onDiag)) {
    thread_.sync([&] {
        js_ = std::make_unique<QuickJSContext>(size_t(256) << 20, JSThread::kJSStackLimit);
        js_->registerFunction("__diag", [this](const std::vector<std::string>& args) {
            std::string kind = args.empty() ? "" : args[0], detail = args.size() > 1 ? args[1] : "";
            if (kind == "limit") {
                // Fatal: the owner reads it once the call returns and stops the widget (the bundle reports it once).
                std::lock_guard<std::mutex> l(stoppedMutex_);
                if (!stopped_) stopped_ = detail;
            }
            if (onDiag_) onDiag_(kind, detail);
            return HostReturn::undefined();
        });
        // Bundles of another release are refused before anything runs (bundle_stamp.h): no bundle, every call
        // answers empty (`validator-error`).
        if (auto refusal = BundleStamp::refusal(*resources)) {
            if (onDiag_) onDiag_("bundle-mismatch", *refusal);
            return;
        }
        // Precompiled when shipped and matching the source, else the source (same rule as the engine).
        bool fromBytecode = false;
        if (!resources->validatorBytecode.empty()) {
            try { js_->evaluateBytecode(resources->validatorBytecode, resources->validatorBundle); fromBytecode = true; }
            catch (const JSError& e) { if (onDiag_) onDiag_("bytecode", std::string("validator-bundle.js: falling back to source — ") + e.what()); }
        }
        if (!fromBytecode) js_->evaluate(resources->validatorBundle, "validator-bundle.js");
        // Flatten the API into globals so QuickJSContext::call can reach it.
        js_->evaluate(
            "globalThis.__v_processOps = (j) => __twValidator.processOps(j);"
            "globalThis.__v_validateQuery = (j) => __twValidator.validateQuery(j);"
            "globalThis.__v_validateListen = (j) => __twValidator.validateListen(j);"
            "globalThis.__v_validatePreventDefault = (j) => __twValidator.validatePreventDefault(j);"
            "globalThis.__v_validateAnim = (j) => __twValidator.validateAnim(j);"
            "globalThis.__v_setTheme = (t) => __twValidator.setTheme(t);"
            "globalThis.__v_rewriteMediaQuery = (q) => __twValidator.rewriteMediaQuery(q);"
            "globalThis.__v_nodeCount = () => __twValidator.nodeCount();"
            "globalThis.__v_markLoaded = () => { __twValidator.markLoaded(); return ''; };"
            "globalThis.__v_create = (t) => { __twValidator.create(t); return ''; };"
            "globalThis.__v_setPolicy = (p) => __twValidator.setPolicy ? __twValidator.setPolicy(p) : '';",
            "validator-glue.js");
        js_->call("__v_create", {theme});
    });
}

TrustedValidator::~TrustedValidator() {
    thread_.sync([&] { js_.reset(); });
    thread_.join();
}

std::string TrustedValidator::str(const char* name, const std::vector<std::string>& args) {
    return thread_.sync([&]() -> std::string {
        try {
            return js_->call(name, args).string.value_or("");
        } catch (const JSError& e) {
            if (onDiag_) onDiag_("validator-error", std::string(name) + ": " + e.what());
            return "";
        }
    });
}

void TrustedValidator::setCpuSink(CpuSink sink) {
    thread_.sync([&] { cpuSink_ = std::move(sink); });
}

std::string TrustedValidator::metered(const char* name, const std::vector<std::string>& args) {
    return thread_.sync([&]() -> std::string {
        if (!cpuSink_) return str(name, args);
        double t0 = QuickJSContext::threadCpuTime();
        std::string out = str(name, args);
        cpuSink_((QuickJSContext::threadCpuTime() - t0) * 1000);
        return out;
    });
}

std::string TrustedValidator::processOps(const std::string& json) {
    std::string out = metered("__v_processOps", {json});
    return out.empty() ? "[]" : out;
}

std::optional<std::string> TrustedValidator::validateQuery(const std::string& json) {
    std::string out = metered("__v_validateQuery", {json});
    if (out.empty()) return std::nullopt;
    return out;
}

std::string TrustedValidator::validateListen(const std::string& json) { auto s = metered("__v_validateListen", {json}); return s.empty() ? "[]" : s; }
void TrustedValidator::setPolicy(const std::string& policy) { str("__v_setPolicy", {policy}); }
void TrustedValidator::markLoaded() { str("__v_markLoaded", {}); }
std::string TrustedValidator::validatePreventDefault(const std::string& json) { auto s = metered("__v_validatePreventDefault", {json}); return s.empty() ? "[]" : s; }
std::string TrustedValidator::validateAnim(const std::string& json) { auto s = metered("__v_validateAnim", {json}); return s.empty() ? "[]" : s; }
std::string TrustedValidator::setTheme(const std::string& theme) { auto s = str("__v_setTheme", {theme}); return s.empty() ? "[]" : s; }
std::string TrustedValidator::rewriteMediaQuery(const std::string& query) { return metered("__v_rewriteMediaQuery", {query}); }

std::optional<std::string> TrustedValidator::stopped() const {
    std::lock_guard<std::mutex> l(stoppedMutex_);
    return stopped_;
}

int TrustedValidator::nodeCount() {
    return thread_.sync([&]() -> int {
        try { return int(js_->call("__v_nodeCount").number.value_or(0)); } catch (const JSError&) { return 0; }
    });
}

} // namespace twidget

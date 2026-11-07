// Thin C++ wrapper over quickjs-ng through the same qjs_shim.c that iOS and
// Android use (quickjs.h's JSValue macros are compound literals, not C++).
// One runtime, one context, string-based host functions. All methods must be
// called from the JSThread that owns the context.
#pragma once

#include <cstdint>
#include <functional>
#include <limits>
#include <optional>
#include <stdexcept>
#include <string>
#include <vector>

struct JSRuntime;
struct JSContext;

namespace twidget {

class JSError : public std::runtime_error {
public:
    using std::runtime_error::runtime_error;
};

/// Value returned by a host function into the sandbox.
struct HostReturn {
    enum class Kind { Undefined, Null, String, Number, ArrayBuffer };
    Kind kind = Kind::Undefined;
    std::string str;
    double num = 0;
    std::vector<uint8_t> bytes;

    static HostReturn undefined() { return {}; }
    static HostReturn null() { HostReturn r; r.kind = Kind::Null; return r; }
    static HostReturn string(std::string s) { HostReturn r; r.kind = Kind::String; r.str = std::move(s); return r; }
    static HostReturn number(double d) { HostReturn r; r.kind = Kind::Number; r.num = d; return r; }
    static HostReturn arrayBuffer(std::vector<uint8_t> b) { HostReturn r; r.kind = Kind::ArrayBuffer; r.bytes = std::move(b); return r; }
};

/// Result of calling a sandbox function from the host: string, number or nothing.
struct JSResult {
    std::optional<std::string> string;
    std::optional<double> number;
};

class QuickJSContext {
public:
    using HostFunction = std::function<HostReturn(const std::vector<std::string>&)>;

    /// `maxStackBytes` must stay below the real stack of the thread that enters
    /// JS (JSThread: 8 MB). QuickJS only *detects* overflow against this limit.
    explicit QuickJSContext(size_t memoryLimitBytes, size_t maxStackBytes = size_t(384) << 10);
    ~QuickJSContext();
    QuickJSContext(const QuickJSContext&) = delete;
    QuickJSContext& operator=(const QuickJSContext&) = delete;

    /// Interrupt deadline in `now()` seconds; +inf disables it.
    double deadline = std::numeric_limits<double>::infinity();
    /// Set by the interrupt handler when the deadline fired; reset by the caller.
    bool interrupted = false;

    /// Interrupt deadline in `threadCpuTime()` seconds of the thread running JS; +inf disables it.
    double cpuDeadline = std::numeric_limits<double>::infinity();

    /// Monotonic seconds (steady clock), the clock `deadline` is measured on.
    static double now();
    /// CPU seconds consumed by the calling thread; unaffected by how busy the machine is.
    static double threadCpuTime();

    size_t memoryUsed() const;

    /// Exposes `name(...)` to the sandbox. Arguments arrive as strings (numbers are formatted).
    void registerFunction(const std::string& name, HostFunction fn);

    JSResult evaluate(const std::string& code, const std::string& filename, bool asModule = false);
    /// Runs one of our own precompiled bundles (`.qbc`, see qjs_shim.h), checked against
    /// the source it was built from; throws JSError on any mismatch so the caller can
    /// fall back to `evaluate`. Never for widget code: bytecode is not verified.
    JSResult evaluateBytecode(const std::vector<uint8_t>& blob, const std::string& source);
    /// Compiles `code` into the same container the build ships (tests).
    std::vector<uint8_t> compileBytecode(const std::string& code, const std::string& filename);
    /// Calls the global function `name` with string arguments.
    JSResult call(const std::string& name, const std::vector<std::string>& args = {});

    /// Keeps a private reference to the global function `name`; `call(name)` uses it from then on, even after the global is deleted.
    bool captureGlobalFunction(const std::string& name);

    bool hasPendingJob() const;
    /// Runs queued promise jobs. Errors thrown by jobs go to `onError` and do not stop the loop.
    void executePendingJobs(const std::function<void(const JSError&)>& onError);

private:
    void enter();
    JSError takeException();

    JSRuntime* runtime_;
    JSContext* context_;
    std::vector<HostFunction> functions_;
    struct Captured;
    std::vector<Captured*> captured_;
    friend struct QuickJSTrampoline;
};

} // namespace twidget

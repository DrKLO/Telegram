#include "twidget/quickjs_context.h"

#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif

#include <chrono>
#include <cmath>
#include <cstdlib>
#include <cstring>

extern "C" {
#include "qjs_shim.h"
}

namespace twidget {

struct QuickJSTrampoline {
    static JSValue call(JSContext* ctx, JSValue /*this*/, int argc, JSValue* argv, int magic) {
        auto* self = static_cast<QuickJSContext*>(JS_GetContextOpaque(ctx));
        if (!self || magic < 0 || size_t(magic) >= self->functions_.size()) return qjs_undefined();
        std::vector<std::string> args;
        args.reserve(size_t(argc));
        for (int i = 0; i < argc; i++) {
            JSValue v = argv[i];
            if (qjs_is_string(v)) {
                const char* c = qjs_to_cstring(ctx, v);
                args.emplace_back(c ? c : "");
                if (c) qjs_free_cstring(ctx, c);
            } else if (qjs_is_number(v)) {
                double d = qjs_to_float64(ctx, v);
                char buf[64];
                if (d == std::floor(d) && std::fabs(d) < 1e15) snprintf(buf, sizeof buf, "%lld", (long long)d);
                else snprintf(buf, sizeof buf, "%.17g", d);
                args.emplace_back(buf);
            } else {
                args.emplace_back();
            }
        }
        HostReturn r = self->functions_[size_t(magic)](args);
        switch (r.kind) {
        case HostReturn::Kind::Undefined: return qjs_undefined();
        case HostReturn::Kind::Null: return qjs_null();
        case HostReturn::Kind::String: return qjs_new_string_len(ctx, r.str.data(), r.str.size());
        case HostReturn::Kind::Number: return qjs_new_float64(ctx, r.num);
        case HostReturn::Kind::ArrayBuffer: return qjs_new_array_buffer_copy(ctx, r.bytes.data(), r.bytes.size());
        }
        return qjs_undefined();
    }

    static int interrupt(JSContext* /*ctx*/, void* opaque) {
        auto* self = static_cast<QuickJSContext*>(opaque);
        if (!self) return 0;
        if (QuickJSContext::now() > self->deadline || (self->cpuDeadline != std::numeric_limits<double>::infinity() && QuickJSContext::threadCpuTime() > self->cpuDeadline)) {
            self->interrupted = true;
            return 1;
        }
        return 0;
    }
};

double QuickJSContext::now() {
    using namespace std::chrono;
    return duration<double>(steady_clock::now().time_since_epoch()).count();
}

double QuickJSContext::threadCpuTime() {
#ifdef _WIN32
    FILETIME creation, exit, kernel, user;
    if (!GetThreadTimes(GetCurrentThread(), &creation, &exit, &kernel, &user)) return now();
    auto ticks = [](const FILETIME& f) { return (uint64_t(f.dwHighDateTime) << 32) | f.dwLowDateTime; };
    return double(ticks(kernel) + ticks(user)) / 1e7;
#else
    timespec t{};
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t);
    return double(t.tv_sec) + double(t.tv_nsec) / 1e9;
#endif
}

QuickJSContext::QuickJSContext(size_t memoryLimitBytes, size_t maxStackBytes) {
    runtime_ = JS_NewRuntime();
    JS_SetMemoryLimit(runtime_, memoryLimitBytes);
    JS_SetMaxStackSize(runtime_, maxStackBytes);
    context_ = JS_NewContext(runtime_);
    JS_SetContextOpaque(context_, this);
    // No module loader is installed: `import()` of anything fails inside the sandbox.
    JS_SetInterruptHandler(runtime_, &QuickJSTrampoline::interrupt, this);
}

struct QuickJSContext::Captured {
    std::string name;
    JSValue fn;
};

QuickJSContext::~QuickJSContext() {
    for (Captured* c : captured_) {
        qjs_free_value(context_, c->fn);
        delete c;
    }
    JS_FreeContext(context_);
    JS_FreeRuntime(runtime_);
}

size_t QuickJSContext::memoryUsed() const { return qjs_memory_used(runtime_); }

void QuickJSContext::registerFunction(const std::string& name, HostFunction fn) {
    int magic = int(functions_.size());
    functions_.push_back(std::move(fn));
    JSValue f = qjs_new_cfunction_magic(context_, &QuickJSTrampoline::call, name.c_str(), 2, magic);
    qjs_set_global_prop(context_, name.c_str(), f); // takes ownership of f
}

// QuickJS measures stack depth from the stack pointer it saw when the runtime
// was created; re-read it on every entry (belt and braces — contexts are pinned
// to a JSThread anyway).
void QuickJSContext::enter() { JS_UpdateStackTop(runtime_); }

JSError QuickJSContext::takeException() {
    char* c = qjs_take_exception_description(context_);
    if (!c) return JSError("unknown error");
    std::string text(c);
    free(c);
    return JSError(text);
}

static JSResult readResult(JSContext* ctx, JSValue v) {
    JSResult r;
    if (qjs_is_string(v)) {
        size_t length = 0;
        const char* c = JS_ToCStringLen2(ctx, &length, v, false);
        r.string = c ? std::string(c, length) : std::string();
        if (c) JS_FreeCString(ctx, c);
    } else if (qjs_is_number(v)) {
        r.number = qjs_to_float64(ctx, v);
    }
    qjs_free_value(ctx, v);
    return r;
}

JSResult QuickJSContext::evaluate(const std::string& code, const std::string& filename, bool asModule) {
    enter();
    JSValue v = qjs_eval(context_, code.c_str(), code.size(), filename.c_str(), asModule);
    if (qjs_is_exception(v)) throw takeException();
    return readResult(context_, v);
}

JSResult QuickJSContext::evaluateBytecode(const std::vector<uint8_t>& blob, const std::string& source) {
    enter();
    JSValue v = qjs_eval_bytecode(context_, blob.data(), blob.size(), source.data(), source.size());
    if (qjs_is_exception(v)) throw takeException();
    return readResult(context_, v);
}

std::vector<uint8_t> QuickJSContext::compileBytecode(const std::string& code, const std::string& filename) {
    enter();
    size_t len = 0;
    uint8_t* blob = qjs_compile_bytecode(context_, code.data(), code.size(), filename.c_str(), &len);
    if (!blob) throw takeException();
    std::vector<uint8_t> out(blob, blob + len);
    qjs_free_bytecode(context_, blob);
    return out;
}

bool QuickJSContext::captureGlobalFunction(const std::string& name) {
    enter();
    JSValue fn = qjs_get_global_prop(context_, name.c_str());
    if (!qjs_is_function(context_, fn)) {
        qjs_free_value(context_, fn);
        return false;
    }
    for (Captured* c : captured_) {
        if (c->name == name) {
            qjs_free_value(context_, c->fn);
            c->fn = fn;
            return true;
        }
    }
    captured_.push_back(new Captured{name, fn});
    return true;
}

JSResult QuickJSContext::call(const std::string& name, const std::vector<std::string>& args) {
    enter();
    const JSValue* kept = nullptr;
    for (Captured* c : captured_) if (c->name == name) kept = &c->fn;
    JSValue fn = kept ? qjs_dup_value(context_, *kept) : qjs_get_global_prop(context_, name.c_str());
    if (!qjs_is_function(context_, fn)) {
        qjs_free_value(context_, fn);
        throw JSError(name + " is not a function");
    }
    std::vector<JSValue> argv;
    argv.reserve(args.size());
    for (const auto& a : args) argv.push_back(qjs_new_string_len(context_, a.data(), a.size()));
    JSValue r = qjs_call(context_, fn, int(argv.size()), argv.data());
    for (auto& v : argv) qjs_free_value(context_, v);
    qjs_free_value(context_, fn);
    if (qjs_is_exception(r)) throw takeException();
    return readResult(context_, r);
}

bool QuickJSContext::hasPendingJob() const { return qjs_is_job_pending(runtime_); }

void QuickJSContext::executePendingJobs(const std::function<void(const JSError&)>& onError) {
    enter();
    for (int i = 0; i < 100; i++) {
        JSContext* ctx = nullptr;
        int r = qjs_execute_pending_job(runtime_, &ctx);
        if (r < 0) { onError(takeException()); continue; }
        if (r == 0) break;
    }
}

} // namespace twidget

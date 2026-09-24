#include "v2wasm/WamrCoreBackend.h"

#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>

#include "v2wasm/CallCoreABI.h"

#include "rtc_base/logging.h"

namespace tgcalls {

namespace {

bool ensureWamrRuntime() {
    static bool initialized = []() {
        RuntimeInitArgs initArgs;
        memset(&initArgs, 0, sizeof(initArgs));
        initArgs.mem_alloc_type = Alloc_With_System_Allocator;

        // "(*~)" / "(*~*~)i": WAMR validates each (ptr, len) buffer range
        // inside module memory and passes native pointers — the
        // trust-boundary check. Registered directly against the class's
        // public static trampolines (see WamrCoreBackend.h) — no separate
        // extern "C" thunks are needed since NativeSymbol::func_ptr is a
        // plain void*.
        static NativeSymbol nativeSymbols[] = {
            { "host_emit", (void *)WamrCoreBackend::hostEmitNative, "(*~)", nullptr },
            { "host_deflate", (void *)WamrCoreBackend::hostDeflateNative, "(*~*~)i", nullptr },
            { "host_inflate", (void *)WamrCoreBackend::hostInflateNative, "(*~*~)i", nullptr },
        };
        initArgs.native_module_name = "env";
        initArgs.native_symbols = nativeSymbols;
        initArgs.n_native_symbols = 3;

        return wasm_runtime_full_init(&initArgs);
    }();
    return initialized;
}

} // namespace

void WamrCoreBackend::hostEmitNative(wasm_exec_env_t execEnv, uint8_t *data, uint32_t len) {
    auto instance = wasm_runtime_get_module_inst(execEnv);
    auto backend = static_cast<WamrCoreBackend *>(wasm_runtime_get_custom_data(instance));
    if (backend && backend->_emit) {
        backend->_emit(data, len);
    }
}

int32_t WamrCoreBackend::hostDeflateNative(wasm_exec_env_t execEnv, uint8_t *in, uint32_t inLen, uint8_t *out, uint32_t outCap) {
    (void)execEnv; // stateless service; buffers already range-validated by WAMR
    return tgcalls_host_deflate(in, inLen, out, outCap);
}

int32_t WamrCoreBackend::hostInflateNative(wasm_exec_env_t execEnv, uint8_t *in, uint32_t inLen, uint8_t *out, uint32_t outCap) {
    (void)execEnv;
    return tgcalls_host_inflate(in, inLen, out, outCap);
}

#if TGCALLS_ALLOW_EXTERNAL_WASM_CORE
WamrCoreBackend::WamrCoreBackend(std::string modulePath) :
_modulePath(std::move(modulePath)) {
}
#endif

WamrCoreBackend::WamrCoreBackend(const uint8_t *moduleBytes, size_t moduleSize) :
_moduleBytes(moduleBytes, moduleBytes + moduleSize) {
}

WamrCoreBackend::~WamrCoreBackend() {
    if (_execEnv) {
        wasm_runtime_destroy_exec_env(_execEnv);
    }
    if (_instance) {
        wasm_runtime_deinstantiate(_instance);
    }
    if (_module) {
        wasm_runtime_unload(_module);
    }
    if (_devNullFd >= 0) {
        close(_devNullFd);
    }
    // The process-wide runtime stays initialized (shared across calls).
}

#if TGCALLS_ALLOW_EXTERNAL_WASM_CORE
bool WamrCoreBackend::readModuleFromFile() {
    FILE *file = fopen(_modulePath.c_str(), "rb");
    if (!file) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: cannot open module: " << _modulePath;
        return false;
    }
    fseek(file, 0, SEEK_END);
    const long size = ftell(file);
    if (size <= 0) {
        fclose(file);
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: cannot determine module size: " << _modulePath;
        return false;
    }
    fseek(file, 0, SEEK_SET);
    _moduleBytes.resize((size_t)size);
    const size_t read = fread(_moduleBytes.data(), 1, (size_t)size, file);
    fclose(file);
    if (read != (size_t)size) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: short read of module";
        return false;
    }
    return true;
}
#endif

bool WamrCoreBackend::create(std::string const &configJson, EmitFn emit) {
    _emit = std::move(emit);

    if (!ensureWamrRuntime()) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: runtime init failed";
        return false;
    }

#if TGCALLS_ALLOW_EXTERNAL_WASM_CORE
    if (!_modulePath.empty() && !readModuleFromFile()) {
        return false;
    }
#endif
    if (_moduleBytes.empty()) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: no module bytes";
        return false;
    }

    char error[128] = {0};
    _module = wasm_runtime_load(_moduleBytes.data(), (uint32_t)_moduleBytes.size(), error, sizeof(error));
    if (!_module) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: load failed: " << error;
        return false;
    }

    // Sandbox the module's WASI stdio to /dev/null: without this the loader
    // maps the module's stdio to the REAL process stdin/stdout/stderr
    // (fd_read on stdin could even block the media thread). No preopened
    // dirs, no env, no args — the module gets no other WASI capabilities.
    _devNullFd = open("/dev/null", O_RDWR);
    if (_devNullFd < 0) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: cannot open /dev/null";
        return false;
    }
    wasm_runtime_set_wasi_args_ex(_module, nullptr, 0, nullptr, 0, nullptr, 0, nullptr, 0, _devNullFd, _devNullFd, _devNullFd);

    _instance = wasm_runtime_instantiate(_module, 512 * 1024, 0, error, sizeof(error));
    if (!_instance) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: instantiate failed: " << error;
        return false;
    }
    wasm_runtime_set_custom_data(_instance, this);
    _execEnv = wasm_runtime_create_exec_env(_instance, 512 * 1024);
    if (!_execEnv) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: exec env failed";
        return false;
    }

    // WASI reactor protocol: DO NOT call "_initialize" here. WAMR's
    // wasm_runtime_instantiate() already invokes it internally (once,
    // before returning) for any module that imports the WASI API — see
    // execute_post_instantiate_functions() in
    // core/iwasm/interpreter/wasm_runtime.c. wasi-libc's crt1-reactor.c
    // guards "_initialize" with a "called more than once" trap
    // (__builtin_trap() -> an "unreachable" instruction), so calling it
    // again here traps every time and create() always fails.

    _coreInit = wasm_runtime_lookup_function(_instance, "core_init");
    _coreOnEvent = wasm_runtime_lookup_function(_instance, "core_on_event");
    _rtAlloc = wasm_runtime_lookup_function(_instance, "rt_alloc");
    _rtFree = wasm_runtime_lookup_function(_instance, "rt_free");
    if (!_coreInit || !_coreOnEvent || !_rtAlloc || !_rtFree) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: missing required export";
        return false;
    }

    return callCoreFunction(_coreInit, (const uint8_t *)configJson.data(), configJson.size());
}

bool WamrCoreBackend::onEvent(const uint8_t *data, size_t len) {
    if (!_instance) {
        return false;
    }
    return callCoreFunction(_coreOnEvent, data, len);
}

bool WamrCoreBackend::callCoreFunction(wasm_function_inst_t function, const uint8_t *data, size_t len) {
    // rt_alloc -> copy in -> call -> rt_free (ABI module-form buffer contract).
    uint32_t args[2] = { (uint32_t)len, 0 };
    if (!wasm_runtime_call_wasm(_execEnv, _rtAlloc, 1, args)) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: rt_alloc trapped: " << wasm_runtime_get_exception(_instance);
        return false;
    }
    const uint32_t appOffset = args[0];
    if (appOffset == 0) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: rt_alloc returned null";
        return false;
    }
    if (!wasm_runtime_validate_app_addr(_instance, (uint64_t)appOffset, (uint64_t)len)) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: rt_alloc returned invalid range";
        return false;
    }
    void *native = wasm_runtime_addr_app_to_native(_instance, (uint64_t)appOffset);
    memcpy(native, data, len);

    uint32_t callArgs[2] = { appOffset, (uint32_t)len };
    const bool ok = wasm_runtime_call_wasm(_execEnv, function, 2, callArgs);
    if (!ok) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: core call trapped: " << wasm_runtime_get_exception(_instance);
    }

    uint32_t freeArgs[1] = { appOffset };
    if (!wasm_runtime_call_wasm(_execEnv, _rtFree, 1, freeArgs)) {
        RTC_LOG(LS_ERROR) << "WamrCoreBackend: rt_free trapped: " << wasm_runtime_get_exception(_instance);
        return false;
    }
    return ok;
}

} // namespace tgcalls

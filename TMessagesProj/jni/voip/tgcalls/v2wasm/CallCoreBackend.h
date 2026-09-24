#ifndef TGCALLS_V2WASM_CALL_CORE_BACKEND_H
#define TGCALLS_V2WASM_CALL_CORE_BACKEND_H

#include <cstdint>
#include <functional>
#include <string>

namespace tgcalls {

// Backend abstraction over the ABI-v1 call core: native (linked) or WASM
// (runtime-loaded module). Emit semantics are the ABI's: the callback fires
// only from inside create()/onEvent() on the calling thread; the host queues
// emitted commands and executes them after the call returns. A false return
// from create()/onEvent() is a fatal core failure — the host disables the
// core and fails the call (no fallback in Phase 2).
class CallCoreBackend {
public:
    using EmitFn = std::function<void(const uint8_t *, size_t)>;

    virtual ~CallCoreBackend() = default;

    virtual bool create(std::string const &configJson, EmitFn emit) = 0;
    virtual bool onEvent(const uint8_t *data, size_t len) = 0;
};

} // namespace tgcalls

#endif

#include "v2wasm/NativeCoreBackend.h"

namespace tgcalls {

NativeCoreBackend::~NativeCoreBackend() {
    if (_core) {
        tgcalls_core_destroy(_core);
        _core = nullptr;
    }
}

void NativeCoreBackend::emitTrampoline(void *userData, const uint8_t *data, size_t len) {
    auto backend = static_cast<NativeCoreBackend *>(userData);
    if (backend->_emit) {
        backend->_emit(data, len);
    }
}

bool NativeCoreBackend::create(std::string const &configJson, EmitFn emit) {
    _emit = std::move(emit);
    _core = tgcalls_core_create(configJson.c_str(), &NativeCoreBackend::emitTrampoline, this);
    return _core != nullptr;
}

bool NativeCoreBackend::onEvent(const uint8_t *data, size_t len) {
    if (!_core) {
        return false;
    }
    tgcalls_core_on_event(_core, data, len);
    return true;
}

} // namespace tgcalls

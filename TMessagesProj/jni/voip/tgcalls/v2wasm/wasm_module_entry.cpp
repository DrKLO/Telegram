// ABI v1 "module form" entry points (see CallCoreABI.h). This file is
// compiled ONLY for wasm32 by the reference_core_wasm genrule — it must
// never be added to the native source lists. One module instance per call:
// the instance is the handle, so state is a single global core.

#include "v2wasm/CoreFactory.h"

#include "third-party/json11.hpp"

#include <cstdint>
#include <cstdlib>
#include <memory>
#include <string>

extern "C" {
__attribute__((import_module("env"), import_name("host_emit")))
void host_emit(const uint8_t *data, size_t len);
}

namespace {

std::unique_ptr<tgcalls::v2wasm::ReferenceCallCore> globalCore;

} // namespace

extern "C" {

__attribute__((export_name("rt_alloc")))
uint8_t *rt_alloc(size_t len) {
    return (uint8_t *)malloc(len);
}

__attribute__((export_name("rt_free")))
void rt_free(uint8_t *ptr) {
    free(ptr);
}

__attribute__((export_name("core_init")))
void core_init(const uint8_t *configJson, size_t len) {
    std::string parsingError;
    const auto config = json11::Json::parse(std::string((const char *)configJson, len), parsingError);
    globalCore = tgcalls::v2wasm::createModuleCore(config, [](json11::Json::object &&command) {
        const std::string serialized = json11::Json(std::move(command)).dump();
        host_emit((const uint8_t *)serialized.data(), serialized.size());
    });
}

__attribute__((export_name("core_on_event")))
void core_on_event(const uint8_t *data, size_t len) {
    if (!globalCore || !data) {
        return;
    }
    std::string parsingError;
    const auto event = json11::Json::parse(std::string((const char *)data, len), parsingError);
    if (!event.is_object()) {
        return;
    }
    globalCore->onEvent(event);
}

} // extern "C"

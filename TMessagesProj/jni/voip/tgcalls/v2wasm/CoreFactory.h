#ifndef TGCALLS_V2WASM_CORE_FACTORY_H
#define TGCALLS_V2WASM_CORE_FACTORY_H

// Selects which core class a WASM module instantiates. Each module genrule
// compiles wasm_module_entry.cpp plus exactly ONE *_core_factory.cpp
// implementing createModuleCore. WASM discipline: include only core headers,
// json11 and std.

#include <functional>
#include <memory>

#include "third-party/json11.hpp"

#include "v2wasm/ReferenceCallCore.h"

namespace tgcalls {
namespace v2wasm {

std::unique_ptr<ReferenceCallCore> createModuleCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit);

} // namespace v2wasm
} // namespace tgcalls

#endif

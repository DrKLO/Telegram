// Factory TU for reference-core-abi1.wasm. Compiled ONLY by the
// reference_core_wasm genrule — never in native source lists.

#include "v2wasm/CoreFactory.h"

namespace tgcalls {
namespace v2wasm {

std::unique_ptr<ReferenceCallCore> createModuleCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit) {
    return std::make_unique<ReferenceCallCore>(config, std::move(emit));
}

} // namespace v2wasm
} // namespace tgcalls

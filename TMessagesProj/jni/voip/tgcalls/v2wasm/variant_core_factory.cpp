// Factory TU for variant-core-abi1.wasm. Compiled ONLY by the
// variant_core_wasm genrule — never in native source lists.

#include "v2wasm/CoreFactory.h"
#include "v2wasm/VariantCallCore.h"

namespace tgcalls {
namespace v2wasm {

std::unique_ptr<ReferenceCallCore> createModuleCore(json11::Json const &config, std::function<void(json11::Json::object &&)> emit) {
    return std::make_unique<VariantCallCore>(config, std::move(emit));
}

} // namespace v2wasm
} // namespace tgcalls

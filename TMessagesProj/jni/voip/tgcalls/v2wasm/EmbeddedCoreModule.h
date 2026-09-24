#ifndef TGCALLS_V2WASM_EMBEDDED_CORE_MODULE_H
#define TGCALLS_V2WASM_EMBEDDED_CORE_MODULE_H

#include <cstddef>
#include <cstdint>

namespace tgcalls {
namespace v2wasm {

// reference-core-abi1.wasm, linked into the binary by the
// :reference_core_embedded_src genrule (generated source
// ReferenceCoreEmbedded.cpp). In a production build this is the ONLY module
// that can be executed — the filesystem loader is compiled out, see
// TGCALLS_ALLOW_EXTERNAL_WASM_CORE in WamrCoreBackend.h.
extern const uint8_t kReferenceCoreWasm[];
extern const size_t kReferenceCoreWasmSize;

} // namespace v2wasm
} // namespace tgcalls

#endif

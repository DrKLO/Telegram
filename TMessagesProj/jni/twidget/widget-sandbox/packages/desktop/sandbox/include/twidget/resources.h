// The JavaScript shipped with the core (built by `pnpm build:desktop` into
// desktop/WidgetJS): the three bundles and their bytecode.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace twidget {

class SandboxResources {
public:
    /// Reads `dir`/sandbox-bundle.js, validator-bundle.js and renderer-bundle.js
    /// (optional). Throws std::runtime_error when a bundle is missing.
    static std::shared_ptr<SandboxResources> fromDirectory(const std::string& dir);

    std::string sandboxBundle;
    std::string validatorBundle;
    std::string rendererBundle;
    /// Precompiled bytecode of the bundles (`.qbc` next to the `.js`, built together; empty when not shipped).
    std::vector<uint8_t> sandboxBytecode;
    std::vector<uint8_t> validatorBytecode;
};

} // namespace twidget

#include "twidget/resources.h"

#include <fstream>
#include <iterator>
#include <optional>
#include <sstream>
#include <stdexcept>

namespace twidget {

static std::optional<std::string> readFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return std::nullopt;
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

static std::vector<uint8_t> readBytes(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return {};
    return std::vector<uint8_t>((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
}

std::shared_ptr<SandboxResources> SandboxResources::fromDirectory(const std::string& dir) {
    auto res = std::make_shared<SandboxResources>();
    auto need = [&](const char* name) {
        auto s = readFile(dir + "/" + name);
        if (!s) throw std::runtime_error(std::string("WidgetSandbox: missing resource ") + dir + "/" + name + " — run `pnpm build:desktop`");
        return *s;
    };
    res->sandboxBundle = need("sandbox-bundle.js");
    res->validatorBundle = need("validator-bundle.js");
    res->sandboxBytecode = readBytes(dir + "/sandbox-bundle.qbc");
    res->validatorBytecode = readBytes(dir + "/validator-bundle.qbc");
    res->rendererBundle = readFile(dir + "/renderer-bundle.js").value_or("");
    return res;
}

} // namespace twidget

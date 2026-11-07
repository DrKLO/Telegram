#include "twidget/bundle_stamp.h"

#include "twidget/resources.h"
#include "twidget/version.h"

#include <map>

namespace twidget {

BundleStamp BundleStamp::glue() {
    return BundleStamp{version::VERSION, version::PROTOCOL, version::PROFILE, version::SDK};
}

std::optional<BundleStamp> BundleStamp::parse(const std::string& source) {
    static const std::string prefix = "/* widget-sandbox ", suffix = " */";
    std::string line = source.substr(0, source.find('\n'));
    if (line.size() < prefix.size() + suffix.size() || line.compare(0, prefix.size(), prefix) != 0 ||
        line.compare(line.size() - suffix.size(), suffix.size(), suffix) != 0) return std::nullopt;
    std::string body = line.substr(prefix.size(), line.size() - prefix.size() - suffix.size());
    std::map<std::string, std::string> fields;
    size_t start = 0;
    while (true) {
        size_t end = body.find(' ', start);
        std::string pair = body.substr(start, end == std::string::npos ? std::string::npos : end - start);
        size_t eq = pair.find('=');
        if (eq == std::string::npos || eq == 0) return std::nullopt;
        fields[pair.substr(0, eq)] = pair.substr(eq + 1);
        if (end == std::string::npos) break;
        start = end + 1;
    }
    auto number = [&](const char* key) -> std::optional<int> {
        auto it = fields.find(key);
        if (it == fields.end() || it->second.empty() || it->second.size() > 6) return std::nullopt;
        int n = 0;
        for (char c : it->second) {
            if (c < '0' || c > '9') return std::nullopt;
            n = n * 10 + (c - '0');
        }
        return n;
    };
    auto v = fields.find("version");
    auto p = number("protocol"), pr = number("profile"), sdk = number("sdk");
    if (v == fields.end() || v->second.empty() || !p || !pr || !sdk) return std::nullopt;
    return BundleStamp{v->second, *p, *pr, *sdk};
}

std::string BundleStamp::describe() const {
    return "widget-sandbox " + version + " (protocol " + std::to_string(protocol) + ", profile " + std::to_string(profile) + ", sdk " + std::to_string(sdk) + ")";
}

std::string BundleStamp::line() const {
    return "/* widget-sandbox version=" + version + " protocol=" + std::to_string(protocol) + " profile=" + std::to_string(profile) + " sdk=" + std::to_string(sdk) + " */";
}

std::optional<std::string> BundleStamp::refusal(const std::string& source, const std::string& name) {
    static const std::string fix = "the bundles and the glue must come from one release (rebuild the bundles with pnpm build:desktop)";
    BundleStamp mine = glue();
    auto stamp = parse(source);
    if (!stamp) return name + " has no version stamp, this glue is " + mine.describe() + ": " + fix;
    if (stamp->protocol == mine.protocol && stamp->profile == mine.profile && stamp->sdk == mine.sdk) return std::nullopt;
    return name + " is " + stamp->describe() + ", this glue is " + mine.describe() + ": " + fix;
}

std::optional<std::string> BundleStamp::refusal(const SandboxResources& r) {
    if (auto why = refusal(r.sandboxBundle, "sandbox-bundle.js")) return why;
    if (auto why = refusal(r.validatorBundle, "validator-bundle.js")) return why;
    // The renderer bundle is optional here (no renderer of the core's own yet).
    if (!r.rendererBundle.empty()) return refusal(r.rendererBundle, "renderer-bundle.js");
    return std::nullopt;
}

} // namespace twidget

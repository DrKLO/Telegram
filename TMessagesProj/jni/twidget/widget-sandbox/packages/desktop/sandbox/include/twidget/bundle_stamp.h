// The stamp packages/core/scripts/build-native-bundles.ts writes on the first line of every
// bundle, from VERSION.json (packages/core/scripts/version.ts): this glue runs only bundles of
// its own protocol, profile and sdk (twidget/version.h, generated from the same file), the
// way it runs only a `.qbc` compiled from the source next to it. The release itself is not
// compared. Twins: BundleStamp.swift, BundleStamp.kt; the format's tests: version.test.ts.
#pragma once

#include <optional>
#include <string>

namespace twidget {

class SandboxResources;

struct BundleStamp {
    std::string version;
    int protocol = 0;
    int profile = 0;
    int sdk = 0;

    /// This glue's own.
    static BundleStamp glue();
    /// The stamp on the first line of a bundle's source; nullopt when there is none (a bundle built before
    /// VERSION.json) or it is malformed: the prefix, then `key=value` pairs separated by one space, unknown keys
    /// ignored, `version` non-empty and the three numbers 1 to 6 ASCII digits.
    static std::optional<BundleStamp> parse(const std::string& source);
    /// Why this glue refuses a bundle (`bundle-mismatch`), or nullopt when it runs it. `name` is the bundle's file name.
    static std::optional<std::string> refusal(const std::string& source, const std::string& name);
    /// The first of a set's bundles this glue refuses (sandbox, validator, then the renderer when there is one).
    static std::optional<std::string> refusal(const SandboxResources& resources);

    /// `widget-sandbox 0.6.0 (protocol 1, profile 1, sdk 1)`.
    std::string describe() const;
    /// The line itself, as build-native-bundles.ts writes it.
    std::string line() const;
    bool operator==(const BundleStamp&) const = default;
};

} // namespace twidget

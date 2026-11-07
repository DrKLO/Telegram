// `Intl` is delegated to the host (kinds number, numberParts, numberOptions,
// date, dateMethod, dateParts, dateOptions, plural, relative, list, compare,
// upper, lower; see src/sandbox/globals.ts). The core ships a locale-agnostic
// fallback good enough for the harness and tests; a product host (tdesktop)
// plugs QLocale / ICU in through this interface.
#pragma once

#include <string>

namespace twidget {

class Json;

class IntlProvider {
public:
    virtual ~IntlProvider() = default;
    /// Request object → formatted string ("" when unsupported). Pure, no I/O.
    virtual std::string format(const Json& request) = 0;
};

/// Formatting without locale data, as V8 answers in en-US: numbers (with the
/// separators of a few dozen languages), dates in UTC, CLDR plural rules,
/// relative times, lists, a Latin-1 collator, every kind incl. `*Parts` and
/// `*Options`; checked against tests/fixtures/intl-cases.json (`basic` cases).
class BasicIntl : public IntlProvider {
public:
    std::string format(const Json& request) override;
};

} // namespace twidget

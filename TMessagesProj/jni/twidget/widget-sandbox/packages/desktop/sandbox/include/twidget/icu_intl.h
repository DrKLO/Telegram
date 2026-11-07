// `Intl` on the system's ICU (Windows 10 1903 and later ship it as icu.dll): CLDR data for every kind
// the shim forwards, as the Apple and Android bridges answer them. Windows only; a host passes it to
// WidgetHost in place of the BasicIntl fallback.
#pragma once

#include "twidget/intl.h"

#include <string>

namespace twidget {

class IcuIntl : public IntlProvider {
public:
    /// `fallbackLocale` (BCP 47) answers a request that names no locale: the client's UI language.
    explicit IcuIntl(std::string fallbackLocale = "en-US");
    /// A kind or an option this ICU cannot answer comes from BasicIntl.
    std::string format(const Json& request) override;

private:
    std::string fallback_;
    BasicIntl basic_;
};

} // namespace twidget

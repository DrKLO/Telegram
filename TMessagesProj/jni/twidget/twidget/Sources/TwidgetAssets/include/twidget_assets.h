#ifndef TWIDGET_ASSETS_H
#define TWIDGET_ASSETS_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

int twa_is_icon_url(const char *url);
char *twa_icon_svg(const char *url, double *size);
char *twa_icon_data_url(const char *url);
char *twa_icon_canonical_url(const char *url);
void twa_free(char *string);
int twa_manifest_version(void);

#ifdef __cplusplus
}

#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace twa
{

enum class Variant : uint8_t
{
    light,
    regular,
    bold,
    fill
};

struct Icon
{
    std::string name;
    Variant     variant = Variant::regular;
    float       size    = 20;
    uint32_t    rgba    = 0;
    bool        known   = false;
};

std::optional<Icon> parseIconURL(std::string_view url);
std::optional<Icon> parseSymbolURL(std::string_view url);
std::optional<Icon> parseURL(std::string_view url);

std::string svg(const Icon& icon);
std::string dataURL(const Icon& icon);
std::string canonicalURL(const Icon& icon);

bool             hasIcon(std::string_view name);
const char*      symbolAlias(std::string_view symbol);
size_t           iconCount();
const char*      iconName(size_t index);
size_t           symbolAliasCount();
const char*      symbolAliasName(size_t index);
int              manifestVersion();
std::string_view variantName(Variant variant);

}
#endif

#endif

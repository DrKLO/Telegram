#include "twidget_assets.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>

namespace twa
{

namespace
{

struct IconRecord
{
    const char* name;
    const char* paths[4];
};

struct AliasRecord
{
    const char* symbol;
    const char* icon;
    uint32_t    tint;
};

struct WeightRecord
{
    const char* name;
    Variant     variant;
};

#include "twa_icon_data.inc"

template<typename Record, size_t N>
const Record* Find(const Record (&records)[N], std::string_view key, const char* Record::*field)
{
    const Record* end = records + N;
    const Record* it  = std::lower_bound(records, end, key, [field](const Record& r, std::string_view k) { return std::string_view(r.*field) < k; });
    return it != end && std::string_view(it->*field) == key ? it : nullptr;
}

const IconRecord* FindIcon(std::string_view name)
{
    return Find(kIcons, name, &IconRecord::name);
}

const AliasRecord* FindAlias(std::string_view symbol)
{
    return Find(kSymbolAliases, symbol, &AliasRecord::symbol);
}

bool StartsWithScheme(std::string_view url, std::string_view scheme)
{
    if(url.size() < scheme.size())
        return false;
    for(size_t i = 0; i < scheme.size(); i++)
    {
        char c = url[i];
        if(c >= 'A' && c <= 'Z')
            c = static_cast<char>(c - 'A' + 'a');
        if(c != scheme[i])
            return false;
    }
    return true;
}

bool IsLowerAlnum(char c)
{
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
}

bool ValidIconName(std::string_view name)
{
    if(name.empty() || name.size() > kMaxNameLength || !IsLowerAlnum(name.front()) || !IsLowerAlnum(name.back()))
        return false;
    for(size_t i = 0; i < name.size(); i++)
    {
        char c = name[i];
        if(c == '-')
        {
            if(name[i - 1] == '-')
                return false;
        } else if(!IsLowerAlnum(c))
            return false;
    }
    return true;
}

bool ValidSymbolName(std::string_view name)
{
    if(name.empty() || name.size() > kMaxNameLength || !IsLowerAlnum(name.front()) || !IsLowerAlnum(name.back()))
        return false;
    for(size_t i = 0; i < name.size(); i++)
    {
        char c = name[i];
        if(c == '.')
        {
            if(name[i - 1] == '.')
                return false;
        } else if(!IsLowerAlnum(c))
            return false;
    }
    return true;
}

int HexDigit(char c)
{
    if(c >= '0' && c <= '9')
        return c - '0';
    if(c >= 'a' && c <= 'f')
        return c - 'a' + 10;
    if(c >= 'A' && c <= 'F')
        return c - 'A' + 10;
    return -1;
}

std::string Decode(std::string_view value)
{
    std::string out;
    out.reserve(value.size());
    for(size_t i = 0; i < value.size(); i++)
    {
        if(value[i] == '%' && i + 2 < value.size())
        {
            int hi = HexDigit(value[i + 1]), lo = HexDigit(value[i + 2]);
            if(hi >= 0 && lo >= 0)
            {
                out.push_back(static_cast<char>(hi * 16 + lo));
                i += 2;
                continue;
            }
        }
        out.push_back(value[i]);
    }
    return out;
}

std::optional<float> ParseSize(std::string_view text)
{
    if(text.empty() || text.size() > 16)
        return std::nullopt;
    size_t i = 0;
    if(text[0] == '+')
        i++;
    double value  = 0;
    double scale  = 0;
    int    digits = 0;
    for(; i < text.size(); i++)
    {
        char c = text[i];
        if(c >= '0' && c <= '9')
        {
            digits++;
            if(scale == 0)
                value = value * 10 + (c - '0');
            else
            {
                value += (c - '0') * scale;
                scale /= 10;
            }
        } else if(c == '.' && scale == 0)
            scale = 0.1;
        else
            return std::nullopt;
    }
    if(digits == 0 || !std::isfinite(value))
        return std::nullopt;
    double clamped = std::min<double>(kMaxSize, std::max<double>(kMinSize, value));
    return static_cast<float>(std::round(clamped * 100) / 100);
}

std::optional<uint32_t> ParseColor(std::string_view text)
{
    if(!text.empty() && text.front() == '#')
        text.remove_prefix(1);
    if(text.size() != 3 && text.size() != 4 && text.size() != 6 && text.size() != 8)
        return std::nullopt;
    uint32_t channels[4] = {0, 0, 0, 255};
    bool     shortForm   = text.size() <= 4;
    size_t   count       = shortForm ? text.size() : text.size() / 2;
    for(size_t i = 0; i < count; i++)
    {
        int a = HexDigit(text[shortForm ? i : i * 2]);
        int b = HexDigit(text[shortForm ? i : i * 2 + 1]);
        if(a < 0 || b < 0)
            return std::nullopt;
        channels[i] = shortForm ? static_cast<uint32_t>(a * 17) : static_cast<uint32_t>(a * 16 + b);
    }
    return (channels[0] << 24) | (channels[1] << 16) | (channels[2] << 8) | channels[3];
}

std::optional<Variant> ParseWeight(std::string_view text)
{
    std::string lower(text);
    for(char& c : lower)
        if(c >= 'A' && c <= 'Z')
            c = static_cast<char>(c - 'A' + 'a');
    if(const WeightRecord* w = Find(kWeights, lower, &WeightRecord::name))
        return w->variant;
    return std::nullopt;
}

struct Query
{
    std::optional<float>    size;
    std::optional<uint32_t> color;
    std::optional<Variant>  weight;
    std::string             mode;
};

Query ParseQuery(std::string_view query)
{
    Query out;
    while(!query.empty())
    {
        size_t           amp  = query.find('&');
        std::string_view pair = query.substr(0, amp);
        query                 = amp == std::string_view::npos ? std::string_view() : query.substr(amp + 1);
        size_t           eq   = pair.find('=');
        if(eq == std::string_view::npos)
            continue;
        std::string_view key   = pair.substr(0, eq);
        std::string      value = Decode(pair.substr(eq + 1));
        if(key == "size")
            out.size = ParseSize(value);
        else if(key == "color")
            out.color = ParseColor(value);
        else if(key == "weight")
            out.weight = ParseWeight(value);
        else if(key == "mode")
            out.mode = value.size() <= 16 ? value : std::string();
    }
    return out;
}

void SplitURL(std::string_view rest, std::string_view& name, std::string_view& query)
{
    size_t hash = rest.find('#');
    if(hash != std::string_view::npos)
        rest = rest.substr(0, hash);
    size_t q = rest.find('?');
    name     = rest.substr(0, q);
    query    = q == std::string_view::npos ? std::string_view() : rest.substr(q + 1);
}

Icon Placeholder(Icon icon)
{
    icon.name  = kPlaceholder;
    icon.known = false;
    return icon;
}

Icon Defaults()
{
    Icon icon;
    icon.size    = kDefaultSize;
    icon.rgba    = kDefaultColor;
    icon.variant = kDefaultVariant;
    return icon;
}

void AppendNumber(std::string& out, double value, int decimals)
{
    double factor = std::pow(10.0, decimals);
    long long scaled = static_cast<long long>(std::llround(value * factor));
    long long whole  = scaled / static_cast<long long>(factor);
    long long frac   = scaled % static_cast<long long>(factor);
    out += std::to_string(whole);
    if(frac == 0)
        return;
    std::string digits = std::to_string(frac);
    digits.insert(0, static_cast<size_t>(decimals) - digits.size(), '0');
    while(!digits.empty() && digits.back() == '0')
        digits.pop_back();
    out += '.';
    out += digits;
}

void AppendHex(std::string& out, uint32_t value, int bytes)
{
    static const char digits[] = "0123456789abcdef";
    for(int i = bytes - 1; i >= 0; i--)
    {
        uint32_t b = (value >> (i * 8)) & 0xff;
        out += digits[b >> 4];
        out += digits[b & 15];
    }
}

char* Copy(const std::string& s)
{
    char* out = static_cast<char*>(std::malloc(s.size() + 1));
    if(out)
        std::memcpy(out, s.c_str(), s.size() + 1);
    return out;
}

}

std::optional<Icon> parseIconURL(std::string_view url)
{
    if(!StartsWithScheme(url, "icon:"))
        return std::nullopt;
    Icon icon = Defaults();
    if(url.size() > kMaxURLLength)
        return Placeholder(icon);
    std::string_view name, query;
    SplitURL(url.substr(5), name, query);
    Query q = ParseQuery(query);
    if(q.size)
        icon.size = *q.size;
    if(q.color)
        icon.rgba = *q.color;
    if(q.weight)
        icon.variant = *q.weight;
    if(!ValidIconName(name))
        return Placeholder(icon);
    if(FindIcon(name))
        icon.name = std::string(name);
    else if(name.size() > 5 && name.substr(name.size() - 5) == "-fill" && FindIcon(name.substr(0, name.size() - 5)))
    {
        icon.name    = std::string(name.substr(0, name.size() - 5));
        icon.variant = Variant::fill;
    } else
        return Placeholder(icon);
    icon.known = true;
    return icon;
}

std::optional<Icon> parseSymbolURL(std::string_view url)
{
    if(!StartsWithScheme(url, "sf:"))
        return std::nullopt;
    Icon icon = Defaults();
    icon.size = std::min(kMaxSize, std::round(kDefaultSize * kSymbolSizeScale * 100) / 100);
    if(url.size() > kMaxURLLength)
        return Placeholder(icon);
    std::string_view name, query;
    SplitURL(url.substr(3), name, query);
    Query q = ParseQuery(query);
    if(q.size)
        icon.size = std::min(kMaxSize, std::round(*q.size * kSymbolSizeScale * 100) / 100);
    if(q.weight && *q.weight != Variant::fill)
        icon.variant = *q.weight;
    const AliasRecord* alias = ValidSymbolName(name) ? FindAlias(name) : nullptr;
    bool multicolor = q.mode == "multicolor";
    if(multicolor && alias && alias->tint)
        icon.rgba = alias->tint;
    else if(q.color)
        icon.rgba = *q.color;
    if(!alias)
        return Placeholder(icon);
    std::string_view target = alias->icon;
    if(target.size() > 5 && target.substr(target.size() - 5) == "-fill")
    {
        icon.variant = Variant::fill;
        target       = target.substr(0, target.size() - 5);
    }
    icon.name  = std::string(target);
    icon.known = true;
    return icon;
}

std::optional<Icon> parseURL(std::string_view url)
{
    if(auto icon = parseIconURL(url))
        return icon;
    return parseSymbolURL(url);
}

std::string svg(const Icon& icon)
{
    const IconRecord* record = FindIcon(icon.name);
    if(!record)
        record = FindIcon(kPlaceholder);
    std::string out = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"";
    AppendNumber(out, icon.size, 2);
    out += "\" height=\"";
    AppendNumber(out, icon.size, 2);
    out += "\" viewBox=\"0 0 256 256\">";
    std::string paint = "<path fill=\"#";
    AppendHex(paint, icon.rgba >> 8, 3);
    paint += '"';
    if((icon.rgba & 0xff) != 0xff)
    {
        paint += " fill-opacity=\"";
        AppendNumber(paint, (icon.rgba & 0xff) / 255.0, 3);
        paint += '"';
    }
    paint += " d=\"";
    std::string_view paths = record->paths[static_cast<size_t>(icon.variant)];
    while(true)
    {
        size_t bar = paths.find('|');
        out += paint;
        out += paths.substr(0, bar);
        out += "\"/>";
        if(bar == std::string_view::npos)
            break;
        paths = paths.substr(bar + 1);
    }
    out += "</svg>";
    return out;
}

std::string dataURL(const Icon& icon)
{
    static const char digits[] = "0123456789ABCDEF";
    std::string       markup   = svg(icon);
    std::string       out      = "data:image/svg+xml,";
    out.reserve(out.size() + markup.size() + markup.size() / 4);
    for(unsigned char c : markup)
    {
        bool plain = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~' || c == ',' ||
                     c == '/' || c == ':' || c == '=';
        if(plain)
            out += static_cast<char>(c);
        else
        {
            out += '%';
            out += digits[c >> 4];
            out += digits[c & 15];
        }
    }
    return out;
}

std::string canonicalURL(const Icon& icon)
{
    std::string out = "icon:";
    out += FindIcon(icon.name) ? icon.name : std::string(kPlaceholder);
    if(icon.variant == Variant::fill)
        out += "-fill";
    out += "?size=";
    AppendNumber(out, icon.size, 2);
    out += "&color=";
    if((icon.rgba & 0xff) == 0xff)
        AppendHex(out, icon.rgba >> 8, 3);
    else
        AppendHex(out, icon.rgba, 4);
    if(icon.variant != Variant::fill)
    {
        out += "&weight=";
        out += variantName(icon.variant);
    }
    return out;
}

bool hasIcon(std::string_view name)
{
    return FindIcon(name) != nullptr;
}

const char* symbolAlias(std::string_view symbol)
{
    const AliasRecord* alias = FindAlias(symbol);
    return alias ? alias->icon : nullptr;
}

size_t iconCount()
{
    return sizeof(kIcons) / sizeof(kIcons[0]);
}

const char* iconName(size_t index)
{
    return index < iconCount() ? kIcons[index].name : nullptr;
}

size_t symbolAliasCount()
{
    return sizeof(kSymbolAliases) / sizeof(kSymbolAliases[0]);
}

const char* symbolAliasName(size_t index)
{
    return index < symbolAliasCount() ? kSymbolAliases[index].symbol : nullptr;
}

int manifestVersion()
{
    return kManifestVersion;
}

std::string_view variantName(Variant variant)
{
    switch(variant)
    {
        case Variant::light:
            return "light";
        case Variant::regular:
            return "regular";
        case Variant::bold:
            return "bold";
        case Variant::fill:
            return "fill";
    }
    return "regular";
}

}

extern "C" int twa_is_icon_url(const char* url)
{
    return url && twa::parseIconURL(url) ? 1 : 0;
}

extern "C" char* twa_icon_svg(const char* url, double* size)
{
    auto icon = url ? twa::parseURL(url) : std::nullopt;
    if(!icon)
        return nullptr;
    if(size)
        *size = icon->size;
    return twa::Copy(twa::svg(*icon));
}

extern "C" char* twa_icon_data_url(const char* url)
{
    auto icon = url ? twa::parseURL(url) : std::nullopt;
    return icon ? twa::Copy(twa::dataURL(*icon)) : nullptr;
}

extern "C" char* twa_icon_canonical_url(const char* url)
{
    auto icon = url ? twa::parseURL(url) : std::nullopt;
    return icon ? twa::Copy(twa::canonicalURL(*icon)) : nullptr;
}

extern "C" void twa_free(char* string)
{
    std::free(string);
}

extern "C" int twa_manifest_version(void)
{
    return twa::manifestVersion();
}

#include "lhx_sanitizer.h"

#include <cctype>
#include <mutex>

namespace lhx
{

namespace
{

std::vector<std::string_view> Split(const char* text)
{
    std::vector<std::string_view> out;
    const char*                   p = text;
    while(*p)
    {
        while(*p && std::isspace(static_cast<unsigned char>(*p)))
            p++;
        const char* start = p;
        while(*p && !std::isspace(static_cast<unsigned char>(*p)))
            p++;
        if(p > start)
            out.emplace_back(start, static_cast<size_t>(p - start));
    }
    return out;
}

std::string Lower(std::string_view s)
{
    std::string out(s);
    for(char& c : out)
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return out;
}

std::unordered_set<std::string> Words(const char* text)
{
    std::unordered_set<std::string> out;
    for(auto w : Split(text))
        out.insert(Lower(w));
    return out;
}

std::string_view Trimmed(std::string_view s)
{
    while(!s.empty() && static_cast<unsigned char>(s.front()) <= ' ')
        s.remove_prefix(1);
    while(!s.empty() && static_cast<unsigned char>(s.back()) <= ' ')
        s.remove_suffix(1);
    return s;
}

std::string Scheme(std::string_view url)
{
    size_t i = 0;
    while(i < url.size() && (std::isalnum(static_cast<unsigned char>(url[i])) || url[i] == '+' || url[i] == '-' || url[i] == '.'))
        i++;
    if(i == 0 || i >= url.size() || url[i] != ':' || !std::isalpha(static_cast<unsigned char>(url[0])))
        return {};
    return Lower(url.substr(0, i));
}

bool IsFragment(std::string_view url)
{
    if(url.empty() || url[0] != '#')
        return false;
    for(char c : url.substr(1))
    {
        if(std::isspace(static_cast<unsigned char>(c)) || c == '"' || c == '\'' || c == '(' || c == ')' || c == '\\' ||
           c == '<' || c == '>')
            return false;
    }
    return true;
}

bool ValidAttributeName(const std::string& name)
{
    if(name.empty() || !(std::isalpha(static_cast<unsigned char>(name[0])) || name[0] == '_'))
        return false;
    for(char c : name)
    {
        if(!(std::isalnum(static_cast<unsigned char>(c)) || c == '_' || c == '.' || c == ':' || c == '-'))
            return false;
    }
    return true;
}

bool ValidTagName(const std::string& name)
{
    if(name.empty() || name.size() > 64 || !std::isalpha(static_cast<unsigned char>(name[0])))
        return false;
    for(char c : name)
    {
        if(!(std::isalnum(static_cast<unsigned char>(c)) || c == '_' || c == '.' || c == '-'))
            return false;
    }
    return true;
}

}

SanitizerPolicy SanitizerPolicy::standard()
{
    SanitizerPolicy p;
    p.linkSchemes  = Words("http https mailto");
    p.imageSchemes = {};
    p.droppedTags = Words("script iframe frame frameset object embed link meta base portal noscript set title template applet param noembed noframes plaintext xmp listing slot track area map math metadata fencedframe audio video source");
    p.quietDroppedTags = Words("script title meta");
    p.htmlTags = Words("html head body div span p a b i u s em strong small big sub sup mark code pre kbd samp var abbr cite q blockquote del ins dfn time data address br hr wbr h1 h2 h3 h4 h5 h6 header footer main nav section article aside hgroup search ul ol li dl dt dd menu table thead tbody tfoot tr td th caption colgroup col form input button select option optgroup textarea label fieldset legend datalist output progress meter details summary dialog figure figcaption picture img canvas ruby rt rp bdi bdo center font tt strike nobr style");
    p.svgTags = Words("svg g path rect circle ellipse line polyline polygon text tspan textPath defs symbol use image clipPath mask pattern marker linearGradient radialGradient stop filter foreignObject title desc a switch view feBlend feColorMatrix feComponentTransfer feComposite feConvolveMatrix feDiffuseLighting feDisplacementMap feDistantLight feDropShadow feFlood feFuncA feFuncB feFuncG feFuncR feGaussianBlur feImage feMerge feMergeNode feMorphology feOffset fePointLight feSpecularLighting feSpotLight feTile feTurbulence animate animateTransform animateMotion mpath style");
    p.attributes = Words("class id title lang dir hidden tabindex role draggable contenteditable spellcheck translate inert popover enterkeyhint inputmode autocapitalize slot part name type value checked disabled readonly required placeholder min max step minlength maxlength pattern multiple selected size rows cols wrap for label accept autocomplete list open colspan rowspan headers scope span width height alt align valign bgcolor border cellpadding cellspacing color face nowrap start reversed low high optimum datetime novalidate method enctype dirname popovertarget popovertargetaction command commandfor style viewBox preserveAspectRatio x y x1 y1 x2 y2 cx cy r rx ry d points dx dy rotate transform fill fill-opacity fill-rule stroke stroke-width stroke-opacity stroke-linecap stroke-linejoin stroke-dasharray stroke-dashoffset stroke-miterlimit opacity display visibility overflow clip-path clip-rule mask filter marker marker-start marker-mid marker-end markerWidth markerHeight markerUnits refX refY orient text-anchor dominant-baseline alignment-baseline baseline-shift font-family font-size font-weight font-style font-variant font-stretch letter-spacing word-spacing text-decoration writing-mode textLength lengthAdjust startOffset spacing side gradientUnits gradientTransform spreadMethod fx fy fr offset stop-color stop-opacity patternUnits patternContentUnits patternTransform clipPathUnits maskUnits maskContentUnits pointer-events shape-rendering text-rendering image-rendering color-interpolation color-interpolation-filters vector-effect paint-order mix-blend-mode isolation cursor pathLength in in2 result mode operator k1 k2 k3 k4 stdDeviation edgeMode kernelMatrix kernelUnitLength order divisor bias targetX targetY preserveAlpha surfaceScale diffuseConstant specularConstant specularExponent lighting-color flood-color flood-opacity azimuth elevation z pointsAtX pointsAtY pointsAtZ limitingConeAngle baseFrequency numOctaves seed stitchTiles scale xChannelSelector yChannelSelector radius values tableValues slope intercept amplitude exponent filterUnits primitiveUnits attributeName attributeType begin dur end repeatCount repeatDur from to by keyTimes keySplines keyPoints calcMode additive accumulate restart path version xml:space xml:lang systemLanguage requiredExtensions");
    p.urlAttributes = Words("href xlink:href src srcset poster action formaction background ping data cite codebase manifest longdesc usemap profile icon archive classid lowsrc dynsrc srcdoc imagesrcset imagesizes attributionsrc");
    p.quietAttributes = Words("xmlns xmlns:xlink target rel download autofocus nonce is loading decoding crossorigin referrerpolicy integrity fetchpriority charset http-equiv content async defer");
    p.paintAttributes = Words("fill stroke filter mask clip-path marker marker-start marker-mid marker-end cursor color flood-color lighting-color stop-color");
    p.fragmentHrefTags = Words("use lineargradient radialgradient pattern filter textpath mpath");
    p.atRules = Words("media supports keyframes -webkit-keyframes container layer property counter-style starting-style scope page font-feature-values font-palette-values position-try charset");
    p.imageMIMETypes = Words("image/png image/jpeg image/jpg image/gif image/webp image/avif image/bmp image/x-icon image/vnd.microsoft.icon image/svg+xml");
    p.imageFunctions = Words("image-set -webkit-image-set image src cross-fade -webkit-cross-fade");
    p.blockedProperties = Words("behavior -moz-binding");
    return p;
}

Sanitizer::Sanitizer(std::shared_ptr<const SanitizerPolicy> policy) :
    m_policy(policy ? std::move(policy) : current())
{
}

bool Sanitizer::imageAllowed(std::string_view raw) const
{
    std::string_view url    = Trimmed(raw);
    std::string      scheme = Scheme(url);
    if(!scheme.empty() && (m_policy->imageSchemes.count(scheme) || m_policy->builtinImageSchemes.count(scheme)))
        return true;
    if(scheme != "data")
        return false;
    std::string_view rest = url.substr(5);
    size_t           end  = rest.find_first_of(";,");
    if(end == std::string_view::npos)
        return false;
    return m_policy->imageMIMETypes.count(Lower(Trimmed(rest.substr(0, end)))) != 0;
}

bool Sanitizer::linkAllowed(std::string_view raw) const
{
    std::string_view url = Trimmed(raw);
    if(IsFragment(url))
        return true;
    std::string scheme = Scheme(url);
    return !scheme.empty() && m_policy->linkSchemes.count(scheme) != 0;
}

bool Sanitizer::paintValueAllowed(const std::string& value) const
{
    const std::string lower = Lower(value);
    size_t            pos   = 0;
    while((pos = lower.find("url(", pos)) != std::string::npos)
    {
        size_t           close = value.find(')', pos);
        std::string_view inner = Trimmed(std::string_view(value).substr(pos + 4, close == std::string::npos ? std::string::npos : close - pos - 4));
        if(!inner.empty() && (inner.front() == '"' || inner.front() == '\''))
            inner = Trimmed(inner.substr(1, inner.size() >= 2 ? inner.size() - 2 : 0));
        if(!IsFragment(inner))
            return false;
        pos += 4;
    }
    return true;
}

bool Sanitizer::element(std::string& tag, bool svg, litehtml::string_map& attrs, const Report& report) const
{
    if(!ValidTagName(tag))
    {
        report("tag-dropped", "<" + tag + ">");
        return false;
    }
    std::string name    = Lower(tag);
    bool        renamed = false;
    if(svg)
    {
        if(name != "title" && m_policy->droppedTags.count(name))
            return false;
        if(!m_policy->svgTags.count(name))
        {
            report("tag-dropped", "<svg:" + tag + ">");
            return false;
        }
    } else if(m_policy->droppedTags.count(name))
    {
        if(!m_policy->quietDroppedTags.count(name))
            report("tag-dropped", "<" + name + ">");
        return false;
    } else if(!m_policy->htmlTags.count(name))
    {
        report("tag-replaced", "<" + name + "> rendered as <span>");
        attrs[m_policy->renamedTagAttribute] = name;
        tag                  = "span";
        name                 = "span";
        renamed              = true;
    }

    for(auto it = attrs.begin(); it != attrs.end();)
    {
        const std::string& attr = it->first;
        if(!ValidAttributeName(attr))
        {
            it = attrs.erase(it);
            continue;
        }
        std::string key = Lower(attr);
        if(key == m_policy->renamedTagAttribute && renamed)
        {
            ++it;
            continue;
        }
        if(key.compare(0, 2, "on") == 0)
        {
            report("attr-dropped", name + "[" + key + "]");
            it = attrs.erase(it);
            continue;
        }
        if(m_policy->urlAttributes.count(key))
        {
            const bool href = key == "href" || key == "xlink:href";
            bool       keep = false;
            if(href && (name == "a" || name == "area"))
                keep = linkAllowed(it->second);
            else if(href && svg && name == "image")
                keep = imageAllowed(it->second);
            else if(href && svg && m_policy->fragmentHrefTags.count(name))
                keep = IsFragment(Trimmed(it->second));
            else if(key == "src" && (name == "img" || name == "input"))
                keep = imageAllowed(it->second);
            else if(key == "src" && name == "canvas" && !svg)
                keep = Scheme(Trimmed(it->second)) == "twcanvas";
            if(!keep)
            {
                report("url-blocked", name + "[" + key + "]=" + it->second);
                it = attrs.erase(it);
                continue;
            }
            ++it;
            continue;
        }
        if(!m_policy->reservedAttributePrefix.empty() && key.compare(0, m_policy->reservedAttributePrefix.size(), m_policy->reservedAttributePrefix) == 0 && !m_policy->hostAttributes.count(key))
        {
            it = attrs.erase(it);
            continue;
        }
        if(key.compare(0, 5, "data-") == 0 || key.compare(0, 5, "aria-") == 0)
        {
            ++it;
            continue;
        }
        if(m_policy->quietAttributes.count(key))
        {
            it = attrs.erase(it);
            continue;
        }
        if(!m_policy->attributes.count(key))
        {
            report("attr-dropped", name + "[" + key + "]");
            it = attrs.erase(it);
            continue;
        }
        if(svg && m_policy->paintAttributes.count(key) && !paintValueAllowed(it->second))
        {
            report("url-blocked", name + "[" + key + "]=" + it->second);
            it = attrs.erase(it);
            continue;
        }
        ++it;
    }
    return true;
}

bool Sanitizer::valueAllowed(const litehtml::css_token_vector& tokens, std::vector<std::string>& functions, std::string& why) const
{
    for(const auto& tok : tokens)
    {
        if(tok.type == litehtml::URL || tok.type == litehtml::BAD_URL)
        {
            if(tok.type == litehtml::BAD_URL || (!imageAllowed(tok.str()) && !IsFragment(Trimmed(tok.str()))))
            {
                why = "url(" + tok.str() + ")";
                return false;
            }
            continue;
        }
        if(tok.type == litehtml::STRING && !functions.empty() && m_policy->imageFunctions.count(functions.back()))
        {
            if(!imageAllowed(tok.str()))
            {
                why = "\"" + tok.str() + "\" in " + functions.back() + "()";
                return false;
            }
            continue;
        }
        if(tok.type == litehtml::CV_FUNCTION)
        {
            std::string fn = Lower(tok.name());
            if(fn == "expression")
            {
                why = "expression()";
                return false;
            }
            if(fn == "url")
            {
                if(tok.value.size() != 1 || tok.value[0].type != litehtml::STRING ||
                   (!imageAllowed(tok.value[0].str()) && !IsFragment(Trimmed(tok.value[0].str()))))
                {
                    why = "url() with a blocked source or modifiers";
                    return false;
                }
                continue;
            }
            if((fn == "var" || fn == "env" || fn == "attr"))
            {
                for(const auto& f : functions)
                {
                    if(m_policy->imageFunctions.count(f))
                    {
                        why = fn + "() inside " + f + "()";
                        return false;
                    }
                }
            }
            functions.push_back(fn);
            bool ok = valueAllowed(tok.value, functions, why);
            functions.pop_back();
            if(!ok)
                return false;
            continue;
        }
        if(tok.is_component_value() && !valueAllowed(tok.value, functions, why))
            return false;
    }
    return true;
}

bool Sanitizer::declaration(const std::string& name, const litehtml::css_token_vector& value, const Report& report) const
{
    if(m_policy->blockedProperties.count(name))
    {
        report("css-dropped", name);
        return false;
    }
    bool simple = true;
    for(const auto& tok : value)
    {
        if(tok.type == litehtml::URL || tok.type == litehtml::BAD_URL || tok.is_component_value())
        {
            simple = false;
            break;
        }
    }
    if(simple)
        return true;
    std::vector<std::string> functions;
    std::string              why;
    if(valueAllowed(value, functions, why))
        return true;
    report(why.compare(0, 3, "url") == 0 || why.front() == '"' ? "url-blocked" : "css-dropped", name + ": " + why);
    return false;
}

bool Sanitizer::atRule(const std::string& name, const Report& report) const
{
    if(m_policy->atRules.count(name))
        return true;
    report("css-dropped", "@" + name);
    return false;
}

namespace
{
std::mutex&                             currentLock = *new std::mutex;
std::shared_ptr<const SanitizerPolicy>& currentPolicy = *new std::shared_ptr<const SanitizerPolicy>;
}

std::shared_ptr<const SanitizerPolicy> Sanitizer::current()
{
    std::lock_guard<std::mutex> guard(currentLock);
    if(!currentPolicy)
        currentPolicy = std::make_shared<SanitizerPolicy>(SanitizerPolicy::standard());
    return currentPolicy;
}

void Sanitizer::setCurrent(std::shared_ptr<const SanitizerPolicy> policy)
{
    std::lock_guard<std::mutex> guard(currentLock);
    currentPolicy = std::move(policy);
}

}

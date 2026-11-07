#pragma once

#include <functional>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_set>

#include "css_tokenizer.h"
#include "types.h"

namespace lhx
{

struct SanitizerPolicy
{
    std::unordered_set<std::string> linkSchemes;
    std::unordered_set<std::string> imageSchemes;
    std::unordered_set<std::string> builtinImageSchemes{"icon"};
    std::unordered_set<std::string> imageMIMETypes;
    std::unordered_set<std::string> htmlTags;
    std::unordered_set<std::string> svgTags;
    std::unordered_set<std::string> droppedTags;
    std::unordered_set<std::string> quietDroppedTags;
    std::unordered_set<std::string> attributes;
    std::unordered_set<std::string> urlAttributes;
    std::unordered_set<std::string> quietAttributes;
    std::unordered_set<std::string> paintAttributes;
    std::unordered_set<std::string> fragmentHrefTags;
    std::unordered_set<std::string> atRules;
    std::unordered_set<std::string> imageFunctions;
    std::unordered_set<std::string> blockedProperties;
    std::string                     renamedTagAttribute = "data-lh-tag";
    std::string                     reservedAttributePrefix = "data-lh-";
    std::unordered_set<std::string> hostAttributes{"data-lh-id"};

    static SanitizerPolicy standard();
};

class Sanitizer
{
  public:
    using Report = std::function<void(const char* kind, const std::string& detail)>;

    explicit Sanitizer(std::shared_ptr<const SanitizerPolicy> policy);

    bool element(std::string& tag, bool svg, litehtml::string_map& attrs, const Report& report) const;
    bool declaration(const std::string& name, const litehtml::css_token_vector& value, const Report& report) const;
    bool atRule(const std::string& name, const Report& report) const;
    bool imageAllowed(std::string_view url) const;
    bool linkAllowed(std::string_view url) const;

    const SanitizerPolicy& policy() const { return *m_policy; }

    static std::shared_ptr<const SanitizerPolicy> current();
    static void setCurrent(std::shared_ptr<const SanitizerPolicy> policy);

  private:
    bool valueAllowed(const litehtml::css_token_vector& tokens, std::vector<std::string>& functions, std::string& why) const;
    bool paintValueAllowed(const std::string& value) const;

    std::shared_ptr<const SanitizerPolicy> m_policy;
};

}

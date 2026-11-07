#import "LHSanitizer.h"

#include "LHContainer.h"
#include "lhx_sanitizer.h"

namespace lh = litehtml;

@implementation LHSanitizer

static NSMutableSet<NSString *> *ExtraImageSchemes(void)
{
    static NSMutableSet<NSString *> *schemes;
    static dispatch_once_t           once;
    dispatch_once(&once, ^{
        schemes = [NSMutableSet set];
    });
    return schemes;
}

static NSArray<NSString *> *CurrentLinkSchemes = @[@"http", @"https", @"mailto"];
static NSArray<NSString *> *CurrentImageSchemes = @[];

+ (void)apply
{
    auto policy = std::make_shared<lhx::SanitizerPolicy>(lhx::SanitizerPolicy::standard());
    policy->linkSchemes.clear();
    for(NSString *scheme in CurrentLinkSchemes)
        policy->linkSchemes.insert(scheme.lowercaseString.UTF8String);
    policy->imageSchemes.clear();
    for(NSString *scheme in CurrentImageSchemes)
        policy->imageSchemes.insert(scheme.lowercaseString.UTF8String);
    for(NSString *scheme in ExtraImageSchemes())
        policy->imageSchemes.insert(scheme.lowercaseString.UTF8String);
    lhx::Sanitizer::setCurrent(policy);
}

+ (void)setLinkSchemes:(NSArray<NSString *> *)linkSchemes imageSchemes:(NSArray<NSString *> *)imageSchemes
{
    @synchronized(self)
    {
        CurrentLinkSchemes  = [linkSchemes copy];
        CurrentImageSchemes = [imageSchemes copy];
        [self apply];
    }
}

+ (void)addImageScheme:(NSString *)scheme
{
    @synchronized(self)
    {
        if([ExtraImageSchemes() containsObject:scheme])
            return;
        [ExtraImageSchemes() addObject:scheme];
        [self apply];
    }
}

@end

void LHContainer::sanitizer_report(const char* kind, const std::string& detail)
{
    auto& entry = m_sanitizer_report[kind];
    entry.first++;
    if(entry.second.size() < 8)
        entry.second.push_back(detail.size() > 120 ? detail.substr(0, 120) + "…" : detail);
}

bool LHContainer::sanitize_element(std::string& tag, bool svg, lh::string_map& attrs)
{
    return m_sanitizer.element(tag, svg, attrs, [this](const char* kind, const std::string& detail) { sanitizer_report(kind, detail); });
}

bool LHContainer::sanitize_declaration(const std::string& name, const lh::css_token_vector& value)
{
    if(m_trusted_styles)
        return true;
    return m_sanitizer.declaration(name, value, [this](const char* kind, const std::string& detail) { sanitizer_report(kind, detail); });
}

bool LHContainer::sanitize_at_rule(const std::string& name)
{
    if(m_trusted_styles)
        return true;
    return m_sanitizer.atRule(name, [this](const char* kind, const std::string& detail) { sanitizer_report(kind, detail); });
}

void LHContainer::sanitizer_limit(const char* what)
{
    sanitizer_report("limit", what);
}

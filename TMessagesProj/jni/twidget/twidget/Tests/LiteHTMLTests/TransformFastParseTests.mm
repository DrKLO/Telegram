#import <XCTest/XCTest.h>

#include <cstring>
#include <random>
#include <string>
#include <vector>

#include "litehtml.h"
#include "css_parser.h"
#include "css_effects.h"
#include "style.h"

using namespace litehtml;

@interface TransformFastParseTests : XCTestCase
@end

@implementation TransformFastParseTests

static bool SameTokens(const css_token_vector& a, const css_token_vector& b);

static bool SameToken(const css_token& a, const css_token& b)
{
    if(a.type != b.type || a.repr != b.repr || a.str() != b.str())
        return false;
    if(a.type == NUMBER || a.type == PERCENTAGE || a.type == DIMENSION)
    {
        if(std::memcmp(&a.n.number, &b.n.number, sizeof(float)) != 0)
            return false;
        if(a.type != PERCENTAGE && a.n.number_type != b.n.number_type)
            return false;
    }
    return SameTokens(a.value, b.value);
}

static bool SameTokens(const css_token_vector& a, const css_token_vector& b)
{
    if(a.size() != b.size())
        return false;
    for(size_t i = 0; i < a.size(); i++)
        if(!SameToken(a[i], b[i]))
            return false;
    return true;
}

static bool SameProperty(const style& a, const style& b, string_id name)
{
    const property_value& x = a.get_property(name);
    const property_value& y = b.get_property(name);
    if(x.is<css_token_vector>() != y.is<css_token_vector>() || x.m_important != y.m_important)
        return false;
    if(!x.is<css_token_vector>())
        return x.is<invalid>() == y.is<invalid>();
    return SameTokens(x.get<css_token_vector>(), y.get<css_token_vector>());
}

static bool SameLength(const css_length& a, const css_length& b)
{
    return a.is_predefined() == b.is_predefined() && a.units() == b.units() &&
           (a.is_predefined() ? a.predef() == b.predef() : a.val() == b.val() || (std::isnan(a.val()) && std::isnan(b.val())));
}

static bool SameTransform(const transform_vector& a, const transform_vector& b)
{
    if(a.size() != b.size())
        return false;
    for(size_t i = 0; i < a.size(); i++)
    {
        if(a[i].kind != b[i].kind || !SameLength(a[i].x, b[i].x) || !SameLength(a[i].y, b[i].y) ||
           std::memcmp(a[i].v, b[i].v, sizeof(a[i].v)) != 0)
            return false;
    }
    return true;
}

static int Check(const std::string& value, bool* mismatch)
{
    css_token_vector fast;
    if(!fast_transform_tokens(value, fast))
        return 0;
    style general;
    general.add(normalize("transform:" + value + ";", f_componentize), "", nullptr);
    style quick;
    quick.add_property(_transform_, fast, "", false, nullptr);
    bool same = SameProperty(general, quick, _transform_) &&
                SameProperty(general, quick, raw_declaration_id(_transform_));
    const property_value& g = general.get_property(_transform_);
    if(same && g.is<css_token_vector>())
    {
        transform_vector a, b;
        bool             ga = parse_transform(g.get<css_token_vector>(), a);
        bool             gb = parse_transform(quick.get_property(_transform_).get<css_token_vector>(), b);
        same                = ga == gb && SameTransform(a, b);
    }
    if(!same)
        *mismatch = true;
    return 1;
}

- (void)testCommonValuesTakeFastPath
{
    const char* accepted[] = {
        "translate(1.5px, -2px) rotate(45deg) scale(1.1)",
        "none",
        "  none  ",
        "translateX(10px) translateY(-3.25px)",
        "translate(50%, 0)",
        "rotate(0.5turn)",
        "rotate(-1.2rad)",
        "scaleX(2) scaleY(.5)",
        "skewX(10deg) skewY(-5deg)",
        "matrix(1, 0, 0, 1, 10, 20)",
        "translate(1px 2px)",
        "translate(0)",
        "rotate(0)",
        "scale(50%)",
        "TRANSLATE(1PX)",
    };
    for(const char* value : accepted)
    {
        bool mismatch = false;
        XCTAssertEqual(Check(value, &mismatch), 1, @"%s", value);
        XCTAssertFalse(mismatch, @"%s", value);
    }
    const char* declined[] = {
        "calc(1px)", "translate(calc(1px + 2px))", "translate(var(--x))", "translate3d(1px,2px,3px)",
        "rotateX(10deg)", "perspective(10px)", "translate(1em)", "rotate(10grad)", "translate(1e3px)",
        "translate(1px", "translate()", "translate((1px))", "translate(5.px)", "translate(--5px)",
        "translate(1px) !important", "translate(\\31 px)", "translate(/*x*/1px)", "rotate(1deg);x:y",
        "translate(1px2)", "translate(1px-2px)", "", " ", "nonesuch", "none none", "none translate(1px)",
        "translate(1px,2px,3px,4px,5px,6px,7px)", "skew(10deg)", "translateZ(1px)",
    };
    for(const char* value : declined)
    {
        bool mismatch = false;
        XCTAssertEqual(Check(value, &mismatch), 0, @"%s", value);
    }
    std::string longValue;
    while(longValue.size() <= 512)
        longValue += "translate(1px) ";
    bool mismatch = false;
    XCTAssertEqual(Check(longValue, &mismatch), 0);
    std::string many;
    for(int i = 0; i < 33; i++)
        many += "scale(1)";
    XCTAssertEqual(Check(many, &mismatch), 0);
}

- (void)testRandomValuesMatchGeneralParser
{
    std::mt19937 rng(20261014);
    const char*  names[] = {"translate", "translateX", "translatey", "rotate", "scale", "scaleX", "SCALEY",
                            "skewX", "skewy", "matrix", "skew", "rotateZ", "translate3d", "calc", "var", "perspective",
                            "rotatex", "none", "NONE", "", "url"};
    const char*  units[]  = {"", "", "px", "PX", "deg", "rad", "turn", "TURN", "%", "em", "grad", "e3", "E+3", "px2",
                             "x", "-px", "."};
    const char*  numbers[] = {"0", "1", "-1", "+1", ".5", "-.5", "+.5", "1.5", "5.", "1e3", "1e999", "-", "--5",
                              "123456789012", "0.000001", "3.14159", "00", "-0", "99999999999999999999999999999999999"};
    const char*  seps[]    = {",", ", ", " ", "  ", ",,", " , ", "\t", "\n", "", ",)", "("};
    const char*  noise[]   = {"", "", "", "", " ", "\\", "/*x*/", "!important", ";", ":", "\xc3\xa9", ")", "(", "\r", "_"};
    int          accepted  = 0;
    bool         mismatch  = false;
    for(int round = 0; round < 30000; round++)
    {
        std::string value;
        int         count = 1 + rng() % 4;
        for(int f = 0; f < count; f++)
        {
            if(rng() % 3 == 0)
                value += " ";
            value += names[rng() % (sizeof(names) / sizeof(*names))];
            value += "(";
            int args = rng() % 8;
            for(int a = 0; a < args; a++)
            {
                if(a)
                    value += seps[rng() % (sizeof(seps) / sizeof(*seps))];
                value += numbers[rng() % (sizeof(numbers) / sizeof(*numbers))];
                value += units[rng() % (sizeof(units) / sizeof(*units))];
            }
            value += rng() % 10 ? ")" : "";
            value += noise[rng() % (sizeof(noise) / sizeof(*noise))];
        }
        if(rng() % 5 == 0 && !value.empty())
            value.erase(rng() % value.size(), 1);
        if(rng() % 7 == 0)
            value = "none" + std::string(noise[rng() % (sizeof(noise) / sizeof(*noise))]);
        bool bad = false;
        accepted += Check(value, &bad);
        XCTAssertFalse(bad, @"%s", value.c_str());
        mismatch = mismatch || bad;
    }
    XCTAssertFalse(mismatch);
    XCTAssertGreaterThan(accepted, 1000);
}

@end

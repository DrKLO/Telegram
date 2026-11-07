#include "LHSimpleText.h"

#include <cmath>
#include <cstdlib>

namespace {

constexpr CGFloat kEpsilon = 1e-6;

std::atomic<bool>   gEnabled{getenv("TW_NO_SIMPLE_TEXT") == nullptr};
std::atomic<size_t> gHits{0}, gMisses{0}, gLearned{0}, gRejected{0};

uint16_t PairKey(unsigned char a, unsigned char b)
{
    return uint16_t(a) << 8 | b;
}

}

bool LHSimpleShaper::enabled()
{
    return gEnabled.load(std::memory_order_relaxed);
}

void LHSimpleShaper::set_enabled(bool enabled)
{
    gEnabled.store(enabled);
}

LHSimpleShaper::Stats LHSimpleShaper::stats()
{
    return {gHits.load(), gMisses.load(), gLearned.load(), gRejected.load()};
}

void LHSimpleShaper::reset_stats()
{
    gHits = 0;
    gMisses = 0;
    gLearned = 0;
    gRejected = 0;
}

bool LHSimpleShaper::simple_bytes(const char* text, size_t length)
{
    for(size_t i = 0; i < length; i++)
    {
        const unsigned char c = static_cast<unsigned char>(text[i]);
        if(c < 0x20 || c > 0x7e)
            return false;
    }
    return true;
}

void LHSimpleShaper::clear()
{
    std::lock_guard<std::mutex> lock(m_mutex);
    std::fill(std::begin(m_single), std::end(m_single), Unknown);
    m_kern.clear();
    m_contextual.clear();
    m_triples.clear();
}

bool LHSimpleShaper::shape(const char* text, size_t length, CGGlyph* glyphs, CGFloat* xs, CGFloat* width)
{
    if(length == 0 || length > kMaxLength || !enabled() || !simple_bytes(text, length))
        return false;
    const auto* s = reinterpret_cast<const unsigned char*>(text);
    std::lock_guard<std::mutex> lock(m_mutex);
    CGFloat x = 0;
    for(size_t i = 0; i < length; i++)
    {
        if(m_single[s[i]] != Simple)
        {
            gMisses.fetch_add(1, std::memory_order_relaxed);
            return false;
        }
        glyphs[i] = m_glyph[s[i]];
        xs[i]     = x;
        x += m_advance[s[i]];
        if(i + 1 < length)
        {
            auto kern = m_kern.find(PairKey(s[i], s[i + 1]));
            if(kern == m_kern.end() || (i + 2 < length && !m_triples.count(triple_key(s[i], s[i + 1], s[i + 2]))))
            {
                gMisses.fetch_add(1, std::memory_order_relaxed);
                return false;
            }
            x += kern->second;
        }
    }
    *width = x;
    gHits.fetch_add(1, std::memory_order_relaxed);
    return true;
}

bool LHSimpleShaper::learn_single(unsigned char c)
{
    m_single[c]    = Complex;
    const UniChar u = c;
    CFStringRef string = CFStringCreateWithCharacters(nullptr, &u, 1);
    if(!string)
        return false;
    CFAttributedStringRef attributed = CFAttributedStringCreate(nullptr, string, m_attrs);
    CFRelease(string);
    if(!attributed)
        return false;
    CTLineRef line = CTLineCreateWithAttributedString(attributed);
    CFRelease(attributed);
    if(!line)
        return false;
    CGGlyph mapped = 0;
    bool    ok     = CTFontGetGlyphsForCharacters(m_font, &u, &mapped, 1);
    CFArrayRef runs = CTLineGetGlyphRuns(line);
    if(ok && CFArrayGetCount(runs) == 1)
    {
        auto      run  = static_cast<CTRunRef>(CFArrayGetValueAtIndex(runs, 0));
        CTFontRef font = static_cast<CTFontRef>(CFDictionaryGetValue(CTRunGetAttributes(run), kCTFontAttributeName));
        CGGlyph   glyph = 0;
        CGPoint   point = CGPointZero;
        if(CTRunGetGlyphCount(run) == 1 && font && CFEqual(font, m_font))
        {
            CTRunGetGlyphs(run, CFRangeMake(0, 1), &glyph);
            CTRunGetPositions(run, CFRangeMake(0, 1), &point);
            if(glyph == mapped && point.x == 0 && point.y == 0)
            {
                m_glyph[c]   = glyph;
                m_advance[c] = CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr) + (c == ' ' ? m_word_spacing : 0);
                m_single[c]  = Simple;
            }
        }
    }
    CFRelease(line);
    return m_single[c] == Simple;
}

void LHSimpleShaper::learn(const char* text, size_t length, CTLineRef line)
{
    if(!line || length == 0 || length > kMaxLength || !enabled() || !simple_bytes(text, length))
        return;
    CFArrayRef runs = CTLineGetGlyphRuns(line);
    if(CFArrayGetCount(runs) != 1)
        return;
    auto      run  = static_cast<CTRunRef>(CFArrayGetValueAtIndex(runs, 0));
    CTFontRef font = static_cast<CTFontRef>(CFDictionaryGetValue(CTRunGetAttributes(run), kCTFontAttributeName));
    if(!font || !CFEqual(font, m_font) || CTRunGetGlyphCount(run) != CFIndex(length))
        return;
    CGGlyph glyphs[kMaxLength];
    CGPoint points[kMaxLength];
    CTRunGetGlyphs(run, CFRangeMake(0, 0), glyphs);
    CTRunGetPositions(run, CFRangeMake(0, 0), points);
    const CGFloat width = CTLineGetTypographicBounds(line, nullptr, nullptr, nullptr);
    const auto*   s     = reinterpret_cast<const unsigned char*>(text);

    std::lock_guard<std::mutex> lock(m_mutex);
    for(size_t i = 0; i < length; i++)
    {
        if(m_single[s[i]] == Unknown)
            learn_single(s[i]);
        if(m_single[s[i]] != Simple || glyphs[i] != m_glyph[s[i]] || points[i].y != 0)
            return;
    }
    if(points[0].x != 0 || std::fabs(width - points[length - 1].x - m_advance[s[length - 1]]) > kEpsilon)
        return;
    bool consistent = true;
    for(size_t i = 0; i + 1 < length; i++)
    {
        const uint16_t key  = PairKey(s[i], s[i + 1]);
        const CGFloat  kern = points[i + 1].x - points[i].x - m_advance[s[i]];
        if(m_contextual.count(key))
        {
            consistent = false;
            continue;
        }
        auto known = m_kern.find(key);
        if(known == m_kern.end())
        {
            if(m_kern.size() < 16384)
            {
                m_kern.emplace(key, kern);
                gLearned.fetch_add(1, std::memory_order_relaxed);
            }
            else
                consistent = false;
        }
        else if(std::fabs(known->second - kern) > kEpsilon)
        {
            m_kern.erase(known);
            m_contextual.insert(key);
            gRejected.fetch_add(1, std::memory_order_relaxed);
            consistent = false;
        }
    }
    if(!consistent)
        return;
    if(m_triples.size() > 65536)
        m_triples.clear();
    for(size_t i = 0; i + 2 < length; i++)
        m_triples.insert(triple_key(s[i], s[i + 1], s[i + 2]));
}

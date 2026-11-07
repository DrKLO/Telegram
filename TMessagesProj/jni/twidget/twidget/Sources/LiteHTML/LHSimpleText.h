#pragma once

#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include <atomic>
#include <cstddef>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <vector>

class LHSimpleShaper
{
  public:
    static constexpr size_t kMaxLength = 64;

    struct Stats
    {
        size_t hits = 0, misses = 0, learned = 0, rejected = 0;
    };

    explicit LHSimpleShaper(CTFontRef font, CFDictionaryRef attrs, CGFloat wordSpacing = 0) : m_font(font), m_attrs(attrs), m_word_spacing(wordSpacing) {}

    bool shape(const char* text, size_t length, CGGlyph* glyphs, CGFloat* xs, CGFloat* width);
    void learn(const char* text, size_t length, CTLineRef line);
    void clear();

    static bool enabled();
    static void set_enabled(bool enabled);
    static Stats stats();
    static void reset_stats();

  private:
    enum : uint8_t
    {
        Unknown = 0,
        Simple  = 1,
        Complex = 2,
    };

    static bool simple_bytes(const char* text, size_t length);
    bool        learn_single(unsigned char c);
    static uint32_t triple_key(unsigned char a, unsigned char b, unsigned char c)
    {
        return uint32_t(a) << 16 | uint32_t(b) << 8 | c;
    }

    CTFontRef       m_font;
    CFDictionaryRef m_attrs;
    CGFloat         m_word_spacing;
    std::mutex      m_mutex;
    uint8_t         m_single[128] = {};
    CGGlyph         m_glyph[128]  = {};
    CGFloat         m_advance[128] = {};
    std::unordered_map<uint16_t, CGFloat> m_kern;
    std::unordered_set<uint16_t>          m_contextual;
    std::unordered_set<uint32_t>          m_triples;
};

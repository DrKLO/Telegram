#include "PrismaEngine.h"

#include <algorithm>
#include <limits>
#include <set>
#include <stdexcept>

#include "SyntaxHighlighter.h"

namespace prisma_android {
namespace {

class GrammarReader {
public:
    explicit GrammarReader(std::string_view bytes) : bytes_(bytes) {}

    uint8_t readByte() {
        require(1);
        return static_cast<uint8_t>(bytes_[position_++]);
    }

    uint16_t readShort() {
        const uint16_t low = readByte();
        return static_cast<uint16_t>(low | (readByte() << 8));
    }

    std::string_view readString() {
        size_t length = readByte();
        if (length >= 254) {
            const size_t a = readByte();
            const size_t b = readByte();
            const size_t c = readByte();
            length = a | (b << 8) | (c << 16);
        }
        require(length);
        const auto value = bytes_.substr(position_, length);
        position_ += length;
        return value;
    }

    bool finished() const { return position_ == bytes_.size(); }

private:
    void require(size_t length) const {
        if (length > bytes_.size() - position_) {
            throw std::invalid_argument("Truncated grammars.dat");
        }
    }

    std::string_view bytes_;
    size_t position_ = 0;
};

// The upstream binary reader assumes that its input and all references are
// valid. Check structure before exposing a byte[] constructor through JNI.
void validateGrammars(std::string_view bytes, std::set<std::string>& names) {
    GrammarReader reader(bytes);
    const size_t patternCount = reader.readShort();
    if (patternCount == 0) {
        throw std::invalid_argument("Empty grammar table");
    }
    std::vector<size_t> insideGrammars;
    for (size_t i = 0; i < patternCount; ++i) {
        const auto pattern = reader.readString();
        const size_t firstSlash = pattern.find('/');
        const size_t lastSlash = pattern.rfind('/');
        if (firstSlash == std::string_view::npos || lastSlash <= firstSlash) {
            throw std::invalid_argument("Invalid grammar pattern");
        }
        const auto options = pattern.substr(lastSlash + 1);
        const size_t firstComma = options.find(',');
        const size_t lastComma = options.rfind(',');
        if (firstComma == std::string_view::npos || lastComma <= firstComma) {
            throw std::invalid_argument("Invalid grammar pattern options");
        }
        names.emplace(options.substr(firstComma + 1, lastComma - firstComma - 1));
        if (lastComma + 1 < options.size()) {
            size_t index = 0;
            for (char c : options.substr(lastComma + 1)) {
                if (c < '0' || c > '9' || index > 65535) {
                    throw std::invalid_argument("Invalid nested grammar index");
                }
                index = index * 10 + static_cast<size_t>(c - '0');
            }
            insideGrammars.push_back(index);
        }
    }
    const size_t grammarCount = reader.readShort();
    for (size_t index : insideGrammars) {
        if (index >= grammarCount) {
            throw std::invalid_argument("Nested grammar index out of bounds");
        }
    }
    for (size_t i = 0; i < grammarCount; ++i) {
        const size_t tokenCount = reader.readByte();
        for (size_t j = 0; j < tokenCount; ++j) {
            names.emplace(reader.readString());
            const size_t references = reader.readByte();
            for (size_t k = 0; k < references; ++k) {
                if (reader.readShort() >= patternCount) {
                    throw std::invalid_argument("Pattern index out of bounds");
                }
            }
        }
    }
    const size_t languageCount = reader.readShort();
    for (size_t i = 0; i < languageCount; ++i) {
        reader.readString();
        reader.readString();
        if (reader.readShort() >= grammarCount) {
            throw std::invalid_argument("Language grammar index out of bounds");
        }
    }
    if (!reader.finished()) {
        throw std::invalid_argument("Unexpected trailing grammar data");
    }
}

struct Utf8Text {
    std::string bytes;
    std::vector<int32_t> offsets;

    int32_t endOffset(size_t byteOffset) const {
        // A byte-oriented regex can split a UTF-8 sequence. Keep spans on full
        // UTF-16 code points, including both halves of a surrogate pair.
        while (byteOffset < bytes.size() &&
               (static_cast<uint8_t>(bytes[byteOffset]) & 0xC0) == 0x80) {
            ++byteOffset;
        }
        return offsets.at(byteOffset);
    }
};

Utf8Text encodeUtf8(std::u16string_view text) {
    if (text.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max())) {
        throw std::length_error("Text exceeds JNI range capacity");
    }
    Utf8Text result;
    result.bytes.reserve(text.size());
    result.offsets.reserve(text.size() + 1);
    for (size_t i = 0; i < text.size(); ++i) {
        const int32_t start = static_cast<int32_t>(i);
        uint32_t cp = text[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < text.size() && text[i + 1] >= 0xDC00 &&
            text[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (text[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        const auto append = [&](uint8_t byte) {
            result.bytes.push_back(static_cast<char>(byte));
            result.offsets.push_back(start);
        };
        if (cp < 0x80) {
            append(static_cast<uint8_t>(cp));
        } else if (cp < 0x800) {
            append(static_cast<uint8_t>(0xC0 | (cp >> 6)));
            append(static_cast<uint8_t>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            append(static_cast<uint8_t>(0xE0 | (cp >> 12)));
            append(static_cast<uint8_t>(0x80 | ((cp >> 6) & 0x3F)));
            append(static_cast<uint8_t>(0x80 | (cp & 0x3F)));
        } else {
            append(static_cast<uint8_t>(0xF0 | (cp >> 18)));
            append(static_cast<uint8_t>(0x80 | ((cp >> 12) & 0x3F)));
            append(static_cast<uint8_t>(0x80 | ((cp >> 6) & 0x3F)));
            append(static_cast<uint8_t>(0x80 | (cp & 0x3F)));
        }
    }
    result.offsets.push_back(static_cast<int32_t>(text.size()));
    return result;
}

void flatten(const TokenList& list, const Utf8Text& source, size_t start, size_t limit,
             int32_t parent, const std::unordered_map<std::string, int32_t>& names,
             std::vector<int32_t>& ranges) {
    size_t offset = start;
    for (const auto& node : list) {
        if (offset > limit || node.length() > limit - offset) {
            throw std::runtime_error("Invalid tokenizer range");
        }
        if (node.isSyntax()) {
            const auto& syntax = static_cast<const Syntax&>(node);
            // Rounding both boundaries up gives every full code point to the
            // first byte fragment, without overlapping sibling token ranges.
            const int32_t begin = source.endOffset(offset);
            const int32_t end = source.endOffset(offset + node.length());
            int32_t childParent = parent;
            if (begin < end) {
                if (ranges.size() > static_cast<size_t>(std::numeric_limits<int32_t>::max()) - 5) {
                    throw std::length_error("Too many syntax tokens");
                }
                childParent = static_cast<int32_t>(ranges.size() / 5);
                ranges.insert(ranges.end(), {begin, end, names.at(syntax.m_type),
                                             names.at(syntax.m_alias), parent});
            }
            flatten(syntax.children(), source, offset, offset + node.length(), childParent, names,
                    ranges);
        }
        offset += node.length();
    }
    if (offset != limit) throw std::runtime_error("Tokenizer changed text length");
}

}  // namespace

Engine::Engine(std::string_view grammars) {
    std::set<std::string> names;
    names.emplace("");
    validateGrammars(grammars, names);
    tokenNames_.assign(names.begin(), names.end());
    for (size_t i = 0; i < tokenNames_.size(); ++i) {
        tokenNameIds_.emplace(tokenNames_[i], static_cast<int32_t>(i));
    }
    highlighter_ = std::make_unique<SyntaxHighlighter>(std::string(grammars));
}

Engine::~Engine() = default;

std::vector<int32_t> Engine::highlight(std::u16string_view text, std::u16string_view language) {
    std::vector<int32_t> result;
    if (text.empty() || language.empty()) return result;
    const auto source = encodeUtf8(text);
    auto languageUtf8 = encodeUtf8(language).bytes;
    for (char& c : languageUtf8) {
        if (c >= 'A' && c <= 'Z') c = static_cast<char>(c + ('a' - 'A'));
    }
    // TokenList holds string_views into source.bytes. Flatten before source dies.
    const auto tokens = highlighter_->tokenize(source.bytes, languageUtf8);
    flatten(tokens, source, 0, source.bytes.size(), -1, tokenNameIds_, result);
    return result;
}

std::vector<std::string> Engine::languages() const {
    std::vector<std::string> result;
    for (const auto& item : highlighter_->languages()) result.push_back(item.first);
    return result;
}

}  // namespace prisma_android

#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

class SyntaxHighlighter;

namespace prisma_android {

// One instance is used serially. PrismaHighlighter.java enforces this.
class Engine final {
public:
    explicit Engine(std::string_view grammars);
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    // Each record is [UTF-16 start, exclusive end, type ID, alias ID, parent index].
    // Syntax nodes are emitted in preorder. Parent ranges may contain children.
    // Name IDs reference tokenNames(); zero is the empty name, root parent is -1.
    std::vector<int32_t> highlight(std::u16string_view text, std::u16string_view language);
    std::vector<std::string> languages() const;
    const std::vector<std::string>& tokenNames() const { return tokenNames_; }

private:
    std::unique_ptr<SyntaxHighlighter> highlighter_;
    std::vector<std::string> tokenNames_;
    std::unordered_map<std::string, int32_t> tokenNameIds_;
};

}  // namespace prisma_android

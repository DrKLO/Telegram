#include "Highlight.h"

#include "LanguageTree.h"

GrammarPtr::GrammarPtr(std::shared_ptr<LanguageTree> tree, size_t path)
    : m_tree(tree), m_path(path) {}

const Grammar* GrammarPtr::operator->() const { return get(); }

const Grammar* GrammarPtr::get() const {
    const auto tree = m_tree.lock();
    return tree ? tree->resolveGrammar(m_path) : nullptr;
}

PatternPtr::PatternPtr(std::shared_ptr<LanguageTree> tree, size_t path)
    : m_tree(tree), m_path(path) {}

const Pattern* PatternPtr::get() const {
    const auto tree = m_tree.lock();
    return tree ? tree->resolvePattern(m_path) : nullptr;
}

std::shared_ptr<Pattern> PatternRaw::realize() {
    return std::make_shared<Pattern>(m_regex, m_flags, m_lookbehind, m_greedy, m_alias, m_inside);
}

const Grammar* Pattern::inside() const {
    if (m_inside) {
        return m_inside->get();
    }

    return nullptr;
}

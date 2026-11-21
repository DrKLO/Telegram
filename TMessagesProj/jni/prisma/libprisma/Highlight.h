#pragma once
#include <boost/regex.hpp>
#include <cstddef>
#include <functional>
#include <iterator>
#include <map>
#include <memory>
#include <string>
#include <vector>

// Thrown out of a search that has spent its budget.
struct SearchOutOfBudget {};

// A place in the text that a search pays for moving from.
class BudgetedPosition {
public:
    using iterator_category = std::random_access_iterator_tag;
    using value_type = char;
    using difference_type = std::ptrdiff_t;
    using pointer = const char*;
    using reference = const char&;

    BudgetedPosition() = default;
    BudgetedPosition(const char* position, std::ptrdiff_t* budget)
        : m_position(position), m_budget(budget) {}

    reference operator*() const { return *m_position; }
    reference operator[](difference_type n) const { return m_position[n]; }

    BudgetedPosition& operator++() {
        pay();
        ++m_position;
        return *this;
    }
    BudgetedPosition operator++(int) {
        BudgetedPosition before = *this;
        ++*this;
        return before;
    }
    BudgetedPosition& operator--() {
        pay();
        --m_position;
        return *this;
    }
    BudgetedPosition operator--(int) {
        BudgetedPosition before = *this;
        --*this;
        return before;
    }
    BudgetedPosition& operator+=(difference_type n) {
        m_position += n;
        return *this;
    }
    BudgetedPosition& operator-=(difference_type n) {
        m_position -= n;
        return *this;
    }

    friend BudgetedPosition operator+(BudgetedPosition a, difference_type n) { return a += n; }
    friend BudgetedPosition operator+(difference_type n, BudgetedPosition a) { return a += n; }
    friend BudgetedPosition operator-(BudgetedPosition a, difference_type n) { return a -= n; }
    friend difference_type operator-(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position - b.m_position;
    }
    friend bool operator==(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position == b.m_position;
    }
    friend bool operator!=(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position != b.m_position;
    }
    friend bool operator<(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position < b.m_position;
    }
    friend bool operator>(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position > b.m_position;
    }
    friend bool operator<=(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position <= b.m_position;
    }
    friend bool operator>=(const BudgetedPosition& a, const BudgetedPosition& b) {
        return a.m_position >= b.m_position;
    }

    // Takes no effect until the next move, so it can be charged from anywhere.
    void charge(std::ptrdiff_t cost) const {
        if (m_budget) {
            *m_budget -= cost;
        }
    }

private:
    void pay() {
        if (--*m_budget < 0) {
            throw SearchOutOfBudget{};
        }
    }

    const char* m_position = nullptr;
    std::ptrdiff_t* m_budget = nullptr;
};

namespace boost {
namespace BOOST_REGEX_DETAIL_NS {

// Boost's own, but for one thing. It looks for the last count of a repeat through the counts of
// everything repeated since, and in (?:a(?:b)+)?, as the HTML tag pattern has, it goes through
// every (?:b) so far each time it tries to leave one. That takes a search over a long tag from
// the square of its length to the cube: minutes over 64 KB, which no budget of moves can tell
// from a search that is only long. Here a count knows where the run of its own repeat ends, and
// a walk that still has to be long is paid for.
template <>
class repeater_count<BudgetedPosition> {
    repeater_count** stack;
    repeater_count* next;
    repeater_count* beforeRun;
    int state_id;
    std::size_t count;
    BudgetedPosition start_pos;

    repeater_count* unwind_until(int n, repeater_count* p, int current_recursion_id,
                                 std::ptrdiff_t& walked) {
        while (p && (p->state_id != n)) {
            if (-2 - current_recursion_id == p->state_id) return 0;
            ++walked;
            p = p->beforeRun;
            if (p && (p->state_id < 0)) {
                p = unwind_until(p->state_id, p, current_recursion_id, walked);
                if (!p) return p;
                p = p->next;
            }
        }
        return p;
    }

public:
    repeater_count(repeater_count** s)
        : stack(s), next(0), beforeRun(0), state_id(-1), count(0), start_pos() {}

    repeater_count(int i, repeater_count** s, BudgetedPosition start, int current_recursion_id)
        : start_pos(start) {
        state_id = i;
        stack = s;
        next = *stack;
        *stack = this;
        beforeRun = (state_id >= 0 && next->state_id == state_id) ? next->beforeRun : next;
        if ((state_id > next->state_id) && (next->state_id >= 0)) {
            count = 0;
        } else {
            std::ptrdiff_t walked = 0;
            repeater_count* p = unwind_until(state_id, next, current_recursion_id, walked);
            start.charge(walked);
            if (p) {
                count = p->count;
                start_pos = p->start_pos;
            } else {
                count = 0;
            }
        }
    }
    ~repeater_count() {
        if (next) *stack = next;
    }

    std::size_t get_count() { return count; }
    int get_id() { return state_id; }
    std::size_t operator++() { return ++count; }
    bool check_null_repeat(const BudgetedPosition& pos, std::size_t max) {
        bool result = (count == 0) ? false : (pos == start_pos);
        if (result) {
            count = max;
        } else {
            start_pos = pos;
        }
        return result;
    }
};

}  // namespace BOOST_REGEX_DETAIL_NS
}  // namespace boost

class GrammarPtr;
class GrammarToken;
class Pattern;
class TokenList;

class LanguageTree;

struct Grammar {
    std::vector<GrammarToken> tokens;
};

class GrammarPtr {
public:
    GrammarPtr(std::shared_ptr<LanguageTree> tree, size_t path);

    const Grammar* operator->() const;
    const Grammar* get() const;

private:
    std::weak_ptr<LanguageTree> m_tree;
    size_t m_path;
};

class PatternRaw {
public:
    PatternRaw(std::string_view pattern, boost::regex_constants::syntax_option_type flags,
               bool lookbehind, bool greedy, std::string alias, std::shared_ptr<GrammarPtr> inside)
        : m_regex(std::string{pattern}),
          m_flags(flags),
          m_lookbehind(lookbehind),
          m_greedy(greedy),
          m_alias(alias),
          m_inside(inside) {}

    std::shared_ptr<Pattern> realize();

private:
    std::string m_regex;
    boost::regex_constants::syntax_option_type m_flags;
    bool m_lookbehind;
    bool m_greedy;
    std::string m_alias;
    std::shared_ptr<GrammarPtr> m_inside;
};

class Pattern {
public:
    Pattern(std::string_view pattern, boost::regex_constants::syntax_option_type flags,
            bool lookbehind, bool greedy, std::string alias, std::shared_ptr<GrammarPtr> inside)
        : m_regex(boost::regex(std::string{pattern}, flags | boost::regex_constants::optimize)),
          m_lookbehind(lookbehind),
          m_greedy(greedy),
          m_alias(alias),
          m_inside(inside) {}

    // The first match from pos on, as where it starts, its length and how much of it Prism's
    // lookbehind group took.
    bool search(size_t pos,
                std::string_view text,
                size_t& start,
                size_t& length,
                size_t& lookbehindLength) const {
        // Code takes under 4 million moves a search at 64 KB in the languages measured, Markdown's
        // title pattern the most: from every start on a line, to its end and back. The moves of a
        // search as bad as the HTML tag pattern over `<a b="<a b="...` take about 6 ns each.
        constexpr std::ptrdiff_t kSearchBudget = 24000000;

        std::ptrdiff_t budget = kSearchBudget;
        boost::match_results<BudgetedPosition> m;

        auto flags = boost::regex_constants::match_not_dot_newline;
        if (pos > 0) {
            // A search from JavaScript's lastIndex sees the text before it: ^ does not match
            // there, and \b and lookbehinds read the character before.
            flags |= boost::regex_constants::match_prev_avail;
        }
        if (!boost::regex_search(BudgetedPosition(text.data() + pos, &budget),
                                 BudgetedPosition(text.data() + text.size(), &budget), m, m_regex,
                                 flags)) {
            return false;
        }

        start = pos + m.position();
        length = m[0].length();
        lookbehindLength = m_lookbehind && m[1].matched ? m[1].length() : 0;
        return true;
    }

    bool lookbehind() const { return m_lookbehind; }

    bool greedy() const { return m_greedy; }

    std::string alias() const { return m_alias; }

    const Grammar* inside() const;

private:
    boost::regex m_regex;
    bool m_lookbehind;
    bool m_greedy;
    std::string m_alias;
    std::shared_ptr<GrammarPtr> m_inside;
};

class PatternPtr {
public:
    PatternPtr(std::shared_ptr<LanguageTree> tree, size_t path);

    const Pattern* operator->() const { return get(); }

    const Pattern* get() const;

private:
    std::weak_ptr<LanguageTree> m_tree;
    size_t m_path;
};

class GrammarToken {
public:
    GrammarToken(const std::string name, std::vector<PatternPtr> patterns)
        : m_name(name), m_patterns(std::move(patterns)) {}

    const std::string& name() const { return m_name; }

    std::vector<PatternPtr>::const_iterator cbegin() const noexcept { return m_patterns.cbegin(); }

    std::vector<PatternPtr>::const_iterator cend() const noexcept { return m_patterns.cend(); }

private:
    std::string m_name;
    const std::vector<PatternPtr> m_patterns;
};

#pragma once

#include <cstddef>
#include <unordered_set>
#include <utility>
#include <vector>

namespace lhx
{

template<class Node>
class PaintExclusionCache
{
  public:
    using Set = std::unordered_set<const Node*>;

    struct Sets
    {
        Set paint;
        Set scroll;
        Set background;
    };

    explicit PaintExclusionCache(size_t maxEntries = 64, size_t maxPointerRefs = 4096) :
        m_maxEntries(maxEntries), m_maxPointerRefs(maxPointerRefs)
    {
    }

    const Sets* find(const Node* root, bool snapshot) const
    {
        const size_t index = indexOf(root, snapshot);
        return index == m_entries.size() ? nullptr : &m_entries[index].sets;
    }

    bool store(const Node* root, bool snapshot, const Sets& sets)
    {
        return store(root, snapshot, sets.paint, sets.scroll, sets.background);
    }

    bool store(const Node* root, bool snapshot, const Set& paint, const Set& scroll, const Set& background)
    {
        size_t index = indexOf(root, snapshot);
        size_t count = 0;
        if(m_maxEntries == 0 || !countWithinLimit(paint, scroll, background, count))
        {
            erase(index);
            return false;
        }
        Sets copy{paint, scroll, background};
        const size_t previous = index == m_entries.size() ? 0 : m_entries[index].pointerRefs;
        if(count > m_maxPointerRefs - (m_pointerRefs - previous) ||
           (index == m_entries.size() && m_entries.size() >= m_maxEntries))
        {
            clear();
            index = m_entries.size();
        }
        if(index == m_entries.size())
        {
            m_entries.push_back({root, snapshot, count, std::move(copy)});
            m_pointerRefs += count;
        } else
        {
            m_pointerRefs -= m_entries[index].pointerRefs;
            m_entries[index].sets = std::move(copy);
            m_entries[index].pointerRefs = count;
            m_pointerRefs += count;
        }
        return true;
    }

    void clear()
    {
        m_entries.clear();
        m_pointerRefs = 0;
    }

    size_t size() const { return m_entries.size(); }
    size_t pointerCount() const { return m_pointerRefs; }

  private:
    struct Entry
    {
        const Node* root;
        bool snapshot;
        size_t pointerRefs;
        Sets sets;
    };

    size_t m_maxEntries;
    size_t m_maxPointerRefs;
    size_t m_pointerRefs = 0;
    std::vector<Entry> m_entries;

    size_t indexOf(const Node* root, bool snapshot) const
    {
        for(size_t index = 0; index < m_entries.size(); ++index)
            if(m_entries[index].root == root && m_entries[index].snapshot == snapshot)
                return index;
        return m_entries.size();
    }

    bool countWithinLimit(const Set& paint, const Set& scroll, const Set& background, size_t& count) const
    {
        count = 0;
        for(const auto* values : {&paint, &scroll, &background})
        {
            if(values->size() > m_maxPointerRefs - count)
                return false;
            count += values->size();
        }
        return true;
    }

    void erase(size_t index)
    {
        if(index == m_entries.size())
            return;
        m_pointerRefs -= m_entries[index].pointerRefs;
        m_entries.erase(m_entries.begin() + index);
    }
};

}

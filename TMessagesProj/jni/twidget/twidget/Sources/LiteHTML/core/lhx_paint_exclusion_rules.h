#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace lhx
{

class PaintExclusionRules
{
  public:
    static constexpr uint8_t paint = 1;
    static constexpr uint8_t layer = 2;
    static constexpr uint8_t background = 4;
    static constexpr uint8_t scroll = 8;

    void add(std::string attribute, std::string value, uint8_t bits)
    {
        if(bits == 0)
            return;
        for(auto& group : m_groups)
        {
            if(group.attribute == attribute)
            {
                group.bits |= bits;
                group.values[std::move(value)] |= bits;
                return;
            }
        }
        Group group{std::move(attribute), bits, {}};
        group.values.emplace(std::move(value), bits);
        m_groups.push_back(std::move(group));
    }

    void clear()
    {
        m_groups.clear();
    }

    bool active(uint8_t mask) const
    {
        for(const auto& group : m_groups)
            if(group.bits & mask)
                return true;
        return false;
    }

    template<class AttributeReader>
    uint8_t match(AttributeReader&& reader, uint8_t activeMask) const
    {
        uint8_t result = 0;
        for(const auto& group : m_groups)
        {
            if(!(group.bits & activeMask))
                continue;
            const char* value = reader(group.attribute.c_str());
            if(!value)
                continue;
            const auto found = group.values.find(value);
            if(found != group.values.end())
                result |= found->second & activeMask;
        }
        return result;
    }

  private:
    struct Group
    {
        std::string attribute;
        uint8_t bits;
        std::unordered_map<std::string, uint8_t> values;
    };

    std::vector<Group> m_groups;
};

}

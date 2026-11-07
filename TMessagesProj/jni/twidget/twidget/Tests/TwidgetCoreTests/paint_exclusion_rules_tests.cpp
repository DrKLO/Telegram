#include "../../Sources/LiteHTML/core/lhx_paint_exclusion_rules.h"

#include <array>
#include <cstdio>
#include <set>

namespace
{

using Rules = lhx::PaintExclusionRules;

struct Rule
{
    std::string attribute;
    std::string value;
    uint8_t bits;
};

struct Node
{
    std::unordered_map<std::string, std::string> attributes;
    std::vector<size_t> children;
};

using Results = std::array<std::set<size_t>, 4>;

size_t checks = 0;
size_t failures = 0;

void check(bool condition, const char* name)
{
    ++checks;
    if(!condition)
    {
        if(failures < 20)
            std::printf("FAIL %s\n", name);
        ++failures;
    }
}

uint8_t match(const Rules& rules, const Node& node, uint8_t mask, size_t& reads)
{
    return rules.match([&](const char* attribute) -> const char* {
        ++reads;
        const auto found = node.attributes.find(attribute);
        return found == node.attributes.end() ? nullptr : found->second.c_str();
    }, mask);
}

void referenceWalk(const std::vector<Node>& tree, size_t root, const std::vector<Rule>& rules,
                   uint8_t category, std::set<size_t>& result)
{
    const auto& node = tree[root];
    for(const auto& rule : rules)
    {
        if(!(rule.bits & category))
            continue;
        const auto found = node.attributes.find(rule.attribute);
        if(found != node.attributes.end() && found->second == rule.value)
            result.insert(root);
    }
    for(size_t child : node.children)
        referenceWalk(tree, child, rules, category, result);
}

Results reference(const std::vector<Node>& tree, size_t root, const std::vector<Rule>& rules, uint8_t mask)
{
    Results result;
    for(size_t category = 0; category < result.size(); ++category)
    {
        const uint8_t bit = uint8_t(1u << category);
        if(mask & bit)
            referenceWalk(tree, root, rules, bit, result[category]);
    }
    return result;
}

void combinedWalk(const std::vector<Node>& tree, size_t root, const Rules& rules, uint8_t mask,
                  Results& result, size_t& reads)
{
    const uint8_t bits = match(rules, tree[root], mask, reads);
    for(size_t category = 0; category < result.size(); ++category)
        if(bits & (1u << category))
            result[category].insert(root);
    for(size_t child : tree[root].children)
        combinedWalk(tree, child, rules, mask, result, reads);
}

Results combined(const std::vector<Node>& tree, size_t root, const Rules& rules, uint8_t mask, size_t& reads)
{
    Results result;
    combinedWalk(tree, root, rules, mask, result, reads);
    return result;
}

void masksAndDuplicates()
{
    Rules rules;
    Node node{{{"data-id", "shared"}, {"other", "second"}}, {}};
    size_t reads = 0;
    check(!rules.active(15) && match(rules, node, 15, reads) == 0 && reads == 0, "empty rules do not read attributes");
    rules.add("data-id", "shared", Rules::paint);
    rules.add("data-id", "shared", Rules::paint);
    rules.add("data-id", "shared", Rules::layer);
    rules.add("data-id", "shared", Rules::background);
    rules.add("data-id", "shared", Rules::scroll);
    rules.add("unused", "anything", 0);
    for(uint8_t mask = 0; mask < 16; ++mask)
    {
        reads = 0;
        check(rules.active(mask) == (mask != 0), "active follows requested categories");
        check(match(rules, node, mask, reads) == mask, "duplicate values combine all category bits");
        check(reads == (mask ? 1u : 0u), "shared attribute read once for every active mode");
    }
    rules.add("other", "second", Rules::paint | Rules::scroll);
    reads = 0;
    check(match(rules, node, Rules::layer | Rules::background, reads) == (Rules::layer | Rules::background) && reads == 1,
          "inactive attribute groups are not queried");
    reads = 0;
    check(match(rules, node, Rules::paint | Rules::scroll, reads) == (Rules::paint | Rules::scroll) && reads == 2,
          "matches across distinct attributes combine exactly");
    rules.clear();
    reads = 0;
    check(!rules.active(15) && match(rules, node, 15, reads) == 0 && reads == 0, "clear removes all groups and activity");
    rules.add("other", "second", Rules::background);
    reads = 0;
    check(match(rules, node, 15, reads) == Rules::background && reads == 1, "rules reusable after clear");
}

void exactStrings()
{
    Rules rules;
    std::string attribute = "id", value = "01";
    rules.add(attribute, value, Rules::paint);
    attribute = "changed";
    value = "1";
    rules.add("id", "1", Rules::scroll);
    rules.add("", "", Rules::background);
    rules.add("utf8", "данные", Rules::layer);
    Node node{{{"id", "01"}, {"", ""}, {"utf8", "данные"}}, {}};
    size_t reads = 0;
    check(match(rules, node, 15, reads) == (Rules::paint | Rules::background | Rules::layer),
          "strings are owned and exact including empty and UTF-8 values");
    node.attributes["id"] = "1";
    reads = 0;
    check(match(rules, node, 15, reads) == (Rules::scroll | Rules::background | Rules::layer), "numeric-looking strings remain distinct");
    node.attributes.erase("");
    reads = 0;
    check(match(rules, node, 15, reads) == (Rules::scroll | Rules::layer), "missing value differs from empty value");
    node.attributes["id"] = "01 ";
    node.attributes["utf8"] = "ДАННЫЕ";
    reads = 0;
    check(match(rules, node, 15, reads) == 0, "matching does not trim or change case");
}

uint32_t next(uint32_t& state)
{
    state = state * 1664525u + 1013904223u;
    return state;
}

void separateWalkOracle()
{
    const std::array<std::string, 5> attributes{"data-id", "class", "data-other", "id", ""};
    const std::array<std::string, 7> values{"shared", "x", "", "01", "1", "данные", "long-attribute-value-0123456789"};
    uint32_t random = 1729;
    Rules compiled;
    for(size_t trial = 0; trial < 200; ++trial)
    {
        compiled.clear();
        std::vector<Rule> rules;
        for(size_t index = 0; index < trial % 31; ++index)
        {
            Rule rule{attributes[next(random) % attributes.size()], values[next(random) % values.size()], uint8_t(next(random) % 16)};
            compiled.add(rule.attribute, rule.value, rule.bits);
            rules.push_back(rule);
            if(index % 3 == 0)
            {
                compiled.add(rule.attribute, rule.value, rule.bits);
                rules.push_back(rule);
            }
        }
        std::vector<Node> tree(37);
        for(size_t node = 0; node < tree.size(); ++node)
        {
            for(const auto& attribute : attributes)
                if(next(random) % 3 != 0)
                    tree[node].attributes.emplace(attribute, values[next(random) % values.size()]);
            for(size_t child : {node * 2 + 1, node * 2 + 2})
                if(child < tree.size())
                    tree[node].children.push_back(child);
        }
        for(uint8_t mask = 0; mask < 16; ++mask)
        {
            bool active = false;
            for(const auto& rule : rules)
                active = active || (rule.bits & mask);
            check(compiled.active(mask) == active, "activity matches independent rules");
            for(size_t root : {size_t(0), size_t(1), size_t(2), size_t(7), size_t(18)})
            {
                size_t reads = 0;
                check(combined(tree, root, compiled, mask, reads) == reference(tree, root, rules, mask),
                      "combined matching equals independent category subtree walks");
                if(mask == 0 || !active)
                    check(reads == 0, "inactive oracle cases perform no attribute reads");
            }
        }
    }
}

void sharedAttributeReads()
{
    std::vector<Node> tree(120);
    for(size_t node = 0; node < tree.size(); ++node)
    {
        tree[node].attributes.emplace("data-id", "shared");
        if(node + 1 < tree.size())
            tree[node].children.push_back(node + 1);
    }
    Rules rules;
    std::vector<Rule> source;
    for(uint8_t bit : {Rules::paint, Rules::layer, Rules::background, Rules::scroll})
    {
        rules.add("data-id", "shared", bit);
        source.push_back({"data-id", "shared", bit});
    }
    size_t normalReads = 0, snapshotReads = 0;
    check(combined(tree, 0, rules, 15, normalReads) == reference(tree, 0, source, 15), "normal shared-attribute tree matches separate walks");
    check(combined(tree, 0, rules, Rules::paint | Rules::scroll, snapshotReads) ==
          reference(tree, 0, source, Rules::paint | Rules::scroll), "snapshot shared-attribute tree matches separate walks");
    check(normalReads == tree.size() && snapshotReads == tree.size(), "shared attribute read once per node in both modes");
    std::printf("Shared attribute reads: normal %zu versus %zu separate; snapshot %zu versus %zu separate\n",
                normalReads, tree.size() * 4, snapshotReads, tree.size() * 2);
}

}

int main()
{
    masksAndDuplicates();
    exactStrings();
    separateWalkOracle();
    sharedAttributeReads();
    std::printf("%zu checks, %zu failures\n", checks, failures);
    return failures ? 1 : 0;
}

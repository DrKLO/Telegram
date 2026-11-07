#include "../../Sources/LiteHTML/core/lhx_paint_exclusion_cache.h"

#include <array>
#include <cstdio>

namespace
{

struct Node
{
    int value = 0;
};

using Cache = lhx::PaintExclusionCache<Node>;

size_t checks = 0;
size_t failures = 0;

void check(bool condition, const char* name)
{
    ++checks;
    if(!condition)
    {
        std::printf("FAIL %s\n", name);
        ++failures;
    }
}

bool equal(const Cache::Sets* actual, const Cache::Sets& expected)
{
    return actual && actual->paint == expected.paint && actual->scroll == expected.scroll &&
           actual->background == expected.background;
}

void isolationAndModes()
{
    std::array<Node, 8> nodes;
    Cache cache;
    Cache::Sets ordinary{{&nodes[1], &nodes[1], &nodes[2]}, {&nodes[3]}, {&nodes[4]}};
    const Cache::Sets original = ordinary;
    Cache::Sets snapshot{{&nodes[1]}, {&nodes[3], &nodes[4]}, {}};
    check(!cache.find(&nodes[0], false), "empty cache misses");
    check(cache.store(&nodes[0], false, ordinary), "ordinary sets stored");
    check(cache.pointerCount() == 4, "duplicate values count once per set");
    check(cache.store(&nodes[0], true, snapshot), "snapshot sets stored separately");
    ordinary.paint.clear();
    ordinary.scroll.insert(&nodes[7]);
    ordinary.background.clear();
    check(equal(cache.find(&nodes[0], false), original), "mutable live sets cannot change canonical sets");
    check(equal(cache.find(&nodes[0], true), snapshot), "snapshot mode has independent canonical sets");
    check(!cache.find(&nodes[7], false), "different root misses");
    auto live = *cache.find(&nodes[0], true);
    live.scroll.clear();
    live.paint.insert(&nodes[7]);
    check(equal(cache.find(&nodes[0], true), snapshot), "temporary capture mutations cannot leak into cache");
    check(cache.size() == 2 && cache.pointerCount() == 7, "both modes counted");
    Cache::Sets empty;
    check(cache.store(nullptr, false, empty) && equal(cache.find(nullptr, false), empty), "null root and empty result are valid keys");
    cache.clear();
    check(cache.size() == 0 && cache.pointerCount() == 0 && !cache.find(&nodes[0], false) && !cache.find(nullptr, false),
          "clear removes every mode and count");
    check(cache.store(&nodes[0], false, original) && equal(cache.find(&nodes[0], false), original), "cache reusable after clear");
}

void replacement()
{
    std::array<Node, 8> nodes;
    Cache cache(3, 8);
    Cache::Sets first{{&nodes[1], &nodes[2]}, {}, {}};
    Cache::Sets second{{}, {&nodes[3]}, {&nodes[4]}};
    Cache::Sets third{{&nodes[1], &nodes[2], &nodes[3]}, {&nodes[4]}, {&nodes[5]}};
    check(cache.store(&nodes[0], false, first) && cache.store(&nodes[6], false, second), "replacement setup stored");
    check(cache.store(&nodes[0], false, third), "larger replacement stored");
    check(cache.size() == 2 && cache.pointerCount() == 7 && equal(cache.find(&nodes[0], false), third) &&
          equal(cache.find(&nodes[6], false), second), "replacement subtracts old pointer count");
    check(cache.store(&nodes[0], false, first) && cache.pointerCount() == 4, "smaller replacement reduces pointer count");
    check(cache.store(&nodes[0], false, *cache.find(&nodes[0], false)) && equal(cache.find(&nodes[0], false), first),
          "same-key aliased replacement is safe");
    check(cache.store(&nodes[7], true, *cache.find(&nodes[0], false)) && equal(cache.find(&nodes[7], true), first),
          "aliased new key is copied independently");
    check(cache.store(&nodes[0], false, {}) && cache.size() == 3 && cache.pointerCount() == 4 &&
          equal(cache.find(&nodes[0], false), {}), "empty replacement remains a cache hit");
}

void limits()
{
    std::array<Node, 10> nodes;
    Cache::Sets one{{&nodes[1]}, {}, {}};
    Cache::Sets two{{&nodes[1], &nodes[2]}, {}, {}};
    Cache::Sets three{{&nodes[1]}, {&nodes[1]}, {&nodes[1]}};
    Cache::Sets four{{&nodes[1], &nodes[2]}, {&nodes[3]}, {&nodes[4]}};
    Cache noEntries(0, 10);
    check(!noEntries.store(&nodes[0], false, {}) && !noEntries.store(&nodes[0], false, one) && noEntries.size() == 0,
          "zero entry capacity disables cache");
    Cache noPointers(2, 0);
    check(noPointers.store(&nodes[0], false, {}) && !noPointers.store(&nodes[0], true, one) && noPointers.pointerCount() == 0,
          "zero pointer capacity accepts only empty sets");
    Cache entryBound(2, 10);
    check(entryBound.store(&nodes[0], false, one) && entryBound.store(&nodes[0], true, two), "entry limit reached exactly");
    check(entryBound.store(&nodes[5], false, *entryBound.find(&nodes[0], false)), "aliased insertion across eviction succeeds");
    check(entryBound.size() == 1 && entryBound.pointerCount() == 1 && equal(entryBound.find(&nodes[5], false), one) &&
          !entryBound.find(&nodes[0], false) && !entryBound.find(&nodes[0], true), "entry eviction clears old roots and modes");
    Cache pointerBound(4, 3);
    check(pointerBound.store(&nodes[0], false, three) && pointerBound.pointerCount() == 3,
          "same pointer in different sets counts as separate references");
    check(pointerBound.store(&nodes[5], false, one) && pointerBound.size() == 1 && pointerBound.pointerCount() == 1 &&
          !pointerBound.find(&nodes[0], false), "total pointer overflow evicts bounded cache");
    check(!pointerBound.store(&nodes[6], false, four) && equal(pointerBound.find(&nodes[5], false), one),
          "oversized new entry rejected without evicting valid entries");
    check(!pointerBound.store(&nodes[5], false, four) && !pointerBound.find(&nodes[5], false) && pointerBound.pointerCount() == 0,
          "oversized replacement cannot leave stale canonical result");
    Cache replacementBound(4, 3);
    check(replacementBound.store(&nodes[0], false, one) && replacementBound.store(&nodes[5], false, one), "replacement budget setup");
    check(replacementBound.store(&nodes[0], false, three) && replacementBound.size() == 1 &&
          replacementBound.pointerCount() == 3 && equal(replacementBound.find(&nodes[0], false), three) &&
          !replacementBound.find(&nodes[5], false), "larger replacement evicts when total budget exceeded");
}

void defaultLimits()
{
    std::array<Node, 4100> nodes;
    Cache entries;
    for(size_t index = 0; index < 64; ++index)
        check(entries.store(&nodes[index], false, {}), "default entry inserted");
    check(entries.size() == 64, "default entry limit is 64");
    check(entries.store(&nodes[64], false, {}) && entries.size() == 1 && !entries.find(&nodes[0], false),
          "default entry limit enforced");
    Cache pointers;
    Cache::Sets maximum;
    for(size_t index = 0; index < 4096; ++index)
        maximum.paint.insert(&nodes[index]);
    check(pointers.store(&nodes[4097], false, maximum) && pointers.pointerCount() == 4096, "default pointer limit reached exactly");
    maximum.scroll.insert(&nodes[0]);
    check(!pointers.store(&nodes[4098], false, maximum) && pointers.pointerCount() == 4096, "default pointer limit rejects oversized result");
    Cache::Sets one{{&nodes[0]}, {}, {}};
    check(pointers.store(&nodes[4099], false, one) && pointers.pointerCount() == 1 && !pointers.find(&nodes[4097], false),
          "default total pointer limit enforced");
}

void separateSetArguments()
{
    std::array<Node, 10> nodes;
    Cache cache(2, 6);
    Cache::Sets first{{&nodes[1]}, {&nodes[2]}, {&nodes[3]}};
    Cache::Sets second{{&nodes[4]}, {}, {&nodes[5]}};
    check(cache.store(&nodes[0], false, first.paint, first.scroll, first.background), "separate sets stored");
    const Cache::Sets original = first;
    first.paint.clear();
    first.scroll.clear();
    first.background.clear();
    check(equal(cache.find(&nodes[0], false), original), "separate arguments copied independently");
    const auto* same = cache.find(&nodes[0], false);
    check(cache.store(&nodes[0], false, same->paint, same->scroll, same->background) &&
          equal(cache.find(&nodes[0], false), original), "separate arguments allow aliased same-key replacement");
    check(cache.store(&nodes[6], true, second.paint, second.scroll, second.background), "separate sets fill entry capacity");
    const auto* firstCached = cache.find(&nodes[0], false);
    const auto* secondCached = cache.find(&nodes[6], true);
    Cache::Sets mixed{firstCached->paint, firstCached->scroll, secondCached->background};
    check(cache.store(&nodes[7], false, firstCached->paint, firstCached->scroll, secondCached->background),
          "separate arguments allow aliases from multiple entries across eviction");
    check(cache.size() == 1 && cache.pointerCount() == 3 && equal(cache.find(&nodes[7], false), mixed) &&
          !cache.find(&nodes[0], false) && !cache.find(&nodes[6], true), "all aliased sets copied before cache eviction");
    Cache::Set oversized;
    for(size_t index = 0; index < 7; ++index)
        oversized.insert(&nodes[index]);
    check(!cache.store(&nodes[8], false, oversized, first.scroll, first.background) &&
          equal(cache.find(&nodes[7], false), mixed), "oversized separate arguments preserve unrelated entries");
    check(!cache.store(&nodes[7], false, oversized, first.scroll, first.background) &&
          !cache.find(&nodes[7], false) && cache.pointerCount() == 0, "oversized separate replacement removes stale entry");
    Cache::Set empty;
    check(cache.store(nullptr, true, empty, empty, empty) && equal(cache.find(nullptr, true), {}),
          "separate empty arguments cache an empty result");
}

}

int main()
{
    isolationAndModes();
    replacement();
    limits();
    defaultLimits();
    separateSetArguments();
    std::printf("%zu checks, %zu failures\n", checks, failures);
    return failures ? 1 : 0;
}

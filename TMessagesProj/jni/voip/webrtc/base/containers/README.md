# base/containers library

[TOC]

## What goes here

This directory contains some STL-like containers.

Things should be moved here that are generally applicable across the code base.
Don't add things here just because you need them in one place and think others
may someday want something similar. You can put specialized containers in
your component's directory and we can promote them here later if we feel there
is broad applicability.

### Design and naming

Fundamental [//base principles](../README.md#design-and-naming) apply, i.e.:

Containers should adhere as closely to STL as possible. Functions and behaviors
not present in STL should only be added when they are related to the specific
data structure implemented by the container.

For STL-like containers our policy is that they should use STL-like naming even
when it may conflict with the style guide. So functions and class names should
be lower case with underscores. Non-STL-like classes and functions should use
Google naming. Be sure to use the base namespace.

## Map and set selection

### Usage advice

*   Generally avoid `std::unordered_set` and `std::unordered_map`. In the common
    case, query performance is unlikely to be sufficiently higher than
    `std::map` to make a difference, insert performance is slightly worse, and
    the memory overhead is high. This makes sense mostly for large tables where
    you expect a lot of lookups.

*   Most maps and sets in Chrome are small and contain objects that can be moved
    efficiently. In this case, consider `base::flat_map` and `base::flat_set`.
    You need to be aware of the maximum expected size of the container since
    individual inserts and deletes are O(n), giving O(n^2) construction time for
    the entire map. But because it avoids mallocs in most cases, inserts are
    better or comparable to other containers even for several dozen items, and
    efficiently-moved types are unlikely to have performance problems for most
    cases until you have hundreds of items. If your container can be constructed
    in one shot, the constructor from vector gives O(n log n) construction times
    and it should be strictly better than a `std::map`.

    Conceptually inserting a range of n elements into a `base::flat_map` or
    `base::flat_set` behaves as if insert() was called for each individually
    element. Thus in case the input range contains repeated elements, only the
    first one of these duplicates will be inserted into the container. This
    behaviour applies to construction from a range as well.

*   `base::small_map` has better runtime memory usage without the poor mutation
    performance of large containers that `base::flat_map` has. But this
    advantage is partially offset by additional code size. Prefer in cases where
    you make many objects so that the code/heap tradeoff is good.

*   Use `std::map` and `std::set` if you can't decide. Even if they're not
    great, they're unlikely to be bad or surprising.

### Map and set details

Sizes are on 64-bit platforms. Stable iterators aren't invalidated when the
container is mutated.

| Container                                  | Empty size            | Per-item overhead | Stable iterators? |
|:------------------------------------------ |:--------------------- |:----------------- |:----------------- |
| `std::map`, `std::set`                     | 16 bytes              | 32 bytes          | Yes               |
| `std::unordered_map`, `std::unordered_set` | 128 bytes             | 16 - 24 bytes     | No                |
| `base::flat_map`, `base::flat_set`         | 24 bytes              | 0 (see notes)     | No                |
| `base::small_map`                          | 24 bytes (see notes)  | 32 bytes          | No                |

**Takeaways:** `std::unordered_map` and `std::unordered_set` have high
overhead for small container sizes, so prefer these only for larger workloads.

Code size comparisons for a block of code (see appendix) on Windows using
strings as keys.

| Container            | Code size  |
|:-------------------- |:---------- |
| `std::unordered_map` | 1646 bytes |
| `std::map`           | 1759 bytes |
| `base::flat_map`     | 1872 bytes |
| `base::small_map`    | 2410 bytes |

**Takeaways:** `base::small_map` generates more code because of the inlining of
both brute-force and red-black tree searching. This makes it less attractive
for random one-off uses. But if your code is called frequently, the runtime
memory benefits will be more important. The code sizes of the other maps are
close enough it's not worth worrying about.

### std::map and std::set

A red-black tree. Each inserted item requires the memory allocation of a node
on the heap. Each node contains a left pointer, a right pointer, a parent
pointer, and a "color" for the red-black tree (32 bytes per item on 64-bit
platforms).

### std::unordered\_map and std::unordered\_set

A hash table. Implemented on Windows as a `std::vector` + `std::list` and in libc++
as the equivalent of a `std::vector` + a `std::forward_list`. Both implementations
allocate an 8-entry hash table (containing iterators into the list) on
initialization, and grow to 64 entries once 8 items are inserted. Above 64
items, the size doubles every time the load factor exceeds 1.

The empty size is `sizeof(std::unordered_map)` = 64 + the initial hash table
size which is 8 pointers. The per-item overhead in the table above counts the
list node (2 pointers on Windows, 1 pointer in libc++), plus amortizes the hash
table assuming a 0.5 load factor on average.

In a microbenchmark on Windows, inserts of 1M integers into a
`std::unordered_set` took 1.07x the time of `std::set`, and queries took 0.67x
the time of `std::set`. For a typical 4-entry set (the statistical mode of map
sizes in the browser), query performance is identical to `std::set` and
`base::flat_set`. On ARM, `std::unordered_set` performance can be worse because
integer division to compute the bucket is slow, and a few "less than" operations
can be faster than computing a hash depending on the key type. The takeaway is
that you should not default to using unordered maps because "they're faster."

### base::flat\_map and base::flat\_set

A sorted `std::vector`. Seached via binary search, inserts in the middle require
moving elements to make room. Good cache locality. For large objects and large
set sizes, `std::vector`'s doubling-when-full strategy can waste memory.

Supports efficient construction from a vector of items which avoids the O(n^2)
insertion time of each element separately.

The per-item overhead will depend on the underlying `std::vector`'s reallocation
strategy and the memory access pattern. Assuming items are being linearly added,
one would expect it to be 3/4 full, so per-item overhead will be 0.25 *
sizeof(T).

`flat_set` and `flat_map` support a notion of transparent comparisons.
Therefore you can, for example, lookup `base::StringPiece` in a set of
`std::strings` without constructing a temporary `std::string`. This
functionality is based on C++14 extensions to the `std::set`/`std::map`
interface.

You can find more information about transparent comparisons in [the `less<void>`
documentation](https://en.cppreference.com/w/cpp/utility/functional/less_void).

Example, smart pointer set:

```cpp
// Declare a type alias using base::UniquePtrComparator.
template <typename T>
using UniquePtrSet = base::flat_set<std::unique_ptr<T>,
                                    base::UniquePtrComparator>;

// ...
// Collect data.
std::vector<std::unique_ptr<int>> ptr_vec;
ptr_vec.reserve(5);
std::generate_n(std::back_inserter(ptr_vec), 5, []{
  return std::make_unique<int>(0);
});

// Construct a set.
UniquePtrSet<int> ptr_set(std::move(ptr_vec));

// Use raw pointers to lookup keys.
int* ptr = ptr_set.begin()->get();
EXPECT_TRUE(ptr_set.find(ptr) == ptr_set.begin());
```

Example `flat_map<std::string, int>`:

```cpp
base::flat_map<std::string, int> str_to_int({{"a", 1}, {"c", 2},{"b", 2}});

// Does not construct temporary strings.
str_to_int.find("c")->second = 3;
str_to_int.erase("c");
EXPECT_EQ(str_to_int.end(), str_to_int.find("c")->second);

// NOTE: This does construct a temporary string. This happens since if the
// item is not in the container, then it needs to be constructed, which is
// something that transparent comparators don't have to guarantee.
str_to_int["c"] = 3;
```

### base::small\_map

A small inline buffer that is brute-force searched that overflows into a full
`std::map` or `std::unordered_map`. This gives the memory benefit of
`base::flat_map` for small data sizes without the degenerate insertion
performance for large container sizes.

Since instantiations require both code for a `std::map` and a brute-force search
of the inline container, plus a fancy iterator to cover both cases, code size
is larger.

The initial size in the above table is assuming a very small inline table. The
actual size will be `sizeof(int) + min(sizeof(std::map), sizeof(T) *
inline_size)`.

## Deque

### Usage advice

Chromium code should always use `base::circular_deque` or `base::queue` in
preference to `std::deque` or `std::queue` due to memory usage and platform
variation.

The `base::circular_deque` implementation (and the `base::queue` which uses it)
provide performance consistent across platforms that better matches most
programmer's expectations on performance (it doesn't waste as much space as
libc++ and doesn't do as many heap allocations as MSVC). It also generates less
code tham `std::queue`: using it across the code base saves several hundred
kilobytes.

Since `base::deque` does not have stable iterators and it will move the objects
it contains, it may not be appropriate for all uses. If you need these,
consider using a `std::list` which will provide constant time insert and erase.

### std::deque and std::queue

The implementation of `std::deque` varies considerably which makes it hard to
reason about. All implementations use a sequence of data blocks referenced by
an array of pointers. The standard guarantees random access, amortized
constant operations at the ends, and linear mutations in the middle.

In Microsoft's implementation, each block is the smaller of 16 bytes or the
size of the contained element. This means in practice that every expansion of
the deque of non-trivial classes requires a heap allocation. libc++ (on Android
and Mac) uses 4K blocks which eliminates the problem of many heap allocations,
but generally wastes a large amount of space (an Android analysis revealed more
than 2.5MB wasted space from deque alone, resulting in some optimizations).
libstdc++ uses an intermediate-size 512-byte buffer.

Microsoft's implementation never shrinks the deque capacity, so the capacity
will always be the maximum number of elements ever contained. libstdc++
deallocates blocks as they are freed. libc++ keeps up to two empty blocks.

### base::circular_deque and base::queue

A deque implemented as a circular buffer in an array. The underlying array will
grow like a `std::vector` while the beginning and end of the deque will move
around. The items will wrap around the underlying buffer so the storage will
not be contiguous, but fast random access iterators are still possible.

When the underlying buffer is filled, it will be reallocated and the constents
moved (like a `std::vector`). The underlying buffer will be shrunk if there is
too much wasted space (_unlike_ a `std::vector`). As a result, iterators are
not stable across mutations.

## Stack

`std::stack` is like `std::queue` in that it is a wrapper around an underlying
container. The default container is `std::deque` so everything from the deque
section applies.

Chromium provides `base/containers/stack.h` which defines `base::stack` that
should be used in preference to `std::stack`. This changes the underlying
container to `base::circular_deque`. The result will be very similar to
manually specifying a `std::vector` for the underlying implementation except
that the storage will shrink when it gets too empty (vector will never
reallocate to a smaller size).

Watch out: with some stack usage patterns it's easy to depend on unstable
behavior:

```cpp
base::stack<Foo> stack;
for (...) {
  Foo& current = stack.top();
  DoStuff();  // May call stack.push(), say if writing a parser.
  current.done = true;  // Current may reference deleted item!
}
```

## Safety

Code throughout Chromium, running at any level of privilege, may directly or
indirectly depend on these containers. Much calling code implicitly or
explicitly assumes that these containers are safe, and won't corrupt memory.
Unfortunately, [such assumptions have not always proven
true](https://bugs.chromium.org/p/chromium/issues/detail?id=817982).

Therefore, we are making an effort to ensure basic safety in these classes so
that callers' assumptions are true. In particular, we are adding bounds checks,
arithmetic overflow checks, and checks for internal invariants to the base
containers where necessary. Here, safety means that the implementation will
`CHECK`.

As of 8 August 2018, we have added checks to the following classes:

- `base::StringPiece`
- `base::span`
- `base::Optional`
- `base::RingBuffer`
- `base::small_map`

Ultimately, all base containers will have these checks.

### Safety, completeness, and efficiency

Safety checks can affect performance at the micro-scale, although they do not
always. On a larger scale, if we can have confidence that these fundamental
classes and templates are minimally safe, we can sometimes avoid the security
requirement to sandbox code that (for example) processes untrustworthy inputs.
Sandboxing is a relatively heavyweight response to memory safety problems, and
in our experience not all callers can afford to pay it.

(However, where affordable, privilege separation and reduction remain Chrome
Security Team's first approach to a variety of safety and security problems.)

One can also imagine that the safety checks should be passed on to callers who
require safety. There are several problems with that approach:

- Not all authors of all call sites will always
  - know when they need safety
  - remember to write the checks
  - write the checks correctly
  - write the checks maximally efficiently, considering
    - space
    - time
    - object code size
- These classes typically do not document themselves as being unsafe
- Some call sites have their requirements change over time
  - Code that gets moved from a low-privilege process into a high-privilege
    process
  - Code that changes from accepting inputs from only trustworthy sources to
    accepting inputs from all sources
- Putting the checks in every call site results in strictly larger object code
  than centralizing them in the callee

Therefore, the minimal checks that we are adding to these base classes are the
most efficient and effective way to achieve the beginning of the safety that we
need. (Note that we cannot account for undefined behavior in callers.)

## Appendix

### Code for map code size comparison

This just calls insert and query a number of times, with `printf`s that prevent
things from being dead-code eliminated.

```cpp
TEST(Foo, Bar) {
  base::small_map<std::map<std::string, Flubber>> foo;
  foo.insert(std::make_pair("foo", Flubber(8, "bar")));
  foo.insert(std::make_pair("bar", Flubber(8, "bar")));
  foo.insert(std::make_pair("foo1", Flubber(8, "bar")));
  foo.insert(std::make_pair("bar1", Flubber(8, "bar")));
  foo.insert(std::make_pair("foo", Flubber(8, "bar")));
  foo.insert(std::make_pair("bar", Flubber(8, "bar")));
  auto found = foo.find("asdf");
  printf("Found is %d\n", (int)(found == foo.end()));
  found = foo.find("foo");
  printf("Found is %d\n", (int)(found == foo.end()));
  found = foo.find("bar");
  printf("Found is %d\n", (int)(found == foo.end()));
  found = foo.find("asdfhf");
  printf("Found is %d\n", (int)(found == foo.end()));
  found = foo.find("bar1");
  printf("Found is %d\n", (int)(found == foo.end()));
}
```

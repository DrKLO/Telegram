// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/hash/hash.h"

#include "base/rand_util.h"
#include "base/third_party/cityhash/city.h"
#include "build/build_config.h"

// Definition in base/third_party/superfasthash/superfasthash.c. (Third-party
// code did not come with its own header file, so declaring the function here.)
// Note: This algorithm is also in Blink under Source/wtf/StringHasher.h.
extern "C" uint32_t SuperFastHash(const char* data, int len);

namespace base {

namespace {

size_t FastHashImpl(base::span<const uint8_t> data) {
  // We use the updated CityHash within our namespace (not the deprecated
  // version from third_party/smhasher).
#if defined(ARCH_CPU_64_BITS)
  return base::internal::cityhash_v111::CityHash64(
      reinterpret_cast<const char*>(data.data()), data.size());
#else
  return base::internal::cityhash_v111::CityHash32(
      reinterpret_cast<const char*>(data.data()), data.size());
#endif
}

// Implement hashing for pairs of at-most 32 bit integer values.
// When size_t is 32 bits, we turn the 64-bit hash code into 32 bits by using
// multiply-add hashing. This algorithm, as described in
// Theorem 4.3.3 of the thesis "Über die Komplexität der Multiplikation in
// eingeschränkten Branchingprogrammmodellen" by Woelfel, is:
//
//   h32(x32, y32) = (h64(x32, y32) * rand_odd64 + rand16 * 2^16) % 2^64 / 2^32
//
// Contact danakj@chromium.org for any questions.
size_t HashInts32Impl(uint32_t value1, uint32_t value2) {
  uint64_t value1_64 = value1;
  uint64_t hash64 = (value1_64 << 32) | value2;

  if (sizeof(size_t) >= sizeof(uint64_t))
    return static_cast<size_t>(hash64);

  uint64_t odd_random = 481046412LL << 32 | 1025306955LL;
  uint32_t shift_random = 10121U << 16;

  hash64 = hash64 * odd_random + shift_random;
  size_t high_bits =
      static_cast<size_t>(hash64 >> (8 * (sizeof(uint64_t) - sizeof(size_t))));
  return high_bits;
}

// Implement hashing for pairs of up-to 64-bit integer values.
// We use the compound integer hash method to produce a 64-bit hash code, by
// breaking the two 64-bit inputs into 4 32-bit values:
// http://opendatastructures.org/versions/edition-0.1d/ods-java/node33.html#SECTION00832000000000000000
// Then we reduce our result to 32 bits if required, similar to above.
size_t HashInts64Impl(uint64_t value1, uint64_t value2) {
  uint32_t short_random1 = 842304669U;
  uint32_t short_random2 = 619063811U;
  uint32_t short_random3 = 937041849U;
  uint32_t short_random4 = 3309708029U;

  uint32_t value1a = static_cast<uint32_t>(value1 & 0xffffffff);
  uint32_t value1b = static_cast<uint32_t>((value1 >> 32) & 0xffffffff);
  uint32_t value2a = static_cast<uint32_t>(value2 & 0xffffffff);
  uint32_t value2b = static_cast<uint32_t>((value2 >> 32) & 0xffffffff);

  uint64_t product1 = static_cast<uint64_t>(value1a) * short_random1;
  uint64_t product2 = static_cast<uint64_t>(value1b) * short_random2;
  uint64_t product3 = static_cast<uint64_t>(value2a) * short_random3;
  uint64_t product4 = static_cast<uint64_t>(value2b) * short_random4;

  uint64_t hash64 = product1 + product2 + product3 + product4;

  if (sizeof(size_t) >= sizeof(uint64_t))
    return static_cast<size_t>(hash64);

  uint64_t odd_random = 1578233944LL << 32 | 194370989LL;
  uint32_t shift_random = 20591U << 16;

  hash64 = hash64 * odd_random + shift_random;
  size_t high_bits =
      static_cast<size_t>(hash64 >> (8 * (sizeof(uint64_t) - sizeof(size_t))));
  return high_bits;
}

// The random seed is used to perturb the output of base::FastHash() and
// base::HashInts() so that it is only deterministic within the lifetime of a
// process. This prevents inadvertent dependencies on the underlying
// implementation, e.g. anything that persists the hash value and expects it to
// be unchanging will break.
//
// Note: this is the same trick absl uses to generate a random seed. This is
// more robust than using base::RandBytes(), which can fail inside a sandboxed
// environment. Note that without ASLR, the seed won't be quite as random...
#if DCHECK_IS_ON()
constexpr const void* kSeed = &kSeed;
#endif

template <typename T>
T Scramble(T input) {
#if DCHECK_IS_ON()
  return HashInts64Impl(input, reinterpret_cast<uintptr_t>(kSeed));
#else
  return input;
#endif
}

}  // namespace

size_t FastHash(base::span<const uint8_t> data) {
  return Scramble(FastHashImpl(data));
}

uint32_t Hash(const void* data, size_t length) {
  // Currently our in-memory hash is the same as the persistent hash. The
  // split between in-memory and persistent hash functions is maintained to
  // allow the in-memory hash function to be updated in the future.
  return PersistentHash(data, length);
}

uint32_t Hash(const std::string& str) {
  return PersistentHash(as_bytes(make_span(str)));
}

uint32_t Hash(const string16& str) {
  return PersistentHash(as_bytes(make_span(str)));
}

uint32_t PersistentHash(span<const uint8_t> data) {
  // This hash function must not change, since it is designed to be persistable
  // to disk.
  if (data.size() > static_cast<size_t>(std::numeric_limits<int>::max())) {
    NOTREACHED();
    return 0;
  }
  return ::SuperFastHash(reinterpret_cast<const char*>(data.data()),
                         static_cast<int>(data.size()));
}

uint32_t PersistentHash(const void* data, size_t length) {
  return PersistentHash(make_span(static_cast<const uint8_t*>(data), length));
}

uint32_t PersistentHash(const std::string& str) {
  return PersistentHash(str.data(), str.size());
}

size_t HashInts32(uint32_t value1, uint32_t value2) {
  return Scramble(HashInts32Impl(value1, value2));
}

// Implement hashing for pairs of up-to 64-bit integer values.
// We use the compound integer hash method to produce a 64-bit hash code, by
// breaking the two 64-bit inputs into 4 32-bit values:
// http://opendatastructures.org/versions/edition-0.1d/ods-java/node33.html#SECTION00832000000000000000
// Then we reduce our result to 32 bits if required, similar to above.
size_t HashInts64(uint64_t value1, uint64_t value2) {
  return Scramble(HashInts64Impl(value1, value2));
}

}  // namespace base

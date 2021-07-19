# Choosing A Hash Function

> Note: this document is still very much a work-in-progress. Currently missing:
> - recommendations for hashed containers
> - recommendations for a better persistent hash
> - recommendations for a secure hash

If a hash function with unchanging output is needed, please select from one of
the unchanging forever options below.

## Non-cryptographic

name                                         | input                       | output     | unchanging forever | notes
---------------------------------------------|-----------------------------|------------|--------------------|-------
[`Hash()`][hash]                             | overloaded                  | `uint32_t` | no                 | This function is currently being updated to return `size_t`.
[`PersistentHash()`][persistenthash]         | overloaded                  | `uint32_t` | yes                | Fairly weak but widely used for persisted hashes.
[`CityHash64()`][cityhash64]                 | `base::span<const uint8_t>` | `uint64_t` | yes (note 1)       | Version 1.0.3. Has some known weaknesses.
[`CityHash64WithSeed()`][cityhash64withseed] | `base::span<const uint8_t>` | `uint64_t` | yes (note 1)       | Version 1.0.3. Has some known weaknesses.

## Cryptographic

**There are no hashes in `//base` that provide cryptographic security.**

 name                          | input         | output        | unchanging forever | notes
-------------------------------|---------------|---------------|--------------------|-------
[`MD5String()`][md5string]     | `std::string` | `std::string` | yes                | **INSECURE**
[`SHA1HashString`][sha1string] | `std::string` | `std::string` | yes                | **INSECURE**

## Deprecated

> Note: CRC32, Murmur2, and Murmur3 will be listed here.

Note 1: While CityHash is not guaranteed unchanging forever, the version used in
Chrome is pinned to version 1.0.3.

[hash]: https://cs.chromium.org/chromium/src/base/hash/hash.h?l=26
[persistenthash]: https://cs.chromium.org/chromium/src/base/hash/hash.h?l=36
[cityhash64]: https://cs.chromium.org/chromium/src/base/hash/city_v103.h?l=19
[cityhash64withseed]: https://cs.chromium.org/chromium/src/base/hash/city_v103.h?l=20
[md5string]: https://cs.chromium.org/chromium/src/base/hash/md5.h?l=74
[sha1string]: https://cs.chromium.org/chromium/src/base/hash/sha1.h?l=22

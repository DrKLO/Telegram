CityHash, a family of hash functions for strings.


Introduction
============

CityHash provides hash functions for strings.  The functions mix the
input bits thoroughly but are not suitable for cryptography.  See
"Hash Quality," below, for details on how CityHash was tested and so on.

We provide reference implementations in C++, with a friendly MIT license.

CityHash32() returns a 32-bit hash.

CityHash64() and similar return a 64-bit hash.

CityHash128() and similar return a 128-bit hash and are tuned for
strings of at least a few hundred bytes.  Depending on your compiler
and hardware, it's likely faster than CityHash64() on sufficiently long
strings.  It's slower than necessary on shorter strings, but we expect
that case to be relatively unimportant.

CityHashCrc128() and similar are variants of CityHash128() that depend
on _mm_crc32_u64(), an intrinsic that compiles to a CRC32 instruction
on some CPUs.  However, none of the functions we provide are CRCs.

CityHashCrc256() is a variant of CityHashCrc128() that also depends
on _mm_crc32_u64().  It returns a 256-bit hash.

All members of the CityHash family were designed with heavy reliance
on previous work by Austin Appleby, Bob Jenkins, and others.
For example, CityHash32 has many similarities with Murmur3a.

Performance on long strings: 64-bit CPUs
========================================
 
We are most excited by the performance of CityHash64() and its variants on
short strings, but long strings are interesting as well.

CityHash is intended to be fast, under the constraint that it hash very
well.  For CPUs with the CRC32 instruction, CRC is speedy, but CRC wasn't
designed as a hash function and shouldn't be used as one.  CityHashCrc128()
is not a CRC, but it uses the CRC32 machinery.

On a single core of a 2.67GHz Intel Xeon X5550, CityHashCrc256 peaks at about
5 to 5.5 bytes/cycle.  The other CityHashCrc functions are wrappers around
CityHashCrc256 and should have similar performance on long strings.
(CityHashCrc256 in v1.0.3 was even faster, but we decided it wasn't as thorough
as it should be.)  CityHash128 peaks at about 4.3 bytes/cycle.  The fastest
Murmur variant on that hardware, Murmur3F, peaks at about 2.4 bytes/cycle.
We expect the peak speed of CityHash128 to dominate CityHash64, which is
aimed more toward short strings or use in hash tables.

For long strings, a new function by Bob Jenkins, SpookyHash, is just
slightly slower than CityHash128 on Intel x86-64 CPUs, but noticeably
faster on AMD x86-64 CPUs.  For hashing long strings on AMD CPUs
and/or CPUs without the CRC instruction, SpookyHash may be just as
good or better than any of the CityHash variants.

Performance on short strings: 64-bit CPUs
=========================================

For short strings, e.g., most hash table keys, CityHash64 is faster than
CityHash128, and probably faster than all the aforementioned functions,
depending on the mix of string lengths.  Here are a few results from that
same hardware, where we (unrealistically) tested a single string length over
and over again:

Hash              Results
------------------------------------------------------------------------------
CityHash64 v1.0.3 7ns for 1 byte, or 6ns for 8 bytes, or 9ns for 64 bytes
Murmur2 (64-bit)  6ns for 1 byte, or 6ns for 8 bytes, or 15ns for 64 bytes
Murmur3F          14ns for 1 byte, or 15ns for 8 bytes, or 23ns for 64 bytes

We don't have CityHash64 benchmarks results for v1.1, but we expect the
numbers to be similar.

Performance: 32-bit CPUs
========================

CityHash32 is the newest variant of CityHash.  It is intended for
32-bit hardware in general but has been mostly tested on x86.  Our benchmarks
suggest that Murmur3 is the nearest competitor to CityHash32 on x86.
We don't know of anything faster that has comparable quality.  The speed rankings
in our testing: CityHash32 > Murmur3f > Murmur3a (for long strings), and
CityHash32 > Murmur3a > Murmur3f (for short strings).

Installation
============

We provide reference implementations of several CityHash functions, written
in C++.  The build system is based on autoconf.  It defaults the C++
compiler flags to "-g -O2", which is probably slower than -O3 if you are
using gcc.  YMMV.

On systems with gcc, we generally recommend:

./configure
make all check CXXFLAGS="-g -O3"
sudo make install

Or, if your system has the CRC32 instruction, and you want to build everything:

./configure --enable-sse4.2
make all check CXXFLAGS="-g -O3 -msse4.2"
sudo make install

Note that our build system doesn't try to determine the appropriate compiler
flag for enabling SSE4.2.  For gcc it is "-msse4.2".  The --enable-sse4.2
flag to the configure script controls whether citycrc.h is installed when
you "make install."  In general, picking the right compiler flags can be
tricky, and may depend on your compiler, your hardware, and even how you
plan to use the library.

For generic information about how to configure this software, please try:

./configure --help

Failing that, please work from city.cc and city*.h, as they contain all the
necessary code.


Usage
=====

The above installation instructions will produce a single library.  It will
contain CityHash32(), CityHash64(), and CityHash128(), and their variants,
and possibly CityHashCrc128(), CityHashCrc128WithSeed(), and
CityHashCrc256().  The functions with Crc in the name are declared in
citycrc.h; the rest are declared in city.h.


Limitations
===========

1) CityHash32 is intended for little-endian 32-bit code, and everything else in
the current version of CityHash is intended for little-endian 64-bit CPUs.

All functions that don't use the CRC32 instruction should work in
little-endian 32-bit or 64-bit code.  CityHash should work on big-endian CPUs
as well, but we haven't tested that very thoroughly yet.

2) CityHash is fairly complex.  As a result of its complexity, it may not
perform as expected on some compilers.  For example, preliminary reports
suggest that some Microsoft compilers compile CityHash to assembly that's
10-20% slower than it could be.


Hash Quality
============

We like to test hash functions with SMHasher, among other things.
SMHasher isn't perfect, but it seems to find almost any significant flaw.
SMHasher is available at http://code.google.com/p/smhasher/

SMHasher is designed to pass a 32-bit seed to the hash functions it tests.
No CityHash function is designed to work that way, so we adapt as follows:
For our functions that accept a seed, we use the given seed directly (padded
with zeroes); for our functions that don't accept a seed, we hash the
concatenation of the given seed and the input string.

The CityHash functions have the following flaws according to SMHasher:

(1) CityHash64: none

(2) CityHash64WithSeed: none

(3) CityHash64WithSeeds: did not test

(4) CityHash128: none

(5) CityHash128WithSeed: none

(6) CityHashCrc128: none

(7) CityHashCrc128WithSeed: none

(8) CityHashCrc256: none

(9) CityHash32: none

Some minor flaws in 32-bit and 64-bit functions are harmless, as we
expect the primary use of these functions will be in hash tables.  We
may have gone slightly overboard in trying to please SMHasher and other
similar tests, but we don't want anyone to choose a different hash function
because of some minor issue reported by a quality test.


For more information
====================

http://code.google.com/p/cityhash/

cityhash-discuss@googlegroups.com

Please feel free to send us comments, questions, bug reports, or patches.

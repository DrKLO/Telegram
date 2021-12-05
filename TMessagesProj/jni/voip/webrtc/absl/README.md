# Abseil - C++ Common Libraries

The repository contains the Abseil C++ library code. Abseil is an open-source
collection of C++ code (compliant to C++11) designed to augment the C++
standard library.

## Table of Contents

- [About Abseil](#about)
- [Quickstart](#quickstart)
- [Building Abseil](#build)
- [Codemap](#codemap)
- [License](#license)
- [Links](#links)

<a name="about"></a>
## About Abseil

Abseil is an open-source collection of C++ library code designed to augment
the C++ standard library. The Abseil library code is collected from Google's
own C++ code base, has been extensively tested and used in production, and
is the same code we depend on in our daily coding lives.

In some cases, Abseil provides pieces missing from the C++ standard; in
others, Abseil provides alternatives to the standard for special needs
we've found through usage in the Google code base. We denote those cases
clearly within the library code we provide you.

Abseil is not meant to be a competitor to the standard library; we've
just found that many of these utilities serve a purpose within our code
base, and we now want to provide those resources to the C++ community as
a whole.

<a name="quickstart"></a>
## Quickstart

If you want to just get started, make sure you at least run through the
[Abseil Quickstart](https://abseil.io/docs/cpp/quickstart). The Quickstart
contains information about setting up your development environment, downloading
the Abseil code, running tests, and getting a simple binary working.

<a name="build"></a>
## Building Abseil

[Bazel](https://bazel.build) is the official build system for Abseil,
which is supported on most major platforms (Linux, Windows, macOS, for example)
and compilers. See the [quickstart](https://abseil.io/docs/cpp/quickstart) for
more information on building Abseil using the Bazel build system.

<a name="cmake"></a>
If you require CMake support, please check the
[CMake build instructions](CMake/README.md).

## Codemap

Abseil contains the following C++ library components:

* [`base`](absl/base/) Abseil Fundamentals
  <br /> The `base` library contains initialization code and other code which
  all other Abseil code depends on. Code within `base` may not depend on any
  other code (other than the C++ standard library).
* [`algorithm`](absl/algorithm/)
  <br /> The `algorithm` library contains additions to the C++ `<algorithm>`
  library and container-based versions of such algorithms.
* [`container`](absl/container/)
  <br /> The `container` library contains additional STL-style containers,
  including Abseil's unordered "Swiss table" containers.
* [`debugging`](absl/debugging/)
  <br /> The `debugging` library contains code useful for enabling leak
  checks, and stacktrace and symbolization utilities.
* [`hash`](absl/hash/)
  <br /> The `hash` library contains the hashing framework and default hash
  functor implementations for hashable types in Abseil.
* [`memory`](absl/memory/)
  <br /> The `memory` library contains C++11-compatible versions of
  `std::make_unique()` and related memory management facilities.
* [`meta`](absl/meta/)
  <br /> The `meta` library contains C++11-compatible versions of type checks
  available within C++14 and C++17 versions of the C++ `<type_traits>` library.
* [`numeric`](absl/numeric/)
  <br /> The `numeric` library contains C++11-compatible 128-bit integers.
* [`strings`](absl/strings/)
  <br /> The `strings` library contains a variety of strings routines and
  utilities, including a C++11-compatible version of the C++17
  `std::string_view` type.
* [`synchronization`](absl/synchronization/)
  <br /> The `synchronization` library contains concurrency primitives (Abseil's
  `absl::Mutex` class, an alternative to `std::mutex`) and a variety of
  synchronization abstractions.
* [`time`](absl/time/)
  <br /> The `time` library contains abstractions for computing with absolute
  points in time, durations of time, and formatting and parsing time within
  time zones.
* [`types`](absl/types/)
  <br /> The `types` library contains non-container utility types, like a
  C++11-compatible version of the C++17 `std::optional` type.
* [`utility`](absl/utility/)
  <br /> The `utility` library contains utility and helper code.

## License

The Abseil C++ library is licensed under the terms of the Apache
license. See [LICENSE](LICENSE) for more information.

## Links

For more information about Abseil:

* Consult our [Abseil Introduction](https://abseil.io/about/intro)
* Read [Why Adopt Abseil](https://abseil.io/about/philosophy) to understand our
  design philosophy.
* Peruse our
  [Abseil Compatibility Guarantees](https://abseil.io/about/compatibility) to
  understand both what we promise to you, and what we expect of you in return.

# How to write code in the `api/` directory

Mostly, just follow the regular [style guide](../style-guide.md), but:

* Note that `api/` code is not exempt from the “`.h` and `.cc` files come in
  pairs” rule, so if you declare something in `api/path/to/foo.h`, it should be
  defined in `api/path/to/foo.cc`.
* Headers in `api/` should, if possible, not `#include` headers outside `api/`.
  It’s not always possible to avoid this, but be aware that it adds to a small
  mountain of technical debt that we’re trying to shrink.
* `.cc` files in `api/`, on the other hand, are free to `#include` headers
  outside `api/`.

That is, the preferred way for `api/` code to access non-`api/` code is to call
it from a `.cc` file, so that users of our API headers won’t transitively
`#include` non-public headers.

For headers in `api/` that need to refer to non-public types, forward
declarations are often a lesser evil than including non-public header files. The
usual [rules](../style-guide.md#forward-declarations) still apply, though.

`.cc` files in `api/` should preferably be kept reasonably small. If a
substantial implementation is needed, consider putting it with our non-public
code, and just call it from the `api/` `.cc` file.

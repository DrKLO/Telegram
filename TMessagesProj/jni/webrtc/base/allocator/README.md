This document describes how malloc / new calls are routed in the various Chrome
platforms.

Bare in mind that the chromium codebase does not always just use `malloc()`.
Some examples:
 - Large parts of the renderer (Blink) use two home-brewed allocators,
   PartitionAlloc and BlinkGC (Oilpan).
 - Some subsystems, such as the V8 JavaScript engine, handle memory management
   autonomously.
 - Various parts of the codebase use abstractions such as `SharedMemory` or
   `DiscardableMemory` which, similarly to the above, have their own page-level
   memory management.

Background
----------
The `allocator` target defines at compile-time the platform-specific choice of
the allocator and extra-hooks which services calls to malloc/new. The relevant
build-time flags involved are `use_allocator` and `use_allocator_shim`.

The default choices are as follows:

**Windows**
`use_allocator: winheap`, the default Windows heap.
Additionally, `static_library` (i.e. non-component) builds have a shim
layer wrapping malloc/new, which is controlled by `use_allocator_shim`.
The shim layer provides extra security features, such as preventing large
allocations that can hit signed vs. unsigned bugs in third_party code.

**Linux Desktop / CrOS**
`use_allocator: tcmalloc`, a forked copy of tcmalloc which resides in
`third_party/tcmalloc/chromium`. Setting `use_allocator: none` causes the build
to fall back to the system (Glibc) symbols.

**Android**
`use_allocator: none`, always use the allocator symbols coming from Android's
libc (Bionic). As it is developed as part of the OS, it is considered to be
optimized for small devices and more memory-efficient than other choices.
The actual implementation backing malloc symbols in Bionic is up to the board
config and can vary (typically *dlmalloc* or *jemalloc* on most Nexus devices).

**Mac/iOS**
`use_allocator: none`, we always use the system's allocator implementation.

In addition, when building for `asan` / `msan` both the allocator and the shim
layer are disabled.

Layering and build deps
-----------------------
The `allocator` target provides both the source files for tcmalloc (where
applicable) and the linker flags required for the Windows shim layer.
The `base` target is (almost) the only one depending on `allocator`. No other
targets should depend on it, with the exception of the very few executables /
dynamic libraries that don't depend, either directly or indirectly, on `base`
within the scope of a linker unit.

More importantly, **no other place outside of `/base` should depend on the
specific allocator** (e.g., directly include `third_party/tcmalloc`).
If such a functional dependency is required that should be achieved using
abstractions in `base` (see `/base/allocator/allocator_extension.h` and
`/base/memory/`)

**Why `base` depends on `allocator`?**
Because it needs to provide services that depend on the actual allocator
implementation. In the past `base` used to pretend to be allocator-agnostic
and get the dependencies injected by other layers. This ended up being an
inconsistent mess.
See the [allocator cleanup doc][url-allocator-cleanup] for more context.

Linker unit targets (executables and shared libraries) that depend in some way
on `base` (most of the targets in the codebase) get automatically the correct
set of linker flags to pull in tcmalloc or the Windows shim-layer.


Source code
-----------
This directory contains just the allocator (i.e. shim) layer that switches
between the different underlying memory allocation implementations.

The tcmalloc library originates outside of Chromium and exists in
`../../third_party/tcmalloc` (currently, the actual location is defined in the
allocator.gyp file). The third party sources use a vendor-branch SCM pattern to
track Chromium-specific changes independently from upstream changes.

The general intent is to push local changes upstream so that over
time we no longer need any forked files.


Unified allocator shim
----------------------
On most platforms, Chrome overrides the malloc / operator new symbols (and
corresponding free / delete and other variants). This is to enforce security
checks and lately to enable the
[memory-infra heap profiler][url-memory-infra-heap-profiler].
Historically each platform had its special logic for defining the allocator
symbols in different places of the codebase. The unified allocator shim is
a project aimed to unify the symbol definition and allocator routing logic in
a central place.

 - Full documentation: [Allocator shim design doc][url-allocator-shim].
 - Current state: Available and enabled by default on Android, CrOS, Linux,
   Mac OS and Windows.
 - Tracking bug: [https://crbug.com/550886][crbug.com/550886].
 - Build-time flag: `use_allocator_shim`.

**Overview of the unified allocator shim**
The allocator shim consists of three stages:
```
+-------------------------+    +-----------------------+    +----------------+
|     malloc & friends    | -> |       shim layer      | -> |   Routing to   |
|    symbols definition   |    |     implementation    |    |    allocator   |
+-------------------------+    +-----------------------+    +----------------+
| - libc symbols (malloc, |    | - Security checks     |    | - tcmalloc     |
|   calloc, free, ...)    |    | - Chain of dispatchers|    | - glibc        |
| - C++ symbols (operator |    |   that can intercept  |    | - Android      |
|   new, delete, ...)     |    |   and override        |    |   bionic       |
| - glibc weak symbols    |    |   allocations         |    | - WinHeap      |
|   (__libc_malloc, ...)  |    +-----------------------+    +----------------+
+-------------------------+
```

**1. malloc symbols definition**
This stage takes care of overriding the symbols `malloc`, `free`,
`operator new`, `operator delete` and friends and routing those calls inside the
allocator shim (next point).
This is taken care of by the headers in `allocator_shim_override_*`.

*On Linux/CrOS*: the allocator symbols are defined as exported global symbols
in `allocator_shim_override_libc_symbols.h` (for `malloc`, `free` and friends)
and in `allocator_shim_override_cpp_symbols.h` (for `operator new`,
`operator delete` and friends).
This enables proper interposition of malloc symbols referenced by the main
executable and any third party libraries. Symbol resolution on Linux is a breadth first search that starts from the root link unit, that is the executable
(see EXECUTABLE AND LINKABLE FORMAT (ELF) - Portable Formats Specification).
Additionally, when tcmalloc is the default allocator, some extra glibc symbols
are also defined in `allocator_shim_override_glibc_weak_symbols.h`, for subtle
reasons explained in that file.
The Linux/CrOS shim was introduced by
[crrev.com/1675143004](https://crrev.com/1675143004).

*On Android*: load-time symbol interposition (unlike the Linux/CrOS case) is not
possible. This is because Android processes are `fork()`-ed from the Android
zygote, which pre-loads libc.so and only later native code gets loaded via
`dlopen()` (symbols from `dlopen()`-ed libraries get a different resolution
scope).
In this case, the approach instead of wrapping symbol resolution at link time
(i.e. during the build), via the `--Wl,-wrap,malloc` linker flag.
The use of this wrapping flag causes:
 - All references to allocator symbols in the Chrome codebase to be rewritten as
   references to `__wrap_malloc` and friends. The `__wrap_malloc` symbols are
   defined in the `allocator_shim_override_linker_wrapped_symbols.h` and
   route allocator calls inside the shim layer.
 - The reference to the original `malloc` symbols (which typically is defined by
   the system's libc.so) are accessible via the special `__real_malloc` and
   friends symbols (which will be relocated, at load time, against `malloc`).

In summary, this approach is transparent to the dynamic loader, which still sees
undefined symbol references to malloc symbols.
These symbols will be resolved against libc.so as usual.
More details in [crrev.com/1719433002](https://crrev.com/1719433002).

**2. Shim layer implementation**
This stage contains the actual shim implementation. This consists of:
- A singly linked list of dispatchers (structs with function pointers to `malloc`-like functions). Dispatchers can be dynamically inserted at runtime
(using the `InsertAllocatorDispatch` API). They can intercept and override
allocator calls.
- The security checks (suicide on malloc-failure via `std::new_handler`, etc).
This happens inside `allocator_shim.cc`

**3. Final allocator routing**
The final element of the aforementioned dispatcher chain is statically defined
at build time and ultimately routes the allocator calls to the actual allocator
(as described in the *Background* section above). This is taken care of by the
headers in `allocator_shim_default_dispatch_to_*` files.


Appendixes
----------
**How does the Windows shim layer replace the malloc symbols?**
The mechanism for hooking LIBCMT in Windows is rather tricky.  The core
problem is that by default, the Windows library does not declare malloc and
free as weak symbols.  Because of this, they cannot be overridden.  To work
around this, we start with the LIBCMT.LIB, and manually remove all allocator
related functions from it using the visual studio library tool.  Once removed,
we can now link against the library and provide custom versions of the
allocator related functionality.
See the script `preb_libc.py` in this folder.

Related links
-------------
- [Unified allocator shim doc - Feb 2016][url-allocator-shim]
- [Allocator cleanup doc - Jan 2016][url-allocator-cleanup]
- [Proposal to use PartitionAlloc as default allocator](https://crbug.com/339604)
- [Memory-Infra: Tools to profile memory usage in Chrome](/docs/memory-infra/README.md)

[url-allocator-cleanup]: https://docs.google.com/document/d/1V77Kgp_4tfaaWPEZVxNevoD02wXiatnAv7Ssgr0hmjg/edit?usp=sharing
[url-memory-infra-heap-profiler]: /docs/memory-infra/heap_profiler.md
[url-allocator-shim]: https://docs.google.com/document/d/1yKlO1AO4XjpDad9rjcBOI15EKdAGsuGO_IeZy0g0kxo/edit?usp=sharing

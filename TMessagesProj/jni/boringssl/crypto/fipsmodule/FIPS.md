# FIPS 140-2

BoringSSL as a whole is not FIPS validated. However, there is a core library (called BoringCrypto) that has been FIPS validated. This document contains some notes about the design of the FIPS module and some documentation on performing FIPS-related tasks. This is not a substitute for reading the offical Security Policy.

Please note that we cannot answer questions about FIPS, nor about using BoringSSL in a FIPS-compliant manner. Please consult with an [accredited CMVP lab](http://csrc.nist.gov/groups/STM/testing_labs/) on these subjects.

## Validations

BoringCrypto has undergone the following validations:

1. 2017-06-15: certificate [#2964](https://csrc.nist.gov/Projects/Cryptographic-Module-Validation-Program/Certificate/2964), [security policy](/crypto/fipsmodule/policydocs/BoringCrypto-Security-Policy-20170615.docx) (in docx format).
1. 2018-07-30: certificate [#3318](https://csrc.nist.gov/Projects/Cryptographic-Module-Validation-Program/Certificate/3318), [security policy](/crypto/fipsmodule/policydocs/BoringCrypto-Security-Policy-20180730.docx) (in docx format).

## Running CAVP tests

CAVP results are calculated by `util/fipstools/cavp`, but that binary is almost always run by `util/fipstools/run_cavp.go`. The latter knows the set of tests to be processed and the flags needed to configure `cavp` for each one. It must be run from the top of a CAVP directory and needs the following options:

1. `-oracle-bin`: points to the location of `util/fipstools/cavp`
2. `-no-fax`: this is needed to suppress checking of the FAX files, which are only included in sample sets.

## Breaking power-on and continuous tests

In order to demonstrate failures of the various FIPS 140 tests, BoringSSL can be built in ways that will trigger such failures. This is controlled by passing `-DFIPS_BREAK_TEST=`(test to break) to CMake, where the following tests can be specified:

1. AES\_CBC
1. AES\_GCM
1. DES
1. SHA\_1
1. SHA\_256
1. SHA\_512
1. RSA\_SIG
1. ECDSA\_SIG
1. DRBG
1. RSA\_PWCT
1. ECDSA\_PWCT

## Breaking the integrity test

The utility in `util/fipstools/break-hash.go` can be used to corrupt the FIPS module inside a binary and thus trigger a failure of the integrity test. Note that the binary must not be stripped, otherwise the utility will not be able to find the FIPS module.

## RNG design

FIPS 140-2 requires that one of its PRNGs be used (which they call DRBGs). In BoringCrypto, we use CTR-DRBG with AES-256 exclusively and `RAND_bytes` (the primary interface for the rest of the system to get random data) takes its output from there.

The DRBG state is kept in a thread-local structure and is seeded from one of the following entropy sources in preference order: RDRAND (on Intel chips), `getrandom`, and `/dev/urandom`. In the case of `/dev/urandom`, in order to ensure that the system has a minimum level of entropy, BoringCrypto polls the kernel until the estimated entropy is at least 256 bits. This is a poor man's version of `getrandom` and we strongly recommend using a kernel recent enough to support the real thing.

In FIPS mode, each of those entropy sources is subject to a 10× overread. That is, when *n* bytes of entropy are needed, *10n* bytes will be read from the entropy source and XORed down to *n* bytes. Reads from the entropy source are also processed in blocks of 16 bytes and if two consecutive chunks are equal the process will abort.

The CTR-DRBG is reseeded every 4096 calls to `RAND_bytes`. Thus the process will randomly crash about every 2¹³⁵ calls.

The FIPS PRNGs allow “additional input” to be fed into a given call. We use this feature to be as robust as possible to state duplication from process forks and VM copies: for every call we read 32 bytes of “additional data” from the entropy source (without overread) which means that cloned states will diverge at the next call to `RAND_bytes`. This is called “prediction resistance” by FIPS, but we do *not* claim this property in a FIPS context because we don't implement it the way they want.

There is a second interface to the RNG which allows the caller to supply bytes that will be XORed into the generated additional data (`RAND_bytes_with_additional_data`). This is used in the ECDSA code to include the message and private key in the generation of *k*, the ECDSA nonce. This allows ECDSA to be robust to entropy failures while still following the FIPS rules.

FIPS requires that RNG state be zeroed when the process exits. In order to implement this, all per-thread RNG states are tracked in a linked list and a destructor function is included which clears them. In order for this to be safe in the presence of threads, a lock is used to stop all other threads from using the RNG once this process has begun. Thus the main thread exiting may cause other threads to deadlock, and drawing on entropy in a destructor function may also deadlock.

## Integrity Test

FIPS-140 mandates that a module calculate an HMAC of its own code in a constructor function and compare the result to a known-good value. Typical code produced by a C compiler includes large numbers of relocations: places in the machine code where the linker needs to resolve and inject the final value of a symbolic expression. These relocations mean that the bytes that make up any specific bit of code generally aren't known until the final link has completed.

Additionally, because of shared libraries and ASLR, some relocations can only be resolved at run-time, and thus targets of those relocations vary even after the final link.

BoringCrypto is linked (often statically) into a large number of binaries. It would be a significant cost if each of these binaries had to be post-processed in order to calculate the known-good HMAC value. We would much prefer if the value could be calculated, once, when BoringCrypto itself is compiled.

In order for the value to be calculated before the final link, there can be no relocations in the hashed code and data. This document describes how we build C and assembly code in order to produce a binary file containing all the code and data for the FIPS module without that code having any relocations.

There are two build configurations supported: static and shared. The shared build produces `libcrypto.so`, which includes the FIPS module and is significantly more straightforward and so is described first:

### Shared build

First, all the C source files for the module are compiled as a single unit by compiling a single source file that `#include`s them all (this is `bcm.c`). This, along with some assembly sources, comprise the FIPS module.

The object files resulting from compiling (or assembling) those files is linked in partial-linking mode with a linker script that causes the linker to insert symbols marking the beginning and end of the text and rodata sections. The linker script also discards other types of data sections to ensure that no unhashed data is used by the module.

One source of such data are `rel.ro` sections, which contain data that includes function pointers. Since these function pointers are absolute, they are written by the dynamic linker at run-time and so we must eliminate them. The pattern that causes them is when we have a static `EVP_MD` or `EVP_CIPHER` object thus, inside the module, this pattern is changed to instead reserve space in the BSS for the object, and to add a `CRYPTO_once_t` to protect its initialisation.

Once the partially-linked result is linked again, with other parts of libcrypto, to produce `libcrypto.so`, the contents of the module are fixed, as required. The module code uses the linker-added symbols to find the its code and data at run-time and hashes them upon initialisation. The result is compared against a value stored inside `libcrypto.so`, but outside of the module. That value will, initially, be incorrect, but `inject-hash.go` can inject the correct value.

### Static build

The static build cannot depend on the shared-object link to resolve relocations and thus must take another path.

As with the shared build, all the C sources are build in a single compilation unit. The `-fPIC` flag is used to cause the compiler to use IP-relative addressing in many (but not all) cases. Also the `-S` flag is used to instruct the compiler to produce a textual assembly file rather than a binary object file.

The textual assembly file is then processed by a script to merge in assembly implementations of some primitives and to eliminate the remaining sources of relocations.

##### Redirector functions

The most obvious cause of relocations are out-calls from the module to non-cryptographic functions outside of the module. Most obviously these include `malloc`, `memcpy` and other libc functions, but also include calls to support code in BoringSSL such as functions for managing the error queue.

Offsets to these functions cannot be known until the final link because only the linker sees the object files containing them. Thus calls to these functions are rewritten into an IP-relative jump to a redirector function. The redirector functions contain a single jump instruction to the real function and are placed outside of the module and are thus not hashed (see diagram).

![module structure](/crypto/fipsmodule/intcheck1.png)

In this diagram, the integrity check hashes from `module_start` to `module_end`. Since this does not cover the jump to `memcpy`, it's fine that the linker will poke the final offset into that instruction.

##### Read-only data

Normally read-only data is placed in an `.rodata` segment that doesn't get mapped into memory with execute permissions. However, the offset of the data segment from the text segment is another thing that isn't determined until the final link. In order to fix data offsets before the link, read-only data is simply placed in the module's `.text` segment. This might make building ROP chains easier for an attacker, but so it goes.

Data containing function pointers remains an issue. The source-code changes described above for the shared build apply here too, but no direct references to a BSS section are possible because the offset to that section is not known at compile time. Instead, the script generates functions outside of the module that return pointers to these areas of memory—they effectively act like special-purpose malloc calls that cannot fail.

##### Read-write data

Mutable data is a problem. It cannot be in the text segment because the text segment is mapped read-only. If it's in a different segment then the code cannot reference it with a known, IP-relative offset because the segment layout is only fixed during the final link.

In order to allow this we use a similar design to the redirector functions: the code references a symbol that's in the text segment, but out of the module and thus not hashed. A relocation record is emitted to instruct the linker to poke the final offset to the variable in that location. Thus the only change needed is an extra indirection when loading the value.

##### Other transforms

The script performs a number of other transformations which are worth noting but do not warrant their own discussions:

1.  It duplicates each global symbol with a local symbol that has `_local_target` appended to the name. References to the global symbols are rewritten to use these duplicates instead. Otherwise, although the generated code uses IP-relative references, relocations are emitted for global symbols in case they are overridden by a different object file during the link.
1.  Various sections, notably `.rodata`, are moved to the `.text` section, inside the module, so module code may reference it without relocations.
1.  For each BSS symbol, it generates a function named after that symbol but with `_bss_get` appended, which returns its address.
1.  It inserts the labels that delimit the module's code and data (called `module_start` and `module_end` in the diagram above).
1.  It adds a 64-byte, read-only array outside of the module to contain the known-good HMAC value.

##### Integrity testing

In order to actually implement the integrity test, a constructor function within the module calculates an HMAC from `module_start` to `module_end` using a fixed, all-zero key. It compares the result with the known-good value added (by the script) to the unhashed portion of the text segment. If they don't match, it calls `exit` in an infinite loop.

Initially the known-good value will be incorrect. Another script (`inject_hash.go`) calculates the correct value from the assembled object and injects it back into the object.

![build process](/crypto/fipsmodule/intcheck2.png)

### Comparison with OpenSSL's method

(This is based on reading OpenSSL's [user guide](https://www.openssl.org/docs/fips/UserGuide-2.0.pdf) and inspecting the code of OpenSSL FIPS 2.0.12.)

OpenSSL's solution to this problem is very similar to our shared build, with just a few differences:

1.  OpenSSL deals with run-time relocations by not hashing parts of the module's data.
1.  OpenSSL uses `ld -r` (the partial linking mode) to merge a number of object files into their `fipscanister.o`. For BoringCrypto's static build, we merge all the C source files by building a single C file that #includes all the others, and we merge the assembly sources by appending them to the assembly output from the C compiler.
1.  OpenSSL depends on the link order and inserts two object files, `fips_start.o` and `fips_end.o`, in order to establish the `module_start` and `module_end` values. BoringCrypto adds labels at the correct places in the assembly for the static build, or uses a linker script for the shared build.
1.  OpenSSL calculates the hash after the final link and either injects it into the binary or recompiles with the value of the hash passed in as a #define. BoringCrypto calculates it prior to the final link and injects it into the object file.
1.  OpenSSL references read-write data directly, since it can know the offsets to it. BoringCrypto indirects these loads and stores.
1.  OpenSSL doesn't run the power-on test until `FIPS_module_mode_set` is called. BoringCrypto does it in a constructor function. Failure of the test is non-fatal in OpenSSL, BoringCrypto will crash.
1.  Since the contents of OpenSSL's module change between compilation and use, OpenSSL generates `fipscanister.o.sha1` to check that the compiled object doesn't change before linking. Since BoringCrypto's module is fixed after compilation (in the static case), the final integrity check is unbroken through the linking process.

Some of the similarities are worth noting:

1.  OpenSSL has all out-calls from the module indirecting via the PLT, which is equivalent to the redirector functions described above.

![OpenSSL build process](/crypto/fipsmodule/intcheck3.png)

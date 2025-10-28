# Porting to Other Implementations

## Introduction

This document provides an overview of the test runner and how to
integrate it with other stacks.  So far we have it working with
BoringSSL and some incomplete integrations with NSS and OpenSSL.

Note that supporting non-BoringSSL implementations is a work in
progress and interfaces may change in the future. Consumers should pin
to a particular revision rather than using BoringSSL’s `main` branch
directly. As we gain experience with other implementations, we hope to
make further improvements to portability, so please contact
davidben@google.com and ekr@rtfm.com if implementing a new shim.


## Integration Architecture

The test runner integrates with the TLS stack under test through a
“shim”: a command line program which encapsulates the stack. By
default, the shim points to the BoringSSL shim in the same source
tree, but any program can be supplied via the `-shim-path` flag. The
runner opens up a server socket and provides the shim with `-port`, `-shim-id`
and optional `-ipv6` arguments.

For each connection, the shim should connect to loopback as a TCP client on
the specified port, using IPv6 if `-ipv6` is specified and IPv4 otherwise.
It then sends the shim ID as a 64-bit, little-endian integer and proceeds with
the test. The shim is a TCP client even when testing DTLS or TLS server
behavior. For DTLS, there is a small framing layer that gives packet boundaries
over TCP. The shim can also pass a variety of command line arguments
which are used to configure the stack under test. These can be found at
`test_config.cc`.

The shim reports success by exiting with a `0` error code and failure by
reporting a non-zero error code and generally sending a textual error
value to stderr. Many of the tests expect specific error string (such
as `NO_SHARED_CIPHER`) that indicates what went wrong.


## Compatibility Issues

There are a number of situations in which the runner might succeed
with some tests and not others:

* Defects in the stack under test
* Features which haven’t yet been implemented
* Failure to implement one or more of the command line flags the runner uses with the shim
* Disagreement about the right behavior/interpretation of the spec


We have implemented several features which allow implementations to ease these compatibility issues.

### Configuration File

The runner can be supplied with a JSON configuration file which is
intended to allow for a per-stack mapping. This file currently takes
two directives:


* `DisabledTests`: A JSON map consisting of the pattern matching the
  tests to be disabled as the key and some sort of reason why it was
  disabled as the value. The key is used as a match against the test
  name. The value is ignored and is just used for documentation
  purposes so you can remember why you disabled a
  test. `-include-disabled` overrides this filter.

* `ErrorMap`: A JSON map from the internal errors the runner expects to
  the error strings that your implementation spits out. Generally
  you’ll need to map every error, but if you also provide the
 ` -loose-errors` flag, then every un-mapped error just gets mapped to
  the empty string and treated as if it matched every error the runner
  expects.


The `-shim-config` flag is used to provide the config file.


### Unimplemented Features
If the shim encounters some request from the runner that it knows it
can’t fulfill (e.g., a command line flag that it doesn’t recognize),
then it can exit with the special code `89`. Shims are recommended to
use this exit code on unknown command-line arguments.

The test runner interprets this as “unimplemented” and skips the
test. If run normally, this will cause the test runner to report that
the entire test suite failed. The `-allow-unimplemented` flag suppresses
this behavior and causes the test runner to ignore these tests for the
purpose of evaluating the success or failure of the test suite.


### Malloc Tests

The test runner can also be used to stress malloc failure
codepaths. If passed `-malloc-test=0`, the runner will run each test
repeatedly with an incrementing `MALLOC_NUMBER_TO_FAIL` environment
variable. The shim should then replace the malloc implementation with
one which fails at the specified number of calls. If there are not
enough calls to reach the number, the shim should fail with exit code
`88`. This signals to the runner that the test has completed.

Historically, BoringSSL did this by replacing the actual `malloc`
symbol, but we have found hooking the library's `malloc` wrapper, under a
test-only build configuration, to be more straightforward. See `crypto/mem.c`
for an example which handles the environment variables in `OPENSSL_malloc`.

Note these tests are slow and will hit Go's test timeout. Pass `-timeout 72h` to
avoid crashing after 10 minutes.


## Example: Running Against NSS

```
DYLD_LIBRARY_PATH=~/dev/nss-dev/nss-sandbox/dist/Darwin15.6.0_64_DBG.OBJ/lib go test -shim-path ~/dev/nss-dev/nss-sandbox/dist/Darwin15.6.0_64_DBG.OBJ/bin/nss_bogo_shim -loose-errors -allow-unimplemented -shim-config ~/dev/nss-dev/nss-sandbox/nss/external_tests/nss_bogo_shim/config.json
```

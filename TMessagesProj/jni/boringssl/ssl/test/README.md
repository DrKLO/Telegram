# BoringSSL SSL Tests

This directory contains BoringSSL's protocol-level test suite.

Testing a TLS implementation can be difficult. We need to produce invalid but
sufficiently correct handshakes to get our implementation close to its edge
cases. TLS's cryptographic steps mean we cannot use a transcript and effectively
need a TLS implementation on the other end. But we do not wish to litter
BoringSSL with options for bugs to test against.

Instead, we use a fork of the Go `crypto/tls` package, heavily patched with
configurable bugs. This code, along with a test suite and harness written in Go,
lives in the `runner` directory. The harness runs BoringSSL via a C/C++ shim
binary which lives in this directory. All communication with the shim binary
occurs with command-line flags, sockets, and standard I/O.

This strategy also ensures we always test against a second implementation. All
features should be implemented twice, once in C for BoringSSL and once in Go for
testing. If possible, the Go code should be suitable for potentially
upstreaming. However, sometimes test code has different needs. For example, our
test DTLS code enforces strict ordering on sequence numbers and has controlled
packet drop simulation.

To run the tests manually, run `go test` from the `runner` directory. It takes
command-line flags found at the top of `runner/runner.go`. The `-help` option
also works after using `go test -c` to make a `runner.test` binary first.

If adding a new test, these files may be a good starting point:

 * `runner/runner.go`: the test harness and all the individual tests.
 * `runner/common.go`: contains the `Config` and `ProtocolBugs` struct which
   control the Go TLS implementation's behavior.
 * `test_config.h`, `test_config.cc`: the command-line flags which control the
   shim's behavior.
 * `bssl_shim.cc`: the shim binary itself.

For porting the test suite to a different implementation see
[PORTING.md](./PORTING.md).

# Temporary-address starvation regression

This is an isolated selector test, not an APK or socket integration test. The
extractor compiles actual function bodies from `Datacenter.cpp` and includes the
real `Datacenter.h`/`Defines.h`. Empty handshake/salt dependency types are used
only because crypto and network operations are outside this test. A non-null
sentinel represents an existing permanent key; no key is read or dereferenced.

The regression state is: permanent auth key present, temporary auth key absent,
known ordinary IPv4 endpoint, and a failing emergency IPv4 endpoint. Repeated
`nextAddressOrPort(0)` calls must eventually select the ordinary endpoint.

The test uses documentation-only IP ranges, performs no network I/O, and neither
reads nor modifies an installed Telegram application's files or accounts.

Run with a native C++ compiler (Linux/macOS example):

```sh
python3 tests/extract_address_test.py /tmp/address-test.cpp
c++ -std=c++17 -I TMessagesProj/jni/tgnet /tmp/address-test.cpp -o /tmp/address-test
/tmp/address-test
```

For a baseline comparison, run the same extractor and test template against the
unmodified `Datacenter.cpp` and matching headers from the base commit. The optional
second extractor argument selects that original `.cpp` file. Do not reverse the
patch in a checkout containing unrelated work.

Actual validation used Android NDK 27.0.12077973, target
`aarch64-linux-android24`, C++17 and `-static-libstdc++`; the independent native
executables ran on an Android 16 arm64 device using `adb shell`. Baseline returned
1 (six failed assertions), candidate returned 0 (all 15 assertions passed).
The complete modified `Datacenter.cpp` also compiled to an Android object using
the repository-pinned BoringSSL headers. The full APK has not been built, signed,
installed, or tested. The proposal still needs upstream review and network-level
integration tests, especially bootstrap and network changes.

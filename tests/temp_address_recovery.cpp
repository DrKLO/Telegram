// GPL-2.0-or-later. Isolated tests of the production Datacenter address selector.
// The runner compiles the actual function bodies from Datacenter.cpp, not copies.
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <map>
#include <vector>
#include "Defines.h"
// These dependencies are never used by address-selection tests.
class Handshake {};
class TL_future_salt {};
#define private public
#include "Datacenter.h"
#undef private

void Datacenter::onHandshakeComplete(Handshake*, int64_t, ByteArray*, int32_t) { std::abort(); }

// PRODUCTION_METHODS

static int failures = 0;
static void check(bool ok, const char* label) {
    std::printf("%s %s\n", ok ? "PASS" : "FAIL", label);
    failures += !ok;
}
static void setup(Datacenter& dc, bool regular = true) {
    // Simulate the state after a -404 clears only the temporary auth key.
    dc.authKeyPerm = reinterpret_cast<ByteArray*>(1);
    dc.authKeyTemp = nullptr;
    if (regular) dc.addAddressAndPort("192.0.2.10", 8443, 0, "");
    dc.addAddressAndPort("192.0.2.20", 9443, TcpAddressFlagTemp, "test-secret");
}
static bool addressIs(Datacenter& dc, uint32_t flags, const char* ip) {
    auto* address = dc.getCurrentAddress(flags);
    return address && address->address == ip;
}
static void exhaust(Datacenter& dc, unsigned advances = 4) {
    for (unsigned i = 0; i < advances; ++i) dc.nextAddressOrPort(0);
}
int main() {
    {
        Datacenter dc(0, 4); setup(dc);
        check(addressIs(dc, 0, "192.0.2.20"), "temporary bootstrap remains initially available");
        exhaust(dc);
        check(addressIs(dc, 0, "192.0.2.10"), "exhausted temporary routes fall back to known ordinary IPv4");
        check(dc.getCurrentPort(0) == 8443, "address and port use the same fallback list");
        check(!dc.isCustomPort(0), "custom-port query uses the fallback list");
        check(addressIs(dc, TcpAddressFlagTemp, "192.0.2.20"), "explicit config-fetch connection remains temporary");
        std::vector<TcpAddress> same{TcpAddress("192.0.2.20", 9443, TcpAddressFlagTemp, "test-secret")};
        dc.replaceAddresses(same, TcpAddressFlagTemp);
        check(addressIs(dc, 0, "192.0.2.10"), "refreshing identical failed config does not rearm the loop");
        same[0].secret = "new-test-secret";
        dc.replaceAddresses(same, TcpAddressFlagTemp);
        check(addressIs(dc, 0, "192.0.2.20"), "genuinely changed temporary config can be tried again");
    }
    {
        Datacenter dc(0, 4); setup(dc, false); exhaust(dc, 12);
        check(addressIs(dc, 0, "192.0.2.20"), "no known ordinary IPv4 preserves bootstrap route");
    }
    {
        Datacenter dc(0, 4); setup(dc);
        dc.addAddressAndPort("192.0.2.21", 9443, TcpAddressFlagTemp, "second-secret");
        exhaust(dc, 4);
        check(addressIs(dc, 0, "192.0.2.21"), "tries all temporary endpoints before falling back");
        exhaust(dc, 4);
        check(addressIs(dc, 0, "192.0.2.10"), "multi-endpoint temporary list also terminates");
        std::vector<TcpAddress> reordered{
            TcpAddress("192.0.2.21", 9443, TcpAddressFlagTemp, "second-secret"),
            TcpAddress("192.0.2.20", 9443, TcpAddressFlagTemp, "test-secret")};
        dc.replaceAddresses(reordered, TcpAddressFlagTemp);
        check(addressIs(dc, 0, "192.0.2.10"), "reordering identical failed routes does not rearm the loop");
    }
    {
        Datacenter dc(0, 4); setup(dc);
        exhaust(dc, 4);
        check(addressIs(dc, 0, "192.0.2.10"), "ordinary list receives a full retry cycle");
        exhaust(dc, 4);
        check(addressIs(dc, 0, "192.0.2.20"), "if ordinary routes also fail, emergency routes are retried");
    }
    {
        Datacenter dc(0, 4); setup(dc);
        dc.authKeyTemp = reinterpret_cast<ByteArray*>(2);
        check(addressIs(dc, 0, "192.0.2.10"), "healthy auth key keeps ordinary routes");
        check(addressIs(dc, TcpAddressFlagStatic, "192.0.2.10"), "explicit proxy/static selection unchanged");
    }
    std::printf("FAILURES=%d\n", failures);
    return failures ? 1 : 0;
}

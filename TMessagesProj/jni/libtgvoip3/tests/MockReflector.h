//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//
#ifndef TGVOIP_MOCK_REFLECTOR
#define TGVOIP_MOCK_REFLECTOR

#include <net/if.h>
#include <netdb.h>
#include <netinet/tcp.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

#include <array>
#include <cstdint>
#include <string>
#include <unordered_map>

namespace tgvoip
{

namespace test
{

class MockReflector
{
public:
    MockReflector(const std::string& bindAddress, std::uint16_t bindPort);
    ~MockReflector();
    void Start();
    void Stop();
    void SetDropAllPackets(bool drop);
    static std::array<std::array<std::uint8_t, 16>, 2> GeneratePeerTags();

private:
    struct ClientPair
    {
        ClientPair();
        sockaddr_in addr0;
        sockaddr_in addr1;
    };

    std::unordered_map<std::uint64_t, ClientPair> m_clients; // clients are identified by the first half of their peer_tag
    pthread_t m_thread;
    int m_sfd;

    bool m_running = false;
    bool m_dropAllPackets = false;

    void RunThread();
};

} // namespace test

} // namespace tgvoip

#endif // TGVOIP_MOCK_REFLECTOR

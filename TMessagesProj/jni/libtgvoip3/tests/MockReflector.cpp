//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "MockReflector.h"

#include <arpa/inet.h>

#include <cassert>
#include <cstdio>
#include <cstring>

using namespace tgvoip;
using namespace tgvoip::test;

struct UdpReflectorSelfInfo
{
    std::uint8_t peerTag[16];
    std::uint64_t _id1 = 0xFFFFFFFFFFFFFFFFLL;
    std::uint32_t _id2 = 0xFFFFFFFF;
    std::uint32_t magic = 0xc01572c7;
    std::int32_t date;
    std::uint64_t query_id;
    std::uint64_t my_ip_padding1;
    std::uint32_t my_ip_padding2;
    std::uint32_t my_ip;
    std::uint32_t my_port;
} __attribute__((packed));

MockReflector::MockReflector(const std::string& bindAddress, std::uint16_t bindPort)
{
    m_sfd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
    assert(m_sfd != -1);
    sockaddr_in bindAddr;
    std::memset(&bindAddr, 0, sizeof(bindAddr));
    bindAddr.sin_family = AF_INET;
    bindAddr.sin_port = htons(bindPort);
    inet_aton(bindAddress.c_str(), &bindAddr.sin_addr);
    int res = bind(m_sfd, reinterpret_cast<struct sockaddr*>(&bindAddr), sizeof(bindAddr));
    assert(res == 0);
}

MockReflector::~MockReflector() = default;

std::array<std::array<std::uint8_t, 16>, 2> MockReflector::GeneratePeerTags()
{
    std::array<std::uint8_t, 16> tag1;
    for (std::size_t i = 0; i < 16; ++i)
    {
        tag1[i] = static_cast<std::uint8_t>(rand());
    }
    tag1[15] &= 0xFE;
    std::array<std::array<std::uint8_t, 16>, 2> res;
    res[0] = tag1;
    std::copy(tag1.begin(), tag1.end(), res[1].begin());
    res[1][15] |= 1;
    return res;
}

MockReflector::ClientPair::ClientPair()
{
    std::memset(&addr0, 0, sizeof(addr0));
    std::memset(&addr1, 0, sizeof(addr1));
}

void MockReflector::Start()
{
    if (m_running)
        return;
    m_running = true;
    ::pthread_create(
        &m_thread, nullptr, [](void* arg) -> void*
        {
            reinterpret_cast<MockReflector*>(arg)->RunThread();
            return nullptr;
        },
        this);
}

void MockReflector::Stop()
{
    m_running = false;
    ::shutdown(m_sfd, SHUT_RDWR);
    ::close(m_sfd);
    ::pthread_join(m_thread, nullptr);
}

void MockReflector::SetDropAllPackets(bool drop)
{
    m_dropAllPackets = drop;
}

void MockReflector::RunThread()
{
    while (m_running)
    {
        std::array<std::uint8_t, 1500> buf;
        sockaddr_in addr;
        socklen_t addrlen = sizeof(addr);
        ssize_t len = recvfrom(m_sfd, buf.data(), sizeof(buf), 0, reinterpret_cast<struct sockaddr*>(&addr), &addrlen);
        if (len <= 0)
            return;
        if (len >= 32)
        {
            std::array<std::uint8_t, 16> peerTag;
            std::int32_t specialID[4];
            std::copy(buf.begin(), buf.begin() + 16, peerTag.begin());
            std::memcpy(specialID, buf.data() + 16, 16);
            std::uint64_t tagID = *reinterpret_cast<std::uint64_t*>(peerTag.data());
            ClientPair c = m_clients[tagID];
            sockaddr_in* dest;
            if (peerTag[15] & 1)
            {
                c.addr1 = addr;
                dest = &c.addr0;
            }
            else
            {
                c.addr0 = addr;
                dest = &c.addr1;
            }
            m_clients[tagID] = c;

            if (specialID[0] == -1 && specialID[1] == -1 && specialID[2] == -1)
            {
                if (specialID[3] == -1)
                {
                    continue;
                }
                if (specialID[3] == -2)
                {
                    UdpReflectorSelfInfo response;
                    std::memcpy(response.peerTag, peerTag.data(), 16);
                    response.date = static_cast<std::int32_t>(time(nullptr));
                    response.query_id = *reinterpret_cast<std::uint64_t*>(buf.data() + 32);
                    response.my_ip_padding1 = 0;
                    response.my_ip_padding2 = 0xFFFF0000;
                    response.my_ip = static_cast<std::uint32_t>(addr.sin_addr.s_addr);
                    response.my_port = ntohs(addr.sin_port);
                    sendto(m_sfd, &response, sizeof(response), 0, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
                    continue;
                }
            }

            if (dest->sin_family == AF_INET && !m_dropAllPackets)
            {
                if (peerTag[15] & 1)
                    buf[15] &= 0xFE;
                else
                    buf[15] |= 1;

                sendto(m_sfd, buf.data(), static_cast<std::size_t>(len), 0, reinterpret_cast<struct sockaddr*>(dest), sizeof(sockaddr_in));
            }
        }
    }
}

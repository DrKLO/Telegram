//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//
#ifndef TGVOIP_MOCK_REFLECTOR
#define TGVOIP_MOCK_REFLECTOR

#include <string>
#include <unordered_map>
#include <array>
#include <stdint.h>
#include <pthread.h>

#include <sys/socket.h>
#include <errno.h>
#include <assert.h>
#include <netdb.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <netinet/tcp.h>

namespace tgvoip{
	namespace test{
		class MockReflector{
		public:
			MockReflector(std::string bindAddress, uint16_t bindPort);
			~MockReflector();
			void Start();
			void Stop();
			void SetDropAllPackets(bool drop);
			static std::array<std::array<uint8_t, 16>, 2> GeneratePeerTags();

		private:
			void RunThread();
			struct ClientPair{
				sockaddr_in addr0={0};
				sockaddr_in addr1={0};
			};
			std::unordered_map<uint64_t, ClientPair> clients; // clients are identified by the first half of their peer_tag
			int sfd;
			pthread_t thread;
			bool running=false;
			bool dropAllPackets=false;
		};
	}
}

#endif //TGVOIP_MOCK_REFLECTOR

//
// Created by Grishka on 24/03/2019.
//

#ifndef LIBTGVOIP_VIDEOFEC_H
#define LIBTGVOIP_VIDEOFEC_H

#include "../Buffers.h"
#include <vector>

namespace tgvoip{
	namespace video{
		class ParityFEC{
		public:
			static Buffer Encode(std::vector<Buffer>& packets);
			static Buffer Decode(std::vector<Buffer>& dataPackets, Buffer& fecPacket);
		};

		class CM256FEC{

		};
	}
}

#endif //LIBTGVOIP_VIDEOFEC_H

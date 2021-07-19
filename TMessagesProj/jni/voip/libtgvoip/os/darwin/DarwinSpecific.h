//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef TGVOIP_DARWINSPECIFIC_H
#define TGVOIP_DARWINSPECIFIC_H

#include <string>

namespace tgvoip {
	
	struct CellularCarrierInfo;
	
class DarwinSpecific{
public:
	enum{
		THREAD_PRIO_USER_INTERACTIVE,
		THREAD_PRIO_USER_INITIATED,
		THREAD_PRIO_UTILITY,
		THREAD_PRIO_BACKGROUND,
		THREAD_PRIO_DEFAULT
	};
	static void GetSystemName(char* buf, size_t len);
	static void SetCurrentThreadPriority(int priority);
	static CellularCarrierInfo GetCarrierInfo();
	static void ConfigureAudioSession();
};
}

#endif //TGVOIP_DARWINSPECIFIC_H

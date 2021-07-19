//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_OPENSLENGINEWRAPPER_H
#define LIBTGVOIP_OPENSLENGINEWRAPPER_H

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

namespace tgvoip{ namespace audio{
class OpenSLEngineWrapper{
public:
	static SLEngineItf CreateEngine();
	static void DestroyEngine();

private:
	static SLObjectItf sharedEngineObj;
	static SLEngineItf sharedEngine;
	static int count;
};
}}

#endif //LIBTGVOIP_OPENSLENGINEWRAPPER_H

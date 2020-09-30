//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_WINDOWS_SANDBOX_UTILS
#define LIBTGVOIP_WINDOWS_SANDBOX_UTILS

#include <audioclient.h>
#include <windows.h>
#ifndef TGVOIP_WP_SILVERLIGHT
#include <mmdeviceapi.h>
#endif
#include <wrl.h>
#include <wrl/implements.h>

using namespace Microsoft::WRL;

namespace tgvoip {

#ifndef TGVOIP_WP_SILVERLIGHT
	class ActivationHandler :
		public RuntimeClass< RuntimeClassFlags< ClassicCom >, FtmBase, IActivateAudioInterfaceCompletionHandler >
	{
	public:
		STDMETHOD(ActivateCompleted)(IActivateAudioInterfaceAsyncOperation *operation);

		ActivationHandler(HANDLE _event);
		HANDLE event;
		IAudioClient2* client;
		HRESULT actResult;
	};
#endif

	class WindowsSandboxUtils {
	public:
		static IAudioClient2* ActivateAudioDevice(const wchar_t* devID, HRESULT* callResult, HRESULT* actResult);
	};
}

#endif // LIBTGVOIP_WINDOWS_SANDBOX_UTILS

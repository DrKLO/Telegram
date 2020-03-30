
//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "WindowsSandboxUtils.h"
#include <audioclient.h>
#include <windows.h>
#ifdef TGVOIP_WP_SILVERLIGHT
#include <phoneaudioclient.h>
#endif

using namespace tgvoip;
using namespace Microsoft::WRL;


IAudioClient2* WindowsSandboxUtils::ActivateAudioDevice(const wchar_t* devID, HRESULT* callRes, HRESULT* actRes) {
#ifndef TGVOIP_WP_SILVERLIGHT
	// Did I say that I hate pointlessly asynchronous things?
	HANDLE event = CreateEventEx(NULL, NULL, 0, EVENT_ALL_ACCESS);
	ActivationHandler activationHandler(event);
	IActivateAudioInterfaceAsyncOperation* actHandler;
	HRESULT cr = ActivateAudioInterfaceAsync(devID, __uuidof(IAudioClient2), NULL, (IActivateAudioInterfaceCompletionHandler*)&activationHandler, &actHandler);
	if (callRes)
		*callRes = cr;
	DWORD resulttt = WaitForSingleObjectEx(event, INFINITE, false);
	DWORD last = GetLastError();
	CloseHandle(event);
	if (actRes)
		*actRes = activationHandler.actResult;
	return activationHandler.client;
#else
	IAudioClient2* client;
	HRESULT res=ActivateAudioInterface(devID, __uuidof(IAudioClient2), (void**)&client);
	if(callRes)
		*callRes=S_OK;
	if(actRes)
		*actRes=res;
	return client;
#endif
}

#ifndef TGVOIP_WP_SILVERLIGHT
ActivationHandler::ActivationHandler(HANDLE _event) : event(_event)
{

}

STDMETHODIMP ActivationHandler::ActivateCompleted(IActivateAudioInterfaceAsyncOperation * operation)
{
	HRESULT hr = S_OK;
	HRESULT hrActivateResult = S_OK;
	IUnknown *punkAudioInterface = nullptr;

	hr = operation->GetActivateResult(&hrActivateResult, &punkAudioInterface);
	if (SUCCEEDED(hr) && SUCCEEDED(hrActivateResult))
	{
		punkAudioInterface->QueryInterface(IID_PPV_ARGS(&client));
	}

	SetEvent(event);

	return hr;
}

#endif
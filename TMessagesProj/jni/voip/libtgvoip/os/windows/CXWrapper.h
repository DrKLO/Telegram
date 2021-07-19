#pragma once

#include <wrl.h>
#include <wrl/implements.h>
#include <windows.storage.streams.h>
#include <robuffer.h>
#include <vector>
#include "../../VoIPController.h"
#include "../../VoIPServerConfig.h"

using namespace Platform;

#define STACK_ARRAY(TYPE, LEN) \
  static_cast<TYPE*>(::alloca((LEN) * sizeof(TYPE)))

inline std::wstring ToUtf16(const char* utf8, size_t len) {
	int len16 = ::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len),
		nullptr, 0);
	wchar_t* ws = STACK_ARRAY(wchar_t, len16);
	::MultiByteToWideChar(CP_UTF8, 0, utf8, static_cast<int>(len), ws, len16);
	return std::wstring(ws, len16);
}

inline std::wstring ToUtf16(const std::string& str) {
	return ToUtf16(str.data(), str.length());
}

inline std::string ToUtf8(const wchar_t* wide, size_t len) {
	int len8 = ::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len),
		nullptr, 0, nullptr, nullptr);
	char* ns = STACK_ARRAY(char, len8);
	::WideCharToMultiByte(CP_UTF8, 0, wide, static_cast<int>(len), ns, len8,
		nullptr, nullptr);
	return std::string(ns, len8);
}

inline std::string ToUtf8(const wchar_t* wide) {
	return ToUtf8(wide, wcslen(wide));
}

inline std::string ToUtf8(const std::wstring& wstr) {
	return ToUtf8(wstr.data(), wstr.length());
}

namespace libtgvoip{
	public ref class Endpoint sealed{
	public:
		property int64 id;
		property uint16 port;
		property Platform::String^ ipv4;
		property Platform::String^ ipv6;
		property Platform::Array<uint8>^ peerTag;
	};

	public ref class TrafficStats sealed{
	public:
		property uint64_t bytesSentWifi;
		property uint64_t bytesRecvdWifi;
		property uint64_t bytesSentMobile;
		property uint64_t bytesRecvdMobile;
	};

	public enum class CallState : int{
		WaitInit=1,
		WaitInitAck,
		Established,
		Failed
	};

	public enum class Error : int{
		Unknown=0,
		Incompatible,
		Timeout,
		AudioIO
	};

	public enum class NetworkType : int{
		Unknown=0,
		GPRS,
		EDGE,
		UMTS,
		HSPA,
		LTE,
		WiFi,
		Ethernet,
		OtherHighSpeed,
		OtherLowSpeed,
		Dialup,
		OtherMobile
	};

	public enum class DataSavingMode{
		Never=0,
		MobileOnly,
		Always
	};

	public enum class ProxyProtocol{
		None=0,
		SOCKS5
	};

	public ref class VoIPConfig sealed {
	public:
		VoIPConfig() {
			logPacketStats = false;
			enableVolumeControl = false;
			enableVideoSend = false;
			enableVideoReceive = false;
		}

		property double initTimeout;
		property double recvTimeout;
		property DataSavingMode dataSaving;
		property String^ logFilePath;
		property String^ statsDumpFilePath;

		property bool enableAEC;
		property bool enableNS;
		property bool enableAGC;

		property bool enableCallUpgrade;

		property bool logPacketStats;
		property bool enableVolumeControl;

		property bool enableVideoSend;
		property bool enableVideoReceive;
	};

	ref class VoIPControllerWrapper;
	public delegate void CallStateChangedEventHandler(VoIPControllerWrapper^ sender, CallState newState);

	ref class VoIPControllerWrapper;
	public delegate void SignalBarsChangedEventHandler(VoIPControllerWrapper^ sender, int newCount);

    public ref class VoIPControllerWrapper sealed{
    public:
        VoIPControllerWrapper();
		virtual ~VoIPControllerWrapper();
		void Start();
		void Connect();
		void SetPublicEndpoints(const Platform::Array<Endpoint^>^ endpoints, bool allowP2P, int32_t connectionMaxLayer);
		void SetNetworkType(NetworkType type);
		void SetMicMute(bool mute);
		void SetEncryptionKey(const Platform::Array<uint8>^ key, bool isOutgoing);
		void SetConfig(VoIPConfig^ config);
		void SetProxy(ProxyProtocol protocol, Platform::String^ address, uint16_t port, Platform::String^ username, Platform::String^ password);
		int GetSignalBarsCount();
		CallState GetConnectionState();
		TrafficStats^ GetStats();
		Platform::String^ GetDebugString();
		Platform::String^ GetDebugLog();
		Error GetLastError();
		static Platform::String^ GetVersion();
		int64 GetPreferredRelayID();
		void SetAudioOutputGainControlEnabled(bool enabled);

		void SetInputVolume(float level);
		void SetOutputVolume(float level);

		property String^ CurrentAudioInput
		{
			String^ get()
			{
				return ref new String(ToUtf16(controller->GetCurrentAudioInputID()).data());
			}
			void set(String^ value)
			{
				controller->SetCurrentAudioInput(ToUtf8(value->Data()));
			}
		}

		property String^ CurrentAudioOutput
		{
			String^ get()
			{
				return ref new String(ToUtf16(controller->GetCurrentAudioOutputID()).data());
			}
			void set(String^ value)
			{
				controller->SetCurrentAudioOutput(ToUtf8(value->Data()));
			}
		}

		static int32_t GetConnectionMaxLayer();
		static void UpdateServerConfig(Platform::String^ json);
		static void SwitchSpeaker(bool external);
		//static Platform::String^ TestAesIge();

		event CallStateChangedEventHandler^ CallStateChanged;
		event SignalBarsChangedEventHandler^ SignalBarsChanged;

	private:
		static void OnStateChanged(tgvoip::VoIPController* c, int state);
		static void OnSignalBarsChanged(tgvoip::VoIPController* c, int count);
		void OnStateChangedInternal(int state);
		void OnSignalBarsChangedInternal(int count);
		tgvoip::VoIPController* controller;
    };

	ref class MicrosoftCryptoImpl{
	public:
		static void AesIgeEncrypt(uint8_t* in, uint8_t* out, size_t len, uint8_t* key, uint8_t* iv);
		static void AesIgeDecrypt(uint8_t* in, uint8_t* out, size_t len, uint8_t* key, uint8_t* iv);
		static void AesCtrEncrypt(uint8_t* inout, size_t len, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num);
		static void SHA1(uint8_t* msg, size_t len, uint8_t* out);
		static void SHA256(uint8_t* msg, size_t len, uint8_t* out);
		static void RandBytes(uint8_t* buffer, size_t len);
		static void Init();
	private:
		static inline void XorInt128(uint8_t* a, uint8_t* b, uint8_t* out);
		static void IBufferToPtr(Windows::Storage::Streams::IBuffer^ buffer, size_t len, uint8_t* out);
		static Windows::Storage::Streams::IBuffer^ IBufferFromPtr(uint8_t* msg, size_t len);
		/*static Windows::Security::Cryptography::Core::CryptographicHash^ sha1Hash;
		static Windows::Security::Cryptography::Core::CryptographicHash^ sha256Hash;*/
		static Windows::Security::Cryptography::Core::HashAlgorithmProvider^ sha1Provider;
		static Windows::Security::Cryptography::Core::HashAlgorithmProvider^ sha256Provider;
		static Windows::Security::Cryptography::Core::SymmetricKeyAlgorithmProvider^ aesKeyProvider;
	};

	class NativeBuffer :
		public Microsoft::WRL::RuntimeClass<Microsoft::WRL::RuntimeClassFlags<Microsoft::WRL::RuntimeClassType::WinRtClassicComMix>,
		ABI::Windows::Storage::Streams::IBuffer,
		Windows::Storage::Streams::IBufferByteAccess>
	{
	public:
		NativeBuffer(byte *buffer, UINT totalSize)
		{
			m_length=totalSize;
			m_buffer=buffer;
		}

		virtual ~NativeBuffer()
		{
		}

		STDMETHODIMP RuntimeClassInitialize(byte *buffer, UINT totalSize)
		{
			m_length=totalSize;
			m_buffer=buffer;
			return S_OK;
		}

		STDMETHODIMP Buffer(byte **value)
		{
			*value=m_buffer;
			return S_OK;
		}

		STDMETHODIMP get_Capacity(UINT32 *value)
		{
			*value=m_length;
			return S_OK;
		}

		STDMETHODIMP get_Length(UINT32 *value)
		{
			*value=m_length;
			return S_OK;
		}

		STDMETHODIMP put_Length(UINT32 value)
		{
			m_length=value;
			return S_OK;
		}

	private:
		UINT32 m_length;
		byte *m_buffer;
	};
}
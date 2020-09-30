#include <stdlib.h>
#include <string.h>
#include <windows.h>
#include <vector>
#include <string>
#include <collection.h>
#include "CXWrapper.h"
#include <wrl.h>  
#include <robuffer.h>  

using namespace Windows::Storage::Streams;
using namespace Microsoft::WRL;
using namespace libtgvoip;
using namespace Platform;
using namespace tgvoip;
using namespace Windows::Security::Cryptography;
using namespace Windows::Security::Cryptography::Core;
using namespace Windows::Storage::Streams;
using namespace Windows::Data::Json;
using namespace Windows::Phone::Media::Devices;

//CryptographicHash^ MicrosoftCryptoImpl::sha1Hash;
//CryptographicHash^ MicrosoftCryptoImpl::sha256Hash;
HashAlgorithmProvider^ MicrosoftCryptoImpl::sha1Provider;
HashAlgorithmProvider^ MicrosoftCryptoImpl::sha256Provider;
SymmetricKeyAlgorithmProvider^ MicrosoftCryptoImpl::aesKeyProvider;

/*struct tgvoip_cx_data{
	VoIPControllerWrapper^ self;
};*/

VoIPControllerWrapper::VoIPControllerWrapper(){
	VoIPController::crypto.aes_ige_decrypt=MicrosoftCryptoImpl::AesIgeDecrypt;
	VoIPController::crypto.aes_ige_encrypt=MicrosoftCryptoImpl::AesIgeEncrypt;
	VoIPController::crypto.aes_ctr_encrypt = MicrosoftCryptoImpl::AesCtrEncrypt;
	VoIPController::crypto.sha1=MicrosoftCryptoImpl::SHA1;
	VoIPController::crypto.sha256=MicrosoftCryptoImpl::SHA256;
	VoIPController::crypto.rand_bytes=MicrosoftCryptoImpl::RandBytes;
	MicrosoftCryptoImpl::Init();
	controller=new VoIPController();
	controller->implData=(void*)this;
	VoIPController::Callbacks callbacks={0};
	callbacks.connectionStateChanged=VoIPControllerWrapper::OnStateChanged;
	callbacks.signalBarCountChanged=VoIPControllerWrapper::OnSignalBarsChanged;
	controller->SetCallbacks(callbacks);
}

VoIPControllerWrapper::~VoIPControllerWrapper(){
	controller->Stop();
	delete controller;
}

void VoIPControllerWrapper::Start(){
	controller->Start();
}

void VoIPControllerWrapper::Connect(){
	controller->Connect();
}

void VoIPControllerWrapper::SetPublicEndpoints(const Platform::Array<libtgvoip::Endpoint^>^ endpoints, bool allowP2P, int32_t connectionMaxLayer){
	std::vector<tgvoip::Endpoint> eps;
	for (unsigned int i = 0; i < endpoints->Length; i++)
	{
		libtgvoip::Endpoint^ _ep = endpoints[i];
		tgvoip::Endpoint ep;
		ep.id = _ep->id;
		ep.type = tgvoip::Endpoint::Type::UDP_RELAY;
		char buf[128];
		if (_ep->ipv4){
			WideCharToMultiByte(CP_UTF8, 0, _ep->ipv4->Data(), -1, buf, sizeof(buf), NULL, NULL);
			ep.address = IPv4Address(buf);
		}
		if (_ep->ipv6){
			WideCharToMultiByte(CP_UTF8, 0, _ep->ipv6->Data(), -1, buf, sizeof(buf), NULL, NULL);
			ep.v6address = IPv6Address(buf);
		}
		ep.port = _ep->port;
		if (_ep->peerTag->Length != 16)
			throw ref new Platform::InvalidArgumentException("Peer tag must be exactly 16 bytes long");
		memcpy(ep.peerTag, _ep->peerTag->Data, 16);
		eps.push_back(ep);
	}
	controller->SetRemoteEndpoints(eps, allowP2P, connectionMaxLayer);
}

void VoIPControllerWrapper::SetNetworkType(NetworkType type){
	controller->SetNetworkType((int)type);
}

void VoIPControllerWrapper::SetMicMute(bool mute){
	controller->SetMicMute(mute);
}

int64 VoIPControllerWrapper::GetPreferredRelayID(){
	return controller->GetPreferredRelayID();
}

int32_t VoIPControllerWrapper::GetConnectionMaxLayer(){
	return tgvoip::VoIPController::GetConnectionMaxLayer();
}

void VoIPControllerWrapper::SetEncryptionKey(const Platform::Array<uint8>^ key, bool isOutgoing){
	if(key->Length!=256)
		throw ref new Platform::InvalidArgumentException("Encryption key must be exactly 256 bytes long");
	controller->SetEncryptionKey((char*)key->Data, isOutgoing);
}

int VoIPControllerWrapper::GetSignalBarsCount(){
	return controller->GetSignalBarsCount();
}

CallState VoIPControllerWrapper::GetConnectionState(){
	return (CallState)controller->GetConnectionState();
}

TrafficStats^ VoIPControllerWrapper::GetStats(){
	tgvoip::VoIPController::TrafficStats _stats;
	controller->GetStats(&_stats);

	TrafficStats^ stats = ref new TrafficStats();
	stats->bytesSentWifi = _stats.bytesSentWifi;
	stats->bytesSentMobile = _stats.bytesSentMobile;
	stats->bytesRecvdWifi = _stats.bytesRecvdWifi;
	stats->bytesRecvdMobile = _stats.bytesRecvdMobile;

	return stats;
}

Platform::String^ VoIPControllerWrapper::GetDebugString(){
	std::string log = controller->GetDebugString();
	size_t len = sizeof(wchar_t)*(log.length() + 1);
	wchar_t* wlog = (wchar_t*)malloc(len);
	MultiByteToWideChar(CP_UTF8, 0, log.c_str(), -1, wlog, len / sizeof(wchar_t));
	Platform::String^ res = ref new Platform::String(wlog);
	free(wlog);
	return res;
}

Platform::String^ VoIPControllerWrapper::GetDebugLog(){
	std::string log=controller->GetDebugLog();
	size_t len=sizeof(wchar_t)*(log.length()+1);
	wchar_t* wlog=(wchar_t*)malloc(len);
	MultiByteToWideChar(CP_UTF8, 0, log.c_str(), -1, wlog, len/sizeof(wchar_t));
	Platform::String^ res=ref new Platform::String(wlog);
	free(wlog);
	return res;
}

Error VoIPControllerWrapper::GetLastError(){
	return (Error)controller->GetLastError();
}

Platform::String^ VoIPControllerWrapper::GetVersion(){
	const char* v=VoIPController::GetVersion();
	wchar_t buf[32];
	MultiByteToWideChar(CP_UTF8, 0, v, -1, buf, sizeof(buf));
	return ref new Platform::String(buf);
}

void VoIPControllerWrapper::OnStateChanged(VoIPController* c, int state){
	reinterpret_cast<VoIPControllerWrapper^>(c->implData)->OnStateChangedInternal(state);
}

void VoIPControllerWrapper::OnSignalBarsChanged(VoIPController* c, int count){
	reinterpret_cast<VoIPControllerWrapper^>(c->implData)->OnSignalBarsChangedInternal(count);
}

void VoIPControllerWrapper::OnStateChangedInternal(int state){
	CallStateChanged(this, (CallState)state);
}

void VoIPControllerWrapper::OnSignalBarsChangedInternal(int count){
	SignalBarsChanged(this, count);
}

void VoIPControllerWrapper::SetConfig(VoIPConfig^ wrapper){
	VoIPController::Config config{0};
	config.initTimeout=wrapper->initTimeout;
	config.recvTimeout=wrapper->recvTimeout;
	config.dataSaving=(int)wrapper->dataSaving;
	config.logFilePath;
	config.statsDumpFilePath;

	config.enableAEC=wrapper->enableAEC;
	config.enableNS=wrapper->enableNS;
	config.enableAGC=wrapper->enableAGC;

	config.enableCallUpgrade=wrapper->enableCallUpgrade;

	config.logPacketStats=wrapper->logPacketStats;
	config.enableVolumeControl=wrapper->enableVolumeControl;

	config.enableVideoSend=wrapper->enableVideoSend;
	config.enableVideoReceive=wrapper->enableVideoReceive;

	if(wrapper->logFilePath!=nullptr&&!wrapper->logFilePath->IsEmpty()){
		config.logFilePath = wstring(wrapper->logFilePath->Data());
	}
	if (wrapper->statsDumpFilePath != nullptr&&!wrapper->statsDumpFilePath->IsEmpty()){
		config.statsDumpFilePath = wstring(wrapper->statsDumpFilePath->Data());
	}

	controller->SetConfig(config);
}

void VoIPControllerWrapper::SetProxy(ProxyProtocol protocol, Platform::String^ address, uint16_t port, Platform::String^ username, Platform::String^ password){
	char _address[2000];
	char _username[256];
	char _password[256];

	WideCharToMultiByte(CP_UTF8, 0, address->Data(), -1, _address, sizeof(_address), NULL, NULL);
	WideCharToMultiByte(CP_UTF8, 0, username->Data(), -1, _username, sizeof(_username), NULL, NULL);
	WideCharToMultiByte(CP_UTF8, 0, password->Data(), -1, _password, sizeof(_password), NULL, NULL);

	controller->SetProxy((int)protocol, _address, port, _username, _password);
}

void VoIPControllerWrapper::SetAudioOutputGainControlEnabled(bool enabled){
	controller->SetAudioOutputGainControlEnabled(enabled);
}

void VoIPControllerWrapper::SetInputVolume(float level){
	controller->SetInputVolume(level);
}

void VoIPControllerWrapper::SetOutputVolume(float level){
	controller->SetOutputVolume(level);
}

void VoIPControllerWrapper::UpdateServerConfig(Platform::String^ json){
	std::string config=ToUtf8(json->Data(), json->Length());
	ServerConfig::GetSharedInstance()->Update(config);
}

void VoIPControllerWrapper::SwitchSpeaker(bool external){
	auto routingManager = AudioRoutingManager::GetDefault();
	if (external){
		routingManager->SetAudioEndpoint(AudioRoutingEndpoint::Speakerphone);
	}
	else{
		if ((routingManager->AvailableAudioEndpoints & AvailableAudioRoutingEndpoints::Bluetooth) == AvailableAudioRoutingEndpoints::Bluetooth){
			routingManager->SetAudioEndpoint(AudioRoutingEndpoint::Bluetooth);
		}
		else if ((routingManager->AvailableAudioEndpoints & AvailableAudioRoutingEndpoints::Earpiece) == AvailableAudioRoutingEndpoints::Earpiece){
			routingManager->SetAudioEndpoint(AudioRoutingEndpoint::Earpiece);
		}
	}
}

void MicrosoftCryptoImpl::AesIgeEncrypt(uint8_t* in, uint8_t* out, size_t len, uint8_t* key, uint8_t* iv){
	IBuffer^ keybuf=IBufferFromPtr(key, 32);
	CryptographicKey^ _key=aesKeyProvider->CreateSymmetricKey(keybuf);
	uint8_t tmpOut[16];
	uint8_t* xPrev=iv+16;
	uint8_t* yPrev=iv;
	uint8_t x[16];
	uint8_t y[16];
	for(size_t offset=0;offset<len;offset+=16){
		for (size_t i=0;i<16;i++){
			if (offset+i < len){
				x[i] = in[offset+i];
			}
			else{
				x[i]=0;
			}
		}
		XorInt128(x, yPrev, y);
		IBuffer^ inbuf=IBufferFromPtr(y, 16);
		IBuffer^ outbuf=CryptographicEngine::Encrypt(_key, inbuf, nullptr);
		IBufferToPtr(outbuf, 16, tmpOut);
		XorInt128(tmpOut, xPrev, y);
		memcpy(xPrev, x, 16);
		memcpy(yPrev, y, 16);
		memcpy(out+offset, y, 16);
	}
}

void MicrosoftCryptoImpl::AesIgeDecrypt(uint8_t* in, uint8_t* out, size_t len, uint8_t* key, uint8_t* iv){
	IBuffer^ keybuf=IBufferFromPtr(key, 32);
	CryptographicKey^ _key=aesKeyProvider->CreateSymmetricKey(keybuf);
	uint8_t tmpOut[16];
	uint8_t* xPrev=iv;
	uint8_t* yPrev=iv+16;
	uint8_t x[16];
	uint8_t y[16];
	for(size_t offset=0;offset<len;offset+=16){
		for (size_t i=0;i<16;i++){
			if (offset+i < len){
				x[i] = in[offset+i];
			}
			else{
				x[i]=0;
			}
		}
		XorInt128(x, yPrev, y);
		IBuffer^ inbuf=IBufferFromPtr(y, 16);
		IBuffer^ outbuf=CryptographicEngine::Decrypt(_key, inbuf, nullptr);
		IBufferToPtr(outbuf, 16, tmpOut);
		XorInt128(tmpOut, xPrev, y);
		memcpy(xPrev, x, 16);
		memcpy(yPrev, y, 16);
		memcpy(out+offset, y, 16);
	}
}

#define GETU32(pt) (((uint32_t)(pt)[0] << 24) ^ ((uint32_t)(pt)[1] << 16) ^ ((uint32_t)(pt)[2] <<  8) ^ ((uint32_t)(pt)[3]))
#define PUTU32(ct, st) { (ct)[0] = (u8)((st) >> 24); (ct)[1] = (u8)((st) >> 16); (ct)[2] = (u8)((st) >> 8); (ct)[3] = (u8)(st); }

typedef  uint8_t u8;

#define L_ENDIAN

/* increment counter (128-bit int) by 2^64 */
static void AES_ctr128_inc(unsigned char *counter) {
	unsigned long c;

	/* Grab 3rd dword of counter and increment */
#ifdef L_ENDIAN
	c = GETU32(counter + 8);
	c++;
	PUTU32(counter + 8, c);
#else
	c = GETU32(counter + 4);
	c++;
	PUTU32(counter + 4, c);
#endif

	/* if no overflow, we're done */
	if (c)
		return;

	/* Grab top dword of counter and increment */
#ifdef L_ENDIAN
	c = GETU32(counter + 12);
	c++;
	PUTU32(counter + 12, c);
#else
	c = GETU32(counter + 0);
	c++;
	PUTU32(counter + 0, c);
#endif

}

void MicrosoftCryptoImpl::AesCtrEncrypt(uint8_t* inout, size_t len, uint8_t* key, uint8_t* counter, uint8_t* ecount_buf, uint32_t* num){
	unsigned int n;
	unsigned long l = len;

	//assert(in && out && key && counter && num);
	//assert(*num < AES_BLOCK_SIZE);

	IBuffer^ keybuf = IBufferFromPtr(key, 32);
	CryptographicKey^ _key = aesKeyProvider->CreateSymmetricKey(keybuf);

	n = *num;

	while (l--) {
		if (n == 0) {
			IBuffer^ inbuf = IBufferFromPtr(counter, 16);
			IBuffer^ outbuf = CryptographicEngine::Encrypt(_key, inbuf, nullptr);
			IBufferToPtr(outbuf, 16, ecount_buf);
			//AES_encrypt(counter, ecount_buf, key);
			AES_ctr128_inc(counter);
		}
		*inout = *(inout++) ^ ecount_buf[n];
		n = (n + 1) % 16;
	}

	*num = n;
}

void MicrosoftCryptoImpl::SHA1(uint8_t* msg, size_t len, uint8_t* out){
	//EnterCriticalSection(&hashMutex);

	IBuffer^ arr=IBufferFromPtr(msg, len);
	CryptographicHash^ hash=sha1Provider->CreateHash();
	hash->Append(arr);
	IBuffer^ res=hash->GetValueAndReset();
	IBufferToPtr(res, 20, out);

	//LeaveCriticalSection(&hashMutex);
}

void MicrosoftCryptoImpl::SHA256(uint8_t* msg, size_t len, uint8_t* out){
	//EnterCriticalSection(&hashMutex);

	IBuffer^ arr=IBufferFromPtr(msg, len);
	CryptographicHash^ hash=sha256Provider->CreateHash();
	hash->Append(arr);
	IBuffer^ res=hash->GetValueAndReset();
	IBufferToPtr(res, 32, out);
	//LeaveCriticalSection(&hashMutex);
}

void MicrosoftCryptoImpl::RandBytes(uint8_t* buffer, size_t len){
	IBuffer^ res=CryptographicBuffer::GenerateRandom(len);
	IBufferToPtr(res, len, buffer);
}

void MicrosoftCryptoImpl::Init(){
	/*sha1Hash=HashAlgorithmProvider::OpenAlgorithm(HashAlgorithmNames::Sha1)->CreateHash();
	sha256Hash=HashAlgorithmProvider::OpenAlgorithm(HashAlgorithmNames::Sha256)->CreateHash();*/
	sha1Provider=HashAlgorithmProvider::OpenAlgorithm(HashAlgorithmNames::Sha1);
	sha256Provider=HashAlgorithmProvider::OpenAlgorithm(HashAlgorithmNames::Sha256);
	aesKeyProvider=SymmetricKeyAlgorithmProvider::OpenAlgorithm(SymmetricAlgorithmNames::AesEcb);
}

void MicrosoftCryptoImpl::XorInt128(uint8_t* a, uint8_t* b, uint8_t* out){
	uint64_t* _a=reinterpret_cast<uint64_t*>(a);
	uint64_t* _b=reinterpret_cast<uint64_t*>(b);
	uint64_t* _out=reinterpret_cast<uint64_t*>(out);
	_out[0]=_a[0]^_b[0];
	_out[1]=_a[1]^_b[1];
}

void MicrosoftCryptoImpl::IBufferToPtr(IBuffer^ buffer, size_t len, uint8_t* out)
{
	ComPtr<IBufferByteAccess> bufferByteAccess;
	reinterpret_cast<IInspectable*>(buffer)->QueryInterface(IID_PPV_ARGS(&bufferByteAccess));

	byte* hashBuffer;
	bufferByteAccess->Buffer(&hashBuffer);
	CopyMemory(out, hashBuffer, len);
}

IBuffer^ MicrosoftCryptoImpl::IBufferFromPtr(uint8_t* msg, size_t len)
{
	ComPtr<NativeBuffer> nativeBuffer=Make<NativeBuffer>((byte *)msg, len);
	return reinterpret_cast<IBuffer^>(nativeBuffer.Get());
}

/*Platform::String^ VoIPControllerWrapper::TestAesIge(){
	MicrosoftCryptoImpl::Init();
	Platform::String^ res="";
	Platform::Array<uint8>^ data=ref new Platform::Array<uint8>(32);
	Platform::Array<uint8>^ out=ref new Platform::Array<uint8>(32);
	Platform::Array<uint8>^ key=ref new Platform::Array<uint8>(16);
	Platform::Array<uint8>^ iv=ref new Platform::Array<uint8>(32);
	
	
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("0000000000000000000000000000000000000000000000000000000000000000"), &data);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"), &iv);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("000102030405060708090a0b0c0d0e0f"), &key);
	MicrosoftCryptoImpl::AesIgeEncrypt(data->Data, out->Data, 32, key->Data, iv->Data);
	res+=CryptographicBuffer::EncodeToHexString(CryptographicBuffer::CreateFromByteArray(out));
	res+="\n";

	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("1A8519A6557BE652E9DA8E43DA4EF4453CF456B4CA488AA383C79C98B34797CB"), &data);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"), &iv);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("000102030405060708090a0b0c0d0e0f"), &key);
	MicrosoftCryptoImpl::AesIgeDecrypt(data->Data, out->Data, 32, key->Data, iv->Data);
	res+=CryptographicBuffer::EncodeToHexString(CryptographicBuffer::CreateFromByteArray(out));
	res+="\n";

	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("99706487A1CDE613BC6DE0B6F24B1C7AA448C8B9C3403E3467A8CAD89340F53B"), &data);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("6D656E746174696F6E206F6620494745206D6F646520666F72204F70656E5353"), &iv);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("5468697320697320616E20696D706C65"), &key);
	MicrosoftCryptoImpl::AesIgeEncrypt(data->Data, out->Data, 32, key->Data, iv->Data);
	res+=CryptographicBuffer::EncodeToHexString(CryptographicBuffer::CreateFromByteArray(out));
	res+="\n";

	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("4C2E204C6574277320686F70652042656E20676F74206974207269676874210A"), &data);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("6D656E746174696F6E206F6620494745206D6F646520666F72204F70656E5353"), &iv);
	CryptographicBuffer::CopyToByteArray(CryptographicBuffer::DecodeFromHexString("5468697320697320616E20696D706C65"), &key);
	MicrosoftCryptoImpl::AesIgeDecrypt(data->Data, out->Data, 32, key->Data, iv->Data);
	res+=CryptographicBuffer::EncodeToHexString(CryptographicBuffer::CreateFromByteArray(out));
	return res;
}*/
#include "InstanceImplLegacy.h"

#include <stdarg.h>

extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/modes.h>
#endif
#include <openssl/rand.h>
}

void tgvoip_openssl_aes_ige_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_ige_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 32*8, &akey);
  AES_ige_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

void tgvoip_openssl_rand_bytes(uint8_t* buffer, size_t len){
  RAND_bytes(buffer, len);
}

void tgvoip_openssl_sha1(uint8_t* msg, size_t len, uint8_t* output){
  SHA1(msg, len, output);
}

void tgvoip_openssl_sha256(uint8_t* msg, size_t len, uint8_t* output){
  SHA256(msg, len, output);
}

void tgvoip_openssl_aes_ctr_encrypt(uint8_t* inout, size_t length, uint8_t* key, uint8_t* iv, uint8_t* ecount, uint32_t* num){
  AES_KEY akey;
  AES_set_encrypt_key(key, 32*8, &akey);
#ifdef OPENSSL_IS_BORINGSSL
  AES_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num);
#else
  CRYPTO_ctr128_encrypt(inout, inout, length, &akey, iv, ecount, num, (block128_f) AES_encrypt);
#endif
}

void tgvoip_openssl_aes_cbc_encrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_encrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_ENCRYPT);
}

void tgvoip_openssl_aes_cbc_decrypt(uint8_t* in, uint8_t* out, size_t length, uint8_t* key, uint8_t* iv){
  AES_KEY akey;
  AES_set_decrypt_key(key, 256, &akey);
  AES_cbc_encrypt(in, out, length, &akey, iv, AES_DECRYPT);
}

tgvoip::CryptoFunctions tgvoip::VoIPController::crypto = {
	tgvoip_openssl_rand_bytes,
	tgvoip_openssl_sha1,
	tgvoip_openssl_sha256,
	tgvoip_openssl_aes_ige_encrypt,
	tgvoip_openssl_aes_ige_decrypt,
	tgvoip_openssl_aes_ctr_encrypt,
	tgvoip_openssl_aes_cbc_encrypt,
	tgvoip_openssl_aes_cbc_decrypt
};

namespace tgcalls {

InstanceImplLegacy::InstanceImplLegacy(Descriptor &&descriptor) :
onStateUpdated_(std::move(descriptor.stateUpdated)),
onSignalBarsUpdated_(std::move(descriptor.signalBarsUpdated)) {
	controller_ = new tgvoip::VoIPController();
	controller_->implData = this;

	controller_->SetPersistentState(descriptor.persistentState.value);

	if (const auto proxy = descriptor.proxy.get()) {
		controller_->SetProxy(tgvoip::PROXY_SOCKS5, proxy->host, proxy->port, proxy->login, proxy->password);
	}

	auto callbacks = tgvoip::VoIPController::Callbacks();
	callbacks.connectionStateChanged = &InstanceImplLegacy::ControllerStateCallback;
	callbacks.groupCallKeyReceived = nullptr;
	callbacks.groupCallKeySent = nullptr;
	callbacks.signalBarCountChanged = &InstanceImplLegacy::SignalBarsCallback;
	callbacks.upgradeToGroupCallRequested = nullptr;
	controller_->SetCallbacks(callbacks);

	std::vector<tgvoip::Endpoint> mappedEndpoints;
	for (auto endpoint : descriptor.endpoints) {
		tgvoip::Endpoint::Type mappedType;
		switch (endpoint.type) {
			case EndpointType::UdpRelay:
				mappedType = tgvoip::Endpoint::Type::UDP_RELAY;
				break;
			case EndpointType::Lan:
				mappedType = tgvoip::Endpoint::Type::UDP_P2P_LAN;
				break;
			case EndpointType::Inet:
				mappedType = tgvoip::Endpoint::Type::UDP_P2P_INET;
				break;
			case EndpointType::TcpRelay:
				mappedType = tgvoip::Endpoint::Type::TCP_RELAY;
				break;
			default:
				mappedType = tgvoip::Endpoint::Type::UDP_RELAY;
				break;
		}

		tgvoip::IPv4Address address(endpoint.host.ipv4);
		tgvoip::IPv6Address addressv6(endpoint.host.ipv6);

		mappedEndpoints.emplace_back(endpoint.endpointId, endpoint.port, address, addressv6, mappedType, endpoint.peerTag);
	}

	const auto mappedDataSaving = [&] {
		switch (descriptor.config.dataSaving) {
		case DataSaving::Mobile:
			return tgvoip::DATA_SAVING_MOBILE;
		case DataSaving::Always:
			return tgvoip::DATA_SAVING_ALWAYS;
		default:
			return tgvoip::DATA_SAVING_NEVER;
		}
	}();

	tgvoip::VoIPController::Config mappedConfig(
		descriptor.config.initializationTimeout,
		descriptor.config.receiveTimeout,
		mappedDataSaving,
		descriptor.config.enableAEC,
		descriptor.config.enableNS,
		descriptor.config.enableAGC,
		descriptor.config.enableCallUpgrade
	);
	mappedConfig.enableVolumeControl = descriptor.config.enableVolumeControl;
	mappedConfig.logFilePath = descriptor.config.logPath.data;
	mappedConfig.statsDumpFilePath = {};

	controller_->SetConfig(mappedConfig);

	setNetworkType(descriptor.initialNetworkType);

	controller_->SetEncryptionKey((char *)(descriptor.encryptionKey.value->data()), descriptor.encryptionKey.isOutgoing);
	controller_->SetRemoteEndpoints(mappedEndpoints, descriptor.config.enableP2P, descriptor.config.maxApiLayer);

	controller_->Start();

	controller_->Connect();

	controller_->SetCurrentAudioInput(descriptor.mediaDevicesConfig.audioInputId);
	controller_->SetCurrentAudioOutput(descriptor.mediaDevicesConfig.audioOutputId);
	controller_->SetInputVolume(descriptor.mediaDevicesConfig.inputVolume);
	controller_->SetOutputVolume(descriptor.mediaDevicesConfig.outputVolume);
}

InstanceImplLegacy::~InstanceImplLegacy() {
	if (controller_) {
        stop([](FinalState state){});
    }
}

void InstanceImplLegacy::setNetworkType(NetworkType networkType) {
	const auto mappedType = [&] {
		switch (networkType) {
		case NetworkType::Unknown:
			return tgvoip::NET_TYPE_UNKNOWN;
		case NetworkType::Gprs:
			return tgvoip::NET_TYPE_GPRS;
		case NetworkType::Edge:
			return tgvoip::NET_TYPE_EDGE;
		case NetworkType::ThirdGeneration:
			return tgvoip::NET_TYPE_3G;
		case NetworkType::Hspa:
			return tgvoip::NET_TYPE_HSPA;
		case NetworkType::Lte:
			return tgvoip::NET_TYPE_LTE;
		case NetworkType::WiFi:
			return tgvoip::NET_TYPE_WIFI;
		case NetworkType::Ethernet:
			return tgvoip::NET_TYPE_ETHERNET;
		case NetworkType::OtherHighSpeed:
			return tgvoip::NET_TYPE_OTHER_HIGH_SPEED;
		case NetworkType::OtherLowSpeed:
			return tgvoip::NET_TYPE_OTHER_LOW_SPEED;
		case NetworkType::OtherMobile:
			return tgvoip::NET_TYPE_OTHER_MOBILE;
		case NetworkType::Dialup:
			return tgvoip::NET_TYPE_DIALUP;
		default:
			return tgvoip::NET_TYPE_UNKNOWN;
		}
	}();

	controller_->SetNetworkType(mappedType);
}

void InstanceImplLegacy::setMuteMicrophone(bool muteMicrophone) {
	controller_->SetMicMute(muteMicrophone);
}

void InstanceImplLegacy::receiveSignalingData(const std::vector<uint8_t> &data) {
}

void InstanceImplLegacy::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
}

void InstanceImplLegacy::sendVideoDeviceUpdated() {
}

void InstanceImplLegacy::setRequestedVideoAspect(float aspect) {
}

void InstanceImplLegacy::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
}

void InstanceImplLegacy::setAudioOutputGainControlEnabled(bool enabled) {
	controller_->SetAudioOutputGainControlEnabled(enabled);
}

void InstanceImplLegacy::setEchoCancellationStrength(int strength) {
	controller_->SetEchoCancellationStrength(strength);
}

void InstanceImplLegacy::setAudioInputDevice(std::string id) {
	controller_->SetCurrentAudioInput(id);
}

void InstanceImplLegacy::setAudioOutputDevice(std::string id) {
	controller_->SetCurrentAudioOutput(id);
}

void InstanceImplLegacy::setInputVolume(float level) {
	controller_->SetInputVolume(level);
}

void InstanceImplLegacy::setOutputVolume(float level) {
	controller_->SetOutputVolume(level);
}

void InstanceImplLegacy::setAudioOutputDuckingEnabled(bool enabled) {
#if defined(__APPLE__) && TARGET_OS_OSX
	controller_->SetAudioOutputDuckingEnabled(enabled);
#endif // TARGET_OS_OSX
}

void InstanceImplLegacy::setIsLowBatteryLevel(bool isLowBatteryLevel) {
}

std::string InstanceImplLegacy::getLastError() {
	switch (controller_->GetLastError()) {
		case tgvoip::ERROR_INCOMPATIBLE: return "ERROR_INCOMPATIBLE";
		case tgvoip::ERROR_TIMEOUT: return "ERROR_TIMEOUT";
		case tgvoip::ERROR_AUDIO_IO: return "ERROR_AUDIO_IO";
		case tgvoip::ERROR_PROXY: return "ERROR_PROXY";
		default: return "ERROR_UNKNOWN";
	}
}

std::string InstanceImplLegacy::getDebugInfo() {
	return controller_->GetDebugString();
}

int64_t InstanceImplLegacy::getPreferredRelayId() {
	return controller_->GetPreferredRelayID();
}

TrafficStats InstanceImplLegacy::getTrafficStats() {
	tgvoip::VoIPController::TrafficStats stats;
	controller_->GetStats(&stats);
	auto result = TrafficStats();
	result.bytesSentWifi = stats.bytesSentWifi;
	result.bytesReceivedWifi = stats.bytesRecvdWifi;
	result.bytesSentMobile = stats.bytesSentMobile;
	result.bytesReceivedMobile = stats.bytesRecvdMobile;
	return result;
}

PersistentState InstanceImplLegacy::getPersistentState() {
	return {controller_->GetPersistentState()};
}

void InstanceImplLegacy::stop(std::function<void(FinalState)> completion) {
	controller_->Stop();

	auto result = FinalState();
	result.persistentState = getPersistentState();
	result.debugLog = controller_->GetDebugLog();
	result.trafficStats = getTrafficStats();
	result.isRatingSuggested = controller_->NeedRate();

	delete controller_;
	controller_ = nullptr;

    completion(result);
}

void InstanceImplLegacy::ControllerStateCallback(tgvoip::VoIPController *controller, int state) {
	const auto self = static_cast<InstanceImplLegacy*>(controller->implData);
	if (self->onStateUpdated_) {
		const auto mappedState = [&] {
			switch (state) {
			case tgvoip::STATE_WAIT_INIT:
				return State::WaitInit;
			case tgvoip::STATE_WAIT_INIT_ACK:
				return State::WaitInitAck;
			case tgvoip::STATE_ESTABLISHED:
				return State::Established;
			case tgvoip::STATE_FAILED:
				return State::Failed;
			case tgvoip::STATE_RECONNECTING:
				return State::Reconnecting;
			default:
				return State::Established;
			}
		}();

		self->onStateUpdated_(mappedState);
	}
}

void InstanceImplLegacy::SignalBarsCallback(tgvoip::VoIPController *controller, int signalBars) {
	const auto self = static_cast<InstanceImplLegacy*>(controller->implData);
	if (self->onSignalBarsUpdated_) {
		self->onSignalBarsUpdated_(signalBars);
	}
}

int InstanceImplLegacy::GetConnectionMaxLayer() {
	return tgvoip::VoIPController::GetConnectionMaxLayer();
}

std::vector<std::string> InstanceImplLegacy::GetVersions() {
	std::vector<std::string> result;
	result.push_back("2.4.4");
	return result;
}

template <>
bool Register<InstanceImplLegacy>() {
	return Meta::RegisterOne<InstanceImplLegacy>();
}

void SetLegacyGlobalServerConfig(const std::string &serverConfig) {
	tgvoip::ServerConfig::GetSharedInstance()->Update(serverConfig);
}

} // namespace tgcalls

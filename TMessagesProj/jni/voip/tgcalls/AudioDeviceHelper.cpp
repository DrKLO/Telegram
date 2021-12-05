#include "AudioDeviceHelper.h"

#include "modules/audio_device/include/audio_device.h"
#include "rtc_base/logging.h"

namespace tgcalls {
namespace {

bool SkipDefaultDevice(const char *name) {
	const auto utfName = std::string(name);
#ifdef WEBRTC_WIN
	return (utfName.rfind("Default - ", 0) == 0)
		|| (utfName.rfind("Communication - ", 0) == 0);
#elif defined WEBRTC_MAC
	return (utfName.rfind("default (", 0) == 0)
		&& (utfName.find(")", utfName.size() - 1) == utfName.size() - 1);
#else
	return false;
#endif // WEBRTC_WIN || WEBRTC_MAC
}

} // namespace

void SetAudioInputDeviceById(webrtc::AudioDeviceModule *adm, const std::string &id) {
	const auto recording = adm->Recording() || adm->RecordingIsInitialized();
	if (recording) {
		adm->StopRecording();
	}
	auto specific = false;
	const auto finish = [&] {
		if (!specific) {
			if (const auto result = adm->SetRecordingDevice(webrtc::AudioDeviceModule::kDefaultCommunicationDevice)) {
				RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): SetRecordingDevice(kDefaultCommunicationDevice) failed: " << result << ".";
			} else {
				RTC_LOG(LS_INFO) << "setAudioInputDevice(" << id << "): SetRecordingDevice(kDefaultCommunicationDevice) success.";
			}
		}
		if (recording && adm->InitRecording() == 0) {
			adm->StartRecording();
		}
	};
	if (id == "default" || id.empty()) {
		return finish();
	}
	const auto count = adm
		? adm->RecordingDevices()
		: int16_t(-666);
	if (count <= 0) {
		RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): Could not get recording devices count: " << count << ".";
		return finish();
	}

        int16_t order = !id.empty() && id[0] == '#' ? static_cast<int16_t>(std::stoi(id.substr(1))) : -1;
        for (auto i = 0; i != count; ++i) {
		char name[webrtc::kAdmMaxDeviceNameSize + 1] = { 0 };
		char guid[webrtc::kAdmMaxGuidSize + 1] = { 0 };
		adm->RecordingDeviceName(i, name, guid);
		if ((!SkipDefaultDevice(name) && id == guid) || order == i) {
			const auto result = adm->SetRecordingDevice(i);
			if (result != 0) {
				RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << ") name '" << std::string(name) << "' failed: " << result << ".";
			} else {
				RTC_LOG(LS_INFO) << "setAudioInputDevice(" << id << ") name '" << std::string(name) << "' success.";
				specific = true;
			}
			return finish();
		}
	}
	RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): Could not find recording device.";
	return finish();
}

void SetAudioOutputDeviceById(webrtc::AudioDeviceModule *adm, const std::string &id) {
	if (adm->Playing()) {
		adm->StopPlayout();
	}
	auto specific = false;
	const auto finish = [&] {
		if (!specific) {
			if (const auto result = adm->SetPlayoutDevice(webrtc::AudioDeviceModule::kDefaultCommunicationDevice)) {
				RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): SetPlayoutDevice(kDefaultCommunicationDevice) failed: " << result << ".";
			} else {
				RTC_LOG(LS_INFO) << "setAudioOutputDevice(" << id << "): SetPlayoutDevice(kDefaultCommunicationDevice) success.";
			}
		}
		if (adm->InitPlayout() == 0) {
			adm->StartPlayout();
		}
	};
	if (id == "default" || id.empty()) {
		return finish();
	}
	const auto count = adm
		? adm->PlayoutDevices()
		: int16_t(-666);
	if (count <= 0) {
		RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): Could not get playout devices count: " << count << ".";
		return finish();
	}
        int16_t order = !id.empty() && id[0] == '#' ? static_cast<int16_t>(std::stoi(id.substr(1))) : -1;
	for (auto i = 0; i != count; ++i) {
		char name[webrtc::kAdmMaxDeviceNameSize + 1] = { 0 };
		char guid[webrtc::kAdmMaxGuidSize + 1] = { 0 };
		adm->PlayoutDeviceName(i, name, guid);
		if ((!SkipDefaultDevice(name) && id == guid) || order == i) {
			const auto result = adm->SetPlayoutDevice(i);
			if (result != 0) {
				RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << ") name '" << std::string(name) << "' failed: " << result << ".";
			} else {
				RTC_LOG(LS_INFO) << "setAudioOutputDevice(" << id << ") name '" << std::string(name) << "' success.";
				specific = true;
			}
			return finish();
		}
	}
	RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): Could not find playout device.";
	return finish();
}

} // namespace tgcalls

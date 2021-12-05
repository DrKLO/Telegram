#include "Manager.h"

#include "rtc_base/byte_buffer.h"
#include "StaticThreads.h"

#include <fstream>

namespace tgcalls {
namespace {

void dumpStatsLog(const FilePath &path, const CallStats &stats) {
	if (path.data.empty()) {
		return;
	}
    std::ofstream file;
    file.open(path.data);

    file << "{";
    file << "\"v\":\"" << 1 << "\"";
    file << ",";

    file << "\"codec\":\"" << stats.outgoingCodec << "\"";
    file << ",";

    file << "\"bitrate\":[";
    bool addComma = false;
    for (auto &it : stats.bitrateRecords) {
        if (addComma) {
            file << ",";
        }
        file << "{";
        file << "\"t\":\"" << it.timestamp << "\"";
        file << ",";
        file << "\"b\":\"" << it.bitrate << "\"";
        file << "}";
        addComma = true;
    }
    file << "]";
    file << ",";

    file << "\"network\":[";
    addComma = false;
    for (auto &it : stats.networkRecords) {
        if (addComma) {
            file << ",";
        }
        file << "{";
        file << "\"t\":\"" << it.timestamp << "\"";
        file << ",";
        file << "\"e\":\"" << (int)(it.endpointType) << "\"";
        file << ",";
        file << "\"w\":\"" << (it.isLowCost ? 1 : 0) << "\"";
        file << "}";
        addComma = true;
    }
    file << "]";

    file << "}";

    file.close();
}

} // namespace

bool Manager::ResolvedNetworkStatus::operator==(const ResolvedNetworkStatus &rhs) {
    if (rhs.isLowCost != isLowCost) {
        return false;
    }
    if (rhs.isLowDataRequested != isLowDataRequested) {
        return false;
    }
    return true;
}

bool Manager::ResolvedNetworkStatus::operator!=(const ResolvedNetworkStatus &rhs) {
    return !(*this == rhs);
}

Manager::Manager(rtc::Thread *thread, Descriptor &&descriptor) :
_thread(thread),
_encryptionKey(descriptor.encryptionKey),
_signaling(
	EncryptedConnection::Type::Signaling,
	_encryptionKey,
	[=](int delayMs, int cause) { sendSignalingAsync(delayMs, cause); }),
_enableP2P(descriptor.config.enableP2P),
_enableTCP(descriptor.config.allowTCP),
_enableStunMarking(descriptor.config.enableStunMarking),
_protocolVersion(descriptor.config.protocolVersion),
_statsLogPath(descriptor.config.statsLogPath),
_rtcServers(std::move(descriptor.rtcServers)),
_proxy(std::move(descriptor.proxy)),
_mediaDevicesConfig(std::move(descriptor.mediaDevicesConfig)),
_videoCapture(std::move(descriptor.videoCapture)),
_stateUpdated(std::move(descriptor.stateUpdated)),
_remoteMediaStateUpdated(std::move(descriptor.remoteMediaStateUpdated)),
_remoteBatteryLevelIsLowUpdated(std::move(descriptor.remoteBatteryLevelIsLowUpdated)),
_remotePrefferedAspectRatioUpdated(std::move(descriptor.remotePrefferedAspectRatioUpdated)),
_signalingDataEmitted(std::move(descriptor.signalingDataEmitted)),
_signalBarsUpdated(std::move(descriptor.signalBarsUpdated)),
_audioLevelUpdated(std::move(descriptor.audioLevelUpdated)),
_createAudioDeviceModule(std::move(descriptor.createAudioDeviceModule)),
_enableHighBitrateVideo(descriptor.config.enableHighBitrateVideo),
_dataSaving(descriptor.config.dataSaving),
_platformContext(descriptor.platformContext) {

	assert(_thread->IsCurrent());
	assert(_stateUpdated != nullptr);
	assert(_signalingDataEmitted != nullptr);

    _preferredCodecs = descriptor.config.preferredVideoCodecs;

	_sendSignalingMessage = [=](const Message &message) {
		if (const auto prepared = _signaling.prepareForSending(message)) {
			_signalingDataEmitted(prepared->bytes);
			return prepared->counter;
		}
		return uint32_t(0);
	};
	_sendTransportMessage = [=](Message &&message) {
		_networkManager->perform(RTC_FROM_HERE, [message = std::move(message)](NetworkManager *networkManager) {
			networkManager->sendMessage(message);
		});
	};
}

Manager::~Manager() {
	assert(_thread->IsCurrent());
}

void Manager::sendSignalingAsync(int delayMs, int cause) {
	auto task = [weak = std::weak_ptr<Manager>(shared_from_this()), cause] {
		const auto strong = weak.lock();
		if (!strong) {
			return;
		}
		if (const auto prepared = strong->_signaling.prepareForSendingService(cause)) {
			strong->_signalingDataEmitted(prepared->bytes);
		}
	};
	if (delayMs) {
		_thread->PostDelayedTask(RTC_FROM_HERE, std::move(task), delayMs);
	} else {
		_thread->PostTask(RTC_FROM_HERE, std::move(task));
	}
}

void Manager::start() {
	const auto weak = std::weak_ptr<Manager>(shared_from_this());
	const auto thread = _thread;
	const auto sendSignalingMessage = [=](Message &&message) {
		thread->PostTask(RTC_FROM_HERE, [=, message = std::move(message)]() mutable {
			const auto strong = weak.lock();
			if (!strong) {
				return;
			}
			strong->_sendSignalingMessage(std::move(message));
		});
	};
	_networkManager.reset(new ThreadLocalObject<NetworkManager>(StaticThreads::getNetworkThread(), [weak, thread, sendSignalingMessage, encryptionKey = _encryptionKey, enableP2P = _enableP2P, enableTCP = _enableTCP, enableStunMarking = _enableStunMarking, rtcServers = _rtcServers, proxy = std::move(_proxy)] () mutable {
		return new NetworkManager(
            StaticThreads::getNetworkThread(),
			encryptionKey,
			enableP2P,
            enableTCP,
            enableStunMarking,
			rtcServers,
			std::move(proxy),
			[=](const NetworkManager::State &state) {
				thread->PostTask(RTC_FROM_HERE, [=] {
					const auto strong = weak.lock();
					if (!strong) {
						return;
					}
                    State mappedState;
                    if (state.isFailed) {
                        mappedState = State::Failed;
                    } else {
                        mappedState = state.isReadyToSendData
                            ? State::Established
                            : State::Reconnecting;
                    }
                    bool isFirstConnection = false;
					if (state.isReadyToSendData) {
						if (!strong->_didConnectOnce) {
							strong->_didConnectOnce = true;
                            isFirstConnection = true;
						}
					}
					strong->_state = mappedState;
					strong->_stateUpdated(mappedState);

					strong->_mediaManager->perform(RTC_FROM_HERE, [=](MediaManager *mediaManager) {
						mediaManager->setIsConnected(state.isReadyToSendData);
					});

                    if (isFirstConnection) {
                        strong->sendInitialSignalingMessages();
                    }
				});
			},
			[=](DecryptedMessage &&message) {
				thread->PostTask(RTC_FROM_HERE, [=, message = std::move(message)]() mutable {
					if (const auto strong = weak.lock()) {
						strong->receiveMessage(std::move(message));
					}
				});
			},
			sendSignalingMessage,
			[=](int delayMs, int cause) {
				const auto task = [=] {
					if (const auto strong = weak.lock()) {
						strong->_networkManager->perform(RTC_FROM_HERE, [=](NetworkManager *networkManager) {
							networkManager->sendTransportService(cause);
							});
					}
				};
				if (delayMs) {
					thread->PostDelayedTask(RTC_FROM_HERE, task, delayMs);
				} else {
					thread->PostTask(RTC_FROM_HERE, task);
				}
			});
	}));
	bool isOutgoing = _encryptionKey.isOutgoing;
	_mediaManager.reset(new ThreadLocalObject<MediaManager>(StaticThreads::getMediaThread(), [weak, isOutgoing, protocolVersion = _protocolVersion, thread, sendSignalingMessage, videoCapture = _videoCapture, mediaDevicesConfig = _mediaDevicesConfig, enableHighBitrateVideo = _enableHighBitrateVideo, signalBarsUpdated = _signalBarsUpdated, audioLevelUpdated = _audioLevelUpdated, preferredCodecs = _preferredCodecs, createAudioDeviceModule = _createAudioDeviceModule, platformContext = _platformContext]() {
		return new MediaManager(
            StaticThreads::getMediaThread(),
			isOutgoing,
            protocolVersion,
			mediaDevicesConfig,
			videoCapture,
			sendSignalingMessage,
			[=](Message &&message) {
				thread->PostTask(RTC_FROM_HERE, [=, message = std::move(message)]() mutable {
					const auto strong = weak.lock();
					if (!strong) {
						return;
					}
					strong->_sendTransportMessage(std::move(message));
				});
			},
            signalBarsUpdated,
            audioLevelUpdated,
			createAudioDeviceModule,
			enableHighBitrateVideo,
            preferredCodecs,
            platformContext);
	}));
    _networkManager->perform(RTC_FROM_HERE, [](NetworkManager *networkManager) {
        networkManager->start();
    });
	_mediaManager->perform(RTC_FROM_HERE, [](MediaManager *mediaManager) {
		mediaManager->start();
	});
}

void Manager::receiveSignalingData(const std::vector<uint8_t> &data) {
	if (auto decrypted = _signaling.handleIncomingPacket((const char*)data.data(), data.size())) {
		receiveMessage(std::move(decrypted->main));
		for (auto &message : decrypted->additional) {
			receiveMessage(std::move(message));
		}
	}
}

void Manager::receiveMessage(DecryptedMessage &&message) {
	const auto data = &message.message.data;
	if (const auto candidatesList = absl::get_if<CandidatesListMessage>(data)) {
		_networkManager->perform(RTC_FROM_HERE, [message = std::move(message)](NetworkManager *networkManager) mutable {
			networkManager->receiveSignalingMessage(std::move(message));
		});
	} else if (const auto videoFormats = absl::get_if<VideoFormatsMessage>(data)) {
		_mediaManager->perform(RTC_FROM_HERE, [message = std::move(message)](MediaManager *mediaManager) mutable {
			mediaManager->receiveMessage(std::move(message));
		});
    } else if (const auto remoteMediaState = absl::get_if<RemoteMediaStateMessage>(data)) {
		if (_remoteMediaStateUpdated) {
			_remoteMediaStateUpdated(
				remoteMediaState->audio,
				remoteMediaState->video);
		}
        _mediaManager->perform(RTC_FROM_HERE, [video = remoteMediaState->video](MediaManager *mediaManager) {
            mediaManager->remoteVideoStateUpdated(video);
        });
	} else if (const auto remoteBatteryLevelIsLow = absl::get_if<RemoteBatteryLevelIsLowMessage>(data)) {
        if (_remoteBatteryLevelIsLowUpdated) {
			_remoteBatteryLevelIsLowUpdated(remoteBatteryLevelIsLow->batteryLow);
        }
    } else if (const auto remoteNetworkStatus = absl::get_if<RemoteNetworkStatusMessage>(data)) {
        _remoteNetworkIsLowCost = remoteNetworkStatus->isLowCost;
        _remoteIsLowDataRequested = remoteNetworkStatus->isLowDataRequested;
        updateCurrentResolvedNetworkStatus();
    } else {
        if (const auto videoParameters = absl::get_if<VideoParametersMessage>(data)) {
            float value = ((float)videoParameters->aspectRatio) / 1000.0;
			if (_remotePrefferedAspectRatioUpdated) {
				_remotePrefferedAspectRatioUpdated(value);
			}
        }
		_mediaManager->perform(RTC_FROM_HERE, [=, message = std::move(message)](MediaManager *mediaManager) mutable {
			mediaManager->receiveMessage(std::move(message));
		});
	}
}

void Manager::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
	assert(_didConnectOnce);

	if (_videoCapture == videoCapture) {
		return;
	}
    _videoCapture = videoCapture;
    _mediaManager->perform(RTC_FROM_HERE, [videoCapture](MediaManager *mediaManager) {
        mediaManager->setSendVideo(videoCapture);
    });
}

void Manager::sendVideoDeviceUpdated() {
    _mediaManager->perform(RTC_FROM_HERE, [](MediaManager *mediaManager) {
        mediaManager->sendVideoDeviceUpdated();
    });
}

void Manager::setRequestedVideoAspect(float aspect) {
    _mediaManager->perform(RTC_FROM_HERE, [aspect](MediaManager *mediaManager) {
        mediaManager->setRequestedVideoAspect(aspect);
    });
}

void Manager::setMuteOutgoingAudio(bool mute) {
	_mediaManager->perform(RTC_FROM_HERE, [mute](MediaManager *mediaManager) {
		mediaManager->setMuteOutgoingAudio(mute);
	});
}

void Manager::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
	_mediaManager->perform(RTC_FROM_HERE, [sink](MediaManager *mediaManager) {
		mediaManager->setIncomingVideoOutput(sink);
	});
}

void Manager::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _sendTransportMessage({ RemoteBatteryLevelIsLowMessage{ isLowBatteryLevel } });
}

void Manager::setIsLocalNetworkLowCost(bool isLocalNetworkLowCost) {
    if (isLocalNetworkLowCost != _localNetworkIsLowCost) {
        _networkManager->perform(RTC_FROM_HERE, [isLocalNetworkLowCost](NetworkManager *networkManager) {
            networkManager->setIsLocalNetworkLowCost(isLocalNetworkLowCost);
        });

        _localNetworkIsLowCost = isLocalNetworkLowCost;
        updateCurrentResolvedNetworkStatus();
    }
}

void Manager::getNetworkStats(std::function<void (TrafficStats, CallStats)> completion) {
    _networkManager->perform(RTC_FROM_HERE, [thread = _thread, weak = std::weak_ptr<Manager>(shared_from_this()), completion = std::move(completion), statsLogPath = _statsLogPath](NetworkManager *networkManager) {
        auto networkStats = networkManager->getNetworkStats();

        CallStats callStats;
        networkManager->fillCallStats(callStats);

        thread->PostTask(RTC_FROM_HERE, [weak, networkStats, completion = std::move(completion), callStats = std::move(callStats), statsLogPath = statsLogPath] {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->_mediaManager->perform(RTC_FROM_HERE, [networkStats, completion = std::move(completion), callStatsValue = std::move(callStats), statsLogPath = statsLogPath](MediaManager *mediaManager) {
                CallStats callStats = std::move(callStatsValue);
                mediaManager->fillCallStats(callStats);
                dumpStatsLog(statsLogPath, callStats);
                completion(networkStats, callStats);
            });
        });
    });
}

void Manager::updateCurrentResolvedNetworkStatus() {
    bool localIsLowDataRequested = false;
    switch (_dataSaving) {
        case DataSaving::Never:
            localIsLowDataRequested = false;
            break;
        case DataSaving::Mobile:
            localIsLowDataRequested = !_localNetworkIsLowCost;
            break;
        case DataSaving::Always:
            localIsLowDataRequested = true;
        default:
            break;
    }

    ResolvedNetworkStatus localStatus;
    localStatus.isLowCost = _localNetworkIsLowCost;
    localStatus.isLowDataRequested = localIsLowDataRequested;

    if (!_currentResolvedLocalNetworkStatus.has_value() || *_currentResolvedLocalNetworkStatus != localStatus) {
        _currentResolvedLocalNetworkStatus = localStatus;

        switch (_protocolVersion) {
            case ProtocolVersion::V1:
                if (_didConnectOnce) {
                    _sendTransportMessage({ RemoteNetworkStatusMessage{ localStatus.isLowCost, localStatus.isLowDataRequested } });
                }
                break;
            default:
                break;
        }
    }

    ResolvedNetworkStatus status;
    status.isLowCost = _localNetworkIsLowCost && _remoteNetworkIsLowCost;
    status.isLowDataRequested = localIsLowDataRequested || _remoteIsLowDataRequested;

    if (!_currentResolvedNetworkStatus.has_value() || *_currentResolvedNetworkStatus != status) {
        _currentResolvedNetworkStatus = status;
        _mediaManager->perform(RTC_FROM_HERE, [status](MediaManager *mediaManager) {
            mediaManager->setNetworkParameters(status.isLowCost, status.isLowDataRequested);
        });
    }
}

void Manager::sendInitialSignalingMessages() {
    if (_currentResolvedLocalNetworkStatus.has_value()) {
        switch (_protocolVersion) {
        case ProtocolVersion::V1:
            _sendTransportMessage({ RemoteNetworkStatusMessage{ _currentResolvedLocalNetworkStatus->isLowCost, _currentResolvedLocalNetworkStatus->isLowDataRequested } });
                break;
            default:
                break;
        }
    }
}

void Manager::setAudioInputDevice(std::string id) {
	_mediaManager->perform(RTC_FROM_HERE, [id](MediaManager *mediaManager) {
		mediaManager->setAudioInputDevice(id);
	});
}

void Manager::setAudioOutputDevice(std::string id) {
	_mediaManager->perform(RTC_FROM_HERE, [id](MediaManager *mediaManager) {
		mediaManager->setAudioOutputDevice(id);
	});
}

void Manager::setInputVolume(float level) {
	_mediaManager->perform(RTC_FROM_HERE, [level](MediaManager *mediaManager) {
		mediaManager->setInputVolume(level);
	});
}

void Manager::setOutputVolume(float level) {
	_mediaManager->perform(RTC_FROM_HERE, [level](MediaManager *mediaManager) {
		mediaManager->setOutputVolume(level);
	});
}

void Manager::addExternalAudioSamples(std::vector<uint8_t> &&samples) {
    _mediaManager->perform(RTC_FROM_HERE, [samples = std::move(samples)](MediaManager *mediaManager) mutable {
        mediaManager->addExternalAudioSamples(std::move(samples));
    });
}

} // namespace tgcalls

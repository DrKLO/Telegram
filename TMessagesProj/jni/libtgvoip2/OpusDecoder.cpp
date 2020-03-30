//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "OpusDecoder.h"
#include "audio/Resampler.h"
#include "logging.h"
#include <assert.h>
#include <math.h>
#include <algorithm>
#if defined HAVE_CONFIG_H || defined TGVOIP_USE_INSTALLED_OPUS
#include <opus/opus.h>
#else
#include "opus.h"
#endif

#include "VoIPController.h"

#define PACKET_SIZE (960 * 2)

using namespace tgvoip;

tgvoip::OpusDecoder::OpusDecoder(const std::shared_ptr<MediaStreamItf>& dst, bool isAsync, bool needEC) {
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

tgvoip::OpusDecoder::OpusDecoder(const std::unique_ptr<MediaStreamItf>& dst, bool isAsync, bool needEC) {
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

tgvoip::OpusDecoder::OpusDecoder(MediaStreamItf* dst, bool isAsync, bool needEC) {
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

void tgvoip::OpusDecoder::Initialize(bool isAsync, bool needEC) {
    async = isAsync;
    if (async) {
        decodedQueue = new BlockingQueue<Buffer>(33);
        semaphore = new Semaphore(32, 0);
    } else {
        decodedQueue = NULL;
        semaphore = NULL;
	}
    dec=opus_decoder_create(48000, 1, NULL);
    if (needEC)
        ecDec = opus_decoder_create(48000, 1, NULL);
	else
        ecDec = NULL;
    // todo buffer = reinterpret_cast<unsigned char*>(aligned_alloc(2, 8192));
    buffer=(unsigned char *) malloc(8192);
    lastDecoded = NULL;
    outputBufferSize = 0;
    echoCanceller = NULL;
    frameDuration = 20;
    consecutiveLostPackets = 0;
    enableDTX = false;
    silentPacketCount = 0;
    levelMeter = NULL;
    nextLen = 0;
    running = false;
    remainingDataLen = 0;
    processedBuffer = NULL;
    prevWasEC = false;
    prevLastSample = 0;
}

tgvoip::OpusDecoder::~OpusDecoder() {
	opus_decoder_destroy(dec);
    if (ecDec)
		opus_decoder_destroy(ecDec);
	free(buffer);
    if (decodedQueue)
		delete decodedQueue;
    if (semaphore)
		delete semaphore;
}


void tgvoip::OpusDecoder::SetEchoCanceller(EchoCanceller* canceller) {
    echoCanceller = canceller;
}

size_t tgvoip::OpusDecoder::Callback(unsigned char* data, size_t len, void* param) {
    return (reinterpret_cast<OpusDecoder*>(param)->HandleCallback(data, len));
}

size_t tgvoip::OpusDecoder::HandleCallback(unsigned char* data, size_t len) {
    if (async) {
        if (!running) {
			memset(data, 0, len);
			return 0;
		}
        if (outputBufferSize == 0) {
            outputBufferSize = len;
			int packetsNeeded;
            if (len > PACKET_SIZE)
                packetsNeeded = len / PACKET_SIZE;
			else
                packetsNeeded = 1;
            packetsNeeded *= 2;
			semaphore->Release(packetsNeeded);
		}
        assert(outputBufferSize == len && "output buffer size is supposed to be the same throughout callbacks");
        if (len == PACKET_SIZE) {
			Buffer lastDecoded=decodedQueue->GetBlocking();
            if (lastDecoded.IsEmpty())
				return 0;
			memcpy(data, *lastDecoded, PACKET_SIZE);
			semaphore->Release();
            if (silentPacketCount > 0) {
				silentPacketCount--;
                if (levelMeter)
                    levelMeter->Update(reinterpret_cast<int16_t*>(data), 0);
				return 0;
			}
            if (echoCanceller) {
				echoCanceller->SpeakerOutCallback(data, PACKET_SIZE);
			}
        } else {
			LOGE("Opus decoder buffer length != 960 samples");
			abort();
		}
    } else {
        if (remainingDataLen == 0 && silentPacketCount == 0) {
            int duration = DecodeNextFrame();
            remainingDataLen = static_cast<size_t>(duration) / 20 * 960 * 2;
		}
        if (silentPacketCount > 0 || remainingDataLen == 0 || !processedBuffer){
            if (silentPacketCount > 0)
				silentPacketCount--;
            memset(data, 0, 960 * 2);
            if (levelMeter)
                levelMeter->Update(reinterpret_cast<int16_t*>(data), 0);
			return 0;
		}
        memcpy(data, processedBuffer, 960 * 2);
        remainingDataLen -= 960 * 2;
        if (remainingDataLen > 0) {
            memmove(processedBuffer, processedBuffer + 960 * 2, remainingDataLen);
		}
	}
    if (levelMeter)
        levelMeter->Update(reinterpret_cast<int16_t*>(data), len / 2);
	return len;
}


void tgvoip::OpusDecoder::Start() {
    if (!async)
		return;
    running = true;
    thread = new Thread(std::bind(&tgvoip::OpusDecoder::RunThread, this));
	thread->SetName("opus_decoder");
	thread->SetMaxPriority();
	thread->Start();
}

void tgvoip::OpusDecoder::Stop() {
    if (!running || !async)
		return;
    running = false;
	semaphore->Release();
	thread->Join();
	delete thread;
}

void tgvoip::OpusDecoder::RunThread() {
	LOGI("decoder: packets per frame %d", packetsPerFrame);
    while(running) {
        int playbackDuration = DecodeNextFrame();
        for (int i = 0; i < playbackDuration / 20; i++) {
			semaphore->Acquire();
            if (!running) {
				LOGI("==== decoder exiting ====");
				return;
			}
            try {
				Buffer buf=bufferPool.Get();
                if (remainingDataLen > 0) {
                    for (effects::AudioEffect*& effect:postProcEffects) {
                        effect->Process(reinterpret_cast<int16_t*>(processedBuffer+(PACKET_SIZE * i)), 960);
					}
                    buf.CopyFrom(processedBuffer + (PACKET_SIZE * i), 0, PACKET_SIZE);
                } else {
					//LOGE("Error decoding, result=%d", size);
					memset(*buf, 0, PACKET_SIZE);
				}
				decodedQueue->Put(std::move(buf));
            } catch (const std::bad_alloc&) {
				LOGW("decoder: no buffers left!");
			}
		}
	}
}

int tgvoip::OpusDecoder::DecodeNextFrame() {
    int playbackDuration = 0;
    bool isEC = false;
    size_t len = jitterBuffer->HandleOutput(buffer, 8192, 0, true, playbackDuration, isEC);
    bool fec = false;
    if (!len) {
        fec = true;
        len = jitterBuffer->HandleOutput(buffer, 8192, 0, false, playbackDuration, isEC);
		//if(len)
		//	LOGV("Trying FEC...");
	}
	int size;
    if (len) {
        size = opus_decode(isEC ? ecDec : dec, buffer, len, reinterpret_cast<opus_int16*>(decodeBuffer), packetsPerFrame * 960, fec ? 1 : 0);
        consecutiveLostPackets = 0;
        if (prevWasEC != isEC && size) {
			// It turns out the waveforms generated by the PLC feature are also great to help smooth out the
			// otherwise audible transition between the frames from different decoders. Those are basically an extrapolation
			// of the previous successfully decoded data -- which is exactly what we need here.
            size = opus_decode(prevWasEC ? ecDec : dec, NULL, 0, reinterpret_cast<opus_int16*>(nextBuffer), packetsPerFrame * 960, 0);
            if (size) {
                int16_t* plcSamples = reinterpret_cast<int16_t*>(nextBuffer);
                int16_t* samples = reinterpret_cast<int16_t*>(decodeBuffer);
                constexpr float coeffs[] = {0.999802f, 0.995062f, 0.984031f, 0.966778f, 0.943413f, 0.914084f, 0.878975f, 0.838309f, 0.792344f, 0.741368f,
                                            0.685706f, 0.625708f, 0.561754f, 0.494249f, 0.423619f, 0.350311f, 0.274788f, 0.197527f, 0.119018f, 0.039757f};
                for (int i = 0; i < 20; i++) {
                    samples[i] = static_cast<int16_t>(round(plcSamples[i] * coeffs[i] + samples[i] * (1.f - coeffs[i])));
				}
			}
		}
        prevWasEC = isEC;
        prevLastSample = decodeBuffer[size - 1];
    } else { // do packet loss concealment
		consecutiveLostPackets++;
        if (consecutiveLostPackets > 2 && enableDTX) {
            silentPacketCount += packetsPerFrame;
            size = packetsPerFrame * 960;
        } else {
            size = opus_decode(prevWasEC ? ecDec : dec, NULL, 0, reinterpret_cast<opus_int16*>(decodeBuffer), packetsPerFrame * 960, 0);
			//LOGV("PLC");
		}
	}
    if (size < 0)
		LOGW("decoder: opus_decode error %d", size);
    remainingDataLen = size;
    if (playbackDuration == 80) {
        processedBuffer = buffer;
        audio::Resampler::Rescale60To80(reinterpret_cast<int16_t*>(decodeBuffer),
                                        reinterpret_cast<int16_t*>(processedBuffer));
    } else if (playbackDuration == 40) {
        processedBuffer = buffer;
        audio::Resampler::Rescale60To40(reinterpret_cast<int16_t*>(decodeBuffer),
                                        reinterpret_cast<int16_t*>(processedBuffer));
    } else {
        processedBuffer = decodeBuffer;
	}
	return playbackDuration;
}


void tgvoip::OpusDecoder::SetFrameDuration(uint32_t duration) {
    frameDuration = duration;
    packetsPerFrame = frameDuration / 20;
}


void tgvoip::OpusDecoder::SetJitterBuffer(std::shared_ptr<JitterBuffer> jitterBuffer) {
    this->jitterBuffer = jitterBuffer;
}

void tgvoip::OpusDecoder::SetDTX(bool enable) {
    enableDTX = enable;
}

void tgvoip::OpusDecoder::SetLevelMeter(AudioLevelMeter* levelMeter) {
    this->levelMeter = levelMeter;
}

void tgvoip::OpusDecoder::AddAudioEffect(effects::AudioEffect* effect) {
	postProcEffects.push_back(effect);
}

void tgvoip::OpusDecoder::RemoveAudioEffect(effects::AudioEffect *effect) {
    std::vector<effects::AudioEffect*>::iterator it = std::find(postProcEffects.begin(), postProcEffects.end(), effect);
    if (it != postProcEffects.end())
        postProcEffects.erase(it);
}

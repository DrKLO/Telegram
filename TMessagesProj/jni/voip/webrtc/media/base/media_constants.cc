/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/media_constants.h"

namespace cricket {

const int kVideoCodecClockrate = 90000;

const int kVideoMtu = 1200;
const int kVideoRtpSendBufferSize = 65536;
const int kVideoRtpRecvBufferSize = 262144;

const float kHighSystemCpuThreshold = 0.85f;
const float kLowSystemCpuThreshold = 0.65f;
const float kProcessCpuThreshold = 0.10f;

const char kRedCodecName[] = "red";
const char kUlpfecCodecName[] = "ulpfec";
const char kMultiplexCodecName[] = "multiplex";

// TODO(brandtr): Change this to 'flexfec' when we are confident that the
// header format is not changing anymore.
const char kFlexfecCodecName[] = "flexfec-03";

// draft-ietf-payload-flexible-fec-scheme-02.txt
const char kFlexfecFmtpRepairWindow[] = "repair-window";

// RFC 4588 RTP Retransmission Payload Format
const char kRtxCodecName[] = "rtx";
const char kCodecParamRtxTime[] = "rtx-time";
const char kCodecParamAssociatedPayloadType[] = "apt";

const char kCodecParamAssociatedCodecName[] = "acn";
// Parameters that do not follow the key-value convention
// are treated as having the empty string as key.
const char kCodecParamNotInNameValueFormat[] = "";

const char kOpusCodecName[] = "opus";
const char kIsacCodecName[] = "ISAC";
const char kL16CodecName[] = "L16";
const char kG722CodecName[] = "G722";
const char kIlbcCodecName[] = "ILBC";
const char kPcmuCodecName[] = "PCMU";
const char kPcmaCodecName[] = "PCMA";
const char kCnCodecName[] = "CN";
const char kDtmfCodecName[] = "telephone-event";

// draft-spittka-payload-rtp-opus-03.txt
const char kCodecParamPTime[] = "ptime";
const char kCodecParamMaxPTime[] = "maxptime";
const char kCodecParamMinPTime[] = "minptime";
const char kCodecParamSPropStereo[] = "sprop-stereo";
const char kCodecParamStereo[] = "stereo";
const char kCodecParamUseInbandFec[] = "useinbandfec";
const char kCodecParamUseDtx[] = "usedtx";
const char kCodecParamMaxAverageBitrate[] = "maxaveragebitrate";
const char kCodecParamMaxPlaybackRate[] = "maxplaybackrate";

const char kParamValueTrue[] = "1";
const char kParamValueEmpty[] = "";

const int kOpusDefaultMaxPTime = 120;
const int kOpusDefaultPTime = 20;
const int kOpusDefaultMinPTime = 3;
const int kOpusDefaultSPropStereo = 0;
const int kOpusDefaultStereo = 0;
const int kOpusDefaultUseInbandFec = 0;
const int kOpusDefaultUseDtx = 0;
const int kOpusDefaultMaxPlaybackRate = 48000;

const int kPreferredMaxPTime = 120;
const int kPreferredMinPTime = 10;
const int kPreferredSPropStereo = 0;
const int kPreferredStereo = 0;
const int kPreferredUseInbandFec = 0;

const char kPacketizationParamRaw[] = "raw";

const char kRtcpFbParamLntf[] = "goog-lntf";
const char kRtcpFbParamNack[] = "nack";
const char kRtcpFbNackParamPli[] = "pli";
const char kRtcpFbParamRemb[] = "goog-remb";
const char kRtcpFbParamTransportCc[] = "transport-cc";

const char kRtcpFbParamCcm[] = "ccm";
const char kRtcpFbCcmParamFir[] = "fir";
const char kRtcpFbParamRrtr[] = "rrtr";
const char kCodecParamMaxBitrate[] = "x-google-max-bitrate";
const char kCodecParamMinBitrate[] = "x-google-min-bitrate";
const char kCodecParamStartBitrate[] = "x-google-start-bitrate";
const char kCodecParamMaxQuantization[] = "x-google-max-quantization";

const char kComfortNoiseCodecName[] = "CN";

const char kVp8CodecName[] = "VP8";
const char kVp9CodecName[] = "VP9";
const char kAv1CodecName[] = "AV1";
const char kH264CodecName[] = "H264";
#ifndef DISABLE_H265
const char kH265CodecName[] = "H265";
#endif

// RFC 6184 RTP Payload Format for H.264 video
const char kH264FmtpProfileLevelId[] = "profile-level-id";
const char kH264FmtpLevelAsymmetryAllowed[] = "level-asymmetry-allowed";
const char kH264FmtpPacketizationMode[] = "packetization-mode";
const char kH264FmtpSpropParameterSets[] = "sprop-parameter-sets";
const char kH264FmtpSpsPpsIdrInKeyframe[] = "sps-pps-idr-in-keyframe";
const char kH264ProfileLevelConstrainedBaseline[] = "42e01f";
const char kH264ProfileLevelConstrainedHigh[] = "640c1f";
#ifndef DISABLE_H265
// RFC 7798 RTP Payload Format for H.265 video
const char kH265FmtpProfileSpace[] = "profile-space";
const char kH265FmtpProfileId[] = "profile-id";
const char kH265FmtpTierFlag[] = "tier-flag";
const char kH265FmtpLevelId[] = "level-id";
#endif

const char kVP9ProfileId[] = "profile-id";

const int kDefaultVideoMaxFramerate = 60;

const size_t kConferenceMaxNumSpatialLayers = 3;
const size_t kConferenceMaxNumTemporalLayers = 3;
const size_t kConferenceDefaultNumTemporalLayers = 3;

// RFC 3556 and RFC 3890
const char kApplicationSpecificBandwidth[] = "AS";
const char kTransportSpecificBandwidth[] = "TIAS";
}  // namespace cricket

/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_MEDIA_CONSTANTS_H_
#define MEDIA_BASE_MEDIA_CONSTANTS_H_

#include <stddef.h>

#include "rtc_base/system/rtc_export.h"

// This file contains constants related to media.

namespace cricket {

extern const int kVideoCodecClockrate;

extern const int kVideoMtu;
extern const int kVideoRtpSendBufferSize;
extern const int kVideoRtpRecvBufferSize;

// Default CPU thresholds.
extern const float kHighSystemCpuThreshold;
extern const float kLowSystemCpuThreshold;
extern const float kProcessCpuThreshold;

extern const char kRedCodecName[];
extern const char kUlpfecCodecName[];
extern const char kFlexfecCodecName[];
extern const char kMultiplexCodecName[];

extern const char kFlexfecFmtpRepairWindow[];

extern const char kRtxCodecName[];
extern const char kCodecParamRtxTime[];
extern const char kCodecParamAssociatedPayloadType[];

extern const char kCodecParamAssociatedCodecName[];
extern const char kCodecParamNotInNameValueFormat[];

extern const char kOpusCodecName[];
extern const char kL16CodecName[];
extern const char kG722CodecName[];
extern const char kIlbcCodecName[];
extern const char kPcmuCodecName[];
extern const char kPcmaCodecName[];
extern const char kCnCodecName[];
extern const char kDtmfCodecName[];

// Attribute parameters
extern const char kCodecParamPTime[];
extern const char kCodecParamMaxPTime[];
// fmtp parameters
extern const char kCodecParamMinPTime[];
extern const char kCodecParamSPropStereo[];
extern const char kCodecParamStereo[];
extern const char kCodecParamUseInbandFec[];
extern const char kCodecParamUseDtx[];
extern const char kCodecParamMaxAverageBitrate[];
extern const char kCodecParamMaxPlaybackRate[];

extern const char kParamValueTrue[];
// Parameters are stored as parameter/value pairs. For parameters who do not
// have a value, `kParamValueEmpty` should be used as value.
extern const char kParamValueEmpty[];

// opus parameters.
// Default value for maxptime according to
// http://tools.ietf.org/html/draft-spittka-payload-rtp-opus-03
extern const int kOpusDefaultMaxPTime;
extern const int kOpusDefaultPTime;
extern const int kOpusDefaultMinPTime;
extern const int kOpusDefaultSPropStereo;
extern const int kOpusDefaultStereo;
extern const int kOpusDefaultUseInbandFec;
extern const int kOpusDefaultUseDtx;
extern const int kOpusDefaultMaxPlaybackRate;

// Prefered values in this code base. Note that they may differ from the default
// values in http://tools.ietf.org/html/draft-spittka-payload-rtp-opus-03
// Only frames larger or equal to 10 ms are currently supported in this code
// base.
extern const int kPreferredMaxPTime;
extern const int kPreferredMinPTime;
extern const int kPreferredSPropStereo;
extern const int kPreferredStereo;
extern const int kPreferredUseInbandFec;

extern const char kPacketizationParamRaw[];

// rtcp-fb message in its first experimental stages. Documentation pending.
extern const char kRtcpFbParamLntf[];
// rtcp-fb messages according to RFC 4585
extern const char kRtcpFbParamNack[];
extern const char kRtcpFbNackParamPli[];
// rtcp-fb messages according to
// http://tools.ietf.org/html/draft-alvestrand-rmcat-remb-00
extern const char kRtcpFbParamRemb[];
// rtcp-fb messages according to
// https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
extern const char kRtcpFbParamTransportCc[];
// ccm submessages according to RFC 5104
extern const char kRtcpFbParamCcm[];
extern const char kRtcpFbCcmParamFir[];
// Receiver reference time report
// https://tools.ietf.org/html/rfc3611 section 4.4
extern const char kRtcpFbParamRrtr[];
// Google specific parameters
extern const char kCodecParamMaxBitrate[];
extern const char kCodecParamMinBitrate[];
extern const char kCodecParamStartBitrate[];
extern const char kCodecParamMaxQuantization[];

extern const char kComfortNoiseCodecName[];

RTC_EXPORT extern const char kVp8CodecName[];
RTC_EXPORT extern const char kVp9CodecName[];
RTC_EXPORT extern const char kAv1CodecName[];
RTC_EXPORT extern const char kH264CodecName[];
RTC_EXPORT extern const char kH265CodecName[];

// RFC 6184 RTP Payload Format for H.264 video
RTC_EXPORT extern const char kH264FmtpProfileLevelId[];
RTC_EXPORT extern const char kH264FmtpLevelAsymmetryAllowed[];
RTC_EXPORT extern const char kH264FmtpPacketizationMode[];
extern const char kH264FmtpSpropParameterSets[];
extern const char kH264FmtpSpsPpsIdrInKeyframe[];
extern const char kH264ProfileLevelConstrainedBaseline[];
extern const char kH264ProfileLevelConstrainedHigh[];

// RFC 7798 RTP Payload Format for H.265 video.
// According to RFC 7742, the sprop parameters MUST NOT be included
// in SDP generated by WebRTC, so for H.265 we don't handle them, though
// current H.264 implementation honors them when receiving
// sprop-parameter-sets in SDP.
RTC_EXPORT extern const char kH265FmtpProfileSpace[];
RTC_EXPORT extern const char kH265FmtpTierFlag[];
RTC_EXPORT extern const char kH265FmtpProfileId[];
RTC_EXPORT extern const char kH265FmtpLevelId[];
RTC_EXPORT extern const char kH265FmtpProfileCompatibilityIndicator[];
RTC_EXPORT extern const char kH265FmtpInteropConstraints[];
RTC_EXPORT extern const char kH265FmtpTxMode[];

// draft-ietf-payload-vp9
extern const char kVP9ProfileId[];

// https://aomediacodec.github.io/av1-rtp-spec/
extern const char kAv1FmtpProfile[];
extern const char kAv1FmtpLevelIdx[];
extern const char kAv1FmtpTier[];

extern const int kDefaultVideoMaxFramerate;
extern const int kDefaultVideoMaxQpVpx;
extern const int kDefaultVideoMaxQpH26x;

extern const size_t kConferenceMaxNumSpatialLayers;
extern const size_t kConferenceMaxNumTemporalLayers;
extern const size_t kConferenceDefaultNumTemporalLayers;

extern const char kApplicationSpecificBandwidth[];
extern const char kTransportSpecificBandwidth[];
}  // namespace cricket

#endif  // MEDIA_BASE_MEDIA_CONSTANTS_H_

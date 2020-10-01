/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/p2p_constants.h"

namespace cricket {

const char CN_AUDIO[] = "audio";
const char CN_VIDEO[] = "video";
const char CN_DATA[] = "data";
const char CN_OTHER[] = "main";

const char GROUP_TYPE_BUNDLE[] = "BUNDLE";

// Minimum ufrag length is 4 characters as per RFC5245.
const int ICE_UFRAG_LENGTH = 4;
// Minimum password length of 22 characters as per RFC5245. We chose 24 because
// some internal systems expect password to be multiple of 4.
const int ICE_PWD_LENGTH = 24;
const size_t ICE_UFRAG_MIN_LENGTH = 4;
const size_t ICE_PWD_MIN_LENGTH = 22;
const size_t ICE_UFRAG_MAX_LENGTH = 256;
const size_t ICE_PWD_MAX_LENGTH = 256;

// This is media-specific, so might belong
// somewhere like media/base/mediaconstants.h
const int ICE_CANDIDATE_COMPONENT_RTP = 1;
const int ICE_CANDIDATE_COMPONENT_RTCP = 2;
const int ICE_CANDIDATE_COMPONENT_DEFAULT = 1;

// From RFC 4145, SDP setup attribute values.
const char CONNECTIONROLE_ACTIVE_STR[] = "active";
const char CONNECTIONROLE_PASSIVE_STR[] = "passive";
const char CONNECTIONROLE_ACTPASS_STR[] = "actpass";
const char CONNECTIONROLE_HOLDCONN_STR[] = "holdconn";

const char LOCAL_TLD[] = ".local";

const int MIN_CHECK_RECEIVING_INTERVAL = 50;
const int RECEIVING_TIMEOUT = MIN_CHECK_RECEIVING_INTERVAL * 50;
const int RECEIVING_SWITCHING_DELAY = 1000;
const int BACKUP_CONNECTION_PING_INTERVAL = 25 * 1000;
const int REGATHER_ON_FAILED_NETWORKS_INTERVAL = 5 * 60 * 1000;

// When the socket is unwritable, we will use 10 Kbps (ignoring IP+UDP headers)
// for pinging. When the socket is writable, we will use only 1 Kbps because we
// don't want to degrade the quality on a modem.  These numbers should work well
// on a 28.8K modem, which is the slowest connection on which the voice quality
// is reasonable at all.
const int STUN_PING_PACKET_SIZE = 60 * 8;
const int STRONG_PING_INTERVAL = 1000 * STUN_PING_PACKET_SIZE / 1000;  // 480ms.
const int WEAK_PING_INTERVAL = 1000 * STUN_PING_PACKET_SIZE / 10000;   // 48ms.
const int WEAK_OR_STABILIZING_WRITABLE_CONNECTION_PING_INTERVAL = 900;
const int STRONG_AND_STABLE_WRITABLE_CONNECTION_PING_INTERVAL = 2500;
const int CONNECTION_WRITE_CONNECT_TIMEOUT = 5 * 1000;  // 5 seconds
const uint32_t CONNECTION_WRITE_CONNECT_FAILURES = 5;   // 5 pings

const int STUN_KEEPALIVE_INTERVAL = 10 * 1000;  // 10 seconds

const int MIN_CONNECTION_LIFETIME = 10 * 1000;          // 10 seconds.
const int DEAD_CONNECTION_RECEIVE_TIMEOUT = 30 * 1000;  // 30 seconds.
const int WEAK_CONNECTION_RECEIVE_TIMEOUT = 2500;       // 2.5 seconds
const int CONNECTION_WRITE_TIMEOUT = 15 * 1000;         // 15 seconds
// There is no harm to keep this value high other than a small amount
// of increased memory, but in some networks (2G), we observe up to 60s RTTs.
const int CONNECTION_RESPONSE_TIMEOUT = 60 * 1000;  // 60 seconds

}  // namespace cricket

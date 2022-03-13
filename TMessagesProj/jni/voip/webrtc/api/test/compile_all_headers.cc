/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file verifies that all include files in this directory can be
// compiled without errors or other required includes.

// Note: The following header files are not not tested here, as their
// associated targets are not included in all configurations.
// "api/test/audioproc_float.h"
// "api/test/create_video_quality_test_fixture.h"
// "api/test/neteq_simulator_factory.h"
// "api/test/video_quality_test_fixture.h"
// The following header files are also not tested:
// "api/test/create_simulcast_test_fixture.h"
// "api/test/create_videocodec_test_fixture.h"
// "api/test/neteq_simulator.h"
// "api/test/simulated_network.h"
// "api/test/simulcast_test_fixture.h"
// "api/test/test_dependency_factory.h"
// "api/test/videocodec_test_fixture.h"
// "api/test/videocodec_test_stats.h"

#include "api/test/dummy_peer_connection.h"
#include "api/test/fake_frame_decryptor.h"
#include "api/test/fake_frame_encryptor.h"
#include "api/test/mock_async_dns_resolver.h"
#include "api/test/mock_audio_mixer.h"
#include "api/test/mock_audio_sink.h"
#include "api/test/mock_data_channel.h"
#include "api/test/mock_frame_decryptor.h"
#include "api/test/mock_frame_encryptor.h"
#include "api/test/mock_media_stream_interface.h"
#include "api/test/mock_peer_connection_factory_interface.h"
#include "api/test/mock_peerconnectioninterface.h"
#include "api/test/mock_rtp_transceiver.h"
#include "api/test/mock_rtpreceiver.h"
#include "api/test/mock_rtpsender.h"
#include "api/test/mock_transformable_video_frame.h"
#include "api/test/mock_video_bitrate_allocator.h"
#include "api/test/mock_video_bitrate_allocator_factory.h"
#include "api/test/mock_video_decoder.h"
#include "api/test/mock_video_decoder_factory.h"
#include "api/test/mock_video_encoder.h"
#include "api/test/mock_video_encoder_factory.h"
#include "api/test/mock_video_track.h"

/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/common/handover_testing.h"

namespace dcsctp {
namespace {
// Default transformer function does nothing - dcSCTP does not implement
// state serialization that could be tested by setting
// `g_handover_state_transformer_for_test`.
void NoTransformation(DcSctpSocketHandoverState*) {}
}  // namespace

void (*g_handover_state_transformer_for_test)(DcSctpSocketHandoverState*) =
    NoTransformation;
}  // namespace dcsctp

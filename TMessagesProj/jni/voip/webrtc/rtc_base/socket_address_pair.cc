/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/socket_address_pair.h"

namespace rtc {

SocketAddressPair::SocketAddressPair(const SocketAddress& src,
                                     const SocketAddress& dest)
    : src_(src), dest_(dest) {}

bool SocketAddressPair::operator==(const SocketAddressPair& p) const {
  return (src_ == p.src_) && (dest_ == p.dest_);
}

bool SocketAddressPair::operator<(const SocketAddressPair& p) const {
  if (src_ < p.src_)
    return true;
  if (p.src_ < src_)
    return false;
  if (dest_ < p.dest_)
    return true;
  if (p.dest_ < dest_)
    return false;
  return false;
}

size_t SocketAddressPair::Hash() const {
  return src_.Hash() ^ dest_.Hash();
}

}  // namespace rtc

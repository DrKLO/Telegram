/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/portal/scoped_glib.h"

namespace webrtc {

template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<GError>;
template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<char>;
template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<GVariant>;
template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<GVariantIter>;
template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<GDBusMessage>;
template class RTC_EXPORT_TEMPLATE_DEFINE(RTC_EXPORT) Scoped<GUnixFDList>;

template <>
Scoped<GError>::~Scoped() {
  if (ptr_) {
    g_error_free(ptr_);
  }
}

template <>
Scoped<char>::~Scoped() {
  if (ptr_) {
    g_free(ptr_);
  }
}

template <>
Scoped<GVariant>::~Scoped() {
  if (ptr_) {
    g_variant_unref(ptr_);
  }
}

template <>
Scoped<GVariantIter>::~Scoped() {
  if (ptr_) {
    g_variant_iter_free(ptr_);
  }
}

template <>
Scoped<GDBusMessage>::~Scoped() {
  if (ptr_) {
    g_object_unref(ptr_);
  }
}

template <>
Scoped<GUnixFDList>::~Scoped() {
  if (ptr_) {
    g_object_unref(ptr_);
  }
}

}  // namespace webrtc

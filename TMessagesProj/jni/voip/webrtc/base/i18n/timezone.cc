// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/i18n/timezone.h"

#include <memory>
#include <string>

#include "third_party/icu/source/common/unicode/unistr.h"
#include "third_party/icu/source/i18n/unicode/timezone.h"

namespace base {

std::string CountryCodeForCurrentTimezone() {
  std::unique_ptr<icu::TimeZone> zone(icu::TimeZone::createDefault());
  icu::UnicodeString id;
  // ICU returns '001' (world) for Etc/GMT. Preserve the old behavior
  // only for Etc/GMT while returning an empty string for Etc/UTC and
  // Etc/UCT because they're less likely to be chosen by mistake in UK in
  // place of Europe/London (Briitish Time).
  if (zone->getID(id) == UNICODE_STRING_SIMPLE("Etc/GMT"))
    return "GB";
  char region_code[4];
  UErrorCode status = U_ZERO_ERROR;
  int length = zone->getRegion(id, region_code, 4, status);
  // Return an empty string if region_code is a 3-digit numeric code such
  // as 001 (World) for Etc/UTC, Etc/UCT.
  return (U_SUCCESS(status) && length == 2)
             ? std::string(region_code, static_cast<size_t>(length))
             : std::string();
}

}  // namespace base

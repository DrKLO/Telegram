// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/enterprise_util.h"

#include "base/win/win_util.h"

namespace base {

bool IsMachineExternallyManaged() {
  // TODO(rogerta): this function should really be:
  //
  //    return IsEnrolledToDomain() || IsDeviceRegisteredWithManagement();
  //
  // However, for now it is decided to collect some UMA metrics about
  // IsDeviceRegisteredWithMdm() before changing chrome's behavior.
  return base::win::IsEnrolledToDomain();
}

}  // namespace base

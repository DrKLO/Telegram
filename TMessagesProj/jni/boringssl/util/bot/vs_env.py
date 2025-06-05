# Copyright 2015 The BoringSSL Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import print_function

import subprocess
import sys

import vs_toolchain

if len(sys.argv) < 2:
  print("Usage: vs_env.py TARGET_ARCH CMD...", file=sys.stderr)
  sys.exit(1)

target_arch = sys.argv[1]
cmd = sys.argv[2:]

vs_toolchain.SetEnvironmentForCPU(target_arch)
sys.exit(subprocess.call(cmd))

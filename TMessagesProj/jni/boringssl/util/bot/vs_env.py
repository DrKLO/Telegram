# Copyright (c) 2015, Google Inc.
#
# Permission to use, copy, modify, and/or distribute this software for any
# purpose with or without fee is hereby granted, provided that the above
# copyright notice and this permission notice appear in all copies.
#
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
# WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
# SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
# WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
# OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
# CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

import subprocess
import sys

import vs_toolchain
# vs_toolchain adds gyp to sys.path.
import gyp.MSVSVersion

if len(sys.argv) < 2:
  print >>sys.stderr, "Usage: vs_env.py TARGET_ARCH CMD..."
  sys.exit(1)

target_arch = sys.argv[1]
cmd = sys.argv[2:]

vs_toolchain.SetEnvironmentAndGetRuntimeDllDirs()
vs_version = gyp.MSVSVersion.SelectVisualStudioVersion()

# Using shell=True is somewhat ugly, but the alternative is to pull in a copy
# of the Chromium GN build's setup_toolchain.py which runs the setup script,
# then 'set', and then parses the environment variables out. (GYP internally
# does the same thing.)
sys.exit(subprocess.call(vs_version.SetupScript(target_arch) + ["&&"] + cmd,
                         shell=True))

# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import json
import os
import os.path
import subprocess
import sys


script_dir = os.path.dirname(os.path.realpath(__file__))
toolchain_dir = os.path.join(script_dir, 'win_toolchain')
json_data_file = os.path.join(script_dir, 'win_toolchain.json')


def SetEnvironmentForCPU(cpu):
  """Sets the environment to build with the selected toolchain for |cpu|."""
  with open(json_data_file, 'r') as tempf:
    toolchain_data = json.load(tempf)
  sdk_dir = toolchain_data['win_sdk']
  os.environ['WINDOWSSDKDIR'] = sdk_dir
  os.environ['WDK_DIR'] = toolchain_data['wdk']
  # Include the VS runtime in the PATH in case it's not machine-installed.
  vs_runtime_dll_dirs = toolchain_data['runtime_dirs']
  runtime_path = os.pathsep.join(vs_runtime_dll_dirs)
  os.environ['PATH'] = runtime_path + os.pathsep + os.environ['PATH']

  # Set up the architecture-specific environment from the SetEnv files. See
  # _LoadToolchainEnv() from setup_toolchain.py in Chromium.
  assert cpu in ('x86', 'x64', 'arm', 'arm64')
  with open(os.path.join(sdk_dir, 'bin', 'SetEnv.%s.json' % cpu)) as f:
    env = json.load(f)['env']
  if env['VSINSTALLDIR'] == [["..", "..\\"]]:
    # Old-style paths were relative to the win_sdk\bin directory.
    json_relative_dir = os.path.join(sdk_dir, 'bin')
  else:
    # New-style paths are relative to the toolchain directory.
    json_relative_dir = toolchain_data['path']
  for k in env:
    entries = [os.path.join(*([json_relative_dir] + e)) for e in env[k]]
    # clang-cl wants INCLUDE to be ;-separated even on non-Windows,
    # lld-link wants LIB to be ;-separated even on non-Windows.  Path gets :.
    sep = os.pathsep if k == 'PATH' else ';'
    env[k] = sep.join(entries)
  # PATH is a bit of a special case, it's in addition to the current PATH.
  env['PATH'] = env['PATH'] + os.pathsep + os.environ['PATH']

  for k, v in env.items():
    os.environ[k] = v


def FindDepotTools():
  """Returns the path to depot_tools in $PATH."""
  for path in os.environ['PATH'].split(os.pathsep):
    if os.path.isfile(os.path.join(path, 'gclient.py')):
      return path
  raise Exception("depot_tools not found!")


def _GetDesiredVsToolchainHashes(version):
  """Load a list of SHA1s corresponding to the toolchains that we want installed
  to build with."""
  if version == '2022':
    # VS 2022 17.9.2 with 10.0.22621.2428 SDK with ARM64 libraries and UWP
    # support.
    return ['7393122652']
  raise Exception('Unsupported VS version %s' % version)


def Update(version):
  """Requests an update of the toolchain to the specific hashes we have at
  this revision. The update outputs a .json of the various configuration
  information required to pass to vs_env.py which we use in
  |SetEnvironmentForCPU()|.
  """
  depot_tools_path = FindDepotTools()
  get_toolchain_args = [
      sys.executable,
      os.path.join(depot_tools_path,
                  'win_toolchain',
                  'get_toolchain_if_necessary.py'),
      '--output-json', json_data_file,
      '--toolchain-dir', toolchain_dir,
    ] + _GetDesiredVsToolchainHashes(version)
  subprocess.check_call(get_toolchain_args)
  return 0


def main():
  if not sys.platform.startswith(('win32', 'cygwin')):
    return 0
  commands = {
      'update': Update,
  }
  if len(sys.argv) < 2 or sys.argv[1] not in commands:
    print('Expected one of: %s' % ', '.join(commands), file=sys.stderr)
    return 1
  return commands[sys.argv[1]](*sys.argv[2:])


if __name__ == '__main__':
  sys.exit(main())

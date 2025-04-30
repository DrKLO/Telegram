# coding=utf8

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

"""Enumerates source files for consumption by various build systems."""

import optparse
import os
import subprocess
import sys
import json


PREFIX = None


def PathOf(x):
  return x if not PREFIX else os.path.join(PREFIX, x)


TARGET_PREFIX = ''


LICENSE_TEMPLATE = """Copyright 2015 The BoringSSL Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.""".split("\n")

def LicenseHeader(comment):
  lines = []
  for line in LICENSE_TEMPLATE:
    if not line:
      lines.append(comment)
    else:
      lines.append("%s %s" % (comment, line))
  lines.append("")
  return "\n".join(lines)


class Android(object):

  def __init__(self):
    self.header = LicenseHeader("#") + """
# This file is created by generate_build_files.py. Do not edit manually.
"""

  def PrintVariableSection(self, out, name, files):
    name = f'{TARGET_PREFIX}{name}'
    out.write('%s := \\\n' % name)
    for f in sorted(files):
      out.write('  %s\\\n' % f)
    out.write('\n')

  def WriteFiles(self, files):
    # New Android.bp format
    with open('sources.bp', 'w+') as blueprint:
      blueprint.write(self.header.replace('#', '//'))

      #  Separate out BCM files to allow different compilation rules (specific to Android FIPS)
      bcm_c_files = files['bcm_crypto']
      non_bcm_c_files = [file for file in files['crypto'] if file not in bcm_c_files]
      non_bcm_asm = self.FilterBcmAsm(files['crypto_asm'], False)
      bcm_asm = self.FilterBcmAsm(files['crypto_asm'], True)

      self.PrintDefaults(blueprint, 'libcrypto_sources', non_bcm_c_files, asm_files=non_bcm_asm)
      self.PrintDefaults(blueprint, 'libcrypto_bcm_sources', bcm_c_files, asm_files=bcm_asm)
      self.PrintDefaults(blueprint, 'libssl_sources', files['ssl'])
      self.PrintDefaults(blueprint, 'bssl_sources', files['tool'])
      self.PrintDefaults(blueprint, 'boringssl_test_support_sources', files['test_support'])
      self.PrintDefaults(blueprint, 'boringssl_crypto_test_sources', files['crypto_test'], data=files['crypto_test_data'])
      self.PrintDefaults(blueprint, 'boringssl_ssl_test_sources', files['ssl_test'])
      self.PrintDefaults(blueprint, 'libpki_sources', files['pki'])

    # Legacy Android.mk format, only used by Trusty in new branches
    with open('sources.mk', 'w+') as makefile:
      makefile.write(self.header)
      makefile.write('\n')
      self.PrintVariableSection(makefile, 'crypto_sources', files['crypto'])
      self.PrintVariableSection(makefile, 'crypto_sources_asm',
                                files['crypto_asm'])

  def PrintDefaults(self, blueprint, name, files, asm_files=[], data=[]):
    """Print a cc_defaults section from a list of C files and optionally assembly outputs"""
    name = f'{TARGET_PREFIX}{name}'
    if asm_files:
      blueprint.write('\n')
      blueprint.write('%s_asm = [\n' % name)
      for f in sorted(asm_files):
        blueprint.write('    "%s",\n' % f)
      blueprint.write(']\n')

    blueprint.write('\n')
    blueprint.write('cc_defaults {\n')
    blueprint.write('    name: "%s",\n' % name)
    blueprint.write('    srcs: [\n')
    for f in sorted(files):
      blueprint.write('        "%s",\n' % f)
    blueprint.write('    ],\n')
    if data:
      blueprint.write('    data: [\n')
      for f in sorted(data):
        blueprint.write('        "%s",\n' % f)
      blueprint.write('    ],\n')

    if asm_files:
      blueprint.write('    target: {\n')
      # Only emit asm for Linux. On Windows, BoringSSL requires NASM, which is
      # not available in AOSP. On Darwin, the assembly works fine, but it
      # conflicts with Android's FIPS build. See b/294399371.
      blueprint.write('        linux: {\n')
      blueprint.write('            srcs: %s_asm,\n' % name)
      blueprint.write('        },\n')
      blueprint.write('        darwin: {\n')
      blueprint.write('            cflags: ["-DOPENSSL_NO_ASM"],\n')
      blueprint.write('        },\n')
      blueprint.write('        windows: {\n')
      blueprint.write('            cflags: ["-DOPENSSL_NO_ASM"],\n')
      blueprint.write('        },\n')
      blueprint.write('    },\n')

    blueprint.write('}\n')

  def FilterBcmAsm(self, asm, want_bcm):
    """Filter a list of assembly outputs based on whether they belong in BCM

    Args:
      asm: Assembly file list to filter
      want_bcm: If true then include BCM files, otherwise do not

    Returns:
      A copy of |asm| with files filtered according to |want_bcm|
    """
    # TODO(https://crbug.com/boringssl/542): Rather than filtering by filename,
    # use the variable listed in the CMake perlasm line, available in
    # ExtractPerlAsmFromCMakeFile.
    return filter(lambda p: ("/crypto/fipsmodule/" in p) == want_bcm, asm)


class AndroidCMake(object):

  def __init__(self):
    self.header = LicenseHeader("#") + """
# This file is created by generate_build_files.py. Do not edit manually.
# To specify a custom path prefix, set BORINGSSL_ROOT before including this
# file, or use list(TRANSFORM ... PREPEND) from CMake 3.12.

"""

  def PrintVariableSection(self, out, name, files):
    out.write('set(%s\n' % name)
    for f in sorted(files):
      # Ideally adding the prefix would be the caller's job, but
      # list(TRANSFORM ... PREPEND) is only available starting CMake 3.12. When
      # sources.cmake is the source of truth, we can ask Android to either write
      # a CMake function or update to 3.12.
      out.write('  ${BORINGSSL_ROOT}%s\n' % f)
    out.write(')\n')

  def WriteFiles(self, files):
    # The Android emulator uses a custom CMake buildsystem.
    #
    # TODO(crbug.com/boringssl/542): Move our various source lists into
    # sources.cmake and have Android consume that directly.
    with open('android-sources.cmake', 'w+') as out:
      out.write(self.header)

      self.PrintVariableSection(out, 'crypto_sources', files['crypto'])
      self.PrintVariableSection(out, 'crypto_sources_asm', files['crypto_asm'])
      self.PrintVariableSection(out, 'crypto_sources_nasm',
                                files['crypto_nasm'])
      self.PrintVariableSection(out, 'ssl_sources', files['ssl'])
      self.PrintVariableSection(out, 'tool_sources', files['tool'])
      self.PrintVariableSection(out, 'test_support_sources',
                                files['test_support'])
      self.PrintVariableSection(out, 'crypto_test_sources',
                                files['crypto_test'])
      self.PrintVariableSection(out, 'crypto_test_data',
                                files['crypto_test_data'])
      self.PrintVariableSection(out, 'ssl_test_sources', files['ssl_test'])


class Bazel(object):
  """Bazel outputs files suitable for including in Bazel files."""

  def __init__(self):
    self.firstSection = True
    self.header = \
"""# This file is created by generate_build_files.py. Do not edit manually.

"""

  def PrintVariableSection(self, out, name, files):
    if not self.firstSection:
      out.write('\n')
    self.firstSection = False

    out.write('%s = [\n' % name)
    for f in sorted(files):
      out.write('    "%s",\n' % PathOf(f))
    out.write(']\n')

  def WriteFiles(self, files):
    with open('BUILD.generated.bzl', 'w+') as out:
      out.write(self.header)

      self.PrintVariableSection(out, 'ssl_headers', files['ssl_headers'])
      self.PrintVariableSection(out, 'fips_fragments', files['fips_fragments'])
      self.PrintVariableSection(
          out, 'ssl_internal_headers', files['ssl_internal_headers'])
      self.PrintVariableSection(out, 'ssl_sources', files['ssl'])
      self.PrintVariableSection(out, 'crypto_headers', files['crypto_headers'])
      self.PrintVariableSection(
          out, 'crypto_internal_headers', files['crypto_internal_headers'])
      self.PrintVariableSection(out, 'crypto_sources', files['crypto'])
      self.PrintVariableSection(out, 'crypto_sources_asm', files['crypto_asm'])
      self.PrintVariableSection(out, 'crypto_sources_nasm', files['crypto_nasm'])
      self.PrintVariableSection(out, 'pki_headers', files['pki_headers'])
      self.PrintVariableSection(
          out, 'pki_internal_headers', files['pki_internal_headers'])
      self.PrintVariableSection(out, 'pki_sources', files['pki'])
      self.PrintVariableSection(out, 'rust_bssl_sys', files['rust_bssl_sys'])
      self.PrintVariableSection(out, 'rust_bssl_crypto', files['rust_bssl_crypto'])
      self.PrintVariableSection(out, 'tool_sources', files['tool'])
      self.PrintVariableSection(out, 'tool_headers', files['tool_headers'])

    with open('BUILD.generated_tests.bzl', 'w+') as out:
      out.write(self.header)

      out.write('test_support_sources = [\n')
      for filename in sorted(files['test_support'] +
                             files['test_support_headers'] +
                             files['crypto_internal_headers'] +
                             files['pki_internal_headers'] +
                             files['ssl_internal_headers']):
        out.write('    "%s",\n' % PathOf(filename))

      out.write(']\n')

      self.PrintVariableSection(out, 'crypto_test_sources',
                                files['crypto_test'])
      self.PrintVariableSection(out, 'ssl_test_sources', files['ssl_test'])
      self.PrintVariableSection(out, 'pki_test_sources',
                                files['pki_test'])
      self.PrintVariableSection(out, 'crypto_test_data',
                                files['crypto_test_data'])
      self.PrintVariableSection(out, 'pki_test_data',
                                files['pki_test_data'])
      self.PrintVariableSection(out, 'urandom_test_sources',
                                files['urandom_test'])


class Eureka(object):

  def __init__(self):
    self.header = LicenseHeader("#") + """
# This file is created by generate_build_files.py. Do not edit manually.

"""

  def PrintVariableSection(self, out, name, files):
    out.write('%s := \\\n' % name)
    for f in sorted(files):
      out.write('  %s\\\n' % f)
    out.write('\n')

  def WriteFiles(self, files):
    # Legacy Android.mk format
    with open('eureka.mk', 'w+') as makefile:
      makefile.write(self.header)

      self.PrintVariableSection(makefile, 'crypto_sources', files['crypto'])
      self.PrintVariableSection(makefile, 'crypto_sources_asm',
                                files['crypto_asm'])
      self.PrintVariableSection(makefile, 'crypto_sources_nasm',
                                files['crypto_nasm'])
      self.PrintVariableSection(makefile, 'ssl_sources', files['ssl'])
      self.PrintVariableSection(makefile, 'tool_sources', files['tool'])


class GN(object):

  def __init__(self):
    self.firstSection = True
    self.header = LicenseHeader("#") + """
# This file is created by generate_build_files.py. Do not edit manually.

"""

  def PrintVariableSection(self, out, name, files):
    if not self.firstSection:
      out.write('\n')
    self.firstSection = False

    if len(files) == 0:
      out.write('%s = []\n' % name)
    elif len(files) == 1:
      out.write('%s = [ "%s" ]\n' % (name, files[0]))
    else:
      out.write('%s = [\n' % name)
      for f in sorted(files):
        out.write('  "%s",\n' % f)
      out.write(']\n')

  def WriteFiles(self, files):
    with open('BUILD.generated.gni', 'w+') as out:
      out.write(self.header)

      self.PrintVariableSection(out, 'crypto_sources',
                                files['crypto'] +
                                files['crypto_internal_headers'])
      self.PrintVariableSection(out, 'crypto_sources_asm', files['crypto_asm'])
      self.PrintVariableSection(out, 'crypto_sources_nasm',
                                files['crypto_nasm'])
      self.PrintVariableSection(out, 'crypto_headers', files['crypto_headers'])
      self.PrintVariableSection(out, 'rust_bssl_sys', files['rust_bssl_sys'])
      self.PrintVariableSection(out, 'rust_bssl_crypto',
                                files['rust_bssl_crypto'])
      self.PrintVariableSection(out, 'ssl_sources',
                                files['ssl'] + files['ssl_internal_headers'])
      self.PrintVariableSection(out, 'ssl_headers', files['ssl_headers'])
      self.PrintVariableSection(out, 'pki_sources', files['pki'])
      self.PrintVariableSection(out, 'pki_internal_headers',
                                files['pki_internal_headers'])
      self.PrintVariableSection(out, 'pki_headers', files['pki_headers'])
      self.PrintVariableSection(out, 'tool_sources',
                                files['tool'] + files['tool_headers'])

      fuzzers = [os.path.splitext(os.path.basename(fuzzer))[0]
                 for fuzzer in files['fuzz']]
      self.PrintVariableSection(out, 'fuzzers', fuzzers)

    with open('BUILD.generated_tests.gni', 'w+') as out:
      self.firstSection = True
      out.write(self.header)

      self.PrintVariableSection(out, 'test_support_sources',
                                files['test_support'] +
                                files['test_support_headers'])
      self.PrintVariableSection(out, 'crypto_test_sources',
                                files['crypto_test'])
      self.PrintVariableSection(out, 'crypto_test_data',
                                files['crypto_test_data'])
      self.PrintVariableSection(out, 'pki_test_data',
                                files['pki_test_data'])
      self.PrintVariableSection(out, 'ssl_test_sources', files['ssl_test'])
      self.PrintVariableSection(out, 'pki_test_sources', files['pki_test'])


class GYP(object):

  def __init__(self):
    self.header = LicenseHeader("#") + """
# This file is created by generate_build_files.py. Do not edit manually.

"""

  def PrintVariableSection(self, out, name, files):
    out.write('    \'%s\': [\n' % name)
    for f in sorted(files):
      out.write('      \'%s\',\n' % f)
    out.write('    ],\n')

  def WriteFiles(self, files):
    with open('boringssl.gypi', 'w+') as gypi:
      gypi.write(self.header + '{\n  \'variables\': {\n')

      self.PrintVariableSection(gypi, 'boringssl_ssl_sources',
                                files['ssl'] + files['ssl_headers'] +
                                files['ssl_internal_headers'])
      self.PrintVariableSection(gypi, 'boringssl_crypto_sources',
                                files['crypto'] + files['crypto_headers'] +
                                files['crypto_internal_headers'])
      self.PrintVariableSection(gypi, 'boringssl_crypto_asm_sources',
                                files['crypto_asm'])
      self.PrintVariableSection(gypi, 'boringssl_crypto_nasm_sources',
                                files['crypto_nasm'])

      gypi.write('  }\n}\n')

class CMake(object):

  def __init__(self):
    self.header = LicenseHeader("#") + R'''
# This file is created by generate_build_files.py. Do not edit manually.

cmake_minimum_required(VERSION 3.16)

project(BoringSSL LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
if(CMAKE_COMPILER_IS_GNUCXX OR CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fno-common -fno-exceptions -fno-rtti")
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fvisibility=hidden -fno-common")
endif()

# pthread_rwlock_t requires a feature flag on glibc.
if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_XOPEN_SOURCE=700")
endif()

if(WIN32)
  add_definitions(-D_HAS_EXCEPTIONS=0)
  add_definitions(-DWIN32_LEAN_AND_MEAN)
  add_definitions(-DNOMINMAX)
  # Allow use of fopen.
  add_definitions(-D_CRT_SECURE_NO_WARNINGS)
endif()

add_definitions(-DBORINGSSL_IMPLEMENTATION)

if(OPENSSL_NO_ASM)
  add_definitions(-DOPENSSL_NO_ASM)
else()
  # On x86 and x86_64 Windows, we use the NASM output.
  if(WIN32 AND CMAKE_SYSTEM_PROCESSOR MATCHES "AMD64|x86_64|amd64|x86|i[3-6]86")
    enable_language(ASM_NASM)
    set(OPENSSL_NASM TRUE)
    set(CMAKE_ASM_NASM_FLAGS "${CMAKE_ASM_NASM_FLAGS} -gcv8")
  else()
    enable_language(ASM)
    set(OPENSSL_ASM TRUE)
    # Work around https://gitlab.kitware.com/cmake/cmake/-/issues/20771 in older
    # CMake versions.
    if(APPLE AND CMAKE_VERSION VERSION_LESS 3.19)
      if(CMAKE_OSX_SYSROOT)
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -isysroot \"${CMAKE_OSX_SYSROOT}\"")
      endif()
      foreach(arch ${CMAKE_OSX_ARCHITECTURES})
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -arch ${arch}")
      endforeach()
    endif()
    if(NOT WIN32)
      set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,--noexecstack")
    endif()
    # Clang's integerated assembler does not support debug symbols.
    if(NOT CMAKE_ASM_COMPILER_ID MATCHES "Clang")
      set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,-g")
    endif()
  endif()
endif()

if(BUILD_SHARED_LIBS)
  add_definitions(-DBORINGSSL_SHARED_LIBRARY)
  # Enable position-independent code globally. This is needed because
  # some library targets are OBJECT libraries.
  set(CMAKE_POSITION_INDEPENDENT_CODE TRUE)
endif()

'''

  def PrintLibrary(self, out, name, files, libs=[]):
    out.write('add_library(\n')
    out.write('  %s\n\n' % name)

    for f in sorted(files):
      out.write('  %s\n' % PathOf(f))

    out.write(')\n\n')
    if libs:
      out.write('target_link_libraries(%s %s)\n\n' % (name, ' '.join(libs)))

  def PrintExe(self, out, name, files, libs):
    out.write('add_executable(\n')
    out.write('  %s\n\n' % name)

    for f in sorted(files):
      out.write('  %s\n' % PathOf(f))

    out.write(')\n\n')
    out.write('target_link_libraries(%s %s)\n\n' % (name, ' '.join(libs)))

  def PrintVariable(self, out, name, files):
    out.write('set(\n')
    out.write('  %s\n\n' % name)
    for f in sorted(files):
      out.write('  %s\n' % PathOf(f))
    out.write(')\n\n')

  def WriteFiles(self, files):
    with open('CMakeLists.txt', 'w+') as cmake:
      cmake.write(self.header)

      self.PrintVariable(cmake, 'CRYPTO_SOURCES_ASM', files['crypto_asm'])
      self.PrintVariable(cmake, 'CRYPTO_SOURCES_NASM', files['crypto_nasm'])

      cmake.write(
R'''if(OPENSSL_ASM)
  list(APPEND CRYPTO_SOURCES_ASM_USED ${CRYPTO_SOURCES_ASM})
endif()
if(OPENSSL_NASM)
  list(APPEND CRYPTO_SOURCES_ASM_USED ${CRYPTO_SOURCES_NASM})
endif()

''')

      self.PrintLibrary(cmake, 'crypto',
          files['crypto'] + ['${CRYPTO_SOURCES_ASM_USED}'])
      cmake.write('target_include_directories(crypto PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/src/include>)\n\n')
      self.PrintLibrary(cmake, 'ssl', files['ssl'], ['crypto'])
      self.PrintExe(cmake, 'bssl', files['tool'], ['ssl', 'crypto'])

      cmake.write(
R'''if(NOT CMAKE_SYSTEM_NAME STREQUAL "Android")
  find_package(Threads REQUIRED)
  target_link_libraries(crypto Threads::Threads)
endif()

if(WIN32)
  target_link_libraries(crypto ws2_32)
endif()

''')

class JSON(object):
  def WriteFiles(self, files):
    with open('sources.json', 'w+') as f:
      json.dump(files, f, sort_keys=True, indent=2)

def NoTestsNorFIPSFragments(path, dent, is_dir):
  return (NoTests(path, dent, is_dir) and
      (is_dir or not OnlyFIPSFragments(path, dent, is_dir)))

def NoTests(path, dent, is_dir):
  """Filter function that can be passed to FindCFiles in order to remove test
  sources."""
  if is_dir:
    return dent != 'test'
  return 'test.' not in dent


def AllFiles(path, dent, is_dir):
  """Filter function that can be passed to FindCFiles in order to include all
  sources."""
  return True


def NoTestRunnerFiles(path, dent, is_dir):
  """Filter function that can be passed to FindCFiles or FindHeaderFiles in
  order to exclude test runner files."""
  # NOTE(martinkr): This prevents .h/.cc files in src/ssl/test/runner, which
  # are in their own subpackage, from being included in boringssl/BUILD files.
  return not is_dir or dent != 'runner'


def FindCFiles(directory, filter_func):
  """Recurses through directory and returns a list of paths to all the C source
  files that pass filter_func."""
  cfiles = []

  for (path, dirnames, filenames) in os.walk(directory):
    for filename in filenames:
      if not filename.endswith('.c') and not filename.endswith('.cc'):
        continue
      if not filter_func(path, filename, False):
        continue
      cfiles.append(os.path.join(path, filename))

    for (i, dirname) in enumerate(dirnames):
      if not filter_func(path, dirname, True):
        del dirnames[i]

  cfiles.sort()
  return cfiles


def FindHeaderFiles(directory, filter_func):
  """Recurses through directory and returns a list of paths to all the header files that pass filter_func."""
  hfiles = []

  for (path, dirnames, filenames) in os.walk(directory):
    for filename in filenames:
      if not filename.endswith('.h'):
        continue
      if not filter_func(path, filename, False):
        continue
      hfiles.append(os.path.join(path, filename))

      for (i, dirname) in enumerate(dirnames):
        if not filter_func(path, dirname, True):
          del dirnames[i]

  hfiles.sort()
  return hfiles


def PrefixWithSrc(files):
  return ['src/' + x for x in files]


def main(platforms):
  with open(os.path.join('src', 'gen', 'sources.json')) as f:
    sources = json.load(f)

  # TODO(crbug.com/boringssl/542): generate_build_files.py historically reported
  # all the assembly files as part of libcrypto. Merge them for now, but we
  # should split them out later.
  crypto = sorted(sources['bcm']['srcs'] + sources['crypto']['srcs'])
  crypto_asm = sorted(sources['bcm']['asm'] + sources['crypto']['asm'] +
                      sources['test_support']['asm'])
  crypto_nasm = sorted(sources['bcm']['nasm'] + sources['crypto']['nasm'] +
                       sources['test_support']['nasm'])

  files = {
      'bcm_crypto': PrefixWithSrc(sources['bcm']['srcs']),
      'crypto': PrefixWithSrc(crypto),
      'crypto_asm': PrefixWithSrc(crypto_asm),
      'crypto_nasm': PrefixWithSrc(crypto_nasm),
      'crypto_headers': PrefixWithSrc(sources['crypto']['hdrs']),
      'crypto_internal_headers':
          PrefixWithSrc(sources['crypto']['internal_hdrs']),
      'crypto_test': PrefixWithSrc(sources['crypto_test']['srcs']),
      'crypto_test_data': PrefixWithSrc(sources['crypto_test']['data']),
      'fips_fragments': PrefixWithSrc(sources['bcm']['internal_hdrs']),
      'fuzz': PrefixWithSrc(sources['fuzz']['srcs']),
      'pki': PrefixWithSrc(sources['pki']['srcs']),
      'pki_headers': PrefixWithSrc(sources['pki']['hdrs']),
      'pki_internal_headers': PrefixWithSrc(sources['pki']['internal_hdrs']),
      'pki_test': PrefixWithSrc(sources['pki_test']['srcs']),
      'pki_test_data': PrefixWithSrc(sources['pki_test']['data']),
      'rust_bssl_crypto': PrefixWithSrc(sources['rust_bssl_crypto']['srcs']),
      'rust_bssl_sys': PrefixWithSrc(sources['rust_bssl_sys']['srcs']),
      'ssl': PrefixWithSrc(sources['ssl']['srcs']),
      'ssl_headers': PrefixWithSrc(sources['ssl']['hdrs']),
      'ssl_internal_headers': PrefixWithSrc(sources['ssl']['internal_hdrs']),
      'ssl_test': PrefixWithSrc(sources['ssl_test']['srcs']),
      'tool': PrefixWithSrc(sources['bssl']['srcs']),
      'tool_headers': PrefixWithSrc(sources['bssl']['internal_hdrs']),
      'test_support': PrefixWithSrc(sources['test_support']['srcs']),
      'test_support_headers':
          PrefixWithSrc(sources['test_support']['internal_hdrs']),
      'urandom_test': PrefixWithSrc(sources['urandom_test']['srcs']),
  }

  for platform in platforms:
    platform.WriteFiles(files)

  return 0

ALL_PLATFORMS = {
    'android': Android,
    'android-cmake': AndroidCMake,
    'bazel': Bazel,
    'cmake': CMake,
    'eureka': Eureka,
    'gn': GN,
    'gyp': GYP,
    'json': JSON,
}

if __name__ == '__main__':
  parser = optparse.OptionParser(
      usage='Usage: %%prog [--prefix=<path>] [--target-prefix=<prefix>] [all|%s]' %
      '|'.join(sorted(ALL_PLATFORMS.keys())))
  parser.add_option('--prefix', dest='prefix',
      help='For Bazel, prepend argument to all source files')
  parser.add_option('--target-prefix', dest='target_prefix',
      help='For Android, prepend argument to all target names')
  options, args = parser.parse_args(sys.argv[1:])
  PREFIX = options.prefix
  TARGET_PREFIX = options.target_prefix

  if not args:
    parser.print_help()
    sys.exit(1)

  if 'all' in args:
    platforms = [platform() for platform in ALL_PLATFORMS.values()]
  else:
    platforms = []
    for s in args:
      platform = ALL_PLATFORMS.get(s)
      if platform is None:
        parser.print_help()
        sys.exit(1)
      platforms.append(platform())

  sys.exit(main(platforms))

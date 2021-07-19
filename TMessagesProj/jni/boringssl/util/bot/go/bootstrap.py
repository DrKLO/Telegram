#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Modified from go/bootstrap.py in Chromium infrastructure's repository to patch
# out everything but the core toolchain.
#
# https://chromium.googlesource.com/infra/infra/

"""Prepares a local hermetic Go installation.

- Downloads and unpacks the Go toolset in ../golang.
"""

import contextlib
import logging
import os
import platform
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib
import zipfile

# TODO(vadimsh): Migrate to new golang.org/x/ paths once Golang moves to
# git completely.

LOGGER = logging.getLogger(__name__)


# /path/to/util/bot
ROOT = os.path.dirname(os.path.abspath(__file__))

# Where to install Go toolset to. GOROOT would be <TOOLSET_ROOT>/go.
TOOLSET_ROOT = os.path.join(os.path.dirname(ROOT), 'golang')

# Default workspace with infra go code.
WORKSPACE = os.path.join(ROOT, 'go')

# Platform depended suffix for executable files.
EXE_SFX = '.exe' if sys.platform == 'win32' else ''

# Pinned version of Go toolset to download.
TOOLSET_VERSION = 'go1.13'

# Platform dependent portion of a download URL. See http://golang.org/dl/.
TOOLSET_VARIANTS = {
  ('darwin', 'x86-64'): 'darwin-amd64.tar.gz',
  ('linux2', 'x86-32'): 'linux-386.tar.gz',
  ('linux2', 'x86-64'): 'linux-amd64.tar.gz',
  ('win32', 'x86-32'): 'windows-386.zip',
  ('win32', 'x86-64'): 'windows-amd64.zip',
}

# Download URL root.
DOWNLOAD_URL_PREFIX = 'https://storage.googleapis.com/golang'


class Failure(Exception):
  """Bootstrap failed."""


def get_toolset_url():
  """URL of a platform specific Go toolset archive."""
  # TODO(vadimsh): Support toolset for cross-compilation.
  arch = {
    'amd64': 'x86-64',
    'x86_64': 'x86-64',
    'i386': 'x86-32',
    'x86': 'x86-32',
  }.get(platform.machine().lower())
  variant = TOOLSET_VARIANTS.get((sys.platform, arch))
  if not variant:
    # TODO(vadimsh): Compile go lang from source.
    raise Failure('Unrecognized platform')
  return '%s/%s.%s' % (DOWNLOAD_URL_PREFIX, TOOLSET_VERSION, variant)


def read_file(path):
  """Returns contents of a given file or None if not readable."""
  assert isinstance(path, (list, tuple))
  try:
    with open(os.path.join(*path), 'r') as f:
      return f.read()
  except IOError:
    return None


def write_file(path, data):
  """Writes |data| to a file."""
  assert isinstance(path, (list, tuple))
  with open(os.path.join(*path), 'w') as f:
    f.write(data)


def remove_directory(path):
  """Recursively removes a directory."""
  assert isinstance(path, (list, tuple))
  p = os.path.join(*path)
  if not os.path.exists(p):
    return
  LOGGER.info('Removing %s', p)
  # Crutch to remove read-only file (.git/* in particular) on Windows.
  def onerror(func, path, _exc_info):
    if not os.access(path, os.W_OK):
      os.chmod(path, stat.S_IWUSR)
      func(path)
    else:
      raise
  shutil.rmtree(p, onerror=onerror if sys.platform == 'win32' else None)


def install_toolset(toolset_root, url):
  """Downloads and installs Go toolset.

  GOROOT would be <toolset_root>/go/.
  """
  if not os.path.exists(toolset_root):
    os.makedirs(toolset_root)
  pkg_path = os.path.join(toolset_root, url[url.rfind('/')+1:])

  LOGGER.info('Downloading %s...', url)
  download_file(url, pkg_path)

  LOGGER.info('Extracting...')
  if pkg_path.endswith('.zip'):
    with zipfile.ZipFile(pkg_path, 'r') as f:
      f.extractall(toolset_root)
  elif pkg_path.endswith('.tar.gz'):
    with tarfile.open(pkg_path, 'r:gz') as f:
      f.extractall(toolset_root)
  else:
    raise Failure('Unrecognized archive format')

  LOGGER.info('Validating...')
  if not check_hello_world(toolset_root):
    raise Failure('Something is not right, test program doesn\'t work')


def download_file(url, path):
  """Fetches |url| to |path|."""
  last_progress = [0]
  def report(a, b, c):
    progress = int(a * b * 100.0 / c)
    if progress != last_progress[0]:
      print >> sys.stderr, 'Downloading... %d%%' % progress
      last_progress[0] = progress
  # TODO(vadimsh): Use something less crippled, something that validates SSL.
  urllib.urlretrieve(url, path, reporthook=report)


@contextlib.contextmanager
def temp_dir(path):
  """Creates a temporary directory, then deletes it."""
  tmp = tempfile.mkdtemp(dir=path)
  try:
    yield tmp
  finally:
    remove_directory([tmp])


def check_hello_world(toolset_root):
  """Compiles and runs 'hello world' program to verify that toolset works."""
  with temp_dir(toolset_root) as tmp:
    path = os.path.join(tmp, 'hello.go')
    write_file([path], r"""
        package main
        func main() { println("hello, world\n") }
    """)
    out = subprocess.check_output(
        [get_go_exe(toolset_root), 'run', path],
        env=get_go_environ(toolset_root, tmp),
        stderr=subprocess.STDOUT)
    if out.strip() != 'hello, world':
      LOGGER.error('Failed to run sample program:\n%s', out)
      return False
    return True


def ensure_toolset_installed(toolset_root):
  """Installs or updates Go toolset if necessary.

  Returns True if new toolset was installed.
  """
  installed = read_file([toolset_root, 'INSTALLED_TOOLSET'])
  available = get_toolset_url()
  if installed == available:
    LOGGER.debug('Go toolset is up-to-date: %s', TOOLSET_VERSION)
    return False

  LOGGER.info('Installing Go toolset.')
  LOGGER.info('  Old toolset is %s', installed)
  LOGGER.info('  New toolset is %s', available)
  remove_directory([toolset_root])
  install_toolset(toolset_root, available)
  LOGGER.info('Go toolset installed: %s', TOOLSET_VERSION)
  write_file([toolset_root, 'INSTALLED_TOOLSET'], available)
  return True


def get_go_environ(
    toolset_root,
    workspace=None):
  """Returns a copy of os.environ with added GO* environment variables.

  Overrides GOROOT, GOPATH and GOBIN. Keeps everything else. Idempotent.

  Args:
    toolset_root: GOROOT would be <toolset_root>/go.
    workspace: main workspace directory or None if compiling in GOROOT.
  """
  env = os.environ.copy()
  env['GOROOT'] = os.path.join(toolset_root, 'go')
  if workspace:
    env['GOBIN'] = os.path.join(workspace, 'bin')
  else:
    env.pop('GOBIN', None)

  all_go_paths = []
  if workspace:
    all_go_paths.append(workspace)
  env['GOPATH'] = os.pathsep.join(all_go_paths)

  # New PATH entries.
  paths_to_add = [
    os.path.join(env['GOROOT'], 'bin'),
    env.get('GOBIN'),
  ]

  # Make sure not to add duplicates entries to PATH over and over again when
  # get_go_environ is invoked multiple times.
  path = env['PATH'].split(os.pathsep)
  paths_to_add = [p for p in paths_to_add if p and p not in path]
  env['PATH'] = os.pathsep.join(paths_to_add + path)

  return env


def get_go_exe(toolset_root):
  """Returns path to go executable."""
  return os.path.join(toolset_root, 'go', 'bin', 'go' + EXE_SFX)


def bootstrap(logging_level):
  """Installs all dependencies in default locations.

  Supposed to be called at the beginning of some script (it modifies logger).

  Args:
    logging_level: logging level of bootstrap process.
  """
  logging.basicConfig()
  LOGGER.setLevel(logging_level)
  ensure_toolset_installed(TOOLSET_ROOT)


def prepare_go_environ():
  """Returns dict with environment variables to set to use Go toolset.

  Installs or updates the toolset if necessary.
  """
  bootstrap(logging.INFO)
  return get_go_environ(TOOLSET_ROOT, WORKSPACE)


def find_executable(name, workspaces):
  """Returns full path to an executable in some bin/ (in GOROOT or GOBIN)."""
  basename = name
  if EXE_SFX and basename.endswith(EXE_SFX):
    basename = basename[:-len(EXE_SFX)]
  roots = [os.path.join(TOOLSET_ROOT, 'go', 'bin')]
  for path in workspaces:
    roots.extend([
      os.path.join(path, 'bin'),
    ])
  for root in roots:
    full_path = os.path.join(root, basename + EXE_SFX)
    if os.path.exists(full_path):
      return full_path
  return name


def main(args):
  if args:
    print >> sys.stderr, sys.modules[__name__].__doc__,
    return 2
  bootstrap(logging.DEBUG)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))

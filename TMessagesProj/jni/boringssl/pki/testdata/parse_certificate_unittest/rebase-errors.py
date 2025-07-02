#!/usr/bin/env python3
# Copyright 2016 The Chromium Authors
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

"""Helper script to update the test error expectations based on actual results.

This is useful for regenerating test expectations after making changes to the
error format.

To use this run the affected tests, and then pass the input to this script
(either via stdin, or as the first argument). For instance:

  $ ./build/pki_test --gtest_filter="*ParseCertificate*:*ParsedCertificate*:" | \
     pki/testdata/parse_certificate_unittest/rebase-errors.py

The script works by scanning the stdout looking for gtest failures having a
particular format. The C++ test side should have been instrumented to dump
out the test file's path on mismatch.

This script will then update the corresponding .pem file
"""

import base64
import os
import re
import sys

# Regular expression to find the failed errors in test stdout.
#  * Group 1 of the match is file path (relative to //src) where the
#    expected errors were read from.
#  * Group 2 of the match is the actual error text
failed_test_regex = re.compile(r"""
Cert errors don't match expectations \((.+?)\)

EXPECTED:

(?:.|\n)*?
ACTUAL:

((?:.|\n)*?)
===> Use pki/testdata/parse_certificate_unittest/rebase-errors.py to rebaseline.
""", re.MULTILINE)


# Regular expression to find the ERRORS block (and any text above it) in a PEM
# file. The assumption is that ERRORS is not the very first block in the file
# (since it looks for an -----END to precede it).
#  * Group 1 of the match is the ERRORS block content and any comments
#    immediately above it.
errors_block_regex = re.compile(r""".*
-----END .*?-----
(.*?
-----BEGIN ERRORS-----
.*?
-----END ERRORS-----)""", re.MULTILINE | re.DOTALL)


def read_file_to_string(path):
  """Reads a file entirely to a string"""
  with open(path, 'r') as f:
    return f.read()


def write_string_to_file(data, path):
  """Writes a string to a file"""
  print("Writing file %s ..." % (path))
  with open(path, "w") as f:
    f.write(data)


def replace_string(original, start, end, replacement):
  """Replaces the specified range of |original| with |replacement|"""
  return original[0:start] + replacement + original[end:]


def text_data_to_pem(block_header, text_data):
  # b64encode takes in bytes and returns bytes.
  pem_data = base64.b64encode(text_data.encode('utf8')).decode('utf8')
  return '%s\n-----BEGIN %s-----\n%s\n-----END %s-----\n' % (
      text_data, block_header, pem_data, block_header)


def fixup_pem_file(path, actual_errors):
  """Updates the ERRORS block in the test .pem file"""
  contents = read_file_to_string(path)

  errors_block_text = '\n' + text_data_to_pem('ERRORS', actual_errors)
  # Strip the trailing newline.
  errors_block_text = errors_block_text[:-1]

  m = errors_block_regex.search(contents)

  if not m:
    contents += errors_block_text
  else:
    contents = replace_string(contents, m.start(1), m.end(1),
                              errors_block_text)

  # Update the file.
  write_string_to_file(contents, path)


def get_src_root():
  """Returns the path to BoringSSL source tree. This assumes the
  current script is inside the source tree."""
  cur_dir = os.path.dirname(os.path.realpath(__file__))

  while True:
    # Check if it looks like the BoringSSL root.
    if os.path.isdir(os.path.join(cur_dir, "crypto")) and \
       os.path.isdir(os.path.join(cur_dir, "pki")) and \
       os.path.isdir(os.path.join(cur_dir, "ssl")):
      return cur_dir
    parent_dir, _ = os.path.split(cur_dir)
    if not parent_dir or parent_dir == cur_dir:
      break
    cur_dir = parent_dir

  print("Couldn't find src dir")
  sys.exit(1)


def get_abs_path(rel_path):
  """Converts |rel_path| (relative to src) to a full path"""
  return os.path.join(get_src_root(), rel_path)


def main():
  if len(sys.argv) > 2:
    print('Usage: %s [path-to-unittest-stdout]' % (sys.argv[0]))
    sys.exit(1)

  # Read the input either from a file, or from stdin.
  test_stdout = None
  if len(sys.argv) == 2:
    test_stdout = read_file_to_string(sys.argv[1])
  else:
    print('Reading input from stdin...')
    test_stdout = sys.stdin.read()

  for m in failed_test_regex.finditer(test_stdout):
    src_relative_errors_path = "pki/" + m.group(1)
    errors_path = get_abs_path(src_relative_errors_path)
    actual_errors = m.group(2)

    fixup_pem_file(errors_path, actual_errors)


if __name__ == "__main__":
  main()

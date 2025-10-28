#!/usr/bin/env python
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

"""
Given a path to a XXX.pem file, re-generates a CERTIFICATE.

The .pem file is expected to contain comments that resemble:

#-----BEGIN XXX-----
<ascii-der values in here>
#-----END XXX-----

These are interpreted as substitutions to make inside of the Certificate
template (v3_certificate_template.txt)
"""

import sys
import os
import re
import base64
import subprocess


def read_file_to_string(path):
  """Reads a file entirely to a string"""
  with open(path, 'r') as f:
    return f.read()


def write_string_to_file(data, path):
  """Writes a string to a file"""
  print "Writing file %s ..." % (path)
  with open(path, "w") as f:
    f.write(data)


def replace_string(original, start, end, replacement):
  """Replaces the specified range of |original| with |replacement|"""
  return original[0:start] + replacement + original[end:]


def apply_substitution(template, name, value):
  """Finds a section named |name| in |template| and replaces it with |value|."""
  # Find the section |name| in |template|.
  regex = re.compile(r'#-----BEGIN %s-----(.*?)#-----END %s-----' %
                    (re.escape(name), re.escape(name)), re.DOTALL)
  m = regex.search(template)
  if not m:
    print "Couldn't find a section named %s in the template" % (name)
    sys.exit(1)

  return replace_string(template, m.start(1), m.end(1), value)


def main():
  if len(sys.argv) != 2:
    print 'Usage: %s <PATH_TO_PEM>' % (sys.argv[0])
    sys.exit(1)

  pem_path = sys.argv[1]
  orig = read_file_to_string(pem_path)

  cert_ascii = read_file_to_string("v3_certificate_template.txt")

  # Apply all substitutions described by comments in |orig|
  regex = re.compile(r'#-----BEGIN ([\w ]+)-----(.*?)#-----END \1-----',
                     re.DOTALL)
  num_matches = 0
  for m in regex.finditer(orig):
    num_matches += 1
    cert_ascii = apply_substitution(cert_ascii, m.group(1), m.group(2))

  if num_matches == 0:
    print "Input did not contain any substitutions"
    sys.exit(1)

  # Convert the ascii-der to actual DER binary.
  cert_der = None
  try:
    p = subprocess.Popen(['ascii2der'], stdout=subprocess.PIPE,
                         stdin=subprocess.PIPE, stderr=subprocess.STDOUT)
    cert_der = p.communicate(input=cert_ascii)[0]
  except OSError as e:
    print ('ERROR: Failed executing ascii2der.\n'
           'Make sure this is in your path\n'
           'Obtain it from https://github.com/google/der-ascii')
    sys.exit(1)

  # Replace the CERTIFICATE block with the newly generated one.
  regex = re.compile(r'-----BEGIN CERTIFICATE-----\n(.*?)\n'
                     '-----END CERTIFICATE-----', re.DOTALL)
  m = regex.search(orig)
  if not m:
    print "ERROR: Cannot find CERTIFICATE block in input"
    sys.exit(1)
  modified = replace_string(orig, m.start(1), m.end(1),
                            base64.b64encode(cert_der))

  # Write back the .pem file.
  write_string_to_file(modified, pem_path)

main()

#!/usr/bin/env python
# Copyright 2018 The Chromium Authors
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

import sys
sys.path += ['../..']

import gencerts

def add_excluded_name_constraints(cert, num_dns, num_ip, num_dirnames, num_uri):
  cert.get_extensions().set_property('nameConstraints', '@nameConstraints_info')
  constraints = cert.config.get_section('nameConstraints_info')
  for i in range(num_dns):
    constraints.set_property('excluded;DNS.%i' % (i + 1), 'x%i.test' % i)
  for i in range(num_ip):
    b,c = divmod(i, 256)
    a,b = divmod(b, 256)
    constraints.set_property('excluded;IP.%i' % (i + 1),
                             '11.%i.%i.%i/255.255.255.255' % (a, b, c))
  for i in range(num_dirnames):
    section_name = 'nameConstraints_dirname_x%i' % (i + 1)
    dirname = cert.config.get_section(section_name)
    dirname.set_property('commonName', '"x%i' % i)
    constraints.set_property('excluded;dirName.%i' % (i + 1), section_name)
  for i in range(num_uri):
    constraints.set_property('excluded;URI.%i' % (i + 1), 'http://xest/%i' % i)


def add_permitted_name_constraints(
    cert, num_dns, num_ip, num_dirnames, num_uri):
  cert.get_extensions().set_property('nameConstraints', '@nameConstraints_info')
  constraints = cert.config.get_section('nameConstraints_info')
  for i in range(num_dns):
    constraints.set_property('permitted;DNS.%i' % (i + 1), 't%i.test' % i)
  for i in range(num_ip):
    b,c = divmod(i, 256)
    a,b = divmod(b, 256)
    constraints.set_property('permitted;IP.%i' % (i + 1),
                             '10.%i.%i.%i/255.255.255.255' % (a, b, c))
  for i in range(num_dirnames):
    section_name = 'nameConstraints_dirname_p%i' % (i + 1)
    dirname = cert.config.get_section(section_name)
    dirname.set_property('commonName', '"t%i' % i)
    constraints.set_property('permitted;dirName.%i' % (i + 1), section_name)
  for i in range(num_uri):
    constraints.set_property('permitted;URI.%i' % (i + 1),
                               'http://test/%i' % i)


def add_sans(cert, num_dns, num_ip, num_dirnames, num_uri):
  cert.get_extensions().set_property('subjectAltName', '@san_info')
  sans = cert.config.get_section('san_info')
  for i in range(num_dns):
    sans.set_property('DNS.%i' % (i + 1), 't%i.test' % i)
  for i in range(num_ip):
    b,c = divmod(i, 256)
    a,b = divmod(b, 256)
    sans.set_property('IP.%i' % (i + 1), '10.%i.%i.%i' % (a, b, c))
  for i in range(num_dirnames):
    section_name = 'san_dirname%i' % (i + 1)
    dirname = cert.config.get_section(section_name)
    dirname.set_property('commonName', '"t%i' % i)
    sans.set_property('dirName.%i' % (i + 1), section_name)
  for i in range(num_uri):
    sans.set_property('URI.%i' % (i + 1), 'http://test/%i' % i)


# Self-signed root certificate.
root = gencerts.create_self_signed_root_certificate('Root')

# Use the same keys for all the chains. Fewer key files to check in, and also
# gives stability against re-ordering of the calls to |make_chain|.
intermediate_key = gencerts.get_or_generate_rsa_key(
    2048, gencerts.create_key_path('Intermediate'))
target_key = gencerts.get_or_generate_rsa_key(
    2048, gencerts.create_key_path('t0'))

def make_chain(name, doc, excluded, permitted, sans):
  # Intermediate certificate.
  intermediate = gencerts.create_intermediate_certificate('Intermediate', root)
  intermediate.set_key(intermediate_key)
  add_excluded_name_constraints(intermediate, **excluded)
  add_permitted_name_constraints(intermediate, **permitted)

  # Target certificate.
  target = gencerts.create_end_entity_certificate('t0', intermediate)
  target.set_key(target_key)
  add_sans(target, **sans)

  chain = [target, intermediate, root]
  gencerts.write_chain(doc, chain, '%s.pem' % name)


make_chain('ok-all-types',
           "A chain containing a large number of name constraints and names,\n"
           "but below the limit.",
           excluded=dict(num_dns=170,
                         num_ip=170,
                         num_dirnames=170,
                         num_uri=1025),
           permitted=dict(num_dns=171,
                          num_ip=171,
                          num_dirnames=172,
                          num_uri=1025),
           sans=dict(num_dns=341, num_ip=341, num_dirnames=342, num_uri=1025))

make_chain('toomany-all-types',
           "A chain containing a large number of different types of name\n"
           "constraints and names, above the limit.",
           excluded=dict(num_dns=170, num_ip=170, num_dirnames=170, num_uri=0),
           permitted=dict(num_dns=172, num_ip=171, num_dirnames=172, num_uri=0),
           sans=dict(num_dns=342, num_ip=341, num_dirnames=341, num_uri=0))

make_chain(
    'toomany-dns-excluded',
    "A chain containing a large number of excluded DNS name\n"
    "constraints and DNS names, above the limit.",
    excluded=dict(num_dns=1025, num_ip=0, num_dirnames=0, num_uri=0),
    permitted=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    sans=dict(num_dns=1024, num_ip=0, num_dirnames=0, num_uri=0))
make_chain(
    'toomany-ips-excluded',
    "A chain containing a large number of excluded IP name\n"
    "constraints and IP names, above the limit.",
    excluded=dict(num_dns=0, num_ip=1025, num_dirnames=0, num_uri=0),
    permitted=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    sans=dict(num_dns=0, num_ip=1024, num_dirnames=0, num_uri=0))
make_chain(
    'toomany-dirnames-excluded',
    "A chain containing a large number of excluded directory name\n"
    "constraints and directory names, above the limit.",
    excluded=dict(num_dns=0, num_ip=0, num_dirnames=1025, num_uri=0),
    permitted=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    sans=dict(num_dns=0, num_ip=0, num_dirnames=1024, num_uri=0))

make_chain(
    'toomany-dns-permitted',
    "A chain containing a large number of permitted DNS name\n"
    "constraints and DNS names, above the limit.",
    excluded=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    permitted=dict(num_dns=1025, num_ip=0, num_dirnames=0, num_uri=0),
    sans=dict(num_dns=1024, num_ip=0, num_dirnames=0, num_uri=0))
make_chain(
    'toomany-ips-permitted',
    "A chain containing a large number of permitted IP name\n"
    "constraints and IP names, above the limit.",
    excluded=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    permitted=dict(num_dns=0, num_ip=1025, num_dirnames=0, num_uri=0),
    sans=dict(num_dns=0, num_ip=1024, num_dirnames=0, num_uri=0))
make_chain(
    'toomany-dirnames-permitted',
    "A chain containing a large number of permitted directory name\n"
    "constraints and directory names, above the limit.",
    excluded=dict(num_dns=0, num_ip=0, num_dirnames=0, num_uri=0),
    permitted=dict(num_dns=0, num_ip=0, num_dirnames=1025, num_uri=0),
    sans=dict(num_dns=0, num_ip=0, num_dirnames=1024, num_uri=0))

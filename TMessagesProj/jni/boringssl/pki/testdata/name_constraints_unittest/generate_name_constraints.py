#!/usr/bin/env python3
# Copyright 2015 The Chromium Authors
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

import base64
import copy
import os
import random
import subprocess
import sys
import tempfile

sys.path += [os.path.join('..', 'verify_name_match_unittest', 'scripts')]

import generate_names


def generate(s, out_fn):
  conf_tempfile = tempfile.NamedTemporaryFile(mode='wt', encoding='utf-8')
  conf_tempfile.write(str(s))
  conf_tempfile.flush()
  der_tmpfile = tempfile.NamedTemporaryFile()
  subprocess.check_call([
      'openssl', 'asn1parse', '-genconf', conf_tempfile.name, '-i', '-out',
      der_tmpfile.name
  ],
                        stdout=subprocess.DEVNULL)
  conf_tempfile.close()

  description_tmpfile = tempfile.NamedTemporaryFile()
  subprocess.check_call(['der2ascii', '-i', der_tmpfile.name],
                        stdout=description_tmpfile)

  output_file = open(out_fn, 'wb')
  description_tmpfile.seek(0)
  output_file.write(description_tmpfile.read())
  output_file.write(b'-----BEGIN %b-----\n' % s.token())
  output_file.write(base64.encodebytes(der_tmpfile.read()))
  output_file.write(b'-----END %b-----\n' % s.token())
  output_file.close()


class SubjectAltNameGenerator:
  def __init__(self):
    self.names = []

  def token(self):
    return b"SUBJECT ALTERNATIVE NAME"

  def add_name(self, general_name):
    self.names.append(general_name)

  def __str__(self):
    s = "asn1 = SEQUENCE:subjectAltNameSequence\n"
    s += "[subjectAltNameSequence]\n"
    s_suffix = ""
    for n, name in enumerate(self.names):
      n1, n2 = (str(name) + '\n').split('\n', 1)
      if n2:
        s_suffix += n2 + '\n'
      s += '%s%s\n' % (n, n1)

    return s + s_suffix


class NameConstraintsGenerator:
  def __init__(self,
               force_permitted_sequence=False,
               force_excluded_sequence=False):
    self.permitted = []
    self.excluded = []
    self.force_permitted_sequence = force_permitted_sequence
    self.force_excluded_sequence = force_excluded_sequence

  def token(self):
    return b"NAME CONSTRAINTS"

  def union_from(self, c):
    self.permitted.extend(c.permitted)
    self.excluded.extend(c.excluded)

  def add_permitted(self, general_name):
    self.permitted.append(general_name)

  def add_excluded(self, general_name):
    self.excluded.append(general_name)

  def __str__(self):
    s = "asn1 = SEQUENCE:nameConstraintsSequence\n[nameConstraintsSequence]\n"

    if self.permitted or self.force_permitted_sequence:
      s += "permittedSubtrees = IMPLICIT:0,SEQUENCE:permittedSubtreesSequence\n"
    if self.excluded or self.force_excluded_sequence:
      s += "excludedSubtrees = IMPLICIT:1,SEQUENCE:excludedSubtreesSequence\n"

    if self.permitted or self.force_permitted_sequence:
      s += "[permittedSubtreesSequence]\n"
      for n, subtree in enumerate(self.permitted):
        s += 'subtree%i = SEQUENCE:permittedSubtree%i\n' % (n, n)

    if self.excluded or self.force_excluded_sequence:
      s += "[excludedSubtreesSequence]\n"
      for n, subtree in enumerate(self.excluded):
        s += 'subtree%i = SEQUENCE:excludedSubtree%i\n' % (n, n)

    for n, subtree in enumerate(self.permitted):
      s += '[permittedSubtree%i]\n%s\n' % (n, subtree)

    for n, subtree in enumerate(self.excluded):
      s += '[excludedSubtree%i]\n%s\n' % (n, subtree)

    return s


def other_name():
  i = random.randint(0, sys.maxsize)
  s = 'otherName = IMPLICIT:0,SEQUENCE:otherNameSequence%i\n' % i
  s += '[otherNameSequence%i]\n' % i
  s += 'type_id = OID:1.2.3.4.5\n'
  s += 'value = FORMAT:HEX,OCTETSTRING:DEADBEEF\n'
  return s


def rfc822_name(name):
  return 'rfc822Name = IMPLICIT:1,IA5STRING:' + name


def dns_name(name):
  return 'dNSName = IMPLICIT:2,IA5STRING:' + name


def x400_address():
  i = random.randint(0, sys.maxsize)
  s = 'x400Address = IMPLICIT:3,SEQUENCE:x400AddressSequence%i\n' % i
  s += '[x400AddressSequence%i]\n' % i
  s += 'builtinstandardattributes = SEQUENCE:BuiltInStandardAttributes%i\n' % i
  s += '[BuiltInStandardAttributes%i]\n' % i
  s += 'countryname = EXPLICIT:1A,PRINTABLESTRING:US\n'
  return s


def directory_name(name):
  return str(name).replace(
      'asn1 = SEQUENCE', 'directoryName = EXPLICIT:4,SEQUENCE')


def edi_party_name():
  i = random.randint(0, sys.maxsize)
  s = 'ediPartyName = IMPLICIT:5,SEQUENCE:ediPartyNameSequence%i\n' % i
  s += '[ediPartyNameSequence%i]\n' % i
  s += 'partyName = IMPLICIT:1,UTF8:foo\n'
  return s


def uniform_resource_identifier(name):
  return 'uniformResourceIdentifier = IMPLICIT:6,IA5STRING:' + name


def ip_address(addr, enforce_length=True):
  if enforce_length:
    assert len(addr) in (4,16)
  addr_str = ""
  for addr_byte in addr:
    addr_str += '%02X'%(addr_byte)
  return 'iPAddress = IMPLICIT:7,FORMAT:HEX,OCTETSTRING:' + addr_str


def ip_address_range(addr, netmask, enforce_length=True):
  if enforce_length:
    assert len(addr) in (4,16)
  addr_str = ""
  netmask_str = ""
  for addr_byte, mask_byte in zip(addr, netmask, strict=True):
    assert (addr_byte & ~mask_byte) == 0
    addr_str += '%02X'%(addr_byte)
    netmask_str += '%02X'%(mask_byte)
  return ('iPAddress = IMPLICIT:7,FORMAT:HEX,OCTETSTRING:' + addr_str +
          netmask_str)


def registered_id(oid):
  return 'registeredID = IMPLICIT:8,OID:' + oid


def with_min_max(val, minimum=None, maximum=None):
  s = val
  s += '\n'
  assert '\n[' not in s
  if minimum is not None:
    s += 'minimum = IMPLICIT:0,INTEGER:%i\n' % minimum
  if maximum is not None:
    s += 'maximum = IMPLICIT:1,INTEGER:%i\n' % maximum
  return s


def main():
  dnsname_constraints = NameConstraintsGenerator()
  dnsname_constraints.add_permitted(dns_name("permitted.example.com"))
  dnsname_constraints.add_permitted(dns_name("permitted.example2.com"))
  dnsname_constraints.add_permitted(dns_name("permitted.example3.com."))
  dnsname_constraints.add_permitted(dns_name("alsopermitted.example.com"))
  dnsname_constraints.add_excluded(dns_name("excluded.permitted.example.com"))
  dnsname_constraints.add_permitted(
      dns_name("stillnotpermitted.excluded.permitted.example.com"))
  dnsname_constraints.add_excluded(dns_name("extraneousexclusion.example.com"))
  generate(dnsname_constraints, "dnsname.pem")

  dnsname_constraints2 = NameConstraintsGenerator()
  dnsname_constraints2.add_permitted(dns_name("com"))
  dnsname_constraints2.add_excluded(dns_name("foo.bar.com"))
  generate(dnsname_constraints2, "dnsname2.pem")

  dnsname_constraints3 = NameConstraintsGenerator()
  dnsname_constraints3.add_permitted(dns_name(".bar.com"))
  generate(dnsname_constraints3, "dnsname-permitted_with_leading_dot.pem")

  dnsname_constraints4 = NameConstraintsGenerator()
  dnsname_constraints4.add_excluded(dns_name(".bar.com"))
  generate(dnsname_constraints4, "dnsname-excluded_with_leading_dot.pem")

  dnsname_constraints5 = NameConstraintsGenerator()
  dnsname_constraints5.add_permitted(dns_name(".."))
  generate(dnsname_constraints5, "dnsname-permitted_two_dot.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(dns_name("excluded.permitted.example.com"))
  generate(c, "dnsname-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(dns_name("permitted.example.com"))
  c.add_excluded(dns_name(""))
  generate(c, "dnsname-excludeall.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(dns_name("permitted.example.com"))
  c.add_excluded(dns_name("."))
  generate(c, "dnsname-exclude_dot.pem")

  ipaddress_constraints = NameConstraintsGenerator()
  ipaddress_constraints.add_permitted(
      ip_address_range((192,168,0,0),(255,255,0,0)))
  ipaddress_constraints.add_excluded(
      ip_address_range((192,168,5,0),(255,255,255,0)))
  ipaddress_constraints.add_permitted(
      ip_address_range((192,168,5,32),(255,255,255,224)))
  ipaddress_constraints.add_permitted(
      ip_address_range((192,167,5,32),(255,255,255,224)))
  ipaddress_constraints.add_excluded(
      ip_address_range((192,166,5,32),(255,255,255,224)))
  ipaddress_constraints.add_permitted(ip_address_range(
      (1,2,3,4,5,6,7,8,9,10,11,12,0,0,0,0),
      (255,255,255,255,255,255,255,255,255,255,255,255,0,0,0,0)))
  ipaddress_constraints.add_excluded(ip_address_range(
      (1,2,3,4,5,6,7,8,9,10,11,12,5,0,0,0),
      (255,255,255,255,255,255,255,255,255,255,255,255,255,0,0,0)))
  ipaddress_constraints.add_permitted(ip_address_range(
      (1,2,3,4,5,6,7,8,9,10,11,12,5,32,0,0),
      (255,255,255,255,255,255,255,255,255,255,255,255,255,224,0,0)))
  ipaddress_constraints.add_permitted(ip_address_range(
      (1,2,3,4,5,6,7,8,9,10,11,11,5,32,0,0),
      (255,255,255,255,255,255,255,255,255,255,255,255,255,224,0,0)))
  ipaddress_constraints.add_excluded(ip_address_range(
      (1,2,3,4,5,6,7,8,9,10,11,10,5,32,0,0),
      (255,255,255,255,255,255,255,255,255,255,255,255,255,224,0,0)))
  generate(ipaddress_constraints, "ipaddress.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,1,3),(255,255,255,255)))
  generate(c, "ipaddress-permit_singlehost.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((0,0,0,0),(0,0,0,0)))
  generate(c, "ipaddress-permit_all.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((0x80,0,0,0),(0x80,0,0,0)))
  generate(c, "ipaddress-permit_prefix1.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,1,2),(255,255,255,254)))
  generate(c, "ipaddress-permit_prefix31.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,1,0),(255,255,255,253)))
  generate(c, "ipaddress-invalid_mask_not_contiguous_1.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,0,0),(255,253,0,0)))
  generate(c, "ipaddress-invalid_mask_not_contiguous_2.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((0,0,0,0),(0x40,0,0,0)))
  generate(c, "ipaddress-invalid_mask_not_contiguous_3.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,0,0,0),(0xFF,0,0xFF,0)))
  generate(c, "ipaddress-invalid_mask_not_contiguous_4.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(ip_address_range((192,168,5,0),(255,255,255,0)))
  generate(c, "ipaddress-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,0,0),(255,255,0,0)))
  c.add_permitted(ip_address_range((1,2,3,4,5,6,7,8,9,10,11,12,0,0,0,0),
                                 (255,255,255,255,255,255,255,255,
                                  255,255,255,255,0,0,0,0)))
  c.add_excluded(ip_address_range((0,0,0,0),(0,0,0,0)))
  c.add_excluded(ip_address_range((0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
                                (0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)))
  generate(c, "ipaddress-excludeall.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192,168,0,0),(255,255,255,0)))
  c.add_permitted(ip_address_range((192,168,5,0,0),(255,255,255,0,0),
                                 enforce_length=False))
  generate(c, "ipaddress-invalid_addr.pem")

  v4_mapped_prefix = (0, ) * 10 + (255, ) * 2
  v4_mapped_mask = (255, ) * 12
  c = NameConstraintsGenerator()
  c.add_permitted(ip_address_range((192, 168, 1, 0), (255, 255, 255, 0)))
  c.add_excluded(ip_address_range((192, 168, 1, 1), (255, 255, 255, 255)))
  c.add_excluded(
      ip_address_range(v4_mapped_prefix + (192, 168, 1, 2),
                       v4_mapped_mask + (255, 255, 255, 255)))
  c.add_permitted(
      ip_address_range(v4_mapped_prefix + (192, 168, 2, 0),
                       v4_mapped_mask + (255, 255, 255, 0)))
  c.add_excluded(
      ip_address_range(v4_mapped_prefix + (192, 168, 2, 1),
                       v4_mapped_mask + (255, 255, 255, 255)))
  c.add_excluded(ip_address_range((192, 168, 2, 2), (255, 255, 255, 255)))
  generate(c, "ipaddress-mapped_addrs.pem")

  n_us = generate_names.NameGenerator()
  n_us.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'US')
  generate(n_us, "name-us.pem")
  n_us_az = copy.deepcopy(n_us)
  n_us_az.add_rdn().add_attr('stateOrProvinceName', 'UTF8', 'Arizona')
  generate(n_us_az, "name-us-arizona.pem")
  n_us_ca = copy.deepcopy(n_us)
  n_us_ca.add_rdn().add_attr('stateOrProvinceName', 'UTF8', 'California')
  generate(n_us_ca, "name-us-california.pem")
  n_us_ca_mountain_view = copy.deepcopy(n_us_ca)
  n_us_ca_mountain_view.add_rdn().add_attr(
      'localityName', 'UTF8', 'Mountain View')
  generate(n_us_ca_mountain_view, "name-us-california-mountain_view.pem")

  n_jp = generate_names.NameGenerator()
  n_jp.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'JP')
  generate(n_jp, "name-jp.pem")
  n_jp_tokyo = copy.deepcopy(n_jp)
  n_jp_tokyo.add_rdn().add_attr('stateOrProvinceName', 'UTF8', '\u6771\u4eac',
                                'FORMAT:UTF8')
  generate(n_jp_tokyo, "name-jp-tokyo.pem")

  n_us_az_foodotcom = copy.deepcopy(n_us_az)
  n_us_az_foodotcom.add_rdn().add_attr('commonName', 'UTF8', 'foo.com')
  generate(n_us_az_foodotcom, "name-us-arizona-foo.com.pem")

  n_us_az_permittedexamplecom = copy.deepcopy(n_us_az)
  n_us_az_permittedexamplecom.add_rdn().add_attr('commonName', 'UTF8',
                                                 'permitted.example.com')
  generate(n_us_az_permittedexamplecom,
           "name-us-arizona-permitted.example.com.pem")

  n_us_ca_permittedexamplecom = copy.deepcopy(n_us_ca)
  n_us_ca_permittedexamplecom.add_rdn().add_attr('commonName', 'UTF8',
                                                 'permitted.example.com')
  generate(n_us_ca_permittedexamplecom,
           "name-us-california-permitted.example.com.pem")

  n_us_az_ip1111 = copy.deepcopy(n_us_az)
  n_us_az_ip1111.add_rdn().add_attr('commonName', 'UTF8', '1.1.1.1')
  generate(n_us_az_ip1111, "name-us-arizona-1.1.1.1.pem")

  n_us_az_192_168_1_1 = copy.deepcopy(n_us_az)
  n_us_az_192_168_1_1.add_rdn().add_attr('commonName', 'UTF8', '192.168.1.1')
  generate(n_us_az_192_168_1_1, "name-us-arizona-192.168.1.1.pem")

  n_us_az_ipv6 = copy.deepcopy(n_us_az)
  n_us_az_ipv6.add_rdn().add_attr('commonName', 'UTF8',
                                  '102:304:506:708:90a:b0c::1')
  generate(n_us_az_ipv6, "name-us-arizona-ipv6.pem")

  n_us_ca_192_168_1_1 = copy.deepcopy(n_us_ca)
  n_us_ca_192_168_1_1.add_rdn().add_attr('commonName', 'UTF8', '192.168.1.1')
  generate(n_us_ca_192_168_1_1, "name-us-california-192.168.1.1.pem")

  n_us_az_email = copy.deepcopy(n_us_az)
  n_us_az_email.add_rdn().add_attr('emailAddress', 'IA5STRING',
                                   'bar@example.com')
  generate(n_us_az_email, "name-us-arizona-email.pem")

  n_us_az_email = copy.deepcopy(n_us_az)
  n_us_az_email.add_rdn().add_attr('emailAddress', 'IA5STRING',
                                   'FoO@example.com')
  generate(n_us_az_email, "name-us-arizona-email-localpartcase.pem")

  n_us_az_email = copy.deepcopy(n_us_az)
  n_us_az_email.add_rdn().add_attr('emailAddress', 'IA5STRING',
                                   'foo@example.com')
  n_us_az_email.add_rdn().add_attr('emailAddress', 'IA5STRING',
                                   'bar@example.com')
  generate(n_us_az_email, "name-us-arizona-email-multiple.pem")

  n_us_az_email = copy.deepcopy(n_us_az)
  n_us_az_email.add_rdn().add_attr('emailAddress', 'VISIBLESTRING',
                                   'bar@example.com')
  generate(n_us_az_email, "name-us-arizona-email-invalidstring.pem")

  n_ca = generate_names.NameGenerator()
  n_ca.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'CA')
  generate(n_ca, "name-ca.pem")

  n_de = generate_names.NameGenerator()
  n_de.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'DE')
  generate(n_de, "name-de.pem")

  n_empty = generate_names.NameGenerator()
  generate(n_empty, "name-empty.pem")


  directoryname_constraints = NameConstraintsGenerator()
  directoryname_constraints.add_permitted(directory_name(n_us))
  directoryname_constraints.add_excluded(directory_name(n_us_ca))
  directoryname_constraints.add_permitted(directory_name(n_us_ca_mountain_view))
  directoryname_constraints.add_excluded(directory_name(n_de))
  directoryname_constraints.add_permitted(directory_name(n_jp_tokyo))
  generate(directoryname_constraints, "directoryname.pem")

  c = NameConstraintsGenerator()
  c.union_from(directoryname_constraints)
  c.union_from(dnsname_constraints)
  generate(c, "directoryname_and_dnsname.pem")

  c = NameConstraintsGenerator()
  c.union_from(directoryname_constraints)
  c.union_from(dnsname_constraints)
  c.union_from(ipaddress_constraints)
  generate(c, "directoryname_and_dnsname_and_ipaddress.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(directory_name(n_us_ca))
  generate(c, "directoryname-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(directory_name(n_us))
  c.add_excluded(directory_name(n_empty))
  generate(c, "directoryname-excludeall.pem")

  san = SubjectAltNameGenerator()
  san.add_name(dns_name("permitted.example.com"))
  san.add_name(ip_address((192,168,1,2)))
  san.add_name(directory_name(n_us_az))
  generate(san, "san-permitted.pem")

  san2 = copy.deepcopy(san)
  san2.add_name(
      dns_name("foo.stillnotpermitted.excluded.permitted.example.com"))
  generate(san2, "san-excluded-dnsname.pem")

  san2 = copy.deepcopy(san)
  san2.add_name(ip_address((192,168,5,5)))
  generate(san2, "san-excluded-ipaddress.pem")

  san2 = copy.deepcopy(san)
  san2.add_name(directory_name(n_us_ca_mountain_view))
  generate(san2, "san-excluded-directoryname.pem")

  san = SubjectAltNameGenerator()
  san.add_name(other_name())
  generate(san, "san-othername.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@example.com"))
  generate(san, "san-rfc822name.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@eXaMplE.cOm"))
  generate(san, "san-rfc822name-domaincase.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("FoO@example.com"))
  generate(san, "san-rfc822name-localpartcase.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name('\\"foo\\"@example.com'))
  generate(san, "san-rfc822name-quoted.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("@example.com"))
  generate(san, "san-rfc822name-empty-localpart.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@subdomain.example.com"))
  generate(san, "san-rfc822name-subdomain.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@sUbdoMAin.exAmPLe.COm"))
  generate(san, "san-rfc822name-subdomaincase.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("example.com"))
  generate(san, "san-rfc822name-no-at.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@bar@example.com"))
  generate(san, "san-rfc822name-two-ats.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("subdomain.example.com"))
  generate(san, "san-rfc822name-subdomain-no-at.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@bar@subdomain.example.com"))
  generate(san, "san-rfc822name-subdomain-two-ats.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name(""))
  generate(san, "san-rfc822name-empty.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@[8.8.8.8]"))
  generate(san, "san-rfc822name-ipv4.pem")

  san = SubjectAltNameGenerator()
  san.add_name(rfc822_name("foo@example.com"))
  san.add_name(rfc822_name("bar@example.com"))
  generate(san, "san-rfc822name-multiple.pem")

  san = SubjectAltNameGenerator()
  san.add_name(dns_name("foo.example.com"))
  generate(san, "san-dnsname.pem")

  san = SubjectAltNameGenerator()
  san.add_name(x400_address())
  generate(san, "san-x400address.pem")

  san = SubjectAltNameGenerator()
  san.add_name(directory_name(n_us))
  generate(san, "san-directoryname.pem")

  san = SubjectAltNameGenerator()
  san.add_name(uniform_resource_identifier('http://example.com'))
  generate(san, "san-uri.pem")

  san = SubjectAltNameGenerator()
  san.add_name(ip_address((192,168,6,7)))
  generate(san, "san-ipaddress4.pem")

  san = SubjectAltNameGenerator()
  san.add_name(ip_address((0xFE, 0x80, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                           13, 14)))
  generate(san, "san-ipaddress6.pem")

  san = SubjectAltNameGenerator()
  san.add_name(registered_id("1.2.3.4"))
  generate(san, "san-registeredid.pem")

  san = SubjectAltNameGenerator()
  generate(san, "san-invalid-empty.pem")

  san = SubjectAltNameGenerator()
  san.add_name(ip_address((192,168,0,5,0), enforce_length=False))
  generate(san, "san-invalid-ipaddress.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(other_name())
  generate(c, "othername-permitted.pem")
  c = NameConstraintsGenerator()
  c.add_excluded(other_name())
  generate(c, "othername-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name("foo@example.com"))
  generate(c, "rfc822name-permitted.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name('\\"foo\\"@example.com'))
  generate(c, "rfc822name-permitted-quoted.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name("example.com"))
  generate(c, "rfc822name-permitted-hostname.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name("@example.com"))
  generate(c, "rfc822name-permitted-hostnamewithat.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name(".example.com"))
  generate(c, "rfc822name-permitted-subdomains.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name(""))
  generate(c, "rfc822name-permitted-empty.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(rfc822_name("[8.8.8.8]"))
  generate(c, "rfc822name-permitted-ipv4.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name("foo@example.com"))
  generate(c, "rfc822name-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name('\\"foo\\"@example.com'))
  generate(c, "rfc822name-excluded-quoted.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name("example.com"))
  generate(c, "rfc822name-excluded-hostname.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name("@example.com"))
  generate(c, "rfc822name-excluded-hostnamewithat.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name(".example.com"))
  generate(c, "rfc822name-excluded-subdomains.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name(""))
  generate(c, "rfc822name-excluded-empty.pem")

  c = NameConstraintsGenerator()
  c.add_excluded(rfc822_name("[8.8.8.8]"))
  generate(c, "rfc822name-excluded-ipv4.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(x400_address())
  generate(c, "x400address-permitted.pem")
  c = NameConstraintsGenerator()
  c.add_excluded(x400_address())
  generate(c, "x400address-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(edi_party_name())
  generate(c, "edipartyname-permitted.pem")
  c = NameConstraintsGenerator()
  c.add_excluded(edi_party_name())
  generate(c, "edipartyname-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(uniform_resource_identifier("http://example.com"))
  generate(c, "uri-permitted.pem")
  c = NameConstraintsGenerator()
  c.add_excluded(uniform_resource_identifier("http://example.com"))
  generate(c, "uri-excluded.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(registered_id("1.2.3.4"))
  generate(c, "registeredid-permitted.pem")
  c = NameConstraintsGenerator()
  c.add_excluded(registered_id("1.2.3.4"))
  generate(c, "registeredid-excluded.pem")

  c = NameConstraintsGenerator()
  generate(c, "invalid-no_subtrees.pem")

  c = NameConstraintsGenerator(force_permitted_sequence=True)
  generate(c, "invalid-empty_permitted_subtree.pem")

  c = NameConstraintsGenerator(force_excluded_sequence=True)
  generate(c, "invalid-empty_excluded_subtree.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(with_min_max(dns_name("permitted.example.com"), minimum=0))
  generate(c, "dnsname-with_min_0.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(with_min_max(dns_name("permitted.example.com"), minimum=1))
  generate(c, "dnsname-with_min_1.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(with_min_max(
      dns_name("permitted.example.com"), minimum=0, maximum=2))
  generate(c, "dnsname-with_min_0_and_max.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(with_min_max(
      dns_name("permitted.example.com"), minimum=1, maximum=2))
  generate(c, "dnsname-with_min_1_and_max.pem")

  c = NameConstraintsGenerator()
  c.add_permitted(with_min_max(dns_name("permitted.example.com"), maximum=2))
  generate(c, "dnsname-with_max.pem")


if __name__ == '__main__':
  main()

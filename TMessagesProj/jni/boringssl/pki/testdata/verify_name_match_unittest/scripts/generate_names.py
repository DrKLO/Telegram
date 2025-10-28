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
import subprocess
import tempfile


class RDN:
  def __init__(self):
    self.attrs = []

  def add_attr(self, attr_type, attr_value_type, attr_value,
               attr_modifier=None):
    self.attrs.append((attr_type, attr_value_type, attr_value, attr_modifier))
    return self

  def __str__(self):
    s = ''
    for n, attr in enumerate(self.attrs):
      s += 'attrTypeAndValue%i=SEQUENCE:attrTypeAndValueSequence%i_%i\n' % (
          n, id(self), n)

    s += '\n'
    for n, attr in enumerate(self.attrs):
      attr_type, attr_value_type, attr_value, attr_modifier = attr
      s += '[attrTypeAndValueSequence%i_%i]\n' % (id(self), n)
      # Note the quotes around the string value here, which is necessary for
      # trailing whitespace to be included by openssl.
      s += 'type=OID:%s\n' % attr_type
      s += 'value='
      if attr_modifier:
        s += attr_modifier + ','
      s += '%s:"%s"\n' % (attr_value_type, attr_value)

    return s


class NameGenerator:
  def __init__(self):
    self.rdns = []

  def token(self):
    return b"NAME"

  def add_rdn(self):
    rdn = RDN()
    self.rdns.append(rdn)
    return rdn

  def __str__(self):
    s = 'asn1 = SEQUENCE:rdnSequence%i\n\n[rdnSequence%i]\n' % (
        id(self), id(self))
    for n, rdn in enumerate(self.rdns):
      s += 'rdn%i = SET:rdnSet%i_%i\n' % (n, id(self), n)

    s += '\n'

    for n, rdn in enumerate(self.rdns):
      s += '[rdnSet%i_%i]\n%s\n' % (id(self), n, rdn)

    return s


def generate(s, fn):
  out_fn = os.path.join('..', 'names', fn + '.pem')
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
  output_file.write(b'-----BEGIN NAME-----\n')
  output_file.write(base64.encodebytes(der_tmpfile.read()))
  output_file.write(b'-----END NAME-----\n')
  output_file.close()


def unmangled(s):
  return s


def extra_whitespace(s):
  return '  ' + s.replace(' ', '   ') + '  '


def case_swap(s):
  return s.swapcase()


def main():
  for valuetype in ('PRINTABLESTRING', 'T61STRING', 'UTF8', 'BMPSTRING',
                    'UNIVERSALSTRING'):
    for string_mangler in (unmangled, extra_whitespace, case_swap):
      n=NameGenerator()
      n.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'US')
      n.add_rdn().add_attr('stateOrProvinceName',
                           valuetype,
                           string_mangler('New York'))
      n.add_rdn().add_attr('localityName',
                           valuetype,
                           string_mangler("ABCDEFGHIJKLMNOPQRSTUVWXYZ "
                                          "abcdefghijklmnopqrstuvwxyz "
                                          "0123456789 '()+,-./:=?"))

      n_extra_attr = copy.deepcopy(n)
      n_extra_attr.rdns[-1].add_attr('organizationName',
                                     valuetype,
                                     string_mangler('Name of company'))

      n_dupe_attr = copy.deepcopy(n)
      n_dupe_attr.rdns[-1].add_attr(*n_dupe_attr.rdns[-1].attrs[-1])

      n_extra_rdn = copy.deepcopy(n)
      n_extra_rdn.add_rdn().add_attr('organizationName',
                                     valuetype,
                                     string_mangler('Name of company'))

      filename_base = 'ascii-' + valuetype + '-' + string_mangler.__name__

      generate(n, filename_base)
      generate(n_extra_attr, filename_base + '-extra_attr')
      generate(n_dupe_attr, filename_base + '-dupe_attr')
      generate(n_extra_rdn, filename_base + '-extra_rdn')

  for valuetype in ('UTF8', 'BMPSTRING', 'UNIVERSALSTRING'):
    n=NameGenerator()
    n.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'JP')
    n.add_rdn().add_attr('localityName', valuetype, "\u6771\u4eac",
                         "FORMAT:UTF8")

    filename_base = 'unicode_bmp-' + valuetype + '-' + 'unmangled'
    generate(n, filename_base)

  for valuetype in ('UTF8', 'UNIVERSALSTRING'):
    n=NameGenerator()
    n.add_rdn().add_attr('countryName', 'PRINTABLESTRING', 'JP')
    n.add_rdn().add_attr('localityName', valuetype, "\U0001d400\U0001d419",
                         "FORMAT:UTF8")

    filename_base = 'unicode_supplementary-' + valuetype + '-' + 'unmangled'
    generate(n, filename_base)

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
value=PRINTABLESTRING:"US"
extra=PRINTABLESTRING:"hello world"
""", "invalid-AttributeTypeAndValue-extradata")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
""", "invalid-AttributeTypeAndValue-onlyOneElement")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
""", "invalid-AttributeTypeAndValue-empty")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=PRINTABLESTRING:"hello world"
value=PRINTABLESTRING:"US"
""", "invalid-AttributeTypeAndValue-badAttributeType")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SET:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
value=PRINTABLESTRING:"US"
""", "invalid-AttributeTypeAndValue-setNotSequence")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SEQUENCE:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
value=PRINTABLESTRING:"US"
""", "invalid-RDN-sequenceInsteadOfSet")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
""", "invalid-RDN-empty")

  generate("""asn1 = SET:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
value=PRINTABLESTRING:"US"
""", "invalid-Name-setInsteadOfSequence")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
""", "valid-Name-empty")

  # Certs with a RDN that is sorted differently due to length of the values, but
  # which should compare equal when normalized.
  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
attrTypeAndValue1=SEQUENCE:attrTypeAndValueSequence0_1
[attrTypeAndValueSequence0_0]
type=OID:stateOrProvinceName
value=PRINTABLESTRING:"    state"
[attrTypeAndValueSequence0_1]
type=OID:localityName
value=PRINTABLESTRING:"locality"
""", "ascii-PRINTABLESTRING-rdn_sorting_1")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
attrTypeAndValue1=SEQUENCE:attrTypeAndValueSequence0_1
[attrTypeAndValueSequence0_0]
type=OID:stateOrProvinceName
value=PRINTABLESTRING:"state"
[attrTypeAndValueSequence0_1]
type=OID:localityName
value=PRINTABLESTRING:" locality"
""", "ascii-PRINTABLESTRING-rdn_sorting_2")

  # Certs with a RDN that is sorted differently due to length of the values, and
  # also contains multiple values with the same type.
  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
attrTypeAndValue1=SEQUENCE:attrTypeAndValueSequence0_1
attrTypeAndValue2=SEQUENCE:attrTypeAndValueSequence0_2
attrTypeAndValue3=SEQUENCE:attrTypeAndValueSequence0_3
attrTypeAndValue4=SEQUENCE:attrTypeAndValueSequence0_4
[attrTypeAndValueSequence0_0]
type=OID:domainComponent
value=IA5STRING:"     cOm"
[attrTypeAndValueSequence0_1]
type=OID:domainComponent
value=IA5STRING:"eXaMple"
[attrTypeAndValueSequence0_2]
type=OID:domainComponent
value=IA5STRING:"wWw"
[attrTypeAndValueSequence0_3]
type=OID:localityName
value=PRINTABLESTRING:"NEw"
[attrTypeAndValueSequence0_4]
type=OID:localityName
value=PRINTABLESTRING:"   yORk    "
""", "ascii-mixed-rdn_dupetype_sorting_1")

  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
attrTypeAndValue1=SEQUENCE:attrTypeAndValueSequence0_1
attrTypeAndValue2=SEQUENCE:attrTypeAndValueSequence0_2
attrTypeAndValue3=SEQUENCE:attrTypeAndValueSequence0_3
attrTypeAndValue4=SEQUENCE:attrTypeAndValueSequence0_4
[attrTypeAndValueSequence0_0]
type=OID:domainComponent
value=IA5STRING:"cOM"
[attrTypeAndValueSequence0_1]
type=OID:domainComponent
value=IA5STRING:"eXampLE"
[attrTypeAndValueSequence0_2]
type=OID:domainComponent
value=IA5STRING:"    Www  "
[attrTypeAndValueSequence0_3]
type=OID:localityName
value=PRINTABLESTRING:"   nEw            "
[attrTypeAndValueSequence0_4]
type=OID:localityName
value=PRINTABLESTRING:"yoRK"
""", "ascii-mixed-rdn_dupetype_sorting_2")

  # Minimal valid config. Copy and modify this one when generating new invalid
  # configs.
  generate("""asn1 = SEQUENCE:rdnSequence
[rdnSequence]
rdn0 = SET:rdnSet0
[rdnSet0]
attrTypeAndValue0=SEQUENCE:attrTypeAndValueSequence0_0
[attrTypeAndValueSequence0_0]
type=OID:countryName
value=PRINTABLESTRING:"US"
""", "valid-minimal")

  # Single Name that exercises all of the string types, unicode (basic and
  # supplemental planes), whitespace collapsing, case folding, as well as SET
  # sorting.
  n = NameGenerator()
  rdn1 = n.add_rdn()
  rdn1.add_attr('countryName', 'PRINTABLESTRING', 'AA')
  rdn1.add_attr('stateOrProvinceName', 'T61STRING', '  AbCd  Ef  ')
  rdn1.add_attr('localityName', 'UTF8', "  Ab\u6771\u4eac ", "FORMAT:UTF8")
  rdn1.add_attr('organizationName', 'BMPSTRING', " aB  \u6771\u4eac  cD ",
                "FORMAT:UTF8")
  rdn1.add_attr('organizationalUnitName', 'UNIVERSALSTRING',
                " \U0001d400  A  bC ", "FORMAT:UTF8")
  rdn1.add_attr('domainComponent', 'IA5STRING', 'eXaMpLe')
  rdn2 = n.add_rdn()
  rdn2.add_attr('localityName', 'UTF8', "AAA")
  rdn2.add_attr('localityName', 'BMPSTRING', "aaa")
  rdn3 = n.add_rdn()
  rdn3.add_attr('localityName', 'PRINTABLESTRING', "cCcC")
  generate(n, "unicode-mixed-unnormalized")
  # Expected normalized version of above.
  n = NameGenerator()
  rdn1 = n.add_rdn()
  rdn1.add_attr('countryName', 'UTF8', 'aa')
  rdn1.add_attr('stateOrProvinceName', 'T61STRING', '  AbCd  Ef  ')
  rdn1.add_attr('localityName', 'UTF8', "ab\u6771\u4eac", "FORMAT:UTF8")
  rdn1.add_attr('organizationName', 'UTF8', "ab \u6771\u4eac cd", "FORMAT:UTF8")
  rdn1.add_attr('organizationalUnitName', 'UTF8', "\U0001d400 a bc",
                "FORMAT:UTF8")
  rdn1.add_attr('domainComponent', 'UTF8', 'example')
  rdn2 = n.add_rdn()
  rdn2.add_attr('localityName', 'UTF8', "aaa")
  rdn2.add_attr('localityName', 'UTF8', "aaa")
  rdn3 = n.add_rdn()
  rdn3.add_attr('localityName', 'UTF8', "cccc")
  generate(n, "unicode-mixed-normalized")


if __name__ == '__main__':
  main()

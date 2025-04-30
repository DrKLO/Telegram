#!/usr/bin/env python
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


def generate(token, s, out_fn):
  with tempfile.NamedTemporaryFile() as der_tmpfile:
    with tempfile.NamedTemporaryFile() as conf_tempfile:
      conf_tempfile.write(str(s))
      conf_tempfile.flush()
      description_tmpfile = tempfile.NamedTemporaryFile()
      subprocess.check_call(["openssl", "asn1parse", "-genconf",
                             conf_tempfile.name, "-i", "-out",
                             der_tmpfile.name],
                            stdout=description_tmpfile)

    with open(out_fn, "w") as output_file:
      description_tmpfile.seek(0)
      output_file.write(description_tmpfile.read())
      output_file.write("-----BEGIN %s-----\n" % token)
      output_file.write(base64.encodestring(der_tmpfile.read()))
      output_file.write("-----END %s-----\n" % token)


class CertificatePoliciesGenerator:
  def __init__(self):
    self.policies = []

  def generate(self, out_fn):
    generate("CERTIFICATE POLICIES", self, out_fn)

  def add_policy(self, policy):
    self.policies.append(policy)

  def __str__(self):
    s = "asn1 = SEQUENCE:certificatePoliciesSequence\n"
    s += "[certificatePoliciesSequence]\n"
    s_suffix = ""
    for n, policy in enumerate(self.policies):
      n1, n2 = (str(policy) + "\n").split("\n", 1)
      if n2:
        s_suffix += n2 + "\n"
      s += "%s%s\n" % (n, n1)

    return s + s_suffix


def policy_qualifier(qualifier_id, qualifier):
  i = random.randint(0, sys.maxint)
  s = "asn1 = SEQUENCE:PolicyQualifierInfoSequence%i\n" % i
  s += "[PolicyQualifierInfoSequence%i]\n" % i
  s += "policyQualifierId = %s\n" % qualifier_id
  s += qualifier
  return s


def cps_uri_qualifier(url):
  return policy_qualifier("OID:id-qt-cps", "cPSUri = IA5STRING:%s\n" % url)


def policy_information(policy_id, qualifiers):
  i = random.randint(0, sys.maxint)
  s = "policyInformation = SEQUENCE:PolicyInformationSequence%i\n" % i
  s += "[PolicyInformationSequence%i]\n" % i
  s += "policyIdentifier = OID:%s\n" % policy_id
  s_suffix = ""
  if qualifiers is not None:
    s += "policyQualifiers = SEQUENCE:PolicyQualifiersSequence%i\n" % i
    s += "[PolicyQualifiersSequence%i]\n" % i
    for n, qualifier in enumerate(qualifiers):
      n1, n2 = (str(qualifier) + "\n").split("\n", 1)
      if n2:
        s_suffix += n2 + "\n"
      s += "%s%s\n" % (n, n1)

  return s + s_suffix


def main():
  p = CertificatePoliciesGenerator()
  p.generate("invalid-empty.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("anyPolicy", None))
  p.generate("anypolicy.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("anyPolicy", [
      cps_uri_qualifier("https://example.com/1_2_3")]))
  p.generate("anypolicy_with_qualifier.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("anyPolicy", [
      policy_qualifier("OID:1.2.3.4", 'foo = UTF8:"hi"')]))
  p.generate("invalid-anypolicy_with_custom_qualifier.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", None))
  p.generate("policy_1_2_3.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", [
      cps_uri_qualifier("https://example.com/1_2_3")]))
  p.generate("policy_1_2_3_with_qualifier.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", [
      policy_qualifier("OID:1.2.3.4", 'foo = UTF8:"hi"')]))
  p.generate("policy_1_2_3_with_custom_qualifier.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", None))
  p.add_policy(policy_information("1.2.3", [
      cps_uri_qualifier("https://example.com/1_2_3")]))
  p.generate("invalid-policy_1_2_3_dupe.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", []))
  p.generate("invalid-policy_1_2_3_with_empty_qualifiers_sequence.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", None))
  p.add_policy(policy_information("1.2.4", None))
  p.generate("policy_1_2_3_and_1_2_4.pem")

  p = CertificatePoliciesGenerator()
  p.add_policy(policy_information("1.2.3", [
      cps_uri_qualifier("https://example.com/1_2_3")]))
  p.add_policy(policy_information("1.2.4", [
      cps_uri_qualifier("http://example.com/1_2_4")]))
  p.generate("policy_1_2_3_and_1_2_4_with_qualifiers.pem")

  generate("CERTIFICATE POLICIES",
           "asn1 = SEQUENCE:certificatePoliciesSequence\n"
           "[certificatePoliciesSequence]\n"
           "policyInformation = SEQUENCE:PolicyInformationSequence\n"
           'extradata = IA5STRING:"unconsumed data"\n'
           "[PolicyInformationSequence]\n"
           "policyIdentifier = OID:1.2.3\n",
           "invalid-policy_1_2_3_policyinformation_unconsumed_data.pem")

  generate("CERTIFICATE POLICIES",
           "asn1 = SEQUENCE:certificatePoliciesSequence\n"
           "[certificatePoliciesSequence]\n"
           "policyInformation = SEQUENCE:PolicyInformationSequence\n"
           "[PolicyInformationSequence]\n"
           "policyIdentifier = OID:1.2.3\n"
           "policyQualifiers = SEQUENCE:PolicyQualifiersSequence\n"
           "[PolicyQualifiersSequence]\n"
           "policyQualifierInfo = SEQUENCE:PolicyQualifierInfoSequence\n"
           "[PolicyQualifierInfoSequence]\n"
           "policyQualifierId = OID:id-qt-cps\n"
           "cPSUri = IA5STRING:https://example.com/1_2_3\n"
           'extradata = IA5STRING:"unconsumed data"\n',
           "invalid-policy_1_2_3_policyqualifierinfo_unconsumed_data.pem")

  generate("CERTIFICATE POLICIES",
           "asn1 = SEQUENCE:certificatePoliciesSequence\n"
           "[certificatePoliciesSequence]\n"
           "policyInformation = SEQUENCE:PolicyInformationSequence\n"
           "[PolicyInformationSequence]\n"
           'policyIdentifier = IA5STRING:"1.2.3"\n',
           "invalid-policy_identifier_not_oid.pem")


if __name__ == "__main__":
  main()

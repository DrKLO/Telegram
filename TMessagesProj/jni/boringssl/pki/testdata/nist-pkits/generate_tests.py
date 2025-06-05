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

'''Generates a test suite from NIST PKITS test descriptions.

The output is a set of Type Parameterized Tests which are included by
pkits_unittest.h. See pkits_unittest.h for information on using the tests.
GoogleTest has a limit of 50 tests per type parameterized testcase, so the tests
are split up by section number (this also makes it possible to easily skip
sections that pertain to non-implemented features).

Usage:
  generate_tests.py <PKITS.pdf> <output.h>
'''

import os
import re
import subprocess
import sys
import tempfile


def sanitize_name(s):
  return s.translate(str.maketrans('', '', ' -'))


def finalize_test_case(test_case_name, sanitized_test_names, output):
  output.write('\nWRAPPED_REGISTER_TYPED_TEST_SUITE_P(%s' % test_case_name)
  for name in sanitized_test_names:
    output.write(',\n    %s' % name)
  output.write(');\n')


def bool_to_str(b):
  return "true" if b else "false"


def make_policies_string(policies):
  return '"' + ','.join(policies) + '"'


def output_test(test_case_name, test_number, raw_test_name, subpart_number,
                info, certs, crls, sanitized_test_names, output):
  '''Writes a test case to |output|, and appends the test name to
  |sanitized_test_names|.'''
  sanitized_test_name = 'Section%s%s' % (test_number.split('.')[1],
                                         sanitize_name(raw_test_name))

  subpart_comment = ''
  if subpart_number is not None:
    sanitized_test_name += "Subpart%d" % (subpart_number)
    subpart_comment = ' (Subpart %d)' % (subpart_number)

  sanitized_test_names.append(sanitized_test_name)

  certs_formatted = ', '.join('"%s"' % n for n in certs)
  crls_formatted = ', '.join('"%s"' % n for n in crls)

  output.write('''
// %(test_number)s %(raw_test_name)s%(subpart_comment)s
WRAPPED_TYPED_TEST_P(%(test_case_name)s, %(sanitized_test_name)s) {
  const char* const certs[] = {
    %(certs_formatted)s
  };
  const char* const crls[] = {
    %(crls_formatted)s
  };
''' % vars())

  default_info = TestInfo(None)

  if info.include_subpart_in_test_number:
    test_number = "%s.%d" % (test_number, subpart_number)

  output.write('''PkitsTestInfo info;
  info.test_number = "%s";
  info.should_validate = %s;
''' % (test_number, bool_to_str(info.should_validate)))

  # Output any non-default inputs/outputs. Only properties that differ from
  # the defaults are written, so as to keep the generated file more readable.
  if info.initial_policy_set != default_info.initial_policy_set:
    output.write('''  info.SetInitialPolicySet(%s);
''' % make_policies_string(info.initial_policy_set))

  if info.initial_explicit_policy != default_info.initial_explicit_policy:
    output.write('''  info.SetInitialExplicitPolicy(%s);
''' % bool_to_str(info.initial_explicit_policy))

  if (info.initial_policy_mapping_inhibit !=
          default_info.initial_policy_mapping_inhibit):
    output.write('''  info.SetInitialPolicyMappingInhibit(%s);
''' % bool_to_str(info.initial_policy_mapping_inhibit))

  if (info.initial_inhibit_any_policy !=
          default_info.initial_inhibit_any_policy):
    output.write('''  info.SetInitialInhibitAnyPolicy(%s);
''' % bool_to_str(info.initial_inhibit_any_policy))

  if (info.user_constrained_policy_set !=
          default_info.user_constrained_policy_set):
    output.write('''  info.SetUserConstrainedPolicySet(%s);
''' % make_policies_string(info.user_constrained_policy_set))

  output.write('''
  this->RunTest(certs, crls, info);
}
''' % vars())


# Matches a section header, ex: "4.1 Signature Verification"
SECTION_MATCHER = re.compile('^\s*(\d+\.\d+)\s+(.+?)\s*\ufffd?$')
# Matches a test header, ex: "4.1.1 Valid Signatures Test1"
TEST_MATCHER = re.compile('^\s*(\d+\.\d+.\d+)\s+(.+?)\s*\ufffd?$')

# Matches the various headers in a test specification.
EXPECTED_HEADER_MATCHER = re.compile('^\s*Expected Result:')
PROCEDURE_HEADER_MATCHER = re.compile('^\s*Procedure:')
PATH_HEADER_MATCHER = re.compile('^\s*Certification Path:')

# Matches the Procedure text if using default settings.
USING_DEFAULT_SETTINGS_MATCHER = re.compile(
    '^.*using the \s*default settings.*')

# Matches the description text if using custom settings.
CUSTOM_SETTINGS_MATCHER = re.compile(
    '.*this\s+test\s+be\s+validated\s+using\s+the\s+following\s+inputs:.*')

# Match an expected test result. Note that some results in the PDF have a typo
# "path not should validate" instead of "path should not validate".
TEST_RESULT_MATCHER = re.compile(
    '^.*path (should validate|should not validate|not should validate)')

# Matches a line in the certification path, ex:
#    "\u2022 Good CA Cert, Good CA CRL"
PATH_MATCHER = re.compile('^\s*\u2022\s*(.+)\s*$')
# Matches a page number. These may appear in the middle of multi-line fields and
# thus need to be ignored.
PAGE_NUMBER_MATCHER = re.compile('^\s*\d+\s*$')
# Matches if an entry in a certification path refers to a CRL, ex:
# "onlySomeReasons CA2 CRL1".
CRL_MATCHER = re.compile('^.*CRL\d*$')


class TestSections(object):
  def __init__(self):
    self.description_lines = []
    self.procedure_lines = []
    self.expected_result_lines = []
    self.cert_path_lines = []


def parse_main_test_sections(lines, i):
  result = TestSections()

  # Read the description lines (text after test name up until
  # "Procedure:").
  result.description_lines = []
  while i < len(lines):
    if PROCEDURE_HEADER_MATCHER.match(lines[i]):
      break
    result.description_lines.append(lines[i])
    i += 1

  # Read the procedure lines (text starting at "Procedure:" and up until
  # "Expected Result:".
  result.procedure_lines = []
  while i < len(lines):
    if EXPECTED_HEADER_MATCHER.match(lines[i]):
      break
    result.procedure_lines.append(lines[i])
    i += 1

  # Read the expected result lines (text starting at "Expected Result:" and up
  # until "Certification Path:".
  result.expected_result_lines = []
  while i < len(lines):
    if PATH_HEADER_MATCHER.match(lines[i]):
      break
    result.expected_result_lines.append(lines[i])
    i += 1

  # Read the certification path lines (text starting at "Certification Path:"
  # and up until the next test title.
  result.cert_path_lines = []
  while i < len(lines):
    if TEST_MATCHER.match(lines[i]) or SECTION_MATCHER.match(lines[i]):
      break
    result.cert_path_lines.append(lines[i])
    i += 1

  return i, result


def parse_cert_path_lines(lines):
  path_lines = []
  crls = []
  certs = []

  for line in lines[1:]:
    line = line.strip()

    if "is composed of the following objects:" in line:
      continue
    if "See the introduction to Section 4.4 for more information." in line:
      continue

    if not line or PAGE_NUMBER_MATCHER.match(line):
      continue
    path_match = PATH_MATCHER.match(line)
    if path_match:
      path_lines.append(path_match.group(1))
      continue
    # Continuation of previous path line.
    path_lines[-1] += ' ' + line

  for path_line in path_lines:
    for path in path_line.split(','):
      path = sanitize_name(path.strip())
      if CRL_MATCHER.match(path):
        crls.append(path)
      else:
        certs.append(path)

  return certs, crls


ANY_POLICY = 'anyPolicy'
TEST_POLICY_1 = 'NIST-test-policy-1'
TEST_POLICY_2 = 'NIST-test-policy-2'
TEST_POLICY_3 = 'NIST-test-policy-3'
TEST_POLICY_6 = 'NIST-test-policy-6'

# Note: This omits some outputs from PKITS:
#
#  * authorities-constrained-policy-set
#  * explicit-policy-indicator
class TestInfo(object):
  """This structure describes a test inputs and outputs"""

  def __init__(self, should_validate,
               # These defaults come from section 3 of PKITS.pdf
               initial_policy_set = [ANY_POLICY],
               initial_explicit_policy = False,
               initial_policy_mapping_inhibit = False,
               initial_inhibit_any_policy = False,
               # In all of the tests that are not related to policy processing,
               # each certificate in the path asserts the certificate policy
               # 2.16.840.1.101.3.2.1.48.1
               user_constrained_policy_set = [TEST_POLICY_1],
               include_subpart_in_test_number = False):
    self.should_validate = should_validate
    self.initial_policy_set = initial_policy_set
    self.initial_explicit_policy = initial_explicit_policy
    self.initial_policy_mapping_inhibit = initial_policy_mapping_inhibit
    self.initial_inhibit_any_policy = initial_inhibit_any_policy
    self.user_constrained_policy_set = user_constrained_policy_set
    self.include_subpart_in_test_number = include_subpart_in_test_number


TEST_OVERRIDES = {
  '4.8.1': [ # All Certificates Same Policy Test1
    # 1. default settings, but with initial-explicit-policy set. The path
    # should validate successfully
    TestInfo(True, initial_explicit_policy=True,
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-explicit-policy set and
    # initial-policy-set = {NIST-test-policy-1}. The path should validate
    # successfully.
    TestInfo(True, initial_explicit_policy=True,
             initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 3. default settings, but with initial-explicit-policy set and
    # initial-policy-set = {NIST-test-policy-2}. The path should not validate
    # successfully.
    TestInfo(False, initial_explicit_policy=True,
             initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[]),

    # 4. default settings, but with initial-explicit-policy set and
    # initial-policy-set = {NIST-test-policy-1, NIST-test-policy-2}. The path
    # should validate successfully.
    TestInfo(True, initial_explicit_policy=True,
             initial_policy_set=[TEST_POLICY_1, TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.2': [ # All Certificates No Policies Test2
    # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[]),

    # 2. default settings, but with initial-explicit-policy set. The path
    # should not validate successfully
    TestInfo(False, initial_explicit_policy=True,
             user_constrained_policy_set=[]),
  ],

  '4.8.3': [ # Different Policies Test3
    # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[]),

    # 2. default settings, but with initial-explicit-policy set. The path
    # should not validate successfully.
    TestInfo(False, initial_explicit_policy=True, user_constrained_policy_set=[]),

    # 3. default settings, but with initial-explicit-policy set and
    # initial-policy-set = {NIST-test-policy-1, NIST-test-policy-2}. The path
    # should not validate successfully.
    TestInfo(False, initial_explicit_policy=True,
             initial_policy_set=[TEST_POLICY_1, TEST_POLICY_2],
             user_constrained_policy_set=[]),
  ],

  '4.8.4': [ # Different Policies Test4
    # Procedure: Validate Different Policies Test4 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.69 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set if the application can process the policyConstraints
    # extension. If the application can process the policyConstraints extension
    # then the path should not validate successfully. If the application can
    # not process the policyConstraints extension, then the path should
    # validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.5': [ # 4.8.5 Different Policies Test5
    # Procedure: Validate Different Policies Test5 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.70 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set if the application can process the policyConstraints
    # extension. If the application can process the policyConstraints extension
    # then the path should not validate successfully. If the application can
    # not process the policyConstraints extension, then the path should
    # validate successfully
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.6': [ # Overlapping Policies Test6
    # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 3. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[]),
  ],

  '4.8.7': [ # Different Policies Test7
    # Procedure: Validate Different Policies Test7 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.72 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. If the
    # explicit-policy-indicator will be set if the application can process the
    # policyConstraints extension. If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully. If the application can not process the policyConstraints
    # extension, then the path should validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.8': [ # Different Policies Test8
    # Procedure: Validate Different Policies Test8 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.73 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set if the application can process the policyConstraints
    # extension. If the application can process the policyConstraints extension
    # then the path should not validate successfully. If the application can
    # not process the policyConstraints extension, then the path should
    # validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.9': [ # Different Policies Test9
    # Procedure: Validate Different Policies Test9 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.74 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set if the application can process the policyConstraints
    # extension. If the application can process the policyConstraints
    # extension, then the path should not validate successfully. If the
    # application can not process the policyConstraints extension, then the
    # path should validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.10': [ # All Certificates Same Policies Test10
    # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1, TEST_POLICY_2]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 3. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_2]),
  ],

  '4.8.11': [ # All Certificates AnyPolicy Test11
    # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[ANY_POLICY]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.12': [ # Different Policies Test12
    # Procedure: Validate Different Policies Test12 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.77 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set if the application can process the policyConstraints
    # extension. If the application can process the policyConstraints
    # extension, then the path should not validate successfully. If the
    # application can not process the policyConstraints extension, then the
    # path should validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.8.13': [ # All Certificates Same Policies Test13
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_2]),

    # 3. default settings, but with initial-policy-set = {NIST-test-policy-3}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_3],
             user_constrained_policy_set=[TEST_POLICY_3]),
  ],

  '4.8.14': [ # AnyPolicy Test14
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[]),
  ],

  '4.8.15': [ # User Notice Qualifier Test15
    # Procedure: Validate User Notice Qualifier Test15 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.80 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be the same
    # as the initial-explicit-policy indicator. If the initial-policy-set is
    # any-policy or otherwise includes NIST-test-policy-1, then the
    # user-constrained-policy-set will be {NIST-test-policy-1}. If not, the
    # user-constrained-policy-set will be empty. If the initial-explicit-policy
    # indicator is set and the initial-policy-set does not include
    # NIST-test-policy-1, then the path should be rejected, otherwise it should
    # validate successfully. If the path validates successfully, then the
    # application should display the user notice.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.16': [ # User Notice Qualifier Test16
    # Procedure: Validate User Notice Qualifier Test16 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.81 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be the same
    # as the initial-explicit-policy indicator. If the initial-policy-set is
    # any-policy or otherwise includes NIST-test-policy-1, then the
    # user-constrained-policy-set will be {NIST-test-policy-1}. If not, the
    # user-constrained-policy-set will be empty. If the initial-explicit-policy
    # indicator is set and the initial-policy-set does not include
    # NIST-test-policy-1, then the path should be rejected, otherwise it should
    # validate successfully. If the path validates successfully, then the
    # application should display the user notice associated with
    # NIST-test-policy-1. The user notice associated with NIST-test-policy-2
    # should not be displayed.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.17': [ # User Notice Qualifier Test17
    # Procedure: Validate User Notice Qualifier Test17 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.82 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be the same
    # as the initial-explicit-policy indicator. If the initial-policy-set is
    # any-policy or otherwise includes NIST-test-policy-1, then the
    # user-constrained-policy-set will be {NIST-test-policy-1}. If not, the
    # user-constrained-policy-set will be empty. If the initial-explicit-policy
    # indicator is set and the initial-policy-set does not include
    # NIST-test-policy-1, then the path should be rejected, otherwise it should
    # validate successfully. If the path validates successfully, then the
    # application should display the user notice associated with anyPolicy.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.18': [ # User Notice Qualifier Test18
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully and the qualifier associated with
    # NIST-test-policy-1 in the end entity certificate should be displayed.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should validate successfully and the qualifier associated with
    # anyPolicy in the end entity certificate should be displayed.
    TestInfo(True, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_2]),
  ],

  '4.8.19': [ # User Notice Qualifier Test19
    # Procedure: Validate User Notice Qualifier Test19 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.84 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be the same
    # as the initial-explicit-policy indicator. If the initial-policy-set is
    # any-policy or otherwise includes NIST-test-policy-1, then the
    # user-constrained-policy-set will be {NIST-test-policy-1}. If not, the
    # user-constrained-policy-set will be empty. If the initial-explicit-policy
    # indicator is set and the initial-policy-set does not include
    # NIST-test-policy-1, then the path should be rejected, otherwise it should
    # validate successfully.  Since the explicitText exceeds the maximum size
    # of 200 characters, the application may choose to reject the certificate.
    # If the application accepts the certificate, display of the user notice is
    # optional.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.8.20': [ # CPS Pointer Qualifier Test20
    # Procedure: Validate CPS Pointer Qualifier Test20 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.85 using the
    # default settings. (If possible, it is recommended that this test be run
    # with the initial-explicit-policy indicator set. If this can not be done,
    # manually check that the authorities-constrained-policy-set and
    # user-constrained-policy-set are correct.)
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be the same
    # as the initial-explicit-policy indicator. If the initial-policy-set is
    # any-policy or otherwise includes NIST-test-policy-1, then the
    # user-constrained-policy-set will be {NIST-test-policy-1}. If not, the
    # user-constrained-policy-set will be empty. If the initial-explicit-policy
    # indicator is set and the initial-policy-set does not include
    # NIST-test-policy-1, then the path should be rejected, otherwise it should
    # validate successfully. The CPS pointer in the qualifier should be
    # associated with NIST-testpolicy-1 in the
    # authorities-constrained-policy-set (and in the user-constrained-policy-set
    # if NIST-test-policy-1 is in that set). There are no processing
    # requirements associated with the CPS pointer qualifier.
    TestInfo(True, initial_explicit_policy=True,
             initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.9.1': [ # Valid RequireExplicitPolicy Test1
    # Procedure: Validate Valid requireExplicitPolicy Test1 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.86 using
    # the default settings.
    #
    # Expected Result: The path should validate successfully since the
    # explicit-policy-indicator is not set.
    TestInfo(True, user_constrained_policy_set=[]),
  ],

  '4.9.2': [ # Valid RequireExplicitPolicy Test2
    # Procedure: Validate Valid requireExplicitPolicy Test2 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.87 using
    # the default settings.
    #
    # Expected Result: The path should validate successfully since the
    # explicit-policy-indicator is not set
    TestInfo(True, user_constrained_policy_set=[]),
  ],

  '4.9.6': [ # Valid Self-Issued requireExplicitPolicy Test6
    # Procedure: Validate Valid Self-Issued requireExplicitPolicy Test6 EE using
    # the default settings or open and verify Signed Test Message 6.2.2.91 using
    # the default settings.
    #
    # Expected Result: The path should validate successfully since the
    # explicit-policy-indicator is not set.
    TestInfo(True, user_constrained_policy_set=[]),
  ],

  '4.10.1': [ # Valid Policy Mapping Test1
    # The errors in subparts 2 and 3 vary slightly, so we set
    # include_subpart_in_test_number.

    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1],
             include_subpart_in_test_number=True),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[],
             include_subpart_in_test_number=True),

    # 3. default settings, but with initial-policy-mapping-inhibit set. The
    # path should not validate successfully.
    TestInfo(False, initial_policy_mapping_inhibit=True,
             user_constrained_policy_set=[],
             include_subpart_in_test_number=True),
  ],

  '4.10.2': [ # Invalid Policy Mapping Test2
    # 1. default settings. The path should not validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),

    # 2. default settings, but with initial-policy-mapping-inhibit set. The
    # path should not validate successfully.
    TestInfo(False, initial_policy_mapping_inhibit=True,
             user_constrained_policy_set=[]),
  ],

  '4.10.3': [ # Valid Policy Mapping Test3
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_2]),
  ],

  '4.10.4': [ # Invalid Policy Mapping Test4
    # Procedure: Validate Invalid Policy Mapping Test4 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.97 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should be rejected, otherwise
    # it should validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.10.5': [ # Valid Policy Mapping Test5
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-6}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_6],
             user_constrained_policy_set=[]),
  ],

  '4.10.6': [ # Valid Policy Mapping Test6
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
                   user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-6}.
    # The path should not validate successfully.
    TestInfo(False, initial_policy_set=[TEST_POLICY_6],
             user_constrained_policy_set=[]),
  ],

  '4.10.7': [ # Invalid Mapping From anyPolicy Test7
    # Procedure: Validate Invalid Mapping From anyPolicy Test7 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.100 using
    # the default settings.
    #
    # Expected Result: The path should not validate successfully since the
    # intermediate certificate includes a policy mapping extension in which
    # anyPolicy appears as an issuerDomainPolicy.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.10.8': [ # Invalid Mapping To anyPolicy Test8
    # Procedure: Validate Invalid Mapping To anyPolicy Test8 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.101 using
    # the default settings.
    #
    # Expected Result: The path should not validate successfully since the
    # intermediate certificate includes a policy mapping extension in which
    # anyPolicy appears as an subjectDomainPolicy.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.10.9': [ # Valid Policy Mapping Test9
    # Procedure: Validate Valid Policy Mapping Test9 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.102 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1}. If not, the user-constrained-policy-set will be
    # empty. If the initial-policy-set does not include NIST-test-policy-1 (and
    # the application can process the policyConstraints extension), then the
    # path should be rejected, otherwise it should validate successfully.
    TestInfo(True),
  ],

  '4.10.10': [ # Invalid Policy Mapping Test10
    # Procedure: Validate Invalid Policy Mapping Test10 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.103 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should be rejected, otherwise
    # it should validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.10.11': [ # Valid Policy Mapping Test11
    # Procedure: Validate Valid Policy Mapping Test11 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.104 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1}. If not, the user-constrained-policy-set will be
    # empty. If the initial-policy-set does not include NIST-test-policy-1 (and
    # the application can process the policyConstraints extension), then the
    # path should be rejected, otherwise it should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.10.12': [ # Valid Policy Mapping Test12
    # 1. default settings, but with initial-policy-set = {NIST-test-policy-1}.
    # The path should validate successfully and the application should display
    # the user notice associated with NIST-test-policy-3 in the end entity
    # certificate.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1],
             user_constrained_policy_set=[TEST_POLICY_1]),

    # 2. default settings, but with initial-policy-set = {NIST-test-policy-2}.
    # The path should validate successfully and the application should display
    # the user notice associated with anyPolicy in the end entity certificate.
    TestInfo(True, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_2]),
  ],

  '4.10.13': [ # Valid Policy Mapping Test13
    # Procedure: Validate Valid Policy Mapping Test13 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.106 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1}. If not, the user-constrained-policy-set will be
    # empty. If the initial-policy-set does not include NIST-test-policy-1 (and
    # the application can process the policyConstraints extension), then the
    # path should be rejected, otherwise it should validate successfully. If
    # the path is accepted, the application should display the user notice
    # associated with NIST-testpolicy-1 in the intermediate certificate.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),

    # While not explicitly divided into sub-parts, the above describes what
    # should happen given various values of initial-policy-set. Test some
    # combinations, as these cover an interesting interaction with anyPolicy.
    #
    # These extra tests are a regression test for https://crbug.com/1403258.
    TestInfo(True, initial_policy_set=[TEST_POLICY_1, TEST_POLICY_2],
             user_constrained_policy_set=[TEST_POLICY_1]),
    TestInfo(False, initial_policy_set=[TEST_POLICY_2],
             user_constrained_policy_set=[]),
  ],

  '4.10.14': [ # Valid Policy Mapping Test14
    # Procedure: Validate Valid Policy Mapping Test14 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.107 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1}. If not, the user-constrained-policy-set will be
    # empty. If the initial-policy-set does not include NIST-test-policy-1 (and
    # the application can process the policyConstraints extension), then the
    # path should be rejected, otherwise it should validate successfully. If
    # the path is accepted, the application should display the user notice
    # associated with anyPolicy in the intermediate certificate
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.11.1': [ # Invalid inhibitPolicyMapping Test1
    # Procedure: Validate Invalid inhibitPolicyMapping Test1 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.108 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty. The explicit-policy-indicator
    # will be set.  The path should not validate successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.2': [ # Valid inhibitPolicyMapping Test2
    # Procedure: Validate Valid inhibitPolicyMapping Test2 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.109 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set. If
    # the initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.11.3': [ # Invalid inhibitPolicyMapping Test3
    # Procedure: Validate Invalid inhibitPolicyMapping Test3 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.110 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set.  The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.4': [ # Valid inhibitPolicyMapping Test4
    # Procedure: Validate Valid inhibitPolicyMapping Test4 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.111 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-2} and the explicit-policy-indicator will be set. If
    # the initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-2, then the path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_2]),
  ],

  '4.11.5': [ # Invalid inhibitPolicyMapping Test5
    # Procedure: Validate Invalid inhibitPolicyMapping Test5 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.112 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set.  The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.6': [ # Invalid inhibitPolicyMapping Test6
    # Procedure: Validate Invalid inhibitPolicyMapping Test6 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.113 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and the
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set. The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.7': [ # Valid Self-Issued inhibitPolicyMapping Test7
    # Procedure: Validate Valid Self-Issued inhibitPolicyMapping Test7 EE using
    # the default settings or open and verify Signed Test Message 6.2.2.114
    # using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set. If
    # the initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.11.8': [ # Invalid Self-Issued inhibitPolicyMapping Test8
    # Procedure: Validate Invalid Self-Issued inhibitPolicyMapping Test8 EE
    # using the default settings or open and verify Signed Test Message
    # 6.2.2.115 using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set. The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.9': [ # Invalid Self-Issued inhibitPolicyMapping Test9
    # Procedure: Validate Invalid Self-Issued inhibitPolicyMapping Test9 EE
    # using the default settings or open and verify Signed Test Message
    # 6.2.2.116 using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set. The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.10': [ # Invalid Self-Issued inhibitPolicyMapping Test10
    # Procedure: Validate Invalid Self-Issued inhibitPolicyMapping Test10 EE
    # using the default settings or open and verify Signed Test Message
    # 6.2.2.117 using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set. The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.11.11': [ # Invalid Self-Issued inhibitPolicyMapping Test11
    # Procedure: Validate Invalid Self-Issued inhibitPolicyMapping Test11 EE
    # using the default settings or open and verify Signed Test Message
    # 6.2.2.118 using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set. The path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.1': [ # Invalid inhibitAnyPolicy Test1
    # Procedure: Validate Invalid inhibitAnyPolicy Test1 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.119 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.2': [ # Valid inhibitAnyPolicy Test2
    # Procedure: Validate Valid inhibitAnyPolicy Test2 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.120 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1} and the path should validate successfully. If not,
    # then the user-constrained-policy-set will be empty. If the
    # user-constrained-policy-set is empty and the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.12.3': [ # inhibitAnyPolicy Test3
     # 1. default settings. The path should validate successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),

     # 2. default settings, but with initial-inhibit-any-policy set. The path
     # should not validate successfully.
    TestInfo(False, initial_inhibit_any_policy=True,
             user_constrained_policy_set=[]),
  ],

  '4.12.4': [ # Invalid inhibitAnyPolicy Test4
    # Procedure: Validate Invalid inhibitAnyPolicy Test4 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.122 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.5': [ # Invalid inhibitAnyPolicy Test5
    # Procedure: Validate Invalid inhibitAnyPolicy Test5 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.123 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.6': [ # Invalid inhibitAnyPolicy Test6
    # Procedure: Validate Invalid inhibitAnyPolicy Test6 EE using the default
    # settings or open and verify Signed Test Message 6.2.2.124 using the
    # default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.7': [ # Valid Self-Issued inhibitAnyPolicy Test7
    # Procedure: Validate Valid Self-Issued inhibitAnyPolicy Test7 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.125 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1} and the path should validate successfully. If not,
    # then the user-constrained-policy-set will be empty. If the
    # user-constrained-policy-set is empty and the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.12.8': [ # Invalid Self-Issued inhibitAnyPolicy Test8
    # Procedure: Validate Invalid Self-Issued inhibitAnyPolicy Test8 EE using
    # the default settings or open and verify Signed Test Message 6.2.2.126
    # using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],

  '4.12.9': [ # Valid Self-Issued inhibitAnyPolicy Test9
    # Procedure: Validate Valid Self-Issued inhibitAnyPolicy Test9 EE using the
    # default settings or open and verify Signed Test Message 6.2.2.127 using
    # the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set will be
    # {NIST-test-policy-1} and the explicit-policy-indicator will be set (if
    # the application can process the policyConstraints extension). If the
    # initial-policy-set is any-policy or otherwise includes
    # NIST-test-policy-1, then the user-constrained-policy-set will be
    # {NIST-test-policy-1} and the path should validate successfully. If not,
    # then the user-constrained-policy-set will be empty. If the
    # user-constrained-policy-set is empty and the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(True, user_constrained_policy_set=[TEST_POLICY_1]),
  ],

  '4.12.10': [ # Invalid Self-Issued inhibitAnyPolicy Test10
    # Procedure: Validate Invalid Self-Issued inhibitAnyPolicy Test10 EE using
    # the default settings or open and verify Signed Test Message 6.2.2.128
    # using the default settings.
    #
    # Expected Result: The authorities-constrained-policy-set and
    # user-constrained-policy-set will be empty and the
    # explicit-policy-indicator will be set (if the application can process the
    # policyConstraints extension). If the application can process the
    # policyConstraints extension, then the path should not validate
    # successfully.
    TestInfo(False, user_constrained_policy_set=[]),
  ],
}


def parse_test(lines, i, test_case_name, test_number, test_name,
               sanitized_test_names, output):
  # Start by doing a coarse level of parsing that separates out the lines for
  # the main sections.
  i, test_sections = parse_main_test_sections(lines, i)

  certs, crls = parse_cert_path_lines(test_sections.cert_path_lines)

  # Most tests have a formulaic specification: they use the default
  # settings, and have one expectation. These are easily parsed and are handled
  # programmatically. In contrast, many of the policies tests have a more
  # complicated specification which involves multiple subtests having various
  # settings, as well as expectations described in terms of supported
  # extensions. Rather than try to handle all the nuanced language, these are
  # handled manually via "overrides".
  overrides = TEST_OVERRIDES.get(test_number, None)

  if overrides is None:
    # Verify that the test description doesn't include numbered subparts (those
    # are not handled here).
    if CUSTOM_SETTINGS_MATCHER.match(" ".join(test_sections.description_lines)):
      sys.stderr.write('Unexpected custom settings for %s\n' % test_number)
      sys.exit(1)

    # Verify that the test is using only default settings.
    if not USING_DEFAULT_SETTINGS_MATCHER.match(
        " ".join(test_sections.procedure_lines)):
      sys.stderr.write('Unexpected procedure for %s: %s\n' %
                       (test_number, " ".join(test_section.procedure_lines)))
      sys.exit(1)

    # Check whether expected result is validation success or failure.
    result_match = TEST_RESULT_MATCHER.match(
       test_sections.expected_result_lines[0])
    if not result_match:
      sys.stderr.write('Unknown expectation for %s:\n%s\n' % (
          test_number, " ".join(test_sections.expected_result_lines)))
      sys.exit(1)
    # Initializes with default settings.
    info = TestInfo(result_match.group(1) == 'should validate')

    # Special case the 4.9 test failures (require explicit policy) to set
    # user_constrained_policy_set to empty. This is only done for the 4.9
    # tests, because the other policy tests are special cased as overrides and
    # hence set this manually on a per-test basis.
    #
    # user_constrained_policy_set enumerates the subset of the initial policy
    # set (anyPolicy in the default case) that were valid for the path. For
    # non-policy tests the expectation for user_constrained_policy_set is
    # [TEST_POLICY_1] since each policy asserts that. However for these tests,
    # the expectation is an empty user_constrained_policy_set since there was
    # no valid policy for the path (in fact, that is why the path validation is
    # expected to fail).
    if test_number.startswith('4.9.') and not info.should_validate:
      info.user_constrained_policy_set = []

    output_test(test_case_name, test_number, test_name, None, info, certs,
                crls, sanitized_test_names, output)
  else:
    # The overrides may have a series of inputs (settings) and outputs
    # (success/failure) for this test. Output each as a separate test case.
    for subpart_i in range(len(overrides)):
      info = overrides[subpart_i]
      # If the test has only 1 subpart, don't number it.
      subpart_number = subpart_i + 1 if len(overrides) > 1 else None
      output_test(test_case_name, test_number, test_name, subpart_number, info,
                  certs, crls, sanitized_test_names, output)

  return i


def main():
  pkits_pdf_path, output_path = sys.argv[1:]

  pkits_txt_file = tempfile.NamedTemporaryFile()

  subprocess.check_call(['pdftotext', '-layout', '-nopgbrk', '-eol', 'unix',
                         pkits_pdf_path, pkits_txt_file.name])

  test_descriptions = pkits_txt_file.read().decode('utf-8')

  # Extract section 4 of the text, which is the part that contains the tests.
  test_descriptions = test_descriptions.split(
      '4 Certification Path Validation Tests')[-1]
  test_descriptions = test_descriptions.split(
      '5 Relationship to Previous Test Suite', 1)[0]

  output = open(output_path, 'w')
  output.write('// Autogenerated by %s, do not edit\n\n' % sys.argv[0])
  output.write("""
// This file intentionally does not have header guards, it's intended to
// be inlined in another header file. The following line silences a
// presubmit warning that would otherwise be triggered by this:
// no-include-guard-because-multiply-included
// NOLINT(build/header_guard)\n\n""")
  output.write('// Hack to allow disabling type parameterized test cases.\n'
               '// See https://github.com/google/googletest/issues/389\n')
  output.write('#define WRAPPED_TYPED_TEST_P(CaseName, TestName) '
               'TYPED_TEST_P(CaseName, TestName)\n')
  output.write('#define WRAPPED_REGISTER_TYPED_TEST_SUITE_P(CaseName, ...) '
               'REGISTER_TYPED_TEST_SUITE_P(CaseName, __VA_ARGS__)\n\n')

  test_case_name = None
  sanitized_test_names = []

  lines = test_descriptions.splitlines()

  i = 0
  while i < len(lines):
    section_match = SECTION_MATCHER.match(lines[i])
    match = TEST_MATCHER.match(lines[i])
    i += 1

    if section_match:
      if test_case_name:
        finalize_test_case(test_case_name, sanitized_test_names, output)
        sanitized_test_names = []

      test_case_name = 'PkitsTest%02d%s' % (
          int(section_match.group(1).split('.')[-1]),
          sanitize_name(section_match.group(2)))
      output.write('\ntemplate <typename PkitsTestDelegate>\n')
      output.write('class %s : public PkitsTest<PkitsTestDelegate> {};\n' %
                   test_case_name)
      output.write('TYPED_TEST_SUITE_P(%s);\n' % test_case_name)

    if match:
      test_number = match.group(1)
      test_name = match.group(2)
      if not test_case_name:
        output.write('// Skipped %s %s\n' % (test_number, test_name))
        continue
      i, parse_test(lines, i, test_case_name, test_number,
                    test_name, sanitized_test_names, output)

  if test_case_name:
    finalize_test_case(test_case_name, sanitized_test_names, output)


if __name__ == '__main__':
  main()

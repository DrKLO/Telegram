#!/usr/bin/env python3
# Copyright 2023 The Chromium Authors
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
"""Certificate chain with inhibitPolicyMapping=0 on the root, and an
intermediate that uses policy mappings. Should fail if the policyConstraints on
the root are enforced."""

import sys
sys.path += ['../..']

import gencerts

# Self-signed root certificate.
root = gencerts.create_self_signed_root_certificate('Root')
root.get_extensions().set_property('policyConstraints',
                                   'critical,inhibitPolicyMapping:0')

# Intermediate certificate.
intermediate = gencerts.create_intermediate_certificate('Intermediate', root)
intermediate.get_extensions().set_property('policyConstraints',
                                           'critical,requireExplicitPolicy:0')

intermediate.get_extensions().set_property('certificatePolicies',
                                           'critical,1.2.3.4')

intermediate.get_extensions().set_property('policyMappings',
                                           'critical,@policy_mappings')
policy_mappings = intermediate.config.get_section('policy_mappings')
policy_mappings.set_property('1.2.3.4', '1.2.3.5')

# Target certificate.
target = gencerts.create_end_entity_certificate('Target', intermediate)
target.get_extensions().set_property('certificatePolicies', 'critical,1.2.3.5')

chain = [target, intermediate, root]
gencerts.write_chain(__doc__, chain, 'chain.pem')

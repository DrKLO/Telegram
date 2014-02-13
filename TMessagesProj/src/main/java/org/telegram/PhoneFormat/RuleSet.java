// Copyright (c) 2012, Rick Maddy
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// * Redistributions of source code must retain the above copyright notice, this
//   list of conditions and the following disclaimer.
//
// * Redistributions in binary form must reproduce the above copyright notice,
//   this list of conditions and the following disclaimer in the documentation
//   and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
// OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.telegram.PhoneFormat;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleSet {
    public int matchLen;
    public ArrayList<PhoneRule> rules;
    public boolean hasRuleWithIntlPrefix;
    public boolean hasRuleWithTrunkPrefix;
    public static Pattern pattern = Pattern.compile("[0-9]+");

    String format(String str, String intlPrefix, String trunkPrefix, boolean prefixRequired) {
        if (str.length() >= matchLen) {
            String begin = str.substring(0, matchLen);

            int val = 0;
            Matcher matcher = pattern.matcher(begin);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Integer.parseInt(num);
            }

            for (PhoneRule rule : rules) {
                if (val >= rule.minVal && val <= rule.maxVal && str.length() <= rule.maxLen) {
                    if (prefixRequired) {
                        if (((rule.flag12 & 0x03) == 0 && trunkPrefix == null && intlPrefix == null) || (trunkPrefix != null && (rule.flag12 & 0x01) != 0) || (intlPrefix != null && (rule.flag12 & 0x02) != 0)) {
                            return rule.format(str, intlPrefix, trunkPrefix);
                        }
                    } else {
                        if ((trunkPrefix == null && intlPrefix == null) || (trunkPrefix != null && (rule.flag12 & 0x01) != 0) || (intlPrefix != null && (rule.flag12 & 0x02) != 0)) {
                            return rule.format(str, intlPrefix, trunkPrefix);
                        }
                    }
                }
            }

            if (!prefixRequired) {
                if (intlPrefix != null) {
                    for (PhoneRule rule : rules) {
                        if (val >= rule.minVal && val <= rule.maxVal && str.length() <= rule.maxLen) {
                            if (trunkPrefix == null || (rule.flag12 & 0x01) != 0) {
                                return rule.format(str, intlPrefix, trunkPrefix);
                            }
                        }
                    }
                } else if (trunkPrefix != null) {
                    for (PhoneRule rule : rules) {
                        if (val >= rule.minVal && val <= rule.maxVal && str.length() <= rule.maxLen) {
                            if (intlPrefix == null || (rule.flag12 & 0x02) != 0) {
                                return rule.format(str, intlPrefix, trunkPrefix);
                            }
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    boolean isValid(String str, String intlPrefix, String trunkPrefix, boolean prefixRequired) {
        if (str.length() >= matchLen) {
            String begin = str.substring(0, matchLen);
            int val = 0;
            Matcher matcher = pattern.matcher(begin);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Integer.parseInt(num);
            }

            for (PhoneRule rule : rules) {
                if (val >= rule.minVal && val <= rule.maxVal && str.length() == rule.maxLen) {
                    if (prefixRequired) {
                        if (((rule.flag12 & 0x03) == 0 && trunkPrefix == null && intlPrefix == null) || (trunkPrefix != null && (rule.flag12 & 0x01) != 0) || (intlPrefix != null && (rule.flag12 & 0x02) != 0)) {
                            return true;
                        }
                    } else {
                        if ((trunkPrefix == null && intlPrefix == null) || (trunkPrefix != null && (rule.flag12 & 0x01) != 0) || (intlPrefix != null && (rule.flag12 & 0x02) != 0)) {
                            return true;
                        }
                    }
                }
            }

            if (!prefixRequired) {
                if (intlPrefix != null && !hasRuleWithIntlPrefix) {
                    for (PhoneRule rule : rules) {
                        if (val >= rule.minVal && val <= rule.maxVal && str.length() == rule.maxLen) {
                            if (trunkPrefix == null || (rule.flag12 & 0x01) != 0) {
                                return true;
                            }
                        }
                    }
                } else if (trunkPrefix != null && !hasRuleWithTrunkPrefix) {
                    for (PhoneRule rule : rules) {
                        if (val >= rule.minVal && val <= rule.maxVal && str.length() == rule.maxLen) {
                            if (intlPrefix == null || (rule.flag12 & 0x02) != 0) {
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }
}

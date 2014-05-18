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

public class CallingCodeInfo {
    public ArrayList<String> countries = new ArrayList<String>();
    public String callingCode = "";
    public ArrayList<String> trunkPrefixes = new ArrayList<String>();
    public ArrayList<String> intlPrefixes = new ArrayList<String>();
    public ArrayList<RuleSet> ruleSets = new ArrayList<RuleSet>();
    //public ArrayList formatStrings;

    String matchingAccessCode(String str) {
        for (String code : intlPrefixes) {
            if (str.startsWith(code)) {
                return code;
            }
        }
        return null;
    }

    String matchingTrunkCode(String str) {
        for (String code : trunkPrefixes) {
            if (str.startsWith(code)) {
                return code;
            }
        }

        return null;
    }

    String format(String orig) {
        String str = orig;
        String trunkPrefix = null;
        String intlPrefix = null;
        if (str.startsWith(callingCode)) {
            intlPrefix = callingCode;
            str = str.substring(intlPrefix.length());
        } else {
            String trunk = matchingTrunkCode(str);
            if (trunk != null) {
                trunkPrefix = trunk;
                str = str.substring(trunkPrefix.length());
            }
        }

        for (RuleSet set : ruleSets) {
            String phone = set.format(str, intlPrefix, trunkPrefix, true);
            if (phone != null) {
                return phone;
            }
        }

        for (RuleSet set : ruleSets) {
            String phone = set.format(str, intlPrefix, trunkPrefix, false);
            if (phone != null) {
                return phone;
            }
        }

        if (intlPrefix != null && str.length() != 0) {
            return String.format("%s %s", intlPrefix, str);
        }

        return orig;
    }

    boolean isValidPhoneNumber(String orig) {
        String str = orig;
        String trunkPrefix = null;
        String intlPrefix = null;
        if (str.startsWith(callingCode)) {
            intlPrefix = callingCode;
            str = str.substring(intlPrefix.length());
        } else {
            String trunk = matchingTrunkCode(str);
            if (trunk != null) {
                trunkPrefix = trunk;
                str = str.substring(trunkPrefix.length());
            }
        }

        for (RuleSet set : ruleSets) {
            boolean valid = set.isValid(str, intlPrefix, trunkPrefix, true);
            if (valid) {
                return valid;
            }
        }

        for (RuleSet set : ruleSets) {
            boolean valid = set.isValid(str, intlPrefix, trunkPrefix, false);
            if (valid) {
                return valid;
            }
        }

        return false;
    }
}

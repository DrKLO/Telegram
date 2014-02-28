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

import org.telegram.ui.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class PhoneFormat {
    public byte[] data;
    private boolean initialzed = false;
    public ByteBuffer buffer;
    public String defaultCountry;
    public String defaultCallingCode;
    public HashMap<String, Integer> callingCodeOffsets;
    public HashMap<String, ArrayList<String>> callingCodeCountries;
    public HashMap<String, CallingCodeInfo> callingCodeData;
    public HashMap<String, String> countryCallingCode;

    public static PhoneFormat Instance = new PhoneFormat();

    public static String strip(String str) {
        StringBuilder res = new StringBuilder(str);
        String phoneChars = "0123456789+*#";
        for (int i = res.length() - 1; i >= 0; i--) {
            if (!phoneChars.contains(res.substring(i, i + 1))) {
                res.deleteCharAt(i);
            }
        }
        return res.toString();
    }

    public static String stripExceptNumbers(String str, boolean includePlus) {
        StringBuilder res = new StringBuilder(str);
        String phoneChars = "0123456789";
        if (includePlus) {
            phoneChars += "+";
        }
        for (int i = res.length() - 1; i >= 0; i--) {
            if (!phoneChars.contains(res.substring(i, i + 1))) {
                res.deleteCharAt(i);
            }
        }
        return res.toString();
    }

    public static String stripExceptNumbers(String str) {
        return stripExceptNumbers(str, false);
    }

    public PhoneFormat() {
        init(null);
    }

    public PhoneFormat(String countryCode) {
        init(countryCode);
    }

    public void init(String countryCode) {
        try {
            InputStream stream = ApplicationLoader.applicationContext.getAssets().open("PhoneFormats.dat");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = stream.read(buf, 0, 1024)) != -1) {
                bos.write(buf, 0, len);
            }
            data = bos.toByteArray();
            buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (countryCode != null && countryCode.length() != 0) {
            defaultCountry = countryCode;
        } else {
            Locale loc = Locale.getDefault();
            defaultCountry = loc.getCountry().toLowerCase();
        }
        callingCodeOffsets = new HashMap<String, Integer>(255);
        callingCodeCountries = new HashMap<String, ArrayList<String>>(255);
        callingCodeData = new HashMap<String, CallingCodeInfo>(10);
        countryCallingCode = new HashMap<String, String>(255);

        parseDataHeader();
        initialzed = true;
    }

    public String defaultCallingCode() {
        return callingCodeForCountryCode(defaultCountry);
    }

    public String callingCodeForCountryCode(String countryCode) {
        return countryCallingCode.get(countryCode.toLowerCase());
    }

    public ArrayList countriesForCallingCode(String callingCode) {
        if (callingCode.startsWith("+")) {
            callingCode = callingCode.substring(1);
        }

        return callingCodeCountries.get(callingCode);
    }

    public CallingCodeInfo findCallingCodeInfo(String str) {
        CallingCodeInfo res = null;
        for (int i = 0; i < 3; i++) {
            if (i < str.length()) {
                res = callingCodeInfo(str.substring(0, i + 1));
                if (res != null) {
                    break;
                }
            } else {
                break;
            }
        }

        return res;
    }

    public String format(String orig) {
        if (!initialzed) {
            return orig;
        }
        String str = strip(orig);

        if (str.startsWith("+")) {
            String rest = str.substring(1);
            CallingCodeInfo info = findCallingCodeInfo(rest);
            if (info != null) {
                String phone = info.format(rest);
                return "+" +  phone;
            } else {
                return orig;
            }
        } else {
            CallingCodeInfo info = callingCodeInfo(defaultCallingCode);
            if (info == null) {
                return orig;
            }

            String accessCode = info.matchingAccessCode(str);
            if (accessCode != null) {
                String rest = str.substring(accessCode.length());
                String phone = rest;
                CallingCodeInfo info2 = findCallingCodeInfo(rest);
                if (info2 != null) {
                    phone = info2.format(rest);
                }

                if (phone.length() == 0) {
                    return accessCode;
                } else {
                    return String.format("%s %s", accessCode, phone);
                }
            } else {
                return info.format(str);
            }
        }
    }

    public boolean isPhoneNumberValid(String phoneNumber) {
        if (!initialzed) {
            return true;
        }
        String str = strip(phoneNumber);

        if (str.startsWith("+")) {
            String rest = str.substring(1);
            CallingCodeInfo info = findCallingCodeInfo(rest);
            return info != null && info.isValidPhoneNumber(rest);
        } else {
            CallingCodeInfo info = callingCodeInfo(defaultCallingCode);
            if (info == null) {
                return false;
            }

            String accessCode = info.matchingAccessCode(str);
            if (accessCode != null) {
                String rest = str.substring(accessCode.length());
                if (rest.length() != 0) {
                    CallingCodeInfo info2 = findCallingCodeInfo(rest);
                    return info2 != null && info2.isValidPhoneNumber(rest);
                } else {
                    return false;
                }
            } else {
                return info.isValidPhoneNumber(str);
            }
        }
    }

    int value32(int offset) {
        if (offset + 4 <= data.length) {
            buffer.position(offset);
            return buffer.getInt();
        } else {
            return 0;
        }
    }

    short value16(int offset) {
        if (offset + 2 <= data.length) {
            buffer.position(offset);
            return buffer.getShort();
        } else {
            return 0;
        }
    }

    public String valueString(int offset) {
        try {
            for (int a = offset; a < data.length; a++) {
                if (data[a] == '\0') {
                    if (offset == a - offset) {
                        return "";
                    }
                    return new String(data, offset, a - offset);
                }
            }
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public CallingCodeInfo callingCodeInfo(String callingCode) {
        CallingCodeInfo res = callingCodeData.get(callingCode);
        if (res == null) {
            Integer num = callingCodeOffsets.get(callingCode);
            if (num != null) {
                final byte[] bytes = data;
                int start = num;
                int offset = start;
                res = new CallingCodeInfo();
                res.callingCode = callingCode;
                res.countries = callingCodeCountries.get(callingCode);
                callingCodeData.put(callingCode, res);

                int block1Len = value16(offset);
                offset += 2;

                offset += 2;
                int block2Len = value16(offset);
                offset += 2;

                offset += 2;
                int setCnt = value16(offset);
                offset += 2;

                offset += 2;

                ArrayList<String> strs = new ArrayList<String>(5);
                String str;
                while ((str = valueString(offset)).length() != 0) {
                    strs.add(str);
                    offset += str.length() + 1;
                }
                res.trunkPrefixes = strs;
                offset++;

                strs = new ArrayList<String>(5);
                while ((str = valueString(offset)).length() != 0) {
                    strs.add(str);
                    offset += str.length() + 1;
                }
                res.intlPrefixes = strs;

                ArrayList<RuleSet> ruleSets = new ArrayList<RuleSet>(setCnt);
                offset = start + block1Len;
                for (int s = 0; s < setCnt; s++) {
                    RuleSet ruleSet = new RuleSet();
                    ruleSet.matchLen = value16(offset);
                    offset += 2;
                    int ruleCnt = value16(offset);
                    offset += 2;
                    ArrayList<PhoneRule> rules = new ArrayList<PhoneRule>(ruleCnt);
                    for (int r = 0; r < ruleCnt; r++) {
                        PhoneRule rule = new PhoneRule();
                        rule.minVal = value32(offset);
                        offset += 4;
                        rule.maxVal = value32(offset);
                        offset += 4;
                        rule.byte8 = (int)bytes[offset++];
                        rule.maxLen = (int)bytes[offset++];
                        rule.otherFlag = (int)bytes[offset++];
                        rule.prefixLen = (int)bytes[offset++];
                        rule.flag12 = (int)bytes[offset++];
                        rule.flag13 = (int)bytes[offset++];
                        int strOffset = value16(offset);
                        offset += 2;
                        rule.format = valueString(start + block1Len + block2Len + strOffset);

                        int openPos = rule.format.indexOf("[[");
                        if (openPos != -1) {
                            int closePos = rule.format.indexOf("]]");
                            rule.format = String.format("%s%s", rule.format.substring(0, openPos), rule.format.substring(closePos + 2));
                        }

                        rules.add(rule);

                        if (rule.hasIntlPrefix) {
                            ruleSet.hasRuleWithIntlPrefix = true;
                        }
                        if (rule.hasTrunkPrefix) {
                            ruleSet.hasRuleWithTrunkPrefix = true;
                        }
                    }
                    ruleSet.rules = rules;
                    ruleSets.add(ruleSet);
                }
                res.ruleSets = ruleSets;
            }
        }

        return res;
    }

    public void parseDataHeader() {
        int count = value32(0);
        int base = count * 12 + 4;
        int spot = 4;
        for (int i = 0; i < count; i++) {
            String callingCode = valueString(spot);
            spot += 4;
            String country = valueString(spot);
            spot += 4;
            int offset = value32(spot) + base;
            spot += 4;

            if (country.equals(defaultCountry)) {
                defaultCallingCode = callingCode;
            }

            countryCallingCode.put(country, callingCode);

            callingCodeOffsets.put(callingCode, offset);
            ArrayList<String> countries = callingCodeCountries.get(callingCode);
            if (countries == null) {
                countries = new ArrayList<String>();
                callingCodeCountries.put(callingCode, countries);
            }
            countries.add(country);
        }

        if (defaultCallingCode != null) {
            callingCodeInfo(defaultCallingCode);
        }
    }
}

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

public class PhoneRule {
    public int minVal;
    public int maxVal;
    public int byte8;
    public int maxLen;
    public int otherFlag;
    public int prefixLen;
    public int flag12;
    public int flag13;
    public String format;
    public boolean hasIntlPrefix;
    public boolean hasTrunkPrefix;

    String format(String str, String intlPrefix, String trunkPrefix) {
        boolean hadC = false;
        boolean hadN = false;
        boolean hasOpen = false;
        int spot = 0;
        StringBuilder res = new StringBuilder(20);
        for (int i = 0; i < format.length(); i++) {
            char ch = format.charAt(i);
            switch (ch) {
                case 'c':
                    hadC = true;
                    if (intlPrefix != null) {
                        res.append(intlPrefix);
                    }
                    break;
                case 'n':
                    hadN = true;
                    if (trunkPrefix != null) {
                        res.append(trunkPrefix);
                    }
                    break;
                case '#':
                    if (spot < str.length()) {
                        res.append(str.substring(spot, spot + 1));
                        spot++;
                    } else if (hasOpen) {
                        res.append(" ");
                    }
                break;
                case '(':
                    if (spot < str.length()) {
                        hasOpen = true;
                    }
                default:
                    if (!(ch == ' ' && i > 0 && ((format.charAt(i - 1) == 'n' && trunkPrefix == null) || (format.charAt(i - 1) == 'c' && intlPrefix == null)))) {
                        if (spot < str.length() || (hasOpen && ch == ')')) {
                            res.append(format.substring(i, i + 1));
                            if (ch == ')') {
                                hasOpen = false;
                            }
                        }
                    }
                break;
            }
        }
        if (intlPrefix != null && !hadC) {
            res.insert(0, String.format("%s ", intlPrefix));
        } else if (trunkPrefix != null && !hadN) {
            res.insert(0, trunkPrefix);
        }

        return res.toString();
    }

    boolean hasIntlPrefix() {
        return (flag12 & 0x02) != 0;
    }

    boolean hasTrunkPrefix() {
        return (flag12 & 0x01) != 0;
    }
}

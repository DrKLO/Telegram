/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android;

import android.text.TextUtils;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

public class NameQuery {
    private static final ArrayList<Range> NO_RANGES = new ArrayList<Range>();
    private final List<String> queryList = new ArrayList<String>();

    public NameQuery(String name) {
        if (!TextUtils.isEmpty(name)) {
            String[] names = name.split(" ");
            if (names.length < 5) {
                HashSet<String> set = new HashSet<String>();
                for (String part : names) {
                    part = normalize(part);
                    if (part.length() != 0) {
                        set.add(part);
                    }
                }
                queryList.addAll(set);
            } else {
                queryList.add(normalize(name));
            }
        }
    }

    private static String normalize(String str) {
        int length = str.length();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            char ch = str.charAt(i);
            if (Character.isLetterOrDigit(ch))
                sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }

    public List<Range> match(String str) {
        if (!TextUtils.isEmpty(str) && !queryList.isEmpty()) {
            NameMatcher nameMatcher = new NameMatcher(this, str);
            if (nameMatcher.match())
                return nameMatcher.getRanges();
        }
        return NO_RANGES;
    }

    public final class Range {
        private int begin;
        private int end;

        public Range(int begin, int end) {
            this.setBegin(begin);
            this.setEnd(end);
        }

        public int getBegin() {
            return begin;
        }

        private void setBegin(int begin) {
            this.begin = begin;
        }

        public int getEnd() {
            return end;
        }

        private void setEnd(int end) {
            this.end = end;
        }

        @Override
        public String toString() {
            return "Range{begin=" + getBegin() + ",end=" + getEnd() + '}';
        }
    }

    private final class NameMatcher {
        private final NameQuery nameQuery;
        private final String source;
        private final int sourceLength;
        private String target;
        private Range lastRange;
        private final List<Range> rangeList = new ArrayList<Range>();

        public NameMatcher(NameQuery nameQuery, String source) {
            this.nameQuery = nameQuery;
            this.source = source;
            this.sourceLength = source.length();
        }

        public final List<Range> getRanges() {
            return rangeList;
        }

        public void addRange(final Range range) {
            for (int i = 0; i < rangeList.size(); ++i) {
                final Range current = rangeList.get(i);
                int ax = range.getBegin(), ay = range.getEnd();
                int bx = current.getBegin(), by = current.getEnd();
                if (ax >= bx && ay <= by)
                    return;
                if (ay < bx) {
                    rangeList.add(i, range);
                    return;
                }
                if (ax <= by) {
                    if (ay > by) {
                        current.setEnd(ay);
                        if (rangeList.size() > ++i && rangeList.get(i).getBegin() == ay) {
                            current.setEnd(rangeList.get(i).getEnd());
                            rangeList.remove(i);
                        }
                        return;
                    }
                    if (ax < bx) {
                        current.setBegin(ax);
                        return;
                    }
                }
            }
            rangeList.add(range);
        }

        public boolean match() {
            boolean[] resultMap = new boolean[nameQuery.queryList.size()];
            for (int i = 0; i < resultMap.length; ++i) {
                target = nameQuery.queryList.get(i);
                while (next()) {
                    addRange(lastRange);
                    resultMap[i] = true;
                }
            }
            for (boolean b : resultMap) {
                if (!b)
                    return false;
            }
            return true;
        }

        public boolean next() {
            return next(lastRange == null ? 0 : lastRange.getEnd());
        }

        public boolean next(int offset) {
            lastRange = null;
            for (int begin = offset; begin < sourceLength; ++begin) {
                int targetPos = 0;
                do {
                    if (isEqual(Character.toLowerCase(source.charAt(begin)), target.charAt(0)))
                        targetPos = 1;
                } while (targetPos == 0 && ++begin < sourceLength);

                int end = begin + 1;
                for (; targetPos < target.length() && end < sourceLength; ++end) {
                    char ch = source.charAt(end);
                    if (!Character.isLetterOrDigit(ch))
                        continue;
                    if (!isEqual(Character.toLowerCase(ch), target.charAt(targetPos)))
                        break;
                    ++targetPos;
                }

                if (targetPos == target.length()) {
                    lastRange = new Range(begin, end);
                    return true;
                }
            }
            return false;
        }

        public boolean isEqual(char a, char b) {
            if (Utilities.isKoreanCharacter(a) && Utilities.isKoreanChosung(b)) {
                return Utilities.extractKoreanChosung(a) == b;
            }
            return a == b;
        }
    }
}

package org.telegram.ui.Components;

import android.text.InputFilter;
import android.text.Spanned;

public class CodepointsLengthInputFilter implements InputFilter {
    private final int mMax;

    public CodepointsLengthInputFilter(int max) {
        mMax = max;
    }

    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                               int dstart, int dend) {
        int destAfter = Character.codePointCount(dest, 0, dest.length()) -  Character.codePointCount(dest, dstart, dend);
        int keep = mMax - destAfter;
        if (keep <= 0) {
            return "";
        } else if (keep >= Character.codePointCount(source, start, end)) {
            return null; // keep original
        } else {
            keep += start;
            if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                --keep;
                if (keep == start) {
                    return "";
                }
            }
            return source.subSequence(start, keep);
        }
    }

    public int getMax() {
        return mMax;
    }
}

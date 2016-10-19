/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.text.SpannableString;

import org.telegram.messenger.FileLog;

import java.lang.reflect.Field;

public class SpannableStringLight extends SpannableString {

    private static Field mSpansField;
    private static Field mSpanDataField;
    private static Field mSpanCountField;
    private static boolean fieldsAvailable;

    private Object[] mSpansOverride;
    private int[] mSpanDataOverride;
    private int mSpanCountOverride;
    private int num;

    public SpannableStringLight(CharSequence source) {
        super(source);

        try {
            mSpansOverride = (Object[]) mSpansField.get(this);
            mSpanDataOverride = (int[]) mSpanDataField.get(this);
            mSpanCountOverride = (int) mSpanCountField.get(this);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public void setSpansCount(int count) {
        count += mSpanCountOverride;
        mSpansOverride = new Object[count];
        mSpanDataOverride = new int[count * 3];
        num = mSpanCountOverride;
        mSpanCountOverride = count;

        try {
            mSpansField.set(this, mSpansOverride);
            mSpanDataField.set(this, mSpanDataOverride);
            mSpanCountField.set(this, mSpanCountOverride);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }

    public static boolean isFieldsAvailable() {
        if (!fieldsAvailable && mSpansField == null) {
            try {
                mSpansField = SpannableString.class.getSuperclass().getDeclaredField("mSpans");
                mSpansField.setAccessible(true);

                mSpanDataField = SpannableString.class.getSuperclass().getDeclaredField("mSpanData");
                mSpanDataField.setAccessible(true);

                mSpanCountField = SpannableString.class.getSuperclass().getDeclaredField("mSpanCount");
                mSpanCountField.setAccessible(true);
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
            fieldsAvailable = true;
        }
        return mSpansField != null;
    }

    public void setSpanLight(Object what, int start, int end, int flags) {
        mSpansOverride[num] = what;
        mSpanDataOverride[num * 3] = start;
        mSpanDataOverride[num * 3 + 1] = end;
        mSpanDataOverride[num * 3 + 2] = flags;
        num++;
    }

    @Override
    public void removeSpan(Object what) {
        super.removeSpan(what);
    }
}

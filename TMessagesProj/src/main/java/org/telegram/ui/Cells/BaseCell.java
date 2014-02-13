/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.R;

public class BaseCell extends View {
    private CharSequence currentNameMessage;

    public BaseCell(Context context) {
        super(context);
        tryInstallAccessibilityDelegate();
    }

    public BaseCell(Context context, AttributeSet attrs) {
        super(context, attrs);
        tryInstallAccessibilityDelegate();
    }

    public BaseCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        tryInstallAccessibilityDelegate();
    }

    protected void setDrawableBounds(Drawable drawable, int x, int y) {
        setDrawableBounds(drawable, x, y, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    protected void setDrawableBounds(Drawable drawable, int x, int y, int w, int h) {
        drawable.setBounds(x, y, x + w, y + h);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // We called the super implementation to let super classes set
        // appropriate info properties. Then we add our properties
        // (checkable and checked) which are not supported by a super class.
        // Very often you will need to add only the text on the custom view.
        CharSequence text = getTextAccessibility();
        if (!TextUtils.isEmpty(text)) {
            info.setText(text);
        }
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        // We called the super implementation to populate its text to the
        // event. Then we add our text not present in a super class.
        // Very often you will need to add only the text on the custom view.
        CharSequence text = getTextAccessibility();
        if (!TextUtils.isEmpty(text)) {
            event.getText().add(text);
        }
    }

    public void tryInstallAccessibilityDelegate() {
        if (Build.VERSION.SDK_INT < 14) {
            return;
        }

        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host,info);
                // We called the super implementation to let super classes set
                // appropriate info properties. Then we add our properties
                // (checkable and checked) which are not supported by a super class.
                // Very often you will need to add only the text on the custom view.
                CharSequence text = getTextAccessibility();
                if (!TextUtils.isEmpty(text)) {
                    info.setText(text);
                }
            }

            @Override
            public void onPopulateAccessibilityEvent(View host,AccessibilityEvent event) {
                super.onPopulateAccessibilityEvent(host,event);
                // We called the super implementation to populate its text to the
                // event. Then we add our text not present in a super class.
                // Very often you will need to add only the text on the custom view.
                CharSequence text = getTextAccessibility();
                if (!TextUtils.isEmpty(text)) {
                    event.getText().add(text);
                }
            }
        });
    }

    public CharSequence getTextAccessibility() {
        if (!TextUtils.isEmpty(currentNameMessage)) {
            return currentNameMessage;
        }else{
            return  getResources().getString(R.string.ContactUnAllocated);
        }
    }

    public void setTextAccessibility(CharSequence text){

        currentNameMessage = text;
    }

}

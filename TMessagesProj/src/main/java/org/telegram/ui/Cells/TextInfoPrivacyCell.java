/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.util.ArrayList;

public class TextInfoPrivacyCell extends FrameLayout {

    private TextView textView;
    private LinkSpanDrawable.LinkCollector links;
    private int linkTextColorKey = Theme.key_windowBackgroundWhiteLinkText;
    private int topPadding = 10;
    private int bottomPadding = 17;
    private int fixedSize;
    private boolean isRTL;

    private CharSequence text;
    private final Theme.ResourcesProvider resourcesProvider;

    public TextInfoPrivacyCell(Context context) {
        this(context, 21, null);
    }

    public TextInfoPrivacyCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 21, resourcesProvider);
    }

    public TextInfoPrivacyCell(Context context, int padding) {
        this(context, padding, null);
    }

    public TextInfoPrivacyCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new LinkSpanDrawable.LinksTextView(context, links = new LinkSpanDrawable.LinkCollector(this), resourcesProvider) {
            @Override
            protected void onDraw(Canvas canvas) {
                onTextDraw();
                super.onDraw(canvas);
                afterTextDraw();
            }
        };
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(17));
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText4));
        textView.setLinkTextColor(getThemedColor(linkTextColorKey));
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        isRTL = LocaleController.isRTL;

        setWillNotDraw(false);
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) {
            return;
        }
        isRTL = LocaleController.isRTL;

        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        layoutParams.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        textView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (links != null) {
            canvas.save();
            canvas.translate(textView.getLeft(), textView.getTop());
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();
        }
        super.onDraw(canvas);
    }

    protected void onTextDraw() {

    }

    protected void afterTextDraw() {

    }

    public void setLinkTextColorKey(int key) {
        linkTextColorKey = key;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (fixedSize != 0) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(fixedSize), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    public void setTopPadding(int topPadding) {
        this.topPadding = topPadding;
    }

    public void setBottomPadding(int value) {
        bottomPadding = value;
    }

    public void setFixedSize(int size) {
        fixedSize = size;
    }

    public CharSequence getText() {
        return textView.getText();
    }

    public void setText(CharSequence text) {
        if (!TextUtils.equals(text, this.text)) {
            this.text = text;
            if (text == null) {
                textView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
            } else {
                textView.setPadding(0, AndroidUtilities.dp(topPadding), 0, AndroidUtilities.dp(bottomPadding));
            }
            SpannableString spannableString = null;
            if (text != null) {
                for (int i = 0, len = text.length(); i < len - 1; i++) {
                    if (text.charAt(i) == '\n' && text.charAt(i + 1) == '\n') {
                        if (spannableString == null) {
                            spannableString = new SpannableString(text);
                        }
                        spannableString.setSpan(new AbsoluteSizeSpan(10, true), i + 1, i + 2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            textView.setText(spannableString != null ? spannableString : text);
        }
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextColorByKey(int key) {
        textView.setTextColor(getThemedColor(key));
        textView.setTag(key);
    }

    public TextView getTextView() {
        return textView;
    }

    public int length() {
        return textView.length();
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, View.ALPHA, value ? 1.0f : 0.5f));
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
        }
    }

    public void setTextGravity(int gravity) {
        textView.setGravity(gravity);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(TextView.class.getName());
        info.setText(text);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

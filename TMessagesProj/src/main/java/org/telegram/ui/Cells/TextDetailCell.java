/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class TextDetailCell extends FrameLayout {

    private final TextView textView;
    private final LinkSpanDrawable.LinksTextView valueTextView;
    private final TextView showMoreTextView = null;
    private final ImageView imageView;
    private boolean needDivider;
    private boolean contentDescriptionValueFirst;
    private boolean multiline;

    private Theme.ResourcesProvider resourcesProvider;

    public TextDetailCell(Context context) {
        this(context, null);
    }

    public TextDetailCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public TextDetailCell(Context context, Theme.ResourcesProvider resourcesProvider, boolean multiline) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 23, 8, 23, 0));

        valueTextView = new LinkSpanDrawable.LinksTextView(context);
        valueTextView.setOnLinkLongPressListener(span -> {
            if (span != null) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {};
                span.onClick(valueTextView);
            }
        });
        if (this.multiline = multiline) {
            setMinimumHeight(AndroidUtilities.dp(60));
        } else {
            valueTextView.setLines(1);
            valueTextView.setSingleLine(true);
        }
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 23, 33, 23, 10));

        imageView = new ImageView(context);
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrameRelatively(48, 48, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean hit = valueTextView.hit((int) ev.getX() - valueTextView.getLeft(), (int) ev.getY() - valueTextView.getTop()) != null;
        if (hit) {
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            multiline ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
        );
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setImage(Drawable drawable) {
        setImage(drawable, null);
    }

    public void setImage(Drawable drawable, CharSequence imageContentDescription) {
        ((MarginLayoutParams) valueTextView.getLayoutParams()).rightMargin = !LocaleController.isRTL && drawable != null ? AndroidUtilities.dp(28 + 12 + 12 + 6) : AndroidUtilities.dp(23);
        imageView.setImageDrawable(drawable);
        imageView.setFocusable(drawable != null);
        imageView.setContentDescription(imageContentDescription);
        if (drawable == null) {
            imageView.setBackground(null);
            imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        } else {
            imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(48), Color.TRANSPARENT, Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        int margin = AndroidUtilities.dp(23) + (drawable == null ? 0 : AndroidUtilities.dp(48));
        if (LocaleController.isRTL) {
            ((MarginLayoutParams) textView.getLayoutParams()).leftMargin = margin;
        } else {
            ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = margin;
        }
        textView.requestLayout();
    }

    public void setImageClickListener(View.OnClickListener clickListener) {
        imageView.setOnClickListener(clickListener);
        if (clickListener == null) {
            imageView.setClickable(false);
        }
    }

    public void setTextWithEmojiAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setContentDescriptionValueFirst(boolean contentDescriptionValueFirst) {
        this.contentDescriptionValueFirst = contentDescriptionValueFirst;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        textView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(
                LocaleController.isRTL ? 0 : AndroidUtilities.dp(20),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0),
                getMeasuredHeight() - 1,
                Theme.dividerPaint
            );
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final CharSequence text = textView.getText();
        final CharSequence valueText = valueTextView.getText();
        if (!TextUtils.isEmpty(text) && !TextUtils.isEmpty(valueText)) {
            info.setText((contentDescriptionValueFirst ? valueText : text) + ": " + (contentDescriptionValueFirst ? text : valueText));
        }
    }
}

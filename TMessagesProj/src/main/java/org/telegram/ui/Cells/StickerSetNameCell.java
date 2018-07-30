/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;

public class StickerSetNameCell extends FrameLayout {

    private TextView textView;
    private TextView urlTextView;
    private ImageView buttonView;
    private boolean empty;

    public StickerSetNameCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setSingleLine(true);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 17, 4, 57, 0));

        urlTextView = new TextView(context);
        urlTextView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName));
        urlTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        urlTextView.setEllipsize(TextUtils.TruncateAt.END);
        urlTextView.setSingleLine(true);
        urlTextView.setVisibility(INVISIBLE);
        addView(urlTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 17, 6, 17, 0));

        buttonView = new ImageView(context);
        buttonView.setScaleType(ImageView.ScaleType.CENTER);
        buttonView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelStickerSetNameIcon), PorterDuff.Mode.MULTIPLY));
        addView(buttonView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.RIGHT, 0, 0, 16, 0));
    }

    public void setUrl(CharSequence text, int searchLength) {
        if (text != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            try {
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ColorSpanUnderline(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName)), searchLength, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            urlTextView.setText(builder);
            urlTextView.setVisibility(VISIBLE);
        } else {
            urlTextView.setVisibility(GONE);
        }
    }

    public void setText(CharSequence text, int resId) {
        setText(text, resId, 0, 0);
    }

    public void setText(CharSequence text, int resId, int index, int searchLength) {
        if (text == null) {
            empty = true;
            textView.setText("");
            buttonView.setVisibility(INVISIBLE);
        } else {
            if (searchLength != 0) {
                SpannableStringBuilder builder = new SpannableStringBuilder(text);
                try {
                    builder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + searchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception ignore) {

                }
                textView.setText(builder);
            } else {
                textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
            }
            if (resId != 0) {
                buttonView.setImageResource(resId);
                buttonView.setVisibility(VISIBLE);
            } else {
                buttonView.setVisibility(INVISIBLE);
            }
        }
    }

    public void setOnIconClickListener(OnClickListener onIconClickListener) {
        buttonView.setOnClickListener(onIconClickListener);
    }

    @Override
    public void invalidate() {
        textView.invalidate();
        super.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (empty) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
    }
}

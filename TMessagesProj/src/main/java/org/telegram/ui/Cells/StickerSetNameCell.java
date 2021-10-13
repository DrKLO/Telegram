/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

public class StickerSetNameCell extends FrameLayout {

    private TextView textView;
    private TextView urlTextView;
    private ImageView buttonView;
    private boolean empty;
    private boolean isEmoji;

    private CharSequence stickerSetName;
    private int stickerSetNameSearchIndex;
    private int stickerSetNameSearchLength;

    private CharSequence url;
    private int urlSearchLength;
    private final Theme.ResourcesProvider resourcesProvider;

    public StickerSetNameCell(Context context, boolean emoji, Theme.ResourcesProvider resourcesProvider) {
        this(context, emoji, false, resourcesProvider);
    }

    public StickerSetNameCell(Context context, boolean emoji, boolean supportRtl, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        isEmoji = emoji;

        FrameLayout.LayoutParams lp;

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setSingleLine(true);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, emoji ? 15 : 17, 2, 57, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, emoji ? 15 : 17, 2, 57, 0);
        }
        addView(textView, lp);

        urlTextView = new TextView(context);
        urlTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
        urlTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        urlTextView.setEllipsize(TextUtils.TruncateAt.END);
        urlTextView.setSingleLine(true);
        urlTextView.setVisibility(INVISIBLE);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.END, 17, 6, 17, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 17, 6, 17, 0);
        }
        addView(urlTextView, lp);

        buttonView = new ImageView(context);
        buttonView.setScaleType(ImageView.ScaleType.CENTER);
        buttonView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelStickerSetNameIcon), PorterDuff.Mode.MULTIPLY));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(24, 24, Gravity.TOP | Gravity.END, 0, 0, 16, 0);
        } else {
            lp = LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.RIGHT, 0, 0, 16, 0);
        }
        addView(buttonView, lp);
    }

    public void setUrl(CharSequence text, int searchLength) {
        url = text;
        urlSearchLength = searchLength;
        urlTextView.setVisibility(text != null ? VISIBLE : GONE);
        updateUrlSearchSpan();
    }

    private void updateUrlSearchSpan() {
        if (url != null) {
            SpannableStringBuilder builder = new SpannableStringBuilder(url);
            try {
                builder.setSpan(new ColorSpanUnderline(getThemedColor(Theme.key_chat_emojiPanelStickerSetNameHighlight)), 0, urlSearchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ColorSpanUnderline(getThemedColor(Theme.key_chat_emojiPanelStickerSetName)), urlSearchLength, url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
            }
            urlTextView.setText(builder);
        }
    }

    public void setText(CharSequence text, int resId) {
        setText(text, resId, 0, 0);
    }

    public void setTitleColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(CharSequence text, int resId, int index, int searchLength) {
        stickerSetName = text;
        stickerSetNameSearchIndex = index;
        stickerSetNameSearchLength = searchLength;
        if (text == null) {
            empty = true;
            textView.setText("");
            buttonView.setVisibility(INVISIBLE);
        } else {
            if (searchLength != 0) {
                updateTextSearchSpan();
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

    private void updateTextSearchSpan() {
        if (stickerSetName != null && stickerSetNameSearchLength != 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(stickerSetName);
            try {
                builder.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_chat_emojiPanelStickerSetNameHighlight)), stickerSetNameSearchIndex, stickerSetNameSearchIndex + stickerSetNameSearchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
            }
            textView.setText(Emoji.replaceEmoji(builder, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
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
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(isEmoji ? 28 : 24), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (child == urlTextView) {
            widthUsed += textView.getMeasuredWidth() + AndroidUtilities.dp(16);
        }
        super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
    }

    public void updateColors() {
        updateTextSearchSpan();
        updateUrlSearchSpan();
    }

    public static void createThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{StickerSetNameCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chat_emojiPanelStickerSetName));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{StickerSetNameCell.class}, new String[]{"urlTextView"}, null, null, null, Theme.key_chat_emojiPanelStickerSetName));
        descriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{StickerSetNameCell.class}, new String[]{"buttonView"}, null, null, null, Theme.key_chat_emojiPanelStickerSetNameIcon));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_emojiPanelStickerSetNameHighlight));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_chat_emojiPanelStickerSetName));
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}

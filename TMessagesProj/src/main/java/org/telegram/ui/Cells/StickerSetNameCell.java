/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ColorSpanUnderline;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.List;

public class StickerSetNameCell extends FrameLayout {
    public int position;

    private TextView textView;
    private TextView urlTextView;
    private TextView editView;
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

        LayoutParams lp;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setSingleLine(true);
        if (emoji) {
            textView.setGravity(Gravity.CENTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            }
        }
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,  Gravity.START | Gravity.TOP, emoji ? 5 : 15, 5, emoji ? 15 : 25, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, emoji ? 5 : 15, 5, emoji ? 15 : 25, 0);
        }
        addView(layout, lp);
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL));

        editView = new TextView(context);
        editView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
        editView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        editView.setTypeface(AndroidUtilities.bold());
        editView.setEllipsize(TextUtils.TruncateAt.END);
        editView.setPadding(dp(6), 0, dp(6.33f), 0);
        editView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(9),
            Theme.multAlpha(getThemedColor(Theme.key_chat_emojiPanelStickerSetName), .10f),
            Theme.multAlpha(getThemedColor(Theme.key_chat_emojiPanelStickerSetName), .24f)
        ));
        editView.setGravity(Gravity.CENTER);
        editView.setSingleLine(true);
        ScaleStateListAnimator.apply(editView);
        layout.addView(editView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 5, 1, 0, 0));
        editView.setVisibility(View.GONE);

        urlTextView = new TextView(context);
        urlTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
        urlTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        urlTextView.setEllipsize(TextUtils.TruncateAt.END);
        urlTextView.setSingleLine(true);
        urlTextView.setVisibility(INVISIBLE);
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.END, 12, 6, 17, 0);
        } else {
            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 12, 6, 17, 0);
        }
        addView(urlTextView, lp);

        buttonView = new ImageView(context);
        buttonView.setScaleType(ImageView.ScaleType.CENTER);
        buttonView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelStickerSetNameIcon), PorterDuff.Mode.MULTIPLY));
        buttonView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        if (supportRtl) {
            lp = LayoutHelper.createFrameRelatively(24, 24, Gravity.TOP | Gravity.END, 0, 0, isEmoji ? 0 : 10, 0);
        } else {
            lp = LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.RIGHT, 0, 0, isEmoji ? 0 : 10, 0);
        }
        buttonView.setTranslationY(dp(4));
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
        setText(text, resId, null, 0, 0);
    }

    public void setText(CharSequence text, int resId, CharSequence iconAccDescr) {
        setText(text, resId, iconAccDescr, 0, 0);
    }

    public void setTitleColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(CharSequence text, int resId, int index, int searchLength) {
        setText(text, resId, null, index, searchLength);
    }

    public void setText(CharSequence text, int resId, CharSequence iconAccDescr, int index, int searchLength) {
        stickerSetName = text;
        stickerSetNameSearchIndex = index;
        stickerSetNameSearchLength = searchLength;
        if (text == null) {
            empty = true;
            textView.setText("");
            buttonView.setVisibility(INVISIBLE);
        } else {
            empty = false;
            if (searchLength != 0) {
                updateTextSearchSpan();
            } else {
                textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
            }
            if (resId != 0) {
                buttonView.setImageResource(resId);
                buttonView.setContentDescription(iconAccDescr);
                buttonView.setVisibility(VISIBLE);
            } else {
                buttonView.setVisibility(INVISIBLE);
            }
        }
        editView.setVisibility(View.GONE);
    }

    public void setEdit(View.OnClickListener whenClickedEdit) {
        editView.setVisibility(View.VISIBLE);
        editView.setText(LocaleController.getString(R.string.EditPack));
        editView.setOnClickListener(whenClickedEdit);
    }

    public void setHeaderOnClick(View.OnClickListener listener) {
        textView.setOnClickListener(listener);
    }

    private void updateTextSearchSpan() {
        if (stickerSetName != null && stickerSetNameSearchLength > 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(stickerSetName);
            try {
                builder.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_chat_emojiPanelStickerSetNameHighlight)), stickerSetNameSearchIndex, stickerSetNameSearchIndex + stickerSetNameSearchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {
            }
            textView.setText(Emoji.replaceEmoji(builder, textView.getPaint().getFontMetricsInt(), false));
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
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(27), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
        if (child == urlTextView) {
            widthUsed += textView.getMeasuredWidth() + dp(16);
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

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public TextView getTextView() {
        return textView;
    }
}

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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class StickerSetNameCell extends FrameLayout {

    private TextView textView;
    private ImageView buttonView;

    public StickerSetNameCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setSingleLine(true);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 17, 4, 57, 0));

        buttonView = new ImageView(context);
        buttonView.setScaleType(ImageView.ScaleType.CENTER);
        buttonView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelStickerSetNameIcon), PorterDuff.Mode.MULTIPLY));
        addView(buttonView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.RIGHT, 0, 0, 16, 0));
    }

    public void setText(String text, int resId) {
        textView.setText(text);
        if (resId != 0) {
            buttonView.setImageResource(resId);
            buttonView.setVisibility(VISIBLE);
        } else {
            buttonView.setVisibility(INVISIBLE);
        }
    }

    public void setOnIconClickListener(OnClickListener onIconClickListener) {
        buttonView.setOnClickListener(onIconClickListener);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
    }
}

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DialogsHintCell extends FrameLayout {
    private final LinearLayout contentView;
    private final TextView titleView;
    private final TextView messageView;
    private final ImageView chevronView;
    private final ImageView closeView;

    public DialogsHintCell(@NonNull Context context) {
        super(context);

        setWillNotDraw(false);
        setPadding(dp(16), dp(8), dp(16), dp(8));

        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(LocaleController.isRTL ? dp(24) : 0, 0, LocaleController.isRTL ? 0 : dp(24), 0);
        addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleView.setSingleLine();
        contentView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP));

        messageView = new TextView(context);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageView.setMaxLines(2);
        messageView.setEllipsize(TextUtils.TruncateAt.END);
        contentView.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.TOP));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.arrow_newchat);
        addView(chevronView, LayoutHelper.createFrame(16, 16, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.msg_close);
        closeView.setPadding(dp(6), dp(6), dp(6), dp(6));
        addView(closeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? -15 : 0, 0, LocaleController.isRTL ? 0 : -15, 0));
        closeView.setVisibility(GONE);
        setClipToPadding(false);
        updateColors();
    }

    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        chevronView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN);
        closeView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN);
        closeView.setBackground(Theme.AdaptiveRipple.filledCircle());
        setBackground(Theme.AdaptiveRipple.filledRect());
    }

    public void setText(CharSequence title, CharSequence subtitle) {
        titleView.setText(title);
        titleView.setCompoundDrawables(null, null, null, null);
        messageView.setText(subtitle);
        chevronView.setVisibility(VISIBLE);
        closeView.setVisibility(GONE);
    }

    public void setChristmasStyle(OnClickListener closeListener) {
        chevronView.setVisibility(INVISIBLE);
        closeView.setVisibility(VISIBLE);
        closeView.setOnClickListener(closeListener);
        Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable("\uD83C\uDF84");
        if (drawable != null) {
            drawable.setBounds(dp(2), -dp(2), Emoji.drawImgSize + dp(2), Emoji.drawImgSize - dp(2));
            titleView.setCompoundDrawables(null, null, drawable, null);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
    }

    private int height;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = AndroidUtilities.displaySize.x;
        }
        contentView.measure(
                MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST)
        );
        this.height = contentView.getMeasuredHeight() + getPaddingTop() + getPaddingBottom() + 1;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public int height() {
        if (getVisibility() != View.VISIBLE) {
            return 0;
        }
        if (height <= 0) {
            height = dp(72) + 1;
        }
        return height;
    }
}

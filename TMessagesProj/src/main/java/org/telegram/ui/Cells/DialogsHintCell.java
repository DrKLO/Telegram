package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BlurredFrameLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

public class DialogsHintCell extends BlurredFrameLayout {
    private final LinearLayout parentView;
    private final LinearLayout contentView;
    public final AnimatedEmojiSpan.TextViewEmojis titleView;
    private final TextView messageView;
    private final ImageView chevronView;
    private final ImageView closeView;
    private final AvatarsImageView avatarsImageView;

    public DialogsHintCell(@NonNull Context context, SizeNotifierFrameLayout parentFrameLayout) {
        super(context, parentFrameLayout);

        setWillNotDraw(false);
        setPadding(dp(9), dp(8), dp(9), dp(8));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStepFactor(46f / 81f);
        avatarsImageView.setVisibility(View.GONE);
        avatarsImageView.setCount(0);

        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(LocaleController.isRTL ? dp(24) : 0, 0, LocaleController.isRTL ? 0 : dp(24), 0);

        titleView = new AnimatedEmojiSpan.TextViewEmojis(context);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine();
        contentView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP));

        messageView = new TextView(context);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageView.setMaxLines(2);
        messageView.setEllipsize(TextUtils.TruncateAt.END);
        contentView.addView(messageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.TOP));

        NotificationCenter.getGlobalInstance().listenGlobal(this, NotificationCenter.emojiLoaded, args -> {
            if (titleView != null) {
                titleView.invalidate();
            }
            if (messageView != null) {
                messageView.invalidate();
            }
        });

        parentView = new LinearLayout(context);
        parentView.setOrientation(LinearLayout.HORIZONTAL);
        if (LocaleController.isRTL) {
            parentView.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 7, 0, 7, 0));
            parentView.addView(avatarsImageView, LayoutHelper.createFrame(0, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
        } else {
            parentView.addView(avatarsImageView, LayoutHelper.createFrame(0, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 0, 0, 2, 0));
            parentView.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 7, 0, 7, 0));
        }
        addView(parentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.arrow_newchat);
        addView(chevronView, LayoutHelper.createFrame(16, 16, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));

        closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.msg_close);
        closeView.setPadding(dp(6), dp(6), dp(6), dp(6));
        addView(closeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? -15 + 7 : 0, 0, LocaleController.isRTL ? 0 : -15 + 7, 0));
        closeView.setVisibility(GONE);
        setClipToPadding(false);
        updateColors();
    }

    public void setCompact(boolean compact) {
        setPadding(dp(9), dp(compact ? 4 : 8), dp(9), dp(8));
    }

    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        chevronView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN);
        closeView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN);
        closeView.setBackground(Theme.AdaptiveRipple.filledCircle());
        setBackground(Theme.AdaptiveRipple.filledRect());
    }

    public void setAvatars(int currentAccount, ArrayList<TLRPC.User> users) {
        final int count = Math.min(3, users == null ? 0 : users.size());
        final boolean updated = count != avatarsImageView.avatarsDrawable.count;
        if (count <= 1) {
            avatarsImageView.setAvatarsTextSize(dp(20));
            avatarsImageView.setSize(dp(32));
        } else {
            avatarsImageView.setAvatarsTextSize(dp(18));
            avatarsImageView.setSize(dp(27));
        }
        avatarsImageView.setCount(count);
        avatarsImageView.setVisibility(count <= 0 ? View.GONE : View.VISIBLE);
        avatarsImageView.getLayoutParams().width = count <= 1 ? dp(32) : dp(27 + 16 * (count - 1));
        if (updated) parentView.requestLayout();
        if (users != null) {
            for (int i = 0; i < 3; ++i) {
                avatarsImageView.setObject(i, currentAccount, i >= users.size() ? null : users.get(i));
            }
        }
        avatarsImageView.commitTransition(false);
    }

    public void setText(CharSequence title, CharSequence subtitle) {
        titleView.setText(title);
        titleView.setCompoundDrawables(null, null, null, null);
        messageView.setText(subtitle);
        chevronView.setVisibility(VISIBLE);
        closeView.setVisibility(GONE);
    }

    public void setOnCloseListener(OnClickListener closeListener) {
        chevronView.setVisibility(INVISIBLE);
        closeView.setVisibility(VISIBLE);
        closeView.setOnClickListener(closeListener);
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(v -> {
            if (getAlpha() > .5f && l != null)
                l.onClick(v);
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getAlpha() < .5f) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawRect(0, getHeight() - 1, getWidth(), getHeight(), Theme.dividerPaint);
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

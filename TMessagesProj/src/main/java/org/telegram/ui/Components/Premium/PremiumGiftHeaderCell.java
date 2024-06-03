package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class PremiumGiftHeaderCell extends LinearLayout {
    private TLRPC.User user;

    private StarParticlesView.Drawable drawable;

    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;
    private TextView titleView;
    private TextView subtitleView;

    public PremiumGiftHeaderCell(@NonNull Context context) {
        super(context);

        setOrientation(VERTICAL);

        avatarDrawable = new AvatarDrawable();
        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(50));
        addView(avatarImageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 24, 24, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 8, 24, 28));

        setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        drawable = new StarParticlesView.Drawable(50);
        drawable.useGradient = true;
        drawable.roundEffect = true;

        drawable.init();
        setWillNotDraw(false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float cx = avatarImageView.getX() + avatarImageView.getWidth() / 2f;
        float cy = avatarImageView.getPaddingTop() + avatarImageView.getY() + avatarImageView.getHeight() / 2f - AndroidUtilities.dp(3);
        int outerPadding = AndroidUtilities.dp(32);
        drawable.rect.set(
                cx - outerPadding, cy - outerPadding,
                cx + outerPadding, cy + outerPadding
        );
        if (changed) {
            drawable.resetPositions();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawable.onDraw(canvas);
        invalidate();
    }

    public void bind(TLRPC.User user) {
        this.user = user;

        avatarDrawable.setInfo(user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);

        titleView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.GiftTelegramPremiumTitle)));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.GiftTelegramPremiumDescription, user.first_name)));
    }
}

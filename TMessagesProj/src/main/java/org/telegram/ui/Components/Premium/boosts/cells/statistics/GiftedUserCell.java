package org.telegram.ui.Components.Premium.boosts.cells.statistics;

import static org.telegram.tgnet.tl.TL_stories.Boost.NO_USER_ID;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Date;

@SuppressLint("ViewConstructor")
public class GiftedUserCell extends UserCell {

    private TextView badgeTextView;
    private FrameLayout badgeLayout;
    private Drawable giveawayDrawable;
    private Drawable giftDrawable;
    private TL_stories.Boost boost;
    private CounterDrawable counterDrawable;

    public GiftedUserCell(Context context, int padding, int checkbox, boolean admin) {
        super(context, padding, checkbox, admin);
        init();
    }

    public GiftedUserCell(Context context, int padding, int checkbox, boolean admin, Theme.ResourcesProvider resourcesProvider) {
        super(context, padding, checkbox, admin, resourcesProvider);
        init();
    }

    public GiftedUserCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton) {
        super(context, padding, checkbox, admin, needAddButton);
        init();
    }

    public GiftedUserCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton, Theme.ResourcesProvider resourcesProvider) {
        super(context, padding, checkbox, admin, needAddButton, resourcesProvider);
        init();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public TL_stories.Boost getBoost() {
        return boost;
    }

    private void init() {
        counterDrawable = new CounterDrawable(getContext());
        badgeLayout = new FrameLayout(getContext());
        badgeTextView = new TextView(getContext());
        badgeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        badgeTextView.setTypeface(AndroidUtilities.bold());
        badgeTextView.setTextSize(12);
        badgeTextView.setGravity(Gravity.CENTER);
        badgeLayout.addView(badgeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 22));
        badgeLayout.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        addView(badgeLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, (LocaleController.isRTL ? 9 : 0), 9, (LocaleController.isRTL ? 0 : 9), 0));
    }

    private void setAvatarColorByMonths(int months) {
        if (months == 12) {
            avatarDrawable.setColor(0xFFff8560, 0xFFd55246);
        } else if (months == 6) {
            avatarDrawable.setColor(0xFF5caefa, 0xFF418bd0);
        } else {
            avatarDrawable.setColor(0xFF9ad164, 0xFF49ba44);
        }
    }

    public void setStatus(TL_stories.Boost boost) {
        this.boost = boost;
        if ((boost.gift || boost.giveaway)) {
            badgeLayout.setVisibility(VISIBLE);
            int months = (boost.expires - boost.date) / 30 / 86400;
            if (boost.stars > 0) {
                nameTextView.setText(LocaleController.formatPluralString("BoostingBoostStars", (int) boost.stars));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_STARS);
                avatarImageView.setForUserOrChat(null, avatarDrawable);
                nameTextView.setRightDrawable(null);
            } else if (boost.unclaimed) {
                nameTextView.setText(LocaleController.getString(R.string.BoostingUnclaimed));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_UNCLAIMED);
                setAvatarColorByMonths(months);
                avatarImageView.setForUserOrChat(null, avatarDrawable);
                nameTextView.setRightDrawable(null);
            } else if (boost.user_id == NO_USER_ID) {
                nameTextView.setText(LocaleController.getString(R.string.BoostingToBeDistributed));
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_TO_BE_DISTRIBUTED);
                setAvatarColorByMonths(months);
                avatarImageView.setForUserOrChat(null, avatarDrawable);
                nameTextView.setRightDrawable(null);
            }

            final String date = LocaleController.getInstance().getFormatterBoostExpired().format(new Date(boost.expires * 1000L));
            if (boost.stars > 0) {
                statusTextView.setText(LocaleController.formatString(R.string.BoostingStarsExpires, date));
            } else {
                statusTextView.setText(LocaleController.formatString(R.string.BoostingExpires, date));
            }

            if (boost.gift) {
                if (giftDrawable == null) {
                    giftDrawable = getResources().getDrawable(R.drawable.mini_gift);
                    giftDrawable.setColorFilter(new PorterDuffColorFilter(0xFFce8e1f, PorterDuff.Mode.MULTIPLY));
                }
                badgeTextView.setTextColor(0xFFce8e1f);
                badgeTextView.setCompoundDrawablesWithIntrinsicBounds(giftDrawable, null, null, null);
                badgeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                badgeTextView.setText(LocaleController.getString(R.string.BoostingGift));
                badgeLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), AndroidUtilities.dp(12), Theme.multAlpha(0xFFce8e1f, 0.2f)));
            }
            if (boost.giveaway) {
                if (giveawayDrawable == null) {
                    giveawayDrawable = getResources().getDrawable(R.drawable.mini_giveaway);
                    giveawayDrawable.setColorFilter(new PorterDuffColorFilter(0XFF3391d4, PorterDuff.Mode.MULTIPLY));
                }
                badgeTextView.setTextColor(0XFF3391d4);
                badgeTextView.setCompoundDrawablesWithIntrinsicBounds(giveawayDrawable, null, null, null);
                badgeTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                badgeTextView.setText(LocaleController.getString(R.string.BoostingGiveaway));
                badgeLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), AndroidUtilities.dp(12), Theme.multAlpha(0XFF3391d4, 0.2f)));
            }
        } else {
            badgeLayout.setVisibility(GONE);
        }

        if (boost.multiplier > 0) {
            counterDrawable.setText(String.valueOf(boost.multiplier));
            nameTextView.setRightDrawable(counterDrawable);
        } else {
            nameTextView.setRightDrawable(null);
        }

        if (badgeLayout.getVisibility() == VISIBLE) {
            int badgeWidth = (int) badgeTextView.getPaint().measureText(badgeTextView.getText().toString()) + AndroidUtilities.dp(22);
            nameTextView.setPadding(LocaleController.isRTL ? badgeWidth : 0, nameTextView.getPaddingTop(), LocaleController.isRTL ? 0 : badgeWidth, nameTextView.getPaddingBottom());
        } else {
            nameTextView.setPadding(0, nameTextView.getPaddingTop(), 0, nameTextView.getPaddingBottom());
        }
    }
}

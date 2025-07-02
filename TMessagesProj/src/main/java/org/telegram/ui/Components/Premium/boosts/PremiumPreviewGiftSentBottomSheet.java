package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Build;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.Components.Premium.boosts.cells.ActionBtnCell;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.List;

public class PremiumPreviewGiftSentBottomSheet extends PremiumPreviewBottomSheet {
    private static final int BOTTOM_HEIGHT_DP = 64;

    private final List<TLRPC.User> selectedUsers = new ArrayList<>();

    public static void show(List<TLRPC.User> selectedUsers) {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment == null) {
            return;
        }
        PremiumPreviewGiftSentBottomSheet sheet = new PremiumPreviewGiftSentBottomSheet(fragment, UserConfig.selectedAccount, selectedUsers, fragment.getResourceProvider());
        sheet.setAnimateConfetti(true);
        sheet.setAnimateConfettiWithStars(true);
        sheet.show();
    }

    public PremiumPreviewGiftSentBottomSheet(BaseFragment fragment, int currentAccount, List<TLRPC.User> selectedUsers, Theme.ResourcesProvider resourcesProvider) {
        super(fragment, currentAccount, null, null, resourcesProvider);
        this.selectedUsers.addAll(selectedUsers);
        init();
    }

    @Override
    protected boolean needDefaultPremiumBtn() {
        return false;
    }

    @Override
    protected void updateRows() {
        rowCount = 0;
        paddingRow = rowCount++;
        featuresStartRow = rowCount;
        rowCount += premiumFeatures.size();
        featuresEndRow = rowCount;
        termsRow = rowCount++;
    }

    @Override
    public void setTitle(boolean animated) {
        titleView[0].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        subtitleView.setPadding(dp(30), 0, dp(30), 0);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        titleView[0].setText(getPluralString("GiftPremiumGiftsSent", selectedUsers.size()));
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).bottomMargin = dp(16);
        ((ViewGroup.MarginLayoutParams) subtitleView.getLayoutParams()).topMargin = dp(4f);

        String subTitle;
        switch (selectedUsers.size()) {
            case 1: {
                String names = formatString(R.string.GiftPremiumUsersOne, UserObject.getFirstName(selectedUsers.get(0)));
                subTitle = formatString(R.string.GiftPremiumUsersPurchasedManyZero, names);
                break;
            }
            case 2: {
                String names = formatString("GiftPremiumUsersTwo", R.string.GiftPremiumUsersTwo, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)));
                subTitle = formatString("GiftPremiumUsersPurchasedManyZero", R.string.GiftPremiumUsersPurchasedManyZero, names);
                break;
            }
            case 3: {
                String names = formatString("GiftPremiumUsersThree", R.string.GiftPremiumUsersThree, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)), UserObject.getFirstName(selectedUsers.get(2)));
                subTitle = formatString("GiftPremiumUsersPurchasedManyZero", R.string.GiftPremiumUsersPurchasedManyZero, names);
                break;
            }
            default: {
                String names = formatString("GiftPremiumUsersThree", R.string.GiftPremiumUsersThree, UserObject.getFirstName(selectedUsers.get(0)), UserObject.getFirstName(selectedUsers.get(1)), UserObject.getFirstName(selectedUsers.get(2)));
                subTitle = formatPluralString("GiftPremiumUsersPurchasedMany", selectedUsers.size() - 3, names);
                break;
            }
        }
        subtitleView.setText(replaceTags(subTitle));

        subtitleView.append("\n");
        subtitleView.append("\n");

        if (selectedUsers.size() == 1) {
            subtitleView.append(replaceTags(formatString("GiftPremiumGiftsSentStatusForUser", R.string.GiftPremiumGiftsSentStatusForUser, UserObject.getFirstName(selectedUsers.get(0)))));
        } else {
            subtitleView.append(replaceTags(getString("GiftPremiumGiftsSentStatus", R.string.GiftPremiumGiftsSentStatus)));
        }
    }

    @SuppressLint("ViewConstructor")
    static class AvatarHolderView extends FrameLayout {

        public static View createAvatarsContainer(Context context, List<TLRPC.User> selectedUsers) {
            FrameLayout avatarsContainer = new FrameLayout(context);
            avatarsContainer.setClipChildren(false);
            FrameLayout avatarsWrapper = new FrameLayout(context);
            avatarsWrapper.setClipChildren(false);

            if (selectedUsers.size() == 1) {
                avatarsContainer.addView(avatarsWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 94, 0, 0, 0, 0, 0));
                AvatarHolderView avatarHolderView = new AvatarHolderView(context, 47);
                avatarHolderView.drawCycle = false;
                avatarHolderView.setUser(selectedUsers.get(0));
                avatarsWrapper.addView(avatarHolderView, 0, LayoutHelper.createFrame(94, 94, Gravity.CENTER));
            } else {
                avatarsContainer.addView(avatarsWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 83, 0, 0, 0, 0, 0));
                int visibleCount = 0;
                for (int i = 0; i < selectedUsers.size(); i++) {
                    TLRPC.User user = selectedUsers.get(i);
                    AvatarHolderView avatarHolderView = new AvatarHolderView(context, 41.5f);
                    avatarHolderView.setUser(user);
                    avatarsWrapper.addView(avatarHolderView, 0, LayoutHelper.createFrame(83, 83, Gravity.CENTER));
                    avatarHolderView.setTranslationX(-i * dp(29));
                    if (i == 0 && selectedUsers.size() > 3) {
                        avatarHolderView.iconView.setAlpha(1f);
                        avatarHolderView.iconView.count = selectedUsers.size() - 3;
                    }
                    visibleCount++;
                    if (i == 2) {
                        break;
                    }
                }
                avatarsContainer.setTranslationX(dp(29 / 2f) * (visibleCount - 1));
            }
            return avatarsContainer;
        }

        private final BackupImageView imageView;
        protected final AdditionalCounterView iconView;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TLRPC.User user;
        public boolean drawCycle = true;
        AvatarDrawable fromAvatarDrawable = new AvatarDrawable();

        public AvatarHolderView(Context context, float radiusDp) {
            super(context);
            imageView = new BackupImageView(getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(radiusDp));
            iconView = new AdditionalCounterView(context);
            iconView.setAlpha(0f);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 5, 5, 5, 5));
            addView(iconView, LayoutHelper.createFrame(26, 26, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 1, 3));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            } else {
                bgPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
            }
        }

        public void setUser(TLRPC.User user) {
            this.user = user;
            fromAvatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, fromAvatarDrawable);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawCycle) {
                canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, (getMeasuredHeight() / 2f) - dp(2f), bgPaint);
            }
            super.dispatchDraw(canvas);
        }
    }

    static class AdditionalCounterView extends View {

        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        int count;

        public AdditionalCounterView(Context context) {
            super(context);
            paint.setTextAlign(Paint.Align.CENTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            } else {
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
            }
            paint.setTextSize(dp(11.5f));
            paint.setTypeface(AndroidUtilities.bold());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getMeasuredWidth() / 2f;
            float cy = getMeasuredHeight() / 2f;
            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f, paint);
            PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -AndroidUtilities.dp(10), 0);
            canvas.drawCircle(cx, cy, getMeasuredWidth() / 2f - AndroidUtilities.dp(1.5f), PremiumGradient.getInstance().getMainGradientPaint());
            cy = (int) (cy - ((paint.descent() + paint.ascent()) / 2f));
            canvas.drawText("+" + count, cx, cy, paint);
        }
    }

    private void init() {
        updateRows();
        useBackgroundTopPadding = false;
        setApplyTopPadding(false);
        backgroundPaddingTop = 0;
        ActionBtnCell actionBtn = new ActionBtnCell(getContext(), resourcesProvider);
        actionBtn.setOnClickListener(v -> dismiss());
        actionBtn.setCloseStyle(true);
        containerView.addView(actionBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, BOTTOM_HEIGHT_DP, Gravity.BOTTOM, 0, 0, 0, 0));

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(BOTTOM_HEIGHT_DP));
        overrideTitleIcon = AvatarHolderView.createAvatarsContainer(getContext(), selectedUsers);
        fixNavigationBar();
    }

    protected void afterCellCreated(int viewType, View view) {
        if (viewType == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        float cornerRadius = AndroidUtilities.dp(12);
                        outline.setRoundRect(0, 0, view.getWidth(), (int) (view.getHeight() + cornerRadius), cornerRadius);
                    }
                });
                view.setClipToOutline(true);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            }
            ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin = -dp(6);
        }
    }

    @Override
    protected void attachIconContainer(LinearLayout container) {
        container.addView(overrideTitleIcon, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                selectedUsers.size() == 1 ? 94 : 83,
                0,
                selectedUsers.size() == 1 ? 28 : 34,
                0,
                selectedUsers.size() == 1 ? 9 : 14)
        );
    }
}

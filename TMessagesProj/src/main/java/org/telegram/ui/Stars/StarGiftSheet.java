package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatSpannable;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttribute;
import static org.telegram.ui.Stars.StarsController.findAttributes;
import static org.telegram.ui.Stars.StarsController.showNoSupportDialog;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;
import static org.telegram.ui.Stars.StarsIntroActivity.addAvailabilityRow;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceStars;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain;
import static org.telegram.ui.Stars.StarsIntroActivity.setGiftImage;
import static org.telegram.ui.bots.AffiliateProgramFragment.percents;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CompatDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.Gifts.ProfileGiftsContainer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.TwoStepVerificationActivity;
import org.telegram.ui.TwoStepVerificationSetupActivity;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.ui.bots.BotWebViewSheet;

import java.util.ArrayList;
import java.util.Date;

public class StarGiftSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    private final long dialogId;
    private ContainerView container;
    private ViewPagerFixed viewPager;
    private FireworksOverlay fireworksOverlay;
    private final View bottomView;

    private StarGiftSheet left, right;

    private final TopView topView;

    private final LinearLayout infoLayout;
    private final LinkSpanDrawable.LinksTextView beforeTableTextView;
    private final TableView tableView;
    private final LinkSpanDrawable.LinksTextView afterTableTextView;
    private final ButtonWithCounterView button;
    private final FrameLayout buttonContainer;
    private final View buttonShadow;
    private final FrameLayout bottomBulletinContainer;

    private final LinearLayout upgradeLayout;
    private final AffiliateProgramFragment.FeatureCell[] upgradeFeatureCells;
    private final View checkboxSeparator;
    private final LinearLayout checkboxLayout;
    private final CheckBox2 checkbox;
    private final TextView checkboxTextView;

    private boolean onlyWearInfo;
    private final LinearLayout wearLayout;
    private final TextView wearTitle;
    private final TextView wearSubtitle;
    private final AffiliateProgramFragment.FeatureCell[] wearFeatureCells;

    private boolean myProfile;
    private TL_stars.SavedStarGift savedStarGift;
    private StarsController.GiftsList giftsList;

    private MessageObject messageObject;
    private String slug;
    private TL_stars.TL_starGiftUnique slugStarGift;
    private boolean messageObjectRepolling;
    private boolean messageObjectRepolled;
    private boolean userStarGiftRepolling;
    private boolean userStarGiftRepolled;

    public StarGiftSheet(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        topPadding = 0.2f;

        containerView = new FrameLayout(context);
        container = new ContainerView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void swapViews() {
                super.swapViews(); // TODO
                if (currentPosition != (hasNeighbour(false) ? 1 : 0)) {
                    final boolean r = currentPosition > (hasNeighbour(false) ? 1 : 0);
                    AndroidUtilities.runOnUIThread(() -> {
                        final TL_stars.SavedStarGift gift = getNeighbourGift(r);
                        if (gift != null) {
                            firstSet = true;
                            set(gift, giftsList);
                        }
                    });
                }
            }

            @Override
            protected void setTranslationX(View view, float tx) {
                if (getMeasuredWidth() <= 0) {
                    view.setTranslationX(tx);
//                    if (view instanceof ViewGroup && ((ViewGroup) view).getChildAt(0) instanceof ContainerView) {
//                        ((ContainerView) ((ViewGroup) view).getChildAt(0)).setDimAlpha(0);
//                    }
                    return;
                }
                final float t = Utilities.clamp(tx / getMeasuredWidth(), +1, -1);
                view.setTranslationX(tx + -t * 2 * backgroundPaddingLeft);
                view.setPivotX(t > 0 ? 0 : view.getMeasuredWidth());
                view.setCameraDistance(view.getMeasuredHeight() * 3.4f);
                view.setScaleX(1.0f - Math.abs(t * .25f));
                view.setRotationY(t * 10);
//                if (view instanceof ViewGroup && ((ViewGroup) view).getChildAt(0) instanceof ContainerView) {
//                    ((ContainerView) ((ViewGroup) view).getChildAt(0)).setDimAlpha(Math.abs(t * .15f));
//                }
            }

            @Override
            protected boolean canScroll(MotionEvent e) {
                return currentPage == null || currentPage.is(PAGE_INFO);
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 1 + (hasNeighbour(false) ? 1 : 0) + (hasNeighbour(true) ? 1 : 0);
            }

            @Override
            public View createView(int viewType) {
                View thisContainer;
                if (viewType == 0) {
                    setupNeighbour(false);
                    if (left == null) return null;
                    thisContainer = left.container;
                } else if (viewType == 1) {
                    thisContainer = container;
                } else if (viewType == 2) {
                    setupNeighbour(true);
                    if (right == null) return null;
                    thisContainer = right.container;
                } else return null;
                AndroidUtilities.removeFromParent(thisContainer);
                final FrameLayout frameLayout = new FrameLayout(context);
                frameLayout.addView(thisContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                return frameLayout;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                View newContainer;
                if (viewType == 0) {
                    setupNeighbour(false);
                    if (left == null) return;
                    newContainer = left.container;
                } else if (viewType == 2) {
                    setupNeighbour(true);
                    if (right == null) return;
                    newContainer = right.container;
                } else return;
                FrameLayout parent = (FrameLayout) view;
                parent.removeAllViews();
                AndroidUtilities.removeFromParent(newContainer);
                parent.addView(newContainer);
            }

            @Override
            public int getItemViewType(int position) {
                position -= hasNeighbour(false) ? 1 : 0;
                return 1 + position;
            }
        });
        updateViewPager();
        bottomView = new View(context);
        bottomView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        containerView.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM));
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        AndroidUtilities.removeFromParent(recyclerListView);
        container.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        infoLayout = new LinearLayout(context);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setPadding(backgroundPaddingLeft + dp(14), dp(16), backgroundPaddingLeft + dp(14), dp(12 + 48 + 8));
        container.addView(infoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        beforeTableTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        beforeTableTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        beforeTableTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        beforeTableTextView.setGravity(Gravity.CENTER);
        beforeTableTextView.setLineSpacing(dp(2), 1f);
        beforeTableTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        beforeTableTextView.setDisablePaddingsOffsetY(true);
        infoLayout.addView(beforeTableTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 5, -4, 5, 16));
        beforeTableTextView.setVisibility(View.GONE);

        tableView = new TableView(context, resourcesProvider);
        infoLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        afterTableTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        afterTableTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        afterTableTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        afterTableTextView.setGravity(Gravity.CENTER);
        afterTableTextView.setLineSpacing(dp(2), 1f);
        afterTableTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        afterTableTextView.setDisablePaddingsOffsetY(true);
        afterTableTextView.setPadding(dp(5), 0, dp(5), 0);
        infoLayout.addView(afterTableTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 5, 2, 5, 8));
        afterTableTextView.setVisibility(View.GONE);

        upgradeLayout = new LinearLayout(context);
        upgradeLayout.setOrientation(LinearLayout.VERTICAL);
        upgradeLayout.setPadding(dp(4) + backgroundPaddingLeft, dp(24), dp(4) + backgroundPaddingLeft, dp(12 + 48 + 6));
        container.addView(upgradeLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        upgradeFeatureCells = new AffiliateProgramFragment.FeatureCell[3];
        upgradeFeatureCells[0] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        upgradeFeatureCells[0].set(R.drawable.menu_feature_unique, getString(R.string.Gift2UpgradeFeature1Title), getString(R.string.Gift2UpgradeFeature1Text));
        upgradeLayout.addView(upgradeFeatureCells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        upgradeFeatureCells[1] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        upgradeFeatureCells[1].set(R.drawable.menu_feature_transfer, getString(R.string.Gift2UpgradeFeature2Title), getString(R.string.Gift2UpgradeFeature2Text));
        upgradeLayout.addView(upgradeFeatureCells[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        upgradeFeatureCells[2] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        upgradeFeatureCells[2].set(R.drawable.menu_feature_tradable, getString(R.string.Gift2UpgradeFeature3Title), getString(R.string.Gift2UpgradeFeature3Text));
        upgradeLayout.addView(upgradeFeatureCells[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        checkboxSeparator = new View(context);
        checkboxSeparator.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        upgradeLayout.addView(checkboxSeparator, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL, 17, -4, 17, 6));

        checkboxLayout = new LinearLayout(context);
        checkboxLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
        checkbox = new CheckBox2(context, 24, resourcesProvider);
        checkbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkbox.setDrawUnchecked(true);
        checkbox.setChecked(false, false);
        checkbox.setDrawBackgroundAsArc(10);
        checkboxLayout.addView(checkbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        checkboxTextView = new TextView(context);
        checkboxTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        checkboxTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkboxTextView.setText(LocaleController.getString(R.string.Gift2AddSenderName));
        checkboxLayout.addView(checkboxTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
        upgradeLayout.addView(checkboxLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        ScaleStateListAnimator.apply(checkboxLayout, 0.025f, 1.5f);

        wearLayout = new LinearLayout(context);
        wearLayout.setOrientation(LinearLayout.VERTICAL);
        wearLayout.setPadding(dp(4) + backgroundPaddingLeft, dp(20), dp(4) + backgroundPaddingLeft, dp(12 + 48 + 6));
        container.addView(wearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        wearTitle = new TextView(context);
        wearTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        wearTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        wearTitle.setGravity(Gravity.CENTER);
        wearTitle.setTypeface(AndroidUtilities.bold());
        wearLayout.addView(wearTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 20, 0, 20, 0));

        wearSubtitle = new TextView(context);
        wearSubtitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        wearSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        wearSubtitle.setGravity(Gravity.CENTER);
        wearSubtitle.setText(LocaleController.getString(R.string.Gift2WearSubtitle));
        wearLayout.addView(wearSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 20, 6, 20, 24));

        wearFeatureCells = new AffiliateProgramFragment.FeatureCell[3];
        wearFeatureCells[0] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        wearFeatureCells[0].set(R.drawable.menu_feature_unique, getString(R.string.Gift2WearFeature1Title), getString(R.string.Gift2WearFeature1Text));
        wearLayout.addView(wearFeatureCells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        wearFeatureCells[1] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        wearFeatureCells[1].set(R.drawable.menu_feature_cover, getString(R.string.Gift2WearFeature2Title), getString(R.string.Gift2WearFeature2Text));
        wearLayout.addView(wearFeatureCells[1], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        wearFeatureCells[2] = new AffiliateProgramFragment.FeatureCell(context, resourcesProvider);
        wearFeatureCells[2].set(R.drawable.menu_verification, getString(R.string.Gift2WearFeature3Title), getString(R.string.Gift2WearFeature3Text));
        wearLayout.addView(wearFeatureCells[2], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        infoLayout.setAlpha(1.0f);
        upgradeLayout.setAlpha(0.0f);
        wearLayout.setAlpha(0.0f);

        topView = new TopView(context, resourcesProvider, this::onBackPressed, this::onMenuPressed, v -> openTransfer(), this::onWearPressed, this::onSharePressed);
        topView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        container.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        layoutManager.setReverseLayout(reverseLayout = true);

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(getThemedColor(Theme.key_divider));
        buttonShadow.setAlpha(0.0f);
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        final FrameLayout.LayoutParams buttonLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 0, 12, 0, 12);
        buttonLayoutParams.leftMargin = backgroundPaddingLeft + dp(14);
        buttonLayoutParams.rightMargin = backgroundPaddingLeft + dp(14);
        buttonContainer.addView(button, buttonLayoutParams);
        container.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 12 + 48 + 12, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                container.updateTranslations();
            }
        });

        checkboxLayout.setOnClickListener(v -> {
            if (button.isLoading()) return;
            checkbox.setChecked(!checkbox.isChecked(), true);
        });

        fireworksOverlay = new FireworksOverlay(context);
        container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bottomBulletinContainer = new FrameLayout(context);
        bottomBulletinContainer.setPadding(backgroundPaddingLeft + dp(14 - 8), 0, backgroundPaddingLeft + dp(14 - 8), 0);
        container.addView(bottomBulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 12 + 48));

        AndroidUtilities.removeFromParent(actionBar);
        container.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6, 0));
    }

    private final int[] heights = new int[2];
    private Adapter adapter;
    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        private class SpaceView extends View {
            public SpaceView(Context context) {
                super(context);
            }

            private int height = 0;
            public void setHeight(int heightPx, int a) {
                if (this.height != heightPx) {
                    this.height = heightPx;
                    requestLayout();
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(this.height, MeasureSpec.EXACTLY)
                );
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return false;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new SpaceView(getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            position = heights.length - 1 - position;
            ((SpaceView) holder.itemView).setHeight(heights[position], position);
        }

        @Override
        public int getItemCount() {
            return heights.length;
        }

        public void setHeights(int top, int bottom) {
            if (heights[0] != top || heights[1] != bottom) {
                heights[0] = top;
                heights[1] = bottom;
                notifyDataSetChanged();
            }
        }
    }

    private int getListPosition() {
        if (giftsList == null || savedStarGift == null) return -1;
        int index = giftsList.gifts.indexOf(savedStarGift);
        if (index >= 0) return index;
        for (int i = 0; i < giftsList.gifts.size(); ++i) {
            final TL_stars.SavedStarGift savedGift = giftsList.gifts.get(i);
            if (eq(savedStarGift, savedGift)) {
                return i;
            }
        }
        return -1;
    }

    private TL_stars.SavedStarGift getNeighbourGift(boolean r) {
        final int thisPosition = getListPosition();
        if (thisPosition < 0) return null;
        TL_stars.SavedStarGift gift = null;
        final int index = thisPosition + (r ? +1 : -1);
        if (giftsList != null && index >= 0 && index < giftsList.gifts.size()) {
            gift = giftsList.gifts.get(index);
        }
        return gift;
    }

    private boolean hasNeighbour(boolean r) {
        return getNeighbourGift(r) != null;
    }

    private void setupNeighbour(boolean r) {
        int thisPosition = getListPosition();
        if (thisPosition < 0) return;
        TL_stars.SavedStarGift gift = null;
        final int index = thisPosition + (r ? +1 : -1);
        if (giftsList != null && index >= 0 && index < giftsList.gifts.size()) {
            gift = giftsList.gifts.get(index);
        }
        if (gift == null) return;
        if ((r ? right : left) != null && eq((r ? right : left).savedStarGift, gift)) {
            return;
        }
        StarGiftSheet sheet = new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider);
        sheet.set(gift, giftsList);
        AndroidUtilities.removeFromParent(sheet.containerView);
        if (r) {
            right = sheet;
        } else {
            left = sheet;
        }
    }

    private void updateViewPager() {
        viewPager.setPosition(hasNeighbour(false) ? 1 : 0);
        viewPager.rebuild(false);
        if (giftsList != null && !hasNeighbour(true) && giftsList.gifts.size() < giftsList.totalCount) {
            giftsList.load();
        }
    }

    public boolean eq(TL_stars.SavedStarGift a, TL_stars.SavedStarGift b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.gift == b.gift) return true;
        if (a.gift instanceof TL_stars.TL_starGiftUnique && b.gift instanceof TL_stars.TL_starGiftUnique) {
            return a.gift.id == b.gift.id;
        }
        if (a.gift instanceof TL_stars.TL_starGift && b.gift instanceof TL_stars.TL_starGift) {
            return a.gift.id == b.gift.id && a.date == b.date;
        }
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
    }

    @Override
    protected boolean shouldDrawBackground() {
        return false;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new Adapter();
    }

    private String title = "";
    @Override
    protected CharSequence getTitle() {
        return title;
    }

    public static boolean isMine(int currentAccount, long dialogId) {
        if (dialogId >= 0) {
            return UserConfig.getInstance(currentAccount).getClientUserId() == dialogId;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
        }
    }

    public static boolean isMineWithActions(int currentAccount, long dialogId) {
        if (dialogId >= 0) {
            return UserConfig.getInstance(currentAccount).getClientUserId() == dialogId;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return chat != null && chat.creator;
        }
    }

    private void onMenuPressed(View btn) {
        final String link = getLink();
        ItemOptions.makeOptions(container, resourcesProvider, btn)
            .addIf(getUniqueGift() != null && isMineWithActions(currentAccount, DialogObject.getPeerDialogId(getUniqueGift().owner_id)) && giftsList != null && savedStarGift != null && getInputStarGift() != null, (savedStarGift != null && savedStarGift.pinned_to_top) ? R.drawable.msg_unpin : R.drawable.msg_pin, getString((savedStarGift != null && savedStarGift.pinned_to_top) ? R.string.Gift2Unpin : R.string.Gift2Pin), () -> {
                if (savedStarGift.unsaved) {
                    savedStarGift.unsaved = false;

                    final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                    req.stargift = getInputStarGift();
                    req.unsave = savedStarGift.unsaved;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
                }

                final boolean newPinned = !savedStarGift.pinned_to_top;
                if (giftsList.togglePinned(savedStarGift, newPinned, false)) {
                    new ProfileGiftsContainer.UnpinSheet(getContext(), dialogId, savedStarGift, resourcesProvider, this::getBulletinFactory).show();
                } else if (newPinned) {
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.ic_pin, getString(R.string.Gift2PinnedTitle), getString(R.string.Gift2PinnedSubtitle))
                        .show();
                } else {
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.ic_unpin, getString(R.string.Gift2Unpinned))
                        .show();
                }
            })
            .addIf(link != null, R.drawable.msg_link, getString(R.string.CopyLink), () -> {
                AndroidUtilities.addToClipboard(link);
                getBulletinFactory()
                    .createCopyLinkBulletin(false)
                    .ignoreDetach()
                    .show();
            })
            .addIf(link != null, R.drawable.msg_share, getString(R.string.ShareFile), () -> {
                onSharePressed(null);
            })
            .addIf(canTransfer(), R.drawable.menu_feature_transfer, getString(R.string.Gift2TransferOption), this::openTransfer)
            .addIf(savedStarGift == null && getDialogId() != 0, R.drawable.msg_view_file, getString(R.string.Gift2ViewInProfile), this::openInProfile)
            .setDrawScrim(false)
            .setOnTopOfScrim()
            .setDimAlpha(0)
            .translate(0, -dp(2))
            .show();
    }

    private ColoredImageSpan lockSpan;
    private boolean shownWearInfo;
    private void onWearPressed(View v) {
        if (UserConfig.getInstance(currentAccount).isPremium() && (isWorn(currentAccount, getUniqueGift()) || shownWearInfo)) {
            toggleWear();
            return;
        }
        final TL_stars.TL_starGiftUnique gift = getUniqueGift();
        if (gift == null) return;
        final long owner_id = DialogObject.getPeerDialogId(gift.owner_id);
        final String product = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');
        wearTitle.setText(LocaleController.formatString(R.string.Gift2WearTitle, product));
        SpannableStringBuilder buttonText = new SpannableStringBuilder(getString(R.string.Gift2WearStart));
        if (!UserConfig.getInstance(currentAccount).isPremium()) {
            buttonText.append(" l");
            if (lockSpan == null) {
                lockSpan = new ColoredImageSpan(R.drawable.msg_mini_lock3);
            }
            buttonText.setSpan(lockSpan, buttonText.length() - 1, buttonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        button.setText(buttonText, true);
        button.setOnClickListener(v2 -> {
            shownWearInfo = true;
            toggleWear();
        });
        topView.setWearPreview(MessagesController.getInstance(currentAccount).getUserOrChat(owner_id));
        switchPage(PAGE_WEAR, true);
    }

    public StarGiftSheet setupWearPage() {
        final TL_stars.TL_starGiftUnique gift = getUniqueGift();
        if (gift == null) return this;
        final long owner_id = DialogObject.getPeerDialogId(gift.owner_id);
        final String product = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');
        wearTitle.setText(LocaleController.formatString(R.string.Gift2WearTitle, product));
        SpannableStringBuilder buttonText = new SpannableStringBuilder(getString(R.string.Gift2WearStart));
        if (owner_id == UserConfig.getInstance(currentAccount).getClientUserId() && !UserConfig.getInstance(currentAccount).isPremium()) {
            buttonText.append(" l");
            if (lockSpan == null) {
                lockSpan = new ColoredImageSpan(R.drawable.msg_mini_lock3);
            }
            buttonText.setSpan(lockSpan, buttonText.length() - 1, buttonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        button.setText(buttonText, true);
        button.setOnClickListener(v2 -> {
            shownWearInfo = true;
            toggleWear();
        });
        topView.setWearPreview(MessagesController.getInstance(currentAccount).getUserOrChat(owner_id));
        switchPage(PAGE_WEAR, false);
        onlyWearInfo = true;
        return this;
    }

    public static boolean isWorn(int currentAccount, TL_stars.TL_starGiftUnique gift) {
        if (gift == null) return false;
        final long owner_id = DialogObject.getPeerDialogId(gift.owner_id);
        if (owner_id == 0) return false;
        if (owner_id > 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(owner_id);
            if (user != null && user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible) {
                final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) user.emoji_status;
                return status.collectible_id == gift.id;
            }
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-owner_id);
            if (chat != null && chat.emoji_status instanceof TLRPC.TL_emojiStatusCollectible) {
                final TLRPC.TL_emojiStatusCollectible status = (TLRPC.TL_emojiStatusCollectible) chat.emoji_status;
                return status.collectible_id == gift.id;
            }
        }
        return false;
    }

    public void toggleWear() {
        toggleWear(false);
    }
    public void toggleWear(boolean checkedLevel) {
        final TL_stars.TL_starGiftUnique gift = getUniqueGift();
        if (gift == null) return;
        MessagesController.getGlobalMainSettings().edit().putInt("statusgiftpage", 3).apply();
        final boolean worn = !isWorn(currentAccount, getUniqueGift());
        if (isWorn(currentAccount, getUniqueGift())) {
            MessagesController.getInstance(currentAccount).updateEmojiStatus(getDialogId(), new TLRPC.TL_emojiStatusEmpty(), null);
        } else {
            final long did = getDialogId();
            if (did >= 0) {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    getBulletinFactory()
                        .createSimpleBulletinDetail(R.raw.star_premium_2, AndroidUtilities.premiumText(getString(R.string.Gift2ActionWearNeededPremium), () -> {
                            new PremiumFeatureBottomSheet(getDummyFragment(), PremiumPreviewFragment.PREMIUM_FEATURE_EMOJI_STATUS, false).show();
                        }))
                        .ignoreDetach()
                        .show();
                    return;
                }
            } else if (!checkedLevel) {
                final MessagesController m = MessagesController.getInstance(currentAccount);
                button.setLoading(true);
                MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(did, boostsStatus -> {
                    if (boostsStatus == null || boostsStatus.level >= m.channelEmojiStatusLevelMin) {
                        button.setLoading(false);
                        toggleWear(true);
                    } else {
                        m.getBoostsController().userCanBoostChannel(did, boostsStatus, canApplyBoost -> {
                            button.setLoading(false);
                            LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(getDummyFragment(), getContext(), LimitReachedBottomSheet.TYPE_BOOSTS_FOR_WEAR_COLLECTIBLE, currentAccount, resourcesProvider);
                            limitReachedBottomSheet.setCanApplyBoost(canApplyBoost);
                            limitReachedBottomSheet.setBoostsStats(boostsStatus, true);
                            limitReachedBottomSheet.setDialogId(did);
                            final TLRPC.Chat channel = m.getChat(-did);
                            if (channel != null) {
                                limitReachedBottomSheet.showStatisticButtonInLink(() -> {
                                    presentFragment(StatisticActivity.create(channel));
                                });
                            }
                            limitReachedBottomSheet.show();
                        });
                    }
                });
                return;
            }
            final TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
            status.collectible_id = gift.id;
            MessagesController.getInstance(currentAccount).updateEmojiStatus(getDialogId(), status, gift);
        }
        topView.buttons[1].set(worn ? R.drawable.filled_crown_off : R.drawable.filled_crown_on, getString(worn ? R.string.Gift2ActionWearOff : R.string.Gift2ActionWear), true);
        if (onlyWearInfo) {
            dismiss();
            return;
        }
        final Runnable showHint = () -> showHint(AndroidUtilities.replaceTags(formatString(worn ? R.string.Gift2ActionWearDone : R.string.Gift2ActionWearOffDone, getGiftName())), ownerTextView, true);
        if (currentPage.is(PAGE_INFO)) {
            showHint.run();
        } else {
            switchPage(PAGE_INFO, true, showHint);
        }
        button.setText(getString(R.string.OK), !firstSet);
        button.setOnClickListener(v -> onBackPressed());
    }

    private BaseFragment getDummyFragment() {
        return new BaseFragment() {
            @Override
            public int getCurrentAccount() {
                return currentAccount;
            }

            @Override
            public Context getContext() {
                return StarGiftSheet.this.getContext();
            }

            @Override
            public Activity getParentActivity() {
                Context context = getContext();
                while (context instanceof ContextWrapper) {
                    if (context instanceof Activity) {
                        return (Activity) context;
                    }
                    context = ((ContextWrapper) context).getBaseContext();
                }
                return null;
            }

            @Override
            public Dialog showDialog(Dialog dialog) {
                dialog.show();
                return dialog;
            }
        };
    }

    private ShareAlert shareAlert;
    public void onSharePressed(View btn) {
        if (shareAlert != null && shareAlert.isShown()) {
            shareAlert.dismiss();
        }
        final String link = getLink();
        shareAlert = new ShareAlert(getContext(), null, null, link, null, false, link, null, false, false, true, null, resourcesProvider) {
            { includeStoryFromMessage = true; }

            @Override
            protected void onShareStory(View cell) {
                repostStory(cell);
            }

            @Override
            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                if (!showToast) return;
                super.onSend(dids, count, topic, showToast);
                BulletinFactory bulletinFactory = getBulletinFactory();
                if (bulletinFactory != null) {
                    if (dids.size() == 1) {
                        long did = dids.keyAt(0);
                        if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                            bulletinFactory.createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.LinkSharedToSavedMessages)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).ignoreDetach().show();
                        } else if (did < 0) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.LinkSharedTo, topic != null ? topic.title : chat.title)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).ignoreDetach().show();
                        } else {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.LinkSharedTo, user.first_name)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).ignoreDetach().show();
                        }
                    } else {
                        bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatPluralString("LinkSharedToManyChats", dids.size(), dids.size()))).hideAfterBottomSheet(false).ignoreDetach().show();
                    }
                    try {
                        container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } catch (Exception ignored) {}
                }
            }
        };
        shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
            @Override
            public boolean didCopy() {
                getBulletinFactory()
                    .createCopyLinkBulletin(false)
                    .ignoreDetach()
                    .show();
                return true;
            }
        });
        shareAlert.show();
    }

    private void repostStory(View cell) {
        Activity activity = LaunchActivity.instance;
        if (activity == null) {
            return;
        }
        StoryRecorder.SourceView sourceView = null;
        if (cell instanceof ShareDialogCell) {
            sourceView = StoryRecorder.SourceView.fromShareCell((ShareDialogCell) cell);
        }
        final ArrayList<MessageObject> messageObjects = new ArrayList<>();
        if (messageObject != null) {
            messageObjects.add(messageObject);
        } else if (getGift() instanceof TL_stars.TL_starGiftUnique) {
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) getGift();

            final TLRPC.TL_messageService message = new TLRPC.TL_messageService();
            message.peer_id = MessagesController.getInstance(currentAccount).getPeer(selfId);
            message.from_id = MessagesController.getInstance(currentAccount).getPeer(selfId);
            message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            final TLRPC.TL_messageActionStarGiftUnique action = new TLRPC.TL_messageActionStarGiftUnique();
            action.gift = gift;
            action.upgrade = true;
            message.action = action;

            final MessageObject msg = new MessageObject(currentAccount, message, false, false);
            msg.setType();
            messageObjects.add(msg);
        } else {
            return;
        }
        StoryRecorder editor = StoryRecorder.getInstance(activity, currentAccount);
        editor.setOnPrepareCloseListener((t, close, sent, did) -> {
            if (sent) {
                AndroidUtilities.runOnUIThread(() -> {
                    String chatTitle = "";
                    if (did < 0) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                        if (chat != null) {
                            chatTitle = chat.title;
                        }
                    }
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.contact_check, AndroidUtilities.replaceTags(
                            TextUtils.isEmpty(chatTitle) ?
                                LocaleController.getString(R.string.GiftRepostedToProfile) :
                                LocaleController.formatString(R.string.GiftRepostedToChannelProfile, chatTitle)
                    )).ignoreDetach().show();
                });
                editor.replaceSourceView(null);
                if (shareAlert != null) {
                    shareAlert.dismiss();
                    shareAlert = null;
                }
            } else {
                StoryRecorder.SourceView sourceView2 = null;
                if (cell instanceof ShareDialogCell && cell.isAttachedToWindow()) {
                    sourceView2 = StoryRecorder.SourceView.fromShareCell((ShareDialogCell) cell);
                }
                editor.replaceSourceView(sourceView2);
            }
            AndroidUtilities.runOnUIThread(close);
        });
        editor.openRepost(sourceView, StoryEntry.repostMessage(messageObjects));
    }

    @Override
    protected int getActionBarProgressHeight() {
        return dp(12);
    }

    private class ContainerView extends FrameLayout {

        private final RectF rect = new RectF();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ContainerView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top() && containerView.isAttachedToWindow()) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            preDrawInternal(canvas, this);
            canvas.save();

            final float top = top();
            final float r = dp(12);
            rect.set(backgroundPaddingLeft, top, getWidth() - backgroundPaddingLeft, getHeight() + r);
            backgroundPaint.setColor(getThemedColor(Theme.key_dialogBackground));
            path.rewind();
            path.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.drawPath(path, backgroundPaint);
            canvas.clipPath(path);

            super.dispatchDraw(canvas);
            if (dimAlpha != 0) {
                canvas.drawColor(Theme.multAlpha(0xFF000000, dimAlpha));
            }
            updateTranslations();

            canvas.restore();
            drawView(canvas, actionBar);
//            drawView(canvas, bottomView);

            postDrawInternal(canvas, this);
        }

        private float dimAlpha = 0;
        public void setDimAlpha(float dimAlpha) {
            if ((int) (0xFF * dimAlpha) != (int) (0xFF * this.dimAlpha)) {
                this.dimAlpha = dimAlpha;
                invalidate();
            }
        }

        private void drawView(Canvas canvas, View view) {
            if (view == null || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0) return;
            if (view.getAlpha() < 1) {
                canvas.saveLayerAlpha(view.getX(), view.getY(), view.getX() + view.getMeasuredWidth(), view.getY() + view.getMeasuredHeight(), (int) (0xFF * actionBar.getAlpha()), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
                canvas.clipRect(view.getX(), view.getY(), view.getX() + view.getMeasuredWidth(), view.getY() + actionBar.getMeasuredHeight());
            }
            canvas.translate(view.getX(), view.getY());
            view.draw(canvas);
            canvas.restore();
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child == actionBar) {
                return false;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (adapter != null) {
                adapter.setHeights(topView.getFinalHeight(), getBottomView().getMeasuredHeight());
            }
            onSwitchedPage();
        }

        public void updateTranslations() {
            float top = top();
            Log.i("lolkek", "a=" + containerView.isAttachedToWindow() + " top=" + top);
            topView.setTranslationY(top);
            infoLayout.setTranslationY(top + topView.getRealHeight());
            upgradeLayout.setTranslationY(top + topView.getRealHeight());
            wearLayout.setTranslationY(top + topView.getRealHeight());
            if (topBulletinContainer != null) {
                topBulletinContainer.setTranslationY(getTranslationY() - height() - AndroidUtilities.navigationBarHeight);
            }
            AndroidUtilities.updateViewVisibilityAnimated(buttonShadow, recyclerListView.canScrollVertically(1));
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            if (topBulletinContainer != null) {
                topBulletinContainer.setTranslationY(getTranslationY() - height() - AndroidUtilities.navigationBarHeight);
            }
        }

        public float height() {
            float h = 0;
            h += topView.getRealHeight();
            h += currentPage.at(PAGE_INFO) * infoLayout.getMeasuredHeight();
            h += currentPage.at(PAGE_UPGRADE) * upgradeLayout.getMeasuredHeight();
            h += currentPage.at(PAGE_WEAR) * wearLayout.getMeasuredHeight();
            return h;
        }

        public float top() {
            float top = Math.max(0, getHeight() - height());
            for (int i = recyclerListView.getChildCount() - 1; i >= 0; --i) {
                final View child = recyclerListView.getChildAt(i);
                int position = recyclerListView.getChildAdapterPosition(child);
                if (position < 0) continue;
                if (position == 2) {
                    top = child.getTop() + child.getTranslationY() + child.getHeight();
                    break;
                } else if (position == 1) {
                    top = child.getY();
                    break;
                } else if (position == 0) {
                    top = child.getY() - topView.getRealHeight();
                    break;
                }
            }
            if (lastTop != null && currentPage != null && currentPage.progress < 1) {
                return lerp(lastTop, top, currentPage.progress);
            }
            return top;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            contentHeight = height;
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof HintView2) {
                    child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(100), MeasureSpec.EXACTLY));
                } else if (child == recyclerListView) {
                    child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                } else {
                    child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST));
                }
            }
            setMeasuredDimension(width, height);
            if (adapter != null) {
                adapter.setHeights(topView.getFinalHeight(), getBottomView().getMeasuredHeight());
            }
        }
    }

    public static class TopView extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final FrameLayout imageLayout;
        private final BackupImageView[] imageView = new BackupImageView[3];
        private int currentImageIndex = 0;

        private final LinearLayout[] layout = new LinearLayout[3];
        private final FrameLayout.LayoutParams[] layoutLayoutParams = new FrameLayout.LayoutParams[3];

        private final LinkSpanDrawable.LinksTextView[] titleView = new LinkSpanDrawable.LinksTextView[3];
        private LinkSpanDrawable.LinksTextView priceView;
        private final LinkSpanDrawable.LinksTextView[] subtitleView = new LinkSpanDrawable.LinksTextView[3];
        private final LinearLayout.LayoutParams[] subtitleViewLayoutParams = new LinearLayout.LayoutParams[3];
        private final LinearLayout buttonsLayout;
        private int buttonsBackgroundColor;
        public final Button[] buttons;

        private FrameLayout userLayout;
        private BackupImageView avatarView;

        private final ImageView closeView;
        private final ImageView optionsView;

        public static class Button extends FrameLayout {

            public ImageView imageView;
            public TextView textView;

            public Button(Context context) {
                super(context);

                imageView = new ImageView(context);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

                textView = new TextView(context);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                textView.setTextColor(0xFFFFFFFF);
                textView.setGravity(Gravity.CENTER);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, 35, 4, 0));
            }

            public void set(int iconResId, CharSequence text, boolean animated) {
                if (animated) {
                    AndroidUtilities.updateImageViewImageAnimated(imageView, iconResId);
                } else {
                    imageView.setImageResource(iconResId);
                }
                textView.setText(text);
            }
        }

        public TopView(Context context, Theme.ResourcesProvider resourcesProvider, Runnable dismiss, View.OnClickListener onMenuClick, View.OnClickListener onTransferClick, View.OnClickListener onWearClick, View.OnClickListener onShareClick) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setWillNotDraw(false);

            imageLayout = new FrameLayout(context);
            for (int i = 0; i < 3; ++i) {
                imageView[i] = new BackupImageView(context);
                imageView[i].setLayerNum(4 | 6656);
                if (i > 0) {
                    imageView[i].getImageReceiver().setCrossfadeDuration(1);
                }
                imageLayout.addView(imageView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                imageView[i].setAlpha(i == currentImageIndex ? 1.0f : 0.0f);
            }

            buttonsLayout = new LinearLayout(context);
            buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttons = new Button[3];
            for (int i = 0; i < buttons.length; ++i) {
                buttons[i] = new Button(context);
                switch (i) {
                    case 0:
                        buttons[i].set(R.drawable.filled_gift_transfer, getString(R.string.Gift2ActionTransfer), false);
                        buttons[i].setOnClickListener(onTransferClick);
                        break;
                    case 1:
                        buttons[i].set(R.drawable.filled_crown_on, getString(R.string.Gift2ActionWear), false);
                        buttons[i].setOnClickListener(onWearClick);
                        break;
                    case 2:
                        buttons[i].set(R.drawable.filled_share, getString(R.string.Gift2ActionShare), false);
                        buttons[i].setOnClickListener(onShareClick);
                        break;
                }
                ScaleStateListAnimator.apply(buttons[i], .075f, 1.5f);
                buttonsLayout.addView(buttons[i], LayoutHelper.createLinear(0, 56, 1, Gravity.FILL, i == 0 ? 0 : 11, 0, 0, 0));
            }

            for (int i = 0; i < 3; ++i) {
                layout[i] = new LinearLayout(context);
                layout[i].setOrientation(LinearLayout.VERTICAL);
                addView(layout[i], layoutLayoutParams[i] = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 16, i == 2 ? 64 : 8 + 160 + 2, 16, 0));

                if (i == 2) {
                    userLayout = new FrameLayout(context);
                    layout[i].addView(userLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 104, Gravity.FILL));

                    avatarView = new BackupImageView(context);
                    avatarView.setRoundRadius(dp(30));
                    userLayout.addView(avatarView, LayoutHelper.createFrame(60, 60, Gravity.CENTER_VERTICAL | Gravity.LEFT, 1, 0, 0, 0));

                    titleView[i] = new LinkSpanDrawable.LinksTextView(context);
                    titleView[i].setTextColor(0xFFFFFFFF);
                    titleView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    titleView[i].setTypeface(AndroidUtilities.bold());
                    titleView[i].setSingleLine();
                    titleView[i].setEllipsize(TextUtils.TruncateAt.END);
                    userLayout.addView(titleView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 81, 30.33f, 36 + 4, 0));

                    subtitleView[i] = new LinkSpanDrawable.LinksTextView(context);
                    subtitleView[i].setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    subtitleView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    subtitleView[i].setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                    subtitleView[i].setLineSpacing(dp(2), 1f);
                    subtitleView[i].setDisablePaddingsOffsetY(true);
                    subtitleView[i].setSingleLine();
                    titleView[i].setEllipsize(TextUtils.TruncateAt.END);
                    userLayout.addView(subtitleView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 81, 57, 4, 0));

                } else {

                    titleView[i] = new LinkSpanDrawable.LinksTextView(context);
                    titleView[i].setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    titleView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    titleView[i].setTypeface(AndroidUtilities.bold());
                    titleView[i].setGravity(Gravity.CENTER);
                    layout[i].addView(titleView[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

                    if (i == 0) {
                        priceView = new LinkSpanDrawable.LinksTextView(context);
                        priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                        priceView.setTypeface(AndroidUtilities.bold());
                        priceView.setGravity(Gravity.CENTER);
                        priceView.setTextColor(Theme.getColor(Theme.key_color_green, resourcesProvider));
                        layout[i].addView(priceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 4));
                    }

                    subtitleView[i] = new LinkSpanDrawable.LinksTextView(context);
                    subtitleView[i].setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    subtitleView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    subtitleView[i].setGravity(Gravity.CENTER);
                    subtitleView[i].setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                    subtitleView[i].setLineSpacing(dp(2), 1f);
                    subtitleView[i].setDisablePaddingsOffsetY(true);
                    layout[i].addView(subtitleView[i], subtitleViewLayoutParams[i] = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
                    subtitleViewLayoutParams[i].topMargin = dp(i == 1 ? 7.33f : (backdrop[0] == null ? 9 : 5.66f));
                }

                if (i == 0) {
                    layout[i].addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 15, 0, 0));
                }
            }
            addView(imageLayout, LayoutHelper.createFrame(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

            closeView = new ImageView(context);
            closeView.setBackground(Theme.createCircleDrawable(dp(28), 0x24FFFFFF));
            closeView.setImageResource(R.drawable.msg_close);
            ScaleStateListAnimator.apply(closeView);
            addView(closeView, LayoutHelper.createFrame(28, 28, Gravity.RIGHT | Gravity.TOP, 0, 12, 12, 0));
            closeView.setOnClickListener(v -> dismiss.run());
            closeView.setVisibility(View.GONE);

            optionsView = new ImageView(context);
            optionsView.setImageResource(R.drawable.media_more);
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setBackground(Theme.createSelectorDrawable(0x20ffffff, Theme.RIPPLE_MASK_CIRCLE_20DP));
            ScaleStateListAnimator.apply(optionsView);
            addView(optionsView, LayoutHelper.createFrame(42, 42, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));
            optionsView.setOnClickListener(onMenuClick);
            optionsView.setVisibility(View.GONE);
        }

        public void setText(int page, CharSequence title, long amount, CharSequence subtitle) {
            titleView[page].setText(title);
            if (page == 0) {
                priceView.setTextColor(Theme.getColor(Theme.key_color_green, resourcesProvider));
                priceView.setText(replaceStarsWithPlain(LocaleController.formatNumber((int) amount, ' ') + " ⭐️", .8f));
                priceView.setVisibility(amount != 0 ? View.VISIBLE : View.GONE);
            }
            subtitleView[page].setText(subtitle);
            subtitleView[page].setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        }

        private PageTransition currentPage = new PageTransition(PAGE_INFO, PAGE_INFO, 1.0f);
        public void onSwitchPage(PageTransition p) {
            currentPage = p;
            for (int i = 0; i < layout.length; ++i) {
                layout[i].setAlpha(p.at(i));
            }
            closeView.setAlpha(Math.max(backdrop[0] != null ? p.at(PAGE_WEAR) : 0.0f, backdrop[1] != null ? p.at(PAGE_UPGRADE) : 0.0f));
            closeView.setVisibility(backdrop[0] != null && p.to == PAGE_WEAR || backdrop[1] != null && p.to == PAGE_UPGRADE ? View.VISIBLE : View.GONE);
            optionsView.setAlpha(lerp(false, backdrop[0] != null, p.at(PAGE_INFO)));
            optionsView.setVisibility(backdrop[0] != null && p.to == PAGE_INFO ? View.VISIBLE : View.GONE);
            final int black = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
            for (int i = 0; i < 2; ++i) {
                titleView[i].setTextColor(backdrop[Math.min(1, i)] == null ? black : 0xFFFFFFFF);
                subtitleView[i].setTextColor(i == 0 || i == 2 ? (backdrop[i] == null ? black : backdrop[i].text_color | 0xFF000000) : ColorUtils.blendARGB(backdrop[1] == null ? black : backdrop[1].text_color | 0xFF000000, backdrop[2] == null ? black : backdrop[2].text_color | 0xFF000000, toggleBackdrop));

                boolean changed;
                if (backdrop[i] != null) {
                    changed = dp(24 + 160) != layoutLayoutParams[i].topMargin || layout[i].getPaddingBottom() != dp(18);
                    if (changed) {
                        layout[i].setPadding(0, 0, 0, dp(18));
                        layoutLayoutParams[i].topMargin = dp(24 + 160);
                    }
                } else {
                    changed = dp(8 + 160 + 2) != layoutLayoutParams[i].topMargin || layout[i].getPaddingBottom() != dp(3);
                    if (changed) {
                        layout[i].setPadding(0, 0, 0, dp(3));
                        layoutLayoutParams[i].topMargin = dp(8 + 160 + 2);
                    }
                }
                subtitleViewLayoutParams[i].topMargin = dp(i == 1 ? 7.33f : (backdrop[0] == null ? 9 : 5.66f));
                if (changed) {
                    layout[i].setLayoutParams(layoutLayoutParams[i]);
                    subtitleView[i].setLayoutParams(subtitleViewLayoutParams[i]);
                }
            }
            subtitleView[2].setTextColor(backdrop[0] == null ? black : backdrop[0].text_color | 0xFF000000);
            imageView[0].setAlpha(currentPage.at(PAGE_INFO, PAGE_WEAR));
            imageView[1].setAlpha(p.at(1) * (1.0f - toggleBackdrop));
            imageView[2].setAlpha(p.at(1) * toggleBackdrop);
            imageLayout.setScaleX(lerp(1.0f, wearImageScale, p.at(PAGE_WEAR)));
            imageLayout.setScaleY(lerp(1.0f, wearImageScale, p.at(PAGE_WEAR)));
            imageLayout.setTranslationX(wearImageTx * p.at(PAGE_WEAR));
            imageLayout.setTranslationY(dp(16) * p.at(PAGE_UPGRADE) + wearImageTy * p.at(PAGE_WEAR));
            layout[2].setTranslationY(p.from != PAGE_WEAR || p.to != PAGE_WEAR ? -(layout[p.from == PAGE_WEAR ? p.to : p.from].getMeasuredHeight() - layout[2].getMeasuredHeight()) * (1.0f - p.at(PAGE_WEAR)) : 0);
            invalidate();
        }

        public void prepareSwitchPage(final PageTransition t) {
            if (t.from != t.to) {
                final RLottieDrawable fromAnimation = imageView[t.from].getImageReceiver().getLottieAnimation();
                final RLottieDrawable toAnimation = imageView[t.to].getImageReceiver().getLottieAnimation();
                if (toAnimation != null && fromAnimation != null) {
                    toAnimation.setProgress(fromAnimation.getProgress(), false);
                }
            }
        }

        private final TL_stars.starGiftAttributeBackdrop[] backdrop = new TL_stars.starGiftAttributeBackdrop[3];

        private ArrayList<TL_stars.StarGiftAttribute> sampleAttributes;
        private BagRandomizer<TL_stars.starGiftAttributeModel> models;
        private BagRandomizer<TL_stars.starGiftAttributePattern> patterns;
        private BagRandomizer<TL_stars.starGiftAttributeBackdrop> backdrops;

        private boolean hasLink;

        public void setGift(TL_stars.StarGift gift, boolean withButtons, boolean worn, boolean hasLink) {
            final int page = 0;
            if (gift instanceof TL_stars.TL_starGiftUnique) {
                backdrop[page] = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
                setPattern(page, findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class), false);
                subtitleView[page].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                buttonsLayout.setVisibility(withButtons ? View.VISIBLE : View.GONE);
                if (withButtons) {
                    buttons[1].set(worn ? R.drawable.filled_crown_off : R.drawable.filled_crown_on, getString(worn ? R.string.Gift2ActionWearOff : R.string.Gift2ActionWear), false);
                }
            } else {
                backdrop[page] = null;
                setPattern(page, null, false);
                subtitleView[page].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                buttonsLayout.setVisibility(View.GONE);
            }
            this.hasLink = hasLink;
            setBackdropPaint(page, backdrop[page]);
            setGiftImage(imageView[page].getImageReceiver(), gift, 160);
            onSwitchPage(currentPage);
        }

        public void setPreviewingAttributes(/* int page = 1, */ArrayList<TL_stars.StarGiftAttribute> sampleAttributes) {
            final int page = 1;

            this.sampleAttributes = sampleAttributes;
            models = new BagRandomizer(findAttributes(sampleAttributes, TL_stars.starGiftAttributeModel.class));
            patterns = new BagRandomizer(findAttributes(sampleAttributes, TL_stars.starGiftAttributePattern.class));
            backdrops = new BagRandomizer(findAttributes(sampleAttributes, TL_stars.starGiftAttributeBackdrop.class));

            subtitleView[page].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            buttonsLayout.setVisibility(View.GONE);

            toggleBackdrop = 0.0f;
            toggled = 0;
            setPattern(1, patterns.next(), true);
            setGiftImage(imageView[page].getImageReceiver(), models.next().document, 160);
            setBackdropPaint(1, backdrop[1] = backdrops.next());

            setGiftImage(imageView[page + 1].getImageReceiver(), models.getNext().document, 160);

            AndroidUtilities.cancelRunOnUIThread(checkToRotateRunnable);
            AndroidUtilities.runOnUIThread(checkToRotateRunnable, 2500);

            invalidate();
        }

        private TLObject wearPreviewObject;
        private float wearImageTx, wearImageTy, wearImageScale;
        public void setWearPreview(TLObject object) {
            wearPreviewObject = object;
            String title, subtitle;
            if (object instanceof TLRPC.User) {
                final TLRPC.User user = (TLRPC.User) object;
                title = UserObject.getUserName(user);
                subtitle = getString(R.string.Online);
            } else if (object instanceof TLRPC.Chat) {
                final TLRPC.Chat chat = (TLRPC.Chat) object;
                title = chat == null ? "" : chat.title;
                if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                    if (chat.participants_count > 1) {
                        subtitle = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                    } else {
                        subtitle = LocaleController.getString(R.string.DiscussChannel);
                    }
                } else {
                    if (chat.participants_count > 1) {
                        subtitle = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                    } else {
                        subtitle = LocaleController.getString(R.string.AccDescrGroup).toLowerCase();
                    }
                }
            } else {
                return;
            }

            final AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(object);
            avatarView.setForUserOrChat(object, avatarDrawable);
            titleView[PAGE_WEAR].setText(title);
            subtitleView[PAGE_WEAR].setText(subtitle);

            updateWearImageTranslation();
            onSwitchPage(currentPage);
        }

        private void updateWearImageTranslation() {
            wearImageScale = dpf2(33.33f) / dpf2(160.0f);
            wearImageTx = -imageLayout.getLeft() + dp(97) + Math.min(titleView[PAGE_WEAR].getPaint().measureText(titleView[PAGE_WEAR].getText().toString()) + dp(12), titleView[PAGE_WEAR].getWidth()) - dp(160 - 33.33f) / 2.0f;
            wearImageTy = -imageLayout.getTop() + dp(88.66f) - dp(160 - 33.33f) / 2.0f;
        }

        private ValueAnimator rotationAnimator;
        private final Runnable checkToRotateRunnable = () -> {
            BackupImageView imageView = this.imageView[1 + (1 - this.toggled)];
            if (imageView.getImageReceiver().hasImageLoaded()) {
                rotateAttributes();
            } else {
                AndroidUtilities.cancelRunOnUIThread(this.checkToRotateRunnable);
                AndroidUtilities.runOnUIThread(this.checkToRotateRunnable, 150);
            }
        };
        private void rotateAttributes() {
            if (currentPage == null || currentPage.to != PAGE_UPGRADE || !isAttachedToWindow()) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(checkToRotateRunnable);
            if (rotationAnimator != null) {
                rotationAnimator.cancel();
                rotationAnimator = null;
            }

            toggled = 1 - toggled;

            final RLottieDrawable fromAnimation = imageView[1 + (1 - toggled)].getImageReceiver().getLottieAnimation();
            final RLottieDrawable toAnimation = imageView[1 + toggled].getImageReceiver().getLottieAnimation();
            if (toAnimation != null && fromAnimation != null) {
                toAnimation.setProgress(fromAnimation.getProgress(), false);
            }

            models.next();
            setBackdropPaint(1 + toggled, backdrop[1 + toggled] = backdrops.next());
            setPattern(1, patterns.next(), true);
            animateSwitch();

            rotationAnimator = ValueAnimator.ofFloat(1.0f - toggled, toggled);
            rotationAnimator.addUpdateListener(anm -> {
                toggleBackdrop = (float) anm.getAnimatedValue();
                onSwitchPage(currentPage);
            });
            rotationAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    toggleBackdrop = toggled;
                    onSwitchPage(currentPage);
                    setGiftImage(imageView[1 + (1 - toggled)].getImageReceiver(), models.getNext().document, 160);
                    preloadPattern(patterns.getNext());

                    AndroidUtilities.cancelRunOnUIThread(checkToRotateRunnable);
                    AndroidUtilities.runOnUIThread(checkToRotateRunnable, 2500);
                }
            });
            rotationAnimator.setDuration(320);
            rotationAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            rotationAnimator.start();
        }

        private final Paint[] backgroundPaint = new Paint[3];
        private final RadialGradient[] backgroundGradient = new RadialGradient[3];
        private final Matrix[] backgroundMatrix = new Matrix[3];
        private LinearGradient profileBackgroundGradient;
        private final Matrix profileBackgroundMatrix = new Matrix();
        private Paint profileBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable[] pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable[2];
        {
            for (int i = 0; i < backgroundPaint.length; ++i) {
                backgroundPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            for (int i = 0; i < pattern.length; ++i) {
                pattern[i] = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(28));
            }
        }

        private int toggled;
        private float toggleBackdrop;

        private void setBackdropPaint(int p, TL_stars.starGiftAttributeBackdrop backdrop) {
            if (backdrop == null) return;
            backgroundGradient[p] = new RadialGradient(0, 0, dp(200), new int[] { backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
            if (p == 0) {
                profileBackgroundGradient = new LinearGradient(0, 0, 0, dp(168), new int[] { backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
                profileBackgroundPaint.setShader(profileBackgroundGradient);
            }
            if (backgroundMatrix[p] == null) {
                backgroundMatrix[p] = new Matrix();
            }
            backgroundPaint[p].setShader(backgroundGradient[p]);
        }

        private void setPattern(int p, TL_stars.starGiftAttributePattern pattern, boolean animated) {
            if (pattern == null) return;
            this.pattern[p].set(pattern.document, animated);
        }

        private void preloadPattern(TL_stars.starGiftAttributePattern pattern) {
            if (pattern == null) return;
            AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS, pattern.document).preload();
        }

        private boolean attached;
        @Override
        protected void onAttachedToWindow() {
            attached = true;
            super.onAttachedToWindow();
            this.pattern[0].attach();
            this.pattern[1].attach();
        }

        @Override
        protected void onDetachedFromWindow() {
            attached = false;
            super.onDetachedFromWindow();
            this.pattern[0].detach();
            this.pattern[1].detach();
            AndroidUtilities.cancelRunOnUIThread(checkToRotateRunnable);
        }

        private float switchScale = 1;
        private ValueAnimator switchAnimator;
        private void animateSwitch() {
            if (switchAnimator != null) {
                switchAnimator.cancel();
                switchAnimator = null;
            }

            switchAnimator = ValueAnimator.ofFloat(0, 1);
            switchAnimator.addUpdateListener(anm -> {
                final float x = (float) anm.getAnimatedValue();
                switchScale = 1.0f + 0.6f * .125f * (float) Math.pow(2 * x - 2, 2) * x;
                imageLayout.setScaleX(switchScale);
                imageLayout.setScaleY(switchScale);
                invalidate();
            });
            switchAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    switchScale = 1;
                    imageLayout.setScaleX(switchScale);
                    imageLayout.setScaleY(switchScale);
                    invalidate();
                }
            });
            switchAnimator.setDuration(320);
            switchAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            switchAnimator.start();
        }

        private final RectF particlesBounds = new RectF();
        private StarsReactionsSheet.Particles particles;

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final float height = getRealHeight();

            canvas.save();
            canvas.clipRect(0, 0, getWidth(), height);

            final float cx = getWidth() / 2.0f;
            final float cy = lerp(dp(8), dp(24), currentPage.at(PAGE_UPGRADE)) + dp(160 / 2);

            final float infoBackdrop = currentPage.at(PAGE_INFO, PAGE_WEAR);
            if (infoBackdrop > 0 && backdrop[0] != null) {
                if (profileBackgroundGradient == null || currentPage.at(PAGE_WEAR) < 1) {
                    backgroundPaint[0].setAlpha((int) (0xFF * infoBackdrop));
                    backgroundMatrix[0].reset();
                    backgroundMatrix[0].postTranslate(cx, cy);
                    backgroundGradient[0].setLocalMatrix(backgroundMatrix[0]);
                    canvas.drawRect(0, 0, getWidth(), height, backgroundPaint[0]);
                }

                if (profileBackgroundGradient != null && currentPage.at(PAGE_WEAR) > 0) {
                    profileBackgroundPaint.setAlpha((int) (0xFF * currentPage.at(PAGE_WEAR)));
                    profileBackgroundMatrix.reset();
                    profileBackgroundMatrix.postTranslate(0, 0);
                    profileBackgroundGradient.setLocalMatrix(profileBackgroundMatrix);
                    canvas.drawRect(0, 0, getWidth(), height, profileBackgroundPaint);
                }

                final int patternColor = backdrop[0].pattern_color | 0xFF000000;
                if (currentPage.at(PAGE_INFO) > 0) {
                    canvas.save();
                    canvas.translate(cx, cy);
                    pattern[0].setColor(patternColor);
                    StarGiftPatterns.drawPattern(canvas, pattern[0], getWidth(), height, currentPage.at(PAGE_INFO), 1.0f);
                    canvas.restore();
                }
                if (currentPage.at(PAGE_WEAR) > 0) {
                    canvas.save();
                    pattern[0].setColor(patternColor);
                    StarGiftPatterns.drawProfilePattern(canvas, pattern[0], getWidth(), height, currentPage.at(PAGE_WEAR), 1.0f);
                    canvas.restore();
                }

                if (patternColor != buttonsBackgroundColor) {
                    for (Button btn : buttons) {
                        btn.setBackground(Theme.createRadSelectorDrawable(ColorUtils.blendARGB(backdrop[0].edge_color | 0xFF000000, buttonsBackgroundColor = patternColor, .25f), 0x10FFFFFF, 10, 10));
                    }
                }

                if (currentPage.at(PAGE_WEAR) > 0) {
                    if (particles == null) {
                        particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 12);
                    }
                    final float imageCx = imageLayout.getX() + imageLayout.getMeasuredWidth() / 2.0f, imageHw = imageLayout.getMeasuredWidth() * imageLayout.getScaleX() / 2.0f;
                    final float imageCy = imageLayout.getY() + imageLayout.getMeasuredHeight() / 2.0f, imageHh = imageLayout.getMeasuredHeight() * imageLayout.getScaleY() / 2.0f;
                    particlesBounds.set(imageCx - imageHw, imageCy - imageHh, imageCx + imageHw, imageCy + imageHh);
                    particles.setBounds(particlesBounds);
                    particles.process();
                    particles.draw(canvas, Theme.multAlpha(0xFFFFFFFF, currentPage.at(PAGE_WEAR)));
                    invalidate();
                }
            }

            if (currentPage.at(PAGE_UPGRADE) > 0) {
                if (toggled == 0) {
                    if (toggleBackdrop > 0.0f && backdrop[2] != null) {
                        backgroundPaint[2].setAlpha((int) (0xFF * currentPage.at(PAGE_UPGRADE)));
                        backgroundMatrix[2].reset();
                        backgroundMatrix[2].postTranslate(cx, cy);
                        backgroundGradient[2].setLocalMatrix(backgroundMatrix[2]);
                        canvas.drawRect(0, 0, getWidth(), height, backgroundPaint[2]);
                    }
                    if (toggleBackdrop < 1.0f && backdrop[1] != null) {
                        backgroundPaint[1].setAlpha((int) (0xFF * currentPage.at(PAGE_UPGRADE) * (1.0f - toggleBackdrop)));
                        backgroundMatrix[1].reset();
                        backgroundMatrix[1].postTranslate(cx, cy);
                        backgroundGradient[1].setLocalMatrix(backgroundMatrix[1]);
                        canvas.drawRect(0, 0, getWidth(), height, backgroundPaint[1]);
                    }
                } else {
                    if (toggleBackdrop < 1.0f && backdrop[1] != null) {
                        backgroundPaint[1].setAlpha((int) (0xFF * currentPage.at(PAGE_UPGRADE)));
                        backgroundMatrix[1].reset();
                        backgroundMatrix[1].postTranslate(cx, cy);
                        backgroundGradient[1].setLocalMatrix(backgroundMatrix[1]);
                        canvas.drawRect(0, 0, getWidth(), height, backgroundPaint[1]);
                    }
                    if (toggleBackdrop > 0.0f && backdrop[2] != null) {
                        backgroundPaint[2].setAlpha((int) (0xFF * currentPage.at(PAGE_UPGRADE) * toggleBackdrop));
                        backgroundMatrix[2].reset();
                        backgroundMatrix[2].postTranslate(cx, cy);
                        backgroundGradient[2].setLocalMatrix(backgroundMatrix[2]);
                        canvas.drawRect(0, 0, getWidth(), height, backgroundPaint[2]);
                    }
                }

                canvas.save();
                canvas.translate(cx, cy);
                final int patternColor = ColorUtils.blendARGB(backdrop[1] == null ? 0 : backdrop[1].pattern_color | 0xFF000000, backdrop[2] == null ? 0 : backdrop[2].pattern_color | 0xFF000000, toggleBackdrop);
                pattern[1].setColor(patternColor);
                StarGiftPatterns.drawPattern(canvas, pattern[1], getWidth(), getRealHeight(), currentPage.at(PAGE_UPGRADE), switchScale);
                canvas.restore();
            }

            super.dispatchDraw(canvas);
            canvas.restore();
        }

        public float getRealHeight() {
            float h = 0;
            h += (dp(backdrop[0] != null ? 24 : 10) + dp(160) + layout[0].getMeasuredHeight()) * currentPage.at(PAGE_INFO);
            h += (dp(backdrop[1] != null ? 24 : 10) + dp(160) + layout[1].getMeasuredHeight()) * currentPage.at(PAGE_UPGRADE);
            h += (dp(64) + layout[2].getMeasuredHeight()) * currentPage.at(PAGE_WEAR);
            return h;
        }

        public int getFinalHeight() {
            if (currentPage.to(PAGE_INFO)) {
                return dp(backdrop[0] != null ? 24 : 10) + dp(160) + layout[0].getMeasuredHeight();
            }
            if (currentPage.to(PAGE_UPGRADE)) {
                return dp(backdrop[1] != null ? 24 : 10) + dp(160) + layout[1].getMeasuredHeight();
            }
            if (currentPage.to(PAGE_WEAR)) {
                return dp(64) + layout[2].getMeasuredHeight();
            }
            return 0;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (currentPage.contains(PAGE_WEAR)) {
                updateWearImageTranslation();
                onSwitchPage(currentPage);
            }
        }
    }

    public static final int PAGE_INFO = 0;
    public static final int PAGE_UPGRADE = 1;
    public static final int PAGE_WEAR = 2;

    private Float lastTop;
    private PageTransition currentPage = new PageTransition(PAGE_INFO, PAGE_INFO, 1.0f);
    private ValueAnimator switchingPagesAnimator;
    public void switchPage(int page, boolean animated) {
        switchPage(page, animated, null);
    }
    public void switchPage(int page, boolean animated, Runnable done) {
        if (switchingPagesAnimator != null) {
            switchingPagesAnimator.cancel();
            switchingPagesAnimator = null;
        }

        if (page != PAGE_UPGRADE) {
            AndroidUtilities.cancelRunOnUIThread(topView.checkToRotateRunnable);
        }

        if (!firstSet) {
            lastTop = container.top();
        }
        currentPage = new PageTransition(currentPage == null ? PAGE_INFO : currentPage.to, page, 0.0f);
        adapter.setHeights(topView.getFinalHeight(), getBottomView().getMeasuredHeight());
        if (animated) {
            infoLayout.setVisibility(currentPage.contains(PAGE_INFO) ? View.VISIBLE : View.GONE);
            upgradeLayout.setVisibility(currentPage.contains(PAGE_UPGRADE) ? View.VISIBLE : View.GONE);
            wearLayout.setVisibility(currentPage.contains(PAGE_WEAR) ? View.VISIBLE : View.GONE);
            switchingPagesAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
            switchingPagesAnimator.addUpdateListener(a -> {
                currentPage.setProgress((float) a.getAnimatedValue());
                onSwitchedPage();
            });
            switchingPagesAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onSwitchedPage();
                    infoLayout.setVisibility(page == PAGE_INFO ? View.VISIBLE : View.GONE);
                    upgradeLayout.setVisibility(page == PAGE_UPGRADE ? View.VISIBLE : View.GONE);
                    wearLayout.setVisibility(page == PAGE_WEAR ? View.VISIBLE : View.GONE);
                    switchingPagesAnimator = null;
                    if (done != null) {
                        done.run();
                    }
                }
            });
            switchingPagesAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            switchingPagesAnimator.setDuration(320);
            switchingPagesAnimator.start();
            topView.prepareSwitchPage(currentPage);
        } else {
            currentPage.setProgress(1.0f);
            onSwitchedPage();
            infoLayout.setVisibility(page == PAGE_INFO ? View.VISIBLE : View.GONE);
            upgradeLayout.setVisibility(page == PAGE_UPGRADE ? View.VISIBLE : View.GONE);
            wearLayout.setVisibility(page == PAGE_WEAR ? View.VISIBLE : View.GONE);
            if (done != null) {
                done.run();
            }
        }

        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }
    }

    private View getBottomView() {
        if (currentPage.to(PAGE_UPGRADE)) return upgradeLayout;
        if (currentPage.to(PAGE_WEAR)) return wearLayout;
        return infoLayout;
    }

    private void onSwitchedPage() {
        infoLayout.setAlpha(currentPage.at(PAGE_INFO));
        upgradeLayout.setAlpha(currentPage.at(PAGE_UPGRADE));
        wearLayout.setAlpha(currentPage.at(PAGE_WEAR));
        topView.onSwitchPage(currentPage);
        container.updateTranslations();
        container.invalidate();
    }

    public boolean canTransfer() {
        if (getInputStarGift() == null) return false;
        TL_stars.TL_starGiftUnique gift;
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
            TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            if ((action.flags & 16) == 0) {
                return false;
            }
            if (!(action.gift instanceof TL_stars.TL_starGiftUnique)) {
                return false;
            }
            gift = (TL_stars.TL_starGiftUnique) action.gift;
        } else if (savedStarGift != null && savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
            gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
        } else if (slugStarGift != null) {
            gift = slugStarGift;
        } else {
            return false;
        }
        return isMineWithActions(currentAccount, DialogObject.getPeerDialogId(gift.owner_id));
    }

    private void addAttributeRow(TL_stars.StarGiftAttribute attr) {
        String name;
        if (attr instanceof TL_stars.starGiftAttributeModel) {
            name = getString(R.string.Gift2AttributeModel);
        } else if (attr instanceof TL_stars.starGiftAttributeBackdrop) {
            name = getString(R.string.Gift2AttributeBackdrop);
        } else if (attr instanceof TL_stars.starGiftAttributePattern) {
            name = getString(R.string.Gift2AttributeSymbol);
        } else return;
        final ButtonSpan.TextViewButtons[] textView = new ButtonSpan.TextViewButtons[1];
        TableRow row = tableView.addRow(name, attr.name, percents(attr.rarity_permille), () -> showHint(LocaleController.formatString(R.string.Gift2RarityHint, percents(attr.rarity_permille)), textView[0], false));
        textView[0] = (ButtonSpan.TextViewButtons) ((TableView.TableRowContent) row.getChildAt(1)).getChildAt(0);
    }

    private ColoredImageSpan upgradeIconSpan;
    private boolean firstSet = true;

    public StarGiftSheet set(String slug, TL_stars.TL_starGiftUnique gift) {
        this.slug = slug;
        this.slugStarGift = gift;

        set(gift, true, false);

        beforeTableTextView.setVisibility(View.GONE);
        final String owner_address = gift == null ? null : gift.owner_address;
        final String gift_address = gift == null ? null : gift.gift_address;
        if (!TextUtils.isEmpty(owner_address) && !TextUtils.isEmpty(gift_address)) {
            afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2InBlockchain), () -> {
                Browser.openUrlInSystemBrowser(getContext(), MessagesController.getInstance(currentAccount).tonBlockchainExplorerUrl + gift_address);
            }), true, dp(8f / 3f), dp(.66f)));
            afterTableTextView.setVisibility(View.VISIBLE);
        } else {
            afterTableTextView.setVisibility(View.GONE);
        }

        if (firstSet) {
            switchPage(PAGE_INFO, false);
            layoutManager.scrollToPosition(0);
            firstSet = false;
        }

        return this;
    }

    private View ownerTextView;
    public void set(TL_stars.TL_starGiftUnique gift, boolean canTransfer, boolean refunded) {
        final long owner_id = DialogObject.getPeerDialogId(gift.owner_id);

        title = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');

        topView.setGift(gift, isMineWithActions(currentAccount, owner_id), isWorn(currentAccount, getUniqueGift()), getLink() != null);
        topView.setText(0, gift.title, 0, LocaleController.formatPluralStringComma("Gift2CollectionNumber", gift.num));

        ownerTextView = null;
        tableView.clear();
        if (!refunded) {
            if (!TextUtils.isEmpty(gift.owner_address)) {
                tableView.addWalletAddressRow(getString(R.string.Gift2Owner), gift.owner_address, () -> {
                    getBulletinFactory().createSimpleBulletin(R.raw.copy, getString(R.string.WalletAddressCopied)).show(false);
                });
            } else if (owner_id == 0 && gift.owner_name != null) {
                tableView.addRow(getString(R.string.Gift2Owner), gift.owner_name);
            } else if (owner_id != 0) {
                TableRow row = tableView.addRowUserWithEmojiStatus(getString(R.string.Gift2Owner), currentAccount, owner_id, () -> openProfile(owner_id));
                ownerTextView = ((TableView.TableRowContent) row.getChildAt(1)).getChildAt(0);
            }
        }
        addAttributeRow(findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class));
        addAttributeRow(findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
        addAttributeRow(findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class));
        if (!refunded) {
            if (messageObject != null) {
                if (!messageObjectRepolled) {
                    final TableRow row = tableView.addRow(getString(R.string.Gift2Quantity), "");
                    final TextView rowTextView = (TextView) ((TableView.TableRowContent) row.getChildAt(1)).getChildAt(0);
                    final SpannableStringBuilder sb = new SpannableStringBuilder("x ");
                    final LoadingSpan span = new LoadingSpan(rowTextView, dp(90), 0, resourcesProvider);
                    span.setColors(
                            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .21f),
                            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .08f)
                    );
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    rowTextView.setText(sb, TextView.BufferType.SPANNABLE);

                    repollMessage();
                } else {
                    tableView.addRow(getString(R.string.Gift2Quantity), formatPluralStringComma("Gift2QuantityIssued1", gift.availability_issued) + formatPluralStringComma("Gift2QuantityIssued2", gift.availability_total));
                }
            } else {
                tableView.addRow(getString(R.string.Gift2Quantity), formatPluralStringComma("Gift2QuantityIssued1", gift.availability_issued) + formatPluralStringComma("Gift2QuantityIssued2", gift.availability_total));
            }
        }
        final TL_stars.starGiftAttributeOriginalDetails details = findAttribute(gift.attributes, TL_stars.starGiftAttributeOriginalDetails.class);
        if (details != null) {
            SpannableString from = null;
            if ((details.flags & 1) != 0) {
                final long sender_id = DialogObject.getPeerDialogId(details.sender_id);
                from = new SpannableString(DialogObject.getName(sender_id));
                from.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openProfile(sender_id);
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(ds.linkColor);
                    }
                }, 0, from.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            final long recipient_id = DialogObject.getPeerDialogId(details.recipient_id);
            SpannableString to = new SpannableString(DialogObject.getName(recipient_id));
            to.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    openProfile(recipient_id);
                }
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(ds.linkColor);
                }
            }, 0, to.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            CharSequence comment = null;
            if (details.message != null) {
                TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(dp(14));

                comment = new SpannableStringBuilder(details.message.text);
                MessageObject.addEntitiesToText(comment, details.message.entities, false, false, false, false);
                comment = Emoji.replaceEmoji(comment, textPaint.getFontMetricsInt(), false);
                comment = MessageObject.replaceAnimatedEmoji(comment, details.message.entities, textPaint.getFontMetricsInt());
            }
            CharSequence date = LocaleController.getInstance().getFormatterYear().format(details.date * 1000L).replaceAll("\\.", "/");
            TableView.TableRowFullContent tableRow;
            if (details.sender_id == details.recipient_id) {
                if (comment == null) {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetailsSelf, from, date));
                } else {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetailsSelfComment, from, date, comment));
                }
            } else if (from != null) {
                if (comment == null) {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetails, from, to, date));
                } else {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetailsComment, from, to, date, comment));
                }
            } else {
                if (comment == null) {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetailsNoSender, to, date));
                } else {
                    tableRow = tableView.addFullRow(formatSpannable(R.string.Gift2AttributeOriginalDetailsNoSenderComment, to, date, comment));
                }
            }
            tableRow.setFilled(true);
            SpoilersTextView textView = (SpoilersTextView) tableRow.getChildAt(0);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setGravity(Gravity.CENTER);
        }

        button.setText(getString(R.string.OK), !firstSet);
        button.setOnClickListener(v -> onBackPressed());

        actionBar.setTitle(getTitle());
    }

    public boolean isSaved() {
        if (messageObject != null && messageObject.messageOwner != null) {
            TLRPC.MessageAction _action = messageObject.messageOwner.action;
            if (_action instanceof TLRPC.TL_messageActionStarGift) {
                return ((TLRPC.TL_messageActionStarGift) _action).saved;
            } else if (_action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                return ((TLRPC.TL_messageActionStarGiftUnique) _action).saved;
            }
        } else if (savedStarGift != null) {
            return !savedStarGift.unsaved;
        }
        return false;
    }

    public StarGiftSheet set(TL_stars.SavedStarGift savedStarGift, StarsController.GiftsList list) {
        if (savedStarGift == null) {
            return this;
        }

        this.myProfile = isMine(currentAccount, dialogId);
        this.savedStarGift = savedStarGift;
        this.giftsList = list;
        this.messageObject = null;

        final String name = DialogObject.getShortName(dialogId);

        final long from_id = DialogObject.getPeerDialogId(savedStarGift.from_id);
        final boolean fromBot = UserObject.isBot(MessagesController.getInstance(currentAccount).getUser(from_id));
        final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - savedStarGift.date);
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final long fromId = (savedStarGift.flags & 2) != 0 ? from_id : UserObject.ANONYMOUS;
        final boolean isForChannel = dialogId < 0;
        final String owner_address, gift_address;
        final TLRPC.TL_textWithEntities message = savedStarGift.message;

        boolean refunded = savedStarGift.refunded, self = false;

        if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
            owner_address = savedStarGift.gift.owner_address;
            gift_address = savedStarGift.gift.gift_address;
            set((TL_stars.TL_starGiftUnique) savedStarGift.gift, (savedStarGift.flags & 256) != 0, refunded);
        } else {
            self = myProfile && selfId == fromId && dialogId >= 0;
            owner_address = null;
            gift_address = null;

            topView.setGift(savedStarGift.gift, false, isWorn(currentAccount, getUniqueGift()), getLink() != null);
            tableView.clear();
            if (self) {
                topView.setText(0, title = getString(R.string.Gift2TitleSaved), 0, refunded ? null :
                    savedStarGift.can_upgrade ? AndroidUtilities.replaceTags(getString(R.string.Gift2SelfInfoUpgrade)) :
                    savedStarGift.convert_stars > 0 ? AndroidUtilities.replaceTags(formatPluralStringComma("Gift2SelfInfoConvert", (int) savedStarGift.convert_stars)) :
                    AndroidUtilities.replaceTags(getString(R.string.Gift2SelfInfo))
                );
            } else if (isForChannel && !myProfile) {
                topView.setText(0, title = getString(R.string.Gift2TitleProfile), 0, null);
            } else if ((!myProfile || savedStarGift.can_upgrade) && savedStarGift.upgrade_stars > 0) {
                topView.setText(0,
                        title = getString(myProfile ? R.string.Gift2TitleReceived : R.string.Gift2TitleProfile), 0,
                    refunded ? null : myProfile ? getString(R.string.Gift2InfoInFreeUpgrade) : null
                );
            } else {
                topView.setText(
                    0,
                        title = getString(selfId == fromId && !isForChannel ? R.string.Gift2TitleSaved : myProfile ? R.string.Gift2TitleReceived : R.string.Gift2TitleProfile),
                    refunded || isForChannel ? 0 : Math.abs(Math.max(savedStarGift.gift.convert_stars, savedStarGift.convert_stars)),
                    refunded ? null :
                        TextUtils.concat(
                            AndroidUtilities.replaceTags(fromBot || !canConvert() ? (
                                myProfile ?
                                    savedStarGift.unsaved ? LocaleController.getString(isForChannel ? R.string.Gift2Info2ChannelKeep : R.string.Gift2Info2BotKeep) : LocaleController.getString(isForChannel ? R.string.Gift2Info2ChannelRemove : R.string.Gift2Info2BotRemove) :
                                    formatString(savedStarGift.can_upgrade && savedStarGift.upgrade_stars > 0 ? R.string.Gift2Info2OutUpgrade : R.string.Gift2Info2OutExpired, name)
                            ) : myProfile ?
                                formatPluralStringComma(within <= 0 ? (isForChannel ? "Gift2Info2ChannelExpired" : "Gift2Info2Expired") : (isForChannel ? "Gift2Info3Channel" : "Gift2Info3"), (int) savedStarGift.convert_stars) :
                                formatPluralStringComma("Gift2Info2Out", (int) savedStarGift.convert_stars, name)
                            ),
                            " ",
                            ((fromBot || !canConvert()) ? "" :
                                AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2More).replace(' ', ' '), () -> {
                                    new ExplainStarsSheet(getContext()).show();
                                }), true)
                            )
                        )
                );
            }

            if (selfId != fromId || isForChannel) {
                final TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
                tableView.addRowUser(getString(R.string.Gift2From), currentAccount, fromId, () -> openProfile(fromId), fromId != selfId && fromId != UserObject.ANONYMOUS && !fromBot && !UserObject.isDeleted(fromUser) && !isForChannel ? getString(R.string.Gift2ButtonSendGift) : null, () -> {
                    new GiftSheet(getContext(), currentAccount, fromId, this::dismiss).show();
                });
            }
            tableView.addRow(getString(R.string.StarsTransactionDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(savedStarGift.date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(savedStarGift.date * 1000L))));
            tableView.addRow(getString(R.string.Gift2Value), replaceStarsWithPlain(TextUtils.concat("⭐️ " + LocaleController.formatNumber(savedStarGift.gift.stars + savedStarGift.upgrade_stars, ','), " ", canConvert() && !refunded ? ButtonSpan.make(formatPluralStringComma("Gift2ButtonSell", (int) savedStarGift.convert_stars), this::convert, resourcesProvider) : ""), .8f));
            if (savedStarGift.gift.limited && !refunded) {
                addAvailabilityRow(tableView, currentAccount, savedStarGift.gift, resourcesProvider);
            }
            if (!refunded && savedStarGift.can_upgrade) {
                tableView.addRow(getString(R.string.Gift2Status), getString(R.string.Gift2StatusNonUnique), getString(R.string.Gift2StatusUpgrade), this::openUpgrade);
            }
            if (savedStarGift.message != null && !TextUtils.isEmpty(savedStarGift.message.text) && !refunded) {
                tableView.addFullRow(savedStarGift.message.text, savedStarGift.message.entities);
            }

            if (myProfile && savedStarGift.can_upgrade && savedStarGift.upgrade_stars > 0) {
                SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.Gift2UpgradeButtonFree));
                sb.append(" ^");
                if (upgradeIconSpan == null) {
                    upgradeIconSpan = new ColoredImageSpan(new UpgradeIcon(button, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
                }
                sb.setSpan(upgradeIconSpan, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                button.setText(sb, !firstSet);
                button.setOnClickListener(v -> openUpgrade());
            } else {
                button.setText(getString(R.string.OK), !firstSet);
                button.setOnClickListener(v -> onBackPressed());
            }
        }

        if (savedStarGift.refunded) {
            beforeTableTextView.setVisibility(View.VISIBLE);
            beforeTableTextView.setText(getString(R.string.Gift2Refunded));
            beforeTableTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        } else if (TextUtils.isEmpty(owner_address) && TextUtils.isEmpty(gift_address) && myProfile && savedStarGift.gift instanceof TL_stars.TL_starGift && savedStarGift.name_hidden) {
            beforeTableTextView.setVisibility(View.VISIBLE);
            beforeTableTextView.setText(getString(
                (message != null && !TextUtils.isEmpty(message.text)) ? R.string.Gift2InSenderMessageHidden2 : R.string.Gift2InSenderHidden2)
            );
            beforeTableTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        } else {
            beforeTableTextView.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(owner_address) && !TextUtils.isEmpty(gift_address)) {
            afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2InBlockchain), () -> {
                Browser.openUrlInSystemBrowser(getContext(), MessagesController.getInstance(currentAccount).tonBlockchainExplorerUrl + gift_address);
            }), true, dp(8f / 3f), dp(.66f)));
            afterTableTextView.setVisibility(View.VISIBLE);
        } else if (myProfile && isMine(currentAccount, dialogId)) {
            if (dialogId >= 0) {
                afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(!savedStarGift.unsaved ? R.string.Gift2ProfileVisible3 : R.string.Gift2ProfileInvisible3), this::toggleShow), true, dp(8f / 3f), dp(.66f)));
            } else {
                afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(!savedStarGift.unsaved ? R.string.Gift2ChannelProfileVisible3 : R.string.Gift2ChannelProfileInvisible3), this::toggleShow), true, dp(8f / 3f), dp(.66f)));
            }
            afterTableTextView.setVisibility(View.VISIBLE);
        } else {
            afterTableTextView.setVisibility(View.GONE);
        }

        if (firstSet) {
            switchPage(PAGE_INFO, false);
            layoutManager.scrollToPosition(0);
            firstSet = false;
        }

        actionBar.setTitle(getTitle());
        updateViewPager();

        return this;
    }

    public TL_stars.TL_starGiftUnique getUniqueGift() {
        final TL_stars.StarGift gift = getGift();
        if (gift instanceof TL_stars.TL_starGiftUnique) {
            return (TL_stars.TL_starGiftUnique) gift;
        }
        return null;
    }

    public String getGiftName() {
        final TL_stars.StarGift gift = getGift();
        if (gift instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.TL_starGiftUnique uniqueGift = (TL_stars.TL_starGiftUnique) gift;
            return uniqueGift.title + " #" + LocaleController.formatNumber(uniqueGift.num, ',');
        }
        return "";
    }

    public static String getGiftName(TL_stars.StarGift gift) {
        if (gift instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.TL_starGiftUnique uniqueGift = (TL_stars.TL_starGiftUnique) gift;
            return uniqueGift.title + " #" + LocaleController.formatNumber(uniqueGift.num, ',');
        }
        return "";
    }

    public TL_stars.StarGift getGift() {
        if (messageObject != null) {
            if (messageObject.messageOwner == null) return null;
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                return ((TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action).gift;
            } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                return ((TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action).gift;
            }
        } else if (savedStarGift != null) {
            return savedStarGift.gift;
        } else if (slugStarGift != null) {
            return slugStarGift;
        }
        return null;
    }

    public StarGiftSheet set(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return this;
        }

        this.myProfile = false;
        this.savedStarGift = null;
        this.messageObject = messageObject;
        this.giftsList = null;
        boolean converted, saved, out, refunded, name_hidden;

        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final boolean self = messageObject.getDialogId() == selfId;
        TL_stars.StarGift stargift;
        TLRPC.TL_textWithEntities message;
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique && ((TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action).gift instanceof TL_stars.TL_starGift) {
            out = messageObject.isOutOwner();
            if (self) {
                out = false;
            }
            final int date = messageObject.messageOwner.date;
            boolean can_upgrade, upgraded;
            long convert_stars, upgrade_stars;
            TLRPC.Peer from_id, peer;
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                converted = action.converted;
                saved = action.saved;
                refunded = action.refunded;
                name_hidden = action.name_hidden;
                stargift = action.gift;
                can_upgrade = action.can_upgrade;
                convert_stars = action.convert_stars;
                upgrade_stars = action.upgrade_stars;
                message = action.message;
                upgraded = action.upgraded;
                from_id = action.from_id;
                peer = action.peer;
            } else {
                final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
                converted = false;
                saved = action.saved;
                refunded = action.refunded;
                name_hidden = false;
                stargift = action.gift;
                can_upgrade = false;
                convert_stars = 0;
                upgrade_stars = 0;
                message = null;
                upgraded = true;
                from_id = action.from_id;
                peer = action.peer;
            }

            final String name = DialogObject.getShortName(dialogId);
            final boolean fromBot = UserObject.isBot(MessagesController.getInstance(currentAccount).getUser(dialogId));
            final boolean isForChannel = peer != null && DialogObject.getPeerDialogId(peer) < 0;

            topView.setGift(stargift, false, isWorn(currentAccount, getUniqueGift()), getLink() != null);
            if (self) {
                topView.setText(0, title = getString(R.string.Gift2TitleSaved), 0, refunded ? null :
                    can_upgrade ? AndroidUtilities.replaceTags(getString(R.string.Gift2SelfInfoUpgrade)) :
                    convert_stars > 0 ? AndroidUtilities.replaceTags(formatPluralStringComma(converted ? "Gift2SelfInfoConverted" : "Gift2SelfInfoConvert", (int) convert_stars)) :
                    AndroidUtilities.replaceTags(getString(R.string.Gift2SelfInfo))
                );
            } else if (isForChannel && !myProfile) {
                topView.setText(0, getString(R.string.Gift2TitleProfile), 0, null);
            } else if ((out || can_upgrade) && upgrade_stars > 0) {
                topView.setText(0,
                    title = getString(out ? R.string.Gift2TitleSent : R.string.Gift2TitleReceived), 0,
                    refunded ? null : !out ? getString(R.string.Gift2InfoInFreeUpgrade) : formatString(R.string.Gift2InfoFreeUpgrade, name)
                );
            } else {
                topView.setText(
                    0,
                    title = getString(out ? R.string.Gift2TitleSent : R.string.Gift2TitleReceived),
                    0,
                    refunded ? null : TextUtils.concat(
                        AndroidUtilities.replaceTags(fromBot || !canSomeoneConvert() ? (
                            out ?
                                formatString(can_upgrade && upgrade_stars > 0 ? R.string.Gift2Info2OutUpgrade : R.string.Gift2Info2OutExpired, name) :
                                getString(!saved ? (isForChannel ? R.string.Gift2Info2ChannelKeep : R.string.Gift2Info2BotKeep) : (isForChannel ? R.string.Gift2Info2ChannelRemove : R.string.Gift2Info2BotRemove))
                        ) : out ?
                            can_upgrade && upgrade_stars > 0 ? formatString(R.string.Gift2Info2OutUpgrade, name) : saved && !converted ? formatString(R.string.Gift2InfoOutPinned, name) : formatPluralStringComma(converted ? "Gift2InfoOutConverted" : "Gift2InfoOut", (int) convert_stars, name) :
                            formatPluralStringComma(converted ? (isForChannel ? "Gift2InfoChannelConverted" : "Gift2InfoConverted") : (isForChannel ? "Gift2Info3Channel" : "Gift2Info3"), (int) convert_stars)
                        ),
                        " ",
                        ((fromBot || !canConvert()) ? "" :
                            AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2More).replace(' ', ' '), () -> {
                                new ExplainStarsSheet(getContext()).show();
                            }), true)
                        )
                    )
                );
            }
            tableView.clear();
            final long fromId = from_id != null ? DialogObject.getPeerDialogId(from_id) : out ? selfId : dialogId;
            final long toId = peer != null ? DialogObject.getPeerDialogId(peer) : out ? dialogId : selfId;
            final TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
            if (fromId != selfId || isForChannel) {
                tableView.addRowUser(getString(R.string.Gift2From), currentAccount, fromId, () -> openProfile(fromId), fromId != selfId && fromId != UserObject.ANONYMOUS && !UserObject.isDeleted(fromUser) && !fromBot && !isForChannel ? getString(R.string.Gift2ButtonSendGift) : null, isForChannel ? null : () -> {
                    new GiftSheet(getContext(), currentAccount, fromId, this::dismiss).show();
                });
            }
            if (toId != selfId || isForChannel) {
                tableView.addRowUser(getString(R.string.Gift2To), currentAccount, toId, () -> openProfile(toId), null, isForChannel ? null : () -> {
                    new GiftSheet(getContext(), currentAccount, toId, this::dismiss).show();
                });
            }
            tableView.addRowDateTime(getString(R.string.StarsTransactionDate), date);
            if (stargift.stars > 0) {
                tableView.addRow(getString(R.string.Gift2Value), replaceStarsWithPlain(TextUtils.concat("⭐️ " + LocaleController.formatNumber(stargift.stars + upgrade_stars, ','), " ", canConvert() && !refunded ? ButtonSpan.make(formatPluralStringComma("Gift2ButtonSell", (int) convert_stars), this::convert, resourcesProvider) : ""), .8f));
            }
            if (stargift != null && stargift.limited && !refunded) {
                addAvailabilityRow(tableView, currentAccount, stargift, resourcesProvider);
            }
            if (!out && !refunded) {
                if (!messageObjectRepolled && !upgraded) {
                    final TableRow row = tableView.addRow(getString(R.string.Gift2Status), "");
                    final TextView rowTextView = (TextView) ((TableView.TableRowContent) row.getChildAt(1)).getChildAt(0);
                    final SpannableStringBuilder sb = new SpannableStringBuilder("x ");
                    final LoadingSpan span = new LoadingSpan(rowTextView, dp(90), 0, resourcesProvider);
                    span.setColors(
                        Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .21f),
                        Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .08f)
                    );
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    rowTextView.setText(sb, TextView.BufferType.SPANNABLE);

                    repollMessage();
                } else {
                    if (can_upgrade) {
                        SpannableStringBuilder ssb = new SpannableStringBuilder();
                        ssb.append(getString(R.string.Gift2StatusNonUnique));
                        ssb.append(" ");
                        ssb.append(ButtonSpan.make(getString(R.string.Gift2StatusUpgrade), this::openUpgrade, resourcesProvider));
                        tableView.addRow(getString(R.string.Gift2Status), ssb);
                    } else {
                        tableView.addRow(getString(R.string.Gift2Status), getString(R.string.Gift2StatusNonUnique));
                    }
                }
            }
            if (message != null && !TextUtils.isEmpty(message.text) && !refunded) {
                tableView.addFullRow(message.text, message.entities);
            }

            if (!out && can_upgrade && upgrade_stars > 0 && !refunded) {
                SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.Gift2UpgradeButtonFree));
                sb.append(" ^");
                if (upgradeIconSpan == null) {
                    upgradeIconSpan = new ColoredImageSpan(new UpgradeIcon(button, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
                }
                sb.setSpan(upgradeIconSpan, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                button.setText(sb, !firstSet);
                button.setOnClickListener(v -> openUpgrade());
            } else {
                button.setText(getString(R.string.OK), !firstSet);
                button.setOnClickListener(v -> onBackPressed());
            }
        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
            final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            if (!(action.gift instanceof TL_stars.TL_starGiftUnique)) {
                return this;
            }
            message = null;
            set((TL_stars.TL_starGiftUnique) action.gift, (action.flags & 16) != 0, refunded = action.refunded);
            converted = false;
            saved = action.saved;
            stargift = action.gift;
            out = (!action.upgrade == messageObject.isOutOwner());
            if (messageObject.getDialogId() == selfId) {
                out = false;
            }
            name_hidden = false;
            repollSavedStarGift();
        } else {
            return this;
        }

        final String owner_address = stargift == null ? null : stargift.owner_address;
        final String gift_address = stargift == null ? null : stargift.gift_address;

        if (refunded) {
            beforeTableTextView.setVisibility(View.VISIBLE);
            beforeTableTextView.setText(getString(R.string.Gift2Refunded));
            beforeTableTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        } else if (TextUtils.isEmpty(owner_address) && TextUtils.isEmpty(gift_address) && name_hidden && !self) {
            beforeTableTextView.setVisibility(View.VISIBLE);
            beforeTableTextView.setText(
                out ?
                    formatString((message != null && !TextUtils.isEmpty(message.text)) ? R.string.Gift2OutSenderMessageHidden2 : R.string.Gift2OutSenderHidden2, DialogObject.getShortName(messageObject.getDialogId())) :
                    getString((message != null && !TextUtils.isEmpty(message.text)) ? R.string.Gift2InSenderMessageHidden2 : R.string.Gift2InSenderHidden2)
            );
            beforeTableTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        } else {
            beforeTableTextView.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(owner_address) && !TextUtils.isEmpty(gift_address)) {
            afterTableTextView.setVisibility(View.VISIBLE);
            afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2InBlockchain), () -> {
                Browser.openUrlInSystemBrowser(getContext(), MessagesController.getInstance(currentAccount).tonBlockchainExplorerUrl + gift_address);
            }), true, dp(8f / 3f), dp(.66f)));
        } else if (!converted && !refunded && stargift != null && isMine(currentAccount, getDialogId())) {
            afterTableTextView.setVisibility(View.VISIBLE);
            if (getDialogId() >= 0) {
                afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(saved ? R.string.Gift2ProfileVisible3 : R.string.Gift2ProfileInvisible3), this::toggleShow), true, dp(8f / 3f), dp(.66f)));
            } else {
                afterTableTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(saved ? R.string.Gift2ChannelProfileVisible3 : R.string.Gift2ChannelProfileInvisible3), this::toggleShow), true, dp(8f / 3f), dp(.66f)));
            }
        } else {
            afterTableTextView.setVisibility(View.GONE);
        }

        if (firstSet) {
            switchPage(PAGE_INFO, false);
            layoutManager.scrollToPosition(0);
            firstSet = false;
        }

        actionBar.setTitle(getTitle());
        updateViewPager();

        return this;
    }

    private void repollMessage() {
        if (messageObjectRepolling || messageObjectRepolled || messageObject == null) {
            return;
        }
        messageObjectRepolling = true;
        final int id = messageObject.getId();
        final TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
        req.id.add(id);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            MessageObject newMessageObject = null;
            if (res instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) res;
                for (int i = 0; i < messages.messages.size(); ++i) {
                    final TLRPC.Message msg = messages.messages.get(i);
                    if (msg != null && msg.id == id && (msg.action instanceof TLRPC.TL_messageActionStarGift || msg.action instanceof TLRPC.TL_messageActionStarGiftUnique)) {
                        newMessageObject = new MessageObject(currentAccount, msg, false, false);
                        newMessageObject.setType();
                        break;
                    }
                }
            }
            if (newMessageObject != null) {
                final MessageObject msg = newMessageObject;
                AndroidUtilities.runOnUIThread(() -> {
                    final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) res;
                    MessagesController.getInstance(currentAccount).putUsers(messages.users, false);
                    MessagesController.getInstance(currentAccount).putChats(messages.chats, false);
                    messageObjectRepolled = true;
                    messageObjectRepolling = false;
                    if (unsavedFromSavedStarGift != null && msg != null && msg.messageOwner != null) {
                        if (msg.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                            ((TLRPC.TL_messageActionStarGift) msg.messageOwner.action).saved = !unsavedFromSavedStarGift;
                        } else if (msg.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                            ((TLRPC.TL_messageActionStarGiftUnique) msg.messageOwner.action).saved = !unsavedFromSavedStarGift;
                        }
                    }
                    set(msg);
                });
            }
        });
    }

    private Boolean unsavedFromSavedStarGift;
    private void repollSavedStarGift() {
        if (userStarGiftRepolling || userStarGiftRepolled || messageObject == null) return;
        final TL_stars.InputSavedStarGift inputSavedStarGift = getInputStarGift();
        if (inputSavedStarGift == null) return;
        userStarGiftRepolling = true;
        StarsController.getInstance(currentAccount).getUserStarGift(inputSavedStarGift, upgradedGift -> {
            userStarGiftRepolling = false;
            userStarGiftRepolled = true;
            if (upgradedGift != null) {
                unsavedFromSavedStarGift = upgradedGift.unsaved;
                if (messageObject != null && messageObject.messageOwner != null) {
                    if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                        TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
                        if (action.saved == !upgradedGift.unsaved) return;
                        action.saved = !upgradedGift.unsaved;
                    } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                        TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                        if (action.saved == !upgradedGift.unsaved) return;
                        action.saved = !upgradedGift.unsaved;
                    }
                    set(messageObject);
                }
            }
        });
    }

    private boolean isLearnMore;
    public void openAsLearnMore(long gift_id, String username) {
        isLearnMore = true;
        StarsController.getInstance(currentAccount).getStarGiftPreview(gift_id, preview -> {
            if (preview == null) return;

            topView.setPreviewingAttributes(preview.sample_attributes);
            switchPage(PAGE_UPGRADE, false);

            topView.setText(1, getString(R.string.Gift2LearnMoreTitle), 0, formatString(R.string.Gift2LearnMoreText, username));

            upgradeFeatureCells[0].setText(getString(R.string.Gift2UpgradeFeature1TextLearn));
            upgradeFeatureCells[1].setText(getString(R.string.Gift2UpgradeFeature2TextLearn));
            upgradeFeatureCells[2].setText(getString(R.string.Gift2UpgradeFeature3TextLearn));
            checkboxLayout.setVisibility(View.GONE);
            checkboxSeparator.setVisibility(View.GONE);

            button.setText(getString(R.string.OK), false);
            button.setOnClickListener(v -> dismiss());

            show();
        });
    }

    private long getDialogId() {
        if (messageObject != null) {
            if (messageObject.messageOwner == null) return 0;
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                if (action.peer != null) {
                    return DialogObject.getPeerDialogId(action.peer);
                }
                return messageObject.isOutOwner() ? messageObject.getDialogId() : UserConfig.getInstance(currentAccount).getClientUserId();
            } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
                if (action.gift instanceof TL_stars.TL_starGiftUnique && action.gift.owner_id != null) {
                    return DialogObject.getPeerDialogId(action.gift.owner_id);
                }
                if (action.peer != null) {
                    return DialogObject.getPeerDialogId(action.peer);
                }
            }
        } else if (savedStarGift != null) {
            if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                return DialogObject.getPeerDialogId(gift.owner_id);
            }
            return dialogId;
        } else if (slugStarGift != null) {
            if (slugStarGift instanceof TL_stars.TL_starGiftUnique) {
                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) slugStarGift;
                return DialogObject.getPeerDialogId(gift.owner_id);
            }
        }
        return 0;
    }

    private String getLink() {
        final TL_stars.StarGift starGift = getGift();
        if (starGift instanceof TL_stars.TL_starGiftUnique && starGift.slug != null) {
            return MessagesController.getInstance(currentAccount).linkPrefix + "/nft/" + starGift.slug;
        }
        return null;
    }

    private void openInProfile() {
        final long dialogId = getDialogId();
        if (dialogId == 0) return;
        openProfile(dialogId);
    }

    private void openProfile(long did) {
        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }
        dismiss();
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null) {
            if (UserObject.isService(did)) return;
            Bundle args = new Bundle();
            if (did > 0) {
                args.putLong("user_id", did);
                if (did == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    args.putBoolean("my_profile", true);
                }
            } else {
                args.putLong("chat_id", -did);
            }
            args.putBoolean("open_gifts", true);
            lastFragment.presentFragment(new ProfileActivity(args));
        }
    }

    private boolean canSomeoneConvert() {
        if (getInputStarGift() == null) return false;
        if (messageObject != null) {
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                final boolean isForChannel = action.peer != null;
                final boolean out = messageObject.isOutOwner();
                final boolean self = messageObject.getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId();
                final int date = messageObject.messageOwner.date;
                final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
                return (!isForChannel || action.peer != null && isMineWithActions(currentAccount, DialogObject.getPeerDialogId(action.peer))) && !action.converted && action.convert_stars > 0 && within > 0;
            }
        } else if (savedStarGift != null) {
            final int date = savedStarGift.date;
            final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
            return isMineWithActions(currentAccount, dialogId) && (savedStarGift.flags & (dialogId < 0 ? 2048 : 8)) != 0 && (savedStarGift.flags & 16) != 0 && (savedStarGift.flags & 2) != 0 && within > 0;
        }
        return false;
    }

    private boolean canConvert() {
        if (getInputStarGift() == null) return false;
        if (messageObject != null) {
            if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                final boolean isForChannel = action.peer != null;
                final boolean out = messageObject.isOutOwner();
                final boolean self = messageObject.getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId();
                final int date = messageObject.messageOwner.date;
                final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
                return (!isForChannel && (!out || self) || action.peer != null && isMineWithActions(currentAccount, DialogObject.getPeerDialogId(action.peer))) && !action.converted && action.convert_stars > 0 && within > 0;
            }
        } else if (savedStarGift != null) {
            final int date = savedStarGift.date;
            final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
            return isMineWithActions(currentAccount, dialogId) && (savedStarGift.flags & (dialogId < 0 ? 2048 : 8)) != 0 && (savedStarGift.flags & 16) != 0 && (savedStarGift.flags & 2) != 0 && within > 0;
        }
        return false;
    }

    private void convert() {
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final long fromId;
        final long dialogId;
        final long convert_stars;
        final int date;
        final TL_stars.InputSavedStarGift inputStarGift = getInputStarGift();
        if (inputStarGift == null) {
            return;
        }
        if (messageObject != null) {
            date = messageObject.messageOwner.date;
            final boolean out = messageObject.isOutOwner();
            if (messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                if (action.peer != null) {
                    dialogId = DialogObject.getPeerDialogId(action.peer);
                } else {
                    dialogId = out ? messageObject.getDialogId() : selfId;
                }
                if (action.from_id != null) {
                    fromId = DialogObject.getPeerDialogId(action.from_id);
                } else {
                    fromId = out ? selfId : messageObject.getDialogId();
                }
                convert_stars = action.convert_stars;
            } else {
                return;
            }
        } else if (savedStarGift != null) {
            date = savedStarGift.date;
            fromId = (savedStarGift.flags & 2) != 0 && !savedStarGift.name_hidden ? DialogObject.getPeerDialogId(savedStarGift.from_id) : UserObject.ANONYMOUS;
            convert_stars = savedStarGift.convert_stars;
            dialogId = this.dialogId;
        } else {
            return;
        }
        final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
        final int withinDays = Math.max(1, within / (60 * 60 * 24));
        new AlertDialog.Builder(getContext(), resourcesProvider)
            .setTitle(getString(R.string.Gift2ConvertTitle))
            .setMessage(AndroidUtilities.replaceTags(formatPluralString("Gift2ConvertText2", withinDays, UserObject.isService(fromId) || fromId == UserObject.ANONYMOUS ? getString(R.string.StarsTransactionHidden) : DialogObject.getShortName(fromId), formatPluralStringComma("Gift2ConvertStars", (int) convert_stars))))
            .setPositiveButton(getString(R.string.Gift2ConvertButton), (di, w) -> {
                final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(500);
                final TL_stars.convertStarGift req = new TL_stars.convertStarGift();
                req.stargift = inputStarGift;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismissUnless(400);
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment == null) return;
                    if (res instanceof TLRPC.TL_boolTrue) {
                        dismiss();
                        StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
                        if (dialogId >= 0) {
                            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(selfId);
                            if (userFull != null) {
                                userFull.stargifts_count = Math.max(0, userFull.stargifts_count - 1);
                                if (userFull.stargifts_count <= 0) {
                                    userFull.flags2 &= ~256;
                                }
                            }
                            StarsController.getInstance(currentAccount).invalidateBalance();
                            StarsController.getInstance(currentAccount).invalidateTransactions(true);
                            if (!(lastFragment instanceof StarsIntroActivity)) {
                                final StarsIntroActivity fragment = new StarsIntroActivity();
                                fragment.whenFullyVisible(() -> {
                                    BulletinFactory.of(fragment)
                                        .createSimpleBulletin(
                                            R.raw.stars_topup,
                                            LocaleController.getString(R.string.Gift2ConvertedTitle),
                                            LocaleController.formatPluralStringComma("Gift2Converted", (int) convert_stars)
                                        )
                                        .show(true);
                                });
                                lastFragment.presentFragment(fragment);
                            } else {
                                BulletinFactory.of(lastFragment)
                                    .createSimpleBulletin(
                                        R.raw.stars_topup,
                                        LocaleController.getString(R.string.Gift2ConvertedTitle),
                                        LocaleController.formatPluralStringComma("Gift2Converted", (int) convert_stars)
                                    )
                                    .show(true);
                            }
                        } else {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", -dialogId);
                            args.putBoolean("start_from_monetization", true);
                            final StatisticActivity fragment = new StatisticActivity(args);
                            BotStarsController.getInstance(currentAccount).invalidateStarsBalance(dialogId);
                            BotStarsController.getInstance(currentAccount).invalidateTransactions(dialogId, true);
                            fragment.whenFullyVisible(() -> {
                                BulletinFactory.of(fragment)
                                    .createSimpleBulletin(
                                        R.raw.stars_topup,
                                        LocaleController.getString(R.string.Gift2ConvertedTitle),
                                        LocaleController.formatPluralStringComma("Gift2ConvertedChannel", (int) convert_stars)
                                    )
                                    .show(true);
                            });
                            lastFragment.presentFragment(fragment);
                        }
                    } else if (err != null) {
                        getBulletinFactory().createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
                    } else {
                        getBulletinFactory().createErrorBulletin(getString(R.string.UnknownError)).show(false);
                    }
                }));
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .show();
    }

    private void toggleShow() {
        if (button.isLoading()) return;

        final boolean saved;
        final TLRPC.Document sticker;
        final TL_stars.InputSavedStarGift inputStarGift = getInputStarGift();
        if (messageObject != null && messageObject.messageOwner != null) {
            final TLRPC.MessageAction _action = messageObject.messageOwner.action;
            if (_action instanceof TLRPC.TL_messageActionStarGift) {
                final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                saved = action.saved;
                sticker = action.gift.getDocument();
            } else if (_action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
                saved = action.saved;
                sticker = action.gift.getDocument();
            } else {
                return;
            }
        } else if (savedStarGift != null) {
            saved = !savedStarGift.unsaved;
            sticker = savedStarGift.gift.getDocument();
        } else {
            return;
        }

        button.setLoading(true);

        final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
        final boolean unsave = req.unsave = saved;
        req.stargift = inputStarGift;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment == null) return;
            if (res instanceof TLRPC.TL_boolTrue) {
                dismiss();
                final long did = getDialogId();
                StarsController.getInstance(currentAccount).invalidateProfileGifts(did);
                if (did >= 0) {
                    BulletinFactory.of(lastFragment)
                        .createEmojiBulletin(
                            sticker,
                            LocaleController.getString(unsave ? R.string.Gift2MadePrivateTitle : R.string.Gift2MadePublicTitle),
                            AndroidUtilities.replaceSingleTag(LocaleController.getString(unsave ? R.string.Gift2MadePrivate : R.string.Gift2MadePublic), lastFragment instanceof ProfileActivity ? null : () -> {
                                final Bundle args = new Bundle();
                                if (did >= 0) {
                                    args.putLong("user_id", did);
                                } else {
                                    args.putLong("chat_id", -did);
                                }
                                args.putBoolean("my_profile", true);
                                args.putBoolean("open_gifts", true);
                                final ProfileActivity profileActivity = new ProfileActivity(args);
                                lastFragment.presentFragment(profileActivity);
                            })
                        )
                        .show(true);
                } else {
                    BulletinFactory.of(lastFragment)
                        .createEmojiBulletin(
                            sticker,
                            LocaleController.getString(unsave ? R.string.Gift2ChannelMadePrivateTitle : R.string.Gift2ChannelMadePublicTitle),
                            LocaleController.getString(unsave ? R.string.Gift2ChannelMadePrivate : R.string.Gift2ChannelMadePublic)
                        )
                        .show();
                }
            } else if (err != null) {
                getBulletinFactory().createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
            }
        }));
    }

    @Override
    public void show() {
        if (MessagesController.getInstance(currentAccount).isFrozen()) {
            AccountFrozenAlert.show(currentAccount);
            return;
        }
        if (slug != null && slugStarGift == null) {
            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(500);

            final TL_stars.getUniqueStarGift req = new TL_stars.getUniqueStarGift();
            req.slug = slug;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TL_stars.TL_payments_uniqueStarGift) {
                    final TL_stars.TL_payments_uniqueStarGift r = (TL_stars.TL_payments_uniqueStarGift) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    if (r.gift instanceof TL_stars.TL_starGiftUnique) {
                        AndroidUtilities.runOnUIThread(() -> {
                            slugStarGift = (TL_stars.TL_starGiftUnique) r.gift;
                            set(slugStarGift, true, false);
                            super.show();
                        });
                        return;
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        BulletinFactory.of(lastFragment)
                            .createSimpleBulletin(R.raw.error, getString(R.string.UniqueGiftNotFound))
                            .show();
                    }
                });
            });
        } else if (savedStarGift == null && messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
            final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
            if (action.upgraded) {
                if (action.upgrade_msg_id != 0) {
                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.showDelayed(500);

                    final TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                    req.id.add(action.upgrade_msg_id);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                        MessageObject newMessageObject = null;
                        if (res instanceof TLRPC.messages_Messages) {
                            final TLRPC.messages_Messages messages = (TLRPC.messages_Messages) res;
                            MessagesController.getInstance(currentAccount).putUsers(messages.users, false);
                            MessagesController.getInstance(currentAccount).putChats(messages.chats, false);
                            for (int i = 0; i < messages.messages.size(); ++i) {
                                final TLRPC.Message msg = messages.messages.get(i);
                                if (msg != null && !(msg instanceof TLRPC.TL_messageEmpty) && msg.id == action.upgrade_msg_id) {
                                    newMessageObject = new MessageObject(currentAccount, msg, false, false);
                                    newMessageObject.setType();
                                    break;
                                }
                            }
                        }
                        if (newMessageObject != null) {
                            final MessageObject msg = newMessageObject;
                            AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.dismiss();
                                messageObjectRepolled = true;
                                set(msg);
                                super.show();
                            });
                        } else {
                            AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.dismiss();
                                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment != null) {
                                    BulletinFactory.of(lastFragment)
                                        .createSimpleBulletin(R.raw.error, getString(R.string.MessageNotFound))
                                        .ignoreDetach()
                                        .show();
                                }
                            });
                        }
                    });
                    return;
                } else if (getInputStarGift() != null) {
                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.showDelayed(500);

                    StarsController.getInstance(currentAccount).getUserStarGift(getInputStarGift(), savedGift -> {
                        if (savedGift != null) {
                            progressDialog.dismiss();
                            userStarGiftRepolled = true;
                            set(savedGift, null);
                            super.show();
                        } else {
                            progressDialog.dismiss();
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment != null) {
                                BulletinFactory.of(lastFragment)
                                    .createSimpleBulletin(R.raw.error, getString(R.string.MessageNotFound))
                                    .ignoreDetach()
                                    .show();
                            }
                        }
                    });
                    return;
                }
            }
        }
        super.show();
    }

    private ArrayList<TL_stars.StarGiftAttribute> sample_attributes;
    private TLRPC.PaymentForm upgrade_form;
    private void openUpgrade() {
        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }
        if (switchingPagesAnimator != null) {
            return;
        }

        long stars;
        long gift_id;
        boolean name_hidden;
        boolean hasMessage;
        final TL_stars.InputSavedStarGift inputStarGift = getInputStarGift();
        if (inputStarGift == null) return;
        final boolean isForChannel;
        if (messageObject != null) {
            TLRPC.MessageAction _action = messageObject.messageOwner.action;
            if (_action instanceof TLRPC.TL_messageActionStarGift) {
                TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) _action;
                gift_id = action.gift.id;
                stars = action.upgrade_stars;
                name_hidden = action.name_hidden;
                hasMessage = action.message != null && !TextUtils.isEmpty(action.message.text);
                isForChannel = action.peer != null;
            } else {
                return;
            }
        } else if (savedStarGift != null) {
            gift_id = savedStarGift.gift.id;
            stars = savedStarGift.upgrade_stars;
            name_hidden = savedStarGift.gift instanceof TL_stars.TL_starGift && savedStarGift.name_hidden;
            hasMessage = savedStarGift.message != null && !TextUtils.isEmpty(savedStarGift.message.text);
            isForChannel = dialogId < 0;
        } else {
            return;
        }

        if (name_hidden) {
            checkboxTextView.setText(getString(isForChannel ? R.string.Gift2AddMyNameNameChannel : R.string.Gift2AddMyNameName));
        } else {
            if (hasMessage) {
                checkboxTextView.setText(getString(R.string.Gift2AddSenderNameComment));
            } else {
                checkboxTextView.setText(getString(R.string.Gift2AddSenderName));
            }
        }
        checkbox.setChecked(!name_hidden && stars > 0, false);

        if (sample_attributes == null || stars <= 0 && upgrade_form == null) {
            if (sample_attributes == null) {
                StarsController.getInstance(currentAccount).getStarGiftPreview(gift_id, preview -> {
                    if (preview == null) return;
                    sample_attributes = preview.sample_attributes;
                    openUpgradeAfter();
                });
            }

            if (stars <= 0 && upgrade_form == null) {
                final TLRPC.TL_inputInvoiceStarGiftUpgrade invoice = new TLRPC.TL_inputInvoiceStarGiftUpgrade();
                invoice.keep_original_details = checkbox.isChecked();
                invoice.stargift = inputStarGift;

                final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                req.invoice = invoice;
                final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
                if (themeParams != null) {
                    req.theme_params = new TLRPC.TL_dataJSON();
                    req.theme_params.data = themeParams.toString();
                    req.flags |= 1;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TLRPC.PaymentForm) {
                        TLRPC.PaymentForm form = (TLRPC.PaymentForm) res;
                        MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                        upgrade_form = form;
                        openUpgradeAfter();
                    } else {
                        getBulletinFactory().makeForError(err).ignoreDetach().show();
                    }
                }));
            }
        } else {
            openUpgradeAfter();
        }
    }

    private void openUpgradeAfter() {
        long stars;
        if (messageObject != null) {
            TLRPC.MessageAction action = messageObject.messageOwner.action;
            if (action instanceof TLRPC.TL_messageActionStarGift) {
                stars = ((TLRPC.TL_messageActionStarGift) action).upgrade_stars;
            } else {
                return;
            }
        } else if (savedStarGift != null) {
            stars = savedStarGift.upgrade_stars;
        } else {
            return;
        }

        if (sample_attributes == null || stars <= 0 && upgrade_form == null) {
            return;
        }

        long price = 0;
        if (upgrade_form != null) {
            for (int i = 0; i < upgrade_form.invoice.prices.size(); ++i) {
                price += upgrade_form.invoice.prices.get(i).amount;
            }
        }

        topView.setPreviewingAttributes(sample_attributes);
        topView.setText(1, getString(R.string.Gift2UpgradeTitle), 0, getString(R.string.Gift2UpgradeText));

        if (price > 0) {
            button.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.Gift2UpgradeButton, price)), true);
        } else {
            button.setText(getString(R.string.Confirm), true);
        }
        button.setOnClickListener(v -> doUpgrade());

        switchPage(PAGE_UPGRADE, true);
    }

    private boolean applyNewGiftFromUpdates(TLRPC.Updates updates) {
        if (updates == null) {
            return false;
        }
        TLRPC.TL_updateNewMessage upd = null;
        if (updates.update instanceof TLRPC.TL_updateNewMessage) {
            upd = (TLRPC.TL_updateNewMessage) updates.update;
        } else if (updates.updates != null) {
            for (int i = 0; i < updates.updates.size(); ++i) {
                final TLRPC.Update update = updates.updates.get(i);
                if (update instanceof TLRPC.TL_updateNewMessage) {
                    upd = (TLRPC.TL_updateNewMessage) update;
                    break;
                }
            }
        }

        if (upd != null) {
            savedStarGift = null;
            myProfile = false;
            final MessageObject messageObject = new MessageObject(currentAccount, upd.message, false, false);
            messageObject.setType();
            set(messageObject);
            return true;
        }
        return false;
    }

    private void doUpgrade() {
        if (button.isLoading()) return;

        long stars;
        final TL_stars.InputSavedStarGift inputStarGift = getInputStarGift();
        if (inputStarGift == null) {
            return;
        }
        if (messageObject != null) {
            TLRPC.MessageAction action = messageObject.messageOwner.action;
            if (action instanceof TLRPC.TL_messageActionStarGift) {
                stars = ((TLRPC.TL_messageActionStarGift) action).upgrade_stars;
            } else {
                return;
            }
        } else if (savedStarGift != null) {
            stars = savedStarGift.upgrade_stars;
        } else {
            return;
        }

        if (stars <= 0 && upgrade_form == null) {
            return;
        }

        button.setLoading(true);
        if (stars > 0) {
            final TL_stars.upgradeStarGift req = new TL_stars.upgradeStarGift();
            req.keep_original_details = checkbox.isChecked();
            req.stargift = inputStarGift;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    MessagesController.getInstance(currentAccount).putUsers(((TLRPC.Updates) res).users, false);
                    MessagesController.getInstance(currentAccount).putChats(((TLRPC.Updates) res).chats, false);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (err != null || !(res instanceof TLRPC.Updates)) {
                        getBulletinFactory()
                            .showForError(err);
                        return;
                    }

                    StarsController.getInstance(currentAccount).invalidateProfileGifts(getDialogId());

                    if (!applyNewGiftFromUpdates((TLRPC.Updates) res)) {
                        dismiss();
                        return;
                    }
                    button.setLoading(false);
                    fireworksOverlay.start(true);
                    switchPage(PAGE_INFO, true);

                    String product = "";
                    if (getGift() != null) {
                        product = getGift().title + " #" + LocaleController.formatNumber(getGift().num, ',');
                    }
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.gift_upgrade, getString(R.string.Gift2UpgradedTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2UpgradedText, product)))
                        .setDuration(Bulletin.DURATION_PROLONG)
                        .ignoreDetach()
                        .show();

                    Utilities.stageQueue.postRunnable(() -> {
                        MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                    });
                });
            });
        } else {

            StarsController s = StarsController.getInstance(currentAccount);
            if (!s.balanceAvailable()) {
                s.getBalance(() -> {
                    if (!s.balanceAvailable()) {
                        getBulletinFactory()
                            .createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, "NO_BALANCE"))
                            .ignoreDetach()
                            .show();
                        return;
                    }
                    button.setLoading(false);
                    doUpgrade();
                });
                return;
            }

            final TLRPC.TL_inputInvoiceStarGiftUpgrade invoice = new TLRPC.TL_inputInvoiceStarGiftUpgrade();
            invoice.keep_original_details = checkbox.isChecked();
            invoice.stargift = inputStarGift;

            final TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
            req2.form_id = upgrade_form.form_id;
            req2.invoice = invoice;

            long _formStars = 0;
            for (TLRPC.TL_labeledPrice price : upgrade_form.invoice.prices) {
                _formStars += price.amount;
            }
            final long formStars = _formStars;

            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                if (res2 instanceof TLRPC.TL_payments_paymentResult) {
                    TLRPC.TL_payments_paymentResult r = (TLRPC.TL_payments_paymentResult) res2;
                    MessagesController.getInstance(currentAccount).putUsers(r.updates.users, false);
                    MessagesController.getInstance(currentAccount).putChats(r.updates.chats, false);

                    StarsController.getInstance(currentAccount).invalidateTransactions(false);
                    StarsController.getInstance(currentAccount).invalidateProfileGifts(getDialogId());
                    StarsController.getInstance(currentAccount).invalidateBalance();

                    if (!applyNewGiftFromUpdates(r.updates)) {
                        dismiss();
                        return;
                    }
                    button.setLoading(false);
                    fireworksOverlay.start(true);
                    switchPage(PAGE_INFO, true);

                    String product = "";
                    if (getGift() != null) {
                        product = getGift().title + " #" + LocaleController.formatNumber(getGift().num, ',');
                    }
                    getBulletinFactory()
                        .createSimpleBulletin(R.raw.gift_upgrade, getString(R.string.Gift2UpgradedTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2UpgradedText, product)))
                        .setDuration(Bulletin.DURATION_PROLONG)
                        .ignoreDetach()
                        .show();

                    Utilities.stageQueue.postRunnable(() -> {
                        MessagesController.getInstance(currentAccount).processUpdates(r.updates, false);
                    });

                } else if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                    if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                        button.setLoading(false);
                        showNoSupportDialog(getContext(), resourcesProvider);
                        return;
                    }
                    StarsController.getInstance(currentAccount).invalidateBalance(() -> {
                        final boolean[] purchased = new boolean[] { false };
                        StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(getContext(), resourcesProvider, formStars, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_UPGRADE, null, () -> {
                            purchased[0] = true;
                            button.setLoading(false);
                            doUpgrade();
                        });
                        sheet.setOnDismissListener(d -> {
                            button.setLoading(false);
                        });
                        sheet.show();
                    });
                } else {
                    getBulletinFactory()
                        .showForError(err2);
                }
            }));

        }
    }

    public void openTransfer() {
        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }

        final TL_stars.TL_starGiftUnique gift;
        final int can_export_at;
        final long transfer_stars;
        if (savedStarGift != null && savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
            gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
            can_export_at = savedStarGift.can_export_at;
            transfer_stars = savedStarGift.transfer_stars;
        } else if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
            TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            if (!(action.gift instanceof TL_stars.TL_starGiftUnique)) {
                return;
            }
            gift = (TL_stars.TL_starGiftUnique) action.gift;
            can_export_at = action.can_export_at;
            transfer_stars = action.transfer_stars;
        } else {
            return;
        }
        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

        final UserSelectorBottomSheet[] sheet = new UserSelectorBottomSheet[1];
        sheet[0] = new UserSelectorBottomSheet(new BaseFragment() {
            @Override
            public Context getContext() {
                return StarGiftSheet.this.getContext();
            }
            @Override
            public Activity getParentActivity() {
                Activity activity = LaunchActivity.instance;
                if (activity == null) activity = AndroidUtilities.findActivity(StarGiftSheet.this.getContext());
                return activity;
            }
            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return StarGiftSheet.this.resourcesProvider;
            }
            @Override
            public boolean presentFragment(BaseFragment fragment) {
                sheet[0].dismiss();
                dismiss();
                BaseFragment baseFragment = LaunchActivity.getSafeLastFragment();
                if (baseFragment != null) {
                    return baseFragment.presentFragment(baseFragment);
                }
                return false;
            }
        }, 0, BirthdayController.getInstance(currentAccount).getState(), UserSelectorBottomSheet.TYPE_TRANSFER, true);
//        sheet[0].setTitle(LocaleController.formatString(R.string.Gift2Transfer, getGiftName()));
        sheet[0].setTitle(getString(R.string.Gift2TransferShort));
        final int days = now > can_export_at ? 0 : (int) Math.max(1, Math.round((float) Math.max(0, can_export_at - now) / (60 * 60 * 24f)));
        sheet[0].addTONOption(days);
        sheet[0].setOnUserSelector(dialogId -> {
            if (dialogId == -99) {
                if (now < can_export_at) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.Gift2ExportTONUnlocksAlertTitle))
                        .setMessage(formatPluralString("Gift2ExportTONUnlocksAlertText", Math.max(1, days)))
                        .setPositiveButton(getString(R.string.OK), null)
                        .show();
                } else {
                    final LinearLayout topView = new LinearLayout(getContext());
                    topView.setOrientation(LinearLayout.VERTICAL);
                    topView.addView(new GiftTransferTopView(getContext(), gift), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -4, 0, 0));
                    final TextView titleView = new TextView(getContext());
                    titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    titleView.setTypeface(AndroidUtilities.bold());
                    titleView.setText(getString(R.string.Gift2ExportTONFragmentTitle));
                    topView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 14));
                    final TextView textView = new TextView(getContext());
                    textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setText(AndroidUtilities.replaceTags(formatString(R.string.Gift2ExportTONFragmentText, getGiftName())));
                    topView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 0, 24, 4));
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setView(topView)
                        .setPositiveButton(getString(R.string.Gift2ExportTONFragmentOpen), (di, w) -> {
                            final Browser.Progress progress = di.makeButtonLoading(w);
                            final TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
                            passwordFragment.setDelegate(2, password -> initTONTransfer(password, passwordFragment));
                            passwordFragment.setDelegateString(getGiftName());
                            progress.init();
                            passwordFragment.preload(() -> {
                                sheet[0].dismiss();
                                progress.end();
                                presentFragment(passwordFragment);
                            });
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                }
                return;
            }
            final Runnable showAlert = () -> {
                openTransferAlert(dialogId, err -> sheet[0].dismiss());
            };

            if (dialogId < 0) {
                final TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                if (chatFull == null) {
                    final TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
                    req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_messages_chatFull) {
                            final TLRPC.TL_messages_chatFull r = (TLRPC.TL_messages_chatFull) res;
                            MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                            MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                            MessagesController.getInstance(currentAccount).putChatFull(r.full_chat);

                            if (!r.full_chat.stargifts_available) {
                                new AlertDialog.Builder(getContext(), resourcesProvider)
                                    .setTitle(getString(R.string.Gift2ChannelDoesntSupportGiftsTitle))
                                    .setMessage(getString(R.string.Gift2ChannelDoesntSupportGiftsText))
                                    .setPositiveButton(getString(R.string.OK), null)
                                    .show();
                                return;
                            }

                            showAlert.run();
                        } else {
                            getBulletinFactory().makeForError(err).ignoreDetach().show();
                        }
                    }));
                    return;
                }

                if (!chatFull.stargifts_available) {
                    new AlertDialog.Builder(getContext(), resourcesProvider)
                        .setTitle(getString(R.string.Gift2ChannelDoesntSupportGiftsTitle))
                        .setMessage(getString(R.string.Gift2ChannelDoesntSupportGiftsText))
                        .setPositiveButton(getString(R.string.OK), null)
                        .show();
                    return;
                }
            } else if (dialogId >= 0) {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                if (userFull != null && userFull.disallowed_stargifts != null && userFull.disallowed_stargifts.disallow_unique_stargifts) {
                    BulletinFactory.of(sheet[0].container, resourcesProvider).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
                    return;
                }

                if (userFull == null && user != null) {
                    final TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
                    req.id = MessagesController.getInstance(currentAccount).getInputUser(user);
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_users_userFull) {
                            final TLRPC.TL_users_userFull r = (TLRPC.TL_users_userFull) res;
                            MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                            MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                            if (r.full_user != null && r.full_user.disallowed_stargifts != null && r.full_user.disallowed_stargifts.disallow_unique_stargifts) {
                                BulletinFactory.of(sheet[0].container, resourcesProvider).createSimpleBulletin(R.raw.error, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserDisallowedGifts, DialogObject.getShortName(dialogId)))).show();
                                return;
                            }
                            showAlert.run();
                        } else {
                            getBulletinFactory().makeForError(err).ignoreDetach().show();
                        }
                    }));
                    return;
                }
            }
            showAlert.run();
        });
        sheet[0].show();
    }

    public void openTransferAlert(long dialogId, Utilities.Callback<TLRPC.TL_error> dismiss) {
        final TL_stars.TL_starGiftUnique gift;
        final int can_export_at;
        final long transfer_stars;
        if (savedStarGift != null && savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
            gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
            can_export_at = savedStarGift.can_export_at;
            transfer_stars = savedStarGift.transfer_stars;
        } else if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
            TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            if (!(action.gift instanceof TL_stars.TL_starGiftUnique)) {
                return;
            }
            gift = (TL_stars.TL_starGiftUnique) action.gift;
            can_export_at = action.can_export_at;
            transfer_stars = action.transfer_stars;
        } else {
            return;
        }

        final String name;
        final TLObject obj;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            name = UserObject.getForcedFirstName(user);
            obj = user;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            name = chat == null ? "" : chat.title;
            obj = chat;
        }
        LinearLayout topView = new LinearLayout(getContext());
        topView.setOrientation(LinearLayout.VERTICAL);
        topView.addView(new GiftTransferTopView(getContext(), gift, obj), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -4, 0, 0));
        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(AndroidUtilities.replaceTags(
            transfer_stars > 0 ?
                formatPluralString("Gift2TransferPriceText", (int) transfer_stars, getGiftName(), DialogObject.getShortName(dialogId)) :
                formatString(R.string.Gift2TransferText, getGiftName(), name)
        ));
        topView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 4));
        new AlertDialog.Builder(getContext(), resourcesProvider)
            .setView(topView)
            .setPositiveButton(transfer_stars > 0 ? replaceStars(formatString(R.string.Gift2TransferDoPrice, (int) transfer_stars)) : getString(R.string.Gift2TransferDo), (di, w) -> {
                final Browser.Progress progress = di.makeButtonLoading(w);
                progress.init();
                doTransfer(dialogId, err -> {
                    progress.end();
                    dismiss.run(err);
                    if (err != null) {
                        AndroidUtilities.runOnUIThread(() -> getBulletinFactory().showForError(err));
                        return;
                    }
                    dismiss();
                });
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .show();
    }

    private void initTONTransfer(TLRPC.InputCheckPasswordSRP password, TwoStepVerificationActivity passwordFragment) {
        TL_stars.getStarGiftWithdrawalUrl req = new TL_stars.getStarGiftWithdrawalUrl();
        req.stargift = getInputStarGift();
        if (req.stargift == null) {
            return;
        }
        req.password = password;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (getContext() == null) return;
            if (error != null) {
                if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(LocaleController.getString(R.string.Gift2TransferToTONAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(getContext());
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(getContext());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.Gift2TransferToTONAlertText)));
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(getContext());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(getContext());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getContext());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.Gift2TransferToTONAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(getContext());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(getContext());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getContext());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.Gift2TransferToTONAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString(R.string.Gift2TransferToTONSetPassword), (dialogInterface, i) -> presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(getContext());
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString(R.string.Gift2TransferToTONAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                    }
                    if (passwordFragment != null) {
                        passwordFragment.showDialog(builder.create());
                    } else {
                        builder.show();
                    }
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initTONTransfer(passwordFragment.getNewSrpPassword(), passwordFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                    BulletinFactory.showError(error);
                }
            } else {
                passwordFragment.needHideProgress();
                passwordFragment.finishFragment();
                if (response instanceof TL_stars.starGiftWithdrawalUrl) {
                    Browser.openUrlInSystemBrowser(getContext(), ((TL_stars.starGiftWithdrawalUrl) response).url);
                }
            }
        }));
    }

    private void presentFragment(BaseFragment fragment) {
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment == null) return;

        final BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
        params.transitionFromLeft = true;
        params.allowNestedScroll = false;
        lastFragment.showAsSheet(fragment, params);
    }

    private TL_stars.InputSavedStarGift getInputStarGift() {
        if (dialogId < 0) {
            final TL_stars.TL_inputSavedStarGiftChat stargift = new TL_stars.TL_inputSavedStarGiftChat();
            stargift.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            if (messageObject != null && messageObject.messageOwner != null) {
                if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift) {
                    final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
                    if ((action.flags & 4096) == 0) {
                        return null;
                    }
                    stargift.saved_id = action.saved_id;
                } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
                    final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
                    if ((action.flags & 128) == 0) {
                        return null;
                    }
                    stargift.saved_id = action.saved_id;
                } else return null;
            } else if (savedStarGift != null) {
                if ((savedStarGift.flags & 2048) == 0) {
                    return null;
                }
                stargift.saved_id = savedStarGift.saved_id;
            } else return null;
            return stargift;
        } else if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGift && (messageObject.messageOwner.action.flags & 4096) != 0) {
            final TLRPC.TL_messageActionStarGift action = (TLRPC.TL_messageActionStarGift) messageObject.messageOwner.action;
            final TL_stars.TL_inputSavedStarGiftChat stargift = new TL_stars.TL_inputSavedStarGiftChat();
            stargift.peer = MessagesController.getInstance(currentAccount).getInputPeer(action.peer);
            stargift.saved_id = action.saved_id;
            return stargift;
        } else if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique && (messageObject.messageOwner.action.flags & 128) != 0) {
            final TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            final TL_stars.TL_inputSavedStarGiftChat stargift = new TL_stars.TL_inputSavedStarGiftChat();
            stargift.peer = MessagesController.getInstance(currentAccount).getInputPeer(action.peer);
            stargift.saved_id = action.saved_id;
            return stargift;
        } else {
            final TL_stars.TL_inputSavedStarGiftUser stargift = new TL_stars.TL_inputSavedStarGiftUser();
            if (messageObject != null) {
                stargift.msg_id = messageObject.getId();
            } else if (savedStarGift != null) {
                stargift.msg_id = savedStarGift.msg_id;
            } else return null;
            return stargift;
        }
    }

    public void doTransfer(long dialogId, Utilities.Callback<TLRPC.TL_error> done) {
        final long transfer_stars;
        final TL_stars.InputSavedStarGift inputStarGift = getInputStarGift();
        final long fromDialogId;
        if (inputStarGift == null) return;
        if (savedStarGift != null && savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
            fromDialogId = this.dialogId;
            transfer_stars = savedStarGift.transfer_stars;
        } else if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.action instanceof TLRPC.TL_messageActionStarGiftUnique) {
            TLRPC.TL_messageActionStarGiftUnique action = (TLRPC.TL_messageActionStarGiftUnique) messageObject.messageOwner.action;
            fromDialogId = DialogObject.getPeerDialogId(action.gift.owner_id);
            transfer_stars = action.transfer_stars;
        } else {
            return;
        }

        if (transfer_stars <= 0) {
            final TL_stars.transferStarGift req = new TL_stars.transferStarGift();
            req.stargift = inputStarGift;
            req.to_id = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.Updates) {
                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (done != null) {
                        done.run(err);
                    }
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        if (res instanceof TLRPC.Updates) {
                            if (dialogId >= 0 && fromDialogId >= 0) {
                                ChatActivity chat = ChatActivity.of(dialogId);
                                chat.whenFullyVisible(() -> {
                                    BulletinFactory.of(chat)
                                        .createSimpleBulletin(R.raw.forward, getString(R.string.Gift2TransferredTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2TransferredText, getGiftName(), DialogObject.getShortName(dialogId))))
                                        .ignoreDetach()
                                        .show();
                                });
                                lastFragment.presentFragment(chat);
                            } else {
                                BulletinFactory.of(lastFragment)
                                    .createSimpleBulletin(R.raw.forward, getString(R.string.Gift2TransferredTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2TransferredText, getGiftName(), DialogObject.getShortName(dialogId))))
                                    .ignoreDetach()
                                    .show();
                            }
                        } else {
                            BulletinFactory.of(lastFragment).showForError(err);
                        }
                    }
                    StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
                    StarsController.getInstance(currentAccount).invalidateProfileGifts(fromDialogId);
                });
            });
        } else {

            StarsController s = StarsController.getInstance(currentAccount);
            if (!s.balanceAvailable()) {
                s.getBalance(() -> {
                    if (!s.balanceAvailable()) {
                        getBulletinFactory()
                            .createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, "NO_BALANCE"))
                            .ignoreDetach()
                            .show();
                        return;
                    }
                    doTransfer(dialogId, done);
                });
                return;
            }

            final TLRPC.TL_inputInvoiceStarGiftTransfer invoice = new TLRPC.TL_inputInvoiceStarGiftTransfer();
            invoice.stargift = inputStarGift;
            invoice.to_id = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);

            TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
            req.invoice = invoice;
            final JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
            if (themeParams != null) {
                req.theme_params = new TLRPC.TL_dataJSON();
                req.theme_params.data = themeParams.toString();
                req.flags |= 1;
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.PaymentForm) {
                    TLRPC.PaymentForm form = (TLRPC.PaymentForm) res;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);

                    TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
                    req2.form_id = form.form_id;
                    req2.invoice = invoice;

                    long _stars = 0;
                    for (TLRPC.TL_labeledPrice price : form.invoice.prices) {
                        _stars += price.amount;
                    }
                    final long stars = _stars;

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res2 instanceof TLRPC.TL_payments_paymentResult) {
                            TLRPC.TL_payments_paymentResult r = (TLRPC.TL_payments_paymentResult) res2;
                            MessagesController.getInstance(currentAccount).putUsers(r.updates.users, false);
                            MessagesController.getInstance(currentAccount).putChats(r.updates.chats, false);

                            StarsController.getInstance(currentAccount).invalidateTransactions(false);
                            StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
                            StarsController.getInstance(currentAccount).invalidateProfileGifts(fromDialogId);
                            StarsController.getInstance(currentAccount).invalidateBalance();
                            if (done != null) {
                                done.run(null);
                            }

                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment != null) {
                                if (dialogId >= 0 && fromDialogId >= 0) {
                                    ChatActivity chat = ChatActivity.of(dialogId);
                                    chat.whenFullyVisible(() -> {
                                        BulletinFactory.of(chat)
                                            .createSimpleBulletin(R.raw.forward, getString(R.string.Gift2TransferredTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2TransferredText, getGiftName(), DialogObject.getShortName(dialogId))))
                                            .ignoreDetach()
                                            .show();
                                    });
                                    lastFragment.presentFragment(chat);
                                } else {
                                    BulletinFactory.of(lastFragment)
                                        .createSimpleBulletin(R.raw.forward, getString(R.string.Gift2TransferredTitle), AndroidUtilities.replaceTags(formatString(R.string.Gift2TransferredText, getGiftName(), DialogObject.getShortName(dialogId))))
                                        .ignoreDetach()
                                        .show();
                                }
                            }

                            Utilities.stageQueue.postRunnable(() -> {
                                MessagesController.getInstance(currentAccount).processUpdates(r.updates, false);
                            });

                        } else if (err2 != null && "BALANCE_TOO_LOW".equals(err2.text)) {
                            if (!MessagesController.getInstance(currentAccount).starsPurchaseAvailable()) {
                                button.setLoading(false);
                                showNoSupportDialog(getContext(), resourcesProvider);
                                return;
                            }
                            StarsController.getInstance(currentAccount).invalidateBalance(() -> {
                                final boolean[] purchased = new boolean[]{false};
                                StarsIntroActivity.StarsNeededSheet sheet = new StarsIntroActivity.StarsNeededSheet(getContext(), resourcesProvider, stars, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_TRANSFER, null, () -> {
                                    purchased[0] = true;
                                    button.setLoading(false);
                                    doTransfer(dialogId, done);
                                });
                                sheet.setOnDismissListener(d -> {
                                    button.setLoading(false);
                                });
                                sheet.show();
                            });
                        } else {
                            if (done != null) {
                                done.run(err2);
                            }
                            getBulletinFactory().showForError(err2);
                        }
                    }));
                } else {
                    if (done != null) {
                        done.run(err);
                    }
                    getBulletinFactory().makeForError(err).ignoreDetach().show();
                }
            }));
        }
    }

    protected BulletinFactory getBulletinFactory() {
        return BulletinFactory.of(bottomBulletinContainer, resourcesProvider);
    }

    @Override
    public void onBackPressed() {
        if (!onlyWearInfo && currentPage.to > 0 && !button.isLoading() && !isLearnMore) {
            if (messageObject != null) {
                set(messageObject);
            } else if (savedStarGift != null) {
                set(savedStarGift, giftsList);
            }
            switchPage(PAGE_INFO, true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onSwipeStarts() {
        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }
    }

    private HintView2 currentHintView;
    private View currentHintViewTextView;
    public void showHint(CharSequence hintText, View textView, boolean onRightDrawable) {
        if (currentHintView != null && currentHintView.shown() && currentHintViewTextView == textView) {
            return;
        }
        if (textView == null) return;

        final float cx;
        if (onRightDrawable) {
            if (textView instanceof SimpleTextView) {
                cx = ((SimpleTextView) textView).getRightDrawableX() + ((SimpleTextView) textView).getRightDrawableWidth() / 2.0f;
            } else {
                return;
            }
        } else {
            final Layout layout;
            if (textView instanceof TextView) {
                layout = ((TextView) textView).getLayout();
            } else if (textView instanceof SimpleTextView) {
                layout = ((SimpleTextView) textView).getLayout();
            } else {
                return;
            }
            if (layout == null) return;
            final CharSequence text = layout.getText();
            if (!(text instanceof Spanned)) return;
            final Spanned spanned = (Spanned) text;

            final ButtonSpan[] buttons = spanned.getSpans(0, spanned.length(), ButtonSpan.class);
            if (buttons == null || buttons.length <= 0) return;

            final ButtonSpan span = buttons[buttons.length - 1];
            final int start = spanned.getSpanStart(span);
            cx = textView.getPaddingLeft() + layout.getPrimaryHorizontal(start) + span.getSize() / 2.0f;
        }

        final int[] loc = new int[2];
        final int[] loc2 = new int[2];
        textView.getLocationOnScreen(loc);
        container.getLocationOnScreen(loc2);
        loc[0] -= loc2[0];
        loc[1] -= loc2[1];

        if (currentHintView != null) {
            currentHintView.hide();
            currentHintView = null;
        }
        final HintView2 hintView = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
        hintView.setMultilineText(!onRightDrawable);
        hintView.setText(hintText);
        hintView.setJointPx(0, loc[0] + cx - (dp(16) + backgroundPaddingLeft));
        hintView.setTranslationY(loc[1] - dp(100) - textView.getHeight() / 2.0f + dp(4.33f + (onRightDrawable ? 18 : 0)));
        hintView.setDuration(3000);
        hintView.setPadding(dp(16) + backgroundPaddingLeft, 0, dp(16) + backgroundPaddingLeft, 0);
        hintView.setOnHiddenListener(() -> AndroidUtilities.removeFromParent(hintView));
        hintView.show();
        container.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100));
        currentHintView = hintView;
        currentHintViewTextView = textView;
    }

    public static class GiftTransferTopView extends View {

        private final StarGiftDrawableIcon giftDrawable;
        private final ImageReceiver userImageReceiver;

        private final Path arrowPath = new Path();
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GiftTransferTopView(Context context, TL_stars.StarGift gift, TLObject obj) {
            super(context);

            giftDrawable = new StarGiftDrawableIcon(this, gift, 60, .27f);
            giftDrawable.setPatternsType(StarGiftPatterns.TYPE_LINK_PREVIEW);
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(obj);
            userImageReceiver = new ImageReceiver(this);
            userImageReceiver.setRoundRadius(dp(30));
            userImageReceiver.setForUserOrChat(obj, avatarDrawable);

            arrowPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
            arrowPaint.setStrokeWidth(dp(2));

            arrowPath.rewind();
            arrowPath.moveTo(0, -dp(8));
            arrowPath.lineTo(dp(6.166f), 0);
            arrowPath.lineTo(0, dp(8));
        }

        public GiftTransferTopView(Context context, TL_stars.StarGift gift) {
            super(context);

            giftDrawable = new StarGiftDrawableIcon(this, gift, 60, .27f);
            giftDrawable.setPatternsType(StarGiftPatterns.TYPE_LINK_PREVIEW);
            userImageReceiver = new ImageReceiver(this);
            userImageReceiver.setRoundRadius(dp(30));
            userImageReceiver.setImageBitmap(SessionCell.createDrawable(60, "fragment"));

            arrowPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
            arrowPaint.setStrokeWidth(dp(2.33f));

            arrowPath.rewind();
            arrowPath.moveTo(0, -dp(8));
            arrowPath.lineTo(dp(6.166f), 0);
            arrowPath.lineTo(0, dp(8));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(100), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int width = dp(60 + 36 + 60);
            int left = getWidth() / 2 - width / 2, top = getHeight() / 2 - dp(30);
            giftDrawable.setBounds(left, top, left + dp(60), top + dp(60));
            giftDrawable.draw(canvas);

            canvas.save();
            canvas.translate(getWidth() / 2.0f - dp(6.166f) / 2.0f, getHeight() / 2.0f);
            canvas.drawPath(arrowPath, arrowPaint);
            canvas.restore();

            userImageReceiver.setImageCoords(left + dp(60 + 36), top, dp(60), dp(60));
            userImageReceiver.draw(canvas);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            userImageReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            userImageReceiver.onDetachedFromWindow();
        }
    }

    public static class StarGiftDrawableIcon extends CompatDrawable {

        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final int sizeDp;

        private final ImageReceiver imageReceiver;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable pattern;
        private RadialGradient gradient;
        private final Matrix matrix = new Matrix();

        private float patternsScale;

        public StarGiftDrawableIcon(View view, TL_stars.StarGift gift, int sizeDp, float patternsScale) {
            super(view);

            this.patternsScale = patternsScale;
            imageReceiver = new ImageReceiver(view);
            pattern = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(view, false, dp(sizeDp > 180 ? 24 : 18));
            this.sizeDp = sizeDp;

            if (gift != null) {
                final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
                final TL_stars.starGiftAttributePattern pattern = findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class);
                final TL_stars.starGiftAttributeModel model = findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class);

                if (pattern != null) {
                    this.pattern.set(pattern.document, false);
                }
                if (backdrop != null) {
                    gradient = new RadialGradient(0, 0, dpf2(sizeDp) / 2, new int[]{ backdrop.center_color | 0xFF000000, backdrop.edge_color | 0xFF000000 }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
                    this.pattern.setColor(backdrop.pattern_color | 0xFF000000);
                }
                if (model != null) {
                    setGiftImage(imageReceiver, model.document, (int) (sizeDp * .75f));
                }
            }
            paint.setShader(gradient);

            if (view.isAttachedToWindow()) {
                onAttachedToWindow();
            }
        }

        private int rounding = dp(16);
        public StarGiftDrawableIcon setRounding(int r) {
            this.rounding = r;
            return this;
        }

        private int patternsType = StarGiftPatterns.TYPE_DEFAULT;
        public StarGiftDrawableIcon setPatternsType(int type) {
            this.patternsType = type;
            return this;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());

            canvas.save();
            path.rewind();
            path.addRoundRect(rect, rounding, rounding, Path.Direction.CW);
            canvas.clipPath(path);

            if (gradient != null) {
                matrix.reset();
                matrix.postTranslate(rect.centerX(), rect.centerY());
                gradient.setLocalMatrix(matrix);
                paint.setShader(gradient);
            }
            canvas.drawPaint(paint);

            canvas.save();
            canvas.translate(rect.centerX(), rect.centerY());
            StarGiftPatterns.drawPattern(canvas, patternsType, pattern, rect.width(), rect.height(), 1.0f, patternsScale);
            canvas.restore();

            final float imageSize = Math.min(rect.width(), rect.height()) * 0.75f;
            imageReceiver.setImageCoords(rect.centerX() - imageSize / 2, rect.centerY() - imageSize / 2, imageSize, imageSize);
            imageReceiver.draw(canvas);

            canvas.restore();
        }

        @Override
        public void onAttachedToWindow() {
            pattern.attach();
            imageReceiver.onAttachedToWindow();
        }

        @Override
        public void onDetachedToWindow() {
            pattern.detach();
            imageReceiver.onDetachedFromWindow();
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(sizeDp);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(sizeDp);
        }
    }

    public static class UpgradeIcon extends CompatDrawable {

        private final View view;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private final long start = System.currentTimeMillis();

        public UpgradeIcon(View view, int color) {
            super(view);
            this.view = view;

            paint.setColor(0xFFFFFFFF);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor(color);

            arrow.rewind();
            arrow.moveTo(-dpf2(2.91f), dpf2(1.08f));
            arrow.lineTo(0, -dpf2(1.08f));
            arrow.lineTo(dpf2(2.91f), dpf2(1.08f));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawCircle(getBounds().centerX(), getBounds().centerY(), getBounds().width() / 2.0f, paint);

            final float t = (System.currentTimeMillis() - start) % 400 / 400.0f;

            strokePaint.setStrokeWidth(dpf2(1.33f));
            canvas.save();
            final float arrowsHeight = dpf2(2.16f) * 3 + dpf2(1.166f) * 2;
            canvas.translate(getBounds().centerX(), getBounds().centerY() - (arrowsHeight) / 2.0f);
            for (int i = 0; i < 4; ++i) {
                float alpha = 1.0f;
                if (i == 0) {
                    alpha = 1.0f - t;
                } else if (i == 3) {
                    alpha = t;
                }
                strokePaint.setAlpha((int) (0xFF * alpha));
                canvas.save();
                final float s = lerp(0.5f, 1.0f, alpha);
                canvas.scale(s, s);
                canvas.drawPath(arrow, strokePaint);
                canvas.restore();
                canvas.translate(0, dpf2(2.16f + 1.166f) * alpha);
            }
            canvas.restore();

            if (view != null) {
                view.invalidate();
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(18);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(18);
        }
    }

    private static class PageTransition {
        public int from, to;
        public float progress;

        public PageTransition(int from, int to, float t) {
            this.from = from;
            this.to = to;
        }

        public void setProgress(float t) {
            this.progress = t;
        }

        public float at(int page) {
            if (to == page && from == page) {
                return 1.0f;
            }
            if (to == page) {
                return progress;
            }
            if (from == page) {
                return 1.0f - progress;
            }
            return 0.0f;
        }

        public boolean to(int page) {
            return to == page;
        }

        public float at(int page1, int page2) {
            if (contains(page1) && contains(page2)) {
                return 1.0f;
            }
            return Math.max(at(page1), at(page2));
        }

        public boolean contains(int page) {
            return from == page || to == page;
        }

        public boolean is(int page) {
            return to == page;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            StarsController.GiftsList notifiedList = (StarsController.GiftsList) args[1];
            if (giftsList == notifiedList) {
                updateViewPager();
            }
        }
    }
}

package org.telegram.ui.Components.Premium;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;

public class DoubledLimitsBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    FrameLayout titleLayout;
    TextView titleView;
    ImageView titleImage;

    PremiumButtonView premiumButtonView;
    PremiumPreviewFragment premiumPreviewFragment;

    float titleProgress;

    private BaseFragment baseFragment;

    private View divider;
    private PremiumPreviewFragment.SubscriptionTier selectedTier;
    private Adapter adapter;

    public DoubledLimitsBottomSheet(BaseFragment fragment, int currentAccount) {
        this(fragment, currentAccount, null);
    }

    public DoubledLimitsBottomSheet(BaseFragment fragment, int currentAccount, PremiumPreviewFragment.SubscriptionTier subscriptionTier) {
        super(fragment, false, false, false, fragment == null ? null : fragment.getResourceProvider());
        this.selectedTier = subscriptionTier;
        this.baseFragment = fragment;

        clipToActionBar = true;

        titleLayout = new FrameLayout(getContext());
        titleView = new TextView(getContext());
        titleView.setText(LocaleController.getString(R.string.DoubledLimits));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTypeface(AndroidUtilities.bold());
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        titleImage = new ImageView(getContext());
        titleImage.setImageDrawable(PremiumGradient.getInstance().createGradientDrawable(ContextCompat.getDrawable(getContext(), R.drawable.other_2x_large)));
        titleLayout.addView(titleImage, LayoutHelper.createFrame(40, 28, Gravity.CENTER_VERTICAL));
        containerView.addView(titleLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40));

        divider = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawRect(0, 0, getMeasuredWidth(), 1, Theme.dividerPaint);
            }
        };
        divider.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        containerView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.BOTTOM, 0, 0, 0, 0));

        premiumButtonView = new PremiumButtonView(getContext(), true, resourcesProvider);
        premiumButtonView.buttonTextView.setText(PremiumPreviewFragment.getPremiumButtonText(currentAccount, selectedTier));

        containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 12));

        premiumButtonView.buttonLayout.setOnClickListener((view) -> {
            if (!UserConfig.getInstance(currentAccount).isPremium()) {
                PremiumPreviewFragment.buyPremium(fragment, selectedTier, "double_limits");
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener((v) -> {
            dismiss();
        });
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(48 + 24));
        bindPremium(UserConfig.getInstance(getCurrentAccount()).isPremium());
    }

    private void bindPremium(boolean hasPremium) {
        if (hasPremium) {
            premiumButtonView.setOverlayText(LocaleController.getString(R.string.OK), false, false);
        }
    }

    @Override
    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
        adapter.measureGradient(getContext(), View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {
        float minTop = AndroidUtilities.statusBarHeight + (actionBar.getMeasuredHeight() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(40)) / 2f;
        float fromIconX = (titleLayout.getMeasuredWidth() - titleView.getMeasuredWidth() - titleImage.getMeasuredWidth() - AndroidUtilities.dp(6)) / 2f;
        float toIconX = AndroidUtilities.dp(72) - titleImage.getMeasuredWidth() - AndroidUtilities.dp(6);
        float fromX = fromIconX + titleImage.getMeasuredWidth() + AndroidUtilities.dp(6);
        float toX = AndroidUtilities.dp(72);

        float fromY = Math.max(top + AndroidUtilities.dp(24), minTop);
        float toY = minTop;

        if (progressToFullView > 0 && titleProgress != 1f) {
            titleProgress += 16f / 150f;
            if (titleProgress > 1f) {
                titleProgress = 1f;
            }
            containerView.invalidate();
        } else if (progressToFullView == 0 && titleProgress != 0) {
            titleProgress -= 16f / 150f;
            if (titleProgress < 0) {
                titleProgress = 0;
            }
            containerView.invalidate();
        }

        titleLayout.setTranslationY(fromY * (1f - titleProgress) + toY * titleProgress);
        titleView.setTranslationX(fromX * (1f - titleProgress) + toX * titleProgress);
        titleImage.setTranslationX(fromIconX * (1f - titleProgress) + toIconX * titleProgress);
        titleImage.setAlpha(1f - titleProgress);
        float s = 0.6f + 0.4f * (1f - titleProgress);
        titleImage.setScaleX(s);
        titleImage.setScaleY(s);
    }

    @Override
    protected CharSequence getTitle() {
        return null;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new Adapter(currentAccount, false, resourcesProvider);
        adapter.containerView = containerView;
        return adapter;
    }

    public void setParentFragment(PremiumPreviewFragment premiumPreviewFragment) {
        this.premiumPreviewFragment = premiumPreviewFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    public void dismiss() {
        super.dismiss();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.billingProductDetailsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.premiumPromoUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.billingProductDetailsUpdated || id == NotificationCenter.premiumPromoUpdated) {
            premiumButtonView.buttonTextView.setText(PremiumPreviewFragment.getPremiumButtonText(currentAccount, selectedTier));
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            bindPremium(UserConfig.getInstance(currentAccount).isPremium());
        }
    }


    private static class LimitCell extends LinearLayout {

        TextView title;
        TextView subtitle;
        LimitPreviewView previewView;

        public LimitCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);

            title = new TextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 0, 16, 0));

            subtitle = new TextView(context);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 1, 16, 0));

            previewView = new LimitPreviewView(context, 0, 10, 20, resourcesProvider);
            addView(previewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 21));
        }

        @SuppressLint("SetTextI18n")
        public void setData(Limit limit) {
            title.setText(limit.title);
            subtitle.setText(limit.subtitle);
            previewView.premiumCount.setText(String.format("%d", limit.premiumLimit));
            previewView.defaultCount.setText(String.format("%d", limit.defaultLimit));
        }
    }


    private static class Limit {
        final String title;
        final String subtitle;
        final int defaultLimit;
        final int premiumLimit;
        final int current = -1;
        public int yOffset;

        private Limit(String title, String subtitle, int defaultLimit, int premiumLimit) {
            this.title = title;
            this.subtitle = subtitle;
            this.defaultLimit = defaultLimit;
            this.premiumLimit = premiumLimit;
        }
    }

    public static class Adapter extends RecyclerListView.SelectionAdapter {

        private final Theme.ResourcesProvider resourcesProvider;

        int rowCount;
        int headerRow;
        int limitsStartRow;
        int limitsStartEnd;
        int lastViewRow;

        final ArrayList<Limit> limits = new ArrayList<>();

        PremiumGradient.PremiumGradientTools gradientTools;
        private int totalGradientHeight;

        ViewGroup containerView;
        boolean drawHeader;

        public Adapter(int currentAccount, boolean drawHeader, Theme.ResourcesProvider resourcesProvider) {
            this.drawHeader = drawHeader;
            this.resourcesProvider = resourcesProvider;

            gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4, -1, resourcesProvider);
            gradientTools.x1 = 0;
            gradientTools.y1 = 0;
            gradientTools.x2 = 0;
            gradientTools.y2 = 1f;

            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            limits.add(new Limit(
                    LocaleController.getString(R.string.GroupsAndChannelsLimitTitle),
                    LocaleController.formatString("GroupsAndChannelsLimitSubtitle", R.string.GroupsAndChannelsLimitSubtitle, messagesController.channelsLimitPremium),
                    messagesController.channelsLimitDefault, messagesController.channelsLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.PinChatsLimitTitle),
                    LocaleController.formatString("PinChatsLimitSubtitle", R.string.PinChatsLimitSubtitle, messagesController.dialogFiltersPinnedLimitPremium),
                    messagesController.dialogFiltersPinnedLimitDefault, messagesController.dialogFiltersPinnedLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.PublicLinksLimitTitle),
                    LocaleController.formatString("PublicLinksLimitSubtitle", R.string.PublicLinksLimitSubtitle, messagesController.publicLinksLimitPremium),
                    messagesController.publicLinksLimitDefault, messagesController.publicLinksLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.SavedGifsLimitTitle),
                    LocaleController.formatString("SavedGifsLimitSubtitle", R.string.SavedGifsLimitSubtitle, messagesController.savedGifsLimitPremium),
                    messagesController.savedGifsLimitDefault, messagesController.savedGifsLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.FavoriteStickersLimitTitle),
                    LocaleController.formatString("FavoriteStickersLimitSubtitle", R.string.FavoriteStickersLimitSubtitle, messagesController.stickersFavedLimitPremium),
                    messagesController.stickersFavedLimitDefault, messagesController.stickersFavedLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.BioLimitTitle),
                    LocaleController.formatString("BioLimitSubtitle", R.string.BioLimitSubtitle, messagesController.stickersFavedLimitPremium),
                    messagesController.aboutLengthLimitDefault, messagesController.aboutLengthLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.CaptionsLimitTitle),
                    LocaleController.formatString("CaptionsLimitSubtitle", R.string.CaptionsLimitSubtitle, messagesController.stickersFavedLimitPremium),
                    messagesController.captionLengthLimitDefault, messagesController.captionLengthLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.FoldersLimitTitle),
                    LocaleController.formatString("FoldersLimitSubtitle", R.string.FoldersLimitSubtitle, messagesController.dialogFiltersLimitPremium),
                    messagesController.dialogFiltersLimitDefault, messagesController.dialogFiltersLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.ChatPerFolderLimitTitle),
                    LocaleController.formatString("ChatPerFolderLimitSubtitle", R.string.ChatPerFolderLimitSubtitle, messagesController.dialogFiltersChatsLimitPremium),
                    messagesController.dialogFiltersChatsLimitDefault, messagesController.dialogFiltersChatsLimitPremium
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.ConnectedAccountsLimitTitle),
                    LocaleController.formatString("ConnectedAccountsLimitSubtitle", R.string.ConnectedAccountsLimitSubtitle, 4),
                    UserConfig.MAX_ACCOUNT_DEFAULT_COUNT, UserConfig.MAX_ACCOUNT_COUNT
            ));
            limits.add(new Limit(
                    LocaleController.getString(R.string.SimilarChannelsLimitTitle),
                    LocaleController.formatString(R.string.SimilarChannelsLimitSubtitle, messagesController.recommendedChannelsLimitPremium),
                    messagesController.recommendedChannelsLimitDefault, messagesController.recommendedChannelsLimitPremium
            ));

            rowCount = 0;
            headerRow = rowCount++;
            limitsStartRow = rowCount;
            rowCount += limits.size();
            limitsStartEnd = rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view;
            switch (viewType) {
                default:
                case 0:
                    LimitCell limitCell = new LimitCell(context, resourcesProvider);
                    limitCell.previewView.setParentViewForGradien(containerView);
                    limitCell.previewView.setStaticGradinet(gradientTools);
                    view = limitCell;
                    break;
                case 1:
                    if (drawHeader) {
                        FrameLayout titleLayout = new FrameLayout(context) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY));
                            }
                        };
                        LinearLayout linearLayout = new LinearLayout(context);
                        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

                        ImageView titleImage = new ImageView(context);
                        titleImage.setImageDrawable(PremiumGradient.getInstance().createGradientDrawable(ContextCompat.getDrawable(context, R.drawable.other_2x_large)));
                        linearLayout.addView(titleImage, LayoutHelper.createFrame(40, 28, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

                        TextView titleView = new TextView(context);
                        titleView.setText(LocaleController.getString(R.string.DoubledLimits));
                        titleView.setGravity(Gravity.CENTER);
                        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                        titleView.setTypeface(AndroidUtilities.bold());
                        linearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));


                        titleLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                        view = titleLayout;
                    } else {
                        view = new FixedHeightEmptyCell(context, 40 + 24);
                    }
                    break;
                case 2:
                    view = new FixedHeightEmptyCell(context, 16);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                LimitCell limitCell = (LimitCell) holder.itemView;
                limitCell.setData(limits.get(position - limitsStartRow));
                limitCell.previewView.gradientYOffset = limits.get(position - limitsStartRow).yOffset;
                limitCell.previewView.gradientTotalHeight = totalGradientHeight;
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return 1;
            } else if (position == lastViewRow) {
                return 2;
            }
            return 0;
        }

        public void measureGradient(Context context, int w, int h) {
            int yOffset = 0;
            LimitCell dummyCell = new LimitCell(context, resourcesProvider);
            for (int i = 0; i < limits.size(); i++) {
                dummyCell.setData(limits.get(i));
                dummyCell.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST));
                limits.get(i).yOffset = yOffset;
                yOffset += dummyCell.getMeasuredHeight();
            }

            totalGradientHeight = yOffset;
        }
    }

}

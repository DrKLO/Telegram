package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINK_NBSP;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ChannelMonetizationLayout.replaceTON;
import static org.telegram.ui.ChatEditActivity.applyNewSpan;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.TwoStepVerificationActivity;
import org.telegram.ui.TwoStepVerificationSetupActivity;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.ui.bots.ChannelAffiliateProgramsFragment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

public class BotStarsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int TYPE_STARS = 0;
    public static final int TYPE_TON = 1;

    public final int type;
    public final long bot_id;
    public final boolean self;

    private ChatAvatarContainer avatarContainer;
    private UniversalRecyclerView listView;

    private TLRPC.TL_payments_starsRevenueStats lastStats;
    private TLRPC.TL_starsRevenueStatus lastStatsStatus;

    private StatisticActivity.ChartViewData revenueChartData;
    private final ChannelMonetizationLayout.ProceedOverview availableValue = ChannelMonetizationLayout.ProceedOverview.as("XTR", getString(R.string.BotStarsOverviewAvailableBalance));
    private final ChannelMonetizationLayout.ProceedOverview totalValue = ChannelMonetizationLayout.ProceedOverview.as("XTR", getString(R.string.BotStarsOverviewTotalBalance));
    private final ChannelMonetizationLayout.ProceedOverview totalProceedsValue =     ChannelMonetizationLayout.ProceedOverview.as("XTR", getString(R.string.BotStarsOverviewTotalProceeds));

    private final ChannelMonetizationLayout.ProceedOverview tonAvailableValue =      ChannelMonetizationLayout.ProceedOverview.as("TON", getString(R.string.BotMonetizationOverviewAvailable));
    private final ChannelMonetizationLayout.ProceedOverview tonLastWithdrawalValue = ChannelMonetizationLayout.ProceedOverview.as("TON", getString(R.string.BotMonetizationOverviewLastWithdrawal));
    private final ChannelMonetizationLayout.ProceedOverview tonLifetimeValue =       ChannelMonetizationLayout.ProceedOverview.as("TON", getString(R.string.BotMonetizationOverviewTotal));

    private final CharSequence withdrawInfo;

    private StarsIntroActivity.StarsTransactionsLayout transactionsLayout;

    private int balanceBlockedUntil;
    private LinearLayout balanceLayout;
    private LinearLayout balanceButtonsLayout;
    private RelativeSizeSpan balanceTitleSizeSpan;
    private AnimatedTextView balanceTitle;
    private AnimatedTextView balanceSubtitle;
    private OutlineTextContainerView balanceEditTextContainer;
    private boolean balanceEditTextIgnore = false;
    private boolean balanceEditTextAll = true;
    private long balanceEditTextValue;
    private EditTextBoldCursor balanceEditText;
    private ButtonWithCounterView balanceButton, adsButton;
    private ColoredImageSpan[] starRef = new ColoredImageSpan[1];
    private int shakeDp = 4;

    private LinearLayout tonBalanceLayout;
    private RelativeSizeSpan tonBalanceTitleSizeSpan;
    private AnimatedTextView tonBalanceTitle;
    private AnimatedTextView tonBalanceSubtitle;
    private ButtonWithCounterView tonBalanceButton;

    private double rate;

    public BotStarsActivity(int type, long botId) {
        this.type = type;
        this.bot_id = botId;
        this.self = botId == getUserConfig().getClientUserId();

        if (type == TYPE_STARS) {
            BotStarsController.getInstance(currentAccount).preloadStarsStats(bot_id);
            if (!self) {
                BotStarsController.getInstance(currentAccount).invalidateTransactions(bot_id, true);
            }
        } else if (type == TYPE_TON) {
            BotStarsController.getInstance(currentAccount).preloadTonStats(bot_id);
        }

        withdrawInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(self ? formatPluralStringComma("SelfStarsWithdrawInfo", (int) getMessagesController().starsRevenueWithdrawalMin) : getString(R.string.BotStarsWithdrawInfo), () -> {
            Browser.openUrl(getContext(), getString(R.string.BotStarsWithdrawInfoLink));
        }), true);
    }

    @Override
    public View createView(Context context) {

        NestedFrameLayout frameLayout = new NestedFrameLayout(context);

        avatarContainer = new ChatAvatarContainer(context, null, false);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        avatarContainer.getAvatarImageView().setScaleX(0.9f);
        avatarContainer.getAvatarImageView().setScaleY(0.9f);
        avatarContainer.setRightAvatarPadding(-dp(3));
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 50 : 0, 0, 40, 0));

        TLRPC.User bot = getMessagesController().getUser(bot_id);
        avatarContainer.setUserAvatar(bot, true);
        avatarContainer.setTitle(UserObject.getUserName(bot));
        if (type == BotStarsActivity.TYPE_STARS) {
            avatarContainer.setSubtitle(LocaleController.getString(R.string.BotStatsStars));
        } else {
            avatarContainer.setSubtitle(LocaleController.getString(R.string.BotStatsTON));
        }

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        avatarContainer.setTitleColors(Theme.getColor(Theme.key_player_actionBarTitle), Theme.getColor(Theme.key_player_actionBarSubtitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarTitle), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarTitle), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        transactionsLayout = new StarsIntroActivity.StarsTransactionsLayout(context, currentAccount, bot_id, getClassGuid(), getResourceProvider());

        balanceLayout = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
                );
            }
        };
        balanceLayout.setOrientation(LinearLayout.VERTICAL);
        balanceLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
        balanceLayout.setPadding(0, 0, 0, dp(17));

        balanceTitle = new AnimatedTextView(context, false, true, true);
        balanceTitle.setTypeface(AndroidUtilities.bold());
        balanceTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        balanceTitle.setTextSize(dp(32));
        balanceTitle.setGravity(Gravity.CENTER);
        balanceTitleSizeSpan = new RelativeSizeSpan(65f / 96f);
        balanceLayout.addView(balanceTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 15, 22, 0));

        balanceSubtitle = new AnimatedTextView(context, true, true, true);
        balanceSubtitle.setGravity(Gravity.CENTER);
        balanceSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
        balanceSubtitle.setTextSize(dp(14));
        balanceLayout.addView(balanceSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 17, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 4, 22, 0));

        balanceEditTextContainer = new OutlineTextContainerView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (balanceEditText != null && !balanceEditText.isFocusable()) {
                    balanceEditText.setFocusable(true);
                    balanceEditText.setFocusableInTouchMode(true);
                    int position = listView.findPositionByItemId(BALANCE);
                    if (position >= 0 && position < listView.adapter.getItemCount()) {
                        listView.stopScroll();
                        listView.smoothScrollToPosition(position);
                    }
                    balanceEditText.requestFocus();
                }
                return super.dispatchTouchEvent(event);
            }
        };
        balanceEditTextContainer.setText(getString(R.string.BotStarsWithdrawPlaceholder));
        balanceEditTextContainer.setLeftPadding(dp(14 + 22));
        balanceEditText = new EditTextBoldCursor(context) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                AndroidUtilities.hideKeyboard(this);
            }
        };
        balanceEditText.setFocusable(false);
        balanceEditText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        balanceEditText.setCursorSize(AndroidUtilities.dp(20));
        balanceEditText.setCursorWidth(1.5f);
        balanceEditText.setBackground(null);
        balanceEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        balanceEditText.setMaxLines(1);
        int padding = AndroidUtilities.dp(16);
        balanceEditText.setPadding(dp(6), padding, padding, padding);
        balanceEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        balanceEditText.setTypeface(Typeface.DEFAULT);
        balanceEditText.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        balanceEditText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        balanceEditText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        balanceEditText.setOnFocusChangeListener((v, hasFocus) -> balanceEditTextContainer.animateSelection(hasFocus ? 1f : 0f));
        balanceEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                long balance = BotStarsController.getInstance(currentAccount).getAvailableBalance(bot_id);
                balanceEditTextValue = TextUtils.isEmpty(s) ? 0 : Long.parseLong(s.toString());
                if (balanceEditTextValue > balance) {
                    balanceEditTextValue = balance;
                    balanceEditTextIgnore = true;
                    balanceEditText.setText(Long.toString(balanceEditTextValue));
                    balanceEditText.setSelection(balanceEditText.getText().length());
                    balanceEditTextIgnore = false;
                }
                balanceEditTextAll = balanceEditTextValue == balance;
                AndroidUtilities.cancelRunOnUIThread(setBalanceButtonText);
                setBalanceButtonText.run();
                if (balanceEditTextIgnore) return;
                balanceEditTextAll = false;
            }
        });
        LinearLayout balanceEditTextLayout = new LinearLayout(context);
        balanceEditTextLayout.setOrientation(LinearLayout.HORIZONTAL);
        ImageView starImage = new ImageView(context);
        starImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        starImage.setImageResource(R.drawable.star_small_inner);
        balanceEditTextLayout.addView(starImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        balanceEditTextLayout.addView(balanceEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
        balanceEditTextContainer.attachEditText(balanceEditText);
        balanceEditTextContainer.addView(balanceEditTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        balanceEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                withdraw();
                return true;
            }
            return false;
        });
        balanceLayout.addView(balanceEditTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 18, 14, 18, 2));
        balanceEditTextContainer.setVisibility(View.GONE);

        balanceButtonsLayout = new LinearLayout(context);
        balanceButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);

        balanceButton = new ButtonWithCounterView(context, getResourceProvider()) {
            @Override
            protected boolean subTextSplitToWords() {
                return false;
            }
        };
        balanceButton.setEnabled(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled);
        balanceButton.setText(getString(R.string.BotStarsButtonWithdrawShortAll), false);
        balanceButton.setOnClickListener(v -> {
            withdraw();
        });

        adsButton = new ButtonWithCounterView(context, getResourceProvider());
        adsButton.setEnabled(true);
        adsButton.setText(getString(R.string.MonetizationStarsAds), false);
        adsButton.setOnClickListener(v -> {
            if (!v.isEnabled() || adsButton.isLoading()) return;

            adsButton.setLoading(true);
            TLRPC.TL_payments_getStarsRevenueAdsAccountUrl req = new TLRPC.TL_payments_getStarsRevenueAdsAccountUrl();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(bot_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_payments_starsRevenueAdsAccountUrl) {
                    Browser.openUrl(context, ((TLRPC.TL_payments_starsRevenueAdsAccountUrl) res).url);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    adsButton.setLoading(false);
                }, 1000);
            }));
        });

        balanceButtonsLayout.addView(balanceButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL));
        if (!self) {
            balanceButtonsLayout.addView(new Space(context), LayoutHelper.createLinear(8, 48, 0, Gravity.FILL));
            balanceButtonsLayout.addView(adsButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL));
        }
        balanceLayout.addView(balanceButtonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18, 13, 18, 0));

        tonBalanceLayout = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        heightMeasureSpec
                );
            }
        };
        tonBalanceLayout.setOrientation(LinearLayout.VERTICAL);
        tonBalanceLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        tonBalanceLayout.setPadding(0, 0, 0, dp(17));

        tonBalanceTitle = new AnimatedTextView(context, false, true, true);
        tonBalanceTitle.setTypeface(AndroidUtilities.bold());
        tonBalanceTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        tonBalanceTitle.setTextSize(dp(32));
        tonBalanceTitle.setGravity(Gravity.CENTER);
        tonBalanceTitleSizeSpan = new RelativeSizeSpan(65f / 96f);
        tonBalanceLayout.addView(tonBalanceTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 15, 22, 0));

        tonBalanceSubtitle = new AnimatedTextView(context, true, true, true);
        tonBalanceSubtitle.setGravity(Gravity.CENTER);
        tonBalanceSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        tonBalanceSubtitle.setTextSize(dp(14));
        tonBalanceLayout.addView(tonBalanceSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 17, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 4, 22, 0));

        tonBalanceButton = new ButtonWithCounterView(context, resourceProvider);
        tonBalanceButton.setEnabled(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled);
        tonBalanceButton.setText(getString(R.string.MonetizationWithdraw), false);
        tonBalanceButton.setVisibility(View.GONE);
        tonBalanceButton.setOnClickListener(v -> {
            if (!v.isEnabled() || tonBalanceButton.isLoading()) {
                return;
            }
            TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
            passwordFragment.setDelegate(1, password -> initWithdraw(false, 0, password, passwordFragment));
            tonBalanceButton.setLoading(true);
            passwordFragment.preload(() -> {
                tonBalanceButton.setLoading(false);
                presentFragment(passwordFragment);;
            });
        });
        tonBalanceLayout.addView(tonBalanceButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18, 13, 18, 0));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (type == TYPE_TON && (!listView.canScrollVertically(1) || isLoadingVisible())) {
                    loadTonTransactions();
                }
            }
        });

        return fragmentView = frameLayout;
    }

    private Bulletin withdrawalBulletin;
    private void withdraw() {
        if (!balanceButton.isEnabled() || balanceButton.isLoading()) {
            return;
        }

        final int now = getConnectionsManager().getCurrentTime();
        if (balanceBlockedUntil > now) {
            withdrawalBulletin = BulletinFactory.of(this).createSimpleBulletin(R.raw.timer_3, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotStarsWithdrawalToast, untilString(balanceBlockedUntil - now)))).show();
            return;
        }

        if (balanceEditTextValue < getMessagesController().starsRevenueWithdrawalMin) {
            Drawable starDrawable = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
            BulletinFactory.of(this).createSimpleBulletin(starDrawable, AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("BotStarsWithdrawMinLimit", (int) getMessagesController().starsRevenueWithdrawalMin), () -> {
                Bulletin.hideVisible();
                long balance = BotStarsController.getInstance(currentAccount).getAvailableBalance(bot_id);
                if (balance < getMessagesController().starsRevenueWithdrawalMin) {
                    balanceEditTextAll = true;
                    balanceEditTextValue = balance;
                } else {
                    balanceEditTextAll = false;
                    balanceEditTextValue = getMessagesController().starsRevenueWithdrawalMin;
                }
                balanceEditTextIgnore = true;
                balanceEditText.setText(Long.toString(balanceEditTextValue));
                balanceEditText.setSelection(balanceEditText.getText().length());
                balanceEditTextIgnore = false;

                AndroidUtilities.cancelRunOnUIThread(setBalanceButtonText);
                setBalanceButtonText.run();
            })).show();
            return;
        }

        final long stars = balanceEditTextValue;
        TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
        passwordFragment.setDelegate(1, password -> initWithdraw(true, stars, password, passwordFragment));
        balanceButton.setLoading(true);
        passwordFragment.preload(() -> {
            balanceButton.setLoading(false);
            presentFragment(passwordFragment);;
        });
    }

    private final int BALANCE = 1;
    private final int BUTTON_AFFILIATE = 2;

    private CharSequence titleInfo;
    private CharSequence proceedsInfo;
    private CharSequence balanceInfo;
    private boolean proceedsAvailable;
    private StatisticActivity.ChartViewData impressionsChart;
    private StatisticActivity.ChartViewData revenueChart;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final BotStarsController s = BotStarsController.getInstance(currentAccount);
        if (type == TYPE_STARS) {
            items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_STACKBAR, stats_dc, revenueChartData));
            items.add(UItem.asShadow(-1, null));
            items.add(UItem.asBlackHeader(getString(R.string.BotStarsOverview)));
            TLRPC.TL_payments_starsRevenueStats stats = s.getStarsRevenueStats(bot_id);
            if (stats != null && stats.status != null) {
                availableValue.contains1 = false;
                availableValue.contains2 = true;
                availableValue.crypto_amount2 = stats.status.available_balance;
                availableValue.crypto_currency2 = "XTR";
                availableValue.currency = "USD";
                availableValue.amount2 = (long) (stats.status.available_balance.amount * rate * 100.0);
                totalValue.contains1 = false;
                totalValue.contains2 = true;
                totalValue.crypto_amount2 = stats.status.current_balance;
                totalValue.crypto_currency2 = "XTR";
                totalValue.amount2 = (long) (stats.status.current_balance.amount * rate * 100.0);
                totalValue.currency = "USD";
                totalProceedsValue.contains1 = false;
                totalProceedsValue.contains2 = true;
                totalProceedsValue.crypto_amount2 = stats.status.overall_revenue;
                totalProceedsValue.crypto_currency2 = "XTR";
                totalProceedsValue.amount2 = (long) (stats.status.overall_revenue.amount * rate * 100.0);
                totalProceedsValue.currency = "USD";
                setStarsBalance(stats.status.available_balance, stats.status.next_withdrawal_at);

                balanceButtonsLayout.setVisibility(stats.status.withdrawal_enabled ? View.VISIBLE : View.GONE);
            }
            items.add(UItem.asProceedOverview(availableValue));
            items.add(UItem.asProceedOverview(totalValue));
            items.add(UItem.asProceedOverview(totalProceedsValue));
            items.add(UItem.asShadow(-2, getString(self ? R.string.SelfStarsOverviewInfo : R.string.BotStarsOverviewInfo)));
            items.add(UItem.asBlackHeader(getString(R.string.BotStarsAvailableBalance)));
            items.add(UItem.asCustom(BALANCE, balanceLayout));
            items.add(UItem.asShadow(-3, withdrawInfo));
            if (!self) {
                if (getMessagesController().starrefConnectAllowed) {
                    items.add(AffiliateProgramFragment.ColorfulTextCell.Factory.as(BUTTON_AFFILIATE, Theme.getColor(Theme.key_color_green, resourceProvider), R.drawable.filled_earn_stars, applyNewSpan(getString(R.string.BotAffiliateProgramRowTitle)), getString(R.string.BotAffiliateProgramRowText)));
                    items.add(UItem.asShadow(-4, null));
                }
                items.add(UItem.asFullscreenCustom(transactionsLayout, 0));
            }
        } else if (type == TYPE_TON) {
            TL_stats.TL_broadcastRevenueStats stats = s.getTONRevenueStats(bot_id, true);
            if (titleInfo == null) {
                titleInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.BotMonetizationInfo, 50), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
                    showDialog(ChannelMonetizationLayout.makeLearnSheet(getContext(), true, resourceProvider));
                }, resourceProvider), true);
            }
            items.add(UItem.asCenterShadow(titleInfo));
            if (impressionsChart == null && stats != null) {
                impressionsChart = StatisticActivity.createViewData(stats.top_hours_graph, getString(R.string.BotMonetizationGraphImpressions), 0);
                if (impressionsChart != null) {
                    impressionsChart.useHourFormat = true;
                }
            }
            if (impressionsChart != null && !impressionsChart.isEmpty) {
                items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_BAR_LINEAR, stats_dc, impressionsChart));
                items.add(UItem.asShadow(-1, null));
            }
            if (revenueChart == null && stats != null) {
                if (stats.revenue_graph != null) {
                    stats.revenue_graph.rate = (float) (1_000_000_000.0 / 100.0 / stats.usd_rate);
                }
                revenueChart = StatisticActivity.createViewData(stats.revenue_graph, getString(R.string.BotMonetizationGraphRevenue), 2);
            }
            if (revenueChart != null && !revenueChart.isEmpty) {
                items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_STACKBAR, stats_dc, revenueChart));
                items.add(UItem.asShadow(-2, null));
            }
            if (!proceedsAvailable && stats != null && stats.balances != null) {
                double ton_rate = stats.usd_rate;
                tonAvailableValue.crypto_amount = stats.balances.available_balance;
                tonAvailableValue.amount = (long) (tonAvailableValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
                setBalance(tonAvailableValue.crypto_amount, tonAvailableValue.amount);
                tonAvailableValue.currency = "USD";
                tonLastWithdrawalValue.crypto_amount = stats.balances.current_balance;
                tonLastWithdrawalValue.amount = (long) (tonLastWithdrawalValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
                tonLastWithdrawalValue.currency = "USD";
                tonLifetimeValue.contains1 = true;
                tonLifetimeValue.crypto_amount = stats.balances.overall_revenue;
                tonLifetimeValue.amount = (long) (tonLifetimeValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
                tonLifetimeValue.currency = "USD";
                proceedsAvailable = true;
                tonBalanceButton.setVisibility(stats.balances.available_balance > 0 && stats.balances.withdrawal_enabled ? View.VISIBLE : View.GONE);
            }
            if (proceedsAvailable) {
                items.add(UItem.asBlackHeader(getString(R.string.BotMonetizationOverview)));
                items.add(UItem.asProceedOverview(tonAvailableValue));
                items.add(UItem.asProceedOverview(tonLastWithdrawalValue));
                items.add(UItem.asProceedOverview(tonLifetimeValue));
                if (proceedsInfo == null) {
                    final int proceedsInfoText = R.string.BotMonetizationProceedsTONInfo;
                    final int proceedsInfoLink = R.string.BotMonetizationProceedsTONInfoLink;
                    proceedsInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(proceedsInfoText), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
                        Browser.openUrl(getContext(), getString(proceedsInfoLink));
                    }, resourceProvider), true);
                }
                items.add(UItem.asShadow(-4, proceedsInfo));
            }

            items.add(UItem.asBlackHeader(getString(R.string.BotMonetizationBalance)));
            items.add(UItem.asCustom(tonBalanceLayout));
            if (balanceInfo == null) {
                balanceInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled ? R.string.BotMonetizationBalanceInfo : R.string.BotMonetizationBalanceInfoNotAvailable), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
                    Browser.openUrl(getContext(), getString(R.string.BotMonetizationBalanceInfoLink));
                }), true);
            }
            items.add(UItem.asShadow(-5, balanceInfo));
            if (!tonTransactionsEndReached || !tonTransactions.isEmpty()) {
                items.add(UItem.asBlackHeader(getString(R.string.BotMonetizationTransactions)));
                for (TL_stats.BroadcastRevenueTransaction t : tonTransactions) {
                    items.add(UItem.asTransaction(t));
                }
                if (!tonTransactionsEndReached) {
                    items.add(UItem.asFlicker(1, FlickerLoadingView.DIALOG_CELL_TYPE));
                    items.add(UItem.asFlicker(2, FlickerLoadingView.DIALOG_CELL_TYPE));
                    items.add(UItem.asFlicker(3, FlickerLoadingView.DIALOG_CELL_TYPE));
                }
            }
            items.add(UItem.asShadow(-6, null));
        }
    }

    private boolean tonTransactionsLoading = false;
    private boolean tonTransactionsEndReached = false;
    private int tonTransactionsCount = 0;
    private final ArrayList<TL_stats.BroadcastRevenueTransaction> tonTransactions = new ArrayList<>();
    private void loadTonTransactions() {
        if (tonTransactionsLoading || tonTransactionsEndReached) return;
        tonTransactionsLoading = true;
        TL_stats.TL_getBroadcastRevenueTransactions req = new TL_stats.TL_getBroadcastRevenueTransactions();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(bot_id);
        req.offset = tonTransactions.size();
        req.limit = tonTransactions.isEmpty() ? 5 : 20;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_stats.TL_broadcastRevenueTransactions) {
                TL_stats.TL_broadcastRevenueTransactions r = (TL_stats.TL_broadcastRevenueTransactions) res;
                tonTransactionsCount = r.count;
                tonTransactions.addAll(r.transactions);
                tonTransactionsEndReached = tonTransactions.size() >= tonTransactionsCount || r.transactions.isEmpty();
            } else if (err != null) {
                BulletinFactory.showError(err);
                tonTransactionsEndReached = true;
            }
            tonTransactionsLoading = false;
            if (listView.adapter != null) {
                listView.adapter.update(true);
            }
        }));
    }

    public boolean isLoadingVisible() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof FlickerLoadingView)
                return true;
        }
        return false;
    }

    private void onItemClick(UItem item, View view, int pos, float x, float y) {
        if (item.instanceOf(StarsIntroActivity.StarsTransactionView.Factory.class)) {
            TL_stars.StarsTransaction t = (TL_stars.StarsTransaction) item.object;
            StarsIntroActivity.showTransactionSheet(getContext(), true, bot_id, currentAccount, t, getResourceProvider());
        } else if (item.object instanceof TL_stats.BroadcastRevenueTransaction) {
            ChannelMonetizationLayout.showTransactionSheet(getContext(), currentAccount, (TL_stats.BroadcastRevenueTransaction) item.object, bot_id, resourceProvider);
        } else if (item.id == BUTTON_AFFILIATE) {
            presentFragment(new ChannelAffiliateProgramsFragment(bot_id));
        }
    }

    private void setStarsBalance(TL_stars.StarsAmount crypto_amount, int blockedUntil) {
        if (balanceTitle == null || balanceSubtitle == null)
            return;
        long amount = (long) (rate * crypto_amount.amount * 100.0);
        SpannableStringBuilder ssb = new SpannableStringBuilder(StarsIntroActivity.replaceStarsWithPlain(TextUtils.concat("XTR ", StarsIntroActivity.formatStarsAmount(crypto_amount, 0.8f, ' ')), 1f));
        int index = TextUtils.indexOf(ssb, ".");
        if (index >= 0) {
            ssb.setSpan(balanceTitleSizeSpan, index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        balanceTitle.setText(ssb);
        balanceSubtitle.setText("≈" + BillingController.getInstance().formatCurrency(amount, "USD"));
        balanceEditTextContainer.setVisibility(amount > 0 ? View.VISIBLE : View.GONE);
        if (balanceEditTextAll) {
            balanceEditTextIgnore = true;
            balanceEditText.setText(Long.toString(balanceEditTextValue = crypto_amount.amount));
            balanceEditText.setSelection(balanceEditText.getText().length());
            balanceEditTextIgnore = false;

            balanceButton.setEnabled(balanceEditTextValue > 0);
        }
        balanceBlockedUntil = blockedUntil;

        AndroidUtilities.cancelRunOnUIThread(setBalanceButtonText);
        setBalanceButtonText.run();
    }

    private DecimalFormat formatter;
    private void setBalance(long crypto_amount, long amount) {
        if (formatter == null) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setDecimalSeparator('.');
            formatter = new DecimalFormat("#.##", symbols);
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(6);
            formatter.setGroupingUsed(false);
        }
        formatter.setMaximumFractionDigits(crypto_amount / 1_000_000_000.0 > 1.5 ? 2 : 6);
        SpannableStringBuilder ssb = new SpannableStringBuilder(replaceTON("TON " + formatter.format(crypto_amount / 1_000_000_000.0), tonBalanceTitle.getPaint(), .9f, true));
        int index = TextUtils.indexOf(ssb, ".");
        if (index >= 0) {
            ssb.setSpan(tonBalanceTitleSizeSpan, index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tonBalanceTitle.setText(ssb);
        tonBalanceSubtitle.setText("≈" + BillingController.getInstance().formatCurrency(amount, "USD"));
    }

    private SpannableStringBuilder lock;
    private Runnable setBalanceButtonText = () -> {
        final int now = getConnectionsManager().getCurrentTime();
        balanceButton.setEnabled(balanceEditTextValue > 0 || balanceBlockedUntil > now);
        if (now < balanceBlockedUntil) {
            balanceButton.setText(getString(R.string.BotStarsButtonWithdrawShortUntil), true);

            if (lock == null) {
                lock = new SpannableStringBuilder("l");
                ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.mini_switch_lock);
                coloredImageSpan.setTopOffset(1);
                lock.setSpan(coloredImageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            SpannableStringBuilder buttonLockedText = new SpannableStringBuilder();
            buttonLockedText.append(lock).append(untilString(balanceBlockedUntil - now));
            balanceButton.setSubText(buttonLockedText, true);

            if (withdrawalBulletin != null && withdrawalBulletin.getLayout() instanceof Bulletin.LottieLayout && withdrawalBulletin.getLayout().isAttachedToWindow()) {
                ((Bulletin.LottieLayout) withdrawalBulletin.getLayout()).textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotStarsWithdrawalToast, untilString(balanceBlockedUntil - now))));
            }

            AndroidUtilities.cancelRunOnUIThread(this.setBalanceButtonText);
            AndroidUtilities.runOnUIThread(this.setBalanceButtonText, 1000);
        } else {
            balanceButton.setSubText(null, true);
            balanceButton.setText(StarsIntroActivity.replaceStars(balanceEditTextAll ? getString(R.string.BotStarsButtonWithdrawShortAll) : LocaleController.formatPluralStringSpaced("BotStarsButtonWithdrawShort", (int) balanceEditTextValue), starRef), true);
        }
    };

    public static String untilString(int t) {
        final int d = t / (60 * 60 * 24);
        t -= d * (60 * 60 * 24);
        final int h = t / (60 * 60);
        t -= h * (60 * 60);
        final int m = t / 60;
        t -= m * 60;
        final int s = t;

        if (d == 0) {
            if (h == 0) {
                return String.format(Locale.ENGLISH, "%02d:%02d", m, s);
            }
            return String.format(Locale.ENGLISH, "%02d:%02d:%02d", h, m, s);
        }
        return LocaleController.formatString(R.string.PeriodDHM, String.format(Locale.ENGLISH, "%02d", d), String.format(Locale.ENGLISH, "%02d", h), String.format(Locale.ENGLISH, "%02d", m));
    }

    private boolean onItemLongClick(UItem item, View view, int pos, float x, float y) {
        return false;
    }

    private int stats_dc = -1;
    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsUpdated);
        checkStats();
        return super.onFragmentCreate();
    }

    private void checkStats() {
        TLRPC.TL_payments_starsRevenueStats stats = BotStarsController.getInstance(currentAccount).getStarsRevenueStats(bot_id);
        if (stats == lastStats && (stats == null ? null : stats.status) == lastStatsStatus) {
            return;
        }

        lastStats = stats;
        lastStatsStatus = stats == null ? null : stats.status;
        if (stats != null) {
            rate = stats.usd_rate;
            revenueChartData = StatisticActivity.createViewData(stats.revenue_graph, getString(R.string.BotStarsChartRevenue), 2);
            if (revenueChartData != null && revenueChartData.chartData != null && revenueChartData.chartData.lines != null && !revenueChartData.chartData.lines.isEmpty() && revenueChartData.chartData.lines.get(0) != null) {
                revenueChartData.showAll = true;
                revenueChartData.chartData.lines.get(0).colorKey = Theme.key_color_yellow;
                revenueChartData.chartData.yRate = (float) (1.0 / rate / 100.0);
            }
            setStarsBalance(stats.status.available_balance, stats.status.next_withdrawal_at);
            if (listView != null) {
                listView.adapter.update(true);
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.botStarsUpdated) {
            if ((long) args[0] == bot_id) {
                checkStats();
            }
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite)) > 0.721f;
    }


    private class NestedFrameLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3 {

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private NestedScrollingParentHelper nestedScrollingParentHelper;

        public NestedFrameLayout(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
            try {
                if (target == listView && transactionsLayout.isAttachedToWindow()) {
                    RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                    int bottom = ((View) transactionsLayout.getParent()).getBottom();
                    actionBar.setCastShadows(listView.getHeight() - bottom < 0);
                    if (listView.getHeight() - bottom >= 0) {
                        consumed[1] = dyUnconsumed;
                        innerListView.scrollBy(0, dyUnconsumed);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                        if (innerListView != null && innerListView.getAdapter() != null) {
                            innerListView.getAdapter().notifyDataSetChanged();
                        }
                    } catch (Throwable e2) {

                    }
                });
            }
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {

        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return super.onNestedPreFling(target, velocityX, velocityY);
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
            if (target == listView && transactionsLayout.isAttachedToWindow()) {
                boolean searchVisible = actionBar.isSearchFieldVisible();
                int t = ((View) transactionsLayout.getParent()).getTop() - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight();
                int bottom = ((View) transactionsLayout.getParent()).getBottom();
                if (dy < 0) {
                    boolean scrolledInner = false;
                    actionBar.setCastShadows(listView.getHeight() - bottom < 0);
                    if (listView.getHeight() - bottom >= 0) {
                        RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) innerListView.getLayoutManager();
                        int pos = linearLayoutManager.findFirstVisibleItemPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            RecyclerView.ViewHolder holder = innerListView.findViewHolderForAdapterPosition(pos);
                            int top = holder != null ? holder.itemView.getTop() : -1;
                            int paddingTop = innerListView.getPaddingTop();
                            if (top != paddingTop || pos != 0) {
                                consumed[1] = pos != 0 ? dy : Math.max(dy, (top - paddingTop));
                                innerListView.scrollBy(0, dy);
                                scrolledInner = true;
                            }
                        }
                    }
                    if (searchVisible) {
                        if (!scrolledInner && t < 0) {
                            consumed[1] = dy - Math.max(t, dy);
                        } else {
                            consumed[1] = dy;
                        }
                    }
                } else {
                    if (searchVisible) {
                        RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                        consumed[1] = dy;
                        if (t > 0) {
                            consumed[1] -= dy;
                        }
                        if (innerListView != null && consumed[1] > 0) {
                            innerListView.scrollBy(0, consumed[1]);
                        }
                    } else if (dy > 0) {
                        RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                        if (listView.getHeight() - bottom >= 0 && innerListView != null && !innerListView.canScrollVertically(1)) {
                            consumed[1] = dy;
                            listView.stopScroll();
                        }
                    }
                }
            }
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int axes, int type) {
            return axes == ViewCompat.SCROLL_AXIS_VERTICAL;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int axes, int type) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        }

        @Override
        public void onStopNestedScroll(View target, int type) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
        }

        @Override
        public void onStopNestedScroll(View child) {

        }
    }

    private void initWithdraw(boolean stars, long stars_amount, TLRPC.InputCheckPasswordSRP password, TwoStepVerificationActivity passwordFragment) {
        Activity parentActivity = getParentActivity();
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (parentActivity == null || currentUser == null) return;

        TLObject r;
        if (stars) {
            TLRPC.TL_payments_getStarsRevenueWithdrawalUrl req = new TLRPC.TL_payments_getStarsRevenueWithdrawalUrl();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(bot_id);
            req.password = password != null ? password : new TLRPC.TL_inputCheckPasswordEmpty();
            req.stars = stars_amount;
            r = req;
        } else {
            TL_stats.TL_getBroadcastRevenueWithdrawalUrl req = new TL_stats.TL_getBroadcastRevenueWithdrawalUrl();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(bot_id);
            req.password = password != null ? password : new TLRPC.TL_inputCheckPasswordEmpty();
            r = req;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(r, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(LocaleController.getString(R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(parentActivity);
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(parentActivity);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.WithdrawChannelAlertText)));
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(parentActivity);
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(parentActivity);
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(parentActivity);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(parentActivity);
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(parentActivity);
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(parentActivity);
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(parentActivity);
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString(R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
                    }
                    if (passwordFragment != null) {
                        passwordFragment.showDialog(builder.create());
                    } else {
                        showDialog(builder.create());
                    }
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initWithdraw(stars, stars_amount, passwordFragment.getNewSrpPassword(), passwordFragment);
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
                if (response instanceof TL_stats.TL_broadcastRevenueWithdrawalUrl) {
                    Browser.openUrlInSystemBrowser(getContext(), ((TL_stats.TL_broadcastRevenueWithdrawalUrl) response).url);
                } else if (response instanceof TLRPC.TL_payments_starsRevenueWithdrawalUrl) {
                    balanceEditTextAll = true;
                    Browser.openUrlInSystemBrowser(getContext(), ((TLRPC.TL_payments_starsRevenueWithdrawalUrl) response).url);
                }
            }
        }));
    }
}

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINK_NBSP;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.makeBlurBitmap;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.DynamicDrawableSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
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
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class ChannelMonetizationLayout extends FrameLayout {

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;
    private final long dialogId;
    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private int currentBoostLevel;

    private final CharSequence titleInfo;
    private final CharSequence balanceInfo;

    private final LinearLayout balanceLayout;
    private final RelativeSizeSpan balanceTitleSizeSpan;
    private final AnimatedTextView balanceTitle;
    private final AnimatedTextView balanceSubtitle;
    private final ButtonWithCounterView balanceButton;
    private int shakeDp = 4;

    private boolean transfering;

    private final UniversalRecyclerView listView;
    private final FrameLayout progress;

    private DecimalFormat formatter;

    public ChannelMonetizationLayout(
        Context context,
        BaseFragment fragment,
        int currentAccount,
        long dialogId,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        formatter = new DecimalFormat("#.##", symbols);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(12);
        formatter.setGroupingUsed(false);

        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        initLevel();
        loadTransactions();

        titleInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.MonetizationInfo, 50), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
            showLearnSheet();
        }, resourcesProvider), true);
        balanceInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled ? R.string.MonetizationBalanceInfo : R.string.MonetizationBalanceInfoNotAvailable), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
            showLearnSheet();
        }), true);

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

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
        balanceLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        balanceLayout.setPadding(0, 0, 0, dp(17));

        balanceTitle = new AnimatedTextView(context, false, true, true);
        balanceTitle.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        balanceTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        balanceTitle.setTextSize(dp(32));
        balanceTitle.setGravity(Gravity.CENTER);
        balanceTitleSizeSpan = new RelativeSizeSpan(65f / 96f);
        balanceLayout.addView(balanceTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 15, 22, 0));

        balanceSubtitle = new AnimatedTextView(context, true, true, true);
        balanceSubtitle.setGravity(Gravity.CENTER);
        balanceSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        balanceSubtitle.setTextSize(dp(14));
        balanceLayout.addView(balanceSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 17, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 4, 22, 0));

        final CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(dp(15), dpf2(2), Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider)) {
            @Override
            public int getIntrinsicWidth() {
                return dp(24);
            }
            @Override
            public int getIntrinsicHeight() {
                return dp(24);
            }
        };
        circularProgressDrawable.setBounds(0, 0, dp(24), dp(24));

        balanceButton = new ButtonWithCounterView(context, resourcesProvider) {
            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == circularProgressDrawable || super.verifyDrawable(who);
            }
        };
        balanceButton.setEnabled(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled);
        circularProgressDrawable.setCallback(balanceButton);
        balanceButton.setText(getString(R.string.MonetizationWithdraw), false);
        balanceButton.setVisibility(View.GONE);
        balanceButton.setOnClickListener(v -> {
            if (!v.isEnabled()) {
                return;
            }
            TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
            passwordFragment.setDelegate(1, password -> initWithdraw(password, passwordFragment));
            fragment.presentFragment(passwordFragment);
        });
        balanceLayout.addView(balanceButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18, 13, 18, 0));

        listView = new UniversalRecyclerView(fragment, this::fillItems, this::onClick, this::onLongClick);
        addView(listView);

        LinearLayout progressLayout = new LinearLayout(context);
        progressLayout.setOrientation(LinearLayout.VERTICAL);

        progress = new FrameLayout(context);
        progress.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        progress.addView(progressLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAutoRepeat(true);
        imageView.setAnimation(R.raw.statistic_preload, 120, 120);
        imageView.playAnimation();

        TextView loadingTitle = new TextView(context);
        loadingTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        loadingTitle.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        loadingTitle.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        loadingTitle.setTag(Theme.key_player_actionBarTitle);
        loadingTitle.setText(getString("LoadingStats", R.string.LoadingStats));
        loadingTitle.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView loadingSubtitle = new TextView(context);
        loadingSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        loadingSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        loadingSubtitle.setTag(Theme.key_player_actionBarSubtitle);
        loadingSubtitle.setText(getString("LoadingStatsDescription", R.string.LoadingStatsDescription));
        loadingSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);

        progressLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        progressLayout.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        progressLayout.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        addView(progress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private void initWithdraw(TLRPC.InputCheckPasswordSRP password, TwoStepVerificationActivity passwordFragment) {
        if (fragment == null) return;
        Activity parentActivity = fragment.getParentActivity();
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (parentActivity == null || currentUser == null) return;

        TL_stats.TL_getBroadcastRevenueWithdrawalUrl req = new TL_stats.TL_getBroadcastRevenueWithdrawalUrl();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
        req.password = password != null ? password : new TLRPC.TL_inputCheckPasswordEmpty();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setTitle(LocaleController.getString("EditAdminTransferAlertTitle", R.string.EditAdminTransferAlertTitle));

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
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText1", R.string.EditAdminTransferAlertText1)));
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
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText2", R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString("EditAdminTransferSetPassword", R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> fragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(parentActivity);
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString("EditAdminTransferAlertText3", R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                    }
                    if (passwordFragment != null) {
                        passwordFragment.showDialog(builder.create());
                    } else {
                        fragment.showDialog(builder.create());
                    }
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TLRPC.account_Password currentPassword = (TLRPC.account_Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initWithdraw(passwordFragment.getNewSrpPassword(), passwordFragment);
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
                    Browser.openUrl(getContext(), ((TL_stats.TL_broadcastRevenueWithdrawalUrl) response).url);
                }
            }
        }));
    }

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
        SpannableStringBuilder ssb = new SpannableStringBuilder(replaceTON("TON " + formatter.format(crypto_amount / 1_000_000_000.0), balanceTitle.getPaint(), .9f, true));
        int index = TextUtils.indexOf(ssb, ".");
        if (index >= 0) {
            ssb.setSpan(balanceTitleSizeSpan, index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        balanceTitle.setText(ssb);
        balanceSubtitle.setText("~" + BillingController.getInstance().formatCurrency(amount, "USD"));
    }

    private void initLevel() {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        if (chat != null) {
            currentBoostLevel = chat.level;
        }
        MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, boostsStatus -> AndroidUtilities.runOnUIThread(() -> {
            this.boostsStatus = boostsStatus;
            if (boostsStatus != null) {
                currentBoostLevel = boostsStatus.level;
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }));

        TLObject req;
        if (ChatObject.isMegagroup(chat)) {
            return;
        } else {
            TL_stats.TL_getBroadcastRevenueStats getBroadcastStats = new TL_stats.TL_getBroadcastRevenueStats();
            getBroadcastStats.dark = Theme.isCurrentThemeDark();
            getBroadcastStats.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
            req = getBroadcastStats;
        }
        int stats_dc = -1;
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
        if (chatFull != null) {
            stats_dc = chatFull.stats_dc;
            switchOffValue = chatFull.restricted_sponsored;
        }
        if (stats_dc == -1) return;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_stats.TL_broadcastRevenueStats) {
                TL_stats.TL_broadcastRevenueStats stats = (TL_stats.TL_broadcastRevenueStats) res;

                impressionsChart = StatisticActivity.createViewData(stats.top_hours_graph, getString(R.string.MonetizationGraphImpressions), 0);
                if (stats.revenue_graph != null) {
                    stats.revenue_graph.rate = (float) (1_000_000_000.0 / 100.0 / stats.usd_rate);
                }
                revenueChart = StatisticActivity.createViewData(stats.revenue_graph, getString(R.string.MonetizationGraphRevenue), 2);
                if (impressionsChart != null) {
                    impressionsChart.useHourFormat = true;
                }

                availableValue.crypto_amount = stats.available_balance;
                availableValue.amount = (long) (availableValue.crypto_amount / 1_000_000_000.0 * stats.usd_rate * 100.0);
                setBalance(availableValue.crypto_amount, availableValue.amount);
                availableValue.currency = "USD";
                lastWithdrawalValue.crypto_amount = stats.current_balance;
                lastWithdrawalValue.amount = (long) (lastWithdrawalValue.crypto_amount / 1_000_000_000.0 * stats.usd_rate * 100.0);
                lastWithdrawalValue.currency = "USD";
                lifetimeValue.crypto_amount = stats.overall_revenue;
                lifetimeValue.amount = (long) (lifetimeValue.crypto_amount / 1_000_000_000.0 * stats.usd_rate * 100.0);
                lifetimeValue.currency = "USD";
                proceedsAvailable = true;

                balanceButton.setVisibility(stats.available_balance > 0 || BuildVars.DEBUG_PRIVATE_VERSION ? View.VISIBLE : View.GONE);

                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }

                progress.animate().alpha(0).setDuration(380).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                    progress.setVisibility(View.GONE);
                }).start();

                checkLearnSheet();
            }
        }), null, null, 0, stats_dc, ConnectionsManager.ConnectionTypeGeneric, true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkLearnSheet();
    }

    private void checkLearnSheet() {
        if (isAttachedToWindow() && proceedsAvailable && MessagesController.getGlobalMainSettings().getBoolean("monetizationadshint", true)) {
            showLearnSheet();
            MessagesController.getGlobalMainSettings().edit().putBoolean("monetizationadshint", false).apply();
        }
    }

    private boolean switchOffValue = false;

    private StatisticActivity.ChartViewData impressionsChart;
    private StatisticActivity.ChartViewData revenueChart;
    private boolean proceedsAvailable = false;
    private final ProceedOverview availableValue =      ProceedOverview.as(getString(R.string.MonetizationOverviewAvailable));
    private final ProceedOverview lastWithdrawalValue = ProceedOverview.as(getString(R.string.MonetizationOverviewLastWithdrawal));
    private final ProceedOverview lifetimeValue =       ProceedOverview.as(getString(R.string.MonetizationOverviewTotal));

    private final ArrayList<TL_stats.BroadcastRevenueTransaction> transactions = new ArrayList<>();
    private int transactionsTotalCount;

    private final static int CHECK_SWITCHOFF = 1;
    private final static int BUTTON_LOAD_MORE_TRANSACTIONS = 2;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int stats_dc = -1;
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
        if (chatFull != null) {
            stats_dc = chatFull.stats_dc;
        }
        items.add(UItem.asCenterShadow(titleInfo));
        if (impressionsChart != null && !impressionsChart.isEmpty) {
            items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_BAR_LINEAR, stats_dc, impressionsChart));
            items.add(UItem.asShadow(-1, null));
        }
        if (revenueChart != null && !revenueChart.isEmpty) {
            items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_STACKBAR, stats_dc, revenueChart));
            items.add(UItem.asShadow(-2, null));
        }
        if (proceedsAvailable) {
            items.add(UItem.asBlackHeader(getString(R.string.MonetizationOverview)));
            items.add(UItem.asProceedOverview(availableValue));
            items.add(UItem.asProceedOverview(lastWithdrawalValue));
            items.add(UItem.asProceedOverview(lifetimeValue));
            items.add(UItem.asShadow(-3, null));
        }
        if (chat != null && chat.creator) {
            items.add(UItem.asBlackHeader(getString(R.string.MonetizationBalance)));
            items.add(UItem.asCustom(balanceLayout));
            items.add(UItem.asShadow(-4, balanceInfo));
        }
        if (!transactions.isEmpty() || transactionsTotalCount > 0) {
            items.add(UItem.asBlackHeader(getString(R.string.MonetizationTransactions)));
            for (TL_stats.BroadcastRevenueTransaction t : transactions) {
                items.add(UItem.asTransaction(t));
            }
            if (transactionsTotalCount - transactions.size() > 0) {
                items.add(UItem.asButton(BUTTON_LOAD_MORE_TRANSACTIONS, R.drawable.arrow_more, formatPluralString("MonetizationMoreTransactions", transactionsTotalCount - transactions.size())).accent());
            }
            items.add(UItem.asShadow(-5, null));
        }
        if (chat != null && chat.creator) {
            final int switchOffLevel = MessagesController.getInstance(currentAccount).channelRestrictSponsoredLevelMin;
            items.add(UItem.asCheck(CHECK_SWITCHOFF, PeerColorActivity.withLevelLock(getString(R.string.MonetizationSwitchOff), currentBoostLevel < switchOffLevel ? switchOffLevel : 0)).setChecked(currentBoostLevel >= switchOffLevel && switchOffValue));
            items.add(UItem.asShadow(-6, getString(R.string.MonetizationSwitchOffInfo)));
        }
        items.add(UItem.asShadow(-7, null));
        items.add(UItem.asShadow(-8, null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == CHECK_SWITCHOFF) {
            if (currentBoostLevel < MessagesController.getInstance(currentAccount).channelRestrictSponsoredLevelMin) {
                if (boostsStatus == null) return;
                LimitReachedBottomSheet sheet = new LimitReachedBottomSheet(fragment, getContext(), LimitReachedBottomSheet.TYPE_BOOSTS_FOR_ADS, currentAccount, resourcesProvider);
                sheet.setDialogId(dialogId);
                sheet.setBoostsStats(boostsStatus, true);
                MessagesController.getInstance(currentAccount).getBoostsController().userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> {
                    sheet.setCanApplyBoost(canApplyBoost);
                    fragment.showDialog(sheet);
                });
                return;
            }
            switchOffValue = !switchOffValue;
            AndroidUtilities.cancelRunOnUIThread(sendCpmUpdateRunnable);
            AndroidUtilities.runOnUIThread(sendCpmUpdateRunnable, 1000);
            listView.adapter.update(true);
        } else if (item.object instanceof TL_stats.BroadcastRevenueTransaction) {
            showTransactionSheet((TL_stats.BroadcastRevenueTransaction) item.object, dialogId);
        } else if (item.id == BUTTON_LOAD_MORE_TRANSACTIONS) {
            loadTransactions();
        }
    }

    private boolean loadingTransactions = false;
    private void loadTransactions() {
        if (loadingTransactions) return;
        if (transactions.size() >= transactionsTotalCount && transactionsTotalCount != 0) return;
//        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
//        if (chat == null || !chat.creator) {
//            return;
//        }

        loadingTransactions = true;
        TL_stats.TL_getBroadcastRevenueTransactions req = new TL_stats.TL_getBroadcastRevenueTransactions();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
        req.offset = transactions.size();
        req.limit = transactions.isEmpty() ? 5 : 20;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_stats.TL_broadcastRevenueTransactions) {
                TL_stats.TL_broadcastRevenueTransactions r = (TL_stats.TL_broadcastRevenueTransactions) res;
                transactionsTotalCount = r.count;
                transactions.addAll(r.transactions);

                if (listView != null) {
                    listView.adapter.update(true);
                }
                loadingTransactions = false;
            } else if (err != null) {
                BulletinFactory.showError(err);
            }
        }));
    }

    private final Runnable sendCpmUpdateRunnable = this::sendCpmUpdate;
    private void sendCpmUpdate() {
        AndroidUtilities.cancelRunOnUIThread(sendCpmUpdateRunnable);

        TLRPC.TL_channels_restrictSponsoredMessages req = new TLRPC.TL_channels_restrictSponsoredMessages();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
        req.restricted = switchOffValue;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                BulletinFactory.showError(err);
            } else if (res instanceof TLRPC.Updates) {
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
            }
        }));
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private static final long DIAMOND_EMOJI = 5471952986970267163L;
    private static HashMap<Integer, SpannableString> tonString;
    public static CharSequence replaceTON(CharSequence text, TextPaint textPaint) {
        return replaceTON(text, textPaint, 1f, true);
    }
    public static CharSequence replaceTON(CharSequence text, TextPaint textPaint, boolean large) {
        return replaceTON(text, textPaint, 1f, large);
    }

    public static CharSequence replaceTON(CharSequence text, TextPaint textPaint, float scale, boolean large) {
        return replaceTON(text, textPaint, scale, 0, large);
    }

    public static CharSequence replaceTON(CharSequence text, TextPaint textPaint, float scale, float translateY, boolean large) {
        if (ChannelMonetizationLayout.tonString == null) {
            ChannelMonetizationLayout.tonString = new HashMap<>();
        }
        final int key = textPaint.getFontMetricsInt().bottom * (large ? 1 : -1) * (int) (100 * scale);
        SpannableString tonString = ChannelMonetizationLayout.tonString.get(key);
        if (tonString == null) {
            tonString = new SpannableString("T");
            if (large) {
                ColoredImageSpan span = new ColoredImageSpan(R.drawable.ton);
                span.setScale(scale, scale);
                span.setColorKey(Theme.key_windowBackgroundWhiteBlueText2);
                span.setRelativeSize(textPaint.getFontMetricsInt());
                span.spaceScaleX = .9f;
                tonString.setSpan(span, 0, tonString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_ton);
                span.setScale(scale, scale);
                span.setTranslateY(translateY);
                span.spaceScaleX = .95f;
                tonString.setSpan(span, 0, tonString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ChannelMonetizationLayout.tonString.put(key, tonString);
        }
        text = AndroidUtilities.replaceMultipleCharSequence("TON", text, tonString);
        return text;
    }

    public static class ProceedOverviewCell extends LinearLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final LinearLayout layout;
        private final AnimatedEmojiSpan.TextViewEmojis cryptoAmountView;
        private final TextView amountView;
        private final TextView titleView;

        private final DecimalFormat formatter;

        public ProceedOverviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setOrientation(VERTICAL);

            layout = new LinearLayout(context);
            layout.setOrientation(HORIZONTAL);
            addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 22, 9, 22, 0));

            cryptoAmountView = new AnimatedEmojiSpan.TextViewEmojis(context);
            cryptoAmountView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            cryptoAmountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            cryptoAmountView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            layout.addView(cryptoAmountView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 5, 0));

            amountView = new AnimatedEmojiSpan.TextViewEmojis(context);
            amountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            amountView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            layout.addView(amountView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 22, 5, 22, 9));

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setDecimalSeparator('.');
            formatter = new DecimalFormat("#.##", symbols);
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(12);
            formatter.setGroupingUsed(false);
        }

        public void set(ProceedOverview value) {
            titleView.setText(value.text);
            SpannableStringBuilder cryptoAmount = new SpannableStringBuilder(replaceTON("TON " + formatter.format(value.crypto_amount / 1_000_000_000.0), cryptoAmountView.getPaint(), .87f, true));
            int index = TextUtils.indexOf(cryptoAmount, ".");
            if (index >= 0) {
                cryptoAmount.setSpan(new RelativeSizeSpan(13f / 16f), index, cryptoAmount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            cryptoAmountView.setText(cryptoAmount);
            amountView.setText("~" + BillingController.getInstance().formatCurrency(value.amount, value.currency));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public static class ProceedOverview {
        public CharSequence text;
        public long crypto_amount;
        public long amount;
        public String currency;

        public static ProceedOverview as(CharSequence text) {
            ProceedOverview o = new ProceedOverview();
            o.text = text;
            return o;
        }
    }

    public static class TransactionCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final AnimatedEmojiSpan.TextViewEmojis valueText;
        private final LinearLayout layout;
        private final TextView titleView;
//        private final TextView addressView;
        private final TextView dateView;

        private final DecimalFormat formatter;

        public TransactionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 17, 9, 130, 9));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

//            addressView = new TextView(context);
//            addressView.setSingleLine(false);
//            addressView.setLines(2);
//            addressView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
//            addressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
//            addressView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
//            layout.addView(addressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            dateView = new TextView(context);
            dateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            layout.addView(dateView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            valueText = new AnimatedEmojiSpan.TextViewEmojis(context);
            valueText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            valueText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(valueText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 18, 0));

            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setDecimalSeparator('.');
            formatter = new DecimalFormat("#.##", symbols);
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(12);
            formatter.setGroupingUsed(false);
        }

        private boolean needDivider;

        public void set(TL_stats.BroadcastRevenueTransaction transaction, boolean divider) {
            int type;
            long amount;
            boolean failed = false;
            if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionWithdrawal) {
                TL_stats.TL_broadcastRevenueTransactionWithdrawal t = (TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction;
                titleView.setText(getString(R.string.MonetizationTransactionWithdraw));
                if (t.pending) {
                    dateView.setText(getString(R.string.MonetizationTransactionPending));
                } else {
                    failed = t.failed;
                    dateView.setText(LocaleController.formatShortDateTime(t.date) + (failed ? " — " + getString(R.string.MonetizationTransactionNotCompleted) : ""));
                }
                amount = t.amount;
                type = -1;
            } else if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionProceeds) {
                TL_stats.TL_broadcastRevenueTransactionProceeds t = (TL_stats.TL_broadcastRevenueTransactionProceeds) transaction;
                titleView.setText(getString(R.string.MonetizationTransactionProceed));
                dateView.setText(LocaleController.formatShortDateTime(t.from_date) + " - " + LocaleController.formatShortDateTime(t.to_date));
                amount = t.amount;
                type = +1;
            } else if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionRefund) {
                TL_stats.TL_broadcastRevenueTransactionRefund t = (TL_stats.TL_broadcastRevenueTransactionRefund) transaction;
                titleView.setText(getString(R.string.MonetizationTransactionRefund));
                dateView.setText(LocaleController.formatShortDateTime(t.from_date));
                amount = t.amount;
                type = +1;
            } else {
                return;
            }

            dateView.setTextColor(Theme.getColor(failed ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

            SpannableStringBuilder value = new SpannableStringBuilder();
            value.append(type < 0 ? "-" : "+");
            value.append(formatter.format((Math.abs(amount) / 1_000_000_000.0)));
            value.append(" TON");
            int index = TextUtils.indexOf(value, ".");
            if (index >= 0) {
                value.setSpan(new RelativeSizeSpan(1.15f), 0, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            valueText.setText(value);
            valueText.setTextColor(Theme.getColor(type < 0 ? Theme.key_text_RedBold : Theme.key_avatar_nameInMessageGreen, resourcesProvider));

            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : Theme.dividerPaint;
                if (dividerPaint != null) {
                    canvas.drawLine(LocaleController.isRTL ? 0 : dp(17), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(17) : 0), getMeasuredHeight() - 1, dividerPaint);
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public static boolean validateTONAddress(String address) {
        try {
            byte[] bytes = Base64.decode(address, Base64.URL_SAFE);
            if (bytes.length != 36) return false;
            byte[] addr = Arrays.copyOfRange(bytes, 0, 34);
            byte[] crc = Arrays.copyOfRange(bytes, 34, 36);
            byte[] calculatedCrc = crc16(addr);
            if (
                crc[0] != calculatedCrc[0] ||
                crc[1] != calculatedCrc[1]
            ) {
                return false;
            }
            boolean isTestOnly = true;
            int tag = addr[0];
            if ((tag & 0x80) != 0) {
                isTestOnly = false;
                tag = tag ^ 0x80;
            }
            if (!BuildVars.DEBUG_VERSION && isTestOnly) {
                return false;
            }
            if (tag != 0x11 && tag != 0x51) {
                return false;
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private static byte[] crc16(byte[] data) {
        final int poly = 0x1021;
        int reg = 0;
        byte[] message = new byte[data.length + 2];
        System.arraycopy(data, 0, message, 0, data.length);

        for (byte b : message) {
            int mask = 0x80;
            while (mask > 0) {
                reg <<= 1;
                if ((b & mask) != 0) {
                    reg += 1;
                }
                mask >>= 1;
                if (reg > 0xffff) {
                    reg &= 0xffff;
                    reg ^= poly;
                }
            }
        }

        byte[] result = new byte[2];
        result[0] = (byte) ((reg >> 8) & 0xff);
        result[1] = (byte) (reg & 0xff);

        return result;
    }

    private void showTransactionSheet(TL_stats.BroadcastRevenueTransaction transaction, long dialogId) {
        BottomSheet sheet = new BottomSheet(getContext(), false, resourcesProvider);
        sheet.fixNavigationBar();

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        long dateFrom, dateTo;
        CharSequence title;
        int type;
        long amount;
        boolean pending = false;
        boolean failed = false;
        if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionWithdrawal) {
            TL_stats.TL_broadcastRevenueTransactionWithdrawal t = (TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction;
            title = getString(R.string.MonetizationTransactionDetailWithdraw);
            dateFrom = t.date;
            dateTo = 0;
            type = -1;
            amount = t.amount;
            pending = t.pending;
            failed = t.failed;
        } else if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionProceeds) {
            TL_stats.TL_broadcastRevenueTransactionProceeds t = (TL_stats.TL_broadcastRevenueTransactionProceeds) transaction;
            title = getString(R.string.MonetizationTransactionDetailProceed);
            dateFrom = t.from_date;
            dateTo = t.to_date;
            type = +1;
            amount = t.amount;
        } else if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionRefund) {
            TL_stats.TL_broadcastRevenueTransactionRefund t = (TL_stats.TL_broadcastRevenueTransactionRefund) transaction;
            title = getString(R.string.MonetizationTransactionDetailRefund);
            dateFrom = t.from_date;
            dateTo = 0;
            type = +1;
            amount = t.amount;
        } else {
            return;
        }

        TextView textView = new TextView(getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setTextColor(Theme.getColor(type < 0 ? Theme.key_text_RedBold : Theme.key_avatar_nameInMessageGreen));
        SpannableStringBuilder amountText = new SpannableStringBuilder();
        amountText.append(type < 0 ? "-" : "+");
        amountText.append(formatter.format(Math.round(Math.abs(amount) / 1_000_000_000.0 * 100000.0) / 100000.0));
        amountText.append(" TON");
        int index = TextUtils.indexOf(amountText, ".");
        if (index >= 0) {
            amountText.setSpan(new RelativeSizeSpan(24f / 18f), 0, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(amountText);
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24, 0, 6));

        textView = new TextView(getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        if (pending) {
            textView.setText(getString(R.string.MonetizationTransactionPending));
        } else if (dateFrom == 0) {
            textView.setText(LocaleController.formatShortDateTime(dateTo));
        } else if (dateTo == 0) {
            textView.setText(LocaleController.formatShortDateTime(dateFrom));
        } else {
            textView.setText(LocaleController.formatShortDateTime(dateFrom) + " - " + LocaleController.formatShortDateTime(dateTo));
        }
        if (failed) {
            textView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
            textView.setText(TextUtils.concat(textView.getText(), " — ", getString(R.string.MonetizationTransactionNotCompleted)));
        }
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        textView = new TextView(getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setText(title);
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0));

//        if (transaction.type == Transaction.TYPE_WITHDRAW) {
//            textView = new TextView(getContext());
//            textView.setPadding(dp(14), dp(8), dp(14), dp(8));
//            textView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider))));
//            textView.setOnClickListener(v -> {
//                AndroidUtilities.addToClipboard(transaction.address);
//                BulletinFactory.of(sheet.getContainer(), resourcesProvider).createCopyBulletin(getString(R.string.TextCopied)).show(true);
//            });
//            textView.setGravity(Gravity.CENTER);
//            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
//            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
//            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
//            int center = transaction.address.length() / 2;
//            textView.setText(transaction.address.substring(0, center) + "\n" + transaction.address.substring(center));
//            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
//        } else {
        if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionProceeds) {
            FrameLayout chipLayout = new FrameLayout(getContext());
            chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), dp(28), Theme.getColor(Theme.key_groupcreate_spanBackground, resourcesProvider)));

            String ownerName;
            TLObject owner;
            if (dialogId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                ownerName = chat == null ? "" : chat.title;
                owner = chat;
            } else {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                ownerName = UserObject.getUserName(user);
                owner = user;
            }

            BackupImageView chipAvatar = new BackupImageView(getContext());
            chipAvatar.setRoundRadius(dp(28));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(owner);
            chipAvatar.setForUserOrChat(owner, avatarDrawable);
            chipLayout.addView(chipAvatar, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP));

            TextView chipText = new TextView(getContext());
            chipText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            chipText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chipText.setSingleLine();
            chipText.setText(ownerName);
            chipLayout.addView(chipText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 37, 0, 10, 0));

            layout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 42, 10, 42, 0));
        }

        ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);
        if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionWithdrawal && (((TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction).flags & 2) != 0) {
            TL_stats.TL_broadcastRevenueTransactionWithdrawal t = (TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction;
            button.setText(getString(R.string.MonetizationTransactionDetailWithdrawButton), false);
            button.setOnClickListener(v -> {
                Browser.openUrl(getContext(), t.transaction_url);
            });
        } else {
            button.setText(getString(R.string.OK), false);
            button.setOnClickListener(v -> {
                sheet.dismiss();
            });
        }
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP, 18, 30, 18, 14));

        sheet.setCustomView(layout);

        fragment.showDialog(sheet);
    }

    private void showLearnSheet() {
        BottomSheet sheet = new BottomSheet(getContext(), false, resourcesProvider);
        sheet.fixNavigationBar();

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(8), 0, dp(8), 0);

        RLottieImageView imageView = new RLottieImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.large_monetize);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        TextView textView = new TextView(getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setText(getString(R.string.MonetizationInfoTitle));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 25));

        layout.addView(
                new FeatureCell(getContext(), R.drawable.msg_channel, getString(R.string.MonetizationInfoFeature1Name), getString(R.string.MonetizationInfoFeature1Text)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16)
        );

        layout.addView(
                new FeatureCell(getContext(), R.drawable.menu_feature_split, getString(R.string.MonetizationInfoFeature2Name), getString(R.string.MonetizationInfoFeature2Text)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16)
        );

        layout.addView(
                new FeatureCell(getContext(), R.drawable.menu_feature_withdrawals, getString(R.string.MonetizationInfoFeature3Name), getString(R.string.MonetizationInfoFeature3Text)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16)
        );

        View separator = new View(getContext());
        separator.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        layout.addView(separator, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 0, 12, 0));

        textView = new AnimatedEmojiSpan.TextViewEmojis(getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        SpannableString animatedDiamond = new SpannableString("💎");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.ton);
        span.setScale(.9f, .9f);
        span.setColorKey(Theme.key_windowBackgroundWhiteBlueText2);
        span.setRelativeSize(textView.getPaint().getFontMetricsInt());
        span.spaceScaleX = .9f;
        animatedDiamond.setSpan(span, 0, animatedDiamond.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(AndroidUtilities.replaceCharSequence("💎", getString(R.string.MonetizationInfoTONTitle), animatedDiamond));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 20, 8, 0));

        textView = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setText(AndroidUtilities.withLearnMore(AndroidUtilities.replaceTags(getString(R.string.MonetizationInfoTONText)), () -> Browser.openUrl(getContext(), getString(R.string.MonetizationInfoTONLink))));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 9, 28, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);
        button.setText(getString(R.string.GotIt), false);
        button.setOnClickListener(v -> {
            sheet.dismiss();
        });
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP, 10, 25, 10, 14));

        sheet.setCustomView(layout);

        fragment.showDialog(sheet);
    }

    private class FeatureCell extends FrameLayout {
        public FeatureCell(Context context, int icon, CharSequence header, CharSequence text) {
            super(context);

            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            imageView.setImageResource(icon);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 0, 5, 18, 0));

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 42, 0, 0, 0));

            TextView textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setText(header);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            textView.setText(text);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(325)), MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec);
        }
    }
}

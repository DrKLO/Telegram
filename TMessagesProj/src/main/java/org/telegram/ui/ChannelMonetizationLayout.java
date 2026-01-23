package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINK_NBSP;
import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.makeBlurBitmap;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.tgnet.ConnectionsManager.DEFAULT_DATACENTER_ID;
import static org.telegram.ui.ChatEditActivity.applyNewSpan;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
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
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
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
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stars.BotStarsActivity;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.ui.bots.ChannelAffiliateProgramsFragment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class ChannelMonetizationLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3 {

    public static ChannelMonetizationLayout instance;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount;
    public final long dialogId;
    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private int currentBoostLevel;

    private final CharSequence titleInfo;
    private final CharSequence balanceInfo;
    private final CharSequence proceedsInfo;
    private final CharSequence starsBalanceInfo;

    private final LinearLayout balanceLayout;
    private final RelativeSizeSpan balanceTitleSizeSpan;
    private final AnimatedTextView balanceTitle;
    private final AnimatedTextView balanceSubtitle;
    private final ButtonWithCounterView balanceButton;
    private int shakeDp = 4;

    private int starsBalanceBlockedUntil;
    private final LinearLayout starsBalanceLayout;
    private final RelativeSizeSpan starsBalanceTitleSizeSpan;
    private TL_stars.StarsAmount starsBalance = TL_stars.StarsAmount.ofStars(0);
    private final AnimatedTextView starsBalanceTitle;
    private final AnimatedTextView starsBalanceSubtitle;
    private final ButtonWithCounterView starsBalanceButton;
    private ColoredImageSpan[] starRef = new ColoredImageSpan[1];
    private final LinearLayout starsBalanceButtonsLayout;
    private final ButtonWithCounterView starsAdsButton;
    private OutlineTextContainerView starsBalanceEditTextContainer;
    private boolean starsBalanceEditTextIgnore = false;
    private boolean starsBalanceEditTextAll = true;
    private long starsBalanceEditTextValue;
    private EditTextBoldCursor starsBalanceEditText;

    private Bulletin withdrawalBulletin;

    private boolean transfering;

    private final UniversalRecyclerView listView;
    private final FrameLayout progress;

    private DecimalFormat formatter;

    private final ChannelTransactionsView transactionsLayout;
    public void updateList() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    public final boolean tonRevenueAvailable;
    public final boolean starsRevenueAvailable;

    public ChannelMonetizationLayout(
        Context context,
        BaseFragment fragment,
        int currentAccount,
        long dialogId,
        Theme.ResourcesProvider resourcesProvider,

        boolean tonRevenueAvailable,
        boolean starsRevenueAvailable
    ) {
        super(context);

        this.tonRevenueAvailable = tonRevenueAvailable;
        this.starsRevenueAvailable = starsRevenueAvailable;

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

        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

        titleInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(formatString(R.string.MonetizationInfo, 50), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
            fragment.showDialog(makeLearnSheet(context, false, resourcesProvider));
        }, resourcesProvider), true);
        balanceInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled ? R.string.MonetizationBalanceInfo : R.string.MonetizationBalanceInfoNotAvailable), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
            Browser.openUrl(getContext(), getString(R.string.MonetizationBalanceInfoLink));
        }), true);
        final int proceedsInfoText = starsRevenueAvailable && tonRevenueAvailable ? R.string.MonetizationProceedsStarsTONInfo : starsRevenueAvailable ? R.string.MonetizationProceedsStarsInfo : R.string.MonetizationProceedsTONInfo;
        final int proceedsInfoLink = starsRevenueAvailable && tonRevenueAvailable ? R.string.MonetizationProceedsStarsTONInfoLink : starsRevenueAvailable ? R.string.MonetizationProceedsStarsInfoLink : R.string.MonetizationProceedsTONInfoLink;
        proceedsInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(proceedsInfoText), -1, REPLACING_TAG_TYPE_LINK_NBSP, () -> {
            Browser.openUrl(getContext(), getString(proceedsInfoLink));
        }, resourcesProvider), true);
        starsBalanceInfo = AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(ChatObject.isChannelAndNotMegaGroup(chat) ? R.string.MonetizationStarsInfo : R.string.MonetizationStarsInfoGroup), () -> {
            Browser.openUrl(getContext(), getString(R.string.MonetizationStarsInfoLink));
        }), true);

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        transactionsLayout = new ChannelTransactionsView(context, currentAccount, dialogId, fragment.getClassGuid(), this::updateList, resourcesProvider);

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
        balanceTitle.setTypeface(AndroidUtilities.bold());
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

        balanceButton = new ButtonWithCounterView(context, resourcesProvider);
        balanceButton.setEnabled(MessagesController.getInstance(currentAccount).channelRevenueWithdrawalEnabled);
        balanceButton.setText(getString(R.string.MonetizationWithdraw), false);
        balanceButton.setVisibility(View.GONE);
        balanceButton.setOnClickListener(v -> {
            if (!v.isEnabled() || balanceButton.isLoading() || ChannelMonetizationLayout.this.starsBalanceButton != null && ChannelMonetizationLayout.this.starsBalanceButton.isLoading()) {
                return;
            }
            TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
            passwordFragment.setDelegate(1, password -> initWithdraw(false, password, passwordFragment));
            balanceButton.setLoading(true);
            passwordFragment.preload(() -> {
                balanceButton.setLoading(false);
                fragment.presentFragment(passwordFragment);;
            });
        });
        balanceLayout.addView(balanceButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18, 13, 18, 0));


        starsBalanceLayout = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        heightMeasureSpec
                );
            }
        };
        starsBalanceLayout.setOrientation(LinearLayout.VERTICAL);
        starsBalanceLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        starsBalanceLayout.setPadding(0, 0, 0, dp(17));

        starsBalanceTitle = new AnimatedTextView(context, false, true, true);
        starsBalanceTitle.setTypeface(AndroidUtilities.bold());
        starsBalanceTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        starsBalanceTitle.setTextSize(dp(32));
        starsBalanceTitle.setGravity(Gravity.CENTER);
        starsBalanceTitleSizeSpan = new RelativeSizeSpan(65f / 96f);
        starsBalanceLayout.addView(starsBalanceTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 15, 22, 0));

        starsBalanceSubtitle = new AnimatedTextView(context, true, true, true);
        starsBalanceSubtitle.setGravity(Gravity.CENTER);
        starsBalanceSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        starsBalanceSubtitle.setTextSize(dp(14));
        starsBalanceLayout.addView(starsBalanceSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 17, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 22, 4, 22, 0));

        starsBalanceEditTextContainer = new OutlineTextContainerView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (starsBalanceEditText != null && !starsBalanceEditText.isFocusable()) {
                    starsBalanceEditText.setFocusable(true);
                    starsBalanceEditText.setFocusableInTouchMode(true);
                    int position = listView.findPositionByItemId(STARS_BALANCE);
                    if (position >= 0 && position < listView.adapter.getItemCount()) {
                        listView.stopScroll();
                        listView.smoothScrollToPosition(position);
                    }
                    starsBalanceEditText.requestFocus();
                }
                return super.dispatchTouchEvent(event);
            }
        };
        starsBalanceEditTextContainer.setVisibility(GONE);
        starsBalanceEditTextContainer.setText(getString(R.string.BotStarsWithdrawPlaceholder));
        starsBalanceEditTextContainer.setLeftPadding(dp(14 + 22));
        starsBalanceEditText = new EditTextBoldCursor(context) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                AndroidUtilities.hideKeyboard(this);
            }
        };
        starsBalanceEditText.setFocusable(false);
        starsBalanceEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        starsBalanceEditText.setCursorSize(AndroidUtilities.dp(20));
        starsBalanceEditText.setCursorWidth(1.5f);
        starsBalanceEditText.setBackground(null);
        starsBalanceEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        starsBalanceEditText.setMaxLines(1);
        int padding = AndroidUtilities.dp(16);
        starsBalanceEditText.setPadding(dp(6), padding, padding, padding);
        starsBalanceEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        starsBalanceEditText.setTypeface(Typeface.DEFAULT);
        starsBalanceEditText.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        starsBalanceEditText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        starsBalanceEditText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        starsBalanceEditText.setOnFocusChangeListener((v, hasFocus) -> starsBalanceEditTextContainer.animateSelection(hasFocus ? 1f : 0f));
        starsBalanceEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (starsBalanceEditTextIgnore) return;
                starsBalanceEditTextValue = TextUtils.isEmpty(s) ? 0 : Long.parseLong(s.toString());
                if (starsBalanceEditTextValue > starsBalance.amount) {
                    starsBalanceEditTextValue = starsBalance.amount;
                    starsBalanceEditTextIgnore = true;
                    starsBalanceEditText.setText(Long.toString(starsBalanceEditTextValue));
                    starsBalanceEditText.setSelection(starsBalanceEditText.getText().length());
                    starsBalanceEditTextIgnore = false;
                }
                starsBalanceEditTextAll = starsBalanceEditTextValue == starsBalance.amount;
                AndroidUtilities.cancelRunOnUIThread(setStarsBalanceButtonText);
                setStarsBalanceButtonText.run();
                starsBalanceEditTextAll = false;
            }
        });
        LinearLayout balanceEditTextLayout = new LinearLayout(context);
        balanceEditTextLayout.setOrientation(LinearLayout.HORIZONTAL);
        ImageView starImage = new ImageView(context);
        starImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        starImage.setImageResource(R.drawable.star_small_inner);
        balanceEditTextLayout.addView(starImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        balanceEditTextLayout.addView(starsBalanceEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
        starsBalanceEditTextContainer.attachEditText(starsBalanceEditText);
        starsBalanceEditTextContainer.addView(balanceEditTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        starsBalanceLayout.addView(starsBalanceEditTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 18, 14, 18, 2));

        starsBalanceButtonsLayout = new LinearLayout(context);
        starsBalanceButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);

        starsBalanceButton = new ButtonWithCounterView(context, resourcesProvider) {
            @Override
            protected boolean subTextSplitToWords() {
                return false;
            }
        };
        starsBalanceButton.setEnabled(false);
        starsBalanceButton.setText(formatPluralString("MonetizationStarsWithdraw", 0), false);
        starsBalanceButton.setVisibility(View.VISIBLE);
        starsBalanceButton.setOnClickListener(v -> {
            if (!v.isEnabled() || starsBalanceButton.isLoading() || balanceButton.isLoading()) {
                return;
            }

            final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (starsBalanceBlockedUntil > now) {
                withdrawalBulletin = BulletinFactory.of(fragment).createSimpleBulletin(R.raw.timer_3, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotStarsWithdrawalToast, BotStarsActivity.untilString(starsBalanceBlockedUntil - now)))).show();
                return;
            }

            if (starsBalanceEditTextValue < MessagesController.getInstance(currentAccount).starsRevenueWithdrawalMin) {
                Drawable starDrawable = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
                BulletinFactory.of(fragment).createSimpleBulletin(starDrawable, AndroidUtilities.replaceSingleTag(LocaleController.formatPluralString("BotStarsWithdrawMinLimit", (int) MessagesController.getInstance(currentAccount).starsRevenueWithdrawalMin), () -> {
                    Bulletin.hideVisible();
                    if (starsBalance.amount < MessagesController.getInstance(currentAccount).starsRevenueWithdrawalMin) {
                        starsBalanceEditTextAll = true;
                        starsBalanceEditTextValue = starsBalance.amount;
                    } else {
                        starsBalanceEditTextAll = false;
                        starsBalanceEditTextValue = MessagesController.getInstance(currentAccount).starsRevenueWithdrawalMin;
                    }
                    starsBalanceEditTextIgnore = true;
                    starsBalanceEditText.setText(Long.toString(starsBalanceEditTextValue));
                    starsBalanceEditText.setSelection(starsBalanceEditText.getText().length());
                    starsBalanceEditTextIgnore = false;

                    AndroidUtilities.cancelRunOnUIThread(setStarsBalanceButtonText);
                    setStarsBalanceButtonText.run();
                })).show();
                return;
            }

            TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
            passwordFragment.setDelegate(1, password -> initWithdraw(true, password, passwordFragment));
            starsBalanceButton.setLoading(true);
            passwordFragment.preload(() -> {
                starsBalanceButton.setLoading(false);
                fragment.presentFragment(passwordFragment);;
            });
        });

        starsAdsButton = new ButtonWithCounterView(context, resourcesProvider);
        starsAdsButton.setEnabled(false);
        starsAdsButton.setText(getString(R.string.MonetizationStarsAds), false);
        starsAdsButton.setOnClickListener(v -> {
            if (!v.isEnabled() || starsAdsButton.isLoading()) return;

            starsAdsButton.setLoading(true);
            TLRPC.TL_payments_getStarsRevenueAdsAccountUrl req = new TLRPC.TL_payments_getStarsRevenueAdsAccountUrl();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_payments_starsRevenueAdsAccountUrl) {
                    Browser.openUrl(context, ((TLRPC.TL_payments_starsRevenueAdsAccountUrl) res).url);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    starsAdsButton.setLoading(false);
                }, 1000);
            }));
        });

        starsBalanceButtonsLayout.addView(starsBalanceButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL));
        if (ChatObject.isChannelAndNotMegaGroup(chat)) {
            starsBalanceButtonsLayout.addView(new Space(context), LayoutHelper.createLinear(8, 48, 0, Gravity.FILL));
            starsBalanceButtonsLayout.addView(starsAdsButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1, Gravity.FILL));
        }
        starsBalanceLayout.addView(starsBalanceButtonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL, 18, 13, 18, 0));

        starsBalanceEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                TwoStepVerificationActivity passwordFragment = new TwoStepVerificationActivity();
                passwordFragment.setDelegate(1, password -> initWithdraw(true, password, passwordFragment));
                starsBalanceButton.setLoading(true);
                passwordFragment.preload(() -> {
                    starsBalanceButton.setLoading(false);
                    fragment.presentFragment(passwordFragment);;
                });
                return true;
            }
            return false;
        });
        setStarsBalanceButtonText = () -> {
            final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            starsBalanceButton.setEnabled(starsBalanceEditTextValue > 0 || starsBalanceBlockedUntil > now);
            if (now < starsBalanceBlockedUntil) {
                starsBalanceButton.setText(getString(R.string.MonetizationStarsWithdrawUntil), true);

                if (lock == null) {
                    lock = new SpannableStringBuilder("l");
                    ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.mini_switch_lock);
                    coloredImageSpan.setTopOffset(1);
                    lock.setSpan(coloredImageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                SpannableStringBuilder buttonLockedText = new SpannableStringBuilder();
                buttonLockedText.append(lock).append(BotStarsActivity.untilString(starsBalanceBlockedUntil - now));
                starsBalanceButton.setSubText(buttonLockedText, true);

                if (withdrawalBulletin != null && withdrawalBulletin.getLayout() instanceof Bulletin.LottieLayout && withdrawalBulletin.getLayout().isAttachedToWindow()) {
                    ((Bulletin.LottieLayout) withdrawalBulletin.getLayout()).textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotStarsWithdrawalToast, BotStarsActivity.untilString(starsBalanceBlockedUntil - now))));
                }

                AndroidUtilities.cancelRunOnUIThread(this.setStarsBalanceButtonText);
                AndroidUtilities.runOnUIThread(this.setStarsBalanceButtonText, 1000);
            } else {
                starsBalanceButton.setSubText(null, true);
                starsBalanceButton.setText(StarsIntroActivity.replaceStars(starsBalanceEditTextAll ? getString(R.string.MonetizationStarsWithdrawAll) : LocaleController.formatPluralStringSpaced("MonetizationStarsWithdraw", (int) starsBalanceEditTextValue), starRef), true);
            }
        };

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
        loadingTitle.setTypeface(AndroidUtilities.bold());
        loadingTitle.setTextColor(Theme.getColor(Theme.key_player_actionBarTitle));
        loadingTitle.setTag(Theme.key_player_actionBarTitle);
        loadingTitle.setText(getString("LoadingStats", R.string.LoadingStats));
        loadingTitle.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView loadingSubtitle = new TextView(context);
        loadingSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        loadingSubtitle.setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
        loadingSubtitle.setTag(Theme.key_player_actionBarSubtitle);
        loadingSubtitle.setText(getString(R.string.LoadingStatsDescription));
        loadingSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);

        progressLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        progressLayout.addView(loadingTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));
        progressLayout.addView(loadingSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        addView(progress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private void initWithdraw(boolean stars, TLRPC.InputCheckPasswordSRP password, TwoStepVerificationActivity passwordFragment) {
        if (fragment == null) return;
        Activity parentActivity = fragment.getParentActivity();
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (parentActivity == null || currentUser == null) return;

        TLObject r;
        if (stars) {
            TLRPC.TL_payments_getStarsRevenueWithdrawalUrl req = new TLRPC.TL_payments_getStarsRevenueWithdrawalUrl();
            req.ton = false;
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.password = password != null ? password : new TLRPC.TL_inputCheckPasswordEmpty();
            req.flags |= 2;
            req.amount = starsBalanceEditTextValue;
            r = req;
        } else {
            TLRPC.TL_payments_getStarsRevenueWithdrawalUrl req = new TLRPC.TL_payments_getStarsRevenueWithdrawalUrl();
            req.ton = true;
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
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
                        builder.setPositiveButton(LocaleController.getString(R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> fragment.presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
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
                        fragment.showDialog(builder.create());
                    }
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TL_account.getPassword getPasswordReq = new TL_account.getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TL_account.Password currentPassword = (TL_account.Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initWithdraw(stars, passwordFragment.getNewSrpPassword(), passwordFragment);
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
                if (response instanceof TLRPC.TL_payments_starsRevenueWithdrawalUrl) {
                    Browser.openUrl(getContext(), ((TLRPC.TL_payments_starsRevenueWithdrawalUrl) response).url);
                    if (stars) {
                        loadStarsStats(true);
                    }
                }
                reloadTransactions();
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
        balanceSubtitle.setText("≈" + BillingController.getInstance().formatCurrency(amount, "USD"));
    }

    private void setStarsBalance(TL_stars.StarsAmount amount, int blockedUntil) {
        if (balanceTitle == null || balanceSubtitle == null)
            return;
//        long amount = (long) (stars_rate * crypto_amount * 100.0);
        SpannableStringBuilder ssb = new SpannableStringBuilder(StarsIntroActivity.replaceStarsWithPlain(TextUtils.concat("XTR ", StarsIntroActivity.formatStarsAmount(amount, 0.8f, ' ')), 1f));
        int index = TextUtils.indexOf(ssb, ".");
        if (index >= 0) {
            ssb.setSpan(balanceTitleSizeSpan, index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        starsBalance = amount;
        starsBalanceTitle.setText(ssb);
        starsBalanceSubtitle.setText("≈" + BillingController.getInstance().formatCurrency((long) (stars_rate * amount.amount * 100.0), "USD"));
        starsBalanceEditTextContainer.setVisibility(amount.amount > 0 ? VISIBLE : GONE);
        if (starsBalanceEditTextAll) {
            starsBalanceEditTextIgnore = true;
            starsBalanceEditText.setText(Long.toString(starsBalanceEditTextValue = amount.amount));
            starsBalanceEditText.setSelection(starsBalanceEditText.getText().length());
            starsBalanceEditTextIgnore = false;

            starsBalanceButton.setEnabled(starsBalanceEditTextValue > 0);
        }
        if (starsAdsButton != null) {
            starsAdsButton.setEnabled(amount.amount > 0);
        }
        starsBalanceBlockedUntil = blockedUntil;

        AndroidUtilities.cancelRunOnUIThread(setStarsBalanceButtonText);
        setStarsBalanceButtonText.run();
    }


    private SpannableStringBuilder lock;
    private Runnable setStarsBalanceButtonText;

    private double ton_rate;
    private double stars_rate;

    private void loadStarsStats(boolean force) {
        if (!starsRevenueAvailable) return;

        TLRPC.TL_payments_starsRevenueStats cachedStats = BotStarsController.getInstance(currentAccount).getStarsRevenueStats(dialogId, force);
        if (cachedStats != null) {
            AndroidUtilities.runOnUIThread(() -> {
                applyStarsStats(cachedStats);
            });
        } else {
            TLRPC.TL_payments_getStarsRevenueStats req2 = new TLRPC.TL_payments_getStarsRevenueStats();
            req2.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req2.dark = Theme.isCurrentThemeDark();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res2 instanceof TLRPC.TL_payments_starsRevenueStats) {
                    TLRPC.TL_payments_starsRevenueStats stats = (TLRPC.TL_payments_starsRevenueStats) res2;
                    applyStarsStats(stats);
                }
            }));
        }
    }

    private void applyStarsStats(TLRPC.TL_payments_starsRevenueStats stats) {
        final boolean first = starsRevenueChart == null;
        stars_rate = stats.usd_rate;
        starsRevenueChart = StatisticActivity.createViewData(stats.revenue_graph, getString(R.string.MonetizationGraphStarsRevenue), 2);
        if (starsRevenueChart != null && starsRevenueChart.chartData != null && starsRevenueChart.chartData.lines != null && !starsRevenueChart.chartData.lines.isEmpty() && starsRevenueChart.chartData.lines.get(0) != null) {
            starsRevenueChart.chartData.lines.get(0).colorKey = Theme.key_statisticChartLine_golden;
            starsRevenueChart.chartData.yRate = (float) (1.0 / stars_rate / 100.0);
        }
        setupBalances(false, stats.status);

        if (!tonRevenueAvailable && progress != null) {
            progress.animate().alpha(0).setDuration(380).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                progress.setVisibility(View.GONE);
            }).start();
        }

        if (listView != null) {
            listView.adapter.update(!first);
            if (first) {
                listView.scrollToPosition(0);
            }
        }
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

        loadStarsStats(false);

        if (tonRevenueAvailable) {
            TLRPC.TL_payments_getStarsRevenueStats req = new TLRPC.TL_payments_getStarsRevenueStats();
            req.dark = Theme.isCurrentThemeDark();
            req.ton = true;
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
//            int stats_dc = -1;
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            if (chatFull != null) {
//                stats_dc = chatFull.stats_dc;
                initialSwitchOffValue = switchOffValue = chatFull.restricted_sponsored;
            }
//            if (stats_dc == -1) return;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_payments_starsRevenueStats) {
                    TLRPC.TL_payments_starsRevenueStats stats = (TLRPC.TL_payments_starsRevenueStats) res;

                    impressionsChart = StatisticActivity.createViewData(stats.top_hours_graph, getString(R.string.MonetizationGraphImpressions), 0);
                    if (stats.revenue_graph != null) {
                        stats.revenue_graph.rate = (float) (1_000_000_000.0 / 100.0 / stats.usd_rate);
                    }
                    revenueChart = StatisticActivity.createViewData(stats.revenue_graph, getString(R.string.MonetizationGraphRevenue), 2);
                    if (impressionsChart != null) {
                        impressionsChart.useHourFormat = true;
                    }

                    ton_rate = stats.usd_rate;
                    setupBalances(true, stats.status);

                    progress.animate().alpha(0).setDuration(380).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                        progress.setVisibility(View.GONE);
                    }).start();

                    checkLearnSheet();
                }
            }), null, null, 0, DEFAULT_DATACENTER_ID, ConnectionsManager.ConnectionTypeGeneric, true);
        }
    }

    public void setupBalances(boolean ton, TLRPC.TL_starsRevenueStatus balances) {
        if (ton) {
            availableValue.contains1 = true;
            availableValue.crypto_amount = balances.available_balance.amount;
            availableValue.amount = (long) (availableValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
            setBalance(availableValue.crypto_amount, availableValue.amount);
            availableValue.currency = "USD";
            lastWithdrawalValue.contains1 = true;
            lastWithdrawalValue.crypto_amount = balances.current_balance.amount;
            lastWithdrawalValue.amount = (long) (lastWithdrawalValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
            lastWithdrawalValue.currency = "USD";
            lifetimeValue.contains1 = true;
            lifetimeValue.crypto_amount = balances.overall_revenue.amount;
            lifetimeValue.amount = (long) (lifetimeValue.crypto_amount / 1_000_000_000.0 * ton_rate * 100.0);
            lifetimeValue.currency = "USD";
            proceedsAvailable = true;
            balanceButton.setVisibility(balances.available_balance.amount > 0 && balances.withdrawal_enabled ? View.VISIBLE : View.GONE);
        } else {
            if (stars_rate == 0) {
                return;
            }
            availableValue.contains2 = true;
            availableValue.crypto_amount2 = balances.available_balance;
            availableValue.amount2 = (long) (availableValue.crypto_amount2.amount * stars_rate * 100.0);
            setStarsBalance(availableValue.crypto_amount2, balances.next_withdrawal_at);
            availableValue.currency = "USD";
            lastWithdrawalValue.contains2 = true;
            lastWithdrawalValue.crypto_amount2 = balances.current_balance;
            lastWithdrawalValue.amount2 = (long) (lastWithdrawalValue.crypto_amount2.amount * stars_rate * 100.0);
            lastWithdrawalValue.currency = "USD";
            lifetimeValue.contains2 = true;
            lifetimeValue.crypto_amount2 = balances.overall_revenue;
            lifetimeValue.amount2 = (long) (lifetimeValue.crypto_amount2.amount * stars_rate * 100.0);
            lifetimeValue.currency = "USD";
            proceedsAvailable = true;
            if (starsBalanceButtonsLayout != null) {
                starsBalanceButtonsLayout.setVisibility(balances.withdrawal_enabled ? View.VISIBLE : View.GONE);
            }
            if (starsBalanceButton != null) {
                starsBalanceButton.setVisibility(balances.available_balance.amount > 0 || BuildVars.DEBUG_PRIVATE_VERSION ? View.VISIBLE : View.GONE);
            }
        }

        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    public void reloadTransactions() {
        transactionsLayout.reloadTransactions();
    }

    @Override
    protected void onAttachedToWindow() {
        instance = this;
        super.onAttachedToWindow();
        checkLearnSheet();
    }

    @Override
    protected void onDetachedFromWindow() {
        instance = null;
        super.onDetachedFromWindow();
        if (actionBar != null) {
            actionBar.setCastShadows(true);
        }
    }

    private ActionBar actionBar;
    public void setActionBar(ActionBar actionBar) {
        this.actionBar = actionBar;
    }

    private void checkLearnSheet() {
        if (isAttachedToWindow() && tonRevenueAvailable && proceedsAvailable && MessagesController.getGlobalMainSettings().getBoolean("monetizationadshint", true)) {
            fragment.showDialog(makeLearnSheet(getContext(), false, resourcesProvider));
            MessagesController.getGlobalMainSettings().edit().putBoolean("monetizationadshint", false).apply();
        }
    }

    private boolean switchOffValue = false;
    private boolean initialSwitchOffValue = false;

    private StatisticActivity.ChartViewData impressionsChart;
    private StatisticActivity.ChartViewData revenueChart;
    private StatisticActivity.ChartViewData starsRevenueChart;
    private boolean proceedsAvailable = false;
    private final ProceedOverview availableValue =      ProceedOverview.as("TON", "XTR", getString(R.string.MonetizationOverviewAvailable));
    private final ProceedOverview lastWithdrawalValue = ProceedOverview.as("TON", "XTR", getString(R.string.MonetizationOverviewLastWithdrawal));
    private final ProceedOverview lifetimeValue =       ProceedOverview.as("TON", "XTR", getString(R.string.MonetizationOverviewTotal));

    private final static int CHECK_SWITCHOFF = 1;
    private final static int BUTTON_LOAD_MORE_TRANSACTIONS = 2;
    private final static int STARS_BALANCE = 3;
    private final static int BUTTON_AFFILIATE =4;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int stats_dc = -1;
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
        if (chatFull != null) {
            stats_dc = chatFull.stats_dc;
        }
        if (tonRevenueAvailable) {
            items.add(UItem.asCenterShadow(titleInfo));
            if (impressionsChart != null && !impressionsChart.isEmpty) {
                items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_BAR_LINEAR, stats_dc, impressionsChart));
                items.add(UItem.asShadow(-1, null));
            }
            if (revenueChart != null && !revenueChart.isEmpty) {
                items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_STACKBAR, stats_dc, revenueChart));
                items.add(UItem.asShadow(-2, null));
            }
        }
        if (starsRevenueAvailable && starsRevenueChart != null && !starsRevenueChart.isEmpty) {
            items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_STACKBAR, stats_dc, starsRevenueChart));
            items.add(UItem.asShadow(-3, null));
        }
        if (proceedsAvailable) {
            items.add(UItem.asBlackHeader(getString(R.string.MonetizationOverview)));
            items.add(UItem.asProceedOverview(availableValue));
            items.add(UItem.asProceedOverview(lastWithdrawalValue));
            items.add(UItem.asProceedOverview(lifetimeValue));
            items.add(UItem.asShadow(-4, proceedsInfo));
        }
        if (chat != null && chat.creator) {
            if (tonRevenueAvailable) {
                items.add(UItem.asBlackHeader(getString(R.string.MonetizationBalance)));
                items.add(UItem.asCustom(balanceLayout));
                items.add(UItem.asShadow(-5, balanceInfo));

                final int switchOffLevel = MessagesController.getInstance(currentAccount).channelRestrictSponsoredLevelMin;
                items.add(UItem.asCheck(CHECK_SWITCHOFF, PeerColorActivity.withLevelLock(getString(R.string.MonetizationSwitchOff), currentBoostLevel < switchOffLevel ? switchOffLevel : 0)).setChecked(currentBoostLevel >= switchOffLevel && switchOffValue));
                items.add(UItem.asShadow(-8, getString(R.string.MonetizationSwitchOffInfo)));
            }

            if (starsRevenueAvailable) {
                items.add(UItem.asBlackHeader(getString(R.string.MonetizationStarsBalance)));
                items.add(UItem.asCustom(STARS_BALANCE, starsBalanceLayout));
                items.add(UItem.asShadow(-6, starsBalanceInfo));
            }
        }
        if (ChatObject.isChannelAndNotMegaGroup(MessagesController.getInstance(currentAccount).getChat(-dialogId)) && MessagesController.getInstance(currentAccount).starrefConnectAllowed) {
            items.add(AffiliateProgramFragment.ColorfulTextCell.Factory.as(BUTTON_AFFILIATE, Theme.getColor(Theme.key_color_green, resourcesProvider), R.drawable.filled_earn_stars, applyNewSpan(getString(R.string.ChannelAffiliateProgramRowTitle)), getString(R.string.ChannelAffiliateProgramRowText)));
            items.add(UItem.asShadow(-7, null));
        }
        if (transactionsLayout.hasTransactions()) {
            items.add(UItem.asFullscreenCustom(transactionsLayout, 0));
        } else {
            items.add(UItem.asShadow(-10, null));
        }
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
        } else if (item.id == BUTTON_AFFILIATE) {
            fragment.presentFragment(new ChannelAffiliateProgramsFragment(dialogId));
        }
    }

    private final Runnable sendCpmUpdateRunnable = this::sendCpmUpdate;
    private void sendCpmUpdate() {
        AndroidUtilities.cancelRunOnUIThread(sendCpmUpdateRunnable);

        if (switchOffValue == initialSwitchOffValue)
            return;

        TLRPC.TL_channels_restrictSponsoredMessages req = new TLRPC.TL_channels_restrictSponsoredMessages();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-dialogId);
        req.restricted = switchOffValue;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (err != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    BulletinFactory.showError(err);
                });
            } else if (res instanceof TLRPC.Updates) {
                AndroidUtilities.runOnUIThread(() -> {
                    initialSwitchOffValue = switchOffValue;
                });
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
            }
        });
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
        final int key = textPaint.getFontMetricsInt().bottom * (large ? 1 : -1) * (int) (100 * scale) - (int) (100 * translateY);
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
        private final LinearLayout[] amountContainer = new LinearLayout[2];
        private final AnimatedEmojiSpan.TextViewEmojis[] cryptoAmountView = new AnimatedEmojiSpan.TextViewEmojis[2];
        private final TextView amountView[] = new TextView[2];
        private final TextView titleView;

        private final DecimalFormat formatter;

        public ProceedOverviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setOrientation(VERTICAL);

            layout = new LinearLayout(context);
            layout.setOrientation(VERTICAL);
            addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 9, 22, 0));

            for (int i = 0; i < 2; ++i) {
                amountContainer[i] = new LinearLayout(context);
                amountContainer[i].setOrientation(HORIZONTAL);
                layout.addView(amountContainer[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));

                cryptoAmountView[i] = new AnimatedEmojiSpan.TextViewEmojis(context);
                cryptoAmountView[i].setTypeface(AndroidUtilities.bold());
                cryptoAmountView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                cryptoAmountView[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                amountContainer[i].addView(cryptoAmountView[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 5, 0));

                amountView[i] = new AnimatedEmojiSpan.TextViewEmojis(context);
                amountView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                amountView[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                amountContainer[i].addView(amountView[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
            }

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

            for (int i = 0; i < 2; ++i) {
                final String crypto_currency = i == 0 ? value.crypto_currency : value.crypto_currency2;
//                final long crypto_amount = i == 0 ? value.crypto_amount : value.crypto_amount2;
//                CharSequence cryptoAmount;
//                if (i == 0) {
//                    cryptoAmount
//                }
                final long amount = i == 0 ? value.amount : value.amount2;

                if (i == 0 && !value.contains1) {
                    amountContainer[i].setVisibility(View.GONE);
                    continue;
                }
                if (i == 1 && !value.contains2) {
                    amountContainer[i].setVisibility(View.GONE);
                    continue;
                }

                SpannableStringBuilder s = new SpannableStringBuilder(crypto_currency + " ");
                CharSequence finalS;
                if ("TON".equalsIgnoreCase(crypto_currency)) {
                    String formatted = formatter.format(value.crypto_amount / 1_000_000_000.0);
                    int index = formatted.indexOf('.');
                    if (index >= 0) {
                        s.append(LocaleController.formatNumber((long) Math.floor(value.crypto_amount / 1_000_000_000.0), ' '));
                        s.append(formatted.substring(index));
                    } else {
                        s.append(formatted);
                    }
                    finalS = replaceTON(s, cryptoAmountView[i].getPaint(), 1.05f, true);
                } else if ("XTR".equalsIgnoreCase(crypto_currency)) {
                    if (i == 0) {
                        s.append(LocaleController.formatNumber(value.crypto_amount, ' '));
                    } else {
                        s.append(StarsIntroActivity.formatStarsAmount(value.crypto_amount2, .8f, ' '));
                    }
                    finalS = StarsIntroActivity.replaceStarsWithPlain(s, .7f);
                } else {
                    s.append(Long.toString(value.crypto_amount));
                    finalS = s;
                }
                SpannableStringBuilder cryptoAmount = new SpannableStringBuilder(finalS);
                if ("TON".equalsIgnoreCase(crypto_currency)) {
                    int index = TextUtils.indexOf(cryptoAmount, ".");
                    if (index >= 0) {
                        cryptoAmount.setSpan(new RelativeSizeSpan(13f / 16f), index, cryptoAmount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                amountContainer[i].setVisibility(View.VISIBLE);
                cryptoAmountView[i].setText(cryptoAmount);
                amountView[i].setText("≈" + BillingController.getInstance().formatCurrency(amount, value.currency));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public static class ProceedOverview {

        public boolean contains1 = true;
        public String crypto_currency;
        public CharSequence text;
        public long crypto_amount;
        public long amount;
        public String currency;

        public boolean contains2;
        public String crypto_currency2;
        public TL_stars.StarsAmount crypto_amount2 = TL_stars.StarsAmount.ofStars(0);
        public long amount2;

        public static ProceedOverview as(String cryptoCurrency, CharSequence text) {
            ProceedOverview o = new ProceedOverview();
            o.crypto_currency = cryptoCurrency;
            o.text = text;
            return o;
        }

        public static ProceedOverview as(String cryptoCurrency, String cryptoCurrency2, CharSequence text) {
            ProceedOverview o = new ProceedOverview();
            o.contains1 = false;
            o.crypto_currency = cryptoCurrency;
            o.crypto_currency2 = cryptoCurrency2;
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
            valueText.setTypeface(AndroidUtilities.bold());
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
            value.append("TON ");
            value.append(formatter.format((Math.abs(amount) / 1_000_000_000.0)));
            int index = TextUtils.indexOf(value, ".");
            if (index >= 0) {
                value.setSpan(new RelativeSizeSpan(1.15f), 0, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            valueText.setText(replaceTON(value, valueText.getPaint(), 1.1f, dp(.33f), false));
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

    public static void showTransactionSheet(Context context, int currentAccount, TL_stats.BroadcastRevenueTransaction transaction, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar();

        LinearLayout layout = new LinearLayout(context);
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

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        final DecimalFormat formatter = new DecimalFormat("#.##", symbols);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(12);
        formatter.setGroupingUsed(false);

        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
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

        textView = new TextView(context);
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

        textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
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
            FrameLayout chipLayout = new FrameLayout(context);
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

            BackupImageView chipAvatar = new BackupImageView(context);
            chipAvatar.setRoundRadius(dp(28));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(owner);
            chipAvatar.setForUserOrChat(owner, avatarDrawable);
            chipLayout.addView(chipAvatar, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP));

            TextView chipText = new TextView(context);
            chipText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            chipText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chipText.setSingleLine();
            chipText.setText(ownerName);
            chipLayout.addView(chipText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 37, 0, 10, 0));

            layout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 42, 10, 42, 0));
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        if (transaction instanceof TL_stats.TL_broadcastRevenueTransactionWithdrawal && (((TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction).flags & 2) != 0) {
            TL_stats.TL_broadcastRevenueTransactionWithdrawal t = (TL_stats.TL_broadcastRevenueTransactionWithdrawal) transaction;
            button.setText(getString(R.string.MonetizationTransactionDetailWithdrawButton), false);
            button.setOnClickListener(v -> {
                Browser.openUrl(context, t.transaction_url);
            });
        } else {
            button.setText(getString(R.string.OK), false);
            button.setOnClickListener(v -> {
                sheet.dismiss();
            });
        }
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP, 18, 30, 18, 14));

        sheet.setCustomView(layout);
        sheet.show();
    }

    public static BottomSheet makeLearnSheet(Context context, boolean bots, Theme.ResourcesProvider resourcesProvider) {
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(8), 0, dp(8), 0);

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.large_monetize);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        imageView.setBackground(Theme.createCircleDrawable(dp(80), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        layout.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setText(getString(bots ? R.string.BotMonetizationInfoTitle : R.string.MonetizationInfoTitle));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 25));

        layout.addView(
                new FeatureCell(context, R.drawable.msg_channel, getString(bots ? R.string.BotMonetizationInfoFeature1Name : R.string.MonetizationInfoFeature1Name), getString(bots ? R.string.BotMonetizationInfoFeature1Text : R.string.MonetizationInfoFeature1Text), resourcesProvider),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 8, 0, 8, 16)
        );

        layout.addView(
                new FeatureCell(context, R.drawable.menu_feature_split, getString(bots ? R.string.BotMonetizationInfoFeature2Name : R.string.MonetizationInfoFeature2Name), getString(bots ? R.string.BotMonetizationInfoFeature2Text : R.string.MonetizationInfoFeature2Text), resourcesProvider),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 8, 0, 8, 16)
        );

        layout.addView(
                new FeatureCell(context, R.drawable.menu_feature_withdrawals, getString(bots ? R.string.BotMonetizationInfoFeature3Name : R.string.MonetizationInfoFeature3Name), getString(bots ? R.string.BotMonetizationInfoFeature3Text : R.string.MonetizationInfoFeature3Text), resourcesProvider),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 8, 0, 8, 16)
        );

        View separator = new View(context);
        separator.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        layout.addView(separator, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL, 12, 0, 12, 0));

        textView = new AnimatedEmojiSpan.TextViewEmojis(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        SpannableString animatedDiamond = new SpannableString("💎");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.ton);
        span.setScale(.9f, .9f);
        span.setColorKey(Theme.key_windowBackgroundWhiteBlueText2);
        span.setRelativeSize(textView.getPaint().getFontMetricsInt());
        span.spaceScaleX = .9f;
        animatedDiamond.setSpan(span, 0, animatedDiamond.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(AndroidUtilities.replaceCharSequence("💎", getString(bots ? R.string.BotMonetizationInfoTONTitle : R.string.MonetizationInfoTONTitle), animatedDiamond));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 20, 8, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setText(AndroidUtilities.withLearnMore(AndroidUtilities.replaceTags(getString(bots ? R.string.BotMonetizationInfoTONText : R.string.MonetizationInfoTONText)), () -> Browser.openUrl(context, getString(bots ? R.string.BotMonetizationInfoTONLink : R.string.MonetizationInfoTONLink))));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 9, 28, 0));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.GotIt), false);
        button.setOnClickListener(v -> {
            sheet.dismiss();
        });
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP, 10, 25, 10, 14));

        sheet.setCustomView(layout);

        return sheet;
    }

    public static class FeatureCell extends FrameLayout {
        public FeatureCell(Context context, int icon, CharSequence header, CharSequence text, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            imageView.setImageResource(icon);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.LEFT, 0, 5, 18, 0));

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 42, 0, 0, 0));

            LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setText(header);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

            textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setText(text);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(325)), MeasureSpec.getMode(widthMeasureSpec)), heightMeasureSpec);
        }
    }

    public class ChannelTransactionsView extends LinearLayout {

        private final int currentAccount;
        private final ViewPagerFixed viewPager;
        private final PageAdapter adapter;
        private final ViewPagerFixed.TabsView tabsView;
        private final long dialogId;
        private final Runnable updateParentList;

        public static final int STARS_TRANSACTIONS = 0;
        public static final int TON_TRANSACTIONS = 1;

        private String tonTransactionsLastOffset = "";
        private final ArrayList<TL_stars.StarsTransaction> tonTransactions = new ArrayList<>();

        private final ArrayList<TL_stars.StarsTransaction> starsTransactions = new ArrayList<>();
        private String starsLastOffset = "";

        private class PageAdapter extends ViewPagerFixed.Adapter {

            private final Context context;
            private final int currentAccount;
            private final int classGuid;
            private final Theme.ResourcesProvider resourcesProvider;
            private final long dialogId;

            public PageAdapter(Context context, int currentAccount, long dialogId, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                this.context = context;
                this.currentAccount = currentAccount;
                this.classGuid = classGuid;
                this.resourcesProvider = resourcesProvider;
                this.dialogId = dialogId;
                fill();
            }

            private final ArrayList<UItem> items = new ArrayList<>();

            public void fill() {
                items.clear();
                if (!tonTransactions.isEmpty())
                    items.add(UItem.asSpace(TON_TRANSACTIONS));
                if (!starsTransactions.isEmpty())
                    items.add(UItem.asSpace(STARS_TRANSACTIONS));
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public View createView(int viewType) {
                return new Page(context, dialogId, viewType, currentAccount, classGuid, () -> loadTransactions(viewType), resourcesProvider);
            }

            @Override
            public void bindView(View view, int position, int viewType) {}

            @Override
            public int getItemViewType(int position) {
                if (position < 0 || position >= items.size())
                    return TON_TRANSACTIONS;
                return items.get(position).intValue;
            }

            @Override
            public String getItemTitle(int position) {
                final int viewType = getItemViewType(position);
                switch (viewType) {
                    case STARS_TRANSACTIONS: return getString(R.string.MonetizationTransactionsStars);
                    case TON_TRANSACTIONS: return getString(R.string.MonetizationTransactionsTON);
                    default: return "";
                }
            }
        }

        public RecyclerListView getCurrentListView() {
            View currentView = viewPager.getCurrentView();
            if (!(currentView instanceof Page)) return null;
            return ((Page) currentView).listView;
        }

        public ChannelTransactionsView(Context context, int currentAccount, long dialogId, int classGuid, Runnable updateList, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.updateParentList = updateList;

            setOrientation(VERTICAL);

            viewPager = new ViewPagerFixed(context);
            viewPager.setAdapter(adapter = new PageAdapter(context, currentAccount, dialogId, classGuid, resourcesProvider));
            tabsView = viewPager.createTabsView(true, 3);

            View separatorView = new View(context);
            separatorView.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));

            addView(tabsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            addView(separatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density));
            addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

            loadTransactions(TON_TRANSACTIONS);
            loadTransactions(STARS_TRANSACTIONS);
        }

        private void updateTabs() {
            adapter.fill();
            viewPager.fillTabs(false);
            viewPager.updateCurrent();
        }

        public void reloadTransactions() {
            final boolean hadTransactions = hasTransactions();
            for (int i = 0; i < 2; ++i) {
                final int type = i;
                if (loadingTransactions[type]) return;
                if (type == TON_TRANSACTIONS) {
                    tonTransactions.clear();
                    tonTransactionsLastOffset = "";
                } else {
                    starsTransactions.clear();
                    starsLastOffset = "";
                }
                loadingTransactions[type] = false;
                loadTransactions(type);
            }
            if (hasTransactions() != hadTransactions && updateParentList != null) {
                updateTabs();
                updateParentList.run();
            }
        }

        private void updateLists(boolean animated, boolean checkMore) {
            for (int i = 0; i < viewPager.getViewPages().length; ++i) {
                View page = viewPager.getViewPages()[i];
                if (page instanceof Page) {
                    ((Page) page).listView.adapter.update(animated);
                    if (checkMore) {
                        ((Page) page).checkMore();
                    }
                }
            }
        }

        public boolean hasTransactions() {
            return !tonTransactions.isEmpty() || !starsTransactions.isEmpty();
        }
        public boolean hasTransactions(int type) {
            if (type == TON_TRANSACTIONS) return !tonTransactions.isEmpty();
            if (type == STARS_TRANSACTIONS) return !starsTransactions.isEmpty();
            return false;
        }

        private boolean[] loadingTransactions = new boolean[] { false, false };
        private void loadTransactions(int type) {
            if (loadingTransactions[type]) return;

            final boolean hadTransactions = hasTransactions();
            final boolean hadTheseTransactions = hasTransactions(type);
            if (type == TON_TRANSACTIONS) {
                if (tonTransactionsLastOffset == null || !tonRevenueAvailable)
                    return;
                loadingTransactions[type] = true;
                TL_stars.TL_payments_getStarsTransactions req = new TL_stars.TL_payments_getStarsTransactions();
                req.ton = true;
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.offset = tonTransactionsLastOffset;
                req.limit = tonTransactions.isEmpty() ? 5 : 20;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TL_stars.StarsStatus) {
                        TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                        MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                        MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                        tonTransactions.addAll(r.history);
                        tonTransactionsLastOffset = r.next_offset;
                        loadingTransactions[type] = false;
                        updateLists(true, true);
                    } else if (err != null) {
                        BulletinFactory.showError(err);
                    }
                    if (hasTransactions() != hadTransactions && updateParentList != null) {
                        updateParentList.run();
                    }
                    if (hasTransactions(type) != hadTheseTransactions) {
                        updateTabs();
                    }
                }));
            } else if (type == STARS_TRANSACTIONS) {
                if (starsLastOffset == null || !starsRevenueAvailable)
                    return;
                loadingTransactions[type] = true;
                TL_stars.TL_payments_getStarsTransactions req = new TL_stars.TL_payments_getStarsTransactions();
                req.ton = false;
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.offset = starsLastOffset;
                req.limit = starsTransactions.isEmpty() ? 5 : 20;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TL_stars.StarsStatus) {
                        TL_stars.StarsStatus r = (TL_stars.StarsStatus) res;
                        MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                        MessagesController.getInstance(currentAccount).putChats(r.chats, false);
                        starsTransactions.addAll(r.history);
                        starsLastOffset = r.next_offset;
                        loadingTransactions[type] = false;
                        updateLists(true, true);
                    } else if (err != null) {
                        BulletinFactory.showError(err);
                    }
                    if (hasTransactions() != hadTransactions && updateParentList != null) {
                        updateParentList.run();
                    }
                    if (hasTransactions(type) != hadTheseTransactions) {
                        updateTabs();
                    }
                }));
            }
        }

        public class Page extends FrameLayout {

            private final UniversalRecyclerView listView;
            private final Theme.ResourcesProvider resourcesProvider;
            private final int currentAccount;
            private final int type;
            private final long bot_id;
            private final Runnable loadMore;

            public Page(Context context, long bot_id, int type, int currentAccount, int classGuid, Runnable loadMore, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                this.type = type;
                this.currentAccount = currentAccount;
                this.bot_id = bot_id;
                this.resourcesProvider = resourcesProvider;
                this.loadMore = loadMore;

                listView = new UniversalRecyclerView(context, currentAccount, classGuid, true, this::fillItems, this::onClick, null, resourcesProvider);
                addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        if (!Page.this.listView.canScrollVertically(1) || isLoadingVisible()) {
                            loadMore.run();
                        }
                    }
                });
            }

            public void checkMore() {
                if (!Page.this.listView.canScrollVertically(1) || isLoadingVisible()) {
                    loadMore.run();
                }
            }

            public boolean isLoadingVisible() {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    if (listView.getChildAt(i) instanceof FlickerLoadingView)
                        return true;
                }
                return false;
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                listView.adapter.update(false);
            }

            private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
                if (type == STARS_TRANSACTIONS) {
                    for (TL_stars.StarsTransaction t : starsTransactions) {
                        items.add(StarsIntroActivity.StarsTransactionView.Factory.asTransaction(t, true));
                    }
                    if (!TextUtils.isEmpty(starsLastOffset)) {
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    }
                } else if (type == TON_TRANSACTIONS) {
                    for (TL_stars.StarsTransaction t : tonTransactions) {
                        items.add(StarsIntroActivity.StarsTransactionView.Factory.asTransaction(t, true));
                    }
                    if (!TextUtils.isEmpty(tonTransactionsLastOffset)) {
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    }
                }
            }

            private void onClick(UItem item, View view, int position, float x, float y) {
                if (item.object instanceof TL_stars.StarsTransaction) {
                    StarsIntroActivity.showTransactionSheet(getContext(), true, dialogId, currentAccount, (TL_stars.StarsTransaction) item.object, resourcesProvider);
                } else if (item.object instanceof TL_stats.BroadcastRevenueTransaction) {
                    showTransactionSheet(getContext(), currentAccount, (TL_stats.BroadcastRevenueTransaction) item.object, dialogId, resourcesProvider);
                }
            }

        }

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private NestedScrollingParentHelper nestedScrollingParentHelper = new NestedScrollingParentHelper(this);

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
        try {
            if (target == listView && transactionsLayout.isAttachedToWindow()) {
                RecyclerListView innerListView = transactionsLayout.getCurrentListView();
                int bottom = ((View) transactionsLayout.getParent()).getBottom();
                if (actionBar != null) {
                    actionBar.setCastShadows(!isAttachedToWindow() || listView.getHeight() - bottom < 0);
                }
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
            boolean searchVisible = false;
            int t = ((View) transactionsLayout.getParent()).getTop() - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight();
            int bottom = ((View) transactionsLayout.getParent()).getBottom();
            if (dy < 0) {
                boolean scrolledInner = false;
                if (actionBar != null) {
                    actionBar.setCastShadows(!isAttachedToWindow() || listView.getHeight() - bottom < 0);
                }
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

package org.telegram.ui.TON;

import static org.telegram.messenger.AndroidUtilities.dp;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsIntroActivity.formatStarsAmount;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SiblingRecyclerPagerLayout;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.GradientHeaderActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.BotStarsActivity;
import org.telegram.ui.Stars.BotStarsController;
import org.telegram.ui.Stars.ExplainStarsSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.bots.ChannelAffiliateProgramsFragment;
import org.telegram.ui.Wallet.WalletDiamondView;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;

public class TONIntroActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout aboveTitleView;
    private WalletDiamondView iconTextureView;

    private StarsIntroActivity.StarsTransactionsLayout transactionsLayout;
    private SiblingRecyclerPagerLayout scrollCoordinator;
    private View transactionsBoundary;
    private View listTopOverlay;
    private BoolAnimator actionBarShownAnimator;
    private BoolAnimator tabsPinnedAnimator;
    private BoolAnimator headerVisibleAnimator;
    private int transactionsRow = RecyclerView.NO_POSITION;
    private int contentInsetTop;
    private int contentInsetBottom;
    private boolean receivedInsets;
    private boolean savedTransactionsPinned;
    private final Runnable alignTransactionsBoundary = () -> {
        if (scrollCoordinator != null && transactionsRow != RecyclerView.NO_POSITION) {
            scrollCoordinator.alignCommonBoundaryToPin();
        }
    };
    private View emptyLayout;
    private FireworksOverlay fireworksOverlay;

    private final boolean allowTopUp;
    private LinearLayout balanceLayout;
    private LinearLayout starBalanceLayout;
    private SpannableStringBuilder starBalanceIcon;
    private AnimatedTextView starBalanceTextView;
    private AnimatedTextView starBalanceTitleView;

    private FrameLayout oneButtonsLayout;
    private ButtonWithCounterView buyButton;
    private LinearLayout twoButtonsLayout;
    private ButtonWithCounterView topUpButton;
    private ButtonWithCounterView withdrawButton;

    public static boolean allowTopUp() {
        return ApplicationLoader.isStandaloneBuild() || BuildVars.isBetaApp() || BuildVars.isHuaweiStoreApp();
    }

    public TONIntroActivity() {
        allowTopUp = allowTopUp();
        setWhiteBackground(true);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starTransactionsLoaded);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starSubscriptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.botStarsUpdated);
        StarsController.getTonInstance(currentAccount).invalidateTransactions(true);
        StarsController.getTonInstance(currentAccount).invalidateSubscriptions(true);
        StarsController.getTonInstance(currentAccount).getOptions();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (listView != null) {
            listView.removeCallbacks(alignTransactionsBoundary);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starSubscriptionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsUpdated);
    }

    private boolean hadTransactions;
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (
            id == NotificationCenter.starOptionsLoaded
//            || id == NotificationCenter.starTransactionsLoaded
        ) {
            saveScrollPosition();
            if (adapter != null) {
                adapter.update(true);
            }
            applyScrolledPosition();
        } else if (id == NotificationCenter.starTransactionsLoaded) {
            final StarsController c = StarsController.getTonInstance(currentAccount);
            if (hadTransactions != c.hasTransactions()) {
                hadTransactions = c.hasTransactions();
                saveScrollPosition();
                if (adapter != null) {
                    adapter.update(false);
                }
                applyScrolledPosition();
            }
        } else if (id == NotificationCenter.starSubscriptionsLoaded) {
            if (adapter != null) {
                adapter.update(true);
            }
        } else if (id == NotificationCenter.starBalanceUpdated) {
            updateBalance();
        } else if (id == NotificationCenter.botStarsUpdated) {
            if (getUserConfig().getClientUserId() == (long) args[0]) {
                updateBalance();
            }
        }
    }

    @Override
    public int getNavigationBarColor() {
        return Theme.getColor(Theme.key_dialogBackgroundGray);
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        useFillLastLayoutManager = false;
        particlesViewHeight = dp(32 + 190 + 16);
        scrollCoordinator = new SiblingRecyclerPagerLayout(context);
        scrollCoordinator.setCommonInsetsManagedExternally(true);
        if (!receivedInsets) {
            contentInsetTop = parentLayout != null && parentLayout.isLayersLayout() ? 0 : AndroidUtilities.statusBarHeight;
            contentInsetBottom = AndroidUtilities.navigationBarHeight;
        }
        scrollCoordinator.setGeometry(new SiblingRecyclerPagerLayout.Geometry() {
            @Override
            public float tabsPinnedTop(SiblingRecyclerPagerLayout parent) {
                // the glass of the tabs (inset by 9.66dp) 7dp under the header's buttons
                return Math.max(0, BaseFragmentSimpleGlass.getPillGlassTop(contentInsetTop) - dp(9.66f));
            }

            @Override
            public int pageBottomInset(SiblingRecyclerPagerLayout parent) {
                return contentInsetBottom;
            }

            @Override
            public void onTabsPinnedChanged(boolean pinned) {
                if (tabsPinnedAnimator != null) {
                    tabsPinnedAnimator.setValue(pinned, true);
                }
                if (contentView != null) {
                    contentView.invalidate();
                }
            }
        });
        scrollCoordinator.setVerticalBleed(
                -Math.min(-dp(8), contentInsetTop - dp(48)),
                -Math.min(-dp(8), contentInsetBottom - dp(48)));
        transactionsBoundary = scrollCoordinator.createBoundarySpacer(context);
        transactionsLayout = new StarsIntroActivity.StarsTransactionsLayout(context, currentAccount, true, 0,
                getClassGuid(), getResourceProvider(), scrollCoordinator);
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int firstViewHeight;
                if (isLandscapeMode) {
                    firstViewHeight = statusBarHeight + getHeaderView().getMeasuredHeight() - AndroidUtilities.dp(16);
                } else {
                    int h = AndroidUtilities.dp(140) + statusBarHeight;
                    if (backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24) > h) {
                        h = backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24);
                    }
                    firstViewHeight = h;
                }
                firstViewHeight -= 2.5f * yOffset;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(firstViewHeight, MeasureSpec.EXACTLY));
            }
        };

        super.createView(context);
        configureTransactionsGlass();
        if (parentLayout != null && parentLayout.isRightLayout()) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_close);
        }

        aboveTitleView = new FrameLayout(context);
        aboveTitleView.setClickable(true);
        iconTextureView = new WalletDiamondView(context, 170);
        iconTextureView.setStarParticlesView(particlesView);
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 12));
        configureHeader(getString(R.string.GramEarningsTitle), AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.GramEarningsText), () -> {
            new ExplainStarsSheet(context).show();
        }), true), aboveTitleView, null);

        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (adapter == null) return;
            UItem item = adapter.getItem(position);
            if (item == null) return;
            onItemClick(item, position);
        });

        fireworksOverlay = new FireworksOverlay(getContext());
        contentView.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final StarsController s = StarsController.getTonInstance(currentAccount);

        balanceLayout = new LinearLayout(getContext());
        balanceLayout.setOrientation(LinearLayout.VERTICAL);
        balanceLayout.setPadding(0, dp(20), 0, dp(10));

        starBalanceTextView = new AnimatedTextView(getContext(), false, true, false);
        starBalanceTextView.setTypeface(AndroidUtilities.bold());
        starBalanceTextView.setTextSize(dp(32));
        starBalanceTextView.setGravity(Gravity.CENTER);
        ThemeBind.textColor(starBalanceTextView, Theme.key_windowBackgroundWhiteBlackText, resourceProvider);

        starBalanceIcon = new SpannableStringBuilder("S");
        final ColoredImageSpan starBalanceIconSpan = new ColoredImageSpan(R.drawable.mini_gram_72);
        starBalanceIconSpan.recolorDrawable = false;
        starBalanceIconSpan.setScale(0.5f, 0.5f);
        starBalanceIconSpan.translate(-dp(3), 0);
        starBalanceIcon.setSpan(starBalanceIconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        balanceLayout.addView(starBalanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER, 24, 0, 24, 0));

        starBalanceTitleView = new AnimatedTextView(getContext());
        starBalanceTitleView.setTextSize(dp(14));
        starBalanceTitleView.setGravity(Gravity.CENTER);
        starBalanceTitleView.setText(LocaleController.getString(R.string.YourTonBalance));
        ThemeBind.textColor(starBalanceTitleView, Theme.key_windowBackgroundWhiteGrayText2, resourceProvider);
        balanceLayout.addView(starBalanceTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.CENTER, 24, 0, 24, 8));

        FrameLayout buttonsLayout = new FrameLayout(getContext());

        oneButtonsLayout = new FrameLayout(getContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (twoButtons) return false;
                return super.dispatchTouchEvent(ev);
            }
        };
        buttonsLayout.addView(oneButtonsLayout);

        if (allowTopUp) {
            buyButton = new ButtonWithCounterView(getContext(), resourceProvider).setRound();
            buyButton.setRound();
            buyButton.setText(getString(R.string.TopUpViaFragment), false);
            buyButton.setOnClickListener(v -> {
                Browser.openUrlInSystemBrowser(getContext(), getString(R.string.TopUpViaFragmentLink));
            });
            oneButtonsLayout.addView(buyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL));
        }

        twoButtonsLayout = new LinearLayout(getContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (!twoButtons) return false;
                return super.dispatchTouchEvent(ev);
            }
        };
        buttonsLayout.addView(twoButtonsLayout);

        topUpButton = new ButtonWithCounterView(getContext(), resourceProvider).setRound();
        SpannableStringBuilder ssb = new SpannableStringBuilder("x  ");
        ssb.setSpan(new ColoredImageSpan(R.drawable.mini_topup, ColoredImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(getString(R.string.TonTopUp));
        topUpButton.setText(ssb, false);
        topUpButton.setOnClickListener(v -> {
            Browser.openUrlInSystemBrowser(getContext(), getString(R.string.TopUpViaFragmentLink));
        });
        if (allowTopUp) {
            twoButtonsLayout.addView(topUpButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 1, 0, 0, 8, 0));
        }

        withdrawButton = new ButtonWithCounterView(getContext(), resourceProvider).setRound();
        ssb = new SpannableStringBuilder("x  ");
        ssb.setSpan(new ColoredImageSpan(R.drawable.mini_stats, ColoredImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append(getString(R.string.TonStats));
        withdrawButton.setText(ssb, false);
        withdrawButton.setOnClickListener(v -> {
            presentFragment(new BotStarsActivity(BotStarsActivity.TYPE_TON, getUserConfig().getClientUserId()));
        });
        twoButtonsLayout.addView(withdrawButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 1, 0, 0, 0, 0));

        balanceLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 20, 6, 20, 4));

        oneButtonsLayout.animate().cancel();
        twoButtonsLayout.animate().cancel();
        twoButtonsLayout.setAlpha(twoButtons ? 1.0f : 0.0f);
        oneButtonsLayout.setAlpha(twoButtons ? 0.0f : 1.0f);
        twoButtonsLayout.setVisibility(twoButtons ? View.VISIBLE : View.GONE);
        oneButtonsLayout.setVisibility(twoButtons ? View.GONE : View.VISIBLE);

        updateBalance();

        if (adapter != null) {
            adapter.update(false);
        }

        return fragmentView;
    }

    private void updateBalance() {
        final StarsController s = StarsController.getTonInstance(currentAccount);
        final double ton_usd_rate = getMessagesController().config.tonUsdRate.get();

        final TL_stars.StarsAmount balance = s.getBalance();

        final SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(starBalanceIcon);
        sb.append(formatStarsAmount(balance, 0.66f, ' '));
        starBalanceTextView.setText(sb);

        final double dollars = balance.amount / 1_000_000_000.0 * ton_usd_rate;
        if ((int) (dollars * 100) > 0) {
            starBalanceTitleView.setText("≈" + BillingController.getInstance().formatCurrency((int) (dollars * 100), "USD"));
        } else {
            starBalanceTitleView.setText(LocaleController.getString(R.string.YourTonBalance));
        }

        final TLRPC.TL_payments_starsRevenueStats stats = BotStarsController.getInstance(currentAccount).getTONRevenueStats(getUserConfig().getClientUserId(), true);
        updateButtonsLayouts(stats != null && stats.status != null && stats.status.overall_revenue.positive(), true);
    }

    private boolean twoButtons;
    private void updateButtonsLayouts(boolean two, boolean animated) {
        if (twoButtons == two) return;
        twoButtons = two;
        if (animated) {
            oneButtonsLayout.setVisibility(View.VISIBLE);
            twoButtonsLayout.setVisibility(View.VISIBLE);
            oneButtonsLayout.animate()
                .alpha(two ? 0.0f : 1.0f)
                .withEndAction(() -> {
                    if (two) {
                        oneButtonsLayout.setVisibility(View.GONE);
                    }
                })
                .start();
            twoButtonsLayout.animate()
                .alpha(!two ? 0.0f : 1.0f)
                .withEndAction(() -> {
                    if (!two) {
                        twoButtonsLayout.setVisibility(View.GONE);
                    }
                })
                .start();
        } else {
            oneButtonsLayout.animate().cancel();
            twoButtonsLayout.animate().cancel();
            twoButtonsLayout.setAlpha(two ? 1.0f : 0.0f);
            oneButtonsLayout.setAlpha(two ? 0.0f : 1.0f);
            twoButtonsLayout.setVisibility(two ? View.VISIBLE : View.GONE);
            oneButtonsLayout.setVisibility(two ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected LinearLayoutManager createMainLayoutManager(Context context) {
        return scrollCoordinator.createCommonLayoutManager(context);
    }

    @Override
    protected void onHeaderCollapseProgress(float progress) {
        if (iconTextureView != null) {
            iconTextureView.setHeaderTilt(70f * progress);
        }
    }

    @Override
    protected void attachMainList(Context context) {
        contentView.addView(scrollCoordinator, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        scrollCoordinator.setCommonRecyclerView(listView, () -> transactionsRow);
        scrollCoordinator.setPager(transactionsLayout, transactionsLayout.getViewPager(),
                transactionsLayout::getPageListView);
        scrollCoordinator.setTabs(transactionsLayout.getTabsContainer(), ViewGroup.LayoutParams.WRAP_CONTENT);
        transactionsLayout.getTabsContainer().setLayoutParams(LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, -4, 0, -4, 0));
        applyScrollInsets();
    }

    private void configureTransactionsGlass() {
        final FrameLayout captureRoot = contentView;
        getBaseSimpleGlass().setViews(captureRoot, listView, simpleActionBar, resourceProvider);
        simpleActionBar.setBackground(null);
        // Keep the header above the coordinator's fade, and ActionBar controls above the header.
        backgroundView.bringToFront();
        simpleActionBar.bringToFront();
        // The common list translates independently; it cannot bound capture of sibling pages.
        getBaseSimpleGlass().setClipView(scrollCoordinator);
        listView.setCaptureSectionsDecoratorAllowed(true);
        transactionsLayout.setGlassEngine(glassEngine);
        final SiblingRecyclerPagerLayout.ScrollCapture listCapture = (canvas, position, view) -> {
            RecyclerListView recyclerListView = (RecyclerListView) view;
            Blur3Utils.captureRelativeParent(recyclerListView, canvas, position,
                    recyclerListView, captureRoot);
        };
        getBaseSimpleGlass().setCapture((canvas, position) ->
                scrollCoordinator.captureScrollingContent(canvas, position, listCapture));
        FrameLayout tabs = transactionsLayout.getTabsContainer();
        listTopOverlay = new View(getContext());
        listTopOverlay.setAlpha(0f);
        scrollCoordinator.addView(listTopOverlay, scrollCoordinator.indexOfChild(tabs),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        listTopOverlay.setBackground(getBaseSimpleGlass().createActionBarFadeDrawable(listTopOverlay));
        actionBarShownAnimator = new BoolAnimator(0, (id, factor, fraction, callee) ->
                updateListTopOverlayTranslation(), CubicBezierInterpolator.EASE_OUT_QUINT, 380);
        tabsPinnedAnimator = new BoolAnimator(1, (id, factor, fraction, callee) ->
                updateListTopOverlayTranslation(), CubicBezierInterpolator.EASE_OUT_QUINT, 380);
        headerVisibleAnimator = new BoolAnimator(2, (id, factor, fraction, callee) ->
                listTopOverlay.setAlpha(factor), CubicBezierInterpolator.EASE_OUT_QUINT, 380);
        actionBarShownAnimator.setValue(listView.canScrollVertically(-1) || simpleActionBar.isActionModeShowed(), false);
        tabsPinnedAnimator.setValue(scrollCoordinator.areTabsPinned(), false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                actionBarShownAnimator.setValue(listView.canScrollVertically(-1)
                        || simpleActionBar.isActionModeShowed(), true);
            }
        });
        updateListTopOverlaySize();
        tabs.setBackground(getBaseSimpleGlass().factory.create(tabs)
                .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider))
                .setPadding(dp(9.66f))
                .setRadius(dp(18)));
    }

    private void updateListTopOverlaySize() {
        if (listTopOverlay == null) {
            return;
        }
        int height = contentInsetTop + ActionBar.getCurrentActionBarHeight() + dp(44);
        ViewGroup.LayoutParams params = listTopOverlay.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            listTopOverlay.setLayoutParams(params);
        }
        updateListTopOverlayTranslation();
    }

    private void updateListTopOverlayTranslation() {
        if (listTopOverlay == null) {
            return;
        }
        float actionBarFactor = actionBarShownAnimator == null ? 0f : actionBarShownAnimator.getFloatValue();
        float tabsFactor = tabsPinnedAnimator == null ? 0f : tabsPinnedAnimator.getFloatValue();
        listTopOverlay.setTranslationY(-(1f - actionBarFactor) * ActionBar.getCurrentActionBarHeight()
                - (1f - tabsFactor) * dp(44));
    }

    @Override
    protected void onHeaderBackgroundVisibilityChanged(boolean visible) {
        if (headerVisibleAnimator != null && headerVisibleAnimator.getValue() != visible) {
            headerVisibleAnimator.setValue(visible, true);
        }
    }

    @Override
    protected View getMainScrollLayer() {
        return scrollCoordinator;
    }

    @Override
    protected float getMainListOffsetY() {
        return listView == null ? 0f : listView.getY();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        receivedInsets = true;
        contentInsetTop = top;
        contentInsetBottom = bottom;
        applyScrollInsets();
    }

    private void applyScrollInsets() {
        updateListTopOverlaySize();
        if (listView == null || scrollCoordinator == null || listView.getLayoutParams() == null) {
            return;
        }
        int marginTop = Math.min(-dp(8), contentInsetTop - dp(48));
        int marginBottom = Math.min(-dp(8), contentInsetBottom - dp(48));
        AndroidUtilities.setViewLayoutMargins(listView, 0, marginTop, 0, marginBottom);
        // The gradient header's spacer already includes the top inset. Only compensate bleed.
        listView.setPadding(listView.getPaddingLeft(), -marginTop,
                listView.getPaddingRight(), contentInsetBottom - marginBottom);
        scrollCoordinator.setVerticalBleed(-marginTop, -marginBottom);
    }

    @Override
    public void saveScrollPosition() {
        super.saveScrollPosition();
        savedTransactionsPinned = scrollCoordinator != null && scrollCoordinator.areTabsPinned();
        if (listView != null && savedScrollPosition >= 0) {
            savedScrollOffset -= listView.getPaddingTop();
        }
    }

    @Override
    public void applyScrolledPosition(boolean ignorePaddingView) {
        super.applyScrolledPosition(ignorePaddingView);
        if (savedTransactionsPinned && scrollCoordinator != null && transactionsRow != RecyclerView.NO_POSITION) {
            scrollCoordinator.alignCommonBoundaryToPin();
        }
        savedTransactionsPinned = false;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (scrollCoordinator == null || event == null
                || scrollCoordinator.isCommonContentAt(event.getX(), event.getY())) {
            return super.isSwipeBackEnabled(event);
        }
        return !scrollCoordinator.canStartPageSwipe() || transactionsLayout.getViewPager().isCurrentTabFirst();
    }

    /** Returns whether transaction tabs are currently pinned under the ActionBar. */
    public boolean attachedTransactionsLayout() {
        return scrollCoordinator != null && scrollCoordinator.areTabsPinned();
    }

    @Override
    protected boolean usesActionBarFadeBackground() {
        return true;
    }

    @Override
    protected boolean drawActionBarShadow() {
        return false;
    }

    @Override
    public StarParticlesView createParticlesView() {
        return makeParticlesView(getContext(), 75, 1);
    }

    public static StarParticlesView makeParticlesView(Context context, int particlesCount, int type) {
        return new StarParticlesView(context) {
            Paint[] paints;

            @Override
            protected void configure() {
                drawable = new Drawable(particlesCount);
                drawable.type = 106;
                drawable.roundEffect = false;
                drawable.useRotate = false;
                drawable.useBlur = true;
                drawable.checkBounds = true;
                drawable.isCircle = false;
                drawable.useScale = true;
                drawable.startFromCenter = true;
                if (type == 1) {
                    drawable.centerOffsetY = dp(32 - 8);
                }
                paints = new Paint[20];
                for (int i = 0; i < paints.length; ++i) {
                    paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paints[i].setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFF2E81D9, 0xFF26BBFA, i / (float) (paints.length - 1)), PorterDuff.Mode.SRC_IN));
                }
                drawable.getPaint = i -> paints[i % paints.length];
                drawable.size1 = 17;
                drawable.size2 = 18;
                drawable.size3 = 19;
                drawable.colorKey = Theme.key_windowBackgroundWhiteBlackText;
                drawable.init();
            }

            @Override
            protected int getStarsRectWidth() {
                return getMeasuredWidth();
            }

            { setClipWithGradient(); }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (iconTextureView != null) {
            iconTextureView.setPaused(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (iconTextureView != null) {
            iconTextureView.setPaused(true);
        }
    }

    @Override
    protected View getHeader(Context context) {
        return super.getHeader(context);
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, classGuid, true, this::fillItems, getResourceProvider()) {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == UniversalAdapter.VIEW_TYPE_ANIMATED_HEADER) {
                    HeaderCell headerCell = new HeaderCell(getContext(), Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, false, resourceProvider);
                    headerCell.setHeight(40 - 15);
                    return new RecyclerListView.Holder(headerCell);
                }
                return super.onCreateViewHolder(parent, viewType);
            }
        };
        adapter.setApplyBackground(false);
        return adapter;
    }

    private boolean expanded = false;
    private final int BUTTON_EXPAND = -1;
    private final int BUTTON_GIFT = -2;
    private final int BUTTON_SUBSCRIPTIONS_EXPAND = -3;
    private final int BUTTON_AFFILIATE = -4;

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean wasPinned = scrollCoordinator != null && scrollCoordinator.areTabsPinned();
        transactionsRow = RecyclerView.NO_POSITION;
        if (getContext() == null) {
            return;
        }

        final StarsController c = StarsController.getTonInstance(currentAccount);

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(UItem.asCustom(balanceLayout));
        if (allowTopUp) {
            items.add(UItem.asShadow(getString(R.string.GramEarningsHint)));
        }

        if (hadTransactions = c.hasTransactions()) {
            if (!allowTopUp) items.add(UItem.asShadow(null));
            transactionsRow = items.size();
            items.add(UItem.asCustomShadow(transactionsBoundary, LayoutHelper.WRAP_CONTENT));
            if (wasPinned && listView != null) {
                listView.removeCallbacks(alignTransactionsBoundary);
                listView.post(alignTransactionsBoundary);
            }
        } else {
            items.add(UItem.asCustomShadow(emptyLayout));
        }
    }

    public void onItemClick(UItem item, int position) {
        if (item.id == BUTTON_EXPAND) {
            expanded = !expanded;
            adapter.update(true);
        } else if (item.id == BUTTON_GIFT) {
            StarsController.getTonInstance(currentAccount).getGiftOptions();
            UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STARS, 0, BirthdayController.getInstance(currentAccount).getState());
        } else if (item.id == BUTTON_SUBSCRIPTIONS_EXPAND) {
            StarsController.getTonInstance(currentAccount).loadSubscriptions();
            adapter.update(true);
        } else if (item.id == BUTTON_AFFILIATE) {
            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                AccountFrozenAlert.show(currentAccount);
                return;
            }
            presentFragment(new ChannelAffiliateProgramsFragment(getUserConfig().getClientUserId()));
        }
    }

    public static class StarsNeededSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {
        private final AmountUtils.Amount requiredAmount;

        private final HeaderView headerView;
        private final FrameLayout footerView;
        private final ButtonWithCounterView topUpButton;
        private Runnable whenPurchased;

        private int lastThemedBackgroundColor;

        @Override
        public void onThemeColorsChanged() {
            super.onThemeColorsChanged();
            try {
                final int color = Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
                if (color != lastThemedBackgroundColor) { // setBackgroundColor allocates a color filter
                    lastThemedBackgroundColor = color;
                    setBackgroundColor(color);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starOptionsLoaded || id == NotificationCenter.starBalanceUpdated) {
                if (adapter != null) {
                    adapter.update(true);
                }

                AmountUtils.Amount balance = StarsController.getTonInstance(currentAccount).getBalanceAmount();
                headerView.titleView.setText(formatString(R.string.TonNeededTitle,
                    AmountUtils.Amount.fromNano(requiredAmount.asNano() - balance.asNano(), AmountUtils.Currency.TON).asFormatString()));
                if (actionBar != null) {
                    actionBar.setTitle(getTitle());
                }
                if (balance.asNano() >= requiredAmount.asNano()) {
                    if (whenPurchased != null) {
                        whenPurchased.run();
                        whenPurchased = null;
                        dismiss();
                    }
                }
            }
        }

        @Override
        public void show() {
            AmountUtils.Amount balance = StarsController.getTonInstance(currentAccount).getBalanceAmount();
            if (balance.asNano() >= requiredAmount.asNano()) {
                if (whenPurchased != null) {
                    whenPurchased.run();
                    whenPurchased = null;
                }
                return;
            }
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment instanceof ChatActivity) {
                ChatActivity chatActivity = (ChatActivity) lastFragment;
                if (chatActivity.isKeyboardVisible() && chatActivity.getChatActivityEnterView() != null) {
                    chatActivity.getChatActivityEnterView().closeKeyboard();
                }
            }
            super.show();
            NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starOptionsLoaded);
            NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starBalanceUpdated);
        }

        @Override
        public void dismissInternal() {
            super.dismissInternal();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starOptionsLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        }

        public StarsNeededSheet(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            AmountUtils.Amount requiredAmount,
            boolean canToUpFragment,
            Runnable whenPurchased
        ) {
            super(context, null, false, false, false, resourcesProvider);

            topPadding = .2f;

            this.whenPurchased = whenPurchased;

            fixNavigationBar();
            recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            recyclerListView.setOnItemClickListener((view, position) -> {
                if (adapter == null) return;
                UItem item = adapter.getItem(position - 1);
                if (item == null) return;
                onItemClick(item, adapter);
            });
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            recyclerListView.setItemAnimator(itemAnimator);
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

            this.requiredAmount = requiredAmount;
            headerView = new HeaderView(context, currentAccount, resourcesProvider);

            final AmountUtils.Amount balance = StarsController.getTonInstance(currentAccount).getBalanceAmount();
            headerView.titleView.setText(formatString(R.string.TonNeededTitle,
                AmountUtils.Amount.fromNano(requiredAmount.asNano() - balance.asNano(), AmountUtils.Currency.TON).asFormatString()));

            headerView.subtitleView.setText(AndroidUtilities.replaceTags(getString(R.string.FragmentAddFunds)));
            headerView.subtitleView.setMaxWidth(HintView2.cutInFancyHalf(headerView.subtitleView.getText(), headerView.subtitleView.getPaint()));
            actionBar.setTitle(getTitle());

            footerView = new FrameLayout(context);

            topUpButton = new ButtonWithCounterView(getContext(), getResourcesProvider());
            footerView.addView(topUpButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 20, 10, 20, 20));

            if (canToUpFragment || allowTopUp()) {
                topUpButton.setText(getString(R.string.TopUpViaFragment), false);
                topUpButton.setOnClickListener(v -> {
                    Browser.openUrlInSystemBrowser(getContext(), getString(R.string.TopUpViaFragmentLink));
                });
            } else {
                topUpButton.setText(getString(R.string.Close), false);
                topUpButton.setOnClickListener(v -> {
                   dismiss();
                });
            }

            if (adapter != null) {
                adapter.update(false);
            }
        }

        @Override
        protected CharSequence getTitle() {
            if (headerView == null) return null;
            return headerView.titleView.getText();
        }

        private UniversalAdapter adapter;
        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            return adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        }

        private boolean expanded;

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCustom(headerView));
            items.add(UItem.asCustom(footerView));
            // items.add(UItem.asSpace(dp(256)));
        }

        public void onItemClick(UItem item, UniversalAdapter adapter) {

        }

        @Override
        public void dismiss() {
            super.dismiss();
            if (headerView != null) {
                headerView.iconView.setPaused(true);
            }
        }

        public static class HeaderView extends LinearLayout {
            private final FrameLayout topView;
            public final StarParticlesView particlesView;
            public final WalletDiamondView iconView;
            public final TextView titleView;
            public final TextView subtitleView;

            public HeaderView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                setOrientation(VERTICAL);
                topView = new FrameLayout(context);
                topView.setClipChildren(false);
                topView.setClipToPadding(false);

                particlesView = makeParticlesView(context, 70, 0);
                topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                iconView = new WalletDiamondView(context, 170);
                iconView.setStarParticlesView(particlesView);
                topView.addView(iconView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
                iconView.setPaused(false);

                addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180));

                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                titleView.setTypeface(AndroidUtilities.bold());
                ThemeBind.textColor(titleView, Theme.key_dialogTextBlack, resourcesProvider);
                titleView.setGravity(Gravity.CENTER);
                addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

                subtitleView = new TextView(context);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                ThemeBind.textColor(subtitleView, Theme.key_dialogTextBlack, resourcesProvider);
                subtitleView.setGravity(Gravity.CENTER);
                addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 18));
            }
        }
    }
}

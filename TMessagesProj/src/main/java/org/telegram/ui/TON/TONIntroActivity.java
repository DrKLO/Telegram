package org.telegram.ui.TON;

import static org.telegram.messenger.AndroidUtilities.dp;
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
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
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

import java.util.ArrayList;

public class TONIntroActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout aboveTitleView;
    private GLIconTextureView iconTextureView;

    private StarsIntroActivity.StarsTransactionsLayout transactionsLayout;
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starSubscriptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsUpdated);
        StarsController.getTonInstance(currentAccount).invalidateTransactions(true);
        StarsController.getTonInstance(currentAccount).invalidateSubscriptions(true);
        StarsController.getTonInstance(currentAccount).getOptions();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
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
            if (savedScrollPosition == 0 && savedScrollOffset < 0) {
                savedScrollOffset = 0;
            }
            applyScrolledPosition();
        } else if (id == NotificationCenter.starTransactionsLoaded) {
            final StarsController c = StarsController.getTonInstance(currentAccount);
            if (hadTransactions != c.hasTransactions()) {
                hadTransactions = c.hasTransactions();
                saveScrollPosition();
                if (adapter != null) {
                    adapter.update(true);
                }
                if (savedScrollPosition == 0 && savedScrollOffset < 0) {
                    savedScrollOffset = 0;
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
    public View createView(Context context) {
        useFillLastLayoutManager = false;
        particlesViewHeight = dp(32 + 190 + 16);
        transactionsLayout = new StarsIntroActivity.StarsTransactionsLayout(context, currentAccount, true, 0, getClassGuid(), getResourceProvider());
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int firstViewHeight;
                if (isLandscapeMode) {
                    firstViewHeight = statusBarHeight + actionBar.getMeasuredHeight() - AndroidUtilities.dp(16);
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
        emptyLayout.setBackgroundColor(Theme.getColor(allowTopUp ? Theme.key_dialogBackgroundGray : Theme.key_dialogBackground));

        super.createView(context);

        aboveTitleView = new FrameLayout(context);
        aboveTitleView.setClickable(true);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_DIAMOND);
        iconTextureView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconTextureView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconTextureView.mRenderer.updateColors();
        iconTextureView.setStarParticlesView(particlesView);
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
        configureHeader(getString(R.string.TONBalanceTitle), AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.TONBalanceText), () -> {
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
        balanceLayout.setPadding(0, 0, 0, dp(10));

        starBalanceTextView = new AnimatedTextView(getContext(), false, true, false);
        starBalanceTextView.setTypeface(AndroidUtilities.bold());
        starBalanceTextView.setTextSize(dp(32));
        starBalanceTextView.setGravity(Gravity.CENTER);
        starBalanceTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));

        starBalanceIcon = new SpannableStringBuilder("S");
        final ColoredImageSpan starBalanceIconSpan = new ColoredImageSpan(R.drawable.ton);
        starBalanceIconSpan.setOverrideColor(0xFF3391D4);
        starBalanceIconSpan.setScale(0.5f, 0.5f);
        starBalanceIconSpan.translate(-dp(3), 0);
        starBalanceIcon.setSpan(starBalanceIconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        balanceLayout.addView(starBalanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER, 24, 0, 24, 0));

        starBalanceTitleView = new AnimatedTextView(getContext());
        starBalanceTitleView.setTextSize(dp(14));
        starBalanceTitleView.setGravity(Gravity.CENTER);
        starBalanceTitleView.setText(LocaleController.getString(R.string.YourTonBalance));
        starBalanceTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
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
            buyButton = new ButtonWithCounterView(getContext(), resourceProvider);
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

        topUpButton = new ButtonWithCounterView(getContext(), resourceProvider);
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

        withdrawButton = new ButtonWithCounterView(getContext(), resourceProvider);
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
    protected ContentView createContentView() {
        return new NestedFrameLayout(getContext());
    }

    private class NestedFrameLayout extends ContentView implements NestedScrollingParent3 {

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

    public boolean attachedTransactionsLayout() {
        if (transactionsLayout == null || !(transactionsLayout.getParent() instanceof View))
            return false;
        int bottom = ((View) transactionsLayout.getParent()).getBottom();
        return listView.getHeight() - bottom >= 0;
    }

    @Override
    protected boolean drawActionBarShadow() {
        return !attachedTransactionsLayout();
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
            iconTextureView.setDialogVisible(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (iconTextureView != null) {
            iconTextureView.setPaused(true);
            iconTextureView.setDialogVisible(true);
        }
    }

    @Override
    protected View getHeader(Context context) {
        return super.getHeader(context);
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, classGuid, true, this::fillItems, getResourceProvider()) {
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
    }

    private boolean expanded = false;
    private final int BUTTON_EXPAND = -1;
    private final int BUTTON_GIFT = -2;
    private final int BUTTON_SUBSCRIPTIONS_EXPAND = -3;
    private final int BUTTON_AFFILIATE = -4;

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) {
            return;
        }

        final StarsController c = StarsController.getTonInstance(currentAccount);

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(UItem.asCustom(balanceLayout));
        if (allowTopUp) {
            items.add(UItem.asShadow(getString(R.string.TopUpViaFragmentInfo)));
        }

        if (hadTransactions = c.hasTransactions()) {
            if (!allowTopUp) items.add(UItem.asShadow(null));
            items.add(UItem.asFullscreenCustom(transactionsLayout, ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight));
        } else {
            items.add(UItem.asCustom(emptyLayout));
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
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starOptionsLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
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
            public final GLIconTextureView iconView;
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

                iconView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_DIAMOND);
                iconView.mRenderer.colorKey1 = Theme.key_starsGradient1;
                iconView.mRenderer.colorKey2 = Theme.key_starsGradient2;
                iconView.mRenderer.updateColors();
                iconView.setStarParticlesView(particlesView);
                topView.addView(iconView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
                iconView.setPaused(false);

                addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180));

                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                titleView.setGravity(Gravity.CENTER);
                addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

                subtitleView = new TextView(context);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                subtitleView.setGravity(Gravity.CENTER);
                addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 18));
            }
        }
    }
}

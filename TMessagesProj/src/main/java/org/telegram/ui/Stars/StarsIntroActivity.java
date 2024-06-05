package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.SessionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.GradientHeaderActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class StarsIntroActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private StarsBalanceView balanceView;

    private FrameLayout aboveTitleView;
    private GLIconTextureView iconTextureView;

    private StarsTransactionsLayout transactionsLayout;
    private View emptyLayout;
    private FireworksOverlay fireworksOverlay;

    public StarsIntroActivity() {
        setWhiteBackground(true);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
        StarsController.getInstance(currentAccount).invalidateTransactions(true);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
    }

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
        }
    }

    @Override
    public View createView(Context context) {
        useFillLastLayoutManager = false;
        particlesViewHeight = dp(32 + 190 + 16);
        transactionsLayout = new StarsTransactionsLayout(context, currentAccount, getClassGuid(), getResourceProvider());
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.EXACTLY));
            }
        };
        emptyLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        super.createView(context);

        balanceView = new StarsBalanceView(context, currentAccount);
        actionBar.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM));

//        yOffset = dp(16);
        aboveTitleView = new FrameLayout(context);
        aboveTitleView.setClickable(true);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR);
        iconTextureView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconTextureView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconTextureView.mRenderer.updateColors();
        iconTextureView.setStarParticlesView(particlesView);
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(190, 190, Gravity.CENTER, 0, 32, 0, 24));
        configureHeader(getString(R.string.TelegramStars), getString(R.string.TelegramStarsInfo), aboveTitleView, null);

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

        return fragmentView;
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
                drawable.type = 105;
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
                    paints[i].setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0xFFFA5416, 0xFFFFC837, i / (float) (paints.length - 1)), PorterDuff.Mode.SRC_IN));
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
                if (viewType == UniversalAdapter.VIEW_TYPE_HEADER) {
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

    public static class StarTier {
        public final boolean main;
        public final int id;
        public final int stars;
        public final int amount;
        public final String currency;
        public StarTier(boolean main, int id, int stars, int amount, String currency) {
            this.main = main;
            this.id = id;
            this.stars = stars;
            this.amount = amount;
            this.currency = currency;
        }
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getContext() == null) {
            return;
        }

        final StarsController c = StarsController.getInstance(currentAccount);

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(UItem.asHeader(getString(R.string.TelegramStarsChoose)));

        int stars = 1;
        ArrayList<TLRPC.TL_starsTopupOption> options = c.getOptions();
        if (options != null && !options.isEmpty()) {
            int hidden = 0;
            for (int id = 0; id < options.size(); ++id) {
                TLRPC.TL_starsTopupOption option = options.get(id);
                if (option.collapsed && !expanded) {
                    hidden++;
                    continue;
                }
                items.add(StarTierView.Factory.asStarTier(id, stars++, option));
            }
            if (!expanded && hidden > 0) {
                items.add(ExpandView.Factory.asExpand(BUTTON_EXPAND, getString(expanded ? R.string.NotifyLessOptions : R.string.NotifyMoreOptions), !expanded).accent());
            }
        } else {
            items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
            items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
            items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
            items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
            items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
        }

        items.add(UItem.asShadow(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTOS), () -> {
            Browser.openUrl(getContext(), getString(R.string.StarsTOSLink));
        })));

//        if (c.hasTransactions()) {
            items.add(UItem.asFullscreenCustom(transactionsLayout, ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight));
//        } else {
//            items.add(UItem.asCustom(emptyLayout));
//        }
    }

    public void onItemClick(UItem item, int position) {
        if (item.id == BUTTON_EXPAND) {
            expanded = !expanded;
            adapter.update(true);
        } else if (item.instanceOf(StarTierView.Factory.class)) {
            if (item.object instanceof TLRPC.TL_starsTopupOption) {
                StarsController.getInstance(currentAccount).buy(getParentActivity(), (TLRPC.TL_starsTopupOption) item.object, (success, error) -> {
                    if (getContext() == null) return;
                    if (success) {
                        Drawable starDrawable = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
                        BulletinFactory.of(this).createSimpleBulletin(starDrawable, getString(R.string.StarsAcquired), AndroidUtilities.replaceTags(formatPluralString("StarsAcquiredInfo", (int) item.longValue))).show();
                        fireworksOverlay.start(true);
                        StarsController.getInstance(currentAccount).invalidateTransactions(true);
                    } else if (error != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                    }
                });
            }
        }
    }

    public static class StarsBalanceView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final TextView headerTextView;
        private final AnimatedTextView amountTextView;

        public StarsBalanceView(Context context, int currentAccount) {
            super(context);

            this.currentAccount = currentAccount;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

            headerTextView = new TextView(context);
            headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            headerTextView.setText(getString(R.string.StarsBalance));
            headerTextView.setGravity(Gravity.RIGHT);
            headerTextView.setTypeface(AndroidUtilities.bold());
            addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));

            Drawable starDrawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
            amountTextView = new AnimatedTextView(context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    int x = (int) (getMeasuredWidth() - getDrawable().getCurrentWidth() - dp(20));
                    starDrawable.setBounds(x, (getMeasuredHeight() - dp(17)) / 2, x + dp(17), (getMeasuredHeight() + dp(17)) / 2);
                    starDrawable.draw(canvas);
                    super.dispatchDraw(canvas);
                }
            };
            amountTextView.adaptWidth = true;
            amountTextView.getDrawable().setHacks(false, true, true);
            amountTextView.setTypeface(AndroidUtilities.bold());
            amountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            amountTextView.setTextSize(dp(13));
            amountTextView.setGravity(Gravity.RIGHT);
            amountTextView.setPadding(dp(19), 0, 0, 0);
            addView(amountTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.RIGHT, 0, -2, 0, 0));

            updateBalance(false);

            setPadding(dp(15), dp(4), dp(15), dp(4));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateBalance(false);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starBalanceUpdated) {
                updateBalance(true);
            }
        }

        private SpannableString loadingString;
        private long lastBalance = -1;

        public void updateBalance(boolean animated) {
            StarsController c = StarsController.getInstance(currentAccount);

            amountTextView.cancelAnimation();
            long balance = StarsController.getInstance(currentAccount).getBalance();
            if (balance > lastBalance && lastBalance != -1) {
                bounce();
            }
            if (!c.balanceAvailable()) {
                if (loadingString == null) {
                    loadingString = new SpannableString("x");
                    loadingString.setSpan(new LoadingSpan(amountTextView, dp(48)), 0, loadingString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                amountTextView.setText(loadingString, animated);
                lastBalance = -1;
            } else {
                amountTextView.setText(LocaleController.formatNumber(balance, ' '));
                lastBalance = balance;
            }
        }

        private ValueAnimator bounceAnimator;
        public void bounce() {
            if (bounceAnimator != null) {
                bounceAnimator.cancel();
            }
            bounceAnimator = ValueAnimator.ofFloat(.9f, 1f);
            bounceAnimator.addUpdateListener(anm -> {
                float t = (float) anm.getAnimatedValue();
                amountTextView.setScaleX(t);
                amountTextView.setScaleY(t);
            });
            bounceAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    amountTextView.setScaleX(1f);
                    amountTextView.setScaleY(1f);
                }
            });
            bounceAnimator.setDuration(320);
            bounceAnimator.setInterpolator(new OvershootInterpolator());
            bounceAnimator.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ActionBar.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
        }
    }

    public static class StarTierView extends FrameLayout {

        private final Drawable starDrawableOutline;
        private final Drawable starDrawable;
        private final TextView textView;
        private final AnimatedTextView textView2;

        public StarTierView(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            starDrawableOutline = context.getResources().getDrawable(R.drawable.star_small_outline).mutate();
            starDrawableOutline.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.SRC_IN));
            starDrawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
            setWillNotDraw(false);

            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START, 48, 0, 0, 0));

            textView2 = new AnimatedTextView(context);
            textView2.setTextSize(dp(15));
            textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textView2.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            addView(textView2, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 21, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 19, 0));
        }

        private SpannableString loading;

        private boolean needDivider;
        public void set(int starsCount, CharSequence text1, CharSequence text2, boolean divider) {
            final boolean animated = TextUtils.equals(textView.getText(), text1);

            this.starsCount = starsCount;
            if (!animated) {
                animatedStarsCount.set(starsCount, true);
            }
            textView.setText(text1);
            if (text2 == null) {
                if (loading == null) {
                    loading = new SpannableString("x");
                    loading.setSpan(new LoadingSpan(textView2, dp(55)), 0, loading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                text2 = loading;
            }
            textView2.setText(text2);
            final float rtl = LocaleController.isRTL ? -1f : 1f;
            if (animated) {
                textView.animate().translationX(rtl * (starsCount - 1) * dp(2.66f)).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                textView.setTranslationX(rtl * (starsCount - 1) * dp(2.66f));
            }

            needDivider = divider;
            invalidate();
        }

        private int starsCount;
        private final AnimatedFloat animatedStarsCount = new AnimatedFloat(this, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final float starsCount = animatedStarsCount.set(this.starsCount);
            final float rtl = LocaleController.isRTL ? -1f : 1f;
            final float wsize = dp(24), hsize = dp(24);
            final float pad = dp(2.5f);
            final float sx = LocaleController.isRTL ? getWidth() - dp(19) - wsize : dp(19);
            for (int i = (int) Math.ceil(starsCount) - 1; i >= 0; --i) {
                final float alpha = Utilities.clamp(starsCount - i, 1f, 0f);
                final float x = sx + (i - 1 - (1f - alpha)) * pad * rtl;
                final float y = (getMeasuredHeight() - hsize) / 2f;
                starDrawableOutline.setBounds((int) x, (int) y, (int) (x + wsize), (int) (y + hsize));
                starDrawableOutline.setAlpha((int) (0xFF * alpha));
                starDrawableOutline.draw(canvas);
                starDrawable.setBounds((int) x, (int) y, (int) (x + wsize), (int) (y + hsize));
                starDrawable.setAlpha((int) (0xFF * alpha));
                starDrawable.draw(canvas);
            }

            if (needDivider) {
                canvas.drawRect(LocaleController.isRTL ? 0 : dp(22), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(22) : 0), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<StarTierView> {
            @Override
            public StarTierView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new StarTierView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((StarTierView) view).set(item.intValue, item.text, item.subtext, divider);
            }

            public static UItem asStarTier(int id, int index, TLRPC.TL_starsTopupOption option) {
                UItem item = UItem.ofFactory(StarTierView.Factory.class);
                item.id = id;
                item.intValue = index;
                item.longValue = option.stars;
                item.text = formatPluralString("StarsCount", (int) option.stars);
                item.subtext = option.loadingStorePrice ? null : BillingController.getInstance().formatCurrency(option.amount, option.currency);
                item.object = option;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a.intValue == b.intValue && a.id == b.id && TextUtils.equals(a.subtext, b.subtext);
            }
        }
    }

    public static class ExpandView extends FrameLayout {

        public final AnimatedTextView textView;
        public final ImageView arrowView;

        public ExpandView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            textView = new AnimatedTextView(context);
            textView.getDrawable().setHacks(true, true, true);
            textView.setTextSize(dp(15));
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.START, 22, 0, 17 + 24 + 17, 0));

            arrowView = new ImageView(context);
            arrowView.setScaleType(ImageView.ScaleType.CENTER);
            arrowView.setImageResource(R.drawable.arrow_more);
            addView(arrowView, LayoutHelper.createFrameRelatively(24, 24, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 17, 0));
        }

        private int lastId;
        private boolean needDivider;
        public void set(UItem item, boolean divider) {
            final boolean animated = lastId == item.id;
            lastId = item.id;
            textView.setText(item.text, animated);
            final int color = Theme.getColor(item.accent ? Theme.key_windowBackgroundWhiteBlueText2 : Theme.key_windowBackgroundWhiteBlackText);
            textView.setTextColor(color);
            arrowView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            if (animated) {
                arrowView.animate().rotation(item.collapsed ? 0 : 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                arrowView.setRotation(item.collapsed ? 0 : 180);
            }
            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(LocaleController.isRTL ? 0 : dp(22), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(22) : 0), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            lastId = Integer.MAX_VALUE;
        }

        public static class Factory extends UItem.UItemFactory<ExpandView> {
            @Override
            public ExpandView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ExpandView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((ExpandView) view).set(item, divider);
            }

            public static UItem asExpand(int id, CharSequence text, boolean collapsed) {
                UItem item = UItem.ofFactory(ExpandView.Factory.class);
                item.id = id;
                item.text = text;
                item.collapsed = collapsed;
                return item;
            }
        }
    }

    public static class StarsTransactionsLayout extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final ViewPagerFixed viewPager;
        private final PageAdapter adapter;
        private final ViewPagerFixed.TabsView tabsView;

        private static class PageAdapter extends ViewPagerFixed.Adapter {

            private final Context context;
            private final int currentAccount;
            private final int classGuid;
            private final Theme.ResourcesProvider resourcesProvider;

            public PageAdapter(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                this.context = context;
                this.currentAccount = currentAccount;
                this.classGuid = classGuid;
                this.resourcesProvider = resourcesProvider;
                fill();
            }

            private final ArrayList<UItem> items = new ArrayList<>();

            public void fill() {
                items.clear();
                StarsController s = StarsController.getInstance(currentAccount);
                items.add(UItem.asSpace(StarsController.ALL_TRANSACTIONS));
                if (s.hasTransactions(StarsController.INCOMING_TRANSACTIONS)) {
                    items.add(UItem.asSpace(StarsController.INCOMING_TRANSACTIONS));
                }
                if (s.hasTransactions(StarsController.OUTGOING_TRANSACTIONS)) {
                    items.add(UItem.asSpace(StarsController.OUTGOING_TRANSACTIONS));
                }
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public View createView(int viewType) {
                return new Page(context, viewType, currentAccount, classGuid, resourcesProvider);
            }

            @Override
            public void bindView(View view, int position, int viewType) {}

            @Override
            public int getItemViewType(int position) {
                if (position < 0 || position >= items.size())
                    return StarsController.ALL_TRANSACTIONS;
                return items.get(position).intValue;
            }

            @Override
            public String getItemTitle(int position) {
                final int viewType = getItemViewType(position);
                switch (viewType) {
                    case StarsController.ALL_TRANSACTIONS: return getString(R.string.StarsTransactionsAll);
                    case StarsController.INCOMING_TRANSACTIONS: return getString(R.string.StarsTransactionsIncoming);
                    case StarsController.OUTGOING_TRANSACTIONS: return getString(R.string.StarsTransactionsOutgoing);
                    default: return "";
                }
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starTransactionsLoaded) {
                adapter.fill();
                viewPager.fillTabs(true);
            }
        }

        public StarsTransactionsLayout(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;

            setOrientation(VERTICAL);

            viewPager = new ViewPagerFixed(context);
            viewPager.setAdapter(adapter = new PageAdapter(context, currentAccount, classGuid, resourcesProvider));
            tabsView = viewPager.createTabsView(true, 3);

            View separatorView = new View(context);
            separatorView.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));

            addView(tabsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            addView(separatorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density));
            addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        }

        @Override
        protected void onAttachedToWindow() {
            adapter.fill();
            viewPager.fillTabs(false);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
            super.onDetachedFromWindow();
        }

        public RecyclerListView getCurrentListView() {
            View currentView = viewPager.getCurrentView();
            if (!(currentView instanceof Page)) return null;
            return ((Page) currentView).listView;
        }

        public static class Page extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

            private final UniversalRecyclerView listView;
            private final Theme.ResourcesProvider resourcesProvider;
            private final int currentAccount;
            private final int type;

            public Page(Context context, int type, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                this.type = type;
                this.currentAccount = currentAccount;
                this.resourcesProvider = resourcesProvider;

                listView = new UniversalRecyclerView(context, currentAccount, classGuid, true, this::fillItems, this::onClick, null, resourcesProvider);
                addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        scheduleLoadTransactions();
                    }
                });
            }

            private final Runnable loadTransactionsRunnable = () -> {
                StarsController.getInstance(Page.this.currentAccount).loadTransactions(Page.this.type);
            };

            private void scheduleLoadTransactions() {
                if (!Page.this.listView.canScrollVertically(1)) {
                    AndroidUtilities.cancelRunOnUIThread(loadTransactionsRunnable);
                    AndroidUtilities.runOnUIThread(loadTransactionsRunnable, 250);
                }
            }

            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.starTransactionsLoaded) {
                    listView.adapter.update(true);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
                listView.adapter.update(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
            }

            private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
                final StarsController c = StarsController.getInstance(currentAccount);
                for (TLRPC.TL_starsTransaction t : c.transactions[type]) {
                    items.add(StarsTransactionView.Factory.asTransaction(t));
                }
                if (!c.didFullyLoadTransactions(type)) {
                    items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                }
            }

            private void onClick(UItem item, View view, int position, float x, float y) {
                if (item.object instanceof TLRPC.TL_starsTransaction) {
                    showTransactionSheet(getContext(), currentAccount, (TLRPC.TL_starsTransaction) item.object, resourcesProvider);
                }
            }

        }
    }

    public static class Transaction {
        public int date;
        public String platform;
        public long userId;
        public int stars;

        public static Transaction as(int date, String platform, int stars) {
            Transaction t = new Transaction();
            t.date = date;
            t.platform = platform;
            t.stars = stars;
            return t;
        }

        public static Transaction as(int date, long userId, int stars) {
            Transaction t = new Transaction();
            t.date = date;
            t.userId = userId;
            t.stars = stars;
            return t;
        }
    }

    public static class StarsTransactionView extends LinearLayout {

        private final int currentAccount;

        private final AvatarDrawable avatarDrawable;
        private final BackupImageView imageView;
        private final LinearLayout textLayout;
        private final TextView titleTextView;
        private final LinearLayout.LayoutParams titleTextViewParams;
        private final TextView subtitleTextView;
        private final TextView dateTextView;
        private final LinearLayout.LayoutParams dateTextViewParams;
        private final TextView amountTextView;

        private final SpannableString star;

        private boolean threeLines;

        public StarsTransactionView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;

            setOrientation(HORIZONTAL);

            avatarDrawable = new AvatarDrawable();
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(46));
            addView(imageView, LayoutHelper.createLinear(46, 46, Gravity.CENTER_VERTICAL, 13, 0, 13, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);
            textLayout.setGravity(Gravity.CENTER | Gravity.LEFT);
            addView(textLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            titleTextView = new TextView(context);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textLayout.addView(titleTextView, titleTextViewParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4.33f));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, .33f));

            dateTextView = new TextView(context);
            dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textLayout.addView(dateTextView, dateTextViewParams = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            amountTextView = new TextView(context);
            amountTextView.setTypeface(AndroidUtilities.bold());
            amountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.3f);
            amountTextView.setGravity(Gravity.RIGHT);
            addView(amountTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 20, 0));

            star = new SpannableString("⭐️");
            Drawable drawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
            drawable.setBounds(0, 0, dp(21), dp(21));
            star.setSpan(new ImageSpan(drawable), 0, star.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        public static HashMap<String, Drawable> cachedPlatformDrawables;
        public static Drawable getPlatformDrawable(String platform) {
            if (cachedPlatformDrawables == null) {
                cachedPlatformDrawables = new HashMap<>();
            }
            Drawable drawable = cachedPlatformDrawables.get(platform);
            if (drawable == null) {
                cachedPlatformDrawables.put(platform, drawable = SessionCell.createDrawable(44, platform));
            }
            return drawable;
        }

        private boolean needDivider;
        public void set(TLRPC.TL_starsTransaction transaction, boolean divider) {
            long did = DialogObject.getPeerDialogId(transaction.peer.peer);

            threeLines = did != 0;
            titleTextViewParams.bottomMargin = threeLines ? 0 : dp(4.33f);
            subtitleTextView.setVisibility(threeLines ? View.VISIBLE : View.GONE);
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, threeLines ? 13 : 14);

            dateTextView.setText(LocaleController.formatShortDateTime(transaction.date) + (transaction.refund ? " — " + getString(R.string.StarsRefunded) : ""));
            if (did != 0) {
                String username;
                if (did >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                    if (transaction.photo == null) {
                        avatarDrawable.setInfo(user);
                        imageView.setForUserOrChat(user, avatarDrawable);
                    }
                    username = UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    if (transaction.photo == null) {
                        avatarDrawable.setInfo(chat);
                        imageView.setForUserOrChat(chat, avatarDrawable);
                    }
                    username = chat == null ? "" : chat.title;
                }
                titleTextView.setText(transaction.title != null ? transaction.title : "");
                subtitleTextView.setText(username);
                if (transaction.photo != null) {
                    imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(transaction.photo)), "46_46", null, 0, null);
                }
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerAppStore) {
                titleTextView.setText(getString(R.string.StarsTransactionInApp));
                imageView.setImageDrawable(getPlatformDrawable("ios"));
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPlayMarket) {
                titleTextView.setText(getString(R.string.StarsTransactionInApp));
                imageView.setImageDrawable(getPlatformDrawable("android"));
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerFragment) {
                titleTextView.setText(getString(R.string.StarsTransactionFragment));
                imageView.setImageDrawable(getPlatformDrawable("fragment"));
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPremiumBot) {
                titleTextView.setText(getString(R.string.StarsTransactionBot));
                imageView.setImageDrawable(getPlatformDrawable("premiumbot"));
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerUnsupported) {
                titleTextView.setText(getString(R.string.StarsTransactionUnsupported));
                imageView.setImageDrawable(getPlatformDrawable("?"));
            } else {
                titleTextView.setText("");
                imageView.setImageDrawable(null);
            }

            if (transaction.stars > 0) {
                amountTextView.setVisibility(View.VISIBLE);
                amountTextView.setTextColor(Theme.getColor(Theme.key_color_green));
                amountTextView.setText(TextUtils.concat("+", LocaleController.formatNumber(transaction.stars, ' '), " ", star));
            } else if (transaction.stars < 0) {
                amountTextView.setVisibility(View.VISIBLE);
                amountTextView.setTextColor(Theme.getColor(Theme.key_color_red));
                amountTextView.setText(TextUtils.concat("-", LocaleController.formatNumber(-transaction.stars, ' '), " ", star));
            } else {
                amountTextView.setVisibility(View.GONE);
            }

            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(LocaleController.isRTL ? 0 : dp(72), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(72) : 0), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(threeLines ? 71 : 58), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<StarsTransactionView> {
            @Override
            public StarsTransactionView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                StarsTransactionView cached = getCached();
                if (cached != null) {
                    return cached;
                }
                return new StarsTransactionView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((StarsTransactionView) view).set((TLRPC.TL_starsTransaction) item.object, divider);
            }

            public static UItem asTransaction(TLRPC.TL_starsTransaction transaction) {
                UItem item = UItem.ofFactory(StarsTransactionView.Factory.class);
                item.object = transaction;
                return item;
            }
        }
    }

    public static BottomSheet openConfirmPurchaseSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        int currentAccount,
        long userId,
        String purchase,
        long stars,
        TLRPC.WebDocument photo,
        Utilities.Callback<Utilities.Callback<Boolean>> whenConfirmed,
        Runnable whenDismissed
    ) {
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), 0, dp(16), dp(8));

        FrameLayout topView = new FrameLayout(context);
        topView.addView(makeParticlesView(context, 40, 0), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        BackupImageView imageView = new BackupImageView(context);
        if (photo == null) {
            imageView.setRoundRadius(dp(80));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);
        } else {
            imageView.setRoundRadius(dp(80));
            imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(photo)), "80_80", null, 0, null);
        }
        topView.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER));

        StarsBalanceView balanceView = new StarsBalanceView(context, currentAccount);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                bottomSheetParams.transitionFromLeft = true;
                bottomSheetParams.allowNestedScroll = false;
                lastFragment.showAsSheet(new StarsIntroActivity(), bottomSheetParams);
            }
        });
        topView.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 0, -8, 0));

        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 117, Gravity.FILL_HORIZONTAL));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setText(getString(R.string.StarsConfirmPurchaseTitle));
        titleView.setGravity(Gravity.CENTER);
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        subtitleView.setText(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmPurchaseText", (int) stars, purchase, UserObject.getUserName(user))));
        subtitleView.setMaxWidth(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()));
        subtitleView.setGravity(Gravity.CENTER);
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 24));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(replaceStars(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmPurchaseButton", (int) stars))), false);
        linearLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        b.setCustomView(linearLayout);
        BottomSheet sheet = b.create();

        button.setOnClickListener(v -> {
            if (whenConfirmed != null) {
                sheet.setCanDismissWithSwipe(false);
                button.setLoading(true);
                whenConfirmed.run(close -> {
                    if (close) {
                        sheet.dismiss();
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            sheet.setCanDismissWithSwipe(false);
                            button.setLoading(false);
                        }, 400);
                    }
                });
            } else {
                sheet.dismiss();
            }
        });
        sheet.setOnDismissListener(d -> {
            if (whenDismissed != null) {
                whenDismissed.run();
            }
        });

        sheet.fixNavigationBar();
        sheet.show();
        return sheet;
    }

    public static class StarsNeededSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        private final long starsNeeded;
        private final HeaderView headerView;
        private final FrameLayout footerView;
        private final FireworksOverlay fireworksOverlay;
        private Runnable whenPurchased;

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starOptionsLoaded || id == NotificationCenter.starBalanceUpdated) {
                if (adapter != null) {
                    adapter.update(true);
                }
                long balance = StarsController.getInstance(currentAccount).getBalance();
                headerView.titleView.setText(formatPluralStringComma("StarsNeededTitle", (int) (starsNeeded - balance)));
                if (actionBar != null) {
                    actionBar.setTitle(getTitle());
                }
                if (balance >= starsNeeded) {
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
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment instanceof ChatActivity) {
                ChatActivity chatActivity = (ChatActivity) lastFragment;
                if (chatActivity.isKeyboardVisible() && chatActivity.getChatActivityEnterView() != null) {
                    chatActivity.getChatActivityEnterView().closeKeyboard();
                }
            }
            super.show();
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
            long starsNeeded,
            String botName,
            Runnable whenPurchased
        ) {
            super(context, null, false, false, false, resourcesProvider);

            this.whenPurchased = whenPurchased;
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starOptionsLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);

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
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider));

            this.starsNeeded = starsNeeded;
            headerView = new HeaderView(context, currentAccount, resourcesProvider);

            long balance = StarsController.getInstance(currentAccount).getBalance();
            headerView.titleView.setText(formatPluralString("StarsNeededTitle", (int) (starsNeeded - balance)));
            headerView.subtitleView.setText(AndroidUtilities.replaceTags(formatString(R.string.StarsNeededText, botName)));
            headerView.subtitleView.setMaxWidth(HintView2.cutInFancyHalf(headerView.subtitleView.getText(), headerView.subtitleView.getPaint()));
            actionBar.setTitle(getTitle());

            footerView = new FrameLayout(context);
            LinkSpanDrawable.LinksTextView footerTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            footerView.setPadding(0, dp(11), 0, dp(11));
            footerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            footerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
            footerTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            footerTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTOS), () -> {
                Browser.openUrl(getContext(), getString(R.string.StarsTOSLink));
            }));
            footerTextView.setGravity(Gravity.CENTER);
            footerTextView.setMaxWidth(HintView2.cutInFancyHalf(footerTextView.getText(), footerTextView.getPaint()));
            footerView.addView(footerTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            footerView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

            fireworksOverlay = new FireworksOverlay(getContext());
            containerView.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        private final int BUTTON_EXPAND = -1;

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asCustom(headerView));
            items.add(UItem.asHeader(getString(R.string.TelegramStarsChoose)));
            int stars = 1;
            ArrayList<TLRPC.TL_starsTopupOption> options = StarsController.getInstance(currentAccount).getOptions();
            if (options != null && !options.isEmpty()) {
                int count = 0;
                int hidden = 0;
                for (int id = 0; id < options.size(); ++id) {
                    TLRPC.TL_starsTopupOption option = options.get(id);
                    if (option.stars < starsNeeded) {
                        continue;
                    }
                    if (option.collapsed && !expanded) {
                        hidden++;
                        continue;
                    }
                    items.add(StarTierView.Factory.asStarTier(id, stars++, option));
                    count++;
                }
                if (count > 0) {
                    if (!expanded && hidden > 0)
                        items.add(ExpandView.Factory.asExpand(BUTTON_EXPAND, getString(expanded ? R.string.NotifyLessOptions : R.string.NotifyMoreOptions), !expanded).accent());
                } else {
                    for (int id = 0; id < options.size(); ++id) {
                        items.add(StarTierView.Factory.asStarTier(id, stars++, options.get(id)));
                        count++;
                    }
                }
            } else {
                items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
                items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
                items.add(UItem.asFlicker(FlickerLoadingView.STAR_TIER));
            }
            items.add(UItem.asCustom(footerView));
        }

        public void onItemClick(UItem item, UniversalAdapter adapter) {
            if (item.id == BUTTON_EXPAND) {
                expanded = !expanded;
                adapter.update(true);
            } else if (item.instanceOf(StarTierView.Factory.class)) {
                if (item.object instanceof TLRPC.TL_starsTopupOption) {
                    Activity activity = AndroidUtilities.findActivity(getContext());
                    if (activity == null) {
                        activity = LaunchActivity.instance;
                    }
                    if (activity == null) {
                        return;
                    }
                    StarsController.getInstance(currentAccount).buy(activity, (TLRPC.TL_starsTopupOption) item.object, (success, error) -> {
                        if (getContext() == null) return;
                        if (success) {
                            Drawable starDrawable = getContext().getResources().getDrawable(R.drawable.star_small_inner).mutate();
                            BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(starDrawable, getString(R.string.StarsAcquired), AndroidUtilities.replaceTags(formatPluralString("StarsAcquiredInfo", (int) item.longValue))).show();
                            fireworksOverlay.start(true);
                            StarsController.getInstance(currentAccount).invalidateTransactions(true);
                        } else if (error != null) {
                            BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                        }
                    });
                }
            }
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
            public final StarsBalanceView balanceView;
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

                iconView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR);
                iconView.mRenderer.colorKey1 = Theme.key_starsGradient1;
                iconView.mRenderer.colorKey2 = Theme.key_starsGradient2;
                iconView.mRenderer.updateColors();
                iconView.setStarParticlesView(particlesView);
                topView.addView(iconView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
                iconView.setPaused(false);

                balanceView = new StarsBalanceView(context, currentAccount);
                ScaleStateListAnimator.apply(balanceView);
                balanceView.setOnClickListener(v -> {
                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                    if (lastFragment != null) {
                        BaseFragment.BottomSheetParams bottomSheetParams = new BaseFragment.BottomSheetParams();
                        bottomSheetParams.transitionFromLeft = true;
                        bottomSheetParams.allowNestedScroll = false;
                        lastFragment.showAsSheet(new StarsIntroActivity(), bottomSheetParams);
                    }
                });
                topView.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 0, 0, 0));

                addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));

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

    public static SpannableStringBuilder replaceStars(CharSequence cs) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        SpannableString spacedStar = new SpannableString("⭐ ");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_premium_liststar);
        span.setScale(1.13f, 1.13f);
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency + " ", ssb, StarsController.currency);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency, ssb, spacedStar);
        return ssb;
    }


    public static SpannableStringBuilder replaceStarsWithPlain(CharSequence cs, float scale) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        SpannableString spacedStar = new SpannableString("⭐ ");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.star_small_inner);
        span.recolorDrawable = false;
        span.setScale(scale, scale);
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency + " ", ssb, StarsController.currency);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency, ssb, spacedStar);
        return ssb;
    }

    public static CharSequence getTransactionTitle(TLRPC.TL_starsTransaction t) {
        if (t.title != null) {
            return t.title;
        }
        final long did = DialogObject.getPeerDialogId(t.peer.peer);
        if (did != 0) {
            if (did >= 0) {
                return UserObject.getUserName(MessagesController.getInstance(UserConfig.selectedAccount).getUser(did));
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                return chat == null ? "" : chat.title;
            }
        } else if (t.peer instanceof TLRPC.TL_starsTransactionPeerFragment) {
            return getString(R.string.StarsTransactionFragment);
        } else if (t.peer instanceof TLRPC.TL_starsTransactionPeerPremiumBot) {
            return getString(R.string.StarsTransactionBot);
        } else {
            return getString(R.string.StarsTransactionUnsupported);
        }
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount, TLRPC.TL_payments_paymentReceiptStars receipt, Theme.ResourcesProvider resourcesProvider) {
        TLRPC.TL_starsTransaction t = new TLRPC.TL_starsTransaction();
        t.title = receipt.title;
        t.description = receipt.description;
        t.photo = receipt.photo;
        t.peer = new TLRPC.TL_starsTransactionPeer();
        t.peer.peer = MessagesController.getInstance(currentAccount).getPeer(receipt.bot_id);
        t.date = receipt.date;
        t.stars = receipt.total_amount;
        t.id = receipt.transaction_id;
        return showTransactionSheet(context, currentAccount, t, resourcesProvider);
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount, TLRPC.TL_starsTransaction transaction, Theme.ResourcesProvider resourcesProvider) {
        if (transaction == null || context == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(16), dp(16), dp(8));

        BackupImageView imageView = new BackupImageView(context);
        if (transaction.peer instanceof TLRPC.TL_starsTransactionPeer) {
            if (transaction.photo != null) {
                imageView.setRoundRadius(dp(50));
                imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(transaction.photo)), "100_100", null, 0, null);
            } else {
                imageView.setRoundRadius(dp(50));
                final long did = DialogObject.getPeerDialogId(transaction.peer.peer);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                if (did >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                    avatarDrawable.setInfo(user);
                    imageView.setForUserOrChat(user, avatarDrawable);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(did);
                    avatarDrawable.setInfo(chat);
                    imageView.setForUserOrChat(chat, avatarDrawable);
                }
            }
            linearLayout.addView(imageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0, 0, 0, 10));
        } else {
            String platform = "?";
            if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerAppStore) {
                platform = "ios";
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPlayMarket) {
                platform = "android";
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPremiumBot) {
                platform = "premiumbot";
            } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerFragment) {
                platform = "fragment";
            }
            CombinedDrawable drawable = (CombinedDrawable) SessionCell.createDrawable(100, platform);
            drawable.setIconSize(dp(40), dp(40));
            imageView.setImageDrawable(drawable);
            // linearLayout.addView(imageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0, 0, 0, 10));
        }

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(getTransactionTitle(transaction));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(transaction.stars >= 0 ? Theme.key_color_green : Theme.key_color_red, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(replaceStarsWithPlain((transaction.stars >= 0 ? "+" : "-") + LocaleController.formatNumber((int) Math.abs(transaction.stars), ' ') + " ⭐️", .8f));
        if (transaction.refund) {
            SpannableStringBuilder s = new SpannableStringBuilder(textView.getText());
            s.append(" ");
            SpannableString refund = new SpannableString(getString(R.string.StarsRefunded));
            final int color = textView.getCurrentTextColor();
            refund.setSpan(new ReplacementSpan() {
                private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                { backgroundPaint.setColor(Theme.multAlpha(color, .10f)); }
                private final Text layout = new Text(getString(R.string.StarsRefunded), 13, AndroidUtilities.bold());

                @Override
                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                    return (int) (dp(6 + 6) + layout.getCurrentWidth());
                }

                @Override
                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                    AndroidUtilities.rectTmp.set(x, (top + bottom - dp(20)) / 2f, x + dp(6 + 6) + layout.getCurrentWidth(), (top + bottom + dp(20)) / 2f);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), backgroundPaint);
                    layout.draw(canvas, x + dp(6), (top + bottom) / 2f, color, 1f);
                }
            }, 0, refund.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append(refund);
            textView.setText(s);
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        if (transaction.description != null) {
            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setGravity(Gravity.CENTER);
            textView.setText(transaction.description);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));
        }

        TableView tableView = new TableView(context, resourcesProvider);
        if (transaction.peer instanceof TLRPC.TL_starsTransactionPeer) {
            final long did = DialogObject.getPeerDialogId(transaction.peer.peer);
            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
            AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
            CharSequence username;
            if (did >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                username = UserObject.getUserName(user);
                avatarSpan.setUser(user);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                username = chat == null ? "" : chat.title;
                avatarSpan.setChat(chat);
            }
            SpannableStringBuilder ssb = new SpannableStringBuilder("x  " + username);
            ssb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    sheet[0].dismiss();
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        lastFragment.presentFragment(ChatActivity.of(did));
                    }
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, 3, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(ssb);
            tableView.addRowUnpadded(getString(R.string.StarsTransactionRecipient), textView);
        } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerFragment) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.Fragment));
        } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerAppStore) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.AppStore));
        } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPlayMarket) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.PlayMarket));
        } else if (transaction.peer instanceof TLRPC.TL_starsTransactionPeerPremiumBot) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.StarsTransactionBot));
        }

        if (!TextUtils.isEmpty(transaction.id)) {
            FrameLayout idLayout = new FrameLayout(context);
            idLayout.setPadding(dp(12.66f), dp(9.33f), dp(10.66f), dp(9.33f));
            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setMaxLines(4);
            textView.setSingleLine(false);
            textView.setText(transaction.id);
            idLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 32, 0));
            ImageView copyView = new ImageView(context);
            copyView.setImageResource(R.drawable.msg_copy);
            copyView.setScaleType(ImageView.ScaleType.CENTER);
            copyView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            copyView.setOnClickListener(v -> {
                AndroidUtilities.addToClipboard(transaction.id);
                BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.copy, getString(R.string.StarsTransactionIDCopied)).show(false);
            });
            ScaleStateListAnimator.apply(copyView);
            copyView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon, resourcesProvider), .10f), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
            idLayout.addView(copyView, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

            tableView.addRowUnpadded(getString(R.string.StarsTransactionID), idLayout);
        }

        tableView.addRow(getString(R.string.StarsTransactionDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().formatterGiveawayCard.format(new Date(transaction.date * 1000L)), LocaleController.getInstance().formatterDay.format(new Date(transaction.date * 1000L))));
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTransactionTOS), () -> {
            Browser.openUrl(context, getString(R.string.StarsTOSLink));
        }));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 15, 0, 15));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        b.setCustomView(linearLayout);
        sheet[0] = b.create();

        button.setOnClickListener(v -> {
            sheet[0].dismiss();
        });

        sheet[0].fixNavigationBar();
        sheet[0].show();
        return sheet[0];
    }

}

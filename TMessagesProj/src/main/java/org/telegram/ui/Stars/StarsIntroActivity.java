package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatPluralStringSpaced;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ChatEditActivity.applyNewSpan;
import static org.telegram.ui.Stars.StarsIntroActivity.StarsTransactionView.getPlatformDrawable;
import static org.telegram.ui.bots.AffiliateProgramFragment.percents;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
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
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
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
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.StarAppsSheet;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.GradientHeaderActivity;
import org.telegram.ui.ImageReceiverSpan;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.bots.AffiliateProgramFragment;
import org.telegram.ui.bots.ChannelAffiliateProgramsFragment;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class StarsIntroActivity extends GradientHeaderActivity implements NotificationCenter.NotificationCenterDelegate {

    private StarsBalanceView balanceView;

    private FrameLayout aboveTitleView;
    private GLIconTextureView iconTextureView;

    private StarsTransactionsLayout transactionsLayout;
    private View emptyLayout;
    private FireworksOverlay fireworksOverlay;

    private LinearLayout balanceLayout;
    private LinearLayout starBalanceLayout;
    private SpannableStringBuilder starBalanceIcon;
    private AnimatedTextView starBalanceTextView;
    private TextView starBalanceTitleView;
    private ButtonWithCounterView buyButton;
    private ButtonWithCounterView giftButton;

    public StarsIntroActivity() {
        setWhiteBackground(true);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starSubscriptionsLoaded);
        StarsController.getInstance(currentAccount).invalidateTransactions(true);
        StarsController.getInstance(currentAccount).invalidateSubscriptions(true);
        StarsController.getInstance(currentAccount).getOptions();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starOptionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starSubscriptionsLoaded);
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
            final StarsController c = StarsController.getInstance(currentAccount);
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
        transactionsLayout = new StarsTransactionsLayout(context, currentAccount, 0, getClassGuid(), getResourceProvider());
        emptyLayout = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int firstViewHeight;
                if (StarsIntroActivity.this.isLandscapeMode) {
                    firstViewHeight = StarsIntroActivity.this.statusBarHeight + actionBar.getMeasuredHeight() - AndroidUtilities.dp(16);
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
        emptyLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));

        super.createView(context);

//        balanceView = new StarsBalanceView(context, currentAccount);
//        actionBar.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM));

//        yOffset = dp(16);
        aboveTitleView = new FrameLayout(context);
        aboveTitleView.setClickable(true);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR);
        iconTextureView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconTextureView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconTextureView.mRenderer.updateColors();
        iconTextureView.setStarParticlesView(particlesView);
        aboveTitleView.addView(iconTextureView, LayoutHelper.createFrame(190, 190, Gravity.CENTER, 0, 32, 0, 24));
        configureHeader(getString(R.string.TelegramStars), AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.TelegramStarsInfo2), () -> {
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

        final StarsController s = StarsController.getInstance(currentAccount);

        balanceLayout = new LinearLayout(getContext());
        balanceLayout.setOrientation(LinearLayout.VERTICAL);
        balanceLayout.setPadding(0, 0, 0, dp(10));

        starBalanceTextView = new AnimatedTextView(getContext(), false, true, false);
        starBalanceTextView.setTypeface(AndroidUtilities.bold());
        starBalanceTextView.setTextSize(dp(32));
        starBalanceTextView.setGravity(Gravity.CENTER);
        starBalanceTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));

        starBalanceIcon = new SpannableStringBuilder("S");
        final ImageReceiverSpan starBalanceIconSpan = new ImageReceiverSpan(starBalanceTextView, currentAccount, 42);
        starBalanceIconSpan.imageReceiver.setImageBitmap(new RLottieDrawable(R.raw.star_reaction, "s" + R.raw.star_reaction, dp(42), dp(42)));
        starBalanceIconSpan.imageReceiver.setAutoRepeat(2);
        starBalanceIconSpan.enableShadow(false);
        starBalanceIconSpan.translate(-dp(3), 0);
        starBalanceIcon.setSpan(starBalanceIconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        balanceLayout.addView(starBalanceTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.CENTER, 24, 0, 24, 0));

        starBalanceTitleView = new TextView(getContext());
        starBalanceTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        starBalanceTitleView.setGravity(Gravity.CENTER);
        starBalanceTitleView.setText(LocaleController.getString(R.string.YourStarsBalance));
        starBalanceTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        balanceLayout.addView(starBalanceTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));

        buyButton = new ButtonWithCounterView(getContext(), resourceProvider);
        buyButton.setOnClickListener(v -> {
            new StarsOptionsSheet(context, resourceProvider).show();
        });
        balanceLayout.addView(buyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 20, 17, 20, 0));

        giftButton = new ButtonWithCounterView(getContext(), false, resourceProvider);
        SpannableStringBuilder sb2 = new SpannableStringBuilder();
        sb2.append("G  ");
        sb2.setSpan(new ColoredImageSpan(R.drawable.menu_stars_gift), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb2.append(LocaleController.getString(R.string.TelegramStarsGift));
        giftButton.setText(sb2, false);
        giftButton.setOnClickListener(v -> {
            StarsController.getInstance(currentAccount).getGiftOptions();
            UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STARS, 0, BirthdayController.getInstance(currentAccount).getState());
        });
        balanceLayout.addView(giftButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 20, 8, 20, 0));

        updateBalance();

        if (adapter != null) {
            adapter.update(false);
        }

        return fragmentView;
    }

    private void updateBalance() {
        final StarsController s = StarsController.getInstance(currentAccount);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(starBalanceIcon);
        sb.append(formatStarsAmount(s.getBalance(), 0.66f, ' '));
        starBalanceTextView.setText(sb);

        buyButton.setText(LocaleController.getString(s.getBalance().amount > 0 ? R.string.StarsBuyMore : R.string.StarsBuy), true);
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

        final StarsController c = StarsController.getInstance(currentAccount);

        items.add(UItem.asFullyCustom(getHeader(getContext())));
        items.add(UItem.asCustom(balanceLayout));

        if (giftButton != null) {
            giftButton.setVisibility(getMessagesController().starsGiftsEnabled ? View.VISIBLE : View.GONE);
        }

        items.add(UItem.asShadow(null));

        if (getMessagesController().starrefConnectAllowed) {
            items.add(AffiliateProgramFragment.ColorfulTextCell.Factory.as(BUTTON_AFFILIATE, getThemedColor(Theme.key_color_green), R.drawable.filled_earn_stars, applyNewSpan(getString(R.string.UserAffiliateProgramRowTitle)), getString(R.string.UserAffiliateProgramRowText)));
            items.add(UItem.asShadow(null));
        }

        if (c.hasSubscriptions()) {
            items.add(UItem.asHeader(getString(R.string.StarMySubscriptions)));
            for (int i = 0; i < c.subscriptions.size(); ++i) {
                items.add(StarsSubscriptionView.Factory.asSubscription(c.subscriptions.get(i)));
            }
            if (c.isLoadingSubscriptions()) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.STAR_SUBSCRIPTION));
            } else if (!c.didFullyLoadSubscriptions()) {
                items.add(UItem.asButton(BUTTON_SUBSCRIPTIONS_EXPAND, R.drawable.arrow_more, getString(R.string.StarMySubscriptionsExpand)).accent());
            }
            items.add(UItem.asShadow(null));
        }

        if (hadTransactions = c.hasTransactions()) {
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
            StarsController.getInstance(currentAccount).getGiftOptions();
            UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STARS, 0, BirthdayController.getInstance(currentAccount).getState());
        } else if (item.id == BUTTON_SUBSCRIPTIONS_EXPAND) {
            StarsController.getInstance(currentAccount).loadSubscriptions();
            adapter.update(true);
        } else if (item.id == BUTTON_AFFILIATE) {
            presentFragment(new ChannelAffiliateProgramsFragment(getUserConfig().getClientUserId()));
        } else if (item.instanceOf(StarTierView.Factory.class)) {
            if (item.object instanceof TL_stars.TL_starsTopupOption) {
                StarsController.getInstance(currentAccount).buy(getParentActivity(), (TL_stars.TL_starsTopupOption) item.object, (success, error) -> {
                    if (getContext() == null) return;
                    if (success) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.stars_topup, getString(R.string.StarsAcquired), AndroidUtilities.replaceTags(formatPluralString("StarsAcquiredInfo", (int) item.longValue))).show();
                        fireworksOverlay.start(true);
                        StarsController.getInstance(currentAccount).invalidateTransactions(true);
                    } else if (error != null) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                    }
                });
            }
        } else if (item.instanceOf(StarsSubscriptionView.Factory.class)) {
            if (item.object instanceof TL_stars.StarsSubscription) {
                showSubscriptionSheet(getContext(), currentAccount, (TL_stars.StarsSubscription) item.object, getResourceProvider());
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
            long balance = StarsController.getInstance(currentAccount).getBalance().amount;
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
            static { setup(new Factory()); }

            @Override
            public StarTierView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new StarTierView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((StarTierView) view).set(item.intValue, item.text, item.subtext, divider);
            }

            public static UItem asStarTier(int id, int index, TL_stars.TL_starsTopupOption option) {
                UItem item = UItem.ofFactory(StarTierView.Factory.class);
                item.id = id;
                item.intValue = index;
                item.longValue = option.stars;
                item.text = formatPluralStringSpaced("StarsCount", (int) option.stars);
                item.subtext = option.loadingStorePrice ? null : BillingController.getInstance().formatCurrency(option.amount, option.currency);
                item.object = option;
                return item;
            }

            public static UItem asStarTier(int id, int index, TL_stars.TL_starsGiftOption option) {
                UItem item = UItem.ofFactory(StarTierView.Factory.class);
                item.id = id;
                item.intValue = index;
                item.longValue = option.stars;
                item.text = formatPluralStringSpaced("StarsCount", (int) option.stars);
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

        public void set(String text, boolean collapsed, boolean accent, boolean divider) {
            final boolean animated = lastId == -1;
            lastId = -1;
            textView.setText(text, animated);
            final int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2);
            textView.setTextColor(color);
            arrowView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            if (animated) {
                arrowView.animate().rotation(collapsed ? 0 : 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            } else {
                arrowView.setRotation(collapsed ? 0 : 180);
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
            static { setup(new Factory()); }

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
        private final long bot_id;

        private static class PageAdapter extends ViewPagerFixed.Adapter {

            private final Context context;
            private final int currentAccount;
            private final int classGuid;
            private final Theme.ResourcesProvider resourcesProvider;
            private final long bot_id;

            public PageAdapter(Context context, int currentAccount, long bot_id, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                this.context = context;
                this.currentAccount = currentAccount;
                this.classGuid = classGuid;
                this.resourcesProvider = resourcesProvider;
                this.bot_id = bot_id;
                fill();
            }

            private final ArrayList<UItem> items = new ArrayList<>();

            public void fill() {
                items.clear();
                if (bot_id == 0) {
                    StarsController s = StarsController.getInstance(currentAccount);
                    items.add(UItem.asSpace(StarsController.ALL_TRANSACTIONS));
                    if (s.hasTransactions(StarsController.INCOMING_TRANSACTIONS)) {
                        items.add(UItem.asSpace(StarsController.INCOMING_TRANSACTIONS));
                    }
                    if (s.hasTransactions(StarsController.OUTGOING_TRANSACTIONS)) {
                        items.add(UItem.asSpace(StarsController.OUTGOING_TRANSACTIONS));
                    }
                } else {
                    BotStarsController s = BotStarsController.getInstance(currentAccount);
                    items.add(UItem.asSpace(StarsController.ALL_TRANSACTIONS));
                    if (s.hasTransactions(bot_id, BotStarsController.INCOMING_TRANSACTIONS)) {
                        items.add(UItem.asSpace(BotStarsController.INCOMING_TRANSACTIONS));
                    }
                    if (s.hasTransactions(bot_id, BotStarsController.OUTGOING_TRANSACTIONS)) {
                        items.add(UItem.asSpace(BotStarsController.OUTGOING_TRANSACTIONS));
                    }
                }
            }

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public View createView(int viewType) {
                return new Page(context, bot_id, viewType, currentAccount, classGuid, resourcesProvider);
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

        public StarsTransactionsLayout(Context context, int currentAccount, long bot_id, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.bot_id = bot_id;

            setOrientation(VERTICAL);

            viewPager = new ViewPagerFixed(context);
            viewPager.setAdapter(adapter = new PageAdapter(context, currentAccount, bot_id, classGuid, resourcesProvider));
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
            private final long bot_id;

            public Page(Context context, long bot_id, int type, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                this.type = type;
                this.currentAccount = currentAccount;
                this.bot_id = bot_id;
                this.resourcesProvider = resourcesProvider;

                loadTransactionsRunnable = () -> {
                    if (bot_id != 0) {
                        BotStarsController.getInstance(currentAccount).loadTransactions(bot_id, type);
                    } else {
                        StarsController.getInstance(currentAccount).loadTransactions(type);
                    }
                };

                listView = new UniversalRecyclerView(context, currentAccount, classGuid, true, this::fillItems, this::onClick, null, resourcesProvider);
                addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        if (!Page.this.listView.canScrollVertically(1) || isLoadingVisible()) {
                            loadTransactionsRunnable.run();
                        }
                    }
                });
            }

            private final Runnable loadTransactionsRunnable;

            public boolean isLoadingVisible() {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    if (listView.getChildAt(i) instanceof FlickerLoadingView)
                        return true;
                }
                return false;
            }

            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.starTransactionsLoaded) {
                    listView.adapter.update(true);
                    if (!Page.this.listView.canScrollVertically(1) || isLoadingVisible()) {
                        loadTransactionsRunnable.run();
                    }
                } else if (id == NotificationCenter.botStarsTransactionsLoaded) {
                    if ((long) args[0] == bot_id) {
                        listView.adapter.update(true);
                    }
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (bot_id != 0) {
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsTransactionsLoaded);
                } else {
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starTransactionsLoaded);
                }
                listView.adapter.update(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                if (bot_id != 0) {
                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsTransactionsLoaded);
                } else {
                    NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starTransactionsLoaded);
                }
            }

            private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
                if (bot_id != 0) {
                    final BotStarsController c = BotStarsController.getInstance(currentAccount);
                    for (TL_stars.StarsTransaction t : c.getTransactions(bot_id, type)) {
                        items.add(StarsTransactionView.Factory.asTransaction(t, true));
                    }
                    if (!c.didFullyLoadTransactions(bot_id, type)) {
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    }
                } else {
                    final StarsController c = StarsController.getInstance(currentAccount);
                    for (TL_stars.StarsTransaction t : c.transactions[type]) {
                        items.add(StarsTransactionView.Factory.asTransaction(t, false));
                    }
                    if (!c.didFullyLoadTransactions(type)) {
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                        items.add(UItem.asFlicker(items.size(), FlickerLoadingView.DIALOG_CELL_TYPE));
                    }
                }
            }

            private void onClick(UItem item, View view, int position, float x, float y) {
                if (item.object instanceof TL_stars.StarsTransaction) {
                    showTransactionSheet(getContext(), false, 0, currentAccount, (TL_stars.StarsTransaction) item.object, resourcesProvider);
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
        private final FrameLayout imageViewContainer;
        private final BackupImageView imageView;
        private final BackupImageView imageView2;
        private int imageViewCount = 1;
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

            imageViewContainer = new FrameLayout(context) {
                private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    if (imageViewCount > 1) {
                        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                        AndroidUtilities.rectTmp.set(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
                        AndroidUtilities.rectTmp.inset(-dp(1.66f), -dp(1.66f));
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(13), dp(13), backgroundPaint);
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }
            };
            addView(imageViewContainer, LayoutHelper.createLinear(72, LayoutHelper.MATCH_PARENT, 0, Gravity.LEFT | Gravity.FILL_VERTICAL));

            imageView2 = new BackupImageView(context);
            imageView2.setRoundRadius(dp(46));
            imageViewContainer.addView(imageView2, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL, 13, 0, 13, 0));

            avatarDrawable = new AvatarDrawable();
            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(46));
            imageViewContainer.addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.CENTER_VERTICAL, 13, 0, 13, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);
            textLayout.setGravity(Gravity.CENTER | Gravity.LEFT);
            addView(textLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 1, Gravity.FILL));

            titleTextView = new TextView(context);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
            titleTextView.setSingleLine(true);
            textLayout.addView(titleTextView, titleTextViewParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4.33f));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleTextView.setSingleLine(true);
            textLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, .33f));

            dateTextView = new TextView(context);
            dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            dateTextView.setEllipsize(TextUtils.TruncateAt.END);
            dateTextView.setSingleLine(true);
            textLayout.addView(dateTextView, dateTextViewParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            amountTextView = new TextView(context);
            amountTextView.setTypeface(AndroidUtilities.bold());
            amountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.3f);
            amountTextView.setGravity(Gravity.RIGHT);
            addView(amountTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 8, 0, 20, 0));

            star = new SpannableString("⭐️");
            Drawable drawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
            drawable.setBounds(0, 0, dp(21), dp(21));
            star.setSpan(new ImageSpan(drawable), 0, star.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        public static HashMap<String, CombinedDrawable> cachedPlatformDrawables;
        public static CombinedDrawable getPlatformDrawable(String platform) {
            return getPlatformDrawable(platform, 44);
        }
        public static CombinedDrawable getPlatformDrawable(String platform, int sz) {
            if (sz != 44) return SessionCell.createDrawable(sz, platform);
            if (cachedPlatformDrawables == null) {
                cachedPlatformDrawables = new HashMap<>();
            }
            CombinedDrawable drawable = cachedPlatformDrawables.get(platform);
            if (drawable == null) {
                cachedPlatformDrawables.put(platform, drawable = SessionCell.createDrawable(44, platform));
            }
            return drawable;
        }

        private Runnable cancelCurrentGift;
        private boolean needDivider;
        public void set(TL_stars.StarsTransaction transaction, boolean bot, boolean divider) {
            long did = DialogObject.getPeerDialogId(transaction.peer.peer);

            final boolean affiliate_to_bot = (transaction.flags & 131072) != 0;
            final boolean affiliate_to_channel = !affiliate_to_bot && (transaction.flags & 65536) != 0;
            threeLines = did != 0 || transaction.subscription || transaction.floodskip || transaction.stargift != null || transaction.gift && transaction.peer instanceof TL_stars.TL_starsTransactionPeerFragment;
            titleTextViewParams.bottomMargin = threeLines ? 0 : dp(4.33f);
            subtitleTextView.setVisibility(threeLines ? View.VISIBLE : View.GONE);
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, threeLines ? 13 : 14);

            dateTextView.setText(LocaleController.formatShortDateTime(transaction.date));
            if (transaction.refund) {
                dateTextView.setText(TextUtils.concat(dateTextView.getText(), " — ", getString(R.string.StarsRefunded)));
            } else if (transaction.failed) {
                dateTextView.setText(TextUtils.concat(dateTextView.getText(), " — ", getString(R.string.StarsFailed)));
            } else if (transaction.pending) {
                dateTextView.setText(TextUtils.concat(dateTextView.getText(), " — ", getString(R.string.StarsPending)));
            }

            if (cancelCurrentGift != null) {
                cancelCurrentGift.run();
                cancelCurrentGift = null;
            }

            imageView.setTranslationX(0);
            imageView.setTranslationY(0);
            imageView2.setVisibility(GONE);
            imageView.setRoundRadius(dp(46));
            if (did != 0) {
                boolean deleted = false;
                String username;
                if (UserObject.isService(did)) {
                    username = getString(R.string.StarsTransactionUnknown);
                    imageView.setImageDrawable(getPlatformDrawable("fragment"));
                } else if (did >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                    deleted = user == null;
                    avatarDrawable.setInfo(user);
                    imageView.setForUserOrChat(user, avatarDrawable);
                    username = UserObject.getUserName(user);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    deleted = chat == null;
                    avatarDrawable.setInfo(chat);
                    imageView.setForUserOrChat(chat, avatarDrawable);
                    username = chat == null ? "" : chat.title;
                }
                if (transaction.stargift != null) {
                    ImageReceiverSpan span = new ImageReceiverSpan(subtitleTextView, currentAccount, 16);
                    span.setRoundRadius(4);
                    span.enableShadow(false);
                    SpannableString spanString = new SpannableString("x");
                    spanString.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    setGiftImage(span.imageReceiver, transaction.stargift, 16);
                    titleTextView.setText(username);
                    if (transaction.refund) {
                        subtitleTextView.setText(TextUtils.concat(spanString, " ", LocaleController.getString(transaction.stars.amount > 0 ? R.string.Gift2TransactionRefundedSent : R.string.Gift2TransactionRefundedConverted)));
                    } else {
                        subtitleTextView.setText(TextUtils.concat(spanString, " ", LocaleController.getString(transaction.stars.amount > 0 ? R.string.Gift2TransactionConverted : R.string.Gift2TransactionSent)));
                    }
                } else if (transaction.subscription) {
                    titleTextView.setText(username);
                    if (transaction.subscription_period == StarsController.PERIOD_MONTHLY) {
                        subtitleTextView.setVisibility(VISIBLE);
                        subtitleTextView.setText(getString(R.string.StarsTransactionSubscriptionMonthly));
                    } else {
                        final String period = transaction.subscription_period == StarsController.PERIOD_5MINUTES ? "5 minutes" : "Minute";
                        subtitleTextView.setVisibility(VISIBLE);
                        subtitleTextView.setText(String.format(Locale.US, "%s subscription fee", period));
                    }
                } else if (affiliate_to_channel) {
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(LocaleController.formatString(R.string.StarTransactionCommission, percents(transaction.starref_commission_permille)));
                } else if (transaction.gift) {
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(LocaleController.getString(R.string.StarsGiftReceived));
                } else if ((transaction.flags & 8192) != 0) {
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(LocaleController.getString(R.string.StarsGiveawayPrizeReceived));
                } else if (transaction.reaction) {
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(LocaleController.getString(R.string.StarsReactionsSent));
                } else if (!transaction.extended_media.isEmpty()) {
                    if (bot) {
                        titleTextView.setText(username);
                        subtitleTextView.setVisibility(VISIBLE);
                        subtitleTextView.setText(LocaleController.getString(R.string.StarMediaPurchase));
                    } else {
                        titleTextView.setText(LocaleController.getString(R.string.StarMediaPurchase));
                        subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                        subtitleTextView.setText(username);
                    }
                    imageViewCount = 0;
                    for (int i = 0; i < Math.min(2, transaction.extended_media.size()); ++i) {
                        TLRPC.MessageMedia media = transaction.extended_media.get(i);
                        BackupImageView imageView = i == 0 ? this.imageView : this.imageView2;

                        imageView.setRoundRadius(dp(12));
                        ImageLocation location = null;
                        if (media instanceof TLRPC.TL_messageMediaPhoto) {
                            location = ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(46), true), media.photo);
                        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
                            location = ImageLocation.getForDocument(FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(46), true), media.document);
                        }
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImage(location, "46_46", null, null, null, 0);
                        imageViewCount++;
                    }
                    for (int i = 0; i < imageViewCount; ++i) {
                        BackupImageView imageView = i == 0 ? this.imageView : this.imageView2;
                        imageView.setTranslationX(dp(2) + (i - imageViewCount / 2f) * dp(4.33f));
                        imageView.setTranslationY((i - imageViewCount / 2f) * dp(4.33f));
                    }
                } else if (transaction.photo != null) {
                    ImageReceiverSpan span = new ImageReceiverSpan(subtitleTextView, currentAccount, 14);
                    span.setRoundRadius(4);
                    span.enableShadow(false);
                    SpannableString spanString = new SpannableString("x");
                    spanString.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    span.imageReceiver.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(transaction.photo)), "14_14", null, null, 0, 0);
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(Emoji.replaceEmoji(TextUtils.concat(spanString, " ", transaction.title != null ? transaction.title : ""), subtitleTextView.getPaint().getFontMetricsInt(), false));
                } else {
                    titleTextView.setText(username);
                    subtitleTextView.setVisibility(deleted ? GONE : VISIBLE);
                    subtitleTextView.setText(Emoji.replaceEmoji(transaction.title != null ? transaction.title : "", subtitleTextView.getPaint().getFontMetricsInt(), false));
                }
            } else if (transaction.floodskip) {
                titleTextView.setText(getString(R.string.StarsTransactionFloodskip));
                subtitleTextView.setText(LocaleController.formatPluralStringComma("StarsTransactionFloodskipMessages", transaction.floodskip_number));
                imageView.setImageDrawable(getPlatformDrawable("api"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAppStore) {
                titleTextView.setText(getString(R.string.StarsTransactionInApp));
                imageView.setImageDrawable(getPlatformDrawable("ios"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPlayMarket) {
                titleTextView.setText(getString(R.string.StarsTransactionInApp));
                imageView.setImageDrawable(getPlatformDrawable("android"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerFragment) {
                if (transaction.gift) {
                    titleTextView.setText(LocaleController.getString(R.string.StarsGiftReceived));
                    subtitleTextView.setText(getString(R.string.StarsTransactionUnknown));
                    subtitleTextView.setVisibility(VISIBLE);
                } else {
                    titleTextView.setText(getString(bot ? R.string.StarsTransactionWithdrawFragment : R.string.StarsTransactionFragment));
                }
                imageView.setImageDrawable(getPlatformDrawable("fragment"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPremiumBot) {
                titleTextView.setText(getString(R.string.StarsTransactionBot));
                imageView.setImageDrawable(getPlatformDrawable("premiumbot"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerUnsupported) {
                titleTextView.setText(getString(R.string.StarsTransactionUnsupported));
                imageView.setImageDrawable(getPlatformDrawable("?"));
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAds) {
                titleTextView.setText(getString(R.string.StarsTransactionAds));
                imageView.setImageDrawable(getPlatformDrawable("ads"));
            } else {
                titleTextView.setText("");
                imageView.setImageDrawable(null);
            }

            if (transaction.stars.amount > 0) {
                amountTextView.setVisibility(View.VISIBLE);
                amountTextView.setTextColor(Theme.getColor(Theme.key_color_green));
                amountTextView.setText(TextUtils.concat("+", formatStarsAmount(transaction.stars), " ", star));
            } else if (transaction.stars.amount < 0) {
                amountTextView.setVisibility(View.VISIBLE);
                amountTextView.setTextColor(Theme.getColor(Theme.key_color_red));
                amountTextView.setText(TextUtils.concat(formatStarsAmount(transaction.stars), " ", star));
            } else {
                amountTextView.setVisibility(View.GONE);
            }

            setWillNotDraw(!(needDivider = divider));
        }

        public void setLoading() {

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
            static { setup(new Factory()); }

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
                StarsTransactionView transactionView = (StarsTransactionView) view;
                transactionView.set((TL_stars.StarsTransaction) item.object, item.accent, divider);
            }

            public static UItem asTransaction(TL_stars.StarsTransaction transaction, boolean bot) {
                UItem item = UItem.ofFactory(StarsTransactionView.Factory.class);
                item.object = transaction;
                item.accent = bot;
                return item;
            }

            public static UItem asLoading() {
                UItem item = UItem.ofFactory(StarsTransactionView.Factory.class);
                item.accent = true;
                return item;
            }
        }
    }

    public static class StarsSubscriptionView extends LinearLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        public final BackupImageView imageView;
        public final LinearLayout textLayout;
        public final SimpleTextView titleView;
        public final TextView productView;
        public final TextView subtitleView;
        public final LinearLayout priceLayout;
        public final TextView priceTitleView;
        public final TextView priceSubtitleView;

        private boolean threeLines;
        private boolean needDivider;

        public StarsSubscriptionView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(46));
            addView(imageView, LayoutHelper.createLinear(46, 46, 0, Gravity.CENTER_VERTICAL | Gravity.LEFT, 13, 0, 13, 0));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(VERTICAL);
            addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            titleView = new SimpleTextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTextSize(16);
            titleView.setTypeface(AndroidUtilities.bold());
            NotificationCenter.listenEmojiLoading(titleView);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

            productView = new TextView(context);
            productView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            productView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            productView.setVisibility(View.GONE);
            textLayout.addView(productView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            priceLayout = new LinearLayout(context);
            priceLayout.setOrientation(VERTICAL);
            addView(priceLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 0, 0, 18, 0));

            priceTitleView = new TextView(context);
            priceTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            priceTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            priceTitleView.setTypeface(AndroidUtilities.bold());
            priceTitleView.setGravity(Gravity.RIGHT);
            priceLayout.addView(priceTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 0, 0, 1));

            priceSubtitleView = new TextView(context);
            priceSubtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            priceSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            priceSubtitleView.setGravity(Gravity.RIGHT);
            priceLayout.addView(priceSubtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 0, 0, 0));
        }

        public void set(TL_stars.StarsSubscription subscription, boolean divider) {
            long dialogId = DialogObject.getPeerDialogId(subscription.peer);

            threeLines = !TextUtils.isEmpty(subscription.title);

            String name = "";
            boolean business;
            if (dialogId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(chat);
                imageView.setForUserOrChat(chat, avatarDrawable);
                name = chat != null ? chat.title : null;
                business = false;
            } else {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
                name = UserObject.getUserName(user);
                business = !UserObject.isBot(user);
            }

            final long now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            titleView.setText(Emoji.replaceEmoji(name, titleView.getPaint().getFontMetricsInt(), false));

            if (!TextUtils.isEmpty(subscription.title)) {
                productView.setVisibility(View.VISIBLE);
                SpannableStringBuilder productName = new SpannableStringBuilder();
                if (subscription.photo != null) {
                    ImageReceiverSpan span = new ImageReceiverSpan(productView, currentAccount, 14);
                    span.setRoundRadius(4);
                    span.enableShadow(false);
                    SpannableString spanString = new SpannableString("x");
                    spanString.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    span.imageReceiver.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(subscription.photo)), "14_14", null, null, 0, 0);
                    productName.append(spanString).append(" ");
                }
                productName.append(Emoji.replaceEmoji(subscription.title, titleView.getPaint().getFontMetricsInt(), false));
                productView.setText(productName);
            } else {
                productView.setVisibility(View.GONE);
            }

            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, threeLines ? 13 : 14);
            if (subscription.canceled || subscription.bot_canceled) {
                subtitleView.setText(formatString(subscription.until_date < now ? R.string.StarsSubscriptionExpired : R.string.StarsSubscriptionExpires, LocaleController.formatDateChat(subscription.until_date)));
                priceTitleView.setVisibility(View.GONE);
                priceSubtitleView.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                priceSubtitleView.setText(LocaleController.getString(subscription.bot_canceled ? (business ? R.string.StarsSubscriptionStatusBizCancelled : R.string.StarsSubscriptionStatusBotCancelled) : R.string.StarsSubscriptionStatusCancelled));
            } else if (subscription.until_date < now) {
                subtitleView.setText(formatString(R.string.StarsSubscriptionExpired, LocaleController.formatDateChat(subscription.until_date)));
                priceTitleView.setVisibility(View.GONE);
                priceSubtitleView.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                priceSubtitleView.setText(LocaleController.getString(R.string.StarsSubscriptionStatusExpired));
            } else {
                subtitleView.setText(formatString(R.string.StarsSubscriptionRenews, LocaleController.formatDateChat(subscription.until_date)));
                priceTitleView.setVisibility(View.VISIBLE);
                priceTitleView.setText(replaceStarsWithPlain("⭐️ " + Long.toString(subscription.pricing.amount), .8f));
                priceSubtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                if (subscription.pricing.period == StarsController.PERIOD_MONTHLY) {
                    priceSubtitleView.setText(getString(R.string.StarsParticipantSubscriptionPerMonth));
                } else if (subscription.pricing.period == StarsController.PERIOD_MINUTE) {
                    priceSubtitleView.setText("per minute");
                } else if (subscription.pricing.period == StarsController.PERIOD_5MINUTES) {
                    priceSubtitleView.setText("per 5 minutes");
                }
            }

            setWillNotDraw(!(needDivider = divider));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(threeLines ? 68 : 58), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(dp(72), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        public static class Factory extends UItem.UItemFactory<StarsSubscriptionView> {
            static { setup(new Factory()); }

            @Override
            public StarsSubscriptionView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                StarsSubscriptionView cached = getCached();
                if (cached != null) {
                    return cached;
                }
                return new StarsSubscriptionView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                StarsSubscriptionView subscriptionView = (StarsSubscriptionView) view;
                subscriptionView.set((TL_stars.StarsSubscription) item.object, divider);
            }

            public static UItem asSubscription(TL_stars.StarsSubscription subscription) {
                UItem item = UItem.ofFactory(StarsSubscriptionView.Factory.class);
                item.object = subscription;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                if (a == null && b == null) return true;
                if (a == null || b == null) return false;
                if (!(a.object instanceof TL_stars.StarsSubscription) || !(b.object instanceof TL_stars.StarsSubscription)) return false;
                TL_stars.StarsSubscription subA = (TL_stars.StarsSubscription) a.object;
                TL_stars.StarsSubscription subB = (TL_stars.StarsSubscription) b.object;
                return TextUtils.equals(subA.id, subB.id);
            }
        }
    }

    public static BottomSheet openConfirmPurchaseSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        int currentAccount,
        MessageObject messageObject,
        long userId,
        String purchase,
        long stars,
        TLRPC.WebDocument photo,
        int subscription_period,
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

        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
            BackupImageView imageView = new BackupImageView(context) {
                private SpoilerEffect2 spoilerEffect2;
                private Path clipPath = new Path();
                private RectF clipRect = new RectF();
                private Drawable lock = context.getResources().getDrawable(R.drawable.large_locked_post).mutate();
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (spoilerEffect2 == null) {
                        spoilerEffect2 = SpoilerEffect2.getInstance(this);
                    }
                    if (spoilerEffect2 != null) {
                        clipRect.set(0,0,getWidth(),getHeight());
                        clipPath.rewind();
                        clipPath.addRoundRect(clipRect, dp(24), dp(24), Path.Direction.CW);
                        canvas.save();
                        canvas.clipPath(clipPath);
                        spoilerEffect2.draw(canvas, this, getWidth(), getHeight(), 1f);
                        canvas.restore();
                    }
                    lock.setBounds((getWidth()-lock.getIntrinsicWidth())/2, (getHeight()-lock.getIntrinsicHeight())/2, (getWidth()+lock.getIntrinsicWidth())/2, (getHeight()+lock.getIntrinsicHeight())/2);
                    lock.draw(canvas);
                }
                @Override
                protected void onAttachedToWindow() {
                    if (spoilerEffect2 != null) {
                        spoilerEffect2.attach(this);
                    }
                    super.onAttachedToWindow();
                }
                @Override
                protected void onDetachedFromWindow() {
                    if (spoilerEffect2 != null) {
                        spoilerEffect2.detach(this);
                    }
                    super.onDetachedFromWindow();
                }
            };
            imageView.setRoundRadius(dp(24));
            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) messageObject.messageOwner.media;
            if (!paidMedia.extended_media.isEmpty()) {
                TLRPC.MessageExtendedMedia extMedia = paidMedia.extended_media.get(0);
                ImageLocation location = null;
                if (extMedia instanceof TLRPC.TL_messageExtendedMediaPreview) {
                    TLRPC.TL_messageExtendedMediaPreview m = (TLRPC.TL_messageExtendedMediaPreview) extMedia;
                    location = ImageLocation.getForObject(m.thumb, messageObject.messageOwner);
                } else if (extMedia instanceof TLRPC.TL_messageExtendedMedia) {
                    TLRPC.MessageMedia media = ((TLRPC.TL_messageExtendedMedia) extMedia).media;
                    if (media instanceof TLRPC.TL_messageMediaPhoto) {
                        location = ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(80), true), media.photo);
                    } else if (media instanceof TLRPC.TL_messageMediaDocument) {
                        location = ImageLocation.getForDocument(FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(80), true), media.document);
                    }
                }
                imageView.setImage(location, "80_80_b2", null, null, null, messageObject);
            }
            topView.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER));
        } else if (photo == null) {
            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(80));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);
            topView.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER));
        } else {
            FrameLayout imageViewLayout = new FrameLayout(context);
            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(18));
            imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(photo)), "80_80", null, 0, null);
            imageViewLayout.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.TOP));
            topView.addView(imageViewLayout, LayoutHelper.createFrame(80, 87, Gravity.CENTER));

            TextView priceView = new TextView(context);
            priceView.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            priceView.setTextColor(0xFFFFFFFF);
            priceView.setText(replaceStars("XTR " + LocaleController.formatNumber((int) stars, ','), .85f));
            priceView.setPadding(dp(5.33f), 0, dp(5.33f), 0);
            priceView.setBackground(Theme.createRoundRectDrawable(dp(16), 0xFFEEB402));
            FrameLayout backgroundLayout = new FrameLayout(context);
            backgroundLayout.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.getColor(Theme.key_dialogBackground, resourcesProvider)));
            backgroundLayout.setPadding(dp(1.33f), dp(1.33f), dp(1.33f), dp(1.33f));
            backgroundLayout.addView(priceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 16, Gravity.FILL));
            imageViewLayout.addView(backgroundLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 16+2.66f, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }

        StarsBalanceView balanceView = new StarsBalanceView(context, currentAccount);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            if (balanceView.lastBalance <= 0) return;
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
        if (subscription_period > 0) {
            titleView.setText(Emoji.replaceEmoji(photo != null ? purchase : getString(R.string.StarsConfirmSubscriptionTitle), titleView.getPaint().getFontMetricsInt(), false));
        } else {
            titleView.setText(Emoji.replaceEmoji(photo != null ? purchase : getString(R.string.StarsConfirmPurchaseTitle), titleView.getPaint().getFontMetricsInt(), false));
        }
        NotificationCenter.listenEmojiLoading(titleView);
        titleView.setGravity(Gravity.CENTER);
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, photo != null ? -8 : 8, 0, 0));

        if (photo != null) {
            LinearLayout chipLayout = new LinearLayout(context);
            chipLayout.setOrientation(LinearLayout.HORIZONTAL);
            chipLayout.setBackground(Theme.createRoundRectDrawable(dp(28), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(dp(14));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            imageView.setForUserOrChat(user, avatarDrawable);
            chipLayout.addView(imageView, LayoutHelper.createLinear(28, 28));
            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setText(UserObject.getUserName(user));
            chipLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 10, 0));
            linearLayout.addView(chipLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 2));
        }

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPaidMedia) {
            long did = messageObject.getDialogId();
            if (messageObject.messageOwner != null && messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
                did = DialogObject.getPeerDialogId(messageObject.messageOwner.fwd_from.from_id);
            }
            if (did < 0 && messageObject.getFromChatId() > 0) {
                final TLRPC.User _user = MessagesController.getInstance(currentAccount).getUser(messageObject.getFromChatId());
                if (_user != null && _user.bot) {
                    did = _user.id;
                }
            }
            final String chatTitle;
            final boolean isBot;
            if (did >= 0) {
                final TLRPC.User _user = MessagesController.getInstance(currentAccount).getUser(did);
                chatTitle = UserObject.getUserName(_user);
                isBot = _user != null && _user.bot;
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                chatTitle = chat == null ? "" : chat.title;
                isBot = false;
            }

            int photosCount = 0, videosCount = 0;
            TLRPC.TL_messageMediaPaidMedia paidMedia = (TLRPC.TL_messageMediaPaidMedia) messageObject.messageOwner.media;
            for (int i = 0; i < paidMedia.extended_media.size(); ++i) {
                TLRPC.MessageExtendedMedia extMedia = paidMedia.extended_media.get(i);
                boolean isVideo = false;
                if (extMedia instanceof TLRPC.TL_messageExtendedMediaPreview) {
                    TLRPC.TL_messageExtendedMediaPreview m = (TLRPC.TL_messageExtendedMediaPreview) extMedia;
                    isVideo = (m.flags & 4) != 0;
                } else if (extMedia instanceof TLRPC.TL_messageExtendedMedia) {
                    TLRPC.MessageMedia media = ((TLRPC.TL_messageExtendedMedia) extMedia).media;
                    isVideo = media instanceof TLRPC.TL_messageMediaDocument;
                }
                if (isVideo) videosCount++;
                else photosCount++;
            }

            String c;
            if (videosCount == 0) {
                c = formatPluralString(isBot ? "StarsConfirmPurchaseMediaBotOne2" : "StarsConfirmPurchaseMediaOne2", (int) stars, photosCount == 1 ? getString(R.string.StarsConfirmPurchaseMedia_SinglePhoto) : formatPluralString("StarsConfirmPurchaseMedia_Photos", photosCount), chatTitle);
            } else if (photosCount == 0) {
                c = formatPluralString(isBot ? "StarsConfirmPurchaseMediaBotOne2" : "StarsConfirmPurchaseMediaOne2", (int) stars, videosCount == 1 ? getString(R.string.StarsConfirmPurchaseMedia_SingleVideo) : formatPluralString("StarsConfirmPurchaseMedia_Videos", videosCount), chatTitle);
            } else {
                c = formatPluralString(isBot ? "StarsConfirmPurchaseMediaBotTwo2" : "StarsConfirmPurchaseMediaTwo2", (int) stars, photosCount == 1 ? getString(R.string.StarsConfirmPurchaseMedia_SinglePhoto) : formatPluralString("StarsConfirmPurchaseMedia_Photos", photosCount), videosCount == 1 ? getString(R.string.StarsConfirmPurchaseMedia_SingleVideo) : formatPluralString("StarsConfirmPurchaseMedia_Videos", videosCount), chatTitle);
            }
            subtitleView.setText(AndroidUtilities.replaceTags(c));
        } else {
            if (subscription_period > 0) {
                subtitleView.setText(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmSubscriptionText2", (int) stars, purchase, UserObject.getUserName(user))));
            } else {
                subtitleView.setText(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmPurchaseText2", (int) stars, purchase, UserObject.getUserName(user))));
            }
        }
        subtitleView.setMaxWidth(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()));
        subtitleView.setGravity(Gravity.CENTER);
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 18));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        if (subscription_period > 0) {
            button.setText(replaceStars(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmSubscriptionButton", (int) stars))), false);
        } else {
            button.setText(replaceStars(AndroidUtilities.replaceTags(formatPluralStringComma("StarsConfirmPurchaseButton", (int) stars))), false);
        }
        linearLayout.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        LinkSpanDrawable.LinksTextView footerTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        footerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        footerTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        footerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        footerTextView.setText(AndroidUtilities.replaceSingleTag(getString(subscription_period > 0 ? R.string.StarsConfirmSubscriptionTOS : R.string.StarsConfirmPurchaseTOS), () -> {
            Browser.openUrl(context, getString(R.string.StarsTOSLink));
        }));
        footerTextView.setGravity(Gravity.CENTER);
        linearLayout.addView(footerTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 2));

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

    public static BottomSheet openStarsChannelInviteSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        int currentAccount,
        TLRPC.ChatInvite chatInvite,
        Utilities.Callback<Utilities.Callback<Boolean>> whenConfirmed,
        Runnable whenDismissed
    ) {
        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), 0, dp(16), dp(8));

        FrameLayout topView = new FrameLayout(context);
        topView.addView(makeParticlesView(context, 40, 0), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(80));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setPeerColor(chatInvite.color);
        avatarDrawable.setText(chatInvite.title);
        if (chatInvite.photo != null) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(chatInvite.photo.sizes, dp(80));
            imageView.setImage(ImageLocation.getForPhoto(photoSize, chatInvite.photo), "80_80", avatarDrawable, chatInvite);
        } else {
            imageView.setImageDrawable(avatarDrawable);
        }

        topView.addView(imageView, LayoutHelper.createFrame(80, 80, Gravity.CENTER));

        Drawable starBg = context.getResources().getDrawable(R.drawable.star_small_outline);
        starBg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.SRC_IN));
        Drawable starFg = context.getResources().getDrawable(R.drawable.star_small_inner);

        ImageView starBgView = new ImageView(context);
        starBgView.setImageDrawable(starBg);
        topView.addView(starBgView, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
        starBgView.setTranslationX(dp(26));
        starBgView.setTranslationY(dp(26));
        starBgView.setScaleX(1.2f);
        starBgView.setScaleY(1.2f);

        ImageView starFgView = new ImageView(context);
        starFgView.setImageDrawable(starFg);
        topView.addView(starFgView, LayoutHelper.createFrame(26, 26, Gravity.CENTER));
        starFgView.setTranslationX(dp(26));
        starFgView.setTranslationY(dp(26));

        StarsBalanceView balanceView = new StarsBalanceView(context, currentAccount);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            if (balanceView.lastBalance <= 0) return;
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
        titleView.setText(getString(R.string.StarsSubscribeTitle));
        titleView.setGravity(Gravity.CENTER);
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        if (chatInvite.subscription_pricing.period == StarsController.PERIOD_MONTHLY) {
            subtitleView.setText(AndroidUtilities.replaceTags(formatPluralString("StarsSubscribeText", (int) chatInvite.subscription_pricing.amount, chatInvite.title)));
        } else {
            final String period = chatInvite.subscription_pricing.period == StarsController.PERIOD_5MINUTES ? "5 minutes" : "a minute";
            subtitleView.setText(AndroidUtilities.replaceTags(formatPluralString("StarsSubscribeTextTest", (int) chatInvite.subscription_pricing.amount, chatInvite.title, period)));
        }
        subtitleView.setMaxWidth(HintView2.cutInFancyHalf(subtitleView.getText(), subtitleView.getPaint()));
        subtitleView.setGravity(Gravity.CENTER);
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 22));

        if (!TextUtils.isEmpty(chatInvite.about)) {
            TextView aboutView = new TextView(context);
            aboutView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            aboutView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            aboutView.setText(Emoji.replaceEmoji(chatInvite.about, aboutView.getPaint().getFontMetricsInt(), false));
            aboutView.setGravity(Gravity.CENTER);
            linearLayout.addView(aboutView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 22));
        }

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.StarsSubscribeButton), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        LinkSpanDrawable.LinksTextView infoTextView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        infoTextView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsSubscribeInfo), () -> {
            Browser.openUrl(context, getString(R.string.StarsSubscribeInfoLink));
        }));
        infoTextView.setGravity(Gravity.CENTER);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        infoTextView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 14, 14, 14, 6));

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

        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && lastFragment != null && !AndroidUtilities.hasDialogOnTop(lastFragment)) {
            sheet.makeAttached(lastFragment);
        }

        sheet.show();
        return sheet;
    }

    public static class StarsOptionsSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        private final FrameLayout footerView;
        private final FireworksOverlay fireworksOverlay;

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starOptionsLoaded || id == NotificationCenter.starBalanceUpdated) {
                if (adapter != null) {
                    adapter.update(true);
                }
            }
        }

        @Override
        public void show() {
            long balance = StarsController.getInstance(currentAccount).getBalance().amount;
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

        public StarsOptionsSheet(
            Context context,
            Theme.ResourcesProvider resourcesProvider
        ) {
            super(context, null, false, false, false, resourcesProvider);

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
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

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
            return LocaleController.getString(R.string.StarsBuy);
        }

        private UniversalAdapter adapter;
        @Override
        protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
            adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
            adapter.setApplyBackground(false);
            return adapter;
        }

        private boolean expanded;
        private final int BUTTON_EXPAND = -1;

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(getString(R.string.TelegramStarsChoose)));
            int stars = 1;
            ArrayList<TL_stars.TL_starsTopupOption> options = StarsController.getInstance(currentAccount).getOptions();
            if (options != null && !options.isEmpty()) {
                int hidden = 0;
                for (int id = 0; id < options.size(); ++id) {
                    TL_stars.TL_starsTopupOption option = options.get(id);
                    if (option.extended && !expanded) {
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
            items.add(UItem.asCustom(footerView));
        }

        public void onItemClick(UItem item, UniversalAdapter adapter) {
            if (item.id == BUTTON_EXPAND) {
                expanded = !expanded;
                adapter.update(true);
                recyclerListView.smoothScrollBy(0, dp(300));
            } else if (item.instanceOf(StarTierView.Factory.class)) {
                if (item.object instanceof TL_stars.TL_starsTopupOption) {
                    Activity activity = AndroidUtilities.findActivity(getContext());
                    if (activity == null) {
                        activity = LaunchActivity.instance;
                    }
                    if (activity == null) {
                        return;
                    }
                    StarsController.getInstance(currentAccount).buy(activity, (TL_stars.TL_starsTopupOption) item.object, (success, error) -> {
                        if (getContext() == null) return;
                        dismiss();
                        StarsController.getInstance(currentAccount).invalidateTransactions(true);
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        if (success) {
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.stars_topup, getString(R.string.StarsAcquired), AndroidUtilities.replaceTags(formatPluralString("StarsAcquiredInfo", (int) item.longValue))).show();
                            if (LaunchActivity.instance != null) {
                                LaunchActivity.instance.getFireworksOverlay().start(true);
                            }
                        } else if (error != null) {
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                        }
                    });
                }
            }
        }
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
                long balance = StarsController.getInstance(currentAccount).getBalance().amount;
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
            long balance = StarsController.getInstance(currentAccount).getBalance().amount;
            if (balance >= starsNeeded) {
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

        public static final int TYPE_BOT = 0;
        public static final int TYPE_SUBSCRIPTION_BUY = 1;
        public static final int TYPE_SUBSCRIPTION_KEEP = 2;
        public static final int TYPE_SUBSCRIPTION_REFULFILL = 3;
        public static final int TYPE_LINK = 4;
        public static final int TYPE_REACTIONS = 5;
        public static final int TYPE_STAR_GIFT_BUY = 6;
        public static final int TYPE_BOT_SUBSCRIPTION_KEEP = 7;
        public static final int TYPE_BIZ_SUBSCRIPTION_KEEP = 8;
        public static final int TYPE_BIZ = 9;

        public StarsNeededSheet(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            long starsNeeded,
            int type, String botName,
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
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider));

            this.starsNeeded = starsNeeded;
            headerView = new HeaderView(context, currentAccount, resourcesProvider);

            long balance = StarsController.getInstance(currentAccount).getBalance().amount;
            headerView.titleView.setText(formatPluralString("StarsNeededTitle", (int) Math.max(0, starsNeeded - balance)));
            String stringRes;
            if (type == TYPE_SUBSCRIPTION_BUY) {
                stringRes = "StarsNeededTextBuySubscription";
            } else if (type == TYPE_SUBSCRIPTION_KEEP) {
                stringRes = "StarsNeededTextKeepSubscription";
            } else if (type == TYPE_BOT_SUBSCRIPTION_KEEP) {
                stringRes = "StarsNeededTextKeepBotSubscription";
            } else if (type == TYPE_BIZ_SUBSCRIPTION_KEEP) {
                stringRes = "StarsNeededTextKeepBizSubscription";
            } else if (type == TYPE_SUBSCRIPTION_REFULFILL) {
                stringRes = "StarsNeededTextKeepSubscription";
            } else if (type == TYPE_LINK) {
                stringRes = botName == null ? "StarsNeededTextLink" : "StarsNeededTextLink_" + botName.toLowerCase();
                if (LocaleController.nullable(LocaleController.getString(stringRes)) == null) {
                    stringRes = "StarsNeededTextLink";
                }
            } else if (type == TYPE_REACTIONS) {
                stringRes = "StarsNeededTextReactions";
            } else if (type == TYPE_STAR_GIFT_BUY) {
                stringRes = "StarsNeededTextGift";
            } else if (type == TYPE_BIZ) {
                stringRes = "StarsNeededBizText";
            } else {
                stringRes = "StarsNeededText";
            }
            if (TextUtils.isEmpty(stringRes)) {
                headerView.subtitleView.setText("");
            } else {
                String str = LocaleController.nullable(formatString(stringRes, LocaleController.getStringResId(stringRes), botName));
                if (str == null) {
                    str = getString(stringRes);
                }
                headerView.subtitleView.setText(AndroidUtilities.replaceTags(str));
                headerView.subtitleView.setMaxWidth(HintView2.cutInFancyHalf(headerView.subtitleView.getText(), headerView.subtitleView.getPaint()));
            }
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
            ArrayList<TL_stars.TL_starsTopupOption> options = StarsController.getInstance(currentAccount).getOptions();
            if (options != null && !options.isEmpty()) {
                int count = 0;
                int hidden = 0;
                boolean shownNearest = false;
                for (int id = 0; id < options.size(); ++id) {
                    TL_stars.TL_starsTopupOption option = options.get(id);
                    if (option.stars < starsNeeded) {
                        continue;
                    }
                    if (option.extended && !expanded && shownNearest) {
                        hidden++;
                        continue;
                    }
                    items.add(StarTierView.Factory.asStarTier(id, stars++, option));
                    shownNearest = true;
                    count++;
                }
                if (count < 3) {
                    items.clear();
                    items.add(UItem.asCustom(headerView));
                    items.add(UItem.asHeader(getString(R.string.TelegramStarsChoose)));
                    count = 0;
                    for (int id = 0; id < options.size(); ++id) {
                        TL_stars.TL_starsTopupOption option = options.get(id);
                        if (option.stars < starsNeeded) {
                            continue;
                        }
                        items.add(StarTierView.Factory.asStarTier(id, stars++, option));
                        count++;
                    }
                    if (count == 0) {
                        for (int id = 0; id < options.size(); ++id) {
                            TL_stars.TL_starsTopupOption option = options.get(id);
                            items.add(StarTierView.Factory.asStarTier(id, stars++, option));
                            count++;
                        }
                        if (!expanded)
                            items.add(ExpandView.Factory.asExpand(BUTTON_EXPAND, getString(expanded ? R.string.NotifyLessOptions : R.string.NotifyMoreOptions), !expanded).accent());
                    } else {
                        expanded = true;
                    }
                } else if (count > 0) {
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
                if (item.object instanceof TL_stars.TL_starsTopupOption) {
                    Activity activity = AndroidUtilities.findActivity(getContext());
                    if (activity == null) {
                        activity = LaunchActivity.instance;
                    }
                    if (activity == null) {
                        return;
                    }
                    StarsController.getInstance(currentAccount).buy(activity, (TL_stars.TL_starsTopupOption) item.object, (success, error) -> {
                        if (getContext() == null) return;
                        if (success) {
                            BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(R.raw.stars_topup, getString(R.string.StarsAcquired), AndroidUtilities.replaceTags(formatPluralString("StarsAcquiredInfo", (int) item.longValue))).show();
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
                    if (balanceView.lastBalance <= 0) return;
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

    public static class GiftStarsSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {

        private final HeaderView headerView;
        private final FrameLayout footerView;
        private final FireworksOverlay fireworksOverlay;
        private final TLRPC.User user;
        private final Runnable whenPurchased;

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.starGiftOptionsLoaded || id == NotificationCenter.starBalanceUpdated) {
                if (adapter != null) {
                    adapter.update(true);
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
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftOptionsLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        }

        public GiftStarsSheet(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            TLRPC.User user,
            Runnable whenPurchased
        ) {
            super(context, null, false, false, false, resourcesProvider);

            this.user = user;
            this.whenPurchased = whenPurchased;
            topPadding = .2f;

            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftOptionsLoaded);
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

            headerView = new HeaderView(context, currentAccount, resourcesProvider);

//            long balance = StarsController.getInstance(currentAccount).getBalance();
            headerView.titleView.setText(getString(R.string.GiftStarsTitle));
            headerView.subtitleView.setText(
                TextUtils.concat(
                    AndroidUtilities.replaceTags(formatString(R.string.GiftStarsSubtitle, UserObject.getForcedFirstName(user))),
                    " ",
                    AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.GiftStarsSubtitleLinkName).replace(' ', ' '), () -> {
                        StarAppsSheet sheet = new StarAppsSheet(getContext());
                        if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(attachedFragment) && attachedFragment != null) {
                            sheet.makeAttached(attachedFragment);
                        }
                        sheet.show();
                    }), true)
                )
            );
            headerView.subtitleView.setMaxWidth(HintView2.cutInFancyHalf(headerView.subtitleView.getText(), headerView.subtitleView.getPaint()) + 1);
            actionBar.setTitle(getTitle());

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            headerView.avatarImageView.setForUserOrChat(user, avatarDrawable);

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
            ArrayList<TL_stars.TL_starsGiftOption> options = StarsController.getInstance(currentAccount).getGiftOptions();
            if (options != null && !options.isEmpty()) {
                int hidden = 0;
                for (int id = 0; id < options.size(); ++id) {
                    TL_stars.TL_starsGiftOption option = options.get(id);
                    if (!expanded && option.extended) {
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
            }
            items.add(UItem.asCustom(footerView));
        }

        public void onItemClick(UItem item, UniversalAdapter adapter) {
            if (item.id == BUTTON_EXPAND) {
                expanded = !expanded;
                adapter.update(true);
                recyclerListView.smoothScrollBy(0, dp(200), CubicBezierInterpolator.EASE_OUT);
            } else if (item.instanceOf(StarTierView.Factory.class)) {
                if (item.object instanceof TL_stars.TL_starsGiftOption) {
                    Activity activity = AndroidUtilities.findActivity(getContext());
                    if (activity == null) {
                        activity = LaunchActivity.instance;
                    }
                    if (activity == null) {
                        return;
                    }
                    final long userId = user.id;
                    StarsController.getInstance(currentAccount).buyGift(activity, (TL_stars.TL_starsGiftOption) item.object, userId, (success, error) -> {
                        if (getContext() == null) return;
                        if ((success || error != null) && whenPurchased != null) {
                            whenPurchased.run();
                        }
                        dismiss();
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        FireworksOverlay fireworksOverlay = LaunchActivity.instance.getFireworksOverlay();
                        if (lastFragment == null) return;
                        if (success) {
                            BulletinFactory.of(lastFragment)
                                .createSimpleBulletin(
                                    R.raw.stars_send,
                                    getString(R.string.StarsGiftSentPopup),
                                    AndroidUtilities.replaceTags(formatPluralString("StarsGiftSentPopupInfo", (int) item.longValue, UserObject.getForcedFirstName(user))),
                                    getString(R.string.ViewInChat),
                                    () -> {
                                        BaseFragment lastFragment2 = LaunchActivity.getSafeLastFragment();
                                        if (lastFragment2 != null) {
                                            lastFragment2.presentFragment(ChatActivity.of(userId));
                                        }
                                    }
                                )
                                .setDuration(Bulletin.DURATION_PROLONG)
                                .show(true);
                            if (fireworksOverlay != null) {
                                fireworksOverlay.start(true);
                            }
                            StarsController.getInstance(currentAccount).invalidateTransactions(true);
                        } else if (error != null) {
                            BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, error)).show();
                        }
                    });
                }
            }
        }

        @Override
        public void dismiss() {
            super.dismiss();
//            if (headerView != null) {
//                headerView.iconView.setPaused(true);
//            }
        }

        public static class HeaderView extends LinearLayout {
            private final FrameLayout topView;
            public final StarParticlesView particlesView;
            public final BackupImageView avatarImageView;
            public final TextView titleView;
            public final LinkSpanDrawable.LinksTextView subtitleView;

            public HeaderView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
                super(context);

                setOrientation(VERTICAL);
                topView = new FrameLayout(context);
                topView.setClipChildren(false);
                topView.setClipToPadding(false);

                particlesView = makeParticlesView(context, 70, 0);
                topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                avatarImageView = new BackupImageView(context);
                avatarImageView.setRoundRadius(dp(50));
                topView.addView(avatarImageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 0, 32, 0, 24));

                addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150));

                titleView = new TextView(context);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                titleView.setTypeface(AndroidUtilities.bold());
                titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                titleView.setGravity(Gravity.CENTER);
                addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

                subtitleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                subtitleView.setGravity(Gravity.CENTER);
                addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 18));
            }
        }
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs) {
        return replaceStars(cs, 1.13f);
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, final float scale) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        SpannableString spacedStar = new SpannableString("⭐ ");
        ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_premium_liststar);
        span.setScale(scale, scale);
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency + " ", ssb, StarsController.currency);
        AndroidUtilities.replaceMultipleCharSequence(StarsController.currency, ssb, spacedStar);
        return ssb;
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, ColoredImageSpan[] spanRef) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        ColoredImageSpan span;
        if (spanRef != null && spanRef[0] != null ) {
            span = spanRef[0];
        } else {
            span = new ColoredImageSpan(R.drawable.msg_premium_liststar);
            span.setScale(1.13f, 1.13f);
        }
        if (spanRef != null) {
            spanRef[0] = span;
        }
        SpannableString spacedStar = new SpannableString("⭐ ");
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

    public static CharSequence getTransactionTitle(int currentAccount, boolean bot, TL_stars.StarsTransaction t) {
        if (t.floodskip) {
            return LocaleController.getString(R.string.StarsTransactionFloodskip);
        }
        if (!t.extended_media.isEmpty()) {
            return getString(R.string.StarMediaPurchase);
        }
        final boolean affiliate_to_bot = (t.flags & 131072) != 0;
        final boolean affiliate_to_channel = !affiliate_to_bot && (t.flags & 65536) != 0;
        if (affiliate_to_channel) {
            return LocaleController.formatString(R.string.StarTransactionCommission, percents(t.starref_commission_permille));
        }
        if (t.stargift != null) {
            if (t.refund) {
                return LocaleController.getString(t.stars.amount > 0 ? R.string.Gift2TransactionRefundedSent : R.string.Gift2TransactionRefundedConverted);
            } else {
                return LocaleController.getString(t.stars.amount > 0 ? R.string.Gift2TransactionConverted : R.string.Gift2TransactionSent);
            }
        }
        if (t.subscription) {
            if (t.subscription_period == StarsController.PERIOD_MONTHLY) {
                return getString(R.string.StarSubscriptionPurchase);
            }
            if (t.subscription_period == StarsController.PERIOD_5MINUTES) {
                return "5-minute subscription fee";
            }
            if (t.subscription_period == StarsController.PERIOD_MINUTE) {
                return "Minute subscription fee";
            }
        }
        if ((t.flags & 8192) != 0) {
            return getString(R.string.StarsGiveawayPrizeReceived);
        }
        if (t.gift) {
            if (t.sent_by != null) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(DialogObject.getPeerDialogId(t.sent_by));
                return getString(UserObject.isUserSelf(user) ? R.string.StarsGiftSent : R.string.StarsGiftReceived);
            }
            return getString(R.string.StarsGiftReceived);
        }
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
        } else if (t.peer instanceof TL_stars.TL_starsTransactionPeerFragment) {
            return getString(bot ? R.string.StarsTransactionWithdrawFragment : R.string.StarsTransactionFragment);
        } else if (t.peer instanceof TL_stars.TL_starsTransactionPeerPremiumBot) {
            return getString(R.string.StarsTransactionBot);
        } else {
            return getString(R.string.StarsTransactionUnsupported);
        }
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount, int date, TLRPC.Peer sent_by, TLRPC.Peer received_by, TLRPC.TL_messageActionPrizeStars action, Theme.ResourcesProvider resourcesProvider) {
        TL_stars.StarsTransaction t = new TL_stars.StarsTransaction();
        t.title = null;
        t.description = null;
        t.photo = null;
        t.peer = new TL_stars.TL_starsTransactionPeer();
        t.peer.peer = action.boost_peer;
        t.date = date;
        t.stars = new TL_stars.StarsAmount(action.stars);
        t.id = action.transaction_id;
        t.gift = true;
        t.flags |= 8192;
        t.giveaway_post_id = action.giveaway_msg_id;
        t.sent_by = sent_by;
        t.received_by = received_by;
        return showTransactionSheet(context, false, 0, currentAccount, t, resourcesProvider);
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount, int date, TLRPC.Peer sent_by, TLRPC.Peer received_by, TLRPC.TL_messageActionGiftStars action, Theme.ResourcesProvider resourcesProvider) {
        TL_stars.StarsTransaction t = new TL_stars.StarsTransaction();
        t.title = null;
        t.description = null;
        t.photo = null;
        t.peer = new TL_stars.TL_starsTransactionPeer();
        t.peer.peer = sent_by;
        t.date = date;
        t.stars = new TL_stars.StarsAmount(action.stars);
        t.id = action.transaction_id;
        t.gift = true;
        t.sent_by = sent_by;
        t.received_by = received_by;
        return showTransactionSheet(context, false, 0, currentAccount, t, resourcesProvider);
    }

    public static BottomSheet showTransactionSheet(Context context, int currentAccount, int date, TLRPC.TL_messageActionPaymentRefunded action, Theme.ResourcesProvider resourcesProvider) {
        TL_stars.StarsTransaction t = new TL_stars.StarsTransaction();
        t.title = null;
        t.description = null;
        t.photo = null;
        t.peer = new TL_stars.TL_starsTransactionPeer();
        t.peer.peer = action.peer;
        t.date = date;
        t.stars = new TL_stars.StarsAmount(action.total_amount);
        t.id = action.charge.id;
        t.refund = true;
        return showTransactionSheet(context, false, 0, currentAccount, t, resourcesProvider);
    }

    public static BottomSheet showTransactionSheet(Context context, boolean bot, int currentAccount, TLRPC.TL_payments_paymentReceiptStars receipt, Theme.ResourcesProvider resourcesProvider) {
        TL_stars.StarsTransaction t = new TL_stars.StarsTransaction();
        t.title = receipt.title;
        t.description = receipt.description;
        t.photo = receipt.photo;
        t.peer = new TL_stars.TL_starsTransactionPeer();
        t.peer.peer = MessagesController.getInstance(currentAccount).getPeer(receipt.bot_id);
        t.date = receipt.date;
        t.stars = new TL_stars.StarsAmount(-receipt.total_amount);
        t.id = receipt.transaction_id;
        return showTransactionSheet(context, bot, 0, currentAccount, t, resourcesProvider);
    }

    public static Runnable setGiftImage(View view, ImageReceiver imageReceiver, long stars) {
        int type;
        if (stars <= 1_000) {
            type = 2;
        } else if (stars < 2_500) {
            type = 3;
        } else {
            type = 4;
        }
        return setGiftImage(view, imageReceiver, type);
    }

    public static Runnable setGiftImage(View view, ImageReceiver imageReceiver, int type) {
        final boolean[] played = new boolean[1];
        final int currentAccount = imageReceiver.getCurrentAccount();
        Runnable setImage = () -> {
            TLRPC.TL_messages_stickerSet set;
            TLRPC.Document document = null;

            String packName = UserConfig.getInstance(currentAccount).premiumGiftsStickerPack;
            if (packName == null) {
                MediaDataController.getInstance(currentAccount).checkPremiumGiftStickers();
                return;
            }
            set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName);
            }
            if (set != null) {
                String emoji;
                if (type == 2) {
                    emoji = "2⃣";
                } else if (type == 3) {
                    emoji = "3⃣";
                } else {
                    emoji = "4⃣";
                }
                for (int i = 0; i < set.packs.size(); ++i) {
                    TLRPC.TL_stickerPack pack = set.packs.get(i);
                    if (TextUtils.equals(pack.emoticon, emoji) && !pack.documents.isEmpty()) {
                        long documentId = pack.documents.get(0);
                        for (int j = 0; j < set.documents.size(); ++j) {
                            TLRPC.Document d = set.documents.get(j);
                            if (d != null && d.id == documentId) {
                                document = d;
                                break;
                            }
                        }
                        break;
                    }
                }
                if (document == null && !set.documents.isEmpty()) {
                    document = set.documents.get(0);
                }
            }

            if (document != null) {
                imageReceiver.setAllowStartLottieAnimation(true);
                imageReceiver.setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                    @Override
                    public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                        if (set) {
                            RLottieDrawable drawable = imageReceiver.getLottieAnimation();
                            if (drawable != null && !played[0]) {
                                drawable.setCurrentFrame(0, false);
                                AndroidUtilities.runOnUIThread(drawable::restart);
                                played[0] = true;
                            }
                        }
                    }
                });
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 160, true, null, true);
                imageReceiver.setAutoRepeat(0);
                imageReceiver.setImage(
                    ImageLocation.getForDocument(document), "160_160_nr",
                    ImageLocation.getForDocument(thumb, document), "160_160",
                    svgThumb,
                    document.size, "tgs",
                    set,
                    1
                );
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, set == null);
            }
        };
        setImage.run();
        final Runnable cancel1 = NotificationCenter.getInstance(currentAccount).listen(view, NotificationCenter.didUpdatePremiumGiftStickers, args -> setImage.run());
        final Runnable cancel2 = NotificationCenter.getInstance(currentAccount).listen(view, NotificationCenter.diceStickersDidLoad, args -> setImage.run());
        return () -> {
            cancel1.run();
            cancel2.run();
        };
    }

    public static BottomSheet showTransactionSheet(Context context, boolean bot, long dialogId, int currentAccount, TL_stars.StarsTransaction transaction, Theme.ResourcesProvider resourcesProvider) {
        if (transaction == null || context == null)
            return null;

        final boolean giveaway = (transaction.flags & 8192) != 0;
        final boolean affiliate_to_bot = (transaction.flags & 131072) != 0;
        final boolean affiliate_to_channel = !affiliate_to_bot && (transaction.flags & 65536) != 0;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(giveaway || transaction.gift ? 0 : 20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        BackupImageView imageView = new BackupImageView(context);
        if (transaction.stargift != null) {
            setGiftImage(imageView.getImageReceiver(), transaction.stargift, 160);
            linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, -8, 0, 10));
        } else if (giveaway || transaction.gift) {
            setGiftImage(imageView, imageView.getImageReceiver(), transaction.stars.amount);
            linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, -8, 0, 10));
        } else if (!transaction.extended_media.isEmpty()) {
            imageView.setRoundRadius(dp(30));
            TLRPC.MessageMedia media = transaction.extended_media.get(0);
            ImageLocation location = null;
            if (media instanceof TLRPC.TL_messageMediaPhoto) {
                location = ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(100), true), media.photo);
            } else if (media instanceof TLRPC.TL_messageMediaDocument) {
                location = ImageLocation.getForDocument(FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(100), true), media.document);
            }
            imageView.setImage(location, "100_100", null, null, null, 0);
            linearLayout.addView(imageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0, 0, 0, 10));

            imageView.setOnClickListener(v -> {
                final long did = bot ? dialogId : DialogObject.getPeerDialogId(transaction.peer.peer);
                ArrayList<MessageObject> messages = new ArrayList<>();
                for (int i = 0; i < transaction.extended_media.size(); ++i) {
                    TLRPC.MessageMedia emedia = transaction.extended_media.get(i);
                    TLRPC.TL_message msg = new TLRPC.TL_message();
                    msg.id = transaction.msg_id;
                    msg.dialog_id = did;
                    msg.from_id = new TLRPC.TL_peerChannel();
                    msg.from_id.channel_id = -did;
                    msg.peer_id = new TLRPC.TL_peerChannel();
                    msg.peer_id.channel_id = -did;
                    msg.date = transaction.date;
                    msg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    msg.media = emedia;
                    msg.noforwards = true;
                    MessageObject msgObj = new MessageObject(currentAccount, msg, false, false);
                    messages.add(msgObj);
                }
                if (messages.isEmpty()) return;

                PhotoViewer.getInstance().setParentActivity(LaunchActivity.getLastFragment(), resourcesProvider);
                PhotoViewer.getInstance().openPhoto(messages, 0, did, 0, 0, new PhotoViewer.EmptyPhotoViewerProvider() {

                    @Override
                    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
                        final ImageReceiver imageReceiver = imageView.getImageReceiver();
                        int[] coords = new int[2];
                        imageView.getLocationInWindow(coords);
                        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                        object.viewX = coords[0];
                        object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                        object.parentView = linearLayout;
                        object.animatingImageView = null;
                        object.imageReceiver = imageReceiver;
                        if (needPreview) {
                            object.thumb = imageReceiver.getBitmapSafe();
                        }
                        object.radius = imageReceiver.getRoundRadius(true);
                        object.dialogId = did;
                        object.clipTopAddition = 0; // (int) (chatListViewPaddingTop - chatListViewPaddingVisibleOffset - AndroidUtilities.dp(4));
                        object.clipBottomAddition = 0; // blurredViewBottomOffset;
                        return object;
                    }

                    @Override
                    public boolean forceAllInGroup() {
                        return true;
                    }

                    @Override
                    public boolean validateGroupId(long groupId) {
                        return false;
                    }
                });
            });
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeer) {
            if (transaction.photo != null) {
                imageView.setRoundRadius(dp(50));
                imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(transaction.photo)), "100_100", null, 0, null);
            } else {
                imageView.setRoundRadius(dp(50));
                final long did = affiliate_to_channel ? DialogObject.getPeerDialogId(transaction.starref_peer) : transaction.subscription && bot ? dialogId : DialogObject.getPeerDialogId(transaction.peer.peer);
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                if (did >= 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                    avatarDrawable.setInfo(user);
                    imageView.setForUserOrChat(user, avatarDrawable);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    avatarDrawable.setInfo(chat);
                    imageView.setForUserOrChat(chat, avatarDrawable);
                }
            }
            linearLayout.addView(imageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0, 0, 0, 10));
        } else {
            String platform = "?";
            if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAppStore) {
                platform = "ios";
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPlayMarket) {
                platform = "android";
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPremiumBot) {
                platform = "premiumbot";
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerFragment) {
                platform = "fragment";
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAds) {
                platform = "ads";
            } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAPI) {
                platform = "api";
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
        textView.setText(getTransactionTitle(currentAccount, bot, transaction));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(transaction.stars.amount >= 0 ? Theme.key_color_green : Theme.key_color_red, resourcesProvider));
        textView.setText(replaceStarsWithPlain(TextUtils.concat((transaction.stars.amount >= 0 ? "+" : ""), formatStarsAmount(transaction.stars), " ⭐️"), .8f));
        SpannableStringBuilder s = new SpannableStringBuilder(textView.getText());
        if (transaction.refund) {
            appendStatus(s, textView, getString(R.string.StarsRefunded));
        } else if (transaction.failed) {
            textView.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
            appendStatus(s, textView, getString(R.string.StarsFailed));
        } else if (transaction.pending) {
            textView.setTextColor(Theme.getColor(Theme.key_color_yellow, resourcesProvider));
            appendStatus(s, textView, getString(R.string.StarsPending));
        }
        textView.setText(s);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        if (giveaway || transaction.gift) {
            final TLRPC.User user =   transaction.sent_by == null ? null : MessagesController.getInstance(currentAccount).getUser(DialogObject.getPeerDialogId(transaction.sent_by));
            final TLRPC.User received = transaction.sent_by == null ? null : MessagesController.getInstance(currentAccount).getUser(DialogObject.getPeerDialogId(transaction.received_by));
            final boolean self = UserObject.isUserSelf(user);

            if (self) {
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                textView.setText(replaceStarsWithPlain(TextUtils.concat(formatStarsAmount(transaction.stars), " ⭐️"), .8f));
            }

            textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setGravity(Gravity.CENTER);
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
            textView.setText(TextUtils.concat(
                AndroidUtilities.replaceTags(self ? formatString(R.string.ActionGiftStarsSubtitle, UserObject.getForcedFirstName(received)) : getString(R.string.ActionGiftStarsSubtitleYou)),
                " ",
                AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.GiftStarsSubtitleLinkName).replace(' ', ' '), () -> {
                    StarAppsSheet sheet1 = new StarAppsSheet(context);
                    if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(sheet[0].attachedFragment) && sheet[0] != null && sheet[0].attachedFragment != null) {
                        sheet1.makeAttached(sheet[0].attachedFragment);
                    }
                    sheet1.show();
                }), true)
            ));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));
        } else if (transaction.description != null && transaction.extended_media.isEmpty()) {
            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setGravity(Gravity.CENTER);
            textView.setText(transaction.description);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));
        }

        TableView tableView = new TableView(context, resourcesProvider);
        if (transaction.stargift != null) {
            if (!transaction.refund) {
                final long did = DialogObject.getPeerDialogId(transaction.peer.peer);
                final TLRPC.User didUser = MessagesController.getInstance(currentAccount).getUser(did);
                if (transaction.stars.amount > 0) { // converted
                    tableView.addRowUser(getString(R.string.StarGiveawayPrizeFrom), currentAccount, did, () -> {
                        sheet[0].dismiss();
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            if ((transaction.flags & 8192) != 0) {
                                lastFragment.presentFragment(ChatActivity.of(did, transaction.giveaway_post_id));
                            } else {
                                lastFragment.presentFragment(ChatActivity.of(did));
                            }
                        }
                    }, !UserObject.isDeleted(didUser) ? getString(R.string.Gift2ButtonSendGift) : null, () -> {
                        new GiftSheet(context, currentAccount, did, sheet[0]::dismiss).show();
                    });
                    tableView.addRowUser(getString(R.string.StarGiveawayPrizeTo), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), () -> {
                        sheet[0].dismiss();
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            final Bundle args = new Bundle();
                            args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                            args.putBoolean("my_profile", true);
                            args.putBoolean("open_gifts", true);
                            lastFragment.presentFragment(new ProfileActivity(args));
                        }
                    });
                } else { // sent
                    tableView.addRowUser(getString(R.string.StarGiveawayPrizeFrom), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), () -> {
                        sheet[0].dismiss();
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            final Bundle args = new Bundle();
                            args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                            args.putBoolean("my_profile", true);
                            args.putBoolean("open_gifts", true);
                            lastFragment.presentFragment(new ProfileActivity(args));
                        }
                    });
                    tableView.addRowUser(getString(R.string.StarGiveawayPrizeTo), currentAccount, did, () -> {
                        sheet[0].dismiss();
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            if ((transaction.flags & 8192) != 0) {
                                lastFragment.presentFragment(ChatActivity.of(did, transaction.giveaway_post_id));
                            } else {
                                lastFragment.presentFragment(ChatActivity.of(did));
                            }
                        }
                    }, !UserObject.isDeleted(didUser) ? getString(R.string.Gift2ButtonSendGift) : null, () -> {
                        new GiftSheet(context, currentAccount, did, sheet[0]::dismiss).show();
                    });
                }
            }
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeer) {
            final long did = DialogObject.getPeerDialogId(transaction.peer.peer);
            if (affiliate_to_bot) {
                final long botId = dialogId;
                final long channelId = DialogObject.getPeerDialogId(transaction.starref_peer);
                final long referredUserId = did;
                tableView.addRowLink(getString(R.string.StarAffiliateReason), getString(R.string.StarAffiliateReasonProgram), () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        lastFragment.presentFragment(new AffiliateProgramFragment(botId));
                    }
                });
                tableView.addRowUser(getString(R.string.StarAffiliate), currentAccount, channelId, () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        lastFragment.presentFragment(ProfileActivity.of(channelId));
                    }
                });
                tableView.addRowUser(getString(R.string.StarAffiliateReferredUser), currentAccount, referredUserId, () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        lastFragment.presentFragment(ProfileActivity.of(referredUserId));
                    }
                });
                tableView.addRow(getString(R.string.StarAffiliateCommission), percents(transaction.starref_commission_permille));
            } else if (affiliate_to_channel) {
                final long botId = did;
                final long channelId = dialogId;
                tableView.addRowLink(getString(R.string.StarAffiliateReason), getString(R.string.StarAffiliateReasonProgram), () -> {
                    BotStarsController.getInstance(currentAccount).getConnectedBot(context, dialogId, botId, connectedBot -> {
                        sheet[0].dismiss();
                        ChannelAffiliateProgramsFragment.showShareAffiliateAlert(context, currentAccount, connectedBot, dialogId, resourcesProvider);
                    });
                });
                tableView.addRowUser(getString(R.string.StarAffiliateMiniApp), currentAccount, botId, () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        lastFragment.presentFragment(ProfileActivity.of(botId));
                    }
                });
            } else if (giveaway) {
                tableView.addRowUser(getString(R.string.StarGiveawayPrizeFrom), currentAccount, did, () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        if ((transaction.flags & 8192) != 0) {
                            lastFragment.presentFragment(ChatActivity.of(did, transaction.giveaway_post_id));
                        } else {
                            lastFragment.presentFragment(ChatActivity.of(did));
                        }
                    }
                });
                tableView.addRowUser(getString(R.string.StarGiveawayPrizeTo), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        final Bundle args = new Bundle();
                        args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                        args.putBoolean("my_profile", true);
                        lastFragment.presentFragment(new ProfileActivity(args));
                    }
                });
                tableView.addRowLink(getString(R.string.StarGiveawayReason), getString(R.string.StarGiveawayReasonLink), () -> {
                    sheet[0].dismiss();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        if ((transaction.flags & 8192) != 0) {
                            lastFragment.presentFragment(ChatActivity.of(did, transaction.giveaway_post_id));
                        } else {
                            lastFragment.presentFragment(ChatActivity.of(did));
                        }
                    }
                });
                tableView.addRow(getString(R.string.StarGiveawayGift), formatStarsAmountString(transaction.stars));
            } else if (transaction.subscription && !bot) {
                tableView.addRowUser(getString(R.string.StarSubscriptionTo), currentAccount, did, () -> {
                    sheet[0].dismiss();
                    if (UserObject.isService(did)) {
                        Browser.openUrl(context, getString(R.string.StarsTransactionUnknownLink));
                    } else {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            lastFragment.presentFragment(ChatActivity.of(did));
                        }
                    }
                });
            } else {
                tableView.addRowUser(getString(R.string.StarsTransactionRecipient), currentAccount, did, () -> {
                    sheet[0].dismiss();
                    if (UserObject.isService(did)) {
                        Browser.openUrl(context, getString(R.string.StarsTransactionUnknownLink));
                    } else {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            lastFragment.presentFragment(ChatActivity.of(did));
                        }
                    }
                });
            }
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerFragment) {
            if (transaction.gift) {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setSingleLine(true);
                ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
                AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
                CharSequence username = getString(R.string.StarsTransactionUnknown);
                CombinedDrawable iconDrawable = getPlatformDrawable("fragment", 24);
                iconDrawable.setIconSize(dp(16), dp(16));
                avatarSpan.setImageDrawable(iconDrawable);
                SpannableStringBuilder ssb = new SpannableStringBuilder("x  " + username);
                ssb.setSpan(avatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        sheet[0].dismiss();
                        Browser.openUrl(context, getString(R.string.StarsTransactionUnknownLink));
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setUnderlineText(false);
                    }
                }, 3, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setText(ssb);
                tableView.addRowUnpadded(getString(R.string.StarsTransactionRecipient), textView);
            } else {
                tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.Fragment));
            }
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerAppStore) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.AppStore));
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPlayMarket) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.PlayMarket));
        } else if (transaction.peer instanceof TL_stars.TL_starsTransactionPeerPremiumBot) {
            tableView.addRow(getString(R.string.StarsTransactionSource), getString(R.string.StarsTransactionBot));
        }

        if (transaction.peer instanceof TL_stars.TL_starsTransactionPeer && (transaction.flags & 256) != 0) {
            long did = DialogObject.getPeerDialogId(transaction.peer.peer);
            if (bot) {
                did = dialogId;
            }
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            if (chat != null) {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
                SpannableStringBuilder ssb = new SpannableStringBuilder("");
                if (!transaction.extended_media.isEmpty()) {
                    int count = 0;
                    for (TLRPC.MessageMedia media : transaction.extended_media) {
                        ImageReceiverSpan span = new ImageReceiverSpan(textView, currentAccount, 24);
                        ImageLocation location = null;
                        if (media instanceof TLRPC.TL_messageMediaPhoto) {
                            location = ImageLocation.getForPhoto(FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, dp(24), true), media.photo);
                        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
                            location = ImageLocation.getForDocument(FileLoader.getClosestPhotoSizeWithSize(media.document.thumbs, dp(24), true), media.document);
                        }
                        if (location != null) {
                            span.setRoundRadius(6);
                            span.imageReceiver.setImage(location, "24_24", null, null, null, 0);
                            SpannableString str = new SpannableString("x");
                            str.setSpan(span, 0, str.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.append(str);
                            ssb.append(" ");
                            count++;
                        }
                        if (count >= 3) break;
                    }
                }
                ssb.append(" ");
                int start = ssb.length();
                String username = ChatObject.getPublicUsername(chat);
                if (TextUtils.isEmpty(username)) {
                    ssb.append(chat.title);
                } else {
                    ssb.append(MessagesController.getInstance(currentAccount).linkPrefix + "/" + username + "/" + transaction.msg_id);
                }
                final long finalDialogId = did;
                Runnable open = () -> {
                    sheet[0].dismiss();
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        Bundle args = new Bundle();
                        args.putLong("chat_id", -finalDialogId);
                        args.putInt("message_id", transaction.msg_id);
                        lastFragment.presentFragment(new ChatActivity(args));
                    }
                };
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        open.run();
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setUnderlineText(false);
                    }
                }, start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setText(ssb);
                textView.setOnClickListener(v -> open.run());
                tableView.addRowUnpadded(getString(transaction.reaction ? R.string.StarsTransactionMessage : R.string.StarsTransactionMedia), textView);
            }
        }

        if (!TextUtils.isEmpty(transaction.id) && !giveaway) {
            FrameLayout idLayout = new FrameLayout(context);
            idLayout.setPadding(dp(12.66f), dp(9.33f), dp(10.66f), dp(9.33f));
            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, transaction.id.length() > 25 ? 9 : 10);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setMaxLines(4);
            textView.setSingleLine(false);
            textView.setText(transaction.id);
            idLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 34, 0));
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

        if (transaction.floodskip && transaction.floodskip_number > 0) {
            tableView.addRow(getString(R.string.StarsTransactionFloodskipNumberName), LocaleController.formatPluralStringComma("StarsTransactionFloodskipNumber", transaction.floodskip_number));
        }

        tableView.addRow(getString(R.string.StarsTransactionDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(transaction.date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(transaction.date * 1000L))));
        if (transaction.stargift != null) {
            if (transaction.stargift.limited) {
                addAvailabilityRow(tableView, currentAccount, transaction.stargift, resourcesProvider);
            }
            if (!TextUtils.isEmpty(transaction.description)) {
                CharSequence message = new SpannableStringBuilder(transaction.description);
                tableView.addFullRow(message);
            }
        }
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 0));

        if ((transaction.flags & 32) != 0) {
            tableView.addRow(getString(R.string.StarsTransactionTONDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(transaction.transaction_date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(transaction.transaction_date * 1000L))));
        }

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
        if ((transaction.flags & 32) != 0) {
            button.setText(getString(R.string.StarsTransactionViewInBlockchainExplorer), false);
        } else {
            button.setText(getString(R.string.OK), false);
        }
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;
        if ((transaction.flags & 32) != 0) {
            button.setOnClickListener(v -> {
                Browser.openUrl(context, transaction.transaction_url);
            });
        } else {
            button.setOnClickListener(v -> {
                sheet[0].dismiss();
            });
        }

        sheet[0].fixNavigationBar();
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
            sheet[0].makeAttached(fragment);
        }
        sheet[0].show();
        return sheet[0];
    }

    public static BottomSheet showSubscriptionSheet(Context context, int currentAccount, TL_stars.StarsSubscription subscription, Theme.ResourcesProvider resourcesProvider) {
        if (subscription == null || context == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(4));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        FrameLayout topView = new FrameLayout(context);
        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL, 0, 0, 0, 10));

        final boolean[] maybeCloseAfterUpdate = new boolean[1];
        final NotificationCenter.NotificationCenterDelegate observer = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.starSubscriptionsLoaded) {
                    if (maybeCloseAfterUpdate[0] && sheet[0] != null) {
                        sheet[0].dismiss();
                    }
                }
            }
        };
        NotificationCenter.getInstance(currentAccount).addObserver(observer, NotificationCenter.starSubscriptionsLoaded);
        final long did = DialogObject.getPeerDialogId(subscription.peer);
        BackupImageView imageView = new BackupImageView(context);
        final TLObject peerObject;
        final String peerName;
        final boolean bot, business;
        if (did >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            peerObject = user;
            peerName = UserObject.getUserName(user);
            bot = UserObject.isBot(user);
            business = !bot;
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            peerObject = chat;
            peerName = chat == null ? "" : chat.title;
            bot = false;
            business = false;
        }
        if (subscription.photo != null) {
            imageView.setRoundRadius(dp(21));
            imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(subscription.photo)), "100_100", null, 0, null);
        } else {
            imageView.setRoundRadius(dp(50));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (did >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                avatarDrawable.setInfo(chat);
                imageView.setForUserOrChat(chat, avatarDrawable);
            }
        }
        topView.addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER));

        Drawable starBg = context.getResources().getDrawable(R.drawable.star_small_outline);
        starBg.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.SRC_IN));
        Drawable starFg = context.getResources().getDrawable(R.drawable.star_small_inner);

        if (subscription.photo == null) {
            ImageView starBgView = new ImageView(context);
            starBgView.setImageDrawable(starBg);
            topView.addView(starBgView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            starBgView.setTranslationX(dp(34));
            starBgView.setTranslationY(dp(35));
            starBgView.setScaleX(1.1f);
            starBgView.setScaleY(1.1f);

            ImageView starFgView = new ImageView(context);
            starFgView.setImageDrawable(starFg);
            topView.addView(starFgView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            starFgView.setTranslationX(dp(34));
            starFgView.setTranslationY(dp(35));
        }

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        if (!TextUtils.isEmpty(subscription.title)) {
            textView.setText(subscription.title);
        } else {
            textView.setText(getString(R.string.StarsSubscriptionTitle));
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        if (subscription.pricing.period == StarsController.PERIOD_MONTHLY) {
            textView.setText(replaceStarsWithPlain(formatString(R.string.StarsSubscriptionPrice, subscription.pricing.amount), .8f));
        } else {
            final String period = subscription.pricing.period == StarsController.PERIOD_5MINUTES ? "5min" : "min";
            textView.setText(replaceStarsWithPlain(formatString(R.string.StarsSubscriptionPrice, subscription.pricing.amount, period), .8f));
        }
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        final TableView tableView = new TableView(context, resourcesProvider);
        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setSingleLine(true);
        ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
        AvatarSpan avatarSpan = new AvatarSpan(textView, currentAccount, 24);
        CharSequence username;
        boolean deleted = false;
        if (did >= 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            deleted = user == null || UserObject.isDeleted(user);
            username = UserObject.getUserName(user);
            avatarSpan.setUser(user);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            deleted = chat == null;
            username = chat != null ? chat.title : "";
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
        if (!deleted) {
            tableView.addRowUnpadded(getString(did < 0 ? R.string.StarsSubscriptionChannel : (business ? R.string.StarsSubscriptionBusiness : R.string.StarsSubscriptionBot)), textView);
        }

        if (did >= 0 && !TextUtils.isEmpty(subscription.title)) {
            tableView.addRow(getString(business ? R.string.StarsSubscriptionBusinessProduct : R.string.StarsSubscriptionBotProduct), subscription.title);
        }

        tableView.addRow(
                getString(R.string.StarsSubscriptionSince),
                LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date((subscription.until_date - subscription.pricing.period) * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date((subscription.until_date - subscription.pricing.period) * 1000L)))
        );
        final long now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        tableView.addRow(
                getString(subscription.canceled || subscription.bot_canceled ? R.string.StarsSubscriptionUntilExpires : now > subscription.until_date ? R.string.StarsSubscriptionUntilExpired : R.string.StarsSubscriptionUntilRenews),
                LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(subscription.until_date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(subscription.until_date * 1000L)))
        );
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTransactionTOS), () -> {
            Browser.openUrl(context, getString(R.string.StarsTOSLink));
        }));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 15, 14, 7));

        if (now < subscription.until_date) {
            if (subscription.can_refulfill) {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setText(formatString(bot ? R.string.StarsSubscriptionBotRefulfillInfo : R.string.StarsSubscriptionRefulfillInfo, LocaleController.formatDateChat(subscription.until_date)));
                textView.setSingleLine(false);
                textView.setMaxLines(4);
                textView.setGravity(Gravity.CENTER);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 26, 7, 26, 15));

                ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
                button.setText(getString(bot ? R.string.StarsSubscriptionBotRefulfill : R.string.StarsSubscriptionRefulfill), false);
                linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                button.setOnClickListener(v -> {
                    if (button.isLoading()) return;
                    StarsController c = StarsController.getInstance(currentAccount);
                    final Runnable refulfill = () -> {
                        button.setLoading(true);
                        TL_stars.TL_fulfillStarsSubscription req = new TL_stars.TL_fulfillStarsSubscription();
                        req.subscription_id = subscription.id;
                        req.peer = new TLRPC.TL_inputPeerSelf();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            button.setLoading(false);
                            if (sheet[0] != null) {
                                sheet[0].dismiss();
                            }
                            StarsController.getInstance(currentAccount).invalidateSubscriptions(true);

                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            lastFragment.presentFragment(ChatActivity.of(did));
                        }));
                    };
                    if (c.balance.amount < subscription.pricing.amount) {
                        new StarsNeededSheet(context, resourcesProvider, subscription.pricing.amount, business ? StarsNeededSheet.TYPE_BIZ_SUBSCRIPTION_KEEP : did < 0 ? StarsNeededSheet.TYPE_SUBSCRIPTION_KEEP : StarsNeededSheet.TYPE_BOT_SUBSCRIPTION_KEEP, peerName, refulfill).show();
                    } else {
                        refulfill.run();
                    }
                });
            } else if (subscription.bot_canceled) {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setText(getString(business ? R.string.StarsSubscriptionBusinessCancelledText : R.string.StarsSubscriptionBotCancelledText));
                textView.setSingleLine(false);
                textView.setMaxLines(4);
                textView.setGravity(Gravity.CENTER);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 26, 7, 26, 15));
            } else if (subscription.canceled) {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setText(getString(R.string.StarsSubscriptionCancelledText));
                textView.setSingleLine(false);
                textView.setMaxLines(4);
                textView.setGravity(Gravity.CENTER);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 26, 7, 26, 15));

                if (subscription.chat_invite_hash != null || subscription.invoice_slug != null) {
                    ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
                    button.setText(getString(R.string.StarsSubscriptionRenew), false);
                    linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    button.setOnClickListener(v -> {
                        if (button.isLoading()) return;
                        button.setLoading(true);
                        TL_stars.TL_changeStarsSubscription req = new TL_stars.TL_changeStarsSubscription();
                        req.canceled = false;
                        req.peer = new TLRPC.TL_inputPeerSelf();
                        req.subscription_id = subscription.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            button.setLoading(false);
                            if (sheet[0] != null) {
                                sheet[0].dismiss();
                            }
                            StarsController.getInstance(currentAccount).invalidateSubscriptions(true);

                            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                            if (fragment != null) {
                                BulletinFactory.of(fragment).createUsersBulletin(Collections.singletonList(peerObject), getString(R.string.StarsSubscriptionRenewedToast), AndroidUtilities.replaceTags(formatString(R.string.StarsSubscriptionRenewedToastText, peerName))).show(false);
                            }
                        }));
                    });
                }
            } else {
                textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setText(formatString(R.string.StarsSubscriptionCancelInfo, LocaleController.formatDateChat(subscription.until_date)));
                textView.setSingleLine(false);
                textView.setMaxLines(4);
                textView.setGravity(Gravity.CENTER);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 26, 7, 26, 15));

                ButtonWithCounterView button = new ButtonWithCounterView(context, false, resourcesProvider);
                button.setText(getString(R.string.StarsSubscriptionCancel), false);
                button.setTextColor(Theme.getColor(Theme.key_color_red, resourcesProvider));
                linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                button.setOnClickListener(v -> {
                    if (button.isLoading()) return;
                    button.setLoading(true);
                    TL_stars.TL_changeStarsSubscription req = new TL_stars.TL_changeStarsSubscription();
                    req.canceled = true;
                    req.peer = new TLRPC.TL_inputPeerSelf();
                    req.subscription_id = subscription.id;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        button.setLoading(false);
                        if (sheet[0] != null) {
                            sheet[0].dismiss();
                        }
                        StarsController.getInstance(currentAccount).invalidateSubscriptions(true);

                        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                        if (fragment != null) {
                            String message;
                            if (business && !TextUtils.isEmpty(subscription.title)) {
                                message = formatString(R.string.StarsSubscriptionCancelledBizToastText, LocaleController.formatDateChat(subscription.until_date), subscription.title);
                            } else if (bot && !TextUtils.isEmpty(subscription.title)) {
                                message = formatString(R.string.StarsSubscriptionCancelledBotToastText, LocaleController.formatDateChat(subscription.until_date), subscription.title);
                            } else {
                                message = formatString(R.string.StarsSubscriptionCancelledToastText, LocaleController.formatDateChat(subscription.until_date));
                            }
                            BulletinFactory.of(fragment).createUsersBulletin(Collections.singletonList(peerObject), getString(R.string.StarsSubscriptionCancelledToast), AndroidUtilities.replaceTags(message)).show(false);
                        }
                    }));
                });
            }
        } else {
            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setText(formatString(R.string.StarsSubscriptionExpiredInfo, LocaleController.formatDateChat(subscription.until_date)));
            textView.setSingleLine(false);
            textView.setMaxLines(4);
            textView.setGravity(Gravity.CENTER);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 26, 7, 26, 15));

            if (subscription.chat_invite_hash != null || subscription.invoice_slug != null) {
                ButtonWithCounterView button = new ButtonWithCounterView(context, true, resourcesProvider);
                button.setText(getString(R.string.StarsSubscriptionAgain), false);
                linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                button.setOnClickListener(v -> {
                    if (button.isLoading()) return;
                    button.setLoading(true);
                    if (subscription.chat_invite_hash != null) {
                        TLRPC.TL_messages_checkChatInvite req = new TLRPC.TL_messages_checkChatInvite();
                        req.hash = subscription.chat_invite_hash;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            button.setLoading(false);
                            if (res instanceof TLRPC.ChatInvite) {
                                TLRPC.ChatInvite invite = (TLRPC.ChatInvite) res;
                                if (invite.subscription_pricing == null) { // wtf
                                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(getString(R.string.UnknownError)).show(false);
                                    return;
                                }
                                final long stars = invite.subscription_pricing.amount;
                                StarsController.getInstance(currentAccount).subscribeTo(req.hash, invite, (status, dialogId) -> {
                                    if ("paid".equals(status) && dialogId != 0) {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                            if (lastFragment == null) return;
                                            BaseFragment chatActivity = ChatActivity.of(dialogId);
                                            lastFragment.presentFragment(chatActivity);

                                            TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                                            if (newChat != null) {
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    BulletinFactory.of(chatActivity).createSimpleBulletin(R.raw.stars_send, getString(R.string.StarsSubscriptionCompleted), AndroidUtilities.replaceTags(formatPluralString("StarsSubscriptionCompletedText", (int) stars, newChat.title))).show(true);
                                                }, 250);
                                            }
                                        });
                                    }
                                });
                            } else {
                                BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.LinkHashExpired)).show(false);
                            }
                        }));
                    } else if (subscription.invoice_slug != null) {
                        maybeCloseAfterUpdate[0] = true;
                        Browser.openUrl(context, Uri.parse("https://t.me/$" + subscription.invoice_slug), true, false, false, new Browser.Progress() {
                            @Override
                            public void end() {
                                button.setLoading(false);
                            }
                        }, null, false, true, false);
                    }
                });
            }
        }

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;
        sheet[0].setOnDismissListener(d -> {
            NotificationCenter.getInstance(currentAccount).removeObserver(observer, NotificationCenter.starSubscriptionsLoaded);
        });

        sheet[0].fixNavigationBar();
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
            sheet[0].makeAttached(fragment);
        }
        sheet[0].show();
        return sheet[0];
    }

    public static BottomSheet showBoostsSheet(Context context, int currentAccount, long dialogId, TL_stories.Boost boost, Theme.ResourcesProvider resourcesProvider) {
        if (boost == null || context == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, dp(20), 0, dp(4));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        FrameLayout topView = new FrameLayout(context);
        topView.setClipChildren(false);
        topView.setClipToPadding(false);
        linearLayout.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 150, Gravity.FILL_HORIZONTAL, 0, 0, 0, 10));

        final StarParticlesView particlesView = makeParticlesView(context, 70, 0);
        topView.addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final GLIconTextureView iconView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_GOLDEN_STAR);
        iconView.mRenderer.colorKey1 = Theme.key_starsGradient1;
        iconView.mRenderer.colorKey2 = Theme.key_starsGradient2;
        iconView.mRenderer.updateColors();
        iconView.setStarParticlesView(particlesView);
        topView.addView(iconView, LayoutHelper.createFrame(170, 170, Gravity.CENTER, 0, 32, 0, 24));
        iconView.setPaused(false);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(LocaleController.formatPluralStringSpaced("BoostStars", (int) boost.stars));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setBackground(Theme.createRoundRectDrawable(dp(20), 0xFF967BFF));
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.33f);
        textView.setPadding(dp(4), 0, dp(8.33f), 0);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.bold());
        final SpannableStringBuilder sb = new SpannableStringBuilder("x" + LocaleController.formatPluralStringSpaced("BoostingBoostsCount", boost.multiplier == 0 ? 1 : boost.multiplier));
        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_boost_badge, ColoredImageSpan.ALIGN_CENTER);
        span.translate(0, dp(0.66f));
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(sb);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER, 20, 4, 20, 4));

        TableView tableView = new TableView(context, resourcesProvider);

        tableView.addRowUser(getString(R.string.BoostFrom), currentAccount, dialogId, () -> {
            if (sheet[0] != null) sheet[0].dismiss();
            final BaseFragment baseFragment = LaunchActivity.getSafeLastFragment();
            if (baseFragment == null) return;
            baseFragment.presentFragment(ChatActivity.of(dialogId));
        });
        tableView.addRow(getString(R.string.BoostGift), formatPluralString("BoostStars", (int) boost.stars));
        if (boost.giveaway_msg_id != 0) {
            tableView.addRowLink(getString(R.string.BoostReason), getString(R.string.BoostReasonGiveaway), () -> {
                if (sheet[0] != null) sheet[0].dismiss();
                final BaseFragment baseFragment = LaunchActivity.getSafeLastFragment();
                if (baseFragment == null) return;
                baseFragment.presentFragment(ChatActivity.of(dialogId, boost.giveaway_msg_id));
            });
        }
        tableView.addRow(getString(R.string.BoostDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(boost.date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(boost.date * 1000L))));
        tableView.addRow(getString(R.string.BoostUntil), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(boost.expires * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(boost.expires * 1000L))));

        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 17, 16, 0));

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsTransactionTOS), () -> {
            Browser.openUrl(context, getString(R.string.StarsTOSLink));
        }));
        textView.setGravity(Gravity.CENTER);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 15, 14, 7));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        button.setOnClickListener(v -> {
            if (sheet[0] != null) sheet[0].dismiss();
        });
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;

        sheet[0].fixNavigationBar();
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
            sheet[0].makeAttached(fragment);
        }
        iconView.setPaused(false);
        sheet[0].show();
        sheet[0].setOnDismissListener(() -> {
            iconView.setPaused(true);
        });

        return sheet[0];
    }

    private static CharSequence appendStatus(SpannableStringBuilder s, TextView textView, String string) {
        s.append(" ");
        SpannableString refund = new SpannableString(string);
        final int color = textView.getCurrentTextColor();
        refund.setSpan(new ReplacementSpan() {
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            { backgroundPaint.setColor(Theme.multAlpha(color, .10f)); }
            private final Text layout = new Text(string, 13, AndroidUtilities.bold());

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
        return s;
    }

    public static BottomSheet showMediaPriceSheet(Context context, long stars, boolean allowClear, Utilities.Callback2<Long, Runnable> whenDone, Theme.ResourcesProvider resourcesProvider) {
        final BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        final BottomSheet[] sheet = new BottomSheet[1];

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setPadding(dp(16), dp(16), dp(16), dp(8));

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(getString(R.string.PaidContentTitle));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 18));

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        final OutlineTextContainerView editTextContainer = new OutlineTextContainerView(context, resourcesProvider);
        editTextContainer.setForceForceUseCenter(true);
        editTextContainer.setText(getString(R.string.PaidContentPriceTitle));
        editTextContainer.setLeftPadding(dp(14 + 22));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackground(null);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setMaxLines(1);
        int padding = AndroidUtilities.dp(16);
        editText.setPadding(dp(6), padding, padding, padding);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setTypeface(Typeface.DEFAULT);
        editText.setSelectAllOnFocus(true);
        editText.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setOnFocusChangeListener((v, hasFocus) -> editTextContainer.animateSelection(hasFocus, !TextUtils.isEmpty(editText.getText())));
        LinearLayout editTextLayout = new LinearLayout(context);
        editTextLayout.setOrientation(LinearLayout.HORIZONTAL);
        ImageView starImage = new ImageView(context);
        starImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        starImage.setImageResource(R.drawable.star_small_inner);
        editTextLayout.addView(starImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        editTextLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL));
        editTextContainer.attachEditText(editText);
        editTextContainer.addView(editTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        linearLayout.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subPriceView = new TextView(context);
        subPriceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        subPriceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        editTextContainer.addView(subPriceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 14, 0));

        final LinkSpanDrawable.LinksTextView infoView = new LinkSpanDrawable.LinksTextView(context);
        infoView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.PaidContentInfo), () -> {
            Browser.openUrl(context, getString(R.string.PaidContentInfoLink));
        }), true));
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        infoView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        linearLayout.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 3, 14, 24));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(stars > 0 ? R.string.PaidContentUpdateButton : R.string.PaidContentButton), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        final ButtonWithCounterView clearButton;
        if (stars > 0 && allowClear) {
            clearButton = new ButtonWithCounterView(context, false, resourcesProvider);
            clearButton.setText(getString(R.string.PaidContentClearButton), false, false);
            linearLayout.addView(clearButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 4, 0, 0));
        } else {
            clearButton = null;
        }

        b.setCustomView(linearLayout);
        sheet[0] = b.create();

        editText.setText(stars <= 0 ? "" : Long.toString(stars));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            private boolean ignore;
            private int shakeDp = 2;
            @Override
            public void afterTextChanged(Editable s) {
                if (ignore) return;

                long input_stars = 0;
                try {
                    input_stars = TextUtils.isEmpty(s) ? 0 : Long.parseLong(s.toString());
                    if (input_stars > MessagesController.getInstance(UserConfig.selectedAccount).starsPaidPostAmountMax) {
                        ignore = true;
                        editText.setText(Long.toString(input_stars = MessagesController.getInstance(UserConfig.selectedAccount).starsPaidPostAmountMax));
                        editText.setSelection(editText.getText().length());
                        AndroidUtilities.shakeViewSpring(editTextContainer, shakeDp = -shakeDp);
                    }
                } catch (Exception e) {
                    ignore = true;
                    editText.setText(stars <= 0 ? "" : Long.toString(stars));
                    editText.setSelection(editText.getText().length());
                }
                ignore = false;

                if (!allowClear) {
                    button.setEnabled(input_stars > 0);
                }

                editTextContainer.animateSelection(editText.isFocused(), !TextUtils.isEmpty(editText.getText()));

                if (input_stars == 0) {
                    subPriceView.animate().alpha(0).start();
                    subPriceView.setText("");
                } else {
                    subPriceView.animate().alpha(1f).start();
                    subPriceView.setText("≈" + BillingController.getInstance().formatCurrency((long) (input_stars / 1000.0 * MessagesController.getInstance(UserConfig.selectedAccount).starsUsdWithdrawRate1000), "USD"));
                }
            }
        });

        final boolean[] loading = new boolean[] { false };
        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                if (loading[0]) return true;
                if (whenDone != null) {
                    button.setLoading(loading[0] = true);
                    whenDone.run(Long.parseLong(editText.getText().toString()), () -> {
                        AndroidUtilities.hideKeyboard(editText);
                        sheet[0].dismiss();
                    });
                } else {
                    AndroidUtilities.hideKeyboard(editText);
                    sheet[0].dismiss();
                }
                return true;
            }
            return false;
        });
        button.setOnClickListener(v -> {
            if (loading[0]) return;
            if (whenDone != null) {
                String s = editText.getText().toString();
                button.setLoading(loading[0] = true);
                whenDone.run(TextUtils.isEmpty(s) ? 0 : Long.parseLong(s), () -> {
                    AndroidUtilities.hideKeyboard(editText);
                    sheet[0].dismiss();
                });
            } else {
                AndroidUtilities.hideKeyboard(editText);
                sheet[0].dismiss();
            }
        });
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (loading[0]) return;
                if (whenDone != null) {
                    clearButton.setLoading(loading[0] = true);
                    whenDone.run(0L, () -> {
                        loading[0] = false;
                        AndroidUtilities.hideKeyboard(editText);
                        sheet[0].dismiss();
                    });
                } else {
                    AndroidUtilities.hideKeyboard(editText);
                    sheet[0].dismiss();
                }
            });
        }

        sheet[0].fixNavigationBar();
        sheet[0].setOnDismissListener(d -> {
            AndroidUtilities.hideKeyboard(editText);
        });
        sheet[0].show();

        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        boolean keyboardVisible = false;
        if (lastFragment instanceof ChatActivity) {
            keyboardVisible = ((ChatActivity) lastFragment).needEnterText();
        }
        AndroidUtilities.runOnUIThread(() -> {
            sheet[0].setFocusable(true);
            editText.requestFocus();
            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
        }, keyboardVisible ? 200 : 80);

        return sheet[0];
    }

    public static void setGiftImage(ImageReceiver imageReceiver, TL_stars.StarGift gift, int size) {
        if (gift == null || gift.sticker == null) {
            imageReceiver.clearImage();
            return;
        }

        final TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(gift.sticker.thumbs, size);
        final SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(gift.sticker.thumbs, Theme.key_windowBackgroundGray, 0.35f);
        imageReceiver.setImage(
            ImageLocation.getForDocument(gift.sticker),
            size + "_" + size,
            ImageLocation.getForDocument(photoSize, gift.sticker),
            size + "_" + size,
            svgThumb,
            0,
            null,
            gift,
            0
        );
    }
    public static BottomSheet showActionGiftSheet(Context context, int currentAccount, long dialogId, boolean out, int date, int msg_id, TLRPC.TL_messageActionStarGift action, Theme.ResourcesProvider resourcesProvider) {
        if (action == null || context == null)
            return null;

        final TL_stars.StarGift gift = action.gift;
        if (gift == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        BackupImageView imageView = new BackupImageView(context);
        setGiftImage(imageView.getImageReceiver(), gift, 160);
        linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, -8, 0, 10));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(getString(out ? R.string.Gift2TitleSent : R.string.Gift2TitleReceived));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final boolean fromBot = UserObject.isBot(user);

        textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(Gravity.CENTER);
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setLineSpacing(dp(2), 1f);
        ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
        final int within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - date);
        final int withinDays = Math.max(1, within / (60 * 60 * 24));
        textView.setText(TextUtils.concat(
                AndroidUtilities.replaceTags(fromBot ? (
                                action.saved ? LocaleController.getString(R.string.Gift2Info2BotRemove) : LocaleController.getString(R.string.Gift2Info2BotKeep)
                        ) : out ?
                        action.saved && !action.converted ? formatString(R.string.Gift2InfoOutPinned, UserObject.getForcedFirstName(user)) : formatPluralStringComma(action.converted ? "Gift2InfoOutConverted" : "Gift2InfoOut", (int) action.convert_stars, UserObject.getForcedFirstName(user)) :
                        action.converted ? formatPluralStringComma("Gift2InfoConverted", (int) action.convert_stars) : formatPluralStringComma("Gift2Info", (int) action.convert_stars)
                ),
                " ",
                AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2More).replace(' ', ' '), () -> {
                    new ExplainStarsSheet(context).show();
                }), true)
        ));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 5, 5, 4));

        if (action.name_hidden) {
            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setText(getString(R.string.Gift2SenderHidden));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 13, 20, 2));
        }

        TableView tableView = new TableView(context, resourcesProvider);
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final long fromId = out ? selfId : dialogId;
        final long toId = out ? dialogId : selfId;
        final TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
        if (fromId != selfId) {
            tableView.addRowUser(getString(R.string.Gift2From), currentAccount, fromId, () -> {
                sheet[0].dismiss();
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    if (UserObject.isService(fromId)) return;
                    Bundle args = new Bundle();
                    if (fromId > 0) {
                        args.putLong("user_id", fromId);
                        if (fromId == selfId) {
                            args.putBoolean("my_profile", true);
                            args.putBoolean("open_gifts", true);
                        }
                    } else {
                        args.putLong("chat_id", -fromId);
                    }
                    lastFragment.presentFragment(new ProfileActivity(args));
                }
            }, fromId != selfId && fromId != UserObject.ANONYMOUS && !UserObject.isDeleted(fromUser) && !fromBot ? getString(R.string.Gift2ButtonSendGift) : null, () -> {
                new GiftSheet(context, currentAccount, fromId, sheet[0]::dismiss).show();
            });
        }
        if (toId != selfId) {
            tableView.addRowUser(getString(R.string.Gift2To), currentAccount, toId, () -> {
                sheet[0].dismiss();
                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    if (UserObject.isService(toId)) return;
                    Bundle args = new Bundle();
                    if (toId > 0) {
                        args.putLong("user_id", toId);
                        if (toId == selfId) {
                            args.putBoolean("my_profile", true);
                            args.putBoolean("open_gifts", true);
                        }
                    } else {
                        args.putLong("chat_id", -toId);
                    }
                    lastFragment.presentFragment(new ProfileActivity(args));
                }
            }, null, () -> {
                new GiftSheet(context, currentAccount, toId, sheet[0]::dismiss).show();
            });
        }
        tableView.addRowDateTime(getString(R.string.StarsTransactionDate), date);
        Runnable convert = null;
        if (!out && !action.converted && action.convert_stars > 0 && within > 0) {
            convert = () -> {
                new AlertDialog.Builder(context, resourcesProvider)
                        .setTitle(getString(R.string.Gift2ConvertTitle))
                        .setMessage(AndroidUtilities.replaceTags(formatPluralString("Gift2ConvertText2", withinDays, UserObject.getForcedFirstName(user), formatPluralStringComma("Gift2ConvertStars", (int) action.convert_stars))))
                        .setPositiveButton(getString(R.string.Gift2ConvertButton), (di, w) -> {
                            final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog.showDelayed(500);
                            TL_stars.convertStarGift req = new TL_stars.convertStarGift();
                            req.msg_id = msg_id;
                            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(dialogId);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.dismissUnless(400);
                                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment == null) return;
                                if (res instanceof TLRPC.TL_boolTrue) {
                                    sheet[0].dismiss();
                                    StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
                                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(selfId);
                                    if (userFull != null) {
                                        userFull.stargifts_count = Math.max(0, userFull.stargifts_count - 1);
                                        if (userFull.stargifts_count <= 0) {
                                            userFull.flags2 &=~ 256;
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
                                                            LocaleController.formatPluralStringComma("Gift2Converted", (int) action.convert_stars)
                                                    )
                                                    .show(true);
                                        });
                                        lastFragment.presentFragment(fragment);
                                    } else {
                                        BulletinFactory.of(lastFragment)
                                                .createSimpleBulletin(
                                                        R.raw.stars_topup,
                                                        LocaleController.getString(R.string.Gift2ConvertedTitle),
                                                        LocaleController.formatPluralStringComma("Gift2Converted", (int) action.convert_stars)
                                                )
                                                .show(true);
                                    }
                                } else if (err != null) {
                                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
                                } else {
                                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(getString(R.string.UnknownError)).show(false);
                                }
                            }));
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
            };
        }
        tableView.addRow(getString(R.string.Gift2Value), replaceStarsWithPlain(TextUtils.concat("⭐️ " + LocaleController.formatNumber(gift.stars, ','), " ", convert == null ? "" : ButtonSpan.make(formatPluralStringComma("Gift2ButtonSell", (int) action.convert_stars), convert, resourcesProvider)), .8f));
        final ButtonWithCounterView button1 = new ButtonWithCounterView(context, resourcesProvider);
        final Runnable toggleShow = () -> {
            if (button1.isLoading()) return;
            button1.setLoading(true);

            TL_stars.saveStarGift req = new TL_stars.saveStarGift();
            final boolean unsave = req.unsave = action.saved;
            req.msg_id = msg_id;
            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(dialogId);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) return;
                if (res instanceof TLRPC.TL_boolTrue) {
                    sheet[0].dismiss();
                    StarsController.getInstance(currentAccount).invalidateProfileGifts(selfId);
                    BulletinFactory.of(lastFragment)
                            .createEmojiBulletin(
                                    gift.sticker,
                                    LocaleController.getString(unsave ? R.string.Gift2MadePrivateTitle : R.string.Gift2MadePublicTitle),
                                    AndroidUtilities.replaceSingleTag(LocaleController.getString(unsave ? R.string.Gift2MadePrivate : R.string.Gift2MadePublic), lastFragment instanceof ProfileActivity ? null : () -> {
                                        final Bundle args = new Bundle();
                                        args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                                        args.putBoolean("my_profile", true);
                                        args.putBoolean("open_gifts", true);
                                        final ProfileActivity profileActivity = new ProfileActivity(args);
                                        lastFragment.presentFragment(profileActivity);
                                    })
                            )
                            .show(true);
                } else if (err != null) {
                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
                }
            }));
        };
        if (!out && !action.converted) {
            tableView.addRow(getString(R.string.Gift2Visibility), getString(action.saved ? R.string.Gift2Visible : R.string.Gift2Invisible), getString(action.saved ? R.string.Gift2VisibleHide : R.string.Gift2InvisibleShow), toggleShow);
        }

        if (gift.limited) {
            addAvailabilityRow(tableView, currentAccount, gift, resourcesProvider);
        }
        if (action.message != null && !TextUtils.isEmpty(action.message.text)) {
            tableView.addFullRow(action.message.text, action.message.entities);
        }
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 12));

        if (!out && !action.converted) {
            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setLineSpacing(dp(2), 1f);
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
            textView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(action.saved ? R.string.Gift2ProfileVisible2 : R.string.Gift2ProfileInvisible), () -> {
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) return;
                sheet[0].dismiss();
                if (!(lastFragment instanceof ProfileActivity && ((ProfileActivity) lastFragment).myProfile)) {
                    final Bundle args = new Bundle();
                    args.putLong("user_id", selfId);
                    args.putBoolean("my_profile", true);
                    args.putBoolean("open_gifts", true);
                    lastFragment.presentFragment(new ProfileActivity(args));
                }
            }), true, dp(8f / 3f), dp(.66f)));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 6, 5, 16));
        }

//        if (out || action.converted) {
            button1.setText(getString(R.string.OK), false);
            linearLayout.addView(button1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            b.setCustomView(linearLayout);
            sheet[0] = b.create();
            sheet[0].useBackgroundTopPadding = false;

            button1.setOnClickListener(v -> {
                sheet[0].dismiss();
            });

            sheet[0].fixNavigationBar();
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
                sheet[0].makeAttached(fragment);
            }
            sheet[0].show();
            return sheet[0];
//        } else {
//            button1.setText(getString(action.saved ? R.string.Gift2ProfileMakeInvisible : R.string.Gift2ProfileMakeVisible), false);
//            linearLayout.addView(button1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 4));
//
//            button1.setOnClickListener(v -> toggleShow.run());
//
//            b.setCustomView(linearLayout);
//            sheet[0] = b.create();
//            sheet[0].useBackgroundTopPadding = false;
//
//            sheet[0].fixNavigationBar();
//            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
//            if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
//                sheet[0].makeAttached(fragment);
//            }
//            sheet[0].show();
//            return sheet[0];
//        }
    }

    public static BottomSheet showGiftSheet(Context context, int currentAccount, long dialogId, boolean myProfile, TL_stars.UserStarGift userGift, Theme.ResourcesProvider resourcesProvider) {
        if (userGift == null || context == null)
            return null;

        final TL_stars.StarGift gift = userGift.gift;
        if (gift == null)
            return null;

        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final boolean fromBot = UserObject.isBot(MessagesController.getInstance(currentAccount).getUser(userGift.from_id));

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        BackupImageView imageView = new BackupImageView(context);
        setGiftImage(imageView.getImageReceiver(), gift, 160);
        linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, -8, 0, 10));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(getString(myProfile ? R.string.Gift2TitleReceived : R.string.Gift2TitleProfile));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 9));

        if (!fromBot) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_color_green, resourcesProvider));
            textView.setText(replaceStarsWithPlain(LocaleController.formatNumber((int) Math.abs(Math.max(userGift.gift.convert_stars, userGift.convert_stars)), ' ') + " ⭐️", .8f));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));
        }

        int within = 0;
        if (myProfile) {
            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            textView.setLineSpacing(dp(2), 1f);
            ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
            within = MessagesController.getInstance(currentAccount).stargiftsConvertPeriodMax - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - userGift.date);
            textView.setText(TextUtils.concat(
                    AndroidUtilities.replaceTags(fromBot ? (
                                userGift.unsaved ? LocaleController.getString(R.string.Gift2Info2BotKeep) : LocaleController.getString(R.string.Gift2Info2BotRemove)
                            ) : myProfile ?
                            within <= 0 ? formatPluralStringComma("Gift2Info2Expired", (int) userGift.convert_stars) : formatPluralStringComma("Gift2Info", (int) userGift.convert_stars) :
                            formatPluralStringComma("Gift2Info2Out", (int) userGift.convert_stars, UserObject.getForcedFirstName(user))
                    ),
                    " ",
                    AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2More).replace(' ', ' '), () -> {
                        new ExplainStarsSheet(context).show();
                    }), true)
            ));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 5, 5, 4));
        }
        int withinDays = Math.max(1, within / (60 * 60 * 24));

        TableView tableView = new TableView(context, resourcesProvider);
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final long fromId = (userGift.flags & 2) != 0 && !userGift.name_hidden ? userGift.from_id : UserObject.ANONYMOUS;
        final TLRPC.User fromUser = MessagesController.getInstance(currentAccount).getUser(fromId);
        tableView.addRowUser(getString(R.string.Gift2From), currentAccount, fromId, () -> {
            sheet[0].dismiss();
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                if (UserObject.isService(fromId)) return;
                if ((userGift.flags & 8) != 0) {
                    lastFragment.presentFragment(ChatActivity.of(fromId, userGift.msg_id));
                } else {
                    Bundle args = new Bundle();
                    if (fromId > 0) {
                        args.putLong("user_id", fromId);
                        if (fromId == selfId) {
                            args.putBoolean("my_profile", true);
                            args.putBoolean("open_gifts", true);
                        }
                    } else {
                        args.putLong("chat_id", -fromId);
                    }
                    lastFragment.presentFragment(new ProfileActivity(args));
                }
            }
        }, fromId != selfId && fromId != UserObject.ANONYMOUS && !fromBot && !UserObject.isDeleted(fromUser) ? getString(R.string.Gift2ButtonSendGift) : null, () -> {
            new GiftSheet(context, currentAccount, fromId, sheet[0]::dismiss).show();
        });
        tableView.addRow(getString(R.string.StarsTransactionDate), LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterGiveawayCard().format(new Date(userGift.date * 1000L)), LocaleController.getInstance().getFormatterDay().format(new Date(userGift.date * 1000L))));
        Runnable convert = null;
        if (myProfile && (userGift.flags & 8) != 0 && (userGift.flags & 16) != 0 && (userGift.flags & 2) != 0 && within > 0) {
            convert = () -> {
                new AlertDialog.Builder(context, resourcesProvider)
                        .setTitle(getString(R.string.Gift2ConvertTitle))
                        .setMessage(AndroidUtilities.replaceTags(formatPluralString("Gift2ConvertText2", withinDays, fromUser == null || UserObject.isService(fromId) ? getString(R.string.StarsTransactionHidden) : UserObject.getForcedFirstName(fromUser), formatPluralStringComma("Gift2ConvertStars", (int) userGift.convert_stars))))
                        .setPositiveButton(getString(R.string.Gift2ConvertButton), (di, w) -> {
                            final AlertDialog progressDialog = new AlertDialog(ApplicationLoader.applicationContext, AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog.showDelayed(500);
                            TL_stars.convertStarGift req = new TL_stars.convertStarGift();
                            req.msg_id = userGift.msg_id;
                            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userGift.from_id);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                progressDialog.dismissUnless(400);
                                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment == null) return;
                                if (res instanceof TLRPC.TL_boolTrue) {
                                    sheet[0].dismiss();
                                    StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
                                    final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(selfId);
                                    if (userFull != null) {
                                        userFull.stargifts_count = Math.max(0, userFull.stargifts_count - 1);
                                        if (userFull.stargifts_count <= 0) {
                                            userFull.flags2 &=~ 256;
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
                                                            LocaleController.formatPluralStringComma("Gift2Converted", (int) userGift.convert_stars)
                                                    )
                                                    .show(true);
                                        });
                                        lastFragment.presentFragment(fragment);
                                    } else {
                                        BulletinFactory.of(lastFragment)
                                                .createSimpleBulletin(
                                                        R.raw.stars_topup,
                                                        LocaleController.getString(R.string.Gift2ConvertedTitle),
                                                        LocaleController.formatPluralStringComma("Gift2Converted", (int) userGift.convert_stars)
                                                )
                                                .show(true);
                                    }
                                } else if (err != null) {
                                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
                                } else {
                                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(getString(R.string.UnknownError)).show(false);
                                }
                            }));
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
            };
        }
        tableView.addRow(getString(R.string.Gift2Value), replaceStarsWithPlain(TextUtils.concat("⭐️ " + LocaleController.formatNumber(gift.stars, ','), " ", convert == null ? "" : ButtonSpan.make(formatPluralStringComma("Gift2ButtonSell", (int) userGift.convert_stars), convert, resourcesProvider)), .8f));

        if (gift.limited) {
            addAvailabilityRow(tableView, currentAccount, gift, resourcesProvider);
        }
        final ButtonWithCounterView button1 = new ButtonWithCounterView(context, resourcesProvider);
        final Runnable toggleShow = () -> {
            if (button1.isLoading()) return;
            button1.setLoading(true);

            TL_stars.saveStarGift req = new TL_stars.saveStarGift();
            final boolean unsave = req.unsave = !userGift.unsaved;
            req.msg_id = userGift.msg_id;
            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userGift.from_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null) return;
                if (res instanceof TLRPC.TL_boolTrue) {
                    sheet[0].dismiss();
                    StarsController.getInstance(currentAccount).invalidateProfileGifts(selfId);
                    BulletinFactory.of(lastFragment)
                            .createEmojiBulletin(
                                    gift.sticker,
                                    LocaleController.getString(unsave ? R.string.Gift2MadePrivateTitle : R.string.Gift2MadePublicTitle),
                                    AndroidUtilities.replaceSingleTag(LocaleController.getString(unsave ? R.string.Gift2MadePrivate : R.string.Gift2MadePublic), lastFragment instanceof ProfileActivity ? null : () -> {
                                        final Bundle args = new Bundle();
                                        args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                                        args.putBoolean("my_profile", true);
                                        args.putBoolean("open_gifts", true);
                                        final ProfileActivity profileActivity = new ProfileActivity(args);
                                        lastFragment.presentFragment(profileActivity);
                                    })
                            )
                            .show(true);
                } else if (err != null) {
                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(formatString(R.string.UnknownErrorCode, err.text)).show(false);
                } else {
                    BulletinFactory.of(sheet[0].topBulletinContainer, resourcesProvider).createErrorBulletin(getString(R.string.UnknownError)).show(false);
                }
                button1.setLoading(false);
            }));
        };
        if (myProfile && (userGift.flags & 8) != 0 && (userGift.flags & 2) != 0) {
            tableView.addRow(getString(R.string.Gift2Visibility), getString(!userGift.unsaved ? R.string.Gift2Visible : R.string.Gift2Invisible), getString(!userGift.unsaved ? R.string.Gift2VisibleHide : R.string.Gift2InvisibleShow), toggleShow);
        }

        if (userGift.message != null && !TextUtils.isEmpty(userGift.message.text)) {
            tableView.addFullRow(userGift.message.text, userGift.message.entities);
        }
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 12));

        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setLineSpacing(dp(2), 1f);
            textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            ((LinkSpanDrawable.LinksTextView) textView).setDisablePaddingsOffsetY(true);
            textView.setText(getString(!userGift.unsaved ? R.string.Gift2ProfileVisible : R.string.Gift2ProfileInvisible));
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 6, 5, 16));
        }

//        if (!myProfile || (userGift.flags & 8) == 0 || (userGift.flags & 2) == 0) {
            ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
            button.setText(getString(R.string.OK), false);
            linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

            b.setCustomView(linearLayout);
            sheet[0] = b.create();
            sheet[0].useBackgroundTopPadding = false;

            button.setOnClickListener(v -> {
                sheet[0].dismiss();
            });

            sheet[0].fixNavigationBar();
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
                sheet[0].makeAttached(fragment);
            }
            sheet[0].show();
            return sheet[0];
//        } else {
//            button1.setText(getString(!userGift.unsaved ? R.string.Gift2ProfileMakeInvisible : R.string.Gift2ProfileMakeVisible), false);
//            linearLayout.addView(button1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 4));
//
//            button1.setOnClickListener(v -> toggleShow.run());
//
//            b.setCustomView(linearLayout);
//            sheet[0] = b.create();
//            sheet[0].useBackgroundTopPadding = false;
//
//            sheet[0].fixNavigationBar();
//            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
//            if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
//                sheet[0].makeAttached(fragment);
//            }
//            sheet[0].show();
//            return sheet[0];
//        }
    }

    public static BottomSheet showSoldOutGiftSheet(Context context, int currentAccount, TL_stars.StarGift gift, Theme.ResourcesProvider resourcesProvider) {
        if (gift == null || context == null)
            return null;

        BottomSheet.Builder b = new BottomSheet.Builder(context, false, resourcesProvider);
        BottomSheet[] sheet = new BottomSheet[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(dp(16), dp(20), dp(16), dp(8));
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        BackupImageView imageView = new BackupImageView(context);
        setGiftImage(imageView.getImageReceiver(), gift, 160);
        linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER, 0, -8, 0, 10));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setText(getString(R.string.Gift2SoldOutSheetTitle));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        textView.setText(getString(R.string.Gift2SoldOutSheetSubtitle));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 4));

        TableView tableView = new TableView(context, resourcesProvider);

        if (gift.first_sale_date != 0) {
            tableView.addRowDateTime(getString(R.string.Gift2SoldOutSheetFirstSale), gift.first_sale_date);
        }
        if (gift.last_sale_date != 0) {
            tableView.addRowDateTime(getString(R.string.Gift2SoldOutSheetLastSale), gift.last_sale_date);
        }
        tableView.addRow(getString(R.string.Gift2SoldOutSheetValue), replaceStarsWithPlain("⭐️ " + LocaleController.formatNumber(gift.stars, ','), .8f));
        if (gift.limited) {
            addAvailabilityRow(tableView, currentAccount, gift, resourcesProvider);
        }
        linearLayout.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 17, 0, 12));

        ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.OK), false);
        linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        b.setCustomView(linearLayout);
        sheet[0] = b.create();
        sheet[0].useBackgroundTopPadding = false;

        button.setOnClickListener(v -> {
            sheet[0].dismiss();
        });

        sheet[0].fixNavigationBar();
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!AndroidUtilities.isTablet() && !AndroidUtilities.hasDialogOnTop(fragment)) {
            sheet[0].makeAttached(fragment);
        }
        sheet[0].show();
        return sheet[0];
    }

    public static void addAvailabilityRow(TableView tableView, int currentAccount, TL_stars.StarGift gift, Theme.ResourcesProvider resourcesProvider) {
        final TableRow row = tableView.addRow(getString(R.string.Gift2Availability), "");
        final TextView rowTextView = (TextView) ((TableView.TableRowContent) row.getChildAt(1)).getChildAt(0);
        final SpannableStringBuilder sb = new SpannableStringBuilder("x ");
        final LoadingSpan span = new LoadingSpan(rowTextView, dp(90), 0, resourcesProvider);
        span.setColors(
                Theme.multAlpha(rowTextView.getPaint().getColor(), .21f),
                Theme.multAlpha(rowTextView.getPaint().getColor(), .08f)
        );
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        rowTextView.setText(sb, TextView.BufferType.SPANNABLE);
        if (!gift.sold_out) {
            StarsController.getInstance(currentAccount).getStarGift(gift.id, remoteGift -> {
                if (remoteGift == null) return;
                rowTextView.setText(remoteGift.availability_remains <= 0 ? formatPluralStringComma("Gift2Availability2ValueNone", remoteGift.availability_total) : formatPluralStringComma("Gift2Availability4Value", remoteGift.availability_remains, LocaleController.formatNumber(remoteGift.availability_total, ',')));
            });
        } else {
            rowTextView.setText(gift.availability_remains <= 0 ? formatPluralStringComma("Gift2Availability2ValueNone", gift.availability_total) : formatPluralStringComma("Gift2Availability4Value", gift.availability_remains, LocaleController.formatNumber(gift.availability_total, ',')));
        }
    }

    private static DecimalFormat floatFormat;

    public static CharSequence formatStarsAmount(TL_stars.StarsAmount starsAmount) {
        return formatStarsAmount(starsAmount, 0.777f, ',');
    }

    public static CharSequence formatStarsAmount(TL_stars.StarsAmount starsAmount, float relativeSize, char symbol) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        final long amount = starsAmount.amount + (starsAmount.nanos < 0 && starsAmount.amount > 0 ? -1 : (starsAmount.nanos > 0 && starsAmount.amount < 0 ? +1 : 0));
        final boolean negative = amount < 0;
        if (starsAmount.nanos != 0) {
            ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
            if (floatFormat == null) floatFormat = new DecimalFormat("0.################");
            String str = floatFormat.format((starsAmount.nanos < 0 ? 1e9 + starsAmount.nanos : starsAmount.nanos) / 1e9d);
            int index;
            if ((index = str.indexOf(".")) >= 0) {
                int fromIndex = ssb.length();
                ssb.append(str.substring(index));
                ssb.setSpan(new RelativeSizeSpan(relativeSize), fromIndex + 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), ' '));
        }
        return ssb;
    }

    public static CharSequence formatStarsAmountShort(TL_stars.StarsAmount starsAmount) {
        return formatStarsAmountShort(starsAmount, 0.777f, ' ');
    }

    public static CharSequence formatStarsAmountShort(TL_stars.StarsAmount starsAmount, float relativeSize, char symbol) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        final long amount = starsAmount.amount + (starsAmount.nanos < 0 && starsAmount.amount > 0 ? -1 : (starsAmount.nanos > 0 && starsAmount.amount < 0 ? +1 : 0));
        final boolean negative = amount < 0;
        if (Math.abs(amount) <= 1000 && starsAmount.nanos != 0) {
            ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
            if (floatFormat == null) floatFormat = new DecimalFormat("0.################");
            String str = floatFormat.format((starsAmount.nanos < 0 ? 1e9 + starsAmount.nanos : starsAmount.nanos) / 1e9d);
            int index;
            if ((index = str.indexOf(".")) >= 0) {
                int fromIndex = ssb.length();
                String part = str.substring(index);
                if (part.length() > 1) {
                    ssb.append(part.substring(0, Math.min(part.length(), 3)));
                    ssb.setSpan(new RelativeSizeSpan(relativeSize), fromIndex + 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } else if (starsAmount.amount <= 1000) {
            ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
        } else {
            ssb.append((negative ? "-" : "") + AndroidUtilities.formatWholeNumber((int) Math.abs(amount), 0));
        }
        return ssb;
    }

    public static CharSequence formatStarsAmountString(TL_stars.StarsAmount starsAmount) {
        return formatStarsAmountString(starsAmount, 0.777f, ',');
    }

    public static CharSequence formatStarsAmountString(TL_stars.StarsAmount starsAmount, float relativeSize, char symbol) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        final long amount = starsAmount.amount + (starsAmount.nanos < 0 && starsAmount.amount > 0 ? -1 : (starsAmount.nanos > 0 && starsAmount.amount < 0 ? +1 : 0));
        final boolean negative = amount < 0;
        if (starsAmount.nanos != 0) {
            ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
            if (floatFormat == null) floatFormat = new DecimalFormat("0.################");
            String str = floatFormat.format((starsAmount.nanos < 0 ? 1e9 + starsAmount.nanos : starsAmount.nanos) / 1e9d);
            int index;
            if ((index = str.indexOf(".")) >= 0) {
                int fromIndex = ssb.length();
                ssb.append(str.substring(index));
                ssb.setSpan(new RelativeSizeSpan(relativeSize), fromIndex + 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append(" ").append(getString(R.string.StarsNano));
        } else {
            ssb.append(formatPluralStringComma("Stars", (int) starsAmount.amount));
        }
        return ssb;
    }
}

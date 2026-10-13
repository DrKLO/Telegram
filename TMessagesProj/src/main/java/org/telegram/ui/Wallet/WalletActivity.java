package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.UpdateAppearance;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_toncenter;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EdgeEffectTrackerFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LinkManager;
import org.telegram.ui.PasscodeActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.ui.Stories.UploadingDotsSpannable;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.TwoStepVerificationSetupActivity;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.iv.RichEditor;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class WalletActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int TAB_TRANSACTIONS = 0;
    private static final int TAB_COLLECTIBLES = 1;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 44;

    private static final int WALLET_BLUE = 0xFF129EEA;
    private static final int WALLET_SHEET_BLUE = 0xFF088FEE;
    private static final int WALLET_BLUE_LIGHT = 0xFF45B8F7;
    private static Typeface walletAddressTypeface;

    private WalletCardComponent card;
    private View cardShadowView;
    private int cardItemPosition;
    private FrameLayout cardLayer;
    private int maxCardHeight;
    private boolean topOverscrollVisible;
    private final Runnable updateCardOverscrollTilt = new Runnable() {
        @Override
        public void run() {
            if (listView == null || card == null) return;
            updateCardTilt();
            if (topOverscrollVisible) {
                listView.postOnAnimation(this);
            }
        }
    };
    private final EdgeEffectTrackerFactory.OnEdgeEffectListener cardOverscrollListener = (direction, visible) -> {
        if (direction != RecyclerView.EdgeEffectFactory.DIRECTION_TOP && direction != RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM || listView == null) return;
        topOverscrollVisible = visible;
        listView.removeCallbacks(updateCardOverscrollTilt);
        updateCardOverscrollTilt.run();
    };
    private boolean use2DCard = false;
    private FrameLayout actionBarBalance;
    private View actionBarBalanceAnchor;
    private FrameLayout balanceOverlay;
    private boolean balanceInOverlay;
    private boolean balanceReturningToCard;
    private final Rect balanceCardBounds = new Rect();
    private final Matrix balanceFaceTransform = new Matrix();
    private final Matrix balanceDrawTransform = new Matrix();
    private final Matrix nextBalanceDrawTransform = new Matrix();
    private final float[] balanceLocalCorners = new float[8];
    private final float[] balanceProjectedCorners = new float[8];
    private final float[] balanceTransitionCorners = new float[8];
    private final int[] balanceTargetLocation = new int[2];
    private final int[] balanceOverlayLocation = new int[2];
    private final int[] cardListLocation = new int[2];
    private final int[] cardLayerLocation = new int[2];
    private FrameLayout walletTopPanelContainer;
    private DialogsActivityTopPanelLayout walletTopPanel;
    private FrameLayout waltButton;
    private TextView waltButtonText;
    private FrameLayout oldWalletsButton;
    private TextView oldWalletsButtonText;
    private FrameLayout gramEarningsButton;
    private TextView gramEarningsButtonText;
    private LinkSpanDrawable.LinksTextView tosText;
    private View tosSpacer;
    private boolean termsVisible;
    private FrameLayout protectAccountContainer;
    private FrameLayout protectAccountButton;

    private int tab = TAB_TRANSACTIONS;
    private boolean hasCollectibles;
    private ViewPagerFixed.TabsView historyTabs;
    private FrameLayout historyTabsContainer;
    private float historyTabsVisibility;
    private ValueAnimator historyTabsAnimator;
    private LinearLayout historyLayout;
    private boolean showingLearnCells;
    private ViewPagerFixed historyPager;
    private WalletHeaderSnapHelper headerSnapHelper;
    private final UniversalRecyclerView[] historyLists = new UniversalRecyclerView[2];

    private LinearLayout balanceLayout;
    private AnimatedTextView balanceView;
    private AnimatedTextView usdBalanceView;
    private ColoredImageSpan balanceGramIconSpan;
    private BalanceLabelSpan balanceGramLabelSpan;
    private float balanceTransitionProgress;

    private LinearLayout buttonsContainer;
    private ButtonWithCounterView addFundsButton;
    private ButtonWithCounterView sendButton;

    private GramWallet.TransactionsList transactionsList;
    private GramWallet.CollectiblesList nftsList;
    private static final int RETRY_NFTS = -10001;
    private final Runnable onNftsChanged = () -> {
        final boolean hasItems = nftsList != null && !nftsList.collectibles.isEmpty();
        if (hasCollectibles != hasItems) setHasCollectibles(hasItems);
        if (listView != null && listView.adapter != null) updateHistory(true);
    };

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.WalletTitle);
    }

    @Override
    public View createView(Context context) {
        cancelHistoryTabsAnimation();
        stopCardOverscrollTracking();
        if (headerSnapHelper != null) {
            headerSnapHelper.attachToRecyclerView(null);
        }
        cardShadowView = null;
        walletTopPanel = null;
        walletTopPanelContainer = null;
        historyLayout = null;
        historyPager = null;
        historyLists[TAB_TRANSACTIONS] = null;
        historyLists[TAB_COLLECTIBLES] = null;
        hasCollectibles = nftsList != null && !nftsList.collectibles.isEmpty();
        if (!hasCollectibles) tab = TAB_TRANSACTIONS;
        final View content = super.createView(context);
        final WalletNestedLayout nestedLayout = new WalletNestedLayout(context);
        nestedLayout.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = nestedLayout;

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        actionBar.setCastShadows(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setClipChildren(false);
        actionBar.setClipToPadding(false);

        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(2, R.drawable.scan_qr).setOnClickListener(v -> {
            openWalletQrScanner();
        });
        menu.addItem(1, R.drawable.ic_ab_other).setOnClickListener(v -> {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            final ItemOptions o = ItemOptions.makeOptions(WalletActivity.this, v, true);
            o.translate(0, -dp(48));
            o.setDimAlpha(0);
            o.setGravity(Gravity.RIGHT);

            final ItemOptions oc = o.makeSwipeback();

            oc.add(R.drawable.ic_ab_back, LocaleController.getString(R.string.WalletBack), o::closeSwipeback);
            oc.addGap();

            final ScrollView scrollView = new ScrollView(context);
            final LinearLayout currenciesLayout = new LinearLayout(context);
            currenciesLayout.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(currenciesLayout);
            final Runnable layoutCurrencies = () -> {
                currenciesLayout.removeAllViews();
                final String currency = wallet.currencies.getCurrency();
                final TL_wallet.currencyRates rates = wallet.currencies.getCurrencies();
                if (rates == null) {
                    for (int i = 0; i < 3; ++i) {
                        final FlickerLoadingView flickerView = new FlickerLoadingView(getContext(), getResourceProvider());
                        flickerView.setIsSingleCell(true);
                        flickerView.setViewType(FlickerLoadingView.STAR_TIER);
                        currenciesLayout.addView(flickerView, LayoutHelper.createLinear(200, LayoutHelper.WRAP_CONTENT));
                    }
                    return;
                }

                final Utilities.Callback<TL_wallet.currencyRate> addCurrency = rate -> {
                    if (rate == null) return;
                    final ActionBarMenuSubItem button = new ActionBarMenuSubItem(getContext(), 2, false, false, resourceProvider);
                    button.setPadding(dp(18), 0, dp(18), 0);
                    button.setText(rate.currency);
                    button.setSubtext(!TextUtils.isEmpty(rate.translatedTitle) ? rate.translatedTitle : rate.title);
                    final boolean checked = TextUtils.equals(currency, rate.currency);
                    button.subtextView.setPadding(0, 0, dp(checked ? 42 : 0), 0);
                    button.setChecked(checked);
                    button.setOnClickListener(b -> {
                        wallet.currencies.setCurrency(rate.currency);
                        o.dismiss();
                    });
                    currenciesLayout.addView(button, LayoutHelper.createLinear(200, LayoutHelper.WRAP_CONTENT));
                };

                final ArrayList<TL_wallet.currencyRate> hintCurrencies = wallet.currencies.getHintCurrencies();
                final HashSet<String> hintedCurrencyCodes = new HashSet<>();
                for (int i = 0; i < hintCurrencies.size(); ++i) {
                    final TL_wallet.currencyRate rate = hintCurrencies.get(i);
                    if (rate != null && hintedCurrencyCodes.add(rate.currency)) {
                        addCurrency.run(rate);
                    }
                }

                if (!hintedCurrencyCodes.isEmpty() && !rates.rates.isEmpty()) {
                    currenciesLayout.addView(
                            new ActionBarPopupWindow.GapView(getContext(), resourceProvider),
                            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8)
                    );
                }

                for (int i = 0; i < rates.rates.size(); ++i) {
                    final TL_wallet.currencyRate rate = rates.rates.get(i);
                    if (rate != null && !hintedCurrencyCodes.contains(rate.currency)) {
                        addCurrency.run(rate);
                    }
                }
            };
            oc.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 350));

            final ActionBarMenuSubItem currencyItem = o.add(R.drawable.wallet_globe, LocaleController.getString(R.string.WalletCurrency), () -> {
                layoutCurrencies.run();
                o.openSwipeback(oc);
            }).getLast();
            CharSequence currencyTitle = wallet.currencies.getCurrencyTitle();
            if (TextUtils.isEmpty(currencyTitle)) {
                currencyItem.setSubtext("USD");
                SpannableStringBuilder ssb = new SpannableStringBuilder(getString(R.string.Loading));
                ssb.setSpan(new LoadingSpan(currencyItem.subtextView, dp(75)), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                currencyTitle = ssb;
            }
            currencyItem.setSubtext(currencyTitle);
            final boolean[] loaded = new boolean[] { wallet.currencies.getCurrencies() != null };
            final Runnable currenciesLoaded = () -> {
                final TL_wallet.currencyRates rates = wallet.currencies.getCurrencies();
                if (rates == null || loaded[0]) return;
                currencyItem.setSubtext(wallet.currencies.getCurrencyTitle());
                layoutCurrencies.run();
                loaded[0] = true;
            };
            updateRunnables.add(currenciesLoaded);
            layoutCurrencies.run();
            o.add(R.drawable.wallet_lock, getString(R.string.Passcode), () -> {
                o.dismiss();
                presentFragment(PasscodeActivity.determineOpenFragment());
            })
            .add(R.drawable.wallet_key, LocaleController.getString(R.string.WalletKeysAndBackup), () -> {
                o.dismiss();
                presentFragment(new WalletKeysBackupFragment());
            })
            .addIf(!wallet.tonConnect.sessions.isEmpty(), R.drawable.wallet_apps, LocaleController.getString(R.string.WalletConnectedApps), () -> {
                o.dismiss();
                presentFragment(new WalletTonConnectSessionsFragment());
            })
            .addGap()
            .add(R.drawable.wallet_help, LocaleController.getString(R.string.WalletWhatIsWallet), () -> {
                o.dismiss();
                openWalletHowItWorksAlert(getContext(), getResourceProvider());
            });
            if (BuildVars.DEBUG_VERSION) {
                o.addGap()
                 .add(
                    use2DCard ? LocaleController.getString(R.string.WalletCard3D) : LocaleController.getString(R.string.WalletCard2D),
                    () -> {
                        o.dismiss();
                        setUse2DCard(!use2DCard);
                    }
                 );
            }
            o.setOnDismiss(() -> {
                updateRunnables.remove(currenciesLoaded);
            });
            o.show();
        });

        walletTopPanelContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setMeasuredDimension(getMeasuredWidth(), (int) Math.ceil(
                        walletTopPanel.getAnimatedHeightWithPadding(dp(4))));
            }
        };
        walletTopPanelContainer.setClipToPadding(false);
        walletTopPanelContainer.setClipChildren(false);
        walletTopPanelContainer.setPadding(dp(16), 0, dp(16), dp(4));
        walletTopPanel = new DialogsActivityTopPanelLayout(context) {
            private final Drawable solidBackground = RichEditor.withShadow(
                    Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_windowBackgroundWhite)),
                    dpf2(2.67f), 0, dpf2(0.67f), 0x21000000);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final int height = (int) Math.ceil(getMetadata().getTotalHeight());
                if (height > 0) {
                    solidBackground.setBounds(0, 0, getWidth(), height);
                    solidBackground.setAlpha((int) (255 * getMetadata().getTotalVisibility()));
                    solidBackground.draw(canvas);
                }
                super.dispatchDraw(canvas);
            }
        };
        walletTopPanel.setDefaultRadiusDp(22);
        walletTopPanel.setOnAnimatedHeightChangedListener(() -> walletTopPanelContainer.post(() -> {
            if (!walletTopPanelContainer.isAttachedToWindow()) {
                return;
            }
            if (!listView.canScrollVertically(-1)) {
                listView.layoutManager.scrollToPositionWithOffset(0, 0);
            }
            walletTopPanelContainer.requestLayout();
        }));
        waltButtonText = new TextView(context);
        waltButtonText.setText(LocaleController.getString(R.string.WalletWaltFunds));
        waltButton = createWalletNoticeRow(waltButtonText,
                () -> Browser.openUrl(getContext(), GramWallet.getInstance(currentAccount).getWaltUrl()));
        oldWalletsButtonText = new TextView(context);
        oldWalletsButton = createWalletNoticeRow(oldWalletsButtonText,
                () -> presentFragment(new WalletKeysBackupFragment()));
        gramEarningsButtonText = new TextView(context);
        gramEarningsButton = createWalletNoticeRow(gramEarningsButtonText,
                () -> presentFragment(new TONIntroActivity()));
        walletTopPanelContainer.addView(walletTopPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        tosText = new LinkSpanDrawable.LinksTextView(context);
        termsVisible = false;
        tosText.setAlpha(0.0f);
        tosText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        tosText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tosText.setGravity(Gravity.CENTER);
        tosText.setText(AndroidUtilities.replaceSingleLink(LocaleController.getString(R.string.WalletTermsOfService), Theme.getColor(Theme.key_chat_messageLinkIn, resourceProvider), () -> {
            Browser.openUrlInSystemBrowser(context, getString(R.string.TermsOfServiceUrl));
        }));
        tosText.setVisibility(View.INVISIBLE);
        if (content instanceof FrameLayout) {
            ((FrameLayout) content).addView(tosText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 12, 0, 12, 24));
        }
        tosSpacer = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                tosText.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(24)), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                // Reserve the footer and a gap above it; list padding already includes the bottom inset.
                setMeasuredDimension(width, tosText.getMeasuredHeight() + dp(36));
            }
        };
        tosSpacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        protectAccountContainer = new FrameLayout(context);
        protectAccountButton = new FrameLayout(context);
        protectAccountButton.setBackground(Theme.createRadSelectorDrawable(
            getThemedColor(Theme.key_windowBackgroundWhite),
            Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)),
            16, 16
        ));
        protectAccountContainer.addView(protectAccountButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 12, 0, 12, 12));
        final ImageView protectAccountIcon = new ImageView(context);
        final float rad = dpf2(10);
        final ShapeDrawable iconBg = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        iconBg.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(
                    0, 0, 0, height,
                    IconBackgroundColors.RED.top, IconBackgroundColors.RED.bottom,
                    Shader.TileMode.CLAMP
                );
            }
        });
        protectAccountIcon.setBackground(iconBg);
        protectAccountIcon.setScaleType(ImageView.ScaleType.CENTER);
        protectAccountIcon.setImageResource(R.drawable.wallet_protect);
        protectAccountButton.addView(protectAccountIcon, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 11, 16, 11));
        final TextView protectAccountText = new TextView(context);
        protectAccountText.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourceProvider));
        protectAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        protectAccountText.setText(LocaleController.getString(R.string.WalletProtectAccount));
        protectAccountButton.addView(protectAccountText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 58, 0, 32, 0));
        protectAccountButton.setOnClickListener(v -> {
            presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null));
        });
        ScaleStateListAnimator.apply(protectAccountButton, .02f, 1.2f);

        actionBarBalance = new FrameLayout(context);
        actionBarBalance.setClipChildren(false);
        actionBarBalance.setClipToPadding(false);
        actionBar.addView(actionBarBalance, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 64, 0, 96, 0));

        actionBarBalanceAnchor = new View(context);
        actionBarBalance.addView(actionBarBalanceAnchor, LayoutHelper.createFrame(1, 1, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        balanceInOverlay = false;
        balanceReturningToCard = false;
        balanceCardBounds.setEmpty();
        balanceDrawTransform.reset();
        balanceOverlay = new FrameLayout(context) {
            private final ViewTreeObserver.OnPreDrawListener updateProjection = () -> {
                if (balanceInOverlay || balanceReturningToCard) {
                    updateBalanceOverlayTransform();
                }
                // Its real parent now receives animated-text invalidations.
                if (balanceReturningToCard) invalidate();
                return true;
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                getViewTreeObserver().addOnPreDrawListener(updateProjection);
            }

            @Override
            protected void onDetachedFromWindow() {
                getViewTreeObserver().removeOnPreDrawListener(updateProjection);
                super.onDetachedFromWindow();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (balanceReturningToCard) {
                    // The views already belong to the card again, but its
                    // displayed GL buffer may still omit them. Bridge only
                    // until that exact content revision has been presented.
                    final int save = canvas.save();
                    canvas.concat(balanceDrawTransform);
                    canvas.translate(-balanceLayout.getLeft(), -balanceLayout.getTop());
                    super.drawChild(canvas, balanceLayout, getDrawingTime());
                    canvas.restoreToCount(save);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child != balanceLayout || !balanceInOverlay) {
                    return super.drawChild(canvas, child, drawingTime);
                }
                final int save = canvas.save();
                canvas.concat(balanceDrawTransform);
                final boolean result = super.drawChild(canvas, child, drawingTime);
                canvas.restoreToCount(save);
                return result;
            }
        };
        balanceOverlay.setClipChildren(false);
        balanceOverlay.setClipToPadding(false);

        card = new WalletCardComponent(context, use2DCard) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int unconstrainedHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                super.onMeasure(widthMeasureSpec, unconstrainedHeight);
                if (maxCardHeight > 0 && getMeasuredHeight() > maxCardHeight) {
                    // Shrink the face uniformly, keeping the component's padding unchanged.
                    final int horizontalPadding = getPaddingLeft() + getPaddingRight();
                    final int verticalPadding = getPaddingTop() + getPaddingBottom();
                    final float scale = Math.max(0, maxCardHeight - verticalPadding)
                            / (float) Math.max(1, getMeasuredHeight() - verticalPadding);
                    final int width = horizontalPadding
                            + (int) ((getMeasuredWidth() - horizontalPadding) * scale);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), unconstrainedHeight);
                }
            }
        };
        card.setDiamondOnCard(true);
        card.setCardOnClickListener(v -> openWalletQRAlert(getContext(), currentAccount, getResourceProvider()));
        listView.addEdgeEffectListener(cardOverscrollListener);
        cardShadowView = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Keep a full-width row with the real card's height, including padding, in either mode.
                card.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), card.getMeasuredHeight());
            }
        };
        cardShadowView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cardLayer = new FrameLayout(context) {
            private View lastCardItem;
            private float lastCardY;
            private int lastCardHeight;
            private final ViewTreeObserver.OnPreDrawListener updateCardPosition = () -> {
                updateTermsVisibility();
                final View item = listView.layoutManager.findViewByPosition(cardItemPosition);
                final float y = item == null ? 0 : item.getY();
                final int height = item == null ? 0 : item.getHeight();
                if (item != lastCardItem || y != lastCardY || height != lastCardHeight) {
                    lastCardItem = item;
                    lastCardY = y;
                    lastCardHeight = height;
                    updateBalancePosition();
                }
                return true;
            };

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                getViewTreeObserver().addOnPreDrawListener(updateCardPosition);
            }

            @Override
            protected void onDetachedFromWindow() {
                getViewTreeObserver().removeOnPreDrawListener(updateCardPosition);
                super.onDetachedFromWindow();
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateBalancePosition();
            }
        };
        cardLayer.setClipChildren(false);
        cardLayer.setClipToPadding(false);
        cardLayer.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        nestedLayout.addView(cardLayer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setClipChildren(false);
        listView.setClipToPadding(false);

        balanceLayout = card.getBalanceLayout();
        balanceView = card.getBalanceView();
        usdBalanceView = card.getUsdBalanceView();
        card.setCardHolder(UserObject.getUserName(getUserConfig().getCurrentUser()).toUpperCase());

        buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonsContainer.setPadding(dp(16), 0, dp(16), dp(12));

        addFundsButton = new ButtonWithCounterView(context, resourceProvider).setRound();
        addFundsButton.setText(LocaleController.getString(R.string.WalletAddFunds));
        buttonsContainer.addView(addFundsButton, LayoutHelper.createLinear(0, 48, 1.0f, 0, 0, 4, 0));
        addFundsButton.setOnClickListener(v -> {
            openWalletQRAlert(getContext(), currentAccount, getResourceProvider());
        });

        sendButton = new ButtonWithCounterView(context, resourceProvider).setRound();
        sendButton.setText(LocaleController.getString(R.string.WalletSend));
        sendButton.setEnabled(GramWallet.getInstance(currentAccount).canSend());
        sendButton.setOnClickListener(v -> {
            final GramWallet wallet = GramWallet.getInstance(currentAccount);
            if (sendButton.isLoading()) return;
            sendButton.setLoading(true);
            wallet.makeSureSecretPhraseBackedUpLocally(false, false, error -> {
                sendButton.setLoading(false);
                if ("NO_LOCAL_BACKUP".equalsIgnoreCase(error)) {
                    presentFragment(new WalletImportFragment().setMatchPublicKey(wallet.getPublicKey()));
                    return;
                } else if (error != null) {
                    BulletinFactory.of(WalletActivity.this).showForError(error);
                    return;
                }
                presentFragment(new WalletSendSelectSender());
            });
        });
        buttonsContainer.addView(sendButton, LayoutHelper.createLinear(0, 48, 1.0f, 4, 0, 0, 0));

        transactionsList = GramWallet.getInstance(currentAccount).getTransactions();
        listView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        createHistoryLayout(context);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0 && recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING
                        && headerSnapHelper != null) {
                    headerSnapHelper.onUserScroll();
                }
                updateBalancePosition();
            }
        });

        listView.adapter.setApplyBackground(false);
        listView.adapter.update(false);
        headerSnapHelper = new WalletHeaderSnapHelper();
        headerSnapHelper.attachToRecyclerView(listView);

        update();
        transactionsList.load();

        actionBar.post(this::attachBalanceOverlay);
        listView.post(this::updateBalancePosition);

        return fragmentView;
    }

    private void updateCardTilt() {
        if (listView == null || card == null) return;
        final float topDistance = listView.getEdgeEffectDistance(RecyclerView.EdgeEffectFactory.DIRECTION_TOP);
        final float bottomDistance = listView.getEdgeEffectDistance(RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM);
        final float overscrollTilt = -Math.min(10.0f, topDistance * 40.0f) + Math.min(10.0f, bottomDistance * 40.0f);
        card.setAdditionalTilt(overscrollTilt + Math.max(0, 90f * (float) Math.sqrt(Utilities.clamp01(balanceTransitionProgress)) - 10 + 10 * balanceTransitionProgress));
        card.setUseGyroscope(1.0f - (float) Math.pow(balanceTransitionProgress, 0.33f));
    }

    private void stopCardOverscrollTracking() {
        topOverscrollVisible = false;
        if (listView != null) {
            listView.removeEdgeEffectListener(cardOverscrollListener);
            listView.removeCallbacks(updateCardOverscrollTilt);
        }
        if (card != null) {
            card.setAdditionalTilt(0.0f);
        }
    }

    private void createHistoryLayout(Context context) {
        historyLayout = new LinearLayout(context);
        historyLayout.setOrientation(LinearLayout.VERTICAL);
        historyLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        historyPager = new ViewPagerFixed(context, resourceProvider) {
            @Override
            protected void onTabPageSelected(int position) {
                tab = position;
                if (position == TAB_COLLECTIBLES) historyPager.post(WalletActivity.this::loadMoreNfts);
            }

            @Override
            protected int tabMarginDp() {
                return 12;
            }
        };
        historyPager.setAllowDisallowInterceptTouch(false);
        historyPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return hasCollectibles ? 2 : 1;
            }
            @Override
            public CharSequence getItemTitle(int position) {
                return position == TAB_TRANSACTIONS ? LocaleController.getString(R.string.WalletTransactions) : LocaleController.getString(R.string.WalletCollectibles);
            }
            @Override
            public int getItemViewType(int position) {
                return position;
            }
            @Override
            public View createView(int viewType) {
                if (historyLists[viewType] != null) {
                    return historyLists[viewType];
                }
                final UniversalRecyclerView page = new UniversalRecyclerView(WalletActivity.this,
                        (items, adapter) -> {
                            if (viewType == TAB_TRANSACTIONS) {
                                fillTransactions(items, adapter);
                            } else {
                                fillCollectibles(items, adapter);
                            }
                        }, WalletActivity.this::onClick, WalletActivity.this::onLongClick) {
                    @Override
                    protected boolean shouldClipChildToSection(View child) {
                        return !(child instanceof TransactionCell) || !((TransactionCell) child).isRaised();
                    }
                };
                page.setFocusableInTouchMode(false);
                page.setClipChildren(false);
                page.setClipToPadding(false);
                page.setSections();
                page.adapter.setApplyBackground(false);
                page.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        if (viewType == TAB_TRANSACTIONS) {
                            loadMoreTransactions();
                        } else {
                            // Loading publishes state immediately; defer adapter updates until after layout.
                            recyclerView.post(WalletActivity.this::loadMoreNfts);
                        }
                    }
                });
                historyLists[viewType] = page;
                return page;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                updateHistoryPage((UniversalRecyclerView) view, false);
            }
        });
        historyTabs = historyPager.createTabsView(true, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE);
        historyTabs.setColors(Theme.key_featuredStickers_addButton,
                Theme.key_featuredStickers_addButton, Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector, Theme.key_windowBackgroundWhite);
        historyTabs.setBackground(Theme.createRoundRectDrawable(dp(18),
                getThemedColor(Theme.key_windowBackgroundWhite)));
        historyTabsContainer = new FrameLayout(context) {
            private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                textPaint.setTextSize(dp(14));
                textPaint.setTypeface(AndroidUtilities.bold());
                // Match the compact pill design while the pager owns selection and animation.
                int width = dp(14);
                for (int i = 0; i < 2; i++) {
                    width += (int) Math.ceil(textPaint.measureText(
                            i == TAB_TRANSACTIONS ? LocaleController.getString(R.string.WalletTransactions) : LocaleController.getString(R.string.WalletCollectibles))) + dp(24);
                }
                historyTabs.getLayoutParams().width = Math.min(width,
                        Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - dp(32)));
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        historyTabsContainer.setPadding(0, 0, 0, dp(12));
        historyTabsContainer.addView(historyTabs, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                36, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        historyPager.setPosition(tab);
        historyLayout.addView(historyTabsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, -6));
        historyLayout.addView(historyPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));
        setHasCollectibles(hasCollectibles);
    }

    public void showTransactions() {
        tab = TAB_TRANSACTIONS;
        if (historyPager != null) historyPager.setPosition(TAB_TRANSACTIONS);
    }

    void prepareSendLanding(TL_wallet.walletTransaction transaction) {
        showTransactions();
        updateHistory(false);
        if (listView == null) return;
        listView.stopScroll();
        listView.layoutManager.scrollToPositionWithOffset(0, 0);
        UniversalRecyclerView page = historyLists[TAB_TRANSACTIONS];
        if (page != null) {
            page.stopScroll();
            page.adapter.update(false);
            for (int i = 0; i < page.adapter.getItemCount(); i++) {
                UItem item = page.adapter.getItem(i);
                if (item != null && item.object instanceof TL_wallet.walletTransaction
                        && (TransactionCell.Factory.sourceOf((TL_wallet.walletTransaction) item.object) == transaction
                        || GramWallet.transactionMatches((TL_wallet.walletTransaction) item.object, transaction))) {
                    page.layoutManager.scrollToPositionWithOffset(i, 0);
                    break;
                }
            }
        }
    }

    TransactionCell findSendLanding(TL_wallet.walletTransaction transaction) {
        UniversalRecyclerView page = historyLists[TAB_TRANSACTIONS];
        if (page == null) return null;
        for (int i = 0; i < page.getChildCount(); i++) {
            View child = page.getChildAt(i);
            if (child instanceof TransactionCell && ((TransactionCell) child).matches(transaction)) {
                return (TransactionCell) child;
            }
        }
        return null;
    }

    private void setHasCollectibles(boolean hasCollectibles) {
        this.hasCollectibles = hasCollectibles;
        if (!hasCollectibles) {
            tab = TAB_TRANSACTIONS;
        }
        if (historyPager == null || historyTabsContainer == null) return;
        if (!hasCollectibles) {
            historyPager.setPosition(TAB_TRANSACTIONS);
        }
        cancelHistoryTabsAnimation();
        final float targetVisibility = hasCollectibles ? 1.0f : 0.0f;
        final boolean animated = historyLayout.isAttachedToWindow() && historyLayout.isLaidOut();
        if (hasCollectibles) historyPager.fillTabs(false);
        if (animated && historyTabsVisibility != targetVisibility) {
            historyTabsContainer.setVisibility(View.VISIBLE);
            historyTabsAnimator = ValueAnimator.ofFloat(historyTabsVisibility, targetVisibility);
            historyTabsAnimator.setDuration(250);
            historyTabsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            historyTabsAnimator.addUpdateListener(animation -> setHistoryTabsVisibility((float) animation.getAnimatedValue()));
            historyTabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (historyTabsAnimator != animation) return;
                    historyTabsAnimator = null;
                    setHistoryTabsVisibility(targetVisibility);
                    historyPager.fillTabs(false);
                }
            });
            historyTabsAnimator.start();
        } else {
            setHistoryTabsVisibility(targetVisibility);
            historyPager.fillTabs(false);
        }
        if (listView != null && showingLearnCells != shouldShowLearnCells()) {
            updateHistory(false);
        }
    }

    private void setHistoryTabsVisibility(float visibility) {
        historyTabsVisibility = visibility;
        historyTabsContainer.setVisibility(visibility > 0 || historyTabsAnimator != null ? View.VISIBLE : View.GONE);
        historyTabsContainer.setAlpha(visibility);
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) historyTabsContainer.getLayoutParams();
        params.height = Math.round(dp(48) * visibility);
        params.bottomMargin = -Math.round(dp(6) * visibility);
        historyTabsContainer.setLayoutParams(params);
    }

    private void cancelHistoryTabsAnimation() {
        if (historyTabsAnimator == null) return;
        final ValueAnimator animator = historyTabsAnimator;
        historyTabsAnimator = null;
        animator.cancel();
    }

    private boolean shouldShowLearnCells() {
        return !hasCollectibles && transactionsList != null
                && transactionsList.allTransactions.isEmpty()
                && !transactionsList.isLoading()
                && transactionsList.isEndReached();
    }

    private void updateHistory(boolean animated) {
        final boolean showLearnCells = shouldShowLearnCells();
        if (listView != null && showingLearnCells != showLearnCells) {
            showingLearnCells = showLearnCells;
            listView.stopScroll();
            for (UniversalRecyclerView page : historyLists) {
                if (page != null) page.stopScroll();
            }
            listView.adapter.update(false);
            listView.layoutManager.scrollToPositionWithOffset(0, 0);
            listView.post(this::updateBalancePosition);
        }
        if (!showLearnCells) {
            for (UniversalRecyclerView page : historyLists) {
                if (page != null) {
                    updateHistoryPage(page, animated);
                }
            }
            if (historyLayout != null) {
                historyLayout.post(this::loadMoreTransactions);
                historyLayout.post(this::loadMoreNfts);
            }
        }
    }

    private void updateHistoryPage(UniversalRecyclerView page, boolean animated) {
        final boolean wasAtTop = !page.canScrollVertically(-1);
        page.adapter.update(animated);
        page.invalidateItemDecorations();
        if (wasAtTop) {
            page.layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private void loadMoreTransactions() {
        final UniversalRecyclerView page = historyLists[TAB_TRANSACTIONS];
        if (page != null && page.isAttachedToWindow() && transactionsList != null
                && !transactionsList.isLoading() && !transactionsList.isEndReached()
                && page.layoutManager.findLastVisibleItemPosition() >= page.adapter.getItemCount() - 3) {
            transactionsList.load();
        }
    }

    private void loadMoreNfts() {
        final UniversalRecyclerView page = historyLists[TAB_COLLECTIBLES];
        if (tab == TAB_COLLECTIBLES && page != null && page.isAttachedToWindow()
                && nftsList != null && !nftsList.isLoading() && !nftsList.isEndReached()
                && nftsList.getError() == null
                && page.layoutManager.findLastVisibleItemPosition() >= page.adapter.getItemCount() - 3) {
            nftsList.load();
        }
    }

    private boolean hasAttachedHistoryPager() {
        return !showingLearnCells && historyLayout != null && historyLayout.isAttachedToWindow()
                && historyLayout.getParent() == listView.layoutManager.findViewByPosition(listView.adapter.getItemCount() - 1);
    }

    private UniversalRecyclerView getCurrentHistoryList() {
        if (!hasAttachedHistoryPager()) return null;
        final View page = historyPager == null ? null : historyPager.getCurrentView();
        return page instanceof UniversalRecyclerView ? (UniversalRecyclerView) page : null;
    }

    @Override
    public void onBeginSlide() {
        if (headerSnapHelper != null) {
            headerSnapHelper.cancelGesture();
        }
        if (listView != null) listView.stopScroll();
        final UniversalRecyclerView inner = getCurrentHistoryList();
        if (inner != null) inner.stopScroll();
        super.onBeginSlide();
    }

    private class WalletHeaderSnapHelper extends SnapHelper {
        private UniversalRecyclerView recyclerView;
        private boolean userSnapPending;
        private boolean touchInProgress;
        private boolean verticalGesture;
        private float touchStartX, touchStartY;

        void cancelGesture() {
            touchInProgress = false;
            verticalGesture = false;
            userSnapPending = false;
            if (recyclerView != null) recyclerView.removeCallbacks(snapWhenIdle);
        }

        void onTouchEvent(MotionEvent event) {
            final int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                cancelGesture();
                touchInProgress = true;
                touchStartX = event.getX();
                touchStartY = event.getY();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                // Navigation can cancel the list while the finger is still down.
                // Clear intent before RecyclerView becomes idle during cancellation.
                cancelGesture();
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                final float dx = Math.abs(event.getX() - touchStartX);
                final float dy = Math.abs(event.getY() - touchStartY);
                verticalGesture = dy > ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop()
                        && dy > dx * 1.5f;
                if (!verticalGesture) userSnapPending = false;
                if (action == MotionEvent.ACTION_UP) touchInProgress = false;
            }
        }

        void onUserScroll() {
            if (verticalGesture) userSnapPending = true;
        }

        @Override
        public boolean onFling(int velocityX, int velocityY) {
            if (!verticalGesture) return false;
            onUserScroll();
            return super.onFling(velocityX, velocityY);
        }

        @Override
        public void attachToRecyclerView(RecyclerView recyclerView) {
            if (this.recyclerView != null) {
                this.recyclerView.removeCallbacks(snapWhenIdle);
            }
            this.recyclerView = (UniversalRecyclerView) recyclerView;
            super.attachToRecyclerView(recyclerView);
        }

        private boolean isIdle() {
            if (recyclerView == null || recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                return false;
            }
            final UniversalRecyclerView inner = getCurrentHistoryList();
            return inner == null || inner.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
        }

        @Override
        public int[] calculateDistanceToFinalSnap(RecyclerView.LayoutManager layoutManager, View targetView) {
            return new int[] {0, layoutManager.getDecoratedTop(targetView) - layoutManager.getPaddingTop()};
        }

        @Override
        public View findSnapView(RecyclerView.LayoutManager layoutManager) {
            if (touchInProgress || !isIdle()) return null;
            // Adapter updates and scrollToPositionWithOffset also stop nested
            // scrolling. Only a user gesture should choose a header endpoint.
            if (!userSnapPending) return null;
            userSnapPending = false;
            if (!hasAttachedHistoryPager()) return null;
            // Expand the whole header, including the optional Walt row above the card.
            final View headerRow = layoutManager.findViewByPosition(0);
            final View historyRow = layoutManager.findViewByPosition(layoutManager.getItemCount() - 1);
            if (headerRow == null) return historyRow;
            if (historyRow == null) return headerRow;
            final int headerDistance = Math.abs(calculateDistanceToFinalSnap(layoutManager, headerRow)[1]);
            final int historyDistance = Math.abs(calculateDistanceToFinalSnap(layoutManager, historyRow)[1]);
            return headerDistance < historyDistance ? headerRow : historyRow;
        }

        @Override
        public int findTargetSnapPosition(RecyclerView.LayoutManager layoutManager, int velocityX, int velocityY) {
            if (recyclerView == null || !verticalGesture || Math.abs(velocityY) < dp(600)
                    || !hasAttachedHistoryPager()) {
                return RecyclerView.NO_POSITION;
            }
            final int direction = velocityY > 0 ? 1 : -1;
            final UniversalRecyclerView inner = getCurrentHistoryList();
            if (!recyclerView.canScrollVertically(direction)
                    || (direction < 0 && inner != null && inner.canScrollVertically(-1))) {
                // Once the header is collapsed, keep ordinary transaction flings.
                // A downward fling must reach the start of that list before revealing the card.
                return RecyclerView.NO_POSITION;
            }
            // Let SnapHelper's SmoothScroller go straight to the endpoint on release,
            // rather than running a free fling followed by a separate snap animation.
            return direction > 0 ? layoutManager.getItemCount() - 1 : 0;
        }

        private final Runnable snapWhenIdle = () -> {
            if (!isIdle()) return;
            final View target = findSnapView(recyclerView.layoutManager);
            if (target == null) return;
            final int dy = calculateDistanceToFinalSnap(recyclerView.layoutManager, target)[1];
            if (dy != 0) {
                recyclerView.smoothScrollBy(0, dy);
            }
        };

        void onNestedScrollStopped() {
            if (recyclerView == null) return;
            // The outer list stays IDLE when an inner gesture moves it via scrollBy.
            // Defer until release has either started a fling or left both lists idle.
            recyclerView.removeCallbacks(snapWhenIdle);
            recyclerView.post(snapWhenIdle);
        }
    }

    private class WalletNestedLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3 {
        private final NestedScrollingParentHelper nestedHelper = new NestedScrollingParentHelper(this);
        private boolean forwardingScroll;

        WalletNestedLayout(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (headerSnapHelper != null) headerSnapHelper.onTouchEvent(event);
            final boolean handled = super.dispatchTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && headerSnapHelper != null) {
                headerSnapHelper.onNestedScrollStopped();
            }
            return handled;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int height = (int) (MeasureSpec.getSize(heightMeasureSpec) * 0.75f);
            if (maxCardHeight != height) {
                maxCardHeight = height;
                // Invalidate both measurements even when only the viewport height changes.
                if (card != null) card.requestLayout();
                if (cardShadowView != null) cardShadowView.requestLayout();
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private boolean historyPinned() {
            return historyLayout != null && historyLayout.getParent() instanceof View
                    && ((View) historyLayout.getParent()).getTop() <= listView.getPaddingTop();
        }

        // Count actual movement: short/empty pages must not consume the remainder of a gesture.
        private int scrollList(RecyclerView target, int dy) {
            final int[] scrolled = {0};
            final RecyclerView.OnScrollListener listener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    scrolled[0] += dy;
                }
            };
            forwardingScroll = true;
            target.addOnScrollListener(listener);
            try {
                target.scrollBy(0, dy);
            } finally {
                target.removeOnScrollListener(listener);
                forwardingScroll = false;
            }
            return scrolled[0];
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            if (target == getCurrentHistoryList() && headerSnapHelper != null) {
                // Inner gestures move the same header, so give its SnapHelper the
                // release velocity before the inner RecyclerView starts a free fling.
                return headerSnapHelper.onFling((int) velocityX, (int) velocityY);
            }
            return false;
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
            if (forwardingScroll) return;
            final UniversalRecyclerView inner = getCurrentHistoryList();
            if (inner == null) return;
            if (dy != 0 && type == ViewCompat.TYPE_TOUCH
                    && (target == listView || target == inner) && headerSnapHelper != null) {
                headerSnapHelper.onUserScroll();
            }
            if (target == listView && dy < 0 && historyPinned()) {
                consumed[1] += scrollList(inner, dy);
            } else if (target == inner && dy > 0) {
                // Collapse the card first, even when the drag starts inside a page.
                consumed[1] += scrollList(listView, dy);
            }
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
            if (forwardingScroll || dyUnconsumed == 0) return;
            final UniversalRecyclerView inner = getCurrentHistoryList();
            if (inner == null) return;
            if (target == listView && historyPinned()) {
                consumed[1] += scrollList(inner, dyUnconsumed);
            } else if (target == inner && dyUnconsumed < 0) {
                consumed[1] += scrollList(listView, dyUnconsumed);
            }
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed,
                int dxUnconsumed, int dyUnconsumed, int type) {
            onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, new int[2]);
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int axes, int type) {
            return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int axes, int type) {
            nestedHelper.onNestedScrollAccepted(child, target, axes, type);
        }

        @Override
        public void onStopNestedScroll(View target, int type) {
            nestedHelper.onStopNestedScroll(target, type);
            if (headerSnapHelper != null) {
                headerSnapHelper.onNestedScrollStopped();
            }
        }

        @Override
        public int getNestedScrollAxes() {
            return nestedHelper.getNestedScrollAxes();
        }
    }

    private void openWalletQrScanner() {
        if (getParentActivity() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
            return;
        }
        CameraScanActivity.showAsSheet(this, true, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                final Uri uri;
                try {
                    uri = Uri.parse(text == null ? "" : text.trim());
                } catch (Throwable e) {
                    showInvalidWalletQr();
                    return;
                }

                final String scheme = uri.getScheme();
                if (!("ton".equalsIgnoreCase(scheme) || "tc".equalsIgnoreCase(scheme))
                        || !(getParentActivity() instanceof LaunchActivity)
                        || !new LinkManager((LaunchActivity) getParentActivity(), currentAccount, null, false).handle(uri)) {
                    showInvalidWalletQr();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE || getParentActivity() == null) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openWalletQrScanner();
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setMessage(AndroidUtilities.replaceTags(getString(R.string.QRCodePermissionNoCameraWithHint)))
                .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialog, which) -> {
                    try {
                        final Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                .setTopAnimation(R.raw.permission_request_camera, 72, false, Theme.getColor(Theme.key_dialogTopBackground))
                .show();
    }

    private void showInvalidWalletQr() {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(this).createSimpleBulletin(
                getString(R.string.ScanQrCode),
                getString(R.string.ErrorOccurred)
        ).show());
    }

    private void attachBalanceOverlay() {
        if (balanceOverlay == null || actionBar == null
                || !(actionBar.getParent() instanceof ViewGroup)) {
            return;
        }
        final ViewGroup navigationContainer = (ViewGroup) actionBar.getParent();
        navigationContainer.setClipChildren(false);
        navigationContainer.setClipToPadding(false);
        if (balanceOverlay.getParent() != actionBar) {
            AndroidUtilities.removeFromParent(balanceOverlay);
            actionBar.addView(
                    balanceOverlay,
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT)
            );
        }
        balanceOverlay.bringToFront();
        actionBar.bringToFront();
        balanceOverlay.post(this::updateBalancePosition);
    }

    private void updateTermsVisibility() {
        if (tosText != null && listView != null) {
            final boolean visible = showingLearnCells && !listView.canScrollVertically(1);
            if (termsVisible == visible) return;
            termsVisible = visible;
            tosText.animate().cancel();
            if (visible) {
                tosText.setVisibility(View.VISIBLE);
                tosText.animate().alpha(1.0f).setDuration(180)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            } else {
                tosText.setAlpha(0.0f);
                tosText.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateBalancePosition() {
        if (listView == null || card == null || balanceLayout == null
                || actionBarBalanceAnchor == null
                || balanceOverlay == null
                || actionBar == null) {
            return;
        }
        balanceOverlay.bringToFront();

        final View cardItem = listView.layoutManager.findViewByPosition(cardItemPosition);
        final float progress;
        if (cardItem == null) {
            progress = listView.layoutManager.findFirstVisibleItemPosition() > cardItemPosition ? 1.0f : 0.0f;
        } else {
            final float scroll = listView.getPaddingTop() - cardItem.getTop();
            progress = Utilities.clamp(scroll / (float) cardItem.getHeight(), 1.0f, 0.0f);
        }
        balanceTransitionProgress = progress;
        updateCardTilt();

        float ty = cardItem != null ? Math.max(0, cardItem.getHeight() / 2f + dp(24)) * balanceTransitionProgress : 0;
        final float cardScale = lerp(1.0f, 0.8f, balanceTransitionProgress);
        card.setPivotX(card.getWidth() * 0.5f);
        card.setPivotY(0);
        card.setScaleX(cardScale);
        card.setScaleY(cardScale);
        card.setAlpha(1.0f - Utilities.clamp01(ilerp(progress, 0.75f, 1.0f)));
        card.setVisibility(cardItem != null && progress < 1.0f
                && cardItem.getBottom() > listView.getPaddingTop()
                && cardItem.getTop() < listView.getHeight() - listView.getPaddingBottom()
                ? View.VISIBLE : View.INVISIBLE);
        if (cardItem != null) {
            listView.getLocationInWindow(cardListLocation);
            cardLayer.getLocationInWindow(cardLayerLocation);
            card.setTranslationX(cardListLocation[0] - cardLayerLocation[0] + cardItem.getX() + cardShadowView.getX()
                    + (cardShadowView.getWidth() - card.getWidth()) / 2f);
            card.setTranslationY(cardListLocation[1] - cardLayerLocation[1] + cardItem.getY() + cardShadowView.getY() + ty);
        }

        setBalanceInOverlay(progress > 0.0f);
        updateBalanceTransitionColors(progress);

        if (balanceInOverlay) {
            updateBalanceOverlayTransform();
        }

        final float titleAlpha = 1.0f - progress;
        final float titleTranslationY = -dp(8.0f) * (1.0f - titleAlpha);
        if (actionBar.getTitleTextView() != null) {
            actionBar.getTitleTextView().setAlpha(titleAlpha);
            actionBar.getTitleTextView().setTranslationY(titleTranslationY);
        }
        if (actionBar.getTitleTextView2() != null) {
            actionBar.getTitleTextView2().setAlpha(titleAlpha);
            actionBar.getTitleTextView2().setTranslationY(titleTranslationY);
        }
    }

    private void updateBalanceOverlayTransform() {
        if (balanceCardBounds.isEmpty() || !actionBarBalanceAnchor.isAttachedToWindow()
                || !card.getFrontFaceTransform(balanceOverlay, balanceFaceTransform)) {
            return;
        }
        final float width = balanceCardBounds.width();
        final float height = balanceCardBounds.height();
        for (int i = 0; i < 4; i++) {
            final float x = i == 1 || i == 2 ? width : 0.0f;
            final float y = i >= 2 ? height : 0.0f;
            balanceLocalCorners[i * 2] = x;
            balanceLocalCorners[i * 2 + 1] = y;
            balanceProjectedCorners[i * 2] = balanceCardBounds.left + x;
            balanceProjectedCorners[i * 2 + 1] = balanceCardBounds.top + y;
        }
        balanceFaceTransform.mapPoints(balanceProjectedCorners);
        balanceOverlay.getLocationInWindow(balanceOverlayLocation);
        actionBarBalanceAnchor.getLocationInWindow(balanceTargetLocation);
        final float targetScale = 18.0f / 24.0f;
        card.setBalanceTransition(balanceTransitionProgress, actionBarBalance.getWidth() / targetScale);
        final float targetLeft = balanceTargetLocation[0] - balanceOverlayLocation[0];
        final float targetTop = balanceTargetLocation[1] - balanceOverlayLocation[1] - height * targetScale * 0.5f;
        for (int i = 0; i < 4; i++) {
            balanceTransitionCorners[i * 2] = lerp(balanceProjectedCorners[i * 2],
                    targetLeft + balanceLocalCorners[i * 2] * targetScale, balanceTransitionProgress);
            balanceTransitionCorners[i * 2 + 1] = lerp(balanceProjectedCorners[i * 2 + 1],
                    targetTop + balanceLocalCorners[i * 2 + 1] * targetScale, balanceTransitionProgress);
        }
        if (nextBalanceDrawTransform.setPolyToPoly(balanceLocalCorners, 0, balanceTransitionCorners, 0, 4)
                && !balanceDrawTransform.equals(nextBalanceDrawTransform)) {
            balanceDrawTransform.set(nextBalanceDrawTransform);
            balanceOverlay.invalidate();
        }
        card.setBalanceDiamondTransform(balanceFaceTransform, balanceDrawTransform);
        final float subtitleScale = lerp(1.0f, (12.0f / 14.0f) / targetScale, balanceTransitionProgress);
        usdBalanceView.setPivotX(0.0f);
        usdBalanceView.setPivotY(0.0f);
        usdBalanceView.setScaleX(subtitleScale);
        usdBalanceView.setScaleY(subtitleScale);
    }

    private void setBalanceInOverlay(boolean inOverlay) {
        if (balanceInOverlay == inOverlay || balanceLayout == null) {
            return;
        }
        if (inOverlay) {
            if (balanceLayout.getWidth() == 0 || balanceLayout.getHeight() == 0) return;
            balanceCardBounds.set(balanceLayout.getLeft(), balanceLayout.getTop(),
                    balanceLayout.getRight(), balanceLayout.getBottom());
        }
        // A rapid reversal must cancel the previous return acknowledgement.
        card.setOnFrontContentPresented(null);
        balanceReturningToCard = false;
        AndroidUtilities.removeFromParent(balanceLayout);
        balanceInOverlay = inOverlay;
        if (inOverlay) {
            balanceOverlay.addView(balanceLayout, new FrameLayout.LayoutParams(
                    balanceCardBounds.width(), balanceCardBounds.height(), Gravity.LEFT | Gravity.TOP));
            balanceLayout.layout(0, 0, balanceCardBounds.width(), balanceCardBounds.height());
            updateBalanceOverlayTransform();
        } else {
            card.attachBalanceToCard();
            balanceLayout.layout(balanceCardBounds.left, balanceCardBounds.top,
                    balanceCardBounds.right, balanceCardBounds.bottom);
            balanceReturningToCard = !card.isUse2D();
            updateBalanceOverlayTransform();
            if (balanceReturningToCard) {
                card.setOnFrontContentPresented(() -> {
                    balanceReturningToCard = false;
                    balanceOverlay.invalidate();
                });
            }
        }
        balanceOverlay.invalidate();
    }

    private void updateBalanceTransitionColors(float progress) {
        final int actionBarTextColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
        balanceView.setTextColor(AndroidUtilities.lerpColor(
                Color.WHITE,
                actionBarTextColor,
                progress
        ));
        usdBalanceView.setTextColor(AndroidUtilities.lerpColor(
                0xFF87EFFF,
                ColorUtils.setAlphaComponent(actionBarTextColor, Math.round(255 * 0.56f)),
                progress
        ));
        if (balanceGramIconSpan != null) {
            balanceGramIconSpan.setOverrideColor(AndroidUtilities.lerpColor(
                    Color.WHITE,
                    getThemedColor(Theme.key_featuredStickers_addButton),
                    progress
            ));
        }
        if (balanceGramLabelSpan != null) {
            balanceGramLabelSpan.setColor(ColorUtils.setAlphaComponent(
                    0xFF6DDCFF,
                    Math.round(255 * (1.0f - progress))
            ));
        }
        balanceView.invalidate();
    }

    public static void openOnramp(int currentAccount, long amount, Utilities.Callback<String> whenDone) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        wallet.whenReady(() -> {
            final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
            connectionsManager.sendRequestTyped(new TL_toncenter.getOnrampProviders(), AndroidUtilities::runOnUIThread, (res, err) -> {
                if (res == null) {
                    whenDone.run(err != null && err.text != null ? err.text : "NULL_ERROR");
                    BulletinFactory.showError(err != null && err.text != null ? err.text : "NULL_ERROR");
                    return;
                }
                if (res.objects.size() <= 0) {
                    whenDone.run("NO_PROVIDERS");
                    BulletinFactory.showError("NO_PROVIDERS");
                    return;
                }
                final ArrayList<TL_toncenter.onrampProviderInfo> providers = res.objects;
                TL_toncenter.onrampProviderInfo _provider = null;
                for (TL_toncenter.onrampProviderInfo p : providers) {
                    if (p.supports_base_currencies && (p.crypto_currencies.contains("GRAM") || p.crypto_currencies.contains("gram"))) {
                        _provider = p;
                        break;
                    }
                }
                final TL_toncenter.onrampProviderInfo provider = _provider;
                if (provider == null) {
                    whenDone.run("NO_SUITABLE_PROVIDER");
                    BulletinFactory.showError("NO_SUITABLE_PROVIDER");
                    return;
                }
                final TL_toncenter.createOnrampSession req = new TL_toncenter.createOnrampSession();
                req.address = wallet.getAddress();
                req.crypto_currency = "gram";
                req.base_currency = wallet.currencies.getCurrency();
                req.provider = provider.id;
                connectionsManager.sendRequestTyped(req, AndroidUtilities::runOnUIThread, (session, err2) -> {
                    if (session == null || TextUtils.isEmpty(session.url)) {
                        whenDone.run(err2 != null && err2.text != null ? err2.text : "NO_SESSION");
                        return;
                    }
                    whenDone.run(null);
                    final BaseFragment parentFragment = LaunchActivity.getSafeLastFragment();
                    if (parentFragment == null) return;
                    Browser.openUrl(parentFragment.getContext(), session.url);
                });
            });
        });
    }

    public static void openWalletQRAlert(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        if (!wallet.isReady()) {
            return;
        }

        final String address = wallet.getAddress();
        if (TextUtils.isEmpty(address)) {
            return;
        }

        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider, WALLET_BLUE_LIGHT);

        final FrameLayout content = new FrameLayout(context);
        content.setBackground(new WalletSheetGradientDrawable());

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(layout, LayoutHelper.createFrameMarginPx(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.FILL_HORIZONTAL | Gravity.TOP,
                dp(12), dp(30), dp(12), dp(12) + AndroidUtilities.navigationBarHeight
        ));

        final WalletQrView qrView = new WalletQrView(context, address);
        layout.addView(qrView, LayoutHelper.createLinear(288, 302, Gravity.CENTER_HORIZONTAL));

        final TextView description = new TextView(context);
        description.setText(LocaleController.getString(R.string.WalletReceiveInfo));
        description.setTextColor(Color.WHITE);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(1), 1.0f);
        layout.addView(description, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                24, 0, 24, 20
        ));

        final ButtonWithCounterView button = new ButtonWithCounterView(context, null).setRound();
        button.setColor(0xFFFFFFFF);
        button.setTextColor(WALLET_BLUE);
        final SpannableStringBuilder buttonText = new SpannableStringBuilder();
        buttonText.append("$");
        buttonText.setSpan(new ColoredImageSpan(R.drawable.wallet_buy), 0, buttonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        buttonText.append(" ").append(LocaleController.getString(R.string.WalletBuyWithCashOrCrypto));
        button.setText(buttonText);
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        final ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        closeButton.setContentDescription(getString(R.string.Close));
        ScaleStateListAnimator.apply(closeButton);
        content.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP, 4, 2, 0, 0));

        builder.setCustomView(content);
        final BottomSheet sheet = builder.create();
        sheet.occupyNavigationBar = true;
        sheet.setApplyTopPadding(false);
        sheet.setApplyBottomPadding(false);
        sheet.fixNavigationBar(WALLET_BLUE_LIGHT);

        closeButton.setOnClickListener(v -> sheet.dismiss());
        qrView.setOnCopyAddressListener(() -> {
            AndroidUtilities.addToClipboard(address);
        });

        sheet.show();
        sheet.setOverlayNavBarColor(WALLET_BLUE_LIGHT);
        AndroidUtilities.setNavigationBarColor(sheet, WALLET_BLUE_LIGHT, false);
        AndroidUtilities.setLightNavigationBar(sheet, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sheet.getWindow() != null) {
            sheet.getWindow().setNavigationBarContrastEnforced(false);
        }

        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
            button.setLoading(true);
            openOnramp(currentAccount, 0, error -> {
                button.setLoading(false);
                if (!TextUtils.isEmpty(error)) {
                    BulletinFactory.of(sheet.topBulletinContainer, resourcesProvider).showForError(error);
                } else {
                    sheet.dismiss();
                }
            });
        });
    }

    public static void openWalletHowItWorksAlert(Context context, Theme.ResourcesProvider resourceProvider) {
        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourceProvider);
        final BottomSheet[] sheet = new BottomSheet[1];

        final FrameLayout content = new FrameLayout(context);
        content.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
        content.setPadding(dp(36), 0, dp(36), dp(14));

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(layout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT
        ));

        final WalletDiamondView diamond = new WalletDiamondView(context, 160);
        layout.addView(diamond, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL, 0, -12, 0, -8));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.WalletHowItWorks));
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 6
        ));

        final TextView subtitle = new TextView(context);
        subtitle.setText(LocaleController.getString(R.string.WalletHowItWorksInfo));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(1), 1.0f);
        layout.addView(subtitle, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 24
        ));

        final LearnCell sendCell = new LearnCell(context, resourceProvider);
        sendCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        sendCell.set(
                R.drawable.wallet_learn_instant,
                LocaleController.getString(R.string.WalletSendInstantly),
                LocaleController.getString(R.string.WalletSendInstantlyInfo)
        );
        layout.addView(sendCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 0
        ));

        final LearnCell feesCell = new LearnCell(context, resourceProvider);
        feesCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        feesCell.set(
                R.drawable.wallet_learn_fees,
                LocaleController.getString(R.string.WalletNoFees),
                LocaleController.getString(R.string.WalletNoFeesInfo)
        );
        layout.addView(feesCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 0
        ));

        final LearnCell verifiedCell = new LearnCell(context, resourceProvider);
        verifiedCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        verifiedCell.set(
                R.drawable.wallet_learn_verified,
                LocaleController.getString(R.string.WalletBlockchainVerified),
                LocaleController.getString(R.string.WalletBlockchainVerifiedInfo)
        );
        layout.addView(verifiedCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 14
        ));

        final ButtonWithCounterView gotIt = new ButtonWithCounterView(context, resourceProvider).setRound();
        gotIt.setText(LocaleController.getString(R.string.WalletGotIt));
        layout.addView(gotIt, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 0
        ));
        ScaleStateListAnimator.apply(gotIt, 0.02f, 1.1f);

        final LinkSpanDrawable.LinksTextView tosText = new LinkSpanDrawable.LinksTextView(context);
        tosText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        tosText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tosText.setGravity(Gravity.CENTER);
        tosText.setText(AndroidUtilities.replaceSingleLink(LocaleController.getString(R.string.WalletTermsOfService), Theme.getColor(Theme.key_chat_messageLinkIn, resourceProvider), () -> {
            Browser.openUrlInSystemBrowser(context, getString(R.string.TermsOfServiceUrl));
        }));
        layout.addView(tosText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 6));

        builder.setCustomView(content);
        sheet[0] = builder.create();
        sheet[0].setAllowNestedScroll(true);
        sheet[0].fixNavigationBar();

        gotIt.setOnClickListener(v -> sheet[0].dismiss());

        sheet[0].show();
    }

    public static BottomSheet openWalletFirstGramsAlert(Context context, int currentAccount, Theme.ResourcesProvider resourceProvider) {
        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourceProvider);
        final BottomSheet[] sheet = new BottomSheet[1];

        final FrameLayout content = new FrameLayout(context);
        content.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_dialogBackground, resourceProvider)));
        content.setPadding(dp(36), 0, dp(36), dp(14));

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(layout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT
        ));

        final WalletDiamondView diamond = new WalletDiamondView(context, 120);
        layout.addView(diamond, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, -8));

        final TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.WalletFirstGrams));
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 6
        ));

        final TextView subtitle = new TextView(context);
        final double usdRate = MessagesController.getInstance(currentAccount).config.tonUsdRate.get();
        subtitle.setText(usdRate > 0 && !Double.isNaN(usdRate) && !Double.isInfinite(usdRate)
                ? LocaleController.formatString(R.string.WalletFirstGramsRate, GramWallet.formatGramsWithUnit(1_000_000_000L), String.format(LocaleController.getInstance().getCurrentLocale(), "%.2f", usdRate))
                : LocaleController.getString(R.string.WalletFirstGramsActions));
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourceProvider));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(1), 1.0f);
        layout.addView(subtitle, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 24
        ));

        final LearnCell sendCell = new LearnCell(context, resourceProvider);
        final SpannableStringBuilder sendText = new SpannableStringBuilder(LocaleController.getString(R.string.WalletFirstGramsSendInfo));
        final ColoredImageSpan attachIcon = new ColoredImageSpan(R.drawable.mini_attach, ColoredImageSpan.ALIGN_CENTER);
        attachIcon.setSize(dp(14));
        final int attachIndex = sendText.toString().indexOf("📎");
        sendText.setSpan(attachIcon, attachIndex, attachIndex + "📎".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final int sendArrowIndex = sendText.toString().indexOf("→");
        sendText.setSpan(new ColoredImageSpan(R.drawable.wallet_intro_arrow, ColoredImageSpan.ALIGN_CENTER), sendArrowIndex, sendArrowIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sendCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        sendCell.set(
                R.drawable.wallet_intro_telegram,
                LocaleController.getString(R.string.WalletSend),
                sendText
        );
        sendCell.icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), PorterDuff.Mode.SRC_IN));
        layout.addView(sendCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 0
        ));

        final LearnCell tradeCell = new LearnCell(context, resourceProvider);
        tradeCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        tradeCell.set(
                R.drawable.wallet_intro_trade,
                LocaleController.getString(R.string.WalletTrade),
                LocaleController.getString(R.string.WalletFirstGramsTradeInfo)
        );
        tradeCell.icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), PorterDuff.Mode.SRC_IN));
        layout.addView(tradeCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 0
        ));

        final LearnCell storeCell = new LearnCell(context, resourceProvider);
        final SpannableStringBuilder storeText = new SpannableStringBuilder(LocaleController.getString(R.string.WalletFirstGramsStoreInfo));
        final ColoredImageSpan chatsIcon = new ColoredImageSpan(R.drawable.wallet_intro_chats, ColoredImageSpan.ALIGN_CENTER);
        final int chatsIndex = storeText.toString().indexOf("💬");
        storeText.setSpan(chatsIcon, chatsIndex, chatsIndex + "💬".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final int optionsIndex = storeText.toString().indexOf("⋮");
        storeText.setSpan(new ColoredImageSpan(R.drawable.wallet_intro_options, ColoredImageSpan.ALIGN_CENTER), optionsIndex, optionsIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int arrowIndex = TextUtils.indexOf(storeText, "→"); arrowIndex >= 0; arrowIndex = TextUtils.indexOf(storeText, "→", arrowIndex + 1)) {
            storeText.setSpan(new ColoredImageSpan(R.drawable.wallet_intro_arrow, ColoredImageSpan.ALIGN_CENTER), arrowIndex, arrowIndex + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        storeCell.setPadding(dp(0), dp(8), dp(0), dp(8));
        storeCell.set(
                R.drawable.wallet_intro_store,
                LocaleController.getString(R.string.WalletStore),
                storeText
        );
        storeCell.icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), PorterDuff.Mode.SRC_IN));
        layout.addView(storeCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0, 0, 0, 14
        ));

        final ButtonWithCounterView gotIt = new ButtonWithCounterView(context, resourceProvider).setRound();
        gotIt.setText(LocaleController.getString(R.string.WalletGotIt));
        layout.addView(gotIt, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.CENTER_HORIZONTAL,
                0, 0, 0, 0
        ));
        ScaleStateListAnimator.apply(gotIt, 0.02f, 1.1f);

        builder.setCustomView(content);
        sheet[0] = builder.create();
        sheet[0].setAllowNestedScroll(true);
        sheet[0].fixNavigationBar();

        gotIt.setOnClickListener(v -> sheet[0].dismiss());

        sheet[0].show();
        return sheet[0];
    }

    public static void openCollectibleSheet(
        Context context,
        TL_wallet.nftItem collectible,
        Theme.ResourcesProvider resourceProvider
    ) {
        if (context == null || collectible == null) return;

        final int backgroundColor = Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider);
        final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        final BottomSheet sheet = new BottomSheet(context, false, resourceProvider) {
            {
                behindKeyboardColorKey = Theme.key_windowBackgroundGray;
            }
        };
        sheet.setBackgroundColor(backgroundColor);
        sheet.fixNavigationBar(backgroundColor);
        final ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(12), dp(28), dp(12), dp(12));
        scrollView.addView(layout, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final org.telegram.ui.Components.BackupImageView imageView = new org.telegram.ui.Components.BackupImageView(context);
        imageView.setRoundRadius(dp(12));
        imageView.setContentDescription(collectible.name);
        final Drawable placeholder = context.getResources().getDrawable(R.drawable.wallet_nft_placeholder).mutate();
        placeholder.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider), PorterDuff.Mode.SRC_IN));
        final org.telegram.messenger.ImageLocation image = CollectibleCell.getImage(collectible, false);
        if (image != null) {
            imageView.setImage(image, "120_120", placeholder, collectible);
        } else {
            imageView.setImageDrawable(placeholder);
        }
        layout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        final TextView nameView = new TextView(context);
        nameView.setText(TextUtils.isEmpty(collectible.name) ? LocaleController.getString(R.string.WalletCollectible) : collectible.name);
        nameView.setTextColor(textColor);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        layout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 0));

        final TextView descriptionView = new TextView(context);
        descriptionView.setText(collectible.description);
        descriptionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setVisibility(TextUtils.isEmpty(collectible.description) ? View.GONE : View.VISIBLE);
        layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 4, 12, 0));

        final LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54, 0, 20, 0, 16));
        for (int i = 0; i < 2; ++i) {
            final LinearLayout action = new LinearLayout(context);
            action.setOrientation(LinearLayout.VERTICAL);
            action.setGravity(Gravity.CENTER);
            action.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16),
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
            action.setClickable(true);
            action.setFocusable(true);
            final String label = i == 0 ? LocaleController.getString(R.string.WalletTransfer) : LocaleController.getString(R.string.WalletSell);
            action.setContentDescription(label);

            final ImageView icon = new ImageView(context);
            icon.setImageResource(i == 0 ? R.drawable.wallet_transfer : R.drawable.wallet_sell);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            action.addView(icon, LayoutHelper.createLinear(24, 24));
            final TextView text = new TextView(context);
            text.setText(label);
            text.setTextColor(textColor);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            text.setTypeface(AndroidUtilities.bold());
            text.setGravity(Gravity.CENTER);
            action.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            actions.addView(action, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, i == 0 ? 0 : 4, 0, i == 0 ? 4 : 0, 0));
        }

        if (collectible.attributes != null && !collectible.attributes.isEmpty()) {
            final TableView table = new TableView(context, resourceProvider);
            table.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
            for (TL_wallet.nftAttribute attr : collectible.attributes) {
                table.addRow(attr.trait_type, attr.value);
            }
            layout.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));
        }

        final ButtonWithCounterView okButton = new ButtonWithCounterView(context, resourceProvider).setRound();
        okButton.setText(LocaleController.getString(R.string.WalletOK));
        layout.addView(okButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        sheet.setCustomView(scrollView);
        sheet.useBackgroundTopPadding = false;
        actions.getChildAt(0).setOnClickListener(v -> {
            final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment == null) return;
            sheet.dismiss();
            fragment.presentFragment(new WalletSendSelectSender().setCollectible(collectible));
        });
        actions.getChildAt(1).setOnClickListener(v -> Browser.openUrlInSystemBrowser(context, "https://fragment.com/"));
        okButton.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
        sheet.fixNavigationBar(backgroundColor);
        AndroidUtilities.setNavigationBarColor(sheet, backgroundColor, false);
    }

    public static void openTransactionSheet(
        Context context,
        int currentAccount,
        TL_wallet.walletTransaction initialTransaction,
        Utilities.Callback3<String, Boolean, Utilities.Callback<String>> onSend,
        Utilities.Callback2<String, Boolean> onDismiss,
        Theme.ResourcesProvider resourceProvider
    ) {
        openTransactionSheet(context, currentAccount, initialTransaction, onSend, onDismiss, null, null, resourceProvider);
    }

    public static void openTransactionSheet(
            Context context,
            int currentAccount,
            TL_wallet.walletTransaction initialTransaction,
            Utilities.Callback3<String, Boolean, Utilities.Callback<String>> onSend,
            Utilities.Callback2<String, Boolean> onDismiss,
            byte[] commentRecipientPublicKey,
            Runnable openWallet, Theme.ResourcesProvider resourceProvider
    ) {
        if (context == null) return;

        final NewBottomSheet[] sheet = new NewBottomSheet[1];
        final View[] bottomBackground = new View[1];
        final GramWallet.TransactionsList history = GramWallet.getInstance(currentAccount).getTransactions();
        final ArrayList<TL_wallet.walletTransaction> transactions = new ArrayList<>(history.allTransactions);
        int initialPosition = -1;
        for (int i = 0; i < transactions.size(); i++) {
            if (transactions.get(i) == initialTransaction || GramWallet.transactionMatches(initialTransaction, transactions.get(i))) {
                initialPosition = i;
                break;
            }
        }

        final ViewGroup content;
        if (onSend == null && openWallet == null && initialPosition >= 0) {
            final Rect pagePadding = new Rect();
            context.getResources().getDrawable(R.drawable.sheet_shadow_round).getPadding(pagePadding);
            final ViewPagerFixed pager = new ViewPagerFixed(context, resourceProvider) {
                private boolean swipeTouchActive;

                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    final int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        swipeTouchActive = true;
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        swipeTouchActive = false;
                    }
                    return super.dispatchTouchEvent(event);
                }

                private final NotificationCenter.NotificationCenterDelegate listener = (id, account, args) -> {
                    for (TL_wallet.walletTransaction transaction : history.allTransactions) {
                        boolean found = false;
                        for (int i = 0; i < transactions.size(); i++) {
                            if (transactions.get(i) == transaction || GramWallet.transactionMatches(transactions.get(i), transaction)) {
                                transactions.set(i, transaction);
                                found = true;
                                break;
                            }
                        }
                        if (!found) transactions.add(transaction);
                    }
                };

                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    NotificationCenter.getInstance(currentAccount).addObserver(listener, NotificationCenter.walletTransactionsUpdate);
                    loadMore();
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    NotificationCenter.getInstance(currentAccount).removeObserver(listener, NotificationCenter.walletTransactionsUpdate);
                }

                private void loadMore() {
                    if (currentPosition >= transactions.size() - 2 && !history.isLoading() && !history.isEndReached()) {
                        history.load();
                    }
                }

                @Override
                protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                    requestLayout();
                    final View pageContent = ((FrameLayout) currentPage).getChildAt(0);
                    if (pageContent != null && pageContent.getTag() instanceof Runnable) {
                        ((Runnable) pageContent.getTag()).run();
                    }
                    loadMore();
                    if (Bulletin.getVisibleBulletin() != null) {
                        Bulletin.getVisibleBulletin().hide(false, 0);
                    }
                }

                @Override
                protected float getAvailableTranslationX() {
                    return getMeasuredWidth();
                }

                @Override
                protected void setTranslationX(View view, float tx) {
                    view.setTranslationX(tx);
                    if (getMeasuredWidth() <= 0) return;
                    final float t = Utilities.clamp(tx / getMeasuredWidth(), 1, -1);
                    view.setTranslationX(tx - t * 2 * pagePadding.left);
                    view.setPivotX(t > 0 ? 0 : view.getMeasuredWidth());
                    view.setCameraDistance(view.getMeasuredHeight() * 3.4f);
                    view.setScaleX(1.0f - Math.abs(t * .25f));
                    view.setRotationY(t * 10);
                    final WalletDiamondView diamond = view.findViewWithTag(WalletDiamondView.class);
                    if (diamond != null) {
                        diamond.setSwipeProgress(t, swipeTouchActive);
                    }
                }

                @Override
                public void onTabAnimationUpdate(boolean manual) {
                    requestLayout();
                    if (bottomBackground[0] != null) bottomBackground[0].invalidate();
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final View currentPage = getViewPages()[0];
                    if (currentPage != null) {
                        currentPage.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        int height = currentPage.getMeasuredHeight();
                        final View nextPage = getViewPages()[1];
                        if (nextPage != null && nextPage.getVisibility() == View.VISIBLE && getMeasuredWidth() > 0) {
                            nextPage.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                            final float progress = Utilities.clamp(Math.abs(currentPage.getTranslationX()) / getMeasuredWidth(), 1, 0);
                            height = AndroidUtilities.lerp(height, nextPage.getMeasuredHeight(), progress);
                        }
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            pager.setPosition(initialPosition);
            pager.setAdapter(new ViewPagerFixed.Adapter() {
                @Override
                public int getItemCount() {
                    return transactions.size();
                }

                @Override
                public View createView(int viewType) {
                    final FrameLayout page = new FrameLayout(context);
                    page.setBackground(new android.graphics.drawable.InsetDrawable(
                            Theme.createRoundRectDrawable(dp(20), 0,
                                    Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)),
                            pagePadding.left, 0, pagePadding.right, 0));
                    return page;
                }

                @Override
                public void bindView(View view, int position, int viewType) {
                    final FrameLayout frame = (FrameLayout) view;
                    frame.removeAllViews();
                    frame.addView(createTransactionSheetContent(context, currentAccount, transactions.get(position),
                            null, null, null, null, resourceProvider, sheet),
                            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
            });
            content = pager;
        } else {
            content = createTransactionSheetContent(context, currentAccount, initialTransaction,
                    onSend, onDismiss, commentRecipientPublicKey, openWallet, resourceProvider, sheet);
        }
        sheet[0] = new NewBottomSheet(context, content, resourceProvider) {
            @Override
            protected boolean shouldDrawBackground() {
                return !(content instanceof ViewPagerFixed);
            }

            @Override
            public void onSheetTop(float top) {
                super.onSheetTop(top);
                if (content instanceof ViewPagerFixed) {
                    // Keep outside-tap hit testing without drawing the shared sheet background.
                    shadowDrawable.setBounds(0, (int) top, containerView.getWidth(), containerView.getHeight());
                }
            }

            @Override
            public void show() {
                super.show();
                if (content instanceof ViewPagerFixed) {
                    if (bottomBackground[0] == null) {
                        bottomBackground[0] = new View(context) {
                            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            private final int[] pagerLocation = new int[2];
                            private final int[] backgroundLocation = new int[2];

                            @Override
                            protected void onDraw(Canvas canvas) {
                                paint.setColor(getSheetBackgroundColor());
                                content.getLocationInWindow(pagerLocation);
                                getLocationInWindow(backgroundLocation);
                                for (View page : ((ViewPagerFixed) content).getViewPages()) {
                                    if (page == null || page.getVisibility() != View.VISIBLE) continue;
                                    canvas.save();
                                    canvas.translate(pagerLocation[0] - backgroundLocation[0] + page.getLeft(),
                                            pagerLocation[1] - backgroundLocation[1] + page.getTop());
                                    canvas.concat(page.getMatrix());
                                    // Extend each page separately under the tilted lower edge, preserving the gap.
                                    canvas.drawRect(page.getPaddingLeft(), 0,
                                            page.getWidth() - page.getPaddingRight(),
                                            page.getHeight() + dp(100), paint);
                                    canvas.restore();
                                }
                            }
                        };
                        containerView.addView(bottomBackground[0], 0,
                                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM));
                    }
                    containerView.setBackground(null);
                    containerView.setPadding(0, 0, 0, 0);
                    recyclerListView.setPadding(0, 0, 0, 0);
                }
            }

            @Override
            protected int getSheetBackgroundColor() {
                return getThemedColor(Theme.key_windowBackgroundWhite);
            }
        };
        if (content instanceof ViewPagerFixed && onDismiss != null) {
            sheet[0].setOnDismissListener(() -> onDismiss.run(initialTransaction.comment, initialTransaction.comment_encrypted));
        }
        sheet[0].setFocusable(onSend != null);
        sheet[0].useBackgroundTopPadding = false;
        sheet[0].show();
    }

    private static LinearLayout createTransactionSheetContent(
            Context context,
            int currentAccount,
            TL_wallet.walletTransaction initialTransaction,
            Utilities.Callback3<String, Boolean, Utilities.Callback<String>> onSend,
            Utilities.Callback2<String, Boolean> onDismiss,
            byte[] commentRecipientPublicKey,
            Runnable openWallet,
            Theme.ResourcesProvider resourceProvider,
            NewBottomSheet[] sheet
    ) {
        final boolean preview = onSend != null;
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout topBar = new FrameLayout(context);
        layout.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

        final int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        final int selectorColor = Theme.getColor(Theme.key_listSelector, resourceProvider);

        final ImageView closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageResource(R.drawable.ic_ab_close);
        closeButton.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        closeButton.setBackground(Theme.createSelectorDrawable(selectorColor, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setContentDescription(getString(R.string.Close));
        topBar.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL, 4, -2, 0, 0));

        final ImageView moreButton = new ImageView(context);
        moreButton.setScaleType(ImageView.ScaleType.CENTER);
        moreButton.setImageResource(R.drawable.ic_ab_other);
        moreButton.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        moreButton.setBackground(Theme.createSelectorDrawable(
                selectorColor,
                Theme.RIPPLE_MASK_CIRCLE_20DP
        ));
        moreButton.setContentDescription(getString(R.string.AccDescrMoreOptions));
        moreButton.setVisibility(onSend != null ? View.GONE : View.VISIBLE);
        topBar.addView(moreButton, LayoutHelper.createFrame(
                44, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 4, 0
        ));

        final LinearLayout amountLayout = new LinearLayout(context);
        amountLayout.setOrientation(LinearLayout.VERTICAL);
        amountLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(amountLayout, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                14, initialTransaction.key_change ? -20 : -40, 14, 24
        ));

        final WalletDiamondView diamond = new WalletDiamondView(context, 120);
        diamond.setTag(WalletDiamondView.class);
        amountLayout.addView(diamond, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, -8));

        final TextView amountView = new TextView(context) {
            @Override
            public boolean canScrollHorizontally(int direction) {
                return false;
            }
        };
        amountView.setSingleLine(true);
        amountView.setGravity(Gravity.CENTER);
        amountView.setIncludeFontPadding(false);
        amountView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
        amountView.setTypeface(AndroidUtilities.bold());
        amountLayout.addView(amountView, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL
        ));

        final TextView usdView = new TextView(context);
        usdView.setGravity(Gravity.CENTER);
        usdView.setIncludeFontPadding(false);
        usdView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        usdView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        amountLayout.addView(usdView, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, 20, Gravity.CENTER_HORIZONTAL, 0, 3, 0, 0
        ));

        final TL_wallet.walletTransaction T = initialTransaction;
        if (T.key_change) {
            amountLayout.removeAllViews();

            final FrameLayout keyIcon = new FrameLayout(context);
            keyIcon.setBackground(Theme.createCircleDrawable(dp(76), IconBackgroundColors.GRAY.top, IconBackgroundColors.GRAY.bottom));
            final ImageView keyImage = new ImageView(context);
            keyImage.setImageResource(R.drawable.wallet_transaction_key);
            keyImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            keyIcon.addView(keyImage, LayoutHelper.createFrame(44, 44, Gravity.CENTER));
            amountLayout.addView(keyIcon, LayoutHelper.createLinear(76, 76, Gravity.CENTER_HORIZONTAL));

            final TextView titleView = new TextView(context);
            titleView.setText(LocaleController.getString(R.string.WalletKeyUpdate));
            titleView.setTextColor(iconColor);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setGravity(Gravity.CENTER);
            titleView.setIncludeFontPadding(false);
            amountLayout.addView(titleView, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, 24, Gravity.CENTER_HORIZONTAL, 12, 12, 12, 0
            ));
        } else if (T.nft != null) {
            amountLayout.removeAllViews();
            final TL_wallet.nftItem collectible = T.nft;
            final org.telegram.ui.Components.BackupImageView imageView = new org.telegram.ui.Components.BackupImageView(context);
            imageView.setRoundRadius(dp(12));
            imageView.setContentDescription(collectible.name);
            final Drawable placeholder = context.getResources().getDrawable(R.drawable.wallet_nft_placeholder).mutate();
            placeholder.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider), PorterDuff.Mode.SRC_IN));
            final org.telegram.messenger.ImageLocation image = CollectibleCell.getImage(collectible, false);
            if (image != null) {
                imageView.setImage(image, "120_120", placeholder, collectible);
            } else {
                imageView.setImageDrawable(placeholder);
            }
            amountLayout.addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

            final TextView nameView = new TextView(context);
            nameView.setText(TextUtils.isEmpty(collectible.name) ? LocaleController.getString(R.string.WalletCollectible) : collectible.name);
            nameView.setTextColor(iconColor);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setGravity(Gravity.CENTER);
            amountLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 16, 12, 0));

        }
        final String senderAddress = T.incoming ? T.peer != null ? T.peer.address : null : GramWallet.getInstance(currentAccount).getAddress();
        if (onSend == null) {
            if (T.comment_encrypted || T.comment_encrypted_preparing) {
                if (T.comment_encrypted_preparing || !TextUtils.isEmpty(senderAddress)) {
                    final SpoilersTextView commentView = new SpoilersTextView(context, false, resourceProvider);
                    commentView.allowClickSpoilers = false;
                    commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    commentView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                    SpannableString sb = new SpannableString("a".repeat(25));
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    sb.setSpan(new TextStyleSpan(run), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    commentView.setText(sb);
                    final MessageDrawable commentBackground = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false);
                    commentBackground.shadow = false;
                    commentBackground.overrideColor = Theme.getColor(Theme.key_wallet_comment, resourceProvider);
                    commentView.setBackground(commentBackground);
                    commentView.setPadding(dp(19), dp(6), dp(15), dp(11));
                    amountLayout.addView(commentView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 15, 32, 0));
                    ScaleStateListAnimator.apply(commentView);
                    final boolean[] loadingTransactionComment = new boolean[] { false };
                    commentView.setOnClickListener(v -> {
                        if (loadingTransactionComment[0]) return;
                        loadingTransactionComment[0] = true;
                        final Utilities.Callback2<String, String> onComment = (comment, error) -> {
                            loadingTransactionComment[0] = false;
                            if (error != null) {
                                BulletinFactory.of(sheet[0].topBulletinContainer, resourceProvider).showForError(error);
                                return;
                            }
                            commentView.setPadding(dp(19), dp(9), dp(15), dp(8));
                            final SpannableString sb2 = new SpannableString(comment);
                            final TextStyleSpan.TextStyleRun run2 = new TextStyleSpan.TextStyleRun();
                            run2.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                            sb2.setSpan(new TextStyleSpan(run2), 0, sb2.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            commentView.setText(sb2);
                            commentView.setTextIsSelectable(true);
                            commentView.setOnClickListener(null);
                            ScaleStateListAnimator.reset(commentView);
                            commentView.reveal();
                        };
                        if (T.comment_encrypted_preparing) {
                            onComment.run(T.comment, null);
                        } else {
                            GramWallet.getInstance(currentAccount).decryptTransactionComment(T.comment, senderAddress, onComment);
                        }
                    });
                }
            } else if (!TextUtils.isEmpty(T.comment)) {
                final SpoilersTextView commentView = new SpoilersTextView(context);
                commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                commentView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                commentView.setText(Emoji.replaceEmoji(T.comment, commentView.getPaint().getFontMetricsInt(), false));
                commentView.setTextIsSelectable(true);
                final MessageDrawable commentBackground = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false);
                commentBackground.shadow = false;
                commentBackground.overrideColor = Theme.getColor(Theme.key_wallet_comment, resourceProvider);
                commentView.setBackground(commentBackground);
                commentView.setPadding(dp(19), dp(9), dp(15), dp(8));
                amountLayout.addView(commentView, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0
                ));
            }
        }

        final SpannableString[] feeViewLoading = new SpannableString[1];
        final TextView[] feeView = new TextView[1];
        final TableView table = new TableView(context, resourceProvider);
        layout.addView(table, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 0));

        final String[] typingComment = new String[] { T.comment };
        final boolean[] typingCommentEncrypted = new boolean[] { T.comment_encrypted && (!preview || commentRecipientPublicKey != null) };
        final EditTextBoldCursor commentEditText;
        if (onSend != null) {
            final FrameLayout editTextContainer = new FrameLayout(context);
            editTextContainer.setBackground(Theme.createRoundRectDrawable(dp(22), dp(22), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)));

            final Runnable setFeeLoading = () -> {
                if (feeView[0] == null) return;
                if (feeViewLoading[0] == null) {
                    feeViewLoading[0] = new SpannableString(getString(R.string.Loading));
                    feeViewLoading[0].setSpan(new LoadingSpan(feeView[0], dp(150)), 0, feeViewLoading[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                feeView[0].setText(feeViewLoading[0]);
            };
            final Runnable[] cancelFetchFee = new Runnable[1];
            final Runnable fetchFee = () -> {
                if (cancelFetchFee[0] != null) cancelFetchFee[0].run();
                final Utilities.Callback2<TL_wallet.walletTransaction, String> onFee = (t, error) -> {
                    cancelFetchFee[0] = null;
                    if (t != null && t.fee > 0) {
                        feeView[0].setText(formatTransactionFee(currentAccount, t.fee, resourceProvider));
                    }
                };
                final GramWallet wallet = GramWallet.getInstance(currentAccount);
                cancelFetchFee[0] = T.nft != null
                        ? wallet.emulateSendNFT(T.peer.address, T.nft, typingComment[0], onFee)
                        : wallet.emulateSend(T.peer.address, T.amount, typingComment[0], typingCommentEncrypted[0] ? commentRecipientPublicKey : null, onFee);
            };
            final EditTextBoldCursor editText = commentEditText = new EditTextBoldCursor(context) {
                private int remaining = 960;
                private final AnimatedColor limitColor = new AnimatedColor(this);
                private final AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
                {
                    limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
                    limit.setTextSize(dp(15.33f));
                    limit.setGravity(Gravity.RIGHT);
                    limit.setCallback(this);
                }

                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return who == limit || super.verifyDrawable(who);
                }

                @Override
                protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                    super.onTextChanged(text, start, lengthBefore, lengthAfter);
                    if (limit == null) return;
                    remaining = 960 - text.toString().getBytes(StandardCharsets.UTF_8).length;
                    limit.cancelAnimation();
                    limit.setText(remaining <= 100 ? Integer.toString(remaining) : "", isAttachedToWindow());
                    invalidate();
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    canvas.save();
                    canvas.clipRect(getScrollX() + getPaddingLeft(), getScrollY(),
                        getScrollX() + getWidth() - getPaddingRight(), getScrollY() + getHeight());
                    super.onDraw(canvas);
                    canvas.restore();
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    limit.setTextColor(limitColor.set(Theme.getColor(
                        remaining <= 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourceProvider)));
                    final int right = getScrollX() + getWidth() - getPaddingRight() + dp(48);
                    final int bottom = getScrollY() + getHeight();
                    limit.setBounds(right - dp(48), bottom - Math.min(dp(44), getHeight()), right, bottom);
                    limit.draw(canvas);
                }
            };
            editText.setHint(LocaleController.getString(R.string.WalletOptionalComment));
            editText.setText(typingComment[0]);
            editText.setPadding(dp(16), dp(11), dp(16 + 48), dp(12));
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            editText.setHintTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), .65f));
            editText.setMaxLines(4);
            editTextContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 0, 0, T.nft == null && commentRecipientPublicKey != null ? 44 : 0, 0));

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {
                    typingComment[0] = editable.toString();
                    setFeeLoading.run();
                    if (cancelFetchFee[0] != null) {
                        cancelFetchFee[0].run();
                        cancelFetchFee[0] = null;
                    }
                    AndroidUtilities.cancelRunOnUIThread(fetchFee);
                    AndroidUtilities.runOnUIThread(fetchFee, 1000);
                }
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            });

            if (T.nft == null && commentRecipientPublicKey != null) {
                final FrameLayout lockViewContainer = new FrameLayout(context);
                ScaleStateListAnimator.apply(lockViewContainer);
                final ImageView lockView = new ImageView(context);
                lockViewContainer.addView(lockView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                lockView.setScaleType(ImageView.ScaleType.CENTER);
                lockView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(typingCommentEncrypted[0] ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText2, resourceProvider), PorterDuff.Mode.SRC_IN));
                lockView.setImageResource(typingCommentEncrypted[0] ? R.drawable.wallet_comment_locked : R.drawable.wallet_comment_unlocked);
                lockView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourceProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
                final HintView2[] lastHint = new HintView2[1];
                lockViewContainer.setOnClickListener(v -> {
                    typingCommentEncrypted[0] = !typingCommentEncrypted[0];

                    ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(150);
                    AtomicBoolean changed = new AtomicBoolean();
                    animator.addUpdateListener(animation -> {
                        float val = (float) animation.getAnimatedValue();
                        float scale = 0.75f + Math.abs(val - 0.5f) / 0.5f * 0.25f;
                        lockView.setScaleX(scale);
                        lockView.setScaleY(scale);
                        if (val >= 0.5f && !changed.get()) {
                            changed.set(true);
                            lockView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(typingCommentEncrypted[0] ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteGrayText2, resourceProvider), PorterDuff.Mode.SRC_IN));
                            lockView.setImageResource(typingCommentEncrypted[0] ? R.drawable.wallet_comment_locked : R.drawable.wallet_comment_unlocked);
                        }
                    });
                    animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    animator.setDuration(420);
                    animator.start();

                    if (lastHint[0] != null) {
                        lastHint[0].hide();
                        lastHint[0] = null;
                    }
                    if (layout.getParent() instanceof FrameLayout) {
                        final FrameLayout parent = (FrameLayout) layout.getParent();

                        final HintView2 hintView = lastHint[0] = new HintView2(context, HintView2.DIRECTION_BOTTOM);
                        hintView.setRoundingWithCornerEffect(false);
                        hintView.setPadding(dp(10), 0, dp(10), 0);
                        hintView.setText(typingCommentEncrypted[0] ? LocaleController.getString(R.string.WalletPrivateCommentInfo) : LocaleController.getString(R.string.WalletPublicCommentInfo));
                        hintView.setTranslationY(-(layout.getHeight() - editTextContainer.getBottom() + dp(44)));
                        hintView.setJointPx(1, -dp(14 + 22 - 10));
                        hintView.setRounding(20);
                        hintView.setShadow(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .25f));
                        parent.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
                        hintView.show();
                        hintView.setOnHiddenListener(() -> AndroidUtilities.removeFromParent(hintView));
                    }

                    if (!TextUtils.isEmpty(typingComment[0])) {
                        setFeeLoading.run();
                        if (cancelFetchFee[0] != null) {
                            cancelFetchFee[0].run();
                            cancelFetchFee[0] = null;
                        }
                        AndroidUtilities.cancelRunOnUIThread(fetchFee);
                        AndroidUtilities.runOnUIThread(fetchFee, 1000);
                    }
                });
                editTextContainer.addView(lockViewContainer, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.BOTTOM));
            }
            layout.addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 12, 14, 8));
        } else {
            commentEditText = null;
        }

        final ButtonWithCounterView okButton = new ButtonWithCounterView(context, resourceProvider).setRound();
        okButton.setText(getString(R.string.OK));
        layout.addView(okButton, LayoutHelper.createLinear(
            LayoutHelper.MATCH_PARENT, 48, 14, 14, 14, 14
        ));

        final TL_wallet.walletTransaction[] transaction = new TL_wallet.walletTransaction[] { initialTransaction };
        final Utilities.Callback<TL_wallet.walletTransaction> update = t -> {
            final long amount = Math.abs(t.amount);
            final boolean incoming = t.incoming;
            final long fee = Math.abs(t.fee);

            final boolean unsettled = t.pending || t.failed;
            final boolean currentPage = !(layout.getParent() instanceof View
                    && layout.getParent().getParent() instanceof ViewPagerFixed)
                    || ((ViewPagerFixed) layout.getParent().getParent()).getViewPages()[0] == layout.getParent();

            if (!t.key_change && t.nft == null) {
                amountView.setTextColor(unsettled
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider)
                    : Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                );
                final SpannableStringBuilder formattedAmount = GramWallet.formatGramsScaled(amount, 30f / 40f);
                formattedAmount.insert(0, incoming ? "+" : "–");
                if (!unsettled) {
                    formattedAmount.setSpan(new ForegroundColorSpan(Theme.getColor(incoming ? Theme.key_color_green : Theme.key_windowBackgroundWhiteBlackText, resourceProvider)), 0, formattedAmount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                final SpannableStringBuilder amountText = new SpannableStringBuilder();
                amountText.append(GramWallet.formatGramsWithUnit(formattedAmount));
                final int amountStart = TextUtils.indexOf(amountText, formattedAmount);
                final int amountEnd = amountStart + formattedAmount.length();
                if (amountStart > 0) {
                    amountText.setSpan(new RelativeSizeSpan(30f / 40f), 0, amountStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (amountStart >= 0 && amountEnd < amountText.length()) {
                    amountText.setSpan(new RelativeSizeSpan(30f / 40f), amountEnd, amountText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                amountView.setText(amountText);
                final CharSequence currency = GramWallet.getInstance(currentAccount).formatCurrency(amount);
                if (unsettled) {
                    final SpannableStringBuilder status = new SpannableStringBuilder(t.failed
                            ? LocaleController.getString(R.string.WalletFailed)
                            : UploadingDotsSpannable.replace(LocaleController.getString(R.string.WalletSending), usdView));
                    status.setSpan(new ForegroundColorSpan(t.failed ? Theme.getColor(Theme.key_text_RedRegular, resourceProvider) : Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider)), 0, status.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    usdView.setText(LocaleController.formatSpannable(R.string.WalletTransactionAmountStatus, currency, status));
                } else {
                    usdView.setText(currency);
                }
                if (currentPage && sheet[0] != null && sheet[0].actionBar != null) {
                    sheet[0].actionBar.setTitle(GramWallet.formatGramsWithUnit(formattedAmount));
                }
            } else if (t.key_change) {
                if (currentPage && sheet[0] != null && sheet[0].actionBar != null) {
                    sheet[0].actionBar.setTitle(GramWallet.formatGramsWithUnit(GramWallet.formatGramsScaled(amount, 30f / 40f)));
                }
            } else if (t.nft != null) {
                if (currentPage && sheet[0] != null && sheet[0].actionBar != null) {
                    sheet[0].actionBar.setTitle(TextUtils.isEmpty(t.nft.name) ? getString(R.string.WalletCollectible) : t.nft.name);
                }
            }

            table.clear();
            if (t.peer instanceof TL_wallet.walletTransactionPeerUser) {
                final TL_wallet.walletTransactionPeerUser peer = (TL_wallet.walletTransactionPeerUser) t.peer;
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peer.user_id);
                if (user != null && !UserObject.isService(user.id)) {
                    table.addRowUser(incoming ? LocaleController.getString(R.string.WalletSender) : LocaleController.getString(R.string.WalletRecipient), currentAccount, peer.user_id, () -> {
                        if (onSend != null) return;
                        if (sheet[0] != null) {
                            sheet[0].dismiss();
                        }
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        lastFragment.presentFragment(ChatActivity.of(peer.user_id));
                    }, LocaleController.getString(R.string.WalletSend), onSend != null ? null : () -> {
                        sheet[0].setOnDismissListener(dialog -> {
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            lastFragment.presentFragment(new WalletSendEnterAmount(user));
                        });
                        sheet[0].dismiss();
                    });
                }
                if (!TextUtils.isEmpty(peer.domain) && !TextUtils.isEmpty(peer.address)) {
                    table.addRowLink(LocaleController.getString(R.string.WalletDomain), peer.domain, () -> {
                        sheet[0].setOnDismissListener(dialog -> {
                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment == null) return;
                            lastFragment.presentFragment(user != null ? new WalletSendEnterAmount(user) : new WalletSendEnterAmount(peer.address).setRecipientDomain(peer.domain));
                        });
                        sheet[0].dismiss();
                    });
                }
            } else if (t.peer instanceof TL_wallet.walletTransactionPeerAddress) {
                final TL_wallet.walletTransactionPeerAddress peer = (TL_wallet.walletTransactionPeerAddress) t.peer;
                if (!TextUtils.isEmpty(peer.domain)) {
                    if (!TextUtils.isEmpty(peer.address)) {
                        table.addRowLink(incoming ? LocaleController.getString(R.string.WalletSender) : LocaleController.getString(R.string.WalletRecipient), peer.domain, () -> {
                            sheet[0].setOnDismissListener(dialog -> {
                                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment == null) return;
                                lastFragment.presentFragment(new WalletSendEnterAmount(peer.address).setRecipientDomain(peer.domain));
                            });
                            sheet[0].dismiss();
                        }, LocaleController.getString(R.string.WalletSend), onSend != null ? null : () -> {
                            sheet[0].setOnDismissListener(dialog -> {
                                final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                                if (lastFragment == null) return;
                                lastFragment.presentFragment(new WalletSendEnterAmount(peer.address).setRecipientDomain(peer.domain));
                            });
                            sheet[0].dismiss();
                        });
                    } else {
                        table.addRowLink(incoming ? LocaleController.getString(R.string.WalletSender) : LocaleController.getString(R.string.WalletRecipient), peer.domain, () -> {

                        });
                    }
                }
            }

            if (t.peer instanceof TL_wallet.walletTransactionPeerUser || t.peer instanceof TL_wallet.walletTransactionPeerAddress) {
                final TL_wallet.WalletTransactionPeer peer = t.peer;
                final boolean hasRecipientUser = peer instanceof TL_wallet.walletTransactionPeerUser && MessagesController.getInstance(currentAccount).getUser(peer.user_id) != null && !UserObject.isService(peer.user_id);
                final boolean showAddressSend = onSend == null && !hasRecipientUser && TextUtils.isEmpty(peer.domain) && !TextUtils.isEmpty(peer.address);
                table.addRowMonospaced(LocaleController.getString(R.string.WalletAddress), formatTransactionAddress(peer.address, () -> {
                    BulletinFactory.of(sheet[0].topBulletinContainer, resourceProvider)
                            .createSimpleBulletin(R.raw.copy, LocaleController.getString(R.string.WalletAddressCopiedBulletin)).show(false);
                }), 14, null, showAddressSend ? LocaleController.getString(R.string.WalletSend) : null, () -> {
                    sheet[0].setOnDismissListener(dialog -> {
                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return;
                        lastFragment.presentFragment(new WalletSendEnterAmount(peer.address));
                    });
                    sheet[0].dismiss();
                });
            }

            if (fee > 0 && t.gasless) {
                final SpannableStringBuilder feeString = new SpannableStringBuilder("x ");
                final ColoredImageSpan icon = new ColoredImageSpan(R.drawable.wallet_gram_small, ColoredImageSpan.ALIGN_CENTER);
                icon.recolorDrawable = false;
                icon.setSize(dp(16));
                icon.translate(0, dp(1));
                feeString.setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                feeString.append(LocaleController.getString(R.string.WalletFeePaidByTelegram));
                table.addRow(LocaleController.getString(R.string.WalletFee), feeString, "?", () -> {
                    new AlertDialog.Builder(context, resourceProvider)
                        .setTitle(LocaleController.getString(R.string.WalletNetworkFee))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.formatSpannable(R.string.WalletNetworkFeeInfo, GramWallet.getInstance(currentAccount).formatCurrency(fee, true))))
                        .setPositiveButton(LocaleController.getString(R.string.WalletOK), null)
                        .show();
                });
            } else if (fee > 0 || t.nft != null || t.preview) {
                feeView[0] = new TextView(context);
                feeView[0].setGravity(Gravity.CENTER_VERTICAL);
                feeView[0].setIncludeFontPadding(false);
                feeView[0].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                feeView[0].setTextColor(unsettled ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider) : iconColor);
                if (t.preview && fee <= 0) {
                    if (feeViewLoading[0] == null) {
                        feeViewLoading[0] = new SpannableString(getString(R.string.Loading));
                        feeViewLoading[0].setSpan(new LoadingSpan(feeView[0], dp(150)), 0, feeViewLoading[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    feeView[0].setText(feeViewLoading[0]);
                } else {
                    feeView[0].setText(formatTransactionFee(currentAccount, fee, resourceProvider));
                }
                table.addRow(LocaleController.getString(R.string.WalletFee), feeView[0]);
            }

            if (t.date != 0) {
                table.addRow(LocaleController.getString(R.string.WalletDate), LocaleController.formatShortDateTime(t.date));
            }
        };

        layout.setTag((Runnable) () -> update.run(transaction[0]));
        update.run(initialTransaction);

        final NotificationCenter.NotificationCenterDelegate listener;
        if (!preview) {
            listener = new NotificationCenter.NotificationCenterDelegate() {
                @Override
                public void didReceivedNotification(int id, int account, Object... args) {
                    if (id == NotificationCenter.walletUpdate || id == NotificationCenter.walletTransactionsUpdate) {
                        final GramWallet wallet = GramWallet.getInstance(currentAccount);
                        final GramWallet.TransactionsList transactions = wallet.getTransactions();
                        TL_wallet.walletTransaction newTransaction = null;
                        for (TL_wallet.walletTransaction t : transactions.allTransactions) {
                            if (t == transaction[0] || GramWallet.transactionMatches(transaction[0], t)) {
                                newTransaction = t;
                                break;
                            }
                        }
                        if (newTransaction != null) {
                            update.run(transaction[0] = newTransaction);
                        }
                    }
                }
            };

            if (initialTransaction.preview) {
                GramWallet.getInstance(currentAccount).getTransaction(initialTransaction.id, t -> {
                    if (t == null) return;
                    t.incoming = initialTransaction.incoming;
                    t.peer = initialTransaction.peer;
                    update.run(transaction[0] = t);
                });
            }
        } else {
            listener = null;
        }
        layout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                update.run(transaction[0]);
                if (listener != null) {
                    NotificationCenter.getInstance(currentAccount).addObserver(listener, NotificationCenter.walletUpdate);
                    NotificationCenter.getInstance(currentAccount).addObserver(listener, NotificationCenter.walletTransactionsUpdate);
                }
                if (onDismiss != null) {
                    sheet[0].setOnDismissListener(() -> onDismiss.run(typingComment[0], typingCommentEncrypted[0]));
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (listener != null) {
                    NotificationCenter.getInstance(currentAccount).removeObserver(listener, NotificationCenter.walletUpdate);
                    NotificationCenter.getInstance(currentAccount).removeObserver(listener, NotificationCenter.walletTransactionsUpdate);
                }
            }
        });

        if (onSend != null) {
            okButton.setText(LocaleController.formatSpannable(R.string.WalletSendAmount, T.nft != null
                    ? TextUtils.isEmpty(T.nft.name) ? LocaleController.getString(R.string.WalletCollectible) : T.nft.name
                        : GramWallet.formatGramsWithUnit(Math.abs(initialTransaction.amount))));
            okButton.setOnClickListener(v -> {
                if (okButton.isLoading()) return;
                if (commentEditText != null && commentEditText.getText().toString().getBytes(StandardCharsets.UTF_8).length > 960) {
                    AndroidUtilities.shakeViewSpring(commentEditText);
                    return;
                }
                okButton.setLoading(true);
                onSend.run(typingComment[0], typingCommentEncrypted[0], error -> {
                    okButton.setLoading(false);
                    if (!TextUtils.isEmpty(error)) {
                        BulletinFactory.of(sheet[0].topBulletinContainer, resourceProvider).showForError(error);
                        return;
                    }
                    sheet[0].dismiss();
                });
            });
        } else if (openWallet != null) {
            okButton.setText(LocaleController.getString(R.string.WalletOpenMyWallet));
            okButton.setOnClickListener(v -> {
                openWallet.run();
                sheet[0].dismiss();
            });
        } else {
            okButton.setOnClickListener(v -> sheet[0].dismiss());
        }

        closeButton.setOnClickListener(v -> sheet[0].dismiss());
        moreButton.setOnClickListener(v -> {
            final ItemOptions options = ItemOptions.makeOptions(
                sheet[0].getContainer(),
                resourceProvider,
                moreButton
            );
            if (!TextUtils.isEmpty(transaction[0].tx_hash)) {
                options.add(
                    R.drawable.msg_search,
                    LocaleController.getString(R.string.WalletViewInExplorer),
                    () -> openTransactionInExplorer(context, currentAccount, transaction[0].tx_hash)
                );
            }
            options.add(R.drawable.wallet_help, LocaleController.getString(R.string.WalletWhatIsGram), () -> {
                openWalletHowItWorksAlert(context, resourceProvider);
            });
            options.translate(0, -dp(48));
            options.show();
        });

        return layout;
    }

    private static void openTransactionInExplorer(Context context, int currentAccount, String hash) {
        if (TextUtils.isEmpty(hash)) {
            return;
        }
        String baseUrl = MessagesController.getInstance(currentAccount).tonBlockchainExplorerUrl;
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = "https://tonviewer.com/";
        } else if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        Browser.openUrlInSystemBrowser(
                context,
                baseUrl + "transaction/" + Uri.encode(hash)
        );
    }

    private static CharSequence formatTransactionAmount(long nanotons, boolean recolorIcon) {
        final SpannableStringBuilder amount = GramWallet.formatGramsScaled(Math.abs(nanotons), 28.0f / 44.0f);
        if (nanotons > 0) {
            amount.insert(0, "+");
        } else if (nanotons < 0) {
            amount.insert(0, "–");
        }
        amount.append(" ");
        int index = amount.length();
        amount.append("GRAM");
        final ColoredImageSpan icon = new ColoredImageSpan(
                R.drawable.wallet_gram_small,
                ColoredImageSpan.ALIGN_CENTER
        );
        icon.recolorDrawable = recolorIcon;
        icon.setSize(dp(36));
        amount.setSpan(
            icon,
            index,
            amount.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return amount;
    }

    private static CharSequence formatTransactionFee(int currentAccount, long nanotons, Theme.ResourcesProvider resourceProvider) {
        final SpannableStringBuilder fee = new SpannableStringBuilder("G ");
        final ColoredImageSpan icon = new ColoredImageSpan(R.drawable.wallet_gram_small, ColoredImageSpan.ALIGN_CENTER);
        icon.recolorDrawable = false;
        icon.setSize(dp(16));
        fee.setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fee.append(new BigDecimal(nanotons)
            .movePointLeft(9)
            .stripTrailingZeros()
            .toPlainString());
        final CharSequence currencyAmount = GramWallet.getInstance(currentAccount).formatCurrency(nanotons, true);
        if (!TextUtils.isEmpty(currencyAmount)) {
            final int approximationStart = fee.length();
            fee.append("  ~ ").append(currencyAmount);
            fee.setSpan(
                new ForegroundColorSpan(Theme.getColor(
                        Theme.key_windowBackgroundWhiteGrayText2,
                        resourceProvider
                )),
                approximationStart,
                fee.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return fee;
    }

    public static CharSequence formatTransactionAddress(String address, Runnable copy) {
        if (TextUtils.isEmpty(address)) {
            return LocaleController.getString(R.string.WalletUnknown);
        }
        final SpannableStringBuilder formatted = new SpannableStringBuilder();
        int from = 0;
        for (int i = 0, j = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(i % 16 == 0 ? '\n' : ' ');
                if (i % 16 == 0) j++;
                from = formatted.length();
            }
            formatted.append(address.charAt(i));
            if (i % 4 == 3) {
//                if (j++ % 2 == 1) {
//                    formatted.setSpan(new EllipsizeSpanAnimator.TextAlphaSpan(0.6f), from, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                }
                from = formatted.length();
            }
        }
        if (copy != null) {
            formatted.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View view) {
                    AndroidUtilities.addToClipboard(address);
                    copy.run();
                }
                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, 0, formatted.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return formatted;
    }

    private CharSequence[] cachedLoading = new CharSequence[2];
    private CharSequence loading(int t) {
        if (cachedLoading[t] == null) {
            cachedLoading[t] = new SpannableString("l");
            final LoadingSpan loadingSpan = new LoadingSpan(t == 0 ? balanceView : usdBalanceView, dp(t == 0 ? 100 : 50));
            loadingSpan.setColors(Theme.multAlpha(0xFFFFFFFF, 0.45f), Theme.multAlpha(0xFFFFFFFF, 0.20f));
            ((SpannableString) cachedLoading[t]).setSpan(loadingSpan, 0, cachedLoading[t].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return cachedLoading[t];
    }

    private void update() {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        updateWalletTopPanel();
        sendButton.setEnabled(wallet.isReady() && wallet.canSend());
        if (!wallet.isReady()) {
            balanceView.setText(loading(0));
            usdBalanceView.setText(loading(1));
            return;
        }

        updateBalance(wallet.getBalance());
        if (nftsList != null && !nftsList.isLoaded() && !nftsList.isLoading() && nftsList.getError() == null) {
            nftsList.load();
        }

        final String address = wallet.getAddress();
        if (!TextUtils.isEmpty(address)) {
            card.setCardNumber(formatAddress(address.toUpperCase()));
        }
    }

    private HashSet<Runnable> updateRunnables = new HashSet<>();
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.walletUpdate && balanceView != null) {
            update();
            for (Runnable callback : updateRunnables) {
                callback.run();
            }
        } else if (id == NotificationCenter.starBalanceUpdated) {
            updateWalletTopPanel();
        } else if (id == NotificationCenter.didSetOrRemoveTwoStepPassword) {
            if (args.length > 0 && args[0] instanceof TL_account.Password) {
                updatePasswordState((TL_account.Password) args[0]);
            } else {
                requestPassword();
            }
        } else if (id == NotificationCenter.walletTransactionsUpdate
                && args.length > 0
                && args[0] == transactionsList
                && listView != null
                && listView.adapter != null) {
            if (transactionsList.transactions.isEmpty() && !transactionsList.isEndReached()) {
                transactionsList.load();
            }
            updateHistory(true);
        }
    }

    private CharSequence gramIcon;
    private void updateBalance(long nanotons) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (gramIcon == null) {
            gramIcon = new SpannableString("G");
            balanceGramIconSpan = card.createBalanceDiamondSpan();
            ((SpannableString) gramIcon).setSpan(balanceGramIconSpan, 0, gramIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.append(gramIcon);
        sb.append(" ");
        sb.append(WalletCardComponent.formatBalance(nanotons));
        sb.append(" ");
        int from = sb.length();
        sb.append(LocaleController.getString(R.string.GramCurrency));
        if (balanceGramLabelSpan == null) {
            balanceGramLabelSpan = new BalanceLabelSpan(0xFF6DDCFF);
        }
        sb.setSpan(balanceGramLabelSpan, from, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        balanceView.setText(sb);

        final CharSequence currencyText = GramWallet.getInstance(currentAccount).formatCurrency(nanotons);
        if (TextUtils.isEmpty(currencyText)) {
            usdBalanceView.setText(loading(1));
        } else {
            usdBalanceView.setText(currencyText);
        }

        updateBalanceTransitionColors(balanceTransitionProgress);
    }

    private static class BalanceLabelSpan extends CharacterStyle implements UpdateAppearance {
        private int color;

        private BalanceLabelSpan(int color) {
            this.color = color;
        }

        public void setColor(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setColor(color);
        }
    }

    private static String formatAddress(String address) {
        StringBuilder formatted = new StringBuilder(address.length() + address.length() / 4);
        for (int i = 0; i < address.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(' ');
            }
            formatted.append(address.charAt(i));
        }
        return formatted.toString();
    }

    private void setUse2DCard(boolean use2D) {
        if (use2DCard == use2D || card == null) {
            return;
        }
        use2DCard = use2D;

        card.setUse2D(use2D);
        cardShadowView.requestLayout();

        card.post(this::updateBalancePosition);
    }

    private FrameLayout createWalletNoticeRow(TextView text, Runnable onClick) {
        final Context context = text.getContext();
        final FrameLayout row = new FrameLayout(context);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        text.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text.setTypeface(AndroidUtilities.bold());
        text.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 42, 0));
        final ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.attach_arrow_right);
        arrow.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        row.addView(arrow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        row.setOnClickListener(v -> onClick.run());
        text.setDuplicateParentStateEnabled(true);
        ScaleStateListAnimator.apply(text, .02f, 1.2f);
        walletTopPanel.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return row;
    }

    private CharSequence formatWalletNoticeBalance(long balance, int stringRes) {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        final int iconStart = text.length();
        text.append('\uFFFC');
        final ColoredImageSpan icon = new ColoredImageSpan(R.drawable.mini_gram_16);
        icon.recolorDrawable = false;
        final int amountColor = getThemedColor(Theme.key_featuredStickers_addButton);
        text.setSpan(icon, iconStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(" ");
        final int amountStart = text.length();
        text.append(GramWallet.formatGrams(balance));
        text.setSpan(new ForegroundColorSpan(amountColor), amountStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return LocaleController.formatSpannable(stringRes, text);
    }

    private void updateWalletTopPanel() {
        if (walletTopPanel == null) return;
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        final boolean animated = walletTopPanel.isAttachedToWindow() && walletTopPanel.isLaidOut();
        walletTopPanel.setViewVisible(waltButton, wallet.hasWaltBalance(), animated);
        final long previousWalletsBalance = wallet.getBalanceOfPreviousWallets();
        final boolean showOldWallets = previousWalletsBalance > 500_000_000L;
        if (showOldWallets) {
            oldWalletsButtonText.setText(formatWalletNoticeBalance(previousWalletsBalance, R.string.WalletOldWalletsBalance));
        }
        walletTopPanel.setViewVisible(oldWalletsButton, showOldWallets, animated);
        final long earningsBalance = StarsController.getTonInstance(currentAccount).getBalance().amount;
        final boolean showEarnings = earningsBalance > 500_000_000L;
        if (showEarnings) {
            gramEarningsButtonText.setText(formatWalletNoticeBalance(earningsBalance, R.string.WalletGramEarningsBalance));
        }
        walletTopPanel.setViewVisible(gramEarningsButton, showEarnings, animated);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        updateWalletTopPanel();
        // Keep a stable item even when empty, so visibility changes animate its
        // height instead of inserting/removing rows and shifting adapter positions.
        items.add(UItem.asCustomShadow(walletTopPanelContainer));
        cardItemPosition = items.size();
        items.add(UItem.asCustomShadow(cardShadowView, true));
        items.add(UItem.asCustomShadow(buttonsContainer));
        showingLearnCells = shouldShowLearnCells();
        if (showingLearnCells) {
            items.add(LearnCell.Factory.of(R.drawable.wallet_learn_instant, LocaleController.getString(R.string.WalletSendInstantly), LocaleController.getString(R.string.WalletSendInstantlyInfo)));
            items.add(LearnCell.Factory.of(R.drawable.wallet_learn_fees, LocaleController.getString(R.string.WalletNoFees), LocaleController.getString(R.string.WalletNoFeesInfo)));
            items.add(LearnCell.Factory.of(R.drawable.wallet_learn_verified, LocaleController.getString(R.string.WalletBlockchainVerified), LocaleController.getString(R.string.WalletBlockchainVerifiedInfo)));
            items.add(UItem.asCustomShadow(tosSpacer));
        } else if (historyLayout != null) {
            if (loadedPassword && !hasPassword && wallet.getBalance() > 500_000_000) {
                items.add(UItem.asCustom(protectAccountContainer));
            }
            items.add(UItem.asFullscreenCustom(historyLayout, 0, true));
        }
        if (tosText != null) {
            // Visibility is resolved after layout, once the new scroll range is known.
            termsVisible = false;
            tosText.animate().cancel();
            tosText.setAlpha(0.0f);
            tosText.setVisibility(View.INVISIBLE);
        }
    }

    private boolean loadedPassword, hasPassword;
    private void requestPassword() {
        getConnectionsManager().sendRequestTyped(new TL_account.getPassword(), AndroidUtilities::runOnUIThread, (password, err) -> {
            if (password != null) {
                updatePasswordState(password);
            }
        });
    }

    private void updatePasswordState(TL_account.Password password) {
        loadedPassword = true;
        hasPassword = password.has_password;
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillCollectibles(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (nftsList == null) return;
        // Keep one background while collectibles and loading rows animate in and out.
        adapter.whiteSectionStart();
        for (TL_wallet.nftItem nft : nftsList.collectibles) items.add(CollectibleCell.Factory.of(nft));
        if (nftsList.isLoading()) {
            items.add(UItem.asFlicker(-1, FlickerLoadingView.WALLET_COLLECTIBLE));
        } else if (nftsList.getError() != null) {
            items.add(UItem.asButton(RETRY_NFTS, LocaleController.getString(R.string.Retry)));
        }
        adapter.whiteSectionEnd();
    }

    private void fillTransactions(ArrayList<UItem> items, UniversalAdapter adapter) {
        // Keep one background while transaction rows animate in and out.
        adapter.whiteSectionStart();
        final boolean hasTransactions = transactionsList != null && !transactionsList.allTransactions.isEmpty();
        if (hasTransactions) {
            for (int i = 0; i < transactionsList.allTransactions.size(); i++) {
                items.add(TransactionCell.Factory.of(transactionsList.allTransactions.get(i), transactionsList.addressFormatter));
            }
        }
        if (transactionsList != null && (transactionsList.isLoading() || !transactionsList.hasEndReached())) {
            items.add(UItem.asFlicker(-1, FlickerLoadingView.WALLET_TRANSACTION));
            items.add(UItem.asFlicker(-2, FlickerLoadingView.WALLET_TRANSACTION));
            items.add(UItem.asFlicker(-3, FlickerLoadingView.WALLET_TRANSACTION));
        }
        adapter.whiteSectionEnd();
        if (hasTransactions) {
            final long min_amount = getMessagesController().config.walletTransferMinNanos.get();
            if (min_amount > 0) {
                items.add(UItem.asShadow(LocaleController.formatSpannable(R.string.WalletHiddenTransactions, GramWallet.formatGramsWithUnit(min_amount))));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (actionBar != null) {
            actionBar.post(this::attachBalanceOverlay);
        }
        GramWallet.getInstance(currentAccount).invalidateState();
        if (nftsList != null && GramWallet.getInstance(currentAccount).isReady()) nftsList.invalidate();
        update();
        if (listView != null) {
            updateHistory(true);
        }
    }

    private Runnable cancelListen;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.walletUpdate);
        getNotificationCenter().addObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.walletTransactionsUpdate);
        getNotificationCenter().addObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword);
        final GramWallet wallet = GramWallet.getInstance(currentAccount);
        nftsList = wallet.getCollectibles();
        nftsList.subscribe(onNftsChanged);
        if (cancelListen != null) cancelListen.run();
        cancelListen = wallet.listen();
        requestPassword();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (card != null) card.setOnFrontContentPresented(null);
        balanceReturningToCard = false;
        cancelHistoryTabsAnimation();
        if (nftsList != null) nftsList.unsubscribe(onNftsChanged);
        stopCardOverscrollTracking();
        if (headerSnapHelper != null) {
            headerSnapHelper.attachToRecyclerView(null);
        }
        getNotificationCenter().removeObserver(this, NotificationCenter.walletUpdate);
        getNotificationCenter().removeObserver(this, NotificationCenter.starBalanceUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.walletTransactionsUpdate);
        getNotificationCenter().removeObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword);
        AndroidUtilities.removeFromParent(balanceOverlay);
        if (transactionsList != null) {
            transactionsList.cancel();
        }
        super.onFragmentDestroy();
        if (cancelListen != null) {
            cancelListen.run();
            cancelListen = null;
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_wallet.nftItem) {
            openCollectibleSheet(getContext(), (TL_wallet.nftItem) item.object, getResourceProvider());
            return;
        }
        if (item.id == RETRY_NFTS && nftsList != null) {
            nftsList.load();
            return;
        }
        if (item.object instanceof TL_wallet.walletTransaction) {
            openTransactionSheet(getContext(), currentAccount, (TL_wallet.walletTransaction) item.object, null, null, getResourceProvider());
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private static Typeface getWalletAddressTypeface(Context context) {
        if (walletAddressTypeface == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                walletAddressTypeface = new Typeface.Builder(
                        context.getAssets(),
                        "fonts/rmono_var.ttf"
                ).setFontVariationSettings("'wght' 500")
                        .setWeight(500)
                        .build();
            } else {
                walletAddressTypeface = AndroidUtilities.getTypeface("fonts/rmono.ttf");
            }
        }
        return walletAddressTypeface;
    }

    private static Bitmap createWalletQrCode(Context context, String address) {
        try {
            final HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 0);
            final TelegramQRCodeWriter writer = new TelegramQRCodeWriter();
            final Drawable gramLogo = context.getResources().getDrawable(R.drawable.mini_gram_72).mutate();
            writer.setCenterDrawable(gramLogo);
            return writer.encode("ton://transfer/" + address, 768, 768, hints, null);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static class WalletQrView extends FrameLayout {

        private final TextView copyButton;
        private final TextView showQrButton;
        private final FrameLayout qrCard;
        private final FrameLayout frontFace;
        private final FrameLayout backFace;
        private Runnable onCopyAddressListener;
        private boolean showingAddress;
        private boolean flipInProgress;
        private ValueAnimator flipAnimator;

        public WalletQrView(Context context, String address) {
            super(context);

            final AddressRingView ringView = new AddressRingView(context, address);
            addView(ringView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT,
                    Gravity.CENTER
            ));

            qrCard = new FrameLayout(context);
            qrCard.setBackground(Theme.createRoundRectDrawable(dp(20), Color.WHITE));
            qrCard.setCameraDistance(dp(8000));
            addView(qrCard, LayoutHelper.createFrame(
                    212,
                    244,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, 22, 0, 0
            ));

            frontFace = new FrameLayout(context);
            qrCard.addView(frontFace, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT
            ));

            final FrameLayout qrContainer = new FrameLayout(context);
            frontFace.addView(qrContainer, LayoutHelper.createFrame(
                    164,
                    164,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, 24, 0, 0
            ));

            final ImageView qrImage = new ImageView(context);
            qrImage.setScaleType(ImageView.ScaleType.FIT_XY);
            qrImage.setImageBitmap(createWalletQrCode(context, address));
            qrContainer.addView(qrImage, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT
            ));

            copyButton = new TextView(context);
            copyButton.setText(LocaleController.getString(R.string.WalletCopyAddress));
            copyButton.setTextColor(WALLET_BLUE);
            copyButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            copyButton.setGravity(Gravity.CENTER);
            copyButton.setCompoundDrawablePadding(dp(4));
            copyButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            copyButton.setPadding(dp(10), 0, dp(12), 0);
            copyButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(18),
                    0xFFF0F9FE,
                    0xFFE2F3FC
            ));
            final Drawable copyIcon = context.getResources().getDrawable(R.drawable.wallet_copy).mutate();
            copyIcon.setColorFilter(new PorterDuffColorFilter(WALLET_BLUE, PorterDuff.Mode.SRC_IN));
            copyIcon.setBounds(0, 0, dp(14), dp(14));
            copyButton.setCompoundDrawables(copyIcon, null, null, null);
            ScaleStateListAnimator.apply(copyButton);
            frontFace.addView(copyButton, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    28,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, 202, 0, 0
            ));

            backFace = new FrameLayout(context);
            backFace.setVisibility(INVISIBLE);
            backFace.setRotationY(180.0f);
            qrCard.addView(backFace, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.MATCH_PARENT
            ));

            final TextView addressView = new TextView(context);
            addressView.setText(createAddressText(address));
            addressView.setTextColor(Color.BLACK);
            addressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            addressView.setTypeface(getWalletAddressTypeface(context));
            addressView.setGravity(Gravity.CENTER);
            addressView.setIncludeFontPadding(false);
            addressView.setMaxLines(4);
            addressView.setLineSpacing(0, 1.0f);
            backFace.addView(addressView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    112,
                    Gravity.TOP | Gravity.FILL_HORIZONTAL,
                    8, 32, 8, 0
            ));

            final TextView copiedView = new TextView(context);
            copiedView.setText(LocaleController.getString(R.string.WalletAddressCopiedShort));
            copiedView.setTextColor(WALLET_BLUE);
            copiedView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            copiedView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            copiedView.setGravity(Gravity.CENTER);
            copiedView.setCompoundDrawablePadding(dp(3));
            final Drawable checkIcon = context.getResources()
                    .getDrawable(R.drawable.msg_text_check)
                    .mutate();
            checkIcon.setColorFilter(new PorterDuffColorFilter(WALLET_BLUE, PorterDuff.Mode.SRC_IN));
            checkIcon.setBounds(0, 0, dp(16), dp(16));
            copiedView.setCompoundDrawables(checkIcon, null, null, null);
            backFace.addView(copiedView, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    28,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, 149, 0, 0
            ));

            showQrButton = new TextView(context);
            showQrButton.setText(LocaleController.getString(R.string.WalletShowMyQR));
            showQrButton.setTextColor(WALLET_BLUE);
            showQrButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            showQrButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            showQrButton.setGravity(Gravity.CENTER);
            showQrButton.setPadding(dp(12), 0, dp(12), 0);
            showQrButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(18),
                    0xFFF0F9FE,
                    0xFFE2F3FC
            ));
            ScaleStateListAnimator.apply(showQrButton);
            backFace.addView(showQrButton, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    28,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL,
                    0, 202, 0, 0
            ));

            final OnClickListener showAddress = v -> {
                if (!showingAddress && !flipInProgress) {
                    if (onCopyAddressListener != null) {
                        onCopyAddressListener.run();
                    }
                    setShowingAddress(true);
                }
            };
            qrContainer.setOnClickListener(showAddress);
            copyButton.setOnClickListener(showAddress);
            showQrButton.setOnClickListener(v -> setShowingAddress(false));
        }

        public void setOnCopyAddressListener(Runnable listener) {
            onCopyAddressListener = listener;
        }

        private void setShowingAddress(boolean showAddress) {
            if (showingAddress == showAddress || flipInProgress) {
                return;
            }
            flipInProgress = true;
            showingAddress = showAddress;

            qrCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            final float startRotation = qrCard.getRotationY();
            final float endRotation = showAddress ? 180.0f : 0.0f;
            flipAnimator = ValueAnimator.ofFloat(startRotation, endRotation);
            flipAnimator.setDuration(420);
            flipAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            flipAnimator.addUpdateListener(animation -> {
                final float rotation = (float) animation.getAnimatedValue();
                qrCard.setRotationY(rotation);
                final boolean showBackFace = rotation > 90.0f;
                frontFace.setVisibility(showBackFace ? INVISIBLE : VISIBLE);
                backFace.setVisibility(showBackFace ? VISIBLE : INVISIBLE);
            });
            flipAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    flipInProgress = false;
                    flipAnimator = null;
                    qrCard.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });
            flipAnimator.start();
        }

        private static CharSequence createAddressText(String address) {
            final String compactAddress = address.replace(" ", "");
            final SpannableStringBuilder result = new SpannableStringBuilder();
            int groupIndex = 0;
            for (int start = 0; start < compactAddress.length(); start += 4) {
                if (groupIndex > 0) {
                    result.append(groupIndex % 3 == 0 ? '\n' : ' ');
                }
                final int spanStart = result.length();
                result.append(compactAddress, start, Math.min(start + 4, compactAddress.length()));
                if ((groupIndex & 1) != 0) {
                    result.setSpan(
                            new ForegroundColorSpan(0xFF8E8E93),
                            spanStart,
                            result.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                groupIndex++;
            }
            return result;
        }
    }

    private static class AddressRingView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final PathMeasure pathMeasure = new PathMeasure();
        private final String address;
        private final RectF oval = new RectF();
        private final float[] position = new float[2];
        private final float[] tangent = new float[2];
        private String ringText;
        private float[] characterAdvances;
        private float advanceScale;
        private float perimeter;
        private float addressWidth;
        private float textOffset;
        private ValueAnimator scrollAnimator;

        public AddressRingView(Context context, String address) {
            super(context);
            this.address = formatAddress(
                    address.toUpperCase()
                            .replace('_', ' ')
                            .replace('-', ' ')
            ) + "   ·   ";
            paint.setColor(0xFF0072CE);
            paint.setTextSize(dp(12));
            paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            oval.set(
                    (width - dp(212)) / 2.0f - dp(12),
                    dp(22) - dp(12),
                    (width + dp(212)) / 2.0f + dp(12),
                    dp(22 + 244) + dp(12)
            );
            path.reset();
            path.addRoundRect(oval, dp(32), dp(32), Path.Direction.CW);
            pathMeasure.setPath(path, true);

            perimeter = pathMeasure.getLength();
            final float naturalAddressWidth = paint.measureText(address);
            final int repetitions = Math.max(1, Math.round(perimeter / naturalAddressWidth));
            final StringBuilder text = new StringBuilder(address.length() * repetitions);
            for (int i = 0; i < repetitions; i++) {
                text.append(address);
            }
            ringText = text.toString();

            characterAdvances = new float[ringText.length()];
            float naturalRingWidth = 0;
            for (int i = 0; i < ringText.length(); i++) {
                characterAdvances[i] = paint.measureText(ringText, i, i + 1);
                naturalRingWidth += characterAdvances[i];
            }
            advanceScale = perimeter / naturalRingWidth;
            addressWidth = perimeter / repetitions;
            restartScrollAnimator();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (characterAdvances == null || perimeter <= 0 || addressWidth <= 0) {
                return;
            }

            // Keep a fixed set of glyphs on the closed path and wrap their positions.
            // This avoids characters popping in or out at the path's seam.
            float characterOffset = 0;
            for (int i = 0; i < ringText.length(); i++) {
                final float advance = characterAdvances[i] * advanceScale;
                float distance = (characterOffset + advance / 2.0f - textOffset) % perimeter;
                if (distance < 0) {
                    distance += perimeter;
                }
                if (ringText.charAt(i) != ' '
                        && pathMeasure.getPosTan(distance, position, tangent)) {
                    canvas.save();
                    canvas.translate(position[0], position[1]);
                    canvas.rotate((float) Math.toDegrees(Math.atan2(tangent[1], tangent[0])));
                    canvas.drawText(ringText, i, i + 1, -characterAdvances[i] / 2.0f, dp(3), paint);
                    canvas.restore();
                }
                characterOffset += advance;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            restartScrollAnimator();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (scrollAnimator != null) {
                scrollAnimator.cancel();
                scrollAnimator = null;
            }
            super.onDetachedFromWindow();
        }

        private void restartScrollAnimator() {
            if (!isAttachedToWindow() || addressWidth <= 0) {
                return;
            }
            if (scrollAnimator != null) {
                scrollAnimator.cancel();
            }
            scrollAnimator = ValueAnimator.ofFloat(0, addressWidth);
            scrollAnimator.setDuration(Math.max(1L, (long) (addressWidth / dp(24) * 1000)));
            scrollAnimator.setInterpolator(new LinearInterpolator());
            scrollAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scrollAnimator.addUpdateListener(animation -> {
                textOffset = (float) animation.getAnimatedValue();
                invalidate();
            });
            scrollAnimator.start();
        }
    }

    private static class WalletSheetGradientDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final float[] cornerRadii = {
                dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0
        };

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            rect.set(bounds);
            path.reset();
            path.addRoundRect(rect, cornerRadii, Path.Direction.CW);
            paint.setShader(new LinearGradient(
                    0,
                    bounds.top,
                    0,
                    bounds.bottom,
                    new int[]{WALLET_SHEET_BLUE, WALLET_BLUE_LIGHT},
                    new float[]{0.15f, 0.53f},
                    Shader.TileMode.CLAMP
            ));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static class LearnCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        private final ImageView icon;
        private final LinearLayout layout;
        private final TextView header;
        private final TextView text;

        public LearnCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setPadding(dp(36), dp(8), dp(36), dp(8));

            icon = new ImageView(context);
            addView(icon, LayoutHelper.createFrame(28, 28, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 28 + 14, 0, 0, 0));

            header = new TextView(context);
            header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            header.setTypeface(AndroidUtilities.bold());
            layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            layout.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 2));

            updateColors();
        }

        public void set(int icon, CharSequence header, CharSequence text) {
            this.icon.setImageResource(icon);
            this.header.setText(header);
            this.text.setText(text);
        }

        @Override
        public void updateColors() {
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        }

        public static class Factory extends UItem.UItemFactory<LearnCell> {
            static { setup(new LearnCell.Factory()); }

            @Override
            public LearnCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new LearnCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((LearnCell) view).set(item.iconResId, item.text, item.subtext);
            }

            public static UItem of(int icon, CharSequence title, CharSequence text) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.iconResId = icon;
                item.text = title;
                item.subtext = text;
                return item;
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean isShadow() {
                return true;
            }
        }
    }

    public static class TabsView extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final LinearLayout layout;
        private final TextView[] tabs;

        private int selected = 0;
        public boolean shadowed = false;

        private final AnimatedFloat animatedSelected;

        public TabsView(Context context, CharSequence[] tabTitles, Utilities.Callback<Integer> onTabClick, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context) {
                private final RectF lowRect = new RectF(), highRect = new RectF(), rect = new RectF();
                private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private void set(TextView tab, RectF bounds) {
                    bounds.set(tab.getX(), tab.getY(), tab.getX() + tab.getWidth(), tab.getY() + tab.getHeight());
                }
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float selected = animatedSelected.set(TabsView.this.selected);
                    final int low = Utilities.clamp((int) Math.floor(selected), tabs.length, 0);
                    final int high = Utilities.clamp((int) Math.ceil(selected), tabs.length, 0);

                    if (low >= 0 && low < tabs.length)
                        set(tabs[low], lowRect);
                    if (high >= 0 && high < tabs.length)
                        set(tabs[high], highRect);
                    AndroidUtilities.lerp(lowRect, highRect, selected - low, rect);

                    backgroundPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f));
                    canvas.drawRoundRect(rect, dp(14), dp(14), backgroundPaint);

                    super.dispatchDraw(canvas);
                }
            };
            animatedSelected = new AnimatedFloat(() -> AndroidUtilities.runOnUIThread(this::stepAnimation), 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(dp(3), dp(3), dp(3), dp(3));

            this.tabs = new TextView[tabTitles.length];
            for (int i = 0; i < tabTitles.length; ++i) {
                final TextView tab = new TextView(context);
                tab.setPadding(dp(12), dp(6), dp(12), dp(6));
                tab.setTypeface(AndroidUtilities.bold());
                tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                tab.setText(tabTitles[i]);
                final int index = i;
                tab.setOnClickListener(v -> {
                    if (onTabClick != null) onTabClick.run(index);
                    setSelected(index);
                });
                ScaleStateListAnimator.apply(tab);
                layout.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0.0f, Gravity.LEFT | Gravity.CENTER_VERTICAL, i == 0 ? 0 : 3, 0, 0, 0));
                this.tabs[i] = tab;
            }

            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            updateColors();
        }

        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (shadowed && child == layout) {
                shadowPaint.setShadowLayer(dpf2(1.67f), 0, dpf2(0.67f), 0x1F000000);
                shadowPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                AndroidUtilities.rectTmp.set(layout.getX(), layout.getY(), layout.getX() + layout.getWidth(), layout.getY() + layout.getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(18), dp(18), shadowPaint);
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        private void stepAnimation() {
            final float selected = animatedSelected.set(this.selected);
            for (int i = 0; i < tabs.length; ++i) {
                final float tabSelected = Utilities.clamp01(1 - Math.abs(selected - i));
                tabs[i].setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), tabSelected));
            }
            layout.invalidate();
        }

        @Override
        public void updateColors() {
            stepAnimation();
            layout.setBackground(shadowed ? null : Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        }

        public void setSelected(int selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            stepAnimation();
        }

        public void setTabVisible(int index, boolean visible) {
            if (index < 0 || index >= tabs.length) return;
            tabs[index].setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        public boolean isTabVisible(int index) {
            if (index < 0 || index >= tabs.length) return false;
            return tabs[index].getVisibility() == View.VISIBLE;
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
        if (tosText != null) {
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tosText.getLayoutParams();
            lp.bottomMargin = dp(24) + bottom;
            tosText.setLayoutParams(lp);
        }
    }
}

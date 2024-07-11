package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Components.Bulletin.DURATION_PROLONG;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OverlayActionBarLayoutDialog;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.Stars.StarsController;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;

public class BotWebViewAttachedSheet implements NotificationCenter.NotificationCenterDelegate, BaseFragment.AttachedSheet {
    public final static int TYPE_WEB_VIEW_BUTTON = 0, TYPE_SIMPLE_WEB_VIEW_BUTTON = 1, TYPE_BOT_MENU_BUTTON = 2, TYPE_WEB_VIEW_BOT_APP = 3;

    public final static int FLAG_FROM_INLINE_SWITCH = 1;
    public final static int FLAG_FROM_SIDE_MENU = 2;
    private int lineColor;

    public void showJustAddedBulletin() {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                currentBot = bot;
                break;
            }
        }
        if (currentBot == null) {
            return;
        }
        String str;
        if (currentBot.show_in_side_menu && currentBot.show_in_attach_menu) {
            str = LocaleController.formatString("BotAttachMenuShortcatAddedAttachAndSide", R.string.BotAttachMenuShortcatAddedAttachAndSide, user.first_name);
        } else if (currentBot.show_in_side_menu) {
            str = LocaleController.formatString("BotAttachMenuShortcatAddedSide", R.string.BotAttachMenuShortcatAddedSide, user.first_name);
        } else {
            str = LocaleController.formatString("BotAttachMenuShortcatAddedAttach", R.string.BotAttachMenuShortcatAddedAttach, user.first_name);
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory.of(windowView, resourcesProvider)
                    .createSimpleBulletin(R.raw.contact_check, AndroidUtilities.replaceTags(str))
                    .setDuration(DURATION_PROLONG)
                    .show(true);
        }, 200);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TYPE_WEB_VIEW_BUTTON,
            TYPE_SIMPLE_WEB_VIEW_BUTTON,
            TYPE_BOT_MENU_BUTTON,
            TYPE_WEB_VIEW_BOT_APP
    })
    public @interface WebViewType {}

    private final static int POLL_PERIOD = 60000;

    private final static SimpleFloatPropertyCompat<BotWebViewAttachedSheet> ACTION_BAR_TRANSITION_PROGRESS_VALUE = new SimpleFloatPropertyCompat<BotWebViewAttachedSheet>("actionBarTransitionProgress", obj -> obj.actionBarTransitionProgress, (obj, value) -> {
        obj.actionBarTransitionProgress = value;
        obj.windowView.invalidate();
        obj.actionBar.setAlpha(value);
        obj.updateLightStatusBar();
    }).setMultiplier(100f);
    private float actionBarTransitionProgress = 0f;
    private SpringAnimation springAnimation;

    private Boolean wasLightStatusBar;

    public final WindowView windowView;

    @Override
    public WindowView getWindowView() {
        return windowView;
    }

    private long lastSwipeTime;

    private ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
    private BotWebViewContainer webViewContainer;
    private ChatAttachAlertBotWebViewLayout.WebProgressView progressView;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean ignoreLayout;

    private WebViewRequestProps requestProps;
    private TLObject requestResponse;
    private long requestResponseTime;

    private int currentAccount;
    private long botId;
    private long peerId;
    private long queryId;
    private int replyToMsgId;
    private boolean silent;
    private String buttonText;
    private boolean forceExpnaded;

    private boolean defaultFullsize = false;
    private Boolean fullsize = null;
    private boolean needsContext;

    public static class MainButtonSettings {

        public boolean isVisible;
        public boolean isActive;
        public String text;
        public int color;
        public int textColor;
        public boolean isProgressVisible;

        public static MainButtonSettings of(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible) {
            MainButtonSettings s = new MainButtonSettings();
            s.isVisible = isVisible;
            s.isActive = isActive;
            s.text = text;
            s.color = color;
            s.textColor = textColor;
            s.isProgressVisible = isProgressVisible;
            return s;
        }

    }

    public BottomSheetTabs.WebTabData saveState() {
        BottomSheetTabs.WebTabData tab = new BottomSheetTabs.WebTabData();
        tab.actionBarColor = actionBarColor;
        tab.actionBarColorKey = actionBarColorKey;
        tab.overrideActionBarColor = overrideBackgroundColor;
        tab.backgroundColor = backgroundPaint.getColor();
        tab.props = requestProps;
        tab.ready = webViewContainer != null && webViewContainer.isPageLoaded();
        tab.themeIsDark = Theme.isCurrentThemeDark();
        tab.lastUrl = webViewContainer != null ? webViewContainer.getUrlLoaded() : null;
        tab.expanded = swipeContainer != null && ((1f - Math.min(swipeContainer.getTopActionBarOffsetY(), swipeContainer.getTranslationY() - swipeContainer.getTopActionBarOffsetY()) / swipeContainer.getTopActionBarOffsetY()) > .5f) || forceExpnaded || getFullSize();
        tab.fullsize = getFullSize();
        tab.expandedOffset = swipeContainer != null ? swipeContainer.getOffsetY() : Float.MAX_VALUE;
        tab.backButton = backButtonShown;
        tab.settings = settingsItem != null && settingsItem.getVisibility() == View.VISIBLE;
        tab.main = mainButtonSettings;
        tab.confirmDismiss = needCloseConfirmation;
        tab.needsContext = needsContext;
        BotWebViewContainer.MyWebView webView = webViewContainer == null ? null : webViewContainer.getWebView();
        if (webView != null) {
            webViewContainer.preserveWebView();
            tab.webView = webView;
            tab.webViewProxy = webViewContainer == null ? null : webViewContainer.getProxy();
            tab.webViewWidth = webView.getWidth();
            tab.webViewHeight = webView.getHeight();
            tab.webViewScroll = webView.getScrollY();
            webView.onPause();
//            webView.pauseTimers();
        }
        return tab;
    }

    public boolean showExpanded;
    public float showOffsetY = Float.MAX_VALUE;

    public boolean restoreState(BaseFragment fragment, BottomSheetTabs.WebTabData tab) {
        if (tab == null || tab.props == null) return false;
        if (tab.overrideActionBarColor) {
            setBackgroundColor(tab.backgroundColor, false);
        }
        setActionBarColor(!tab.overrideActionBarColor ? Theme.getColor(tab.actionBarColorKey < 0 ? Theme.key_windowBackgroundWhite : tab.actionBarColorKey, resourcesProvider) : tab.actionBarColor, tab.overrideActionBarColor, false);
        showExpanded = tab.expanded;
        showOffsetY = tab.expandedOffset;
        fullsize = tab.fullsize;
        needsContext = tab.needsContext;
        webViewContainer.setIsBackButtonVisible(backButtonShown = tab.backButton);
        if (backDrawable != null) {
            backDrawable.setRotation(backButtonShown ? 0f : 1f, false);
        }
        if (tab.main != null) {
            setMainButton(tab.main);
        }
        needCloseConfirmation = tab.confirmDismiss;
        if (tab.webView != null) {
//            tab.webView.resumeTimers();
            tab.webView.onResume();
            webViewContainer.replaceWebView(tab.webView, tab.webViewProxy);
            webViewContainer.setState(tab.ready || tab.webView.isPageLoaded(), tab.lastUrl);
            if (Theme.isCurrentThemeDark() != tab.themeIsDark) {
//                webViewContainer.notifyThemeChanged();
                if (webViewContainer.getWebView() != null) {
                    webViewContainer.getWebView().animate().cancel();
                    webViewContainer.getWebView().animate().alpha(0).start();
                }

                progressView.setLoadProgress(0);
                progressView.setAlpha(1f);
                progressView.setVisibility(View.VISIBLE);

                webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
                webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
                webViewContainer.setState(false, null);
                if (webViewContainer.getWebView() != null) {
                    webViewContainer.getWebView().loadUrl("about:blank");
                }

                tab.props.response = null;
                tab.props.responseTime = 0;
            }
        } else {
            tab.props.response = null;
            tab.props.responseTime = 0;
        }
        requestWebView(fragment, tab.props);
        if (settingsItem != null) {
            settingsItem.setVisibility(tab.settings ? View.VISIBLE : View.GONE);
        }
        return true;
    }

    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint dimPaint = new Paint();
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int actionBarColorKey = -1;
    private int actionBarColor;
    private int navBarColor;
    private boolean actionBarIsLight;
    private Paint actionBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean overrideBackgroundColor;

    private BackDrawable backDrawable;
    private ActionBar actionBar;
    private Drawable actionBarShadow;
    private ActionBarMenuSubItem settingsItem;
    private TLRPC.BotApp currentWebApp;

    private boolean dismissed;
    private boolean backButtonShown;

    private Activity parentActivity;

    private boolean mainButtonWasVisible, mainButtonProgressWasVisible;
    private TextView mainButton;
    private RadialProgressView radialProgressView;

    private boolean needCloseConfirmation;

    private VerticalPositionAutoAnimator mainButtonAutoAnimator, radialProgressAutoAnimator;

    private Runnable pollRunnable = () -> {
        if (!dismissed && queryId != 0) {
            TLRPC.TL_messages_prolongWebView prolongWebView = new TLRPC.TL_messages_prolongWebView();
            prolongWebView.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
            prolongWebView.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId);
            prolongWebView.query_id = queryId;
            prolongWebView.silent = silent;
            if (replyToMsgId != 0) {
                prolongWebView.reply_to = SendMessagesHelper.getInstance(currentAccount).createReplyInput(replyToMsgId);
                prolongWebView.flags |= 1;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(prolongWebView, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (dismissed) {
                    return;
                }
                if (error != null) {
                    dismiss();
                } else {
                    AndroidUtilities.runOnUIThread(this.pollRunnable, POLL_PERIOD);
                }
            }));
        }
    };

    private final Context context;
    private final BaseFragment fragment;

    public Context getContext() {
        return context;
    }

    public BotWebViewAttachedSheet(@NonNull BaseFragment fragment) {
        this.fragment = fragment;
        this.context = fragment.getContext();
        this.resourcesProvider = fragment.getResourceProvider();
        lineColor = Theme.getColor(Theme.key_sheet_scrollUp);

        swipeContainer = new ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int availableHeight = MeasureSpec.getSize(heightMeasureSpec);

                int padding;
                if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    padding = (int) (availableHeight / 3.5f);
                } else {
                    padding = (availableHeight / 5 * 2);
                }
                if (padding < 0) {
                    padding = 0;
                }

                if (getOffsetY() != padding && !dismissed) {
                    ignoreLayout = true;
                    setOffsetY(padding);
                    ignoreLayout = false;
                }

                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight + dp(24) - (mainButtonWasVisible ? mainButton.getLayoutParams().height : 0), MeasureSpec.EXACTLY));
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        webViewContainer = new BotWebViewContainer(getContext(), resourcesProvider, getColor(Theme.key_windowBackgroundWhite)) {
            @Override
            public void onWebViewCreated() {
                super.onWebViewCreated();
                swipeContainer.setWebView(webViewContainer.getWebView());
            }
        };
        webViewContainer.setDelegate(new BotWebViewContainer.Delegate() {
            private boolean sentWebViewData;

            @Override
            public void onCloseRequested(Runnable callback) {
                dismiss(false, callback);
            }

            @Override
            public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
                BotWebViewAttachedSheet.this.needCloseConfirmation = needConfirmation;
            }

            @Override
            public void onSendWebViewData(String data) {
                if (queryId != 0 || sentWebViewData) {
                    return;
                }
                sentWebViewData = true;

                TLRPC.TL_messages_sendWebViewData sendWebViewData = new TLRPC.TL_messages_sendWebViewData();
                sendWebViewData.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                sendWebViewData.random_id = Utilities.random.nextLong();
                sendWebViewData.button_text = buttonText;
                sendWebViewData.data = data;
                ConnectionsManager.getInstance(currentAccount).sendRequest(sendWebViewData, (response, error) -> {
                    if (response instanceof TLRPC.TL_updates) {
                        MessagesController.getInstance(currentAccount).processUpdates((TLRPC.TL_updates) response, false);
                    }
                    AndroidUtilities.runOnUIThread(BotWebViewAttachedSheet.this::dismiss);
                });
            }

            @Override
            public void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {
                actionBarColorKey = colorKey;
                setActionBarColor(color, isOverrideColor, true);
            }

            @Override
            public void onWebAppSetBackgroundColor(int color) {
                setBackgroundColor(color, true);
            }

            @Override
            public void onSetBackButtonVisible(boolean visible) {
                backButtonShown = visible;
                backDrawable.setRotation(visible ? 0f : 1f, true);
            }

            @Override
            public void onSetSettingsButtonVisible(boolean visible) {
                if (settingsItem != null) {
                    settingsItem.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response) {
                if (getContext() == null) {
                    return;
                }
                BaseFragment parentFragment = ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment();
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                    AndroidUtilities.hideKeyboard(windowView);
                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    progressDialog.showDelayed(150);
                    StarsController.getInstance(currentAccount).openPaymentForm(null, inputInvoice, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                        progressDialog.dismiss();
                    }, status -> {
                        webViewContainer.onInvoiceStatusUpdate(slug, status);
                    });
                    return;
                } else if (response instanceof TLRPC.PaymentForm) {
                    TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, slug, parentFragment);
                } else if (response instanceof TLRPC.PaymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                }

                if (paymentFormActivity != null) {
                    swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());

                    AndroidUtilities.hideKeyboard(windowView);
                    OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(getContext(), resourcesProvider);
                    overlayActionBarLayoutDialog.show();
                    paymentFormActivity.setPaymentFormCallback(status -> {
                        if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                            overlayActionBarLayoutDialog.dismiss();
                        }

                        webViewContainer.onInvoiceStatusUpdate(slug, status.name().toLowerCase(Locale.ROOT));
                    });
                    paymentFormActivity.setResourcesProvider(resourcesProvider);
                    overlayActionBarLayoutDialog.addFragment(paymentFormActivity);
                }
            }

            @Override
            public void onWebAppExpand() {
                if (/* System.currentTimeMillis() - lastSwipeTime <= 1000 || */ swipeContainer.isSwipeInProgress()) {
                    return;
                }
                swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
            }

            @Override
            public void onWebAppSwitchInlineQuery(TLRPC.User botUser, String query, List<String> chatTypes) {
                if (chatTypes.isEmpty()) {
                    if (parentActivity instanceof LaunchActivity) {
                        BaseFragment lastFragment = ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment();
                        if (lastFragment instanceof ChatActivity) {
                            ((ChatActivity) lastFragment).getChatActivityEnterView().setFieldText("@" + UserObject.getPublicUsername(botUser) + " " + query);
                            dismiss();
                        }
                    }
                } else {
                    Bundle args = new Bundle();
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT);
                    args.putBoolean("onlySelect", true);

                    args.putBoolean("allowGroups", chatTypes.contains("groups"));
                    args.putBoolean("allowMegagroups", chatTypes.contains("groups"));
                    args.putBoolean("allowLegacyGroups", chatTypes.contains("groups"));
                    args.putBoolean("allowUsers", chatTypes.contains("users"));
                    args.putBoolean("allowChannels", chatTypes.contains("channels"));
                    args.putBoolean("allowBots", chatTypes.contains("bots"));

                    DialogsActivity dialogsActivity = new DialogsActivity(args);
                    AndroidUtilities.hideKeyboard(windowView);
                    OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(getContext(), resourcesProvider);
                    dialogsActivity.setDelegate((fragment, dids, message1, param, topicsFragment) -> {
                        long did = dids.get(0).dialogId;

                        Bundle args1 = new Bundle();
                        args1.putBoolean("scrollToTopOnResume", true);
                        if (DialogObject.isEncryptedDialog(did)) {
                            args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                        } else if (DialogObject.isUserDialog(did)) {
                            args1.putLong("user_id", did);
                        } else {
                            args1.putLong("chat_id", -did);
                        }
                        args1.putString("start_text", "@" + UserObject.getPublicUsername(botUser) + " " + query);

                        if (parentActivity instanceof LaunchActivity) {
                            BaseFragment lastFragment = ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment();
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, lastFragment)) {
                                overlayActionBarLayoutDialog.dismiss();

                                dismissed = true;
                                AndroidUtilities.cancelRunOnUIThread(pollRunnable);

                                webViewContainer.destroyWebView();
                                NotificationCenter.getInstance(currentAccount).removeObserver(BotWebViewAttachedSheet.this, NotificationCenter.webViewResultSent);
                                NotificationCenter.getGlobalInstance().removeObserver(BotWebViewAttachedSheet.this, NotificationCenter.didSetNewTheme);
                                release();

                                lastFragment.presentFragment(new INavigationLayout.NavigationParams(new ChatActivity(args1)).setRemoveLast(true));
                            }
                        }
                        return true;
                    });
                    overlayActionBarLayoutDialog.show();
                    overlayActionBarLayoutDialog.addFragment(dialogsActivity);
                }
            }

            @Override
            public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible) {
                setMainButton(MainButtonSettings.of(isVisible, isActive, text, color, textColor, isProgressVisible));
            }

            @Override
            public String getWebAppName() {
                if (currentWebApp != null) {
                    return currentWebApp.title;
                }
                return null;
            }

            @Override
            public boolean isClipboardAvailable() {
                return MediaDataController.getInstance(currentAccount).botInAttachMenu(botId);
            }
        });

        linePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        linePaint.setStrokeWidth(dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        dimPaint.setColor(0x40000000);
        actionBarColor = getColor(Theme.key_windowBackgroundWhite);
        navBarColor = getColor(Theme.key_windowBackgroundGray);
        checkNavBarColor();
        windowView = new WindowView(getContext());
        windowView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight > dp(20)) {
                swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
            }
        });
        windowView.addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        mainButton = new TextView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        mainButton.setVisibility(View.GONE);
        mainButton.setAlpha(0f);
        mainButton.setSingleLine();
        mainButton.setGravity(Gravity.CENTER);
        mainButton.setTypeface(AndroidUtilities.bold());
        int padding = dp(16);
        mainButton.setPadding(padding, 0, padding, 0);
        mainButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        mainButton.setOnClickListener(v -> webViewContainer.onMainButtonPressed());
        windowView.addView(mainButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        mainButtonAutoAnimator = VerticalPositionAutoAnimator.attach(mainButton);

        radialProgressView = new RadialProgressView(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
                params.rightMargin = dp(10);
            }
        };
        radialProgressView.setSize(dp(18));
        radialProgressView.setAlpha(0f);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        radialProgressView.setVisibility(View.GONE);
        windowView.addView(radialProgressView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 10));
        radialProgressAutoAnimator = VerticalPositionAutoAnimator.attach(radialProgressView);

        actionBarShadow = ContextCompat.getDrawable(getContext(), R.drawable.header_shadow).mutate();

        actionBar = new ActionBar(getContext(), resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
        backDrawable.setAnimationTime(200.0f);
        backDrawable.setColorFilter(null);
        backDrawable.setRotation(1f, false);
        updateActionBarColors();
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    onCheckDismissByUser();
                }
            }
        });
        actionBar.setAlpha(0f);
        windowView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        windowView.addView(progressView = new ChatAttachAlertBotWebViewLayout.WebProgressView(getContext(), resourcesProvider), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        webViewContainer.setWebViewProgressListener(progress -> {
            progressView.setLoadProgressAnimated(progress);
            if (progress == 1f) {
                ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> progressView.setAlpha((Float) animation.getAnimatedValue()));
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressView.setVisibility(View.GONE);
                    }
                });
                animator.start();
            }
        });


        if (springAnimation == null) {
            springAnimation = new SpringAnimation(this, ACTION_BAR_TRANSITION_PROGRESS_VALUE)
                .setSpring(new SpringForce()
                        .setStiffness(1200f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                );
        }

        swipeContainer.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        swipeContainer.setScrollListener(()->{
            if (swipeContainer.getSwipeOffsetY() > 0) {
                dimPaint.setAlpha((int) (0x40 * (1f - MathUtils.clamp(swipeContainer.getSwipeOffsetY() / (float)swipeContainer.getHeight(), 0, 1))));
            } else {
                dimPaint.setAlpha(0x40);
            }
            windowView.invalidate();
            webViewContainer.invalidateViewPortHeight();

            if (springAnimation != null) {
                float progress = (1f - Math.min(swipeContainer.getTopActionBarOffsetY(), swipeContainer.getTranslationY() - swipeContainer.getTopActionBarOffsetY()) / swipeContainer.getTopActionBarOffsetY());
                float newPos = (progress > 0.5f ? 1 : 0) * 100f;
                if (springAnimation.getSpring().getFinalPosition() != newPos) {
                    springAnimation.getSpring().setFinalPosition(newPos);
                    springAnimation.start();
                }
            }
            float offsetY = Math.max(0, swipeContainer.getSwipeOffsetY());
            mainButtonAutoAnimator.setOffsetY(offsetY);
            radialProgressAutoAnimator.setOffsetY(offsetY);
            lastSwipeTime = System.currentTimeMillis();
        });
        swipeContainer.setScrollEndListener(()-> webViewContainer.invalidateViewPortHeight(true));
        swipeContainer.setDelegate(() -> {
//            if (can_minimize) {
                dismiss(true, null);
//            } else {
//                if (!onCheckDismissByUser()) {
//                    swipeContainer.stickTo(0);
//                }
//            }
        });
        swipeContainer.setTopActionBarOffsetY(ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - dp(24));
        swipeContainer.setIsKeyboardVisible(obj -> windowView.getKeyboardHeight() >= dp(20));
    }

    public float getContainerTop() {
        float transitionProgress = AndroidUtilities.isTablet() ? 0 : actionBarTransitionProgress;
        return AndroidUtilities.isTablet() ? lerp(swipeContainer.getTranslationY() + dp(12), AndroidUtilities.statusBarHeight / 2f, actionBarTransitionProgress) :
                (lerp(swipeContainer.getTranslationY(), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f, transitionProgress) + dp(12));
    }

    public void attachInternal() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);

        if (fragment.getLayoutContainer() != null) {
            fragment.getLayoutContainer().addView(windowView);
        }
    }

    @Override
    public void release() {
        if (webViewContainer != null) {
            webViewContainer.destroyWebView();
        }
        if (fragment.sheetsStack != null) {
            fragment.sheetsStack.remove(this);
        }
        if (windowView != null && windowView.getParent() instanceof ViewGroup) {
            ((ViewGroup) windowView.getParent()).removeView(windowView);
        }
    }

    @Override
    public boolean attachedToParent() {
        return windowView != null;
    }

    //    @Override
//    protected void onStart() {
//        super.onStart();
//
//        Context context = getContext();
//        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
//            context = ((ContextWrapper) context).getBaseContext();
//        }
//        if (context instanceof LaunchActivity) {
//            ((LaunchActivity) context).addOverlayPasscodeView(passcodeView);
//        }
//    }
//
//    @Override
//    protected void onStop() {
//        super.onStop();
//
//        Context context = getContext();
//        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
//            context = ((ContextWrapper) context).getBaseContext();
//        }
//        if (context instanceof LaunchActivity) {
//            ((LaunchActivity) context).removeOverlayPasscodeView(passcodeView);
//        }
//    }

    public void setParentActivity(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    private void updateActionBarColors() {
        if (!overrideBackgroundColor) {
            backDrawable.setColor(getColor(Theme.key_windowBackgroundWhiteBlackText));
            backDrawable.setRotatedColor(getColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setTitleColor(getColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsColor(getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setItemsBackgroundColor(getColor(Theme.key_actionBarWhiteSelector), false);
            actionBar.setPopupBackgroundColor(getColor(Theme.key_actionBarDefaultSubmenuBackground), false);
            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItem), false, false);
            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, false);
            actionBar.setPopupItemsSelectorColor(getColor(Theme.key_dialogButtonSelector), false);
        }
    }

    private void updateLightStatusBar() {
        boolean lightStatusBar;
        if (overrideBackgroundColor) {
            lightStatusBar = !actionBarIsLight && actionBarTransitionProgress >= 0.85f;
        } else {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
            lightStatusBar = !AndroidUtilities.isTablet() && ColorUtils.calculateLuminance(color) >= 0.721f && actionBarTransitionProgress >= 0.85f;
        }
        if (wasLightStatusBar != null && wasLightStatusBar == lightStatusBar) {
            return;
        }
        wasLightStatusBar = lightStatusBar;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = windowView.getSystemUiVisibility();
            if (lightStatusBar) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            windowView.setSystemUiVisibility(flags);
        }
    }

    public static JSONObject makeThemeParams(Theme.ResourcesProvider resourcesProvider) {
        try {
            JSONObject jsonObject = new JSONObject();
            final int backgroundColor = Theme.getColor(Theme.key_dialogBackground, resourcesProvider);
            jsonObject.put("bg_color", backgroundColor);
            jsonObject.put("section_bg_color", Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            jsonObject.put("secondary_bg_color", Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            jsonObject.put("text_color", Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            jsonObject.put("hint_color", Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
            jsonObject.put("link_color", Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            jsonObject.put("button_color", Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            jsonObject.put("button_text_color", Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            jsonObject.put("header_bg_color", Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
            jsonObject.put("accent_text_color", Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider)));
            jsonObject.put("section_header_text_color", Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider)));
            jsonObject.put("subtitle_text_color", Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider)));
            jsonObject.put("destructive_text_color", Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)));
            jsonObject.put("section_separator_color", Theme.blendOver(backgroundColor, Theme.getColor(Theme.key_divider, resourcesProvider)));
            return jsonObject;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public void setDefaultFullsize(boolean fullsize) {
        if (this.defaultFullsize != fullsize) {
            this.defaultFullsize = fullsize;

            if (swipeContainer != null) {
                swipeContainer.setFullSize(getFullSize());
            }
        }
    }

    public void setWasOpenedByLinkIntent(boolean value) {
        if (webViewContainer != null) {
            webViewContainer.setWasOpenedByLinkIntent(value);
        }
    }

    public void setNeedsContext(boolean needsContext) {
        this.needsContext = needsContext;
    }

    public boolean getFullSize() {
        return fullsize == null ? defaultFullsize : fullsize;
    }

    public static class WebViewRequestProps {

        public int currentAccount;
        public long peerId;
        public long botId;
        public String buttonText;
        public String buttonUrl;
        public @WebViewType int type;
        public int replyToMsgId;
        public boolean silent;
        public TLRPC.BotApp app;
        public boolean allowWrite;
        public String startParam;
        public TLRPC.User botUser;
        public int flags;
        public boolean compact;

        public TLObject response;
        public long responseTime;

        public static WebViewRequestProps of(
            int currentAccount,
            long peerId,
            long botId,
            String buttonText,
            String buttonUrl,
            @WebViewType int type,
            int replyToMsgId,
            boolean silent,
            TLRPC.BotApp app,
            boolean allowWrite,
            String startParam,
            TLRPC.User botUser,
            int flags,
            boolean compact
        ) {
            WebViewRequestProps p = new WebViewRequestProps();
            p.currentAccount = currentAccount;
            p.peerId = peerId;
            p.botId = botId;
            p.buttonText = buttonText;
            p.buttonUrl = buttonUrl;
            p.type = type;
            p.replyToMsgId = replyToMsgId;
            p.silent = silent;
            p.app = app;
            p.allowWrite = allowWrite;
            p.startParam = startParam;
            p.botUser = botUser;
            p.flags = flags;
            p.compact = compact;
            if (!compact && !TextUtils.isEmpty(buttonUrl)) {
                try {
                    Uri uri = Uri.parse(buttonUrl);
                    p.compact = TextUtils.equals(uri.getQueryParameter("mode"), "compact");
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return p;
        }

        public void applyResponse(TLObject response) {
            this.response = response;
            this.responseTime = System.currentTimeMillis();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof WebViewRequestProps))
                return false;
            final WebViewRequestProps p = (WebViewRequestProps) obj;
            return (
                currentAccount == p.currentAccount &&
                peerId == p.peerId &&
                botId == p.botId &&
                TextUtils.equals(buttonUrl, p.buttonUrl) &&
                type == p.type &&
                replyToMsgId == p.replyToMsgId &&
                silent == p.silent &&
                (app == null ? 0 : app.id) == (p.app == null ? 0 : p.app.id) &&
                allowWrite == p.allowWrite &&
                TextUtils.equals(startParam, p.startParam) &&
                (botUser == null ? 0 : botUser.id) == (p.botUser == null ? 0 : p.botUser.id) &&
                flags == p.flags
            );
        }
    }

    public void requestWebView(BaseFragment fragment, WebViewRequestProps props) {
        this.requestProps = props;
        this.currentAccount = props.currentAccount;
        this.peerId = props.peerId;
        this.botId = props.botId;
        this.replyToMsgId = props.replyToMsgId;
        this.silent = props.silent;
        this.buttonText = props.buttonText;
        this.currentWebApp = props.app;

        CharSequence title = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId));
        try {
            TextPaint tp = new TextPaint();
            tp.setTextSize(dp(20));
            title = Emoji.replaceEmoji(title, tp.getFontMetricsInt(), false);
        } catch (Exception ignore) {}
        actionBar.setTitle(title);
        ActionBarMenu menu = actionBar.createMenu();
        menu.removeAllViews();

        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                currentBot = bot;
                break;
            }
        }

        menu.addItem(R.id.menu_collapse_bot, R.drawable.arrow_more);
        ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(R.id.menu_open_bot, R.drawable.msg_bot, LocaleController.getString(R.string.BotWebViewOpenBot));
        settingsItem = otherItem.addSubItem(R.id.menu_settings, R.drawable.msg_settings, LocaleController.getString(R.string.BotWebViewSettings));
        settingsItem.setVisibility(View.GONE);
        otherItem.addSubItem(R.id.menu_reload_page, R.drawable.msg_retry, LocaleController.getString(R.string.BotWebViewReloadPage));
        if (currentBot != null && MediaDataController.getInstance(currentAccount).canCreateAttachedMenuBotShortcut(currentBot.bot_id)) {
            otherItem.addSubItem(R.id.menu_add_to_home_screen_bot, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut));
        }
        otherItem.addSubItem(R.id.menu_tos_bot, R.drawable.menu_intro, LocaleController.getString(R.string.BotWebViewToS));
        if (currentBot != null && (currentBot.show_in_side_menu || currentBot.show_in_attach_menu)) {
            otherItem.addSubItem(R.id.menu_delete_bot, R.drawable.msg_delete, LocaleController.getString(R.string.BotWebViewDeleteBot));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!webViewContainer.onBackPressed()) {
                        onCheckDismissByUser();
                    }
                } else if (id == R.id.menu_open_bot) {
                    Bundle bundle = new Bundle();
                    bundle.putLong("user_id", botId);
                    if (parentActivity instanceof LaunchActivity) {
                        ((LaunchActivity) parentActivity).presentFragment(new ChatActivity(bundle));
                    }
                    dismiss();
                } else if (id == R.id.menu_tos_bot) {
                    Browser.openUrl(getContext(), LocaleController.getString(R.string.BotWebViewToSLink));
                } else if (id == R.id.menu_reload_page) {
                    if (webViewContainer.getWebView() != null) {
                        webViewContainer.getWebView().animate().cancel();
                        webViewContainer.getWebView().animate().alpha(0).start();
                    }

                    progressView.setLoadProgress(0);
                    progressView.setAlpha(1f);
                    progressView.setVisibility(View.VISIBLE);

                    webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
                    webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
                    webViewContainer.reload();
                } else if (id == R.id.menu_settings) {
                    webViewContainer.onSettingsButtonPressed();
                } else if (id == R.id.menu_delete_bot) {
                    deleteBot(currentAccount, botId, () -> dismiss());
                } else if (id == R.id.menu_add_to_home_screen_bot) {
                    MediaDataController.getInstance(currentAccount).installShortcut(botId, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT);
                } else if (id == R.id.menu_collapse_bot) {
                    forceExpnaded = true;
                    dismiss(true, null);
                }
            }
        });

        final JSONObject themeParams = makeThemeParams(resourcesProvider);

        webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
        webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
        preloadShortcutBotIcon(props.botUser, currentBot);
        if (props.response != null) {
            loadFromResponse(true);
        } else {
            switch (props.type) {
                case TYPE_BOT_MENU_BUTTON: {
                    TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
                    req.platform = "android";
                    req.compact = props.compact;

                    req.url = props.buttonUrl;
                    req.flags |= 2;

                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 4;
                    }

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error != null) {

                        } else if (requestProps != null) {
                            requestProps.applyResponse(response);
                            loadFromResponse(false);
                        }
                    }));
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);

                    break;
                }
                case TYPE_SIMPLE_WEB_VIEW_BUTTON: {
                    TLRPC.TL_messages_requestSimpleWebView req = new TLRPC.TL_messages_requestSimpleWebView();
                    req.from_switch_webview = (props.flags & FLAG_FROM_INLINE_SWITCH) != 0;
                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                    req.platform = "android";
                    req.from_side_menu = (props.flags & FLAG_FROM_SIDE_MENU) != 0;
                    req.compact = props.compact;
                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 1;
                    }
                    if (!TextUtils.isEmpty(props.buttonUrl)) {
                        req.flags |= 8;
                        req.url = props.buttonUrl;
                    }
                    if (!TextUtils.isEmpty(props.startParam)) {
                        req.start_param = props.startParam;
                        req.flags |= 16;
                    }

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error != null) {

                        } else if (requestProps != null) {
                            requestProps.applyResponse(response);
                            loadFromResponse(false);
                        }
                    }));
                    break;
                }
                case TYPE_WEB_VIEW_BUTTON: {
                    TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId);
                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                    req.platform = "android";
                    req.compact = props.compact;
                    if (props.buttonUrl != null) {
                        req.url = props.buttonUrl;
                        req.flags |= 2;
                    }

                    if (replyToMsgId != 0) {
                        req.reply_to = SendMessagesHelper.getInstance(currentAccount).createReplyInput(replyToMsgId);
                        req.flags |= 1;
                    }

                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 4;
                    }

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error != null) {

                        } else if (requestProps != null) {
                            requestProps.applyResponse(response);
                            loadFromResponse(false);
                        }
                    }));
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);
                    break;
                }
                case TYPE_WEB_VIEW_BOT_APP: {
                    TLRPC.TL_messages_requestAppWebView req = new TLRPC.TL_messages_requestAppWebView();
                    TLRPC.TL_inputBotAppID botApp = new TLRPC.TL_inputBotAppID();
                    botApp.id = props.app.id;
                    botApp.access_hash = props.app.access_hash;

                    req.app = botApp;
                    req.write_allowed = props.allowWrite;
                    req.platform = "android";
                    req.peer = fragment instanceof ChatActivity ? ((ChatActivity) fragment).getCurrentUser() != null ? MessagesController.getInputPeer(((ChatActivity) fragment).getCurrentUser()) : MessagesController.getInputPeer(((ChatActivity) fragment).getCurrentChat())
                            : MessagesController.getInputPeer(props.botUser);
                    req.compact = props.compact;

                    if (!TextUtils.isEmpty(props.startParam)) {
                        req.start_param = props.startParam;
                        req.flags |= 2;
                    }

                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 4;
                    }

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 != null) {

                        } else if (requestProps != null) {
                            requestProps.applyResponse(response2);
                            loadFromResponse(false);
                        }
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                }
            }
        }
    }

    private void loadFromResponse(boolean fromTab) {
        if (requestProps == null) return;
        final long pollTimeout = Math.max(0, POLL_PERIOD - (System.currentTimeMillis() - requestProps.responseTime));
        String url = null;
        fullsize = null;
        if (requestProps.response instanceof TLRPC.TL_webViewResultUrl) {
            TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) requestProps.response;
            queryId = resultUrl.query_id;
            url = resultUrl.url;
            fullsize = resultUrl.fullsize;
        } else if (requestProps.response instanceof TLRPC.TL_appWebViewResultUrl) { // deprecated
            TLRPC.TL_appWebViewResultUrl result = (TLRPC.TL_appWebViewResultUrl) requestProps.response;
            queryId = 0;
            url = result.url;
        } else if (requestProps.response instanceof TLRPC.TL_simpleWebViewResultUrl) { // deprecated
            TLRPC.TL_simpleWebViewResultUrl resultUrl = (TLRPC.TL_simpleWebViewResultUrl) requestProps.response;
            queryId = 0;
            url = resultUrl.url;
        }
        if (url != null && !fromTab) {
            webViewContainer.loadUrl(currentAccount, url);
        }
        AndroidUtilities.runOnUIThread(pollRunnable, pollTimeout);
        if (swipeContainer != null) {
            swipeContainer.setFullSize(getFullSize());
        }
    }

    private void preloadShortcutBotIcon(TLRPC.User botUser, TLRPC.TL_attachMenuBot currentBot) {
        if (currentBot != null && currentBot.show_in_side_menu && !MediaDataController.getInstance(currentAccount).isShortcutAdded(botId, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT)) {
            TLRPC.User user = botUser;
            if (user == null) {
                user = MessagesController.getInstance(currentAccount).getUser(botId);
            }
            if (user != null && user.photo != null) {
                File f = FileLoader.getInstance(currentAccount).getPathToAttach(user.photo.photo_small, true);
                if (!f.exists()) {
                    MediaDataController.getInstance(currentAccount).preloadImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), FileLoader.PRIORITY_LOW);
                }
            }
        }
    }

    public static void deleteBot(int currentAccount, long botId, Runnable onDone) {
        String description;
        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                currentBot = bot;
                break;
            }
        }
        if (currentBot == null) {
            return;
        }
        String botName = currentBot.short_name;
        description = LocaleController.formatString("BotRemoveFromMenu", R.string.BotRemoveFromMenu, botName);
        TLRPC.TL_attachMenuBot finalCurrentBot = currentBot;
        new AlertDialog.Builder(LaunchActivity.getLastFragment().getContext())
                .setTitle(LocaleController.getString(R.string.BotRemoveFromMenuTitle))
                .setMessage(AndroidUtilities.replaceTags(description))
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    TLRPC.TL_messages_toggleBotInAttachMenu req = new TLRPC.TL_messages_toggleBotInAttachMenu();
                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                    req.enabled = false;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        MediaDataController.getInstance(currentAccount).loadAttachMenuBots(false, true);
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                    finalCurrentBot.show_in_side_menu = false;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.attachMenuBotsDidLoad);
                    MediaDataController.getInstance(currentAccount).uninstallShortcut(botId, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT);
                    if (onDone != null) {
                        onDone.run();
                    }
                })
                .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
                .show();
    }

    private int getColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void show() {
        show(false);
    }
    public void show(boolean lowBounce) {
        show(lowBounce, false);
    }
    public void show(boolean lowBounce, boolean instant) {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;

        windowView.setAlpha(0f);
        windowView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);

                swipeContainer.setSwipeOffsetY(lowBounce ? .5f * swipeContainer.getHeight() : swipeContainer.getHeight());
                windowView.setAlpha(1f);

                AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
                locker.lock();
                updateShownAnimated(true);

                if (showOffsetY != Float.MAX_VALUE) {
                    swipeContainer.setSwipeOffsetAnimationDisallowed(true);
                    swipeContainer.setOffsetY(showOffsetY);
                    swipeContainer.setSwipeOffsetAnimationDisallowed(false);
                }

                if (showExpanded || getFullSize()) {
                    if (instant) {
                        swipeContainer.setSwipeOffsetY(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
                        locker.unlock();
                    } else {
                        swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY(), locker::unlock);
                    }
                } else {
                    if (instant) {
                        swipeContainer.setSwipeOffsetY(0);
                        locker.unlock();
                    } else {
                        new SpringAnimation(swipeContainer, ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer.SWIPE_OFFSET_Y, 0)
                            .setSpring(new SpringForce(0)
                                .setDampingRatio(lowBounce ? SpringForce.DAMPING_RATIO_NO_BOUNCY : SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                                .setStiffness(lowBounce ? 800 : 500.0f)
                            ).addEndListener((animation, canceled, value, velocity) -> {
                                locker.unlock();
                            }).start();
                    }
                }
                swipeContainer.opened = true;
            }
        });

        attachInternal();
    }

    private float shown;
    private ValueAnimator shownAnimator;
    private void updateShownAnimated(boolean shown) {
        if (shownAnimator != null) {
            shownAnimator.cancel();
        }
        shownAnimator = ValueAnimator.ofFloat(this.shown, shown ? 1f : 0f);
        shownAnimator.addUpdateListener(anm -> {
            this.shown = (float) anm.getAnimatedValue();
            checkNavBarColor();
        });
        shownAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                BotWebViewAttachedSheet.this.shown = shown ? 1f : 0f;
                checkNavBarColor();
            }
        });
        shownAnimator.setDuration(200);
        shownAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        shownAnimator.start();
    }

    public long getBotId() {
        return botId;
    }

    @Override
    public boolean isShown() {
        return !dismissed;
    }

    @Override
    public boolean showDialog(Dialog dialog) {
        return false;
    }

    @Override
    public void setKeyboardHeightFromParent(int keyboardHeight) {
        // TODO
    }

    @Override
    public int getNavigationBarColor(int color) {
        return ColorUtils.blendARGB(color, navBarColor, shown);
    }

    @Override
    public boolean onBackPressed() {
        if (webViewContainer.onBackPressed()) {
            return true;
        }
//        if (can_minimize) {
            dismiss(true, null);
//        } else {
//            onCheckDismissByUser();
//        }
        return true;
    }

    public void dismiss() {
        dismiss(false, null);
    }

    public void dismiss(boolean intoTabs) {
        dismiss(intoTabs, null);
    }

    public boolean onCheckDismissByUser() {
        if (needCloseConfirmation) {
            String botName = null;
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
            if (user != null) {
                botName = ContactsController.formatName(user.first_name, user.last_name);
            }

            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle(botName)
                    .setMessage(LocaleController.getString(R.string.BotWebViewChangesMayNotBeSaved))
                    .setPositiveButton(LocaleController.getString(R.string.BotWebViewCloseAnyway), (dialog2, which) -> dismiss())
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .create();
            dialog.show();
            TextView textView = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            textView.setTextColor(getColor(Theme.key_text_RedBold));
            return false;
        } else {
            dismiss();
            return true;
        }
    }

    private Runnable onDismissListener;
    @Override
    public void setOnDismissListener(Runnable listener) {
        onDismissListener = listener;
    }


    public void dismiss(boolean intoTabs, Runnable callback) {
        if (dismissed) {
            return;
        }
        dismissed = true;
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);

        updateShownAnimated(false);
        if (intoTabs && (LaunchActivity.instance == null || LaunchActivity.instance.getBottomSheetTabsOverlay() == null)) {
            intoTabs = false;
        }
        if (intoTabs) {
            if (springAnimation != null) {
                springAnimation.getSpring().setFinalPosition(0);
                springAnimation.start();
            }
            LaunchActivity.instance.getBottomSheetTabsOverlay().dismissSheet(this);
        } else {
            webViewContainer.destroyWebView();
            swipeContainer.setFullSize(false);
            swipeContainer.stickTo(swipeContainer.getHeight() + windowView.measureKeyboardHeight() + (getFullSize() ? dp(200) : 0), () -> {
                release();
                if (callback != null) {
                    callback.run();
                }
                if (onDismissListener != null) {
                    onDismissListener.run();
                    onDismissListener = null;
                }
            });
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webViewResultSent) {
            long queryId = (long) args[0];

            if (this.queryId == queryId) {
                dismiss();
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            windowView.invalidate();
            webViewContainer.updateFlickerBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
            updateActionBarColors();
            updateLightStatusBar();
        }
    }

    public static int navigationBarColor(int actionBarColor) {
//        final boolean isDark = AndroidUtilities.computePerceivedBrightness(actionBarColor) < 0.721f;
//        final int themeNavBarColor = (Theme.isCurrentThemeDark() == isDark ? Theme.getColor(Theme.key_windowBackgroundGray) : (isDark ? 0xFF151E27 : 0xFFF0F0F0));
//        return Theme.adaptHue(themeNavBarColor, actionBarColor);
//        return OKLCH.adapt(themeNavBarColor, actionBarColor);
        return Theme.adaptHSV(actionBarColor, +.35f, -.1f);
    }

    public void checkNavBarColor() {
        if (attachedToParent() && LaunchActivity.instance != null) {
            LaunchActivity.instance.checkSystemBarColors(true, true, true, false);
            //LaunchActivity.instance.setNavigationBarColor(fragment.getNavigationBarColor(), false);
        }
    }

    @Override
    public boolean isFullyVisible() {
        return false;
    }

    public class WindowView extends SizeNotifierFrameLayout implements BaseFragment.AttachedSheetWindow {
        public WindowView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        private final Paint navbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawingFromOverlay) return;
            super.dispatchDraw(canvas);
            navbarPaint.setColor(navBarColor);
            AndroidUtilities.rectTmp.set(0, getHeight() - getPaddingBottom(), getWidth(), getHeight() + AndroidUtilities.navigationBarHeight);
            canvas.drawRect(AndroidUtilities.rectTmp, navbarPaint);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (drawingFromOverlay) return false;
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawingFromOverlay) return;

            super.onDraw(canvas);

            if (!overrideBackgroundColor) {
                backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
            }
            AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
            canvas.drawRect(AndroidUtilities.rectTmp, dimPaint);

            actionBarPaint.setColor(actionBarColor);
            float radius = dp(16) * (AndroidUtilities.isTablet() ? 1f : 1f - actionBarTransitionProgress);
            AndroidUtilities.rectTmp.set(swipeContainer.getLeft(), lerp(swipeContainer.getTranslationY(), 0, actionBarTransitionProgress), swipeContainer.getRight(), swipeContainer.getTranslationY() + dp(24) + radius);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, actionBarPaint);

            AndroidUtilities.rectTmp.set(swipeContainer.getLeft(), swipeContainer.getTranslationY() + dp(24), swipeContainer.getRight(), getHeight());
            canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);
        }

        @Override
        public void draw(Canvas canvas) {
            if (drawingFromOverlay) return;

            super.draw(canvas);

            float transitionProgress = AndroidUtilities.isTablet() ? 0 : actionBarTransitionProgress;
            linePaint.setColor(lineColor);
            linePaint.setAlpha((int) (linePaint.getAlpha() * (1f - Math.min(0.5f, transitionProgress) / 0.5f)));

            canvas.save();
            float scale = 1f - transitionProgress;
            float y = getContainerTop();
            canvas.scale(scale, scale, getWidth() / 2f, y);
            canvas.drawLine(getWidth() / 2f - dp(16), y, getWidth() / 2f + dp(16), y, linePaint);
            canvas.restore();

            actionBarShadow.setAlpha((int) (actionBar.getAlpha() * 0xFF));
            y = actionBar.getY() + actionBar.getTranslationY() + actionBar.getHeight();
            actionBarShadow.setBounds(0, (int)y, getWidth(), (int)(y + actionBarShadow.getIntrinsicHeight()));
            actionBarShadow.draw(canvas);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (drawingFromOverlay) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN && (event.getY() <= lerp(swipeContainer.getTranslationY() + dp(24), 0, actionBarTransitionProgress) ||
                    event.getX() > swipeContainer.getRight() || event.getX() < swipeContainer.getLeft())) {
//                if (can_minimize) {
                    dismiss(true, null);
//                } else {
//                    onCheckDismissByUser();
//                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getTopOffset(int tag) {
                    return AndroidUtilities.statusBarHeight;
                }
            });
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Bulletin.removeDelegate(this);
        }

        private boolean drawingFromOverlay;
        public void setDrawingFromOverlay(boolean drawingFromOverlay) {
            if (this.drawingFromOverlay != drawingFromOverlay) {
                this.drawingFromOverlay = drawingFromOverlay;
                invalidate();
            }
        }

        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        public RectF getRect() {
            rect.set(swipeContainer.getLeft(), lerp(swipeContainer.getTranslationY() + dp(24), 0, actionBarTransitionProgress), swipeContainer.getRight(), getHeight());
            return rect;
        }


        public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening) {
            rect.set(swipeContainer.getLeft(),  lerp(swipeContainer.getTranslationY() + dp(24), 0, actionBarTransitionProgress), swipeContainer.getRight(), getHeight());
            AndroidUtilities.lerpCentered(rect, finalRect, progress, clipRect);

            canvas.save();

            clipPath.rewind();
            float radius = dp(16) * (AndroidUtilities.isTablet() ? 1f : 1f - actionBarTransitionProgress);
            final float r = lerp(radius, dp(10), progress);
            rect.set(clipRect);
            if (opening) {
                rect.top -= dp(16) * (1f - actionBarTransitionProgress);
            }
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);

            if (!overrideBackgroundColor) {
                backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
            }
            int wasAlpha = backgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (wasAlpha * alpha));
            canvas.drawPaint(backgroundPaint);
            backgroundPaint.setAlpha(wasAlpha);

            if (swipeContainer != null) {
                canvas.save();
                canvas.translate(swipeContainer.getX(), Math.max(swipeContainer.getY(), clipRect.top) + (opening ? lerp(dp(16), actionBar.getHeight(), actionBarTransitionProgress) * (1f - alpha) : progress * dp(51)));
                if (opening) {
                    canvas.scale(1f, lerp(1f, 1.25f, 1f - alpha));
                    swipeContainer.setAlpha(alpha);
                }
                swipeContainer.draw(canvas);
                if (opening) {
                    swipeContainer.setAlpha(1f);
                }
                canvas.restore();
            }

            if (opening) {
                if (actionBar != null && actionBarTransitionProgress > 0) {
                    canvas.saveLayerAlpha(actionBar.getX(), actionBar.getY() + clipRect.top, actionBar.getX() + actionBar.getWidth(), actionBar.getY() + clipRect.top + actionBar.getHeight() + actionBarShadow.getIntrinsicHeight(), (int) (0xFF * alpha * actionBarTransitionProgress), Canvas.ALL_SAVE_FLAG);
                    canvas.translate(actionBar.getX(), actionBar.getY() + clipRect.top);
                    actionBar.draw(canvas);
                    actionBarShadow.setAlpha(0xff);//(int) (actionBar.getAlpha() * progress * 0xFF));
                    actionBarShadow.setBounds(0, actionBar.getHeight(), actionBar.getWidth(), (int) (actionBar.getHeight() + actionBarShadow.getIntrinsicHeight()));
                    actionBarShadow.draw(canvas);
                    canvas.restore();
                }
            }

            canvas.restore();

            return r;
        }

    }

    public void setBackgroundColor(int color, boolean animated) {
        int from = backgroundPaint.getColor();
        if (animated) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(animation -> {
                backgroundPaint.setColor(ColorUtils.blendARGB(from, color, (Float) animation.getAnimatedValue()));
                updateActionBarColors();
                windowView.invalidate();
            });
            animator.start();
        } else {
            backgroundPaint.setColor(color);
            updateActionBarColors();
            windowView.invalidate();
        }
    }

    public void setActionBarColor(int color, boolean isOverrideColor, boolean animated) {
        int from = actionBarColor;
        int navBarFrom = navBarColor;
        int to = color;
        int navBarTo = navigationBarColor(color);

        BotWebViewMenuContainer.ActionBarColorsAnimating actionBarColorsAnimating = new BotWebViewMenuContainer.ActionBarColorsAnimating();
        actionBarColorsAnimating.setFrom(overrideBackgroundColor ? actionBarColor : 0, resourcesProvider);
        overrideBackgroundColor = isOverrideColor;
        actionBarIsLight = ColorUtils.calculateLuminance(color) < 0.721f;
        actionBarColorsAnimating.setTo(overrideBackgroundColor ? to : 0, resourcesProvider);

        if (animated) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                actionBarColor = ColorUtils.blendARGB(from, to, progress);
                navBarColor = ColorUtils.blendARGB(navBarFrom, navBarTo, progress);
                checkNavBarColor();
                windowView.invalidate();
                actionBar.setBackgroundColor(actionBarColor);

                actionBarColorsAnimating.updateActionBar(actionBar, progress);
                lineColor = actionBarColorsAnimating.getColor(Theme.key_sheet_scrollUp);

                windowView.invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    float progress = 1f;
                    actionBarColor = ColorUtils.blendARGB(from, to, progress);
                    navBarColor = ColorUtils.blendARGB(navBarFrom, navBarTo, progress);
                    checkNavBarColor();
                    windowView.invalidate();
                    actionBar.setBackgroundColor(actionBarColor);

                    actionBarColorsAnimating.updateActionBar(actionBar, progress);
                    lineColor = actionBarColorsAnimating.getColor(Theme.key_sheet_scrollUp);

                    windowView.invalidate();
                }
            });
            animator.start();
        } else {
            float progress = 1f;
            actionBarColor = to;
            navBarColor = navBarTo;
            checkNavBarColor();
            windowView.invalidate();
            actionBar.setBackgroundColor(actionBarColor);

            actionBarColorsAnimating.updateActionBar(actionBar, progress);
            lineColor = actionBarColorsAnimating.getColor(Theme.key_sheet_scrollUp);

            windowView.invalidate();
        }
        updateLightStatusBar();
    }

    private MainButtonSettings mainButtonSettings;

    public void setMainButton(MainButtonSettings s) {
        mainButtonSettings = s;

        mainButton.setClickable(s.isActive);
        mainButton.setText(s.text);
        mainButton.setTextColor(s.textColor);
        mainButton.setBackground(BotWebViewContainer.getMainButtonRippleDrawable(s.color));
        if (s.isVisible != mainButtonWasVisible) {
            mainButtonWasVisible = s.isVisible;
            mainButton.animate().cancel();
            if (s.isVisible) {
                mainButton.setAlpha(0f);
                mainButton.setVisibility(View.VISIBLE);
            }
            mainButton.animate().alpha(s.isVisible ? 1f : 0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!s.isVisible) {
                        mainButton.setVisibility(View.GONE);
                    }
                    swipeContainer.requestLayout();
                }
            }).start();
        }
        radialProgressView.setProgressColor(s.textColor);
        if (s.isProgressVisible != mainButtonProgressWasVisible) {
            mainButtonProgressWasVisible = s.isProgressVisible;
            radialProgressView.animate().cancel();
            if (s.isProgressVisible) {
                radialProgressView.setAlpha(0f);
                radialProgressView.setVisibility(View.VISIBLE);
            }
            radialProgressView.animate().alpha(s.isProgressVisible ? 1f : 0f)
                    .scaleX(s.isProgressVisible ? 1f : 0.1f)
                    .scaleY(s.isProgressVisible ? 1f : 0.1f)
                    .setDuration(250)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!s.isProgressVisible) {
                                radialProgressView.setVisibility(View.GONE);
                            }
                        }
                    }).start();
        }
    }

}

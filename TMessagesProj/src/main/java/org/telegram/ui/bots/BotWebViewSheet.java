package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.distanceInfluenceForSnapDuration;
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
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.gms.vision.Frame;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BotFullscreenButtons;
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
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabDialog;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnchorSpan;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OverlayActionBarLayoutDialog;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.web.BotWebViewContainer;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BotWebViewSheet extends Dialog implements NotificationCenter.NotificationCenterDelegate, BottomSheetTabsOverlay.Sheet {
    public final static int TYPE_WEB_VIEW_BUTTON = 0, TYPE_SIMPLE_WEB_VIEW_BUTTON = 1, TYPE_BOT_MENU_BUTTON = 2, TYPE_WEB_VIEW_BOT_APP = 3, TYPE_WEB_VIEW_BOT_MAIN = 4;

    public final static int FLAG_FROM_INLINE_SWITCH = 1;
    public final static int FLAG_FROM_SIDE_MENU = 2;
    private int lineColor;

    public static HashSet<BotWebViewSheet> activeSheets = new HashSet<>();

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
            str = LocaleController.formatString(R.string.BotAttachMenuShortcatAddedAttachAndSide, user.first_name);
        } else if (currentBot.show_in_side_menu) {
            str = LocaleController.formatString(R.string.BotAttachMenuShortcatAddedSide, user.first_name);
        } else {
            str = LocaleController.formatString(R.string.BotAttachMenuShortcatAddedAttach, user.first_name);
        }
        AndroidUtilities.runOnUIThread(() -> {
            showBulletin(b ->
                b
                    .createSimpleBulletin(R.raw.contact_check, AndroidUtilities.replaceTags(str))
                    .setDuration(DURATION_PROLONG)
            );
        }, 200);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TYPE_WEB_VIEW_BUTTON,
            TYPE_SIMPLE_WEB_VIEW_BUTTON,
            TYPE_BOT_MENU_BUTTON,
            TYPE_WEB_VIEW_BOT_APP,
            TYPE_WEB_VIEW_BOT_MAIN
    })
    public @interface WebViewType {}

    private final static int POLL_PERIOD = 60000;

    private final static SimpleFloatPropertyCompat<BotWebViewSheet> ACTION_BAR_TRANSITION_PROGRESS_VALUE = new SimpleFloatPropertyCompat<BotWebViewSheet>("actionBarTransitionProgress", obj -> obj.actionBarTransitionProgress, (obj, value) -> {
        obj.actionBarTransitionProgress = value;
        obj.windowView.invalidate();

        obj.actionBar.setAlpha(value);

        obj.updateLightStatusBar();
        obj.updateDownloadBulletinArrow();
    }).setMultiplier(100f);
    private float actionBarTransitionProgress = 0f;
    private SpringAnimation springAnimation;

    private Boolean wasLightStatusBar;

    private WindowView windowView;
    private final Rect navInsets = new Rect();
    private final Rect insets = new Rect();
    private int keyboardInset = 0;
    
    private BottomSheetTabs bottomTabs;
    private BottomSheetTabs.ClipTools bottomTabsClip;

    private long lastSwipeTime;

    private ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
    private FrameLayout.LayoutParams swipeContainerLayoutParams;
    private BotWebViewContainer webViewContainer;
    private ChatAttachAlertBotWebViewLayout.WebProgressView progressView;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean ignoreLayout;

    private int currentAccount;
    private long botId;
    private long peerId;
    private long queryId;
    private int replyToMsgId;
    private boolean silent;
    private String buttonText;


    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint dimPaint = new Paint();
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int actionBarColor;
    private int navBarColor;
    private boolean actionBarIsLight;
    private Paint actionBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean overrideActionBarColor;
    private boolean overrideBackgroundColor;

    private ActionBar actionBar;
    private FrameLayout.LayoutParams actionBarLayoutParams;
    private Drawable actionBarShadow;
    private ActionBarMenuItem optionsItem;
    private BotFullscreenButtons.OptionsIcon optionsIcon;
    private boolean hasSettings;
    private TLRPC.BotApp currentWebApp;

    private boolean dismissed;
    private boolean fullscreen;
    private float fullscreenProgress;
    private float fullscreenTransitionProgress;
    private boolean fullscreenInProgress;
    private int swipeContainerFromWidth, swipeContainerFromHeight;

    private Activity parentActivity;
    private BotButtons botButtons;
    private FrameLayout.LayoutParams botButtonsLayoutParams;
    private BotFullscreenButtons fullscreenButtons;

    private Bulletin downloadBulletin;
    private BotDownloads.DownloadBulletin downloadBulletinLayout;
    private FrameLayout bulletinContainer;
    private FrameLayout.LayoutParams bulletinContainerLayoutParams;

    private boolean needCloseConfirmation;

    private PasscodeView passcodeView;

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

    private int actionBarColorKey = -1;
    private WebViewRequestProps requestProps;
    private boolean backButtonShown;
    private boolean forceExpnaded;

    private boolean defaultFullsize = false;
    private Boolean fullsize = null;
    private boolean needsContext;

    private BotSensors sensors;
    private boolean orientationLocked;

    private BottomSheetTabs.WebTabData lastTab;

    public BottomSheetTabs.WebTabData saveState() {
        BottomSheetTabs.WebTabData tab = new BottomSheetTabs.WebTabData();
        tab.actionBarColor = actionBarColor;
        tab.actionBarColorKey = actionBarColorKey;
        tab.overrideActionBarColor = overrideActionBarColor;
        tab.overrideBackgroundColor = overrideBackgroundColor;
        tab.backgroundColor = backgroundPaint.getColor();
        tab.props = requestProps;
        tab.ready = webViewContainer != null && webViewContainer.isPageLoaded();
        tab.themeIsDark = Theme.isCurrentThemeDark();
        tab.lastUrl = webViewContainer != null ? webViewContainer.getUrlLoaded() : null;
        tab.expanded = swipeContainer != null && swipeContainer.getSwipeOffsetY() < 0 || forceExpnaded || isFullSize() || fullscreen;
        tab.fullscreen = fullscreen;
        tab.fullsize = (fullsize == null ? defaultFullsize : fullsize);
        tab.expandedOffset = swipeContainer != null ? swipeContainer.getOffsetY() : Float.MAX_VALUE;
        tab.needsContext = needsContext;
        tab.backButton = backButtonShown;
        tab.confirmDismiss = needCloseConfirmation;
        tab.settings = hasSettings;
        tab.allowSwipes = swipeContainer == null || swipeContainer.isAllowedSwipes();
        tab.buttons = botButtons.state;
        tab.navigationBarColor = navBarColor;
        if (sensors != null) {
            sensors.pause();
        }
        tab.sensors = sensors;
        BotWebViewContainer.MyWebView webView = webViewContainer == null ? null : webViewContainer.getWebView();
        if (webView != null) {
            webViewContainer.preserveWebView();
            tab.webView = webView;
            tab.proxy = webViewContainer == null ? null : webViewContainer.getBotProxy();
            tab.viewWidth = webView.getWidth();
            tab.viewHeight = webView.getHeight();
            webView.onPause();
//            webView.pauseTimers();
        }
        if (tab.error = errorShown) {
            tab.errorDescription = errorCode;
        }
        tab.orientationLocked = orientationLocked;
        return lastTab = tab;
    }

    public Activity getActivity() {
        Activity a = getOwnerActivity();
        if (a == null) a = LaunchActivity.instance;
        if (a == null) a = AndroidUtilities.findActivity(getContext());
        return a;
    }

    public boolean fromTab;
    public boolean showExpanded;
    public float showOffsetY;

    public boolean restoreState(BaseFragment fragment, BottomSheetTabs.WebTabData tab) {
        if (tab == null || tab.props == null) return false;
        fromTab = true;
        if (overrideBackgroundColor = tab.overrideBackgroundColor) {
            setBackgroundColor(tab.backgroundColor, true, false);
        }
        setActionBarColor(!tab.overrideActionBarColor ? Theme.getColor(tab.actionBarColorKey < 0 ? Theme.key_windowBackgroundWhite : tab.actionBarColorKey, resourcesProvider) : tab.actionBarColor, tab.overrideActionBarColor, false);
        setNavigationBarColor(tab.navigationBarColor, false);
        showExpanded = tab.expanded;
        showOffsetY = tab.expandedOffset;
        webViewContainer.setIsBackButtonVisible(backButtonShown = tab.backButton);
        swipeContainer.setAllowSwipes(tab.allowSwipes);
        AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), backButtonShown ? R.drawable.ic_ab_back : R.drawable.ic_close_white);
        if (fullscreenButtons != null) {
            fullscreenButtons.setBack(backButtonShown, false);
        }
        needCloseConfirmation = tab.confirmDismiss;
        fullsize = tab.fullsize;
        needsContext = tab.needsContext;
        sensors = tab.sensors;
        if (sensors != null) {
            sensors.resume();
        }
        if (tab.buttons != null) {
//            setMainButton(tab.main);
            botButtons.setState(tab.buttons, false);
        }
        setFullscreen( tab.fullscreen, false);
        currentAccount = tab.props != null ? tab.props.currentAccount : UserConfig.selectedAccount;
        if (tab.webView != null) {
//            tab.webView.resumeTimers();
            tab.webView.onResume();
            webViewContainer.replaceWebView(currentAccount, tab.webView, tab.proxy);
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
                webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, null);
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
        hasSettings = tab.settings;

        if (tab.error) {
            errorShown = true;
            createErrorContainer();
            errorContainer.set(UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId)), errorCode = tab.errorDescription);
            errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
            errorContainer.setBackgroundColor(backgroundPaint.getColor());
            errorContainer.setVisibility(View.VISIBLE);
            errorContainer.setAlpha(1f);
        }
        lockOrientation(tab.orientationLocked);
        return true;
    }

    public BotWebViewSheet(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.resourcesProvider = resourcesProvider;
        lineColor = Theme.getColor(Theme.key_sheet_scrollUp);

        swipeContainer = new ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer(context) {
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

                if (getOffsetY() != padding && !dismissed && resetOffsetY) {
                    ignoreLayout = true;
                    setOffsetY(padding);
                    ignoreLayout = false;
                    resetOffsetY = false;
                }

                if (!fullscreen && AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (!fullscreen) {
                    height -= AndroidUtilities.statusBarHeight;
                    height -= ActionBar.getCurrentActionBarHeight();
                }
                if (botButtons != null && botButtons.getTotalHeight() > 0) {
                    height -= botButtons.getTotalHeight();
//                    if (fullscreen) {
//                        height -= insets.bottom;
//                    }
                }
                height += dp(24);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (fullscreenButtons != null) {
                    fullscreenButtons.setTranslationY(dp(24) + translationY);
                }
                if (bulletinContainer != null) {
                    bulletinContainer.setTranslationY(lerp(ActionBar.getCurrentActionBarHeight() - dp(24), insets.top + dp(24 + 8 + 30 + 8), fullscreenProgress) + swipeContainer.getTranslationY());
                }
            }
        };
        swipeContainer.setAllowFullSizeSwipe(true);
        swipeContainer.setShouldWaitWebViewScroll(true);
        webViewContainer = new BotWebViewContainer(context, resourcesProvider, getColor(Theme.key_windowBackgroundWhite), true) {
            @Override
            public void onWebViewCreated(MyWebView webView) {
                super.onWebViewCreated(webView);
                swipeContainer.setWebView(webView);
                if (sensors != null) {
                    sensors.attachWebView(webView);
                }
                fullscreenButtons.setWebView(webView);
            }

            @Override
            public void onWebViewDestroyed(MyWebView webView) {
                if (sensors != null) {
                    sensors.detachWebView(webView);
                }
                fullscreenButtons.setWebView(null);
            }

            @Override
            protected void onErrorShown(boolean shown, int errorCode, String description) {
                if (shown) {
                    createErrorContainer();
                    errorContainer.set(UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId)), description);
                    errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
                    errorContainer.setBackgroundColor(backgroundPaint.getColor());
                    BotWebViewSheet.this.errorCode = description;
                }
                AndroidUtilities.updateViewVisibilityAnimated(errorContainer, errorShown = shown, 1f, false);
                invalidate();
            }
        };
        webViewContainer.setDelegate(new BotWebViewContainer.Delegate() {
            private boolean sentWebViewData;

            @Override
            public void onCloseRequested(Runnable callback) {
                dismiss(callback);
            }

            @Override
            public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
                BotWebViewSheet.this.needCloseConfirmation = needConfirmation;
            }

            @Override
            public void onWebAppSwipingBehavior(boolean allowSwiping) {
                if (swipeContainer != null) {
                    swipeContainer.setAllowSwipes(allowSwiping);
                }
            }

            @Override
            public void onCloseToTabs() {
                dismiss(true);
            }

            @Override
            public void onSharedTo(ArrayList<Long> dialogIds) {
                String message;
                if (dialogIds.size() == 1) {
                    message = LocaleController.formatString(R.string.BotSharedToOne, MessagesController.getInstance(currentAccount).getPeerName(dialogIds.get(0)));
                } else {
                    message = LocaleController.formatPluralString("BotSharedToMany", dialogIds.size());
                }
                showBulletin(b -> b.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(message)));
            }

            @Override
            public void onOrientationLockChanged(boolean locked) {
                lockOrientation(locked);
            }

            @Override
            public void onOpenBackFromTabs() {
                if (lastTab != null) {
                    final BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();
                    if (tabs != null) {
                        tabs.openTab(lastTab);
                    }
                    lastTab = null;
                }
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
                    AndroidUtilities.runOnUIThread(BotWebViewSheet.this::dismiss);
                });
            }

            @Override
            public void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {
                actionBarColorKey = colorKey;
                setActionBarColor(color, isOverrideColor, true);
            }

            @Override
            public void onWebAppSetNavigationBarColor(int color) {
                setNavigationBarColor(color, true);
            }

            @Override
            public void onWebAppSetBackgroundColor(int color) {
                setBackgroundColor(color, true, true);
            }

            @Override
            public void onLocationGranted(boolean granted) {
                final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
                if (granted) {
                    BulletinFactory.UndoObject undo = new BulletinFactory.UndoObject();
                    undo.undoText = LocaleController.getString(R.string.Undo);
                    undo.onUndo = () -> {
                        BotLocation.get(getContext(), currentAccount, botId).setGranted(false, null);
                    };
                    showBulletin(b ->
                            b
                                    .createUsersBulletin(Arrays.asList(bot), AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotLocationPermissionRequestGranted, UserObject.getUserName(bot))), null, undo)
                                    .setDuration(DURATION_PROLONG)
                    );
                } else {
                    SpannableStringBuilder text = new SpannableStringBuilder();
                    text.append(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotLocationPermissionRequestDeniedApp, UserObject.getUserName(bot))));
                    text.append(" ");
                    text.append(AndroidUtilities.replaceArrows(AndroidUtilities.makeClickable(LocaleController.getString(R.string.BotLocationPermissionRequestDeniedAppSettings), () -> {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null || lastFragment.getParentLayout() == null) return;
                        final INavigationLayout parentLayout = lastFragment.getParentLayout();
                        lastFragment.presentFragment(ProfileActivity.of(botId));
                        AndroidUtilities.scrollToFragmentRow(parentLayout, "botPermissionLocation");
                        dismiss(true);
                    }), true));
                    showBulletin(b ->
                            b.createSimpleBulletinDetail(R.raw.error, text)
                                    .setDuration(DURATION_PROLONG)
                    );
                }
            }

            @Override
            public void onEmojiStatusGranted(boolean granted) {
                final TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(botId);
                if (granted) {
                    BulletinFactory.UndoObject undo = new BulletinFactory.UndoObject();
                    undo.onUndo = () -> {
                        TL_bots.toggleUserEmojiStatusPermission req = new TL_bots.toggleUserEmojiStatusPermission();
                        req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                        req.enabled = false;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                            if (res instanceof TLRPC.TL_boolTrue) {
                                webViewContainer.notifyEmojiStatusAccess("cancelled");
                            } else {
                                showBulletin(b -> b.makeForError(err));
                            }
                        }));
                    };
                    showBulletin(b ->
                            b
                                    .createUsersBulletin(Arrays.asList(bot), AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BotEmojiStatusPermissionRequestGranted, UserObject.getUserName(bot))), null, undo)
                                    .setDuration(DURATION_PROLONG)
                    );
                }
            }

            @Override
            public void onEmojiStatusSet(TLRPC.Document document) {
                showBulletin(b -> b.createEmojiBulletin(document, LocaleController.getString(R.string.BotEmojiStatusUpdated)));
            }

            @Override
            public void onSetBackButtonVisible(boolean visible) {
                AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), (backButtonShown = visible) ? R.drawable.ic_ab_back : R.drawable.ic_close_white);
                if (fullscreenButtons != null) {
                    fullscreenButtons.setBack(visible, true);
                }
            }

            @Override
            public void onSetSettingsButtonVisible(boolean visible) {
                hasSettings = visible;
            }

            @Override
            public void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response) {
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
                    OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(context, resourcesProvider);
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
                    OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(context, resourcesProvider);
                    dialogsActivity.setDelegate((fragment, dids, message1, param, notify, scheduleDate, topicsFragment) -> {
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
                                NotificationCenter.getInstance(currentAccount).removeObserver(BotWebViewSheet.this, NotificationCenter.webViewResultSent);
                                NotificationCenter.getGlobalInstance().removeObserver(BotWebViewSheet.this, NotificationCenter.didSetNewTheme);
                                if (!superDismissed) {
                                    BotWebViewSheet.super.dismiss();
                                    superDismissed = true;
                                }

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
            public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect) {
                botButtons.setMainState(BotButtons.ButtonState.of(isVisible, isActive, isProgressVisible, hasShineEffect, text, color, textColor), true);
                if (fullscreen) {
                    updateFullscreenLayout();
                    updateWindowFlags();
                }
            }

            @Override
            public void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position) {
                botButtons.setSecondaryState(BotButtons.ButtonState.of(isVisible, isActive, isProgressVisible, hasShineEffect, text, color, textColor, position), true);
                if (fullscreen) {
                    updateFullscreenLayout();
                    updateWindowFlags();
                }
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

            @Override
            public String onFullscreenRequested(boolean fullscreen) {
                if (BotWebViewSheet.this.fullscreen == fullscreen) {
                    if (BotWebViewSheet.this.fullscreen)
                        return "ALREADY_FULLSCREEN";
                    return null;
                }
                setFullscreen(fullscreen, true);
                return null;
            }

            @Override
            public BotSensors getBotSensors() {
                if (sensors == null) {
                    sensors = new BotSensors(context, botId);
                    sensors.attachWebView(webViewContainer.getWebView());
                }
                return sensors;
            }
        });

        linePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        dimPaint.setColor(0x40000000);
        actionBarColor = getColor(Theme.key_windowBackgroundWhite);
        navBarColor = getColor(Theme.key_windowBackgroundGray);
        AndroidUtilities.setNavigationBarColor(getWindow(), navBarColor, false);
        windowView = new WindowView(context);
        windowView.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight > AndroidUtilities.dp(20)) {
                swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
            }
        });
        windowView.addView(swipeContainer, swipeContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        botButtons = new BotButtons(getContext(), resourcesProvider) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (!fullscreen && AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        botButtons.setOnButtonClickListener(main -> {
            if (webViewContainer != null) {
                if (main) {
                    webViewContainer.onMainButtonPressed();
                } else {
                    webViewContainer.onSecondaryButtonPressed();
                }
            }
        });
        botButtons.setOnResizeListener(() -> {
            swipeContainer.requestLayout();
        });
        windowView.addView(botButtons, botButtonsLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        fullscreenButtons = new BotFullscreenButtons(getContext());
        fullscreenButtons.setAlpha(0f);
        fullscreenButtons.setVisibility(View.GONE);
        if (!MessagesController.getInstance(currentAccount).disableBotFullscreenBlur) {
            fullscreenButtons.setParentRenderNode(swipeContainer.getRenderNode());
        }
        windowView.addView(fullscreenButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fullscreenButtons.setOnCloseClickListener(() -> {
            if (!webViewContainer.onBackPressed()) {
                onCheckDismissByUser();
            }
        });
        fullscreenButtons.setOnCollapseClickListener(() -> {
            forceExpnaded = true;
            dismiss(true, null);
        });
        fullscreenButtons.setOnMenuClickListener(this::openOptions);

        bulletinContainer = new FrameLayout(context);
        windowView.addView(bulletinContainer, bulletinContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        actionBarShadow = ContextCompat.getDrawable(getContext(), R.drawable.header_shadow).mutate();

        actionBar = new ActionBar(context, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackButtonImage(R.drawable.ic_close_white);
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
        windowView.addView(actionBar, actionBarLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        windowView.addView(progressView = new ChatAttachAlertBotWebViewLayout.WebProgressView(context, resourcesProvider) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
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
            float offsetY = fullscreen ? insets.bottom : Math.max(0, swipeContainer.getSwipeOffsetY());
            lastSwipeTime = System.currentTimeMillis();
        });
        swipeContainer.setScrollEndListener(()-> webViewContainer.invalidateViewPortHeight(true));
        swipeContainer.setDelegate(byTap -> {
            if (fullscreen && byTap) return;
            dismiss(true, null);
        });
        swipeContainer.setIsKeyboardVisible(obj -> windowView.getKeyboardHeight() >= AndroidUtilities.dp(20));

        passcodeView = new PasscodeView(context);
        windowView.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateFullscreenLayout();

        bottomTabs = LaunchActivity.instance != null ? LaunchActivity.instance.getBottomSheetTabs() : null;
        if (bottomTabs != null) {
            bottomTabs.listen(windowView::invalidate, this::relayout);
            bottomTabsClip = new BottomSheetTabs.ClipTools(bottomTabs);
        }
    }

    private void relayout() {
        updateFullscreenLayout();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).addOverlayPasscodeView(passcodeView);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).removeOverlayPasscodeView(passcodeView);
        }
    }

    public void setParentActivity(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    private void updateActionBarColors() {
        if (!overrideActionBarColor) {
            actionBar.setTitleColor(getColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsColor(getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setItemsBackgroundColor(getColor(Theme.key_actionBarWhiteSelector), false);
            actionBar.setPopupBackgroundColor(getColor(Theme.key_actionBarDefaultSubmenuBackground), false);
            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItem), false, false);
            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, false);
            actionBar.setPopupItemsSelectorColor(getColor(Theme.key_dialogButtonSelector), false);
        }
        webViewContainer.setFlickerViewColor(backgroundPaint.getColor());
    }

    private void updateLightStatusBar() {
        boolean lightStatusBar;
        if (overrideActionBarColor) {
            lightStatusBar = !actionBarIsLight;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        window.setWindowAnimations(R.style.DialogNoAnimation);

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (fullscreen) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        } else {
            params.flags &=~ WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        }
        window.setAttributes(params);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        windowView.setFitsSystemWindows(true);
        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windowView.setOnApplyWindowInsetsListener((v, insets) -> {
                final WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
                final androidx.core.graphics.Insets navInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.navigationBars());
                this.navInsets.set(navInsets.left, navInsets.top, navInsets.right, navInsets.bottom);
                final androidx.core.graphics.Insets cutoutInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                this.insets.set(
                    Math.max(cutoutInsets.left,   insets.getStableInsetLeft()),
                    Math.max(cutoutInsets.top,    insets.getStableInsetTop()),
                    Math.max(cutoutInsets.right,  insets.getStableInsetRight()),
                    Math.max(cutoutInsets.bottom, insets.getStableInsetBottom())
                );
                if (Build.VERSION.SDK_INT <= 28) {
                    this.insets.top = Math.max(this.insets.top, AndroidUtilities.getStatusBarHeight(getContext()));
                }
                final androidx.core.graphics.Insets keyboardInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.ime());
                final int keyboardHeight = keyboardInsets.bottom;
                if (keyboardHeight > this.insets.bottom && keyboardHeight > dp(20)) {
                    this.keyboardInset = keyboardHeight;
                } else {
                    this.keyboardInset = 0;
                }
                updateFullscreenLayout();
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
            });
        }
        if (fullscreen && !(botButtons != null && botButtons.getTotalHeight() > 0)) {
            windowView.setSystemUiVisibility(windowView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else {
            windowView.setSystemUiVisibility(windowView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AndroidUtilities.setLightNavigationBar(window, ColorUtils.calculateLuminance(navBarColor) >= 0.721f);
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botDownloadsUpdate);
    }

    public void updateFullscreenLayout() {
        fullscreenButtons.setInsets(insets);
        if (fullscreen) {
            final int bottom = botButtons != null && botButtons.getTotalHeight() > 0 ? insets.bottom : 0;
            webViewContainer.reportSafeInsets(new Rect(insets.left, insets.top, insets.right, keyboardInset > bottom ? 0 : (botButtons != null && botButtons.getTotalHeight() > 0 ? 0 : insets.bottom)), dp(8 + 30 + 8));
            windowView.setPadding(0, 0, 0, Math.max(keyboardInset, bottom));
        } else {
            webViewContainer.reportSafeInsets(new Rect(0, 0, 0, 0), 0);
            windowView.setPadding(insets.left, 0, insets.right, Math.max(this.keyboardInset, (bottomTabs != null ? bottomTabs.getHeight(false) : 0) + insets.bottom));
        }
        swipeContainerLayoutParams.topMargin = dp(24);
//        botButtonsLayoutParams.bottomMargin = fullscreen ? insets.bottom : 0;
        actionBarLayoutParams.leftMargin = !fullscreen ? 0 : insets.left;
        actionBarLayoutParams.rightMargin = 0;
        bulletinContainerLayoutParams.leftMargin = !fullscreen ? 0 : insets.left;
        bulletinContainerLayoutParams.rightMargin = !fullscreen ? 0 : insets.right;
        if (!fullscreenInProgress) {
            swipeContainer.setSwipeOffsetAnimationDisallowed(true);
            if (fullscreen) {
                swipeContainer.setTopActionBarOffsetY(-dp(24));
            } else {
                swipeContainer.setTopActionBarOffsetY(ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - dp(24));
            }
            swipeContainer.setSwipeOffsetAnimationDisallowed(false);
            swipeContainer.invalidateTranslation();
            swipeContainer.invalidate();
            swipeContainer.requestLayout();
        }
        if (swipeContainer != null) {
            swipeContainer.setFullSize(isFullSize());
        }
        botButtons.requestLayout();
        windowView.requestLayout();
        fullscreenButtons.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
    }

    public void updateWindowFlags() {
        try {
            Window window = getWindow();
            if (window == null) return;
            WindowManager.LayoutParams params = window.getAttributes();
            final int flags;
            if (Build.VERSION.SDK_INT <= 28) {
                flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            } else {
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            }
            if (fullscreen) {
                params.flags |= flags;
            } else {
                params.flags &= ~flags;
            }
            if (fullscreen && !(botButtons != null && botButtons.getTotalHeight() > 0) && !windowView.drawingFromOverlay) {
                windowView.setSystemUiVisibility(windowView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            } else {
                windowView.setSystemUiVisibility(windowView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
            window.setAttributes(params);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        setAttached(true);

        if (springAnimation == null) {
            springAnimation = new SpringAnimation(this, ACTION_BAR_TRANSITION_PROGRESS_VALUE)
                .setSpring(new SpringForce()
                    .setStiffness(1200f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                );
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        setAttached(false);

        if (springAnimation != null) {
            springAnimation.cancel();
            springAnimation = null;
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
            jsonObject.put("bottom_bar_bg_color", Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
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
                swipeContainer.setFullSize(isFullSize());
            }
        }
    }

    public void setWasOpenedByBot(WebViewRequestProps props) {
        if (webViewContainer != null) {
            webViewContainer.setWasOpenedByBot(props);
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

    public boolean isFullSize() {
        return fullscreen || (fullsize == null ? defaultFullsize : fullsize);
    }

    @Override
    public boolean setDialog(BottomSheetTabDialog dialog) {
        return false;
    }

    @Override
    public boolean hadDialog() {
        return false;
    }

    Drawable verifiedDrawable;

    public void requestWebView(BaseFragment fragment, WebViewRequestProps props) {
        this.requestProps = props;
        this.currentAccount = props.currentAccount;
        this.peerId = props.peerId;
        this.botId = props.botId;
        this.replyToMsgId = props.replyToMsgId;
        this.silent = props.silent;
        this.buttonText = props.buttonText;
        this.currentWebApp = props.app;

        final TLRPC.User userbot = MessagesController.getInstance(currentAccount).getUser(botId);
        CharSequence title = UserObject.getUserName(userbot);
        try {
            TextPaint tp = new TextPaint();
            tp.setTextSize(dp(20));
            title = Emoji.replaceEmoji(title, tp.getFontMetricsInt(), false);
        } catch (Exception ignore) {}
        actionBar.setTitle(title);
        final TLRPC.UserFull userInfo = MessagesController.getInstance(currentAccount).getUserFull(botId);
        if (userbot != null && userbot.verified || userInfo != null && userInfo.user != null && userInfo.user.verified) {
            verifiedDrawable = getContext().getResources().getDrawable(R.drawable.verified_profile).mutate();
            verifiedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
            actionBar.getTitleTextView().setDrawablePadding(dp(2));
            actionBar.getTitleTextView().setRightDrawable(new Drawable() {
                @Override
                public void draw(@NonNull Canvas canvas) {
                    canvas.save();
                    canvas.translate(0, dp(1));
                    verifiedDrawable.setBounds(getBounds());
                    verifiedDrawable.draw(canvas);
                    canvas.restore();
                }
                @Override
                public void setAlpha(int alpha) {
                    verifiedDrawable.setAlpha(alpha);
                }
                @Override
                public void setColorFilter(@Nullable ColorFilter colorFilter) {
                    verifiedDrawable.setColorFilter(colorFilter);
                }
                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }

                @Override
                public int getIntrinsicHeight() {
                    return dp(20);
                }

                @Override
                public int getIntrinsicWidth() {
                    return dp(20);
                }
            });
        }
        if (fullscreenButtons != null) {
            fullscreenButtons.setName(UserObject.getUserName(userbot), userbot != null && userbot.verified);
        }
        ActionBarMenu menu = actionBar.createMenu();
        menu.removeAllViews();

        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                currentBot = bot;
                break;
            }
        }
        if (!fromTab) {
            if (userInfo != null) {
                if (userInfo.bot_info != null && userInfo.bot_info.app_settings != null) {
                    applyAppBotSettings(userInfo.bot_info.app_settings, false);
                }
            } else {
                MessagesController.getInstance(currentAccount).loadFullUser(userbot, 0, true, (userFull2) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (userFull2 != null && userFull2.bot_info != null && userFull2.bot_info.app_settings != null) {
                            applyAppBotSettings(userFull2.bot_info.app_settings, true);
                        }
                    });
                });
            }
            if (props.fullscreen) {
                setFullscreen(true, false);
            }
        }

        menu.addItem(R.id.menu_collapse_bot, R.drawable.arrow_more);
        optionsItem = menu.addItem(0, optionsIcon = new BotFullscreenButtons.OptionsIcon(getContext()));
        optionsItem.setOnClickListener(v -> {
            openOptions();
        });

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!webViewContainer.onBackPressed()) {
                        onCheckDismissByUser();
                    }
                } else if (id == R.id.menu_collapse_bot) {
                    forceExpnaded = true;
                    dismiss(true, null);
                }
            }
        });

        final JSONObject themeParams = makeThemeParams(resourcesProvider);

        webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
        webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, null);
        preloadShortcutBotIcon(props.botUser, currentBot);
        if (props.response != null) {
            loadFromResponse();
        } else {
            switch (props.type) {
                case TYPE_BOT_MENU_BUTTON: {
                    TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                    req.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
                    req.platform = "android";
                    req.compact = props.compact;
                    req.fullscreen = props.fullscreen;

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
                            loadFromResponse();
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
                    req.fullscreen = props.fullscreen;
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
                            loadFromResponse();
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
                    req.fullscreen = props.fullscreen;
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
                            loadFromResponse();
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
                    req.fullscreen = props.fullscreen;

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
                            loadFromResponse();
                        }
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                    break;
                }
                case TYPE_WEB_VIEW_BOT_MAIN: {
                    TLRPC.TL_messages_requestMainWebView req = new TLRPC.TL_messages_requestMainWebView();

                    req.bot = MessagesController.getInstance(currentAccount).getInputUser(props.botId);
                    req.platform = "android";
                    req.peer = fragment instanceof ChatActivity ? ((ChatActivity) fragment).getCurrentUser() != null ? MessagesController.getInputPeer(((ChatActivity) fragment).getCurrentUser()) : MessagesController.getInputPeer(((ChatActivity) fragment).getCurrentChat())
                            : MessagesController.getInputPeer(props.botUser);
                    req.compact = props.compact;
                    req.fullscreen = props.fullscreen;

                    if (!TextUtils.isEmpty(props.startParam)) {
                        req.start_param = props.startParam;
                        req.flags |= 2;
                    }

                    if (themeParams != null) {
                        req.theme_params = new TLRPC.TL_dataJSON();
                        req.theme_params.data = themeParams.toString();
                        req.flags |= 1;
                    }

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 != null) {

                        } else if (requestProps != null) {
                            requestProps.applyResponse(response2);
                            loadFromResponse();
                        }
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                    break;
                }
            }
        }
    }

    private HashMap<BotDownloads.FileDownload, ActionBarMenuSubItem> fileItems = new HashMap<>();
    private ItemOptions options;
    private void openOptions() {
        final TLRPC.User userbot = MessagesController.getInstance(currentAccount).getUser(botId);
        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                currentBot = bot;
                break;
            }
        }
        if (options != null) {
            options.dismiss();
        }
        final ItemOptions o = options = ItemOptions.makeOptions(windowView, resourcesProvider, fullscreen ? fullscreenButtons : optionsItem, true);
        final BotDownloads botDownloads = BotDownloads.get(getContext(), currentAccount, botId);
        fileItems.clear();
        if (botDownloads.hasFiles()) {
            final ItemOptions so = o.makeSwipeback();
            so.add(R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), o::closeSwipeback);
            so.addGap();
            for (BotDownloads.FileDownload file : botDownloads.getFiles()) {
                final ActionBarMenuSubItem fileItem = so.add(file.file_name, "", () -> {}).getLast();
                fileItems.put(file, fileItem);
            }
            updateDownloadBulletin();
            so.setMinWidth(dp(180));

            o.add(R.drawable.menu_download_round, LocaleController.getString(R.string.BotDownloads), () -> {
                o.openSwipeback(so);
            });
            o.addGap();
        }
        o
            .add(R.drawable.msg_bot, LocaleController.getString(R.string.BotWebViewOpenBot), () -> {
                if (parentActivity instanceof LaunchActivity) {
                    ((LaunchActivity) parentActivity).presentFragment(ChatActivity.of(botId));
                }
                dismiss(true);
            })
            .addIf(hasSettings, R.drawable.msg_settings, LocaleController.getString(R.string.BotWebViewSettings), () -> {
                webViewContainer.onSettingsButtonPressed();
            })
            .add(R.drawable.msg_retry, LocaleController.getString(R.string.BotWebViewReloadPage), () -> {
                if (webViewContainer.getWebView() != null) {
                    webViewContainer.getWebView().animate().cancel();
                    webViewContainer.getWebView().animate().alpha(0).start();
                }

                progressView.setLoadProgress(0);
                progressView.setAlpha(1f);
                progressView.setVisibility(View.VISIBLE);

                webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
                webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, null);
                webViewContainer.reload();
            })
            .addIf(userbot != null && userbot.bot_has_main_app, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut), () -> {
                MediaDataController.getInstance(currentAccount).installShortcut(botId, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT);
            })
            .add(R.drawable.menu_intro, LocaleController.getString(R.string.BotWebViewToS), () -> {
                Browser.openUrl(getContext(), LocaleController.getString(R.string.BotWebViewToSLink));
            })
            .addIf(currentBot != null && (currentBot.show_in_side_menu || currentBot.show_in_attach_menu), R.drawable.msg_delete, LocaleController.getString(R.string.BotWebViewDeleteBot), () -> {
                deleteBot(currentAccount, botId, () -> dismiss());
            })
            .setGravity(Gravity.RIGHT)
            .translate(-insets.right, 0)
            .forceTop(true)
            .setDrawScrim(false)
            .show();
    }

    private void showBulletin(Utilities.CallbackReturn<BulletinFactory, Bulletin> make) {
        make.run(BulletinFactory.of(bulletinContainer, resourcesProvider)).show(true);
    }

    private void updateDownloadBulletinArrow() {
        if (downloadBulletinLayout == null) return;
        if (fullscreen) {
            downloadBulletinLayout.setArrow(lerp(dp(24), dp(26), fullscreenProgress));
        } else if (actionBarTransitionProgress > .5f) {
            downloadBulletinLayout.setArrow(dp(24));
        } else {
            downloadBulletinLayout.setArrow(-1);
        }
    }

    private BotDownloads.FileDownload lastBulletinFile;
    private void updateDownloadBulletin() {
        final BotDownloads botDownloads = BotDownloads.get(getContext(), currentAccount, botId);
        final BotDownloads.FileDownload file = botDownloads.getCurrent();

        if (file == null) {
            if (downloadBulletin != null) {
                downloadBulletin.hide();
                downloadBulletin = null;
            }
        } else if (file.isDownloading() && !file.shown || file.resaved) {
            if (lastBulletinFile != file && downloadBulletin != null) {
                downloadBulletin.hide();
                downloadBulletin = null;
            }
            if (downloadBulletin == null || !downloadBulletin.isShowing()) {
                lastBulletinFile = file;
                downloadBulletin = Bulletin.make(bulletinContainer, downloadBulletinLayout = new BotDownloads.DownloadBulletin(getContext(), resourcesProvider), DURATION_PROLONG);
                downloadBulletin.show(true);
            }
            if (downloadBulletinLayout.set(file)) {
                downloadBulletin = null;
            }
            file.resaved = false;
            file.shown = true;
        } else if (downloadBulletinLayout != null) {
            lastBulletinFile = file;
            if (downloadBulletinLayout.set(file)) {
                downloadBulletin = null;
            }
        }
        updateDownloadBulletinArrow();

        for (Map.Entry<BotDownloads.FileDownload, ActionBarMenuSubItem> entry : fileItems.entrySet()) {
            final ActionBarMenuSubItem item = entry.getValue();
            final BotDownloads.FileDownload itemFile = entry.getKey();

            item.setText(itemFile.file_name);
            if (!itemFile.isDownloading()) {
                item.setSubtext(AndroidUtilities.formatFileSize(itemFile.size));
            } else {
                final Pair<Long, Long> progress = itemFile.getProgress();
                if (progress.second > 0) {
                    item.setSubtext(AndroidUtilities.formatFileSize(progress.first) + " / " + AndroidUtilities.formatFileSize(progress.second));
                } else {
                    item.setSubtext(AndroidUtilities.formatFileSize(progress.first));
                }
            }

            if (itemFile.isDownloading()) {
                item.setRightIcon(R.drawable.msg_close);
                item.subtextView.setPadding(0, 0, dp(32), 0);
            } else if (itemFile.cancelled) {
                item.setVisibility(View.GONE);
            } else {
                item.setRightIcon(0);
                item.subtextView.setPadding(0, 0, 0, 0);
            }

            item.setOnClickListener(v -> {
                if (itemFile.isDownloading()) {
                    itemFile.cancel();
                } else {
                    itemFile.open();
                }
                if (options != null) {
                    options.dismiss();
                    options = null;
                }
            });
        }

        optionsIcon.setDownloading(botDownloads.isDownloading());
        fullscreenButtons.setDownloading(botDownloads.isDownloading());

    }

    private void applyAppBotSettings(TL_bots.botAppSettings botAppSettings, boolean animated) {
        if (botAppSettings == null) return;
        final boolean dark = Theme.isCurrentThemeDark();
        final boolean hasBackgroundColor = (botAppSettings.flags & ((dark ? 4 : 2))) != 0;
        final boolean hasHeaderColor = (botAppSettings.flags & ((dark ? 16 : 8))) != 0;
        if (hasHeaderColor) {
            setActionBarColor((dark ? botAppSettings.header_dark_color : botAppSettings.header_color) | 0xFF000000, true, animated);
        }
        if (hasBackgroundColor) {
            setBackgroundColor((dark ? botAppSettings.background_dark_color : botAppSettings.background_color) | 0xFF000000, true, animated);
            setNavigationBarColor((dark ? botAppSettings.background_dark_color : botAppSettings.background_color) | 0xFF000000, animated);
        }
    }

    private void loadFromResponse() {
        if (requestProps == null) return;
        final long pollTimeout = Math.max(0, POLL_PERIOD - (System.currentTimeMillis() - requestProps.responseTime));
        String url = null;
        fullsize = null;
        if (requestProps.response instanceof TLRPC.TL_webViewResultUrl) {
            TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) requestProps.response;
            queryId = resultUrl.query_id;
            url = resultUrl.url;
            fullsize = resultUrl.fullsize;
            if (!fromTab) {
                setFullscreen(resultUrl.fullscreen, !fromTab);
            }
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
            MediaDataController.getInstance(currentAccount).increaseWebappRating(requestProps.botId);
            webViewContainer.loadUrl(currentAccount, url);
        }
        AndroidUtilities.runOnUIThread(pollRunnable, pollTimeout);
        if (swipeContainer != null) {
            swipeContainer.setFullSize(isFullSize());
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
        description = LocaleController.formatString(R.string.BotRemoveFromMenu, botName);
        TLRPC.TL_attachMenuBot finalCurrentBot = currentBot;
        new AlertDialog.Builder(LaunchActivity.getLastFragment().getContext())
                .setTitle(LocaleController.getString(R.string.BotRemoveFromMenuTitle))
                .setMessage(AndroidUtilities.replaceTags(description))
                .setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
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
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private int getColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        setOpen(true);
        windowView.setAlpha(0f);
        windowView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);

                swipeContainer.setSwipeOffsetY(swipeContainer.getHeight());
                windowView.setAlpha(1f);

                if (showOffsetY != Float.MAX_VALUE) {
                    swipeContainer.setSwipeOffsetAnimationDisallowed(true);
                    swipeContainer.setOffsetY(showOffsetY);
                    swipeContainer.setSwipeOffsetAnimationDisallowed(false);
                }

                webViewContainer.invalidateViewPortHeight(true, true);
                AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
                locker.lock();

                if (showExpanded || isFullSize()) {
                    swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY(), locker::unlock);
                } else {
                    new SpringAnimation(swipeContainer, ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer.SWIPE_OFFSET_Y, 0)
                            .setSpring(new SpringForce(0)
                                    .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                                    .setStiffness(500.0f)
                            ).addEndListener((animation, canceled, value, velocity) -> {
                                locker.unlock();
                            }).start();
                }
                swipeContainer.opened = true;

                if (fullscreen && fullscreenButtons != null) {
                    fullscreenButtons.setAlpha(0f);
                    fullscreenButtons.animate().alpha(1f).setDuration(220).start();
                }
            }
        });
        super.show();
        superDismissed = false;
        activeSheets.add(this);
    }

    @Override
    public void dismiss(boolean tabs) {
        dismiss(tabs, null);
    }

    public long getBotId() {
        return botId;
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.getVisibility() == View.VISIBLE) {
            if (getOwnerActivity() != null) {
                getOwnerActivity().finish();
            }
            return;
        }
        if (webViewContainer.onBackPressed()) {
            return;
        }
//        if (can_minimize) {
            dismiss(true, null);
//        } else {
//            onCheckDismissByUser();
//        }
    }

    @Override
    public void dismiss() {
        dismiss(null);
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

    public void dismiss(Runnable callback) {
        dismiss(false, callback);
    }

    private boolean superDismissed = false;
    public void dismiss(boolean intoTabs, Runnable callback) {
        if (dismissed) {
            return;
        }
        dismissed = true;
        setOpen(false);
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botDownloadsUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);

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
            if (botButtons != null) {
                botButtons.animate().translationY(botButtons.getTotalHeight()).alpha(0).setDuration(160).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            webViewContainer.destroyWebView();
            swipeContainer.stickTo(swipeContainer.getHeight() + (botButtons != null ? botButtons.getTotalHeight() : 0) + insets.top + insets.bottom + windowView.measureKeyboardHeight() + (isFullSize() ? dp(200) : 0), true, () -> {
                if (!superDismissed) {
                    super.dismiss();
                    superDismissed = true;
                }
                if (callback != null) {
                    callback.run();
                }
            });
        }
        activeSheets.remove(this);
    }

    public void release() {
        if (superDismissed) return;
        try {
            super.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
        setOpen(false);
    }

    private float openedProgress;
    private ValueAnimator openAnimator;
    public void setOpen(boolean opened) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        if (Math.abs(openedProgress - (opened ? 1.0f : 0.0f)) < 0.01f) return;
        openAnimator = ValueAnimator.ofFloat(openedProgress, opened ? 1.0f : 0.0f);
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openedProgress = opened ? 1.0f : 0.0f;
                checkNavBarColor();
            }
        });
        openAnimator.addUpdateListener(anm -> {
            openedProgress = (float) anm.getAnimatedValue();
            checkNavBarColor();
        });
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(220);
        openAnimator.start();
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
        } else if (id == NotificationCenter.botDownloadsUpdate) {
            updateDownloadBulletin();
        }
    }

    public static int navigationBarColor(int actionBarColor) {
        return Theme.adaptHSV(actionBarColor, +.35f, -.1f);
    }

    private ValueAnimator backgroundColorAnimator;
    public void setBackgroundColor(int color, boolean isOverride, boolean animated) {
        int from = backgroundPaint.getColor();
        overrideBackgroundColor = isOverride;
        if (backgroundColorAnimator != null) {
            backgroundColorAnimator.cancel();
        }
        if (animated) {
            backgroundColorAnimator = ValueAnimator.ofFloat(0, 1).setDuration(200);
            backgroundColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            backgroundColorAnimator.addUpdateListener(animation -> {
                backgroundPaint.setColor(ColorUtils.blendARGB(from, color, (Float) animation.getAnimatedValue()));
                updateActionBarColors();
                windowView.invalidate();
                if (errorContainer != null) {
                    errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
                    errorContainer.setBackgroundColor(backgroundPaint.getColor());
                }
            });
            backgroundColorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    backgroundPaint.setColor(color);
                    updateActionBarColors();
                    windowView.invalidate();
                    if (errorContainer != null) {
                        errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
                        errorContainer.setBackgroundColor(backgroundPaint.getColor());
                    }
                }
            });
            backgroundColorAnimator.start();
        } else {
            backgroundPaint.setColor(color);
            updateActionBarColors();
            windowView.invalidate();
            if (errorContainer != null) {
                errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
                errorContainer.setBackgroundColor(backgroundPaint.getColor());
            }
        }
    }

    private boolean resetOffsetY = true;
    private ValueAnimator fullscreenAnimator;
    public void setFullscreen(boolean fullscreen, boolean animated) {
        if (this.fullscreen == fullscreen) return;
        this.fullscreen = fullscreen;
        if (fullscreenAnimator != null) {
            fullscreenAnimator.cancel();
        }
        if (fullscreenButtons != null) {
            fullscreenButtons.setPreview(fullscreen, animated);
        }
        swipeContainerFromWidth = swipeContainer.getWidth();
        swipeContainerFromHeight = swipeContainer.getHeight();
        resetOffsetY = false;
        if (animated) {
            updateFullscreenLayout();
            updateWindowFlags();
            updateDownloadBulletinArrow();
            final float tabletOffset = AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet() ? (AndroidUtilities.displaySize.x - (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f)) / 2f : 0;
            final float fromTranslationX = fullscreen ? insets.left + tabletOffset : -insets.left - tabletOffset;
            final float fromButtonsTranslationX = fullscreen ? +tabletOffset : -tabletOffset;
            final float toTranslationX = 0;
            final float fromTranslationY = fullscreen ? swipeContainer.getTranslationY() : -dp(24);
            final float toTranslationY = fullscreen ? -dp(24) : (ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - dp(24));
            final float topoffset = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
            swipeContainer.cancelStickTo();
            swipeContainer.setSwipeOffsetAnimationDisallowed(true);
            actionBar.setVisibility(View.VISIBLE);
            if (fullscreen) {
                swipeContainer.setTopActionBarOffsetY(-dp(24));
            } else {
                swipeContainer.setTopActionBarOffsetY(topoffset - dp(24));
            }
            swipeContainer.invalidateTranslation();
            swipeContainer.invalidate();

            fullscreenTransitionProgress = 0.0f;
            fullscreenProgress = fullscreen ? fullscreenTransitionProgress : 1.0f - fullscreenTransitionProgress;
            actionBar.setAlpha(1.0f - fullscreenProgress);
            actionBar.setTranslationY(-ActionBar.getCurrentActionBarHeight() * fullscreenProgress);
            swipeContainer.setTranslationY(lerp(fromTranslationY, toTranslationY, fullscreenTransitionProgress));
            swipeContainer.setTranslationX(lerp(fromTranslationX, toTranslationX, fullscreenTransitionProgress));
            botButtons.setTranslationX(lerp(fromButtonsTranslationX, 0, fullscreenTransitionProgress));
            fullscreenButtons.setAlpha(fullscreenProgress);
            windowView.invalidate();
            webViewContainer.setViewPortHeightOffset(swipeContainer.getTranslationY() - toTranslationY);
            webViewContainer.invalidateViewPortHeight(false, false);

            fullscreenInProgress = true;
            fullscreenAnimator = ValueAnimator.ofFloat(0, 1);
            fullscreenAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                    fullscreenTransitionProgress = (float) animation.getAnimatedValue();
                    fullscreenProgress = fullscreen ? fullscreenTransitionProgress : 1.0f - fullscreenTransitionProgress;
                    actionBar.setAlpha(1.0f - fullscreenProgress);
                    actionBar.setTranslationY(-ActionBar.getCurrentActionBarHeight() * fullscreenProgress);
                    swipeContainer.setTranslationY(lerp(fromTranslationY, toTranslationY, fullscreenTransitionProgress));
                    swipeContainer.setTranslationX(lerp(fromTranslationX, toTranslationX, fullscreenTransitionProgress));
                    botButtons.setTranslationX(lerp(fromButtonsTranslationX, 0, fullscreenTransitionProgress));
                    fullscreenButtons.setAlpha(fullscreenProgress);
                    windowView.invalidate();
                    webViewContainer.setViewPortHeightOffset(swipeContainer.getTranslationY() - toTranslationY);
                    webViewContainer.invalidateViewPortHeight(false, false);
                    updateDownloadBulletinArrow();
                }
            });
            fullscreenAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fullscreenInProgress = false;
                    if (!fullscreen) {
                        updateFullscreenLayout();
                        updateWindowFlags();
                        swipeContainer.setForceOffsetY(topoffset - dp(24));
                        swipeContainer.setTopActionBarOffsetY(topoffset - dp(24));
                        swipeContainer.setSwipeOffsetY(0);
                    } else {
                        swipeContainer.setForceOffsetY(-dp(24));
                        swipeContainer.setTopActionBarOffsetY(-dp(24));
                        swipeContainer.setSwipeOffsetY(0);
                    }
                    fullscreenProgress = fullscreen ? fullscreenTransitionProgress : 1.0f - fullscreenTransitionProgress;
                    actionBar.setAlpha(1.0f - fullscreenProgress);
                    actionBar.setTranslationY(-ActionBar.getCurrentActionBarHeight() * fullscreenProgress);
                    fullscreenButtons.setAlpha(fullscreenProgress);
                    if (fullscreen) {
                        actionBar.setVisibility(View.GONE);
                    }
                    swipeContainer.setSwipeOffsetAnimationDisallowed(false);
                    swipeContainer.setTranslationX(lerp(fromTranslationX, toTranslationX, fullscreenTransitionProgress));
                    botButtons.setTranslationX(0);
                    windowView.invalidate();
                    webViewContainer.setViewPortHeightOffset(0);
                    webViewContainer.invalidateViewPortHeight(true, true);
                    updateDownloadBulletinArrow();
                }
            });
            fullscreenAnimator.setDuration(280);
            fullscreenAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            fullscreenAnimator.start();
        } else {
            fullscreenInProgress = false;
            fullscreenProgress = fullscreen ? 1.0f : 0.0f;
            fullscreenTransitionProgress = 0.0f;
            updateFullscreenLayout();
            updateWindowFlags();
            actionBar.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            actionBar.setAlpha(1.0f - fullscreenProgress);
            actionBar.setTranslationY(-ActionBar.getCurrentActionBarHeight() * fullscreenProgress);
            botButtons.setTranslationX(0);
            fullscreenButtons.setAlpha(fullscreenProgress);
            webViewContainer.setViewPortHeightOffset(0);
            webViewContainer.invalidateViewPortHeight(true, true);
            updateDownloadBulletinArrow();
        }
    }

    public void setNavigationBarColor(int color, boolean animated) {
        int from = navBarColor;
        int to = color;

        botButtons.setBackgroundColor(color, animated);
        if (animated) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                navBarColor = ColorUtils.blendARGB(from, to, progress);
                checkNavBarColor();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    float progress = 1f;
                    navBarColor = ColorUtils.blendARGB(from, to, progress);
                    checkNavBarColor();
                }
            });
            animator.start();
        } else {
            navBarColor = to;
            checkNavBarColor();
        }
        AndroidUtilities.setNavigationBarColor(getWindow(), navBarColor, false);
    }

    public void setActionBarColor(int color, boolean isOverride, boolean animated) {
        int from = actionBarColor;
//        int navBarFrom = navBarColor;
        int to = color;
        int navBarTo = navigationBarColor(color);

        BotWebViewMenuContainer.ActionBarColorsAnimating actionBarColorsAnimating = new BotWebViewMenuContainer.ActionBarColorsAnimating();
        actionBarColorsAnimating.setFrom(overrideActionBarColor ? actionBarColor : 0, resourcesProvider);
        overrideActionBarColor = isOverride;
        actionBarIsLight = ColorUtils.calculateLuminance(color) < 0.721f;
        actionBarColorsAnimating.setTo(overrideActionBarColor ? to : 0, resourcesProvider);

        if (animated) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                actionBarColor = ColorUtils.blendARGB(from, to, progress);
//                navBarColor = ColorUtils.blendARGB(navBarFrom, navBarTo, progress);
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
//                    navBarColor = ColorUtils.blendARGB(navBarFrom, navBarTo, progress);
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
//            navBarColor = navBarTo;
            checkNavBarColor();
            windowView.invalidate();
            actionBar.setBackgroundColor(actionBarColor);

            actionBarColorsAnimating.updateActionBar(actionBar, progress);
            lineColor = actionBarColorsAnimating.getColor(Theme.key_sheet_scrollUp);

            windowView.invalidate();
        }
        updateLightStatusBar();
    }

    public void checkNavBarColor() {
        if (!superDismissed && LaunchActivity.instance != null) {
            LaunchActivity.instance.checkSystemBarColors(true, true, true, false);
//            AndroidUtilities.setNavigationBarColor(getWindow(), navBarColor, false);
        }
        if (windowView != null) {
            windowView.invalidate();
        }
    }

    @Override
    public int getNavigationBarColor(int color) {
        return ColorUtils.blendARGB(color, navBarColor, openedProgress);
    }

    public WindowView getWindowView() {
        return windowView;
    }

    public class WindowView extends SizeNotifierFrameLayout implements BottomSheetTabsOverlay.SheetView {
        public WindowView(Context context) {
            super(context);
        }

        {
            setClipChildren(false);
            setClipToPadding(false);
            setWillNotDraw(false);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            boolean restore = false;
            if (child == swipeContainer && fullscreenInProgress && swipeContainerFromHeight > 0 && swipeContainerFromWidth > 0) {
                canvas.save();
                canvas.clipRect(
                    child.getX(), child.getY(),
                    child.getX() + lerp(swipeContainerFromWidth, child.getWidth(), fullscreenTransitionProgress),
                    child.getY() + lerp(swipeContainerFromHeight, child.getHeight(), fullscreenTransitionProgress)
                );
                restore = true;
            }
            boolean r = super.drawChild(canvas, child, drawingTime);
            if (restore) {
                canvas.restore();
            }
            return r;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final BottomSheetTabs tabs = LaunchActivity.instance != null ? LaunchActivity.instance.getBottomSheetTabs() : null;
            if (tabs != null && insets != null) {
                final int bottomTabsHeight = (int) (tabs.getHeight(true) * (1.0f - fullscreenProgress));
                if (ev.getY() >= getHeight() - insets.bottom - bottomTabsHeight && ev.getY() <= getHeight() - insets.bottom && !AndroidUtilities.isTablet()) {
                    return tabs.touchEvent(ev.getAction(), ev.getX(), ev.getY() - (getHeight() - insets.bottom - bottomTabsHeight));
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        private final Paint navbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawingFromOverlay) return;

            if (passcodeView.getVisibility() != View.VISIBLE && fullscreenProgress < 1 && fullscreenProgress > 0) {
                navbarPaint.setColor(Theme.multAlpha(navBarColor, openedProgress));
                if (navInsets.left > 0) {
                    canvas.drawRect(0, 0, navInsets.left, getHeight(), navbarPaint);
                }
                if (navInsets.top > 0) {
                    canvas.drawRect(0, 0, getWidth(), navInsets.top, navbarPaint);
                }
                if (navInsets.bottom > 0) {
                    canvas.drawRect(0, getHeight() - navInsets.bottom, getWidth(), getHeight(), navbarPaint);
                }
                if (navInsets.right > 0) {
                    canvas.drawRect(getWidth() - navInsets.right, 0, getWidth(), getHeight(), navbarPaint);
                }
            }

            boolean restore = false;
            if (bottomTabsClip != null && !AndroidUtilities.isTablet()) {
                canvas.save();
                canvas.translate(insets.left * (1.0f - fullscreenProgress), 0);
                bottomTabsClip.clip(canvas, true, false, lerp(getWidth() - insets.left - insets.right, getWidth(), fullscreenProgress), getHeight(), 1.0f - fullscreenProgress);
                canvas.translate(-insets.left * (1.0f - fullscreenProgress), 0);
                restore = true;
            }
            super.dispatchDraw(canvas);
            if (restore) {
                canvas.restore();
            }

            if (passcodeView.getVisibility() != View.VISIBLE) {
                navbarPaint.setColor(Theme.multAlpha(navBarColor, openedProgress));
                if (navInsets.left > 0) {
                    canvas.drawRect(0, 0, navInsets.left * (1.0f - fullscreenProgress), getHeight(), navbarPaint);
                }
                if (navInsets.top > 0) {
                    canvas.drawRect(0, 0, getWidth(), navInsets.top * (1.0f - fullscreenProgress), navbarPaint);
                }
                if (navInsets.bottom > 0) {
                    canvas.drawRect(0, getHeight() - (navInsets.bottom * (botButtons != null && botButtons.getTotalHeight() > 0 ? 1.0f : 1.0f - fullscreenProgress)), getWidth(), getHeight(), navbarPaint);
                }
                if (navInsets.right > 0) {
                    canvas.drawRect(getWidth() - (navInsets.right * (1.0f - fullscreenProgress)), 0, getWidth(), getHeight(), navbarPaint);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawingFromOverlay) return;

            super.onDraw(canvas);

            if (passcodeView.getVisibility() != View.VISIBLE) {
                canvas.save();
                if (bottomTabsClip != null) {
                    bottomTabsClip.clip(canvas, false, false, getWidth(), getHeight(), 1.0f - fullscreenProgress);
                }

                if (!overrideBackgroundColor) {
                    final int color = getColor(Theme.key_windowBackgroundWhite);
                    backgroundPaint.setColor(color);
                    webViewContainer.setFlickerViewColor(color);
                    if (errorContainer != null) {
                        errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) <= .721f, false);
                        errorContainer.setBackgroundColor(backgroundPaint.getColor());
                    }
                }
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRect(AndroidUtilities.rectTmp, dimPaint);

                final int bottomTabsHeight = bottomTabs != null ? bottomTabs.getHeight(true) : 0;

                actionBarPaint.setColor(actionBarColor);
                float radius = AndroidUtilities.dp(16) * (AndroidUtilities.isTablet() ? 1f : 1f - actionBarTransitionProgress);
                AndroidUtilities.rectTmp.set(lerp(swipeContainer.getLeft(), 0, fullscreenProgress), lerp(swipeContainer.getTranslationY(), 0, actionBarTransitionProgress), swipeContainer.getRight(), swipeContainer.getTranslationY() + AndroidUtilities.dp(24) + radius);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, actionBarPaint);

                AndroidUtilities.rectTmp.set(lerp(swipeContainer.getLeft(), 0, fullscreenProgress), swipeContainer.getTranslationY() + AndroidUtilities.dp(24), lerp(swipeContainer.getRight(), getWidth(), fullscreenProgress), getHeight() - bottomTabsHeight);
                canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);

                canvas.restore();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (drawingFromOverlay) return;

            super.draw(canvas);

            float transitionProgress = AndroidUtilities.isTablet() ? 0 : actionBarTransitionProgress;
            linePaint.setColor(lineColor);
            linePaint.setAlpha((int) (linePaint.getAlpha() * (1f - Math.min(0.5f, transitionProgress) / 0.5f) * (1.0f - fullscreenProgress)));

            canvas.save();
            float scale = 1f - transitionProgress;
            float y = AndroidUtilities.isTablet() ? lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(12), AndroidUtilities.statusBarHeight / 2f, actionBarTransitionProgress) :
                    (lerp(swipeContainer.getTranslationY(), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f, transitionProgress) + AndroidUtilities.dp(12));
            canvas.scale(scale, scale, getWidth() / 2f, y);
            canvas.drawLine(getWidth() / 2f - AndroidUtilities.dp(16), y, getWidth() / 2f + AndroidUtilities.dp(16), y, linePaint);
            canvas.restore();

            actionBarShadow.setAlpha((int) (actionBar.getAlpha() * 0xFF));
            y = actionBar.getY() + actionBar.getTranslationY() + actionBar.getHeight();
            actionBarShadow.setBounds(insets.left, (int) y, getWidth() - insets.right, (int) (y + actionBarShadow.getIntrinsicHeight()));
            actionBarShadow.draw(canvas);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && (event.getY() <= lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(24), 0, actionBarTransitionProgress) ||
                    event.getX() > swipeContainer.getRight() || event.getX() < swipeContainer.getLeft())) {
                dismiss(true, null);
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
                updateWindowFlags();
                if (LaunchActivity.instance != null && fullscreen) {
                    LaunchActivity.instance.requestCustomNavigationBar();
                    LaunchActivity.instance.setNavigationBarColor(navBarColor, false);
                }
            }
        }

        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        public RectF getRect() {
            rect.set(swipeContainer.getLeft(), swipeContainer.getTranslationY() + dp(24), swipeContainer.getRight(), getHeight());
            return rect;
        }
        public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening) {
            rect.set(swipeContainer.getLeft(), swipeContainer.getTranslationY() + dp(24), swipeContainer.getRight(), getHeight());
            AndroidUtilities.lerpCentered(rect, finalRect, progress, clipRect);

            canvas.save();

            clipPath.rewind();
            float radius = dp(16) * (AndroidUtilities.isTablet() ? 1f : 1f - actionBarTransitionProgress);
            final float r = lerp(radius, dp(10), progress);
            clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.drawPaint(backgroundPaint);

            if (swipeContainer != null) {
                canvas.save();
                canvas.translate(clipRect.left, Math.max(swipeContainer.getY(), clipRect.top) + progress * dp(51));
                swipeContainer.draw(canvas);
                canvas.restore();
            }

            canvas.restore();

            return r;
        }

    }

    private boolean errorShown;
    private String errorCode;
    private ArticleViewer.ErrorContainer errorContainer;
    public ArticleViewer.ErrorContainer createErrorContainer() {
        if (errorContainer == null) {
            swipeContainer.addView(errorContainer = new ArticleViewer.ErrorContainer(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            errorContainer.setTranslationY(-1);
            errorContainer.buttonView.setOnClickListener(v -> {
                BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                if (webView != null) {
                    webView.reload();
                }
            });
            errorContainer.setBackgroundColor(backgroundPaint.getColor());
            AndroidUtilities.updateViewVisibilityAnimated(errorContainer, errorShown, 1f, false);
        }
        return errorContainer;
    }

    private static int shownLockedBots = 0;
    public boolean attached = false;
    public void setAttached(boolean b) {
        if (attached == b) return;
        if (attached = b) {
            if (orientationLocked) {
                shownLockedBots++;
            }
        } else {
            if (orientationLocked) {
                shownLockedBots--;
            }
        }
        if (shownLockedBots > 0) {
            AndroidUtilities.lockOrientation(getActivity());
        } else {
            AndroidUtilities.unlockOrientation(getActivity());
        }
    }
    public void lockOrientation(boolean lock) {
        if (orientationLocked == lock) return;
        orientationLocked = lock;
        if (attached) {
            if (lock) {
                shownLockedBots++;
            } else {
                shownLockedBots--;
            }
        }
        if (shownLockedBots > 0) {
            AndroidUtilities.lockOrientation(getActivity());
        } else {
            AndroidUtilities.unlockOrientation(getActivity());
        }
    }
}

package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.ChatListItemAnimator;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
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
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.web.BotWebViewContainer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BotWebViewMenuContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, BottomSheetTabsOverlay.Sheet, BottomSheetTabsOverlay.SheetView {
    private final static int POLL_PERIOD = 60000;

    private final static SimpleFloatPropertyCompat<BotWebViewMenuContainer> ACTION_BAR_TRANSITION_PROGRESS_VALUE = new SimpleFloatPropertyCompat<BotWebViewMenuContainer>("actionBarTransitionProgress", obj -> obj.actionBarTransitionProgress, (obj, value) -> {
        obj.actionBarTransitionProgress = value;
        obj.invalidate();
        obj.invalidateActionBar();

        if (obj.botCollapseItem != null) {
            obj.botCollapseItem.setAlpha(value);
        }
    }).setMultiplier(100f);

    private float actionBarTransitionProgress;
    private SpringAnimation springAnimation;
    private ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
    private ChatAttachAlertBotWebViewLayout.WebProgressView progressView;
    private boolean ignoreLayout;
    private BotWebViewContainer webViewContainer;
    private BotWebViewContainer.Delegate webViewDelegate;
    private ValueAnimator webViewScrollAnimator;
    private boolean ignoreMeasure;

    private Paint dimPaint = new Paint();
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint actionBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint linePaint = new Paint();

    private ChatActivityEnterView parentEnterView;
    private boolean botWebViewButtonWasVisible;
    private SpringAnimation botWebViewButtonAnimator;

    private long lastSwipeTime;

    private int currentAccount;
    private long botId;
    private String botUrl;

    private boolean isLoaded;
    private TLObject loadedResponse;
    private long loadedResponseTime;
    private boolean dismissed;

    private Boolean wasLightStatusBar;
    private long queryId;

    private ActionBarMenuItem botMenuItem;
    private ActionBarMenuItem botCollapseItem;
    private ActionBar.ActionBarMenuOnItemClick actionBarOnItemClick;
    private ActionBarMenuSubItem settingsItem;
    private ActionBarMenuSubItem addToHomeScreenItem;

    private Editable savedEditText;
    private MessageObject savedReplyMessageObject;
    private MessageObject savedEditMessageObject;

    private Runnable globalOnDismissListener;

    private float overrideActionBarBackgroundProgress;
    private int overrideActionBarBackground;
    private boolean overrideBackgroundColor;
    private int actionBarColorKey = -1;

    private boolean needCloseConfirmation;
    ActionBarColorsAnimating actionBarColors;

    private Runnable pollRunnable = () -> {
        if (!dismissed) {
            TLRPC.TL_messages_prolongWebView prolongWebView = new TLRPC.TL_messages_prolongWebView();
            prolongWebView.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
            prolongWebView.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
            prolongWebView.query_id = queryId;

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
    private boolean actionBarIsLight;
    private int lineColor;
    private boolean preserving;

    public boolean isFullSize() {
        return false;
    }

    @Override
    public boolean setDialog(BottomSheetTabDialog dialog) {
        return false;
    }

    public BottomSheetTabs.WebTabData saveState() {
        preserving = true;
        BottomSheetTabs.WebTabData tab = new BottomSheetTabs.WebTabData();

        tab.actionBarColor = overrideBackgroundColor ? overrideActionBarBackground : actionBarColors.getColor(Theme.key_windowBackgroundWhite);
        tab.actionBarColorKey = actionBarColorKey;
        tab.overrideActionBarColor = overrideBackgroundColor;
        tab.backgroundColor = backgroundPaint.getColor();

        tab.props = new WebViewRequestProps();
        tab.props.currentAccount = currentAccount;
        tab.props.botId = botId;
        tab.props.peerId = botId;
        tab.props.buttonUrl = botUrl;
        tab.props.type = BotWebViewAttachedSheet.TYPE_BOT_MENU_BUTTON;
        tab.props.response = loadedResponse;
        tab.props.responseTime = loadedResponseTime;
        tab.ready = webViewContainer != null && webViewContainer.isPageLoaded();
        tab.lastUrl = webViewContainer != null ? webViewContainer.getUrlLoaded() : null;
        tab.themeIsDark = Theme.isCurrentThemeDark();
        tab.settings = settingsItem != null && settingsItem.getVisibility() == View.VISIBLE;
        tab.main = mainButtonSettings;
        tab.confirmDismiss = needCloseConfirmation;
        tab.expanded = swipeContainer != null && ((1f - Math.min(swipeContainer.getTopActionBarOffsetY(), swipeContainer.getTranslationY() - swipeContainer.getTopActionBarOffsetY()) / swipeContainer.getTopActionBarOffsetY()) > .5f);
        tab.expandedOffset = swipeContainer != null ? swipeContainer.getOffsetY() : Float.MAX_VALUE;
        tab.needsContext = true;

        BotWebViewContainer.MyWebView webView = webViewContainer == null ? null : webViewContainer.getWebView();
        if (webView != null) {
            webViewContainer.preserveWebView();
            tab.webView = webView;
            tab.proxy = webViewContainer == null ? null : webViewContainer.getBotProxy();
            tab.viewWidth = webView.getWidth();
            tab.viewScroll = webView.getScrollY();
            tab.viewHeight = webView.getHeight();
            webView.onPause();
//            webView.pauseTimers();
            webView.setContainers(null, null);
        }
        return tab;
    }

    // not really used, tab is already open from BotWebViewAttachedSheet place
    public boolean restoreState(BaseFragment fragment, BottomSheetTabs.WebTabData tab) {
        if (tab == null || tab.props == null) return false;
//        setBackgroundColor(tab.backgroundColor, false);
//        setActionBarColor(tab.actionBarColor, tab.overrideActionBarColor, false);
        if (tab.webView != null) {
//            tab.webView.resumeTimers();
            tab.webView.onResume();
            webViewContainer.replaceWebView(tab.webView, tab.proxy);
        } else {
            tab.props.response = null;
            tab.props.responseTime = 0;
        }
        currentAccount = tab.props.currentAccount;
        botId = tab.props.botId;
        botUrl = tab.props.buttonUrl;
        loadedResponse = tab.props.response;
        loadedResponseTime = tab.props.responseTime;
        loadWebView();
        return true;
    }

    private void checkBotMenuItem() {
        if (botCollapseItem == null) {
            ActionBarMenu menu = parentEnterView.getParentFragment().getActionBar().createMenu();
            botCollapseItem = menu.addItem(R.id.menu_collapse_bot, R.drawable.arrow_more);
            menu.removeView(botCollapseItem);
            menu.addView(botCollapseItem, 0);
            botCollapseItem.setOnClickListener(v -> {
                dismiss(true);
            });
            botCollapseItem.setAlpha(actionBarTransitionProgress);
            botCollapseItem.setVisibility(GONE);
        }
        if (botMenuItem == null) {
            ActionBarMenu menu = parentEnterView.getParentFragment().getActionBar().createMenu();
            botMenuItem = menu.addItem(1000, R.drawable.ic_ab_other);
            botMenuItem.setVisibility(GONE);

            botMenuItem.addSubItem(R.id.menu_reload_page, R.drawable.msg_retry, LocaleController.getString(R.string.BotWebViewReloadPage));
            settingsItem = botMenuItem.addSubItem(R.id.menu_settings, R.drawable.msg_settings, LocaleController.getString(R.string.BotWebViewSettings));
            settingsItem.setVisibility(View.GONE);
            addToHomeScreenItem = botMenuItem.addSubItem(R.id.menu_add_to_home_screen_bot, R.drawable.msg_home, LocaleController.getString(R.string.AddShortcut));
            if (botId != 0 && MediaDataController.getInstance(currentAccount).canCreateAttachedMenuBotShortcut(botId)) {
                addToHomeScreenItem.setVisibility(View.VISIBLE);
            } else {
                addToHomeScreenItem.setVisibility(View.GONE);
            }
            botMenuItem.addSubItem(R.id.menu_tos_bot, R.drawable.menu_intro, LocaleController.getString(R.string.BotWebViewToS));
        }
    }

    public BotWebViewMenuContainer(@NonNull Context context, ChatActivityEnterView parentEnterView) {
        super(context);
        actionBarColors = new ActionBarColorsAnimating();
        actionBarColors.setTo(0, null);
        actionBarColors.progress = 1;

        this.parentEnterView = parentEnterView;
        ChatActivity chatActivity = parentEnterView.getParentFragment();
        ActionBar actionBar = chatActivity.getActionBar();
        actionBarOnItemClick = actionBar.getActionBarMenuOnItemClick();

        webViewContainer = new BotWebViewContainer(context, parentEnterView.getParentFragment().getResourceProvider(), getColor(Theme.key_windowBackgroundWhite), true) {
            @Override
            public void onWebViewCreated() {
                swipeContainer.setWebView(webViewContainer.getWebView());
            }
        };
        webViewContainer.setDelegate(webViewDelegate = new BotWebViewContainer.Delegate() {

            @Override
            public void onCloseRequested(Runnable callback) {
                dismiss(callback);
            }

            @Override
            public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
                BotWebViewMenuContainer.this.needCloseConfirmation = needConfirmation;
            }

            @Override
            public void onWebAppSwipingBehavior(boolean allowSwiping) {
                swipeContainer.setAllowSwipes(allowSwiping);
            }

            @Override
            public void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {
                int from = overrideActionBarBackground;
                int to = color;

                actionBarColorKey = colorKey;
                actionBarColors = new BotWebViewMenuContainer.ActionBarColorsAnimating();
                actionBarColors.setFrom(overrideBackgroundColor ? from : 0, null);
                overrideBackgroundColor = isOverrideColor;
                actionBarIsLight = ColorUtils.calculateLuminance(color) < 0.5f;
                actionBarColors.setTo(overrideBackgroundColor ? to : 0, null);
                if (from == 0) {
                    overrideActionBarBackground = to;
                }

                ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    if (from != 0) {
                        overrideActionBarBackground = ColorUtils.blendARGB(from, to, progress);
                    } else {
                        overrideActionBarBackgroundProgress = progress;
                    }
                    actionBarColors.progress = progress;
                    actionBarPaint.setColor(overrideActionBarBackground);
                    invalidateActionBar();
                });
                animator.start();
            }

            @Override
            public void onWebAppSetBackgroundColor(int color) {
                overrideBackgroundColor = true;

                int from = backgroundPaint.getColor();
                ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    backgroundPaint.setColor(ColorUtils.blendARGB(from, color, (Float) animation.getAnimatedValue()));
                    BotWebViewMenuContainer.this.invalidate();
                });
                animator.start();
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
                    parentEnterView.setFieldText("@" + UserObject.getPublicUsername(botUser) + " " + query);
                    dismiss();
                } else {
                    Bundle args = new Bundle();
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT);
                    args.putBoolean("onlySelect", true);

                    args.putBoolean("allowGroups", chatTypes.contains("groups"));
                    args.putBoolean("allowUsers", chatTypes.contains("users"));
                    args.putBoolean("allowChannels", chatTypes.contains("channels"));
                    args.putBoolean("allowBots", chatTypes.contains("bots"));

                    DialogsActivity dialogsActivity = new DialogsActivity(args);
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

                        if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment)) {
                            fragment.presentFragment(new INavigationLayout.NavigationParams(new ChatActivity(args1)).setRemoveLast(true));
                        }
                        return true;
                    });
                    parentEnterView.getParentFragment().presentFragment(dialogsActivity);
                }
            }

            @Override
            public void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response) {
                ChatActivity parentFragment = parentEnterView.getParentFragment();
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.TL_payments_paymentFormStars) {
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
                    paymentFormActivity.setPaymentFormCallback(status -> webViewContainer.onInvoiceStatusUpdate(slug, status.name().toLowerCase(Locale.ROOT)));
                    parentFragment.presentFragment(paymentFormActivity);
                }
            }

            @Override
            public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect) {
                setMainButton(BotWebViewAttachedSheet.MainButtonSettings.of(isVisible, isActive, text, color, textColor, isProgressVisible));
            }

            @Override
            public void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position) {

            }

            @Override
            public void onSetBackButtonVisible(boolean visible) {
                if (actionBarTransitionProgress == 1f) {
                    if (visible) {
                        AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), actionBar.getBackButtonDrawable());
                    } else {
                        AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), R.drawable.ic_close_white);
                    }
                }
            }

            @Override
            public void onSetSettingsButtonVisible(boolean visible) {
                if (settingsItem != null) {
                    settingsItem.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public boolean isClipboardAvailable() {
                return MediaDataController.getInstance(currentAccount).botInAttachMenu(botId);
            }
        });

        linePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        dimPaint.setColor(0x40000000);

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

                if (getOffsetY() != padding) {
                    ignoreLayout = true;
                    setOffsetY(padding);
                    ignoreLayout = false;
                }

                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight + AndroidUtilities.dp(24) - AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        swipeContainer.setScrollListener(() -> {
            if (swipeContainer.getSwipeOffsetY() > 0) {
                dimPaint.setAlpha((int) (0x40 * (1f - Math.min(swipeContainer.getSwipeOffsetY(), swipeContainer.getHeight()) / (float)swipeContainer.getHeight())));
            } else {
                dimPaint.setAlpha(0x40);
            }
            invalidate();
            webViewContainer.invalidateViewPortHeight();

            if (springAnimation != null) {
                float progress = (1f - Math.min(swipeContainer.getTopActionBarOffsetY(), swipeContainer.getTranslationY() - swipeContainer.getTopActionBarOffsetY()) / swipeContainer.getTopActionBarOffsetY());
                if (BotWebViewMenuContainer.this.getVisibility() != VISIBLE || preserving) {
                    progress = 0;
                }
                float newPos = (progress > 0.5f ? 1 : 0) * 100f;
                if (springAnimation.getSpring().getFinalPosition() != newPos) {
                    springAnimation.getSpring().setFinalPosition(newPos);
                    if (newPos == 100) {
                        checkBotMenuItem();
                        if (botCollapseItem != null) {
                            botCollapseItem.setVisibility(VISIBLE);
                        }
                    }
                    springAnimation.start();

                    if (!webViewContainer.isBackButtonVisible()) {
                        if (newPos == 100f) {
                            AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), R.drawable.ic_close_white);
                        } else {
                            AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), actionBar.getBackButtonDrawable());
                        }
                    }
                }
            }
            lastSwipeTime = System.currentTimeMillis();
        });
        swipeContainer.setScrollEndListener(()-> webViewContainer.invalidateViewPortHeight(true));
        swipeContainer.addView(webViewContainer);
        swipeContainer.setDelegate(() -> {
//            if (!onCheckDismissByUser()) {
//                swipeContainer.stickTo(0);
//            }
            dismiss(true, null);
        });
        swipeContainer.setTopActionBarOffsetY(ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - AndroidUtilities.dp(24));
        swipeContainer.setSwipeOffsetAnimationDisallowed(true);
        swipeContainer.setIsKeyboardVisible(obj -> parentEnterView.getSizeNotifierLayout().getKeyboardHeight() >= AndroidUtilities.dp(20));
        addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 24, 0, 0));

        addView(progressView = new ChatAttachAlertBotWebViewLayout.WebProgressView(context, parentEnterView.getParentFragment().getResourceProvider()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 5));
        webViewContainer.setWebViewProgressListener(progress -> {
            progressView.setLoadProgressAnimated(progress);
            if (progress == 1f) {
                ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> progressView.setAlpha((Float) animation.getAnimatedValue()));
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressView.setVisibility(GONE);
                    }
                });
                animator.start();
            }
        });

        setWillNotDraw(false);
    }

    private void invalidateActionBar() {
        ChatActivity chatActivity = parentEnterView.getParentFragment();
        if (chatActivity == null || getVisibility() != VISIBLE) {
            return;
        }

        ActionBar actionBar = chatActivity.getActionBar();
        int backgroundColor = ColorUtils.blendARGB(getColor(Theme.key_actionBarDefault), getColor(Theme.key_windowBackgroundWhite), actionBarTransitionProgress);
        if (overrideBackgroundColor) {
            backgroundColor = ColorUtils.blendARGB(getColor(Theme.key_actionBarDefault), overrideActionBarBackground, actionBarTransitionProgress);
        } else {
            ColorUtils.blendARGB(getColor(Theme.key_actionBarDefault), actionBarColors.getColor(Theme.key_windowBackgroundWhite), actionBarTransitionProgress);
        }

        actionBar.setBackgroundColor(backgroundColor);
        actionBar.setItemsColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultIcon), actionBarColors.getColor(key_windowBackgroundWhiteBlackText), actionBarTransitionProgress), false);
        if (actionBar.backButtonImageView != null) {
            actionBar.backButtonImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultIcon), actionBarColors.getColor(key_windowBackgroundWhiteBlackText), actionBarTransitionProgress), PorterDuff.Mode.SRC_IN));
        }
        actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultSelector), actionBarColors.getColor(Theme.key_actionBarWhiteSelector), actionBarTransitionProgress), false);

        ChatAvatarContainer chatAvatarContainer = chatActivity.getAvatarContainer();
        chatAvatarContainer.setAlpha(1f - actionBarTransitionProgress);
        chatActivity.getOrCreateWebBotTitleView().setAlpha(actionBarTransitionProgress);
        chatActivity.getOrCreateWebBotTitleView().setTextColor(ColorUtils.blendARGB(getColor(Theme.key_actionBarDefaultIcon), actionBarColors.getColor(key_windowBackgroundWhiteBlackText), actionBarTransitionProgress));
        updateLightStatusBar();
    }

    public boolean onBackPressed() {
        if (webViewContainer.onBackPressed()) {
            return true;
        }

        if (getVisibility() == VISIBLE) {
            dismiss(true);
//            onCheckDismissByUser();
            return true;
        }

        return false;
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

    private void animateBotButton(boolean isVisible) {
        ChatActivityBotWebViewButton botWebViewButton = parentEnterView.getBotWebViewButton();
        if (botWebViewButtonAnimator != null) {
            botWebViewButtonAnimator.cancel();
            botWebViewButtonAnimator = null;
        }

        botWebViewButton.setProgress(isVisible ? 0f : 1f);
        if (isVisible) {
            botWebViewButton.setVisibility(VISIBLE);
        }

        botWebViewButtonAnimator = new SpringAnimation(botWebViewButton, ChatActivityBotWebViewButton.PROGRESS_PROPERTY)
                .setSpring(new SpringForce((isVisible ? 1f : 0f) * ChatActivityBotWebViewButton.PROGRESS_PROPERTY.getMultiplier())
                        .setStiffness(isVisible ? 600f : 750f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                )
                .addUpdateListener((animation, value, velocity) -> {
                    float v = value / ChatActivityBotWebViewButton.PROGRESS_PROPERTY.getMultiplier();
                    parentEnterView.setBotWebViewButtonOffsetX(AndroidUtilities.dp(64) * v);
                    parentEnterView.setComposeShadowAlpha(1f - v);
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    if (!isVisible) {
                        botWebViewButton.setVisibility(GONE);
                    }
                    if (botWebViewButtonAnimator == animation) {
                        botWebViewButtonAnimator = null;
                    }
                });
        botWebViewButtonAnimator.start();
        botWebViewButtonWasVisible = isVisible;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (springAnimation == null) {
            springAnimation = new SpringAnimation(this, ACTION_BAR_TRANSITION_PROGRESS_VALUE)
                    .setSpring(new SpringForce()
                            .setStiffness(1200f)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                    )
            .addEndListener((animation, canceled, value, velocity) -> {
                ChatActivity chatActivity = parentEnterView.getParentFragment();
                ChatAvatarContainer chatAvatarContainer = chatActivity.getAvatarContainer();
                chatAvatarContainer.setClickable(value == 0);
                chatAvatarContainer.getAvatarImageView().setClickable(value == 0);

                ActionBar actionBar = chatActivity.getActionBar();
                if (value == 100 && parentEnterView.hasBotWebView()) {
                    chatActivity.showHeaderItem(false);
                    checkBotMenuItem();
                    botMenuItem.setVisibility(VISIBLE);
                    botCollapseItem.setVisibility(VISIBLE);
//                    botCollapseItem.animate().alpha(1f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                        @Override
                        public void onItemClick(int id) {
                            if (id == -1) {
                                if (!webViewContainer.onBackPressed()) {
                                    onCheckDismissByUser();
                                }
                            } else if (id == R.id.menu_reload_page) {
                                if (webViewContainer.getWebView() != null) {
                                    webViewContainer.getWebView().animate().cancel();
                                    webViewContainer.getWebView().animate().alpha(0).start();
                                }

                                isLoaded = false;
                                progressView.setLoadProgress(0);
                                progressView.setAlpha(1f);
                                progressView.setVisibility(VISIBLE);

                                webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
                                webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
                                webViewContainer.reload();
                            } else if (id == R.id.menu_settings) {
                                webViewContainer.onSettingsButtonPressed();
                            } else if (id == R.id.menu_add_to_home_screen_bot) {
                                MediaDataController.getInstance(currentAccount).installShortcut(botId, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT);
                            } else if (id == R.id.menu_tos_bot) {
                                Browser.openUrl(getContext(), LocaleController.getString(R.string.BotWebViewToSLink));
                            }
                        }
                    });
                } else {
                    chatActivity.showHeaderItem(true);
                    if (botMenuItem != null) {
                        botMenuItem.setVisibility(GONE);
                    }
                    if (botCollapseItem != null) {
                        botCollapseItem.setVisibility(GONE);
                    }
                    actionBar.setActionBarMenuOnItemClick(actionBarOnItemClick);
                }
            });
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (springAnimation != null) {
            springAnimation.cancel();
            springAnimation = null;
        }
        actionBarTransitionProgress = 0f;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ignoreMeasure) {
            setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        if (!keyboardVisible) {
            return;
        }

        boolean doNotScroll = false;
        float openOffset = -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY();
        if (swipeContainer.getSwipeOffsetY() != openOffset) {
            swipeContainer.stickTo(openOffset);
            doNotScroll = true;
        }

        int oldh = contentHeight + parentEnterView.getSizeNotifierLayout().measureKeyboardHeight();
        setMeasuredDimension(getMeasuredWidth(), contentHeight);
        ignoreMeasure = true;

        if (!doNotScroll) {
            if (webViewScrollAnimator != null) {
                webViewScrollAnimator.cancel();
                webViewScrollAnimator = null;
            }

            if (webViewContainer.getWebView() != null) {
                int fromY = webViewContainer.getWebView().getScrollY();
                int toY = fromY + (oldh - contentHeight);
                webViewScrollAnimator = ValueAnimator.ofInt(fromY, toY).setDuration(250);
                webViewScrollAnimator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                webViewScrollAnimator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    if (webViewContainer.getWebView() != null) {
                        webViewContainer.getWebView().setScrollY(val);
                    }
                });
                webViewScrollAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (webViewContainer.getWebView() != null) {
                            webViewContainer.getWebView().setScrollY(toY);
                        }
                        if (animation == webViewScrollAnimator) {
                            webViewScrollAnimator = null;
                        }
                    }
                });
                webViewScrollAnimator.start();
            }
        }
    }

    public void onPanTransitionEnd() {
        ignoreMeasure = false;
        requestLayout();
    }

    private void updateLightStatusBar() {
        int color = backgroundPaint.getColor();
        boolean lightStatusBar = ColorUtils.calculateLuminance(color) >= 0.9 && actionBarTransitionProgress >= 0.85f;

        if (wasLightStatusBar != null && wasLightStatusBar == lightStatusBar) {
            return;
        }
        wasLightStatusBar = lightStatusBar;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getSystemUiVisibility();
            if (lightStatusBar) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drawingFromOverlay) return;
        super.onDraw(canvas);

        if (!overrideBackgroundColor) {
            backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
        }
        if (overrideActionBarBackgroundProgress == 0) {
            actionBarPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
        }
        AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
        canvas.drawRect(AndroidUtilities.rectTmp, dimPaint);

        float radius = AndroidUtilities.dp(16) * (1f - actionBarTransitionProgress);
        AndroidUtilities.rectTmp.set(0, AndroidUtilities.lerp(swipeContainer.getTranslationY(), 0, actionBarTransitionProgress), getWidth(), swipeContainer.getTranslationY() + AndroidUtilities.dp(24) + radius);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius,actionBarPaint );

        AndroidUtilities.rectTmp.set(0, swipeContainer.getTranslationY() + AndroidUtilities.dp(24), getWidth(), getHeight() + radius);
        canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() <= AndroidUtilities.lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(24), 0, actionBarTransitionProgress)) {
//            onCheckDismissByUser();
            dismiss(true);
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void draw(Canvas canvas) {
        if (drawingFromOverlay) return;

        super.draw(canvas);

        lineColor = actionBarColors.getColor(Theme.key_sheet_scrollUp);

        linePaint.setColor(lineColor);
        linePaint.setAlpha((int) (linePaint.getAlpha() * (1f - Math.min(0.5f, actionBarTransitionProgress) / 0.5f)));

        canvas.save();
        float scale = 1f - actionBarTransitionProgress;
        float y = AndroidUtilities.lerp(swipeContainer.getTranslationY(), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f, actionBarTransitionProgress) + AndroidUtilities.dp(12);
        canvas.scale(scale, scale, getWidth() / 2f, y);
        canvas.drawLine(getWidth() / 2f - AndroidUtilities.dp(16), y, getWidth() / 2f + AndroidUtilities.dp(16), y, linePaint);
        canvas.restore();
    }

    /**
     * Shows menu for the bot
     */
    public void show(int currentAccount, long botId, String botUrl) {
        dismissed = false;
        if (this.currentAccount != currentAccount || this.botId != botId || !Objects.equals(this.botUrl, botUrl)) {
            isLoaded = false;
        }
        this.currentAccount = currentAccount;
        this.botId = botId;
        this.botUrl = botUrl;

        if (addToHomeScreenItem != null) {
            if (MediaDataController.getInstance(currentAccount).canCreateAttachedMenuBotShortcut(botId)) {
                addToHomeScreenItem.setVisibility(View.VISIBLE);
            } else {
                addToHomeScreenItem.setVisibility(View.GONE);
            }
        }

        savedEditText = parentEnterView.getEditText();
        parentEnterView.getEditField().setText(null);
        savedReplyMessageObject = parentEnterView.getReplyingMessageObject();
        savedEditMessageObject = parentEnterView.getEditingMessageObject();
        ChatActivity chatActivity = parentEnterView.getParentFragment();
        if (chatActivity != null) {
            chatActivity.hideFieldPanel(true);
            TextView webViewTitle = chatActivity.getOrCreateWebBotTitleView();
            webViewTitle.setText(chatActivity.getCurrentUser().first_name);
        }

        if (!isLoaded) {
            overrideBackgroundColor = false;
            backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
            loadWebView();
        }

        setVisibility(VISIBLE);
        setAlpha(0f);
        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);

                swipeContainer.setSwipeOffsetY(swipeContainer.getHeight());
                setAlpha(1f);

                new SpringAnimation(swipeContainer, ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer.SWIPE_OFFSET_Y, 0)
                        .setSpring(new SpringForce(0)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                                .setStiffness(500.0f)
                        )
                        .addEndListener((animation, canceled, value, velocity) -> {
                            webViewContainer.restoreButtonData();
                            webViewContainer.invalidateViewPortHeight(true);
                        })
                        .start();
            }
        });
    }

    private void loadWebView() {
//        if (loadedResponse != null) {
//            processResponse(loadedResponse);
//            return;
//        }

        progressView.setLoadProgress(0);
        progressView.setAlpha(1f);
        progressView.setVisibility(VISIBLE);

        webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
        webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);

        TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
        req.platform = "android";

        req.url = botUrl;
        req.flags |= 2;

        Theme.ResourcesProvider resourcesProvider = null;
        if (parentEnterView != null && parentEnterView.getParentFragment() != null) {
            resourcesProvider = parentEnterView.getParentFragment().getResourceProvider();
        }
        JSONObject themeParams = BotWebViewSheet.makeThemeParams(resourcesProvider);
        if (themeParams != null) {
            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = themeParams.toString();
            req.flags |= 4;
        }
        req.from_bot_menu = true;

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            processResponse(response);
        }));
    }

    private void processResponse(TLObject response) {
        if (response instanceof TLRPC.TL_webViewResultUrl) {
            isLoaded = true;
            loadedResponse = response;
            loadedResponseTime = System.currentTimeMillis();

            TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) response;
            queryId = resultUrl.query_id;
            webViewContainer.loadUrl(currentAccount, resultUrl.url);
            swipeContainer.setWebView(webViewContainer.getWebView());

            AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD);
        }
    }

    private int getColor(int key) {
        Theme.ResourcesProvider resourcesProvider = parentEnterView.getParentFragment().getResourceProvider();
        return Theme.getColor(key, resourcesProvider);
    }

    /**
     * Sets global dismiss callback to run every time menu being dismissed
     */
    public void setOnDismissGlobalListener(Runnable callback) {
        globalOnDismissListener = callback;
    }

    /**
     * Dismisses menu
     */
    public void dismiss() {
        dismiss(false, null);
    }

    /**
     * Dismisses menu
     */
    public void dismiss(Runnable callback) {
        dismiss(false, callback);
    }

    public void dismiss(boolean intoTabs) {
        dismiss(intoTabs, null);
    }

    public void dismiss(boolean intoTabs, Runnable callback) {
        if (dismissed) {
            return;
        }
        dismissed = true;

        if (intoTabs && (LaunchActivity.instance == null || LaunchActivity.instance.getBottomSheetTabsOverlay() == null)) {
            intoTabs = false;
        }
        if (intoTabs) {
            if (springAnimation != null) {
                springAnimation.getSpring().setFinalPosition(0);
                springAnimation.start();
            }
            ChatActivity chatActivity = parentEnterView.getParentFragment();
            if (chatActivity != null) {
                ActionBar actionBar = chatActivity.getActionBar();
                if (actionBar != null) {
                    AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), actionBar.getBackButtonDrawable());
                }
            }
            LaunchActivity.instance.getBottomSheetTabsOverlay().dismissSheet(this);
        } else {
            swipeContainer.stickTo(swipeContainer.getHeight() + parentEnterView.getSizeNotifierLayout().measureKeyboardHeight(), () -> {
                onDismiss();
                if (callback != null) {
                    callback.run();
                }
                if (globalOnDismissListener != null) {
                    globalOnDismissListener.run();
                }
            });
        }
    }

    public void release() {
        onDismiss();
    }

    /**
     * Called when menu is fully dismissed
     */
    public void onDismiss() {
        ChatActivity chatActivity = parentEnterView == null ? null : parentEnterView.getParentFragment();
        if (botMenuItem != null) {
            botMenuItem.setVisibility(GONE);
        }
        if (botCollapseItem != null) {
            botCollapseItem.setVisibility(GONE);
        }

        setVisibility(GONE);

        needCloseConfirmation = false;
        overrideActionBarBackground = 0;
        overrideActionBarBackgroundProgress = 0;
        actionBarPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
        webViewContainer.destroyWebView();
        swipeContainer.removeView(webViewContainer);

        webViewContainer = new BotWebViewContainer(getContext(), parentEnterView.getParentFragment().getResourceProvider(), getColor(Theme.key_windowBackgroundWhite), true) {
            @Override
            public void onWebViewCreated() {
                swipeContainer.setWebView(webViewContainer.getWebView());
            }
        };
        webViewContainer.setDelegate(webViewDelegate);
        webViewContainer.setWebViewProgressListener(progress -> {
            progressView.setLoadProgressAnimated(progress);
            if (progress == 1f) {
                ValueAnimator animator = ValueAnimator.ofFloat(1, 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> progressView.setAlpha((Float) animation.getAnimatedValue()));
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progressView.setVisibility(GONE);
                    }
                });
                animator.start();
            }
        });
        swipeContainer.addView(webViewContainer);
        isLoaded = false;

        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
        boolean delayRestoreText = botWebViewButtonWasVisible;
        if (botWebViewButtonWasVisible) {
            botWebViewButtonWasVisible = false;
            animateBotButton(false);
        }
        mainButtonSettings = null;

        AndroidUtilities.runOnUIThread(()->{
            if (savedEditText != null && parentEnterView.getEditField() != null) {
                parentEnterView.getEditField().setText(savedEditText);
                savedEditText = null;
            }
            if (savedReplyMessageObject != null) {
                if (chatActivity != null) {
                    chatActivity.showFieldPanelForReply(savedReplyMessageObject);
                }
                savedReplyMessageObject = null;
            }
            if (savedEditMessageObject != null) {
                if (chatActivity != null) {
                    chatActivity.showFieldPanelForEdit(true, savedEditMessageObject);
                }
                savedEditMessageObject = null;
            }
        }, delayRestoreText ? 200 : 0);

        preserving = false;
    }

    public boolean hasSavedText() {
        return savedEditText != null || savedReplyMessageObject != null || savedEditMessageObject != null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webViewResultSent) {
            long queryId = (long) args[0];

            if (this.queryId == queryId) {
                dismiss();
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            webViewContainer.updateFlickerBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
            invalidate();
            invalidateActionBar();
            AndroidUtilities.runOnUIThread(this::invalidateActionBar, 300);
        }
    }

    public static class ActionBarColorsAnimating {

        SparseIntArray fromColors = new SparseIntArray();
        SparseIntArray toColors = new SparseIntArray();

        int[] colorKeys = new int[]{
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_actionBarWhiteSelector,
                Theme.key_actionBarDefaultSubmenuBackground,
                Theme.key_actionBarDefaultSubmenuItem,
                Theme.key_actionBarDefaultSubmenuItemIcon,
                Theme.key_dialogButtonSelector,
                Theme.key_sheet_scrollUp
        };

        public float progress;

        public void setFrom(int actionBarColor, Theme.ResourcesProvider resourcesProvider) {
            updateColors(fromColors, actionBarColor, resourcesProvider);
        }

        public void setTo(int actionBarColor, Theme.ResourcesProvider resourcesProvider) {
            updateColors(toColors, actionBarColor, resourcesProvider);
        }

        private void updateColors(SparseIntArray fromColors, int actionBarColor, Theme.ResourcesProvider resourcesProvider) {
            if (actionBarColor == 0) {
                for (int i = 0; i < colorKeys.length; i++) {
                    int key = colorKeys[i];
                    fromColors.put(key,  Theme.getColor(key, resourcesProvider));
                }
            } else {
                int color = ColorUtils.calculateLuminance(actionBarColor) < 0.5f ? Color.WHITE : Color.BLACK;
                int selectorColor = ColorUtils.setAlphaComponent(color, 60);
                for (int i = 0; i < colorKeys.length; i++) {
                    int key = colorKeys[i];
                    if (key == Theme.key_actionBarDefaultSubmenuBackground || key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_actionBarDefaultSubmenuItemIcon || key == Theme.key_dialogButtonSelector) {
                        fromColors.put(key,  Theme.getColor(key, resourcesProvider));
                    } else if (key == Theme.key_sheet_scrollUp) {
                        fromColors.put(key, ColorUtils.blendARGB(actionBarColor, color, 0.5f));
                    } else if (key == Theme.key_actionBarWhiteSelector || key == Theme.key_dialogButtonSelector) {
                        fromColors.put(key, selectorColor);
                    } else {
                        fromColors.put(key, color);
                    }
                }
            }
        }

        public int getColor(int key) {
            return ColorUtils.blendARGB(fromColors.get(key), toColors.get(key), progress);
        }

        public void updateActionBar(ActionBar actionBar, float progress) {
            this.progress = progress;
            actionBar.setTitleColor(getColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setSubtitleColor(Theme.multAlpha(getColor(Theme.key_windowBackgroundWhiteBlackText), .45f));
            actionBar.setItemsColor(getColor(Theme.key_windowBackgroundWhiteBlackText), false);
            if (actionBar.backButtonImageView != null) {
                actionBar.backButtonImageView.setColorFilter(new PorterDuffColorFilter(getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
            }
            actionBar.setItemsBackgroundColor(getColor(Theme.key_actionBarWhiteSelector), false);
//            actionBar.setPopupBackgroundColor(getColor(Theme.key_actionBarDefaultSubmenuBackground), false);
//            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItem), false, false);
//            actionBar.setPopupItemsColor(getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, false);
//            actionBar.setPopupItemsSelectorColor(getColor(Theme.key_dialogButtonSelector), false);
        }
    }


    private boolean drawingFromOverlay;
    public void setDrawingFromOverlay(boolean drawingFromOverlay) {
        if (this.drawingFromOverlay != drawingFromOverlay) {
            this.drawingFromOverlay = drawingFromOverlay;
            invalidate();
        }
    }

    public BottomSheetTabsOverlay.SheetView getWindowView() {
        return this;
    }

    @Override
    public void show() {
        show(currentAccount, botId, botUrl);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawingFromOverlay) return;
        super.dispatchDraw(canvas);
    }

    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    @Override
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
        final float r = AndroidUtilities.lerp(radius, dp(10), progress);
        clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawPaint(backgroundPaint);

        if (swipeContainer != null) {
            canvas.saveLayerAlpha(clipRect, (int) (0xFF * (1f - progress)), Canvas.ALL_SAVE_FLAG);
            canvas.translate(swipeContainer.getX(), Math.max(swipeContainer.getY(), clipRect.top) + progress * dp(51));
            swipeContainer.draw(canvas);
            canvas.restore();
        }

        canvas.restore();

        return r;
    }

    private BotWebViewAttachedSheet.MainButtonSettings mainButtonSettings;

    public void setMainButton(BotWebViewAttachedSheet.MainButtonSettings s) {
        mainButtonSettings = s;

        ChatActivityBotWebViewButton botWebViewButton = parentEnterView.getBotWebViewButton();
        botWebViewButton.setupButtonParams(s.isActive, s.text, s.color, s.textColor, s.isProgressVisible);
        botWebViewButton.setOnClickListener(v -> webViewContainer.onMainButtonPressed());
        if (s.isVisible != botWebViewButtonWasVisible) {
            animateBotButton(s.isVisible);
        }
    }

    @Override
    public int getNavigationBarColor(int color) {
        return 0;
    }

}

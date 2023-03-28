package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.ChatListItemAnimator;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

public class ChatAttachAlertBotWebViewLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {
    private final static int POLL_PERIOD = 60000;

    private BotWebViewContainer webViewContainer;
    private ValueAnimator webViewScrollAnimator;

    private boolean ignoreLayout;

    private long botId;
    private long peerId;
    private long queryId;
    private boolean silent;
    private int replyToMsgId;
    private int currentAccount;
    private String startCommand;

    private boolean needReload;
    private WebProgressView progressView;
    private WebViewSwipeContainer swipeContainer;
    private ActionBarMenuItem otherItem;
    private ActionBarMenuSubItem settingsItem;

    private int measureOffsetY;

    private long lastSwipeTime;

    private boolean ignoreMeasure;
    private boolean isBotButtonAvailable;

    private boolean hasCustomBackground;
    private int customBackground;

    private boolean needCloseConfirmation;

    private boolean destroyed;
    private Runnable pollRunnable = () -> {
        if (!destroyed) {
            TLRPC.TL_messages_prolongWebView prolongWebView = new TLRPC.TL_messages_prolongWebView();
            prolongWebView.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
            prolongWebView.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId);
            prolongWebView.query_id = queryId;
            prolongWebView.silent = silent;
            if (replyToMsgId != 0) {
                prolongWebView.reply_to_msg_id = replyToMsgId;
                prolongWebView.flags |= 1;
            }

            if (peerId < 0) {
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId);
                if (chatFull != null) {
                    TLRPC.Peer peer = chatFull.default_send_as;
                    if (peer != null) {
                        prolongWebView.send_as = MessagesController.getInstance(currentAccount).getInputPeer(peer);
                        prolongWebView.flags |= 8192;
                    }
                }
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(prolongWebView, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (destroyed) {
                    return;
                }
                if (error != null) {
                    parentAlert.dismiss();
                } else {
                    AndroidUtilities.runOnUIThread(this.pollRunnable, POLL_PERIOD);
                }
            }));
        }
    };

    public ChatAttachAlertBotWebViewLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(R.id.menu_open_bot, R.drawable.msg_bot, LocaleController.getString(R.string.BotWebViewOpenBot));
        settingsItem = otherItem.addSubItem(R.id.menu_settings, R.drawable.msg_settings, LocaleController.getString(R.string.BotWebViewSettings));
        otherItem.addSubItem(R.id.menu_reload_page, R.drawable.msg_retry, LocaleController.getString(R.string.BotWebViewReloadPage));
        otherItem.addSubItem(R.id.menu_delete_bot, R.drawable.msg_delete, LocaleController.getString(R.string.BotWebViewDeleteBot));
        parentAlert.actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!webViewContainer.onBackPressed()) {
                        onCheckDismissByUser();
                    }
                } else if (id == R.id.menu_open_bot) {
                    Bundle bundle = new Bundle();
                    bundle.putLong("user_id", botId);
                    parentAlert.baseFragment.presentFragment(new ChatActivity(bundle));
                    parentAlert.dismiss();
                } else if (id == R.id.menu_reload_page) {
                    if (webViewContainer.getWebView() != null) {
                        webViewContainer.getWebView().animate().cancel();
                        webViewContainer.getWebView().animate().alpha(0).start();
                    }

                    progressView.setLoadProgress(0);
                    progressView.setAlpha(1f);
                    progressView.setVisibility(VISIBLE);

                    webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
                    webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
                    webViewContainer.reload();
                } else if (id == R.id.menu_delete_bot) {
                    for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
                        if (bot.bot_id == botId) {
                            parentAlert.onLongClickBotButton(bot, MessagesController.getInstance(currentAccount).getUser(botId));
                            break;
                        }
                    }
                } else if (id == R.id.menu_settings) {
                    webViewContainer.onSettingsButtonPressed();
                }
            }
        });

        webViewContainer = new BotWebViewContainer(context, resourcesProvider, getThemedColor(Theme.key_dialogBackground)) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!isBotButtonAvailable) {
                        isBotButtonAvailable = true;
                        webViewContainer.restoreButtonData();
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        swipeContainer = new WebViewSwipeContainer(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(84) + measureOffsetY, MeasureSpec.EXACTLY));
            }
        };
        swipeContainer.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        swipeContainer.setScrollListener(() -> {
            parentAlert.updateLayout(this, true, 0);
            webViewContainer.invalidateViewPortHeight();
            lastSwipeTime = System.currentTimeMillis();
        });
        swipeContainer.setScrollEndListener(()-> webViewContainer.invalidateViewPortHeight(true));
        swipeContainer.setDelegate(() -> {
            if (!onCheckDismissByUser()) {
                swipeContainer.stickTo(0);
            }
        });
        swipeContainer.setIsKeyboardVisible(obj -> parentAlert.sizeNotifierFrameLayout.getKeyboardHeight() >= AndroidUtilities.dp(20));

        addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(progressView = new WebProgressView(context, resourcesProvider), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 84));

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

                requestEnableKeyboard();
            }
        });

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    public void setNeedCloseConfirmation(boolean needCloseConfirmation) {
        this.needCloseConfirmation = needCloseConfirmation;
    }

    @Override
    boolean onDismissWithTouchOutside() {
        onCheckDismissByUser();
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
                    .setPositiveButton(LocaleController.getString(R.string.BotWebViewCloseAnyway), (dialog2, which) -> parentAlert.dismiss())
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .create();
            dialog.show();
            TextView textView = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextRed));
            return false;
        } else {
            parentAlert.dismiss();
            return true;
        }
    }

    public void setCustomBackground(int customBackground) {
        this.customBackground = customBackground;
        hasCustomBackground = true;
    }

    @Override
    boolean hasCustomBackground() {
        return hasCustomBackground;
    }

    @Override
    public int getCustomBackground() {
        return customBackground;
    }

    public boolean canExpandByRequest() {
        return /* System.currentTimeMillis() - lastSwipeTime > 1000 && */ !swipeContainer.isSwipeInProgress();
    }

    public void setMeasureOffsetY(int measureOffsetY) {
        this.measureOffsetY = measureOffsetY;
        swipeContainer.requestLayout();
    }

    public void disallowSwipeOffsetAnimation() {
        swipeContainer.setSwipeOffsetAnimationDisallowed(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ignoreMeasure) {
            setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        if (!keyboardVisible) {
            return;
        }

        webViewContainer.setViewPortByMeasureSuppressed(true);

        boolean doNotScroll = false;
        float openOffset = -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY();
        if (swipeContainer.getSwipeOffsetY() != openOffset) {
            swipeContainer.stickTo(openOffset);
            doNotScroll = true;
        }

        int oldh = contentHeight + parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight();
        setMeasuredDimension(getMeasuredWidth(), contentHeight);
        ignoreMeasure = true;
        swipeContainer.setSwipeOffsetAnimationDisallowed(true);

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

    @Override
    public void onPanTransitionEnd() {
        ignoreMeasure = false;
        swipeContainer.setSwipeOffsetAnimationDisallowed(false);
        webViewContainer.setViewPortByMeasureSuppressed(false);
        requestLayout();
    }

    @Override
    void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        parentAlert.actionBar.setTitle(UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId)));
        swipeContainer.setSwipeOffsetY(0);
        if (webViewContainer.getWebView() != null) {
            webViewContainer.getWebView().scrollTo(0, 0);
        }
        if (parentAlert.getBaseFragment() != null) {
            webViewContainer.setParentActivity(parentAlert.getBaseFragment().getParentActivity());
        }
        otherItem.setVisibility(VISIBLE);

        if (!webViewContainer.isBackButtonVisible()) {
            AndroidUtilities.updateImageViewImageAnimated(parentAlert.actionBar.getBackButton(), R.drawable.ic_close_white);
        }
    }

    @Override
    void onShown() {
        if (webViewContainer.isPageLoaded()) {
            requestEnableKeyboard();
        }

        swipeContainer.setSwipeOffsetAnimationDisallowed(false);
        AndroidUtilities.runOnUIThread(() -> webViewContainer.restoreButtonData());
    }

    private void requestEnableKeyboard() {
        BaseFragment fragment = parentAlert.getBaseFragment();
        if (fragment instanceof ChatActivity && ((ChatActivity) fragment).contentView.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            AndroidUtilities.hideKeyboard(parentAlert.baseFragment.getFragmentView());
            AndroidUtilities.runOnUIThread(this::requestEnableKeyboard, 250);
            return;
        }

        parentAlert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setFocusable(true);
        parentAlert.setFocusable(true);
    }

    @Override
    void onHidden() {
        super.onHidden();

        parentAlert.setFocusable(false);
        parentAlert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    @Override
    int getCurrentItemTop() {
        return (int) (swipeContainer.getSwipeOffsetY() + swipeContainer.getOffsetY());
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    public String getStartCommand() {
        return startCommand;
    }

    public void requestWebView(int currentAccount, long peerId, long botId, boolean silent, int replyToMsgId) {
        requestWebView(currentAccount, peerId, botId, silent, replyToMsgId, null);
    }

    public void requestWebView(int currentAccount, long peerId, long botId, boolean silent, int replyToMsgId, String startCommand) {
        this.currentAccount = currentAccount;
        this.peerId = peerId;
        this.botId = botId;
        this.silent = silent;
        this.replyToMsgId = replyToMsgId;
        this.startCommand = startCommand;

        webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
        webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);

        TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId);
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
        req.silent = silent;
        req.platform = "android";

        if (peerId < 0) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-peerId);
            if (chatFull != null) {
                TLRPC.Peer peer = chatFull.default_send_as;
                if (peer != null) {
                    req.send_as = MessagesController.getInstance(currentAccount).getInputPeer(peer);
                    req.flags |= 8192;
                }
            }
        }
        if (startCommand != null) {
            req.start_param = startCommand;
            req.flags |= 8;
        }

        if (replyToMsgId != 0) {
            req.reply_to_msg_id = replyToMsgId;
            req.flags |= 1;
        }

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("bg_color", getThemedColor(Theme.key_dialogBackground));
            jsonObject.put("secondary_bg_color", getThemedColor(Theme.key_windowBackgroundGray));
            jsonObject.put("text_color", getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            jsonObject.put("hint_color", getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            jsonObject.put("link_color", getThemedColor(Theme.key_windowBackgroundWhiteLinkText));
            jsonObject.put("button_color", getThemedColor(Theme.key_featuredStickers_addButton));
            jsonObject.put("button_text_color", getThemedColor(Theme.key_featuredStickers_buttonText));

            req.theme_params = new TLRPC.TL_dataJSON();
            req.theme_params.data = jsonObject.toString();
            req.flags |= 4;
        } catch (Exception e) {
            FileLog.e(e);
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_webViewResultUrl) {
                TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) response;
                queryId = resultUrl.query_id;
                webViewContainer.loadUrl(currentAccount, resultUrl.url);
                swipeContainer.setWebView(webViewContainer.getWebView());

                AndroidUtilities.runOnUIThread(pollRunnable);
            }
        }));

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);
    }

    @Override
    void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        otherItem.removeAllSubItems();
        menu.removeView(otherItem);

        webViewContainer.destroyWebView();
        destroyed = true;

        AndroidUtilities.cancelRunOnUIThread(pollRunnable);
    }

    @Override
    void onHide() {
        super.onHide();
        otherItem.setVisibility(GONE);
        isBotButtonAvailable = false;
        if (!webViewContainer.isBackButtonVisible()) {
            AndroidUtilities.updateImageViewImageAnimated(parentAlert.actionBar.getBackButton(), R.drawable.ic_ab_back);
        }
        parentAlert.actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        if (webViewContainer.hasUserPermissions()) {
            webViewContainer.destroyWebView();
            needReload = true;
        }
    }

    public boolean needReload() {
        if (needReload) {
            needReload = false;
            return true;
        }
        return false;
    }

    @Override
    int getListTopPadding() {
        return (int) swipeContainer.getOffsetY();
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            padding = (int) (availableHeight / 3.5f);
        } else {
            padding = (availableHeight / 5 * 2);
        }
        parentAlert.setAllowNestedScroll(true);

        if (padding < 0) {
            padding = 0;
        }
        if (swipeContainer.getOffsetY() != padding) {
            ignoreLayout = true;
            swipeContainer.setOffsetY(padding);
            ignoreLayout = false;
        }
    }

    @Override
    int getButtonsHideOffset() {
        return (int) swipeContainer.getTopActionBarOffsetY() + AndroidUtilities.dp(12);
    }

    @Override
    boolean onBackPressed() {
        if (webViewContainer.onBackPressed()) {
            return true;
        }
        onCheckDismissByUser();
        return true;
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    void scrollToTop() {
        swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
    }

    @Override
    boolean shouldHideBottomButtons() {
        return false;
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    public BotWebViewContainer getWebViewContainer() {
        return webViewContainer;
    }

    public void setDelegate(BotWebViewContainer.Delegate delegate) {
        webViewContainer.setDelegate(delegate);
    }

    public boolean isBotButtonAvailable() {
        return isBotButtonAvailable;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webViewResultSent) {
            long queryId = (long) args[0];

            if (this.queryId == queryId) {
                webViewContainer.destroyWebView();
                needReload = true;
                parentAlert.dismiss();
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            webViewContainer.updateFlickerBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        }
    }

    public static class WebViewSwipeContainer extends FrameLayout {
        public final static SimpleFloatPropertyCompat<WebViewSwipeContainer> SWIPE_OFFSET_Y = new SimpleFloatPropertyCompat<>("swipeOffsetY", WebViewSwipeContainer::getSwipeOffsetY, WebViewSwipeContainer::setSwipeOffsetY);

        private GestureDetectorCompat gestureDetector;
        private boolean isScrolling;
        private boolean isSwipeDisallowed;

        private float topActionBarOffsetY = ActionBar.getCurrentActionBarHeight();
        private float offsetY = 0;
        private float pendingOffsetY = -1;
        private float pendingSwipeOffsetY = Integer.MIN_VALUE;
        private float swipeOffsetY;
        private boolean isSwipeOffsetAnimationDisallowed;

        private SpringAnimation offsetYAnimator;

        private boolean flingInProgress;

        private WebView webView;

        private Runnable scrollListener;
        private Runnable scrollEndListener;
        private Delegate delegate;

        private SpringAnimation scrollAnimator;

        private int swipeStickyRange;

        private GenericProvider<Void, Boolean> isKeyboardVisible = obj -> false;

        public WebViewSwipeContainer(@NonNull Context context) {
            super(context);

            int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (isSwipeDisallowed) {
                        return false;
                    }
                    if (velocityY >= 700 && (webView == null || webView.getScrollY() == 0)) {
                        flingInProgress = true;

                        if (swipeOffsetY >= swipeStickyRange) {
                            if (delegate != null) {
                                delegate.onDismiss();
                            }
                        } else {
                            stickTo(0);
                        }
                        return true;
                    } else if (velocityY <= -700 && swipeOffsetY > -offsetY + topActionBarOffsetY) {
                        flingInProgress = true;
                        stickTo(-offsetY + topActionBarOffsetY);
                        return true;
                    }
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (!isScrolling && !isSwipeDisallowed) {
                        if (isKeyboardVisible.provide(null) && swipeOffsetY == -offsetY + topActionBarOffsetY) {
                            isSwipeDisallowed = true;
                        } else if (Math.abs(distanceY) >= touchSlop && Math.abs(distanceY) * 1.5f >= Math.abs(distanceX) && (swipeOffsetY != -offsetY + topActionBarOffsetY || webView == null || distanceY < 0 && webView.getScrollY() == 0)) {
                            isScrolling = true;

                            MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                            for (int i = 0; i < getChildCount(); i++) {
                                getChildAt(i).dispatchTouchEvent(ev);
                            }
                            ev.recycle();

                            return true;
                        } else if (webView != null && webView.canScrollHorizontally(distanceX >= 0 ? 1 : -1) || Math.abs(distanceX) >= touchSlop && Math.abs(distanceX) * 1.5f >= Math.abs(distanceY)) {
                            isSwipeDisallowed = true;
                        }
                    }
                    if (isScrolling) {
                        if (distanceY < 0) {
                            if (swipeOffsetY > -offsetY + topActionBarOffsetY) {
                                swipeOffsetY -= distanceY;
                            } else if (webView != null) {
                                float newWebScrollY = webView.getScrollY() + distanceY;
                                webView.setScrollY((int) MathUtils.clamp(newWebScrollY, 0, Math.max(webView.getContentHeight(), webView.getHeight()) - topActionBarOffsetY));

                                if (newWebScrollY < 0) {
                                    swipeOffsetY -= newWebScrollY;
                                }
                            } else {
                                swipeOffsetY -= distanceY;
                            }
                        } else {
                            swipeOffsetY -= distanceY;

                            if (webView != null && swipeOffsetY < -offsetY + topActionBarOffsetY) {
                                float newWebScrollY = webView.getScrollY() - (swipeOffsetY + offsetY - topActionBarOffsetY);
                                webView.setScrollY((int) MathUtils.clamp(newWebScrollY, 0, Math.max(webView.getContentHeight(), webView.getHeight()) - topActionBarOffsetY));
                            }
                        }

                        swipeOffsetY = MathUtils.clamp(swipeOffsetY, -offsetY + topActionBarOffsetY, getHeight() - offsetY + topActionBarOffsetY);
                        invalidateTranslation();
                        return true;
                    }

                    return true;
                }
            });
            updateStickyRange();
        }

        public void setIsKeyboardVisible(GenericProvider<Void, Boolean> isKeyboardVisible) {
            this.isKeyboardVisible = isKeyboardVisible;
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            updateStickyRange();
        }

        private void updateStickyRange() {
            swipeStickyRange = AndroidUtilities.dp(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 8 : 64);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);

            if (disallowIntercept) {
                isSwipeDisallowed = true;
                isScrolling = false;
            }
        }

        public void setSwipeOffsetAnimationDisallowed(boolean swipeOffsetAnimationDisallowed) {
            isSwipeOffsetAnimationDisallowed = swipeOffsetAnimationDisallowed;
        }

        public void setScrollListener(Runnable scrollListener) {
            this.scrollListener = scrollListener;
        }

        public void setScrollEndListener(Runnable scrollEndListener) {
            this.scrollEndListener = scrollEndListener;
        }

        public void setWebView(WebView webView) {
            this.webView = webView;
        }

        public void setTopActionBarOffsetY(float topActionBarOffsetY) {
            this.topActionBarOffsetY = topActionBarOffsetY;
            invalidateTranslation();
        }

        public void setSwipeOffsetY(float swipeOffsetY) {
            this.swipeOffsetY = swipeOffsetY;
            invalidateTranslation();
        }

        public void setOffsetY(float offsetY) {
            if (pendingSwipeOffsetY != Integer.MIN_VALUE) {
                pendingOffsetY = offsetY;
                return;
            }

            if (offsetYAnimator != null) {
                offsetYAnimator.cancel();
            }

            float wasOffsetY = this.offsetY;
            float deltaOffsetY = offsetY - wasOffsetY;
            boolean wasOnTop = Math.abs(swipeOffsetY + wasOffsetY - topActionBarOffsetY) <= AndroidUtilities.dp(1);
            if (!isSwipeOffsetAnimationDisallowed) {
                if (offsetYAnimator != null) {
                    offsetYAnimator.cancel();
                }
                offsetYAnimator = new SpringAnimation(new FloatValueHolder(wasOffsetY))
                        .setSpring(new SpringForce(offsetY)
                                .setStiffness(1400)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                        .addUpdateListener((animation, value, velocity) -> {
                            this.offsetY = value;

                            float progress = (value - wasOffsetY) / deltaOffsetY;

                            if (wasOnTop) {
                                swipeOffsetY = MathUtils.clamp(swipeOffsetY - progress * Math.max(0, deltaOffsetY), -this.offsetY + topActionBarOffsetY, getHeight() - this.offsetY + topActionBarOffsetY);
                            }
                            if (scrollAnimator != null && scrollAnimator.getSpring().getFinalPosition() == -wasOffsetY + topActionBarOffsetY) {
                                scrollAnimator.getSpring().setFinalPosition(-offsetY + topActionBarOffsetY);
                            }
                            invalidateTranslation();
                        })
                        .addEndListener((animation, canceled, value, velocity) -> {
                            offsetYAnimator = null;

                            if (!canceled) {
                                WebViewSwipeContainer.this.offsetY = offsetY;
                                invalidateTranslation();
                            } else {
                                pendingOffsetY = offsetY;
                            }
                        });
                offsetYAnimator.start();
            } else {
                this.offsetY = offsetY;

                if (wasOnTop) {
                    swipeOffsetY = MathUtils.clamp(swipeOffsetY - Math.max(0, deltaOffsetY), -this.offsetY + topActionBarOffsetY, getHeight() - this.offsetY + topActionBarOffsetY);
                }
                invalidateTranslation();
            }
        }

        private void invalidateTranslation() {
            setTranslationY(Math.max(topActionBarOffsetY, offsetY + swipeOffsetY));
            if (scrollListener != null) {
                scrollListener.run();
            }
        }

        public float getTopActionBarOffsetY() {
            return topActionBarOffsetY;
        }

        public float getOffsetY() {
            return offsetY;
        }

        public float getSwipeOffsetY() {
            return swipeOffsetY;
        }

        public void setDelegate(Delegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (isScrolling && ev.getActionIndex() != 0) {
                return false;
            }

            MotionEvent rawEvent = MotionEvent.obtain(ev);
            int index = ev.getActionIndex();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                rawEvent.setLocation(ev.getRawX(index), ev.getRawY(index));
            } else {
                float offsetX = ev.getRawX() - ev.getX(), offsetY = ev.getRawY() - ev.getY();
                rawEvent.setLocation(ev.getX(index) + offsetX, ev.getY(index) + offsetY);
            }
            boolean detector = gestureDetector.onTouchEvent(rawEvent);
            rawEvent.recycle();

            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                isSwipeDisallowed = false;
                isScrolling = false;

                if (flingInProgress) {
                    flingInProgress = false;
                } else {
                    if (swipeOffsetY <= -swipeStickyRange) {
                        stickTo(-offsetY + topActionBarOffsetY);
                    } else if (swipeOffsetY > -swipeStickyRange && swipeOffsetY <= swipeStickyRange) {
                        stickTo(0);
                    } else {
                        if (delegate != null) {
                            delegate.onDismiss();
                        }
                    }
                }
            }

            boolean superTouch = super.dispatchTouchEvent(ev);
            if (!superTouch && !detector && ev.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            return superTouch || detector;
        }

        public void stickTo(float offset) {
            stickTo(offset, null);
        }

        public void stickTo(float offset, Runnable callback) {
            if (swipeOffsetY == offset || scrollAnimator != null && scrollAnimator.getSpring().getFinalPosition() == offset) {
                if (callback != null) {
                    callback.run();
                }
                if (scrollEndListener != null) {
                    scrollEndListener.run();
                }
                return;
            }
            pendingSwipeOffsetY = offset;

            if (offsetYAnimator != null) {
                offsetYAnimator.cancel();
            }
            if (scrollAnimator != null) {
                scrollAnimator.cancel();
            }
            scrollAnimator = new SpringAnimation(this, SWIPE_OFFSET_Y, offset)
                    .setSpring(new SpringForce(offset)
                            .setStiffness(1400)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                    .addEndListener((animation, canceled, value, velocity) -> {
                        if (animation == scrollAnimator) {
                            scrollAnimator = null;

                            if (callback != null) {
                                callback.run();
                            }

                            if (scrollEndListener != null) {
                                scrollEndListener.run();
                            }

                            if (pendingOffsetY != -1) {
                                boolean wasDisallowed = isSwipeOffsetAnimationDisallowed;
                                isSwipeOffsetAnimationDisallowed = true;
                                setOffsetY(pendingOffsetY);
                                pendingOffsetY = -1;
                                isSwipeOffsetAnimationDisallowed = wasDisallowed;
                            }
                            pendingSwipeOffsetY = Integer.MIN_VALUE;
                        }
                    });
            scrollAnimator.start();
        }

        public boolean isSwipeInProgress() {
            return isScrolling;
        }

        public interface Delegate {
            /**
             * Called to dismiss parent layout
             */
            void onDismiss();
        }
    }

    public static class WebProgressView extends View {
        private final SimpleFloatPropertyCompat<WebProgressView> LOAD_PROGRESS_PROPERTY = new SimpleFloatPropertyCompat<>("loadProgress", obj -> obj.loadProgress, WebProgressView::setLoadProgress).setMultiplier(100f);

        private Paint bluePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float loadProgress;
        private SpringAnimation springAnimation;
        private Theme.ResourcesProvider resourcesProvider;

        public WebProgressView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            bluePaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
            bluePaint.setStyle(Paint.Style.STROKE);
            bluePaint.setStrokeWidth(AndroidUtilities.dp(2));
            bluePaint.setStrokeCap(Paint.Cap.ROUND);
        }

        protected int getThemedColor(String key) {
            Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
            return color != null ? color : Theme.getColor(key);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();

            springAnimation = new SpringAnimation(this, LOAD_PROGRESS_PROPERTY)
                    .setSpring(new SpringForce()
                        .setStiffness(400f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            springAnimation.cancel();
            springAnimation = null;
        }

        public void setLoadProgressAnimated(float loadProgress) {
            if (springAnimation == null) {
                setLoadProgress(loadProgress);
                return;
            }
            springAnimation.getSpring().setFinalPosition(loadProgress * 100f);
            springAnimation.start();
        }

        public void setLoadProgress(float loadProgress) {
            this.loadProgress = loadProgress;
            invalidate();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);

            float y = getHeight() - bluePaint.getStrokeWidth() / 2f;
            canvas.drawLine(0, y, getWidth() * loadProgress, y, bluePaint);
        }
    }
}

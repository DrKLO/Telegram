package org.telegram.ui.bots;

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
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OverlayActionBarLayoutDialog;
import org.telegram.ui.Components.PasscodeView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PaymentFormActivity;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;

public class BotWebViewSheet extends Dialog implements NotificationCenter.NotificationCenterDelegate {
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
            BulletinFactory.of(frameLayout, resourcesProvider)
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

    private final static SimpleFloatPropertyCompat<BotWebViewSheet> ACTION_BAR_TRANSITION_PROGRESS_VALUE = new SimpleFloatPropertyCompat<BotWebViewSheet>("actionBarTransitionProgress", obj -> obj.actionBarTransitionProgress, (obj, value) -> {
        obj.actionBarTransitionProgress = value;
        obj.frameLayout.invalidate();

        obj.actionBar.setAlpha(value);

        obj.updateLightStatusBar();
    }).setMultiplier(100f);
    private float actionBarTransitionProgress = 0f;
    private SpringAnimation springAnimation;

    private Boolean wasLightStatusBar;

    private SizeNotifierFrameLayout frameLayout;

    private long lastSwipeTime;

    private ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
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
    private boolean actionBarIsLight;
    private Paint actionBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean overrideBackgroundColor;

    private ActionBar actionBar;
    private Drawable actionBarShadow;
    private ActionBarMenuSubItem settingsItem;
    private TLRPC.BotApp currentWebApp;

    private boolean dismissed;

    private Activity parentActivity;

    private boolean mainButtonWasVisible, mainButtonProgressWasVisible;
    private TextView mainButton;
    private RadialProgressView radialProgressView;

    private boolean needCloseConfirmation;

    private VerticalPositionAutoAnimator mainButtonAutoAnimator, radialProgressAutoAnimator;

    private PasscodeView passcodeView;

    private Runnable pollRunnable = () -> {
        if (!dismissed) {
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

                if (getOffsetY() != padding && !dismissed) {
                    ignoreLayout = true;
                    setOffsetY(padding);
                    ignoreLayout = false;
                }

                if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight + AndroidUtilities.dp(24) - (mainButtonWasVisible ? mainButton.getLayoutParams().height : 0), MeasureSpec.EXACTLY));
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        webViewContainer = new BotWebViewContainer(context, resourcesProvider, getColor(Theme.key_windowBackgroundWhite)) {
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
                dismiss(callback);
            }

            @Override
            public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
                BotWebViewSheet.this.needCloseConfirmation = needConfirmation;
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
            public void onWebAppSetActionBarColor(int color, boolean isOverrideColor) {
                int from = actionBarColor;
                int to = color;

                BotWebViewMenuContainer.ActionBarColorsAnimating actionBarColorsAnimating = new BotWebViewMenuContainer.ActionBarColorsAnimating();
                actionBarColorsAnimating.setFrom(overrideBackgroundColor ? actionBarColor : 0, resourcesProvider);
                overrideBackgroundColor = isOverrideColor;
                actionBarIsLight = ColorUtils.calculateLuminance(color) < 0.5f;
                actionBarColorsAnimating.setTo(overrideBackgroundColor ? to : 0, resourcesProvider);

                ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    actionBarColor = ColorUtils.blendARGB(from, to, progress);
                    actionBar.setBackgroundColor(actionBarColor);

                    actionBarColorsAnimating.updateActionBar(actionBar, progress);
                    lineColor = actionBarColorsAnimating.getColor(Theme.key_sheet_scrollUp);

                    frameLayout.invalidate();
                });
                animator.start();
                updateLightStatusBar();
            }

            @Override
            public void onWebAppSetBackgroundColor(int color) {
                int from = backgroundPaint.getColor();
                ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    backgroundPaint.setColor(ColorUtils.blendARGB(from, color, (Float) animation.getAnimatedValue()));
                    updateActionBarColors();
                    frameLayout.invalidate();
                });
                animator.start();
            }

            @Override
            public void onSetBackButtonVisible(boolean visible) {
                AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), visible ? R.drawable.ic_ab_back : R.drawable.ic_close_white);
            }

            @Override
            public void onSetSettingsButtonVisible(boolean visible) {
                if (settingsItem != null) {
                    settingsItem.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onWebAppOpenInvoice(String slug, TLObject response) {
                BaseFragment parentFragment = ((LaunchActivity) parentActivity).getActionBarLayout().getLastFragment();
                PaymentFormActivity paymentFormActivity = null;
                if (response instanceof TLRPC.TL_payments_paymentForm) {
                    TLRPC.TL_payments_paymentForm form = (TLRPC.TL_payments_paymentForm) response;
                    MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                    paymentFormActivity = new PaymentFormActivity(form, slug, parentFragment);
                } else if (response instanceof TLRPC.TL_payments_paymentReceipt) {
                    paymentFormActivity = new PaymentFormActivity((TLRPC.TL_payments_paymentReceipt) response);
                }

                if (paymentFormActivity != null) {
                    swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());

                    AndroidUtilities.hideKeyboard(frameLayout);
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
                    AndroidUtilities.hideKeyboard(frameLayout);
                    OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(context, resourcesProvider);
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
                                NotificationCenter.getInstance(currentAccount).removeObserver(BotWebViewSheet.this, NotificationCenter.webViewResultSent);
                                NotificationCenter.getGlobalInstance().removeObserver(BotWebViewSheet.this, NotificationCenter.didSetNewTheme);
                                BotWebViewSheet.super.dismiss();

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
                mainButton.setClickable(isActive);
                mainButton.setText(text);
                mainButton.setTextColor(textColor);
                mainButton.setBackground(BotWebViewContainer.getMainButtonRippleDrawable(color));
                if (isVisible != mainButtonWasVisible) {
                    mainButtonWasVisible = isVisible;
                    mainButton.animate().cancel();
                    if (isVisible) {
                        mainButton.setAlpha(0f);
                        mainButton.setVisibility(View.VISIBLE);
                    }
                    mainButton.animate().alpha(isVisible ? 1f : 0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!isVisible) {
                                mainButton.setVisibility(View.GONE);
                            }
                            swipeContainer.requestLayout();
                        }
                    }).start();
                }
                radialProgressView.setProgressColor(textColor);
                if (isProgressVisible != mainButtonProgressWasVisible) {
                    mainButtonProgressWasVisible = isProgressVisible;
                    radialProgressView.animate().cancel();
                    if (isProgressVisible) {
                        radialProgressView.setAlpha(0f);
                        radialProgressView.setVisibility(View.VISIBLE);
                    }
                    radialProgressView.animate().alpha(isProgressVisible ? 1f : 0f)
                            .scaleX(isProgressVisible ? 1f : 0.1f)
                            .scaleY(isProgressVisible ? 1f : 0.1f)
                            .setDuration(250)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (!isProgressVisible) {
                                        radialProgressView.setVisibility(View.GONE);
                                    }
                                }
                            }).start();
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
        });

        linePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(4));
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        dimPaint.setColor(0x40000000);
        actionBarColor = getColor(Theme.key_windowBackgroundWhite);
        frameLayout = new SizeNotifierFrameLayout(context) {
            {
                setWillNotDraw(false);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                if (passcodeView.getVisibility() != View.VISIBLE) {
                    if (!overrideBackgroundColor) {
                        backgroundPaint.setColor(getColor(Theme.key_windowBackgroundWhite));
                    }
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    canvas.drawRect(AndroidUtilities.rectTmp, dimPaint);

                    actionBarPaint.setColor(actionBarColor);
                    float radius = AndroidUtilities.dp(16) * (AndroidUtilities.isTablet() ? 1f : 1f - actionBarTransitionProgress);
                    AndroidUtilities.rectTmp.set(swipeContainer.getLeft(), AndroidUtilities.lerp(swipeContainer.getTranslationY(), 0, actionBarTransitionProgress), swipeContainer.getRight(), swipeContainer.getTranslationY() + AndroidUtilities.dp(24) + radius);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, actionBarPaint);

                    AndroidUtilities.rectTmp.set(swipeContainer.getLeft(), swipeContainer.getTranslationY() + AndroidUtilities.dp(24), swipeContainer.getRight(), getHeight());
                    canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);
                }
            }

            @Override
            public void draw(Canvas canvas) {
                super.draw(canvas);

                float transitionProgress = AndroidUtilities.isTablet() ? 0 : actionBarTransitionProgress;
                linePaint.setColor(lineColor);
                linePaint.setAlpha((int) (linePaint.getAlpha() * (1f - Math.min(0.5f, transitionProgress) / 0.5f)));

                canvas.save();
                float scale = 1f - transitionProgress;
                float y = AndroidUtilities.isTablet() ? AndroidUtilities.lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(12), AndroidUtilities.statusBarHeight / 2f, actionBarTransitionProgress) :
                        (AndroidUtilities.lerp(swipeContainer.getTranslationY(), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f, transitionProgress) + AndroidUtilities.dp(12));
                canvas.scale(scale, scale, getWidth() / 2f, y);
                canvas.drawLine(getWidth() / 2f - AndroidUtilities.dp(16), y, getWidth() / 2f + AndroidUtilities.dp(16), y, linePaint);
                canvas.restore();

                actionBarShadow.setAlpha((int) (actionBar.getAlpha() * 0xFF));
                y = actionBar.getY() + actionBar.getTranslationY() + actionBar.getHeight();
                actionBarShadow.setBounds(0, (int)y, getWidth(), (int)(y + actionBarShadow.getIntrinsicHeight()));
                actionBarShadow.draw(canvas);
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && (event.getY() <= AndroidUtilities.lerp(swipeContainer.getTranslationY() + AndroidUtilities.dp(24), 0, actionBarTransitionProgress) ||
                        event.getX() > swipeContainer.getRight() || event.getX() < swipeContainer.getLeft())) {
                    onCheckDismissByUser();
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
        };
        frameLayout.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight > AndroidUtilities.dp(20)) {
                swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
            }
        });
        frameLayout.addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        mainButton = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f), MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        mainButton.setVisibility(View.GONE);
        mainButton.setAlpha(0f);
        mainButton.setSingleLine();
        mainButton.setGravity(Gravity.CENTER);
        mainButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        int padding = AndroidUtilities.dp(16);
        mainButton.setPadding(padding, 0, padding, 0);
        mainButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        mainButton.setOnClickListener(v -> webViewContainer.onMainButtonPressed());
        frameLayout.addView(mainButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        mainButtonAutoAnimator = VerticalPositionAutoAnimator.attach(mainButton);

        radialProgressView = new RadialProgressView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) getLayoutParams();
                if (AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isSmallTablet()) {
                    params.rightMargin = (int) (AndroidUtilities.dp(10) + Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.1f);
                } else {
                    params.rightMargin = AndroidUtilities.dp(10);
                }
            }
        };
        radialProgressView.setSize(AndroidUtilities.dp(18));
        radialProgressView.setAlpha(0f);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        radialProgressView.setVisibility(View.GONE);
        frameLayout.addView(radialProgressView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 10));
        radialProgressAutoAnimator = VerticalPositionAutoAnimator.attach(radialProgressView);

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
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        frameLayout.addView(progressView = new ChatAttachAlertBotWebViewLayout.WebProgressView(context, resourcesProvider) {
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
            frameLayout.invalidate();
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
            if (!onCheckDismissByUser()) {
                swipeContainer.stickTo(0);
            }
        });
        swipeContainer.setTopActionBarOffsetY(ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight - AndroidUtilities.dp(24));
        swipeContainer.setIsKeyboardVisible(obj -> frameLayout.getKeyboardHeight() >= AndroidUtilities.dp(20));

        passcodeView = new PasscodeView(context);
        frameLayout.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setContentView(frameLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        if (!overrideBackgroundColor) {
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
            lightStatusBar = !actionBarIsLight;
        } else {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
            lightStatusBar = !AndroidUtilities.isTablet() && ColorUtils.calculateLuminance(color) >= 0.9 && actionBarTransitionProgress >= 0.85f;
        }
        if (wasLightStatusBar != null && wasLightStatusBar == lightStatusBar) {
            return;
        }
        wasLightStatusBar = lightStatusBar;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = frameLayout.getSystemUiVisibility();
            if (lightStatusBar) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            frameLayout.setSystemUiVisibility(flags);
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
        window.setAttributes(params);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        frameLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frameLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());
                return insets;
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
            AndroidUtilities.setLightNavigationBar(window, ColorUtils.calculateLuminance(color) >= 0.9);
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

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
            return jsonObject;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public void requestWebView(int currentAccount, long peerId, long botId, String buttonText, String buttonUrl, @WebViewType int type, int replyToMsgId, boolean silent, int flags) {
        requestWebView(currentAccount, peerId, botId, buttonText, buttonUrl, type, replyToMsgId, silent, null, null, false, null, null, flags);
    }

    public void requestWebView(int currentAccount, long peerId, long botId, String buttonText, String buttonUrl, @WebViewType int type, int replyToMsgId, boolean silent) {
        requestWebView(currentAccount, peerId, botId, buttonText, buttonUrl, type, replyToMsgId, silent, null, null, false, null, null, 0);
    }

    public void requestWebView(int currentAccount, long peerId, long botId, String buttonText, String buttonUrl, @WebViewType int type, int replyToMsgId, boolean silent, BaseFragment lastFragment, TLRPC.BotApp app, boolean allowWrite, String startParam, TLRPC.User botUser) {
        requestWebView(currentAccount, peerId, botId, buttonText, buttonUrl, type, replyToMsgId, silent, lastFragment, app, allowWrite, startParam, botUser, 0);
    }

    public void requestWebView(int currentAccount, long peerId, long botId, String buttonText, String buttonUrl, @WebViewType int type, int replyToMsgId, boolean silent, BaseFragment lastFragment, TLRPC.BotApp app, boolean allowWrite, String startParam, TLRPC.User botUser, int flags) {
        this.currentAccount = currentAccount;
        this.peerId = peerId;
        this.botId = botId;
        this.replyToMsgId = replyToMsgId;
        this.silent = silent;
        this.buttonText = buttonText;
        this.currentWebApp = app;

        CharSequence title = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(botId));
        try {
            TextPaint tp = new TextPaint();
            tp.setTextSize(AndroidUtilities.dp(20));
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
                }
            }
        });

        final JSONObject themeParams = makeThemeParams(resourcesProvider);

        webViewContainer.setBotUser(MessagesController.getInstance(currentAccount).getUser(botId));
        webViewContainer.loadFlickerAndSettingsItem(currentAccount, botId, settingsItem);
        preloadShortcutBotIcon(botUser, currentBot);
        switch (type) {
            case TYPE_BOT_MENU_BUTTON: {
                TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(botId);
                req.platform = "android";

                req.url = buttonUrl;
                req.flags |= 2;

                if (themeParams != null) {
                    req.theme_params = new TLRPC.TL_dataJSON();
                    req.theme_params.data = themeParams.toString();
                    req.flags |= 4;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_webViewResultUrl) {
                        TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) response;
                        queryId = resultUrl.query_id;
                        webViewContainer.loadUrl(currentAccount, resultUrl.url);
                        AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD);
                    }
                }));
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);

                break;
            }
            case TYPE_SIMPLE_WEB_VIEW_BUTTON: {
                TLRPC.TL_messages_requestSimpleWebView req = new TLRPC.TL_messages_requestSimpleWebView();
                req.from_switch_webview = (flags & FLAG_FROM_INLINE_SWITCH) != 0;
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                req.platform = "android";
                req.from_side_menu = (flags & FLAG_FROM_SIDE_MENU) != 0;
                if (themeParams != null) {
                    req.theme_params = new TLRPC.TL_dataJSON();
                    req.theme_params.data = themeParams.toString();
                    req.flags |= 1;
                }
                if (!TextUtils.isEmpty(buttonUrl)) {
                    req.flags |= 8;
                    req.url = buttonUrl;
                }
                if (!TextUtils.isEmpty(startParam)) {
                    req.start_param = startParam;
                    req.flags |= 16;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_simpleWebViewResultUrl) {
                        TLRPC.TL_simpleWebViewResultUrl resultUrl = (TLRPC.TL_simpleWebViewResultUrl) response;
                        queryId = 0;
                        webViewContainer.loadUrl(currentAccount, resultUrl.url);
                    }
                }));
                break;
            }
            case TYPE_WEB_VIEW_BUTTON: {
                TLRPC.TL_messages_requestWebView req = new TLRPC.TL_messages_requestWebView();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(peerId);
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
                req.platform = "android";
                if (buttonUrl != null) {
                    req.url = buttonUrl;
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
                    if (response instanceof TLRPC.TL_webViewResultUrl) {
                        TLRPC.TL_webViewResultUrl resultUrl = (TLRPC.TL_webViewResultUrl) response;
                        queryId = resultUrl.query_id;
                        webViewContainer.loadUrl(currentAccount, resultUrl.url);
                        AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD);
                    }
                }));
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.webViewResultSent);
                break;
            }
            case TYPE_WEB_VIEW_BOT_APP: {
                TLRPC.TL_messages_requestAppWebView req = new TLRPC.TL_messages_requestAppWebView();
                TLRPC.TL_inputBotAppID botApp = new TLRPC.TL_inputBotAppID();
                botApp.id = app.id;
                botApp.access_hash = app.access_hash;

                req.app = botApp;
                req.write_allowed = allowWrite;
                req.platform = "android";
                req.peer = lastFragment instanceof ChatActivity ? ((ChatActivity) lastFragment).getCurrentUser() != null ? MessagesController.getInputPeer(((ChatActivity) lastFragment).getCurrentUser()) : MessagesController.getInputPeer(((ChatActivity) lastFragment).getCurrentChat())
                        : MessagesController.getInputPeer(botUser);

                if (!TextUtils.isEmpty(startParam)) {
                    req.start_param = startParam;
                    req.flags |= 2;
                }

                if (themeParams != null) {
                    req.theme_params = new TLRPC.TL_dataJSON();
                    req.theme_params.data = themeParams.toString();
                    req.flags |= 4;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error2 == null) {
                        TLRPC.TL_appWebViewResultUrl result = (TLRPC.TL_appWebViewResultUrl) response2;
                        queryId = 0;
                        webViewContainer.loadUrl(currentAccount, result.url);

                        AndroidUtilities.runOnUIThread(pollRunnable, POLL_PERIOD);
                    }
                }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
            }
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

    @Override
    public void show() {
        frameLayout.setAlpha(0f);
        frameLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);

                swipeContainer.setSwipeOffsetY(swipeContainer.getHeight());
                frameLayout.setAlpha(1f);

                AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
                locker.lock();
                new SpringAnimation(swipeContainer, ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer.SWIPE_OFFSET_Y, 0)
                        .setSpring(new SpringForce(0)
                                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                                .setStiffness(500.0f)
                        ).addEndListener((animation, canceled, value, velocity) -> {
                            locker.unlock();
                        }).start();
            }
        });
        super.show();
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
        onCheckDismissByUser();
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
        if (dismissed) {
            return;
        }
        dismissed = true;
        AndroidUtilities.cancelRunOnUIThread(pollRunnable);

        webViewContainer.destroyWebView();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.webViewResultSent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);

        swipeContainer.stickTo(swipeContainer.getHeight() + frameLayout.measureKeyboardHeight(), ()->{
            try {
                super.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (callback != null) {
                callback.run();
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.webViewResultSent) {
            long queryId = (long) args[0];

            if (this.queryId == queryId) {
                dismiss();
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            frameLayout.invalidate();
            webViewContainer.updateFlickerBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
            updateActionBarColors();
            updateLightStatusBar();
        }
    }
}

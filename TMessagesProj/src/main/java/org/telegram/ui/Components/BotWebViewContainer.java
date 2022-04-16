package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class BotWebViewContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final static String DURGER_KING_USERNAME = "DurgerKingBot";
    private final static int REQUEST_CODE_WEB_VIEW_FILE = 3000, REQUEST_CODE_WEB_PERMISSION = 4000;

    private final static List<String> WHITELISTED_SCHEMES = Arrays.asList("http", "https");

    private WebView webView;
    private String mUrl;
    private Delegate delegate;
    private WebViewScrollListener webViewScrollListener;
    private Theme.ResourcesProvider resourcesProvider;

    private CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();
    private BackupImageView flickerView;
    private boolean isFlickeringCenter;

    private Consumer<Float> webViewProgressListener;

    private ValueCallback<Uri[]> mFilePathCallback;

    private int lastButtonColor = Theme.getColor(Theme.key_featuredStickers_addButton);
    private int lastButtonTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
    private String lastButtonText = "";
    private String buttonData;

    private boolean isPageLoaded;
    private int viewPortOffset;
    private boolean lastExpanded;

    private boolean hasUserPermissions;
    private TLRPC.User botUser;
    private Runnable onPermissionsRequestResultCallback;

    private Activity parentActivity;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public BotWebViewContainer(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, int backgroundColor) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        if (context instanceof Activity) {
            this.parentActivity = (Activity) context;
        }

        flickerDrawable.drawFrame = false;
        flickerDrawable.setColors(backgroundColor, 0x99, 0xCC);
        flickerView = new BackupImageView(context) {
            {
                imageReceiver = new ImageReceiver(this) {
                    @Override
                    protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                        boolean set = super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                        ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(300);
                        anim.addUpdateListener(animation -> {
                            imageReceiver.setAlpha((Float) animation.getAnimatedValue());
                            invalidate();
                        });
                        anim.start();
                        return set;
                    }
                };
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (isFlickeringCenter) {
                    super.onDraw(canvas);
                } else {
                    Drawable drawable = imageReceiver.getDrawable();
                    if (drawable != null) {
                        imageReceiver.setImageCoords(0, 0, getWidth(), drawable.getIntrinsicHeight() * ((float) getWidth() / drawable.getIntrinsicWidth()));
                        imageReceiver.draw(canvas);
                    }
                }
            }
        };
        flickerView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundGray), PorterDuff.Mode.SRC_IN));
        flickerView.getImageReceiver().setAspectFit(true);
        addView(flickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        webView = new WebView(context) {
            private int prevScrollX, prevScrollY;

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);

                if (webViewScrollListener != null) {
                    webViewScrollListener.onWebViewScrolled(this, getScrollX() - prevScrollX, getScrollY() - prevScrollY);
                }

                prevScrollX = getScrollX();
                prevScrollY = getScrollY();
            }

            @Override
            public void setScrollX(int value) {
                super.setScrollX(value);
                prevScrollX = value;
            }

            @Override
            public void setScrollY(int value) {
                super.setScrollY(value);
                prevScrollY = value;
            }

            @Override
            public boolean onCheckIsTextEditor() {
                return true;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
            }
        };
        webView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        GeolocationPermissions.getInstance().clearAll();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uriOrig = Uri.parse(mUrl);
                Uri uriNew = Uri.parse(url);

                boolean override;
                if (isPageLoaded && (!Objects.equals(uriOrig.getHost(), uriNew.getHost()) || !Objects.equals(uriOrig.getPath(), uriNew.getPath()))) {
                    override = true;

                    if (WHITELISTED_SCHEMES.contains(uriNew.getScheme())) {
                        new AlertDialog.Builder(context, resourcesProvider)
                                .setTitle(LocaleController.getString(R.string.OpenUrlTitle))
                                .setMessage(LocaleController.formatString(R.string.OpenUrlAlert2, uriNew.toString()))
                                .setPositiveButton(LocaleController.getString(R.string.Open), (dialog, which) -> {
                                    boolean[] forceBrowser = {false};
                                    boolean internal = Browser.isInternalUri(uriNew, forceBrowser);
                                    Browser.openUrl(getContext(), uriNew, true, false);
                                    if (internal && delegate != null) {
                                        delegate.onCloseRequested();
                                    }
                                })
                                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                .show();
                    }
                } else {
                    override = false;
                }

                return override;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setPageLoaded(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            private Dialog lastPermissionsDialog;

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                Context ctx = getContext();
                if (!(ctx instanceof Activity)) {
                    return false;
                }
                Activity activity = (Activity) ctx;

                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }

                mFilePathCallback = filePathCallback;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.startActivityForResult(fileChooserParams.createIntent(), REQUEST_CODE_WEB_VIEW_FILE);
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    activity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.BotWebViewFileChooserTitle)), REQUEST_CODE_WEB_VIEW_FILE);
                }

                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (webViewProgressListener != null) {
                    webViewProgressListener.accept(newProgress / 100f);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (parentActivity == null) {
                    callback.invoke(origin, false, false);
                    return;
                }
                lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(parentActivity, resourcesProvider, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, R.raw.permission_request_location, LocaleController.formatString(R.string.BotWebViewRequestGeolocationPermission, UserObject.getUserName(botUser)), LocaleController.formatString(R.string.BotWebViewRequestGeolocationPermissionWithHint, UserObject.getUserName(botUser)), allow -> {
                    if (lastPermissionsDialog != null) {
                        lastPermissionsDialog = null;

                        if (allow) {
                            runWithPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, allowSystem -> {
                                callback.invoke(origin, allowSystem, false);
                                if (allowSystem) {
                                    hasUserPermissions = true;
                                }
                            });
                        } else {
                            callback.invoke(origin, false, false);
                        }
                    }
                });
                lastPermissionsDialog.show();
            }

            @Override
            public void onGeolocationPermissionsHidePrompt() {
                if (lastPermissionsDialog != null){
                    lastPermissionsDialog.dismiss();
                    lastPermissionsDialog = null;
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (lastPermissionsDialog != null){
                    lastPermissionsDialog.dismiss();
                    lastPermissionsDialog = null;
                }

                String[] resources = request.getResources();
                if (resources.length == 1) {
                    String resource = resources[0];

                    if (parentActivity == null) {
                        request.deny();
                        return;
                    }

                    switch (resource) {
                        case PermissionRequest.RESOURCE_AUDIO_CAPTURE: {
                            lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(parentActivity, resourcesProvider, new String[] {Manifest.permission.RECORD_AUDIO}, R.raw.permission_request_microphone, LocaleController.formatString(R.string.BotWebViewRequestMicrophonePermission, UserObject.getUserName(botUser)), LocaleController.formatString(R.string.BotWebViewRequestMicrophonePermissionWithHint, UserObject.getUserName(botUser)), allow -> {
                                if (lastPermissionsDialog != null) {
                                    lastPermissionsDialog = null;

                                    if (allow) {
                                        runWithPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, allowSystem -> {
                                            if (allowSystem) {
                                                request.grant(new String[] {resource});
                                                hasUserPermissions = true;
                                            } else {
                                                request.deny();
                                            }
                                        });
                                    } else {
                                        request.deny();
                                    }
                                }
                            });
                            lastPermissionsDialog.show();
                            break;
                        }
                        case PermissionRequest.RESOURCE_VIDEO_CAPTURE: {
                            lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(parentActivity, resourcesProvider, new String[] {Manifest.permission.CAMERA}, R.raw.permission_request_camera, LocaleController.formatString(R.string.BotWebViewRequestCameraPermission, UserObject.getUserName(botUser)), LocaleController.formatString(R.string.BotWebViewRequestCameraPermissionWithHint, UserObject.getUserName(botUser)), allow -> {
                                if (lastPermissionsDialog != null) {
                                    lastPermissionsDialog = null;

                                    if (allow) {
                                        runWithPermissions(new String[] {Manifest.permission.CAMERA}, allowSystem -> {
                                            if (allowSystem) {
                                                request.grant(new String[] {resource});
                                                hasUserPermissions = true;
                                            } else {
                                                request.deny();
                                            }
                                        });
                                    } else {
                                        request.deny();
                                    }
                                }
                            });
                            lastPermissionsDialog.show();
                            break;
                        }
                    }
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (lastPermissionsDialog != null){
                    lastPermissionsDialog.dismiss();
                    lastPermissionsDialog = null;
                }
            }
        });
        webView.setAlpha(0f);
        addView(webView);

        // We can't use javascript interface because of minSDK 16, it can be exploited because of reflection access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.addJavascriptInterface(new WebViewProxy(), "TelegramWebviewProxy");
        }
    }

    private void setPageLoaded(String url) {
        if (isPageLoaded) {
            return;
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(webView, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(flickerView, View.ALPHA, 0f)
        );
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                flickerView.setVisibility(GONE);
            }
        });
        set.start();
        mUrl = url;
        isPageLoaded = true;
    }

    public boolean hasUserPermissions() {
        return hasUserPermissions;
    }

    public void setBotUser(TLRPC.User botUser) {
        this.botUser = botUser;
    }

    private void runWithPermissions(String[] permissions, Consumer<Boolean> callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.accept(true);
        } else {
            if (checkPermissions(permissions)) {
                callback.accept(true);
            } else {
                onPermissionsRequestResultCallback = ()-> callback.accept(checkPermissions(permissions));

                if (parentActivity != null) {
                    parentActivity.requestPermissions(permissions, REQUEST_CODE_WEB_PERMISSION);
                }
            }
        }
    }

    public void setParentActivity(Activity parentActivity) {
        this.parentActivity = parentActivity;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkPermissions(String[] permissions) {
        for (String perm : permissions) {
            if (getContext().checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void restoreButtonData() {
        if (buttonData != null) {
            onEventReceived("web_app_setup_main_button", buttonData);
        }
    }

    public void onMainButtonPressed() {
        evaluateJs("window.Telegram.WebView.receiveEvent('main_button_pressed', null);");
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_WEB_PERMISSION) {
            if (onPermissionsRequestResultCallback != null) {
                onPermissionsRequestResultCallback.run();
                onPermissionsRequestResultCallback = null;
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_WEB_VIEW_FILE && mFilePathCallback != null) {
            Uri[] results = null;

            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getDataString() != null) {
                    results = new Uri[] {Uri.parse(data.getDataString())};
                }
            }

            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
    }

    public void setViewPortOffset(int viewPortOffset) {
        this.viewPortOffset = viewPortOffset;
        invalidateViewPortHeight(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateViewPortHeight(true);
    }

    public void invalidateViewPortHeight() {
        invalidateViewPortHeight(false);
    }

    public void invalidateViewPortHeight(boolean isStable) {
        invalidateViewPortHeight(isStable, false);
    }

    public void invalidateViewPortHeight(boolean isStable, boolean force) {
        if (!isPageLoaded && !force) {
            return;
        }

        if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
            ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();

            if (isStable) {
                lastExpanded = swipeContainer.getSwipeOffsetY() == -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY();
            }

            int viewPortHeight = (int) (swipeContainer.getMeasuredHeight() - swipeContainer.getOffsetY() - swipeContainer.getSwipeOffsetY() + swipeContainer.getTopActionBarOffsetY() + viewPortOffset);
            try {
                JSONObject data = new JSONObject();
                data.put("height", viewPortHeight / AndroidUtilities.density);
                data.put("is_state_stable", isStable);
                data.put("is_expanded", lastExpanded);
                evaluateJs("window.Telegram.WebView.receiveEvent('viewport_changed', " + data + ");");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == flickerView) {
            if (isFlickeringCenter) {
                canvas.save();
                View parent = (View) BotWebViewContainer.this.getParent();
                canvas.translate(0, (ActionBar.getCurrentActionBarHeight() - parent.getTranslationY()) / 2f);
            }
            boolean draw = super.drawChild(canvas, child, drawingTime);
            if (isFlickeringCenter) {
                canvas.restore();
            }

            AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
            flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, 0);
            invalidate();
            return draw;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        flickerDrawable.setParentWidth(BotWebViewContainer.this.getMeasuredWidth());
    }

    public void setWebViewProgressListener(Consumer<Float> webViewProgressListener) {
        this.webViewProgressListener = webViewProgressListener;
    }

    public WebView getWebView() {
        return webView;
    }

    public void loadFlicker(int currentAccount, long botId) {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
        if (user.username != null && Objects.equals(user.username, DURGER_KING_USERNAME)) {
            flickerView.setVisibility(VISIBLE);
            flickerView.setAlpha(1f);
            flickerView.setImageDrawable(SvgHelper.getDrawable(R.raw.durgerking_placeholder, Theme.getColor(Theme.key_windowBackgroundGray)));
            setupFlickerParams(false);
            return;
        }

        TLRPC.TL_attachMenuBot cachedBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == botId) {
                cachedBot = bot;
                break;
            }
        }

        if (cachedBot != null) {
            boolean center = false;
            TLRPC.TL_attachMenuBotIcon botIcon = MediaDataController.getPlaceholderStaticAttachMenuBotIcon(cachedBot);
            if (botIcon == null) {
                botIcon = MediaDataController.getStaticAttachMenuBotIcon(cachedBot);
                center = true;
            }
            if (botIcon != null) {
                flickerView.setVisibility(VISIBLE);
                flickerView.setAlpha(1f);
                flickerView.setImage(ImageLocation.getForDocument(botIcon.icon), null, (Drawable) null, cachedBot);
                setupFlickerParams(center);
            }
        } else {
            TLRPC.TL_messages_getAttachMenuBot req = new TLRPC.TL_messages_getAttachMenuBot();
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(botId);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_attachMenuBotsBot) {
                    TLRPC.TL_attachMenuBot bot = ((TLRPC.TL_attachMenuBotsBot) response).bot;

                    boolean center = false;
                    TLRPC.TL_attachMenuBotIcon botIcon = MediaDataController.getPlaceholderStaticAttachMenuBotIcon(bot);
                    if (botIcon == null) {
                        botIcon = MediaDataController.getStaticAttachMenuBotIcon(bot);
                        center = true;
                    }
                    if (botIcon != null) {
                        flickerView.setVisibility(VISIBLE);
                        flickerView.setAlpha(1f);
                        flickerView.setImage(ImageLocation.getForDocument(botIcon.icon), null, (Drawable) null, bot);
                        setupFlickerParams(center);
                    }
                }
            }));
        }
    }

    private void setupFlickerParams(boolean center) {
        isFlickeringCenter = center;
        FrameLayout.LayoutParams params = (LayoutParams) flickerView.getLayoutParams();
        params.gravity = center ? Gravity.CENTER : Gravity.TOP;
        if (center) {
            params.width = params.height = AndroidUtilities.dp(64);
        } else {
            params.width = LayoutParams.MATCH_PARENT;
            params.height = LayoutParams.WRAP_CONTENT;
        }

        flickerView.requestLayout();
    }

    public void loadUrl(String url) {
        isPageLoaded = false;
        hasUserPermissions = false;
        mUrl = url;
        webView.loadUrl(url);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.onActivityResultReceived);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.onRequestPermissionResultReceived);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onActivityResultReceived);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onRequestPermissionResultReceived);
    }

    public void destroyWebView() {
        webView.destroy();
    }

    @SuppressWarnings("deprecation")
    public void evaluateJs(String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(script, value -> {});
        } else {
            try {
                webView.loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                webView.loadUrl("javascript:" + URLEncoder.encode(script));
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            evaluateJs("window.Telegram.WebView.receiveEvent('theme_changed', {theme_params: " + buildThemeParams() + "});");
        } else if (id == NotificationCenter.onActivityResultReceived) {
            onActivityResult((int) args[0], (int) args[1], (Intent) args[2]);
        } else if (id == NotificationCenter.onRequestPermissionResultReceived) {
            onRequestPermissionsResult((int) args[0], (String[]) args[1], (int[]) args[2]);
        }
    }

    public void setWebViewScrollListener(WebViewScrollListener webViewScrollListener) {
        this.webViewScrollListener = webViewScrollListener;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private void onEventReceived(String eventType, String eventData) {
        switch (eventType) {
            case "web_app_close": {
                delegate.onCloseRequested();
                break;
            }
            case "web_app_data_send": {
                delegate.onSendWebViewData(eventData);
                break;
            }
            case "web_app_expand": {
                delegate.onWebAppExpand();
                break;
            }
            case "web_app_request_viewport": {
                boolean hasSwipeInProgress = getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer && ((ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent()).isSwipeInProgress();
                invalidateViewPortHeight(!hasSwipeInProgress, true);
                break;
            }
            case "web_app_ready": {
                setPageLoaded(webView.getUrl());
                break;
            }
            case "web_app_setup_main_button": {
                try {
                    JSONObject info = new JSONObject(eventData);
                    boolean isActive = info.optBoolean("is_active", false);
                    String text = info.optString("text", lastButtonText).trim();
                    boolean isVisible = info.optBoolean("is_visible", false) && !TextUtils.isEmpty(text);
                    int color = info.has("color") ? Color.parseColor(info.optString("color")) : lastButtonColor;
                    int textColor = info.has("text_color") ? Color.parseColor(info.optString("text_color")) : lastButtonTextColor;
                    boolean isProgressVisible = info.optBoolean("is_progress_visible", false) && isVisible;

                    lastButtonColor = color;
                    lastButtonTextColor = textColor;
                    lastButtonText = text;
                    buttonData = eventData;

                    delegate.onSetupMainButton(isVisible, isActive, text, color, textColor, isProgressVisible);
                } catch (JSONException | IllegalArgumentException e) {
                    FileLog.e(e);
                }
                break;
            }
        }
    }

    private String buildThemeParams() {
        try {
            JSONObject object = new JSONObject();
            object.put("bg_color", formatColor(Theme.key_windowBackgroundWhite));
            object.put("text_color", formatColor(Theme.key_windowBackgroundWhiteBlackText));
            object.put("hint_color", formatColor(Theme.key_windowBackgroundWhiteHintText));
            object.put("link_color", formatColor(Theme.key_windowBackgroundWhiteLinkText));
            object.put("button_color", formatColor(Theme.key_featuredStickers_addButton));
            object.put("button_text_color", formatColor(Theme.key_featuredStickers_buttonText));
            return object.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "{}";
        }
    }

    private String formatColor(String colorKey) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(colorKey) : Theme.getColor(colorKey);
        if (color == null) {
            color = Theme.getColor(colorKey);
        }
        return "#" + hexFixed(Color.red(color)) + hexFixed(Color.green(color)) + hexFixed(Color.blue(color));
    }

    private String hexFixed(int h) {
        String hex = Integer.toHexString(h);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    private class WebViewProxy {
        @JavascriptInterface
        public void postEvent(String eventType, String eventData) {
            AndroidUtilities.runOnUIThread(() -> onEventReceived(eventType, eventData));
        }
    }

    public interface WebViewScrollListener {
        /**
         * Called when WebView scrolls
         *
         * @param webView   WebView that scrolled
         * @param dx        Delta X
         * @param dy        Delta Y
         */
        void onWebViewScrolled(WebView webView, int dx, int dy);
    }

    public interface Delegate {
        /**
         * Called when WebView requests to close itself
         */
        void onCloseRequested();

        /**
         * Called when WebView requests to send custom data
         *
         * @param data  Custom data to send
         */
        void onSendWebViewData(String data);

        /**
         * Called when WebView requests to expand viewport
         */
        void onWebAppExpand();

        /**
         * Setups main button
         */
        void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible);
    }
}

package org.telegram.ui.web;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.readRes;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Views.LinkPreview;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.WrappedResourceProvider;
import org.telegram.ui.bots.BotBiometry;
import org.telegram.ui.bots.BotBiometrySettings;
import org.telegram.ui.bots.BotDownloads;
import org.telegram.ui.bots.BotLocation;
import org.telegram.ui.bots.BotSensors;
import org.telegram.ui.bots.BotShareSheet;
import org.telegram.ui.bots.BotStorage;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.ChatAttachAlertBotWebViewLayout;
import org.telegram.ui.bots.SetupEmojiStatusSheet;
import org.telegram.ui.bots.WebViewRequestProps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BotWebViewContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final static String DURGER_KING_USERNAME = "DurgerKingBot";
    private final static int REQUEST_CODE_WEB_VIEW_FILE = 3000, REQUEST_CODE_WEB_PERMISSION = 4000, REQUEST_CODE_QR_CAMERA_PERMISSION = 5000;
    private final static int DIALOG_SEQUENTIAL_COOLDOWN_TIME = 3000;

    private MyWebView webView;
    private String mUrl;
    private Delegate delegate;
    private WebViewScrollListener webViewScrollListener;
    private Theme.ResourcesProvider resourcesProvider;

    private TextView webViewNotAvailableText;
    private boolean webViewNotAvailable;

    private final CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();
    private SvgHelper.SvgDrawable flickerViewDrawable;
    private BackupImageView flickerView;
    private boolean isFlickeringCenter;

    private Consumer<Float> webViewProgressListener;

    private ValueCallback<Uri[]> mFilePathCallback;

    private int lastButtonColor = getColor(Theme.key_featuredStickers_addButton);
    private int lastButtonTextColor = getColor(Theme.key_featuredStickers_buttonText);
    private String lastButtonText = "";
    private String buttonData;

    private int lastSecondaryButtonColor = getColor(Theme.key_featuredStickers_addButton);
    private int lastSecondaryButtonTextColor = getColor(Theme.key_featuredStickers_buttonText);
    private String lastSecondaryButtonText = "";
    private String lastSecondaryButtonPosition = "";
    private String secondaryButtonData;

    private int currentAccount = UserConfig.selectedAccount;
    private boolean isPageLoaded;
    private boolean lastExpanded;
    private boolean isRequestingPageOpen;
    private long lastClickMs;
    private long lastPostStoryMs;

    private boolean isBackButtonVisible;
    private boolean isSettingsButtonVisible;

    private boolean hasUserPermissions;
    private TLRPC.User botUser;
    private Runnable onPermissionsRequestResultCallback;

    private Activity parentActivity;

    private boolean isViewPortByMeasureSuppressed;

    private String currentPaymentSlug;

    private AlertDialog currentDialog;
    private int dialogSequentialOpenTimes;
    private long lastDialogClosed;
    private long lastDialogCooldownTime;

    private BottomSheet cameraBottomSheet;
    private boolean hasQRPending;
    private String lastQrText;

    private BotBiometry biometry;
    private BotLocation location;
    private BotDownloads downloads;
    private BotStorage storage;
    private BotStorage secureStorage;
    public final boolean bot;

    private BotSensors sensors;

    public void showLinkCopiedBulletin() {
        BulletinFactory.of(this, resourcesProvider).createCopyLinkBulletin().show(true);
    }

    public BotWebViewContainer(
        @NonNull Context context,
        Theme.ResourcesProvider resourcesProvider,
        int backgroundColor,
        boolean isBot
    ) {
        super(context);
        this.bot = isBot;
        this.resourcesProvider = resourcesProvider;

        d("created new webview container");

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
        flickerView.setColorFilter(new PorterDuffColorFilter(flickerViewColor = getColor(Theme.key_bot_loadingIcon), PorterDuff.Mode.SRC_IN));
        flickerView.getImageReceiver().setAspectFit(true);
        addView(flickerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        webViewNotAvailableText = new TextView(context);
        webViewNotAvailableText.setText(getString(R.string.BotWebViewNotAvailablePlaceholder));
        webViewNotAvailableText.setTextColor(getColor(Theme.key_windowBackgroundWhiteGrayText));
        webViewNotAvailableText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        webViewNotAvailableText.setGravity(Gravity.CENTER);
        webViewNotAvailableText.setVisibility(GONE);
        int padding = dp(16);
        webViewNotAvailableText.setPadding(padding, padding, padding, padding);
        addView(webViewNotAvailableText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        setFocusable(false);
    }
    public void setViewPortByMeasureSuppressed(boolean viewPortByMeasureSuppressed) {
        isViewPortByMeasureSuppressed = viewPortByMeasureSuppressed;
    }

    private int flickerViewColor;
    private boolean flickerViewColorOverriden;
    public void setFlickerViewColor(int bgColor) {
        final boolean light = AndroidUtilities.computePerceivedBrightness(bgColor) > .7f;
        final int color;
        if (light) {
            color = Theme.adaptHSV(bgColor, 0, -.15f);
        } else {
            color = Theme.adaptHSV(bgColor, +.025f, +.15f);
        }
        if (flickerViewColor == color) return;
        flickerView.setColorFilter(new PorterDuffColorFilter(flickerViewColor = color, PorterDuff.Mode.SRC_IN));
        if (flickerViewDrawable != null) {
            flickerViewDrawable.setColor(flickerViewColor);
            flickerViewDrawable.setupGradient(Theme.key_bot_loadingIcon, resourcesProvider, 1.0f, false);
        }
        flickerViewColorOverriden = true;
        flickerView.invalidate();
        invalidate();
    }

    public void checkCreateWebView() {
        if (webView == null && !webViewNotAvailable) {
            try {
                setupWebView(null);
            } catch (Throwable t) {
                FileLog.e(t);

                flickerView.setVisibility(GONE);
                webViewNotAvailable = true;
                webViewNotAvailableText.setVisibility(VISIBLE);
                if (webView != null) {
                    removeView(webView);
                }
            }
        }
    }

    public void replaceWebView(int currentAccount, MyWebView webView, Object proxy) {
        this.currentAccount = currentAccount;
        setupWebView(webView, proxy);
        if (bot) {
            notifyEvent("visibility_changed", obj("is_visible", true));
        }
    }

    private void setupWebView(MyWebView replaceWith) {
        setupWebView(replaceWith, null);
    }

    private BotWebViewProxy botWebViewProxy;
    public BotWebViewProxy getBotProxy() {
        return botWebViewProxy;
    }
    private WebViewProxy webViewProxy;
    public WebViewProxy getProxy() {
        return webViewProxy;
    }

    public static boolean firstWebView = true;

    private MyWebView opener;
    public void setOpener(MyWebView webView) {
        this.opener = webView;
        if (!bot && this.webView != null) {
            this.webView.opener = webView;
        }
    }

    private static String capitalizeFirst(String str) {
        if (str == null) return "";
        if (str.length() <= 1) return str.toUpperCase();
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView(MyWebView replaceWith, Object proxy) {
        if (webView != null) {
            webView.destroy();
            removeView(webView);
        }
        if (replaceWith != null) {
            AndroidUtilities.removeFromParent(replaceWith);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && SharedConfig.debugWebView) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        webView = replaceWith == null ? new MyWebView(getContext(), bot, bot ? botUser == null ? 0 : botUser.id : 0) : replaceWith;
        if (!bot) {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webView, true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush();
            }
            webView.opener = opener;
        } else {
            webView.setBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
        }
        if (!MessagesController.getInstance(currentAccount).disableBotFullscreenBlur) {
            webView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        webView.setContainers(this, webViewScrollListener);
        webView.setCloseListener(onCloseListener);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        if (!bot) {
            settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setSaveFormData(true);
            settings.setSavePassword(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.setSafeBrowsingEnabled(true);
            }
        }

        try {
            String useragent = settings.getUserAgentString();
            useragent = useragent.replace("; wv)", ")");
            useragent = useragent.replaceAll("\\(Linux; Android.+;[^)]+\\)", "(Linux; Android " + Build.VERSION.RELEASE + "; K)");
            useragent = useragent.replaceAll("Version/[\\d\\.]+ ", "");
            if (bot) {
                final PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                final int perf = SharedConfig.getDevicePerformanceClass();
                final String perfName = perf == SharedConfig.PERFORMANCE_CLASS_LOW ? "LOW" : perf == SharedConfig.PERFORMANCE_CLASS_AVERAGE ? "AVERAGE" : "HIGH";
                useragent += " Telegram-Android/" + packageInfo.versionName + " (" + capitalizeFirst(Build.MANUFACTURER) + " " + Build.MODEL + "; Android " + Build.VERSION.RELEASE + "; SDK " + Build.VERSION.SDK_INT + "; " + perfName + ")";
            }
            settings.setUserAgentString(useragent);
        } catch (Exception e) {
            FileLog.e(e);
        }

        // Hackfix text on some Xiaomi devices
        settings.setTextSize(WebSettings.TextSize.NORMAL);

        File databaseStorage = new File(ApplicationLoader.getFilesDirFixed(), "webview_database");
        if (databaseStorage.exists() && databaseStorage.isDirectory() || databaseStorage.mkdirs()) {
            settings.setDatabasePath(databaseStorage.getAbsolutePath());
        }
        GeolocationPermissions.getInstance().clearAll();

        webView.setVerticalScrollBarEnabled(false);
        if (replaceWith == null && bot) {
            webView.setAlpha(0f);
        }
        addView(webView);

        // We can't use javascript interface because of minSDK 16, it can be exploited because of reflection access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (bot) {
                if (proxy instanceof BotWebViewProxy) {
                    botWebViewProxy = (BotWebViewProxy) proxy;
                }
                if (botWebViewProxy == null) {
                    botWebViewProxy = new BotWebViewProxy(this);
                    webView.addJavascriptInterface(botWebViewProxy, "TelegramWebviewProxy");
                } else if (replaceWith == null) {
                    webView.addJavascriptInterface(botWebViewProxy, "TelegramWebviewProxy");
                }
                botWebViewProxy.setContainer(this);
            } else {
                if (proxy instanceof WebViewProxy) {
                    webViewProxy = (WebViewProxy) proxy;
                }
                if (webViewProxy == null) {
                    webViewProxy = new WebViewProxy(webView, this);
                    webView.addJavascriptInterface(webViewProxy, "TelegramWebview");
                } else if (replaceWith == null) {
                    webView.addJavascriptInterface(webViewProxy, "TelegramWebview");
                }
                webViewProxy.setContainer(this);
            }
        }

        onWebViewCreated(webView);
        firstWebView = false;
    }

    private void onOpenUri(Uri uri) {
        onOpenUri(uri, null, !bot, false, false);
    }

    private void onOpenUri(Uri uri, String browser, boolean tryInstantView, boolean suppressPopup, boolean forceRequest) {
        if (isRequestingPageOpen || System.currentTimeMillis() - lastClickMs > 10_000 && suppressPopup) {
            return;
        }

        lastClickMs = 0;
        boolean[] forceBrowser = {false};
        boolean internal = Browser.isInternalUri(uri, forceBrowser);

        if (internal && !forceBrowser[0] && delegate != null) {
            setKeyboardFocusable(false);
        }

        Browser.openUrl(getContext(), uri, true, tryInstantView, false, null, browser, false, true, forceRequest);
    }

    private boolean keyboardFocusable;
    private boolean wasFocusable;
    private void updateKeyboardFocusable() {
        final boolean focusable = keyboardFocusable && isPageLoaded && false;
        if (wasFocusable != focusable) {
            if (!focusable) {
                setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                BotWebViewContainer.this.setFocusable(false);
//                webView.setFocusable(false);
                if (webView != null) {
                    webView.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                    webView.clearFocus();
                }
                AndroidUtilities.hideKeyboard(this);
            } else {
                setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
                BotWebViewContainer.this.setFocusable(true);
//                webView.setFocusable(true);
                if (webView != null) {
                    webView.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
                }
            }
        }
        wasFocusable = focusable;
    }

    public void setKeyboardFocusable(boolean focusable) {
        keyboardFocusable = focusable;
        updateKeyboardFocusable();
    }

    public static int getMainButtonRippleColor(int buttonColor) {
        return ColorUtils.calculateLuminance(buttonColor) >= 0.3f ? 0x12000000 : 0x16FFFFFF;
    }

    public static Drawable getMainButtonRippleDrawable(int buttonColor) {
        return Theme.createSelectorWithBackgroundDrawable(buttonColor, getMainButtonRippleColor(buttonColor));
    }

    public void updateFlickerBackgroundColor(int backgroundColor) {
        flickerDrawable.setColors(backgroundColor, 0x99, 0xCC);
    }

    protected void onTitleChanged(String title) {

    }
    protected void onFaviconChanged(Bitmap favicon) {

    }
    protected void onURLChanged(String url, boolean first, boolean last) {

    }

    /**
     * @return If this press was consumed
     */
    public boolean onBackPressed() {
        if (webView == null) {
            return false;
        }
        if (isBackButtonVisible) {
            notifyEvent("back_button_pressed", null);
            return true;
        }
        return false;
    }

    public void setPageLoaded(String url, boolean animated) {
        onURLChanged(webView != null && webView.dangerousUrl ? webView.urlFallback : url, !(webView != null && webView.canGoBack()), !(webView != null && webView.canGoForward()));

        if (webView != null) {
            webView.isPageLoaded = true;
            updateKeyboardFocusable();
        }

        if (isPageLoaded) {
            d("setPageLoaded: already loaded");
            return;
        }

        if (animated && webView != null && flickerView != null) {
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
        } else {
            if (webView != null) {
                webView.setAlpha(1f);
            }
            if (flickerView != null) {
                flickerView.setAlpha(0f);
                flickerView.setVisibility(GONE);
            }
        }
        mUrl = url;
        d("setPageLoaded: isPageLoaded = true!");
        isPageLoaded = true;
        updateKeyboardFocusable();
        delegate.onWebAppReady();
    }

    protected void onErrorShown(boolean shown, int errorCode, String description) {

    }

    protected void onDangerousTriggered(DangerousWebWarning warning) {

    }

    public void setState(boolean loaded, String url) {
        d("setState(" + loaded + ", " + url + ")");
        isPageLoaded = loaded;
        mUrl = url;
        updateKeyboardFocusable();
    }

    public void setIsBackButtonVisible(boolean visible) {
        isBackButtonVisible = visible;
    }

    public String getUrlLoaded() {
        return mUrl;
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

    public boolean isPageLoaded() {
        return isPageLoaded;
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
        try {
            if (buttonData != null) {
                onEventReceived(botWebViewProxy, "web_app_setup_main_button", buttonData);
            }
            if (secondaryButtonData != null) {
                onEventReceived(botWebViewProxy, "web_app_setup_secondary_button", secondaryButtonData);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void onInvoiceStatusUpdate(String slug, String status) {
        onInvoiceStatusUpdate(slug, status, false);
    }

    public void onInvoiceStatusUpdate(String slug, String status, boolean ignoreCurrentCheck) {
        try {
            JSONObject data = new JSONObject();
            data.put("slug", slug);
            data.put("status", status);
            notifyEvent("invoice_closed", data);
            FileLog.d("invoice_closed " + data);

            if (!ignoreCurrentCheck && Objects.equals(currentPaymentSlug, slug)) {
                currentPaymentSlug = null;
            }
        } catch (JSONException e) {
            FileLog.e(e);
        }
    }

    public void onSettingsButtonPressed() {
        lastClickMs = System.currentTimeMillis();
        notifyEvent("settings_button_pressed", null);
    }

    public void onMainButtonPressed() {
        lastClickMs = System.currentTimeMillis();
        notifyEvent("main_button_pressed", null);
    }

    public void onSecondaryButtonPressed() {
        lastClickMs = System.currentTimeMillis();
        notifyEvent("secondary_button_pressed", null);
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!isViewPortByMeasureSuppressed) {
            invalidateViewPortHeight(true);
        }
    }

    public void invalidateViewPortHeight() {
        invalidateViewPortHeight(false);
    }

    public void invalidateViewPortHeight(boolean isStable) {
        invalidateViewPortHeight(isStable, false);
    }

    private int lastViewportHeightReported;
    private boolean lastViewportStateStable;
    private boolean lastViewportIsExpanded;
    private float viewPortHeightOffset;

    public int getMinHeight() {
        if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
            ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();
            if (swipeContainer.isFullSize()) {
                return (int) (swipeContainer.getMeasuredHeight() - swipeContainer.getOffsetY() /*- swipeContainer.getTopActionBarOffsetY()*/ + viewPortHeightOffset);
            }
        }
        return 0;
    }

    public void setViewPortHeightOffset(float viewPortHeightOffset) {
        this.viewPortHeightOffset = viewPortHeightOffset;
    }

    public void invalidateViewPortHeight(boolean isStable, boolean force) {
        invalidate();
        if (!isPageLoaded && !force || !bot) {
            return;
        }

        if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
            ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();

            if (isStable) {
                lastExpanded = swipeContainer.getSwipeOffsetY() == -swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY();
            }

            final int viewPortHeight = Math.max(getMinHeight(), (int) (swipeContainer.getMeasuredHeight() - swipeContainer.getOffsetY() - swipeContainer.getSwipeOffsetY() + swipeContainer.getTopActionBarOffsetY() + viewPortHeightOffset));
            if (
                force ||
                viewPortHeight != lastViewportHeightReported ||
                lastViewportStateStable != isStable ||
                lastViewportIsExpanded != lastExpanded
            ) {
                lastViewportHeightReported = viewPortHeight;
                lastViewportStateStable = isStable;
                lastViewportIsExpanded = lastExpanded;

                StringBuilder sb = new StringBuilder();
                sb.append("{height:").append(viewPortHeight / AndroidUtilities.density).append(",");
                sb.append("is_state_stable:").append(isStable).append(",");
                sb.append("is_expanded:").append(lastExpanded).append("}");
                notifyEvent_fast("viewport_changed", sb.toString());
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

            if (!isFlickeringCenter) {
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, 0, this);
                invalidate();
            }
            return draw;
        }
        if (child == webViewNotAvailableText) {
            canvas.save();
            View parent = (View) BotWebViewContainer.this.getParent();
            canvas.translate(0, (ActionBar.getCurrentActionBarHeight() - parent.getTranslationY()) / 2f);
            boolean draw = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return draw;
        }
        if (child == webView && (AndroidUtilities.makingGlobalBlurBitmap || getLayerType() == LAYER_TYPE_HARDWARE && !canvas.isHardwareAccelerated())) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private int forceHeight = -1;
    public void setForceHeight(int height) {
        if (this.forceHeight == height) return;
        this.forceHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (forceHeight >= 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(forceHeight, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        flickerDrawable.setParentWidth(BotWebViewContainer.this.getMeasuredWidth());
    }

    public void setWebViewProgressListener(Consumer<Float> webViewProgressListener) {
        this.webViewProgressListener = webViewProgressListener;
    }

    public MyWebView getWebView() {
        return webView;
    }

    public void loadFlickerAndSettingsItem(int currentAccount, long botId, ActionBarMenuSubItem settingsItem) {
        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
        final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(botId);
        String username = UserObject.getPublicUsername(user);
        if (username != null && Objects.equals(username, DURGER_KING_USERNAME)) {
            flickerView.setVisibility(VISIBLE);
            flickerView.setAlpha(1f);
            flickerView.setImage(null, null, SvgHelper.getDrawable(R.raw.durgerking_placeholder, getColor(Theme.key_windowBackgroundGray)));
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
        } else if (userFull != null && userFull.bot_info != null && userFull.bot_info.app_settings != null && userFull.bot_info.app_settings.placeholder_svg_path != null) {
            flickerView.setVisibility(VISIBLE);
            flickerView.setAlpha(1f);
            flickerViewDrawable = SvgHelper.getDrawableByPath(userFull.bot_info.app_settings.placeholder_svg_path, 512, 512);
            if (flickerViewDrawable != null) {
                flickerViewDrawable.setColor(flickerViewColor);
                flickerViewDrawable.setupGradient(Theme.key_bot_loadingIcon, resourcesProvider, 1.0f, false);
            }
            flickerView.setImage(null, null, flickerViewDrawable);
            setupFlickerParams(true);
        } else {
            Path path = new Path();
            final float c = 256, sz = 133.69f, hp = 31.29f / 2.0f;
            AndroidUtilities.rectTmp.set(c - sz - hp, c - sz - hp, c - hp, c - hp);
            path.addRoundRect(AndroidUtilities.rectTmp, 18, 18, Path.Direction.CW);
            AndroidUtilities.rectTmp.set(c + hp, c - sz - hp, c + sz + hp, c - hp);
            path.addRoundRect(AndroidUtilities.rectTmp, 18, 18, Path.Direction.CW);
            AndroidUtilities.rectTmp.set(c - sz - hp, c + hp, c - hp, c + sz + hp);
            path.addRoundRect(AndroidUtilities.rectTmp, 18, 18, Path.Direction.CW);
            AndroidUtilities.rectTmp.set(c + hp, c + hp, c + sz + hp, c + sz + hp);
            path.addRoundRect(AndroidUtilities.rectTmp, 18, 18, Path.Direction.CW);
            flickerView.setVisibility(VISIBLE);
            flickerView.setAlpha(1f);
            flickerViewDrawable = SvgHelper.getDrawableByPath(path, 512, 512);
            if (flickerViewDrawable != null) {
                flickerViewDrawable.setColor(flickerViewColor);
                flickerViewDrawable.setupGradient(Theme.key_bot_loadingIcon, resourcesProvider, 1.0f, false);
            }
            flickerView.setImage(null, null, flickerViewDrawable);
            setupFlickerParams(true);
        }
    }

    private void setupFlickerParams(boolean center) {
        isFlickeringCenter = center;
        FrameLayout.LayoutParams params = (LayoutParams) flickerView.getLayoutParams();
        params.gravity = center ? Gravity.CENTER : Gravity.TOP;
        if (center) {
            params.width = params.height = dp(100);
        } else {
            params.width = LayoutParams.MATCH_PARENT;
            params.height = LayoutParams.WRAP_CONTENT;
        }

        flickerView.requestLayout();
    }

    public void reload() {
        NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            if (isSettingsButtonVisible) {
                isSettingsButtonVisible = false;
                if (delegate != null) {
                    delegate.onSetSettingsButtonVisible(isSettingsButtonVisible);
                }
            }

            checkCreateWebView();
            isPageLoaded = false;
            lastClickMs = 0;
            hasUserPermissions = false;
            if (webView != null) {
                webView.onResume();
                webView.reload();
            }
            updateKeyboardFocusable();

            if (sensors != null) {
                sensors.stopAll();
            }
        });
    }

    public void loadUrl(int currentAccount, String url) {
        this.currentAccount = currentAccount;
        NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            isPageLoaded = false;
            lastClickMs = 0;
            hasUserPermissions = false;
            mUrl = url;
            checkCreateWebView();
            if (webView != null) {
                webView.onResume();
                webView.loadUrl(url);
            }
            updateKeyboardFocusable();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        d("attached");

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.onActivityResultReceived);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.onRequestPermissionResultReceived);

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
                    ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();
                    return (int) (swipeContainer.getOffsetY() + swipeContainer.getSwipeOffsetY() - swipeContainer.getTopActionBarOffsetY());
                }
                return 0;
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        d("detached");

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onActivityResultReceived);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onRequestPermissionResultReceived);

        Bulletin.removeDelegate(this);
    }

    private boolean preserving;
    public void preserveWebView() {
        d("preserveWebView");
        preserving = true;
        if (bot) {
            notifyEvent("visibility_changed", obj("is_visible", false));
        }
    }

    public void destroyWebView() {
        d("destroyWebView preserving=" + preserving);
        if (webView != null) {
            if (webView.getParent() != null) {
                removeView(webView);
            }
            if (!preserving) {
                webView.destroy();
                onWebViewDestroyed(webView);
            }
            isPageLoaded = false;
            updateKeyboardFocusable();

            if (biometry != null) {
                biometry = null;
            }
            if (storage != null) {
                storage = null;
            }
            if (secureStorage != null) {
                secureStorage = null;
            }
            if (location != null) {
                location.unlisten(this.notifyLocationChecked);
                location = null;
            }
        }
    }

    public void resetWebView() {
        webView = null;
    }

    public boolean isBackButtonVisible() {
        return isBackButtonVisible;
    }

    public void evaluateJs(String script) {
        evaluateJs(script, true);
    }

    @SuppressWarnings("deprecation")
    public void evaluateJs(String script, boolean create) {
        NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            if (create) {
                checkCreateWebView();
            }
            if (webView == null) {
                return;
            }
            webView.evaluateJS(script);
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            if (webView != null) {
                webView.setBackgroundColor(getColor(Theme.key_windowBackgroundWhite));
            }
            if (!flickerViewColorOverriden) {
                flickerView.setColorFilter(new PorterDuffColorFilter(flickerViewColor = getColor(Theme.key_bot_loadingIcon), PorterDuff.Mode.SRC_IN));
                if (flickerViewDrawable != null) {
                    flickerViewDrawable.setColor(flickerViewColor);
                    flickerViewDrawable.setupGradient(Theme.key_bot_loadingIcon, resourcesProvider, 1.0f, false);
                }
                flickerView.invalidate();
            }
            notifyThemeChanged();
        } else if (id == NotificationCenter.onActivityResultReceived) {
            onActivityResult((int) args[0], (int) args[1], (Intent) args[2]);
        } else if (id == NotificationCenter.onRequestPermissionResultReceived) {
            onRequestPermissionsResult((int) args[0], (String[]) args[1], (int[]) args[2]);
        }
    }

    public void notifyThemeChanged() {
        notifyEvent("theme_changed", buildThemeParams());
    }

    private void notifyEvent(String event, JSONObject eventData) {
        d("notifyEvent " + event);
        evaluateJs("window.Telegram.WebView.receiveEvent('" + event + "', " + eventData + ");", false);
    }

    private void notifyEvent_fast(String event, String eventData) {
        StringBuilder sb = new StringBuilder();
        sb.append("window.Telegram.WebView.receiveEvent('");
        sb.append(event);
        sb.append("', ");
        sb.append(eventData);
        sb.append(");");
        evaluateJs(sb.toString(), false);
    }

    private static void notifyEvent(int currentAccount, MyWebView webView, String event, JSONObject eventData) {
        if (webView == null) return;
        NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            webView.evaluateJS("window.Telegram.WebView.receiveEvent('" + event + "', " + eventData + ");");
        });
    }

    public void setWebViewScrollListener(WebViewScrollListener webViewScrollListener) {
        this.webViewScrollListener = webViewScrollListener;
        if (webView != null) {
            webView.setContainers(this, webViewScrollListener);
        }
    }

    private Runnable onCloseListener;
    public void setOnCloseRequestedListener(Runnable listener) {
        onCloseListener = listener;
        if (webView != null) {
            webView.setCloseListener(listener);
        }
    }

    private boolean wasOpenedByLinkIntent;
    public void setWasOpenedByLinkIntent(boolean value) {
        wasOpenedByLinkIntent = value;
    }

    private WebViewRequestProps wasOpenedByBot;
    public void setWasOpenedByBot(WebViewRequestProps props) {
        wasOpenedByBot = props;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private void onWebEventReceived(String type, String data) {
        if (bot) return;
        if (delegate == null) return;
        d("onWebEventReceived " + type + " " + data);
        switch (type) {
            case "actionBarColor":
            case "navigationBarColor": {
                try {
                    final JSONArray jsonArray = new JSONArray(data);
                    final boolean isActionBarColor = TextUtils.equals(type, "actionBarColor");
                    final int color = Color.argb(
                        (int) (Math.round(jsonArray.optDouble(3, 1) * 255)),
                        (int) (Math.round(jsonArray.optDouble(0))),
                        (int) (Math.round(jsonArray.optDouble(1))),
                        (int) (Math.round(jsonArray.optDouble(2)))
                    );
                    if (webView != null) {
                        if (isActionBarColor) {
                            webView.lastActionBarColorGot = true;
                            webView.lastActionBarColor = color;
                        } else {
                            webView.lastBackgroundColorGot = true;
                            webView.lastBackgroundColor = color;
                        }
                        webView.saveHistory();
                    }
                    delegate.onWebAppBackgroundChanged(isActionBarColor, color);
                } catch (Exception e) {}
                break;
            }
            case "allowScroll": {
                boolean x = true, y = true;
                try {
                    JSONArray jsonArray = new JSONArray(data);
                    x = jsonArray.optBoolean(0, true);
                    y = jsonArray.optBoolean(1, true);
                } catch (Exception e) {}
//                d("allowScroll " + x + " " + y);
                if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
                    ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();
                    swipeContainer.allowThisScroll(x, y);
                }
                break;
            }
            case "siteName": {
                d("siteName " + data);
                if (webView != null) {
                    webView.lastSiteName = data;
                    webView.saveHistory();
                }
                break;
            }
        }
    }

    private void onEventReceived(BotWebViewProxy proxy, String eventType, String eventData) {
        if (!bot) {
            return;
        }
        if (webView == null || delegate == null) {
            d("onEventReceived " + eventType + ": no webview or delegate!");
            return;
        }
        d("onEventReceived " + eventType);
        switch (eventType) {
            case "web_app_allow_scroll": {
                boolean x = true, y = true;
                try {
                    JSONArray jsonArray = new JSONArray(eventData);
                    x = jsonArray.optBoolean(0, true);
                    y = jsonArray.optBoolean(1, true);
                } catch (Exception e) {}
                d("allowScroll " + x + " " + y);
                if (getParent() instanceof ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) {
                    ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer = (ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer) getParent();
                    swipeContainer.allowThisScroll(x, y);
                }
                break;
            }
            case "web_app_close": {
                boolean return_back = false;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    return_back = jsonObject.optBoolean("return_back");
                } catch (Exception e) {
                    FileLog.e(e);
                }

                delegate.onCloseRequested(null);
                if (return_back) {
                    if (wasOpenedByLinkIntent && LaunchActivity.instance != null) {
                        Activity activity = AndroidUtilities.findActivity(getContext());
                        if (activity == null) activity = LaunchActivity.instance;
                        if (activity != null && !activity.isFinishing()) {
                            activity.moveTaskToBack(true);
                        }
                    } else if (wasOpenedByBot != null && LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null) {
                        final BottomSheetTabs bottomSheetTabs = LaunchActivity.instance.getBottomSheetTabs();
                        final ArrayList<BottomSheetTabs.WebTabData> allTabs = bottomSheetTabs.getTabs();
                        BottomSheetTabs.WebTabData openedByTab = null;
                        for (int i = 0; i < allTabs.size(); ++i) {
                            BottomSheetTabs.WebTabData tab = allTabs.get(i);
                            if (wasOpenedByBot.equals(tab.props) && tab.webView != webView) {
                                openedByTab = tab;
                                break;
                            }
                        }
                        if (openedByTab != null) {
                            bottomSheetTabs.openTab(openedByTab);
                        }
                    }
                }
                break;
            }
            case "web_app_switch_inline_query": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    List<String> types = new ArrayList<>();
                    JSONArray arr = jsonObject.getJSONArray("chat_types");
                    for (int i = 0; i < arr.length(); i++) {
                        types.add(arr.getString(i));
                    }

                    delegate.onWebAppSwitchInlineQuery(botUser, jsonObject.getString("query"), types);
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_read_text_from_clipboard": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    String reqId = jsonObject.getString("req_id");
                    if (!delegate.isClipboardAvailable() || System.currentTimeMillis() - lastClickMs > 10_000) {
                        notifyEvent("clipboard_text_received", new JSONObject().put("req_id", reqId));
                        break;
                    }

                    ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence text = clipboardManager.getText();
                    String data = text != null ? text.toString() : "";
                    notifyEvent("clipboard_text_received", new JSONObject().put("req_id", reqId).put("data", data));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_close_scan_qr_popup": {
                if (hasQRPending && cameraBottomSheet != null) {
                    cameraBottomSheet.dismiss();
                }
                break;
            }
            case "web_app_open_scan_qr_popup": {
                try {
                    if (hasQRPending || parentActivity == null) {
                        break;
                    }

                    JSONObject jsonObject = new JSONObject(eventData);
                    lastQrText = jsonObject.optString("text");
                    hasQRPending = true;

                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                            @Override
                            public void didReceivedNotification(int id, int account, Object... args) {
                                if (id == NotificationCenter.onRequestPermissionResultReceived) {
                                    int requestCode = (int) args[0];
                                    // String[] permissions = (String[]) args[1];
                                    int[] grantResults = (int[]) args[2];

                                    if (requestCode == REQUEST_CODE_QR_CAMERA_PERMISSION) {
                                        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onRequestPermissionResultReceived);

                                        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                            openQrScanActivity();
                                        } else {
                                            notifyEvent("scan_qr_popup_closed", new JSONObject());
                                        }
                                    }
                                }
                            }
                        }, NotificationCenter.onRequestPermissionResultReceived);
                        parentActivity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_QR_CAMERA_PERMISSION);
                        return;
                    }

                    openQrScanActivity();
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_open_popup": {
                try {
                    if (currentDialog != null) {
                        break;
                    }

                    if (System.currentTimeMillis() - lastDialogClosed <= 150) {
                        dialogSequentialOpenTimes++;

                        if (dialogSequentialOpenTimes >= 3) {
                            dialogSequentialOpenTimes = 0;
                            lastDialogCooldownTime = System.currentTimeMillis();
                            break;
                        }
                    }

                    if (System.currentTimeMillis() - lastDialogCooldownTime <= DIALOG_SEQUENTIAL_COOLDOWN_TIME) {
                        break;
                    }

                    JSONObject jsonObject = new JSONObject(eventData);
                    String title = jsonObject.optString("title", null);
                    String message = jsonObject.getString("message");
                    JSONArray buttons = jsonObject.getJSONArray("buttons");

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                            .setTitle(title)
                            .setMessage(message);

                    List<PopupButton> buttonsList = new ArrayList<>();
                    for (int i = 0; i < buttons.length(); i++) {
                        buttonsList.add(new PopupButton(buttons.getJSONObject(i)));
                    }
                    if (buttonsList.size() > 3) {
                        break;
                    }

                    AtomicBoolean notifiedClose = new AtomicBoolean();
                    if (buttonsList.size() >= 1) {
                        PopupButton btn = buttonsList.get(0);
                        builder.setPositiveButton(btn.text, (dialog, which) -> {
                            dialog.dismiss();
                            try {
                                lastClickMs = System.currentTimeMillis();
                                notifyEvent("popup_closed", new JSONObject().put("button_id", btn.id));
                                notifiedClose.set(true);
                            } catch (JSONException e) {
                                FileLog.e(e);
                            }
                        });
                    }

                    if (buttonsList.size() >= 2) {
                        PopupButton btn = buttonsList.get(1);
                        builder.setNegativeButton(btn.text, (dialog, which) -> {
                            dialog.dismiss();
                            try {
                                lastClickMs = System.currentTimeMillis();
                                notifyEvent("popup_closed", new JSONObject().put("button_id", btn.id));
                                notifiedClose.set(true);
                            } catch (JSONException e) {
                                FileLog.e(e);
                            }
                        });
                    }

                    if (buttonsList.size() == 3) {
                        PopupButton btn = buttonsList.get(2);
                        builder.setNeutralButton(btn.text, (dialog, which) -> {
                            dialog.dismiss();
                            try {
                                lastClickMs = System.currentTimeMillis();
                                notifyEvent("popup_closed", new JSONObject().put("button_id", btn.id));
                                notifiedClose.set(true);
                            } catch (JSONException e) {
                                FileLog.e(e);
                            }
                        });
                    }
                    builder.setOnDismissListener(dialog -> {
                        if (!notifiedClose.get()) {
                            notifyEvent("popup_closed", new JSONObject());
                        }
                        currentDialog = null;
                        lastDialogClosed = System.currentTimeMillis();
                    });

                    currentDialog = builder.show();
                    if (buttonsList.size() >= 1) {
                        PopupButton btn = buttonsList.get(0);
                        if (btn.textColorKey >= 0) {
                            TextView textView = (TextView) currentDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                            if (textView != null) {
                                textView.setTextColor(getColor(btn.textColorKey));
                            }
                        }
                    }
                    if (buttonsList.size() >= 2) {
                        PopupButton btn = buttonsList.get(1);
                        if (btn.textColorKey >= 0) {
                            TextView textView = (TextView) currentDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                            if (textView != null) {
                                textView.setTextColor(getColor(btn.textColorKey));
                            }
                        }
                    }
                    if (buttonsList.size() == 3) {
                        PopupButton btn = buttonsList.get(2);
                        if (btn.textColorKey >= 0) {
                            TextView textView = (TextView) currentDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                            if (textView != null) {
                                textView.setTextColor(getColor(btn.textColorKey));
                            }
                        }
                    }
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_setup_closing_behavior": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    delegate.onWebAppSetupClosingBehavior(jsonObject.optBoolean("need_confirmation"));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_setup_swipe_behavior": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    delegate.onWebAppSwipingBehavior(jsonObject.optBoolean("allow_vertical_swipe"));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_set_background_color": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    delegate.onWebAppSetBackgroundColor(Color.parseColor(jsonObject.optString("color", "#ffffff")) | 0xFF000000);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_set_header_color": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    String overrideColorString = jsonObject.optString("color", null);
                    if (!TextUtils.isEmpty(overrideColorString)) {
                        int color = Color.parseColor(overrideColorString);
                        if (color != 0) {
                            delegate.onWebAppSetActionBarColor(-1, color, true);
                        }
                    } else {
                        String key = jsonObject.optString("color_key");
                        int themeKey = -1;
                        switch (key) {
                            case "bg_color": {
                                themeKey = Theme.key_windowBackgroundWhite;
                                break;
                            }
                            case "secondary_bg_color": {
                                themeKey = Theme.key_windowBackgroundGray;
                                break;
                            }
                        }
                        if (themeKey >= 0) {
                            delegate.onWebAppSetActionBarColor(themeKey, Theme.getColor(themeKey, resourcesProvider), false);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_set_bottom_bar_color": {
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    String colorString = jsonObject.optString("color", null);
                    int color;
                    if (TextUtils.isEmpty(colorString)) {
                        color = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
                    } else {
                        color = Color.parseColor(colorString);
                    }
                    if (delegate != null) {
                        delegate.onWebAppSetNavigationBarColor(color);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_data_send": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    delegate.onSendWebViewData(jsonData.optString("data"));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_trigger_haptic_feedback": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    String type = jsonData.optString("type");

                    BotWebViewVibrationEffect vibrationEffect = null;
                    switch (type) {
                        case "impact": {
                            switch (jsonData.optString("impact_style")) {
                                case "light": {
                                    vibrationEffect = BotWebViewVibrationEffect.IMPACT_LIGHT;
                                    break;
                                }
                                case "medium": {
                                    vibrationEffect = BotWebViewVibrationEffect.IMPACT_MEDIUM;
                                    break;
                                }
                                case "heavy": {
                                    vibrationEffect = BotWebViewVibrationEffect.IMPACT_HEAVY;
                                    break;
                                }
                                case "rigid": {
                                    vibrationEffect = BotWebViewVibrationEffect.IMPACT_RIGID;
                                    break;
                                }
                                case "soft": {
                                    vibrationEffect = BotWebViewVibrationEffect.IMPACT_SOFT;
                                    break;
                                }
                            }
                            break;
                        }
                        case "notification": {
                            switch (jsonData.optString("notification_type")) {
                                case "error": {
                                    vibrationEffect = BotWebViewVibrationEffect.NOTIFICATION_ERROR;
                                    break;
                                }
                                case "success": {
                                    vibrationEffect = BotWebViewVibrationEffect.NOTIFICATION_SUCCESS;
                                    break;
                                }
                                case "warning": {
                                    vibrationEffect = BotWebViewVibrationEffect.NOTIFICATION_WARNING;
                                    break;
                                }
                            }
                            break;
                        }
                        case "selection_change": {
                            vibrationEffect = BotWebViewVibrationEffect.SELECTION_CHANGE;
                            break;
                        }
                    }
                    if (vibrationEffect != null) {
                        vibrationEffect.vibrate();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_open_link": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    Uri uri = Uri.parse(jsonData.optString("url"));
                    String browser = jsonData.optString("try_browser");
                    if (MessagesController.getInstance(currentAccount).webAppAllowedProtocols != null &&
                        MessagesController.getInstance(currentAccount).webAppAllowedProtocols.contains(uri.getScheme())) {
                        onOpenUri(uri, browser, jsonData.optBoolean("try_instant_view"), true, false);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_open_tg_link": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    String pathFull = jsonData.optString("path_full");
                    boolean force_request = jsonData.optBoolean("force_request", false);
                    if (pathFull.startsWith("/")) {
                        pathFull = pathFull.substring(1);
                    }
                    onOpenUri(Uri.parse("https://t.me/" + pathFull), null, false, true, force_request);
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_setup_back_button": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    boolean newVisible = jsonData.optBoolean("is_visible");
                    if (newVisible != isBackButtonVisible) {
                        isBackButtonVisible = newVisible;

                        delegate.onSetBackButtonVisible(isBackButtonVisible);
                    }
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_setup_settings_button": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    boolean newVisible = jsonData.optBoolean("is_visible");
                    if (newVisible != isSettingsButtonVisible) {
                        isSettingsButtonVisible = newVisible;

                        delegate.onSetSettingsButtonVisible(isSettingsButtonVisible);
                    }
                } catch (JSONException e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_open_invoice": {
                try {
                    JSONObject jsonData = new JSONObject(eventData);
                    String slug = jsonData.optString("slug");

                    if (currentPaymentSlug != null) {
                        onInvoiceStatusUpdate(slug, "cancelled", true);
                        break;
                    }

                    currentPaymentSlug = slug;

                    TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
                    TLRPC.TL_inputInvoiceSlug invoiceSlug = new TLRPC.TL_inputInvoiceSlug();
                    invoiceSlug.slug = slug;
                    req.invoice = invoiceSlug;

                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error != null) {
                            onInvoiceStatusUpdate(slug, "failed");
                        } else {
                            delegate.onWebAppOpenInvoice(invoiceSlug, slug, response);
                        }
                    }));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
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
            case "web_app_request_theme": {
                notifyThemeChanged();
                break;
            }
            case "web_app_ready": {
                setPageLoaded(webView.getUrl(), true);
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
                    boolean hasShineEffect = info.optBoolean("has_shine_effect", false) && isVisible;

                    lastButtonColor = color;
                    lastButtonTextColor = textColor;
                    lastButtonText = text;
                    buttonData = eventData;

                    delegate.onSetupMainButton(isVisible, isActive, text, color, textColor, isProgressVisible, hasShineEffect);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_setup_secondary_button": {
                try {
                    JSONObject info = new JSONObject(eventData);
                    boolean isActive = info.optBoolean("is_active", false);
                    String text = info.optString("text", lastSecondaryButtonText).trim();
                    boolean isVisible = info.optBoolean("is_visible", false) && !TextUtils.isEmpty(text);
                    int color = info.has("color") ? Color.parseColor(info.optString("color")) : lastSecondaryButtonColor;
                    int textColor = info.has("text_color") ? Color.parseColor(info.optString("text_color")) : lastSecondaryButtonTextColor;
                    boolean isProgressVisible = info.optBoolean("is_progress_visible", false) && isVisible;
                    boolean hasShineEffect = info.optBoolean("has_shine_effect", false) && isVisible;
                    String position = info.has("position") ? info.optString("position") : lastSecondaryButtonPosition;
                    if (position == null) position = "left";

                    lastSecondaryButtonColor = color;
                    lastSecondaryButtonTextColor = textColor;
                    lastSecondaryButtonText = text;
                    lastSecondaryButtonPosition = position;
                    secondaryButtonData = eventData;

                    delegate.onSetupSecondaryButton(isVisible, isActive, text, color, textColor, isProgressVisible, hasShineEffect, position);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case "web_app_request_write_access": {
                if (ignoreDialog(3)) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("status", "cancelled");
                        notifyEvent("write_access_requested", data);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }

                final int account = currentAccount;
                final MyWebView finalWebView = webView;
                TL_bots.canSendMessage req = new TL_bots.canSendMessage();
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botUser);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (res instanceof TLRPC.TL_boolTrue) {
                        try {
                            JSONObject data = new JSONObject();
                            data.put("status", "allowed");
                            notifyEvent(account, finalWebView, "write_access_requested", data);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        return;
                    } else if (err != null) {
                        unknownError(err.text);
                        return;
                    }

                    final String[] status = new String[] { "cancelled" };
                    showDialog(3, new AlertDialog.Builder(getContext())
                        .setTitle(getString(R.string.BotWebViewRequestWriteTitle))
                        .setMessage(getString(R.string.BotWebViewRequestWriteMessage))
                        .setPositiveButton(getString(R.string.BotWebViewRequestAllow), (di, w) -> {
                            TL_bots.allowSendMessage req2 = new TL_bots.allowSendMessage();
                            req2.bot = MessagesController.getInstance(currentAccount).getInputUser(botUser);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                                if (res2 != null) {
                                    status[0] = "allowed";
                                    if (res2 instanceof TLRPC.Updates) {
                                        MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res2, false);
                                    }
                                }
                                if (err2 != null) {
                                    unknownError(err2.text);
                                }
                                di.dismiss();
                            }));
                        })
                        .setNegativeButton(getString(R.string.BotWebViewRequestDontAllow), (di, w) -> {
                            di.dismiss();
                        })
                        .create(),
                        () -> {
                            try {
                                JSONObject data = new JSONObject();
                                data.put("status", status[0]);
                                notifyEvent(account, finalWebView, "write_access_requested", data);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    );
                }));
                break;
            }
            case "web_app_invoke_custom_method": {
                if (botUser == null) return;

                String reqId, method, paramsString;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    reqId = jsonObject.getString("req_id");
                    method = jsonObject.getString("method");
                    Object params = jsonObject.get("params");
                    paramsString = params.toString();
                } catch (Exception e) {
                    FileLog.e(e);
                    if (e instanceof JSONException) {
                        error("JSON Parse error");
                    } else {
                        unknownError();
                    }
                    return;
                }

                final int account = currentAccount;
                final MyWebView finalWebView = webView;
                TL_bots.invokeWebViewCustomMethod req = new TL_bots.invokeWebViewCustomMethod();
                req.bot = MessagesController.getInstance(account).getInputUser(botUser.id);
                req.custom_method = method;
                req.params = new TLRPC.TL_dataJSON();
                req.params.data = paramsString;
                ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("req_id", reqId);
                        if (res instanceof TLRPC.TL_dataJSON) {
                            Object json = new JSONTokener(((TLRPC.TL_dataJSON) res).data).nextValue();
                            data.put("result", json);
                        } else if (err != null) {
                            data.put("error", err.text);
                        }
                        notifyEvent(account, finalWebView, "custom_method_invoked", data);
                    } catch (Exception e) {
                        FileLog.e(e);
                        unknownError();
                    }
                }));
                break;
            }
            case "web_app_request_phone": {
                if (ignoreDialog(4)) {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("status", "cancelled");
                        notifyEvent("phone_requested", data);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }

                final int account = currentAccount;
                final MyWebView finalWebView = webView;
                final String[] status = new String[] { "cancelled" };
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                builder.setTitle(getString(R.string.ShareYouPhoneNumberTitle));
                SpannableStringBuilder message = new SpannableStringBuilder();
                String botName = UserObject.getUserName(botUser);
                if (!TextUtils.isEmpty(botName)) {
                    message.append(AndroidUtilities.replaceTags(formatString(R.string.AreYouSureShareMyContactInfoWebapp, botName)));
                } else {
                    message.append(AndroidUtilities.replaceTags(getString(R.string.AreYouSureShareMyContactInfoBot)));
                }
                final boolean blocked = MessagesController.getInstance(currentAccount).blockePeers.indexOfKey(botUser.id) >= 0;
                if (blocked) {
                    message.append("\n\n");
                    message.append(getString(R.string.AreYouSureShareMyContactInfoBotUnblock));
                }
                builder.setMessage(message);
                builder.setPositiveButton(getString(R.string.ShareContact), (di, i) -> {
                    status[0] = null;
                    di.dismiss();

                    if (blocked) {
                        MessagesController.getInstance(currentAccount).unblockPeer(botUser.id, () -> {
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(UserConfig.getInstance(currentAccount).getCurrentUser(), botUser.id, null, null, null, null, true, 0));

                            try {
                                JSONObject data = new JSONObject();
                                data.put("status", "sent");
                                notifyEvent(account, finalWebView, "phone_requested", data);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(UserConfig.getInstance(currentAccount).getCurrentUser(), botUser.id, null, null, null, null, true, 0));

                        try {
                            JSONObject data = new JSONObject();
                            data.put("status", "sent");
                            notifyEvent(account, finalWebView, "phone_requested", data);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
                builder.setNegativeButton(getString(R.string.Cancel), (di, i) -> {
                    di.dismiss();
                });
                showDialog(4, builder.create(), () -> {
                    if (status[0] == null) {
                        return;
                    }
                    try {
                        JSONObject data = new JSONObject();
                        data.put("status", status[0]);
                        notifyEvent(account, finalWebView, "phone_requested", data);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                break;
            }
            case "web_app_biometry_get_info": {
                notifyBiometryReceived();
                break;
            }
            case "web_app_biometry_request_access": {
                String reason = null;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    reason = jsonObject.getString("reason");
                } catch (Exception e) {}
                final String finalReason = reason;
                createBiometry();
                if (biometry == null) {
                    return;
                }
                if (biometry.access_requested) {
                    notifyBiometryReceived();
                    return;
                }
                if (!biometry.access_granted) {
                    Runnable[] cancel = new Runnable[] {() -> {
                        biometry.access_requested = true;
                        biometry.save();
                        notifyBiometryReceived();
                    }};
                    AlertDialog.Builder alert = new AlertDialog.Builder(getContext(), resourcesProvider);
                    if (TextUtils.isEmpty(reason)) {
                        alert.setTitle(getString(R.string.BotAllowBiometryTitle));
                        alert.setMessage(AndroidUtilities.replaceTags(formatString(R.string.BotAllowBiometryMessage, UserObject.getUserName(botUser))));
                    } else {
                        alert.setTitle(AndroidUtilities.replaceTags(formatString(R.string.BotAllowBiometryMessage, UserObject.getUserName(botUser))));
                        alert.setMessage(reason);
                    }
                    alert.setPositiveButton(getString(R.string.Allow), (di, w) -> {
                        if (cancel[0] != null) {
                            cancel[0] = null;
                        }
                        biometry.access_requested = true;
                        biometry.save();
                        biometry.requestToken(null, (status, token) -> {
                            if (status) {
                                biometry.access_granted = true;
                                biometry.save();
                            }
                            notifyBiometryReceived();
                        });
                    });
                    alert.setNegativeButton(getString(R.string.Cancel), (di, w) -> {
                        if (cancel[0] != null) {
                            cancel[0] = null;
                        }
                        biometry.access_requested = true;
                        biometry.disabled = true;
                        biometry.save();
                        notifyBiometryReceived();
                    });
                    alert.setOnDismissListener(di -> {
                        if (cancel[0] != null) {
                            cancel[0].run();
                            cancel[0] = null;
                        }
                    });
                    alert.show();
                } else {
                    if (!biometry.access_requested) {
                        biometry.access_requested = true;
                        biometry.save();
                    }
                    notifyBiometryReceived();
                }
                break;
            }
            case "web_app_biometry_request_auth": {
                String reason = null;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    reason = jsonObject.getString("reason");
                } catch (Exception e) {}
                createBiometry();
                if (biometry == null) {
                    return;
                }
                if (!biometry.access_granted) {
                    try {
                        JSONObject auth = new JSONObject();
                        auth.put("status", "failed");
                        notifyEvent("biometry_auth_requested", auth);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }
                biometry.requestToken(reason, (status, token) -> {
                    if (status) {
                        biometry.access_granted = true;
                    }
                    try {
                        JSONObject auth = new JSONObject();
                        auth.put("status", status ? "authorized" : "failed");
                        auth.put("token", token);
                        notifyEvent("biometry_auth_requested", auth);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                break;
            }
            case "web_app_biometry_update_token": {
                String reason = null;
                String token;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    token = jsonObject.getString("token");
                    try {
                        reason = jsonObject.getString("reason");
                    } catch (Exception e2) {}
                } catch (Exception e) {
                    FileLog.e(e);
                    if (e instanceof JSONException) {
                        error("JSON Parse error");
                    } else {
                        unknownError();
                    }
                    return;
                }
                createBiometry();
                if (biometry == null) {
                    return;
                }
                if (!biometry.access_granted) {
                    try {
                        JSONObject auth = new JSONObject();
                        auth.put("status", "failed");
                        notifyEvent("biometry_token_updated", auth);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return;
                }
                biometry.updateToken(reason, token, status -> {
                    try {
                        JSONObject auth = new JSONObject();
                        auth.put("status", status ? (TextUtils.isEmpty(token) ? "removed" : "updated") : "failed");
                        notifyEvent("biometry_token_updated", auth);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                break;
            }
            case "web_app_biometry_open_settings": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }

                lastClickMs = 0;

                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null || lastFragment.getParentLayout() == null) return;
                final INavigationLayout parentLayout = lastFragment.getParentLayout();
                lastFragment.presentFragment(ProfileActivity.of(botUser.id));
                AndroidUtilities.scrollToFragmentRow(parentLayout, "botPermissionBiometry");
                if (delegate != null) {
                    delegate.onCloseToTabs();
                }

                break;
            }
            case "web_app_share_to_story": {
                if (isRequestingPageOpen || System.currentTimeMillis() - lastClickMs > 10_000 || System.currentTimeMillis() - lastPostStoryMs < 2000) {
                    return;
                }
                lastClickMs = 0;
                lastPostStoryMs = System.currentTimeMillis();
                String media_url = null;
                String text = null;
                String widget_link = null;
                String widget_link_name = null;
                try {
                    JSONObject jsonObject = new JSONObject(eventData);
                    media_url = jsonObject.optString("media_url");
                    text = jsonObject.optString("text");
                    JSONObject link = jsonObject.optJSONObject("widget_link");
                    if (link != null) {
                        widget_link = link.optString("url");
                        widget_link_name = link.optString("name");
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (media_url == null) return;
                final String finalText = text;
                final String finalLink = widget_link;
                final String finalLinkName = widget_link_name;

                if (!MessagesController.getInstance(currentAccount).storiesEnabled()) {
                    new PremiumFeatureBottomSheet(new BaseFragment() {
                        { this.currentAccount = BotWebViewContainer.this.currentAccount; }
                        @Override
                        public Dialog showDialog(Dialog dialog) {
                            dialog.show();
                            return dialog;
                        }
                        @Override
                        public Activity getParentActivity() {
                            return BotWebViewContainer.this.parentActivity;
                        }
                        @Override
                        public Theme.ResourcesProvider getResourceProvider() {
                            return new WrappedResourceProvider(resourcesProvider) {
                                @Override
                                public void appendColors() {
                                    sparseIntArray.append(Theme.key_dialogBackground, 0xFF1E1E1E);
                                    sparseIntArray.append(Theme.key_windowBackgroundGray, 0xFF000000);
                                }
                            };
                        }
                        @Override
                        public boolean isLightStatusBar() {
                            return false;
                        }
                    }, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, true).show();
                    return;
                }

                AlertDialog progressDialog = new AlertDialog(parentActivity, AlertDialog.ALERT_TYPE_SPINNER);
                new HttpGetFileTask(file -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (file == null) {
                            progressDialog.dismissUnless(500);
                            return;
                        }
                        final int[] params = new int[AnimatedFileDrawable.PARAM_NUM_COUNT];
                        Runnable open = () -> {
                            StoryEntry entry;
                            final boolean isVideo = params[AnimatedFileDrawable.PARAM_NUM_DURATION] > 0;
                            if (isVideo) {
                                final int width = params[AnimatedFileDrawable.PARAM_NUM_WIDTH];
                                final int height = params[AnimatedFileDrawable.PARAM_NUM_HEIGHT];
                                int twidth = width, theight = height;
                                if (twidth > AndroidUtilities.getPhotoSize()) {
                                    twidth = AndroidUtilities.getPhotoSize();
                                }
                                if (theight > AndroidUtilities.getPhotoSize()) {
                                    theight = AndroidUtilities.getPhotoSize();
                                }
                                File thumb = StoryEntry.makeCacheFile(UserConfig.selectedAccount, "jpg");
                                AnimatedFileDrawable drawable = new AnimatedFileDrawable(file, true, 0, 0, null, null, null, 0, UserConfig.selectedAccount, true, twidth, theight, null);
                                Bitmap thumbBitmap = drawable.getFirstFrame(null);
                                drawable.recycle();
                                if (thumbBitmap != null) {
                                    try {
                                        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 80, new FileOutputStream(thumb));
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                        thumb = null;
                                    }
                                }
                                entry = StoryEntry.fromVideoShoot(file, thumb == null ? null : thumb.getAbsolutePath(), params[AnimatedFileDrawable.PARAM_NUM_DURATION]);
                                entry.width = width;
                                entry.height = height;
                                entry.setupMatrix();
                            } else {
                                Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(file);
                                entry = StoryEntry.fromPhotoShoot(file, orientation.first);
                            }
                            if (entry.width <= 0 || entry.height <= 0) {
                                progressDialog.dismissUnless(500);
                                return;
                            }
                            if (finalText != null) {
                                entry.caption = finalText;
                            }
                            if (!TextUtils.isEmpty(finalLink) && UserConfig.getInstance(currentAccount).isPremium()) {
                                if (entry.mediaEntities == null) entry.mediaEntities = new ArrayList<>();
                                VideoEditedInfo.MediaEntity entity = new VideoEditedInfo.MediaEntity();
                                entity.type = VideoEditedInfo.MediaEntity.TYPE_LINK;
                                entity.subType = -1;
                                entity.color = 0xFFFFFFFF;
                                entity.linkSettings = new LinkPreview.WebPagePreview();
                                entity.linkSettings.url = finalLink;
                                if (finalLinkName != null) {
                                    entity.linkSettings.flags |= 2;
                                    entity.linkSettings.name = finalLinkName;
                                }
                                entry.mediaEntities.add(entity);
                            }
                            StoryRecorder.getInstance(parentActivity, UserConfig.selectedAccount)
                                .openRepost(null, entry);
                            progressDialog.dismissUnless(500);
                        };
                        Utilities.globalQueue.postRunnable(() -> {
                            AnimatedFileDrawable.getVideoInfo(file.getAbsolutePath(), params);
                            AndroidUtilities.runOnUIThread(open);
                        });
                    });
                }, null).execute(media_url);
                progressDialog.showDelayed(250);

                break;
            }
            case "web_app_request_fullscreen": {
                final String err;
                if ((err = delegate.onFullscreenRequested(true)) == null) {
                    notifyEvent("fullscreen_changed", obj("is_fullscreen", true));
                } else {
                    notifyEvent("fullscreen_failed", obj("error", err));
                }
                break;
            }
            case "web_app_exit_fullscreen": {
                final String err;
                if ((err = delegate.onFullscreenRequested(false)) == null) {
                    notifyEvent("fullscreen_changed", obj("is_fullscreen", false));
                } else {
                    notifyEvent("fullscreen_failed", obj("error", err));
                }
                break;
            }
            case "web_app_start_accelerometer": {
                final BotSensors sensors = delegate.getBotSensors();
                long refresh_rate = 1000;
                try {
                    refresh_rate = new JSONObject(eventData).getLong("refresh_rate");
                } catch (Exception e) {}
                refresh_rate = Utilities.clamp(refresh_rate, 1000, 20);
                if (sensors != null && sensors.startAccelerometer(refresh_rate)) {
                    notifyEvent("accelerometer_started", null);
                } else {
                    notifyEvent("accelerometer_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_stop_accelerometer": {
                final BotSensors sensors = delegate.getBotSensors();
                if (sensors != null && sensors.stopAccelerometer()) {
                    notifyEvent("accelerometer_stopped", null);
                } else {
                    notifyEvent("accelerometer_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_start_gyroscope": {
                final BotSensors sensors = delegate.getBotSensors();
                long refresh_rate = 1000;
                try {
                    refresh_rate = new JSONObject(eventData).getLong("refresh_rate");
                } catch (Exception e) {}
                refresh_rate = Utilities.clamp(refresh_rate, 1000, 20);
                if (sensors != null && sensors.startGyroscope(refresh_rate)) {
                    notifyEvent("gyroscope_started", null);
                } else {
                    notifyEvent("gyroscope_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_stop_gyroscope": {
                final BotSensors sensors = delegate.getBotSensors();
                if (sensors != null && sensors.stopGyroscope()) {
                    notifyEvent("gyroscope_stopped", null);
                } else {
                    notifyEvent("gyroscope_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_start_device_orientation": {
                final BotSensors sensors = delegate.getBotSensors();
                long refresh_rate = 1000;
                boolean absolute = false;
                try {
                    JSONObject json = new JSONObject(eventData);
                    refresh_rate = json.getLong("refresh_rate");
                    absolute = json.optBoolean("need_absolute", false);
                } catch (Exception e) {}
                refresh_rate = Utilities.clamp(refresh_rate, 1000, 20);
                if (sensors != null && sensors.startOrientation(absolute, refresh_rate)) {
                    notifyEvent("device_orientation_started", null);
                } else {
                    notifyEvent("device_orientation_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_stop_device_orientation": {
                final BotSensors sensors = delegate.getBotSensors();
                if (sensors != null && sensors.stopOrientation()) {
                    notifyEvent("device_orientation_stopped", null);
                } else {
                    notifyEvent("device_orientation_failed", obj("error", "UNSUPPORTED"));
                }
                break;
            }
            case "web_app_add_to_home_screen": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }
                if (MediaDataController.getInstance(currentAccount).isShortcutAdded(botUser.id, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT)) {
                    notifyEvent("home_screen_added", null);
                    return;
                }
                MediaDataController.getInstance(currentAccount).installShortcut(botUser.id, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT, result -> {
                    if (result) {
                        notifyEvent("home_screen_added", null);
                    } else {
                        notifyEvent("home_screen_failed", obj("error", "UNSUPPORTED"));
                    }
                });
                break;
            }
            case "web_app_check_home_screen": {
                notifyEvent("home_screen_checked", obj(
                    "status", botUser != null && Build.VERSION.SDK_INT >= 26 ? (
                        MediaDataController.getInstance(currentAccount).isShortcutAdded(botUser.id, MediaDataController.SHORTCUT_TYPE_ATTACHED_BOT) ? "added" : "missed"
                    ) : "unsupported"
                ));
                break;
            }
            case "web_app_set_emoji_status": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }
                long custom_emoji_id = 0;
                int duration = 0;
                try {
                    JSONObject o = new JSONObject(eventData);
                    custom_emoji_id = Long.parseLong(o.getString("custom_emoji_id"));
                    duration = o.getInt("duration");
                } catch (Exception e) {}
                if (botUser == null) {
                    notifyEvent("emoji_status_failed", obj("error", "UNKNOWN_ERROR"));
                    return;
                }
                SetupEmojiStatusSheet.show(currentAccount, botUser, custom_emoji_id, duration, (error, document) -> {
                    if (error == null) {
                        notifyEvent("emoji_status_set", null);
                        if (delegate != null) {
                            delegate.onEmojiStatusSet(document);
                        }
                    } else {
                        notifyEvent("emoji_status_failed", obj("error", error));
                    }
                });
                break;
            }
            case "web_app_request_emoji_status_access": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }
                SetupEmojiStatusSheet.askPermission(currentAccount, botUser.id, (shownDialog, status) -> {
                    notifyEmojiStatusAccess(status);
                    if (shownDialog && "allowed".equalsIgnoreCase(status) && delegate != null) {
                        delegate.onEmojiStatusGranted(true);
                    }
                });
                break;
            }
            case "web_app_request_safe_area": {
                reportSafeInsets(lastInsets, true);
                break;
            }
            case "web_app_request_content_safe_area": {
                reportSafeContentInsets(lastInsetsTopMargin, true);
                break;
            }
            case "web_app_request_location": {
                if (isRequestingPageOpen || botUser == null) {
                    return;
                }
                if (location == null) {
                    location = BotLocation.get(getContext(), currentAccount, botUser.id);
                    location.listen(this.notifyLocationChecked);
                }
                if (!location.granted()) {
                    location.request((now, granted) -> {
                        if (delegate != null && now) {
                            delegate.onLocationGranted(granted);
                        }
                        location.requestObject(obj -> {
                            notifyEvent("location_requested", obj);
                        });
                    });
                } else {
                    location.requestObject(obj -> {
                        notifyEvent("location_requested", obj);
                    });
                }
                break;
            }
            case "web_app_check_location": {
                if (location == null) {
                    location = BotLocation.get(getContext(), currentAccount, botUser.id);
                    location.listen(this.notifyLocationChecked);
                }
                notifyLocationChecked.run();
                break;
            }
            case "web_app_open_location_settings": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }

                lastClickMs = 0;

                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment == null || lastFragment.getParentLayout() == null) return;
                final INavigationLayout parentLayout = lastFragment.getParentLayout();
                lastFragment.presentFragment(ProfileActivity.of(botUser.id));
                AndroidUtilities.scrollToFragmentRow(parentLayout, "botPermissionLocation");
                if (delegate != null) {
                    delegate.onCloseToTabs();
                }

                break;
            }
            case "web_app_request_file_download": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }

                if (downloads == null) {
                    downloads = BotDownloads.get(getContext(), currentAccount, botUser.id);
                }
                String url, file_name;
                try {
                    JSONObject o = new JSONObject(eventData);
                    url = o.getString("url");
                    file_name = o.getString("file_name");
                } catch (Exception e) {
                    FileLog.e(e);
                    notifyEvent("file_download_requested", obj("status", "cancelled"));
                    return;
                }
                if (downloads.getCached(url) != null) {
                    downloads.download(url, file_name);
                    notifyEvent("file_download_requested", obj("status", "downloading"));
                    return;
                }

                final String finalUrl = url;
                final String finalFileName = file_name;
                final TL_bots.checkDownloadFileParams req = new TL_bots.checkDownloadFileParams();
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(botUser);
                req.file_name = file_name;
                req.url = url;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!(res instanceof TLRPC.TL_boolTrue)) {
                        notifyEvent("file_download_requested", obj("status", "cancelled"));
                        return;
                    }
                    BotDownloads.showAlert(getContext(), finalUrl, finalFileName, UserObject.getUserName(botUser), status -> {
                        if (!status) {
                            notifyEvent("file_download_requested", obj("status", "cancelled"));
                            return;
                        }

                        downloads.download(finalUrl, finalFileName);
                        notifyEvent("file_download_requested", obj("status", "downloading"));
                    });
                }));
                break;
            }
            case "web_app_send_prepared_message": {
                if (isRequestingPageOpen || botUser == null || System.currentTimeMillis() - lastClickMs > 10_000) {
                    return;
                }

                String id = null;
                try {
                    JSONObject o = new JSONObject(eventData);
                    id = o.getString("id");
                } catch (Exception e) {
                    FileLog.e(e);
                    notifyEvent("prepared_message_failed", obj("error", "MESSAGE_EXPIRED"));
                    return;
                }
                if (TextUtils.isEmpty(id)) {
                    notifyEvent("prepared_message_failed", obj("error", "MESSAGE_EXPIRED"));
                    return;
                }

                BotShareSheet.share(getContext(), currentAccount, botUser.id, id, resourcesProvider, () -> {
                    if (delegate != null) {
                        delegate.onCloseToTabs();
                    }
                    LaunchActivity.dismissAllWeb();
                }, (error, dialogIds) -> {
                    if (TextUtils.isEmpty(error)) {
                        notifyEvent("prepared_message_sent", null);
                        if (delegate != null) {
                            delegate.onOpenBackFromTabs();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (proxy != null && proxy.container != null && proxy.container.delegate != null) {
                                proxy.container.delegate.onSharedTo(dialogIds);
                            }
                        }, 500);
                    } else {
                        notifyEvent("prepared_message_failed", obj("error", error));
                    }
                });
                break;
            }
            case "web_app_toggle_orientation_lock": {
                boolean locked = false;
                try {
                    JSONObject o = new JSONObject(eventData);
                    locked = o.getBoolean("locked");
                } catch (Exception e) {}
                if (delegate != null) {
                    delegate.onOrientationLockChanged(locked);
                }
                break;
            }
            case "web_app_device_storage_save_key": {
                if (botUser == null) return;
                if (storage == null) storage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, false);
                setStorageKey(storage, eventData, "device_storage_key_saved", "device_storage_failed");
                break;
            }
            case "web_app_device_storage_get_key": {
                if (botUser == null) return;
                if (storage == null) storage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, false);
                getStorageKey(storage, eventData, "device_storage_key_received", "device_storage_failed");
                break;
            }
            case "web_app_device_storage_clear": {
                if (botUser == null) return;
                if (storage == null) storage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, false);
                clearStorageKey(storage, eventData, "device_storage_cleared", "device_storage_failed");
                break;
            }
            case "web_app_secure_storage_save_key": {
                if (botUser == null) return;
                if (secureStorage == null) secureStorage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, true);
                setStorageKey(secureStorage, eventData, "secure_storage_key_saved", "secure_storage_failed");
                break;
            }
            case "web_app_secure_storage_get_key": {
                if (botUser == null) return;
                if (secureStorage == null) secureStorage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, true);
                getStorageKey(secureStorage, eventData, "secure_storage_key_received", "secure_storage_failed");
                break;
            }
            case "web_app_secure_storage_clear": {
                if (botUser == null) return;
                if (secureStorage == null) secureStorage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, true);
                clearStorageKey(secureStorage, eventData, "secure_storage_cleared", "secure_storage_cleared");
                break;
            }
            case "web_app_secure_storage_restore_key": {
                if (botUser == null) return;
                if (secureStorage == null) secureStorage = new BotStorage(getContext(), currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), botUser.id, true);
                restoreStorageKey(secureStorage, eventData, "secure_storage_key_restored", "secure_storage_cleared");
                break;
            }
            default: {
                FileLog.d("unknown webapp event " + eventType);
                break;
            }
        }
    }

    private void setStorageKey(BotStorage storage, String eventData, String eventSuccess, String eventFail) {
        if (storage == null || botUser == null) return;
        String req_id = "";
        JSONObject o;
        try {
            o = new JSONObject(eventData);
            req_id = o.getString("req_id");
        } catch (Exception e) {
            FileLog.e(e);
            if (!TextUtils.isEmpty(req_id)) {
                notifyEvent(eventFail, obj("req_id", req_id, "error", "UNKNOWN_ERROR"));
            }
            return;
        }
        String key;
        try {
            key = o.optString("key");
        } catch (Exception e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        if (key == null) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        String value;
        try {
            value = o.optString("value");
        } catch (Exception e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "VALUE_INVALID"));
            return;
        }
        try {
            storage.setKey(key, value);
        } catch (RuntimeException e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", e.getMessage()));
            return;
        }
        notifyEvent(eventSuccess, obj("req_id", req_id));
    }

    private void getStorageKey(BotStorage storage, String eventData, String eventSuccess, String eventFail) {
        if (storage == null || botUser == null) return;
        String req_id = "";
        JSONObject o;
        try {
            o = new JSONObject(eventData);
            req_id = o.getString("req_id");
        } catch (Exception e) {
            FileLog.e(e);
            if (!TextUtils.isEmpty(req_id)) {
                notifyEvent(eventFail, obj("req_id", req_id, "error", "UNKNOWN_ERROR"));
            }
            return;
        }
        String key;
        try {
            key = o.optString("key");
        } catch (Exception e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        if (key == null) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        try {
            Pair<String, Boolean> pair = storage.getKey(key);
            if (storage.secured && pair.first == null) {
                notifyEvent(eventSuccess, obj("req_id", req_id, "value", pair.first, "can_restore", pair.second));
            } else {
                notifyEvent(eventSuccess, obj("req_id", req_id, "value", pair.first));
            }
        } catch (RuntimeException e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", e.getMessage()));
        }
    }

    private void restoreStorageKey(BotStorage storage, String eventData, String eventSuccess, String eventFail) {
        if (storage == null || botUser == null) return;
        String req_id = "";
        JSONObject o;
        try {
            o = new JSONObject(eventData);
            req_id = o.getString("req_id");
        } catch (Exception e) {
            FileLog.e(e);
            if (!TextUtils.isEmpty(req_id)) {
                notifyEvent(eventFail, obj("req_id", req_id, "error", "UNKNOWN_ERROR"));
            }
            return;
        }
        String key;
        try {
            key = o.optString("key");
        } catch (Exception e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        if (key == null) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "KEY_INVALID"));
            return;
        }
        final List<BotStorage.StorageConfig> storages;
        try {
            storages = storage.getStoragesWithKey(key);
        } catch (Exception e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", e.getMessage()));
            return;
        }
        if (storages.isEmpty()) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", "RESTORE_UNAVAILABLE"));
            return;
        }
        final String f_req_id = req_id;
        storage.showChooseStorage(getContext(), storages, selected -> {
            if (selected == null) {
                notifyEvent(eventFail, obj("req_id", f_req_id, "error", "RESTORE_CANCELLED"));
                return;
            }
            final String restoredValue;
            try {
                storage.restoreFrom(selected);
                restoredValue = storage.getKey(key).first;
            } catch (Exception e) {
                notifyEvent(eventFail, obj("req_id", f_req_id, "error", e.getMessage()));
                return;
            }
            notifyEvent(eventSuccess, obj("req_id", f_req_id, "value", restoredValue));
        });
    }

    private void clearStorageKey(BotStorage storage, String eventData, String eventSuccess, String eventFail) {
        if (storage == null || botUser == null) return;
        String req_id = "";
        JSONObject o;
        try {
            o = new JSONObject(eventData);
            req_id = o.getString("req_id");
        } catch (Exception e) {
            FileLog.e(e);
            if (!TextUtils.isEmpty(req_id)) {
                notifyEvent(eventFail, obj("req_id", req_id, "error", "UNKNOWN_ERROR"));
            }
            return;
        }
        try {
            storage.clear();
        } catch (RuntimeException e) {
            notifyEvent(eventFail, obj("req_id", req_id, "error", e.getMessage()));
            return;
        }
        notifyEvent(eventSuccess, obj("req_id", req_id));
    }

    private final Rect lastInsets = new Rect(0, 0, 0, 0);
    private int lastInsetsTopMargin = 0;
    public void reportSafeInsets(Rect insets, int topContentMargin) {
        reportSafeInsets(insets, false);
        reportSafeContentInsets(topContentMargin, false);
    }
    private void reportSafeInsets(Rect insets, boolean force) {
        if (insets == null || !force && lastInsets.equals(insets))
            return;
        notifyEvent("safe_area_changed", obj(
            "left", insets.left / AndroidUtilities.density,
            "top", insets.top / AndroidUtilities.density,
            "right", insets.right / AndroidUtilities.density,
            "bottom", insets.bottom / AndroidUtilities.density
        ));
        lastInsets.set(insets);
    }
    private void reportSafeContentInsets(int topContentMargin, boolean force) {
        if (!force && topContentMargin == lastInsetsTopMargin)
            return;
        notifyEvent("content_safe_area_changed", obj(
            "left", 0,
            "top", topContentMargin / AndroidUtilities.density,
            "right", 0,
            "bottom", 0
        ));
        lastInsetsTopMargin = topContentMargin;
    }

    public void notifyEmojiStatusAccess(String status) {
        notifyEvent("emoji_status_access_requested", obj("status", status));
    }

    private void createBiometry() {
        if (botUser == null) {
            return;
        }
        if (biometry == null) {
            biometry = BotBiometry.get(getContext(), currentAccount, botUser.id);
        } else {
            biometry.load();
        }
    }

    private void notifyBiometryReceived() {
        if (botUser == null) {
            return;
        }
        createBiometry();
        if (biometry == null) {
            return;
        }
        try {
            notifyEvent("biometry_info_received", biometry.getStatus());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void unknownError() {
        unknownError(null);
    }

    private void unknownError(String errCode) {
        error(getString("UnknownError", R.string.UnknownError) + (errCode != null ? ": " + errCode : ""));
    }

    private void error(String reason) {
        BulletinFactory.of(this, resourcesProvider).createSimpleBulletin(R.raw.error, reason).show();
    }

    private final Runnable notifyLocationChecked = () -> {
        notifyEvent("location_checked", location.checkObject());
    };

    private int lastDialogType = -1;
    private int shownDialogsCount = 0;
    private long blockedDialogsUntil;

    private boolean ignoreDialog(int type) {
        if (currentDialog != null) {
            return true;
        }
        if (blockedDialogsUntil > 0 && System.currentTimeMillis() < blockedDialogsUntil) {
            return true;
        }
        if (lastDialogType == type && shownDialogsCount > 3) {
            blockedDialogsUntil = System.currentTimeMillis() + 3 * 1000L;
            shownDialogsCount = 0;
            return true;
        }
        return false;
    }

    private boolean showDialog(int type, AlertDialog dialog, Runnable onDismiss) {
        if (dialog == null || ignoreDialog(type)) {
            return false;
        }
        dialog.setOnDismissListener(di -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
            currentDialog = null;
        });
        currentDialog = dialog;
        currentDialog.setDismissDialogByButtons(false);
        currentDialog.show();

        if (lastDialogType != type) {
            lastDialogType = type;
            shownDialogsCount = 0;
            blockedDialogsUntil = 0;
        }
        shownDialogsCount++;

        return true;
    }

    private void openQrScanActivity() {
        if (parentActivity == null) {
            return;
        }

        cameraBottomSheet = CameraScanActivity.showAsSheet(parentActivity, false, CameraScanActivity.TYPE_QR_WEB_BOT, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                try {
                    lastClickMs = System.currentTimeMillis();
                    notifyEvent("qr_text_received", new JSONObject().put("data", text));
                } catch (JSONException e) {
                    FileLog.e(e);
                }
            }

            @Override
            public String getSubtitleText() {
                return lastQrText;
            }

            @Override
            public void onDismiss() {
                notifyEvent("scan_qr_popup_closed", null);
                hasQRPending = false;
            }
        });
    }

    private JSONObject buildThemeParams() {
        try {
            JSONObject object = BotWebViewSheet.makeThemeParams(resourcesProvider, true);
            if (object != null) {
                return new JSONObject().put("theme_params", object);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new JSONObject();
    }

    private int getColor(int colorKey) {
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(colorKey);
        }
        return Theme.getColor(colorKey);
    }

    private String formatColor(int colorKey) {
        int color = getColor(colorKey);
        return "#" + hexFixed(Color.red(color)) + hexFixed(Color.green(color)) + hexFixed(Color.blue(color));
    }

    private String hexFixed(int h) {
        String hex = Integer.toHexString(h);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    public void onWebViewCreated(MyWebView webView) {

    }

    public void onWebViewDestroyed(MyWebView webView) {

    }

    public static class BotWebViewProxy {
        public BotWebViewContainer container;
        public BotWebViewProxy(BotWebViewContainer container) {
            this.container = container;
        }
        public void setContainer(BotWebViewContainer container) {
            this.container = container;
        }
        @Keep
        @JavascriptInterface
        public void postEvent(String eventType, String eventData) {
            try {
                if (container == null) {
                    FileLog.d("webviewproxy.postEvent: no container");
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (container == null) return;
                        container.onEventReceived(this, eventType, eventData);
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static class WebViewProxy {

        public BotWebViewContainer container;
        public final MyWebView webView;

        public WebViewProxy(MyWebView webView, BotWebViewContainer container) {
            this.webView = webView;
            this.container = container;
        }
        public void setContainer(BotWebViewContainer container) {
            this.container = container;
        }

        @Keep
        @JavascriptInterface
        public void post(String type, String data) {
            if (container == null) return;
            AndroidUtilities.runOnUIThread(() -> {
                if (container == null) return;
                container.onWebEventReceived(type, data);
            });
        }

        @Keep
        @JavascriptInterface
        public void resolveShare(String json, byte[] file, String fileName, String fileMimeType) {
            AndroidUtilities.runOnUIThread(() -> {
                if (container == null) return;
                if (System.currentTimeMillis() - container.lastClickMs > 10_000) {
                    webView.evaluateJS("window.navigator.__share__receive(\"security\")");
                    return;
                }
                container.lastClickMs = 0;
                final Context context = webView.getContext();
                Activity activity = AndroidUtilities.findActivity(context);
                if (activity == null && LaunchActivity.instance != null) {
                    activity = LaunchActivity.instance;
                }
                if (context == null || activity == null || !(activity instanceof LaunchActivity) || activity.isFinishing() || !webView.isAttachedToWindow()) {
                    webView.evaluateJS("window.navigator.__share__receive(\"security\")");
                    return;
                }
                final LaunchActivity launchActivity = (LaunchActivity) activity;
                String url = null, title = null, text = null;
                try {
                    JSONObject object = new JSONObject(json);
                    url = object.optString("url", null);
                    text = object.optString("text", null);
                    title = object.optString("title", null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                StringBuilder totalText = new StringBuilder();
                if (title != null) {
                    totalText.append(title);
                }
                if (text != null) {
                    if (totalText.length() > 0)
                        totalText.append("\n");
                    totalText.append(text);
                }
                if (url != null) {
                    if (totalText.length() > 0)
                        totalText.append("\n");
                    totalText.append(url);
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, totalText.toString());
                if (file != null) {
                    File finalFile = null;
                    int i = 0;
                    while (finalFile == null || finalFile.exists()) {
                        finalFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), FileLoader.fixFileName(fileName == null ? "file" : fileName) + (i > 0 ? " (" + i + ")" : ""));
                        i++;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(finalFile);
                        fos.write(file);
                        fos.close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        if (fileMimeType == null) {
                            intent.setType("text/plain");
                        } else {
                            intent.setType(fileMimeType);
                        }
                        if (fileName != null) {
                            intent.putExtra(Intent.EXTRA_TITLE, fileName);
                        }
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(launchActivity, ApplicationLoader.getApplicationId() + ".provider", finalFile));
                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignore) {
                                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                            }
                        } else {
                            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    intent.setType("text/plain");
                }
                launchActivity.whenWebviewShareAPIDone(success -> {
                    webView.evaluateJS("window.navigator.__share__receive("+(success?"":"'abort'")+")");
                });
                launchActivity.startActivityForResult(Intent.createChooser(intent, getString(R.string.ShareFile)), LaunchActivity.WEBVIEW_SHARE_API_REQUEST_CODE);
            });
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
        void onCloseRequested(@Nullable Runnable callback);

        default void onInstantClose() { onCloseRequested(null); };
        default void onCloseToTabs() { onCloseRequested(null); };
        default void onOpenBackFromTabs() {}
        default void onSharedTo(ArrayList<Long> dialogIds) {}

        default void onOrientationLockChanged(boolean locked) {}

        /**
         * Called when WebView requests to change closing behavior
         *
         * @param needConfirmation  If confirmation popup should be shown
         */
        void onWebAppSetupClosingBehavior(boolean needConfirmation);

        void onWebAppSwipingBehavior(boolean allowSwiping);

        /**
         * Called when WebView requests to send custom data
         *
         * @param data  Custom data to send
         */
        default void onSendWebViewData(String data) {}

        /**
         * Called when WebView requests to set action bar color
         *
         * @param colorKey  Color theme key
         * @param color color
         * @param isOverrideColor
         */
        void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor);

        default void onWebAppSetNavigationBarColor(int color) {};

        default void onWebAppBackgroundChanged(boolean actionBarColor, int color) {};

        default void onLocationGranted(boolean granted) {}
        default void onEmojiStatusGranted(boolean granted) {}
        default void onEmojiStatusSet(TLRPC.Document document) {}

        /**
         * Called when WebView requests to set background color
         *
         * @param color New color
         */
        void onWebAppSetBackgroundColor(int color);

        /**
         * Called when WebView requests to expand viewport
         */
        void onWebAppExpand();

        /**
         * Called when web apps requests to switch to inline mode picker
         *
         * @param botUser Bot user
         * @param query Inline query
         * @param chatTypes Chat types
         */
        void onWebAppSwitchInlineQuery(TLRPC.User botUser, String query, List<String> chatTypes);

        /**
         * Called when web app attempts to open invoice
         *
         * @param inputInvoice Invoice source
         * @param slug      Invoice slug for the form
         * @param response  Payment request response
         */
        void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response);

        /**
         * Setups main button
         */
        void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect);
        void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position);

        /**
         * Sets back button enabled and visible
         */
        void onSetBackButtonVisible(boolean visible);

        void onSetSettingsButtonVisible(boolean visible);

        /**
         * Called when WebView is ready (Called web_app_ready or page load finished)
         */
        default void onWebAppReady() {}

        /**
         * @return If clipboard access is available to webapp
         */
        default boolean isClipboardAvailable() {

            return false;
        }

        default String getWebAppName() {
            return null;
        }

        default String onFullscreenRequested(boolean fullscreen) {
            return "UNSUPPORTED";
        }

        default BotSensors getBotSensors() {
            return null;
        }
    }

    public final static class PopupButton {
        public String id;
        public String text;
        public int textColorKey = -1;

        public PopupButton(JSONObject obj) throws JSONException {
            id = obj.getString("id");
            String type = obj.getString("type");
            boolean textRequired = false;
            switch (type) {
                default:
                case "default": {
                    textRequired = true;
                    break;
                }
                case "ok": {
                    text = getString(R.string.OK);
                    break;
                }
                case "close": {
                    text = getString(R.string.Close);
                    break;
                }
                case "cancel": {
                    text = getString(R.string.Cancel);
                    break;
                }
                case "destructive": {
                    textRequired = true;
                    textColorKey = Theme.key_text_RedBold;
                    break;
                }
            }

            if (textRequired) {
                text = obj.getString("text");
            }
        }
    }

    private static int tags = 0;

    public static boolean isTonsite(String url) {
        return url != null && isTonsite(Uri.parse(url));
    }

    public static boolean isTonsite(Uri uri) {
        if ("tonsite".equals(uri.getScheme())) {
            return true;
        }
        String host = uri.getAuthority();
        if (host == null && uri.getScheme() == null) {
            host = Uri.parse("http://" + uri.toString()).getAuthority();
        }
        return host != null && (host.endsWith(".ton") || host.endsWith(".adnl"));
    }

    public static WebResourceResponse proxyTON(WebResourceRequest req) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return proxyTON(req.getMethod(), req.getUrl().toString(), req.getRequestHeaders());
        }
        return null;
    }

    public static String rotateTONHost(String hostname) {
        try {
            hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED);
        } catch (Exception e) {
            FileLog.e(e);
        }
        final String[] parts = hostname.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; ++i) {
            if (i > 0) {
                sb.append("-d");
            }
            sb.append(parts[i].replaceAll("\\-", "-h"));
        }
        sb.append(".").append(MessagesController.getInstance(UserConfig.selectedAccount).tonProxyAddress);
        return sb.toString();
    }

    public static WebResourceResponse proxyTON(String method, String url, Map<String, String> headers) {
        try {
            url = Browser.replaceHostname(Uri.parse(url), rotateTONHost(AndroidUtilities.getHostAuthority(url)), "https");
            URL urlObj = new URL(url);
            HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
            urlConnection.setRequestMethod(method);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    urlConnection.addRequestProperty(e.getKey(), e.getValue());
                }
            }
            urlConnection.connect();
            InputStream inputStream = urlConnection.getInputStream();
            final String contentType = urlConnection.getContentType();
            final String mimeType = contentType.split(";", 2)[0];
            return new WebResourceResponse(mimeType, urlConnection.getContentEncoding(), inputStream);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static class DangerousWebWarning {
        public final String url;
        public final String threatType;
        public final Runnable back, proceed;
        public DangerousWebWarning(
            String url,
            String type,
            Runnable back,
            Runnable proceed
        ) {
            this.url = url;
            this.threatType = type;
            this.back = back;
            this.proceed = proceed;
        }
    }

    public static class MyWebView extends WebView {
        private final int tag = tags++;
        private boolean isPageLoaded;
        private Runnable whenPageLoaded;
        public final boolean bot;

        private String openedByUrl;
        private String currentUrl;
        private BrowserHistory.Entry currentHistoryEntry;

        public MyWebView opener;
        public boolean errorShown;
        public String errorShownAt;

        public String lastSiteName;
        public boolean lastActionBarColorGot, lastBackgroundColorGot;
        public int lastActionBarColor, lastBackgroundColor;

        public String urlFallback = "about:blank";
        public boolean dangerousUrl;

        private BottomSheet currentSheet;

        public DangerousWebWarning currentWarning;
        public boolean isPageLoaded() {
            return isPageLoaded;
        }

        public void whenPageLoaded(Runnable runnable, long maxDelay) {
            this.whenPageLoaded = runnable;
            AndroidUtilities.runOnUIThread(() -> {
                if (this.whenPageLoaded != null) {
                    Runnable callback = this.whenPageLoaded;
                    this.whenPageLoaded = null;
                    callback.run();
                }
            }, maxDelay);
        }

        public void d(String s) {
            FileLog.d("[webview] #" + tag + " " + s);
        }

        public MyWebView(Context context, boolean bot, long botId) {
            super(context);
            this.bot = bot;
            d("created new webview " + this);

            setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    WebView.HitTestResult result = getHitTestResult();
                    if (result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                        final String url = result.getExtra();
                        AndroidUtilities.runOnUIThread(() -> {

                            BottomSheet.Builder builder = new BottomSheet.Builder(getContext(), false, null);
                            String formattedUrl = url;
                            try {
                                try {
                                    Uri uri = Uri.parse(formattedUrl);
                                    if (uri != null && !uri.getScheme().equalsIgnoreCase("data")) {
                                        formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e, false);
                                }
                                formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            builder.setTitleMultipleLines(true);
                            builder.setTitle(formattedUrl);
                            builder.setItems(new CharSequence[]{
                                    LocaleController.getString(R.string.OpenInTelegramBrowser),
                                    LocaleController.getString(R.string.OpenInSystemBrowser),
                                    LocaleController.getString(R.string.Copy)
                            }, (dialog, which) -> {
                                if (which == 0) {
                                    loadUrl(url);
                                } else if (which == 1) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                        intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
                                        intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                                        getContext().startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                        loadUrl(url);
                                    }
                                } else if (which == 2) {
                                    AndroidUtilities.addToClipboard(url);
                                    if (botWebViewContainer != null) {
                                        botWebViewContainer.showLinkCopiedBulletin();
                                    }
                                }
                            });
                            currentSheet = builder.show();
                        });

                        return true;
                    } else if (result.getType() == HitTestResult.IMAGE_TYPE) {
                        final String imageUrl = result.getExtra();

                        AndroidUtilities.runOnUIThread(() -> {

                            BottomSheet.Builder builder = new BottomSheet.Builder(getContext(), false, null);
                            String formattedUrl = imageUrl;
                            try {
                                try {
                                    Uri uri = Uri.parse(formattedUrl);
                                    formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                                } catch (Exception e) {
                                    FileLog.e(e, false);
                                }
                                formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            builder.setTitleMultipleLines(true);
                            builder.setTitle(formattedUrl);
                            builder.setItems(new CharSequence[]{
                                    LocaleController.getString(R.string.OpenInSystemBrowser),
                                    LocaleController.getString(R.string.AccActionDownload),
                                    LocaleController.getString(R.string.CopyLink)
                            }, (dialog, which) -> {
                                if (which == 0) {
                                    try {
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl));
                                        intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
                                        intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                                        getContext().startActivity(intent);
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                        loadUrl(imageUrl);
                                    }
                                } else if (which == 1) {
                                    try {
                                        String filename = URLUtil.guessFileName(imageUrl, null, "image/*");
                                        if (filename == null) {
                                            filename = "image.png";
                                        }

                                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
                                        request.setMimeType("image/*");
                                        request.setDescription(getString(R.string.WebDownloading));
                                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                                        DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                                        if (downloadManager != null) {
                                            downloadManager.enqueue(request);
                                        }

                                        if (botWebViewContainer != null) {
                                            BulletinFactory.of(botWebViewContainer, botWebViewContainer.resourcesProvider)
                                                    .createSimpleBulletin(R.raw.ic_download, AndroidUtilities.replaceTags(formatString(R.string.WebDownloadingFile, filename)))
                                                    .show(true);
                                        }
                                    } catch (Exception e2) {
                                        FileLog.e(e2);
                                    }
                                } else if (which == 2) {
                                    AndroidUtilities.addToClipboard(imageUrl);
                                    if (botWebViewContainer != null) {
                                        botWebViewContainer.showLinkCopiedBulletin();
                                    }
                                }
                            });
                            currentSheet = builder.show();
                        });

                        return true;
                    }
                    return false;
                }
            });

            setWebViewClient(new WebViewClient() {

                private boolean firstRequest = true;
                @Nullable
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        d("shouldInterceptRequest " + (request == null ? null : request.getUrl()));
                        if (request != null && isTonsite(request.getUrl())) {
                            d("proxying ton");
                            firstRequest = false;
                            return proxyTON(request);
                        }
                        if (!bot && opener != null && firstRequest) {
                            HttpURLConnection connection = null;
                            try {
                                URL connectionUrl = new URL(request.getUrl().toString());
                                connection = (HttpURLConnection) connectionUrl.openConnection();
                                connection.setRequestMethod(request.getMethod());
                                if (request.getRequestHeaders() != null) {
                                    for (Map.Entry<String, String> e: request.getRequestHeaders().entrySet()) {
                                        connection.setRequestProperty(e.getKey(), e.getValue());
                                    }
                                }
                                connection.connect();
                                HashMap<String, String> headers = new HashMap<>();
                                for (Map.Entry<String, List<String>> e: connection.getHeaderFields().entrySet()) {
                                    final String key = e.getKey();
                                    if (key == null) continue;
                                    headers.put(key, TextUtils.join(", ", e.getValue()));
                                    if (!dangerousUrl && (
                                        "cross-origin-resource-policy".equals(key.toLowerCase()) ||
                                        "cross-origin-embedder-policy".equals(key.toLowerCase())
                                    )) {
                                        for (String val : e.getValue()) {
                                            if (val == null) continue;
                                            if (!("unsafe-none".equals(val.toLowerCase()) || "same-site".equals(val.toLowerCase()))) {
                                                d("<!> dangerous header CORS policy: " + key + ": " + val + " from " + request.getMethod() + " " + request.getUrl());
                                                dangerousUrl = true;
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    if (botWebViewContainer != null) {
                                                        botWebViewContainer.onURLChanged(urlFallback, !canGoBack(), !canGoForward());
                                                    }
                                                });
                                                break;
                                            }
                                        }
                                    }
                                }
                                String contentType = connection.getContentType();
                                String encoding = connection.getContentEncoding();
                                if (contentType.indexOf("; ") >= 0) {
                                    String[] parts = contentType.split("; ");
                                    if (!TextUtils.isEmpty(parts[0])) {
                                        contentType = parts[0];
                                    }
                                    for (int i = 1; i < parts.length; ++i) {
                                        if (parts[i].startsWith("charset=")) {
                                            encoding = parts[i].substring(8);
                                        }
                                    }
                                }
                                firstRequest = false;
                                return new WebResourceResponse(
                                    contentType,
                                    encoding,
                                    connection.getResponseCode(),
                                    connection.getResponseMessage(),
                                    headers,
                                    connection.getInputStream()
                                );
                            } catch (Exception e) {
                                FileLog.e(e);
                                if (connection != null) {
                                    connection.disconnect();
                                }
                            }
                        }
                    }
                    firstRequest = false;
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public void onPageCommitVisible(WebView view, String url) {
                    if (MyWebView.this.whenPageLoaded != null) {
                        Runnable callback = MyWebView.this.whenPageLoaded;
                        MyWebView.this.whenPageLoaded = null;
                        callback.run();
                    }
                    d("onPageCommitVisible " + url);
                    if (!bot) {
                        injectedJS = true;
                        evaluateJS(readRes(R.raw.webview_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
                        evaluateJS(readRes(R.raw.webview_share));
                    } else {
                        injectedJS = true;
                        evaluateJS(readRes(R.raw.webview_app_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
                    }
                    super.onPageCommitVisible(view, url);
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    if (!bot && (currentHistoryEntry == null || !TextUtils.equals(currentHistoryEntry.url, url))) {
                        currentHistoryEntry = new BrowserHistory.Entry();
                        currentHistoryEntry.id = Utilities.fastRandom.nextLong();
                        currentHistoryEntry.time = System.currentTimeMillis();
                        currentHistoryEntry.url = magic2tonsite(getUrl());
                        currentHistoryEntry.meta = WebMetadataCache.WebMetadata.from(MyWebView.this);
                        BrowserHistory.pushHistory(currentHistoryEntry);
                    }
                    d("doUpdateVisitedHistory " + url + " " + isReload);
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : url, !canGoBack(), !canGoForward());
                    }
                    super.doUpdateVisitedHistory(view, url, isReload);
                }

                @Nullable
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    d("shouldInterceptRequest " + url);
                    if (isTonsite(url)) {
                        d("proxying ton");
                        return proxyTON("GET", url, null);
                    }
                    return super.shouldInterceptRequest(view, url);
                }

                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        d("onRenderProcessGone priority=" + (detail == null ? null : detail.rendererPriorityAtExit()) + " didCrash=" + (detail == null ? null : detail.didCrash()));
                    } else {
                        d("onRenderProcessGone");
                    }
                    try {
                        if (!AndroidUtilities.isSafeToShow(getContext())) {
                            return true;
                        }
                        new AlertDialog.Builder(getContext(), botWebViewContainer == null ? null : botWebViewContainer.resourcesProvider)
                                .setTitle(getString(R.string.ChromeCrashTitle))
                                .setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.ChromeCrashMessage), () -> Browser.openUrl(getContext(), "https://play.google.com/store/apps/details?id=com.google.android.webview")))
                                .setPositiveButton(getString(R.string.OK), null)
                                .setOnDismissListener(d -> {
                                    if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                                        botWebViewContainer.delegate.onCloseRequested(null);
                                    }
                                })
                                .show();
                        return true;
                    } catch (Exception e) {
                        FileLog.e(e);
                        return false;
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url == null) return false;
                    if (url.trim().startsWith("sms:")) {
                        return false;
                    }
                    if (url.trim().startsWith("tel:")) {
                        if (opener != null) {
                            if (botWebViewContainer.delegate != null) {
                                botWebViewContainer.delegate.onInstantClose();
                            } else if (onCloseListener != null) {
                                onCloseListener.run();
                                onCloseListener = null;
                            }
                        }
                        Browser.openUrl(context, url);
                        return true;
                    }
                    Uri uriNew = Uri.parse(url);
                    if (!bot) {
                        if (Browser.openInExternalApp(context, url, true)) {
                            d("shouldOverrideUrlLoading("+url+") = true (openInExternalBrowser)");
                            if (!isPageLoaded && !canGoBack()) {
                                if (botWebViewContainer.delegate != null) {
                                    botWebViewContainer.delegate.onInstantClose();
                                } else if (onCloseListener != null) {
                                    onCloseListener.run();
                                    onCloseListener = null;
                                }
                            }
                            return true;
                        }
                        if (url.startsWith("intent://") || uriNew != null && uriNew.getScheme() != null && uriNew.getScheme().equalsIgnoreCase("intent")) {
                            try {
                                final Intent intent = Intent.parseUri(uriNew.toString(), Intent.URI_INTENT_SCHEME);
                                final String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                                if (!TextUtils.isEmpty(fallbackUrl)) {
                                    loadUrl(fallbackUrl);
                                    return true;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        if (uriNew != null && uriNew.getScheme() != null && !("https".equals(uriNew.getScheme()) || "http".equals(uriNew.getScheme()) || "tonsite".equals(uriNew.getScheme()))) {
                            d("shouldOverrideUrlLoading("+url+") = true (browser open)");
                            Browser.openUrl(getContext(), uriNew);
                            return true;
                        }
                    }
                    if (botWebViewContainer != null && Browser.isInternalUri(uriNew, null)) {
                        if (!bot && "1".equals(uriNew.getQueryParameter("embed")) && "t.me".equals(uriNew.getAuthority())) {
                            return false;
                        }
                        if (MessagesController.getInstance(botWebViewContainer.currentAccount).webAppAllowedProtocols != null &&
                            MessagesController.getInstance(botWebViewContainer.currentAccount).webAppAllowedProtocols.contains(uriNew.getScheme())) {
                            if (opener != null) {
                                if (botWebViewContainer.delegate != null) {
                                    botWebViewContainer.delegate.onInstantClose();
                                } else if (onCloseListener != null) {
                                    onCloseListener.run();
                                    onCloseListener = null;
                                }
                                if (opener.botWebViewContainer != null && opener.botWebViewContainer.delegate != null) {
                                    opener.botWebViewContainer.delegate.onCloseToTabs();
                                }
                            }
                            botWebViewContainer.onOpenUri(uriNew);
                        }
                        d("shouldOverrideUrlLoading("+url+") = true");
                        return true;
                    }
                    if (uriNew != null) {
                        currentUrl = uriNew.toString();
                    }
                    d("shouldOverrideUrlLoading("+url+") = false");
                    return false;
                }

                private final Runnable resetErrorRunnable = () -> {
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onErrorShown(errorShown = false, 0, null);
                    }
                };

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    getSettings().setMediaPlaybackRequiresUserGesture(true);
                    if (currentSheet != null) {
                        currentSheet.dismiss();
                        currentSheet = null;
                    }
                    currentHistoryEntry = null;
                    currentUrl = url;
                    lastSiteName = null;
                    lastActionBarColorGot = false;
                    lastBackgroundColorGot = false;
                    lastFaviconGot = false;
                    d("onPageStarted " + url);
                    if (botWebViewContainer != null && errorShown && (errorShownAt == null || !TextUtils.equals(errorShownAt, url))) {
                        AndroidUtilities.runOnUIThread(resetErrorRunnable, 40);
                    }
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : url, !canGoBack(), !canGoForward());
                    }
                    super.onPageStarted(view, url, favicon);
                    injectedJS = false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    isPageLoaded = true;
                    boolean animated = true;
                    if (MyWebView.this.whenPageLoaded != null) {
                        Runnable callback = MyWebView.this.whenPageLoaded;
                        MyWebView.this.whenPageLoaded = null;
                        callback.run();
                        animated = false;
                    }
                    d("onPageFinished");
                    if (botWebViewContainer != null) {
                        botWebViewContainer.setPageLoaded(url, animated);
                    } else {
                        d("onPageFinished: no container");
                    }
                    if (!bot) {
                        injectedJS = true;
                        evaluateJS(readRes(R.raw.webview_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
                        evaluateJS(readRes(R.raw.webview_share));
                    } else {
                        injectedJS = true;
                        evaluateJS(readRes(R.raw.webview_app_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
                    }
                    saveHistory();
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : getUrl(), !canGoBack(), !canGoForward());
                    }
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                        CookieManager.getInstance().flush();
//                    }
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        d("onReceivedError: " + error.getErrorCode() + " " + error.getDescription());
                        if (botWebViewContainer != null && (request == null || request.isForMainFrame())) {
                            AndroidUtilities.cancelRunOnUIThread(resetErrorRunnable);
                            lastSiteName = null;
                            lastActionBarColorGot = false;
                            lastBackgroundColorGot = false;
                            lastFaviconGot = false;
                            lastTitleGot = false;
                            errorShownAt = request == null || request.getUrl() == null ? getUrl() : request.getUrl().toString();
                            botWebViewContainer.onTitleChanged(lastTitle = null);
                            botWebViewContainer.onFaviconChanged(lastFavicon = null);
                            botWebViewContainer.onErrorShown(errorShown = true, error.getErrorCode(), error.getDescription() == null ? null : error.getDescription().toString());
                        }
                    }
                    super.onReceivedError(view, request, error);
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    d("onReceivedError: " + errorCode + " " + description + " url=" + failingUrl);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        if (botWebViewContainer != null) {
                            AndroidUtilities.cancelRunOnUIThread(resetErrorRunnable);
                            lastSiteName = null;
                            lastActionBarColorGot = false;
                            lastBackgroundColorGot = false;
                            lastFaviconGot = false;
                            lastTitleGot = false;
                            errorShownAt = getUrl();
                            botWebViewContainer.onTitleChanged(lastTitle = null);
                            botWebViewContainer.onFaviconChanged(lastFavicon = null);
                            botWebViewContainer.onErrorShown(errorShown = true, errorCode, description);
                        }
                    }
                    super.onReceivedError(view, errorCode, description, failingUrl);
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                    super.onReceivedHttpError(view, request, errorResponse);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        d("onReceivedHttpError: statusCode=" + (errorResponse == null ? null : errorResponse.getStatusCode()) + " request=" + (request == null ? null : request.getUrl()));
                        if (botWebViewContainer != null && (request == null || request.isForMainFrame()) && errorResponse != null && TextUtils.isEmpty(errorResponse.getMimeType())) {
                            AndroidUtilities.cancelRunOnUIThread(resetErrorRunnable);
                            lastSiteName = null;
                            lastActionBarColorGot = false;
                            lastBackgroundColorGot = false;
                            lastFaviconGot = false;
                            lastTitleGot = false;
                            errorShownAt = request == null || request.getUrl() == null ? getUrl() : request.getUrl().toString();
                            botWebViewContainer.onTitleChanged(lastTitle = null);
                            botWebViewContainer.onFaviconChanged(lastFavicon = null);
                            botWebViewContainer.onErrorShown(errorShown = true, errorResponse.getStatusCode(), errorResponse.getReasonPhrase());
                        }
                    }
                }

                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    d("onReceivedSslError: error="+error+" url=" + (error == null ? null : error.getUrl()));
                    handler.cancel();
                    super.onReceivedSslError(view, handler, error);
                }
            });
            setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    final boolean[] done = new boolean[] { false };
                    new AlertDialog.Builder(context, botWebViewContainer == null ? null : botWebViewContainer.resourcesProvider)
                        .setTitle(bot ? DialogObject.getName(botId) : formatString(R.string.WebsiteSays, url))
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                            if (!done[0]) {
                                done[0] = true;
                                result.confirm();
                            }
                        })
                        .setOnDismissListener(d -> {
                            if (!done[0]) {
                                done[0] = true;
                                result.cancel();
                            }
                        }).show();
                    return true;
                }

                @Override
                public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                    final boolean[] done = new boolean[] { false };
                    new AlertDialog.Builder(context, botWebViewContainer == null ? null : botWebViewContainer.resourcesProvider)
                        .setTitle(bot ? DialogObject.getName(botId) : formatString(R.string.WebsiteSays, url))
                        .setMessage(message)
                        .setNegativeButton(getString(R.string.Cancel), (dialog, which) -> {
                            if (!done[0]) {
                                done[0] = true;
                                result.cancel();
                            }
                        })
                        .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                            if (!done[0]) {
                                done[0] = true;
                                result.confirm();
                            }
                        })
                        .setOnDismissListener(d -> {
                            if (!done[0]) {
                                done[0] = true;
                                result.cancel();
                            }
                        })
                        .show();
                    return true;
                }

                @Override
                public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                    final Theme.ResourcesProvider resourcesProvider = botWebViewContainer == null ? null : botWebViewContainer.resourcesProvider;
                    final boolean[] done = new boolean[] { false };
                    AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider)
                        .setTitle(bot ? DialogObject.getName(botId) : formatString(R.string.WebsiteSays, url))
                        .setMessage(message);

                    EditTextCaption editText = new EditTextCaption(context, resourcesProvider);
                    editText.lineYFix = true;
                    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                    editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
                    editText.setFocusable(true);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
                    editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    editText.setBackgroundDrawable(null);
                    editText.setPadding(0, dp(6), 0, dp(6));
                    editText.setText(defaultValue);

                    LinearLayout container = new LinearLayout(context);
                    container.setOrientation(LinearLayout.VERTICAL);
                    container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

                    builder.makeCustomMaxHeight();
                    builder.setView(container);
                    builder.setWidth(dp(292));

                    builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> {
                        if (!done[0]) {
                            done[0] = true;
                            result.cancel();
                        }
                    });
                    builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                        if (!done[0]) {
                            done[0] = true;
                            result.confirm(editText.getText().toString());
                        }
                    });
                    builder.setOnDismissListener(d -> {
                        if (!done[0]) {
                            done[0] = true;
                            result.cancel();
                        }
                    });
                    builder.overrideDismissListener(dismiss -> {
                        AndroidUtilities.hideKeyboard(editText);
                        AndroidUtilities.runOnUIThread(dismiss, 80);
                    });
                    AlertDialog dialog = builder.show();
                    editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_DONE) {
                                if (!done[0]) {
                                    done[0] = true;
                                    result.confirm(editText.getText().toString());
                                    dialog.dismiss();
                                }
                                return true;
                            }
                            return false;
                        }
                    });
                    AndroidUtilities.runOnUIThread(() -> {
                        editText.requestFocus();
                    });
                    return true;
                }

                private Dialog lastPermissionsDialog;

                @Override
                public void onReceivedIcon(WebView view, Bitmap icon) {
                    d("onReceivedIcon favicon=" + (icon == null ? "null" : icon.getWidth() + "x" + icon.getHeight()));
                    if (icon != null && (!TextUtils.equals(getUrl(), lastFaviconUrl) || lastFavicon == null || icon.getWidth() > lastFavicon.getWidth())) {
                        lastFavicon = icon;
                        lastFaviconUrl = getUrl();
                        lastFaviconGot = true;
                        saveHistory();
                    }
                    Bitmap lastFav = lastFavicons.get(getUrl());
                    if (icon != null && (lastFav == null || lastFav.getWidth() < icon.getWidth())) {
                        lastFavicons.put(getUrl(), icon);
                    }
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onFaviconChanged(icon);
                    }
                    super.onReceivedIcon(view, icon);
                }

                @Override
                public void onReceivedTitle(WebView view, String title) {
                    d("onReceivedTitle title=" + title);
                    if (!errorShown) {
                        lastTitleGot = true;
                        lastTitle = title;
                    }
                    if (botWebViewContainer != null) {
                        botWebViewContainer.onTitleChanged(title);
                    }
                    super.onReceivedTitle(view, title);
                }

                @Override
                public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
                    d("onReceivedTouchIconUrl url=" + url + " precomposed=" + precomposed);
                    super.onReceivedTouchIconUrl(view, url, precomposed);
                }

                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                    d("onCreateWindow isDialog=" + isDialog + " isUserGesture=" + isUserGesture + " resultMsg=" + resultMsg);
                    final String fromUrl = getUrl();
                    if (SharedConfig.inappBrowser) {
                        if (botWebViewContainer == null) return false;
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment == null) return false;
                        if (lastFragment.getParentLayout() instanceof ActionBarLayout) {
                            lastFragment = ((ActionBarLayout) lastFragment.getParentLayout()).getSheetFragment();
                        }
                        ArticleViewer articleViewer = lastFragment.createArticleViewer(true);
                        articleViewer.setOpener(MyWebView.this);
                        articleViewer.open((String) null);

                        MyWebView newWebView = articleViewer.getLastWebView();
                        if (!TextUtils.isEmpty(fromUrl)) {
                            newWebView.urlFallback = fromUrl;
                        }
                        d("onCreateWindow: newWebView=" + newWebView);
                        if (newWebView != null) {
                            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                            transport.setWebView(newWebView);
                            resultMsg.sendToTarget();

                            return true;
                        } else {
                            articleViewer.close(true, true);
                            return false;
                        }
                    } else {
                        WebView newWebView = new WebView(view.getContext());
                        newWebView.setWebViewClient(new WebViewClient() {
                            @Override
                            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    d("newWebView.onRenderProcessGone priority=" + (detail == null ? null : detail.rendererPriorityAtExit()) + " didCrash=" + (detail == null ? null : detail.didCrash()));
                                } else {
                                    d("newWebView.onRenderProcessGone");
                                }
                                try {
                                    if (!AndroidUtilities.isSafeToShow(getContext())) {
                                        return true;
                                    }
                                    new AlertDialog.Builder(getContext(), botWebViewContainer == null ? null : botWebViewContainer.resourcesProvider)
                                            .setTitle(getString(R.string.ChromeCrashTitle))
                                            .setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.ChromeCrashMessage), () -> Browser.openUrl(getContext(), "https://play.google.com/store/apps/details?id=com.google.android.webview")))
                                            .setPositiveButton(getString(R.string.OK), null)
                                            .setOnDismissListener(d -> {
                                                if (botWebViewContainer.delegate != null) {
                                                    botWebViewContainer.delegate.onCloseRequested(null);
                                                }
                                            })
                                            .show();
                                    return true;
                                } catch (Exception e) {
                                    FileLog.e(e);
                                    return false;
                                }
                            }

                            @Override
                            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                if (botWebViewContainer != null) {
                                    botWebViewContainer.onOpenUri(Uri.parse(url));
                                    newWebView.destroy();
                                }
                                return true;
                            }
                        });
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(newWebView);
                        resultMsg.sendToTarget();
                        return true;
                    }
                }

                @Override
                public void onCloseWindow(WebView window) {
                    d("onCloseWindow " + window);
                    if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                        botWebViewContainer.delegate.onCloseRequested(null);
                    } else if (onCloseListener != null) {
                        onCloseListener.run();
                        onCloseListener = null;
                    }
                    super.onCloseWindow(window);
                }

                @Override
                public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                    Activity activity = AndroidUtilities.findActivity(getContext());
                    if (activity == null) {
                        d("onShowFileChooser: no activity, false");
                        return false;
                    }
                    if (botWebViewContainer == null) {
                        d("onShowFileChooser: no container, false");
                        return false;
                    }

                    if (botWebViewContainer.mFilePathCallback != null) {
                        botWebViewContainer.mFilePathCallback.onReceiveValue(null);
                    }

                    botWebViewContainer.mFilePathCallback = filePathCallback;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        activity.startActivityForResult(fileChooserParams.createIntent(), REQUEST_CODE_WEB_VIEW_FILE);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        activity.startActivityForResult(Intent.createChooser(intent, getString(R.string.BotWebViewFileChooserTitle)), REQUEST_CODE_WEB_VIEW_FILE);
                    }

                    d("onShowFileChooser: true");
                    return true;
                }

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (botWebViewContainer != null && botWebViewContainer.webViewProgressListener != null) {
                        d("onProgressChanged " + newProgress + "%");
                        botWebViewContainer.webViewProgressListener.accept(newProgress / 100f);
                    } else {
                        d("onProgressChanged " + newProgress + "%: no container");
                    }
                }

                @Override
                public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                    if (botWebViewContainer == null || botWebViewContainer.parentActivity == null) {
                        d("onGeolocationPermissionsShowPrompt: no container");
                        callback.invoke(origin, false, false);
                        return;
                    }
                    d("onGeolocationPermissionsShowPrompt " + origin);
                    final String name = bot ? UserObject.getUserName(botWebViewContainer.botUser) : AndroidUtilities.getHostAuthority(getUrl());
                    lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(
                        botWebViewContainer.parentActivity,
                        botWebViewContainer.resourcesProvider,
                        new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                        R.raw.permission_request_location,
                        formatString(bot ? R.string.BotWebViewRequestGeolocationPermission : R.string.WebViewRequestGeolocationPermission, name),
                        formatString(bot ? R.string.BotWebViewRequestGeolocationPermissionWithHint : R.string.WebViewRequestGeolocationPermissionWithHint, name),
                        allow -> {
                            if (lastPermissionsDialog != null) {
                                lastPermissionsDialog = null;

                                if (allow) {
                                    botWebViewContainer.runWithPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, allowSystem -> {
                                        callback.invoke(origin, allowSystem, false);
                                        if (allowSystem) {
                                            botWebViewContainer.hasUserPermissions = true;
                                        }
                                    });
                                } else {
                                    callback.invoke(origin, false, false);
                                }
                            }
                        }
                    );
                    lastPermissionsDialog.show();
                }

                @Override
                public void onGeolocationPermissionsHidePrompt() {
                    if (lastPermissionsDialog != null) {
                        d("onGeolocationPermissionsHidePrompt: dialog.dismiss");
                        lastPermissionsDialog.dismiss();
                        lastPermissionsDialog = null;
                    } else {
                        d("onGeolocationPermissionsHidePrompt: no dialog");
                    }
                }

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    if (lastPermissionsDialog != null){
                        lastPermissionsDialog.dismiss();
                        lastPermissionsDialog = null;
                    }
                    if (botWebViewContainer == null) {
                        d("onPermissionRequest: no container");
                        request.deny();
                        return;
                    }
                    d("onPermissionRequest " + request);

                    final String name = bot ? UserObject.getUserName(botWebViewContainer.botUser) : AndroidUtilities.getHostAuthority(getUrl());
                    String[] resources = request.getResources();
                    if (resources.length == 1) {
                        String resource = resources[0];

                        if (botWebViewContainer.parentActivity == null) {
                            request.deny();
                            return;
                        }

                        switch (resource) {
                            case PermissionRequest.RESOURCE_AUDIO_CAPTURE: {
                                lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(
                                    botWebViewContainer.parentActivity,
                                    botWebViewContainer.resourcesProvider,
                                    new String[] {Manifest.permission.RECORD_AUDIO},
                                    R.raw.permission_request_microphone,
                                    formatString(bot ? R.string.BotWebViewRequestMicrophonePermission : R.string.WebViewRequestMicrophonePermission, name),
                                    formatString(bot ? R.string.BotWebViewRequestMicrophonePermissionWithHint : R.string.WebViewRequestMicrophonePermissionWithHint, name),
                                    allow -> {
                                        if (lastPermissionsDialog != null) {
                                            lastPermissionsDialog = null;

                                            if (allow) {
                                                botWebViewContainer.runWithPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, allowSystem -> {
                                                    if (allowSystem) {
                                                        request.grant(new String[] {resource});
                                                        botWebViewContainer.hasUserPermissions = true;
                                                    } else {
                                                        request.deny();
                                                    }
                                                });
                                            } else {
                                                request.deny();
                                            }
                                        }
                                    }
                                );
                                lastPermissionsDialog.show();
                                break;
                            }
                            case PermissionRequest.RESOURCE_VIDEO_CAPTURE: {
                                lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(
                                    botWebViewContainer.parentActivity,
                                    botWebViewContainer.resourcesProvider,
                                    new String[] {Manifest.permission.CAMERA},
                                    R.raw.permission_request_camera,
                                    formatString(bot ? R.string.BotWebViewRequestCameraPermission : R.string.WebViewRequestCameraPermission, name),
                                    formatString(bot ? R.string.BotWebViewRequestCameraPermissionWithHint : R.string.WebViewRequestCameraPermissionWithHint, name),
                                    allow -> {
                                        if (lastPermissionsDialog != null) {
                                            lastPermissionsDialog = null;

                                            if (allow) {
                                                botWebViewContainer.runWithPermissions(new String[] {Manifest.permission.CAMERA}, allowSystem -> {
                                                    if (allowSystem) {
                                                        request.grant(new String[] {resource});
                                                        botWebViewContainer.hasUserPermissions = true;
                                                    } else {
                                                        request.deny();
                                                    }
                                                });
                                            } else {
                                                request.deny();
                                            }
                                        }
                                    }
                                );
                                lastPermissionsDialog.show();
                                break;
                            }
                        }
                    } else if (
                            resources.length == 2 &&
                                    (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[0]) || PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resources[0])) &&
                                    (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[1]) || PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resources[1]))
                    ) {
                        lastPermissionsDialog = AlertsCreator.createWebViewPermissionsRequestDialog(
                            botWebViewContainer.parentActivity,
                            botWebViewContainer.resourcesProvider,
                            new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                            R.raw.permission_request_camera,
                            formatString(bot ? R.string.BotWebViewRequestCameraMicPermission : R.string.WebViewRequestCameraMicPermission, name),
                            formatString(bot ? R.string.BotWebViewRequestCameraMicPermissionWithHint : R.string.WebViewRequestCameraMicPermissionWithHint, name),
                            allow -> {
                                if (lastPermissionsDialog != null) {
                                    lastPermissionsDialog = null;

                                    if (allow) {
                                        botWebViewContainer.runWithPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, allowSystem -> {
                                            if (allowSystem) {
                                                request.grant(new String[] {resources[0], resources[1]});
                                                botWebViewContainer.hasUserPermissions = true;
                                            } else {
                                                request.deny();
                                            }
                                        });
                                    } else {
                                        request.deny();
                                    }
                                }
                            }
                        );
                        lastPermissionsDialog.show();
                    }
                }

                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onPermissionRequestCanceled(PermissionRequest request) {
                    if (lastPermissionsDialog != null) {
                        d("onPermissionRequestCanceled: dialog.dismiss");
                        lastPermissionsDialog.dismiss();
                        lastPermissionsDialog = null;
                    } else {
                        d("onPermissionRequestCanceled: no dialog");
                    }
                }

                @Nullable
                @Override
                public Bitmap getDefaultVideoPoster() {
                    return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
                }
            });
            setFindListener(new FindListener() {
                @Override
                public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
                    searchIndex = activeMatchOrdinal;
                    searchCount = numberOfMatches;
                    searchLoading = !isDoneCounting;
                    if (searchListener != null) {
                        searchListener.run();
                    }
                }
            });
            if (!bot) {
                setDownloadListener(new DownloadListener() {
                    private String getFilename(String url, String contentDisposition, String mimeType) {
                        try {
                            List<String> segments = Uri.parse(url).getPathSegments();
                            String lastSegment = segments.get(segments.size() - 1);
                            int index = lastSegment.lastIndexOf(".");
                            if (index > 0) {
                                String ext = lastSegment.substring(index + 1);
                                if (!TextUtils.isEmpty(ext))
                                    return lastSegment;
                            }
                        } catch (Exception e) {}
                        return URLUtil.guessFileName(url, contentDisposition, mimeType);
                    }

                    @Override
                    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                        d("onDownloadStart " + url + " " + userAgent + " " + contentDisposition + " " + mimeType + " " + contentLength);
                        try {
                            if (url.startsWith("blob:")) {
                                // we can't get blob binary from webview :(
                                return;
                            } else {
                                final String filename = AndroidUtilities.escape(getFilename(url, contentDisposition, mimeType));

                                final Runnable download = () -> {
                                    try {
                                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                                        request.setMimeType(mimeType);
                                        request.addRequestHeader("User-Agent", userAgent);
                                        request.setDescription(getString(R.string.WebDownloading));
                                        request.setTitle(filename);
                                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                                        DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                                        if (downloadManager != null) {
                                            downloadManager.enqueue(request);
                                        }

                                        if (botWebViewContainer != null) {
                                            BulletinFactory.of(botWebViewContainer, botWebViewContainer.resourcesProvider)
                                                    .createSimpleBulletin(R.raw.ic_download, AndroidUtilities.replaceTags(formatString(R.string.WebDownloadingFile, filename)))
                                                    .show(true);
                                        }
                                    } catch (Exception e2) {
                                        FileLog.e(e2);
                                    }
                                };
                                if (!DownloadController.getInstance(UserConfig.selectedAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT, contentLength)) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                    builder.setTitle(getString(R.string.WebDownloadAlertTitle));
                                    builder.setMessage(AndroidUtilities.replaceTags(contentLength > 0 ? formatString(R.string.WebDownloadAlertInfoWithSize, filename, AndroidUtilities.formatFileSize(contentLength)) : formatString(R.string.WebDownloadAlertInfo, filename)));
                                    builder.setPositiveButton(getString(R.string.WebDownloadAlertYes), (di, w) -> download.run());
                                    builder.setNegativeButton(getString(R.string.Cancel), null);
                                    AlertDialog alertDialog = builder.show();
                                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                                    if (button != null) {
                                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                                    }
                                } else {
                                    download.run();
                                }
                            }
                        } catch(Exception e){
                            FileLog.e(e);
                        }
                    }
                });
            }
        }

        private void saveHistory() {
            if (bot) return;
            WebMetadataCache.WebMetadata meta = WebMetadataCache.WebMetadata.from(MyWebView.this);
            WebMetadataCache.getInstance().save(meta);
            if (currentHistoryEntry != null && meta != null) {
                currentHistoryEntry.meta = meta;
                BrowserHistory.pushHistory(currentHistoryEntry);
            }
        }

        private int searchIndex;
        private int searchCount;
        private boolean searchLoading;
        private Runnable searchListener;

        public void search(String text, Runnable listener) {
            searchLoading = true;
            this.searchListener = listener;
            findAllAsync(text);
        }

        public int getSearchIndex() {
            return searchIndex;
        }

        public int getSearchCount() {
            return searchCount;
        }

        public boolean lastTitleGot;
        public String lastTitle;
        private String lastFaviconUrl;
        public boolean lastFaviconGot;
        public boolean injectedJS;
        public Bitmap lastFavicon;
        private String lastUrl;
        private HashMap<String, Bitmap> lastFavicons = new HashMap<>();
        private boolean loading;

        public String getTitle() {
            if (currentWarning != null) return "";
            return lastTitle;
        }
        public void setTitle(String title) {
            lastTitle = title;
        }

        public String getOpenURL() {
            return openedByUrl;
        }

        @Nullable
        @Override
        public String getUrl() {
            if (currentWarning != null) return currentWarning.url;
            if (dangerousUrl) return urlFallback;
//            if (errorShown) return lastUrl;
            return lastUrl = super.getUrl();
        }

        public boolean isUrlDangerous() {
            return dangerousUrl || currentWarning != null;
        }

        public Bitmap getFavicon() {
            if (errorShown) return null;
            return lastFavicon;
        }

        public Bitmap getFavicon(String url) {
            return lastFavicons.get(url);
        }

        private BotWebViewContainer botWebViewContainer;
        private WebViewScrollListener webViewScrollListener;
        private Runnable onCloseListener;

        public void setContainers(BotWebViewContainer botWebViewContainer, WebViewScrollListener webViewScrollListener) {
            d("setContainers(" + botWebViewContainer + ", " + webViewScrollListener + ")");
            final boolean attachedAgain = this.botWebViewContainer == null && botWebViewContainer != null;
            this.botWebViewContainer = botWebViewContainer;
            this.webViewScrollListener = webViewScrollListener;
            if (attachedAgain) {
                evaluateJS("window.__tg__postBackgroundChange()");
            }
        }

        public void setCloseListener(Runnable closeListener) {
            onCloseListener = closeListener;
        }

        public void evaluateJS(String script) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                evaluateJavascript(script, value -> {});
            } else {
                try {
                    loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    loadUrl("javascript:" + URLEncoder.encode(script));
                }
            }
        }

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

        public float getScrollProgress() {
            final float scrollHeight = Math.max(1, computeVerticalScrollRange() - computeVerticalScrollExtent());
            if (scrollHeight <= getHeight()) {
                return 0f;
            }
            return Utilities.clamp01((float) getScrollY() / scrollHeight);
        }

        public void setScrollProgress(float progress) {
            setScrollY((int) (progress * Math.max(1, computeVerticalScrollRange() - computeVerticalScrollExtent())));
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
            if (botWebViewContainer == null) {
                d("onCheckIsTextEditor: no container");
                return false;
            }
            final boolean r = botWebViewContainer.isFocusable();
            d("onCheckIsTextEditor: " + r);
            return r;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                botWebViewContainer.lastClickMs = System.currentTimeMillis();
                getSettings().setMediaPlaybackRequiresUserGesture(false);
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onAttachedToWindow() {
            d("attached");
            AndroidUtilities.checkAndroidTheme(getContext(), true);
            super.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            d("detached");
            AndroidUtilities.checkAndroidTheme(getContext(), false);
            super.onDetachedFromWindow();
        }

        @Override
        public void destroy() {
            d("destroy");
            super.destroy();
        }

        @Override
        public void loadUrl(@NonNull String url) {
            if (currentSheet != null) {
                currentSheet.dismiss();
                currentSheet = null;
            }
            final String ourl = url;
            checkCachedMetaProperties(url);
            openedByUrl = url;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                CookieManager.getInstance().flush();
//            }
            url = tonsite2magic(url);
            currentUrl = url;
            d("loadUrl " + url);
            super.loadUrl(url);
            if (botWebViewContainer != null) {
                botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : url, !canGoBack(), !canGoForward());
            }
        }

        @Override
        public void loadUrl(@NonNull String url, @NonNull Map<String, String> additionalHttpHeaders) {
            if (currentSheet != null) {
                currentSheet.dismiss();
                currentSheet = null;
            }
            final String ourl = url;
            checkCachedMetaProperties(url);
            openedByUrl = url;
            url = tonsite2magic(url);
            currentUrl = url;
            d("loadUrl " + url + " " + additionalHttpHeaders);
            super.loadUrl(url, additionalHttpHeaders);
            if (botWebViewContainer != null) {
                botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : url, !canGoBack(), !canGoForward());
            }
        }

        public void loadUrl(String url, WebMetadataCache.WebMetadata meta) {
            if (currentSheet != null) {
                currentSheet.dismiss();
                currentSheet = null;
            }
            final String ourl = url;
            applyCachedMeta(meta);
            openedByUrl = url;
            url = tonsite2magic(url);
            currentUrl = url;
            d("loadUrl " + url + " with cached meta");
            super.loadUrl(url);
            if (botWebViewContainer != null) {
                botWebViewContainer.onURLChanged(dangerousUrl ? urlFallback : url, !canGoBack(), !canGoForward());
            }
        }

        public void checkCachedMetaProperties(String url) {
            if (bot) return;
            String domain = AndroidUtilities.getHostAuthority(url, true);
            WebMetadataCache.WebMetadata meta = WebMetadataCache.getInstance().get(domain);
            applyCachedMeta(meta);
        }

        public boolean applyCachedMeta(WebMetadataCache.WebMetadata meta) {
            if (meta == null) return false;
            boolean foundTitle = false;
            int backgroundColor = 0xFFFFFFFF;
            if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                if (meta.actionBarColor != 0) {
                    botWebViewContainer.delegate.onWebAppBackgroundChanged(true, meta.actionBarColor);
                    lastActionBarColorGot = true;
                }
                if (meta.backgroundColor != 0) {
                    backgroundColor = meta.backgroundColor;
                    botWebViewContainer.delegate.onWebAppBackgroundChanged(false, meta.backgroundColor);
                    lastBackgroundColorGot = true;
                }
                if (meta.favicon != null) {
                    botWebViewContainer.onFaviconChanged(lastFavicon = meta.favicon);
                    lastFaviconGot = true;
                }
                if (!TextUtils.isEmpty(meta.sitename)) {
                    foundTitle = true;
                    lastSiteName = meta.sitename;
                    botWebViewContainer.onTitleChanged(lastTitle = meta.sitename);
                }
                if (SharedConfig.adaptableColorInBrowser) {
                    setBackgroundColor(backgroundColor);
                }
            }
            if (!foundTitle) {
                setTitle(null);
                if (botWebViewContainer != null) {
                    botWebViewContainer.onTitleChanged(null);
                }
            }
            return true;
        }

        @Override
        public void reload() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush();
            }
            d("reload");
            super.reload();
        }

        @Override
        public void loadData(@NonNull String data, @Nullable String mimeType, @Nullable String encoding) {
            openedByUrl = null;
            d("loadData " + data + " " + mimeType + " " + encoding);
            super.loadData(data, mimeType, encoding);
        }

        @Override
        public void loadDataWithBaseURL(@Nullable String baseUrl, @NonNull String data, @Nullable String mimeType, @Nullable String encoding, @Nullable String historyUrl) {
            openedByUrl = null;
            d("loadDataWithBaseURL " + baseUrl + " " + data + " " + mimeType + " " + encoding + " " + historyUrl);
            super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
        }

        @Override
        public void stopLoading() {
            d("stopLoading");
            super.stopLoading();
        }

        @Override
        public void stopNestedScroll() {
            d("stopNestedScroll");
            super.stopNestedScroll();
        }

        @Override
        public void postUrl(@NonNull String url, @NonNull byte[] postData) {
            d("postUrl " + url + " " + postData);
            super.postUrl(url, postData);
        }

        @Override
        public void onPause() {
            d("onPause");
            super.onPause();
        }

        @Override
        public void onResume() {
            d("onResume");
            super.onResume();
        }

        @Override
        public void pauseTimers() {
            d("pauseTimers");
            super.pauseTimers();
        }

        @Override
        public void resumeTimers() {
            d("resumeTimers");
            super.resumeTimers();
        }

        @Override
        public boolean canGoBack() {
            return currentWarning != null || super.canGoBack();
        }

        @Override
        public void goBack() {
            d("goBack");
            if (currentWarning != null) {
                currentWarning.back.run();
                return;
            }
            super.goBack();
        }

        @Override
        public void goForward() {
            d("goForward");
            super.goForward();
        }

        @Override
        public void clearHistory() {
            d("clearHistory");
            super.clearHistory();
        }

        @Override
        public void setFocusable(int focusable) {
            d("setFocusable " + focusable);
            super.setFocusable(focusable);
        }

        @Override
        public void setFocusable(boolean focusable) {
            d("setFocusable " + focusable);
            super.setFocusable(focusable);
        }

        @Override
        public void setFocusableInTouchMode(boolean focusableInTouchMode) {
            d("setFocusableInTouchMode " + focusableInTouchMode);
            super.setFocusableInTouchMode(focusableInTouchMode);
        }

        @Override
        public void setFocusedByDefault(boolean isFocusedByDefault) {
            d("setFocusedByDefault " + isFocusedByDefault);
            super.setFocusedByDefault(isFocusedByDefault);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
        }
    }

    private final int tag = tags++;
    public void d(String s) {
        FileLog.d("[webviewcontainer] #" + tag + " " + s);
    }

    private static HashMap<String, String> rotatedTONHosts;

    private static String tonsite2magic(String url) {
        if (url == null) return url;
        final Uri uri = Uri.parse(url);
        if (isTonsite(uri)) {
            String tonsite_host = AndroidUtilities.getHostAuthority(url);
            try {
                tonsite_host = IDN.toASCII(tonsite_host, IDN.ALLOW_UNASSIGNED);
            } catch (Exception e) {}
            String magic_host = rotateTONHost(tonsite_host);
            if (rotatedTONHosts == null) rotatedTONHosts = new HashMap<>();
            rotatedTONHosts.put(magic_host, tonsite_host);
            url = Browser.replaceHostname(Uri.parse(url), magic_host, "https");
        }
        return url;
    }

    public static String magic2tonsite(String url) {
        if (rotatedTONHosts == null) return url;
        if (url == null) return url;
        String host = AndroidUtilities.getHostAuthority(url);
        if (host == null || !host.endsWith("." + MessagesController.getInstance(UserConfig.selectedAccount).tonProxyAddress)) {
            return url;
        }
        String tonsite_host = rotatedTONHosts.get(host);
        if (tonsite_host == null) return url;
        return Browser.replace(Uri.parse(url), "tonsite", null, tonsite_host, null);
    }

    private static JSONObject obj(String key1, Object value) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(key1, value);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject obj(String key1, Object value, String key2, Object value2) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(key1, value);
            obj.put(key2, value2);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject obj(String key1, Object value, String key2, Object value2, String key3, Object value3) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(key1, value);
            obj.put(key2, value2);
            obj.put(key3, value3);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject obj(String key1, Object value, String key2, Object value2, String key3, Object value3, String key4, Object value4) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(key1, value);
            obj.put(key2, value2);
            obj.put(key3, value3);
            obj.put(key4, value4);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }
}

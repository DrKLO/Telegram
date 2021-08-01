package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BringAppForegroundService;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.PhotoViewer;

import java.util.HashMap;
import java.util.Locale;

public class PhotoViewerWebView extends FrameLayout {

    private int currentAccount = UserConfig.selectedAccount;
    private PipVideoView pipVideoView;

    private WebView webView;
    private View progressBarBlackBackground;
    private RadialProgressView progressBar;
    private View pipItem;

    private boolean isYouTube;
    private TLRPC.WebPage currentWebpage;

    private float playbackSpeed;
    private boolean setPlaybackSpeed;

    private class YoutubeProxy {
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            if ("loaded".equals(eventName)) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressBar.setVisibility(View.INVISIBLE);
                    progressBarBlackBackground.setVisibility(View.INVISIBLE);
                    if (setPlaybackSpeed) {
                        setPlaybackSpeed = false;
                        setPlaybackSpeed(playbackSpeed);
                    }
                    pipItem.setEnabled(true);
                    pipItem.setAlpha(1.0f);
                });
            }
        }
    }

    private static final String youtubeFrame = "<!DOCTYPE html><html><head><style>" +
            "body { margin: 0; width:100%%; height:100%%;  background-color:#000; }" +
            "html { width:100%%; height:100%%; background-color:#000; }" +
            ".embed-container iframe," +
            ".embed-container object," +
            "   .embed-container embed {" +
            "       position: absolute;" +
            "       top: 0;" +
            "       left: 0;" +
            "       width: 100%% !important;" +
            "       height: 100%% !important;" +
            "   }" +
            "   </style></head><body>" +
            "   <div class=\"embed-container\">" +
            "       <div id=\"player\"></div>" +
            "   </div>" +
            "   <script src=\"https://www.youtube.com/iframe_api\"></script>" +
            "   <script>" +
            "   var player;" +
            "   var posted = false;" +
            "   YT.ready(function() {" +
            "       player = new YT.Player(\"player\", {" +
            "                              \"width\" : \"100%%\"," +
            "                              \"events\" : {" +
            "                              \"onReady\" : \"onReady\"," +
            "                              \"onError\" : \"onError\"," +
            "                              \"onStateChange\" : \"onStateChange\"," +
            "                              }," +
            "                              \"videoId\" : \"%1$s\"," +
            "                              \"height\" : \"100%%\"," +
            "                              \"playerVars\" : {" +
            "                              \"start\" : %2$d," +
            "                              \"rel\" : 1," +
            "                              \"showinfo\" : 0," +
            "                              \"modestbranding\" : 0," +
            "                              \"iv_load_policy\" : 3," +
            "                              \"autohide\" : 1," +
            "                              \"autoplay\" : 1," +
            "                              \"cc_load_policy\" : 1," +
            "                              \"playsinline\" : 1," +
            "                              \"controls\" : 1" +
            "                              }" +
            "                            });" +
            "        player.setSize(window.innerWidth, window.innerHeight);" +
            "    });" +
            "    function setPlaybackSpeed(speed) { " +
            "       player.setPlaybackRate(speed);" +
            "    }" +
            "    function onError(event) {" +
            "       if (!posted) {" +
            "            if (window.YoutubeProxy !== undefined) {" +
            "                   YoutubeProxy.postEvent(\"loaded\", null); " +
            "            }" +
            "            posted = true;" +
            "       }" +
            "    }" +
            "    function onStateChange(event) {" +
            "       if (event.data == YT.PlayerState.PLAYING && !posted) {" +
            "            if (window.YoutubeProxy !== undefined) {" +
            "                   YoutubeProxy.postEvent(\"loaded\", null); " +
            "            }" +
            "            posted = true;" +
            "       }" +
            "    }" +
            "    function onReady(event) {" +
            "       player.playVideo();" +
            "    }" +
            "    window.onresize = function() {" +
            "       player.setSize(window.innerWidth, window.innerHeight);" +
            "       player.playVideo();" +
            "    }" +
            "    </script>" +
            "</body>" +
            "</html>";

    @SuppressLint("SetJavaScriptEnabled")
    public PhotoViewerWebView(Context context, View pip) {
        super(context);

        pipItem = pip;
        webView = new WebView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                processTouch(event);
                return super.onTouchEvent(event);
            }
        };
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= 17) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isYouTube || Build.VERSION.SDK_INT < 17) {
                    progressBar.setVisibility(View.INVISIBLE);
                    progressBarBlackBackground.setVisibility(View.INVISIBLE);
                    pipItem.setEnabled(true);
                    pipItem.setAlpha(1.0f);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isYouTube) {
                    Browser.openUrl(view.getContext(), url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        progressBarBlackBackground = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                drawBlackBackground(canvas, getMeasuredWidth(), getMeasuredHeight());
            }
        };
        progressBarBlackBackground.setBackgroundColor(0xff000000);
        progressBarBlackBackground.setVisibility(View.INVISIBLE);
        addView(progressBarBlackBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressBar = new RadialProgressView(context);
        progressBar.setVisibility(View.INVISIBLE);
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }

    private void runJsCode(String code) {
        if (Build.VERSION.SDK_INT >= 21) {
            webView.evaluateJavascript(code, null);
        } else {
            try {
                webView.loadUrl("javascript:" + code);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    protected void processTouch(MotionEvent event) {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (webView.getParent() == this) {
            int w = currentWebpage.embed_width != 0 ? currentWebpage.embed_width : 100;
            int h = currentWebpage.embed_height != 0 ? currentWebpage.embed_height : 100;
            int viewWidth = MeasureSpec.getSize(widthMeasureSpec);
            int viewHeight = MeasureSpec.getSize(heightMeasureSpec);
            float minScale = Math.min(viewWidth / (float) w, viewHeight / (float) h);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) webView.getLayoutParams();
            layoutParams.width = (int) (w * minScale);
            layoutParams.height = (int) (h * minScale);
            layoutParams.topMargin = (viewHeight - layoutParams.height) / 2;
            layoutParams.leftMargin = (viewWidth - layoutParams.width) / 2;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected void drawBlackBackground(Canvas canvas, int w, int h) {

    }

    public boolean isLoaded() {
        return progressBar.getVisibility() != View.VISIBLE;
    }

    public PipVideoView openInPip() {
        boolean inAppOnly = isYouTube && "inapp".equals(MessagesController.getInstance(currentAccount).youtubePipType);
        if (!inAppOnly && !checkInlinePermissions()) {
            return null;
        }
        if (progressBar.getVisibility() == View.VISIBLE) {
            return null;
        }
        boolean animated = false;
        pipVideoView = new PipVideoView(inAppOnly);
        pipVideoView.show((Activity) getContext(), PhotoViewer.getInstance(), currentWebpage.embed_width != 0 && currentWebpage.embed_height != 0 ? currentWebpage.embed_width / (float) currentWebpage.embed_height : 1.0f, 0, webView);
        return pipVideoView;
    }

    public void setPlaybackSpeed(float speed) {
        playbackSpeed = speed;
        if (progressBar.getVisibility() != View.VISIBLE) {
            if (isYouTube) {
                runJsCode("setPlaybackSpeed(" + speed + ");");
            }
        } else {
            setPlaybackSpeed = true;
        }
    }

    @SuppressLint("AddJavascriptInterface")
    public void init(int seekTime, TLRPC.WebPage webPage) {
        currentWebpage = webPage;
        String currentYoutubeId = WebPlayerView.getYouTubeVideoId(webPage.embed_url);
        String originalUrl = webPage.url;
        requestLayout();

        try {
            if (currentYoutubeId != null) {
                progressBarBlackBackground.setVisibility(View.VISIBLE);
                isYouTube = true;
                if (Build.VERSION.SDK_INT >= 17) {
                    webView.addJavascriptInterface(new YoutubeProxy(), "YoutubeProxy");
                }
                int seekToTime = 0;
                if (originalUrl != null) {
                    try {
                        Uri uri = Uri.parse(originalUrl);
                        String t = seekTime > 0 ? "" + seekTime : null;
                        if (t == null) {
                            t = uri.getQueryParameter("t");
                            if (t == null) {
                                t = uri.getQueryParameter("time_continue");
                            }
                        }
                        if (t != null) {
                            if (t.contains("m")) {
                                String[] arg = t.split("m");
                                seekToTime = Utilities.parseInt(arg[0]) * 60 + Utilities.parseInt(arg[1]);
                            } else {
                                seekToTime = Utilities.parseInt(t);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                webView.loadDataWithBaseURL("https://messenger.telegram.org/", String.format(Locale.US, youtubeFrame, currentYoutubeId, seekToTime), "text/html", "UTF-8", "https://youtube.com");
            } else {
                HashMap<String, String> args = new HashMap<>();
                args.put("Referer", "messenger.telegram.org");
                webView.loadUrl(webPage.embed_url, args);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        pipItem.setEnabled(false);
        pipItem.setAlpha(0.5f);

        progressBar.setVisibility(View.VISIBLE);
        if (currentYoutubeId != null) {
            progressBarBlackBackground.setVisibility(View.VISIBLE);
        }
        webView.setVisibility(View.VISIBLE);
        webView.setKeepScreenOn(true);
        if (currentYoutubeId != null && "disabled".equals(MessagesController.getInstance(currentAccount).youtubePipType)) {
            pipItem.setVisibility(View.GONE);
        }
    }

    public boolean checkInlinePermissions() {
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(getContext())) {
            return true;
        } else {
            AlertsCreator.createDrawOverlayPermissionDialog((Activity) getContext(), null);
        }
        return false;
    }

    public void exitFromPip() {
        if (webView == null || pipVideoView == null) {
            return;
        }
        if (ApplicationLoader.mainInterfacePaused) {
            try {
                getContext().startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }
        addView(webView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        pipVideoView.close();
        pipVideoView = null;
    }

    public void release() {
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.destroy();
    }
}

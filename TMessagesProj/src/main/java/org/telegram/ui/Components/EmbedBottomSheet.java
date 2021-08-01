/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BringAppForegroundService;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;

import java.util.HashMap;
import java.util.Locale;

public class EmbedBottomSheet extends BottomSheet {

    private WebView webView;
    private WebPlayerView videoView;
    private View customView;
    private FrameLayout fullscreenVideoContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View progressBarBlackBackground;
    private RadialProgressView progressBar;
    private Activity parentActivity;
    private PipVideoView pipVideoView;
    private LinearLayout imageButtonsContainer;
    private TextView copyTextButton;
    private FrameLayout containerLayout;
    private ImageView pipButton;
    private boolean isYouTube;

    private int[] position = new int[2];

    private OrientationEventListener orientationEventListener;
    private int lastOrientation = -1;

    private int width;
    private int height;
    private String openUrl;
    private boolean hasDescription;
    private String embedUrl;
    private int prevOrientation = -2;
    private boolean fullscreenedByButton;
    private boolean wasInLandscape;
    private boolean animationInProgress;

    private int waitingForDraw;

    private int seekTimeOverride;

    private class YoutubeProxy {
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            if ("loaded".equals(eventName)) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressBar.setVisibility(View.INVISIBLE);
                    progressBarBlackBackground.setVisibility(View.INVISIBLE);
                    pipButton.setEnabled(true);
                    pipButton.setAlpha(1.0f);
                });
            }
        }
    }

    private final String youtubeFrame = "<!DOCTYPE html><html><head><style>" +
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
            "   var observer;" +
            "   var videoEl;" +
            "   var playing;" +
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
            "    function hideControls() { " +
            "       playing = !videoEl.paused;" +
            "       videoEl.controls = 0;" +
            "       observer.observe(videoEl, {attributes: true});" +
            "    }" +
            "    function showControls() { " +
            "       playing = !videoEl.paused;" +
            "       observer.disconnect();" +
            "       videoEl.controls = 1;" +
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

    private OnShowListener onShowListener = new OnShowListener() {
        @Override
        public void onShow(DialogInterface dialog) {
            if (pipVideoView != null && videoView.isInline()) {
                videoView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        videoView.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
                    }
                });
            }
        }
    };

    @SuppressLint("StaticFieldLeak")
    private static EmbedBottomSheet instance;

    public static void show(Activity activity, MessageObject message, PhotoViewer.PhotoViewerProvider photoViewerProvider, String title, String description, String originalUrl, final String url, int w, int h, boolean keyboardVisible) {
        show(activity, message, photoViewerProvider, title, description, originalUrl, url, w, h, -1, keyboardVisible);
    }

    public static void show(Activity activity, MessageObject message, PhotoViewer.PhotoViewerProvider photoViewerProvider, String title, String description, String originalUrl, final String url, int w, int h, int seekTime, boolean keyboardVisible) {
        if (instance != null) {
            instance.destroy();
        }
        String youtubeId = message != null && message.messageOwner.media != null && message.messageOwner.media.webpage != null ? WebPlayerView.getYouTubeVideoId(url) : null;
        if (youtubeId != null) {
            PhotoViewer.getInstance().setParentActivity(activity);
            PhotoViewer.getInstance().openPhoto(message, seekTime, null, 0, 0, photoViewerProvider);
        } else {
            EmbedBottomSheet sheet = new EmbedBottomSheet(activity, title, description, originalUrl, url, w, h, seekTime);
            sheet.setCalcMandatoryInsets(keyboardVisible);
            sheet.show();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private EmbedBottomSheet(Context context, String title, String description, String originalUrl, final String url, int w, int h, int seekTime) {
        super(context, false);
        fullWidth = true;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);
        seekTimeOverride = seekTime;

        if (context instanceof Activity) {
            parentActivity = (Activity) context;
        }

        embedUrl = url;
        hasDescription = description != null && description.length() > 0;
        openUrl = originalUrl;
        width = w;
        height = h;
        if (width == 0 || height == 0) {
            width = AndroidUtilities.displaySize.x;
            height = AndroidUtilities.displaySize.y / 2;
        }

        fullscreenVideoContainer = new FrameLayout(context);
        fullscreenVideoContainer.setKeepScreenOn(true);
        fullscreenVideoContainer.setBackgroundColor(0xff000000);
        if (Build.VERSION.SDK_INT >= 21) {
            fullscreenVideoContainer.setFitsSystemWindows(true);
        }
        fullscreenVideoContainer.setOnTouchListener((v, event) -> true);
        container.addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fullscreenVideoContainer.setVisibility(View.INVISIBLE);

        containerLayout = new FrameLayout(context) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                try {
                    if ((pipVideoView == null || webView.getVisibility() != VISIBLE) && webView.getParent() != null) {
                        removeView(webView);
                        webView.stopLoading();
                        webView.loadUrl("about:blank");
                        webView.destroy();
                    }

                    if (!videoView.isInline() && pipVideoView == null) {
                        if (instance == EmbedBottomSheet.this) {
                            instance = null;
                        }

                        videoView.destroy();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
                float scale = width / (float) parentWidth;
                int h = (int) Math.min(height / scale, AndroidUtilities.displaySize.y / 2);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h + AndroidUtilities.dp(48 + 36 + (hasDescription ? 22 : 0)) + 1, MeasureSpec.EXACTLY));
            }
        };
        containerLayout.setOnTouchListener((v, event) -> true);
        setCustomView(containerLayout);

        webView = new WebView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                boolean result = super.onTouchEvent(event);
                if (result) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        setDisableScroll(false);
                    } else {
                        setDisableScroll(true);
                    }
                }
                return result;
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

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
                onShowCustomView(view, callback);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null || pipVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                exitFromPip();
                customView = view;
                getSheetContainer().setVisibility(View.INVISIBLE);
                fullscreenVideoContainer.setVisibility(View.VISIBLE);
                fullscreenVideoContainer.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                customViewCallback = callback;
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (customView == null) {
                    return;
                }

                getSheetContainer().setVisibility(View.VISIBLE);
                fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                fullscreenVideoContainer.removeView(customView);

                if (customViewCallback != null && !customViewCallback.getClass().getName().contains(".chromium.")) {
                    customViewCallback.onCustomViewHidden();
                }
                customView = null;
            }
        });

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
                    pipButton.setEnabled(true);
                    pipButton.setAlpha(1.0f);
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

        containerLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0)));

        videoView = new WebPlayerView(context, true, false, new WebPlayerView.WebPlayerViewDelegate() {
            @Override
            public void onInitFailed() {
                webView.setVisibility(View.VISIBLE);
                imageButtonsContainer.setVisibility(View.VISIBLE);
                copyTextButton.setVisibility(View.INVISIBLE);
                webView.setKeepScreenOn(true);
                videoView.setVisibility(View.INVISIBLE);
                videoView.getControlsView().setVisibility(View.INVISIBLE);
                videoView.getTextureView().setVisibility(View.INVISIBLE);
                if (videoView.getTextureImageView() != null) {
                    videoView.getTextureImageView().setVisibility(View.INVISIBLE);
                }
                videoView.loadVideo(null, null, null, null, false);
                HashMap<String, String> args = new HashMap<>();
                args.put("Referer", "messenger.telegram.org");
                try {
                    webView.loadUrl(embedUrl, args);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public TextureView onSwitchToFullscreen(View controlsView, boolean fullscreen, float aspectRatio, int rotation, boolean byButton) {
                if (fullscreen) {
                    fullscreenVideoContainer.setVisibility(View.VISIBLE);
                    fullscreenVideoContainer.setAlpha(1.0f);
                    fullscreenVideoContainer.addView(videoView.getAspectRatioView());
                    wasInLandscape = false;

                    fullscreenedByButton = byButton;
                    if (parentActivity != null) {
                        try {
                            prevOrientation = parentActivity.getRequestedOrientation();
                            if (byButton) {
                                WindowManager manager = (WindowManager) parentActivity.getSystemService(Activity.WINDOW_SERVICE);
                                int displayRotation = manager.getDefaultDisplay().getRotation();
                                if (displayRotation == Surface.ROTATION_270) {
                                    parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                                } else {
                                    parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                }
                            }
                            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } else {
                    fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                    fullscreenedByButton = false;

                    if (parentActivity != null) {
                        try {
                            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                            parentActivity.setRequestedOrientation(prevOrientation);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
                return null;
            }

            @Override
            public void onVideoSizeChanged(float aspectRatio, int rotation) {

            }

            @Override
            public void onInlineSurfaceTextureReady() {
                if (videoView.isInline()) {
                    dismissInternal();
                }
            }

            @Override
            public void prepareToSwitchInlineMode(boolean inline, final Runnable switchInlineModeRunnable, float aspectRatio, boolean animated) {
                if (inline) {
                    if (parentActivity != null) {
                        try {
                            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                            if (prevOrientation != -2) {
                                parentActivity.setRequestedOrientation(prevOrientation);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }

                    if (fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
                        containerView.setTranslationY(containerView.getMeasuredHeight() + AndroidUtilities.dp(10));
                        backDrawable.setAlpha(0);
                    }

                    setOnShowListener(null);
                    if (animated) {
                        TextureView textureView = videoView.getTextureView();
                        View controlsView = videoView.getControlsView();
                        ImageView textureImageView = videoView.getTextureImageView();

                        Rect rect = PipVideoView.getPipRect(aspectRatio);

                        float scale = rect.width / textureView.getWidth();

                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, scale),
                                ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, scale),
                                ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, rect.x),
                                ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, rect.y),
                                ObjectAnimator.ofFloat(textureView, View.SCALE_X, scale),
                                ObjectAnimator.ofFloat(textureView, View.SCALE_Y, scale),
                                ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, rect.x),
                                ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, rect.y),
                                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                                ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                                ObjectAnimator.ofFloat(fullscreenVideoContainer, View.ALPHA, 0),
                                ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0)
                        );
                        animatorSet.setInterpolator(new DecelerateInterpolator());
                        animatorSet.setDuration(250);
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
                                    fullscreenVideoContainer.setAlpha(1.0f);
                                    fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                                }
                                switchInlineModeRunnable.run();
                            }
                        });
                        animatorSet.start();
                    } else {
                        if (fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
                            fullscreenVideoContainer.setAlpha(1.0f);
                            fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                        }
                        switchInlineModeRunnable.run();
                        dismissInternal();
                    }
                } else {
                    if (ApplicationLoader.mainInterfacePaused) {
                        try {
                            parentActivity.startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }

                    if (animated) {
                        setOnShowListener(onShowListener);
                        Rect rect = PipVideoView.getPipRect(aspectRatio);

                        TextureView textureView = videoView.getTextureView();
                        ImageView textureImageView = videoView.getTextureImageView();
                        float scale = rect.width / textureView.getLayoutParams().width;
                        textureImageView.setScaleX(scale);
                        textureImageView.setScaleY(scale);
                        textureImageView.setTranslationX(rect.x);
                        textureImageView.setTranslationY(rect.y);
                        textureView.setScaleX(scale);
                        textureView.setScaleY(scale);
                        textureView.setTranslationX(rect.x);
                        textureView.setTranslationY(rect.y);
                    } else {
                        pipVideoView.close();
                        pipVideoView = null;
                    }
                    setShowWithoutAnimation(true);
                    show();
                    if (animated) {
                        waitingForDraw = 4;
                        backDrawable.setAlpha(1);
                        containerView.setTranslationY(containerView.getMeasuredHeight() + AndroidUtilities.dp(10));
                    }
                }
            }

            @Override
            public TextureView onSwitchInlineMode(View controlsView, boolean inline, float aspectRatio, int rotation, boolean animated) {
                if (inline) {
                    controlsView.setTranslationY(0);
                    pipVideoView = new PipVideoView(false);
                    return pipVideoView.show(parentActivity, EmbedBottomSheet.this, controlsView, aspectRatio, rotation, null);
                }

                if (animated) {
                    animationInProgress = true;

                    View view = videoView.getAspectRatioView();
                    view.getLocationInWindow(position);
                    position[0] -= getLeftInset();
                    position[1] -= containerView.getTranslationY();

                    TextureView textureView = videoView.getTextureView();
                    ImageView textureImageView = videoView.getTextureImageView();
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, position[0]),
                            ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, position[1]),
                            ObjectAnimator.ofFloat(textureView, View.SCALE_X, 1.0f),
                            ObjectAnimator.ofFloat(textureView, View.SCALE_Y, 1.0f),
                            ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, position[0]),
                            ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, position[1]),
                            ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 51)
                    );
                    animatorSet.setInterpolator(new DecelerateInterpolator());
                    animatorSet.setDuration(250);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animationInProgress = false;
                        }
                    });
                    animatorSet.start();
                } else {
                    containerView.setTranslationY(0);
                }
                return null;
            }

            @Override
            public void onSharePressed() {

            }

            @Override
            public void onPlayStateChanged(WebPlayerView playerView, boolean playing) {
                if (playing) {
                    try {
                        parentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    try {
                        parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }

            @Override
            public boolean checkInlinePermissions() {
                return EmbedBottomSheet.this.checkInlinePermissions();
            }

            @Override
            public ViewGroup getTextureViewContainer() {
                return container;
            }
        });
        videoView.setVisibility(View.INVISIBLE);
        containerLayout.addView(videoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0) - 10));

        progressBarBlackBackground = new View(context);
        progressBarBlackBackground.setBackgroundColor(0xff000000);
        progressBarBlackBackground.setVisibility(View.INVISIBLE);
        containerLayout.addView(progressBarBlackBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0)));

        progressBar = new RadialProgressView(context);
        progressBar.setVisibility(View.INVISIBLE);
        containerLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, (48 + 36 + (hasDescription ? 22 : 0)) / 2));

        TextView textView;

        if (hasDescription) {
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            textView.setText(description);
            textView.setSingleLine(true);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            containerLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48 + 9 + 20));
        }

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        textView.setText(title);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        containerLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48 + 9));

        View lineView = new View(context);
        lineView.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine));
        containerLayout.addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM));
        ((FrameLayout.LayoutParams) lineView.getLayoutParams()).bottomMargin = AndroidUtilities.dp(48);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        containerLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        textView.setText(LocaleController.getString("Close", R.string.Close).toUpperCase());
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(v -> dismiss());

        imageButtonsContainer = new LinearLayout(context);
        imageButtonsContainer.setVisibility(View.INVISIBLE);
        frameLayout.addView(imageButtonsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        pipButton = new ImageView(context);
        pipButton.setScaleType(ImageView.ScaleType.CENTER);
        pipButton.setImageResource(R.drawable.video_pip);
        pipButton.setContentDescription(LocaleController.getString("AccDescrPipMode", R.string.AccDescrPipMode));
        pipButton.setEnabled(false);
        pipButton.setAlpha(0.5f);
        pipButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlue4), PorterDuff.Mode.MULTIPLY));
        pipButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        imageButtonsContainer.addView(pipButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 4, 0));
        pipButton.setOnClickListener(v -> {
            boolean inAppOnly = isYouTube && "inapp".equals(MessagesController.getInstance(currentAccount).youtubePipType);
            if (!inAppOnly && !checkInlinePermissions()) {
                return;
            }
            if (progressBar.getVisibility() == View.VISIBLE) {
                return;
            }
            boolean animated = false;
            pipVideoView = new PipVideoView(inAppOnly);
            pipVideoView.show(parentActivity, EmbedBottomSheet.this, null, width != 0 && height != 0 ? width / (float) height : 1.0f, 0, webView);
            if (isYouTube) {
                runJsCode("hideControls();");
            }
            if (animated) {
                animationInProgress = true;

                View view = videoView.getAspectRatioView();
                view.getLocationInWindow(position);
                position[0] -= getLeftInset();
                position[1] -= containerView.getTranslationY();

                TextureView textureView = videoView.getTextureView();
                ImageView textureImageView = videoView.getTextureImageView();
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(textureImageView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(textureImageView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_X, position[0]),
                        ObjectAnimator.ofFloat(textureImageView, View.TRANSLATION_Y, position[1]),
                        ObjectAnimator.ofFloat(textureView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(textureView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(textureView, View.TRANSLATION_X, position[0]),
                        ObjectAnimator.ofFloat(textureView, View.TRANSLATION_Y, position[1]),
                        ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0),
                        ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 51)
                );
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.setDuration(250);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animationInProgress = false;
                    }
                });
                animatorSet.start();
            } else {
                containerView.setTranslationY(0);
            }
            dismissInternal();
        });

        View.OnClickListener copyClickListener = v -> {
            try {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", openUrl);
                clipboard.setPrimaryClip(clip);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).showBulletin(BulletinFactory::createCopyLinkBulletin);
            }
            dismiss();
        };

        ImageView copyButton = new ImageView(context);
        copyButton.setScaleType(ImageView.ScaleType.CENTER);
        copyButton.setImageResource(R.drawable.video_copy);
        copyButton.setContentDescription(LocaleController.getString("CopyLink", R.string.CopyLink));
        copyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlue4), PorterDuff.Mode.MULTIPLY));
        copyButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        imageButtonsContainer.addView(copyButton, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT));
        copyButton.setOnClickListener(copyClickListener);

        copyTextButton = new TextView(context);
        copyTextButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        copyTextButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        copyTextButton.setGravity(Gravity.CENTER);
        copyTextButton.setSingleLine(true);
        copyTextButton.setEllipsize(TextUtils.TruncateAt.END);
        copyTextButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        copyTextButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        copyTextButton.setText(LocaleController.getString("Copy", R.string.Copy).toUpperCase());
        copyTextButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(copyTextButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        copyTextButton.setOnClickListener(copyClickListener);

        TextView openInButton = new TextView(context);
        openInButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        openInButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        openInButton.setGravity(Gravity.CENTER);
        openInButton.setSingleLine(true);
        openInButton.setEllipsize(TextUtils.TruncateAt.END);
        openInButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        openInButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        openInButton.setText(LocaleController.getString("OpenInBrowser", R.string.OpenInBrowser).toUpperCase());
        openInButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(openInButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        openInButton.setOnClickListener(v -> {
            Browser.openUrl(parentActivity, openUrl);
            dismiss();
        });

        boolean canHandleUrl = videoView.canHandleUrl(embedUrl) || videoView.canHandleUrl(originalUrl);
        videoView.setVisibility(canHandleUrl ? View.VISIBLE : View.INVISIBLE);
        if (canHandleUrl) {
            videoView.willHandle();
        }

        setDelegate(new BottomSheet.BottomSheetDelegate() {
            @Override
            public void onOpenAnimationEnd() {
                boolean handled = canHandleUrl && videoView.loadVideo(embedUrl, null, null, openUrl, true);
                if (handled) {
                    progressBar.setVisibility(View.INVISIBLE);
                    webView.setVisibility(View.INVISIBLE);
                    videoView.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    webView.setVisibility(View.VISIBLE);
                    imageButtonsContainer.setVisibility(View.VISIBLE);
                    copyTextButton.setVisibility(View.INVISIBLE);
                    webView.setKeepScreenOn(true);
                    videoView.setVisibility(View.INVISIBLE);
                    videoView.getControlsView().setVisibility(View.INVISIBLE);
                    videoView.getTextureView().setVisibility(View.INVISIBLE);
                    if (videoView.getTextureImageView() != null) {
                        videoView.getTextureImageView().setVisibility(View.INVISIBLE);
                    }
                    videoView.loadVideo(null, null, null, null, false);
                    HashMap<String, String> args = new HashMap<>();
                    args.put("Referer", "messenger.telegram.org");
                    try {
                        String currentYoutubeId = videoView.getYoutubeId();
                        if (currentYoutubeId != null) {
                            progressBarBlackBackground.setVisibility(View.VISIBLE);
                            isYouTube = true;
                            if (Build.VERSION.SDK_INT >= 17) {
                                webView.addJavascriptInterface(new YoutubeProxy(), "YoutubeProxy");
                            }
                            int seekToTime = 0;
                            if (openUrl != null) {
                                try {
                                    Uri uri = Uri.parse(openUrl);
                                    String t = seekTimeOverride > 0 ? "" + seekTimeOverride : null;
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
                            webView.loadUrl(embedUrl, args);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }

            @Override
            public boolean canDismiss() {
                if (videoView.isInFullscreen()) {
                    videoView.exitFullscreen();
                    return false;
                }
                try {
                    parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return true;
            }
        });

        orientationEventListener = new OrientationEventListener(ApplicationLoader.applicationContext) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientationEventListener == null || videoView.getVisibility() != View.VISIBLE) {
                    return;
                }
                if (parentActivity != null && videoView.isInFullscreen() && fullscreenedByButton) {
                    if (orientation >= 270 - 30 && orientation <= 270 + 30) {
                        wasInLandscape = true;
                    } else if (wasInLandscape && orientation > 0 && (orientation >= 330 || orientation <= 30)) {
                        parentActivity.setRequestedOrientation(prevOrientation);
                        fullscreenedByButton = false;
                        wasInLandscape = false;
                    }
                }
            }
        };

        String currentYoutubeId = videoView.getYouTubeVideoId(embedUrl);
        if (currentYoutubeId != null || !canHandleUrl) {
            progressBar.setVisibility(View.VISIBLE);
            webView.setVisibility(View.VISIBLE);
            imageButtonsContainer.setVisibility(View.VISIBLE);
            if (currentYoutubeId != null) {
                progressBarBlackBackground.setVisibility(View.VISIBLE);
            }
            copyTextButton.setVisibility(View.INVISIBLE);
            webView.setKeepScreenOn(true);
            videoView.setVisibility(View.INVISIBLE);
            videoView.getControlsView().setVisibility(View.INVISIBLE);
            videoView.getTextureView().setVisibility(View.INVISIBLE);
            if (videoView.getTextureImageView() != null) {
                videoView.getTextureImageView().setVisibility(View.INVISIBLE);
            }
            if (currentYoutubeId != null && "disabled".equals(MessagesController.getInstance(currentAccount).youtubePipType)) {
                pipButton.setVisibility(View.GONE);
            }
        }

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
        instance = this;
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

    public boolean checkInlinePermissions() {
        if (parentActivity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity)) {
            return true;
        } else {
            AlertsCreator.createDrawOverlayPermissionDialog(parentActivity, null);
        }
        return false;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return videoView.getVisibility() != View.VISIBLE || !videoView.isInFullscreen();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (videoView.getVisibility() == View.VISIBLE && videoView.isInitied() && !videoView.isInline()) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (!videoView.isInFullscreen()) {
                    videoView.enterFullscreen();
                }
            } else {
                if (videoView.isInFullscreen()) {
                    videoView.exitFullscreen();
                }
            }
        }
        if (pipVideoView != null) {
            pipVideoView.onConfigurationChanged();
        }
    }

    public void destroy() {
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            containerLayout.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        if (pipVideoView != null) {
            pipVideoView.close();
            pipVideoView = null;
        }
        if (videoView != null) {
            videoView.destroy();
        }
        instance = null;
        dismissInternal();
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (orientationEventListener != null) {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
    }

    public void exitFromPip() {
        if (webView == null || pipVideoView == null) {
            return;
        }
        if (ApplicationLoader.mainInterfacePaused) {
            try {
                parentActivity.startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (isYouTube) {
            runJsCode("showControls();");
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }
        containerLayout.addView(webView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0)));
        setShowWithoutAnimation(true);
        show();
        pipVideoView.close();
        pipVideoView = null;
    }

    public static EmbedBottomSheet getInstance() {
        return instance;
    }

    public void updateTextureViewPosition() {
        View view = videoView.getAspectRatioView();
        view.getLocationInWindow(position);
        position[0] -= getLeftInset();

        if (!videoView.isInline() && !animationInProgress) {
            TextureView textureView = videoView.getTextureView();
            textureView.setTranslationX(position[0]);
            textureView.setTranslationY(position[1]);
            View textureImageView = videoView.getTextureImageView();
            if (textureImageView != null) {
                textureImageView.setTranslationX(position[0]);
                textureImageView.setTranslationY(position[1]);
            }
        }
        View controlsView = videoView.getControlsView();
        if (controlsView.getParent() == container) {
            controlsView.setTranslationY(position[1]);
        } else {
            controlsView.setTranslationY(0);
        }
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        return fullscreenVideoContainer.getVisibility() != View.VISIBLE;
    }

    @Override
    protected void onContainerTranslationYChanged(float translationY) {
        updateTextureViewPosition();
    }

    @Override
    protected boolean onCustomMeasure(View view, int width, int height) {
        if (view == videoView.getControlsView()) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            layoutParams.width = videoView.getMeasuredWidth();
            layoutParams.height = videoView.getAspectRatioView().getMeasuredHeight() + (videoView.isInFullscreen() ? 0 : AndroidUtilities.dp(10));
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        if (view == videoView.getControlsView()) {
            updateTextureViewPosition();
        }
        return false;
    }

    public void pause() {
        if (videoView != null && videoView.isInitied()) {
            videoView.pause();
        }
    }

    @Override
    public void onContainerDraw(Canvas canvas) {
        if (waitingForDraw != 0) {
            waitingForDraw--;
            if (waitingForDraw == 0) {
                videoView.updateTextureImageView();
                pipVideoView.close();
                pipVideoView = null;
            } else {
                container.invalidate();
            }
        }
    }
}

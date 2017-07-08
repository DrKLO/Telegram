/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BringAppForegroundService;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

@TargetApi(16)
public class EmbedBottomSheet extends BottomSheet {

    private WebView webView;
    private WebPlayerView videoView;
    private View customView;
    private FrameLayout fullscreenVideoContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private RadialProgressView progressBar;
    private Activity parentActivity;
    private PipVideoView pipVideoView;

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

    private OnShowListener onShowListener = new OnShowListener() {
        @Override
        public void onShow(DialogInterface dialog) {
            if (pipVideoView != null && videoView.isInline()) {
                videoView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        videoView.getViewTreeObserver().removeOnPreDrawListener(this);
                        /*AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {*/

                            /*}
                        }, 100);*/
                        return true;
                    }
                });
            }
        }
    };

    @SuppressLint("StaticFieldLeak")
    private static EmbedBottomSheet instance;

    public static void show(Context context, String title, String description, String originalUrl, final String url, int w, int h) {
        if (instance != null) {
            instance.destroy();
        }
        new EmbedBottomSheet(context, title, description, originalUrl, url, w, h).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private EmbedBottomSheet(Context context, String title, String description, String originalUrl, final String url, int w, int h) {
        super(context, false);
        fullWidth = true;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

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
        fullscreenVideoContainer.setBackgroundColor(0xff000000);
        if (Build.VERSION.SDK_INT >= 21) {
            fullscreenVideoContainer.setFitsSystemWindows(true);
        }

        container.addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fullscreenVideoContainer.setVisibility(View.INVISIBLE);
        fullscreenVideoContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        FrameLayout containerLayout = new FrameLayout(context) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                try {
                    if (webView.getParent() != null) {
                        removeView(webView);
                        webView.stopLoading();
                        webView.loadUrl("about:blank");
                        webView.destroy();
                    }

                    if (!videoView.isInline()) {
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
        containerLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        setCustomView(containerLayout);

        webView = new WebView(context);
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
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
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
                progressBar.setVisibility(View.INVISIBLE);
            }
        });

        containerLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0)));

        videoView = new WebPlayerView(context, true, false, new WebPlayerView.WebPlayerViewDelegate() {
            @Override
            public void onInitFailed() {
                webView.setVisibility(View.VISIBLE);
                webView.setKeepScreenOn(true);
                videoView.setVisibility(View.INVISIBLE);
                videoView.getControlsView().setVisibility(View.INVISIBLE);
                videoView.getTextureView().setVisibility(View.INVISIBLE);
                if (videoView.getTextureImageView() != null) {
                    videoView.getTextureImageView().setVisibility(View.INVISIBLE);
                }
                videoView.loadVideo(null, null, null, false);
                HashMap<String, String> args = new HashMap<>();
                args.put("Referer", "http://youtube.com");
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
                        if (Build.VERSION.SDK_INT >= 21) {
                            rect.y += AndroidUtilities.statusBarHeight;
                        }

                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(textureImageView, "scaleX", scale),
                                ObjectAnimator.ofFloat(textureImageView, "scaleY", scale),
                                ObjectAnimator.ofFloat(textureImageView, "translationX", rect.x),
                                ObjectAnimator.ofFloat(textureImageView, "translationY", rect.y),
                                ObjectAnimator.ofFloat(textureView, "scaleX", scale),
                                ObjectAnimator.ofFloat(textureView, "scaleY", scale),
                                ObjectAnimator.ofFloat(textureView, "translationX", rect.x),
                                ObjectAnimator.ofFloat(textureView, "translationY", rect.y),
                                ObjectAnimator.ofFloat(containerView, "translationY", containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                                ObjectAnimator.ofInt(backDrawable, "alpha", 0),
                                ObjectAnimator.ofFloat(fullscreenVideoContainer, "alpha", 0),
                                ObjectAnimator.ofFloat(controlsView, "alpha", 0)
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
                        parentActivity.startService(new Intent(ApplicationLoader.applicationContext, BringAppForegroundService.class));
                    }

                    if (animated) {
                        setOnShowListener(onShowListener);
                        Rect rect = PipVideoView.getPipRect(aspectRatio);

                        TextureView textureView = videoView.getTextureView();
                        ImageView textureImageView = videoView.getTextureImageView();
                        float scale = rect.width / textureView.getLayoutParams().width;
                        if (Build.VERSION.SDK_INT >= 21) {
                            rect.y += AndroidUtilities.statusBarHeight;
                        }
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
                    pipVideoView = new PipVideoView();
                    return pipVideoView.show(parentActivity, EmbedBottomSheet.this, controlsView, aspectRatio, rotation);
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
                            ObjectAnimator.ofFloat(textureImageView, "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(textureImageView, "scaleY", 1.0f),
                            ObjectAnimator.ofFloat(textureImageView, "translationX", position[0]),
                            ObjectAnimator.ofFloat(textureImageView, "translationY", position[1]),
                            ObjectAnimator.ofFloat(textureView, "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(textureView, "scaleY", 1.0f),
                            ObjectAnimator.ofFloat(textureView, "translationX", position[0]),
                            ObjectAnimator.ofFloat(textureView, "translationY", position[1]),
                            ObjectAnimator.ofFloat(containerView, "translationY", 0),
                            ObjectAnimator.ofInt(backDrawable, "alpha", 51)
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
            public boolean checkInlinePermissons() {
                if (parentActivity == null) {
                    return false;
                }
                if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity)) {
                    return true;
                } else {
                    new AlertDialog.Builder(parentActivity).setTitle(LocaleController.getString("AppName", R.string.AppName))
                            .setMessage(LocaleController.getString("PermissionDrawAboveOtherApps", R.string.PermissionDrawAboveOtherApps))
                            .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
                                @TargetApi(Build.VERSION_CODES.M)
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (parentActivity != null) {
                                        parentActivity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + parentActivity.getPackageName())));
                                    }
                                }
                            }).show();
                }
                return false;
            }

            @Override
            public ViewGroup getTextureViewContainer() {
                return container;
            }
        });
        videoView.setVisibility(View.INVISIBLE);
        containerLayout.addView(videoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48 + 36 + (hasDescription ? 22 : 0) - 10));

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

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        textView.setText(LocaleController.getString("Close", R.string.Close).toUpperCase());
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        textView.setText(LocaleController.getString("Copy", R.string.Copy).toUpperCase());
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", openUrl);
                    clipboard.setPrimaryClip(clip);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                Toast.makeText(getContext(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue4));
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        textView.setText(LocaleController.getString("OpenInBrowser", R.string.OpenInBrowser).toUpperCase());
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Browser.openUrl(parentActivity, openUrl);
                dismiss();
            }
        });

        setDelegate(new BottomSheet.BottomSheetDelegate() {
            @Override
            public void onOpenAnimationEnd() {
                boolean handled = videoView.loadVideo(embedUrl, null, openUrl, true);
                if (handled) {
                    progressBar.setVisibility(View.INVISIBLE);
                    webView.setVisibility(View.INVISIBLE);
                    videoView.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    webView.setVisibility(View.VISIBLE);
                    webView.setKeepScreenOn(true);
                    videoView.setVisibility(View.INVISIBLE);
                    videoView.getControlsView().setVisibility(View.INVISIBLE);
                    videoView.getTextureView().setVisibility(View.INVISIBLE);
                    if (videoView.getTextureImageView() != null) {
                        videoView.getTextureImageView().setVisibility(View.INVISIBLE);
                    }
                    videoView.loadVideo(null, null, null, false);
                    HashMap<String, String> args = new HashMap<>();
                    args.put("Referer", "http://youtube.com");
                    try {
                        webView.loadUrl(embedUrl, args);
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
                    } else if (wasInLandscape && (orientation >= 330 || orientation <= 30)) {
                        parentActivity.setRequestedOrientation(prevOrientation);
                        fullscreenedByButton = false;
                        wasInLandscape = false;
                    }
                }
            }
        };

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        } else {
            orientationEventListener.disable();
            orientationEventListener = null;
        }
        instance = this;
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

/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;

import java.util.HashMap;

public class WebFrameLayout extends FrameLayout {

    private WebView webView;
    private BottomSheet dialog;
    private View customView;
    private FrameLayout fullscreenVideoContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ProgressBar progressBar;

    private int width;
    private int height;
    private String openUrl;

    @SuppressLint("SetJavaScriptEnabled")
    public WebFrameLayout(Context context, BottomSheet parentDialog, String title, String originalUrl, final String url, int w, int h) {
        super(context);

        openUrl = originalUrl;
        width = w;
        height = h;
        if (width == 0 || height == 0) {
            width = AndroidUtilities.displaySize.x;
            height = AndroidUtilities.displaySize.y / 2;
        }
        dialog = parentDialog;

        fullscreenVideoContainer = new FrameLayout(context);
        fullscreenVideoContainer.setBackgroundColor(0xff000000);
        if (Build.VERSION.SDK_INT >= 21) {
            fullscreenVideoContainer.setFitsSystemWindows(true);
        }
        dialog.getContainer().addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fullscreenVideoContainer.setVisibility(INVISIBLE);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.LEFT | Gravity.TOP));

        TextView textView = new TextView(context);
        textView.setTextColor(0xff666666);
        textView.setText(title);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        linearLayout.addView(textView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 16, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextColor(0xff999999);
        textView.setText(LocaleController.getString("OpenInBrowser", R.string.OpenInBrowser));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 16, 0, 0, 0));
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AndroidUtilities.openUrl(getContext(), openUrl);
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        textView = new TextView(context);
        textView.setTextColor(0xff999999);
        textView.setText(LocaleController.getString("CopyUrl", R.string.CopyUrl));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 16, 0, 16, 0));
        textView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (Build.VERSION.SDK_INT < 11) {
                        android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setText(openUrl);
                    } else {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("label", openUrl);
                        clipboard.setPrimaryClip(clip);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                Toast.makeText(getContext(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        View lineView = new View(context);
        lineView.setBackgroundResource(R.drawable.header_shadow);
        addView(lineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 0, 40, 0, 0));

        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= 17) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

        String userAgent = webView.getSettings().getUserAgentString();
        if (userAgent != null) {
            userAgent = userAgent.replace("Android", "");
            webView.getSettings().setUserAgentString(userAgent);
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
                if (dialog != null) {
                    dialog.getSheetContainer().setVisibility(INVISIBLE);
                    fullscreenVideoContainer.setVisibility(VISIBLE);
                    fullscreenVideoContainer.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                }
                customViewCallback = callback;
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (customView == null) {
                    return;
                }
                if (dialog != null) {
                    dialog.getSheetContainer().setVisibility(VISIBLE);
                    fullscreenVideoContainer.setVisibility(INVISIBLE);
                    fullscreenVideoContainer.removeView(customView);
                }
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
                progressBar.setVisibility(INVISIBLE);
            }
        });

        addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 8, 49, 8, 0));

        progressBar = new ProgressBar(context);
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 8, 24, 8, 0));

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        parentDialog.setDelegate(new BottomSheet.BottomSheetDelegate() {

            @Override
            public void onOpenAnimationEnd() {
                HashMap<String, String> args = new HashMap<>();
                args.put("Referer", "http://youtube.com");
                try {
                    webView.loadUrl(url, args);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        float scale = width / parentWidth;
        int h = (int) Math.min(height / scale, AndroidUtilities.displaySize.y / 2);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h + AndroidUtilities.dp(49), MeasureSpec.EXACTLY));
    }
}

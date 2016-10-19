/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatorListenerAdapterProxy;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.SerializedData;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ShareAlert;

import java.net.URLEncoder;

public class WebviewActivity extends BaseFragment {

    private WebView webView;
    private ActionBarMenuItem progressItem;
    private ContextProgressView progressView;
    private String currentUrl;
    private String currentBot;
    private String currentGame;
    private String linkToCopy;
    private MessageObject currentMessageObject;
    private String short_param;

    private final static int share = 1;
    private final static int open_in = 2;

    private class TelegramWebviewProxy {
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (getParentActivity() == null) {
                        return;
                    }
                    FileLog.e("tmessages", eventName);
                    switch (eventName) {
                        case "share_game":
                            currentMessageObject.messageOwner.with_my_score = false;
                            break;
                        case "share_score":
                            currentMessageObject.messageOwner.with_my_score = true;
                            break;
                    }
                    showDialog(new ShareAlert(getParentActivity(), currentMessageObject, null, false, linkToCopy));
                }
            });
        }
    }

    public WebviewActivity(String url, String botName, String gameName, String startParam, MessageObject messageObject) {
        super();
        currentUrl = url;
        currentBot = botName;
        currentGame = gameName;
        currentMessageObject = messageObject;
        short_param = startParam;
        linkToCopy = "https://telegram.me/" + currentBot + (TextUtils.isEmpty(startParam) ? "" : "?game=" + startParam);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        try {
            ViewParent parent = webView.getParent();
            if (parent != null) {
                ((FrameLayout) parent).removeView(webView);
            }
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public View createView(Context context) {
        swipeBackEnabled = false;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(currentGame);
        actionBar.setSubtitle("@" + currentBot);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == share) {
                    currentMessageObject.messageOwner.with_my_score = false;
                    showDialog(new ShareAlert(getParentActivity(), currentMessageObject, null, false, linkToCopy));
                } else if (id == open_in) {
                    openGameInBrowser(currentUrl, currentMessageObject, getParentActivity(), short_param, currentBot);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        progressItem = menu.addItemWithWidth(share, R.drawable.share, AndroidUtilities.dp(54));
        progressView = new ContextProgressView(context, 1);
        progressItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        progressItem.getImageView().setVisibility(View.INVISIBLE);

        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(open_in, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp), 0);

        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        if (Build.VERSION.SDK_INT >= 21) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(webView, true);
            webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                progressItem.getImageView().setVisibility(View.VISIBLE);
                progressItem.setEnabled(true);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f, 0.1f),
                        ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f, 0.1f),
                        ObjectAnimator.ofFloat(progressView, "alpha", 1.0f, 0.0f),
                        ObjectAnimator.ofFloat(progressItem.getImageView(), "scaleX", 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(progressItem.getImageView(), "scaleY", 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(progressItem.getImageView(), "alpha", 0.0f, 1.0f));
                animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        progressView.setVisibility(View.INVISIBLE);
                    }
                });
                animatorSet.setDuration(150);
                animatorSet.start();
            }
        });

        frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && webView != null) {
            webView.loadUrl(currentUrl);
        }
    }

    public static boolean supportWebview() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if ("samsung".equals(manufacturer)) {
            if ("GT-I9500".equals(model)) {
                return false;
            }
        }
        return true;
    }

    public static void openGameInBrowser(String urlStr, MessageObject messageObject, Activity parentActivity, String short_name, String username) {
        try {
            String url = urlStr;
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("botshare", Activity.MODE_PRIVATE);
            String existing = sharedPreferences.getString("" + messageObject.getId(), null);
            StringBuilder hash = new StringBuilder(existing != null ? existing : "");
            StringBuilder addHash = new StringBuilder("tgShareScoreUrl=" + URLEncoder.encode("tgb://share_game_score?hash=", "UTF-8"));
            if (existing == null) {
                final char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
                for (int i = 0; i < 20; i++) {
                    hash.append(chars[Utilities.random.nextInt(chars.length)]);
                }
            }
            addHash.append(hash);
            int index = url.indexOf('#');
            if (index < 0) {
                url += "#" + addHash;
            } else {
                String curHash = url.substring(index + 1);
                if (curHash.indexOf('=') >= 0 || curHash.indexOf('?') >= 0) {
                    url += "&" + addHash;
                } else {
                    if (curHash.length() > 0) {
                        url += "?" + addHash;
                    } else {
                        url += addHash;
                    }
                }
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(hash + "_date", (int) (System.currentTimeMillis() / 1000));
            SerializedData serializedData = new SerializedData(messageObject.messageOwner.getObjectSize());
            messageObject.messageOwner.serializeToStream(serializedData);
            editor.putString(hash + "_m", Utilities.bytesToHex(serializedData.toByteArray()));
            editor.putString(hash + "_link", "https://telegram.me/" + username + (TextUtils.isEmpty(short_name) ? "" : "?game=" + short_name));
            editor.commit();
            Browser.openUrl(parentActivity, url, false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }
}

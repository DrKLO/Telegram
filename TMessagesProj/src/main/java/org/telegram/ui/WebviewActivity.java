/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ShareAlert;

import java.net.URLEncoder;
import java.util.ArrayList;

public class WebviewActivity extends BaseFragment {

    private WebView webView;
    private ActionBarMenuItem progressItem;
    private ContextProgressView progressView;

    private String currentUrl;
    private long currentDialogId;
    private String currentBot;
    private String currentGame;
    private String linkToCopy;
    private MessageObject currentMessageObject;
    private String short_param;

    private boolean loadStats;

    private final static int share = 1;
    private final static int open_in = 2;

    private static final int TYPE_GAME = 0;
    private static final int TYPE_STAT = 1;

    private int type;

    private class TelegramWebviewProxy {
        @JavascriptInterface
        public void postEvent(final String eventName, final String eventData) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) {
                    return;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(eventName);
                }
                switch (eventName) {
                    case "share_game":
                        currentMessageObject.messageOwner.with_my_score = false;
                        break;
                    case "share_score":
                        currentMessageObject.messageOwner.with_my_score = true;
                        break;
                }
                showDialog(ShareAlert.createShareAlert(getParentActivity(), currentMessageObject, null, false, linkToCopy, false));
            });
        }
    }

    public Runnable typingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentMessageObject == null || getParentActivity() == null || typingRunnable == null) {
                return;
            }
            MessagesController.getInstance(currentAccount).sendTyping(currentMessageObject.getDialogId(), 0, 6, 0);
            AndroidUtilities.runOnUIThread(typingRunnable, 25000);
        }
    };

    public WebviewActivity(String url, String botName, String gameName, String startParam, MessageObject messageObject) {
        super();
        currentUrl = url;
        currentBot = botName;
        currentGame = gameName;
        currentMessageObject = messageObject;
        short_param = startParam;
        linkToCopy = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + currentBot + (TextUtils.isEmpty(startParam) ? "" : "?game=" + startParam);
        type = TYPE_GAME;
    }

    public WebviewActivity(String statUrl, long did) {
        super();
        currentUrl = statUrl;
        currentDialogId = did;
        type = TYPE_STAT;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.checkAndroidTheme(getContext(), false);
        AndroidUtilities.cancelRunOnUIThread(typingRunnable);
        webView.setLayerType(View.LAYER_TYPE_NONE, null);
        typingRunnable = null;
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
            FileLog.e(e);
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == share) {
                    if (currentMessageObject != null) {
                        currentMessageObject.messageOwner.with_my_score = false;
                        showDialog(ShareAlert.createShareAlert(getParentActivity(), currentMessageObject, null, false, linkToCopy, false));
                    }
                } else if (id == open_in) {
                    openGameInBrowser(currentUrl, currentMessageObject, getParentActivity(), short_param, currentBot);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        progressItem = menu.addItemWithWidth(share, R.drawable.share, AndroidUtilities.dp(54));
        if (type == TYPE_GAME) {
            ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
            menuItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));

            actionBar.setTitle(currentGame);
            actionBar.setSubtitle("@" + currentBot);

            progressView = new ContextProgressView(context, 1);
            progressItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            progressView.setAlpha(0.0f);
            progressView.setScaleX(0.1f);
            progressView.setScaleY(0.1f);
            progressView.setVisibility(View.INVISIBLE);
        } else if (type == TYPE_STAT) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarItems), false);
            actionBar.setItemsColor(Theme.getColor(Theme.key_player_actionBarItems), true);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_player_actionBarSelector), false);
            actionBar.setTitleColor(Theme.getColor(Theme.key_player_actionBarTitle));
            actionBar.setSubtitleColor(Theme.getColor(Theme.key_player_actionBarSubtitle));
            actionBar.setTitle(LocaleController.getString(R.string.Statistics));

            progressView = new ContextProgressView(context, 3);
            progressItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            progressView.setAlpha(1.0f);
            progressView.setScaleX(1.0f);
            progressView.setScaleY(1.0f);
            progressView.setVisibility(View.VISIBLE);
            progressItem.getContentView().setVisibility(View.GONE);
            progressItem.setEnabled(false);
        }

        AndroidUtilities.checkAndroidTheme(context, true);
        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        if (Build.VERSION.SDK_INT >= 19) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            webView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }

        if (Build.VERSION.SDK_INT >= 17) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptThirdPartyCookies(webView, true);
            if (type == TYPE_GAME) {
                webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
            }
        }

        webView.setWebViewClient(new WebViewClient() {

            private boolean isInternalUrl(String url) {
                if (TextUtils.isEmpty(url)) {
                    return false;
                }
                Uri uri = Uri.parse(url);
                if ("tg".equals(uri.getScheme())) {
                    if (type == TYPE_STAT) {
                        try {
                            uri = Uri.parse(url.replace("tg:statsrefresh", "tg://telegram.org"));
                            reloadStats(uri.getQueryParameter("params"));
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    } else {
                        finishFragment(false);
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            ComponentName componentName = new ComponentName(ApplicationLoader.applicationContext.getPackageName(), LaunchActivity.class.getName());
                            intent.setComponent(componentName);
                            intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, ApplicationLoader.applicationContext.getPackageName());
                            ApplicationLoader.applicationContext.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                if (isInternalUrl(url)) {
                    return;
                }
                super.onLoadResource(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return isInternalUrl(url) || super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressView != null && progressView.getVisibility() == View.VISIBLE) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    if (type == TYPE_GAME) {
                        progressItem.getContentView().setVisibility(View.VISIBLE);
                        progressItem.setEnabled(true);
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f, 0.1f),
                                ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f, 0.1f),
                                ObjectAnimator.ofFloat(progressView, "alpha", 1.0f, 0.0f),
                                ObjectAnimator.ofFloat(progressItem.getContentView(), "scaleX", 0.0f, 1.0f),
                                ObjectAnimator.ofFloat(progressItem.getContentView(), "scaleY", 0.0f, 1.0f),
                                ObjectAnimator.ofFloat(progressItem.getContentView(), "alpha", 0.0f, 1.0f));
                    } else {
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f, 0.1f),
                                ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f, 0.1f),
                                ObjectAnimator.ofFloat(progressView, "alpha", 1.0f, 0.0f));
                    }
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animator) {
                            if (type == TYPE_STAT) {
                                progressItem.setVisibility(View.GONE);
                            } else {
                                progressView.setVisibility(View.INVISIBLE);
                            }
                        }
                    });
                    animatorSet.setDuration(150);
                    animatorSet.start();
                }
            }
        });

        frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.cancelRunOnUIThread(typingRunnable);
        typingRunnable.run();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && webView != null) {
            webView.loadUrl(currentUrl);
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
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

    private void reloadStats(String params) {
        if (loadStats) {
            return;
        }
        loadStats = true;
        TLRPC.TL_messages_getStatsURL req = new TLRPC.TL_messages_getStatsURL();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(currentDialogId);
        req.params = params != null ? params : "";
        req.dark = Theme.getCurrentTheme().isDark();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadStats = false;
            if (response != null) {
                TLRPC.TL_statsURL url = (TLRPC.TL_statsURL) response;
                webView.loadUrl(currentUrl = url.url);
            }
        }));
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
            editor.putString(hash + "_link", "https://" + MessagesController.getInstance(messageObject.currentAccount).linkPrefix + "/" + username + (TextUtils.isEmpty(short_name) ? "" : "?game=" + short_name));
            editor.commit();
            Browser.openUrl(parentActivity, url, false);
            serializedData.cleanup();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        if (type == TYPE_GAME) {
            themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

            themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2));
            themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2));
        } else {
            themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_player_actionBarItems));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_player_actionBarTitle));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_player_actionBarSelector));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
            themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

            themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner4));
            themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter4));
        }
        return themeDescriptions;
    }
}

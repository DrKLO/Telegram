package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.bots.BotButtons;
import org.telegram.ui.bots.BotSensors;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.web.BotWebViewContainer;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.WebViewRequestProps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BottomSheetTabs extends FrameLayout {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public boolean drawTabs = true;
    public boolean doNotDismiss = false;

    private final ActionBarLayout actionBarLayout;

    public BottomSheetTabs(Context context, ActionBarLayout actionBarLayout) {
        super(context);
        this.actionBarLayout = actionBarLayout;

        setNavigationBarColor(Theme.getColor(Theme.key_windowBackgroundGray));

        updateMultipleTitle();
        updateVisibility(false);
    }

    public void openTab(WebTabData tab) {
        BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null || lastFragment.getParentActivity() == null) return;
        if (lastFragment instanceof ChatActivity) {
            if (((ChatActivity) lastFragment).getChatActivityEnterView() != null) {
                ((ChatActivity) lastFragment).getChatActivityEnterView().closeKeyboard();
                ((ChatActivity) lastFragment).getChatActivityEnterView().hidePopup(true, false);
            }
        }
        if (tab.articleViewer != null) {
            BaseFragment fragment = actionBarLayout.getSheetFragment();
            final ArticleViewer articleViewer = tab.articleViewer;
            BottomSheetTabDialog.checkSheet(articleViewer.sheet);
            fragment.addSheet(articleViewer.sheet);
            articleViewer.sheet.reset();
            articleViewer.setParentActivity(fragment.getParentActivity(), fragment);
            articleViewer.sheet.attachInternal(fragment);
            articleViewer.sheet.animateOpen(true, true, null);
            removeTab(tab, false);
            return;
        }
        boolean closed = closeAttachedSheets();
        Utilities.Callback<BaseFragment> open = fragment -> {
            if (fragment == null) return;
            if (fragment instanceof ChatActivity) {
                if (((ChatActivity) fragment).getChatActivityEnterView() != null) {
                    ((ChatActivity) fragment).getChatActivityEnterView().closeKeyboard();
                    ((ChatActivity) fragment).getChatActivityEnterView().hidePopup(true, false);
                }
            }
            if (fragment.getContext() == null || fragment.getParentActivity() == null) {
                return;
            }
//            if (AndroidUtilities.isTablet() && !tab.isWeb || true) {
                BotWebViewSheet sheet = new BotWebViewSheet(fragment.getContext(), fragment.getResourceProvider());
                sheet.setParentActivity(fragment.getParentActivity());
                if (sheet.restoreState(fragment, tab)) {
                    removeTab(tab, false);
                    sheet.show();
                }
//            } else {
//                BaseFragment sheetFragment = actionBarLayout.getSheetFragment();
//                if (sheetFragment == null) return;
//                BotWebViewAttachedSheet webViewSheet = sheetFragment.createBotViewer();
//                webViewSheet.setParentActivity(fragment.getParentActivity());
//                if (webViewSheet.restoreState(fragment, tab)) {
//                    removeTab(tab, false);
//                    webViewSheet.show(closed);
//                }
//            }
        };
        open.run(lastFragment);
        if (tab.needsContext && (!(lastFragment instanceof ChatActivity) || ((ChatActivity) lastFragment).getDialogId() != tab.props.botId)) {
            doNotDismiss = true;
            BaseFragment chatActivity = ChatActivity.of(tab.props.botId);
            AndroidUtilities.runOnUIThread(() -> {
                lastFragment.presentFragment(chatActivity);
                doNotDismiss = false;
            }, 220);
        }
    }

    public WebTabData tryReopenTab(WebViewRequestProps props) {
        ArrayList<WebTabData> tabs = this.tabs.get(currentAccount);
        if (tabs == null) this.tabs.put(currentAccount, tabs = new ArrayList<>());

        if (props == null) return null;
        for (int i = 0; i < tabs.size(); ++i) {
            WebTabData tab = tabs.get(i);
            if (props.equals(tab.props)) {
                openTab(tab);
                return tab;
            }
        }
        return null;
    }

    public static String urlWithoutFragment(String url) {
        if (url == null) return null;
        int index = url.indexOf('#');
        if (index >= 0) return url.substring(0, index + 1);
        return url;
    }

    public WebTabData tryReopenTab(String url) {
        if (TextUtils.isEmpty(url)) return null;
        final ArrayList<WebTabData> tabs = getTabs();
        for (int i = 0; i < tabs.size(); ++i) {
            WebTabData tab = tabs.get(i);
            if (tab.articleViewer != null && !tab.articleViewer.pagesStack.isEmpty()) {
                Object lastPage = tab.articleViewer.pagesStack.get(tab.articleViewer.pagesStack.size() - 1);
                if (lastPage instanceof ArticleViewer.CachedWeb) {
                    ArticleViewer.CachedWeb web = (ArticleViewer.CachedWeb) lastPage;
                    BotWebViewContainer.MyWebView webView = web.webView;
                    if (webView == null && tab.articleViewer.pages != null && tab.articleViewer.pages[0] != null) {
                        webView = tab.articleViewer.pages[0].getWebView();
                    }
                    if (webView != null && TextUtils.equals(urlWithoutFragment(webView.canGoBack() ? webView.getUrl() : webView.getOpenURL()), urlWithoutFragment(url))) {
                        openTab(tab);
                        return tab;
                    }
                }
            }
        }
        return null;
    }

    public WebTabData tryReopenTab(TLRPC.WebPage webpage) {
        if (webpage == null) return null;
        final ArrayList<WebTabData> tabs = getTabs();
        for (int i = 0; i < tabs.size(); ++i) {
            WebTabData tab = tabs.get(i);
            if (tab.articleViewer != null && !tab.articleViewer.pagesStack.isEmpty()) {
                Object lastPage = tab.articleViewer.pagesStack.get(tab.articleViewer.pagesStack.size() - 1);
                if (lastPage instanceof TLRPC.WebPage) {
                    TLRPC.WebPage pageWebPage = (TLRPC.WebPage) lastPage;
                    if (pageWebPage != null && pageWebPage.id == webpage.id) {
                        openTab(tab);
                        return tab;
                    }
                }
            }
        }
        return null;
    }

    public WebTabData tryReopenTab(MessageObject messageObject) {
        if (messageObject == null) return null;
        if (messageObject.messageOwner == null) return null;
        if (messageObject.messageOwner.media == null) return null;
        if (messageObject.messageOwner.media.webpage == null) return null;
        return tryReopenTab(messageObject.messageOwner.media.webpage);
    }

    public boolean closeAttachedSheets() {
        boolean had = false;
        BottomSheetTabsOverlay overlay = LaunchActivity.instance.getBottomSheetTabsOverlay();
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            for (int i = 0; fragment.sheetsStack != null && i < fragment.sheetsStack.size(); ++i) {
                BaseFragment.AttachedSheet sheet = fragment.sheetsStack.get(i);
                if (sheet instanceof BotWebViewAttachedSheet) {
                    if (overlay != null) {
                        overlay.setSlowerDismiss(true);
                    }
                    ((BotWebViewAttachedSheet) sheet).dismiss(true, null);
                    had = true;
                }
            }
        }
        return had;
    }

    private int backgroundColor;
    private AnimatedColor backgroundColorAnimated = new AnimatedColor(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
    private int tabColor;
    private AnimatedColor tabColorAnimated = new AnimatedColor(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean tabIsDark;
    private AnimatedFloat tabDarkAnimated = new AnimatedFloat(this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void setNavigationBarColor(int color) {
        setNavigationBarColor(color, true);
    }

    public void setNavigationBarColor(int color, boolean animated) {
        if (color != backgroundColor) {
            if (!actionBarLayout.startedTracking || actionBarLayout.animationInProgress) {
                animated = false;
            }
            backgroundColor = color;
            final boolean isDark = AndroidUtilities.computePerceivedBrightness(color) < .721f;
            tabColor = Theme.blendOver(color, Theme.multAlpha(0xFFFFFFFF, isDark ? .08f : .75f));
            tabIsDark = AndroidUtilities.computePerceivedBrightness(tabColor) < .721f;
            if (!animated) {
                backgroundColorAnimated.set(backgroundColor, true);
                tabColorAnimated.set(tabColor, true);
                tabDarkAnimated.set(tabIsDark, true);
            }
            invalidate();
        }
    }

    public int currentAccount = UserConfig.selectedAccount;
    public static final HashMap<Integer, ArrayList<WebTabData>> tabs = new HashMap<>();
    public static final HashMap<Integer, ArrayList<TabDrawable>> tabDrawables = new HashMap<>();

    public void updateCurrentAccount() {
        setCurrentAccount(UserConfig.selectedAccount);
    }

    public void setCurrentAccount(int account) {
        if (currentAccount != account) {
            currentAccount = account;

            updateVisibility(false);
            invalidate();
        }
    }

    public boolean isExpanded() {
        return !getTabs().isEmpty();
    }

    public int getExpandedHeight() {
        final int count = getTabs().size();
        if (count == 0) {
            return 0;
        } else if (count == 1) {
            return dp(60);
        } else {
            return dp(68);
        }
    }

    public ArrayList<WebTabData> getTabs() {
        return getTabs(this.currentAccount);
    }

    public ArrayList<TabDrawable> getTabDrawables() {
        return getTabDrawables(this.currentAccount);
    }

    public ArrayList<WebTabData> getTabs(int currentAccount) {
        ArrayList<WebTabData> tabs = BottomSheetTabs.tabs.get(currentAccount);
        if (tabs == null) BottomSheetTabs.tabs.put(currentAccount, tabs = new ArrayList<>());
        return tabs;
    }

    public ArrayList<TabDrawable> getTabDrawables(int currentAccount) {
        ArrayList<TabDrawable> tabDrawables = BottomSheetTabs.tabDrawables.get(currentAccount);
        if (tabDrawables == null) BottomSheetTabs.tabDrawables.put(currentAccount, tabDrawables = new ArrayList<>());
        return tabDrawables;
    }

    public TabDrawable findTabDrawable(WebTabData tab) {
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        for (int i = 0; i < tabDrawables.size(); ++i) {
            if (tabDrawables.get(i).tab == tab) {
                return tabDrawables.get(i);
            }
        }
        return null;
    }

    public TabDrawable pushTab(WebTabData tab) {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        TabDrawable tabDrawable = new TabDrawable(this, tab);
        tabDrawable.animatedPosition.set(-1, true);
        tabDrawable.animatedAlpha.set(0, true);
        tabDrawables.add(tabDrawable);

        tabs.add(0, tab);
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);
            final int index = tabs.indexOf(drawable.tab);
            drawable.index = index;
            if (index >= 0) {
                drawable.position = index;
            }
        }
        updateMultipleTitle();

        updateVisibility(true);

        invalidate();
        return tabDrawable;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || true;
    }

    private static TextPaint textPaint;
    private static TextPaint getTextPaint() {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(17));
        }
        return textPaint;
    }

    private void updateMultipleTitle() {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        CharSequence title = null;
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);

            if (tabs.size() > 1 && drawable.position == 0) {
                title = LocaleController.formatPluralString("BotMoreTabs", tabs.size() - 1, drawable.tab.getTitle());
                title = Emoji.replaceEmoji(title, getTextPaint().getFontMetricsInt(), false);
                drawable.setOverrideTitle(title);
            } else {
                title = drawable.tab.getTitle();
                title = Emoji.replaceEmoji(title, getTextPaint().getFontMetricsInt(), false);
                drawable.setOverrideTitle(null);
            }
        }

        if (tabs.isEmpty()) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setContentDescription(LocaleController.formatString(R.string.AccDescrTabs, ""));
        } else {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(LocaleController.formatString(R.string.AccDescrTabs, title == null ? "" : title));
        }
    }

    public boolean removeAll() {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        for (int i = 0; i < tabs.size(); ++i) {
            tabs.get(i).destroy();
        }
        tabs.clear();
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);
            drawable.index = -1;
        }
        updateMultipleTitle();
        updateVisibility(true);
        invalidate();
        return tabs.isEmpty();
    }

    public boolean tryRemoveTabWith(ArticleViewer articleViewer) {
        for (int account = 0; account < this.tabs.size(); ++account) {
            ArrayList<WebTabData> tabs = this.tabs.get(account);
            if (tabs != null) {
                for (WebTabData tab : tabs) {
                    if (tab.articleViewer == articleViewer) {
                        return removeTab(account, tab, true);
                    }
                }
            }
        }
        return false;
    }

    public void removeTab(WebTabData tab, Utilities.Callback<Boolean> callback) {
        if (tab == null) {
            callback.run(true);
            return;
        }
        if (!tab.confirmDismiss) {
            removeTab(tab, true);
            callback.run(true);
            return;
        }

        String botName = null;
        TLRPC.User user = MessagesController.getInstance(tab.props.currentAccount).getUser(tab.props.botId);
        if (user != null) {
            botName = ContactsController.formatName(user.first_name, user.last_name);
        }

        final boolean[] clicked = new boolean[] { false };
        final AlertDialog[] dialog = new AlertDialog[1];
        dialog[0] = new AlertDialog.Builder(getContext())
            .setTitle(botName)
            .setMessage(getString(R.string.BotWebViewChangesMayNotBeSaved))
            .setPositiveButton(getString(R.string.BotWebViewCloseAnyway), (d, w) -> {
                clicked[0] = true;
                removeTab(tab, true);
                callback.run(true);
                dialog[0].dismiss();
            })
            .setNegativeButton(getString(R.string.Cancel), (d, w) -> {
                clicked[0] = true;
                callback.run(false);
                dialog[0].dismiss();
            })
            .create();
        dialog[0].setOnDismissListener(v -> {
            if (!clicked[0]) {
                callback.run(false);
                clicked[0] = true;
            }
        });
        dialog[0].show();
        TextView textView = (TextView) dialog[0].getButton(AlertDialog.BUTTON_POSITIVE);
        textView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
    }

    public boolean removeTab(WebTabData tab, boolean destroy) {
        return removeTab(currentAccount, tab, destroy);
    }

    public boolean removeTab(int currentAccount, WebTabData tab, boolean destroy) {
        final ArrayList<WebTabData> tabs = getTabs(currentAccount);
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables(currentAccount);

        tabs.remove(tab);
        if (destroy) {
            tab.destroy();
        }
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);
            final int index = tabs.indexOf(drawable.tab);
            drawable.index = index;
            if (index >= 0) {
                drawable.position = index;
            }
        }
        updateMultipleTitle();
        final ArrayList<TabDrawable> finalTabDrawables = tabDrawables;
        AndroidUtilities.runOnUIThread(() -> {
            for (int i = 0; i < finalTabDrawables.size(); ++i) {
                TabDrawable drawable = finalTabDrawables.get(i);
                if (drawable.tab == tab) {
                    finalTabDrawables.remove(i);
                    i--;
                }
            }
            invalidate();
        }, 320);
        updateVisibility(true);
        invalidate();
        return tabs.isEmpty();
    }

    private boolean closeRippleHit;
    private boolean hit;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return touchEvent(event.getAction(), event.getX(), event.getY()) || super.onTouchEvent(event);
    }

    public boolean touchEvent(int action, float x, float y) {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        if (drawTabs) {
            WebTabData lastTab = tabs.isEmpty() ? null : tabs.get(0);
            TabDrawable drawable = findTabDrawable(lastTab);

            if (drawable != null) {
                getTabBounds(rect, drawable.getPosition());
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    closeRippleHit = drawable.closeRipple.getBounds().contains((int) (x - rect.left), (int) (y - rect.centerY()));
                    hit = !closeRippleHit && rect.contains(x, y);
                    drawable.closeRipple.setState(closeRippleHit ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[] {});
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (hit && action == MotionEvent.ACTION_UP) {
                        click();
                    } else if (closeRippleHit && action == MotionEvent.ACTION_UP) {
                        removeTab(lastTab, success -> {});
                    }
                    closeRippleHit = false;
                    hit = false;
                    drawable.closeRipple.setState(new int[] {});
                }
                for (int i = 0; i < tabDrawables.size(); ++i) {
                    if (tabDrawables.get(i) != drawable) {
                        tabDrawables.get(i).closeRipple.setState(new int[] {});
                    }
                }
            } else {
                hit = false;
                closeRippleHit = false;
            }
        } else {
            hit = false;
            closeRippleHit = false;
        }
        return hit || closeRippleHit;
    }

    public void click() {
        final ArrayList<WebTabData> tabs = getTabs();

        final int count = tabs.size();
        if (count == 0) return;
        WebTabData lastTab = tabs.get(tabs.size() - 1);
        BottomSheetTabsOverlay overlay = LaunchActivity.instance == null ? null : LaunchActivity.instance.getBottomSheetTabsOverlay();
        if (overlay != null) {
            overlay.stopAnimations();
        }

        if (count == 1 || overlay == null) {
            openTab(lastTab);
        } else {
            overlay.openTabsView();
        }
    }

    private final RectF rect = new RectF();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        if (bottomTabsProgress <= 0) {
            return;
        }

        backgroundPaint.setColor(backgroundColorAnimated.set(backgroundColor));
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        super.dispatchDraw(canvas);

        final int tabColor = tabColorAnimated.set(this.tabColor);
        final float tabIsDark = tabDarkAnimated.set(this.tabIsDark);
        if (drawTabs) {
            for (int i = 0; i < tabDrawables.size(); ++i) {
                final TabDrawable drawable = tabDrawables.get(i);
                float position = drawable.getPosition();
                float alpha = drawable.getAlpha();

                if (alpha <= 0) continue;
                if (position > 1.99f) continue;

                getTabBounds(rect, position);
                drawable.setExpandProgress(0f);
                drawable.setBackgroundColor(tabColor, tabIsDark > .5f);
                drawable.draw(canvas, rect, dp(10), alpha, 1f);
            }
        }
    }

    public void setupTab(TabDrawable drawable) {
        final int tabColor = tabColorAnimated.set(this.tabColor);
        final float tabIsDark = tabDarkAnimated.set(this.tabIsDark);
        drawable.setExpandProgress(0f);
        drawable.setBackgroundColor(tabColor, tabIsDark > .5f);
    }

    public void getTabBounds(RectF rect, float position) {
        rect.set(dp(4), getHeight() - dp(4) - dp(50), getWidth() - dp(4), getHeight() - dp(4));
        rect.offset(0, position * -dp(8));
        final float s = lerp(1f, .95f, Math.abs(position));
        final float cx = rect.centerX(), cy = rect.centerY(), w = rect.width(), h = rect.height();
        rect.left = cx - w / 2f * s;
        rect.right = cx + w / 2f * s;
        rect.top = cy - h / 2f * s;
        rect.bottom = cy + h / 2f * s;
    }


    public static class TabDrawable {

        public final WebTabData tab;
        public final View parentView;

        private int position;
        public int index;
        public final AnimatedFloat animatedPosition;
        public final AnimatedFloat animatedAlpha;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint faviconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public int closeRippleColor;
        public final Drawable closeRipple = Theme.createSelectorDrawable(0x30ffffff, Theme.RIPPLE_MASK_CIRCLE_20DP);

        private boolean tabColorOverride;
        private int backgroundColor, tabColor;
        private boolean backgroundIsDark, tabIsDark;

        private float progress;

        private Bitmap favicon;
        private Drawable iconDrawable;
        private int iconDrawableColor = Color.WHITE;
        private final Text title;
        private Text overrideTitle;

        public TabDrawable(View view, WebTabData tab) {
            parentView = view;
            this.tab = tab;
            closeRipple.setCallback(view);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);

            animatedPosition = new AnimatedFloat(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            animatedAlpha = new AnimatedFloat(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

            this.favicon = tab.favicon;
            CharSequence title = Emoji.replaceEmoji(tab.getTitle(), getTextPaint().getFontMetricsInt(), false);
            this.title = new Text(title, 17, AndroidUtilities.bold());
            this.tabColor = tab.actionBarColor;
            this.tabIsDark = AndroidUtilities.computePerceivedBrightness(tabColor) < .721f;
            if (tab.isArticle()) {
                iconDrawable = view.getContext().getResources().getDrawable(R.drawable.msg_instant).mutate();
            }
            progress = tab.articleProgress;

            closePath.rewind();
            closePath.moveTo(0, 0);
            closePath.lineTo(dp(12), dp(12));
            closePath.moveTo(dp(12), 0);
            closePath.lineTo(0, dp(12));

            expandPath.rewind();
            expandPath.moveTo(0, dp(6.33f) / 2f);
            expandPath.lineTo(dp(12.66f) / 2f, -dp(6.33f) / 2f);
            expandPath.lineTo(dp(12.66f), dp(6.33f) / 2f);
        }

        public void setOverrideTitle(CharSequence title) {
            if (title == null) {
                overrideTitle = null;
            } else {
                overrideTitle = new Text(title, 17, AndroidUtilities.bold());
            }
        }

        public float getPosition() {
            return index < 0 ? position : animatedPosition.set(position);
        }

        public float getAlpha() {
            final float position = getPosition();
            float positionalpha;
            if (position < 0)
                positionalpha = 1f + position;
            else if (position >= 0 && position < 1)
                positionalpha = lerp(1f, .87f, position);
            else
                positionalpha = .87f * (1f - Math.min(1, position - 1));
            return positionalpha * animatedAlpha.set(index >= 0);
        }

        public void setBackgroundColor(int color, boolean isDark) {
            backgroundColor = color;
            backgroundIsDark = isDark;
        }

        public void setOnCloseClick(Runnable listener) {

        }

        public void setOnExpandClick(Runnable listener) {

        }

        private float expandProgress;
        public void setExpandProgress(float expandProgress) {
            this.expandProgress = expandProgress;
        }

        private final float[] radii = new float[8];
        private final Path rectPath = new Path();
        private final Path closePath = new Path();
        private final Path expandPath = new Path();

        public void draw(Canvas canvas, RectF bounds, float r, float alpha, float contentAlpha) {
            final int backgroundColor = ColorUtils.blendARGB(this.backgroundColor, this.tabColor, expandProgress);
            backgroundPaint.setColor(backgroundColor);
            backgroundPaint.setAlpha((int) (0xFF * alpha));
            backgroundPaint.setShadowLayer(dp(2.33f), 0, dp(1), Theme.multAlpha(0x10000000, alpha));

            radii[0] = radii[1] = radii[2] = radii[3] = r;
            radii[4] = radii[5] = radii[6] = radii[7] = lerp(r, 0, expandProgress);
            rectPath.rewind();
            rectPath.addRoundRect(bounds, radii, Path.Direction.CW);
            canvas.drawPath(rectPath, backgroundPaint);

            if (progress > 0 && expandProgress > 0 && alpha > 0) {
                canvas.save();
                canvas.clipPath(rectPath);
                progressPaint.setColor(Theme.multAlpha(AndroidUtilities.computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE, .07f * alpha * expandProgress));
                canvas.drawRect(bounds.left, bounds.top, bounds.left + bounds.width() * progress, bounds.bottom, progressPaint);
                canvas.restore();
            }

            final float isDark = lerp(backgroundIsDark ? 1f : 0f, tabIsDark ? 1f : 0f, expandProgress);
            final int iconColor = ColorUtils.blendARGB(0xFF000000, 0xFFFFFFFF, isDark);

            iconPaint.setColor(iconColor);
            iconPaint.setStrokeWidth(dp(2));

            canvas.save();
            canvas.translate(bounds.left, bounds.centerY());
            int rippleColor = ColorUtils.blendARGB(0x20FFFFFF, 0x20FFFFFF, isDark);
            closeRipple.setBounds(
                (int) (dp(25) + -dp(25)),
                (int) ( + -dp(25)),
                (int) (dp(25) + dp(25)),
                (int) ( + dp(25))
            );
            if (closeRippleColor != rippleColor) {
                Theme.setSelectorDrawableColor(closeRipple, closeRippleColor = rippleColor, false);
            }
            closeRipple.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(bounds.left + dp(22 - 4), bounds.centerY() - dp(6));
            iconPaint.setAlpha((int) (0xFF * alpha * contentAlpha));
            canvas.drawPath(closePath, iconPaint);
            canvas.restore();

            canvas.save();
            canvas.translate(bounds.right - dp(22 - 4 + 12.66f), bounds.centerY());
            iconPaint.setAlpha((int) (0xFF * alpha * contentAlpha * (1f - expandProgress)));
            canvas.drawPath(expandPath, iconPaint);
            canvas.restore();

            int leftPadding = 0;
            if (favicon != null) {
                final int sz = dp(24);

                canvas.save();
                AndroidUtilities.rectTmp2.set(
                    (int) (bounds.left + dp(56)),
                    (int) (bounds.centerY() - sz / 2f),
                    (int) (bounds.left + dp(56) + sz),
                    (int) (bounds.centerY() + sz / 2f)
                );
                faviconPaint.setAlpha((int) (0xFF * alpha * contentAlpha));
                canvas.drawBitmap(favicon, null, AndroidUtilities.rectTmp2, faviconPaint);
                canvas.restore();

                leftPadding = sz + dp(4);
            } else if (iconDrawable != null) {
                final int sz = dp(24);

                final int h = sz;
                final int w = (int) (sz / (float) iconDrawable.getIntrinsicHeight() * iconDrawable.getIntrinsicWidth());
                final float s = .7f;

                AndroidUtilities.rectTmp2.set(
                    (int) (bounds.left + dp(56)),
                    (int) (bounds.centerY() - h / 2f * s),
                    (int) (bounds.left + dp(56) + w * s),
                    (int) (bounds.centerY() + h / 2f * s)
                );
                if (iconColor != iconDrawableColor) {
                    iconDrawable.setColorFilter(new PorterDuffColorFilter(iconDrawableColor = iconColor, PorterDuff.Mode.SRC_IN));
                }
                iconDrawable.setAlpha((int) (0xFF * alpha * contentAlpha));
                iconDrawable.setBounds(AndroidUtilities.rectTmp2);
                iconDrawable.draw(canvas);

                leftPadding = w - dp(2);
            }

            if (overrideTitle != null) {
                overrideTitle
                    .ellipsize((int) (bounds.width() - dp(100) - leftPadding))
                    .draw(canvas, bounds.left + dp(60) + leftPadding, bounds.centerY(), iconColor, (1f - expandProgress) * alpha * contentAlpha);
            }
            title
                .ellipsize((int) (bounds.width() - dp(100) - leftPadding))
                .draw(canvas, bounds.left + dp(60) + leftPadding, bounds.centerY(), iconColor, (overrideTitle == null ? 1f : expandProgress) * alpha * contentAlpha);
        }

    }

    public static class WebTabData {

        public WebViewRequestProps props;
        public Bundle webViewState;
        public BotWebViewContainer.MyWebView webView;
        public View view2;
        public Object proxy;
        public int viewWidth, viewHeight;
        public int viewScroll;
        public boolean expanded;
        public float expandedOffset = Float.MAX_VALUE;
        public boolean allowSwipes = true;

        public Bitmap previewBitmap;
        public Object previewNode;

        public boolean overrideActionBarColor;
        public boolean overrideBackgroundColor;
        public int actionBarColorKey;
        public int actionBarColor;
        public int backgroundColor;

        public int navigationBarColor;

        public boolean ready;
        public boolean backButton;
        public boolean settings;
        public BotWebViewAttachedSheet.MainButtonSettings main;
        public BotButtons.ButtonsState buttons;
        public String lastUrl;
        public boolean confirmDismiss;

        public boolean fullscreen;
        public boolean fullsize;
        public boolean needsContext;

        public boolean themeIsDark;

        public boolean isWeb;
        public String title;
        public Bitmap favicon;
        public String startUrl;
        public String currentUrl;

        public boolean error;
        public int errorCode;
        public String errorDescription;

        public float articleProgress;
        public ArticleViewer articleViewer;

        public BotSensors sensors;

        public boolean orientationLocked;

        public long getBotId() {
            if (props == null) return 0;
            return props.botId;
        }

        public void destroy() {
            try {
                if (webView != null) {
                    webView.destroy();
                    webView = null;
                }
                if (articleViewer != null) {
                    articleViewer.destroy();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public boolean isArticle() {
            return articleViewer != null && articleViewer.isLastArticle();
        }

        public String getTitle() {
            if (isWeb || articleViewer != null) {
                if (TextUtils.isEmpty(title))
                    return getString(R.string.WebEmpty);
                return title;
            }
            if (props == null) return "";
            TLRPC.User user = MessagesController.getInstance(props.currentAccount).getUser(props.botId);
            return UserObject.getUserName(user);
        }

    }

    private ValueAnimator bottomTabsAnimator;
    public float bottomTabsProgress;
    public int bottomTabsHeight;

    public void updateVisibility(boolean animated) {
        if (bottomTabsHeight == getExpandedHeight())
            return;
        if (bottomTabsAnimator != null) {
            ValueAnimator prev = bottomTabsAnimator;
            bottomTabsAnimator = null;
            prev.cancel();
        }
        bottomTabsHeight = getExpandedHeight();
        for (Runnable l : relayoutListeners)
            l.run();
        if (animated) {
            bottomTabsAnimator = ValueAnimator.ofFloat(bottomTabsProgress, bottomTabsHeight);
            bottomTabsAnimator.addUpdateListener(anm -> {
                bottomTabsProgress = (float) anm.getAnimatedValue();
                for (Runnable l : invalidateListeners)
                    l.run();
                invalidate();
            });
            bottomTabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bottomTabsAnimator == animation) {
                        bottomTabsProgress = bottomTabsHeight;
                        for (Runnable l : invalidateListeners)
                            l.run();
                    }
                }
            });
            bottomTabsAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
            bottomTabsAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
            bottomTabsAnimator.start();
        } else {
            bottomTabsProgress = bottomTabsHeight;
            invalidate();
        }
    }

    public static class ClipTools {

        private final BottomSheetTabs tabs;
        private final RectF clipRect = new RectF();
        private final float[] clipRadius = new float[8];
        private final Path clipPath = new Path();
        private final Paint clipShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ClipTools(BottomSheetTabs tabs) {
            this.tabs = tabs;
        }

        public void clip(Canvas canvas, boolean withShadow, boolean isKeyboardVisible, int width, int height, float visible) {
            final int bottomSheetHeight = (int) ((isKeyboardVisible ? 0 : tabs.getHeight(true)) * visible);
            final int bottomRadius = Math.min(1, bottomSheetHeight / dp(60)) * dp(10);
            if (bottomSheetHeight <= 0)
                return;

            clipRadius[0] = clipRadius[1] = clipRadius[2] = clipRadius[3] = 0; // top
            clipRadius[4] = clipRadius[5] = clipRadius[6] = clipRadius[7] = bottomRadius; // bottom

            clipPath.rewind();
            clipRect.set(0, 0, width, tabs.getY() + tabs.getHeight() - bottomSheetHeight);
            clipPath.addRoundRect(clipRect, clipRadius, Path.Direction.CW);

            clipShadowPaint.setAlpha(0);
            if (withShadow) {
                clipShadowPaint.setShadowLayer(dp(2), 0, dp(1), 0x10000000);
                canvas.drawPath(clipPath, clipShadowPaint);
            }
            canvas.clipPath(clipPath);
        }

    }

    public int getHeight(boolean animated) {
        if (animated) {
            return (int) bottomTabsProgress;
        } else {
            return bottomTabsHeight;
        }
    }

    private final HashSet<Runnable> invalidateListeners = new HashSet<>();
    private final HashSet<Runnable> relayoutListeners = new HashSet<>();
    public void listen(Runnable invalidate, Runnable relayout) {
        invalidateListeners.add(invalidate);
        relayoutListeners.add(relayout);
    }
    public void stopListening(Runnable invalidate, Runnable relayout) {
        invalidateListeners.remove(invalidate);
        relayoutListeners.remove(relayout);
    }

}

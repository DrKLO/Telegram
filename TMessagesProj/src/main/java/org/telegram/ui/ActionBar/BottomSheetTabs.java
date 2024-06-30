package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.bots.BotWebViewContainer;
import org.telegram.ui.bots.BotWebViewSheet;

import java.io.CharArrayReader;
import java.util.ArrayList;
import java.util.HashMap;

public class BottomSheetTabs extends FrameLayout {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public boolean drawTabs = true;

    private final ActionBarLayout actionBarLayout;

    public BottomSheetTabs(Context context, ActionBarLayout actionBarLayout) {
        super(context);
        this.actionBarLayout = actionBarLayout;

        setNavigationBarColor(Theme.getColor(Theme.key_windowBackgroundGray));

        setOnClickListener(v -> {
            final ArrayList<WebTabData> tabs = getTabs();

            final int count = tabs.size();
            if (count == 0) return;
            WebTabData lastTab = tabs.get(tabs.size() - 1);
            BottomSheetTabsOverlay overlay = LaunchActivity.instance.getBottomSheetTabsOverlay();

            if (count == 1 || overlay == null) {
                openTab(lastTab);
            } else {
                overlay.openTabsView();
            }
        });

        updateMultipleTitle();
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
        boolean closed = closeAttachedSheets();
        Utilities.Callback<BaseFragment> open = fragment -> {
            if (fragment instanceof ChatActivity) {
                if (((ChatActivity) fragment).getChatActivityEnterView() != null) {
                    ((ChatActivity) fragment).getChatActivityEnterView().closeKeyboard();
                    ((ChatActivity) fragment).getChatActivityEnterView().hidePopup(true, false);
                }
            }
            if (AndroidUtilities.isTablet()) {
                BotWebViewSheet sheet = new BotWebViewSheet(fragment.getContext(), fragment.getResourceProvider());
                sheet.setParentActivity(fragment.getParentActivity());
                if (sheet.restoreState(fragment, tab)) {
                    removeTab(tab, false);
                    sheet.show();
                }
            } else {
                BotWebViewAttachedSheet webViewSheet = fragment.createBotViewer();
                webViewSheet.setParentActivity(fragment.getParentActivity());
                if (webViewSheet.restoreState(fragment, tab)) {
                    removeTab(tab, false);
                    webViewSheet.show(closed);
                }
            }
        };
        if (tab.needsContext && (!(lastFragment instanceof ChatActivity) || ((ChatActivity) lastFragment).getDialogId() != tab.props.botId)) {
            BaseFragment chatActivity = ChatActivity.of(tab.props.botId);
            lastFragment.presentFragment(chatActivity);
            AndroidUtilities.runOnUIThread(() -> {
                open.run(chatActivity);
            }, 200);
        } else {
            open.run(lastFragment);
        }
    }

    public WebTabData tryReopenTab(BotWebViewAttachedSheet.WebViewRequestProps props) {
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
    public final HashMap<Integer, ArrayList<WebTabData>> tabs = new HashMap<>();
    public final HashMap<Integer, ArrayList<TabDrawable>> tabDrawables = new HashMap<>();

    public void updateCurrentAccount() {
        setCurrentAccount(UserConfig.selectedAccount);
    }

    public void setCurrentAccount(int account) {
        if (currentAccount != account) {
            currentAccount = account;

            actionBarLayout.updateBottomTabsVisibility(false);
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
        ArrayList<WebTabData> tabs = this.tabs.get(currentAccount);
        if (tabs == null) this.tabs.put(currentAccount, tabs = new ArrayList<>());
        return tabs;
    }

    public ArrayList<TabDrawable> getTabDrawables() {
        ArrayList<TabDrawable> tabDrawables = this.tabDrawables.get(currentAccount);
        if (tabDrawables == null) this.tabDrawables.put(currentAccount, tabDrawables = new ArrayList<>());
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

        actionBarLayout.updateBottomTabsVisibility(true);

        invalidate();
        return tabDrawable;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || true;
    }

    private void updateMultipleTitle() {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        String title = null;
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);

            if (tabs.size() > 1 && drawable.position == 0) {
                TLRPC.User user = MessagesController.getInstance(drawable.tab.props.currentAccount).getUser(drawable.tab.props.botId);
                title = LocaleController.formatPluralString("BotMoreTabs", tabs.size() - 1, UserObject.getUserName(user));
                drawable.setOverrideTitle(title);
            } else {
                TLRPC.User user = MessagesController.getInstance(drawable.tab.props.currentAccount).getUser(drawable.tab.props.botId);
                title = UserObject.getUserName(user);
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
        actionBarLayout.updateBottomTabsVisibility(true);
        invalidate();
        return tabs.isEmpty();
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
            .setMessage(LocaleController.getString(R.string.BotWebViewChangesMayNotBeSaved))
            .setPositiveButton(LocaleController.getString(R.string.BotWebViewCloseAnyway), (d, w) -> {
                clicked[0] = true;
                removeTab(tab, true);
                callback.run(true);
                dialog[0].dismiss();
            })
            .setNegativeButton(LocaleController.getString(R.string.Cancel), (d, w) -> {
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
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

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
        actionBarLayout.updateBottomTabsVisibility(true);
        invalidate();
        return tabs.isEmpty();
    }

    private boolean closeRippleHit;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

        if (drawTabs) {

            WebTabData lastTab = tabs.isEmpty() ? null : tabs.get(0);
            TabDrawable drawable = findTabDrawable(lastTab);

            if (drawable != null) {
                getTabBounds(rect, drawable.getPosition());
                final boolean closeHit = drawable.closeRipple.getBounds().contains((int) (event.getX() - rect.left), (int) (event.getY() - rect.centerY()));
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    closeRippleHit = closeHit;
                    drawable.closeRipple.setState(closeHit ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[] {});
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (closeRippleHit && event.getAction() == MotionEvent.ACTION_UP) {
                        removeTab(lastTab, success -> {});
                    }
                    closeRippleHit = false;
                    drawable.closeRipple.setState(new int[] {});
                }
                for (int i = 0; i < tabDrawables.size(); ++i) {
                    if (tabDrawables.get(i) != drawable) {
                        tabDrawables.get(i).closeRipple.setState(new int[] {});
                    }
                }
            } else {
                closeRippleHit = false;
            }
        }
        if (closeRippleHit) return true;
        return super.onTouchEvent(event);
    }

    private final RectF rect = new RectF();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final ArrayList<WebTabData> tabs = getTabs();
        final ArrayList<TabDrawable> tabDrawables = getTabDrawables();

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
                drawable.draw(canvas, rect, dp(10), alpha);
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
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public int closeRippleColor;
        public final Drawable closeRipple = Theme.createSelectorDrawable(0x30ffffff, Theme.RIPPLE_MASK_CIRCLE_20DP);

        private boolean tabColorOverride;
        private int backgroundColor, tabColor;
        private boolean backgroundIsDark, tabIsDark;

        private Text title;
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

            TLRPC.User user = MessagesController.getInstance(tab.props.currentAccount).getUser(tab.getBotId());
            this.title = new Text(UserObject.getUserName(user), 17, AndroidUtilities.bold());
            this.tabColor = tab.actionBarColor;
            this.tabIsDark = AndroidUtilities.computePerceivedBrightness(tabColor) < .721f;

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

        public void setOverrideTitle(String title) {
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

        public void draw(Canvas canvas, RectF bounds, float r, float alpha) {
            final int backgroundColor = ColorUtils.blendARGB(this.backgroundColor, this.tabColor, expandProgress);
            backgroundPaint.setColor(backgroundColor);
            backgroundPaint.setAlpha((int) (0xFF * alpha));
            backgroundPaint.setShadowLayer(dp(2.33f), 0, dp(1), Theme.multAlpha(0x10000000, alpha));

            radii[0] = radii[1] = radii[2] = radii[3] = r;
            radii[4] = radii[5] = radii[6] = radii[7] = lerp(r, 0, expandProgress);
            rectPath.rewind();
            rectPath.addRoundRect(bounds, radii, Path.Direction.CW);
            canvas.drawPath(rectPath, backgroundPaint);

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
            iconPaint.setAlpha((int) (0xFF * alpha));
            canvas.drawPath(closePath, iconPaint);
            canvas.restore();

            canvas.save();
            canvas.translate(bounds.right - dp(22 - 4 + 12.66f), bounds.centerY());
            iconPaint.setAlpha((int) (0xFF * alpha * (1f - expandProgress)));
            canvas.drawPath(expandPath, iconPaint);
            canvas.restore();

            if (overrideTitle != null) {
                overrideTitle
                    .ellipsize((int) (bounds.width() - dp(100)))
                    .draw(canvas, bounds.left + dp(60), bounds.centerY(), iconColor, (1f - expandProgress) * alpha);
            }
            title
                .ellipsize((int) (bounds.width() - dp(100)))
                .draw(canvas, bounds.left + dp(60), bounds.centerY(), iconColor, (overrideTitle == null ? 1f : expandProgress) * alpha);
        }

    }

    public static class WebTabData {

        public BotWebViewAttachedSheet.WebViewRequestProps props;
        public Bundle webViewState;
        public BotWebViewContainer.MyWebView webView;
        public BotWebViewContainer.WebViewProxy webViewProxy;
        public int webViewWidth, webViewHeight;
        public int webViewScroll;
        public boolean expanded;
        public float expandedOffset = Float.MAX_VALUE;

        public Bitmap previewBitmap;
        public Object previewNode;

        public boolean overrideActionBarColor;
        public int actionBarColorKey;
        public int actionBarColor;
        public int backgroundColor;

        public boolean ready;
        public boolean backButton;
        public boolean settings;
        public BotWebViewAttachedSheet.MainButtonSettings main;
        public String lastUrl;
        public boolean confirmDismiss;

        public boolean fullsize;
        public boolean needsContext;

        public boolean themeIsDark;

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
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

    }

}

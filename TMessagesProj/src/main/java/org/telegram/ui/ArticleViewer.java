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
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.MetricAffectingSpan;
import android.text.style.URLSpan;
import android.util.Property;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnchorSpan;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.TableLayout;
import org.telegram.ui.Components.TextPaintImageReceiverSpan;
import org.telegram.ui.Components.TextPaintMarkSpan;
import org.telegram.ui.Components.TextPaintSpan;
import org.telegram.ui.Components.TextPaintUrlSpan;
import org.telegram.ui.Components.TextPaintWebpageUrlSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.WebPlayerView;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.telegram.messenger.MessageObject.POSITION_FLAG_BOTTOM;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_LEFT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_RIGHT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_TOP;

public class ArticleViewer implements NotificationCenter.NotificationCenterDelegate {

    private Activity parentActivity;
    private BaseFragment parentFragment;
    private ArrayList<BlockEmbedCell> createdWebViews = new ArrayList<>();

    private View customView;
    private FrameLayout fullscreenVideoContainer;
    private TextureView fullscreenTextureView;
    private AspectRatioFrameLayout fullscreenAspectRatioView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private TLRPC.Chat loadedChannel;
    private boolean loadingChannel;

    private Object lastInsets;
    private boolean hasCutout;

    private boolean isVisible;
    private boolean collapsed;
    private boolean attachedToWindow;
    
    private int currentAccount;

    private int lastBlockNum = 1;

    private int animationInProgress;
    private Runnable animationEndRunnable;
    private long transitionAnimationStartTime;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);

    private ArrayList<TLRPC.WebPage> pagesStack = new ArrayList<>();

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowView windowView;
    private FrameLayout containerView;
    private FrameLayout headerView;
    private SimpleTextView titleTextView;
    private LineProgressView lineProgressView;
    private Runnable lineProgressTickRunnable;
    private ImageView backButton;
    private ActionBarMenuItem menuButton;
    private FrameLayout menuContainer;
    private ContextProgressView progressView;
    private BackDrawable backDrawable;
    private Dialog visibleDialog;
    private Paint backgroundPaint;
    private Drawable layerShadowDrawable;
    private Paint scrimPaint;
    private AnimatorSet progressViewAnimation;

    private AnimatorSet runAfterKeyboardClose;
    private boolean keyboardVisible;

    private FrameLayout searchContainer;
    private ImageView clearButton;
    private EditTextBoldCursor searchField;
    private boolean animateClear = true;
    private boolean ignoreOnTextChange;
    private View searchShadow;

    private FrameLayout searchPanel;
    private ImageView searchUpButton;
    private ImageView searchDownButton;
    private SimpleTextView searchCountText;

    private RecyclerListView[] listView;
    private LinearLayoutManager[] layoutManager;
    private WebpageAdapter[] adapter;

    private AnimatorSet pageSwitchAnimation;

    private Paint headerPaint = new Paint();
    private Paint statusBarPaint = new Paint();
    private Paint headerProgressPaint = new Paint();

    private ActionBarPopupWindow popupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private TextView deleteView;
    private Rect popupRect;

    private WebPlayerView currentPlayingVideo;
    private WebPlayerView fullscreenedVideo;

    private Drawable slideDotDrawable;
    private Drawable slideDotBigDrawable;

    private int openUrlReqId;
    private int previewsReqId;
    private int lastReqId;

    private int currentHeaderHeight;

    private boolean checkingForLongPress = false;
    private CheckForLongPress pendingCheckForLongPress = null;
    private int pressCount = 0;
    private CheckForTap pendingCheckForTap = null;

    private TextPaintUrlSpan pressedLink;
    private BottomSheet linkSheet;
    private int pressedLayoutY;
    private DrawingText pressedLinkOwnerLayout;
    private View pressedLinkOwnerView;
    private boolean drawBlockSelection;
    private LinkPath urlPath = new LinkPath();

    private static final int search_item = 1;
    private static final int share_item = 2;
    private static final int open_item = 3;
    private static final int settings_item = 4;

    private int anchorsOffsetMeasuredWidth;

    TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
    TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelperBottomSheet;

    PinchToZoomHelper pinchToZoomHelper;

    private int allowAnimationIndex = -1;

    private final String BOTTOM_SHEET_VIEW_TAG = "bottomSheet";

    @SuppressLint("StaticFieldLeak")
    private static volatile ArticleViewer Instance = null;

    public static ArticleViewer getInstance() {
        ArticleViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (ArticleViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ArticleViewer();
                }
            }
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    private int selectedFont = 0;
    private FontCell[] fontCells = new FontCell[2];

    private static class TL_pageBlockRelatedArticlesChild extends TLRPC.PageBlock {
        private TLRPC.TL_pageBlockRelatedArticles parent;
        private int num;
    }

    private static class TL_pageBlockRelatedArticlesShadow extends TLRPC.PageBlock {
        private TLRPC.TL_pageBlockRelatedArticles parent;
    }

    private static class TL_pageBlockDetailsChild extends TLRPC.PageBlock {
        private TLRPC.PageBlock parent;
        private TLRPC.PageBlock block;
    }

    private static class TL_pageBlockDetailsBottom extends TLRPC.PageBlock {
        private TLRPC.TL_pageBlockDetails parent;
    }

    private class TL_pageBlockListParent extends TLRPC.PageBlock {
        private TLRPC.TL_pageBlockList pageBlockList;
        private ArrayList<TL_pageBlockListItem> items = new ArrayList<>();
        private int maxNumWidth;
        private int lastMaxNumCalcWidth;
        private int lastFontSize;
        private int level;
    }

    private class TL_pageBlockListItem extends TLRPC.PageBlock {
        private TL_pageBlockListParent parent;
        private TLRPC.PageBlock blockItem;
        private TLRPC.RichText textItem;
        private String num;
        private DrawingText numLayout;
        private int index = Integer.MAX_VALUE;
    }

    private class TL_pageBlockOrderedListParent extends TLRPC.PageBlock {
        private TLRPC.TL_pageBlockOrderedList pageBlockOrderedList;
        private ArrayList<TL_pageBlockOrderedListItem> items = new ArrayList<>();
        private int maxNumWidth;
        private int lastMaxNumCalcWidth;
        private int lastFontSize;
        private int level;
    }

    private class TL_pageBlockOrderedListItem extends TLRPC.PageBlock {
        private TL_pageBlockOrderedListParent parent;
        private TLRPC.PageBlock blockItem;
        private TLRPC.RichText textItem;
        private String num;
        private DrawingText numLayout;
        private int index = Integer.MAX_VALUE;
    }

    private static class TL_pageBlockEmbedPostCaption extends TLRPC.TL_pageBlockEmbedPost {
        private TLRPC.TL_pageBlockEmbedPost parent;
    }

    public class DrawingText implements TextSelectionHelper.TextLayoutBlock {
        public StaticLayout textLayout;
        public LinkPath textPath;
        public LinkPath markPath;
        public LinkPath searchPath;
        public int searchIndex = -1;
        public TLRPC.PageBlock parentBlock;
        public Object parentText;
        public int x;
        public int y;
        public int row;
        public CharSequence prefix;

        public void draw(Canvas canvas) {
            if (!searchResults.isEmpty()) {
                SearchResult result = searchResults.get(currentSearchIndex);
                if (result.block == parentBlock && (result.text == parentText || result.text instanceof String && parentText == null)) {
                    if (searchIndex != result.index) {
                        searchPath = new LinkPath(true);
                        searchPath.setAllowReset(false);
                        searchPath.setCurrentLayout(textLayout, result.index, 0);
                        searchPath.setBaselineShift(0);
                        textLayout.getSelectionPath(result.index, result.index + searchText.length(), searchPath);
                        searchPath.setAllowReset(true);
                    }
                } else {
                    searchIndex = -1;
                    searchPath = null;
                }
            } else {
                searchIndex = -1;
                searchPath = null;
            }
            if (searchPath != null) {
                canvas.drawPath(searchPath, webpageSearchPaint);
            }
            if (textPath != null) {
                canvas.drawPath(textPath, webpageUrlPaint);
            }
            if (markPath != null) {
                canvas.drawPath(markPath, webpageMarkPaint);
            }
            drawLayoutLink(canvas, this);
            textLayout.draw(canvas);
        }

        public CharSequence getText() {
            return textLayout.getText();
        }

        public int getLineCount() {
            return textLayout.getLineCount();
        }

        public int getLineAscent(int line) {
            return textLayout.getLineAscent(line);
        }

        public float getLineLeft(int line) {
            return textLayout.getLineLeft(line);
        }

        public float getLineWidth(int line) {
            return textLayout.getLineWidth(line);
        }

        public int getHeight() {
            return textLayout.getHeight();
        }

        public int getWidth() {
            return textLayout.getWidth();
        }

        @Override
        public StaticLayout getLayout() {
            return textLayout;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getRow() {
            return row;
        }

        @Override
        public CharSequence getPrefix() {
            return prefix;
        }
    }

    private class TextSizeCell extends FrameLayout {

        private SeekBarView sizeBar;
        private int startFontSize = 12;
        private int endFontSize = 30;
        private int lastWidth;

        private TextPaint textPaint;

        public TextSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int fontSize = Math.round(startFontSize + (endFontSize - startFontSize) * progress);
                    if (fontSize != SharedConfig.ivFontSize) {
                        SharedConfig.ivFontSize = fontSize;
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt("iv_font_size", SharedConfig.ivFontSize);
                        editor.commit();
                        adapter[0].searchTextOffset.clear();
                        updatePaintSize();
                        invalidate();
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(startFontSize + (endFontSize - startFontSize) * sizeBar.getProgress()));
                }

                @Override
                public int getStepsCount() {
                    return endFontSize - startFontSize;
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + SharedConfig.ivFontSize, getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int w = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != w) {
                sizeBar.setProgress((SharedConfig.ivFontSize - startFontSize) / (float) (endFontSize - startFontSize));
                lastWidth = w;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }
    }

    public static class FontCell extends FrameLayout {

        private TextView textView;
        private RadioButton radioButton;

        public FontCell(Context context) {
            super(context);

            setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));

            radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setColor(Theme.getColor(Theme.key_dialogRadioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 22), 13, (LocaleController.isRTL ? 22 : 0), 0));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 17 + 45), 0, (LocaleController.isRTL ? 17 + 45 : 17), 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void select(boolean value, boolean animated) {
            radioButton.setChecked(value, animated);
        }

        public void setTextAndTypeface(String text, Typeface typeface) {
            textView.setText(text);
            textView.setTypeface(typeface);
            setContentDescription(text);
            invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(RadioButton.class.getName());
            info.setChecked(radioButton.isChecked());
            info.setCheckable(true);
        }
    }

    private final class CheckForTap implements Runnable {
        public void run() {
            if (pendingCheckForLongPress == null) {
                pendingCheckForLongPress = new CheckForLongPress();
            }
            pendingCheckForLongPress.currentPressCount = ++pressCount;
            if (windowView != null) {
                windowView.postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
            }
        }
    }

    public static final Property<WindowView, Float> ARTICLE_VIEWER_INNER_TRANSLATION_X = new AnimationProperties.FloatProperty<WindowView>("innerTranslationX") {
        @Override
        public void setValue(WindowView object, float value) {
            object.setInnerTranslationX(value);
        }

        @Override
        public Float get(WindowView object) {
            return object.getInnerTranslationX();
        }
    };

    private boolean closeAnimationInProgress;

    private class WindowView extends FrameLayout {

        private final Paint blackPaint = new Paint();

        private Runnable attachRunnable;
        private boolean selfLayout;
        private int startedTrackingPointerId;
        private boolean maybeStartTracking;
        private boolean startedTracking;
        private boolean movingPage;
        private int startMovingHeaderHeight;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker tracker;
        private float innerTranslationX;
        private float alpha;

        private int bX, bWidth, bY, bHeight;

        public WindowView(Context context) {
            super(context);
        }

        @TargetApi(21)
        @Override
        public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
            WindowInsets oldInsets = (WindowInsets) lastInsets;
            lastInsets = insets;
            if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                if (windowView != null) {
                    windowView.requestLayout();
                }
            }
            if (Build.VERSION.SDK_INT >= 28 && parentActivity != null) {
                DisplayCutout cutout = parentActivity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
                if (cutout != null) {
                    List<Rect> rects = cutout.getBoundingRects();
                    if (rects != null && !rects.isEmpty()) {
                        hasCutout = rects.get(0).height() != 0;
                    }
                }
            }
            return super.dispatchApplyWindowInsets(insets);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);
            if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                setMeasuredDimension(widthSize, heightSize);
                WindowInsets insets = (WindowInsets) lastInsets;
                if (AndroidUtilities.incorrectDisplaySizeFix) {
                    if (heightSize > AndroidUtilities.displaySize.y) {
                        heightSize = AndroidUtilities.displaySize.y;
                    }
                    heightSize += AndroidUtilities.statusBarHeight;
                }
                heightSize -= insets.getSystemWindowInsetBottom();
                widthSize -= insets.getSystemWindowInsetRight() + insets.getSystemWindowInsetLeft();
                if (insets.getSystemWindowInsetRight() != 0) {
                    bWidth = insets.getSystemWindowInsetRight();
                    bHeight = heightSize;
                } else if (insets.getSystemWindowInsetLeft() != 0) {
                    bWidth = insets.getSystemWindowInsetLeft();
                    bHeight = heightSize;
                } else {
                    bWidth = widthSize;
                    bHeight = insets.getStableInsetBottom();
                }
                heightSize -= insets.getSystemWindowInsetTop();
            } else {
                setMeasuredDimension(widthSize, heightSize);
            }
            menuButton.setAdditionalYOffset(-(currentHeaderHeight - AndroidUtilities.dp(56)) / 2 + (Build.VERSION.SDK_INT < 21 ? AndroidUtilities.statusBarHeight : 0));
            keyboardVisible = heightSize < AndroidUtilities.displaySize.y - AndroidUtilities.dp(100);
            containerView.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
            fullscreenVideoContainer.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (pinchToZoomHelper.isInOverlayMode()) {
                ev.offsetLocation(-containerView.getX(), -containerView.getY());
                return pinchToZoomHelper.onTouchEvent(ev);
            }
            TextSelectionHelper.TextSelectionOverlay selectionOverlay = textSelectionHelper.getOverlayView(getContext());
            MotionEvent textSelectionEv = MotionEvent.obtain(ev);
            textSelectionEv.offsetLocation(-containerView.getX(), -containerView.getY());

            if (textSelectionHelper.isSelectionMode() && textSelectionHelper.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                return true;
            }

            if (selectionOverlay.checkOnTap(ev)) {
                ev.setAction(MotionEvent.ACTION_CANCEL);
            }

            if (ev.getAction() == MotionEvent.ACTION_DOWN && textSelectionHelper.isSelectionMode() && (ev.getY() < containerView.getTop() || ev.getY() > containerView.getBottom())) {
                if (textSelectionHelper.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                    return super.dispatchTouchEvent(ev);
                } else {
                    return true;
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        @SuppressWarnings("DrawAllocation")
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (selfLayout) {
                return;
            }
            int width = right - left;
            if (anchorsOffsetMeasuredWidth != width) {
                for (int i = 0; i < listView.length; i++) {
                    for (HashMap.Entry<String, Integer> entry : adapter[i].anchorsOffset.entrySet()) {
                        entry.setValue(-1);
                    }
                }
                anchorsOffsetMeasuredWidth = width;
            }
            int x;
            int y = 0;
            if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                WindowInsets insets = (WindowInsets) lastInsets;
                x = insets.getSystemWindowInsetLeft();

                if (insets.getSystemWindowInsetRight() != 0) {
                    bX = width - bWidth;
                    bY = 0;
                } else if (insets.getSystemWindowInsetLeft() != 0) {
                    bX = 0;
                    bY = 0;
                } else {
                    bX = 0;
                    bY = bottom - top - bHeight;
                }
                y += insets.getSystemWindowInsetTop();
            } else {
                x = 0;
            }
            containerView.layout(x, y, x + containerView.getMeasuredWidth(), y + containerView.getMeasuredHeight());
            fullscreenVideoContainer.layout(x, y, x + fullscreenVideoContainer.getMeasuredWidth(), y + fullscreenVideoContainer.getMeasuredHeight());
            if (runAfterKeyboardClose != null) {
                runAfterKeyboardClose.start();
                runAfterKeyboardClose = null;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachedToWindow = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attachedToWindow = false;
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            handleTouchEvent(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return !collapsed && (handleTouchEvent(ev) || super.onInterceptTouchEvent(ev));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return !collapsed && (handleTouchEvent(event) || super.onTouchEvent(event));
        }

        @Keep
        public void setInnerTranslationX(float value) {
            innerTranslationX = value;
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isVisible || alpha != 1.0f || innerTranslationX != 0);
            }
            invalidate();
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            int width = getMeasuredWidth();
            int translationX = (int) innerTranslationX;

            final int restoreCount = canvas.save();
            canvas.clipRect(translationX, 0, width, getHeight());
            final boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(restoreCount);

            if (translationX != 0 && child == containerView) {
                float opacity = Math.min(0.8f, (width - translationX) / (float) width);
                if (opacity < 0) {
                    opacity = 0;
                }
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * opacity) << 24);
                canvas.drawRect(0, 0, translationX, getHeight(), scrimPaint);

                final float alpha = Math.max(0, Math.min((width - translationX) / (float) AndroidUtilities.dp(20), 1.0f));
                layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), child.getTop(), translationX, child.getBottom());
                layerShadowDrawable.setAlpha((int) (0xff * alpha));
                layerShadowDrawable.draw(canvas);
            }

            return result;
        }

        @Keep
        public float getInnerTranslationX() {
            return innerTranslationX;
        }

        private void prepareForMoving(MotionEvent ev) {
            maybeStartTracking = false;
            startedTracking = true;
            startedTrackingX = (int) ev.getX();
            if (pagesStack.size() > 1) {
                movingPage = true;
                startMovingHeaderHeight = currentHeaderHeight;
                listView[1].setVisibility(VISIBLE);
                listView[1].setAlpha(1.0f);
                listView[1].setTranslationX(0.0f);
                listView[0].setBackgroundColor(backgroundPaint.getColor());
                updateInterfaceForCurrentPage(pagesStack.get(pagesStack.size() - 2), true, -1);
            } else {
                movingPage = false;
            }
            cancelCheckLongPress();
        }

        public boolean handleTouchEvent(MotionEvent event) {
            if (pageSwitchAnimation == null && !closeAnimationInProgress && fullscreenVideoContainer.getVisibility() != VISIBLE && !textSelectionHelper.isSelectionMode()) {
                if (event != null && event.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                    startedTrackingPointerId = event.getPointerId(0);
                    maybeStartTracking = true;
                    startedTrackingX = (int) event.getX();
                    startedTrackingY = (int) event.getY();
                    if (tracker != null) {
                        tracker.clear();
                    }
                } else if (event != null && event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerId(0) == startedTrackingPointerId) {
                    if (tracker == null) {
                        tracker = VelocityTracker.obtain();
                    }
                    int dx = Math.max(0, (int) (event.getX() - startedTrackingX));
                    int dy = Math.abs((int) event.getY() - startedTrackingY);
                    tracker.addMovement(event);
                    if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                        prepareForMoving(event);
                    } else if (startedTracking) {
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                        if (movingPage) {
                            listView[0].setTranslationX(dx);
                        } else {
                            containerView.setTranslationX(dx);
                            setInnerTranslationX(dx);
                        }
                    }
                } else if (event != null && event.getPointerId(0) == startedTrackingPointerId && (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (tracker == null) {
                        tracker = VelocityTracker.obtain();
                    }
                    tracker.computeCurrentVelocity(1000);
                    float velX = tracker.getXVelocity();
                    float velY = tracker.getYVelocity();
                    if (!startedTracking && velX >= 3500 && velX > Math.abs(velY)) {
                        prepareForMoving(event);
                    }
                    if (startedTracking) {
                        View movingView = movingPage ? listView[0] : containerView;
                        float x = movingView.getX();
                        final boolean backAnimation = x < movingView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                        float distToMove;
                        AnimatorSet animatorSet = new AnimatorSet();
                        if (!backAnimation) {
                            distToMove = movingView.getMeasuredWidth() - x;
                            if (movingPage) {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(listView[0], View.TRANSLATION_X, movingView.getMeasuredWidth())
                                );
                            } else {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, movingView.getMeasuredWidth()),
                                        ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, (float) movingView.getMeasuredWidth())
                                );
                            }
                        } else {
                            distToMove = x;
                            if (movingPage) {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(listView[0], View.TRANSLATION_X, 0)
                                );
                            } else {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0),
                                        ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, 0.0f)
                                );
                            }
                        }

                        animatorSet.setDuration(Math.max((int) (200.0f / movingView.getMeasuredWidth() * distToMove), 50));
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                if (movingPage) {
                                    listView[0].setBackgroundDrawable(null);
                                    if (!backAnimation) {
                                        WebpageAdapter adapterToUpdate = adapter[1];
                                        adapter[1] = adapter[0];
                                        adapter[0] = adapterToUpdate;

                                        RecyclerListView listToUpdate = listView[1];
                                        listView[1] = listView[0];
                                        listView[0] = listToUpdate;

                                        LinearLayoutManager layoutManagerToUpdate = layoutManager[1];
                                        layoutManager[1] = layoutManager[0];
                                        layoutManager[0] = layoutManagerToUpdate;

                                        pagesStack.remove(pagesStack.size() - 1);

                                        textSelectionHelper.setParentView(listView[0]);
                                        textSelectionHelper.layoutManager = layoutManager[0];
                                        titleTextView.setText(adapter[0].currentPage.site_name == null ? "" : adapter[0].currentPage.site_name);
                                        textSelectionHelper.clear(true);
                                        headerView.invalidate();
                                    }
                                    listView[1].setVisibility(GONE);
                                    headerView.invalidate();
                                } else {
                                    if (!backAnimation) {
                                        saveCurrentPagePosition();
                                        onClosed();
                                    }
                                }
                                movingPage = false;
                                startedTracking = false;
                                closeAnimationInProgress = false;
                            }
                        });
                        animatorSet.start();
                        closeAnimationInProgress = true;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                        movingPage = false;
                    }
                    if (tracker != null) {
                        tracker.recycle();
                        tracker = null;
                    }
                } else if (event == null) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    movingPage = false;
                    if (tracker != null) {
                        tracker.recycle();
                        tracker = null;
                    }
                    if (textSelectionHelper != null && !textSelectionHelper.isSelectionMode()) {
                        textSelectionHelper.clear();
                    }
                }
                return startedTracking;
            }
            return false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (bWidth != 0 && bHeight != 0) {
                blackPaint.setAlpha((int) (255 * windowView.getAlpha()));
                if (bX == 0 && bY == 0) {
                    canvas.drawRect(bX, bY, bX + bWidth, bY + bHeight, blackPaint);
                } else {
                    canvas.drawRect(bX - getTranslationX(), bY, bX + bWidth - getTranslationX(), bY + bHeight, blackPaint);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getMeasuredWidth();
            int h = getMeasuredHeight();
            canvas.drawRect(innerTranslationX, 0, w, h, backgroundPaint);
            if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                WindowInsets insets = (WindowInsets) lastInsets;
                canvas.drawRect(innerTranslationX, 0, w, insets.getSystemWindowInsetTop(), statusBarPaint);
                if (hasCutout) {
                    int left = insets.getSystemWindowInsetLeft();
                    if (left != 0) {
                        canvas.drawRect(0, 0, left, h, statusBarPaint);
                    }
                    int right = insets.getSystemWindowInsetRight();
                    if (right != 0) {
                        canvas.drawRect(getMeasuredWidth() - right, 0, getMeasuredWidth(), h, statusBarPaint);
                    }
                }
            }
        }

        @Keep
        @Override
        public void setAlpha(float value) {
            backgroundPaint.setAlpha((int) (255 * value));
            statusBarPaint.setAlpha((int) (255 * value));
            alpha = value;
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isVisible || alpha != 1.0f || innerTranslationX != 0);
            }
            invalidate();
        }

        @Keep
        @Override
        public float getAlpha() {
            return alpha;
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (searchField.isFocused()) {
                    searchField.clearFocus();
                    AndroidUtilities.hideKeyboard(searchField);
                } else {
                    close(true, false);
                }
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
        }
    }

    class CheckForLongPress implements Runnable {
        public int currentPressCount;

        public void run() {
            if (checkingForLongPress && windowView != null) {
                checkingForLongPress = false;
                if (pressedLink != null) {
                    windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    showCopyPopup(pressedLink.getUrl());
                    pressedLink = null;
                    pressedLinkOwnerLayout = null;
                    if (pressedLinkOwnerView != null) {
                        pressedLinkOwnerView.invalidate();
                    }
                } else if (pressedLinkOwnerView != null && textSelectionHelper.isSelectable(pressedLinkOwnerView)) {
                    if (pressedLinkOwnerView.getTag() != null && pressedLinkOwnerView.getTag() == BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
                        textSelectionHelperBottomSheet.trySelect(pressedLinkOwnerView);
                    } else {
                        textSelectionHelper.trySelect(pressedLinkOwnerView);
                    }
                    if (textSelectionHelper.isSelectionMode()) {
                        windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                } else if (pressedLinkOwnerLayout != null && pressedLinkOwnerView != null) {
                    windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                    int[] location = new int[2];
                    pressedLinkOwnerView.getLocationInWindow(location);
                    int y = location[1] + pressedLayoutY - AndroidUtilities.dp(54);
                    if (y < 0) {
                        y = 0;
                    }
                    pressedLinkOwnerView.invalidate();
                    drawBlockSelection = true;
                    showPopup(pressedLinkOwnerView, Gravity.TOP, 0, y);
                    listView[0].setLayoutFrozen(true);
                    listView[0].setLayoutFrozen(false);
                }
            }
        }
    }

    private void createPaint(boolean update) {
        if (quoteLinePaint == null) {
            quoteLinePaint = new Paint();

            preformattedBackgroundPaint = new Paint();

            tableLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tableLinePaint.setStyle(Paint.Style.STROKE);
            tableLinePaint.setStrokeWidth(AndroidUtilities.dp(1));

            tableHalfLinePaint = new Paint();
            tableHalfLinePaint.setStyle(Paint.Style.STROKE);
            tableHalfLinePaint.setStrokeWidth(AndroidUtilities.dp(1) / 2.0f);

            tableHeaderPaint = new Paint();
            tableStripPaint = new Paint();

            urlPaint = new Paint();
            webpageUrlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            webpageSearchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            photoBackgroundPaint = new Paint();
            dividerPaint = new Paint();
            webpageMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        } else if (!update) {
            return;
        }

        int color2 = Theme.getColor(Theme.key_windowBackgroundWhite);
        float lightness = (0.2126f * Color.red(color2) + 0.7152f * Color.green(color2) + 0.0722f * Color.blue(color2)) / 255.0f;
        webpageSearchPaint.setColor(lightness <= 0.705f ? 0xffd1982e : 0xffffe669);
        webpageUrlPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
        urlPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
        tableHalfLinePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        tableLinePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));

        photoBackgroundPaint.setColor(0x0f000000);
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        webpageMarkPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);

        int color = Theme.getColor(Theme.key_switchTrack);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        tableStripPaint.setColor(Color.argb(20, r, g, b));
        tableHeaderPaint.setColor(Color.argb(34, r, g, b));

        color = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection);
        r = Color.red(color);
        g = Color.green(color);
        b = Color.blue(color);
        preformattedBackgroundPaint.setColor(Color.argb(20, r, g, b));

        quoteLinePaint.setColor(Theme.getColor(Theme.key_chat_inReplyLine));
    }

    private void showCopyPopup(String urlFinal) {
        if (parentActivity == null) {
            return;
        }
        if (linkSheet != null) {
            linkSheet.dismiss();
            linkSheet = null;
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
        builder.setTitle(urlFinal);
        builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
            if (parentActivity == null) {
                return;
            }
            if (which == 0) {
                int index;
                if ((index = urlFinal.lastIndexOf('#')) != -1) {
                    String webPageUrl;
                    if (!TextUtils.isEmpty(adapter[0].currentPage.cached_page.url)) {
                        webPageUrl = adapter[0].currentPage.cached_page.url.toLowerCase();
                    } else {
                        webPageUrl = adapter[0].currentPage.url.toLowerCase();
                    }
                    String anchor;
                    try {
                        anchor = URLDecoder.decode(urlFinal.substring(index + 1), "UTF-8");
                    } catch (Exception ignore) {
                        anchor = "";
                    }
                    if (urlFinal.toLowerCase().contains(webPageUrl)) {
                        if (TextUtils.isEmpty(anchor)) {
                            layoutManager[0].scrollToPositionWithOffset(0, 0);
                            checkScrollAnimated();
                        } else {
                            scrollToAnchor(anchor);
                        }
                        return;
                    }
                }
                Browser.openUrl(parentActivity, urlFinal);
            } else if (which == 1) {
                String url = urlFinal;
                if (url.startsWith("mailto:")) {
                    url = url.substring(7);
                } else if (url.startsWith("tel:")) {
                    url = url.substring(4);
                }
                AndroidUtilities.addToClipboard(url);
            }
        });
        BottomSheet sheet = builder.create();
        showDialog(sheet);
    }

    private void showPopup(View parent, int gravity, int x, int y) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }

        if (popupLayout == null) {
            popupRect = new android.graphics.Rect();
            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity);
            popupLayout.setPadding(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
            popupLayout.setBackgroundDrawable(parentActivity.getResources().getDrawable(R.drawable.menu_copy));
            popupLayout.setAnimationEnabled(false);
            popupLayout.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (popupWindow != null && popupWindow.isShowing()) {
                        v.getHitRect(popupRect);
                        if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                            popupWindow.dismiss();
                        }
                    }
                }
                return false;
            });
            popupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss();
                }
            });
            popupLayout.setShownFromBotton(false);

            deleteView = new TextView(parentActivity);
            deleteView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            deleteView.setGravity(Gravity.CENTER_VERTICAL);
            deleteView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
            deleteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            deleteView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            deleteView.setText(LocaleController.getString("Copy", R.string.Copy).toUpperCase());
            deleteView.setOnClickListener(v -> {
                if (pressedLinkOwnerLayout != null) {
                    AndroidUtilities.addToClipboard(pressedLinkOwnerLayout.getText());
                    Toast.makeText(parentActivity, LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                }
                if (popupWindow != null && popupWindow.isShowing()) {
                    popupWindow.dismiss(true);
                }
            });
            popupLayout.addView(deleteView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48));

            popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            popupWindow.setAnimationEnabled(false);
            popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupWindow.getContentView().setFocusableInTouchMode(true);
            popupWindow.setOnDismissListener(() -> {
                if (pressedLinkOwnerView != null) {
                    pressedLinkOwnerLayout = null;
                    pressedLinkOwnerView.invalidate();
                    pressedLinkOwnerView = null;
                }
            });
        }

        deleteView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        if (popupLayout != null) {
            popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        }

        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setFocusable(true);
        popupWindow.showAtLocation(parent, gravity, x, y);
        popupWindow.startAnimation();
    }

    private TLRPC.RichText getBlockCaption(TLRPC.PageBlock block, int type) {
        if (type == 2) {
            TLRPC.RichText text1 = getBlockCaption(block, 0);
            if (text1 instanceof TLRPC.TL_textEmpty) {
                text1 = null;
            }
            TLRPC.RichText text2 = getBlockCaption(block, 1);
            if (text2 instanceof TLRPC.TL_textEmpty) {
                text2 = null;
            }
            if (text1 != null && text2 == null) {
                return text1;
            } else if (text1 == null && text2 != null) {
                return text2;
            } else if (text1 != null && text2 != null) {
                TLRPC.TL_textPlain text3 = new TLRPC.TL_textPlain();
                text3.text = " ";

                TLRPC.TL_textConcat textConcat = new TLRPC.TL_textConcat();
                textConcat.texts.add(text1);
                textConcat.texts.add(text3);
                textConcat.texts.add(text2);
                return textConcat;
            } else {
                return null;
            }
        }
        if (block instanceof TLRPC.TL_pageBlockEmbedPost) {
            TLRPC.TL_pageBlockEmbedPost blockEmbedPost = (TLRPC.TL_pageBlockEmbedPost) block;
            if (type == 0) {
                return blockEmbedPost.caption.text;
            } else if (type == 1) {
                return blockEmbedPost.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
            TLRPC.TL_pageBlockSlideshow pageBlockSlideshow = (TLRPC.TL_pageBlockSlideshow) block;
            if (type == 0) {
                return pageBlockSlideshow.caption.text;
            } else if (type == 1) {
                return pageBlockSlideshow.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockPhoto) {
            TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) block;
            if (type == 0) {
                return pageBlockPhoto.caption.text;
            } else if (type == 1) {
                return pageBlockPhoto.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockCollage) {
            TLRPC.TL_pageBlockCollage pageBlockCollage = (TLRPC.TL_pageBlockCollage) block;
            if (type == 0) {
                return pageBlockCollage.caption.text;
            } else if (type == 1) {
                return pageBlockCollage.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockEmbed) {
            TLRPC.TL_pageBlockEmbed pageBlockEmbed = (TLRPC.TL_pageBlockEmbed) block;
            if (type == 0) {
                return pageBlockEmbed.caption.text;
            } else if (type == 1) {
                return pageBlockEmbed.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockBlockquote) {
            TLRPC.TL_pageBlockBlockquote pageBlockBlockquote = (TLRPC.TL_pageBlockBlockquote) block;
            return pageBlockBlockquote.caption;
        } else if (block instanceof TLRPC.TL_pageBlockVideo) {
            TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) block;
            if (type == 0) {
                return pageBlockVideo.caption.text;
            } else if (type == 1) {
                return pageBlockVideo.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockPullquote) {
            TLRPC.TL_pageBlockPullquote pageBlockPullquote = (TLRPC.TL_pageBlockPullquote) block;
            return pageBlockPullquote.caption;
        } else if (block instanceof TLRPC.TL_pageBlockAudio) {
            TLRPC.TL_pageBlockAudio pageBlockAudio = (TLRPC.TL_pageBlockAudio) block;
            if (type == 0) {
                return pageBlockAudio.caption.text;
            } else if (type == 1) {
                return pageBlockAudio.caption.credit;
            }
        } else if (block instanceof TLRPC.TL_pageBlockCover) {
            TLRPC.TL_pageBlockCover pageBlockCover = (TLRPC.TL_pageBlockCover) block;
            return getBlockCaption(pageBlockCover.cover, type);
        } else if (block instanceof TLRPC.TL_pageBlockMap) {
            TLRPC.TL_pageBlockMap pageBlockMap = (TLRPC.TL_pageBlockMap) block;
            if (type == 0) {
                return pageBlockMap.caption.text;
            } else if (type == 1) {
                return pageBlockMap.caption.credit;
            }
        }
        return null;
    }

    private View getLastNonListCell(View view) {
        if (view instanceof BlockListItemCell) {
            BlockListItemCell cell = (BlockListItemCell) view;
            if (cell.blockLayout != null) {
                return getLastNonListCell(cell.blockLayout.itemView);
            }
        } else if (view instanceof BlockOrderedListItemCell) {
            BlockOrderedListItemCell cell = (BlockOrderedListItemCell) view;
            if (cell.blockLayout != null) {
                return getLastNonListCell(cell.blockLayout.itemView);
            }
        }
        return view;
    }

    private boolean isListItemBlock(TLRPC.PageBlock block) {
        return block instanceof TL_pageBlockListItem || block instanceof TL_pageBlockOrderedListItem;
    }

    private TLRPC.PageBlock getLastNonListPageBlock(TLRPC.PageBlock block) {
        if (block instanceof TL_pageBlockListItem) {
            TL_pageBlockListItem blockListItem = (TL_pageBlockListItem) block;
            if (blockListItem.blockItem != null) {
                return getLastNonListPageBlock(blockListItem.blockItem);
            } else {
                return blockListItem.blockItem;
            }
        } else if (block instanceof TL_pageBlockOrderedListItem) {
            TL_pageBlockOrderedListItem blockListItem = (TL_pageBlockOrderedListItem) block;
            if (blockListItem.blockItem != null) {
                return getLastNonListPageBlock(blockListItem.blockItem);
            } else {
                return blockListItem.blockItem;
            }
        }
        return block;
    }

    private boolean openAllParentBlocks(TL_pageBlockDetailsChild child) {
        TLRPC.PageBlock parentBlock = getLastNonListPageBlock(child.parent);
        if (parentBlock instanceof TLRPC.TL_pageBlockDetails) {
            TLRPC.TL_pageBlockDetails blockDetails = (TLRPC.TL_pageBlockDetails) parentBlock;
            if (!blockDetails.open) {
                blockDetails.open = true;
                return true;
            }
            return false;
        } else if (parentBlock instanceof TL_pageBlockDetailsChild) {
            TL_pageBlockDetailsChild parent = (TL_pageBlockDetailsChild) parentBlock;
            parentBlock = getLastNonListPageBlock(parent.block);
            boolean opened = false;
            if (parentBlock instanceof TLRPC.TL_pageBlockDetails) {
                TLRPC.TL_pageBlockDetails blockDetails = (TLRPC.TL_pageBlockDetails) parentBlock;
                if (!blockDetails.open) {
                    blockDetails.open = true;
                    opened = true;
                }
            }
            return openAllParentBlocks(parent) || opened;
        }
        return false;
    }

    private TLRPC.PageBlock fixListBlock(TLRPC.PageBlock parentBlock, TLRPC.PageBlock childBlock) {
        if (parentBlock instanceof TL_pageBlockListItem) {
            TL_pageBlockListItem blockListItem = (TL_pageBlockListItem) parentBlock;
            blockListItem.blockItem = childBlock;
            return parentBlock;
        } else if (parentBlock instanceof TL_pageBlockOrderedListItem) {
            TL_pageBlockOrderedListItem blockListItem = (TL_pageBlockOrderedListItem) parentBlock;
            blockListItem.blockItem = childBlock;
            return parentBlock;
        }
        return childBlock;
    }

    private TLRPC.PageBlock wrapInTableBlock(TLRPC.PageBlock parentBlock, TLRPC.PageBlock childBlock) {
        if (parentBlock instanceof TL_pageBlockListItem) {
            TL_pageBlockListItem parent = (TL_pageBlockListItem) parentBlock;

            TL_pageBlockListItem item = new TL_pageBlockListItem();
            item.parent = parent.parent;
            item.blockItem = wrapInTableBlock(parent.blockItem, childBlock);
            return item;
        } else if (parentBlock instanceof TL_pageBlockOrderedListItem) {
            TL_pageBlockOrderedListItem parent = (TL_pageBlockOrderedListItem) parentBlock;

            TL_pageBlockOrderedListItem item = new TL_pageBlockOrderedListItem();
            item.parent = parent.parent;
            item.blockItem = wrapInTableBlock(parent.blockItem, childBlock);
            return item;
        }
        return childBlock;
    }

    private void updateInterfaceForCurrentPage(TLRPC.WebPage webPage, boolean previous, int order) {
        if (webPage == null || webPage.cached_page == null) {
            return;
        }
        if (!previous && order != 0) {
            WebpageAdapter adapterToUpdate = adapter[1];
            adapter[1] = adapter[0];
            adapter[0] = adapterToUpdate;

            RecyclerListView listToUpdate = listView[1];
            listView[1] = listView[0];
            listView[0] = listToUpdate;

            LinearLayoutManager layoutManagerToUpdate = layoutManager[1];
            layoutManager[1] = layoutManager[0];
            layoutManager[0] = layoutManagerToUpdate;

            int index1 = containerView.indexOfChild(listView[0]);
            int index2 = containerView.indexOfChild(listView[1]);
            if (order == 1) {
                if (index1 < index2) {
                    containerView.removeView(listView[0]);
                    containerView.addView(listView[0], index2);
                }
            } else {
                if (index2 < index1) {
                    containerView.removeView(listView[0]);
                    containerView.addView(listView[0], index1);
                }
            }

            pageSwitchAnimation = new AnimatorSet();
            listView[0].setVisibility(View.VISIBLE);
            int index = order == 1 ? 0 : 1;
            listView[index].setBackgroundColor(backgroundPaint.getColor());
            if (Build.VERSION.SDK_INT >= 18) {
                listView[index].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            if (order == 1) {
                pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(listView[0], View.TRANSLATION_X, AndroidUtilities.dp(56), 0),
                        ObjectAnimator.ofFloat(listView[0], View.ALPHA, 0.0f, 1.0f));
            } else if (order == -1) {
                listView[0].setAlpha(1.0f);
                listView[0].setTranslationX(0.0f);
                pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(listView[1], View.TRANSLATION_X, 0, AndroidUtilities.dp(56)),
                        ObjectAnimator.ofFloat(listView[1], View.ALPHA, 1.0f, 0.0f));
            }
            pageSwitchAnimation.setDuration(150);
            pageSwitchAnimation.setInterpolator(interpolator);
            pageSwitchAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    listView[1].setVisibility(View.GONE);
                    textSelectionHelper.setParentView(listView[0]);
                    textSelectionHelper.layoutManager = layoutManager[0];
                    listView[index].setBackgroundDrawable(null);
                    if (Build.VERSION.SDK_INT >= 18) {
                        listView[index].setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    pageSwitchAnimation = null;
                }
            });
            pageSwitchAnimation.start();
        }
        if (!previous) {
            titleTextView.setText(webPage.site_name == null ? "" : webPage.site_name);
            textSelectionHelper.clear(true);
            headerView.invalidate();
        }
        int index = previous ? 1 : 0;
        TLRPC.WebPage page = previous ? pagesStack.get(pagesStack.size() - 2) : webPage;
        adapter[index].isRtl = webPage.cached_page.rtl;
        adapter[index].cleanup();
        adapter[index].currentPage = page;

        int numBlocks = 0;
        int count = page.cached_page.blocks.size();
        for (int a = 0; a < count; a++) {
            TLRPC.PageBlock block = page.cached_page.blocks.get(a);
            if (a == 0) {
                block.first = true;
                if (block instanceof TLRPC.TL_pageBlockCover) {
                    TLRPC.TL_pageBlockCover pageBlockCover = (TLRPC.TL_pageBlockCover) block;
                    TLRPC.RichText caption = getBlockCaption(pageBlockCover, 0);
                    TLRPC.RichText credit = getBlockCaption(pageBlockCover, 1);
                    if ((caption != null && !(caption instanceof TLRPC.TL_textEmpty) || credit != null && !(credit instanceof TLRPC.TL_textEmpty)) && count > 1) {
                        TLRPC.PageBlock next = page.cached_page.blocks.get(1);
                        if (next instanceof TLRPC.TL_pageBlockChannel) {
                            adapter[index].channelBlock = (TLRPC.TL_pageBlockChannel) next;
                        }
                    }
                }
            } else if (a == 1 && adapter[index].channelBlock != null) {
                continue;
            }
            adapter[index].addBlock(adapter[index], block, 0, 0, a == count - 1 ? a : 0);
        }

        adapter[index].notifyDataSetChanged();

        if (pagesStack.size() == 1 || order == -1) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
            String key = "article" + page.id;
            int position = preferences.getInt(key, -1);
            int offset;
            if (preferences.getBoolean(key + "r", true) == AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                offset = preferences.getInt(key + "o", 0) - listView[index].getPaddingTop();
            } else {
                offset = AndroidUtilities.dp(10);
            }
            if (position != -1) {
                layoutManager[index].scrollToPositionWithOffset(position, offset);
            }
        } else {
            layoutManager[index].scrollToPositionWithOffset(0, 0);
        }
        if (!previous) {
            checkScrollAnimated();
        }
    }

    private boolean addPageToStack(TLRPC.WebPage webPage, String anchor, int order) {
        saveCurrentPagePosition();
        pagesStack.add(webPage);
        showSearch(false);
        updateInterfaceForCurrentPage(webPage, false, order);
        return scrollToAnchor(anchor);
    }

    private boolean scrollToAnchor(String anchor) {
        if (TextUtils.isEmpty(anchor)) {
            return false;
        }
        anchor = anchor.toLowerCase();
        Integer row = adapter[0].anchors.get(anchor);
        if (row != null) {
            TLRPC.TL_textAnchor textAnchor = adapter[0].anchorsParent.get(anchor);
            if (textAnchor != null) {
                TLRPC.TL_pageBlockParagraph paragraph = new TLRPC.TL_pageBlockParagraph();
                paragraph.text = textAnchor.text;

                int type = adapter[0].getTypeForBlock(paragraph);
                RecyclerView.ViewHolder holder = adapter[0].onCreateViewHolder(null, type);
                adapter[0].bindBlockToHolder(type, holder, paragraph, 0, 0);

                BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                builder.setApplyTopPadding(false);
                builder.setApplyBottomPadding(false);
                LinearLayout linearLayout = new LinearLayout(parentActivity);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                textSelectionHelperBottomSheet = new TextSelectionHelper.ArticleTextSelectionHelper();
                textSelectionHelperBottomSheet.setParentView(linearLayout);
                textSelectionHelperBottomSheet.setCallback(new TextSelectionHelper.Callback() {
                    @Override
                    public void onStateChanged(boolean isSelected) {
                        if (linkSheet != null) {
                            linkSheet.setDisableScroll(isSelected);
                        }
                    }
                });
                TextView textView = new TextView(parentActivity) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
                        super.onDraw(canvas);
                    }
                };
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setText(LocaleController.getString("InstantViewReference", R.string.InstantViewReference));
                textView.setGravity((adapter[0].isRtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                textView.setTextColor(getTextColor());
                textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
                linearLayout.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48) + 1));

                holder.itemView.setTag(BOTTOM_SHEET_VIEW_TAG);
                linearLayout.addView(holder.itemView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 7, 0, 0));

                View overlayView = textSelectionHelperBottomSheet.getOverlayView(parentActivity);
                FrameLayout frameLayout = new FrameLayout(parentActivity) {

                    @Override
                    public boolean dispatchTouchEvent(MotionEvent ev) {
                        TextSelectionHelper.TextSelectionOverlay selectionOverlay = textSelectionHelperBottomSheet.getOverlayView(getContext());
                        MotionEvent textSelectionEv = MotionEvent.obtain(ev);
                        textSelectionEv.offsetLocation(-linearLayout.getX(), -linearLayout.getY());
                        if (textSelectionHelperBottomSheet.isSelectionMode() && textSelectionHelperBottomSheet.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                            return true;
                        }

                        if (selectionOverlay.checkOnTap(ev)) {
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }

                        if (ev.getAction() == MotionEvent.ACTION_DOWN && textSelectionHelperBottomSheet.isSelectionMode() && (ev.getY() < linearLayout.getTop() || ev.getY() > linearLayout.getBottom())) {
                            if (textSelectionHelperBottomSheet.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                                return super.dispatchTouchEvent(ev);
                            } else {
                                return true;
                            }
                        }
                        return super.dispatchTouchEvent(ev);
                    }

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(linearLayout.getMeasuredHeight() + AndroidUtilities.dp(8), MeasureSpec.EXACTLY);
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                };

                builder.setDelegate(new BottomSheet.BottomSheetDelegate() {
                    @Override
                    public boolean canDismiss() {
                        if (textSelectionHelperBottomSheet != null && textSelectionHelperBottomSheet.isSelectionMode()){
                            textSelectionHelperBottomSheet.clear();
                            return false;
                        }
                        return true;
                    }
                });

                frameLayout.addView(linearLayout, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                frameLayout.addView(overlayView, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                builder.setCustomView(frameLayout);
                if (textSelectionHelper.isSelectionMode()) {
                    textSelectionHelper.clear();
                }
                showDialog(linkSheet = builder.create());
            } else {
                if (row < 0 || row >= adapter[0].blocks.size()) {
                    return false;
                }
                TLRPC.PageBlock originalBlock = adapter[0].blocks.get(row);
                TLRPC.PageBlock block = getLastNonListPageBlock(originalBlock);

                if (block instanceof TL_pageBlockDetailsChild) {
                    if (openAllParentBlocks((TL_pageBlockDetailsChild) block)) {
                        adapter[0].updateRows();
                        adapter[0].notifyDataSetChanged();
                    }
                }
                int position = adapter[0].localBlocks.indexOf(originalBlock);
                if (position != -1) {
                    row = position;
                }

                Integer offset = adapter[0].anchorsOffset.get(anchor);
                if (offset != null) {
                    if (offset == -1) {
                        int type = adapter[0].getTypeForBlock(originalBlock);
                        RecyclerView.ViewHolder holder = adapter[0].onCreateViewHolder(null, type);
                        adapter[0].bindBlockToHolder(type, holder, originalBlock, 0, 0);
                        holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(listView[0].getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        offset = adapter[0].anchorsOffset.get(anchor);
                        if (offset == -1) {
                            offset = 0;
                        }
                    }
                } else {
                    offset = 0;
                }
                layoutManager[0].scrollToPositionWithOffset(row, currentHeaderHeight - AndroidUtilities.dp(56) - offset);
            }
            return true;
        }
        return false;
    }

    private boolean removeLastPageFromStack() {
        if (pagesStack.size() < 2) {
            return false;
        }
        pagesStack.remove(pagesStack.size() - 1);
        updateInterfaceForCurrentPage(pagesStack.get(pagesStack.size() - 1), false, -1);
        return true;
    }

    protected void startCheckLongPress(float x, float y, View parentView) {
        if (checkingForLongPress) {
            return;
        }
        checkingForLongPress = true;
        if (pendingCheckForTap == null) {
            pendingCheckForTap = new CheckForTap();
        }
        if (parentView.getTag() != null && parentView.getTag() == BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
            textSelectionHelperBottomSheet.setMaybeView((int) x, (int) y, parentView);
        } else {
            textSelectionHelper.setMaybeView((int) x, (int) y, parentView);
        }
        windowView.postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    protected void cancelCheckLongPress() {
        checkingForLongPress = false;
        if (pendingCheckForLongPress != null) {
            windowView.removeCallbacks(pendingCheckForLongPress);
            pendingCheckForLongPress = null;
        }
        if (pendingCheckForTap != null) {
            windowView.removeCallbacks(pendingCheckForTap);
            pendingCheckForTap = null;
        }
    }

    private static final int TEXT_FLAG_REGULAR = 0;
    private static final int TEXT_FLAG_MEDIUM = 1;
    private static final int TEXT_FLAG_ITALIC = 2;
    private static final int TEXT_FLAG_MONO = 4;
    private static final int TEXT_FLAG_URL = 8;
    private static final int TEXT_FLAG_UNDERLINE = 16;
    private static final int TEXT_FLAG_STRIKE = 32;
    private static final int TEXT_FLAG_MARKED = 64;
    private static final int TEXT_FLAG_SUB = 128;
    private static final int TEXT_FLAG_SUP = 256;
    private static final int TEXT_FLAG_WEBPAGE_URL = 512;

    private static TextPaint audioTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static TextPaint errorTextPaint;
    private static SparseArray<TextPaint> photoCaptionTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> photoCreditTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> titleTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> kickerTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> headerTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> subtitleTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> subheaderTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> authorTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> footerTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> paragraphTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> listTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> preformattedTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> quoteTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> embedPostTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> embedPostCaptionTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> mediaCaptionTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> mediaCreditTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> relatedArticleTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> detailsTextPaints = new SparseArray<>();
    private static SparseArray<TextPaint> tableTextPaints = new SparseArray<>();

    private static TextPaint embedPostAuthorPaint;
    private static TextPaint embedPostDatePaint;
    private static TextPaint channelNamePaint;
    private static TextPaint channelNamePhotoPaint;
    private static TextPaint relatedArticleHeaderPaint;
    private static TextPaint relatedArticleTextPaint;

    private static TextPaint listTextPointerPaint;
    private static TextPaint listTextNumPaint;

    private static Paint photoBackgroundPaint;
    private static Paint preformattedBackgroundPaint;
    private static Paint quoteLinePaint;
    private static Paint dividerPaint;
    private static Paint tableLinePaint;
    private static Paint tableHalfLinePaint;
    private static Paint tableHeaderPaint;
    private static Paint tableStripPaint;
    private static Paint urlPaint;
    private static Paint webpageUrlPaint;
    private static Paint webpageSearchPaint;
    private static Paint webpageMarkPaint;

    private int getTextFlags(TLRPC.RichText richText) {
        if (richText instanceof TLRPC.TL_textFixed) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MONO;
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_ITALIC;
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MEDIUM;
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_UNDERLINE;
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_STRIKE;
        } else if (richText instanceof TLRPC.TL_textEmail) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
        } else if (richText instanceof TLRPC.TL_textPhone) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
        } else if (richText instanceof TLRPC.TL_textUrl) {
            TLRPC.TL_textUrl textUrl = (TLRPC.TL_textUrl) richText;
            if (textUrl.webpage_id != 0) {
                return getTextFlags(richText.parentRichText) | TEXT_FLAG_WEBPAGE_URL;
            } else {
                return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
            }
        } else if (richText instanceof TLRPC.TL_textSubscript) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_SUB;
        } else if (richText instanceof TLRPC.TL_textSuperscript) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_SUP;
        } else if (richText instanceof TLRPC.TL_textMarked) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MARKED;
        } else if (richText != null) {
            return getTextFlags(richText.parentRichText);
        }
        return TEXT_FLAG_REGULAR;
    }

    private TLRPC.RichText getLastRichText(TLRPC.RichText richText) {
        if (richText == null) {
            return null;
        }
        if (richText instanceof TLRPC.TL_textFixed) {
            return getLastRichText(((TLRPC.TL_textFixed) richText).text);
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getLastRichText(((TLRPC.TL_textItalic) richText).text);
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getLastRichText(((TLRPC.TL_textBold) richText).text);
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getLastRichText(((TLRPC.TL_textUnderline) richText).text);
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getLastRichText(((TLRPC.TL_textStrike) richText).text);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            return getLastRichText(((TLRPC.TL_textEmail) richText).text);
        } else if (richText instanceof TLRPC.TL_textUrl) {
            return getLastRichText(((TLRPC.TL_textUrl) richText).text);
        } else if (richText instanceof TLRPC.TL_textAnchor) {
            getLastRichText(((TLRPC.TL_textAnchor) richText).text);
        } else if (richText instanceof TLRPC.TL_textSubscript) {
            return getLastRichText(((TLRPC.TL_textSubscript) richText).text);
        } else if (richText instanceof TLRPC.TL_textSuperscript) {
            return getLastRichText(((TLRPC.TL_textSuperscript) richText).text);
        } else if (richText instanceof TLRPC.TL_textMarked) {
            return getLastRichText(((TLRPC.TL_textMarked) richText).text);
        } else if (richText instanceof TLRPC.TL_textPhone) {
            return getLastRichText(((TLRPC.TL_textPhone) richText).text);
        }
        return richText;
    }

    private CharSequence getText(WebpageAdapter adapter, View parentView, TLRPC.RichText parentRichText, TLRPC.RichText richText, TLRPC.PageBlock parentBlock, int maxWidth) {
        return getText(adapter.currentPage, parentView, parentRichText, richText, parentBlock, maxWidth);
    }

    private CharSequence getText(TLRPC.WebPage page, View parentView, TLRPC.RichText parentRichText, TLRPC.RichText richText, TLRPC.PageBlock parentBlock, int maxWidth) {
        if (richText == null) {
            return null;
        }
        if (richText instanceof TLRPC.TL_textFixed) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textFixed) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textItalic) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textBold) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textUnderline) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textStrike) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(page, parentView, parentRichText, ((TLRPC.TL_textEmail) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null, "mailto:" + getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textUrl) {
            TLRPC.TL_textUrl textUrl = (TLRPC.TL_textUrl) richText;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(page, parentView, parentRichText, ((TLRPC.TL_textUrl) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            TextPaint paint = innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null;
            MetricAffectingSpan span;
            if (textUrl.webpage_id != 0) {
                span = new TextPaintWebpageUrlSpan(paint, getUrl(richText));
            } else {
                span = new TextPaintUrlSpan(paint, getUrl(richText));
            }
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(span, 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textPlain) {
            return ((TLRPC.TL_textPlain) richText).text;
        } else if (richText instanceof TLRPC.TL_textAnchor) {
            TLRPC.TL_textAnchor textAnchor = (TLRPC.TL_textAnchor) richText;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(page, parentView, parentRichText, textAnchor.text, parentBlock, maxWidth));
            spannableStringBuilder.setSpan(new AnchorSpan(textAnchor.name), 0, spannableStringBuilder.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textEmpty) {
            return "";
        } else if (richText instanceof TLRPC.TL_textConcat) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                TLRPC.RichText innerRichText = richText.texts.get(a);
                TLRPC.RichText lastRichText = getLastRichText(innerRichText);
                boolean extraSpace = maxWidth >= 0 && innerRichText instanceof TLRPC.TL_textUrl && ((TLRPC.TL_textUrl) innerRichText).webpage_id != 0;
                if (extraSpace && spannableStringBuilder.length() != 0 && spannableStringBuilder.charAt(spannableStringBuilder.length() - 1) != '\n') {
                    spannableStringBuilder.append(" ");
                    spannableStringBuilder.setSpan(new TextSelectionHelper.IgnoreCopySpannable(), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                CharSequence innerText = getText(page, parentView, parentRichText, innerRichText, parentBlock, maxWidth);
                int flags = getTextFlags(lastRichText);
                int startLength = spannableStringBuilder.length();
                spannableStringBuilder.append(innerText);
                if (flags != 0 && !(innerText instanceof SpannableStringBuilder)) {
                    if ((flags & TEXT_FLAG_URL) != 0 || (flags & TEXT_FLAG_WEBPAGE_URL) != 0) {
                        String url = getUrl(innerRichText);
                        if (url == null) {
                            url = getUrl(parentRichText);
                        }
                        MetricAffectingSpan span;
                        if ((flags & TEXT_FLAG_WEBPAGE_URL) != 0) {
                            span = new TextPaintWebpageUrlSpan(getTextPaint(parentRichText, lastRichText, parentBlock), url);
                        } else {
                            span = new TextPaintUrlSpan(getTextPaint(parentRichText, lastRichText, parentBlock), url);
                        }
                        if (startLength != spannableStringBuilder.length()) {
                            spannableStringBuilder.setSpan(span, startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else {
                        if (startLength != spannableStringBuilder.length()) {
                            spannableStringBuilder.setSpan(new TextPaintSpan(getTextPaint(parentRichText, lastRichText, parentBlock)), startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
                if (extraSpace && a != count - 1) {
                    spannableStringBuilder.append(" ");
                    spannableStringBuilder.setSpan(new TextSelectionHelper.IgnoreCopySpannable(), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textSubscript) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textSubscript) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textSuperscript) {
            return getText(page, parentView, parentRichText, ((TLRPC.TL_textSuperscript) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TLRPC.TL_textMarked) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(page, parentView, parentRichText, ((TLRPC.TL_textMarked) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintMarkSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textPhone) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(page, parentView, parentRichText, ((TLRPC.TL_textPhone) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null, "tel:" + getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textImage) {
            TLRPC.TL_textImage textImage = (TLRPC.TL_textImage) richText;
            TLRPC.Document document = WebPageUtils.getDocumentWithId(page, textImage.document_id);
            if (document != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("*");
                int w = AndroidUtilities.dp(textImage.w);
                int h = AndroidUtilities.dp(textImage.h);
                maxWidth = Math.abs(maxWidth);
                if (w > maxWidth) {
                    float scale = maxWidth / (float) w;
                    w = maxWidth;
                    h *= scale;
                }
                if (parentView != null) {
                    int color = Theme.getColor(Theme.key_windowBackgroundWhite);
                    float lightness = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255.0f;
                    spannableStringBuilder.setSpan(new TextPaintImageReceiverSpan(parentView, document, page, w, h, false, lightness <= 0.705f), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return spannableStringBuilder;
            } else {
                return "";
            }
        }
        return "not supported " + richText;
    }

    public static CharSequence getPlainText(TLRPC.RichText richText) {
        if (richText == null) {
            return "";
        }
        if (richText instanceof TLRPC.TL_textFixed) {
            return getPlainText(((TLRPC.TL_textFixed) richText).text);
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getPlainText(((TLRPC.TL_textItalic) richText).text);
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getPlainText(((TLRPC.TL_textBold) richText).text);
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getPlainText(((TLRPC.TL_textUnderline) richText).text);
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getPlainText(((TLRPC.TL_textStrike) richText).text);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            return getPlainText(((TLRPC.TL_textEmail) richText).text);
        } else if (richText instanceof TLRPC.TL_textUrl) {
            return getPlainText(((TLRPC.TL_textUrl) richText).text);
        } else if (richText instanceof TLRPC.TL_textPlain) {
            return ((TLRPC.TL_textPlain) richText).text;
        } else if (richText instanceof TLRPC.TL_textAnchor) {
            return getPlainText(((TLRPC.TL_textAnchor) richText).text);
        } else if (richText instanceof TLRPC.TL_textEmpty) {
            return "";
        } else if (richText instanceof TLRPC.TL_textConcat) {
            StringBuilder stringBuilder = new StringBuilder();
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                stringBuilder.append(getPlainText(richText.texts.get(a)));
            }
            return stringBuilder;
        } else if (richText instanceof TLRPC.TL_textSubscript) {
            return getPlainText(((TLRPC.TL_textSubscript) richText).text);
        } else if (richText instanceof TLRPC.TL_textSuperscript) {
            return getPlainText(((TLRPC.TL_textSuperscript) richText).text);
        } else if (richText instanceof TLRPC.TL_textMarked) {
            return getPlainText(((TLRPC.TL_textMarked) richText).text);
        } else if (richText instanceof TLRPC.TL_textPhone) {
            return getPlainText(((TLRPC.TL_textPhone) richText).text);
        } else if (richText instanceof TLRPC.TL_textImage) {
            return "";
        }
        return "";
    }

    public static String getUrl(TLRPC.RichText richText) {
        if (richText instanceof TLRPC.TL_textFixed) {
            return getUrl(((TLRPC.TL_textFixed) richText).text);
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getUrl(((TLRPC.TL_textItalic) richText).text);
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getUrl(((TLRPC.TL_textBold) richText).text);
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getUrl(((TLRPC.TL_textUnderline) richText).text);
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getUrl(((TLRPC.TL_textStrike) richText).text);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            return ((TLRPC.TL_textEmail) richText).email;
        } else if (richText instanceof TLRPC.TL_textUrl) {
            return ((TLRPC.TL_textUrl) richText).url;
        }else if (richText instanceof TLRPC.TL_textPhone) {
            return ((TLRPC.TL_textPhone) richText).phone;
        }
        return null;
    }

    private int getTextColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    private int getLinkTextColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteLinkText);
    }

    private static int getGrayTextColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    private TextPaint getTextPaint(TLRPC.RichText parentRichText, TLRPC.RichText richText, TLRPC.PageBlock parentBlock) {
        int flags = getTextFlags(richText);
        SparseArray<TextPaint> currentMap = null;
        int textSize = AndroidUtilities.dp(14);
        int textColor = 0xffff0000;

        int additionalSize = AndroidUtilities.dp(SharedConfig.ivFontSize - 16);

        if (parentBlock instanceof TLRPC.TL_pageBlockPhoto) {
            TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) parentBlock;
            if (pageBlockPhoto.caption.text == richText || pageBlockPhoto.caption.text == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockMap) {
            TLRPC.TL_pageBlockMap pageBlockMap = (TLRPC.TL_pageBlockMap) parentBlock;
            if (pageBlockMap.caption.text == richText || pageBlockMap.caption.text == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockTitle) {
            currentMap = titleTextPaints;
            textSize = AndroidUtilities.dp(23);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockKicker) {
            currentMap = kickerTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockAuthorDate) {
            currentMap = authorTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockFooter) {
            currentMap = footerTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockSubtitle) {
            currentMap = subtitleTextPaints;
            textSize = AndroidUtilities.dp(20);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockHeader) {
            currentMap = headerTextPaints;
            textSize = AndroidUtilities.dp(20);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockSubheader) {
            currentMap = subheaderTextPaints;
            textSize = AndroidUtilities.dp(17);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockBlockquote) {
            TLRPC.TL_pageBlockBlockquote pageBlockBlockquote = (TLRPC.TL_pageBlockBlockquote) parentBlock;
            if (pageBlockBlockquote.text == parentRichText) {
                currentMap = quoteTextPaints;
                textSize = AndroidUtilities.dp(15);
                textColor = getTextColor();
            } else if (pageBlockBlockquote.caption == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getGrayTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockPullquote) {
            TLRPC.TL_pageBlockPullquote pageBlockBlockquote = (TLRPC.TL_pageBlockPullquote) parentBlock;
            if (pageBlockBlockquote.text == parentRichText) {
                currentMap = quoteTextPaints;
                textSize = AndroidUtilities.dp(15);
                textColor = getTextColor();
            } else if (pageBlockBlockquote.caption == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getGrayTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockPreformatted) {
            currentMap = preformattedTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockParagraph) {
            currentMap = paragraphTextPaints;
            textSize = AndroidUtilities.dp(16);
            textColor = getTextColor();
        } else if (isListItemBlock(parentBlock)) {
            currentMap = listTextPaints;
            textSize = AndroidUtilities.dp(16);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockEmbed) {
            TLRPC.TL_pageBlockEmbed pageBlockEmbed = (TLRPC.TL_pageBlockEmbed) parentBlock;
            if (pageBlockEmbed.caption.text == richText || pageBlockEmbed.caption.text == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockSlideshow) {
            TLRPC.TL_pageBlockSlideshow pageBlockSlideshow = (TLRPC.TL_pageBlockSlideshow) parentBlock;
            if (pageBlockSlideshow.caption.text == richText || pageBlockSlideshow.caption.text == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockCollage) {
            TLRPC.TL_pageBlockCollage pageBlockCollage = (TLRPC.TL_pageBlockCollage) parentBlock;
            if (pageBlockCollage.caption.text == richText || pageBlockCollage.caption.text == parentRichText) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockEmbedPost) {
            TLRPC.TL_pageBlockEmbedPost pageBlockEmbedPost = (TLRPC.TL_pageBlockEmbedPost) parentBlock;
            if (richText == pageBlockEmbedPost.caption.text) {
                currentMap = photoCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getGrayTextColor();
            } else if (richText == pageBlockEmbedPost.caption.credit) {
                currentMap = photoCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
                textColor = getGrayTextColor();
            } else if (richText != null) {
                currentMap = embedPostTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockVideo) {
            TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) parentBlock;
            if (richText == pageBlockVideo.caption.text) {
                currentMap = mediaCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = mediaCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockAudio) {
            TLRPC.TL_pageBlockAudio pageBlockAudio = (TLRPC.TL_pageBlockAudio) parentBlock;
            if (richText == pageBlockAudio.caption.text) {
                currentMap = mediaCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
            } else {
                currentMap = mediaCreditTextPaints;
                textSize = AndroidUtilities.dp(12);
            }
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockRelatedArticles) {
            currentMap = relatedArticleTextPaints;
            textSize = AndroidUtilities.dp(15);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockDetails) {
            currentMap = detailsTextPaints;
            textSize = AndroidUtilities.dp(15);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockTable) {
            currentMap = tableTextPaints;
            textSize = AndroidUtilities.dp(15);
            textColor = getTextColor();
        }
        if ((flags & TEXT_FLAG_SUP) != 0 || (flags & TEXT_FLAG_SUB) != 0) {
            textSize -= AndroidUtilities.dp(4);
        }
        if (currentMap == null) {
            if (errorTextPaint == null) {
                errorTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                errorTextPaint.setColor(0xffff0000);
            }
            errorTextPaint.setTextSize(AndroidUtilities.dp(14));
            return errorTextPaint;
        }
        TextPaint paint = currentMap.get(flags);
        if (paint == null) {
            paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            if ((flags & TEXT_FLAG_MONO) != 0) {
                paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            } else {
                if (parentBlock instanceof TLRPC.TL_pageBlockRelatedArticles) {
                    paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                } else if (selectedFont == 1 || parentBlock instanceof TLRPC.TL_pageBlockTitle || parentBlock instanceof TLRPC.TL_pageBlockKicker || parentBlock instanceof TLRPC.TL_pageBlockHeader || parentBlock instanceof TLRPC.TL_pageBlockSubtitle || parentBlock instanceof TLRPC.TL_pageBlockSubheader) {
                    if (parentBlock instanceof TLRPC.TL_pageBlockTitle || parentBlock instanceof TLRPC.TL_pageBlockHeader || parentBlock instanceof TLRPC.TL_pageBlockSubtitle || parentBlock instanceof TLRPC.TL_pageBlockSubheader) {
                        paint.setTypeface(AndroidUtilities.getTypeface("fonts/mw_bold.ttf"));
                    } else {
                        if ((flags & TEXT_FLAG_MEDIUM) != 0 && (flags & TEXT_FLAG_ITALIC) != 0) {
                            paint.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                        } else if ((flags & TEXT_FLAG_MEDIUM) != 0) {
                            paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
                        } else if ((flags & TEXT_FLAG_ITALIC) != 0) {
                            paint.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                        } else {
                            paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                        }
                    }
                } else {
                    if ((flags & TEXT_FLAG_MEDIUM) != 0 && (flags & TEXT_FLAG_ITALIC) != 0) {
                        paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf"));
                    } else if ((flags & TEXT_FLAG_MEDIUM) != 0) {
                        paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    } else if ((flags & TEXT_FLAG_ITALIC) != 0) {
                        paint.setTypeface(AndroidUtilities.getTypeface("fonts/ritalic.ttf"));
                    }
                }
            }
            if ((flags & TEXT_FLAG_STRIKE) != 0) {
                paint.setFlags(paint.getFlags() | TextPaint.STRIKE_THRU_TEXT_FLAG);
            }
            if ((flags & TEXT_FLAG_UNDERLINE) != 0) {
                paint.setFlags(paint.getFlags() | TextPaint.UNDERLINE_TEXT_FLAG);
            }
            if ((flags & TEXT_FLAG_URL) != 0 || (flags & TEXT_FLAG_WEBPAGE_URL) != 0) {
                paint.setFlags(paint.getFlags());
                textColor = getLinkTextColor();
            }
            if ((flags & TEXT_FLAG_SUP) != 0) {
                paint.baselineShift -= AndroidUtilities.dp(6.0f);
            } else if ((flags & TEXT_FLAG_SUB) != 0) {
                paint.baselineShift += AndroidUtilities.dp(2.0f);
            }
            paint.setColor(textColor);
            currentMap.put(flags, paint);
        }
        paint.setTextSize(textSize + additionalSize);
        return paint;
    }

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TLRPC.RichText richText, int width, int textY, TLRPC.PageBlock parentBlock, Layout.Alignment align, WebpageAdapter parentAdapter) {
        return createLayoutForText(parentView, plainText, richText, width, 0, parentBlock, align, 0, parentAdapter);
    }

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TLRPC.RichText richText, int width, int textY, TLRPC.PageBlock parentBlock, WebpageAdapter parentAdapter) {
        return createLayoutForText(parentView, plainText, richText, width, textY, parentBlock, Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter);
    }

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TLRPC.RichText richText, int width, int textY, TLRPC.PageBlock parentBlock, Layout.Alignment align, int maxLines, WebpageAdapter parentAdapter) {
        if (plainText == null && (richText == null || richText instanceof TLRPC.TL_textEmpty)) {
            return null;
        }
        if (width < 0) {
            width = AndroidUtilities.dp(10);
        }

        CharSequence text;
        if (plainText != null) {
            text = plainText;
        } else {
            text = getText(parentAdapter, parentView, richText, richText, parentBlock, width);
        }
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        int additionalSize = AndroidUtilities.dp(SharedConfig.ivFontSize - 16);

        TextPaint paint;
        if (parentBlock instanceof TLRPC.TL_pageBlockEmbedPost && richText == null) {
            TLRPC.TL_pageBlockEmbedPost pageBlockEmbedPost = (TLRPC.TL_pageBlockEmbedPost) parentBlock;
            if (pageBlockEmbedPost.author == plainText) {
                if (embedPostAuthorPaint == null) {
                    embedPostAuthorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    embedPostAuthorPaint.setColor(getTextColor());
                }
                embedPostAuthorPaint.setTextSize(AndroidUtilities.dp(15) + additionalSize);
                paint = embedPostAuthorPaint;
            } else {
                if (embedPostDatePaint == null) {
                    embedPostDatePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    embedPostDatePaint.setColor(getGrayTextColor());
                }
                embedPostDatePaint.setTextSize(AndroidUtilities.dp(14) + additionalSize);
                paint = embedPostDatePaint;
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockChannel) {
            if (channelNamePaint == null) {
                channelNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                channelNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

                channelNamePhotoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                channelNamePhotoPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            }
            channelNamePaint.setColor(getTextColor());
            channelNamePaint.setTextSize(AndroidUtilities.dp(15));

            channelNamePhotoPaint.setColor(0xffffffff);
            channelNamePhotoPaint.setTextSize(AndroidUtilities.dp(15));

            paint = parentAdapter.channelBlock != null ? channelNamePhotoPaint : channelNamePaint;
        } else if (parentBlock instanceof TL_pageBlockRelatedArticlesChild) {
            TL_pageBlockRelatedArticlesChild pageBlockRelatedArticlesChild = (TL_pageBlockRelatedArticlesChild) parentBlock;
            if (plainText == pageBlockRelatedArticlesChild.parent.articles.get(pageBlockRelatedArticlesChild.num).title) {
                if (relatedArticleHeaderPaint == null) {
                    relatedArticleHeaderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    relatedArticleHeaderPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                }
                relatedArticleHeaderPaint.setColor(getTextColor());
                relatedArticleHeaderPaint.setTextSize(AndroidUtilities.dp(15) + additionalSize);
                paint = relatedArticleHeaderPaint;
            } else {
                if (relatedArticleTextPaint == null) {
                    relatedArticleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                }
                relatedArticleTextPaint.setColor(getGrayTextColor());
                relatedArticleTextPaint.setTextSize(AndroidUtilities.dp(14) + additionalSize);
                paint = relatedArticleTextPaint;
            }
        } else if (isListItemBlock(parentBlock) && plainText != null) {
            if (listTextPointerPaint == null) {
                listTextPointerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                listTextPointerPaint.setColor(getTextColor());
            }
            if (listTextNumPaint == null) {
                listTextNumPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                listTextNumPaint.setColor(getTextColor());
            }
            listTextPointerPaint.setTextSize(AndroidUtilities.dp(19) + additionalSize);
            listTextNumPaint.setTextSize(AndroidUtilities.dp(16) + additionalSize);
            if (parentBlock instanceof TL_pageBlockListItem && !((TL_pageBlockListItem) parentBlock).parent.pageBlockList.ordered) {
                paint = listTextPointerPaint;
            } else {
                paint = listTextNumPaint;
            }
        } else {
            paint = getTextPaint(richText, richText, parentBlock);
        }
        StaticLayout result;
        if (maxLines != 0) {
            if (parentBlock instanceof TLRPC.TL_pageBlockPullquote) {
                result = StaticLayoutEx.createStaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, maxLines);
            } else {
                result = StaticLayoutEx.createStaticLayout(text, paint, width, align, 1.0f, AndroidUtilities.dp(4), false, TextUtils.TruncateAt.END, width, maxLines);
            }
        } else {
            if (text.charAt(text.length() - 1) == '\n') {
                text = text.subSequence(0, text.length() - 1);
            }
            if (parentBlock instanceof TLRPC.TL_pageBlockPullquote) {
                result = new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            } else {
                result = new StaticLayout(text, paint, width, align, 1.0f, AndroidUtilities.dp(4), false);
            }
        }
        if (result == null) {
            return null;
        }
        CharSequence finalText = result.getText();
        LinkPath textPath = null;
        LinkPath markPath = null;
        if (textY >= 0) {
            if (result != null && !searchResults.isEmpty() && searchText != null) {
                String lowerString = text.toString().toLowerCase();
                int startIndex = 0;
                int index;
                while ((index = lowerString.indexOf(searchText, startIndex)) >= 0) {
                    startIndex = index + searchText.length();
                    if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString.charAt(index - 1))) {
                        adapter[0].searchTextOffset.put(searchText + parentBlock + richText + index, textY + result.getLineTop(result.getLineForOffset(index)));
                    }
                }
            }
        }
        if (result != null && finalText instanceof Spanned) {
            Spanned spanned = (Spanned) finalText;
            try {
                AnchorSpan[] innerSpans = spanned.getSpans(0, spanned.length(), AnchorSpan.class);
                int linesCount = result.getLineCount();
                if (innerSpans != null && innerSpans.length > 0) {
                    for (int a = 0; a < innerSpans.length; a++) {
                        if (linesCount <= 1) {
                            parentAdapter.anchorsOffset.put(innerSpans[a].getName(), textY);
                        } else {
                            parentAdapter.anchorsOffset.put(innerSpans[a].getName(), textY + result.getLineTop(result.getLineForOffset(spanned.getSpanStart(innerSpans[a]))));
                        }
                    }
                }
            } catch (Exception ignore) {

            }
            try {
                TextPaintWebpageUrlSpan[] innerSpans = spanned.getSpans(0, spanned.length(), TextPaintWebpageUrlSpan.class);
                if (innerSpans != null && innerSpans.length > 0) {
                    textPath = new LinkPath(true);
                    textPath.setAllowReset(false);
                    for (int a = 0; a < innerSpans.length; a++) {
                        int start = spanned.getSpanStart(innerSpans[a]);
                        int end = spanned.getSpanEnd(innerSpans[a]);
                        textPath.setCurrentLayout(result, start, 0);
                        int shift = innerSpans[a].getTextPaint() != null ? innerSpans[a].getTextPaint().baselineShift : 0;
                        textPath.setBaselineShift(shift != 0 ? shift + AndroidUtilities.dp(shift > 0 ? 5 : -2) : 0);
                        result.getSelectionPath(start, end, textPath);
                    }
                    textPath.setAllowReset(true);
                }
            } catch (Exception ignore) {

            }
            try {
                TextPaintMarkSpan[] innerSpans = spanned.getSpans(0, spanned.length(), TextPaintMarkSpan.class);
                if (innerSpans != null && innerSpans.length > 0) {
                    markPath = new LinkPath(true);
                    markPath.setAllowReset(false);
                    for (int a = 0; a < innerSpans.length; a++) {
                        int start = spanned.getSpanStart(innerSpans[a]);
                        int end = spanned.getSpanEnd(innerSpans[a]);
                        markPath.setCurrentLayout(result, start, 0);
                        int shift = innerSpans[a].getTextPaint() != null ? innerSpans[a].getTextPaint().baselineShift : 0;
                        markPath.setBaselineShift(shift != 0 ? shift + AndroidUtilities.dp(shift > 0 ? 5 : -2) : 0);
                        result.getSelectionPath(start, end, markPath);
                    }
                    markPath.setAllowReset(true);
                }
            } catch (Exception ignore) {

            }
        }
        DrawingText drawingText = new DrawingText();
        drawingText.textLayout = result;
        drawingText.textPath = textPath;
        drawingText.markPath = markPath;
        drawingText.parentBlock = parentBlock;
        drawingText.parentText = richText;
        return drawingText;
    }

    private void drawLayoutLink(Canvas canvas, DrawingText layout) {
        if (canvas == null || layout == null || pressedLinkOwnerLayout != layout) {
            return;
        }
        if (pressedLink != null) {
            canvas.drawPath(urlPath, urlPaint);
        } else if (drawBlockSelection && layout != null) {
            float width;
            float x;
            if (layout.getLineCount() == 1) {
                width = layout.getLineWidth(0);
                x = layout.getLineLeft(0);
            } else {
                width = layout.getWidth();
                x = 0;
            }
            canvas.drawRect(-AndroidUtilities.dp(2) + x, 0, x + width + AndroidUtilities.dp(2), layout.getHeight(), urlPaint);
        }
    }

    private boolean checkLayoutForLinks(WebpageAdapter adapter, MotionEvent event, View parentView, DrawingText drawingText, int layoutX, int layoutY) {
        if (pageSwitchAnimation != null || parentView == null || !textSelectionHelper.isSelectable(parentView)) {
            return false;
        }
        pressedLinkOwnerView = parentView;
        if (drawingText != null) {
            StaticLayout layout = drawingText.textLayout;
            int x = (int) event.getX();
            int y = (int) event.getY();
            boolean removeLink = false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float width = 0;
                float left = Integer.MAX_VALUE;
                for (int a = 0, N = layout.getLineCount(); a < N; a++) {
                    width = Math.max(layout.getLineWidth(a), width);
                    left = Math.min(layout.getLineLeft(a), left);
                }
                if (x >= layoutX + left && x <= left + layoutX + width && y >= layoutY && y <= layoutY + layout.getHeight()) {
                    pressedLinkOwnerLayout = drawingText;
                    pressedLinkOwnerView = parentView;
                    pressedLayoutY = layoutY;
                    CharSequence text = layout.getText();
                    if (text instanceof Spannable) {
                        try {
                            int checkX = x - layoutX;
                            int checkY = y - layoutY;
                            final int line = layout.getLineForVertical(checkY);
                            final int off = layout.getOffsetForHorizontal(line, checkX);
                            left = layout.getLineLeft(line);
                            if (left <= checkX && left + layout.getLineWidth(line) >= checkX) {
                                Spannable buffer = (Spannable) layout.getText();
                                TextPaintUrlSpan[] link = buffer.getSpans(off, off, TextPaintUrlSpan.class);
                                if (link != null && link.length > 0) {
                                    pressedLink = link[0];
                                    int pressedStart = buffer.getSpanStart(pressedLink);
                                    int pressedEnd = buffer.getSpanEnd(pressedLink);
                                    for (int a = 1; a < link.length; a++) {
                                        TextPaintUrlSpan span = link[a];
                                        int start = buffer.getSpanStart(span);
                                        int end = buffer.getSpanEnd(span);
                                        if (pressedStart > start || end > pressedEnd) {
                                            pressedLink = span;
                                            pressedStart = start;
                                            pressedEnd = end;
                                        }
                                    }
                                    try {
                                        urlPath.setUseRoundRect(true);
                                        urlPath.setCurrentLayout(layout, pressedStart, 0);
                                        int shift = pressedLink.getTextPaint() != null ? pressedLink.getTextPaint().baselineShift : 0;
                                        urlPath.setBaselineShift(shift != 0 ? shift + AndroidUtilities.dp(shift > 0 ? 5 : -2) : 0);
                                        layout.getSelectionPath(pressedStart, pressedEnd, urlPath);
                                        parentView.invalidate();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressedLink != null) {
                    removeLink = true;
                    String url = pressedLink.getUrl();
                    if (url != null) {
                        if (linkSheet != null) {
                            linkSheet.dismiss();
                            linkSheet = null;
                        }
                        int index;
                        boolean isAnchor = false;
                        String anchor;
                        if ((index = url.lastIndexOf('#')) != -1) {
                            String webPageUrl;
                            if (!TextUtils.isEmpty(adapter.currentPage.cached_page.url)) {
                                webPageUrl = adapter.currentPage.cached_page.url.toLowerCase();
                            } else {
                                webPageUrl = adapter.currentPage.url.toLowerCase();
                            }
                            try {
                                anchor = URLDecoder.decode(url.substring(index + 1), "UTF-8");
                            } catch (Exception ignore) {
                                anchor = "";
                            }
                            if (url.toLowerCase().contains(webPageUrl)) {
                                if (TextUtils.isEmpty(anchor)) {
                                    layoutManager[0].scrollToPositionWithOffset(0, 0);
                                    checkScrollAnimated();
                                } else {
                                    scrollToAnchor(anchor);
                                }
                                isAnchor = true;
                            }
                        } else {
                            anchor = null;
                        }
                        if (!isAnchor) {
                            openWebpageUrl(pressedLink.getUrl(), anchor);
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL && (popupWindow == null || !popupWindow.isShowing())) {
                removeLink = true;
            }
            if (removeLink) {
                removePressedLink();
            }
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress(event.getX(), event.getY(), parentView);
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }
        if (parentView instanceof BlockDetailsCell) {
            return pressedLink != null;
        } else {
            return pressedLinkOwnerLayout != null;
        }
    }

    private void removePressedLink() {
        if (pressedLink == null && pressedLinkOwnerView == null) {
            return;
        }
        View parentView = pressedLinkOwnerView;
        pressedLink = null;
        pressedLinkOwnerLayout = null;
        pressedLinkOwnerView = null;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    private void openWebpageUrl(String url, String anchor) {
        if (openUrlReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, false);
            openUrlReqId = 0;
        }
        int reqId = ++lastReqId;
        showProgressView(true, true);
        final TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
        req.url = url;
        req.hash = 0;
        openUrlReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (openUrlReqId == 0 || reqId != lastReqId) {
                return;
            }
            openUrlReqId = 0;
            showProgressView(true, false);
            if (isVisible) {
                if (response instanceof TLRPC.TL_webPage && ((TLRPC.TL_webPage) response).cached_page instanceof TLRPC.TL_page) {
                    addPageToStack((TLRPC.TL_webPage) response, anchor, 1);
                } else {
                    Browser.openUrl(parentActivity, req.url);
                }
            }
        }));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject messageObject = (MessageObject) args[0];
            if (listView != null) {
                for (int i = 0; i < listView.length; i++) {
                    int count = listView[i].getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = listView[i].getChildAt(a);
                        if (view instanceof BlockAudioCell) {
                            BlockAudioCell cell = (BlockAudioCell) view;
                            cell.updateButtonState(true);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (listView != null) {
                for (int i = 0; i < listView.length; i++) {
                    int count = listView[i].getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = listView[i].getChildAt(a);
                        if (view instanceof BlockAudioCell) {
                            BlockAudioCell cell = (BlockAudioCell) view;
                            MessageObject messageObject = cell.getMessageObject();
                            if (messageObject != null) {
                                cell.updateButtonState(true);
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (listView != null) {
                for (int i = 0; i < listView.length; i++) {
                    int count = listView[i].getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = listView[i].getChildAt(a);
                        if (view instanceof BlockAudioCell) {
                            BlockAudioCell cell = (BlockAudioCell) view;
                            MessageObject playing = cell.getMessageObject();
                            if (playing != null && playing.getId() == mid) {
                                MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                                if (player != null) {
                                    playing.audioProgress = player.audioProgress;
                                    playing.audioProgressSec = player.audioProgressSec;
                                    playing.audioPlayerDuration = player.audioPlayerDuration;
                                    cell.updatePlayingMessageProgress();
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public void updateThemeColors(float progress) {
        refreshThemeColors();
        updatePaintColors();
        if (windowView != null) {
            listView[0].invalidateViews();
            listView[1].invalidateViews();
            windowView.invalidate();
            searchPanel.invalidate();
            if (progress == 1) {
                adapter[0].notifyDataSetChanged();
                adapter[1].notifyDataSetChanged();
            }
        }
    }

    private void updatePaintSize() {
        for (int i = 0; i < 2; i++) {
            adapter[i].notifyDataSetChanged();
        }
    }

    private void updatePaintFonts() {
        ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putInt("font_type", selectedFont).commit();
        Typeface typefaceNormal = selectedFont == 0 ? Typeface.DEFAULT : Typeface.SERIF;
        Typeface typefaceItalic = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/ritalic.ttf") : Typeface.create("serif", Typeface.ITALIC);
        Typeface typefaceBold = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/rmedium.ttf") : Typeface.create("serif", Typeface.BOLD);
        Typeface typefaceBoldItalic = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf") : Typeface.create("serif", Typeface.BOLD_ITALIC);

        for (int a = 0; a < quoteTextPaints.size(); a++) {
            updateFontEntry(quoteTextPaints.keyAt(a), quoteTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < preformattedTextPaints.size(); a++) {
            updateFontEntry(preformattedTextPaints.keyAt(a), preformattedTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < paragraphTextPaints.size(); a++) {
            updateFontEntry(paragraphTextPaints.keyAt(a), paragraphTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < listTextPaints.size(); a++) {
            updateFontEntry(listTextPaints.keyAt(a), listTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < embedPostTextPaints.size(); a++) {
            updateFontEntry(embedPostTextPaints.keyAt(a), embedPostTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < mediaCaptionTextPaints.size(); a++) {
            updateFontEntry(mediaCaptionTextPaints.keyAt(a), mediaCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < mediaCreditTextPaints.size(); a++) {
            updateFontEntry(mediaCreditTextPaints.keyAt(a), mediaCreditTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < photoCaptionTextPaints.size(); a++) {
            updateFontEntry(photoCaptionTextPaints.keyAt(a), photoCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < photoCreditTextPaints.size(); a++) {
            updateFontEntry(photoCreditTextPaints.keyAt(a), photoCreditTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < authorTextPaints.size(); a++) {
            updateFontEntry(authorTextPaints.keyAt(a), authorTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < footerTextPaints.size(); a++) {
            updateFontEntry(footerTextPaints.keyAt(a), footerTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < embedPostCaptionTextPaints.size(); a++) {
            updateFontEntry(embedPostCaptionTextPaints.keyAt(a), embedPostCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < relatedArticleTextPaints.size(); a++) {
            updateFontEntry(relatedArticleTextPaints.keyAt(a), relatedArticleTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < detailsTextPaints.size(); a++) {
            updateFontEntry(detailsTextPaints.keyAt(a), detailsTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (int a = 0; a < tableTextPaints.size(); a++) {
            updateFontEntry(tableTextPaints.keyAt(a), tableTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
    }

    private void updateFontEntry(int flags, TextPaint paint, Typeface typefaceNormal, Typeface typefaceBoldItalic, Typeface typefaceBold, Typeface typefaceItalic) {
        if ((flags & TEXT_FLAG_MEDIUM) != 0 && (flags & TEXT_FLAG_ITALIC) != 0) {
            paint.setTypeface(typefaceBoldItalic);
        } else if ((flags & TEXT_FLAG_MEDIUM) != 0) {
            paint.setTypeface(typefaceBold);
        } else if ((flags & TEXT_FLAG_ITALIC) != 0) {
            paint.setTypeface(typefaceItalic);
        } else if ((flags & TEXT_FLAG_MONO) != 0) {
            //change nothing
        } else {
            paint.setTypeface(typefaceNormal);
        }
    }

    private void updatePaintColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        for (int i = 0; i < listView.length; i++) {
            listView[i].setGlowColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        if (listTextPointerPaint != null) {
            listTextPointerPaint.setColor(getTextColor());
        }
        if (listTextNumPaint != null) {
            listTextNumPaint.setColor(getTextColor());
        }
        if (embedPostAuthorPaint != null) {
            embedPostAuthorPaint.setColor(getTextColor());
        }
        if (channelNamePaint != null) {
            channelNamePaint.setColor(getTextColor());
        }
        if (channelNamePhotoPaint != null) {
            channelNamePhotoPaint.setColor(0xffffffff);
        }
        if (relatedArticleHeaderPaint != null) {
            relatedArticleHeaderPaint.setColor(getTextColor());
        }
        if (relatedArticleTextPaint != null) {
            relatedArticleTextPaint.setColor(getGrayTextColor());
        }

        if (embedPostDatePaint != null) {
            embedPostDatePaint.setColor(getGrayTextColor());
        }

        createPaint(true);

        setMapColors(titleTextPaints);
        setMapColors(kickerTextPaints);
        setMapColors(subtitleTextPaints);
        setMapColors(headerTextPaints);
        setMapColors(subheaderTextPaints);
        setMapColors(quoteTextPaints);
        setMapColors(preformattedTextPaints);
        setMapColors(paragraphTextPaints);
        setMapColors(listTextPaints);
        setMapColors(embedPostTextPaints);
        setMapColors(mediaCaptionTextPaints);
        setMapColors(mediaCreditTextPaints);
        setMapColors(photoCaptionTextPaints);
        setMapColors(photoCreditTextPaints);
        setMapColors(authorTextPaints);
        setMapColors(footerTextPaints);
        setMapColors(embedPostCaptionTextPaints);
        setMapColors(relatedArticleTextPaints);
        setMapColors(detailsTextPaints);
        setMapColors(tableTextPaints);
    }

    private void setMapColors(SparseArray<TextPaint> map) {
        for (int a = 0; a < map.size(); a++) {
            int flags = map.keyAt(a);
            TextPaint paint = map.valueAt(a);
            if ((flags & TEXT_FLAG_URL) != 0 || (flags & TEXT_FLAG_WEBPAGE_URL) != 0) {
                paint.setColor(getLinkTextColor());
            } else {
                paint.setColor(getTextColor());
            }
        }
    }

    public void setParentActivity(Activity activity, BaseFragment fragment) {
        parentFragment = fragment;
        currentAccount = UserConfig.selectedAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        if (parentActivity == activity) {
            updatePaintColors();
            refreshThemeColors();
            return;
        }
        parentActivity = activity;

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
        selectedFont = sharedPreferences.getInt("font_type", 0);
        createPaint(false);
        backgroundPaint = new Paint();

        layerShadowDrawable = activity.getResources().getDrawable(R.drawable.layer_shadow);
        slideDotDrawable = activity.getResources().getDrawable(R.drawable.slide_dot_small);
        slideDotBigDrawable = activity.getResources().getDrawable(R.drawable.slide_dot_big);
        scrimPaint = new Paint();

        windowView = new WindowView(activity);
        windowView.setWillNotDraw(false);
        windowView.setClipChildren(true);
        windowView.setFocusable(false);

        containerView = new FrameLayout(activity) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (windowView.movingPage) {
                    int width = getMeasuredWidth();
                    int translationX = (int) listView[0].getTranslationX();
                    int clipLeft = 0;
                    int clipRight = width;

                    if (child == listView[1]) {
                        clipRight = translationX;
                    } else if (child == listView[0]) {
                        clipLeft = translationX;
                    }

                    final int restoreCount = canvas.save();
                    canvas.clipRect(clipLeft, 0, clipRight, getHeight());
                    final boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restoreToCount(restoreCount);

                    if (translationX != 0) {
                        if (child == listView[0]) {
                            final float alpha = Math.max(0, Math.min((width - translationX) / (float) AndroidUtilities.dp(20), 1.0f));
                            layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), child.getTop(), translationX, child.getBottom());
                            layerShadowDrawable.setAlpha((int) (0xff * alpha));
                            layerShadowDrawable.draw(canvas);
                        } else if (child == listView[1]) {
                            float opacity = Math.min(0.8f, (width - translationX) / (float) width);
                            if (opacity < 0) {
                                opacity = 0;
                            }
                            scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * opacity) << 24);
                            canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
                        }
                    }

                    return result;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        //containerView.setFitsSystemWindows(true);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            containerView.setOnApplyWindowInsetsListener((v, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
            });
        }

        fullscreenVideoContainer = new FrameLayout(activity);
        fullscreenVideoContainer.setBackgroundColor(0xff000000);
        fullscreenVideoContainer.setVisibility(View.INVISIBLE);
        windowView.addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fullscreenAspectRatioView = new AspectRatioFrameLayout(activity);
        fullscreenAspectRatioView.setVisibility(View.GONE);
        fullscreenVideoContainer.addView(fullscreenAspectRatioView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        fullscreenTextureView = new TextureView(activity);

        listView = new RecyclerListView[2];
        adapter = new WebpageAdapter[2];
        layoutManager = new LinearLayoutManager[2];
        for (int i = 0; i < listView.length; i++) {
            WebpageAdapter webpageAdapter = adapter[i] = new WebpageAdapter(parentActivity);
            listView[i] = new RecyclerListView(activity) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    int count = getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = getChildAt(a);
                        if (child.getTag() instanceof Integer) {
                            Integer tag = (Integer) child.getTag();
                            if (tag == 90) {
                                int bottom = child.getBottom();
                                if (bottom < getMeasuredHeight()) {
                                    int height = getMeasuredHeight();
                                    child.layout(0, height - child.getMeasuredHeight(), child.getMeasuredWidth(), height);
                                    break;
                                }
                            }
                        }
                    }
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent e) {
                    if (pressedLinkOwnerLayout != null && pressedLink == null && (popupWindow == null || !popupWindow.isShowing()) && (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)) {
                        pressedLink = null;
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                    } else if (pressedLinkOwnerLayout != null && pressedLink != null && e.getAction() == MotionEvent.ACTION_UP) {
                        checkLayoutForLinks(webpageAdapter, e, pressedLinkOwnerView, pressedLinkOwnerLayout, 0, 0);
                    }
                    return super.onInterceptTouchEvent(e);
                }

                @Override
                public boolean onTouchEvent(MotionEvent e) {
                    if (pressedLinkOwnerLayout != null && pressedLink == null && (popupWindow == null || !popupWindow.isShowing()) && (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)) {
                        pressedLink = null;
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                    }
                    return super.onTouchEvent(e);
                }

                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (windowView.movingPage) {
                        containerView.invalidate();
                        float progress = translationX / getMeasuredWidth();
                        setCurrentHeaderHeight((int) (windowView.startMovingHeaderHeight + (AndroidUtilities.dp(56) - windowView.startMovingHeaderHeight) * progress));
                    }
                }
            };
            ((DefaultItemAnimator) listView[i].getItemAnimator()).setDelayAnimations(false);
            listView[i].setLayoutManager(layoutManager[i] = new LinearLayoutManager(parentActivity, LinearLayoutManager.VERTICAL, false));
            listView[i].setAdapter(webpageAdapter);
            listView[i].setClipToPadding(false);
            listView[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            listView[i].setPadding(0, AndroidUtilities.dp(56), 0, 0);
            listView[i].setTopGlowOffset(AndroidUtilities.dp(56));
            containerView.addView(listView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            listView[i].setOnItemLongClickListener((view, position) -> {
                if (view instanceof BlockRelatedArticlesCell) {
                    BlockRelatedArticlesCell cell = (BlockRelatedArticlesCell) view;
                    showCopyPopup(cell.currentBlock.parent.articles.get(cell.currentBlock.num).url);
                    return true;
                }
                return false;
            });
            listView[i].setOnItemClickListener((view, position, x, y) -> {
                if (textSelectionHelper != null) {
                    if (textSelectionHelper.isSelectionMode()) {
                        textSelectionHelper.clear();
                        return;
                    }
                    textSelectionHelper.clear();
                }
                if (view instanceof ReportCell && webpageAdapter.currentPage != null) {
                    ReportCell cell = (ReportCell) view;
                    if (previewsReqId != 0 || cell.hasViews && x < view.getMeasuredWidth() / 2) {
                        return;
                    }
                    TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat("previews");
                    if (object instanceof TLRPC.TL_user) {
                        openPreviewsChat((TLRPC.User) object, webpageAdapter.currentPage.id);
                    } else {
                        final int currentAccount = UserConfig.selectedAccount;
                        final long pageId = webpageAdapter.currentPage.id;
                        showProgressView(true, true);
                        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                        req.username = "previews";
                        previewsReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (previewsReqId == 0) {
                                return;
                            }
                            previewsReqId = 0;
                            showProgressView(true, false);
                            if (response != null) {
                                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true);
                                if (!res.users.isEmpty()) {
                                    openPreviewsChat(res.users.get(0), pageId);
                                }
                            }
                        }));
                    }
                } else if (position >= 0 && position < webpageAdapter.localBlocks.size()) {
                    TLRPC.PageBlock pageBlock = webpageAdapter.localBlocks.get(position);
                    TLRPC.PageBlock originalBlock = pageBlock;
                    pageBlock = getLastNonListPageBlock(pageBlock);
                    if (pageBlock instanceof TL_pageBlockDetailsChild) {
                        TL_pageBlockDetailsChild detailsChild = (TL_pageBlockDetailsChild) pageBlock;
                        pageBlock = detailsChild.block;
                    }
                    if (pageBlock instanceof TLRPC.TL_pageBlockChannel) {
                        TLRPC.TL_pageBlockChannel pageBlockChannel = (TLRPC.TL_pageBlockChannel) pageBlock;
                        MessagesController.getInstance(currentAccount).openByUserName(pageBlockChannel.channel.username, parentFragment, 2);
                        close(false, true);
                    } else if (pageBlock instanceof TL_pageBlockRelatedArticlesChild) {
                        TL_pageBlockRelatedArticlesChild pageBlockRelatedArticlesChild = (TL_pageBlockRelatedArticlesChild) pageBlock;
                        openWebpageUrl(pageBlockRelatedArticlesChild.parent.articles.get(pageBlockRelatedArticlesChild.num).url, null);
                    } else if (pageBlock instanceof TLRPC.TL_pageBlockDetails) {
                        view = getLastNonListCell(view);
                        if (!(view instanceof BlockDetailsCell)) {
                            return;
                        }

                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                        int index = webpageAdapter.blocks.indexOf(originalBlock);
                        if (index < 0) {
                            return;
                        }
                        TLRPC.TL_pageBlockDetails pageBlockDetails = (TLRPC.TL_pageBlockDetails) pageBlock;
                        pageBlockDetails.open = !pageBlockDetails.open;

                        int oldCount = webpageAdapter.getItemCount();
                        webpageAdapter.updateRows();
                        int newCount = webpageAdapter.getItemCount();
                        int changeCount = Math.abs(newCount - oldCount);

                        BlockDetailsCell cell = (BlockDetailsCell) view;
                        cell.arrow.setAnimationProgressAnimated(pageBlockDetails.open ? 0.0f : 1.0f);
                        cell.invalidate();
                        if (changeCount != 0) {
                            if (pageBlockDetails.open) {
                                webpageAdapter.notifyItemRangeInserted(position + 1, changeCount);
                            } else {
                                webpageAdapter.notifyItemRangeRemoved(position + 1, changeCount);
                            }
                        }
                    }
                }
            });
            listView[i].setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        textSelectionHelper.stopScrolling();
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (recyclerView.getChildCount() == 0) {
                        return;
                    }
                    textSelectionHelper.onParentScrolled();
                    headerView.invalidate();
                    checkScroll(dy);
                }
            });
        }

        headerPaint.setColor(0xff000000);
        statusBarPaint.setColor(0xff000000);
        headerProgressPaint.setColor(0xff242426);
        headerView = new FrameLayout(activity) {
            @Override
            protected void onDraw(Canvas canvas) {
                int width = getMeasuredWidth();
                int height = getMeasuredHeight();
                canvas.drawRect(0, 0, width, height, headerPaint);
                if (layoutManager == null) {
                    return;
                }
                int first = layoutManager[0].findFirstVisibleItemPosition();
                int last = layoutManager[0].findLastVisibleItemPosition();
                int count = layoutManager[0].getItemCount();
                View view;
                if (last >= count - 2) {
                    view = layoutManager[0].findViewByPosition(count - 2);
                } else {
                    view = layoutManager[0].findViewByPosition(first);
                }
                if (view == null) {
                    return;
                }

                float itemProgress = width / (float) (count - 1);

                int childCount = layoutManager[0].getChildCount();

                float viewHeight = view.getMeasuredHeight();
                float viewProgress;
                if (last >= count - 2) {
                    viewProgress = (count - 2 - first) * itemProgress * (listView[0].getMeasuredHeight() - view.getTop()) / viewHeight;
                } else {
                    viewProgress = itemProgress * (1.0f - (Math.min(0, view.getTop() - listView[0].getPaddingTop()) + viewHeight) / viewHeight);
                }
                float progress = first * itemProgress + viewProgress;

                canvas.drawRect(0, 0, progress, height, headerProgressPaint);
            }
        };
        headerView.setWillNotDraw(false);
        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));
        headerView.setOnClickListener(v -> listView[0].smoothScrollToPosition(0));

        titleTextView = new SimpleTextView(activity);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        titleTextView.setTextSize(20);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setTextColor(0xffb3b3b3);
        titleTextView.setPivotX(0.0f);
        titleTextView.setPivotY(AndroidUtilities.dp(28));
        headerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.LEFT | Gravity.TOP, 72, 0, 48 * 2, 0));

        lineProgressView = new LineProgressView(activity);
        lineProgressView.setProgressColor(0xffffffff);
        lineProgressView.setPivotX(0.0f);
        lineProgressView.setPivotY(AndroidUtilities.dp(2));
        headerView.addView(lineProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 1));

        lineProgressTickRunnable = () -> {
            float progressLeft = 0.7f - lineProgressView.getCurrentProgress();
            if (progressLeft > 0.0f) {
                float tick;
                if (progressLeft < 0.25f) {
                    tick = 0.01f;
                } else {
                    tick = 0.02f;
                }
                lineProgressView.setProgress(lineProgressView.getCurrentProgress() + tick, true);
                AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100);
            }
        };

        menuContainer = new FrameLayout(activity);
        headerView.addView(menuContainer, LayoutHelper.createFrame(48, 56, Gravity.TOP | Gravity.RIGHT));

        searchShadow = new View(activity);
        searchShadow.setBackgroundResource(R.drawable.header_shadow);
        searchShadow.setAlpha(0.0f);
        containerView.addView(searchShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.TOP, 0, 56, 0, 0));

        searchContainer = new FrameLayout(parentActivity);
        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchContainer.setVisibility(View.INVISIBLE);
        if (Build.VERSION.SDK_INT < 21) {
            searchContainer.setAlpha(0.0f);
        }
        headerView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));

        searchField = new EditTextBoldCursor(parentActivity) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                return super.onTouchEvent(event);
            }
        };
        searchField.setCursorWidth(1.5f);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        searchField.setSingleLine(true);
        searchField.setHint(LocaleController.getString("Search", R.string.Search));
        searchField.setBackgroundResource(0);
        searchField.setPadding(0, 0, 0, 0);
        int inputType = searchField.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        searchField.setInputType(inputType);
        if (Build.VERSION.SDK_INT < 23) {
            searchField.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                public void onDestroyActionMode(ActionMode mode) {

                }

                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }
            });
        }
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                AndroidUtilities.hideKeyboard(searchField);
            }
            return false;
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreOnTextChange) {
                    ignoreOnTextChange = false;
                    return;
                }
                processSearch(s.toString().toLowerCase());
                if (clearButton != null) {
                    if (TextUtils.isEmpty(s)) {
                        if (clearButton.getTag() != null) {
                            clearButton.setTag(null);
                            clearButton.clearAnimation();
                            if (animateClear) {
                                clearButton.animate().setInterpolator(new DecelerateInterpolator()).alpha(0.0f).setDuration(180).scaleY(0.0f).scaleX(0.0f).rotation(45).withEndAction(() -> clearButton.setVisibility(View.INVISIBLE)).start();
                            } else {
                                clearButton.setAlpha(0.0f);
                                clearButton.setRotation(45);
                                clearButton.setScaleX(0.0f);
                                clearButton.setScaleY(0.0f);
                                clearButton.setVisibility(View.INVISIBLE);
                                animateClear = true;
                            }
                        }
                    } else {
                        if (clearButton.getTag() == null) {
                            clearButton.setTag(1);
                            clearButton.clearAnimation();
                            clearButton.setVisibility(View.VISIBLE);
                            if (animateClear) {
                                clearButton.animate().setInterpolator(new DecelerateInterpolator()).alpha(1.0f).setDuration(180).scaleY(1.0f).scaleX(1.0f).rotation(0).start();
                            } else {
                                clearButton.setAlpha(1.0f);
                                clearButton.setRotation(0);
                                clearButton.setScaleX(1.0f);
                                clearButton.setScaleY(1.0f);
                                animateClear = true;
                            }
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchField.setTextIsSelectable(false);
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL, 72, 0, 48, 0));

        clearButton = new ImageView(parentActivity) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                clearAnimation();
                if (getTag() == null) {
                    clearButton.setVisibility(INVISIBLE);
                    clearButton.setAlpha(0.0f);
                    clearButton.setRotation(45);
                    clearButton.setScaleX(0.0f);
                    clearButton.setScaleY(0.0f);
                } else {
                    clearButton.setAlpha(1.0f);
                    clearButton.setRotation(0);
                    clearButton.setScaleX(1.0f);
                    clearButton.setScaleY(1.0f);
                }
            }
        };
        clearButton.setImageDrawable(new CloseProgressDrawable2());
        clearButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setAlpha(0.0f);
        clearButton.setRotation(45);
        clearButton.setScaleX(0.0f);
        clearButton.setScaleY(0.0f);
        clearButton.setOnClickListener(v -> {
            if (searchField.length() != 0) {
                searchField.setText("");
            }
            searchField.requestFocus();
            AndroidUtilities.showKeyboard(searchField);
        });
        clearButton.setContentDescription(LocaleController.getString("ClearButton", R.string.ClearButton));
        searchContainer.addView(clearButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        backButton = new ImageView(activity);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backDrawable = new BackDrawable(false);
        backDrawable.setAnimationTime(200.0f);
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        backDrawable.setRotatedColor(0xffb3b3b3);
        backDrawable.setRotation(1.0f, false);
        backButton.setImageDrawable(backDrawable);
        backButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        headerView.addView(backButton, LayoutHelper.createFrame(54, 56));
        backButton.setOnClickListener(v -> {
            /*if (collapsed) {
                uncollapse();
            } else {
                collapse();
            }*/
            if (searchContainer.getTag() != null) {
                showSearch(false);
            } else {
                close(true, true);
            }
        });
        backButton.setContentDescription(LocaleController.getString("AccDescrGoBack", R.string.AccDescrGoBack));

        menuButton = new ActionBarMenuItem(parentActivity, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, 0xffb3b3b3) {
            @Override
            public void toggleSubMenu() {
                super.toggleSubMenu();
                listView[0].stopScroll();
                checkScrollAnimated();
            }
        };
        menuButton.setLayoutInScreen(true);
        menuButton.setDuplicateParentStateEnabled(false);
        menuButton.setClickable(true);
        menuButton.setIcon(R.drawable.ic_ab_other);
        menuButton.addSubItem(search_item, R.drawable.msg_search, LocaleController.getString("Search", R.string.Search));
        menuButton.addSubItem(share_item, R.drawable.msg_share, LocaleController.getString("ShareFile", R.string.ShareFile));
        menuButton.addSubItem(open_item, R.drawable.msg_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp));
        menuButton.addSubItem(settings_item, R.drawable.menu_settings, LocaleController.getString("Settings", R.string.Settings));
        menuButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        menuButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuContainer.addView(menuButton, LayoutHelper.createFrame(48, 56));
        progressView = new ContextProgressView(activity, 2);
        progressView.setVisibility(View.GONE);
        menuContainer.addView(progressView, LayoutHelper.createFrame(48, 56));
        menuButton.setOnClickListener(v -> menuButton.toggleSubMenu());
        menuButton.setDelegate(id -> {
            if (adapter[0].currentPage == null || parentActivity == null) {
                return;
            }
            if (id == search_item) {
                showSearch(true);
            } else if (id == share_item) {
                showDialog(new ShareAlert(parentActivity, null, adapter[0].currentPage.url, false, adapter[0].currentPage.url, false));
            } else if (id == open_item) {
                String webPageUrl;
                if (!TextUtils.isEmpty(adapter[0].currentPage.cached_page.url)) {
                    webPageUrl = adapter[0].currentPage.cached_page.url;
                } else {
                    webPageUrl = adapter[0].currentPage.url;
                }
                Browser.openUrl(parentActivity, webPageUrl, true, false);
            } else if (id == settings_item) {
                BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                builder.setApplyTopPadding(false);

                LinearLayout settingsContainer = new LinearLayout(parentActivity);
                settingsContainer.setPadding(0, 0, 0, AndroidUtilities.dp(4));
                settingsContainer.setOrientation(LinearLayout.VERTICAL);

                HeaderCell headerCell = new HeaderCell(parentActivity);
                headerCell.setText(LocaleController.getString("FontSize", R.string.FontSize));
                settingsContainer.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 1, 3, 0));

                TextSizeCell sizeCell = new TextSizeCell(parentActivity);
                settingsContainer.addView(sizeCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 0, 3, 0));

                headerCell = new HeaderCell(parentActivity);
                headerCell.setText(LocaleController.getString("FontType", R.string.FontType));
                settingsContainer.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 4, 3, 2));

                for (int a = 0; a < 2; a++) {
                    fontCells[a] = new FontCell(parentActivity);
                    switch (a) {
                        case 0:
                            fontCells[a].setTextAndTypeface(LocaleController.getString("Default", R.string.Default), Typeface.DEFAULT);
                            break;
                        case 1:
                            fontCells[a].setTextAndTypeface("Serif", Typeface.SERIF);
                            break;
                    }
                    fontCells[a].select(a == selectedFont, false);
                    fontCells[a].setTag(a);
                    fontCells[a].setOnClickListener(v -> {
                        int num = (Integer) v.getTag();
                        selectedFont = num;
                        for (int a1 = 0; a1 < 2; a1++) {
                            fontCells[a1].select(a1 == num, true);
                        }
                        updatePaintFonts();
                        for (int i = 0; i < listView.length; i++) {
                            adapter[i].notifyDataSetChanged();
                        }
                    });
                    settingsContainer.addView(fontCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                }

                builder.setCustomView(settingsContainer);
                showDialog(linkSheet = builder.create());
            }
        });

        searchPanel = new FrameLayout(parentActivity) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        searchPanel.setOnTouchListener((v, event) -> true);
        searchPanel.setWillNotDraw(false);
        searchPanel.setVisibility(View.INVISIBLE);
        searchPanel.setFocusable(true);
        searchPanel.setFocusableInTouchMode(true);
        searchPanel.setClickable(true);
        searchPanel.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        containerView.addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        searchUpButton = new ImageView(parentActivity);
        searchUpButton.setScaleType(ImageView.ScaleType.CENTER);
        searchUpButton.setImageResource(R.drawable.msg_go_up);
        searchUpButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        searchUpButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchPanel.addView(searchUpButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 48, 0));
        searchUpButton.setOnClickListener(view -> scrollToSearchIndex(currentSearchIndex - 1));
        searchUpButton.setContentDescription(LocaleController.getString("AccDescrSearchNext", R.string.AccDescrSearchNext));

        searchDownButton = new ImageView(parentActivity);
        searchDownButton.setScaleType(ImageView.ScaleType.CENTER);
        searchDownButton.setImageResource(R.drawable.msg_go_down);
        searchDownButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        searchDownButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchPanel.addView(searchDownButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));
        searchDownButton.setOnClickListener(view -> scrollToSearchIndex(currentSearchIndex + 1));
        searchDownButton.setContentDescription(LocaleController.getString("AccDescrSearchPrev", R.string.AccDescrSearchPrev));

        searchCountText = new SimpleTextView(parentActivity);
        searchCountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchCountText.setTextSize(15);
        searchCountText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        searchCountText.setGravity(Gravity.LEFT);
        searchPanel.addView(searchCountText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 108, 0));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW - 1;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            if (Build.VERSION.SDK_INT >= 28) {
                windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }

        textSelectionHelper = new TextSelectionHelper.ArticleTextSelectionHelper();
        textSelectionHelper.setParentView(listView[0]);
        textSelectionHelper.layoutManager = layoutManager[0];
        textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                if (isSelected) {
                    showSearch(false);
                }
            }

            @Override
            public void onTextCopied() {
                BulletinFactory.of(containerView, null).createCopyBulletin(LocaleController.getString("TextCopied", R.string.TextCopied)).show();
            }
        });
        containerView.addView(textSelectionHelper.getOverlayView(activity));

        pinchToZoomHelper = new PinchToZoomHelper(containerView, windowView);
        pinchToZoomHelper.setClipBoundsListener(new PinchToZoomHelper.ClipBoundsListener() {
            @Override
            public void getClipTopBottom(float[] topBottom) {
                topBottom[0] = currentHeaderHeight;
                topBottom[1] = listView[0].getMeasuredHeight();
            }
        });
        pinchToZoomHelper.setCallback(new PinchToZoomHelper.Callback() {
            @Override
            public void onZoomStarted(MessageObject messageObject) {
                if (listView[0] != null) {
                    listView[0].cancelClickRunnables(true);
                }
            }
        });
        updatePaintColors();
    }

    private void showSearch(boolean show) {
        if (searchContainer == null || (searchContainer.getTag() != null) == show) {
            return;
        }
        searchContainer.setTag(show ? 1 : null);
        searchResults.clear();
        searchText = null;
        adapter[0].searchTextOffset.clear();
        currentSearchIndex = 0;
        if (attachedToWindow) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setDuration(250);
            if (show) {
                searchContainer.setVisibility(View.VISIBLE);
                backDrawable.setRotation(0.0f, true);
            } else {
                menuButton.setVisibility(View.VISIBLE);
                listView[0].invalidateViews();
                AndroidUtilities.hideKeyboard(searchField);
                updateWindowLayoutParamsForSearch();
            }
            ArrayList<Animator> animators = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 21) {
                if (show) {
                    searchContainer.setAlpha(1.0f);
                }
                int x = menuContainer.getLeft() + menuContainer.getMeasuredWidth() / 2;
                int y = menuContainer.getTop() + menuContainer.getMeasuredHeight() / 2;
                float rad = (float) Math.sqrt(x * x + y * y);
                Animator animator = ViewAnimationUtils.createCircularReveal(searchContainer, x, y, show ? 0 : rad, show ? rad : 0);
                animators.add(animator);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            searchContainer.setAlpha(0.0f);
                        }
                    }
                });
            } else {
                animators.add(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, show ? 1.0f : 0.0f));
            }
            if (!show) {
                animators.add(ObjectAnimator.ofFloat(searchPanel, View.ALPHA, 0.0f));
            }
            animators.add(ObjectAnimator.ofFloat(searchShadow, View.ALPHA, show ? 1.0f : 0.0f));
            animatorSet.playTogether(animators);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (show) {
                        updateWindowLayoutParamsForSearch();
                        searchField.requestFocus();
                        AndroidUtilities.showKeyboard(searchField);
                        menuButton.setVisibility(View.INVISIBLE);
                    } else {
                        searchContainer.setVisibility(View.INVISIBLE);
                        searchPanel.setVisibility(View.INVISIBLE);
                        searchField.setText("");
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    if (!show) {
                        backDrawable.setRotation(1.0f, true);
                    }
                }
            });
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            if (!show && !AndroidUtilities.usingHardwareInput && keyboardVisible) {
                runAfterKeyboardClose = animatorSet;
                AndroidUtilities.runOnUIThread(() -> {
                    if (runAfterKeyboardClose != null) {
                        runAfterKeyboardClose.start();
                        runAfterKeyboardClose = null;
                    }
                }, 300);
            } else {
                animatorSet.start();
            }
        } else {
            searchContainer.setAlpha(show ? 1.0f : 0.0f);
            menuButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
            backDrawable.setRotation(show ? 0.0f : 1.0f, false);
            searchShadow.setAlpha(show ? 1.0f : 0.0f);
            if (show) {
                searchContainer.setVisibility(View.VISIBLE);
            } else {
                searchContainer.setVisibility(View.INVISIBLE);
                searchPanel.setVisibility(View.INVISIBLE);
                searchField.setText("");
            }
            updateWindowLayoutParamsForSearch();
        }
    }

    private void updateWindowLayoutParamsForSearch() {
        /*try {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            if (searchContainer.getTag() != null) {
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            } else {
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            }
            wm.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }*/
    }

    private void updateSearchButtons() {
        if (searchResults == null) {
            return;
        }
        searchUpButton.setEnabled(!searchResults.isEmpty() && currentSearchIndex != 0);
        searchDownButton.setEnabled(!searchResults.isEmpty() && currentSearchIndex != searchResults.size() - 1);
        searchUpButton.setAlpha(searchUpButton.isEnabled() ? 1.0f : 0.5f);
        searchDownButton.setAlpha(searchDownButton.isEnabled() ? 1.0f : 0.5f);
        int count = searchResults.size();
        if (count < 0) {
            searchCountText.setText("");
        } else if (count == 0) {
            searchCountText.setText(LocaleController.getString("NoResult", R.string.NoResult));
        } else if (count == 1) {
            searchCountText.setText(LocaleController.getString("OneResult", R.string.OneResult));
        } else {
            searchCountText.setText(String.format(LocaleController.getPluralString("CountOfResults", count), currentSearchIndex + 1, count));
        }
    }

    private Runnable searchRunnable;
    private ArrayList<SearchResult> searchResults = new ArrayList<>();
    private String searchText;
    private int currentSearchIndex;
    private int lastSearchIndex = -1;

    private static class SearchResult {
        private int index;
        private Object text;
        private TLRPC.PageBlock block;
    }

    private void processSearch(final String text) {
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
        if (TextUtils.isEmpty(text)) {
            searchResults.clear();
            searchText = text;
            adapter[0].searchTextOffset.clear();
            searchPanel.setVisibility(View.INVISIBLE);
            listView[0].invalidateViews();
            scrollToSearchIndex(0);
            lastSearchIndex = -1;
            return;
        }
        int searchIndex = ++lastSearchIndex;
        AndroidUtilities.runOnUIThread(searchRunnable = () -> {
            HashMap<Object, TLRPC.PageBlock> copy = new HashMap<>(adapter[0].textToBlocks);
            ArrayList<Object> array = new ArrayList<>(adapter[0].textBlocks);
            searchRunnable = null;
            Utilities.searchQueue.postRunnable(() -> {
                ArrayList<SearchResult> results = new ArrayList<>();
                for (int b = 0, N = array.size(); b < N; b++) {
                    Object object = array.get(b);
                    TLRPC.PageBlock block = copy.get(object);
                    String textToSearchIn = null;
                    if (object instanceof TLRPC.RichText) {
                        TLRPC.RichText richText = (TLRPC.RichText) object;
                        CharSequence innerText = getText(adapter[0], null, richText, richText, block, 1000);
                        if (!TextUtils.isEmpty(innerText)) {
                            textToSearchIn = innerText.toString().toLowerCase();
                        }
                    } else if (object instanceof String) {
                        textToSearchIn = ((String) object).toLowerCase();
                    }
                    if (textToSearchIn != null) {
                        int startIndex = 0;
                        int index;
                        while ((index = textToSearchIn.indexOf(text, startIndex)) >= 0) {
                            startIndex = index + text.length();
                            if (index == 0 || AndroidUtilities.isPunctuationCharacter(textToSearchIn.charAt(index - 1))) {
                                SearchResult result = new SearchResult();
                                result.index = index;
                                result.block = block;
                                result.text = object;
                                results.add(result);
                            }
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (searchIndex == lastSearchIndex) {
                        searchPanel.setAlpha(1.0f);
                        searchPanel.setVisibility(View.VISIBLE);
                        searchResults = results;
                        searchText = text;
                        adapter[0].searchTextOffset.clear();
                        listView[0].invalidateViews();
                        scrollToSearchIndex(0);
                    }
                });
            });
        }, 400);
    }

    private void scrollToSearchIndex(int index) {
        if (index < 0 || index >= searchResults.size()) {
            updateSearchButtons();
            return;
        }
        currentSearchIndex = index;
        updateSearchButtons();
        SearchResult result = searchResults.get(index);
        TLRPC.PageBlock block = getLastNonListPageBlock(result.block);

        int row = -1;
        for (int a = 0, N = adapter[0].blocks.size(); a < N; a++) {
            TLRPC.PageBlock localBlock = adapter[0].blocks.get(a);
            if (localBlock instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild child = (TL_pageBlockDetailsChild) localBlock;
                if (child.block == result.block || child.block == block) {
                    if (openAllParentBlocks(child)) {
                        adapter[0].updateRows();
                        adapter[0].notifyDataSetChanged();
                    }
                    break;
                }
            }
        }
        for (int a = 0, N = adapter[0].localBlocks.size(); a < N; a++) {
            TLRPC.PageBlock localBlock = adapter[0].localBlocks.get(a);
            if (localBlock == result.block || localBlock == block) {
                row = a;
                break;
            } else if (localBlock instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild child = (TL_pageBlockDetailsChild) localBlock;
                if (child.block == result.block || child.block == block) {
                    row = a;
                    break;
                }
            }
        }

        if (row == -1) {
            return;
        }

        if (block instanceof TL_pageBlockDetailsChild) {
            if (openAllParentBlocks((TL_pageBlockDetailsChild) block)) {
                adapter[0].updateRows();
                adapter[0].notifyDataSetChanged();
            }
        }

        String key = searchText + result.block + result.text + result.index;
        Integer offset = adapter[0].searchTextOffset.get(key);
        if (offset == null) {
            int type = adapter[0].getTypeForBlock(result.block);
            RecyclerView.ViewHolder holder = adapter[0].onCreateViewHolder(null, type);
            adapter[0].bindBlockToHolder(type, holder, result.block, 0, 0);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(listView[0].getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            offset = adapter[0].searchTextOffset.get(key);
            if (offset == null) {
                offset = 0;
            }
        }
        layoutManager[0].scrollToPositionWithOffset(row, currentHeaderHeight - AndroidUtilities.dp(56) - offset + AndroidUtilities.dp(100));
        listView[0].invalidateViews();
    }

    public static class ScrollEvaluator extends IntEvaluator {

        @Override
        public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
            return super.evaluate(fraction, startValue, endValue);
        }
    }

    private void checkScrollAnimated() {
        int maxHeight = AndroidUtilities.dp(56);
        if (currentHeaderHeight == maxHeight) {
            return;
        }
        ValueAnimator va = ValueAnimator.ofObject(new IntEvaluator(), currentHeaderHeight, AndroidUtilities.dp(56)).setDuration(180);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(animation -> setCurrentHeaderHeight((int) animation.getAnimatedValue()));
        va.start();
    }

    private void setCurrentHeaderHeight(int newHeight) {
        if (searchContainer.getTag() != null) {
            return;
        }
        int maxHeight = AndroidUtilities.dp(56);
        int minHeight = Math.max(AndroidUtilities.statusBarHeight, AndroidUtilities.dp(24));

        if (newHeight < minHeight) {
            newHeight = minHeight;
        } else if (newHeight > maxHeight) {
            newHeight = maxHeight;
        }

        float heightDiff = maxHeight - minHeight;
        if (heightDiff == 0) {
            heightDiff = 1;
        }

        currentHeaderHeight = newHeight;
        float scale = 0.8f + (currentHeaderHeight - minHeight) / heightDiff * 0.2f;
        float scale2 = 0.5f + (currentHeaderHeight - minHeight) / heightDiff * 0.5f;
        backButton.setScaleX(scale);
        backButton.setScaleY(scale);
        backButton.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        menuContainer.setScaleX(scale);
        menuContainer.setScaleY(scale);
        titleTextView.setScaleX(scale);
        titleTextView.setScaleY(scale);
        lineProgressView.setScaleY(scale2);
        menuContainer.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        titleTextView.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        headerView.setTranslationY(currentHeaderHeight - maxHeight);
        searchShadow.setTranslationY(currentHeaderHeight - maxHeight);
        menuButton.setAdditionalYOffset(-(currentHeaderHeight - maxHeight) / 2 + (Build.VERSION.SDK_INT < 21 ? AndroidUtilities.statusBarHeight : 0));
        textSelectionHelper.setTopOffset(currentHeaderHeight);
        for (int i = 0; i < listView.length; i++) {
            listView[i].setTopGlowOffset(currentHeaderHeight);
        }
    }

    private void checkScroll(int dy) {
        setCurrentHeaderHeight(currentHeaderHeight - dy);
    }

    private void openPreviewsChat(TLRPC.User user, long wid) {
        if (user == null || !(parentActivity instanceof LaunchActivity)) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        args.putString("botUser", "webpage" + wid);
        ((LaunchActivity) parentActivity).presentFragment(new ChatActivity(args), false, true);
        close(false, true);
    }

    public boolean open(MessageObject messageObject) {
        return open(messageObject, null, null, true);
    }

    public boolean open(TLRPC.TL_webPage webpage, String url) {
        return open(null, webpage, url, true);
    }

    private boolean open(final MessageObject messageObject, TLRPC.WebPage webpage, String url, boolean first) {
        if (parentActivity == null || isVisible && !collapsed || messageObject == null && webpage == null) {
            return false;
        }

        String anchor = null;
        if (messageObject != null) {
            webpage = messageObject.messageOwner.media.webpage;
            int index;
            for (int a = 0; a < messageObject.messageOwner.entities.size(); a++) {
                TLRPC.MessageEntity entity = messageObject.messageOwner.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityUrl) {
                    try {
                        url = messageObject.messageOwner.message.substring(entity.offset, entity.offset + entity.length).toLowerCase();
                        String webPageUrl;
                        if (!TextUtils.isEmpty(webpage.cached_page.url)) {
                            webPageUrl = webpage.cached_page.url.toLowerCase();
                        } else {
                            webPageUrl = webpage.url.toLowerCase();
                        }
                        if (url.contains(webPageUrl) || webPageUrl.contains(url)) {
                            if ((index = url.lastIndexOf('#')) != -1) {
                                anchor = url.substring(index + 1);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        } else if (url != null) {
            int index;
            if ((index = url.lastIndexOf('#')) != -1) {
                anchor = url.substring(index + 1);
            }
        }

        pagesStack.clear();
        collapsed = false;
        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        listView[0].setTranslationY(0);
        listView[0].setTranslationX(0.0f);
        listView[1].setTranslationX(0.0f);
        listView[0].setAlpha(1.0f);
        windowView.setInnerTranslationX(0);

        layoutManager[0].scrollToPositionWithOffset(0, 0);
        if (first) {
            setCurrentHeaderHeight(AndroidUtilities.dp(56));
        } else {
            checkScrollAnimated();
        }

        boolean scrolledToAnchor = addPageToStack(webpage, anchor, 0);

        if (first) {
            final String anchorFinal = !scrolledToAnchor && anchor != null ? anchor : null;
            TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
            req.url = webpage.url;
            if (webpage.cached_page instanceof TLRPC.TL_pagePart_layer82 || webpage.cached_page.part) {
                req.hash = 0;
            } else {
                req.hash = webpage.hash;
            }
            final TLRPC.WebPage webPageFinal = webpage;
            final int currentAccount = UserConfig.selectedAccount;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_webPage) {
                    final TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) response;
                    if (webPage.cached_page == null) {
                        return;
                    }

                    if (!pagesStack.isEmpty() && pagesStack.get(0) == webPageFinal) {
                        if (messageObject != null) {
                            messageObject.messageOwner.media.webpage = webPage;
                            TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                            messagesRes.messages.add(messageObject.messageOwner);
                            MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.getDialogId(), -2, 0, false, messageObject.scheduled);
                        }
                        pagesStack.set(0, webPage);
                        if (pagesStack.size() == 1) {
                            ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().remove("article" + webPage.id).commit();
                            updateInterfaceForCurrentPage(webPage, false, 0);
                            if (anchorFinal != null) {
                                scrollToAnchor(anchorFinal);
                            }
                        }
                    }

                    LongSparseArray<TLRPC.WebPage> webpages = new LongSparseArray<>(1);
                    webpages.put(webPage.id, webPage);
                    MessagesStorage.getInstance(currentAccount).putWebPages(webpages);
                } else if (response instanceof TLRPC.TL_webPageNotModified) {
                    TLRPC.TL_webPageNotModified webPage = (TLRPC.TL_webPageNotModified) response;
                    if (webPageFinal != null && webPageFinal.cached_page != null) {
                        if (webPageFinal.cached_page.views != webPage.cached_page_views) {
                            webPageFinal.cached_page.views = webPage.cached_page_views;
                            webPageFinal.cached_page.flags |= 8;
                            for (int a = 0; a < adapter.length; a++) {
                                if (adapter[a].currentPage == webPageFinal) {
                                    int p = adapter[a].getItemCount() - 1;
                                    RecyclerView.ViewHolder holder = listView[a].findViewHolderForAdapterPosition(p);
                                    if (holder != null) {
                                        adapter[a].onViewAttachedToWindow(holder);
                                    }
                                }
                            }
                            if (messageObject != null) {
                                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                                messagesRes.messages.add(messageObject.messageOwner);
                                MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.getDialogId(), -2, 0, false, messageObject.scheduled);
                            }
                        }
                    }
                }
            }));
        }

        lastInsets = null;
        if (!isVisible) {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            if (attachedToWindow) {
                try {
                    wm.removeView(windowView);
                } catch (Exception e) {
                    //ignore
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= 21) {
                    windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                    if (Build.VERSION.SDK_INT >= 28) {
                        windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }
                }
                //windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
                windowView.setFocusable(false);
                containerView.setFocusable(false);
                wm.addView(windowView, windowLayoutParams);
            } catch (Exception e) {
                FileLog.e(e);
                return false;
            }
        } else {
            windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(windowView, windowLayoutParams);
        }
        isVisible = true;
        animationInProgress = 1;
        windowView.setAlpha(0);
        containerView.setAlpha(0);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(windowView, View.ALPHA, 0, 1.0f),
                ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, AndroidUtilities.dp(56), 0)
        );

        animationEndRunnable = () -> {
            if (containerView == null || windowView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            animationInProgress = 0;
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        };

        animatorSet.setDuration(150);
        animatorSet.setInterpolator(interpolator);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(allowAnimationIndex);
                    if (animationEndRunnable != null) {
                        animationEndRunnable.run();
                        animationEndRunnable = null;
                    }
                });
            }
        });
        transitionAnimationStartTime = System.currentTimeMillis();
        AndroidUtilities.runOnUIThread(() -> {
            allowAnimationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(allowAnimationIndex, new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats});
            animatorSet.start();
        });
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        return true;
    }

    private void showProgressView(boolean useLine, final boolean show) {
        if (useLine) {
            AndroidUtilities.cancelRunOnUIThread(lineProgressTickRunnable);
            if (show) {
                lineProgressView.setProgress(0.0f, false);
                lineProgressView.setProgress(0.3f, true);
                AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100);
            } else {
                lineProgressView.setProgress(1.0f, true);
            }
        } else {
            if (progressViewAnimation != null) {
                progressViewAnimation.cancel();
            }
            progressViewAnimation = new AnimatorSet();
            if (show) {
                progressView.setVisibility(View.VISIBLE);
                menuContainer.setEnabled(false);
                progressViewAnimation.playTogether(
                        ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(menuButton, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f));
            } else {
                menuButton.setVisibility(View.VISIBLE);
                menuContainer.setEnabled(true);
                progressViewAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(menuButton, View.ALPHA, 1.0f));
            }
            progressViewAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (progressViewAnimation != null && progressViewAnimation.equals(animation)) {
                        if (!show) {
                            progressView.setVisibility(View.INVISIBLE);
                        } else {
                            menuButton.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (progressViewAnimation != null && progressViewAnimation.equals(animation)) {
                        progressViewAnimation = null;
                    }
                }
            });
            progressViewAnimation.setDuration(150);
            progressViewAnimation.start();
        }
    }

    public void collapse() {
        if (parentActivity == null || !isVisible || checkAnimation()) {
            return;
        }
        if (fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
            if (customView != null) {
                fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                customViewCallback.onCustomViewHidden();
                fullscreenVideoContainer.removeView(customView);
                customView = null;
            } else if (fullscreenedVideo != null) {
                fullscreenedVideo.exitFullscreen();
            }
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, containerView.getMeasuredWidth() - AndroidUtilities.dp(56)),
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, ActionBar.getCurrentActionBarHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)),
                ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(listView[0], View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(listView[0], View.TRANSLATION_Y, -AndroidUtilities.dp(56)),
                ObjectAnimator.ofFloat(headerView, View.TRANSLATION_Y, 0),

                ObjectAnimator.ofFloat(backButton, View.SCALE_X, 1.0f),
                ObjectAnimator.ofFloat(backButton, View.SCALE_Y, 1.0f),
                ObjectAnimator.ofFloat(backButton, View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(menuContainer, View.SCALE_X, 1.0f),
                ObjectAnimator.ofFloat(menuContainer, View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(menuContainer, View.SCALE_Y, 1.0f)
        );
        collapsed = true;
        animationInProgress = 2;
        animationEndRunnable = () -> {
            if (containerView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            animationInProgress = 0;

            //windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(windowView, windowLayoutParams);

            //onClosed();
            //containerView.setScaleX(1.0f);
            //containerView.setScaleY(1.0f);
        };
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(250);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animationEndRunnable != null) {
                    animationEndRunnable.run();
                    animationEndRunnable = null;
                }
            }
        });
        transitionAnimationStartTime = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        backDrawable.setRotation(1, true);
        animatorSet.start();
    }

    public void uncollapse() {
        if (parentActivity == null || !isVisible || checkAnimation()) {
            return;
        }

        /*windowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.updateViewLayout(windowView, windowLayoutParams);*/

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0),
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(listView[0], View.ALPHA, 1.0f),
                ObjectAnimator.ofFloat(listView[0], View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(headerView, View.TRANSLATION_Y, 0),

                ObjectAnimator.ofFloat(backButton, View.SCALE_X, 1.0f),
                ObjectAnimator.ofFloat(backButton, View.SCALE_Y, 1.0f),
                ObjectAnimator.ofFloat(backButton, View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(menuContainer, View.SCALE_X, 1.0f),
                ObjectAnimator.ofFloat(menuContainer, View.TRANSLATION_Y, 0),
                ObjectAnimator.ofFloat(menuContainer, View.SCALE_Y, 1.0f)
        );
        collapsed = false;
        animationInProgress = 2;
        animationEndRunnable = () -> {
            if (containerView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            animationInProgress = 0;
            //onClosed();
        };
        animatorSet.setDuration(250);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animationEndRunnable != null) {
                    animationEndRunnable.run();
                    animationEndRunnable = null;
                }
            }
        });
        transitionAnimationStartTime = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        backDrawable.setRotation(0, true);
        animatorSet.start();
    }

    private void saveCurrentPagePosition() {
        if (adapter[0].currentPage == null) {
            return;
        }
        int position = layoutManager[0].findFirstVisibleItemPosition();
        if (position != RecyclerView.NO_POSITION) {
            int offset;
            View view = layoutManager[0].findViewByPosition(position);
            if (view != null) {
                offset = view.getTop();
            } else {
                offset = 0;
            }
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit();
            String key = "article" + adapter[0].currentPage.id;
            editor.putInt(key, position).putInt(key + "o", offset).putBoolean(key + "r", AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y).commit();
        }
    }

    private void refreshThemeColors() {
        if (deleteView != null) {
            deleteView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            deleteView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        }
        if (popupLayout != null) {
            popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        }
        if (searchContainer != null) {
            searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        if (searchField != null) {
            searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            searchField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        }
        if (searchUpButton != null) {
            searchUpButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            searchUpButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        }
        if (searchDownButton != null) {
            searchDownButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            searchDownButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        }
        if (searchCountText != null) {
            searchCountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        if (menuButton != null) {
            menuButton.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            menuButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false);
            menuButton.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
        }
        if (clearButton != null) {
            clearButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        }
        if (backDrawable != null) {
            backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    public void close(boolean byBackPress, boolean force) {
        if (parentActivity == null || closeAnimationInProgress || !isVisible || checkAnimation()) {
            return;
        }
        if (fullscreenVideoContainer.getVisibility() == View.VISIBLE) {
            if (customView != null) {
                fullscreenVideoContainer.setVisibility(View.INVISIBLE);
                customViewCallback.onCustomViewHidden();
                fullscreenVideoContainer.removeView(customView);
                customView = null;
            }  else if (fullscreenedVideo != null) {
                fullscreenedVideo.exitFullscreen();
            }
            if (!force) {
                return;
            }
        }
        if (textSelectionHelper.isSelectionMode()) {
            textSelectionHelper.clear();
            return;
        }
        if (searchContainer.getTag() != null) {
            showSearch(false);
            return;
        }
        if (openUrlReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, true);
            openUrlReqId = 0;
            showProgressView(true, false);
        }
        if (previewsReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(previewsReqId, true);
            previewsReqId = 0;
            showProgressView(true, false);
        }
        saveCurrentPagePosition();
        if (byBackPress && !force) {
            if (removeLastPageFromStack()) {
                return;
            }
        }

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        parentFragment = null;
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(windowView, View.ALPHA, 0),
                ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, 0, AndroidUtilities.dp(56))
        );
        animationInProgress = 2;
        animationEndRunnable = () -> {
            if (containerView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            animationInProgress = 0;
            onClosed();
        };
        animatorSet.setDuration(150);
        animatorSet.setInterpolator(interpolator);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animationEndRunnable != null) {
                    animationEndRunnable.run();
                    animationEndRunnable = null;
                }
            }
        });
        transitionAnimationStartTime = System.currentTimeMillis();
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        animatorSet.start();
    }

    private void onClosed() {
        isVisible = false;
        for (int i = 0; i < listView.length; i++) {
            adapter[i].cleanup();
        }
        try {
            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.e(e);
        }
        for (int a = 0; a < createdWebViews.size(); a++) {
            BlockEmbedCell cell = createdWebViews.get(a);
            cell.destroyWebView(false);
        }
        containerView.post(() -> {
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void loadChannel(final BlockChannelCell cell, WebpageAdapter adapter, TLRPC.Chat channel) {
        if (loadingChannel || TextUtils.isEmpty(channel.username)) {
            return;
        }
        loadingChannel = true;
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = channel.username;
        final int currentAccount = UserConfig.selectedAccount;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingChannel = false;
            if (parentFragment == null || adapter.blocks.isEmpty()) {
                return;
            }
            if (error == null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                if (!res.chats.isEmpty()) {
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true);
                    loadedChannel = res.chats.get(0);
                    if (loadedChannel.left && !loadedChannel.kicked) {
                        cell.setState(0, false);
                    } else {
                        cell.setState(4, false);
                    }
                } else {
                    cell.setState(4, false);
                }
            } else {
                cell.setState(4, false);
            }
        }));
    }

    private void joinChannel(final BlockChannelCell cell, final TLRPC.Chat channel) {
        final TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        req.channel = MessagesController.getInputChannel(channel);
        final int currentAccount = UserConfig.selectedAccount;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    cell.setState(0, false);
                    AlertsCreator.processError(currentAccount, error, parentFragment, req, true);
                });
                return;
            }
            boolean hasJoinMessage = false;
            TLRPC.Updates updates = (TLRPC.Updates) response;
            for (int a = 0; a < updates.updates.size(); a++) {
                TLRPC.Update update = updates.updates.get(a);
                if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                    if (((TLRPC.TL_updateNewChannelMessage) update).message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        hasJoinMessage = true;
                        break;
                    }
                }
            }
            MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            if (!hasJoinMessage) {
                MessagesController.getInstance(currentAccount).generateJoinMessage(channel.id, true);
            }
            AndroidUtilities.runOnUIThread(() -> cell.setState(2, false));
            AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).loadFullChat(channel.id, 0, true), 1000);
            MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(-channel.id, channel.id, new ArrayList<>(), null, true);
        });
    }

    private boolean checkAnimation() {
        if (animationInProgress != 0) {
            if (Math.abs(transitionAnimationStartTime - System.currentTimeMillis()) >= 500) {
                if (animationEndRunnable != null) {
                    animationEndRunnable.run();
                    animationEndRunnable = null;
                }
                animationInProgress = 0;
            }
        }
        return animationInProgress != 0;
    }

    public void destroyArticleViewer() {
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
        for (int a = 0; a < createdWebViews.size(); a++) {
            BlockEmbedCell cell = createdWebViews.get(a);
            cell.destroyWebView(true);
        }
        createdWebViews.clear();
        try {
            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.e(e);
        }
        parentActivity = null;
        parentFragment = null;
        Instance = null;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void showDialog(Dialog dialog) {
        if (parentActivity == null) {
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            visibleDialog = dialog;
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(dialog1 -> visibleDialog = null);
            dialog.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static final class WebPageUtils {

        private WebPageUtils() {
        }

        public static TLRPC.Photo getPhotoWithId(TLRPC.WebPage page, long id) {
            if (page == null || page.cached_page == null) {
                return null;
            }
            if (page.photo != null && page.photo.id == id) {
                return page.photo;
            }
            for (int a = 0; a < page.cached_page.photos.size(); a++) {
                TLRPC.Photo photo = page.cached_page.photos.get(a);
                if (photo.id == id) {
                    return photo;
                }
            }
            return null;
        }

        public static TLRPC.Document getDocumentWithId(TLRPC.WebPage page, long id) {
            if (page == null || page.cached_page == null) {
                return null;
            }
            if (page.document != null && page.document.id == id) {
                return page.document;
            }
            for (int a = 0; a < page.cached_page.documents.size(); a++) {
                TLRPC.Document document = page.cached_page.documents.get(a);
                if (document.id == id) {
                    return document;
                }
            }
            return null;
        }

        public static boolean isVideo(TLRPC.WebPage page, TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(page, ((TLRPC.TL_pageBlockVideo) block).video_id);
                if (document != null) {
                    return MessageObject.isVideoDocument(document);
                }
            }
            return false;
        }

        public static TLObject getMedia(TLRPC.WebPage page, TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockPhoto) {
                return getPhotoWithId(page, ((TLRPC.TL_pageBlockPhoto) block).photo_id);
            } else if (block instanceof TLRPC.TL_pageBlockVideo) {
                return getDocumentWithId(page, ((TLRPC.TL_pageBlockVideo) block).video_id);
            } else {
                return null;
            }
        }

        public static File getMediaFile(TLRPC.WebPage page,  TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockPhoto) {
                TLRPC.Photo photo = getPhotoWithId(page, ((TLRPC.TL_pageBlockPhoto) block).photo_id);
                if (photo != null) {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return FileLoader.getPathToAttach(sizeFull, true);
                    }
                }
            } else if (block instanceof TLRPC.TL_pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(page, ((TLRPC.TL_pageBlockVideo) block).video_id);
                if (document != null) {
                    return FileLoader.getPathToAttach(document, true);
                }
            }
            return null;
        }
    }

    private class WebpageAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<TLRPC.PageBlock> localBlocks = new ArrayList<>();
        private ArrayList<TLRPC.PageBlock> blocks = new ArrayList<>();
        private ArrayList<TLRPC.PageBlock> photoBlocks = new ArrayList<>();
        private HashMap<String, Integer> anchors = new HashMap<>();
        private HashMap<String, Integer> anchorsOffset = new HashMap<>();
        private HashMap<String, TLRPC.TL_textAnchor> anchorsParent = new HashMap<>();
        private HashMap<TLRPC.TL_pageBlockAudio, MessageObject> audioBlocks = new HashMap<>();
        private ArrayList<MessageObject> audioMessages = new ArrayList<>();
        private HashMap<Object, TLRPC.PageBlock> textToBlocks = new HashMap<>();
        private ArrayList<Object> textBlocks = new ArrayList<>();
        private HashMap<String, Integer> searchTextOffset = new HashMap<>();

        private TLRPC.WebPage currentPage;
        private TLRPC.TL_pageBlockChannel channelBlock;
        private boolean isRtl;

        public WebpageAdapter(Context ctx) {
            context = ctx;
        }

        private TLRPC.Photo getPhotoWithId(long id) {
            return WebPageUtils.getPhotoWithId(currentPage, id);
        }

        private TLRPC.Document getDocumentWithId(long id) {
            return WebPageUtils.getDocumentWithId(currentPage, id);
        }

        private void setRichTextParents(TLRPC.RichText parentRichText, TLRPC.RichText richText) {
            if (richText == null) {
                return;
            }
            richText.parentRichText = parentRichText;
            if (richText instanceof TLRPC.TL_textFixed) {
                setRichTextParents(richText, ((TLRPC.TL_textFixed) richText).text);
            } else if (richText instanceof TLRPC.TL_textItalic) {
                setRichTextParents(richText, ((TLRPC.TL_textItalic) richText).text);
            } else if (richText instanceof TLRPC.TL_textBold) {
                setRichTextParents(richText, ((TLRPC.TL_textBold) richText).text);
            } else if (richText instanceof TLRPC.TL_textUnderline) {
                setRichTextParents(richText, ((TLRPC.TL_textUnderline) richText).text);
            } else if (richText instanceof TLRPC.TL_textStrike) {
                setRichTextParents(richText, ((TLRPC.TL_textStrike) richText).text);
            } else if (richText instanceof TLRPC.TL_textEmail) {
                setRichTextParents(richText, ((TLRPC.TL_textEmail) richText).text);
            } else if (richText instanceof TLRPC.TL_textPhone) {
                setRichTextParents(richText, ((TLRPC.TL_textPhone) richText).text);
            } else if (richText instanceof TLRPC.TL_textUrl) {
                setRichTextParents(richText, ((TLRPC.TL_textUrl) richText).text);
            } else if (richText instanceof TLRPC.TL_textConcat) {
                int count = richText.texts.size();
                for (int a = 0; a < count; a++) {
                    setRichTextParents(richText, richText.texts.get(a));
                }
            } else if (richText instanceof TLRPC.TL_textSubscript) {
                setRichTextParents(richText, ((TLRPC.TL_textSubscript) richText).text);
            } else if (richText instanceof TLRPC.TL_textSuperscript) {
                setRichTextParents(richText, ((TLRPC.TL_textSuperscript) richText).text);
            } else if (richText instanceof TLRPC.TL_textMarked) {
                setRichTextParents(richText, ((TLRPC.TL_textMarked) richText).text);
            } else if (richText instanceof TLRPC.TL_textAnchor) {
                TLRPC.TL_textAnchor textAnchor = (TLRPC.TL_textAnchor) richText;
                setRichTextParents(richText, textAnchor.text);
                String name = textAnchor.name.toLowerCase();
                anchors.put(name, blocks.size());
                if (textAnchor.text instanceof TLRPC.TL_textPlain) {
                    TLRPC.TL_textPlain textPlain = (TLRPC.TL_textPlain) textAnchor.text;
                    if (!TextUtils.isEmpty(textPlain.text)) {
                        anchorsParent.put(name, textAnchor);
                    }
                } else if (!(textAnchor.text instanceof TLRPC.TL_textEmpty)) {
                    anchorsParent.put(name, textAnchor);
                }
                anchorsOffset.put(name, -1);
            }
        }

        private void addTextBlock(Object text, TLRPC.PageBlock block) {
            if (text instanceof TLRPC.TL_textEmpty || textToBlocks.containsKey(text)) {
                return;
            }
            textToBlocks.put(text, block);
            textBlocks.add(text);
        }

        private void setRichTextParents(TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockEmbedPost) {
                TLRPC.TL_pageBlockEmbedPost blockEmbedPost = (TLRPC.TL_pageBlockEmbedPost) block;
                setRichTextParents(null, blockEmbedPost.caption.text);
                setRichTextParents(null, blockEmbedPost.caption.credit);
                addTextBlock(blockEmbedPost.caption.text, blockEmbedPost);
                addTextBlock(blockEmbedPost.caption.credit, blockEmbedPost);
            } else if (block instanceof TLRPC.TL_pageBlockParagraph) {
                TLRPC.TL_pageBlockParagraph pageBlockParagraph = (TLRPC.TL_pageBlockParagraph) block;
                setRichTextParents(null, pageBlockParagraph.text);
                addTextBlock(pageBlockParagraph.text, pageBlockParagraph);
            } else if (block instanceof TLRPC.TL_pageBlockKicker) {
                TLRPC.TL_pageBlockKicker pageBlockKicker = (TLRPC.TL_pageBlockKicker) block;
                setRichTextParents(null, pageBlockKicker.text);
                addTextBlock(pageBlockKicker.text, pageBlockKicker);
            } else if (block instanceof TLRPC.TL_pageBlockFooter) {
                TLRPC.TL_pageBlockFooter pageBlockFooter = (TLRPC.TL_pageBlockFooter) block;
                setRichTextParents(null, pageBlockFooter.text);
                addTextBlock(pageBlockFooter.text, pageBlockFooter);
            } else if (block instanceof TLRPC.TL_pageBlockHeader) {
                TLRPC.TL_pageBlockHeader pageBlockHeader = (TLRPC.TL_pageBlockHeader) block;
                setRichTextParents(null, pageBlockHeader.text);
                addTextBlock(pageBlockHeader.text, pageBlockHeader);
            } else if (block instanceof TLRPC.TL_pageBlockPreformatted) {
                TLRPC.TL_pageBlockPreformatted pageBlockPreformatted = (TLRPC.TL_pageBlockPreformatted) block;
                setRichTextParents(null, pageBlockPreformatted.text);
                addTextBlock(pageBlockPreformatted.text, pageBlockPreformatted);
            } else if (block instanceof TLRPC.TL_pageBlockSubheader) {
                TLRPC.TL_pageBlockSubheader pageBlockTitle = (TLRPC.TL_pageBlockSubheader) block;
                setRichTextParents(null, pageBlockTitle.text);
                addTextBlock(pageBlockTitle.text, pageBlockTitle);
            } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                TLRPC.TL_pageBlockSlideshow pageBlockSlideshow = (TLRPC.TL_pageBlockSlideshow) block;
                setRichTextParents(null, pageBlockSlideshow.caption.text);
                setRichTextParents(null, pageBlockSlideshow.caption.credit);
                addTextBlock(pageBlockSlideshow.caption.text, pageBlockSlideshow);
                addTextBlock(pageBlockSlideshow.caption.credit, pageBlockSlideshow);
                for (int a = 0, size = pageBlockSlideshow.items.size(); a < size; a++) {
                    setRichTextParents(pageBlockSlideshow.items.get(a));
                }
            } else if (block instanceof TLRPC.TL_pageBlockPhoto) {
                TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) block;
                setRichTextParents(null, pageBlockPhoto.caption.text);
                setRichTextParents(null, pageBlockPhoto.caption.credit);
                addTextBlock(pageBlockPhoto.caption.text, pageBlockPhoto);
                addTextBlock(pageBlockPhoto.caption.credit, pageBlockPhoto);
            } else if (block instanceof TL_pageBlockListItem) {
                TL_pageBlockListItem pageBlockListItem = (TL_pageBlockListItem) block;
                if (pageBlockListItem.textItem != null) {
                    setRichTextParents(null, pageBlockListItem.textItem);
                    addTextBlock(pageBlockListItem.textItem, pageBlockListItem);
                } else if (pageBlockListItem.blockItem != null) {
                    setRichTextParents(pageBlockListItem.blockItem);
                }
            } else if (block instanceof TL_pageBlockOrderedListItem) {
                TL_pageBlockOrderedListItem pageBlockOrderedListItem = (TL_pageBlockOrderedListItem) block;
                if (pageBlockOrderedListItem.textItem != null) {
                    setRichTextParents(null, pageBlockOrderedListItem.textItem);
                    addTextBlock(pageBlockOrderedListItem.textItem, pageBlockOrderedListItem);
                } else if (pageBlockOrderedListItem.blockItem != null) {
                    setRichTextParents(pageBlockOrderedListItem.blockItem);
                }
            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                TLRPC.TL_pageBlockCollage pageBlockCollage = (TLRPC.TL_pageBlockCollage) block;
                setRichTextParents(null, pageBlockCollage.caption.text);
                setRichTextParents(null, pageBlockCollage.caption.credit);
                addTextBlock(pageBlockCollage.caption.text, pageBlockCollage);
                addTextBlock(pageBlockCollage.caption.credit, pageBlockCollage);
                for (int a = 0, size = pageBlockCollage.items.size(); a < size; a++) {
                    setRichTextParents(pageBlockCollage.items.get(a));
                }
            } else if (block instanceof TLRPC.TL_pageBlockEmbed) {
                TLRPC.TL_pageBlockEmbed pageBlockEmbed = (TLRPC.TL_pageBlockEmbed) block;
                setRichTextParents(null, pageBlockEmbed.caption.text);
                setRichTextParents(null, pageBlockEmbed.caption.credit);
                addTextBlock(pageBlockEmbed.caption.text, pageBlockEmbed);
                addTextBlock(pageBlockEmbed.caption.credit, pageBlockEmbed);
            } else if (block instanceof TLRPC.TL_pageBlockSubtitle) {
                TLRPC.TL_pageBlockSubtitle pageBlockSubtitle = (TLRPC.TL_pageBlockSubtitle) block;
                setRichTextParents(null, pageBlockSubtitle.text);
                addTextBlock(pageBlockSubtitle.text, pageBlockSubtitle);
            } else if (block instanceof TLRPC.TL_pageBlockBlockquote) {
                TLRPC.TL_pageBlockBlockquote pageBlockBlockquote = (TLRPC.TL_pageBlockBlockquote) block;
                setRichTextParents(null, pageBlockBlockquote.text);
                setRichTextParents(null, pageBlockBlockquote.caption);
                addTextBlock(pageBlockBlockquote.text, pageBlockBlockquote);
                addTextBlock(pageBlockBlockquote.caption, pageBlockBlockquote);
            } else if (block instanceof TLRPC.TL_pageBlockDetails) {
                TLRPC.TL_pageBlockDetails pageBlockDetails = (TLRPC.TL_pageBlockDetails) block;
                setRichTextParents(null, pageBlockDetails.title);
                addTextBlock(pageBlockDetails.title, pageBlockDetails);
                for (int a = 0, size = pageBlockDetails.blocks.size(); a < size; a++) {
                    setRichTextParents(pageBlockDetails.blocks.get(a));
                }
            } else if (block instanceof TLRPC.TL_pageBlockVideo) {
                TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) block;
                setRichTextParents(null, pageBlockVideo.caption.text);
                setRichTextParents(null, pageBlockVideo.caption.credit);
                addTextBlock(pageBlockVideo.caption.text, pageBlockVideo);
                addTextBlock(pageBlockVideo.caption.credit, pageBlockVideo);
            } else if (block instanceof TLRPC.TL_pageBlockPullquote) {
                TLRPC.TL_pageBlockPullquote pageBlockPullquote = (TLRPC.TL_pageBlockPullquote) block;
                setRichTextParents(null, pageBlockPullquote.text);
                setRichTextParents(null, pageBlockPullquote.caption);
                addTextBlock(pageBlockPullquote.text, pageBlockPullquote);
                addTextBlock(pageBlockPullquote.caption, pageBlockPullquote);
            } else if (block instanceof TLRPC.TL_pageBlockAudio) {
                TLRPC.TL_pageBlockAudio pageBlockAudio = (TLRPC.TL_pageBlockAudio) block;
                setRichTextParents(null, pageBlockAudio.caption.text);
                setRichTextParents(null, pageBlockAudio.caption.credit);
                addTextBlock(pageBlockAudio.caption.text, pageBlockAudio);
                addTextBlock(pageBlockAudio.caption.credit, pageBlockAudio);
            } else if (block instanceof TLRPC.TL_pageBlockTable) {
                TLRPC.TL_pageBlockTable pageBlockTable = (TLRPC.TL_pageBlockTable) block;
                setRichTextParents(null, pageBlockTable.title);
                addTextBlock(pageBlockTable.title, pageBlockTable);
                for (int a = 0, size = pageBlockTable.rows.size(); a < size; a++) {
                    TLRPC.TL_pageTableRow row = pageBlockTable.rows.get(a);
                    for (int b = 0, size2 = row.cells.size(); b < size2; b++) {
                        TLRPC.TL_pageTableCell cell = row.cells.get(b);
                        setRichTextParents(null, cell.text);
                        addTextBlock(cell.text, pageBlockTable);
                    }
                }
            } else if (block instanceof TLRPC.TL_pageBlockTitle) {
                TLRPC.TL_pageBlockTitle pageBlockTitle = (TLRPC.TL_pageBlockTitle) block;
                setRichTextParents(null, pageBlockTitle.text);
                addTextBlock(pageBlockTitle.text, pageBlockTitle);
            } else if (block instanceof TLRPC.TL_pageBlockCover) {
                TLRPC.TL_pageBlockCover pageBlockCover = (TLRPC.TL_pageBlockCover) block;
                setRichTextParents(pageBlockCover.cover);
            } else if (block instanceof TLRPC.TL_pageBlockAuthorDate) {
                TLRPC.TL_pageBlockAuthorDate pageBlockAuthorDate = (TLRPC.TL_pageBlockAuthorDate) block;
                setRichTextParents(null, pageBlockAuthorDate.author);
                addTextBlock(pageBlockAuthorDate.author, pageBlockAuthorDate);
            } else if (block instanceof TLRPC.TL_pageBlockMap) {
                TLRPC.TL_pageBlockMap pageBlockMap = (TLRPC.TL_pageBlockMap) block;
                setRichTextParents(null, pageBlockMap.caption.text);
                setRichTextParents(null, pageBlockMap.caption.credit);
                addTextBlock(pageBlockMap.caption.text, pageBlockMap);
                addTextBlock(pageBlockMap.caption.credit, pageBlockMap);
            } else if (block instanceof TLRPC.TL_pageBlockRelatedArticles) {
                TLRPC.TL_pageBlockRelatedArticles pageBlockRelatedArticles = (TLRPC.TL_pageBlockRelatedArticles) block;
                setRichTextParents(null, pageBlockRelatedArticles.title);
                addTextBlock(pageBlockRelatedArticles.title, pageBlockRelatedArticles);
            }
        }

        private void addBlock(WebpageAdapter adapter, TLRPC.PageBlock block, int level, int listLevel, int position) {
            TLRPC.PageBlock originalBlock = block;
            if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild blockDetailsChild = (TL_pageBlockDetailsChild) block;
                block = blockDetailsChild.block;
            }
            if (!(block instanceof TLRPC.TL_pageBlockList || block instanceof TLRPC.TL_pageBlockOrderedList)) {
                setRichTextParents(block);
                addAllMediaFromBlock(adapter, block);
            }
            block = getLastNonListPageBlock(block);
            if (block instanceof TLRPC.TL_pageBlockUnsupported) {
                return;
            } else if (block instanceof TLRPC.TL_pageBlockAnchor) {
                anchors.put(((TLRPC.TL_pageBlockAnchor) block).name.toLowerCase(), blocks.size());
                return;
            }
            if (!(block instanceof TLRPC.TL_pageBlockList || block instanceof TLRPC.TL_pageBlockOrderedList)) {
                blocks.add(originalBlock);
            }

            if (block instanceof TLRPC.TL_pageBlockAudio) {
                TLRPC.TL_pageBlockAudio blockAudio = (TLRPC.TL_pageBlockAudio) block;
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.out = true;
                message.id = block.mid = -((Long) blockAudio.audio_id).hashCode();
                message.peer_id = new TLRPC.TL_peerUser();
                message.from_id = new TLRPC.TL_peerUser();
                message.from_id.user_id = message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                message.date = (int) (System.currentTimeMillis() / 1000);
                message.message = "";
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.webpage = currentPage;
                message.media.flags |= 3;
                message.media.document = getDocumentWithId(blockAudio.audio_id);
                message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                MessageObject messageObject = new MessageObject(UserConfig.selectedAccount, message, false, true);
                audioMessages.add(messageObject);
                audioBlocks.put(blockAudio, messageObject);

                String author = messageObject.getMusicAuthor(false);
                String title = messageObject.getMusicTitle(false);
                if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
                    SpannableStringBuilder stringBuilder;
                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
                        addTextBlock(String.format("%s - %s", author, title), block);
                    } else if (!TextUtils.isEmpty(title)) {
                        addTextBlock(title, block);
                    } else {
                        addTextBlock(author, block);
                    }
                }

            } else if (block instanceof TLRPC.TL_pageBlockEmbedPost) {
                TLRPC.TL_pageBlockEmbedPost pageBlockEmbedPost = (TLRPC.TL_pageBlockEmbedPost) block;
                if (!pageBlockEmbedPost.blocks.isEmpty()) {
                    block.level = -1;
                    for (int b = 0; b < pageBlockEmbedPost.blocks.size(); b++) {
                        TLRPC.PageBlock innerBlock = pageBlockEmbedPost.blocks.get(b);
                        if (innerBlock instanceof TLRPC.TL_pageBlockUnsupported) {
                            continue;
                        } else if (innerBlock instanceof TLRPC.TL_pageBlockAnchor) {
                            TLRPC.TL_pageBlockAnchor pageBlockAnchor = (TLRPC.TL_pageBlockAnchor) innerBlock;
                            anchors.put(pageBlockAnchor.name.toLowerCase(), blocks.size());
                            continue;
                        }
                        innerBlock.level = 1;
                        if (b == pageBlockEmbedPost.blocks.size() - 1) {
                            innerBlock.bottom = true;
                        }
                        blocks.add(innerBlock);
                        addAllMediaFromBlock(adapter, innerBlock);
                    }
                    if (!TextUtils.isEmpty(getPlainText(pageBlockEmbedPost.caption.text)) || !TextUtils.isEmpty(getPlainText(pageBlockEmbedPost.caption.credit))) {
                        TL_pageBlockEmbedPostCaption pageBlockEmbedPostCaption = new TL_pageBlockEmbedPostCaption();
                        pageBlockEmbedPostCaption.parent = pageBlockEmbedPost;
                        pageBlockEmbedPostCaption.caption = pageBlockEmbedPost.caption;
                        blocks.add(pageBlockEmbedPostCaption);
                    }
                }
            } else if (block instanceof TLRPC.TL_pageBlockRelatedArticles) {
                TLRPC.TL_pageBlockRelatedArticles pageBlockRelatedArticles = (TLRPC.TL_pageBlockRelatedArticles) block;

                TL_pageBlockRelatedArticlesShadow shadow = new TL_pageBlockRelatedArticlesShadow();
                shadow.parent = pageBlockRelatedArticles;
                blocks.add(blocks.size() - 1, shadow);

                for (int b = 0, size = pageBlockRelatedArticles.articles.size(); b < size; b++) {
                    TL_pageBlockRelatedArticlesChild child = new TL_pageBlockRelatedArticlesChild();
                    child.parent = pageBlockRelatedArticles;
                    child.num = b;
                    blocks.add(child);
                }
                if (position == 0) {
                    shadow = new TL_pageBlockRelatedArticlesShadow();
                    shadow.parent = pageBlockRelatedArticles;
                    blocks.add(shadow);
                }
            } else if (block instanceof TLRPC.TL_pageBlockDetails) {
                TLRPC.TL_pageBlockDetails pageBlockDetails = (TLRPC.TL_pageBlockDetails) block;
                for (int b = 0, size = pageBlockDetails.blocks.size(); b < size; b++) {
                    TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                    child.parent = originalBlock;
                    child.block = pageBlockDetails.blocks.get(b);
                    addBlock(adapter, wrapInTableBlock(originalBlock, child), level + 1, listLevel, position);
                }
                /*if (level == 0) {
                    TL_pageBlockDetailsBottom child = new TL_pageBlockDetailsBottom();
                    child.parent = pageBlockDetails;
                    blocks.add(wrapInTableBlock(originalBlock, child));
                } else {
                    TL_pageBlockDetailsBottom bottom = new TL_pageBlockDetailsBottom();
                    bottom.parent = pageBlockDetails;

                    TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                    child.parent = originalBlock;
                    child.block = bottom;
                    blocks.add(wrapInTableBlock(originalBlock, child));
                }*/
            } else if (block instanceof TLRPC.TL_pageBlockList) {
                TLRPC.TL_pageBlockList pageBlockList = (TLRPC.TL_pageBlockList) block;

                TL_pageBlockListParent pageBlockListParent = new TL_pageBlockListParent();
                pageBlockListParent.pageBlockList = pageBlockList;
                pageBlockListParent.level = listLevel;

                for (int b = 0, size = pageBlockList.items.size(); b < size; b++) {
                    TLRPC.PageListItem item = pageBlockList.items.get(b);

                    TL_pageBlockListItem pageBlockListItem = new TL_pageBlockListItem();
                    pageBlockListItem.index = b;
                    pageBlockListItem.parent = pageBlockListParent;
                    if (pageBlockList.ordered) {
                        if (isRtl) {
                            pageBlockListItem.num = String.format(".%d", b + 1);
                        } else {
                            pageBlockListItem.num = String.format("%d.", b + 1);
                        }
                    } else {
                        pageBlockListItem.num = "•";
                    }
                    pageBlockListParent.items.add(pageBlockListItem);

                    if (item instanceof TLRPC.TL_pageListItemText) {
                        pageBlockListItem.textItem = ((TLRPC.TL_pageListItemText) item).text;
                    } else if (item instanceof TLRPC.TL_pageListItemBlocks) {
                        TLRPC.TL_pageListItemBlocks pageListItemBlocks = (TLRPC.TL_pageListItemBlocks) item;
                        if (!pageListItemBlocks.blocks.isEmpty()) {
                            pageBlockListItem.blockItem = pageListItemBlocks.blocks.get(0);
                        } else {
                            TLRPC.TL_pageListItemText text = new TLRPC.TL_pageListItemText();
                            TLRPC.TL_textPlain textPlain = new TLRPC.TL_textPlain();
                            textPlain.text = " ";
                            text.text = textPlain;
                            item = text;
                        }
                    }
                    if (originalBlock instanceof TL_pageBlockDetailsChild) {
                        TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) originalBlock;
                        TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                        child.parent = pageBlockDetailsChild.parent;
                        child.block = pageBlockListItem;
                        addBlock(adapter, child, level, listLevel + 1, position);
                    } else {
                        TLRPC.PageBlock finalBlock;
                        if (b == 0) {
                            finalBlock = fixListBlock(originalBlock, pageBlockListItem);
                        } else {
                            finalBlock = pageBlockListItem;
                        }
                        addBlock(adapter, finalBlock, level, listLevel + 1, position);
                    }

                    if (item instanceof TLRPC.TL_pageListItemBlocks) {
                        TLRPC.TL_pageListItemBlocks pageListItemBlocks = (TLRPC.TL_pageListItemBlocks) item;
                        for (int c = 1, size2 = pageListItemBlocks.blocks.size(); c < size2; c++) {
                            pageBlockListItem = new TL_pageBlockListItem();
                            pageBlockListItem.blockItem = pageListItemBlocks.blocks.get(c);
                            pageBlockListItem.parent = pageBlockListParent;

                            if (originalBlock instanceof TL_pageBlockDetailsChild) {
                                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) originalBlock;
                                TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                                child.parent = pageBlockDetailsChild.parent;
                                child.block = pageBlockListItem;
                                addBlock(adapter, child, level, listLevel + 1, position);
                            } else {
                                addBlock(adapter, pageBlockListItem, level, listLevel + 1, position);
                            }
                            pageBlockListParent.items.add(pageBlockListItem);
                        }
                    }
                }
            } else if (block instanceof TLRPC.TL_pageBlockOrderedList) {
                TLRPC.TL_pageBlockOrderedList pageBlockOrderedList = (TLRPC.TL_pageBlockOrderedList) block;

                TL_pageBlockOrderedListParent pageBlockOrderedListParent = new TL_pageBlockOrderedListParent();
                pageBlockOrderedListParent.pageBlockOrderedList = pageBlockOrderedList;
                pageBlockOrderedListParent.level = listLevel;

                for (int b = 0, size = pageBlockOrderedList.items.size(); b < size; b++) {
                    TLRPC.PageListOrderedItem item = pageBlockOrderedList.items.get(b);

                    TL_pageBlockOrderedListItem pageBlockOrderedListItem = new TL_pageBlockOrderedListItem();
                    pageBlockOrderedListItem.index = b;
                    pageBlockOrderedListItem.parent = pageBlockOrderedListParent;
                    pageBlockOrderedListParent.items.add(pageBlockOrderedListItem);

                    if (item instanceof TLRPC.TL_pageListOrderedItemText) {
                        TLRPC.TL_pageListOrderedItemText pageListOrderedItemText = (TLRPC.TL_pageListOrderedItemText) item;
                        pageBlockOrderedListItem.textItem = pageListOrderedItemText.text;

                        if (TextUtils.isEmpty(pageListOrderedItemText.num)) {
                            if (isRtl) {
                                pageBlockOrderedListItem.num = String.format(".%d", b + 1);
                            } else {
                                pageBlockOrderedListItem.num = String.format("%d.", b + 1);
                            }
                        } else {
                            if (isRtl) {
                                pageBlockOrderedListItem.num = "." + pageListOrderedItemText.num;
                            } else {
                                pageBlockOrderedListItem.num = pageListOrderedItemText.num + ".";
                            }
                        }
                    } else if (item instanceof TLRPC.TL_pageListOrderedItemBlocks) {
                        TLRPC.TL_pageListOrderedItemBlocks pageListOrderedItemBlocks = (TLRPC.TL_pageListOrderedItemBlocks) item;
                        if (!pageListOrderedItemBlocks.blocks.isEmpty()) {
                            pageBlockOrderedListItem.blockItem = pageListOrderedItemBlocks.blocks.get(0);
                        } else {
                            TLRPC.TL_pageListOrderedItemText text = new TLRPC.TL_pageListOrderedItemText();
                            TLRPC.TL_textPlain textPlain = new TLRPC.TL_textPlain();
                            textPlain.text = " ";
                            text.text = textPlain;
                            item = text;
                        }

                        if (TextUtils.isEmpty(pageListOrderedItemBlocks.num)) {
                            if (isRtl) {
                                pageBlockOrderedListItem.num = String.format(".%d", b + 1);
                            } else {
                                pageBlockOrderedListItem.num = String.format("%d.", b + 1);
                            }
                        } else {
                            if (isRtl) {
                                pageBlockOrderedListItem.num = "." + pageListOrderedItemBlocks.num;
                            } else {
                                pageBlockOrderedListItem.num = pageListOrderedItemBlocks.num + ".";
                            }
                        }
                    }
                    if (originalBlock instanceof TL_pageBlockDetailsChild) {
                        TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) originalBlock;
                        TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                        child.parent = pageBlockDetailsChild.parent;
                        child.block = pageBlockOrderedListItem;
                        addBlock(adapter, child, level, listLevel + 1, position);
                    } else {
                        TLRPC.PageBlock finalBlock;
                        if (b == 0) {
                            finalBlock = fixListBlock(originalBlock, pageBlockOrderedListItem);
                        } else {
                            finalBlock = pageBlockOrderedListItem;
                        }
                        addBlock(adapter, finalBlock, level, listLevel + 1, position);
                    }

                    if (item instanceof TLRPC.TL_pageListOrderedItemBlocks) {
                        TLRPC.TL_pageListOrderedItemBlocks pageListOrderedItemBlocks = (TLRPC.TL_pageListOrderedItemBlocks) item;
                        for (int c = 1, size2 = pageListOrderedItemBlocks.blocks.size(); c < size2; c++) {
                            pageBlockOrderedListItem = new TL_pageBlockOrderedListItem();
                            pageBlockOrderedListItem.blockItem = pageListOrderedItemBlocks.blocks.get(c);
                            pageBlockOrderedListItem.parent = pageBlockOrderedListParent;

                            if (originalBlock instanceof TL_pageBlockDetailsChild) {
                                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) originalBlock;
                                TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                                child.parent = pageBlockDetailsChild.parent;
                                child.block = pageBlockOrderedListItem;
                                addBlock(adapter, child, level, listLevel + 1, position);
                            } else {
                                addBlock(adapter, pageBlockOrderedListItem, level, listLevel + 1, position);
                            }
                            pageBlockOrderedListParent.items.add(pageBlockOrderedListItem);
                        }
                    }
                }
            }
        }

        private void addAllMediaFromBlock(WebpageAdapter adapter, TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockPhoto) {
                TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) block;
                TLRPC.Photo photo = getPhotoWithId(pageBlockPhoto.photo_id);
                if (photo != null) {
                    pageBlockPhoto.thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 56, true);
                    pageBlockPhoto.thumbObject = photo;
                    photoBlocks.add(block);
                }
            } else if (block instanceof TLRPC.TL_pageBlockVideo && WebPageUtils.isVideo(adapter.currentPage, block)) {
                TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) block;
                TLRPC.Document document = getDocumentWithId(pageBlockVideo.video_id);
                if (document != null) {
                    pageBlockVideo.thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 56, true);
                    pageBlockVideo.thumbObject = document;
                    photoBlocks.add(block);
                }
            } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                TLRPC.TL_pageBlockSlideshow slideshow = (TLRPC.TL_pageBlockSlideshow) block;
                int count = slideshow.items.size();
                for (int a = 0; a < count; a++) {
                    TLRPC.PageBlock innerBlock = slideshow.items.get(a);
                    innerBlock.groupId = lastBlockNum;
                    addAllMediaFromBlock(adapter, innerBlock);
                }
                lastBlockNum++;
            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                TLRPC.TL_pageBlockCollage collage = (TLRPC.TL_pageBlockCollage) block;
                int count = collage.items.size();
                for (int a = 0; a < count; a++) {
                    TLRPC.PageBlock innerBlock = collage.items.get(a);
                    innerBlock.groupId = lastBlockNum;
                    addAllMediaFromBlock(adapter, innerBlock);
                }
                lastBlockNum++;
            } else if (block instanceof TLRPC.TL_pageBlockCover) {
                TLRPC.TL_pageBlockCover pageBlockCover = (TLRPC.TL_pageBlockCover) block;
                addAllMediaFromBlock(adapter, pageBlockCover.cover);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new BlockParagraphCell(context, this);
                    break;
                }
                case 1: {
                    view = new BlockHeaderCell(context, this);
                    break;
                }
                case 2: {
                    view = new BlockDividerCell(context);
                    break;
                }
                case 3: {
                    view = new BlockEmbedCell(context, this);
                    break;
                }
                case 4: {
                    view = new BlockSubtitleCell(context, this);
                    break;
                }
                case 5: {
                    view = new BlockVideoCell(context, this, 0);
                    break;
                }
                case 6: {
                    view = new BlockPullquoteCell(context, this);
                    break;
                }
                case 7: {
                    view = new BlockBlockquoteCell(context, this);
                    break;
                }
                case 8: {
                    view = new BlockSlideshowCell(context, this);
                    break;
                }
                case 9: {
                    view = new BlockPhotoCell(context, this, 0);
                    break;
                }
                case 10: {
                    view = new BlockAuthorDateCell(context, this);
                    break;
                }
                case 11: {
                    view = new BlockTitleCell(context, this);
                    break;
                }
                case 12: {
                    view = new BlockListItemCell(context, this);
                    break;
                }
                case 13: {
                    view = new BlockFooterCell(context, this);
                    break;
                }
                case 14: {
                    view = new BlockPreformattedCell(context, this);
                    break;
                }
                case 15: {
                    view = new BlockSubheaderCell(context, this);
                    break;
                }
                case 16: {
                    view = new BlockEmbedPostCell(context, this);
                    break;
                }
                case 17: {
                    view = new BlockCollageCell(context, this);
                    break;
                }
                case 18: {
                    view = new BlockChannelCell(context, this, 0);
                    break;
                }
                case 19: {
                    view = new BlockAudioCell(context, this);
                    break;
                }
                case 20: {
                    view = new BlockKickerCell(context, this);
                    break;
                }
                case 21: {
                    view = new BlockOrderedListItemCell(context, this);
                    break;
                }
                case 22: {
                    view = new BlockMapCell(context, this, 0);
                    break;
                }
                case 23: {
                    view = new BlockRelatedArticlesCell(context, this);
                    break;
                }
                case 24: {
                    view = new BlockDetailsCell(context, this);
                    break;
                }
                case 25: {
                    view = new BlockTableCell(context, this);
                    break;
                }
                case 26: {
                    view = new BlockRelatedArticlesHeaderCell(context, this);
                    break;
                }
                case 27: {
                    view = new BlockDetailsBottomCell(context);
                    break;
                }
                case 28: {
                    view = new BlockRelatedArticlesShadowCell(context);
                    break;
                }
                case 90: {
                    view = new ReportCell(context);
                    break;
                }
                case 100:
                default: {
                    TextView textView = new TextView(context);
                    textView.setBackgroundColor(0xffff0000);
                    textView.setTextColor(0xff000000);
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    view = textView;
                    break;
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            view.setFocusable(true);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 23 || type == 24) {
                return true;
            }
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < localBlocks.size()) {
                TLRPC.PageBlock block = localBlocks.get(position);
                bindBlockToHolder(holder.getItemViewType(), holder, block, position, localBlocks.size());
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 90) {
                ReportCell cell = (ReportCell) holder.itemView;
                cell.setViews(currentPage.cached_page != null ? currentPage.cached_page.views : 0);
            }
        }

        private void bindBlockToHolder(int type, RecyclerView.ViewHolder holder, TLRPC.PageBlock block, int position, int total) {
            TLRPC.PageBlock originalBlock = block;
            if (block instanceof TLRPC.TL_pageBlockCover) {
                block = ((TLRPC.TL_pageBlockCover) block).cover;
            } else if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                block = pageBlockDetailsChild.block;
            }
            switch (type) {
                case 0: {
                    BlockParagraphCell cell = (BlockParagraphCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockParagraph) block);
                    break;
                }
                case 1: {
                    BlockHeaderCell cell = (BlockHeaderCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockHeader) block);
                    break;
                }
                case 2: {
                    BlockDividerCell cell = (BlockDividerCell) holder.itemView;
                    break;
                }
                case 3: {
                    BlockEmbedCell cell = (BlockEmbedCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockEmbed) block);
                    break;
                }
                case 4: {
                    BlockSubtitleCell cell = (BlockSubtitleCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockSubtitle) block);
                    break;
                }
                case 5: {
                    BlockVideoCell cell = (BlockVideoCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockVideo) block, position == 0, position == total - 1);
                    cell.setParentBlock(channelBlock, originalBlock);
                    break;
                }
                case 6: {
                    BlockPullquoteCell cell = (BlockPullquoteCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockPullquote) block);
                    break;
                }
                case 7: {
                    BlockBlockquoteCell cell = (BlockBlockquoteCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockBlockquote) block);
                    break;
                }
                case 8: {
                    BlockSlideshowCell cell = (BlockSlideshowCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockSlideshow) block);
                    break;
                }
                case 9: {
                    BlockPhotoCell cell = (BlockPhotoCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockPhoto) block, position == 0, position == total - 1);
                    cell.setParentBlock(originalBlock);
                    break;
                }
                case 10: {
                    BlockAuthorDateCell cell = (BlockAuthorDateCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockAuthorDate) block);
                    break;
                }
                case 11: {
                    BlockTitleCell cell = (BlockTitleCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockTitle) block);
                    break;
                }
                case 12: {
                    BlockListItemCell cell = (BlockListItemCell) holder.itemView;
                    cell.setBlock((TL_pageBlockListItem) block);
                    break;
                }
                case 13: {
                    BlockFooterCell cell = (BlockFooterCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockFooter) block);
                    break;
                }
                case 14: {
                    BlockPreformattedCell cell = (BlockPreformattedCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockPreformatted) block);
                    break;
                }
                case 15: {
                    BlockSubheaderCell cell = (BlockSubheaderCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockSubheader) block);
                    break;
                }
                case 16: {
                    BlockEmbedPostCell cell = (BlockEmbedPostCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockEmbedPost) block);
                    break;
                }
                case 17: {
                    BlockCollageCell cell = (BlockCollageCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockCollage) block);
                    break;
                }
                case 18: {
                    BlockChannelCell cell = (BlockChannelCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockChannel) block);
                    break;
                }
                case 19: {
                    BlockAudioCell cell = (BlockAudioCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockAudio) block, position == 0, position == total - 1);
                    break;
                }
                case 20: {
                    BlockKickerCell cell = (BlockKickerCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockKicker) block);
                    break;
                }
                case 21: {
                    BlockOrderedListItemCell cell = (BlockOrderedListItemCell) holder.itemView;
                    cell.setBlock((TL_pageBlockOrderedListItem) block);
                    break;
                }
                case 22: {
                    BlockMapCell cell = (BlockMapCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockMap) block, position == 0, position == total - 1);
                    break;
                }
                case 23: {
                    BlockRelatedArticlesCell cell = (BlockRelatedArticlesCell) holder.itemView;
                    cell.setBlock((TL_pageBlockRelatedArticlesChild) block);
                    break;
                }
                case 24: {
                    BlockDetailsCell cell = (BlockDetailsCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockDetails) block);
                    break;
                }
                case 25: {
                    BlockTableCell cell = (BlockTableCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockTable) block);
                    break;
                }
                case 26: {
                    BlockRelatedArticlesHeaderCell cell = (BlockRelatedArticlesHeaderCell) holder.itemView;
                    cell.setBlock((TLRPC.TL_pageBlockRelatedArticles) block);
                    break;
                }
                case 27: {
                    BlockDetailsBottomCell cell = (BlockDetailsBottomCell) holder.itemView;
                    break;
                }
                case 100: {
                    TextView textView = (TextView) holder.itemView;
                    textView.setText("unsupported block " + block);
                    break;
                }
            }
        }

        private int getTypeForBlock(TLRPC.PageBlock block) {
            if (block instanceof TLRPC.TL_pageBlockParagraph) {
                return 0;
            } else if (block instanceof TLRPC.TL_pageBlockHeader) {
                return 1;
            } else if (block instanceof TLRPC.TL_pageBlockDivider) {
                return 2;
            } else if (block instanceof TLRPC.TL_pageBlockEmbed) {
                return 3;
            } else if (block instanceof TLRPC.TL_pageBlockSubtitle) {
                return 4;
            } else if (block instanceof TLRPC.TL_pageBlockVideo) {
                return 5;
            } else if (block instanceof TLRPC.TL_pageBlockPullquote) {
                return 6;
            } else if (block instanceof TLRPC.TL_pageBlockBlockquote) {
                return 7;
            } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                return 8;
            } else if (block instanceof TLRPC.TL_pageBlockPhoto) {
                return 9;
            } else if (block instanceof TLRPC.TL_pageBlockAuthorDate) {
                return 10;
            } else if (block instanceof TLRPC.TL_pageBlockTitle) {
                return 11;
            } else if (block instanceof TL_pageBlockListItem) {
                return 12;
            } else if (block instanceof TLRPC.TL_pageBlockFooter) {
                return 13;
            } else if (block instanceof TLRPC.TL_pageBlockPreformatted) {
                return 14;
            } else if (block instanceof TLRPC.TL_pageBlockSubheader) {
                return 15;
            } else if (block instanceof TLRPC.TL_pageBlockEmbedPost) {
                return 16;
            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                return 17;
            } else if (block instanceof TLRPC.TL_pageBlockChannel) {
                return 18;
            } else if (block instanceof TLRPC.TL_pageBlockAudio) {
                return 19;
            } else if (block instanceof TLRPC.TL_pageBlockKicker) {
                return 20;
            } else if (block instanceof TL_pageBlockOrderedListItem) {
                return 21;
            } else if (block instanceof TLRPC.TL_pageBlockMap) {
                return 22;
            } else if (block instanceof TL_pageBlockRelatedArticlesChild) {
                return 23;
            } else if (block instanceof TLRPC.TL_pageBlockDetails) {
                return 24;
            } else if (block instanceof TLRPC.TL_pageBlockTable) {
                return 25;
            } else if (block instanceof TLRPC.TL_pageBlockRelatedArticles) {
                return 26;
            } else if (block instanceof TL_pageBlockDetailsBottom) {
                return 27;
            } else if (block instanceof TL_pageBlockRelatedArticlesShadow) {
                return 28;
            } else if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                return getTypeForBlock(pageBlockDetailsChild.block);
            } else if (block instanceof TLRPC.TL_pageBlockCover) {
                TLRPC.TL_pageBlockCover pageBlockCover = (TLRPC.TL_pageBlockCover) block;
                return getTypeForBlock(pageBlockCover.cover);
            } else {
                return 100;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == localBlocks.size()) {
                return 90;
            }
            return getTypeForBlock(localBlocks.get(position));
        }

        public TLRPC.PageBlock getItem(int position) {
            return localBlocks.get(position);
        }

        @Override
        public int getItemCount() {
            return currentPage != null && currentPage.cached_page != null ? localBlocks.size() + 1 : 0;
        }

        private boolean isBlockOpened(TL_pageBlockDetailsChild child) {
            TLRPC.PageBlock parentBlock = getLastNonListPageBlock(child.parent);
            if (parentBlock instanceof TLRPC.TL_pageBlockDetails) {
                return ((TLRPC.TL_pageBlockDetails) parentBlock).open;
            } else if (parentBlock instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild parent = (TL_pageBlockDetailsChild) parentBlock;
                parentBlock = getLastNonListPageBlock(parent.block);
                if (parentBlock instanceof TLRPC.TL_pageBlockDetails && !((TLRPC.TL_pageBlockDetails) parentBlock).open) {
                    return false;
                }
                return isBlockOpened(parent);
            }
            return false;
        }

        private void updateRows() {
            localBlocks.clear();
            for (int a = 0, size = blocks.size(); a < size; a++) {
                TLRPC.PageBlock originalBlock = blocks.get(a);
                TLRPC.PageBlock block = getLastNonListPageBlock(originalBlock);
                if (block instanceof TL_pageBlockDetailsChild) {
                    TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                    if (!isBlockOpened(pageBlockDetailsChild)) {
                        continue;
                    }
                }
                localBlocks.add(originalBlock);
            }
        }

        private void cleanup() {
            currentPage = null;
            blocks.clear();
            photoBlocks.clear();
            audioBlocks.clear();
            audioMessages.clear();
            anchors.clear();
            anchorsParent.clear();
            anchorsOffset.clear();
            textBlocks.clear();
            textToBlocks.clear();
            channelBlock = null;
            notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            super.notifyDataSetChanged();
        }

        @Override
        public void notifyItemChanged(int position) {
            updateRows();
            super.notifyItemChanged(position);
        }

        @Override
        public void notifyItemChanged(int position, Object payload) {
            updateRows();
            super.notifyItemChanged(position, payload);
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount, Object payload) {
            updateRows();
            super.notifyItemRangeChanged(positionStart, itemCount, payload);
        }

        @Override
        public void notifyItemInserted(int position) {
            updateRows();
            super.notifyItemInserted(position);
        }

        @Override
        public void notifyItemMoved(int fromPosition, int toPosition) {
            updateRows();
            super.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void notifyItemRemoved(int position) {
            updateRows();
            super.notifyItemRemoved(position);
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeRemoved(positionStart, itemCount);
        }
    }

    private class BlockVideoCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private ImageReceiver imageView;
        private RadialProgress2 radialProgress;
        private BlockChannelCell channelCell;
        private int currentType;
        private boolean isFirst;
        private int textX;
        private int textY;
        private int creditOffset;

        private int buttonX;
        private int buttonY;
        private boolean photoPressed;
        private int buttonState;
        private int buttonPressed;

        private int TAG;

        private TLRPC.TL_pageBlockVideo currentBlock;
        private TLRPC.PageBlock parentBlock;
        private TLRPC.Document currentDocument;
        private boolean isGif;

        private boolean autoDownload;

        private boolean cancelLoading;

        private MessageObject.GroupedMessagePosition groupPosition;

        private WebpageAdapter parentAdapter;

        public BlockVideoCell(Context context, WebpageAdapter adapter, int type) {
            super(context);
            parentAdapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            imageView.setNeedsQualityThumb(true);
            imageView.setShouldGenerateQualityThumb(true);
            currentType = type;
            radialProgress = new RadialProgress2(this);
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
            channelCell = new BlockChannelCell(context, parentAdapter, 1);
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        public void setBlock(TLRPC.TL_pageBlockVideo block, boolean first, boolean last) {
            currentBlock = block;
            parentBlock = null;
            currentDocument = parentAdapter.getDocumentWithId(currentBlock.video_id);
            isGif = MessageObject.isGifDocument(currentDocument)/* && currentBlock.autoplay*/;
            isFirst = first;
            channelCell.setVisibility(INVISIBLE);
            updateButtonState(false);
            requestLayout();
        }

        public void setParentBlock(TLRPC.TL_pageBlockChannel channelBlock, TLRPC.PageBlock block) {
            parentBlock = block;
            if (channelBlock != null && parentBlock instanceof TLRPC.TL_pageBlockCover) {
                channelCell.setBlock(channelBlock);
                channelCell.setVisibility(VISIBLE);
            }
        }

        public View getChannelCell() {
            return channelCell;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (pinchToZoomHelper.checkPinchToZoom(event, this, imageView, null)) {
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + AndroidUtilities.dp(39)) {
                if (parentAdapter.channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    MessagesController.getInstance(currentAccount).openByUserName(parentAdapter.channelBlock.channel.username, parentFragment, 2);
                    close(false, true);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48) || buttonState == 0) {
                    buttonPressed = 1;
                    invalidate();
                } else {
                    photoPressed = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (photoPressed) {
                    photoPressed = false;
                    openPhoto(currentBlock, parentAdapter);
                } else if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentType == 1) {
                width = ((View) getParent()).getMeasuredWidth();
                height = ((View) getParent()).getMeasuredHeight();
            } else if (currentType == 2) {
                height = (int) Math.ceil(groupPosition.ph * Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
            }

            if (currentBlock != null) {
                int photoWidth = width;
                int photoHeight = height;
                int photoX;
                int textWidth;
                if (currentType == 0 && currentBlock.level > 0) {
                    textX = photoX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                    photoWidth -= photoX + AndroidUtilities.dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                }
                if (currentDocument != null) {
                    int size = AndroidUtilities.dp(48);
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(currentDocument.thumbs, 48);
                    if (currentType == 0) {
                        float scale;
                        boolean found = false;
                        for (int a = 0, count = currentDocument.attributes.size(); a < count; a++) {
                            TLRPC.DocumentAttribute attribute = currentDocument.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                scale = photoWidth / (float) attribute.w;
                                height = (int) (scale * attribute.h);
                                found = true;
                                break;
                            }
                        }
                        float w = thumb != null ? thumb.w : 100.0f;
                        float h = thumb != null ? thumb.h : 100.0f;
                        if (!found) {
                            scale = photoWidth / w;
                            height = (int) (scale * h);
                        }
                        if (parentBlock instanceof TLRPC.TL_pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / h;
                                photoWidth = (int) (scale * w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                        if (height == 0) {
                            height = AndroidUtilities.dp(100);
                        } else if (height < size) {
                            height = size;
                        }
                        photoHeight = height;
                    } else if (currentType == 2) {
                        if ((groupPosition.flags & POSITION_FLAG_RIGHT) == 0) {
                            photoWidth -= AndroidUtilities.dp(2);
                        }
                        if ((groupPosition.flags & POSITION_FLAG_BOTTOM) == 0) {
                            photoHeight -= AndroidUtilities.dp(2);
                        }
                    }
                    imageView.setQualityThumbDocument(currentDocument);
                    imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : AndroidUtilities.dp(8), photoWidth, photoHeight);

                    if (isGif) {
                        autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, currentDocument.size);
                        File path = FileLoader.getPathToAttach(currentDocument, true);
                        if (autoDownload || path.exists()) {
                            imageView.setStrippedLocation(null);
                            imageView.setImage(ImageLocation.getForDocument(currentDocument), null, null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument.size, null, parentAdapter.currentPage, 1);
                        } else {
                            imageView.setStrippedLocation(ImageLocation.getForDocument(currentDocument));
                            imageView.setImage(null, null, null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument.size, null, parentAdapter.currentPage, 1);
                        }
                    } else {
                        imageView.setStrippedLocation(null);
                        imageView.setImage(null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", 0, null, parentAdapter.currentPage, 1);
                    }
                    imageView.setAspectFit(true);
                    buttonX = (int) (imageView.getImageX() + (imageView.getImageWidth() - size) / 2.0f);
                    buttonY = (int) (imageView.getImageY() + (imageView.getImageHeight() - size) / 2.0f);
                    radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + AndroidUtilities.dp(8));
                if (currentType == 0) {
                    captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                    if (captionLayout != null) {
                        creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                        height += creditOffset + AndroidUtilities.dp(4);
                        captionLayout.x = textX;
                        captionLayout.y = textY;
                    }
                    creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                    if (creditLayout != null) {
                        height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                        creditLayout.x = textX;
                        creditLayout.y = textY + creditOffset;
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }
                boolean nextIsChannel = parentBlock instanceof TLRPC.TL_pageBlockCover && parentAdapter.blocks.size() > 1 && parentAdapter.blocks.get(1) instanceof TLRPC.TL_pageBlockChannel;
                if (currentType != 2 && !nextIsChannel) {
                    height += AndroidUtilities.dp(8);
                }
            } else {
                height = 1;
            }
            channelCell.measure(widthMeasureSpec, heightMeasureSpec);
            channelCell.setTranslationY(imageView.getImageHeight() - AndroidUtilities.dp(39));

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (!imageView.hasBitmapImage() || imageView.getCurrentAlpha() != 1.0f) {
                canvas.drawRect(imageView.getDrawRegion(), photoBackgroundPaint);
            }
            if (!pinchToZoomHelper.isInOverlayModeFor(this)) {
                imageView.draw(canvas);
                if (imageView.getVisible()) {
                    radialProgress.draw(canvas);
                }
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        private int getIconForCurrentState() {
            if (buttonState == 0) {
                return MediaActionDrawable.ICON_DOWNLOAD;
            } else if (buttonState == 1) {
                return MediaActionDrawable.ICON_CANCEL;
            } else if (buttonState == 2) {
                return MediaActionDrawable.ICON_GIF;
            } else if (buttonState == 3) {
                return MediaActionDrawable.ICON_PLAY;
            }
            return MediaActionDrawable.ICON_NONE;
        }

        public void updateButtonState(boolean animated) {
            String fileName = FileLoader.getAttachFileName(currentDocument);
            File path = FileLoader.getPathToAttach(currentDocument, true);
            boolean fileExists = path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                return;
            }
            if (fileExists) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                if (!isGif) {
                    buttonState = 3;
                } else {
                    buttonState = -1;
                }
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                float setProgress = 0;
                boolean progressVisible = false;
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    if (!cancelLoading && autoDownload && isGif) {
                        progressVisible = true;
                        buttonState = 1;
                    } else {
                        buttonState = 0;
                    }
                } else {
                    progressVisible = true;
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                }
                radialProgress.setIcon(getIconForCurrentState(), progressVisible, animated);
                radialProgress.setProgress(setProgress, false);
            }
            invalidate();
        }

        private void didPressedButton(boolean animated) {
            if (buttonState == 0) {
                cancelLoading = false;
                radialProgress.setProgress(0, false);
                if (isGif) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(currentDocument.thumbs, 40);
                    imageView.setImage(ImageLocation.getForDocument(currentDocument), null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", currentDocument.size, null, parentAdapter.currentPage, 1);
                } else {
                    FileLoader.getInstance(currentAccount).loadFile(currentDocument, parentAdapter.currentPage, 1, 1);
                }
                buttonState = 1;
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                invalidate();
            } else if (buttonState == 1) {
                cancelLoading = true;
                if (isGif) {
                    imageView.cancelLoadImage();
                } else {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(currentDocument);
                }
                buttonState = 0;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                invalidate();
            } else if (buttonState == 2) {
                imageView.setAllowStartAnimation(true);
                imageView.startAnimation();
                buttonState = -1;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else if (buttonState == 3) {
                openPhoto(currentBlock, parentAdapter);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageView.onDetachedFromWindow();
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageView.onAttachedToWindow();
            updateButtonState(false);
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(false);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            if (isGif) {
                buttonState = 2;
                didPressedButton(true);
            } else {
                updateButtonState(true);
            }
        }

        @Override
        public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            radialProgress.setProgress(Math.min(1f, downloadSize / (float) totalSize), true);
            if (buttonState != 1) {
                updateButtonState(true);
            }
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(LocaleController.getString("AttachVideo", R.string.AttachVideo));
            if (captionLayout != null) {
                sb.append(", ");
                sb.append(captionLayout.getText());
            }
            info.setText(sb.toString());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private class BlockAudioCell extends View implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private RadialProgress2 radialProgress;
        private SeekBar seekBar;
        private boolean isFirst;
        private int textX;
        private int textY = AndroidUtilities.dp(58);
        private int creditOffset;

        private String lastTimeString;

        private DrawingText titleLayout;
        private StaticLayout durationLayout;

        private int seekBarX;
        private int seekBarY;

        private int buttonX;
        private int buttonY;
        private int buttonState;
        private int buttonPressed;

        private int TAG;

        private TLRPC.TL_pageBlockAudio currentBlock;
        private TLRPC.Document currentDocument;
        private MessageObject currentMessageObject;

        private WebpageAdapter parentAdapter;

        public BlockAudioCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            radialProgress = new RadialProgress2(this);
            radialProgress.setCircleRadius(AndroidUtilities.dp(24));
            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

            seekBar = new SeekBar(this);

            seekBar.setDelegate(progress -> {
                if (currentMessageObject == null) {
                    return;
                }
                currentMessageObject.audioProgress = progress;
                MediaController.getInstance().seekToProgress(currentMessageObject, progress);
            });
        }

        public void setBlock(TLRPC.TL_pageBlockAudio block, boolean first, boolean last) {
            currentBlock = block;

            currentMessageObject = parentAdapter.audioBlocks.get(currentBlock);
            if (currentMessageObject != null) {
                currentDocument = currentMessageObject.getDocument();
            }

            isFirst = first;

            seekBar.setColors(Theme.getColor(Theme.key_chat_inAudioSeekbar), Theme.getColor(Theme.key_chat_inAudioCacheSeekbar), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarSelected));

            updateButtonState(false);
            requestLayout();
        }

        public MessageObject getMessageObject() {
            return currentMessageObject;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            boolean result = seekBar.onTouch(event.getAction(), event.getX() - seekBarX, event.getY() - seekBarY);
            if (result) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48) || buttonState == 0) {
                    buttonPressed = 1;
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                buttonPressed = 0;
            }
            return buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint({"DrawAllocation", "NewApi"})
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(54);

            if (currentBlock != null) {
                int textWidth;
                if (currentBlock.level > 0) {
                    textX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                } else {
                    textX = AndroidUtilities.dp(18);
                }
                textWidth = width - textX - AndroidUtilities.dp(18);
                int size = AndroidUtilities.dp(44);
                buttonX = AndroidUtilities.dp(16);
                buttonY = AndroidUtilities.dp(5);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);

                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = AndroidUtilities.dp(8) + captionLayout.getHeight();
                    height += creditOffset + AndroidUtilities.dp(8);
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                }

                if (!isFirst && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }

                String author = currentMessageObject.getMusicAuthor(false);
                String title = currentMessageObject.getMusicTitle(false);
                seekBarX = buttonX + AndroidUtilities.dp(50) + size;
                int w = width - seekBarX - AndroidUtilities.dp(18);
                if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
                    SpannableStringBuilder stringBuilder;
                    if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
                        stringBuilder = new SpannableStringBuilder(String.format("%s - %s", author, title));
                    } else if (!TextUtils.isEmpty(title)) {
                        stringBuilder = new SpannableStringBuilder(title);
                    } else {
                        stringBuilder = new SpannableStringBuilder(author);
                    }
                    if (!TextUtils.isEmpty(author)) {
                        TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        stringBuilder.setSpan(span, 0, author.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                    CharSequence stringFinal = TextUtils.ellipsize(stringBuilder, Theme.chat_audioTitlePaint, w, TextUtils.TruncateAt.END);
                    titleLayout = new DrawingText();
                    titleLayout.textLayout = new StaticLayout(stringFinal, audioTimePaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    titleLayout.parentBlock = currentBlock;
                    seekBarY = buttonY + (size - AndroidUtilities.dp(30)) / 2 + AndroidUtilities.dp(11);
                } else {
                    titleLayout = null;
                    seekBarY = buttonY + (size - AndroidUtilities.dp(30)) / 2;
                }
                seekBar.setSize(w, AndroidUtilities.dp(30));
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
            updatePlayingMessageProgress();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
            radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_inFileProgress));
            radialProgress.draw(canvas);
            canvas.save();
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
            canvas.restore();
            int count = 0;
            if (durationLayout != null) {
                canvas.save();
                canvas.translate(buttonX + AndroidUtilities.dp(54), seekBarY + AndroidUtilities.dp(6));
                durationLayout.draw(canvas);
                canvas.restore();
            }
            if (titleLayout != null) {
                canvas.save();
                titleLayout.x = buttonX + AndroidUtilities.dp(54);
                titleLayout.y = seekBarY - AndroidUtilities.dp(16);
                canvas.translate(titleLayout.x, titleLayout.y);
                drawTextSelection(canvas, this, count++);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (captionLayout != null) {
                canvas.save();
                captionLayout.x = textX;
                captionLayout.y = textY;
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                creditLayout.x = textX;
                creditLayout.y = textY + creditOffset;
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        private int getIconForCurrentState() {
            if (buttonState == 1) {
                return MediaActionDrawable.ICON_PAUSE;
            } else if (buttonState == 2) {
                return MediaActionDrawable.ICON_DOWNLOAD;
            } else if (buttonState == 3) {
                return MediaActionDrawable.ICON_CANCEL;
            }
            return MediaActionDrawable.ICON_PLAY;
        }

        public void updatePlayingMessageProgress() {
            if (currentDocument == null || currentMessageObject == null) {
                return;
            }

            if (!seekBar.isDragging()) {
                seekBar.setProgress(currentMessageObject.audioProgress);
            }

            int duration = 0;

            if (MediaController.getInstance().isPlayingMessage(currentMessageObject)) {
                duration = currentMessageObject.audioProgressSec;
            } else {
                for (int a = 0; a < currentDocument.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = currentDocument.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        duration = attribute.duration;
                        break;
                    }
                }
            }
            String timeString = AndroidUtilities.formatShortDuration(duration);
            if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
                lastTimeString = timeString;
                audioTimePaint.setTextSize(AndroidUtilities.dp(16));
                int timeWidth = (int) Math.ceil(audioTimePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            audioTimePaint.setColor(getTextColor());
            invalidate();
        }

        public void updateButtonState(boolean animated) {
            String fileName = FileLoader.getAttachFileName(currentDocument);
            File path = FileLoader.getPathToAttach(currentDocument, true);
            boolean fileExists = path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                return;
            }
            if (fileExists) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    buttonState = 2;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                } else {
                    buttonState = 3;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                    radialProgress.setIcon(getIconForCurrentState(), true, animated);
                }
            }
            updatePlayingMessageProgress();
        }

        private void didPressedButton(boolean animated) {
            if (buttonState == 0) {
                if (MediaController.getInstance().setPlaylist(parentAdapter.audioMessages, currentMessageObject, 0, false, null)) {
                    buttonState = 1;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    invalidate();
                }
            } else if (buttonState == 1) {
                boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setIcon(getIconForCurrentState(), false, animated);
                    invalidate();
                }
            } else if (buttonState == 2) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance(currentAccount).loadFile(currentDocument, parentAdapter.currentPage, 1, 1);
                buttonState = 3;
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                invalidate();
            } else if (buttonState == 3) {
                FileLoader.getInstance(currentAccount).cancelLoadFile(currentDocument);
                buttonState = 2;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                invalidate();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateButtonState(false);
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(true);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            updateButtonState(true);
        }

        @Override
        public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            radialProgress.setProgress(Math.min(1f, downloadSize / (float) totalSize), true);
            if (buttonState != 3) {
                updateButtonState(true);
            }
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (titleLayout != null) {
                blocks.add(titleLayout);
            }
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private class BlockEmbedPostCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private ImageReceiver avatarImageView;
        private AvatarDrawable avatarDrawable;
        private DrawingText dateLayout;
        private DrawingText nameLayout;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private boolean avatarVisible;
        private int nameX;
        private int dateX;

        private int textX;
        private int textY;
        private int creditOffset;

        private int lineHeight;

        private TLRPC.TL_pageBlockEmbedPost currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockEmbedPostCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            avatarImageView = new ImageReceiver(this);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            avatarImageView.setImageCoords(AndroidUtilities.dp(18 + 14), AndroidUtilities.dp(8), AndroidUtilities.dp(40), AndroidUtilities.dp(40));

            avatarDrawable = new AvatarDrawable();
        }

        public void setBlock(TLRPC.TL_pageBlockEmbedPost block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                if (currentBlock instanceof TL_pageBlockEmbedPostCaption) {
                    height = 0;
                    textX = AndroidUtilities.dp(18);
                    textY = AndroidUtilities.dp(4);
                    int textWidth = width - AndroidUtilities.dp(36 + 14);
                    captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                    if (captionLayout != null) {
                        creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                        height += creditOffset + AndroidUtilities.dp(4);
                    }
                    creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                    if (creditLayout != null) {
                        height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                    }
                } else {
                    if (avatarVisible = (currentBlock.author_photo_id != 0)) {
                        TLRPC.Photo photo = parentAdapter.getPhotoWithId(currentBlock.author_photo_id);
                        if (avatarVisible = (photo instanceof TLRPC.TL_photo)) {
                            avatarDrawable.setInfo(0, currentBlock.author, null);
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.dp(40), true);
                            avatarImageView.setImage(ImageLocation.getForPhoto(image, photo), "40_40", avatarDrawable, 0, null, parentAdapter.currentPage, 1);
                        }
                    }
                    nameLayout = createLayoutForText(this, currentBlock.author, null, width - AndroidUtilities.dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, parentAdapter);
                    if (nameLayout != null){
                        nameLayout.x = AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0));
                        nameLayout.y = AndroidUtilities.dp(dateLayout != null ? 10 : 19);
                    }

                    if (currentBlock.date != 0) {
                        dateLayout = createLayoutForText(this, LocaleController.getInstance().chatFullDate.format((long) currentBlock.date * 1000), null, width - AndroidUtilities.dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), AndroidUtilities.dp(29), currentBlock, parentAdapter);
                    } else {
                        dateLayout = null;
                    }

                    height = AndroidUtilities.dp(40 + 8 + 8);

                    if (currentBlock.blocks.isEmpty()) {
                        textX = AndroidUtilities.dp(18 + 14);
                        textY = AndroidUtilities.dp(40 + 8 + 8);
                        int textWidth = width - AndroidUtilities.dp(36 + 14);
                        captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                        if (captionLayout != null) {
                            creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                            height += creditOffset + AndroidUtilities.dp(4);
                        }
                        creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                        if (creditLayout != null) {
                            height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                        }
                    } else {
                        captionLayout = null;
                        creditLayout = null;
                    }

                    if (dateLayout != null) {
                        dateLayout.x = AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0));
                        dateLayout.y = AndroidUtilities.dp(29);
                    }

                    if (captionLayout != null) {
                        captionLayout.x = textX;
                        captionLayout.y = textY;
                    }
                    if (creditLayout != null) {
                        creditLayout.x = textX;
                        creditLayout.y = textY;
                    }
                }
                lineHeight = height;
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int count = 0;
            if (!(currentBlock instanceof TL_pageBlockEmbedPostCaption)) {
                if (avatarVisible) {
                    avatarImageView.draw(canvas);
                }
                if (nameLayout != null) {
                    canvas.save();
                    canvas.translate(AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), AndroidUtilities.dp(dateLayout != null ? 10 : 19));
                    drawTextSelection(canvas, this, count++);
                    nameLayout.draw(canvas);
                    canvas.restore();
                }
                if (dateLayout != null) {
                    canvas.save();
                    canvas.translate(AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), AndroidUtilities.dp(29));
                    drawTextSelection(canvas, this, count++);
                    dateLayout.draw(canvas);
                    canvas.restore();
                }
                canvas.drawRect(AndroidUtilities.dp(18), AndroidUtilities.dp(6), AndroidUtilities.dp(20), lineHeight - (currentBlock.level != 0 ? 0 : AndroidUtilities.dp(6)), quoteLinePaint);
            }
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (nameLayout != null) {
                blocks.add(nameLayout);
            }
            if (dateLayout != null) {
                blocks.add(dateLayout);
            }
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    public class BlockParagraphCell extends View implements TextSelectionHelper.ArticleSelectableView {

        public DrawingText textLayout;
        public int textX;
        public int textY;

        private TLRPC.TL_pageBlockParagraph currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockParagraphCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockParagraph block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.level == 0) {
                    textY = AndroidUtilities.dp(8);
                    textX = AndroidUtilities.dp(18);
                } else {
                    textY = 0;
                    textX = AndroidUtilities.dp(18 + 14 * currentBlock.level);
                }
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(18) - textX, textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter);
                if (textLayout != null) {
                    height = textLayout.getHeight();
                    if (currentBlock.level > 0) {
                        height += AndroidUtilities.dp(8);
                    } else {
                        height += AndroidUtilities.dp(8 + 8);
                    }
                    textLayout.x = textX;
                    textLayout.y = textY;
                }

            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(textLayout.getText());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockEmbedCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private class TelegramWebviewProxy {
            @JavascriptInterface
            public void postEvent(final String eventName, final String eventData) {
                AndroidUtilities.runOnUIThread(() -> {
                    if ("resize_frame".equals(eventName)) {
                        try {
                            JSONObject object = new JSONObject(eventData);
                            exactWebViewHeight = Utilities.parseInt(object.getString("height"));
                            requestLayout();
                        } catch (Throwable ignore) {

                        }
                    }
                });
            }
        }

        private TouchyWebView webView;
        private WebPlayerView videoView;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int textX;
        private int textY;
        private int creditOffset;
        private int listX;
        private int exactWebViewHeight;
        private boolean wasUserInteraction;

        private TLRPC.TL_pageBlockEmbed currentBlock;

        private WebpageAdapter parentAdapter;

        public class TouchyWebView extends WebView {

            public TouchyWebView(Context context) {
                super(context);
                setFocusable(false);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                wasUserInteraction = true;
                if (currentBlock != null) {
                    if (currentBlock.allow_scrolling) {
                        requestDisallowInterceptTouchEvent(true);
                    } else {
                        windowView.requestDisallowInterceptTouchEvent(true);
                    }
                }
                return super.onTouchEvent(event);
            }
        }

        @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
        public BlockEmbedCell(final Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
            setWillNotDraw(false);

            videoView = new WebPlayerView(context, false, false, new WebPlayerView.WebPlayerViewDelegate() {
                @Override
                public void onInitFailed() {
                    webView.setVisibility(VISIBLE);
                    videoView.setVisibility(INVISIBLE);
                    videoView.loadVideo(null, null, null, null, false);
                    HashMap<String, String> args = new HashMap<>();
                    args.put("Referer", ApplicationLoader.applicationContext.getPackageName());
                    webView.loadUrl(currentBlock.url, args);
                }

                @Override
                public void onVideoSizeChanged(float aspectRatio, int rotation) {
                    fullscreenAspectRatioView.setAspectRatio(aspectRatio, rotation);
                }

                @Override
                public void onInlineSurfaceTextureReady() {

                }

                @Override
                public TextureView onSwitchToFullscreen(View controlsView, boolean fullscreen, float aspectRatio, int rotation, boolean byButton) {
                    if (fullscreen) {
                        fullscreenAspectRatioView.addView(fullscreenTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        fullscreenAspectRatioView.setVisibility(View.VISIBLE);
                        fullscreenAspectRatioView.setAspectRatio(aspectRatio, rotation);
                        fullscreenedVideo = videoView;
                        fullscreenVideoContainer.addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        fullscreenVideoContainer.setVisibility(VISIBLE);
                    } else {
                        fullscreenAspectRatioView.removeView(fullscreenTextureView);
                        fullscreenedVideo = null;
                        fullscreenAspectRatioView.setVisibility(View.GONE);
                        fullscreenVideoContainer.setVisibility(INVISIBLE);
                    }
                    return fullscreenTextureView;
                }

                @Override
                public void prepareToSwitchInlineMode(boolean inline, Runnable switchInlineModeRunnable, float aspectRatio, boolean animated) {

                }

                @Override
                public TextureView onSwitchInlineMode(View controlsView, boolean inline, float aspectRatio, int rotation, boolean animated) {
                    return null;
                }

                @Override
                public void onSharePressed() {
                    if (parentActivity == null) {
                        return;
                    }
                    showDialog(new ShareAlert(parentActivity, null, currentBlock.url, false, currentBlock.url, false));
                }

                @Override
                public void onPlayStateChanged(WebPlayerView playerView, boolean playing) {
                    if (playing) {
                        if (currentPlayingVideo != null && currentPlayingVideo != playerView) {
                            currentPlayingVideo.pause();
                        }
                        currentPlayingVideo = playerView;
                        try {
                            parentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        if (currentPlayingVideo == playerView) {
                            currentPlayingVideo = null;
                        }
                        try {
                            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }

                @Override
                public boolean checkInlinePermissions() {
                    return false;
                }

                @Override
                public ViewGroup getTextureViewContainer() {
                    return null;
                }
            });
            addView(videoView);
            createdWebViews.add(this);

            webView = new TouchyWebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);

            webView.getSettings().setAllowContentAccess(true);
            if (Build.VERSION.SDK_INT >= 17) {
                webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");
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
                    customViewCallback = callback;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (customView != null) {
                            fullscreenVideoContainer.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                            fullscreenVideoContainer.setVisibility(VISIBLE);
                        }
                    }, 100);
                }

                @Override
                public void onHideCustomView() {
                    super.onHideCustomView();
                    if (customView == null) {
                        return;
                    }
                    fullscreenVideoContainer.setVisibility(INVISIBLE);
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
                    //progressBar.setVisibility(INVISIBLE);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (wasUserInteraction) {
                        Browser.openUrl(parentActivity, url);
                        return true;
                    }
                    return false;
                }
            });
            addView(webView);
        }

        public void destroyWebView(boolean completely) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                if (completely) {
                    webView.destroy();
                }
                currentBlock = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
            videoView.destroy();
        }

        public void setBlock(TLRPC.TL_pageBlockEmbed block) {
            TLRPC.TL_pageBlockEmbed previousBlock = currentBlock;
            currentBlock = block;
            webView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            if (previousBlock != currentBlock) {
                wasUserInteraction = false;
                if (currentBlock.allow_scrolling) {
                    webView.setVerticalScrollBarEnabled(true);
                    webView.setHorizontalScrollBarEnabled(true);
                } else {
                    webView.setVerticalScrollBarEnabled(false);
                    webView.setHorizontalScrollBarEnabled(false);
                }
                exactWebViewHeight = 0;
                try {
                    webView.loadUrl("about:blank");
                } catch (Exception e) {
                    FileLog.e(e);
                }

                try {
                    if (currentBlock.html != null) {
                        webView.loadDataWithBaseURL("https://telegram.org/embed", currentBlock.html, "text/html", "UTF-8", null);
                        videoView.setVisibility(INVISIBLE);
                        videoView.loadVideo(null, null, null, null, false);
                        webView.setVisibility(VISIBLE);
                    } else {
                        TLRPC.Photo thumb = currentBlock.poster_photo_id != 0 ? parentAdapter.getPhotoWithId(currentBlock.poster_photo_id) : null;
                        boolean handled = videoView.loadVideo(block.url, thumb, parentAdapter.currentPage, null, false);
                        if (handled) {
                            webView.setVisibility(INVISIBLE);
                            videoView.setVisibility(VISIBLE);
                            webView.stopLoading();
                            webView.loadUrl("about:blank");
                        } else {
                            webView.setVisibility(VISIBLE);
                            videoView.setVisibility(INVISIBLE);
                            videoView.loadVideo(null, null, null, null, false);
                            HashMap<String, String> args = new HashMap<>();
                            args.put("Referer", ApplicationLoader.applicationContext.getPackageName());
                            webView.loadUrl(currentBlock.url, args);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            requestLayout();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (!isVisible) {
                currentBlock = null;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                int listWidth = width;
                int textWidth;
                if (currentBlock.level > 0) {
                    textX = listX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                    listWidth -= listX + AndroidUtilities.dp(18);
                    textWidth = listWidth;
                } else {
                    listX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                    if (!currentBlock.full_width) {
                        listWidth -= AndroidUtilities.dp(36);
                        listX += AndroidUtilities.dp(18);
                    }
                }
                float scale;
                if (currentBlock.w == 0) {
                    scale = 1;
                } else {
                    scale = width / (float) currentBlock.w;
                }
                if (exactWebViewHeight != 0) {
                    height = AndroidUtilities.dp(exactWebViewHeight);
                } else {
                    height = (int) (currentBlock.w == 0 ? AndroidUtilities.dp(currentBlock.h) * scale : currentBlock.h * scale);
                }
                if (height == 0) {
                    height = AndroidUtilities.dp(10);
                }
                webView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                if (videoView.getParent() == this) {
                    videoView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height + AndroidUtilities.dp(10), MeasureSpec.EXACTLY));
                }

                textY = AndroidUtilities.dp(8) + height;
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                    height += creditOffset + AndroidUtilities.dp(4);
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = creditOffset;
                }

                height += AndroidUtilities.dp(5);

                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += AndroidUtilities.dp(8);
                } else if (currentBlock.level == 0 && captionLayout != null) {
                    height += AndroidUtilities.dp(8);
                }
                if (captionLayout != null) {
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            webView.layout(listX, 0, listX + webView.getMeasuredWidth(), webView.getMeasuredHeight());
            if (videoView.getParent() == this) {
                videoView.layout(listX, 0, listX + videoView.getMeasuredWidth(), videoView.getMeasuredHeight());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }

            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    public class BlockTableCell extends FrameLayout implements TableLayout.TableLayoutDelegate, TextSelectionHelper.ArticleSelectableView {

        private HorizontalScrollView scrollView;
        private DrawingText titleLayout;
        private TableLayout tableLayout;
        private int listX;
        private int listY;
        private int textX;
        private int textY;

        private boolean firstLayout;

        private TLRPC.TL_pageBlockTable currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockTableCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            scrollView = new HorizontalScrollView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    boolean intercept = super.onInterceptTouchEvent(ev);
                    if (tableLayout.getMeasuredWidth() > getMeasuredWidth() - AndroidUtilities.dp(36) && intercept) {
                        windowView.requestDisallowInterceptTouchEvent(true);
                    }
                    return intercept;
                }

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    if (tableLayout.getMeasuredWidth() <= getMeasuredWidth() - AndroidUtilities.dp(36)) {
                        return false;
                    }
                    return super.onTouchEvent(ev);
                }

                @Override
                protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                    super.onScrollChanged(l, t, oldl, oldt);
                    if (pressedLinkOwnerLayout != null) {
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                    }
                    updateChildTextPositions();
                    if (textSelectionHelper != null && textSelectionHelper.isSelectionMode()) {
                        textSelectionHelper.invalidate();
                    }
                }

                @Override
                protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
                    removePressedLink();
                    return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    tableLayout.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight(), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), tableLayout.getMeasuredHeight());
                }
            };
            scrollView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            scrollView.setClipToPadding(false);
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            tableLayout = new TableLayout(context, this, textSelectionHelper);
            tableLayout.setOrientation(TableLayout.HORIZONTAL);
            tableLayout.setRowOrderPreserved(true);
            scrollView.addView(tableLayout, new HorizontalScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            setWillNotDraw(false);
        }

        @Override
        public DrawingText createTextLayout(TLRPC.TL_pageTableCell cell, int maxWidth) {
            if (cell == null) {
                return null;
            }
            Layout.Alignment alignment;
            if (cell.align_right) {
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
            } else if (cell.align_center) {
                alignment = Layout.Alignment.ALIGN_CENTER;
            } else {
                alignment = Layout.Alignment.ALIGN_NORMAL;
            }
            return createLayoutForText(this, null, cell.text, maxWidth, -1, currentBlock, alignment, 0, parentAdapter);
        }

        @Override
        public Paint getLinePaint() {
            return tableLinePaint;
        }

        @Override
        public Paint getHalfLinePaint() {
            return tableHalfLinePaint;
        }

        @Override
        public Paint getHeaderPaint() {
            return tableHeaderPaint;
        }

        @Override
        public Paint getStripPaint() {
            return tableStripPaint;
        }

        @Override
        public void onLayoutChild(DrawingText text, int x, int y) {
            if (text != null && !searchResults.isEmpty() && searchText != null) {
                String lowerString = text.textLayout.getText().toString().toLowerCase();
                int startIndex = 0;
                int index;
                while ((index = lowerString.indexOf(searchText, startIndex)) >= 0) {
                    startIndex = index + searchText.length();
                    if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString.charAt(index - 1))) {
                        adapter[0].searchTextOffset.put(searchText + currentBlock + text.parentText + index, y + text.textLayout.getLineTop(text.textLayout.getLineForOffset(index)));
                    }
                }
            }
        }

        public void setBlock(TLRPC.TL_pageBlockTable block) {
            currentBlock = block;
            AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_windowBackgroundWhite));
            tableLayout.removeAllChildrens();
            tableLayout.setDrawLines(currentBlock.bordered);
            tableLayout.setStriped(currentBlock.striped);
            tableLayout.setRtl(parentAdapter.isRtl);

            int maxCols = 0;

            if (!currentBlock.rows.isEmpty()) {
                TLRPC.TL_pageTableRow row = currentBlock.rows.get(0);
                for (int c = 0, size2 = row.cells.size(); c < size2; c++) {
                    TLRPC.TL_pageTableCell cell = row.cells.get(c);
                    maxCols += (cell.colspan != 0 ? cell.colspan : 1);
                }
            }

            for (int r = 0, size = currentBlock.rows.size(); r < size; r++) {
                TLRPC.TL_pageTableRow row = currentBlock.rows.get(r);
                int cols = 0;
                for (int c = 0, size2 = row.cells.size(); c < size2; c++) {
                    TLRPC.TL_pageTableCell cell = row.cells.get(c);
                    int colspan = (cell.colspan != 0 ? cell.colspan : 1);
                    int rowspan = (cell.rowspan != 0 ? cell.rowspan : 1);
                    if (cell.text != null) {
                        tableLayout.addChild(cell, cols, r, colspan);
                    } else {
                        tableLayout.addChild(cols, r, colspan, rowspan);
                    }
                    cols += colspan;
                }
            }
            tableLayout.setColumnCount(maxCols);
            firstLayout = true;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (checkLayoutForLinks(parentAdapter, event, this, c.textLayout, scrollView.getPaddingLeft() - scrollView.getScrollX() + listX + c.getTextX(), listY + c.getTextY())) {
                    return true;
                }
            }
            return checkLayoutForLinks(parentAdapter, event, this, titleLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            tableLayout.invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                int textWidth;
                if (currentBlock.level > 0) {
                    listX = AndroidUtilities.dp(14 * currentBlock.level);
                    textX = listX + AndroidUtilities.dp(18);
                    textWidth = width - textX;
                } else {
                    listX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                }

                titleLayout = createLayoutForText(this, null, currentBlock.title, textWidth, 0, currentBlock, Layout.Alignment.ALIGN_CENTER, 0, parentAdapter);
                if (titleLayout != null) {
                    textY = 0;
                    height += titleLayout.getHeight() + AndroidUtilities.dp(8);
                    listY = height;
                    titleLayout.x = textX;
                    titleLayout.y = textY;
                } else {
                    listY = AndroidUtilities.dp(8);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width - listX, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height += scrollView.getMeasuredHeight() + AndroidUtilities.dp(8);

                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += AndroidUtilities.dp(8);
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
            updateChildTextPositions();
        }

        private void updateChildTextPositions() {
            int count = titleLayout == null ? 0 : 1;
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout != null) {
                    c.textLayout.x = c.getTextX() + listX + AndroidUtilities.dp(18) - scrollView.getScrollX();
                    c.textLayout.y = c.getTextY() + listY;
                    c.textLayout.row = c.getRow();
                    c.setSelectionIndex(count++);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            scrollView.layout(listX, listY, listX + scrollView.getMeasuredWidth(), listY + scrollView.getMeasuredHeight());
            if (firstLayout) {
                if (parentAdapter.isRtl) {
                    scrollView.setScrollX(tableLayout.getMeasuredWidth() - scrollView.getMeasuredWidth() + AndroidUtilities.dp(36));
                } else {
                    scrollView.setScrollX(0);
                }
                firstLayout = false;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, 0);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (titleLayout != null) {
                blocks.add(titleLayout);
            }

            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout != null) {
                    blocks.add(c.textLayout);
                }
            }
        }
    }

    private class BlockCollageCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView{

        private RecyclerListView innerListView;
        private RecyclerView.Adapter innerAdapter;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int listX;
        private int textX;
        private int textY;
        private int creditOffset;

        private boolean inLayout;

        private TLRPC.TL_pageBlockCollage currentBlock;
        private GroupedMessages group = new GroupedMessages();

        private WebpageAdapter parentAdapter;

        public class GroupedMessages {
            public long groupId;
            public boolean hasSibling;
            public ArrayList<MessageObject.GroupedMessagePosition> posArray = new ArrayList<>();
            public HashMap<TLObject, MessageObject.GroupedMessagePosition> positions = new HashMap<>();

            private int maxSizeWidth = 1000;

            private class MessageGroupedLayoutAttempt {

                public int[] lineCounts;
                public float[] heights;

                public MessageGroupedLayoutAttempt(int i1, int i2, float f1, float f2) {
                    lineCounts = new int[] {i1, i2};
                    heights = new float[] {f1, f2};
                }

                public MessageGroupedLayoutAttempt(int i1, int i2, int i3, float f1, float f2, float f3) {
                    lineCounts = new int[] {i1, i2, i3};
                    heights = new float[] {f1, f2, f3};
                }

                public MessageGroupedLayoutAttempt(int i1, int i2, int i3, int i4, float f1, float f2, float f3, float f4) {
                    lineCounts = new int[] {i1, i2, i3, i4};
                    heights = new float[] {f1, f2, f3, f4};
                }
            }

            private float multiHeight(float[] array, int start, int end) {
                float sum = 0;
                for (int a = start; a < end; a++) {
                    sum += array[a];
                }
                return maxSizeWidth / sum;
            }

            public void calculate() {
                posArray.clear();
                positions.clear();
                int count = currentBlock.items.size();
                if (count <= 1) {
                    return;
                }

                float maxSizeHeight = 814.0f;
                StringBuilder proportions = new StringBuilder();
                float averageAspectRatio = 1.0f;
                boolean forceCalc = false;
                hasSibling = false;

                for (int a = 0; a < count; a++) {

                    TLRPC.PhotoSize photoSize;

                    TLObject object = currentBlock.items.get(a);
                    if (object instanceof TLRPC.TL_pageBlockPhoto) {
                        TLRPC.TL_pageBlockPhoto pageBlockPhoto = (TLRPC.TL_pageBlockPhoto) object;
                        TLRPC.Photo photo = parentAdapter.getPhotoWithId(pageBlockPhoto.photo_id);
                        if (photo == null) {
                            continue;
                        }
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    } else if (object instanceof TLRPC.TL_pageBlockVideo) {
                        TLRPC.TL_pageBlockVideo pageBlockVideo = (TLRPC.TL_pageBlockVideo) object;
                        TLRPC.Document document = parentAdapter.getDocumentWithId(pageBlockVideo.video_id);
                        if (document == null) {
                            continue;
                        }
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    } else {
                        continue;
                    }

                    MessageObject.GroupedMessagePosition position = new MessageObject.GroupedMessagePosition();
                    position.last = a == count - 1;
                    position.aspectRatio = photoSize == null ? 1.0f : photoSize.w / (float) photoSize.h;

                    if (position.aspectRatio > 1.2f) {
                        proportions.append("w");
                    } else if (position.aspectRatio < 0.8f) {
                        proportions.append("n");
                    } else {
                        proportions.append("q");
                    }

                    averageAspectRatio += position.aspectRatio;

                    if (position.aspectRatio > 2.0f) {
                        forceCalc = true;
                    }

                    positions.put(object, position);
                    posArray.add(position);
                }

                int minHeight = AndroidUtilities.dp(120);
                int minWidth = (int) (AndroidUtilities.dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
                int paddingsWidth = (int) (AndroidUtilities.dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));

                float maxAspectRatio = maxSizeWidth / maxSizeHeight;
                averageAspectRatio = averageAspectRatio / count;

                if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
                    if (count == 2) {
                        MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                        MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                        String pString = proportions.toString();
                        if (pString.equals("ww") && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
                            float height = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, Math.min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f))) / maxSizeHeight;
                            position1.set(0, 0, 0, 0, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                            position2.set(0, 0, 1, 1, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        } else if (pString.equals("ww") || pString.equals("qq")) {
                            int width = maxSizeWidth / 2;
                            float height = Math.round(Math.min(width / position1.aspectRatio, Math.min(width / position2.aspectRatio, maxSizeHeight))) / maxSizeHeight;
                            position1.set(0, 0, 0, 0, width, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                            position2.set(1, 1, 0, 0, width, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        } else {
                            int secondWidth = (int) Math.max(0.4f * maxSizeWidth, Math.round((maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio))));
                            int firstWidth = maxSizeWidth - secondWidth;
                            if (firstWidth < minWidth) {
                                int diff = minWidth - firstWidth;
                                firstWidth = minWidth;
                                secondWidth -= diff;
                            }

                            float height = Math.min(maxSizeHeight, Math.round(Math.min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio))) / maxSizeHeight;
                            position1.set(0, 0, 0, 0, firstWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                            position2.set(1, 1, 0, 0, secondWidth, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        }
                    } else if (count == 3) {
                        MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                        MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                        MessageObject.GroupedMessagePosition position3 = posArray.get(2);
                        if (proportions.charAt(0) == 'n') {
                            float thirdHeight = Math.min(maxSizeHeight * 0.5f, Math.round(position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)));
                            float secondHeight = maxSizeHeight - thirdHeight;
                            int rightWidth = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.5f, Math.round(Math.min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio))));

                            int leftWidth = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - rightWidth));
                            position1.set(0, 0, 0, 1, leftWidth, 1.0f, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);

                            position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                            position3.set(0, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                            position3.spanSize = maxSizeWidth;

                            position1.siblingHeights = new float[] {thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight};

                            position2.spanSize = maxSizeWidth - leftWidth;
                            position3.leftSpanOffset = leftWidth;

                            hasSibling = true;
                        } else {
                            float firstHeight = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, (maxSizeHeight) * 0.66f)) / maxSizeHeight;
                            position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                            int width = maxSizeWidth / 2;
                            float secondHeight = Math.min(maxSizeHeight - firstHeight, Math.round(Math.min(width / position2.aspectRatio, width / position3.aspectRatio))) / maxSizeHeight;
                            position2.set(0, 0, 1, 1, width, secondHeight, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                            position3.set(1, 1, 1, 1, width, secondHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        }
                    } else if (count == 4) {
                        MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                        MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                        MessageObject.GroupedMessagePosition position3 = posArray.get(2);
                        MessageObject.GroupedMessagePosition position4 = posArray.get(3);
                        if (proportions.charAt(0) == 'w') {
                            float h0 = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                            position1.set(0, 2, 0, 0, maxSizeWidth, h0, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                            float h = Math.round(maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio));
                            int w0 = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.4f, h * position2.aspectRatio));
                            int w2 = (int) Math.max(Math.max(minWidth, maxSizeWidth * 0.33f), h * position4.aspectRatio);
                            int w1 = maxSizeWidth - w0 - w2;
                            h = Math.min(maxSizeHeight - h0, h);
                            h /= maxSizeHeight;
                            position2.set(0, 0, 1, 1, w0, h, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                            position3.set(1, 1, 1, 1, w1, h, POSITION_FLAG_BOTTOM);
                            position4.set(2, 2, 1, 1, w2, h, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        } else {
                            int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / posArray.get(3).aspectRatio)));
                            float h0 = Math.min(0.33f, Math.max(minHeight, w / position2.aspectRatio) / maxSizeHeight);
                            float h1 = Math.min(0.33f, Math.max(minHeight, w / position3.aspectRatio) / maxSizeHeight);
                            float h2 = 1.0f - h0 - h1;
                            int w0 = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - w));

                            position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, POSITION_FLAG_LEFT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);

                            position2.set(1, 1, 0, 0, w, h0, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                            position3.set(0, 1, 1, 1, w, h1, POSITION_FLAG_RIGHT);
                            position3.spanSize = maxSizeWidth;

                            position4.set(0, 1, 2, 2, w, h2, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                            position4.spanSize = maxSizeWidth;

                            position2.spanSize = maxSizeWidth - w0;
                            position3.leftSpanOffset = w0;
                            position4.leftSpanOffset = w0;

                            position1.siblingHeights = new float[] {h0, h1, h2};
                            hasSibling = true;
                        }
                    }
                } else {
                    float[] croppedRatios = new float[posArray.size()];
                    for (int a = 0; a < count; a++) {
                        if (averageAspectRatio > 1.1f) {
                            croppedRatios[a] = Math.max(1.0f, posArray.get(a).aspectRatio);
                        } else {
                            croppedRatios[a] = Math.min(1.0f, posArray.get(a).aspectRatio);
                        }
                        croppedRatios[a] = Math.max(0.66667f, Math.min(1.7f, croppedRatios[a]));
                    }

                    int firstLine;
                    int secondLine;
                    int thirdLine;
                    int fourthLine;
                    ArrayList<MessageGroupedLayoutAttempt> attempts = new ArrayList<>();
                    for (firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
                        secondLine = croppedRatios.length - firstLine;
                        if (firstLine > 3 || secondLine > 3) {
                            continue;
                        }
                        attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.length)));
                    }

                    for (firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
                        for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                            thirdLine = croppedRatios.length - firstLine - secondLine;
                            if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
                                continue;
                            }
                            attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length)));
                        }
                    }

                    for (firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
                        for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                            for (thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
                                fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
                                if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                                    continue;
                                }
                                attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length)));
                            }
                        }
                    }

                    MessageGroupedLayoutAttempt optimal = null;
                    float optimalDiff = 0.0f;
                    float maxHeight = maxSizeWidth / 3 * 4;
                    for (int a = 0; a < attempts.size(); a++) {
                        MessageGroupedLayoutAttempt attempt = attempts.get(a);
                        float height = 0;
                        float minLineHeight = Float.MAX_VALUE;
                        for (int b = 0; b < attempt.heights.length; b++){
                            height += attempt.heights[b];
                            if (attempt.heights[b] < minLineHeight) {
                                minLineHeight = attempt.heights[b];
                            }
                        }

                        float diff = Math.abs(height - maxHeight);
                        if (attempt.lineCounts.length > 1) {
                            if (attempt.lineCounts[0] > attempt.lineCounts[1] || (attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
                                diff *= 1.2f;
                            }
                        }

                        if (minLineHeight < minWidth) {
                            diff *= 1.5f;
                        }

                        if (optimal == null || diff < optimalDiff) {
                            optimal = attempt;
                            optimalDiff = diff;
                        }
                    }
                    if (optimal == null) {
                        return;
                    }

                    int index = 0;
                    float y = 0.0f;

                    for (int i = 0; i < optimal.lineCounts.length; i++) {
                        int c = optimal.lineCounts[i];
                        float lineHeight = optimal.heights[i];
                        int spanLeft = maxSizeWidth;
                        MessageObject.GroupedMessagePosition posToFix = null;
                        for (int k = 0; k < c; k++) {
                            float ratio = croppedRatios[index];
                            int width = (int) (ratio * lineHeight);
                            spanLeft -= width;
                            MessageObject.GroupedMessagePosition pos = posArray.get(index);
                            int flags = 0;
                            if (i == 0) {
                                flags |= POSITION_FLAG_TOP;
                            }
                            if (i == optimal.lineCounts.length - 1) {
                                flags |= POSITION_FLAG_BOTTOM;
                            }
                            if (k == 0) {
                                flags |= POSITION_FLAG_LEFT;
                            }
                            if (k == c - 1) {
                                flags |= POSITION_FLAG_RIGHT;
                                posToFix = pos;
                            }
                            pos.set(k, k, i, i, width, lineHeight / maxSizeHeight, flags);
                            index++;
                        }
                        posToFix.pw += spanLeft;
                        posToFix.spanSize += spanLeft;
                        y += lineHeight;
                    }
                }
                for (int a = 0; a < count; a++) {
                    MessageObject.GroupedMessagePosition pos = posArray.get(a);

                    if ((pos.flags & POSITION_FLAG_LEFT) != 0) {
                        pos.edge = true;
                    }
                }
            }
        }

        public BlockCollageCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            innerListView = new RecyclerListView(context) {
                @Override
                public void requestLayout() {
                    if (inLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };
            innerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                    outRect.bottom = 0;
                    MessageObject.GroupedMessagePosition position;
                    if (view instanceof BlockPhotoCell) {
                        position = group.positions.get(((BlockPhotoCell) view).currentBlock);
                    } else if (view instanceof BlockVideoCell) {
                        position = group.positions.get(((BlockVideoCell) view).currentBlock);
                    } else {
                        position = null;
                    }
                    if (position != null && position.siblingHeights != null) {
                        float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                        int h = 0;
                        for (int a = 0; a < position.siblingHeights.length; a++) {
                            h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                        }
                        h += (position.maxY - position.minY) * AndroidUtilities.dp2(11);
                        int count = group.posArray.size();
                        for (int a = 0; a < count; a++) {
                            MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                            if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                                continue;
                            }
                            if (pos.minY == position.minY) {
                                h -= (int) Math.ceil(maxHeight * pos.ph) - AndroidUtilities.dp(4);
                                break;
                            }
                        }
                        outRect.bottom = -h;
                    }

                    //outRect.top = outRect.left = 0;
                    //outRect.bottom = outRect.right = AndroidUtilities.dp(2);
                }
            });

            GridLayoutManager gridLayoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }

                @Override
                public boolean shouldLayoutChildFromOpositeSide(View child) {
                    return false;
                }

                @Override
                protected boolean hasSiblingChild(int position) {
                    TLObject message = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    MessageObject.GroupedMessagePosition pos = group.positions.get(message);
                    if (pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                        return false;
                    }
                    int count = group.posArray.size();
                    for (int a = 0; a < count; a++) {
                        MessageObject.GroupedMessagePosition p = group.posArray.get(a);
                        if (p == pos) {
                            continue;
                        }
                        if (p.minY <= pos.minY && p.maxY >= pos.minY) {
                            return true;
                        }
                    }
                    return false;
                }
            };
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManagerFixed.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    TLObject message = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    return group.positions.get(message).spanSize;
                }
            });

            innerListView.setLayoutManager(gridLayoutManager);
            innerListView.setAdapter(innerAdapter = new RecyclerView.Adapter() {
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    View view;
                    switch (viewType) {
                        case 0: {
                            view = new BlockPhotoCell(getContext(), parentAdapter, 2);
                            break;
                        }
                        case 1:
                        default: {
                            view = new BlockVideoCell(getContext(), parentAdapter, 2);
                            break;
                        }
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    TLRPC.PageBlock pageBlock = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    switch (holder.getItemViewType()) {
                        case 0: {
                            BlockPhotoCell cell = (BlockPhotoCell) holder.itemView;
                            cell.groupPosition = group.positions.get(pageBlock);
                            cell.setBlock((TLRPC.TL_pageBlockPhoto) pageBlock, true, true);
                            break;
                        }
                        case 1:
                        default: {
                            BlockVideoCell cell = (BlockVideoCell) holder.itemView;
                            cell.groupPosition = group.positions.get(pageBlock);
                            cell.setBlock((TLRPC.TL_pageBlockVideo) pageBlock, true, true);
                            break;
                        }
                    }
                }

                @Override
                public int getItemCount() {
                    if (currentBlock == null) {
                        return 0;
                    }
                    return currentBlock.items.size();
                }

                @Override
                public int getItemViewType(int position) {
                    TLRPC.PageBlock block = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    if (block instanceof TLRPC.TL_pageBlockPhoto) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
            addView(innerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setWillNotDraw(false);
        }

        public void setBlock(TLRPC.TL_pageBlockCollage block) {
            if (currentBlock != block) {
                currentBlock = block;
                group.calculate();
            }
            innerAdapter.notifyDataSetChanged();
            innerListView.setGlowColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            inLayout = true;
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                int listWidth = width;
                int textWidth;
                if (currentBlock.level > 0) {
                    textX = listX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                    listWidth -= listX + AndroidUtilities.dp(18);
                    textWidth = listWidth;
                } else {
                    listX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                }

                innerListView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = innerListView.getMeasuredHeight();

                textY = height + AndroidUtilities.dp(8);
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                    height += creditOffset + AndroidUtilities.dp(4);
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = textY + creditOffset;
                }

                height += AndroidUtilities.dp(16);
                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += AndroidUtilities.dp(8);
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
            inLayout = false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            innerListView.layout(listX, AndroidUtilities.dp(8), listX + innerListView.getMeasuredWidth(), innerListView.getMeasuredHeight() + AndroidUtilities.dp(8));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }


        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private static Paint dotsPaint;

    private class BlockSlideshowCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private ViewPager innerListView;
        private PagerAdapter innerAdapter;
        private View dotsContainer;

        private TLRPC.TL_pageBlockSlideshow currentBlock;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY;
        private int creditOffset;

        private float pageOffset;
        private int currentPage;

        private WebpageAdapter parentAdapter;

        public BlockSlideshowCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            if (dotsPaint == null) {
                dotsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                dotsPaint.setColor(0xffffffff);
            }

            innerListView = new ViewPager(context) {
                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    return super.onTouchEvent(ev);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    windowView.requestDisallowInterceptTouchEvent(true);
                    cancelCheckLongPress();
                    return super.onInterceptTouchEvent(ev);
                }
            };
            innerListView.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    float width = innerListView.getMeasuredWidth();
                    if (width == 0) {
                        return;
                    }
                    pageOffset = (position * width + positionOffsetPixels - currentPage * width) / width;
                    dotsContainer.invalidate();
                }

                @Override
                public void onPageSelected(int position) {
                    currentPage = position;
                    dotsContainer.invalidate();
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
            innerListView.setAdapter(innerAdapter = new PagerAdapter() {

                class ObjectContainer {
                    private TLRPC.PageBlock block;
                    private View view;
                }

                @Override
                public int getCount() {
                    if (currentBlock == null) {
                        return 0;
                    }
                    return currentBlock.items.size();
                }

                @Override
                public boolean isViewFromObject(View view, Object object) {
                    return ((ObjectContainer) object).view == view;
                }

                @Override
                public int getItemPosition(Object object) {
                    ObjectContainer objectContainer = (ObjectContainer) object;
                    if (currentBlock.items.contains(objectContainer.block)) {
                        return POSITION_UNCHANGED;
                    }
                    return POSITION_NONE;
                }

                @Override
                public Object instantiateItem(ViewGroup container, int position) {
                    View view;
                    TLRPC.PageBlock block = currentBlock.items.get(position);
                    if (block instanceof TLRPC.TL_pageBlockPhoto) {
                        view = new BlockPhotoCell(getContext(), parentAdapter, 1);
                        ((BlockPhotoCell) view).setBlock((TLRPC.TL_pageBlockPhoto) block, true, true);
                    } else {
                        view = new BlockVideoCell(getContext(), parentAdapter, 1);
                        ((BlockVideoCell) view).setBlock((TLRPC.TL_pageBlockVideo) block, true, true);
                    }
                    container.addView(view);
                    ObjectContainer objectContainer = new ObjectContainer();
                    objectContainer.view = view;
                    objectContainer.block = block;
                    return objectContainer;
                }

                @Override
                public void destroyItem(ViewGroup container, int position, Object object) {
                    container.removeView(((ObjectContainer) object).view);
                }

                @Override
                public void unregisterDataSetObserver(DataSetObserver observer) {
                    if (observer != null) {
                        super.unregisterDataSetObserver(observer);
                    }
                }
            });
            AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, Theme.getColor(Theme.key_windowBackgroundWhite));
            addView(innerListView);

            dotsContainer = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (currentBlock == null) {
                        return;
                    }

                    int count = innerAdapter.getCount();
                    int totalWidth = count * AndroidUtilities.dp(7) + (count - 1) * AndroidUtilities.dp(6) + AndroidUtilities.dp(4);
                    int xOffset;
                    if (totalWidth < getMeasuredWidth()) {
                        xOffset = (getMeasuredWidth() - totalWidth) / 2;
                    } else {
                        xOffset = AndroidUtilities.dp(4);
                        int size = AndroidUtilities.dp(13);
                        int halfCount = (getMeasuredWidth() - AndroidUtilities.dp(8)) / 2 / size;
                        if (currentPage == count - halfCount - 1 && pageOffset < 0) {
                            xOffset -= (int) (pageOffset * size) + (count - halfCount * 2 - 1) * size;
                        } else if (currentPage >= count - halfCount - 1) {
                            xOffset -= (count - halfCount * 2 - 1) * size;
                        } else if (currentPage > halfCount) {
                            xOffset -= (int) (pageOffset * size) + (currentPage - halfCount) * size;
                        } else if (currentPage == halfCount && pageOffset > 0) {
                            xOffset -= (int) (pageOffset * size);
                        }
                    }
                    for (int a = 0; a < currentBlock.items.size(); a++) {
                        int cx = xOffset + AndroidUtilities.dp(4) + AndroidUtilities.dp(13) * a;
                        Drawable drawable = currentPage == a ? slideDotBigDrawable : slideDotDrawable;
                        drawable.setBounds(cx - AndroidUtilities.dp(5), 0, cx + AndroidUtilities.dp(5), AndroidUtilities.dp(10));
                        drawable.draw(canvas);
                    }
                }
            };
            addView(dotsContainer);

            setWillNotDraw(false);
        }

        public void setBlock(TLRPC.TL_pageBlockSlideshow block) {
            currentBlock = block;
            innerAdapter.notifyDataSetChanged();
            innerListView.setCurrentItem(0, false);
            innerListView.forceLayout();
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                height = AndroidUtilities.dp(310);
                innerListView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                int count = currentBlock.items.size();
                dotsContainer.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10), MeasureSpec.EXACTLY));

                int textWidth = width - AndroidUtilities.dp(36);
                textY = height + AndroidUtilities.dp(16);
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                    height += creditOffset + AndroidUtilities.dp(4);
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = textY + creditOffset;
                }

                height += AndroidUtilities.dp(16);
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            innerListView.layout(0, AndroidUtilities.dp(8), innerListView.getMeasuredWidth(), AndroidUtilities.dp(8) + innerListView.getMeasuredHeight());
            int y = innerListView.getBottom() - AndroidUtilities.dp(7 + 16);
            dotsContainer.layout(0, y, dotsContainer.getMeasuredWidth(), y + dotsContainer.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private class BlockListItemCell extends ViewGroup implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private RecyclerView.ViewHolder blockLayout;
        private int textX;
        private int textY;
        private int numOffsetY;
        private int blockX;
        private int blockY;

        private boolean verticalAlign;
        private int currentBlockType;
        private TL_pageBlockListItem currentBlock;
        private boolean drawDot;

        private WebpageAdapter parentAdapter;

        public BlockListItemCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
            setWillNotDraw(false);
        }

        public void setBlock(TL_pageBlockListItem block) {
            if (currentBlock != block) {
                currentBlock = block;
                if (blockLayout != null) {
                    removeView(blockLayout.itemView);
                    blockLayout = null;
                }
                if (currentBlock.blockItem != null) {
                    currentBlockType = parentAdapter.getTypeForBlock(currentBlock.blockItem);
                    blockLayout = parentAdapter.onCreateViewHolder(this, currentBlockType);
                    addView(blockLayout.itemView);
                }
            }
            if (currentBlock.blockItem != null) {
                parentAdapter.bindBlockToHolder(currentBlockType, blockLayout, currentBlock.blockItem, 0, 0);
            }
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY)) {
                return true;
            }
            return super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = null;
                textY = currentBlock.index == 0 && currentBlock.parent.level == 0 ? AndroidUtilities.dp(10) : 0;
                numOffsetY = 0;
                if (currentBlock.parent.lastMaxNumCalcWidth != width || currentBlock.parent.lastFontSize != SharedConfig.ivFontSize) {
                    currentBlock.parent.lastMaxNumCalcWidth = width;
                    currentBlock.parent.lastFontSize = SharedConfig.ivFontSize;
                    currentBlock.parent.maxNumWidth = 0;
                    for (int a = 0, size = currentBlock.parent.items.size(); a < size; a++) {
                        TL_pageBlockListItem item = currentBlock.parent.items.get(a);
                        if (item.num == null) {
                            continue;
                        }
                        item.numLayout = createLayoutForText(this, item.num, null, width - AndroidUtilities.dp(36 + 18), textY, currentBlock, parentAdapter);
                        currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(item.numLayout.getLineWidth(0)));
                    }
                    currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(listTextNumPaint.measureText("00.")));
                }
                drawDot = !currentBlock.parent.pageBlockList.ordered;

                if (parentAdapter.isRtl) {
                    textX = AndroidUtilities.dp(18);
                } else {
                    textX = AndroidUtilities.dp(18 + 6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * AndroidUtilities.dp(12);
                }
                int maxWidth = width - AndroidUtilities.dp(18) - textX;
                if (parentAdapter.isRtl) {
                    maxWidth -= AndroidUtilities.dp(6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * AndroidUtilities.dp(12);
                }
                if (currentBlock.textItem != null) {
                    textLayout = createLayoutForText(this, null, currentBlock.textItem, maxWidth, textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                    if (textLayout != null && textLayout.getLineCount() > 0) {
                        if (currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            int ascent = textLayout.getLineAscent(0);
                            numOffsetY = (currentBlock.numLayout.getLineAscent(0) + AndroidUtilities.dp(2.5f)) - ascent;
                        }
                        height += textLayout.getHeight() + AndroidUtilities.dp(8);
                    }
                } else if (currentBlock.blockItem != null) {
                    blockX = textX;
                    blockY = textY;
                    if (blockLayout != null) {
                        if (blockLayout.itemView instanceof BlockParagraphCell) {
                            blockY -= AndroidUtilities.dp(8);
                            if (!parentAdapter.isRtl) {
                                blockX -= AndroidUtilities.dp(18);
                            }
                            maxWidth += AndroidUtilities.dp(18);
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockHeaderCell ||
                                blockLayout.itemView instanceof BlockSubheaderCell ||
                                blockLayout.itemView instanceof BlockTitleCell ||
                                blockLayout.itemView instanceof BlockSubtitleCell) {
                            if (!parentAdapter.isRtl) {
                                blockX -= AndroidUtilities.dp(18);
                            }
                            maxWidth += AndroidUtilities.dp(18);
                        } else if (isListItemBlock(currentBlock.blockItem)) {
                            blockX = 0;
                            blockY = 0;
                            textY = 0;
                            if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                                height -= AndroidUtilities.dp(10);
                            }
                            maxWidth = width;
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockTableCell) {
                            blockX -= AndroidUtilities.dp(18);
                            maxWidth += AndroidUtilities.dp(36);
                        }
                        blockLayout.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        if (blockLayout.itemView instanceof BlockParagraphCell && currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            BlockParagraphCell paragraphCell = (BlockParagraphCell) blockLayout.itemView;
                            if (paragraphCell.textLayout != null && paragraphCell.textLayout.getLineCount() > 0) {
                                int ascent = paragraphCell.textLayout.getLineAscent(0);
                                numOffsetY = (currentBlock.numLayout.getLineAscent(0) + AndroidUtilities.dp(2.5f)) - ascent;
                            }
                        }
                        if (currentBlock.blockItem instanceof TLRPC.TL_pageBlockDetails) {
                            verticalAlign = true;
                            blockY = 0;
                            if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                                height -= AndroidUtilities.dp(10);
                            }
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockOrderedListItemCell) {
                            verticalAlign = ((BlockOrderedListItemCell) blockLayout.itemView).verticalAlign;
                        } else if (blockLayout.itemView instanceof BlockListItemCell) {
                            verticalAlign = ((BlockListItemCell) blockLayout.itemView).verticalAlign;
                        }
                        if (verticalAlign && currentBlock.numLayout != null) {
                            textY = (blockLayout.itemView.getMeasuredHeight() - currentBlock.numLayout.getHeight()) / 2 - AndroidUtilities.dp(4);
                            drawDot = false;
                        }
                        height += blockLayout.itemView.getMeasuredHeight();
                    }
                    height += AndroidUtilities.dp(8);
                }
                if (currentBlock.parent.items.get(currentBlock.parent.items.size() - 1) == currentBlock) {
                    height += AndroidUtilities.dp(8);
                }
                if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                    height += AndroidUtilities.dp(10);
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
                if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                    textSelectionHelper.arrayList.clear();
                    ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(textSelectionHelper.arrayList);
                    for (TextSelectionHelper.TextLayoutBlock block : textSelectionHelper.arrayList) {
                        if (block instanceof DrawingText) {
                            ((DrawingText) block).x += blockX;
                            ((DrawingText) block).y += blockY;
                        }
                    }
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (blockLayout != null) {
                blockLayout.itemView.layout(blockX, blockY, blockX + blockLayout.itemView.getMeasuredWidth(), blockY + blockLayout.itemView.getMeasuredHeight());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int width = getMeasuredWidth();
            if (currentBlock.numLayout != null) {
                canvas.save();
                if (parentAdapter.isRtl) {
                    canvas.translate(width - AndroidUtilities.dp(15) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * AndroidUtilities.dp(12), textY + numOffsetY - (drawDot ? AndroidUtilities.dp(1) : 0));
                } else {
                    canvas.translate(AndroidUtilities.dp(15) + currentBlock.parent.maxNumWidth - (int) Math.ceil(currentBlock.numLayout.getLineWidth(0)) + currentBlock.parent.level * AndroidUtilities.dp(12), textY + numOffsetY - (drawDot ? AndroidUtilities.dp(1) : 0));
                }
                currentBlock.numLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas,this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (blockLayout != null) {
                blockLayout.itemView.invalidate();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(textLayout.getText());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(blocks);
            }
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockOrderedListItemCell extends ViewGroup implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private RecyclerView.ViewHolder blockLayout;
        private int textX;
        private int textY;
        private int numOffsetY;
        private int blockX;
        private int blockY;

        private int currentBlockType;
        private boolean verticalAlign;

        private TL_pageBlockOrderedListItem currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockOrderedListItemCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
            setWillNotDraw(false);
        }

        public void setBlock(TL_pageBlockOrderedListItem block) {
            if (currentBlock != block) {
                currentBlock = block;
                if (blockLayout != null) {
                    removeView(blockLayout.itemView);
                    blockLayout = null;
                }
                if (currentBlock.blockItem != null) {
                    currentBlockType = parentAdapter.getTypeForBlock(currentBlock.blockItem);
                    blockLayout = parentAdapter.onCreateViewHolder(this, currentBlockType);
                    addView(blockLayout.itemView);
                }
            }
            if (currentBlock.blockItem != null) {
                parentAdapter.bindBlockToHolder(currentBlockType, blockLayout, currentBlock.blockItem, 0, 0);
            }
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY)) {
                return true;
            }
            return super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = null;
                textY = currentBlock.index == 0 && currentBlock.parent.level == 0 ? AndroidUtilities.dp(10) : 0;
                numOffsetY = 0;
                if (currentBlock.parent.lastMaxNumCalcWidth != width || currentBlock.parent.lastFontSize != SharedConfig.ivFontSize) {
                    currentBlock.parent.lastMaxNumCalcWidth = width;
                    currentBlock.parent.lastFontSize = SharedConfig.ivFontSize;
                    currentBlock.parent.maxNumWidth = 0;
                    for (int a = 0, size = currentBlock.parent.items.size(); a < size; a++) {
                        TL_pageBlockOrderedListItem item = currentBlock.parent.items.get(a);
                        if (item.num == null) {
                            continue;
                        }
                        item.numLayout = createLayoutForText(this, item.num, null, width - AndroidUtilities.dp(36 + 18), textY, currentBlock, parentAdapter);
                        currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(item.numLayout.getLineWidth(0)));
                    }
                    currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(listTextNumPaint.measureText("00.")));
                }
                if (parentAdapter.isRtl) {
                    textX = AndroidUtilities.dp(18);
                } else {
                    textX = AndroidUtilities.dp(18 + 6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * AndroidUtilities.dp(20);
                }
                verticalAlign = false;
                int maxWidth = width - AndroidUtilities.dp(18) - textX;
                if (parentAdapter.isRtl) {
                    maxWidth -= AndroidUtilities.dp(6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * AndroidUtilities.dp(20);
                }
                if (currentBlock.textItem != null) {
                    textLayout = createLayoutForText(this, null, currentBlock.textItem, maxWidth, textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                    if (textLayout != null && textLayout.getLineCount() > 0) {
                        if (currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            int ascent = textLayout.getLineAscent(0);
                            numOffsetY = currentBlock.numLayout.getLineAscent(0) - ascent;
                        }
                        height += textLayout.getHeight() + AndroidUtilities.dp(8);
                    }
                } else if (currentBlock.blockItem != null) {
                    blockX = textX;
                    blockY = textY;
                    if (blockLayout != null) {
                        if (blockLayout.itemView instanceof BlockParagraphCell) {
                            blockY -= AndroidUtilities.dp(8);
                            if (!parentAdapter.isRtl) {
                                blockX -= AndroidUtilities.dp(18);
                            }
                            maxWidth += AndroidUtilities.dp(18);
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockHeaderCell ||
                                blockLayout.itemView instanceof BlockSubheaderCell ||
                                blockLayout.itemView instanceof BlockTitleCell ||
                                blockLayout.itemView instanceof BlockSubtitleCell) {
                            if (!parentAdapter.isRtl) {
                                blockX -= AndroidUtilities.dp(18);
                            }
                            maxWidth += AndroidUtilities.dp(18);
                        } else if (isListItemBlock(currentBlock.blockItem)) {
                            blockX = 0;
                            blockY = 0;
                            textY = 0;
                            maxWidth = width;
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockTableCell) {
                            blockX -= AndroidUtilities.dp(18);
                            maxWidth += AndroidUtilities.dp(36);
                        }
                        blockLayout.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        if (blockLayout.itemView instanceof BlockParagraphCell && currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            BlockParagraphCell paragraphCell = (BlockParagraphCell) blockLayout.itemView;
                            if (paragraphCell.textLayout != null && paragraphCell.textLayout.getLineCount() > 0) {
                                int ascent = paragraphCell.textLayout.getLineAscent(0);
                                numOffsetY = currentBlock.numLayout.getLineAscent(0) - ascent;
                            }
                        }
                        if (currentBlock.blockItem instanceof TLRPC.TL_pageBlockDetails) {
                            verticalAlign = true;
                            blockY = 0;
                            height -= AndroidUtilities.dp(8);
                        } else if (blockLayout.itemView instanceof BlockOrderedListItemCell) {
                            verticalAlign = ((BlockOrderedListItemCell) blockLayout.itemView).verticalAlign;
                        } else if (blockLayout.itemView instanceof BlockListItemCell) {
                            verticalAlign = ((BlockListItemCell) blockLayout.itemView).verticalAlign;
                        }
                        if (verticalAlign && currentBlock.numLayout != null) {
                            textY = (blockLayout.itemView.getMeasuredHeight() - currentBlock.numLayout.getHeight()) / 2;
                        }
                        height += blockLayout.itemView.getMeasuredHeight();
                    }
                    height += AndroidUtilities.dp(8);
                }
                if (currentBlock.parent.items.get(currentBlock.parent.items.size() - 1) == currentBlock) {
                    height += AndroidUtilities.dp(8);
                }
                if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                    height += AndroidUtilities.dp(10);
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                    if (currentBlock.numLayout != null) {
                        textLayout.prefix = currentBlock.numLayout.textLayout.getText();
                    }
                }
                if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                    textSelectionHelper.arrayList.clear();
                    ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(textSelectionHelper.arrayList);
                    for (TextSelectionHelper.TextLayoutBlock block : textSelectionHelper.arrayList) {
                        if (block instanceof DrawingText) {
                            ((DrawingText)block).x += blockX;
                            ((DrawingText)block).y += blockY;
                        }
                    }
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (blockLayout != null) {
                blockLayout.itemView.layout(blockX, blockY, blockX + blockLayout.itemView.getMeasuredWidth(), blockY + blockLayout.itemView.getMeasuredHeight());
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int width = getMeasuredWidth();
            if (currentBlock.numLayout != null) {
                canvas.save();
                if (parentAdapter.isRtl) {
                    canvas.translate(width - AndroidUtilities.dp(18) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * AndroidUtilities.dp(20), textY + numOffsetY);
                } else {
                    canvas.translate(AndroidUtilities.dp(18) + currentBlock.parent.maxNumWidth - (int) Math.ceil(currentBlock.numLayout.getLineWidth(0)) + currentBlock.parent.level * AndroidUtilities.dp(20), textY + numOffsetY);
                }
                currentBlock.numLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (blockLayout != null) {
                blockLayout.itemView.invalidate();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(textLayout.getText());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(blocks);
            }
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockDetailsCell extends View implements Drawable.Callback, TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(44 + 6);
        private int textY = AndroidUtilities.dp(11) + 1;
        private AnimatedArrowDrawable arrow;

        private TLRPC.TL_pageBlockDetails currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockDetailsCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            arrow = new AnimatedArrowDrawable(getGrayTextColor(), true);
        }

        @Override
        public void invalidateDrawable(Drawable drawable) {
            invalidate();
        }

        @Override
        public void scheduleDrawable(Drawable drawable, Runnable runnable, long l) {

        }

        @Override
        public void unscheduleDrawable(Drawable drawable, Runnable runnable) {

        }

        public void setBlock(TLRPC.TL_pageBlockDetails block) {
            currentBlock = block;
            arrow.setAnimationProgress(block.open ? 0.0f : 1.0f);
            arrow.setCallback(this);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int h = AndroidUtilities.dp(39);
            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.title, width - AndroidUtilities.dp(36 + 16), 0, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    h = Math.max(h, AndroidUtilities.dp(21) + textLayout.getHeight());
                    textY = (textLayout.getHeight() + AndroidUtilities.dp(21) - textLayout.getHeight()) / 2;
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            }
            setMeasuredDimension(width, h + 1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            canvas.save();
            canvas.translate(AndroidUtilities.dp(18), (getMeasuredHeight() - AndroidUtilities.dp(13) - 1) / 2);
            arrow.draw(canvas);
            canvas.restore();

            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas,this);
                textLayout.draw(canvas);
                canvas.restore();
            }

            int y = getMeasuredHeight() - 1;
            canvas.drawLine(0, y, getMeasuredWidth(), y, dividerPaint);
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private static class BlockDetailsBottomCell extends View {

        private RectF rect = new RectF();

        public BlockDetailsBottomCell(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1 + AndroidUtilities.dp(4));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(0, 0, getMeasuredWidth(), 0, dividerPaint);
        }
    }

    private static class BlockRelatedArticlesShadowCell extends View {

        private CombinedDrawable shadowDrawable;

        public BlockRelatedArticlesShadowCell(Context context) {
            super(context);

            Drawable drawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, 0xff000000);
            shadowDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
            shadowDrawable.setFullsize(true);
            setBackgroundDrawable(shadowDrawable);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(12));
            Theme.setCombinedDrawableColor(shadowDrawable, Theme.getColor(Theme.key_windowBackgroundGray), false);
        }
    }

    private class BlockRelatedArticlesHeaderCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY;

        private TLRPC.TL_pageBlockRelatedArticles currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockRelatedArticlesHeaderCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockRelatedArticles block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.title, width - AndroidUtilities.dp(36 + 16), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, parentAdapter);
                if (textLayout != null) {
                    textY = AndroidUtilities.dp(6) + (AndroidUtilities.dp(32) - textLayout.getHeight()) / 2;
                }
            }
            if (textLayout != null) {
                setMeasuredDimension(width, AndroidUtilities.dp(38));
                textLayout.x = textX;
                textLayout.y = textY;
            } else {
                setMeasuredDimension(width, 1);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockRelatedArticlesCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private boolean divider;
        private boolean drawImage;

        private ImageReceiver imageView;

        private TL_pageBlockRelatedArticlesChild currentBlock;

        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(10);
        private int textOffset;

        private WebpageAdapter parentAdapter;

        public BlockRelatedArticlesCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            imageView = new ImageReceiver(this);
            imageView.setRoundRadius(AndroidUtilities.dp(6));
        }

        public void setBlock(TL_pageBlockRelatedArticlesChild block) {
            currentBlock = block;
            requestLayout();
        }

        @SuppressLint({"DrawAllocation", "NewApi"})
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);

            divider = currentBlock.num != currentBlock.parent.articles.size() - 1;
            TLRPC.TL_pageRelatedArticle item = currentBlock.parent.articles.get(currentBlock.num);

            int additionalHeight = AndroidUtilities.dp(SharedConfig.ivFontSize - 16);

            TLRPC.Photo photo = item.photo_id != 0 ? parentAdapter.getPhotoWithId(item.photo_id) : null;
            if (photo != null) {
                drawImage = true;
                TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80, true);
                if (image == thumb) {
                    thumb = null;
                }
                imageView.setImage(ImageLocation.getForPhoto(image, photo), "64_64", ImageLocation.getForPhoto(thumb, photo), "64_64_b", image.size, null, parentAdapter.currentPage, 1);
            } else {
                drawImage = false;
            }

            int layoutHeight = AndroidUtilities.dp(16 + 44);
            int availableWidth = width - AndroidUtilities.dp(18 + 18);
            if (drawImage) {
                int imageWidth = AndroidUtilities.dp(44);
                imageView.setImageCoords(width - imageWidth - AndroidUtilities.dp(8), AndroidUtilities.dp(8), imageWidth, imageWidth);
                availableWidth -= imageView.getImageWidth() + AndroidUtilities.dp(6);
            }

            int height = AndroidUtilities.dp(18);

            boolean isTitleRtl = false;
            if (item.title != null) {
                textLayout = createLayoutForText(this, item.title, null, availableWidth, textY, currentBlock, Layout.Alignment.ALIGN_NORMAL, 3, parentAdapter);
            }
            int lineCount = 4;
            if (textLayout != null) {
                int count = textLayout.getLineCount();
                lineCount -= count;
                textOffset = textLayout.getHeight() + AndroidUtilities.dp(6) + additionalHeight;
                height += textLayout.getHeight();
                for (int a = 0; a < count; a++) {
                    if (textLayout.getLineLeft(a) != 0) {
                        isTitleRtl = true;
                        break;
                    }
                }
                textLayout.x = textX;
                textLayout.y = textY;
            } else {
                textOffset = 0;
            }
            String description;
            if (item.published_date != 0 && !TextUtils.isEmpty(item.author)) {
                description = LocaleController.formatString("ArticleDateByAuthor", R.string.ArticleDateByAuthor, LocaleController.getInstance().chatFullDate.format((long) item.published_date * 1000), item.author);
            } else if (!TextUtils.isEmpty(item.author)) {
                description = LocaleController.formatString("ArticleByAuthor", R.string.ArticleByAuthor, item.author);
            } else if (item.published_date != 0) {
                description = LocaleController.getInstance().chatFullDate.format((long) item.published_date * 1000);
            } else if (!TextUtils.isEmpty(item.description)) {
                description = item.description;
            } else {
                description = item.url;
            }
            textLayout2 = createLayoutForText(this, description, null, availableWidth, textY + textOffset, currentBlock, parentAdapter.isRtl || isTitleRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, lineCount, parentAdapter);
            if (textLayout2 != null) {
                height += textLayout2.getHeight();
                if (textLayout != null) {
                    height += AndroidUtilities.dp(6) + additionalHeight;
                }
                textLayout2.x = textX;
                textLayout2.y = textY + textOffset;
            }
            layoutHeight = Math.max(layoutHeight, height);

            setMeasuredDimension(width, layoutHeight + (divider ? 1 : 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (drawImage) {
                imageView.draw(canvas);
            }
            int count = 0;
            canvas.save();
            canvas.translate(textX, AndroidUtilities.dp(10));
            if (textLayout != null) {
                drawTextSelection(canvas, this, count++);
                textLayout.draw(canvas);
            }
            if (textLayout2 != null) {
                canvas.translate(0, textOffset);
                drawTextSelection(canvas, this, count);
                textLayout2.draw(canvas);
            }
            canvas.restore();
            if (divider) {
                canvas.drawLine(parentAdapter.isRtl ? 0 : AndroidUtilities.dp(17), getMeasuredHeight() - 1, getMeasuredWidth() - (parentAdapter.isRtl ? AndroidUtilities.dp(17) : 0), getMeasuredHeight() - 1, dividerPaint);
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
            if (textLayout2 != null) {
                blocks.add(textLayout2);
            }
        }
    }

    private class BlockHeaderCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockHeader currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockHeaderCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockHeader block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(textLayout.getText() + ", " + LocaleController.getString("AccDescrIVHeading", R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private static class BlockDividerCell extends View {

        private RectF rect = new RectF();

        public BlockDividerCell(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(2 + 16));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getMeasuredWidth() / 3;
            rect.set(width, AndroidUtilities.dp(8), width * 2, AndroidUtilities.dp(10));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), dividerPaint);
        }
    }

    private class BlockSubtitleCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockSubtitle currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockSubtitleCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockSubtitle block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null) {
                return;
            }
            info.setText(textLayout.getText() + ", " + LocaleController.getString("AccDescrIVHeading", R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockPullquoteCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private int textY2;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockPullquote currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockPullquoteCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockPullquote block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
                textY2 = height + AndroidUtilities.dp(2);
                textLayout2 = createLayoutForText(this, null, currentBlock.caption, width - AndroidUtilities.dp(36), textY2, currentBlock, parentAdapter);
                if (textLayout2 != null) {
                    height += AndroidUtilities.dp(8) + textLayout2.getHeight();
                    textLayout2.x = textX;
                    textLayout2.y = textY2;
                }
                if (height != 0) {
                    height += AndroidUtilities.dp(8);
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int count = 0;
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawTextSelection(canvas, this, count);
                textLayout2.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
            if (textLayout2 != null) {
                blocks.add(textLayout2);
            }
        }
    }

    private class BlockBlockquoteCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private int textY2;
        private int textX;
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockBlockquote currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockBlockquoteCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockBlockquote block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                int textWidth = width - AndroidUtilities.dp(36 + 14);
                if (currentBlock.level > 0) {
                    textWidth -= AndroidUtilities.dp(14 * currentBlock.level);
                }
                textLayout = createLayoutForText(this, null, currentBlock.text, textWidth, textY, currentBlock, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8) + textLayout.getHeight();
                }
                if (currentBlock.level > 0) {
                    if (parentAdapter.isRtl) {
                        textX = AndroidUtilities.dp(14 + currentBlock.level * 14);
                    } else {
                        textX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18 + 14);
                    }
                } else {
                    if (parentAdapter.isRtl) {
                        textX = AndroidUtilities.dp(14);
                    } else {
                        textX = AndroidUtilities.dp(18 + 14);
                    }
                }
                textY2 = height + AndroidUtilities.dp(8);
                textLayout2 = createLayoutForText(this, null, currentBlock.caption, textWidth, textY2, currentBlock, parentAdapter);
                if (textLayout2 != null) {
                    height += AndroidUtilities.dp(8) + textLayout2.getHeight();
                }
                if (height != 0) {
                    height += AndroidUtilities.dp(8);
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                }

                if (textLayout2 != null) {
                    textLayout2.x = textX;
                    textLayout2.y = textY2;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            int counter = 0;
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, counter++);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawTextSelection(canvas, this, counter);
                textLayout2.draw(canvas);
                canvas.restore();
            }
            if (parentAdapter.isRtl) {
                int x = getMeasuredWidth() - AndroidUtilities.dp(20);
                canvas.drawRect(x, AndroidUtilities.dp(6), x + AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(6), quoteLinePaint);
            } else {
                canvas.drawRect(AndroidUtilities.dp(18 + currentBlock.level * 14), AndroidUtilities.dp(6), AndroidUtilities.dp(20 + currentBlock.level * 14), getMeasuredHeight() - AndroidUtilities.dp(6), quoteLinePaint);
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
            if (textLayout2 != null) {
                blocks.add(textLayout2);
            }
        }
    }

    private class BlockPhotoCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private ImageReceiver imageView;
        private RadialProgress2 radialProgress;
        private BlockChannelCell channelCell;
        private int currentType;
        private boolean isFirst;
        private int textX;
        private int textY;
        private int creditOffset;

        private int buttonX;
        private int buttonY;
        private boolean photoPressed;
        private int buttonState;
        private int buttonPressed;

        private TLRPC.PhotoSize currentPhotoObject;
        private String currentFilter;
        private TLRPC.PhotoSize currentPhotoObjectThumb;
        private String currentThumbFilter;
        private TLRPC.Photo currentPhoto;

        private int TAG;

        private TLRPC.TL_pageBlockPhoto currentBlock;
        private TLRPC.PageBlock parentBlock;

        private MessageObject.GroupedMessagePosition groupPosition;
        private Drawable linkDrawable;

        boolean autoDownload;

        private WebpageAdapter parentAdapter;

        public BlockPhotoCell(Context context, WebpageAdapter adapter, int type) {
            super(context);
            parentAdapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            channelCell = new BlockChannelCell(context, parentAdapter, 1);
            radialProgress = new RadialProgress2(this);
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            currentType = type;
        }

        public void setBlock(TLRPC.TL_pageBlockPhoto block, boolean first, boolean last) {
            parentBlock = null;
            currentBlock = block;
            isFirst = first;
            channelCell.setVisibility(INVISIBLE);
            if (!TextUtils.isEmpty(currentBlock.url)) {
                linkDrawable = getResources().getDrawable(R.drawable.instant_link);
            }
            if (currentBlock != null) {
                TLRPC.Photo photo = parentAdapter.getPhotoWithId(currentBlock.photo_id);
                if (photo != null) {
                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                } else {
                    currentPhotoObject = null;
                }
            } else {
                currentPhotoObject = null;
            }
            updateButtonState(false);
            requestLayout();
        }

        public void setParentBlock(TLRPC.PageBlock block) {
            parentBlock = block;
            if (parentAdapter.channelBlock != null && parentBlock instanceof TLRPC.TL_pageBlockCover) {
                channelCell.setBlock(parentAdapter.channelBlock);
                channelCell.setVisibility(VISIBLE);
            }
        }

        public View getChannelCell() {
            return channelCell;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (pinchToZoomHelper.checkPinchToZoom(event, this, imageView, null)) {
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + AndroidUtilities.dp(39)) {
                if (parentAdapter.channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    MessagesController.getInstance(currentAccount).openByUserName(parentAdapter.channelBlock.channel.username, parentFragment, 2);
                    close(false, true);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48) || buttonState == 0) {
                    buttonPressed = 1;
                    invalidate();
                } else {
                    photoPressed = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (photoPressed) {
                    photoPressed = false;
                    openPhoto(currentBlock, parentAdapter);
                } else if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
                buttonPressed = 0;
            }
            return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentType == 1) {
                width = ((View) getParent()).getMeasuredWidth();
                height = ((View) getParent()).getMeasuredHeight();
            } else if (currentType == 2) {
                height = (int) Math.ceil(groupPosition.ph * Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f);
            }
            if (currentBlock != null) {
                currentPhoto = parentAdapter.getPhotoWithId(currentBlock.photo_id);
                int size = AndroidUtilities.dp(48);
                int photoWidth = width;
                int photoHeight = height;
                int photoX;
                int textWidth;
                if (currentType == 0 && currentBlock.level > 0) {
                    textX = photoX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                    photoWidth -= photoX + AndroidUtilities.dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                }
                if (currentPhoto != null && currentPhotoObject != null) {
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(currentPhoto.sizes, 40, true);
                    if (currentPhotoObject == currentPhotoObjectThumb) {
                        currentPhotoObjectThumb = null;
                    }
                    if (currentType == 0) {
                        float scale;
                        scale = photoWidth / (float) currentPhotoObject.w;
                        height = (int) (scale * currentPhotoObject.h);
                        if (parentBlock instanceof TLRPC.TL_pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / (float) currentPhotoObject.h;
                                photoWidth = (int) (scale * currentPhotoObject.w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                        photoHeight = height;
                    } else if (currentType == 2) {
                        if ((groupPosition.flags & POSITION_FLAG_RIGHT) == 0) {
                            photoWidth -= AndroidUtilities.dp(2);
                        }
                        if ((groupPosition.flags & POSITION_FLAG_BOTTOM) == 0) {
                            photoHeight -= AndroidUtilities.dp(2);
                        }
                        if (groupPosition.leftSpanOffset != 0) {
                            int offset = (int) Math.ceil(width * groupPosition.leftSpanOffset / 1000.0f);
                            photoWidth -= offset;
                            photoX += offset;
                        }
                    }
                    imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : AndroidUtilities.dp(8), photoWidth, photoHeight);
                    if (currentType == 0) {
                        currentFilter = null;
                    } else {
                        currentFilter = String.format(Locale.US, "%d_%d", photoWidth, photoHeight);
                    }
                    currentThumbFilter = "80_80_b";

                    autoDownload = (DownloadController.getInstance(currentAccount).getCurrentDownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0;
                    File path = FileLoader.getPathToAttach(currentPhotoObject, true);
                    if (autoDownload || path.exists()) {
                        imageView.setStrippedLocation(null);
                        imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, parentAdapter.currentPage, 1);
                    } else {
                        imageView.setStrippedLocation(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto));
                        imageView.setImage(null, currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, parentAdapter.currentPage, 1);
                    }
                    buttonX = (int) (imageView.getImageX() + (imageView.getImageWidth() - size) / 2.0f);
                    buttonY = (int) (imageView.getImageY() + (imageView.getImageHeight() - size) / 2.0f);
                    radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + AndroidUtilities.dp(8));

                if (currentType == 0) {
                    captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                    if (captionLayout != null) {
                        creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                        height += creditOffset + AndroidUtilities.dp(4);
                    }
                    creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter);
                    if (creditLayout != null) {
                        height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }
                boolean nextIsChannel = parentBlock instanceof TLRPC.TL_pageBlockCover && parentAdapter.blocks != null && parentAdapter.blocks.size() > 1 && parentAdapter.blocks.get(1) instanceof TLRPC.TL_pageBlockChannel;
                if (currentType != 2 && !nextIsChannel) {
                    height += AndroidUtilities.dp(8);
                }
                if (captionLayout != null) {
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                }

                if (creditLayout != null) {
                    creditLayout.x = textX;
                    creditLayout.y = textY + creditOffset;
                }
            } else {
                height = 1;
            }
            channelCell.measure(widthMeasureSpec, heightMeasureSpec);
            channelCell.setTranslationY(imageView.getImageHeight() - AndroidUtilities.dp(39));

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (!imageView.hasBitmapImage() || imageView.getCurrentAlpha() != 1.0f) {
                canvas.drawRect(imageView.getImageX(), imageView.getImageY(), imageView.getImageX2(), imageView.getImageY2(), photoBackgroundPaint);
            }
            if (!pinchToZoomHelper.isInOverlayModeFor(this)) {
                imageView.draw(canvas);
                if (imageView.getVisible()) {
                    radialProgress.draw(canvas);
                }
            }
            if (!TextUtils.isEmpty(currentBlock.url)) {
                int x = getMeasuredWidth() - AndroidUtilities.dp(11 + 24);
                int y = (int) (imageView.getImageY() + AndroidUtilities.dp(11));
                linkDrawable.setBounds(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
                linkDrawable.draw(canvas);
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas,this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas,this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        private int getIconForCurrentState() {
            if (buttonState == 0) {
                return MediaActionDrawable.ICON_DOWNLOAD;
            } else if (buttonState == 1) {
                return MediaActionDrawable.ICON_CANCEL;
            }
            return MediaActionDrawable.ICON_NONE;
        }

        private void didPressedButton(boolean animated) {
            if (buttonState == 0) {
                radialProgress.setProgress(0, animated);
                imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, parentAdapter.currentPage, 1);
                buttonState = 1;
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                invalidate();
            } else if (buttonState == 1) {
                imageView.cancelLoadImage();
                buttonState = 0;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
                invalidate();
            }
        }

        public void updateButtonState(boolean animated) {
            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
            File path = FileLoader.getPathToAttach(currentPhotoObject, true);
            boolean fileExists = path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
                return;
            }

            if (fileExists) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                buttonState = -1;
                radialProgress.setIcon(getIconForCurrentState(), false, animated);
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                float setProgress = 0;
                if (autoDownload || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
                    buttonState = 1;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    setProgress = progress != null ? progress : 0;
                } else {
                    buttonState = 0;
                }
                radialProgress.setIcon(getIconForCurrentState(), true, animated);
                radialProgress.setProgress(setProgress, false);
            }
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            imageView.onDetachedFromWindow();
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageView.onAttachedToWindow();
            updateButtonState(false);
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(false);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            updateButtonState(true);
        }

        @Override
        public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            radialProgress.setProgress(Math.min(1f, downloadSize / (float) totalSize), true);
            if (buttonState != 1) {
                updateButtonState(true);
            }
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
            if (captionLayout != null) {
                sb.append(", ");
                sb.append(captionLayout.getText());
            }
            info.setText(sb.toString());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private class BlockMapCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private ImageReceiver imageView;
        private int currentType;
        private boolean isFirst;
        private int textX;
        private int textY;
        private int creditOffset;
        private boolean photoPressed;
        private int currentMapProvider;

        private TLRPC.TL_pageBlockMap currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockMapCell(Context context, WebpageAdapter adapter, int type) {
            super(context);
            parentAdapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            currentType = type;
        }

        public void setBlock(TLRPC.TL_pageBlockMap block, boolean first, boolean last) {
            currentBlock = block;
            isFirst = first;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                photoPressed = true;
            } else if (event.getAction() == MotionEvent.ACTION_UP && photoPressed) {
                photoPressed = false;
                try {
                    double lat = currentBlock.geo.lat;
                    double lon = currentBlock.geo._long;
                    parentActivity.startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentType == 1) {
                width = ((View) getParent()).getMeasuredWidth();
                height = ((View) getParent()).getMeasuredHeight();
            } else if (currentType == 2) {
                height = width;
            }
            if (currentBlock != null) {
                int photoWidth = width;
                int photoX;
                int textWidth;
                if (currentType == 0 && currentBlock.level > 0) {
                    textX = photoX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18);
                    photoWidth -= photoX + AndroidUtilities.dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = AndroidUtilities.dp(18);
                    textWidth = width - AndroidUtilities.dp(36);
                }

                if (currentType == 0) {
                    float scale;
                    scale = photoWidth / (float) currentBlock.w;
                    height = (int) (scale * currentBlock.h);

                    int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(56)) * 0.9f);
                    if (height > maxHeight) {
                        height = maxHeight;
                        scale = height / (float) currentBlock.h;
                        photoWidth = (int) (scale * currentBlock.w);
                        photoX += (width - photoX - photoWidth) / 2;
                    }
                }
                imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : AndroidUtilities.dp(8), photoWidth, height);

                String currentUrl = AndroidUtilities.formapMapUrl(currentAccount, currentBlock.geo.lat, currentBlock.geo._long, (int) (photoWidth / AndroidUtilities.density), (int) (height / AndroidUtilities.density), true, 15, -1);
                WebFile currentWebFile = WebFile.createWithGeoPoint(currentBlock.geo, (int) (photoWidth / AndroidUtilities.density), (int) (height / AndroidUtilities.density), 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));

                currentMapProvider = MessagesController.getInstance(currentAccount).mapProvider;
                if (currentMapProvider == 2) {
                    if (currentWebFile != null) {
                        imageView.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, parentAdapter.currentPage, 0);
                    }
                } else if (currentUrl != null) {
                    imageView.setImage(currentUrl, null, null, null, 0);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + AndroidUtilities.dp(8));
                if (currentType == 0) {
                    captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                    if (captionLayout != null) {
                        creditOffset = AndroidUtilities.dp(4) + captionLayout.getHeight();
                        height += creditOffset + AndroidUtilities.dp(4);
                        captionLayout.x = textX;
                        captionLayout.y = textY;
                    }
                    creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                    if (creditLayout != null) {
                        height += AndroidUtilities.dp(4) + creditLayout.getHeight();
                        creditLayout.x = textX;
                        creditLayout.y = textY + creditOffset;
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }
                if (currentType != 2) {
                    height += AndroidUtilities.dp(8);
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }

            Theme.chat_docBackPaint.setColor(Theme.getColor(Theme.key_chat_inLocationBackground));
            canvas.drawRect(imageView.getImageX(), imageView.getImageY(), imageView.getImageX2(), imageView.getImageY2(), Theme.chat_docBackPaint);
            int left = (int) (imageView.getCenterX() - Theme.chat_locationDrawable[0].getIntrinsicWidth() / 2);
            int top = (int) (imageView.getCenterY() - Theme.chat_locationDrawable[0].getIntrinsicHeight() / 2);
            Theme.chat_locationDrawable[0].setBounds(left, top, left + Theme.chat_locationDrawable[0].getIntrinsicWidth(), top + Theme.chat_locationDrawable[0].getIntrinsicHeight());
            Theme.chat_locationDrawable[0].draw(canvas);

            imageView.draw(canvas);
            if (currentMapProvider == 2 && imageView.hasNotThumb()) {
                int w = (int) (Theme.chat_redLocationIcon.getIntrinsicWidth() * 0.8f);
                int h = (int) (Theme.chat_redLocationIcon.getIntrinsicHeight() * 0.8f);
                int x = (int) (imageView.getImageX() + (imageView.getImageWidth() - w) / 2);
                int y = (int) (imageView.getImageY() + (imageView.getImageHeight() / 2 - h));
                Theme.chat_redLocationIcon.setAlpha((int) (255 * imageView.getCurrentAlpha()));
                Theme.chat_redLocationIcon.setBounds(x, y, x + w, y + h);
                Theme.chat_redLocationIcon.draw(canvas);
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this, count++);
                captionLayout.draw(canvas);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(LocaleController.getString("Map", R.string.Map));
            if (captionLayout != null) {
                sb.append(", ");
                sb.append(captionLayout.getText());
            }
            info.setText(sb.toString());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (captionLayout != null) {
                blocks.add(captionLayout);
            }
            if (creditLayout != null) {
                blocks.add(creditLayout);
            }
        }
    }

    private class BlockChannelCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private ContextProgressView progressView;
        private TextView textView;
        private ImageView imageView;
        private int currentState;

        private DrawingText textLayout;
        private int buttonWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(11);
        private int textX2;
        private Paint backgroundPaint;
        private AnimatorSet currentAnimation;
        private int currentType;

        private TLRPC.TL_pageBlockChannel currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockChannelCell(Context context, WebpageAdapter adapter, int type) {
            super(context);
            parentAdapter = adapter;
            setWillNotDraw(false);
            backgroundPaint = new Paint();
            currentType = type;

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 39, Gravity.RIGHT | Gravity.TOP));
            textView.setOnClickListener(v -> {
                if (currentState != 0) {
                    return;
                }
                setState(1, true);
                joinChannel(BlockChannelCell.this, loadedChannel);
            });

            imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.list_check);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(39, 39, Gravity.RIGHT | Gravity.TOP));

            progressView = new ContextProgressView(context, 0);
            addView(progressView, LayoutHelper.createFrame(39, 39, Gravity.RIGHT | Gravity.TOP));
        }

        public void setBlock(TLRPC.TL_pageBlockChannel block) {
            currentBlock = block;

            if (currentType == 0) {
                int color = Theme.getColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                textView.setTextColor(getLinkTextColor());
                backgroundPaint.setColor(Color.argb(34, r, g, b));
                imageView.setColorFilter(new PorterDuffColorFilter(getGrayTextColor(), PorterDuff.Mode.MULTIPLY));
            } else {
                textView.setTextColor(0xffffffff);
                backgroundPaint.setColor(0x7f000000);
                imageView.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
            }
            TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(block.channel.id);
            if (channel == null || channel.min) {
                loadChannel(this, parentAdapter, block.channel);
                setState(1, false);
            } else {
                loadedChannel = channel;
                if (channel.left && !channel.kicked) {
                    setState(0, false);
                } else {
                    setState(4, false);
                }
            }
            requestLayout();
        }

        public void setState(int state, boolean animated) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
            }
            currentState = state;
            if (animated) {
                currentAnimation = new AnimatorSet();
                currentAnimation.playTogether(
                        ObjectAnimator.ofFloat(textView, View.ALPHA, state == 0 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(textView, View.SCALE_X, state == 0 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(textView, View.SCALE_Y, state == 0 ? 1.0f : 0.1f),

                        ObjectAnimator.ofFloat(progressView, View.ALPHA, state == 1 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, state == 1 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, state == 1 ? 1.0f : 0.1f),

                        ObjectAnimator.ofFloat(imageView, View.ALPHA, state == 2 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_X, state == 2 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_Y, state == 2 ? 1.0f : 0.1f)
                );
                currentAnimation.setDuration(150);
                currentAnimation.start();
            } else {
                textView.setAlpha(state == 0 ? 1.0f : 0.0f);
                textView.setScaleX(state == 0 ? 1.0f : 0.1f);
                textView.setScaleY(state == 0 ? 1.0f : 0.1f);

                progressView.setAlpha(state == 1 ? 1.0f : 0.0f);
                progressView.setScaleX(state == 1 ? 1.0f : 0.1f);
                progressView.setScaleY(state == 1 ? 1.0f : 0.1f);

                imageView.setAlpha(state == 2 ? 1.0f : 0.0f);
                imageView.setScaleX(state == 2 ? 1.0f : 0.1f);
                imageView.setScaleY(state == 2 ? 1.0f : 0.1f);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (currentType != 0) {
                return super.onTouchEvent(event);
            }
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, AndroidUtilities.dp(39 + 9));

            textView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            buttonWidth = textView.getMeasuredWidth();
            progressView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            if (currentBlock != null) {
                textLayout = createLayoutForText(this, currentBlock.channel.title, null, width - AndroidUtilities.dp(36 + 16) - buttonWidth, textY, currentBlock, StaticLayoutEx.ALIGN_LEFT(), parentAdapter);
                if (parentAdapter.isRtl) {
                    textX2 = textX;
                } else {
                    textX2 = getMeasuredWidth() - textX - buttonWidth;
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            imageView.layout(textX2 + buttonWidth / 2 - AndroidUtilities.dp(19), 0, textX2 + buttonWidth / 2 + AndroidUtilities.dp(20), AndroidUtilities.dp(39));
            progressView.layout(textX2 + buttonWidth / 2 - AndroidUtilities.dp(19), 0, textX2 + buttonWidth / 2 + AndroidUtilities.dp(20), AndroidUtilities.dp(39));
            textView.layout(textX2, 0, textX2 + textView.getMeasuredWidth(), textView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(39), backgroundPaint);
            if (textLayout != null && textLayout.getLineCount() > 0) {
                canvas.save();
                if (parentAdapter.isRtl) {
                    canvas.translate(getMeasuredWidth() - textLayout.getLineWidth(0) - textX, textY);
                } else {
                    canvas.translate(textX, textY);
                }
                if (currentType == 0) {
                    drawTextSelection(canvas, this);
                }
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockAuthorDateCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX;
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockAuthorDate currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockAuthorDateCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockAuthorDate block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                CharSequence text;
                CharSequence author = getText(parentAdapter, this, currentBlock.author, currentBlock.author, currentBlock, width);
                Spannable spannableAuthor;
                MetricAffectingSpan[] spans;
                if (author instanceof Spannable) {
                    spannableAuthor = (Spannable) author;
                    spans = spannableAuthor.getSpans(0, author.length(), MetricAffectingSpan.class);
                } else {
                    spannableAuthor = null;
                    spans = null;
                }
                if (currentBlock.published_date != 0 && !TextUtils.isEmpty(author)) {
                    text = LocaleController.formatString("ArticleDateByAuthor", R.string.ArticleDateByAuthor, LocaleController.getInstance().chatFullDate.format((long) currentBlock.published_date * 1000), author);
                } else if (!TextUtils.isEmpty(author)) {
                    text = LocaleController.formatString("ArticleByAuthor", R.string.ArticleByAuthor, author);
                } else {
                    text = LocaleController.getInstance().chatFullDate.format((long) currentBlock.published_date * 1000);
                }
                try {
                    if (spans != null && spans.length > 0) {
                        int idx = TextUtils.indexOf(text, author);
                        if (idx != -1) {
                            Spannable spannable = Spannable.Factory.getInstance().newSpannable(text);
                            text = spannable;
                            for (int a = 0; a < spans.length; a++) {
                                spannable.setSpan(spans[a], idx + spannableAuthor.getSpanStart(spans[a]), idx + spannableAuthor.getSpanEnd(spans[a]), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                textLayout = createLayoutForText(this, text, null, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    if (parentAdapter.isRtl) {
                        textX = (int) Math.floor(width - textLayout.getLineWidth(0) - AndroidUtilities.dp(16));
                    } else {
                        textX = AndroidUtilities.dp(18);
                    }
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

		@Override
		public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
			super.onInitializeAccessibilityNodeInfo(info);
			info.setEnabled(true);
            if (textLayout == null)
                return;
			info.setText(textLayout.getText());
		}

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockTitleCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;

        private TLRPC.TL_pageBlockTitle currentBlock;
        private int textX = AndroidUtilities.dp(18);
        private int textY;

        private WebpageAdapter parentAdapter;

        public BlockTitleCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockTitle block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.first) {
                    height += AndroidUtilities.dp(8);
                    textY = AndroidUtilities.dp(16);
                } else {
                    textY = AndroidUtilities.dp(8);
                }
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas,this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(textLayout.getText() + ", " + LocaleController.getString("AccDescrIVTitle", R.string.AccDescrIVTitle));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockKickerCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;

        private TLRPC.TL_pageBlockKicker currentBlock;
        private int textX = AndroidUtilities.dp(18);
        private int textY;

        private WebpageAdapter parentAdapter;

        public BlockKickerCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockKicker block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.first) {
                    textY = AndroidUtilities.dp(16);
                    height += AndroidUtilities.dp(8);
                } else {
                    textY = AndroidUtilities.dp(8);
                }
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockFooterCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockFooter currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockFooterCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockFooter block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.level == 0) {
                    textY = AndroidUtilities.dp(8);
                    textX = AndroidUtilities.dp(18);
                } else {
                    textY = 0;
                    textX = AndroidUtilities.dp(18 + 14 * currentBlock.level);
                }
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(18) - textX, textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height = textLayout.getHeight();
                    if (currentBlock.level > 0) {
                        height += AndroidUtilities.dp(8);
                    } else {
                        height += AndroidUtilities.dp(8 + 8);
                    }
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private class BlockPreformattedCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private DrawingText textLayout;
        private HorizontalScrollView scrollView;
        private View textContainer;

        private TLRPC.TL_pageBlockPreformatted currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockPreformattedCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;

            scrollView = new HorizontalScrollView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (textContainer.getMeasuredWidth() > getMeasuredWidth()) {
                        windowView.requestDisallowInterceptTouchEvent(true);
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                    super.onScrollChanged(l, t, oldl, oldt);
                    if (pressedLinkOwnerLayout != null) {
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                    }
                }
            };
            scrollView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            textContainer = new View(context) {

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int height = 0;
                    int width = 1;
                    if (currentBlock != null) {
                        textLayout = createLayoutForText(this, null, currentBlock.text, AndroidUtilities.dp(5000), 0, currentBlock, parentAdapter);
                        if (textLayout != null) {
                            height += textLayout.getHeight();
                            for (int a = 0, count = textLayout.getLineCount(); a < count; a++) {
                                width = Math.max((int) Math.ceil(textLayout.getLineWidth(a)), width);
                            }
                        }
                    } else {
                        height = 1;
                    }
                    setMeasuredDimension(width + AndroidUtilities.dp(32), height);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return checkLayoutForLinks(parentAdapter, event, BlockPreformattedCell.this, textLayout, 0, 0) || super.onTouchEvent(event);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (textLayout != null) {
                        canvas.save();
                        drawTextSelection(canvas, BlockPreformattedCell.this);
                        textLayout.draw(canvas);
                        canvas.restore();
                        textLayout.x = (int) getX();
                        textLayout.y = (int) getY();
                    }
                }
            };
            HorizontalScrollView.LayoutParams layoutParams = new HorizontalScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT);
            layoutParams.leftMargin = layoutParams.rightMargin = AndroidUtilities.dp(16);
            layoutParams.topMargin = layoutParams.bottomMargin = AndroidUtilities.dp(12);
            scrollView.addView(textContainer, layoutParams);

            if (Build.VERSION.SDK_INT >= 23) {
                scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (textSelectionHelper != null && textSelectionHelper.isSelectionMode()) {
                        textSelectionHelper.invalidate();
                    }
                });
            }

            setWillNotDraw(false);
        }

        public void setBlock(TLRPC.TL_pageBlockPreformatted block) {
            currentBlock = block;
            scrollView.setScrollX(0);
            textContainer.requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            setMeasuredDimension(width, scrollView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            canvas.drawRect(0, AndroidUtilities.dp(8), getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(8), preformattedBackgroundPaint);
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void invalidate() {
            textContainer.invalidate();
            super.invalidate();
        }
    }

    private class BlockSubheaderCell extends View implements TextSelectionHelper.ArticleSelectableView{

        private DrawingText textLayout;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockSubheader currentBlock;

        private WebpageAdapter parentAdapter;

        public BlockSubheaderCell(Context context, WebpageAdapter adapter) {
            super(context);
            parentAdapter = adapter;
        }

        public void setBlock(TLRPC.TL_pageBlockSubheader block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36), textY, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (textLayout != null) {
                    height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(canvas, this);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null) {
                return;
            }
            info.setText(textLayout.getText() + ", " + LocaleController.getString("AccDescrIVHeading", R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }
    }

    private static class ReportCell extends FrameLayout {

        private TextView textView;
        private TextView viewsTextView;
        private boolean hasViews;

        public ReportCell(Context context) {
            super(context);
            setTag(90);

            textView = new TextView(context);
            textView.setText(LocaleController.getString("PreviewFeedback2", R.string.PreviewFeedback2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 0, 10, 0, 0));

            viewsTextView = new TextView(context);
            viewsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            viewsTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            viewsTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            addView(viewsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 0, 10, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
        }

        public void setViews(int count) {
            if (count == 0) {
                hasViews = false;
                viewsTextView.setVisibility(GONE);
                textView.setGravity(Gravity.CENTER);
            } else {
                hasViews = true;
                viewsTextView.setVisibility(VISIBLE);
                textView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                viewsTextView.setText(LocaleController.formatPluralStringComma("Views", count));
            }
            int color = Theme.getColor(Theme.key_switchTrack);
            textView.setTextColor(getGrayTextColor());
            viewsTextView.setTextColor(getGrayTextColor());
            textView.setBackgroundColor(Color.argb(34, Color.red(color), Color.green(color), Color.blue(color)));
        }
    }

    private void drawTextSelection(Canvas canvas, TextSelectionHelper.ArticleSelectableView view) {
        drawTextSelection(canvas, view, 0);
    }

    private void drawTextSelection(Canvas canvas, TextSelectionHelper.ArticleSelectableView view, int i) {
        View v = (View) view;
        if (v.getTag() != null && v.getTag() == BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
            textSelectionHelperBottomSheet.draw(canvas, view, i);
        } else {
            textSelectionHelper.draw(canvas, view, i);
        }
    }

    public boolean openPhoto(TLRPC.PageBlock block, WebpageAdapter adapter) {
        final int index;
        final List<TLRPC.PageBlock> pageBlocks;
        if (!(block instanceof TLRPC.TL_pageBlockVideo) || WebPageUtils.isVideo(adapter.currentPage, block)) {
            pageBlocks = new ArrayList<>(adapter.photoBlocks);
            index = adapter.photoBlocks.indexOf(block);
        } else {
            pageBlocks = Collections.singletonList(block);
            index = 0;
        }
        final PhotoViewer photoViewer = PhotoViewer.getInstance();
        photoViewer.setParentActivity(parentActivity);
        return photoViewer.openPhoto(index, new RealPageBlocksAdapter(adapter.currentPage, pageBlocks), new PageBlocksPhotoViewerProvider(pageBlocks));
    }


    private class RealPageBlocksAdapter implements PhotoViewer.PageBlocksAdapter {

        private final TLRPC.WebPage page;
        private final List<TLRPC.PageBlock> pageBlocks;

        private RealPageBlocksAdapter(TLRPC.WebPage page, List<TLRPC.PageBlock> pageBlocks) {
            this.page = page;
            this.pageBlocks = pageBlocks;
        }

        @Override
        public int getItemsCount() {
            return pageBlocks.size();
        }

        @Override
        public TLRPC.PageBlock get(int index) {
            return pageBlocks.get(index);
        }

        @Override
        public List<TLRPC.PageBlock> getAll() {
            return pageBlocks;
        }

        @Override
        public boolean isVideo(int index) {
            return !(index >= pageBlocks.size() || index < 0) && WebPageUtils.isVideo(page, get(index));
        }

        @Override
        public TLObject getMedia(int index) {
            if (index >= pageBlocks.size() || index < 0) {
                return null;
            }
            return WebPageUtils.getMedia(page, get(index));
        }

        @Override
        public File getFile(int index) {
            if (index >= pageBlocks.size() || index < 0) {
                return null;
            }
            return WebPageUtils.getMediaFile(page, get(index));
        }

        @Override
        public String getFileName(int index) {
            TLObject media = getMedia(index);
            if (media instanceof TLRPC.Photo) {
                media = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
            }
            return FileLoader.getAttachFileName(media);
        }

        @Override
        public CharSequence getCaption(int index) {
            CharSequence caption = null;
            final TLRPC.PageBlock pageBlock = get(index);
            if (pageBlock instanceof TLRPC.TL_pageBlockPhoto) {
                String url = ((TLRPC.TL_pageBlockPhoto) pageBlock).url;
                if (!TextUtils.isEmpty(url)) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(url);
                    stringBuilder.setSpan(new URLSpan(url) {
                        @Override
                        public void onClick(View widget) {
                            openWebpageUrl(getURL(), null);
                        }
                    }, 0, url.length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                    caption = stringBuilder;
                }
            }
            if (caption == null) {
                TLRPC.RichText captionRichText = getBlockCaption(pageBlock, 2);
                caption = getText(page, null, captionRichText, captionRichText, pageBlock, -AndroidUtilities.dp(100));
                if (caption instanceof Spannable) {
                    Spannable spannable = (Spannable) caption;
                    TextPaintUrlSpan[] spans = spannable.getSpans(0, caption.length(), TextPaintUrlSpan.class);
                    SpannableStringBuilder builder = new SpannableStringBuilder(caption.toString());
                    caption = builder;
                    if (spans != null && spans.length > 0) {
                        for (int a = 0; a < spans.length; a++) {
                            builder.setSpan(new URLSpan(spans[a].getUrl()) {
                                @Override
                                public void onClick(View widget) {
                                    openWebpageUrl(getURL(), null);
                                }
                            }, spannable.getSpanStart(spans[a]), spannable.getSpanEnd(spans[a]), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
            }
            return caption;
        }

        @Override
        public TLRPC.PhotoSize getFileLocation(TLObject media, int[] size) {
            if (media instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) media;
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    size[0] = sizeFull.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    return sizeFull;
                } else {
                    size[0] = -1;
                }
            } else if (media instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) media;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if (thumb != null) {
                    size[0] = thumb.size;
                    if (size[0] == 0) {
                        size[0] = -1;
                    }
                    return thumb;
                }
            }
            return null;
        }

        @Override
        public void updateSlideshowCell(TLRPC.PageBlock currentPageBlock) {
            int count = listView[0].getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView[0].getChildAt(a);
                if (child instanceof ArticleViewer.BlockSlideshowCell) {
                    ArticleViewer.BlockSlideshowCell cell = (ArticleViewer.BlockSlideshowCell) child;
                    int idx = cell.currentBlock.items.indexOf(currentPageBlock);
                    if (idx != -1) {
                        cell.innerListView.setCurrentItem(idx, false);
                        break;
                    }
                }
            }
        }

        @Override
        public Object getParentObject() {
            return page;
        }
    }

    private class PageBlocksPhotoViewerProvider extends PhotoViewer.EmptyPhotoViewerProvider {

        private final int[] tempArr = new int[2];
        private final List<TLRPC.PageBlock> pageBlocks;

        public PageBlocksPhotoViewerProvider(List<TLRPC.PageBlock> pageBlocks) {
            this.pageBlocks = pageBlocks;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            if (index < 0 || index >= pageBlocks.size()) {
                return null;
            }
            ImageReceiver imageReceiver = getImageReceiverFromListView(listView[0], pageBlocks.get(index), tempArr);
            if (imageReceiver == null) {
                return null;
            }
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = tempArr[0];
            object.viewY = tempArr[1];
            object.parentView = listView[0];
            object.imageReceiver = imageReceiver;
            object.thumb = imageReceiver.getBitmapSafe();
            object.radius = imageReceiver.getRoundRadius();
            object.clipTopAddition = currentHeaderHeight;
            return object;
        }

        private ImageReceiver getImageReceiverFromListView(ViewGroup listView, TLRPC.PageBlock pageBlock, int[] coords) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = getImageReceiverView(listView.getChildAt(a), pageBlock, coords);
                if (imageReceiver != null) {
                    return imageReceiver;
                }
            }
            return null;
        }

        private ImageReceiver getImageReceiverView(View view, TLRPC.PageBlock pageBlock, int[] coords) {
            if (view instanceof BlockPhotoCell) {
                BlockPhotoCell cell = (BlockPhotoCell) view;
                if (cell.currentBlock == pageBlock) {
                    view.getLocationInWindow(coords);
                    return cell.imageView;
                }
            } else if (view instanceof BlockVideoCell) {
                BlockVideoCell cell = (BlockVideoCell) view;
                if (cell.currentBlock == pageBlock) {
                    view.getLocationInWindow(coords);
                    return cell.imageView;
                }
            } else if (view instanceof BlockCollageCell) {
                ImageReceiver imageReceiver = getImageReceiverFromListView(((BlockCollageCell) view).innerListView, pageBlock, coords);
                if (imageReceiver != null) {
                    return imageReceiver;
                }
            } else if (view instanceof BlockSlideshowCell) {
                ImageReceiver imageReceiver = getImageReceiverFromListView(((BlockSlideshowCell) view).innerListView, pageBlock, coords);
                if (imageReceiver != null) {
                    return imageReceiver;
                }
            } else if (view instanceof BlockListItemCell) {
                BlockListItemCell blockListItemCell = (BlockListItemCell) view;
                if (blockListItemCell.blockLayout != null) {
                    ImageReceiver imageReceiver = getImageReceiverView(blockListItemCell.blockLayout.itemView, pageBlock, coords);
                    if (imageReceiver != null) {
                        return imageReceiver;
                    }
                }
            } else if (view instanceof BlockOrderedListItemCell) {
                BlockOrderedListItemCell blockOrderedListItemCell = (BlockOrderedListItemCell) view;
                if (blockOrderedListItemCell.blockLayout != null) {
                    ImageReceiver imageReceiver = getImageReceiverView(blockOrderedListItemCell.blockLayout.itemView, pageBlock, coords);
                    if (imageReceiver != null) {
                        return imageReceiver;
                    }
                }
            }
            return null;
        }
    }
}

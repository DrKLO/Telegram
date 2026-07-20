/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_BOTTOM;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_LEFT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_RIGHT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_TOP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.DynamicDrawableSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Property;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
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
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
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
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotGuardHelper;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileStreamLoadOperation;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.WindowVisibilityManager;
import org.telegram.messenger.video.VideoPlayerHolderBase;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.BottomSheetTabDialog;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnchorSpan;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SmoothScroller;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.TableLayout;
import org.telegram.ui.Components.TextPaintImageReceiverSpan;
import org.telegram.ui.Components.TextPaintMarkSpan;
import org.telegram.ui.Components.TextPaintSpan;
import org.telegram.ui.Components.TextPaintUrlSpan;
import org.telegram.ui.Components.TextPaintWebpageUrlSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.WebPlayerView;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.KeyboardNotifier;
import org.telegram.ui.web.AddressBarList;
import org.telegram.ui.web.BookmarksFragment;
import org.telegram.ui.web.BotWebViewContainer;
import org.telegram.ui.bots.ChatAttachAlertBotWebViewLayout;
import org.telegram.ui.web.BrowserHistory;
import org.telegram.ui.web.HistoryFragment;
import org.telegram.ui.web.SearchEngine;
import org.telegram.ui.web.WebActionBar;
import org.telegram.ui.web.WebBrowserSettings;
import org.telegram.ui.web.WebInstantView;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import ru.noties.jlatexmath.JLatexMathDrawable;

public class ArticleViewer extends IArticleViewer implements NotificationCenter.NotificationCenterDelegate {

    public static HashSet<ArticleViewer> activeSheets = new HashSet<>();

    public static final boolean BOTTOM_ACTION_BAR = false;

    public final boolean isSheet;
    public final ArticleViewer.Sheet sheet;

    public ArticleViewer() {
        this.isSheet = false;
        this.sheet = null;
    }

    public ArticleViewer(BaseFragment fragment) {
        this.isSheet = true;
        this.sheet = new Sheet(fragment);
        setParentActivity(fragment.getParentActivity(), fragment);
    }

    private Activity parentActivity;
    private BaseFragment parentFragment;
    private ArrayList<BlockEmbedCell> createdWebViews = new ArrayList<>();

    private View customView;
    private FrameLayout fullscreenVideoContainer;
    private TextureView fullscreenTextureView;
    private AspectRatioFrameLayout fullscreenAspectRatioView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private Object lastInsets;
    private boolean hasCutout;

    private boolean isVisible;
    private boolean collapsed;
    private boolean attachedToWindow;

    private int currentAccount;
    public int getCurrentAccount() {
        return currentAccount;
    }

    private int lastBlockNum = 1;

    private int animationInProgress;
    private Runnable animationEndRunnable;
    private long transitionAnimationStartTime;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);

    public final ArrayList<Object> pagesStack = new ArrayList<>();
    public boolean isLastArticle() {
        if (pagesStack.isEmpty()) return false;
        final Object last = pagesStack.get(pagesStack.size() - 1);
        if (last instanceof TLRPC.WebPage) {
            final TLRPC.WebPage webpage = (TLRPC.WebPage) last;
            if (webpage.cached_page != null && webpage.cached_page.local != null) return false;
            return true;
        }
        return false;
    }

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowView windowView;
    private FrameLayout containerView;
    private WebActionBar actionBar;
    private AddressBarList addressBarList;
//    private FrameLayout headerView;
//    private SimpleTextView titleTextView;
//    private LineProgressView lineProgressView;
    private Runnable lineProgressTickRunnable;
//    private ImageView backButton;
//    private ActionBarMenuItem menuButton;
//    private ActionBarMenuItem collapseButton;
//    private FrameLayout menuContainer;
    private ContextProgressView progressView;
//    private BackDrawable backDrawable;
    private Dialog visibleDialog;
    private Paint backgroundPaint;
    private Drawable layerShadowDrawable;
    private Paint scrimPaint;
    private AnimatorSet progressViewAnimation;

    private AnimatorSet runAfterKeyboardClose;
    private boolean keyboardVisible;

//    private FrameLayout searchContainer;
//    private ImageView clearButton;
//    private EditTextBoldCursor searchField;
//    private boolean animateClear = true;
//    private boolean ignoreOnTextChange;
//    private View searchShadow;

    private float searchPanelTranslation;
    private FrameLayout searchPanel;
    private ImageView searchUpButton;
    private ImageView searchDownButton;
    private AnimatedTextView searchCountText;

    private FrameLayout bulletinContainer;
    public PageLayout[] pages;
//    private RecyclerListView[] listView;
//    private LinearLayoutManager[] layoutManager;
//    private WebpageAdapter[] adapter;

    private AnimatorSet pageSwitchAnimation;

    private Paint headerPaint = new Paint();
    private Paint statusBarPaint = new Paint();
    private Paint navigationBarPaint = new Paint();
    private Paint headerProgressPaint = new Paint();

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

    private Browser.Progress loadingProgress;

    private int anchorsOffsetMeasuredWidth;

    TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
    TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelperBottomSheet;

    PinchToZoomHelper pinchToZoomHelper;

    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker(new int[]{
            NotificationCenter.dialogsNeedReload,
            NotificationCenter.closeChats
    });

    private final String BOTTOM_SHEET_VIEW_TAG = "bottomSheet";

    public static TLRPC.WebPage debugCopiedRichMessageWebPage;

    @SuppressLint("StaticFieldLeak")
    private static volatile ArticleViewer Instance = null;
    private Drawable chat_redLocationIcon;

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

    public static ArticleViewer makeSheet(BaseFragment fragment) {
        return new ArticleViewer(fragment);
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    private FontCell[] fontCells = new FontCell[2];

    public static class TL_pageBlockRelatedArticlesChild extends TL_iv.PageBlock {
        public TL_iv.pageBlockRelatedArticles parent;
        public int num;
    }

    private static class TL_pageBlockRelatedArticlesShadow extends TL_iv.PageBlock {
        private TL_iv.pageBlockRelatedArticles parent;
    }

    private static class TL_pageBlockDetailsChild extends TL_iv.PageBlock {
        private TL_iv.PageBlock parent;
        private TL_iv.PageBlock block;
    }

    private static class TL_pageBlockDetailsBottom extends TL_iv.PageBlock {
        private TL_iv.pageBlockDetails parent;
    }

    public static class TL_pageBlockListParent extends TL_iv.PageBlock {
        public TL_iv.pageBlockList pageBlockList;
        public ArrayList<TL_pageBlockListItem> items = new ArrayList<>();
        public int maxNumWidth;
        public int lastMaxNumCalcWidth;
        public int lastFontSize;
        public int level;
    }

    public static class TL_pageBlockListItem extends TL_iv.PageBlock {
        public boolean isCheckbox, checked;
        public TL_pageBlockListParent parent;
        public TL_iv.PageBlock blockItem;
        public TL_iv.RichText textItem;
        public String num;
        public DrawingText numLayout;
        public int index = Integer.MAX_VALUE;
    }

    public static class TL_pageBlockOrderedListParent extends TL_iv.PageBlock {
        public TL_iv.pageBlockOrderedList pageBlockOrderedList;
        public ArrayList<TL_pageBlockOrderedListItem> items = new ArrayList<>();
        public int maxNumWidth;
        public int lastMaxNumCalcWidth;
        public int lastFontSize;
        public int level;
    }

    public static class TL_pageBlockOrderedListItem extends TL_iv.PageBlock {
        public boolean isCheckbox, checked;
        public TL_pageBlockOrderedListParent parent;
        public TL_iv.PageBlock blockItem;
        public TL_iv.RichText textItem;
        public String num;
        public DrawingText numLayout;
        public int index = Integer.MAX_VALUE;
    }

    private static class TL_pageBlockEmbedPostCaption extends TL_iv.pageBlockEmbedPost {
        private TL_iv.pageBlockEmbedPost parent;
    }

    public static class DrawingText implements TextSelectionHelper.TextLayoutBlock, MultiLayoutTypingAnimator.Block, TableLayout.CellText {
        private final IArticleViewer parent;
        private View latestParentView;

        private boolean isDrawing;
        public StaticLayout textLayout;
        public LinkPath textPath;
        public LinkPath markPath;
        public LinkPath searchPath;
        public int searchIndex = -1;
        public TL_iv.PageBlock parentBlock;
        public Object parentText;
        public int x;
        public int y;
        public int row;
        public CharSequence prefix;
        private CharSequence accessibilityText;
        private int boundLeft = -1, boundRight = -1, lastLineBoundRight = -1;

        public int emojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
        public AnimatedEmojiSpan.EmojiGroupedSpans animatedEmojiStack;
        public List<SpoilerEffect> spoilers;
        public Stack<SpoilerEffect> spoilersPool;
        public AtomicReference<Layout> spoilersPatchedLayout;
        private boolean attached;
        private View attachedToView;

        public MultiLayoutTypingAnimator typingAnimator;

        public DrawingText(IArticleViewer parent) {
            this.parent = parent;
        }

        public void attach(View view) {
            attachedToView = view;
            attached = true;
            if (textLayout != null) {
                animatedEmojiStack = AnimatedEmojiSpan.update(emojiCacheType, view, false, animatedEmojiStack, textLayout);
            }
        }

        public void detach(View view) {
            attached = false;
            if (view == null) {
                view = attachedToView;
            }
            AnimatedEmojiSpan.release(view, animatedEmojiStack);
            attachedToView = null;
        }

        public boolean isAttached() {
            return attached;
        }

        @Override
        public Layout getLayout() {
            return textLayout;
        }

        @Override
        public View getParentView() {
            return attachedToView != null ? attachedToView : latestParentView;
        }

        public void draw(Canvas canvas, View view) {
            isDrawing = true;
            latestParentView = view;

            final boolean typing = typingAnimator != null && typingAnimator.isRunning()
                    && typingAnimator.indexOf(this) >= 0;
            if (typing && !typingAnimator.needDraw(this)) {
                isDrawing = false;
                return;
            }

            if (!parent.searchResults.isEmpty()) {
                final SearchResult result = parent.searchResults.get(parent.currentSearchIndex);
                if (result.block == parentBlock && (result.text == parentText || result.text instanceof String && parentText == null)) {
                    if (searchIndex != result.index) {
                        searchPath = new LinkPath(true);
                        searchPath.setAllowReset(false);
                        searchPath.setCurrentLayout(textLayout, result.index, 0);
                        searchPath.setBaselineShift(0);
                        textLayout.getSelectionPath(result.index, result.index + parent.searchText.length(), searchPath);
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
            if (parent.links.draw(canvas, this)) {
                view.invalidate();
            }
            if (parent.pressedLinkOwnerLayout == this && parent.pressedLink == null && parent.drawBlockSelection) {
                float width;
                float x;
                if (getLineCount() == 1) {
                    width = getLineWidth(0);
                    x = getLineLeft(0);
                } else {
                    width = getWidth();
                    x = 0;
                }
                canvas.drawRect(-dp(2) + x, 0, x + width + dp(2), getHeight(), urlPaint);
            }
            if (typing && typingAnimator.isFadeBlock(this)) {
                MultiLayoutTypingAnimator.drawLayoutWithLastLineFade(
                    canvas, textLayout,
                    typingAnimator.getFadeLineIndex(this),
                    typingAnimator.getFadeXPosition(this)
                );
            } else if (spoilers != null && !spoilers.isEmpty()) {
                SpoilerEffect.renderWithRipple(view, false, textLayout.getPaint().getColor(), 0, spoilersPatchedLayout, 0, textLayout, spoilers, canvas, false);
            } else {
                textLayout.draw(canvas);
            }
            isDrawing = false;
        }

        public void invalidateParent() {
            if (!isDrawing && latestParentView != null) {
                latestParentView.invalidate();
            }
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

        public int getBoundLeft() {
            if (boundLeft != -1) return boundLeft;
            boundLeft = textLayout.getWidth();
            for (int line = 0; line < textLayout.getLineCount(); ++line) {
                boundLeft = Math.min(boundLeft, (int) textLayout.getLineLeft(line));
            }
            return boundLeft;
        }

        public int getBoundRight() {
            if (boundRight != -1) return boundRight;
            boundRight = 0;
            for (int line = 0; line < textLayout.getLineCount(); ++line) {
                boundRight = Math.max(boundRight, (int) textLayout.getLineRight(line));
            }
            return boundRight;
        }

        public int getLastLineBoundRight() {
            if (lastLineBoundRight != -1) return lastLineBoundRight;
            lastLineBoundRight = 0;
            if (textLayout.getLineCount() > 0) {
                lastLineBoundRight = Math.max(lastLineBoundRight, (int) textLayout.getLineRight(textLayout.getLineCount() - 1));
            }
            return lastLineBoundRight;
        }

        public int getHeight() {
            return textLayout.getHeight();
        }

        public int getWidth() {
            return textLayout.getWidth();
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
        public void setX(int x) {
            this.x = x;
        }

        @Override
        public void setY(int y) {
            this.y = y;
        }

        @Override
        public void setRow(int row) {
            this.row = row;
        }

        @Override
        public CharSequence getPrefix() {
            return prefix;
        }
    }

    public static CharSequence buildAccessibilityText(IArticleViewer parent, WebpageAdapter adapter, DrawingText layout) {
        if (layout == null || layout.textLayout == null) return null;
        if (layout.accessibilityText != null) return layout.accessibilityText;
        CharSequence text = layout.textLayout.getText();
        if (!(text instanceof Spannable)) {
            return text;
        }
        Spannable buffer = (Spannable) text;
        TextPaintUrlSpan[] spans = buffer.getSpans(0, buffer.length(), TextPaintUrlSpan.class);
        if (spans == null || spans.length == 0) {
            return text;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(buffer);
        for (TextPaintUrlSpan span : spans) {
            int start = sb.getSpanStart(span);
            int end = sb.getSpanEnd(span);
            if (start < 0 || end <= start) continue;
            final TextPaintUrlSpan urlSpan = span;
            sb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    parent.handleLinkClick(adapter, urlSpan);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        layout.accessibilityText = sb;
        return sb;
    }

    public void handleLinkClick(WebpageAdapter adapter, TextPaintUrlSpan span) {
        if (span == null) return;
        final String url = span.getUrl();
        if (url == null) return;
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
            if (index == 0 || url.toLowerCase().contains(webPageUrl)) {
//                if (TextUtils.isEmpty(anchor)) {
//                    pages[0].layoutManager.scrollToPositionWithOffset(0, 0);
//                    checkScrollAnimated();
//                } else {
                    scrollToAnchor(anchor, true);
//                }
                isAnchor = true;
            }
        } else {
            anchor = null;
        }
        if (!isAnchor) {
            openWebpageUrl(url, anchor, pressedLinkOwnerLayout == null ? null : makeProgress(pressedLink, pressedLinkOwnerLayout));
        }
    }

    public static CharSequence appendA11yLabel(CharSequence content, int labelResId) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (content != null) sb.append(content);
        if (sb.length() > 0) sb.append(", ");
        sb.append(getString(labelResId));
        return sb;
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
            textPaint.setTextSize(dp(16));

            sizeBar = new SeekBarView(context, getResourcesProvider());
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endFontSize - startFontSize + 1);
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
                        pages[0].getAdapter().searchTextOffset.clear();
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
            textPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + SharedConfig.ivFontSize, getMeasuredWidth() - dp(39), dp(28), textPaint);
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

    public class FontCell extends FrameLayout {

        private TextView textView;
        private RadioButton radioButton;

        public FontCell(Context context) {
            super(context);

            setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 2));

            radioButton = new RadioButton(context);
            radioButton.setSize(dp(20));
            radioButton.setColor(getThemedColor(Theme.key_dialogRadioBackground), getThemedColor(Theme.key_dialogRadioBackgroundChecked));
            addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 22), 13, (LocaleController.isRTL ? 22 : 0), 0));

            textView = new TextView(context);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 17 + 45), 0, (LocaleController.isRTL ? 17 + 45 : 17), 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
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
        private int startedTrackingPointerId;
        private boolean maybeStartTracking;
        private boolean startedTracking;
        private boolean movingPage;
        private boolean openingPage;
        private int startMovingHeaderHeight;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker tracker;
        private float innerTranslationX;
        private float alpha = 1f;

        private int bX, bWidth, bY, bHeight;

        public WindowView(Context context) {
            super(context);
        }

        @Override
        public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
            if (sheet != null) return super.dispatchApplyWindowInsets(insets);
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
            if (lastInsets != null) {
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
//            menuButton.setAdditionalYOffset(-(currentHeaderHeight - dp(56)) / 2 + (Build.VERSION.SDK_INT < 21 ? AndroidUtilities.statusBarHeight : 0));
//            collapseButton.setAdditionalYOffset(-(currentHeaderHeight - dp(56)) / 2 + (Build.VERSION.SDK_INT < 21 ? AndroidUtilities.statusBarHeight : 0));
            if (sheet == null) {
                keyboardVisible = heightSize < AndroidUtilities.displaySize.y - dp(100);
            }
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

            if (textSelectionHelper.isInSelectionMode() && textSelectionHelper.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                return true;
            }

            if (selectionOverlay.checkOnTap(ev)) {
                if (pages != null && pages[0] != null && pages[0].isWeb() && pagesStack != null && pagesStack.size() <= 1) {
                    ev.setAction(MotionEvent.ACTION_UP);
                } else {
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
            }

            if (ev.getAction() == MotionEvent.ACTION_DOWN && textSelectionHelper.isInSelectionMode() && (ev.getY() < containerView.getTop() || ev.getY() > containerView.getBottom())) {
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
            int width = right - left;
            if (anchorsOffsetMeasuredWidth != width) {
                for (int i = 0; i < pages.length; i++) {
                    for (HashMap.Entry<String, Integer> entry : pages[i].adapter.anchorsOffset.entrySet()) {
                        entry.setValue(-1);
                    }
                }
                anchorsOffsetMeasuredWidth = width;
            }
            int x;
            int y = 0;
            if (lastInsets != null) {
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
            if (videoPlayer != null) {
                videoPlayer.release(null);
                videoPlayer = null;
            }
            currentPlayer = null;
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
            if (activityVisibilityController != null) {
                activityVisibilityController.setHidden(!(!isVisible || alpha != 1.0f || innerTranslationX != 0));
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

                final float alpha = Math.max(0, Math.min((width - translationX) / (float) dp(20), 1.0f));
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
            if (pagesStack.size() > 1 && (actionBar == null || !actionBar.isSearching() && !actionBar.isAddressing())) {
                movingPage = true;
                startMovingHeaderHeight = currentHeaderHeight;
                pages[1].setVisibility(VISIBLE);
                pages[1].setAlpha(1.0f);
                pages[1].setTranslationX(0.0f);
                pages[0].setBackgroundColor(sheet == null ? 0 : backgroundPaint.getColor());
                updateInterfaceForCurrentPage(pagesStack.get(pagesStack.size() - 2), true, -1);
                if (containerView.indexOfChild(pages[0]) < containerView.indexOfChild(pages[1])) {
                    int index = containerView.indexOfChild(pages[0]);
                    containerView.removeView(pages[1]);
                    containerView.addView(pages[1], index);
                }
            } else {
                movingPage = false;
            }
            cancelCheckLongPress();
        }

        private boolean lastWebviewAllowedScroll;
        public boolean handleTouchEvent(MotionEvent event) {
            if (pageSwitchAnimation == null && !closeAnimationInProgress && fullscreenVideoContainer.getVisibility() != VISIBLE && !textSelectionHelper.isInSelectionMode()) {
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
                    lastWebviewAllowedScroll = pages[0] == null || !pages[0].isWeb() || pages[0].swipeContainer.allowingScroll(true) && !pages[0].swipeContainer.isScrolling;
                    if ((sheet == null || !sheet.nestedVerticalScroll) && maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy && lastWebviewAllowedScroll) {
                        prepareForMoving(event);
                    } else if (startedTracking) {
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                        if (movingPage && pages[0] != null) {
                            pages[0].setTranslationX(dx);
                        } else if (sheet != null) {
                            sheet.setBackProgress(dx / (float) getWidth());
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
                    if ((sheet == null || !sheet.nestedVerticalScroll) && !startedTracking && velX >= 3500 && velX > Math.abs(velY)) {
                        prepareForMoving(event);
                    }
                    if (startedTracking) {
                        View movingView = movingPage ? pages[0] : containerView;
                        float x = !movingPage && sheet != null ? sheet.getBackProgress() * sheet.windowView.getWidth() : movingView.getX();

                        final boolean backAnimation = x < movingView.getMeasuredWidth() * .3f && (velX < 2500 || velX < velY) || !lastWebviewAllowedScroll;
                        float distToMove;
                        AnimatorSet animatorSet = new AnimatorSet();
                        if (!backAnimation) {
                            distToMove = movingView.getMeasuredWidth() - x;
                            if (movingPage) {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, movingView.getMeasuredWidth())
                                );
                            } else {
                                if (sheet != null) {
                                    animatorSet.playTogether(
                                        sheet.animateBackProgressTo(1f)
                                    );
                                } else {
                                    animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, movingView.getMeasuredWidth()),
                                        ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, (float) movingView.getMeasuredWidth())
                                    );
                                }
                            }
                        } else {
                            distToMove = x;
                            if (movingPage) {
                                animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, 0)
                                );
                            } else {
                                if (sheet != null) {
                                    animatorSet.playTogether(
                                        sheet.animateBackProgressTo(0)
                                    );
                                } else {
                                    animatorSet.playTogether(
                                        ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0),
                                        ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, 0.0f)
                                    );
                                }
                            }
                        }

                        animatorSet.setDuration(Math.max((int) (420.0f / movingView.getMeasuredWidth() * distToMove), 250));
                        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                if (movingPage) {
                                    Object removed = null;
                                    pages[0].setBackgroundDrawable(null);
                                    if (!backAnimation) {
                                        PageLayout pageToUpdate = pages[1];
                                        pages[1] = pages[0];
                                        pages[0] = pageToUpdate;
                                        actionBar.swap();
                                        page0Background.set(pages[0].getBackgroundColor(), true);
                                        page1Background.set(pages[1].getBackgroundColor(), true);
                                        if (sheet != null) {
                                            sheet.updateLastVisible();
                                        }

                                        removed = pagesStack.remove(pagesStack.size() - 1);

                                        textSelectionHelper.setParentView(pages[0].listView);
                                        textSelectionHelper.layoutManager = pages[0].layoutManager;
                                        textSelectionHelper.clear(true);

                                        updateTitle(false);
                                        updatePages();
                                    }
                                    pages[1].cleanup();
                                    pages[1].setVisibility(GONE);
                                    if (removed instanceof CachedWeb) {
                                        ((CachedWeb) removed).destroy();
                                    }
                                    if (removed instanceof TLRPC.WebPage) {
                                        WebInstantView.recycle((TLRPC.WebPage) removed);
                                    }
                                } else {
                                    if (!backAnimation) {
                                        if (sheet != null) {
                                            sheet.release();
                                            destroy();
                                        } else {
                                            saveCurrentPagePosition();
                                            onClosed();
                                        }
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
                    if (textSelectionHelper != null && !textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.clear();
                    }
                }
                return startedTracking && lastWebviewAllowedScroll;
            }
            return false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (lastInsets == null) {
                if (bWidth != 0 && bHeight != 0) {
                    blackPaint.setAlpha((int) (255 * windowView.getAlpha()));
                    if (bX == 0 && bY == 0) {
                        canvas.drawRect(bX, bY, bX + bWidth, bY + bHeight, blackPaint);
                    } else {
                        canvas.drawRect(bX - getTranslationX(), bY, bX + bWidth - getTranslationX(), bY + bHeight, blackPaint);
                    }
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (sheet == null) {
                int w = getMeasuredWidth();
                int h = getMeasuredHeight();
                canvas.drawRect(innerTranslationX, 0, w, h, backgroundPaint);
                if (lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    canvas.drawRect(innerTranslationX, 0, w, insets.getSystemWindowInsetTop(), statusBarPaint);
                    if (hasCutout) {
                        int left = insets.getSystemWindowInsetLeft();
                        if (left != 0) {
                            canvas.drawRect(0, 0, left, h, statusBarPaint);
                        }
                        int right = insets.getSystemWindowInsetRight();
                        if (right != 0) {
                            canvas.drawRect(w - right, 0, w, h, statusBarPaint);
                        }
                    }
                    canvas.drawRect(0, h - insets.getStableInsetBottom(), w, h, navigationBarPaint);
                }
            }
        }

        @Keep
        @Override
        public void setAlpha(float value) {
            backgroundPaint.setAlpha((int) (255 * value));
            statusBarPaint.setAlpha((int) (255 * value));
            alpha = value;
            if (activityVisibilityController != null) {
                activityVisibilityController.setHidden(!(!isVisible || alpha != 1.0f || innerTranslationX != 0));
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
                if (actionBar.searchEditText.isFocused()) {
                    actionBar.searchEditText.clearFocus();
                    AndroidUtilities.hideKeyboard(actionBar.searchEditText);
                } else if (actionBar.addressEditText.isFocused()) {
                    actionBar.addressEditText.clearFocus();
                    AndroidUtilities.hideKeyboard(actionBar.addressEditText);
                } else if (keyboardVisible) {
                    AndroidUtilities.hideKeyboard(this);
                } else if (pages[0] != null && pages[0].isWeb() && pages[0].getWebView() != null && pages[0].getWebView().canGoBack()) {
                    pages[0].getWebView().goBack();
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
                    try {
                        windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                    showCopyPopup(pressedLink.getSpan().getUrl());
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
                    if (textSelectionHelper.isInSelectionMode()) {
                        try {
                            windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                    }
                } else if (pressedLinkOwnerLayout != null && pressedLinkOwnerView != null) {
                    try {
                        windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}

                    int[] location = new int[2];
                    pressedLinkOwnerView.getLocationInWindow(location);
                    int y = location[1] + pressedLayoutY - dp(54);
                    if (y < 0) {
                        y = 0;
                    }
                    pressedLinkOwnerView.invalidate();
                    drawBlockSelection = true;
                    showPopup(pressedLinkOwnerView, Gravity.TOP, 0, y);
                    pages[0].listView.setLayoutFrozen(true);
                    pages[0].listView.setLayoutFrozen(false);
                }
            }
        }
    }

    public static void createPaint(IArticleViewer parent, boolean update) {
        if (quoteLinePaint == null) {
            quoteLinePaint = new Paint();

            preformattedBackgroundPaint = new Paint();

            tableLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tableLinePaint.setStyle(Paint.Style.STROKE);
            tableLinePaint.setStrokeWidth(dp(1));

            tableHalfLinePaint = new Paint();
            tableHalfLinePaint.setStyle(Paint.Style.STROKE);
            tableHalfLinePaint.setStrokeWidth(dp(1) / 2.0f);

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

        int color2 = parent.getThemedColor(Theme.key_windowBackgroundWhite);
        float lightness = (0.2126f * Color.red(color2) + 0.7152f * Color.green(color2) + 0.0722f * Color.blue(color2)) / 255.0f;
        webpageSearchPaint.setColor(lightness <= 0.705f ? 0xffd1982e : 0xffffe669);
        webpageUrlPaint.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
        webpageUrlPaint.setPathEffect(LinkPath.getRoundedEffect());
        urlPaint.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
        urlPaint.setPathEffect(LinkPath.getRoundedEffect());
        tableHalfLinePaint.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteInputField));
        tableLinePaint.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteInputField));

        photoBackgroundPaint.setColor(0x0f000000);
        dividerPaint.setColor(parent.getThemedColor(Theme.key_divider));
        webpageMarkPaint.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
        webpageMarkPaint.setPathEffect(LinkPath.getRoundedEffect());

        int color = parent.getThemedColor(Theme.key_switchTrack);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        tableStripPaint.setColor(Color.argb(20, r, g, b));
        tableHeaderPaint.setColor(Color.argb(34, r, g, b));

        color = parent.getThemedColor(Theme.key_windowBackgroundWhiteLinkSelection);
        r = Color.red(color);
        g = Color.green(color);
        b = Color.blue(color);
        preformattedBackgroundPaint.setColor(Color.argb(20, r, g, b));

        quoteLinePaint.setColor(parent.getThemedColor(Theme.key_chat_inReplyLine));
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
        String formattedUrl = urlFinal;
        try {
            formattedUrl = URLDecoder.decode(urlFinal.replaceAll("\\+", "%2b"), "UTF-8");
        } catch (Exception e) {
            FileLog.e(e);
        }
        builder.setTitle(formattedUrl);
        builder.setTitleMultipleLines(true);
        builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
            if (parentActivity == null || pages[0].adapter.currentPage == null) {
                return;
            }
            if (which == 0) {
                int index;
                if ((index = urlFinal.lastIndexOf('#')) != -1) {
                    String webPageUrl;
                    if (!TextUtils.isEmpty(pages[0].adapter.currentPage.cached_page.url)) {
                        webPageUrl = pages[0].adapter.currentPage.cached_page.url.toLowerCase();
                    } else {
                        webPageUrl = pages[0].adapter.currentPage.url.toLowerCase();
                    }
                    String anchor;
                    try {
                        anchor = URLDecoder.decode(urlFinal.substring(index + 1), "UTF-8");
                    } catch (Exception ignore) {
                        anchor = "";
                    }
                    if (urlFinal.toLowerCase().contains(webPageUrl)) {
                        if (TextUtils.isEmpty(anchor)) {
                            pages[0].layoutManager.scrollToPositionWithOffset(sheet != null && sheet.halfSize() ? 1 : 0, sheet != null ? dp(56 - 24) : 0);
                            checkScrollAnimated();
                        } else {
                            scrollToAnchor(anchor, true);
                        }
                        return;
                    }
                }
                Browser.openUrl(parentActivity, urlFinal);
            } else if (which == 1) {
                String url = urlFinal;
                if (url == null) return;
                if (url.startsWith("mailto:")) {
                    url = url.substring(7);
                } else if (url.startsWith("tel:")) {
                    url = url.substring(4);
                }
                AndroidUtilities.addToClipboard(url);
            }
        });
        builder.setOnPreDismissListener(di -> links.clear());
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
            popupLayout.setPadding(dp(1), dp(1), dp(1), dp(1));
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
            popupLayout.setShownFromBottom(false);

            deleteView = new TextView(parentActivity);
            deleteView.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 2));
            deleteView.setGravity(Gravity.CENTER_VERTICAL);
            deleteView.setPadding(dp(20), 0, dp(20), 0);
            deleteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            deleteView.setTypeface(AndroidUtilities.bold());
            deleteView.setText(LocaleController.getString(R.string.Copy).toUpperCase());
            deleteView.setOnClickListener(v -> {
                if (pressedLinkOwnerLayout != null) {
                    AndroidUtilities.addToClipboard(pressedLinkOwnerLayout.getText());
                    if (AndroidUtilities.shouldShowClipboardToast()) {
                        Toast.makeText(parentActivity, LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
                    }
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

        deleteView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        if (popupLayout != null) {
            popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
        }

        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setFocusable(true);
        popupWindow.showAtLocation(parent, gravity, x, y);
        popupWindow.startAnimation();
    }

    private TL_iv.RichText getBlockCaption(TL_iv.PageBlock block, int type) {
        if (type == 2) {
            TL_iv.RichText text1 = getBlockCaption(block, 0);
            if (text1 instanceof TL_iv.textEmpty) {
                text1 = null;
            }
            TL_iv.RichText text2 = getBlockCaption(block, 1);
            if (text2 instanceof TL_iv.textEmpty) {
                text2 = null;
            }
            if (text1 != null && text2 == null) {
                return text1;
            } else if (text1 == null && text2 != null) {
                return text2;
            } else if (text1 != null && text2 != null) {
                TL_iv.textPlain text3 = new TL_iv.textPlain();
                text3.text = " ";

                TL_iv.textConcat textConcat = new TL_iv.textConcat();
                textConcat.texts.add(text1);
                textConcat.texts.add(text3);
                textConcat.texts.add(text2);
                return textConcat;
            } else {
                return null;
            }
        }
        if (block instanceof TL_iv.pageBlockEmbedPost) {
            TL_iv.pageBlockEmbedPost blockEmbedPost = (TL_iv.pageBlockEmbedPost) block;
            if (type == 0) {
                return blockEmbedPost.caption.text;
            } else if (type == 1) {
                return blockEmbedPost.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockSlideshow) {
            TL_iv.pageBlockSlideshow pageBlockSlideshow = (TL_iv.pageBlockSlideshow) block;
            if (type == 0) {
                return pageBlockSlideshow.caption.text;
            } else if (type == 1) {
                return pageBlockSlideshow.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockPhoto) {
            TL_iv.pageBlockPhoto pageBlockPhoto = (TL_iv.pageBlockPhoto) block;
            if (type == 0) {
                return pageBlockPhoto.caption.text;
            } else if (type == 1) {
                return pageBlockPhoto.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockCollage) {
            TL_iv.pageBlockCollage pageBlockCollage = (TL_iv.pageBlockCollage) block;
            if (type == 0) {
                return pageBlockCollage.caption.text;
            } else if (type == 1) {
                return pageBlockCollage.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockEmbed) {
            TL_iv.pageBlockEmbed pageBlockEmbed = (TL_iv.pageBlockEmbed) block;
            if (type == 0) {
                return pageBlockEmbed.caption.text;
            } else if (type == 1) {
                return pageBlockEmbed.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockBlockquote) {
            TL_iv.pageBlockBlockquote pageBlockBlockquote = (TL_iv.pageBlockBlockquote) block;
            return pageBlockBlockquote.caption;
        } else if (block instanceof TL_iv.pageBlockVideo) {
            TL_iv.pageBlockVideo pageBlockVideo = (TL_iv.pageBlockVideo) block;
            if (type == 0) {
                return pageBlockVideo.caption.text;
            } else if (type == 1) {
                return pageBlockVideo.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockPullquote) {
            TL_iv.pageBlockPullquote pageBlockPullquote = (TL_iv.pageBlockPullquote) block;
            return pageBlockPullquote.caption;
        } else if (block instanceof TL_iv.pageBlockAudio) {
            TL_iv.pageBlockAudio pageBlockAudio = (TL_iv.pageBlockAudio) block;
            if (type == 0) {
                return pageBlockAudio.caption.text;
            } else if (type == 1) {
                return pageBlockAudio.caption.credit;
            }
        } else if (block instanceof TL_iv.pageBlockCover) {
            TL_iv.pageBlockCover pageBlockCover = (TL_iv.pageBlockCover) block;
            return getBlockCaption(pageBlockCover.cover, type);
        } else if (block instanceof TL_iv.pageBlockMap) {
            TL_iv.pageBlockMap pageBlockMap = (TL_iv.pageBlockMap) block;
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

    public static boolean isListItemBlock(TL_iv.PageBlock block) {
        return block instanceof TL_pageBlockListItem || block instanceof TL_pageBlockOrderedListItem;
    }

    public static boolean isHeadingBlock(TL_iv.PageBlock block) {
        return (
            block instanceof TL_iv.pageBlockHeading1 ||
            block instanceof TL_iv.pageBlockHeading2 ||
            block instanceof TL_iv.pageBlockHeading3 ||
            block instanceof TL_iv.pageBlockHeading4 ||
            block instanceof TL_iv.pageBlockHeading5 ||
            block instanceof TL_iv.pageBlockHeading6
        );
    }

    private TL_iv.PageBlock getLastNonListPageBlock(TL_iv.PageBlock block) {
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
        TL_iv.PageBlock parentBlock = getLastNonListPageBlock(child.parent);
        if (parentBlock instanceof TL_iv.pageBlockDetails) {
            TL_iv.pageBlockDetails blockDetails = (TL_iv.pageBlockDetails) parentBlock;
            if (!blockDetails.open) {
                blockDetails.open = true;
                return true;
            }
            return false;
        } else if (parentBlock instanceof TL_pageBlockDetailsChild) {
            TL_pageBlockDetailsChild parent = (TL_pageBlockDetailsChild) parentBlock;
            parentBlock = getLastNonListPageBlock(parent.block);
            boolean opened = false;
            if (parentBlock instanceof TL_iv.pageBlockDetails) {
                TL_iv.pageBlockDetails blockDetails = (TL_iv.pageBlockDetails) parentBlock;
                if (!blockDetails.open) {
                    blockDetails.open = true;
                    opened = true;
                }
            }
            return openAllParentBlocks(parent) || opened;
        }
        return false;
    }

    private TL_iv.PageBlock fixListBlock(TL_iv.PageBlock parentBlock, TL_iv.PageBlock childBlock) {
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

    private TL_iv.PageBlock wrapInTableBlock(TL_iv.PageBlock parentBlock, TL_iv.PageBlock childBlock) {
        if (parentBlock instanceof TL_pageBlockListItem) {
            TL_pageBlockListItem parent = (TL_pageBlockListItem) parentBlock;

            TL_pageBlockListItem item = new TL_pageBlockListItem();
            item.isCheckbox = parent.isCheckbox;
            item.checked = parent.checked;
            item.parent = parent.parent;
            item.blockItem = wrapInTableBlock(parent.blockItem, childBlock);
            return item;
        } else if (parentBlock instanceof TL_pageBlockOrderedListItem) {
            TL_pageBlockOrderedListItem parent = (TL_pageBlockOrderedListItem) parentBlock;

            TL_pageBlockOrderedListItem item = new TL_pageBlockOrderedListItem();
            item.isCheckbox = parent.isCheckbox;
            item.checked = parent.checked;
            item.parent = parent.parent;
            item.blockItem = wrapInTableBlock(parent.blockItem, childBlock);
            return item;
        }
        return childBlock;
    }

    public PageLayout getCurrentPageLayout() {
        return pages[0];
    }

    private void updateInterfaceForCurrentPage(Object obj, boolean previous, int order) {
        if (obj == null || !(
            obj instanceof TLRPC.WebPage && ((TLRPC.WebPage) obj).cached_page != null ||
            obj instanceof CachedWeb
        )) {
            return;
        }
        if (!previous && order != 0) {
            PageLayout pageToUpdate = pages[1];
            pages[1] = pages[0];
            pages[0] = pageToUpdate;
            actionBar.swap();
            page0Background.set(pages[0].getBackgroundColor(), true);
            page1Background.set(pages[1].getBackgroundColor(), true);
            if (sheet != null) {
                sheet.updateLastVisible();
            }

            int index1 = containerView.indexOfChild(pages[0]);
            int index2 = containerView.indexOfChild(pages[1]);
            if (order == 1) {
                if (index1 < index2) {
                    containerView.removeView(pages[0]);
                    containerView.addView(pages[0], index2);
                }
            } else {
                if (index2 < index1) {
                    containerView.removeView(pages[0]);
                    containerView.addView(pages[0], index1);
                }
            }

            pageSwitchAnimation = new AnimatorSet();
            pages[0].setVisibility(View.VISIBLE);
            int index = order == 1 ? 0 : 1;
            pages[index].setBackgroundColor(sheet == null ? 0 : backgroundPaint.getColor());
            pages[index].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (order == 1) {
                pages[0].setTranslationX(AndroidUtilities.displaySize.x);
                pageSwitchAnimation.playTogether(
                    ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, AndroidUtilities.displaySize.x, 0)
                );
            } else if (order == -1) {
//                pages[0].setAlpha(1.0f);
                pages[0].setTranslationX(0.0f);
                pageSwitchAnimation.playTogether(
                    ObjectAnimator.ofFloat(pages[1], View.TRANSLATION_X, 0, AndroidUtilities.displaySize.x)//,
//                    ObjectAnimator.ofFloat(pages[1], View.ALPHA, 1.0f, 0.0f)
                );
            }
            pageSwitchAnimation.setDuration(320);
            pageSwitchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            pageSwitchAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pages[1].cleanup();
                    pages[1].setVisibility(View.GONE);
                    textSelectionHelper.setParentView(pages[0].listView);
                    textSelectionHelper.layoutManager = pages[0].layoutManager;
                    pages[index].setBackgroundDrawable(null);
                    pages[index].setLayerType(View.LAYER_TYPE_NONE, null);
                    pageSwitchAnimation = null;
                    windowView.openingPage = false;
                }
            });
            windowView.openingPage = true;
            actionBar.setMenuColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getBackgroundColor() : getThemedColor(Theme.key_iv_background));
            actionBar.setColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getActionBarColor() : getThemedColor(Theme.key_iv_background), true);
            actionBar.setIsTonsite(pages[0] != null && pages[0].isTonsite());
            actionBar.setIsLocal(pages[0] != null && pages[0].isLocal());
            AndroidUtilities.runOnUIThread(pageSwitchAnimation::start);
        }

        final int index = previous ? 1 : 0;
        if (!previous) {
            textSelectionHelper.clear(true);
        }
        final WebpageAdapter adapter = pages[index].adapter;

        Object page = previous ? pagesStack.get(pagesStack.size() - 2) : obj;
        pages[index].cleanup();
        if (page instanceof TLRPC.WebPage) {
            TLRPC.WebPage webPage = (TLRPC.WebPage) page;

            pages[index].setWeb(null);
            pages[index].setType(PageLayout.TYPE_ARTICLE);
            adapter.isRtl = webPage.cached_page.rtl;
            adapter.currentPage = webPage;

            int numBlocks = 0;
            int count = webPage.cached_page.blocks.size();
            for (int a = 0; a < count; a++) {
                TL_iv.PageBlock block = webPage.cached_page.blocks.get(a);
                if (a == 0) {
                    block.first = true;
                    if (block instanceof TL_iv.pageBlockCover) {
                        TL_iv.pageBlockCover pageBlockCover = (TL_iv.pageBlockCover) block;
                        TL_iv.RichText caption = getBlockCaption(pageBlockCover, 0);
                        TL_iv.RichText credit = getBlockCaption(pageBlockCover, 1);
                        if ((caption != null && !(caption instanceof TL_iv.textEmpty) || credit != null && !(credit instanceof TL_iv.textEmpty)) && count > 1) {
                            TL_iv.PageBlock next = webPage.cached_page.blocks.get(1);
                            if (next instanceof TL_iv.pageBlockChannel) {
                                adapter.channelBlock = (TL_iv.pageBlockChannel) next;
                            }
                        }
                    }
                } else if (a == 1 && adapter.channelBlock != null) {
                    continue;
                }
                adapter.addBlock(adapter, block, 0, 0, a == count - 1 ? a : 0);
            }

            adapter.notifyDataSetChanged();

            if (pagesStack.size() == 1 || order == -1) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
                String key = "article" + webPage.id;
                int position = preferences.getInt(key, -1);
                int offset;
                if (preferences.getBoolean(key + "r", true) == AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    offset = preferences.getInt(key + "o", 0) - pages[index].listView.getPaddingTop();
                } else {
                    offset = dp(10);
                }
                if (position != -1) {
                    pages[index].layoutManager.scrollToPositionWithOffset(position, offset);
                }
            } else {
                pages[index].layoutManager.scrollToPositionWithOffset(sheet != null && sheet.halfSize() ? 1 : 0, sheet != null ? dp(56 - 24) : 0);
            }
        } else if (page instanceof CachedWeb) {
            pages[index].setType(PageLayout.TYPE_WEB);
            pages[index].scrollToTop(false);
            pages[index].setWeb((CachedWeb) page);
        }
        if (!previous) {
            checkScrollAnimated();
        }

        updateTitle(false);
        updatePages();
    }

    public BotWebViewContainer.MyWebView getLastWebView() {
        if (pages[0] != null && pages[0].isWeb()) {
            if (pages[0].getWebView() == null) {
                pages[0].webViewContainer.checkCreateWebView();
            }
            return pages[0].getWebView();
        }
        return null;
    }

    private boolean addPageToStack(TLRPC.WebPage webPage, String anchor, int order) {
        saveCurrentPagePosition();
        pagesStack.add(webPage);
        actionBar.showSearch(false, true);
        updateInterfaceForCurrentPage(webPage, false, order);
        return scrollToAnchor(anchor, false);
    }

    private boolean addPageToStack(String url, int order) {
        saveCurrentPagePosition();
        CachedWeb web = new CachedWeb(url);
        pagesStack.add(web);
        actionBar.showSearch(false, true);
        updateInterfaceForCurrentPage(web, false, order);
        return false;
    }

    private void goBack() {
        if (pagesStack.size() <= 1) {
            windowView.movingPage = false;
            windowView.startedTracking = false;
            View movingView = containerView;
            float x = sheet != null ? sheet.getBackProgress() * sheet.windowView.getWidth() : movingView.getX();
            final boolean backAnimation = false;
            float distToMove;
            AnimatorSet animatorSet = new AnimatorSet();
            distToMove = movingView.getMeasuredWidth() - x;
            if (sheet != null) {
                animatorSet.playTogether(sheet.animateBackProgressTo(1f));
            } else {
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, movingView.getMeasuredWidth()),
                    ObjectAnimator.ofFloat(windowView, ARTICLE_VIEWER_INNER_TRANSLATION_X, (float) movingView.getMeasuredWidth())
                );
            }

            animatorSet.setDuration(Math.max((int) (420.0f / movingView.getMeasuredWidth() * distToMove), 250));
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (windowView.movingPage) {
                        Object removed = null;
                        pages[0].setBackgroundDrawable(null);
                        if (!backAnimation) {
                            PageLayout pageToUpdate = pages[1];
                            pages[1] = pages[0];
                            pages[0] = pageToUpdate;
                            actionBar.swap();
                            page0Background.set(pages[0].getBackgroundColor(), true);
                            page1Background.set(pages[1].getBackgroundColor(), true);
                            if (sheet != null) {
                                sheet.updateLastVisible();
                            }

                            removed = pagesStack.remove(pagesStack.size() - 1);

                            textSelectionHelper.setParentView(pages[0].listView);
                            textSelectionHelper.layoutManager = pages[0].layoutManager;
                            textSelectionHelper.clear(true);

                            updateTitle(false);
                            updatePages();
                        }
                        pages[1].cleanup();
                        pages[1].setVisibility(View.GONE);
                        if (removed instanceof CachedWeb) {
                            ((CachedWeb) removed).destroy();
                        }
                        if (removed instanceof TLRPC.WebPage) {
                            WebInstantView.recycle((TLRPC.WebPage) removed);
                        }
                    } else {
                        if (!backAnimation) {
                            if (sheet != null) {
                                sheet.release();
                                destroy();
                            } else {
                                saveCurrentPagePosition();
                                onClosed();
                            }
                        }
                    }
                    windowView.movingPage = false;
                    windowView.startedTracking = false;
                    closeAnimationInProgress = false;
                }
            });
            animatorSet.start();
            closeAnimationInProgress = true;
            return;
        }
        windowView.openingPage = true;
        windowView.movingPage = true;
        windowView.startMovingHeaderHeight = currentHeaderHeight;
        pages[1].setVisibility(View.VISIBLE);
        pages[1].setAlpha(1.0f);
        pages[1].setTranslationX(0.0f);
        pages[0].setBackgroundColor(sheet == null ? 0 : backgroundPaint.getColor());
        updateInterfaceForCurrentPage(pagesStack.get(pagesStack.size() - 2), true, -1);

        View movingView = pages[0];
        float x = movingView.getX();
        final boolean backAnimation = false;
        float distToMove;
        AnimatorSet animatorSet = new AnimatorSet();
        if (!backAnimation) {
            distToMove = movingView.getMeasuredWidth() - x;
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, movingView.getMeasuredWidth())
            );
        } else {
            distToMove = x;
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, 0)
            );
        }

        animatorSet.setDuration(420);
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (windowView.openingPage) {
                    Object removed = null;
                    pages[0].setBackgroundDrawable(null);
                    if (!backAnimation) {
                        PageLayout pageToUpdate = pages[1];
                        pages[1] = pages[0];
                        pages[0] = pageToUpdate;
                        actionBar.swap();
                        page0Background.set(pages[0].getBackgroundColor(), true);
                        page1Background.set(pages[1].getBackgroundColor(), true);
                        if (sheet != null) {
                            sheet.updateLastVisible();
                        }

                        removed = pagesStack.remove(pagesStack.size() - 1);

                        textSelectionHelper.setParentView(pages[0].listView);
                        textSelectionHelper.layoutManager = pages[0].layoutManager;
                        textSelectionHelper.clear(true);

                        updateTitle(false);
                        updatePages();
                    }
                    pages[1].cleanup();
                    pages[1].setVisibility(View.GONE);
                    if (removed instanceof CachedWeb) {
                        ((CachedWeb) removed).destroy();
                    }
                    if (removed instanceof TLRPC.WebPage) {
                        WebInstantView.recycle((TLRPC.WebPage) removed);
                    }
                } else {
                    if (!backAnimation) {
                        saveCurrentPagePosition();
                        onClosed();
                    }
                }
                windowView.openingPage = false;
                windowView.startedTracking = false;
                closeAnimationInProgress = false;
            }
        });
        animatorSet.start();
        actionBar.setMenuColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getBackgroundColor() : getThemedColor(Theme.key_iv_background));
        actionBar.setColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getActionBarColor() : getThemedColor(Theme.key_iv_background), true);
        actionBar.setIsTonsite(pages[0] != null && pages[0].isTonsite());
        actionBar.setIsLocal(pages[0] != null && pages[0].isLocal());
        closeAnimationInProgress = true;
    }

    private void goBack(int intoIndex) {
        if (pagesStack.size() <= 1) {
            windowView.movingPage = false;
            windowView.startedTracking = false;
            View movingView = containerView;
            float x = sheet != null ? sheet.getBackProgress() * sheet.windowView.getWidth() : movingView.getX();
            final boolean backAnimation = false;
            float distToMove;
            AnimatorSet animatorSet = new AnimatorSet();
            distToMove = movingView.getMeasuredWidth() - x;
            if (sheet != null) {
                animatorSet.playTogether(
                    sheet.animateBackProgressTo(1f)
                );
            } else {
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, movingView.getMeasuredWidth()),
                    ObjectAnimator.ofFloat(windowView, ARTICLE_VIEWER_INNER_TRANSLATION_X, (float) movingView.getMeasuredWidth())
                );
            }

            animatorSet.setDuration(Math.max((int) (420.0f / movingView.getMeasuredWidth() * distToMove), 250));
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (windowView.movingPage) {
                        Object removed = null;
                        pages[0].setBackgroundDrawable(null);
                        if (!backAnimation) {
                            PageLayout pageToUpdate = pages[1];
                            pages[1] = pages[0];
                            pages[0] = pageToUpdate;
                            actionBar.swap();
                            page0Background.set(pages[0].getBackgroundColor(), true);
                            page1Background.set(pages[1].getBackgroundColor(), true);
                            if (sheet != null) {
                                sheet.updateLastVisible();
                            }

                            removed = pagesStack.remove(pagesStack.size() - 1);

                            textSelectionHelper.setParentView(pages[0].listView);
                            textSelectionHelper.layoutManager = pages[0].layoutManager;
                            textSelectionHelper.clear(true);

                            updateTitle(false);
                            updatePages();
                        }
                        pages[1].cleanup();
                        pages[1].setVisibility(View.GONE);
                        if (removed instanceof CachedWeb) {
                            ((CachedWeb) removed).destroy();
                        }
                        if (removed instanceof TLRPC.WebPage) {
                            WebInstantView.recycle((TLRPC.WebPage) removed);
                        }
                    } else {
                        if (!backAnimation) {
                            if (sheet != null) {
                                sheet.release();
                                destroy();
                            } else {
                                saveCurrentPagePosition();
                                onClosed();
                            }
                        }
                    }
                    windowView.movingPage = false;
                    windowView.startedTracking = false;
                    closeAnimationInProgress = false;
                }
            });
            animatorSet.start();
            closeAnimationInProgress = true;
            return;
        }

        windowView.openingPage = true;
        pages[1].setVisibility(View.VISIBLE);
        pages[1].setAlpha(1.0f);
        pages[1].setTranslationX(0.0f);
        pages[0].setBackgroundColor(sheet == null ? 0 : backgroundPaint.getColor());
        updateInterfaceForCurrentPage(pagesStack.get(intoIndex), true, -1);

        View movingView = pages[0];
        float x = movingView.getX();
        final boolean backAnimation = false;
        float distToMove;
        AnimatorSet animatorSet = new AnimatorSet();
        if (!backAnimation) {
            distToMove = movingView.getMeasuredWidth() - x;
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, movingView.getMeasuredWidth())
            );
        } else {
            distToMove = x;
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(pages[0], View.TRANSLATION_X, 0)
            );
        }

        animatorSet.setDuration(420);
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (windowView.openingPage) {
                    ArrayList<Object> removed = new ArrayList<>();
                    pages[0].setBackgroundDrawable(null);
                    if (!backAnimation) {
                        PageLayout pageToUpdate = pages[1];
                        pages[1] = pages[0];
                        pages[0] = pageToUpdate;
                        actionBar.swap();
                        page0Background.set(pages[0].getBackgroundColor(), true);
                        page1Background.set(pages[1].getBackgroundColor(), true);
                        if (sheet != null) {
                            sheet.updateLastVisible();
                        }

                        for (int i = pagesStack.size() - 1; i > intoIndex; --i) {
                            removed.add(pagesStack.remove(i));
                        }

                        textSelectionHelper.setParentView(pages[0].listView);
                        textSelectionHelper.layoutManager = pages[0].layoutManager;
                        textSelectionHelper.clear(true);

                        updateTitle(false);
                        updatePages();
                    }
                    pages[1].cleanup();
                    pages[1].setVisibility(View.GONE);
                    for (Object obj : removed) {
                        if (obj instanceof CachedWeb) {
                            ((CachedWeb) obj).destroy();
                        }
                        if (obj instanceof TLRPC.WebPage) {
                            WebInstantView.recycle((TLRPC.WebPage) obj);
                        }
                    }
                } else {
                    if (!backAnimation) {
                        saveCurrentPagePosition();
                        onClosed();
                    }
                }
                windowView.openingPage = false;
                windowView.startedTracking = false;
                closeAnimationInProgress = false;
            }
        });
        animatorSet.start();
        actionBar.setMenuColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getBackgroundColor() : getThemedColor(Theme.key_iv_background));
        actionBar.setColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getActionBarColor() : getThemedColor(Theme.key_iv_background), true);
        actionBar.setIsTonsite(pages[0] != null && pages[0].isTonsite());
        actionBar.setIsLocal(pages[0] != null && pages[0].isLocal());
        closeAnimationInProgress = true;
    }

    @Override
    public boolean scrollToAnchor(String anchor, boolean animated) {
        if (TextUtils.isEmpty(anchor)) {
            return false;
        }
        anchor = anchor.toLowerCase();
        Integer row = pages[0].adapter.anchors.get(anchor);
        if (row != null) {
            TL_iv.textAnchor textAnchor = pages[0].adapter.anchorsParent.get(anchor);
            if (textAnchor != null) {
                TL_iv.pageBlockParagraph paragraph = new TL_iv.pageBlockParagraph();
                String webPageUrl;
                if (!TextUtils.isEmpty(pages[0].adapter.currentPage.cached_page.url)) {
                    webPageUrl = pages[0].adapter.currentPage.cached_page.url.toLowerCase();
                } else {
                    webPageUrl = pages[0].adapter.currentPage.url.toLowerCase();
                }
                paragraph.text = WebInstantView.filterRecursiveAnchorLinks(textAnchor.text, webPageUrl, anchor);

                int type = pages[0].adapter.getTypeForBlock(paragraph);
                RecyclerView.ViewHolder holder = pages[0].adapter.onCreateViewHolder(null, type);
                pages[0].adapter.bindBlockToHolder(type, holder, paragraph, 0, 0, false);

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
                textView.setTypeface(AndroidUtilities.bold());
                textView.setText(LocaleController.getString(R.string.InstantViewReference));
                textView.setGravity((pages[0].adapter.isRtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
                textView.setTextColor(getTextColor());
                textView.setPadding(dp(18), 0, dp(18), 0);
                linearLayout.addView(textView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48) + 1));

                holder.itemView.setTag(BOTTOM_SHEET_VIEW_TAG);
                linearLayout.addView(holder.itemView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 7, 0, 0));

                View overlayView = textSelectionHelperBottomSheet.getOverlayView(parentActivity);
                FrameLayout frameLayout = new FrameLayout(parentActivity) {

                    @Override
                    public boolean dispatchTouchEvent(MotionEvent ev) {
                        TextSelectionHelper.TextSelectionOverlay selectionOverlay = textSelectionHelperBottomSheet.getOverlayView(getContext());
                        MotionEvent textSelectionEv = MotionEvent.obtain(ev);
                        textSelectionEv.offsetLocation(-linearLayout.getX(), -linearLayout.getY());
                        if (textSelectionHelperBottomSheet.isInSelectionMode() && textSelectionHelperBottomSheet.getOverlayView(getContext()).onTouchEvent(textSelectionEv)) {
                            return true;
                        }

                        if (selectionOverlay.checkOnTap(ev)) {
                            ev.setAction(MotionEvent.ACTION_CANCEL);
                        }

                        if (ev.getAction() == MotionEvent.ACTION_DOWN && textSelectionHelperBottomSheet.isInSelectionMode() && (ev.getY() < linearLayout.getTop() || ev.getY() > linearLayout.getBottom())) {
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
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(linearLayout.getMeasuredHeight() + dp(8), MeasureSpec.EXACTLY);
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    }
                };

                builder.setDelegate(new BottomSheet.BottomSheetDelegate() {
                    @Override
                    public boolean canDismiss() {
                        if (textSelectionHelperBottomSheet != null && textSelectionHelperBottomSheet.isInSelectionMode()) {
                            textSelectionHelperBottomSheet.clear();
                            return false;
                        }
                        return true;
                    }
                });

                frameLayout.addView(linearLayout, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                frameLayout.addView(overlayView, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                builder.setCustomView(frameLayout);
                if (textSelectionHelper.isInSelectionMode()) {
                    textSelectionHelper.clear();
                }
                showDialog(linkSheet = builder.create());
            } else {
                if (row < 0 || row >= pages[0].adapter.blocks.size()) {
                    return false;
                }
                TL_iv.PageBlock originalBlock = pages[0].adapter.blocks.get(row);
                TL_iv.PageBlock block = getLastNonListPageBlock(originalBlock);

                if (block instanceof TL_pageBlockDetailsChild) {
                    if (openAllParentBlocks((TL_pageBlockDetailsChild) block)) {
                        pages[0].adapter.updateRows();
                        pages[0].adapter.notifyDataSetChanged();
                    }
                }
                int position = pages[0].adapter.localBlocks.indexOf(originalBlock);
                if (position != -1) {
                    row = position;
                }

                Integer offset = pages[0].adapter.anchorsOffset.get(anchor);
                if (offset != null) {
                    if (offset == -1) {
                        int type = pages[0].adapter.getTypeForBlock(originalBlock);
                        RecyclerView.ViewHolder holder = pages[0].adapter.onCreateViewHolder(null, type);
                        pages[0].adapter.bindBlockToHolder(type, holder, originalBlock, 0, 0, false);
                        holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(pages[0].listView.getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                        offset = pages[0].adapter.anchorsOffset.get(anchor);
                        if (offset == -1) {
                            offset = 0;
                        }
                    }
                } else {
                    offset = 0;
                }
                if (pages[0].adapter.padding) {
                    row++;
                }
                if (animated) {
                    SmoothScroller s = new SmoothScroller(pages[0].getContext()) {
                        @Override
                        protected int getVerticalSnapPreference() {
                            return SNAP_TO_START;
                        }
                    };
                    s.setTargetPosition(row);
                    s.setOffset(-dp(56) - offset);
                    pages[0].layoutManager.startSmoothScroll(s);
                } else {
                    pages[0].layoutManager.scrollToPositionWithOffset(row, -dp(56) - offset);
                }
            }
            return true;
        }
        return false;
    }

    private boolean removeLastPageFromStack() {
        if (pagesStack.size() < 2) {
            return false;
        }
        Object removed = pagesStack.remove(pagesStack.size() - 1);
        if (removed instanceof CachedWeb) {
            ((CachedWeb) removed).destroy();
        }
        if (removed instanceof TLRPC.WebPage) {
            WebInstantView.recycle((TLRPC.WebPage) removed);
        }
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

    public static final int TEXT_FLAG_REGULAR     = 0;
    public static final int TEXT_FLAG_MEDIUM      = 1 << 0;
    public static final int TEXT_FLAG_ITALIC      = 1 << 1;
    public static final int TEXT_FLAG_MONO        = 1 << 2;
    public static final int TEXT_FLAG_URL         = 1 << 3;
    public static final int TEXT_FLAG_UNDERLINE   = 1 << 4;
    public static final int TEXT_FLAG_STRIKE      = 1 << 5;
    public static final int TEXT_FLAG_MARKED      = 1 << 6;
    public static final int TEXT_FLAG_SUB         = 1 << 7;
    public static final int TEXT_FLAG_SUP         = 1 << 8;
    public static final int TEXT_FLAG_WEBPAGE_URL = 1 << 9;

    private static final TextPaint audioTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final Resources resources = new Resources(false);

    @Override
    public Resources getResources() {
        return resources;
    }

    public static class Resources {

        public final boolean isRichMessage;
        public TextPaint errorTextPaint;
        public final SparseArray<TextPaint> photoCaptionTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> photoCreditTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> titleTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> kickerTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> headerTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> subtitleTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> subheaderTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading1TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading2TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading3TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading4TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading5TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> heading6TextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> authorTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> footerTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> paragraphTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> listTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> preformattedTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> quoteTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> embedPostTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> embedPostCaptionTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> mediaCaptionTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> mediaCreditTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> relatedArticleTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> detailsTextPaints = new SparseArray<>();
        public final SparseArray<TextPaint> tableTextPaints = new SparseArray<>();

        public Resources(boolean isRichMessage) {
            this.isRichMessage = isRichMessage;
        }

        public void updatePaintColors(IArticleViewer parent) {
            setMapColors(parent, titleTextPaints);
            setMapColors(parent, kickerTextPaints);
            setMapColors(parent, subtitleTextPaints);
            setMapColors(parent, headerTextPaints);
            setMapColors(parent, subheaderTextPaints);
            setMapColors(parent, heading1TextPaints);
            setMapColors(parent, heading2TextPaints);
            setMapColors(parent, heading3TextPaints);
            setMapColors(parent, heading4TextPaints);
            setMapColors(parent, heading5TextPaints);
            setMapColors(parent, heading6TextPaints);
            setMapColors(parent, quoteTextPaints);
            setMapColors(parent, preformattedTextPaints);
            setMapColors(parent, paragraphTextPaints);
            setMapColors(parent, listTextPaints);
            setMapColors(parent, embedPostTextPaints);
            setMapColors(parent, mediaCaptionTextPaints);
            setMapColors(parent, mediaCreditTextPaints);
            setMapColors(parent, photoCaptionTextPaints);
            setMapColors(parent, photoCreditTextPaints);
            setMapColors(parent, authorTextPaints);
            setMapColors(parent, footerTextPaints);
            setMapColors(parent, embedPostCaptionTextPaints);
            setMapColors(parent, relatedArticleTextPaints);
            setMapColors(parent, detailsTextPaints);
            setMapColors(parent, tableTextPaints);
        }

        private void setMapColors(IArticleViewer parent, SparseArray<TextPaint> map) {
            for (int a = 0; a < map.size(); a++) {
                final int flags = map.keyAt(a);
                final TextPaint paint = map.valueAt(a);
                if (paint == null) continue;
                if ((flags & TEXT_FLAG_URL) != 0 || (flags & TEXT_FLAG_WEBPAGE_URL) != 0) {
                    paint.setColor(parent.getLinkTextColor());
                } else {
                    paint.setColor(parent.getTextColor());
                }
            }
        }

        public void updatePaintFonts(int selectedFont) {
            ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putInt("font_type", selectedFont).commit();
            Typeface typefaceNormal = selectedFont == 0 ? Typeface.DEFAULT : Typeface.SERIF;
            Typeface typefaceItalic = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/ritalic.ttf") : Typeface.create("serif", Typeface.ITALIC);
            Typeface typefaceBold = selectedFont == 0 ? AndroidUtilities.bold() : Typeface.create("serif", Typeface.BOLD);
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
    }

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

    // Draws one or more vertical blockquote lines on the left of any block cell.
    // Decoded from block.quoteLevels: bit i set => draw a line at layer i (padx + i*14 dp).
    // Legacy fallback: when quoteLevels==0 but level>0 (e.g. embedPost children), draws a single
    // line at padx so existing behavior is preserved.
    public static void drawQuoteLines(Canvas canvas, IArticleViewer parent, TL_iv.PageBlock block, int height) {
        if (block == null || parent == null || quoteLinePaint == null) return;
        final int padxDp = parent.padx();
        final int bottomTrim = block.bottom ? AndroidUtilities.dp(6) : 0;
        final int y2 = height - bottomTrim;
        int mask = block.quoteLevels;
        if (mask == 0) {
            if (block.level > 0) {
                final int x = AndroidUtilities.dp(padxDp);
                canvas.drawRect(x, 0, x + AndroidUtilities.dp(2), y2, quoteLinePaint);
            }
            return;
        }
        int i = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                final int x = AndroidUtilities.dp(padxDp + i * 14);
                canvas.drawRect(x, 0, x + AndroidUtilities.dp(2), y2, quoteLinePaint);
            }
            mask >>>= 1;
            i++;
        }
    }

    private static Paint dividerPaint;
    public static Paint tableLinePaint;
    public static Paint tableHalfLinePaint;
    public static Paint tableHeaderPaint;
    public static Paint tableStripPaint;
    private static Paint urlPaint;
    private static Paint webpageUrlPaint;
    private static Paint webpageSearchPaint;
    private static Paint webpageMarkPaint;

    public static int getTextFlags(TL_iv.RichText richText) {
        if (richText instanceof TL_iv.textFixed) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MONO;
        } else if (richText instanceof TL_iv.textItalic) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_ITALIC;
        } else if (richText instanceof TL_iv.textBold) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MEDIUM;
        } else if (richText instanceof TL_iv.textUnderline) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_UNDERLINE;
        } else if (richText instanceof TL_iv.textStrike) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_STRIKE;
        } else if (richText instanceof TL_iv.textEmail) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
        } else if (richText instanceof TL_iv.textPhone) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
        } else if (richText instanceof TL_iv.textUrl) {
            TL_iv.textUrl textUrl = (TL_iv.textUrl) richText;
            if (textUrl.webpage_id != 0) {
                return getTextFlags(richText.parentRichText) | TEXT_FLAG_WEBPAGE_URL;
            } else {
                return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
            }
        } else if (richText instanceof TL_iv.textSubscript) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_SUB;
        } else if (richText instanceof TL_iv.textSuperscript) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_SUP;
        } else if (richText instanceof TL_iv.textMarked) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_MARKED;
        } else if (richText != null) {
            return getTextFlags(richText.parentRichText);
        }
        return TEXT_FLAG_REGULAR;
    }

    public static TL_iv.RichText getLastRichText(TL_iv.RichText richText) {
        if (richText == null) {
            return null;
        }
        if (richText instanceof TL_iv.textFixed) {
            return getLastRichText(((TL_iv.textFixed) richText).text);
        } else if (richText instanceof TL_iv.textItalic) {
            return getLastRichText(((TL_iv.textItalic) richText).text);
        } else if (richText instanceof TL_iv.textBold) {
            return getLastRichText(((TL_iv.textBold) richText).text);
        } else if (richText instanceof TL_iv.textUnderline) {
            return getLastRichText(((TL_iv.textUnderline) richText).text);
        } else if (richText instanceof TL_iv.textStrike) {
            return getLastRichText(((TL_iv.textStrike) richText).text);
        } else if (richText instanceof TL_iv.textEmail) {
            return getLastRichText(((TL_iv.textEmail) richText).text);
        } else if (richText instanceof TL_iv.textUrl) {
            return getLastRichText(((TL_iv.textUrl) richText).text);
        } else if (richText instanceof TL_iv.textAnchor) {
            getLastRichText(((TL_iv.textAnchor) richText).text);
        } else if (richText instanceof TL_iv.textSubscript) {
            return getLastRichText(((TL_iv.textSubscript) richText).text);
        } else if (richText instanceof TL_iv.textSuperscript) {
            return getLastRichText(((TL_iv.textSuperscript) richText).text);
        } else if (richText instanceof TL_iv.textMarked) {
            return getLastRichText(((TL_iv.textMarked) richText).text);
        } else if (richText instanceof TL_iv.textPhone) {
            return getLastRichText(((TL_iv.textPhone) richText).text);
        } else if (richText instanceof TL_iv.textSpoiler) {
            return getLastRichText(((TL_iv.textSpoiler) richText).text);
        }
        return richText;
    }

    private CharSequence getText(WebpageAdapter adapter, View parentView, TL_iv.RichText parentRichText, TL_iv.RichText richText, TL_iv.PageBlock parentBlock, int maxWidth) {
        return getText(adapter.currentPage, parentView, parentRichText, richText, parentBlock, maxWidth);
    }
    public static CharSequence getText(IArticleViewer parent, WebpageAdapter adapter, View parentView, TL_iv.RichText parentRichText, TL_iv.RichText richText, TL_iv.PageBlock parentBlock, int maxWidth) {
        return getText(parent, adapter != null ? adapter.currentPage : null, parentView, parentRichText, richText, parentBlock, maxWidth);
    }

    private CharSequence getText(TLRPC.WebPage page, View parentView, TL_iv.RichText parentRichText, TL_iv.RichText richText, TL_iv.PageBlock parentBlock, int maxWidth) {
        return getText(this, page, parentView, parentRichText, richText, parentBlock, maxWidth);
    }
    public static CharSequence getText(
        IArticleViewer parent,
        TLRPC.WebPage page,
        View parentView,
        TL_iv.RichText parentRichText,
        TL_iv.RichText richText,
        TL_iv.PageBlock parentBlock,
        int maxWidth
    ) {
        if (richText == null) {
            return null;
        }
        if (richText instanceof TL_iv.textFixed) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textFixed) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textItalic) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textItalic) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textBold) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textBold) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textUnderline) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textUnderline) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textStrike) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textStrike) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textEmail) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, ((TL_iv.textEmail) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parent, parentRichText, richText, parentBlock) : null, "mailto:" + getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textUrl) {
            TL_iv.textUrl textUrl = (TL_iv.textUrl) richText;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, ((TL_iv.textUrl) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            TextPaint paint = innerSpans == null || innerSpans.length == 0 ? getTextPaint(parent, parentRichText, richText, parentBlock) : null;
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
        } else if (richText instanceof TL_iv.textPlain) {
            return ((TL_iv.textPlain) richText).text;
        } else if (richText instanceof TL_iv.textAnchor) {
            TL_iv.textAnchor textAnchor = (TL_iv.textAnchor) richText;
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, textAnchor.text, parentBlock, maxWidth));
            spannableStringBuilder.setSpan(new AnchorSpan(textAnchor.name), 0, spannableStringBuilder.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textEmpty) {
            return "";
        } else if (richText instanceof TL_iv.textConcat) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                TL_iv.RichText innerRichText = richText.texts.get(a);
                TL_iv.RichText lastRichText = getLastRichText(innerRichText);
                boolean extraSpace = maxWidth >= 0 && innerRichText instanceof TL_iv.textUrl && ((TL_iv.textUrl) innerRichText).webpage_id != 0;
                if (extraSpace && spannableStringBuilder.length() != 0 && spannableStringBuilder.charAt(spannableStringBuilder.length() - 1) != '\n') {
                    spannableStringBuilder.append(" ");
                    spannableStringBuilder.setSpan(new TextSelectionHelper.IgnoreCopySpannable(), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                CharSequence innerText = getText(parent, page, parentView, parentRichText, innerRichText, parentBlock, maxWidth);
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
                            span = new TextPaintWebpageUrlSpan(getTextPaint(parent, parentRichText, lastRichText, parentBlock), url);
                        } else {
                            span = new TextPaintUrlSpan(getTextPaint(parent, parentRichText, lastRichText, parentBlock), url);
                        }
                        if (startLength != spannableStringBuilder.length()) {
                            spannableStringBuilder.setSpan(span, startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else {
                        if (startLength != spannableStringBuilder.length()) {
                            spannableStringBuilder.setSpan(new TextPaintSpan(getTextPaint(parent, parentRichText, lastRichText, parentBlock)), startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
                if (extraSpace && a != count - 1) {
                    spannableStringBuilder.append(" ");
                    spannableStringBuilder.setSpan(new TextSelectionHelper.IgnoreCopySpannable(), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textSubscript) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textSubscript) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textSuperscript) {
            return getText(parent, page, parentView, parentRichText, ((TL_iv.textSuperscript) richText).text, parentBlock, maxWidth);
        } else if (richText instanceof TL_iv.textMarked) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, ((TL_iv.textMarked) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintMarkSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parent, parentRichText, richText, parentBlock) : null), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textSpoiler) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, ((TL_iv.textSpoiler) richText).text, parentBlock, maxWidth));
            if (spannableStringBuilder.length() != 0) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                spannableStringBuilder.setSpan(new TextStyleSpan(run), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textPhone) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parent, page, parentView, parentRichText, ((TL_iv.textPhone) richText).text, parentBlock, maxWidth));
            MetricAffectingSpan[] innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            if (spannableStringBuilder.length() != 0) {
                spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parent, parentRichText, richText, parentBlock) : null, "tel:" + getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        } else if (richText instanceof TL_iv.textImage) {
            final TL_iv.textImage textImage = (TL_iv.textImage) richText;
            final TLRPC.Document document = WebPageUtils.getDocumentWithId(page, textImage.document_id);
            final TLRPC.Photo photo = WebPageUtils.getPhotoWithId(page, textImage.photo_id);
            if (document != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("*");
                int w = dp(textImage.w);
                int h = dp(textImage.h);
                maxWidth = Math.abs(maxWidth);
                if (w > maxWidth) {
                    float scale = maxWidth / (float) w;
                    w = maxWidth;
                    h *= scale;
                }
                if (parentView != null) {
                    final boolean invert = AndroidUtilities.computePerceivedBrightness(parent.getThemedColor(Theme.key_windowBackgroundWhite)) <= 0.705f;
                    spannableStringBuilder.setSpan(new TextPaintImageReceiverSpan(parentView, document, page, w, h, false, invert), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return spannableStringBuilder;
            } else if (photo instanceof WebInstantView.WebPhoto) {
                WebInstantView.WebPhoto webPhoto = (WebInstantView.WebPhoto) photo;
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("*");
                int w = dp(textImage.w);
                int h = dp(textImage.h);
                maxWidth = Math.abs(maxWidth);
                if (w > maxWidth) {
                    float scale = maxWidth / (float) w;
                    w = maxWidth;
                    h *= scale;
                }
                if (parentView != null) {
                    spannableStringBuilder.setSpan(new TextPaintImageReceiverSpan(parentView, webPhoto, page, w, h, false, false), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return spannableStringBuilder;
            } else {
                return "";
            }
        } else if (richText instanceof TL_iv.textMath) {
            final TL_iv.textMath textLatex = (TL_iv.textMath) richText;
            if (textLatex.bitmap == null && !textLatex.tried) {
                textLatex.tried = true;
                try {
                    final JLatexMathDrawable drawable =
                            JLatexMathDrawable.builder(textLatex.source)
                                    .textSize(AndroidUtilities.dp(20))
                                    .build();
                    final int w = drawable.getIntrinsicWidth();
                    final int h = drawable.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                        drawable.setBounds(0, 0, w, h);
                        drawable.draw(new Canvas(bm));
                        textLatex.w = w;
                        textLatex.h = h;
                        try {
                            textLatex.depth = drawable.icon().getIconDepth();
                        } catch (Throwable t) {
                            FileLog.e(t);
                        }
                        textLatex.bitmap = bm;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (textLatex.bitmap == null) {
                return textLatex.source == null ? "" : textLatex.source;
            }
            final SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("￼");
            spannableStringBuilder.setSpan(new TextPaintImageReceiverSpan(parentView, textLatex.bitmap, textLatex.w, textLatex.h, parent.getThemedColor(Theme.key_windowBackgroundWhiteBlackText), textLatex.depth), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (textLatex.source != null && !textLatex.source.isEmpty()) {
                spannableStringBuilder.setSpan(new TextSelectionHelper.ReplaceCopyTextSpannable(textLatex.source), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return spannableStringBuilder;
        }
        return "not supported " + richText;
    }

    public static CharSequence getPlainText(TL_iv.RichText richText) {
        if (richText == null) {
            return "";
        }
        if (richText instanceof TL_iv.textFixed) {
            return getPlainText(((TL_iv.textFixed) richText).text);
        } else if (richText instanceof TL_iv.textItalic) {
            return getPlainText(((TL_iv.textItalic) richText).text);
        } else if (richText instanceof TL_iv.textBold) {
            return getPlainText(((TL_iv.textBold) richText).text);
        } else if (richText instanceof TL_iv.textUnderline) {
            return getPlainText(((TL_iv.textUnderline) richText).text);
        } else if (richText instanceof TL_iv.textStrike) {
            return getPlainText(((TL_iv.textStrike) richText).text);
        } else if (richText instanceof TL_iv.textEmail) {
            return getPlainText(((TL_iv.textEmail) richText).text);
        } else if (richText instanceof TL_iv.textUrl) {
            return getPlainText(((TL_iv.textUrl) richText).text);
        } else if (richText instanceof TL_iv.textPlain) {
            return ((TL_iv.textPlain) richText).text;
        } else if (richText instanceof TL_iv.textAnchor) {
            return getPlainText(((TL_iv.textAnchor) richText).text);
        } else if (richText instanceof TL_iv.textEmpty) {
            return "";
        } else if (richText instanceof TL_iv.textConcat) {
            StringBuilder stringBuilder = new StringBuilder();
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                stringBuilder.append(getPlainText(richText.texts.get(a)));
            }
            return stringBuilder;
        } else if (richText instanceof TL_iv.textSubscript) {
            return getPlainText(((TL_iv.textSubscript) richText).text);
        } else if (richText instanceof TL_iv.textSuperscript) {
            return getPlainText(((TL_iv.textSuperscript) richText).text);
        } else if (richText instanceof TL_iv.textMarked) {
            return getPlainText(((TL_iv.textMarked) richText).text);
        } else if (richText instanceof TL_iv.textPhone) {
            return getPlainText(((TL_iv.textPhone) richText).text);
        } else if (richText instanceof TL_iv.textImage) {
            return "";
        }
        return "";
    }

    public static String getUrl(TL_iv.RichText richText) {
        if (richText instanceof TL_iv.textFixed) {
            return getUrl(((TL_iv.textFixed) richText).text);
        } else if (richText instanceof TL_iv.textItalic) {
            return getUrl(((TL_iv.textItalic) richText).text);
        } else if (richText instanceof TL_iv.textBold) {
            return getUrl(((TL_iv.textBold) richText).text);
        } else if (richText instanceof TL_iv.textUnderline) {
            return getUrl(((TL_iv.textUnderline) richText).text);
        } else if (richText instanceof TL_iv.textStrike) {
            return getUrl(((TL_iv.textStrike) richText).text);
        } else if (richText instanceof TL_iv.textEmail) {
            return ((TL_iv.textEmail) richText).email;
        } else if (richText instanceof TL_iv.textUrl) {
            return ((TL_iv.textUrl) richText).url;
        } else if (richText instanceof TL_iv.textPhone) {
            return ((TL_iv.textPhone) richText).phone;
        }
        return null;
    }

    @Override
    public int getTextColor() {
        return getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    @Override
    public int getLinkTextColor() {
        return getThemedColor(Theme.key_windowBackgroundWhiteLinkText);
    }

    @Override
    public int getGrayTextColor() {
        return getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    private TextPaint getTextPaint(TL_iv.RichText parentRichText, TL_iv.RichText richText, TL_iv.PageBlock parentBlock) {
        return getTextPaint(this, parentRichText, richText, parentBlock);
    }
    public static TextPaint getTextPaint(
        IArticleViewer parent,
        TL_iv.RichText parentRichText,
        TL_iv.RichText richText,
        TL_iv.PageBlock parentBlock
    ) {
        int flags = getTextFlags(richText);
        SparseArray<TextPaint> currentMap = null;
        int textSize = dp(14);
        int textColor = 0xffff0000;
        final Resources resources = parent.getResources();

        final int additionalSize = dp((resources.isRichMessage ? SharedConfig.fontSize : SharedConfig.ivFontSize) - 16);

        if (parentBlock instanceof TL_iv.pageBlockPhoto) {
            final TL_iv.pageBlockPhoto pageBlockPhoto = (TL_iv.pageBlockPhoto) parentBlock;
            if (pageBlockPhoto.caption.text == richText || pageBlockPhoto.caption.text == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockMap) {
            final TL_iv.pageBlockMap pageBlockMap = (TL_iv.pageBlockMap) parentBlock;
            if (pageBlockMap.caption.text == richText || pageBlockMap.caption.text == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockTitle) {
            currentMap = resources.titleTextPaints;
            textSize = dp(23);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockKicker) {
            currentMap = resources.kickerTextPaints;
            textSize = dp(14);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockAuthorDate) {
            currentMap = resources.authorTextPaints;
            textSize = dp(14);
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockFooter) {
            currentMap = resources.footerTextPaints;
            textSize = dp(14);
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockSubtitle) {
            currentMap = resources.subtitleTextPaints;
            textSize = dp(20);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeader) {
            currentMap = resources.headerTextPaints;
            textSize = dp(20);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockSubheader) {
            currentMap = resources.subheaderTextPaints;
            textSize = dp(17);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading1) {
            currentMap = resources.heading1TextPaints;
            textSize = dp(18);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading2) {
            currentMap = resources.heading2TextPaints;
            textSize = dp(16);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading3) {
            currentMap = resources.heading3TextPaints;
            textSize = dp(15);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading4) {
            currentMap = resources.heading4TextPaints;
            textSize = dp(14);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading5) {
            currentMap = resources.heading5TextPaints;
            textSize = dp(13);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockHeading6) {
            currentMap = resources.heading6TextPaints;
            textSize = dp(12);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockBlockquote) {
            final TL_iv.pageBlockBlockquote pageBlockBlockquote = (TL_iv.pageBlockBlockquote) parentBlock;
            if (pageBlockBlockquote.text == parentRichText) {
                currentMap = resources.quoteTextPaints;
                textSize = dp(15);
                textColor = parent.getTextColor();
            } else if (pageBlockBlockquote.caption == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
                textColor = parent.getGrayTextColor();
            }
        } else if (parentBlock instanceof TL_iv.pageBlockPullquote) {
            final TL_iv.pageBlockPullquote pageBlockBlockquote = (TL_iv.pageBlockPullquote) parentBlock;
            if (pageBlockBlockquote.text == parentRichText) {
                currentMap = resources.quoteTextPaints;
                textSize = dp(15);
                textColor = parent.getTextColor();
            } else if (pageBlockBlockquote.caption == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
                textColor = parent.getGrayTextColor();
            }
        } else if (parentBlock instanceof TL_iv.pageBlockPreformatted) {
            currentMap = resources.preformattedTextPaints;
            textSize = dp(14);
            textColor = parent.getTextColor();
            flags |= TEXT_FLAG_MONO;
        } else if (parentBlock instanceof TL_iv.pageBlockParagraph) {
            currentMap = resources.paragraphTextPaints;
            textSize = dp(16);
            textColor = parent.getTextColor();
        } else if (isListItemBlock(parentBlock)) {
            currentMap = resources.listTextPaints;
            textSize = dp(16);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockEmbed) {
            final TL_iv.pageBlockEmbed pageBlockEmbed = (TL_iv.pageBlockEmbed) parentBlock;
            if (pageBlockEmbed.caption.text == richText || pageBlockEmbed.caption.text == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockSlideshow) {
            final TL_iv.pageBlockSlideshow pageBlockSlideshow = (TL_iv.pageBlockSlideshow) parentBlock;
            if (pageBlockSlideshow.caption.text == richText || pageBlockSlideshow.caption.text == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockCollage) {
            final TL_iv.pageBlockCollage pageBlockCollage = (TL_iv.pageBlockCollage) parentBlock;
            if (pageBlockCollage.caption.text == richText || pageBlockCollage.caption.text == parentRichText) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockEmbedPost) {
            final TL_iv.pageBlockEmbedPost pageBlockEmbedPost = (TL_iv.pageBlockEmbedPost) parentBlock;
            if (richText == pageBlockEmbedPost.caption.text) {
                currentMap = resources.photoCaptionTextPaints;
                textSize = dp(14);
                textColor = parent.getGrayTextColor();
            } else if (richText == pageBlockEmbedPost.caption.credit) {
                currentMap = resources.photoCreditTextPaints;
                textSize = dp(12);
                textColor = parent.getGrayTextColor();
            } else if (richText != null) {
                currentMap = resources.embedPostTextPaints;
                textSize = dp(14);
                textColor = parent.getTextColor();
            }
        } else if (parentBlock instanceof TL_iv.pageBlockVideo) {
            final TL_iv.pageBlockVideo pageBlockVideo = (TL_iv.pageBlockVideo) parentBlock;
            if (richText == pageBlockVideo.caption.text) {
                currentMap = resources.mediaCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.mediaCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockAudio) {
            final TL_iv.pageBlockAudio pageBlockAudio = (TL_iv.pageBlockAudio) parentBlock;
            if (richText == pageBlockAudio.caption.text) {
                currentMap = resources.mediaCaptionTextPaints;
                textSize = dp(14);
            } else {
                currentMap = resources.mediaCreditTextPaints;
                textSize = dp(12);
            }
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockRelatedArticles) {
            currentMap = resources.relatedArticleTextPaints;
            textSize = dp(15);
            textColor = parent.getGrayTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockDetails) {
            currentMap = resources.detailsTextPaints;
            textSize = dp(15);
            textColor = parent.getTextColor();
        } else if (parentBlock instanceof TL_iv.pageBlockTable) {
            currentMap = resources.tableTextPaints;
            textSize = dp(15);
            textColor = parent.getTextColor();
        }
        if ((flags & TEXT_FLAG_SUP) != 0 || (flags & TEXT_FLAG_SUB) != 0) {
            textSize -= dp(4);
        }
        if (currentMap == null) {
            if (resources.errorTextPaint == null) {
                resources.errorTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                resources.errorTextPaint.setColor(0xffff0000);
            }
            resources.errorTextPaint.setTextSize(dp(14));
            return resources.errorTextPaint;
        }
        TextPaint paint = currentMap.get(flags);
        if (paint == null) {
            paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            if ((flags & TEXT_FLAG_MONO) != 0) {
                paint.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf"));
            } else {
                if (parentBlock instanceof TL_iv.pageBlockRelatedArticles) {
                    paint.setTypeface(AndroidUtilities.bold());
                } else if (parent.selectedFont == 1 || parentBlock instanceof TL_iv.pageBlockTitle || parentBlock instanceof TL_iv.pageBlockKicker || parentBlock instanceof TL_iv.pageBlockHeader || parentBlock instanceof TL_iv.pageBlockSubtitle || parentBlock instanceof TL_iv.pageBlockSubheader || isHeadingBlock(parentBlock)) {
                    if (parentBlock instanceof TL_iv.pageBlockTitle || parentBlock instanceof TL_iv.pageBlockHeader || parentBlock instanceof TL_iv.pageBlockSubtitle || parentBlock instanceof TL_iv.pageBlockSubheader || isHeadingBlock(parentBlock)) {
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
                        paint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
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
                textColor = parent.getLinkTextColor();
            }
            if ((flags & TEXT_FLAG_SUP) != 0) {
                paint.baselineShift -= dp(6.0f);
            } else if ((flags & TEXT_FLAG_SUB) != 0) {
                paint.baselineShift += dp(2.0f);
            }
            paint.setColor(textColor);
            currentMap.put(flags, paint);
        }
        paint.setTextSize(textSize + additionalSize);
        return paint;
    }

    private static final WeakHashMap<View, ArrayList<DrawingText>> liveDrawingTexts = new WeakHashMap<>();

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, Layout.Alignment align, WebpageAdapter parentAdapter) {
        return createLayoutForText(parentView, plainText, richText, width, 0, parentBlock, align, 0, parentAdapter);
    }
    private static DrawingText createLayoutForText(IArticleViewer parent, View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, Layout.Alignment align, WebpageAdapter parentAdapter) {
        return createLayoutForText(parent, parentView, plainText, richText, width, 0, parentBlock, align, 0, parentAdapter);
    }

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, WebpageAdapter parentAdapter) {
        return createLayoutForText(parentView, plainText, richText, width, textY, parentBlock, Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter);
    }
    public static DrawingText createLayoutForText(IArticleViewer parent, View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, WebpageAdapter parentAdapter) {
        return createLayoutForText(parent, parentView, plainText, richText, width, textY, parentBlock, Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter);
    }

    private DrawingText createLayoutForText(View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, Layout.Alignment align, int maxLines, WebpageAdapter parentAdapter) {
        return createLayoutForText(this, parentView, plainText, richText, width, textY, parentBlock, align, maxLines, parentAdapter);
    }
    public static DrawingText createLayoutForText(IArticleViewer parent, View parentView, CharSequence plainText, TL_iv.RichText richText, int width, int textY, TL_iv.PageBlock parentBlock, Layout.Alignment align, int maxLines, WebpageAdapter parentAdapter) {
        if (plainText == null && (richText == null || richText instanceof TL_iv.textEmpty)) {
            return null;
        }
        if (width < 0) {
            width = dp(10);
        }

        CharSequence text;
        if (plainText != null) {
            text = plainText;
        } else {
            text = getText(parent, parentAdapter, parentView, richText, richText, parentBlock, width);
        }
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        int additionalSize = dp(SharedConfig.ivFontSize - 16);

        TextPaint paint;
        if (parentBlock instanceof TL_iv.pageBlockEmbedPost && richText == null) {
            TL_iv.pageBlockEmbedPost pageBlockEmbedPost = (TL_iv.pageBlockEmbedPost) parentBlock;
            if (pageBlockEmbedPost.author == plainText) {
                if (embedPostAuthorPaint == null) {
                    embedPostAuthorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    embedPostAuthorPaint.setColor(parent.getTextColor());
                }
                embedPostAuthorPaint.setTextSize(dp(15) + additionalSize);
                paint = embedPostAuthorPaint;
            } else {
                if (embedPostDatePaint == null) {
                    embedPostDatePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    embedPostDatePaint.setColor(parent.getGrayTextColor());
                }
                embedPostDatePaint.setTextSize(dp(14) + additionalSize);
                paint = embedPostDatePaint;
            }
        } else if (parentBlock instanceof TL_iv.pageBlockChannel) {
            if (channelNamePaint == null) {
                channelNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                channelNamePaint.setTypeface(AndroidUtilities.bold());

                channelNamePhotoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                channelNamePhotoPaint.setTypeface(AndroidUtilities.bold());
            }
            channelNamePaint.setColor(parent.getTextColor());
            channelNamePaint.setTextSize(dp(15));

            channelNamePhotoPaint.setColor(0xffffffff);
            channelNamePhotoPaint.setTextSize(dp(15));

            paint = parentAdapter != null && parentAdapter.channelBlock != null ? channelNamePhotoPaint : channelNamePaint;
        } else if (parentBlock instanceof TL_pageBlockRelatedArticlesChild) {
            TL_pageBlockRelatedArticlesChild pageBlockRelatedArticlesChild = (TL_pageBlockRelatedArticlesChild) parentBlock;
            if (plainText == pageBlockRelatedArticlesChild.parent.articles.get(pageBlockRelatedArticlesChild.num).title) {
                if (relatedArticleHeaderPaint == null) {
                    relatedArticleHeaderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    relatedArticleHeaderPaint.setTypeface(AndroidUtilities.bold());
                }
                relatedArticleHeaderPaint.setColor(parent.getTextColor());
                relatedArticleHeaderPaint.setTextSize(dp(15) + additionalSize);
                paint = relatedArticleHeaderPaint;
            } else {
                if (relatedArticleTextPaint == null) {
                    relatedArticleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                }
                relatedArticleTextPaint.setColor(parent.getGrayTextColor());
                relatedArticleTextPaint.setTextSize(dp(14) + additionalSize);
                paint = relatedArticleTextPaint;
            }
        } else if (isListItemBlock(parentBlock) && plainText != null) {
            if (listTextPointerPaint == null) {
                listTextPointerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                listTextPointerPaint.setColor(parent.getTextColor());
            }
            if (listTextNumPaint == null) {
                listTextNumPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                listTextNumPaint.setColor(parent.getTextColor());
            }
            listTextPointerPaint.setTextSize(dp(19) + additionalSize);
            listTextNumPaint.setTextSize(dp(16) + additionalSize);
            if (parentBlock instanceof TL_pageBlockListItem && !((TL_pageBlockListItem) parentBlock).parent.pageBlockList.ordered) {
                paint = listTextPointerPaint;
            } else {
                paint = listTextNumPaint;
            }
        } else {
            paint = getTextPaint(parent, richText, richText, parentBlock);
        }
        text = Emoji.replaceEmoji(text, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_CENTER);
        StaticLayout result;
        if (maxLines != 0) {
            if (parentBlock instanceof TL_iv.pageBlockPullquote) {
                result = StaticLayoutEx.createStaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, maxLines);
            } else {
                result = StaticLayoutEx.createStaticLayout(text, paint, width, align, 1.0f, dp(4), false, TextUtils.TruncateAt.END, width, maxLines);
            }
        } else {
            if (text.charAt(text.length() - 1) == '\n') {
                text = text.subSequence(0, text.length() - 1);
            }
            if (parentBlock instanceof TL_iv.pageBlockPullquote) {
                result = new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            } else {
                result = new StaticLayout(text, paint, width, align, 1.0f, dp(4), false);
            }
        }
        if (result == null) {
            return null;
        }
        CharSequence finalText = result.getText();
        LinkPath textPath = null;
        LinkPath markPath = null;
        if (textY >= 0) {
            if (result != null && !parent.searchResults.isEmpty() && parent.searchText != null) {
                final WebpageAdapter adapter = parent.getAdapter();
                if (adapter != null) {
                    final String lowerString = text.toString().toLowerCase();
                    int startIndex = 0;
                    int index;
                    while ((index = lowerString.indexOf(parent.searchText, startIndex)) >= 0) {
                        startIndex = index + parent.searchText.length();
                        if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString.charAt(index - 1))) {
                            adapter.searchTextOffset.put(parent.searchText + parentBlock + richText + index, textY + result.getLineTop(result.getLineForOffset(index)));
                        }
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
                        textPath.setBaselineShift(shift != 0 ? shift + dp(shift > 0 ? 5 : -2) : 0);
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
                        markPath.setBaselineShift(shift != 0 ? shift + dp(shift > 0 ? 5 : -2) : 0);
                        result.getSelectionPath(start, end, markPath);
                    }
                    markPath.setAllowReset(true);
                }
            } catch (Exception ignore) {

            }
        }
        DrawingText drawingText = new DrawingText(parent);
        drawingText.textLayout = result;
        drawingText.textPath = textPath;
        drawingText.markPath = markPath;
        drawingText.parentBlock = parentBlock;
        drawingText.parentText = richText;
        drawingText.spoilersPool = new Stack<>();
        drawingText.spoilers = new ArrayList<>();
        drawingText.spoilersPatchedLayout = new AtomicReference<>();
        if (finalText instanceof Spanned) {
            SpoilerEffect.addSpoilers(parentView, result, (Spanned) finalText, drawingText.spoilersPool, drawingText.spoilers);
        }
        if (parentView != null) {
            ArrayList<DrawingText> list = liveDrawingTexts.get(parentView);
            if (list != null) {
                for (int i = 0; i < list.size(); ++i) {
                    DrawingText other = list.get(i);
                    if (other.parentBlock != parentBlock || richText != null && other.parentText == richText) {
                        other.detach(parentView);
                        list.remove(i);
                        i--;
                    }
                }
            }
            if (richText != null) {
                if (list == null) {
                    liveDrawingTexts.put(parentView, list = new ArrayList<>());
                }
                list.add(drawingText);
            }
            if (parentView.isAttachedToWindow()) {
                drawingText.attach(parentView);
            }
        }
        return drawingText;
    }

    @Override
    public boolean allowTouches() {
        return pageSwitchAnimation == null;
    }

    private boolean checkLayoutForLinks(WebpageAdapter adapter, MotionEvent event, View parentView, DrawingText drawingText, int layoutX, int layoutY) {
        return checkLayoutForLinks(this, adapter, event, parentView, drawingText, layoutX, layoutY);
    }
    public static boolean checkLayoutForLinks(IArticleViewer parent, WebpageAdapter adapter, MotionEvent event, View parentView, DrawingText drawingText, int layoutX, int layoutY) {
        if (!parent.allowTouches() || parentView == null) {
            return false;
        }
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(parentView);
        if (textSelectionHelper != null && !textSelectionHelper.isSelectable(parentView)) {
            return false;
        }
        parent.pressedLinkOwnerView = parentView;
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
                    parent.pressedLinkOwnerLayout = drawingText;
                    parent.pressedLayoutY = layoutY;
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
                                    TextPaintUrlSpan selectedLink = link[0];
                                    int pressedStart = buffer.getSpanStart(selectedLink);
                                    int pressedEnd = buffer.getSpanEnd(selectedLink);
                                    for (int a = 1; a < link.length; a++) {
                                        TextPaintUrlSpan span = link[a];
                                        int start = buffer.getSpanStart(span);
                                        int end = buffer.getSpanEnd(span);
                                        if (pressedStart > start || end > pressedEnd) {
                                            selectedLink = span;
                                            pressedStart = start;
                                            pressedEnd = end;
                                        }
                                    }
                                    if (parent.pressedLink == null || parent.pressedLink.getSpan() != selectedLink) {
                                        if (parent.pressedLink != null) {
                                            parent.links.removeLink(parent.pressedLink);
                                        }
                                        parent.pressedLink = new LinkSpanDrawable(selectedLink, null, x, y);
                                        parent.pressedLink.setColor(parent.getThemedColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
                                        parent.links.addLink(parent.pressedLink, parent.pressedLinkOwnerLayout);
                                        try {
                                            final LinkPath path = parent.pressedLink.obtainNewPath();
                                            path.setCurrentLayout(layout, pressedStart, 0);
                                            final TextPaint textPaint = selectedLink.getTextPaint();
                                            final int shift = textPaint != null ? textPaint.baselineShift : 0;
                                            path.setBaselineShift(shift != 0 ? shift + dp(shift > 0 ? 5 : -2) : 0);
                                            layout.getSelectionPath(pressedStart, pressedEnd, path);
                                            parentView.invalidate();
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                }
                                if (drawingText.spoilers != null && !drawingText.spoilers.isEmpty()) {
                                    TextStyleSpan[] spoilerSpans = buffer.getSpans(off, off, TextStyleSpan.class);
                                    boolean spoilerHit = false;
                                    if (spoilerSpans != null) {
                                        for (int a = 0; a < spoilerSpans.length; a++) {
                                            if (spoilerSpans[a].isSpoiler()) {
                                                spoilerHit = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (spoilerHit) {
                                        Path spoilerPath = new Path();
                                        for (SpoilerEffect eff : drawingText.spoilers) {
                                            Rect b = eff.getBounds();
                                            spoilerPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
                                        }
                                        RectF spoilerBounds = new RectF();
                                        spoilerPath.computeBounds(spoilerBounds, false);
                                        float rad = (float) Math.sqrt(spoilerBounds.width() * spoilerBounds.width() + spoilerBounds.height() * spoilerBounds.height());
                                        final DrawingText finalDrawingText = drawingText;
                                        final View finalParentView = parentView;
                                        finalDrawingText.spoilers.get(0).setOnRippleEndCallback(() -> finalParentView.post(() -> {
                                            finalDrawingText.spoilers.clear();
                                            finalDrawingText.spoilersPatchedLayout.set(null);
                                            finalParentView.invalidate();
                                        }));
                                        for (SpoilerEffect eff : drawingText.spoilers) {
                                            eff.startRipple(checkX, checkY, rad);
                                        }
                                        parentView.invalidate();
                                        if (parentView.getParent() != null) {
                                            parentView.getParent().requestDisallowInterceptTouchEvent(true);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (parent.pressedLink != null) {
                    removeLink = true;
                    parent.handleLinkClick(adapter, parent.pressedLink.getSpan());
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL && (parent.popupWindow == null || !parent.popupWindow.isShowing())) {
                removeLink = true;
            }
            if (removeLink) {
                removePressedLink(parent);
            }
        }
        parent.checkLayoutForLinks(event, parentView);
        if (parentView instanceof BlockDetailsCell) {
            return parent.pressedLink != null;
        } else {
            return parent.pressedLinkOwnerLayout != null;
        }
    }

    @Override
    public void checkLayoutForLinks(MotionEvent event, View parentView) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress(event.getX(), event.getY(), parentView);
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }
    }

    private Browser.Progress makeProgress(LinkSpanDrawable<TextPaintUrlSpan> link, DrawingText text) {
        return makeProgress(this, link, text);
    }
    private static Browser.Progress makeProgress(IArticleViewer parent, LinkSpanDrawable<TextPaintUrlSpan> link, DrawingText text) {
        if (link == null) return null;
        return new Browser.Progress() {
            @Override
            public void init() {
                parent.loadingText = text;
                parent.loadingLinkView = text != null ? text.latestParentView : null;
                parent.loadingLink = link.getSpan();

                parent.links.removeLoading(parent.loadingLinkDrawable, true);
                if (text != null) {
                    parent.loadingLinkDrawable = LinkSpanDrawable.LinkCollector.makeLoading(text.textLayout, link.getSpan(), 0);
                    final int color = parent.getThemedColor(Theme.key_chat_linkSelectBackground);
                    parent.loadingLinkDrawable.setColors(
                        Theme.multAlpha(color, .8f),
                        Theme.multAlpha(color, 1.3f),
                        Theme.multAlpha(color, 1f),
                        Theme.multAlpha(color, 4f)
                    );
                    parent.loadingLinkDrawable.strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.25f));
                    parent.links.addLoading(parent.loadingLinkDrawable, text);
                }

                if (parent.loadingLinkView != null) {
                    parent.loadingLinkView.invalidate();
                }
                super.init();
            }
            @Override
            public void end() {
                parent.links.removeLoading(parent.loadingLinkDrawable, true);
                if (parent.loadingLinkView != null) {
                    parent.loadingLinkView.invalidate();
                }
                parent.loadingLink = null;
                super.end();
            }
        };
    }

    private void removePressedLink() {
        removePressedLink(this);
    }

    private static void removePressedLink(IArticleViewer parent) {
        if (parent.pressedLink == null && parent.pressedLinkOwnerView == null) {
            return;
        }
        View parentView = parent.pressedLinkOwnerView;
        parent.links.clear();
        parent.pressedLink = null;
        parent.pressedLinkOwnerLayout = null;
        parent.pressedLinkOwnerView = null;
        if (parentView != null) {
            parentView.invalidate();
        }
    }

    @Override
    public void openWebpageUrl(String url, String anchor, Browser.Progress progress) {
        if (parentActivity == null || parentActivity.isFinishing()) return;
        if (pages[0] != null && pages[0].isLocal()) {
            String format = LocaleController.getString(R.string.OpenUrlAlert2);
            int index = format.indexOf("%");
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder(String.format(format, url));
            if (index >= 0) {
                stringBuilder.setSpan(new URLSpan(url), index, index + url.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            new AlertDialog.Builder(parentActivity, getResourcesProvider())
                .setTitle(getString(R.string.OpenUrlTitle))
                .setMessage(stringBuilder)
                .setMessageTextViewClickable(false)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Open), (di, w) -> {
                    openWebpageUrlInternal(url, anchor, progress);
                })
                .show();
            return;
        }
        openWebpageUrlInternal(url, anchor, progress);
    }
    private void openWebpageUrlInternal(String url, String anchor, Browser.Progress progress) {
        if (loadingProgress != null) {
            loadingProgress.cancel();
        }
        loadingProgress = progress;
        if (openUrlReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, false);
            openUrlReqId = 0;
        }

        final boolean[] forceBrowser = new boolean[1];
        if (Browser.openInExternalApp(parentActivity, url, false)) {
            if (pagesStack.isEmpty()) {
                if (sheet != null) {
                    sheet.dismiss(false);
                }
            }
            return;
        }
        Utilities.Callback0Return<Boolean> checkInternal = () -> {
            if (!Browser.isInternalUri(Uri.parse(url), forceBrowser)) {
                return false;
            }
            if (progress != null) {
                progress.onEnd(() -> {
                    if (sheet != null) {
                        sheet.dismiss(true);
                    }
                    if (loadingProgress == progress) {
                        loadingProgress = null;
                    }
                });
            } else {
                if (sheet != null) {
                    sheet.dismiss(true);
                }
            }
            Browser.openUrl(parentActivity, Uri.parse(url), true, true, false, progress, null, true, true, false);
            return true;
        };

        final int reqId = ++lastReqId;
        showProgressView(true, true);
        final TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
        req.url = url;
        req.hash = 0;
        openUrlReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (openUrlReqId == 0 || reqId != lastReqId) {
                return;
            }
            if (progress != null) {
                progress.end();
            }
            openUrlReqId = 0;
            showProgressView(true, false);
            if (isVisible) {
                if (response instanceof TLRPC.TL_messages_webPage) {
                    TLRPC.TL_messages_webPage res = (TLRPC.TL_messages_webPage) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    if (res.webpage != null && res.webpage.cached_page instanceof TL_iv.TL_page) {
                        addPageToStack(res.webpage, anchor, 1);
                    } else if (!checkInternal.run()) {
                        if (MessagesController.getInstance(currentAccount).isWebBrowserOpenInApp(req.url)) {
                            addPageToStack(req.url, 1);
                        } else {
                            Browser.openUrl(parentActivity, req.url);
                        }
                    }
                } else if (response instanceof TLRPC.TL_webPage && ((TLRPC.TL_webPage) response).cached_page instanceof TL_iv.TL_page) {
                    addPageToStack((TLRPC.TL_webPage) response, anchor, 1);
                } else if (!checkInternal.run()) {
                    if (MessagesController.getInstance(currentAccount).isWebBrowserOpenInApp(req.url)) {
                        addPageToStack(req.url, 1);
                    } else {
                        Browser.openUrl(parentActivity, req.url);
                    }
                }
            }
        }));
        if (progress != null) {
            progress.onCancel(() -> {
                if (lastReqId == reqId && openUrlReqId != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, false);
                    openUrlReqId = 0;
                }
                if (loadingProgress == progress) {
                    loadingProgress = null;
                }
            });
            progress.init();
        }

    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject messageObject = (MessageObject) args[0];
            if (pages != null) {
                for (int i = 0; i < pages.length; i++) {
                    int count = pages[i].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = pages[i].listView.getChildAt(a);
                        if (view instanceof BlockAudioCell) {
                            BlockAudioCell cell = (BlockAudioCell) view;
                            cell.updateButtonState(true);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (pages != null) {
                for (int i = 0; i < pages.length; i++) {
                    int count = pages[i].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = pages[i].listView.getChildAt(a);
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
            if (pages != null) {
                for (int i = 0; i < pages.length; i++) {
                    int count = pages[i].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = pages[i].listView.getChildAt(a);
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
        } else if (id == NotificationCenter.emojiLoaded) {
            if (pages != null) {
                for (int i = 0; i < pages.length; i++) {
                    int count = pages[i].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = pages[i].listView.getChildAt(a);
                        if (view instanceof BlockTableCell) {
                            ((BlockTableCell) view).tableLayout.invalidate();
                        } else {
                            view.invalidate();
                        }
                    }
                }
            }
        }
    }

    public void updateThemeColors(float progress) {
        refreshThemeColors();
        updatePaintColors(this);
        if (windowView != null) {
            pages[0].listView.invalidateViews();
            pages[1].listView.invalidateViews();
            windowView.invalidate();
            searchPanel.invalidate();
            if (progress == 1) {
                pages[0].adapter.notifyDataSetChanged();
                pages[1].adapter.notifyDataSetChanged();
            }
        }
    }

    private void updatePaintSize() {
        for (int i = 0; i < 2; i++) {
            pages[i].adapter.notifyDataSetChanged();
            pages[i].adapter.resetCachedHeights();
        }
    }

    public static void updatePaintColors(IArticleViewer parent) {
        if (listTextPointerPaint != null) {
            listTextPointerPaint.setColor(parent.getTextColor());
        }
        if (listTextNumPaint != null) {
            listTextNumPaint.setColor(parent.getTextColor());
        }
        if (embedPostAuthorPaint != null) {
            embedPostAuthorPaint.setColor(parent.getTextColor());
        }
        if (channelNamePaint != null) {
            channelNamePaint.setColor(parent.getTextColor());
        }
        if (channelNamePhotoPaint != null) {
            channelNamePhotoPaint.setColor(0xffffffff);
        }
        if (relatedArticleHeaderPaint != null) {
            relatedArticleHeaderPaint.setColor(parent.getTextColor());
        }
        if (relatedArticleTextPaint != null) {
            relatedArticleTextPaint.setColor(parent.getGrayTextColor());
        }

        if (embedPostDatePaint != null) {
            embedPostDatePaint.setColor(parent.getGrayTextColor());
        }

        createPaint(parent, true);
        resources.updatePaintColors(parent);
    }

    private WindowVisibilityManager.Controller activityVisibilityController;

    public void setParentActivity(Activity activity, BaseFragment fragment) {
        if (activityVisibilityController != null) {
            activityVisibilityController.destroy();
            activityVisibilityController = null;
        }
        activityVisibilityController = LaunchActivity.obtainActivityVisibilityController();

        parentFragment = fragment;
        currentAccount = fragment != null && !(fragment instanceof EmptyBaseFragment) ? fragment.getCurrentAccount() : UserConfig.selectedAccount;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.emojiLoaded);
        if (parentActivity == activity || parentActivity != null && isSheet && sheet != null && sheet.dialog != null) {
            updatePaintColors(this);
            refreshThemeColors();
            return;
        }
        parentActivity = activity;

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
        selectedFont = sharedPreferences.getInt("font_type", 0);
        createPaint(this, false);
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
                if (windowView != null && (windowView.movingPage || windowView.openingPage)) {
                    int width = getMeasuredWidth();
                    int translationX = (int) pages[0].getTranslationX();
                    int clipLeft = 0;
                    int clipRight = width;

                    if (child == pages[1]) {
                        clipRight = translationX;
                    } else if (child == pages[0]) {
                        clipLeft = translationX;
                    }

                    final int restoreCount = canvas.save();
                    canvas.clipRect(clipLeft, 0, clipRight, getHeight());
                    final boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restoreToCount(restoreCount);

                    if (translationX != 0) {
                        if (child == pages[0]) {
                            final float alpha = Math.max(0, Math.min((width - translationX) / (float) dp(20), 1.0f));
                            layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), child.getTop(), translationX, child.getBottom());
                            layerShadowDrawable.setAlpha((int) (0xff * alpha));
                            layerShadowDrawable.draw(canvas);
                        } else if (child == pages[1]) {
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

            @Override
            public void invalidate() {
                super.invalidate();
            }
        };
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        //containerView.setFitsSystemWindows(true);
        if (sheet == null) {
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
        fullscreenAspectRatioView.setVisibility(View.VISIBLE);
        fullscreenAspectRatioView.setBackgroundColor(Color.BLACK);
        fullscreenVideoContainer.addView(fullscreenAspectRatioView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        fullscreenTextureView = new TextureView(activity);

        pages = new PageLayout[2];
        for (int i = 0; i < pages.length; i++) {
            PageLayout page = pages[i] = new PageLayout(activity, getResourcesProvider());
            page.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            containerView.addView(page, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));
            page.listView.setOnItemLongClickListener((view, position) -> {
                if (view instanceof BlockRelatedArticlesCell) {
                    BlockRelatedArticlesCell cell = (BlockRelatedArticlesCell) view;
                    showCopyPopup(cell.currentBlock.parent.articles.get(cell.currentBlock.num).url);
                    return true;
                }
                return false;
            });
            page.listView.setOnItemClickListener((view, position, x, y) -> {
                if (sheet != null) {
                    position--;
                    if (position < 0) return;
                }
                if (textSelectionHelper != null) {
                    if (textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.clear();
                        return;
                    }
                    textSelectionHelper.clear();
                }
                final WebpageAdapter adapter = page.getAdapter();
                if (view instanceof ReportCell && adapter.currentPage != null) {
                    ReportCell cell = (ReportCell) view;
                    if (previewsReqId != 0 || cell.hasViews && x < view.getMeasuredWidth() / 2 || cell.web) {
                        return;
                    }
                    TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat("previews");
                    if (object instanceof TLRPC.TL_user) {
                        openPreviewsChat((TLRPC.User) object, adapter.currentPage.id);
                    } else {
                        final int currentAccount = UserConfig.selectedAccount;
                        final long pageId = adapter.currentPage.id;
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
                } else if (position >= 0 && position < adapter.localBlocks.size()) {
                    TL_iv.PageBlock pageBlock = adapter.localBlocks.get(position);
                    TL_iv.PageBlock originalBlock = pageBlock;
                    pageBlock = getLastNonListPageBlock(pageBlock);
                    if (pageBlock instanceof TL_pageBlockDetailsChild) {
                        TL_pageBlockDetailsChild detailsChild = (TL_pageBlockDetailsChild) pageBlock;
                        pageBlock = detailsChild.block;
                    }
                    if (pageBlock instanceof TL_iv.pageBlockChannel) {
                        TL_iv.pageBlockChannel pageBlockChannel = (TL_iv.pageBlockChannel) pageBlock;
                        MessagesController.getInstance(currentAccount).openByUserName(ChatObject.getPublicUsername(pageBlockChannel.channel), parentFragment, 2);
                        close(false, true);
                    } else if (pageBlock instanceof TL_pageBlockRelatedArticlesChild) {
                        TL_pageBlockRelatedArticlesChild pageBlockRelatedArticlesChild = (TL_pageBlockRelatedArticlesChild) pageBlock;
                        openWebpageUrl(pageBlockRelatedArticlesChild.parent.articles.get(pageBlockRelatedArticlesChild.num).url, null, null);
                    } else if (pageBlock instanceof TL_iv.pageBlockDetails) {
                        view = getLastNonListCell(view);
                        if (!(view instanceof BlockDetailsCell)) {
                            return;
                        }

                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView = null;
                        int index = adapter.blocks.indexOf(originalBlock);
                        if (index < 0) {
                            return;
                        }
                        TL_iv.pageBlockDetails pageBlockDetails = (TL_iv.pageBlockDetails) pageBlock;
                        pageBlockDetails.open = !pageBlockDetails.open;

                        int oldCount = adapter.getItemCount();
                        adapter.updateRows();
                        int newCount = adapter.getItemCount();
                        int changeCount = Math.abs(newCount - oldCount);

                        BlockDetailsCell cell = (BlockDetailsCell) view;
                        cell.arrow.setAnimationProgressAnimated(pageBlockDetails.open ? 0.0f : 1.0f);
                        cell.invalidate();
                        if (changeCount != 0) {
                            if (pageBlockDetails.open) {
                                adapter.notifyItemRangeInserted(position + 1, changeCount);
                            } else {
                                adapter.notifyItemRangeRemoved(position + 1, changeCount);
                            }
                        }
                    }
                }
            });
        }
        bulletinContainer = new FrameLayout(activity);
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, !BOTTOM_ACTION_BAR && sheet != null && !sheet.halfSize() ? 56 : 0, 0, BOTTOM_ACTION_BAR && sheet != null && !sheet.halfSize() ? 24 : 0));

        headerPaint.setColor(0xff000000);
        statusBarPaint.setColor(0xff000000);
        headerProgressPaint.setColor(0xff242426);
        navigationBarPaint.setColor(Color.BLACK);
        actionBar = new WebActionBar(activity, getResourcesProvider()) {
            @Override
            protected void onOpenedMenu() {
                pages[0].listView.stopScroll();
                checkScrollAnimated();
            }
            @Override
            protected void onSearchUpdated(String s) {
                processSearch(s.toLowerCase());
            }
            @Override
            protected void onColorsUpdated() {
                if (sheet != null) {
                    sheet.checkNavColor();
                }
            }
            @Override
            protected void onScrolledProgress(float delta) {
                pages[0].addProgress(delta);
            }

            @Override
            protected void onAddressColorsChanged(int backgroundColor, int textColor) {
                if (addressBarList != null) {
                    addressBarList.setColors(backgroundColor, textColor);
                }
            }

            @Override
            protected void onAddressingProgress(float progress) {
                super.onAddressingProgress(progress);
                if (addressBarList != null) {
                    addressBarList.setOpenProgress(progress);
                }
                if (sheet != null) {
                    sheet.checkNavColor();
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                ((MarginLayoutParams) addressBarList.getLayoutParams()).topMargin = getMeasuredHeight();
            }

            @Override
            public void showAddress(boolean show, boolean animated) {
                super.showAddress(show, animated);
                if (addressBarList != null) {
                    addressBarList.setOpened(show);
                }
            }

            @Override
            protected WebInstantView.Loader getInstantViewLoader() {
                return pages[0].loadInstant();
            }
        };
        actionBar.occupyStatusBar(sheet != null && !BOTTOM_ACTION_BAR);
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, BOTTOM_ACTION_BAR ? Gravity.BOTTOM : Gravity.TOP));
        actionBar.setOnClickListener(v -> {
            if (actionBar.longClicked) return;
            final PageLayout page = pages[0];
            if (page.isWeb()) {
                if (page.getWebView() != null && !actionBar.isAddressing()) {
                    if (addressBarList != null) {
                        BotWebViewContainer.MyWebView webView = page.getWebView();
                        final String title = webView != null ? webView.getTitle() : null;
                        final String url = BotWebViewContainer.magic2tonsite(webView != null ? webView.getUrl() : null);
                        addressBarList.setCurrent(
                            webView != null ? webView.getFavicon() : null,
                            TextUtils.isEmpty(title) ? getString(R.string.WebEmpty) : title,
                            TextUtils.isEmpty(url) ? "about:blank" : url,

                            () -> {
                                actionBar.addressEditText.setText(TextUtils.isEmpty(url) ? "about:blank" : url);
                                actionBar.addressEditText.setSelection(actionBar.addressEditText.getText().length());
                                AndroidUtilities.showKeyboard(actionBar.addressEditText);
                            },
                            query -> {
                                if (TextUtils.isEmpty(query)) return;
                                if (page.getWebView() == null) return;
                                SpannableStringBuilder sb = new SpannableStringBuilder(query.trim());
                                AndroidUtilities.addLinksSafe(sb, Linkify.WEB_URLS, false, true);
                                URLSpan[] spans = sb.getSpans(0, sb.length(), URLSpan.class);
                                int start = sb.length(), end = 0;
                                for (int i = 0; i < spans.length; ++i) {
                                    start = Math.min(sb.getSpanStart(spans[i]), start);
                                    end = Math.max(sb.getSpanEnd(spans[i]), end);
                                }
                                actionBar.showAddress(false, true);
                                Uri uri = Utilities.uriParseSafe(query);
                                if (spans.length > 0 && start == 0 && end > 0 || uri != null && uri.getScheme() != null) {
                                    if (uri != null && uri.getScheme() == null && uri.getHost() == null && uri.getPath() != null) {
                                        query = Browser.replace(uri, "https", null, uri.getPath(), "/");
                                    }
                                    page.getWebView().loadUrl(query);
                                } else {
                                    AddressBarList.pushRecentSearch(activity, query);
                                    page.getWebView().loadUrl(SearchEngine.getCurrent().getSearchURL(query));
                                }
                            },
                            query -> {
                                if (TextUtils.isEmpty(query)) return;
                                actionBar.addressEditText.setText(query);
                                actionBar.addressEditText.setSelection(actionBar.addressEditText.getText().length());
                                AndroidUtilities.showKeyboard(actionBar.addressEditText);
                            },
                            ArticleViewer.this::openBookmark,
                            view -> {
                                actionBar.showAddress(false, true);
                                AndroidUtilities.hideKeyboard(actionBar.addressEditText);
                                final String link = TextUtils.isEmpty(url) ? "about:blank" : url;
                                AndroidUtilities.addToClipboard(link);
                                BulletinFactory.of(page.webViewContainer, getResourcesProvider()).createCopyLinkBulletin().show(true);
                            }
                        );
                    }
                    actionBar.showAddress("", url -> {
                        if (TextUtils.isEmpty(url)) return;
                        if (page.getWebView() == null) return;
                        SpannableStringBuilder sb = new SpannableStringBuilder(url.trim());
                        AndroidUtilities.addLinksSafe(sb, Linkify.WEB_URLS, false, true);
                        URLSpan[] spans = sb.getSpans(0, sb.length(), URLSpan.class);
                        int start = sb.length(), end = 0;
                        for (int i = 0; i < spans.length; ++i) {
                            start = Math.min(sb.getSpanStart(spans[i]), start);
                            end = Math.max(sb.getSpanEnd(spans[i]), end);
                        }
                        Uri uri = Utilities.uriParseSafe(url);
                        if (uri != null && TextUtils.equals(uri.getScheme(), "javascript")) return;
                        if (spans.length > 0 && start == 0 && end > 0 || uri != null && uri.getScheme() != null) {
                            if (uri != null && uri.getScheme() == null && uri.getHost() == null && uri.getPath() != null) {
                                url = Browser.replace(uri, "https", null, uri.getPath(), "/");
                            }
                            page.getWebView().loadUrl(url);
                        } else {
                            AddressBarList.pushRecentSearch(activity, url);
                            page.getWebView().loadUrl(SearchEngine.getCurrent().getSearchURL(url));
                        }
                    });
                }
            } else if (sheet != null) {
                SmoothScroller s = new SmoothScroller(activity);
                if (sheet.halfSize()) {
                    s.setTargetPosition(1);
                    s.setOffset(-dp(56 - 24));
                } else {
                    s.setTargetPosition(0);
                }
                page.layoutManager.startSmoothScroll(s);
            } else {
                page.listView.smoothScrollToPosition(0);
            }
        });
        actionBar.addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!actionBar.isAddressing()) return;
                if (addressBarList == null) return;
                addressBarList.setInput(s == null ? null : s.toString());
            }
        });

        addressBarList = new AddressBarList(activity);
        addressBarList.setOpenProgress(0f);
        addressBarList.listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (addressBarList.listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(actionBar.addressEditText);
                }
            }
        });
        containerView.addView(addressBarList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        lineProgressTickRunnable = () -> {
            float progressLeft = 0.7f - actionBar.lineProgressView.getCurrentProgress();
            if (progressLeft > 0.0f) {
                float tick;
                if (progressLeft < 0.25f) {
                    tick = 0.01f;
                } else {
                    tick = 0.02f;
                }
                actionBar.lineProgressView.setProgress(actionBar.lineProgressView.getCurrentProgress() + tick, true);
                AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100);
            }
        };
        actionBar.backButton.setOnClickListener(v -> {
            if (actionBar.isSearching()) {
                actionBar.showSearch(false, true);
            } else if (actionBar.isAddressing()) {
                actionBar.showAddress(false, true);
            } else if (isFirstArticle() && pages[0].hasBackButton()) {
                pages[0].back();
            } else if (pagesStack.size() > 1) {
                goBack();
            } else if (sheet != null) {
                sheet.dismiss(false);
            } else {
                close(true, true);
            }
        });
        actionBar.backButton.setOnLongClickListener(v -> {
            if (pages[0] == null) return false;

            final float wasRotation = actionBar.backButtonDrawable.getRotation();
            ItemOptions options = ItemOptions.makeOptions(sheet != null ? sheet.windowView : windowView, v);

            final int backgroundColor = SharedConfig.adaptableColorInBrowser ? Theme.getColor(Theme.key_iv_background) : pages[0].getBackgroundColor();
            final int textColor = SharedConfig.adaptableColorInBrowser ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText) : AndroidUtilities.computePerceivedBrightness(pages[0].getBackgroundColor()) >= .721f ? Color.BLACK : Color.WHITE;
            final int subtextColor = Theme.multAlpha(textColor, .65f);

            final BotWebViewContainer.MyWebView webView = pages[0].getWebView();
            if (webView != null) {
                final WebBackForwardList history = webView.copyBackForwardList();
                final int currentIndex = history.getCurrentIndex();
                if (history.getCurrentIndex() > 0) {
                    for (int i = 0; i < currentIndex; ++i) {
                        WebHistoryItem item = history.getItemAtIndex(i);
                        final int index = i;
                        options.add(item.getTitle(), () -> {
                            for (int j = 0; j < (currentIndex - index); ++j) webView.goBack();
                        });
                        ActionBarMenuSubItem menuItem = options.getLast();
                        if (menuItem != null) {
                            menuItem.setSubtext(item.getUrl());
                            Bitmap bitmap = webView.getFavicon(item.getUrl());
                            if (bitmap == null) {
                                bitmap = item.getFavicon();
                            }
                            final Bitmap finalBitmap = bitmap;
                            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                            menuItem.setTextAndIcon(item.getTitle(), 0, new Drawable() {
                                @Override
                                public void draw(@NonNull Canvas canvas) {
                                    if (finalBitmap != null) {
                                        canvas.save();
                                        canvas.translate(getBounds().left, getBounds().top);
                                        canvas.scale(getBounds().width() / (float) finalBitmap.getWidth(), getBounds().height() / (float) finalBitmap.getHeight());
                                        canvas.drawBitmap(finalBitmap, 0, 0, paint);
                                        canvas.restore();
                                    }
                                }

                                @Override
                                public void setColorFilter(@Nullable ColorFilter colorFilter) {

                                }

                                @Override
                                public void setAlpha(int alpha) {

                                }

                                @Override
                                public int getIntrinsicHeight() {
                                    return dp(24);
                                }

                                @Override
                                public int getIntrinsicWidth() {
                                    return dp(24);
                                }

                                @Override
                                public int getOpacity() {
                                    return PixelFormat.TRANSPARENT;
                                }
                            });
                            menuItem.setTextColor(textColor);
                            menuItem.setSubtextColor(subtextColor);
                        }
                    }
                }
            }
            for (int i = pagesStack.size() - 2; i >= 0; --i) {
                Object obj = pagesStack.get(i);
                if (obj instanceof CachedWeb) {
                    CachedWeb web = (CachedWeb) obj;
                    final int index = i;
                    options.add(web.getTitle(), () -> {
                        goBack(index);
                    });
                    ActionBarMenuSubItem item = options.getLast();
                    if (item != null) {
                        item.setSubtext(web.lastUrl);
                        Bitmap bitmap = webView != null ? webView.getFavicon(web.lastUrl) : null;
                        if (bitmap == null) {
                            bitmap = web.favicon;
                        }
                        final Bitmap finalBitmap = bitmap;
                        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                        item.setTextAndIcon(web.getTitle(), 0, new Drawable() {
                            @Override
                            public void draw(@NonNull Canvas canvas) {
                                if (finalBitmap != null) {
                                    canvas.save();
                                    canvas.translate(getBounds().left, getBounds().top);
                                    canvas.scale(getBounds().width() / (float) finalBitmap.getWidth(), getBounds().height() / (float) finalBitmap.getHeight());
                                    canvas.drawBitmap(finalBitmap, 0, 0, paint);
                                    canvas.restore();
                                }
                            }

                            @Override
                            public void setColorFilter(@Nullable ColorFilter colorFilter) {

                            }

                            @Override
                            public void setAlpha(int alpha) {

                            }

                            @Override
                            public int getIntrinsicHeight() {
                                return dp(24);
                            }

                            @Override
                            public int getIntrinsicWidth() {
                                return dp(24);
                            }

                            @Override
                            public int getOpacity() {
                                return PixelFormat.TRANSPARENT;
                            }
                        });
                        item.setTextColor(textColor);
                        item.setSubtextColor(subtextColor);
                        item.setColors(textColor, textColor);
                    }
                } else if (obj instanceof TLRPC.WebPage) {
                    TLRPC.WebPage webpage = (TLRPC.WebPage) obj;
                    final int index = i;
                    options.add(webpage.title, () -> {
                        goBack(index);
                    });
                    ActionBarMenuSubItem item = options.getLast();
                    if (item != null) {
                        item.setTextAndIcon(webpage.title, R.drawable.msg_instant);
                        item.setTextColor(textColor);
                        if (!TextUtils.isEmpty(webpage.site_name)) {
                            item.setSubtext(webpage.site_name);
                        }
                        item.setSubtextColor(subtextColor);
                        item.imageView.getLayoutParams().width = dp(24);
                        item.imageView.setScaleX(1.45f);
                        item.imageView.setScaleY(1.45f);
                        item.setColors(textColor, textColor);
                    }
                }
            }
            options.setScrimViewBackground(Theme.createCircleDrawable(dp(40), actionBar.getBackgroundColor()));
            options.setBackgroundColor(backgroundColor);
            options.updateColors();
            if (options.getItemsCount() <= 0) return false;
            checkScrollAnimated(() -> {
                actionBar.backButtonDrawable.setRotation(0f, true);
                options.setOnDismiss(() -> {
                    actionBar.backButtonDrawable.setRotation(wasRotation, true);
                });
                options.show();
            });

            return true;
        });
//        actionBar.forwardButton.setOnLongClickListener(v -> {
//            if (pages[0] == null) return false;
//            final BotWebViewContainer.MyWebView webView = pages[0].getWebView();
//            if (webView == null) return false;
//            final WebBackForwardList history = webView.copyBackForwardList();
//            final int currentIndex = history.getCurrentIndex();
//            if (history.getSize() - (currentIndex + 1) <= 0) return false;
//
//            ItemOptions options = ItemOptions.makeOptions(sheet != null ? sheet.windowView : windowView, v);
//            final int textColor = AndroidUtilities.computePerceivedBrightness(pages[0].getBackgroundColor()) >= .721f ? Color.BLACK : Color.WHITE;
//            final int subtextColor = Theme.multAlpha(textColor, .65f);
//
//            for (int i = currentIndex + 1; i < history.getSize(); ++i) {
//                WebHistoryItem item = history.getItemAtIndex(i);
//                final int index = i;
//                options.add(item.getTitle(), () -> {
//                    for (int j = 0; j < (index - currentIndex); ++j) webView.goForward();
//                });
//                ActionBarMenuSubItem menuItem = options.getLast();
//                if (menuItem != null) {
//                    menuItem.setSubtext(item.getUrl());
//                    Bitmap bitmap = webView.getFavicon(item.getUrl());
//                    if (bitmap == null) {
//                        bitmap = item.getFavicon();
//                    }
//                    final Bitmap finalBitmap = bitmap;
//                    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
//                    menuItem.setTextAndIcon(item.getTitle(), 0, new Drawable() {
//                        @Override
//                        public void draw(@NonNull Canvas canvas) {
//                            if (finalBitmap != null) {
//                                canvas.save();
//                                canvas.translate(getBounds().left, getBounds().top);
//                                canvas.scale(getBounds().width() / (float) finalBitmap.getWidth(), getBounds().height() / (float) finalBitmap.getHeight());
//                                canvas.drawBitmap(finalBitmap, 0, 0, paint);
//                                canvas.restore();
//                            }
//                        }
//                        @Override
//                        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
//                        @Override
//                        public void setAlpha(int alpha) {}
//                        @Override
//                        public int getIntrinsicHeight() {
//                            return dp(24);
//                        }
//                        @Override
//                        public int getIntrinsicWidth() {
//                            return dp(24);
//                        }
//                        @Override
//                        public int getOpacity() {
//                            return PixelFormat.TRANSPARENT;
//                        }
//                    });
//                    menuItem.setColors(textColor, textColor);
//                    menuItem.setSubtextColor(subtextColor);
//                }
//            }
//            options.setScrimViewBackground(Theme.createCircleDrawable(dp(40), actionBar.getBackgroundColor()));
//            options.setBackgroundColor(pages[0].getBackgroundColor());
//            checkScrollAnimated(options::show);
//
//            return true;
//        });

        actionBar.setMenuListener(id -> {
            if (pages[0].isArticle() && pages[0].adapter.currentPage == null || parentActivity == null) {
                return;
            }
            if (id == WebActionBar.search_item) {
                actionBar.setHeight(currentHeaderHeight = dp(56));
                actionBar.showSearch(true, true);
            } else if (id == WebActionBar.share_item) {
                String url;
                if (pages[0].isWeb()) {
                    if (pages[0].getWebView() == null) return;
                    url = pages[0].getWebView().getUrl();
                } else {
                    if (pages[0].adapter.currentPage == null) return;
                    url = pages[0].adapter.currentPage.url;
                }
                url = BotWebViewContainer.magic2tonsite(url);
                showDialog(new ShareAlert(parentActivity, null, url, false, url, false, AndroidUtilities.computePerceivedBrightness(actionBar.getBackgroundColor()) < .721f ? new DarkThemeResourceProvider() : null));
            } else if (id == WebActionBar.bookmark_item) {
                String url;
                final FrameLayout container;
                if (pages[0].isWeb()) {
                    if (pages[0].getWebView() == null) return;
                    url = pages[0].getWebView().getUrl();
                    container = pages[0].webViewContainer;
                } else {
                    if (pages[0].adapter.currentPage == null) return;
                    url = pages[0].adapter.currentPage.url;
                    container = pages[0];
                }
                addBookmark(url, currentAccount, container, sheet, getResourcesProvider());
            } else if (id == WebActionBar.bookmarks_item) {
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    lastFragment.showAsSheet(new BookmarksFragment(sheet == null ? null : () -> sheet.dismiss(true), ArticleViewer.this::openBookmark), params);
                }
            } else if (id == WebActionBar.history_item) {
                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                params.transitionFromLeft = true;
                BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                if (lastFragment != null) {
                    lastFragment.showAsSheet(new HistoryFragment(sheet == null ? null : () -> sheet.dismiss(true), ArticleViewer.this::openHistoryEntry), params);
                }
            } else if (id == WebActionBar.forward_item) {
                if (pages[0].getWebView() != null) {
                    pages[0].getWebView().goForward();
                }
            } else if (id == WebActionBar.open_item) {
                File file = null;
                String webPageUrl, webPageOpenUrl;
                if (pages[0].isWeb()) {
                    if (pages[0].getWebView() == null) return;
                    webPageUrl = pages[0].getWebView().getUrl();
                    webPageOpenUrl = pages[0].getWebView().getOpenURL();
                } else {
                    if (pages[0].adapter.currentPage == null) return;
                    webPageUrl = pages[0].adapter.currentPage.url;
                    webPageOpenUrl = null;
                    if (pages[0].adapter.currentPage.cached_page != null) {
                        file = pages[0].adapter.currentPage.cached_page.local;
                    }
                }
                if (parentActivity == null || parentActivity.isFinishing()) return;
                if (file != null) {
                    AndroidUtilities.openForView(file, null, "text/markdown", parentActivity, getResourcesProvider(), true);
                    return;
                }
                if (webPageUrl == null) return;
                final String open_domain = AndroidUtilities.getHostAuthority(webPageOpenUrl, true);
                final String domain = AndroidUtilities.getHostAuthority(webPageUrl, true);
                final Runnable open = () -> {
                    Browser.openInExternalBrowser(parentActivity, webPageUrl, false);
                };
                final Utilities.Callback<Boolean> restrict = waitForResume -> {
                    MessagesController.getInstance(currentAccount).addWebBrowserException(domain, true);
                    if (!TextUtils.isEmpty(open_domain) && !TextUtils.equals(open_domain, domain)) {
                        MessagesController.getInstance(currentAccount).addWebBrowserException(open_domain, true);
                    }
                    if (!waitForResume) {
                        showRestrictedWebsiteToast();
                    } else {
                        LaunchActivity.whenResumed = this::showRestrictedWebsiteToast;
                    }
                };
                if (pages[0].isWeb() && MessagesController.getInstance(currentAccount).isWebBrowserOpenInApp(domain) && !MessagesController.getInstance(currentAccount).isWebBrowserExceptionsLimitReached(true)) {
                    AlertsCreator.showOpenExternalBrowserAlert(activity, getResourcesProvider(), webPageUrl, true, true, (confirmed, checked) -> {
                        if (confirmed) {
                            if (checked) restrict.run(true);
                            open.run();
                        }
                    });
                } else open.run();
            } else if (id == WebActionBar.settings_item) {
                if (pages[0].isWeb()) {
                    openWebSettings();
                } else {

                    BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                    builder.setApplyTopPadding(false);

                    LinearLayout settingsContainer = new LinearLayout(parentActivity);
                    settingsContainer.setPadding(0, 0, 0, dp(4));
                    settingsContainer.setOrientation(LinearLayout.VERTICAL);

                    HeaderCell headerCell = new HeaderCell(parentActivity, getResourcesProvider());
                    headerCell.setText(LocaleController.getString(R.string.FontSize));
                    settingsContainer.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 1, 3, 0));

                    TextSizeCell sizeCell = new TextSizeCell(parentActivity);
                    settingsContainer.addView(sizeCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 0, 3, 0));

                    headerCell = new HeaderCell(parentActivity, getResourcesProvider());
                    headerCell.setText(LocaleController.getString(R.string.FontType));
                    settingsContainer.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 3, 4, 3, 2));

                    for (int a = 0; a < 2; a++) {
                        fontCells[a] = new FontCell(parentActivity);
                        switch (a) {
                            case 0:
                                fontCells[a].setTextAndTypeface(LocaleController.getString(R.string.Default), Typeface.DEFAULT);
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
                            resources.updatePaintFonts(selectedFont);
                            for (int i = 0; i < pages.length; i++) {
                                pages[i].adapter.notifyDataSetChanged();
                            }
                        });
                        settingsContainer.addView(fontCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    }

                    builder.setCustomView(settingsContainer);
                    showDialog(linkSheet = builder.create());
                }
            } else if (id == WebActionBar.reload_item) {
                if (pages[0].isWeb() && pages[0].getWebView() != null) {
                    pages[0].getWebView().reload();
                }
            } else if (id == WebActionBar.instant_item) {
                WebInstantView.Loader loader = pages[0].currentInstantLoader;
                if (loader != null && loader.getWebPage() != null) {
                    addPageToStack(loader.getWebPage(), null, 1);
                }
            }
        });

        actionBar.forwardButton.setOnClickListener(v -> {
            if (sheet != null) {
//                if (pages[0].hasForwardButton()) {
//                    if (pages[0].getWebView() != null) {
//                        pages[0].getWebView().goForward();
//                    }
//                } else {
                    sheet.dismiss(true);
//                }
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
        searchPanel.setTranslationY(dp(51));
        searchPanel.setVisibility(View.INVISIBLE);
        searchPanel.setFocusable(true);
        searchPanel.setFocusableInTouchMode(true);
        searchPanel.setClickable(true);
        searchPanel.setPadding(0, dp(3), 0, 0);
        containerView.addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        new KeyboardNotifier(windowView, (keyboardHeight) -> {
            searchPanel.setTranslationY((searchPanelTranslation = -keyboardHeight) + dp(51) * (1f - searchPanelAlpha));
        });

        searchUpButton = new ImageView(parentActivity);
        searchUpButton.setScaleType(ImageView.ScaleType.CENTER);
        searchUpButton.setImageResource(R.drawable.msg_go_up);
        searchUpButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        searchUpButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchPanel.addView(searchUpButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 48, 0));
        searchUpButton.setOnClickListener(view -> {
            if (pages[0].isWeb()) {
                if (pages[0].getWebView() != null) {
                    pages[0].getWebView().findNext(false);
                }
            } else {
                scrollToSearchIndex(currentSearchIndex - 1);
            }
        });
        searchUpButton.setContentDescription(LocaleController.getString(R.string.AccDescrSearchNext));

        searchDownButton = new ImageView(parentActivity);
        searchDownButton.setScaleType(ImageView.ScaleType.CENTER);
        searchDownButton.setImageResource(R.drawable.msg_go_down);
        searchDownButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        searchDownButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        searchPanel.addView(searchDownButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));
        searchDownButton.setOnClickListener(view -> {
            if (pages[0].isWeb()) {
                if (pages[0].getWebView() != null) {
                    pages[0].getWebView().findNext(true);
                }
            } else {
                scrollToSearchIndex(currentSearchIndex + 1);
            }
        });
        searchDownButton.setContentDescription(LocaleController.getString(R.string.AccDescrSearchPrev));

        searchCountText = new AnimatedTextView(parentActivity, true, true, true);
        searchCountText.setScaleProperty(.6f);
        searchCountText.setAnimationProperties(0.4f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        searchCountText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchCountText.setTextSize(dp(15));
        searchCountText.setTypeface(AndroidUtilities.bold());
        searchCountText.setGravity(Gravity.LEFT);
        searchCountText.getDrawable().setOverrideFullWidth(AndroidUtilities.displaySize.x);
        searchPanel.addView(searchCountText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 108, 0));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW - 1;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        int navigationColor = sheet == null ? Theme.getColor(Theme.key_windowBackgroundGray, null, true) : getThemedColor(Theme.key_windowBackgroundGray);
        float navigationBrightness = AndroidUtilities.computePerceivedBrightness(navigationColor);
        boolean isLightNavigation = navigationBrightness >= 0.721f;
        if (isLightNavigation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        navigationBarPaint.setColor(navigationColor);
        windowLayoutParams.systemUiVisibility = uiFlags;

        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        if (Build.VERSION.SDK_INT >= 28) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        textSelectionHelper = new TextSelectionHelper.ArticleTextSelectionHelper();
        textSelectionHelper.setParentView(pages[0].listView);
        if (MessagesController.getInstance(currentAccount).getTranslateController().isContextTranslateEnabled()) {
            textSelectionHelper.setOnTranslate((text, fromLang, toLang, onAlertDismiss) -> {
                TranslateAlert2.showAlert(parentActivity, parentFragment, currentAccount, fromLang, toLang, text, null, false, null, onAlertDismiss);
//                final TranslateAlert3 alert =
//                    new TranslateAlert3(parentActivity, parentFragment != null ? parentFragment.getResourceProvider() : getResourcesProvider())
//                        .setText(fromLang, text);
//                alert.setOnDismissListener(onAlertDismiss);
//                alert.show();
            });
        }
        textSelectionHelper.layoutManager = pages[0].layoutManager;
        textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                if (isSelected) {
                    actionBar.showSearch(false, true);
                }
            }

            @Override
            public void onTextCopied() {
                if (AndroidUtilities.shouldShowClipboardToast()) {
                    BulletinFactory.of(containerView, null).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                }
            }
        });
        containerView.addView(textSelectionHelper.getOverlayView(activity));

        pinchToZoomHelper = new PinchToZoomHelper(containerView, containerView);
        pinchToZoomHelper.setClipBoundsListener(topBottom -> {
            topBottom[0] = currentHeaderHeight;
            topBottom[1] = pages[0].listView.getMeasuredHeight();
        });
        pinchToZoomHelper.setCallback(new PinchToZoomHelper.Callback() {
            @Override
            public void onZoomStarted(MessageObject messageObject) {
                if (pages[0] != null) {
                    pages[0].listView.cancelClickRunnables(true);
                }
            }
        });
        backgroundPaint.setColor(getThemedColor(Theme.key_iv_background));
        updatePaintColors(this);
    }

    public static void addBookmark(String url, int currentAccount, FrameLayout container, Sheet sheet, Theme.ResourcesProvider resourcesProvider) {
        url = BotWebViewContainer.magic2tonsite(url);
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(url, selfId));
        TLRPC.TL_message msg = new TLRPC.TL_message();
        msg.peer_id = new TLRPC.TL_peerUser();
        msg.peer_id.user_id = selfId;
        msg.from_id = new TLRPC.TL_peerUser();
        msg.from_id.user_id = selfId;
        msg.message = url;
        msg.media = new TLRPC.TL_messageMediaWebPage();
        msg.media.webpage = new TLRPC.TL_webPage();
        msg.media.webpage.url = url;
        msg.media.webpage.display_url = url;
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.bookmarkAdded, new MessageObject(currentAccount, msg, false, false));
        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.WebBookmarkedToast), () -> {
            if (sheet != null) {
                sheet.dismiss(true);
            }
            BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", selfId);
                lastFragment.presentFragment(new ChatActivity(args));
            }
        })).show(true);
    }

    private boolean showRestrictedToastOnResume;
    private void showRestrictedWebsiteToast() {
        showRestrictedToastOnResume = false;
        if (!attachedToWindow || LaunchActivity.instance == null || LaunchActivity.instance.isFinishing()) return;
        final FrameLayout container;
        if (pages[0].isWeb()) {
            if (pages[0].getWebView() == null) return;
            container = pages[0].webViewContainer;
        } else {
            if (pages[0].adapter.currentPage == null) return;
            container = pages[0];
        }
        BulletinFactory.of(container, getResourcesProvider())
            .createSimpleBulletin(R.raw.chats_infotip, AndroidUtilities.replaceSingleTag(getString(R.string.BrowserExternalRestricted), this::openWebSettings), 4)
            .show(true);
    }

    public void openBookmark(String link) {
        if (parentActivity == null) return;
        if (link == null) return;
        actionBar.showAddress(false, true);
        if (Browser.isInternalUri(Uri.parse(link), null)) {
            if (sheet != null) {
                sheet.dismiss(true);
            }
            Browser.openAsInternalIntent(parentActivity, link);
        } else if (!Browser.openInExternalApp(parentActivity, link, false)) {
            if (pages[0] == null || pages[0].getWebView() == null) {
                Browser.openInTelegramBrowser(parentActivity, link, null);
            } else {
                pages[0].getWebView().loadUrl(link);
            }
        }
    }

    public void openHistoryEntry(BrowserHistory.Entry entry) {
        if (parentActivity == null || entry == null) return;
        actionBar.showAddress(false, true);
        if (pages[0] == null || pages[0].getWebView() == null) {
            Browser.openInTelegramBrowser(parentActivity, entry.url, null);
        } else {
            pages[0].getWebView().loadUrl(entry.url, entry.meta);
        }
    }

    public void openWebSettings() {
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null) {
            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
            params.transitionFromLeft = true;
            lastFragment.showAsSheet(new WebBrowserSettings(this::openHistoryEntry), params);
        }
    }

    private void checkVideoPlayer() {
        RecyclerView recyclerView = pages[0].listView;
        if (recyclerView == null || !attachedToWindow) {
            return;
        }
        BlockVideoCell bestView = null;
        float bestViewCenterX = 0;
        float parentCenterX = recyclerView.getMeasuredHeight() / 2f;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child instanceof BlockVideoCell) {
                float centerX = child.getTop() + child.getMeasuredHeight() / 2f;
                if (bestView == null || (Math.abs(parentCenterX - centerX) < (Math.abs(parentCenterX - bestViewCenterX)))) {
                    bestView = (BlockVideoCell) child;
                    bestViewCenterX = centerX;
                }
            }
        }
        boolean allowPlayer = !PhotoViewer.getInstance().isVisibleOrAnimating();
        if (!allowPlayer || (currentPlayer != null && currentPlayer != bestView && videoPlayer != null)) {
            if (videoPlayer != null) {
                videoStates.put(currentPlayer.currentBlock.video_id, currentPlayer.setState(BlockVideoCellState.fromPlayer(videoPlayer, currentPlayer)));
                if (currentPlayer.videoState != null) {
                    if (currentPlayer.videoState.lastFrameBitmap != null) {
                        currentPlayer.imageView.setImageBitmap(currentPlayer.videoState.lastFrameBitmap);
                    }
                    currentPlayer.updateButtonState(false);
                }
                videoPlayer.release(null);
            }
            videoPlayer = null;
            currentPlayer = null;
        }
        if (allowPlayer && bestView != null) {
            bestView.startVideoPlayer();
            currentPlayer = bestView;
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
        if (searchResults == null && !pages[0].isWeb()) {
            return;
        }
        int index, count;
        if (pages[0].isWeb()) {
            index = pages[0].getWebView() == null ? 0 : pages[0].getWebView().getSearchIndex();
            count = pages[0].getWebView() == null ? 0 : pages[0].getWebView().getSearchCount();
        } else {
            index = currentSearchIndex;
            count = searchResults.size();
        }
        searchUpButton.setEnabled(count > 0 && index != 0);
        searchDownButton.setEnabled(count > 0 && index != count - 1);
        searchUpButton.setAlpha(searchUpButton.isEnabled() ? 1.0f : 0.5f);
        searchDownButton.setAlpha(searchDownButton.isEnabled() ? 1.0f : 0.5f);
        searchCountText.cancelAnimation();
        if (count < 0) {
            searchCountText.setText("");
        } else if (count == 0) {
            searchCountText.setText(LocaleController.getString(R.string.NoResult));
        } else if (count == 1) {
            searchCountText.setText(LocaleController.getString(R.string.OneResult));
        } else {
            searchCountText.setText(String.format(LocaleController.getPluralString("CountOfResults", count), index + 1, count));
        }
    }

    private Runnable searchRunnable;
    private int lastSearchIndex = -1;

    public static class SearchResult {
        private int index;
        private Object text;
        private TL_iv.PageBlock block;
    }

    private void processSearch(final String text) {
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
        if (TextUtils.isEmpty(text)) {
            searchResults.clear();
            searchText = text;
            pages[0].adapter.searchTextOffset.clear();
            showSearchPanel(false);
            if (pages[0].isWeb()) {
                if (pages[0].getWebView() != null) {
                    pages[0].getWebView().search("", this::updateSearchButtons);
                    updateSearchButtons();
                }
            } else {
                pages[0].listView.invalidateViews();
                scrollToSearchIndex(0);
            }
            lastSearchIndex = -1;
            return;
        }
        int searchIndex = ++lastSearchIndex;
        if (pages[0].isWeb()) {
            showSearchPanel(true);
            if (pages[0].getWebView() != null) {
                pages[0].getWebView().search(text, this::updateSearchButtons);
                updateSearchButtons();
            }
        } else {
            AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                HashMap<Object, TL_iv.PageBlock> copy = new HashMap<>(pages[0].adapter.textToBlocks);
                ArrayList<Object> array = new ArrayList<>(pages[0].adapter.textBlocks);
                searchRunnable = null;
                Utilities.searchQueue.postRunnable(() -> {
                    ArrayList<SearchResult> results = new ArrayList<>();
                    for (int b = 0, N = array.size(); b < N; b++) {
                        Object object = array.get(b);
                        TL_iv.PageBlock block = copy.get(object);
                        String textToSearchIn = null;
                        if (object instanceof TL_iv.RichText) {
                            TL_iv.RichText richText = (TL_iv.RichText) object;
                            CharSequence innerText = getText(pages[0].adapter, null, richText, richText, block, 1000);
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
                            showSearchPanel(true);
                            searchResults = results;
                            searchText = text;
                            pages[0].adapter.searchTextOffset.clear();
                            pages[0].listView.invalidateViews();
                            scrollToSearchIndex(0);
                        }
                    });
                });
            }, 400);
        }
    }

    private ValueAnimator searchPanelAnimator;
    private float searchPanelAlpha;
    public void showSearchPanel(boolean show) {
        searchPanel.setVisibility(View.VISIBLE);
        if (searchPanelAnimator != null) {
            searchPanelAnimator.cancel();
        }
        searchPanelAnimator = ValueAnimator.ofFloat(searchPanelAlpha, show ? 1f : 0f);
        searchPanelAnimator.addUpdateListener(anm -> {
            searchPanelAlpha = (float) anm.getAnimatedValue();
            searchPanel.setTranslationY(searchPanelTranslation + (1f - searchPanelAlpha) * dp(51));
        });
        searchPanelAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                searchPanelAlpha = show ? 1f : 0f;
                searchPanel.setTranslationY(searchPanelTranslation + (1f - searchPanelAlpha) * dp(51));
                if (!show) {
                    searchPanel.setVisibility(View.GONE);
                }
            }
        });
        searchPanelAnimator.setDuration(320);
        searchPanelAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        searchPanelAnimator.start();
    }

    private void scrollToSearchIndex(int index) {
        if (index < 0 || index >= searchResults.size()) {
            updateSearchButtons();
            return;
        }
        currentSearchIndex = index;
        updateSearchButtons();
        SearchResult result = searchResults.get(index);
        TL_iv.PageBlock block = getLastNonListPageBlock(result.block);

        int row = -1;
        for (int a = 0, N = pages[0].adapter.blocks.size(); a < N; a++) {
            TL_iv.PageBlock localBlock = pages[0].adapter.blocks.get(a);
            if (localBlock instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild child = (TL_pageBlockDetailsChild) localBlock;
                if (child.block == result.block || child.block == block) {
                    if (openAllParentBlocks(child)) {
                        pages[0].adapter.updateRows();
                        pages[0].adapter.notifyDataSetChanged();
                    }
                    break;
                }
            }
        }
        for (int a = 0, N = pages[0].adapter.localBlocks.size(); a < N; a++) {
            TL_iv.PageBlock localBlock = pages[0].adapter.localBlocks.get(a);
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
                pages[0].adapter.updateRows();
                pages[0].adapter.notifyDataSetChanged();
            }
        }

        String key = searchText + result.block + result.text + result.index;
        Integer offset = pages[0].adapter.searchTextOffset.get(key);
        if (offset == null) {
            int type = pages[0].adapter.getTypeForBlock(result.block);
            RecyclerView.ViewHolder holder = pages[0].adapter.onCreateViewHolder(null, type);
            pages[0].adapter.bindBlockToHolder(type, holder, result.block, 0, 0, false);
            holder.itemView.measure(View.MeasureSpec.makeMeasureSpec(pages[0].listView.getMeasuredWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            offset = pages[0].adapter.searchTextOffset.get(key);
            if (offset == null) {
                offset = 0;
            }
        }
        SmoothScroller s = new SmoothScroller(pages[0].getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return SNAP_TO_START;
            }
        };
        if (pages[0].adapter.padding) {
            row++;
        }
        s.setTargetPosition( + row);
        s.setOffset(-(currentHeaderHeight - dp(56) - offset + dp(100)));
        s.setDurationScale(1.2f);
        pages[0].layoutManager.startSmoothScroll(s);
        pages[0].listView.invalidateViews();
    }

    public static class ScrollEvaluator extends IntEvaluator {

        @Override
        public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
            return super.evaluate(fraction, startValue, endValue);
        }
    }

    private void checkScrollAnimated() {
        checkScrollAnimated(null);
    }
    private void checkScrollAnimated(Runnable callback) {
        int maxHeight = dp(56);
        if (currentHeaderHeight == maxHeight) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        ValueAnimator va = ValueAnimator.ofObject(new IntEvaluator(), currentHeaderHeight, dp(56)).setDuration(180);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(animation -> setCurrentHeaderHeight((int) animation.getAnimatedValue()));
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (callback != null) {
                    callback.run();
                }
            }
        });
        if (callback != null) {
            va.setDuration(va.getDuration() / 2);
        }
        va.start();
    }

    private void setCurrentHeaderHeight(int newHeight) {
        if (actionBar == null || actionBar.isSearching() || actionBar.isAddressing()) {
            return;
        }
        currentHeaderHeight = Utilities.clamp(newHeight, dp(56), dp(24));
        actionBar.setHeight(currentHeaderHeight);
        textSelectionHelper.setTopOffset(currentHeaderHeight);
        for (int i = 0; i < pages.length; i++) {
            pages[i].listView.setTopGlowOffset(currentHeaderHeight);
        }
    }

    private void checkScroll(int dy) {
        if (sheet != null && !sheet.attachedToActionBar) return;
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
        return open(messageObject, null, null, null, null);
    }

    public boolean open(MessageObject messageObject, TLRPC.WebPage webpage) {
        return open(messageObject, webpage, null, null, null);
    }

    public boolean open(TLRPC.TL_webPage webpage, String url) {
        return open(null, webpage, url, null, null);
    }

    public boolean open(String url) {
        return open(null, null, null, url, null);
    }

    public boolean open(String url, Browser.Progress progress) {
        return open(null, null, null, url, progress);
    }

    private boolean open(final MessageObject messageObject, TLRPC.WebPage webpage, String url, String webUrl, Browser.Progress progress) {
        if (parentActivity == null || sheet == null && isVisible && !collapsed) {
            return false;
        }

        if (parentFragment != null && parentFragment.getParentLayout() instanceof ActionBarLayout) {
            AndroidUtilities.hideKeyboard((ActionBarLayout) parentFragment.getParentLayout());
        }

        boolean localWebpage = false;
        String anchor = null;
        if (messageObject != null) {
            if (webpage == null) {
                webpage = messageObject.messageOwner.media.webpage;
            }
            if (webpage != null && webpage.cached_page != null && webpage.cached_page.local != null) {
                localWebpage = true;
            }
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

        final boolean openingAbove = sheet != null && !pagesStack.isEmpty();

        collapsed = false;
        if (openingAbove) {

        } else {
            pagesStack.clear();
            containerView.setTranslationX(0);
            if (sheet != null) {
                sheet.setBackProgress(0);
            }
            containerView.setTranslationY(0);
            pages[0].setTranslationY(0);
            pages[0].setTranslationX(0.0f);
            pages[1].setTranslationX(0.0f);
            pages[0].setAlpha(1.0f);
            windowView.setInnerTranslationX(0);

            pages[0].scrollToTop(false);
            //        if (first) {
            setCurrentHeaderHeight(dp(56));
            //        } else {
            //            checkScrollAnimated();
            //        }
        }

        boolean scrolledToAnchor;

        if (sheet != null && BotWebViewContainer.firstWebView) {
            sheet.animationsLock.lock();
        }

        if (webpage != null) {
            scrolledToAnchor = addPageToStack(webpage, anchor, openingAbove ? 1 : 0);
            if (!localWebpage) {
                final String anchorFinal = !scrolledToAnchor && anchor != null ? anchor : null;
                final TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
                req.url = webpage.url;
                if (webpage.cached_page instanceof TL_iv.TL_pagePart_layer82 || webpage.cached_page.part) {
                    req.hash = 0;
                } else {
                    req.hash = webpage.hash;
                }
                final TLRPC.WebPage webPageFinal = webpage;
                final int currentAccount = messageObject != null ? messageObject.currentAccount : UserConfig.selectedAccount;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (_response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    Object response = _response;
                    if (response instanceof TLRPC.TL_messages_webPage) {
                        TLRPC.TL_messages_webPage res = (TLRPC.TL_messages_webPage) response;
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        response = res.webpage;
                    }
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
                                MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.getDialogId(), -2, 0, false, messageObject.scheduled ? 1 : 0, 0);
                            }
                            if (openingAbove) {
                                pagesStack.add(webPage);
                            } else {
                                pagesStack.set(0, webPage);
                            }
                            if (pagesStack.size() == 1) {
                                ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().remove("article" + webPage.id).commit();
                                updateInterfaceForCurrentPage(webPage, false, openingAbove ? 1 : 0);
                                if (anchorFinal != null) {
                                    scrollToAnchor(anchorFinal, false);
                                }
                            }
                        }

                        LongSparseArray<TLRPC.WebPage> webpages = new LongSparseArray<>(1);
                        webpages.put(webPage.id, webPage);
                        MessagesStorage.getInstance(currentAccount).putWebPages(webpages);
                    } else if (response instanceof TLRPC.TL_webPageNotModified) {
                        final TLRPC.TL_webPageNotModified webPage = (TLRPC.TL_webPageNotModified) response;
                        if (webPageFinal != null && webPageFinal.cached_page != null) {
                            if (webPageFinal.cached_page.views != webPage.cached_page_views) {
                                webPageFinal.cached_page.views = webPage.cached_page_views;
                                webPageFinal.cached_page.flags |= 8;
                                for (int a = 0; a < pages.length; a++) {
                                    if (pages[a].adapter.currentPage == webPageFinal) {
                                        int p = pages[a].adapter.getItemCount() - 1;
                                        RecyclerView.ViewHolder holder = pages[a].listView.findViewHolderForAdapterPosition(p);
                                        if (holder != null) {
                                            pages[a].adapter.onViewAttachedToWindow(holder);
                                        }
                                    }
                                }
                                if (messageObject != null) {
                                    TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                                    messagesRes.messages.add(messageObject.messageOwner);
                                    MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.getDialogId(), -2, 0, false, messageObject.scheduled ? 1 : 0, 0);
                                }
                            }
                        }
                    }
                }));
            }
        } else {
            scrolledToAnchor = addPageToStack(webUrl, openingAbove ? 1 : 0);
        }
        if (actionBar != null && !openingAbove) {
            actionBar.setIsLocal(pages[0].isLocal());
        }

        lastInsets = null;
        if (sheet != null) {
            if (!openingAbove) {
                AndroidUtilities.removeFromParent(windowView);
                sheet.setContainerView(windowView);
                sheet.windowView.addView(windowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
        } else if (!isVisible) {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            if (attachedToWindow) {
                try {
                    wm.removeView(windowView);
                } catch (Exception e) {
                    //ignore
                }
            }
            try {
                windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
                if (Build.VERSION.SDK_INT >= 28) {
                    windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
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

        if (openingAbove) {

        } else if (sheet != null) {
            if (openingAbove) {
                if (sheet != null) {
                    sheet.animationsLock.unlock();
                }
            } else {
                if (progress != null) {
                    // feature: to show progress before opening webview
//                    BotWebViewContainer.MyWebView webView = pages[0].getWebView();
//                    if (webView != null) {
//                        boolean[] cancelled = new boolean[] { false };
//                        progress.onCancel(() -> {
//                            cancelled[0] = true;
//                            sheet.dismissInstant();
//                        });
//                        progress.init();
//                        webView.whenPageLoaded(() -> {
//                            progress.end();
//                            if (!cancelled[0]) {
//                                AndroidUtilities.runOnUIThread(sheet::show, 80);
//                            }
//                        }, 1200);
//                        return true;
//                    }
                }
                sheet.show();
            }
        } else {
            windowView.setAlpha(0);
            containerView.setAlpha(0);

            final AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(windowView, View.ALPHA, 0, 1.0f),
                    ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, dp(56), 0)
            );

            animationEndRunnable = () -> {
                if (containerView == null || windowView == null) {
                    return;
                }
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                animationInProgress = 0;
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            };

            animatorSet.setDuration(150);
            animatorSet.setInterpolator(interpolator);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AndroidUtilities.runOnUIThread(() -> {
                        notificationsLocker.unlock();
                        if (animationEndRunnable != null) {
                            animationEndRunnable.run();
                            animationEndRunnable = null;
                        }
                    });
                }
            });
            transitionAnimationStartTime = System.currentTimeMillis();
            AndroidUtilities.runOnUIThread(() -> {
                notificationsLocker.lock();
                animatorSet.start();
            });
        }
        containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return true;
    }

    private void showProgressView(boolean useLine, final boolean show) {
        if (useLine) {
            AndroidUtilities.cancelRunOnUIThread(lineProgressTickRunnable);
            if (show) {
                actionBar.lineProgressView.setProgress(0.0f, false);
                actionBar.lineProgressView.setProgress(0.3f, true);
                AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100);
            } else {
                actionBar.lineProgressView.setProgress(1.0f, true);
            }
        } else {
            if (progressViewAnimation != null) {
                progressViewAnimation.cancel();
            }
            progressViewAnimation = new AnimatorSet();
            if (show) {
                progressView.setVisibility(View.VISIBLE);
//                menuContainer.setEnabled(false);
                progressViewAnimation.playTogether(
//                        ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 0.1f),
//                        ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 0.1f),
//                        ObjectAnimator.ofFloat(menuButton, View.ALPHA, 0.0f),
//                        ObjectAnimator.ofFloat(collapseButton, View.SCALE_X, 0.1f),
//                        ObjectAnimator.ofFloat(collapseButton, View.SCALE_Y, 0.1f),
//                        ObjectAnimator.ofFloat(collapseButton, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f));
            } else {
//                menuButton.setVisibility(View.VISIBLE);
//                collapseButton.setVisibility(View.VISIBLE);
//                menuContainer.setEnabled(true);
                progressViewAnimation.playTogether(
                        ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f)//,
//                        ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 1.0f),
//                        ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 1.0f),
//                        ObjectAnimator.ofFloat(menuButton, View.ALPHA, 1.0f),
//                        ObjectAnimator.ofFloat(collapseButton, View.SCALE_X, 1.0f),
//                        ObjectAnimator.ofFloat(collapseButton, View.SCALE_Y, 1.0f),
//                        ObjectAnimator.ofFloat(collapseButton, View.ALPHA, 1.0f)
                );
            }
            progressViewAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (progressViewAnimation != null && progressViewAnimation.equals(animation)) {
                        if (!show) {
                            progressView.setVisibility(View.INVISIBLE);
                        } else {
//                            menuButton.setVisibility(View.INVISIBLE);
//                            collapseButton.setVisibility(View.INVISIBLE);
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

    private void saveCurrentPagePosition() {
        if (pages[0].adapter.currentPage == null) {
            return;
        }
        int position = pages[0].layoutManager.findFirstVisibleItemPosition();
        if (position != RecyclerView.NO_POSITION) {
            int offset;
            View view = pages[0].layoutManager.findViewByPosition(position);
            if (view != null) {
                offset = view.getTop();
            } else {
                offset = 0;
            }
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit();
            String key = "article" + pages[0].adapter.currentPage.id;
            editor.putInt(key, position).putInt(key + "o", offset).putBoolean(key + "r", AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y).commit();
        }
    }

    private void refreshThemeColors() {
        if (deleteView != null) {
            deleteView.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 2));
            deleteView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        }
        if (popupLayout != null) {
            popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
        }
        if (searchUpButton != null) {
            searchUpButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            searchUpButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        }
        if (searchDownButton != null) {
            searchDownButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            searchDownButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), 1));
        }
        if (searchCountText != null) {
            searchCountText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        if (actionBar != null) {
            actionBar.setMenuColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getBackgroundColor() : getThemedColor(Theme.key_iv_background));
            actionBar.setColors(pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getActionBarColor() : getThemedColor(Theme.key_iv_background), true);
        }
        backgroundPaint.setColor(getThemedColor(Theme.key_iv_background));
    }

    public void close(boolean byBackPress, boolean force) {
        if (parentActivity == null || closeAnimationInProgress || !isVisible || checkAnimation()) {
            return;
        }
        if (sheet != null) {
            sheet.dismiss(false);
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
            if (!force) {
                return;
            }
        }
        if (textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.clear();
            return;
        }
        if (actionBar.isSearching()) {
            actionBar.showSearch(false, true);
            return;
        }
        if (actionBar.isAddressing()) {
            actionBar.showAddress(false, true);
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.emojiLoaded);

        if (activityVisibilityController != null) {
            activityVisibilityController.destroy();
            activityVisibilityController = null;
        }

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
                ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, 0, dp(56))
        );
        animationInProgress = 2;
        animationEndRunnable = () -> {
            if (containerView == null) {
                return;
            }
            containerView.setLayerType(View.LAYER_TYPE_NONE, null);
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
        containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        animatorSet.start();

        for (int i = 0; i < videoStates.size(); ++i) {
            BlockVideoCellState state = videoStates.valueAt(i);
            if (state.lastFrameBitmap != null) {
                state.lastFrameBitmap.recycle();
                state.lastFrameBitmap = null;
            }
        }
        videoStates.clear();
    }

    private void onClosed() {
        isVisible = false;
        for (int i = 0; i < pages.length; i++) {
            pages[i].cleanup();
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
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.articleClosed);
    }

    public static void loadChannel(IArticleViewer parent, final BlockChannelCell cell, WebpageAdapter adapter, TLRPC.Chat channel) {
        if (parent.loadingChannel || !ChatObject.isPublic(channel)) {
            return;
        }
        parent.loadingChannel = true;
        final int currentAccount = parent.getCurrentAccount();
        final TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = channel.username;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            parent.loadingChannel = false;
            if (adapter.blocks.isEmpty()) return;
            if (error == null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                if (!res.chats.isEmpty()) {
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true);
                    parent.loadedChannel = res.chats.get(0);
                    if (parent.loadedChannel.left && !parent.loadedChannel.kicked) {
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

    public static void joinChannel(int currentAccount, final BlockChannelCell cell, final TLRPC.Chat channel) {
        final TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        req.channel = MessagesController.getInputChannel(channel);
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    cell.setState(0, false);
                    AlertsCreator.processError(currentAccount, error, null, req, true);
                });
                return;
            }

            boolean hasJoinMessage = false;
            if (response instanceof TLRPC.TL_chatInviteJoinResultOk) {
                final TLRPC.Updates updates = ((TLRPC.TL_chatInviteJoinResultOk) response).updates;
                for (int a = 0; a < updates.updates.size(); a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (update instanceof TL_update.TL_updateNewChannelMessage) {
                        if (((TL_update.TL_updateNewChannelMessage) update).message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            hasJoinMessage = true;
                            break;
                        }
                    }
                }
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            } else if (response instanceof TLRPC.TL_chatInviteJoinResultWebView) {
                TLRPC.TL_chatInviteJoinResultWebView resultWebView = (TLRPC.TL_chatInviteJoinResultWebView) response;
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getInstance(currentAccount).putUsers(resultWebView.users, false);
                    BotGuardHelper.getInstance(currentAccount).openGuardBotWebApp(-channel.id,
                        resultWebView.bot_id, resultWebView.query_id);
                });
                hasJoinMessage = true; // do not generate join message
            }

            if (!hasJoinMessage) {
                MessagesController.getInstance(currentAccount).generateJoinMessage(channel.id, true);
            }
            AndroidUtilities.runOnUIThread(() -> cell.setState(2, false));
            AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).loadFullChat(channel.id, 0, true), 1000);
            MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(-channel.id, channel.id, new ArrayList<>(), null);
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
        if (sheet == null) {
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeViewImmediate(windowView);
                }
                windowView = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
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

    public static final class WebPageUtils {

        private WebPageUtils() {
        }

        public static TLRPC.Photo getPhotoWithId(TLObject page, long id) {
            if (page instanceof TL_iv.RichMessage) return getPhotoWithId((TL_iv.RichMessage) page, id);
            if (page instanceof TL_iv.Page) return getPhotoWithId((TL_iv.Page) page, id);
            if (page instanceof TLRPC.WebPage) return getPhotoWithId((TLRPC.WebPage) page, id);
            return null;
        }

        public static TLRPC.Photo getPhotoWithId(TL_iv.RichMessage page, long id) {
            if (page == null) return null;
            for (int a = 0; a < page.photos.size(); a++) {
                TLRPC.Photo photo = page.photos.get(a);
                if (photo.id == id)
                    return photo;
            }
            return null;
        }

        public static TLRPC.Photo getPhotoWithId(TL_iv.Page page, long id) {
            if (page == null) return null;
            for (int a = 0; a < page.photos.size(); a++) {
                TLRPC.Photo photo = page.photos.get(a);
                if (photo.id == id)
                    return photo;
            }
            return null;
        }

        public static TLRPC.Photo getPhotoWithId(TLRPC.WebPage page, long id) {
            if (page == null || page.cached_page == null)
                return null;
            if (page.photo != null && page.photo.id == id)
                return page.photo;
            for (int a = 0; a < page.cached_page.photos.size(); a++) {
                TLRPC.Photo photo = page.cached_page.photos.get(a);
                if (photo.id == id)
                    return photo;
            }
            return null;
        }

        public static TLRPC.Document getDocumentWithId(TLObject page, long id) {
            if (page instanceof TL_iv.RichMessage) return getDocumentWithId((TL_iv.RichMessage) page, id);
            if (page instanceof TLRPC.WebPage) return getDocumentWithId((TLRPC.WebPage) page, id);
            return null;
        }

        public static TLRPC.Document getDocumentWithId(TL_iv.RichMessage richMessage, long id) {
            if (richMessage == null) return null;
            for (int a = 0; a < richMessage.documents.size(); a++) {
                TLRPC.Document document = richMessage.documents.get(a);
                if (document.id == id) {
                    return document;
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

        public static boolean isVideo(TLObject page, long id) {
            if (page instanceof TL_iv.RichMessage) return isVideo((TL_iv.RichMessage) page, id);
            if (page instanceof TLRPC.WebPage) return isVideo((TLRPC.WebPage) page, id);
            return false;
        }

        public static boolean isVideo(TL_iv.RichMessage richMessage, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(richMessage, ((TL_iv.pageBlockVideo) block).video_id);
                if (BuildVars.LOGS_ENABLED) {
                    StringBuilder attrs = new StringBuilder();
                    if (document != null) {
                        for (TLRPC.DocumentAttribute a : document.attributes) attrs.append(a.getClass().getSimpleName()).append(",");
                    }
                    FileLog.d("[richmedia] WebPageUtils.isVideo video_id=" + ((TL_iv.pageBlockVideo) block).video_id
                        + (document == null
                            ? " doc=NOT_FOUND documents.size=" + richMessage.documents.size()
                            : " doc=" + document.id + " mime=" + document.mime_type + " attrs=[" + attrs + "] isVideoDocument=" + MessageObject.isVideoDocument(document) + " isGifDocument=" + MessageObject.isGifDocument(document)));
                }
                if (document != null) {
                    return MessageObject.isVideoDocument(document);
                }
            }
            return false;
        }

        public static boolean isVideo(TLRPC.WebPage page, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(page, ((TL_iv.pageBlockVideo) block).video_id);
                if (document != null) {
                    return MessageObject.isVideoDocument(document);
                }
            }
            return false;
        }

        public static TLObject getMedia(TLObject page, TL_iv.PageBlock block) {
            if (page instanceof TL_iv.RichMessage) return getMedia((TL_iv.RichMessage) page, block);
            if (page instanceof TLRPC.WebPage) return getMedia((TLRPC.WebPage) page, block);
            return null;
        }

        public static TLObject getMedia(TL_iv.RichMessage page, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockPhoto) {
                return getPhotoWithId(page, ((TL_iv.pageBlockPhoto) block).photo_id);
            } else if (block instanceof TL_iv.pageBlockVideo) {
                return getDocumentWithId(page, ((TL_iv.pageBlockVideo) block).video_id);
            } else {
                return null;
            }
        }

        public static TLObject getMedia(TLRPC.WebPage page, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockPhoto) {
                return getPhotoWithId(page, ((TL_iv.pageBlockPhoto) block).photo_id);
            } else if (block instanceof TL_iv.pageBlockVideo) {
                return getDocumentWithId(page, ((TL_iv.pageBlockVideo) block).video_id);
            } else {
                return null;
            }
        }

        public static File getMediaFile(TLObject page, TL_iv.PageBlock block) {
            if (page instanceof TL_iv.RichMessage) return getMediaFile((TL_iv.RichMessage) page, block);
            if (page instanceof TLRPC.WebPage) return getMediaFile((TLRPC.WebPage) page, block);
            return null;
        }

        public static File getMediaFile(TLRPC.WebPage page, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockPhoto) {
                TLRPC.Photo photo = getPhotoWithId(page, ((TL_iv.pageBlockPhoto) block).photo_id);
                if (photo != null) {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getExistingPathToAttach(sizeFull);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(page, ((TL_iv.pageBlockVideo) block).video_id);
                if (document != null) {
                    return getExistingPathToAttach(document);
                }
            }
            return null;
        }

        private static File getExistingPathToAttach(TLObject attach) {
            final FileLoader fileLoader = FileLoader.getInstance(UserConfig.selectedAccount);
            final File nonCache = fileLoader.getPathToAttach(attach, false);
            if (nonCache != null && nonCache.exists()) {
                return nonCache;
            }
            final File cache = fileLoader.getPathToAttach(attach, true);
            if (cache != null && cache.exists()) {
                return cache;
            }
            return nonCache != null ? nonCache : cache;
        }

        public static File getMediaFile(TL_iv.RichMessage page, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockPhoto) {
                TLRPC.Photo photo = getPhotoWithId(page, ((TL_iv.pageBlockPhoto) block).photo_id);
                if (photo != null) {
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    if (sizeFull != null) {
                        return getExistingPathToAttach(sizeFull);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockVideo) {
                TLRPC.Document document = getDocumentWithId(page, ((TL_iv.pageBlockVideo) block).video_id);
                if (document != null) {
                    return getExistingPathToAttach(document);
                }
            }
            return null;
        }
    }

    @Override
    public WebpageAdapter getAdapter() {
        return pages[0].adapter;
    }

    public class WebpageAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<TL_iv.PageBlock> localBlocks = new ArrayList<>();
        private ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
        private ArrayList<TL_iv.PageBlock> photoBlocks = new ArrayList<>();
        private HashMap<String, Integer> anchors = new HashMap<>();
        private HashMap<String, Integer> anchorsOffset = new HashMap<>();
        private HashMap<String, TL_iv.textAnchor> anchorsParent = new HashMap<>();
        private HashMap<TL_iv.pageBlockAudio, MessageObject> audioBlocks = new HashMap<>();
        private ArrayList<MessageObject> audioMessages = new ArrayList<>();
        private HashMap<Object, TL_iv.PageBlock> textToBlocks = new HashMap<>();
        private ArrayList<Object> textBlocks = new ArrayList<>();
        private HashMap<String, Integer> searchTextOffset = new HashMap<>();

        private TLRPC.WebPage currentPage;
        private TL_iv.pageBlockChannel channelBlock;
        private boolean isRtl;
        private final boolean padding;

        public WebpageAdapter(Context ctx, boolean withPadding) {
            context = ctx;
            padding = withPadding;
        }

        private TLRPC.Photo getPhotoWithId(long id) {
            return WebPageUtils.getPhotoWithId(currentPage, id);
        }

        private TLRPC.Document getDocumentWithId(long id) {
            return WebPageUtils.getDocumentWithId(currentPage, id);
        }

        private void setRichTextParents(TL_iv.RichText parentRichText, TL_iv.RichText richText) {
            if (richText == null) {
                return;
            }
            richText.parentRichText = parentRichText;
            if (richText instanceof TL_iv.textFixed) {
                setRichTextParents(richText, ((TL_iv.textFixed) richText).text);
            } else if (richText instanceof TL_iv.textItalic) {
                setRichTextParents(richText, ((TL_iv.textItalic) richText).text);
            } else if (richText instanceof TL_iv.textBold) {
                setRichTextParents(richText, ((TL_iv.textBold) richText).text);
            } else if (richText instanceof TL_iv.textUnderline) {
                setRichTextParents(richText, ((TL_iv.textUnderline) richText).text);
            } else if (richText instanceof TL_iv.textStrike) {
                setRichTextParents(richText, ((TL_iv.textStrike) richText).text);
            } else if (richText instanceof TL_iv.textEmail) {
                setRichTextParents(richText, ((TL_iv.textEmail) richText).text);
            } else if (richText instanceof TL_iv.textPhone) {
                setRichTextParents(richText, ((TL_iv.textPhone) richText).text);
            } else if (richText instanceof TL_iv.textUrl) {
                setRichTextParents(richText, ((TL_iv.textUrl) richText).text);
            } else if (richText instanceof TL_iv.textConcat) {
                int count = richText.texts.size();
                for (int a = 0; a < count; a++) {
                    setRichTextParents(richText, richText.texts.get(a));
                }
            } else if (richText instanceof TL_iv.textSubscript) {
                setRichTextParents(richText, ((TL_iv.textSubscript) richText).text);
            } else if (richText instanceof TL_iv.textSuperscript) {
                setRichTextParents(richText, ((TL_iv.textSuperscript) richText).text);
            } else if (richText instanceof TL_iv.textMarked) {
                setRichTextParents(richText, ((TL_iv.textMarked) richText).text);
            } else if (richText instanceof TL_iv.textSpoiler) {
                setRichTextParents(richText, ((TL_iv.textSpoiler) richText).text);
            } else if (richText instanceof TL_iv.textAnchor) {
                TL_iv.textAnchor textAnchor = (TL_iv.textAnchor) richText;
                setRichTextParents(richText, textAnchor.text);
                String name = textAnchor.name.toLowerCase();
                anchors.put(name, blocks.size());
                if (textAnchor.text instanceof TL_iv.textPlain) {
                    TL_iv.textPlain textPlain = (TL_iv.textPlain) textAnchor.text;
                    if (!TextUtils.isEmpty(textPlain.text)) {
                        anchorsParent.put(name, textAnchor);
                    }
                } else if (!(textAnchor.text instanceof TL_iv.textEmpty)) {
                    anchorsParent.put(name, textAnchor);
                }
                anchorsOffset.put(name, -1);
            }
        }

        private void addTextBlock(Object text, TL_iv.PageBlock block) {
            if (text instanceof TL_iv.textEmpty || textToBlocks.containsKey(text)) {
                return;
            }
            textToBlocks.put(text, block);
            textBlocks.add(text);
        }

        private void setRichTextParents(TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockEmbedPost) {
                TL_iv.pageBlockEmbedPost blockEmbedPost = (TL_iv.pageBlockEmbedPost) block;
                setRichTextParents(null, blockEmbedPost.caption.text);
                setRichTextParents(null, blockEmbedPost.caption.credit);
                addTextBlock(blockEmbedPost.caption.text, blockEmbedPost);
                addTextBlock(blockEmbedPost.caption.credit, blockEmbedPost);
            } else if (block instanceof TL_iv.pageBlockParagraph) {
                TL_iv.pageBlockParagraph pageBlockParagraph = (TL_iv.pageBlockParagraph) block;
                setRichTextParents(null, pageBlockParagraph.text);
                addTextBlock(pageBlockParagraph.text, pageBlockParagraph);
            } else if (block instanceof TL_iv.pageBlockKicker) {
                TL_iv.pageBlockKicker pageBlockKicker = (TL_iv.pageBlockKicker) block;
                setRichTextParents(null, pageBlockKicker.text);
                addTextBlock(pageBlockKicker.text, pageBlockKicker);
            } else if (block instanceof TL_iv.pageBlockFooter) {
                TL_iv.pageBlockFooter pageBlockFooter = (TL_iv.pageBlockFooter) block;
                setRichTextParents(null, pageBlockFooter.text);
                addTextBlock(pageBlockFooter.text, pageBlockFooter);
            } else if (block instanceof TL_iv.pageBlockHeader) {
                TL_iv.pageBlockHeader pageBlockHeader = (TL_iv.pageBlockHeader) block;
                setRichTextParents(null, pageBlockHeader.text);
                addTextBlock(pageBlockHeader.text, pageBlockHeader);
            } else if (isHeadingBlock(block)) {
                final TL_iv.RichText text = block.text;
                setRichTextParents(null, text);
                addTextBlock(text, block);
            } else if (block instanceof TL_iv.pageBlockPreformatted) {
                TL_iv.pageBlockPreformatted pageBlockPreformatted = (TL_iv.pageBlockPreformatted) block;
                setRichTextParents(null, pageBlockPreformatted.text);
                addTextBlock(pageBlockPreformatted.text, pageBlockPreformatted);
            } else if (block instanceof TL_iv.pageBlockSubheader) {
                TL_iv.pageBlockSubheader pageBlockTitle = (TL_iv.pageBlockSubheader) block;
                setRichTextParents(null, pageBlockTitle.text);
                addTextBlock(pageBlockTitle.text, pageBlockTitle);
            } else if (block instanceof TL_iv.pageBlockSlideshow) {
                TL_iv.pageBlockSlideshow pageBlockSlideshow = (TL_iv.pageBlockSlideshow) block;
                setRichTextParents(null, pageBlockSlideshow.caption.text);
                setRichTextParents(null, pageBlockSlideshow.caption.credit);
                addTextBlock(pageBlockSlideshow.caption.text, pageBlockSlideshow);
                addTextBlock(pageBlockSlideshow.caption.credit, pageBlockSlideshow);
                for (int a = 0, size = pageBlockSlideshow.items.size(); a < size; a++) {
                    setRichTextParents(pageBlockSlideshow.items.get(a));
                }
            } else if (block instanceof TL_iv.pageBlockPhoto) {
                TL_iv.pageBlockPhoto pageBlockPhoto = (TL_iv.pageBlockPhoto) block;
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
            } else if (block instanceof TL_iv.pageBlockCollage) {
                TL_iv.pageBlockCollage pageBlockCollage = (TL_iv.pageBlockCollage) block;
                setRichTextParents(null, pageBlockCollage.caption.text);
                setRichTextParents(null, pageBlockCollage.caption.credit);
                addTextBlock(pageBlockCollage.caption.text, pageBlockCollage);
                addTextBlock(pageBlockCollage.caption.credit, pageBlockCollage);
                for (int a = 0, size = pageBlockCollage.items.size(); a < size; a++) {
                    setRichTextParents(pageBlockCollage.items.get(a));
                }
            } else if (block instanceof TL_iv.pageBlockEmbed) {
                TL_iv.pageBlockEmbed pageBlockEmbed = (TL_iv.pageBlockEmbed) block;
                setRichTextParents(null, pageBlockEmbed.caption.text);
                setRichTextParents(null, pageBlockEmbed.caption.credit);
                addTextBlock(pageBlockEmbed.caption.text, pageBlockEmbed);
                addTextBlock(pageBlockEmbed.caption.credit, pageBlockEmbed);
            } else if (block instanceof TL_iv.pageBlockSubtitle) {
                TL_iv.pageBlockSubtitle pageBlockSubtitle = (TL_iv.pageBlockSubtitle) block;
                setRichTextParents(null, pageBlockSubtitle.text);
                addTextBlock(pageBlockSubtitle.text, pageBlockSubtitle);
            } else if (block instanceof TL_iv.pageBlockBlockquote) {
                TL_iv.pageBlockBlockquote pageBlockBlockquote = (TL_iv.pageBlockBlockquote) block;
                setRichTextParents(null, pageBlockBlockquote.text);
                setRichTextParents(null, pageBlockBlockquote.caption);
                addTextBlock(pageBlockBlockquote.text, pageBlockBlockquote);
                addTextBlock(pageBlockBlockquote.caption, pageBlockBlockquote);
            } else if (block instanceof TL_iv.pageBlockDetails) {
                TL_iv.pageBlockDetails pageBlockDetails = (TL_iv.pageBlockDetails) block;
                setRichTextParents(null, pageBlockDetails.title);
                addTextBlock(pageBlockDetails.title, pageBlockDetails);
                for (int a = 0, size = pageBlockDetails.blocks.size(); a < size; a++) {
                    setRichTextParents(pageBlockDetails.blocks.get(a));
                }
            } else if (block instanceof TL_iv.pageBlockVideo) {
                TL_iv.pageBlockVideo pageBlockVideo = (TL_iv.pageBlockVideo) block;
                setRichTextParents(null, pageBlockVideo.caption.text);
                setRichTextParents(null, pageBlockVideo.caption.credit);
                addTextBlock(pageBlockVideo.caption.text, pageBlockVideo);
                addTextBlock(pageBlockVideo.caption.credit, pageBlockVideo);
            } else if (block instanceof TL_iv.pageBlockPullquote) {
                TL_iv.pageBlockPullquote pageBlockPullquote = (TL_iv.pageBlockPullquote) block;
                setRichTextParents(null, pageBlockPullquote.text);
                setRichTextParents(null, pageBlockPullquote.caption);
                addTextBlock(pageBlockPullquote.text, pageBlockPullquote);
                addTextBlock(pageBlockPullquote.caption, pageBlockPullquote);
            } else if (block instanceof TL_iv.pageBlockAudio) {
                TL_iv.pageBlockAudio pageBlockAudio = (TL_iv.pageBlockAudio) block;
                setRichTextParents(null, pageBlockAudio.caption.text);
                setRichTextParents(null, pageBlockAudio.caption.credit);
                addTextBlock(pageBlockAudio.caption.text, pageBlockAudio);
                addTextBlock(pageBlockAudio.caption.credit, pageBlockAudio);
            } else if (block instanceof TL_iv.pageBlockTable) {
                TL_iv.pageBlockTable pageBlockTable = (TL_iv.pageBlockTable) block;
                setRichTextParents(null, pageBlockTable.title);
                addTextBlock(pageBlockTable.title, pageBlockTable);
                for (int a = 0, size = pageBlockTable.rows.size(); a < size; a++) {
                    TL_iv.pageTableRow row = pageBlockTable.rows.get(a);
                    for (int b = 0, size2 = row.cells.size(); b < size2; b++) {
                        TL_iv.pageTableCell cell = row.cells.get(b);
                        setRichTextParents(null, cell.text);
                        addTextBlock(cell.text, pageBlockTable);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockTitle) {
                TL_iv.pageBlockTitle pageBlockTitle = (TL_iv.pageBlockTitle) block;
                setRichTextParents(null, pageBlockTitle.text);
                addTextBlock(pageBlockTitle.text, pageBlockTitle);
            } else if (block instanceof TL_iv.pageBlockCover) {
                TL_iv.pageBlockCover pageBlockCover = (TL_iv.pageBlockCover) block;
                setRichTextParents(pageBlockCover.cover);
            } else if (block instanceof TL_iv.pageBlockAuthorDate) {
                TL_iv.pageBlockAuthorDate pageBlockAuthorDate = (TL_iv.pageBlockAuthorDate) block;
                setRichTextParents(null, pageBlockAuthorDate.author);
                addTextBlock(pageBlockAuthorDate.author, pageBlockAuthorDate);
            } else if (block instanceof TL_iv.pageBlockMap) {
                TL_iv.pageBlockMap pageBlockMap = (TL_iv.pageBlockMap) block;
                setRichTextParents(null, pageBlockMap.caption.text);
                setRichTextParents(null, pageBlockMap.caption.credit);
                addTextBlock(pageBlockMap.caption.text, pageBlockMap);
                addTextBlock(pageBlockMap.caption.credit, pageBlockMap);
            } else if (block instanceof TL_iv.pageBlockRelatedArticles) {
                TL_iv.pageBlockRelatedArticles pageBlockRelatedArticles = (TL_iv.pageBlockRelatedArticles) block;
                setRichTextParents(null, pageBlockRelatedArticles.title);
                addTextBlock(pageBlockRelatedArticles.title, pageBlockRelatedArticles);
            }
        }

        private void addBlock(WebpageAdapter adapter, TL_iv.PageBlock block, int level, int listLevel, int position) {
            TL_iv.PageBlock originalBlock = block;
            if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild blockDetailsChild = (TL_pageBlockDetailsChild) block;
                block = blockDetailsChild.block;
            }
            if (!(block instanceof TL_iv.pageBlockList || block instanceof TL_iv.pageBlockOrderedList)) {
                setRichTextParents(block);
                addAllMediaFromBlock(adapter, block);
            }
            block = getLastNonListPageBlock(block);
            if (block instanceof TL_iv.pageBlockUnsupported) {
                return;
            } else if (block instanceof TL_iv.pageBlockAnchor) {
                anchors.put(((TL_iv.pageBlockAnchor) block).name.toLowerCase(), blocks.size());
                return;
            }
            if (block instanceof TL_iv.pageBlockTitle || block instanceof TL_iv.pageBlockHeader || isHeadingBlock(block)) {
                final TL_iv.RichText text = block.text;
                final String anchor = getPlainText(text).toString().toLowerCase().replaceAll("[\\[\\]\\(\\)\\{\\}]", "").replaceAll(" ", "-");
                if (!anchors.containsKey(anchor)) {
                    anchors.put(anchor, blocks.size());
                }
            }

            if (!(block instanceof TL_iv.pageBlockList || block instanceof TL_iv.pageBlockOrderedList)) {
                blocks.add(originalBlock);
            }

            if (block instanceof TL_iv.pageBlockAudio) {
                TL_iv.pageBlockAudio blockAudio = (TL_iv.pageBlockAudio) block;
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

            } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
                TL_iv.pageBlockBlockquoteBlocks pageBlockBlockquoteBlocks = (TL_iv.pageBlockBlockquoteBlocks) block;
                if (!pageBlockBlockquoteBlocks.blocks.isEmpty()) {
                    final int parentLayer = Math.max(0, block.level);
                    final int parentQuoteLevels = block.quoteLevels;
                    // If nothing enclosing wrote block.bottom yet, this is the outermost quote — trim the
                    // last leaf so the line gets a rounded end.
                    final boolean parentBottom = (block.quoteLevels == 0 && block.level <= 0) || block.bottom;
                    block.level = -1; // skip drawing the wrapper itself
                    final int size = pageBlockBlockquoteBlocks.blocks.size();
                    for (int b = 0; b < size; b++) {
                        TL_iv.PageBlock innerBlock = pageBlockBlockquoteBlocks.blocks.get(b);
                        if (innerBlock instanceof TL_iv.pageBlockUnsupported) {
                            continue;
                        } else if (innerBlock instanceof TL_iv.pageBlockAnchor) {
                            TL_iv.pageBlockAnchor pageBlockAnchor = (TL_iv.pageBlockAnchor) innerBlock;
                            anchors.put(pageBlockAnchor.name.toLowerCase(), blocks.size());
                            continue;
                        }
                        innerBlock.level = parentLayer + 1;
                        innerBlock.quoteLevels = parentQuoteLevels | (1 << parentLayer);
                        innerBlock.bottom = (b == size - 1) && parentBottom;
                        addBlock(adapter, innerBlock, level, listLevel, position);
                    }
                }
            } else if (block instanceof TL_iv.pageBlockEmbedPost) {
                TL_iv.pageBlockEmbedPost pageBlockEmbedPost = (TL_iv.pageBlockEmbedPost) block;
                if (!pageBlockEmbedPost.blocks.isEmpty()) {
                    block.level = -1;
                    for (int b = 0; b < pageBlockEmbedPost.blocks.size(); b++) {
                        TL_iv.PageBlock innerBlock = pageBlockEmbedPost.blocks.get(b);
                        if (innerBlock instanceof TL_iv.pageBlockUnsupported) {
                            continue;
                        } else if (innerBlock instanceof TL_iv.pageBlockAnchor) {
                            TL_iv.pageBlockAnchor pageBlockAnchor = (TL_iv.pageBlockAnchor) innerBlock;
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
            } else if (block instanceof TL_iv.pageBlockRelatedArticles) {
                TL_iv.pageBlockRelatedArticles pageBlockRelatedArticles = (TL_iv.pageBlockRelatedArticles) block;

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
            } else if (block instanceof TL_iv.pageBlockDetails) {
                TL_iv.pageBlockDetails pageBlockDetails = (TL_iv.pageBlockDetails) block;
                for (int b = 0, size = pageBlockDetails.blocks.size(); b < size; b++) {
                    TL_pageBlockDetailsChild child = new TL_pageBlockDetailsChild();
                    child.parent = originalBlock;
                    child.block = pageBlockDetails.blocks.get(b);
                    addBlock(adapter, wrapInTableBlock(originalBlock, child), level + 1, listLevel, position);
                }
            } else if (block instanceof TL_iv.pageBlockList) {
                TL_iv.pageBlockList pageBlockList = (TL_iv.pageBlockList) block;

                TL_pageBlockListParent pageBlockListParent = new TL_pageBlockListParent();
                pageBlockListParent.pageBlockList = pageBlockList;
                pageBlockListParent.level = listLevel;

                for (int b = 0, size = pageBlockList.items.size(); b < size; b++) {
                    TL_iv.PageListItem item = pageBlockList.items.get(b);

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

                    if (item instanceof TL_iv.TL_pageListItemText) {
                        pageBlockListItem.textItem = ((TL_iv.TL_pageListItemText) item).text;
                        pageBlockListItem.isCheckbox = ((TL_iv.TL_pageListItemText) item).checkbox;
                        pageBlockListItem.checked = ((TL_iv.TL_pageListItemText) item).checked;
                    } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                        TL_iv.TL_pageListItemBlocks pageListItemBlocks = (TL_iv.TL_pageListItemBlocks) item;
                        pageBlockListItem.isCheckbox = pageListItemBlocks.checkbox;
                        pageBlockListItem.checked = pageListItemBlocks.checked;
                        if (!pageListItemBlocks.blocks.isEmpty()) {
                            pageBlockListItem.blockItem = pageListItemBlocks.blocks.get(0);
                        } else {
                            TL_iv.TL_pageListItemText text = new TL_iv.TL_pageListItemText();
                            TL_iv.textPlain textPlain = new TL_iv.textPlain();
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
                        TL_iv.PageBlock finalBlock;
                        if (b == 0) {
                            finalBlock = fixListBlock(originalBlock, pageBlockListItem);
                        } else {
                            finalBlock = pageBlockListItem;
                        }
                        addBlock(adapter, finalBlock, level, listLevel + 1, position);
                    }

                    if (item instanceof TL_iv.TL_pageListItemBlocks) {
                        TL_iv.TL_pageListItemBlocks pageListItemBlocks = (TL_iv.TL_pageListItemBlocks) item;
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
            } else if (block instanceof TL_iv.pageBlockOrderedList) {
                TL_iv.pageBlockOrderedList pageBlockOrderedList = (TL_iv.pageBlockOrderedList) block;

                TL_pageBlockOrderedListParent pageBlockOrderedListParent = new TL_pageBlockOrderedListParent();
                pageBlockOrderedListParent.pageBlockOrderedList = pageBlockOrderedList;
                pageBlockOrderedListParent.level = listLevel;

                for (int b = 0, size = pageBlockOrderedList.items.size(); b < size; b++) {
                    TL_iv.PageListOrderedItem item = pageBlockOrderedList.items.get(b);

                    TL_pageBlockOrderedListItem pageBlockOrderedListItem = new TL_pageBlockOrderedListItem();
                    pageBlockOrderedListItem.index = b;
                    pageBlockOrderedListItem.parent = pageBlockOrderedListParent;
                    pageBlockOrderedListParent.items.add(pageBlockOrderedListItem);

                    if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                        TL_iv.TL_pageListOrderedItemText pageListOrderedItemText = (TL_iv.TL_pageListOrderedItemText) item;
                        pageBlockOrderedListItem.textItem = pageListOrderedItemText.text;
                        pageBlockOrderedListItem.isCheckbox = pageListOrderedItemText.checkbox;
                        pageBlockOrderedListItem.checked = pageListOrderedItemText.checked;

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
                    } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                        TL_iv.TL_pageListOrderedItemBlocks pageListOrderedItemBlocks = (TL_iv.TL_pageListOrderedItemBlocks) item;
                        pageBlockOrderedListItem.isCheckbox = pageListOrderedItemBlocks.checkbox;
                        pageBlockOrderedListItem.checked = pageListOrderedItemBlocks.checked;
                        if (!pageListOrderedItemBlocks.blocks.isEmpty()) {
                            pageBlockOrderedListItem.blockItem = pageListOrderedItemBlocks.blocks.get(0);
                        } else {
                            TL_iv.TL_pageListOrderedItemText text = new TL_iv.TL_pageListOrderedItemText();
                            TL_iv.textPlain textPlain = new TL_iv.textPlain();
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
                        TL_iv.PageBlock finalBlock;
                        if (b == 0) {
                            finalBlock = fixListBlock(originalBlock, pageBlockOrderedListItem);
                        } else {
                            finalBlock = pageBlockOrderedListItem;
                        }
                        addBlock(adapter, finalBlock, level, listLevel + 1, position);
                    }

                    if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                        TL_iv.TL_pageListOrderedItemBlocks pageListOrderedItemBlocks = (TL_iv.TL_pageListOrderedItemBlocks) item;
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

        private void addAllMediaFromBlock(WebpageAdapter adapter, TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockPhoto) {
                TL_iv.pageBlockPhoto pageBlockPhoto = (TL_iv.pageBlockPhoto) block;
                TLRPC.Photo photo = getPhotoWithId(pageBlockPhoto.photo_id);
                if (photo != null) {
                    pageBlockPhoto.thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 56, true);
                    pageBlockPhoto.thumbObject = photo;
                    photoBlocks.add(block);
                }
            } else if (block instanceof TL_iv.pageBlockVideo && WebPageUtils.isVideo(adapter.currentPage, block)) {
                TL_iv.pageBlockVideo pageBlockVideo = (TL_iv.pageBlockVideo) block;
                TLRPC.Document document = getDocumentWithId(pageBlockVideo.video_id);
                if (document != null) {
                    pageBlockVideo.thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 56, true);
                    pageBlockVideo.thumbObject = document;
                    photoBlocks.add(block);
                }
            } else if (block instanceof TL_iv.pageBlockSlideshow) {
                TL_iv.pageBlockSlideshow slideshow = (TL_iv.pageBlockSlideshow) block;
                int count = slideshow.items.size();
                for (int a = 0; a < count; a++) {
                    TL_iv.PageBlock innerBlock = slideshow.items.get(a);
                    innerBlock.groupId = lastBlockNum;
                    addAllMediaFromBlock(adapter, innerBlock);
                }
                lastBlockNum++;
            } else if (block instanceof TL_iv.pageBlockCollage) {
                TL_iv.pageBlockCollage collage = (TL_iv.pageBlockCollage) block;
                int count = collage.items.size();
                for (int a = 0; a < count; a++) {
                    TL_iv.PageBlock innerBlock = collage.items.get(a);
                    innerBlock.groupId = lastBlockNum;
                    addAllMediaFromBlock(adapter, innerBlock);
                }
                lastBlockNum++;
            } else if (block instanceof TL_iv.pageBlockCover) {
                TL_iv.pageBlockCover pageBlockCover = (TL_iv.pageBlockCover) block;
                addAllMediaFromBlock(adapter, pageBlockCover.cover);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case (Integer.MAX_VALUE - 1): {
                    view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            final int height = AndroidUtilities.displaySize.y;
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) (height * .4f), MeasureSpec.EXACTLY));
                        }
                    };
                    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    break;
                }
                case 0: {
                    view = new BlockParagraphCell(context, ArticleViewer.this, this);
                    break;
                }
                case 1: {
                    view = new BlockHeaderCell(context, ArticleViewer.this, this);
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
                    view = new BlockSubtitleCell(context, ArticleViewer.this, this);
                    break;
                }
                case 5: {
                    view = new BlockVideoCell(context, ArticleViewer.this, this, 0);
                    break;
                }
                case 6: {
                    view = new BlockPullquoteCell(context, ArticleViewer.this, this);
                    break;
                }
                case 7: {
                    view = new BlockBlockquoteCell(context, ArticleViewer.this, this);
                    break;
                }
                case 8: {
                    view = new BlockSlideshowCell(context, this);
                    break;
                }
                case 9: {
                    view = new BlockPhotoCell(context, ArticleViewer.this, this, 0);
                    break;
                }
                case 10: {
                    view = new BlockAuthorDateCell(context, ArticleViewer.this, this);
                    break;
                }
                case 11: {
                    view = new BlockTitleCell(context, ArticleViewer.this, this);
                    break;
                }
                case 12: {
                    view = new BlockListItemCell(context, ArticleViewer.this, this);
                    break;
                }
                case 13: {
                    view = new BlockFooterCell(context, ArticleViewer.this, this);
                    break;
                }
                case 14: {
                    view = new BlockPreformattedCell(context, ArticleViewer.this, this);
                    break;
                }
                case 15: {
                    view = new BlockSubheaderCell(context, ArticleViewer.this, this);
                    break;
                }
                case 16: {
                    view = new BlockEmbedPostCell(context, ArticleViewer.this, this);
                    break;
                }
                case 17: {
                    view = new BlockCollageCell(context, this);
                    break;
                }
                case 18: {
                    view = new BlockChannelCell(context, ArticleViewer.this, this, 0);
                    break;
                }
                case 19: {
                    view = new BlockAudioCell(context, ArticleViewer.this, this);
                    break;
                }
                case 20: {
                    view = new BlockKickerCell(context, ArticleViewer.this, this);
                    break;
                }
                case 21: {
                    view = new BlockOrderedListItemCell(context, ArticleViewer.this, this);
                    break;
                }
                case 22: {
                    view = new BlockMapCell(context, ArticleViewer.this, this, 0);
                    break;
                }
                case 23: {
                    view = new BlockRelatedArticlesCell(context, ArticleViewer.this, this);
                    break;
                }
                case 24: {
                    view = new BlockDetailsCell(context, ArticleViewer.this, this);
                    break;
                }
                case 25: {
                    view = new BlockTableCell(context, ArticleViewer.this, this);
                    break;
                }
                case 26: {
                    view = new BlockRelatedArticlesHeaderCell(context, ArticleViewer.this, this);
                    break;
                }
                case 27: {
                    view = new BlockDetailsBottomCell(context);
                    break;
                }
                case 28: {
                    view = new BlockRelatedArticlesShadowCell(context, ArticleViewer.this);
                    break;
                }
                case 90: {
                    view = new ReportCell(context, false);
                    break;
                }
                case 91: {
                    view = new ReportCell(context, true);
                    break;
                }
                case 92: {
                    view = new BlockMathCell(context, ArticleViewer.this, this);
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
            if (padding) {
                position--;
            }
            if (position >= 0 && position < localBlocks.size()) {
                TL_iv.PageBlock block = localBlocks.get(position);
                bindBlockToHolder(holder.getItemViewType(), holder, block, position, localBlocks.size(), false);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 90 || holder.getItemViewType() == 91) {
                ReportCell cell = (ReportCell) holder.itemView;
                cell.setViews(currentPage.cached_page != null ? currentPage.cached_page.views : 0);
            }
        }

        private void bindBlockToHolder(int type, RecyclerView.ViewHolder holder, TL_iv.PageBlock block, int position, int total, boolean calcHeight) {
            TL_iv.PageBlock originalBlock = block;
            if (block instanceof TL_iv.pageBlockCover) {
                block = ((TL_iv.pageBlockCover) block).cover;
            } else if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                block = pageBlockDetailsChild.block;
            }
            switch (type) {
                case 0: {
                    BlockParagraphCell cell = (BlockParagraphCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockParagraph) block);
                    break;
                }
                case 1: {
                    BlockHeaderCell cell = (BlockHeaderCell) holder.itemView;
                    cell.setBlock(block);
                    break;
                }
                case 2: {
                    BlockDividerCell cell = (BlockDividerCell) holder.itemView;
                    break;
                }
                case 3: {
                    BlockEmbedCell cell = (BlockEmbedCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockEmbed) block);
                    break;
                }
                case 4: {
                    BlockSubtitleCell cell = (BlockSubtitleCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockSubtitle) block);
                    break;
                }
                case 5: {
                    BlockVideoCell cell = (BlockVideoCell) holder.itemView;
                    TL_iv.pageBlockVideo blockVideo = (TL_iv.pageBlockVideo) block;
                    cell.setBlock(blockVideo, videoStates.get(blockVideo.video_id), calcHeight, position == 0, position == total - 1);
                    cell.setParentBlock(channelBlock, originalBlock);
                    break;
                }
                case 6: {
                    BlockPullquoteCell cell = (BlockPullquoteCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockPullquote) block);
                    break;
                }
                case 7: {
                    BlockBlockquoteCell cell = (BlockBlockquoteCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockBlockquote) block);
                    break;
                }
                case 8: {
                    BlockSlideshowCell cell = (BlockSlideshowCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockSlideshow) block);
                    break;
                }
                case 9: {
                    BlockPhotoCell cell = (BlockPhotoCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockPhoto) block, currentPage, null, calcHeight, position == 0);
                    cell.setParentBlock(originalBlock);
                    break;
                }
                case 10: {
                    BlockAuthorDateCell cell = (BlockAuthorDateCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockAuthorDate) block);
                    break;
                }
                case 11: {
                    BlockTitleCell cell = (BlockTitleCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockTitle) block);
                    break;
                }
                case 12: {
                    BlockListItemCell cell = (BlockListItemCell) holder.itemView;
                    cell.setBlock((TL_pageBlockListItem) block);
                    break;
                }
                case 13: {
                    BlockFooterCell cell = (BlockFooterCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockFooter) block);
                    break;
                }
                case 14: {
                    BlockPreformattedCell cell = (BlockPreformattedCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockPreformatted) block);
                    break;
                }
                case 15: {
                    BlockSubheaderCell cell = (BlockSubheaderCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockSubheader) block);
                    break;
                }
                case 16: {
                    BlockEmbedPostCell cell = (BlockEmbedPostCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockEmbedPost) block);
                    break;
                }
                case 17: {
                    BlockCollageCell cell = (BlockCollageCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockCollage) block);
                    break;
                }
                case 18: {
                    BlockChannelCell cell = (BlockChannelCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockChannel) block);
                    break;
                }
                case 19: {
                    BlockAudioCell cell = (BlockAudioCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockAudio) block, position == 0, position == total - 1);
                    break;
                }
                case 20: {
                    BlockKickerCell cell = (BlockKickerCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockKicker) block);
                    break;
                }
                case 21: {
                    BlockOrderedListItemCell cell = (BlockOrderedListItemCell) holder.itemView;
                    cell.setBlock((TL_pageBlockOrderedListItem) block);
                    break;
                }
                case 22: {
                    BlockMapCell cell = (BlockMapCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockMap) block, position == 0, position == total - 1);
                    break;
                }
                case 23: {
                    BlockRelatedArticlesCell cell = (BlockRelatedArticlesCell) holder.itemView;
                    cell.setBlock((TL_pageBlockRelatedArticlesChild) block, currentPage);
                    break;
                }
                case 24: {
                    BlockDetailsCell cell = (BlockDetailsCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockDetails) block);
                    break;
                }
                case 25: {
                    BlockTableCell cell = (BlockTableCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockTable) block);
                    break;
                }
                case 26: {
                    BlockRelatedArticlesHeaderCell cell = (BlockRelatedArticlesHeaderCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockRelatedArticles) block);
                    break;
                }
                case 27: {
                    BlockDetailsBottomCell cell = (BlockDetailsBottomCell) holder.itemView;
                    break;
                }
                case 92: {
                    BlockMathCell cell = (BlockMathCell) holder.itemView;
                    cell.setBlock((TL_iv.pageBlockMath) block);
                    break;
                }
                case 100: {
                    TextView textView = (TextView) holder.itemView;
                    textView.setText("unsupported block " + block);
                    break;
                }
            }
        }

        private int getTypeForBlock(TL_iv.PageBlock block) {
            if (block instanceof TL_iv.pageBlockParagraph) {
                return 0;
            } else if (block instanceof TL_iv.pageBlockHeader || isHeadingBlock(block)) {
                return 1;
            } else if (block instanceof TL_iv.pageBlockDivider) {
                return 2;
            } else if (block instanceof TL_iv.pageBlockEmbed) {
                return 3;
            } else if (block instanceof TL_iv.pageBlockSubtitle) {
                return 4;
            } else if (block instanceof TL_iv.pageBlockVideo) {
                return 5;
            } else if (block instanceof TL_iv.pageBlockPullquote) {
                return 6;
            } else if (block instanceof TL_iv.pageBlockBlockquote) {
                return 7;
            } else if (block instanceof TL_iv.pageBlockSlideshow) {
                return 8;
            } else if (block instanceof TL_iv.pageBlockPhoto) {
                return 9;
            } else if (block instanceof TL_iv.pageBlockAuthorDate) {
                return 10;
            } else if (block instanceof TL_iv.pageBlockTitle) {
                return 11;
            } else if (block instanceof TL_pageBlockListItem) {
                return 12;
            } else if (block instanceof TL_iv.pageBlockFooter) {
                return 13;
            } else if (block instanceof TL_iv.pageBlockPreformatted) {
                return 14;
            } else if (block instanceof TL_iv.pageBlockSubheader) {
                return 15;
            } else if (block instanceof TL_iv.pageBlockEmbedPost) {
                return 16;
            } else if (block instanceof TL_iv.pageBlockCollage) {
                return 17;
            } else if (block instanceof TL_iv.pageBlockChannel) {
                return 18;
            } else if (block instanceof TL_iv.pageBlockAudio) {
                return 19;
            } else if (block instanceof TL_iv.pageBlockKicker) {
                return 20;
            } else if (block instanceof TL_pageBlockOrderedListItem) {
                return 21;
            } else if (block instanceof TL_iv.pageBlockMap) {
                return 22;
            } else if (block instanceof TL_pageBlockRelatedArticlesChild) {
                return 23;
            } else if (block instanceof TL_iv.pageBlockDetails) {
                return 24;
            } else if (block instanceof TL_iv.pageBlockTable) {
                return 25;
            } else if (block instanceof TL_iv.pageBlockRelatedArticles) {
                return 26;
            } else if (block instanceof TL_pageBlockDetailsBottom) {
                return 27;
            } else if (block instanceof TL_pageBlockRelatedArticlesShadow) {
                return 28;
            } else if (block instanceof TL_iv.pageBlockMath) {
                return 92;
            } else if (block instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                return getTypeForBlock(pageBlockDetailsChild.block);
            } else if (block instanceof TL_iv.pageBlockCover) {
                TL_iv.pageBlockCover pageBlockCover = (TL_iv.pageBlockCover) block;
                return getTypeForBlock(pageBlockCover.cover);
            } else {
                return 100;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (padding) {
                if (position == 0)
                    return Integer.MAX_VALUE - 1;
                position--;
            }
            if (position == localBlocks.size()) {
                return currentPage != null && currentPage.cached_page != null && currentPage.cached_page.web ? 91 : 90;
            }
            return getTypeForBlock(localBlocks.get(position));
        }

        public TL_iv.PageBlock getItem(int position) {
            return localBlocks.get(position);
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (currentPage != null && currentPage.cached_page != null) {
                count += localBlocks.size();
                if (currentPage.cached_page.local == null) {
                    count++;
                }
            }
            if (padding) {
                count++;
            }
            return count;
        }

        private boolean isBlockOpened(TL_pageBlockDetailsChild child) {
            TL_iv.PageBlock parentBlock = getLastNonListPageBlock(child.parent);
            if (parentBlock instanceof TL_iv.pageBlockDetails) {
                return ((TL_iv.pageBlockDetails) parentBlock).open;
            } else if (parentBlock instanceof TL_pageBlockDetailsChild) {
                TL_pageBlockDetailsChild parent = (TL_pageBlockDetailsChild) parentBlock;
                parentBlock = getLastNonListPageBlock(parent.block);
                if (parentBlock instanceof TL_iv.pageBlockDetails && !((TL_iv.pageBlockDetails) parentBlock).open) {
                    return false;
                }
                return isBlockOpened(parent);
            }
            return false;
        }

        public void resetCachedHeights() {
            for (int i = 0; i < localBlocks.size(); ++i) {
                TL_iv.PageBlock pageBlock = localBlocks.get(i);
                if (pageBlock != null) {
                    pageBlock.cachedWidth = 0;
                    pageBlock.cachedHeight = 0;
                }
            }
            calculateContentHeight();
        }

        private void updateRows() {
            localBlocks.clear();
            for (int a = 0, size = blocks.size(); a < size; a++) {
                TL_iv.PageBlock originalBlock = blocks.get(a);
                TL_iv.PageBlock block = getLastNonListPageBlock(originalBlock);
                if (block instanceof TL_pageBlockDetailsChild) {
                    TL_pageBlockDetailsChild pageBlockDetailsChild = (TL_pageBlockDetailsChild) block;
                    if (!isBlockOpened(pageBlockDetailsChild)) {
                        continue;
                    }
                }
                localBlocks.add(originalBlock);
            }

            if (localBlocks.size() < 100)
                calculateContentHeight();
            else itemHeights = null;
        }

        public int[] itemHeights;
        public int[] sumItemHeights;
        public int fullHeight;

        public void calculateContentHeight() {
            Utilities.globalQueue.cancelRunnable(calculateContentHeightRunnable);
            Utilities.globalQueue.postRunnable(calculateContentHeightRunnable, 100);
        }

        private final Runnable calculateContentHeightRunnable = () -> {
            final ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>(localBlocks);
            final int itemCount = blocks.size() + (sheet != null && sheet.halfSize() ? 1 : 0);
            int fullHeight = 0;
            final int[] itemHeights = new int[itemCount];
            final int[] sumItemHeights = new int[itemCount];
            if (pages[0] == null) return;
            final RecyclerView listView = pages[0].listView;
            if (listView == null) return;
            int widthSpec = View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.AT_MOST);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST);
            for (int i = 0; i < itemCount; i++) {
                if (ArticleViewer.WebpageAdapter.this.padding && i == 0) {
                    itemHeights[0] = 0;
                } else {
                    final int position = ArticleViewer.WebpageAdapter.this.padding ? i - 1 : i;
                    TL_iv.PageBlock page = position < 0 || position >= blocks.size() ? null : blocks.get(position);
                    if (page != null && page.cachedHeight != 0 && page.cachedWidth == View.MeasureSpec.getSize(widthSpec)) {
                        itemHeights[i] = page.cachedHeight;
                    } else {
                        RecyclerView.ViewHolder viewHolder = createViewHolder(listView, getTypeForBlock(page));
                        bindBlockToHolder(viewHolder.getItemViewType(), viewHolder, page, position, blocks.size(), true);
                        viewHolder.itemView.measure(widthSpec, heightSpec);
                        itemHeights[i] = viewHolder.itemView.getMeasuredHeight();
                        if (page != null) {
                            page.cachedHeight = itemHeights[i];
                            page.cachedWidth = View.MeasureSpec.getSize(widthSpec);
                        }
                    }
                }
                sumItemHeights[i] = (i - 1 < 0 ? 0 : sumItemHeights[i - 1]) + itemHeights[i];
                fullHeight += itemHeights[i];
            }
            final int finalFullHeight = fullHeight;
            AndroidUtilities.runOnUIThread(() -> {
                this.fullHeight = finalFullHeight;
                this.itemHeights = itemHeights;
                this.sumItemHeights = sumItemHeights;
                updatePages();
            });
        };

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

    public static class BlockVideoCellState {
        long playFrom;
        Bitmap lastFrameBitmap;

        public static BlockVideoCellState fromPlayer(VideoPlayerHolderBase player, BlockVideoCell videoCell) {
            BlockVideoCellState state = new BlockVideoCellState();
            state.playFrom = player.getCurrentPosition();
            if (player.firstFrameRendered && videoCell.textureView != null && videoCell.textureView.getSurfaceTexture() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Surface surface = new Surface(videoCell.textureView.getSurfaceTexture());
                    Bitmap bitmap = Bitmap.createBitmap(videoCell.textureView.getMeasuredWidth(), videoCell.textureView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    AndroidUtilities.getBitmapFromSurface(surface, bitmap);
                    surface.release();
                    state.lastFrameBitmap = bitmap;
                } else {
                    state.lastFrameBitmap = videoCell.textureView.getBitmap();
                }
            }
            return state;
        }

        public static BlockVideoCellState fromPlayer(VideoPlayer player, BlockVideoCell videoCell, TextureView textureView) {
            BlockVideoCellState state = new BlockVideoCellState();
            state.playFrom = player.getCurrentPosition();
            if (textureView != null && textureView.getSurfaceTexture() != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Surface surface = new Surface(textureView.getSurfaceTexture());
                    Bitmap bitmap = Bitmap.createBitmap(textureView.getMeasuredWidth(), textureView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    AndroidUtilities.getBitmapFromSurface(surface, bitmap);
                    surface.release();
                    state.lastFrameBitmap = bitmap;
                } else {
                    state.lastFrameBitmap = textureView.getBitmap();
                }
            }
            return state;
        }

        public static BlockVideoCellState fromPlayer(VideoPlayer player, BlockVideoCell videoCell, SurfaceView surfaceView) {
            BlockVideoCellState state = new BlockVideoCellState();
            state.playFrom = player.getCurrentPosition();
            if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Bitmap bitmap = Bitmap.createBitmap(surfaceView.getMeasuredWidth(), surfaceView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                AndroidUtilities.getBitmapFromSurface(surfaceView, bitmap);
                state.lastFrameBitmap = bitmap;
            }
            return state;
        }
    }

    public static interface IBlock {
        // returns -1 if not sure (then takes full 0 as left and getWidth() as right)
        public int getBoundLeft();
        public int getBoundRight();
        public int getLastLineBoundRight();

        public default int getMinWidth() {
            final int l = getBoundLeft();
            final int r = getBoundRight();
            if (l < 0 || r < 0 || r < l) return -1;
            return r - l;
        }
    }

    public static class BlockVideoCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private ImageReceiver imageView;
        FrameLayout aspectRationContainer;
        private AspectRatioFrameLayout aspectRatioFrameLayout;
        private TextureView textureView;
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

        private TL_iv.pageBlockVideo currentBlock;
        private BlockVideoCellState videoState;
        private TL_iv.PageBlock parentBlock;
        private TLRPC.Document currentDocument;
        private boolean isGif;
        private boolean calcHeight;

        private boolean autoDownload;

        private boolean cancelLoading;

        private MessageObject.GroupedMessagePosition groupPosition;
        private boolean firstFrameRendered;

        public BlockVideoCell(Context context, IArticleViewer parent, WebpageAdapter adapter, int type) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            imageView.setNeedsQualityThumb(true);
            imageView.setShouldGenerateQualityThumb(true);
            currentType = type;
            radialProgress = new RadialProgress2(this);
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
            TAG = DownloadController.getInstance(parent.getCurrentAccount()).generateObserverTag();
            channelCell = new BlockChannelCell(context, parent, adapter, 1);

            aspectRatioFrameLayout = new AspectRatioFrameLayout(context);
            aspectRatioFrameLayout.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            textureView = new TextureView(context);
            textureView.setOpaque(false);
            aspectRationContainer = new FrameLayout(getContext());
            aspectRatioFrameLayout.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            aspectRationContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            addView(aspectRationContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            // TODO: pass pinchToZoomHelper in IArticleViewer
//            if (child == aspectRationContainer && pinchToZoomHelper.isInOverlayModeFor(this)) {
//                return true;
//            }
            return super.drawChild(canvas, child, drawingTime);
        }

        public void setBlock(TL_iv.pageBlockVideo block, BlockVideoCellState state, boolean calcHeight, boolean first, boolean last) {
            setBlock(block, null, state, calcHeight, first, last);
        }

        public void setBlock(TL_iv.pageBlockVideo block, TLObject page, BlockVideoCellState state, boolean calcHeight, boolean first, boolean last) {
            if (currentBlock != null && parent.videoPlayer != null && parent.currentPlayer == this) {
                parent.videoStates.put(currentBlock.video_id, videoState = BlockVideoCellState.fromPlayer(parent.videoPlayer, this));
            }
            currentBlock = block;
            videoState = state;
            parentBlock = null;
            this.calcHeight = calcHeight;
            if (adapter != null) {
                currentDocument = adapter.getDocumentWithId(currentBlock.video_id);
            } else if (page != null) {
                currentDocument = WebPageUtils.getDocumentWithId(page, currentBlock.video_id);
            } else {
                currentDocument = null;
            }
            isGif = MessageObject.isVideoDocument(currentDocument) || MessageObject.isGifDocument(currentDocument)/* && currentBlock.autoplay*/;
            isFirst = first;
            channelCell.setVisibility(INVISIBLE);
            updateButtonState(false);

            requestLayout();
        }

        public void setParentBlock(TL_iv.pageBlockChannel channelBlock, TL_iv.PageBlock block) {
            parentBlock = block;
            if (channelBlock != null && parentBlock instanceof TL_iv.pageBlockCover) {
                channelCell.setBlock(channelBlock);
                channelCell.setVisibility(VISIBLE);
            }
        }

        public View getChannelCell() {
            return channelCell;
        }

        public ImageReceiver getImageView() {
            return imageView;
        }

        public TL_iv.pageBlockVideo getCurrentBlock() {
            return currentBlock;
        }

        public TextureView getTextureView() {
            return textureView;
        }

        public boolean isFirstFrameRendered() {
            return firstFrameRendered;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // TODO: pass pinchToZoomHelper in IArticleViewer
//            if (pinchToZoomHelper.checkPinchToZoom(event, this, imageView, aspectRationContainer, textureView, null)) {
//                return true;
//            }
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + dp(39)) {
                if (adapter != null && adapter.channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    // TODO
                    // MessagesController.getInstance(parent.getCurrentAccount()).openByUserName(ChatObject.getPublicUsername(adapter.channelBlock.channel), parentFragment, 2);
                    // close(false, true);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48) || buttonState == 0) {
                    buttonPressed = 1;
                    invalidate();
                } else {
                    photoPressed = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (photoPressed) {
                    photoPressed = false;
                    parent.openPhoto(currentBlock, adapter);
                } else if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(true);
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parent, adapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
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

            final int currentAccount = parent.getCurrentAccount();
            if (currentBlock != null) {
                int photoWidth = width;
                int photoHeight = height;
                int photoX;
                int textWidth;
                if (currentType == 0 && currentBlock.level > 0) {
                    textX = photoX = dp(14 * currentBlock.level) + dp(18);
                    photoWidth -= photoX + dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = dp(18);
                    textWidth = width - dp(36);
                }
                if (currentDocument != null) {
                    int size = dp(48);
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
                        if (parentBlock instanceof TL_iv.pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / h;
                                photoWidth = (int) (scale * w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                        if (height == 0) {
                            height = dp(100);
                        } else if (height < size) {
                            height = size;
                        }
                        photoHeight = height;
                    } else if (currentType == 2) {
                        if ((groupPosition.flags & POSITION_FLAG_RIGHT) == 0) {
                            photoWidth -= dp(2);
                        }
                        if ((groupPosition.flags & POSITION_FLAG_BOTTOM) == 0) {
                            photoHeight -= dp(2);
                        }
                    }
                    imageView.setQualityThumbDocument(currentDocument);
                    int photoY = (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : dp(8);
                    imageView.setImageCoords(photoX, photoY, photoWidth, photoHeight);
                    if (calcHeight) {

                    } else if (isGif) {
                        if (videoState != null && videoState.lastFrameBitmap != null) {
                            imageView.setStrippedLocation(null);
                            imageView.setImageBitmap(videoState.lastFrameBitmap);
                        } else {
                            autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, currentDocument.size);
                            File path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument);
                            File path2 = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true);
                            if (autoDownload || path.exists() || path2.exists()) {
                                imageView.setStrippedLocation(null);
                                imageView.setImage(null, null, ImageLocation.getForDocument(currentDocument), "200_200_pframe", ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument.size, null, adapter.currentPage, 1);
                            } else {
                                imageView.setStrippedLocation(ImageLocation.getForDocument(currentDocument));
                                imageView.setImage(null, null, null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument.size, null, adapter.currentPage, 1);
                            }
                        }
                    } else {
                        imageView.setStrippedLocation(null);
                        imageView.setImage(null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", 0, null, adapter != null ? adapter.currentPage : null, 1);
                    }
                    imageView.setAspectFit(true);
                    buttonX = (int) (imageView.getImageX() + (imageView.getImageWidth() - size) / 2.0f);
                    buttonY = (int) (imageView.getImageY() + (imageView.getImageHeight() - size) / 2.0f);
                    radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + dp(8));
                if (currentType == 0) {
                    captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                    if (captionLayout != null) {
                        creditOffset = dp(4) + captionLayout.getHeight();
                        height += creditOffset + dp(4);
                        captionLayout.x = textX;
                        captionLayout.y = textY;
                    }
                    creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                    if (creditLayout != null) {
                        height += dp(4) + creditLayout.getHeight();
                        creditLayout.x = textX;
                        creditLayout.y = textY + creditOffset;
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += dp(8);
                }
                boolean nextIsChannel = parentBlock instanceof TL_iv.pageBlockCover && adapter != null && adapter.blocks.size() > 1 && adapter.blocks.get(1) instanceof TL_iv.pageBlockChannel;
                if (currentType != 2 && !nextIsChannel) {
                    height += dp(8);
                }
            } else {
                height = 1;
            }
            channelCell.measure(widthMeasureSpec, heightMeasureSpec);
            channelCell.setTranslationY(imageView.getImageHeight() - dp(39));
            FrameLayout.LayoutParams params = (LayoutParams) aspectRationContainer.getLayoutParams();
            params.leftMargin = (int) imageView.getImageX();
            params.topMargin = (int) imageView.getImageY();
            params.width = (int) imageView.getImageWidth();
            params.height = (int) imageView.getImageHeight();
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (!imageView.hasBitmapImage() || imageView.getCurrentAlpha() != 1.0f) {
                canvas.drawRect(imageView.getDrawRegion(), photoBackgroundPaint);
            }
            // TODO
//            if (!pinchToZoomHelper.isInOverlayModeFor(this)) {
                imageView.draw(canvas);
//            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this, count++);
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(parent, canvas, this, count);
                creditLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
            super.onDraw(canvas);

            // TODO
//            if (!pinchToZoomHelper.isInOverlayModeFor(this)) {
                if (imageView.getVisible()) {
                    radialProgress.draw(canvas);
                }
//            }
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
            final int currentAccount = parent.getCurrentAccount();
            final String fileName = FileLoader.getAttachFileName(currentDocument);
            final File path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument);
            final File path2 = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true);
            boolean fileExists = path.exists() || path2.exists();
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
                if (videoState != null && videoState.lastFrameBitmap != null) {
                    buttonState = -1;
                } else if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
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
            final int currentAccount = parent.getCurrentAccount();
            if (buttonState == 0) {
                cancelLoading = false;
                radialProgress.setProgress(0, false);
                if (isGif) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(currentDocument.thumbs, 40);
                    imageView.setImage(ImageLocation.getForDocument(currentDocument), null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", currentDocument.size, null, adapter.currentPage, 1);
                } else {
                    FileLoader.getInstance(currentAccount).loadFile(currentDocument, adapter.currentPage, FileLoader.PRIORITY_NORMAL, 1);
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
                // TODO
//                openPhoto(currentBlock, adapter);
            }
        }

        private boolean attached;

        private void updateAttachedState() {
            if (getVisibility() == View.VISIBLE && isAttachedToWindow()) {
                attach();
            } else {
                detach();
            }
        }

        private void attach() {
            if (attached) return;
            attached = true;

            imageView.onAttachedToWindow();
            updateButtonState(false);
        }

        private void detach() {
            if (!attached) return;
            attached = false;
            if (currentBlock != null && parent.videoPlayer != null && parent.currentPlayer == this) {
                parent.videoStates.put(currentBlock.video_id, setState(BlockVideoCellState.fromPlayer(parent.videoPlayer, this)));
            }
            imageView.onDetachedFromWindow();
            DownloadController.getInstance(parent.getCurrentAccount()).removeLoadingFileObserver(this);
            firstFrameRendered = false;
        }


        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            updateAttachedState();
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateAttachedState();
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            updateAttachedState();
        }

        public BlockVideoCellState setState(BlockVideoCellState newState) {
            if (videoState != null && newState != null && newState.lastFrameBitmap != null && videoState.lastFrameBitmap != null && newState.lastFrameBitmap != videoState.lastFrameBitmap) {
                videoState.lastFrameBitmap.recycle();
                videoState.lastFrameBitmap = null;
            }
            if (videoState != null && newState != null && newState.lastFrameBitmap == null && videoState.lastFrameBitmap != null) {
                newState.playFrom = videoState.playFrom;
                newState.lastFrameBitmap = videoState.lastFrameBitmap;
            }
            videoState = newState;
            return videoState;
        }

        private void startVideoPlayer() {
            if (currentDocument == null || parent.videoPlayer != null) {
                return;
            }
//            if (!firstFrameRendered) {
//                textureView.setAlpha(0f);
//            }
            parent.videoPlayer = new VideoPlayerHolderBase() {
                @Override
                public boolean needRepeat() {
                    return true;
                }

                @Override
                public void onRenderedFirstFrame() {
                    super.onRenderedFirstFrame();
                    if (!firstFrameRendered) {
                        firstFrameRendered = true;
                        textureView.setAlpha(1f);

                        if (currentBlock != null) {
                            parent.videoStates.put(currentBlock.video_id, setState(BlockVideoCellState.fromPlayer(parent.videoPlayer, BlockVideoCell.this)));
                        }
                    }
                }
            }.with(textureView);

            TLRPC.Document document = currentDocument;
            for (int i = 0; i < document.attributes.size(); i++) {
                if (document.attributes.get(i) instanceof TLRPC.TL_documentAttributeVideo) {
                    TLRPC.TL_documentAttributeVideo videoAttr = (TLRPC.TL_documentAttributeVideo) document.attributes.get(i);
                    aspectRatioFrameLayout.setAspectRatio(videoAttr.w / (float) videoAttr.h, 0);
                }
            }
            Uri uri = adapter.currentPage == null ? null : FileStreamLoadOperation.prepareUri(parent.getCurrentAccount(), document, adapter.currentPage);
            if (uri == null) {
                return;
            }

            parent.videoPlayer.seekTo(videoState == null ? 0 : videoState.playFrom);
            parent.videoPlayer.preparePlayer(uri, true, 1f);
            parent.videoPlayer.play();
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
            StringBuilder sb = new StringBuilder(LocaleController.getString(R.string.AttachVideo));
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

    public static class BlockAudioCell extends View implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private RadialProgress2 radialProgress;
        private SeekBar seekBar;
        private boolean isFirst;
        private int textX;
        private int textY = dp(58);
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

        private TL_iv.pageBlockAudio currentBlock;
        private TLRPC.Document currentDocument;
        private MessageObject currentMessageObject;

        public BlockAudioCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            radialProgress = new RadialProgress2(this);
            radialProgress.setCircleRadius(dp(24));
            TAG = DownloadController.getInstance(parent.getCurrentAccount()).generateObserverTag();

            seekBar = new SeekBar(this);

            seekBar.setDelegate(progress -> {
                if (currentMessageObject == null) {
                    return;
                }
                currentMessageObject.audioProgress = progress;
                MediaController.getInstance().seekToProgress(currentMessageObject, progress);
            });
        }

        public void setBlock(TL_iv.pageBlockAudio block, boolean first, boolean last) {
            currentBlock = block;

            currentMessageObject = adapter.audioBlocks.get(currentBlock);
            if (currentMessageObject != null) {
                currentDocument = currentMessageObject.getDocument();
            }

            isFirst = first;

            seekBar.setColors(parent.getThemedColor(Theme.key_chat_inAudioSeekbar), parent.getThemedColor(Theme.key_chat_inAudioCacheSeekbar), parent.getThemedColor(Theme.key_chat_inAudioSeekbarFill), parent.getThemedColor(Theme.key_chat_inAudioSeekbarFill), parent.getThemedColor(Theme.key_chat_inAudioSeekbarSelected));

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
                if (buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48) || buttonState == 0) {
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
            return buttonPressed != 0 || checkLayoutForLinks(parent, adapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint({"DrawAllocation", "NewApi"})
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = dp(54);

            if (currentBlock != null) {
                int textWidth;
                if (currentBlock.level > 0) {
                    textX = dp(14 * currentBlock.level) + dp(18);
                } else {
                    textX = dp(18);
                }
                textWidth = width - textX - dp(18);
                int size = dp(44);
                buttonX = dp(16);
                buttonY = dp(5);
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);

                captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                if (captionLayout != null) {
                    creditOffset = dp(8) + captionLayout.getHeight();
                    height += creditOffset + dp(8);
                }
                creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, 0, adapter);
                if (creditLayout != null) {
                    height += dp(4) + creditLayout.getHeight();
                }

                if (!isFirst && currentBlock.level <= 0) {
                    height += dp(8);
                }

                String author = currentMessageObject.getMusicAuthor(false);
                String title = currentMessageObject.getMusicTitle(false);
                seekBarX = buttonX + dp(50) + size;
                int w = width - seekBarX - dp(18);
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
                        TypefaceSpan span = new TypefaceSpan(AndroidUtilities.bold());
                        stringBuilder.setSpan(span, 0, author.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    }
                    CharSequence stringFinal = TextUtils.ellipsize(stringBuilder, Theme.chat_audioTitlePaint, w, TextUtils.TruncateAt.END);
                    titleLayout = new DrawingText(parent);
                    titleLayout.textLayout = new StaticLayout(stringFinal, audioTimePaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    titleLayout.parentBlock = currentBlock;
                    seekBarY = buttonY + (size - dp(30)) / 2 + dp(11);
                } else {
                    titleLayout = null;
                    seekBarY = buttonY + (size - dp(30)) / 2;
                }
                seekBar.setSize(w, dp(30));
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
            radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
            radialProgress.setProgressColor(parent.getThemedColor(Theme.key_chat_inFileProgress));
            radialProgress.draw(canvas);
            canvas.save();
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
            canvas.restore();
            int count = 0;
            if (durationLayout != null) {
                canvas.save();
                canvas.translate(buttonX + dp(54), seekBarY + dp(6));
                durationLayout.draw(canvas);
                canvas.restore();
            }
            if (titleLayout != null) {
                canvas.save();
                titleLayout.x = buttonX + dp(54);
                titleLayout.y = seekBarY - dp(16);
                canvas.translate(titleLayout.x, titleLayout.y);
                drawTextSelection(parent, canvas, this, count++);
                titleLayout.draw(canvas, this);
                canvas.restore();
            }
            if (captionLayout != null) {
                canvas.save();
                captionLayout.x = textX;
                captionLayout.y = textY;
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this, count++);
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                creditLayout.x = textX;
                creditLayout.y = textY + creditOffset;
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(parent, canvas, this, count);
                creditLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
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
                        duration = (int) attribute.duration;
                        break;
                    }
                }
            }
            String timeString = AndroidUtilities.formatShortDuration(duration);
            if (lastTimeString == null || lastTimeString != null && !lastTimeString.equals(timeString)) {
                lastTimeString = timeString;
                audioTimePaint.setTextSize(dp(16));
                int timeWidth = (int) Math.ceil(audioTimePaint.measureText(timeString));
                durationLayout = new StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
            audioTimePaint.setColor(parent.getTextColor());
            invalidate();
        }

        public void updateButtonState(boolean animated) {
            final int currentAccount = parent.getCurrentAccount();
            final String fileName = FileLoader.getAttachFileName(currentDocument);
            final File path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true);
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
            final int currentAccount = parent.getCurrentAccount();
            if (buttonState == 0) {
                if (adapter != null && MediaController.getInstance().setPlaylist(adapter.audioMessages, currentMessageObject, 0, false, null)) {
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
                FileLoader.getInstance(currentAccount).loadFile(currentDocument, adapter == null ? null : adapter.currentPage, 1, 1);
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
            DownloadController.getInstance(parent.getCurrentAccount()).removeLoadingFileObserver(this);
            if (titleLayout != null) titleLayout.detach(this);
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateButtonState(false);
            if (titleLayout != null) titleLayout.attach(this);
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrIVAudio));
            if (titleLayout != null) sb.append(", ").append(titleLayout.getText());
            if (captionLayout != null) sb.append(", ").append(captionLayout.getText());
            if (creditLayout != null) sb.append(", ").append(creditLayout.getText());
            info.setText(sb);
        }
    }

    public static class BlockEmbedPostCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

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

        private TL_iv.pageBlockEmbedPost currentBlock;

        public BlockEmbedPostCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            avatarImageView = new ImageReceiver(this);
            avatarImageView.setRoundRadius(dp(20));
            avatarImageView.setImageCoords(dp(18 + 14), dp(8), dp(40), dp(40));

            avatarDrawable = new AvatarDrawable();
        }

        public void setBlock(TL_iv.pageBlockEmbedPost block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                if (currentBlock instanceof TL_pageBlockEmbedPostCaption) {
                    height = 0;
                    textX = dp(18);
                    textY = dp(4);
                    int textWidth = width - dp(36 + 14);
                    captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                    if (captionLayout != null) {
                        creditOffset = dp(4) + captionLayout.getHeight();
                        height += creditOffset + dp(4);
                    }
                    creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                    if (creditLayout != null) {
                        height += dp(4) + creditLayout.getHeight();
                    }
                } else {
                    if (avatarVisible = (currentBlock.author_photo_id != 0)) {
                        TLRPC.Photo photo = adapter.getPhotoWithId(currentBlock.author_photo_id);
                        if (avatarVisible = (photo instanceof TLRPC.TL_photo)) {
                            avatarDrawable.setInfo(0, currentBlock.author, null);
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, dp(40), true);
                            avatarImageView.setImage(ImageLocation.getForPhoto(image, photo), "40_40", avatarDrawable, 0, null, adapter.currentPage, 1);
                        }
                    }
                    nameLayout = createLayoutForText(parent, this, currentBlock.author, null, width - dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, adapter);
                    if (nameLayout != null) {
                        nameLayout.x = dp(18 + 14 + (avatarVisible ? 40 + 14 : 0));
                        nameLayout.y = dp(dateLayout != null ? 10 : 19);
                    }

                    if (currentBlock.date != 0) {
                        dateLayout = createLayoutForText(parent, this, LocaleController.getInstance().getChatFullDate().format((long) currentBlock.date * 1000), null, width - dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), dp(29), currentBlock, adapter);
                    } else {
                        dateLayout = null;
                    }

                    height = dp(40 + 8 + 8);

                    if (currentBlock.blocks.isEmpty()) {
                        textX = dp(18 + 14);
                        textY = dp(40 + 8 + 8);
                        int textWidth = width - dp(36 + 14);
                        captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                        if (captionLayout != null) {
                            creditOffset = dp(4) + captionLayout.getHeight();
                            height += creditOffset + dp(4);
                        }
                        creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                        if (creditLayout != null) {
                            height += dp(4) + creditLayout.getHeight();
                        }
                    } else {
                        captionLayout = null;
                        creditLayout = null;
                    }

                    if (dateLayout != null) {
                        dateLayout.x = dp(18 + 14 + (avatarVisible ? 40 + 14 : 0));
                        dateLayout.y = dp(29);
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
                    canvas.translate(dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), dp(dateLayout != null ? 10 : 19));
                    drawTextSelection(parent, canvas, this, count++);
                    nameLayout.draw(canvas, this);
                    canvas.restore();
                }
                if (dateLayout != null) {
                    canvas.save();
                    canvas.translate(dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), dp(29));
                    drawTextSelection(parent, canvas, this, count++);
                    dateLayout.draw(canvas, this);
                    canvas.restore();
                }
                canvas.drawRect(dp(18), dp(6), dp(20), lineHeight - (currentBlock.level != 0 ? 0 : dp(6)), quoteLinePaint);
            }
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this, count++);
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(parent, canvas, this, count);
                creditLayout.draw(canvas, this);
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (nameLayout != null) nameLayout.attach(this);
            if (dateLayout != null) dateLayout.attach(this);
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (nameLayout != null) nameLayout.detach(this);
            if (dateLayout != null) dateLayout.detach(this);
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrIVEmbedPost));
            if (nameLayout != null) sb.append(", ").append(nameLayout.getText());
            if (dateLayout != null) sb.append(", ").append(dateLayout.getText());
            if (captionLayout != null) sb.append(", ").append(captionLayout.getText());
            if (creditLayout != null) sb.append(", ").append(creditLayout.getText());
            info.setText(sb);
        }
    }

    public static class BlockParagraphCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        public DrawingText textLayout;
        public int textX;
        public int textY;

        private TL_iv.pageBlockParagraph currentBlock;

        public BlockParagraphCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockParagraph block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.level == 0) {
                    textY = dp(parent.pady());
                    textX = dp(parent.padx());
                } else {
                    textY = 0;
                    textX = dp(parent.padx() + 14 * currentBlock.level);
                }
                final Layout.Alignment paraAlign;
                if (currentBlock.text instanceof TL_iv.textMath) {
                    paraAlign = Layout.Alignment.ALIGN_CENTER;
                } else {
                    paraAlign = adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL;
                }
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(parent.padx()) - textX, textY, currentBlock, paraAlign, 0, adapter);
                if (textLayout != null) {
                    height = textLayout.getHeight();
                    if (currentBlock.level > 0) {
                        height += dp(parent.pady());
                    } else {
                        height += dp(2 * parent.pady());
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(buildAccessibilityText(parent, adapter, textLayout));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    private class BlockEmbedCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private class TelegramWebviewProxy {
            @Keep
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

        @Nullable
        private final TouchyWebView webView;
        @Nullable
        private final WebPlayerView videoView;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int textX;
        private int textY;
        private int creditOffset;
        private int listX;
        private int exactWebViewHeight;
        private boolean wasUserInteraction;

        private TL_iv.pageBlockEmbed currentBlock;

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

            if (Looper.myLooper() == Looper.getMainLooper()) {
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
                    public TextureView onSwitchInlineMode(View controlsView, boolean inline, int videoWidth, int videoHeight, int rotation, boolean animated) {
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
                webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
                webView.addJavascriptInterface(new TelegramWebviewProxy(), "TelegramWebviewProxy");

                webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptThirdPartyCookies(webView, true);

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
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        try {
                            if (LaunchActivity.instance != null && LaunchActivity.instance.isFinishing()) {
                                return true;
                            }
                            new AlertDialog.Builder(getContext(), null)
                                    .setTitle(getString(R.string.ChromeCrashTitle))
                                    .setMessage(AndroidUtilities.replaceSingleTag(getString(R.string.ChromeCrashMessage), () -> Browser.openUrl(getContext(), "https://play.google.com/store/apps/details?id=com.google.android.webview")))
                                    .setPositiveButton(getString(R.string.OK), null)
                                    .show();
                            return true;
                        } catch (Exception e) {
                            FileLog.e(e);
                            return false;
                        }
                    }

                    @Override
                    public void onLoadResource(WebView view, String url) {
                        super.onLoadResource(view, url);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
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
            } else {
                videoView = null;
                webView = null;
            }
        }

        public void destroyWebView(boolean completely) {
            try {
                if (webView != null) {
                    webView.stopLoading();
                    webView.loadUrl("about:blank");
                    if (completely) {
                        webView.destroy();
                    }
                }
                currentBlock = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (videoView != null) {
                videoView.destroy();
            }
        }

        public void setBlock(TL_iv.pageBlockEmbed block) {
            TL_iv.pageBlockEmbed previousBlock = currentBlock;
            currentBlock = block;
            if (webView != null) {
                webView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            }
            if (previousBlock != currentBlock) {
                wasUserInteraction = false;
                if (webView != null) {
                    if (currentBlock.allow_scrolling) {
                        webView.setVerticalScrollBarEnabled(true);
                        webView.setHorizontalScrollBarEnabled(true);
                    } else {
                        webView.setVerticalScrollBarEnabled(false);
                        webView.setHorizontalScrollBarEnabled(false);
                    }
                }
                exactWebViewHeight = 0;
                if (webView != null) {
                    try {
                        webView.loadUrl("about:blank");
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                try {
                    if (currentBlock.html != null) {
                        if (webView != null) {
                            webView.loadDataWithBaseURL("https://telegram.org/embed", currentBlock.html, "text/html", "UTF-8", null);
                            webView.setVisibility(VISIBLE);
                        }
                        if (videoView != null) {
                            videoView.setVisibility(INVISIBLE);
                            videoView.loadVideo(null, null, null, null, false);
                        }
                    } else {
                        TLRPC.Photo thumb = currentBlock.poster_photo_id != 0 ? parentAdapter.getPhotoWithId(currentBlock.poster_photo_id) : null;
                        boolean handled = videoView.loadVideo(block.url, thumb, parentAdapter.currentPage, null, false);
                        if (handled) {
                            if (webView != null) {
                                webView.setVisibility(INVISIBLE);
                                webView.stopLoading();
                                webView.loadUrl("about:blank");
                            }
                            if (videoView != null) {
                                videoView.setVisibility(VISIBLE);
                            }
                        } else {
                            if (webView != null) {
                                webView.setVisibility(VISIBLE);
                                HashMap<String, String> args = new HashMap<>();
                                args.put("Referer", ApplicationLoader.applicationContext.getPackageName());
                                webView.loadUrl(currentBlock.url, args);
                            }
                            if (videoView != null) {
                                videoView.setVisibility(INVISIBLE);
                                videoView.loadVideo(null, null, null, null, false);
                            }
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
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
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
                    textX = listX = dp(14 * currentBlock.level) + dp(18);
                    listWidth -= listX + dp(18);
                    textWidth = listWidth;
                } else {
                    listX = 0;
                    textX = dp(18);
                    textWidth = width - dp(36);
                    if (!currentBlock.full_width) {
                        listWidth -= dp(36);
                        listX += dp(18);
                    }
                }
                float scale;
                if (currentBlock.w == 0) {
                    scale = 1;
                } else {
                    scale = width / (float) currentBlock.w;
                }
                if (exactWebViewHeight != 0) {
                    height = dp(exactWebViewHeight);
                } else {
                    height = (int) (currentBlock.w == 0 ? dp(currentBlock.h) * scale : currentBlock.h * scale);
                }
                if (height == 0) {
                    height = dp(10);
                }
                if (webView != null) {
                    webView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                }
                if (videoView != null && videoView.getParent() == this) {
                    videoView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height + dp(10), MeasureSpec.EXACTLY));
                }

                textY = dp(8) + height;
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = dp(4) + captionLayout.getHeight();
                    height += creditOffset + dp(4);
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = creditOffset;
                }

                height += dp(5);

                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += dp(8);
                } else if (currentBlock.level == 0 && captionLayout != null) {
                    height += dp(8);
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
            if (webView != null) {
                webView.layout(listX, 0, listX + webView.getMeasuredWidth(), webView.getMeasuredHeight());
            }
            if (videoView != null && videoView.getParent() == this) {
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
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas, this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrIVEmbed));
            if (captionLayout != null) sb.append(", ").append(captionLayout.getText());
            if (creditLayout != null) sb.append(", ").append(creditLayout.getText());
            info.setText(sb);
        }
    }

    public static class BlockTableCell extends FrameLayout implements TableLayout.TableLayoutDelegate, TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private HorizontalScrollView scrollView;
        private DrawingText titleLayout;
        public TableLayout tableLayout;
        private int listX;
        private int listY;
        private int textX;
        private int textY;

        private boolean firstLayout;

        private TL_iv.pageBlockTable currentBlock;

        private float selectionDownX, selectionDownY;
        private boolean selectionPending;
        private final Runnable selectionLongPress = new Runnable() {
            @Override
            public void run() {
                if (!selectionPending) return;
                selectionPending = false;
                if (!parent.canStartSelection(BlockTableCell.this)) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = parent.getTextSelectionHelper(BlockTableCell.this);
                if (helper == null) return;
                if (helper.isInSelectionMode()) return;
                helper.setMaybeView((int) selectionDownX, (int) selectionDownY, BlockTableCell.this);
                helper.trySelect(BlockTableCell.this);
                if (helper.isInSelectionMode()) {
                    ViewParent p = getParent();
                    while (p != null) {
                        p.requestDisallowInterceptTouchEvent(true);
                        p = p.getParent();
                    }
                    try {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                }
            }
        };

        public BlockTableCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            scrollView = new HorizontalScrollView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    boolean intercept = super.onInterceptTouchEvent(ev);
                    if (tableLayout.getMeasuredWidth() > getMeasuredWidth() - dp(36) && intercept) {
                        // TODO
//                        parent.windowView.requestDisallowInterceptTouchEvent(true);
                    }
                    return intercept;
                }

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    if (tableLayout.getMeasuredWidth() <= getMeasuredWidth() - dp(36)) {
                        return false;
                    }
                    return super.onTouchEvent(ev);
                }

                @Override
                protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                    super.onScrollChanged(l, t, oldl, oldt);
                    if (parent.pressedLinkOwnerLayout != null) {
                        parent.pressedLinkOwnerLayout = null;
                        parent.pressedLinkOwnerView = null;
                    }
                    updateChildTextPositions();
                    final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(null);
                    if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.invalidate();
                    }
                }

                @Override
                protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
                    removePressedLink(parent);
                    return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    tableLayout.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight(), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), tableLayout.getMeasuredHeight());
                }
            };
            scrollView.setPadding(dp(parent.padx()), 0, dp(parent.padx()), 0);
            scrollView.setClipToPadding(false);
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(null);
            tableLayout = new TableLayout(context, this, textSelectionHelper);
            tableLayout.setOrientation(TableLayout.HORIZONTAL);
            tableLayout.setRowOrderPreserved(true);
            scrollView.addView(tableLayout, new HorizontalScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            setWillNotDraw(false);
        }

        @Override
        public DrawingText createTextLayout(TL_iv.pageTableCell cell, int maxWidth) {
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
            return createLayoutForText(parent, this, null, cell.text, maxWidth, -1, currentBlock, alignment, 0, adapter);
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
        public void onLayoutChild(TableLayout.CellText text, int x, int y) {
            if (text instanceof DrawingText && !parent.searchResults.isEmpty() && parent.searchText != null) {
                final DrawingText dt = (DrawingText) text;
                String lowerString = dt.textLayout.getText().toString().toLowerCase();
                int startIndex = 0;
                int index;
                while ((index = lowerString.indexOf(parent.searchText, startIndex)) >= 0) {
                    startIndex = index + parent.searchText.length();
                    if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString.charAt(index - 1))) {
                        adapter.searchTextOffset.put(parent.searchText + currentBlock + dt.parentText + index, y + dt.textLayout.getLineTop(dt.textLayout.getLineForOffset(index)));
                    }
                }
            }
        }

        public void setBlock(TL_iv.pageBlockTable block) {
            currentBlock = block;
            AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, parent.getThemedColor(Theme.key_windowBackgroundWhite));
            tableLayout.removeAllChildrens();
            tableLayout.setDrawLines(currentBlock.bordered);
            tableLayout.setStriped(currentBlock.striped);
            tableLayout.setRtl(adapter != null && adapter.isRtl);

            int maxCols = 0;

            if (!currentBlock.rows.isEmpty()) {
                TL_iv.pageTableRow row = currentBlock.rows.get(0);
                for (int c = 0, size2 = row.cells.size(); c < size2; c++) {
                    TL_iv.pageTableCell cell = row.cells.get(c);
                    maxCols += (cell.colspan != 0 ? cell.colspan : 1);
                }
            }

            for (int r = 0, size = currentBlock.rows.size(); r < size; r++) {
                TL_iv.pageTableRow row = currentBlock.rows.get(r);
                int cols = 0;
                for (int c = 0, size2 = row.cells.size(); c < size2; c++) {
                    TL_iv.pageTableCell cell = row.cells.get(c);
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
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (parent.canStartSelection(this)) {
                    selectionDownX = ev.getX();
                    selectionDownY = ev.getY();
                    selectionPending = true;
                    removeCallbacks(selectionLongPress);
                    postDelayed(selectionLongPress, ViewConfiguration.getLongPressTimeout());
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (selectionPending) {
                    final float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.abs(ev.getX() - selectionDownX) > slop || Math.abs(ev.getY() - selectionDownY) > slop) {
                        selectionPending = false;
                        removeCallbacks(selectionLongPress);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                selectionPending = false;
                removeCallbacks(selectionLongPress);
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout instanceof DrawingText && checkLayoutForLinks(parent, adapter, event, this, (DrawingText) c.textLayout, scrollView.getPaddingLeft() - scrollView.getScrollX() + listX + c.getTextX(), listY + c.getTextY())) {
                    return true;
                }
            }
            return checkLayoutForLinks(parent, adapter, event, this, titleLayout, textX, textY) || super.onTouchEvent(event);
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
                    listX = dp(14 * currentBlock.level);
                    textX = listX + dp(parent.padx());
                    textWidth = width - textX;
                } else {
                    listX = 0;
                    textX = dp(parent.padx());
                    textWidth = width - dp(2 * parent.padx());
                }

                titleLayout = createLayoutForText(parent, this, null, currentBlock.title, textWidth, 0, currentBlock, Layout.Alignment.ALIGN_CENTER, 0, adapter);
                if (titleLayout != null) {
                    textY = 0;
                    height += titleLayout.getHeight() + dp(8);
                    listY = height;
                    titleLayout.x = textX;
                    titleLayout.y = textY;
                } else {
                    listY = dp(8);
                }

                scrollView.measure(MeasureSpec.makeMeasureSpec(width - listX, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height += scrollView.getMeasuredHeight() + dp(8);

                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += dp(8);
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
                    c.textLayout.setX(c.getTextX() + listX + dp(18) - scrollView.getScrollX());
                    c.textLayout.setY(c.getTextY() + listY);
                    c.textLayout.setRow(c.getRow());
                    c.setSelectionIndex(count++);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            scrollView.layout(listX, listY, listX + scrollView.getMeasuredWidth(), listY + scrollView.getMeasuredHeight());
            if (firstLayout) {
                if (adapter != null && adapter.isRtl) {
                    scrollView.setScrollX(tableLayout.getMeasuredWidth() - scrollView.getMeasuredWidth() + dp(36));
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
                drawTextSelection(parent, canvas, this, 0);
                titleLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (titleLayout != null) titleLayout.attach(this);
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout != null) {
                    c.textLayout.attach(this);
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (titleLayout != null) titleLayout.detach(this);
            for (int i = 0, N = tableLayout.getChildCount(); i < N; i++) {
                TableLayout.Child c = tableLayout.getChildAt(i);
                if (c.textLayout != null) {
                    c.textLayout.detach(this);
                }
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrIVTable));
            if (titleLayout != null) sb.append(", ").append(titleLayout.getText());
            info.setText(sb);
        }
    }

    private class BlockCollageCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private RecyclerListView innerListView;
        private RecyclerView.Adapter innerAdapter;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int listX;
        private int textX;
        private int textY;
        private int creditOffset;

        private boolean inLayout;

        private TL_iv.pageBlockCollage currentBlock;
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
                    lineCounts = new int[]{i1, i2};
                    heights = new float[]{f1, f2};
                }

                public MessageGroupedLayoutAttempt(int i1, int i2, int i3, float f1, float f2, float f3) {
                    lineCounts = new int[]{i1, i2, i3};
                    heights = new float[]{f1, f2, f3};
                }

                public MessageGroupedLayoutAttempt(int i1, int i2, int i3, int i4, float f1, float f2, float f3, float f4) {
                    lineCounts = new int[]{i1, i2, i3, i4};
                    heights = new float[]{f1, f2, f3, f4};
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
                    if (object instanceof TL_iv.pageBlockPhoto) {
                        TL_iv.pageBlockPhoto pageBlockPhoto = (TL_iv.pageBlockPhoto) object;
                        TLRPC.Photo photo = parentAdapter.getPhotoWithId(pageBlockPhoto.photo_id);
                        if (photo == null) {
                            continue;
                        }
                        photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    } else if (object instanceof TL_iv.pageBlockVideo) {
                        TL_iv.pageBlockVideo pageBlockVideo = (TL_iv.pageBlockVideo) object;
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

                int minHeight = dp(120);
                int minWidth = (int) (dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
                int paddingsWidth = (int) (dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));

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

                            position1.siblingHeights = new float[]{thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight};

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

                            position1.siblingHeights = new float[]{h0, h1, h2};
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
                        for (int b = 0; b < attempt.heights.length; b++) {
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
                                h -= (int) Math.ceil(maxHeight * pos.ph) - dp(4);
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
                            view = new BlockPhotoCell(getContext(), ArticleViewer.this, parentAdapter, 2);
                            break;
                        }
                        case 1:
                        default: {
                            view = new BlockVideoCell(getContext(), ArticleViewer.this, parentAdapter, 2);
                            break;
                        }
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    TL_iv.PageBlock pageBlock = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    switch (holder.getItemViewType()) {
                        case 0: {
                            BlockPhotoCell cell = (BlockPhotoCell) holder.itemView;
                            cell.groupPosition = group.positions.get(pageBlock);
                            // TODO
                            cell.setBlock((TL_iv.pageBlockPhoto) pageBlock, parentAdapter.currentPage.cached_page, null, false, true);
                            break;
                        }
                        case 1:
                        default: {
                            BlockVideoCell cell = (BlockVideoCell) holder.itemView;
                            cell.groupPosition = group.positions.get(pageBlock);
                            TL_iv.pageBlockVideo blockVideo = (TL_iv.pageBlockVideo) pageBlock;
                            cell.setBlock(blockVideo, videoStates.get(blockVideo.video_id), false, true, true);
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
                    TL_iv.PageBlock block = currentBlock.items.get(currentBlock.items.size() - position - 1);
                    if (block instanceof TL_iv.pageBlockPhoto) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
            addView(innerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            setWillNotDraw(false);
        }

        public void setBlock(TL_iv.pageBlockCollage block) {
            if (currentBlock != block) {
                currentBlock = block;
                group.calculate();
            }
            innerAdapter.notifyDataSetChanged();
            innerListView.setGlowColor(getThemedColor(Theme.key_windowBackgroundWhite));
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
                    textX = listX = dp(14 * currentBlock.level) + dp(18);
                    listWidth -= listX + dp(18);
                    textWidth = listWidth;
                } else {
                    listX = 0;
                    textX = dp(18);
                    textWidth = width - dp(36);
                }

                innerListView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = innerListView.getMeasuredHeight();

                textY = height + dp(8);
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = dp(4) + captionLayout.getHeight();
                    height += creditOffset + dp(4);
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = textY + creditOffset;
                }

                height += dp(16);
                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += dp(8);
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
            inLayout = false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            innerListView.layout(listX, dp(8), listX + innerListView.getMeasuredWidth(), innerListView.getMeasuredHeight() + dp(8));
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
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas, this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrCollage));
            if (captionLayout != null) sb.append(", ").append(captionLayout.getText());
            if (creditLayout != null) sb.append(", ").append(creditLayout.getText());
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }
    }

    private static Paint dotsPaint;

    private class BlockSlideshowCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private ViewPager innerListView;
        private PagerAdapter innerAdapter;
        private View dotsContainer;

        private TL_iv.pageBlockSlideshow currentBlock;
        private DrawingText captionLayout;
        private DrawingText creditLayout;
        private int textX = dp(18);
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
                    private TL_iv.PageBlock block;
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
                    TL_iv.PageBlock block = currentBlock.items.get(position);
                    if (block instanceof TL_iv.pageBlockPhoto) {
                        view = new BlockPhotoCell(getContext(), ArticleViewer.this, parentAdapter, 1);
                        ((BlockPhotoCell) view).setBlock((TL_iv.pageBlockPhoto) block, parentAdapter.currentPage.cached_page, null, false, true);
                    } else {
                        view = new BlockVideoCell(getContext(), ArticleViewer.this, parentAdapter, 1);
                        TL_iv.pageBlockVideo videoBlock = (TL_iv.pageBlockVideo) block;
                        ((BlockVideoCell) view).setBlock(videoBlock, videoStates.get(videoBlock.video_id), false, true, true);
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
            AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, getThemedColor(Theme.key_windowBackgroundWhite));
            addView(innerListView);

            dotsContainer = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (currentBlock == null) {
                        return;
                    }

                    int count = innerAdapter.getCount();
                    int totalWidth = count * dp(7) + (count - 1) * dp(6) + dp(4);
                    int xOffset;
                    if (totalWidth < getMeasuredWidth()) {
                        xOffset = (getMeasuredWidth() - totalWidth) / 2;
                    } else {
                        xOffset = dp(4);
                        int size = dp(13);
                        int halfCount = (getMeasuredWidth() - dp(8)) / 2 / size;
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
                        int cx = xOffset + dp(4) + dp(13) * a;
                        Drawable drawable = currentPage == a ? slideDotBigDrawable : slideDotDrawable;
                        drawable.setBounds(cx - dp(5), 0, cx + dp(5), dp(10));
                        drawable.draw(canvas);
                    }
                }
            };
            addView(dotsContainer);

            setWillNotDraw(false);
        }

        public void setBlock(TL_iv.pageBlockSlideshow block) {
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
                height = dp(310);
                innerListView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                int count = currentBlock.items.size();
                dotsContainer.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(10), MeasureSpec.EXACTLY));

                int textWidth = width - dp(36);
                textY = height + dp(16);
                captionLayout = createLayoutForText(this, null, currentBlock.caption.text, textWidth, textY, currentBlock, parentAdapter);
                if (captionLayout != null) {
                    creditOffset = dp(4) + captionLayout.getHeight();
                    height += creditOffset + dp(4);
                    captionLayout.x = textX;
                    captionLayout.y = textY;
                } else {
                    creditOffset = 0;
                }
                creditLayout = createLayoutForText(this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, parentAdapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, parentAdapter);
                if (creditLayout != null) {
                    height += dp(4) + creditLayout.getHeight();
                    creditLayout.x = textX;
                    creditLayout.y = textY + creditOffset;
                }

                height += dp(16);
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            innerListView.layout(0, dp(8), innerListView.getMeasuredWidth(), dp(8) + innerListView.getMeasuredHeight());
            int y = innerListView.getBottom() - dp(7 + 16);
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
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(canvas, this, count);
                creditLayout.draw(canvas, this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(getString(R.string.AccDescrIVSlideshow));
            if (captionLayout != null) sb.append(", ").append(captionLayout.getText());
            if (creditLayout != null) sb.append(", ").append(creditLayout.getText());
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }
    }

    public static class BlockListItemCell extends ViewGroup implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

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
        private CheckBoxBase checkbox;

        public BlockListItemCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
            setWillNotDraw(false);
        }

        private int numLayoutX() {
            final DrawingText numLayout = currentBlock != null ? currentBlock.numLayout : null;
            if (numLayout == null) return 0;
            if (adapter != null && adapter.isRtl) {
                return getMeasuredWidth() - dp(parent.padx() - 3) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * dp(12);
            }
            return dp(parent.padx() - 3) + currentBlock.parent.maxNumWidth - (int) Math.ceil(numLayout.getLineWidth(0)) + currentBlock.parent.level * dp(12);
        }

        @Override
        public int getBoundLeft() {
            final int padx = dp(parent.padx());
            int left = Integer.MAX_VALUE;
            if (checkbox != null) {
                left = Math.min(left, textX - dp(26) - padx);
            }
            if (currentBlock != null && currentBlock.numLayout != null) {
                left = Math.min(left, numLayoutX() + currentBlock.numLayout.getBoundLeft() - padx);
            }
            if (textLayout != null) left = Math.min(left, textLayout.x + textLayout.getBoundLeft() - padx);
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int l = ((IBlock) blockLayout.itemView).getBoundLeft();
                if (l != -1) left = Math.min(left, blockX + l);
            }
            return left == Integer.MAX_VALUE ? -1 : left;
        }

        @Override
        public int getBoundRight() {
            final int padx = dp(parent.padx());
            int right = Integer.MIN_VALUE;
            if (currentBlock != null && currentBlock.numLayout != null) {
                right = Math.max(right, numLayoutX() + currentBlock.numLayout.getBoundRight() + padx);
            }
            if (textLayout != null) right = Math.max(right, textLayout.x + textLayout.getBoundRight() + padx);
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int r = ((IBlock) blockLayout.itemView).getBoundRight();
                if (r != -1) right = Math.max(right, blockX + r);
            }
            return right == Integer.MIN_VALUE ? -1 : right;
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout != null) return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int r = ((IBlock) blockLayout.itemView).getLastLineBoundRight();
                if (r != -1) return blockX + r;
            }
            return -1;
        }

        public void setBlock(TL_pageBlockListItem block) {
            if (currentBlock != block) {
                currentBlock = block;
                if (blockLayout != null) {
                    removeView(blockLayout.itemView);
                    blockLayout = null;
                }
                if (currentBlock.blockItem != null && adapter != null) {
                    currentBlockType = adapter.getTypeForBlock(currentBlock.blockItem);
                    blockLayout = adapter.onCreateViewHolder(this, currentBlockType);
                    addView(blockLayout.itemView);
                }
            }
            if (currentBlock.blockItem != null && adapter != null) {
                adapter.bindBlockToHolder(currentBlockType, blockLayout, currentBlock.blockItem, 0, 0, false);
            }
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY)) {
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
                textY = currentBlock.index == 0 && currentBlock.parent.level == 0 ? dp(2 + parent.pady()) : 0;
                numOffsetY = 0;
                if (currentBlock.parent.lastMaxNumCalcWidth != width || currentBlock.parent.lastFontSize != SharedConfig.ivFontSize) {
                    currentBlock.parent.lastMaxNumCalcWidth = width;
                    currentBlock.parent.lastFontSize = SharedConfig.ivFontSize;
                    currentBlock.parent.maxNumWidth = 0;
                    boolean allCheckboxes = true;
                    for (int a = 0, size = currentBlock.parent.items.size(); a < size; a++) {
                        final TL_pageBlockListItem item = currentBlock.parent.items.get(a);
                        if (item.num == null) continue;
                        if (item.isCheckbox && "•".equalsIgnoreCase(item.num)) {
                            item.numLayout = null;
                        } else {
                            allCheckboxes = false;
                            item.numLayout = createLayoutForText(parent, this, item.num, null, width - dp(3 * parent.padx()), textY, currentBlock, adapter);
                            currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(item.numLayout.getLineWidth(0)));
                        }
                    }
                    if (listTextNumPaint != null && !allCheckboxes) {
                        currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(listTextNumPaint.measureText("00.")));
                    }
                }
                drawDot = !currentBlock.parent.pageBlockList.ordered;
                if (currentBlock.isCheckbox) {
                    if (checkbox == null) {
                        checkbox = new CheckBoxBase(this, 20, parent.getResourcesProvider());
                        checkbox.setColor(Theme.key_telegram_color, Theme.key_dialogCheckboxSquareDisabled, Theme.key_checkboxCheck);
                        checkbox.setBackgroundType(10);
                        checkbox.setDrawUnchecked(true);
                        checkbox.setCustomRadius(dp(5));
                    }
                    checkbox.setChecked(currentBlock.checked, false);
                } else {
                    checkbox = null;
                }

                if (adapter != null && adapter.isRtl) {
                    textX = dp(parent.padx() + (checkbox != null ? 26 : 0));
                } else {
                    textX = dp(parent.padx() + (checkbox != null ? 26 : 0) + 6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * dp(12);
                }
                int maxWidth = width - dp(parent.padx()) - textX;
                if (adapter != null && adapter.isRtl) {
                    maxWidth -= dp(6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * dp(12);
                }
                if (currentBlock.textItem != null) {
                    textLayout = createLayoutForText(parent, this, null, currentBlock.textItem, maxWidth, textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                    if (textLayout != null && textLayout.getLineCount() > 0) {
                        if (currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            int ascent = textLayout.getLineAscent(0);
                            numOffsetY = (currentBlock.numLayout.getLineAscent(0) + dp(2.5f)) - ascent;
                        }
                        height += textLayout.getHeight() + dp(parent.pady());
                    }
                } else if (currentBlock.blockItem != null) {
                    blockX = textX;
                    blockY = textY;
                    if (blockLayout != null) {
                        if (blockLayout.itemView instanceof BlockParagraphCell) {
                            blockY -= dp(parent.pady());
                            if (!(adapter != null && adapter.isRtl)) {
                                blockX -= dp(parent.padx());
                            }
                            maxWidth += dp(parent.padx());
                            height -= dp(parent.pady());
                        } else if (blockLayout.itemView instanceof BlockHeaderCell ||
                                blockLayout.itemView instanceof BlockSubheaderCell ||
                                blockLayout.itemView instanceof BlockTitleCell ||
                                blockLayout.itemView instanceof BlockSubtitleCell) {
                            if (!(adapter != null && adapter.isRtl)) {
                                blockX -= dp(parent.padx());
                            }
                            maxWidth += dp(parent.padx());
                        } else if (isListItemBlock(currentBlock.blockItem)) {
                            blockX = 0;
                            blockY = 0;
                            textY = 0;
                            if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                                height -= dp(2 + parent.pady());
                            }
                            maxWidth = width;
                            height -= dp(parent.pady());
                        } else if (blockLayout.itemView instanceof BlockTableCell) {
                            blockX -= dp(parent.padx());
                            maxWidth += dp(2 * parent.padx());
                        }
                        blockLayout.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        if (blockLayout.itemView instanceof BlockParagraphCell && currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            BlockParagraphCell paragraphCell = (BlockParagraphCell) blockLayout.itemView;
                            if (paragraphCell.textLayout != null && paragraphCell.textLayout.getLineCount() > 0) {
                                int ascent = paragraphCell.textLayout.getLineAscent(0);
                                numOffsetY = (currentBlock.numLayout.getLineAscent(0) + dp(2.5f)) - ascent;
                            }
                        }
                        if (currentBlock.blockItem instanceof TL_iv.pageBlockDetails) {
                            verticalAlign = true;
                            blockY = 0;
                            if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                                height -= dp(2 + parent.pady());
                            }
                            height -= dp(parent.pady());
                        } else if (blockLayout.itemView instanceof BlockOrderedListItemCell) {
                            verticalAlign = ((BlockOrderedListItemCell) blockLayout.itemView).verticalAlign;
                        } else if (blockLayout.itemView instanceof BlockListItemCell) {
                            verticalAlign = ((BlockListItemCell) blockLayout.itemView).verticalAlign;
                        }
                        if (verticalAlign && currentBlock.numLayout != null) {
                            textY = (blockLayout.itemView.getMeasuredHeight() - currentBlock.numLayout.getHeight()) / 2 - dp(4);
                            drawDot = false;
                        }
                        height += blockLayout.itemView.getMeasuredHeight();
                    }
                    height += dp(parent.pady());
                }
                if (currentBlock.parent.items.get(currentBlock.parent.items.size() - 1) == currentBlock) {
                    height += dp(parent.pady());
                }
                if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                    height += dp(2 + parent.pady());
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
                if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                    final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(null);
                    if (textSelectionHelper != null) {
                        textSelectionHelper.arrayList.clear();
                        ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(textSelectionHelper.arrayList);
                        for (TextSelectionHelper.TextLayoutBlock block : textSelectionHelper.arrayList) {
                            if (block instanceof DrawingText) {
                                ((DrawingText) block).x += blockX;
                                ((DrawingText) block).y += blockY;
                            }
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
                if (adapter != null && adapter.isRtl) {
                    canvas.translate(width - dp(parent.padx() - 3) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * dp(12), textY + numOffsetY - (drawDot ? dp(1) : 0));
                } else {
                    canvas.translate(dp(parent.padx() - 3) + currentBlock.parent.maxNumWidth - (int) Math.ceil(currentBlock.numLayout.getLineWidth(0)) + currentBlock.parent.level * dp(12), textY + numOffsetY - (drawDot ? dp(1) : 0));
                }
                currentBlock.numLayout.draw(canvas, this);
                canvas.restore();
            }
            if (checkbox != null) {
                checkbox.setBounds(textX - dp(26), textY, dp(20), dp(20));
                checkbox.draw(canvas);
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
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
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(buildAccessibilityText(parent, adapter, textLayout));
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockOrderedListItemCell extends ViewGroup implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

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
        private CheckBoxBase checkbox;

        public BlockOrderedListItemCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
            setWillNotDraw(false);
        }

        private int numLayoutX() {
            final DrawingText numLayout = currentBlock != null ? currentBlock.numLayout : null;
            if (numLayout == null) return 0;
            if (adapter != null && adapter.isRtl) {
                return getMeasuredWidth() - dp(parent.padx()) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * dp(20);
            }
            return dp(parent.padx()) + currentBlock.parent.maxNumWidth - (int) Math.ceil(numLayout.getLineWidth(0)) + currentBlock.parent.level * dp(20);
        }

        @Override
        public int getBoundLeft() {
            final int padx = dp(parent.padx());
            int left = Integer.MAX_VALUE;
            if (currentBlock != null && currentBlock.numLayout != null) {
                left = Math.min(left, numLayoutX() + currentBlock.numLayout.getBoundLeft() - padx);
            }
            if (textLayout != null) left = Math.min(left, textLayout.x + textLayout.getBoundLeft() - padx);
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int l = ((IBlock) blockLayout.itemView).getBoundLeft();
                if (l != -1) left = Math.min(left, blockX + l);
            }
            return left == Integer.MAX_VALUE ? -1 : left;
        }

        @Override
        public int getBoundRight() {
            final int padx = dp(parent.padx());
            int right = Integer.MIN_VALUE;
            if (currentBlock != null && currentBlock.numLayout != null) {
                right = Math.max(right, numLayoutX() + currentBlock.numLayout.getBoundRight() + padx);
            }
            if (textLayout != null) right = Math.max(right, textLayout.x + textLayout.getBoundRight() + padx);
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int r = ((IBlock) blockLayout.itemView).getBoundRight();
                if (r != -1) right = Math.max(right, blockX + r);
            }
            return right == Integer.MIN_VALUE ? -1 : right;
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout != null) return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
            if (blockLayout != null && blockLayout.itemView instanceof IBlock) {
                final int r = ((IBlock) blockLayout.itemView).getLastLineBoundRight();
                if (r != -1) return blockX + r;
            }
            return -1;
        }

        public void setBlock(TL_pageBlockOrderedListItem block) {
            if (currentBlock != block) {
                currentBlock = block;
                if (blockLayout != null) {
                    removeView(blockLayout.itemView);
                    blockLayout = null;
                }
                if (currentBlock.blockItem != null && adapter != null) {
                    currentBlockType = adapter.getTypeForBlock(currentBlock.blockItem);
                    blockLayout = adapter.onCreateViewHolder(this, currentBlockType);
                    addView(blockLayout.itemView);
                }
            }
            if (currentBlock.blockItem != null && adapter != null) {
                adapter.bindBlockToHolder(currentBlockType, blockLayout, currentBlock.blockItem, 0, 0, false);
            }
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY)) {
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
                textY = currentBlock.index == 0 && currentBlock.parent.level == 0 ? dp(10) : 0;
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
                        item.numLayout = createLayoutForText(parent, this, item.num, null, width - dp(3 * parent.padx()), textY, currentBlock, adapter);
                        currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(item.numLayout.getLineWidth(0)));
                    }
                    currentBlock.parent.maxNumWidth = Math.max(currentBlock.parent.maxNumWidth, (int) Math.ceil(listTextNumPaint.measureText("00.")));
                }
                if (currentBlock.isCheckbox) {
                    if (checkbox == null) {
                        checkbox = new CheckBoxBase(this, 20, parent.getResourcesProvider());
                        checkbox.setColor(Theme.key_telegram_color, Theme.key_dialogCheckboxSquareDisabled, Theme.key_checkboxCheck);
                        checkbox.setBackgroundType(10);
                        checkbox.setDrawUnchecked(true);
                        checkbox.setCustomRadius(dp(5));
                    }
                    checkbox.setChecked(currentBlock.checked, false);
                } else {
                    checkbox = null;
                }
                if (adapter != null && adapter.isRtl) {
                    textX = dp(parent.padx() + (checkbox != null ? 26 : 0));
                } else {
                    textX = dp(parent.padx() + (checkbox != null ? 26 : 0) + 6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * dp(20);
                }
                verticalAlign = false;
                int maxWidth = width - dp(parent.padx()) - textX;
                if (adapter != null && adapter.isRtl) {
                    maxWidth -= dp(6) + currentBlock.parent.maxNumWidth + currentBlock.parent.level * dp(20);
                }
                if (currentBlock.textItem != null) {
                    textLayout = createLayoutForText(parent, this, null, currentBlock.textItem, maxWidth, textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                    if (textLayout != null && textLayout.getLineCount() > 0) {
                        if (currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            int ascent = textLayout.getLineAscent(0);
                            numOffsetY = currentBlock.numLayout.getLineAscent(0) - ascent;
                        }
                        height += textLayout.getHeight() + dp(8);
                    }
                } else if (currentBlock.blockItem != null) {
                    blockX = textX;
                    blockY = textY;
                    if (blockLayout != null) {
                        if (blockLayout.itemView instanceof BlockParagraphCell) {
                            blockY -= dp(8);
                            if (!(adapter != null && adapter.isRtl)) {
                                blockX -= dp(parent.padx());
                            }
                            maxWidth += dp(18);
                            height -= dp(8);
                        } else if (blockLayout.itemView instanceof BlockHeaderCell ||
                                blockLayout.itemView instanceof BlockSubheaderCell ||
                                blockLayout.itemView instanceof BlockTitleCell ||
                                blockLayout.itemView instanceof BlockSubtitleCell) {
                            if (!(adapter != null && adapter.isRtl)) {
                                blockX -= dp(parent.padx());
                            }
                            maxWidth += dp(parent.padx());
                        } else if (isListItemBlock(currentBlock.blockItem)) {
                            blockX = 0;
                            blockY = 0;
                            textY = 0;
                            maxWidth = width;
                            height -= dp(8);
                        } else if (blockLayout.itemView instanceof BlockTableCell) {
                            blockX -= dp(parent.padx());
                            maxWidth += dp(36);
                        }
                        blockLayout.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        if (blockLayout.itemView instanceof BlockParagraphCell && currentBlock.numLayout != null && currentBlock.numLayout.getLineCount() > 0) {
                            BlockParagraphCell paragraphCell = (BlockParagraphCell) blockLayout.itemView;
                            if (paragraphCell.textLayout != null && paragraphCell.textLayout.getLineCount() > 0) {
                                int ascent = paragraphCell.textLayout.getLineAscent(0);
                                numOffsetY = currentBlock.numLayout.getLineAscent(0) - ascent;
                            }
                        }
                        if (currentBlock.blockItem instanceof TL_iv.pageBlockDetails) {
                            verticalAlign = true;
                            blockY = 0;
                            height -= dp(8);
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
                    height += dp(8);
                }
                if (currentBlock.parent.items.get(currentBlock.parent.items.size() - 1) == currentBlock) {
                    height += dp(8);
                }
                if (currentBlock.index == 0 && currentBlock.parent.level == 0) {
                    height += dp(10);
                }
                if (textLayout != null) {
                    textLayout.x = textX;
                    textLayout.y = textY;
                    if (currentBlock.numLayout != null) {
                        textLayout.prefix = currentBlock.numLayout.textLayout.getText();
                    }
                }
                if (blockLayout != null && blockLayout.itemView instanceof TextSelectionHelper.ArticleSelectableView) {
                    final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(null);
                    if (textSelectionHelper != null) {
                        textSelectionHelper.arrayList.clear();
                        ((TextSelectionHelper.ArticleSelectableView) blockLayout.itemView).fillTextLayoutBlocks(textSelectionHelper.arrayList);
                        for (TextSelectionHelper.TextLayoutBlock block : textSelectionHelper.arrayList) {
                            if (block instanceof DrawingText) {
                                ((DrawingText) block).x += blockX;
                                ((DrawingText) block).y += blockY;
                            }
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
                if (adapter != null && adapter.isRtl) {
                    canvas.translate(width - dp(parent.padx()) - currentBlock.parent.maxNumWidth - currentBlock.parent.level * dp(20), textY + numOffsetY);
                } else {
                    canvas.translate(dp(parent.padx()) + currentBlock.parent.maxNumWidth - (int) Math.ceil(currentBlock.numLayout.getLineWidth(0)) + currentBlock.parent.level * dp(20), textY + numOffsetY);
                }
                currentBlock.numLayout.draw(canvas, this);
                canvas.restore();
            }
            if (checkbox != null) {
                checkbox.setBounds(textX - dp(26), textY, dp(20), dp(20));
                checkbox.draw(canvas);
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
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
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(buildAccessibilityText(parent, adapter, textLayout));
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockDetailsCell extends View implements Drawable.Callback, TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX, textY;
        private AnimatedArrowDrawable arrow;

        private TL_iv.pageBlockDetails currentBlock;

        public BlockDetailsCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            arrow = new AnimatedArrowDrawable(parent.getGrayTextColor(), true);
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

        public void setBlock(TL_iv.pageBlockDetails block) {
            currentBlock = block;
            arrow.setAnimationProgress(block.open ? 0.0f : 1.0f);
            arrow.setCallback(this);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int h = dp(39);

            textX = dp(44 + 6);
            textY = dp(11) + 1;

            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.title, width - dp(3 * parent.padx()), 0, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    h = Math.max(h, dp(21) + textLayout.getHeight());
                    textY = (textLayout.getHeight() + dp(21) - textLayout.getHeight()) / 2;
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
            canvas.translate(dp(parent.padx()), (getMeasuredHeight() - dp(13) - 1) / 2);
            arrow.draw(canvas);
            canvas.restore();

            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (textLayout != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout);
                if (t != null) sb.append(t).append(", ");
            }
            sb.append(getString(R.string.AccDescrIVDetails)).append(", ");
            sb.append(getString(currentBlock != null && currentBlock.open ? R.string.AccDescrIVExpanded : R.string.AccDescrIVCollapsed));
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockDetailsBottomCell extends View {

        private final RectF rect = new RectF();

        public BlockDetailsBottomCell(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1 + dp(4));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(0, 0, getMeasuredWidth(), 0, dividerPaint);
        }
    }

    public static class BlockRelatedArticlesShadowCell extends View {

        private final IArticleViewer parent;
        private CombinedDrawable shadowDrawable;

        public BlockRelatedArticlesShadowCell(Context context, IArticleViewer parent) {
            super(context);
            this.parent = parent;

            Drawable drawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, 0xff000000);
            shadowDrawable = new CombinedDrawable(new ColorDrawable(parent.getThemedColor(Theme.key_iv_backgroundGray)), drawable);
            shadowDrawable.setFullsize(true);
            setBackgroundDrawable(shadowDrawable);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(12));
            Theme.setCombinedDrawableColor(shadowDrawable, parent.getThemedColor(Theme.key_iv_backgroundGray), false);
        }
    }

    public static class BlockRelatedArticlesHeaderCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX = dp(18);
        private int textY;

        private TL_iv.pageBlockRelatedArticles currentBlock;

        public BlockRelatedArticlesHeaderCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockRelatedArticles block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.title, width - dp(36 + 16), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, adapter);
                if (textLayout != null) {
                    textY = dp(6) + (dp(32) - textLayout.getHeight()) / 2;
                }
            }
            if (textLayout != null) {
                setMeasuredDimension(width, dp(38));
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            if (textLayout == null) return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVRelatedArticles));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockRelatedArticlesCell extends View implements TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private boolean divider;
        private boolean drawImage;

        private ImageReceiver imageView;

        private TL_pageBlockRelatedArticlesChild currentBlock;
        private TLObject currentPage;

        private int textX = dp(18);
        private int textY = dp(10);
        private int textOffset;

        public BlockRelatedArticlesCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            imageView = new ImageReceiver(this);
            imageView.setRoundRadius(dp(6));
        }

        public void setBlock(TL_pageBlockRelatedArticlesChild block, TLObject page) {
            currentBlock = block;
            currentPage = page;
            requestLayout();
        }

        @SuppressLint({"DrawAllocation", "NewApi"})
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);

            divider = currentBlock.num != currentBlock.parent.articles.size() - 1;
            TL_iv.pageRelatedArticle item = currentBlock.parent.articles.get(currentBlock.num);

            int additionalHeight = dp(SharedConfig.ivFontSize - 16);

            TLRPC.Photo photo = item.photo_id != 0 ? adapter != null ? adapter.getPhotoWithId(item.photo_id) : WebPageUtils.getPhotoWithId(currentPage, item.photo_id) : null;
            if (photo != null) {
                drawImage = true;
                TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80, true);
                if (image == thumb) {
                    thumb = null;
                }
                imageView.setImage(ImageLocation.getForPhoto(image, photo), "64_64", ImageLocation.getForPhoto(thumb, photo), "64_64_b", image.size, null, currentPage, 1);
            } else {
                drawImage = false;
            }

            int layoutHeight = dp(16 + 44);
            int availableWidth = width - dp(18 + 18);
            if (drawImage) {
                int imageWidth = dp(44);
                imageView.setImageCoords(width - imageWidth - dp(8), dp(8), imageWidth, imageWidth);
                availableWidth -= imageView.getImageWidth() + dp(6);
            }

            int height = dp(18);

            boolean isTitleRtl = false;
            if (item.title != null) {
                textLayout = createLayoutForText(parent, this, item.title, null, availableWidth, textY, currentBlock, Layout.Alignment.ALIGN_NORMAL, 3, adapter);
            }
            int lineCount = 4;
            if (textLayout != null) {
                int count = textLayout.getLineCount();
                lineCount -= count;
                textOffset = textLayout.getHeight() + dp(6) + additionalHeight;
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
                description = LocaleController.formatString(R.string.ArticleDateByAuthor, LocaleController.getInstance().getChatFullDate().format((long) item.published_date * 1000), item.author);
            } else if (!TextUtils.isEmpty(item.author)) {
                description = LocaleController.formatString(R.string.ArticleByAuthor, item.author);
            } else if (item.published_date != 0) {
                description = LocaleController.getInstance().getChatFullDate().format((long) item.published_date * 1000);
            } else if (!TextUtils.isEmpty(item.description)) {
                description = item.description;
            } else {
                description = item.url;
            }
            textLayout2 = createLayoutForText(parent, this, description, null, availableWidth, textY + textOffset, currentBlock, adapter != null && adapter.isRtl || isTitleRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, lineCount, adapter);
            if (textLayout2 != null) {
                height += textLayout2.getHeight();
                if (textLayout != null) {
                    height += dp(6) + additionalHeight;
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
            canvas.translate(textX, dp(10));
            if (textLayout != null) {
                drawTextSelection(parent, canvas, this, count++);
                textLayout.draw(canvas, this);
            }
            if (textLayout2 != null) {
                canvas.translate(0, textOffset);
                drawTextSelection(parent, canvas, this, count);
                textLayout2.draw(canvas, this);
            }
            canvas.restore();
            if (divider) {
                canvas.drawLine(adapter != null && adapter.isRtl ? 0 : dp(17), getMeasuredHeight() - 1, getMeasuredWidth() - (adapter != null && adapter.isRtl ? dp(17) : 0), getMeasuredHeight() - 1, dividerPaint);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (textLayout != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout);
                if (t != null) sb.append(t);
            }
            if (textLayout2 != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout2);
                if (t != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(t);
                }
            }
            if (sb.length() == 0) return;
            sb.append(", ").append(getString(R.string.AccDescrIVRelatedArticle));
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
            if (textLayout2 != null) textLayout2.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
            if (textLayout2 != null) textLayout2.detach(this);
        }
    }

    public static class BlockHeaderCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX, textY;

        private TL_iv.PageBlock currentBlock;

        public BlockHeaderCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        public void setBlock(TL_iv.PageBlock block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());
            textY = dp(parent.pady());

            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(parent.padx() * 2), textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    height += dp(2 * parent.pady()) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
            } else {
                height = 1;
            }

            setMeasuredDimension(width, height);
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textX + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textX + textLayout.getBoundRight() + dp(parent.padx());
        }

        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textX + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockDividerCell extends View {

        private final RectF rect = new RectF();

        public BlockDividerCell(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(2 + 16));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getMeasuredWidth() / 3;
            rect.set(width, dp(8), width * 2, dp(10));
            canvas.drawRoundRect(rect, dp(1), dp(1), dividerPaint);
        }
    }

    public static class BlockSubtitleCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX, textY;

        private TL_iv.pageBlockSubtitle currentBlock;

        public BlockSubtitleCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockSubtitle block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());
            textY = dp(parent.pady());

            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()), textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    height += dp(2 * parent.pady()) + textLayout.getHeight();
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
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
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockPullquoteCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private int textY2;
        private int textX, textY;

        private TL_iv.pageBlockPullquote currentBlock;

        public BlockPullquoteCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            int left = Integer.MAX_VALUE;
            if (textLayout != null) left = Math.min(left, textLayout.x + textLayout.getBoundLeft());
            if (textLayout2 != null) left = Math.min(left, textLayout2.x + textLayout2.getBoundLeft());
            if (left == Integer.MAX_VALUE) return -1;
            return left - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            int right = Integer.MIN_VALUE;
            if (textLayout != null) right = Math.max(right, textLayout.x + textLayout.getBoundRight());
            if (textLayout2 != null) right = Math.max(right, textLayout2.x + textLayout2.getBoundRight());
            if (right == Integer.MIN_VALUE) return -1;
            return right + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout2 != null) return textLayout2.x + textLayout2.getLastLineBoundRight() + dp(parent.padx());
            if (textLayout != null) return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
            return -1;
        }

        public void setBlock(TL_iv.pageBlockPullquote block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());
            textY = dp(parent.pady());

            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()), textY, currentBlock, adapter);
                if (textLayout != null) {
                    height += dp(parent.pady()) + textLayout.getHeight();
                    textLayout.x = textX;
                    textLayout.y = textY;
                }
                textY2 = height + dp(2);
                textLayout2 = createLayoutForText(parent, this, null, currentBlock.caption, width - dp(2 * parent.padx()), textY2, currentBlock, adapter);
                if (textLayout2 != null) {
                    height += dp(parent.pady()) + textLayout2.getHeight();
                    textLayout2.x = textX;
                    textLayout2.y = textY2;
                }
                if (height != 0) {
                    height += dp(parent.pady());
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
                drawTextSelection(parent, canvas, this, count++);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawTextSelection(parent, canvas, this, count);
                textLayout2.draw(canvas, this);
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (textLayout != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout);
                if (t != null) sb.append(t);
            }
            if (textLayout2 != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout2);
                if (t != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(t);
                }
            }
            if (sb.length() == 0) return;
            sb.append(", ").append(getString(R.string.AccDescrIVPullquote));
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
            if (textLayout2 != null) textLayout2.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
            if (textLayout2 != null) textLayout2.detach(this);
        }
    }

    public static class BlockBlockquoteCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private DrawingText textLayout2;
        private int textY2;
        private int textX;
        private int textY;

        private TL_iv.pageBlockBlockquote currentBlock;

        public BlockBlockquoteCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            int left = Integer.MAX_VALUE;
            final int padx = dp(parent.padx());
            if (textLayout != null) left = Math.min(left, textLayout.x + textLayout.getBoundLeft() - padx);
            if (textLayout2 != null) left = Math.min(left, textLayout2.x + textLayout2.getBoundLeft() - padx);
            if (left == Integer.MAX_VALUE) return -1;
            return left - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            int right = Integer.MIN_VALUE;
            final int padx = dp(parent.padx());
            if (textLayout != null) right = Math.max(right, textLayout.x + textLayout.getBoundRight() + padx);
            if (textLayout2 != null) right = Math.max(right, textLayout2.x + textLayout2.getBoundRight() + padx);
            if (right == Integer.MIN_VALUE) return -1;
            return right + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout2 != null) return textLayout2.x + textLayout2.getLastLineBoundRight() + dp(parent.padx());
            if (textLayout != null) return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
            return -1;
        }

        public void setBlock(TL_iv.pageBlockBlockquote block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textY = dp(parent.pady());

            if (currentBlock != null) {
                int textWidth = width - dp(2 * parent.padx() + 14);
                if (currentBlock.level > 0) {
                    textWidth -= dp(14 * currentBlock.level);
                }
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, textWidth, textY, currentBlock, adapter);
                if (textLayout != null) {
                    height += dp(parent.pady()) + textLayout.getHeight();
                }
                if (currentBlock.level > 0) {
                    if (adapter != null && adapter.isRtl) {
                        textX = dp(14 + currentBlock.level * 14);
                    } else {
                        textX = dp(14 * currentBlock.level) + dp(parent.padx() + 14);
                    }
                } else {
                    if (adapter != null && adapter.isRtl) {
                        textX = dp(14);
                    } else {
                        textX = dp(parent.padx() + 14);
                    }
                }
                textY2 = height + dp(parent.pady());
                textLayout2 = createLayoutForText(parent, this, null, currentBlock.caption, textWidth, textY2, currentBlock, adapter);
                if (textLayout2 != null) {
                    height += dp(parent.pady()) + textLayout2.getHeight();
                }
                if (height != 0) {
                    height += dp(parent.pady());
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
                drawTextSelection(parent, canvas, this, counter++);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawTextSelection(parent, canvas, this, counter);
                textLayout2.draw(canvas, this);
                canvas.restore();
            }
            if (adapter != null && adapter.isRtl) {
                int x = getMeasuredWidth() - dp(20);
                canvas.drawRect(x, dp(6), x + dp(2), getMeasuredHeight() - dp(6), quoteLinePaint);
            } else {
                canvas.drawRect(dp(parent.padx() + currentBlock.level * 14), dp(6), dp(parent.padx() + 2 + currentBlock.level * 14), getMeasuredHeight() - dp(6), quoteLinePaint);
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (textLayout != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout);
                if (t != null) sb.append(t);
            }
            if (textLayout2 != null) {
                CharSequence t = buildAccessibilityText(parent, adapter, textLayout2);
                if (t != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(t);
                }
            }
            if (sb.length() == 0) return;
            sb.append(", ").append(getString(R.string.AccDescrIVBlockquote));
            info.setText(sb);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
            if (textLayout2 != null) textLayout2.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
            if (textLayout2 != null) textLayout2.detach(this);
        }
    }

    public static class BlockPhotoCell extends FrameLayout implements DownloadController.FileDownloadProgressListener, TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

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

        private TL_iv.pageBlockPhoto currentBlock;
        private TLObject currentPage;
        private Object parentObject;
        private TL_iv.PageBlock parentBlock;
        private boolean calcHeight;

        private MessageObject.GroupedMessagePosition groupPosition;
        private Drawable linkDrawable;

        boolean autoDownload;

        public BlockPhotoCell(Context context, IArticleViewer parent, WebpageAdapter adapter, int type) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            channelCell = new BlockChannelCell(context, parent, adapter, 1);
            radialProgress = new RadialProgress2(this);
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
            TAG = DownloadController.getInstance(parent.getCurrentAccount()).generateObserverTag();
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            currentType = type;
        }

        public void setBlock(TL_iv.pageBlockPhoto block, TLObject page, Object parentObject, boolean calcHeight, boolean first) {
            parentBlock = null;
            currentBlock = block;
            currentPage = page;
            this.parentObject = parentObject;
            this.calcHeight = calcHeight;
            isFirst = first;
            channelCell.setVisibility(INVISIBLE);
            if (!TextUtils.isEmpty(currentBlock.url)) {
                linkDrawable = getResources().getDrawable(R.drawable.msg_instant_link);
            }
            if (currentBlock != null) {
                final TLRPC.Photo photo = WebPageUtils.getPhotoWithId(currentPage, currentBlock.photo_id);
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

        public void setParentBlock(TL_iv.PageBlock block) {
            parentBlock = block;
            if (adapter != null && adapter.channelBlock != null && parentBlock instanceof TL_iv.pageBlockCover) {
                channelCell.setBlock(adapter.channelBlock);
                channelCell.setVisibility(VISIBLE);
            }
        }

        public View getChannelCell() {
            return channelCell;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // TODO
//            if (pinchToZoomHelper.checkPinchToZoom(event, this, imageView, null, null, null)) {
//                return true;
//            }
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + dp(39)) {
                if (adapter != null && adapter.channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    // TODO
//                    MessagesController.getInstance(parent.getCurrentAccount()).openByUserName(ChatObject.getPublicUsername(adapter.channelBlock.channel), parentFragment, 2);
//                    close(false, true);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + dp(48) && y >= buttonY && y <= buttonY + dp(48) || buttonState == 0) {
                    buttonPressed = 1;
                    invalidate();
                } else {
                    photoPressed = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (photoPressed) {
                    photoPressed = false;
                    parent.openPhoto(currentBlock, adapter);
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
            return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parent, adapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
        }

        public ImageReceiver getImageView() {
            return imageView;
        }

        public TL_iv.pageBlockPhoto getCurrentBlock() {
            return currentBlock;
        }

        public TLObject getCurrentPage() {
            return currentPage;
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
                currentPhoto = WebPageUtils.getPhotoWithId(currentPage, currentBlock.photo_id);
                int size = dp(48);
                int photoWidth = width;
                int photoHeight = height;
                int photoX;
                int textWidth;
                if (currentType == 0 && currentBlock.level > 0) {
                    textX = photoX = dp(14 * currentBlock.level) + dp(18);
                    photoWidth -= photoX + dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = dp(18);
                    textWidth = width - dp(36);
                }
                if (currentPhoto != null && (currentPhotoObject != null || currentPhoto instanceof WebInstantView.WebPhoto)) {
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(currentPhoto.sizes, 40, true);
                    if (currentPhotoObject == currentPhotoObjectThumb) {
                        currentPhotoObjectThumb = null;
                    }
                    int w, h;
                    if (currentPhoto instanceof WebInstantView.WebPhoto) {
                        WebInstantView.WebPhoto webphoto = (WebInstantView.WebPhoto) currentPhoto;
                        w = webphoto.w;
                        h = webphoto.h;
                    } else {
                        w = currentPhotoObject.w;
                        h = currentPhotoObject.h;
                    }
                    if (currentType == 0) {
                        float scale;
                        scale = photoWidth / (float) w;
                        height = (int) (scale * h);
                        if (parentBlock instanceof TL_iv.pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / (float) h;
                                photoWidth = (int) (scale * w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                        photoHeight = height;
                    } else if (currentType == 2) {
                        if ((groupPosition.flags & POSITION_FLAG_RIGHT) == 0) {
                            photoWidth -= dp(2);
                        }
                        if ((groupPosition.flags & POSITION_FLAG_BOTTOM) == 0) {
                            photoHeight -= dp(2);
                        }
                        if (groupPosition.leftSpanOffset != 0) {
                            int offset = (int) Math.ceil(width * groupPosition.leftSpanOffset / 1000.0f);
                            photoWidth -= offset;
                            photoX += offset;
                        }
                    }
                    imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : dp(8), photoWidth, photoHeight);
                    if (currentType == 0) {
                        currentFilter = null;
                    } else {
                        currentFilter = String.format(Locale.US, "%d_%d", photoWidth, photoHeight);
                    }
                    currentThumbFilter = "80_80_b";

                    autoDownload = (DownloadController.getInstance(parent.getCurrentAccount()).getCurrentDownloadMask() & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0;
                    if (calcHeight) {

                    } else if (currentPhoto instanceof WebInstantView.WebPhoto) {
                        autoDownload = true;
                        imageView.setStrippedLocation(null);
                        WebInstantView.loadPhoto((WebInstantView.WebPhoto) currentPhoto, imageView, () -> {
                            requestLayout();
                        });
                    } else {
                        final File path = FileLoader.getInstance(parent.getCurrentAccount()).getPathToAttach(currentPhotoObject, true);
                        if (autoDownload || path.exists()) {
                            imageView.setStrippedLocation(null);
                            imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, adapter != null ? adapter.currentPage : parentObject, 1);
                        } else {
                            imageView.setStrippedLocation(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto));
                            imageView.setImage(null, currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, adapter != null ? adapter.currentPage : parentObject, 1);
                        }
                    }
                    buttonX = (int) (imageView.getImageX() + (imageView.getImageWidth() - size) / 2.0f);
                    buttonY = (int) (imageView.getImageY() + (imageView.getImageHeight() - size) / 2.0f);
                    radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + dp(8));

                if (currentType == 0) {
                    captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                    if (captionLayout != null) {
                        creditOffset = dp(4) + captionLayout.getHeight();
                        height += creditOffset + dp(4);
                    }
                    creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, 0, adapter);
                    if (creditLayout != null) {
                        height += dp(4) + creditLayout.getHeight();
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += dp(8);
                }
                // TODO
                boolean nextIsChannel = parentBlock instanceof TL_iv.pageBlockCover && adapter != null && adapter.blocks != null && adapter.blocks.size() > 1 && adapter.blocks.get(1) instanceof TL_iv.pageBlockChannel;
                if (currentType != 2 && !nextIsChannel) {
                    height += dp(8);
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
            channelCell.setTranslationY(imageView.getImageHeight() - dp(39));

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
            // TODO
//            if (!pinchToZoomHelper.isInOverlayModeFor(this)) {
                imageView.draw(canvas);
                if (imageView.getVisible()) {
                    radialProgress.draw(canvas);
                }
//            }
            if (!TextUtils.isEmpty(currentBlock.url) && !(currentPhoto instanceof WebInstantView.WebPhoto)) {
                int x = getMeasuredWidth() - dp(11 + 24);
                int y = (int) (imageView.getImageY() + dp(11));
                linkDrawable.setBounds(x, y, x + dp(24), y + dp(24));
                linkDrawable.draw(canvas);
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this, count++);
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(parent, canvas, this, count);
                creditLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
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
            if (currentPhotoObject == null) return;
            if (buttonState == 0) {
                radialProgress.setProgress(0, animated);
                // TODO
                imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject.size, null, currentPage, 1);
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
            final int currentAccount = parent.getCurrentAccount();
            final String fileName = FileLoader.getAttachFileName(currentPhotoObject);
            final File path = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject, true);
            final File path2 = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject, false);
            boolean fileExists = path.exists() || path2 != null && path2.exists();
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
            DownloadController.getInstance(parent.getCurrentAccount()).removeLoadingFileObserver(this);
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            imageView.onAttachedToWindow();
            updateButtonState(false);
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
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
            StringBuilder sb = new StringBuilder(LocaleController.getString(R.string.AttachPhoto));
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

    public static class BlockMapCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private Drawable chat_redLocationIcon;

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

        private TL_iv.pageBlockMap currentBlock;

        public BlockMapCell(Context context, IArticleViewer parent, WebpageAdapter adapter, int type) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            currentType = type;
        }

        public void setBlock(TL_iv.pageBlockMap block, boolean first, boolean last) {
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
                    getContext().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || checkLayoutForLinks(parent, adapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parent, adapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event);
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
                    textX = photoX = dp(14 * currentBlock.level) + dp(18);
                    photoWidth -= photoX + dp(18);
                    textWidth = photoWidth;
                } else {
                    photoX = 0;
                    textX = dp(18);
                    textWidth = width - dp(36);
                }

                if (currentType == 0) {
                    float scale;
                    scale = photoWidth / (float) currentBlock.w;
                    height = (int) (scale * currentBlock.h);

                    int maxHeight = (int) ((Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(56)) * 0.9f);
                    if (height > maxHeight) {
                        height = maxHeight;
                        scale = height / (float) currentBlock.h;
                        photoWidth = (int) (scale * currentBlock.w);
                        photoX += (width - photoX - photoWidth) / 2;
                    }
                }
                imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : dp(8), photoWidth, height);

                final int currentAccount = parent.getCurrentAccount();
                String currentUrl = AndroidUtilities.formapMapUrl(currentAccount, currentBlock.geo.lat, currentBlock.geo._long, (int) (photoWidth / AndroidUtilities.density), (int) (height / AndroidUtilities.density), true, 15, -1);
                WebFile currentWebFile = WebFile.createWithGeoPoint(currentBlock.geo, (int) (photoWidth / AndroidUtilities.density), (int) (height / AndroidUtilities.density), 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));

                currentMapProvider = MessagesController.getInstance(currentAccount).mapProvider;
                if (currentMapProvider == 2) {
                    if (currentWebFile != null) {
                        imageView.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, adapter != null ? adapter.currentPage : null, 0);
                    }
                } else if (currentUrl != null) {
                    imageView.setImage(currentUrl, null, null, null, 0);
                }
                textY = (int) (imageView.getImageY() + imageView.getImageHeight() + dp(8));
                if (currentType == 0) {
                    captionLayout = createLayoutForText(parent, this, null, currentBlock.caption.text, textWidth, textY, currentBlock, adapter);
                    if (captionLayout != null) {
                        creditOffset = dp(4) + captionLayout.getHeight();
                        height += creditOffset + dp(4);
                        captionLayout.x = textX;
                        captionLayout.y = textY;
                    }
                    creditLayout = createLayoutForText(parent, this, null, currentBlock.caption.credit, textWidth, textY + creditOffset, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                    if (creditLayout != null) {
                        height += dp(4) + creditLayout.getHeight();
                        creditLayout.x = textX;
                        creditLayout.y = textY + creditOffset;
                    }
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += dp(8);
                }
                if (currentType != 2) {
                    height += dp(8);
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

            Theme.chat_docBackPaint.setColor(parent.getThemedColor(Theme.key_chat_inLocationBackground));
            canvas.drawRect(imageView.getImageX(), imageView.getImageY(), imageView.getImageX2(), imageView.getImageY2(), Theme.chat_docBackPaint);
            int left = (int) (imageView.getCenterX() - Theme.chat_locationDrawable[0].getIntrinsicWidth() / 2);
            int top = (int) (imageView.getCenterY() - Theme.chat_locationDrawable[0].getIntrinsicHeight() / 2);
            Theme.chat_locationDrawable[0].setBounds(left, top, left + Theme.chat_locationDrawable[0].getIntrinsicWidth(), top + Theme.chat_locationDrawable[0].getIntrinsicHeight());
            Theme.chat_locationDrawable[0].draw(canvas);

            imageView.draw(canvas);
            if (currentMapProvider == 2 && imageView.hasNotThumb()) {
                if (chat_redLocationIcon == null) {
                    chat_redLocationIcon = ContextCompat.getDrawable(getContext(), R.drawable.map_pin).mutate();
                }
                int w = (int) (chat_redLocationIcon.getIntrinsicWidth() * 0.8f);
                int h = (int) (chat_redLocationIcon.getIntrinsicHeight() * 0.8f);
                int x = (int) (imageView.getImageX() + (imageView.getImageWidth() - w) / 2);
                int y = (int) (imageView.getImageY() + (imageView.getImageHeight() / 2 - h));
                chat_redLocationIcon.setAlpha((int) (255 * imageView.getCurrentAlpha()));
                chat_redLocationIcon.setBounds(x, y, x + w, y + h);
                chat_redLocationIcon.draw(canvas);
            }
            int count = 0;
            if (captionLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawTextSelection(parent, canvas, this, count++);
                captionLayout.draw(canvas, this);
                canvas.restore();
            }
            if (creditLayout != null) {
                canvas.save();
                canvas.translate(textX, textY + creditOffset);
                drawTextSelection(parent, canvas, this, count);
                creditLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            StringBuilder sb = new StringBuilder(LocaleController.getString(R.string.Map));
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (captionLayout != null) captionLayout.attach(this);
            if (creditLayout != null) creditLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (captionLayout != null) captionLayout.detach(this);
            if (creditLayout != null) creditLayout.detach(this);
        }
    }

    public static class BlockChannelCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private ContextProgressView progressView;
        private TextView textView;
        private ImageView imageView;
        private int currentState;

        private DrawingText textLayout;
        private int buttonWidth;
        private int textX = dp(18);
        private int textY = dp(11);
        private int textX2;
        private Paint backgroundPaint;
        private AnimatorSet currentAnimation;
        private int currentType;

        private TL_iv.pageBlockChannel currentBlock;

        public BlockChannelCell(Context context, IArticleViewer parent, WebpageAdapter adapter, int type) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
            setWillNotDraw(false);
            backgroundPaint = new Paint();
            currentType = type;

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setText(LocaleController.getString(R.string.ChannelJoin));
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 39, Gravity.RIGHT | Gravity.TOP));
            textView.setOnClickListener(v -> {
                if (currentState != 0) {
                    return;
                }
                setState(1, true);
                joinChannel(parent.getCurrentAccount(), BlockChannelCell.this, parent.loadedChannel);
            });

            imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.list_check);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(39, 39, Gravity.RIGHT | Gravity.TOP));

            progressView = new ContextProgressView(context, 0);
            addView(progressView, LayoutHelper.createFrame(39, 39, Gravity.RIGHT | Gravity.TOP));
        }

        public void setBlock(TL_iv.pageBlockChannel block) {
            currentBlock = block;

            if (currentType == 0) {
                int color = parent.getThemedColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                textView.setTextColor(parent.getLinkTextColor());
                backgroundPaint.setColor(Color.argb(34, r, g, b));
                imageView.setColorFilter(new PorterDuffColorFilter(parent.getGrayTextColor(), PorterDuff.Mode.MULTIPLY));
            } else {
                textView.setTextColor(0xffffffff);
                backgroundPaint.setColor(0x7f000000);
                imageView.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
            }
            final TLRPC.Chat channel = MessagesController.getInstance(parent.getCurrentAccount()).getChat(block.channel.id);
            if (channel == null || channel.min) {
                loadChannel(parent, this, adapter, block.channel);
                setState(1, false);
            } else {
                parent.loadedChannel = channel;
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
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, dp(39 + 9));

            textView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(39), MeasureSpec.EXACTLY));
            buttonWidth = textView.getMeasuredWidth();
            progressView.measure(MeasureSpec.makeMeasureSpec(dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(39), MeasureSpec.EXACTLY));
            imageView.measure(MeasureSpec.makeMeasureSpec(dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(39), MeasureSpec.EXACTLY));
            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, currentBlock.channel.title, null, width - dp(36 + 16) - buttonWidth, textY, currentBlock, StaticLayoutEx.ALIGN_LEFT(), 1, adapter);
                if (adapter != null && adapter.isRtl) {
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
            imageView.layout(textX2 + buttonWidth / 2 - dp(19), 0, textX2 + buttonWidth / 2 + dp(20), dp(39));
            progressView.layout(textX2 + buttonWidth / 2 - dp(19), 0, textX2 + buttonWidth / 2 + dp(20), dp(39));
            textView.layout(textX2, 0, textX2 + textView.getMeasuredWidth(), textView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            canvas.drawRect(0, 0, getMeasuredWidth(), dp(39), backgroundPaint);
            if (textLayout != null && textLayout.getLineCount() > 0) {
                canvas.save();
                if (adapter != null && adapter.isRtl) {
                    canvas.translate(getMeasuredWidth() - textLayout.getLineWidth(0) - textX, textY);
                } else {
                    canvas.translate(textX, textY);
                }
                if (currentType == 0) {
                    drawTextSelection(parent, canvas, this);
                }
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (textLayout == null) return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrChannel));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockAuthorDateCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX;
        private int textY = dp(8);

        private TL_iv.pageBlockAuthorDate currentBlock;

        public BlockAuthorDateCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockAuthorDate block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                CharSequence text;
                CharSequence author = getText(parent, adapter, this, currentBlock.author, currentBlock.author, currentBlock, width);
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
                    text = LocaleController.formatString(R.string.ArticleDateByAuthor, LocaleController.getInstance().getChatFullDate().format((long) currentBlock.published_date * 1000), author);
                } else if (!TextUtils.isEmpty(author)) {
                    text = LocaleController.formatString(R.string.ArticleByAuthor, author);
                } else {
                    text = LocaleController.getInstance().getChatFullDate().format((long) currentBlock.published_date * 1000);
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
                textLayout = createLayoutForText(parent, this, text, null, width - dp(36), textY, currentBlock, adapter);
                if (textLayout != null) {
                    height += dp(8 + 8) + textLayout.getHeight();
                    if (adapter != null && adapter.isRtl) {
                        textX = (int) Math.floor(width - textLayout.getLineLeft(0) - textLayout.getLineWidth(0) - dp(16));
                    } else {
                        textX = dp(18);
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            if (textLayout == null)
                return;
            info.setText(buildAccessibilityText(parent, adapter, textLayout));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockTitleCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private DrawingText textLayout;

        private TL_iv.pageBlockTitle currentBlock;
        private int textX, textY;

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        public BlockTitleCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockTitle block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());
            if (currentBlock != null) {
                if (currentBlock.first) {
                    height += dp(parent.pady());
                    textY = dp(2 * parent.pady());
                } else {
                    textY = dp(parent.pady());
                }
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()), textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    height += dp(2 * parent.pady()) + textLayout.getHeight();
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            if (textLayout == null)
                return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVTitle));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockKickerCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;

        private TL_iv.pageBlockKicker currentBlock;
        private int textX, textY;

        public BlockKickerCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockKicker block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());

            if (currentBlock != null) {
                if (currentBlock.first) {
                    textY = dp(16);
                    height += dp(8);
                } else {
                    textY = dp(8);
                }
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()), textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, 0, adapter);
                if (textLayout != null) {
                    height += dp(2 * parent.pady()) + textLayout.getHeight();
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            if (textLayout == null) return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVKicker));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockFooterCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX, textY;

        private TL_iv.pageBlockFooter currentBlock;

        public BlockFooterCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockFooter block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.level == 0) {
                    textY = dp(parent.pady());
                    textX = dp(parent.padx());
                } else {
                    textY = 0;
                    textX = dp(parent.padx() + 14 * currentBlock.level);
                }
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()) - textX, textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    height = textLayout.getHeight();
                    if (currentBlock.level > 0) {
                        height += dp(parent.pady());
                    } else {
                        height += dp(2 * parent.pady());
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
                canvas.restore();
            }
            drawQuoteLines(canvas, parent, currentBlock, getMeasuredHeight());
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            if (textLayout == null) return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVFooter));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockPreformattedCell extends FrameLayout implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private HorizontalScrollView scrollView;
        private View textContainer;

        private TL_iv.pageBlockPreformatted currentBlock;
        private CharSequence text;

        public BlockPreformattedCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            scrollView = new HorizontalScrollView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
//                    if (textContainer.getMeasuredWidth() > getMeasuredWidth()) {
//                        windowView.requestDisallowInterceptTouchEvent(true);
//                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                    super.onScrollChanged(l, t, oldl, oldt);
                    if (parent.pressedLinkOwnerLayout != null) {
                        parent.pressedLinkOwnerLayout = null;
                        parent.pressedLinkOwnerView = null;
                    }
                }
            };
            scrollView.setPadding(0, dp(8), 0, dp(8));
            addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            textContainer = new View(context) {

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int height = 0;
                    int width = 1;
                    if (currentBlock != null) {
                        if (text == null) {
                            text = getText(parent, adapter, this, currentBlock.text, currentBlock.text, currentBlock, dp(5000));
                            if (!TextUtils.isEmpty(currentBlock.language)) {
                                text = CodeHighlighting.getHighlighted(text, currentBlock.language);
                            }
                        }
                        textLayout = createLayoutForText(parent, this, text, null, dp(5000), 0, currentBlock, adapter);
                        if (textLayout != null) {
                            height += textLayout.getHeight();
                            for (int a = 0, count = textLayout.getLineCount(); a < count; a++) {
                                width = Math.max((int) Math.ceil(textLayout.getLineWidth(a)), width);
                            }
                        }
                    } else {
                        height = 1;
                    }
                    setMeasuredDimension(width + dp(32), height);
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return checkLayoutForLinks(parent, adapter, event, BlockPreformattedCell.this, textLayout, 0, 0) || super.onTouchEvent(event);
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    if (textLayout != null) {
                        canvas.save();
                        drawTextSelection(parent, canvas, BlockPreformattedCell.this);
                        textLayout.draw(canvas, this);
                        canvas.restore();
                        textLayout.x = (int) getX();
                        textLayout.y = (int) getY();
                    }
                }
            };
            HorizontalScrollView.LayoutParams layoutParams = new HorizontalScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT);
            layoutParams.leftMargin = layoutParams.rightMargin = dp(16);
            layoutParams.topMargin = layoutParams.bottomMargin = dp(12);
            NotificationCenter.listenEmojiLoading(textContainer);
            scrollView.addView(textContainer, layoutParams);

            if (Build.VERSION.SDK_INT >= 23) {
                scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper(null);
                    if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.invalidate();
                    }
                });
            }

            setWillNotDraw(false);
        }

        public void setBlock(TL_iv.pageBlockPreformatted block) {
            text = null;
            currentBlock = block;
            scrollView.setScrollX(0);
            textContainer.requestLayout();
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return dp(16) + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return dp(16) + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return dp(16) + textLayout.getLastLineBoundRight() + dp(parent.padx());
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
            canvas.drawRect(0, dp(8), getMeasuredWidth(), getMeasuredHeight() - dp(8), preformattedBackgroundPaint);
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.TextView");
            info.setEnabled(true);
            info.setClickable(false);
            info.setLongClickable(false);
            if (textLayout == null) return;
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVCode));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }

        @Override
        public void invalidate() {
            textContainer.invalidate();
            super.invalidate();
        }
    }

    public static class BlockSubheaderCell extends View implements TextSelectionHelper.ArticleSelectableView, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private DrawingText textLayout;
        private int textX, textY;

        private TL_iv.pageBlockSubheader currentBlock;

        public BlockSubheaderCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;
        }

        @Override
        public int getBoundLeft() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundLeft() - dp(parent.padx());
        }

        @Override
        public int getBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getBoundRight() + dp(parent.padx());
        }

        @Override
        public int getLastLineBoundRight() {
            if (textLayout == null) return -1;
            return textLayout.x + textLayout.getLastLineBoundRight() + dp(parent.padx());
        }

        public void setBlock(TL_iv.pageBlockSubheader block) {
            currentBlock = block;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(parent, adapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("NewApi")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            textX = dp(parent.padx());
            textY = dp(parent.pady());

            if (currentBlock != null) {
                textLayout = createLayoutForText(parent, this, null, currentBlock.text, width - dp(2 * parent.padx()), textY, currentBlock, adapter != null && adapter.isRtl ? StaticLayoutEx.ALIGN_RIGHT() : Layout.Alignment.ALIGN_NORMAL, adapter);
                if (textLayout != null) {
                    height += textY + textLayout.getHeight() + textY;
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
                drawTextSelection(parent, canvas, this);
                textLayout.draw(canvas, this);
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
            info.setText(appendA11yLabel(buildAccessibilityText(parent, adapter, textLayout), R.string.AccDescrIVHeading));
        }

        @Override
        public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
            if (textLayout != null) {
                blocks.add(textLayout);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (textLayout != null) textLayout.attach(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (textLayout != null) textLayout.detach(this);
        }
    }

    public static class BlockMathCell extends HorizontalScrollView implements Theme.Colorable, IBlock {

        private final IArticleViewer parent;
        private final WebpageAdapter adapter;

        private FrameLayout layout;
        private ImageView imageView;

        private TL_iv.pageBlockMath currentBlock;

        public BlockMathCell(Context context, IArticleViewer parent, WebpageAdapter adapter) {
            super(context);
            this.parent = parent;
            this.adapter = adapter;

            layout = new FrameLayout(context);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            layout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            updateColors();
        }

        private int width;
        public void setBlock(TL_iv.pageBlockMath block) {
            currentBlock = block;
            imageView.setImageBitmap(null);
            imageView.setPadding(dp(parent.padx()), 0, dp(parent.padx()), 0);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(0, 0));
            width = dp(2 * parent.padx());
            if (block != null) {
                try {
                    final JLatexMathDrawable drawable =
                        JLatexMathDrawable.builder(block.source)
                            .textSize(dp(20))
                            .build();
                    final int w = drawable.getIntrinsicWidth();
                    final int h = drawable.getIntrinsicHeight();
                    if (w > 0 && h > 0) {
                        final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                        drawable.setBounds(0, 0, w, h);
                        drawable.draw(new Canvas(bm));
                        imageView.setImageBitmap(bm);
                        imageView.setLayoutParams(new FrameLayout.LayoutParams(width = w + dp(2 * parent.padx()), h));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        @Override
        public void updateColors() {
            imageView.setColorFilter(new PorterDuffColorFilter(parent.getTextColor(), PorterDuff.Mode.SRC_IN));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int w = r - l;
            if (width > w) {
                super.onLayout(changed, l, t, r, b);
            } else {
                layout.layout((w - width) / 2, 0, (w + width) / 2, layout.getMeasuredHeight());
            }
        }

        @Override
        public int getBoundLeft() {
            if (width > getMeasuredWidth()) return 0;
            return (getMeasuredWidth() - width) / 2;
        }

        @Override
        public int getBoundRight() {
            if (width > getMeasuredWidth()) return getMeasuredWidth();
            return (getMeasuredWidth() + width) / 2;
        }

        @Override
        public int getLastLineBoundRight() {
            return getBoundRight();
        }

        @Override
        public int getMinWidth() {
            return width;
        }
    }

    private class ReportCell extends FrameLayout {

        private TextView textView;
        private TextView viewsTextView;
        private boolean hasViews;
        public final boolean web;

        public ReportCell(Context context, boolean web) {
            super(context);
            this.web = web;
            setTag(90);

            textView = new TextView(context);
            textView.setText(LocaleController.getString(web ? R.string.PreviewFeedbackAuto : R.string.PreviewFeedback2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(dp(18), 0, dp(18), 0);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 0, 10, 0, 0));

            viewsTextView = new TextView(context);
            viewsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            viewsTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            viewsTextView.setPadding(dp(18), 0, dp(18), 0);
            addView(viewsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 0, 10, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
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
            int color = getThemedColor(Theme.key_switchTrack);
            textView.setTextColor(getGrayTextColor());
            viewsTextView.setTextColor(getGrayTextColor());
            textView.setBackgroundColor(Color.argb(34, Color.red(color), Color.green(color), Color.blue(color)));
        }
    }

    @Override
    public TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper(View view) {
        if (view != null && view.getTag() != null && view.getTag() == BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
            return textSelectionHelperBottomSheet;
        } else {
            return textSelectionHelper;
        }
    }

    private void drawTextSelection(Canvas canvas, TextSelectionHelper.ArticleSelectableView view) {
        drawTextSelection(this, canvas, view, 0);
    }
    public static void drawTextSelection(IArticleViewer parent, Canvas canvas, TextSelectionHelper.ArticleSelectableView view) {
        drawTextSelection(parent, canvas, view, 0);
    }

    private void drawTextSelection(Canvas canvas, TextSelectionHelper.ArticleSelectableView view, int i) {
        drawTextSelection(this, canvas, view, i);
    }
    public static void drawTextSelection(IArticleViewer parent, Canvas canvas, TextSelectionHelper.ArticleSelectableView view, int i) {
        final TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper = parent.getTextSelectionHelper((View) view);
        if (textSelectionHelper != null) {
            textSelectionHelper.draw(canvas, view, i);
        }
    }

    @Override
    public boolean openPhoto(TL_iv.PageBlock block, WebpageAdapter adapter) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return false;
        }
        final int index;
        final List<TL_iv.PageBlock> pageBlocks;
        if (!(block instanceof TL_iv.pageBlockVideo) || WebPageUtils.isVideo(adapter.currentPage, block)) {
            pageBlocks = new ArrayList<>(adapter.photoBlocks);
            index = adapter.photoBlocks.indexOf(block);
        } else {
            pageBlocks = Collections.singletonList(block);
            index = 0;
        }
        final PhotoViewer photoViewer = PhotoViewer.getInstance();
        photoViewer.setParentActivity(parentFragment);
        if (photoViewer.openPhoto(index, new RealPageBlocksAdapter(adapter.currentPage, pageBlocks), new PageBlocksPhotoViewerProvider(pageBlocks))) {
            checkVideoPlayer();
            return true;
        }
        return false;
    }


    private class RealPageBlocksAdapter implements PhotoViewer.PageBlocksAdapter {

        private final TLRPC.WebPage page;
        private final List<TL_iv.PageBlock> pageBlocks;

        private RealPageBlocksAdapter(TLRPC.WebPage page, List<TL_iv.PageBlock> pageBlocks) {
            this.page = page;
            this.pageBlocks = pageBlocks;
        }

        @Override
        public int getItemsCount() {
            return pageBlocks.size();
        }

        @Override
        public TL_iv.PageBlock get(int index) {
            return pageBlocks.get(index);
        }

        @Override
        public List<TL_iv.PageBlock> getAll() {
            return pageBlocks;
        }

        @Override
        public boolean isVideo(int index) {
            return !(index >= pageBlocks.size() || index < 0) && WebPageUtils.isVideo(page, get(index));
        }

        @Override
        public boolean isHardwarePlayer(int index) {
            return !(index >= pageBlocks.size() || index < 0) && !WebPageUtils.isVideo(page, get(index)) && pages[0].adapter.getTypeForBlock(get(index)) == 5;
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
            final TL_iv.PageBlock pageBlock = get(index);
            if (pageBlock instanceof TL_iv.pageBlockPhoto) {
                String url = ((TL_iv.pageBlockPhoto) pageBlock).url;
                if (!TextUtils.isEmpty(url)) {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(url);
                    stringBuilder.setSpan(new URLSpan(url) {
                        @Override
                        public void onClick(View widget) {
                            openWebpageUrl(getURL(), null, makeProgress(pressedLink, pressedLinkOwnerLayout));
                        }
                    }, 0, url.length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                    caption = stringBuilder;
                }
            }
            if (caption == null) {
                TL_iv.RichText captionRichText = getBlockCaption(pageBlock, 2);
                caption = getText(page, null, captionRichText, captionRichText, pageBlock, -dp(100));
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
                                    openWebpageUrl(getURL(), null, null);
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
        public void updateSlideshowCell(TL_iv.PageBlock currentPageBlock) {
            int count = pages[0].listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = pages[0].listView.getChildAt(a);
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
        private final List<TL_iv.PageBlock> pageBlocks;

        public PageBlocksPhotoViewerProvider(List<TL_iv.PageBlock> pageBlocks) {
            this.pageBlocks = pageBlocks;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            if (index < 0 || index >= pageBlocks.size()) {
                return null;
            }
            ImageReceiver imageReceiver = getImageReceiverFromListView(pages[0].listView, pageBlocks.get(index), tempArr);
            if (imageReceiver == null) {
                return null;
            }
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = tempArr[0];
            object.viewY = tempArr[1];
            object.parentView = pages[0].listView;
            object.imageReceiver = imageReceiver;
            object.thumb = imageReceiver.getBitmapSafe();
            object.radius = imageReceiver.getRoundRadius(true);
            object.clipTopAddition = currentHeaderHeight;
            return object;
        }

        private ImageReceiver getImageReceiverFromListView(ViewGroup listView, TL_iv.PageBlock pageBlock, int[] coords) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = getImageReceiverView(listView.getChildAt(a), pageBlock, coords);
                if (imageReceiver != null) {
                    return imageReceiver;
                }
            }
            return null;
        }

        private ImageReceiver getImageReceiverView(View view, TL_iv.PageBlock pageBlock, int[] coords) {
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
                    if (cell == currentPlayer && videoPlayer != null && videoPlayer.firstFrameRendered && cell.textureView.getSurfaceTexture() != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Surface surface = new Surface(cell.textureView.getSurfaceTexture());
                            Bitmap bitmap = Bitmap.createBitmap(cell.textureView.getMeasuredWidth(), cell.textureView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                            AndroidUtilities.getBitmapFromSurface(surface, bitmap);
                            surface.release();
                            cell.imageView.setImageBitmap(bitmap);
                        } else {
                            cell.imageView.setImageBitmap(cell.textureView.getBitmap());
                        }
                        cell.firstFrameRendered = false;
                        cell.textureView.setAlpha(0);
                    }
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

        @Override
        public void onClose() {
            super.onClose();
            checkVideoPlayer();
        }

        @Override
        public void onReleasePlayerBeforeClose(int photoIndex) {
            TL_iv.PageBlock pageBlock = null;
            if (photoIndex >= 0 && photoIndex < pageBlocks.size()) {
                pageBlock = pageBlocks.get(photoIndex);
            }
            VideoPlayer player = PhotoViewer.getInstance().getVideoPlayer();
            TextureView textureView = PhotoViewer.getInstance().getVideoTextureView();
            SurfaceView surfaceView = PhotoViewer.getInstance().getVideoSurfaceView();
            BlockVideoCell videoCell = getViewFromListView(pages[0].listView, pageBlock);
            if (videoCell != null && player != null && textureView != null) {
                videoStates.put(videoCell.currentBlock.video_id, videoCell.setState(BlockVideoCellState.fromPlayer(player, videoCell, textureView)));
                videoCell.firstFrameRendered = false;
                videoCell.textureView.setAlpha(0);
                if (videoCell.videoState != null && videoCell.videoState.lastFrameBitmap != null) {
                    videoCell.imageView.setImageBitmap(videoCell.videoState.lastFrameBitmap);
                }
            }
            if (videoCell != null && player != null && surfaceView != null) {
                videoStates.put(videoCell.currentBlock.video_id, videoCell.setState(BlockVideoCellState.fromPlayer(player, videoCell, surfaceView)));
                videoCell.firstFrameRendered = false;
                videoCell.textureView.setAlpha(0);
                if (videoCell.videoState != null && videoCell.videoState.lastFrameBitmap != null) {
                    videoCell.imageView.setImageBitmap(videoCell.videoState.lastFrameBitmap);
                }
            }
            checkVideoPlayer();
        }

        private BlockVideoCell getViewFromListView(ViewGroup listView, TL_iv.PageBlock pageBlock) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                if (view instanceof BlockVideoCell) {
                    BlockVideoCell cell = (BlockVideoCell) view;
                    if (cell.currentBlock == pageBlock) {
                        return cell;
                    }
                }
            }
            return null;
        }
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, getResourcesProvider());
    }

    @Override
    public Theme.ResourcesProvider getResourcesProvider() {
        return null; // sheet != null ? sheet.resourcesProvider : null;
    }

    public boolean isFirstArticle() {
        return pagesStack.size() > 0 && pagesStack.get(0) instanceof TLRPC.WebPage;
    }

    private final AnimatedColor page0Background = new AnimatedColor(() -> AndroidUtilities.runOnUIThread(this::updatePages), 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedColor page1Background = new AnimatedColor(() -> AndroidUtilities.runOnUIThread(this::updatePages), 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void updatePages() {
        if (actionBar == null || pages[0] == null || pages[1] == null) {
            return;
        }

        final float page0Alpha = pages[0].getVisibility() != View.VISIBLE ? 0 : 1f - pages[0].getTranslationX() / pages[0].getWidth();
        final float page1Alpha = 1f - page0Alpha;

        actionBar.setProgress(0, pages[0].getProgress());
        actionBar.setProgress(1, pages[1].getProgress());
        actionBar.setTransitionProgress(page1Alpha);
        if (!actionBar.isAddressing() && !actionBar.isSearching() && (windowView.movingPage || windowView.openingPage)) {
            if (isFirstArticle() || pagesStack.size() > 1) {
                final float backButton = lerp(pages[0].hasBackButton() || pagesStack.size() > 1 ? 1f : 0, pages[1].hasBackButton() || pagesStack.size() > 2 ? 1f : 0, page1Alpha);
                actionBar.backButtonDrawable.setRotation(1f - backButton, false);
                actionBar.forwardButtonDrawable.setState(false); // pages[0].hasForwardButton());
                actionBar.setBackButtonCached(backButton > .5f);
            } else {
//                actionBar.backButtonDrawable.setRotation(1f - backButton, false);
                actionBar.forwardButtonDrawable.setState(false); // pages[0].hasForwardButton());
                actionBar.setBackButtonCached(false); // backButton > .5f);
            }
            actionBar.setHasForward(pages[0].hasForwardButton());
            actionBar.setIsLocal(pages[0].isLocal());
            actionBar.setIsLoaded(pages[0].getWebView() != null && pages[0].getWebView().isPageLoaded());
        }

        actionBar.setBackgroundColor(0, page0Background.set(pages[0].getActionBarColor(), windowView.movingPage || windowView.openingPage));
        actionBar.setBackgroundColor(1, page1Background.set(pages[1].getActionBarColor(), windowView.movingPage || windowView.openingPage));

        actionBar.setColors(ColorUtils.blendARGB(pages[0].getActionBarColor(), pages[1].getActionBarColor(), page1Alpha), false);

        actionBar.setMenuType((page0Alpha > .5f ? pages[0] : pages[1]).type);

        if (sheet != null) {
            sheet.windowView.invalidate();
        } else if (windowView != null) {
            windowView.invalidate();
        }
    }

    public void updateTitle(boolean animated) {
        actionBar.setTitle(0, pages[0].getTitle(), animated);
        actionBar.setSubtitle(0, pages[0].getSubtitle(), false);
        actionBar.setIsDangerous(0, pages[0].isWeb() && pages[0].getWebView() != null && pages[0].getWebView().isUrlDangerous(), false);

        actionBar.setTitle(1, pages[1].getTitle(), animated);
        actionBar.setSubtitle(1, pages[1].getSubtitle(), false);
        actionBar.setIsDangerous(1, pages[1].isWeb() && pages[1].getWebView() != null && pages[1].getWebView().isUrlDangerous(), false);
    }

    public void setOpener(BotWebViewContainer.MyWebView webView) {
        if (pages == null) return;
        for (int i = 0; i < pages.length; ++i) {
            if (pages[i] == null) continue;
            pages[i].webViewContainer.setOpener(webView);
        }
    }

    public class PageLayout extends FrameLayout {

        public static final int TYPE_ARTICLE = 0;
        public static final int TYPE_WEB = 1;

        public int type;

        public final RecyclerListView listView;
        public final WebpageAdapter adapter;
        public final LinearLayoutManager layoutManager;

        public final ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer swipeContainer;
        public final BotWebViewContainer webViewContainer;
        private boolean swipeBack;

        private boolean errorShown;
        public ErrorContainer errorContainer;

        public boolean backButton, forwardButton;
        public int webActionBarColor = getThemedColor(Theme.key_iv_background);
        public int webBackgroundColor = getThemedColor(Theme.key_iv_background);

        public WebInstantView.Loader currentInstantLoader;

        public boolean paused = false;
        public void pause() {
            if (paused) return;
            if (getWebView() != null) {
                getWebView().onPause();
            }
            paused = true;
        }
        public void resume() {
            if (!paused) return;
            if (getWebView() != null) {
                getWebView().onResume();
            }
            paused = false;
        }

        public PageLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            listView = new WebpageListView(context, resourcesProvider) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);
                    overrideProgress = -1;
                }
            };
            listView.setClipToPadding(false);
            if (BOTTOM_ACTION_BAR) {
                listView.setPadding(0, (int) (AndroidUtilities.statusBarHeight * 1.25f), 0, dp(24));
            } else {
                listView.setPadding(0, dp(56), 0, 0);
                listView.setTopGlowOffset(dp(56));
            }
            ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
            listView.setAdapter(adapter = new WebpageAdapter(context, sheet != null && sheet.halfSize()));
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    recyclerView.invalidate();
                    textSelectionHelper.onParentScrolled();
                    if (sheet != null) {
                        sheet.windowView.invalidate();
                    } else if (windowView != null) {
                        windowView.invalidate();
                    }
                    updatePages();
                    checkScroll(dy);
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            swipeContainer = new ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer(getContext()) {
                private boolean ignoreLayout;
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    ignoreLayout = true;
                    setOffsetY(MeasureSpec.getSize(heightMeasureSpec) * .4f);
                    ignoreLayout = false;
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - dp(sheet != null && !sheet.halfSize() ? 0 : 56) - AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
                }
                @Override
                public void requestLayout() {
                    if (!ignoreLayout) {
                        super.requestLayout();
                    }
                }
            };
            swipeContainer.setShouldWaitWebViewScroll(true);
            swipeContainer.setFullSize(true);
            swipeContainer.setAllowFullSizeSwipe(true);
            webViewContainer = new BotWebViewContainer(getContext(), resourcesProvider, getThemedColor(Theme.key_windowBackgroundWhite), false) {
                @Override
                public void setPageLoaded(String url, boolean animated) {
                    if (actionBar != null && PageLayout.this == pages[0] && currentInstantLoader != null && currentInstantLoader.getWebPage() == null) {
                        currentInstantLoader.retryLocal(getWebView());
                    }
                    super.setPageLoaded(url, animated);
                }

                @Override
                public void onWebViewCreated(MyWebView webView) {
                    super.onWebViewCreated(webView);
                    swipeContainer.setWebView(webView);
                }

                @Override
                protected void onURLChanged(String url, boolean first, boolean last) {
                    backButton = !first;
                    forwardButton = !last;
                    updateTitle(true);
                    if (PageLayout.this == pages[0] && !actionBar.isAddressing() && !actionBar.isSearching() && !(windowView.movingPage || windowView.openingPage)) {
                        if (isFirstArticle() || pagesStack.size() > 1) {
                            actionBar.backButtonDrawable.setRotation(backButton || pagesStack.size() > 1 ? 0 : 1, true);
                            actionBar.setBackButtonCached(backButton || pagesStack.size() > 1);
                            actionBar.forwardButtonDrawable.setState(false); // hasForwardButton());
                        } else {
                            actionBar.setBackButtonCached(false);
                            actionBar.forwardButtonDrawable.setState(false);
                        }
                        actionBar.setHasForward(forwardButton);
                        actionBar.setIsTonsite(pages[0] != null && pages[0].isTonsite());
                        actionBar.setIsLocal(pages[0] != null && pages[0].isLocal());
                    }
                }

                @Override
                protected void onTitleChanged(String title) {
                    updateTitle(true);
                }

                @Override
                protected void onFaviconChanged(Bitmap favicon) {
                    super.onFaviconChanged(favicon);
                }

                @Override
                protected void onErrorShown(boolean shown, int errorCode, String description) {
                    if (shown) {
                        createErrorContainer();
                        errorContainer.set(getWebView() != null ? getWebView().getUrl() : null, errorCode, description);
                        errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_iv_background)) <= .721f, false);
                        errorContainer.setBackgroundColor(getThemedColor(Theme.key_iv_background));
                    }
                    AndroidUtilities.updateViewVisibilityAnimated(errorContainer, errorShown = shown, 1f, false);
                    invalidate();
                }
            };
            webViewContainer.setOnCloseRequestedListener(() -> {
                if (LaunchActivity.instance == null) return;
                final BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();
                if (tabs == null || !tabs.tryRemoveTabWith(ArticleViewer.this)) {
                    close(true, true);
                }
            });
            webViewContainer.setWebViewProgressListener(progress -> {
                if (PageLayout.this == pages[0]) {
                    if (actionBar.lineProgressView.getCurrentProgress() > progress) {
                        actionBar.lineProgressView.setProgress(0, false);
                    }
                    actionBar.lineProgressView.setProgress(progress, true);
                }
            });
            webViewContainer.setDelegate(new BotWebViewContainer.Delegate() {

                @Override
                public void onCloseRequested(@Nullable Runnable callback) {
                    if (pages[0] == PageLayout.this) {
                        goBack();
                    }
                }

                @Override
                public void onCloseToTabs() {
                    if (sheet != null) {
                        sheet.dismiss(true);
                    }
                }

                @Override
                public void onInstantClose() {
                    if (sheet != null) {
                        sheet.dismissInstant();
                    } else if (pages[0] == PageLayout.this) {
                        goBack();
                    }
                }

                @Override
                public void onWebAppSetupClosingBehavior(boolean needConfirmation) {

                }

                @Override
                public void onWebAppSwipingBehavior(boolean allowSwipes) {

                }

                @Override
                public void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {

                }

                @Override
                public void onWebAppSetBackgroundColor(int color) {

                }

                @Override
                public void onWebAppBackgroundChanged(boolean actionBarColor, int color) {
                    setWebBgColor(actionBarColor, color);
                }

                @Override
                public void onWebAppExpand() {

                }

                @Override
                public void onWebAppSwitchInlineQuery(TLRPC.User botUser, String query, List<String> chatTypes) {

                }

                @Override
                public void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response) {

                }

                @Override
                public void onSetupMainButton(boolean isVisible, boolean isActive, String text, long emojiId, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect) {

                }

                @Override
                public void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, long emojiId, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position) {

                }

                @Override
                public void onSetBackButtonVisible(boolean visible) {

                }

                @Override
                public void onSetSettingsButtonVisible(boolean visible) {

                }
            });
            webViewContainer.setWebViewScrollListener(new BotWebViewContainer.WebViewScrollListener() {
                @Override
                public void onWebViewScrolled(WebView webView, int dx, int dy) {
                    updatePages();
                }
            });
            swipeContainer.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            swipeContainer.setScrollEndListener(() -> webViewContainer.invalidateViewPortHeight(true));
            swipeContainer.setDelegate(byTap -> {
                if (sheet != null) {
                    swipeBack = true;
                    sheet.dismiss(true);
                }
            });
            swipeContainer.setScrollListener(() -> {
                webViewContainer.invalidateViewPortHeight();
                if (errorContainer != null) {
                    errorContainer.layout.setTranslationY(((-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY()) - swipeContainer.getSwipeOffsetY()) / 2f);
                }
                updatePages();
            });
            swipeContainer.setTopActionBarOffsetY(dp(sheet != null && !sheet.halfSize() ? 0 : 56) + AndroidUtilities.statusBarHeight);
//            swipeContainer.setIsKeyboardVisible(obj -> windowView.getKeyboardHeight() >= dp(20));
            addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            cleanup();
            setType(TYPE_ARTICLE);
        }

        private void setWebBgColor(boolean actionBarColor, int color) {
            if (actionBarColor) {
                webActionBarColor = Theme.blendOver(getThemedColor(Theme.key_iv_background), color);
                if (PageLayout.this == pages[0]) {
                    if (SharedConfig.adaptableColorInBrowser) {
                        actionBar.setColors(webActionBarColor, true);
                    }
                    if (sheet != null) {
                        sheet.checkNavColor();
                    }
                }
            } else {
                webBackgroundColor = Theme.blendOver(0xFFFFFFFF, color);
                if (PageLayout.this == pages[0]) {
                    if (SharedConfig.adaptableColorInBrowser) {
                        actionBar.setMenuColors(webBackgroundColor);
                    }
                    if (sheet != null) {
                        sheet.checkNavColor();
                    }
                }
            }
            updatePages();
        }

        public ErrorContainer createErrorContainer() {
            if (errorContainer == null) {
                swipeContainer.addView(errorContainer = new ErrorContainer(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                errorContainer.buttonView.setOnClickListener(v -> {
                    BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                    if (webView != null) {
                        webView.reload();
                    }
                });
                AndroidUtilities.updateViewVisibilityAnimated(errorContainer, errorShown, 1f, false);
            }
            return errorContainer;
        }

        public boolean isWeb() {
            return type == TYPE_WEB;
        }

        public boolean isArticle() {
            return type == TYPE_ARTICLE;
        }

        public void setType(int type) {
            if (this.type != type) {
                cleanup();
            }
            this.type = type;
            listView.setVisibility(isArticle() ? View.VISIBLE : View.GONE);
            swipeContainer.setVisibility(isWeb() ? View.VISIBLE : View.GONE);
        }

        public String getTitle() {
            if (isArticle()) {
                if (adapter.currentPage != null && adapter.currentPage.site_name != null) {
                    return adapter.currentPage.site_name;
                }
                if (adapter.currentPage != null && adapter.currentPage.title != null) {
                    return adapter.currentPage.title;
                }
            }
            if (isWeb()) {
                BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                if (webView != null) {
                    return webView.getTitle();
                }
            }
            return "";
        }

        public int getBackgroundColor() {
            if (isWeb() && SharedConfig.adaptableColorInBrowser) {
                if (errorShown) {
                    return getThemedColor(Theme.key_iv_background);
                }
                return webBackgroundColor;
            }
            return getThemedColor(Theme.key_iv_background);
        }

        public int getActionBarColor() {
            if (isWeb() && SharedConfig.adaptableColorInBrowser) {
                return webActionBarColor;
            }
            return getThemedColor(Theme.key_iv_background);
        }

        private String lastUrl, lastFormattedUrl;
        public String getSubtitle() {
            if (isWeb()) {
                BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                if (webView != null) {
                    if (TextUtils.equals(lastUrl, webView.getUrl())) {
                        return lastFormattedUrl;
                    }
                    try {
                        Uri uri = Uri.parse(BotWebViewContainer.magic2tonsite(lastUrl = webView.getUrl()));
                        String url = (uri.getScheme() != null && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) ? uri.getSchemeSpecificPart() : uri.toString();
                        if (!isTonsite()) {
                            try {
                                try {
                                    Uri uri2 = Uri.parse(url);
                                    if (uri2.getHost() == null) uri2 = uri;
                                    String hostname = Browser.IDN_toUnicode(uri2.getHost());
                                    String[] levels = hostname.split("\\.");
                                    if (levels.length > 2 && actionBar != null && HintView2.measureCorrectly(hostname, actionBar.titlePaint) > AndroidUtilities.displaySize.x - dp(3 * 54)) {
                                        hostname = levels[levels.length - 2] + '.' + levels[levels.length - 1];
                                    }
                                    url = Browser.replace(uri2, null, "", hostname, null);
                                } catch (Exception e) {
                                    FileLog.e(e, false);
                                }
                                url = URLDecoder.decode(url.replaceAll("\\+", "%2b"), "UTF-8");
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        if (url.startsWith("//"))
                            url = url.substring(2);
                        if (url.startsWith("www."))
                            url = url.substring(4);
                        if (url.endsWith("/"))
                            url = url.substring(0, url.length() - 1);
                        int index = 0;
                        if ((index = url.indexOf("#")) >= 0) {
                            url = url.substring(0, index);
                        }
                        return lastFormattedUrl = url;
                    } catch (Exception e) {
                        return webView.getUrl();
                    }
                }
            }
            return "";
        }

        private boolean lastVisible;
        public void setLastVisible(boolean lastVisible) {
            if (this.lastVisible != lastVisible) {
                this.lastVisible = lastVisible;
                webViewContainer.setKeyboardFocusable(lastVisible);
            }
        }

        public boolean hasBackButton() {
            return backButton;
        }

        public void back() {
            if (isWeb() && getWebView() != null) {
                getWebView().goBack();
            }
        }

        public boolean hasForwardButton() {
            return forwardButton;
        }

        public void forward() {}

        public float getListTop() {
            if (isArticle()) {
                float listViewTop = listView.getHeight();
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    int viewType = listView == null || listView.getLayoutManager() == null ? 0 : listView.getLayoutManager().getItemViewType(child);
                    if (viewType == Integer.MAX_VALUE - 1) {
                        listViewTop = Math.min(listViewTop, child.getBottom());
                    } else {
                        listViewTop = Math.min(listViewTop, child.getTop());
                    }
                }
                return listViewTop;
            } else if (isWeb()) {
                return swipeContainer.getTranslationY();
            }
            return 0;
        }

        public float overrideProgress = -1f;
        public float getProgress() {
            if (isArticle()) {
                if (overrideProgress >= 0) return overrideProgress;
                int first = layoutManager.findFirstVisibleItemPosition();
                View view = layoutManager.findViewByPosition(first);
                if (view == null)
                    return 0;

                if (adapter.sumItemHeights == null) {
                    int last = layoutManager.findLastVisibleItemPosition();
                    if (sheet != null && sheet.halfSize()) {
                        if (first < 1) first = 1;
                        if (last < 1) last = 1;
                    }
                    int count = layoutManager.getItemCount();
                    if (last >= count - 2) {
                        view = layoutManager.findViewByPosition(count - 2);
                    } else {
                        view = layoutManager.findViewByPosition(first);
                    }
                    if (view == null) {
                        return 0;
                    }

                    float itemProgress = getWidth() / (float) (count - 1);

                    float viewHeight = view.getMeasuredHeight();
                    float viewProgress;
                    if (last >= count - 2) {
                        viewProgress = (count - 2 - first) * itemProgress * (listView.getMeasuredHeight() - view.getTop()) / viewHeight;
                    } else {
                        viewProgress = itemProgress * (1.0f - (Math.min(0, view.getTop() - listView.getPaddingTop()) + viewHeight) / viewHeight);
                    }
                    float progress = first * itemProgress + viewProgress;
                    return progress / getWidth();
                }

                int offset = 0;
                if (adapter.sumItemHeights != null) {
                    int beforeIndex = first - 1;
                    offset = (beforeIndex >= 0 && beforeIndex < adapter.sumItemHeights.length ? adapter.sumItemHeights[beforeIndex] : 0) + (first != 0 || sheet == null || !sheet.halfSize() ? -view.getTop() : 0);
                }
                return Utilities.clamp01((float) offset / Math.max(1, adapter.fullHeight - listView.getHeight()));
            } else if (isWeb()) {
                BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                if (webView == null) return 0;
                return webView.getScrollProgress();
            }

            return 0;
        }

        public void addProgress(float delta) {
            float progress = getProgress();
            progress = Utilities.clamp01(progress + delta);
            if (isArticle()) {
                return;
//                if (adapter.itemHeights == null) return;
//                int row = 0;
//                int offset = (int) (progress * Math.max(1, adapter.fullHeight - listView.getHeight()));
//                while (offset >= 0 && row < adapter.getItemCount()) {
//                    offset -= adapter.itemHeights[row];
//                    row++;
//                }
//                overrideProgress = progress;
//                layoutManager.scrollToPositionWithOffset(row + 1, offset);
//                listView.invalidate();
//                textSelectionHelper.onParentScrolled();
//                if (sheet != null) {
//                    sheet.windowView.invalidate();
//                } else if (windowView != null) {
//                    windowView.invalidate();
//                }
//                updatePages();
            } else if (isWeb()) {
                BotWebViewContainer.MyWebView webView = webViewContainer.getWebView();
                if (webView == null) return;
                webView.setScrollProgress(progress);
                updatePages();
            }
        }

        public boolean isAtTop() {
            if (isArticle()) {
                return !listView.canScrollVertically(-1);
            } else if (isWeb()) {

            }
            return false;
        }

        public void scrollToTop(boolean smooth) {
            if (isArticle()) {
                if (smooth) {
                    SmoothScroller s = new SmoothScroller(getContext());
                    if (sheet != null && sheet.halfSize()) {
                        s.setTargetPosition(1);
                        s.setOffset(-dp(56 - 24));
                    } else {
                        s.setTargetPosition(0);
                    }
                    layoutManager.startSmoothScroll(s);
                } else {
                    layoutManager.scrollToPositionWithOffset(sheet != null && sheet.halfSize() ? 1 : 0, sheet != null ? dp(56 - 24) : 0);
                }
            } else if (isWeb()) {
                if (smooth) {
                    swipeContainer.stickTo(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
                } else {
                    swipeContainer.setSwipeOffsetY(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
                }
            }
        }

        public RecyclerListView getListView() {
            return listView;
        }

        public WebpageAdapter getAdapter() {
            return adapter;
        }

        public BotWebViewContainer getWebContainer() {
            return webViewContainer;
        }

        public BotWebViewContainer.MyWebView getWebView() {
            return webViewContainer != null ? webViewContainer.getWebView() : null;
        }

        public boolean isTonsite() {
            if (!isWeb()) return false;
            BotWebViewContainer.MyWebView webView = getWebView();
            if (webView == null) return false;
            return BotWebViewContainer.isTonsite(BotWebViewContainer.magic2tonsite(webView.getUrl()));
        }

        public boolean isLocal() {
            if (isWeb()) return false;
            if (adapter == null || adapter.currentPage == null || adapter.currentPage.cached_page == null) return false;
            return adapter.currentPage.cached_page.local != null;
        }

        public void cleanup() {
            backButton = false;
            forwardButton = false;
            setWeb(null);
            webViewContainer.destroyWebView();
            webViewContainer.resetWebView();
            webActionBarColor = getThemedColor(Theme.key_iv_background);
            webBackgroundColor = getThemedColor(Theme.key_iv_background);
            if (errorContainer != null) {
                errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(webBackgroundColor) <= .721f, true);
                errorContainer.setBackgroundColor(webBackgroundColor);
                AndroidUtilities.updateViewVisibilityAnimated(errorContainer, errorShown = false, 1f, false);
            }
            adapter.cleanup();
            invalidate();
        }

        private CachedWeb web;
        public void setWeb(CachedWeb web) {
            if (this.web != web) {
                if (this.web != null) {
                    this.web.detach(this);
                }
                this.web = web;
                if (this.web != null) {
                    this.web.attach(this);
                }

                if (currentInstantLoader != null) {
                    currentInstantLoader.cancel();
                    currentInstantLoader.recycle();
                    currentInstantLoader = null;
                }
            }
        }

        public WebInstantView.Loader loadInstant() {
            if (!isWeb()) {
                if (currentInstantLoader != null) {
                    currentInstantLoader.cancel();
                    currentInstantLoader.recycle();
                    currentInstantLoader = null;
                }
                return null;
            }
            if (getWebView() == null) {
                if (currentInstantLoader != null) {
                    currentInstantLoader.cancel();
                    currentInstantLoader.recycle();
                    currentInstantLoader = null;
                }
                return null;
            }
            if (currentInstantLoader != null && (
                currentInstantLoader.currentIsLoaded != getWebView().isPageLoaded() ||
                currentInstantLoader.currentProgress != getWebView().getProgress()
            )) {
                currentInstantLoader.retryLocal(getWebView());
                return currentInstantLoader;
            }
            if (currentInstantLoader != null && TextUtils.equals(getWebView().getUrl(), currentInstantLoader.currentUrl)) {
                return currentInstantLoader;
            }
            if (currentInstantLoader != null) {
                currentInstantLoader.cancel();
                currentInstantLoader.recycle();
                currentInstantLoader = null;
            }
            currentInstantLoader = new WebInstantView.Loader(currentAccount);
            currentInstantLoader.start(getWebView());
            return currentInstantLoader;
        }

        public void updateWeb() {
            if (this.web != null) {
                this.web.enrich(this);
            }
        }

        @Override
        public void setTranslationX(float translationX) {
            super.setTranslationX(translationX);
            updatePages();
            if (windowView.openingPage) {
                containerView.invalidate();
            }
            if (windowView.movingPage) {
                containerView.invalidate();
                float progress = translationX / getMeasuredWidth();
                setCurrentHeaderHeight((int) (windowView.startMovingHeaderHeight + (dp(56) - windowView.startMovingHeaderHeight) * progress));
            }
            if (sheet != null) {
                sheet.updateTranslation();
            }
        }

        private final GradientClip clip = new GradientClip();

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (BOTTOM_ACTION_BAR) {
                final float t1 = AndroidUtilities.statusBarHeight * 0.5f;
                final float t2 = AndroidUtilities.statusBarHeight * 1.25f;
                canvas.saveLayerAlpha(0, (int) t1 + 1, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                super.dispatchDraw(canvas);
                AndroidUtilities.rectTmp.set(0, (int) t1, getWidth(), t2);
                clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.TOP, 1f);
                canvas.restore();
            } else {
                super.dispatchDraw(canvas);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (errorShown && errorContainer != null) {
                errorContainer.setDark(AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_iv_background)) <= .721f, false);
                errorContainer.setBackgroundColor(getThemedColor(Theme.key_iv_background));
            }
        }
    }

    public class CachedWeb extends BottomSheetTabs.WebTabData {

        public CachedWeb(String url) {
            lastUrl = url;
            currentUrl = url;
        }

        public void attach(PageLayout pageLayout) {
            if (pageLayout == null) return;

            if (webView != null) {
                webView.onResume();
                pageLayout.webViewContainer.replaceWebView(UserConfig.selectedAccount, webView, proxy);
                pageLayout.setWebBgColor(true, actionBarColor);
                pageLayout.setWebBgColor(false, backgroundColor);
            } else if (lastUrl != null) {
                pageLayout.webViewContainer.loadUrl(UserConfig.selectedAccount, lastUrl);
            }
        }
        
        public void enrich(PageLayout pageLayout) {
            BotWebViewContainer.MyWebView webView = pageLayout.webViewContainer.getWebView();
            if (webView != null) {
                title = webView.getTitle();
                favicon = webView.getFavicon();
                lastUrl = webView.getUrl();

                actionBarColor = pageLayout.webActionBarColor;
                backgroundColor = pageLayout.webBackgroundColor;
            }
        }

        public void detach(PageLayout pageLayout) {
            if (pageLayout == null) return;

            pageLayout.webViewContainer.preserveWebView();
            webView = pageLayout.webViewContainer.getWebView();
            proxy = pageLayout.webViewContainer.getProxy();
            if (webView != null) {
                webView.onPause();
                title = webView.getTitle();
                favicon = webView.getFavicon();
                lastUrl = webView.getUrl();
                actionBarColor = pageLayout.webActionBarColor;
                backgroundColor = pageLayout.webBackgroundColor;
            }
        }

        @Override
        public String getTitle() {
            if (webView != null && !TextUtils.isEmpty(webView.getTitle())) {
                return webView.getTitle();
            }
            return super.getTitle();
        }
    }

    public class WebpageListView extends RecyclerListView {

        public WebpageListView(Context context) {
            super(context);
        }

        public WebpageListView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

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
            } else if (pressedLinkOwnerLayout != null && pressedLink != null && e.getAction() == MotionEvent.ACTION_UP && getAdapter() instanceof WebpageAdapter) {
                checkLayoutForLinks((WebpageAdapter) getAdapter(), e, pressedLinkOwnerView, pressedLinkOwnerLayout, 0, 0);
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
        protected void dispatchDraw(Canvas canvas) {
            checkVideoPlayer();
            super.dispatchDraw(canvas);
        }

        @Override
        public void onScrolled(int dx, int dy) {
            super.onScrolled(dx, dy);
            if (sheet != null && sheet.windowView != null) {
                sheet.windowView.invalidate();
            }
        }
    }

    public class Sheet implements BaseFragment.AttachedSheet, BottomSheetTabsOverlay.Sheet {
        public final boolean halfSize() {
            return true;
        }

        public final AnimationNotificationsLocker animationsLock = new AnimationNotificationsLocker();

        public BaseFragment fragment;
        public final Context context;
        public final WindowView windowView;
        public View containerView;
        public Theme.ResourcesProvider resourcesProvider;

        public ArticleViewer getArticleViewer() {
            return ArticleViewer.this;
        }

        public Sheet(BaseFragment fragment) {
            this.fragment = fragment;
            this.resourcesProvider = fragment.getResourceProvider();
            this.context = fragment.getContext();
            this.windowView = new WindowView(context);
            new KeyboardNotifier(windowView, true, keyboardHeight -> {
                keyboardVisible = keyboardHeight - AndroidUtilities.navigationBarHeight > dp(20);
            });
        }

        public void setContainerView(View containerView) {
            this.containerView = containerView;
            updateTranslation();
        }

        public WindowView getWindowView() {
            return windowView;
        }

        public boolean preserve;

        public BottomSheetTabDialog dialog;
        private boolean hadDialog;

        @Override
        public boolean setDialog(BottomSheetTabDialog dialog) {
            this.dialog = dialog;
            if (dialog != null) hadDialog = true;
            return true;
        }

        @Override
        public boolean hadDialog() {
            return hadDialog;
        }

        @Override
        public BottomSheetTabs.WebTabData saveState() {
            BottomSheetTabs.WebTabData tab = new BottomSheetTabs.WebTabData();
            tab.title = actionBar.getTitle();
            tab.articleViewer = ArticleViewer.this;
            tab.actionBarColor = pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getActionBarColor() : getThemedColor(Theme.key_iv_background);
            tab.backgroundColor = pages[0] != null && SharedConfig.adaptableColorInBrowser ? pages[0].getBackgroundColor() : getThemedColor(Theme.key_iv_background);
            tab.overrideActionBarColor = true;

            tab.articleProgress = !attachedToActionBar ? 0 : pages[0].getProgress();

            tab.view2 = pages[0];
            tab.favicon = pages[0] != null && pages[0].getWebView() != null ? pages[0].getWebView().getFavicon() : null;
            if (tab.view2 != null) {
                tab.viewWidth = tab.view2.getWidth();
                tab.viewHeight = tab.view2.getHeight();
            }
            tab.viewScroll = getListTop();
            tab.themeIsDark = Theme.isCurrentThemeDark();

            return tab;
        }

        @Override
        public boolean restoreState(BaseFragment fragment, BottomSheetTabs.WebTabData tab) {
            return false;
        }

        @Override
        public boolean isShown() {
            return !dismissing && !released && openProgress > 0.5f && windowView != null && windowView.isAttachedToWindow() && windowView.isVisible() && backProgress < 1f;
        }

        public void attachInternal(BaseFragment fragment) {
            this.released = false;
            this.fragment = fragment;
            this.resourcesProvider = fragment.getResourceProvider();
            if (fragment instanceof ChatActivity) {
                if (((ChatActivity) fragment).getChatActivityEnterView() != null) {
                    ((ChatActivity) fragment).getChatActivityEnterView().closeKeyboard();
                    ((ChatActivity) fragment).getChatActivityEnterView().hidePopup(true, false);
                }
            }
            if (dialog != null) {
                dialog.attach();
            } else {
                AndroidUtilities.removeFromParent(windowView);
                if (fragment.getLayoutContainer() != null) {
                    fragment.getLayoutContainer().addView(windowView);
                }
            }
            if (pages[0] != null) {
                pages[0].resume();
            }
            if (pages[1] != null) {
                pages[1].resume();
            }
            activeSheets.add(ArticleViewer.this);
        }

        private void dumpTree(StringBuilder sb, View v, int depth, int maxDepth) {
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append(v.getClass().getName())
              .append(" important=").append(v.getImportantForAccessibility())
              .append(" vis=").append(v.getVisibility())
              .append(" size=").append(v.getWidth()).append("x").append(v.getHeight())
              .append(" focusable=").append(v.isFocusable())
              .append(" clickable=").append(v.isClickable())
              .append(" enabled=").append(v.isEnabled())
              .append(" cd=").append(v.getContentDescription())
              .append('\n');
            if (depth >= maxDepth) return;
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    dumpTree(sb, vg.getChildAt(i), depth + 1, maxDepth);
                }
            }
        }

        public void show() {
            if (dismissing) return;
            attachInternal(fragment);
            animateOpen(true, true, null);
        }

        @Override
        public void dismiss() {
            dismiss(true);
        }

        private boolean dismissing;
        private boolean dismissingIntoTabs;
        private boolean released;

        @Override
        public void dismiss(boolean tabs) {
            if (dismissing) return;
            dismissing = true;
            dismissingIntoTabs = tabs;
            if (tabs) {
                LaunchActivity.instance.getBottomSheetTabsOverlay().dismissSheet(this);
            } else {
                animateDismiss(true, true, () -> {
                    release();
                    destroy();
                });
            }
            checkNavColor();
            checkFullyVisible();
        }

        @Override
        public void release() {
            released = true;
            if (pages[0] != null && pages[0].swipeBack) {
                pages[0].swipeContainer.setSwipeOffsetY(-pages[0].swipeContainer.offsetY + pages[0].swipeContainer.topActionBarOffsetY);
                pages[0].swipeBack = false;
            }
            if (pages[0] != null) {
                pages[0].pause();
            }
            if (pages[1] != null) {
                pages[1].pause();
            }
            if (dialog != null) {
                dialog.detach();
            }
            if (fragment != null) {
                fragment.removeSheet(this);
                if (dialog == null) {
                    AndroidUtilities.removeFromParent(windowView);
                }
            }
            if (onDismissListener != null) {
                onDismissListener.run();
                onDismissListener = null;
            }
            activeSheets.remove(ArticleViewer.this);
        }

        public void dismissInstant() {
            if (dismissing) return;
            dismissing = true;
            release();
            destroy();
        }

        @Override
        public boolean isFullSize() {
            return true;
        }

        private boolean wasFullyVisible;
        @Override
        public boolean isFullyVisible() {
            return fullyAttachedToActionBar && dismissProgress <= 0 && openProgress >= 1 && backProgress <= 0 && !dismissingIntoTabs && !dismissing;
        }
        public void checkFullyVisible() {
            if (wasFullyVisible != isFullyVisible()) {
                wasFullyVisible = isFullyVisible();

                if (fragment != null && fragment.getParentLayout() instanceof ActionBarLayout) {
                    ActionBarLayout layout = (ActionBarLayout) fragment.getParentLayout();
                    if (layout.containerView != null) {
                        layout.containerView.invalidate();
                    }
                    if (layout.sheetContainer != null) {
                        layout.sheetContainer.invalidate();
                    }
                } else if (windowView.getParent() instanceof View) {
                    ((View) windowView.getParent()).invalidate();
                }
            }
        }
        @Override
        public boolean attachedToParent() {
            return windowView.isAttachedToWindow();
        }

        @Override
        public boolean onAttachedBackPressed() {
            if (keyboardVisible) {
                AndroidUtilities.hideKeyboard(windowView);
                return true;
            }
            if (actionBar.isSearching()) {
                actionBar.showSearch(false, true);
                return true;
            }
            if (actionBar.isAddressing()) {
                actionBar.showAddress(false, true);
                return true;
            }
            if (isFirstArticle() && pages[0].hasBackButton()) {
                pages[0].back();
                return true;
            }
            if (pagesStack.size() > 1) {
                goBack();
                return true;
            }
            dismiss(false);
            return true;
        }

        @Override
        public boolean showDialog(Dialog dialog) {
            return false;
        }

        @Override
        public void setKeyboardHeightFromParent(int keyboardHeight) {

        }

        @Override
        public int getNavigationBarColor(int color) {
            final float open = dismissingIntoTabs ? 0f : Math.min(openProgress, 1f - dismissProgress) * (1f - backProgress);
            int bgColor = getBackgroundColor();
            if (actionBar != null) {
                bgColor = ColorUtils.blendARGB(bgColor, actionBar.addressBackgroundColor, actionBar.addressingProgress);
            }
            return ColorUtils.blendARGB(color, bgColor, open);
        }

        @Override
        public boolean isAttachedLightStatusBar() {
            final float open = dismissingIntoTabs ? 0f : Math.min(openProgress, 1f - dismissProgress) * (1f - backProgress);
            return attachedToActionBar && open > .25f ? AndroidUtilities.computePerceivedBrightness(getActionBarColor()) >= .721f : false;
        }

        private Runnable onDismissListener;
        @Override
        public void setOnDismissListener(Runnable onDismiss) {
            onDismissListener = onDismiss;
        }

        public void reset() {
            dismissing = false;
            dismissingIntoTabs = false;
            if (openAnimator != null) {
                openAnimator.cancel();
            }
            if (dismissAnimator != null) {
                dismissAnimator.cancel();
            }
            dismissProgress = 0f;
            openProgress = 0f;
            checkFullyVisible();
            updateTranslation();
            windowView.invalidate();
            windowView.requestLayout();
        }

        private float openProgress;
        private float dismissProgress;
        private ValueAnimator openAnimator;
        private ValueAnimator dismissAnimator;

        public void animateOpen(boolean open, boolean animated, Runnable callback) {
            if (openAnimator != null) {
                openAnimator.cancel();
            }
            if (animated) {
                openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1f : 0f);
                openAnimator.addUpdateListener(anm -> {
                    openProgress = (float) anm.getAnimatedValue();
                    updateTranslation();
                    checkNavColor();
                    checkFullyVisible();
                });
                openAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        openProgress = open ? 1f : 0f;
                        updateTranslation();
                        checkNavColor();
                        if (callback != null) {
                            callback.run();
                        }
                        checkFullyVisible();
                        if (open) animationsLock.unlock();
                    }
                });
                if (open) {
                    openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    openAnimator.setDuration(320);
                } else {
                    openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                    openAnimator.setDuration(180);
                }
                openAnimator.start();
            } else {
                openProgress = open ? 1f : 0f;
                updateTranslation();
                if (callback != null) {
                    callback.run();
                }
                checkFullyVisible();
                if (open) animationsLock.unlock();
            }
        }

        public void animateDismiss(boolean dismiss, boolean animated, Runnable callback) {
            if (dismissAnimator != null) {
                dismissAnimator.cancel();
            }
            if (animated) {
                dismissAnimator = ValueAnimator.ofFloat(dismissProgress, dismiss ? 1f : 0f);
                dismissAnimator.addUpdateListener(anm -> {
                    dismissProgress = (float) anm.getAnimatedValue();
                    if (!dismissingIntoTabs) {
                        updateTranslation();
                    }
                    checkNavColor();
                    checkFullyVisible();
                });
                dismissAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        dismissProgress = dismiss ? 1f : 0f;
                        if (!dismissingIntoTabs) {
                            updateTranslation();
                        }
                        checkNavColor();
                        if (callback != null) {
                            callback.run();
                        }
                        checkFullyVisible();
                    }
                });
                dismissAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                dismissAnimator.setDuration(250);
                dismissAnimator.start();
            } else {
                dismissProgress = dismiss ? 1f : 0f;
                if (!dismissingIntoTabs) {
                    updateTranslation();
                }
                if (callback != null) {
                    callback.run();
                }
                checkFullyVisible();
            }
        }

        private int getListTop() {
            int top = 0;
            final float listView0Alpha = pages[0] == null || pages[0].getVisibility() != View.VISIBLE ? 0 : 1f - pages[0].getTranslationX() / pages[0].getWidth();
            final float listView1Alpha = 1f - listView0Alpha;
            if (pages[0] != null && pages[0].getVisibility() == View.VISIBLE) {
                top += (int) (pages[0].getListTop() * listView0Alpha * pages[0].getAlpha());
            }
            if (pages[1] != null && pages[1].getVisibility() == View.VISIBLE) {
                top += (int) (pages[1].getListTop() * listView1Alpha * pages[1].getAlpha());
            }
            return top;
        }

        public void checkNavColor() {
            AndroidUtilities.setLightStatusBar(dialog != null ? dialog.windowView : windowView, isAttachedLightStatusBar());
            if (dialog != null) {
                dialog.updateNavigationBarColor();
            } else {
                LaunchActivity.instance.checkSystemBarColors(true, true, true);
                AndroidUtilities.setLightNavigationBar(getWindowView(), AndroidUtilities.computePerceivedBrightness(getNavigationBarColor(getThemedColor(Theme.key_windowBackgroundGray))) >= .721f);
            }
        }

        public int getBackgroundColor() {
            if (!SharedConfig.adaptableColorInBrowser) {
                return Theme.getColor(Theme.key_iv_navigationBackground);
            }
            final float page0Alpha = pages[0].getVisibility() != View.VISIBLE ? 0 : 1f - pages[0].getTranslationX() / pages[0].getWidth();
            return ColorUtils.blendARGB(pages[0].getBackgroundColor(), pages[1].getBackgroundColor(), 1f - page0Alpha);
        }

        public int getActionBarColor() {
            if (!SharedConfig.adaptableColorInBrowser) {
                return Theme.getColor(Theme.key_iv_background);
            }
            final float page0Alpha = pages[0].getVisibility() != View.VISIBLE ? 0 : 1f - pages[0].getTranslationX() / pages[0].getWidth();
            return ColorUtils.blendARGB(pages[0].getActionBarColor(), pages[1].getActionBarColor(), 1f - page0Alpha);
        }

        private boolean imageAtTop() {
            return false;
        }

        private int getListPaddingTop() {
            return dp(20);
        }

        public int getEmptyPadding() {
            return dp(16) + (containerView == null ? AndroidUtilities.displaySize.y : containerView.getHeight()) - (getListTop() - getListPaddingTop());
        }

        public void updateTranslation() {
            if (containerView == null) return;
            containerView.setTranslationY(getEmptyPadding() * Math.max(1f - openProgress, dismissingIntoTabs ? 0 : dismissProgress));
            windowView.invalidate();
        }

        public boolean attachedToActionBar;
        public boolean fullyAttachedToActionBar;
        public boolean nestedVerticalScroll;

        public class WindowView extends SizeNotifierFrameLayout implements BaseFragment.AttachedSheetWindow, BottomSheetTabsOverlay.SheetView {

            public WindowView(Context context) {
                super(context);
            }

            private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint headerBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final AnimatedFloat attachedActionBar = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            private boolean drawingFromOverlay;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                updateTranslation();
            }

            private final Path clipPath = new Path();
            private final RectF rect = new RectF();
            private final RectF rect2 = new RectF();

            public boolean isVisible() {
                final int top = getListTop() - getListPaddingTop();
                final int drawnTop = lerp(top, 0, Utilities.clamp01(this.attachedActionBar.get()));
                return drawnTop < getHeight();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (drawingFromOverlay) {
                    return;
                }

                final float open = Math.min(openProgress, 1f - dismissProgress);

                scrimPaint.setColor(Color.BLACK);
                scrimPaint.setAlpha((int) (0x60 * open * (1f - backProgress)));
                canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

                final int top = getListTop() - getListPaddingTop();
                final boolean attachedToActionBar = top < (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()) && open > .95f;
                if (Sheet.this.attachedToActionBar != attachedToActionBar) {
                    Sheet.this.attachedToActionBar = attachedToActionBar;
                    checkNavColor();
                }
                final float attachedActionBar = this.attachedActionBar.set(attachedToActionBar);
                if (Sheet.this.fullyAttachedToActionBar != (attachedActionBar >= .999f)) {
                    Sheet.this.fullyAttachedToActionBar = (attachedActionBar >= .999f);
                    checkFullyVisible();
                }
                final int drawnTop = lerp(top, 0, Utilities.clamp01(attachedActionBar));

                final float y = getEmptyPadding() * Math.max(1f - openProgress, dismissProgress);
                canvas.save();
                canvas.translate(getWidth() * backProgress, y);
                rect.set(0, drawnTop, getWidth(), getHeight() + dp(16));
                final float r = dp(24) * (1f - attachedActionBar);
                if (attachedActionBar < 1f) {
                    shadowPaint.setColor(0);
                    shadowPaint.setShadowLayer(dp(18), 0, -dp(3), Theme.multAlpha(Color.BLACK, .26f * open));
                    canvas.drawRoundRect(rect, r, r, shadowPaint);
                }

                if (r <= 0) {
                    canvas.clipRect(rect);
                } else {
                    clipPath.rewind();
                    clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }

                backgroundPaint.setColor(pages[1].getBackgroundColor());
                canvas.drawRect(rect, backgroundPaint);
                backgroundPaint.setColor(pages[0].getBackgroundColor());
                AndroidUtilities.rectTmp.set(rect);
                AndroidUtilities.rectTmp.left = pages[0].getX();
                canvas.drawRect(AndroidUtilities.rectTmp, backgroundPaint);

                actionBar.drawShadow = attachedToActionBar && top + getListPaddingTop() <= AndroidUtilities.statusBarHeight + currentHeaderHeight;
                if (attachedActionBar > 0) {
                    canvas.save();
                    float t = lerp(top + getListPaddingTop() + 1, 0, attachedActionBar);
                    canvas.translate(0, t);
                    actionBar.drawBackground(canvas, (top + getListPaddingTop() + 1) - t, 1f, attachedActionBar, true);
                    canvas.restore();
                }

                canvas.translate(0, -y);
                if (!AndroidUtilities.makingGlobalBlurBitmap && (!pages[0].isWeb() || canvas.isHardwareAccelerated())) {
                    super.dispatchDraw(canvas);
                }
                canvas.translate(0, y);

                if (attachedActionBar < 1f) {
                    final float handleAlpha = 1f - attachedActionBar;

                    final boolean dark = AndroidUtilities.computePerceivedBrightness(getBackgroundColor()) < .721f;
                    handlePaint.setColor(ColorUtils.blendARGB(Theme.multAlpha(dark ? Color.WHITE : Color.BLACK, .15f), Color.BLACK, attachedActionBar));
                    handlePaint.setAlpha((int) (handlePaint.getAlpha() * handleAlpha));
                    final float cx = getWidth() / 2f, cy = drawnTop + getListPaddingTop() / 2f - attachedActionBar * dp(8);
                    final float w = lerp(dp(32), dp(48), attachedActionBar);
                    rect.set(cx - w / 2f, cy - dp(2), cx + w / 2f, cy + dp(2));
                    canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, handlePaint);
                }

                canvas.restore();
            }

            @Override
            public void setDrawingFromOverlay(boolean value) {
                if (drawingFromOverlay != value) {
                    drawingFromOverlay = value;
                    invalidate();
                }
            }

            private RectF clipRect = new RectF();
            private Path clipPath2 = new Path();

            @Override
            public RectF getRect() {
                clipRect.set(0, (attachedToActionBar ? 0 : getListTop() - getListPaddingTop()) + getEmptyPadding() * Math.max(1f - openProgress, dismissProgress), getWidth(), getHeight());
                return clipRect;
            }

            @Override
            public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening) {
                clipRect.set(getRect());
                AndroidUtilities.lerp(clipRect, finalRect, progress, clipRect);

                final float open = Math.min(openProgress, 1f - dismissProgress) * (1f - progress);
                scrimPaint.setColor(Color.BLACK);
                scrimPaint.setAlpha((int) (0x60 * open * (1f - backProgress)));
                canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

                final float r = lerp(dp(16), dp(18), progress);

                backgroundPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                clipPath2.rewind();
                clipPath2.addRoundRect(clipRect, r, r, Path.Direction.CW);
                canvas.drawPath(clipPath2, backgroundPaint);

                if (getChildCount() == 1) {
                    if (attachedToActionBar) {
                        canvas.save();
                        canvas.clipPath(clipPath2);
                        canvas.translate(0, clipRect.top);
                        actionBar.draw(canvas);
                        canvas.restore();
                    }

                    View child = getChildAt(0);
                    canvas.save();
                    final float s = opening ? 1f : lerp(1f, .99f, progress);
                    if (Math.abs(s - 1f) > .01f) {
                        canvas.scale(s, s, clipRect.centerX(), clipRect.centerY());
                    }
                    canvas.clipPath(clipPath2);
                    if (Math.abs(s - 1f) > .01f) {
                        canvas.scale(1f / s, 1f / s, clipRect.centerX(), clipRect.centerY());
                    }
                    canvas.translate(0, -getListTop() + clipRect.top + (attachedToActionBar ? actionBar.getMeasuredHeight() : 0) * (1f - progress));
                    child.draw(canvas);
                    canvas.restore();
                }

                return r;
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < (attachedToActionBar ? 0 : getListTop())) {
                    dismiss(true);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
                return super.onNestedFling(target, velocityX, velocityY, consumed);
            }

            private boolean stoppedAtFling;

            @Override
            public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
                boolean r = super.onNestedPreFling(target, velocityX, velocityY);
                if (halfSize() && !textSelectionHelper.isInSelectionMode()) {
                    if (pages[0].isAtTop() && velocityY < -1000) {
                        dismiss(true);
                    } else {
                        animateDismiss(false, true, null);
                    }
                }
                if (velocityX != 0 || velocityY != 0) {
                    textSelectionHelper.cancelTextSelectionRunnable();
                }
                stoppedAtFling = true;
                return r;
            }

            @Override
            public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
            }

            @Override
            public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
                if (!nestedVerticalScroll) {
                    nestedVerticalScroll = dy != 0;
                }
                if (pages[0].isAtTop() && halfSize() && !textSelectionHelper.isInSelectionMode()) {
                    consumed[1] = Math.min((int) (getEmptyPadding() * dismissProgress), dy);
                    dismissProgress = Utilities.clamp(dismissProgress - (float) dy / getEmptyPadding(), 1, 0);
                    updateTranslation();
                    checkFullyVisible();
                }
                if (dx != 0 || dy != 0) {
                    textSelectionHelper.cancelTextSelectionRunnable();
                }
            }

            @Override
            public void onNestedScrollAccepted(View child, View target, int axes) {
                super.onNestedScrollAccepted(child, target, axes);
            }

            @Override
            public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
                stoppedAtFling = false;
                return halfSize() && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
            }

            @Override
            public void onStopNestedScroll(View child) {
                nestedVerticalScroll = false;
                if (halfSize() && !stoppedAtFling && !textSelectionHelper.isInSelectionMode()) {
                    if (dismissProgress > .25f) {
                        dismiss(true);
                    } else {
                        animateDismiss(false, true, null);
                    }
                }
                super.onStopNestedScroll(child);
            }
        }

        private float backProgress;

        public void setBackProgress(float progress) {
            backProgress = progress;
            windowView.invalidate();
            checkNavColor();
            checkFullyVisible();
        }

        public float getBackProgress() {
            return backProgress;
        }

        public ValueAnimator animateBackProgressTo(float to) {
            ValueAnimator anm = ValueAnimator.ofFloat(backProgress, to);
            anm.addUpdateListener(a -> setBackProgress((float) a.getAnimatedValue()));
            return anm;
        }

        private boolean lastVisible;
        @Override
        public void setLastVisible(boolean lastVisible) {
            this.lastVisible = lastVisible;
            pages[0].setLastVisible(lastVisible);
            pages[1].setLastVisible(false);
        }

        public void updateLastVisible() {
            pages[0].setLastVisible(lastVisible);
            pages[1].setLastVisible(false);
        }

        @Override
        public BulletinFactory getBulletinFactory() {
            final FrameLayout container;
            if (pages[0].isWeb()) {
                if (pages[0].getWebView() == null) return null;
                container = pages[0].webViewContainer;
            } else {
                if (pages[0].adapter.currentPage == null) return null;
                container = pages[0];
            }
            return BulletinFactory.of(container, getResourcesProvider());
        }
    }

    public static class ErrorContainer extends FrameLayout {

        public final LinearLayout layout;
        private boolean imageViewSet;
        private final BackupImageView imageView;
        private final TextView titleView;
        private final TextView descriptionView;
        private final TextView codeView;
        public final ButtonWithCounterView buttonView;

        public ErrorContainer(Context context) {
            super(context);
            setVisibility(GONE);

            layout = new LinearLayout(context);
            layout.setPadding(dp(32), dp(24), dp(32), dp(24));
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.LEFT);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new BackupImageView(context);
            layout.addView(imageView, LayoutHelper.createLinear(100, 100));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(0xFFFFFFFF);
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 4, 0, 2));

            descriptionView = new TextView(context);
            descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP,  15);
            descriptionView.setTextColor(0xFFFFFFFF);
            descriptionView.setSingleLine(false);
            descriptionView.setMaxLines(3);
            layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 1));

            codeView = new TextView(context);
            codeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP,  12);
            codeView.setTextColor(0xFFFFFFFF);
            codeView.setAlpha(.4f);
            layout.addView(codeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));

            buttonView = new ButtonWithCounterView(context, null);
            buttonView.setMinWidth(dp(140));
            buttonView.setText(LocaleController.getString(R.string.Refresh), false);
            layout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, Gravity.LEFT, 0, 12, 0, 0));
        }

        private ValueAnimator darkAnimator;
        private boolean dark = true;
        public void setDark(boolean dark, boolean animated) {
            if (this.dark == dark) return;
            this.dark = dark;
            if (darkAnimator != null) {
                darkAnimator.cancel();
            }
            if (animated) {
                darkAnimator = ValueAnimator.ofFloat(dark ? 0 : 1, dark ? 1 : 0);
                darkAnimator.addUpdateListener(anm -> {
                    float t = (float) anm.getAnimatedValue();
                    titleView.setTextColor(ColorUtils.blendARGB(0xFF000000, 0xFFFFFFFF, t));
                    descriptionView.setTextColor(ColorUtils.blendARGB(0xFF000000, 0xFFFFFFFF, t));
                    codeView.setTextColor(ColorUtils.blendARGB(0xFF000000, 0xFFFFFFFF, t));
                });
                darkAnimator.start();
            } else {
                titleView.setTextColor(!dark ? 0xFF000000 : 0xFFFFFFFF);
                descriptionView.setTextColor(!dark ? 0xFF000000 : 0xFFFFFFFF);
                codeView.setTextColor(!dark ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        public void set(String botName, String description) {
            titleView.setText(getString(R.string.WebErrorTitle));
            descriptionView.setText(AndroidUtilities.replaceTags(formatString(R.string.WebErrorInfoBot, botName)));
            codeView.setText(description);
        }

        public void set(String url, int code, String description) {
            titleView.setText(getString(R.string.WebErrorTitle));
            url = BotWebViewContainer.magic2tonsite(url);
            CharSequence cs = AndroidUtilities.replaceTags(url == null || Uri.parse(url) == null || Uri.parse(url).getAuthority() == null ? getString(R.string.WebErrorInfo) : formatString(R.string.WebErrorInfoDomain, Uri.parse(url).getAuthority()));
            cs = Emoji.replaceEmoji(cs, descriptionView.getPaint().getFontMetricsInt(), false);
            descriptionView.setText(cs);
            codeView.setText(description);
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            if (visibility == VISIBLE && !imageViewSet) {
                imageViewSet = true;
                MediaDataController.getInstance(UserConfig.selectedAccount).setPlaceholderImage(imageView, AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, "🧐", "100_100");
            }
        }
    }

    public void destroy() {
        for (int i = 0; i < pagesStack.size(); ++i) {
            Object obj = pagesStack.get(i);
            if (obj instanceof CachedWeb) {
                if (pages[0] != null && pages[0].web == obj) {
                    ((CachedWeb) obj).detach(pages[0]);
                }
                if (pages[1] != null && pages[1].web == obj) {
                    ((CachedWeb) obj).detach(pages[1]);
                }
                ((CachedWeb) obj).destroy();
            } else if (obj instanceof TLRPC.WebPage) {
                WebInstantView.recycle((TLRPC.WebPage) obj);
            }
        }
        pagesStack.clear();
        destroyArticleViewer();
    }

}

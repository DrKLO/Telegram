/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.MetricAffectingSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.ui.AspectRatioFrameLayout;
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Scroller;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.TextPaintSpan;
import org.telegram.ui.Components.TextPaintUrlSpan;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.WebPlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class ArticleViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

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

    private boolean isVisible;
    private boolean collapsed;
    private boolean attachedToWindow;

    private int animationInProgress;
    private Runnable animationEndRunnable;
    private long transitionAnimationStartTime;

    private TLRPC.WebPage currentPage;
    private ArrayList<TLRPC.WebPage> pagesStack = new ArrayList<>();

    private WindowManager.LayoutParams windowLayoutParams;
    private WindowView windowView;
    private View barBackground;
    private FrameLayout containerView;
    private View photoContainerBackground;
    private FrameLayoutDrawer photoContainerView;
    private WebpageAdapter adapter;
    private FrameLayout headerView;
    private ImageView backButton;
    private ImageView shareButton;
    private ActionBarMenuItem settingsButton;
    private FrameLayout shareContainer;
    private ContextProgressView progressView;
    private BackDrawable backDrawable;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Dialog visibleDialog;
    private Paint backgroundPaint;
    private Drawable layerShadowDrawable;
    private Paint scrimPaint;
    private AnimatorSet progressViewAnimation;

    private Paint headerPaint = new Paint();
    private Paint headerProgressPaint = new Paint();

    private ActionBarPopupWindow popupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private Rect popupRect;

    private WebPlayerView currentPlayingVideo;
    private WebPlayerView fullscreenedVideo;

    private Drawable slideDotDrawable;
    private Drawable slideDotBigDrawable;

    private TLRPC.TL_pageBlockChannel channelBlock;

    private int openUrlReqId;
    private int previewsReqId;

    private int currentHeaderHeight;

    private boolean checkingForLongPress = false;
    private CheckForLongPress pendingCheckForLongPress = null;
    private int pressCount = 0;
    private CheckForTap pendingCheckForTap = null;

    private TextPaintUrlSpan pressedLink;
    private int pressedLayoutY;
    private StaticLayout pressedLinkOwnerLayout;
    private View pressedLinkOwnerView;
    private boolean drawBlockSelection;
    private LinkPath urlPath = new LinkPath();

    private ArrayList<TLRPC.PageBlock> blocks = new ArrayList<>();
    private ArrayList<TLRPC.PageBlock> photoBlocks = new ArrayList<>();
    private HashMap<String, Integer> anchors = new HashMap<>();
    private HashMap<TLRPC.TL_pageBlockAudio, MessageObject> audioBlocks = new HashMap<>();
    private ArrayList<MessageObject> audioMessages = new ArrayList<>();

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

    private static Paint colorPaint;
    private static Paint selectorPaint;
    private final int fontSizeCount = 5;
    private int selectedFontSize = 2;
    private int selectedColor = 0;
    private int selectedFont = 0;
    private boolean nightModeEnabled;
    private ColorCell[] colorCells = new ColorCell[3];
    private ImageView nightModeImageView;
    private FrameLayout nightModeHintView;
    private FontCell[] fontCells = new FontCell[2];

    private int isRtl = -1;

    private class SizeChooseView extends View {

        private Paint paint;

        private int circleSize;
        private int gapSize;
        private int sideSide;
        private int lineSize;

        private boolean moving;
        private boolean startMoving;
        private float startX;

        private int startMovingQuality;

        public SizeChooseView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                for (int a = 0; a < fontSizeCount; a++) {
                    int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                    if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                        startMoving = a == selectedFontSize;
                        startX = x;
                        startMovingQuality = selectedFontSize;
                        break;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (startMoving) {
                    if (Math.abs(startX - x) >= AndroidUtilities.getPixelsInCM(0.5f, true)) {
                        moving = true;
                        startMoving = false;
                    }
                } else if (moving) {
                    for (int a = 0; a < fontSizeCount; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        int diff = lineSize / 2 + circleSize / 2 + gapSize;
                        if (x > cx - diff && x < cx + diff) {
                            if (selectedFontSize != a) {
                                selectedFontSize = a;
                                updatePaintSize();
                                invalidate();
                            }
                            break;
                        }
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (!moving) {
                    for (int a = 0; a < 5; a++) {
                        int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                        if (x > cx - AndroidUtilities.dp(15) && x < cx + AndroidUtilities.dp(15)) {
                            if (selectedFontSize != a) {
                                selectedFontSize = a;
                                updatePaintSize();
                                invalidate();
                            }
                            break;
                        }
                    }
                } else {
                    if (selectedFontSize != startMovingQuality) {
                        updatePaintSize();
                    }
                }
                startMoving = false;
                moving = false;
            }
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            circleSize = AndroidUtilities.dp(5);
            gapSize = AndroidUtilities.dp(2);
            sideSide = AndroidUtilities.dp(17);
            lineSize = (getMeasuredWidth() - circleSize * fontSizeCount - gapSize * 8 - sideSide * 2) / (fontSizeCount - 1);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cy = getMeasuredHeight() / 2;
            for (int a = 0; a < fontSizeCount; a++) {
                int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
                if (a <= selectedFontSize) {
                    paint.setColor(0xff1495e9);
                } else {
                    paint.setColor(0xffcccccc);
                }
                canvas.drawCircle(cx, cy, a == selectedFontSize ? AndroidUtilities.dp(4) : circleSize / 2, paint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    canvas.drawRect(x, cy - AndroidUtilities.dp(1), x + lineSize, cy + AndroidUtilities.dp(1), paint);
                }
            }
        }
    }

    public class ColorCell extends FrameLayout {

        private TextView textView;
        private int currentColor;
        private boolean selected;

        public ColorCell(Context context) {
            super(context);

            if (colorPaint == null) {
                colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                selectorPaint.setColor(0xff1495e9);
                selectorPaint.setStyle(Paint.Style.STROKE);
                selectorPaint.setStrokeWidth(AndroidUtilities.dp(2));
            }

            setBackgroundDrawable(Theme.createSelectorDrawable(0x0f000000, 2));

            setWillNotDraw(false);

            textView = new TextView(context);
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setPadding(0, 0, 0, AndroidUtilities.dp(1));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 17 + 36), 0, (LocaleController.isRTL ? 17 + 36 : 17), 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void setTextAndColor(String text, int color) {
            textView.setText(text);
            currentColor = color;
            invalidate();
        }

        public void select(boolean value) {
            if (selected == value) {
                return;
            }
            selected = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            colorPaint.setColor(currentColor);
            canvas.drawCircle(!LocaleController.isRTL ? AndroidUtilities.dp(28) : getMeasuredWidth() - AndroidUtilities.dp(28 + 20), getMeasuredHeight() / 2, AndroidUtilities.dp(10), colorPaint);
            if (selected) {
                selectorPaint.setStrokeWidth(AndroidUtilities.dp(2));
                selectorPaint.setColor(0xff1495e9);
                canvas.drawCircle(!LocaleController.isRTL ? AndroidUtilities.dp(28) : getMeasuredWidth() - AndroidUtilities.dp(28 + 20), getMeasuredHeight() / 2, AndroidUtilities.dp(10), selectorPaint);
            } else if (currentColor == 0xffffffff) {
                selectorPaint.setStrokeWidth(AndroidUtilities.dp(1));
                selectorPaint.setColor(0xffbababa);
                canvas.drawCircle(!LocaleController.isRTL ? AndroidUtilities.dp(28) : getMeasuredWidth() - AndroidUtilities.dp(28 + 20), getMeasuredHeight() / 2, AndroidUtilities.dp(9), selectorPaint);
            }
        }
    }

    public class FontCell extends FrameLayout {

        private TextView textView;
        private TextView textView2;

        public FontCell(Context context) {
            super(context);

            setBackgroundDrawable(Theme.createSelectorDrawable(0x0f000000, 2));

            textView = new TextView(context);
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 17 : 17 + 36), 0, (LocaleController.isRTL ? 17 + 36 : 17), 0));

            textView2 = new TextView(context);
            textView2.setTextColor(0xff212121);
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView2.setLines(1);
            textView2.setMaxLines(1);
            textView2.setSingleLine(true);
            textView2.setText("Aa");
            textView2.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 0, 17, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void select(boolean value) {
            textView2.setTextColor(value ? 0xff1495e9 : 0xff212121);
        }

        public void setTextAndTypeface(String text, Typeface typeface) {
            textView.setText(text);
            textView.setTypeface(typeface);
            textView2.setTypeface(typeface);
            invalidate();
        }
    }

    private class FrameLayoutDrawer extends FrameLayout {

        public FrameLayoutDrawer(Context context) {
            super(context);
        }

        /*@Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            boolean result = super.onInterceptTouchEvent(event);
            if (!result) {
                processTouchEvent(event);
                result = true;
            }
            return result;
        }*/

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            processTouchEvent(event);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawContent(canvas);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return child != aspectRatioFrameLayout && super.drawChild(canvas, child, drawingTime);
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

    private class WindowView extends FrameLayout {

        private Runnable attachRunnable;
        private boolean selfLayout;
        private int startedTrackingPointerId;
        private boolean maybeStartTracking;
        private boolean startedTracking;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker tracker;
        private boolean closeAnimationInProgress;
        private float innerTranslationX;
        private float alpha;

        public WindowView(Context context) {
            super(context);
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
                    barBackground.measure(View.MeasureSpec.makeMeasureSpec(insets.getSystemWindowInsetRight(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
                } else if (insets.getSystemWindowInsetLeft() != 0) {
                    barBackground.measure(View.MeasureSpec.makeMeasureSpec(insets.getSystemWindowInsetLeft(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
                } else {
                    barBackground.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(insets.getSystemWindowInsetBottom(), View.MeasureSpec.EXACTLY));
                }
            } else {
                setMeasuredDimension(widthSize, heightSize);
            }
            containerView.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
            photoContainerView.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
            photoContainerBackground.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
            fullscreenVideoContainer.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize, View.MeasureSpec.EXACTLY));
            ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            animatingImageView.measure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(layoutParams.height, View.MeasureSpec.AT_MOST));
        }

        @SuppressWarnings("DrawAllocation")
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (selfLayout) {
                return;
            }
            int x;
            if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                WindowInsets insets = (WindowInsets) lastInsets;
                x = insets.getSystemWindowInsetLeft();

                if (insets.getSystemWindowInsetRight() != 0) {
                    barBackground.layout(right - left - insets.getSystemWindowInsetRight(), 0, right - left, bottom - top);
                } else if (insets.getSystemWindowInsetLeft() != 0) {
                    barBackground.layout(0, 0, insets.getSystemWindowInsetLeft(), bottom - top);
                } else {
                    barBackground.layout(0, bottom - top - insets.getStableInsetBottom(), right - left, bottom - top);
                }
            } else {
                x = 0;
            }
            containerView.layout(x, 0, x + containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
            photoContainerView.layout(x, 0, x + photoContainerView.getMeasuredWidth(), photoContainerView.getMeasuredHeight());
            photoContainerBackground.layout(x, 0, x + photoContainerBackground.getMeasuredWidth(), photoContainerBackground.getMeasuredHeight());
            fullscreenVideoContainer.layout(x, 0, x + fullscreenVideoContainer.getMeasuredWidth(), fullscreenVideoContainer.getMeasuredHeight());
            animatingImageView.layout(0, 0, animatingImageView.getMeasuredWidth(), animatingImageView.getMeasuredHeight());
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

        public float getInnerTranslationX() {
            return innerTranslationX;
        }

        private void prepareForMoving(MotionEvent ev) {
            maybeStartTracking = false;
            startedTracking = true;
            startedTrackingX = (int) ev.getX();
        }

        public boolean handleTouchEvent(MotionEvent event) {
            if (!isPhotoVisible && !closeAnimationInProgress && fullscreenVideoContainer.getVisibility() != VISIBLE) {
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
                        containerView.setTranslationX(dx);
                        setInnerTranslationX(dx);
                    }
                } else if (event != null && event.getPointerId(0) == startedTrackingPointerId && (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (tracker == null) {
                        tracker = VelocityTracker.obtain();
                    }
                    tracker.computeCurrentVelocity(1000);
                    if (!startedTracking) {
                        float velX = tracker.getXVelocity();
                        float velY = tracker.getYVelocity();
                        if (velX >= 3500 && velX > Math.abs(velY)) {
                            prepareForMoving(event);
                        }
                    }
                    if (startedTracking) {
                        float x = containerView.getX();
                        AnimatorSet animatorSet = new AnimatorSet();
                        float velX = tracker.getXVelocity();
                        float velY = tracker.getYVelocity();
                        final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                        float distToMove;
                        if (!backAnimation) {
                            distToMove = containerView.getMeasuredWidth() - x;
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, "translationX", containerView.getMeasuredWidth()),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", (float) containerView.getMeasuredWidth())
                            );
                        } else {
                            distToMove = x;
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, "translationX", 0),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f)
                            );
                        }

                        animatorSet.setDuration(Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50));
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                if (!backAnimation) {
                                    saveCurrentPagePosition();
                                    onClosed();
                                }
                                startedTracking = false;
                                closeAnimationInProgress = false;
                            }
                        });
                        animatorSet.start();
                        closeAnimationInProgress = true;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                    }
                    if (tracker != null) {
                        tracker.recycle();
                        tracker = null;
                    }
                } else if (event == null) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    if (tracker != null) {
                        tracker.recycle();
                        tracker = null;
                    }
                }
                return startedTracking;
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(innerTranslationX, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        }

        @Override
        public void setAlpha(float value) {
            backgroundPaint.setAlpha((int) (255 * value));
            alpha = value;
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isVisible || alpha != 1.0f || innerTranslationX != 0);
            }
            invalidate();
        }

        @Override
        public float getAlpha() {
            return alpha;
        }
    }

    class CheckForLongPress implements Runnable {
        public int currentPressCount;

        public void run() {
            if (checkingForLongPress && windowView != null) {
                checkingForLongPress = false;
                windowView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (pressedLink != null) {
                    final String urlFinal = pressedLink.getUrl();
                    BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity);
                    builder.setTitle(urlFinal);
                    builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, final int which) {
                            if (parentActivity == null) {
                                return;
                            }
                            if (which == 0) {
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
                        }
                    });
                    showDialog(builder.create());
                    hideActionBar();
                    pressedLink = null;
                    pressedLinkOwnerLayout = null;
                    pressedLinkOwnerView.invalidate();
                } else if (pressedLinkOwnerLayout != null && pressedLinkOwnerView != null) {
                    int y = pressedLinkOwnerView.getTop() - AndroidUtilities.dp(54) + pressedLayoutY;
                    int x;
                    if (y < 0) {
                        y *= -1;
                    }
                    pressedLinkOwnerView.invalidate();
                    drawBlockSelection = true;
                    showPopup(pressedLinkOwnerView, Gravity.TOP, 0, y);
                    listView.setLayoutFrozen(true);
                    listView.setLayoutFrozen(false);
                }
            }
        }
    }

    private void showPopup(View parent, int gravity, int x, int y) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }

        if (popupLayout == null) {
            popupRect = new android.graphics.Rect();
            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity);
            popupLayout.setBackgroundDrawable(parentActivity.getResources().getDrawable(R.drawable.menu_copy));
            popupLayout.setAnimationEnabled(false);
            popupLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (popupWindow != null && popupWindow.isShowing()) {
                            v.getHitRect(popupRect);
                            if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                popupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            popupLayout.setDispatchKeyEventListener(new ActionBarPopupWindow.OnDispatchKeyEventListener() {
                @Override
                public void onDispatchKeyEvent(KeyEvent keyEvent) {
                    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                    }
                }
            });
            popupLayout.setShowedFromBotton(false);

            TextView deleteView = new TextView(parentActivity);
            deleteView.setTextColor(0xff000000);
            deleteView.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            deleteView.setGravity(Gravity.CENTER_VERTICAL);
            deleteView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            deleteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            deleteView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            deleteView.setText(LocaleController.getString("Copy", R.string.Copy).toUpperCase());
            deleteView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pressedLinkOwnerLayout == null) {
                        return;
                    }
                    AndroidUtilities.addToClipboard(pressedLinkOwnerLayout.getText());
                    Toast.makeText(parentActivity, LocaleController.getString("TextCopied", R.string.TextCopied), Toast.LENGTH_SHORT).show();
                    if (popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss(true);
                    }
                }
            });
            popupLayout.addView(deleteView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38));

            popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            popupWindow.setAnimationEnabled(false);
            popupWindow.setAnimationStyle(R.style.PopupAnimation);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setClippingEnabled(true);
            popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            popupWindow.getContentView().setFocusableInTouchMode(true);
            popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    if (pressedLinkOwnerView != null) {
                        pressedLinkOwnerLayout = null;
                        pressedLinkOwnerView.invalidate();
                        pressedLinkOwnerView = null;
                    }
                }
            });
        }

        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setFocusable(true);
        popupWindow.showAtLocation(parent, gravity, x, y);
        popupWindow.startAnimation();
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
            setRichTextParents(parentRichText, ((TLRPC.TL_textStrike) richText).text);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            setRichTextParents(richText, ((TLRPC.TL_textEmail) richText).text);
        } else if (richText instanceof TLRPC.TL_textUrl) {
            setRichTextParents(richText, ((TLRPC.TL_textUrl) richText).text);
        } else if (richText instanceof TLRPC.TL_textConcat) {
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                setRichTextParents(richText, richText.texts.get(a));
            }
        }
    }

    private void updateInterfaceForCurrentPage(boolean back) {
        if (currentPage == null || currentPage.cached_page == null) {
            return;
        }
        isRtl = -1;
        channelBlock = null;
        blocks.clear();
        photoBlocks.clear();
        audioBlocks.clear();
        audioMessages.clear();
        int numBlocks = 0;
        int count = currentPage.cached_page.blocks.size();
        for (int a = 0; a < count; a++) {
            TLRPC.PageBlock block = currentPage.cached_page.blocks.get(a);
            if (block instanceof TLRPC.TL_pageBlockUnsupported) {
                continue;
            } else if (block instanceof TLRPC.TL_pageBlockAnchor) {
                anchors.put(block.name.toLowerCase(), blocks.size());
                continue;
            } else if (block instanceof TLRPC.TL_pageBlockAudio) {

                TLRPC.TL_message message = new TLRPC.TL_message();
                message.out = true;
                message.id = block.mid = ((int) currentPage.id) + a;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = message.from_id = UserConfig.getClientUserId();
                message.date = (int) (System.currentTimeMillis() / 1000);
                message.message = "-1";
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.flags |= 3;
                message.media.document = getDocumentWithId(block.audio_id);
                message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                MessageObject messageObject = new MessageObject(message, null, false);
                audioMessages.add(messageObject);
                audioBlocks.put((TLRPC.TL_pageBlockAudio) block, messageObject);
            }
            setRichTextParents(null, block.text);
            setRichTextParents(null, block.caption);
            if (block instanceof TLRPC.TL_pageBlockAuthorDate) {
                setRichTextParents(null, ((TLRPC.TL_pageBlockAuthorDate) block).author);
            } else if (block instanceof TLRPC.TL_pageBlockCollage) {
                TLRPC.TL_pageBlockCollage innerBlock = (TLRPC.TL_pageBlockCollage) block;
                for (int i = 0; i < innerBlock.items.size(); i++) {
                    setRichTextParents(null, innerBlock.items.get(i).text);
                    setRichTextParents(null, innerBlock.items.get(i).caption);
                }
            } else if (block instanceof TLRPC.TL_pageBlockList) {
                TLRPC.TL_pageBlockList innerBlock = (TLRPC.TL_pageBlockList) block;
                for (int i = 0; i < innerBlock.items.size(); i++) {
                    setRichTextParents(null, innerBlock.items.get(i));
                }
            } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
                TLRPC.TL_pageBlockSlideshow innerBlock = (TLRPC.TL_pageBlockSlideshow) block;
                for (int i = 0; i < innerBlock.items.size(); i++) {
                    setRichTextParents(null, innerBlock.items.get(i).text);
                    setRichTextParents(null, innerBlock.items.get(i).caption);
                }
            }
            if (a == 0) {
                block.first = true;
                if (block instanceof TLRPC.TL_pageBlockCover && block.cover.caption != null && !(block.cover.caption instanceof TLRPC.TL_textEmpty) && count > 1) {
                    TLRPC.PageBlock next = currentPage.cached_page.blocks.get(1);
                    if (next instanceof TLRPC.TL_pageBlockChannel) {
                        channelBlock = (TLRPC.TL_pageBlockChannel) next;
                    }
                }
            } else if (a == 1 && channelBlock != null) {
                continue;
            }
            addAllMediaFromBlock(block);
            blocks.add(block);
            if (block instanceof TLRPC.TL_pageBlockEmbedPost) {
                if (!block.blocks.isEmpty()) {
                    block.level = -1;
                    for (int b = 0; b < block.blocks.size(); b++) {
                        TLRPC.PageBlock innerBlock = block.blocks.get(b);
                        if (innerBlock instanceof TLRPC.TL_pageBlockUnsupported) {
                            continue;
                        } else if (innerBlock instanceof TLRPC.TL_pageBlockAnchor) {
                            anchors.put(innerBlock.name.toLowerCase(), blocks.size());
                            continue;
                        }
                        innerBlock.level = 1;
                        if (b == block.blocks.size() - 1) {
                            innerBlock.bottom = true;
                        }
                        blocks.add(innerBlock);
                        addAllMediaFromBlock(innerBlock);
                    }
                }
                if (!(block.caption instanceof TLRPC.TL_textEmpty)) {
                    TLRPC.TL_pageBlockParagraph caption = new TLRPC.TL_pageBlockParagraph();
                    caption.caption = block.caption;
                    blocks.add(caption);
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (pagesStack.size() == 1 || back) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
            String key = "article" + currentPage.id;
            int position = preferences.getInt(key, -1);
            int offset;
            if (preferences.getBoolean(key + "r", true) == AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                offset = preferences.getInt(key + "o", 0) - listView.getPaddingTop();
            } else {
                offset = AndroidUtilities.dp(10);
            }
            if (position != -1) {
                layoutManager.scrollToPositionWithOffset(position, offset);
            }
        } else {
            layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    private void addPageToStack(TLRPC.WebPage webPage, String anchor) {
        saveCurrentPagePosition();
        currentPage = webPage;
        pagesStack.add(webPage);
        updateInterfaceForCurrentPage(false);

        if (anchor != null) {
            Integer row = anchors.get(anchor.toLowerCase());
            if (row != null) {
                layoutManager.scrollToPositionWithOffset(row, 0);
            }
        }
    }

    private boolean removeLastPageFromStack() {
        if (pagesStack.size() < 2) {
            return false;
        }
        pagesStack.remove(pagesStack.size() - 1);
        currentPage = pagesStack.get(pagesStack.size() - 1);
        updateInterfaceForCurrentPage(true);
        return true;
    }

    protected void startCheckLongPress() {
        if (checkingForLongPress) {
            return;
        }
        checkingForLongPress = true;
        if (pendingCheckForTap == null) {
            pendingCheckForTap = new CheckForTap();
        }
        windowView.postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    protected void cancelCheckLongPress() {
        checkingForLongPress = false;
        if (pendingCheckForLongPress != null) {
            windowView.removeCallbacks(pendingCheckForLongPress);
        }
        if (pendingCheckForTap != null) {
            windowView.removeCallbacks(pendingCheckForTap);
        }
    }

    private static final int TEXT_FLAG_REGULAR = 0;
    private static final int TEXT_FLAG_MEDIUM = 1;
    private static final int TEXT_FLAG_ITALIC = 2;
    private static final int TEXT_FLAG_MONO = 4;
    private static final int TEXT_FLAG_URL = 8;
    private static final int TEXT_FLAG_UNDERLINE = 16;
    private static final int TEXT_FLAG_STRIKE = 32;

    private static TextPaint audioTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static TextPaint errorTextPaint;
    private static HashMap<Integer, TextPaint> captionTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> titleTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> headerTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> subtitleTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> subheaderTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> authorTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> footerTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> paragraphTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> listTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> preformattedTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> quoteTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> subquoteTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> embedTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> slideshowTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> embedPostTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> embedPostCaptionTextPaints = new HashMap<>();
    private static HashMap<Integer, TextPaint> videoTextPaints = new HashMap<>();

    private static TextPaint embedPostAuthorPaint;
    private static TextPaint embedPostDatePaint;
    private static TextPaint channelNamePaint;

    private static TextPaint listTextPointerPaint;

    private static Paint preformattedBackgroundPaint;
    private static Paint quoteLinePaint;
    private static Paint dividerPaint;
    private static Paint urlPaint;

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
        } else if (richText instanceof TLRPC.TL_textUrl) {
            return getTextFlags(richText.parentRichText) | TEXT_FLAG_URL;
        } else if (richText != null) {
            return getTextFlags(richText.parentRichText);
        }
        return TEXT_FLAG_REGULAR;
    }

    private CharSequence getText(TLRPC.RichText parentRichText, TLRPC.RichText richText, TLRPC.PageBlock parentBlock) {
        if (richText instanceof TLRPC.TL_textFixed) {
            return getText(parentRichText, ((TLRPC.TL_textFixed) richText).text, parentBlock);
        } else if (richText instanceof TLRPC.TL_textItalic) {
            return getText(parentRichText, ((TLRPC.TL_textItalic) richText).text, parentBlock);
        } else if (richText instanceof TLRPC.TL_textBold) {
            return getText(parentRichText, ((TLRPC.TL_textBold) richText).text, parentBlock);
        } else if (richText instanceof TLRPC.TL_textUnderline) {
            return getText(parentRichText, ((TLRPC.TL_textUnderline) richText).text, parentBlock);
        } else if (richText instanceof TLRPC.TL_textStrike) {
            return getText(parentRichText, ((TLRPC.TL_textStrike) richText).text, parentBlock);
        } else if (richText instanceof TLRPC.TL_textEmail) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parentRichText, ((TLRPC.TL_textEmail) richText).text, parentBlock));
            MetricAffectingSpan innerSpans[] = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null, getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textUrl) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(getText(parentRichText, ((TLRPC.TL_textUrl) richText).text, parentBlock));
            MetricAffectingSpan innerSpans[] = spannableStringBuilder.getSpans(0, spannableStringBuilder.length(), MetricAffectingSpan.class);
            spannableStringBuilder.setSpan(new TextPaintUrlSpan(innerSpans == null || innerSpans.length == 0 ? getTextPaint(parentRichText, richText, parentBlock) : null, getUrl(richText)), 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannableStringBuilder;
        } else if (richText instanceof TLRPC.TL_textPlain) {
            return ((TLRPC.TL_textPlain) richText).text;
        } else if (richText instanceof TLRPC.TL_textEmpty) {
            return "";
        } else if (richText instanceof TLRPC.TL_textConcat) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            int count = richText.texts.size();
            for (int a = 0; a < count; a++) {
                TLRPC.RichText innerRichText = richText.texts.get(a);
                CharSequence innerText = getText(parentRichText, innerRichText, parentBlock);
                int flags = getTextFlags(innerRichText);
                int startLength = spannableStringBuilder.length();
                spannableStringBuilder.append(innerText);
                if (flags != 0 && !(innerText instanceof SpannableStringBuilder)) {
                    if ((flags & TEXT_FLAG_URL) != 0) {
                        String url = getUrl(innerRichText);
                        if (url == null) {
                            url = getUrl(parentRichText);
                        }
                        spannableStringBuilder.setSpan(new TextPaintUrlSpan(getTextPaint(parentRichText, innerRichText, parentBlock), url), startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else {
                        spannableStringBuilder.setSpan(new TextPaintSpan(getTextPaint(parentRichText, innerRichText, parentBlock)), startLength, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            return spannableStringBuilder;
        }
        return "not supported " + richText;
    }

    private String getUrl(TLRPC.RichText richText) {
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
        }  else if (richText instanceof TLRPC.TL_textUrl) {
            return ((TLRPC.TL_textUrl) richText).url;
        }
        return null;
    }

    private int getTextColor() {
        switch (getSelectedColor()) {
            case 0:
            case 1:
                return 0xff212121;
            case 2:
            default:
                return 0xff999999;
        }
    }

    private int getGrayTextColor() {
        switch (getSelectedColor()) {
            case 0:
                return 0xff838c96;
            case 1:
                return 0xff4d4b45;
            case 2:
            default:
                return 0xff666666;
        }
    }

    private TextPaint getTextPaint(TLRPC.RichText parentRichText, TLRPC.RichText richText, TLRPC.PageBlock parentBlock) {
        int flags = getTextFlags(richText);
        HashMap<Integer, TextPaint> currentMap = null;
        int textSize = AndroidUtilities.dp(14);
        int textColor = 0xffff0000;

        int additionalSize;
        if (selectedFontSize == 0) {
            additionalSize = -AndroidUtilities.dp(4);
        } else if (selectedFontSize == 1) {
            additionalSize = -AndroidUtilities.dp(2);
        } else if (selectedFontSize == 3) {
            additionalSize = AndroidUtilities.dp(2);
        } else if (selectedFontSize == 4) {
            additionalSize = AndroidUtilities.dp(4);
        } else {
            additionalSize = 0;
        }

        if (parentBlock instanceof TLRPC.TL_pageBlockPhoto) {
            currentMap = captionTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockTitle) {
            currentMap = titleTextPaints;
            textSize = AndroidUtilities.dp(24);
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
            textSize = AndroidUtilities.dp(21);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockHeader) {
            currentMap = headerTextPaints;
            textSize = AndroidUtilities.dp(21);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockSubheader) {
            currentMap = subheaderTextPaints;
            textSize = AndroidUtilities.dp(18);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockBlockquote || parentBlock instanceof TLRPC.TL_pageBlockPullquote) {
            if (parentBlock.text == parentRichText) {
                currentMap = quoteTextPaints;
                textSize = AndroidUtilities.dp(15);
                textColor = getTextColor();
            } else if (parentBlock.caption == parentRichText) {
                currentMap = subquoteTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getGrayTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockPreformatted) {
            currentMap = preformattedTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockParagraph) {
            if (parentBlock.caption == parentRichText) {
                currentMap = embedPostCaptionTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getGrayTextColor();
            } else {
                currentMap = paragraphTextPaints;
                textSize = AndroidUtilities.dp(16);
                textColor = getTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockList) {
            currentMap = listTextPaints;
            textSize = AndroidUtilities.dp(15);
            textColor = getTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockEmbed) {
            currentMap = embedTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockSlideshow) {
            currentMap = slideshowTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getGrayTextColor();
        } else if (parentBlock instanceof TLRPC.TL_pageBlockEmbedPost) {
            if (richText != null) {
                currentMap = embedPostTextPaints;
                textSize = AndroidUtilities.dp(14);
                textColor = getTextColor();
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockVideo || parentBlock instanceof TLRPC.TL_pageBlockAudio) {
            currentMap = videoTextPaints;
            textSize = AndroidUtilities.dp(14);
            textColor = getTextColor();
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
                if (selectedFont == 1 || parentBlock instanceof TLRPC.TL_pageBlockTitle || parentBlock instanceof TLRPC.TL_pageBlockHeader || parentBlock instanceof TLRPC.TL_pageBlockSubtitle || parentBlock instanceof TLRPC.TL_pageBlockSubheader) {
                    if ((flags & TEXT_FLAG_MEDIUM) != 0 && (flags & TEXT_FLAG_ITALIC) != 0) {
                        paint.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                    } else if ((flags & TEXT_FLAG_MEDIUM) != 0) {
                        paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
                    } else if ((flags & TEXT_FLAG_ITALIC) != 0) {
                        paint.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                    } else {
                        paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
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
            if ((flags & TEXT_FLAG_URL) != 0) {
                paint.setFlags(paint.getFlags() | TextPaint.UNDERLINE_TEXT_FLAG);
                textColor = getTextColor();
            }
            paint.setColor(textColor);
            currentMap.put(flags, paint);
        }
        paint.setTextSize(textSize + additionalSize);
        return paint;
    }

    private StaticLayout createLayoutForText(CharSequence plainText, TLRPC.RichText richText, int width, TLRPC.PageBlock parentBlock) {
        if (plainText == null && (richText == null || richText instanceof TLRPC.TL_textEmpty)) {
            return null;
        }

        int color = getSelectedColor();
        if (quoteLinePaint == null) {
            quoteLinePaint = new Paint();
            quoteLinePaint.setColor(getTextColor());

            preformattedBackgroundPaint = new Paint();
            if (color == 0) {
                preformattedBackgroundPaint.setColor(0xfff5f8fc);
            } else if (color == 1) {
                preformattedBackgroundPaint.setColor(0xffe5dec8);
            } else if (color == 2) {
                preformattedBackgroundPaint.setColor(0xff262626);
            }

            urlPaint = new Paint();
            if (color == 0) {
                urlPaint.setColor(0xffebebeb);
            } else if (color == 1) {
                urlPaint.setColor(0xffe5dec8);
            } else if (color == 2) {
                urlPaint.setColor(0xff262626);
            }
        }

        CharSequence text;
        if (plainText != null) {
            text = plainText;
        } else {
            text = getText(richText, richText, parentBlock);
        }
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        TextPaint paint;
        if (parentBlock instanceof TLRPC.TL_pageBlockEmbedPost && richText == null) {
            if (parentBlock.author == plainText) {
                if (embedPostAuthorPaint == null) {
                    embedPostAuthorPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    embedPostAuthorPaint.setColor(getTextColor());
                }
                embedPostAuthorPaint.setTextSize(AndroidUtilities.dp(15));
                paint = embedPostAuthorPaint;
            } else {
                if (embedPostDatePaint == null) {
                    embedPostDatePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                    if (color == 0) {
                        embedPostDatePaint.setColor(0xff8f97a0);
                    } else if (color == 1) {
                        embedPostDatePaint.setColor(0xff4d4b45);
                    } else if (color == 2) {
                        embedPostDatePaint.setColor(0xff666666);
                    }
                }
                embedPostDatePaint.setTextSize(AndroidUtilities.dp(14));
                paint = embedPostDatePaint;
            }
        } else if (parentBlock instanceof TLRPC.TL_pageBlockChannel) {
            if (channelNamePaint == null) {
                channelNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                channelNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            }
            if (channelBlock == null) {
                channelNamePaint.setColor(getTextColor());
            } else {
                channelNamePaint.setColor(0xffffffff);
            }
            channelNamePaint.setTextSize(AndroidUtilities.dp(15));
            paint = channelNamePaint;
        } else if (parentBlock instanceof TLRPC.TL_pageBlockList && plainText != null) {
            if (listTextPointerPaint == null) {
                listTextPointerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                listTextPointerPaint.setColor(getTextColor());
            }
            int additionalSize;
            if (selectedFontSize == 0) {
                additionalSize = -AndroidUtilities.dp(4);
            } else if (selectedFontSize == 1) {
                additionalSize = -AndroidUtilities.dp(2);
            } else if (selectedFontSize == 3) {
                additionalSize = AndroidUtilities.dp(2);
            } else if (selectedFontSize == 4) {
                additionalSize = AndroidUtilities.dp(4);
            } else {
                additionalSize = 0;
            }
            listTextPointerPaint.setTextSize(AndroidUtilities.dp(15) + additionalSize);
            paint = listTextPointerPaint;
        } else {
            paint = getTextPaint(richText, richText, parentBlock);
        }
        if (parentBlock instanceof TLRPC.TL_pageBlockPullquote || richText != null && parentBlock != null && !(parentBlock instanceof TLRPC.TL_pageBlockBlockquote) && richText == parentBlock.caption) {
            return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        } else {
            return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(4), false);
        }
    }

    private void drawLayoutLink(Canvas canvas, StaticLayout layout) {
        if (canvas == null || pressedLinkOwnerLayout != layout) {
            return;
        }
        if (pressedLink != null) {
            canvas.drawPath(urlPath, urlPaint);
        } else if (drawBlockSelection) {
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

    private boolean checkLayoutForLinks(MotionEvent event, View parentView, StaticLayout layout, int layoutX, int layoutY) {
        if (parentView == null || layout == null) {
            return false;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean removeLink = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (x >= layoutX && x <= layoutX + layout.getWidth() && y >= layoutY && y <= layoutY + layout.getHeight()) {
                pressedLinkOwnerLayout = layout;
                pressedLinkOwnerView = parentView;
                pressedLayoutY = layoutY;
                CharSequence text = layout.getText();
                if (text instanceof Spannable) {
                    try {
                        int checkX = x - layoutX;
                        int checkY = y - layoutY;
                        final int line = layout.getLineForVertical(checkY);
                        final int off = layout.getOffsetForHorizontal(line, checkX);
                        final float left = layout.getLineLeft(line);
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
                                    urlPath.setCurrentLayout(layout, pressedStart, 0);
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
                    int index;
                    boolean isAnchor = false;
                    final String anchor;
                    if ((index = url.lastIndexOf('#')) != -1) {
                        anchor = url.substring(index + 1);
                        if (url.toLowerCase().contains(currentPage.url.toLowerCase())) {
                            Integer row = anchors.get(anchor);
                            if (row != null) {
                                layoutManager.scrollToPositionWithOffset(row, 0);
                                isAnchor = true;
                            }
                        }
                    } else {
                        anchor = null;
                    }
                    if (!isAnchor) {
                        if (openUrlReqId == 0) {
                            showProgressView(true);
                            final TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
                            req.url = pressedLink.getUrl();
                            req.hash = 0;
                            openUrlReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(final TLObject response, TLRPC.TL_error error) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (openUrlReqId == 0) {
                                                return;
                                            }
                                            openUrlReqId = 0;
                                            showProgressView(false);
                                            if (isVisible) {
                                                if (response instanceof TLRPC.TL_webPage && ((TLRPC.TL_webPage) response).cached_page instanceof TLRPC.TL_pageFull) {
                                                    addPageToStack((TLRPC.TL_webPage) response, anchor);
                                                } else {
                                                    Browser.openUrl(parentActivity, req.url);
                                                }
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            removeLink = true;
        }
        if (removeLink) {
            pressedLink = null;
            pressedLinkOwnerLayout = null;
            pressedLinkOwnerView = null;
            parentView.invalidate();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress();
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }
        return pressedLinkOwnerLayout != null;
    }

    private TLRPC.Photo getPhotoWithId(long id) {
        if (currentPage == null || currentPage.cached_page == null) {
            return null;
        }
        if (currentPage.photo != null && currentPage.photo.id == id) {
            return currentPage.photo;
        }
        for (int a = 0; a < currentPage.cached_page.photos.size(); a++) {
            TLRPC.Photo photo = currentPage.cached_page.photos.get(a);
            if (photo.id == id) {
                return photo;
            }
        }
        return null;
    }

    private TLRPC.Document getDocumentWithId(long id) {
        if (currentPage == null || currentPage.cached_page == null) {
            return null;
        }
        if (currentPage.document != null && currentPage.document.id == id) {
            return currentPage.document;
        }
        for (int a = 0; a < currentPage.cached_page.documents.size(); a++) {
            TLRPC.Document document = currentPage.cached_page.documents.get(a);
            if (document.id == id) {
                return document;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    break;
                }
            }
        } else if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    radialProgressViews[a].setProgress(1.0f, true);
                    checkProgress(a, true);
                    if (a == 0 && isMediaVideo(currentIndex)) {
                        onActionClick(false);
                    }
                    break;
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String) args[0];
            for (int a = 0; a < 3; a++) {
                if (currentFileNames[a] != null && currentFileNames[a].equals(location)) {
                    Float progress = (Float) args[1];
                    radialProgressViews[a].setProgress(progress, true);
                }
            }
        } else if (id == NotificationCenter.emojiDidLoaded) {
            if (captionTextView != null) {
                captionTextView.invalidate();
            }
        } else if (id == NotificationCenter.messagePlayingDidStarted) {
            MessageObject messageObject = (MessageObject) args[0];

            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof BlockAudioCell) {
                        BlockAudioCell cell = (BlockAudioCell) view;
                        cell.updateButtonState(false);
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof BlockAudioCell) {
                        BlockAudioCell cell = (BlockAudioCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null) {
                            cell.updateButtonState(false);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = listView.getChildAt(a);
                    if (view instanceof BlockAudioCell) {
                        BlockAudioCell cell = (BlockAudioCell) view;
                        MessageObject playing = cell.getMessageObject();
                        if (playing != null && playing.getId() == mid) {
                            MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                            if (player != null) {
                                playing.audioProgress = player.audioProgress;
                                playing.audioProgressSec = player.audioProgressSec;
                                cell.updatePlayingMessageProgress();
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updatePaintSize() {
        ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putInt("font_size", selectedFontSize).commit();
        adapter.notifyDataSetChanged();
    }

    private void updatePaintFonts() {
        ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putInt("font_type", selectedFont).commit();
        Typeface typefaceNormal = selectedFont == 0 ? Typeface.DEFAULT : Typeface.SERIF;
        Typeface typefaceItalic = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/ritalic.ttf") : Typeface.create("serif", Typeface.ITALIC);
        Typeface typefaceBold = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/rmedium.ttf") : Typeface.create("serif", Typeface.BOLD);
        Typeface typefaceBoldItalic = selectedFont == 0 ? AndroidUtilities.getTypeface("fonts/rmediumitalic.ttf") : Typeface.create("serif", Typeface.BOLD_ITALIC);

        for (HashMap.Entry<Integer, TextPaint> entry : quoteTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : preformattedTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : paragraphTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : listTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedPostTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : videoTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : captionTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : authorTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : footerTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : subquoteTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedPostCaptionTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
        for (HashMap.Entry<Integer, TextPaint> entry : slideshowTextPaints.entrySet()) {
            updateFontEntry(entry, typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic);
        }
    }

    private void updateFontEntry(HashMap.Entry<Integer, TextPaint> entry, Typeface typefaceNormal, Typeface typefaceBoldItalic, Typeface typefaceBold, Typeface typefaceItalic) {
        Integer flags = entry.getKey();
        TextPaint paint = entry.getValue();
        if ((flags & TEXT_FLAG_MEDIUM) != 0 && (flags & TEXT_FLAG_ITALIC) != 0) {
            paint.setTypeface(typefaceBoldItalic);
        } else if ((flags & TEXT_FLAG_MEDIUM) != 0) {
            paint.setTypeface(typefaceBold);
        } else if ((flags & TEXT_FLAG_ITALIC) != 0) {
            paint.setTypeface(typefaceItalic);
        } else {
            paint.setTypeface(typefaceNormal);
        }
    }

    private int getSelectedColor() {
        int currentColor = selectedColor;
        if (nightModeEnabled && currentColor != 2) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 22 && hour <= 24 || hour >= 0 && hour <= 6) {
                currentColor = 2;
            }
        }
        return currentColor;
    }

    private void updatePaintColors() {
        ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putInt("font_color", selectedColor).commit();
        int currentColor = getSelectedColor();
        if (currentColor == 0) {
            backgroundPaint.setColor(0xffffffff);
            listView.setGlowColor(0xfff5f6f7);
        } else if (currentColor == 1) {
            backgroundPaint.setColor(0xfff5efdc);
            listView.setGlowColor(0xfff5efdc);
        } else if (currentColor == 2) {
            backgroundPaint.setColor(0xff141414);
            listView.setGlowColor(0xff141414);
        }

        for (int a = 0; a < Theme.chat_ivStatesDrawable.length; a++) {
            Theme.setCombinedDrawableColor(Theme.chat_ivStatesDrawable[a][0], getTextColor(), false);
            Theme.setCombinedDrawableColor(Theme.chat_ivStatesDrawable[a][0], getTextColor(), true);
            Theme.setCombinedDrawableColor(Theme.chat_ivStatesDrawable[a][1], getTextColor(), false);
            Theme.setCombinedDrawableColor(Theme.chat_ivStatesDrawable[a][1], getTextColor(), true);
        }

        if (quoteLinePaint != null) {
            quoteLinePaint.setColor(getTextColor());
        }
        if (listTextPointerPaint != null) {
            listTextPointerPaint.setColor(getTextColor());
        }
        if (preformattedBackgroundPaint != null) {
            if (currentColor == 0) {
                preformattedBackgroundPaint.setColor(0xfff5f8fc);
            } else if (currentColor == 1) {
                preformattedBackgroundPaint.setColor(0xffe5dec8);
            } else if (currentColor == 2) {
                preformattedBackgroundPaint.setColor(0xff262626);
            }
        }
        if (urlPaint != null) {
            if (currentColor == 0) {
                urlPaint.setColor(0xffebebeb);
            } else if (currentColor == 1) {
                urlPaint.setColor(0xffe5dec8);
            } else if (currentColor == 2) {
                urlPaint.setColor(0xff262626);
            }
        }

        if (embedPostAuthorPaint != null) {
            embedPostAuthorPaint.setColor(getTextColor());
        }
        if (channelNamePaint != null) {
            if (channelBlock == null) {
                channelNamePaint.setColor(getTextColor());
            } else {
                channelNamePaint.setColor(0xffffffff);
            }
        }
        if (embedPostDatePaint != null) {
            if (currentColor == 0) {
                embedPostDatePaint.setColor(0xff8f97a0);
            } else if (currentColor == 1) {
                embedPostDatePaint.setColor(0xff4d4b45);
            } else if (currentColor == 2) {
                embedPostDatePaint.setColor(0xff666666);
            }
        }
        if (dividerPaint != null) {
            if (currentColor == 0) {
                dividerPaint.setColor(0xffcdd1d5);
            } else if (currentColor == 1) {
                dividerPaint.setColor(0xffc1baa5);
            } else if (currentColor == 2) {
                dividerPaint.setColor(0xff444444);
            }
        }

        for (HashMap.Entry<Integer, TextPaint> entry : titleTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : subtitleTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : headerTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : subheaderTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : quoteTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : preformattedTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : paragraphTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : listTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedPostTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : videoTextPaints.entrySet()) {
            entry.getValue().setColor(getTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : captionTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : authorTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : footerTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : subquoteTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedPostCaptionTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : embedTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
        for (HashMap.Entry<Integer, TextPaint> entry : slideshowTextPaints.entrySet()) {
            entry.getValue().setColor(getGrayTextColor());
        }
    }

    public void setParentActivity(Activity activity, BaseFragment fragment) {
        parentFragment = fragment;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidStarted);
        if (parentActivity == activity) {
            updatePaintColors();
            return;
        }
        parentActivity = activity;

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE);
        selectedFontSize = sharedPreferences.getInt("font_size", 2);
        selectedFont = sharedPreferences.getInt("font_type", 0);
        selectedColor = sharedPreferences.getInt("font_color", 0);
        nightModeEnabled = sharedPreferences.getBoolean("nightModeEnabled", false);

        backgroundPaint = new Paint();

        layerShadowDrawable = activity.getResources().getDrawable(R.drawable.layer_shadow);
        slideDotDrawable = activity.getResources().getDrawable(R.drawable.slide_dot_small);
        slideDotBigDrawable = activity.getResources().getDrawable(R.drawable.slide_dot_big);
        scrimPaint = new Paint();

        windowView = new WindowView(activity);
        windowView.setWillNotDraw(false);
        windowView.setClipChildren(true);
        windowView.setFocusable(false);

        containerView = new FrameLayout(activity);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        containerView.setFitsSystemWindows(true);
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @SuppressLint("NewApi")
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    WindowInsets oldInsets = (WindowInsets) lastInsets;
                    lastInsets = insets;
                    if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                        windowView.requestLayout();
                    }
                    return insets.consumeSystemWindowInsets();
                }
            });
        }
        containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);

        photoContainerBackground = new View(activity);
        photoContainerBackground.setVisibility(View.INVISIBLE);
        photoContainerBackground.setBackgroundDrawable(photoBackgroundDrawable);
        windowView.addView(photoContainerBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        animatingImageView = new ClippingImageView(activity);
        animatingImageView.setAnimationValues(animationValues);
        animatingImageView.setVisibility(View.GONE);
        windowView.addView(animatingImageView, LayoutHelper.createFrame(40, 40));

        photoContainerView = new FrameLayoutDrawer(activity) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int y = bottom - top - captionTextView.getMeasuredHeight();
                if (bottomLayout.getVisibility() == VISIBLE) {
                    y -= bottomLayout.getMeasuredHeight();
                }
                captionTextView.layout(0, y, captionTextView.getMeasuredWidth(), y + captionTextView.getMeasuredHeight());
            }
        };
        photoContainerView.setVisibility(View.INVISIBLE);
        photoContainerView.setWillNotDraw(false);
        windowView.addView(photoContainerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        fullscreenVideoContainer = new FrameLayout(activity);
        fullscreenVideoContainer.setBackgroundColor(0xff000000);
        fullscreenVideoContainer.setVisibility(View.INVISIBLE);
        windowView.addView(fullscreenVideoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fullscreenAspectRatioView = new AspectRatioFrameLayout(activity);
        fullscreenAspectRatioView.setVisibility(View.GONE);
        fullscreenVideoContainer.addView(fullscreenAspectRatioView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        fullscreenTextureView = new TextureView(activity);

        if (Build.VERSION.SDK_INT >= 21) {
            barBackground = new View(activity);
            barBackground.setBackgroundColor(0xff000000);
            windowView.addView(barBackground);
        }

        listView = new RecyclerListView(activity) {
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
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(parentActivity, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new WebpageAdapter(parentActivity));
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(56), 0, 0);
        listView.setTopGlowOffset(AndroidUtilities.dp(56));
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                return false;
            }
        });
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == blocks.size() && currentPage != null) {
                    if (previewsReqId != 0) {
                        return;
                    }
                    TLObject object = MessagesController.getInstance().getUserOrChat("previews");
                    if (object instanceof TLRPC.TL_user) {
                        openPreviewsChat((TLRPC.User) object, currentPage.id);
                    } else {
                        final long pageId = currentPage.id;
                        showProgressView(true);
                        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                        req.username = "previews";
                        previewsReqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(final TLObject response, final TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (previewsReqId == 0) {
                                            return;
                                        }
                                        previewsReqId = 0;
                                        showProgressView(false);
                                        if (response != null) {
                                            TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                            MessagesController.getInstance().putUsers(res.users, false);
                                            MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, true);
                                            if (!res.users.isEmpty()) {
                                                openPreviewsChat(res.users.get(0), pageId);
                                            }
                                        }
                                    }
                                });
                            }
                        });
                    }
                } else if (position >= 0 && position < blocks.size()) {
                    TLRPC.PageBlock pageBlock = blocks.get(position);
                    if (pageBlock instanceof TLRPC.TL_pageBlockChannel) {
                        MessagesController.openByUserName(pageBlock.channel.username, parentFragment, 2);
                        close(false, true);
                    }
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() == 0) {
                    return;
                }
                headerView.invalidate();
                checkScroll(dy);
            }
        });

        headerPaint.setColor(0xff000000);
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
                int first = layoutManager.findFirstVisibleItemPosition();
                int last = layoutManager.findLastVisibleItemPosition();
                int count = layoutManager.getItemCount();
                View view;
                if (last >= count - 2) {
                    view = layoutManager.findViewByPosition(count - 2);
                } else {
                    view = layoutManager.findViewByPosition(first);
                }
                if (view == null) {
                    return;
                }

                float itemProgress = width / (float) (count - 1);

                int childCount = layoutManager.getChildCount();

                float viewHeight = view.getMeasuredHeight();
                float viewProgress;
                if (last >= count - 2) {
                    viewProgress = (count - 2 - first) * itemProgress * (listView.getMeasuredHeight() - view.getTop()) / viewHeight;
                } else {
                    viewProgress = itemProgress * (1.0f - (Math.min(0, view.getTop() - listView.getPaddingTop()) + viewHeight) / viewHeight);
                }
                float progress = first * itemProgress + viewProgress;

                canvas.drawRect(0, 0, progress, height, headerProgressPaint);
            }
        };
        headerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        headerView.setWillNotDraw(false);
        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56));

        backButton = new ImageView(activity);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backDrawable = new BackDrawable(false);
        backDrawable.setAnimationTime(200.0f);
        backDrawable.setColor(0xffb3b3b3);
        backDrawable.setRotated(false);
        backButton.setImageDrawable(backDrawable);
        backButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        headerView.addView(backButton, LayoutHelper.createFrame(54, 56));
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*if (collapsed) {
                    uncollapse();
                } else {
                    collapse();
                }*/
                close(true, true);
            }
        });

        LinearLayout settingsContainer = new LinearLayout(parentActivity);
        settingsContainer.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        for (int a = 0; a < 3; a++) {
            colorCells[a] = new ColorCell(parentActivity);
            switch (a) {
                case 0:
                    nightModeImageView = new ImageView(parentActivity);
                    nightModeImageView.setScaleType(ImageView.ScaleType.CENTER);
                    nightModeImageView.setImageResource(R.drawable.moon);
                    nightModeImageView.setColorFilter(new PorterDuffColorFilter(nightModeEnabled && selectedColor != 2 ? 0xff1495e9 : 0xffcccccc, PorterDuff.Mode.MULTIPLY));
                    nightModeImageView.setBackgroundDrawable(Theme.createSelectorDrawable(0x0f000000));
                    colorCells[a].addView(nightModeImageView, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
                    nightModeImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            nightModeEnabled = !nightModeEnabled;
                            ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().putBoolean("nightModeEnabled", nightModeEnabled).commit();
                            updateNightModeButton();
                            updatePaintColors();
                            adapter.notifyDataSetChanged();
                            if (nightModeEnabled) {
                                showNightModeHint();
                            }
                        }
                    });
                    colorCells[a].setTextAndColor(LocaleController.getString("ColorWhite", R.string.ColorWhite), 0xffffffff);
                    break;
                case 1:
                    colorCells[a].setTextAndColor(LocaleController.getString("ColorSepia", R.string.ColorSepia), 0xffeae5c9);
                    break;
                case 2:
                    colorCells[a].setTextAndColor(LocaleController.getString("ColorDark", R.string.ColorDark), 0xff232323);
                    break;
            }
            colorCells[a].select(a == selectedColor);
            colorCells[a].setTag(a);
            colorCells[a].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int num = (Integer) v.getTag();
                    selectedColor = num;
                    for (int a = 0; a < 3; a++) {
                        colorCells[a].select(a == num);
                    }
                    updateNightModeButton();
                    updatePaintColors();
                    adapter.notifyDataSetChanged();
                }
            });
            settingsContainer.addView(colorCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        updateNightModeButton();
        View divider = new View(parentActivity);
        divider.setBackgroundColor(0xffe0e0e0);
        settingsContainer.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 15, 4, 15, 4));
        divider.getLayoutParams().height = 1;

        for (int a = 0; a < 2; a++) {
            fontCells[a] = new FontCell(parentActivity);
            switch (a) {
                case 0:
                    fontCells[a].setTextAndTypeface("Roboto", Typeface.DEFAULT);
                    break;
                case 1:
                    fontCells[a].setTextAndTypeface("Serif", Typeface.SERIF);
                    break;
            }
            fontCells[a].select(a == selectedFont);
            fontCells[a].setTag(a);
            fontCells[a].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int num = (Integer) v.getTag();
                    selectedFont = num;
                    for (int a = 0; a < 2; a++) {
                        fontCells[a].select(a == num);
                    }
                    updatePaintFonts();
                    adapter.notifyDataSetChanged();
                }
            });
            settingsContainer.addView(fontCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
        divider = new View(parentActivity);
        divider.setBackgroundColor(0xffe0e0e0);
        settingsContainer.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 15, 4, 15, 4));
        divider.getLayoutParams().height = 1;

        TextView textView = new TextView(parentActivity);
        textView.setTextColor(0xff212121);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        textView.setText(LocaleController.getString("FontSize", R.string.FontSize));
        settingsContainer.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 12, 17, 0));

        SizeChooseView sizeChooseView = new SizeChooseView(parentActivity);
        settingsContainer.addView(sizeChooseView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 1));

        settingsButton = new ActionBarMenuItem(parentActivity, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, 0xffffffff);
        settingsButton.setPopupAnimationEnabled(false);
        settingsButton.setLayoutInScreen(true);
        textView = new TextView(parentActivity);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setText("Aa");
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextColor(0xffb3b3b3);
        textView.setGravity(Gravity.CENTER);
        settingsButton.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        settingsButton.addSubItem(settingsContainer, AndroidUtilities.dp(220), LayoutHelper.WRAP_CONTENT);
        settingsButton.redrawPopup(0xffffffff);
        headerView.addView(settingsButton, LayoutHelper.createFrame(48, 56, Gravity.TOP | Gravity.RIGHT, 0, 0, 56, 0));

        shareContainer = new FrameLayout(activity);
        headerView.addView(shareContainer, LayoutHelper.createFrame(48, 56, Gravity.TOP | Gravity.RIGHT));
        shareContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage == null || parentActivity == null) {
                    return;
                }
                showDialog(new ShareAlert(parentActivity, null, currentPage.url, false, currentPage.url, true));
                hideActionBar();
            }
        });

        shareButton = new ImageView(activity);
        shareButton.setScaleType(ImageView.ScaleType.CENTER);
        shareButton.setImageResource(R.drawable.ic_share_article);
        shareButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        shareContainer.addView(shareButton, LayoutHelper.createFrame(48, 56));

        progressView = new ContextProgressView(activity, 2);
        progressView.setVisibility(View.GONE);
        shareContainer.addView(progressView, LayoutHelper.createFrame(48, 56));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        if (progressDrawables == null) {
            progressDrawables = new Drawable[4];
            progressDrawables[0] = parentActivity.getResources().getDrawable(R.drawable.circle_big);
            progressDrawables[1] = parentActivity.getResources().getDrawable(R.drawable.cancel_big);
            progressDrawables[2] = parentActivity.getResources().getDrawable(R.drawable.load_big);
            progressDrawables[3] = parentActivity.getResources().getDrawable(R.drawable.play_big);
        }

        scroller = new Scroller(activity);

        blackPaint.setColor(0xff000000);

        actionBar = new ActionBar(activity);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(false);
        actionBar.setTitleColor(0xffffffff);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, 1, 1));
        photoContainerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true);
                } else if (id == gallery_menu_save) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                        return;
                    }
                    File f = getMediaFile(currentIndex);
                    if (f != null && f.exists()) {
                        MediaController.saveFile(f.toString(), parentActivity, isMediaVideo(currentIndex) ? 1 : 0, null, null);
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                        showDialog(builder.create());
                    }
                } else if (id == gallery_menu_share) {
                    onSharePressed();
                } else if (id == gallery_menu_openin) {
                    try {
                        AndroidUtilities.openForView(getMedia(currentIndex), parentActivity);
                        closePhoto(false);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }

            @Override
            public boolean canOpenMenu() {
                File f = getMediaFile(currentIndex);
                return f != null && f.exists();
            }
        });

        ActionBarMenu menu = actionBar.createMenu();

        menu.addItem(gallery_menu_share, R.drawable.share);
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setLayoutInScreen(true);
        menuItem.addSubItem(gallery_menu_openin, LocaleController.getString("OpenInExternalApp", R.string.OpenInExternalApp));
        //menuItem.addSubItem(gallery_menu_share, LocaleController.getString("ShareFile", R.string.ShareFile), 0);
        menuItem.addSubItem(gallery_menu_save, LocaleController.getString("SaveToGallery", R.string.SaveToGallery));

        bottomLayout = new FrameLayout(parentActivity);
        bottomLayout.setBackgroundColor(0x7f000000);
        photoContainerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        captionTextViewOld = new TextView(activity);
        captionTextViewOld.setMaxLines(10);
        captionTextViewOld.setBackgroundColor(0x7f000000);
        captionTextViewOld.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        captionTextViewOld.setLinkTextColor(0xffffffff);
        captionTextViewOld.setTextColor(0xffffffff);
        captionTextViewOld.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        captionTextViewOld.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        captionTextViewOld.setVisibility(View.INVISIBLE);
        photoContainerView.addView(captionTextViewOld, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        captionTextView = captionTextViewNew = new TextView(activity);
        captionTextViewNew.setMaxLines(10);
        captionTextViewNew.setBackgroundColor(0x7f000000);
        captionTextViewNew.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        captionTextViewNew.setLinkTextColor(0xffffffff);
        captionTextViewNew.setTextColor(0xffffffff);
        captionTextViewNew.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        captionTextViewNew.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        captionTextViewNew.setVisibility(View.INVISIBLE);
        photoContainerView.addView(captionTextViewNew, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        radialProgressViews[0] = new RadialProgressView(activity, photoContainerView);
        radialProgressViews[0].setBackgroundState(0, false);
        radialProgressViews[1] = new RadialProgressView(activity, photoContainerView);
        radialProgressViews[1].setBackgroundState(0, false);
        radialProgressViews[2] = new RadialProgressView(activity, photoContainerView);
        radialProgressViews[2].setBackgroundState(0, false);

        videoPlayerSeekbar = new SeekBar(activity);
        videoPlayerSeekbar.setColors(0x66ffffff, 0xffffffff, 0xffffffff);
        videoPlayerSeekbar.setDelegate(new SeekBar.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (videoPlayer != null) {
                    videoPlayer.seekTo((int) (progress * videoPlayer.getDuration()));
                }
            }
        });

        videoPlayerControlFrameLayout = new FrameLayout(activity) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                if (videoPlayerSeekbar.onTouch(event.getAction(), event.getX() - AndroidUtilities.dp(48), event.getY())) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                long duration;
                if (videoPlayer != null) {
                    duration = videoPlayer.getDuration();
                    if (duration == C.TIME_UNSET) {
                        duration = 0;
                    }
                } else {
                    duration = 0;
                }
                duration /= 1000;
                int size = (int) Math.ceil(videoPlayerTime.getPaint().measureText(String.format("%02d:%02d / %02d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
                videoPlayerSeekbar.setSize(getMeasuredWidth() - AndroidUtilities.dp(48 + 16) - size, getMeasuredHeight());
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                float progress = 0;
                if (videoPlayer != null) {
                    progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                }
                videoPlayerSeekbar.setProgress(progress);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(48), 0);
                videoPlayerSeekbar.draw(canvas);
                canvas.restore();
            }
        };
        videoPlayerControlFrameLayout.setWillNotDraw(false);
        bottomLayout.addView(videoPlayerControlFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        videoPlayButton = new ImageView(activity);
        videoPlayButton.setScaleType(ImageView.ScaleType.CENTER);
        videoPlayerControlFrameLayout.addView(videoPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));
        videoPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoPlayer != null) {
                    if (isPlaying) {
                        videoPlayer.pause();
                    } else {
                        videoPlayer.play();
                    }
                }
            }
        });

        videoPlayerTime = new TextView(activity);
        videoPlayerTime.setTextColor(0xffffffff);
        videoPlayerTime.setGravity(Gravity.CENTER_VERTICAL);
        videoPlayerTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        videoPlayerControlFrameLayout.addView(videoPlayerTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 8, 0));

        gestureDetector = new GestureDetector(activity, this);
        gestureDetector.setOnDoubleTapListener(this);

        centerImage.setParentView(photoContainerView);
        centerImage.setCrossfadeAlpha((byte) 2);
        centerImage.setInvalidateAll(true);
        leftImage.setParentView(photoContainerView);
        leftImage.setCrossfadeAlpha((byte) 2);
        leftImage.setInvalidateAll(true);
        rightImage.setParentView(photoContainerView);
        rightImage.setCrossfadeAlpha((byte) 2);
        rightImage.setInvalidateAll(true);

        updatePaintColors();
    }

    private void showNightModeHint() {
        if (parentActivity == null || nightModeHintView != null || !nightModeEnabled) {
            return;
        }
        nightModeHintView = new FrameLayout(parentActivity);
        nightModeHintView.setBackgroundColor(0xff333333);
        containerView.addView(nightModeHintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));

        ImageView nightModeImageView = new ImageView(parentActivity);
        nightModeImageView.setScaleType(ImageView.ScaleType.CENTER);
        nightModeImageView.setImageResource(R.drawable.moon);
        nightModeHintView.addView(nightModeImageView, LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        TextView textView = new TextView(parentActivity);
        textView.setText(LocaleController.getString("InstantViewNightMode", R.string.InstantViewNightMode));
        textView.setTextColor(0xffffffff);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nightModeHintView.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 56, 11, 10, 12));

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(nightModeHintView, "translationY", AndroidUtilities.dp(100), 0));
        animatorSet.setInterpolator(new DecelerateInterpolator(1.5f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(ObjectAnimator.ofFloat(nightModeHintView, "translationY", AndroidUtilities.dp(100)));
                        animatorSet.setInterpolator(new DecelerateInterpolator(1.5f));
                        animatorSet.setDuration(250);
                        animatorSet.start();
                    }
                }, 3000);
            }
        });
        animatorSet.setDuration(250);
        animatorSet.start();
    }

    private void updateNightModeButton() {
        nightModeImageView.setEnabled(selectedColor != 2);
        nightModeImageView.setAlpha(selectedColor == 2 ? 0.5f : 1.0f);
        nightModeImageView.setColorFilter(new PorterDuffColorFilter(nightModeEnabled && selectedColor != 2 ? 0xff1495e9 : 0xffcccccc, PorterDuff.Mode.MULTIPLY));
    }

    private void checkScroll(int dy) {
        int maxHeight = AndroidUtilities.dp(56);
        int minHeight = Math.max(AndroidUtilities.statusBarHeight, AndroidUtilities.dp(24));
        float heightDiff = maxHeight - minHeight;
        int newHeight = currentHeaderHeight - dy;
        if (newHeight < minHeight) {
            newHeight = minHeight;
        } else if (newHeight > maxHeight) {
            newHeight = maxHeight;
        }
        currentHeaderHeight = newHeight;
        float scale = 0.8f + (currentHeaderHeight - minHeight) / heightDiff * 0.2f;
        int scaledHeight = (int) (maxHeight * scale);
        backButton.setScaleX(scale);
        backButton.setScaleY(scale);
        backButton.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        shareContainer.setScaleX(scale);
        shareContainer.setScaleY(scale);
        settingsButton.setScaleX(scale);
        settingsButton.setScaleY(scale);
        shareContainer.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        settingsButton.setTranslationY((maxHeight - currentHeaderHeight) / 2);
        headerView.setTranslationY(currentHeaderHeight - maxHeight);
        listView.setTopGlowOffset(currentHeaderHeight);
    }

    private void openPreviewsChat(TLRPC.User user, long wid) {
        if (user == null || parentActivity == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putInt("user_id", user.id);
        args.putString("botUser", "webpage" + wid);
        ((LaunchActivity) parentActivity).presentFragment(new ChatActivity(args), false, true);
        close(false, true);
    }

    private void addAllMediaFromBlock(TLRPC.PageBlock block) {
        if (block instanceof TLRPC.TL_pageBlockPhoto || block instanceof TLRPC.TL_pageBlockVideo && isVideoBlock(block)) {
            photoBlocks.add(block);
        } else if (block instanceof TLRPC.TL_pageBlockSlideshow) {
            TLRPC.TL_pageBlockSlideshow slideshow = (TLRPC.TL_pageBlockSlideshow) block;
            int count = slideshow.items.size();
            for (int a = 0; a < count; a++) {
                TLRPC.PageBlock innerBlock = slideshow.items.get(a);
                if (innerBlock instanceof TLRPC.TL_pageBlockPhoto || innerBlock instanceof TLRPC.TL_pageBlockVideo && isVideoBlock(block)) {
                    photoBlocks.add(innerBlock);
                }
            }
        } else if (block instanceof TLRPC.TL_pageBlockCollage) {
            TLRPC.TL_pageBlockCollage collage = (TLRPC.TL_pageBlockCollage) block;
            int count = collage.items.size();
            for (int a = 0; a < count; a++) {
                TLRPC.PageBlock innerBlock = collage.items.get(a);
                if (innerBlock instanceof TLRPC.TL_pageBlockPhoto || innerBlock instanceof TLRPC.TL_pageBlockVideo && isVideoBlock(block)) {
                    photoBlocks.add(innerBlock);
                }
            }
        } else if (block instanceof TLRPC.TL_pageBlockCover && (block.cover instanceof TLRPC.TL_pageBlockPhoto || block.cover instanceof TLRPC.TL_pageBlockVideo && isVideoBlock(block.cover))) {
            photoBlocks.add(block.cover);
        }
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

        if (messageObject != null) {
            webpage = messageObject.messageOwner.media.webpage;
        }
        if (first) {
            TLRPC.TL_messages_getWebPage req = new TLRPC.TL_messages_getWebPage();
            req.url = webpage.url;
            if (webpage.cached_page instanceof TLRPC.TL_pagePart) {
                req.hash = 0;
            } else {
                req.hash = webpage.hash;
            }
            final TLRPC.WebPage webPageFinal = webpage;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (response instanceof TLRPC.TL_webPage) {
                        final TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) response;
                        if (webPage.cached_page == null) {
                            return;
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!pagesStack.isEmpty() && pagesStack.get(0) == webPageFinal && webPage.cached_page != null) {
                                    if (messageObject != null) {
                                        messageObject.messageOwner.media.webpage = webPage;
                                    }
                                    pagesStack.set(0, webPage);
                                    if (pagesStack.size() == 1) {
                                        currentPage = webPage;
                                        ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit().remove("article" + currentPage.id).commit();
                                        updateInterfaceForCurrentPage(false);
                                    }
                                }
                            }
                        });
                        HashMap<Long, TLRPC.WebPage> webpages = new HashMap<>();
                        webpages.put(webPage.id, webPage);
                        MessagesStorage.getInstance().putWebPages(webpages);
                    }
                }
            });
        }

        pagesStack.clear();
        collapsed = false;
        backDrawable.setRotation(0, false);
        containerView.setTranslationX(0);
        containerView.setTranslationY(0);
        listView.setTranslationY(0);
        listView.setAlpha(1.0f);
        windowView.setInnerTranslationX(0);

        actionBar.setVisibility(View.GONE);
        bottomLayout.setVisibility(View.GONE);
        captionTextViewNew.setVisibility(View.GONE);
        captionTextViewOld.setVisibility(View.GONE);
        shareContainer.setAlpha(0.0f);
        backButton.setAlpha(0.0f);
        settingsButton.setAlpha(0.0f);
        layoutManager.scrollToPositionWithOffset(0, 0);
        checkScroll(-AndroidUtilities.dp(56));

        String anchor = null;
        if (messageObject != null) {
            webpage = messageObject.messageOwner.media.webpage;
            String webPageUrl = webpage.url.toLowerCase();
            int index;
            for (int a = 0; a < messageObject.messageOwner.entities.size(); a++) {
                TLRPC.MessageEntity entity = messageObject.messageOwner.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityUrl) {
                    try {
                        url = messageObject.messageOwner.message.substring(entity.offset, entity.offset + entity.length).toLowerCase();
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
        addPageToStack(webpage, anchor);

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
                }
                windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
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
                ObjectAnimator.ofFloat(windowView, "alpha", 0, 1.0f),
                ObjectAnimator.ofFloat(containerView, "alpha", 0.0f, 1.0f),
                ObjectAnimator.ofFloat(windowView, "translationX", AndroidUtilities.dp(56), 0)
        );

        animationEndRunnable = new Runnable() {
            @Override
            public void run() {
                if (containerView == null || windowView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 18) {
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                animationInProgress = 0;
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        };

        animatorSet.setDuration(150);
        animatorSet.setInterpolator(interpolator);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().setAnimationInProgress(false);
                        if (animationEndRunnable != null) {
                            animationEndRunnable.run();
                            animationEndRunnable = null;
                        }
                    }
                });
            }
        });
        transitionAnimationStartTime = System.currentTimeMillis();
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats});
                NotificationCenter.getInstance().setAnimationInProgress(true);
                animatorSet.start();
            }
        });
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        showActionBar(200);
        return true;
    }

    private void hideActionBar() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(backButton, "alpha", 0.0f),
                ObjectAnimator.ofFloat(shareContainer, "alpha", 0.0f),
                ObjectAnimator.ofFloat(settingsButton, "alpha", 0.0f));
        animatorSet.setDuration(250);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    private void showActionBar(int delay) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(backButton, "alpha", 1.0f),
                ObjectAnimator.ofFloat(shareContainer, "alpha", 1.0f),
                ObjectAnimator.ofFloat(settingsButton, "alpha", 1.0f));
        animatorSet.setDuration(150);
        animatorSet.setStartDelay(delay);
        animatorSet.start();
    }

    private void showProgressView(final boolean show) {
        if (progressViewAnimation != null) {
            progressViewAnimation.cancel();
        }
        progressViewAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            shareContainer.setEnabled(false);
            progressViewAnimation.playTogether(
                    ObjectAnimator.ofFloat(shareButton, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(shareButton, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(shareButton, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            shareButton.setVisibility(View.VISIBLE);
            shareContainer.setEnabled(true);
            progressViewAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(shareButton, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(shareButton, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(shareButton, "alpha", 1.0f));

        }
        progressViewAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (progressViewAnimation != null && progressViewAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        shareButton.setVisibility(View.INVISIBLE);
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
        if (isPhotoVisible) {
            closePhoto(false);
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
                ObjectAnimator.ofFloat(containerView, "translationX", containerView.getMeasuredWidth() - AndroidUtilities.dp(56)),
                ObjectAnimator.ofFloat(containerView, "translationY", ActionBar.getCurrentActionBarHeight() + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0)),
                ObjectAnimator.ofFloat(windowView, "alpha", 0.0f),
                ObjectAnimator.ofFloat(listView, "alpha", 0.0f),
                ObjectAnimator.ofFloat(listView, "translationY", -AndroidUtilities.dp(56)),
                ObjectAnimator.ofFloat(headerView, "translationY", 0),

                ObjectAnimator.ofFloat(backButton, "scaleX", 1.0f),
                ObjectAnimator.ofFloat(backButton, "scaleY", 1.0f),
                ObjectAnimator.ofFloat(backButton, "translationY", 0),
                ObjectAnimator.ofFloat(shareContainer, "scaleX", 1.0f),
                ObjectAnimator.ofFloat(shareContainer, "translationY", 0),
                ObjectAnimator.ofFloat(shareContainer, "scaleY", 1.0f)
        );
        collapsed = true;
        animationInProgress = 2;
        animationEndRunnable = new Runnable() {
            @Override
            public void run() {
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
            }
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
                ObjectAnimator.ofFloat(containerView, "translationX", 0),
                ObjectAnimator.ofFloat(containerView, "translationY", 0),
                ObjectAnimator.ofFloat(windowView, "alpha", 1.0f),
                ObjectAnimator.ofFloat(listView, "alpha", 1.0f),
                ObjectAnimator.ofFloat(listView, "translationY", 0),
                ObjectAnimator.ofFloat(headerView, "translationY", 0),

                ObjectAnimator.ofFloat(backButton, "scaleX", 1.0f),
                ObjectAnimator.ofFloat(backButton, "scaleY", 1.0f),
                ObjectAnimator.ofFloat(backButton, "translationY", 0),
                ObjectAnimator.ofFloat(shareContainer, "scaleX", 1.0f),
                ObjectAnimator.ofFloat(shareContainer, "translationY", 0),
                ObjectAnimator.ofFloat(shareContainer, "scaleY", 1.0f)
        );
        collapsed = false;
        animationInProgress = 2;
        animationEndRunnable = new Runnable() {
            @Override
            public void run() {
                if (containerView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 18) {
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                animationInProgress = 0;
                //onClosed();
            }
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
        if (currentPage == null) {
            return;
        }
        int position = layoutManager.findFirstVisibleItemPosition();
        if (position != RecyclerView.NO_POSITION) {
            int offset;
            View view = layoutManager.findViewByPosition(position);
            if (view != null) {
                offset = view.getTop();
            } else {
                offset = 0;
            }
            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit();
            String key = "article" + currentPage.id;
            editor.putInt(key, position).putInt(key + "o", offset).putBoolean(key + "r", AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y).commit();
        }
    }

    public void close(boolean byBackPress, boolean force) {
        if (parentActivity == null || !isVisible || checkAnimation()) {
            return;
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidStarted);
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
        if (isPhotoVisible) {
            closePhoto(!force);
            if (!force) {
                return;
            }
        }
        if (openUrlReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(openUrlReqId, true);
            openUrlReqId = 0;
            showProgressView(false);
        }
        if (previewsReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(previewsReqId, true);
            previewsReqId = 0;
            showProgressView(false);
        }
        saveCurrentPagePosition();
        if (byBackPress && !force) {
            if (removeLastPageFromStack()) {
                return;
            }
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
                ObjectAnimator.ofFloat(windowView, "alpha", 0),
                ObjectAnimator.ofFloat(containerView, "alpha", 0.0f),
                ObjectAnimator.ofFloat(windowView, "translationX", 0, AndroidUtilities.dp(56))
        );
        animationInProgress = 2;
        animationEndRunnable = new Runnable() {
            @Override
            public void run() {
                if (containerView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 18) {
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                animationInProgress = 0;
                onClosed();
            }
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
        currentPage = null;
        blocks.clear();
        photoBlocks.clear();
        adapter.notifyDataSetChanged();
        try {
            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.e(e);
        }
        for (int a = 0; a < createdWebViews.size(); a++) {
            BlockEmbedCell cell = createdWebViews.get(a);
            cell.destroyWebView(false);
        }
        containerView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (windowView.getParent() != null) {
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(windowView);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void loadChannel(final BlockChannelCell cell, TLRPC.Chat channel) {
        if (loadingChannel || TextUtils.isEmpty(channel.username)) {
            return;
        }
        loadingChannel = true;
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = channel.username;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingChannel = false;
                        if (parentFragment == null || blocks == null || blocks.isEmpty()) {
                            return;
                        }
                        if (error == null) {
                            TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                            if (!res.chats.isEmpty()) {
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, true);
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
                    }
                });
            }
        });
    }

    private void joinChannel(final BlockChannelCell cell, final TLRPC.Chat channel) {
        final TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
        req.channel = MessagesController.getInputChannel(channel);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            cell.setState(0, false);
                            AlertsCreator.processError(error, parentFragment, req, true);
                        }
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
                MessagesController.getInstance().processUpdates(updates, false);
                if (!hasJoinMessage) {
                    MessagesController.getInstance().generateJoinMessage(channel.id, true);
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        cell.setState(2, false);
                    }
                });
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        MessagesController.getInstance().loadFullChat(channel.id, 0, true);
                    }
                }, 1000);
                MessagesStorage.getInstance().updateDialogsWithDeletedMessages(new ArrayList<Integer>(), null, true, channel.id);
            }
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
        releasePlayer();
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
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    showActionBar(120);
                    visibleDialog = null;
                }
            });
            dialog.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private class WebpageAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public WebpageAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new BlockParagraphCell(context);
                    break;
                }
                case 1: {
                    view = new BlockHeaderCell(context);
                    break;
                }
                case 2: {
                    view = new BlockDividerCell(context);
                    break;
                }
                case 3: {
                    view = new BlockEmbedCell(context);
                    break;
                }
                case 4: {
                    view = new BlockSubtitleCell(context);
                    break;
                }
                case 5: {
                    view = new BlockVideoCell(context, 0);
                    break;
                }
                case 6: {
                    view = new BlockPullquoteCell(context);
                    break;
                }
                case 7: {
                    view = new BlockBlockquoteCell(context);
                    break;
                }
                case 8: {
                    view = new BlockSlideshowCell(context);
                    break;
                }
                case 9: {
                    view = new BlockPhotoCell(context, 0);
                    break;
                }
                case 10: {
                    view = new BlockAuthorDateCell(context);
                    break;
                }
                case 11: {
                    view = new BlockTitleCell(context);
                    break;
                }
                case 12: {
                    view = new BlockListCell(context);
                    break;
                }
                case 13: {
                    view = new BlockFooterCell(context);
                    break;
                }
                case 14: {
                    view = new BlockPreformattedCell(context);
                    break;
                }
                case 15: {
                    view = new BlockSubheaderCell(context);
                    break;
                }
                case 16: {
                    view = new BlockEmbedPostCell(context);
                    break;
                }
                case 17: {
                    view = new BlockCollageCell(context);
                    break;
                }
                case 18: {
                    view = new BlockChannelCell(context, 0);
                    break;
                }
                case 19: {
                    view = new BlockAudioCell(context);
                    break;
                }
                case 90:
                default: {
                    FrameLayout frameLayout = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
                        }
                    };
                    frameLayout.setTag(90);
                    TextView textView = new TextView(context);
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 0, 10, 0, 0));
                    textView.setText(LocaleController.getString("PreviewFeedback", R.string.PreviewFeedback));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    textView.setGravity(Gravity.CENTER);
                    view = frameLayout;
                    break;
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < blocks.size()) {
                TLRPC.PageBlock block = blocks.get(position);
                TLRPC.PageBlock originalBlock = block;
                if (block instanceof TLRPC.TL_pageBlockCover) {
                    block = block.cover;
                }
                switch (holder.getItemViewType()) {
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
                        cell.setBlock((TLRPC.TL_pageBlockVideo) block, position == 0, position == blocks.size() - 1);
                        cell.setParentBlock(originalBlock);
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
                        cell.setBlock((TLRPC.TL_pageBlockPhoto) block, position == 0, position == blocks.size() - 1);
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
                        BlockListCell cell = (BlockListCell) holder.itemView;
                        cell.setBlock((TLRPC.TL_pageBlockList) block);
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
                        cell.setBlock((TLRPC.TL_pageBlockAudio) block, position == 0, position == blocks.size() - 1);
                    }
                }
            } else {
                switch (holder.getItemViewType()) {
                    case 90: {
                        TextView textView = (TextView) ((ViewGroup) holder.itemView).getChildAt(0);
                        int color = getSelectedColor();
                        if (color == 0) {
                            textView.setTextColor(0xff78828d);
                            textView.setBackgroundColor(0xffedeff0);
                        } else if (color == 1) {
                            textView.setTextColor(getGrayTextColor());
                            textView.setBackgroundColor(0xffe5dec8);
                        } else if (color == 2) {
                            textView.setTextColor(getGrayTextColor());
                            textView.setBackgroundColor(0xff262626);
                        }
                        break;
                    }
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
            } else if (block instanceof TLRPC.TL_pageBlockList) {
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
            } else if (block instanceof TLRPC.TL_pageBlockCover) {
                return getTypeForBlock(block.cover);
            }
            return 0;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == blocks.size()) {
                return 90;
            }
            return getTypeForBlock(blocks.get(position));
        }

        @Override
        public int getItemCount() {
            return currentPage != null && currentPage.cached_page != null ? blocks.size() + 1 : 0;
        }
    }

    private class BlockVideoCell extends FrameLayout implements MediaController.FileDownloadProgressListener {

        private StaticLayout textLayout;
        private ImageReceiver imageView;
        private RadialProgress radialProgress;
        private BlockChannelCell channelCell;
        private int lastCreatedWidth;
        private int currentType;
        private boolean isFirst;
        private boolean isLast;
        private int textX;
        private int textY;

        private int buttonX;
        private int buttonY;
        private boolean photoPressed;
        private int buttonState;
        private int buttonPressed;
        private boolean cancelLoading;

        private int TAG;

        private TLRPC.TL_pageBlockVideo currentBlock;
        private TLRPC.PageBlock parentBlock;
        private TLRPC.Document currentDocument;
        private boolean isGif;

        public BlockVideoCell(Context context, int type) {
            super(context);

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            currentType = type;
            radialProgress = new RadialProgress(this);
            radialProgress.setAlphaForPrevious(true);
            radialProgress.setProgressColor(Theme.ARTICLE_VIEWER_MEDIA_PROGRESS_COLOR);
            TAG = MediaController.getInstance().generateObserverTag();
            channelCell = new BlockChannelCell(context, 1);
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        public void setBlock(TLRPC.TL_pageBlockVideo block, boolean first, boolean last) {
            currentBlock = block;
            parentBlock = null;
            cancelLoading = false;
            currentDocument = getDocumentWithId(currentBlock.video_id);
            isGif = MessageObject.isGifDocument(currentDocument)/* && currentBlock.autoplay*/;
            lastCreatedWidth = 0;
            isFirst = first;
            isLast = last;
            channelCell.setVisibility(INVISIBLE);
            updateButtonState(false);
            requestLayout();
        }

        public void setParentBlock(TLRPC.PageBlock block) {
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
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + AndroidUtilities.dp(39)) {
                if (channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    MessagesController.openByUserName(channelBlock.channel.username, parentFragment, 2);
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
                    openPhoto(currentBlock);
                } else if (buttonPressed == 1) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton(false);
                    radialProgress.swapBackground(getDrawableForCurrentState());
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || buttonPressed != 0 || checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentType == 1) {
                width = listView.getWidth();
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
                if (currentDocument != null) {
                    TLRPC.PhotoSize thumb = currentDocument.thumb;
                    if (currentType == 0) {
                        float scale;
                        scale = photoWidth / (float) thumb.w;
                        height = (int) (scale * thumb.h);
                        if (parentBlock instanceof TLRPC.TL_pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(listView.getMeasuredWidth(), listView.getMeasuredHeight()) - AndroidUtilities.dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / (float) thumb.h;
                                photoWidth = (int) (scale * thumb.w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                    }
                    imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : AndroidUtilities.dp(8), photoWidth, height);
                    if (isGif) {
                        String filter = String.format(Locale.US, "%d_%d", photoWidth, height);
                        imageView.setImage(currentDocument, filter, thumb != null ? thumb.location : null, thumb != null ? "80_80_b" : null, currentDocument.size, null, 1);
                    } else {
                        imageView.setImage(null, null, thumb != null ? thumb.location : null, thumb != null ? "80_80_b" : null, 0, null, 1);
                    }

                    int size = AndroidUtilities.dp(48);
                    buttonX = (int) (imageView.getImageX() + (imageView.getImageWidth() - size) / 2.0f);
                    buttonY = (int) (imageView.getImageY() + (imageView.getImageHeight() - size) / 2.0f);
                    radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                }

                if (currentType == 0 && lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }
                boolean nextIsChannel = parentBlock instanceof TLRPC.TL_pageBlockCover && blocks != null && blocks.size() > 1 && blocks.get(1) instanceof TLRPC.TL_pageBlockChannel;
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
            imageView.draw(canvas);
            if (imageView.getVisible()) {
                radialProgress.draw(canvas);
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY = imageView.getImageY() + imageView.getImageHeight() + AndroidUtilities.dp(8));
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        private Drawable getDrawableForCurrentState() {
            if (buttonState >= 0 && buttonState < 4) {
                return Theme.chat_photoStatesDrawables[buttonState][buttonPressed];
            }
            return null;
        }

        public void updateButtonState(boolean animated) {
            String fileName = FileLoader.getAttachFileName(currentDocument);
            File path = FileLoader.getPathToAttach(currentDocument, true);
            boolean fileExists = path.exists();
            if (TextUtils.isEmpty(fileName)) {
                radialProgress.setBackground(null, false, false);
                return;
            }
            if (!fileExists) {
                MediaController.getInstance().addLoadingFileObserver(fileName, null, this);
                float setProgress = 0;
                boolean progressVisible = false;
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    if (!cancelLoading && isGif) {
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
                radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
                radialProgress.setProgress(setProgress, false);
                invalidate();
            } else {
                MediaController.getInstance().removeLoadingFileObserver(this);
                if (!isGif) {
                    buttonState = 3;
                } else {
                    buttonState = -1;
                }
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            }
        }

        private void didPressedButton(boolean animated) {
            if (buttonState == 0) {
                cancelLoading = false;
                radialProgress.setProgress(0, false);
                if (isGif) {
                    imageView.setImage(currentDocument, null, currentDocument.thumb != null ? currentDocument.thumb.location : null, "80_80_b", currentDocument.size, null, 1);
                } else {
                    FileLoader.getInstance().loadFile(currentDocument, true, 1);
                }
                buttonState = 1;
                radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                invalidate();
            } else if (buttonState == 1) {
                cancelLoading = true;
                if (isGif) {
                    imageView.cancelLoadImage();
                } else {
                    FileLoader.getInstance().cancelLoadFile(currentDocument);
                }
                buttonState = 0;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            } else if (buttonState == 2) {
                imageView.setAllowStartAnimation(true);
                imageView.startAnimation();
                buttonState = -1;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            } else if (buttonState == 3) {
                openPhoto(currentBlock);
            }
        }

        @Override
        public void onFailedDownload(String fileName) {
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
        public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

        }

        @Override
        public void onProgressDownload(String fileName, float progress) {
            radialProgress.setProgress(progress, true);
            if (buttonState != 1) {
                updateButtonState(false);
            }
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    private class BlockAudioCell extends View implements MediaController.FileDownloadProgressListener {

        private StaticLayout textLayout;
        private RadialProgress radialProgress;
        private SeekBar seekBar;
        private int lastCreatedWidth;
        private boolean isFirst;
        private boolean isLast;
        private int textX;
        private int textY = AndroidUtilities.dp(54);

        private String lastTimeString;

        private StaticLayout titleLayout;
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

        public BlockAudioCell(Context context) {
            super(context);

            radialProgress = new RadialProgress(this);
            radialProgress.setAlphaForPrevious(true);
            radialProgress.setDiff(AndroidUtilities.dp(0));
            radialProgress.setStrikeWidth(AndroidUtilities.dp(2));
            TAG = MediaController.getInstance().generateObserverTag();

            seekBar = new SeekBar(context);

            seekBar.setDelegate(new SeekBar.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
                    if (currentMessageObject == null) {
                        return;
                    }
                    currentMessageObject.audioProgress = progress;
                    MediaController.getInstance().seekToProgress(currentMessageObject, progress);
                }
            });
        }

        public void setBlock(TLRPC.TL_pageBlockAudio block, boolean first, boolean last) {
            currentBlock = block;


            currentMessageObject = audioBlocks.get(currentBlock);
            currentDocument = currentMessageObject.getDocument();

            lastCreatedWidth = 0;
            isFirst = first;
            isLast = last;

            radialProgress.setProgressColor(getTextColor());
            seekBar.setColors(getTextColor() & 0x3fffffff, getTextColor(), getTextColor());

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
                    didPressedButton(false);
                    radialProgress.swapBackground(getDrawableForCurrentState());
                    invalidate();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                buttonPressed = 0;
            }
            return buttonPressed != 0 || checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @SuppressLint("DrawAllocation")
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
                int size = AndroidUtilities.dp(40);
                buttonX = AndroidUtilities.dp(16);
                buttonY = AndroidUtilities.dp(7);
                currentBlock.caption = new TLRPC.TL_textPlain();
                radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size);
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
                    titleLayout = new StaticLayout(stringFinal, audioTimePaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
            radialProgress.draw(canvas);
            canvas.save();
            canvas.translate(seekBarX, seekBarY);
            seekBar.draw(canvas);
            canvas.restore();
            if (durationLayout != null) {
                canvas.save();
                canvas.translate(buttonX + AndroidUtilities.dp(54), seekBarY + AndroidUtilities.dp(6));
                durationLayout.draw(canvas);
                canvas.restore();
            }
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(buttonX + AndroidUtilities.dp(54), seekBarY - AndroidUtilities.dp(16));
                titleLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }

        private Drawable getDrawableForCurrentState() {
            return Theme.chat_ivStatesDrawable[buttonState][buttonPressed != 0 ? 1 : 0];
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
            String timeString = String.format("%d:%02d", duration / 60, duration % 60);
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
                radialProgress.setBackground(null, false, false);
                return;
            }
            if (fileExists) {
                MediaController.getInstance().removeLoadingFileObserver(this);
                boolean playing = MediaController.getInstance().isPlayingMessage(currentMessageObject);
                if (!playing || playing && MediaController.getInstance().isMessagePaused()) {
                    buttonState = 0;
                } else {
                    buttonState = 1;
                }
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            } else {
                MediaController.getInstance().addLoadingFileObserver(fileName, null, this);
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    buttonState = 2;
                    radialProgress.setProgress(0, animated);
                    radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                } else {
                    buttonState = 3;
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        radialProgress.setProgress(progress, animated);
                    } else {
                        radialProgress.setProgress(0, animated);
                    }
                    radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
                }
            }
            updatePlayingMessageProgress();
        }

        private void didPressedButton(boolean animated) {
            if (buttonState == 0) {
                if (MediaController.getInstance().setPlaylist(audioMessages, currentMessageObject, false)) {
                    buttonState = 1;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 1) {
                boolean result = MediaController.getInstance().pauseMessage(currentMessageObject);
                if (result) {
                    buttonState = 0;
                    radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                    invalidate();
                }
            } else if (buttonState == 2) {
                radialProgress.setProgress(0, false);
                FileLoader.getInstance().loadFile(currentDocument, true, 1);
                buttonState = 3;
                radialProgress.setBackground(getDrawableForCurrentState(), true, false);
                invalidate();
            } else if (buttonState == 3) {
                FileLoader.getInstance().cancelLoadFile(currentDocument);
                buttonState = 2;
                radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                invalidate();
            }
        }

        @Override
        public void onFailedDownload(String fileName) {
            updateButtonState(true);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, true);
            updateButtonState(true);
        }

        @Override
        public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

        }

        @Override
        public void onProgressDownload(String fileName, float progress) {
            radialProgress.setProgress(progress, true);
            if (buttonState != 3) {
                updateButtonState(false);
            }
        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    private class BlockEmbedPostCell extends View {

        private ImageReceiver avatarImageView;
        private AvatarDrawable avatarDrawable;
        private StaticLayout dateLayout;
        private StaticLayout nameLayout;
        private StaticLayout textLayout;
        private boolean avatarVisible;
        private int nameX;
        private int dateX;

        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18 + 14);
        private int textY = AndroidUtilities.dp(40 + 8 + 8);

        private int captionX = AndroidUtilities.dp(18);
        private int captionY;

        private int lineHeight;

        private TLRPC.TL_pageBlockEmbedPost currentBlock;

        public BlockEmbedPostCell(Context context) {
            super(context);
            avatarImageView = new ImageReceiver(this);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            avatarImageView.setImageCoords(AndroidUtilities.dp(18 + 14), AndroidUtilities.dp(8), AndroidUtilities.dp(40), AndroidUtilities.dp(40));

            avatarDrawable = new AvatarDrawable();
        }

        public void setBlock(TLRPC.TL_pageBlockEmbedPost block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    if (avatarVisible = (currentBlock.author_photo_id != 0)) {
                        TLRPC.Photo photo = getPhotoWithId(currentBlock.author_photo_id);
                        if (avatarVisible = (photo != null)) {
                            avatarDrawable.setInfo(0, currentBlock.author, null, false);
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.dp(40), true);
                            avatarImageView.setImage(image.location, String.format(Locale.US, "%d_%d", 40, 40), avatarDrawable, 0, null, 1);
                        }
                    }
                    nameLayout = createLayoutForText(currentBlock.author, null, width - AndroidUtilities.dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), currentBlock);
                    if (currentBlock.date != 0) {
                        dateLayout = createLayoutForText(LocaleController.getInstance().chatFullDate.format((long) currentBlock.date * 1000), null, width - AndroidUtilities.dp(36 + 14 + (avatarVisible ? 40 + 14 : 0)), currentBlock);
                    } else {
                        dateLayout = null;
                    }
                    height = AndroidUtilities.dp(40 + 8 + 8);
                    if (currentBlock.text != null) {
                        textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36 + 14), currentBlock);
                        if (textLayout != null) {
                            height += AndroidUtilities.dp(8) + textLayout.getHeight();
                        }
                    }
                    lineHeight = height;
                    //lastCreatedWidth = width;
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
            if (avatarVisible) {
                avatarImageView.draw(canvas);
            }
            if (nameLayout != null) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), AndroidUtilities.dp(dateLayout != null ? 10 : 19));
                nameLayout.draw(canvas);
                canvas.restore();
            }
            if (dateLayout != null) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(18 + 14 + (avatarVisible ? 40 + 14 : 0)), AndroidUtilities.dp(29));
                dateLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            canvas.drawRect(AndroidUtilities.dp(18), AndroidUtilities.dp(6), AndroidUtilities.dp(20), lineHeight - (currentBlock.level != 0 ? 0 : AndroidUtilities.dp(6)), quoteLinePaint);
        }
    }

    private class BlockParagraphCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX;
        private int textY;

        private TLRPC.TL_pageBlockParagraph currentBlock;

        public BlockParagraphCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockParagraph block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (currentBlock.level == 0) {
                    if (currentBlock.caption != null) {
                        textY = AndroidUtilities.dp(4);
                    } else {
                        textY = AndroidUtilities.dp(8);
                    }
                    textX = AndroidUtilities.dp(18);
                } else {
                    textY = 0;
                    textX = AndroidUtilities.dp(18 + 14 * currentBlock.level);
                }
                if (lastCreatedWidth != width) {
                    if (currentBlock.text != null) {
                        textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(18) - textX, currentBlock);
                    } else if (currentBlock.caption != null) {
                        textLayout = createLayoutForText(null, currentBlock.caption, width - AndroidUtilities.dp(18) - textX, currentBlock);
                    }
                    if (textLayout != null) {
                        height = textLayout.getHeight();
                        if (currentBlock.level > 0) {
                            height += AndroidUtilities.dp(8);
                        } else {
                            height += AndroidUtilities.dp(8 + 8);
                        }
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockEmbedCell extends FrameLayout {

        private TouchyWebView webView;
        private WebPlayerView videoView;
        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX;
        private int textY;
        private int listX;

        private TLRPC.TL_pageBlockEmbed currentBlock;

        public class TouchyWebView extends WebView {

            public TouchyWebView(Context context) {
                super(context);
                setFocusable(false);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
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

        @SuppressLint("SetJavaScriptEnabled")
        public BlockEmbedCell(final Context context) {
            super(context);
            setWillNotDraw(false);

            videoView = new WebPlayerView(context, false, false, new WebPlayerView.WebPlayerViewDelegate() {
                @Override
                public void onInitFailed() {
                    webView.setVisibility(VISIBLE);
                    videoView.setVisibility(INVISIBLE);
                    videoView.loadVideo(null, null, null, false);
                    HashMap<String, String> args = new HashMap<>();
                    args.put("Referer", "http://youtube.com");
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
                    showDialog(new ShareAlert(parentActivity, null, currentBlock.url, false, currentBlock.url, true));
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
                public boolean checkInlinePermissons() {
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (customView != null) {
                                fullscreenVideoContainer.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                                fullscreenVideoContainer.setVisibility(VISIBLE);
                            }
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
            /*if (currentBlock == block) {
                return;
            }*/
            TLRPC.TL_pageBlockEmbed previousBlock = currentBlock;
            currentBlock = block;
            lastCreatedWidth = 0;
            if (previousBlock != currentBlock) {
                try {
                    webView.loadUrl("about:blank");
                } catch (Exception e) {
                    FileLog.e(e);
                }

                try {
                    if (currentBlock.html != null) {
                        webView.loadDataWithBaseURL("https://telegram.org/embed", currentBlock.html, "text/html", "UTF-8", null);
                        videoView.setVisibility(INVISIBLE);
                        videoView.loadVideo(null, null, null, false);
                        webView.setVisibility(VISIBLE);
                    } else {
                        TLRPC.Photo thumb = currentBlock.poster_photo_id != 0 ? getPhotoWithId(currentBlock.poster_photo_id) : null;
                        boolean handled = videoView.loadVideo(block.url, thumb, null, currentBlock.autoplay);
                        if (handled) {
                            webView.setVisibility(INVISIBLE);
                            videoView.setVisibility(VISIBLE);
                            webView.stopLoading();
                            webView.loadUrl("about:blank");
                        } else {
                            webView.setVisibility(VISIBLE);
                            videoView.setVisibility(INVISIBLE);
                            videoView.loadVideo(null, null, null, false);
                            HashMap<String, String> args = new HashMap<>();
                            args.put("Referer", "http://youtube.com");
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
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

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
                }
                float scale;
                if (currentBlock.w == 0) {
                    scale = 1;
                } else {
                    scale = width / (float) currentBlock.w;
                }
                height = (int) (currentBlock.w == 0 ? AndroidUtilities.dp(currentBlock.h) * scale : currentBlock.h * scale);
                webView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                if (videoView.getParent() == this) {
                    videoView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height + AndroidUtilities.dp(10), MeasureSpec.EXACTLY));
                }
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout != null) {
                        textY = AndroidUtilities.dp(8) + height;
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
                }
                height += AndroidUtilities.dp(5);

                if (currentBlock.level > 0 && !currentBlock.bottom) {
                    height += AndroidUtilities.dp(8);
                } else if (currentBlock.level == 0 && textLayout != null) {
                    height += AndroidUtilities.dp(8);
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
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockCollageCell extends FrameLayout {

        private RecyclerListView innerListView;
        private GridLayoutManager gridLayoutManager;
        private RecyclerView.Adapter adapter;
        private StaticLayout textLayout;
        private int listX;
        private int textX;
        private int textY;

        private boolean inLayout;

        private TLRPC.TL_pageBlockCollage currentBlock;
        private int lastCreatedWidth;

        public BlockCollageCell(Context context) {
            super(context);

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
                    outRect.top = outRect.left = 0;
                    outRect.bottom = outRect.right = AndroidUtilities.dp(2);
                }
            });
            innerListView.setLayoutManager(gridLayoutManager = new GridLayoutManager(context, 3));
            innerListView.setAdapter(adapter = new RecyclerView.Adapter() {
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    View view;
                    switch (viewType) {
                        case 0: {
                            view = new BlockPhotoCell(getContext(), 2);
                            break;
                        }
                        case 1:
                        default: {
                            view = new BlockVideoCell(getContext(), 2);
                            break;
                        }
                    }
                    return new RecyclerListView.Holder(view);
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    switch (holder.getItemViewType()) {
                        case 0: {
                            BlockPhotoCell cell = (BlockPhotoCell) holder.itemView;
                            cell.setBlock((TLRPC.TL_pageBlockPhoto) currentBlock.items.get(position), true, true);
                            break;
                        }
                        case 1:
                        default: {
                            BlockVideoCell cell = (BlockVideoCell) holder.itemView;
                            cell.setBlock((TLRPC.TL_pageBlockVideo) currentBlock.items.get(position), true, true);
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
                    TLRPC.PageBlock block = currentBlock.items.get(position);
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
            currentBlock = block;
            lastCreatedWidth = 0;
            adapter.notifyDataSetChanged();
            int color = getSelectedColor();
            if (color == 0) {
                innerListView.setGlowColor(0xfff5f6f7);
            } else if (color == 1) {
                innerListView.setGlowColor(0xfff5efdc);
            } else if (color == 2) {
                innerListView.setGlowColor(0xff141414);
            }
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

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

                int countPerRow = listWidth / AndroidUtilities.dp(100);
                int rowCount = (int) Math.ceil(currentBlock.items.size() / (float) countPerRow);
                int itemSize = listWidth / countPerRow;
                gridLayoutManager.setSpanCount(countPerRow);
                innerListView.measure(MeasureSpec.makeMeasureSpec(itemSize * countPerRow + AndroidUtilities.dp(2), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize * rowCount, MeasureSpec.EXACTLY));
                height = rowCount * itemSize - AndroidUtilities.dp(2);

                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout != null) {
                        textY = height + AndroidUtilities.dp(8);
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
                }

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
            innerListView.layout(listX, 0, listX + innerListView.getMeasuredWidth(), innerListView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockSlideshowCell extends FrameLayout {

        private ViewPager innerListView;
        private PagerAdapter adapter;
        private View dotsContainer;

        private TLRPC.TL_pageBlockSlideshow currentBlock;
        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY;

        public BlockSlideshowCell(Context context) {
            super(context);

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
                    return super.onInterceptTouchEvent(ev);
                }
            };
            innerListView.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                }

                @Override
                public void onPageSelected(int position) {
                    dotsContainer.invalidate();
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
            innerListView.setAdapter(adapter = new PagerAdapter() {

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
                        view = new BlockPhotoCell(getContext(), 1);
                        ((BlockPhotoCell) view).setBlock((TLRPC.TL_pageBlockPhoto) block, true, true);
                    } else {
                        view = new BlockVideoCell(getContext(), 1);
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
            int color = getSelectedColor();
            if (color == 0) {
                AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, 0xfff5f6f7);
            } else if (color == 1) {
                AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, 0xfff5efdc);
            } else if (color == 2) {
                AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, 0xff141414);
            }
            addView(innerListView);

            dotsContainer = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (currentBlock == null) {
                        return;
                    }
                    int selected = innerListView.getCurrentItem();
                    for (int a = 0; a < currentBlock.items.size(); a++) {
                        int cx = AndroidUtilities.dp(4) + AndroidUtilities.dp(13) * a;
                        Drawable drawable = selected == a ? slideDotBigDrawable : slideDotDrawable;
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
            lastCreatedWidth = 0;
            innerListView.setCurrentItem(0, false);
            adapter.notifyDataSetChanged();
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height;

            if (currentBlock != null) {
                height = AndroidUtilities.dp(310);
                innerListView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                int count = currentBlock.items.size();
                dotsContainer.measure(MeasureSpec.makeMeasureSpec(count * AndroidUtilities.dp(7) + (count - 1) * AndroidUtilities.dp(6) + AndroidUtilities.dp(4), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10), MeasureSpec.EXACTLY));
                if (lastCreatedWidth != width) {
                    textY = height + AndroidUtilities.dp(16);
                    textLayout = createLayoutForText(null, currentBlock.caption, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
            int x = (right - left - dotsContainer.getMeasuredWidth()) / 2;
            dotsContainer.layout(x, y, x + dotsContainer.getMeasuredWidth(), y + dotsContainer.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (currentBlock == null) {
                return;
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockListCell extends View {

        private ArrayList<StaticLayout> textLayouts = new ArrayList<>();
        private ArrayList<StaticLayout> textNumLayouts = new ArrayList<>();
        private ArrayList<Integer> textYLayouts = new ArrayList<>();
        private int lastCreatedWidth;

        private TLRPC.TL_pageBlockList currentBlock;

        private boolean hasRtl;
        private int textX;
        private int maxLetterWidth;

        public BlockListCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockList block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int count = textLayouts.size();
            int textX = AndroidUtilities.dp(36);
            for (int a = 0; a < count; a++) {
                StaticLayout textLayout = textLayouts.get(a);
                if (checkLayoutForLinks(event, this, textLayout, textX, textYLayouts.get(a))) {
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            hasRtl = false;
            maxLetterWidth = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayouts.clear();
                    textYLayouts.clear();
                    textNumLayouts.clear();
                    int count = currentBlock.items.size();

                    for (int a = 0; a < count; a++) {
                        TLRPC.RichText item = currentBlock.items.get(a);
                        if (a == 0) {
                            StaticLayout textLayout = createLayoutForText(null, item, width - AndroidUtilities.dp(6 + 18) - maxLetterWidth, currentBlock);
                            if (textLayout != null) {
                                int lCount = textLayout.getLineCount();
                                for (int b = 0; b < lCount; b++) {
                                    if (textLayout.getLineLeft(b) > 0) {
                                        hasRtl = true;
                                        isRtl = 1;
                                        break;
                                    }
                                }
                            }
                        }
                        String num;
                        if (currentBlock.ordered) {
                            if (hasRtl) {
                                num = String.format(".%d", a + 1);
                            } else {
                                num = String.format("%d.", a + 1);
                            }
                        } else {
                            num = "•";
                        }
                        StaticLayout textLayout = createLayoutForText(num, item, width - AndroidUtilities.dp(36 + 18), currentBlock);
                        textNumLayouts.add(textLayout);
                        if (currentBlock.ordered) {
                            if (textLayout != null) {
                                maxLetterWidth = Math.max(maxLetterWidth, (int) Math.ceil(textLayout.getLineWidth(0)));
                            }
                        } else if (a == 0) {
                            maxLetterWidth = AndroidUtilities.dp(12);
                        }
                    }

                    for (int a = 0; a < count; a++) {
                        TLRPC.RichText item = currentBlock.items.get(a);
                        height += AndroidUtilities.dp(8);
                        StaticLayout textLayout = createLayoutForText(null, item, width - AndroidUtilities.dp(6 + 18 + 18) - maxLetterWidth, currentBlock);
                        textYLayouts.add(height);
                        textLayouts.add(textLayout);
                        if (textLayout != null) {
                            height += textLayout.getHeight();
                        }
                    }
                    if (hasRtl) {
                        textX = AndroidUtilities.dp(18);
                    } else {
                        textX = AndroidUtilities.dp(18 + 6) + maxLetterWidth;
                    }
                    height += AndroidUtilities.dp(8);
                    //lastCreatedWidth = width;
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
            int count = textLayouts.size();
            int width = getMeasuredWidth();
            for (int a = 0; a < count; a++) {
                StaticLayout textLayout = textLayouts.get(a);
                StaticLayout textLayout2 = textNumLayouts.get(a);
                canvas.save();
                if (hasRtl) {
                    canvas.translate(width - AndroidUtilities.dp(18) - (int) Math.ceil(textLayout2.getLineWidth(0)), textYLayouts.get(a));
                } else {
                    canvas.translate(AndroidUtilities.dp(18), textYLayouts.get(a));
                }
                if (textLayout2 != null) {
                    textLayout2.draw(canvas);
                }
                canvas.restore();
                canvas.save();
                canvas.translate(textX, textYLayouts.get(a));
                drawLayoutLink(canvas, textLayout);
                if (textLayout != null) {
                    textLayout.draw(canvas);
                }
                canvas.restore();
            }
        }
    }

    private class BlockHeaderCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockHeader currentBlock;

        public BlockHeaderCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockHeader block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockDividerCell extends View {

        private RectF rect = new RectF();

        public BlockDividerCell(Context context) {
            super(context);
            if (dividerPaint == null) {
                dividerPaint = new Paint();
                int color = getSelectedColor();
                if (color == 0) {
                    dividerPaint.setColor(0xffcdd1d5);
                } else if (color == 1) {
                    dividerPaint.setColor(0xffc1baa5);
                } else if (color == 2) {
                    dividerPaint.setColor(0xff444444);
                }
            }
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

    private class BlockSubtitleCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockSubtitle currentBlock;

        public BlockSubtitleCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockSubtitle block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockPullquoteCell extends View {

        private StaticLayout textLayout;
        private StaticLayout textLayout2;
        private int textY2;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockPullquote currentBlock;

        public BlockPullquoteCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockPullquote block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || checkLayoutForLinks(event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    textLayout2 = createLayoutForText(null, currentBlock.caption, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout2 != null) {
                        textY2 = height + AndroidUtilities.dp(2);
                        height += AndroidUtilities.dp(8) + textLayout2.getHeight();
                    }
                    if (height != 0) {
                        height += AndroidUtilities.dp(8);
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawLayoutLink(canvas, textLayout2);
                textLayout2.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockBlockquoteCell extends View {

        private StaticLayout textLayout;
        private StaticLayout textLayout2;
        private int textY2;
        private int lastCreatedWidth;
        private int textX;
        private int textY = AndroidUtilities.dp(8);
        private boolean hasRtl;

        private TLRPC.TL_pageBlockBlockquote currentBlock;

        public BlockBlockquoteCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockBlockquote block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || checkLayoutForLinks(event, this, textLayout2, textX, textY2) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    int textWidth = width - AndroidUtilities.dp(36 + 14);
                    if (currentBlock.level > 0) {
                        textWidth -= AndroidUtilities.dp(14 * currentBlock.level);
                    }
                    textLayout = createLayoutForText(null, currentBlock.text, textWidth, currentBlock);
                    hasRtl = false;
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                        int count = textLayout.getLineCount();
                        for (int a = 0; a < count; a++) {
                            if (textLayout.getLineLeft(a) > 0) {
                                isRtl = 1;
                                hasRtl = true;
                                break;
                            }
                        }
                    }
                    if (currentBlock.level > 0) {
                        if (hasRtl) {
                            textX = AndroidUtilities.dp(14 + currentBlock.level * 14);
                        } else {
                            textX = AndroidUtilities.dp(14 * currentBlock.level) + AndroidUtilities.dp(18 + 14);
                        }
                    } else {
                        if (hasRtl) {
                            textX = AndroidUtilities.dp(14);
                        } else {
                            textX = AndroidUtilities.dp(18 + 14);
                        }
                    }
                    textLayout2 = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout2 != null) {
                        textY2 = height + AndroidUtilities.dp(8);
                        height += AndroidUtilities.dp(8) + textLayout2.getHeight();
                    }
                    if (height != 0) {
                        height += AndroidUtilities.dp(8);
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (textLayout2 != null) {
                canvas.save();
                canvas.translate(textX, textY2);
                drawLayoutLink(canvas, textLayout2);
                textLayout2.draw(canvas);
                canvas.restore();
            }
            if (hasRtl) {
                int x = getMeasuredWidth() - AndroidUtilities.dp(20);
                canvas.drawRect(x, AndroidUtilities.dp(6), x + AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(6), quoteLinePaint);
            } else {
                canvas.drawRect(AndroidUtilities.dp(18 + currentBlock.level * 14), AndroidUtilities.dp(6), AndroidUtilities.dp(20 + currentBlock.level * 14), getMeasuredHeight() - AndroidUtilities.dp(6), quoteLinePaint);
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockPhotoCell extends FrameLayout {

        private StaticLayout textLayout;
        private ImageReceiver imageView;
        private BlockChannelCell channelCell;
        private int lastCreatedWidth;
        private int currentType;
        private boolean isFirst;
        private boolean isLast;
        private int textX;
        private int textY;
        private boolean photoPressed;

        private TLRPC.TL_pageBlockPhoto currentBlock;
        private TLRPC.PageBlock parentBlock;

        public BlockPhotoCell(Context context, int type) {
            super(context);

            setWillNotDraw(false);
            imageView = new ImageReceiver(this);
            channelCell = new BlockChannelCell(context, 1);
            addView(channelCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            currentType = type;
            //imageView.setAspectFit(currentType == 1);
        }

        public void setBlock(TLRPC.TL_pageBlockPhoto block, boolean first, boolean last) {
            parentBlock = null;
            currentBlock = block;
            lastCreatedWidth = 0;
            isFirst = first;
            isLast = last;
            channelCell.setVisibility(INVISIBLE);
            requestLayout();
        }

        public void setParentBlock(TLRPC.PageBlock block) {
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
            float x = event.getX();
            float y = event.getY();
            if (channelCell.getVisibility() == VISIBLE && y > channelCell.getTranslationY() && y < channelCell.getTranslationY() + AndroidUtilities.dp(39)) {
                if (channelBlock != null && event.getAction() == MotionEvent.ACTION_UP) {
                    MessagesController.openByUserName(channelBlock.channel.username, parentFragment, 2);
                    close(false, true);
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
                photoPressed = true;
            } else if (event.getAction() == MotionEvent.ACTION_UP && photoPressed) {
                photoPressed = false;
                openPhoto(currentBlock);
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                photoPressed = false;
            }
            return photoPressed || checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;
            if (currentType == 1) {
                width = listView.getWidth();
                height = ((View) getParent()).getMeasuredHeight();
            } else if (currentType == 2) {
                height = width;
            }
            if (currentBlock != null) {
                TLRPC.Photo photo = getPhotoWithId(currentBlock.photo_id);
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
                if (photo != null) {
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80, true);
                    if (image == thumb) {
                        thumb = null;
                    }
                    if (currentType == 0) {
                        float scale;
                        scale = photoWidth / (float) image.w;
                        height = (int) (scale * image.h);
                        if (parentBlock instanceof TLRPC.TL_pageBlockCover) {
                            height = Math.min(height, photoWidth);
                        } else {
                            int maxHeight = (int) ((Math.max(listView.getMeasuredWidth(), listView.getMeasuredHeight()) - AndroidUtilities.dp(56)) * 0.9f);
                            if (height > maxHeight) {
                                height = maxHeight;
                                scale = height / (float) image.h;
                                photoWidth = (int) (scale * image.w);
                                photoX += (width - photoX - photoWidth) / 2;
                            }
                        }
                    }
                    imageView.setImageCoords(photoX, (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) ? 0 : AndroidUtilities.dp(8), photoWidth, height);
                    String filter;
                    if (currentType == 0) {
                        filter = null;
                    } else {
                        filter = String.format(Locale.US, "%d_%d", photoWidth, height);
                    }
                    imageView.setImage(image.location, filter, thumb != null ? thumb.location : null, thumb != null ? "80_80_b" : null, image.size, null, 1);
                }

                if (currentType == 0 && lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.caption, textWidth, currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
                }
                if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
                    height += AndroidUtilities.dp(8);
                }
                boolean nextIsChannel = parentBlock instanceof TLRPC.TL_pageBlockCover && blocks != null && blocks.size() > 1 && blocks.get(1) instanceof TLRPC.TL_pageBlockChannel;
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
            imageView.draw(canvas);
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY = imageView.getImageY() + imageView.getImageHeight() + AndroidUtilities.dp(8));
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockChannelCell extends FrameLayout {

        private ContextProgressView progressView;
        private TextView textView;
        private ImageView imageView;
        private int currentState;

        private StaticLayout textLayout;
        private int buttonWidth;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(11);
        private int textX2;
        private int textY2 = AndroidUtilities.dp(11.5f);
        private Paint backgroundPaint;
        private AnimatorSet currentAnimation;
        private int currentType;

        private TLRPC.TL_pageBlockChannel currentBlock;

        public BlockChannelCell(Context context, int type) {
            super(context);
            setWillNotDraw(false);
            backgroundPaint = new Paint();
            currentType = type;

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 39, Gravity.RIGHT | Gravity.TOP));
            textView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentState != 0) {
                        return;
                    }
                    setState(1, true);
                    joinChannel(BlockChannelCell.this, loadedChannel);
                }
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
            int color = getSelectedColor();
            if (currentType == 0) {
                textView.setTextColor(0xff1d8dd8);
                if (color == 0) {
                    backgroundPaint.setColor(0xfff7f7f7);
                } else if (color == 1) {
                    backgroundPaint.setColor(0xffe5dec8);
                } else if (color == 2) {
                    backgroundPaint.setColor(0xff262626);
                }
                imageView.setColorFilter(new PorterDuffColorFilter(0xff999999, PorterDuff.Mode.MULTIPLY));
            } else {
                textView.setTextColor(0xffffffff);
                backgroundPaint.setColor(0x7f000000);
                imageView.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
            }
            lastCreatedWidth = 0;
            TLRPC.Chat channel = MessagesController.getInstance().getChat(block.channel.id);
            if (channel == null || channel.min) {
                loadChannel(this, block.channel);
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
                        ObjectAnimator.ofFloat(textView, "alpha", state == 0 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(textView, "scaleX", state == 0 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(textView, "scaleY", state == 0 ? 1.0f : 0.1f),

                        ObjectAnimator.ofFloat(progressView, "alpha", state == 1 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(progressView, "scaleX", state == 1 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(progressView, "scaleY", state == 1 ? 1.0f : 0.1f),

                        ObjectAnimator.ofFloat(imageView, "alpha", state == 2 ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(imageView, "scaleX", state == 2 ? 1.0f : 0.1f),
                        ObjectAnimator.ofFloat(imageView, "scaleY", state == 2 ? 1.0f : 0.1f)
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
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, AndroidUtilities.dp(39 + 9));

            textView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            buttonWidth = textView.getMeasuredWidth();
            progressView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39), MeasureSpec.EXACTLY));
            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(currentBlock.channel.title, null, width - AndroidUtilities.dp(36 + 16) - buttonWidth, currentBlock);
                    //lastCreatedWidth = width;
                    textX2 = getMeasuredWidth() - textX - buttonWidth;
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
            if (textLayout != null) {
                canvas.save();
                canvas.translate(textX, textY);
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockAuthorDateCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX;
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockAuthorDate currentBlock;

        public BlockAuthorDateCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockAuthorDate block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    CharSequence text;
                    CharSequence author = getText(currentBlock.author, currentBlock.author, currentBlock); //TODO support styles
                    Spannable spannableAuthor;
                    MetricAffectingSpan spans[];
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
                    textLayout = createLayoutForText(text, null, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                        if (isRtl == 1) {
                            textX = (int) Math.floor(width - textLayout.getLineWidth(0) - AndroidUtilities.dp(16));
                        } else {
                            textX = AndroidUtilities.dp(18);
                        }
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockTitleCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;

        private TLRPC.TL_pageBlockTitle currentBlock;
        private int textX = AndroidUtilities.dp(18);
        private int textY;

        public BlockTitleCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockTitle block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                        if (isRtl == -1) {
                            int count = textLayout.getLineCount();
                            for (int a = 0; a < count; a++) {
                                if (textLayout.getLineLeft(a) > 0) {
                                    isRtl = 1;
                                    break;
                                }
                            }
                            if (isRtl == -1) {
                                isRtl = 0;
                            }
                        }
                    }
                    if (currentBlock.first) {
                        height += AndroidUtilities.dp(8);
                        textY = AndroidUtilities.dp(16);
                    } else {
                        textY = AndroidUtilities.dp(8);
                    }

                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockFooterCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockFooter currentBlock;

        public BlockFooterCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockFooter block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

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
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(18) - textX, currentBlock);
                    if (textLayout != null) {
                        height = textLayout.getHeight();
                        if (currentBlock.level > 0) {
                            height += AndroidUtilities.dp(8);
                        } else {
                            height += AndroidUtilities.dp(8 + 8);
                        }
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (currentBlock.level > 0) {
                canvas.drawRect(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(20), getMeasuredHeight() - (currentBlock.bottom ? AndroidUtilities.dp(6) : 0), quoteLinePaint);
            }
        }
    }

    private class BlockPreformattedCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;

        private TLRPC.TL_pageBlockPreformatted currentBlock;

        public BlockPreformattedCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockPreformatted block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(24), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8 + 8 + 8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
            canvas.drawRect(0, AndroidUtilities.dp(8), getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(8), preformattedBackgroundPaint);
            if (textLayout != null) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(12), AndroidUtilities.dp(16));
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class BlockSubheaderCell extends View {

        private StaticLayout textLayout;
        private int lastCreatedWidth;
        private int textX = AndroidUtilities.dp(18);
        private int textY = AndroidUtilities.dp(8);

        private TLRPC.TL_pageBlockSubheader currentBlock;

        public BlockSubheaderCell(Context context) {
            super(context);
        }

        public void setBlock(TLRPC.TL_pageBlockSubheader block) {
            currentBlock = block;
            lastCreatedWidth = 0;
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return checkLayoutForLinks(event, this, textLayout, textX, textY) || super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = 0;

            if (currentBlock != null) {
                if (lastCreatedWidth != width) {
                    textLayout = createLayoutForText(null, currentBlock.text, width - AndroidUtilities.dp(36), currentBlock);
                    if (textLayout != null) {
                        height += AndroidUtilities.dp(8 + 8) + textLayout.getHeight();
                    }
                    //lastCreatedWidth = width;
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
                drawLayoutLink(canvas, textLayout);
                textLayout.draw(canvas);
                canvas.restore();
            }
        }
    }


    //------------ photo viewer

    private int coords[] = new int[2];

    private boolean isPhotoVisible;

    private ActionBar actionBar;
    private boolean isActionBarVisible = true;

    private static Drawable[] progressDrawables;

    private ClippingImageView animatingImageView;
    private FrameLayout bottomLayout;

    private ActionBarMenuItem menuItem;
    private PhotoBackgroundDrawable photoBackgroundDrawable = new PhotoBackgroundDrawable(0xff000000);
    private Paint blackPaint = new Paint();

    private RadialProgressView radialProgressViews[] = new RadialProgressView[3];
    private AnimatorSet currentActionBarAnimation;

    private TextView captionTextView;
    private TextView captionTextViewOld;
    private TextView captionTextViewNew;

    private AnimatedFileDrawable currentAnimation;

    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private VideoPlayer videoPlayer;
    private FrameLayout videoPlayerControlFrameLayout;
    private ImageView videoPlayButton;
    private TextView videoPlayerTime;
    private SeekBar videoPlayerSeekbar;
    private boolean textureUploaded;
    private boolean videoCrossfadeStarted;
    private float videoCrossfadeAlpha;
    private long videoCrossfadeAlphaLastTime;
    private boolean isPlaying;
    private Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer != null && videoPlayerSeekbar != null) {
                if (!videoPlayerSeekbar.isDragging()) {
                    float progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
                    videoPlayerSeekbar.setProgress(progress);
                    videoPlayerControlFrameLayout.invalidate();
                    updateVideoPlayerTime();
                }
            }
            if (isPlaying) {
                AndroidUtilities.runOnUIThread(updateProgressRunnable, 100);
            }
        }
    };

    private float animationValues[][] = new float[2][8];

    private int photoAnimationInProgress;
    private long photoTransitionAnimationStartTime;
    private Runnable photoAnimationEndRunnable;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private boolean disableShowCheck;

    private ImageReceiver leftImage = new ImageReceiver();
    private ImageReceiver centerImage = new ImageReceiver();
    private ImageReceiver rightImage = new ImageReceiver();
    private int currentIndex;
    private TLRPC.PageBlock currentMedia;
    private String currentFileNames[] = new String[3];
    private PlaceProviderObject currentPlaceObject;
    private Bitmap currentThumb;

    private boolean wasLayout;
    private boolean dontResetZoomOnFirstLayout;

    private boolean draggingDown;
    private float dragY;
    private float translationX;
    private float translationY;
    private float scale = 1;
    private float animateToX;
    private float animateToY;
    private float animateToScale;
    private float animationValue;
    private int currentRotation;
    private long animationStartTime;
    private AnimatorSet imageMoveAnimation;
    private GestureDetector gestureDetector;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float pinchStartDistance;
    private float pinchStartScale = 1;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;
    private float moveStartX;
    private float moveStartY;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;
    private boolean canZoom = true;
    private boolean changingPage;
    private boolean zooming;
    private boolean moving;
    private boolean doubleTap;
    private boolean invalidCoords;
    private boolean canDragDown = true;
    private boolean zoomAnimation;
    private boolean discardTap;
    private int switchImageAfterAnimation;
    private VelocityTracker velocityTracker;
    private Scroller scroller;

    private ArrayList<TLRPC.PageBlock> imagesArr = new ArrayList<>();

    private final static int gallery_menu_save = 1;
    private final static int gallery_menu_share = 2;
    private final static int gallery_menu_openin = 3;

    private static DecelerateInterpolator decelerateInterpolator;
    private static Paint progressPaint;
    private static Paint dotsPaint;

    private class PhotoBackgroundDrawable extends ColorDrawable {

        private Runnable drawRunnable;

        public PhotoBackgroundDrawable(int color) {
            super(color);
        }

        @Override
        public void setAlpha(int alpha) {
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isPhotoVisible || alpha != 255);
            }
            super.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    drawRunnable.run();
                    drawRunnable = null;
                }
            }
        }
    }

    private class RadialProgressView {

        private long lastUpdateTime = 0;
        private float radOffset = 0;
        private float currentProgress = 0;
        private float animationProgressStart = 0;
        private long currentProgressTime = 0;
        private float animatedProgressValue = 0;
        private RectF progressRect = new RectF();
        private int backgroundState = -1;
        private View parent = null;
        private int size = AndroidUtilities.dp(64);
        private int previousBackgroundState = -2;
        private float animatedAlphaValue = 1.0f;
        private float alpha = 1.0f;
        private float scale = 1.0f;

        public RadialProgressView(Context context, View parentView) {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator(1.5f);
                progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                progressPaint.setStyle(Paint.Style.STROKE);
                progressPaint.setStrokeCap(Paint.Cap.ROUND);
                progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
                progressPaint.setColor(0xffffffff);
            }
            parent = parentView;
        }

        private void updateAnimation() {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;

            if (animatedProgressValue != 1) {
                radOffset += 360 * dt / 3000.0f;
                float progressDiff = currentProgress - animationProgressStart;
                if (progressDiff > 0) {
                    currentProgressTime += dt;
                    if (currentProgressTime >= 300) {
                        animatedProgressValue = currentProgress;
                        animationProgressStart = currentProgress;
                        currentProgressTime = 0;
                    } else {
                        animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                    }
                }
                parent.invalidate();
            }
            if (animatedProgressValue >= 1 && previousBackgroundState != -2) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousBackgroundState = -2;
                }
                parent.invalidate();
            }
        }

        public void setProgress(float value, boolean animated) {
            if (!animated) {
                animatedProgressValue = value;
                animationProgressStart = value;
            } else {
                animationProgressStart = animatedProgressValue;
            }
            currentProgress = value;
            currentProgressTime = 0;
        }

        public void setBackgroundState(int state, boolean animated) {
            lastUpdateTime = System.currentTimeMillis();
            if (animated && backgroundState != state) {
                previousBackgroundState = backgroundState;
                animatedAlphaValue = 1.0f;
            } else {
                previousBackgroundState = -2;
            }
            backgroundState = state;
            parent.invalidate();
        }

        public void setAlpha(float value) {
            alpha = value;
        }

        public void setScale(float value) {
            scale = value;
        }

        public void onDraw(Canvas canvas) {
            int sizeScaled = (int) (size * scale);
            int x = (getContainerViewWidth() - sizeScaled) / 2;
            int y = (getContainerViewHeight() - sizeScaled) / 2;

            if (previousBackgroundState >= 0 && previousBackgroundState < 4) {
                Drawable drawable = progressDrawables[previousBackgroundState];
                if (drawable != null) {
                    drawable.setAlpha((int) (255 * animatedAlphaValue * alpha));
                    drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState >= 0 && backgroundState < 4) {
                Drawable drawable = progressDrawables[backgroundState];
                if (drawable != null) {
                    if (previousBackgroundState != -2) {
                        drawable.setAlpha((int) (255 * (1.0f - animatedAlphaValue) * alpha));
                    } else {
                        drawable.setAlpha((int) (255 * alpha));
                    }
                    drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
                    drawable.draw(canvas);
                }
            }

            if (backgroundState == 0 || backgroundState == 1 || previousBackgroundState == 0 || previousBackgroundState == 1) {
                int diff = AndroidUtilities.dp(4);
                if (previousBackgroundState != -2) {
                    progressPaint.setAlpha((int) (255 * animatedAlphaValue * alpha));
                } else {
                    progressPaint.setAlpha((int) (255 * alpha));
                }
                progressRect.set(x + diff, y + diff, x + sizeScaled - diff, y + sizeScaled - diff);
                canvas.drawArc(progressRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
                updateAnimation();
            }
        }
    }

    public static class PlaceProviderObject {
        public ImageReceiver imageReceiver;
        public int viewX;
        public int viewY;
        public View parentView;
        public Bitmap thumb;
        public int index;
        public int size;
        public int radius;
        public int clipBottomAddition;
        public int clipTopAddition;
        public float scale = 1.0f;
    }

    private void onSharePressed() {
        if (parentActivity == null || currentMedia == null) {
            return;
        }
        try {
            File f = getMediaFile(currentIndex);
            if (f != null && f.exists()) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                if (isMediaVideo(currentIndex)) {
                    intent.setType("video/mp4");
                } else {
                    intent.setType("image/jpeg");
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    try {
                        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(parentActivity, BuildConfig.APPLICATION_ID + ".provider", f));
                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignore) {
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                    }
                } else {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                }
                parentActivity.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.setMessage(LocaleController.getString("PleaseDownload", R.string.PleaseDownload));
                showDialog(builder.create());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void setScaleToFill() {
        float bitmapWidth = centerImage.getBitmapWidth();
        float containerWidth = getContainerViewWidth();
        float bitmapHeight = centerImage.getBitmapHeight();
        float containerHeight = getContainerViewHeight();
        float scaleFit = Math.min(containerHeight / bitmapHeight, containerWidth / bitmapWidth);
        float width = (int) (bitmapWidth * scaleFit);
        float height = (int) (bitmapHeight * scaleFit);
        scale = Math.max(containerWidth / width, containerHeight / height);
        updateMinMax(scale);
    }

    private void updateVideoPlayerTime() {
        String newText;
        if (videoPlayer == null) {
            newText = String.format("%02d:%02d / %02d:%02d", 0, 0, 0, 0);
        } else {
            long current = videoPlayer.getCurrentPosition() / 1000;
            long total = videoPlayer.getDuration();
            total /= 1000;
            if (total != C.TIME_UNSET && current != C.TIME_UNSET) {
                newText = String.format("%02d:%02d / %02d:%02d", current / 60, current % 60, total / 60, total % 60);
            } else {
                newText = String.format("%02d:%02d / %02d:%02d", 0, 0, 0, 0);
            }
        }
        if (!TextUtils.equals(videoPlayerTime.getText(), newText)) {
            videoPlayerTime.setText(newText);
        }
    }

    @SuppressLint("NewApi")
    private void preparePlayer(File file, boolean playWhenReady) {
        if (parentActivity == null) {
            return;
        }
        releasePlayer();
        if (videoTextureView == null) {
            aspectRatioFrameLayout = new AspectRatioFrameLayout(parentActivity);
            aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
            photoContainerView.addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            videoTextureView = new TextureView(parentActivity);
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
        textureUploaded = false;
        videoCrossfadeStarted = false;
        videoTextureView.setAlpha(videoCrossfadeAlpha = 0.0f);
        videoPlayButton.setImageResource(R.drawable.inline_video_play);
        if (videoPlayer == null) {
            videoPlayer = new VideoPlayer();
            videoPlayer.setTextureView(videoTextureView);
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null) {
                        return;
                    }
                    if (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE) {
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
                    if (playbackState == ExoPlayer.STATE_READY && aspectRatioFrameLayout.getVisibility() != View.VISIBLE) {
                        aspectRatioFrameLayout.setVisibility(View.VISIBLE);
                    }
                    if (videoPlayer.isPlaying() && playbackState != ExoPlayer.STATE_ENDED) {
                        if (!isPlaying) {
                            isPlaying = true;
                            videoPlayButton.setImageResource(R.drawable.inline_video_pause);
                            AndroidUtilities.runOnUIThread(updateProgressRunnable);
                        }
                    } else if (isPlaying) {
                        isPlaying = false;
                        videoPlayButton.setImageResource(R.drawable.inline_video_play);
                        AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
                        if (playbackState == ExoPlayer.STATE_ENDED) {
                            if (!videoPlayerSeekbar.isDragging()) {
                                videoPlayerSeekbar.setProgress(0.0f);
                                videoPlayerControlFrameLayout.invalidate();
                                videoPlayer.seekTo(0);
                                videoPlayer.pause();
                            }
                        }
                    }
                    updateVideoPlayerTime();
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e(e);
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    if (aspectRatioFrameLayout != null) {
                        if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                        aspectRatioFrameLayout.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height, unappliedRotationDegrees);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (!textureUploaded) {
                        textureUploaded = true;
                        containerView.invalidate();
                    }
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            });
            long duration;
            if (videoPlayer != null) {
                duration = videoPlayer.getDuration();
                if (duration == C.TIME_UNSET) {
                    duration = 0;
                }
            } else {
                duration = 0;
            }
            duration /= 1000;
            int size = (int) Math.ceil(videoPlayerTime.getPaint().measureText(String.format("%02d:%02d / %02d:%02d", duration / 60, duration % 60, duration / 60, duration % 60)));
        }
        videoPlayer.preparePlayer(Uri.fromFile(file), "other");
        bottomLayout.setVisibility(View.VISIBLE);
        videoPlayer.setPlayWhenReady(playWhenReady);
    }

    private void releasePlayer() {
        if (videoPlayer != null) {
            videoPlayer.releasePlayer();
            videoPlayer = null;
        }
        try {
            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (aspectRatioFrameLayout != null) {
            photoContainerView.removeView(aspectRatioFrameLayout);
            aspectRatioFrameLayout = null;
        }
        if (videoTextureView != null) {
            videoTextureView = null;
        }
        if (isPlaying) {
            isPlaying = false;
            videoPlayButton.setImageResource(R.drawable.inline_video_play);
            AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
        }
        bottomLayout.setVisibility(View.GONE);
    }

    private void toggleActionBar(boolean show, final boolean animated) {
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (videoPlayer != null) {
                bottomLayout.setVisibility(View.VISIBLE);
            }
            if (captionTextView.getTag() != null) {
                captionTextView.setVisibility(View.VISIBLE);
            }
        }
        isActionBarVisible = show;
        actionBar.setEnabled(show);
        bottomLayout.setEnabled(show);

        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(bottomLayout, "alpha", show ? 1.0f : 0.0f));
            if (captionTextView.getTag() != null) {
                arrayList.add(ObjectAnimator.ofFloat(captionTextView, "alpha", show ? 1.0f : 0.0f));
            }
            currentActionBarAnimation = new AnimatorSet();
            currentActionBarAnimation.playTogether(arrayList);
            if (!show) {
                currentActionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentActionBarAnimation != null && currentActionBarAnimation.equals(animation)) {
                            actionBar.setVisibility(View.GONE);
                            if (videoPlayer != null) {
                                bottomLayout.setVisibility(View.GONE);
                            }
                            if (captionTextView.getTag() != null) {
                                captionTextView.setVisibility(View.INVISIBLE);
                            }
                            currentActionBarAnimation = null;
                        }
                    }
                });
            }

            currentActionBarAnimation.setDuration(200);
            currentActionBarAnimation.start();
        } else {
            actionBar.setAlpha(show ? 1.0f : 0.0f);
            bottomLayout.setAlpha(show ? 1.0f : 0.0f);
            if (captionTextView.getTag() != null) {
                captionTextView.setAlpha(show ? 1.0f : 0.0f);
            }
            if (!show) {
                actionBar.setVisibility(View.GONE);
                if (videoPlayer != null) {
                    bottomLayout.setVisibility(View.GONE);
                }
                if (captionTextView.getTag() != null) {
                    captionTextView.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private String getFileName(int index) {
        TLObject media = getMedia(index);
        if (media instanceof TLRPC.Photo) {
            media = FileLoader.getClosestPhotoSizeWithSize(((TLRPC.Photo) media).sizes, AndroidUtilities.getPhotoSize());
        }
        return FileLoader.getAttachFileName(media);
    }

    private TLObject getMedia(int index) {
        if (imagesArr.isEmpty() || index >= imagesArr.size() || index < 0) {
            return null;
        }
        TLRPC.PageBlock block = imagesArr.get(index);
        if (block.photo_id != 0) {
            return getPhotoWithId(block.photo_id);
        } else if (block.video_id != 0) {
            return getDocumentWithId(block.video_id);
        }
        return null;
    }

    private File getMediaFile(int index) {
        if (imagesArr.isEmpty() || index >= imagesArr.size() || index < 0) {
            return null;
        }
        TLRPC.PageBlock block = imagesArr.get(index);
        if (block.photo_id != 0) {
            TLRPC.Photo photo = getPhotoWithId(block.photo_id);
            if (photo != null) {
                TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (sizeFull != null) {
                    return FileLoader.getPathToAttach(sizeFull, true);
                }
            }
        } else if (block.video_id != 0) {
            TLRPC.Document document = getDocumentWithId(block.video_id);
            if (document != null) {
                return FileLoader.getPathToAttach(document, true);
            }
        }
        return null;
    }

    private boolean isVideoBlock(TLRPC.PageBlock block) {
        if (block != null && block.video_id != 0) {
            TLRPC.Document document = getDocumentWithId(block.video_id);
            if (document != null) {
                return MessageObject.isVideoDocument(document);
            }
        }
        return false;
    }

    private boolean isMediaVideo(int index) {
        return !(imagesArr.isEmpty() || index >= imagesArr.size() || index < 0) && isVideoBlock(imagesArr.get(index));
    }

    private TLRPC.FileLocation getFileLocation(int index, int size[]) {
        if (index < 0 || index >= imagesArr.size()) {
            return null;
        }
        TLObject media = getMedia(index);
        if (media instanceof TLRPC.Photo) {
            TLRPC.Photo photo = (TLRPC.Photo) media;
            TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            if (sizeFull != null) {
                size[0] = sizeFull.size;
                if (size[0] == 0) {
                    size[0] = -1;
                }
                return sizeFull.location;
            } else {
                size[0] = -1;
            }
        } else if (media instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) media;
            if (document.thumb != null) {
                size[0] = document.thumb.size;
                if (size[0] == 0) {
                    size[0] = -1;
                }
                return document.thumb.location;
            }
        }
        return null;
    }

    private void onPhotoShow(int index, final PlaceProviderObject object) {
        currentIndex = -1;
        currentFileNames[0] = null;
        currentFileNames[1] = null;
        currentFileNames[2] = null;
        currentThumb = object != null ? object.thumb : null;
        menuItem.setVisibility(View.VISIBLE);
        menuItem.hideSubItem(gallery_menu_openin);
        actionBar.setTranslationY(0);
        captionTextView.setTag(null);
        captionTextView.setVisibility(View.INVISIBLE);

        for (int a = 0; a < 3; a++) {
            if (radialProgressViews[a] != null) {
                radialProgressViews[a].setBackgroundState(-1, false);
            }
        }

        setImageIndex(index, true);

        if (currentMedia != null && isMediaVideo(currentIndex)) {
            onActionClick(false);
        }
    }

    private void setImages() {
        if (photoAnimationInProgress == 0) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(rightImage, currentIndex + 1);
            setIndexToImage(leftImage, currentIndex - 1);
        }
    }

    private void setImageIndex(int index, boolean init) {
        if (currentIndex == index) {
            return;
        }
        if (!init) {
            currentThumb = null;
        }
        currentFileNames[0] = getFileName(index);
        currentFileNames[1] = getFileName(index + 1);
        currentFileNames[2] = getFileName(index - 1);

        int prevIndex = currentIndex;
        currentIndex = index;
        boolean isVideo = false;
        boolean sameImage = false;

        if (!imagesArr.isEmpty()) {
            if (currentIndex < 0 || currentIndex >= imagesArr.size()) {
                closePhoto(false);
                return;
            }
            TLRPC.PageBlock newMedia = imagesArr.get(currentIndex);
            sameImage = currentMedia != null && currentMedia == newMedia;
            currentMedia = newMedia;
            isVideo = isMediaVideo(currentIndex);
            if (isVideo) {
                menuItem.showSubItem(gallery_menu_openin);
            }
            setCurrentCaption(getText(currentMedia.caption, currentMedia.caption, currentMedia));
            if (currentAnimation != null) {
                menuItem.setVisibility(View.GONE);
                menuItem.hideSubItem(gallery_menu_save);
                actionBar.setTitle(LocaleController.getString("AttachGif", R.string.AttachGif));
            } else {
                menuItem.setVisibility(View.VISIBLE);
                if (imagesArr.size() == 1) {
                    if (isVideo) {
                        actionBar.setTitle(LocaleController.getString("AttachVideo", R.string.AttachVideo));
                    } else {
                        actionBar.setTitle(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                    }
                } else {
                    actionBar.setTitle(LocaleController.formatString("Of", R.string.Of, currentIndex + 1, imagesArr.size()));
                }
                menuItem.showSubItem(gallery_menu_save);
            }
        }

        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof BlockSlideshowCell) {
                BlockSlideshowCell cell = (BlockSlideshowCell) child;
                int idx = cell.currentBlock.items.indexOf(currentMedia);
                if (idx != -1) {
                    cell.innerListView.setCurrentItem(idx, false);
                    break;
                }
            }
        }

        if (currentPlaceObject != null) {
            if (photoAnimationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(true, true);
            } else {
                showAfterAnimation = currentPlaceObject;
            }
        }
        currentPlaceObject = getPlaceForPhoto(currentMedia);
        if (currentPlaceObject != null) {
            if (photoAnimationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(false, true);
            } else {
                hideAfterAnimation = currentPlaceObject;
            }
        }

        if (!sameImage) {
            draggingDown = false;
            translationX = 0;
            translationY = 0;
            scale = 1;
            animateToX = 0;
            animateToY = 0;
            animateToScale = 1;
            animationStartTime = 0;
            imageMoveAnimation = null;
            if (aspectRatioFrameLayout != null) {
                aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
            }
            releasePlayer();

            pinchStartDistance = 0;
            pinchStartScale = 1;
            pinchCenterX = 0;
            pinchCenterY = 0;
            pinchStartX = 0;
            pinchStartY = 0;
            moveStartX = 0;
            moveStartY = 0;
            zooming = false;
            moving = false;
            doubleTap = false;
            invalidCoords = false;
            canDragDown = true;
            changingPage = false;
            switchImageAfterAnimation = 0;
            canZoom = (currentFileNames[0] != null && !isVideo && radialProgressViews[0].backgroundState != 0);
            updateMinMax(scale);
        }

        if (prevIndex == -1) {
            setImages();

            for (int a = 0; a < 3; a++) {
                checkProgress(a, false);
            }
        } else {
            checkProgress(0, false);
            if (prevIndex > currentIndex) {
                ImageReceiver temp = rightImage;
                rightImage = centerImage;
                centerImage = leftImage;
                leftImage = temp;

                RadialProgressView tempProgress = radialProgressViews[0];
                radialProgressViews[0] = radialProgressViews[2];
                radialProgressViews[2] = tempProgress;
                setIndexToImage(leftImage, currentIndex - 1);

                checkProgress(1, false);
                checkProgress(2, false);
            } else if (prevIndex < currentIndex) {
                ImageReceiver temp = leftImage;
                leftImage = centerImage;
                centerImage = rightImage;
                rightImage = temp;

                RadialProgressView tempProgress = radialProgressViews[0];
                radialProgressViews[0] = radialProgressViews[1];
                radialProgressViews[1] = tempProgress;
                setIndexToImage(rightImage, currentIndex + 1);

                checkProgress(1, false);
                checkProgress(2, false);
            }
        }
    }

    private void setCurrentCaption(final CharSequence caption) {
        if (!TextUtils.isEmpty(caption)) {
            captionTextView = captionTextViewOld;
            captionTextViewOld = captionTextViewNew;
            captionTextViewNew = captionTextView;
            Theme.createChatResources(null, true);
            CharSequence str = Emoji.replaceEmoji(new SpannableStringBuilder(caption.toString()), captionTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            captionTextView.setTag(str);
            captionTextView.setText(str);
            captionTextView.setTextColor(0xffffffff);
            captionTextView.setAlpha(actionBar.getVisibility() == View.VISIBLE ? 1.0f : 0.0f);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    captionTextViewOld.setTag(null);
                    captionTextViewOld.setVisibility(View.INVISIBLE);
                    captionTextViewNew.setVisibility(actionBar.getVisibility() == View.VISIBLE ? View.VISIBLE : View.INVISIBLE);
                }
            });
        } else {
            captionTextView.setTextColor(0xffffffff);
            captionTextView.setTag(null);
            captionTextView.setVisibility(View.INVISIBLE);
        }
    }

    private void checkProgress(int a, boolean animated) {
        if (currentFileNames[a] != null) {
            int index = currentIndex;
            if (a == 1) {
                index += 1;
            } else if (a == 2) {
                index -= 1;
            }
            File f = getMediaFile(index);
            boolean isVideo = isMediaVideo(index);
            if (f != null && f.exists()) {
                if (isVideo) {
                    radialProgressViews[a].setBackgroundState(3, animated);
                } else {
                    radialProgressViews[a].setBackgroundState(-1, animated);
                }
            } else {
                if (isVideo) {
                    if (!FileLoader.getInstance().isLoadingFile(currentFileNames[a])) {
                        radialProgressViews[a].setBackgroundState(2, false);
                    } else {
                        radialProgressViews[a].setBackgroundState(1, false);
                    }
                } else {
                    radialProgressViews[a].setBackgroundState(0, animated);
                }
                Float progress = ImageLoader.getInstance().getFileProgress(currentFileNames[a]);
                if (progress == null) {
                    progress = 0.0f;
                }
                radialProgressViews[a].setProgress(progress, false);
            }
            if (a == 0) {
                canZoom = (currentFileNames[0] != null && !isVideo && radialProgressViews[0].backgroundState != 0);
            }
        } else {
            radialProgressViews[a].setBackgroundState(-1, animated);
        }
    }

    private void setIndexToImage(ImageReceiver imageReceiver, int index) {
        imageReceiver.setOrientation(0, false);

        int size[] = new int[1];
        TLRPC.FileLocation fileLocation = getFileLocation(index, size);

        if (fileLocation != null) {
            TLObject media = getMedia(index);
            if (media instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) media;
                Bitmap placeHolder = null;
                if (currentThumb != null && imageReceiver == centerImage) {
                    placeHolder = currentThumb;
                }
                if (size[0] == 0) {
                    size[0] = -1;
                }
                TLRPC.PhotoSize thumbLocation = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80);
                imageReceiver.setImage(fileLocation, null, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, thumbLocation != null ? thumbLocation.location : null, "b", size[0], null, 1);
            } else if (isMediaVideo(index)) {
                if (!(fileLocation instanceof TLRPC.TL_fileLocationUnavailable)) {
                    Bitmap placeHolder = null;
                    if (currentThumb != null && imageReceiver == centerImage) {
                        placeHolder = currentThumb;
                    }
                    imageReceiver.setImage(null, null, null, placeHolder != null ? new BitmapDrawable(null, placeHolder) : null, fileLocation, "b", 0, null, 1);
                } else {
                    imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
                }
            } else if (currentAnimation != null) {
                imageReceiver.setImageBitmap(currentAnimation);
                currentAnimation.setSecondParentView(photoContainerView);
            } else {
                //TODO gif
            }
        } else {
            if (size[0] == 0) {
                imageReceiver.setImageBitmap((Bitmap) null);
            } else {
                imageReceiver.setImageBitmap(parentActivity.getResources().getDrawable(R.drawable.photoview_placeholder));
            }
        }
    }

    public boolean isShowingImage(TLRPC.PageBlock object) {
        return isPhotoVisible && !disableShowCheck && object != null && currentMedia == object;
    }

    private boolean checkPhotoAnimation() {
        if (photoAnimationInProgress != 0) {
            if (Math.abs(photoTransitionAnimationStartTime - System.currentTimeMillis()) >= 500) {
                if (photoAnimationEndRunnable != null) {
                    photoAnimationEndRunnable.run();
                    photoAnimationEndRunnable = null;
                }
                photoAnimationInProgress = 0;
            }
        }
        return photoAnimationInProgress != 0;
    }

    public boolean openPhoto(TLRPC.PageBlock block) {
        if (parentActivity == null || isPhotoVisible || checkPhotoAnimation() || block == null) {
            return false;
        }

        final PlaceProviderObject object = getPlaceForPhoto(block);
        if (object == null) {
            return false;
        }

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        isPhotoVisible = true;
        toggleActionBar(true, false);
        actionBar.setAlpha(0.0f);
        bottomLayout.setAlpha(0.0f);
        captionTextView.setAlpha(0.0f);
        photoBackgroundDrawable.setAlpha(0);
        disableShowCheck = true;
        photoAnimationInProgress = 1;
        if (block != null) {
            currentAnimation = object.imageReceiver.getAnimation();
        }
        int index = photoBlocks.indexOf(block);

        imagesArr.clear();
        if (!(block instanceof TLRPC.TL_pageBlockVideo) || isVideoBlock(block)) {
            imagesArr.addAll(photoBlocks);
        } else {
            imagesArr.add(block);
            index = 0;
        }

        onPhotoShow(index, object);

        final Rect drawRegion = object.imageReceiver.getDrawRegion();
        int orientation = object.imageReceiver.getOrientation();
        int animatedOrientation = object.imageReceiver.getAnimatedOrientation();
        if (animatedOrientation != 0) {
            orientation = animatedOrientation;
        }

        animatingImageView.setVisibility(View.VISIBLE);
        animatingImageView.setRadius(object.radius);
        animatingImageView.setOrientation(orientation);
        animatingImageView.setNeedRadius(object.radius != 0);
        animatingImageView.setImageBitmap(object.thumb);

        animatingImageView.setAlpha(1.0f);
        animatingImageView.setPivotX(0.0f);
        animatingImageView.setPivotY(0.0f);
        animatingImageView.setScaleX(object.scale);
        animatingImageView.setScaleY(object.scale);
        animatingImageView.setTranslationX(object.viewX + drawRegion.left * object.scale);
        animatingImageView.setTranslationY(object.viewY + drawRegion.top * object.scale);
        final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
        layoutParams.width = (drawRegion.right - drawRegion.left);
        layoutParams.height = (drawRegion.bottom - drawRegion.top);
        animatingImageView.setLayoutParams(layoutParams);

        float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
        float scaleY = (float) (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) / layoutParams.height;
        float scale = scaleX > scaleY ? scaleY : scaleX;
        float width = layoutParams.width * scale;
        float height = layoutParams.height * scale;
        float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
        if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
            xPos += ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
        }
        float yPos = ((AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) - height) / 2.0f;
        int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
        int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

        int coords2[] = new int[2];
        object.parentView.getLocationInWindow(coords2);
        int clipTop = coords2[1] - (object.viewY + drawRegion.top) + object.clipTopAddition;
        if (clipTop < 0) {
            clipTop = 0;
        }
        int clipBottom = (object.viewY + drawRegion.top + layoutParams.height) - (coords2[1] + object.parentView.getHeight()) + object.clipBottomAddition;
        if (clipBottom < 0) {
            clipBottom = 0;
        }
        clipTop = Math.max(clipTop, clipVertical);
        clipBottom = Math.max(clipBottom, clipVertical);

        animationValues[0][0] = animatingImageView.getScaleX();
        animationValues[0][1] = animatingImageView.getScaleY();
        animationValues[0][2] = animatingImageView.getTranslationX();
        animationValues[0][3] = animatingImageView.getTranslationY();
        animationValues[0][4] = clipHorizontal * object.scale;
        animationValues[0][5] = clipTop * object.scale;
        animationValues[0][6] = clipBottom * object.scale;
        animationValues[0][7] = animatingImageView.getRadius();

        animationValues[1][0] = scale;
        animationValues[1][1] = scale;
        animationValues[1][2] = xPos;
        animationValues[1][3] = yPos;
        animationValues[1][4] = 0;
        animationValues[1][5] = 0;
        animationValues[1][6] = 0;
        animationValues[1][7] = 0;

        photoContainerView.setVisibility(View.VISIBLE);
        photoContainerBackground.setVisibility(View.VISIBLE);
        animatingImageView.setAnimationProgress(0);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0, 255),
                ObjectAnimator.ofFloat(actionBar, "alpha", 0, 1.0f),
                ObjectAnimator.ofFloat(bottomLayout, "alpha", 0, 1.0f),
                ObjectAnimator.ofFloat(captionTextView, "alpha", 0, 1.0f)
        );

        photoAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                if (photoContainerView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 18) {
                    photoContainerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                photoAnimationInProgress = 0;
                photoTransitionAnimationStartTime = 0;
                setImages();
                photoContainerView.invalidate();
                animatingImageView.setVisibility(View.GONE);
                if (showAfterAnimation != null) {
                    showAfterAnimation.imageReceiver.setVisible(true, true);
                }
                if (hideAfterAnimation != null) {
                    hideAfterAnimation.imageReceiver.setVisible(false, true);
                }
            }
        };

        animatorSet.setDuration(200);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().setAnimationInProgress(false);
                        if (photoAnimationEndRunnable != null) {
                            photoAnimationEndRunnable.run();
                            photoAnimationEndRunnable = null;
                        }
                    }
                });
            }
        });
        photoTransitionAnimationStartTime = System.currentTimeMillis();
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats});
                NotificationCenter.getInstance().setAnimationInProgress(true);
                animatorSet.start();
            }
        });
        if (Build.VERSION.SDK_INT >= 18) {
            photoContainerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        photoBackgroundDrawable.drawRunnable = new Runnable() {
            @Override
            public void run() {
                disableShowCheck = false;
                object.imageReceiver.setVisible(false, true);
            }
        };
        return true;
    }

    public void closePhoto(boolean animated) {
        if (parentActivity == null || !isPhotoVisible || checkPhotoAnimation()) {
            return;
        }

        releasePlayer();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }

        final PlaceProviderObject object = getPlaceForPhoto(currentMedia);

        if (animated) {
            photoAnimationInProgress = 1;
            animatingImageView.setVisibility(View.VISIBLE);
            photoContainerView.invalidate();

            AnimatorSet animatorSet = new AnimatorSet();

            final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
            Rect drawRegion = null;
            int orientation = centerImage.getOrientation();
            int animatedOrientation = 0;
            if (object != null && object.imageReceiver != null) {
                animatedOrientation = object.imageReceiver.getAnimatedOrientation();
            }
            if (animatedOrientation != 0) {
                orientation = animatedOrientation;
            }
            animatingImageView.setOrientation(orientation);
            if (object != null) {
                animatingImageView.setNeedRadius(object.radius != 0);
                drawRegion = object.imageReceiver.getDrawRegion();
                layoutParams.width = drawRegion.right - drawRegion.left;
                layoutParams.height = drawRegion.bottom - drawRegion.top;
                animatingImageView.setImageBitmap(object.thumb);
            } else {
                animatingImageView.setNeedRadius(false);
                layoutParams.width = centerImage.getImageWidth();
                layoutParams.height = centerImage.getImageHeight();
                animatingImageView.setImageBitmap(centerImage.getBitmap());
            }
            animatingImageView.setLayoutParams(layoutParams);

            float scaleX = (float) AndroidUtilities.displaySize.x / layoutParams.width;
            float scaleY = (float) (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) / layoutParams.height;
            float scale2 = scaleX > scaleY ? scaleY : scaleX;
            float width = layoutParams.width * scale * scale2;
            float height = layoutParams.height * scale * scale2;
            float xPos = (AndroidUtilities.displaySize.x - width) / 2.0f;
            if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                xPos += ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
            }
            float yPos = (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight - height) / 2.0f;
            animatingImageView.setTranslationX(xPos + translationX);
            animatingImageView.setTranslationY(yPos + translationY);
            animatingImageView.setScaleX(scale * scale2);
            animatingImageView.setScaleY(scale * scale2);

            if (object != null) {
                object.imageReceiver.setVisible(false, true);
                int clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());

                int coords2[] = new int[2];
                object.parentView.getLocationInWindow(coords2);
                int clipTop = coords2[1] - (object.viewY + drawRegion.top) + object.clipTopAddition;
                if (clipTop < 0) {
                    clipTop = 0;
                }
                int clipBottom = (object.viewY + drawRegion.top + (drawRegion.bottom - drawRegion.top)) - (coords2[1] + object.parentView.getHeight()) + object.clipBottomAddition;
                if (clipBottom < 0) {
                    clipBottom = 0;
                }

                clipTop = Math.max(clipTop, clipVertical);
                clipBottom = Math.max(clipBottom, clipVertical);

                animationValues[0][0] = animatingImageView.getScaleX();
                animationValues[0][1] = animatingImageView.getScaleY();
                animationValues[0][2] = animatingImageView.getTranslationX();
                animationValues[0][3] = animatingImageView.getTranslationY();
                animationValues[0][4] = 0;
                animationValues[0][5] = 0;
                animationValues[0][6] = 0;
                animationValues[0][7] = 0;

                animationValues[1][0] = object.scale;
                animationValues[1][1] = object.scale;
                animationValues[1][2] = object.viewX + drawRegion.left * object.scale;
                animationValues[1][3] = object.viewY + drawRegion.top * object.scale;
                animationValues[1][4] = clipHorizontal * object.scale;
                animationValues[1][5] = clipTop * object.scale;
                animationValues[1][6] = clipBottom * object.scale;
                animationValues[1][7] = object.radius;

                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(animatingImageView, "animationProgress", 0.0f, 1.0f),
                        ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(actionBar, "alpha", 0),
                        ObjectAnimator.ofFloat(bottomLayout, "alpha", 0),
                        ObjectAnimator.ofFloat(captionTextView, "alpha", 0)
                );
            } else {
                int h = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight;
                animatorSet.playTogether(
                        ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(animatingImageView, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(animatingImageView, "translationY", translationY >= 0 ? h : -h),
                        ObjectAnimator.ofFloat(actionBar, "alpha", 0),
                        ObjectAnimator.ofFloat(bottomLayout, "alpha", 0),
                        ObjectAnimator.ofFloat(captionTextView, "alpha", 0)
                );
            }

            photoAnimationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= 18) {
                        photoContainerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    photoContainerView.setVisibility(View.INVISIBLE);
                    photoContainerBackground.setVisibility(View.INVISIBLE);
                    photoAnimationInProgress = 0;
                    onPhotoClosed(object);
                }
            };

            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (photoAnimationEndRunnable != null) {
                                photoAnimationEndRunnable.run();
                                photoAnimationEndRunnable = null;
                            }
                        }
                    });
                }
            });
            photoTransitionAnimationStartTime = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= 18) {
                photoContainerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            animatorSet.start();
        } else {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(photoContainerView, "scaleX", 0.9f),
                    ObjectAnimator.ofFloat(photoContainerView, "scaleY", 0.9f),
                    ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                    ObjectAnimator.ofFloat(actionBar, "alpha", 0),
                    ObjectAnimator.ofFloat(bottomLayout, "alpha", 0),
                    ObjectAnimator.ofFloat(captionTextView, "alpha", 0)
            );
            photoAnimationInProgress = 2;
            photoAnimationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (photoContainerView == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        photoContainerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    photoContainerView.setVisibility(View.INVISIBLE);
                    photoContainerBackground.setVisibility(View.INVISIBLE);
                    photoAnimationInProgress = 0;
                    onPhotoClosed(object);
                    photoContainerView.setScaleX(1.0f);
                    photoContainerView.setScaleY(1.0f);
                }
            };
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (photoAnimationEndRunnable != null) {
                        photoAnimationEndRunnable.run();
                        photoAnimationEndRunnable = null;
                    }
                }
            });
            photoTransitionAnimationStartTime = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= 18) {
                photoContainerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            animatorSet.start();
        }
        if (currentAnimation != null) {
            currentAnimation.setSecondParentView(null);
            currentAnimation = null;
            centerImage.setImageBitmap((Drawable) null);
        }
    }

    private void onPhotoClosed(PlaceProviderObject object) {
        isPhotoVisible = false;
        disableShowCheck = true;
        currentMedia = null;
        currentThumb = null;
        if (currentAnimation != null) {
            currentAnimation.setSecondParentView(null);
            currentAnimation = null;
        }
        for (int a = 0; a < 3; a++) {
            if (radialProgressViews[a] != null) {
                radialProgressViews[a].setBackgroundState(-1, false);
            }
        }
        centerImage.setImageBitmap((Bitmap) null);
        leftImage.setImageBitmap((Bitmap) null);
        rightImage.setImageBitmap((Bitmap) null);
        photoContainerView.post(new Runnable() {
            @Override
            public void run() {
                animatingImageView.setImageBitmap(null);
            }
        });
        disableShowCheck = false;
        if (object != null) {
            object.imageReceiver.setVisible(true, true);
        }
    }

    public void onPause() {
        if (currentAnimation != null) {
            closePhoto(false);
        }
    }

    private void updateMinMax(float scale) {
        int maxW = (int) (centerImage.getImageWidth() * scale - getContainerViewWidth()) / 2;
        int maxH = (int) (centerImage.getImageHeight() * scale - getContainerViewHeight()) / 2;
        if (maxW > 0) {
            minX = -maxW;
            maxX = maxW;
        } else {
            minX = maxX = 0;
        }
        if (maxH > 0) {
            minY = -maxH;
            maxY = maxH;
        } else {
            minY = maxY = 0;
        }
    }

    private int getContainerViewWidth() {
        return photoContainerView.getWidth();
    }

    private int getContainerViewHeight() {
        return photoContainerView.getHeight();
    }

    private boolean processTouchEvent(MotionEvent ev) {
        if (photoAnimationInProgress != 0 || animationStartTime != 0) {
            return false;
        }

        if (ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev) && doubleTap) {
            doubleTap = false;
            moving = false;
            zooming = false;
            checkMinMax(false);
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            if (!draggingDown && !changingPage) {
                if (canZoom && ev.getPointerCount() == 2) {
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                    pinchStartScale = scale;
                    pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                    pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                    pinchStartX = translationX;
                    pinchStartY = translationY;
                    zooming = true;
                    moving = false;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev.getPointerCount() == 1) {
                    moveStartX = ev.getX();
                    dragY = moveStartY = ev.getY();
                    draggingDown = false;
                    canDragDown = true;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (canZoom && ev.getPointerCount() == 2 && !draggingDown && zooming && !changingPage) {
                discardTap = true;
                scale = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                photoContainerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (dx > AndroidUtilities.dp(3) || dy > AndroidUtilities.dp(3)) {
                    discardTap = true;
                }
                if (canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible) {
                        toggleActionBar(false, true);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    photoContainerView.invalidate();
                } else if (!invalidCoords && animationStartTime == 0) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == 1 && Math.abs(moveDy) + AndroidUtilities.dp(12) < Math.abs(moveDx) || scale != 1) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && (!rightImage.hasImage()) || translationX > maxX && !leftImage.hasImage()) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0 && minY == 0) {
                            if (translationY - moveDy < minY) {
                                translationY = minY;
                                moveDy = 0;
                            } else if (translationY - moveDy > maxY) {
                                translationY = maxY;
                                moveDy = 0;
                            }
                        } else {
                            if (translationY < minY || translationY > maxY) {
                                moveDy /= 3.0f;
                            }
                        }

                        translationX -= moveDx;
                        if (scale != 1) {
                            translationY -= moveDy;
                        }

                        photoContainerView.invalidate();
                    }
                } else {
                    invalidCoords = false;
                    moveStartX = ev.getX();
                    moveStartY = ev.getY();
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL || ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            if (zooming) {
                invalidCoords = true;
                if (scale < 1.0f) {
                    updateMinMax(1.0f);
                    animateTo(1.0f, 0, 0, true);
                } else if (scale > 3.0f) {
                    float atx = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (3.0f / pinchStartScale);
                    float aty = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (3.0f / pinchStartScale);
                    updateMinMax(3.0f);
                    if (atx < minX) {
                        atx = minX;
                    } else if (atx > maxX) {
                        atx = maxX;
                    }
                    if (aty < minY) {
                        aty = minY;
                    } else if (aty > maxY) {
                        aty = maxY;
                    }
                    animateTo(3.0f, atx, aty, true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > getContainerViewHeight() / 6.0f) {
                    closePhoto(true);
                } else {
                    animateTo(1, 0, 0, false);
                }
                draggingDown = false;
            } else if (moving) {
                float moveToX = translationX;
                float moveToY = translationY;
                updateMinMax(scale);
                moving = false;
                canDragDown = true;
                float velocity = 0;
                if (velocityTracker != null && scale == 1) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocity = velocityTracker.getXVelocity();
                }

                if ((translationX < minX - getContainerViewWidth() / 3 || velocity < -AndroidUtilities.dp(650)) && rightImage.hasImage()) {
                    goToNext();
                    return true;
                }
                if ((translationX > maxX + getContainerViewWidth() / 3 || velocity > AndroidUtilities.dp(650)) && leftImage.hasImage()) {
                    goToPrev();
                    return true;
                }

                if (translationX < minX) {
                    moveToX = minX;
                } else if (translationX > maxX) {
                    moveToX = maxX;
                }
                if (translationY < minY) {
                    moveToY = minY;
                } else if (translationY > maxY) {
                    moveToY = maxY;
                }
                animateTo(scale, moveToX, moveToY, false);
            }
        }
        return false;
    }

    private void checkMinMax(boolean zoom) {
        float moveToX = translationX;
        float moveToY = translationY;
        updateMinMax(scale);
        if (translationX < minX) {
            moveToX = minX;
        } else if (translationX > maxX) {
            moveToX = maxX;
        }
        if (translationY < minY) {
            moveToY = minY;
        } else if (translationY > maxY) {
            moveToY = maxY;
        }
        animateTo(scale, moveToX, moveToY, zoom);
    }

    private void goToNext() {
        float extra = 0;
        if (scale != 1) {
            extra = (getContainerViewWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - getContainerViewWidth() - extra - AndroidUtilities.dp(30) / 2, translationY, false);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != 1) {
            extra = (getContainerViewWidth() - centerImage.getImageWidth()) / 2 * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + getContainerViewWidth() + extra + AndroidUtilities.dp(30) / 2, translationY, false);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        animateTo(newScale, newTx, newTy, isZoom, 250);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom, int duration) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(this, "animationValue", 0, 1)
        );
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(duration);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageMoveAnimation = null;
                photoContainerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    public void setAnimationValue(float value) {
        animationValue = value;
        photoContainerView.invalidate();
    }

    public float getAnimationValue() {
        return animationValue;
    }

    private void drawContent(Canvas canvas) {
        if (photoAnimationInProgress == 1 || !isPhotoVisible && photoAnimationInProgress != 2) {
            return;
        }

        float currentTranslationY;
        float currentTranslationX;
        float currentScale;
        float aty = -1;

        if (imageMoveAnimation != null) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            float ts = scale + (animateToScale - scale) * animationValue;
            float tx = translationX + (animateToX - translationX) * animationValue;
            float ty = translationY + (animateToY - translationY) * animationValue;

            if (animateToScale == 1 && scale == 1 && translationX == 0) {
                aty = ty;
            }
            currentScale = ts;
            currentTranslationY = ty;
            currentTranslationX = tx;
            photoContainerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                scale = animateToScale;
                animationStartTime = 0;
                updateMinMax(scale);
                zoomAnimation = false;
            }
            if (!scroller.isFinished()) {
                if (scroller.computeScrollOffset()) {
                    if (scroller.getStartX() < maxX && scroller.getStartX() > minX) {
                        translationX = scroller.getCurrX();
                    }
                    if (scroller.getStartY() < maxY && scroller.getStartY() > minY) {
                        translationY = scroller.getCurrY();
                    }
                    photoContainerView.invalidate();
                }
            }
            if (switchImageAfterAnimation != 0) {
                if (switchImageAfterAnimation == 1) {
                    setImageIndex(currentIndex + 1, false);
                } else if (switchImageAfterAnimation == 2) {
                    setImageIndex(currentIndex - 1, false);
                }
                switchImageAfterAnimation = 0;
            }
            currentScale = scale;
            currentTranslationY = translationY;
            currentTranslationX = translationX;
            if (!moving) {
                aty = translationY;
            }
        }

        if (scale == 1 && aty != -1 && !zoomAnimation) {
            float maxValue = getContainerViewHeight() / 4.0f;
            photoBackgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
        } else {
            photoBackgroundDrawable.setAlpha(255);
        }

        ImageReceiver sideImage = null;

        if (scale >= 1.0f && !zoomAnimation && !zooming) {
            if (currentTranslationX > maxX + AndroidUtilities.dp(5)) {
                sideImage = leftImage;
            } else if (currentTranslationX < minX - AndroidUtilities.dp(5)) {
                sideImage = rightImage;
            }
        }
        changingPage = sideImage != null;

        if (sideImage == rightImage) {
            float tranlateX = currentTranslationX;
            float scaleDiff = 0;
            float alpha = 1;
            if (!zoomAnimation && tranlateX < minX) {
                alpha = Math.min(1.0f, (minX - tranlateX) / canvas.getWidth());
                scaleDiff = (1.0f - alpha) * 0.3f;
                tranlateX = -canvas.getWidth() - AndroidUtilities.dp(30) / 2;
            }

            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(canvas.getWidth() + AndroidUtilities.dp(30) / 2 + tranlateX, 0);
                canvas.scale(1.0f - scaleDiff, 1.0f - scaleDiff);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                float scale = scaleX > scaleY ? scaleY : scaleX;
                int width = (int) (bitmapWidth * scale);
                int height = (int) (bitmapHeight * scale);

                sideImage.setAlpha(alpha);
                sideImage.setImageCoords(-width / 2, -height / 2, width, height);
                sideImage.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(tranlateX, currentTranslationY / currentScale);
            canvas.translate((canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            radialProgressViews[1].setScale(1.0f - scaleDiff);
            radialProgressViews[1].setAlpha(alpha);
            radialProgressViews[1].onDraw(canvas);
            canvas.restore();
        }

        float translateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (!zoomAnimation && translateX > maxX) {
            alpha = Math.min(1.0f, (translateX - maxX) / canvas.getWidth());
            scaleDiff = alpha * 0.3f;
            alpha = 1.0f - alpha;
            translateX = maxX;
        }
        boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
        if (centerImage.hasBitmapImage()) {
            canvas.save();
            canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
            canvas.translate(translateX, currentTranslationY);
            canvas.scale(currentScale - scaleDiff, currentScale - scaleDiff);

            int bitmapWidth = centerImage.getBitmapWidth();
            int bitmapHeight = centerImage.getBitmapHeight();
            if (drawTextureView && textureUploaded) {
                float scale1 = bitmapWidth / (float) bitmapHeight;
                float scale2 = videoTextureView.getMeasuredWidth() / (float) videoTextureView.getMeasuredHeight();
                if (Math.abs(scale1 - scale2) > 0.01f) {
                    bitmapWidth = videoTextureView.getMeasuredWidth();
                    bitmapHeight = videoTextureView.getMeasuredHeight();
                }
            }

            float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
            float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
            float scale = scaleX > scaleY ? scaleY : scaleX;
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);

            if (!drawTextureView || !textureUploaded || !videoCrossfadeStarted || videoCrossfadeAlpha != 1.0f) {
                centerImage.setAlpha(alpha);
                centerImage.setImageCoords(-width / 2, -height / 2, width, height);
                centerImage.draw(canvas);
            }
            if (drawTextureView) {
                if (!videoCrossfadeStarted && textureUploaded) {
                    videoCrossfadeStarted = true;
                    videoCrossfadeAlpha = 0.0f;
                    videoCrossfadeAlphaLastTime = System.currentTimeMillis();
                }
                canvas.translate(-width / 2, -height / 2);
                videoTextureView.setAlpha(alpha * videoCrossfadeAlpha);
                aspectRatioFrameLayout.draw(canvas);
                if (videoCrossfadeStarted && videoCrossfadeAlpha < 1.0f) {
                    long newUpdateTime = System.currentTimeMillis();
                    long dt = newUpdateTime - videoCrossfadeAlphaLastTime;
                    videoCrossfadeAlphaLastTime = newUpdateTime;
                    videoCrossfadeAlpha += dt / 300.0f;
                    photoContainerView.invalidate();
                    if (videoCrossfadeAlpha > 1.0f) {
                        videoCrossfadeAlpha = 1.0f;
                    }
                }
            }
            canvas.restore();
        }
        if (!drawTextureView && bottomLayout.getVisibility() != View.VISIBLE) {
            canvas.save();
            canvas.translate(translateX, currentTranslationY / currentScale);
            radialProgressViews[0].setScale(1.0f - scaleDiff);
            radialProgressViews[0].setAlpha(alpha);
            radialProgressViews[0].onDraw(canvas);
            canvas.restore();
        }

        if (sideImage == leftImage) {
            if (sideImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(getContainerViewWidth() / 2, getContainerViewHeight() / 2);
                canvas.translate(-(canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2 + currentTranslationX, 0);
                int bitmapWidth = sideImage.getBitmapWidth();
                int bitmapHeight = sideImage.getBitmapHeight();

                float scaleX = (float) getContainerViewWidth() / (float) bitmapWidth;
                float scaleY = (float) getContainerViewHeight() / (float) bitmapHeight;
                float scale = scaleX > scaleY ? scaleY : scaleX;
                int width = (int) (bitmapWidth * scale);
                int height = (int) (bitmapHeight * scale);

                sideImage.setAlpha(1.0f);
                sideImage.setImageCoords(-width / 2, -height / 2, width, height);
                sideImage.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            canvas.translate(currentTranslationX, currentTranslationY / currentScale);
            canvas.translate(-(canvas.getWidth() * (scale + 1) + AndroidUtilities.dp(30)) / 2, -currentTranslationY / currentScale);
            radialProgressViews[2].setScale(1.0f);
            radialProgressViews[2].setAlpha(1.0f);
            radialProgressViews[2].onDraw(canvas);
            canvas.restore();
        }
    }

    private void onActionClick(boolean download) {
        TLObject media = getMedia(currentIndex);
        if (!(media instanceof TLRPC.Document) || currentFileNames[0] == null) {
            return;
        }
        TLRPC.Document document = (TLRPC.Document) media;
        File file = null;
        if (currentMedia != null) {
            file = getMediaFile(currentIndex);
            if (file != null && !file.exists()) {
                file = null;
            }
        }
        if (file == null) {
            if (download) {
                if (!FileLoader.getInstance().isLoadingFile(currentFileNames[0])) {
                    FileLoader.getInstance().loadFile(document, true, 1);
                } else {
                    FileLoader.getInstance().cancelLoadFile(document);
                }
            }
        } else {
            preparePlayer(file, true);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (scale != 1) {
            scroller.abortAnimation();
            scroller.fling(Math.round(translationX), Math.round(translationY), Math.round(velocityX), Math.round(velocityY), (int) minX, (int) maxX, (int) minY, (int) maxY);
            photoContainerView.postInvalidate();
        }
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (discardTap) {
            return false;
        }
        boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
        if (radialProgressViews[0] != null && photoContainerView != null && !drawTextureView) {
            int state = radialProgressViews[0].backgroundState;
            if (state > 0 && state <= 3) {
                float x = e.getX();
                float y = e.getY();
                if (x >= (getContainerViewWidth() - AndroidUtilities.dp(100)) / 2.0f && x <= (getContainerViewWidth() + AndroidUtilities.dp(100)) / 2.0f &&
                        y >= (getContainerViewHeight() - AndroidUtilities.dp(100)) / 2.0f && y <= (getContainerViewHeight() + AndroidUtilities.dp(100)) / 2.0f) {
                    onActionClick(true);
                    checkProgress(0, true);
                    return true;
                }
            }
        }
        toggleActionBar(!isActionBarVisible, true);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!canZoom || scale == 1.0f && (translationY != 0 || translationX != 0)) {
            return false;
        }
        if (animationStartTime != 0 || photoAnimationInProgress != 0) {
            return false;
        }
        if (scale == 1.0f) {
            float atx = (e.getX() - getContainerViewWidth() / 2) - ((e.getX() - getContainerViewWidth() / 2) - translationX) * (3.0f / scale);
            float aty = (e.getY() - getContainerViewHeight() / 2) - ((e.getY() - getContainerViewHeight() / 2) - translationY) * (3.0f / scale);
            updateMinMax(3.0f);
            if (atx < minX) {
                atx = minX;
            } else if (atx > maxX) {
                atx = maxX;
            }
            if (aty < minY) {
                aty = minY;
            } else if (aty > maxY) {
                aty = maxY;
            }
            animateTo(3.0f, atx, aty, true);
        } else {
            animateTo(1.0f, 0, 0, true);
        }
        doubleTap = true;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private ImageReceiver getImageReceiverFromListView(ViewGroup listView, TLRPC.PageBlock pageBlock, int[] coords) {
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
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
            }
        }
        return null;
    }

    private PlaceProviderObject getPlaceForPhoto(TLRPC.PageBlock pageBlock) {
        ImageReceiver imageReceiver = getImageReceiverFromListView(listView, pageBlock, coords);
        if (imageReceiver == null) {
            return null;
        }
        PlaceProviderObject object = new PlaceProviderObject();
        object.viewX = coords[0];
        object.viewY = coords[1];
        object.parentView = listView;
        object.imageReceiver = imageReceiver;
        object.thumb = imageReceiver.getBitmap();
        object.radius = imageReceiver.getRoundRadius();
        object.clipTopAddition = currentHeaderHeight;
        return object;
    }
}

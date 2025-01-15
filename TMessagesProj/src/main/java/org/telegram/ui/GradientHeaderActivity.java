package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.NestedSizeNotifierLayout;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;

public abstract class GradientHeaderActivity extends BaseFragment {

    private final PremiumGradient.PremiumGradientTools gradientTools = new PremiumGradient.PremiumGradientTools(
            Theme.key_premiumGradientBackground1,
            Theme.key_premiumGradientBackground2,
            Theme.key_premiumGradientBackground3,
            Theme.key_premiumGradientBackground4) {
        @Override
        protected int getThemeColorByKey(int key) {
            return Theme.getDefaultColor(key);
        }
    };
    private final PremiumGradient.PremiumGradientTools darkGradientTools = new PremiumGradient.PremiumGradientTools(
            Theme.key_premiumGradientBackground1,
            Theme.key_premiumGradientBackground2,
            Theme.key_premiumGradientBackground3,
            Theme.key_premiumGradientBackground4) {
        @Override
        protected int getThemeColorByKey(int key) {
            return Theme.getDefaultColor(key);
        }
    };

    protected RecyclerListView listView;

    private Drawable shadowDrawable;
    protected StarParticlesView particlesView;
    private boolean isDialogVisible;
    private boolean inc;
    private float progress;
    private int currentYOffset;
    protected int yOffset;
    protected FrameLayout contentView;
    private float totalProgress;
    private final Bitmap gradientTextureBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    private final Canvas gradientCanvas = new Canvas(gradientTextureBitmap);
    private float progressToFull;
    public BackgroundView backgroundView;
    public int particlesViewHeight = -1;
    protected LinearLayoutManager layoutManager;
    protected boolean useFillLastLayoutManager = true;
    public boolean isLandscapeMode;
    public int statusBarHeight;
    private int firstViewHeight;
    private final Paint headerBgPaint = new Paint();
    private int minusHeaderHeight;

    public void setMinusHeaderHeight(int h) {
        minusHeaderHeight = h;
    }

    public boolean whiteBackground;

    public void setWhiteBackground(boolean value) {
        this.whiteBackground = value;
    }

    {
        darkGradientTools.darkColors = true;
    }

    abstract protected RecyclerView.Adapter<?> createAdapter();

    protected void configureHeader(CharSequence title, CharSequence subTitle, View aboveTitleView, View underSubTitleView) {
        backgroundView.setData(title, subTitle, aboveTitleView, underSubTitleView);
    }

    protected View getHeader(Context context) {
        return new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (isLandscapeMode) {
                    firstViewHeight = statusBarHeight + actionBar.getMeasuredHeight() - AndroidUtilities.dp(16);
                } else {
                    int h = AndroidUtilities.dp(140) + statusBarHeight;
                    if (backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24) > h) {
                        h = Math.max(h, backgroundView.getMeasuredHeight() + AndroidUtilities.dp(24) - minusHeaderHeight);
                    }
                    firstViewHeight = h;
                }
                firstViewHeight -= 2.5f * yOffset;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(firstViewHeight, MeasureSpec.EXACTLY));
            }
        };
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        Rect padding = new Rect();
        shadowDrawable = ContextCompat.getDrawable(context, R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        shadowDrawable.getPadding(padding);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            statusBarHeight = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;
        }

        contentView = createContentView();
        contentView.setFitsSystemWindows(true);
        listView = new RecyclerListView(context) {
            @Override
            public void onDraw(Canvas canvas) {
                float alpha = 1f - (totalProgress > 0.5f ? (totalProgress - 0.5f) / 0.5f : 0f);
                shadowDrawable.setBounds((int) (-padding.left - AndroidUtilities.dp(16) * progressToFull), currentYOffset + (int) (yOffset * alpha) - padding.top - AndroidUtilities.dp(16), (int) (getMeasuredWidth() + padding.right + AndroidUtilities.dp(16) * progressToFull), getMeasuredHeight());
                shadowDrawable.draw(canvas);
                super.onDraw(canvas);
            }
        };
        if (useFillLastLayoutManager) {
            layoutManager = new FillLastLinearLayoutManager(context, AndroidUtilities.dp(68) + statusBarHeight - AndroidUtilities.dp(16), listView);
        } else {
            layoutManager = new LinearLayoutManager(context);
        }
        listView.setLayoutManager(layoutManager);
        if (layoutManager instanceof FillLastLinearLayoutManager) {
            ((FillLastLinearLayoutManager) layoutManager).setFixedLastItemHeight();
        }

        listView.setAdapter(createAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int maxTop = actionBar.getBottom() + AndroidUtilities.dp(16);
                    if (totalProgress > 0.5f) {
                        listView.smoothScrollBy(0, currentYOffset - maxTop);
                    } else {
                        View firstView = null;
                        if (listView.getLayoutManager() != null) {
                            firstView = listView.getLayoutManager().findViewByPosition(0);
                        }
                        if (firstView != null && firstView.getTop() < 0) {
                            listView.smoothScrollBy(0, firstView.getTop());
                        }
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                contentView.invalidate();
            }
        });

        backgroundView = new BackgroundView(context) {
//            @Override
//            public boolean onInterceptTouchEvent(MotionEvent ev) {
//                return true;
//            }
        };

        contentView.addView(particlesView = createParticlesView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentView.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentView.addView(listView);

        fragmentView = contentView;
        actionBar.setBackground(null);
        actionBar.setCastShadows(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setForceSkipTouches(true);
        updateColors();
        return fragmentView;
    }

    protected ContentView createContentView() {
        return new ContentView(getContext());
    }

    public class ContentView extends NestedSizeNotifierLayout {
        public ContentView(Context context) {
            super(context);
        }

        int lastSize;
        boolean topInterceptedTouch;
        boolean subtitleInterceptedTouch;
        boolean bottomInterceptedTouch;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            BackgroundView backgroundView = GradientHeaderActivity.this.backgroundView;
            isLandscapeMode = View.MeasureSpec.getSize(widthMeasureSpec) > View.MeasureSpec.getSize(heightMeasureSpec);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                statusBarHeight = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;
            }
            backgroundView.measure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            particlesView.getLayoutParams().height = particlesViewHeight > 0 ? particlesViewHeight : backgroundView.getMeasuredHeight();
            if (layoutManager instanceof FillLastLinearLayoutManager) {
                ((FillLastLinearLayoutManager) layoutManager).setAdditionalHeight(actionBar.getMeasuredHeight());
                ((FillLastLinearLayoutManager) layoutManager).setMinimumLastViewHeight(0);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int size = getMeasuredHeight() + getMeasuredWidth() << 16;
            if (lastSize != size) {
                updateBackgroundImage();
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            BackgroundView backgroundView = GradientHeaderActivity.this.backgroundView;

            float topX = backgroundView.getX() + backgroundView.subtitleView.getX();
            float topY = backgroundView.getY() + backgroundView.subtitleView.getY();
            AndroidUtilities.rectTmp.set(topX, topY,
                    topX + backgroundView.subtitleView.getMeasuredWidth(),
                    topY + backgroundView.subtitleView.getMeasuredHeight());
            if ((AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY()) || subtitleInterceptedTouch) && !listView.scrollingByUser && backgroundView.subtitleView.hasLinks() && progressToFull < 1) {
                ev.offsetLocation(-topX, -topY);
                if (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) {
                    subtitleInterceptedTouch = true;
                } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    subtitleInterceptedTouch = false;
                }
                backgroundView.subtitleView.dispatchTouchEvent(ev);
                return true;
            }

            topX = backgroundView.getX() + backgroundView.aboveTitleLayout.getX();
            topY = backgroundView.getY() + backgroundView.aboveTitleLayout.getY();
            boolean isClickableTop = backgroundView.aboveTitleLayout.isClickable();
            AndroidUtilities.rectTmp.set(topX, topY,
                    topX + backgroundView.aboveTitleLayout.getMeasuredWidth(),
                    topY + backgroundView.aboveTitleLayout.getMeasuredHeight());
            if ((AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY()) || topInterceptedTouch) && !listView.scrollingByUser && isClickableTop && progressToFull < 1) {
                ev.offsetLocation(-topX, -topY);
                if (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) {
                    topInterceptedTouch = true;
                } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    topInterceptedTouch = false;
                }
                backgroundView.aboveTitleLayout.dispatchTouchEvent(ev);
                return true;
            }

            float bottomX = backgroundView.getX() + backgroundView.belowSubTitleLayout.getX();
            float bottomY = backgroundView.getY() + backgroundView.belowSubTitleLayout.getY();
            AndroidUtilities.rectTmp.set(bottomX, bottomY,
                    bottomX + backgroundView.belowSubTitleLayout.getMeasuredWidth(),
                    bottomY + backgroundView.belowSubTitleLayout.getMeasuredHeight());
            if ((AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY()) || bottomInterceptedTouch) && !listView.scrollingByUser && progressToFull < 1) {
                ev.offsetLocation(-bottomX, -bottomY);
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    bottomInterceptedTouch = true;
                } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    bottomInterceptedTouch = false;
                }
                backgroundView.belowSubTitleLayout.dispatchTouchEvent(ev);
                if (bottomInterceptedTouch) {
                    return true;
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient backgroundGradient;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            final BackgroundView backgroundView = GradientHeaderActivity.this.backgroundView;
            if (!isDialogVisible) {
                if (inc) {
                    progress += 16f / 1000f;
                    if (progress > 3) {
                        inc = false;
                    }
                } else {
                    progress -= 16f / 1000f;
                    if (progress < 1) {
                        inc = true;
                    }
                }
            }
            View firstView = null;
            if (listView.getLayoutManager() != null) {
                firstView = listView.getLayoutManager().findViewByPosition(0);
            }

            currentYOffset = firstView == null ? 0 : firstView.getBottom();
            int h = actionBar.getBottom() + AndroidUtilities.dp(16);
            totalProgress = (1f - (currentYOffset - h) / (float) (firstViewHeight - h));
            totalProgress = Utilities.clamp(totalProgress, 1f, 0f);

            int maxTop = actionBar.getBottom() + AndroidUtilities.dp(16);
            if (currentYOffset < maxTop) {
                currentYOffset = maxTop;
            }

            float oldProgress = progressToFull;
            progressToFull = 0;
            if (currentYOffset < maxTop + AndroidUtilities.dp(30)) {
                progressToFull = (maxTop + AndroidUtilities.dp(30) - currentYOffset) / (float) AndroidUtilities.dp(30);
            }

            if (isLandscapeMode) {
                progressToFull = 1f;
                totalProgress = 1f;
            }
            if (oldProgress != progressToFull) {
                listView.invalidate();
            }
            float fromTranslation = currentYOffset - (actionBar.getMeasuredHeight() + backgroundView.getMeasuredHeight() - statusBarHeight) + AndroidUtilities.dp(16);
            float toTranslation = ((actionBar.getMeasuredHeight() - statusBarHeight - backgroundView.titleView.getMeasuredHeight()) / 2f) + statusBarHeight - backgroundView.getTop() - backgroundView.titleView.getTop();

            float translationsY = Math.max(toTranslation, fromTranslation);
            float iconTranslationsY = -translationsY / 4f + AndroidUtilities.dp(16);
            backgroundView.setTranslationY(translationsY);

            backgroundView.aboveTitleLayout.setTranslationY(iconTranslationsY + AndroidUtilities.dp(16));
            float s = 0.6f + (1f - totalProgress) * 0.4f;
            float alpha = 1f - (totalProgress > 0.5f ? (totalProgress - 0.5f) / 0.5f : 0f);
            backgroundView.aboveTitleLayout.setScaleX(s);
            backgroundView.aboveTitleLayout.setScaleY(s);
            backgroundView.aboveTitleLayout.setAlpha(alpha);
            backgroundView.belowSubTitleLayout.setAlpha(alpha);
            backgroundView.subtitleView.setAlpha(alpha);
            particlesView.setAlpha(1f - totalProgress);
            particlesView.setTranslationY(backgroundView.getY() + backgroundView.aboveTitleLayout.getY() - AndroidUtilities.dp(30));
            float toX = AndroidUtilities.dp(72) - backgroundView.titleView.getLeft();
            float f = totalProgress > 0.3f ? (totalProgress - 0.3f) / 0.7f : 0f;
            backgroundView.titleView.setTranslationX(toX * (1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(1 - f)));

            if (!isDialogVisible) {
                invalidate();
            }

            gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -getMeasuredWidth() * 0.1f * progress, 0);
            if (whiteBackground) {
                if (backgroundGradient == null) {
                    backgroundGradient = new LinearGradient(0, 0, 0, dp(350), new int[] {getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_windowBackgroundGray)}, new float[] {0.3f, 1}, Shader.TileMode.CLAMP);
                    backgroundGradientPaint.setShader(backgroundGradient);
                }
                canvas.drawRect(0, 0, getMeasuredWidth(), currentYOffset + yOffset + dp(20), backgroundGradientPaint);
            } else {
                canvas.drawRect(0, 0, getMeasuredWidth(), currentYOffset + yOffset + AndroidUtilities.dp(20), gradientTools.paint);
            }

            int titleColor = ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextBlack), getThemedColor(whiteBackground ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_premiumGradientBackgroundOverlay), alpha);
            actionBar.getBackButton().setColorFilter(titleColor);
            backgroundView.titleView.setTextColor(titleColor);
            headerBgPaint.setAlpha((int) (255 * (1f - alpha)));
            setLightStatusBar(Theme.blendOver(Theme.getColor(Theme.key_premiumGradientBackground4, resourceProvider), headerBgPaint.getColor()));
            canvas.drawRect(0, 0, getMeasuredWidth(), currentYOffset + yOffset + AndroidUtilities.dp(20), headerBgPaint);
            super.dispatchDraw(canvas);
            if (alpha <= 0.01f && drawActionBarShadow()) {
                parentLayout.drawHeaderShadow(canvas, 0xFF, actionBar.getMeasuredHeight());
            }
        }

        private Boolean lightStatusBar;
        private void setLightStatusBar(int color) {
            boolean colorLight = AndroidUtilities.computePerceivedBrightness(color) >= .721f;
            if (lightStatusBar == null || lightStatusBar != colorLight) {
                AndroidUtilities.setLightStatusBar(fragmentView, lightStatusBar = colorLight);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == listView) {
                canvas.save();
                canvas.clipRect(0, actionBar.getBottom(), getMeasuredWidth(), getMeasuredHeight());
                super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    protected boolean drawActionBarShadow() {
        return true;
    }

    public StarParticlesView createParticlesView() {
        return new StarParticlesView(getContext()) {
            @Override
            protected void configure() {
                drawable = new Drawable(50) {
                    @Override
                    protected int getPathColor(int i) {
                        return ColorUtils.setAlphaComponent(Theme.getDefaultColor(colorKey), 200);
                    }
                };
                drawable.type = 100;
                drawable.roundEffect = false;
                drawable.useRotate = false;
                drawable.useBlur = true;
                drawable.checkBounds = true;
                drawable.isCircle = false;
                drawable.size1 = 4;
                drawable.k1 = drawable.k2 = drawable.k3 = 0.98f;
                drawable.init();
            }

            @Override
            protected int getStarsRectWidth() {
                return getMeasuredWidth();
            }
        };
    }

    public Paint setDarkGradientLocation(float x, float y) {
        darkGradientTools.gradientMatrix(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), -x - (contentView.getMeasuredWidth() * 0.1f * progress), -y);
        return darkGradientTools.paint;
    }

    @Override
    public boolean isActionBarCrossfadeEnabled() {
        return false;
    }

    private void updateBackgroundImage() {
        if (contentView.getMeasuredWidth() == 0 || contentView.getMeasuredHeight() == 0 || backgroundView == null) {
            return;
        }
        gradientTools.gradientMatrix(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), 0, 0);
        gradientCanvas.save();
        gradientCanvas.scale(100f / contentView.getMeasuredWidth(), 100f / contentView.getMeasuredHeight());
        gradientCanvas.drawRect(0, 0, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), gradientTools.paint);
        gradientCanvas.restore();
    }

    @Override
    public Dialog showDialog(Dialog dialog) {
        Dialog d = super.showDialog(dialog);
        updateDialogVisibility(d != null);
        return d;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);
        updateDialogVisibility(false);
    }

    protected void updateDialogVisibility(boolean isVisible) {
        if (isVisible != isDialogVisible) {
            isDialogVisible = isVisible;
            particlesView.setPaused(isVisible);
            contentView.invalidate();
        }
    }

    private void updateColors() {
        if (backgroundView == null || actionBar == null) {
            return;
        }
        headerBgPaint.setColor(getThemedColor(Theme.key_dialogBackground));
        actionBar.setItemsColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay), false);
        actionBar.setItemsBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay), 60), false);
        particlesView.drawable.updateColors();
        if (backgroundView != null) {
            if (whiteBackground) {
                backgroundView.titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                backgroundView.subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                backgroundView.subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
            } else {
                backgroundView.titleView.setTextColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay));
                backgroundView.subtitleView.setTextColor(Theme.getColor(Theme.key_premiumGradientBackgroundOverlay));
                backgroundView.subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));
            }
        }
        updateBackgroundImage();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_premiumGradient1, Theme.key_premiumGradient2, Theme.key_premiumGradient3, Theme.key_premiumGradient4,
                Theme.key_premiumGradientBackground1, Theme.key_premiumGradientBackground2, Theme.key_premiumGradientBackground3, Theme.key_premiumGradientBackground4,
                Theme.key_premiumGradientBackgroundOverlay, Theme.key_premiumStarGradient1, Theme.key_premiumStarGradient2, Theme.key_premiumStartSmallStarsColor, Theme.key_premiumStartSmallStarsColor2
        );
    }

    @Override
    public boolean isLightStatusBar() {
        return whiteBackground && !Theme.isCurrentThemeDark();
    }

    @Override
    public void onResume() {
        super.onResume();
        particlesView.setPaused(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (particlesView != null) {
            particlesView.setPaused(true);
        }
    }

    protected static class BackgroundView extends LinearLayout {

        private final TextView titleView;
        public final LinkSpanDrawable.LinksTextView subtitleView;
        private final FrameLayout aboveTitleLayout;
        private final FrameLayout belowSubTitleLayout;

        public BackgroundView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            aboveTitleLayout = new FrameLayout(context);
            addView(aboveTitleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            aboveTitleLayout.setClipChildren(false);
            setClipChildren(false);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_HORIZONTAL, 16, 20, 16, 0));

            subtitleView = new LinkSpanDrawable.LinksTextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 24, 7, 24, 0));

            belowSubTitleLayout = new FrameLayout(context);
            addView(belowSubTitleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            belowSubTitleLayout.setClipChildren(false);
        }

        public void setData(CharSequence title, CharSequence subTitle, View aboveTitleView, View underSubTitleView) {
            titleView.setText(title);
            subtitleView.setText(subTitle);
            if (aboveTitleView != null) {
                aboveTitleLayout.removeAllViews();
                aboveTitleLayout.addView(aboveTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
                aboveTitleLayout.setClickable(aboveTitleView.isClickable());
            } else {
                aboveTitleLayout.setClickable(false);
            }
            if (underSubTitleView != null) {
                belowSubTitleLayout.removeAllViews();
                belowSubTitleLayout.addView(underSubTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
                belowSubTitleLayout.setClickable(underSubTitleView.isClickable());
            } else {
                belowSubTitleLayout.setClickable(false);
            }
            requestLayout();
        }
    }


    public int savedScrollPosition = -1;
    public int savedScrollOffset;
    public void saveScrollPosition() {
        if (listView != null && listView.getChildCount() > 0) {
            View view = null;
            int position = -1;
            int top = Integer.MAX_VALUE;
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                int childPosition = listView.getChildAdapterPosition(child);
                if (childPosition >= 0 && child.getTop() < top) {
                    view = child;
                    position = childPosition;
                    top = child.getTop();
                    break;
                }
            }
            if (view != null) {
                savedScrollPosition = position;
                savedScrollOffset = view.getTop();
            }
        }
    }

    public void applyScrolledPosition() {
        this.applyScrolledPosition(false);
    }

    public void applyScrolledPosition(boolean ignorePaddingView) {
        if (listView != null && layoutManager != null && savedScrollPosition >= 0) {
            int offset = savedScrollOffset;
            RecyclerView.ViewHolder paddingViewHolder = listView.findViewHolderForAdapterPosition(0);
            if (ignorePaddingView && paddingViewHolder != null) {
                View view = paddingViewHolder.itemView;
                offset -= Math.max(view.getBottom() - listView.getPaddingTop(), 0);
            }
            layoutManager.scrollToPositionWithOffset(savedScrollPosition, offset);
            savedScrollPosition = -1;
        }
    }
}

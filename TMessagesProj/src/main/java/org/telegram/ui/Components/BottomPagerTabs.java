package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public class BottomPagerTabs extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Tab[] tabs;

    protected class Tab {
        final int i;
        final RLottieDrawable drawable;
        final Drawable ripple;

        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final StaticLayout layout;
        final float layoutWidth, layoutLeft;

        final RectF clickRect = new RectF();

        final AnimatedFloat nonscrollingT = new AnimatedFloat(BottomPagerTabs.this, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        public int customEndFrameMid;
        public int customEndFrameEnd;
        public boolean customFrameInvert;

        public Tab customFrameInvert() {
            this.customFrameInvert = true;
            return this;
        }

        public Tab(int i, int resId, int endFrameMid, int endFrameEnd, CharSequence text) {
            this.i = i;

            customEndFrameMid = endFrameMid;
            customEndFrameEnd = endFrameEnd;

            drawable = new RLottieDrawable(resId, "" + resId, dp(29), dp(29));
            drawable.setMasterParent(BottomPagerTabs.this);
            drawable.setAllowDecodeSingleFrame(true);
            drawable.setPlayInDirectionOfCustomEndFrame(true);
            drawable.setAutoRepeat(0);

            paint.setTypeface(AndroidUtilities.bold());
            paint.setTextSize(dp(12));
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            layout = new StaticLayout(text, paint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            layoutWidth = layout.getLineCount() > 0 ? layout.getLineWidth(0) : 0;
            layoutLeft = layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;

            ripple = Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .1f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(16));
        }

        private boolean active;
        public void setActive(boolean active, boolean animated) {
            if (customFrameInvert) {
                active = !active;
            }
            if (this.active == active) {
                return;
            }

            if (tabs[i].customEndFrameMid != 0) {
                if (active) {
                    drawable.setCustomEndFrame(customEndFrameMid);
                    if (drawable.getCurrentFrame() >= customEndFrameEnd - 2) {
                        drawable.setCurrentFrame(0, false);
                    }
                    if (drawable.getCurrentFrame() <= customEndFrameMid) {
                        drawable.start();
                    } else {
                        drawable.setCurrentFrame(customEndFrameMid);
                    }
                } else {
                    if (drawable.getCurrentFrame() >= customEndFrameMid - 1) {
                        drawable.setCustomEndFrame(customEndFrameEnd - 1);
                        drawable.start();
                    } else {
                        drawable.setCustomEndFrame(0);
                        drawable.setCurrentFrame(0);
                    }
                }
            } else if (active) {
                drawable.setCurrentFrame(0);
                if (animated) {
                    drawable.start();
                }
            }
            this.active = active;
        }

        private int drawableColor = -1;
        public void setColor(int color) {
            paint.setColor(color);
            if (drawableColor != color) {
                drawable.setColorFilter(new PorterDuffColorFilter(drawableColor = color, PorterDuff.Mode.SRC_IN));
            }
        }
    }

    private final Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float progress;
    private int value;

    private boolean scrolling;
    private AnimatedFloat scrollingT = new AnimatedFloat(this, 0, 210, CubicBezierInterpolator.EASE_OUT_QUINT);

    public BottomPagerTabs(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        tabs = createTabs();

        setPadding(dp(12), 0, dp(12), 0);

        setProgress(0, false);
    }

    public Tab[] createTabs() {
        return new Tab[0];
    }

    public void setScrolling(boolean scrolling) {
        if (this.scrolling == scrolling) {
            return;
        }
        this.scrolling = scrolling;
        invalidate();
    }

    public void setProgress(float progress) {
        setProgress(progress, true);
    }

    private void setProgress(float progress, boolean animated) {
        this.value = Math.round(this.progress = Utilities.clamp(progress, tabs.length, 0));
        for (int i = 0; i < tabs.length; ++i) {
            tabs[i].setActive(Math.abs(value - i) < (tabs[i].active ? .25f : .35f), animated);
        }
        invalidate();
    }

    private Utilities.Callback<Integer> onTabClick;

    public void setOnTabClick(Utilities.Callback<Integer> listener) {
        onTabClick = listener;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

        canvas.drawRect(0, 0, getWidth(), AndroidUtilities.getShadowHeight(), Theme.dividerPaint);

        int tabFullWidth = (getWidth() - getPaddingLeft() - getPaddingRight()) / tabs.length;
        int tabWidth = Math.min(dp(64), tabFullWidth);

        float scrollingT = this.scrollingT.set(scrolling);

        if (scrollingT > 0) {
            double halfT = .4f + 2 * (1 - .4f) * Math.abs(.5f + Math.floor(progress) - progress);
            selectPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), (int) (0x12 * halfT * scrollingT)));
            float sx = getPaddingLeft() + lerp(tabFullWidth * (float) Math.floor(progress) + tabFullWidth / 2f, tabFullWidth * (float) Math.ceil(progress) + tabFullWidth / 2f, progress - (int) progress);
            AndroidUtilities.rectTmp.set(
                    sx - tabWidth / 2f,
                    dp(9),
                    sx + tabWidth / 2f,
                    dp(9 + 32)
            );
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), selectPaint);
        }

        for (int i = 0; i < tabs.length; ++i) {
            Tab tab = tabs[i];
            final int x = getPaddingLeft() + i * tabFullWidth;
            tab.clickRect.set(x, 0, x + tabFullWidth, getHeight());

            float t = 1f - Math.min(1, Math.abs(progress - i));
            tab.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), t));

            AndroidUtilities.rectTmp2.set(
                    (int) (tab.clickRect.centerX() - tabWidth / 2f),
                    dp(9),
                    (int) (tab.clickRect.centerX() + tabWidth / 2f),
                    dp(9 + 32)
            );
            final float T = tab.nonscrollingT.set(t > .6f);
            if (scrollingT < 1) {
                selectPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), (int) (0x12 * T * (1f - scrollingT))));
                AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), selectPaint);
            }

            tab.ripple.setBounds(AndroidUtilities.rectTmp2);
            tab.ripple.draw(canvas);

            final int drawableSize = dp(29);
            AndroidUtilities.rectTmp2.set(
                    (int) (tab.clickRect.centerX() - drawableSize / 2f),
                    (int) (dpf2(24.66f) - drawableSize / 2f),
                    (int) (tab.clickRect.centerX() + drawableSize / 2f),
                    (int) (dpf2(24.66f) + drawableSize / 2f)
            );

            tab.drawable.setBounds(AndroidUtilities.rectTmp2);
            tab.drawable.draw(canvas);

            canvas.save();
            canvas.translate(tab.clickRect.centerX() - tab.layoutWidth / 2f - tab.layoutLeft, dp(50) - tab.layout.getHeight() / 2f);
            tab.layout.draw(canvas);
            canvas.restore();
        }
    }

    private boolean touchDown;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDown = true;
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_MOVE) {
            int index = -1;
            final float x = event.getX();
            for (int i = 0; i < tabs.length; ++i) {
                if (tabs[i].clickRect.left < x && tabs[i].clickRect.right > x) {
                    if (event.getAction() != MotionEvent.ACTION_UP) {
                        if (touchDown) {
                            tabs[i].ripple.setState(new int[]{});
                        }
                        tabs[i].ripple.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                    }
                    index = i;
                    break;
                }
            }
            for (int i = 0; i < tabs.length; ++i) {
                if (i != index || event.getAction() == MotionEvent.ACTION_UP) {
                    tabs[i].ripple.setState(new int[] {});
                }
            }
            if (index >= 0 && value != index && onTabClick != null) {
                onTabClick.run(index);
            }
            touchDown = false;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (int i = 0; i < tabs.length; ++i) {
                    tabs[i].ripple.setState(new int[] {});
                }
            }
            touchDown = false;
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                dp(64) + AndroidUtilities.getShadowHeight()
        );
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        for (int i = 0; i < tabs.length; ++i) {
            if (tabs[i].ripple == who) {
                return true;
            }
        }
        return super.verifyDrawable(who);
    }
}
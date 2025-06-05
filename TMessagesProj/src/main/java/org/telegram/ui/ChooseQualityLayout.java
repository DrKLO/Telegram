package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Components.VideoPlayer;

public class ChooseQualityLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    public final LinearLayout buttonsLayout;
    private final Callback callback;

    public ChooseQualityLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        this.callback = callback;
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> {
            swipeBackLayout.closeForeground();
        });
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        gap.setMinimumWidth(dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = dp(8);
        gap.setLayoutParams(layoutParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(buttonsLayout);
    }

    public boolean update(VideoPlayer player) {
        if (player == null) return false;
        if (player.getQualitiesCount() <= 1) return false;

        buttonsLayout.removeAllViews();
        for (int i = -1; i < player.getQualitiesCount(); ++i) {
            final VideoPlayer.Quality q = i == -1 ? null : player.getQuality(i);
            final int index = i;
            String title = "", subtitle = "";
            if (q == null) {
                title = getString(R.string.QualityAuto);
            } else {
                String str = q.toString();
                if (str.contains("\n")) {
                    title = str.substring(0, str.indexOf("\n"));
                    subtitle = str.substring(str.indexOf("\n") + 1);
                } else {
                    title = str;
                }
            }
            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(buttonsLayout, 0, title, true, null);
            if (!TextUtils.isEmpty(subtitle)) {
                item.setSubtext(subtitle);
            }
            item.setChecked(index == player.getSelectedQuality());
            item.setColors(0xfffafafa, 0xfffafafa);
            item.setOnClickListener((view) -> {
                callback.onQualitySelected(index, true, true);
            });
            item.setSelectorColor(0x0fffffff);
        }
        return true;
    }

    public interface Callback {
        void onQualitySelected(int qualityIndex, boolean isFinal, boolean closeMenu);
    }

    public static class QualityIcon extends Drawable {

        private final Theme.ResourcesProvider resourcesProvider;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable base;
        private final RectF rect = new RectF();
        public final AnimatedTextView.AnimatedTextDrawable topText = new AnimatedTextView.AnimatedTextDrawable();
        public final AnimatedTextView.AnimatedTextDrawable bottomText = new AnimatedTextView.AnimatedTextDrawable();

        private final Paint castCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path castCutPath = new Path();
        private final Drawable castFill;
        private int castFillColor;
        public boolean cast;
        public final AnimatedFloat animatedCast = new AnimatedFloat(this::invalidateSelf, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public void setCasting(boolean casting, boolean animated) {
            if (this.cast == casting) return;
            this.cast = casting;
            if (!animated) {
                animatedCast.force(casting);
            }
            invalidateSelf();
        }

        private final Callback callback = new Callback() {
            @Override
            public void invalidateDrawable(@NonNull Drawable who) {
                QualityIcon.this.invalidateSelf();
            }
            @Override
            public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                QualityIcon.this.scheduleSelf(what, when);
            }
            @Override
            public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                QualityIcon.this.unscheduleSelf(what);
            }
        };

        public QualityIcon(Context context, int baseResId, Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;

            base = context.getResources().getDrawable(baseResId).mutate();
            castFill = context.getResources().getDrawable(R.drawable.mini_casting_fill).mutate();

            bgLinePaint.setColor(0xFFFFFFFF);
            bgLinePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            topText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            topText.setTextColor(0xFF000000);
            topText.setTextSize(dp(7));
            topText.setCallback(callback);
            topText.setGravity(Gravity.CENTER);
            topText.setOverrideFullWidth(AndroidUtilities.displaySize.x);

            bottomText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            bottomText.setTextColor(0xFF000000);
            bottomText.setTextSize(dp(7));
            bottomText.setCallback(callback);
            bottomText.setGravity(Gravity.CENTER);
            bottomText.setOverrideFullWidth(AndroidUtilities.displaySize.x);

            AndroidUtilities.rectTmp.set(dp(.66f), dp(4), dp(13), dp(13.33f));
            castCutPath.addRoundRect(AndroidUtilities.rectTmp, dp(2.66f), dp(2.66f), Path.Direction.CW);
            castCutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        private float rotation;
        public void setRotation(float rotation) {
            this.rotation = rotation;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float casting = animatedCast.set(cast);
            final float top_w = dp(5) * topText.isNotEmpty() + topText.getCurrentWidth();
            final float bottom_w = dp(5) * bottomText.isNotEmpty() + bottomText.getCurrentWidth();

            final int restoreCount = canvas.getSaveCount();
            final Rect bounds = getBounds();
            if (top_w > 0 || bottom_w > 0 || casting > 0)
                canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);

            AndroidUtilities.rectTmp2.set(dp(6), dp(6), dp(6) + (int) bounds.width() - dp(12), dp(6) + (int) bounds.height() - dp(12));
            AndroidUtilities.rectTmp2.offset(bounds.left, bounds.top);
            base.setBounds(AndroidUtilities.rectTmp2);
            canvas.save();
            canvas.rotate(rotation * -180, bounds.centerX(), bounds.centerY());
            base.draw(canvas);
            canvas.restore();

            bgPaint.setColor(0xFFFFFFFF);
            final float right = bounds.left + bounds.width() * .98f;
            final float cy_top = bounds.top + bounds.height() * .18f;
            final float cy_bottom = bounds.top + bounds.height() * .78f;
            final float h = dp(10);

            if (top_w > 0) {
                rect.set(right - top_w, cy_top - h / 2f, right, cy_top + h / 2f);
                canvas.drawRoundRect(rect, dp(3), dp(3), bgLinePaint);
            }
            if (bottom_w > 0) {
                rect.set(right - bottom_w, cy_bottom - h / 2f, right, cy_bottom + h / 2f);
                canvas.drawRoundRect(rect, dp(3), dp(3), bgLinePaint);
            }

            if (top_w * (1.0f - casting) > 0) {
                bgPaint.setAlpha((int) (0xFF * topText.isNotEmpty() * (1.0f - casting)));
                topText.setAlpha((int) (0xFF * topText.isNotEmpty() * (1.0f - casting)));
                rect.set(right - top_w, cy_top - h / 2f, right, cy_top + h / 2f);
                rect.inset(dp(1), dp(1));
                canvas.drawRoundRect(rect, dp(3), dp(3), bgPaint);
                rect.inset(-dp(1), -dp(1));
                topText.setBounds(rect);
                topText.draw(canvas);
            }
            if (casting > 0) {
                canvas.save();
                final int fillColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
                if (castFillColor != fillColor) {
                    castFill.setColorFilter(new PorterDuffColorFilter(castFillColor = fillColor, PorterDuff.Mode.SRC_IN));
                }
                castFill.setBounds(bounds.right - castFill.getIntrinsicWidth() - dp(3), bounds.top + dp(0.66f), bounds.right - dp(3), bounds.top + dp(.66f) + castFill.getIntrinsicHeight());
                castFill.setAlpha((int) (0xFF * casting));
                final float s = lerp(.8f, 1.f, casting);
                canvas.scale(s, s, castFill.getBounds().centerX(), castFill.getBounds().centerY());
                if (casting > 0.5f) {
                    canvas.save();
                    canvas.translate(castFill.getBounds().left, castFill.getBounds().top);
                    canvas.drawPath(castCutPath, castCutPaint);
                    canvas.restore();
                }
                castFill.draw(canvas);
                canvas.restore();
            }

            if (bottom_w > 0) {
                bgPaint.setAlpha((int) (0xFF * bottomText.isNotEmpty()));
                bottomText.setAlpha((int) (0xFF * bottomText.isNotEmpty()));
                rect.set(right - bottom_w, cy_bottom - h / 2f, right, cy_bottom + h / 2f);
                rect.inset(dp(1), dp(1));
                canvas.drawRoundRect(rect, dp(3), dp(3), bgPaint);
                rect.inset(-dp(1), -dp(1));
                bottomText.setBounds(rect);
                bottomText.draw(canvas);
            }

            canvas.restoreToCount(restoreCount);
        }

        @Override
        public void setAlpha(int alpha) {
            base.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            base.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return base.getOpacity();
        }

        @Override
        public int getIntrinsicWidth() {
            return base.getIntrinsicWidth() + dp(12);
        }

        @Override
        public int getIntrinsicHeight() {
            return base.getIntrinsicHeight() + dp(12);
        }
    }

}

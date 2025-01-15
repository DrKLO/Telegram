package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
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
import org.telegram.ui.Components.AnimatedTextView;
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

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable base;
        private final RectF rect = new RectF();
        public final AnimatedTextView.AnimatedTextDrawable topText = new AnimatedTextView.AnimatedTextDrawable();
        public final AnimatedTextView.AnimatedTextDrawable bottomText = new AnimatedTextView.AnimatedTextDrawable();

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

        public QualityIcon(Context context) {
            base = context.getResources().getDrawable(R.drawable.msg_settings).mutate();

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
        }

        private float rotation;
        public void setRotation(float rotation) {
            this.rotation = rotation;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float top_w = dp(5) * topText.isNotEmpty() + topText.getCurrentWidth();
            final float bottom_w = dp(5) * bottomText.isNotEmpty() + bottomText.getCurrentWidth();

            final Rect bounds = getBounds();
            if (top_w > 0 || bottom_w > 0)
                canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);

            AndroidUtilities.rectTmp2.set(dp(6), dp(6), dp(6) + (int) bounds.width() - dp(12), dp(6) + (int) bounds.height() - dp(12));
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

            if (top_w > 0 || bottom_w > 0)
                canvas.restore();

            if (top_w > 0) {
                bgPaint.setAlpha((int) (0xFF * topText.isNotEmpty()));
                topText.setAlpha((int) (0xFF * topText.isNotEmpty()));
                rect.set(right - top_w, cy_top - h / 2f, right, cy_top + h / 2f);
                rect.inset(dp(1), dp(1));
                canvas.drawRoundRect(rect, dp(3), dp(3), bgPaint);
                rect.inset(-dp(1), -dp(1));
                topText.setBounds(rect);
                topText.draw(canvas);
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

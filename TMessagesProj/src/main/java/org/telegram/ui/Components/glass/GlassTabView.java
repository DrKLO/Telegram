package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.TabsSelectorView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class GlassTabView extends FrameLayout implements TabsSelectorView.Tab, FactorAnimator.Target {
    private final TextView textView;
    private final RLottieImageView imageView;
    private Theme.ResourcesProvider resourcesProvider;
    private final Paint paintCounterBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable counter;

    private static final int ANIMATOR_ID_IS_SELECTED = 0;
    private static final int ANIMATOR_ID_COUNTER_VISIBLE = 1;
    private static final int ANIMATOR_ID_COUNTER_ERROR = 2;

    private final BoolAnimator isSelectedAnimator = new BoolAnimator(ANIMATOR_ID_IS_SELECTED, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320);
    private final BoolAnimator isHasCounterAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator isHasCounterErrorAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_ERROR, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private int colorSelected;
    private int colorSelectedText;
    private int colorDefault;

    private TabAnimation tabAnimation;

    private final TextPaint defaultTextPaint;

    public GlassTabView(@NonNull Context context) {
        super(context);
        imageView = new RLottieImageView(context);
        addView(imageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, -6, 0, 0));

        imageView.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        textView.setSingleLine();
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);

        defaultTextPaint = new TextPaint(textView.getPaint());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 28.33f, 0, 0));

        counter = new AnimatedTextView.AnimatedTextDrawable();
        counter.setTypeface(AndroidUtilities.bold());
        counter.setCallback(this);
        counter.setGravity(Gravity.CENTER);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(dp(10));
    }

    private static final RectF tmpRectF = new RectF();

    private boolean hasGestureSelectedOverride;
    private float gestureSelectedOverride;

    public void setGestureSelectedOverride(float gestureSelectedOverride, boolean allow) {
        this.gestureSelectedOverride = gestureSelectedOverride;
        this.hasGestureSelectedOverride = allow;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float selectedFactor = hasGestureSelectedOverride ? gestureSelectedOverride : isSelectedAnimator.getFloatValue();
        if (selectedFactor > 0) {
            final float alpha = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(selectedFactor);

            paintCounterBackground.setColor(Theme.multAlpha(colorSelected, 0.09f * alpha));
            tmpRectF.set(0, 0, getWidth(), getHeight());
            final float r = Math.min(tmpRectF.width(), tmpRectF.height()) / 2f;
            final float s = lerp(0.6f, 1, selectedFactor);
            canvas.save();
            canvas.scale(s, s, tmpRectF.centerX(), tmpRectF.centerY());
            canvas.drawRoundRect(tmpRectF, r, r, paintCounterBackground);
            canvas.restore();
        }

        final float hasCounter = isHasCounterAnimator.getFloatValue();
        final boolean saveLayer = hasCounter > 0;
        if (saveLayer) {
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        }

        super.dispatchDraw(canvas);

        if (hasCounter > 0) {
            canvas.save();

            final float gap = dpf2(1.33f);
            final float cx = getWidth() / 2f + dpf2(11);
            final float cy = dpf2(10);
            final float height = dpf2(16);
            final float width = Math.max(height, counter.getCurrentWidth() + dp(8));
            final float rOuter = dpf2(9.333f);
            final float rInner = dpf2(8f);
            tmpRectF.set(
                    cx - width / 2f - gap,
                    cy - height / 2f - gap,
                    cx + width / 2f + gap,
                    cy + height / 2f + gap
            );

            canvas.scale(hasCounter, hasCounter, cx, cy);
            canvas.drawRoundRect(tmpRectF, rOuter, rOuter, Theme.PAINT_CLEAR);
            tmpRectF.inset(gap, gap);
            paintCounterBackground.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_telegram_color), Theme.getColor(Theme.key_fill_RedNormal), isHasCounterErrorAnimator.getFloatValue()));
            canvas.drawRoundRect(tmpRectF, rInner, rInner, paintCounterBackground);
            counter.setBounds(tmpRectF);
            counter.draw(canvas);
            canvas.restore();
        }

        if (saveLayer) {
            canvas.restore();
        }
    }

    public void setCounter(String text, boolean isError, boolean animated) {
        counter.setText(text, animated);
        isHasCounterAnimator.setValue(!TextUtils.isEmpty(text), animated);
        isHasCounterErrorAnimator.setValue(isError, animated);
    }

    public void setSelected(boolean selected, boolean animated) {
        isSelectedAnimator.setValue(selected, animated);
        checkPlayAnimation(animated);

        textView.setTypeface(selected ? AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD) : AndroidUtilities.bold());
    }

    public boolean isSelected() {
        return isSelectedAnimator.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_IS_SELECTED) {
            updateColors();
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //    textView.setTypeface(AndroidUtilities.buildRobotoFlexTypeface(lerp(500, 800, factor)));
            //}
        }
        invalidate();
    }

    private void updateColors() {
        final int color = ColorUtils.blendARGB(colorDefault, colorSelected, isSelectedAnimator.getFloatValue());
        final int colorText = ColorUtils.blendARGB(colorDefault, colorSelectedText, isSelectedAnimator.getFloatValue());

        final PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        imageView.setColorFilter(filter);
        textView.setTextColor(colorText);
    }

    public static GlassTabView create(Context context, Theme.ResourcesProvider resourcesProvider, @DrawableRes int drawableRes, @StringRes int stringRes, Runnable onClick) {
        GlassTabView tab = new GlassTabView(context);
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.imageView.setImageResource(drawableRes);
        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 6, 0, 0));
        tab.colorDefault = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), 153);
        tab.colorSelected = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), 255);
        tab.colorSelectedText = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), 255);
        tab.setOnClickListener(v -> onClick.run());
        tab.updateColors();
        ScaleStateListAnimator.apply(tab);
        return tab;
    }

    public void updateColorsLottie() {
        colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        updateColors();
        invalidate();
    }


    private boolean lastIsSelected;
    private void checkPlayAnimation(boolean animated) {
        if (tabAnimation == null) {
            return;
        }

        final boolean isSelected = isSelectedAnimator.getValue();
        if (imageView.getAnimatedDrawable() == null) {
            imageView.setAnimation(tabAnimation.icon, 24, 24);
        }

        final RLottieDrawable drawable = imageView.getAnimatedDrawable();
        if (drawable == null) {
            return;
        }

        if (lastIsSelected != isSelected) {
            lastIsSelected = isSelected;
            if (isSelected) {
                drawable.setPlayInDirectionOfCustomEndFrame(false);
                drawable.setCurrentFrame(0);
                drawable.setCustomEndFrame(drawable.getFramesCount());
            } else {
                drawable.setPlayInDirectionOfCustomEndFrame(true);
                drawable.setCurrentFrame(drawable.getFramesCount());
                drawable.setCustomEndFrame(0);
            }
            imageView.playAnimation();
        }
    }

    public static GlassTabView createMainTab(Context context, Theme.ResourcesProvider resourcesProvider, TabAnimation tabAnimation, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.tabAnimation = tabAnimation;
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.checkPlayAnimation(false);
        // tab.imageView.getAnimatedDrawable().setAutoRepeat();

        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));

        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.updateColors();
        return tab;
    }

    public static GlassTabView createAvatar(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.imageView.setVisibility(GONE);

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);

        BackupImageView backupImageView = new BackupImageView(context);
        backupImageView.setForUserOrChat(user, avatarDrawable);
        backupImageView.setRoundRadius(dp(11));

        tab.addView(backupImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.updateColors();
        return tab;
    }

    @Override
    public float measureTextWidth() {
        return defaultTextPaint.measureText(textView.getText().toString());
    }


    public enum TabAnimation {
        CONTACTS(R.raw.tab_contacts),
        CALLS(R.raw.tab_calls),
        CHATS(R.raw.tab_chats),
        SETTINGS(R.raw.tab_settings);

        public final @RawRes int icon;

        TabAnimation(int iconRes) {
            this.icon = iconRes;
        }
    }
}
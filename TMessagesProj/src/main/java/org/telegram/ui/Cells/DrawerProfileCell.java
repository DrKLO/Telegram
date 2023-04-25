/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.Reactions.AnimatedEmojiEffect;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.SnowflakesEffect;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

public class DrawerProfileCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private TextView phoneTextView;
    private ImageView shadowView;
    private ImageView arrowView;
    private RLottieImageView darkThemeView;
    private static RLottieDrawable sunDrawable;
    private boolean updateRightDrawable = true;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status;
    private AnimatedStatusView animatedStatus;

    private Rect srcRect = new Rect();
    private Rect destRect = new Rect();
    private Paint paint = new Paint();
    private Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Integer currentColor;
    private Integer currentMoonColor;
    private SnowflakesEffect snowflakesEffect;
    private boolean accountsShown;
    private int darkThemeBackgroundColor;
    public static boolean switchingTheme;
    public boolean drawPremium;
    public float drawPremiumProgress;

    private float stateX, stateY;

    StarParticlesView.Drawable starParticlesDrawable;
    PremiumGradient.PremiumGradientTools gradientTools;

    public DrawerProfileCell(Context context, DrawerLayoutContainer drawerLayoutContainer) {
        super(context);

        shadowView = new ImageView(context);
        shadowView.setVisibility(INVISIBLE);
        shadowView.setScaleType(ImageView.ScaleType.FIT_XY);
        shadowView.setImageResource(R.drawable.bottom_shadow);
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, Gravity.LEFT | Gravity.BOTTOM));

        avatarImageView = new BackupImageView(context);
        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(32));
        addView(avatarImageView, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 0, 67));

        nameTextView = new SimpleTextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (updateRightDrawable) {
                    updateRightDrawable = false;
                    getEmojiStatusLocation(AndroidUtilities.rectTmp2);
                    animatedStatus.translate(AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
                }
            }
        };
        nameTextView.setRightDrawableOnClick(e -> {
            if (lastUser != null && lastUser.premium) {
                onPremiumClick();
            }
        });
        nameTextView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        nameTextView.setTextSize(15);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameTextView.setEllipsizeByGradient(true);
        nameTextView.setRightDrawableOutside(true);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 28));

        phoneTextView = new TextView(context);
        phoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneTextView.setLines(1);
        phoneTextView.setMaxLines(1);
        phoneTextView.setSingleLine(true);
        phoneTextView.setGravity(Gravity.LEFT);
        addView(phoneTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 52, 9));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.msg_expand);
        addView(arrowView, LayoutHelper.createFrame(59, 59, Gravity.RIGHT | Gravity.BOTTOM));
        setArrowState(false);

        boolean playDrawable;
        if (playDrawable = sunDrawable == null) {
            sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
            if (Theme.isCurrentThemeDay()) {
                sunDrawable.setCustomEndFrame(0);
                sunDrawable.setCurrentFrame(0);
            } else {
                sunDrawable.setCurrentFrame(35);
                sunDrawable.setCustomEndFrame(36);
            }
        }
        darkThemeView = new RLottieImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (Theme.isCurrentThemeDark()) {
                    info.setText(LocaleController.getString("AccDescrSwitchToDayTheme", R.string.AccDescrSwitchToDayTheme));
                } else {
                    info.setText(LocaleController.getString("AccDescrSwitchToNightTheme", R.string.AccDescrSwitchToNightTheme));
                }
            }
        };
        darkThemeView.setFocusable(true);
        darkThemeView.setBackground(Theme.createCircleSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0, 0));
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        sunDrawable.commitApplyLayerColors();
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setAnimation(sunDrawable);
        if (Build.VERSION.SDK_INT >= 21) {
            darkThemeView.setBackgroundDrawable(Theme.createSelectorDrawable(darkThemeBackgroundColor = Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(17)));
            Theme.setRippleDrawableForceSoftware((RippleDrawable) darkThemeView.getBackground());
        }
        if (!playDrawable && sunDrawable.getCustomEndFrame() != sunDrawable.getCurrentFrame()) {
            darkThemeView.playAnimation();
        }
        darkThemeView.setOnClickListener(v -> {
            if (switchingTheme) {
                return;
            }
            switchingTheme = true;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            boolean toDark;
            if (toDark = dayThemeName.equals(themeInfo.getKey())) {
                themeInfo = Theme.getTheme(nightThemeName);
                sunDrawable.setCustomEndFrame(36);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
                sunDrawable.setCustomEndFrame(0);
            }
            darkThemeView.playAnimation();
            switchTheme(themeInfo, toDark);

            if (drawerLayoutContainer != null ) {
                FrameLayout layout = drawerLayoutContainer.getParent() instanceof FrameLayout ? (FrameLayout) drawerLayoutContainer.getParent() : null;
                Theme.turnOffAutoNight(layout, () -> {
                    drawerLayoutContainer.closeDrawer(false);
                    drawerLayoutContainer.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
                });
            }
        });
        darkThemeView.setOnLongClickListener(e -> {
            if (drawerLayoutContainer != null) {
                drawerLayoutContainer.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                return true;
            }
            return false;
        });
        addView(darkThemeView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 90));

        if (Theme.getEventType() == 0) {
            snowflakesEffect = new SnowflakesEffect(0);
            snowflakesEffect.setColorKey(Theme.key_chats_menuName);
        }

        status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20));
        nameTextView.setRightDrawable(status);
        animatedStatus = new AnimatedStatusView(context, 20, 60);
        addView(animatedStatus, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.TOP));
    }

    protected void onPremiumClick() {

    }

    public static class AnimatedStatusView extends View {
        private int stateSize;
        private int effectsSize;
        private int renderedEffectsSize;

        private int animationUniq;
        private ArrayList<Object> animations = new ArrayList<>();
        public AnimatedStatusView(Context context, int stateSize, int effectsSize) {
            super(context);
            this.stateSize = stateSize;
            this.effectsSize = effectsSize;
            this.renderedEffectsSize = effectsSize;
        }
        public AnimatedStatusView(Context context, int stateSize, int effectsSize, int renderedEffectsSize) {
            super(context);
            this.stateSize = stateSize;
            this.effectsSize = effectsSize;
            this.renderedEffectsSize = renderedEffectsSize;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(Math.max(renderedEffectsSize, Math.max(stateSize, effectsSize))), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(Math.max(renderedEffectsSize, Math.max(stateSize, effectsSize))), MeasureSpec.EXACTLY)
            );
        }

        private float y1, y2;
        public void translate(float x, float y) {
            setTranslationX(x - getMeasuredWidth() / 2f);
            setTranslationY((this.y1 = y - getMeasuredHeight() / 2f) + this.y2);
        }

        public void translateY2(float y) {
            setTranslationY(this.y1 + (this.y2 = y));
        }

        @Override
        public void dispatchDraw(@NonNull Canvas canvas) {
            final int renderedEffectsSize = AndroidUtilities.dp(this.renderedEffectsSize);
            final int effectsSize = AndroidUtilities.dp(this.effectsSize);
            for (int i = 0; i < animations.size(); ++i) {
                Object animation = animations.get(i);
                if (animation instanceof ImageReceiver) {
                    ImageReceiver imageReceiver = (ImageReceiver) animation;
                    imageReceiver.setImageCoords(
                            (getMeasuredWidth() - effectsSize) / 2f,
                            (getMeasuredHeight() - effectsSize) / 2f,
                            effectsSize,
                            effectsSize
                    );
                    imageReceiver.draw(canvas);
//                    if (imageReceiver.getLottieAnimation() != null && imageReceiver.getLottieAnimation().isRunning() && imageReceiver.getLottieAnimation().isLastFrame()) {
//                        imageReceiver.onDetachedFromWindow();
//                        animations.remove(imageReceiver);
//                    }
                } else if (animation instanceof AnimatedEmojiEffect) {
                    AnimatedEmojiEffect effect = (AnimatedEmojiEffect) animation;
                    effect.setBounds(
                        (int) ((getMeasuredWidth() - renderedEffectsSize) / 2f),
                        (int) ((getMeasuredHeight() - renderedEffectsSize) / 2f),
                        (int) ((getMeasuredWidth() + renderedEffectsSize) / 2f),
                        (int) ((getMeasuredHeight() + renderedEffectsSize) / 2f)
                    );
                    effect.draw(canvas);
                    if (effect.done()) {
                        effect.removeView(this);
                        animations.remove(effect);
                    }
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            detach();
        }

        private void detach() {
            if (!animations.isEmpty()) {
                for (Object obj : animations) {
                    if (obj instanceof ImageReceiver) {
                        ((ImageReceiver) obj).onDetachedFromWindow();
                    } else if (obj instanceof AnimatedEmojiEffect) {
                        ((AnimatedEmojiEffect) obj).removeView(this);
                    }
                }
            }
            animations.clear();
        }

        public void animateChange(ReactionsLayoutInBubble.VisibleReaction react) {
            if (react == null) {
                detach();
                return;
            }

            TLRPC.Document document = null;
            TLRPC.TL_availableReaction r = null;
            if (react.emojicon != null) {
                r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(react.emojicon);
            }
            if (r == null) {
                document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, react.documentId);
                if (document != null) {
                    String emojicon = MessageObject.findAnimatedEmojiEmoticon(document, null);
                    if (emojicon != null) {
                        r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(emojicon);
                    }
                }
            }
            if (document == null && r != null) {
                ImageReceiver imageReceiver = new ImageReceiver();
                imageReceiver.setParentView(this);
                imageReceiver.setUniqKeyPrefix(Integer.toString(animationUniq++));
                imageReceiver.setImage(ImageLocation.getForDocument(r.around_animation), effectsSize + "_" + effectsSize + "_nolimit", null, "tgs", r, 1);
                imageReceiver.setAutoRepeat(0);
                imageReceiver.onAttachedToWindow();
                animations.add(imageReceiver);
                invalidate();
            } else {
                AnimatedEmojiDrawable drawable;
                if (document == null) {
                    drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, react.documentId);
                } else {
                    drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, document);
                }
                if (color != null) {
                    drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
                AnimatedEmojiEffect effect = AnimatedEmojiEffect.createFrom(drawable, false, !drawable.canOverrideColor());
                effect.setView(this);
                animations.add(effect);
                invalidate();
            }
        }

        private Integer color;
        public void setColor(int color) {
            this.color = color;
            final ColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            final ColorFilter colorFilterEmoji = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            for (int i = 0; i < animations.size(); ++i) {
                Object animation = animations.get(i);
                if (animation instanceof ImageReceiver) {
                    ((ImageReceiver) animation).setColorFilter(colorFilter);
                } else if (animation instanceof AnimatedEmojiEffect) {
                    ((AnimatedEmojiEffect) animation).animatedEmojiDrawable.setColorFilter(colorFilterEmoji);
                }
            }
        }
    }

    public void animateStateChange(long documentId) {
        animatedStatus.animateChange(ReactionsLayoutInBubble.VisibleReaction.fromCustomEmoji(documentId));
        updateRightDrawable = true;
    }

    public void getEmojiStatusLocation(Rect rect) {
        if (nameTextView.getRightDrawable() == null) {
            rect.set(nameTextView.getWidth() - 1, nameTextView.getHeight() / 2 - 1, nameTextView.getWidth() + 1, nameTextView.getHeight() / 2 + 1);
            return;
        }
        rect.set(nameTextView.getRightDrawable().getBounds());
        rect.offset((int) nameTextView.getX(), (int) nameTextView.getY());
        animatedStatus.translate(rect.centerX(), rect.centerY());
    }

    private void switchTheme(Theme.ThemeInfo themeInfo, boolean toDark) {
        int[] pos = new int[2];
        darkThemeView.getLocationInWindow(pos);
        pos[0] += darkThemeView.getMeasuredWidth() / 2;
        pos[1] += darkThemeView.getMeasuredHeight() / 2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, darkThemeView);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++){
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++){
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        }
        if (lastAccount >= 0) {
            NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
            NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.updateInterfaces);
            lastAccount = -1;
        }

        if (nameTextView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable) {
            Drawable drawable = ((AnimatedEmojiDrawable.WrapSizeDrawable) nameTextView.getRightDrawable()).getDrawable();
            if (drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawable).removeView(nameTextView);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (Build.VERSION.SDK_INT >= 21) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148) + AndroidUtilities.statusBarHeight, MeasureSpec.EXACTLY));
        } else {
            try {
                super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148), MeasureSpec.EXACTLY));
            } catch (Exception e) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(148));
                FileLog.e(e);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (drawPremium) {
            if (starParticlesDrawable == null) {
                starParticlesDrawable = new StarParticlesView.Drawable(15);
                starParticlesDrawable.init();
                starParticlesDrawable.speedScale = 0.8f;
                starParticlesDrawable.minLifeTime = 3000;
            }
            starParticlesDrawable.rect.set(avatarImageView.getLeft(), avatarImageView.getTop(), avatarImageView.getRight(), avatarImageView.getBottom());
            starParticlesDrawable.rect.inset(-AndroidUtilities.dp(20), -AndroidUtilities.dp(20));
            starParticlesDrawable.resetPositions();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable backgroundDrawable = Theme.getCachedWallpaper();
        int backgroundKey = applyBackground(false);
        boolean useImageBackground = backgroundKey != Theme.key_chats_menuTopBackground && Theme.isCustomTheme() && !Theme.isPatternWallpaper() && backgroundDrawable != null && !(backgroundDrawable instanceof ColorDrawable) && !(backgroundDrawable instanceof GradientDrawable);
        boolean drawCatsShadow = false;
        int color;
        int darkBackColor = 0;
        if (!useImageBackground && Theme.hasThemeKey(Theme.key_chats_menuTopShadowCats)) {
            color = Theme.getColor(Theme.key_chats_menuTopShadowCats);
            drawCatsShadow = true;
        } else {
            if (Theme.hasThemeKey(Theme.key_chats_menuTopShadow)) {
                color = Theme.getColor(Theme.key_chats_menuTopShadow);
            } else {
                color = Theme.getServiceMessageColor() | 0xff000000;
            }
        }
        if (currentColor == null || currentColor != color) {
            currentColor = color;
            shadowView.getDrawable().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        color = Theme.getColor(Theme.key_chats_menuName);
        if (currentMoonColor == null || currentMoonColor != color) {
            currentMoonColor = color;
            sunDrawable.beginApplyLayerColors();
            sunDrawable.setLayerColor("Sunny.**", currentMoonColor);
            sunDrawable.setLayerColor("Path 6.**", currentMoonColor);
            sunDrawable.setLayerColor("Path.**", currentMoonColor);
            sunDrawable.setLayerColor("Path 5.**", currentMoonColor);
            sunDrawable.commitApplyLayerColors();
        }
        nameTextView.setTextColor(Theme.getColor(Theme.key_chats_menuName));
        if (useImageBackground) {
            phoneTextView.setTextColor(Theme.getColor(Theme.key_chats_menuPhone));
            if (shadowView.getVisibility() != VISIBLE) {
                shadowView.setVisibility(VISIBLE);
            }
            if (backgroundDrawable instanceof ColorDrawable || backgroundDrawable instanceof GradientDrawable) {
                backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
                darkBackColor = Theme.getColor(Theme.key_listSelector);
            } else if (backgroundDrawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
                float scaleX = (float) getMeasuredWidth() / (float) bitmap.getWidth();
                float scaleY = (float) getMeasuredHeight() / (float) bitmap.getHeight();
                float scale = Math.max(scaleX, scaleY);
                int width = (int) (getMeasuredWidth() / scale);
                int height = (int) (getMeasuredHeight() / scale);
                int x = (bitmap.getWidth() - width) / 2;
                int y = (bitmap.getHeight() - height) / 2;
                srcRect.set(x, y, x + width, y + height);
                destRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                try {
                    canvas.drawBitmap(bitmap, srcRect, destRect, paint);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                darkBackColor = (Theme.getServiceMessageColor() & 0x00ffffff) | 0x50000000;
            }
        } else {
            int visibility = drawCatsShadow? VISIBLE : INVISIBLE;
            if (shadowView.getVisibility() != visibility) {
                shadowView.setVisibility(visibility);
            }
            phoneTextView.setTextColor(Theme.getColor(Theme.key_chats_menuPhoneCats));
            super.onDraw(canvas);
            darkBackColor = Theme.getColor(Theme.key_listSelector);
        }


//        if (darkBackColor != 0) {
//            if (darkBackColor != darkThemeBackgroundColor) {
//                backPaint.setColor(darkThemeBackgroundColor = darkBackColor);
//                if (Build.VERSION.SDK_INT >= 21) {
//                    Theme.setSelectorDrawableColor(darkThemeView.getBackground(), darkThemeBackgroundColor = darkBackColor, true);
//                }
//            }
//            if (useImageBackground && backgroundDrawable instanceof BitmapDrawable) {
//                canvas.drawCircle(darkThemeView.getX() + darkThemeView.getMeasuredWidth() / 2, darkThemeView.getY() + darkThemeView.getMeasuredHeight() / 2, AndroidUtilities.dp(17), backPaint);
//            }
//        }
        if (drawPremium && drawPremiumProgress != 1f) {
            drawPremiumProgress += 16 / 220f;
        } else if (!drawPremium && drawPremiumProgress != 0) {
            drawPremiumProgress -= 16 / 220f;
        }
        drawPremiumProgress = Utilities.clamp(drawPremiumProgress, 1f, 0);
        if (drawPremiumProgress != 0) {
            if (gradientTools == null) {
                gradientTools = new PremiumGradient.PremiumGradientTools(Theme.key_premiumGradientBottomSheet1, Theme.key_premiumGradientBottomSheet2, Theme.key_premiumGradientBottomSheet3, -1);
                gradientTools.x1 = 0;
                gradientTools.y1 = 1.1f;
                gradientTools.x2 = 1.5f;
                gradientTools.y2 = -0.2f;
                gradientTools.exactly = true;
            }
            gradientTools.gradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), 0, 0);
            gradientTools.paint.setAlpha((int) (drawPremiumProgress * 255));
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), gradientTools.paint);
            if (starParticlesDrawable != null) {
                starParticlesDrawable.onDraw(canvas, drawPremiumProgress);
            }
            invalidate();
        }

        if (snowflakesEffect != null) {
            snowflakesEffect.onDraw(this, canvas);
        }
    }

    public boolean isInAvatar(float x, float y) {
        return x >= avatarImageView.getLeft() && x <= avatarImageView.getRight() && y >= avatarImageView.getTop() && y <= avatarImageView.getBottom();
    }

    public boolean hasAvatar() {
        return avatarImageView.getImageReceiver().hasNotThumb();
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value) {
            return;
        }
        accountsShown = value;
        setArrowState(animated);
    }

    private int lastAccount = -1;
    private TLRPC.User lastUser = null;
    private Drawable premiumStar = null;
    public void setUser(TLRPC.User user, boolean accounts) {
        int account = UserConfig.selectedAccount;
        if (account != lastAccount) {
            if (lastAccount >= 0) {
                NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
                NotificationCenter.getInstance(lastAccount).removeObserver(this, NotificationCenter.updateInterfaces);
            }
            NotificationCenter.getInstance(lastAccount = account).addObserver(this, NotificationCenter.userEmojiStatusUpdated);
            NotificationCenter.getInstance(lastAccount = account).addObserver(this, NotificationCenter.updateInterfaces);
        }
        lastUser = user;
        if (user == null) {
            return;
        }
        accountsShown = accounts;
        setArrowState(false);
        CharSequence text = UserObject.getUserName(user);
        try {
            text = Emoji.replaceEmoji(text, nameTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(22), false);
        } catch (Exception ignore) {}

        drawPremium = false;//user.premium;
        nameTextView.setText(text);
        Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
        if (emojiStatusId != null) {
            animatedStatus.animate().alpha(1).setDuration(200).start();
            nameTextView.setDrawablePadding(AndroidUtilities.dp(4));
            status.set(emojiStatusId, true);
        } else if (user.premium) {
            animatedStatus.animate().alpha(1).setDuration(200).start();
            nameTextView.setDrawablePadding(AndroidUtilities.dp(4));
            if (premiumStar == null) {
                premiumStar = getResources().getDrawable(R.drawable.msg_premium_liststar).mutate();
            }
            premiumStar.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuPhoneCats), PorterDuff.Mode.MULTIPLY));
            status.set(premiumStar, true);
        } else {
            animatedStatus.animateChange(null);
            animatedStatus.animate().alpha(0).setDuration(200).start();
            status.set((Drawable) null, true);
        }
        animatedStatus.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
        status.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
        phoneTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        avatarDrawable.setColor(Theme.getColor(Theme.key_avatar_backgroundInProfileBlue));
        avatarImageView.setForUserOrChat(user, avatarDrawable);
        applyBackground(true);
        updateRightDrawable = true;
    }

    public Integer applyBackground(boolean force) {
        Integer currentTag = (Integer) getTag();
        int backgroundKey = Theme.hasThemeKey(Theme.key_chats_menuTopBackground) && Theme.getColor(Theme.key_chats_menuTopBackground) != 0 ? Theme.key_chats_menuTopBackground : Theme.key_chats_menuTopBackgroundCats;
        if (force || currentTag == null || backgroundKey != currentTag) {
            setBackgroundColor(Theme.getColor(backgroundKey));
            setTag(backgroundKey);
        }
        return backgroundKey;
    }

    public void updateColors() {
        if (snowflakesEffect != null) {
            snowflakesEffect.updateColors();
        }
        if (animatedStatus != null) {
            animatedStatus.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
        }
        if (status != null) {
            status.setColor(Theme.getColor(Theme.isCurrentThemeDark() ? Theme.key_chats_verifiedBackground : Theme.key_chats_menuPhoneCats));
        }
    }

    private void setArrowState(boolean animated) {
        final float rotation = accountsShown ? 180.0f : 0.0f;
        if (animated) {
            arrowView.animate().rotation(rotation).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        } else {
            arrowView.animate().cancel();
            arrowView.setRotation(rotation);
        }
        arrowView.setContentDescription(accountsShown ? LocaleController.getString("AccDescrHideAccounts", R.string.AccDescrHideAccounts) : LocaleController.getString("AccDescrShowAccounts", R.string.AccDescrShowAccounts));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            nameTextView.invalidate();
        } else if (id == NotificationCenter.userEmojiStatusUpdated) {
            setUser((TLRPC.User) args[0], accountsShown);
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            setUser(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), accountsShown);
        } else if (id == NotificationCenter.updateInterfaces) {
            int flags = (int) args[0];
            if ((flags & MessagesController.UPDATE_MASK_NAME) != 0 || (flags & MessagesController.UPDATE_MASK_AVATAR) != 0 ||
                (flags & MessagesController.UPDATE_MASK_STATUS) != 0 || (flags & MessagesController.UPDATE_MASK_PHONE) != 0 ||
                (flags & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0) {
                setUser(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), accountsShown);
            }
        }
    }

    public AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable getEmojiStatusDrawable() {
        return status;
    }

    public View getEmojiStatusDrawableParent() {
        return nameTextView;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.RoundRectShape;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.StateSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.time.SunDate;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.BlurSettingsBottomSheet;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AudioVisualizerDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChoosingStickerStatusDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FragmentContextViewWavesDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.MsgClockDrawable;
import org.telegram.ui.Components.PathAnimator;
import org.telegram.ui.Components.PlayingGameDrawable;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecordStatusDrawable;
import org.telegram.ui.Components.RoundStatusDrawable;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.SendingFileDrawable;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.Components.ThemeEditorView;
import org.telegram.ui.Components.TypingDotsDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.RoundVideoProgressShadow;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.ThemePreviewActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class Theme {

    public static final String DEFAULT_BACKGROUND_SLUG = "d";
    public static final String THEME_BACKGROUND_SLUG = "t";
    public static final String COLOR_BACKGROUND_SLUG = "c";

    public static final int MSG_OUT_COLOR_BLACK = 0xff212121;
    public static final int MSG_OUT_COLOR_WHITE = 0xffffffff;
    public static final int default_shadow_color = ColorUtils.setAlphaComponent(Color.BLACK, 27);
    public static boolean disallowChangeServiceMessageColor;

    public static void applyDefaultShadow(Paint paint) {
        paint.setShadowLayer(dpf2(1), 0, dpf2(0.33f), default_shadow_color);
    }

    public static Paint getThemePaint(String key, ResourcesProvider resourcesProvider) {
        if (resourcesProvider != null) {
            final Paint paint = resourcesProvider.getPaint(key);
            if (paint != null) {
                return paint;
            }
        }
        return getThemePaint(key);
    }

    public static ColorFilter getAnimatedEmojiColorFilter(ResourcesProvider resourcesProvider) {
        if (resourcesProvider != null) {
            return resourcesProvider.getAnimatedEmojiColorFilter();
        }
        return Theme.chat_animatedEmojiTextColorFilter;
    }

    public static class BackgroundDrawableSettings {

        public Drawable wallpaper;
        public Drawable themedWallpaper;
        public Boolean isWallpaperMotion;
        public Boolean isPatternWallpaper;
        public Boolean isCustomTheme;
    }

    public static class MessageDrawable extends Drawable {

        private Shader gradientShader;
        private int currentBackgroundHeight;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint selectedPaint;
        private int currentColor;
        private int currentGradientColor1;
        private int currentGradientColor2;
        private int currentGradientColor3;
        private boolean currentAnimateGradient;

        private RectF rect = new RectF();
        private Matrix matrix = new Matrix();
        private int currentType;
        public boolean isSelected;
        private Path path;

        public Path getPath() {
            return path;
        }

        private Rect backupRect = new Rect();

        private ResourcesProvider resourcesProvider;
        private final boolean isOut;

        private int topY;
        private boolean isTopNear;
        private boolean isBottomNear;
        private boolean botButtonsBottom;
        public boolean themePreview;

        public static MotionBackgroundDrawable[] motionBackground = new MotionBackgroundDrawable[3];

        private int[] currentShadowDrawableRadius = new int[]{-1, -1, -1, -1};
        private Bitmap[] shadowDrawableBitmap = new Bitmap[4];
        private Drawable[] shadowDrawable = new Drawable[4];
        private int[] shadowDrawableColor = new int[]{0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff};

        private int[][] currentBackgroundDrawableRadius = new int[][]{
                {-1, -1, -1, -1},
                {-1, -1, -1, -1},
                {-1, -1, -1, -1},
                {-1, -1, -1, -1}
        };
        private Drawable[][] backgroundDrawable = new Drawable[4][4];
        private int[][] backgroundDrawableColor = new int[][]{
                {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
                {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
                {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff},
                {0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff}
        };

        public static final int TYPE_TEXT = 0;
        public static final int TYPE_MEDIA = 1;
        public static final int TYPE_PREVIEW = 2;

        Drawable transitionDrawable;
        int transitionDrawableColor;
        private int alpha;
        private boolean drawFullBubble;

        public MessageDrawable crossfadeFromDrawable;
        public float crossfadeProgress;
        public boolean isCrossfadeBackground;
        public boolean lastDrawWithShadow;
        private Bitmap crosfadeFromBitmap;
        private Shader crosfadeFromBitmapShader;

        PathDrawParams pathDrawCacheParams;
        private int overrideRoundRadius;
        private float overrideRounding;
        public boolean forceInvalidatePath;

        public MessageDrawable(int type, boolean out, boolean selected) {
            this(type, out, selected, null);
        }

        public MessageDrawable(int type, boolean out, boolean selected, ResourcesProvider resourcesProvider) {
            super();
            this.resourcesProvider = resourcesProvider;
            isOut = out;
            currentType = type;
            isSelected = selected;
            path = new Path();
            selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            alpha = 255;
        }

        public boolean hasGradient() {
            return gradientShader != null && shouldDrawGradientIcons;
        }

        public void applyMatrixScale() {
            if (gradientShader instanceof BitmapShader) {
                if (isCrossfadeBackground && crosfadeFromBitmap != null) {
                    int num = currentType == TYPE_PREVIEW ? 1 : 0;
                    float scaleW = (crosfadeFromBitmap.getWidth() / (float) motionBackground[num].getBounds().width());
                    float scaleH = (crosfadeFromBitmap.getHeight() / (float) motionBackground[num].getBounds().height());
                    float scale = 1.0f / Math.min(scaleW, scaleH);
                    matrix.postScale(scale, scale);
                } else {
                    int num;
                    if (themePreview) {
                        num = 2;
                    } else {
                        num = currentType == TYPE_PREVIEW ? 1 : 0;
                    }
                    Bitmap bitmap = motionBackground[num].getBitmap();
                    float scaleW = (bitmap.getWidth() / (float) motionBackground[num].getBounds().width());
                    float scaleH = (bitmap.getHeight() / (float) motionBackground[num].getBounds().height());
                    float scale = 1.0f / Math.min(scaleW, scaleH);
                    matrix.postScale(scale, scale);
                }
            }
        }

        public Shader getGradientShader() {
            return gradientShader;
        }

        public Matrix getMatrix() {
            return matrix;
        }

        protected int getColor(int key) {
            if (currentType == TYPE_PREVIEW) {
                return Theme.getColor(key);
            }
            if (resourcesProvider != null) {
                return resourcesProvider.getColor(key);
            }
            return Theme.getColor(key);
        }

        protected int getCurrentColor(int key) {
            if (currentType == TYPE_PREVIEW) {
                return Theme.getColor(key);
            }
            return resourcesProvider != null ? resourcesProvider.getCurrentColor(key) : Theme.currentColors.get(key);
        }

        public void setBotButtonsBottom(boolean botButtonsBottom) {
            this.botButtonsBottom = botButtonsBottom;
        }

        public void setTop(int top, int backgroundWidth, int backgroundHeight, boolean topNear, boolean bottomNear) {
            setTop(top, backgroundWidth, backgroundHeight, backgroundHeight, 0, 0, topNear, bottomNear);
        }

        public void setTop(int top, int backgroundWidth, int backgroundHeight, int heightOffset, int blurredViewTopOffset, int blurredViewBottomOffset, boolean topNear, boolean bottomNear) {
            if (crossfadeFromDrawable != null) {
                crossfadeFromDrawable.setTop(top, backgroundWidth, backgroundHeight, heightOffset, blurredViewTopOffset, blurredViewBottomOffset, topNear, bottomNear);
            }
            int color;
            int gradientColor1;
            int gradientColor2;
            int gradientColor3;
            boolean animatedGradient;
            if (isOut) {
                color = getColor(isSelected ? key_chat_outBubbleSelected : key_chat_outBubble);
                gradientColor1 = getCurrentColor(key_chat_outBubbleGradient1);
                gradientColor2 = getCurrentColor(key_chat_outBubbleGradient2);
                gradientColor3 = getCurrentColor(key_chat_outBubbleGradient3);
                animatedGradient = getCurrentColor(key_chat_outBubbleGradientAnimated) != 0;
            } else {
                color = getColor(isSelected ? key_chat_inBubbleSelected : key_chat_inBubble);
                gradientColor1 = 0;
                gradientColor2 = 0;
                gradientColor3 = 0;
                animatedGradient = false;
            }
            if (gradientColor1 != 0) {
                color = getColor(key_chat_outBubble);
            }
            int num = 0;
            if (themePreview) {
                num = 2;
            } else {
                num = currentType == TYPE_PREVIEW ? 1 : 0;
            }
            if (!isCrossfadeBackground && gradientColor2 != 0 && animatedGradient && motionBackground[num] != null) {
                int[] colors = motionBackground[num].getColors();
                currentColor = colors[0];
                currentGradientColor1 = colors[1];
                currentGradientColor2 = colors[2];
                currentGradientColor3 = colors[3];
            }
            if (isCrossfadeBackground && gradientColor2 != 0 && animatedGradient) {
                if (backgroundHeight != currentBackgroundHeight || crosfadeFromBitmapShader == null || currentColor != color || currentGradientColor1 != gradientColor1 || currentGradientColor2 != gradientColor2 || currentGradientColor3 != gradientColor3 || currentAnimateGradient != animatedGradient) {
                    if (crosfadeFromBitmap == null) {
                        crosfadeFromBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
                        crosfadeFromBitmapShader = new BitmapShader(crosfadeFromBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    }
                    if (motionBackground[num] == null) {
                        motionBackground[num] = new MotionBackgroundDrawable();
                        if (currentType != TYPE_PREVIEW) {
                            motionBackground[num].setPostInvalidateParent(true);
                        }
                        motionBackground[num].setRoundRadius(dp(1));
                    }
                    motionBackground[num].setColors(color, gradientColor1, gradientColor2, gradientColor3, crosfadeFromBitmap);
                    crosfadeFromBitmapShader.setLocalMatrix(matrix);
                }
                gradientShader = crosfadeFromBitmapShader;
                paint.setShader(gradientShader);
                paint.setColor(0xffffffff);
                currentColor = color;
                currentAnimateGradient = animatedGradient;
                currentGradientColor1 = gradientColor1;
                currentGradientColor2 = gradientColor2;
                currentGradientColor3 = gradientColor3;
            } else if (gradientColor1 != 0 && (gradientShader == null || backgroundHeight != currentBackgroundHeight || currentColor != color || currentGradientColor1 != gradientColor1 || currentGradientColor2 != gradientColor2 || currentGradientColor3 != gradientColor3 || currentAnimateGradient != animatedGradient)) {
                if (gradientColor2 != 0 && animatedGradient) {
                    if (motionBackground[num] == null) {
                        motionBackground[num] = new MotionBackgroundDrawable();
                        if (currentType != TYPE_PREVIEW) {
                            motionBackground[num].setPostInvalidateParent(true);
                        }
                        motionBackground[num].setRoundRadius(dp(1));
                    }
                    motionBackground[num].setColors(color, gradientColor1, gradientColor2, gradientColor3);
                    gradientShader = motionBackground[num].getBitmapShader();
                } else {
                    if (gradientColor2 != 0) {
                        if (gradientColor3 != 0) {
                            int[] colors = new int[]{gradientColor3, gradientColor2, gradientColor1, color};
                            gradientShader = new LinearGradient(0, blurredViewTopOffset, 0, backgroundHeight, colors, null, Shader.TileMode.CLAMP);
                        } else {
                            int[] colors = new int[]{gradientColor2, gradientColor1, color};
                            gradientShader = new LinearGradient(0, blurredViewTopOffset, 0, backgroundHeight, colors, null, Shader.TileMode.CLAMP);
                        }
                    } else {
                        int[] colors = new int[]{gradientColor1, color};
                        gradientShader = new LinearGradient(0, blurredViewTopOffset, 0, backgroundHeight, colors, null, Shader.TileMode.CLAMP);
                    }
                }
                paint.setShader(gradientShader);
                currentColor = color;
                currentAnimateGradient = animatedGradient;
                currentGradientColor1 = gradientColor1;
                currentGradientColor2 = gradientColor2;
                currentGradientColor3 = gradientColor3;
                paint.setColor(0xffffffff);
            } else if (gradientColor1 == 0) {
                if (gradientShader != null) {
                    gradientShader = null;
                    paint.setShader(null);
                }
                paint.setColor(color);
            }
            if (gradientShader instanceof BitmapShader) {
                motionBackground[num].setBounds(0, blurredViewTopOffset, backgroundWidth, backgroundHeight - heightOffset);
            }
            currentBackgroundHeight = backgroundHeight;

            topY = top - (gradientShader instanceof BitmapShader ? heightOffset : 0);
            isTopNear = topNear;
            isBottomNear = bottomNear;
        }

        public void setTopBottomNear(boolean topNear, boolean bottomNear) {
            isTopNear = topNear;
            isBottomNear = bottomNear;
        }

        public int getTopY() {
            return topY;
        }

        private int dp(float value) {
            if (currentType == TYPE_PREVIEW) {
                return (int) Math.ceil(3 * value);
            } else {
                return AndroidUtilities.dp(value);
            }
        }

        public Paint getPaint() {
            return paint;
        }

        public Drawable[] getShadowDrawables() {
            return shadowDrawable;
        }

        public Drawable getBackgroundDrawable() {
            int newRad;
            if (overrideRoundRadius != 0) {
                newRad = overrideRoundRadius;
            } else if (overrideRounding > 0) {
                newRad = 0;
            } else {
                newRad = dp(SharedConfig.bubbleRadius);
            }
            int idx;
            if (isTopNear && isBottomNear) {
                idx = 3;
            } else if (isTopNear) {
                idx = 2;
            } else if (isBottomNear) {
                idx = 1;
            } else {
                idx = 0;
            }
            int idx2;
            if (isSelected && botButtonsBottom) {
                idx2 = 3;
            } else if (isSelected) {
                idx2 = 1;
            } else if (botButtonsBottom) {
                idx2 = 2;
            } else {
                idx2 = 0;
            }
            boolean forceSetColor = false;

            boolean drawWithShadow = gradientShader == null && !isSelected && !isCrossfadeBackground;
            int shadowColor = getColor(isOut ? key_chat_outBubbleShadow : key_chat_inBubbleShadow);
            if (lastDrawWithShadow != drawWithShadow || currentBackgroundDrawableRadius[idx2][idx] != newRad || (drawWithShadow && shadowDrawableColor[idx] != shadowColor)) {
                currentBackgroundDrawableRadius[idx2][idx] = newRad;
                try {
                    Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);

                    backupRect.set(getBounds());

                    if (drawWithShadow) {
                        shadowDrawableColor[idx] = shadowColor;

                        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                        LinearGradient gradientShader = new LinearGradient(0, 0, 0, dp(40), new int[]{0x155F6569, 0x295F6569}, null, Shader.TileMode.CLAMP);
                        shadowPaint.setShader(gradientShader);
                        shadowPaint.setColorFilter(new PorterDuffColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY));

                        shadowPaint.setShadowLayer(2, 0, 1, 0xffffffff);
                        if (AndroidUtilities.density > 1) {
                            setBounds(-1, -1, bitmap.getWidth() + 1, bitmap.getHeight() + 1);
                        } else {
                            setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        }
                        draw(canvas, shadowPaint);

                        if (AndroidUtilities.density > 1) {
                            shadowPaint.setColor(0);
                            shadowPaint.setShadowLayer(0, 0, 0, 0);
                            shadowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                            setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            draw(canvas, shadowPaint);
                        }
                    }

                    Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    shadowPaint.setColor(0xffffffff);
                    setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    draw(canvas, shadowPaint);

                    backgroundDrawable[idx2][idx] = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1).array(), new Rect(), null);
                    forceSetColor = true;
                    setBounds(backupRect);
                } catch (Throwable ignore) {

                }
            }
            lastDrawWithShadow = drawWithShadow;
            int color;
            if (isSelected) {
                color = getColor(isOut ? key_chat_outBubbleSelected : key_chat_inBubbleSelected);
            } else {
                color = getColor(isOut ? key_chat_outBubble : key_chat_inBubble);
            }
            if (backgroundDrawable[idx2][idx] != null && (backgroundDrawableColor[idx2][idx] != color || forceSetColor)) {
                backgroundDrawable[idx2][idx].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                backgroundDrawableColor[idx2][idx] = color;
            }
            return backgroundDrawable[idx2][idx];
        }

        public Drawable getTransitionDrawable(int color) {
            if (transitionDrawable == null) {
                Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);

                backupRect.set(getBounds());

                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(0xffffffff);
                setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                draw(canvas, shadowPaint);

                transitionDrawable = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1).array(), new Rect(), null);
                setBounds(backupRect);
            }
            if (transitionDrawableColor != color) {
                transitionDrawableColor = color;
                transitionDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }

            return transitionDrawable;
        }

        public MotionBackgroundDrawable getMotionBackgroundDrawable() {
            if (themePreview) {
                return motionBackground[2];
            }
            return motionBackground[currentType == TYPE_PREVIEW ? 1 : 0];
        }

        public Drawable getShadowDrawable() {
            if (isCrossfadeBackground) {
                return null;
            }
            if (gradientShader == null && !isSelected && crossfadeFromDrawable == null) {
                return null;
            }
            int newRad = dp(SharedConfig.bubbleRadius);
            int idx;
            if (isTopNear && isBottomNear) {
                idx = 3;
            } else if (isTopNear) {
                idx = 2;
            } else if (isBottomNear) {
                idx = 1;
            } else {
                idx = 0;
            }
            boolean forceSetColor = false;
            if (currentShadowDrawableRadius[idx] != newRad) {
                currentShadowDrawableRadius[idx] = newRad;
                if (shadowDrawableBitmap[idx] != null) {
                    shadowDrawableBitmap[idx].recycle();
                }
                try {
                    Bitmap bitmap = Bitmap.createBitmap(dp(50), dp(40), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);

                    Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                    LinearGradient gradientShader = new LinearGradient(0, 0, 0, dp(40), new int[]{0x155F6569, 0x295F6569}, null, Shader.TileMode.CLAMP);
                    shadowPaint.setShader(gradientShader);

                    shadowPaint.setShadowLayer(2, 0, 1, 0xffffffff);
                    if (AndroidUtilities.density > 1) {
                        setBounds(-1, -1, bitmap.getWidth() + 1, bitmap.getHeight() + 1);
                    } else {
                        setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    }
                    draw(canvas, shadowPaint);

                    if (AndroidUtilities.density > 1) {
                        shadowPaint.setColor(0);
                        shadowPaint.setShadowLayer(0, 0, 0, 0);
                        shadowPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                        setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        draw(canvas, shadowPaint);
                    }

                    shadowDrawableBitmap[idx] = bitmap;
                    shadowDrawable[idx] = new NinePatchDrawable(bitmap, getByteBuffer(bitmap.getWidth() / 2 - 1, bitmap.getWidth() / 2 + 1, bitmap.getHeight() / 2 - 1, bitmap.getHeight() / 2 + 1).array(), new Rect(), null);
                    forceSetColor = true;
                } catch (Throwable ignore) {

                }
            }
            int color = getColor(isOut ? key_chat_outBubbleShadow : key_chat_inBubbleShadow);
            if (shadowDrawable[idx] != null && (shadowDrawableColor[idx] != color || forceSetColor)) {
                shadowDrawable[idx].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                shadowDrawableColor[idx] = color;
            }
            return shadowDrawable[idx];
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            for (Bitmap bitmap : shadowDrawableBitmap) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
            Arrays.fill(shadowDrawableBitmap, null);
            Arrays.fill(shadowDrawable, null);
            Arrays.fill(currentShadowDrawableRadius, -1);
        }

        private static ByteBuffer getByteBuffer(int x1, int x2, int y1, int y2) {
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 * 7 + 4 * 2 + 4 * 2 + 4 * 9).order(ByteOrder.nativeOrder());
            buffer.put((byte) 0x01);
            buffer.put((byte) 2);
            buffer.put((byte) 2);
            buffer.put((byte) 0x09);

            buffer.putInt(0);
            buffer.putInt(0);

            buffer.putInt(0);
            buffer.putInt(0);
            buffer.putInt(0);
            buffer.putInt(0);

            buffer.putInt(0);

            buffer.putInt(x1);
            buffer.putInt(x2);

            buffer.putInt(y1);
            buffer.putInt(y2);

            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);
            buffer.putInt(0x00000001);

            return buffer;
        }

        public void drawCached(Canvas canvas, PathDrawParams patchDrawCacheParams, Paint paintToUse) {
            this.pathDrawCacheParams = patchDrawCacheParams;
            if (crossfadeFromDrawable != null) {
                crossfadeFromDrawable.pathDrawCacheParams = patchDrawCacheParams;
            }
            draw(canvas, paintToUse);
            this.pathDrawCacheParams = null;
            if (crossfadeFromDrawable != null) {
                crossfadeFromDrawable.pathDrawCacheParams = null;
            }
        }

        public void drawCached(Canvas canvas, PathDrawParams patchDrawCacheParams) {
            drawCached(canvas, patchDrawCacheParams, null);
        }

        @Override
        public void draw(Canvas canvas) {
            if (crossfadeFromDrawable != null) {
                crossfadeFromDrawable.draw(canvas);
                setAlpha((int) (255 * crossfadeProgress));
                draw(canvas, null);
                setAlpha(255);
            } else {
                draw(canvas, null);
            }
        }

        public void draw(Canvas canvas, Paint paintToUse) {
            Rect bounds = getBounds();
            if (paintToUse == null && gradientShader == null && overrideRoundRadius == 0 && overrideRounding <= 0) {
                Drawable background = getBackgroundDrawable();
                if (background != null) {
                    background.setBounds(bounds);
                    background.draw(canvas);
                    return;
                }
            }

            int padding = dp(2);
            int rad;
            int nearRad;
            if (overrideRoundRadius != 0) {
                rad = overrideRoundRadius;
                nearRad = overrideRoundRadius;
            } else if (overrideRounding > 0) {
                rad = AndroidUtilities.lerp(dp(SharedConfig.bubbleRadius), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
                nearRad = AndroidUtilities.lerp(dp(Math.min(6, SharedConfig.bubbleRadius)), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
            } else if (currentType == TYPE_PREVIEW) {
                rad = dp(6);
                nearRad = dp(6);
            } else {
                rad = dp(SharedConfig.bubbleRadius);
                nearRad = dp(Math.min(6, SharedConfig.bubbleRadius));
            }
            int smallRad = dp(6);

            Paint p = paintToUse == null ? paint : paintToUse;

            if (paintToUse == null && gradientShader != null) {
                matrix.reset();
                applyMatrixScale();
                matrix.postTranslate(0, -topY);
                gradientShader.setLocalMatrix(matrix);
            }

            int top = Math.max(bounds.top, 0);
            boolean drawFullBottom, drawFullTop;
            if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
                drawFullBottom = true;
                drawFullTop = true;
            } else {
                drawFullBottom = currentType == TYPE_MEDIA ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight : topY + bounds.bottom - rad < currentBackgroundHeight;
                drawFullTop = topY + rad * 2 >= 0;
            }
            Path path;
            boolean invalidatePath;
            if (pathDrawCacheParams != null) {
                path = pathDrawCacheParams.path;
                invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
            } else {
                path = this.path;
                invalidatePath = true;
            }
            if (invalidatePath || overrideRoundRadius != 0) {
                generatePath(path, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, paintToUse != null);
            }

            canvas.drawPath(path, p);
            if (gradientShader != null && isSelected && paintToUse == null) {
                int color = getColor(key_chat_outBubbleGradientSelectedOverlay);
                selectedPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha / 255f)));
                canvas.drawPath(path, selectedPaint);
            }
        }

        public Path makePath() {
            return makePath(pathDrawCacheParams);
        }

        public Path makePath(MessageDrawable.PathDrawParams pathDrawCacheParams) {
            Rect bounds = getBounds();
            int padding = dp(2);
            int rad;
            int nearRad;
            if (overrideRoundRadius != 0) {
                rad = overrideRoundRadius;
                nearRad = overrideRoundRadius;
            } else if (overrideRounding > 0) {
                rad = AndroidUtilities.lerp(dp(SharedConfig.bubbleRadius), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
                nearRad = AndroidUtilities.lerp(dp(Math.min(6, SharedConfig.bubbleRadius)), Math.min(bounds.width(), bounds.height()) / 2, overrideRounding);
            } else if (currentType == TYPE_PREVIEW) {
                rad = dp(6);
                nearRad = dp(6);
            } else {
                rad = dp(SharedConfig.bubbleRadius);
                nearRad = dp(Math.min(6, SharedConfig.bubbleRadius));
            }
            int smallRad = dp(6);
            int top = Math.max(bounds.top, 0);
            boolean drawFullBottom, drawFullTop;
            if (pathDrawCacheParams != null && bounds.height() < currentBackgroundHeight) {
                drawFullBottom = true;
                drawFullTop = true;
            } else {
                drawFullBottom = currentType == TYPE_MEDIA ? topY + bounds.bottom - smallRad * 2 < currentBackgroundHeight : topY + bounds.bottom - rad < currentBackgroundHeight;
                drawFullTop = topY + rad * 2 >= 0;
            }
            Path path;
            boolean invalidatePath;
            if (pathDrawCacheParams != null) {
                path = pathDrawCacheParams.path;
                invalidatePath = pathDrawCacheParams.invalidatePath(bounds, drawFullBottom, drawFullTop);
            } else {
                path = this.path;
                invalidatePath = true;
            }
            if (invalidatePath || overrideRoundRadius != 0) {
                generatePath(path, bounds, padding, rad, smallRad, nearRad, top, drawFullBottom, drawFullTop, true);
            }
            return path;
        }

        private void generatePath(Path path, Rect bounds, int padding, int rad, int smallRad, int nearRad, int top, boolean drawFullBottom, boolean drawFullTop, boolean customPaint) {
            path.rewind();
            int heightHalf = (bounds.height() - padding) >> 1;
            if (rad > heightHalf) {
                rad = heightHalf;
            }
            if (isOut) {
                // LEFT-BOTTOM <- RIGHT-BOTTOM
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    int radToUse = botButtonsBottom ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.moveTo(bounds.right - dp(8) - radToUse, bounds.bottom - padding);
                    } else {
                        path.moveTo(bounds.right - dp(2.6f), bounds.bottom - padding);
                    }
                    path.lineTo(bounds.left + padding + radToUse, bounds.bottom - padding);
                    rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2, bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                    path.arcTo(rect, 90, 90, false);
                } else {
                    path.moveTo(bounds.right - dp(8), top - topY + currentBackgroundHeight);
                    path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                    // LEFT-BOTTOM -> LEFT-TOP
                    path.lineTo(bounds.left + padding, bounds.top + padding + rad);
                    rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + rad * 2, bounds.top + padding + rad * 2);
                    path.arcTo(rect, 180, 90, false);

                    // LEFT-TOP -> RIGHT-TOP
                    int radToUse = isTopNear ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.right - padding - radToUse, bounds.top + padding);
                        rect.set(bounds.right - padding - radToUse * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + radToUse * 2);
                    } else {
                        path.lineTo(bounds.right - dp(8) - radToUse, bounds.top + padding);
                        rect.set(bounds.right - dp(8) - radToUse * 2, bounds.top + padding, bounds.right - dp(8), bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, 90, false);
                } else {
                    // LEFT-BOTTOM -> LEFT-TOP
                    path.lineTo(bounds.left + padding, top - topY - dp(2));

                    // LEFT-TOP -> RIGHT-TOP
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.right - padding, top - topY - dp(2));
                    } else {
                        path.lineTo(bounds.right - dp(8), top - topY - dp(2));
                    }
                }
                // RIGHT-TOP -> RIGHT-BOTTOM
                if (currentType == TYPE_MEDIA) {
                    if (customPaint || drawFullBottom) {
                        int radToUse = isBottomNear ? nearRad : rad;

                        path.lineTo(bounds.right - padding, bounds.bottom - padding - radToUse);
                        rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2, bounds.right - padding, bounds.bottom - padding);
                        path.arcTo(rect, 0, 90, false);
                    } else {
                        path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                    }
                } else {
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                        path.lineTo(bounds.right - dp(8), bounds.bottom - padding - smallRad - dp(3));
                        rect.set(bounds.right - dp(8), bounds.bottom - padding - smallRad * 2 - dp(9), bounds.right - dp(7) + smallRad * 2, bounds.bottom - padding - dp(1));
                        path.arcTo(rect, 180, -83, false);
                    } else {
                        path.lineTo(bounds.right - dp(8), top - topY + currentBackgroundHeight);
                    }
                }
            } else {
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                    int radToUse = botButtonsBottom ? nearRad : rad;

                    if (currentType == TYPE_MEDIA) {
                        path.moveTo(bounds.left + dp(8) + radToUse, bounds.bottom - padding);
                    } else {
                        path.moveTo(bounds.left + dp(2.6f), bounds.bottom - padding);
                    }
                    path.lineTo(bounds.right - padding - radToUse, bounds.bottom - padding);
                    rect.set(bounds.right - padding - radToUse * 2, bounds.bottom - padding - radToUse * 2, bounds.right - padding, bounds.bottom - padding);
                    path.arcTo(rect, 90, -90, false);
                } else {
                    path.moveTo(bounds.left + dp(8), top - topY + currentBackgroundHeight);
                    path.lineTo(bounds.right - padding, top - topY + currentBackgroundHeight);
                }
                if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullTop) {
                    path.lineTo(bounds.right - padding, bounds.top + padding + rad);
                    rect.set(bounds.right - padding - rad * 2, bounds.top + padding, bounds.right - padding, bounds.top + padding + rad * 2);
                    path.arcTo(rect, 0, -90, false);

                    int radToUse = isTopNear ? nearRad : rad;
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding + radToUse, bounds.top + padding);
                        rect.set(bounds.left + padding, bounds.top + padding, bounds.left + padding + radToUse * 2, bounds.top + padding + radToUse * 2);
                    } else {
                        path.lineTo(bounds.left + dp(8) + radToUse, bounds.top + padding);
                        rect.set(bounds.left + dp(8), bounds.top + padding, bounds.left + dp(8) + radToUse * 2, bounds.top + padding + radToUse * 2);
                    }
                    path.arcTo(rect, 270, -90, false);
                } else {
                    path.lineTo(bounds.right - padding, top - topY - dp(2));
                    if (currentType == TYPE_MEDIA) {
                        path.lineTo(bounds.left + padding, top - topY - dp(2));
                    } else {
                        path.lineTo(bounds.left + dp(8), top - topY - dp(2));
                    }
                }
                if (currentType == TYPE_MEDIA) {
                    if (customPaint || drawFullBottom) {
                        int radToUse = isBottomNear || botButtonsBottom ? nearRad : rad;

                        path.lineTo(bounds.left + padding, bounds.bottom - padding - radToUse);
                        rect.set(bounds.left + padding, bounds.bottom - padding - radToUse * 2, bounds.left + padding + radToUse * 2, bounds.bottom - padding);
                        path.arcTo(rect, 180, -90, false);
                    } else {
                        path.lineTo(bounds.left + padding, top - topY + currentBackgroundHeight);
                    }
                } else {
                    if (drawFullBubble || currentType == TYPE_PREVIEW || customPaint || drawFullBottom) {
                        path.lineTo(bounds.left + dp(8), bounds.bottom - padding - smallRad - dp(3));
                        rect.set(bounds.left + dp(7) - smallRad * 2, bounds.bottom - padding - smallRad * 2 - dp(9), bounds.left + dp(8), bounds.bottom - padding - dp(1));
                        path.arcTo(rect, 0, 83, false);
                    } else {
                        path.lineTo(bounds.left + dp(8), top - topY + currentBackgroundHeight);
                    }
                }
            }
            path.close();
        }

        public void setDrawFullBubble(boolean drawFullBuble) {
            this.drawFullBubble = drawFullBuble;
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha != alpha || this.paint.getAlpha() != alpha) {
                this.alpha = alpha;
                paint.setAlpha(alpha);
                if (isOut) {
                    selectedPaint.setAlpha((int) (Color.alpha(getColor(key_chat_outBubbleGradientSelectedOverlay)) * (alpha / 255.0f)));
                }
            }
            if (gradientShader == null) {
                Drawable background = getBackgroundDrawable();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if (background.getAlpha() != alpha) {
                        background.setAlpha(alpha);
                    }
                } else {
                    background.setAlpha(alpha);
                }
            }
        }

        @Override
        public void setColorFilter(int color, PorterDuff.Mode mode) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom);
            if (crossfadeFromDrawable != null) {
                crossfadeFromDrawable.setBounds(left, top, right, bottom);
            }
        }

        public void setRoundRadius(int radius) {
            this.overrideRoundRadius = radius;
        }

        public void setRoundingRadius(float rounding) {
            this.overrideRounding = rounding;
        }

        public void setResourceProvider(ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
        }

        public static class PathDrawParams {
            Path path = new Path();
            Rect lastRect = new Rect();
            boolean lastDrawFullTop;
            boolean lastDrawFullBottom;

            public boolean invalidatePath(Rect bounds, boolean drawFullBottom, boolean drawFullTop) {
                boolean invalidate = lastRect.isEmpty() || lastRect.top != bounds.top || lastRect.bottom != bounds.bottom || lastRect.right != bounds.right || lastRect.left != bounds.left || lastDrawFullTop != drawFullTop || lastDrawFullBottom != drawFullBottom || !drawFullTop || !drawFullBottom;
                lastDrawFullTop = drawFullTop;
                lastDrawFullBottom = drawFullBottom;
                lastRect.set(bounds);
                return invalidate;
            }
            
            public Path getPath() {
                return path;
            }
        }
    }

    public static class PatternsLoader implements NotificationCenter.NotificationCenterDelegate {

        private static class LoadingPattern {
            public TLRPC.TL_wallPaper pattern;
            public ArrayList<ThemeAccent> accents = new ArrayList<>();
        }

        private int account = UserConfig.selectedAccount;
        private HashMap<String, LoadingPattern> watingForLoad;
        private static PatternsLoader loader;

        public static void createLoader(boolean force) {
            if (loader != null && !force) {
                return;
            }
            ArrayList<ThemeAccent> accentsToLoad = null;
            for (int b = 0; b < 5; b++) {
                String key;
                switch (b) {
                    case 0:
                        key = "Blue";
                        break;
                    case 1:
                        key = "Dark Blue";
                        break;
                    case 2:
                        key = "Arctic Blue";
                        break;
                    case 3:
                        key = "Day";
                        break;
                    case 4:
                    default:
                        key = "Night";
                        break;
                }
                ThemeInfo info = themesDict.get(key);
                if (info == null || info.themeAccents == null || info.themeAccents.isEmpty()) {
                    continue;
                }
                for (int a = 0, N = info.themeAccents.size(); a < N; a++) {
                    ThemeAccent accent = info.themeAccents.get(a);
                    if (accent.id == DEFALT_THEME_ACCENT_ID || TextUtils.isEmpty(accent.patternSlug)) {
                        continue;
                    }
                    if (accentsToLoad == null) {
                        accentsToLoad = new ArrayList<>();
                    }
                    accentsToLoad.add(accent);
                }
            }
            loader = new PatternsLoader(accentsToLoad);
        }

        private PatternsLoader(ArrayList<ThemeAccent> accents) {
            if (accents == null) {
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                ArrayList<String> slugs = null;
                for (int a = 0, N = accents.size(); a < N; a++) {
                    ThemeAccent accent = accents.get(a);
                    File wallpaper = accent.getPathToWallpaper();
                    if (wallpaper != null && wallpaper.length() > 0) {
                        accents.remove(a);
                        a--;
                        N--;
                        continue;
                    }
                    if (slugs == null) {
                        slugs = new ArrayList<>();
                    }
                    if (slugs.contains(accent.patternSlug)) {
                        continue;
                    }
                    slugs.add(accent.patternSlug);
                }
                if (slugs == null) {
                    return;
                }
                TLRPC.TL_account_getMultiWallPapers req = new TLRPC.TL_account_getMultiWallPapers();
                for (int a = 0, N = slugs.size(); a < N; a++) {
                    TLRPC.TL_inputWallPaperSlug slug = new TLRPC.TL_inputWallPaperSlug();
                    slug.slug = slugs.get(a);
                    req.wallpapers.add(slug);
                }
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.Vector) {
                        TLRPC.Vector res = (TLRPC.Vector) response;
                        ArrayList<ThemeAccent> createdAccents = null;
                        for (int b = 0, N2 = res.objects.size(); b < N2; b++) {
                            TLRPC.WallPaper object = (TLRPC.WallPaper) res.objects.get(b);
                            if (!(object instanceof TLRPC.TL_wallPaper)) {
                                continue;
                            }
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                            if (wallPaper.pattern) {
                                File patternPath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(wallPaper.document, true);
                                Boolean exists = null;
                                Bitmap patternBitmap = null;
                                for (int a = 0, N = accents.size(); a < N; a++) {
                                    ThemeAccent accent = accents.get(a);
                                    if (accent.patternSlug.equals(wallPaper.slug)) {
                                        if (exists == null) {
                                            exists = patternPath.exists();
                                        }
                                        if (patternBitmap != null || exists) {
                                            patternBitmap = createWallpaperForAccent(patternBitmap, "application/x-tgwallpattern".equals(wallPaper.document.mime_type), patternPath, accent);
                                            if (createdAccents == null) {
                                                createdAccents = new ArrayList<>();
                                            }
                                            createdAccents.add(accent);
                                        } else {
                                            String key = FileLoader.getAttachFileName(wallPaper.document);
                                            if (watingForLoad == null) {
                                                watingForLoad = new HashMap<>();
                                            }
                                            LoadingPattern loadingPattern = watingForLoad.get(key);
                                            if (loadingPattern == null) {
                                                loadingPattern = new LoadingPattern();
                                                loadingPattern.pattern = wallPaper;
                                                watingForLoad.put(key, loadingPattern);
                                            }
                                            loadingPattern.accents.add(accent);
                                        }
                                    }
                                }
                                if (patternBitmap != null) {
                                    patternBitmap.recycle();
                                }
                            }
                        }
                        checkCurrentWallpaper(createdAccents, true);
                    }
                });
            });
        }

        private void checkCurrentWallpaper(ArrayList<ThemeAccent> accents, boolean load) {
            AndroidUtilities.runOnUIThread(() -> checkCurrentWallpaperInternal(accents, load));
        }

        private void checkCurrentWallpaperInternal(ArrayList<ThemeAccent> accents, boolean load) {
            if (accents != null && currentTheme.themeAccents != null && !currentTheme.themeAccents.isEmpty()) {
                if (accents.contains(currentTheme.getAccent(false))) {
                    reloadWallpaper(true);
                }
            }
            if (load) {
                if (watingForLoad != null) {
                    NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
                    NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
                    for (HashMap.Entry<String, LoadingPattern> entry : watingForLoad.entrySet()) {
                        LoadingPattern loadingPattern = entry.getValue();
                        FileLoader.getInstance(account).loadFile(ImageLocation.getForDocument(loadingPattern.pattern.document), "wallpaper", null, FileLoader.PRIORITY_LOW, 1);
                    }
                }
            } else {
                if (watingForLoad == null || watingForLoad.isEmpty()) {
                    NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
                    NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadFailed);
                }
            }
        }

        private Bitmap createWallpaperForAccent(Bitmap patternBitmap, boolean svg, File patternPath, ThemeAccent accent) {
            try {
                File toFile = accent.getPathToWallpaper();
                if (toFile == null) {
                    return null;
                }
                ThemeInfo themeInfo = accent.parentTheme;
                SparseIntArray values = getThemeFileValues(null, themeInfo.assetName, null);
                checkIsDark(values, themeInfo);
                int backgroundAccent = accent.accentColor;

                int backgroundColor = (int) accent.backgroundOverrideColor;

                int backgroundGradientColor1 = (int) accent.backgroundGradientOverrideColor1;
                if (backgroundGradientColor1 == 0 && accent.backgroundGradientOverrideColor1 == 0) {
                    if (backgroundColor != 0) {
                        backgroundAccent = backgroundColor;
                    }
                    int color = values.get(key_chat_wallpaper_gradient_to1);
                    if (color != 0) {
                        backgroundGradientColor1 = changeColorAccent(themeInfo, backgroundAccent, color);
                    }
                } else {
                    backgroundAccent = 0;
                }

                int backgroundGradientColor2 = (int) accent.backgroundGradientOverrideColor2;
                if (backgroundGradientColor2 == 0 && accent.backgroundGradientOverrideColor2 == 0) {
                    int color = values.get(key_chat_wallpaper_gradient_to2);
                    if (color != 0) {
                        backgroundGradientColor2 = changeColorAccent(themeInfo, backgroundAccent, color);
                    }
                }

                int backgroundGradientColor3 = (int) accent.backgroundGradientOverrideColor3;
                if (backgroundGradientColor3 == 0 && accent.backgroundGradientOverrideColor3 == 0) {
                    int color = values.get(key_chat_wallpaper_gradient_to3);
                    if (color != 0) {
                        backgroundGradientColor3 = changeColorAccent(themeInfo, backgroundAccent, color);
                    }
                }

                if (backgroundColor == 0) {
                    int color = values.get(key_chat_wallpaper);
                    if (color != 0) {
                        backgroundColor = changeColorAccent(themeInfo, backgroundAccent, color);
                    }
                }

                Drawable background;
                int patternColor;
                if (backgroundGradientColor2 != 0) {
                    background = null;
                    patternColor = MotionBackgroundDrawable.getPatternColor(backgroundColor, backgroundGradientColor1, backgroundGradientColor2, backgroundGradientColor3);
                } else if (backgroundGradientColor1 != 0) {
                    BackgroundGradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(accent.backgroundRotation);
                    background = new BackgroundGradientDrawable(orientation, new int[]{backgroundColor, backgroundGradientColor1});
                    patternColor = AndroidUtilities.getPatternColor(AndroidUtilities.getAverageColor(backgroundColor, backgroundGradientColor1));
                } else {
                    background = new ColorDrawable(backgroundColor);
                    patternColor = AndroidUtilities.getPatternColor(backgroundColor);
                }

                if (patternBitmap == null) {
                    int w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                    int h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                    if (svg) {
                        patternBitmap = SvgHelper.getBitmap(patternPath, w, h, false);
                    } else {
                        patternBitmap = loadScreenSizedBitmap(new FileInputStream(patternPath), 0);
                    }
                }

                if (background != null) {
                    Bitmap dst = Bitmap.createBitmap(patternBitmap.getWidth(), patternBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(dst);
                    if (background != null) {
                        background.setBounds(0, 0, patternBitmap.getWidth(), patternBitmap.getHeight());
                        background.draw(canvas);
                    }

                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    paint.setColorFilter(new PorterDuffColorFilter(patternColor, PorterDuff.Mode.SRC_IN));
                    paint.setAlpha((int) (255 * Math.abs(accent.patternIntensity)));
                    canvas.drawBitmap(patternBitmap, 0, 0, paint);

                    FileOutputStream stream = new FileOutputStream(toFile);
                    dst.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                } else {
                    FileOutputStream stream = new FileOutputStream(toFile);
                    patternBitmap.compress(Bitmap.CompressFormat.PNG, 87, stream);
                    stream.close();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return patternBitmap;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (watingForLoad == null) {
                return;
            }
            if (id == NotificationCenter.fileLoaded) {
                String location = (String) args[0];
                LoadingPattern loadingPattern = watingForLoad.remove(location);
                if (loadingPattern != null) {
                    Utilities.globalQueue.postRunnable(() -> {
                        ArrayList<ThemeAccent> createdAccents = null;
                        TLRPC.TL_wallPaper wallPaper = loadingPattern.pattern;
                        File patternPath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(wallPaper.document, true);
                        Bitmap patternBitmap = null;
                        for (int a = 0, N = loadingPattern.accents.size(); a < N; a++) {
                            ThemeAccent accent = loadingPattern.accents.get(a);
                            if (accent.patternSlug.equals(wallPaper.slug)) {
                                patternBitmap = createWallpaperForAccent(patternBitmap, "application/x-tgwallpattern".equals(wallPaper.document.mime_type), patternPath, accent);
                                if (createdAccents == null) {
                                    createdAccents = new ArrayList<>();
                                    createdAccents.add(accent);
                                }
                            }
                        }
                        if (patternBitmap != null) {
                            patternBitmap.recycle();
                        }
                        checkCurrentWallpaper(createdAccents, false);
                    });
                }
            } else if (id == NotificationCenter.fileLoadFailed) {
                String location = (String) args[0];
                if (watingForLoad.remove(location) != null) {
                    checkCurrentWallpaper(null, false);
                }
            }
        }
    }

    public static class ThemeAccent {
        public int id;

        public ThemeInfo parentTheme;

        public int accentColor;
        public int accentColor2;
        public int myMessagesAccentColor;
        public int myMessagesGradientAccentColor1;
        public int myMessagesGradientAccentColor2;
        public int myMessagesGradientAccentColor3;
        public boolean myMessagesAnimated;
        public long backgroundOverrideColor;
        public long backgroundGradientOverrideColor1;
        public long backgroundGradientOverrideColor2;
        public long backgroundGradientOverrideColor3;
        public int backgroundRotation = 45;
        public String patternSlug = "";
        public float patternIntensity;
        public boolean patternMotion;

        public TLRPC.TL_theme info;
        public TLRPC.TL_wallPaper pattern;
        public int account;

        public String pathToFile;
        public String uploadingThumb;
        public String uploadingFile;
        public TLRPC.InputFile uploadedThumb;
        public TLRPC.InputFile uploadedFile;

        public OverrideWallpaperInfo overrideWallpaper;
        public boolean isDefault;

        ThemeAccent() {

        }

        public ThemeAccent(ThemeAccent other) {
            this.id = other.id;
            this.parentTheme = other.parentTheme;
            this.accentColor = other.accentColor;
            this.myMessagesAccentColor = other.myMessagesAccentColor;
            this.myMessagesGradientAccentColor1 = other.myMessagesGradientAccentColor1;
            this.myMessagesGradientAccentColor2 = other.myMessagesGradientAccentColor2;
            this.myMessagesGradientAccentColor3 = other.myMessagesGradientAccentColor3;
            this.myMessagesAnimated = other.myMessagesAnimated;
            this.backgroundOverrideColor = other.backgroundOverrideColor;
            this.backgroundGradientOverrideColor1 = other.backgroundGradientOverrideColor1;
            this.backgroundGradientOverrideColor2 = other.backgroundGradientOverrideColor2;
            this.backgroundGradientOverrideColor3 = other.backgroundGradientOverrideColor3;
            this.backgroundRotation = other.backgroundRotation;
            this.patternSlug = other.patternSlug;
            this.patternIntensity = other.patternIntensity;
            this.patternMotion = other.patternMotion;
            this.info = other.info;
            this.pattern = other.pattern;
            this.account = other.account;
            this.pathToFile = other.pathToFile;
            this.uploadingThumb = other.uploadingThumb;
            this.uploadingFile = other.uploadingFile;
            this.uploadedThumb = other.uploadedThumb;
            this.uploadedFile = other.uploadedFile;
            this.overrideWallpaper = other.overrideWallpaper;
        }

        public boolean fillAccentColors(SparseIntArray currentColorsNoAccent, SparseIntArray currentColors) {
            boolean isMyMessagesGradientColorsNear = false;

            float[] hsvTemp1 = getTempHsv(1);
            float[] hsvTemp2 = getTempHsv(2);

            Color.colorToHSV(parentTheme.accentBaseColor, hsvTemp1);
            Color.colorToHSV(accentColor, hsvTemp2);
            boolean isDarkTheme = parentTheme.isDark();

            if (accentColor != parentTheme.accentBaseColor || accentColor2 != 0) {
                for (int i = 0; i < defaultColors.length; i++) {
                    int key = i;
                    if (themeAccentExclusionKeys.contains(key)) {
                        continue;
                    }
                    int index = currentColorsNoAccent.indexOfKey(key);
                    int color;
                    if (index < 0) {
                        int fallbackKey = fallbackKeys.get(key, -1);
                        if (fallbackKey >= 0 && currentColorsNoAccent.indexOfKey(fallbackKey) >= 0) {
                            continue;
                        }
                        color = defaultColors[key];
                    } else {
                        color = currentColorsNoAccent.valueAt(index);
                    }
                    int newColor = changeColorAccent(hsvTemp1, hsvTemp2, color, isDarkTheme, color);
                    if (newColor != color) {
                        currentColors.put(key, newColor);
                    }
                }
            }
            int myMessagesAccent = myMessagesAccentColor;
            if ((myMessagesAccentColor != 0 || accentColor != 0) && myMessagesGradientAccentColor1 != 0) {
                int firstColor = myMessagesAccentColor != 0 ? myMessagesAccentColor : accentColor;
                int color = currentColorsNoAccent.get(key_chat_outBubble);
                if (color == 0) {
                    color = defaultColors[key_chat_outBubble];
                }
                int newColor = changeColorAccent(hsvTemp1, hsvTemp2, color, isDarkTheme, color);
                int distance1 = AndroidUtilities.getColorDistance(firstColor, newColor);
                int distance2 = AndroidUtilities.getColorDistance(firstColor, myMessagesGradientAccentColor1);

                boolean useBlackText;
                if (myMessagesGradientAccentColor2 != 0) {
                    int averageColor = AndroidUtilities.getAverageColor(myMessagesAccentColor, myMessagesGradientAccentColor1);
                    averageColor = AndroidUtilities.getAverageColor(averageColor, myMessagesGradientAccentColor2);
                    if (myMessagesGradientAccentColor3 != 0) {
                        averageColor = AndroidUtilities.getAverageColor(averageColor, myMessagesGradientAccentColor3);
                    }
                    useBlackText = AndroidUtilities.computePerceivedBrightness(averageColor) > 0.705f;
                } else {
                    useBlackText = useBlackText(myMessagesAccentColor, myMessagesGradientAccentColor1);
                }
                if (useBlackText) {
                    isMyMessagesGradientColorsNear = distance1 <= 35000 && distance2 <= 35000;
                } else {
                    isMyMessagesGradientColorsNear = false;
                }
                myMessagesAccent = getAccentColor(hsvTemp1, color, firstColor);
            }
            boolean changeMyMessagesColors = (myMessagesAccent != 0 && (parentTheme.accentBaseColor != 0 && myMessagesAccent != parentTheme.accentBaseColor || accentColor != 0 && accentColor != myMessagesAccent));
            if (changeMyMessagesColors || accentColor2 != 0) {
                if (accentColor2 != 0) {
                    Color.colorToHSV(accentColor2, hsvTemp2);
                } else {
                    Color.colorToHSV(myMessagesAccent, hsvTemp2);
                }

                for (int key = myMessagesStartIndex; key < myMessagesEndIndex; key++) {
                    int index = currentColorsNoAccent.indexOfKey(key);
                    int color;
                    if (index < 0) {
                        int fallbackKey = fallbackKeys.get(key, -1);
                        if (fallbackKey >= 0 && currentColorsNoAccent.get(fallbackKey, -1) >= 0) {
                            continue;
                        }
                        color = defaultColors[key];
                    } else {
                        color = currentColorsNoAccent.valueAt(index);
                    }
                    int newColor = changeColorAccent(hsvTemp1, hsvTemp2, color, isDarkTheme, color);
                    if (newColor != color) {
                        currentColors.put(key, newColor);
                    }
                }

                if (changeMyMessagesColors) {
                    Color.colorToHSV(myMessagesAccent, hsvTemp2);
                    for (int key = myMessagesBubblesStartIndex; key < myMessagesBubblesEndIndex; key++) {
                        int index = currentColorsNoAccent.indexOfKey(key);
                        int color;
                        if (index < 0) {
                            int fallbackKey = fallbackKeys.get(key, -1);
                            if (fallbackKey >= 0 && currentColorsNoAccent.get(fallbackKey, -1) >= 0) {
                                continue;
                            }
                            color = defaultColors[key];
                        } else {
                            color = currentColorsNoAccent.valueAt(index);
                        }
                        int newColor = changeColorAccent(hsvTemp1, hsvTemp2, color, isDarkTheme, color);
                        if (newColor != color) {
                            currentColors.put(key, newColor);
                        }
                    }
                }
            }
            if (!isMyMessagesGradientColorsNear) {
                if (myMessagesGradientAccentColor1 != 0) {
                    int textColor;
                    int subTextColor;
                    int seekbarColor;
                    boolean useBlackText;
                    if (myMessagesGradientAccentColor2 != 0) {
                        int color = AndroidUtilities.getAverageColor(myMessagesAccentColor, myMessagesGradientAccentColor1);
                        color = AndroidUtilities.getAverageColor(color, myMessagesGradientAccentColor2);
                        if (myMessagesGradientAccentColor3 != 0) {
                            color = AndroidUtilities.getAverageColor(color, myMessagesGradientAccentColor3);
                        }
                        useBlackText = AndroidUtilities.computePerceivedBrightness(color) > 0.705f;
                    } else {
                        useBlackText = useBlackText(myMessagesAccentColor, myMessagesGradientAccentColor1);
                    }
                    if (useBlackText) {
                        textColor = MSG_OUT_COLOR_BLACK;
                        subTextColor = 0xff555555;
                        seekbarColor = 0x4d000000;
                    } else {
                        textColor = MSG_OUT_COLOR_WHITE;
                        subTextColor = 0xffeeeeee;
                        seekbarColor = 0x4dffffff;
                    }

                    if (accentColor2 == 0) {
                        currentColors.put(key_chat_outAudioProgress, seekbarColor);
                        currentColors.put(key_chat_outAudioSelectedProgress, seekbarColor);
                        currentColors.put(key_chat_outAudioSeekbar, seekbarColor);
                        currentColors.put(key_chat_outAudioCacheSeekbar, seekbarColor);
                        currentColors.put(key_chat_outAudioSeekbarSelected, seekbarColor);
                        currentColors.put(key_chat_outAudioSeekbarFill, textColor);

                        currentColors.put(key_chat_outVoiceSeekbar, seekbarColor);
                        currentColors.put(key_chat_outVoiceSeekbarSelected, seekbarColor);
                        currentColors.put(key_chat_outVoiceSeekbarFill, textColor);

                        currentColors.put(key_chat_messageLinkOut, textColor);
                        currentColors.put(key_chat_outForwardedNameText, textColor);
                        currentColors.put(key_chat_outViaBotNameText, textColor);
                        currentColors.put(key_chat_outReplyLine, textColor);
                        currentColors.put(key_chat_outReplyLine2, textColor);
                        currentColors.put(key_chat_outReplyNameText, textColor);

                        currentColors.put(key_chat_outPreviewLine, textColor);
                        currentColors.put(key_chat_outSiteNameText, textColor);
                        currentColors.put(key_chat_outInstant, textColor);
                        currentColors.put(key_chat_outInstantSelected, textColor);
                        currentColors.put(key_chat_outPreviewInstantText, textColor);

                        currentColors.put(key_chat_outViews, textColor);
                        currentColors.put(key_chat_outViewsSelected, textColor);

                        currentColors.put(key_chat_outAudioTitleText, textColor);
                        currentColors.put(key_chat_outFileNameText, textColor);
                        currentColors.put(key_chat_outContactNameText, textColor);

                        currentColors.put(key_chat_outAudioPerformerText, textColor);
                        currentColors.put(key_chat_outAudioPerformerSelectedText, textColor);

                        currentColors.put(key_chat_outSentCheck, textColor);
                        currentColors.put(key_chat_outSentCheckSelected, textColor);

                        currentColors.put(key_chat_outSentCheckRead, textColor);
                        currentColors.put(key_chat_outSentCheckReadSelected, textColor);

                        currentColors.put(key_chat_outSentClock, textColor);
                        currentColors.put(key_chat_outSentClockSelected, textColor);

                        currentColors.put(key_chat_outMenu, textColor);
                        currentColors.put(key_chat_outMenuSelected, textColor);

                        currentColors.put(key_chat_outTimeText, textColor);
                        currentColors.put(key_chat_outTimeSelectedText, textColor);

                        currentColors.put(key_chat_outAudioDurationText, subTextColor);
                        currentColors.put(key_chat_outAudioDurationSelectedText, subTextColor);

                        currentColors.put(key_chat_outContactPhoneText, subTextColor);
                        currentColors.put(key_chat_outContactPhoneSelectedText, subTextColor);

                        currentColors.put(key_chat_outFileInfoText, subTextColor);
                        currentColors.put(key_chat_outFileInfoSelectedText, subTextColor);

                        currentColors.put(key_chat_outVenueInfoText, subTextColor);
                        currentColors.put(key_chat_outVenueInfoSelectedText, subTextColor);


                        currentColors.put(key_chat_outLoader, textColor);
                        currentColors.put(key_chat_outLoaderSelected, textColor);
                        currentColors.put(key_chat_outFileProgress, myMessagesAccentColor);
                        currentColors.put(key_chat_outFileProgressSelected, myMessagesAccentColor);
                        currentColors.put(key_chat_outMediaIcon, myMessagesAccentColor);
                        currentColors.put(key_chat_outMediaIconSelected, myMessagesAccentColor);
                    }

                    currentColors.put(key_chat_outReplyMessageText, textColor);
                    currentColors.put(key_chat_outReplyMediaMessageText, textColor);
                    currentColors.put(key_chat_outReplyMediaMessageSelectedText, textColor);
                    currentColors.put(key_chat_messageTextOut, textColor);
                }
            }
            if (isMyMessagesGradientColorsNear) {
                int outColor = currentColors.indexOfKey(key_chat_outLoader) >= 0
                        ? currentColors.get(key_chat_outLoader)
                        : Color.TRANSPARENT;
                if (AndroidUtilities.getColorDistance(0xffffffff, outColor) < 5000) {
                    isMyMessagesGradientColorsNear = false;
                }
            }
            if (myMessagesAccentColor != 0 && myMessagesGradientAccentColor1 != 0) {
                currentColors.put(key_chat_outBubble, myMessagesAccentColor);
                currentColors.put(key_chat_outBubbleGradient1, myMessagesGradientAccentColor1);
                if (myMessagesGradientAccentColor2 != 0) {
                    currentColors.put(key_chat_outBubbleGradient2, myMessagesGradientAccentColor2);
                    if (myMessagesGradientAccentColor3 != 0) {
                        currentColors.put(key_chat_outBubbleGradient3, myMessagesGradientAccentColor3);
                    }
                }
                currentColors.put(key_chat_outBubbleGradientAnimated, myMessagesAnimated ? 1 : 0);
            }
            int backgroundOverride = (int) backgroundOverrideColor;
            if (backgroundOverride != 0) {
                currentColors.put(key_chat_wallpaper, backgroundOverride);
            } else if (backgroundOverrideColor != 0) {
                currentColors.delete(key_chat_wallpaper);
            }
            int backgroundGradientOverride1 = (int) backgroundGradientOverrideColor1;
            if (backgroundGradientOverride1 != 0) {
                currentColors.put(key_chat_wallpaper_gradient_to1, backgroundGradientOverride1);
            } else if (backgroundGradientOverrideColor1 != 0) {
                currentColors.delete(key_chat_wallpaper_gradient_to1);
            }
            int backgroundGradientOverride2 = (int) backgroundGradientOverrideColor2;
            if (backgroundGradientOverride2 != 0) {
                currentColors.put(key_chat_wallpaper_gradient_to2, backgroundGradientOverride2);
            } else if (backgroundGradientOverrideColor2 != 0) {
                currentColors.delete(key_chat_wallpaper_gradient_to2);
            }
            int backgroundGradientOverride3 = (int) backgroundGradientOverrideColor3;
            if (backgroundGradientOverride3 != 0) {
                currentColors.put(key_chat_wallpaper_gradient_to3, backgroundGradientOverride3);
            } else if (backgroundGradientOverrideColor3 != 0) {
                currentColors.delete(key_chat_wallpaper_gradient_to3);
            }
            if (backgroundRotation != 45) {
                currentColors.put(key_chat_wallpaper_gradient_rotation, backgroundRotation);
            }

            int outBubble = currentColors.get(key_chat_outBubble);
            if (outBubble == 0) {
                outBubble = getColor(key_chat_outBubble);
            }
            int inBubble = currentColors.get(key_chat_inBubble);
            if (inBubble == 0) {
                inBubble = getColor(key_chat_inBubble);
            }

            int gradientAverageColor = outBubble;
            if (info != null && info.emoticon != null && !isDarkTheme) {
                currentColors.delete(key_chat_selectedBackground);
                gradientAverageColor = averageColor(currentColors, key_chat_wallpaper_gradient_to1, key_chat_wallpaper_gradient_to2, key_chat_wallpaper_gradient_to3);
                if (gradientAverageColor == 0) {
                    gradientAverageColor = averageColor(currentColors, key_chat_wallpaper);
                }
                if (gradientAverageColor == 0) {
                    gradientAverageColor = accentColor;
                }

                int outOverlay = bubbleSelectedOverlay(outBubble, gradientAverageColor);
                currentColors.put(key_chat_outBubbleSelectedOverlay, outOverlay);
                currentColors.put(key_chat_outBubbleGradientSelectedOverlay, outOverlay);
                currentColors.put(key_chat_outBubbleSelected, Theme.blendOver(outBubble, outOverlay));

                int inOverlay = bubbleSelectedOverlay(inBubble, accentColor);
                currentColors.put(key_chat_inBubbleSelectedOverlay, inOverlay);
                currentColors.put(key_chat_inBubbleSelected, Theme.blendOver(inBubble, inOverlay));
            }

            if (!isDarkTheme) {
                currentColors.put(key_chat_inTextSelectionHighlight, textSelectionBackground(false, inBubble, accentColor));
                currentColors.put(key_chat_outTextSelectionHighlight, textSelectionBackground(true, outBubble, accentColor));
                currentColors.put(key_chat_outTextSelectionCursor, textSelectionHandle(outBubble, accentColor));
            }

            float accentHue = getHue(getColor(key_windowBackgroundWhiteBlueText));
            currentColors.put(key_chat_outBubbleLocationPlaceholder, locationPlaceholderColor(accentHue, outBubble, isDarkTheme));
            currentColors.put(key_chat_inBubbleLocationPlaceholder, locationPlaceholderColor(accentHue, inBubble, isDarkTheme));

            int inMsgLink = currentColors.get(key_chat_messageLinkIn);
            if (inMsgLink == 0) {
                inMsgLink = getColor(key_chat_messageLinkIn);
            }
            int outMsgLink = currentColors.get(key_chat_messageLinkOut);
            if (outMsgLink == 0) {
                outMsgLink = getColor(key_chat_messageLinkOut);
            }
            currentColors.put(key_chat_linkSelectBackground, linkSelectionBackground(inMsgLink, inBubble, isDarkTheme));
            currentColors.put(key_chat_outLinkSelectBackground, linkSelectionBackground(outMsgLink, outBubble, isDarkTheme));

            int submenuBackground = currentColors.get(key_actionBarDefaultSubmenuBackground);
            if (submenuBackground == 0) {
                submenuBackground = getColor(key_actionBarDefaultSubmenuBackground);
            }
            currentColors.put(key_actionBarDefaultSubmenuSeparator, Color.argb(
                Color.alpha(submenuBackground),
                Math.max(0, Color.red(submenuBackground) - 10),
                Math.max(0, Color.green(submenuBackground) - 10),
                Math.max(0, Color.blue(submenuBackground) - 10)
            ));

            if (isDarkTheme && currentColors.get(key_chat_outBubbleGradient1) != 0) {
                int outBubbleAverage = averageColor(currentColors, key_chat_outBubbleGradient1, key_chat_outBubbleGradient2, key_chat_outBubbleGradient3);
                Color.colorToHSV(outBubbleAverage, tempHSV);
                tempHSV[1] = Utilities.clamp(tempHSV[1] + .1f, 1, 0);
                tempHSV[2] = Utilities.clamp(tempHSV[2] - .8f, 1, 0);
                currentColors.put(key_chat_outCodeBackground, Color.HSVToColor(0x40, tempHSV));
            } else {
                currentColors.put(key_chat_outCodeBackground, codeBackground(outBubble, isDarkTheme));
            }

            return !isMyMessagesGradientColorsNear;
        }

        private float[] tempHSV = new float[3];
        private int setHue(int color, int hueFromColor) {
            Color.colorToHSV(hueFromColor, tempHSV);
            float hue = tempHSV[0];
            Color.colorToHSV(color, tempHSV);
            if (tempHSV[1] > 0.02f) {
                tempHSV[0] = hue;
            }
            return Color.HSVToColor(Color.alpha(color), tempHSV);
        }
        private float getHue(int color) {
            Color.colorToHSV(color, tempHSV);
            return tempHSV[0];
        }
        private int bubbleSelectedOverlay(int bubble, int accentColor) {
            Color.colorToHSV(accentColor, tempHSV);
            float h = tempHSV[0];
            Color.colorToHSV(bubble, tempHSV);
            if (tempHSV[1] <= 0) {
                tempHSV[0] = h;
            }
            tempHSV[1] = Math.max(0, Math.min(1, tempHSV[1] + .6f));
            tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] - .05f));
            return Color.HSVToColor(30, tempHSV);
        }
        private int textSelectionBackground(boolean isOut, int bubble, int accentColor) {
            Color.colorToHSV(accentColor, tempHSV);
            float h = tempHSV[0];
            Color.colorToHSV(bubble, tempHSV);
            if (tempHSV[1] <= 0 || tempHSV[0] > 45 && tempHSV[0] < 85) {
                tempHSV[0] = h;
            }
            tempHSV[1] = Math.max(0, Math.min(1, tempHSV[1] + (tempHSV[2] > .85f ? .25f : .45f)));
            tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] - .15f));
            return Color.HSVToColor(80, tempHSV);
        }
        private int textSelectionHandle(int bubble, int accentColor) {
            Color.colorToHSV(accentColor, tempHSV);
            float h = tempHSV[0];
            Color.colorToHSV(bubble, tempHSV);
            if (tempHSV[1] <= 0 || tempHSV[0] > 45 && tempHSV[0] < 85) {
                tempHSV[0] = h;
            }
            tempHSV[1] = Math.max(0, Math.min(1, tempHSV[1] + .6f));
            tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] - (tempHSV[2] > .7f ? .25f : .125f)));
            return Theme.blendOver(bubble, Color.HSVToColor(255, tempHSV));
        }
        private int linkSelectionBackground(int linkColor, int bgColor, boolean isDarkTheme) {
            Color.colorToHSV(ColorUtils.blendARGB(linkColor, bgColor, .25f), tempHSV);
            tempHSV[1] = Math.max(0, Math.min(1, tempHSV[1] - .1f));
            tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] + (isDarkTheme ? .1f : 0)));
            return Color.HSVToColor(0x33, tempHSV);
        }
        private int codeBackground(int bubbleColor, boolean isDarkTheme) {
            Color.colorToHSV(bubbleColor, tempHSV);
            int alpha = 0x20;
            if (isDarkTheme) {
                alpha = 0x40;
                tempHSV[1] = Utilities.clamp(tempHSV[1] - .08f, 1f, 0f);
                tempHSV[2] = .03f;
            } else {
                if (tempHSV[1] <= 0 || tempHSV[2] >= 1 || tempHSV[2] <= 0) {
                    tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] + -.2f));
                } else {
                    tempHSV[1] = Math.max(0, Math.min(1, tempHSV[1] + .28f));
                    tempHSV[2] = Math.max(0, Math.min(1, tempHSV[2] + -.1f));
                }
            }
            return Color.HSVToColor(alpha, tempHSV);
        }
        private int locationPlaceholderColor(float accentHue, int bubbleColor, boolean isDarkTheme) {
            if (isDarkTheme) {
                return 0x1effffff;
            } else {
                Color.colorToHSV(bubbleColor, tempHSV);
                if (tempHSV[1] <= 0 || tempHSV[2] >= 1 || tempHSV[2] <= 0) {
                    tempHSV[0] = accentHue;
                    tempHSV[1] = .2f;
                } else {
                    tempHSV[0] = MathUtils.clamp(tempHSV[0] + .22f, 0, 1);
                    tempHSV[1] = MathUtils.clamp(tempHSV[1] - .35f, 0, 1);
                }
                tempHSV[2] = MathUtils.clamp(tempHSV[2] - .65f, 0, 1);
                return Color.HSVToColor(0x5a, tempHSV);
            }
        }
        private int averageColor(SparseIntArray colors, int ...keys) {
            int r = 0, g = 0, b = 0, c = 0;
            for (int i = 0; i < keys.length; ++i) {
                if (colors.indexOfKey(keys[i]) < 0) {
                    continue;
                }
                try {
                    int color = colors.get(keys[i]);
                    r += Color.red(color);
                    g += Color.green(color);
                    b += Color.blue(color);
                    c++;
                } catch (Exception ignore) {}
            }
            if (c == 0) {
                return 0;
            }
            return Color.argb(255, r / c, g / c, b / c);
        }

        public File getPathToWallpaper() {
            if (id < 100) {
                return !TextUtils.isEmpty(patternSlug) ? new File(ApplicationLoader.getFilesDirFixed(), String.format(Locale.US, "%s_%d_%s_v5.jpg", parentTheme.getKey(), id, patternSlug)) : null;
            } else {
                return !TextUtils.isEmpty(patternSlug) ? new File(ApplicationLoader.getFilesDirFixed(), String.format(Locale.US, "%s_%d_%s_v8_debug.jpg", parentTheme.getKey(), id, patternSlug)) : null;
            }
        }

        public File saveToFile() {
            File dir = AndroidUtilities.getSharingDirectory();
            dir.mkdirs();
            File path = new File(dir, String.format(Locale.US, "%s_%d.attheme", parentTheme.getKey(), id));

            SparseIntArray currentColorsNoAccent = getThemeFileValues(null, parentTheme.assetName, null);
            SparseIntArray currentColors = currentColorsNoAccent.clone();
            fillAccentColors(currentColorsNoAccent, currentColors);

            String wallpaperLink = null;

            if (!TextUtils.isEmpty(patternSlug)) {
                StringBuilder modes = new StringBuilder();
                if (patternMotion) {
                    modes.append("motion");
                }
                int selectedColor = currentColors.get(key_chat_wallpaper);
                if (selectedColor == 0) {
                    selectedColor = 0xffffffff;
                }
                int selectedGradientColor1 = currentColors.get(key_chat_wallpaper_gradient_to1);
                if (selectedGradientColor1 == 0) {
                    selectedGradientColor1 = 0;
                }
                int selectedGradientColor2 = currentColors.get(key_chat_wallpaper_gradient_to2);
                if (selectedGradientColor2 == 0) {
                    selectedGradientColor2 = 0;
                }
                int selectedGradientColor3 = currentColors.get(key_chat_wallpaper_gradient_to3);
                if (selectedGradientColor3 == 0) {
                    selectedGradientColor3 = 0;
                }
                int selectedGradientRotation = currentColors.get(key_chat_wallpaper_gradient_rotation);
                if (selectedGradientRotation == 0) {
                    selectedGradientRotation = 45;
                }
                String color = String.format("%02x%02x%02x", (byte) (selectedColor >> 16) & 0xff, (byte) (selectedColor >> 8) & 0xff, (byte) (selectedColor & 0xff)).toLowerCase();
                String color2 = selectedGradientColor1 != 0 ? String.format("%02x%02x%02x", (byte) (selectedGradientColor1 >> 16) & 0xff, (byte) (selectedGradientColor1 >> 8) & 0xff, (byte) (selectedGradientColor1 & 0xff)).toLowerCase() : null;
                String color3 = selectedGradientColor2 != 0 ? String.format("%02x%02x%02x", (byte) (selectedGradientColor2 >> 16) & 0xff, (byte) (selectedGradientColor2 >> 8) & 0xff, (byte) (selectedGradientColor2 & 0xff)).toLowerCase() : null;
                String color4 = selectedGradientColor3 != 0 ? String.format("%02x%02x%02x", (byte) (selectedGradientColor3 >> 16) & 0xff, (byte) (selectedGradientColor3 >> 8) & 0xff, (byte) (selectedGradientColor3 & 0xff)).toLowerCase() : null;
                if (color2 != null && color3 != null) {
                    if (color4 != null) {
                        color += "~" + color2 + "~" + color3 + "~" + color4;
                    } else {
                        color += "~" + color2 + "~" + color3;
                    }
                } else if (color2 != null) {
                    color += "-" + color2;
                    color += "&rotation=" + selectedGradientRotation;
                }
                wallpaperLink = "https://attheme.org?slug=" + patternSlug + "&intensity=" + (int) (patternIntensity * 100) + "&bg_color=" + color;
                if (modes.length() > 0) {
                    wallpaperLink += "&mode=" + modes.toString();
                }
            }

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < currentColors.size(); i++) {
                int key = currentColors.keyAt(i);
                int color = currentColors.valueAt(i);
                if (wallpaperLink != null) {
                    if (key_chat_wallpaper == key || key_chat_wallpaper_gradient_to1 == key || key_chat_wallpaper_gradient_to2 == key || key_chat_wallpaper_gradient_to3 == key) {
                        continue;
                    }
                }
                result.append(key).append("=").append(color).append("\n");
            }
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(path);
                stream.write(AndroidUtilities.getStringBytes(result.toString()));
                if (!TextUtils.isEmpty(wallpaperLink)) {
                    stream.write(AndroidUtilities.getStringBytes("WLS=" + wallpaperLink + "\n"));
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return path;
        }
    }

    public static int blendOver(int A, int B) { // B over A
        // over operator: https://en.wikipedia.org/wiki/Alpha_compositing#Description
        float aB = Color.alpha(B) / 255f,
              aA = Color.alpha(A) / 255f,
              aC = (aB + aA * (1 - aB));
        if (aC == 0f)
            return 0;
        return Color.argb(
            (int) (aC * 255),
            (int) ((Color.red(B) * aB + Color.red(A) * aA * (1 - aB)) / aC),
            (int) ((Color.green(B) * aB + Color.green(A) * aA * (1 - aB)) / aC),
            (int) ((Color.blue(B) * aB + Color.blue(A) * aA * (1 - aB)) / aC)
        );
    }

    public static int adaptHue(int color, int hueFromColor) {
        float hue, sat;
        float[] tempHSV = getTempHsv(5);
        Color.colorToHSV(hueFromColor, tempHSV);
        hue = tempHSV[0];
        sat = tempHSV[1];
        Color.colorToHSV(color, tempHSV);
        tempHSV[0] = hue;
        tempHSV[1] = AndroidUtilities.lerp(tempHSV[1], sat, .25f);
        return Color.HSVToColor(Color.alpha(color), tempHSV);
    }

    public static int adaptHSV(int color, float sat, float val) {
        float[] tempHSV = getTempHsv(5);
        Color.colorToHSV(color, tempHSV);
        if (tempHSV[1] > .1f && tempHSV[1] < .9f) { // otherwise, saturation would reveal some random hue there
            tempHSV[1] = MathUtils.clamp(tempHSV[1] + sat, 0, 1);
        }
        tempHSV[2] = MathUtils.clamp(tempHSV[2] + val, 0, 1);
        return Color.HSVToColor(Color.alpha(color), tempHSV);
    }

    public static int percentSV(int color1, int color2, float satT, float valT) {
        float[] tempHSV = getTempHsv(5);
        Color.colorToHSV(color2, tempHSV);
        float sat2 = tempHSV[1];
        float val2 = tempHSV[2];
        Color.colorToHSV(color1, tempHSV);
        tempHSV[1] = MathUtils.clamp(AndroidUtilities.lerp(tempHSV[1], sat2, satT), 0, 1);
        tempHSV[2] = MathUtils.clamp(AndroidUtilities.lerp(tempHSV[2], val2, valT), 0, 1);
        return Color.HSVToColor(
            AndroidUtilities.lerp(Color.alpha(color1), Color.alpha(color2), .85f),
            tempHSV
        );
    }

    public static int multAlpha(int color, float multiply) {
        if (multiply == 1f)
            return color;
        return ColorUtils.setAlphaComponent(color, MathUtils.clamp((int) (Color.alpha(color) * multiply), 0, 0xFF));
    }

    public static int reverseBlendOver(float ax, int y, int z) {
        float ay = Color.alpha(y) / 255f,
              az = Color.alpha(z) / 255f;
        return Color.argb(
            (int) (ax * 255),
            (int) ((Color.red(y) * ay * (1 - ax) - Color.red(z) * az) / ax),
            (int) ((Color.green(y) * ay * (1 - ax) - Color.green(z) * az) / ax),
            (int) ((Color.blue(y) * ay * (1 - ax) - Color.blue(z) * az) / ax)
        );
    }

    public static class OverrideWallpaperInfo {
        public String fileName = "";
        public String originalFileName = "";
        public String slug = "";
        public int color;
        public int gradientColor1;
        public int gradientColor2;
        public int gradientColor3;
        public int rotation;
        public boolean isBlurred;
        public boolean isMotion;
        public float intensity;
        public long wallpaperId;
        public long accessHash;
        public long dialogId;
        public boolean forBoth;

        public ThemeInfo parentTheme;
        public ThemeAccent parentAccent;
        public float uploadingProgress;
        public ArrayList<Integer> requestIds;
        public TLRPC.WallPaper prevUserWallpaper;

        public OverrideWallpaperInfo() {

        }

        public OverrideWallpaperInfo(OverrideWallpaperInfo info, ThemeInfo themeInfo, ThemeAccent accent) {
            slug = info.slug;
            color = info.color;
            gradientColor1 = info.gradientColor1;
            gradientColor2 = info.gradientColor2;
            gradientColor3 = info.gradientColor3;
            rotation = info.rotation;
            isBlurred = info.isBlurred;
            isMotion = info.isMotion;
            intensity = info.intensity;
            parentTheme = themeInfo;
            parentAccent = accent;
            if (!TextUtils.isEmpty(info.fileName)) {
                try {
                    File fromFile = new File(ApplicationLoader.getFilesDirFixed(), info.fileName);
                    File toFile = new File(ApplicationLoader.getFilesDirFixed(), fileName = parentTheme.generateWallpaperName(parentAccent, false));
                    AndroidUtilities.copyFile(fromFile, toFile);
                } catch (Exception e) {
                    fileName = "";
                    FileLog.e(e);
                }
            } else {
                fileName = "";
            }
            if (!TextUtils.isEmpty(info.originalFileName)) {
                if (!info.originalFileName.equals(info.fileName)){
                    try {
                        File fromFile = new File(ApplicationLoader.getFilesDirFixed(), info.originalFileName);
                        File toFile = new File(ApplicationLoader.getFilesDirFixed(), originalFileName = parentTheme.generateWallpaperName(parentAccent, true));
                        AndroidUtilities.copyFile(fromFile, toFile);
                    } catch (Exception e) {
                        originalFileName = "";
                        FileLog.e(e);
                    }
                } else {
                    originalFileName = fileName;
                }
            } else {
                originalFileName = "";
            }
        }

        public boolean isDefault() {
            return DEFAULT_BACKGROUND_SLUG.equals(slug);
        }

        public boolean isColor() {
            return COLOR_BACKGROUND_SLUG.equals(slug);
        }

        public boolean isTheme() {
            return THEME_BACKGROUND_SLUG.equals(slug);
        }

        public void saveOverrideWallpaper() {
            if (parentTheme == null || parentAccent == null && parentTheme.overrideWallpaper != this || parentAccent != null && parentAccent.overrideWallpaper != this) {
                return;
            }
            save();
        }

        private String getKey() {
            if (parentAccent != null) {
                return parentTheme.name + "_" + parentAccent.id + "_owp";
            } else {
                return parentTheme.name + "_owp";
            }
        }

        private void save() {
            try {
                String key = getKey();
                SharedPreferences themeConfig = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = themeConfig.edit();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("wall", fileName);
                jsonObject.put("owall", originalFileName);
                jsonObject.put("pColor", color);
                jsonObject.put("pGrColor", gradientColor1);
                jsonObject.put("pGrColor2", gradientColor2);
                jsonObject.put("pGrColor3", gradientColor3);
                jsonObject.put("pGrAngle", rotation);
                jsonObject.put("wallSlug", slug != null ? slug : "");
                jsonObject.put("wBlur", isBlurred);
                jsonObject.put("wMotion", isMotion);
                jsonObject.put("pIntensity", intensity);
                editor.putString(key, jsonObject.toString());
                editor.commit();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        private void delete() {
            String key = getKey();
            SharedPreferences themeConfig = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            themeConfig.edit().remove(key).commit();
            new File(ApplicationLoader.getFilesDirFixed(), fileName).delete();
            new File(ApplicationLoader.getFilesDirFixed(), originalFileName).delete();
        }
    }

    public static class ThemeInfo implements NotificationCenter.NotificationCenterDelegate {
        public String name;
        public String pathToFile;
        public String pathToWallpaper;
        public String assetName;
        public String slug;
        public boolean badWallpaper;
        public boolean isBlured;
        public boolean isMotion;
        public int patternBgColor;
        public int patternBgGradientColor1;
        public int patternBgGradientColor2;
        public int patternBgGradientColor3;
        public int patternBgGradientRotation = 45;
        public int patternIntensity;

        public int account;

        public TLRPC.TL_theme info;
        public boolean loaded = true;

        public String uploadingThumb;
        public String uploadingFile;
        public TLRPC.InputFile uploadedThumb;
        public TLRPC.InputFile uploadedFile;

        private int previewBackgroundColor;
        public int previewBackgroundGradientColor1;
        public int previewBackgroundGradientColor2;
        public int previewBackgroundGradientColor3;
        public int previewWallpaperOffset;
        private int previewInColor;
        private int previewOutColor;
        public boolean firstAccentIsDefault;
        public boolean previewParsed;
        public boolean themeLoaded = true;

        public int sortIndex;

        public int defaultAccentCount;

        public int accentBaseColor;

        public int currentAccentId;
        public int prevAccentId = -1;
        public SparseArray<ThemeAccent> themeAccentsMap;
        public ArrayList<ThemeAccent> themeAccents;
        public LongSparseArray<ThemeAccent> accentsByThemeId;
        public LongSparseArray<ThemeAccent> chatAccentsByThemeId = new LongSparseArray<>();
        public int lastChatThemeId = 0;
        public int lastAccentId = 100;

        private String loadingThemeWallpaperName;
        private String newPathToWallpaper;

        public OverrideWallpaperInfo overrideWallpaper;
        private int isDark = UNKNOWN;

        private final static int DARK= 1;
        private final static int LIGHT = 0;
        private final static int UNKNOWN = -1;

        ThemeInfo() {

        }

        public ThemeInfo(ThemeInfo other) {
            this.name = other.name;
            this.pathToFile = other.pathToFile;
            this.pathToWallpaper = other.pathToWallpaper;
            this.assetName = other.assetName;
            this.slug = other.slug;
            this.badWallpaper = other.badWallpaper;
            this.isBlured = other.isBlured;
            this.isMotion = other.isMotion;
            this.patternBgColor = other.patternBgColor;
            this.patternBgGradientColor1 = other.patternBgGradientColor1;
            this.patternBgGradientColor2 = other.patternBgGradientColor2;
            this.patternBgGradientColor3 = other.patternBgGradientColor3;
            this.patternBgGradientRotation = other.patternBgGradientRotation;
            this.patternIntensity = other.patternIntensity;
            this.account = other.account;
            this.info = other.info;
            this.loaded = other.loaded;
            this.uploadingThumb = other.uploadingThumb;
            this.uploadingFile = other.uploadingFile;
            this.uploadedThumb = other.uploadedThumb;
            this.uploadedFile = other.uploadedFile;
            this.previewBackgroundColor = other.previewBackgroundColor;
            this.previewBackgroundGradientColor1 = other.previewBackgroundGradientColor1;
            this.previewBackgroundGradientColor2 = other.previewBackgroundGradientColor2;
            this.previewBackgroundGradientColor3 = other.previewBackgroundGradientColor3;
            this.previewWallpaperOffset = other.previewWallpaperOffset;
            this.previewInColor = other.previewInColor;
            this.previewOutColor = other.previewOutColor;
            this.firstAccentIsDefault = other.firstAccentIsDefault;
            this.previewParsed = other.previewParsed;
            this.themeLoaded = other.themeLoaded;
            this.sortIndex = other.sortIndex;
            this.defaultAccentCount = other.defaultAccentCount;
            this.accentBaseColor = other.accentBaseColor;
            this.currentAccentId = other.currentAccentId;
            this.prevAccentId = other.prevAccentId;
            this.themeAccentsMap = other.themeAccentsMap;
            this.themeAccents = other.themeAccents;
            this.accentsByThemeId = other.accentsByThemeId;
            this.lastAccentId = other.lastAccentId;
            this.loadingThemeWallpaperName = other.loadingThemeWallpaperName;
            this.newPathToWallpaper = other.newPathToWallpaper;
            this.overrideWallpaper = other.overrideWallpaper;
        }

        JSONObject getSaveJson() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", name);
                jsonObject.put("path", pathToFile);
                jsonObject.put("account", account);
                if (info != null) {
                    SerializedData data = new SerializedData(info.getObjectSize());
                    info.serializeToStream(data);
                    jsonObject.put("info", Utilities.bytesToHex(data.toByteArray()));
                }
                jsonObject.put("loaded", loaded);
                return jsonObject;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        private void loadWallpapers(SharedPreferences sharedPreferences) {
            if (themeAccents != null && !themeAccents.isEmpty()) {
                for (int a = 0, N = themeAccents.size(); a < N; a++) {
                    ThemeAccent accent = themeAccents.get(a);
                    loadOverrideWallpaper(sharedPreferences, accent, name + "_" + accent.id + "_owp");
                }
            } else {
                loadOverrideWallpaper(sharedPreferences, null, name + "_owp");
            }
        }

        private void loadOverrideWallpaper(SharedPreferences sharedPreferences, ThemeAccent accent, String key) {
            try {
                String json = sharedPreferences.getString(key,  null);
                if (TextUtils.isEmpty(json)) {
                    return;
                }
                JSONObject object = new JSONObject(json);
                OverrideWallpaperInfo wallpaperInfo = new OverrideWallpaperInfo();
                wallpaperInfo.fileName = object.getString("wall");
                wallpaperInfo.originalFileName = object.getString("owall");
                wallpaperInfo.color = object.getInt("pColor");
                wallpaperInfo.gradientColor1 = object.getInt("pGrColor");
                wallpaperInfo.gradientColor2 = object.optInt("pGrColor2");
                wallpaperInfo.gradientColor3 = object.optInt("pGrColor3");
                wallpaperInfo.rotation = object.getInt("pGrAngle");
                wallpaperInfo.slug = object.getString("wallSlug");
                wallpaperInfo.isBlurred = object.getBoolean("wBlur");
                wallpaperInfo.isMotion = object.getBoolean("wMotion");
                wallpaperInfo.intensity = (float) object.getDouble("pIntensity");
                wallpaperInfo.parentTheme = this;
                wallpaperInfo.parentAccent = accent;
                if (accent != null) {
                    accent.overrideWallpaper = wallpaperInfo;
                } else {
                    overrideWallpaper = wallpaperInfo;
                }
                if (object.has("wallId")) {
                    long id = object.getLong("wallId");
                    if (id == 1000001) {
                        wallpaperInfo.slug = DEFAULT_BACKGROUND_SLUG;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        public void setOverrideWallpaper(OverrideWallpaperInfo info) {
            if (overrideWallpaper == info) {
                return;
            }
            ThemeAccent accent = getAccent(false);
            if (overrideWallpaper != null) {
                overrideWallpaper.delete();
            }
            if (info != null) {
                info.parentAccent = accent;
                info.parentTheme = this;
                info.save();
            }
            overrideWallpaper = info;
            if (accent != null) {
                accent.overrideWallpaper = info;
            }
        }

        public String getName() {
            if ("Blue".equals(name)) {
                return getString(R.string.ThemeClassic);
            } else if ("Dark Blue".equals(name)) {
                return getString(R.string.ThemeDark);
            } else if ("Arctic Blue".equals(name)) {
                return getString(R.string.ThemeArcticBlue);
            } else if ("Day".equals(name)) {
                return getString(R.string.ThemeDay);
            } else if ("Night".equals(name)) {
                return getString(R.string.ThemeNight);
            }
            return info != null ? info.title : name;
        }

        public void setCurrentAccentId(int id) {
            currentAccentId = id;
            ThemeAccent accent = getAccent(false);
            if (accent != null) {
                overrideWallpaper = accent.overrideWallpaper;
            }
        }

        public String generateWallpaperName(ThemeAccent accent, boolean original) {
            if (accent == null) {
                accent = getAccent(false);
            }
            if (accent != null) {
                return (original ? name + "_" + accent.id + "_wp_o" : name + "_" + accent.id + "_wp") + Utilities.random.nextInt() + ".jpg";
            } else {
                return (original ? name + "_wp_o" : name + "_wp") + Utilities.random.nextInt() + ".jpg";
            }
        }

        public void setPreviewInColor(int color) {
            previewInColor = color;
        }

        public void setPreviewOutColor(int color) {
            previewOutColor = color;
        }

        public void setPreviewBackgroundColor(int color) {
            previewBackgroundColor = color;
        }

        public int getPreviewInColor() {
            if (firstAccentIsDefault && currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return 0xffffffff;
            }
            return previewInColor;
        }

        public int getPreviewOutColor() {
            if (firstAccentIsDefault && currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return 0xfff0fee0;
            }
            return previewOutColor;
        }

        public int getPreviewBackgroundColor() {
            if (firstAccentIsDefault && currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return 0xffcfd9e3;
            }
            return previewBackgroundColor;
        }


        private boolean isDefaultMyMessagesBubbles() {
            if (!firstAccentIsDefault) {
                return false;
            }
            if (currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return true;
            }
            ThemeAccent defaultAccent = themeAccentsMap.get(DEFALT_THEME_ACCENT_ID);
            ThemeAccent accent = themeAccentsMap.get(currentAccentId);
            if (defaultAccent == null || accent == null) {
                return false;
            }
            return defaultAccent.myMessagesAccentColor == accent.myMessagesAccentColor &&
                    defaultAccent.myMessagesGradientAccentColor1 == accent.myMessagesGradientAccentColor1 &&
                    defaultAccent.myMessagesGradientAccentColor2 == accent.myMessagesGradientAccentColor2 &&
                    defaultAccent.myMessagesGradientAccentColor3 == accent.myMessagesGradientAccentColor3 &&
                    defaultAccent.myMessagesAnimated == accent.myMessagesAnimated;
        }

        private boolean isDefaultMyMessages() {
            if (!firstAccentIsDefault) {
                return false;
            }
            if (currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return true;
            }
            ThemeAccent defaultAccent = themeAccentsMap.get(DEFALT_THEME_ACCENT_ID);
            ThemeAccent accent = themeAccentsMap.get(currentAccentId);
            if (defaultAccent == null || accent == null) {
                return false;
            }
            return defaultAccent.accentColor2 == accent.accentColor2 &&
                    defaultAccent.myMessagesAccentColor == accent.myMessagesAccentColor &&
                    defaultAccent.myMessagesGradientAccentColor1 == accent.myMessagesGradientAccentColor1 &&
                    defaultAccent.myMessagesGradientAccentColor2 == accent.myMessagesGradientAccentColor2 &&
                    defaultAccent.myMessagesGradientAccentColor3 == accent.myMessagesGradientAccentColor3 &&
                    defaultAccent.myMessagesAnimated == accent.myMessagesAnimated;
        }

        private boolean isDefaultMainAccent() {
            if (!firstAccentIsDefault) {
                return false;
            }
            if (currentAccentId == DEFALT_THEME_ACCENT_ID) {
                return true;
            }
            ThemeAccent defaultAccent = themeAccentsMap.get(DEFALT_THEME_ACCENT_ID);
            ThemeAccent accent = themeAccentsMap.get(currentAccentId);
            return accent != null && defaultAccent != null && defaultAccent.accentColor == accent.accentColor;
        }

        public boolean hasAccentColors() {
            return defaultAccentCount != 0;
        }

        public boolean isDark() {
            if (isDark != UNKNOWN) {
                return isDark == DARK;
            }
            if ("Dark Blue".equals(name) || "Night".equals(name)) {
                isDark = DARK;
            } else if ("Blue".equals(name) || "Arctic Blue".equals(name) || "Day".equals(name)) {
                isDark = LIGHT;
            }
            if (isDark == UNKNOWN) {
                String[] wallpaperLink = new String[1];
                SparseIntArray colors = getThemeFileValues(new File(pathToFile), null, wallpaperLink);
                checkIsDark(colors, this);
            }
            return isDark == DARK;
        }

        public boolean isLight() {
            return pathToFile == null && !isDark();
        }

        public String getKey() {
            if (info != null) {
                return "remote" + info.id;
            }
            return name;
        }

        static ThemeInfo createWithJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            try {
                ThemeInfo themeInfo = new ThemeInfo();
                themeInfo.name = object.getString("name");
                themeInfo.pathToFile = object.getString("path");
                if (object.has("account")) {
                    themeInfo.account = object.getInt("account");
                }
                if (object.has("info")) {
                    try {
                        SerializedData serializedData = new SerializedData(Utilities.hexToBytes(object.getString("info")));
                        themeInfo.info = (TLRPC.TL_theme) TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                if (object.has("loaded")) {
                    themeInfo.loaded = object.getBoolean("loaded");
                }
                return themeInfo;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        static ThemeInfo createWithString(String string) {
            if (TextUtils.isEmpty(string)) {
                return null;
            }
            String[] args = string.split("\\|");
            if (args.length != 2) {
                return null;
            }
            ThemeInfo themeInfo = new ThemeInfo();
            themeInfo.name = args[0];
            themeInfo.pathToFile = args[1];
            return themeInfo;
        }

        private void setAccentColorOptions(int[] options) {
            setAccentColorOptions(options, null, null, null, null, null, null, null, null, null, null);
        }

        private void setAccentColorOptions(int[] accent, int[] myMessages, int[] myMessagesGradient, int[] background, int[] backgroundGradient1, int[] backgroundGradient2, int[] backgroundGradient3, int[] ids, String[] patternSlugs, int[] patternRotations, int[] patternIntensities) {
            defaultAccentCount = accent.length;
            themeAccents = new ArrayList<>();
            themeAccentsMap = new SparseArray<>();
            accentsByThemeId = new LongSparseArray<>();

            for (int a = 0; a < accent.length; a++) {
                ThemeAccent themeAccent = new ThemeAccent();
                themeAccent.id = ids != null ? ids[a] : a;
                if (isHome(themeAccent)) {
                    themeAccent.isDefault = true;
                }
                themeAccent.accentColor = accent[a];
                themeAccent.parentTheme = this;
                if (myMessages != null) {
                    themeAccent.myMessagesAccentColor = myMessages[a];
                }
                if (myMessagesGradient != null) {
                    themeAccent.myMessagesGradientAccentColor1 = myMessagesGradient[a];
                }
                if (background != null) {
                    themeAccent.backgroundOverrideColor = background[a];
                    if (firstAccentIsDefault && themeAccent.id == DEFALT_THEME_ACCENT_ID) {
                        themeAccent.backgroundOverrideColor = 0x100000000L;
                    } else {
                        themeAccent.backgroundOverrideColor = background[a];
                    }
                }
                if (backgroundGradient1 != null) {
                    if (firstAccentIsDefault && themeAccent.id == DEFALT_THEME_ACCENT_ID) {
                        themeAccent.backgroundGradientOverrideColor1 = 0x100000000L;
                    } else {
                        themeAccent.backgroundGradientOverrideColor1 = backgroundGradient1[a];
                    }
                }
                if (backgroundGradient2 != null) {
                    if (firstAccentIsDefault && themeAccent.id == DEFALT_THEME_ACCENT_ID) {
                        themeAccent.backgroundGradientOverrideColor2 = 0x100000000L;
                    } else {
                        themeAccent.backgroundGradientOverrideColor2 = backgroundGradient2[a];
                    }
                }
                if (backgroundGradient3 != null) {
                    if (firstAccentIsDefault && themeAccent.id == DEFALT_THEME_ACCENT_ID) {
                        themeAccent.backgroundGradientOverrideColor3 = 0x100000000L;
                    } else {
                        themeAccent.backgroundGradientOverrideColor3 = backgroundGradient3[a];
                    }
                }
                if (patternSlugs != null) {
                    themeAccent.patternIntensity = patternIntensities[a] / 100.0f;
                    themeAccent.backgroundRotation = patternRotations[a];
                    themeAccent.patternSlug = patternSlugs[a];
                }

                //override default themes
                if (isHome(themeAccent) && name.equals("Dark Blue") || name.equals("Night")) {
                    themeAccent.myMessagesAccentColor = 0xff6573f8;
                    themeAccent.myMessagesGradientAccentColor1 = 0xff7644cb;
                    themeAccent.myMessagesGradientAccentColor2 = 0xff8849b4;
                    themeAccent.myMessagesGradientAccentColor3 = 0xffa751a8;
                    if (name.equals("Night")) {
                        themeAccent.patternIntensity = -0.57f;
                        themeAccent.backgroundOverrideColor = 0xff6c7fa6;
                        themeAccent.backgroundGradientOverrideColor1 = 0xff2e344b;
                        themeAccent.backgroundGradientOverrideColor2 = 0xff7874a7;
                        themeAccent.backgroundGradientOverrideColor3 = 0xff333258;
                    }
                }
                themeAccentsMap.put(themeAccent.id, themeAccent);
                themeAccents.add(themeAccent);
            }
            accentBaseColor = themeAccentsMap.get(0).accentColor;
        }

        @UiThread
        private void loadThemeDocument() {
            loaded = false;
            loadingThemeWallpaperName = null;
            newPathToWallpaper = null;
            addObservers();
            FileLoader.getInstance(account).loadFile(info.document, info, FileLoader.PRIORITY_NORMAL, 1);
        }

        private void addObservers() {
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
        }


        @UiThread
        private void removeObservers() {
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadFailed);
        }

        private void onFinishLoadingRemoteTheme() {
            loaded = true;
            previewParsed = false;
            saveOtherThemes(true);
            if (this == currentTheme && previousTheme == null) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, this, this == currentNightTheme, null, -1, fallbackKeys);
            }
        }

        public static boolean accentEquals(ThemeAccent accent, TLRPC.ThemeSettings settings) {
            int bottomColor = settings.message_colors.size() > 0 ? settings.message_colors.get(0) | 0xff000000 : 0;
            int myMessagesGradientAccentColor1 = settings.message_colors.size() > 1 ? settings.message_colors.get(1) | 0xff000000 : 0;
            if (bottomColor == myMessagesGradientAccentColor1) {
                myMessagesGradientAccentColor1 = 0;
            }
            int myMessagesGradientAccentColor2 = settings.message_colors.size() > 2 ? settings.message_colors.get(2) | 0xff000000 : 0;
            int myMessagesGradientAccentColor3 = settings.message_colors.size() > 3 ? settings.message_colors.get(3) | 0xff000000 : 0;
            int backgroundOverrideColor = 0;
            long backgroundGradientOverrideColor1 = 0;
            long backgroundGradientOverrideColor2 = 0;
            long backgroundGradientOverrideColor3 = 0;
            int backgroundRotation = 0;
            String patternSlug = null;
            float patternIntensity = 0;
            if (settings.wallpaper != null && settings.wallpaper.settings != null) {
                backgroundOverrideColor = getWallpaperColor(settings.wallpaper.settings.background_color);
                if (settings.wallpaper.settings.second_background_color == 0) {
                    backgroundGradientOverrideColor1 = 0x100000000L;
                } else {
                    backgroundGradientOverrideColor1 = getWallpaperColor(settings.wallpaper.settings.second_background_color);
                }
                if (settings.wallpaper.settings.third_background_color == 0) {
                    backgroundGradientOverrideColor2 = 0x100000000L;
                } else {
                    backgroundGradientOverrideColor2 = getWallpaperColor(settings.wallpaper.settings.third_background_color);
                }
                if (settings.wallpaper.settings.fourth_background_color == 0) {
                    backgroundGradientOverrideColor3 = 0x100000000L;
                } else {
                    backgroundGradientOverrideColor3 = getWallpaperColor(settings.wallpaper.settings.fourth_background_color);
                }
                backgroundRotation = AndroidUtilities.getWallpaperRotation(settings.wallpaper.settings.rotation, false);
                if (!(settings.wallpaper instanceof TLRPC.TL_wallPaperNoFile) && settings.wallpaper.pattern) {
                    patternSlug = settings.wallpaper.slug;
                    patternIntensity = settings.wallpaper.settings.intensity / 100.0f;
                }
            }
            return settings.accent_color == accent.accentColor &&
                    settings.outbox_accent_color == accent.accentColor2 &&
                    bottomColor == accent.myMessagesAccentColor &&
                    myMessagesGradientAccentColor1 == accent.myMessagesGradientAccentColor1 &&
                    myMessagesGradientAccentColor2 == accent.myMessagesGradientAccentColor2 &&
                    myMessagesGradientAccentColor3 == accent.myMessagesGradientAccentColor3 &&
                    settings.message_colors_animated == accent.myMessagesAnimated &&
                    backgroundOverrideColor == accent.backgroundOverrideColor &&
                    backgroundGradientOverrideColor1 == accent.backgroundGradientOverrideColor1 &&
                    backgroundGradientOverrideColor2 == accent.backgroundGradientOverrideColor2 &&
                    backgroundGradientOverrideColor3 == accent.backgroundGradientOverrideColor3 &&
                    backgroundRotation == accent.backgroundRotation &&
                    TextUtils.equals(patternSlug, accent.patternSlug) &&
                    Math.abs(patternIntensity - accent.patternIntensity) < 0.001;
        }

        public static void fillAccentValues(ThemeAccent themeAccent, TLRPC.ThemeSettings settings) {
            themeAccent.accentColor = settings.accent_color;
            themeAccent.accentColor2 = settings.outbox_accent_color;
            themeAccent.myMessagesAccentColor = settings.message_colors.size() > 0 ? settings.message_colors.get(0) | 0xff000000 : 0;
            themeAccent.myMessagesGradientAccentColor1 = settings.message_colors.size() > 1 ? settings.message_colors.get(1) | 0xff000000 : 0;
            if (themeAccent.myMessagesAccentColor == themeAccent.myMessagesGradientAccentColor1) {
                themeAccent.myMessagesGradientAccentColor1 = 0;
            }
            themeAccent.myMessagesGradientAccentColor2 = settings.message_colors.size() > 2 ? settings.message_colors.get(2) | 0xff000000 : 0;
            themeAccent.myMessagesGradientAccentColor3 = settings.message_colors.size() > 3 ? settings.message_colors.get(3) | 0xff000000 : 0;
            themeAccent.myMessagesAnimated = settings.message_colors_animated;
            if (settings.wallpaper != null && settings.wallpaper.settings != null) {
                if (settings.wallpaper.settings.background_color == 0) {
                    themeAccent.backgroundOverrideColor = 0x100000000L;
                } else {
                    themeAccent.backgroundOverrideColor = getWallpaperColor(settings.wallpaper.settings.background_color);
                }
                if ((settings.wallpaper.settings.flags & 16) != 0 && settings.wallpaper.settings.second_background_color == 0) {
                    themeAccent.backgroundGradientOverrideColor1 = 0x100000000L;
                } else {
                    themeAccent.backgroundGradientOverrideColor1 = getWallpaperColor(settings.wallpaper.settings.second_background_color);
                }
                if ((settings.wallpaper.settings.flags & 32) != 0 && settings.wallpaper.settings.third_background_color == 0) {
                    themeAccent.backgroundGradientOverrideColor2 = 0x100000000L;
                } else {
                    themeAccent.backgroundGradientOverrideColor2 = getWallpaperColor(settings.wallpaper.settings.third_background_color);
                }
                if ((settings.wallpaper.settings.flags & 64) != 0 && settings.wallpaper.settings.fourth_background_color == 0) {
                    themeAccent.backgroundGradientOverrideColor3 = 0x100000000L;
                } else {
                    themeAccent.backgroundGradientOverrideColor3 = getWallpaperColor(settings.wallpaper.settings.fourth_background_color);
                }
                themeAccent.backgroundRotation = AndroidUtilities.getWallpaperRotation(settings.wallpaper.settings.rotation, false);
                if (!(settings.wallpaper instanceof TLRPC.TL_wallPaperNoFile) && settings.wallpaper.pattern) {
                    themeAccent.patternSlug = settings.wallpaper.slug;
                    themeAccent.patternIntensity = settings.wallpaper.settings.intensity / 100.0f;
                    themeAccent.patternMotion = settings.wallpaper.settings.motion;
                }
            }
        }

        public ThemeAccent createNewAccent(TLRPC.ThemeSettings settings) {
            ThemeAccent themeAccent = new ThemeAccent();
            fillAccentValues(themeAccent, settings);
            themeAccent.parentTheme = this;
            return themeAccent;
        }

        public ThemeAccent createNewAccent(TLRPC.TL_theme info, int account) {
            return createNewAccent(info, account, false, 0);
        }

        public ThemeAccent createNewAccent(TLRPC.TL_theme info, int account, boolean ignoreThemeInfoId, int settingsIndex) {
            if (info == null) {
                return null;
            }
            TLRPC.ThemeSettings settings = null;
            if (settingsIndex < info.settings.size()) {
                settings = info.settings.get(settingsIndex);
            }
            if (ignoreThemeInfoId) {
                ThemeAccent themeAccent = chatAccentsByThemeId.get(info.id);
                if (themeAccent != null) {
                    return themeAccent;
                }
                int id = ++lastChatThemeId;
                themeAccent = createNewAccent(settings);
                themeAccent.id = id;
                themeAccent.info = info;
                themeAccent.account = account;
                chatAccentsByThemeId.put(id, themeAccent);
                return themeAccent;
            } else {
                ThemeAccent themeAccent = accentsByThemeId.get(info.id);
                if (themeAccent != null) {
                    return themeAccent;
                }
                int id = ++lastAccentId;
                themeAccent = createNewAccent(settings);
                themeAccent.id = id;
                themeAccent.info = info;
                themeAccent.account = account;
                themeAccentsMap.put(id, themeAccent);
                themeAccents.add(0, themeAccent);
                sortAccents(this);
                accentsByThemeId.put(info.id, themeAccent);
                return themeAccent;
            }
        }

        public ThemeAccent getAccent(boolean createNew) {
            if (themeAccents == null) {
                return null;
            }
            ThemeAccent accent = themeAccentsMap.get(currentAccentId);
            if (accent == null) {
                return null;
            }
            if (createNew) {
                int id = ++lastAccentId;
                ThemeAccent themeAccent = new ThemeAccent();
                themeAccent.accentColor = accent.accentColor;
                themeAccent.accentColor2 = accent.accentColor2;
                themeAccent.myMessagesAccentColor = accent.myMessagesAccentColor;
                themeAccent.myMessagesGradientAccentColor1 = accent.myMessagesGradientAccentColor1;
                themeAccent.myMessagesGradientAccentColor2 = accent.myMessagesGradientAccentColor2;
                themeAccent.myMessagesGradientAccentColor3 = accent.myMessagesGradientAccentColor3;
                themeAccent.myMessagesAnimated = accent.myMessagesAnimated;
                themeAccent.backgroundOverrideColor = accent.backgroundOverrideColor;
                themeAccent.backgroundGradientOverrideColor1 = accent.backgroundGradientOverrideColor1;
                themeAccent.backgroundGradientOverrideColor2 = accent.backgroundGradientOverrideColor2;
                themeAccent.backgroundGradientOverrideColor3 = accent.backgroundGradientOverrideColor3;
                themeAccent.backgroundRotation = accent.backgroundRotation;
                themeAccent.patternSlug = accent.patternSlug;
                themeAccent.patternIntensity = accent.patternIntensity;
                themeAccent.patternMotion = accent.patternMotion;
                themeAccent.parentTheme = this;
                if (overrideWallpaper != null) {
                    themeAccent.overrideWallpaper = new OverrideWallpaperInfo(overrideWallpaper, this, themeAccent);
                }
                prevAccentId = currentAccentId;
                currentAccentId = themeAccent.id = id;
                overrideWallpaper = themeAccent.overrideWallpaper;
                themeAccentsMap.put(id, themeAccent);
                themeAccents.add(0, themeAccent);
                sortAccents(this);
                return themeAccent;
            } else {
                return accent;
            }
        }

        public int getAccentColor(int id) {
            ThemeAccent accent = themeAccentsMap.get(id);
            return accent != null ? accent.accentColor : 0;
        }

        public boolean createBackground(File file, String toPath) {
            try {
                Bitmap bitmap = AndroidUtilities.getScaledBitmap(dp(640), dp(360), file.getAbsolutePath(), null, 0);
                if (bitmap != null && patternBgColor != 0) {
                    Bitmap finalBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
                    Canvas canvas = new Canvas(finalBitmap);
                    int patternColor;
                    if (patternBgGradientColor2 != 0) {
                        patternColor = MotionBackgroundDrawable.getPatternColor(patternBgColor, patternBgGradientColor1, patternBgGradientColor2, patternBgGradientColor3);
                    } else if (patternBgGradientColor1 != 0) {
                        patternColor = AndroidUtilities.getAverageColor(patternBgColor, patternBgGradientColor1);
                        GradientDrawable gradientDrawable = new GradientDrawable(BackgroundGradientDrawable.getGradientOrientation(patternBgGradientRotation), new int[]{patternBgColor, patternBgGradientColor1});
                        gradientDrawable.setBounds(0, 0, finalBitmap.getWidth(), finalBitmap.getHeight());
                        gradientDrawable.draw(canvas);
                    } else {
                        patternColor = AndroidUtilities.getPatternColor(patternBgColor);
                        canvas.drawColor(patternBgColor);
                    }
                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    paint.setColorFilter(new PorterDuffColorFilter(patternColor, PorterDuff.Mode.SRC_IN));
                    paint.setAlpha((int) (patternIntensity / 100.0f * 255));
                    canvas.drawBitmap(bitmap, 0, 0, paint);
                    bitmap = finalBitmap;
                    canvas.setBitmap(null);
                }
                if (isBlured) {
                    bitmap = Utilities.blurWallpaper(bitmap);
                }
                FileOutputStream stream = new FileOutputStream(toPath);
                bitmap.compress(patternBgGradientColor2 != 0 ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 87, stream);
                stream.close();
                return true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return false;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
                String location = (String) args[0];
                if (info != null && info.document != null) {
                    if (location.equals(loadingThemeWallpaperName)) {
                        loadingThemeWallpaperName = null;
                        File file = (File) args[1];
                        Utilities.globalQueue.postRunnable(() -> {
                            createBackground(file, newPathToWallpaper);
                            AndroidUtilities.runOnUIThread(this::onFinishLoadingRemoteTheme);
                        });
                    } else {
                        String name = FileLoader.getAttachFileName(info.document);
                        if (location.equals(name)) {
                            removeObservers();
                            if (id == NotificationCenter.fileLoaded) {
                                File locFile = new File(pathToFile);
                                ThemeInfo themeInfo = fillThemeValues(locFile, info.title, info);
                                if (themeInfo != null && themeInfo.pathToWallpaper != null) {
                                    File file = new File(themeInfo.pathToWallpaper);
                                    if (!file.exists()) {
                                        patternBgColor = themeInfo.patternBgColor;
                                        patternBgGradientColor1 = themeInfo.patternBgGradientColor1;
                                        patternBgGradientColor2 = themeInfo.patternBgGradientColor2;
                                        patternBgGradientColor3 = themeInfo.patternBgGradientColor3;
                                        patternBgGradientRotation = themeInfo.patternBgGradientRotation;
                                        isBlured = themeInfo.isBlured;
                                        patternIntensity = themeInfo.patternIntensity;
                                        newPathToWallpaper = themeInfo.pathToWallpaper;

                                        TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
                                        TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                                        inputWallPaperSlug.slug = themeInfo.slug;
                                        req.wallpaper = inputWallPaperSlug;
                                        ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                            if (response instanceof TLRPC.TL_wallPaper) {
                                                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) response;
                                                loadingThemeWallpaperName = FileLoader.getAttachFileName(wallPaper.document);
                                                addObservers();
                                                FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                                            } else {
                                                onFinishLoadingRemoteTheme();
                                            }
                                        }));
                                        return;
                                    }
                                }
                                onFinishLoadingRemoteTheme();
                            }
                        }
                    }
                }
            }
        }
    }

    public interface ResourcesProvider {

        int getColor(int key);

        default int getColorOrDefault(int key) {
            return getColor(key);
        }

        default int getCurrentColor(int key) {
            return getColor(key);
        }

        default void setAnimatedColor(int key, int color) {}

        default Drawable getDrawable(String drawableKey) {
            return null;
        }

        default Paint getPaint(String paintKey) {
            return Theme.getThemePaint(paintKey);
        }

        default boolean hasGradientService() {
            return false;
        }

        default boolean isDark() {
            // used only in PeerColorActivity
            // support in other implementations to use
            return Theme.isCurrentThemeDark();
        }

        default void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
            Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
        }

        default ColorFilter getAnimatedEmojiColorFilter() {
            return Theme.chat_animatedEmojiTextColorFilter;
        }
    }

    private static final Object sync = new Object();
    public static Runnable wallpaperLoadTask;

    public static final int ACTION_BAR_PHOTO_VIEWER_COLOR = 0x7f000000;
    public static final int ACTION_BAR_MEDIA_PICKER_COLOR = 0xff333333;
    public static final int ACTION_BAR_VIDEO_EDIT_COLOR = 0xff000000;
    public static final int ACTION_BAR_PLAYER_COLOR = 0xffffffff;
    public static final int ACTION_BAR_PICKER_SELECTOR_COLOR = 0xff3d3d3d;
    public static final int ACTION_BAR_WHITE_SELECTOR_COLOR = 0x40ffffff;
    public static final int ACTION_BAR_AUDIO_SELECTOR_COLOR = 0x2f000000;
    public static final int ARTICLE_VIEWER_MEDIA_PROGRESS_COLOR = 0xffffffff;

    public static final int AUTO_NIGHT_TYPE_NONE = 0;
    public static final int AUTO_NIGHT_TYPE_SCHEDULED = 1;
    public static final int AUTO_NIGHT_TYPE_AUTOMATIC = 2;
    public static final int AUTO_NIGHT_TYPE_SYSTEM = 3;

    private static final int LIGHT_SENSOR_THEME_SWITCH_DELAY = 1800;
    private static final int LIGHT_SENSOR_THEME_SWITCH_NEAR_DELAY = 12000;
    private static final int LIGHT_SENSOR_THEME_SWITCH_NEAR_THRESHOLD = 12000;
    private static SensorManager sensorManager;
    private static Sensor lightSensor;
    private static boolean lightSensorRegistered;
    private static float lastBrightnessValue = 1.0f;
    private static long lastThemeSwitchTime;
    private static boolean switchDayRunnableScheduled;
    private static boolean switchNightRunnableScheduled;
    private static Runnable switchDayBrightnessRunnable = new Runnable() {
        @Override
        public void run() {
            switchDayRunnableScheduled = false;
            applyDayNightThemeMaybe(false);
        }
    };
    private static Runnable switchNightBrightnessRunnable = new Runnable() {
        @Override
        public void run() {
            switchNightRunnableScheduled = false;
            applyDayNightThemeMaybe(true);
        }
    };

    public static int DEFALT_THEME_ACCENT_ID = 99;
    public static int selectedAutoNightType = AUTO_NIGHT_TYPE_NONE;
    public static boolean autoNightScheduleByLocation;
    public static float autoNightBrighnessThreshold = 0.25f;
    public static int autoNightDayStartTime = 22 * 60;
    public static int autoNightDayEndTime = 8 * 60;
    public static int autoNightSunsetTime = 22 * 60;
    public static int autoNightLastSunCheckDay = -1;
    public static int autoNightSunriseTime = 8 * 60;
    public static String autoNightCityName = "";
    public static double autoNightLocationLatitude = 10000;
    public static double autoNightLocationLongitude = 10000;

    private static Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static int loadingCurrentTheme;
    private static int lastLoadingCurrentThemeTime;
    private static boolean[] loadingRemoteThemes = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static int[] lastLoadingThemesTime = new int[UserConfig.MAX_ACCOUNT_COUNT];
    private static long[] remoteThemesHash = new long[UserConfig.MAX_ACCOUNT_COUNT];

    public static ArrayList<ThemeInfo> themes;
    private static ArrayList<ThemeInfo> otherThemes;
    private static HashMap<String, ThemeInfo> themesDict;
    private static ThemeInfo currentTheme;
    private static ThemeInfo currentNightTheme;
    private static ThemeInfo currentDayTheme;
    private static ThemeInfo defaultTheme;
    private static ThemeInfo previousTheme;
    private static boolean changingWallpaper;
    private static boolean hasPreviousTheme;
    private static boolean isApplyingAccent;
    private static boolean switchingNightTheme;
    private static boolean isInNigthMode;
    private static int previousPhase;

    private static int switchNightThemeDelay;
    private static long lastDelayUpdateTime;

    private static BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private static boolean isCustomTheme;
    private static int serviceMessageColor;
    private static Bitmap serviceBitmap;
    public static BitmapShader serviceBitmapShader;
    private static Matrix serviceBitmapMatrix;
    private static int serviceSelectedMessageColor;
    public static int serviceMessageColorBackup;
    public static int serviceSelectedMessageColorBackup;
    public static int currentColor;
    private static Drawable wallpaper;
    private static Drawable themedWallpaper;
    private static int themedWallpaperFileOffset;
    private static String themedWallpaperLink;
    private static boolean isWallpaperMotion;
    private static int patternIntensity;
    private static boolean isPatternWallpaper;

    public static Paint dividerPaint;
    public static Paint dividerExtraPaint;
    public static Paint linkSelectionPaint;
    public static Paint checkboxSquare_eraserPaint;
    public static Paint checkboxSquare_checkPaint;
    public static Paint checkboxSquare_backgroundPaint;
    public static Paint avatar_backgroundPaint;

    public static Drawable listSelector;
    public static Drawable[] avatarDrawables = new Drawable[24];

    public static Drawable moveUpDrawable;

    public static Paint dialogs_onlineCirclePaint;
    public static Paint dialogs_tabletSeletedPaint;
    public static Paint dialogs_pinnedPaint;
    public static Paint dialogs_countPaint;
    public static Paint dialogs_errorPaint;
    public static Paint dialogs_countGrayPaint;
    public static Paint dialogs_actionMessagePaint;
    public static Paint dialogs_reactionsCountPaint;
    public static Paint dialogs_tagPaint;
    public static TextPaint[] dialogs_namePaint;
    public static TextPaint[] dialogs_nameEncryptedPaint;
    public static TextPaint dialogs_searchNamePaint;
    public static TextPaint dialogs_searchNameEncryptedPaint;
    public static TextPaint[] dialogs_messagePaint;
    public static TextPaint dialogs_messageNamePaint;
    public static TextPaint[] dialogs_messagePrintingPaint;
    public static TextPaint dialogs_timePaint;
    public static TextPaint dialogs_countTextPaint;
    public static TextPaint dialogs_archiveTextPaint;
    public static TextPaint dialogs_archiveTextPaintSmall;
    public static TextPaint dialogs_onlinePaint;
    public static TextPaint dialogs_offlinePaint;
    public static TextPaint dialogs_tagTextPaint;
    public static Drawable dialogs_checkDrawable;
    public static Drawable dialogs_playDrawable;
    public static Drawable dialogs_checkReadDrawable;
    public static Drawable dialogs_halfCheckDrawable;
    public static Drawable dialogs_clockDrawable;
    public static Drawable dialogs_errorDrawable;
    public static Drawable dialogs_reorderDrawable;
    public static Drawable dialogs_lockDrawable;
    public static Drawable dialogs_lock2Drawable;
    public static Drawable dialogs_muteDrawable;
    public static Drawable dialogs_unmuteDrawable;
    public static Drawable dialogs_verifiedDrawable;
    public static ScamDrawable dialogs_scamDrawable;
    public static ScamDrawable dialogs_fakeDrawable;
    public static Drawable dialogs_verifiedCheckDrawable;
    public static Drawable dialogs_pinnedDrawable;
    public static Drawable dialogs_mentionDrawable;
    public static Drawable dialogs_reactionsMentionDrawable;
    public static Drawable dialogs_holidayDrawable;
    public static Drawable dialogs_forum_arrowDrawable;
    public static RLottieDrawable dialogs_archiveAvatarDrawable;
    public static RLottieDrawable dialogs_archiveDrawable;
    public static RLottieDrawable dialogs_unarchiveDrawable;
    public static RLottieDrawable dialogs_pinArchiveDrawable;
    public static RLottieDrawable dialogs_unpinArchiveDrawable;
    public static RLottieDrawable dialogs_hidePsaDrawable;
    public static boolean dialogs_archiveDrawableRecolored;
    public static boolean dialogs_hidePsaDrawableRecolored;
    public static boolean dialogs_archiveAvatarDrawableRecolored;
    private static int dialogs_holidayDrawableOffsetX;
    private static int dialogs_holidayDrawableOffsetY;
    private static long lastHolidayCheckTime;
    private static boolean canStartHolidayAnimation;

    public static RLottieDrawable dialogs_swipeMuteDrawable;
    public static RLottieDrawable dialogs_swipeUnmuteDrawable;
    public static RLottieDrawable dialogs_swipeDeleteDrawable;
    public static RLottieDrawable dialogs_swipeReadDrawable;
    public static RLottieDrawable dialogs_swipeUnreadDrawable;
    public static RLottieDrawable dialogs_swipePinDrawable;
    public static RLottieDrawable dialogs_swipeUnpinDrawable;

    public static TextPaint profile_aboutTextPaint;
    public static Drawable profile_verifiedDrawable;
    public static Drawable profile_verifiedCheckDrawable;

    public static Paint chat_docBackPaint;
    public static Paint chat_deleteProgressPaint;
    public static Paint chat_urlPaint;
    public static Paint chat_outUrlPaint;
    public static Paint chat_textSearchSelectionPaint;
    public static Paint chat_instantViewRectPaint;
    public static Paint chat_instantViewButtonPaint;
    public static Paint chat_pollTimerPaint;
    public static Paint chat_replyLinePaint;
    public static Paint chat_msgErrorPaint;
    public static Paint chat_statusPaint;
    public static Paint chat_statusRecordPaint;
    public static Paint chat_messageBackgroundSelectedPaint;
    public static Paint chat_actionBackgroundPaint;
    public static Paint chat_actionBackgroundSelectedPaint;
    public static Paint chat_actionBackgroundGradientDarkenPaint;
    public static Paint chat_timeBackgroundPaint;
    public static Paint chat_composeBackgroundPaint;
    public static Paint chat_radialProgressPaint;
    public static Paint chat_radialProgress2Paint;
    public static Paint chat_radialProgressPausedPaint;
    public static Paint chat_radialProgressPausedSeekbarPaint;

    public static TextPaint chat_msgTextPaint;
    public static TextPaint chat_msgTextCodePaint;
    public static TextPaint chat_msgTextCode2Paint;
    public static TextPaint chat_msgTextCode3Paint;
    public static TextPaint chat_actionTextPaint;
    public static TextPaint chat_actionTextPaint2;
    public static TextPaint chat_unlockExtendedMediaTextPaint;
    public static TextPaint chat_msgBotButtonPaint;
    public static TextPaint chat_msgGameTextPaint;
    public static TextPaint[] chat_msgTextPaintEmoji;
    public static TextPaint chat_msgTextPaintOneEmoji;
    public static TextPaint chat_msgTextPaintTwoEmoji;
    public static TextPaint chat_msgTextPaintThreeEmoji;
    public static TextPaint chat_infoPaint;
    public static TextPaint chat_infoBoldPaint;
    public static TextPaint chat_stickerCommentCountPaint;
    public static TextPaint chat_livePaint;
    public static TextPaint chat_docNamePaint;
    public static TextPaint chat_locationTitlePaint;
    public static TextPaint chat_locationAddressPaint;
    public static TextPaint chat_durationPaint;
    public static TextPaint chat_gamePaint;
    public static TextPaint chat_shipmentPaint;
    public static TextPaint chat_instantViewPaint;
    public static TextPaint chat_audioTimePaint;
    public static TextPaint chat_audioTitlePaint;
    public static TextPaint chat_audioPerformerPaint;
    public static TextPaint chat_botButtonPaint;
    public static TextPaint chat_contactNamePaint;
    public static TextPaint chat_contactPhonePaint;
    public static TextPaint chat_timePaint;
    public static TextPaint chat_adminPaint;
    public static TextPaint chat_namePaint;
    public static TextPaint chat_forwardNamePaint;
    public static TextPaint chat_replyNamePaint;
    public static TextPaint chat_replyTextPaint;
    public static TextPaint chat_quoteTextPaint;
    public static TextPaint chat_titleLabelTextPaint;
    public static TextPaint chat_topicTextPaint;
    public static TextPaint chat_commentTextPaint;
    public static TextPaint chat_contextResult_titleTextPaint;
    public static TextPaint chat_contextResult_descriptionTextPaint;
    public static TextPaint chat_msgCodeBgPaint;

    public static Drawable chat_msgNoSoundDrawable;
    public static Drawable chat_composeShadowDrawable;
    public static Drawable chat_composeShadowRoundDrawable;
    public static Drawable chat_roundVideoShadow;
    public static MessageDrawable chat_msgInDrawable;
    public static MessageDrawable chat_msgInSelectedDrawable;
    public static MessageDrawable chat_msgOutDrawable;
    public static MessageDrawable chat_msgOutSelectedDrawable;
    public static MessageDrawable chat_msgInMediaDrawable;
    public static MessageDrawable chat_msgInMediaSelectedDrawable;
    public static MessageDrawable chat_msgOutMediaDrawable;
    public static MessageDrawable chat_msgOutMediaSelectedDrawable;
    private static StatusDrawable[] chat_status_drawables = new StatusDrawable[6];
    public static PorterDuffColorFilter chat_animatedEmojiTextColorFilter;
    public static PorterDuffColorFilter chat_outAnimatedEmojiTextColorFilter;

    public static PathAnimator playPauseAnimator;
    public static Drawable chat_msgOutCheckDrawable;
    public static Drawable chat_msgOutCheckSelectedDrawable;
    public static Drawable chat_msgOutCheckReadDrawable;
    public static Drawable chat_msgOutCheckReadSelectedDrawable;
    public static Drawable chat_msgOutHalfCheckDrawable;
    public static Drawable chat_msgOutHalfCheckSelectedDrawable;
    public static MsgClockDrawable chat_msgClockDrawable;
    public static Drawable chat_msgMediaCheckDrawable;
    public static Drawable chat_msgMediaHalfCheckDrawable;
    public static Drawable chat_msgStickerCheckDrawable;
    public static Drawable chat_msgStickerHalfCheckDrawable;
    public static Drawable chat_msgStickerViewsDrawable;
    public static Drawable chat_msgStickerRepliesDrawable;
    public static Drawable chat_msgUnlockDrawable;
    public static Drawable chat_msgInViewsDrawable;
    public static Drawable chat_msgInViewsSelectedDrawable;
    public static Drawable chat_msgOutViewsDrawable;
    public static Drawable chat_msgOutViewsSelectedDrawable;
    public static Drawable chat_msgInRepliesDrawable;
    public static Drawable chat_msgInRepliesSelectedDrawable;
    public static Drawable chat_msgOutRepliesDrawable;
    public static Drawable chat_msgOutRepliesSelectedDrawable;
    public static Drawable chat_msgInPinnedDrawable;
    public static Drawable chat_msgInPinnedSelectedDrawable;
    public static Drawable chat_msgOutPinnedDrawable;
    public static Drawable chat_msgOutPinnedSelectedDrawable;
    public static Drawable chat_msgStickerPinnedDrawable;
    public static Drawable chat_msgMediaPinnedDrawable;
    public static Drawable chat_msgMediaViewsDrawable;
    public static Drawable chat_msgMediaRepliesDrawable;
    public static Drawable chat_msgInMenuDrawable;
    public static Drawable chat_msgInMenuSelectedDrawable;
    public static Drawable chat_msgOutMenuDrawable;
    public static Drawable chat_msgOutMenuSelectedDrawable;
    public static Drawable chat_msgMediaMenuDrawable;
    public static Drawable chat_msgInInstantDrawable;
    public static Drawable chat_msgOutInstantDrawable;
    public static Drawable chat_msgErrorDrawable;
    public static Drawable chat_muteIconDrawable;
    public static Drawable chat_lockIconDrawable;
    public static Drawable chat_inlineResultFile;
    public static Drawable chat_inlineResultAudio;
    public static Drawable chat_inlineResultLocation;
    public static Drawable chat_msgOutLocationDrawable;
    public static Drawable chat_contextResult_shadowUnderSwitchDrawable;
    public static Drawable chat_shareIconDrawable;
    public static Drawable chat_replyIconDrawable;
    public static Drawable chat_closeIconDrawable;
    public static Drawable chat_moreIconDrawable;
    public static Drawable chat_goIconDrawable;
    public static Drawable chat_botLinkDrawable;
    public static Drawable chat_botCardDrawable;
    public static Drawable chat_botInlineDrawable;
    public static Drawable chat_botWebViewDrawable;
    public static Drawable chat_botInviteDrawable;
    public static Drawable chat_commentDrawable;
    public static Drawable chat_commentStickerDrawable;
    public static Drawable chat_commentArrowDrawable;
    public static Drawable chat_gradientLeftDrawable;
    public static Drawable chat_gradientRightDrawable;
    public static Drawable[] chat_msgInCallDrawable = new Drawable[2];
    public static Drawable[] chat_msgInCallSelectedDrawable = new Drawable[2];
    public static Drawable[] chat_msgOutCallDrawable = new Drawable[2];
    public static Drawable[] chat_msgOutCallSelectedDrawable = new Drawable[2];
    public static Drawable[] chat_pollCheckDrawable = new Drawable[2];
    public static Drawable[] chat_pollCrossDrawable = new Drawable[2];
    public static Drawable[] chat_pollHintDrawable = new Drawable[2];
    public static Drawable[] chat_psaHelpDrawable = new Drawable[2];

    public static Drawable chat_msgCallUpGreenDrawable;
    public static Drawable chat_msgCallDownRedDrawable;
    public static Drawable chat_msgCallDownGreenDrawable;

    public static Drawable chat_attachEmptyDrawable;
    public static RLottieDrawable[] chat_attachButtonDrawables = new RLottieDrawable[6];
    public static Drawable[] chat_locationDrawable = new Drawable[2];
    public static Drawable[] chat_contactDrawable = new Drawable[2];
    public static Drawable[][] chat_fileStatesDrawable = new Drawable[5][2];

    public static Drawable calllog_msgCallUpRedDrawable;
    public static Drawable calllog_msgCallUpGreenDrawable;
    public static Drawable calllog_msgCallDownRedDrawable;
    public static Drawable calllog_msgCallDownGreenDrawable;

    public static Path[] chat_filePath = new Path[2];
    public static Path[] chat_updatePath = new Path[3];
    public static Drawable chat_flameIcon;
    public static Drawable chat_gifIcon;

    private static AudioVisualizerDrawable chat_msgAudioVisualizeDrawable;
    private static HashMap<MessageObject, AudioVisualizerDrawable> animatedOutVisualizerDrawables;

    public static int colorsCount;
    public static final int key_wallpaperFileOffset = colorsCount++;
    public static final int key_dialogBackground = colorsCount++;
    public static final int key_dialogBackgroundGray = colorsCount++;
    public static final int key_dialogTextBlack = colorsCount++;
    public static final int key_dialogTextLink = colorsCount++;
    public static final int key_dialogLinkSelection = colorsCount++;
    public static final int key_dialogTextBlue = colorsCount++;
    public static final int key_dialogTextBlue2 = colorsCount++;
    public static final int key_dialogTextBlue4 = colorsCount++;
    public static final int key_dialogTextGray = colorsCount++;
    public static final int key_dialogTextGray2 = colorsCount++;
    public static final int key_dialogTextGray3 = colorsCount++;
    public static final int key_dialogTextGray4 = colorsCount++;
    public static final int key_dialogTextHint = colorsCount++;
    public static final int key_dialogInputField = colorsCount++;
    public static final int key_dialogInputFieldActivated = colorsCount++;
    public static final int key_dialogCheckboxSquareBackground = colorsCount++;
    public static final int key_dialogCheckboxSquareCheck = colorsCount++;
    public static final int key_dialogCheckboxSquareUnchecked = colorsCount++;
    public static final int key_dialogCheckboxSquareDisabled = colorsCount++;
    public static final int key_dialogScrollGlow = colorsCount++;
    public static final int key_dialogRoundCheckBox = colorsCount++;
    public static final int key_dialogRoundCheckBoxCheck = colorsCount++;
    public static final int key_dialogRadioBackground = colorsCount++;
    public static final int key_dialogRadioBackgroundChecked = colorsCount++;
    public static final int key_dialogLineProgress = colorsCount++;
    public static final int key_dialogLineProgressBackground = colorsCount++;
    public static final int key_dialogButton = colorsCount++;
    public static final int key_dialogButtonSelector = colorsCount++;
    public static final int key_dialogIcon = colorsCount++;
    public static final int key_dialogGrayLine = colorsCount++;
    public static final int key_dialogTopBackground = colorsCount++;
    public static final int key_dialogCameraIcon = colorsCount++;
    public static final int key_dialog_inlineProgressBackground = colorsCount++;
    public static final int key_dialog_inlineProgress = colorsCount++;
    public static final int key_dialogSearchBackground = colorsCount++;
    public static final int key_dialogSearchHint = colorsCount++;
    public static final int key_dialogSearchIcon = colorsCount++;
    public static final int key_dialogSearchText = colorsCount++;
    public static final int key_dialogFloatingButton = colorsCount++;
    public static final int key_dialogFloatingButtonPressed = colorsCount++;
    public static final int key_dialogFloatingIcon = colorsCount++;
    public static final int key_dialogShadowLine = colorsCount++;
    public static final int key_dialogEmptyImage = colorsCount++;
    public static final int key_dialogEmptyText = colorsCount++;
    public static final int key_dialogSwipeRemove = colorsCount++;
    public static final int key_dialogReactionMentionBackground = colorsCount++;
    public static final int key_dialogCardShadow = colorsCount++;
    public static final int key_dialogGiftsBackground = colorsCount++;
    public static final int key_dialogGiftsTabText = colorsCount++;

    public static final int key_windowBackgroundWhite = colorsCount++;
    public static final int key_windowBackgroundUnchecked = colorsCount++;
    public static final int key_windowBackgroundChecked = colorsCount++;
    public static final int key_windowBackgroundCheckText = colorsCount++;
    public static final int key_progressCircle = colorsCount++;
    public static final int key_listSelector = colorsCount++;
    public static final int key_windowBackgroundWhiteInputField = colorsCount++;
    public static final int key_windowBackgroundWhiteInputFieldActivated = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayIcon = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText3 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText4 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText5 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText6 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText7 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueButton = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueIcon = colorsCount++;
    public static final int key_windowBackgroundWhiteGreenText = colorsCount++;
    public static final int key_windowBackgroundWhiteGreenText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText3 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText4 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText5 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText6 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText7 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText8 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlackText = colorsCount++;
    public static final int key_windowBackgroundWhiteHintText = colorsCount++;
    public static final int key_windowBackgroundWhiteValueText = colorsCount++;
    public static final int key_windowBackgroundWhiteLinkText = colorsCount++;
    public static final int key_windowBackgroundWhiteLinkSelection = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueHeader = colorsCount++;
    public static final int key_switchTrack = colorsCount++;
    public static final int key_switchTrackChecked = colorsCount++;
    public static final int key_switchTrackBlue = colorsCount++;
    public static final int key_switchTrackBlueChecked = colorsCount++;
    public static final int key_switchTrackBlueThumb = colorsCount++;
    public static final int key_switchTrackBlueThumbChecked = colorsCount++;
    public static final int key_switchTrackBlueSelector = colorsCount++;
    public static final int key_switchTrackBlueSelectorChecked = colorsCount++;
    public static final int key_switch2Track = colorsCount++;
    public static final int key_switch2TrackChecked = colorsCount++;
    public static final int key_checkboxSquareBackground = colorsCount++;
    public static final int key_checkboxSquareCheck = colorsCount++;
    public static final int key_checkboxSquareUnchecked = colorsCount++;
    public static final int key_checkboxSquareDisabled = colorsCount++;
    public static final int key_windowBackgroundGray = colorsCount++;
    public static final int key_windowBackgroundGrayShadow = colorsCount++;
    public static final int key_emptyListPlaceholder = colorsCount++;
    public static final int key_divider = colorsCount++;
    public static final int key_graySection = colorsCount++;
    public static final int key_graySectionText = colorsCount++;
    public static final int key_radioBackground = colorsCount++;
    public static final int key_radioBackgroundChecked = colorsCount++;
    public static final int key_checkbox = colorsCount++;
    public static final int key_checkboxDisabled = colorsCount++;
    public static final int key_checkboxCheck = colorsCount++;
    public static final int key_fastScrollActive = colorsCount++;
    public static final int key_fastScrollInactive = colorsCount++;
    public static final int key_fastScrollText = colorsCount++;

    public static final int key_text_RedRegular = colorsCount++;
    public static final int key_text_RedBold = colorsCount++;
    public static final int key_fill_RedNormal = colorsCount++;
    public static final int key_fill_RedDark = colorsCount++;

    public static final int key_inappPlayerPerformer = colorsCount++;
    public static final int key_inappPlayerTitle = colorsCount++;
    public static final int key_inappPlayerBackground = colorsCount++;
    public static final int key_inappPlayerPlayPause = colorsCount++;
    public static final int key_inappPlayerClose = colorsCount++;

    public static final int key_returnToCallBackground = colorsCount++;
    public static final int key_returnToCallMutedBackground = colorsCount++;
    public static final int key_returnToCallText = colorsCount++;

    public static final int key_contextProgressInner1 = colorsCount++;
    public static final int key_contextProgressOuter1 = colorsCount++;
    public static final int key_contextProgressInner2 = colorsCount++;
    public static final int key_contextProgressOuter2 = colorsCount++;
    public static final int key_contextProgressInner3 = colorsCount++;
    public static final int key_contextProgressOuter3 = colorsCount++;
    public static final int key_contextProgressInner4 = colorsCount++;
    public static final int key_contextProgressOuter4 = colorsCount++;

    public static final int key_avatar_text = colorsCount++;
    public static final int key_avatar_backgroundSaved = colorsCount++;
    public static final int key_avatar_background2Saved = colorsCount++;
    public static final int key_avatar_backgroundArchived = colorsCount++;
    public static final int key_avatar_backgroundArchivedHidden = colorsCount++;
    public static final int key_avatar_backgroundRed = colorsCount++;
    public static final int key_avatar_backgroundOrange = colorsCount++;
    public static final int key_avatar_backgroundViolet = colorsCount++;
    public static final int key_avatar_backgroundGreen = colorsCount++;
    public static final int key_avatar_backgroundCyan = colorsCount++;
    public static final int key_avatar_backgroundBlue = colorsCount++;
    public static final int key_avatar_backgroundPink = colorsCount++;
    public static final int key_avatar_background2Red = colorsCount++;
    public static final int key_avatar_background2Orange = colorsCount++;
    public static final int key_avatar_background2Violet = colorsCount++;
    public static final int key_avatar_background2Green = colorsCount++;
    public static final int key_avatar_background2Cyan = colorsCount++;
    public static final int key_avatar_background2Blue = colorsCount++;
    public static final int key_avatar_background2Pink = colorsCount++;
    public static final int key_avatar_backgroundGray = colorsCount++;

    public static final int key_avatar_backgroundInProfileBlue = colorsCount++;
    public static final int key_avatar_backgroundActionBarBlue = colorsCount++;
    public static final int key_avatar_actionBarSelectorBlue = colorsCount++;
    public static final int key_avatar_actionBarIconBlue = colorsCount++;
    public static final int key_avatar_subtitleInProfileBlue = colorsCount++;

    public static final int key_avatar_nameInMessageRed = colorsCount++;
    public static final int key_avatar_nameInMessageOrange = colorsCount++;
    public static final int key_avatar_nameInMessageViolet = colorsCount++;
    public static final int key_avatar_nameInMessageGreen = colorsCount++;
    public static final int key_avatar_nameInMessageCyan = colorsCount++;
    public static final int key_avatar_nameInMessageBlue = colorsCount++;
    public static final int key_avatar_nameInMessagePink = colorsCount++;

    public static int[] keys_avatar_background = {key_avatar_backgroundRed, key_avatar_backgroundOrange, key_avatar_backgroundViolet, key_avatar_backgroundGreen, key_avatar_backgroundCyan, key_avatar_backgroundBlue, key_avatar_backgroundPink};
    public static int[] keys_avatar_background2 = {key_avatar_background2Red, key_avatar_background2Orange, key_avatar_background2Violet, key_avatar_background2Green, key_avatar_background2Cyan, key_avatar_background2Blue, key_avatar_background2Pink};
    public static int[] keys_avatar_nameInMessage = {key_avatar_nameInMessageRed, key_avatar_nameInMessageOrange, key_avatar_nameInMessageViolet, key_avatar_nameInMessageGreen, key_avatar_nameInMessageCyan, key_avatar_nameInMessageBlue, key_avatar_nameInMessagePink};

    public static final int key_actionBarDefault = colorsCount++;
    public static final int key_actionBarDefaultSelector = colorsCount++;
    public static final int key_actionBarWhiteSelector = colorsCount++;
    public static final int key_actionBarDefaultIcon = colorsCount++;
    public static final int key_actionBarActionModeDefault = colorsCount++;
    public static final int key_actionBarActionModeDefaultTop = colorsCount++;
    public static final int key_actionBarActionModeDefaultIcon = colorsCount++;
    public static final int key_actionBarActionModeDefaultSelector = colorsCount++;
    public static final int key_actionBarDefaultTitle = colorsCount++;
    public static final int key_actionBarDefaultSubtitle = colorsCount++;
    public static final int key_actionBarDefaultSearch = colorsCount++;
    public static final int key_actionBarDefaultSearchPlaceholder = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItem = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItemIcon = colorsCount++;
    public static final int key_actionBarDefaultSubmenuBackground = colorsCount++;
    public static final int key_actionBarDefaultSubmenuSeparator = colorsCount++;
    public static final int key_actionBarTabActiveText = colorsCount++;
    public static final int key_actionBarTabUnactiveText = colorsCount++;
    public static final int key_actionBarTabLine = colorsCount++;
    public static final int key_actionBarTabSelector = colorsCount++;
    public static final int key_actionBarDefaultArchived = colorsCount++;
    public static final int key_actionBarDefaultArchivedSelector = colorsCount++;
    public static final int key_actionBarDefaultArchivedIcon = colorsCount++;
    public static final int key_actionBarDefaultArchivedTitle = colorsCount++;
    public static final int key_actionBarDefaultArchivedSearch = colorsCount++;
    public static final int key_actionBarDefaultArchivedSearchPlaceholder = colorsCount++;

    public static final int key_actionBarBrowser = colorsCount++;

    public static final int key_chats_onlineCircle = colorsCount++;
    public static final int key_chats_unreadCounter = colorsCount++;
    public static final int key_chats_unreadCounterMuted = colorsCount++;
    public static final int key_chats_unreadCounterText = colorsCount++;
    public static final int key_chats_name = colorsCount++;
    public static final int key_chats_nameArchived = colorsCount++;
    public static final int key_chats_secretName = colorsCount++;
    public static final int key_chats_secretIcon = colorsCount++;
    public static final int key_chats_pinnedIcon = colorsCount++;
    public static final int key_chats_archiveBackground = colorsCount++;
    public static final int key_chats_archivePinBackground = colorsCount++;
    public static final int key_chats_archiveIcon = colorsCount++;
    public static final int key_chats_archiveText = colorsCount++;
    public static final int key_chats_message = colorsCount++;
    public static final int key_chats_messageArchived = colorsCount++;
    public static final int key_chats_message_threeLines = colorsCount++;
    public static final int key_chats_draft = colorsCount++;
    public static final int key_chats_nameMessage = colorsCount++;
    public static final int key_chats_nameMessageArchived = colorsCount++;
    public static final int key_chats_nameMessage_threeLines = colorsCount++;
    public static final int key_chats_nameMessageArchived_threeLines = colorsCount++;
    public static final int key_chats_attachMessage = colorsCount++;
    public static final int key_chats_actionMessage = colorsCount++;
    public static final int key_chats_date = colorsCount++;
    public static final int key_chats_pinnedOverlay = colorsCount++;
    public static final int key_chats_tabletSelectedOverlay = colorsCount++;
    public static final int key_chats_sentCheck = colorsCount++;
    public static final int key_chats_sentReadCheck = colorsCount++;
    public static final int key_chats_sentClock = colorsCount++;
    public static final int key_chats_sentError = colorsCount++;
    public static final int key_chats_sentErrorIcon = colorsCount++;
    public static final int key_chats_verifiedBackground = colorsCount++;
    public static final int key_chats_verifiedCheck = colorsCount++;
    public static final int key_chats_muteIcon = colorsCount++;
    public static final int key_chats_mentionIcon = colorsCount++;
    public static final int key_chats_menuTopShadow = colorsCount++;
    public static final int key_chats_menuTopShadowCats = colorsCount++;
    public static final int key_chats_menuBackground = colorsCount++;
    public static final int key_chats_menuItemText = colorsCount++;
    public static final int key_chats_menuItemCheck = colorsCount++;
    public static final int key_chats_menuItemIcon = colorsCount++;
    public static final int key_chats_menuName = colorsCount++;
    public static final int key_chats_menuPhone = colorsCount++;
    public static final int key_chats_menuPhoneCats = colorsCount++;
    public static final int key_chats_menuTopBackgroundCats = colorsCount++;
    public static final int key_chats_menuTopBackground = colorsCount++;
    public static final int key_chats_actionIcon = colorsCount++;
    public static final int key_chats_actionBackground = colorsCount++;
    public static final int key_chats_actionPressedBackground = colorsCount++;
    public static final int key_chats_archivePullDownBackground = colorsCount++;
    public static final int key_chats_archivePullDownBackgroundActive = colorsCount++;
    public static final int key_chats_tabUnreadActiveBackground = colorsCount++;
    public static final int key_chats_tabUnreadUnactiveBackground = colorsCount++;

    public static final int key_chat_attachCheckBoxCheck = colorsCount++;
    public static final int key_chat_attachCheckBoxBackground = colorsCount++;
    public static final int key_chat_attachPhotoBackground = colorsCount++;
    public static final int key_chat_attachActiveTab = colorsCount++;
    public static final int key_chat_attachUnactiveTab = colorsCount++;
    public static final int key_chat_attachPermissionImage = colorsCount++;
    public static final int key_chat_attachPermissionMark = colorsCount++;
    public static final int key_chat_attachPermissionText = colorsCount++;
    public static final int key_chat_attachEmptyImage = colorsCount++;

    public static final int key_chat_inPollCorrectAnswer = colorsCount++;
    public static final int key_chat_outPollCorrectAnswer = colorsCount++;
    public static final int key_chat_inPollWrongAnswer = colorsCount++;
    public static final int key_chat_outPollWrongAnswer = colorsCount++;

    public static final int key_chat_attachIcon = colorsCount++;
    public static final int key_chat_attachGalleryBackground = colorsCount++;
    public static final int key_chat_attachGalleryText = colorsCount++;
    public static final int key_chat_attachAudioBackground = colorsCount++;
    public static final int key_chat_attachAudioText = colorsCount++;
    public static final int key_chat_attachFileBackground = colorsCount++;
    public static final int key_chat_attachFileText = colorsCount++;
    public static final int key_chat_attachContactBackground = colorsCount++;
    public static final int key_chat_attachContactText = colorsCount++;
    public static final int key_chat_attachLocationBackground = colorsCount++;
    public static final int key_chat_attachLocationText = colorsCount++;
    public static final int key_chat_attachPollBackground = colorsCount++;
    public static final int key_chat_attachPollText = colorsCount++;

    public static final int key_chat_status = colorsCount++;
    public static final int key_chat_inGreenCall = colorsCount++;
    public static final int key_chat_inBubble = colorsCount++;
    public static final int key_chat_inBubbleSelectedOverlay = colorsCount++;
    public static final int key_chat_inBubbleShadow = colorsCount++;

    public static final int key_actionBarActionModeReaction = colorsCount++;
    public static final int key_actionBarActionModeReactionText = colorsCount++;
    public static final int key_actionBarActionModeReactionDot = colorsCount++;

    //my messages bubbles
    public static final int myMessagesBubblesStartIndex = colorsCount;
    public static final int key_chat_outBubble = colorsCount++;
    public static final int key_chat_outBubbleSelected = colorsCount++;
    public static final int key_chat_outBubbleShadow = colorsCount++;
    public static final int key_chat_outBubbleGradient1 = colorsCount++;
    public static final int key_chat_outBubbleGradient2 = colorsCount++;
    public static final int key_chat_outBubbleGradient3 = colorsCount++;
    public static final int myMessagesBubblesEndIndex = colorsCount;

    //my messages
    public static final int myMessagesStartIndex = colorsCount;
    public static final int key_chat_outGreenCall = colorsCount++;
    public static final int key_chat_outSentCheck = colorsCount++;
    public static final int key_chat_outSentCheckSelected = colorsCount++;
    public static final int key_chat_outSentCheckRead = colorsCount++;
    public static final int key_chat_outSentCheckReadSelected = colorsCount++;
    public static final int key_chat_outSentClock = colorsCount++;
    public static final int key_chat_outSentClockSelected = colorsCount++;
    public static final int key_chat_outMediaIcon = colorsCount++;
    public static final int key_chat_outMediaIconSelected = colorsCount++;
    public static final int key_chat_outViews = colorsCount++;
    public static final int key_chat_outViewsSelected = colorsCount++;
    public static final int key_chat_outMenu = colorsCount++;
    public static final int key_chat_outMenuSelected = colorsCount++;
    public static final int key_chat_outInstant = colorsCount++;
    public static final int key_chat_outInstantSelected = colorsCount++;
    public static final int key_chat_outPreviewInstantText = colorsCount++;
    public static final int key_chat_outForwardedNameText = colorsCount++;
    public static final int key_chat_outViaBotNameText = colorsCount++;
    public static final int key_chat_outReplyLine = colorsCount++;
    public static final int key_chat_outReplyLine2 = colorsCount++;
    public static final int key_chat_outReplyNameText = colorsCount++;
    public static final int key_chat_outReplyMessageText = colorsCount++;
    public static final int key_chat_outReplyMediaMessageText = colorsCount++;
    public static final int key_chat_outReplyMediaMessageSelectedText = colorsCount++;
    public static final int key_chat_outPreviewLine = colorsCount++;
    public static final int key_chat_outSiteNameText = colorsCount++;
    public static final int key_chat_outContactNameText = colorsCount++;
    public static final int key_chat_outContactPhoneText = colorsCount++;
    public static final int key_chat_outContactPhoneSelectedText = colorsCount++;
    public static final int key_chat_outAudioPerformerText = colorsCount++;
    public static final int key_chat_outAudioPerformerSelectedText = colorsCount++;
    public static final int key_chat_outTimeSelectedText = colorsCount++;
    public static final int key_chat_outAdminText = colorsCount++;
    public static final int key_chat_outAdminSelectedText = colorsCount++;
    public static final int key_chat_outAudioProgress = colorsCount++;
    public static final int key_chat_outAudioSelectedProgress = colorsCount++;
    public static final int key_chat_outTimeText = colorsCount++;
    public static final int key_chat_outAudioTitleText = colorsCount++;
    public static final int key_chat_outAudioDurationText = colorsCount++;
    public static final int key_chat_outAudioDurationSelectedText = colorsCount++;
    public static final int key_chat_outAudioSeekbar = colorsCount++;
    public static final int key_chat_outAudioCacheSeekbar = colorsCount++;
    public static final int key_chat_outAudioSeekbarSelected = colorsCount++;
    public static final int key_chat_outAudioSeekbarFill = colorsCount++;
    public static final int key_chat_outVoiceSeekbar = colorsCount++;
    public static final int key_chat_outVoiceSeekbarSelected = colorsCount++;
    public static final int key_chat_outVoiceSeekbarFill = colorsCount++;
    public static final int key_chat_outFileProgress = colorsCount++;
    public static final int key_chat_outFileProgressSelected = colorsCount++;
    public static final int key_chat_outFileNameText = colorsCount++;
    public static final int key_chat_outFileInfoText = colorsCount++;
    public static final int key_chat_outFileInfoSelectedText = colorsCount++;
    public static final int key_chat_outFileBackground = colorsCount++;
    public static final int key_chat_outFileBackgroundSelected = colorsCount++;
    public static final int key_chat_outVenueInfoText = colorsCount++;
    public static final int key_chat_outVenueInfoSelectedText = colorsCount++;
    public static final int key_chat_outLinkSelectBackground = colorsCount++;
    public static final int key_chat_outLoader = colorsCount++;
    public static final int key_chat_outLoaderSelected = colorsCount++;
    public static final int key_chat_outLocationIcon = colorsCount++;
    public static final int key_chat_outContactBackground = colorsCount++;
    public static final int key_chat_outContactIcon = colorsCount++;
    public static final int key_chat_outReactionButtonBackground = colorsCount++;
    public static final int myMessagesEndIndex = colorsCount;

    public static final int key_chat_outTextSelectionHighlight = colorsCount++;
    public static final int key_chat_outTextSelectionCursor = colorsCount++;
    public static final int key_chat_outBubbleLocationPlaceholder = colorsCount++;
    public static final int key_chat_outBubbleSelectedOverlay = colorsCount++;
    public static final int key_chat_outPsaNameText = colorsCount++;
    public static final int key_chat_outBubbleGradientAnimated = colorsCount++;
    public static final int key_chat_outBubbleGradientSelectedOverlay = colorsCount++;
    public static final int key_chat_inBubbleSelected = colorsCount++;
    public static final int key_chat_messageTextIn = colorsCount++;
    public static final int key_chat_messageTextOut = colorsCount++;
    public static final int key_chat_messageLinkIn = colorsCount++;
    public static final int key_chat_messageLinkOut = colorsCount++;
    public static final int key_chat_serviceText = colorsCount++;
    public static final int key_chat_serviceLink = colorsCount++;
    public static final int key_chat_serviceIcon = colorsCount++;
    public static final int key_chat_serviceBackground = colorsCount++;
    public static final int key_chat_serviceBackgroundSelected = colorsCount++;
    public static final int key_chat_serviceBackgroundSelector = colorsCount++;
    public static final int key_chat_muteIcon = colorsCount++;
    public static final int key_chat_lockIcon = colorsCount++;
    public static final int key_chat_inSentClock = colorsCount++;
    public static final int key_chat_inSentClockSelected = colorsCount++;
    public static final int key_chat_mediaSentCheck = colorsCount++;
    public static final int key_chat_mediaSentClock = colorsCount++;
    public static final int key_chat_inMediaIcon = colorsCount++;
    public static final int key_chat_inMediaIconSelected = colorsCount++;

    public static final int key_chat_mediaTimeBackground = colorsCount++;
    public static final int key_chat_inViews = colorsCount++;
    public static final int key_chat_inViewsSelected = colorsCount++;
    public static final int key_chat_mediaViews = colorsCount++;
    public static final int key_chat_inMenu = colorsCount++;
    public static final int key_chat_inMenuSelected = colorsCount++;
    public static final int key_chat_mediaMenu = colorsCount++;
    public static final int key_chat_inInstant = colorsCount++;
    public static final int key_chat_inInstantSelected = colorsCount++;
    public static final int key_chat_sentError = colorsCount++;
    public static final int key_chat_sentErrorIcon = colorsCount++;
    public static final int key_chat_selectedBackground = colorsCount++;
    public static final int key_chat_previewDurationText = colorsCount++;
    public static final int key_chat_previewGameText = colorsCount++;
    public static final int key_chat_inPreviewInstantText = colorsCount++;
    public static final int key_chat_secretTimeText = colorsCount++;
    public static final int key_chat_stickerNameText = colorsCount++;
    public static final int key_chat_botButtonText = colorsCount++;
    public static final int key_chat_inForwardedNameText = colorsCount++;
    public static final int key_chat_inPsaNameText = colorsCount++;
    public static final int key_chat_inViaBotNameText = colorsCount++;
    public static final int key_chat_stickerViaBotNameText = colorsCount++;
    public static final int key_chat_inQuote = colorsCount++;
    public static final int key_chat_outQuote = colorsCount++;
    public static final int key_chat_inReplyLine = colorsCount++;
    public static final int key_chat_stickerReplyLine = colorsCount++;
    public static final int key_chat_inReplyNameText = colorsCount++;
    public static final int key_chat_stickerReplyNameText = colorsCount++;
    public static final int key_chat_inReplyMessageText = colorsCount++;
    public static final int key_chat_inReplyMediaMessageText = colorsCount++;
    public static final int key_chat_inReplyMediaMessageSelectedText = colorsCount++;
    public static final int key_chat_stickerReplyMessageText = colorsCount++;
    public static final int key_chat_inPreviewLine = colorsCount++;
    public static final int key_chat_inSiteNameText = colorsCount++;
    public static final int key_chat_inContactNameText = colorsCount++;
    public static final int key_chat_inContactPhoneText = colorsCount++;
    public static final int key_chat_inContactPhoneSelectedText = colorsCount++;
    public static final int key_chat_mediaProgress = colorsCount++;
    public static final int key_chat_inAudioProgress = colorsCount++;
    public static final int key_chat_inAudioSelectedProgress = colorsCount++;
    public static final int key_chat_mediaTimeText = colorsCount++;
    public static final int key_chat_inAdminText = colorsCount++;
    public static final int key_chat_inAdminSelectedText = colorsCount++;
    public static final int key_chat_inTimeText = colorsCount++;
    public static final int key_chat_inTimeSelectedText = colorsCount++;
    public static final int key_chat_inAudioPerformerText = colorsCount++;
    public static final int key_chat_inAudioPerformerSelectedText = colorsCount++;
    public static final int key_chat_inAudioTitleText = colorsCount++;
    public static final int key_chat_inAudioDurationText = colorsCount++;
    public static final int key_chat_inAudioDurationSelectedText = colorsCount++;
    public static final int key_chat_inAudioSeekbar = colorsCount++;
    public static final int key_chat_inAudioCacheSeekbar = colorsCount++;
    public static final int key_chat_inAudioSeekbarSelected = colorsCount++;
    public static final int key_chat_inAudioSeekbarFill = colorsCount++;
    public static final int key_chat_inVoiceSeekbar = colorsCount++;
    public static final int key_chat_inVoiceSeekbarSelected = colorsCount++;
    public static final int key_chat_inVoiceSeekbarFill = colorsCount++;
    public static final int key_chat_inFileProgress = colorsCount++;
    public static final int key_chat_inFileProgressSelected = colorsCount++;
    public static final int key_chat_inFileNameText = colorsCount++;
    public static final int key_chat_inFileInfoText = colorsCount++;
    public static final int key_chat_inFileInfoSelectedText = colorsCount++;
    public static final int key_chat_inFileBackground = colorsCount++;
    public static final int key_chat_inFileBackgroundSelected = colorsCount++;
    public static final int key_chat_inVenueInfoText = colorsCount++;
    public static final int key_chat_inVenueInfoSelectedText = colorsCount++;
    public static final int key_chat_mediaInfoText = colorsCount++;
    public static final int key_chat_linkSelectBackground = colorsCount++;
    public static final int key_chat_textSelectBackground = colorsCount++;
    public static final int key_chat_wallpaper = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to1 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to2 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to3 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_rotation = colorsCount++;
    public static final int key_chat_messagePanelBackground = colorsCount++;
    public static final int key_chat_messagePanelShadow = colorsCount++;
    public static final int key_chat_messagePanelText = colorsCount++;
    public static final int key_chat_messagePanelHint = colorsCount++;
    public static final int key_chat_messagePanelCursor = colorsCount++;
    public static final int key_chat_messagePanelIcons = colorsCount++;
    public static final int key_chat_messagePanelSend = colorsCount++;
    public static final int key_chat_messagePanelVoiceLock = colorsCount++;
    public static final int key_chat_messagePanelVoiceLockBackground = colorsCount++;
    public static final int key_chat_messagePanelVoiceLockShadow = colorsCount++;
    public static final int key_chat_topPanelBackground = colorsCount++;
    public static final int key_chat_topPanelClose = colorsCount++;
    public static final int key_chat_topPanelLine = colorsCount++;
    public static final int key_chat_topPanelTitle = colorsCount++;
    public static final int key_chat_topPanelMessage = colorsCount++;
    public static final int key_chat_addContact = colorsCount++;
    public static final int key_chat_inLoader = colorsCount++;
    public static final int key_chat_inLoaderSelected = colorsCount++;
    public static final int key_chat_inLoaderPhoto = colorsCount++;
    public static final int key_chat_mediaLoaderPhoto = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoSelected = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoIcon = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoIconSelected = colorsCount++;
    public static final int key_chat_inLocationBackground = colorsCount++;
    public static final int key_chat_inLocationIcon = colorsCount++;
    public static final int key_chat_inContactBackground = colorsCount++;
    public static final int key_chat_inContactIcon = colorsCount++;
    public static final int key_chat_replyPanelIcons = colorsCount++;
    public static final int key_chat_replyPanelClose = colorsCount++;
    public static final int key_chat_replyPanelName = colorsCount++;
    public static final int key_chat_replyPanelLine = colorsCount++;
    public static final int key_chat_searchPanelIcons = colorsCount++;
    public static final int key_chat_searchPanelText = colorsCount++;
    public static final int key_chat_secretChatStatusText = colorsCount++;
    public static final int key_chat_fieldOverlayText = colorsCount++;
    public static final int key_chat_stickersHintPanel = colorsCount++;
    public static final int key_chat_botSwitchToInlineText = colorsCount++;
    public static final int key_chat_unreadMessagesStartArrowIcon = colorsCount++;
    public static final int key_chat_unreadMessagesStartText = colorsCount++;
    public static final int key_chat_unreadMessagesStartBackground = colorsCount++;
    public static final int key_chat_inlineResultIcon = colorsCount++;
    public static final int key_chat_emojiPanelBackground = colorsCount++;
    public static final int key_chat_emojiSearchBackground = colorsCount++;
    public static final int key_chat_emojiSearchIcon = colorsCount++;
    public static final int key_chat_emojiPanelShadowLine = colorsCount++;
    public static final int key_chat_emojiPanelEmptyText = colorsCount++;
    public static final int key_chat_emojiPanelIcon = colorsCount++;
    public static final int key_chat_emojiBottomPanelIcon = colorsCount++;
    public static final int key_chat_emojiPanelIconSelected = colorsCount++;
    public static final int key_chat_emojiPanelStickerPackSelector = colorsCount++;
    public static final int key_chat_emojiPanelStickerPackSelectorLine = colorsCount++;
    public static final int key_chat_emojiPanelBackspace = colorsCount++;
    public static final int key_chat_emojiPanelTrendingTitle = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetName = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetNameHighlight = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetNameIcon = colorsCount++;
    public static final int key_chat_emojiPanelTrendingDescription = colorsCount++;
    public static final int key_chat_botKeyboardButtonText = colorsCount++;
    public static final int key_chat_botKeyboardButtonBackground = colorsCount++;
    public static final int key_chat_botKeyboardButtonBackgroundPressed = colorsCount++;
    public static final int key_chat_emojiPanelNewTrending = colorsCount++;
    public static final int key_chat_messagePanelVoicePressed = colorsCount++;
    public static final int key_chat_messagePanelVoiceBackground = colorsCount++;
    public static final int key_chat_messagePanelVoiceDelete = colorsCount++;
    public static final int key_chat_messagePanelVoiceDuration = colorsCount++;
    public static final int key_chat_recordedVoicePlayPause = colorsCount++;
    public static final int key_chat_recordedVoiceProgress = colorsCount++;
    public static final int key_chat_recordedVoiceProgressInner = colorsCount++;
    public static final int key_chat_recordedVoiceDot = colorsCount++;
    public static final int key_chat_recordedVoiceBackground = colorsCount++;
    public static final int key_chat_recordVoiceCancel = colorsCount++;
    public static final int key_chat_recordTime = colorsCount++;
    public static final int key_chat_messagePanelCancelInlineBot = colorsCount++;
    public static final int key_chat_gifSaveHintText = colorsCount++;
    public static final int key_chat_gifSaveHintBackground = colorsCount++;
    public static final int key_chat_goDownButton = colorsCount++;
    public static final int key_chat_goDownButtonIcon = colorsCount++;
    public static final int key_chat_goDownButtonCounter = colorsCount++;
    public static final int key_chat_goDownButtonCounterBackground = colorsCount++;
    public static final int key_chat_inTextSelectionHighlight = colorsCount++;
    public static final int key_chat_TextSelectionCursor = colorsCount++;
    public static final int key_chat_inBubbleLocationPlaceholder = colorsCount++;
    public static final int key_chat_BlurAlpha = colorsCount++;
    public static final int key_chat_BlurAlphaSlow = colorsCount++;
    public static final int key_chat_editMediaButton = colorsCount++;

    public static final int key_voipgroup_listSelector = colorsCount++;
    public static final int key_voipgroup_inviteMembersBackground = colorsCount++;
    public static final int key_voipgroup_actionBar = colorsCount++;
    public static final int key_voipgroup_actionBarItems = colorsCount++;
    public static final int key_voipgroup_actionBarItemsSelector = colorsCount++;
    public static final int key_voipgroup_actionBarUnscrolled = colorsCount++;
    public static final int key_voipgroup_listViewBackgroundUnscrolled = colorsCount++;
    public static final int key_voipgroup_lastSeenTextUnscrolled = colorsCount++;
    public static final int key_voipgroup_mutedIconUnscrolled = colorsCount++;
    public static final int key_voipgroup_nameText = colorsCount++;
    public static final int key_voipgroup_lastSeenText = colorsCount++;
    public static final int key_voipgroup_listeningText = colorsCount++;
    public static final int key_voipgroup_speakingText = colorsCount++;
    public static final int key_voipgroup_mutedIcon = colorsCount++;
    public static final int key_voipgroup_mutedByAdminIcon = colorsCount++;
    public static final int key_voipgroup_listViewBackground = colorsCount++;
    public static final int key_voipgroup_dialogBackground = colorsCount++;
    public static final int key_voipgroup_leaveCallMenu = colorsCount++;
    public static final int key_voipgroup_checkMenu = colorsCount++;
    public static final int key_voipgroup_soundButton = colorsCount++;
    public static final int key_voipgroup_soundButtonActive = colorsCount++;
    public static final int key_voipgroup_soundButtonActiveScrolled = colorsCount++;
    public static final int key_voipgroup_soundButton2 = colorsCount++;
    public static final int key_voipgroup_soundButtonActive2 = colorsCount++;
    public static final int key_voipgroup_soundButtonActive2Scrolled = colorsCount++;
    public static final int key_voipgroup_leaveButton = colorsCount++;
    public static final int key_voipgroup_leaveButtonScrolled = colorsCount++;
    public static final int key_voipgroup_muteButton = colorsCount++;
    public static final int key_voipgroup_muteButton2 = colorsCount++;
    public static final int key_voipgroup_muteButton3 = colorsCount++;
    public static final int key_voipgroup_unmuteButton = colorsCount++;
    public static final int key_voipgroup_unmuteButton2 = colorsCount++;
    public static final int key_voipgroup_disabledButton = colorsCount++;
    public static final int key_voipgroup_rtmpButton = colorsCount++;
    public static final int key_voipgroup_disabledButtonActive = colorsCount++;
    public static final int key_voipgroup_disabledButtonActiveScrolled = colorsCount++;
    public static final int key_voipgroup_connectingProgress = colorsCount++;
    public static final int key_voipgroup_scrollUp = colorsCount++;
    public static final int key_voipgroup_searchPlaceholder = colorsCount++;
    public static final int key_voipgroup_searchBackground = colorsCount++;
    public static final int key_voipgroup_searchText = colorsCount++;
    public static final int key_voipgroup_overlayGreen1 = colorsCount++;
    public static final int key_voipgroup_overlayGreen2 = colorsCount++;
    public static final int key_voipgroup_overlayBlue1 = colorsCount++;
    public static final int key_voipgroup_overlayBlue2 = colorsCount++;
    public static final int key_voipgroup_topPanelGreen1 = colorsCount++;
    public static final int key_voipgroup_topPanelGreen2 = colorsCount++;
    public static final int key_voipgroup_topPanelBlue1 = colorsCount++;
    public static final int key_voipgroup_topPanelBlue2 = colorsCount++;
    public static final int key_voipgroup_topPanelGray = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientMuted = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientMuted2 = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientUnmuted = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientUnmuted2 = colorsCount++;
    public static final int key_voipgroup_overlayAlertMutedByAdmin = colorsCount++;
    public static final int key_voipgroup_overlayAlertMutedByAdmin2 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient2 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient3 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminMuteButton = colorsCount++;
    public static final int key_voipgroup_mutedByAdminMuteButtonDisabled = colorsCount++;
    public static final int key_voipgroup_windowBackgroundWhiteInputField = colorsCount++;
    public static final int key_voipgroup_windowBackgroundWhiteInputFieldActivated = colorsCount++;

    public static final int key_table_background = colorsCount++;
    public static final int key_table_border = colorsCount++;

    public static final int key_passport_authorizeBackground = colorsCount++;
    public static final int key_passport_authorizeBackgroundSelected = colorsCount++;
    public static final int key_passport_authorizeText = colorsCount++;

    public static final int key_profile_creatorIcon = colorsCount++;
    public static final int key_profile_title = colorsCount++;
    public static final int key_profile_actionIcon = colorsCount++;
    public static final int key_profile_actionBackground = colorsCount++;
    public static final int key_profile_actionPressedBackground = colorsCount++;
    public static final int key_profile_verifiedBackground = colorsCount++;
    public static final int key_profile_verifiedCheck = colorsCount++;
    public static final int key_profile_status = colorsCount++;

    public static final int key_profile_tabText = colorsCount++;
    public static final int key_profile_tabSelectedText = colorsCount++;
    public static final int key_profile_tabSelectedLine = colorsCount++;
    public static final int key_profile_tabSelector = colorsCount++;

    public static final int key_sharedMedia_startStopLoadIcon = colorsCount++;
    public static final int key_sharedMedia_linkPlaceholder = colorsCount++;
    public static final int key_sharedMedia_linkPlaceholderText = colorsCount++;
    public static final int key_sharedMedia_photoPlaceholder = colorsCount++;

    public static final int key_featuredStickers_addedIcon = colorsCount++;
    public static final int key_featuredStickers_buttonProgress = colorsCount++;
    public static final int key_featuredStickers_addButton = colorsCount++;
    public static final int key_featuredStickers_addButtonPressed = colorsCount++;
    public static final int key_featuredStickers_removeButtonText = colorsCount++;
    public static final int key_featuredStickers_buttonText = colorsCount++;
    public static final int key_featuredStickers_unread = colorsCount++;

    public static final int key_stickers_menu = colorsCount++;
    public static final int key_stickers_menuSelector = colorsCount++;

    public static final int key_changephoneinfo_image2 = colorsCount++;

    public static final int key_groupcreate_hintText = colorsCount++;
    public static final int key_groupcreate_cursor = colorsCount++;
    public static final int key_groupcreate_sectionShadow = colorsCount++;
    public static final int key_groupcreate_sectionText = colorsCount++;
    public static final int key_groupcreate_spanText = colorsCount++;
    public static final int key_groupcreate_spanBackground = colorsCount++;
    public static final int key_groupcreate_spanDelete = colorsCount++;

    public static final int key_contacts_inviteBackground = colorsCount++;
    public static final int key_contacts_inviteText = colorsCount++;

    public static final int key_login_progressInner = colorsCount++;
    public static final int key_login_progressOuter = colorsCount++;

    public static final int key_picker_enabledButton = colorsCount++;
    public static final int key_picker_disabledButton = colorsCount++;
    public static final int key_picker_badge = colorsCount++;
    public static final int key_picker_badgeText = colorsCount++;

    public static final int key_location_sendLocationBackground = colorsCount++;
    public static final int key_location_sendLocationIcon = colorsCount++;
    public static final int key_location_sendLocationText = colorsCount++;
    public static final int key_location_sendLiveLocationBackground = colorsCount++;
    public static final int key_location_sendLiveLocationIcon = colorsCount++;
    public static final int key_location_sendLiveLocationText = colorsCount++;
    public static final int key_location_liveLocationProgress = colorsCount++;
    public static final int key_location_placeLocationBackground = colorsCount++;
    public static final int key_location_actionIcon = colorsCount++;
    public static final int key_location_actionActiveIcon = colorsCount++;
    public static final int key_location_actionBackground = colorsCount++;
    public static final int key_location_actionPressedBackground = colorsCount++;

    public static final int key_dialog_liveLocationProgress = colorsCount++;

    public static final int key_files_folderIcon = colorsCount++;
    public static final int key_files_folderIconBackground = colorsCount++;
    public static final int key_files_iconText = colorsCount++;

    public static final int key_sessions_devicesImage = colorsCount++;

    public static final int key_calls_callReceivedGreenIcon = colorsCount++;
    public static final int key_calls_callReceivedRedIcon = colorsCount++;

    public static final int key_undo_background = colorsCount++;
    public static final int key_undo_cancelColor = colorsCount++;
    public static final int key_undo_infoColor = colorsCount++;

    public static final int key_sheet_scrollUp = colorsCount++;
    public static final int key_sheet_other = colorsCount++;
    public static final int key_bot_loadingIcon = colorsCount++;
    public static final int key_gift_ribbon = colorsCount++;
    public static final int key_gift_ribbon_soldout = colorsCount++;

    public static final int key_player_actionBarSelector = colorsCount++;
    public static final int key_player_actionBarTitle = colorsCount++;
    public static final int key_player_actionBarSubtitle = colorsCount++;
    public static final int key_player_actionBarItems = colorsCount++;
    public static final int key_player_background = colorsCount++;
    public static final int key_player_time = colorsCount++;
    public static final int key_player_progressBackground = colorsCount++;
    public static final int key_player_progressCachedBackground = colorsCount++;
    public static final int key_player_progress = colorsCount++;
    public static final int key_player_button = colorsCount++;
    public static final int key_player_buttonActive = colorsCount++;

    public static final int key_statisticChartSignature = colorsCount++;
    public static final int key_statisticChartSignatureAlpha = colorsCount++;
    public static final int key_statisticChartHintLine = colorsCount++;
    public static final int key_statisticChartActiveLine = colorsCount++;
    public static final int key_statisticChartInactivePickerChart = colorsCount++;
    public static final int key_statisticChartActivePickerChart = colorsCount++;
    public static final int key_statisticChartRipple = colorsCount++;
    public static final int key_statisticChartBackZoomColor = colorsCount++;
    public static final int key_statisticChartChevronColor = colorsCount++;
    public static final int key_statisticChartLine_blue = colorsCount++;
    public static final int key_statisticChartLine_green = colorsCount++;
    public static final int key_statisticChartLine_red = colorsCount++;
    public static final int key_statisticChartLine_golden = colorsCount++;
    public static final int key_statisticChartLine_lightblue = colorsCount++;
    public static final int key_statisticChartLine_lightgreen = colorsCount++;
    public static final int key_statisticChartLine_orange = colorsCount++;
    public static final int key_statisticChartLine_indigo = colorsCount++;
    public static final int key_statisticChartLine_purple = colorsCount++;
    public static final int key_statisticChartLine_cyan = colorsCount++;
    public static final int key_statisticChartLineEmpty = colorsCount++;

    public static final int key_color_lightblue = colorsCount++;
    public static final int key_color_blue = colorsCount++;
    public static final int key_color_green = colorsCount++;
    public static final int key_color_lightgreen = colorsCount++;
    public static final int key_color_red = colorsCount++;
    public static final int key_color_orange = colorsCount++;
    public static final int key_color_yellow = colorsCount++;
    public static final int key_color_purple = colorsCount++;
    public static final int key_color_cyan = colorsCount++;
    public static final int[] keys_colors = {key_color_lightblue, key_color_blue, key_color_green, key_color_lightgreen, key_color_red, key_color_orange, key_color_yellow, key_color_purple, key_color_cyan};

    public static final int key_chat_inReactionButtonBackground = colorsCount++;
    public static final int key_chat_outReactionButtonText = colorsCount++;
    public static final int key_chat_inReactionButtonText = colorsCount++;
    public static final int key_chat_inReactionButtonTextSelected = colorsCount++;
    public static final int key_chat_outReactionButtonTextSelected = colorsCount++;
    public static final int key_reactionStarSelector = colorsCount++;

    public static final int key_premiumGradient0 = colorsCount++;
    public static final int key_premiumGradient1 = colorsCount++;
    public static final int key_premiumGradient2 = colorsCount++;
    public static final int key_premiumGradient3 = colorsCount++;
    public static final int key_premiumGradient4 = colorsCount++;
    public static final int key_premiumGradientBackground1 = colorsCount++;
    public static final int key_premiumGradientBackground2 = colorsCount++;
    public static final int key_premiumGradientBackground3 = colorsCount++;
    public static final int key_premiumGradientBackground4 = colorsCount++;
    public static final int key_premiumGradientBackgroundOverlay = colorsCount++;
    public static final int key_premiumStartSmallStarsColor = colorsCount++;
    public static final int key_premiumStarGradient1 = colorsCount++;
    public static final int key_premiumStarGradient2 = colorsCount++;
    public static final int key_premiumCoinGradient1 = colorsCount++;
    public static final int key_premiumCoinGradient2 = colorsCount++;
    public static final int key_premiumStartSmallStarsColor2 = colorsCount++;
    public static final int key_premiumGradientBottomSheet1 = colorsCount++;
    public static final int key_premiumGradientBottomSheet2 = colorsCount++;
    public static final int key_premiumGradientBottomSheet3 = colorsCount++;
    public static final int key_topics_unreadCounter = colorsCount++;
    public static final int key_topics_unreadCounterMuted = colorsCount++;
    public static final int key_starsGradient1 = colorsCount++;
    public static final int key_starsGradient2 = colorsCount++;

    public static final int key_stories_circle1 = colorsCount++;
    public static final int key_stories_circle2 = colorsCount++;
    public static final int key_stories_circle_dialog1 = colorsCount++;
    public static final int key_stories_circle_dialog2 = colorsCount++;
    public static final int key_stories_circle_closeFriends1 = colorsCount++;
    public static final int key_stories_circle_closeFriends2 = colorsCount++;

    public static final int key_chat_inCodeBackground = colorsCount++;
    public static final int key_chat_outCodeBackground = colorsCount++;
    public static final int key_code_keyword = colorsCount++;
    public static final int key_code_operator = colorsCount++;
    public static final int key_code_constant = colorsCount++;
    public static final int key_code_string = colorsCount++;
    public static final int key_code_number = colorsCount++;
    public static final int key_code_comment = colorsCount++;
    public static final int key_code_function = colorsCount++;

    public static final int key_iv_background = colorsCount++;
    public static final int key_iv_backgroundGray = colorsCount++;
    public static final int key_iv_ab_progress = colorsCount++;
    public static final int key_iv_navigationBackground = colorsCount++;

    public static final String key_drawable_botInline = "drawableBotInline";
    public static final String key_drawable_botLink = "drawableBotLink";
    public static final String key_drawable_botWebView = "drawableBotWebView";
    public static final String key_drawable_botInvite = "drawable_botInvite";
    public static final String key_drawable_commentSticker = "drawableCommentSticker";
    public static final String key_drawable_goIcon = "drawableGoIcon";
    public static final String key_drawable_msgError = "drawableMsgError";
    public static final String key_drawable_msgIn = "drawableMsgIn";
    public static final String key_drawable_msgInInstant = "drawableMsgInInstant";
    public static final String key_drawable_msgInClock = "drawableMsgInClock";
    public static final String key_drawable_msgInClockSelected = "drawableMsgInClockSelected";
    public static final String key_drawable_msgInSelected = "drawableMsgInSelected";
    public static final String key_drawable_msgInMedia = "drawableMsgInMedia";
    public static final String key_drawable_msgInMediaSelected = "drawableMsgInMediaSelected";
    public static final String key_drawable_msgOut = "drawableMsgOut";
    public static final String key_drawable_msgOutSelected = "drawableMsgOutSelected";
    public static final String key_drawable_msgOutCallAudio = "drawableMsgOutCallAudio";
    public static final String key_drawable_msgOutCallAudioSelected = "drawableMsgOutCallAudioSelected";
    public static final String key_drawable_msgOutCallVideo = "drawableMsgOutCallVideo";
    public static final String key_drawable_msgOutCallVideoSelected = "drawableMsgOutCallVideo";
    public static final String key_drawable_msgOutCheck = "drawableMsgOutCheck";
    public static final String key_drawable_msgOutCheckSelected = "drawableMsgOutCheckSelected";
    public static final String key_drawable_msgOutCheckRead = "drawableMsgOutCheckRead";
    public static final String key_drawable_msgOutCheckReadSelected = "drawableMsgOutCheckReadSelected";
    public static final String key_drawable_msgOutHalfCheck = "drawableMsgOutHalfCheck";
    public static final String key_drawable_msgOutHalfCheckSelected = "drawableMsgOutHalfCheckSelected";
    public static final String key_drawable_msgOutInstant = "drawableMsgOutInstant";
    public static final String key_drawable_msgOutMedia = "drawableMsgOutMedia";
    public static final String key_drawable_msgOutMediaSelected = "drawableMsgOutMediaSelected";
    public static final String key_drawable_msgOutMenu = "drawableMsgOutMenu";
    public static final String key_drawable_msgOutMenuSelected = "drawableMsgOutMenuSelected";
    public static final String key_drawable_msgOutPinned = "drawableMsgOutPinned";
    public static final String key_drawable_msgOutPinnedSelected = "drawableMsgOutPinnedSelected";
    public static final String key_drawable_msgOutReplies = "drawableMsgOutReplies";
    public static final String key_drawable_msgOutRepliesSelected = "drawableMsgOutReplies";
    public static final String key_drawable_msgOutViews = "drawableMsgOutViews";
    public static final String key_drawable_msgOutViewsSelected = "drawableMsgOutViewsSelected";
    public static final String key_drawable_msgStickerCheck = "drawableMsgStickerCheck";
    public static final String key_drawable_msgStickerClock = "drawableMsgStickerClock";
    public static final String key_drawable_msgStickerHalfCheck = "drawableMsgStickerHalfCheck";
    public static final String key_drawable_msgStickerPinned = "drawableMsgStickerPinned";
    public static final String key_drawable_msgStickerReplies = "drawableMsgStickerReplies";
    public static final String key_drawable_msgStickerViews = "drawableMsgStickerViews";
    public static final String key_drawable_replyIcon = "drawableReplyIcon";
    public static final String key_drawable_shareIcon = "drawableShareIcon";
    public static final String key_drawable_closeIcon = "drawableCloseIcon";
    public static final String key_drawable_moreIcon = "drawableMoreIcon";
    public static final String key_drawable_muteIconDrawable = "drawableMuteIcon";
    public static final String key_drawable_lockIconDrawable = "drawableLockIcon";
    public static final String key_drawable_chat_pollHintDrawableOut = "drawable_chat_pollHintDrawableOut";
    public static final String key_drawable_chat_pollHintDrawableIn = "drawable_chat_pollHintDrawableIn";

    private static final HashMap<String, Drawable> defaultChatDrawables = new HashMap<>();
    private static final HashMap<String, Integer> defaultChatDrawableColorKeys = new HashMap<>();

    public static final String key_paint_chatActionBackground = "paintChatActionBackground";
    public static final String key_paint_chatActionBackgroundDarken = "paintChatActionBackgroundDarken";
    public static final String key_paint_chatActionBackgroundSelected = "paintChatActionBackgroundSelected";
    public static final String key_paint_chatMessageBackgroundSelected = "paintChatMessageBackgroundSelected";
    public static final String key_paint_chatActionText = "paintChatActionText";
    public static final String key_paint_chatActionText2 = "paintChatActionText2";
    public static final String key_paint_chatBotButton = "paintChatBotButton";
    public static final String key_paint_chatComposeBackground = "paintChatComposeBackground";
    public static final String key_paint_divider = "paintDivider";
    public static final String key_paint_chatTimeBackground = "paintChatTimeBackground";
    private static final HashMap<String, Paint> defaultChatPaints = new HashMap<>();
    private static final HashMap<String, Integer> defaultChatPaintColors = new HashMap<>();

    private static int[] defaultColors;
    private static SparseIntArray fallbackKeys = new SparseIntArray();
    private static HashSet<Integer> themeAccentExclusionKeys = new HashSet<>();
    private static SparseIntArray currentColorsNoAccent;
    private static SparseIntArray currentColors;
    private static SparseIntArray animatingColors;
    private static boolean shouldDrawGradientIcons;

    private static final ThreadLocal<float[]> hsvTemp1Local = new ThreadLocal<>();
    private static final ThreadLocal<float[]> hsvTemp2Local = new ThreadLocal<>();
    private static final ThreadLocal<float[]> hsvTemp3Local = new ThreadLocal<>();
    private static final ThreadLocal<float[]> hsvTemp4Local = new ThreadLocal<>();
    private static final ThreadLocal<float[]> hsvTemp5Local = new ThreadLocal<>();

    private static FragmentContextViewWavesDrawable fragmentContextViewWavesDrawable;
    private static RoundVideoProgressShadow roundPlayDrawable;

    static {
        defaultColors = ThemeColors.createDefaultColors();

        fallbackKeys.put(key_iv_background, key_windowBackgroundWhite);
        fallbackKeys.put(key_iv_backgroundGray, key_windowBackgroundGray);
        fallbackKeys.put(key_iv_navigationBackground, key_windowBackgroundGray);
        fallbackKeys.put(key_bot_loadingIcon, key_groupcreate_spanBackground);
        fallbackKeys.put(key_gift_ribbon_soldout, key_text_RedBold);
        fallbackKeys.put(key_iv_ab_progress, key_featuredStickers_addButton);
        fallbackKeys.put(key_dialogGiftsBackground, key_windowBackgroundGray);
        fallbackKeys.put(key_dialogGiftsTabText, key_windowBackgroundWhiteGrayText2);
        fallbackKeys.put(key_chat_inQuote, key_featuredStickers_addButtonPressed);
        fallbackKeys.put(key_chat_outQuote, key_chat_outReplyLine);
        fallbackKeys.put(key_chat_outReplyLine2, key_chat_outReplyLine);
        fallbackKeys.put(key_chat_inAdminText, key_chat_inTimeText);
        fallbackKeys.put(key_chat_inAdminSelectedText, key_chat_inTimeSelectedText);
        fallbackKeys.put(key_player_progressCachedBackground, key_player_progressBackground);
        fallbackKeys.put(key_chat_inAudioCacheSeekbar, key_chat_inAudioSeekbar);
        fallbackKeys.put(key_chat_outAudioCacheSeekbar, key_chat_outAudioSeekbar);
        fallbackKeys.put(key_chat_emojiSearchBackground, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_location_sendLiveLocationIcon, key_location_sendLocationIcon);
        fallbackKeys.put(key_changephoneinfo_image2, key_featuredStickers_addButton);
        fallbackKeys.put(key_graySectionText, key_windowBackgroundWhiteGrayText2);
        fallbackKeys.put(key_chat_inMediaIcon, key_chat_inBubble);
        fallbackKeys.put(key_chat_outMediaIcon, key_chat_outBubble);
        fallbackKeys.put(key_chat_inMediaIconSelected, key_chat_inBubbleSelected);
        fallbackKeys.put(key_chat_outMediaIconSelected, key_chat_outBubbleSelected);
        fallbackKeys.put(key_dialog_inlineProgressBackground, key_windowBackgroundGray);
        fallbackKeys.put(key_dialog_inlineProgress, key_chats_menuItemIcon);
        fallbackKeys.put(key_groupcreate_spanDelete, key_chats_actionIcon);
        fallbackKeys.put(key_sharedMedia_photoPlaceholder, key_windowBackgroundGray);
        fallbackKeys.put(key_chat_attachPollBackground, key_chat_attachAudioBackground);
        fallbackKeys.put(key_chats_onlineCircle, key_windowBackgroundWhiteBlueText);
        fallbackKeys.put(key_windowBackgroundWhiteBlueButton, key_windowBackgroundWhiteValueText);
        fallbackKeys.put(key_windowBackgroundWhiteBlueIcon, key_windowBackgroundWhiteValueText);
        fallbackKeys.put(key_undo_background, key_chat_gifSaveHintBackground);
        fallbackKeys.put(key_undo_cancelColor, key_chat_gifSaveHintText);
        fallbackKeys.put(key_undo_infoColor, key_chat_gifSaveHintText);
        fallbackKeys.put(key_windowBackgroundUnchecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_windowBackgroundChecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_switchTrackBlue, key_switchTrack);
        fallbackKeys.put(key_switchTrackBlueChecked, key_switchTrackChecked);
        fallbackKeys.put(key_switchTrackBlueThumb, key_windowBackgroundWhite);
        fallbackKeys.put(key_switchTrackBlueThumbChecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_windowBackgroundCheckText, key_windowBackgroundWhite);
        fallbackKeys.put(key_contextProgressInner4, key_contextProgressInner1);
        fallbackKeys.put(key_contextProgressOuter4, key_contextProgressOuter1);
        fallbackKeys.put(key_switchTrackBlueSelector, key_listSelector);
        fallbackKeys.put(key_switchTrackBlueSelectorChecked, key_listSelector);
        fallbackKeys.put(key_chat_emojiBottomPanelIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_chat_emojiSearchIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_chat_emojiPanelStickerSetNameHighlight, key_windowBackgroundWhiteBlueText4);
        fallbackKeys.put(key_chat_emojiPanelStickerPackSelectorLine, key_chat_emojiPanelIconSelected);
        fallbackKeys.put(key_sheet_scrollUp, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_sheet_other, key_player_actionBarItems);
        fallbackKeys.put(key_dialogSearchBackground, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_dialogSearchHint, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_dialogSearchIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_dialogSearchText, key_windowBackgroundWhiteBlackText);
        fallbackKeys.put(key_dialogFloatingButton, key_dialogRoundCheckBox);
        fallbackKeys.put(key_dialogFloatingButtonPressed, key_dialogRoundCheckBox);
        fallbackKeys.put(key_dialogFloatingIcon, key_dialogRoundCheckBoxCheck);
        fallbackKeys.put(key_dialogShadowLine, key_chat_emojiPanelShadowLine);
        fallbackKeys.put(key_actionBarDefaultArchived, key_actionBarDefault);
        fallbackKeys.put(key_actionBarDefaultArchivedSelector, key_actionBarDefaultSelector);
        fallbackKeys.put(key_actionBarDefaultArchivedIcon, key_actionBarDefaultIcon);
        fallbackKeys.put(key_actionBarDefaultArchivedTitle, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarDefaultArchivedSearch, key_actionBarDefaultSearch);
        fallbackKeys.put(key_actionBarDefaultArchivedSearchPlaceholder, key_actionBarDefaultSearchPlaceholder);
        fallbackKeys.put(key_chats_message_threeLines, key_chats_message);
        fallbackKeys.put(key_chats_nameMessage_threeLines, key_chats_nameMessage);
        fallbackKeys.put(key_chats_nameArchived, key_chats_name);
        fallbackKeys.put(key_chats_nameMessageArchived, key_chats_nameMessage);
        fallbackKeys.put(key_chats_nameMessageArchived_threeLines, key_chats_nameMessage);
        fallbackKeys.put(key_chats_messageArchived, key_chats_message);
        fallbackKeys.put(key_avatar_backgroundArchived, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chats_archiveBackground, key_chats_actionBackground);
        fallbackKeys.put(key_chats_archivePinBackground, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chats_archiveIcon, key_chats_actionIcon);
        fallbackKeys.put(key_chats_archiveText, key_chats_actionIcon);
        fallbackKeys.put(key_actionBarDefaultSubmenuItemIcon, key_dialogIcon);
        fallbackKeys.put(key_checkboxDisabled, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chat_status, key_actionBarDefaultSubtitle);
        fallbackKeys.put(key_chat_inGreenCall, key_calls_callReceivedGreenIcon);
        fallbackKeys.put(key_chat_outGreenCall, key_calls_callReceivedGreenIcon);
        fallbackKeys.put(key_actionBarTabActiveText, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarTabUnactiveText, key_actionBarDefaultSubtitle);
        fallbackKeys.put(key_actionBarTabLine, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarTabSelector, key_actionBarDefaultSelector);
        fallbackKeys.put(key_profile_status, key_avatar_subtitleInProfileBlue);
        fallbackKeys.put(key_chats_menuTopBackgroundCats, key_avatar_backgroundActionBarBlue);
        fallbackKeys.put(key_chat_outLinkSelectBackground, key_chat_linkSelectBackground);
        fallbackKeys.put(key_actionBarDefaultSubmenuSeparator, key_windowBackgroundGray);
        fallbackKeys.put(key_chat_attachPermissionImage, key_dialogTextBlack);
        fallbackKeys.put(key_chat_attachPermissionMark, key_chat_sentError);
        fallbackKeys.put(key_chat_attachPermissionText, key_dialogTextBlack);
        fallbackKeys.put(key_chat_attachEmptyImage, key_emptyListPlaceholder);
        fallbackKeys.put(key_actionBarBrowser, key_actionBarDefault);
        fallbackKeys.put(key_chats_sentReadCheck, key_chats_sentCheck);
        fallbackKeys.put(key_chat_outSentCheckRead, key_chat_outSentCheck);
        fallbackKeys.put(key_chat_outSentCheckReadSelected, key_chat_outSentCheckSelected);
        fallbackKeys.put(key_chats_archivePullDownBackground, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chats_archivePullDownBackgroundActive, key_chats_actionBackground);
        fallbackKeys.put(key_avatar_backgroundArchivedHidden, key_avatar_backgroundSaved);
        fallbackKeys.put(key_featuredStickers_removeButtonText, key_featuredStickers_addButtonPressed);
        fallbackKeys.put(key_dialogEmptyImage, key_player_time);
        fallbackKeys.put(key_dialogEmptyText, key_player_time);
        fallbackKeys.put(key_location_actionIcon, key_dialogTextBlack);
        fallbackKeys.put(key_location_actionActiveIcon, key_windowBackgroundWhiteBlueText7);
        fallbackKeys.put(key_location_actionBackground, key_dialogBackground);
        fallbackKeys.put(key_location_actionPressedBackground, key_dialogBackgroundGray);
        fallbackKeys.put(key_location_sendLocationText, key_windowBackgroundWhiteBlueText7);
        fallbackKeys.put(key_location_sendLiveLocationText, key_windowBackgroundWhiteGreenText);
        fallbackKeys.put(key_chat_outTextSelectionHighlight, key_chat_textSelectBackground);
        fallbackKeys.put(key_chat_inTextSelectionHighlight, key_chat_textSelectBackground);
        fallbackKeys.put(key_chat_TextSelectionCursor, key_chat_messagePanelCursor);
        fallbackKeys.put(key_chat_outTextSelectionCursor, key_chat_TextSelectionCursor);
        fallbackKeys.put(key_chat_inPollCorrectAnswer, key_chat_attachLocationBackground);
        fallbackKeys.put(key_chat_outPollCorrectAnswer, key_chat_attachLocationBackground);
        fallbackKeys.put(key_chat_inPollWrongAnswer, key_chat_attachAudioBackground);
        fallbackKeys.put(key_chat_outPollWrongAnswer, key_chat_attachAudioBackground);
        fallbackKeys.put(key_chat_editMediaButton, key_dialogFloatingButton);
        fallbackKeys.put(key_chat_attachCheckBoxBackground, key_dialogRoundCheckBox);

        fallbackKeys.put(key_profile_tabText, key_windowBackgroundWhiteGrayText);
        fallbackKeys.put(key_profile_tabSelectedText, key_windowBackgroundWhiteBlueHeader);
        fallbackKeys.put(key_profile_tabSelectedLine, key_windowBackgroundWhiteBlueHeader);
        fallbackKeys.put(key_profile_tabSelector, key_listSelector);

        fallbackKeys.put(key_chat_attachGalleryText, key_chat_attachGalleryBackground);
        fallbackKeys.put(key_chat_attachAudioText, key_chat_attachAudioBackground);
        fallbackKeys.put(key_chat_attachFileText, key_chat_attachFileBackground);
        fallbackKeys.put(key_chat_attachContactText, key_chat_attachContactBackground);
        fallbackKeys.put(key_chat_attachLocationText, key_chat_attachLocationBackground);
        fallbackKeys.put(key_chat_attachPollText, key_chat_attachPollBackground);

        fallbackKeys.put(key_chat_inPsaNameText, key_avatar_nameInMessageGreen);
        fallbackKeys.put(key_chat_outPsaNameText, key_avatar_nameInMessageGreen);

        fallbackKeys.put(key_chat_outAdminText, key_chat_outTimeText);
        fallbackKeys.put(key_chat_outAdminSelectedText, key_chat_outTimeSelectedText);

        fallbackKeys.put(key_returnToCallMutedBackground, key_windowBackgroundWhite);
        fallbackKeys.put(key_dialogSwipeRemove, key_avatar_backgroundRed);

        fallbackKeys.put(key_chat_inReactionButtonBackground, key_chat_inLoader);
        fallbackKeys.put(key_chat_outReactionButtonBackground, key_chat_outLoader);
        fallbackKeys.put(key_chat_inReactionButtonText, key_chat_inPreviewInstantText);
        fallbackKeys.put(key_chat_outReactionButtonText, key_chat_outPreviewInstantText);
        fallbackKeys.put(key_chat_inReactionButtonTextSelected, key_windowBackgroundWhite);
        fallbackKeys.put(key_chat_outReactionButtonTextSelected, key_windowBackgroundWhite);
        fallbackKeys.put(key_dialogReactionMentionBackground, key_voipgroup_mutedByAdminGradient2);
        fallbackKeys.put(key_topics_unreadCounter, key_chats_unreadCounter);
        fallbackKeys.put(key_topics_unreadCounterMuted, key_chats_message);

        fallbackKeys.put(key_avatar_background2Saved, key_avatar_backgroundSaved);
        fallbackKeys.put(key_avatar_background2Red, key_avatar_backgroundRed);
        fallbackKeys.put(key_avatar_background2Orange, key_avatar_backgroundOrange);
        fallbackKeys.put(key_avatar_background2Violet, key_avatar_backgroundViolet);
        fallbackKeys.put(key_avatar_background2Green, key_avatar_backgroundGreen);
        fallbackKeys.put(key_avatar_background2Cyan, key_avatar_backgroundCyan);
        fallbackKeys.put(key_avatar_background2Blue, key_avatar_backgroundBlue);
        fallbackKeys.put(key_avatar_background2Pink, key_avatar_backgroundPink);

        fallbackKeys.put(key_statisticChartLine_orange, key_color_orange);
        fallbackKeys.put(key_statisticChartLine_blue, key_color_blue);
        fallbackKeys.put(key_statisticChartLine_red, key_color_red);
        fallbackKeys.put(key_statisticChartLine_lightblue, key_color_lightblue);
        fallbackKeys.put(key_statisticChartLine_golden, key_color_yellow);
        fallbackKeys.put(key_statisticChartLine_purple, key_color_purple);
        fallbackKeys.put(key_statisticChartLine_indigo, key_color_purple);
        fallbackKeys.put(key_statisticChartLine_cyan, key_color_cyan);

        fallbackKeys.put(key_actionBarActionModeReaction, key_windowBackgroundGray);
        fallbackKeys.put(key_actionBarActionModeReactionText, key_chat_inReactionButtonText);

        fallbackKeys.put(key_table_background, key_graySection);
        fallbackKeys.put(key_table_border, key_divider);

        for (int i = 0; i < keys_avatar_background.length; i++) {
            themeAccentExclusionKeys.add(keys_avatar_background[i]);
        }
        for (int i = 0; i < keys_avatar_background2.length; i++) {
            themeAccentExclusionKeys.add(keys_avatar_background2[i]);
        }
        for (int i = 0; i < keys_avatar_nameInMessage.length; i++) {
            themeAccentExclusionKeys.add(keys_avatar_nameInMessage[i]);
        }
        for (int i = 0; i < keys_colors.length; i++) {
            themeAccentExclusionKeys.add(keys_colors[i]);
        }

        themeAccentExclusionKeys.add(key_chat_attachFileBackground);
        themeAccentExclusionKeys.add(key_chat_attachGalleryBackground);
        themeAccentExclusionKeys.add(key_chat_attachFileText);
        themeAccentExclusionKeys.add(key_chat_attachGalleryText);
        themeAccentExclusionKeys.add(key_statisticChartLine_blue);
        themeAccentExclusionKeys.add(key_statisticChartLine_green);
        themeAccentExclusionKeys.add(key_statisticChartLine_red);
        themeAccentExclusionKeys.add(key_statisticChartLine_golden);
        themeAccentExclusionKeys.add(key_statisticChartLine_lightblue);
        themeAccentExclusionKeys.add(key_statisticChartLine_lightgreen);
        themeAccentExclusionKeys.add(key_statisticChartLine_orange);
        themeAccentExclusionKeys.add(key_statisticChartLine_indigo);
        themeAccentExclusionKeys.add(key_chat_inCodeBackground);

        themeAccentExclusionKeys.add(key_voipgroup_checkMenu);
        themeAccentExclusionKeys.add(key_voipgroup_muteButton);
        themeAccentExclusionKeys.add(key_voipgroup_muteButton2);
        themeAccentExclusionKeys.add(key_voipgroup_muteButton3);
        themeAccentExclusionKeys.add(key_voipgroup_searchText);
        themeAccentExclusionKeys.add(key_voipgroup_searchPlaceholder);
        themeAccentExclusionKeys.add(key_voipgroup_searchBackground);
        themeAccentExclusionKeys.add(key_voipgroup_leaveCallMenu);
        themeAccentExclusionKeys.add(key_voipgroup_scrollUp);
        themeAccentExclusionKeys.add(key_voipgroup_soundButton);
        themeAccentExclusionKeys.add(key_voipgroup_soundButtonActive);
        themeAccentExclusionKeys.add(key_voipgroup_soundButtonActiveScrolled);
        themeAccentExclusionKeys.add(key_voipgroup_soundButton2);
        themeAccentExclusionKeys.add(key_voipgroup_soundButtonActive2);
        themeAccentExclusionKeys.add(key_voipgroup_soundButtonActive2Scrolled);
        themeAccentExclusionKeys.add(key_voipgroup_leaveButton);
        themeAccentExclusionKeys.add(key_voipgroup_leaveButtonScrolled);
        themeAccentExclusionKeys.add(key_voipgroup_connectingProgress);
        themeAccentExclusionKeys.add(key_voipgroup_disabledButton);
        themeAccentExclusionKeys.add(key_voipgroup_rtmpButton);
        themeAccentExclusionKeys.add(key_voipgroup_disabledButtonActive);
        themeAccentExclusionKeys.add(key_voipgroup_disabledButtonActiveScrolled);
        themeAccentExclusionKeys.add(key_voipgroup_unmuteButton);
        themeAccentExclusionKeys.add(key_voipgroup_unmuteButton2);
        themeAccentExclusionKeys.add(key_voipgroup_actionBarUnscrolled);
        themeAccentExclusionKeys.add(key_voipgroup_listViewBackgroundUnscrolled);
        themeAccentExclusionKeys.add(key_voipgroup_lastSeenTextUnscrolled);
        themeAccentExclusionKeys.add(key_voipgroup_mutedIconUnscrolled);
        themeAccentExclusionKeys.add(key_voipgroup_actionBar);
        themeAccentExclusionKeys.add(key_voipgroup_actionBarItems);
        themeAccentExclusionKeys.add(key_voipgroup_actionBarItemsSelector);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminIcon);
        themeAccentExclusionKeys.add(key_voipgroup_mutedIcon);
        themeAccentExclusionKeys.add(key_voipgroup_lastSeenText);
        themeAccentExclusionKeys.add(key_voipgroup_nameText);
        themeAccentExclusionKeys.add(key_voipgroup_listViewBackground);
        themeAccentExclusionKeys.add(key_voipgroup_listeningText);
        themeAccentExclusionKeys.add(key_voipgroup_speakingText);
        themeAccentExclusionKeys.add(key_voipgroup_listSelector);
        themeAccentExclusionKeys.add(key_voipgroup_inviteMembersBackground);
        themeAccentExclusionKeys.add(key_voipgroup_dialogBackground);
        themeAccentExclusionKeys.add(key_voipgroup_overlayGreen1);
        themeAccentExclusionKeys.add(key_voipgroup_overlayGreen2);
        themeAccentExclusionKeys.add(key_voipgroup_overlayBlue1);
        themeAccentExclusionKeys.add(key_voipgroup_overlayBlue2);
        themeAccentExclusionKeys.add(key_voipgroup_topPanelGreen1);
        themeAccentExclusionKeys.add(key_voipgroup_topPanelGreen2);
        themeAccentExclusionKeys.add(key_voipgroup_topPanelBlue1);
        themeAccentExclusionKeys.add(key_voipgroup_topPanelBlue2);
        themeAccentExclusionKeys.add(key_voipgroup_topPanelGray);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertGradientMuted);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertGradientMuted2);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertGradientUnmuted);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertGradientUnmuted2);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertMutedByAdmin);
        themeAccentExclusionKeys.add(key_voipgroup_overlayAlertMutedByAdmin2);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminGradient);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminGradient2);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminGradient3);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminMuteButton);
        themeAccentExclusionKeys.add(key_voipgroup_mutedByAdminMuteButtonDisabled);
        themeAccentExclusionKeys.add(key_voipgroup_windowBackgroundWhiteInputField);
        themeAccentExclusionKeys.add(key_voipgroup_windowBackgroundWhiteInputFieldActivated);
        themeAccentExclusionKeys.add(key_premiumGradient0);
        themeAccentExclusionKeys.add(key_premiumGradient1);
        themeAccentExclusionKeys.add(key_premiumGradient2);
        themeAccentExclusionKeys.add(key_premiumGradient3);
        themeAccentExclusionKeys.add(key_premiumGradient4);
        themeAccentExclusionKeys.add(key_premiumGradientBackground1);
        themeAccentExclusionKeys.add(key_premiumGradientBackground2);
        themeAccentExclusionKeys.add(key_premiumGradientBackground3);
        themeAccentExclusionKeys.add(key_premiumGradientBackground4);
        themeAccentExclusionKeys.add(key_premiumStartSmallStarsColor);
        themeAccentExclusionKeys.add(key_premiumStarGradient1);
        themeAccentExclusionKeys.add(key_premiumStarGradient2);
        themeAccentExclusionKeys.add(key_stories_circle1);
        themeAccentExclusionKeys.add(key_stories_circle2);
        themeAccentExclusionKeys.add(key_stories_circle_dialog1);
        themeAccentExclusionKeys.add(key_stories_circle_dialog2);
        themeAccentExclusionKeys.add(key_stories_circle_closeFriends1);
        themeAccentExclusionKeys.add(key_stories_circle_closeFriends2);


        themes = new ArrayList<>();
        otherThemes = new ArrayList<>();
        themesDict = new HashMap<>();
        currentColorsNoAccent = new SparseIntArray();
        currentColors = new SparseIntArray();

        SharedPreferences themeConfig = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);

        ThemeInfo themeInfo = new ThemeInfo();
        themeInfo.name = "Blue";
        themeInfo.assetName = "bluebubbles.attheme";
        themeInfo.previewBackgroundColor = 0xff95beec;
        themeInfo.previewInColor = 0xffffffff;
        themeInfo.previewOutColor = 0xffd0e6ff;
        themeInfo.firstAccentIsDefault = true;
        themeInfo.currentAccentId = DEFALT_THEME_ACCENT_ID;
        themeInfo.sortIndex = 1;
        themeInfo.setAccentColorOptions(
                new int[]    { 0xFF5890C5,                     0xFF239853,                    0xFFCE5E82,                    0xFF7F63C3,                    0xFF2491AD,                    0xFF299C2F,                    0xFF8854B4,                    0xFF328ACF,                    0xFF43ACC7,                    0xFF52AC44,                    0xFFCD5F93,                    0xFFD28036,                    0xFF8366CC,                    0xFFCE4E57,                    0xFFD3AE40,                    0xFF7B88AB },
                new int[]    { 0xFFB8E18D,                     0xFFFAFBCC,                    0xFFFFF9DC,                    0xFFC14F6E,                    0xFFD1BD1B,                    0xFFFFFAC9,                    0xFFFCF6D8,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    { 0x00000000,                     0xFFF2FBC9,                    0xFFFBF4DF, 	                         0,	                             0,                    0xFFFDEDB4,                    0xFFFCF7B6,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    { 0x00000000,                     0xFFdfe2a0,                    0xFFf1b290,                    0xFFd7c1e9,                    0xFFd7b89e,                    0xFFec9e73,                    0xFFcbb0e4,                    0xff9bbce7,                    0xff91c5ec,                    0xff9bc982,                    0xffe4a1c1,                    0xffe3ae7d,                    0xffb8aaea,                    0xffeb9c79,                    0xffd3bc74,                    0xffa0aace },
                new int[]    { 0x00000000,                     0xFFbad89d,                    0xFFeccf94,                    0xFFe8bdd6,                    0xFFe6dec2,                    0xFFe8d085,                    0xFFebc8e9,                    0xffc0d9f3,                    0xffbfdfec,                    0xffe0dd93,                    0xffe9bed6,                    0xffecd5a2,                    0xffc5c9ee,                    0xfff0bd99,                    0xffe9df9e,                    0xffcacedd },
                new int[]    { 0x00000000,                     0xFFe2dea7,                    0xFFe7b384,                    0xFFd2aee9,                    0xFFdac5ae,                    0xFFeea677,                    0xFFdfa8d1,                    0xff95c3eb,                    0xffb5e1d9,                    0xffbed595,                    0xffcca8e1,                    0xffdfb076,                    0xffb3b1e2,                    0xffe79db4,                    0xffe0c88b,                    0xffa6add2 },
                new int[]    { 0x00000000,                     0xFF9ec790,                    0xFFebdea8,                    0xFFeccb88,                    0xFFe5dcbf,                    0xFFede4a9,                    0xFFedc8a8,                    0xffbbd5e8,                    0xffbfdbe8,                    0xffd1db97,                    0xffefcbd7,                    0xffecd694,                    0xffdfbeed,                    0xfff3b182,                    0xffe5d397,                    0xffcacee8 },
                new int[]    {         99,                              9,                            10,                            11,                            12,                            13,                            14,                             0,                             1,                             2,                             3,                             4,                             5,                             6,                             7,                             8 },
                new String[] {         "",  "p-pXcflrmFIBAAAAvXYQk-mCwZU", "JqSUrO0-mFIBAAAAWwTvLzoWGQI", "O-wmAfBPSFADAAAA4zINVfD_bro", "RepJ5uE_SVABAAAAr4d0YhgB850", "-Xc-np9y2VMCAAAARKr0yNNPYW0", "fqv01SQemVIBAAAApND8LDRUhRU", "fqv01SQemVIBAAAApND8LDRUhRU", "RepJ5uE_SVABAAAAr4d0YhgB850", "lp0prF8ISFAEAAAA_p385_CvG0w", "heptcj-hSVACAAAAC9RrMzOa-cs", "PllZ-bf_SFAEAAAA8crRfwZiDNg", "dhf9pceaQVACAAAAbzdVo4SCiZA", "Ujx2TFcJSVACAAAARJ4vLa50MkM", "p-pXcflrmFIBAAAAvXYQk-mCwZU", "dk_wwlghOFACAAAAfz9xrxi6euw" },
                new int[]    {          0,                            180,                            45,                             0,                            45,                           180,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0 },
                new int[]    {          0,                             52,                            46,                            57,                            45,                            64,                            52,                            35,                            36,                            41,                            50,                            50,                            35,                            38,                            37,                            30 }
                );
        sortAccents(themeInfo);
        themes.add(currentDayTheme = defaultTheme = themeInfo);
        themesDict.put("Blue", themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Dark Blue";
        themeInfo.assetName = "darkblue.attheme";
        themeInfo.previewBackgroundColor = 0xff5f6e82;
        themeInfo.previewInColor = 0xff76869c;
        themeInfo.previewOutColor = 0xff82a8e3;
        themeInfo.sortIndex = 3;
        themeInfo.setAccentColorOptions(
                new int[]    {                    0xFF927BD4,                    0xFF698AFB,                    0xFF23A7F0,                    0xFF7B71D1,                    0xFF69B955,                    0xFF2990EA,                    0xFF7082E9,                    0xFF66BAED,                    0xff3685fa,                    0xff46c8ed,                    0xff64AC5F,                    0xffeb7cb1,                    0xffee902a,                    0xffa281f0,                    0xffd34324,                    0xffeebd34,                    0xff7f8fab,                    0xff3581e3 },
                new int[]    {                    0xFF9D5C99,                    0xFF635545,                    0xFF31818B,                    0xFFAD6426,                    0xFF4A7034,                    0xFF335D82,                    0xFF36576F,                    0xFF597563,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFF604DA8,                    0xFF685D4C,                    0xFF1B6080,                    0xFF99354E,                    0xFF275D3B,                    0xFF317A98,                    0xFF376E87,                    0xFF5E7370,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFF271e2e,                    0xFF171a22,                    0xFF071e1f,                    0xFF100f13,                    0xFF0e1811,                    0xFF0b131c,                    0xFF1d2129,                    0xFF202c2a,                    0xff0e141a,                    0xff162325,                    0xff161d15,                    0xff24191e,                    0xff251b13,                    0xff1f1d29,                    0xff22160e,                    0xff272115,                    0xff171a1b,                    0xff0e141a },
                new int[]    {                    0xFF110e13,                    0xFF26262e,                    0xFF141d26,                    0xFF221a27,                    0xFF1f2818,                    0xFF192330,                    0xFF12161a,                    0xFF141a1e,                    0xff172431,                    0xff0e1718,                    0xff172719,                    0xff23171c,                    0xff201408,                    0xff14131c,                    0xff2d1d16,                    0xff1a160d,                    0xff212328,                    0xff172431 },
                new int[]    {                    0xFF2b1e2b,                    0xFF15151b,                    0xFF0c151a,                    0xFF0e0f13,                    0xFF0b170f,                    0xFF131822,                    0xFF17242d,                    0xFF16202b,                    0xff0f171e,                    0xff1e2e2e,                    0xff141e14,                    0xff2b1929,                    0xff2e1f15,                    0xff292331,                    0xff23140c,                    0xff292414,                    0xff181a1d,                    0xff0f171e },
                new int[]    {                    0xFF161227,                    0xFF1a1916,                    0xFF0d272c,                    0xFF271d29,                    0xFF171d19,                    0xFF172331,                    0xFF111521,                    0xFF051717,                    0xff141c2b,                    0xff121f1f,                    0xff1c261a,                    0xff1f141d,                    0xff1b130a,                    0xff17131b,                    0xff2d1924,                    0xff1e170e,                    0xff212228,                    0xff141c2b },
                new int[]    {                            11,                            12,                            13,                            14,                            15,                            16,                            17,                            18,                             0,                             1,                             2,                             3,                             4,                             5,                             6,                             7,                             8,                             9 },
                new String[] { "O-wmAfBPSFADAAAA4zINVfD_bro", "RepJ5uE_SVABAAAAr4d0YhgB850", "dk_wwlghOFACAAAAfz9xrxi6euw", "9LW_RcoOSVACAAAAFTk3DTyXN-M", "PllZ-bf_SFAEAAAA8crRfwZiDNg", "-Xc-np9y2VMCAAAARKr0yNNPYW0", "kO4jyq55SFABAAAA0WEpcLfahXk", "CJNyxPMgSVAEAAAAvW9sMwc51cw", "fqv01SQemVIBAAAApND8LDRUhRU", "RepJ5uE_SVABAAAAr4d0YhgB850", "CJNyxPMgSVAEAAAAvW9sMwc51cw", "9LW_RcoOSVACAAAAFTk3DTyXN-M", "9GcNVISdSVADAAAAUcw5BYjELW4", "F5oWoCs7QFACAAAAgf2bD_mg8Bw", "9ShF73d1MFIIAAAAjWnm8_ZMe8Q", "3rX-PaKbSFACAAAAEiHNvcEm6X4", "dk_wwlghOFACAAAAfz9xrxi6euw", "fqv01SQemVIBAAAApND8LDRUhRU" },
                new int[]    {                           225,                            45,                           225,                           135,                            45,                           225,                            45,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0 },
                new int[]    {                            40,                            40,                            31,                            50,                            25,                            34,                            35,                            35,                            38,                            29,                            24,                            34,                            34,                            31,                            29,                            37,                            21,                            38 }
                );
        sortAccents(themeInfo);
        themes.add(themeInfo);
        themesDict.put("Dark Blue", currentNightTheme = themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Arctic Blue";
        themeInfo.assetName = "arctic.attheme";
        themeInfo.previewBackgroundColor = 0xffe1e9f0;
        themeInfo.previewInColor = 0xffffffff;
        themeInfo.previewOutColor = 0xff6ca1eb;
        themeInfo.sortIndex = 5;
        themeInfo.setAccentColorOptions(
                new int[]    {                    0xFF40B1E2,                    0xFF41B05D,                    0xFFCE8C20,                    0xFF57A3EB,                    0xFFDE8534,                    0xFFCC6189,                    0xFF3490EB,                    0xFF43ACC7,                    0xFF52AC44,                    0xFFCD5F93,                    0xFFD28036,                    0xFF8366CC,                    0xFFCE4E57,                    0xFFD3AE40,                    0xFF7B88AB },
                new int[]    {                    0xFF319FCA,                    0xFF28A359,                    0xFF8C5A3F,                    0xFF3085D3,                    0xFFC95870,                    0xFF7871CD,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFF4EBEE2,                    0xFF6BBC59,                    0xFF9E563C,                    0xFF48C2D8,                    0xFFD87047,                    0xFFBE6EAF,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFFc5e2f0,                    0xFFdadea9,                    0xFFd6c9a5,                    0xFFe3f3f3,                    0xFFeee5b0,                    0xFFe5dfec,                    0xffe0e7ed,                    0xffbfe0eb,                    0xffc2e0af,                    0xffefd9e4,                    0xfff1dfbd,                    0xffe1dbec,                    0xffedd8d8,                    0xffebe1cd,                    0xffdcdee5 },
                new int[]    {                    0xFFe8f4f3,                    0xFFbce3ac,                    0xFFe6dbaf,                    0xFFc8e6ee,                    0xFFeebeaa,                    0xFFe1c6ec,                    0xffbed7f3,                    0xffbfe0eb,                    0xffcbe19a,                    0xffecc6d9,                    0xffe8c79b,                    0xffbdc1ec,                    0xffeecac0,                    0xffebe2b5,                    0xffc3cadf },
                new int[]    {                    0xFFb4daf0,                    0xFFcde7a9,                    0xFFe8c091,                    0xFFd9eff3,                    0xFFeecf92,                    0xFFf6eaf6,                    0xffe0e8f3,                    0xffcaebec,                    0xffb8de89,                    0xfff1d8e6,                    0xfff3d7a6,                    0xffd6d8f5,                    0xffedddcd,                    0xffebdcc9,                    0xffe7edf1 },
                new int[]    {                    0xFFcff0ef,                    0xFFa8cf9b,                    0xFFe1d09f,                    0xFFb4d6e8,                    0xFFeeaf87,                    0xFFe5c5cf,                    0xffc8dbf3,                    0xffaedceb,                    0xffcee5a2,                    0xfff0c0d9,                    0xffdfb48e,                    0xffbdbaf2,                    0xfff1c9bb,                    0xffe7d7ae,                    0xffc5c6da },
                new int[]    {                             9,                            10,                            11,                            12,                            13,                            14,                             0,                             1,                             2,                             3,                             4,                             5,                             6,                             7,                             8 },
                new String[] { "MIo6r0qGSFAFAAAAtL8TsDzNX60", "dhf9pceaQVACAAAAbzdVo4SCiZA", "fqv01SQemVIBAAAApND8LDRUhRU", "p-pXcflrmFIBAAAAvXYQk-mCwZU", "JqSUrO0-mFIBAAAAWwTvLzoWGQI", "F5oWoCs7QFACAAAAgf2bD_mg8Bw", "fqv01SQemVIBAAAApND8LDRUhRU", "RepJ5uE_SVABAAAAr4d0YhgB850", "PllZ-bf_SFAEAAAA8crRfwZiDNg", "pgJfpFNRSFABAAAACDT8s5sEjfc", "ptuUd96JSFACAAAATobI23sPpz0", "dhf9pceaQVACAAAAbzdVo4SCiZA", "JqSUrO0-mFIBAAAAWwTvLzoWGQI", "9iklpvIPQVABAAAAORQXKur_Eyc", "F5oWoCs7QFACAAAAgf2bD_mg8Bw" },
                new int[]    {                           315,                           315,                           225,                           315,                             0,                           180,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0 },
                new int[]    {                            50,                            50,                            58,                            47,                            46,                            50,                            49,                            46,                            51,                            50,                            49,                            34,                            54,                            50,                            40 }
                );
        sortAccents(themeInfo);
        themes.add(themeInfo);
        themesDict.put("Arctic Blue", themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Day";
        themeInfo.assetName = "day.attheme";
        themeInfo.previewBackgroundColor = 0xffffffff;
        themeInfo.previewInColor = 0xffebeef4;
        themeInfo.previewOutColor = 0xff7cb2fe;
        themeInfo.sortIndex = 2;
        themeInfo.setAccentColorOptions(
                new int[]    { 0xFF56A2C9, 0xFFCC6E83, 0xFFD08E47, 0xFFCC6462, 0xFF867CD2, 0xFF4C91DF, 0xFF57B4D9, 0xFF54B169, 0xFFD9BF3F, 0xFFCC6462, 0xFFCC6E83, 0xFF9B7BD2, 0xFFD79144, 0xFF7B88AB },
                new int[]    { 0xFF6580DC, 0xFF6C6DD2, 0xFFCB5481, 0xFFC34A4A, 0xFF5C8EDF, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000 },
                new int[]    { 0xFF3EC1D6, 0xFFC86994, 0xFFDBA12F, 0xFFD08E3B, 0xFF51B5CB, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000 },
                new int[]    { 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000 },
                new int[]    { 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000 },
                null,
                null,
                new int[]    {          9,         10,         11,         12,         13,          0,          1,          2,          3,          4,          5,          6,          7,          8 },
                new String[] {         "",         "",         "",         "",         "",         "",         "",         "",         "",         "",         "",         "",         "",         "" },
                new int[]    {          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0 },
                new int[]    {          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0,          0 }
                );
        sortAccents(themeInfo);
        themes.add(themeInfo);
        themesDict.put("Day", themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Night";
        themeInfo.assetName = "night.attheme";
        themeInfo.previewBackgroundColor = 0xff535659;
        themeInfo.previewInColor = 0xff747A84;
        themeInfo.previewOutColor = 0xff75A2E6;
        themeInfo.sortIndex = 4;
        themeInfo.setAccentColorOptions(
                new int[]    {                    0xFF6ABE3F,                    0xFF8D78E3,                    0xFFDE5E7E,                    0xFF5977E8,                    0xFFDBC11A,                    0xff3e88f7,                    0xff4ab5d3,                    0xff4ab841,                    0xffd95576,                    0xffe27d2b,                    0xff936cda,                    0xffd04336,                    0xffe8ae1c,                    0xff7988a3 },
                new int[]    {                    0xFF8A5294,                    0xFFB46C1B,                    0xFFAF4F6F,                    0xFF266E8D,                    0xFF744EB7,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFF6855BB,                    0xFFA53B4A,                    0xFF62499C,                    0xFF2F919D,                    0xFF298B95,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000,                    0x00000000 },
                new int[]    {                    0xFF16131c,                    0xFF1e1118,                    0xFF0f0b10,                    0xFF090c0c,                    0xFF071519,                    0xff0d0e17,                    0xff111b1c,                    0xff0c110c,                    0xff0e0b0d,                    0xff1d160f,                    0xff09090a,                    0xff1c1210,                    0xff1d1b18,                    0xff0e1012 },
                new int[]    {                    0xFF201827,                    0xFF100f13,                    0xFF1b151a,                    0xFF141f22,                    0xFF0c0c0f,                    0xff090a0c,                    0xff0a0e0e,                    0xff080908,                    0xff1a1618,                    0xff13100d,                    0xff1e1a21,                    0xff0f0d0c,                    0xff0c0b08,                    0xff070707 },
                new int[]    {                    0xFF0e0b13,                    0xFF211623,                    0xFF130e12,                    0xFF0d0f11,                    0xFF10191f,                    0xff181c28,                    0xff142121,                    0xff121812,                    0xff130e11,                    0xff1a130f,                    0xff0b0a0b,                    0xff120d0b,                    0xff15140f,                    0xff101214 },
                new int[]    {                    0xFF1e192a,                    0xFF111016,                    0xFF21141a,                    0xFF111a1b,                    0xFF0a0d13,                    0xff0e0f12,                    0xff070c0b,                    0xff0b0d0b,                    0xff22121e,                    0xff0f0c0c,                    0xff110f17,                    0xff070606,                    0xff0c0a0a,                    0xff09090b },
                new int[]    {                             9,                            10,                            11,                            12,                            13,                             0,                             1,                             2,                             3,                             4,                             5,                             6,                             7,                             8 },
                new String[] { "YIxYGEALQVADAAAAA3QbEH0AowY", "9LW_RcoOSVACAAAAFTk3DTyXN-M", "O-wmAfBPSFADAAAA4zINVfD_bro", "F5oWoCs7QFACAAAAgf2bD_mg8Bw", "-Xc-np9y2VMCAAAARKr0yNNPYW0", "fqv01SQemVIBAAAApND8LDRUhRU", "F5oWoCs7QFACAAAAgf2bD_mg8Bw", "ptuUd96JSFACAAAATobI23sPpz0", "p-pXcflrmFIBAAAAvXYQk-mCwZU", "Nl8Pg2rBQVACAAAA25Lxtb8SDp0", "dhf9pceaQVACAAAAbzdVo4SCiZA", "9GcNVISdSVADAAAAUcw5BYjELW4", "9LW_RcoOSVACAAAAFTk3DTyXN-M", "dk_wwlghOFACAAAAfz9xrxi6euw" },
                new int[]    {                            45,                           135,                             0,                           180,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0,                             0 },
                new int[]    {                            34,                            47,                            52,                            48,                            54,                            50,                            37,                            56,                            48,                            49,                            40,                            64,                            38,                            48 }
                );
        sortAccents(themeInfo);
        themes.add(themeInfo);
        themesDict.put("Night", themeInfo);

        String themesString = themeConfig.getString("themes2", null);

        int remoteVersion = themeConfig.getInt("remote_version", 0);
        int appRemoteThemesVersion = 1;
        if (remoteVersion == appRemoteThemesVersion) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                remoteThemesHash[a] = themeConfig.getLong("2remoteThemesHash" + (a != 0 ? a : ""), 0);
                lastLoadingThemesTime[a] = themeConfig.getInt("lastLoadingThemesTime" + (a != 0 ? a : ""), 0);
            }
        }
        themeConfig.edit().putInt("remote_version", appRemoteThemesVersion).apply();
        if (!TextUtils.isEmpty(themesString)) {
            try {
                JSONArray jsonArray = new JSONArray(themesString);
                for (int a = 0; a < jsonArray.length(); a++) {
                    themeInfo = ThemeInfo.createWithJson(jsonArray.getJSONObject(a));
                    if (themeInfo != null) {
                        otherThemes.add(themeInfo);
                        themes.add(themeInfo);
                        themesDict.put(themeInfo.getKey(), themeInfo);
                        themeInfo.loadWallpapers(themeConfig);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            themesString = themeConfig.getString("themes", null);
            if (!TextUtils.isEmpty(themesString)) {
                String[] themesArr = themesString.split("&");
                for (int a = 0; a < themesArr.length; a++) {
                    themeInfo = ThemeInfo.createWithString(themesArr[a]);
                    if (themeInfo != null) {
                        otherThemes.add(themeInfo);
                        themes.add(themeInfo);
                        themesDict.put(themeInfo.getKey(), themeInfo);
                    }
                }
                saveOtherThemes(true, true);
                themeConfig.edit().remove("themes").commit();
            }
        }

        sortThemes();

        ThemeInfo applyingTheme = null;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        try {
            final ThemeInfo themeDarkBlue = themesDict.get("Dark Blue");

            String theme = preferences.getString("theme", null);
            if ("Default".equals(theme)) {
                applyingTheme = themesDict.get("Blue");
                applyingTheme.currentAccentId = DEFALT_THEME_ACCENT_ID;
            } else if ("Dark".equals(theme)) {
                applyingTheme = themeDarkBlue;
                applyingTheme.currentAccentId = 9;
            } else if (theme != null) {
                applyingTheme = themesDict.get(theme);
                if (applyingTheme != null && !themeConfig.contains("lastDayTheme")) {
                    SharedPreferences.Editor editor = themeConfig.edit();
                    editor.putString("lastDayTheme", applyingTheme.getKey());
                    editor.commit();
                }
            }

            theme = preferences.getString("nighttheme", null);
            if ("Default".equals(theme)) {
                applyingTheme = themesDict.get("Blue");
                applyingTheme.currentAccentId = DEFALT_THEME_ACCENT_ID;
            } else if ("Dark".equals(theme)) {
                currentNightTheme = themeDarkBlue;
                themeDarkBlue.currentAccentId = 9;
            } else if (theme != null) {
                ThemeInfo t = themesDict.get(theme);
                if (t != null) {
                    currentNightTheme = t;
                }
            }

            if (currentNightTheme != null && !themeConfig.contains("lastDarkTheme")) {
                SharedPreferences.Editor editor = themeConfig.edit();
                editor.putString("lastDarkTheme", currentNightTheme.getKey());
                editor.commit();
            }

            SharedPreferences.Editor oldEditor = null;
            SharedPreferences.Editor oldEditorNew = null;
            for (ThemeInfo info : themesDict.values()) {
                if (info.assetName != null && info.accentBaseColor != 0) {
                    String accents = themeConfig.getString("accents_" + info.assetName, null);
                    info.currentAccentId = themeConfig.getInt("accent_current_" + info.assetName, info.firstAccentIsDefault ? DEFALT_THEME_ACCENT_ID : 0);
                    ArrayList<ThemeAccent> newAccents = new ArrayList<>();
                    if (!TextUtils.isEmpty(accents)) {
                        try {
                            SerializedData data = new SerializedData(Base64.decode(accents, Base64.NO_WRAP | Base64.NO_PADDING));
                            int version = data.readInt32(true);
                            int count = data.readInt32(true);
                            for (int a = 0; a < count; a++) {
                                try {
                                    ThemeAccent accent = new ThemeAccent();
                                    accent.id = data.readInt32(true);
                                    accent.accentColor = data.readInt32(true);
                                    if (version >= 9) {
                                        accent.accentColor2 = data.readInt32(true);
                                    }
                                    accent.parentTheme = info;
                                    accent.myMessagesAccentColor = data.readInt32(true);
                                    accent.myMessagesGradientAccentColor1 = data.readInt32(true);
                                    if (version >= 7) {
                                        accent.myMessagesGradientAccentColor2 = data.readInt32(true);
                                        accent.myMessagesGradientAccentColor3 = data.readInt32(true);
                                    }
                                    if (version >= 8) {
                                        accent.myMessagesAnimated = data.readBool(true);
                                    }
                                    if (version >= 3) {
                                        accent.backgroundOverrideColor = data.readInt64(true);
                                    } else {
                                        accent.backgroundOverrideColor = data.readInt32(true);
                                    }
                                    if (version >= 2) {
                                        accent.backgroundGradientOverrideColor1 = data.readInt64(true);
                                    } else {
                                        accent.backgroundGradientOverrideColor1 = data.readInt32(true);
                                    }
                                    if (version >= 6) {
                                        accent.backgroundGradientOverrideColor2 = data.readInt64(true);
                                        accent.backgroundGradientOverrideColor3 = data.readInt64(true);
                                    }
                                    if (version >= 1) {
                                        accent.backgroundRotation = data.readInt32(true);
                                    }
                                    if (version >= 4) {
                                        data.readInt64(true); //unused
                                        accent.patternIntensity = (float) data.readDouble(true);
                                        accent.patternMotion = data.readBool(true);
                                        if (version >= 5) {
                                            accent.patternSlug = data.readString(true);
                                        }
                                    }
                                    if (version >= 5) {
                                        if (data.readBool(true)) {
                                            accent.account = data.readInt32(true);
                                            accent.info = (TLRPC.TL_theme) TLRPC.Theme.TLdeserialize(data, data.readInt32(true), true);
                                        }
                                    }
                                    if (accent.info != null) {
                                        accent.isDefault = accent.info.isDefault;
                                    }
                                    info.themeAccentsMap.put(accent.id, accent);
                                    if (accent.info != null) {
                                        info.accentsByThemeId.put(accent.info.id, accent);
                                    }
                                    newAccents.add(accent);
                                    info.lastAccentId = Math.max(info.lastAccentId, accent.id);
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                            throw new RuntimeException(e);
                        }
                    } else {
                        String key = "accent_for_" + info.assetName;
                        int oldAccentColor = preferences.getInt(key, 0);
                        if (oldAccentColor != 0) {
                            if (oldEditor == null) {
                                oldEditor = preferences.edit();
                                oldEditorNew = themeConfig.edit();
                            }
                            oldEditor.remove(key);
                            boolean found = false;
                            for (int a = 0, N = info.themeAccents.size(); a < N; a++) {
                                ThemeAccent accent = info.themeAccents.get(a);
                                if (accent.accentColor == oldAccentColor) {
                                    info.currentAccentId = accent.id;
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                ThemeAccent accent = new ThemeAccent();
                                accent.id = 100;
                                accent.accentColor = oldAccentColor;
                                accent.parentTheme = info;
                                info.themeAccentsMap.put(accent.id, accent);
                                newAccents.add(0, accent);
                                info.currentAccentId = 100;
                                info.lastAccentId = 101;

                                SerializedData data = new SerializedData(4 * (16 + 2));
                                //verison
                                data.writeInt32(9);
                                data.writeInt32(1);

                                data.writeInt32(accent.id);
                                data.writeInt32(accent.accentColor);
                                data.writeInt32(accent.myMessagesAccentColor);
                                data.writeInt32(accent.myMessagesGradientAccentColor1);
                                data.writeInt32(accent.myMessagesGradientAccentColor2);
                                data.writeInt32(accent.myMessagesGradientAccentColor3);
                                data.writeBool(accent.myMessagesAnimated);
                                data.writeInt64(accent.backgroundOverrideColor);
                                data.writeInt64(accent.backgroundGradientOverrideColor1);
                                data.writeInt64(accent.backgroundGradientOverrideColor2);
                                data.writeInt64(accent.backgroundGradientOverrideColor3);
                                data.writeInt32(accent.backgroundRotation);
                                data.writeInt64(0);
                                data.writeDouble(accent.patternIntensity);
                                data.writeBool(accent.patternMotion);
                                data.writeString(accent.patternSlug);
                                data.writeBool(false);

                                oldEditorNew.putString("accents_" + info.assetName, Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING));
                            }
                            oldEditorNew.putInt("accent_current_" + info.assetName, info.currentAccentId);
                        }
                    }
                    if (!newAccents.isEmpty()) {
                        info.themeAccents.addAll(0, newAccents);
                        sortAccents(info);
                    }
                    if (info.themeAccentsMap != null && info.themeAccentsMap.get(info.currentAccentId) == null) {
                        info.currentAccentId = info.firstAccentIsDefault ? DEFALT_THEME_ACCENT_ID : 0;
                    }
                    info.loadWallpapers(themeConfig);
                    ThemeAccent accent = info.getAccent(false);
                    if (accent != null) {
                        info.overrideWallpaper = accent.overrideWallpaper;
                    }
                }
            }
            if (oldEditor != null) {
                oldEditor.commit();
                oldEditorNew.commit();
            }

            selectedAutoNightType = preferences.getInt("selectedAutoNightType", Build.VERSION.SDK_INT >= 29 ? AUTO_NIGHT_TYPE_SYSTEM : AUTO_NIGHT_TYPE_NONE);
            autoNightScheduleByLocation = preferences.getBoolean("autoNightScheduleByLocation", false);
            autoNightBrighnessThreshold = preferences.getFloat("autoNightBrighnessThreshold", 0.25f);
            autoNightDayStartTime = preferences.getInt("autoNightDayStartTime", 22 * 60);
            autoNightDayEndTime = preferences.getInt("autoNightDayEndTime", 8 * 60);
            autoNightSunsetTime = preferences.getInt("autoNightSunsetTime", 22 * 60);
            autoNightSunriseTime = preferences.getInt("autoNightSunriseTime", 8 * 60);
            autoNightCityName = preferences.getString("autoNightCityName", "");
            long val = preferences.getLong("autoNightLocationLatitude3", 10000);
            if (val != 10000) {
                autoNightLocationLatitude = Double.longBitsToDouble(val);
            } else {
                autoNightLocationLatitude = 10000;
            }
            val = preferences.getLong("autoNightLocationLongitude3", 10000);
            if (val != 10000) {
                autoNightLocationLongitude = Double.longBitsToDouble(val);
            } else {
                autoNightLocationLongitude = 10000;
            }
            autoNightLastSunCheckDay = preferences.getInt("autoNightLastSunCheckDay", -1);
        } catch (Exception e) {
            FileLog.e(e);
            throw new RuntimeException(e);
        }
        if (applyingTheme == null) {
            applyingTheme = defaultTheme;
        } else {
            currentDayTheme = applyingTheme;
        }

        if (preferences.contains("overrideThemeWallpaper") || preferences.contains("selectedBackground2")) {
            boolean override = preferences.getBoolean("overrideThemeWallpaper", false);
            long id = preferences.getLong("selectedBackground2", 1000001);
            if (id == -1 || override && id != -2 && id != 1000001) {
                OverrideWallpaperInfo overrideWallpaper = new OverrideWallpaperInfo();
                overrideWallpaper.color = preferences.getInt("selectedColor", 0);
                overrideWallpaper.slug = preferences.getString("selectedBackgroundSlug", "");
                if (id >= -100 && id <= -1 && overrideWallpaper.color != 0) {
                    overrideWallpaper.slug = COLOR_BACKGROUND_SLUG;
                    overrideWallpaper.fileName = "";
                    overrideWallpaper.originalFileName = "";
                } else {
                    overrideWallpaper.fileName = "wallpaper.jpg";
                    overrideWallpaper.originalFileName = "wallpaper_original.jpg";
                }
                overrideWallpaper.gradientColor1 = preferences.getInt("selectedGradientColor", 0);
                overrideWallpaper.gradientColor2 = preferences.getInt("selectedGradientColor2", 0);
                overrideWallpaper.gradientColor3 = preferences.getInt("selectedGradientColor3", 0);
                overrideWallpaper.rotation = preferences.getInt("selectedGradientRotation", 45);
                overrideWallpaper.isBlurred = preferences.getBoolean("selectedBackgroundBlurred", false);
                overrideWallpaper.isMotion = preferences.getBoolean("selectedBackgroundMotion", false);
                overrideWallpaper.intensity = preferences.getFloat("selectedIntensity", 0.5f);
                currentDayTheme.setOverrideWallpaper(overrideWallpaper);
                if (selectedAutoNightType != AUTO_NIGHT_TYPE_NONE) {
                    currentNightTheme.setOverrideWallpaper(overrideWallpaper);
                }
            }
            preferences.edit().remove("overrideThemeWallpaper").remove("selectedBackground2").commit();
        }

        int switchToTheme = needSwitchToTheme();
        if (switchToTheme == 2) {
            applyingTheme = currentNightTheme;
        }
        applyTheme(applyingTheme, false, false, switchToTheme == 2);
        AndroidUtilities.runOnUIThread(Theme::checkAutoNightThemeConditions);
    }

    private static void sortAccents(ThemeInfo info) {
        Collections.sort(info.themeAccents, (o1, o2) -> {
            if (isHome(o1)) {
                return -1;
            }
            if (isHome(o2)) {
                return 1;
            }
            int i1 = o1.isDefault ? 1 : 0;
            int i2 = o2.isDefault ? 1 : 0;

            if (i1 == i2) {
                if (o1.isDefault) {
                    if (o1.id > o2.id) {
                        return 1;
                    } else if (o1.id < o2.id) {
                        return -1;
                    }
                } else {
                    if (o1.id > o2.id) {
                        return -1;
                    } else if (o1.id < o2.id) {
                        return 1;
                    }
                }
            } else {
                if (i1 > i2) {
                    return -1;
                } else {
                    return 1;
                }
            }
            return 0;
        });
    }

    private static Method StateListDrawable_getStateDrawableMethod;
    private static Field BitmapDrawable_mColorFilter;

    public static void saveAutoNightThemeConfig() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putInt("selectedAutoNightType", selectedAutoNightType);
        editor.putBoolean("autoNightScheduleByLocation", autoNightScheduleByLocation);
        editor.putFloat("autoNightBrighnessThreshold", autoNightBrighnessThreshold);
        editor.putInt("autoNightDayStartTime", autoNightDayStartTime);
        editor.putInt("autoNightDayEndTime", autoNightDayEndTime);
        editor.putInt("autoNightSunriseTime", autoNightSunriseTime);
        editor.putString("autoNightCityName", autoNightCityName);
        editor.putInt("autoNightSunsetTime", autoNightSunsetTime);
        editor.putLong("autoNightLocationLatitude3", Double.doubleToRawLongBits(autoNightLocationLatitude));
        editor.putLong("autoNightLocationLongitude3", Double.doubleToRawLongBits(autoNightLocationLongitude));
        editor.putInt("autoNightLastSunCheckDay", autoNightLastSunCheckDay);
        if (currentNightTheme != null) {
            editor.putString("nighttheme", currentNightTheme.getKey());
        } else {
            editor.remove("nighttheme");
        }
        editor.commit();
    }

    @SuppressLint("PrivateApi")
    private static Drawable getStateDrawable(Drawable drawable, int index) {
        if (Build.VERSION.SDK_INT >= 29 && drawable instanceof StateListDrawable) {
            return ((StateListDrawable) drawable).getStateDrawable(index);
        } else {
            if (StateListDrawable_getStateDrawableMethod == null) {
                try {
                    StateListDrawable_getStateDrawableMethod = StateListDrawable.class.getDeclaredMethod("getStateDrawable", int.class);
                } catch (Throwable ignore) {

                }
            }
            if (StateListDrawable_getStateDrawableMethod == null) {
                return null;
            }
            try {
                return (Drawable) StateListDrawable_getStateDrawableMethod.invoke(drawable, index);
            } catch (Exception ignore) {

            }
            return null;
        }
    }

    public static Drawable createEmojiIconSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = Theme.getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.setEnterFadeDuration(1);
        stateListDrawable.setExitFadeDuration(200);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, defaultDrawable);
        return stateListDrawable;
    }

    public static Drawable createEditTextDrawable(Context context, boolean alert) {
        return createEditTextDrawable(context, getColor(alert ? key_dialogInputField : key_windowBackgroundWhiteInputField), getColor(alert ? key_dialogInputFieldActivated : key_windowBackgroundWhiteInputFieldActivated));
    }

    public static Drawable createEditTextDrawable(Context context, int color, int colorActivated) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(R.drawable.search_dark).mutate();
        defaultDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        Drawable pressedDrawable = resources.getDrawable(R.drawable.search_dark_activated).mutate();
        pressedDrawable.setColorFilter(new PorterDuffColorFilter(colorActivated, PorterDuff.Mode.MULTIPLY));
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = Theme.getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_focused}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_focused}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static boolean canStartHolidayAnimation() {
        return canStartHolidayAnimation;
    }

    public static int getEventType() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        int monthOfYear = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int minutes = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        int eventType = -1;
        if (monthOfYear == 11 && dayOfMonth >= 24 && dayOfMonth <= 31 || monthOfYear == 0 && dayOfMonth == 1) {
            eventType = 0;
        } else if (monthOfYear == 1 && dayOfMonth == 14) {
            eventType = 1;
        } else if (monthOfYear == 9 && dayOfMonth >= 30 || monthOfYear == 10 && dayOfMonth == 1 && hour < 12) {
            eventType = 2;
        }
        return eventType;
    }

    public static Drawable getCurrentHolidayDrawable() {
        if ((System.currentTimeMillis() - lastHolidayCheckTime) >= 60 * 1000) {
            lastHolidayCheckTime = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int minutes = calendar.get(Calendar.MINUTE);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (monthOfYear == 0 && dayOfMonth == 1 && hour <= 23) {
                canStartHolidayAnimation = true;
            } else {
                canStartHolidayAnimation = false;
            }
            if (dialogs_holidayDrawable == null) {
                if (monthOfYear == 11 && dayOfMonth >= (BuildVars.DEBUG_PRIVATE_VERSION ? 29 : 31) && dayOfMonth <= 31 || monthOfYear == 0 && dayOfMonth == 1) {
                    dialogs_holidayDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.newyear);
                    dialogs_holidayDrawableOffsetX = -dp(3);
                    dialogs_holidayDrawableOffsetY = -dp(-7);
                }
            }
        }
        return dialogs_holidayDrawable;
    }

    public static int getCurrentHolidayDrawableXOffset() {
        return dialogs_holidayDrawableOffsetX;
    }

    public static int getCurrentHolidayDrawableYOffset() {
        return dialogs_holidayDrawableOffsetY;
    }

    public static Drawable createSimpleSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = Theme.getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static ShapeDrawable createCircleDrawable(int size, int color) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.setIntrinsicWidth(size);
        defaultDrawable.setIntrinsicHeight(size);
        defaultDrawable.getPaint().setColor(color);
        return defaultDrawable;
    }

    public static ShapeDrawable createCircleDrawable(int size, int colorTop, int colorBottom) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.setIntrinsicWidth(size);
        defaultDrawable.setIntrinsicHeight(size);
        LinearGradient gradient = new LinearGradient(
            0, 0, 0, size,
            colorTop, colorBottom,
            Shader.TileMode.CLAMP
        );
        defaultDrawable.getPaint().setShader(gradient);
        return defaultDrawable;
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes) {
        return createCircleDrawableWithIcon(size, iconRes, 0);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes, int stroke) {
        Drawable drawable;
        if (iconRes != 0) {
            drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        } else {
            drawable = null;
        }
        return createCircleDrawableWithIcon(size, drawable, stroke);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, Drawable drawable, int stroke) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        Paint paint = defaultDrawable.getPaint();
        paint.setColor(0xffffffff);
        if (stroke == 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
        } else if (stroke == 2) {
            paint.setAlpha(0);
        }
        CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
        combinedDrawable.setCustomSize(size, size);
        return combinedDrawable;
    }

    public static Drawable createRoundRectDrawableWithIcon(int rad, int iconRes) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(0xffffffff);
        Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        return new CombinedDrawable(defaultDrawable, drawable);
    }

    public static int getWallpaperColor(int color) {
        if (color == 0) {
            return 0;
        }
        return color | 0xff000000;
    }

    public static float getThemeIntensity(float value) {
        if (value < 0 && !getActiveTheme().isDark()) {
            return -value;
        }
        return value;
    }

    public static void setCombinedDrawableColor(Drawable combinedDrawable, int color, boolean isIcon) {
        if (!(combinedDrawable instanceof CombinedDrawable)) {
            return;
        }
        Drawable drawable;
        if (isIcon) {
            drawable = ((CombinedDrawable) combinedDrawable).getIcon();
        } else {
            drawable = ((CombinedDrawable) combinedDrawable).getBackground();
        }
        if (drawable instanceof ColorDrawable) {
            ((ColorDrawable) drawable).setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static Drawable createSimpleSelectorCircleDrawable(int size, int defaultColor, int pressedColor) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(ovalShape);
        if (Build.VERSION.SDK_INT >= 21) {
            pressedDrawable.getPaint().setColor(0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{pressedColor}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, defaultDrawable, pressedDrawable);
        } else {
            pressedDrawable.getPaint().setColor(pressedColor);
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_focused}, pressedDrawable);
            stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
            return stateListDrawable;
        }
    }

    public static ShapeDrawable createRoundRectDrawable(int rad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static ShapeDrawable createRoundRectDrawable(int topRad, int bottomRad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{topRad, topRad, topRad, topRad, bottomRad, bottomRad, bottomRad, bottomRad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static Drawable createServiceDrawable(int rad, View view, View containerView) {
        return createServiceDrawable(rad, view, containerView, chat_actionBackgroundPaint);
    }

    public static Drawable createServiceDrawable(int rad, View view, View containerView, Paint backgroundPaint) {
        return createServiceDrawable(rad, view, containerView, backgroundPaint, null);
    }

    public static Drawable createServiceDrawable(int rad, View view, View containerView, Theme.ResourcesProvider resourcesProvider) {
        return createServiceDrawable(rad, view, containerView, null, resourcesProvider);
    }

    public static Drawable createServiceDrawable(int rad, View view, View containerView, Paint backgroundPaint, Theme.ResourcesProvider resourcesProvider) {
        return new Drawable() {

            private RectF rect = new RectF();

            @Override
            public void draw(@NonNull Canvas canvas) {
                Rect bounds = getBounds();
                rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
                applyServiceShaderMatrixForView(view, containerView);
                canvas.drawRoundRect(rect, rad, rad, backgroundPaint != null ? backgroundPaint : Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider));
                if (resourcesProvider != null ? resourcesProvider.hasGradientService() : hasGradientService()) {
                    canvas.drawRoundRect(rect, rad, rad, Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, resourcesProvider));
                }
            }

            @Override
            public void setAlpha(int alpha) {

            }

            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {

            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        };
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor) {
        return createSimpleSelectorRoundRectDrawable(rad, defaultColor, pressedColor, pressedColor);
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor, int maskColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        pressedDrawable.getPaint().setColor(maskColor);
        if (Build.VERSION.SDK_INT >= 21) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{pressedColor}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, defaultDrawable, pressedDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
            stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
            return stateListDrawable;
        }
    }

    public static Drawable createSelectorDrawableFromDrawables(Drawable normal, Drawable pressed) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressed);
        stateListDrawable.addState(StateSet.WILD_CARD, normal);
        return stateListDrawable;
    }

    public static Drawable getRoundRectSelectorDrawable(int color) {
        return getRoundRectSelectorDrawable(dp(3), color);
    }

    public static Drawable getRoundRectSelectorDrawable(int corners, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = createRoundRectDrawable(corners, 0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{(color & 0x00ffffff) | 0x19000000}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, createRoundRectDrawable(corners, (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, createRoundRectDrawable(corners, (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static Drawable createSelectorWithBackgroundDrawable(int backgroundColor, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = new ColorDrawable(backgroundColor);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, new ColorDrawable(backgroundColor), maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(backgroundColor));
            return stateListDrawable;
        }
    }

    public static Drawable getSelectorDrawable(boolean whiteBackground) {
        return getSelectorDrawable(getColor(key_listSelector), whiteBackground);
    }
    public static Drawable getSelectorDrawable(boolean whiteBackground, ResourcesProvider resourcesProvider) {
        return getSelectorDrawable(getColor(key_listSelector, resourcesProvider), whiteBackground);
    }

    public static Drawable getSelectorDrawable(int color, boolean whiteBackground) {
        if (whiteBackground) {
            return getSelectorDrawable(color, key_windowBackgroundWhite);
        } else {
            return createSelectorDrawable(color, 2);
        }
    }

    public static Drawable getSelectorDrawable(int color, int backgroundColor) {
        if (backgroundColor >= 0) {
            if (Build.VERSION.SDK_INT >= 21) {
                Drawable maskDrawable = new ColorDrawable(0xffffffff);
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                );
                return new BaseCell.RippleDrawableSafe(colorStateList, new ColorDrawable(getColor(backgroundColor)), maskDrawable);
            } else {
                StateListDrawable stateListDrawable = new StateListDrawable();
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
                stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
                stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(getColor(backgroundColor)));
                return stateListDrawable;
            }
        } else {
            return createSelectorDrawable(color, 2);
        }
    }

    public static Drawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, RIPPLE_MASK_CIRCLE_20DP, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType) {
        return createSelectorDrawable(color, maskType, -1);
    }

    public static final int RIPPLE_MASK_CIRCLE_20DP = 1;
    public static final int RIPPLE_MASK_ALL = 2;
    public static final int RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE = 3;
    public static final int RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER = 4;
    public static final int RIPPLE_MASK_CIRCLE_AUTO = 5;
    public static final int RIPPLE_MASK_ROUNDRECT_6DP = 7;

    public static Drawable createSelectorDrawable(int color, int maskType, int radius) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = null;
            if ((maskType == RIPPLE_MASK_CIRCLE_20DP || maskType == 5) && Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else if (
                maskType == RIPPLE_MASK_CIRCLE_20DP ||
                maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE ||
                maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER ||
                maskType == RIPPLE_MASK_CIRCLE_AUTO ||
                maskType == 6 ||
                maskType == RIPPLE_MASK_ROUNDRECT_6DP
            ) {
                maskPaint.setColor(0xffffffff);
                maskDrawable = new Drawable() {

                    RectF rect;

                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        if (maskType == RIPPLE_MASK_ROUNDRECT_6DP) {
                            if (rect == null) {
                                rect = new RectF();
                            }
                            rect.set(bounds);
                            float rad = radius <= 0 ? dp(6) : radius;
                            canvas.drawRoundRect(rect, rad, rad, maskPaint);
                        } else {
                            int rad;
                            if (maskType == RIPPLE_MASK_CIRCLE_20DP || maskType == 6) {
                                rad = radius <= 0 ? dp(20) : radius;
                            } else if (maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE) {
                                rad = (Math.max(bounds.width(), bounds.height()) / 2);
                            } else {
                                // RIPPLE_MASK_CIRCLE_AUTO = 5
                                // RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER = 4
                                rad = (int) Math.ceil(Math.sqrt((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())));
                            }
                            canvas.drawCircle(bounds.centerX(), bounds.centerY(), rad, maskPaint);
                        }
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            } else if (maskType == RIPPLE_MASK_ALL) {
                maskDrawable = new ColorDrawable(0xffffffff);
            }
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{ StateSet.WILD_CARD },
                new int[]{ color }
            );
            RippleDrawable rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
            if (Build.VERSION.SDK_INT >= 23) {
                if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
                    rippleDrawable.setRadius(radius <= 0 ? dp(20) : radius);
                } else if (maskType == RIPPLE_MASK_CIRCLE_AUTO) {
                    rippleDrawable.setRadius(RippleDrawable.RADIUS_AUTO);
                }
            }
            return rippleDrawable;
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static Drawable createCircleSelectorDrawable(int color, int leftInset, int rightInset) {
        if (Build.VERSION.SDK_INT >= 21) {
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new Drawable() {

                @Override
                public void draw(Canvas canvas) {
                    android.graphics.Rect bounds = getBounds();
                    final int rad = (Math.max(bounds.width(), bounds.height()) / 2) + leftInset + rightInset;
                    canvas.drawCircle(bounds.centerX() - leftInset + rightInset, bounds.centerY(), rad, maskPaint);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.UNKNOWN;
                }
            };
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    /**
     * AdaptiveRipple implements methods for creating ripple drawables
     * Ripple color is based on background color, shifting saturation and value
     *
     * There are four public static methods:
     *  circle(String|int backgroundColor?, float radius?)
     *      - creates ripple drawable for circle buttons, draws only ripples
     *        radius (by default) can be RADIUS_TO_BOUNDS (-1): will be calculated to touch bounds edges
     *        radius can be RADIUS_OUT_BOUNDS (-2): will be calculated to touch bounds corners (fill entire bounds)
     *        otherwise radius is described in dp
     *  filledCircle(String|Drawable|int background?, String|int backgroundColor?, float radius?)
     *      - creates ripple drawable for circle buttons, draws ripples and background
     *        (!) it's not recommended to use only drawable background, as it would be impossible to get background color
     *          (there would be an attempt to cast drawable to ColorDrawable to retrieve a color)
     *  rect(String|int backgroundColor?, ...float radii?)
     *      - creates ripple drawable for rectangular buttons, draws only ripples
     *        radii array is allowed to have 0, 1, 2, 3, 4, 8 elements:
     *        0 - no rounding
     *        1 - all roundings
     *        2 - top, bottom roundings
     *        3 - top-left, top-right, bottom roundings
     *        4 - top-left, top-right, bottom-right, bottom-left roundings
     *        8 - top-left-x, top-left-y, top-right-x, top-right-y,
     *            bottom-right-x, bottom-right-y, bottom-left-x, bottom-left-y roundings
     *        in case it has more, would be downgraded to nearest (ex.: 5 -> 4, 10 -> 8)
     *   filledRect(String|Drawable|int background?, String|int backgroundColor?, ...float radii?)
     *      - creates ripple drawable for rectangular buttons, draws ripples and background
     *
     *  by default, backgroundColor is getColor(Theme.key_windowBackgroundWhite)
     */
    public static class AdaptiveRipple {

        public static final float RADIUS_TO_BOUNDS = -1;
        public static final float RADIUS_OUT_BOUNDS = -2;

        private static final int defaultBackgroundColorKey = Theme.key_windowBackgroundWhite;

        public static Drawable circle() {
            return circle(Theme.getColor(defaultBackgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable circleByKey(int backgroundColorKey) {
            return circle(Theme.getColor(backgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable circleByKey(int backgroundColorKey, float radius) {
            return circle(Theme.getColor(backgroundColorKey), radius);
        }
        public static Drawable circle(int backgroundColor) {
            return circle(backgroundColor, RADIUS_TO_BOUNDS);
        }
        public static Drawable circle(int backgroundColor, float radius) {
            return createCircle(calcRippleColor(backgroundColor), radius);
        }

        public static Drawable filledCircle() {
            return filledCircle(null, Theme.getColor(defaultBackgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable filledCircle(Drawable background) {
            return filledCircle(background, Theme.getColor(defaultBackgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable filledCircleByKey(int backgroundColorKey) {
            return filledCircle(null, Theme.getColor(backgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable filledCircle(Drawable background, int backgroundColorKey) {
            return filledCircle(background, Theme.getColor(backgroundColorKey), RADIUS_TO_BOUNDS);
        }
        public static Drawable filledCircleByKey(int backgroundColorKey, float radius) {
            return filledCircle(null, Theme.getColor(backgroundColorKey), radius);
        }
        public static Drawable filledCircleByKey(Drawable background, int backgroundColorKey, float radius) {
            return filledCircle(background, Theme.getColor(backgroundColorKey), radius);
        }
        public static Drawable filledCircle(int backgroundColor) {
            return filledCircle(null, backgroundColor, RADIUS_TO_BOUNDS);
        }
        public static Drawable filledCircle(int backgroundColor, float radius) {
            return filledCircle(null, backgroundColor, radius);
        }
        public static Drawable filledCircle(Drawable background, int backgroundColor, float radius) {
            return createCircle(background, calcRippleColor(backgroundColor), radius);
        }

        public static Drawable rect() {
            return rect(Theme.getColor(defaultBackgroundColorKey));
        }
        public static Drawable rectByKey(int backgroundColorKey) {
            return rect(Theme.getColor(backgroundColorKey));
        }

        public static Drawable rectByKey(int backgroundColorKey, float ...radii) {
            return rect(Theme.getColor(backgroundColorKey), radii);
        }
        public static Drawable rect(int backgroundColor) {
            return rect(backgroundColor, 0);
        }
        public static Drawable rect(int backgroundColor, float ...radii) {
            return createRect(0, calcRippleColor(backgroundColor), radii);
        }

        public static Drawable filledRect() {
            return filledRect(Theme.getColor(defaultBackgroundColorKey), 0);
        }
        public static Drawable filledRect(Drawable background) {
            int backgroundColor = background instanceof ColorDrawable ? ((ColorDrawable) background).getColor() : Theme.getColor(defaultBackgroundColorKey);
            return filledRect(background, backgroundColor, 0);
        }
        public static Drawable filledRectByKey(int backgroundColorKey) {
            return filledRect(Theme.getColor(backgroundColorKey));
        }
        public static Drawable filledRectByKey(Drawable background, int backgroundColorKey) {
            return filledRect(background, Theme.getColor(backgroundColorKey));
        }
        public static Drawable filledRectByKey(int backgroundColorKey, float ...radii) {
            return filledRect(Theme.getColor(backgroundColorKey), radii);
        }
        public static Drawable filledRectByKey(Drawable background, int backgroundColorKey, float ...radii) {
            return filledRect(background, Theme.getColor(backgroundColorKey), radii);
        }
        public static Drawable filledRect(int backgroundColor) {
            return createRect(backgroundColor, calcRippleColor(backgroundColor));
        }
        public static Drawable filledRect(int backgroundColor, float ...radii) {
            return createRect(backgroundColor, calcRippleColor(backgroundColor), radii);
        }
        public static Drawable filledRect(Drawable background, int backgroundColor, float ...radii) {
            return createRect(background, calcRippleColor(backgroundColor), radii);
        }

        public static Drawable createRect(int rippleColor, float ...radii) {
            return createRect(0, rippleColor, radii);
        }
        public static Drawable createRect(int backgroundColor, int rippleColor, float ...radii) {
            Drawable background = null;
            if (backgroundColor != 0) {
                if (hasNonzeroRadii(radii)) {
                    background = new ShapeDrawable(new RoundRectShape(calcRadii(radii), null, null));
                    ((ShapeDrawable) background).getPaint().setColor(backgroundColor);
                } else {
                    background = new ColorDrawable(backgroundColor);
                }
            }
            return createRect(
                background,
                rippleColor,
                radii
            );
        }
        private static Drawable createRect(Drawable background, int rippleColor, float ...radii) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Drawable maskDrawable = null;
                if (hasNonzeroRadii(radii)) {
                    maskDrawable = new ShapeDrawable(new RoundRectShape(calcRadii(radii), null, null));
                    ((ShapeDrawable) maskDrawable).getPaint().setColor(0xffffffff);
                } else {
                    maskDrawable = new ShapeDrawable(new RectShape());
                    ((ShapeDrawable) maskDrawable).getPaint().setColor(0xffffffff);
                }
                return new BaseCell.RippleDrawableSafe(
                    new ColorStateList(
                        new int[][]{ StateSet.WILD_CARD },
                        new int[]{ rippleColor }
                    ),
                    background,
                    maskDrawable
                );
            } else {
                StateListDrawable stateListDrawable = new StateListDrawable();
                Drawable ripple;
                if (hasNonzeroRadii(radii)) {
                    ripple = new ShapeDrawable(new RoundRectShape(calcRadii(radii), null, null));
                    ((ShapeDrawable) ripple).getPaint().setColor(rippleColor);
                } else {
                    ripple = new ShapeDrawable(new RectShape());
                    ((ShapeDrawable) ripple).getPaint().setColor(rippleColor);
                }
                Drawable pressed = background == null ? ripple : new LayerDrawable(new Drawable[] { background, ripple });
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressed);
                stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressed);
                stateListDrawable.addState(StateSet.WILD_CARD, background);
                return stateListDrawable;
            }
        }

        private static Drawable createCircle(int rippleColor) {
            return createCircle(0, rippleColor, RADIUS_TO_BOUNDS);
        }
        private static Drawable createCircle(int rippleColor, float radius) {
            return createCircle(0, rippleColor, radius);
        }
        private static Drawable createCircle(int backgroundColor, int rippleColor, float radius) {
            return createCircle(
                backgroundColor == 0 ? null : new CircleDrawable(radius, backgroundColor),
                rippleColor,
                radius
            );
        }
        private static Drawable createCircle(Drawable background, int rippleColor, float radius) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return new BaseCell.RippleDrawableSafe(
                    new ColorStateList(
                        new int[][]{ StateSet.WILD_CARD },
                        new int[]{ rippleColor }
                    ),
                    background,
                    new CircleDrawable(radius)
                );
            } else {
                StateListDrawable stateListDrawable = new StateListDrawable();
                Drawable ripple = new CircleDrawable(radius, rippleColor);
                Drawable pressed = background == null ? ripple : new LayerDrawable(new Drawable[] { background, ripple });
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressed);
                stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressed);
                stateListDrawable.addState(StateSet.WILD_CARD, background);
                return stateListDrawable;
            }
        }

        private static class CircleDrawable extends Drawable {

            private static Paint maskPaint;
            private Paint paint;
            private float radius;

            public CircleDrawable(float radius) {
                this.radius = radius;
                if (this.maskPaint == null) {
                    this.maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    this.maskPaint.setColor(0xffffffff);
                }
                this.paint = this.maskPaint;
            }

            public CircleDrawable(float radius, int paintColor) {
                this.radius = radius;
                this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                this.paint.setColor(paintColor);
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float rad;
                if (Math.abs(radius - RADIUS_TO_BOUNDS) < 0.01f) {
                    rad = Math.max(bounds.width(), bounds.height()) / 2;
                } else if (Math.abs(radius - RADIUS_OUT_BOUNDS) < 0.01f) {
                    rad = (int) Math.ceil(Math.sqrt((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())));
                } else {
                    rad = dp(radius);
                }
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), rad, paint);
            }

            @Override
            public void setAlpha(int i) {}
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Deprecated @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }
        }

        private static float[] calcRadii(float ...radii) {
            if (radii.length == 0) {
                return new float[] { 0, 0, 0, 0, 0, 0, 0, 0 };
            } else if (radii.length == 1) {
                return new float[] { dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]) };
            } else if (radii.length == 2) {
                return new float[] { dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(radii[1]), dp(radii[1]), dp(radii[1]) };
            } else if (radii.length == 3) {
                return new float[] { dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(radii[1]), dp(radii[2]), dp(radii[2]), dp(radii[2]), dp(radii[2]) };
            } else if (radii.length < 8) {
                return new float[] { dp(radii[0]), dp(radii[0]), dp(radii[1]), dp(radii[1]), dp(radii[2]), dp(radii[2]), dp(radii[3]), dp(radii[3]) };
            } else {
                return new float[] { dp(radii[0]), dp(radii[1]), dp(radii[2]), dp(radii[3]), dp(radii[4]), dp(radii[5]), dp(radii[6]), dp(radii[7]) };
            }
        }
        private static boolean hasNonzeroRadii(float ...radii) {
            for (int i = 0; i < Math.min(8, radii.length); ++i) {
                if (radii[i] > 0) {
                    return true;
                }
            }
            return false;
        }

        private static float[] tempHSV;
        public static int calcRippleColor(int backgroundColor) {
            if (tempHSV == null) {
                tempHSV = new float[3];
            }
            Color.colorToHSV(backgroundColor, tempHSV);
            if (tempHSV[1] > 0.01f) {
                // when saturation is too low, hue is ignored
                // so changing saturation at that point would reveal ignored hue (usually red, hue=0)
                tempHSV[1] = Math.min(1, Math.max(0, tempHSV[1] + (isCurrentThemeDark() ? .25f : -.25f)));
                tempHSV[2] = Math.min(1, Math.max(0, tempHSV[2] + (isCurrentThemeDark() ? .05f : -.05f)));
            } else {
                tempHSV[2] = Math.min(1, Math.max(0, tempHSV[2] + (isCurrentThemeDark() ? .1f : -.1f)));
            }
            return Color.HSVToColor(127, tempHSV);
        }
    }

    public static class RippleRadMaskDrawable extends Drawable {
        private Path path = new Path();
        private float[] radii = new float[8];
        boolean invalidatePath = true;

        public RippleRadMaskDrawable(float top, float bottom) {
            radii[0] = radii[1] = radii[2] = radii[3] = dp(top);
            radii[4] = radii[5] = radii[6] = radii[7] = dp(bottom);
        }
        public RippleRadMaskDrawable(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            radii[0] = radii[1] = dp(topLeft);
            radii[2] = radii[3] = dp(topRight);
            radii[4] = radii[5] = dp(bottomRight);
            radii[6] = radii[7] = dp(bottomLeft);
        }

        public void setRadius(float top, float bottom) {
            radii[0] = radii[1] = radii[2] = radii[3] = dp(top);
            radii[4] = radii[5] = radii[6] = radii[7] = dp(bottom);
            invalidatePath = true;
            invalidateSelf();
        }
        public void setRadius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            radii[0] = radii[1] = dp(topLeft);
            radii[2] = radii[3] = dp(topRight);
            radii[4] = radii[5] = dp(bottomRight);
            radii[6] = radii[7] = dp(bottomLeft);
            invalidatePath = true;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            invalidatePath = true;
        }

        @Override
        public void draw(Canvas canvas) {
            if (invalidatePath) {
                invalidatePath = false;
                path.reset();
                AndroidUtilities.rectTmp.set(getBounds());
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            }
            canvas.drawPath(path, maskPaint);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    }

    public static void setMaskDrawableRad(Drawable rippleDrawable, int top, int bottom) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        if (rippleDrawable instanceof RippleDrawable) {
            RippleDrawable drawable = (RippleDrawable) rippleDrawable;
            int count = drawable.getNumberOfLayers();
            for (int a = 0; a < count; a++) {
                Drawable layer = drawable.getDrawable(a);
                if (layer instanceof RippleRadMaskDrawable) {
                    ((RippleRadMaskDrawable) layer).setRadius(top, bottom);
                    break;
                }
            }
        }
    }

    public static void setMaskDrawableRad(Drawable rippleDrawable, float topLeftRad, float topRightRad, float bottomRightRad, float bottomLeftRad) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        if (rippleDrawable instanceof RippleDrawable) {
            RippleDrawable drawable = (RippleDrawable) rippleDrawable;
            int count = drawable.getNumberOfLayers();
            for (int a = 0; a < count; a++) {
                Drawable layer = drawable.getDrawable(a);
                if (layer instanceof RippleRadMaskDrawable) {
                    ((RippleRadMaskDrawable) layer).setRadius(topLeftRad, topRightRad, bottomRightRad, bottomLeftRad);
                    break;
                }
            }
        }
    }

    public static Drawable createRadSelectorDrawable(int color, int topRad, int bottomRad) {
        if (Build.VERSION.SDK_INT >= 21) {
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new RippleRadMaskDrawable(topRad, bottomRad);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static Drawable createRadSelectorDrawable(int color, int rippleColor, int topRad, int bottomRad) {
        if (Build.VERSION.SDK_INT >= 21) {
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new RippleRadMaskDrawable(topRad, bottomRad);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{rippleColor}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, createRoundRectDrawable(dp(topRad), dp(bottomRad), color), maskDrawable);
        } else {
            Drawable backgroundDrawable = createRoundRectDrawable(dp(topRad), dp(bottomRad), color);
            Drawable pressedDrawable = new LayerDrawable(new Drawable[]{backgroundDrawable, createRoundRectDrawable(dp(topRad), dp(bottomRad), rippleColor)});
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
            stateListDrawable.addState(StateSet.WILD_CARD, backgroundDrawable);
            return stateListDrawable;
        }
    }

    public static Drawable createRadSelectorDrawable(int color, int topLeftRad, int topRightRad, int bottomRightRad, int bottomLeftRad) {
        if (Build.VERSION.SDK_INT >= 21) {
            maskPaint.setColor(0xffffffff);
            Drawable maskDrawable = new RippleRadMaskDrawable(topLeftRad, topRightRad, bottomRightRad, bottomLeftRad);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static void applyPreviousTheme() {
        if (previousTheme == null) {
            return;
        }
        hasPreviousTheme = false;
        if (isInNigthMode && currentNightTheme != null) {
            applyTheme(currentNightTheme, true, false, true);
        } else if (!isApplyingAccent) {
            applyTheme(previousTheme, true, false, false);
        }
        isApplyingAccent = false;
        previousTheme = null;
        checkAutoNightThemeConditions();
    }

    public static void clearPreviousTheme() {
        if (previousTheme == null) {
            return;
        }
        hasPreviousTheme = false;
        isApplyingAccent = false;
        previousTheme = null;
    }

    private static void sortThemes() {
        Collections.sort(themes, (o1, o2) -> {
            if (o1.pathToFile == null && o1.assetName == null) {
                return -1;
            } else if (o2.pathToFile == null && o2.assetName == null) {
                return 1;
            }
            return o1.name.compareTo(o2.name);
        });
    }

    public static void applyThemeTemporary(ThemeInfo themeInfo, boolean accent) {
        previousTheme = getCurrentTheme();
        hasPreviousTheme = true;
        isApplyingAccent = accent;
        applyTheme(themeInfo, false, false, false);
    }

    public static boolean hasCustomWallpaper() {
        return isApplyingAccent && currentTheme.overrideWallpaper != null;
    }

    public static boolean isCustomWallpaperColor() {
        return hasCustomWallpaper() && currentTheme.overrideWallpaper.color != 0;
    }

    public static void resetCustomWallpaper(boolean temporary) {
        if (temporary) {
            isApplyingAccent = false;
            reloadWallpaper(true);
        } else {
            currentTheme.setOverrideWallpaper(null);
        }
    }

    public static ThemeInfo fillThemeValues(File file, String themeName, TLRPC.TL_theme theme) {
        try {
            ThemeInfo themeInfo = new ThemeInfo();
            themeInfo.name = themeName;
            themeInfo.info = theme;
            themeInfo.pathToFile = file.getAbsolutePath();
            themeInfo.account = UserConfig.selectedAccount;

            String[] wallpaperLink = new String[1];
            SparseIntArray colors = getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
            checkIsDark(colors, themeInfo);

            if (!TextUtils.isEmpty(wallpaperLink[0])) {
                String link = wallpaperLink[0];
                themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(link) + ".wp").getAbsolutePath();
                try {
                    Uri data = Uri.parse(link);
                    themeInfo.slug = data.getQueryParameter("slug");
                    String mode = data.getQueryParameter("mode");
                    if (mode != null) {
                        mode = mode.toLowerCase();
                        String[] modes = mode.split(" ");
                        if (modes != null && modes.length > 0) {
                            for (int a = 0; a < modes.length; a++) {
                                if ("blur".equals(modes[a])) {
                                    themeInfo.isBlured = true;
                                } else if ("motion".equals(modes[a])) {
                                    themeInfo.isMotion = true;
                                }
                            }
                        }
                    }
                    String intensity = data.getQueryParameter("intensity");
                    if (!TextUtils.isEmpty(intensity)) {
                        try {
                            String bgColor = data.getQueryParameter("bg_color");
                            if (!TextUtils.isEmpty(bgColor)) {
                                themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                    themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                }
                                if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                    themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                }
                                if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                    themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                }
                            }
                        } catch (Exception ignore) {

                        }
                        try {
                            String rotation = data.getQueryParameter("rotation");
                            if (!TextUtils.isEmpty(rotation)) {
                                themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                            }
                        } catch (Exception ignore) {

                        }

                        if (!TextUtils.isEmpty(intensity)) {
                            themeInfo.patternIntensity = Utilities.parseInt(intensity);
                        }
                        if (themeInfo.patternIntensity == 0) {
                            themeInfo.patternIntensity = 50;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                themedWallpaperLink = null;
            }

            return themeInfo;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static ThemeInfo applyThemeFile(File file, String themeName, TLRPC.TL_theme theme, boolean temporary) {
        try {
            if (!themeName.toLowerCase().endsWith(".attheme")) {
                themeName += ".attheme";
            }
            if (temporary) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.goingToPreviewTheme);
                ThemeInfo themeInfo = new ThemeInfo();
                themeInfo.name = themeName;
                themeInfo.info = theme;
                themeInfo.pathToFile = file.getAbsolutePath();
                themeInfo.account = UserConfig.selectedAccount;
                applyThemeTemporary(themeInfo, false);
                return themeInfo;
            } else {
                String key;
                File finalFile;
                if (theme != null) {
                    key = "remote" + theme.id;
                    finalFile = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme");
                } else {
                    key = themeName;
                    finalFile = new File(ApplicationLoader.getFilesDirFixed(), key);
                }
                if (!AndroidUtilities.copyFile(file, finalFile)) {
                    applyPreviousTheme();
                    return null;
                }

                previousTheme = null;
                hasPreviousTheme = false;
                isApplyingAccent = false;

                ThemeInfo themeInfo = themesDict.get(key);
                if (themeInfo == null) {
                    themeInfo = new ThemeInfo();
                    themeInfo.name = themeName;
                    themeInfo.account = UserConfig.selectedAccount;
                    themes.add(themeInfo);
                    otherThemes.add(themeInfo);
                    sortThemes();
                } else {
                    themesDict.remove(key);
                }
                themeInfo.info = theme;
                themeInfo.pathToFile = finalFile.getAbsolutePath();
                themesDict.put(themeInfo.getKey(), themeInfo);
                saveOtherThemes(true);

                applyTheme(themeInfo, true, true, false);
                return themeInfo;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static ThemeInfo getTheme(String key) {
        return themesDict.get(key);
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, true, false);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean nightTheme) {
        applyTheme(themeInfo, true, nightTheme);
    }

    public static void applyThemeInBackground(ThemeInfo themeInfo, boolean nightTheme, Runnable onDone) {
        applyThemeInBackground(themeInfo, true, true, nightTheme, onDone);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean save, boolean nightTheme) {
        applyTheme(themeInfo, save, true, nightTheme);
    }

    private static void applyTheme(ThemeInfo themeInfo, boolean save, boolean removeWallpaperOverride, final boolean nightTheme) {
        if (themeInfo == null) {
            return;
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.destroy();
        }
        try {
            if (themeInfo.pathToFile != null || themeInfo.assetName != null) {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("theme", themeInfo.getKey());
                    editor.apply();
                }
                String[] wallpaperLink = new String[1];
                if (themeInfo.assetName != null) {
                    currentColorsNoAccent = getThemeFileValues(null, themeInfo.assetName, null);
                } else {
                    currentColorsNoAccent = getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
                }
                themedWallpaperFileOffset = currentColorsNoAccent.get(key_wallpaperFileOffset, -1);
                if (!TextUtils.isEmpty(wallpaperLink[0])) {
                    themedWallpaperLink = wallpaperLink[0];
                    String newPathToFile = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(themedWallpaperLink) + ".wp").getAbsolutePath();
                    try {
                        if (themeInfo.pathToWallpaper != null && !themeInfo.pathToWallpaper.equals(newPathToFile)) {
                            new File(themeInfo.pathToWallpaper).delete();
                        }
                    } catch (Exception ignore) {

                    }
                    themeInfo.pathToWallpaper = newPathToFile;
                    try {
                        Uri data = Uri.parse(themedWallpaperLink);
                        themeInfo.slug = data.getQueryParameter("slug");

                        String mode = data.getQueryParameter("mode");
                        if (mode != null) {
                            mode = mode.toLowerCase();
                            String[] modes = mode.split(" ");
                            if (modes != null && modes.length > 0) {
                                for (int a = 0; a < modes.length; a++) {
                                    if ("blur".equals(modes[a])) {
                                        themeInfo.isBlured = true;
                                    } else if ("motion".equals(modes[a])) {
                                        themeInfo.isMotion = true;
                                    }
                                }
                            }
                        }
                        int intensity = Utilities.parseInt(data.getQueryParameter("intensity"));
                        themeInfo.patternBgGradientRotation = 45;
                        try {
                            String bgColor = data.getQueryParameter("bg_color");
                            if (!TextUtils.isEmpty(bgColor)) {
                                themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                    themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                }
                                if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                    themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                }
                                if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                    themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                }
                            }
                        } catch (Exception ignore) {

                        }
                        try {
                            String rotation = data.getQueryParameter("rotation");
                            if (!TextUtils.isEmpty(rotation)) {
                                themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                            }
                        } catch (Exception ignore) {

                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    try {
                        if (themeInfo.pathToWallpaper != null) {
                            new File(themeInfo.pathToWallpaper).delete();
                        }
                    } catch (Exception ignore) {

                    }
                    themeInfo.pathToWallpaper = null;
                    themedWallpaperLink = null;
                }
            } else {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove("theme");
                    editor.commit();
                }
                currentColorsNoAccent.clear();
                themedWallpaperFileOffset = 0;
                themedWallpaperLink = null;
                wallpaper = null;
                themedWallpaper = null;
            }
            if (!nightTheme && previousTheme == null) {
                currentDayTheme = themeInfo;
                if (isCurrentThemeNight()) {
                    switchNightThemeDelay = 2000;
                    lastDelayUpdateTime = SystemClock.elapsedRealtime();
                    AndroidUtilities.runOnUIThread(Theme::checkAutoNightThemeConditions, 2100);
                }
            }
            currentTheme = themeInfo;
            refreshThemeColors();
        } catch (Exception e) {
            FileLog.e(e);
        }
        BlurSettingsBottomSheet.onThemeApplyed();
        if (previousTheme == null && save && !switchingNightTheme) {
            MessagesController.getInstance(themeInfo.account).saveTheme(themeInfo, themeInfo.getAccent(false), nightTheme, false);
        }
    }

    private static void applyThemeInBackground(ThemeInfo themeInfo, boolean save, boolean removeWallpaperOverride, final boolean nightTheme, Runnable onDone) {
        if (themeInfo == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.destroy();
        }
        try {
            if (themeInfo.pathToFile != null || themeInfo.assetName != null) {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("theme", themeInfo.getKey());
                    editor.apply();
                }
                String[] wallpaperLink = new String[1];
                Runnable next = () -> {
                    try {
                        themedWallpaperFileOffset = currentColorsNoAccent.get(key_wallpaperFileOffset, -1);
                        if (!TextUtils.isEmpty(wallpaperLink[0])) {
                            themedWallpaperLink = wallpaperLink[0];
                            String newPathToFile = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(themedWallpaperLink) + ".wp").getAbsolutePath();
                            try {
                                if (themeInfo.pathToWallpaper != null && !themeInfo.pathToWallpaper.equals(newPathToFile)) {
                                    new File(themeInfo.pathToWallpaper).delete();
                                }
                            } catch (Exception ignore) {

                            }
                            themeInfo.pathToWallpaper = newPathToFile;
                            try {
                                Uri data = Uri.parse(themedWallpaperLink);
                                themeInfo.slug = data.getQueryParameter("slug");

                                String mode = data.getQueryParameter("mode");
                                if (mode != null) {
                                    mode = mode.toLowerCase();
                                    String[] modes = mode.split(" ");
                                    if (modes != null && modes.length > 0) {
                                        for (int a = 0; a < modes.length; a++) {
                                            if ("blur".equals(modes[a])) {
                                                themeInfo.isBlured = true;
                                            } else if ("motion".equals(modes[a])) {
                                                themeInfo.isMotion = true;
                                            }
                                        }
                                    }
                                }
                                int intensity = Utilities.parseInt(data.getQueryParameter("intensity"));
                                themeInfo.patternBgGradientRotation = 45;
                                try {
                                    String bgColor = data.getQueryParameter("bg_color");
                                    if (!TextUtils.isEmpty(bgColor)) {
                                        themeInfo.patternBgColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                                        if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                                            themeInfo.patternBgGradientColor1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                                        }
                                        if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                                            themeInfo.patternBgGradientColor2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                                        }
                                        if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                                            themeInfo.patternBgGradientColor3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                                        }
                                    }
                                } catch (Exception ignore) {

                                }
                                try {
                                    String rotation = data.getQueryParameter("rotation");
                                    if (!TextUtils.isEmpty(rotation)) {
                                        themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                                    }
                                } catch (Exception ignore) {

                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        } else {
                            try {
                                if (themeInfo.pathToWallpaper != null) {
                                    new File(themeInfo.pathToWallpaper).delete();
                                }
                            } catch (Exception ignore) {

                            }
                            themeInfo.pathToWallpaper = null;
                            themedWallpaperLink = null;
                        }
                        if (!nightTheme && previousTheme == null) {
                            currentDayTheme = themeInfo;
                            if (isCurrentThemeNight()) {
                                switchNightThemeDelay = 2000;
                                lastDelayUpdateTime = SystemClock.elapsedRealtime();
                                AndroidUtilities.runOnUIThread(Theme::checkAutoNightThemeConditions, 2100);
                            }
                        }
                        currentTheme = themeInfo;
                        refreshThemeColors();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (previousTheme == null && save && !switchingNightTheme) {
                        MessagesController.getInstance(themeInfo.account).saveTheme(themeInfo, themeInfo.getAccent(false), nightTheme, false);
                    }
                    if (onDone != null) {
                        onDone.run();
                    }
                };
                if (themeInfo.assetName != null) {
                    getThemeFileValuesInBackground(null, themeInfo.assetName, null, colors -> {
                        currentColorsNoAccent = colors;
                        next.run();
                    });
                } else {
                    getThemeFileValuesInBackground(new File(themeInfo.pathToFile), null, wallpaperLink, colors -> {
                        currentColorsNoAccent = colors;
                        next.run();
                    });
                }
                return;
            } else {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove("theme");
                    editor.apply();
                }
                currentColorsNoAccent.clear();
                themedWallpaperFileOffset = 0;
                themedWallpaperLink = null;
                wallpaper = null;
                themedWallpaper = null;
            }
            if (!nightTheme && previousTheme == null) {
                currentDayTheme = themeInfo;
                if (isCurrentThemeNight()) {
                    switchNightThemeDelay = 2000;
                    lastDelayUpdateTime = SystemClock.elapsedRealtime();
                    AndroidUtilities.runOnUIThread(Theme::checkAutoNightThemeConditions, 2100);
                }
            }
            currentTheme = themeInfo;
            refreshThemeColors();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (previousTheme == null && save && !switchingNightTheme) {
            MessagesController.getInstance(themeInfo.account).saveTheme(themeInfo, themeInfo.getAccent(false), nightTheme, false);
        }
        if (onDone != null) {
            onDone.run();
        }
    }

    private static boolean useBlackText(int color1, int color2) {
        float r1 = Color.red(color1) / 255.0f;
        float r2 = Color.red(color2) / 255.0f;
        float g1 = Color.green(color1) / 255.0f;
        float g2 = Color.green(color2) / 255.0f;
        float b1 = Color.blue(color1) / 255.0f;
        float b2 = Color.blue(color2) / 255.0f;
        float r = (r1 * 0.5f + r2 * 0.5f);
        float g = (g1 * 0.5f + g2 * 0.5f);
        float b = (b1 * 0.5f + b2 * 0.5f);

        float lightness = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float lightness2 = 0.2126f * r1 + 0.7152f * g1 + 0.0722f * b1;
        return lightness > 0.705f || lightness2 > 0.705f;
    }

    public static void refreshThemeColors() {
        refreshThemeColors(false, false);
    }

    public static void refreshThemeColors(boolean bg, boolean messages) {
        currentColors = currentColorsNoAccent.clone();
        shouldDrawGradientIcons = true;
        ThemeAccent accent = currentTheme.getAccent(false);
        if (accent != null) {
            shouldDrawGradientIcons = accent.fillAccentColors(currentColorsNoAccent, currentColors);
        }
        if (!messages) {
            boolean async = !(LaunchActivity.getLastFragment() instanceof ChatActivity);
            reloadWallpaper(async);
        }
        applyCommonTheme();
        applyDialogsTheme();
        applyProfileTheme();
        applyChatTheme(false, bg);
        boolean checkNavigationBarColor = !hasPreviousTheme;
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, checkNavigationBarColor));
    }

    public static boolean hasHue(int color) {
        float[] hsvTemp3 = getTempHsv(3);
        Color.colorToHSV(color, hsvTemp3);
        return hsvTemp3[1] > .1f && hsvTemp3[1] < .9f;
    }

    public static int changeColorAccent(int themeBaseAccent, int accent, int color, boolean isDark) {
        return changeColorAccent(themeBaseAccent, accent, color, isDark, color);
    }

    public static int changeColorAccent(int themeBaseAccent, int accent, int color, boolean isDark, int fallback) {
        float[] hsvTemp3 = getTempHsv(3);
        float[] hsvTemp4 = getTempHsv(4);
        Color.colorToHSV(themeBaseAccent, hsvTemp3);
        Color.colorToHSV(accent, hsvTemp4);
        return changeColorAccent(hsvTemp3, hsvTemp4, color, isDark, fallback);
    }

    public static int changeColorAccent(ThemeInfo themeInfo, int accent, int color) {
        if (accent == 0 || themeInfo.accentBaseColor == 0 || accent == themeInfo.accentBaseColor || themeInfo.firstAccentIsDefault && themeInfo.currentAccentId == DEFALT_THEME_ACCENT_ID) {
            return color;
        }
        float[] hsvTemp3 = getTempHsv(3);
        float[] hsvTemp4 = getTempHsv(4);

        Color.colorToHSV(themeInfo.accentBaseColor, hsvTemp3);
        Color.colorToHSV(accent, hsvTemp4);
        return changeColorAccent(hsvTemp3, hsvTemp4, color, themeInfo.isDark(), color);
    }

    public static float[] getTempHsv(int num) {
        ThreadLocal<float[]> local;
        switch (num) {
            case 1:
                local = hsvTemp1Local;
                break;
            case 2:
                local = hsvTemp2Local;
                break;
            case 3:
                local = hsvTemp3Local;
                break;
            case 4:
                local = hsvTemp4Local;
                break;
            case 5:
            default:
                local = hsvTemp5Local;
                break;
        }
        float[] hsvTemp = local.get();
        if (hsvTemp == null) {
            hsvTemp = new float[3];
            local.set(hsvTemp);
        }
        return hsvTemp;
    }

    private static int getAccentColor(float[] baseHsv, int baseColor, int elementColor) {
        float[] hsvTemp3 = getTempHsv(3);
        float[] hsvTemp4 = getTempHsv(4);
        Color.colorToHSV(baseColor, hsvTemp3);
        Color.colorToHSV(elementColor, hsvTemp4);

        float dist = Math.min(1.5f * hsvTemp3[1] / baseHsv[1], 1f);

        hsvTemp3[0] = hsvTemp4[0] - hsvTemp3[0] + baseHsv[0];
        hsvTemp3[1] = hsvTemp4[1] * baseHsv[1] / hsvTemp3[1];
        hsvTemp3[2] = (hsvTemp4[2] / hsvTemp3[2] + dist - 1f) * baseHsv[2] / dist;
        if (hsvTemp3[2] < 0.3f) {
            return elementColor;
        }
        return Color.HSVToColor(255, hsvTemp3);
    }

    public static int changeColorAccent(int color) {
        ThemeAccent accent = currentTheme.getAccent(false);
        return changeColorAccent(currentTheme, accent != null ? accent.accentColor : 0, color);
    }

    private static float abs(float a) {
        return a > 0 ? a : -a;
    }

    private static float[] tmpHSV5;
    public static int changeColorAccent(float[] baseHsv, float[] accentHsv, int color, boolean isDarkTheme, int fallback) {
        if (tmpHSV5 == null) {
            tmpHSV5 = new float[3];
        }
        float[] colorHsv = tmpHSV5;
        Color.colorToHSV(color, colorHsv);

        final float diffH = Math.min(abs(colorHsv[0] - baseHsv[0]), abs(colorHsv[0] - baseHsv[0] - 360f));
        if (diffH > 30f) {
            return fallback;
        }

        float dist = Math.min(1.5f * colorHsv[1] / baseHsv[1], 1f);

        colorHsv[0] = colorHsv[0] + accentHsv[0] - baseHsv[0];
        colorHsv[1] = colorHsv[1] * accentHsv[1] / baseHsv[1];
        colorHsv[2] = colorHsv[2] * (1f - dist + dist * accentHsv[2] / baseHsv[2]);

        int newColor = Color.HSVToColor(Color.alpha(color), colorHsv);

        float origBrightness = AndroidUtilities.computePerceivedBrightness(color);
        float newBrightness = AndroidUtilities.computePerceivedBrightness(newColor);

        // We need to keep colors lighter in dark themes and darker in light themes
        boolean needRevertBrightness = isDarkTheme ? origBrightness > newBrightness : origBrightness < newBrightness;

        if (needRevertBrightness) {
            float amountOfNew = 0.6f;
            float fallbackAmount = (1f - amountOfNew) * origBrightness / newBrightness + amountOfNew;
            newColor = changeBrightness(newColor, fallbackAmount);
        }

        return newColor;
    }

    private static int changeBrightness(int color, float amount) {
        int r = (int) (Color.red(color) * amount);
        int g = (int) (Color.green(color) * amount);
        int b = (int) (Color.blue(color) * amount);

        r = r < 0 ? 0 : Math.min(r, 255);
        g = g < 0 ? 0 : Math.min(g, 255);
        b = b < 0 ? 0 : Math.min(b, 255);
        return Color.argb(Color.alpha(color), r, g, b);
    }

    public static void onUpdateThemeAccents() {
        refreshThemeColors();
    }

    public static boolean deleteThemeAccent(ThemeInfo theme, ThemeAccent accent, boolean save) {
        if (accent == null || theme == null || theme.themeAccents == null) {
            return false;
        }
        boolean current = accent.id == theme.currentAccentId;
        File wallpaperFile = accent.getPathToWallpaper();
        if (wallpaperFile != null) {
            wallpaperFile.delete();
        }
        theme.themeAccentsMap.remove(accent.id);
        theme.themeAccents.remove(accent);
        if (accent.info != null) {
            theme.accentsByThemeId.remove(accent.info.id);
        }
        if (accent.overrideWallpaper != null) {
            accent.overrideWallpaper.delete();
        }
        if (current) {
            ThemeAccent themeAccent = theme.themeAccents.get(0);
            theme.setCurrentAccentId(themeAccent.id);
        }
        if (save) {
            saveThemeAccents(theme, true, false, false, false);
            if (accent.info != null) {
                MessagesController.getInstance(accent.account).saveTheme(theme, accent, current && theme == currentNightTheme, true);
            }
        }
        return current;
    }

    public static void saveThemeAccents(ThemeInfo theme, boolean save, boolean remove, boolean indexOnly, boolean upload) {
        saveThemeAccents(theme, save, remove, indexOnly, upload, false);
    }

    public static void saveThemeAccents(ThemeInfo theme, boolean save, boolean remove, boolean indexOnly, boolean upload, boolean migration) {
        if (save) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            if (!indexOnly) {
                int N = theme.themeAccents.size();
                int count = Math.max(0, N - theme.defaultAccentCount);
                SerializedData data = new SerializedData(4 * (count * 16 + 2));
                data.writeInt32(9);
                data.writeInt32(count);
                for (int a = 0; a < N; a++) {
                    ThemeAccent accent = theme.themeAccents.get(a);
                    if (accent.id < 100) {
                        continue;
                    }
                    data.writeInt32(accent.id);
                    data.writeInt32(accent.accentColor);
                    data.writeInt32(accent.accentColor2);
                    data.writeInt32(accent.myMessagesAccentColor);
                    data.writeInt32(accent.myMessagesGradientAccentColor1);
                    data.writeInt32(accent.myMessagesGradientAccentColor2);
                    data.writeInt32(accent.myMessagesGradientAccentColor3);
                    data.writeBool(accent.myMessagesAnimated);
                    data.writeInt64(accent.backgroundOverrideColor);
                    data.writeInt64(accent.backgroundGradientOverrideColor1);
                    data.writeInt64(accent.backgroundGradientOverrideColor2);
                    data.writeInt64(accent.backgroundGradientOverrideColor3);
                    data.writeInt32(accent.backgroundRotation);
                    data.writeInt64(0);
                    data.writeDouble(accent.patternIntensity);
                    data.writeBool(accent.patternMotion);
                    data.writeString(accent.patternSlug);
                    data.writeBool(accent.info != null);
                    if (accent.info != null) {
                        data.writeInt32(accent.account);
                        accent.info.serializeToStream(data);
                    }
                }
                editor.putString("accents_" + theme.assetName, Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING));
                if (!migration) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeAccentListUpdated);
                }
                if (upload) {
                    MessagesController.getInstance(UserConfig.selectedAccount).saveThemeToServer(theme, theme.getAccent(false));
                }
            }
            editor.putInt("accent_current_" + theme.assetName, theme.currentAccentId);
            editor.commit();
        } else {
            if (theme.prevAccentId != -1) {
                if (remove) {
                    ThemeAccent accent = theme.themeAccentsMap.get(theme.currentAccentId);
                    theme.themeAccentsMap.remove(accent.id);
                    theme.themeAccents.remove(accent);
                    if (accent.info != null) {
                        theme.accentsByThemeId.remove(accent.info.id);
                    }
                }
                theme.currentAccentId = theme.prevAccentId;
                ThemeAccent accent = theme.getAccent(false);
                if (accent != null) {
                    theme.overrideWallpaper = accent.overrideWallpaper;
                } else {
                    theme.overrideWallpaper = null;
                }
            }
            if (currentTheme == theme) {
                refreshThemeColors();
            }
        }
        theme.prevAccentId = -1;
    }

    private static void saveOtherThemes(boolean full) {
        saveOtherThemes(full, false);
    }

    private static void saveOtherThemes(boolean full, boolean migration) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if (full) {
            JSONArray array = new JSONArray();
            for (int a = 0; a < otherThemes.size(); a++) {
                JSONObject jsonObject = otherThemes.get(a).getSaveJson();
                if (jsonObject != null) {
                    array.put(jsonObject);
                }
            }
            editor.putString("themes2", array.toString());
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            editor.putLong("2remoteThemesHash" + (a != 0 ? a : ""), remoteThemesHash[a]);
            editor.putInt("lastLoadingThemesTime" + (a != 0 ? a : ""), lastLoadingThemesTime[a]);
        }

        editor.putInt("lastLoadingCurrentThemeTime", lastLoadingCurrentThemeTime);
        editor.commit();

        if (full) {
            for (int b = 0; b < 5; b++) {
                String key;
                switch (b) {
                    case 0:
                        key = "Blue";
                        break;
                    case 1:
                        key = "Dark Blue";
                        break;
                    case 2:
                        key = "Arctic Blue";
                        break;
                    case 3:
                        key = "Day";
                        break;
                    case 4:
                    default:
                        key = "Night";
                        break;
                }
                ThemeInfo info = themesDict.get(key);
                if (info == null || info.themeAccents == null || info.themeAccents.isEmpty()) {
                    continue;
                }
                saveThemeAccents(info, true, false, false, false, migration);
            }
        }
    }

    public static int[] getDefaultColors() {
        return defaultColors;
    }

    public static ThemeInfo getPreviousTheme() {
        return previousTheme;
    }

    public static String getCurrentThemeName() {
        String text = currentDayTheme.getName();
        if (text.toLowerCase().endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        return text;
    }

    public static String getCurrentNightThemeName() {
        if (currentNightTheme == null) {
            return "";
        }
        String text = currentNightTheme.getName();
        if (text.toLowerCase().endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        return text;
    }

    public static ThemeInfo getCurrentTheme() {
        return currentDayTheme != null ? currentDayTheme : defaultTheme;
    }

    public static ThemeInfo getCurrentNightTheme() {
        return currentNightTheme;
    }

    public static boolean isCurrentThemeNight() {
        return currentTheme == currentNightTheme;
    }

    public static boolean isCurrentThemeDark() {
        return currentTheme.isDark();
    }

    public static ThemeInfo getActiveTheme() {
        return currentTheme;
    }

    private static long getAutoNightSwitchThemeDelay() {
        long newTime = SystemClock.elapsedRealtime();
        if (Math.abs(lastThemeSwitchTime - newTime) >= LIGHT_SENSOR_THEME_SWITCH_NEAR_THRESHOLD) {
            return LIGHT_SENSOR_THEME_SWITCH_DELAY;
        }
        return LIGHT_SENSOR_THEME_SWITCH_NEAR_DELAY;
    }

    private static final float MAXIMUM_LUX_BREAKPOINT = 500.0f;
    private static SensorEventListener ambientSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            if (lux <= 0) {
                lux = 0.1f;
            }
            if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                return;
            }
            if (lux > MAXIMUM_LUX_BREAKPOINT) {
                lastBrightnessValue = 1.0f;
            } else {
                lastBrightnessValue = (float) Math.ceil(9.9323f * Math.log(lux) + 27.059f) / 100.0f;
            }
            if (lastBrightnessValue <= autoNightBrighnessThreshold) {
                if (!MediaController.getInstance().isRecordingOrListeningByProximity()) {
                    if (switchDayRunnableScheduled) {
                        switchDayRunnableScheduled = false;
                        AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
                    }
                    if (!switchNightRunnableScheduled) {
                        switchNightRunnableScheduled = true;
                        AndroidUtilities.runOnUIThread(switchNightBrightnessRunnable, getAutoNightSwitchThemeDelay());
                    }
                }
            } else {
                if (switchNightRunnableScheduled) {
                    switchNightRunnableScheduled = false;
                    AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
                }
                if (!switchDayRunnableScheduled) {
                    switchDayRunnableScheduled = true;
                    AndroidUtilities.runOnUIThread(switchDayBrightnessRunnable, getAutoNightSwitchThemeDelay());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public static void setCurrentNightTheme(ThemeInfo theme) {
        boolean apply = currentTheme == currentNightTheme;
        currentNightTheme = theme;
        if (apply) {
            applyDayNightThemeMaybe(true);
        }
    }

    public static void checkAutoNightThemeConditions() {
        checkAutoNightThemeConditions(false);
    }

    public static void cancelAutoNightThemeCallbacks() {
        if (selectedAutoNightType != AUTO_NIGHT_TYPE_AUTOMATIC) {
            if (switchNightRunnableScheduled) {
                switchNightRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
            }
            if (switchDayRunnableScheduled) {
                switchDayRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
            }
            if (lightSensorRegistered) {
                lastBrightnessValue = 1.0f;
                sensorManager.unregisterListener(ambientSensorListener, lightSensor);
                lightSensorRegistered = false;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("light sensor unregistered");
                }
            }
        }
    }

    private static int needSwitchToTheme() {
        if (selectedAutoNightType == AUTO_NIGHT_TYPE_SCHEDULED) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int time = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            int timeStart;
            int timeEnd;
            if (autoNightScheduleByLocation) {
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                if (autoNightLastSunCheckDay != day && autoNightLocationLatitude != 10000 && autoNightLocationLongitude != 10000) {
                    int[] t = SunDate.calculateSunriseSunset(autoNightLocationLatitude, autoNightLocationLongitude);
                    autoNightSunriseTime = t[0];
                    autoNightSunsetTime = t[1];
                    autoNightLastSunCheckDay = day;
                    saveAutoNightThemeConfig();
                }
                timeStart = autoNightSunsetTime;
                timeEnd = autoNightSunriseTime;
            } else {
                timeStart = autoNightDayStartTime;
                timeEnd = autoNightDayEndTime;
            }
            if (timeStart < timeEnd) {
                if (timeStart <= time && time <= timeEnd) {
                    return 2;
                } else {
                    return 1;
                }
            } else {
                if (timeStart <= time && time <= 24 * 60 || 0 <= time && time <= timeEnd) {
                    return 2;
                } else {
                    return 1;
                }
            }
        } else if (selectedAutoNightType == AUTO_NIGHT_TYPE_AUTOMATIC) {
            if (lightSensor == null) {
                sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }
            if (!lightSensorRegistered && lightSensor != null && ambientSensorListener != null) {
                sensorManager.registerListener(ambientSensorListener, lightSensor, 500000);
                lightSensorRegistered = true;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("light sensor registered");
                }
            }
            if (lastBrightnessValue <= autoNightBrighnessThreshold) {
                if (!switchNightRunnableScheduled) {
                    return 2;
                }
            } else {
                if (!switchDayRunnableScheduled) {
                    return 1;
                }
            }
        } else if (selectedAutoNightType == AUTO_NIGHT_TYPE_SYSTEM) {
            Configuration configuration = ApplicationLoader.applicationContext.getResources().getConfiguration();
            int currentNightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            switch (currentNightMode) {
                case Configuration.UI_MODE_NIGHT_NO:
                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    return 1;
                case Configuration.UI_MODE_NIGHT_YES:
                    return 2;
            }
        } else if (selectedAutoNightType == AUTO_NIGHT_TYPE_NONE) {
            return 1;
        }
        return 0;
    }

    public static void setChangingWallpaper(boolean value) {
        changingWallpaper = value;
        if (!changingWallpaper) {
            checkAutoNightThemeConditions(false);
        }
    }

    public static void checkAutoNightThemeConditions(boolean force) {
        if (previousTheme != null || changingWallpaper) {
            return;
        }
        if (!force && switchNightThemeDelay > 0) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastDelayUpdateTime;
            lastDelayUpdateTime = newTime;
            switchNightThemeDelay -= dt;
            if (switchNightThemeDelay > 0) {
                return;
            }
        }
        if (force) {
            if (switchNightRunnableScheduled) {
                switchNightRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
            }
            if (switchDayRunnableScheduled) {
                switchDayRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
            }
        }
        cancelAutoNightThemeCallbacks();
        int switchToTheme = needSwitchToTheme();
        if (switchToTheme != 0) {
            applyDayNightThemeMaybe(switchToTheme == 2);
        }
        if (force) {
            lastThemeSwitchTime = 0;
        }
    }

    public static void applyDayNightThemeMaybe(boolean night) {
        if (previousTheme != null) {
            return;
        }

        if (night) {
            if (currentTheme != currentNightTheme && (currentTheme == null || currentNightTheme != null &&  currentTheme.isDark() != currentNightTheme.isDark())) {
                isInNigthMode = true;
                lastThemeSwitchTime = SystemClock.elapsedRealtime();
                switchingNightTheme = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentNightTheme, true, null, -1);
                switchingNightTheme = false;
            }
        } else {
            if (currentTheme != currentDayTheme && (currentTheme == null || currentDayTheme != null && currentTheme.isLight() != currentDayTheme.isLight())) {
                isInNigthMode = false;
                lastThemeSwitchTime = SystemClock.elapsedRealtime();
                switchingNightTheme = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentDayTheme, true, null, -1);
                switchingNightTheme = false;
            }
        }
    }

    public static boolean deleteTheme(ThemeInfo themeInfo) {
        if (themeInfo.pathToFile == null) {
            return false;
        }
        boolean currentThemeDeleted = false;
        if (currentTheme == themeInfo) {
            applyTheme(defaultTheme, true, false, false);
            currentThemeDeleted = true;
        }
        if (themeInfo == currentNightTheme) {
            currentNightTheme = themesDict.get("Dark Blue");
        }

        themeInfo.removeObservers();
        otherThemes.remove(themeInfo);
        themesDict.remove(themeInfo.name);
        if (themeInfo.overrideWallpaper != null) {
            themeInfo.overrideWallpaper.delete();
        }
        themes.remove(themeInfo);
        File file = new File(themeInfo.pathToFile);
        file.delete();
        saveOtherThemes(true);
        return currentThemeDeleted;
    }

    public static ThemeInfo createNewTheme(String name) {
        ThemeInfo newTheme = new ThemeInfo();
        newTheme.pathToFile = new File(ApplicationLoader.getFilesDirFixed(), "theme" + Utilities.random.nextLong() + ".attheme").getAbsolutePath();
        newTheme.name = name;
        themedWallpaperLink = getWallpaperUrl(currentTheme.overrideWallpaper);
        newTheme.account = UserConfig.selectedAccount;
        saveCurrentTheme(newTheme, true, true, false);
        return newTheme;
    }

    private static String getWallpaperUrl(OverrideWallpaperInfo wallpaperInfo) {
        if (wallpaperInfo == null || TextUtils.isEmpty(wallpaperInfo.slug) || wallpaperInfo.slug.equals(DEFAULT_BACKGROUND_SLUG)) {
            return null;
        }
        StringBuilder modes = new StringBuilder();
        if (wallpaperInfo.isBlurred) {
            modes.append("blur");
        }
        if (wallpaperInfo.isMotion) {
            if (modes.length() > 0) {
                modes.append("+");
            }
            modes.append("motion");
        }
        String wallpaperLink;
        if (wallpaperInfo.color == 0) {
            wallpaperLink = "https://attheme.org?slug=" + wallpaperInfo.slug;
        } else {
            String color = String.format("%02x%02x%02x", (byte) (wallpaperInfo.color >> 16) & 0xff, (byte) (wallpaperInfo.color >> 8) & 0xff, (byte) (wallpaperInfo.color & 0xff)).toLowerCase();
            String color2 = wallpaperInfo.gradientColor1 != 0 ? String.format("%02x%02x%02x", (byte) (wallpaperInfo.gradientColor1 >> 16) & 0xff, (byte) (wallpaperInfo.gradientColor1 >> 8) & 0xff, (byte) (wallpaperInfo.gradientColor1 & 0xff)).toLowerCase() : null;
            String color3 = wallpaperInfo.gradientColor2 != 0 ? String.format("%02x%02x%02x", (byte) (wallpaperInfo.gradientColor2 >> 16) & 0xff, (byte) (wallpaperInfo.gradientColor2 >> 8) & 0xff, (byte) (wallpaperInfo.gradientColor2 & 0xff)).toLowerCase() : null;
            String color4 = wallpaperInfo.gradientColor3 != 0 ? String.format("%02x%02x%02x", (byte) (wallpaperInfo.gradientColor3 >> 16) & 0xff, (byte) (wallpaperInfo.gradientColor3 >> 8) & 0xff, (byte) (wallpaperInfo.gradientColor3 & 0xff)).toLowerCase() : null;
            if (color2 != null && color3 != null) {
                if (color4 != null) {
                    color += "~" + color2 + "~" + color3 + "~" + color4;
                } else {
                    color += "~" + color2 + "~" + color3;
                }
            } else if (color2 != null) {
                color += "-" + color2;
                color += "&rotation=" + wallpaperInfo.rotation;
            }
            wallpaperLink = "https://attheme.org?slug=" + wallpaperInfo.slug + "&intensity=" + (int) (wallpaperInfo.intensity * 100) + "&bg_color=" + color;
        }
        if (modes.length() > 0) {
            wallpaperLink += "&mode=" + modes.toString();
        }
        return wallpaperLink;
    }

    public static void saveCurrentTheme(ThemeInfo themeInfo, boolean finalSave, boolean newTheme, boolean upload) {
        String wallpaperLink;
        OverrideWallpaperInfo wallpaperInfo = themeInfo.overrideWallpaper;
        if (wallpaperInfo != null) {
            wallpaperLink = getWallpaperUrl(wallpaperInfo);
        } else {
            wallpaperLink = themedWallpaperLink;
        }

        Drawable wallpaperToSave = newTheme ? wallpaper : themedWallpaper;
        if (newTheme && wallpaperToSave != null) {
            themedWallpaper = wallpaper;
        }
        ThemeAccent accent = currentTheme.getAccent(false);
        boolean useDefaultColors = currentTheme.firstAccentIsDefault && accent.id == DEFALT_THEME_ACCENT_ID;
        SparseIntArray colorsMap = useDefaultColors ? null : currentColors;

        StringBuilder result = new StringBuilder();
        if (!useDefaultColors) {
            int outBubbleColor = accent != null ? accent.myMessagesAccentColor : 0;
            int outBubbleGradient1 = accent != null ? accent.myMessagesGradientAccentColor1 : 0;
            int outBubbleGradient2 = accent != null ? accent.myMessagesGradientAccentColor2 : 0;
            int outBubbleGradient3 = accent != null ? accent.myMessagesGradientAccentColor3 : 0;
            if (outBubbleColor != 0 && outBubbleGradient1 != 0) {
                colorsMap.put(key_chat_outBubble, outBubbleColor);
                colorsMap.put(key_chat_outBubbleGradient1, outBubbleGradient1);
                if (outBubbleGradient2 != 0) {
                    colorsMap.put(key_chat_outBubbleGradient2, outBubbleGradient2);
                    if (outBubbleGradient3 != 0) {
                        colorsMap.put(key_chat_outBubbleGradient3, outBubbleGradient3);
                    }
                }
                colorsMap.put(key_chat_outBubbleGradientAnimated, accent != null && accent.myMessagesAnimated ? 1 : 0);
            }
        }
        if (useDefaultColors) {
            for (int i = 0; i < defaultColors.length; i++) {
                int key = i;
                int color = defaultColors[i];
                if (wallpaperToSave instanceof BitmapDrawable || wallpaperLink != null) {
                    if (key_chat_wallpaper == key || key_chat_wallpaper_gradient_to1 == key || key_chat_wallpaper_gradient_to2 == key || key_chat_wallpaper_gradient_to3 == key) {
                        continue;
                    }
                }
                result.append(ThemeColors.getStringName(key)).append("=").append(color).append("\n");
            }
        } else {
            for (int i = 0; i < colorsMap.size(); i++) {
                int key = colorsMap.keyAt(i);
                int color = colorsMap.valueAt(i);
                if (wallpaperToSave instanceof BitmapDrawable || wallpaperLink != null) {
                    if (key_chat_wallpaper == key || key_chat_wallpaper_gradient_to1 == key || key_chat_wallpaper_gradient_to2 == key || key_chat_wallpaper_gradient_to3 == key) {
                        continue;
                    }
                }
                result.append(ThemeColors.getStringName(key)).append("=").append(color).append("\n");
            }
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(themeInfo.pathToFile);
            if (result.length() == 0 && !(wallpaperToSave instanceof BitmapDrawable) && TextUtils.isEmpty(wallpaperLink)) {
                result.append(' ');
            }
            stream.write(AndroidUtilities.getStringBytes(result.toString()));
            if (!TextUtils.isEmpty(wallpaperLink)) {
                stream.write(AndroidUtilities.getStringBytes("WLS=" + wallpaperLink + "\n"));
                if (newTheme) {
                    try {
                        Bitmap bitmap = ((BitmapDrawable) wallpaperToSave).getBitmap();
                        FileOutputStream wallpaperStream = new FileOutputStream(new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp"));
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 87, wallpaperStream);
                        wallpaperStream.close();
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            } else if (wallpaperToSave instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) wallpaperToSave).getBitmap();
                if (bitmap != null) {
                    stream.write(new byte[]{'W', 'P', 'S', '\n'});
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.write(new byte[]{'\n', 'W', 'P', 'E', '\n'});
                }
                if (finalSave && !upload) {
                    wallpaper = wallpaperToSave;
                    calcBackgroundColor(wallpaperToSave, 2);
                }
            }
            if (!upload) {
                if (themesDict.get(themeInfo.getKey()) == null) {
                    themes.add(themeInfo);
                    themesDict.put(themeInfo.getKey(), themeInfo);
                    otherThemes.add(themeInfo);
                    saveOtherThemes(true);
                    sortThemes();
                }
                currentTheme = themeInfo;
                if (currentTheme != currentNightTheme) {
                    currentDayTheme = currentTheme;
                }
                if (useDefaultColors) {
                    currentColorsNoAccent.clear();
                    refreshThemeColors();
                }
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("theme", currentDayTheme.getKey());
                editor.apply();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (finalSave) {
            MessagesController.getInstance(themeInfo.account).saveThemeToServer(themeInfo, themeInfo.getAccent(false));
        }
    }

    public static void checkCurrentRemoteTheme(boolean force) {
        if (loadingCurrentTheme != 0 || !force && Math.abs(System.currentTimeMillis() / 1000 - lastLoadingCurrentThemeTime) < 60 * 60) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            ThemeInfo themeInfo = a == 0 ? currentDayTheme : currentNightTheme;
            if (themeInfo == null || !UserConfig.getInstance(themeInfo.account).isClientActivated()) {
                continue;
            }
            ThemeAccent accent = themeInfo.getAccent(false);
            TLRPC.TL_theme info;
            int account;
            if (themeInfo.info != null) {
                info = themeInfo.info;
                account = themeInfo.account;
            } else if (accent != null && accent.info != null) {
                info = accent.info;
                account = UserConfig.selectedAccount;
            } else {
                continue;
            }
            if (info == null || info.document == null) {
                continue;
            }

            loadingCurrentTheme++;
            TLRPC.TL_account_getTheme req = new TLRPC.TL_account_getTheme();
            req.document_id = info.document.id;
            req.format = "android";
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.access_hash = info.access_hash;
            inputTheme.id = info.id;
            req.theme = inputTheme;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingCurrentTheme--;
                boolean changed = false;
                if (response instanceof TLRPC.TL_theme) {
                    TLRPC.TL_theme theme = (TLRPC.TL_theme) response;
                    TLRPC.ThemeSettings settings = null;
                    if (theme.settings.size() > 0) {
                        settings = theme.settings.get(0);
                    }
                    if (accent != null && settings != null) {
                        if (!ThemeInfo.accentEquals(accent, settings)) {
                            File file = accent.getPathToWallpaper();
                            if (file != null) {
                                file.delete();
                            }
                            ThemeInfo.fillAccentValues(accent, settings);
                            if (currentTheme == themeInfo && currentTheme.currentAccentId == accent.id) {
                                refreshThemeColors();
                                createChatResources(ApplicationLoader.applicationContext, false);
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentNightTheme == currentTheme, null, -1);
                            }
                            PatternsLoader.createLoader(true);
                            changed = true;
                        }
                        accent.patternMotion = settings.wallpaper != null && settings.wallpaper.settings != null && settings.wallpaper.settings.motion;
                    } else if (theme.document != null && theme.document.id != info.document.id) {
                        if (accent != null) {
                            accent.info = theme;
                        } else {
                            themeInfo.info = theme;
                            themeInfo.loadThemeDocument();
                        }
                        changed = true;
                    }
                }
                if (loadingCurrentTheme == 0) {
                    lastLoadingCurrentThemeTime = (int) (System.currentTimeMillis() / 1000);
                    saveOtherThemes(changed);
                }
            }));
        }
    }

    public static void loadRemoteThemes(final int currentAccount, boolean force) {
        if (loadingRemoteThemes[currentAccount] || !force && Math.abs(System.currentTimeMillis() / 1000 - lastLoadingThemesTime[currentAccount]) < 60 * 60 || !UserConfig.getInstance(currentAccount).isClientActivated()) {
            return;
        }
        loadingRemoteThemes[currentAccount] = true;
        TLRPC.TL_account_getThemes req = new TLRPC.TL_account_getThemes();
        req.format = "android";
        if (!MediaDataController.getInstance(currentAccount).defaultEmojiThemes.isEmpty()) {
            req.hash = remoteThemesHash[currentAccount];
        }
        if (BuildVars.LOGS_ENABLED) {
            Log.i("theme", "loading remote themes, hash " + req.hash);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingRemoteThemes[currentAccount] = false;
            if (response instanceof TLRPC.TL_account_themes) {
                TLRPC.TL_account_themes res = (TLRPC.TL_account_themes) response;
                remoteThemesHash[currentAccount] = res.hash;
                lastLoadingThemesTime[currentAccount] = (int) (System.currentTimeMillis() / 1000);
                ArrayList<TLRPC.TL_theme> emojiPreviewThemes = new ArrayList<>();
                ArrayList<Object> oldServerThemes = new ArrayList<>();
                for (int a = 0, N = themes.size(); a < N; a++) {
                    ThemeInfo info = themes.get(a);
                    if (info.info != null && info.account == currentAccount) {
                        oldServerThemes.add(info);
                    } else if (info.themeAccents != null) {
                        for (int b = 0; b < info.themeAccents.size(); b++) {
                            ThemeAccent accent = info.themeAccents.get(b);
                            if (accent.info != null && accent.account == currentAccount) {
                                oldServerThemes.add(accent);
                            }
                        }
                    }
                }
                boolean loadPatterns = false;
                boolean added = false;
                for (int a = 0, N = res.themes.size(); a < N; a++) {
                    TLRPC.TL_theme t = res.themes.get(a);
                    if (!(t instanceof TLRPC.TL_theme)) {
                        continue;
                    }
                    TLRPC.TL_theme theme = t;
                    if (theme.isDefault) {
                        emojiPreviewThemes.add(theme);
                    }
                    if (theme.settings != null && theme.settings.size() > 0) {
                        for (int i = 0; i < theme.settings.size(); i++) {
                            TLRPC.ThemeSettings settings = theme.settings.get(i);
                            if (settings != null) {
                                String key = getBaseThemeKey(settings);
                                if (key == null) {
                                    continue;
                                }
                                ThemeInfo info = themesDict.get(key);
                                if (info == null || info.themeAccents == null) {
                                    continue;
                                }
                                ThemeAccent accent = info.accentsByThemeId.get(theme.id);
                                if (accent != null) {
                                    if (!ThemeInfo.accentEquals(accent, settings)) {
                                        File file = accent.getPathToWallpaper();
                                        if (file != null) {
                                            file.delete();
                                        }
                                        ThemeInfo.fillAccentValues(accent, settings);
                                        loadPatterns = true;
                                        added = true;
                                        if (currentTheme == info && currentTheme.currentAccentId == accent.id) {
                                            refreshThemeColors();
                                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentNightTheme == currentTheme, null, -1);
                                        }
                                    }
                                    accent.patternMotion = settings.wallpaper != null && settings.wallpaper.settings != null && settings.wallpaper.settings.motion;
                                    oldServerThemes.remove(accent);
                                } else {
                                    accent = info.createNewAccent(theme, currentAccount, false, i);
                                    if (!TextUtils.isEmpty(accent.patternSlug)) {
                                        loadPatterns = true;
                                    }
                                }
                                accent.isDefault = theme.isDefault;
                            }
                        }
                    } else {
                        String key = "remote" + theme.id;
                        ThemeInfo info = themesDict.get(key);
                        if (info == null) {
                            info = new ThemeInfo();
                            info.account = currentAccount;
                            info.pathToFile = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme").getAbsolutePath();
                            themes.add(info);
                            otherThemes.add(info);
                            added = true;
                        } else {
                            oldServerThemes.remove(info);
                        }
                        info.name = theme.title;
                        info.info = theme;
                        themesDict.put(info.getKey(), info);
                    }
                }
                for (int a = 0, N = oldServerThemes.size(); a < N; a++) {
                    Object object = oldServerThemes.get(a);
                    if (object instanceof ThemeInfo) {
                        ThemeInfo info = (ThemeInfo) object;
                        info.removeObservers();
                        otherThemes.remove(info);
                        themesDict.remove(info.name);
                        if (info.overrideWallpaper != null) {
                            info.overrideWallpaper.delete();
                        }
                        themes.remove(info);
                        File file = new File(info.pathToFile);
                        file.delete();
                        boolean isNightTheme = false;
                        if (currentDayTheme == info) {
                            currentDayTheme = defaultTheme;
                        } else if (currentNightTheme == info) {
                            currentNightTheme = themesDict.get("Dark Blue");
                            isNightTheme = true;
                        }
                        if (currentTheme == info) {
                            applyTheme(isNightTheme ? currentNightTheme : currentDayTheme, true, false, isNightTheme);
                        }
                    } else if (object instanceof ThemeAccent) {
                        ThemeAccent accent = (ThemeAccent) object;
                        if (deleteThemeAccent(accent.parentTheme, accent, false) && currentTheme == accent.parentTheme) {
                            Theme.refreshThemeColors();
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentNightTheme == currentTheme, null, -1);
                        }
                    }
                }
                saveOtherThemes(true);
                sortThemes();
                if (added) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);
                }
                if (loadPatterns) {
                    PatternsLoader.createLoader(true);
                }
                MediaDataController.getInstance(currentAccount).generateEmojiPreviewThemes(emojiPreviewThemes, currentAccount);
            }
        }));
    }

    public static String getBaseThemeKey(TLRPC.ThemeSettings settings) {
        if (settings.base_theme instanceof TLRPC.TL_baseThemeClassic) {
            return "Blue";
        } else if (settings.base_theme instanceof TLRPC.TL_baseThemeDay) {
            return "Day";
        } else if (settings.base_theme instanceof TLRPC.TL_baseThemeTinted) {
            return "Dark Blue";
        } else if (settings.base_theme instanceof TLRPC.TL_baseThemeArctic) {
            return "Arctic Blue";
        } else if (settings.base_theme instanceof TLRPC.TL_baseThemeNight) {
            return "Night";
        }
        return null;
    }

    public static TLRPC.BaseTheme getBaseThemeByKey(String key) {
        if ("Blue".equals(key)) {
            return new TLRPC.TL_baseThemeClassic();
        } else if ("Day".equals(key)) {
            return new TLRPC.TL_baseThemeDay();
        } else if ("Dark Blue".equals(key)) {
            return new TLRPC.TL_baseThemeTinted();
        } else if ("Arctic Blue".equals(key)) {
            return new TLRPC.TL_baseThemeArctic();
        } else if ("Night".equals(key)) {
            return new TLRPC.TL_baseThemeNight();
        }
        return null;
    }

    public static void setThemeFileReference(TLRPC.TL_theme info) {
        for (int a = 0, N = themes.size(); a < N; a++) {
            ThemeInfo themeInfo = themes.get(a);
            if (themeInfo.info != null && themeInfo.info.id == info.id) {
                if (themeInfo.info.document != null && info.document != null) {
                    themeInfo.info.document.file_reference = info.document.file_reference;
                    saveOtherThemes(true);
                }
                break;
            }
        }
    }

    public static boolean isThemeInstalled(ThemeInfo themeInfo) {
        return themeInfo != null && themesDict.get(themeInfo.getKey()) != null;
    }

    public static void setThemeUploadInfo(ThemeInfo theme, ThemeAccent accent, TLRPC.TL_theme info, int account, boolean update) {
        if (info == null) {
            return;
        }
        TLRPC.ThemeSettings settings = null;
        if (info.settings.size() > 0) {
            settings = info.settings.get(0);
        }
        if (settings != null) {
            if (theme == null) {
                String key = getBaseThemeKey(settings);
                if (key == null) {
                    return;
                }
                theme = themesDict.get(key);
                if (theme == null) {
                    return;
                }
                accent = theme.accentsByThemeId.get(info.id);
            }
            if (accent == null) {
                return;
            }
            if (accent.info != null) {
                theme.accentsByThemeId.remove(accent.info.id);
            }
            accent.info = info;
            accent.account = account;
            theme.accentsByThemeId.put(info.id, accent);


            if (!ThemeInfo.accentEquals(accent, settings)) {
                File file = accent.getPathToWallpaper();
                if (file != null) {
                    file.delete();
                }
                ThemeInfo.fillAccentValues(accent, settings);
                if (currentTheme == theme && currentTheme.currentAccentId == accent.id) {
                    refreshThemeColors();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentNightTheme == currentTheme, null, -1);
                }
                PatternsLoader.createLoader(true);
            }
            accent.patternMotion = settings.wallpaper != null && settings.wallpaper.settings != null && settings.wallpaper.settings.motion;
            theme.previewParsed = false;
        } else {
            String key;
            if (theme != null) {
                themesDict.remove(key = theme.getKey());
            } else {
                theme = themesDict.get(key = "remote" + info.id);
            }
            if (theme == null) {
                return;
            }
            theme.info = info;
            theme.name = info.title;
            File oldPath = new File(theme.pathToFile);
            File newPath = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme");
            if (!oldPath.equals(newPath)) {
                try {
                    AndroidUtilities.copyFile(oldPath, newPath);
                    theme.pathToFile = newPath.getAbsolutePath();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (update) {
                theme.loadThemeDocument();
            } else {
                theme.previewParsed = false;
            }
            themesDict.put(theme.getKey(), theme);
        }
        saveOtherThemes(true);
    }

    public static File getAssetFile(String assetName) {
        File file = new File(ApplicationLoader.getFilesDirFixed(), assetName);
        long size;
        try {
            InputStream stream = ApplicationLoader.applicationContext.getAssets().open(assetName);
            size = stream.available();
            stream.close();
        } catch (Exception e) {
            size = 0;
            FileLog.e(e);
        }
        if (!file.exists() || size != 0 && file.length() != size) {
            try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(assetName)) {
                AndroidUtilities.copyFile(in, file);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return file;
    }

    public static int getPreviewColor(SparseIntArray colors, int key) {
        int index = colors.indexOfKey(key);
        if (index >= 0) {
            return colors.valueAt(index);
        } else {
            return defaultColors[key];
        }
    }

    public static String createThemePreviewImage(String pathToFile, String wallpaperPath, Theme.ThemeAccent accent) {
        try {
            String[] wallpaperLink = new String[1];
            SparseIntArray colors = getThemeFileValues(new File(pathToFile), null, wallpaperLink);
            if (accent != null) {
                checkIsDark(colors, accent.parentTheme);
            }
            int wallpaperFileOffset = currentColorsNoAccent.get(key_wallpaperFileOffset, -1);
            Bitmap bitmap = Bitmaps.createBitmap(560, 678, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint();

            int actionBarColor = getPreviewColor(colors, key_actionBarDefault);
            int actionBarIconColor = getPreviewColor(colors, key_actionBarDefaultIcon);
            int messageFieldColor = getPreviewColor(colors, key_chat_messagePanelBackground);
            int messageFieldIconColor = getPreviewColor(colors, key_chat_messagePanelIcons);
            int messageInColor = getPreviewColor(colors, key_chat_inBubble);
            int messageOutColor = getPreviewColor(colors, key_chat_outBubble);
            int messageOutGradientColor = colors.get(key_chat_outBubbleGradient1);
            int backgroundColor = colors.get(key_chat_wallpaper);
            int gradientToColor1 = colors.get(key_chat_wallpaper_gradient_to1);
            int gradientToColor2 = colors.get(key_chat_wallpaper_gradient_to2);
            int gradientToColor3 = colors.get(key_chat_wallpaper_gradient_to3);

            int defaultBackgroundColor = backgroundColor;
            int backgroundOverrideColor = accent != null ? (int) accent.backgroundOverrideColor : 0;
            int backColor;
            if (backgroundOverrideColor == 0 && accent != null && accent.backgroundOverrideColor != 0) {
                backColor = 0;
            } else {
                backColor = backgroundOverrideColor != 0 ? backgroundOverrideColor : defaultBackgroundColor;
            }

            int defaultBackgroundGradient1 = gradientToColor1;
            int backgroundGradientOverrideColor1 = accent != null ? (int) accent.backgroundGradientOverrideColor1 : 0;
            int color1;
            if (backgroundGradientOverrideColor1 == 0 && accent != null && accent.backgroundGradientOverrideColor1 != 0) {
                color1 = 0;
            } else {
                color1 = backgroundGradientOverrideColor1 != 0 ? backgroundGradientOverrideColor1 : defaultBackgroundGradient1;
            }
            int defaultBackgroundGradient2 = gradientToColor2;
            int backgroundGradientOverrideColor2 = accent != null ? (int) accent.backgroundGradientOverrideColor2 : 0;
            int color2;
            if (backgroundGradientOverrideColor2 == 0 && accent != null && accent.backgroundGradientOverrideColor2 != 0) {
                color2 = 0;
            } else {
                color2 = backgroundGradientOverrideColor2 != 0 ? backgroundGradientOverrideColor2 : defaultBackgroundGradient2;
            }
            int defaultBackgroundGradient3 = gradientToColor3;
            int backgroundGradientOverrideColor3 = accent != null ? (int) accent.backgroundGradientOverrideColor3 : 0;
            int color3;
            if (backgroundGradientOverrideColor3 == 0 && accent != null && accent.backgroundGradientOverrideColor3 != 0) {
                color3 = 0;
            } else {
                color3 = backgroundGradientOverrideColor3 != 0 ? backgroundGradientOverrideColor3 : defaultBackgroundGradient3;
            }

            if (!TextUtils.isEmpty(wallpaperLink[0])) {
                try {
                    Uri data = Uri.parse(wallpaperLink[0]);
                    String bgColor = data.getQueryParameter("bg_color");
                    if (accent != null && !TextUtils.isEmpty(bgColor)) {
                        accent.backgroundOverrideColor = backColor = Integer.parseInt(bgColor.substring(0, 6), 16) | 0xff000000;
                        if (bgColor.length() >= 13 && AndroidUtilities.isValidWallChar(bgColor.charAt(6))) {
                            accent.backgroundGradientOverrideColor1 = color1 = Integer.parseInt(bgColor.substring(7, 13), 16) | 0xff000000;
                        }
                        if (bgColor.length() >= 20 && AndroidUtilities.isValidWallChar(bgColor.charAt(13))) {
                            accent.backgroundGradientOverrideColor2 = color2 = Integer.parseInt(bgColor.substring(14, 20), 16) | 0xff000000;
                        }
                        if (bgColor.length() == 27 && AndroidUtilities.isValidWallChar(bgColor.charAt(20))) {
                            accent.backgroundGradientOverrideColor3 = color3 = Integer.parseInt(bgColor.substring(21), 16) | 0xff000000;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }


            Drawable backDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_back).mutate();
            setDrawableColor(backDrawable, actionBarIconColor);
            Drawable otherDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_dots).mutate();
            setDrawableColor(otherDrawable, actionBarIconColor);
            Drawable emojiDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_smile).mutate();
            setDrawableColor(emojiDrawable, messageFieldIconColor);
            Drawable micDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_mic).mutate();
            setDrawableColor(micDrawable, messageFieldIconColor);

            MessageDrawable[] msgDrawable = new MessageDrawable[2];
            for (int a = 0; a < 2; a++) {
                msgDrawable[a] = new MessageDrawable(MessageDrawable.TYPE_PREVIEW, a == 1, false) {
                    @Override
                    protected int getColor(int key) {
                        int index = colors.indexOfKey(key);
                        if (index > 0) {
                           return colors.valueAt(index);
                        } else {
                            return defaultColors[key];
                        }
                    }

                    @Override
                    protected int getCurrentColor(int key) {
                        return colors.get(key);
                    }
                };
                setDrawableColor(msgDrawable[a], a == 0 ? messageInColor : messageOutColor);
            }

            RectF rect = new RectF();
            int quality = 80;
            boolean hasBackground = false;
            if (wallpaperPath != null) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(wallpaperPath, options);
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        float side = 560.0f;
                        float scale = Math.min(options.outWidth / side, options.outHeight / side);
                        options.inSampleSize = 1;
                        if (scale > 1.0f) {
                            do {
                                options.inSampleSize *= 2;
                            } while (options.inSampleSize < scale);
                        }
                        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                        options.inJustDecodeBounds = false;
                        Bitmap wallpaper = BitmapFactory.decodeFile(wallpaperPath, options);
                        if (wallpaper != null) {
                            if (color2 != 0 && accent != null) {
                                MotionBackgroundDrawable wallpaperDrawable = new MotionBackgroundDrawable(backColor, color1, color2, color3, true);
                                if (bitmap != null && bitmap.getConfig() != Bitmap.Config.ALPHA_8) {
                                    Bitmap toRecycle = bitmap;
                                    bitmap = bitmap.copy(Bitmap.Config.ALPHA_8, false);
                                    toRecycle.recycle();
                                }
                                wallpaperDrawable.setPatternBitmap((int) (accent.patternIntensity * 100), wallpaper);
                                wallpaperDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                                wallpaperDrawable.draw(canvas);
                            } else {
                                Paint bitmapPaint = new Paint();
                                bitmapPaint.setFilterBitmap(true);
                                scale = Math.min(wallpaper.getWidth() / side, wallpaper.getHeight() / side);
                                rect.set(0, 0, wallpaper.getWidth() / scale, wallpaper.getHeight() / scale);
                                rect.offset((bitmap.getWidth() - rect.width()) / 2, (bitmap.getHeight() - rect.height()) / 2);
                                canvas.drawBitmap(wallpaper, null, rect, bitmapPaint);
                            }
                            hasBackground = true;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else if (backColor != 0) {
                Drawable wallpaperDrawable;
                if (color1 == 0) {
                    wallpaperDrawable = new ColorDrawable(backColor);
                } else {
                    if (color2 != 0) {
                        wallpaperDrawable = new MotionBackgroundDrawable(backColor, color1, color2, color3, true);
                    } else {
                        int gradientRotation = colors.get(key_chat_wallpaper_gradient_rotation, -1);
                        if (gradientRotation == -1) {
                            gradientRotation = 45;
                        }
                        final int[] gradientColors = {backColor, gradientToColor2};
                        wallpaperDrawable = BackgroundGradientDrawable.createDitheredGradientBitmapDrawable(gradientRotation, gradientColors, bitmap.getWidth(), bitmap.getHeight() - 120);
                        quality = 90;
                    }
                }
                wallpaperDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                wallpaperDrawable.draw(canvas);
                hasBackground = true;
            } else if (wallpaperFileOffset >= 0 || !TextUtils.isEmpty(wallpaperLink[0])) {
                FileInputStream stream = null;
                File pathToWallpaper = null;
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    if (!TextUtils.isEmpty(wallpaperLink[0])) {
                        pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink[0]) + ".wp");
                        BitmapFactory.decodeFile(pathToWallpaper.getAbsolutePath(), options);
                    } else {
                        stream = new FileInputStream(pathToFile);
                        stream.getChannel().position(wallpaperFileOffset);
                        BitmapFactory.decodeStream(stream, null, options);
                    }
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        float scale = Math.min(options.outWidth / 560.0f, options.outHeight / 560.0f);
                        options.inSampleSize = 1;
                        if (scale > 1.0f) {
                            do {
                                options.inSampleSize *= 2;
                            } while (options.inSampleSize < scale);
                        }
                        options.inJustDecodeBounds = false;
                        Bitmap wallpaper;
                        if (pathToWallpaper != null) {
                            wallpaper = BitmapFactory.decodeFile(pathToWallpaper.getAbsolutePath(), options);
                        } else {
                            stream.getChannel().position(wallpaperFileOffset);
                            wallpaper = BitmapFactory.decodeStream(stream, null, options);
                        }
                        if (wallpaper != null) {
                            Paint bitmapPaint = new Paint();
                            bitmapPaint.setFilterBitmap(true);
                            scale = Math.min(wallpaper.getWidth() / 560.0f, wallpaper.getHeight() / 560.0f);
                            rect.set(0, 0, wallpaper.getWidth() / scale, wallpaper.getHeight() / scale);
                            rect.offset((bitmap.getWidth() - rect.width()) / 2, (bitmap.getHeight() - rect.height()) / 2);
                            canvas.drawBitmap(wallpaper, null, rect, bitmapPaint);
                            hasBackground = true;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            if (!hasBackground) {
                Drawable catsDrawable = createDefaultWallpaper(bitmap.getWidth(), bitmap.getHeight() - 120);
                catsDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                catsDrawable.draw(canvas);
            }

            paint.setColor(actionBarColor);
            canvas.drawRect(0, 0, bitmap.getWidth(), 120, paint);

            if (backDrawable != null) {
                int x = 13;
                int y = (120 - backDrawable.getIntrinsicHeight()) / 2;
                backDrawable.setBounds(x, y, x + backDrawable.getIntrinsicWidth(), y + backDrawable.getIntrinsicHeight());
                backDrawable.draw(canvas);
            }
            if (otherDrawable != null) {
                int x = bitmap.getWidth() - otherDrawable.getIntrinsicWidth() - 10;
                int y = (120 - otherDrawable.getIntrinsicHeight()) / 2;
                otherDrawable.setBounds(x, y, x + otherDrawable.getIntrinsicWidth(), y + otherDrawable.getIntrinsicHeight());
                otherDrawable.draw(canvas);
            }
            msgDrawable[1].setBounds(161, 216, bitmap.getWidth() - 20, 216 + 92);
            msgDrawable[1].setTop(0, 560, 522, false, false);
            msgDrawable[1].draw(canvas);

            msgDrawable[1].setBounds(161, 430, bitmap.getWidth() - 20, 430 + 92);
            msgDrawable[1].setTop(430, 560, 522, false, false);
            msgDrawable[1].draw(canvas);

            msgDrawable[0].setBounds(20, 323, 399, 323 + 92);
            msgDrawable[0].setTop(323, 560, 522, false, false);
            msgDrawable[0].draw(canvas);

            paint.setColor(messageFieldColor);
            canvas.drawRect(0, bitmap.getHeight() - 120, bitmap.getWidth(), bitmap.getHeight(), paint);
            if (emojiDrawable != null) {
                int x = 22;
                int y = bitmap.getHeight() - 120 + (120 - emojiDrawable.getIntrinsicHeight()) / 2;
                emojiDrawable.setBounds(x, y, x + emojiDrawable.getIntrinsicWidth(), y + emojiDrawable.getIntrinsicHeight());
                emojiDrawable.draw(canvas);
            }
            if (micDrawable != null) {
                int x = bitmap.getWidth() - micDrawable.getIntrinsicWidth() - 22;
                int y = bitmap.getHeight() - 120 + (120 - micDrawable.getIntrinsicHeight()) / 2;
                micDrawable.setBounds(x, y, x + micDrawable.getIntrinsicWidth(), y + micDrawable.getIntrinsicHeight());
                micDrawable.draw(canvas);
            }
            canvas.setBitmap(null);

            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            try {
                FileOutputStream stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
                SharedConfig.saveConfig();
                return cacheFile.getAbsolutePath();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static void checkIsDark(SparseIntArray colors, Theme.ThemeInfo info) {
        if (info == null || colors == null) {
            return;
        }
        if (info.isDark == ThemeInfo.UNKNOWN) {
            int averageBackgroundColor = getPreviewColor(colors, key_windowBackgroundWhite);
            averageBackgroundColor = ColorUtils.blendARGB(averageBackgroundColor, getPreviewColor(colors, key_windowBackgroundWhite), 0.5f);
            if (ColorUtils.calculateLuminance(averageBackgroundColor) < 0.5f) {
                info.isDark = ThemeInfo.DARK;
            } else {
                info.isDark = ThemeInfo.LIGHT;
            }
        }
    }

    public static void getThemeFileValuesInBackground(File file, String assetName, String[] wallpaperLink, Utilities.Callback<SparseIntArray> onDone) {
        Utilities.themeQueue.postRunnable(() -> {
            onDone.run(getThemeFileValues(file, assetName, wallpaperLink));
        });
    }

    public static SparseIntArray getThemeFileValues(File file, String assetName, String[] wallpaperLink) {
        FileInputStream stream = null;
        SparseIntArray stringMap = new SparseIntArray();
        try {
            byte[] bytes = new byte[1024];
            int currentPosition = 0;
            if (assetName != null) {
                file = getAssetFile(assetName);
            }
            stream = new FileInputStream(file);
            int idx;
            int read;
            boolean finished = false;
            int wallpaperFileOffset = -1;
            while ((read = stream.read(bytes)) != -1) {
                int previousPosition = currentPosition;
                int start = 0;
                for (int a = 0; a < read; a++) {
                    if (bytes[a] == '\n') {
                        int len = a - start + 1;
                        String line = new String(bytes, start, len - 1);
                        if (line.startsWith("WLS=")) {
                            if (wallpaperLink != null && wallpaperLink.length > 0) {
                                wallpaperLink[0] = line.substring(4);
                            }
                        } else if (line.startsWith("WPS")) {
                            wallpaperFileOffset = currentPosition + len;
                            finished = true;
                            break;
                        } else {
                            if ((idx = line.indexOf('=')) != -1) {
                                String key = line.substring(0, idx);
                                String param = line.substring(idx + 1);
                                int value;
                                if (param.length() > 0 && param.charAt(0) == '#') {
                                    try {
                                        value = Color.parseColor(param);
                                    } catch (Exception ignore) {
                                        value = Utilities.parseInt(param);
                                    }
                                } else {
                                    value = Utilities.parseInt(param);
                                }
                                int keyFromString = ThemeColors.stringKeyToInt(key);
                                if (keyFromString >= 0) {
                                    stringMap.put(keyFromString, value);
                                }
                            }
                        }
                        start += len;
                        currentPosition += len;
                    }
                }
                if (previousPosition == currentPosition) {
                    break;
                }
                stream.getChannel().position(currentPosition);
                if (finished) {
                    break;
                }
            }
            stringMap.put(key_wallpaperFileOffset, wallpaperFileOffset);
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return stringMap;
    }

    public static void createCommonResources(Context context) {
        if (dividerPaint == null) {
            dividerPaint = new Paint();
            dividerPaint.setStrokeWidth(1);

            dividerExtraPaint = new Paint();
            dividerExtraPaint.setStrokeWidth(1);

            avatar_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            checkboxSquare_checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkboxSquare_checkPaint.setStyle(Paint.Style.STROKE);
            checkboxSquare_checkPaint.setStrokeWidth(dp(2));
            checkboxSquare_checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkboxSquare_eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkboxSquare_eraserPaint.setColor(0);
            checkboxSquare_eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            checkboxSquare_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            linkSelectionPaint = new Paint();
            linkSelectionPaint.setPathEffect(LinkPath.getRoundedEffect());

            Resources resources = context.getResources();

            avatarDrawables[0] = resources.getDrawable(R.drawable.chats_saved);
            avatarDrawables[1] = resources.getDrawable(R.drawable.ghost);
            avatarDrawables[2] = resources.getDrawable(R.drawable.msg_folders_private);
            avatarDrawables[3] = resources.getDrawable(R.drawable.msg_folders_requests);
            avatarDrawables[4] = resources.getDrawable(R.drawable.msg_folders_groups);
            avatarDrawables[5] = resources.getDrawable(R.drawable.msg_folders_channels);
            avatarDrawables[6] = resources.getDrawable(R.drawable.msg_folders_bots);
            avatarDrawables[7] = resources.getDrawable(R.drawable.msg_folders_muted);
            avatarDrawables[8] = resources.getDrawable(R.drawable.msg_folders_read);
            avatarDrawables[9] = resources.getDrawable(R.drawable.msg_folders_archive);
            avatarDrawables[10] = resources.getDrawable(R.drawable.msg_folders_private);
            avatarDrawables[11] = resources.getDrawable(R.drawable.chats_replies);
            avatarDrawables[12] = resources.getDrawable(R.drawable.other_chats);
            avatarDrawables[13] = resources.getDrawable(R.drawable.msg_stories_closefriends);
            avatarDrawables[14] = resources.getDrawable(R.drawable.filled_gift_premium);
            avatarDrawables[15] = resources.getDrawable(R.drawable.filled_unknown);
            avatarDrawables[16] = resources.getDrawable(R.drawable.filled_unclaimed);
            avatarDrawables[17] = resources.getDrawable(R.drawable.large_repost_story);
            avatarDrawables[18] = resources.getDrawable(R.drawable.large_hidden);
            avatarDrawables[19] = resources.getDrawable(R.drawable.large_notes);
            avatarDrawables[20] = resources.getDrawable(R.drawable.filled_folder_new);
            avatarDrawables[21] = resources.getDrawable(R.drawable.filled_folder_existing);
            avatarDrawables[22] = resources.getDrawable(R.drawable.filled_giveaway_premium);
            avatarDrawables[23] = resources.getDrawable(R.drawable.filled_giveaway_stars);

            if (dialogs_archiveAvatarDrawable != null) {
                dialogs_archiveAvatarDrawable.setCallback(null);
                dialogs_archiveAvatarDrawable.recycle(false);
            }
            if (dialogs_archiveDrawable != null) {
                dialogs_archiveDrawable.recycle(false);
            }
            if (dialogs_unarchiveDrawable != null) {
                dialogs_unarchiveDrawable.recycle(false);
            }
            if (dialogs_pinArchiveDrawable != null) {
                dialogs_pinArchiveDrawable.recycle(false);
            }
            if (dialogs_unpinArchiveDrawable != null) {
                dialogs_unpinArchiveDrawable.recycle(false);
            }
            if (dialogs_hidePsaDrawable != null) {
                dialogs_hidePsaDrawable.recycle(false);
            }
            dialogs_archiveAvatarDrawable = new RLottieDrawable(R.raw.chats_archiveavatar, "chats_archiveavatar", dp(36), dp(36), false, null);
            dialogs_archiveDrawable = new RLottieDrawable(R.raw.chats_archive, "chats_archive", dp(36), dp(36), false, null);
            dialogs_unarchiveDrawable = new RLottieDrawable(R.raw.chats_unarchive, "chats_unarchive", dp(dp(36)), dp(36), false, null);
            dialogs_pinArchiveDrawable = new RLottieDrawable(R.raw.chats_hide, "chats_hide", dp(36), dp(36), false, null);
            dialogs_unpinArchiveDrawable = new RLottieDrawable(R.raw.chats_unhide, "chats_unhide", dp(36), dp(36), false, null);
            dialogs_hidePsaDrawable = new RLottieDrawable(R.raw.chat_audio_record_delete, "chats_psahide", dp(30), dp(30), false, null);

            dialogs_swipeMuteDrawable = new RLottieDrawable(R.raw.swipe_mute, "swipe_mute", dp(36), dp(36), false, null);
            dialogs_swipeUnmuteDrawable = new RLottieDrawable(R.raw.swipe_unmute, "swipe_unmute", dp(36), dp(36), false, null);

            dialogs_swipeReadDrawable = new RLottieDrawable(R.raw.swipe_read, "swipe_read", dp(36), dp(36), false, null);
            dialogs_swipeUnreadDrawable = new RLottieDrawable(R.raw.swipe_unread, "swipe_unread", dp(36), dp(36), false, null);
            dialogs_swipeDeleteDrawable = new RLottieDrawable(R.raw.swipe_delete, "swipe_delete", dp(36), dp(36), false, null);
            dialogs_swipeUnpinDrawable = new RLottieDrawable(R.raw.swipe_unpin, "swipe_unpin", dp(36), dp(36), false, null);
            dialogs_swipePinDrawable = new RLottieDrawable(R.raw.swipe_pin, "swipe_pin", dp(36), dp(36), false, null);

            applyCommonTheme();
        }
    }

    public static void applyCommonTheme() {
        if (dividerPaint == null) {
            return;
        }
        dividerPaint.setColor(getColor(key_divider));
        linkSelectionPaint.setColor(getColor(key_windowBackgroundWhiteLinkSelection));

        for (int a = 0; a < avatarDrawables.length; a++) {
            setDrawableColorByKey(avatarDrawables[a], key_avatar_text);
        }

        dialogs_archiveAvatarDrawable.beginApplyLayerColors();
        dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", getNonAnimatedColor(key_avatar_backgroundArchived));
        dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", getNonAnimatedColor(key_avatar_backgroundArchived));
        dialogs_archiveAvatarDrawable.setLayerColor("Box2.**", getNonAnimatedColor(key_avatar_text));
        dialogs_archiveAvatarDrawable.setLayerColor("Box1.**", getNonAnimatedColor(key_avatar_text));
        dialogs_archiveAvatarDrawable.commitApplyLayerColors();
        dialogs_archiveAvatarDrawableRecolored = false;
        dialogs_archiveAvatarDrawable.setAllowDecodeSingleFrame(true);

        dialogs_pinArchiveDrawable.beginApplyLayerColors();
        dialogs_pinArchiveDrawable.setLayerColor("Arrow.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_pinArchiveDrawable.setLayerColor("Line.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_pinArchiveDrawable.commitApplyLayerColors();

        dialogs_unpinArchiveDrawable.beginApplyLayerColors();
        dialogs_unpinArchiveDrawable.setLayerColor("Arrow.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_unpinArchiveDrawable.setLayerColor("Line.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_unpinArchiveDrawable.commitApplyLayerColors();

        dialogs_hidePsaDrawable.beginApplyLayerColors();
        dialogs_hidePsaDrawable.setLayerColor("Line 1.**", getNonAnimatedColor(key_chats_archiveBackground));
        dialogs_hidePsaDrawable.setLayerColor("Line 2.**", getNonAnimatedColor(key_chats_archiveBackground));
        dialogs_hidePsaDrawable.setLayerColor("Line 3.**", getNonAnimatedColor(key_chats_archiveBackground));
        dialogs_hidePsaDrawable.setLayerColor("Cup Red.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_hidePsaDrawable.setLayerColor("Box.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_hidePsaDrawable.commitApplyLayerColors();
        dialogs_hidePsaDrawableRecolored = false;

        dialogs_archiveDrawable.beginApplyLayerColors();
        dialogs_archiveDrawable.setLayerColor("Arrow.**", getNonAnimatedColor(key_chats_archiveBackground));
        dialogs_archiveDrawable.setLayerColor("Box2.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_archiveDrawable.setLayerColor("Box1.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_archiveDrawable.commitApplyLayerColors();
        dialogs_archiveDrawableRecolored = false;

        dialogs_unarchiveDrawable.beginApplyLayerColors();
        dialogs_unarchiveDrawable.setLayerColor("Arrow1.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_unarchiveDrawable.setLayerColor("Arrow2.**", getNonAnimatedColor(key_chats_archivePinBackground));
        dialogs_unarchiveDrawable.setLayerColor("Box2.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_unarchiveDrawable.setLayerColor("Box1.**", getNonAnimatedColor(key_chats_archiveIcon));
        dialogs_unarchiveDrawable.commitApplyLayerColors();

        chat_animatedEmojiTextColorFilter = new PorterDuffColorFilter(getColor(key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN);
        chat_outAnimatedEmojiTextColorFilter = new PorterDuffColorFilter(getColor(key_chat_messageTextOut), PorterDuff.Mode.SRC_IN);

        PremiumGradient.getInstance().checkIconColors();
    }

    public static void createCommonDialogResources(Context context) {
        if (dialogs_countTextPaint == null) {
            dialogs_countTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_countTextPaint.setTypeface(AndroidUtilities.bold());
            dialogs_countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_reactionsCountPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_onlineCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        dialogs_countTextPaint.setTextSize(dp(13));
    }

    public static void createDialogsResources(Context context) {
        createCommonResources(context);
        createCommonDialogResources(context);
        if (dialogs_namePaint == null) {
            Resources resources = context.getResources();

            dialogs_namePaint = new TextPaint[2];
            dialogs_nameEncryptedPaint = new TextPaint[2];
            dialogs_messagePaint = new TextPaint[2];
            dialogs_messagePrintingPaint = new TextPaint[2];
            for (int a = 0; a < 2; a++) {
                dialogs_namePaint[a] = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                dialogs_namePaint[a].setTypeface(AndroidUtilities.bold());
                dialogs_nameEncryptedPaint[a] = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                dialogs_nameEncryptedPaint[a].setTypeface(AndroidUtilities.bold());
                dialogs_messagePaint[a] = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                dialogs_messagePrintingPaint[a] = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            }
            dialogs_searchNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_searchNamePaint.setTypeface(AndroidUtilities.bold());
            dialogs_searchNameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_searchNameEncryptedPaint.setTypeface(AndroidUtilities.bold());
            dialogs_messageNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_messageNamePaint.setTypeface(AndroidUtilities.bold());
            dialogs_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_archiveTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_archiveTextPaint.setTypeface(AndroidUtilities.bold());
            dialogs_archiveTextPaintSmall = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_archiveTextPaintSmall.setTypeface(AndroidUtilities.bold());
            dialogs_onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_tagTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_tagTextPaint.setTypeface(AndroidUtilities.bold());

            dialogs_tabletSeletedPaint = new Paint();
            dialogs_pinnedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_countGrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_actionMessagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            dialogs_lockDrawable = resources.getDrawable(R.drawable.list_secret);
            dialogs_lock2Drawable = resources.getDrawable(R.drawable.msg_mini_lock2);
            dialogs_checkDrawable = resources.getDrawable(R.drawable.list_check).mutate();
            dialogs_playDrawable = resources.getDrawable(R.drawable.minithumb_play).mutate();
            dialogs_checkReadDrawable = resources.getDrawable(R.drawable.list_check).mutate();
            dialogs_halfCheckDrawable = resources.getDrawable(R.drawable.list_halfcheck);
            dialogs_clockDrawable = new MsgClockDrawable();
            dialogs_errorDrawable = resources.getDrawable(R.drawable.list_warning_sign);
            dialogs_reorderDrawable = resources.getDrawable(R.drawable.list_reorder).mutate();
            dialogs_muteDrawable = resources.getDrawable(R.drawable.list_mute).mutate();
            dialogs_unmuteDrawable = resources.getDrawable(R.drawable.list_unmute).mutate();
            dialogs_verifiedDrawable = resources.getDrawable(R.drawable.verified_area).mutate();
            dialogs_scamDrawable = new ScamDrawable(11, 0);
            dialogs_fakeDrawable = new ScamDrawable(11, 1);
            dialogs_verifiedCheckDrawable = resources.getDrawable(R.drawable.verified_check).mutate();
            dialogs_mentionDrawable = resources.getDrawable(R.drawable.mentionchatslist);
            dialogs_reactionsMentionDrawable = resources.getDrawable(R.drawable.reactionchatslist);
            dialogs_pinnedDrawable = resources.getDrawable(R.drawable.list_pin);
            dialogs_forum_arrowDrawable = resources.getDrawable(R.drawable.msg_mini_forumarrow);
            moveUpDrawable = resources.getDrawable(R.drawable.preview_arrow);

            RectF rect = new RectF();
            chat_updatePath[0] = new Path();
            chat_updatePath[2] = new Path();
            float cx = dp(12);
            float cy = dp(12);
            rect.set(cx - dp(5), cy - dp(5), cx + dp(5), cy + dp(5));
            chat_updatePath[2].arcTo(rect, -160, -110, true);
            chat_updatePath[2].arcTo(rect, 20, -110, true);

            chat_updatePath[0].moveTo(cx, cy + dp(5 + 3));
            chat_updatePath[0].lineTo(cx, cy + dp(5 - 3));
            chat_updatePath[0].lineTo(cx + dp(3), cy + dp(5));
            chat_updatePath[0].close();

            chat_updatePath[0].moveTo(cx, cy - dp(5 + 3));
            chat_updatePath[0].lineTo(cx, cy - dp(5 - 3));
            chat_updatePath[0].lineTo(cx - dp(3), cy - dp(5));
            chat_updatePath[0].close();

            applyDialogsTheme();
        }

        dialogs_messageNamePaint.setTextSize(dp(14));
        dialogs_timePaint.setTextSize(dp(13));
        dialogs_archiveTextPaint.setTextSize(dp(13));
        dialogs_archiveTextPaintSmall.setTextSize(dp(11));
        dialogs_onlinePaint.setTextSize(dp(15));
        dialogs_offlinePaint.setTextSize(dp(15));
        dialogs_tagTextPaint.setTextSize(dp(10));
        dialogs_searchNamePaint.setTextSize(dp(16));
        dialogs_searchNameEncryptedPaint.setTextSize(dp(16));
    }

    public static void applyDialogsTheme() {
        if (dialogs_namePaint == null) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            dialogs_namePaint[a].setColor(getColor(key_chats_name));
            dialogs_nameEncryptedPaint[a].setColor(getColor(key_chats_secretName));
            dialogs_messagePaint[a].setColor(dialogs_messagePaint[a].linkColor = getColor(key_chats_message));
            dialogs_messagePrintingPaint[a].setColor(getColor(key_chats_actionMessage));
        }
        dialogs_searchNamePaint.setColor(getColor(key_chats_name));
        dialogs_searchNameEncryptedPaint.setColor(getColor(key_chats_secretName));
        dialogs_messageNamePaint.setColor(dialogs_messageNamePaint.linkColor = getColor(key_chats_nameMessage_threeLines));
        dialogs_tabletSeletedPaint.setColor(getColor(key_chats_tabletSelectedOverlay));
        dialogs_pinnedPaint.setColor(getColor(key_chats_pinnedOverlay));
        dialogs_timePaint.setColor(getColor(key_chats_date));
        dialogs_countTextPaint.setColor(getColor(key_chats_unreadCounterText));
        dialogs_archiveTextPaint.setColor(getColor(key_chats_archiveText));
        dialogs_archiveTextPaintSmall.setColor(getColor(key_chats_archiveText));
        dialogs_countPaint.setColor(getColor(key_chats_unreadCounter));
        dialogs_reactionsCountPaint.setColor(getColor(key_dialogReactionMentionBackground));
        dialogs_countGrayPaint.setColor(getColor(key_chats_unreadCounterMuted));
        dialogs_actionMessagePaint.setColor(getColor(key_chats_actionMessage));
        dialogs_errorPaint.setColor(getColor(key_chats_sentError));
        dialogs_onlinePaint.setColor(getColor(key_windowBackgroundWhiteBlueText3));
        dialogs_offlinePaint.setColor(getColor(key_windowBackgroundWhiteGrayText3));

        setDrawableColorByKey(dialogs_lockDrawable, key_chats_secretIcon);
        setDrawableColorByKey(dialogs_lock2Drawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_checkDrawable, key_chats_sentCheck);
        setDrawableColorByKey(dialogs_checkReadDrawable, key_chats_sentReadCheck);
        setDrawableColorByKey(dialogs_halfCheckDrawable, key_chats_sentReadCheck);
        setDrawableColorByKey(dialogs_clockDrawable, key_chats_sentClock);
        setDrawableColorByKey(dialogs_errorDrawable, key_chats_sentErrorIcon);
        setDrawableColorByKey(dialogs_pinnedDrawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_reorderDrawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_muteDrawable, key_chats_muteIcon);
        setDrawableColorByKey(dialogs_unmuteDrawable, key_chats_muteIcon);
        setDrawableColorByKey(dialogs_mentionDrawable, key_chats_mentionIcon);
        setDrawableColorByKey(dialogs_forum_arrowDrawable, key_chats_message);
        setDrawableColorByKey(dialogs_reactionsMentionDrawable, key_chats_mentionIcon);
        setDrawableColorByKey(dialogs_verifiedDrawable, key_chats_verifiedBackground);
        setDrawableColorByKey(dialogs_verifiedCheckDrawable, key_chats_verifiedCheck);
        setDrawableColorByKey(dialogs_holidayDrawable, key_actionBarDefaultTitle);
        setDrawableColorByKey(dialogs_scamDrawable, key_chats_draft);
        setDrawableColorByKey(dialogs_fakeDrawable, key_chats_draft);
    }

    public static void destroyResources() {

    }

    public static void reloadAllResources(Context context) {
        destroyResources();
        if (chat_msgInDrawable != null) {
            chat_msgInDrawable = null;
            currentColor = 0;
            createChatResources(context, false);
        }
        if (dialogs_namePaint != null) {
            dialogs_namePaint = null;
            createDialogsResources(context);
        }
        if (profile_verifiedDrawable != null) {
            profile_verifiedDrawable = null;
            createProfileResources(context);
        }
    }

    public static void createCommonMessageResources() {
        synchronized (sync) {
            if (chat_msgTextPaint == null) {
                chat_msgTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgGameTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintEmoji = new TextPaint[6];
                chat_msgTextPaintOneEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintTwoEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintThreeEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgBotButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgBotButtonPaint.setTypeface(AndroidUtilities.bold());
                chat_namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_namePaint.setTypeface(AndroidUtilities.bold());
                chat_replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_replyNamePaint.setTypeface(AndroidUtilities.bold());
                chat_replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_quoteTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_titleLabelTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_topicTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_topicTextPaint.setTypeface(AndroidUtilities.bold());
                chat_forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_adminPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                chat_msgTextCodePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextCodePaint.setTypeface(Typeface.MONOSPACE);
                chat_msgTextCode2Paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextCode2Paint.setTypeface(Typeface.MONOSPACE);
                chat_msgTextCode3Paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextCode3Paint.setTypeface(Typeface.MONOSPACE);
                chat_msgCodeBgPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            }

            final float[] emojiSizePercents = new float[] {.68f, .46f, .34f, .28f, .22f, .19f};
            for (int i = 0; i < chat_msgTextPaintEmoji.length; ++i) {
                chat_msgTextPaintEmoji[i] = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintEmoji[i].setTextSize(dp(emojiSizePercents[i] * 120f));
            }
            chat_msgTextPaintOneEmoji.setTextSize(dp(28 + 18));
            chat_msgTextPaintTwoEmoji.setTextSize(dp(24 + 14));
            chat_msgTextPaintThreeEmoji.setTextSize(dp(20 + 10));
            chat_msgTextPaint.setTextSize(dp(SharedConfig.fontSize));
            chat_msgGameTextPaint.setTextSize(dp(14));
            chat_msgBotButtonPaint.setTextSize(dp(15));
            float smallerDp = (2 * SharedConfig.fontSize + 10) / 3f; // 6f + SharedConfig.fontSize / 2f;
            chat_namePaint.setTextSize(dp(smallerDp));
            chat_replyNamePaint.setTextSize(dp(smallerDp));
            chat_replyTextPaint.setTextSize(dp(smallerDp));
            chat_quoteTextPaint.setTextSize(dp(smallerDp - 1));
            chat_topicTextPaint.setTextSize(dp(smallerDp - 1));
            chat_titleLabelTextPaint.setTextSize(dp(smallerDp - 2));
            chat_forwardNamePaint.setTextSize(dp(smallerDp));
            chat_adminPaint.setTextSize(dp(smallerDp - 1));
            chat_msgTextCodePaint.setTextSize(dp(Math.max(Math.min(10, SharedConfig.fontSize - 1), SharedConfig.fontSize - 2)));
            chat_msgTextCode2Paint.setTextSize(dp(Math.max(Math.min(10, SharedConfig.fontSize - 2), SharedConfig.fontSize - 3)));
            chat_msgTextCode3Paint.setTextSize(dp(Math.max(Math.min(10, SharedConfig.fontSize - 2), SharedConfig.fontSize - 5)));
        }
    }

    public static void createCommonChatResources() {
        createCommonMessageResources();

        if (chat_infoPaint == null) {
            chat_infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_infoBoldPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_infoBoldPaint.setTypeface(AndroidUtilities.bold());
            chat_stickerCommentCountPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_stickerCommentCountPaint.setTypeface(AndroidUtilities.bold());
            chat_docNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_docNamePaint.setTypeface(AndroidUtilities.bold());
            chat_docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_deleteProgressPaint.setStyle(Paint.Style.STROKE);
            chat_deleteProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_locationTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_locationTitlePaint.setTypeface(AndroidUtilities.bold());
            chat_locationAddressPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_urlPaint = new Paint();
            chat_urlPaint.setPathEffect(LinkPath.getRoundedEffect());
            chat_outUrlPaint = new Paint();
            chat_outUrlPaint.setPathEffect(LinkPath.getRoundedEffect());
            chat_textSearchSelectionPaint = new Paint();
            chat_textSearchSelectionPaint.setPathEffect(LinkPath.getRoundedEffect());
            chat_radialProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_radialProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_radialProgressPaint.setStyle(Paint.Style.STROKE);
            chat_radialProgressPaint.setColor(0x9fffffff);
            chat_radialProgress2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_radialProgress2Paint.setStrokeCap(Paint.Cap.ROUND);
            chat_radialProgress2Paint.setStyle(Paint.Style.STROKE);
            chat_audioTimePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_livePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_livePaint.setTypeface(Typeface.DEFAULT_BOLD);
            chat_audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_audioTitlePaint.setTypeface(AndroidUtilities.bold());
            chat_audioPerformerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_botButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_botButtonPaint.setTypeface(AndroidUtilities.bold());
            chat_contactNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_contactNamePaint.setTypeface(AndroidUtilities.bold());
            chat_contactPhonePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_gamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_gamePaint.setTypeface(AndroidUtilities.bold());
            chat_shipmentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_adminPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_namePaint.setTypeface(AndroidUtilities.bold());
            chat_forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_replyNamePaint.setTypeface(AndroidUtilities.bold());
            chat_replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_topicTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_topicTextPaint.setTypeface(AndroidUtilities.bold());
            chat_titleLabelTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_commentTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_instantViewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_instantViewPaint.setTypeface(AndroidUtilities.bold());
            chat_instantViewRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_instantViewRectPaint.setStyle(Paint.Style.STROKE);
            chat_instantViewRectPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_instantViewButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_pollTimerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_pollTimerPaint.setStyle(Paint.Style.STROKE);
            chat_pollTimerPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_replyLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_msgErrorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusRecordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusRecordPaint.setStyle(Paint.Style.STROKE);
            chat_statusRecordPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_actionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_actionTextPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_actionTextPaint.setTypeface(AndroidUtilities.bold());
            chat_unlockExtendedMediaTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_unlockExtendedMediaTextPaint.setTypeface(AndroidUtilities.bold());
            chat_actionBackgroundGradientDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_actionBackgroundGradientDarkenPaint.setColor(0x15000000);
            chat_timeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_contextResult_titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_contextResult_titleTextPaint.setTypeface(AndroidUtilities.bold());
            chat_contextResult_descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_composeBackgroundPaint = new Paint();
            chat_radialProgressPausedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_radialProgressPausedSeekbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            chat_messageBackgroundSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_actionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            chat_actionBackgroundSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

            addChatPaint(key_paint_chatMessageBackgroundSelected, chat_messageBackgroundSelectedPaint, key_chat_selectedBackground);
            addChatPaint(key_paint_chatActionBackground, chat_actionBackgroundPaint, key_chat_serviceBackground);
            addChatPaint(key_paint_chatActionBackgroundDarken, chat_actionBackgroundGradientDarkenPaint, key_chat_serviceBackground);
            addChatPaint(key_paint_chatActionBackgroundSelected, chat_actionBackgroundSelectedPaint, key_chat_serviceBackgroundSelected);
            addChatPaint(key_paint_chatActionText, chat_actionTextPaint, key_chat_serviceText);
            addChatPaint(key_paint_chatActionText2, chat_actionTextPaint2, key_chat_serviceText);
            addChatPaint(key_paint_chatBotButton, chat_botButtonPaint, key_chat_botButtonText);
            addChatPaint(key_paint_chatComposeBackground, chat_composeBackgroundPaint, key_chat_messagePanelBackground);
            addChatPaint(key_paint_chatTimeBackground, chat_timeBackgroundPaint, key_chat_mediaTimeBackground);
        }
    }

    public static void createChatResources(Context context, boolean fontsOnly) {
        createCommonChatResources();

        if (!fontsOnly && chat_msgInDrawable == null) {

            Resources resources = context.getResources();

            chat_msgNoSoundDrawable = resources.getDrawable(R.drawable.video_muted);

            chat_msgInDrawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false);
            chat_msgInSelectedDrawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, true);
            chat_msgOutDrawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false);
            chat_msgOutSelectedDrawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, true);
            chat_msgInMediaDrawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, false, false);
            chat_msgInMediaSelectedDrawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, false, true);
            chat_msgOutMediaDrawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, false);
            chat_msgOutMediaSelectedDrawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, true);

            playPauseAnimator = new PathAnimator(0.293f, -26, -28, 1.0f);
            playPauseAnimator.addSvgKeyFrame("M 34.141 16.042 C 37.384 17.921 40.886 20.001 44.211 21.965 C 46.139 23.104 49.285 24.729 49.586 25.917 C 50.289 28.687 48.484 30 46.274 30 L 6 30.021 C 3.79 30.021 2.075 30.023 2 26.021 L 2.009 3.417 C 2.009 0.417 5.326 -0.58 7.068 0.417 C 10.545 2.406 25.024 10.761 34.141 16.042 Z", 166);
            playPauseAnimator.addSvgKeyFrame("M 37.843 17.769 C 41.143 19.508 44.131 21.164 47.429 23.117 C 48.542 23.775 49.623 24.561 49.761 25.993 C 50.074 28.708 48.557 30 46.347 30 L 6 30.012 C 3.79 30.012 2 28.222 2 26.012 L 2.009 4.609 C 2.009 1.626 5.276 0.664 7.074 1.541 C 10.608 3.309 28.488 12.842 37.843 17.769 Z", 200);
            playPauseAnimator.addSvgKeyFrame("M 40.644 18.756 C 43.986 20.389 49.867 23.108 49.884 25.534 C 49.897 27.154 49.88 24.441 49.894 26.059 C 49.911 28.733 48.6 30 46.39 30 L 6 30.013 C 3.79 30.013 2 28.223 2 26.013 L 2.008 5.52 C 2.008 2.55 5.237 1.614 7.079 2.401 C 10.656 4 31.106 14.097 40.644 18.756 Z", 217);
            playPauseAnimator.addSvgKeyFrame("M 43.782 19.218 C 47.117 20.675 50.075 21.538 50.041 24.796 C 50.022 26.606 50.038 24.309 50.039 26.104 C 50.038 28.736 48.663 30 46.453 30 L 6 29.986 C 3.79 29.986 2 28.196 2 25.986 L 2.008 6.491 C 2.008 3.535 5.196 2.627 7.085 3.316 C 10.708 4.731 33.992 14.944 43.782 19.218 Z", 234);
            playPauseAnimator.addSvgKeyFrame("M 47.421 16.941 C 50.544 18.191 50.783 19.91 50.769 22.706 C 50.761 24.484 50.76 23.953 50.79 26.073 C 50.814 27.835 49.334 30 47.124 30 L 5 30.01 C 2.79 30.01 1 28.22 1 26.01 L 1.001 10.823 C 1.001 8.218 3.532 6.895 5.572 7.26 C 7.493 8.01 47.421 16.941 47.421 16.941 Z", 267);
            playPauseAnimator.addSvgKeyFrame("M 47.641 17.125 C 50.641 18.207 51.09 19.935 51.078 22.653 C 51.07 24.191 51.062 21.23 51.088 23.063 C 51.109 24.886 49.587 27 47.377 27 L 5 27.009 C 2.79 27.009 1 25.219 1 23.009 L 0.983 11.459 C 0.983 8.908 3.414 7.522 5.476 7.838 C 7.138 8.486 47.641 17.125 47.641 17.125 Z", 300);
            playPauseAnimator.addSvgKeyFrame("M 48 7 C 50.21 7 52 8.79 52 11 C 52 19 52 19 52 19 C 52 21.21 50.21 23 48 23 L 4 23 C 1.79 23 0 21.21 0 19 L 0 11 C 0 8.79 1.79 7 4 7 C 48 7 48 7 48 7 Z", 383);

            chat_msgOutCheckDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgOutCheckSelectedDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgOutCheckReadDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgOutCheckReadSelectedDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgMediaCheckDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgStickerCheckDrawable = resources.getDrawable(R.drawable.msg_check_s).mutate();
            chat_msgOutHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgOutHalfCheckSelectedDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgMediaHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck_s).mutate();
            chat_msgStickerHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck_s).mutate();
            chat_msgClockDrawable = new MsgClockDrawable();
            chat_msgUnlockDrawable = resources.getDrawable(R.drawable.ic_lock_header).mutate();
            chat_msgInViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgInViewsSelectedDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgOutViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgOutViewsSelectedDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgInRepliesDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgInRepliesSelectedDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgOutRepliesDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgOutRepliesSelectedDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgInPinnedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgInPinnedSelectedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgOutPinnedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgOutPinnedSelectedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgMediaPinnedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgStickerPinnedDrawable = resources.getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_msgMediaViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgMediaRepliesDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgStickerViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgStickerRepliesDrawable = resources.getDrawable(R.drawable.msg_reply_small).mutate();
            chat_msgInMenuDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgInMenuSelectedDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgOutMenuDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgOutMenuSelectedDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgMediaMenuDrawable = resources.getDrawable(R.drawable.video_actions);
            chat_msgInInstantDrawable = resources.getDrawable(R.drawable.msg_instant).mutate();
            chat_msgOutInstantDrawable = resources.getDrawable(R.drawable.msg_instant).mutate();
            chat_msgErrorDrawable = resources.getDrawable(R.drawable.msg_warning);
            chat_muteIconDrawable = resources.getDrawable(R.drawable.list_mute).mutate();
            chat_lockIconDrawable = resources.getDrawable(R.drawable.ic_lock_header);
            chat_msgInCallDrawable[0] = resources.getDrawable(R.drawable.chat_calls_voice).mutate();
            chat_msgInCallSelectedDrawable[0] = resources.getDrawable(R.drawable.chat_calls_voice).mutate();
            chat_msgOutCallDrawable[0] = resources.getDrawable(R.drawable.chat_calls_voice).mutate();
            chat_msgOutCallSelectedDrawable[0] = resources.getDrawable(R.drawable.chat_calls_voice).mutate();
            chat_msgInCallDrawable[1] = resources.getDrawable(R.drawable.chat_calls_video).mutate();
            chat_msgInCallSelectedDrawable[1] = resources.getDrawable(R.drawable.chat_calls_video).mutate();
            chat_msgOutCallDrawable[1] = resources.getDrawable(R.drawable.chat_calls_video).mutate();
            chat_msgOutCallSelectedDrawable[1] = resources.getDrawable(R.drawable.chat_calls_video).mutate();
            chat_msgCallUpGreenDrawable = resources.getDrawable(R.drawable.chat_calls_outgoing).mutate();
            chat_msgCallDownRedDrawable = resources.getDrawable(R.drawable.chat_calls_incoming).mutate();
            chat_msgCallDownGreenDrawable = resources.getDrawable(R.drawable.chat_calls_incoming).mutate();
            for (int a = 0; a < 2; a++) {
                chat_pollCheckDrawable[a] = resources.getDrawable(R.drawable.poll_right).mutate();
                chat_pollCrossDrawable[a] = resources.getDrawable(R.drawable.poll_wrong).mutate();
                chat_pollHintDrawable[a] = resources.getDrawable(R.drawable.msg_emoji_objects).mutate();
                chat_psaHelpDrawable[a] = resources.getDrawable(R.drawable.msg_psa).mutate();
            }

            calllog_msgCallUpRedDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            calllog_msgCallUpGreenDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            calllog_msgCallDownRedDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            calllog_msgCallDownGreenDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();

            chat_inlineResultFile = resources.getDrawable(R.drawable.bot_file);
            chat_inlineResultAudio = resources.getDrawable(R.drawable.bot_music);
            chat_inlineResultLocation = resources.getDrawable(R.drawable.bot_location);

            chat_botLinkDrawable = resources.getDrawable(R.drawable.bot_link);
            chat_botInlineDrawable = resources.getDrawable(R.drawable.bot_lines);
            chat_botCardDrawable = resources.getDrawable(R.drawable.bot_card);
            chat_botWebViewDrawable = resources.getDrawable(R.drawable.bot_webview);
            chat_botInviteDrawable = resources.getDrawable(R.drawable.bot_invite);

            chat_commentDrawable = resources.getDrawable(R.drawable.msg_msgbubble);
            chat_commentStickerDrawable = resources.getDrawable(R.drawable.msg_msgbubble2);
            chat_commentArrowDrawable = resources.getDrawable(R.drawable.msg_arrowright);
            chat_gradientLeftDrawable = resources.getDrawable(R.drawable.gradient_left);
            chat_gradientRightDrawable = resources.getDrawable(R.drawable.gradient_right);

            chat_contextResult_shadowUnderSwitchDrawable = resources.getDrawable(R.drawable.header_shadow).mutate();

            chat_attachButtonDrawables[0] = new RLottieDrawable(R.raw.attach_gallery, "attach_gallery", dp(26), dp(26));
            chat_attachButtonDrawables[1] = new RLottieDrawable(R.raw.attach_music, "attach_music", dp(26), dp(26));
            chat_attachButtonDrawables[2] = new RLottieDrawable(R.raw.attach_file, "attach_file", dp(26), dp(26));
            chat_attachButtonDrawables[3] = new RLottieDrawable(R.raw.attach_contact, "attach_contact", dp(26), dp(26));
            chat_attachButtonDrawables[4] = new RLottieDrawable(R.raw.attach_location, "attach_location", dp(26), dp(26));
            chat_attachButtonDrawables[5] = new RLottieDrawable(R.raw.attach_poll, "attach_poll", dp(26), dp(26));
            chat_attachEmptyDrawable = resources.getDrawable(R.drawable.nophotos3);

            chat_shareIconDrawable = resources.getDrawable(R.drawable.filled_button_share).mutate();
            chat_replyIconDrawable = resources.getDrawable(R.drawable.filled_button_reply);
            chat_closeIconDrawable = resources.getDrawable(R.drawable.msg_voiceclose).mutate();
            chat_moreIconDrawable = resources.getDrawable(R.drawable.media_more).mutate();
            chat_goIconDrawable = resources.getDrawable(R.drawable.filled_open_message);

            int rad = dp(2);
            RectF rect = new RectF();
            chat_filePath[0] = new Path();
            chat_filePath[0].moveTo(dp(7), dp(3));
            chat_filePath[0].lineTo(dp(14), dp(3));
            chat_filePath[0].lineTo(dp(21), dp(10));
            chat_filePath[0].lineTo(dp(21), dp(20));
            rect.set(dp(21) - rad * 2, dp(19) - rad, dp(21), dp(19) + rad);
            chat_filePath[0].arcTo(rect, 0, 90, false);
            chat_filePath[0].lineTo(dp(6), dp(21));
            rect.set(dp(5), dp(19) - rad, dp(5) + rad * 2, dp(19) + rad);
            chat_filePath[0].arcTo(rect, 90, 90, false);
            chat_filePath[0].lineTo(dp(5), dp(4));
            rect.set(dp(5), dp(3), dp(5) + rad * 2, dp(3) + rad * 2);
            chat_filePath[0].arcTo(rect, 180, 90, false);
            chat_filePath[0].close();

            chat_filePath[1] = new Path();
            chat_filePath[1].moveTo(dp(14), dp(5));
            chat_filePath[1].lineTo(dp(19), dp(10));
            chat_filePath[1].lineTo(dp(14), dp(10));
            chat_filePath[1].close();

            chat_flameIcon = resources.getDrawable(R.drawable.filled_fire).mutate();
            chat_gifIcon = resources.getDrawable(R.drawable.msg_round_gif_m).mutate();

            chat_fileStatesDrawable[0][0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[0][1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[1][0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[1][1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[2][0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[2][1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[3][0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[3][1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[4][0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_cancel_m);
            chat_fileStatesDrawable[4][1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_round_cancel_m);

            chat_contactDrawable[0] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_contact);
            chat_contactDrawable[1] = createCircleDrawableWithIcon(dp(44), R.drawable.msg_contact);

            chat_locationDrawable[0] = resources.getDrawable(R.drawable.msg_location).mutate();
            chat_locationDrawable[1] = resources.getDrawable(R.drawable.msg_location).mutate();

            chat_composeShadowDrawable = context.getResources().getDrawable(R.drawable.compose_panel_shadow).mutate();
            chat_composeShadowRoundDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

            chat_roundVideoShadow = new RoundVideoShadow();

            defaultChatDrawables.clear();
            defaultChatDrawableColorKeys.clear();

            addChatDrawable(key_drawable_botInline, chat_botInlineDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_botWebView, chat_botWebViewDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_botLink, chat_botLinkDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_botInvite, chat_botInviteDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_goIcon, chat_goIconDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_commentSticker, chat_commentStickerDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_msgError, chat_msgErrorDrawable, key_chat_sentErrorIcon);
            addChatDrawable(key_drawable_msgIn, chat_msgInDrawable, -1);
            addChatDrawable(key_drawable_msgInSelected, chat_msgInSelectedDrawable, -1);
            addChatDrawable(key_drawable_msgInMedia, chat_msgInMediaDrawable, -1);
            addChatDrawable(key_drawable_msgInMediaSelected, chat_msgInMediaSelectedDrawable, -1);
            addChatDrawable(key_drawable_msgInInstant, chat_msgInInstantDrawable, key_chat_inInstant);
            addChatDrawable(key_drawable_msgOut, chat_msgOutDrawable, -1);
            addChatDrawable(key_drawable_msgOutSelected, chat_msgOutSelectedDrawable, -1);
            addChatDrawable(key_drawable_msgOutMedia, chat_msgOutMediaDrawable, -1);
            addChatDrawable(key_drawable_msgOutMediaSelected, chat_msgOutMediaSelectedDrawable, -1);
            addChatDrawable(key_drawable_msgOutCallAudio, chat_msgOutCallDrawable[0], key_chat_outInstant);
            addChatDrawable(key_drawable_msgOutCallAudioSelected, chat_msgOutCallSelectedDrawable[0], key_chat_outInstantSelected);
            addChatDrawable(key_drawable_msgOutCallVideo, chat_msgOutCallDrawable[1], key_chat_outInstant);
            addChatDrawable(key_drawable_msgOutCallVideoSelected, chat_msgOutCallSelectedDrawable[1], key_chat_outInstantSelected);
            addChatDrawable(key_drawable_msgOutCheck, chat_msgOutCheckDrawable, key_chat_outSentCheck);
            addChatDrawable(key_drawable_msgOutCheckSelected, chat_msgOutCheckSelectedDrawable, key_chat_outSentCheckSelected);
            addChatDrawable(key_drawable_msgOutCheckRead, chat_msgOutCheckReadDrawable, key_chat_outSentCheckRead);
            addChatDrawable(key_drawable_msgOutCheckReadSelected, chat_msgOutCheckReadSelectedDrawable, key_chat_outSentCheckReadSelected);
            addChatDrawable(key_drawable_msgOutHalfCheck, chat_msgOutHalfCheckDrawable, key_chat_outSentCheckRead);
            addChatDrawable(key_drawable_msgOutHalfCheckSelected, chat_msgOutHalfCheckSelectedDrawable, key_chat_outSentCheckReadSelected);
            addChatDrawable(key_drawable_msgOutInstant, chat_msgOutInstantDrawable, key_chat_outInstant);
            addChatDrawable(key_drawable_msgOutMenu, chat_msgOutMenuDrawable, key_chat_outMenu);
            addChatDrawable(key_drawable_msgOutMenuSelected, chat_msgOutMenuSelectedDrawable, key_chat_outMenuSelected);
            addChatDrawable(key_drawable_msgOutPinned, chat_msgOutPinnedDrawable, key_chat_outViews);
            addChatDrawable(key_drawable_msgOutPinnedSelected, chat_msgOutPinnedSelectedDrawable, key_chat_outViewsSelected);
            addChatDrawable(key_drawable_msgOutReplies, chat_msgOutRepliesDrawable, key_chat_outViews);
            addChatDrawable(key_drawable_msgOutRepliesSelected, chat_msgOutRepliesSelectedDrawable, key_chat_outViewsSelected);
            addChatDrawable(key_drawable_msgOutViews, chat_msgOutViewsDrawable, key_chat_outViews);
            addChatDrawable(key_drawable_msgOutViewsSelected, chat_msgOutViewsSelectedDrawable, key_chat_outViewsSelected);
            addChatDrawable(key_drawable_msgStickerCheck, chat_msgStickerCheckDrawable, key_chat_serviceText);
            addChatDrawable(key_drawable_msgStickerHalfCheck, chat_msgStickerHalfCheckDrawable, key_chat_serviceText);
            addChatDrawable(key_drawable_msgStickerPinned, chat_msgStickerPinnedDrawable, key_chat_serviceText);
            addChatDrawable(key_drawable_msgStickerReplies, chat_msgStickerRepliesDrawable, key_chat_serviceText);
            addChatDrawable(key_drawable_msgStickerViews, chat_msgStickerViewsDrawable, key_chat_serviceText);
            addChatDrawable(key_drawable_replyIcon, chat_replyIconDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_closeIcon, chat_closeIconDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_moreIcon, chat_moreIconDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_shareIcon, chat_shareIconDrawable, key_chat_serviceIcon);
            addChatDrawable(key_drawable_muteIconDrawable, chat_muteIconDrawable, key_chat_muteIcon);
            addChatDrawable(key_drawable_lockIconDrawable, chat_lockIconDrawable, key_chat_lockIcon);
            addChatDrawable(key_drawable_chat_pollHintDrawableOut, chat_pollHintDrawable[1], key_chat_outPreviewInstantText);
            addChatDrawable(key_drawable_chat_pollHintDrawableIn, chat_pollHintDrawable[0], key_chat_inPreviewInstantText);

            applyChatTheme(fontsOnly, false);
        }

        if (!fontsOnly && chat_infoPaint != null) {
            chat_infoPaint.setTextSize(dp(12));
            chat_infoBoldPaint.setTextSize(dp(12));
            chat_stickerCommentCountPaint.setTextSize(dp(11));
            chat_docNamePaint.setTextSize(dp(15));
            chat_locationTitlePaint.setTextSize(dp(15));
            chat_locationAddressPaint.setTextSize(dp(13));
            chat_audioTimePaint.setTextSize(dp(12));
            chat_livePaint.setTextSize(dp(12));
            chat_audioTitlePaint.setTextSize(dp(16));
            chat_audioPerformerPaint.setTextSize(dp(15));
            chat_botButtonPaint.setTextSize(dp(15));
            chat_contactNamePaint.setTextSize(dp(15));
            chat_contactPhonePaint.setTextSize(dp(13));
            chat_durationPaint.setTextSize(dp(12));
            float smallerDp = (2 * SharedConfig.fontSize + 10) / 3f; // 6f + SharedConfig.fontSize / 2f;
            chat_namePaint.setTextSize(dp(smallerDp));
            chat_replyNamePaint.setTextSize(dp(smallerDp));
            chat_replyTextPaint.setTextSize(dp(smallerDp));
            chat_topicTextPaint.setTextSize(dp(smallerDp - 1));
            chat_forwardNamePaint.setTextSize(dp(smallerDp));
            chat_adminPaint.setTextSize(dp(smallerDp - 1));
            float timeDp = 2 * (SharedConfig.fontSize - 16) / 3f + 12;
            chat_timePaint.setTextSize(dp(12));
            chat_gamePaint.setTextSize(dp(13));
            chat_shipmentPaint.setTextSize(dp(13));
            chat_instantViewPaint.setTextSize(dp(13));
            chat_instantViewRectPaint.setStrokeWidth(dp(1));
            chat_pollTimerPaint.setStrokeWidth(dp(1.1f));
            chat_actionTextPaint.setTextSize(dp(Math.max(16, SharedConfig.fontSize) - 2));
            chat_actionTextPaint2.setTextSize(dp(Math.max(16, SharedConfig.fontSize) - 2));
            chat_unlockExtendedMediaTextPaint.setTextSize(dp(Math.max(16, SharedConfig.fontSize)));
            chat_contextResult_titleTextPaint.setTextSize(dp(15));
            chat_contextResult_descriptionTextPaint.setTextSize(dp(13));
            chat_radialProgressPaint.setStrokeWidth(dp(3));
            chat_radialProgress2Paint.setStrokeWidth(dp(2.33f));
            chat_commentTextPaint.setTextSize(dp(14));
            chat_commentTextPaint.setTypeface(AndroidUtilities.bold());
        }
    }

    public static void refreshAttachButtonsColors() {
        for (int a = 0; a < chat_attachButtonDrawables.length; a++) {
            if (chat_attachButtonDrawables[a] == null) {
                continue;
            }
            chat_attachButtonDrawables[a].beginApplyLayerColors();
            if (a == 0) {
                chat_attachButtonDrawables[a].setLayerColor("Color_Mount.**", getNonAnimatedColor(key_chat_attachGalleryBackground));
                chat_attachButtonDrawables[a].setLayerColor("Color_PhotoShadow.**", getNonAnimatedColor(key_chat_attachGalleryBackground));
                chat_attachButtonDrawables[a].setLayerColor("White_Photo.**", getNonAnimatedColor(key_chat_attachIcon));
                chat_attachButtonDrawables[a].setLayerColor("White_BackPhoto.**", getNonAnimatedColor(key_chat_attachIcon));
            } else if (a == 1) {
                chat_attachButtonDrawables[a].setLayerColor("White_Play1.**", getNonAnimatedColor(key_chat_attachIcon));
                chat_attachButtonDrawables[a].setLayerColor("White_Play2.**", getNonAnimatedColor(key_chat_attachIcon));
            } else if (a == 2) {
                chat_attachButtonDrawables[a].setLayerColor("Color_Corner.**", getNonAnimatedColor(key_chat_attachFileBackground));
                chat_attachButtonDrawables[a].setLayerColor("White_List.**", getNonAnimatedColor(key_chat_attachIcon));
            } else if (a == 3) {
                chat_attachButtonDrawables[a].setLayerColor("White_User1.**", getNonAnimatedColor(key_chat_attachIcon));
                chat_attachButtonDrawables[a].setLayerColor("White_User2.**", getNonAnimatedColor(key_chat_attachIcon));
            } else if (a == 4) {
                chat_attachButtonDrawables[a].setLayerColor("Color_Oval.**", getNonAnimatedColor(key_chat_attachLocationBackground));
                chat_attachButtonDrawables[a].setLayerColor("White_Pin.**", getNonAnimatedColor(key_chat_attachIcon));
            } else if (a == 5) {
                chat_attachButtonDrawables[a].setLayerColor("White_Column 1.**", getNonAnimatedColor(key_chat_attachIcon));
                chat_attachButtonDrawables[a].setLayerColor("White_Column 2.**", getNonAnimatedColor(key_chat_attachIcon));
                chat_attachButtonDrawables[a].setLayerColor("White_Column 3.**", getNonAnimatedColor(key_chat_attachIcon));
            }
            chat_attachButtonDrawables[a].commitApplyLayerColors();
        }

    }

    public static void applyChatTheme(boolean fontsOnly, boolean bg) {
        if (chat_msgTextPaint == null) {
            return;
        }

        if (chat_msgInDrawable != null && !fontsOnly) {
            chat_gamePaint.setColor(getColor(key_chat_previewGameText));
            chat_durationPaint.setColor(getColor(key_chat_previewDurationText));
            chat_botButtonPaint.setColor(getColor(key_chat_botButtonText));
            chat_urlPaint.setColor(getColor(key_chat_linkSelectBackground));
            chat_outUrlPaint.setColor(getColor(key_chat_outLinkSelectBackground));
            chat_textSearchSelectionPaint.setColor(getColor(key_chat_textSelectBackground));
            chat_msgErrorPaint.setColor(getColor(key_chat_sentError));
            chat_statusPaint.setColor(getColor(key_chat_status));
            chat_statusRecordPaint.setColor(getColor(key_chat_status));
            chat_actionTextPaint.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint2.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint.linkColor = getColor(key_chat_serviceLink);
            chat_unlockExtendedMediaTextPaint.setColor(getColor(key_chat_serviceText));
            chat_contextResult_titleTextPaint.setColor(getColor(key_windowBackgroundWhiteBlackText));
            chat_composeBackgroundPaint.setColor(getColor(key_chat_messagePanelBackground));
            chat_timeBackgroundPaint.setColor(getColor(key_chat_mediaTimeBackground));

            setDrawableColorByKey(chat_msgNoSoundDrawable, key_chat_mediaTimeText);
            setDrawableColorByKey(chat_msgInDrawable, key_chat_inBubble);
            setDrawableColorByKey(chat_msgInSelectedDrawable, key_chat_inBubbleSelected);
            setDrawableColorByKey(chat_msgInMediaDrawable, key_chat_inBubble);
            setDrawableColorByKey(chat_msgInMediaSelectedDrawable, key_chat_inBubbleSelected);
            setDrawableColorByKey(chat_msgOutCheckDrawable, key_chat_outSentCheck);
            setDrawableColorByKey(chat_msgOutCheckSelectedDrawable, key_chat_outSentCheckSelected);
            setDrawableColorByKey(chat_msgOutCheckReadDrawable, key_chat_outSentCheckRead);
            setDrawableColorByKey(chat_msgOutCheckReadSelectedDrawable, key_chat_outSentCheckReadSelected);
            setDrawableColorByKey(chat_msgOutHalfCheckDrawable, key_chat_outSentCheckRead);
            setDrawableColorByKey(chat_msgOutHalfCheckSelectedDrawable, key_chat_outSentCheckReadSelected);
            setDrawableColorByKey(chat_msgMediaCheckDrawable, key_chat_mediaSentCheck);
            setDrawableColorByKey(chat_msgMediaHalfCheckDrawable, key_chat_mediaSentCheck);
            setDrawableColorByKey(chat_msgStickerCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerHalfCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerViewsDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerRepliesDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgUnlockDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_shareIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_replyIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_goIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botInlineDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botWebViewDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botInviteDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botLinkDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_msgInViewsDrawable, key_chat_inViews);
            setDrawableColorByKey(chat_msgInViewsSelectedDrawable, key_chat_inViewsSelected);
            setDrawableColorByKey(chat_msgOutViewsDrawable, key_chat_outViews);
            setDrawableColorByKey(chat_msgOutViewsSelectedDrawable, key_chat_outViewsSelected);
            setDrawableColorByKey(chat_msgInRepliesDrawable, key_chat_inViews);
            setDrawableColorByKey(chat_msgInRepliesSelectedDrawable, key_chat_inViewsSelected);
            setDrawableColorByKey(chat_msgOutRepliesDrawable, key_chat_outViews);
            setDrawableColorByKey(chat_msgOutRepliesSelectedDrawable, key_chat_outViewsSelected);
            setDrawableColorByKey(chat_msgInPinnedDrawable, key_chat_inViews);
            setDrawableColorByKey(chat_msgInPinnedSelectedDrawable, key_chat_inViewsSelected);
            setDrawableColorByKey(chat_msgOutPinnedDrawable, key_chat_outViews);
            setDrawableColorByKey(chat_msgOutPinnedSelectedDrawable, key_chat_outViewsSelected);
            setDrawableColorByKey(chat_msgMediaPinnedDrawable, key_chat_mediaViews);
            setDrawableColorByKey(chat_msgStickerPinnedDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgMediaViewsDrawable, key_chat_mediaViews);
            setDrawableColorByKey(chat_msgMediaRepliesDrawable, key_chat_mediaViews);
            setDrawableColorByKey(chat_msgInMenuDrawable, key_chat_inMenu);
            setDrawableColorByKey(chat_msgInMenuSelectedDrawable, key_chat_inMenuSelected);
            setDrawableColorByKey(chat_msgOutMenuDrawable, key_chat_outMenu);
            setDrawableColorByKey(chat_msgOutMenuSelectedDrawable, key_chat_outMenuSelected);
            setDrawableColorByKey(chat_msgMediaMenuDrawable, key_chat_mediaMenu);
            setDrawableColorByKey(chat_msgOutInstantDrawable, key_chat_outInstant);
            setDrawableColorByKey(chat_msgInInstantDrawable, key_chat_inInstant);
            setDrawableColorByKey(chat_msgErrorDrawable, key_chat_sentErrorIcon);
            setDrawableColorByKey(chat_muteIconDrawable, key_chat_muteIcon);
            setDrawableColorByKey(chat_lockIconDrawable, key_chat_lockIcon);
            setDrawableColorByKey(chat_inlineResultFile, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_inlineResultAudio, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_inlineResultLocation, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_commentDrawable, key_chat_inInstant);
            setDrawableColorByKey(chat_commentStickerDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_commentArrowDrawable, key_chat_inInstant);
            setDrawableColorByKey(chat_gradientLeftDrawable, key_chat_stickersHintPanel);
            setDrawableColorByKey(chat_gradientRightDrawable, key_chat_stickersHintPanel);

            for (int a = 0; a < 2; a++) {
                setDrawableColorByKey(chat_msgInCallDrawable[a], key_chat_inInstant);
                setDrawableColorByKey(chat_msgInCallSelectedDrawable[a], key_chat_inInstantSelected);
                setDrawableColorByKey(chat_msgOutCallDrawable[a], key_chat_outInstant);
                setDrawableColorByKey(chat_msgOutCallSelectedDrawable[a], key_chat_outInstantSelected);
            }

            setDrawableColorByKey(chat_msgCallUpGreenDrawable, key_chat_outGreenCall);
            setDrawableColorByKey(chat_msgCallDownRedDrawable, key_fill_RedNormal);
            setDrawableColorByKey(chat_msgCallDownGreenDrawable, key_chat_inGreenCall);

            setDrawableColorByKey(calllog_msgCallUpRedDrawable, key_fill_RedNormal);
            setDrawableColorByKey(calllog_msgCallUpGreenDrawable, key_calls_callReceivedGreenIcon);
            setDrawableColorByKey(calllog_msgCallDownRedDrawable, key_fill_RedNormal);
            setDrawableColorByKey(calllog_msgCallDownGreenDrawable, key_calls_callReceivedGreenIcon);

            for (int i = 0; i < chat_status_drawables.length; i++) {
                setDrawableColorByKey(chat_status_drawables[i], key_chats_actionMessage);
            }

            for (int a = 0; a < 5; a++) {
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_inLoader), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_inMediaIcon), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_inLoaderSelected), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_inMediaIconSelected), true);
            }

            setCombinedDrawableColor(chat_contactDrawable[0], getColor(key_chat_inContactBackground), false);
            setCombinedDrawableColor(chat_contactDrawable[0], getColor(key_chat_inContactIcon), true);
            setCombinedDrawableColor(chat_contactDrawable[1], getColor(key_chat_outContactBackground), false);
            setCombinedDrawableColor(chat_contactDrawable[1], getColor(key_chat_outContactIcon), true);

            setDrawableColor(chat_locationDrawable[0], getColor(key_chat_inLocationIcon));
            setDrawableColor(chat_locationDrawable[1], getColor(key_chat_outLocationIcon));

            setDrawableColor(chat_pollHintDrawable[0], getColor(key_chat_inPreviewInstantText));
            setDrawableColor(chat_pollHintDrawable[1], getColor(key_chat_outPreviewInstantText));

            setDrawableColor(chat_psaHelpDrawable[0], getColor(key_chat_inViews));
            setDrawableColor(chat_psaHelpDrawable[1], getColor(key_chat_outViews));

            setDrawableColorByKey(chat_composeShadowDrawable, key_chat_messagePanelShadow);
            setDrawableColorByKey(chat_composeShadowRoundDrawable, key_chat_messagePanelBackground);

            int color = getColor(key_chat_outAudioSeekbarFill);
            if (color == 0xffffffff) {
                color = Theme.getColor(Theme.key_chat_outBubble);
            } else {
                color = 0xffffffff;
            }
            setDrawableColor(chat_pollCheckDrawable[1], color);
            setDrawableColor(chat_pollCrossDrawable[1], color);

            setDrawableColor(chat_attachEmptyDrawable, getColor(key_chat_attachEmptyImage));

            if (!bg && !disallowChangeServiceMessageColor) {
                applyChatServiceMessageColor();
                applyChatMessageSelectedBackgroundColor();
            }
            refreshAttachButtonsColors();
        }
    }

    public static void applyChatServiceMessageColor() {
        if (wallpaper != null) {
            applyChatServiceMessageColor(null, null, wallpaper);
        }
    }

    public static boolean hasGradientService() {
        return serviceBitmapShader != null;
    }

    private static int[] viewPos = new int[2];

    public static void applyServiceShaderMatrixForView(View view, View background) {
        applyServiceShaderMatrixForView(view, background, null);
    }

    public static void applyServiceShaderMatrixForView(View view, View background, ResourcesProvider resourcesProvider) {
        if (view == null || background == null) {
            return;
        }
        view.getLocationOnScreen(viewPos);
        int x = viewPos[0];
        int y = viewPos[1];
        background.getLocationOnScreen(viewPos);
        if (background instanceof ThemePreviewActivity.BackgroundView) {
            if (serviceBitmap != null) {
                float bitmapWidth = serviceBitmap.getWidth();
                float bitmapHeight = serviceBitmap.getHeight();
                float maxScale = Math.max(background.getMeasuredWidth() / bitmapWidth, background.getMeasuredHeight() / bitmapHeight);
                float width = bitmapWidth * maxScale;
                x += ((background.getMeasuredWidth() - width) / 2) -((ThemePreviewActivity.BackgroundView) background).tx;
            } else {
                x += -((ThemePreviewActivity.BackgroundView) background).tx;
            }
            y += -((ThemePreviewActivity.BackgroundView) background).ty;
        }
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(background.getMeasuredWidth(), background.getMeasuredHeight(), x, y - viewPos[1]);
        } else {
            applyServiceShaderMatrix(background.getMeasuredWidth(), background.getMeasuredHeight(), x, y - viewPos[1]);
        }
    }

    public static void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
        applyServiceShaderMatrix(serviceBitmap, serviceBitmapShader, serviceBitmapMatrix, w, h, translationX, translationY);
    }

    public static void applyServiceShaderMatrix(Bitmap bitmap, BitmapShader shader, Matrix matrix, int w, int h, float translationX, float translationY) {
        if (shader == null || matrix == null) {
            return;
        }

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        float maxScale = Math.max(w / bitmapWidth, h / bitmapHeight);
        float width = bitmapWidth * maxScale;
        float height = bitmapHeight * maxScale;
        float x = (w - width) / 2;
        float y = (h - height) / 2;

        matrix.reset();
        matrix.setTranslate(x - translationX, y - translationY);
        matrix.preScale(maxScale, maxScale);
        shader.setLocalMatrix(matrix);
    }

    public static void applyChatServiceMessageColor(int[] custom, Drawable wallpaperOverride) {
        applyChatServiceMessageColor(custom, wallpaperOverride, wallpaper);
    }

    public static void applyChatServiceMessageColor(int[] custom, Drawable wallpaperOverride, Drawable currentWallpaper) {
        if (chat_actionBackgroundPaint == null) {
            return;
        }
        int serviceColor;
        int servicePressedColor;
        serviceMessageColor = serviceMessageColorBackup;
        serviceSelectedMessageColor = serviceSelectedMessageColorBackup;
        if (custom != null && custom.length >= 2) {
            serviceColor = custom[0];
            servicePressedColor = custom[1];
            serviceMessageColor = custom[0];
            serviceSelectedMessageColor = custom[1];
        } else {
            int serviceIndex = currentColors.indexOfKey(key_chat_serviceBackground);
            if (serviceIndex >= 0) {
                serviceColor = currentColors.valueAt(serviceIndex);
            } else {
                serviceColor = serviceMessageColor;
            }

            int servicePressedIndex = currentColors.indexOfKey(key_chat_serviceBackgroundSelected);
            if (servicePressedIndex >= 0) {
                servicePressedColor = currentColors.valueAt(servicePressedIndex);
            } else {
                servicePressedColor = serviceSelectedMessageColor;
            }
        }
        Drawable drawable = wallpaperOverride != null ? wallpaperOverride : currentWallpaper;
        boolean drawServiceGradient = (drawable instanceof MotionBackgroundDrawable || drawable instanceof BitmapDrawable) && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
        if (drawServiceGradient) {
            Bitmap newBitmap = null;
            if (drawable instanceof MotionBackgroundDrawable) {
                newBitmap = ((MotionBackgroundDrawable) drawable).getBitmap();
            } else if (drawable instanceof BitmapDrawable) {
                newBitmap = checkBlur(drawable);
            }
            if (serviceBitmap != newBitmap) {
                serviceBitmap = newBitmap;
                serviceBitmapShader = new BitmapShader(serviceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    serviceBitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                }
                if (serviceBitmapMatrix == null) {
                    serviceBitmapMatrix = new Matrix();
                }
            }
            setDrawableColor(chat_msgStickerPinnedDrawable, 0xffffffff);
            setDrawableColor(chat_msgStickerCheckDrawable, 0xffffffff);
            setDrawableColor(chat_msgStickerHalfCheckDrawable, 0xffffffff);
            setDrawableColor(chat_msgStickerViewsDrawable, 0xffffffff);
            setDrawableColor(chat_msgStickerRepliesDrawable, 0xffffffff);
            chat_actionTextPaint.setColor(0xffffffff);
            chat_actionTextPaint2.setColor(0xffffffff);
            chat_actionTextPaint.linkColor = 0xffffffff;
            chat_unlockExtendedMediaTextPaint.setColor(0xffffffff);
            chat_botButtonPaint.setColor(0xffffffff);
            setDrawableColor(chat_commentStickerDrawable, 0xffffffff);
            setDrawableColor(chat_shareIconDrawable, 0xffffffff);
            setDrawableColor(chat_replyIconDrawable, 0xffffffff);
            setDrawableColor(chat_goIconDrawable, 0xffffffff);
            setDrawableColor(chat_botInlineDrawable, 0xffffffff);
            setDrawableColor(chat_botWebViewDrawable, 0xffffffff);
            setDrawableColor(chat_botInviteDrawable, 0xffffffff);
            setDrawableColor(chat_botLinkDrawable, 0xffffffff);
        } else {
            serviceBitmap = null;
            serviceBitmapShader = null;

            setDrawableColorByKey(chat_msgStickerPinnedDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerHalfCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerViewsDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerRepliesDrawable, key_chat_serviceText);
            chat_actionTextPaint.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint2.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint.linkColor = getColor(key_chat_serviceLink);
            chat_unlockExtendedMediaTextPaint.setColor(getColor(key_chat_serviceText));
            setDrawableColorByKey(chat_commentStickerDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_shareIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_replyIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_goIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botInlineDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botWebViewDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botInviteDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botLinkDrawable, key_chat_serviceIcon);
            chat_botButtonPaint.setColor(getColor(key_chat_botButtonText));
        }

        chat_actionBackgroundPaint.setColor(serviceColor);
        chat_actionBackgroundSelectedPaint.setColor(servicePressedColor);
        currentColor = serviceColor;

        if (serviceBitmapShader != null && (currentColors.indexOfKey(key_chat_serviceBackground) < 0 || drawable instanceof MotionBackgroundDrawable || drawable instanceof BitmapDrawable)) {
            ColorMatrix colorMatrix = new ColorMatrix();
            if (drawable instanceof MotionBackgroundDrawable) {
                float intensity = ((MotionBackgroundDrawable) drawable).getIntensity();
                if (intensity >= 0) {
                    colorMatrix.setSaturation(1.6f);
                    AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? .97f : .92f);
                    AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? +.12f : -.06f);
                } else {
                    colorMatrix.setSaturation(1.1f);
                    AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? .4f : .8f);
                    AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? +.08f : -.06f);
                }
            } else {
                colorMatrix.setSaturation(1.6f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? .9f : .84f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? -.04f : +.06f);
            }

            chat_actionBackgroundPaint.setFilterBitmap(true);
            chat_actionBackgroundPaint.setShader(serviceBitmapShader);
            chat_actionBackgroundPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            chat_actionBackgroundPaint.setAlpha(0xff);

            chat_actionBackgroundSelectedPaint.setFilterBitmap(true);
            chat_actionBackgroundSelectedPaint.setShader(serviceBitmapShader);
            colorMatrix = new ColorMatrix(colorMatrix);
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.26f);
            AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, isCurrentThemeDark() ? .92f : .92f);
            chat_actionBackgroundSelectedPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            chat_actionBackgroundSelectedPaint.setAlpha(0xff);

            chat_actionBackgroundGradientDarkenPaint.setAlpha(0);
        } else {
            chat_actionBackgroundPaint.setColorFilter(null);
            chat_actionBackgroundPaint.setShader(null);
            chat_actionBackgroundSelectedPaint.setColorFilter(null);
            chat_actionBackgroundSelectedPaint.setShader(null);

            chat_actionBackgroundGradientDarkenPaint.setAlpha(0x15);
        }
    }

    private static WeakReference<Drawable> lastDrawableToBlur;
    private static Bitmap blurredBitmap;
    private static Bitmap checkBlur(Drawable d) {
        if (lastDrawableToBlur != null && lastDrawableToBlur.get() == d) {
            return blurredBitmap;
        }
        if (lastDrawableToBlur != null) {
            lastDrawableToBlur.clear();
        }
        lastDrawableToBlur = null;
        if (d == null || d.getIntrinsicWidth() == 0 || d.getIntrinsicHeight() == 0) {
            return blurredBitmap = null;
        }
        lastDrawableToBlur = new WeakReference<>(d);

        final int h = 24;
        final int w = (int) ((float) d.getIntrinsicWidth() / d.getIntrinsicHeight() * h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            final ColorFilter wasColorFilter = d.getColorFilter();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(1.3f);
            AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, .94f);
            d.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            d.draw(new Canvas(bitmap));
            d.setColorFilter(wasColorFilter);
        } else {
            d.draw(new Canvas(bitmap));
        }
        Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
        return blurredBitmap = bitmap;
    }

    public static void applyChatMessageSelectedBackgroundColor() {
        applyChatMessageSelectedBackgroundColor(null, wallpaper);
    }

    public static void applyChatMessageSelectedBackgroundColor(Drawable wallpaperOverride) {
        applyChatMessageSelectedBackgroundColor(wallpaperOverride, wallpaper);
    }

    public static void applyChatMessageSelectedBackgroundColor(Drawable wallpaperOverride, Drawable currentWallpaper) {
        if (chat_messageBackgroundSelectedPaint == null) {
            return;
        }

        int selectedBackgroundColor = currentColors.get(key_chat_selectedBackground);

        Drawable drawable = wallpaperOverride != null ? wallpaperOverride : currentWallpaper;
        boolean drawSelectedGradient = drawable instanceof MotionBackgroundDrawable && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW && selectedBackgroundColor == 0;
        if (drawSelectedGradient) {
            Bitmap newBitmap = ((MotionBackgroundDrawable) drawable).getBitmap();
            if (serviceBitmap != newBitmap) {
                serviceBitmap = newBitmap;
                serviceBitmapShader = new BitmapShader(serviceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (serviceBitmapMatrix == null) {
                    serviceBitmapMatrix = new Matrix();
                }
            }
        }

        if (serviceBitmapShader != null && selectedBackgroundColor == 0 && drawSelectedGradient) {
            ColorMatrix colorMatrix2 = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix2, 2.5f);
            AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix2, .75f);
            chat_messageBackgroundSelectedPaint.setShader(serviceBitmapShader);
            chat_messageBackgroundSelectedPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix2));
            chat_messageBackgroundSelectedPaint.setAlpha(64);
        } else {
            chat_messageBackgroundSelectedPaint.setColor(selectedBackgroundColor == 0 ? 0x40000000 : selectedBackgroundColor);
            chat_messageBackgroundSelectedPaint.setColorFilter(null);
            chat_messageBackgroundSelectedPaint.setShader(null);
        }
    }

    public static void createProfileResources(Context context) {
        if (profile_verifiedDrawable == null) {
            profile_aboutTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            Resources resources = context.getResources();

            profile_verifiedDrawable = resources.getDrawable(R.drawable.verified_area).mutate();
            profile_verifiedCheckDrawable = resources.getDrawable(R.drawable.verified_check).mutate();

            applyProfileTheme();
        }

        profile_aboutTextPaint.setTextSize(dp(16));
    }

    private static ColorFilter currentShareColorFilter;
    private static int currentShareColorFilterColor;
    private static ColorFilter currentShareSelectedColorFilter;
    private static  int currentShareSelectedColorFilterColor;
    public static ColorFilter getShareColorFilter(int color, boolean selected) {
        if (selected) {
            if (currentShareSelectedColorFilter == null || currentShareSelectedColorFilterColor != color) {
                currentShareSelectedColorFilterColor = color;
                currentShareSelectedColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }
            return currentShareSelectedColorFilter;
        } else {
            if (currentShareColorFilter == null || currentShareColorFilterColor != color) {
                currentShareColorFilterColor = color;
                currentShareColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }
            return currentShareColorFilter;
        }
    }

    public static void applyProfileTheme() {
        if (profile_verifiedDrawable == null) {
            return;
        }

        profile_aboutTextPaint.setColor(getColor(key_windowBackgroundWhiteBlackText));
        profile_aboutTextPaint.linkColor = getColor(key_windowBackgroundWhiteLinkText);

        setDrawableColorByKey(profile_verifiedDrawable, key_profile_verifiedBackground);
        setDrawableColorByKey(profile_verifiedCheckDrawable, key_profile_verifiedCheck);
    }

    public static Drawable getThemedDrawableByKey(Context context, int resId, int key, Theme.ResourcesProvider resourcesProvider) {
        return getThemedDrawable(context, resId, getColor(key, resourcesProvider));
    }

    public static Drawable getThemedDrawableByKey(Context context, int resId, int key) {
        return getThemedDrawable(context, resId, getColor(key));
    }

    public static Drawable getThemedDrawable(Context context, int resId, int color) {
        if (context == null) {
            return null;
        }
        Drawable drawable = context.getResources().getDrawable(resId).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        return drawable;
    }

    public static int getDefaultColor(int key) {
        int value = defaultColors[key];
        if (value == 0) {
            if (isMyMessagesBubbles(key) || key == key_chats_menuTopShadow || key == key_chats_menuTopBackground || key == key_chats_menuTopShadowCats || key == key_chat_wallpaper_gradient_to2 || key == key_chat_wallpaper_gradient_to3) {
                return 0;
            }
            return 0xffff0000;
        }
        return value;
    }

    public static boolean hasThemeKey(int key) {
        return currentColors.indexOfKey(key) >= 0;
    }

    public static void setAnimatingColor(boolean animating) {
        animatingColors = animating ? new SparseIntArray() : null;
    }

    public static boolean isAnimatingColor() {
        return animatingColors != null;
    }

    public static void setAnimatedColor(int key, int value) {
        if (animatingColors == null) {
            return;
        }
        animatingColors.put(key, value);
    }

    public static int getDefaultAccentColor(int key) {
        int index = currentColorsNoAccent.indexOfKey(key);
        if (index >= 0) {
            int color = currentColorsNoAccent.valueAt(index);
            ThemeAccent accent = currentTheme.getAccent(false);
            if (accent == null) {
                return 0;
            }
            float[] hsvTemp1 = getTempHsv(1);
            float[] hsvTemp2 = getTempHsv(2);
            Color.colorToHSV(currentTheme.accentBaseColor, hsvTemp1);
            Color.colorToHSV(accent.accentColor, hsvTemp2);
            return changeColorAccent(hsvTemp1, hsvTemp2, color, currentTheme.isDark(), color);
        }
        return 0;
    }

    public static int getNonAnimatedColor(int key) {
        return getColor(key, null, true);
    }

    public static int getColor(int key, ResourcesProvider provider) {
        if (provider != null) {
            return provider.getColor(key);
        }
        return getColor(key);
    }

    public static int getColor(int key) {
        return getColor(key, null, false);
    }

    public static int getColor(int key, boolean[] isDefault) {
        return getColor(key, isDefault, false);
    }

    public static int getColor(int key, boolean[] isDefault, boolean ignoreAnimation) {
        if (!ignoreAnimation && animatingColors != null) {
            int index = animatingColors.indexOfKey(key);
            if (index >= 0) {
                return animatingColors.valueAt(index);
            }
        }
        if (serviceBitmapShader != null && (key_chat_serviceText == key || key_chat_serviceLink == key || key_chat_serviceIcon == key
                || key_chat_stickerReplyLine == key || key_chat_stickerReplyNameText == key || key_chat_stickerReplyMessageText == key)) {
            return 0xffffffff;
        }
        if (currentTheme == defaultTheme) {
            boolean useDefault;
            if (isMyMessagesBubbles(key)) {
                useDefault = currentTheme.isDefaultMyMessagesBubbles();
            } else if (isMyMessages(key)) {
                useDefault = currentTheme.isDefaultMyMessages();
            } else if (key_chat_wallpaper == key || key_chat_wallpaper_gradient_to1 == key || key_chat_wallpaper_gradient_to2 == key || key_chat_wallpaper_gradient_to3 == key) {
                useDefault = false;
            } else {
                useDefault = currentTheme.isDefaultMainAccent();
            }
            if (useDefault) {
                if (key == key_chat_serviceBackground) {
                    return serviceMessageColor;
                } else if (key == key_chat_serviceBackgroundSelected) {
                    return serviceSelectedMessageColor;
                }
                return getDefaultColor(key);
            }
        }
        int index = currentColors.indexOfKey(key);
        int color;
        if (index < 0) {
            int fallbackKey = fallbackKeys.get(key, -1);
            if (fallbackKey != -1) {
                int fallbackIndex = currentColors.indexOfKey(fallbackKey);
                if (fallbackIndex >= 0) {
                    return currentColors.valueAt(fallbackIndex);
                }
            }

            if (isDefault != null) {
                isDefault[0] = true;
            }
            if (key == key_chat_serviceBackground) {
                return serviceMessageColor;
            } else if (key == key_chat_serviceBackgroundSelected) {
                return serviceSelectedMessageColor;
            }
            return getDefaultColor(key);
        } else {
            color = currentColors.valueAt(index);
        }
        if (key_windowBackgroundWhite == key || key_windowBackgroundGray == key || key_actionBarDefault == key || key_actionBarDefaultArchived == key) {
            color |= 0xff000000;
        }
        return color;
    }

    private static boolean isMyMessagesBubbles(int key) {
        return key >= myMessagesBubblesStartIndex && key < myMessagesBubblesEndIndex;
    }


    private static boolean isMyMessages(int key) {
        return key >= myMessagesStartIndex && key < myMessagesEndIndex;
    }

    public static void setColor(int key, int color, boolean useDefault) {
        if (key == key_chat_wallpaper || key == key_chat_wallpaper_gradient_to1 || key == key_chat_wallpaper_gradient_to2 || key == key_chat_wallpaper_gradient_to3 || key == key_windowBackgroundWhite || key == key_windowBackgroundGray || key == key_actionBarDefault || key == key_actionBarDefaultArchived) {
            color = 0xff000000 | color;
        }

        if (useDefault) {
            currentColors.delete(key);
        } else {
            currentColors.put(key, color);
        }

        if (key == key_chat_selectedBackground) {
            applyChatMessageSelectedBackgroundColor();
        } else if (key == key_chat_serviceBackground || key == key_chat_serviceBackgroundSelected) {
            applyChatServiceMessageColor();
        } else if (key == key_chat_wallpaper || key == key_chat_wallpaper_gradient_to1 || key == key_chat_wallpaper_gradient_to2
                || key == key_chat_wallpaper_gradient_to3 || key == key_chat_wallpaper_gradient_rotation) {
            reloadWallpaper(true);
        } else if (key == key_actionBarDefault) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
        } else if (key == key_windowBackgroundGray) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }
        }
    }

    public static void setDefaultColor(int key, int color) {
        defaultColors[key] = color;
    }

    public static void setThemeWallpaper(ThemeInfo themeInfo, Bitmap bitmap, File path) {
        currentColors.delete(key_chat_wallpaper);
        currentColors.delete(key_chat_wallpaper_gradient_to1);
        currentColors.delete(key_chat_wallpaper_gradient_to2);
        currentColors.delete(key_chat_wallpaper_gradient_to3);
        currentColors.delete(key_chat_wallpaper_gradient_rotation);
        themedWallpaperLink = null;
        themeInfo.setOverrideWallpaper(null);
        if (bitmap != null) {
            themedWallpaper = new BitmapDrawable(bitmap);
            saveCurrentTheme(themeInfo, false, false, false);
            calcBackgroundColor(themedWallpaper, 0);
            applyChatServiceMessageColor();
            applyChatMessageSelectedBackgroundColor();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        } else {
            themedWallpaper = null;
            wallpaper = null;
            saveCurrentTheme(themeInfo, false, false, false);
            reloadWallpaper(true);
        }
    }

    public static void setDrawableColor(Drawable drawable, int color) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof StatusDrawable) {
            ((StatusDrawable) drawable).setColor(color);
        } else if (drawable instanceof MsgClockDrawable) {
            ((MsgClockDrawable) drawable).setColor(color);
        } else if (drawable instanceof ShapeDrawable) {
            ((ShapeDrawable) drawable).getPaint().setColor(color);
        } else if (drawable instanceof ScamDrawable) {
            ((ScamDrawable) drawable).setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static void setDrawableColorByKey(Drawable drawable, int key) {
        setDrawableColor(drawable, getColor(key));
    }

    public static void setEmojiDrawableColor(Drawable drawable, int color, boolean selected) {
        if (drawable instanceof StateListDrawable) {
            try {
                Drawable state;
                if (selected) {
                    state = getStateDrawable(drawable, 0);
                } else {
                    state = getStateDrawable(drawable, 1);
                }
                if (state instanceof ShapeDrawable) {
                    ((ShapeDrawable) state).getPaint().setColor(color);
                } else {
                    state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
            } catch (Throwable ignore) {

            }
        }
    }

    @TargetApi(21)
    @SuppressLint("DiscouragedPrivateApi")
    public static void setRippleDrawableForceSoftware(RippleDrawable drawable) {
        if (drawable == null) {
            return;
        }
        try {
            Method method = RippleDrawable.class.getDeclaredMethod("setForceSoftware", boolean.class);
            method.invoke(drawable, true);
        } catch (Throwable ignore) {

        }
    }

    public static void setSelectorDrawableColor(Drawable drawable, int color, boolean selected) {
        if (drawable instanceof StateListDrawable) {
            try {
                Drawable state;
                if (selected) {
                    state = getStateDrawable(drawable, 0);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                    state = getStateDrawable(drawable, 1);
                } else {
                    state = getStateDrawable(drawable, 2);
                }
                if (state instanceof ShapeDrawable) {
                    ((ShapeDrawable) state).getPaint().setColor(color);
                } else {
                    state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
            } catch (Throwable ignore) {

            }
        } else if (Build.VERSION.SDK_INT >= 21 && drawable instanceof RippleDrawable) {
            RippleDrawable rippleDrawable = (RippleDrawable) drawable;
            if (selected) {
                rippleDrawable.setColor(new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                ));
            } else {
                if (rippleDrawable.getNumberOfLayers() > 0) {
                    Drawable drawable1 = rippleDrawable.getDrawable(0);
                    if (drawable1 instanceof ShapeDrawable) {
                        ((ShapeDrawable) drawable1).getPaint().setColor(color);
                    } else {
                        drawable1.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                }
            }
        }
    }

    public static boolean isThemeWallpaperPublic() {
        return !TextUtils.isEmpty(themedWallpaperLink);
    }

    public static boolean hasWallpaperFromTheme() {
        if (currentTheme.firstAccentIsDefault && currentTheme.currentAccentId == DEFALT_THEME_ACCENT_ID) {
            return false;
        }
        return currentColors.indexOfKey(key_chat_wallpaper) >= 0 || themedWallpaperFileOffset > 0 || !TextUtils.isEmpty(themedWallpaperLink);
    }

    public static boolean isCustomTheme() {
        return isCustomTheme;
    }

    public static void reloadWallpaper(boolean async) {
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
        if (wallpaper instanceof MotionBackgroundDrawable) {
            previousPhase = ((MotionBackgroundDrawable) wallpaper).getPhase();
        } else {
            previousPhase = 0;
        }
        wallpaper = null;
        themedWallpaper = null;
        loadWallpaper(async);
    }

    private static void calcBackgroundColor(Drawable drawable, int save) {
        if (save != 2) {
            int[] result = AndroidUtilities.calcDrawableColor(drawable);
            serviceMessageColor = serviceMessageColorBackup = result[0];
            serviceSelectedMessageColor = serviceSelectedMessageColorBackup = result[1];
        }
    }

    public static int getServiceMessageColor() {
        int index = currentColors.indexOfKey(key_chat_serviceBackground);
        if (index >= 0) {
            return currentColors.valueAt(index);
        } else {
            return serviceMessageColor;
        }
    }

    public static void loadWallpaper(boolean async) {
        if (wallpaper != null) {
            return;
        }
        boolean defaultTheme = currentTheme.firstAccentIsDefault && currentTheme.currentAccentId == DEFALT_THEME_ACCENT_ID;
        File wallpaperFile;
        boolean wallpaperMotion;
        ThemeAccent accent = currentTheme.getAccent(false);
        TLRPC.Document wallpaperDocument = null;
        if (accent != null) {
            wallpaperFile = accent.getPathToWallpaper();
            wallpaperMotion = accent.patternMotion;
            TLRPC.ThemeSettings settings = null;
            if (accent.info != null && accent.info.settings.size() > 0) {
                settings = accent.info.settings.get(0);
            }
            if (accent.info != null && settings != null && settings.wallpaper != null) {
                wallpaperDocument = settings.wallpaper.document;
            }
        } else {
            wallpaperFile = null;
            wallpaperMotion = false;
        }
        int intensity;
        OverrideWallpaperInfo overrideWallpaper = currentTheme.overrideWallpaper;
        if (overrideWallpaper != null) {
            intensity = (int) (overrideWallpaper.intensity * 100);
        } else {
            intensity = (int) (accent != null ? (accent.patternIntensity * 100) : currentTheme.patternIntensity);
        }

        TLRPC.Document finalWallpaperDocument = wallpaperDocument;
        if (async) {
            Utilities.themeQueue.postRunnable(wallpaperLoadTask = () -> {
                Drawable drawable = loadWallpaperInternal(overrideWallpaper, wallpaperFile, intensity, wallpaperMotion, finalWallpaperDocument, defaultTheme);
                AndroidUtilities.runOnUIThread(() -> {
                    wallpaperLoadTask = null;
                    createCommonChatResources();
                    if (!disallowChangeServiceMessageColor) {
                        applyChatServiceMessageColor(null, null, drawable);
                        applyChatMessageSelectedBackgroundColor(null, drawable);
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
                });
            });
        } else {
            Drawable drawable = loadWallpaperInternal(overrideWallpaper, wallpaperFile, intensity, wallpaperMotion, finalWallpaperDocument, defaultTheme);
            createCommonChatResources();
            if (!disallowChangeServiceMessageColor) {
                applyChatServiceMessageColor(null, null, drawable);
                applyChatMessageSelectedBackgroundColor(null, drawable);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        }
    }

    private static Drawable loadWallpaperInternal(OverrideWallpaperInfo overrideWallpaper, File wallpaperFile, int intensity, boolean wallpaperMotion, TLRPC.Document finalWallpaperDocument, boolean defaultTheme) {
        BackgroundDrawableSettings settings = createBackgroundDrawable(
                currentTheme,
                overrideWallpaper,
                currentColors,
                wallpaperFile,
                themedWallpaperLink,
                themedWallpaperFileOffset,
                intensity,
                previousPhase,
                defaultTheme,
                hasPreviousTheme,
                isApplyingAccent,
                wallpaperMotion,
                finalWallpaperDocument,
                false
        );
        isWallpaperMotion = settings.isWallpaperMotion != null ? settings.isWallpaperMotion : isWallpaperMotion;
        isPatternWallpaper = settings.isPatternWallpaper != null ? settings.isPatternWallpaper : isPatternWallpaper;
        isCustomTheme = settings.isCustomTheme != null ? settings.isCustomTheme : isCustomTheme;
        patternIntensity = intensity;
        wallpaper = settings.wallpaper != null ? settings.wallpaper : wallpaper;
        Drawable drawable = settings.wallpaper;
        calcBackgroundColor(drawable, 1);
        applyChatServiceMessageColor();
        return drawable;
    }


    public static BackgroundDrawableSettings createBackgroundDrawable(
            ThemeInfo currentTheme,
            SparseIntArray currentColors,
            String wallpaperLink,
            int prevoiusPhase,
            boolean local
    ) {
        boolean defaultTheme = currentTheme.firstAccentIsDefault && currentTheme.currentAccentId == DEFALT_THEME_ACCENT_ID;
        ThemeAccent accent = currentTheme.getAccent(false);
        File wallpaperFile = accent != null ? accent.getPathToWallpaper() : null;
        boolean wallpaperMotion = accent != null && accent.patternMotion;
        OverrideWallpaperInfo overrideWallpaper = currentTheme.overrideWallpaper;
        int intensity = overrideWallpaper != null
                ? (int) (overrideWallpaper.intensity * 100)
                : (int) (accent != null ? (accent.patternIntensity * 100) : currentTheme.patternIntensity);

        int wallpaperFileOffset = currentColorsNoAccent.get(key_wallpaperFileOffset, -1);
        return createBackgroundDrawable(currentTheme, overrideWallpaper, currentColors, wallpaperFile, wallpaperLink, wallpaperFileOffset, intensity, prevoiusPhase, defaultTheme, false, false, wallpaperMotion, null, local);
    }

    public static BackgroundDrawableSettings createBackgroundDrawable(
            ThemeInfo currentTheme,
            OverrideWallpaperInfo overrideWallpaper,
            SparseIntArray currentColors,
            File wallpaperFile,
            String themedWallpaperLink,
            int themedWallpaperFileOffset,
            int intensity,
            int previousPhase,
            boolean defaultTheme,
            boolean hasPreviousTheme,
            boolean isApplyingAccent,
            boolean wallpaperMotion,
            TLRPC.Document wallpaperDocument,
            boolean local
    ) {
        BackgroundDrawableSettings settings = new BackgroundDrawableSettings();
        settings.wallpaper = local ? null : wallpaper;
        boolean overrideTheme = (!hasPreviousTheme || isApplyingAccent) && overrideWallpaper != null;
        if (overrideWallpaper != null) {
            settings.isWallpaperMotion = overrideWallpaper.isMotion;
            settings.isPatternWallpaper = overrideWallpaper.color != 0 && !overrideWallpaper.isDefault() && !overrideWallpaper.isColor();
        } else {
            settings.isWallpaperMotion = currentTheme.isMotion;
            settings.isPatternWallpaper = currentTheme.patternBgColor != 0;
        }
        if (!overrideTheme) {
            int backgroundColor = defaultTheme ? 0 : currentColors.get(key_chat_wallpaper);
            int gradientToColor3 = currentColors.get(key_chat_wallpaper_gradient_to3);
            int gradientToColor2 = currentColors.get(key_chat_wallpaper_gradient_to2);
            int gradientToColor1 = currentColors.get(key_chat_wallpaper_gradient_to1);

            boolean bitmapCreated = false;
            if (wallpaperFile != null && wallpaperFile.exists()) {
                bitmapCreated = true;
                try {
                    if (backgroundColor != 0 && gradientToColor1 != 0 && gradientToColor2 != 0) {
                        MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, false);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
                        Bitmap patternBitmap = BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath(), options);
                        if (patternBitmap != null && patternBitmap.getConfig() != Bitmap.Config.ALPHA_8) {
                            Bitmap toRecycle = patternBitmap;
                            patternBitmap = patternBitmap.copy(Bitmap.Config.ALPHA_8, false);
                            toRecycle.recycle();
                        }
                        if (patternBitmap == null) {
                            bitmapCreated = false;
                        }
                        motionBackgroundDrawable.setPatternBitmap(intensity, patternBitmap);
                        motionBackgroundDrawable.setPatternColorFilter(motionBackgroundDrawable.getPatternColor());
                        settings.wallpaper = motionBackgroundDrawable;
                    } else {
                        settings.wallpaper = Drawable.createFromPath(wallpaperFile.getAbsolutePath());
                    }
                    settings.isWallpaperMotion = wallpaperMotion;
                    settings.isPatternWallpaper = true;
                    settings.isCustomTheme = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (bitmapCreated) {

            } else if (backgroundColor != 0) {
                int rotation = currentColors.get(key_chat_wallpaper_gradient_rotation, -1);
                if (rotation == -1) {
                    rotation = 45;
                }
                if (gradientToColor1 != 0 && gradientToColor2 != 0) {
                    MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, false);
                    Bitmap patternBitmap = null;

                    if (wallpaperFile != null) {
                        if (wallpaperDocument != null) {
                            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(wallpaperDocument, true);
                            patternBitmap = SvgHelper.getBitmap(f, dp(360), dp(640), false);
                        } else {
                            patternBitmap = SvgHelper.getBitmap(R.raw.default_pattern, dp(360), dp(640), Color.WHITE);
                        }
                        if (patternBitmap != null) {
                            FileOutputStream stream = null;
                            try {
                                stream = new FileOutputStream(wallpaperFile);
                                Bitmap bitmap = patternBitmap.copy(Bitmap.Config.ARGB_8888, true);
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
                                bitmap.recycle();
                                stream.close();
                            } catch (Exception e) {
                                FileLog.e(e);
                                e.printStackTrace();
                            }
                        }
                    }
                    motionBackgroundDrawable.setPatternBitmap(intensity, patternBitmap);
                    motionBackgroundDrawable.setPhase(previousPhase);
                    settings.wallpaper = motionBackgroundDrawable;
                } else if (gradientToColor1 == 0 || gradientToColor1 == backgroundColor) {
                    settings.wallpaper = new ColorDrawable(backgroundColor);
                } else {
                    final int[] colors = {backgroundColor, gradientToColor1};
                    final BackgroundGradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(rotation);
                    final BackgroundGradientDrawable backgroundGradientDrawable = new BackgroundGradientDrawable(orientation, colors);
                    final BackgroundGradientDrawable.Listener listener = new BackgroundGradientDrawable.ListenerAdapter() {
                        @Override
                        public void onSizeReady(int width, int height) {
                            final boolean isOrientationPortrait = AndroidUtilities.displaySize.x <= AndroidUtilities.displaySize.y;
                            final boolean isGradientPortrait = width <= height;
                            if (isOrientationPortrait == isGradientPortrait) {
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
                            }
                        }
                    };
                    backgroundGradientDisposable = backgroundGradientDrawable.startDithering(BackgroundGradientDrawable.Sizes.ofDeviceScreen(), listener, 100);
                    settings.wallpaper = backgroundGradientDrawable;
                }
                settings.isCustomTheme = true;
            } else if (themedWallpaperLink != null) {
                try {
                    File pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(themedWallpaperLink) + ".wp");
                    Bitmap bitmap = loadScreenSizedBitmap(new FileInputStream(pathToWallpaper), 0);
                    if (bitmap != null) {
                        settings.wallpaper = new BitmapDrawable(bitmap);
                        settings.themedWallpaper = settings.wallpaper;
                        settings.isCustomTheme = true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (themedWallpaperFileOffset > 0 && (currentTheme.pathToFile != null || currentTheme.assetName != null)) {
                try {
                    File file;
                    if (currentTheme.assetName != null) {
                        file = getAssetFile(currentTheme.assetName);
                    } else {
                        file = new File(currentTheme.pathToFile);
                    }
                    Bitmap bitmap = loadScreenSizedBitmap(new FileInputStream(file), themedWallpaperFileOffset);
                    if (bitmap != null) {
                        settings.wallpaper = settings.themedWallpaper = wallpaper = new BitmapDrawable(bitmap);
                        wallpaper.setFilterBitmap(true);
                        settings.isCustomTheme = true;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
        if (settings.wallpaper == null) {
            int selectedColor = overrideWallpaper != null ? overrideWallpaper.color : 0;
            try {
                if (overrideWallpaper == null || overrideWallpaper.isDefault()) {
                    settings.wallpaper = createDefaultWallpaper();
                    settings.isCustomTheme = false;
                } else if (!overrideWallpaper.isColor() || overrideWallpaper.gradientColor1 != 0) {
                    if (selectedColor != 0 && (!isPatternWallpaper || overrideWallpaper.gradientColor2 != 0)) {
                        if (overrideWallpaper.gradientColor1 != 0 && overrideWallpaper.gradientColor2 != 0) {
                            MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(overrideWallpaper.color, overrideWallpaper.gradientColor1, overrideWallpaper.gradientColor2, overrideWallpaper.gradientColor3, false);
                            motionBackgroundDrawable.setPhase(previousPhase);
                            if (settings.isPatternWallpaper) {
                                File toFile = new File(ApplicationLoader.getFilesDirFixed(), overrideWallpaper.fileName);
                                if (toFile.exists()) {
                                    motionBackgroundDrawable.setPatternBitmap((int) (overrideWallpaper.intensity * 100), loadScreenSizedBitmap(new FileInputStream(toFile), 0));
                                    settings.isCustomTheme = true;
                                }
                            }
                            settings.wallpaper = motionBackgroundDrawable;
                        } else if (settings.isPatternWallpaper) {
                            File toFile = new File(ApplicationLoader.getFilesDirFixed(), overrideWallpaper.fileName);
                            if (toFile.exists()) {
                                Bitmap bitmap = loadScreenSizedBitmap(new FileInputStream(toFile), 0);
                                if (bitmap != null) {
                                    settings.wallpaper = new BitmapDrawable(bitmap);
                                    settings.wallpaper.setFilterBitmap(true);
                                    settings.isCustomTheme = true;
                                }
                            }
                        } else if (overrideWallpaper.gradientColor1 != 0) {
                            final int[] colors = {selectedColor, overrideWallpaper.gradientColor1};
                            final BackgroundGradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(overrideWallpaper.rotation);
                            final BackgroundGradientDrawable backgroundGradientDrawable = new BackgroundGradientDrawable(orientation, colors);
                            final BackgroundGradientDrawable.Listener listener = new BackgroundGradientDrawable.ListenerAdapter() {
                                @Override
                                public void onSizeReady(int width, int height) {
                                    final boolean isOrientationPortrait = AndroidUtilities.displaySize.x <= AndroidUtilities.displaySize.y;
                                    final boolean isGradientPortrait = width <= height;
                                    if (isOrientationPortrait == isGradientPortrait) {
                                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
                                    }
                                }
                            };
                            backgroundGradientDisposable = backgroundGradientDrawable.startDithering(BackgroundGradientDrawable.Sizes.ofDeviceScreen(), listener, 100);
                            settings.wallpaper = backgroundGradientDrawable;
                        } else {
                            settings.wallpaper = new ColorDrawable(selectedColor);
                        }
                    } else {
                        File toFile = new File(ApplicationLoader.getFilesDirFixed(), overrideWallpaper.fileName);
                        if (toFile.exists()) {
                            Bitmap bitmap = loadScreenSizedBitmap(new FileInputStream(toFile), 0);
                            if (bitmap != null) {
                                settings.wallpaper = new BitmapDrawable(bitmap);
                                settings.wallpaper.setFilterBitmap(true);
                                settings.isCustomTheme = true;
                            }
                        }
                        if (settings.wallpaper == null) {
                            settings.wallpaper = createDefaultWallpaper();
                            settings.isCustomTheme = false;
                        }
                    }
                }
            } catch (Throwable throwable) {
                //ignore
            }
            if (settings.wallpaper == null) {
                if (selectedColor == 0) {
                    selectedColor = -2693905;
                }
                settings.wallpaper = new ColorDrawable(selectedColor);
            }
        }

        if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND) && settings.wallpaper instanceof MotionBackgroundDrawable) {
            MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) settings.wallpaper;
            int w, h;
            if (motionBackgroundDrawable.getPatternBitmap() == null) {
                w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
                h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            } else {
                w = motionBackgroundDrawable.getPatternBitmap().getWidth();
                h = motionBackgroundDrawable.getPatternBitmap().getHeight();
            }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            settings.wallpaper.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            settings.wallpaper.draw(canvas);
            settings.wallpaper = new BitmapDrawable(bitmap);
        }
        return settings;
    }

    public static Drawable createDefaultWallpaper() {
        return createDefaultWallpaper(0, 0);
    }

    public static Drawable createDefaultWallpaper(int w, int h) {
        MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(0xffdbddbb, 0xff6ba587, 0xffd5d88d, 0xff88b884, w != 0);
        if (w <= 0 || h <= 0) {
            w = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            h = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        }
        motionBackgroundDrawable.setPatternBitmap(34, SvgHelper.getBitmap(R.raw.default_pattern, w, h, Color.BLACK));
        motionBackgroundDrawable.setPatternColorFilter(motionBackgroundDrawable.getPatternColor());
        return motionBackgroundDrawable;
    }

    private static Bitmap loadScreenSizedBitmap(FileInputStream stream, int offset) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            opts.inJustDecodeBounds = true;
            stream.getChannel().position(offset);
            BitmapFactory.decodeStream(stream, null, opts);
            float photoW = opts.outWidth;
            float photoH = opts.outHeight;
            float scaleFactor;
            int w_filter = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int h_filter = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            if (w_filter >= h_filter && photoW > photoH) {
                scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
            } else {
                scaleFactor = Math.min(photoW / w_filter, photoH / h_filter);
            }
            if (scaleFactor < 1.2f) {
                scaleFactor = 1;
            }
            opts.inJustDecodeBounds = false;
            if (scaleFactor > 1.0f && (photoW > w_filter || photoH > h_filter)) {
                int sample = 1;
                do {
                    sample *= 2;
                } while (sample * 2 < scaleFactor);
                opts.inSampleSize = sample;
            } else {
                opts.inSampleSize = (int) scaleFactor;
            }
            stream.getChannel().position(offset);
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, opts);
            if (bitmap.getWidth() < w_filter || bitmap.getHeight() < h_filter) {
                float scale = Math.max((float) w_filter / bitmap.getWidth(), (float) h_filter / bitmap.getHeight());
                if (scale >= 1.02f) {
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), true);
                    bitmap.recycle();
                    return scaledBitmap;
                }
            }
            return bitmap;
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ignore) {

            }
        }
        return null;
    }

    public static Drawable getThemedWallpaper(boolean thumb, View ownerView) {
        int backgroundColor = currentColors.get(key_chat_wallpaper);
        File file = null;
        MotionBackgroundDrawable motionBackgroundDrawable = null;
        int offset = 0;
        if (backgroundColor != 0) {
            int gradientToColor1 = currentColors.get(key_chat_wallpaper_gradient_to1);
            int gradientToColor2 = currentColors.get(key_chat_wallpaper_gradient_to2);
            int gradientToColor3 = currentColors.get(key_chat_wallpaper_gradient_to3);
            int rotation = currentColors.get(key_chat_wallpaper_gradient_rotation, -1);
            if (rotation == -1) {
                rotation = 45;
            }
            if (gradientToColor1 == 0) {
                return new ColorDrawable(backgroundColor);
            } else {
                ThemeAccent accent = currentTheme.getAccent(false);
                if (accent != null && !TextUtils.isEmpty(accent.patternSlug) && previousTheme == null) {
                    File wallpaperFile = accent.getPathToWallpaper();
                    if (wallpaperFile != null && wallpaperFile.exists()) {
                        file = wallpaperFile;
                    }
                }
                if (gradientToColor2 != 0) {
                    motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, true);
                    if (file == null) {
                        return motionBackgroundDrawable;
                    }
                } else if (file == null) {
                    final int[] colors = {backgroundColor, gradientToColor1};
                    final GradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(rotation);
                    final BackgroundGradientDrawable backgroundGradientDrawable = new BackgroundGradientDrawable(orientation, colors);
                    final BackgroundGradientDrawable.Sizes sizes;
                    if (!thumb) {
                        sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen();
                    } else {
                        sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen(BackgroundGradientDrawable.DEFAULT_COMPRESS_RATIO / 4f, BackgroundGradientDrawable.Sizes.Orientation.PORTRAIT);
                    }
                    final BackgroundGradientDrawable.Listener listener;
                    if (ownerView != null) {
                        listener = new BackgroundGradientDrawable.ListenerAdapter() {
                            @Override
                            public void onSizeReady(int width, int height) {
                                if (!thumb) {
                                    final boolean isOrientationPortrait = AndroidUtilities.displaySize.x <= AndroidUtilities.displaySize.y;
                                    final boolean isGradientPortrait = width <= height;
                                    if (isOrientationPortrait == isGradientPortrait) {
                                        ownerView.invalidate();
                                    }
                                } else {
                                    ownerView.invalidate();
                                }
                            }
                        };
                    } else {
                        listener = null;
                    }
                    backgroundGradientDrawable.startDithering(sizes, listener);
                    return backgroundGradientDrawable;
                }
            }
        } else if (themedWallpaperFileOffset > 0 && (currentTheme.pathToFile != null || currentTheme.assetName != null)) {
            if (currentTheme.assetName != null) {
                file = getAssetFile(currentTheme.assetName);
            } else {
                file = new File(currentTheme.pathToFile);
            }
            offset = themedWallpaperFileOffset;
        }
        if (file != null) {
            FileInputStream stream = null;
            try {
                int currentPosition = 0;
                stream = new FileInputStream(file);
                stream.getChannel().position(offset);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                int scaleFactor = 1;
                if (thumb) {
                    opts.inJustDecodeBounds = true;
                    float photoW = opts.outWidth;
                    float photoH = opts.outHeight;
                    int maxWidth = dp(100);
                    while (photoW > maxWidth || photoH > maxWidth) {
                        scaleFactor *= 2;
                        photoW /= 2;
                        photoH /= 2;
                    }
                }
                opts.inPreferredConfig = Bitmap.Config.ALPHA_8;
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = scaleFactor;
                Bitmap bitmap = BitmapFactory.decodeStream(stream, null, opts);
                if (motionBackgroundDrawable != null) {
                    int intensity;
                    ThemeAccent accent = currentTheme.getAccent(false);
                    if (accent != null) {
                        intensity = (int) (accent.patternIntensity * 100);
                    } else {
                        intensity = 100;
                    }
                    if (bitmap != null && bitmap.getConfig() != Bitmap.Config.ALPHA_8) {
                        Bitmap toRecycle = bitmap;
                        bitmap = bitmap.copy(Bitmap.Config.ALPHA_8, false);
                        toRecycle.recycle();
                    }
                    motionBackgroundDrawable.setPatternBitmap(intensity, bitmap);
                    motionBackgroundDrawable.setPatternColorFilter(motionBackgroundDrawable.getPatternColor());
                    return motionBackgroundDrawable;
                }
                if (bitmap != null) {
                    return new BitmapDrawable(bitmap);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return null;
    }

    public static String getSelectedBackgroundSlug() {
        if (currentTheme.overrideWallpaper != null) {
            return currentTheme.overrideWallpaper.slug;
        }
        if (hasWallpaperFromTheme()) {
            return THEME_BACKGROUND_SLUG;
        }
        return DEFAULT_BACKGROUND_SLUG;
    }

    public static Drawable getCachedWallpaper() {
        Drawable drawable = getCachedWallpaperNonBlocking();
        if (drawable == null && wallpaperLoadTask != null) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Utilities.themeQueue.postRunnable(countDownLatch::countDown);
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            drawable = getCachedWallpaperNonBlocking();
        }
        return drawable;
    }

    public static Drawable getCachedWallpaperNonBlocking() {
        if (themedWallpaper != null) {
            return themedWallpaper;
        } else {
            return wallpaper;
        }
    }

    public static boolean isWallpaperMotion() {
        return isWallpaperMotion;
    }

    public static boolean isPatternWallpaper() {
        String selectedBgSlug = getSelectedBackgroundSlug();
        return isPatternWallpaper || "CJz3BZ6YGEYBAAAABboWp6SAv04".equals(selectedBgSlug) || "qeZWES8rGVIEAAAARfWlK1lnfiI".equals(selectedBgSlug);
    }

    public static BackgroundGradientDrawable getCurrentGradientWallpaper() {
        if (currentTheme.overrideWallpaper != null && currentTheme.overrideWallpaper.color != 0 && currentTheme.overrideWallpaper.gradientColor1 != 0) {
            final int[] colors = {currentTheme.overrideWallpaper.color, currentTheme.overrideWallpaper.gradientColor1};
            final GradientDrawable.Orientation orientation = BackgroundGradientDrawable.getGradientOrientation(currentTheme.overrideWallpaper.rotation);
            return new BackgroundGradientDrawable(orientation, colors);
        }
        return null;
    }

    public static AudioVisualizerDrawable getCurrentAudiVisualizerDrawable() {
        if (chat_msgAudioVisualizeDrawable == null) {
            chat_msgAudioVisualizeDrawable = new AudioVisualizerDrawable();
        }
        return chat_msgAudioVisualizeDrawable;
    }

    public static void unrefAudioVisualizeDrawable(MessageObject messageObject) {
        if (chat_msgAudioVisualizeDrawable == null) {
            return;
        }
        if (chat_msgAudioVisualizeDrawable.getParentView() == null || messageObject == null) {
            chat_msgAudioVisualizeDrawable.setParentView(null);
        } else {
            if (animatedOutVisualizerDrawables == null) {
                animatedOutVisualizerDrawables = new HashMap<>();
            }
            animatedOutVisualizerDrawables.put(messageObject, chat_msgAudioVisualizeDrawable);
            chat_msgAudioVisualizeDrawable.setWaveform(false, true, null);
            AndroidUtilities.runOnUIThread(() -> {
                AudioVisualizerDrawable drawable = animatedOutVisualizerDrawables.remove(messageObject);
                if (drawable != null) {
                    drawable.setParentView(null);
                }
            }, 200);
            chat_msgAudioVisualizeDrawable = null;
        }
    }

    public static AudioVisualizerDrawable getAnimatedOutAudioVisualizerDrawable(MessageObject messageObject) {
        if (animatedOutVisualizerDrawables == null || messageObject == null) {
            return null;
        }
        return animatedOutVisualizerDrawables.get(messageObject);
    }

    public static StatusDrawable getChatStatusDrawable(int type) {
        if (type < 0 || type > 5) {
            return null;
        }
        StatusDrawable statusDrawable = chat_status_drawables[type];
        if (statusDrawable != null) {
            return statusDrawable;
        }
        switch (type) {
            case 0:
                chat_status_drawables[0] = new TypingDotsDrawable(true);
                break;
            case 1:
                chat_status_drawables[1] = new RecordStatusDrawable(true);
                break;
            case 2:
                chat_status_drawables[2] = new SendingFileDrawable(true);
                break;
            case 3:
                chat_status_drawables[3] = new PlayingGameDrawable(true, null);
                break;
            case 4:
                chat_status_drawables[4] = new RoundStatusDrawable(true);
                break;
            case 5:
                chat_status_drawables[5] = new ChoosingStickerStatusDrawable(true);
                break;
        }
        statusDrawable = chat_status_drawables[type];
        statusDrawable.start();
        statusDrawable.setColor(getColor(key_chats_actionMessage));
        return statusDrawable;
    }

    public static FragmentContextViewWavesDrawable getFragmentContextViewWavesDrawable() {
        if (fragmentContextViewWavesDrawable == null) {
            fragmentContextViewWavesDrawable = new FragmentContextViewWavesDrawable();
        }
        return fragmentContextViewWavesDrawable;
    }

    public static RoundVideoProgressShadow getRadialSeekbarShadowDrawable() {
        if (roundPlayDrawable == null) {
            roundPlayDrawable = new RoundVideoProgressShadow();
        }
        return roundPlayDrawable;
    }

    public static SparseIntArray getFallbackKeys() {
        return fallbackKeys;
    }

    public static int getFallbackKey(int key) {
        return fallbackKeys.get(key);
    }

    public static Map<String, Drawable> getThemeDrawablesMap() {
        return defaultChatDrawables;
    }

    public static Drawable getThemeDrawable(String drawableKey) {
        return defaultChatDrawables.get(drawableKey);
    }

    public static int getThemeDrawableColorKey(String drawableKey) {
        return defaultChatDrawableColorKeys.get(drawableKey);
    }

    public static Map<String, Paint> getThemePaintsMap() {
        return defaultChatPaints;
    }

    public static Paint getThemePaint(String paintKey) {
        if (Objects.equals(paintKey, Theme.key_paint_divider)) {
            return dividerPaint;
        }
        return defaultChatPaints.get(paintKey);
    }

    public static int getThemePaintColorKey(String paintKey) {
        return defaultChatPaintColors.get(paintKey);
    }

    private static void addChatDrawable(String key, Drawable drawable, int colorKey) {
        defaultChatDrawables.put(key, drawable);
        defaultChatDrawableColorKeys.put(key, colorKey);
    }

    private static void addChatPaint(String key, Paint paint, int colorKey) {
        defaultChatPaints.put(key, paint);
        defaultChatPaintColors.put(key, colorKey);
    }

    public static boolean isCurrentThemeDay() {
        return !getActiveTheme().isDark();
    }

    public static boolean isHome(ThemeAccent accent) {
        if (accent.parentTheme != null) {
            if (accent.parentTheme.getKey().equals("Blue") && accent.id == 99) {
                return true;
            }
            if (accent.parentTheme.getKey().equals("Day") && accent.id == 9) {
                return true;
            }
            if ((accent.parentTheme.getKey().equals("Night") || accent.parentTheme.getKey().equals("Dark Blue")) && accent.id == 0) {
                return true;
            }
        }
        return false;
    }

    public static void turnOffAutoNight(@NonNull BaseFragment fragment) {
        if (selectedAutoNightType != AUTO_NIGHT_TYPE_NONE) {
            if (fragment != null) {
                try {
                    BulletinFactory.of(fragment).createSimpleBulletin(
                        R.raw.auto_night_off,
                        selectedAutoNightType == AUTO_NIGHT_TYPE_SYSTEM ?
                                getString("AutoNightSystemModeOff", R.string.AutoNightSystemModeOff) :
                                getString("AutoNightModeOff", R.string.AutoNightModeOff),
                        getString("Settings", R.string.Settings),
                        Bulletin.DURATION_PROLONG,
                        () -> fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT))
                    ).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            selectedAutoNightType = AUTO_NIGHT_TYPE_NONE;
            saveAutoNightThemeConfig();
            cancelAutoNightThemeCallbacks();
        }
    }

    public static void turnOffAutoNight(FrameLayout container, Runnable openSettings) {
        if (selectedAutoNightType != AUTO_NIGHT_TYPE_NONE) {
            if (container != null && openSettings != null) {
                try {
                    BulletinFactory.of(container, null).createSimpleBulletin(
                        R.raw.auto_night_off,
                        selectedAutoNightType == AUTO_NIGHT_TYPE_SYSTEM ?
                            getString("AutoNightSystemModeOff", R.string.AutoNightSystemModeOff) :
                            getString("AutoNightModeOff", R.string.AutoNightModeOff),
                        getString("Settings", R.string.Settings),
                        Bulletin.DURATION_PROLONG,
                        openSettings
                    ).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            selectedAutoNightType = AUTO_NIGHT_TYPE_NONE;
            saveAutoNightThemeConfig();
            cancelAutoNightThemeCallbacks();
        }
    }

    public interface Colorable {
        public void updateColors();
    }

    public static Paint DEBUG_RED = new Paint(); static { DEBUG_RED.setColor(0xffff0000); }
    public static Paint DEBUG_BLUE = new Paint(); static { DEBUG_BLUE.setColor(0xff0000ff); }
}

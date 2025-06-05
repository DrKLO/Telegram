package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;

import java.util.List;

public class ThemeSmallPreviewView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final static int PATTERN_BITMAP_MAXWIDTH = 120;
    private final static int PATTERN_BITMAP_MAXHEIGHT = 140;

    public final static int TYPE_DEFAULT = 0;
    public final static int TYPE_GRID = 1;
    public final static int TYPE_QR = 2;
    public final static int TYPE_CHANNEL = 3;
    public final static int TYPE_GRID_CHANNEL = 4;

    private final float STROKE_RADIUS = AndroidUtilities.dp(8);
    private final float INNER_RADIUS = AndroidUtilities.dp(6);
    private final float INNER_RECT_SPACE = AndroidUtilities.dp(4);
    private final float BUBBLE_HEIGHT = AndroidUtilities.dp(21);
    private final float BUBBLE_WIDTH = AndroidUtilities.dp(41);

    ThemeDrawable themeDrawable = new ThemeDrawable();
    ThemeDrawable animateOutThemeDrawable;
    private float changeThemeProgress = 1f;

    Paint outlineBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectF = new RectF();
    private final Path clipPath = new Path();
    private final Theme.ResourcesProvider resourcesProvider;

    private ValueAnimator strokeAlphaAnimator;
    private TextPaint noThemeTextPaint;
    private StaticLayout textLayout;
    public ChatThemeBottomSheet.ChatThemeItem chatThemeItem;
    private BackupImageView backupImageView;
    private boolean hasAnimatedEmoji;
    private final int currentAccount;
    Runnable animationCancelRunnable;
    private int currentType;
    int patternColor;
    private float selectionProgress;
    ChatBackgroundDrawable chatBackgroundDrawable;
    boolean attached;

    public ThemeSmallPreviewView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, int currentType) {
        super(context);
        this.currentType = currentType;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        setBackgroundColor(getThemedColor(Theme.key_dialogBackgroundGray));
        backupImageView = new BackupImageView(context);
        backupImageView.getImageReceiver().setCrossfadeWithOldImage(true);
        backupImageView.getImageReceiver().setAllowStartLottieAnimation(false);
        backupImageView.getImageReceiver().setAutoRepeat(0);
        if (currentType == TYPE_DEFAULT || currentType == TYPE_CHANNEL || currentType == TYPE_QR) {
            addView(backupImageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 12));
        } else {
            addView(backupImageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 12));
        }

        outlineBackgroundPaint.setStrokeWidth(AndroidUtilities.dp(2));
        outlineBackgroundPaint.setStyle(Paint.Style.STROKE);
        outlineBackgroundPaint.setColor(0x20E3E3E3);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentType == TYPE_GRID || currentType == TYPE_GRID_CHANNEL) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = (int) (width * 1.2f);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        } else {
            int width = AndroidUtilities.dp(currentType == TYPE_DEFAULT ? 77 : 83);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (height == 0) {
                height = (int) (width * 1.35f);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        backupImageView.setPivotY(backupImageView.getMeasuredHeight());
        backupImageView.setPivotX(backupImageView.getMeasuredWidth() / 2f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw && h == oldh) {
            return;
        }
        rectF.set(INNER_RECT_SPACE, INNER_RECT_SPACE, w - INNER_RECT_SPACE, h - INNER_RECT_SPACE);
        clipPath.reset();
        clipPath.addRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, Path.Direction.CW);
    }

    Theme.MessageDrawable messageDrawableOut = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false);
    Theme.MessageDrawable messageDrawableIn = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (chatThemeItem == null) {
            super.dispatchDraw(canvas);
            return;
        }
        if (chatBackgroundDrawable != null) {
            canvas.save();
            canvas.clipPath(clipPath);
            chatBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            chatBackgroundDrawable.draw(canvas);
            canvas.restore();
        }
        if (changeThemeProgress != 1 && animateOutThemeDrawable != null) {
            animateOutThemeDrawable.drawBackground(canvas, 1f);
        }
        if (changeThemeProgress != 0) {
            themeDrawable.drawBackground(canvas, changeThemeProgress);
        }
        if (changeThemeProgress != 1 && animateOutThemeDrawable != null) {
            animateOutThemeDrawable.draw(canvas, 1f);
        }
        if (changeThemeProgress != 0) {
            themeDrawable.draw(canvas, changeThemeProgress);
        }
        if (changeThemeProgress != 1f) {
            changeThemeProgress += 16 / 150f;
            if (changeThemeProgress >= 1f) {
                changeThemeProgress = 1f;
            }
            invalidate();
        }
        super.dispatchDraw(canvas);
    }

    public TLRPC.WallPaper fallbackWallpaper;
    public void setFallbackWallpaper(TLRPC.WallPaper wallPaper) {
        if (fallbackWallpaper != wallPaper) {
            this.fallbackWallpaper = wallPaper;
            if (chatThemeItem != null && (chatThemeItem.chatTheme == null || chatThemeItem.chatTheme.wallpaper == null)) {
                ChatThemeBottomSheet.ChatThemeItem item = chatThemeItem;
                chatThemeItem = null;
                setItem(item, false);
            }
        }
    }

    public int lastThemeIndex;
    public void setItem(ChatThemeBottomSheet.ChatThemeItem item, boolean animated) {
        boolean itemChanged = chatThemeItem != item;
        boolean darkModeChanged = lastThemeIndex != item.themeIndex;
        lastThemeIndex = item.themeIndex;
        this.chatThemeItem = item;
        hasAnimatedEmoji = false;
        TLRPC.Document document = null;
        if (item.chatTheme.getEmoticon() != null) {
            document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(item.chatTheme.getEmoticon());
        }
        if (itemChanged) {
            if (animationCancelRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
                animationCancelRunnable = null;
            }
            backupImageView.animate().cancel();
            backupImageView.setScaleX(1f);
            backupImageView.setScaleY(1f);
        }
        if (itemChanged) {
            Drawable thumb = null;
            if (document != null) {
                thumb = DocumentObject.getSvgThumb(document, Theme.key_emptyListPlaceholder, 0.2f);
            }
            if (thumb == null) {
                Emoji.preloadEmoji(item.chatTheme.getEmoticon());
                thumb = Emoji.getEmojiDrawable(item.chatTheme.getEmoticon());
            }
            backupImageView.setImage(ImageLocation.getForDocument(document), "50_50", thumb, null);
            TLRPC.WallPaper wallPaper = item.chatTheme.wallpaper;
            if (wallPaper == null) {
                wallPaper = fallbackWallpaper;
            }
            if (wallPaper != null) {
                if (attached && chatBackgroundDrawable != null) {
                    chatBackgroundDrawable.onDetachedFromWindow(ThemeSmallPreviewView.this);
                }
                chatBackgroundDrawable = new ChatBackgroundDrawable(wallPaper, false, true);
                chatBackgroundDrawable.setParent(this);
                if (attached) {
                    chatBackgroundDrawable.onAttachedToWindow(ThemeSmallPreviewView.this);
                }
            } else {
                if (attached && chatBackgroundDrawable != null) {
                    chatBackgroundDrawable.onDetachedFromWindow(ThemeSmallPreviewView.this);
                }
                chatBackgroundDrawable = null;
            }
        }
        backupImageView.setVisibility(item.chatTheme.isAnyStub() && fallbackWallpaper != null ? View.GONE : View.VISIBLE);

        if (itemChanged || darkModeChanged) {
            if (animated) {
                changeThemeProgress = 0f;
                animateOutThemeDrawable = themeDrawable;
                themeDrawable = new ThemeDrawable();
                invalidate();
            } else {
                changeThemeProgress = 1f;
            }
            updatePreviewBackground(themeDrawable);
            TLRPC.TL_theme theme = item.chatTheme.getTlTheme(lastThemeIndex);
            if (theme != null) {
                final long themeId = theme.id;
                TLRPC.WallPaper wallPaper = item.chatTheme.getWallpaper(lastThemeIndex);
                if (wallPaper != null) {
                    final int intensity = wallPaper.settings.intensity;
                    item.chatTheme.loadWallpaperThumb(lastThemeIndex, result -> {
                        if (result != null && result.first == themeId) {
                            if (item.previewDrawable instanceof MotionBackgroundDrawable) {
                                MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) item.previewDrawable;
                                motionBackgroundDrawable.setPatternBitmap(intensity >= 0 ? 100 : -100, prescaleBitmap(result.second), true);
                                motionBackgroundDrawable.setPatternColorFilter(patternColor);
                            }
                            invalidate();
                        }
                    });
                }
            } else {
                Theme.ThemeInfo themeInfo = item.chatTheme.getThemeInfo(lastThemeIndex);
                Theme.ThemeAccent accent = null;

                if (themeInfo.themeAccentsMap != null) {
                    accent = themeInfo.themeAccentsMap.get(item.chatTheme.getAccentId(lastThemeIndex));
                }

                if (accent != null && accent.info != null && accent.info.settings.size() > 0) {
                    TLRPC.WallPaper wallPaper = accent.info.settings.get(0).wallpaper;

                    if (wallPaper != null && wallPaper.document != null) {
                        TLRPC.Document wallpaperDocument = wallPaper.document;
                        final TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(wallpaperDocument.thumbs, PATTERN_BITMAP_MAXWIDTH);
                        ImageLocation imageLocation = ImageLocation.getForDocument(thumbSize, wallpaperDocument);
                        ImageReceiver imageReceiver = new ImageReceiver();
                        imageReceiver.setAllowLoadingOnAttachedOnly(false);
                        imageReceiver.setImage(imageLocation, PATTERN_BITMAP_MAXWIDTH + "_" + PATTERN_BITMAP_MAXHEIGHT, null, null, null, 1);
                        imageReceiver.setDelegate((receiver, set, thumb, memCache) -> {
                            ImageReceiver.BitmapHolder holder = receiver.getBitmapSafe();
                            if (!set || holder == null) {
                                return;
                            }
                            Bitmap resultBitmap = holder.bitmap;
                            if (resultBitmap != null) {
                                if (item.previewDrawable instanceof MotionBackgroundDrawable) {
                                    MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) item.previewDrawable;
                                    motionBackgroundDrawable.setPatternBitmap(wallPaper.settings == null || wallPaper.settings.intensity >= 0 ? 100 : -100, prescaleBitmap(resultBitmap), true);
                                    motionBackgroundDrawable.setPatternColorFilter(patternColor);
                                    invalidate();
                                }
                            }
                        });
                        ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);
                    }
                } else if (accent != null && accent.info == null) {
                    int intensity = (int) (accent.patternIntensity * 100);
                    if (item.previewDrawable instanceof MotionBackgroundDrawable) {
                        ((MotionBackgroundDrawable) item.previewDrawable).setPatternBitmap(intensity);
                    }
                    ChatThemeController.chatThemeQueue.postRunnable(() -> {
                        Bitmap bitmap = SvgHelper.getBitmap(R.raw.default_pattern, AndroidUtilities.dp(PATTERN_BITMAP_MAXWIDTH), AndroidUtilities.dp(PATTERN_BITMAP_MAXHEIGHT), Color.BLACK, AndroidUtilities.density);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (item.previewDrawable instanceof MotionBackgroundDrawable) {
                                MotionBackgroundDrawable motionBackgroundDrawable = (MotionBackgroundDrawable) item.previewDrawable;
                                motionBackgroundDrawable.setPatternBitmap(intensity, prescaleBitmap(bitmap), true);
                                motionBackgroundDrawable.setPatternColorFilter(patternColor);
                                invalidate();
                            }
                        });
                    });
                }
            }
        }

        if (!animated) {
            backupImageView.animate().cancel();;
            backupImageView.setScaleX(1f);
            backupImageView.setScaleY(1f);
            AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
            if (backupImageView.getImageReceiver().getLottieAnimation() != null) {
                backupImageView.getImageReceiver().getLottieAnimation().stop();
                backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, false);
            }
        }

        if (chatThemeItem.chatTheme == null || chatThemeItem.chatTheme.isAnyStub()) {
            setContentDescription(LocaleController.getString(R.string.ChatNoTheme));
        } else {
            setContentDescription(chatThemeItem.chatTheme.getEmoticon());
        }
    }

    boolean isSelected;

    public void setSelected(boolean selected, boolean animated) {
        if (!animated) {
            if (strokeAlphaAnimator != null) {
                strokeAlphaAnimator.cancel();
            }
            isSelected = selected;
            selectionProgress = selected ? 1f : 0;
            invalidate();
            return;
        }
        if (isSelected != selected) {
            float currentProgress = selectionProgress;
            if (strokeAlphaAnimator != null) {
                strokeAlphaAnimator.cancel();
            }
            strokeAlphaAnimator = ValueAnimator.ofFloat(currentProgress, selected ? 1f : 0);
            strokeAlphaAnimator.addUpdateListener(valueAnimator -> {
                selectionProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            strokeAlphaAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    selectionProgress = selected ? 1f : 0;
                    invalidate();
                }
            });
            strokeAlphaAnimator.setDuration(250);
            strokeAlphaAnimator.start();
        }
        isSelected = selected;
    }

    private Bitmap prescaleBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        float scale = Math.max(AndroidUtilities.dp(PATTERN_BITMAP_MAXWIDTH) / bitmap.getWidth(), AndroidUtilities.dp(PATTERN_BITMAP_MAXHEIGHT) / bitmap.getHeight());
        if (bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0 || Math.abs(scale - 1f) < .0125f) {
            return bitmap;
        }
        int w = (int) (bitmap.getWidth() * scale);
        int h = (int) (bitmap.getHeight() * scale);
        if (h <= 0 || w <= 0) {
            return bitmap;
        }
        return Bitmap.createScaledBitmap(bitmap, w, h, true);
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundFillPaint.setColor(getThemedColor(Theme.key_dialogBackgroundGray));
        if (noThemeTextPaint != null) {
            noThemeTextPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
        }
        invalidate();
    }

    private void fillOutBubblePaint(Paint paint, List<Integer> messageColors) {
        if (messageColors.size() > 1) {
            int[] colors = new int[messageColors.size()];
            for (int i = 0; i != messageColors.size(); ++i) {
                colors[i] = messageColors.get(i);
            }
            float top = INNER_RECT_SPACE + AndroidUtilities.dp(8);
            paint.setShader(new LinearGradient(0f, top, 0f, top + BUBBLE_HEIGHT, colors, null, Shader.TileMode.CLAMP));
        } else {
            paint.setShader(null);
        }
    }

    public void updatePreviewBackground(ThemeDrawable themeDrawable) {
        if (chatThemeItem == null || chatThemeItem.chatTheme == null) {
            return;
        }
        EmojiThemes.ThemeItem themeItem = chatThemeItem.chatTheme.getThemeItem(chatThemeItem.themeIndex);
        int color = themeItem.inBubbleColor;
        themeDrawable.inBubblePaint.setColor(color);
        color = themeItem.outBubbleColor;
        themeDrawable.outBubblePaintSecond.setColor(color);

        int strokeColor = chatThemeItem.chatTheme.isAnyStub()
                ? getThemedColor(Theme.key_featuredStickers_addButton)
                : themeItem.outLineColor;
        int strokeAlpha = themeDrawable.strokePaint.getAlpha();
        themeDrawable.strokePaint.setColor(strokeColor);
        themeDrawable.strokePaint.setAlpha(strokeAlpha);


        TLRPC.TL_theme tlTheme = chatThemeItem.chatTheme.getTlTheme(chatThemeItem.themeIndex);

        if (tlTheme != null) {
            int index = chatThemeItem.chatTheme.getSettingsIndex(chatThemeItem.themeIndex);
            TLRPC.ThemeSettings themeSettings = tlTheme.settings.get(index);
            fillOutBubblePaint(themeDrawable.outBubblePaintSecond, themeSettings.message_colors);

            themeDrawable.outBubblePaintSecond.setAlpha(255);
            getPreviewDrawable(tlTheme, index);
        } else {
            EmojiThemes.ThemeItem item = chatThemeItem.chatTheme.getThemeItem(chatThemeItem.themeIndex);
            getPreviewDrawable(item);
        }
        themeDrawable.previewDrawable = chatThemeItem.previewDrawable;
        invalidate();
    }

    private Drawable getPreviewDrawable(TLRPC.TL_theme theme, int settingsIndex) {
        if (chatThemeItem == null) {
            return null;
        }

        int color1 = 0;
        int color2 = 0;
        int color3 = 0;
        int color4 = 0;

        Drawable drawable;
        if (settingsIndex >= 0) {
            TLRPC.ThemeSettings themeSettings = theme.settings.get(settingsIndex);
            TLRPC.WallPaperSettings wallPaperSettings = themeSettings.wallpaper.settings;
            color1 = wallPaperSettings.background_color;
            color2 = wallPaperSettings.second_background_color;
            color3 = wallPaperSettings.third_background_color;
            color4 = wallPaperSettings.fourth_background_color;
        }
        if (color2 != 0) {
            MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(color1, color2, color3, color4, true);
            patternColor = motionBackgroundDrawable.getPatternColor();
            drawable = motionBackgroundDrawable;
        } else {
            drawable = new MotionBackgroundDrawable(color1, color1, color1, color1, true);
            patternColor = Color.BLACK;
        }
        chatThemeItem.previewDrawable = drawable;

        return drawable;
    }

    private Drawable getPreviewDrawable(EmojiThemes.ThemeItem item) {
        if (chatThemeItem == null) {
            return null;
        }
        Drawable drawable = null;

        int color1 = item.patternBgColor;
        int color2 = item.patternBgGradientColor1;
        int color3 = item.patternBgGradientColor2;
        int color4 = item.patternBgGradientColor3;
        int rotation = item.patternBgRotation;

        if (item.themeInfo.getAccent(false) != null) {
            if (color2 != 0) {
                MotionBackgroundDrawable motionBackgroundDrawable = new MotionBackgroundDrawable(color1, color2, color3, color4, rotation, true);
                patternColor = motionBackgroundDrawable.getPatternColor();
                drawable = motionBackgroundDrawable;
            } else {
                drawable = new MotionBackgroundDrawable(color1, color1, color1, color1, rotation, true);
                patternColor = Color.BLACK;
            }
        } else {
            if (color1 != 0 && color2 != 0) {
                drawable = new MotionBackgroundDrawable(color1, color2, color3, color4, rotation, true);
            } else if (color1 != 0) {
                drawable = new ColorDrawable(color1);
            } else if (item.themeInfo != null && (item.themeInfo.previewWallpaperOffset > 0 || item.themeInfo.pathToWallpaper != null)) {
                Bitmap wallpaper = AndroidUtilities.getScaledBitmap(AndroidUtilities.dp(112), AndroidUtilities.dp(134), item.themeInfo.pathToWallpaper, item.themeInfo.pathToFile, item.themeInfo.previewWallpaperOffset);
                if (wallpaper != null) {
                    BitmapDrawable bitmapDrawable = new BitmapDrawable(wallpaper);
                    bitmapDrawable.setFilterBitmap(true);
                    drawable = bitmapDrawable;
                }
            } else if (!(chatThemeItem.chatTheme != null && chatThemeItem.chatTheme.isAnyStub())) {
                drawable = new MotionBackgroundDrawable(0xffdbddbb, 0xff6ba587, 0xffd5d88d, 0xff88b884, true);
            }
        }

        chatThemeItem.previewDrawable = drawable;

        return drawable;
    }

    private StaticLayout getNoThemeStaticLayout() {
        if (textLayout != null) {
            return textLayout;
        }
        noThemeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG + TextPaint.SUBPIXEL_TEXT_FLAG);
        noThemeTextPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
        noThemeTextPaint.setTextSize(AndroidUtilities.dp(noThemeStringTextSize()));
        noThemeTextPaint.setTypeface(AndroidUtilities.bold());
        int width = AndroidUtilities.dp(52);
        if (currentType == TYPE_CHANNEL || currentType == TYPE_GRID_CHANNEL) {
            width = AndroidUtilities.dp(77);
        }
        textLayout = StaticLayoutEx.createStaticLayout2(
                noThemeString(),
                noThemeTextPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                1f, 0f, true,
                TextUtils.TruncateAt.END,
                width,
                3
        );
        return textLayout;
    }

    protected int noThemeStringTextSize() {
        return 14;
    }

    protected String noThemeString() {
        return LocaleController.getString(R.string.ChatNoTheme);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }


    public void playEmojiAnimation() {
        if (backupImageView.getImageReceiver().getLottieAnimation() != null) {
            AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
            backupImageView.setVisibility(View.VISIBLE);
            if (!backupImageView.getImageReceiver().getLottieAnimation().isRunning) {
                backupImageView.getImageReceiver().getLottieAnimation().setCurrentFrame(0, true);
                backupImageView.getImageReceiver().getLottieAnimation().start();
            }
            backupImageView.animate().scaleX(2f).scaleY(2f).setDuration(300).setInterpolator(AndroidUtilities.overshootInterpolator).start();

            AndroidUtilities.runOnUIThread(animationCancelRunnable = () -> {
                animationCancelRunnable = null;
                backupImageView.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }, 2500);
        }
    }

    public void cancelAnimation() {
        if (animationCancelRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(animationCancelRunnable);
            animationCancelRunnable.run();
        }
    }

    private class ThemeDrawable {

        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outBubblePaintSecond = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Drawable previewDrawable;

        ThemeDrawable() {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dp(2));
        }

        public void drawBackground(Canvas canvas, float alpha) {
            if (previewDrawable != null) {
                canvas.save();
                canvas.clipPath(clipPath);
                if (previewDrawable instanceof BitmapDrawable) {
                    int drawableW = previewDrawable.getIntrinsicWidth();
                    int drawableH = previewDrawable.getIntrinsicHeight();
                    if (drawableW / (float) drawableH >  getWidth() / (float) getHeight()) {
                        int w = (int) (getWidth() * (float) drawableH / drawableW);
                        int padding = (w - getWidth()) / 2;
                        previewDrawable.setBounds(padding, 0, padding + w , getHeight());
                    } else {
                        int h = (int) (getHeight() * (float) drawableH / drawableW);
                        int padding = (getHeight() - h) / 2;
                        previewDrawable.setBounds(0, padding, getWidth(), padding + h);
                    }
                } else {
                    previewDrawable.setBounds(0, 0, getWidth(), getHeight());
                }
                previewDrawable.setAlpha((int) (255 * alpha));
                previewDrawable.draw(canvas);
                if (previewDrawable instanceof ColorDrawable || (previewDrawable instanceof MotionBackgroundDrawable && ((MotionBackgroundDrawable) previewDrawable).isOneColor())) {
                    int wasAlpha = outlineBackgroundPaint.getAlpha();
                    outlineBackgroundPaint.setAlpha((int) (wasAlpha * alpha));
                    float padding = INNER_RECT_SPACE;
                    AndroidUtilities.rectTmp.set(padding, padding, getWidth() - padding, getHeight() - padding);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, INNER_RADIUS, INNER_RADIUS, outlineBackgroundPaint);
                    outlineBackgroundPaint.setAlpha(wasAlpha);
                }
                canvas.restore();
            } else if (!(chatThemeItem != null && chatThemeItem.chatTheme != null && chatThemeItem.chatTheme.isAnyStub() && chatBackgroundDrawable != null)) {
                canvas.drawRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, backgroundFillPaint);
            }
        }

        public void draw(Canvas canvas, float alpha) {
            if (isSelected || strokeAlphaAnimator != null) {
                EmojiThemes.ThemeItem themeItem = chatThemeItem.chatTheme.getThemeItem(chatThemeItem.themeIndex);
                int strokeColor = chatThemeItem.chatTheme.isAnyStub()
                        ? getThemedColor(Theme.key_featuredStickers_addButton)
                        : themeItem.outLineColor;
                strokePaint.setColor(strokeColor);
                strokePaint.setAlpha((int) (selectionProgress * alpha * 255));
                float rectSpace = strokePaint.getStrokeWidth() * 0.5f + AndroidUtilities.dp(4) * (1f - selectionProgress);
                rectF.set(rectSpace, rectSpace, getWidth() - rectSpace, getHeight() - rectSpace);
                canvas.drawRoundRect(rectF, STROKE_RADIUS, STROKE_RADIUS, strokePaint);
            }
            outBubblePaintSecond.setAlpha((int) (255 * alpha));
            inBubblePaint.setAlpha((int) (255 * alpha));
            rectF.set(INNER_RECT_SPACE, INNER_RECT_SPACE, getWidth() - INNER_RECT_SPACE, getHeight() - INNER_RECT_SPACE);

            if (chatThemeItem.chatTheme == null || (chatThemeItem.chatTheme.isAnyStub() && chatThemeItem.chatTheme.wallpaper == null)) {
                if (fallbackWallpaper == null) {
                    canvas.drawRoundRect(rectF, INNER_RADIUS, INNER_RADIUS, backgroundFillPaint);
                    canvas.save();
                    StaticLayout textLayout = getNoThemeStaticLayout();
                    canvas.translate((getWidth() - textLayout.getWidth()) * 0.5f, AndroidUtilities.dp(18));
                    textLayout.draw(canvas);
                    canvas.restore();
                }
            } else if (currentType != TYPE_GRID_CHANNEL) {
                if (currentType == TYPE_QR) {
                    if (chatThemeItem.icon != null) {
                        float left = (getWidth() - chatThemeItem.icon.getWidth()) * 0.5f;
                        canvas.drawBitmap(chatThemeItem.icon, left, AndroidUtilities.dp(21), null);
                    }
                } else {
                    float bubbleTop = INNER_RECT_SPACE + AndroidUtilities.dp(8);
                    float bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(currentType == TYPE_CHANNEL ? 5 : 22);
                    if (currentType == TYPE_DEFAULT || currentType == TYPE_CHANNEL) {
                        rectF.set(bubbleLeft, bubbleTop, bubbleLeft + BUBBLE_WIDTH * (currentType == TYPE_CHANNEL ? 1.2f : 1f), bubbleTop + BUBBLE_HEIGHT);
                    } else {
                        bubbleTop = getMeasuredHeight() * 0.12f;
                        bubbleLeft = getMeasuredWidth() - getMeasuredWidth() * 0.65f;
                        float bubbleRight = getMeasuredWidth() - getMeasuredWidth() * 0.1f;
                        float bubbleBottom = getMeasuredHeight() * 0.32f;
                        rectF.set(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom);
                    }

                    Paint paint = currentType == TYPE_CHANNEL ? inBubblePaint : outBubblePaintSecond;
                    if (currentType == TYPE_DEFAULT || currentType == TYPE_CHANNEL) {
                        canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, paint);
                    } else {
                        messageDrawableOut.setBounds((int) rectF.left, (int) rectF.top - AndroidUtilities.dp(2), (int) rectF.right + AndroidUtilities.dp(4), (int) rectF.bottom + AndroidUtilities.dp(2));
                        messageDrawableOut.setRoundRadius((int) (rectF.height() * 0.5f));
                        messageDrawableOut.draw(canvas, paint);
                    }

                    if (currentType == TYPE_DEFAULT || currentType == TYPE_CHANNEL) {
                        bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(5);
                        bubbleTop += BUBBLE_HEIGHT + AndroidUtilities.dp(4);
                        rectF.set(bubbleLeft, bubbleTop, bubbleLeft + BUBBLE_WIDTH * (currentType == TYPE_CHANNEL ? 0.8f : 1f), bubbleTop + BUBBLE_HEIGHT);
                    } else {
                        bubbleTop = getMeasuredHeight() * 0.35f;
                        bubbleLeft = getMeasuredWidth() * 0.1f;
                        float bubbleRight = getMeasuredWidth() * 0.65f;
                        float bubbleBottom = getMeasuredHeight() * 0.55f;
                        rectF.set(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom);
                    }

                    if (currentType == TYPE_DEFAULT || currentType == TYPE_CHANNEL) {
                        canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, inBubblePaint);
                    } else {
                        messageDrawableIn.setBounds((int) rectF.left - AndroidUtilities.dp(4), (int) rectF.top - AndroidUtilities.dp(2), (int) rectF.right, (int) rectF.bottom + AndroidUtilities.dp(2));
                        messageDrawableIn.setRoundRadius((int) (rectF.height() * 0.5f));
                        messageDrawableIn.draw(canvas, inBubblePaint);
                    }
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        attached = true;
        if (chatBackgroundDrawable != null) {
            chatBackgroundDrawable.onAttachedToWindow(ThemeSmallPreviewView.this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        attached = false;
        if (chatBackgroundDrawable != null) {
            chatBackgroundDrawable.onDetachedFromWindow(ThemeSmallPreviewView.this);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            invalidate();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(true);
        info.setSelected(isSelected);
    }
}

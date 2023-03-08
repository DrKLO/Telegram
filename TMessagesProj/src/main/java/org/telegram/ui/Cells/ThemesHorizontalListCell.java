package org.telegram.ui.Cells;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ThemeActivity;
import org.telegram.ui.ThemeSetUrlActivity;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class ThemesHorizontalListCell extends RecyclerListView implements NotificationCenter.NotificationCenterDelegate {

    public static byte[] bytes = new byte[1024];

    private boolean drawDivider;
    private LinearLayoutManager horizontalLayoutManager;
    private HashMap<String, Theme.ThemeInfo> loadingThemes = new HashMap<>();
    private HashMap<Theme.ThemeInfo, String> loadingWallpapers = new HashMap<>();
    private Theme.ThemeInfo prevThemeInfo;
    private ThemesListAdapter adapter;

    private ArrayList<Theme.ThemeInfo> customThemes;
    private ArrayList<Theme.ThemeInfo> defaultThemes;
    private int currentType;
    private int prevCount;
    private BaseFragment fragment;

    private class ThemesListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        ThemesListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new InnerThemeView(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            InnerThemeView view = (InnerThemeView) holder.itemView;
            ArrayList<Theme.ThemeInfo> arrayList;
            int p = position;
            if (position < defaultThemes.size()) {
                arrayList = defaultThemes;
            } else {
                arrayList = customThemes;
                p -= defaultThemes.size();
            }
            view.setTheme(arrayList.get(p), position == getItemCount() - 1, position == 0);
        }

        @Override
        public int getItemCount() {
            return prevCount = defaultThemes.size() + customThemes.size();
        }
    }

    private class InnerThemeView extends FrameLayout {

        private RadioButton button;
        private Theme.ThemeInfo themeInfo;
        private RectF rect = new RectF();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable optionsDrawable;

        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Drawable inDrawable;
        private Drawable outDrawable;
        private boolean isLast;
        private boolean isFirst;

        private float placeholderAlpha;

        private int inColor;
        private int outColor;
        private int backColor;
        private int checkColor;
        private int accentId;

        private int oldInColor;
        private int oldOutColor;
        private int oldBackColor;
        private int oldCheckColor;

        private boolean accentColorChanged;

        private ObjectAnimator accentAnimator;
        private float accentState;
        private final ArgbEvaluator evaluator = new ArgbEvaluator();

        private Drawable backgroundDrawable;
        private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private BitmapShader bitmapShader;
        private boolean hasWhiteBackground;
        private Matrix shaderMatrix = new Matrix();

        private Drawable loadingDrawable;
        private int loadingColor;

        private long lastDrawTime;

        private boolean pressed;

        public InnerThemeView(Context context) {
            super(context);
            setWillNotDraw(false);

            inDrawable = context.getResources().getDrawable(R.drawable.minibubble_in).mutate();
            outDrawable = context.getResources().getDrawable(R.drawable.minibubble_out).mutate();

            textPaint.setTextSize(AndroidUtilities.dp(13));

            button = new RadioButton(context);
            button.setSize(AndroidUtilities.dp(20));
            addView(button, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.TOP, 27, 75, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(76 + (isLast ? 22 : 15) + (isFirst ? 22 : 0)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(148), MeasureSpec.EXACTLY));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (optionsDrawable == null || themeInfo == null || themeInfo.info != null && !themeInfo.themeLoaded || currentType != ThemeActivity.THEME_TYPE_BASIC) {
                return super.onTouchEvent(event);
            }
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float y = event.getY();
                if (x > rect.centerX() && y < rect.centerY() - AndroidUtilities.dp(10)) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        pressed = true;
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        showOptionsForTheme(themeInfo);
                    }
                }
                if (action == MotionEvent.ACTION_UP) {
                    pressed = false;
                }
            }
            return pressed;
        }

        private boolean parseTheme() {
            if (themeInfo == null || themeInfo.pathToFile == null) {
                return false;
            }
            boolean finished = false;
            File file = new File(themeInfo.pathToFile);
            try (FileInputStream stream = new FileInputStream(file)) {
                int currentPosition = 0;
                int idx;
                int read;
                int linesRead = 0;
                while ((read = stream.read(bytes)) != -1) {
                    int previousPosition = currentPosition;
                    int start = 0;
                    for (int a = 0; a < read; a++) {
                        if (bytes[a] == '\n') {
                            linesRead++;
                            int len = a - start + 1;
                            String line = new String(bytes, start, len - 1, "UTF-8");
                            if (line.startsWith("WLS=")) {
                                String wallpaperLink = line.substring(4);
                                Uri uri = Uri.parse(wallpaperLink);
                                themeInfo.slug = uri.getQueryParameter("slug");
                                themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp").getAbsolutePath();

                                String mode = uri.getQueryParameter("mode");
                                if (mode != null) {
                                    mode = mode.toLowerCase();
                                    String[] modes = mode.split(" ");
                                    if (modes != null && modes.length > 0) {
                                        for (int b = 0; b < modes.length; b++) {
                                            if ("blur".equals(modes[b])) {
                                                themeInfo.isBlured = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                String pattern = uri.getQueryParameter("pattern");
                                if (!TextUtils.isEmpty(pattern)) {
                                    try {
                                        String bgColor = uri.getQueryParameter("bg_color");
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
                                        String rotation = uri.getQueryParameter("rotation");
                                        if (!TextUtils.isEmpty(rotation)) {
                                            themeInfo.patternBgGradientRotation = Utilities.parseInt(rotation);
                                        }
                                    } catch (Exception ignore) {

                                    }
                                    String intensity = uri.getQueryParameter("intensity");
                                    if (!TextUtils.isEmpty(intensity)) {
                                        themeInfo.patternIntensity = Utilities.parseInt(intensity);
                                    }
                                    if (themeInfo.patternIntensity == 0) {
                                        themeInfo.patternIntensity = 50;
                                    }
                                }
                            } else if (line.startsWith("WPS")) {
                                themeInfo.previewWallpaperOffset = currentPosition + len;
                                finished = true;
                                break;
                            } else {
                                if ((idx = line.indexOf('=')) != -1) {
                                    String key = line.substring(0, idx);
                                    if (key.equals(Theme.key_chat_inBubble) || key.equals(Theme.key_chat_outBubble) || key.equals(Theme.key_chat_wallpaper) || key.equals(Theme.key_chat_wallpaper_gradient_to1) || key.equals(Theme.key_chat_wallpaper_gradient_to2) || key.equals(Theme.key_chat_wallpaper_gradient_to3)) {
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
                                        switch (key) {
                                            case Theme.key_chat_inBubble:
                                                themeInfo.setPreviewInColor(value);
                                                break;
                                            case Theme.key_chat_outBubble:
                                                themeInfo.setPreviewOutColor(value);
                                                break;
                                            case Theme.key_chat_wallpaper:
                                                themeInfo.setPreviewBackgroundColor(value);
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to1:
                                                themeInfo.previewBackgroundGradientColor1 = value;
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to2:
                                                themeInfo.previewBackgroundGradientColor2 = value;
                                                break;
                                            case Theme.key_chat_wallpaper_gradient_to3:
                                                themeInfo.previewBackgroundGradientColor3 = value;
                                                break;
                                        }
                                    }
                                }
                            }
                            start += len;
                            currentPosition += len;
                        }
                    }
                    if (finished || previousPosition == currentPosition) {
                        break;
                    }
                    stream.getChannel().position(currentPosition);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            if (themeInfo.pathToWallpaper != null && !themeInfo.badWallpaper) {
                file = new File(themeInfo.pathToWallpaper);
                if (!file.exists()) {
                    if (!loadingWallpapers.containsKey(themeInfo)) {
                        loadingWallpapers.put(themeInfo, themeInfo.slug);
                        TLRPC.TL_account_getWallPaper req = new TLRPC.TL_account_getWallPaper();
                        TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                        inputWallPaperSlug.slug = themeInfo.slug;
                        req.wallpaper = inputWallPaperSlug;
                        ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response instanceof TLRPC.TL_wallPaper) {
                                TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) response;
                                String name = FileLoader.getAttachFileName(wallPaper.document);
                                if (!loadingThemes.containsKey(name)) {
                                    loadingThemes.put(name, themeInfo);
                                    FileLoader.getInstance(themeInfo.account).loadFile(wallPaper.document, wallPaper, FileLoader.PRIORITY_NORMAL, 1);
                                }
                            } else {
                                themeInfo.badWallpaper = true;
                            }
                        }));
                    }
                    return false;
                }
            }
            themeInfo.previewParsed = true;
            return true;
        }

        private void applyTheme() {
            inDrawable.setColorFilter(new PorterDuffColorFilter(themeInfo.getPreviewInColor(), PorterDuff.Mode.MULTIPLY));
            outDrawable.setColorFilter(new PorterDuffColorFilter(themeInfo.getPreviewOutColor(), PorterDuff.Mode.MULTIPLY));
            if (themeInfo.pathToFile == null) {
                updateColors(false);
                optionsDrawable = null;
            } else {
                optionsDrawable = getResources().getDrawable(R.drawable.preview_dots).mutate();
                oldBackColor = backColor = themeInfo.getPreviewBackgroundColor();
            }

            bitmapShader = null;
            backgroundDrawable = null;
            double[] hsv = null;
            if (themeInfo.previewBackgroundGradientColor1 != 0 && themeInfo.previewBackgroundGradientColor2 != 0) {
                final MotionBackgroundDrawable drawable = new MotionBackgroundDrawable(themeInfo.getPreviewBackgroundColor(), themeInfo.previewBackgroundGradientColor1, themeInfo.previewBackgroundGradientColor2, themeInfo.previewBackgroundGradientColor3, true);
                drawable.setRoundRadius(AndroidUtilities.dp(6));
                backgroundDrawable = drawable;
                hsv = AndroidUtilities.rgbToHsv(Color.red(themeInfo.getPreviewBackgroundColor()), Color.green(themeInfo.getPreviewBackgroundColor()), Color.blue(themeInfo.getPreviewBackgroundColor()));
            } else if (themeInfo.previewBackgroundGradientColor1 != 0) {
                final GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{themeInfo.getPreviewBackgroundColor(), themeInfo.previewBackgroundGradientColor1});
                drawable.setCornerRadius(AndroidUtilities.dp(6));
                backgroundDrawable = drawable;
                hsv = AndroidUtilities.rgbToHsv(Color.red(themeInfo.getPreviewBackgroundColor()), Color.green(themeInfo.getPreviewBackgroundColor()), Color.blue(themeInfo.getPreviewBackgroundColor()));
            } else if (themeInfo.previewWallpaperOffset > 0 || themeInfo.pathToWallpaper != null) {
                Bitmap wallpaper = AndroidUtilities.getScaledBitmap(AndroidUtilities.dp(76), AndroidUtilities.dp(97), themeInfo.pathToWallpaper, themeInfo.pathToFile, themeInfo.previewWallpaperOffset);
                if (wallpaper != null) {
                    backgroundDrawable = new BitmapDrawable(wallpaper);
                    bitmapShader = new BitmapShader(wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    bitmapPaint.setShader(bitmapShader);
                    int[] colors = AndroidUtilities.calcDrawableColor(backgroundDrawable);
                    hsv = AndroidUtilities.rgbToHsv(Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0]));
                }
            } else if (themeInfo.getPreviewBackgroundColor() != 0) {
                hsv = AndroidUtilities.rgbToHsv(Color.red(themeInfo.getPreviewBackgroundColor()), Color.green(themeInfo.getPreviewBackgroundColor()), Color.blue(themeInfo.getPreviewBackgroundColor()));
            }
            if (hsv != null && hsv[1] <= 0.1f && hsv[2] >= 0.96f) {
                hasWhiteBackground = true;
            } else {
                hasWhiteBackground = false;
            }
            if (themeInfo.getPreviewBackgroundColor() == 0 && themeInfo.previewParsed && backgroundDrawable == null) {
                backgroundDrawable = Theme.createDefaultWallpaper(100, 200);
                if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                    ((MotionBackgroundDrawable) backgroundDrawable).setRoundRadius(AndroidUtilities.dp(6));
                }
            }
            invalidate();
        }

        public void setTheme(Theme.ThemeInfo theme, boolean last, boolean first) {
            themeInfo = theme;
            isFirst = first;
            isLast = last;
            accentId = theme.currentAccentId;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) button.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(isFirst ? 22 + 27 : 27);
            button.setLayoutParams(layoutParams);
            placeholderAlpha = 0.0f;

            if (themeInfo.pathToFile != null && !themeInfo.previewParsed) {
                themeInfo.setPreviewInColor(Theme.getDefaultColor(Theme.key_chat_inBubble));
                themeInfo.setPreviewOutColor(Theme.getDefaultColor(Theme.key_chat_outBubble));
                File file = new File(themeInfo.pathToFile);
                boolean fileExists = file.exists();
                boolean parsed = fileExists && parseTheme();
                if ((!parsed || !fileExists) && themeInfo.info != null) {
                    if (themeInfo.info.document != null) {
                        themeInfo.themeLoaded = false;
                        placeholderAlpha = 1.0f;
                        loadingDrawable = getResources().getDrawable(R.drawable.msg_theme).mutate();
                        Theme.setDrawableColor(loadingDrawable, loadingColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                        if (!fileExists) {
                            String name = FileLoader.getAttachFileName(themeInfo.info.document);
                            if (!loadingThemes.containsKey(name)) {
                                loadingThemes.put(name, themeInfo);
                                FileLoader.getInstance(themeInfo.account).loadFile(themeInfo.info.document, themeInfo.info, FileLoader.PRIORITY_NORMAL, 1);
                            }
                        }
                    } else {
                        loadingDrawable = getResources().getDrawable(R.drawable.preview_custom).mutate();
                        Theme.setDrawableColor(loadingDrawable, loadingColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7));
                    }
                }
            }
            applyTheme();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Theme.ThemeInfo t = currentType == ThemeActivity.THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            button.setChecked(themeInfo == t, false);
            if (themeInfo != null && themeInfo.info != null && !themeInfo.themeLoaded) {
                String name = FileLoader.getAttachFileName(themeInfo.info.document);
                if (!loadingThemes.containsKey(name) && !loadingWallpapers.containsKey(themeInfo)) {
                    themeInfo.themeLoaded = true;
                    placeholderAlpha = 0.0f;
                    parseTheme();
                    applyTheme();
                }
            }
        }

        public void updateCurrentThemeCheck() {
            Theme.ThemeInfo t = currentType == ThemeActivity.THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            button.setChecked(themeInfo == t, true);
        }

        void updateColors(boolean animate) {
            oldInColor = inColor;
            oldOutColor = outColor;
            oldBackColor = backColor;
            oldCheckColor = checkColor;

            Theme.ThemeAccent accent = themeInfo.getAccent(false);

            int accentColor;
            int myAccentColor;
            int backAccent;
            if (accent != null) {
                accentColor = accent.accentColor;
                myAccentColor = accent.myMessagesAccentColor != 0 ? accent.myMessagesAccentColor : accentColor;
                int backgroundOverrideColor = (int) accent.backgroundOverrideColor;
                backAccent = backgroundOverrideColor != 0 ? backgroundOverrideColor : accentColor;
            } else {
                accentColor = 0;
                myAccentColor = 0;
                backAccent = 0;
            }
            inColor = Theme.changeColorAccent(themeInfo, accentColor, themeInfo.getPreviewInColor());
            outColor = Theme.changeColorAccent(themeInfo, myAccentColor, themeInfo.getPreviewOutColor());
            backColor = Theme.changeColorAccent(themeInfo, backAccent, themeInfo.getPreviewBackgroundColor());
            checkColor = outColor;
            accentId = themeInfo.currentAccentId;

            if (accentAnimator != null) {
                accentAnimator.cancel();
            }

            if (animate) {
                accentAnimator = ObjectAnimator.ofFloat(this, "accentState", 0f, 1f);
                accentAnimator.setDuration(200);
                accentAnimator.start();
            } else {
                setAccentState(1f);
            }
        }

        @Keep
        public float getAccentState() {
            return accentState;
        }

        @Keep
        public void setAccentState(float state) {
            accentState = state;
            accentColorChanged = true;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (accentId != themeInfo.currentAccentId) {
                updateColors(true);
            }

            int x = isFirst ? AndroidUtilities.dp(22) : 0;
            int y = AndroidUtilities.dp(11);
            rect.set(x, y, x + AndroidUtilities.dp(76), y + AndroidUtilities.dp(97));

            String name = getThemeName();
            int maxWidth = getMeasuredWidth() - AndroidUtilities.dp(isFirst ? 10 : 15) - (isLast ? AndroidUtilities.dp(7) : 0);
            String text = TextUtils.ellipsize(name, textPaint, maxWidth, TextUtils.TruncateAt.END).toString();
            int width = (int) Math.ceil(textPaint.measureText(text));
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.drawText(text, x + (AndroidUtilities.dp(76) - width) / 2, AndroidUtilities.dp(131), textPaint);

            boolean drawContent = themeInfo.info == null || themeInfo.info.document != null && themeInfo.themeLoaded;

            if (drawContent) {
                paint.setColor(blend(oldBackColor, backColor));

                if (accentColorChanged) {
                    inDrawable.setColorFilter(new PorterDuffColorFilter(blend(oldInColor, inColor), PorterDuff.Mode.MULTIPLY));
                    outDrawable.setColorFilter(new PorterDuffColorFilter(blend(oldOutColor, outColor), PorterDuff.Mode.MULTIPLY));
                    accentColorChanged = false;
                }

                if (backgroundDrawable != null) {
                    if (bitmapShader != null) {
                        BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                        float bitmapW = bitmapDrawable.getBitmap().getWidth();
                        float bitmapH = bitmapDrawable.getBitmap().getHeight();
                        float scaleW = bitmapW / rect.width();
                        float scaleH = bitmapH / rect.height();

                        shaderMatrix.reset();
                        float scale = 1.0f / Math.min(scaleW, scaleH);
                        if (bitmapW / scaleH > rect.width()) {
                            bitmapW /= scaleH;
                            shaderMatrix.setTranslate(x - (bitmapW - rect.width()) / 2, y);
                        } else {
                            bitmapH /= scaleW;
                            shaderMatrix.setTranslate(x, y - (bitmapH - rect.height()) / 2);
                        }
                        shaderMatrix.preScale(scale, scale);
                        bitmapShader.setLocalMatrix(shaderMatrix);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), bitmapPaint);
                    } else {
                        backgroundDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                        backgroundDrawable.draw(canvas);
                    }
                } else {
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                }

                button.setColor(0x66ffffff, 0xffffffff);

                if (themeInfo.accentBaseColor != 0) {
                    if ("Day".equals(themeInfo.name) || "Arctic Blue".equals(themeInfo.name)) {
                        button.setColor(0xffb3b3b3, blend(oldCheckColor, checkColor));
                        Theme.chat_instantViewRectPaint.setColor(0x2bb0b5ba);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.chat_instantViewRectPaint);
                    }
                } else if (hasWhiteBackground) {
                    button.setColor(0xffb3b3b3, themeInfo.getPreviewOutColor());
                    Theme.chat_instantViewRectPaint.setColor(0x2bb0b5ba);
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.chat_instantViewRectPaint);
                }

                inDrawable.setBounds(x + AndroidUtilities.dp(6), AndroidUtilities.dp(22), x + AndroidUtilities.dp(6 + 43), AndroidUtilities.dp(22 + 14));
                inDrawable.draw(canvas);

                outDrawable.setBounds(x + AndroidUtilities.dp(27), AndroidUtilities.dp(41), x + AndroidUtilities.dp(27 + 43), AndroidUtilities.dp(41 + 14));
                outDrawable.draw(canvas);

                if (optionsDrawable != null && currentType == ThemeActivity.THEME_TYPE_BASIC) {
                    x = (int) rect.right - AndroidUtilities.dp(16);
                    y = (int) rect.top + AndroidUtilities.dp(6);
                    optionsDrawable.setBounds(x, y, x + optionsDrawable.getIntrinsicWidth(), y + optionsDrawable.getIntrinsicHeight());
                    optionsDrawable.draw(canvas);
                }
            }

            if (themeInfo.info != null && themeInfo.info.document == null) {
                button.setAlpha(0.0f);
                Theme.chat_instantViewRectPaint.setColor(0x2bb0b5ba);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.chat_instantViewRectPaint);
                if (loadingDrawable != null) {
                    int newColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7);
                    if (loadingColor != newColor) {
                        Theme.setDrawableColor(loadingDrawable, loadingColor = newColor);
                    }
                    x = (int) (rect.centerX() - loadingDrawable.getIntrinsicWidth() / 2);
                    y = (int) (rect.centerY() - loadingDrawable.getIntrinsicHeight() / 2);
                    loadingDrawable.setBounds(x, y, x + loadingDrawable.getIntrinsicWidth(), y + loadingDrawable.getIntrinsicHeight());
                    loadingDrawable.draw(canvas);
                }
            } else if (themeInfo.info != null && !themeInfo.themeLoaded || placeholderAlpha > 0.0f) {
                button.setAlpha(1.0f - placeholderAlpha);
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                paint.setAlpha((int) (placeholderAlpha * 255));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                if (loadingDrawable != null) {
                    int newColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7);
                    if (loadingColor != newColor) {
                        Theme.setDrawableColor(loadingDrawable, loadingColor = newColor);
                    }
                    x = (int) (rect.centerX() - loadingDrawable.getIntrinsicWidth() / 2);
                    y = (int) (rect.centerY() - loadingDrawable.getIntrinsicHeight() / 2);
                    loadingDrawable.setAlpha((int) (placeholderAlpha * 255));
                    loadingDrawable.setBounds(x, y, x + loadingDrawable.getIntrinsicWidth(), y + loadingDrawable.getIntrinsicHeight());
                    loadingDrawable.draw(canvas);
                }
                if (themeInfo.themeLoaded) {
                    long newTime = SystemClock.elapsedRealtime();
                    long dt = Math.min(17, newTime - lastDrawTime);
                    lastDrawTime = newTime;
                    placeholderAlpha -= dt / 180.0f;
                    if (placeholderAlpha < 0.0f) {
                        placeholderAlpha = 0.0f;
                    }
                    invalidate();
                }
            } else if (button.getAlpha() != 1.0f) {
                button.setAlpha(1.0f);
            }
        }

        private String getThemeName() {
            String name = themeInfo.getName();
            if (name.toLowerCase().endsWith(".attheme")) {
                name = name.substring(0, name.lastIndexOf('.'));
            }
            return name;
        }

        private int blend(int color1, int color2) {
            if (accentState == 1.0f) {
                return color2;
            } else {
                return (int) evaluator.evaluate(accentState, color1, color2);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(getThemeName());
            info.setClassName(Button.class.getName());
            info.setChecked(button.isChecked());
            info.setCheckable(true);
            info.setEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions)));
            }
        }
    }

    public ThemesHorizontalListCell(Context context, BaseFragment fragment, int type, ArrayList<Theme.ThemeInfo> def, ArrayList<Theme.ThemeInfo> custom) {
        super(context);

        customThemes = custom;
        defaultThemes = def;
        currentType = type;
        this.fragment = fragment;

        if (type == ThemeActivity.THEME_TYPE_OTHER) {
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        } else {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        setItemAnimator(null);
        setLayoutAnimation(null);
        horizontalLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        setPadding(0, 0, 0, 0);
        setClipToPadding(false);
        horizontalLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        setLayoutManager(horizontalLayoutManager);
        setAdapter(adapter = new ThemesListAdapter(context));
        setOnItemClickListener((view1, position) -> {
            selectTheme(((InnerThemeView) view1).themeInfo);
            int left = view1.getLeft();
            int right = view1.getRight();
            if (left < 0) {
                smoothScrollBy(left - AndroidUtilities.dp(8), 0);
            } else if (right > getMeasuredWidth()) {
                smoothScrollBy(right - getMeasuredWidth(), 0);
            }
        });
        setOnItemLongClickListener((view12, position) -> {
            InnerThemeView innerThemeView = (InnerThemeView) view12;
            showOptionsForTheme(innerThemeView.themeInfo);
            return true;
        });
    }

    public void selectTheme(Theme.ThemeInfo themeInfo) {
        if (themeInfo.info != null) {
            if (!themeInfo.themeLoaded) {
                return;
            }
            if (themeInfo.info.document == null) {
                if (fragment != null) {
                    fragment.presentFragment(new ThemeSetUrlActivity(themeInfo, null, true));
                }
                return;
            }
        }
        if (!TextUtils.isEmpty(themeInfo.assetName)) {
            Theme.PatternsLoader.createLoader(false);
        }

        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE).edit();
        editor.putString(currentType == ThemeActivity.THEME_TYPE_NIGHT || themeInfo.isDark() ? "lastDarkTheme" : "lastDayTheme", themeInfo.getKey());
        editor.commit();

        if (currentType == ThemeActivity.THEME_TYPE_NIGHT) {
            if (themeInfo == Theme.getCurrentNightTheme()) {
                return;
            }
            Theme.setCurrentNightTheme(themeInfo);
        } else {
            if (themeInfo == Theme.getCurrentTheme()) {
                return;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);
        }
        updateRows();

        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child instanceof InnerThemeView) {
                ((InnerThemeView) child).updateCurrentThemeCheck();
            }
        }
        EmojiThemes.saveCustomTheme(themeInfo, themeInfo.currentAccentId);

        if (currentType != ThemeActivity.THEME_TYPE_NIGHT) {
            Theme.turnOffAutoNight(fragment);
        }
    }

    public void setDrawDivider(boolean draw) {
        drawDivider = draw;
    }

    public void notifyDataSetChanged(int width) {
        if (prevCount == adapter.getItemCount()) {
            return;
        }
        adapter.notifyDataSetChanged();
        Theme.ThemeInfo t = currentType == ThemeActivity.THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
        if (prevThemeInfo != t) {
            scrollToCurrentTheme(width, false);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (getParent() != null && getParent().getParent() != null) {
            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        invalidateViews();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.fileLoadFailed);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.fileLoadFailed);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded) {
            String fileName = (String) args[0];
            File file = (File) args[1];
            Theme.ThemeInfo info = loadingThemes.get(fileName);
            if (info != null) {
                loadingThemes.remove(fileName);
                if (loadingWallpapers.remove(info) != null) {
                    Utilities.globalQueue.postRunnable(() -> {
                        info.badWallpaper = !info.createBackground(file, info.pathToWallpaper);
                        AndroidUtilities.runOnUIThread(() -> checkVisibleTheme(info));
                    });
                } else {
                    checkVisibleTheme(info);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String fileName = (String) args[0];
            loadingThemes.remove(fileName);
        }
    }

    private void checkVisibleTheme(Theme.ThemeInfo info) {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child instanceof InnerThemeView) {
                InnerThemeView view = (InnerThemeView) child;
                if (view.themeInfo == info) {
                    if (view.parseTheme()) {
                        view.themeInfo.themeLoaded = true;
                        view.applyTheme();
                    }
                }
            }
        }
    }

    public void scrollToCurrentTheme(int width, boolean animated) {
        if (width == 0) {
            View parent = (View) getParent();
            if (parent != null) {
                width = parent.getMeasuredWidth();
            }
        }
        if (width == 0) {
            return;
        }
        prevThemeInfo = currentType == ThemeActivity.THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
        int index = defaultThemes.indexOf(prevThemeInfo);
        if (index < 0) {
            index = customThemes.indexOf(prevThemeInfo) + defaultThemes.size();
            if (index < 0) {
                return;
            }
        }
        if (animated) {
            smoothScrollToPosition(index);
        } else {
            horizontalLayoutManager.scrollToPositionWithOffset(index, (width - AndroidUtilities.dp(76)) / 2);
        }
    }

    protected void showOptionsForTheme(Theme.ThemeInfo themeInfo) {

    }

    protected void updateRows() {

    }
}

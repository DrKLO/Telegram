/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileInputStream;

public class ThemeCell extends FrameLayout {

    private TextView textView;
    private ImageView checkImage;
    private ImageView optionsButton;
    private boolean needDivider;
    private Paint paint;
    private Paint paintStroke;
    private Theme.ThemeInfo currentThemeInfo;
    private boolean isNightTheme;
    private static byte[] bytes = new byte[1024];

    public ThemeCell(Context context, boolean nightTheme) {
        super(context);

        setWillNotDraw(false);

        isNightTheme = nightTheme;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(AndroidUtilities.dp(2));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setPadding(0, 0, 0, AndroidUtilities.dp(1));
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 53 + 48 + 4 : 60, 0, LocaleController.isRTL ? 60 : 53 + 48 + 4, 0));

        checkImage = new ImageView(context);
        checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
        checkImage.setImageResource(R.drawable.sticker_added);

        if (!isNightTheme) {
            addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21 + 38, 0, 21 + 38, 0));

            optionsButton = new ImageView(context);
            optionsButton.setFocusable(false);
            optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            optionsButton.setImageResource(R.drawable.ic_ab_other);
            optionsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            optionsButton.setScaleType(ImageView.ScaleType.CENTER);
            optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            addView(optionsButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP));
        } else {
            addView(checkImage, LayoutHelper.createFrame(19, 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        checkImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addedIcon), PorterDuff.Mode.MULTIPLY));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    public void setOnOptionsClick(OnClickListener listener) {
        optionsButton.setOnClickListener(listener);
    }

    public TextView getTextView() {
        return textView;
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public Theme.ThemeInfo getCurrentThemeInfo() {
        return currentThemeInfo;
    }

    public void setTheme(Theme.ThemeInfo themeInfo, boolean divider) {
        currentThemeInfo = themeInfo;
        String text = themeInfo.getName();
        if (text.endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        textView.setText(text);
        needDivider = divider;
        updateCurrentThemeCheck();

        boolean finished = false;
        if (Theme.isThemeDefault(themeInfo) || themeInfo.assetName != null) {
            paint.setColor(Theme.changeColorAccent(themeInfo, themeInfo.accentColor, themeInfo.previewBackgroundColor));
            finished = true;
        } else if (themeInfo.pathToFile != null) {
            FileInputStream stream = null;
            try {
                int currentPosition = 0;
                File file = new File(themeInfo.pathToFile);
                stream = new FileInputStream(file);
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
                            if (line.startsWith("WPS")) {
                                break;
                            } else {
                                if ((idx = line.indexOf('=')) != -1) {
                                    String key = line.substring(0, idx);
                                    if (key.equals(Theme.key_actionBarDefault)) {
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
                                        finished = true;
                                        paint.setColor(value);
                                        break;
                                    }
                                }
                            }
                            start += len;
                            currentPosition += len;
                        }
                    }
                    if (previousPosition == currentPosition || linesRead >= 500) {
                        break;
                    }
                    stream.getChannel().position(currentPosition);
                    if (finished) {
                        break;
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
        if (!finished) {
            paint.setColor(Theme.getDefaultColor(Theme.key_actionBarDefault));
        }
        paintStroke.setColor(themeInfo.accentColor);
        if (themeInfo.accentColor != 0) {
            paintStroke.setAlpha(180);
        }
    }

    public void updateCurrentThemeCheck() {
        Theme.ThemeInfo currentTheme;
        if (isNightTheme) {
            currentTheme = Theme.getCurrentNightTheme();
        } else {
            currentTheme = Theme.getCurrentTheme();
        }
        int newVisibility = currentThemeInfo == currentTheme ? VISIBLE : INVISIBLE;
        if (checkImage.getVisibility() != newVisibility) {
            checkImage.setVisibility(newVisibility);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
        int x = AndroidUtilities.dp(16 + 15);
        if (LocaleController.isRTL) {
            x = getWidth() - x;
        }
        canvas.drawCircle(x, AndroidUtilities.dp(13 + 11), AndroidUtilities.dp(11), paint);
        canvas.drawCircle(x, AndroidUtilities.dp(13 + 11), AndroidUtilities.dp(10), paintStroke);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        setSelected(checkImage.getVisibility() == VISIBLE);
    }
}

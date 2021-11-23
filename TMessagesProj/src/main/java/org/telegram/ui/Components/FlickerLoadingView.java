package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

public class FlickerLoadingView extends View {

    public final static int DIALOG_TYPE = 1;
    public final static int PHOTOS_TYPE = 2;
    public final static int FILES_TYPE = 3;
    public final static int AUDIO_TYPE = 4;
    public final static int LINKS_TYPE = 5;
    public final static int USERS_TYPE = 6;
    public final static int DIALOG_CELL_TYPE = 7;
    public final static int CALL_LOG_TYPE = 8;
    public final static int INVITE_LINKS_TYPE = 9;
    public final static int USERS2_TYPE = 10;
    public final static int BOTS_MENU_TYPE = 11;
    public final static int SHARE_ALERT_TYPE = 12;
    public final static int MESSAGE_SEEN_TYPE = 13;
    public final static int CHAT_THEMES_TYPE = 14;
    public final static int MEMBER_REQUESTS_TYPE = 15;

    private int gradientWidth;
    private LinearGradient gradient;
    private Paint paint = new Paint();
    private Paint headerPaint = new Paint();
    private long lastUpdateTime;
    private int totalTranslation;
    private Matrix matrix;
    private RectF rectF = new RectF();
    private int color0;
    private int color1;
    private int skipDrawItemsCount;

    private boolean showDate = true;
    private boolean useHeaderOffset;
    private boolean isSingleCell;

    private int viewType;
    private int paddingTop;
    private int paddingLeft;

    private String colorKey1 = Theme.key_windowBackgroundWhite;
    private String colorKey2 = Theme.key_windowBackgroundGray;
    private String colorKey3;
    private int itemsCount = 1;
    private final Theme.ResourcesProvider resourcesProvider;

    float[] randomParams;
    private Paint backgroundPaint;
    private int parentWidth;
    private int parentHeight;
    private float parentXOffset;

    FlickerLoadingView globalGradientView;

    public void setViewType(int type) {
        this.viewType = type;
        if (viewType == BOTS_MENU_TYPE) {
            Random random = new Random();
            randomParams = new float[2];
            for (int i = 0; i < 2; i++) {
                randomParams[i] = Math.abs(random.nextInt() % 1000) / 1000f;
            }
        }
        invalidate();
    }

    public void setIsSingleCell(boolean b) {
        isSingleCell = b;
    }

    public int getViewType() {
        return viewType;
    }

    public int getColumnsCount() {
        return 2;
    }

    public void setColors(String key1, String key2, String key3) {
        colorKey1 = key1;
        colorKey2 = key2;
        colorKey3 = key3;
        invalidate();
    }

    public FlickerLoadingView(Context context) {
        this(context, null);
    }

    public FlickerLoadingView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        matrix = new Matrix();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isSingleCell) {
            if (itemsCount > 1 && MeasureSpec.getSize(heightMeasureSpec) > 0) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(heightMeasureSpec), getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) * itemsCount), MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY));
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {

        Paint paint = this.paint;
        if (globalGradientView != null) {
            if (getParent() != null) {
                View parent = (View) getParent();
                globalGradientView.setParentSize(parent.getMeasuredWidth(), parent.getMeasuredHeight(), -getX());
            }
            paint = globalGradientView.paint;
        }

        updateColors();
        updateGradient();

        int h = paddingTop;
        if (useHeaderOffset) {
            h += AndroidUtilities.dp(32);
            if (colorKey3 != null) {
                headerPaint.setColor(getThemedColor(colorKey3));
            }
            canvas.drawRect(0,0, getMeasuredWidth(), AndroidUtilities.dp(32), colorKey3 != null ? headerPaint : paint);
        }
        if (getViewType() == DIALOG_CELL_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int childH = getCellHeight(getMeasuredWidth());
                int r = AndroidUtilities.dp(28);
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(10) + r), h + (childH >> 1), r, paint);

                rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(16), AndroidUtilities.dp(148), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(38), AndroidUtilities.dp(268), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (SharedConfig.useThreeLinesLayout) {
                    rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(46 + 8), AndroidUtilities.dp(220), h + AndroidUtilities.dp(46 + 8 + 8));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(16), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == DIALOG_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(25);
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(9) + r), h + (AndroidUtilities.dp(78) >> 1), r, paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(20), AndroidUtilities.dp(140), h + AndroidUtilities.dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(42), AndroidUtilities.dp(260), h + AndroidUtilities.dp(50));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == PHOTOS_TYPE) {
            int photoWidth = (getMeasuredWidth() - (AndroidUtilities.dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
            int k = 0;
            while (h < getMeasuredHeight() || isSingleCell) {
                for (int i = 0; i < getColumnsCount(); i++) {
                    if (k == 0 && i < skipDrawItemsCount) {
                         continue;
                    }
                    int x = i * (photoWidth + AndroidUtilities.dp(2));
                    canvas.drawRect(x, h, x + photoWidth, h + photoWidth, paint);
                }
                h += photoWidth + AndroidUtilities.dp(2);
                k++;
                if (isSingleCell && k >= 2) {
                    break;
                }
            }
        } else if (getViewType() == 3) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                rectF.set(AndroidUtilities.dp(12), h + AndroidUtilities.dp(8), AndroidUtilities.dp(52), h + AndroidUtilities.dp(48));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(12), AndroidUtilities.dp(140), h + AndroidUtilities.dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(34), AndroidUtilities.dp(260), h + AndroidUtilities.dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == 4) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int radius = AndroidUtilities.dp(44) >> 1;
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(12) + radius), h + AndroidUtilities.dp(6) + radius, radius, paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(12), AndroidUtilities.dp(140), h + AndroidUtilities.dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(34), AndroidUtilities.dp(260), h + AndroidUtilities.dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == 5) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                rectF.set(AndroidUtilities.dp(10), h + AndroidUtilities.dp(11), AndroidUtilities.dp(62), h + AndroidUtilities.dp(11 + 52));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(12), AndroidUtilities.dp(140), h + AndroidUtilities.dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(34), AndroidUtilities.dp(268), h + AndroidUtilities.dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(34 + 20), AndroidUtilities.dp(120 + 68), h + AndroidUtilities.dp(42 + 20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == 6 || getViewType() == USERS2_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(9) + r), h + (AndroidUtilities.dp(64) >> 1), r, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(68), h + AndroidUtilities.dp(17), paddingLeft + AndroidUtilities.dp(260), h + AndroidUtilities.dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(68), h + AndroidUtilities.dp(39), paddingLeft + AndroidUtilities.dp(140), h + AndroidUtilities.dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == CALL_LOG_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(11) + r), h + (AndroidUtilities.dp(64) >> 1), r, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(68), h + AndroidUtilities.dp(17), paddingLeft + AndroidUtilities.dp(140), h + AndroidUtilities.dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(68), h + AndroidUtilities.dp(39), paddingLeft + AndroidUtilities.dp(260), h + AndroidUtilities.dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == INVITE_LINKS_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int childH = getCellHeight(getMeasuredWidth());
                int r = AndroidUtilities.dp(32) / 2;
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(35)), h + (childH >> 1), r, paint);

                rectF.set(AndroidUtilities.dp(72), h + AndroidUtilities.dp(16), AndroidUtilities.dp(268), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(72), h + AndroidUtilities.dp(38), AndroidUtilities.dp(140), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(16), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == BOTS_MENU_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                rectF.set(AndroidUtilities.dp(18), AndroidUtilities.dp((36 - 8) / 2f), getMeasuredWidth() * 0.5f + AndroidUtilities.dp(40 * randomParams[0]), AndroidUtilities.dp((36 - 8) / 2f) + AndroidUtilities.dp(8));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(getMeasuredWidth() - AndroidUtilities.dp(18), AndroidUtilities.dp((36 - 8) / 2f), getMeasuredWidth() - getMeasuredWidth() * 0.2f -AndroidUtilities.dp(20 * randomParams[0]), AndroidUtilities.dp((36 - 8) / 2f) + AndroidUtilities.dp(8));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

//                rectF.set(AndroidUtilities.dp(), AndroidUtilities.dp((36 - 8) / 2), AndroidUtilities.dp(268), AndroidUtilities.dp((36 - 8) / 2) + AndroidUtilities.dp(8));
//                checkRtl(rectF);
//                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == SHARE_ALERT_TYPE) {
            int k = 0;
            h += AndroidUtilities.dp(14);
            while (h <= getMeasuredHeight()) {
                int part = getMeasuredWidth() / 4;
                for (int i = 0; i < 4; i++) {
                    float cx = part * i + part / 2f;
                    float cy = h + AndroidUtilities.dp(7) + AndroidUtilities.dp(56) / 2f;
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(56 / 2f), paint);

                    float y = h + AndroidUtilities.dp(7) + AndroidUtilities.dp(56) + AndroidUtilities.dp(16);
                    AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(24), y - AndroidUtilities.dp(4), cx + AndroidUtilities.dp(24), y + AndroidUtilities.dp(4));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }
                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell) {
                    break;
                }
            }
        } else if (getViewType() == MESSAGE_SEEN_TYPE) {
            float cy = getMeasuredHeight() / 2f;

            AndroidUtilities.rectTmp.set(AndroidUtilities.dp(40), cy - AndroidUtilities.dp(4), getMeasuredWidth() - AndroidUtilities.dp(120), cy + AndroidUtilities.dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            }

            for (int i = 0; i < 3; i++) {
                canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(8 + 24 + 12 + 12) + AndroidUtilities.dp(13) + AndroidUtilities.dp(12) * i, cy, AndroidUtilities.dp(13f), backgroundPaint);
                canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(8 + 24 + 12 + 12) + AndroidUtilities.dp(13) + AndroidUtilities.dp(12) * i, cy, AndroidUtilities.dp(12f), paint);
            }
        } else if (getViewType() == CHAT_THEMES_TYPE) {
            int x = AndroidUtilities.dp(12);
            int itemWidth = AndroidUtilities.dp(77);
            int INNER_RECT_SPACE = AndroidUtilities.dp(4);
            float BUBBLE_HEIGHT = AndroidUtilities.dp(21);
            float BUBBLE_WIDTH = AndroidUtilities.dp(41);

            while (x < getMeasuredWidth()) {

                if (backgroundPaint == null) {
                    backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                }

                float bubbleTop = INNER_RECT_SPACE + AndroidUtilities.dp(8);
                float bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(22);
                AndroidUtilities.rectTmp.set(x + AndroidUtilities.dp(4), AndroidUtilities.dp(4), x + itemWidth - AndroidUtilities.dp(4), getMeasuredHeight() - AndroidUtilities.dp(4));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);

                rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);
                bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(5);
                bubbleTop += BUBBLE_HEIGHT + AndroidUtilities.dp(4);
                rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);


                canvas.drawCircle(x + itemWidth / 2, getMeasuredHeight() - AndroidUtilities.dp(20), AndroidUtilities.dp(8), backgroundPaint);
                x += itemWidth;
            }
        } else if (getViewType() == MEMBER_REQUESTS_TYPE) {
            int count = 0;
            int radius = AndroidUtilities.dp(23);
            int rectRadius = AndroidUtilities.dp(4);
            while (h <= getMeasuredHeight()) {
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(12)) + radius, h + AndroidUtilities.dp(8) + radius, radius, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(74), h + AndroidUtilities.dp(12), paddingLeft + AndroidUtilities.dp(260), h + AndroidUtilities.dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(74), h + AndroidUtilities.dp(36), paddingLeft + AndroidUtilities.dp(140), h + AndroidUtilities.dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                h += getCellHeight(getMeasuredWidth());
                count++;
                if (isSingleCell && count >= itemsCount) {
                    break;
                }
            }
        }
        invalidate();
    }

    public void updateGradient() {
        if (globalGradientView != null) {
            globalGradientView.updateGradient();
            return;
        }
        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = Math.abs(lastUpdateTime - newUpdateTime);
        if (dt > 17) {
            dt = 16;
        }
        if (dt < 4) {
            dt = 0;
        }
        int width = parentWidth;
        if (width == 0) {
            width = getMeasuredWidth();
        }
        int height = parentHeight;
        if (height == 0) {
            height = getMeasuredHeight();
        }
        lastUpdateTime = newUpdateTime;
        if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || getViewType() == CHAT_THEMES_TYPE) {
            totalTranslation += dt * width / 400.0f;
            if (totalTranslation >= width * 2) {
                totalTranslation = -gradientWidth * 2;
            }
            matrix.setTranslate(totalTranslation + parentXOffset, 0);
        } else {
            totalTranslation += dt * height / 400.0f;
            if (totalTranslation >= height * 2) {
                totalTranslation = -gradientWidth * 2;
            }
            matrix.setTranslate(parentXOffset, totalTranslation);
        }
        gradient.setLocalMatrix(matrix);
    }

    public void updateColors() {
        if (globalGradientView != null) {
            globalGradientView.updateColors();
            return;
        }
        int color0 = getThemedColor(colorKey1);
        int color1 = getThemedColor(colorKey2);
        if (this.color1 != color1 || this.color0 != color0) {
            this.color0 = color0;
            this.color1 = color1;
            if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || viewType == CHAT_THEMES_TYPE) {
                gradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(200), 0, new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            } else {
                gradient = new LinearGradient(0, 0, 0, gradientWidth = AndroidUtilities.dp(600), new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            }
            paint.setShader(gradient);
        }
    }

    private float checkRtl(float x) {
        if (LocaleController.isRTL) {
            return getMeasuredWidth() - x;
        }
        return x;
    }

    private void checkRtl(RectF rectF) {
        if (LocaleController.isRTL) {
            rectF.left = getMeasuredWidth() - rectF.left;
            rectF.right = getMeasuredWidth() - rectF.right;
        }
    }

    private int getCellHeight(int width) {
        if (getViewType() == DIALOG_CELL_TYPE) {
            return AndroidUtilities.dp((SharedConfig.useThreeLinesLayout ? 78 : 72) + 1);
        } else if (getViewType() == DIALOG_TYPE) {
            return AndroidUtilities.dp(78) + 1;
        } else if (getViewType() == PHOTOS_TYPE) {
            int photoWidth = (width - (AndroidUtilities.dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
            return photoWidth + AndroidUtilities.dp(2);
        } else if (getViewType() == 3) {
            return AndroidUtilities.dp(56);
        } else if (getViewType() == 4) {
            return AndroidUtilities.dp(56);
        } else if (getViewType() == 5) {
            return AndroidUtilities.dp(80);
        } else if (getViewType() == USERS_TYPE) {
            return AndroidUtilities.dp(64);
        } else if (getViewType() == INVITE_LINKS_TYPE) {
            return AndroidUtilities.dp(66);
        } else if (getViewType() == USERS2_TYPE) {
            return AndroidUtilities.dp(58);
        } else if (getViewType() == CALL_LOG_TYPE) {
            return AndroidUtilities.dp(61);
        } else if (getViewType() == BOTS_MENU_TYPE) {
            return AndroidUtilities.dp(36);
        } else if (getViewType() == SHARE_ALERT_TYPE) {
            return AndroidUtilities.dp(103);
        } else if (getViewType() == MEMBER_REQUESTS_TYPE) {
            return AndroidUtilities.dp(107);
        }
        return 0;
    }

    public void showDate(boolean showDate) {
        this.showDate = showDate;
    }

    public void setUseHeaderOffset(boolean useHeaderOffset) {
        this.useHeaderOffset = useHeaderOffset;
    }

    public void skipDrawItemsCount(int i) {
        skipDrawItemsCount = i;
    }

    public void setPaddingTop(int t) {
        paddingTop = t;
        invalidate();
    }

    public void setPaddingLeft(int paddingLeft) {
        this.paddingLeft = paddingLeft;
        invalidate();
    }

    public void setItemsCount(int i) {
        this.itemsCount = i;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void setGlobalGradientView(FlickerLoadingView globalGradientView) {
        this.globalGradientView = globalGradientView;
    }

    public void setParentSize(int parentWidth, int parentHeight, float parentXOffset) {
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;
        this.parentXOffset = parentXOffset;
    }

    public Paint getPaint() {
        return paint;
    }
}
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
import org.telegram.ui.ActionBar.Theme;

public class FlickerLoadingView extends View {

    public final static int DIALOG_TYPE = 1;
    public final static int PHOTOS_TYPE = 2;
    public final static int FILES_TYPE = 3;
    public final static int AUDIO_TYPE = 4;
    public final static int LINKS_TYPE = 5;
    public final static int USERS_TYPE = 6;

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

    private String colorKey1 = Theme.key_windowBackgroundWhite;
    private String colorKey2 = Theme.key_windowBackgroundGray;
    private String colorKey3;

    public void setViewType(int type) {
        this.viewType = type;
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
        super(context);
        matrix = new Matrix();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isSingleCell) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int color0 = Theme.getColor(colorKey1);
        int color1 = Theme.getColor(colorKey2);
        if (this.color1 != color1 || this.color0 != color0) {
            this.color0 = color0;
            this.color1 = color1;
            if (isSingleCell) {
                gradient = new LinearGradient(0, 0, gradientWidth = AndroidUtilities.dp(200), 0, new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            } else {
                gradient = new LinearGradient(0, 0, 0, gradientWidth = AndroidUtilities.dp(600), new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            }
            paint.setShader(gradient);
        }

        int h = 0;
        if (useHeaderOffset) {
            h += AndroidUtilities.dp(32);
            if (colorKey3 != null) {
                headerPaint.setColor(Theme.getColor(colorKey3));
            }
            canvas.drawRect(0,0, getMeasuredWidth(), AndroidUtilities.dp(32), colorKey3 != null ? headerPaint : paint);
        }
        if (getViewType() == DIALOG_TYPE) {
            while (h < getMeasuredHeight()) {
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
                if (isSingleCell) {
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
            while (h < getMeasuredHeight()) {
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
                if (isSingleCell) {
                    break;
                }
            }
        } else if (getViewType() == 4) {
            while (h < getMeasuredHeight()) {
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
                if (isSingleCell) {
                    break;
                }
            }
        } else if (getViewType() == 5) {
            while (h < getMeasuredHeight()) {
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
                if (isSingleCell) {
                    break;
                }
            }
        } else if (getViewType() == 6) {
            while (h < getMeasuredHeight()) {
                int r = AndroidUtilities.dp(23);
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(9) + r), h + (AndroidUtilities.dp(64) >> 1), r, paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(17), AndroidUtilities.dp(260), h + AndroidUtilities.dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(68), h + AndroidUtilities.dp(39), AndroidUtilities.dp(140), h + AndroidUtilities.dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(20), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                if (isSingleCell) {
                    break;
                }
            }
        }

        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = Math.abs(lastUpdateTime - newUpdateTime);
        if (dt > 17) {
            dt = 16;
        }
        lastUpdateTime = newUpdateTime;
        if (isSingleCell) {
            totalTranslation += dt * getMeasuredWidth() / 400.0f;
            if (totalTranslation >= getMeasuredWidth() * 2) {
                totalTranslation = -gradientWidth * 2;
            }
            matrix.setTranslate(totalTranslation, 0);
        } else {
            totalTranslation += dt * getMeasuredHeight() / 400.0f;
            if (totalTranslation >= getMeasuredHeight() * 2) {
                totalTranslation = -gradientWidth * 2;
            }
            matrix.setTranslate(0, totalTranslation);
        }
        gradient.setLocalMatrix(matrix);
        invalidate();
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
        if (getViewType() == DIALOG_TYPE) {
            return AndroidUtilities.dp(78) + 1;
        } else if (getViewType() == PHOTOS_TYPE) {
            int photoWidth = (width - (AndroidUtilities.dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
            return photoWidth + AndroidUtilities.dp(2);
        } else if (getViewType() == 3) {
            return AndroidUtilities.dp(56) + 1;
        } else if (getViewType() == 4) {
            return AndroidUtilities.dp(56) + 1;
        } else if (getViewType() == 5) {
            return AndroidUtilities.dp(80);
        } else if (getViewType() == USERS_TYPE) {
            return AndroidUtilities.dp(64);
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
}
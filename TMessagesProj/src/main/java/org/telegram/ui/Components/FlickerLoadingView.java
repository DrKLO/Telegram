package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
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
    public final static int REACTED_TYPE = 16;
    public final static int QR_TYPE = 17;
    public final static int CONTACT_TYPE = 18;
    public final static int STICKERS_TYPE = 19;
    public final static int LIMIT_REACHED_GROUPS = 21;
    public final static int LIMIT_REACHED_LINKS = 22;
    public final static int REACTED_TYPE_WITH_EMOJI_HINT = 23;
    public static final int TOPIC_CELL_TYPE = 24;
    public static final int DIALOG_CACHE_CONTROL = 25;
    public static final int CHECKBOX_TYPE = 26;
    public final static int STORIES_TYPE = 27;
    public static final int SOTRY_VIEWS_USER_TYPE = 28;
    public static final int PROFILE_SEARCH_CELL = 29;
    public static final int GRAY_SECTION = 30;

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

    private int colorKey1 = Theme.key_actionBarDefaultSubmenuBackground;
    private int colorKey2 = Theme.key_listSelector;
    private int colorKey3 = -1;
    private int itemsCount = 1;
    private final Theme.ResourcesProvider resourcesProvider;

    float[] randomParams;
    private Paint backgroundPaint;
    private int parentWidth;
    private int parentHeight;
    private float parentXOffset;

    FlickerLoadingView globalGradientView;
    private boolean ignoreHeightCheck;

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

    public void setColors(int key1, int key2, int key3) {
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
            if (itemsCount > 1 && ignoreHeightCheck) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) * itemsCount + getAdditionalHeight(), MeasureSpec.EXACTLY));
            } else if (itemsCount > 1 && MeasureSpec.getSize(heightMeasureSpec) > 0) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(heightMeasureSpec), getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) * itemsCount) + getAdditionalHeight(), MeasureSpec.EXACTLY));
            } else {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) + getAdditionalHeight(), MeasureSpec.EXACTLY));
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public int getAdditionalHeight() {
        return 0;
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
            if (colorKey3 >= 0) {
                headerPaint.setColor(getThemedColor(colorKey3));
            }
            canvas.drawRect(0,0, getMeasuredWidth(), AndroidUtilities.dp(32), colorKey3 >= 0 ? headerPaint : paint);
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
        } else if (getViewType() == TOPIC_CELL_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(14);
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(10) + r), h + AndroidUtilities.dp(10) + r, r, paint);

                canvas.save();
                canvas.translate(0, -AndroidUtilities.dp(4));
                rectF.set(AndroidUtilities.dp(50), h + AndroidUtilities.dp(16), AndroidUtilities.dp(148), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(50), h + AndroidUtilities.dp(38), AndroidUtilities.dp(268), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                if (SharedConfig.useThreeLinesLayout) {
                    rectF.set(AndroidUtilities.dp(50), h + AndroidUtilities.dp(46 + 8), AndroidUtilities.dp(220), h + AndroidUtilities.dp(46 + 8 + 8));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }

                if (showDate) {
                    rectF.set(getMeasuredWidth() - AndroidUtilities.dp(50), h + AndroidUtilities.dp(16), getMeasuredWidth() - AndroidUtilities.dp(12), h + AndroidUtilities.dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }
                canvas.restore();

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == CONTACT_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(25);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(9) + r), h + AndroidUtilities.dp(32), r, paint);

                int textStart = 76;
                int firstNameWidth = k % 2 == 0 ? 52 : 72;
                rectF.set(AndroidUtilities.dp(textStart), h + AndroidUtilities.dp(20), AndroidUtilities.dp(textStart + firstNameWidth), h + AndroidUtilities.dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(textStart + firstNameWidth + 8), h + AndroidUtilities.dp(20), AndroidUtilities.dp(textStart + firstNameWidth + 8 + 84), h + AndroidUtilities.dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(textStart), h + AndroidUtilities.dp(42), AndroidUtilities.dp(textStart + 64), h + AndroidUtilities.dp(50));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                canvas.drawLine(AndroidUtilities.dp(textStart), h + getCellHeight(getMeasuredWidth()), getMeasuredWidth(), h + getCellHeight(getMeasuredWidth()), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == STICKERS_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(20);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(9) + r), h + AndroidUtilities.dp(29), r, paint);

                int textStart = 76;
                int titleWidth = k % 2 == 0 ? 92 : 128;
                rectF.set(AndroidUtilities.dp(textStart), h + AndroidUtilities.dp(16), AndroidUtilities.dp(textStart + titleWidth), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(textStart), h + AndroidUtilities.dp(38), AndroidUtilities.dp(textStart + 164), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                canvas.drawLine(AndroidUtilities.dp(textStart), h + getCellHeight(getMeasuredWidth()), getMeasuredWidth(), h + getCellHeight(getMeasuredWidth()), paint);

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
        } else if (getViewType() == PHOTOS_TYPE || getViewType() == STORIES_TYPE) {
            int photoWidth = (getMeasuredWidth() - (AndroidUtilities.dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
            int photoHeight = getViewType() == STORIES_TYPE ? (int) (photoWidth * 1.25f) : photoWidth;
            int k = 0;
            while (h < getMeasuredHeight() || isSingleCell) {
                for (int i = 0; i < getColumnsCount(); i++) {
                    if (k == 0 && i < skipDrawItemsCount) {
                        continue;
                    }
                    int x = i * (photoWidth + AndroidUtilities.dp(2));
                    canvas.drawRect(x, h, x + photoWidth, h + photoHeight, paint);
                }
                h += photoHeight + AndroidUtilities.dp(2);
                k++;
                if (isSingleCell && k >= 2) {
                    break;
                }
            }
        } else if (getViewType() == FILES_TYPE) {
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
        } else if (getViewType() == AUDIO_TYPE) {
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
        } else if (getViewType() == LINKS_TYPE) {
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
        } else if (getViewType() == USERS_TYPE || getViewType() == USERS2_TYPE) {
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
        } else if (getViewType() == PROFILE_SEARCH_CELL) {
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

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == GRAY_SECTION) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int cellHeight = getCellHeight(getMeasuredWidth());

                rectF.set(0, h, getMeasuredWidth(), h + cellHeight);
                checkRtl(rectF);
                canvas.drawRect(rectF, paint);

                h += cellHeight;
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
        } else if (getViewType() == CHAT_THEMES_TYPE || getViewType() == QR_TYPE) {
            int x = AndroidUtilities.dp(12);
            int itemWidth = AndroidUtilities.dp(77);
            int INNER_RECT_SPACE = AndroidUtilities.dp(4);
            float BUBBLE_HEIGHT = AndroidUtilities.dp(21);
            float BUBBLE_WIDTH = AndroidUtilities.dp(41);

            while (x < getMeasuredWidth()) {

                if (backgroundPaint == null) {
                    backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

                AndroidUtilities.rectTmp.set(x + AndroidUtilities.dp(4), AndroidUtilities.dp(4), x + itemWidth - AndroidUtilities.dp(4), getMeasuredHeight() - AndroidUtilities.dp(4));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);

                if (getViewType() == CHAT_THEMES_TYPE) {
                    float bubbleTop = INNER_RECT_SPACE + AndroidUtilities.dp(8);
                    float bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(22);
                    rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);
                    bubbleLeft = INNER_RECT_SPACE + AndroidUtilities.dp(5);
                    bubbleTop += BUBBLE_HEIGHT + AndroidUtilities.dp(4);
                    rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);
                } else if (getViewType() == QR_TYPE) {
                    float radius = AndroidUtilities.dp(5);
                    float squareSize = AndroidUtilities.dp(32);
                    float left = x + (itemWidth - squareSize) / 2;
                    int top = AndroidUtilities.dp(21);
                    AndroidUtilities.rectTmp.set(left, top, left + squareSize, top + AndroidUtilities.dp(32));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, backgroundPaint);
                }


                canvas.drawCircle(x + itemWidth / 2, getMeasuredHeight() - AndroidUtilities.dp(20), AndroidUtilities.dp(8), backgroundPaint);
                x += itemWidth;
            }
        } else if (getViewType() == MEMBER_REQUESTS_TYPE) {
            int count = 0;
            int radius = AndroidUtilities.dp(23);
            int rectRadius = AndroidUtilities.dp(4);
            while (h <= getMeasuredHeight()) {
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(12) + radius), h + AndroidUtilities.dp(8) + radius, radius, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(74), h + AndroidUtilities.dp(12), paddingLeft + AndroidUtilities.dp(260), h + AndroidUtilities.dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(74), h + AndroidUtilities.dp(36), paddingLeft + AndroidUtilities.dp(140), h + AndroidUtilities.dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                if (memberRequestButtonWidth > 0) {
                    rectF.set(paddingLeft + AndroidUtilities.dp(73), h + AndroidUtilities.dp(62), paddingLeft + AndroidUtilities.dp(73) + memberRequestButtonWidth, h + AndroidUtilities.dp(62 + 32));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);
                }

                h += getCellHeight(getMeasuredWidth());
                count++;
                if (isSingleCell && count >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == REACTED_TYPE || getViewType() == REACTED_TYPE_WITH_EMOJI_HINT) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(18);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(8) + r), h + AndroidUtilities.dp(24), r, paint);

                rectF.set(paddingLeft + AndroidUtilities.dp(58), h + AndroidUtilities.dp(20), getWidth() - AndroidUtilities.dp(53), h + AndroidUtilities.dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

                if (k < 4) {
                    r = AndroidUtilities.dp(12);
                    canvas.drawCircle(checkRtl(getWidth() - AndroidUtilities.dp(12) - r), h + AndroidUtilities.dp(24), r, paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
            rectF.set(paddingLeft + AndroidUtilities.dp(8), h + AndroidUtilities.dp(20), getWidth() - AndroidUtilities.dp(8), h + AndroidUtilities.dp(28));
            checkRtl(rectF);
            canvas.drawRoundRect(rectF, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

            rectF.set(paddingLeft + AndroidUtilities.dp(8), h + AndroidUtilities.dp(36), getWidth() - AndroidUtilities.dp(53), h + AndroidUtilities.dp(44));
            checkRtl(rectF);
            canvas.drawRoundRect(rectF, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);

        } else if (viewType == LIMIT_REACHED_GROUPS) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(46) >> 1;
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(20) + r), h + (AndroidUtilities.dp(58) >> 1), r, paint);

                rectF.set(AndroidUtilities.dp(74), h + AndroidUtilities.dp(16), AndroidUtilities.dp(140), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(74), h + AndroidUtilities.dp(38), AndroidUtilities.dp(260), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == LIMIT_REACHED_LINKS) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(48) >> 1;
                canvas.drawCircle(checkRtl(AndroidUtilities.dp(20) + r), h + AndroidUtilities.dp(6) + r, r, paint);

                rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(16), AndroidUtilities.dp(140), h + AndroidUtilities.dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(38), AndroidUtilities.dp(260), h + AndroidUtilities.dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == DIALOG_CACHE_CONTROL) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(38) >> 1;
                canvas.drawCircle(AndroidUtilities.dp(17) + r, h + AndroidUtilities.dp(6) + r, r, paint);

                rectF.set(AndroidUtilities.dp(76), h + AndroidUtilities.dp(21), AndroidUtilities.dp(220), h + AndroidUtilities.dp(29));
               // checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == CHECKBOX_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(21) >> 1;
                canvas.drawCircle((LocaleController.isRTL ? getMeasuredWidth() - AndroidUtilities.dp(21) - r : AndroidUtilities.dp(21) + r), h + AndroidUtilities.dp(16) + r, r, paint);

                rectF.set(AndroidUtilities.dp(60), h + AndroidUtilities.dp(21), AndroidUtilities.dp(190), h + AndroidUtilities.dp(29));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                rectF.set(getMeasuredWidth() - AndroidUtilities.dp(16), h + AndroidUtilities.dp(21), getMeasuredWidth() - AndroidUtilities.dp(62), h + AndroidUtilities.dp(29));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == SOTRY_VIEWS_USER_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = AndroidUtilities.dp(24);
                canvas.drawCircle(checkRtl(paddingLeft + AndroidUtilities.dp(10) + r), h + (AndroidUtilities.dp(58) >> 1), r, paint);

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
        if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || getViewType() == CHAT_THEMES_TYPE || getViewType() == QR_TYPE) {
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
        if (gradient != null) {
            gradient.setLocalMatrix(matrix);
        }
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
            if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || viewType == CHAT_THEMES_TYPE || viewType == QR_TYPE) {
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
        switch (getViewType()) {
            case DIALOG_CELL_TYPE:
                return AndroidUtilities.dp((SharedConfig.useThreeLinesLayout ? 78 : 72) + 1);
            case TOPIC_CELL_TYPE:
                return AndroidUtilities.dp((SharedConfig.useThreeLinesLayout ? 76 : 64) + 1);
            case DIALOG_TYPE:
                return AndroidUtilities.dp(78) + 1;
            case PHOTOS_TYPE:
                int photoWidth = (width - (AndroidUtilities.dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
                return photoWidth + AndroidUtilities.dp(2);
            case FILES_TYPE:
            case AUDIO_TYPE:
                return AndroidUtilities.dp(56);
            case LINKS_TYPE:
                return AndroidUtilities.dp(80);
            case STICKERS_TYPE:
                return AndroidUtilities.dp(58);
            case USERS_TYPE:
            case CONTACT_TYPE:
                return AndroidUtilities.dp(64);
            case INVITE_LINKS_TYPE:
                return AndroidUtilities.dp(66);
            case USERS2_TYPE:
                return AndroidUtilities.dp(58);
            case CALL_LOG_TYPE:
                return AndroidUtilities.dp(61);
            case BOTS_MENU_TYPE:
                return AndroidUtilities.dp(36);
            case SHARE_ALERT_TYPE:
                return AndroidUtilities.dp(103);
            case MEMBER_REQUESTS_TYPE:
                return AndroidUtilities.dp(107);
            case REACTED_TYPE_WITH_EMOJI_HINT:
            case REACTED_TYPE:
                return AndroidUtilities.dp(ReactedUsersListView.ITEM_HEIGHT_DP);
            case LIMIT_REACHED_GROUPS:
                return AndroidUtilities.dp(58);
            case LIMIT_REACHED_LINKS:
                return AndroidUtilities.dp(60);
            case DIALOG_CACHE_CONTROL:
                return AndroidUtilities.dp(51);
            case CHECKBOX_TYPE:
                return AndroidUtilities.dp(50) + 1;
            case SOTRY_VIEWS_USER_TYPE:
                return AndroidUtilities.dp(58);
            case PROFILE_SEARCH_CELL:
                return AndroidUtilities.dp(60) + 1;
            case GRAY_SECTION:
                return AndroidUtilities.dp(32);
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

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
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

    public void setIgnoreHeightCheck(boolean ignore) {
        this.ignoreHeightCheck = ignore;
    }

    private float memberRequestButtonWidth;
    public void setMemberRequestButton(boolean isChannel) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        paint.setTextSize(AndroidUtilities.dp(14));
        memberRequestButtonWidth = AndroidUtilities.dp(17 + 17) + paint.measureText(isChannel ? LocaleController.getString("AddToChannel", R.string.AddToChannel) : LocaleController.getString("AddToGroup", R.string.AddToGroup));
    }
}
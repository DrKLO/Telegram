package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

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

public class FlickerLoadingView extends View implements Theme.Colorable {

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
    public static final int STAR_TIER = 31;
    public static final int BROWSER_BOOKMARK = 32;
    public static final int STAR_SUBSCRIPTION = 33;
    public static final int STAR_GIFT = 34;

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

        if (getViewType() == STAR_GIFT) {
            parentXOffset = -getX();
        }

        updateColors();
        updateGradient();

        int h = paddingTop;
        if (useHeaderOffset) {
            h += dp(32);
            if (colorKey3 >= 0) {
                headerPaint.setColor(getThemedColor(colorKey3));
            }
            canvas.drawRect(0,0, getMeasuredWidth(), dp(32), colorKey3 >= 0 ? headerPaint : paint);
        }
        if (getViewType() == DIALOG_CELL_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int childH = getCellHeight(getMeasuredWidth());
                int r = dp(28);
                canvas.drawCircle(checkRtl(dp(10) + r), h + (childH >> 1), r, paint);

                rectF.set(dp(76), h + dp(16), dp(148), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(76), h + dp(38), dp(268), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (SharedConfig.useThreeLinesLayout) {
                    rectF.set(dp(76), h + dp(46 + 8), dp(220), h + dp(46 + 8 + 8));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
                }

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(16), getMeasuredWidth() - dp(12), h + dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int r = dp(14);
                canvas.drawCircle(checkRtl(dp(10) + r), h + dp(10) + r, r, paint);

                canvas.save();
                canvas.translate(0, -dp(4));
                rectF.set(dp(50), h + dp(16), dp(148), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(50), h + dp(38), dp(268), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (SharedConfig.useThreeLinesLayout) {
                    rectF.set(dp(50), h + dp(46 + 8), dp(220), h + dp(46 + 8 + 8));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
                }

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(16), getMeasuredWidth() - dp(12), h + dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int r = dp(25);
                canvas.drawCircle(checkRtl(paddingLeft + dp(9) + r), h + dp(32), r, paint);

                int textStart = 76;
                int firstNameWidth = k % 2 == 0 ? 52 : 72;
                rectF.set(dp(textStart), h + dp(20), dp(textStart + firstNameWidth), h + dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(textStart + firstNameWidth + 8), h + dp(20), dp(textStart + firstNameWidth + 8 + 84), h + dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(textStart), h + dp(42), dp(textStart + 64), h + dp(50));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                canvas.drawLine(dp(textStart), h + getCellHeight(getMeasuredWidth()), getMeasuredWidth(), h + getCellHeight(getMeasuredWidth()), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == STICKERS_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(20);
                canvas.drawCircle(checkRtl(paddingLeft + dp(9) + r), h + dp(29), r, paint);

                int textStart = 76;
                int titleWidth = k % 2 == 0 ? 92 : 128;
                rectF.set(dp(textStart), h + dp(16), dp(textStart + titleWidth), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(textStart), h + dp(38), dp(textStart + 164), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                canvas.drawLine(dp(textStart), h + getCellHeight(getMeasuredWidth()), getMeasuredWidth(), h + getCellHeight(getMeasuredWidth()), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == DIALOG_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(25);
                canvas.drawCircle(checkRtl(dp(9) + r), h + (dp(78) >> 1), r, paint);

                rectF.set(dp(68), h + dp(20), dp(140), h + dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(42), dp(260), h + dp(50));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(20), getMeasuredWidth() - dp(12), h + dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == PHOTOS_TYPE || getViewType() == STORIES_TYPE) {
            int photoWidth = (getMeasuredWidth() - (dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
            int photoHeight = getViewType() == STORIES_TYPE ? (int) (photoWidth * 1.25f) : photoWidth;
            int k = 0;
            while (h < getMeasuredHeight() || isSingleCell) {
                for (int i = 0; i < getColumnsCount(); i++) {
                    if (k == 0 && i < skipDrawItemsCount) {
                        continue;
                    }
                    int x = i * (photoWidth + dp(2));
                    canvas.drawRect(x, h, x + photoWidth, h + photoHeight, paint);
                }
                h += photoHeight + dp(2);
                k++;
                if (isSingleCell && k >= 2) {
                    break;
                }
            }
        } else if (getViewType() == FILES_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                rectF.set(dp(12), h + dp(8), dp(52), h + dp(48));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(12), dp(140), h + dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(34), dp(260), h + dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(12), getMeasuredWidth() - dp(12), h + dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int radius = dp(44) >> 1;
                canvas.drawCircle(checkRtl(dp(12) + radius), h + dp(6) + radius, radius, paint);

                rectF.set(dp(68), h + dp(12), dp(140), h + dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(34), dp(260), h + dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(12), getMeasuredWidth() - dp(12), h + dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                rectF.set(dp(10), h + dp(11), dp(62), h + dp(11 + 52));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(12), dp(140), h + dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(34), dp(268), h + dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(68), h + dp(34 + 20), dp(120 + 68), h + dp(42 + 20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(12), getMeasuredWidth() - dp(12), h + dp(20));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int r = dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + dp(9) + r), h + (dp(64) >> 1), r, paint);

                rectF.set(paddingLeft + dp(68), h + dp(17), paddingLeft + dp(260), h + dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(68), h + dp(39), paddingLeft + dp(140), h + dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(20), getMeasuredWidth() - dp(12), h + dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int r = dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + dp(9) + r), h + (dp(64) >> 1), r, paint);

                rectF.set(paddingLeft + dp(68), h + dp(17), paddingLeft + dp(260), h + dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(68), h + dp(39), paddingLeft + dp(140), h + dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == STAR_SUBSCRIPTION) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + dp(13) + r), h + (dp(58) >> 1), r, paint);

                rectF.set(paddingLeft + dp(13+46+13), h + dp(17), paddingLeft + dp(260), h + dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(13+46+13), h + dp(39), paddingLeft + dp(140), h + dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

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
                int r = dp(23);
                canvas.drawCircle(checkRtl(paddingLeft + dp(11) + r), h + (dp(64) >> 1), r, paint);

                rectF.set(paddingLeft + dp(68), h + dp(17), paddingLeft + dp(140), h + dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(68), h + dp(39), paddingLeft + dp(260), h + dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(20), getMeasuredWidth() - dp(12), h + dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                int r = dp(32) / 2;
                canvas.drawCircle(checkRtl(dp(35)), h + (childH >> 1), r, paint);

                rectF.set(dp(72), h + dp(16), dp(268), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(72), h + dp(38), dp(140), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(16), getMeasuredWidth() - dp(12), h + dp(24));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
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
                rectF.set(dp(18), dp((36 - 8) / 2f), getMeasuredWidth() * 0.5f + dp(40 * randomParams[0]), dp((36 - 8) / 2f) + dp(8));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(getMeasuredWidth() - dp(18), dp((36 - 8) / 2f), getMeasuredWidth() - getMeasuredWidth() * 0.2f - dp(20 * randomParams[0]), dp((36 - 8) / 2f) + dp(8));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

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
            h += dp(14);
            while (h <= getMeasuredHeight()) {
                int part = getMeasuredWidth() / 4;
                for (int i = 0; i < 4; i++) {
                    float cx = part * i + part / 2f;
                    float cy = h + dp(7) + dp(56) / 2f;
                    canvas.drawCircle(cx, cy, dp(56 / 2f), paint);

                    float y = h + dp(7) + dp(56) + dp(16);
                    AndroidUtilities.rectTmp.set(cx - dp(24), y - dp(4), cx + dp(24), y + dp(4));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), paint);
                }
                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell) {
                    break;
                }
            }
        } else if (getViewType() == MESSAGE_SEEN_TYPE) {
            float cy = getMeasuredHeight() / 2f;

            AndroidUtilities.rectTmp.set(dp(40), cy - dp(4), getMeasuredWidth() - dp(120), cy + dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(4), dp(4), paint);

            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
            }

            for (int i = 0; i < 3; i++) {
                canvas.drawCircle(getMeasuredWidth() - dp(8 + 24 + 12 + 12) + dp(13) + dp(12) * i, cy, dp(13f), backgroundPaint);
                canvas.drawCircle(getMeasuredWidth() - dp(8 + 24 + 12 + 12) + dp(13) + dp(12) * i, cy, dp(12f), paint);
            }
        } else if (getViewType() == CHAT_THEMES_TYPE || getViewType() == QR_TYPE) {
            int x = dp(12);
            int itemWidth = dp(77);
            int INNER_RECT_SPACE = dp(4);
            float BUBBLE_HEIGHT = dp(21);
            float BUBBLE_WIDTH = dp(41);

            while (x < getMeasuredWidth()) {

                if (backgroundPaint == null) {
                    backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

                AndroidUtilities.rectTmp.set(x + dp(4), dp(4), x + itemWidth - dp(4), getMeasuredHeight() - dp(4));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), paint);

                if (getViewType() == CHAT_THEMES_TYPE) {
                    float bubbleTop = INNER_RECT_SPACE + dp(8);
                    float bubbleLeft = INNER_RECT_SPACE + dp(22);
                    rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);
                    bubbleLeft = INNER_RECT_SPACE + dp(5);
                    bubbleTop += BUBBLE_HEIGHT + dp(4);
                    rectF.set(x + bubbleLeft, bubbleTop, x + bubbleLeft + BUBBLE_WIDTH, bubbleTop + BUBBLE_HEIGHT);
                    canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint);
                } else if (getViewType() == QR_TYPE) {
                    float radius = dp(5);
                    float squareSize = dp(32);
                    float left = x + (itemWidth - squareSize) / 2;
                    int top = dp(21);
                    AndroidUtilities.rectTmp.set(left, top, left + squareSize, top + dp(32));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, backgroundPaint);
                }


                canvas.drawCircle(x + itemWidth / 2, getMeasuredHeight() - dp(20), dp(8), backgroundPaint);
                x += itemWidth;
            }
        } else if (getViewType() == MEMBER_REQUESTS_TYPE) {
            int count = 0;
            int radius = dp(23);
            int rectRadius = dp(4);
            while (h <= getMeasuredHeight()) {
                canvas.drawCircle(checkRtl(paddingLeft + dp(12) + radius), h + dp(8) + radius, radius, paint);

                rectF.set(paddingLeft + dp(74), h + dp(12), paddingLeft + dp(260), h + dp(20));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                rectF.set(paddingLeft + dp(74), h + dp(36), paddingLeft + dp(140), h + dp(42));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, rectRadius, rectRadius, paint);

                if (memberRequestButtonWidth > 0) {
                    rectF.set(paddingLeft + dp(73), h + dp(62), paddingLeft + dp(73) + memberRequestButtonWidth, h + dp(62 + 32));
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
                int r = dp(18);
                canvas.drawCircle(checkRtl(paddingLeft + dp(8) + r), h + dp(24), r, paint);

                rectF.set(paddingLeft + dp(58), h + dp(20), getWidth() - dp(53), h + dp(28));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(8), dp(8), paint);

                if (k < 4) {
                    r = dp(12);
                    canvas.drawCircle(checkRtl(getWidth() - dp(12) - r), h + dp(24), r, paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
            rectF.set(paddingLeft + dp(8), h + dp(20), getWidth() - dp(8), h + dp(28));
            checkRtl(rectF);
            canvas.drawRoundRect(rectF, dp(8), dp(8), paint);

            rectF.set(paddingLeft + dp(8), h + dp(36), getWidth() - dp(53), h + dp(44));
            checkRtl(rectF);
            canvas.drawRoundRect(rectF, dp(8), dp(8), paint);

        } else if (viewType == LIMIT_REACHED_GROUPS) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(46) >> 1;
                canvas.drawCircle(checkRtl(dp(20) + r), h + (dp(58) >> 1), r, paint);

                rectF.set(dp(74), h + dp(16), dp(140), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(74), h + dp(38), dp(260), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == LIMIT_REACHED_LINKS) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(48) >> 1;
                canvas.drawCircle(checkRtl(dp(20) + r), h + dp(6) + r, r, paint);

                rectF.set(dp(76), h + dp(16), dp(140), h + dp(24));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(dp(76), h + dp(38), dp(260), h + dp(46));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == DIALOG_CACHE_CONTROL) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(38) >> 1;
                canvas.drawCircle(dp(17) + r, h + dp(6) + r, r, paint);

                rectF.set(dp(76), h + dp(21), dp(220), h + dp(29));
               // checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (viewType == CHECKBOX_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(21) >> 1;
                canvas.drawCircle((LocaleController.isRTL ? getMeasuredWidth() - dp(21) - r : dp(21) + r), h + dp(16) + r, r, paint);

                rectF.set(dp(60), h + dp(21), dp(190), h + dp(29));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(getMeasuredWidth() - dp(16), h + dp(21), getMeasuredWidth() - dp(62), h + dp(29));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == SOTRY_VIEWS_USER_TYPE) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int r = dp(24);
                canvas.drawCircle(checkRtl(paddingLeft + dp(10) + r), h + (dp(58) >> 1), r, paint);

                rectF.set(paddingLeft + dp(68), h + dp(17), paddingLeft + dp(260), h + dp(25));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(68), h + dp(39), paddingLeft + dp(140), h + dp(47));
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                if (showDate) {
                    rectF.set(getMeasuredWidth() - dp(50), h + dp(20), getMeasuredWidth() - dp(12), h + dp(28));
                    checkRtl(rectF);
                    canvas.drawRoundRect(rectF, dp(4), dp(4), paint);
                }

                h += getCellHeight(getMeasuredWidth());
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == STAR_TIER) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int cellHeight = getCellHeight(getMeasuredWidth());

                rectF.set(paddingLeft + dp(18), h + (cellHeight - dp(22)) / 2f, paddingLeft + dp(40), h + (cellHeight + dp(22)) / 2f);
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(11), dp(11), paint);

                rectF.set(paddingLeft + dp(58), h + (cellHeight - dp(8)) / 2f, Math.min(paddingLeft + dp(58 + 74), getMeasuredWidth() - dp(19)), h + (cellHeight + dp(8)) / 2f);
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += cellHeight;
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == BROWSER_BOOKMARK) {
            int k = 0;
            while (h <= getMeasuredHeight()) {
                int cellHeight = getCellHeight(getMeasuredWidth());

                rectF.set(paddingLeft + dp(10), h + (cellHeight - dp(32)) / 2f, paddingLeft + dp(10 + 32), h + (cellHeight + dp(32)) / 2f);
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(6), dp(6), paint);

                rectF.set(paddingLeft + dp(64), h + (cellHeight - dp(14) - dp(10)) / 2f, Math.min(paddingLeft + dp(64 + 54), getMeasuredWidth() - dp(19)), h + (cellHeight - dp(14) + dp(10)) / 2f);
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                rectF.set(paddingLeft + dp(64), h + (cellHeight + dp(14) - dp(8)) / 2f, Math.min(paddingLeft + dp(64 + 80), getMeasuredWidth() - dp(19)), h + (cellHeight + dp(14) + dp(8)) / 2f);
                checkRtl(rectF);
                canvas.drawRoundRect(rectF, dp(4), dp(4), paint);

                h += cellHeight;
                k++;
                if (isSingleCell && k >= itemsCount) {
                    break;
                }
            }
        } else if (getViewType() == STAR_GIFT) {
            rectF.set(paddingLeft, paddingTop, getMeasuredWidth() - paddingLeft, getMeasuredHeight() - paddingTop);
            rectF.inset(dp(3.33f), dp(4));
            canvas.drawRoundRect(rectF, dp(11), dp(11), paint);
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
        if (viewType == STAR_GIFT) {
            width = Math.max(width, AndroidUtilities.displaySize.x);
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

    @Override
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
            if (viewType == STAR_GIFT) {
                gradientWidth = AndroidUtilities.displaySize.x;
            } else if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || viewType == CHAT_THEMES_TYPE || viewType == QR_TYPE) {
                gradientWidth = dp(200);
            } else {
                gradientWidth = dp(600);
            }
            if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || viewType == CHAT_THEMES_TYPE || viewType == QR_TYPE) {
                gradient = new LinearGradient(0, 0, gradientWidth, 0, new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            } else {
                gradient = new LinearGradient(0, 0, 0, gradientWidth, new int[]{color1, color0, color0, color1}, new float[]{0.0f, 0.4f, 0.6f, 1f}, Shader.TileMode.CLAMP);
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
                return dp((SharedConfig.useThreeLinesLayout ? 78 : 72) + 1);
            case TOPIC_CELL_TYPE:
                return dp((SharedConfig.useThreeLinesLayout ? 76 : 64) + 1);
            case DIALOG_TYPE:
                return dp(78) + 1;
            case PHOTOS_TYPE:
                int photoWidth = (width - (dp(2) * (getColumnsCount() - 1))) / getColumnsCount();
                return photoWidth + dp(2);
            case FILES_TYPE:
            case AUDIO_TYPE:
                return dp(56);
            case LINKS_TYPE:
                return dp(80);
            case STICKERS_TYPE:
                return dp(58);
            case USERS_TYPE:
            case CONTACT_TYPE:
                return dp(64);
            case INVITE_LINKS_TYPE:
                return dp(66);
            case USERS2_TYPE:
                return dp(58);
            case CALL_LOG_TYPE:
                return dp(61);
            case BOTS_MENU_TYPE:
                return dp(36);
            case SHARE_ALERT_TYPE:
                return dp(103);
            case MEMBER_REQUESTS_TYPE:
                return dp(107);
            case REACTED_TYPE_WITH_EMOJI_HINT:
            case REACTED_TYPE:
                return dp(ReactedUsersListView.ITEM_HEIGHT_DP);
            case LIMIT_REACHED_GROUPS:
                return dp(58);
            case LIMIT_REACHED_LINKS:
                return dp(60);
            case DIALOG_CACHE_CONTROL:
                return dp(51);
            case CHECKBOX_TYPE:
                return dp(50) + 1;
            case SOTRY_VIEWS_USER_TYPE:
                return dp(58);
            case PROFILE_SEARCH_CELL:
                return dp(60) + 1;
            case STAR_SUBSCRIPTION:
                return dp(58);
            case GRAY_SECTION:
                return dp(32);
            case STAR_TIER:
                return dp(48) + 1;
            case BROWSER_BOOKMARK:
                return dp(56) + 1;
            case STAR_GIFT:
                return dp(140);
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
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(dp(14));
        memberRequestButtonWidth = dp(17 + 17) + paint.measureText(isChannel ? LocaleController.getString(R.string.AddToChannel) : LocaleController.getString(R.string.AddToGroup));
    }
}
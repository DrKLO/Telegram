package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.StaticLayout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.source.dash.manifest.Period;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ClickableAnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;

public class ProfileHoursCell extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private TextView textView;
    private TextView labelTimeText[] = new TextView[2];
    private ImageView arrowView;
    private FrameLayout todayTimeContainer;
    private final ViewGroup[] lines = new ViewGroup[7];
    private final TextView[] labelText = new TextView[7];
    private final TextView[][] timeText = new TextView[7][];
    private ClickableAnimatedTextView switchText;
    private FrameLayout todayTimeTextContainer;
    private LinearLayout todayTimeTextContainer2;
    private int todayLinesCount = 1;
    private int todayLinesHeight = 0;

    public ProfileHoursCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);
        setClipChildren(false);

        for (int i = 0; i < 7; ++i) {
            if (i == 0) {
                FrameLayout line = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(MeasureSpec.getSize(heightMeasureSpec), dp(60)), MeasureSpec.getMode(heightMeasureSpec)));
                    }
                };
                line.setMinimumHeight(dp(60));

                textView = new TextView(context);
                textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                line.addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 0, 9.33f, 0, 0));

                labelText[i] = new TextView(context);
                labelText[i].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                labelText[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                labelText[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                line.addView(labelText[i], LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 0, 33, 0, 10));

                todayTimeTextContainer2 = new LinearLayout(context);
                todayTimeTextContainer2.setOrientation(VERTICAL);

                todayTimeTextContainer = new FrameLayout(context);
                timeText[i] = new TextView[2];
                for (int a = 0; a < 2; ++a) {
                    timeText[i][a] = new TextView(context);
                    timeText[i][a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    timeText[i][a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                    timeText[i][a].setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                    todayTimeTextContainer.addView(timeText[i][a], LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 20, 0));
                }

                for (int a = 0; a < 2; ++a) {
                    labelTimeText[a] = new TextView(context);
                    labelTimeText[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    labelTimeText[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                    labelTimeText[a].setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                    todayTimeTextContainer.addView(labelTimeText[a], LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 20, 0));
                }

                arrowView = new ImageView(context);
                arrowView.setScaleType(ImageView.ScaleType.CENTER);
                arrowView.setScaleX(0.6f);
                arrowView.setScaleY(0.6f);
                arrowView.setImageResource(R.drawable.arrow_more);
                arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), PorterDuff.Mode.SRC_IN));
                todayTimeTextContainer.addView(arrowView, LayoutHelper.createFrameRelatively(20, 20, Gravity.CENTER_VERTICAL | Gravity.END));

                todayTimeTextContainer2.addView(todayTimeTextContainer, LayoutHelper.createLinearRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                switchText = new ClickableAnimatedTextView(context);
                switchText.getDrawable().updateAll = true;
                switchText.setTextSize(dp(13));
                switchText.setPadding(dp(6), 0, dp(6), 0);
                switchText.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                switchText.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)), .10f), Theme.multAlpha(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)), .22f)));
                switchText.setTextColor(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)));
                switchText.getDrawable().setScaleProperty(.6f);
                switchText.setVisibility(View.GONE);
                todayTimeTextContainer2.addView(switchText, LayoutHelper.createLinearRelatively(LayoutHelper.MATCH_PARENT, 17, Gravity.END, 0, 4, 20 - 2, 0));

                todayTimeContainer = new FrameLayout(context);
                todayTimeContainer.addView(todayTimeTextContainer2, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM, 0, 0, 0, 0));
                line.addView(todayTimeContainer, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM, 0, 0, 0, 12));

                addView(lines[i] = line, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 22, 0, 33 - 20, 0));
            } else {
                LinearLayout line = new LinearLayout(context);
                line.setOrientation(HORIZONTAL);

                labelText[i] = new TextView(context);
                labelText[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                labelText[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                labelText[i].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

                FrameLayout timeTextContainer = new FrameLayout(context);
                timeText[i] = new TextView[2];
                for (int a = 0; a < 2; ++a) {
                    timeText[i][a] = new TextView(context);
                    timeText[i][a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    timeText[i][a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                    timeText[i][a].setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                    timeTextContainer.addView(timeText[i][a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
                }

                if (LocaleController.isRTL) {
                    line.addView(timeTextContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
                    line.addView(labelText[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
                } else {
                    line.addView(labelText[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
                    line.addView(timeTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
                }

                addView(lines[i] = line, LayoutHelper.createLinearRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 22, i == 1 ? 1f : 11.66f, 33, i == 6 ? 16.66f : 0));
            }
        }

        setWillNotDraw(false);
    }

    protected int processColor(int color) {
        return color;
    }

    public void updateColors() {
        switchText.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), Theme.multAlpha(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)), .10f), Theme.multAlpha(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)), .22f)));
        switchText.setTextColor(processColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider)));
    }

    public void setOnTimezoneSwitchClick(View.OnClickListener onClickListener) {
        if (switchText != null) {
            switchText.setOnClickListener(onClickListener);
        }
    }

    private boolean firstAfterAttach = true;
    private boolean needDivider;
    private boolean expanded;
    public void set(TLRPC.TL_businessWorkHours value, boolean expanded, boolean showInMyTimezone, boolean divider) {
        this.expanded = expanded;
        this.needDivider = divider;

        if (value == null) return;

        final boolean is24x7 = OpeningHoursActivity.is24x7(value);
        if (is24x7) {
            this.expanded = expanded = false;
        }
        arrowView.setVisibility(is24x7 ? View.GONE : View.VISIBLE);
        todayTimeTextContainer2.setTranslationX(is24x7 ? dp(11) : 0);

        TimezonesController timezonesController = TimezonesController.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_timezone timezone = timezonesController.findTimezone(value.timezone_id);

        Calendar calendar = Calendar.getInstance();
        int currentUtcOffset = calendar.getTimeZone().getOffset(System.currentTimeMillis()) / 1000;
        int valueUtcOffset = timezone == null ? 0 : timezone.utc_offset;
        int utcOffset = (currentUtcOffset - valueUtcOffset) / 60;
        switchText.setVisibility(utcOffset != 0 && !is24x7 ? View.VISIBLE : View.GONE);
        if (utcOffset == 0) showInMyTimezone = false;

        invalidate();

        if (firstAfterAttach) {
            labelTimeText[0].setAlpha(!expanded && !showInMyTimezone ? 1f : 0f);
            labelTimeText[1].setAlpha(!expanded && showInMyTimezone ? 1f : 0f);
            arrowView.setRotation(expanded ? 180 : 0);
        } else {
            labelTimeText[0].animate().alpha(!expanded && !showInMyTimezone ? 1f : 0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            labelTimeText[1].animate().alpha(!expanded && showInMyTimezone ? 1f : 0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            timeText[0][0].animate().alpha(expanded ? 1f : 0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            timeText[0][1].animate().alpha(expanded ? 1f : 0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            arrowView.animate().rotation(expanded ? 180 : 0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
        for (int i = 0; i < timeText.length; ++i) {
            for (int a = 0; a < timeText[i].length; ++a) {
                float alpha = ((i == 0 ? expanded : true) && (a == 1) == showInMyTimezone) ? 1f : 0f;
                if (firstAfterAttach) {
                    timeText[i][a].setAlpha(alpha);
                } else {
                    timeText[i][a].animate().alpha(alpha).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                }
            }
        }
        if (switchText != null) {
            switchText.setText(getString(showInMyTimezone ? R.string.BusinessHoursProfileSwitchMy : R.string.BusinessHoursProfileSwitchLocal), !LocaleController.isRTL && !firstAfterAttach);
        }
        firstAfterAttach = false;

        ArrayList<TLRPC.TL_businessWeeklyOpen> weekly_open = new ArrayList<>(value.weekly_open);
        ArrayList<OpeningHoursActivity.Period>[] localDays = OpeningHoursActivity.getDaysHours(weekly_open);

        int nowWeekday = (7 + calendar.get(Calendar.DAY_OF_WEEK) - 2) % 7;
        int nowHours = calendar.get(Calendar.HOUR_OF_DAY);
        int nowMinutes = calendar.get(Calendar.MINUTE);

        ArrayList<TLRPC.TL_businessWeeklyOpen> adapted_weekly_open = OpeningHoursActivity.adaptWeeklyOpen(value.weekly_open, utcOffset);
        boolean open_now = false;
        int nowPeriodTime = nowMinutes + nowHours * 60 + nowWeekday * (24 * 60);
        for (int i = 0; i < adapted_weekly_open.size(); ++i) {
            TLRPC.TL_businessWeeklyOpen weeklyPeriod = adapted_weekly_open.get(i);
            if (
                nowPeriodTime >= weeklyPeriod.start_minute && nowPeriodTime <= weeklyPeriod.end_minute ||
                nowPeriodTime + (7 * 24 * 60) >= weeklyPeriod.start_minute && nowPeriodTime + (7 * 24 * 60) <= weeklyPeriod.end_minute ||
                nowPeriodTime - (7 * 24 * 60) >= weeklyPeriod.start_minute && nowPeriodTime - (7 * 24 * 60) <= weeklyPeriod.end_minute
            ) {
                open_now = true;
                break;
            }
        }
        ArrayList<OpeningHoursActivity.Period>[] myDays = OpeningHoursActivity.getDaysHours(adapted_weekly_open);

        textView.setText(getString(open_now ? R.string.BusinessHoursProfileNowOpen : R.string.BusinessHoursProfileNowClosed));
        textView.setTextColor(Theme.getColor(open_now ? Theme.key_avatar_nameInMessageGreen : Theme.key_text_RedRegular, resourcesProvider));

        int lastTodayLinesHeight = todayLinesHeight;
        int lastTodayLinesCount = todayLinesCount;
        todayLinesCount = 1;
        todayLinesHeight = 0;
        for (int a = 0; a < 2; ++a) {
            ArrayList<OpeningHoursActivity.Period>[] days = a == 0 ? localDays : myDays;
            for (int i = 0; i < 7; ++i) {
                int weekday = (nowWeekday + i) % 7;
                if (i == 0) {
                    labelText[i].setText(getString(R.string.BusinessHoursProfile));
                } else {
                    String dayName = DayOfWeek.values()[weekday].getDisplayName(TextStyle.FULL, LocaleController.getInstance().getCurrentLocale());
                    dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1);
                    labelText[i].setText(dayName);

                    timeText[i][0].setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
                    timeText[i][1].setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
                    labelText[i].setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
                }
                for (int k = 0; k < (i == 0 ? 2 : 1); ++k) {
                    TextView textView = k == 0 ? timeText[i][a] : labelTimeText[a];
                    if (i == 0 && !open_now && k == 1) {
                        int opensPeriodTime = -1;
                        for (int j = 0; j < adapted_weekly_open.size(); ++j) {
                            TLRPC.TL_businessWeeklyOpen weekly = adapted_weekly_open.get(j);
                            if (nowPeriodTime < weekly.start_minute) {
                                opensPeriodTime = weekly.start_minute;
                                break;
                            }
                        }
                        if (opensPeriodTime == -1 && !adapted_weekly_open.isEmpty()) {
                            opensPeriodTime = adapted_weekly_open.get(0).start_minute;
                        }
                        if (opensPeriodTime == -1) {
                            textView.setText(getString(R.string.BusinessHoursProfileClose));
                        } else {
                            int diff = opensPeriodTime < nowPeriodTime ? opensPeriodTime + (7 * 24 * 60 - nowPeriodTime) : opensPeriodTime - nowPeriodTime;
                            if (diff < 60) {
                                textView.setText(formatPluralString("BusinessHoursProfileOpensInMinutes", diff));
                            } else if (diff < 24 * 60) {
                                textView.setText(formatPluralString("BusinessHoursProfileOpensInHours", (int) Math.ceil(diff / 60f)));
                            } else {
                                textView.setText(formatPluralString("BusinessHoursProfileOpensInDays", (int) Math.ceil(diff / 60f / 24f)));
                            }
                        }
                    } else {
                        if (is24x7) {
                            textView.setText(getString(R.string.BusinessHoursProfileFullOpen));
                        } else if (days[weekday].isEmpty()) {
                            textView.setText(getString(R.string.BusinessHoursProfileClose));
                        } else if (OpeningHoursActivity.isFull(days[weekday])) {
                            textView.setText(getString(R.string.BusinessHoursProfileOpen));
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (int j = 0; j < days[weekday].size(); ++j) {
                                if (j > 0) sb.append("\n");
                                sb.append(days[weekday].get(j));
                            }
                            int linesCount = days[weekday].size();
                            textView.setText(sb);
                            if (i == 0) {
                                todayLinesCount = Math.max(todayLinesCount, linesCount);
                                todayLinesHeight = Math.max(todayLinesHeight, textView.getLineHeight() * linesCount);
                            }
                        }
                    }
                }
            }
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) todayTimeContainer.getLayoutParams();
        lp.topMargin = dp(todayLinesCount > 2 || switchText.getVisibility() == View.VISIBLE ? 6 : 12);
        lp.bottomMargin = dp(todayLinesCount > 2 || switchText.getVisibility() == View.VISIBLE ? 6 : 12);
        lp.gravity = (todayLinesCount > 2 || switchText.getVisibility() == View.VISIBLE ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM) | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        if (lastTodayLinesCount != todayLinesCount || lastTodayLinesHeight != todayLinesHeight) {
            requestLayout();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (needDivider) {
            Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
            canvas.drawRect(dp(LocaleController.isRTL ? 0 : 21.33f), getMeasuredHeight() - 1, getWidth() - dp(LocaleController.isRTL ? 21.33f : 0), getMeasuredHeight(), dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            expanded ? heightMeasureSpec : MeasureSpec.makeMeasureSpec(Math.max(dp(60), todayLinesCount > 2 || switchText.getVisibility() == View.VISIBLE ? todayLinesHeight + dp(12 + 3) + dp(switchText.getVisibility() == View.VISIBLE ? 21 : 0) : 0) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (switchText != null && switchText.getVisibility() == View.VISIBLE) {
            final float x = event.getX() - lines[0].getX() - todayTimeContainer.getX() - todayTimeTextContainer.getX() - switchText.getX();
            final float y = event.getY() - lines[0].getY() - todayTimeContainer.getY() - todayTimeTextContainer.getY() - switchText.getY();
            return switchText.getClickBounds().contains((int) x, (int) y);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }
}

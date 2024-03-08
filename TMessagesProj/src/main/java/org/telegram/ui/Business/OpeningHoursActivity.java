package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

public class OpeningHoursActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int PERIODS_COUNT_LIMIT = 28;

    private UniversalRecyclerView listView;

    private static final int done_button = 1;
    private CrossfadeDrawable doneButtonDrawable;
    private ActionBarMenuItem doneButton;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.BusinessHours));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
        checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
        doneButton = actionBar.createMenu().addItemWithWidth(done_button, doneButtonDrawable, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        checkDone(false);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(context, currentAccount, this::fillItems, this::onClick, null, getResourceProvider());
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setValue();

        return fragmentView = contentView;
    }

    public boolean hasChanges() {
        if ((currentValue != null) != enabled) {
            return true;
        }
        if (!TextUtils.equals(currentTimezoneId, timezoneId)) {
            return true;
        }
        if (currentValue != null && enabled) {
            if (value == null) return true;
            for (int i = 0; i < currentValue.length; ++i) {
                if (currentValue[i].size() != value[i].size())
                    return true;
                for (int j = 0; j < value[i].size(); ++j) {
                    Period a = currentValue[i].get(j);
                    Period b = value[i].get(j);
                    if (a.start != b.start || a.end != b.end) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkDone(boolean animated) {
        if (doneButton == null) return;
        final boolean hasChanges = hasChanges();
        doneButton.setEnabled(hasChanges);
        if (animated) {
            doneButton.animate().alpha(hasChanges ? 1.0f : 0.0f).scaleX(hasChanges ? 1.0f : 0.0f).scaleY(hasChanges ? 1.0f : 0.0f).setDuration(180).start();
        } else {
            doneButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleX(hasChanges ? 1.0f : 0.0f);
            doneButton.setScaleY(hasChanges ? 1.0f : 0.0f);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        TimezonesController.getInstance(currentAccount).load();
        timezoneId = TimezonesController.getInstance(currentAccount).getSystemTimezoneId();
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        super.onFragmentDestroy();
        processDone();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userInfoDidLoad) {
            setValue();
        } else if (id == NotificationCenter.timezonesUpdated) {
            if (currentValue == null) {
                timezoneId = TimezonesController.getInstance(currentAccount).getSystemTimezoneId();
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }
    }

    private boolean valueSet;
    private void setValue() {
        if (valueSet) return;

        final long selfId = getUserConfig().getClientUserId();
        TLRPC.UserFull userFull = getMessagesController().getUserFull(selfId);
        if (userFull == null) {
            getMessagesController().loadUserInfo(getUserConfig().getCurrentUser(), true, getClassGuid());
            return;
        }

        if (enabled = userFull.business_work_hours != null) {
            currentTimezoneId = timezoneId = userFull.business_work_hours.timezone_id;
            currentValue = getDaysHours(userFull.business_work_hours.weekly_open);
            value = getDaysHours(userFull.business_work_hours.weekly_open);
        } else {
            currentTimezoneId = timezoneId = TimezonesController.getInstance(currentAccount).getSystemTimezoneId();
            currentValue = null;
            value = new ArrayList[7];
            for (int i = 0; i < value.length; ++i) {
                value[i] = new ArrayList<>();
            }
        }

        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        checkDone(false);

        valueSet = true;
    }

    public static ArrayList<TLRPC.TL_businessWeeklyOpen> adaptWeeklyOpen(ArrayList<TLRPC.TL_businessWeeklyOpen> hours, int utc_offset) {
        ArrayList<TLRPC.TL_businessWeeklyOpen> array = new ArrayList<>(hours);

//        // join together weeklies
//        Collections.sort(array, (a, b) -> a.start_minute - b.start_minute);
//        for (int i = 0; i < array.size() - 1; ++i) {
//            if (i + 1 >= array.size()) continue;
//            TLRPC.TL_businessWeeklyOpen weekly = array.get(i);
//            TLRPC.TL_businessWeeklyOpen nextWeekly = array.get(i + 1);
//            if (weekly.end_minute + 1 >= nextWeekly.start_minute) {
//                TLRPC.TL_businessWeeklyOpen newWeekly = new TLRPC.TL_businessWeeklyOpen();
//                newWeekly.start_minute = weekly.start_minute;
//                newWeekly.end_minute = nextWeekly.end_minute;
//                array.set(i, newWeekly);
//                array.remove(i + 1);
//                i--;
//            }
//        }

        ArrayList<TLRPC.TL_businessWeeklyOpen> array2 = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            TLRPC.TL_businessWeeklyOpen weekly = array.get(i);
            TLRPC.TL_businessWeeklyOpen newWeekly = new TLRPC.TL_businessWeeklyOpen();

            if (utc_offset != 0) {
                int start = weekly.start_minute % (24 * 60);
                int end = start + (weekly.end_minute - weekly.start_minute);
                if (start == 0 && (end == 24 * 60 || end == 24 * 60 - 1)) {
                    newWeekly.start_minute = weekly.start_minute;
                    newWeekly.end_minute = weekly.end_minute;
                    array2.add(newWeekly);
                    continue;
                }
            }

            newWeekly.start_minute = weekly.start_minute + utc_offset;
            newWeekly.end_minute = weekly.end_minute + utc_offset;
            array2.add(newWeekly);

            if (newWeekly.start_minute < 0) {
                if (newWeekly.end_minute < 0) {
                    newWeekly.start_minute += 24 * 7 * 60;
                    newWeekly.end_minute += 24 * 7 * 60;
                } else {
                    newWeekly.start_minute = 0;

                    newWeekly = new TLRPC.TL_businessWeeklyOpen();
                    newWeekly.start_minute = 24 * 7 * 60 + weekly.start_minute + utc_offset;
                    newWeekly.end_minute = (24 * 7 * 60 - 1);
                    array2.add(newWeekly);
                }
            } else if (newWeekly.end_minute > 24 * 7 * 60) {
                if (newWeekly.start_minute > 24 * 7 * 60) {
                    newWeekly.start_minute -= 24 * 7 * 60;
                    newWeekly.end_minute -= 24 * 7 * 60;
                } else {
                    newWeekly.end_minute = 24 * 7 * 60 - 1;

                    newWeekly = new TLRPC.TL_businessWeeklyOpen();
                    newWeekly.start_minute = 0;
                    newWeekly.end_minute = weekly.end_minute + utc_offset - (24 * 7 * 60 - 1);
                    array2.add(newWeekly);
                }
            }
        }

        Collections.sort(array2, (a, b) -> a.start_minute - b.start_minute);
        return array2;
    }

    public static ArrayList<Period>[] getDaysHours(ArrayList<TLRPC.TL_businessWeeklyOpen> hours) {
        ArrayList<Period>[] days = new ArrayList[7];
        for (int i = 0; i < days.length; ++i) {
            days[i] = new ArrayList<>();
        }
        for (int i = 0; i < hours.size(); ++i) {
            TLRPC.TL_businessWeeklyOpen period = hours.get(i);
            int day = (int) (period.start_minute / (24 * 60)) % 7;
            int start = period.start_minute % (24 * 60);
            int end = start + (period.end_minute - period.start_minute);
            days[day].add(new Period(start, end));
        }
        for (int i = 0; i < 7; ++i) {
            int start = (24 * 60) * i;
            int end = (24 * 60) * (i + 1);

            int m = start;
            for (int j = 0; j < hours.size(); ++j) {
                TLRPC.TL_businessWeeklyOpen period = hours.get(j);
                if (period.start_minute <= m && period.end_minute >= m) {
                    m = period.end_minute + 1;
                }
            }

            boolean isFull = m >= end;
            if (isFull) {
                int prevDay = (7 + i - 1) % 7;
                if (!days[prevDay].isEmpty() && days[prevDay].get(days[prevDay].size() - 1).end >= 24 * 60) {
                    days[prevDay].get(days[prevDay].size() - 1).end = 24 * 60 - 1;
                }
                days[i].clear();
                days[i].add(new Period(0, 24 * 60 - 1));
            } else {
                int nextDay = (i + 1) % 7;
                if (!days[i].isEmpty() && !days[nextDay].isEmpty()) {
                    Period todayLast = days[i].get(days[i].size() - 1);
                    Period tomorrowFirst = days[nextDay].get(0);
                    if (todayLast.end > 24 * 60 && todayLast.end - 24 * 60 + 1 == tomorrowFirst.start) {
                        todayLast.end = 24 * 60 - 1;
                        tomorrowFirst.start = 0;
                    }
                }
            }
        }
        return days;
    }

    public static ArrayList<TLRPC.TL_businessWeeklyOpen> fromDaysHours(ArrayList<Period>[] days) {
        ArrayList<TLRPC.TL_businessWeeklyOpen> hours = new ArrayList<>();
        if (days != null) {
            for (int i = 0; i < days.length; ++i) {
                if (days[i] != null) {
                    for (int j = 0; j < days[i].size(); ++j) {
                        Period period = days[i].get(j);
                        TLRPC.TL_businessWeeklyOpen weekly = new TLRPC.TL_businessWeeklyOpen();
                        weekly.start_minute = i * (24 * 60) + period.start;
                        weekly.end_minute = i * (24 * 60) + period.end;
                        hours.add(weekly);
                    }
                }
            }
        }
        return hours;
    }

    private void processDone() {
        if (doneButtonDrawable.getProgress() > 0f) return;

        if (!hasChanges()) {
            finishFragment();
            return;
        }

        doneButtonDrawable.animateToProgress(1f);
        TLRPC.UserFull userFull = getMessagesController().getUserFull(getUserConfig().getClientUserId());
        TLRPC.TL_account_updateBusinessWorkHours req = new TLRPC.TL_account_updateBusinessWorkHours();
        ArrayList<TLRPC.TL_businessWeeklyOpen> periods = fromDaysHours(value);
        if (enabled && !periods.isEmpty()) {
            TLRPC.TL_businessWorkHours business_work_hours = new TLRPC.TL_businessWorkHours();
            business_work_hours.timezone_id = timezoneId;
            business_work_hours.weekly_open.addAll(periods);

            req.flags |= 1;
            req.business_work_hours = business_work_hours;

            if (userFull != null) {
                userFull.flags2 |= 1;
                userFull.business_work_hours = business_work_hours;
            }
        } else {
            if (userFull != null) {
                userFull.flags2 &=~ 1;
                userFull.business_work_hours = null;
            }
        }

        getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.showError(err);
            } else if (res instanceof TLRPC.TL_boolFalse) {
                if (getContext() == null) return;
                doneButtonDrawable.animateToProgress(0f);
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
            } else {
                if (!isFinished && !finishing)
                    finishFragment();
            }
        }));
        getMessagesStorage().updateUserInfo(userFull, false);
    }

    public boolean enabled;
    public ArrayList<Period>[] currentValue = null;
    public ArrayList<Period>[] value = new ArrayList[] {
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>()
    };
    public String currentTimezoneId;
    public String timezoneId;

    public static class Period {
        // from 0 to 2 * 24 * 60
        public int start;
        public int end;

        public Period(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @NonNull
        @Override
        public String toString() {
            return timeToString(start) + " - " + timeToString(end);
        }

        public static String timeToString(int time) {
            return timeToString(time, true);
        }

        public static String timeToString(int time, boolean includeNextDay) {
//            if (time == 24 * 60)
//                time = 24 * 60 - 1;
            int min = time % 60;
            int hours = (time - min) / 60 % 24;
            Calendar rightNow = Calendar.getInstance();
            rightNow.set(0, 0, 0, hours, min);
            String str = LocaleController.getInstance().formatterConstDay.format(rightNow.getTime());
            if (time > 24 * 60 && includeNextDay) {
                return LocaleController.formatString(R.string.BusinessHoursNextDay, str);
            }
            return str;
        }
    }

    public static boolean isFull(ArrayList<Period> periods) {
        if (periods == null || periods.isEmpty()) return false;
        int lastTime = 0;
        for (int i = 0; i < periods.size(); ++i) {
            Period p = periods.get(i);
            if (lastTime < p.start) {
                return false;
            }
            lastTime = p.end;
        }
        return lastTime == 24 * 60 - 1 || lastTime == 24 * 60;
    }

    private String getPeriodsValue(ArrayList<Period> periods) {
        if (periods.isEmpty()) {
            return getString(R.string.BusinessHoursDayClosed);
        } else if (isFull(periods)) {
            return getString(R.string.BusinessHoursDayFullOpened);
        } else {
            String value = "";
            for (int j = 0; j < periods.size(); ++j) {
                Period p = periods.get(j);
                if (j > 0) {
                    value += "\n";
                }
                value += Period.timeToString(p.start) + " - " + Period.timeToString(p.end);
            }
            return value;
        }
    }

    private int maxPeriodsFor(int day) {
        int usedCount = 0;
        for (int i = 0; i < 7; ++i) {
            if (value[i] == null) continue;
            // at least one is needed in case we want to turn on 24 hours
            usedCount += Math.max(1, value[i].size());
        }
        return PERIODS_COUNT_LIMIT - usedCount;
    }

    public static final int BUTTON_SHOW = -1;
    public static final int BUTTON_TIMEZONE = -2;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asTopView(getString(R.string.BusinessHoursInfo), R.raw.biz_clock));
        items.add(UItem.asCheck(BUTTON_SHOW, getString(R.string.BusinessHoursShow)).setChecked(enabled));
        items.add(UItem.asShadow(-100, null));
        if (enabled) {
            items.add(UItem.asHeader(getString(R.string.BusinessHours)));
            for (int i = 0; i < value.length; ++i) {
                if (value[i] == null) {
                    value[i] = new ArrayList<>();
                }
                String day = DayOfWeek.values()[i].getDisplayName(TextStyle.FULL, LocaleController.getInstance().getCurrentLocale());
                day = day.substring(0, 1).toUpperCase() + day.substring(1);
                items.add(UItem.asButtonCheck(i, day, getPeriodsValue(value[i])).setChecked(!value[i].isEmpty()));
            }
            items.add(UItem.asShadow(-101, null));
            items.add(UItem.asButton(BUTTON_TIMEZONE, getString(R.string.BusinessHoursTimezone), TimezonesController.getInstance(currentAccount).getTimezoneName(timezoneId, false)));
            items.add(UItem.asShadow(-102, null));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_SHOW) {
            enabled = !enabled;
            ((TextCheckCell) view).setChecked(enabled);
            listView.adapter.update(true);
            checkDone(true);
        } else if (item.id == BUTTON_TIMEZONE) {
            presentFragment(new TimezoneSelector().setValue(timezoneId).whenSelected(id -> {
                ((TextCell) view).setValue(TimezonesController.getInstance(currentAccount).getTimezoneName(timezoneId = id, false), true);
                checkDone(true);
            }));
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_TEXT_CHECK && item.id >= 0 && item.id < value.length) {
            final boolean checkClick = LocaleController.isRTL ? x <= dp(76) : x >= view.getMeasuredWidth() - dp(76);
            if (checkClick) {
                if (value[item.id].isEmpty()) {
                    ((NotificationsCheckCell) view).setChecked(true);
                    value[item.id].add(new Period(0, 24 * 60 - 1));
                    adaptPrevDay(item.id);
                } else {
                    value[item.id].clear();
                    ((NotificationsCheckCell) view).setChecked(false);
                }
                ((NotificationsCheckCell) view).setValue(getPeriodsValue(value[item.id]));
                checkDone(true);
            } else {
                int prevDay = (7 + item.id - 1) % 7;
                int prevDayMaxTime = 0;
                for (int i = 0; i < value[prevDay].size(); ++i) {
                    if (value[prevDay].get(i).end > prevDayMaxTime) {
                        prevDayMaxTime = value[prevDay].get(i).end;
                    }
                }
                int min = Math.max(0, prevDayMaxTime + 1 - 24 * 60);
                int nextDay = (item.id + 1) % 7;
                int nextDayMinTime = 24 * 60;
                for (int i = 0; i < value[nextDay].size(); ++i) {
                    if (value[nextDay].get(i).start < nextDayMinTime) {
                        nextDayMinTime = value[nextDay].get(i).start;
                    }
                }
                int max = 24 * 60 + nextDayMinTime - 1;
                presentFragment(new OpeningHoursDayActivity(item.text, value[item.id], min, max, maxPeriodsFor(item.id)).onApplied(() -> {
                    listView.adapter.update(true);
                    checkDone(true);
                }).onDone(() -> {
                    adaptPrevDay(item.id);
                }));
            }
        }
    }

    private void adaptPrevDay(int id) {
        Period thisDayLastPeriod = value[id].isEmpty() ? null : value[id].get(value[id].size() - 1);
        if (thisDayLastPeriod == null)
            return;
        int prevDay = (7 + id - 1) % 7;
        Period prevDayLastPeriod = value[prevDay].isEmpty() ? null : value[prevDay].get(value[prevDay].size() - 1);
        if (prevDayLastPeriod != null && prevDayLastPeriod.end > 24 * 60 - 1) {
            prevDayLastPeriod.end = 24 * 60 - 1;
            if (prevDayLastPeriod.start >= prevDayLastPeriod.end) {
                value[prevDay].remove(prevDayLastPeriod);
            }
            View child = listView.findViewByItemId(prevDay);
            if (child instanceof NotificationsCheckCell) {
                ((NotificationsCheckCell) child).setValue(getPeriodsValue(value[prevDay]));
            } else {
                listView.adapter.update(true);
            }
        }
    }

//    private class TimezonesBottomSheet extends BottomSheetWithRecyclerListView {
//        private String currentTimezoneId;
//        private Utilities.Callback<String> whenSelectedTimezone;
//        public TimezonesBottomSheet(BaseFragment fragment, String timezoneId, Utilities.Callback<String> whenSelected) {
//            super(fragment, false, false);
//            currentTimezoneId = timezoneId;
//            whenSelectedTimezone = whenSelected;
//            listView.setOnItemClickListener((view, position) -> {
//                position -= 2;
//                TimezonesController timezonesController = TimezonesController.getInstance(currentAccount);
//                ArrayList<TLRPC.TL_timezone> timezones = timezonesController.getTimezones();
//                if (position >= 0 && position < timezones.size()) {
//                    if (whenSelectedTimezone != null) {
//                        whenSelectedTimezone.run(timezones.get(position).id);
//                    }
//                    dismiss();
//                }
//            });
//        }
//
//        @Override
//        protected CharSequence getTitle() {
//            return getString(R.string.BusinessHoursTimezonePicker);
//        }
//
//        @Override
//        protected RecyclerListView.SelectionAdapter createAdapter() {
//            return new UniversalAdapter(getContext(), currentAccount, this::fillItems, getResourceProvider());
//        }
//
//        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
//            items.add(UItem.asHeader(getString(R.string.BusinessHoursTimezonePicker)));
//
//            TimezonesController timezonesController = TimezonesController.getInstance(currentAccount);
//            ArrayList<TLRPC.TL_timezone> timezones = timezonesController.getTimezones();
//            for (int i = 0; i < timezones.size(); ++i) {
//                items.add(UItem.asCheck(i, timezonesController.getTimezoneName(timezones.get(i), true)).setChecked(TextUtils.equals(currentTimezoneId, timezones.get(i).id)));
//            }
//        }
//    }

}

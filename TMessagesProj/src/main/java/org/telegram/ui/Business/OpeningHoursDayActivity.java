package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.checkerframework.checker.guieffect.qual.UI;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;

public class OpeningHoursDayActivity extends BaseFragment {

    private final CharSequence title;
    private final ArrayList<OpeningHoursActivity.Period> periods;
    private final int min;
    private final int max;
    private final int maxPeriodsCount;

    public OpeningHoursDayActivity(CharSequence text, ArrayList<OpeningHoursActivity.Period> periods, int min, int max, int maxPeriodsCount) {
        super();
        this.title = text;
        this.periods = periods;
        this.min = min;
        this.max = max;
        this.maxPeriodsCount = maxPeriodsCount;
        enabled = !periods.isEmpty();
    }

    private Runnable whenApplied;
    public OpeningHoursDayActivity onApplied(Runnable whenApplied) {
        this.whenApplied = whenApplied;
        return this;
    }

    public Runnable whenDone;
    public OpeningHoursDayActivity onDone(Runnable whenDone) {
        this.whenDone = whenDone;
        return this;
    }

    @Override
    public void onBecomeFullyHidden() {
        if (this.whenDone != null) {
            this.whenDone.run();
        }
        super.onBecomeFullyHidden();
    }

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(title);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView = contentView;
    }

    public boolean enabled;

    public static final int BUTTON_ENABLE = -1;
    public static final int BUTTON_ADD = -2;

    private boolean is24() {
        return periods.size() == 1 && periods.get(0).start == 0 && periods.get(0).end == 24 * 60 - 1;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(BUTTON_ENABLE, getString(R.string.BusinessHoursDayOpen)).setChecked(enabled));
        items.add(UItem.asShadow(null));
        if (enabled) {
            for (int i = 0; i < periods.size(); ++i) {
                if (i > 0) {
                    items.add(UItem.asShadow(null));
                }
                OpeningHoursActivity.Period period = periods.get(i);
                if (is24()) {
                    continue;
                }
                items.add(UItem.asButton(3 * i, getString(R.string.BusinessHoursDayOpenHour), OpeningHoursActivity.Period.timeToString(period.start)));
                items.add(UItem.asButton(3 * i + 1, getString(R.string.BusinessHoursDayCloseHour), OpeningHoursActivity.Period.timeToString(period.end)));
                items.add(UItem.asButton(3 * i + 2, getString(R.string.Remove)).red());
            }
            if (showAddButton()) {
                items.add(UItem.asShadow(null));
                items.add(UItem.asButton(BUTTON_ADD, R.drawable.menu_premium_clock_add, getString(R.string.BusinessHoursDayAdd)).accent());
            }
            items.add(UItem.asShadow(getString(R.string.BusinessHoursDayInfo)));
        }
    }

    private boolean showAddButton() {
        if (periods.size() >= maxPeriodsCount) {
            return false;
        }
        return periods.isEmpty() || is24() || periods.get(periods.size() - 1).end < max - 2;
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ENABLE) {
            enabled = !enabled;
            periods.clear();
            if (enabled) {
                periods.add(new OpeningHoursActivity.Period(0, 24 * 60 - 1));
            }
            ((TextCheckCell) view).setChecked(item.checked = enabled);
            ((TextCheckCell) view).setBackgroundColorAnimated(enabled, Theme.getColor(enabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            listView.adapter.update(true);
            if (whenApplied != null) {
                whenApplied.run();
            }
        } else if (item.id == BUTTON_ADD) {
            if (periods.isEmpty() || is24()) {
                if (is24()) {
                    periods.clear();
                }
                int start = Utilities.clamp(8 * 60, max - 1, min);
                int end = Utilities.clamp(20 * 60, max, start + 1);
                periods.add(new OpeningHoursActivity.Period(start, end));
            } else {
                int lastEnd = periods.get(periods.size() - 1).end;
                int start = Utilities.clamp(lastEnd + 30, max - 1, min);
                int end = Utilities.clamp((lastEnd + 26 * 60) / 2, max, start + 1);
                periods.add(new OpeningHoursActivity.Period(start, end));
            }
            if (whenApplied != null) {
                whenApplied.run();
            }
            listView.adapter.update(true);
        } else if (item.viewType == UniversalAdapter.VIEW_TYPE_TEXT) {
            int index = item.id / 3;
            if (index < 0 || index >= periods.size()) return;
            OpeningHoursActivity.Period prevPeriod = index - 1 >= 0 ? periods.get(index - 1) : null;
            OpeningHoursActivity.Period period = periods.get(index);
            OpeningHoursActivity.Period nextPeriod = index + 1 < periods.size() ? periods.get(index + 1) : null;
            if (item.id % 3 == 0) {
                AlertsCreator.createTimePickerDialog(getContext(), LocaleController.getString(R.string.BusinessHoursDayOpenHourPicker), period.start, prevPeriod == null ? min : prevPeriod.end + 1, period.end - 1, time -> {
                    final boolean wasAddButtonShown = showAddButton();
                    ((TextCell) view).setValue(OpeningHoursActivity.Period.timeToString(period.start = time), true);
                    if (wasAddButtonShown != showAddButton()) {
                        listView.adapter.update(true);
                    }
                    if (whenApplied != null) {
                        whenApplied.run();
                    }
                });
            } else if (item.id % 3 == 1) {
                AlertsCreator.createTimePickerDialog(getContext(), LocaleController.getString(R.string.BusinessHoursDayCloseHourPicker), period.end, period.start + 1, nextPeriod == null ? max : nextPeriod.start - 1, time -> {
                    final boolean wasAddButtonShown = showAddButton();
                    ((TextCell) view).setValue(OpeningHoursActivity.Period.timeToString(period.end = time), true);
                    if (wasAddButtonShown != showAddButton()) {
                        listView.adapter.update(true);
                    }
                    if (whenApplied != null) {
                        whenApplied.run();
                    }
                });
            } else if (item.id % 3 == 2) {
                periods.remove(index);
                if (periods.isEmpty()) { // turn into 24h
                    periods.add(new OpeningHoursActivity.Period(0, 24 * 60 - 1));
                }
                listView.adapter.update(true);
                if (whenApplied != null) {
                    whenApplied.run();
                }
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (!enabled && !periods.isEmpty()) {
            periods.clear();
            if (whenApplied != null) {
                whenApplied.run();
            }
        }
    }
}

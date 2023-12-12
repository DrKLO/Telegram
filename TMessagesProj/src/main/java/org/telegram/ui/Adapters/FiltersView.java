package org.telegram.ui.Adapters;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FiltersView extends RecyclerListView {

    public final static int FILTER_TYPE_MEDIA = 0;
    public final static int FILTER_TYPE_FILES = 1;
    public final static int FILTER_TYPE_LINKS = 2;
    public final static int FILTER_TYPE_MUSIC = 3;
    public final static int FILTER_TYPE_CHAT = 4;
    public final static int FILTER_TYPE_VOICE = 5;
    public final static int FILTER_TYPE_DATE = 6;
    public final static int FILTER_TYPE_ARCHIVE = 7;

    public final static int FILTER_INDEX_MEDIA = 0;
    public final static int FILTER_INDEX_LINKS = 1;
    public final static int FILTER_INDEX_FILES = 2;
    public final static int FILTER_INDEX_MUSIC = 3;
    public final static int FILTER_INDEX_VOICE = 4;

    public final static MediaFilterData[] filters = new MediaFilterData[]{
            new MediaFilterData(R.drawable.search_media_filled, R.string.SharedMediaTab2, new TLRPC.TL_inputMessagesFilterPhotoVideo(), FILTER_TYPE_MEDIA),
            new MediaFilterData(R.drawable.search_links_filled, R.string.SharedLinksTab2, new TLRPC.TL_inputMessagesFilterUrl(), FILTER_TYPE_LINKS),
            new MediaFilterData(R.drawable.search_files_filled, R.string.SharedFilesTab2, new TLRPC.TL_inputMessagesFilterDocument(), FILTER_TYPE_FILES),
            new MediaFilterData(R.drawable.search_music_filled, R.string.SharedMusicTab2, new TLRPC.TL_inputMessagesFilterMusic(), FILTER_TYPE_MUSIC),
            new MediaFilterData(R.drawable.search_voice_filled, R.string.SharedVoiceTab2, new TLRPC.TL_inputMessagesFilterRoundVoice(), FILTER_TYPE_VOICE)
    };

    private ArrayList<MediaFilterData> usersFilters = new ArrayList<>();
    private ArrayList<MediaFilterData> oldItems = new ArrayList<>();
    LinearLayoutManager layoutManager;

    public FiltersView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull Recycler recycler, @NonNull State state, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(recycler, state, info);
                if (!isEnabled()) {
                    info.setVisibleToUser(false);
                }
            }
        };
        layoutManager.setOrientation(HORIZONTAL);
        setLayoutManager(layoutManager);
        setAdapter(new Adapter());
        addItemDecoration(new ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int position = parent.getChildAdapterPosition(view);
                outRect.left = AndroidUtilities.dp(8);
                if (position == state.getItemCount() - 1) {
                    outRect.right = AndroidUtilities.dp(10);
                }
                if (position == 0) {
                    outRect.left = AndroidUtilities.dp(10);
                }
            }
        });
        setItemAnimator(new DefaultItemAnimator() {

            private final float scaleFrom = 0;

            @Override
            protected long getMoveAnimationDelay() {
                return 0;
            }

            @Override
            protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
                return 0;
            }

            @Override
            public long getMoveDuration() {
                return 220;
            }

            @Override
            public long getAddDuration() {
                return 220;
            }

            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                boolean r = super.animateAdd(holder);
                if (r) {
                    holder.itemView.setScaleX(scaleFrom);
                    holder.itemView.setScaleY(scaleFrom);
                }
                return r;
            }

            @Override
            public void animateAddImpl(RecyclerView.ViewHolder holder) {
                final View view = holder.itemView;
                final ViewPropertyAnimator animation = view.animate();
                mAddAnimations.add(holder);
                animation.alpha(1).scaleX(1f).scaleY(1f).setDuration(getAddDuration())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animator) {
                                dispatchAddStarting(holder);
                            }

                            @Override
                            public void onAnimationCancel(Animator animator) {
                                view.setAlpha(1);
                            }

                            @Override
                            public void onAnimationEnd(Animator animator) {
                                animation.setListener(null);
                                dispatchAddFinished(holder);
                                mAddAnimations.remove(holder);
                                dispatchFinishedWhenDone();
                            }
                        }).start();
            }

            @Override
            protected void animateRemoveImpl(RecyclerView.ViewHolder holder) {
                final View view = holder.itemView;
                final ViewPropertyAnimator animation = view.animate();
                mRemoveAnimations.add(holder);
                animation.setDuration(getRemoveDuration()).alpha(0).scaleX(scaleFrom).scaleY(scaleFrom).setListener(
                        new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animator) {
                                dispatchRemoveStarting(holder);
                            }

                            @Override
                            public void onAnimationEnd(Animator animator) {
                                animation.setListener(null);
                                view.setAlpha(1);
                                view.setTranslationX(0);
                                view.setTranslationY(0);
                                view.setScaleX(1f);
                                view.setScaleY(1f);
                                dispatchRemoveFinished(holder);
                                mRemoveAnimations.remove(holder);
                                dispatchFinishedWhenDone();
                            }
                        }).start();
            }
        });
        setWillNotDraw(false);
        setHideIfEmpty(false);
        setSelectorRadius(AndroidUtilities.dp(28));
        setSelectorDrawableColor(getThemedColor(Theme.key_listSelector));
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
    }

    public MediaFilterData getFilterAt(int i) {
        if (usersFilters.isEmpty()) {
            return filters[i];
        }
        return usersFilters.get(i);
    }

    public void setUsersAndDates(ArrayList<Object> localUsers, ArrayList<DateData> dates, boolean archive) {
        oldItems.clear();
        oldItems.addAll(usersFilters);
        usersFilters.clear();
        if (localUsers != null) {
            for (int i = 0; i < localUsers.size(); i++) {
                Object object = localUsers.get(i);
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    String title;
                    if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id == user.id) {
                        title = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    } else {
                        title = ContactsController.formatName(user.first_name, user.last_name, 10);
                    }
                    MediaFilterData data = new MediaFilterData(R.drawable.search_users_filled, title, null, FILTER_TYPE_CHAT);
                    data.setUser(user);
                    usersFilters.add(data);
                } else if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) object;
                    String title = chat.title;
                    if (chat.title.length() > 12) {
                        title = String.format("%s...", title.substring(0, 10));
                    }
                    MediaFilterData data = new MediaFilterData(R.drawable.search_users_filled, title, null, FILTER_TYPE_CHAT);
                    data.setUser(chat);
                    usersFilters.add(data);
                }
            }
        }
        if (dates != null) {
            for (int i = 0; i < dates.size(); i++) {
                DateData dateData = dates.get(i);
                MediaFilterData data = new MediaFilterData(R.drawable.search_date_filled, dateData.title, null, FILTER_TYPE_DATE);
                data.setDate(dateData);
                usersFilters.add(data);
            }
        }
        if (archive) {
            FiltersView.MediaFilterData filterData = new FiltersView.MediaFilterData(R.drawable.chats_archive, R.string.ArchiveSearchFilter, null, FiltersView.FILTER_TYPE_ARCHIVE);
            usersFilters.add(filterData);
        }
        if (getAdapter() != null) {
            UpdateCallback updateCallback = new UpdateCallback(getAdapter());
            DiffUtil.calculateDiff(diffUtilsCallback).dispatchUpdatesTo(updateCallback);
            if (!usersFilters.isEmpty() && updateCallback.changed) {
                layoutManager.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    private final static int minYear = 2013;
    private final static Pattern yearPatter = Pattern.compile("20[0-9]{1,2}");
    private final static Pattern monthYearOrDayPatter = Pattern.compile("(\\w{3,}) ([0-9]{0,4})");
    private final static Pattern yearOrDayAndMonthPatter = Pattern.compile("([0-9]{0,4}) (\\w{2,})");

    private final static Pattern shortDate = Pattern.compile("^([0-9]{1,4})(\\.| |/|\\-)([0-9]{1,4})$");
    private final static Pattern longDate = Pattern.compile("^([0-9]{1,2})(\\.| |/|\\-)([0-9]{1,2})(\\.| |/|\\-)([0-9]{1,4})$");


    private final static int[] numberOfDaysEachMonth = new int[]{31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    public static void fillTipDates(String query, ArrayList<DateData> dates) {
        dates.clear();
        if (query == null) {
            return;
        }
        String q = query.trim();
        if (q.length() < 3) {
            return;
        }
        if (LocaleController.getString("SearchTipToday", R.string.SearchTipToday).toLowerCase().startsWith(q) || "today".startsWith(q)) {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            calendar.set(year, month, day, 0, 0, 0);
            long minDate = calendar.getTimeInMillis();
            calendar.set(year, month, day + 1, 0, 0, 0);
            long maxDate = calendar.getTimeInMillis() - 1;
            dates.add(new DateData(LocaleController.getString("SearchTipToday", R.string.SearchTipToday), minDate, maxDate));
            return;
        }

        if (LocaleController.getString("SearchTipYesterday", R.string.SearchTipYesterday).toLowerCase().startsWith(q) || "yesterday".startsWith(q)) {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            calendar.set(year, month, day, 0, 0, 0);
            long minDate = calendar.getTimeInMillis() - 86400000L;
            calendar.set(year, month, day + 1, 0, 0, 0);
            long maxDate = calendar.getTimeInMillis() - 86400001L;
            dates.add(new DateData(LocaleController.getString("SearchTipYesterday", R.string.SearchTipYesterday), minDate, maxDate));
            return;
        }
        Matcher matcher;

        int dayOfWeek = getDayOfWeek(q);
        if (dayOfWeek >= 0) {
            Calendar calendar = Calendar.getInstance();
            long now = calendar.getTimeInMillis();
            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
            if (calendar.getTimeInMillis() > now) {
                calendar.setTimeInMillis(calendar.getTimeInMillis()  - 604800000L);
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            calendar.set(year, month, day, 0, 0, 0);
            long minDate = calendar.getTimeInMillis();
            calendar.set(year, month, day + 1, 0, 0, 0);
            long maxDate = calendar.getTimeInMillis() - 1;
            dates.add(new DateData(LocaleController.getInstance().formatterWeekLong.format(minDate), minDate, maxDate));
            return;
        }
        if ((matcher = shortDate.matcher(q)).matches()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(3);
            int k = Integer.parseInt(g1);
            int k1 = Integer.parseInt(g2);
            if (k > 0 && k <= 31) {
                if (k1 >= minYear && k <= 12) {
                    int selectedYear = k1;
                    int month = k - 1;
                    createForMonthYear(dates, month, selectedYear);
                    return;
                } else if (k1 <= 12) {
                    int day = k - 1;
                    int month = k1 - 1;
                    createForDayMonth(dates, day, month);
                }
            } else if (k >= minYear && k1 <= 12) {
                int selectedYear = k;
                int month = k1 - 1;
                createForMonthYear(dates, month, selectedYear);
            }

            return;
        }

        if ((matcher = longDate.matcher(q)).matches()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(3);
            String g3 = matcher.group(5);
            if (!matcher.group(2).equals(matcher.group(4))) {
                return;
            }
            int day = Integer.parseInt(g1);
            int month = Integer.parseInt(g2) - 1;
            int year = Integer.parseInt(g3);
            if (year >= 10 && year <= 99) {
                year += 2000;
            }
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            if (validDateForMont(day - 1, month) && year >= minYear && year <= currentYear) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, day, 0, 0, 0);
                long minDate = calendar.getTimeInMillis();
                calendar.set(year, month, day + 1, 0, 0, 0);
                long maxDate = calendar.getTimeInMillis() - 1;
                dates.add(new DateData(LocaleController.getInstance().formatterYearMax.format(minDate), minDate, maxDate));
                return;
            }

            return;
        }
        if ((matcher = yearPatter.matcher(q)).matches()) {
            int selectedYear = Integer.valueOf(q);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            if (selectedYear < minYear) {
                selectedYear = minYear;
                for (int i = currentYear; i >= selectedYear; i--) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(i, 0, 1, 0, 0, 0);
                    long minDate = calendar.getTimeInMillis();
                    calendar.set(i + 1, 0, 1, 0, 0, 0);
                    long maxDate = calendar.getTimeInMillis() - 1;
                    dates.add(new DateData(Integer.toString(i), minDate, maxDate));
                }
            } else if (selectedYear <= currentYear) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(selectedYear, 0, 1, 0, 0, 0);
                long minDate = calendar.getTimeInMillis();
                calendar.set(selectedYear + 1, 0, 1, 0, 0, 0);
                long maxDate = calendar.getTimeInMillis() - 1;
                dates.add(new DateData(Integer.toString(selectedYear), minDate, maxDate));
            }
            return;
        }

        if ((matcher = monthYearOrDayPatter.matcher(q)).matches()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(2);
            int month = getMonth(g1);
            if (month >= 0) {
                int k = Integer.valueOf(g2);
                if (k > 0 && k <= 31) {
                    int day = k - 1;
                    createForDayMonth(dates, day, month);
                    return;
                } else if (k >= minYear) {
                    int selectedYear = k;
                    createForMonthYear(dates, month, selectedYear);
                    return;
                }
            }
        }

        if ((matcher = yearOrDayAndMonthPatter.matcher(q)).matches()) {
            String g1 = matcher.group(1);
            String g2 = matcher.group(2);
            int month = getMonth(g2);
            if (month >= 0) {
                int k = Integer.valueOf(g1);
                if (k > 0 && k <= 31) {
                    int day = k - 1;
                    createForDayMonth(dates, day, month);
                    return;
                } else if (k >= minYear) {
                    int selectedYear = k;
                    createForMonthYear(dates, month, selectedYear);
                }
            }
        }

        if (!TextUtils.isEmpty(q) && q.length() > 2) {
            int month = getMonth(q);
            long today = Calendar.getInstance().getTimeInMillis();
            if (month >= 0) {
                int selectedYear = minYear;
                int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                for (int j = currentYear; j >= selectedYear; j--) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(j, month, 1, 0, 0, 0);
                    long minDate = calendar.getTimeInMillis();
                    if (minDate > today) {
                        continue;
                    }
                    calendar.add(Calendar.MONTH, 1);
                    long maxDate = calendar.getTimeInMillis() - 1;
                    dates.add(new DateData(LocaleController.getInstance().formatterMonthYear.format(minDate), minDate, maxDate));
                }
            }
        }
    }

    private static void createForMonthYear(ArrayList<DateData> dates, int month, int selectedYear) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        long today = Calendar.getInstance().getTimeInMillis();
        if (selectedYear >= minYear && selectedYear <= currentYear) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(selectedYear, month, 1, 0, 0, 0);
            long minDate = calendar.getTimeInMillis();
            if (minDate > today) {
                return;
            }
            calendar.add(Calendar.MONTH, 1);
            long maxDate = calendar.getTimeInMillis() - 1;
            dates.add(new DateData(LocaleController.getInstance().formatterMonthYear.format(minDate), minDate, maxDate));
        }
    }

    private static void createForDayMonth(ArrayList<DateData> dates, int day, int month) {
        if (validDateForMont(day, month)) {
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            long today = Calendar.getInstance().getTimeInMillis();
            GregorianCalendar georgianCal = (GregorianCalendar) GregorianCalendar.getInstance();
            for (int i = currentYear; i >= minYear; i--) {
                if (month == 1 && day == 28 && !georgianCal.isLeapYear(i)) {
                    continue;
                }
                Calendar calendar = Calendar.getInstance();
                calendar.set(i, month, day + 1, 0, 0, 0);
                long minDate = calendar.getTimeInMillis();
                if (minDate > today) {
                    continue;
                }
                calendar.set(i, month, day + 2, 0, 0, 0);
                long maxDate = calendar.getTimeInMillis() - 1;
                if (i == currentYear) {
                    dates.add(new DateData(LocaleController.getInstance().formatterDayMonth.format(minDate), minDate, maxDate));
                } else {
                    dates.add(new DateData(LocaleController.getInstance().formatterYearMax.format(minDate), minDate, maxDate));
                }
            }
        }
    }

    private static boolean validDateForMont(int day, int month) {
        if (month >= 0 && month < 12) {
            if (day >= 0 && day < numberOfDaysEachMonth[month]) {
                return true;
            }
        }
        return false;
    }

    public static int getDayOfWeek(String q) {
        Calendar c = Calendar.getInstance();
        if (q.length() <= 3) {
            return -1;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE", Locale.ENGLISH);
        for (int i = 0; i < 7; i++) {
            c.set(Calendar.DAY_OF_WEEK, i);
            if (LocaleController.getInstance().formatterWeekLong.format(c.getTime()).toLowerCase().startsWith(q)) {
                return i;
            }
            if (dateFormat.format(c.getTime()).toLowerCase().startsWith(q)) {
                return i;
            }
        }
        return -1;
    }

    public static int getMonth(String q) {
        String[] months = new String[]{
                LocaleController.getString("January", R.string.January).toLowerCase(),
                LocaleController.getString("February", R.string.February).toLowerCase(),
                LocaleController.getString("March", R.string.March).toLowerCase(),
                LocaleController.getString("April", R.string.April).toLowerCase(),
                LocaleController.getString("May", R.string.May).toLowerCase(),
                LocaleController.getString("June", R.string.June).toLowerCase(),
                LocaleController.getString("July", R.string.July).toLowerCase(),
                LocaleController.getString("August", R.string.August).toLowerCase(),
                LocaleController.getString("September", R.string.September).toLowerCase(),
                LocaleController.getString("October", R.string.October).toLowerCase(),
                LocaleController.getString("November", R.string.November).toLowerCase(),
                LocaleController.getString("December", R.string.December).toLowerCase()
        };

        String[] monthsEng = new String[12];
        Calendar c = Calendar.getInstance();
        for (int i = 1; i <= 12; i++) {
            c.set(0, 0, 0, 0, 0, 0);
            c.set(Calendar.MONTH, i);
            monthsEng[i - 1] = c.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH).toLowerCase();
        }


        for (int i = 0; i < 12; i++) {
            if (monthsEng[i].startsWith(q) || months[i].startsWith(q)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isValidFormat(String format, String value, Locale locale) {
        LocalDateTime ldt = null;
        DateTimeFormatter fomatter = DateTimeFormatter.ofPattern(format, locale);

        try {
            ldt = LocalDateTime.parse(value, fomatter);
            String result = ldt.format(fomatter);
            return result.equals(value);
        } catch (DateTimeParseException e) {
            try {
                LocalDate ld = LocalDate.parse(value, fomatter);
                String result = ld.format(fomatter);
                return result.equals(value);
            } catch (DateTimeParseException exp) {
                try {
                    LocalTime lt = LocalTime.parse(value, fomatter);
                    String result = lt.format(fomatter);
                    return result.equals(value);
                } catch (DateTimeParseException e2) {
                }
            }
        }

        return false;
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawRect(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), Theme.dividerPaint);
    }

    public void updateColors() {
        getRecycledViewPool().clear();

        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view instanceof FilterView) {
                ((FilterView) view).updateColors();
            }
        }

        for (int i = 0; i < getCachedChildCount(); i++) {
            View view = getCachedChildAt(i);
            if (view instanceof FilterView) {
                ((FilterView) view).updateColors();
            }
        }

        for (int i = 0; i < getAttachedScrapChildCount(); i++) {
            View view = getAttachedScrapChildAt(i);
            if (view instanceof FilterView) {
                ((FilterView) view).updateColors();
            }
        }
        setSelectorDrawableColor(getThemedColor(Theme.key_listSelector));
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewHolder holder = new ViewHolder(new FilterView(parent.getContext(), resourcesProvider));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(32));
            lp.topMargin = AndroidUtilities.dp(6);
            holder.itemView.setLayoutParams(lp);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MediaFilterData data;
            data = usersFilters.get(position);
            ((ViewHolder) holder).filterView.setData(data);
        }

        @Override
        public int getItemCount() {
            return usersFilters.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    DiffUtil.Callback diffUtilsCallback = new DiffUtil.Callback() {
        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return usersFilters.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            MediaFilterData oldItem = oldItems.get(oldItemPosition);
            MediaFilterData newItem = usersFilters.get(newItemPosition);
            if (oldItem.isSameType(newItem)) {
                if (oldItem.filterType == FILTER_TYPE_CHAT) {
                    if (oldItem.chat instanceof TLRPC.User && newItem.chat instanceof TLRPC.User) {
                        return ((TLRPC.User) oldItem.chat).id == ((TLRPC.User) newItem.chat).id;
                    }
                    if (oldItem.chat instanceof TLRPC.Chat && newItem.chat instanceof TLRPC.Chat) {
                        return ((TLRPC.Chat) oldItem.chat).id == ((TLRPC.Chat) newItem.chat).id;
                    }
                } else if (oldItem.filterType == FILTER_TYPE_DATE) {
                    return oldItem.title.equals(newItem.title);
                } else if (oldItem.filterType == FILTER_TYPE_ARCHIVE) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return true;
        }
    };

    public static class FilterView extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        BackupImageView avatarImageView;
        TextView titleView;
        CombinedDrawable thumbDrawable;
        MediaFilterData data;

        public FilterView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(32, 32));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 38, 0, 16, 0));
            updateColors();
        }

        private void updateColors() {
            setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(28), getThemedColor(Theme.key_groupcreate_spanBackground)));
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            if (thumbDrawable != null) {
                if (data.filterType == FILTER_TYPE_ARCHIVE) {
                    Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundArchived), false);
                    Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                } else {
                    Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundBlue), false);
                    Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                }
            }
        }

        public void setData(MediaFilterData data) {
            this.data = data;
            avatarImageView.getImageReceiver().clearImage();
            if (data.filterType == FILTER_TYPE_ARCHIVE) {
                thumbDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), R.drawable.chats_archive);
                thumbDrawable.setIconSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundArchived), false);
                Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                avatarImageView.setImageDrawable(thumbDrawable);
                titleView.setText(data.title);
                return;
            }
            thumbDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), data.iconResFilled);
            Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_backgroundBlue), false);
            Theme.setCombinedDrawableColor(thumbDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
            if (data.filterType == FILTER_TYPE_CHAT) {
                if (data.chat instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) data.chat;
                    if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id == user.id) {
                        CombinedDrawable combinedDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(32), R.drawable.chats_saved);
                        combinedDrawable.setIconSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                        Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_backgroundSaved), false);
                        Theme.setCombinedDrawableColor(combinedDrawable, getThemedColor(Theme.key_avatar_actionBarIconBlue), true);
                        avatarImageView.setImageDrawable(combinedDrawable);
                    } else {
                        avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(16));
                        avatarImageView.getImageReceiver().setForUserOrChat(user, thumbDrawable);
                    }
                } else if (data.chat instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) data.chat;
                    avatarImageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(16));
                    avatarImageView.getImageReceiver().setForUserOrChat(chat, thumbDrawable);
                }
            } else {
                avatarImageView.setImageDrawable(thumbDrawable);
            }
            titleView.setText(data.title);
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }
    }

    private class ViewHolder extends RecyclerListView.ViewHolder {

        FilterView filterView;

        public ViewHolder(@NonNull FilterView itemView) {
            super(itemView);
            filterView = itemView;
        }
    }

    public static class MediaFilterData {

        public final int iconResFilled;
        public int titleResId;
        private String title;
        public final int filterType;
        public final TLRPC.MessagesFilter filter;
        public TLObject chat;
        public DateData dateData;
        public boolean removable = true;

        public MediaFilterData(int iconResFilled, String title, TLRPC.MessagesFilter filter, int filterType) {
            this.iconResFilled = iconResFilled;
            this.title = title;
            this.filter = filter;
            this.filterType = filterType;
        }

        public MediaFilterData(int iconResFilled, int titleResId, TLRPC.MessagesFilter filter, int filterType) {
            this.iconResFilled = iconResFilled;
            this.titleResId = titleResId;
            this.filter = filter;
            this.filterType = filterType;
        }

        public String getTitle() {
            if (title != null) {
                return title;
            }
            return LocaleController.getString(titleResId);
        }

        public void setUser(TLObject chat) {
            this.chat = chat;
        }

        public boolean isSameType(MediaFilterData filterData) {
            if (filterType == filterData.filterType) {
                return true;
            }
            if (isMedia() && filterData.isMedia()) {
                return true;
            }
            return false;
        }

        public boolean isMedia() {
            return filterType == FILTER_TYPE_MEDIA || filterType == FILTER_TYPE_FILES || filterType == FILTER_TYPE_LINKS || filterType == FILTER_TYPE_MUSIC || filterType == FILTER_TYPE_VOICE;
        }

        public void setDate(DateData dateData) {
            this.dateData = dateData;
        }
    }

    public static class DateData {
        public final String title;
        public final long minDate;
        public final long maxDate;

        private DateData(String title, long minDate, long maxDate) {
            this.title = title;
            this.minDate = minDate;
            this.maxDate = maxDate;
        }
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();
        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_graySection));
        arrayList.add(new ThemeDescription(this, 0, null, null, null, null, Theme.key_graySectionText));
        return arrayList;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        return super.onTouchEvent(e);
    }

    private static class UpdateCallback implements ListUpdateCallback {

        final RecyclerView.Adapter adapter;
        boolean changed;

        private UpdateCallback(RecyclerView.Adapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onInserted(int position, int count) {
            changed = true;
            adapter.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            changed = true;
            adapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            changed = true;
            adapter.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void onChanged(int position, int count, @Nullable Object payload) {
            adapter.notifyItemRangeChanged(position, count, payload);
        }
    }
}

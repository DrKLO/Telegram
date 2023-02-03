package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;

public class CalendarActivity extends BaseFragment {

    public final static int TYPE_CHAT_ACTIVITY = 0;
    public final static int TYPE_MEDIA_CALENDAR = 1;

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    TextView selectDaysButton;
    TextView removeDaysButton;

    private Paint selectOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View blurredView;

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private int topicId;
    private boolean loading;
    private boolean checkEnterItems;
    private boolean inSelectionMode;
    BackDrawable backDrawable;

    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;
    Callback callback;

    HintView selectDaysHint;


    private int dateSelectedStart;
    private int dateSelectedEnd;
    private ValueAnimator selectionAnimator;

    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private int photosVideosTypeFilter;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;
    private FrameLayout bottomBar;
    private int minDate;
    private boolean canClearHistory;

    private int calendarType;

    private Path path = new Path();
    private SpoilerEffect mediaSpoilerEffect = new SpoilerEffect();

    public CalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate) {
        super(args);
        this.photosVideosTypeFilter = photosVideosTypeFilter;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }

        selectOutlinePaint.setStyle(Paint.Style.STROKE);
        selectOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        selectOutlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        topicId = getArguments().getInt("topic_id");
        calendarType = getArguments().getInt("type");

        if (dialogId >= 0) {
            canClearHistory = true;
        } else {
            canClearHistory = false;
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context) {
            int lastSize;

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int size = getMeasuredHeight() + getMeasuredWidth() << 16;
                if (lastSize != size) {
                    lastSize = size;
                    adapter.notifyDataSetChanged();
                }
            }
        };
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        boolean showBottomPanel = calendarType == TYPE_CHAT_ACTIVITY && canClearHistory;
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, showBottomPanel ? 48 : 0));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };


        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (dateSelectedStart != 0 || dateSelectedEnd != 0 || inSelectionMode) {
                        inSelectionMode = false;
                        dateSelectedStart = 0;
                        dateSelectedEnd = 0;
                        updateTitle();
                        animateSelection();
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        backDrawable = new BackDrawable(false);
        actionBar.setBackButtonDrawable(backDrawable);
        backDrawable.setRotation(0f, false);

        loadNext();
        updateColors();
        activeTextPaint.setColor(Color.WHITE);

        if (showBottomPanel) {
            bottomBar = new FrameLayout(context) {
                @Override
                public void onDraw(Canvas canvas) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.getShadowHeight(), Theme.dividerPaint);
                }
            };
            bottomBar.setWillNotDraw(false);
            bottomBar.setPadding(0, AndroidUtilities.getShadowHeight(), 0, 0);
            bottomBar.setClipChildren(false);
            selectDaysButton = new TextView(context);
            selectDaysButton.setGravity(Gravity.CENTER);
            selectDaysButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            selectDaysButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectDaysButton.setOnClickListener(view -> {
                inSelectionMode = true;
                updateTitle();
            });
            selectDaysButton.setText(LocaleController.getString("SelectDays", R.string.SelectDays));
            selectDaysButton.setAllCaps(true);
            bottomBar.addView(selectDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0f, 0, 0));

            removeDaysButton = new TextView(context);
            removeDaysButton.setGravity(Gravity.CENTER);
            removeDaysButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            removeDaysButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            removeDaysButton.setOnClickListener(view -> {
                if (lastDaysSelected == 0) {
                    if (selectDaysHint == null) {
                        selectDaysHint = new HintView(contentView.getContext(), 8);
                        selectDaysHint.setExtraTranslationY(AndroidUtilities.dp(24));
                        contentView.addView(selectDaysHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
                        selectDaysHint.setText(LocaleController.getString("SelectDaysTooltip", R.string.SelectDaysTooltip));
                    }
                    selectDaysHint.showForView(bottomBar, true);
                    return;
                }
                AlertsCreator.createClearDaysDialogAlert(this, lastDaysSelected, getMessagesController().getUser(dialogId), null, false, new MessagesStorage.BooleanCallback() {
                    @Override
                    public void run(boolean forAll) {
                        finishFragment();

                        if (parentLayout.getFragmentStack().size() >= 2) {
                            BaseFragment fragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2);
                            if (fragment instanceof ChatActivity) {
                                ((ChatActivity) fragment).deleteHistory(dateSelectedStart, dateSelectedEnd + 86400, forAll);
                            }
                        }
                    }
                }, null);
            });
            removeDaysButton.setAllCaps(true);

            removeDaysButton.setVisibility(View.GONE);
            bottomBar.addView(removeDaysButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0f, 0, 0));
            contentView.addView(bottomBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 0, 0, 0, 0));


            selectDaysButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_fieldOverlayText), (int) (0.2f * 255)), 2));
            removeDaysButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_dialogTextRed), (int) (0.2f * 255)), 2));
            selectDaysButton.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
            removeDaysButton.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        }

        return fragmentView;
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    periodDay.date = (int) (calendar.getTimeInMillis() / 1000L);
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null || !messagesByDays.get(index, null).hasImage) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }
                }

                int maxDate = (int) (System.currentTimeMillis() / 1000L);
                minDate = res.min_date;

                for (int date = res.min_date; date < maxDate; date += 86400) {
                    calendar.setTimeInMillis(date * 1000L);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        PeriodDay periodDay = new PeriodDay();
                        periodDay.hasImage = false;
                        periodDay.date = (int) (calendar.getTimeInMillis() / 1000L);
                        messagesByDays.put(index, periodDay);
                    }
                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        }
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
            monthView.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
            monthView.setSelectionValue(1f);
            updateRowSelections(monthView, false);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        boolean attached;

        GestureDetectorCompat gestureDetector;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            if (calendarType == TYPE_CHAT_ACTIVITY && canClearHistory) {
                titleView.setOnLongClickListener(view -> {
                    if (messagesByDays == null) {
                        return false;
                    }
                    int start = -1;
                    int end = -1;
                    for (int i = 0; i < daysInMonth; i++) {
                        PeriodDay day = messagesByDays.get(i, null);
                        if (day != null) {
                            if (start == -1) {
                                start = day.date;
                            }
                            end = day.date;
                        }
                    }

                    if (start >= 0 && end >= 0) {
                        inSelectionMode = true;
                        dateSelectedStart = start;
                        dateSelectedEnd = end;
                        updateTitle();
                        animateSelection();
                    }

                    return false;
                });
                titleView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (messagesByDays == null) {
                            return;
                        }
                        if (inSelectionMode) {
                            int start = -1;
                            int end = -1;
                            for (int i = 0; i < daysInMonth; i++) {
                                PeriodDay day = messagesByDays.get(i, null);
                                if (day != null) {
                                    if (start == -1) {
                                        start = day.date;
                                    }
                                    end = day.date;
                                }
                            }

                            if (start >= 0 && end >= 0) {
                                dateSelectedStart = start;
                                dateSelectedEnd = end;
                                updateTitle();
                                animateSelection();
                            }
                        }
                    }
                });
            }
            titleView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));

            gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }


                @SuppressLint("NotifyDataSetChanged")
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (parentLayout == null) {
                        return false;
                    }
                    if (calendarType == TYPE_MEDIA_CALENDAR && messagesByDays != null) {
                        PeriodDay day = getDayAtCoord(e.getX(), e.getY());
                        if (day != null && day.messageObject != null && callback != null) {
                            callback.onDateSelected(day.messageObject.getId(), day.startOffset);
                            finishFragment();
                        }
                    }
                    if (messagesByDays != null) {
                        if (inSelectionMode) {
                            PeriodDay day = getDayAtCoord(e.getX(), e.getY());
                            if (day != null) {
                                if (selectionAnimator != null) {
                                    selectionAnimator.cancel();
                                    selectionAnimator = null;
                                }
                                if (dateSelectedStart != 0 || dateSelectedEnd != 0) {
                                    if (dateSelectedStart == day.date && dateSelectedEnd == day.date) {
                                        dateSelectedStart = dateSelectedEnd = 0;
                                    } else if (dateSelectedStart == day.date) {
                                        dateSelectedStart = dateSelectedEnd;
                                    } else if (dateSelectedEnd == day.date) {
                                        dateSelectedEnd = dateSelectedStart;
                                    } else if (dateSelectedStart == dateSelectedEnd) {
                                        if (day.date > dateSelectedEnd) {
                                            dateSelectedEnd = day.date;
                                        } else {
                                            dateSelectedStart = day.date;
                                        }
                                    } else {
                                        dateSelectedStart = dateSelectedEnd = day.date;
                                    }
                                } else {
                                    dateSelectedStart = dateSelectedEnd = day.date;

                                }
                                updateTitle();
                                animateSelection();
                            }
                        } else {
                            PeriodDay day = getDayAtCoord(e.getX(), e.getY());
                            if (day != null && parentLayout != null && parentLayout.getFragmentStack().size() >= 2) {
                                BaseFragment fragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2);
                                if (fragment instanceof ChatActivity) {
                                    finishFragment();
                                    ((ChatActivity) fragment).jumpToDate(day.date);
                                }
                            }
                        }
                    }
                    return false;
                }

                private PeriodDay getDayAtCoord(float pressedX, float pressedY) {
                    if (messagesByDays == null) {
                        return null;
                    }
                    int currentCell = 0;
                    int currentColumn = startDayOfWeek;

                    float xStep = getMeasuredWidth() / 7f;
                    float yStep = AndroidUtilities.dp(44 + 8);
                    int hrad = AndroidUtilities.dp(44) / 2;
                    for (int i = 0; i < daysInMonth; i++) {
                        float cx = xStep * currentColumn + xStep / 2f;
                        float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);

                        if (pressedX >= cx - hrad && pressedX <= cx + hrad && pressedY >= cy - hrad && pressedY <= cy + hrad) {
                            PeriodDay day = messagesByDays.get(i, null);
                            if (day != null) {
                                return day;
                            }
                        }

                        currentColumn++;
                        if (currentColumn >= 7) {
                            currentColumn = 0;
                            currentCell++;
                        }
                    }
                    return null;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    super.onLongPress(e);
                    if (calendarType != TYPE_CHAT_ACTIVITY) {
                        return;
                    }
                    PeriodDay periodDay = getDayAtCoord(e.getX(), e.getY());

                    if (periodDay != null) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                        Bundle bundle = new Bundle();
                        if (dialogId > 0) {
                            bundle.putLong("user_id", dialogId);
                        } else {
                            bundle.putLong("chat_id", -dialogId);
                        }
                        bundle.putInt("start_from_date", periodDay.date);
                        bundle.putBoolean("need_remove_previous_same_chat_activity", false);
                        ChatActivity chatActivity = new ChatActivity(bundle);

                        ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider());
                        previewMenu.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

                        ActionBarMenuSubItem cellJump = new ActionBarMenuSubItem(getParentActivity(), true, false);
                        cellJump.setTextAndIcon(LocaleController.getString("JumpToDate", R.string.JumpToDate), R.drawable.msg_message);
                        cellJump.setMinimumWidth(160);
                        cellJump.setOnClickListener(view -> {
                            if (parentLayout.getFragmentStack().size() >= 3) {
                                BaseFragment fragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 3);
                                if (fragment instanceof ChatActivity) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        finishFragment();
                                        ((ChatActivity) fragment).jumpToDate(periodDay.date);
                                    }, 300);
                                }
                            }
                            finishPreviewFragment();
                        });
                        previewMenu.addView(cellJump);

                        if (canClearHistory) {
                            ActionBarMenuSubItem cellSelect = new ActionBarMenuSubItem(getParentActivity(), false, false);
                            cellSelect.setTextAndIcon(LocaleController.getString("SelectThisDay", R.string.SelectThisDay), R.drawable.msg_select);
                            cellSelect.setMinimumWidth(160);
                            cellSelect.setOnClickListener(view -> {
                                dateSelectedStart = dateSelectedEnd = periodDay.date;
                                inSelectionMode = true;
                                updateTitle();
                                animateSelection();
                                finishPreviewFragment();
                            });
                            previewMenu.addView(cellSelect);

                            ActionBarMenuSubItem cellDelete = new ActionBarMenuSubItem(getParentActivity(), false, true);
                            cellDelete.setTextAndIcon(LocaleController.getString("ClearHistory", R.string.ClearHistory), R.drawable.msg_delete);
                            cellDelete.setMinimumWidth(160);
                            cellDelete.setOnClickListener(view -> {
                                if (parentLayout.getFragmentStack().size() >= 3) {
                                    BaseFragment fragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 3);
                                    if (fragment instanceof ChatActivity) {
                                        AlertsCreator.createClearDaysDialogAlert(CalendarActivity.this, 1, getMessagesController().getUser(dialogId), null, false, new MessagesStorage.BooleanCallback() {
                                            @Override
                                            public void run(boolean forAll) {
                                                finishFragment();
                                                ((ChatActivity) fragment).deleteHistory(dateSelectedStart, dateSelectedEnd + 86400, forAll);
                                            }
                                        }, null);
                                    }
                                }
                                finishPreviewFragment();
                            });
                            previewMenu.addView(cellDelete);
                        }
                        previewMenu.setFitItems(true);


                        blurredView = new View(context) {
                            @Override
                            public void setAlpha(float alpha) {
                                super.setAlpha(alpha);
                                if (fragmentView != null) {
                                    fragmentView.invalidate();
                                }
                            }
                        };
                        blurredView.setOnClickListener(view -> {
                            finishPreviewFragment();
                        });
                        blurredView.setVisibility(View.GONE);
                        blurredView.setFitsSystemWindows(true);
                        parentLayout.getOverlayContainerView().addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                        prepareBlurBitmap();

                        presentFragmentAsPreviewWithMenu(chatActivity, previewMenu);
                    }
                }
            });
            gestureDetector.setIsLongpressEnabled(calendarType == TYPE_CHAT_ACTIVITY);
        }

        private void startSelectionAnimation(int fromDate, int toDate) {
            if (messagesByDays != null) {
                for (int i = 0; i < daysInMonth; i++) {
                    PeriodDay day = messagesByDays.get(i, null);
                    if (day != null) {
                        day.fromSelProgress = day.selectProgress;
                        day.toSelProgress = day.date >= fromDate && day.date <= toDate ? 1 : 0;

                        day.fromSelSEProgress = day.selectStartEndProgress;
                        if (day.date == fromDate || day.date == toDate)
                            day.toSelSEProgress = 1;
                        else day.toSelSEProgress = 0;
                    }
                }
            }
        }

        private void setSelectionValue(float f) {
            if (messagesByDays != null) {
                for (int i = 0; i < daysInMonth; i++) {
                    PeriodDay day = messagesByDays.get(i, null);
                    if (day != null) {
                        day.selectProgress = day.fromSelProgress + (day.toSelProgress - day.fromSelProgress) * f;
                        day.selectStartEndProgress = day.fromSelSEProgress + (day.toSelSEProgress - day.fromSelSEProgress) * f;
                    }
                }
            }
            invalidate();
        }

        private SparseArray<ValueAnimator> rowAnimators = new SparseArray<>();
        private SparseArray<RowAnimationValue> rowSelectionPos = new SparseArray<>();

        public void dismissRowAnimations(boolean animate) {
            for (int i = 0; i < rowSelectionPos.size(); i++) {
                animateRow(rowSelectionPos.keyAt(i), 0, 0, false, animate);
            }
        }

        public void animateRow(int row, int startColumn, int endColumn, boolean appear, boolean animate) {
            ValueAnimator a = rowAnimators.get(row);
            if (a != null) a.cancel();

            float xStep = getMeasuredWidth() / 7f;

            float cxFrom1, cxFrom2, fromAlpha;
            RowAnimationValue p = rowSelectionPos.get(row);
            if (p != null) {
                cxFrom1 = p.startX;
                cxFrom2 = p.endX;
                fromAlpha = p.alpha;
            } else {
                cxFrom1 = xStep * startColumn + xStep / 2f;
                cxFrom2 = xStep * startColumn + xStep / 2f;
                fromAlpha = 0;
            }
            float cxTo1 = appear ? xStep * startColumn + xStep / 2f : cxFrom1;
            float cxTo2 = appear ? xStep * endColumn + xStep / 2f : cxFrom2;
            float toAlpha = appear ? 1 : 0;

            RowAnimationValue pr = new RowAnimationValue(cxFrom1, cxFrom2);
            rowSelectionPos.put(row, pr);

            if (animate) {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(300);
                anim.setInterpolator(Easings.easeInOutQuad);
                anim.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    pr.startX = cxFrom1 + (cxTo1 - cxFrom1) * val;
                    pr.endX = cxFrom2 + (cxTo2 - cxFrom2) * val;
                    pr.alpha = fromAlpha + (toAlpha - fromAlpha) * val;
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        pr.startX = cxTo1;
                        pr.endX = cxTo2;
                        pr.alpha = toAlpha;
                        invalidate();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rowAnimators.remove(row);
                        if (!appear)
                            rowSelectionPos.remove(row);
                    }
                });
                anim.start();
                rowAnimators.put(row, anim);
            } else {
                pr.startX = cxTo1;
                pr.endX = cxTo2;
                pr.alpha = toAlpha;
                invalidate();
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear || monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null || !messagesByDays.get(key).hasImage) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        boolean hasMediaSpoilers = messageObject.hasMediaSpoilers();
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), hasMediaSpoilers ? "5_5_b" : "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), hasMediaSpoilers ? "5_5_b" : "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), hasMediaSpoilers ? "5_5_b" : "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), hasMediaSpoilers ? "5_5_b" : "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime = (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));

            updateRowSelections(this, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            int selSize = AndroidUtilities.dp(44);
            for (int row = 0; row < Math.ceil((startDayOfWeek + daysInMonth) / 7f); row++) {
                float cy = yStep * row + yStep / 2f + AndroidUtilities.dp(44);
                RowAnimationValue v = rowSelectionPos.get(row);
                if (v != null) {
                    selectPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                    selectPaint.setAlpha((int) (v.alpha * (255 * 0.16f)));
                    AndroidUtilities.rectTmp.set(v.startX - selSize / 2f, cy - selSize / 2f, v.endX + selSize / 2f, cy + selSize / 2f);
                    int dp = AndroidUtilities.dp(32);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp, dp, selectPaint);
                }
            }
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                PeriodDay day = messagesByDays != null ? messagesByDays.get(i, null) : null;
                if (nowTime < startMonthTime + (i + 1) * 86400 || (minDate > 0 && minDate > startMonthTime + (i + 2) * 86400)) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (day != null && day.hasImage) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !day.wasDrawn) {
                            day.enterAlpha = 0f;
                            day.startEnterDelay = Math.max(0, (cy + getY()) / listView.getMeasuredHeight() * 150);
                        }
                        if (day.startEnterDelay > 0) {
                            day.startEnterDelay -= 16;
                            if (day.startEnterDelay < 0) {
                                day.startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (day.startEnterDelay >= 0 && day.enterAlpha != 1f) {
                            day.enterAlpha += 16 / 220f;
                            if (day.enterAlpha > 1f) {
                                day.enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = day.enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s, cx, cy);
                        }
                        int pad = (int) (AndroidUtilities.dp(7f) * day.selectProgress);
                        if (day.selectStartEndProgress >= 0.01f) {
                            selectPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, selectPaint);

                            selectOutlinePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                            AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(44) / 2f, cy + AndroidUtilities.dp(44) / 2f);
                            canvas.drawArc(AndroidUtilities.rectTmp, -90, day.selectStartEndProgress * 360, false, selectOutlinePaint);
                        }

                        imagesByDays.get(i).setAlpha(day.enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - (AndroidUtilities.dp(44) - pad) / 2f, cy - (AndroidUtilities.dp(44) - pad) / 2f, AndroidUtilities.dp(44) - pad, AndroidUtilities.dp(44) - pad);
                        imagesByDays.get(i).draw(canvas);

                        if (messagesByDays.get(i) != null && messagesByDays.get(i).messageObject != null && messagesByDays.get(i).messageObject.hasMediaSpoilers()) {
                            float rad = (AndroidUtilities.dp(44) - pad) / 2f;
                            path.rewind();
                            path.addCircle(cx, cy, rad, Path.Direction.CW);

                            canvas.save();
                            canvas.clipPath(path);

                            int sColor = Color.WHITE;
                            mediaSpoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f * day.enterAlpha)));
                            mediaSpoilerEffect.setBounds((int) (cx - rad), (int) (cy - rad), (int) (cx + rad), (int) (cy + rad));
                            mediaSpoilerEffect.draw(canvas);

                            invalidate();

                            canvas.restore();
                        }

                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (day.enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) - pad) / 2f, blackoutPaint);
                        day.wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }
                } else {
                    if (day != null && day.selectStartEndProgress >= 0.01f) {
                        selectPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, selectPaint);

                        selectOutlinePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                        AndroidUtilities.rectTmp.set(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(44) / 2f, cy + AndroidUtilities.dp(44) / 2f);
                        canvas.drawArc(AndroidUtilities.rectTmp, -90, day.selectStartEndProgress * 360, false, selectOutlinePaint);

                        int pad = (int) (AndroidUtilities.dp(7f) * day.selectStartEndProgress);
                        selectPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                        selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) - pad) / 2f, selectPaint);

                        float alpha = day.selectStartEndProgress;
                        if (alpha != 1f) {
                            int oldAlpha = textPaint.getAlpha();
                            textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                            textPaint.setAlpha(oldAlpha);

                            oldAlpha = textPaint.getAlpha();
                            activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                            activeTextPaint.setAlpha(oldAlpha);
                        } else {
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        }
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    int lastDaysSelected;
    boolean lastInSelectionMode;

    private void updateTitle() {
        if (!canClearHistory) {
            actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
            backDrawable.setRotation(0f, true);
            return;
        }
        int daysSelected;
        if (dateSelectedStart == dateSelectedEnd && dateSelectedStart == 0) {
            daysSelected = 0;
        } else {
            daysSelected = 1 + (Math.abs(dateSelectedStart - dateSelectedEnd) / 86400);
        }
        boolean oldInSelectionMode = lastInSelectionMode;
        if (daysSelected != lastDaysSelected || lastInSelectionMode != inSelectionMode) {
            boolean fromBottom = lastDaysSelected > daysSelected;
            lastDaysSelected = daysSelected;
            lastInSelectionMode = inSelectionMode;
            String title;
            if (daysSelected > 0) {
                title = LocaleController.formatPluralString("Days", daysSelected);
                backDrawable.setRotation(1f, true);
            } else if (inSelectionMode) {
                title = LocaleController.getString("SelectDays", R.string.SelectDays);
                backDrawable.setRotation(1f, true);
            } else {
                title = LocaleController.getString("Calendar", R.string.Calendar);
                backDrawable.setRotation(0f, true);
            }
            if (daysSelected > 1) {
                removeDaysButton.setText(LocaleController.formatString("ClearHistoryForTheseDays", R.string.ClearHistoryForTheseDays));
            } else if (daysSelected > 0 || inSelectionMode) {
                removeDaysButton.setText(LocaleController.formatString("ClearHistoryForThisDay", R.string.ClearHistoryForThisDay));
            }
            actionBar.setTitleAnimated(title, fromBottom, 150);


            if ((!inSelectionMode || daysSelected > 0) && selectDaysHint != null) {
                selectDaysHint.hide();
            }
            if (daysSelected > 0 || inSelectionMode) {
                if (removeDaysButton.getVisibility() == View.GONE) {
                    removeDaysButton.setAlpha(0f);
                    removeDaysButton.setTranslationY(-AndroidUtilities.dp(20));
                }
                removeDaysButton.setVisibility(View.VISIBLE);
                selectDaysButton.animate().setListener(null).cancel();
                removeDaysButton.animate().setListener(null).cancel();
                selectDaysButton.animate().alpha(0f).translationY(AndroidUtilities.dp(20)).setDuration(150).setListener(new HideViewAfterAnimation(selectDaysButton)).start();
                removeDaysButton.animate().alpha(daysSelected == 0 ? 0.5f : 1f).translationY(0).start();
                selectDaysButton.setEnabled(false);
                removeDaysButton.setEnabled(true);
            } else {
                if (selectDaysButton.getVisibility() == View.GONE) {
                    selectDaysButton.setAlpha(0f);
                    selectDaysButton.setTranslationY(AndroidUtilities.dp(20));
                }
                selectDaysButton.setVisibility(View.VISIBLE);
                selectDaysButton.animate().setListener(null).cancel();
                removeDaysButton.animate().setListener(null).cancel();
                selectDaysButton.animate().alpha(1f).translationY(0).start();
                removeDaysButton.animate().alpha(0f).translationY(-AndroidUtilities.dp(20)).setDuration(150).setListener(new HideViewAfterAnimation(removeDaysButton)).start();
                selectDaysButton.setEnabled(true);
                removeDaysButton.setEnabled(false);
            }
        }

    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
        boolean hasImage = true;
        int date;

        float selectStartEndProgress;
        float fromSelSEProgress;
        float toSelSEProgress;

        float selectProgress;
        float fromSelProgress;
        float toSelProgress;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        super.onTransitionAnimationProgress(isOpen, progress);
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
            } else {
                blurredView.setAlpha(progress);
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
    }

    private void animateSelection() {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(300);
        a.setInterpolator(CubicBezierInterpolator.DEFAULT);
        a.addUpdateListener(animation -> {
            float selectProgress = (float) animation.getAnimatedValue();
            for (int j = 0; j < listView.getChildCount(); j++) {
                MonthView m = (MonthView) listView.getChildAt(j);
                m.setSelectionValue(selectProgress);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (int j = 0; j < listView.getChildCount(); j++) {
                    MonthView m = (MonthView) listView.getChildAt(j);
                    m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
                }
            }
        });
        a.start();
        selectionAnimator = a;

        int minIndex = Integer.MAX_VALUE;
        int maxIndex = -1;
        for (int j = 0; j < listView.getChildCount(); j++) {
            MonthView m = (MonthView) listView.getChildAt(j);
            updateRowSelections(m, true);
        }

        for (int j = 0; j < listView.getCachedChildCount(); j++) {
            MonthView m = (MonthView) listView.getCachedChildAt(j);
            updateRowSelections(m, false);
            m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
            m.setSelectionValue(1f);
        }
        for (int j = 0; j < listView.getHiddenChildCount(); j++) {
            MonthView m = (MonthView) listView.getHiddenChildAt(j);
            updateRowSelections(m, false);
            m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
            m.setSelectionValue(1f);
        }
        for (int j = 0; j < listView.getAttachedScrapChildCount(); j++) {
            MonthView m = (MonthView) listView.getAttachedScrapChildAt(j);
            updateRowSelections(m, false);
            m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
            m.setSelectionValue(1f);
        }
    }

    private void updateRowSelections(MonthView m, boolean animate) {
        if (dateSelectedStart == 0 || dateSelectedEnd == 0) {
            m.dismissRowAnimations(animate);
        } else {
            if (m.messagesByDays == null) {
                return;
            }
            if (!animate) {
                m.dismissRowAnimations(false);
            }

            int row = 0;
            int dayInRow = m.startDayOfWeek;
            int sDay = -1, eDay = -1;
            for (int i = 0; i < m.daysInMonth; i++) {
                PeriodDay day = m.messagesByDays.get(i, null);
                if (day != null) {
                    if (day.date >= dateSelectedStart && day.date <= dateSelectedEnd) {
                        if (sDay == -1)
                            sDay = dayInRow;
                        eDay = dayInRow;
                    }
                }

                dayInRow++;
                if (dayInRow >= 7) {
                    dayInRow = 0;
                    if (sDay != -1 && eDay != -1) {
                        m.animateRow(row, sDay, eDay, true, animate);
                    } else m.animateRow(row, 0, 0, false, animate);

                    row++;
                    sDay = -1;
                    eDay = -1;
                }
            }
            if (sDay != -1 && eDay != -1) {
                m.animateRow(row, sDay, eDay, true, animate);
            } else {
                m.animateRow(row, 0, 0, false, animate);
            }
        }
    }

    private final static class RowAnimationValue {
        float startX, endX;
        float alpha;

        RowAnimationValue(float s, float e) {
            startX = s;
            endX = e;
        }
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (parentLayout.getView().getMeasuredWidth() / 6.0f);
        int h = (int) (parentLayout.getView().getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        parentLayout.getView().draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onBackPressed() {
        if (inSelectionMode) {
            inSelectionMode = false;
            dateSelectedStart = dateSelectedEnd = 0;
            updateTitle();
            animateSelection();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}

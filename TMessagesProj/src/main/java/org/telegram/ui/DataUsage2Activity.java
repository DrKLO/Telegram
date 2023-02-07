package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.CacheChart;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;
import java.util.Arrays;

public class DataUsage2Activity extends BaseFragment {

    private Theme.ResourcesProvider resourcesProvider;

    public DataUsage2Activity() {
        this(null);
    }

    public DataUsage2Activity(Theme.ResourcesProvider resourcesProvider) {
        super();
        this.resourcesProvider = resourcesProvider;
    }

    private ViewPagerFixed pager;
    private ViewPagerFixed.Adapter pageAdapter;
    private ViewPagerFixed.TabsView tabsView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("NetworkUsage", R.string.NetworkUsage));
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefault));
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (getParentLayout() != null && tabsView != null) {
                    float y = tabsView.getMeasuredHeight();
                    canvas.drawLine(0, y, getWidth(), y, Theme.dividerPaint);
                }
            }
        };
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        pager = new ViewPagerFixed(context);
        pager.setAdapter(pageAdapter = new PageAdapter());

        tabsView = pager.createTabsView(true, 8);
        tabsView.setBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefault));
        frameLayout.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        frameLayout.addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 48, 0, 0));

        return fragmentView = frameLayout;
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    private class PageAdapter extends ViewPagerFixed.Adapter {

        @Override
        public int getItemCount() {
            return 4;
        }

        @Override
        public View createView(int viewType) {
            return new ListView(getContext());
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            ((ListView) view).setType(position);
            ((ListView) view).scrollToPosition(0);
        }

        @Override
        public String getItemTitle(int position) {
            switch (position) {
                case ListView.TYPE_ALL:     return LocaleController.getString("NetworkUsageAllTab", R.string.NetworkUsageAllTab);
                case ListView.TYPE_MOBILE:  return LocaleController.getString("NetworkUsageMobileTab", R.string.NetworkUsageMobileTab);
                case ListView.TYPE_WIFI:    return LocaleController.getString("NetworkUsageWiFiTab", R.string.NetworkUsageWiFiTab);
                case ListView.TYPE_ROAMING: return LocaleController.getString("NetworkUsageRoamingTab", R.string.NetworkUsageRoamingTab);
                default: return "";
            }
        }
    }

    private static String[] colors = {
        Theme.key_statisticChartLine_blue,
        Theme.key_statisticChartLine_green,
        Theme.key_statisticChartLine_lightblue,
        Theme.key_statisticChartLine_golden,
        Theme.key_statisticChartLine_red,
        Theme.key_statisticChartLine_purple,
        Theme.key_statisticChartLine_cyan
    };

    private static int[] particles = {
        R.drawable.msg_filled_data_videos,
        R.drawable.msg_filled_data_files,
        R.drawable.msg_filled_data_photos,
        R.drawable.msg_filled_data_messages,
        R.drawable.msg_filled_data_music,
        R.drawable.msg_filled_data_voice,
        R.drawable.msg_filled_data_calls
    };

    private static int[] titles = {
        R.string.LocalVideoCache,
        R.string.LocalDocumentCache,
        R.string.LocalPhotoCache,
        R.string.MessagesSettings,
        R.string.LocalMusicCache,
        R.string.LocalAudioCache,
        R.string.CallsDataUsage
    };

    private static int[] stats = {
        StatsController.TYPE_VIDEOS,
        StatsController.TYPE_FILES,
        StatsController.TYPE_PHOTOS,
        StatsController.TYPE_MESSAGES,
        StatsController.TYPE_MUSIC,
        StatsController.TYPE_AUDIOS,
        StatsController.TYPE_CALLS,
    };


    class ListView extends RecyclerListView {

        public static final int TYPE_ALL = 0;
        public static final int TYPE_MOBILE = 1;
        public static final int TYPE_WIFI = 2;
        public static final int TYPE_ROAMING = 3;

        private boolean animateChart = false;

        int currentType = TYPE_ALL;

        LinearLayoutManager layoutManager;
        Adapter adapter;

        public ListView(Context context) {
            super(context);
            setLayoutManager(layoutManager = new LinearLayoutManager(context));
            setAdapter(adapter = new Adapter());
            setOnItemClickListener((view, position) -> {
                if (view instanceof Cell && position >= 0 && position < itemInners.size()) {
                    ItemInner item = itemInners.get(position);
                    if (item != null) {
                        if (item.index >= 0) {
                            collapsed[item.index] = !collapsed[item.index];
                            updateRows(true);
                        } else if (item.index == -2) {
                            presentFragment(new DataAutoDownloadActivity(currentType - 1));
                        }
                    }
                } else if (view instanceof TextCell) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("ResetStatisticsAlertTitle", R.string.ResetStatisticsAlertTitle));
                    builder.setMessage(LocaleController.getString("ResetStatisticsAlert", R.string.ResetStatisticsAlert));
                    builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialogInterface, j) -> {
                        removedSegments.clear();
                        for (int i = 0; i < segments.length; ++i) {
                            long size = segments[i].size;
                            if (size > 0) {
                                removedSegments.add(segments[i].index);
                            }
                        }

                        StatsController.getInstance(currentAccount).resetStats(0);
                        StatsController.getInstance(currentAccount).resetStats(1);
                        StatsController.getInstance(currentAccount).resetStats(2);

                        animateChart = true;
                        setup();
                        updateRows(true);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                }
            });

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setDurations(220);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            setItemAnimator(itemAnimator);
        }

        public void setType(int type) {
            this.currentType = type;

            removedSegments.clear();
            empty = getBytesCount(StatsController.TYPE_TOTAL) <= 0;
            setup();
            updateRows(false);
        }

        private void setup() {
            totalSize = getBytesCount(StatsController.TYPE_TOTAL);
            totalSizeIn = getReceivedBytesCount(StatsController.TYPE_TOTAL);
            totalSizeOut = getSentBytesCount(StatsController.TYPE_TOTAL);
            if (segments == null) {
                segments = new Size[7];
            }
            if (chartSegments == null) {
                chartSegments = new Size[7];
            }
            for (int i = 0; i < stats.length; ++i) {
                long size = getBytesCount(stats[i]);
                chartSegments[i] = segments[i] = new Size(
                        i,
                        size,
                        getReceivedBytesCount(stats[i]),
                        getSentBytesCount(stats[i]),
                        getReceivedItemsCount(stats[i]),
                        getSentItemsCount(stats[i])
                );
                tempSizes[i] = size / (float) totalSize;
            }
            Arrays.sort(segments, (a, b) -> Long.compare(b.size, a.size));
            AndroidUtilities.roundPercents(tempSizes, tempPercents);
            Arrays.fill(collapsed, true);
        }

        private ArrayList<ItemInner> oldItems = new ArrayList<>();
        private ArrayList<ItemInner> itemInners = new ArrayList<>();

        private float[] tempSizes = new float[7];
        private int[] tempPercents = new int[7];

        private ArrayList<Integer> removedSegments = new ArrayList<>();
        private Size[] segments, chartSegments;
        private boolean[] collapsed = new boolean[7];
        private long totalSize, totalSizeIn, totalSizeOut;
        private boolean empty;

        private CacheChart chart;

        private String formatPercent(int percent) {
            return percent <= 0 ? String.format("<%d%%", 1) : String.format("%d%%", percent);
        }

        class Size extends CacheChart.SegmentSize {

            int index;
            long inSize, outSize;
            int inCount, outCount;

            public Size(int index, long size, long inSize, long outSize, int inCount, int outCount) {
                this.index = index;

                this.size = size;
                this.selected = true;

                this.inSize = inSize;
                this.inCount = inCount;
                this.outSize = outSize;
                this.outCount = outCount;
            }
        }

        private void updateRows(boolean animated) {
            oldItems.clear();
            oldItems.addAll(itemInners);

            itemInners.clear();

            itemInners.add(new ItemInner(VIEW_TYPE_CHART));
            final String sinceText = totalSize > 0 ?
                LocaleController.formatString("YourNetworkUsageSince", R.string.YourNetworkUsageSince, LocaleController.getInstance().formatterStats.format(getResetStatsDate())) :
                LocaleController.formatString("NoNetworkUsageSince", R.string.NoNetworkUsageSince, LocaleController.getInstance().formatterStats.format(getResetStatsDate()));
            itemInners.add(ItemInner.asSubtitle(sinceText));

            ArrayList<ItemInner> sections = new ArrayList<>();
            for (int i = 0; i < segments.length; ++i) {
                long size = segments[i].size;
                int index = segments[i].index;
                boolean emptyButShown = empty || removedSegments.contains(index);
                if (size <= 0 && !emptyButShown) {
                    continue;
                }
                SpannableString percent = new SpannableString(formatPercent(tempPercents[index]));
                percent.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, percent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                percent.setSpan(new RelativeSizeSpan(.8f), 0, percent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                percent.setSpan(new CustomCharacterSpan(.1), 0, percent.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sections.add(ItemInner.asCell(
                    i,
                    particles[index],
                    getThemedColor(colors[index]),
                    size == 0 ?
                        LocaleController.getString(titles[index]) :
                        TextUtils.concat(LocaleController.getString(titles[index]), "  ", percent),
                    AndroidUtilities.formatFileSize(size)
                ));
            }

            if (!sections.isEmpty()) {

                SpannableString sentIcon = new SpannableString("^");
                Drawable sentIconDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_upload).mutate();
                sentIconDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                sentIconDrawable.setBounds(0, AndroidUtilities.dp(2), AndroidUtilities.dp(16), AndroidUtilities.dp(2 + 16));
                sentIcon.setSpan(new ImageSpan(sentIconDrawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString receivedIcon = new SpannableString("v");
                Drawable receivedIconDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_download).mutate();
                receivedIconDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                receivedIconDrawable.setBounds(0, AndroidUtilities.dp(2), AndroidUtilities.dp(16), AndroidUtilities.dp(2 + 16));
                receivedIcon.setSpan(new ImageSpan(receivedIconDrawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                for (int i = 0; i < sections.size(); ++i) {
                    int index = sections.get(i).index;
                    if (index >= 0 && !collapsed[index]) {
                        Size size = segments[index];
                        if (stats[size.index] == StatsController.TYPE_CALLS) {
                            if (size.outSize > 0 || size.outCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        LocaleController.formatPluralStringComma("OutgoingCallsCount", size.outCount),
                                        AndroidUtilities.formatFileSize(size.outSize)
                                ));
                            }
                            if (size.inSize > 0 || size.inCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        LocaleController.formatPluralStringComma("IncomingCallsCount", size.inCount),
                                        AndroidUtilities.formatFileSize(size.inSize)
                                ));
                            }
                        } else if (stats[size.index] != StatsController.TYPE_MESSAGES) {
                            if (size.outSize > 0 || size.outCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        TextUtils.concat(sentIcon, " ", AndroidUtilities.replaceTags(LocaleController.formatPluralStringComma("FilesSentCount", size.outCount))),
                                        AndroidUtilities.formatFileSize(size.outSize)
                                ));
                            }
                            if (size.inSize > 0 || size.inCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        TextUtils.concat(receivedIcon, " ", AndroidUtilities.replaceTags(LocaleController.formatPluralStringComma("FilesReceivedCount", size.inCount))),
                                        AndroidUtilities.formatFileSize(size.inSize)
                                ));
                            }
                        } else {
                            if (size.outSize > 0 || size.outCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        TextUtils.concat(sentIcon, " ", LocaleController.getString("BytesSent", R.string.BytesSent)),
                                        AndroidUtilities.formatFileSize(size.outSize)
                                ));
                            }
                            if (size.inSize > 0 || size.inCount > 0) {
                                sections.add(++i, ItemInner.asCell(
                                        -1, 0, 0,
                                        TextUtils.concat(receivedIcon, " ", LocaleController.getString("BytesReceived", R.string.BytesReceived)),
                                        AndroidUtilities.formatFileSize(size.inSize)
                                ));
                            }
                        }
                    }
                }
//                itemInners.add(new ItemInner(VIEW_TYPE_ROUNDING));
                itemInners.addAll(sections);
//                itemInners.add(new ItemInner(VIEW_TYPE_END));
                if (!empty) {
                    itemInners.add(ItemInner.asSeparator(LocaleController.getString("DataUsageSectionsInfo", R.string.DataUsageSectionsInfo)));
                }
            }

            if (!empty) {
                itemInners.add(ItemInner.asHeader(LocaleController.getString("TotalNetworkUsage", R.string.TotalNetworkUsage)));
                itemInners.add(ItemInner.asCell(
                        -1,
                        R.drawable.msg_filled_data_sent,
                        getThemedColor(Theme.key_statisticChartLine_lightblue),
                        LocaleController.getString("BytesSent", R.string.BytesSent),
                        AndroidUtilities.formatFileSize(totalSizeOut)
                ));
                itemInners.add(ItemInner.asCell(
                        -1,
                        R.drawable.msg_filled_data_received,
                        getThemedColor(Theme.key_statisticChartLine_green),
                        LocaleController.getString("BytesReceived", R.string.BytesReceived),
                        AndroidUtilities.formatFileSize(totalSizeIn)
                ));
            }

            if (!sections.isEmpty()) {
                itemInners.add(ItemInner.asSeparator(sinceText));
            }

            if (currentType != TYPE_ALL) {
                if (sections.isEmpty()) {
                    itemInners.add(ItemInner.asSeparator());
                }
                itemInners.add(ItemInner.asCell(
                        -2,
                        R.drawable.msg_download_settings,
                        getThemedColor(Theme.key_statisticChartLine_lightblue),
                        LocaleController.getString("AutomaticDownloadSettings", R.string.AutomaticDownloadSettings),
                        null
                ));
                String info;
                switch (currentType) {
                    case TYPE_MOBILE:
                        info = LocaleController.getString("AutomaticDownloadSettingsInfoMobile", R.string.AutomaticDownloadSettingsInfoMobile);
                        break;
                    case TYPE_ROAMING:
                        info = LocaleController.getString("AutomaticDownloadSettingsInfoRoaming", R.string.AutomaticDownloadSettingsInfoRoaming);
                        break;
                    default:
                    case TYPE_WIFI:
                        info = LocaleController.getString("AutomaticDownloadSettingsInfoWiFi", R.string.AutomaticDownloadSettingsInfoWiFi);
                        break;
                }
                itemInners.add(ItemInner.asSeparator(info));
            }

            if (!sections.isEmpty()) {
                itemInners.add(new ItemInner(VIEW_TYPE_RESET_BUTTON, LocaleController.getString("ResetStatistics", R.string.ResetStatistics)));
            }
            itemInners.add(ItemInner.asSeparator());

            if (adapter != null) {
                if (animated) {
                    adapter.setItems(oldItems, itemInners);
                } else {
                    adapter.notifyDataSetChanged();
                }
            }
        }

        private CharSequence bold(CharSequence text) {
            SpannableString string = new SpannableString(text);
            string.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, string.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return string;
        }

        private class Adapter extends AdapterWithDiffUtils {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case VIEW_TYPE_CHART:
                        chart = new CacheChart(getContext(), colors.length, colors, CacheChart.TYPE_NETWORK, particles) {

                            @Override
                            protected int heightDp() {
                                return 216;
                            }

                            @Override
                            protected int padInsideDp() {
                                return 10;
                            }

                            @Override
                            protected void onSectionDown(int index, boolean down) {
                                if (!down) {
                                    ListView.this.removeHighlightRow();
                                    return;
                                }
                                if (index < 0 || index >= segments.length) {
                                    return;
                                }
                                int pos = -1;
                                for (int i = 0; i < segments.length; ++i) {
                                    if (segments[i].index == index) {
                                        pos = i;
                                        break;
                                    }
                                }
                                int position = -1;
                                for (int i = 0; i < itemInners.size(); ++i) {
                                    ItemInner item2 = itemInners.get(i);
                                    if (item2 != null && item2.viewType == VIEW_TYPE_SECTION && item2.index == pos) {
                                        position = i;
                                        break;
                                    }
                                }

                                if (position >= 0) {
                                    final int finalPosition = position;
                                    ListView.this.highlightRow(() -> finalPosition, 0);
                                } else {
                                    ListView.this.removeHighlightRow();
                                }
                            }
                        };
                        chart.setInterceptTouch(false);
                        view = chart;
                        break;
                    case VIEW_TYPE_SUBTITLE:
                        view = new SubtitleCell(getContext());
                        break;
                    case VIEW_TYPE_SEPARATOR:
                        view = new TextInfoPrivacyCell(getContext());
                        break;
                    case VIEW_TYPE_HEADER:
                        view = new HeaderCell(getContext());
                        view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        break;
                    case VIEW_TYPE_RESET_BUTTON:
                        TextCell textCell = new TextCell(getContext());
                        textCell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteRedText5));
                        textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                        view = textCell;
                        break;
                    case VIEW_TYPE_END:
                        view = new View(getContext()) {
                            { setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite)); }
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(4), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                    case VIEW_TYPE_ROUNDING:
                        view = new RoundingCell(getContext());
                        break;
                    case VIEW_TYPE_SECTION:
                    default:
                        view = new Cell(getContext());
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ItemInner item = itemInners.get(holder.getAdapterPosition());
                int viewType = holder.getItemViewType();
                if (viewType == VIEW_TYPE_CHART) {
                    CacheChart chart = (CacheChart) holder.itemView;
                    if (segments != null) {
                        chart.setSegments(totalSize, animateChart, chartSegments);
                    }
                    animateChart = false;
                } else if (viewType == VIEW_TYPE_SUBTITLE) {
                    SubtitleCell subtitleCell = (SubtitleCell) holder.itemView;
                    subtitleCell.setText(item.text);
                    int bottomViewType;
                    boolean bottom = position + 1 < itemInners.size() && (bottomViewType = itemInners.get(position + 1).viewType) != item.viewType && bottomViewType != VIEW_TYPE_SEPARATOR && bottomViewType != VIEW_TYPE_ROUNDING;
                    if (bottom) {
                        subtitleCell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        subtitleCell.setBackground(null);
                    }
                } else if (viewType == VIEW_TYPE_SECTION) {
                    Cell cell = (Cell) holder.itemView;
                    cell.set(item.imageColor, item.imageResId, item.text, item.valueText, position + 1 < getItemCount() && itemInners.get(position + 1).viewType == viewType);
                    cell.setArrow(item.pad || item.index < 0 || item.index < segments.length && segments[item.index].size <= 0 ? null : collapsed[item.index]);
                } else if (viewType == VIEW_TYPE_SEPARATOR) {
                    TextInfoPrivacyCell view = (TextInfoPrivacyCell) holder.itemView;
                    boolean top = position > 0 && item.viewType != itemInners.get(position - 1).viewType;
                    boolean bottom = position + 1 < itemInners.size() && itemInners.get(position + 1).viewType != item.viewType;
                    if (top && bottom) {
                        view.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (top) {
                        view.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (bottom) {
                        view.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        view.setBackground(null);
                    }
                    view.setText(item.text);
                } else if (viewType == VIEW_TYPE_HEADER) {
                    HeaderCell header = (HeaderCell) holder.itemView;
                    header.setText(item.text);
                } else if (viewType == VIEW_TYPE_RESET_BUTTON) {
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setText(item.text.toString(), false);
                } else if (viewType == VIEW_TYPE_ROUNDING) {
                    ((RoundingCell) holder.itemView).setTop(true);
                }
            }

            @Override
            public int getItemCount() {
                return itemInners.size();
            }

            @Override
            public int getItemViewType(int position) {
                return itemInners.get(position).viewType;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                ItemInner item = itemInners.get(holder.getAdapterPosition());
                return item.viewType == VIEW_TYPE_RESET_BUTTON || item.viewType == VIEW_TYPE_SECTION && item.index != -1;
            }
        }

        private int getSentItemsCount(int dataType) {
            switch (currentType) {
                case TYPE_MOBILE:
                case TYPE_WIFI:
                case TYPE_ROAMING:
                    return StatsController.getInstance(currentAccount).getSentItemsCount(currentType - 1, dataType);
                case TYPE_ALL:
                default:
                    return (
                            StatsController.getInstance(currentAccount).getSentItemsCount(0, dataType) +
                            StatsController.getInstance(currentAccount).getSentItemsCount(1, dataType) +
                            StatsController.getInstance(currentAccount).getSentItemsCount(2, dataType)
                    );
            }
        }

        private int getReceivedItemsCount(int dataType) {
            switch (currentType) {
                case TYPE_MOBILE:
                case TYPE_WIFI:
                case TYPE_ROAMING:
                    return StatsController.getInstance(currentAccount).getRecivedItemsCount(currentType - 1, dataType);
                case TYPE_ALL:
                default:
                    return (
                            StatsController.getInstance(currentAccount).getRecivedItemsCount(0, dataType) +
                            StatsController.getInstance(currentAccount).getRecivedItemsCount(1, dataType) +
                            StatsController.getInstance(currentAccount).getRecivedItemsCount(2, dataType)
                    );
            }
        }

        private long getBytesCount(int dataType) {
            return getSentBytesCount(dataType) + getReceivedBytesCount(dataType);
        }

        private long getSentBytesCount(int dataType) {
            switch (currentType) {
                case TYPE_MOBILE:
                case TYPE_WIFI:
                case TYPE_ROAMING:
                    return StatsController.getInstance(currentAccount).getSentBytesCount(currentType - 1, dataType);
                case TYPE_ALL:
                default:
                    return (
                        StatsController.getInstance(currentAccount).getSentBytesCount(0, dataType) +
                        StatsController.getInstance(currentAccount).getSentBytesCount(1, dataType) +
                        StatsController.getInstance(currentAccount).getSentBytesCount(2, dataType)
                    );
            }
        }

        private long getReceivedBytesCount(int dataType) {
            switch (currentType) {
                case TYPE_MOBILE:
                case TYPE_WIFI:
                case TYPE_ROAMING:
                    return StatsController.getInstance(currentAccount).getReceivedBytesCount(currentType - 1, dataType);
                case TYPE_ALL:
                default:
                    return (
                        StatsController.getInstance(currentAccount).getReceivedBytesCount(0, dataType) +
                        StatsController.getInstance(currentAccount).getReceivedBytesCount(1, dataType) +
                        StatsController.getInstance(currentAccount).getReceivedBytesCount(2, dataType)
                    );
            }
        }

        private long getResetStatsDate() {
            switch (currentType) {
                case TYPE_MOBILE:
                case TYPE_WIFI:
                case TYPE_ROAMING:
                    return StatsController.getInstance(currentAccount).getResetStatsDate(currentType - 1);
                case TYPE_ALL:
                default:
                    return min(
                            StatsController.getInstance(currentAccount).getResetStatsDate(0),
                            StatsController.getInstance(currentAccount).getResetStatsDate(1),
                            StatsController.getInstance(currentAccount).getResetStatsDate(2)
                    );
            }
        }

        private long min(long... numbers) {
            long min = Long.MAX_VALUE;
            for (int i = 0; i < numbers.length; ++i) {
                if (min > numbers[i])
                    min = numbers[i];
            }
            return min;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec), MeasureSpec.EXACTLY));
        }
    }

    private static final int VIEW_TYPE_CHART = 0;
    private static final int VIEW_TYPE_SUBTITLE = 1;
    private static final int VIEW_TYPE_SECTION = 2;
    private static final int VIEW_TYPE_SEPARATOR = 3;
    private static final int VIEW_TYPE_HEADER = 4;
    private static final int VIEW_TYPE_RESET_BUTTON = 5;
    private static final int VIEW_TYPE_ROUNDING = 6;
    private static final int VIEW_TYPE_END = 7;

    private static class ItemInner extends AdapterWithDiffUtils.Item {

        public int imageResId;
        public int imageColor;
        public CharSequence text;
        public CharSequence valueText;

        public int index;
        public boolean pad;

        public int key;

        public ItemInner(int viewType) {
            super(viewType, false);
        }

        public ItemInner(int viewType, int key) {
            super(viewType, false);
            this.key = key;
        }

        private ItemInner(int viewType, CharSequence text) {
            super(viewType, false);
            this.text = text;
        }

        private ItemInner(int viewType, CharSequence text, CharSequence valueText) {
            super(viewType, false);
            this.text = text;
            this.valueText = valueText;
        }

        private ItemInner(int viewType, int index, CharSequence text, CharSequence valueText) {
            super(viewType, false);
            this.index = index;
            this.text = text;
            this.valueText = valueText;
        }

        private ItemInner(int viewType, int index, int imageResId, int imageColor, CharSequence text, CharSequence valueText) {
            super(viewType, false);
            this.index = index;
            this.imageResId = imageResId;
            this.imageColor = imageColor;
            this.text = text;
            this.valueText = valueText;
        }

        public static ItemInner asSeparator() {
            return new ItemInner(VIEW_TYPE_SEPARATOR);
        }

        public static ItemInner asSeparator(String hint) {
            return new ItemInner(VIEW_TYPE_SEPARATOR, hint);
        }

        public static ItemInner asHeader(String text) {
            return new ItemInner(VIEW_TYPE_HEADER, text);
        }

        public static ItemInner asSubtitle(String text) {
            return new ItemInner(VIEW_TYPE_SUBTITLE, text);
        }

        public static ItemInner asCell(int index, int imageResId, int imageColor, CharSequence text, CharSequence valueText) {
            return new ItemInner(VIEW_TYPE_SECTION, index, imageResId, imageColor, text, valueText);
        }

        public static ItemInner asCell(String text, CharSequence valueText) {
            return new ItemInner(VIEW_TYPE_SECTION, text, valueText);
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ItemInner)) {
                return false;
            }
            ItemInner item = (ItemInner) object;
            if (item.viewType != viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_SUBTITLE || viewType == VIEW_TYPE_HEADER || viewType == VIEW_TYPE_SEPARATOR || viewType == VIEW_TYPE_RESET_BUTTON) {
                return TextUtils.equals(text, item.text);
            }
            if (viewType == VIEW_TYPE_SECTION) {
                return item.index == index && TextUtils.equals(text, item.text) && item.imageColor == imageColor && item.imageResId == imageResId;
            }
            return item.key == key;
        }
    }

    class SubtitleCell extends FrameLayout {

        TextView textView;

        public SubtitleCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));

            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 24, 0, 24, 14));
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public static class RoundingCell extends View {
        Path path = new Path();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RoundingCell(Context context) {
            super(context);
            paint.setShadowLayer(dp(1), 0, dp(-0.66f), 0x0f000000);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        private boolean top = true;

        public void setTop(boolean top) {
            path.rewind();
            float r;
            if (this.top = top) {
                r = AndroidUtilities.dp(14);
                AndroidUtilities.rectTmp.set(0, AndroidUtilities.dp(4), getMeasuredWidth(), AndroidUtilities.dp(4) + getMeasuredHeight() * 2);
                path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            } else {
                r = AndroidUtilities.dp(8);
                AndroidUtilities.rectTmp.set(0, -getMeasuredHeight() * 2 - AndroidUtilities.dp(4), getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(4));
                path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(13), MeasureSpec.EXACTLY));
            setTop(this.top);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            requestLayout();
        }
    }

    class Cell extends FrameLayout {

        ImageView imageView;
        LinearLayout linearLayout, linearLayout2;
        TextView textView;
        ImageView arrowView;
        TextView valueTextView;
        boolean divider;

        public Cell(Context context) {
            super(context);

            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
//            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 18, 0, 18, 0));

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setWeightSum(2);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 64, 0, 20, 0));

            linearLayout2 = new LinearLayout(context);
            linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
            if (LocaleController.isRTL) {
                linearLayout2.setGravity(Gravity.RIGHT);
            }
            linearLayout2.setWeightSum(2);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setSingleLine();
            textView.setLines(1);

            arrowView = new ImageView(context);
            arrowView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            arrowView.setImageResource(R.drawable.arrow_more);
            arrowView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            arrowView.setTranslationY(AndroidUtilities.dp(1));
            arrowView.setVisibility(View.GONE);

            if (LocaleController.isRTL) {
                linearLayout2.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 3, 0, 0, 0));
                linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            } else {
                linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
                linearLayout2.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 3, 0, 0, 0));
            }

            valueTextView = new TextView(context);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            valueTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText2));
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);

            if (LocaleController.isRTL) {
                linearLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT));
                linearLayout.addView(linearLayout2, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            } else {
                linearLayout.addView(linearLayout2, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 2, Gravity.CENTER_VERTICAL));
                linearLayout.addView(valueTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
        }

        public void set(
            int imageColor,
            int imageResId,
            CharSequence title,
            CharSequence value,
            boolean divider
        ) {
            if (imageResId == 0) {
                imageView.setVisibility(View.GONE);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), imageColor));
                imageView.setImageResource(imageResId);
            }

            textView.setText(title);
            valueTextView.setText(value);

            setWillNotDraw(!(this.divider = divider));
        }

        public void setArrow(Boolean value) {
            if (value == null) {
                arrowView.setVisibility(View.GONE);
            } else {
                arrowView.setVisibility(View.VISIBLE);
                arrowView.animate().rotation(value ? 0 : 180).setDuration(360).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (divider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(64) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY)
            );
        }
    }

    public class CustomCharacterSpan extends MetricAffectingSpan {
        double ratio = 0.5;

        public CustomCharacterSpan() {
        }

        public CustomCharacterSpan(double ratio) {
            this.ratio = ratio;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.baselineShift += (int) (paint.ascent() * ratio);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            paint.baselineShift += (int) (paint.ascent() * ratio);
        }
    }

    private boolean changeStatusBar;

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (progress > .5f && !changeStatusBar) {
            changeStatusBar = true;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
        }
        super.onTransitionAnimationProgress(isOpen, progress);
    }

    @Override
    public boolean isLightStatusBar() {
        if (!changeStatusBar) {
            return super.isLightStatusBar();
        }
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarActionModeDefault)) > 0.721f;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (event.getY() <= ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(48)) {
            return true;
        }
        return pager.getCurrentPosition() == 0;
    }
}

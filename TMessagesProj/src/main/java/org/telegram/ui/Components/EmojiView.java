/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.AnimationCompat.ViewProxy;
import org.telegram.android.Emoji;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.query.StickersQuery;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.StickerEmojiCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class EmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        boolean onBackspace();
        void onEmojiSelected(String emoji);
        void onStickerSelected(TLRPC.Document sticker);
    }

    private ArrayList<EmojiGridAdapter> adapters = new ArrayList<>();
    private HashMap<Long, Integer> stickersUseHistory = new HashMap<>();
    private ArrayList<TLRPC.Document> recentStickers = new ArrayList<>();
    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();

    private int[] icons = {
            R.drawable.ic_emoji_recent,
            R.drawable.ic_emoji_smile,
            R.drawable.ic_emoji_flower,
            R.drawable.ic_emoji_bell,
            R.drawable.ic_emoji_car,
            R.drawable.ic_emoji_symbol,
            R.drawable.ic_emoji_sticker};

    private Listener listener;
    private ViewPager pager;
    private FrameLayout recentsWrap;
    private FrameLayout stickersWrap;
    private ArrayList<GridView> views = new ArrayList<>();
    private ImageView backspaceButton;
    private StickersGridAdapter stickersGridAdapter;
    private LinearLayout pagerSlidingTabStripContainer;
    private ScrollSlidingTabStrip scrollSlidingTabStrip;

    private int oldWidth;
    private int lastNotifyWidth;

    private boolean backspacePressed;
    private boolean backspaceOnce;
    private boolean showStickers;

    public EmojiView(boolean needStickers, Context context) {
        super(context);

        showStickers = needStickers;

        for (int i = 0; i < Emoji.data.length; i++) {
            GridView gridView = new GridView(context);
            if (AndroidUtilities.isTablet()) {
                gridView.setColumnWidth(AndroidUtilities.dp(60));
            } else {
                gridView.setColumnWidth(AndroidUtilities.dp(45));
            }
            gridView.setNumColumns(-1);
            views.add(gridView);

            EmojiGridAdapter emojiGridAdapter = new EmojiGridAdapter(Emoji.data[i]);
            gridView.setAdapter(emojiGridAdapter);
            AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);
            adapters.add(emojiGridAdapter);
        }

        if (showStickers) {
            StickersQuery.checkStickers();
            GridView gridView = new GridView(context);
            gridView.setColumnWidth(AndroidUtilities.dp(72));
            gridView.setNumColumns(-1);
            gridView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            gridView.setClipToPadding(false);
            views.add(gridView);
            stickersGridAdapter = new StickersGridAdapter(context);
            gridView.setAdapter(stickersGridAdapter);
            AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);

            stickersWrap = new FrameLayout(context);
            stickersWrap.addView(gridView);

            TextView textView = new TextView(context);
            textView.setText(LocaleController.getString("NoStickers", R.string.NoStickers));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            textView.setTextColor(0xff888888);
            stickersWrap.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            gridView.setEmptyView(textView);

            scrollSlidingTabStrip = new ScrollSlidingTabStrip(context) {

                boolean startedScroll;
                float lastX;
                float lastTranslateX;
                boolean first = true;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    if (Build.VERSION.SDK_INT >= 11) {
                        if (first) {
                            first = false;
                            lastX = ev.getX();
                        }
                        float newTranslationX = ViewProxy.getTranslationX(scrollSlidingTabStrip);
                        if (scrollSlidingTabStrip.getScrollX() == 0 && newTranslationX == 0) {
                            if (!startedScroll && lastX - ev.getX() < 0) {
                                if (pager.beginFakeDrag()) {
                                    startedScroll = true;
                                    lastTranslateX = ViewProxy.getTranslationX(scrollSlidingTabStrip);
                                }
                            } else if (startedScroll && lastX - ev.getX() > 0) {
                                if (pager.isFakeDragging()) {
                                    pager.endFakeDrag();
                                    startedScroll = false;
                                }
                            }
                        }
                        if (startedScroll) {
                            int dx = (int) (ev.getX() - lastX + newTranslationX - lastTranslateX);
                            try {
                                pager.fakeDragBy(dx);
                                lastTranslateX = newTranslationX;
                            } catch (Exception e) {
                                try {
                                    pager.endFakeDrag();
                                } catch (Exception e2) {
                                    //don't promt
                                }
                                startedScroll = false;
                                FileLog.e("tmessages", e);
                            }
                        }
                        lastX = ev.getX();
                        if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
                            first = true;
                            if (startedScroll) {
                                pager.endFakeDrag();
                                startedScroll = false;
                            }
                        }
                        return startedScroll || super.onTouchEvent(ev);
                    }
                    return super.onTouchEvent(ev);
                }
            };
            scrollSlidingTabStrip.setUnderlineHeight(AndroidUtilities.dp(1));
            scrollSlidingTabStrip.setIndicatorColor(0xffe2e5e7);
            scrollSlidingTabStrip.setUnderlineColor(0xffe2e5e7);
            scrollSlidingTabStrip.setVisibility(INVISIBLE);
            addView(scrollSlidingTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            ViewProxy.setTranslationX(scrollSlidingTabStrip, AndroidUtilities.displaySize.x);
            updateStickerTabs();
            scrollSlidingTabStrip.setDelegate(new ScrollSlidingTabStrip.ScrollSlidingTabStripDelegate() {
                @Override
                public void onPageSelected(int page) {
                    if (page == 0) {
                        pager.setCurrentItem(0);
                        return;
                    } else if (page == 1 && !recentStickers.isEmpty()) {
                        views.get(6).setSelection(0);
                        return;
                    }
                    int index = page - (recentStickers.isEmpty() ? 1 : 2);
                    if (index >= stickerSets.size()) {
                        index = stickerSets.size() - 1;
                    }
                    views.get(6).setSelection(stickersGridAdapter.getPositionForPack(stickerSets.get(index)));
                }
            });

            gridView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    int count = view.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = view.getChildAt(a);
                        if (child.getHeight() + child.getTop() < AndroidUtilities.dp(5)) {
                            firstVisibleItem++;
                        } else {
                            break;
                        }
                    }
                    scrollSlidingTabStrip.onPageScrolled(stickersGridAdapter.getTabForPosition(firstVisibleItem) + 1, 0);
                }
            });
        }

        setBackgroundColor(0xfff5f6f7);

        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        pager.setAdapter(new EmojiPagesAdapter());

        pagerSlidingTabStripContainer = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        pagerSlidingTabStripContainer.setOrientation(LinearLayout.HORIZONTAL);
        pagerSlidingTabStripContainer.setBackgroundColor(0xfff5f6f7);
        addView(pagerSlidingTabStripContainer, LayoutHelper.createFrame(LayoutParams.MATCH_PARENT, 48));

        PagerSlidingTabStrip pagerSlidingTabStrip = new PagerSlidingTabStrip(context);
        pagerSlidingTabStrip.setViewPager(pager);
        pagerSlidingTabStrip.setShouldExpand(true);
        pagerSlidingTabStrip.setIndicatorHeight(AndroidUtilities.dp(2));
        pagerSlidingTabStrip.setUnderlineHeight(AndroidUtilities.dp(1));
        pagerSlidingTabStrip.setIndicatorColor(0xff2b96e2);
        pagerSlidingTabStrip.setUnderlineColor(0xffe2e5e7);
        pagerSlidingTabStripContainer.addView(pagerSlidingTabStrip, LayoutHelper.createLinear(0, 48, 1.0f));
        pagerSlidingTabStrip.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                EmojiView.this.onPageScrolled(position, getMeasuredWidth(), positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        pagerSlidingTabStripContainer.addView(frameLayout, LayoutHelper.createLinear(52, 48));

        backspaceButton = new ImageView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    backspacePressed = true;
                    backspaceOnce = false;
                    postBackspaceRunnable(350);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                    backspacePressed = false;
                    if (!backspaceOnce) {
                        if (EmojiView.this.listener != null && EmojiView.this.listener.onBackspace()) {
                            backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    }
                }
                super.onTouchEvent(event);
                return true;
            }
        };
        backspaceButton.setImageResource(R.drawable.ic_smiles_backspace);
        backspaceButton.setBackgroundResource(R.drawable.ic_emoji_backspace);
        backspaceButton.setScaleType(ImageView.ScaleType.CENTER);
        frameLayout.addView(backspaceButton, LayoutHelper.createFrame(52, 48));

        View view = new View(context);
        view.setBackgroundColor(0xffe2e5e7);
        frameLayout.addView(view, LayoutHelper.createFrame(52, 1, Gravity.LEFT | Gravity.BOTTOM));

        recentsWrap = new FrameLayout(context);
        recentsWrap.addView(views.get(0));

        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString("NoRecent", R.string.NoRecent));
        textView.setTextSize(18);
        textView.setTextColor(0xff888888);
        textView.setGravity(Gravity.CENTER);
        recentsWrap.addView(textView);
        views.get(0).setEmptyView(textView);

        addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 48, 0, 0));

        loadRecents();

        if (Emoji.data[0] == null || Emoji.data[0].length == 0) {
            pager.setCurrentItem(1);
        }
    }

    private void onPageScrolled(int position, int width, int positionOffsetPixels) {
        if (scrollSlidingTabStrip == null) {
            return;
        }

        if (width == 0) {
            width = AndroidUtilities.displaySize.x;
        }

        int margin = 0;
        if (position == 5) {
            margin = -positionOffsetPixels;
        } else if (position == 6) {
            margin = -width;
        }

        if (ViewProxy.getTranslationX(pagerSlidingTabStripContainer) != margin) {
            ViewProxy.setTranslationX(pagerSlidingTabStripContainer, margin);
            ViewProxy.setTranslationX(scrollSlidingTabStrip, width + margin);
            scrollSlidingTabStrip.setVisibility(margin < 0 ? VISIBLE : INVISIBLE);
            if (Build.VERSION.SDK_INT < 11) {
                if (margin <= -width) {
                    pagerSlidingTabStripContainer.clearAnimation();
                    pagerSlidingTabStripContainer.setVisibility(GONE);
                } else {
                    pagerSlidingTabStripContainer.setVisibility(VISIBLE);
                }
            }
        } else if (Build.VERSION.SDK_INT < 11 && pagerSlidingTabStripContainer.getVisibility() == GONE) {
            pagerSlidingTabStripContainer.clearAnimation();
            pagerSlidingTabStripContainer.setVisibility(GONE);
        }
    }

    private void postBackspaceRunnable(final int time) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (!backspacePressed) {
                    return;
                }
                if (EmojiView.this.listener != null && EmojiView.this.listener.onBackspace()) {
                    backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                backspaceOnce = true;
                postBackspaceRunnable(Math.max(50, time - 100));
            }
        }, time);
    }

    private void addToRecent(long code) {
        if (pager.getCurrentItem() == 0) {
            return;
        }
        ArrayList<Long> recent = new ArrayList<>();
        long[] currentRecent = Emoji.data[0];
        boolean was = false;
        for (long aCurrentRecent : currentRecent) {
            if (code == aCurrentRecent) {
                recent.add(0, code);
                was = true;
            } else {
                recent.add(aCurrentRecent);
            }
        }
        if (!was) {
            recent.add(0, code);
        }
        Emoji.data[0] = new long[Math.min(recent.size(), 50)];
        for (int q = 0; q < Emoji.data[0].length; q++) {
            Emoji.data[0][q] = recent.get(q);
        }
        adapters.get(0).data = Emoji.data[0];
        adapters.get(0).notifyDataSetChanged();
        saveRecents();
    }

    private String convert(long paramLong) {
        String str = "";
        for (int i = 0; ; i++) {
            if (i >= 4) {
                return str;
            }
            int j = (int) (0xFFFF & paramLong >> 16 * (3 - i));
            if (j != 0) {
                str = str + (char) j;
            }
        }
    }

    private void saveRecents() {
        ArrayList<Long> arrayList = new ArrayList<>(Emoji.data[0].length);
        for (int j = 0; j < Emoji.data[0].length; j++) {
            arrayList.add(Emoji.data[0][j]);
        }
        getContext().getSharedPreferences("emoji", 0).edit().putString("recents", TextUtils.join(",", arrayList)).commit();
    }

    private void saveRecentStickers() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<Long, Integer> entry : stickersUseHistory.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("stickers", stringBuilder.toString()).commit();
    }

    private void sortStickers() {
        if (StickersQuery.getStickerSets().isEmpty()) {
            recentStickers.clear();
            return;
        }
        recentStickers.clear();
        HashMap<Long, Integer> hashMap = new HashMap<>();
        for (HashMap.Entry<Long, Integer> entry : stickersUseHistory.entrySet()) {
            TLRPC.Document sticker = StickersQuery.getStickerById(entry.getKey());
            if (sticker != null) {
                recentStickers.add(sticker);
                hashMap.put(sticker.id, entry.getValue());
            }
        }
        if (!stickersUseHistory.isEmpty()) {
            stickersUseHistory = hashMap;
            saveRecents();
        } else {
            stickersUseHistory = hashMap;
        }
        Collections.sort(recentStickers, new Comparator<TLRPC.Document>() {
            @Override
            public int compare(TLRPC.Document lhs, TLRPC.Document rhs) {
                Integer count1 = stickersUseHistory.get(lhs.id);
                Integer count2 = stickersUseHistory.get(rhs.id);
                if (count1 == null) {
                    count1 = 0;
                }
                if (count2 == null) {
                    count2 = 0;
                }
                if (count1 > count2) {
                    return -1;
                } else if (count1 < count2) {
                    return 1;
                }
                return 0;
            }
        });
        while (recentStickers.size() > 20) {
            recentStickers.remove(recentStickers.size() - 1);
        }
    }

    private void updateStickerTabs() {
        scrollSlidingTabStrip.removeTabs();
        scrollSlidingTabStrip.addIconTab(R.drawable.ic_emoji_smile);
        if (!recentStickers.isEmpty()) {
            scrollSlidingTabStrip.addIconTab(R.drawable.ic_smiles_recent);
        }
        stickerSets.clear();
        ArrayList<TLRPC.TL_messages_stickerSet> packs = StickersQuery.getStickerSets();
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if ((pack.set.flags & 2) != 0 || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets.add(pack);
            scrollSlidingTabStrip.addStickerTab(pack.documents.get(0));
        }
        scrollSlidingTabStrip.updateTabStyles();
    }

    public void loadRecents() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        String str = preferences.getString("recents", "");
        try {
            if (str != null && str.length() > 0) {
                String[] args = str.split(",");
                Emoji.data[0] = new long[args.length];
                for (int i = 0; i < args.length; i++) {
                    Emoji.data[0][i] = Long.parseLong(args[i]);
                }
            } else {
                Emoji.data[0] = new long[]{0x00000000D83DDE02L, 0x00000000D83DDE18L, 0x0000000000002764L, 0x00000000D83DDE0DL, 0x00000000D83DDE0AL, 0x00000000D83DDE01L,
                        0x00000000D83DDC4DL, 0x000000000000263AL, 0x00000000D83DDE14L, 0x00000000D83DDE04L, 0x00000000D83DDE2DL, 0x00000000D83DDC8BL,
                        0x00000000D83DDE12L, 0x00000000D83DDE33L, 0x00000000D83DDE1CL, 0x00000000D83DDE48L, 0x00000000D83DDE09L, 0x00000000D83DDE03L,
                        0x00000000D83DDE22L, 0x00000000D83DDE1DL, 0x00000000D83DDE31L, 0x00000000D83DDE21L, 0x00000000D83DDE0FL, 0x00000000D83DDE1EL,
                        0x00000000D83DDE05L, 0x00000000D83DDE1AL, 0x00000000D83DDE4AL, 0x00000000D83DDE0CL, 0x00000000D83DDE00L, 0x00000000D83DDE0BL,
                        0x00000000D83DDE06L, 0x00000000D83DDC4CL, 0x00000000D83DDE10L, 0x00000000D83DDE15L};
            }
            adapters.get(0).data = Emoji.data[0];
            adapters.get(0).notifyDataSetChanged();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        if (showStickers) {
            try {
                stickersUseHistory.clear();
                str = preferences.getString("stickers", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        stickersUseHistory.put(Long.parseLong(args2[0]), Integer.parseInt(args2[1]));
                    }
                }
                sortStickers();
                updateStickerTabs();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) pagerSlidingTabStripContainer.getLayoutParams();
        FrameLayout.LayoutParams layoutParams1 = null;
        layoutParams.width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (scrollSlidingTabStrip != null) {
            layoutParams1 = (FrameLayout.LayoutParams) scrollSlidingTabStrip.getLayoutParams();
            if (layoutParams1 != null) {
                layoutParams1.width = layoutParams.width;
            }
        }
        if (layoutParams.width != oldWidth) {
            if (scrollSlidingTabStrip != null && layoutParams1 != null) {
                onPageScrolled(pager.getCurrentItem(), layoutParams.width, 0);
                scrollSlidingTabStrip.setLayoutParams(layoutParams1);
            }
            pagerSlidingTabStripContainer.setLayoutParams(layoutParams);
            oldWidth = layoutParams.width;
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            if (stickersGridAdapter != null) {
                stickersGridAdapter.notifyDataSetChanged();
            }
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void invalidateViews() {
        for (GridView gridView : views) {
            if (gridView != null) {
                gridView.invalidateViews();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != GONE && stickersGridAdapter != null) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
            sortStickers();
            updateStickerTabs();
            stickersGridAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.stickersDidLoaded) {
            updateStickerTabs();
            stickersGridAdapter.notifyDataSetChanged();
        }
    }

    private class StickersGridAdapter extends BaseAdapter {

        private Context context;
        private int stickersPerRow;
        private HashMap<Integer, TLRPC.TL_messages_stickerSet> rowStartPack = new HashMap<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Integer> packStartRow = new HashMap<>();
        private HashMap<Integer, TLRPC.Document> cache = new HashMap<>();
        private int totalItems;

        public StickersGridAdapter(Context context) {
            this.context = context;
        }

        public int getCount() {
            return totalItems != 0 ? totalItems + 1 : 0;
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public long getItemId(int i) {
            return NO_ID;
        }

        public int getPositionForPack(TLRPC.TL_messages_stickerSet stickerSet) {
            return packStartRow.get(stickerSet) * stickersPerRow;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return cache.get(position) != null;
        }

        @Override
        public int getItemViewType(int position) {
            if (cache.get(position) != null) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        public int getTabForPosition(int position) {
            if (stickersPerRow == 0) {
                int width = getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                stickersPerRow = width / AndroidUtilities.dp(72);
            }
            int row = position / stickersPerRow;
            TLRPC.TL_messages_stickerSet pack = rowStartPack.get(row);
            if (pack == null) {
                return 0;
            }
            return stickerSets.indexOf(pack) + (recentStickers.isEmpty() ? 0 : 1);
        }

        public View getView(int i, View view, ViewGroup viewGroup) {
            TLRPC.Document sticker = cache.get(i);
            if (sticker != null) {
                if (view == null) {
                    view = new StickerEmojiCell(context) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    view.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (listener != null) {
                                TLRPC.Document document = ((StickerEmojiCell) v).getSticker();
                                Integer count = stickersUseHistory.get(document.id);
                                if (count == null) {
                                    count = 0;
                                }
                                if (count == 0 && stickersUseHistory.size() > 19) {
                                    for (int a = recentStickers.size() - 1; a >= 0; a--) {
                                        TLRPC.Document sticker = recentStickers.get(a);
                                        stickersUseHistory.remove(sticker.id);
                                        recentStickers.remove(a);
                                        if (stickersUseHistory.size() <= 19) {
                                            break;
                                        }
                                    }
                                }
                                stickersUseHistory.put(document.id, ++count);
                                saveRecentStickers();
                                listener.onStickerSelected(document);
                            }
                        }
                    });
                }
                ((StickerEmojiCell) view).setSticker(sticker, false);
            } else {
                if (view == null) {
                    view = new EmptyCell(context);
                }
                if (i == totalItems) {
                    int row = (i - 1) / stickersPerRow;
                    TLRPC.TL_messages_stickerSet pack = rowStartPack.get(row);
                    if (pack == null) {
                        ((EmptyCell) view).setHeight(1);
                    } else {
                        int height = pager.getHeight() - (int) Math.ceil(pack.documents.size() / (float) stickersPerRow) * AndroidUtilities.dp(82);
                        ((EmptyCell) view).setHeight(height > 0 ? height : 1);
                    }
                } else {
                    ((EmptyCell) view).setHeight(AndroidUtilities.dp(82));
                }
            }
            return view;
        }

        @Override
        public void notifyDataSetChanged() {
            int width = getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            stickersPerRow = width / AndroidUtilities.dp(72);
            rowStartPack.clear();
            packStartRow.clear();
            cache.clear();
            totalItems = 0;
            ArrayList<TLRPC.TL_messages_stickerSet> packs = stickerSets;
            for (int a = -1; a < packs.size(); a++) {
                ArrayList<TLRPC.Document> documents;
                TLRPC.TL_messages_stickerSet pack = null;
                int startRow = totalItems / stickersPerRow;
                if (a == -1) {
                    documents = recentStickers;
                } else {
                    pack = packs.get(a);
                    documents = pack.documents;
                    packStartRow.put(pack, startRow);
                }
                if (documents.isEmpty()) {
                    continue;
                }
                int count = (int) Math.ceil(documents.size() / (float) stickersPerRow);
                for (int b = 0; b < documents.size(); b++) {
                    cache.put(b + totalItems, documents.get(b));
                }
                totalItems += count * stickersPerRow;
                for (int b = 0; b < count; b++) {
                    rowStartPack.put(startRow + b, pack);
                }
            }
            super.notifyDataSetChanged();
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    private class EmojiGridAdapter extends BaseAdapter {
        long[] data;

        public EmojiGridAdapter(long[] arg2) {
            this.data = arg2;
        }

        public int getCount() {
            return data.length;
        }

        public Object getItem(int i) {
            return null;
        }

        public long getItemId(int i) {
            return data[i];
        }

        public View getView(int i, View view, ViewGroup paramViewGroup) {
            ImageView imageView = (ImageView)view;
            if (imageView == null) {
                imageView = new ImageView(EmojiView.this.getContext()) {
                    public void onMeasure(int paramAnonymousInt1, int paramAnonymousInt2) {
                        setMeasuredDimension(View.MeasureSpec.getSize(paramAnonymousInt1), View.MeasureSpec.getSize(paramAnonymousInt1));
                    }
                };
                imageView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (EmojiView.this.listener != null) {
                            EmojiView.this.listener.onEmojiSelected(EmojiView.this.convert((Long)view.getTag()));
                        }
                        EmojiView.this.addToRecent((Long)view.getTag());
                    }
                });
                imageView.setBackgroundResource(R.drawable.list_selector);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
            }
            imageView.setImageDrawable(Emoji.getEmojiBigDrawable(data[i]));
            imageView.setTag(data[i]);
            return imageView;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

        public void destroyItem(ViewGroup viewGroup, int position, Object object) {
            View view;
            if (position == 0) {
                view = recentsWrap;
            } else if (position == 6) {
                view = stickersWrap;
            } else {
                view = views.get(position);
            }
            viewGroup.removeView(view);
        }

        public int getCount() {
            return views.size();
        }

        public int getPageIconResId(int paramInt) {
            return icons[paramInt];
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view;
            if (position == 0) {
                view = recentsWrap;
            } else if (position == 6) {
                view = stickersWrap;
            } else {
                view = views.get(position);
            }
            viewGroup.addView(view);
            return view;
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }
}
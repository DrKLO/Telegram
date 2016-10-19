/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.StickerPreviewViewer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class EmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        boolean onBackspace();
        void onEmojiSelected(String emoji);
        void onStickerSelected(TLRPC.Document sticker);
        void onStickersSettingsClick();
        void onGifSelected(TLRPC.Document gif);
        void onGifTab(boolean opened);
        void onStickersTab(boolean opened);
        void onClearEmojiRecent();
        void onShowStickerSet(TLRPC.StickerSetCovered stickerSet);
        void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet);
        void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet);
    }

    private static final Field superListenerField;
    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            /* ignored */
        }
        superListenerField = f;
    }
    private static final ViewTreeObserver.OnScrollChangedListener NOP = new ViewTreeObserver.OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
            /* do nothing */
        }
    };

    private static String addColorToCode(String code, String color) {
        String end = null;
        if (code.endsWith("\u200D\u2640") || code.endsWith("\u200D\u2642")) {
            end = code.substring(code.length() - 2);
            code = code.substring(0, code.length() - 2);
        }
        code += color;
        if (end != null) {
            code += end;
        }
        return code;
    }

    private class ImageViewEmoji extends ImageView {

        private boolean touched;
        private float lastX;
        private float lastY;
        private float touchedX;
        private float touchedY;

        public ImageViewEmoji(Context context) {
            super(context);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendEmoji(null);
                }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    String code = (String) view.getTag();
                    if (EmojiData.emojiColoredMap.containsKey(code)) {
                        touched = true;
                        touchedX = lastX;
                        touchedY = lastY;

                        String color = emojiColor.get(code);
                        if (color != null) {
                            switch (color) {
                                case "\uD83C\uDFFB":
                                    pickerView.setSelection(1);
                                    break;
                                case "\uD83C\uDFFC":
                                    pickerView.setSelection(2);
                                    break;
                                case "\uD83C\uDFFD":
                                    pickerView.setSelection(3);
                                    break;
                                case "\uD83C\uDFFE":
                                    pickerView.setSelection(4);
                                    break;
                                case "\uD83C\uDFFF":
                                    pickerView.setSelection(5);
                                    break;
                            }
                        } else {
                            pickerView.setSelection(0);
                        }
                        view.getLocationOnScreen(location);
                        int x = emojiSize * pickerView.getSelection() + AndroidUtilities.dp(4 * pickerView.getSelection() - (AndroidUtilities.isTablet() ? 5 : 1));
                        if (location[0] - x < AndroidUtilities.dp(5)) {
                            x += (location[0] - x) - AndroidUtilities.dp(5);
                        } else if (location[0] - x + popupWidth > AndroidUtilities.displaySize.x - AndroidUtilities.dp(5)) {
                            x += (location[0] - x + popupWidth) - (AndroidUtilities.displaySize.x - AndroidUtilities.dp(5));
                        }
                        int xOffset = -x;
                        int yOffset = view.getTop() < 0 ? view.getTop() : 0;

                        pickerView.setEmoji(code, AndroidUtilities.dp(AndroidUtilities.isTablet() ? 30 : 22) - xOffset + (int) AndroidUtilities.dpf2(0.5f));

                        pickerViewPopup.setFocusable(true);
                        pickerViewPopup.showAsDropDown(view, xOffset, -view.getMeasuredHeight() - popupHeight + (view.getMeasuredHeight() - emojiSize) / 2 - yOffset);
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    } else if (pager.getCurrentItem() == 0) {
                        listener.onClearEmojiRecent();
                    }
                    return false;
                }
            });
            setBackgroundResource(R.drawable.list_selector);
            setScaleType(ImageView.ScaleType.CENTER);
        }

        private void sendEmoji(String override) {
            String code = override != null ? override : (String) getTag();
            if (override == null) {
                if (pager.getCurrentItem() != 0) {
                    String color = emojiColor.get(code);
                    if (color != null) {
                        code = addColorToCode(code, color);
                    }
                }
                Integer count = emojiUseHistory.get(code);
                if (count == null) {
                    count = 0;
                }
                if (count == 0 && emojiUseHistory.size() > 50) {
                    for (int a = recentEmoji.size() - 1; a >= 0; a--) {
                        String emoji = recentEmoji.get(a);
                        emojiUseHistory.remove(emoji);
                        recentEmoji.remove(a);
                        if (emojiUseHistory.size() <= 50) {
                            break;
                        }
                    }
                }
                emojiUseHistory.put(code, ++count);
                if (pager.getCurrentItem() != 0) {
                    sortEmoji();
                }
                saveRecentEmoji();
                adapters.get(0).notifyDataSetChanged();
                if (listener != null) {
                    listener.onEmojiSelected(Emoji.fixEmoji(code));
                }
            } else {
                if (listener != null) {
                    listener.onEmojiSelected(Emoji.fixEmoji(override));
                }
            }
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (touched) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (pickerViewPopup != null && pickerViewPopup.isShowing()) {
                        pickerViewPopup.dismiss();

                        String color = null;
                        switch (pickerView.getSelection()) {
                            case 1:
                                color = "\uD83C\uDFFB";
                                break;
                            case 2:
                                color = "\uD83C\uDFFC";
                                break;
                            case 3:
                                color = "\uD83C\uDFFD";
                                break;
                            case 4:
                                color = "\uD83C\uDFFE";
                                break;
                            case 5:
                                color = "\uD83C\uDFFF";
                                break;
                        }
                        String code = (String) getTag();
                        if (pager.getCurrentItem() != 0) {
                            if (color != null) {
                                emojiColor.put(code, color);
                                code = addColorToCode(code, color);
                            } else {
                                emojiColor.remove(code);
                            }
                            setImageDrawable(Emoji.getEmojiBigDrawable(code));
                            sendEmoji(null);
                            saveEmojiColors();
                        } else {
                            sendEmoji(code + (color != null ? color : ""));
                        }
                    }
                    touched = false;
                    touchedX = -10000;
                    touchedY = -10000;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    boolean ignore = false;
                    if (touchedX != -10000) {
                        if (Math.abs(touchedX - event.getX()) > AndroidUtilities.getPixelsInCM(0.2f, true) || Math.abs(touchedY - event.getY()) > AndroidUtilities.getPixelsInCM(0.2f, false)) {
                            touchedX = -10000;
                            touchedY = -10000;
                        } else {
                            ignore = true;
                        }
                    }
                    if (!ignore) {
                        getLocationOnScreen(location);
                        float x = location[0] + event.getX();
                        pickerView.getLocationOnScreen(location);
                        x -= location[0] + AndroidUtilities.dp(3);
                        int position = (int) (x / (emojiSize + AndroidUtilities.dp(4)));
                        if (position < 0) {
                            position = 0;
                        } else if (position > 5) {
                            position = 5;
                        }
                        pickerView.setSelection(position);
                    }
                }
            }
            lastX = event.getX();
            lastY = event.getY();
            return super.onTouchEvent(event);
        }
    }

    private class EmojiPopupWindow extends PopupWindow {

        private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
        private ViewTreeObserver mViewTreeObserver;

        public EmojiPopupWindow() {
            super();
            init();
        }

        public EmojiPopupWindow(Context context) {
            super(context);
            init();
        }

        public EmojiPopupWindow(int width, int height) {
            super(width, height);
            init();
        }

        public EmojiPopupWindow(View contentView) {
            super(contentView);
            init();
        }

        public EmojiPopupWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
            init();
        }

        public EmojiPopupWindow(View contentView, int width, int height) {
            super(contentView, width, height);
            init();
        }

        private void init() {
            if (superListenerField != null) {
                try {
                    mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                    superListenerField.set(this, NOP);
                } catch (Exception e) {
                    mSuperScrollListener = null;
                }
            }
        }

        private void unregisterListener() {
            if (mSuperScrollListener != null && mViewTreeObserver != null) {
                if (mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                mViewTreeObserver = null;
            }
        }

        private void registerListener(View anchor) {
            if (mSuperScrollListener != null) {
                ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
                if (vto != mViewTreeObserver) {
                    if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                        mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                    }
                    if ((mViewTreeObserver = vto) != null) {
                        vto.addOnScrollChangedListener(mSuperScrollListener);
                    }
                }
            }
        }

        @Override
        public void showAsDropDown(View anchor, int xoff, int yoff) {
            try {
                super.showAsDropDown(anchor, xoff, yoff);
                registerListener(anchor);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }

        @Override
        public void update(View anchor, int xoff, int yoff, int width, int height) {
            super.update(anchor, xoff, yoff, width, height);
            registerListener(anchor);
        }

        @Override
        public void update(View anchor, int width, int height) {
            super.update(anchor, width, height);
            registerListener(anchor);
        }

        @Override
        public void showAtLocation(View parent, int gravity, int x, int y) {
            super.showAtLocation(parent, gravity, x, y);
            unregisterListener();
        }

        @Override
        public void dismiss() {
            setFocusable(false);
            try {
                super.dismiss();
            } catch (Exception e) {
                //don't promt
            }
            unregisterListener();
        }
    }

    private class EmojiColorPickerView extends View {

        private Drawable backgroundDrawable;
        private Drawable arrowDrawable;
        private String currentEmoji;
        private int arrowX;
        private int selection;
        private Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();

        public void setEmoji(String emoji, int arrowPosition) {
            currentEmoji = emoji;
            arrowX = arrowPosition;
            rectPaint.setColor(0x2f000000);
            invalidate();
        }

        public String getEmoji() {
            return currentEmoji;
        }

        public void setSelection(int position) {
            if (selection == position) {
                return;
            }
            selection = position;
            invalidate();
        }

        public int getSelection() {
            return selection;
        }

        public EmojiColorPickerView(Context context) {
            super(context);

            backgroundDrawable = getResources().getDrawable(R.drawable.stickers_back_all);
            arrowDrawable = getResources().getDrawable(R.drawable.stickers_back_arrow);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 52));
            backgroundDrawable.draw(canvas);

            arrowDrawable.setBounds(arrowX - AndroidUtilities.dp(9), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 55.5f : 47.5f), arrowX + AndroidUtilities.dp(9), AndroidUtilities.dp((AndroidUtilities.isTablet() ? 55.5f : 47.5f) + 8));
            arrowDrawable.draw(canvas);

            if (currentEmoji != null) {
                String code;
                for (int a = 0; a < 6; a++) {
                    int x = emojiSize * a + AndroidUtilities.dp(5 + 4 * a);
                    int y = AndroidUtilities.dp(9);
                    if (selection == a) {
                        rect.set(x, y - (int) AndroidUtilities.dpf2(3.5f), x + emojiSize, y + emojiSize + AndroidUtilities.dp(3));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), rectPaint);
                    }
                    code = currentEmoji;
                    if (a != 0) {
                        String color;
                        switch (a) {
                            case 1:
                                color = "\uD83C\uDFFB";
                                break;
                            case 2:
                                color = "\uD83C\uDFFC";
                                break;
                            case 3:
                                color = "\uD83C\uDFFD";
                                break;
                            case 4:
                                color = "\uD83C\uDFFE";
                                break;
                            case 5:
                                color = "\uD83C\uDFFF";
                                break;
                            default:
                                color = "";
                        }
                        code = addColorToCode(code, color);
                    }
                    Drawable drawable = Emoji.getEmojiBigDrawable(code);
                    if (drawable != null) {
                        drawable.setBounds(x, y, x + emojiSize, y + emojiSize);
                        drawable.draw(canvas);
                    }
                }
            }
        }
    }

    private ArrayList<EmojiGridAdapter> adapters = new ArrayList<>();
    private HashMap<String, Integer> emojiUseHistory = new HashMap<>();
    private static HashMap<String, String> emojiColor = new HashMap<>();
    private ArrayList<String> recentEmoji = new ArrayList<>();
    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();

    private ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private ArrayList<TLRPC.Document> recentStickers = new ArrayList<>();

    private Drawable dotDrawable;

    private int[] icons = {
            R.drawable.ic_emoji_recent,
            R.drawable.ic_emoji_smile,
            R.drawable.ic_emoji_flower,
            R.drawable.ic_emoji_bell,
            R.drawable.ic_emoji_car,
            R.drawable.ic_emoji_symbol,
            R.drawable.ic_smiles2_stickers};

    private Listener listener;
    private ViewPager pager;
    private FrameLayout stickersWrap;
    private ArrayList<View> views = new ArrayList<>();
    private ArrayList<GridView> emojiGrids = new ArrayList<>();
    private ImageView backspaceButton;
    private StickersGridAdapter stickersGridAdapter;
    private LinearLayout emojiTab;
    private ScrollSlidingTabStrip stickersTab;
    private RecyclerListView stickersGridView;
    private GridLayoutManager stickersLayoutManager;
    private TextView stickersEmptyView;
    private RecyclerListView gifsGridView;
    private ExtendedGridLayoutManager flowLayoutManager;
    private GifsAdapter gifsAdapter;
    private RecyclerListView trendingGridView;
    private GridLayoutManager trendingLayoutManager;
    private TrendingGridAdapter trendingGridAdapter;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private PagerSlidingTabStrip pagerSlidingTabStrip;

    private HashMap<Long, TLRPC.StickerSetCovered> installingStickerSets = new HashMap<>();
    private HashMap<Long, TLRPC.StickerSetCovered> removingStickerSets = new HashMap<>();
    private boolean trendingLoaded;

    private int currentPage;

    private EmojiColorPickerView pickerView;
    private EmojiPopupWindow pickerViewPopup;
    private int popupWidth;
    private int popupHeight;
    private int emojiSize;
    private int location[] = new int[2];
    private int stickersTabOffset;
    private int recentTabBum = -2;
    private int gifTabNum = -2;
    private int trendingTabNum = -2;
    private boolean switchToGifTab;

    private boolean isLayout;
    private int currentBackgroundType = -1;
    private Object outlineProvider;

    private int oldWidth;
    private int lastNotifyWidth;

    private boolean backspacePressed;
    private boolean backspaceOnce;
    private boolean showGifs;

    private int minusDy;

    public EmojiView(boolean needStickers, boolean needGif, final Context context) {
        super(context);

        showGifs = needGif;

        dotDrawable = context.getResources().getDrawable(R.drawable.bluecircle);

        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), AndroidUtilities.dp(6));
                }
            };
        }

        for (int i = 0; i < EmojiData.dataColored.length + 1; i++) {
            GridView gridView = new GridView(context);
            if (AndroidUtilities.isTablet()) {
                gridView.setColumnWidth(AndroidUtilities.dp(60));
            } else {
                gridView.setColumnWidth(AndroidUtilities.dp(45));
            }
            gridView.setNumColumns(-1);
            EmojiGridAdapter emojiGridAdapter = new EmojiGridAdapter(i - 1);
            AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);
            gridView.setAdapter(emojiGridAdapter);
            adapters.add(emojiGridAdapter);
            emojiGrids.add(gridView);
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

            views.add(frameLayout);
        }

        if (needStickers) {
            stickersWrap = new FrameLayout(context);

            StickersQuery.checkStickers(StickersQuery.TYPE_IMAGE);
            StickersQuery.checkFeaturedStickers();
            stickersGridView = new RecyclerListView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersGridView, EmojiView.this.getMeasuredHeight());
                    return super.onInterceptTouchEvent(event) || result;
                }

                @Override
                public void setVisibility(int visibility) {
                    if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE || trendingGridView != null && trendingGridView.getVisibility() == VISIBLE) {
                        super.setVisibility(GONE);
                        return;
                    }
                    super.setVisibility(visibility);
                }
            };

            stickersGridView.setLayoutManager(stickersLayoutManager = new GridLayoutManager(context, 5));
            stickersLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position == stickersGridAdapter.totalItems) {
                        return stickersGridAdapter.stickersPerRow;
                    }
                    return 1;
                }
            });
            stickersGridView.setPadding(0, AndroidUtilities.dp(4 + 48), 0, 0);
            stickersGridView.setClipToPadding(false);
            views.add(stickersWrap);
            stickersGridView.setAdapter(stickersGridAdapter = new StickersGridAdapter(context));
            stickersGridView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return StickerPreviewViewer.getInstance().onTouch(event, stickersGridView, EmojiView.this.getMeasuredHeight(), stickersOnItemClickListener);
                }
            });
            stickersOnItemClickListener = new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    if (!(view instanceof StickerEmojiCell)) {
                        return;
                    }
                    StickerPreviewViewer.getInstance().reset();
                    StickerEmojiCell cell = (StickerEmojiCell) view;
                    if (cell.isDisabled()) {
                        return;
                    }
                    cell.disable();
                    listener.onStickerSelected(cell.getSticker());
                }
            };
            stickersGridView.setOnItemClickListener(stickersOnItemClickListener);
            stickersGridView.setGlowColor(0xfff5f6f7);
            stickersWrap.addView(stickersGridView);

            trendingGridView = new RecyclerListView(context);
            trendingGridView.setItemAnimator(null);
            trendingGridView.setLayoutAnimation(null);
            trendingGridView.setLayoutManager(trendingLayoutManager = new GridLayoutManager(context, 5) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            });
            trendingLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (trendingGridAdapter.cache.get(position) instanceof Integer || position == trendingGridAdapter.totalItems) {
                        return trendingGridAdapter.stickersPerRow;
                    }
                    return 1;
                }
            });
            trendingGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    checkStickersTabY(recyclerView, dy);
                }
            });
            trendingGridView.setClipToPadding(false);
            trendingGridView.setPadding(0, AndroidUtilities.dp(48), 0, 0);
            trendingGridView.setAdapter(trendingGridAdapter = new TrendingGridAdapter(context));
            trendingGridView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    TLRPC.StickerSetCovered pack = trendingGridAdapter.positionsToSets.get(position);
                    if (pack != null) {
                        listener.onShowStickerSet(pack);
                    }
                }
            });
            trendingGridAdapter.notifyDataSetChanged();
            trendingGridView.setGlowColor(0xfff5f6f7);
            trendingGridView.setVisibility(GONE);
            stickersWrap.addView(trendingGridView);

            if (needGif) {
                gifsGridView = new RecyclerListView(context);
                gifsGridView.setClipToPadding(false);
                gifsGridView.setPadding(0, AndroidUtilities.dp(48), 0, 0);
                gifsGridView.setLayoutManager(flowLayoutManager = new ExtendedGridLayoutManager(context, 100) {

                    private Size size = new Size();

                    @Override
                    protected Size getSizeForItem(int i) {
                        TLRPC.Document document = recentGifs.get(i);
                        size.width = document.thumb != null && document.thumb.w != 0 ? document.thumb.w : 100;
                        size.height = document.thumb != null && document.thumb.h != 0 ? document.thumb.h : 100;
                        for (int b = 0; b < document.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = document.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                size.width = attribute.w;
                                size.height = attribute.h;
                                break;
                            }
                        }
                        return size;
                    }
                });
                flowLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return flowLayoutManager.getSpanSizeForItem(position);
                    }
                });
                gifsGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                        outRect.left = 0;
                        outRect.top = 0;
                        outRect.bottom = 0;
                        int position = parent.getChildAdapterPosition(view);
                        if (!flowLayoutManager.isFirstRow(position)) {
                            outRect.top = AndroidUtilities.dp(2);
                        }
                        outRect.right = flowLayoutManager.isLastInRow(position) ? 0 : AndroidUtilities.dp(2);
                    }
                });
                gifsGridView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                gifsGridView.setAdapter(gifsAdapter = new GifsAdapter(context));
                gifsGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        checkStickersTabY(recyclerView, dy);
                    }
                });
                gifsGridView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        if (position < 0 || position >= recentGifs.size() || listener == null) {
                            return;
                        }
                        listener.onGifSelected(recentGifs.get(position));
                    }
                });
                gifsGridView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position) {
                        if (position < 0 || position >= recentGifs.size()) {
                            return false;
                        }
                        final TLRPC.Document searchImage = recentGifs.get(position);
                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("DeleteGif", R.string.DeleteGif));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK).toUpperCase(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                StickersQuery.removeRecentGif(searchImage);
                                recentGifs = StickersQuery.getRecentGifs();
                                if (gifsAdapter != null) {
                                    gifsAdapter.notifyDataSetChanged();
                                }
                                if (recentGifs.isEmpty()) {
                                    updateStickerTabs();
                                }
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.show().setCanceledOnTouchOutside(true);
                        return true;
                    }
                });
                gifsGridView.setVisibility(GONE);
                stickersWrap.addView(gifsGridView);
            }

            stickersEmptyView = new TextView(context);
            stickersEmptyView.setText(LocaleController.getString("NoStickers", R.string.NoStickers));
            stickersEmptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            stickersEmptyView.setTextColor(0xff888888);
            stickersWrap.addView(stickersEmptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 48, 0, 0));
            stickersGridView.setEmptyView(stickersEmptyView);

            stickersTab = new ScrollSlidingTabStrip(context) {

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
                    if (first) {
                        first = false;
                        lastX = ev.getX();
                    }
                    float newTranslationX = stickersTab.getTranslationX();
                    if (stickersTab.getScrollX() == 0 && newTranslationX == 0) {
                        if (!startedScroll && lastX - ev.getX() < 0) {
                            if (pager.beginFakeDrag()) {
                                startedScroll = true;
                                lastTranslateX = stickersTab.getTranslationX();
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
            };
            stickersTab.setUnderlineHeight(AndroidUtilities.dp(1));
            stickersTab.setIndicatorColor(0xffe2e5e7);
            stickersTab.setUnderlineColor(0xffe2e5e7);
            stickersTab.setBackgroundColor(0xfff5f6f7);
            stickersTab.setVisibility(INVISIBLE);
            addView(stickersTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            stickersTab.setTranslationX(AndroidUtilities.displaySize.x);
            updateStickerTabs();
            stickersTab.setDelegate(new ScrollSlidingTabStrip.ScrollSlidingTabStripDelegate() {
                @Override
                public void onPageSelected(int page) {
                    if (gifsGridView != null) {
                        if (page == gifTabNum + 1) {
                            if (gifsGridView.getVisibility() != VISIBLE) {
                                listener.onGifTab(true);
                                showGifTab();
                            }
                        } else if (page == trendingTabNum + 1) {
                            if (trendingGridView.getVisibility() != VISIBLE) {
                                showTrendingTab();
                            }
                        } else {
                            if (gifsGridView.getVisibility() == VISIBLE) {
                                listener.onGifTab(false);
                                gifsGridView.setVisibility(GONE);
                                stickersGridView.setVisibility(VISIBLE);
                                stickersEmptyView.setVisibility(stickersGridAdapter.getItemCount() != 0 ? GONE : VISIBLE);
                                saveNewPage();
                            } else if (trendingGridView.getVisibility() == VISIBLE) {
                                trendingGridView.setVisibility(GONE);
                                stickersGridView.setVisibility(VISIBLE);
                                stickersEmptyView.setVisibility(stickersGridAdapter.getItemCount() != 0 ? GONE : VISIBLE);
                                saveNewPage();
                            }
                        }
                    }
                    if (page == 0) {
                        pager.setCurrentItem(0);
                        return;
                    } else {
                        if (page == gifTabNum + 1 || page == trendingTabNum + 1) {
                            return;
                        } else if (page == recentTabBum + 1) {
                            stickersLayoutManager.scrollToPositionWithOffset(0, 0);
                            checkStickersTabY(null, 0);
                            stickersTab.onPageScrolled(recentTabBum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
                            return;
                        }
                    }
                    int index = page - 1 - stickersTabOffset;
                    if (index >= stickerSets.size()) {
                        if (listener != null) {
                            listener.onStickersSettingsClick();
                        }
                        return;
                    }
                    if (index >= stickerSets.size()) {
                        index = stickerSets.size() - 1;
                    }
                    stickersLayoutManager.scrollToPositionWithOffset(stickersGridAdapter.getPositionForPack(stickerSets.get(index)), 0);
                    checkStickersTabY(null, 0);
                    checkScroll();
                }
            });

            stickersGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    checkScroll();
                    checkStickersTabY(recyclerView, dy);
                }
            });
        }

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

        emojiTab = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        emojiTab.setOrientation(LinearLayout.HORIZONTAL);
        addView(emojiTab, LayoutHelper.createFrame(LayoutParams.MATCH_PARENT, 48));

        pagerSlidingTabStrip = new PagerSlidingTabStrip(context);
        pagerSlidingTabStrip.setViewPager(pager);
        pagerSlidingTabStrip.setShouldExpand(true);
        pagerSlidingTabStrip.setIndicatorHeight(AndroidUtilities.dp(2));
        pagerSlidingTabStrip.setUnderlineHeight(AndroidUtilities.dp(1));
        pagerSlidingTabStrip.setIndicatorColor(0xff2b96e2);
        pagerSlidingTabStrip.setUnderlineColor(0xffe2e5e7);
        emojiTab.addView(pagerSlidingTabStrip, LayoutHelper.createLinear(0, 48, 1.0f));
        pagerSlidingTabStrip.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                EmojiView.this.onPageScrolled(position, getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), positionOffsetPixels);
            }

            @Override
            public void onPageSelected(int position) {
                saveNewPage();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        emojiTab.addView(frameLayout, LayoutHelper.createLinear(52, 48));

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
                        if (listener != null && listener.onBackspace()) {
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

        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString("NoRecent", R.string.NoRecent));
        textView.setTextSize(18);
        textView.setTextColor(0xff888888);
        textView.setGravity(Gravity.CENTER);
        textView.setClickable(false);
        textView.setFocusable(false);
        ((FrameLayout) views.get(0)).addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 48, 0, 0));
        emojiGrids.get(0).setEmptyView(textView);

        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);
        pickerView = new EmojiColorPickerView(context);
        pickerViewPopup = new EmojiPopupWindow(pickerView, popupWidth = AndroidUtilities.dp((AndroidUtilities.isTablet() ? 40 : 32) * 6 + 10 + 4 * 5), popupHeight = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 64 : 56));
        pickerViewPopup.setOutsideTouchable(true);
        pickerViewPopup.setClippingEnabled(true);
        pickerViewPopup.setInputMethodMode(EmojiPopupWindow.INPUT_METHOD_NOT_NEEDED);
        pickerViewPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        pickerViewPopup.getContentView().setFocusableInTouchMode(true);
        pickerViewPopup.getContentView().setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && pickerViewPopup != null && pickerViewPopup.isShowing()) {
                    pickerViewPopup.dismiss();
                    return true;
                }
                return false;
            }
        });
        currentPage = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).getInt("selected_page", 0);
        loadRecents();
    }

    private void checkStickersTabY(View list, int dy) {
        if (list == null) {
            stickersTab.setTranslationY(minusDy = 0);
            return;
        }
        if (list.getVisibility() != VISIBLE) {
            return;
        }
        minusDy -= dy;
        if (minusDy > 0) {
            minusDy = 0;
        } else if (minusDy < -AndroidUtilities.dp(48 * 6)) {
            minusDy = -AndroidUtilities.dp(48 * 6);
        }
        stickersTab.setTranslationY(Math.max(-AndroidUtilities.dp(47), minusDy));
    }

    private void checkScroll() {
        int firstVisibleItem = stickersLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return;
        }
        checkStickersScroll(firstVisibleItem);
    }

    private void saveNewPage() {
        int newPage;
        if (pager.getCurrentItem() == 6) {
            if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
                newPage = 2;
            } else {
                newPage = 1;
            }
        } else {
            newPage = 0;
        }
        if (currentPage != newPage) {
            currentPage = newPage;
            getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit().putInt("selected_page", newPage).commit();
        }
    }

    public void clearRecentEmoji() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        preferences.edit().putBoolean("filled_default", true).commit();
        emojiUseHistory.clear();
        recentEmoji.clear();
        saveRecentEmoji();
        adapters.get(0).notifyDataSetChanged();
    }

    private void showTrendingTab() {
        trendingGridView.setVisibility(VISIBLE);
        stickersGridView.setVisibility(GONE);
        stickersEmptyView.setVisibility(GONE);
        gifsGridView.setVisibility(GONE);
        stickersTab.onPageScrolled(trendingTabNum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
        saveNewPage();
    }

    private void showGifTab() {
        gifsGridView.setVisibility(VISIBLE);
        stickersGridView.setVisibility(GONE);
        stickersEmptyView.setVisibility(GONE);
        trendingGridView.setVisibility(GONE);
        stickersTab.onPageScrolled(gifTabNum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
        saveNewPage();
    }

    private void checkStickersScroll(int firstVisibleItem) {
        if (stickersGridView == null) {
            return;
        }
        if (stickersGridView.getVisibility() != VISIBLE) {
            if (gifsGridView != null && gifsGridView.getVisibility() != VISIBLE) {
                gifsGridView.setVisibility(VISIBLE);
            }
            if (stickersEmptyView != null && stickersEmptyView.getVisibility() == VISIBLE) {
                stickersEmptyView.setVisibility(GONE);
            }
            stickersTab.onPageScrolled(gifTabNum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            return;
        }
        stickersTab.onPageScrolled(stickersGridAdapter.getTabForPosition(firstVisibleItem) + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
    }

    private void onPageScrolled(int position, int width, int positionOffsetPixels) {
        if (stickersTab == null) {
            return;
        }

        if (width == 0) {
            width = AndroidUtilities.displaySize.x;
        }

        int margin = 0;
        if (position == 5) {
            margin = -positionOffsetPixels;
            if (listener != null) {
                listener.onStickersTab(positionOffsetPixels != 0);
            }
        } else if (position == 6) {
            margin = -width;
            if (listener != null) {
                listener.onStickersTab(true);
            }
        } else {
            if (listener != null) {
                listener.onStickersTab(false);
            }
        }

        if (emojiTab.getTranslationX() != margin) {
            emojiTab.setTranslationX(margin);
            stickersTab.setTranslationX(width + margin);
            stickersTab.setVisibility(margin < 0 ? VISIBLE : INVISIBLE);
        }
    }

    private void postBackspaceRunnable(final int time) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (!backspacePressed) {
                    return;
                }
                if (listener != null && listener.onBackspace()) {
                    backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                backspaceOnce = true;
                postBackspaceRunnable(Math.max(50, time - 100));
            }
        }, time);
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

    private void saveRecentEmoji() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("emojis2", stringBuilder.toString()).commit();
    }

    private void saveEmojiColors() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, String> entry : emojiColor.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("color", stringBuilder.toString()).commit();
    }

    public void switchToGifRecent() {
        if (gifTabNum >= 0 && !recentGifs.isEmpty()) {
            stickersTab.selectTab(gifTabNum + 1);
        } else {
            switchToGifTab = true;
        }
        pager.setCurrentItem(6);
    }

    private void sortEmoji() {
        recentEmoji.clear();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            recentEmoji.add(entry.getKey());
        }
        Collections.sort(recentEmoji, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                Integer count1 = emojiUseHistory.get(lhs);
                Integer count2 = emojiUseHistory.get(rhs);
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
        while (recentEmoji.size() > 50) {
            recentEmoji.remove(recentEmoji.size() - 1);
        }
    }

    private void updateStickerTabs() {
        if (stickersTab == null) {
            return;
        }
        recentTabBum = -2;
        gifTabNum = -2;
        trendingTabNum = -2;

        stickersTabOffset = 0;
        int lastPosition = stickersTab.getCurrentPosition();
        stickersTab.removeTabs();
        stickersTab.addIconTab(R.drawable.ic_smiles2_smile);

        if (showGifs && !recentGifs.isEmpty()) {
            stickersTab.addIconTab(R.drawable.ic_smiles_gif);
            gifTabNum = stickersTabOffset;
            stickersTabOffset++;
        }

        ArrayList<Long> unread = StickersQuery.getUnreadStickerSets();

        TextView stickersCounter;
        if (trendingGridAdapter != null && trendingGridAdapter.getItemCount() != 0 && !unread.isEmpty()) {
            stickersCounter = stickersTab.addIconTabWithCounter(R.drawable.ic_smiles_trend);
            trendingTabNum = stickersTabOffset;
            stickersTabOffset++;
            stickersCounter.setText(String.format("%d", unread.size()));
        }

        if (!recentStickers.isEmpty()) {
            recentTabBum = stickersTabOffset;
            stickersTabOffset++;
            stickersTab.addIconTab(R.drawable.ic_smiles2_recent);
        }

        stickerSets.clear();
        ArrayList<TLRPC.TL_messages_stickerSet> packs = StickersQuery.getStickerSets(StickersQuery.TYPE_IMAGE);
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if (pack.set.archived || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets.add(pack);
        }
        for (int a = 0; a < stickerSets.size(); a++) {
            stickersTab.addStickerTab(stickerSets.get(a).documents.get(0));
        }
        if (trendingGridAdapter != null && trendingGridAdapter.getItemCount() != 0 && unread.isEmpty()) {
            trendingTabNum = stickersTabOffset + stickerSets.size();
            stickersTab.addIconTab(R.drawable.ic_smiles_trend);
        }
        stickersTab.addIconTab(R.drawable.ic_smiles_settings);
        stickersTab.updateTabStyles();
        if (lastPosition != 0) {
            stickersTab.onPageScrolled(lastPosition, lastPosition);
        }
        if (switchToGifTab) {
            if (gifTabNum >= 0 && gifsGridView.getVisibility() != VISIBLE) {
                showGifTab();
                switchToGifTab = false;
            }
        }
        checkPanels();
    }

    private void checkPanels() {
        if (stickersTab == null) {
            return;
        }
        if (trendingTabNum == -2 && trendingGridView != null && trendingGridView.getVisibility() == VISIBLE) {
            gifsGridView.setVisibility(GONE);
            trendingGridView.setVisibility(GONE);
            stickersGridView.setVisibility(VISIBLE);
            stickersEmptyView.setVisibility(stickersGridAdapter.getItemCount() != 0 ? GONE : VISIBLE);
        }
        if (gifTabNum == -2 && gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
            listener.onGifTab(false);
            gifsGridView.setVisibility(GONE);
            trendingGridView.setVisibility(GONE);
            stickersGridView.setVisibility(VISIBLE);
            stickersEmptyView.setVisibility(stickersGridAdapter.getItemCount() != 0 ? GONE : VISIBLE);
        } else if (gifTabNum != -2) {
            if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
                stickersTab.onPageScrolled(gifTabNum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            } else if (trendingGridView != null && trendingGridView.getVisibility() == VISIBLE) {
                stickersTab.onPageScrolled(trendingTabNum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            } else {
                int position = stickersLayoutManager.findFirstVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    stickersTab.onPageScrolled(stickersGridAdapter.getTabForPosition(position) + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
                }
            }
        }
    }

    public void addRecentSticker(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        StickersQuery.addRecentSticker(StickersQuery.TYPE_IMAGE, document, (int) (System.currentTimeMillis() / 1000));
        boolean wasEmpty = recentStickers.isEmpty();
        recentStickers = StickersQuery.getRecentStickers(StickersQuery.TYPE_IMAGE);
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (wasEmpty) {
            updateStickerTabs();
        }
    }

    public void addRecentGif(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        StickersQuery.addRecentGif(document, (int) (System.currentTimeMillis() / 1000));
        boolean wasEmpty = recentGifs.isEmpty();
        recentGifs = StickersQuery.getRecentGifs();
        if (gifsAdapter != null) {
            gifsAdapter.notifyDataSetChanged();
        }
        if (wasEmpty) {
            updateStickerTabs();
        }
    }

    public void loadRecents() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);

        String str;
        try {
            emojiUseHistory.clear();
            if (preferences.contains("emojis")) {
                str = preferences.getString("emojis", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        long value = Utilities.parseLong(args2[0]);
                        String string = "";
                        for (int a = 0; a < 4; a++) {
                            char ch = (char) value;
                            string = String.valueOf(ch) + string;
                            value >>= 16;
                            if (value == 0) {
                                break;
                            }
                        }
                        if (string.length() > 0) {
                            emojiUseHistory.put(string, Utilities.parseInt(args2[1]));
                        }
                    }
                }
                preferences.edit().remove("emojis").commit();
                saveRecentEmoji();
            } else {
                str = preferences.getString("emojis2", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        emojiUseHistory.put(args2[0], Utilities.parseInt(args2[1]));
                    }
                }
            }
            if (emojiUseHistory.isEmpty()) {
                if (!preferences.getBoolean("filled_default", false)) {
                    String[] newRecent = new String[]{
                            "\uD83D\uDE02", "\uD83D\uDE18", "\u2764", "\uD83D\uDE0D", "\uD83D\uDE0A", "\uD83D\uDE01",
                            "\uD83D\uDC4D", "\u263A", "\uD83D\uDE14", "\uD83D\uDE04", "\uD83D\uDE2D", "\uD83D\uDC8B",
                            "\uD83D\uDE12", "\uD83D\uDE33", "\uD83D\uDE1C", "\uD83D\uDE48", "\uD83D\uDE09", "\uD83D\uDE03",
                            "\uD83D\uDE22", "\uD83D\uDE1D", "\uD83D\uDE31", "\uD83D\uDE21", "\uD83D\uDE0F", "\uD83D\uDE1E",
                            "\uD83D\uDE05", "\uD83D\uDE1A", "\uD83D\uDE4A", "\uD83D\uDE0C", "\uD83D\uDE00", "\uD83D\uDE0B",
                            "\uD83D\uDE06", "\uD83D\uDC4C", "\uD83D\uDE10", "\uD83D\uDE15"};
                    for (int i = 0; i < newRecent.length; i++) {
                        emojiUseHistory.put(newRecent[i], newRecent.length - i);
                    }
                    preferences.edit().putBoolean("filled_default", true).commit();
                    saveRecentEmoji();
                }
            }
            sortEmoji();
            adapters.get(0).notifyDataSetChanged();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        try {
            str = preferences.getString("color", "");
            if (str != null && str.length() > 0) {
                String[] args = str.split(",");
                for (int a = 0; a < args.length; a++) {
                    String arg = args[a];
                    String[] args2 = arg.split("=");
                    emojiColor.put(args2[0], args2[1]);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void requestLayout() {
        if (isLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        isLayout = true;
        if (AndroidUtilities.isInMultiwindow) {
            if (currentBackgroundType != 1) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider((ViewOutlineProvider) outlineProvider);
                    setClipToOutline(true);
                    setElevation(AndroidUtilities.dp(2));
                }
                setBackgroundResource(R.drawable.smiles_popup);
                emojiTab.setBackgroundDrawable(null);
                currentBackgroundType = 1;
            }
        } else {
            if (currentBackgroundType != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider(null);
                    setClipToOutline(false);
                    setElevation(0);
                }
                setBackgroundColor(0xfff5f6f7);
                emojiTab.setBackgroundColor(0xfff5f6f7);
                currentBackgroundType = 0;
            }
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiTab.getLayoutParams();
        FrameLayout.LayoutParams layoutParams1 = null;
        layoutParams.width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (stickersTab != null) {
            layoutParams1 = (FrameLayout.LayoutParams) stickersTab.getLayoutParams();
            if (layoutParams1 != null) {
                layoutParams1.width = layoutParams.width;
            }
        }
        if (layoutParams.width != oldWidth) {
            if (stickersTab != null && layoutParams1 != null) {
                onPageScrolled(pager.getCurrentItem(), layoutParams.width - getPaddingLeft() - getPaddingRight(), 0);
                stickersTab.setLayoutParams(layoutParams1);
            }
            emojiTab.setLayoutParams(layoutParams);
            oldWidth = layoutParams.width;
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        isLayout = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            reloadStickersAdapter();
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private void reloadStickersAdapter() {
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (trendingGridAdapter != null) {
            trendingGridAdapter.notifyDataSetChanged();
        }
        if (StickerPreviewViewer.getInstance().isVisible()) {
            StickerPreviewViewer.getInstance().close();
        }
        StickerPreviewViewer.getInstance().reset();
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void invalidateViews() {
        for (int a = 0; a < emojiGrids.size(); a++) {
            emojiGrids.get(a).invalidateViews();
        }
    }

    public void onOpen(boolean forceEmoji) {
        if (stickersTab != null) {
            if (currentPage == 0 || forceEmoji) {
                if (pager.getCurrentItem() == 6) {
                    pager.setCurrentItem(0, !forceEmoji);
                }
            } else if (currentPage == 1) {
                if (pager.getCurrentItem() != 6) {
                    pager.setCurrentItem(6);
                }
                if (stickersTab.getCurrentPosition() == gifTabNum + 1) {
                    if (recentTabBum >= 0) {
                        stickersTab.selectTab(recentTabBum + 1);
                    } else if (gifTabNum >= 0) {
                        stickersTab.selectTab(gifTabNum + 2);
                    } else {
                        stickersTab.selectTab(1);
                    }
                }
            } else if (currentPage == 2) {
                if (pager.getCurrentItem() != 6) {
                    pager.setCurrentItem(6);
                }
                if (stickersTab.getCurrentPosition() != gifTabNum + 1) {
                    if (gifTabNum >= 0 && !recentGifs.isEmpty()) {
                        stickersTab.selectTab(gifTabNum + 1);
                    } else {
                        switchToGifTab = true;
                    }
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.recentImagesDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.featuredStickersDidLoaded);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    updateStickerTabs();
                    reloadStickersAdapter();
                }
            });
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != GONE) {
            sortEmoji();
            adapters.get(0).notifyDataSetChanged();
            if (stickersGridAdapter != null) {
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.stickersDidLoaded);
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.recentDocumentsDidLoaded);
                updateStickerTabs();
                reloadStickersAdapter();
                if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE && listener != null) {
                    listener.onGifTab(pager != null && pager.getCurrentItem() >= 6);
                }
            }
            if (trendingGridAdapter != null) {
                trendingLoaded = false;
                trendingGridAdapter.notifyDataSetChanged();
            }
            checkDocuments(true);
            checkDocuments(false);
            StickersQuery.loadRecents(StickersQuery.TYPE_IMAGE, true, true);
            StickersQuery.loadRecents(StickersQuery.TYPE_IMAGE, false, true);
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void onDestroy() {
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recentDocumentsDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.featuredStickersDidLoaded);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pickerViewPopup != null && pickerViewPopup.isShowing()) {
            pickerViewPopup.dismiss();
        }
    }

    private void checkDocuments(boolean isGif) {
        if (isGif) {
            int previousCount = recentGifs.size();
            recentGifs = StickersQuery.getRecentGifs();
            if (gifsAdapter != null) {
                gifsAdapter.notifyDataSetChanged();
            }
            if (previousCount != recentGifs.size()) {
                updateStickerTabs();
            }
        } else {
            int previousCount = recentStickers.size();
            recentStickers = StickersQuery.getRecentStickers(StickersQuery.TYPE_IMAGE);
            if (stickersGridAdapter != null) {
                stickersGridAdapter.notifyDataSetChanged();
            }
            if (previousCount != recentStickers.size()) {
                updateStickerTabs();
            }
        }
    }

    private void updateVisibleTrendingSets() {
        int first = trendingLayoutManager.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        int last = trendingLayoutManager.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION) {
            return;
        }
        trendingGridAdapter.notifyItemRangeChanged(first, last - first + 1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.stickersDidLoaded) {
            if ((Integer) args[0] == StickersQuery.TYPE_IMAGE) {
                if (trendingGridAdapter != null) {
                    if (trendingLoaded) {
                        updateVisibleTrendingSets();
                    } else {
                        trendingGridAdapter.notifyDataSetChanged();
                    }
                }
                updateStickerTabs();
                reloadStickersAdapter();
                checkPanels();
            }
        } else if (id == NotificationCenter.recentDocumentsDidLoaded) {
            boolean isGif = (Boolean) args[0];
            if (isGif || (Integer) args[1] == StickersQuery.TYPE_IMAGE) {
                checkDocuments(isGif);
            }
        } else if (id == NotificationCenter.featuredStickersDidLoaded) {
            if (trendingGridAdapter != null) {
                if (trendingLoaded) {
                    updateVisibleTrendingSets();
                } else {
                    trendingGridAdapter.notifyDataSetChanged();
                }
            }
            if (pagerSlidingTabStrip != null) {
                int count = pagerSlidingTabStrip.getChildCount();
                for (int a = 0; a < count; a++) {
                    pagerSlidingTabStrip.getChildAt(a).invalidate();
                }
            }
            updateStickerTabs();
        }
    }

    private class TrendingGridAdapter extends RecyclerView.Adapter {

        private Context context;
        private int stickersPerRow;
        private HashMap<Integer, Object> cache = new HashMap<>();
        private ArrayList<TLRPC.StickerSetCovered> sets = new ArrayList<>();
        private HashMap<Integer, TLRPC.StickerSetCovered> positionsToSets = new HashMap<>();
        private int totalItems;

        public TrendingGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return totalItems;
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        @Override
        public int getItemViewType(int position) {
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    return 0;
                } else {
                    return 2;
                }
            }
            return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerEmojiCell(context) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new FeaturedStickerSetInfoCell(context, 17);
                    ((FeaturedStickerSetInfoCell) view).setAddOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            FeaturedStickerSetInfoCell parent = (FeaturedStickerSetInfoCell) v.getParent();
                            TLRPC.StickerSetCovered pack = parent.getStickerSet();
                            if (installingStickerSets.containsKey(pack.set.id) || removingStickerSets.containsKey(pack.set.id)) {
                                return;
                            }
                            if (parent.isInstalled()) {
                                removingStickerSets.put(pack.set.id, pack);
                                listener.onStickerSetRemove(parent.getStickerSet());
                            } else {
                                installingStickerSets.put(pack.set.id, pack);
                                listener.onStickerSetAdd(parent.getStickerSet());
                            }
                            parent.setDrawProgress(true);
                        }
                    });
                    break;
            }

            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    ((StickerEmojiCell) holder.itemView).setSticker(sticker, false);
                    break;
                case 1:
                    ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(82));
                    break;
                case 2:
                    ArrayList<Long> unreadStickers = StickersQuery.getUnreadStickerSets();
                    TLRPC.StickerSetCovered stickerSetCovered = sets.get((Integer) cache.get(position));
                    boolean unread = unreadStickers != null && unreadStickers.contains(stickerSetCovered.set.id);
                    FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) holder.itemView;
                    cell.setStickerSet(stickerSetCovered, unread);
                    if (unread) {
                        StickersQuery.markFaturedStickersByIdAsRead(stickerSetCovered.set.id);
                    }
                    boolean installing = installingStickerSets.containsKey(stickerSetCovered.set.id);
                    boolean removing = removingStickerSets.containsKey(stickerSetCovered.set.id);
                    if (installing || removing) {
                        if (installing && cell.isInstalled()) {
                            installingStickerSets.remove(stickerSetCovered.set.id);
                            installing = false;
                        } else if (removing && !cell.isInstalled()) {
                            removingStickerSets.remove(stickerSetCovered.set.id);
                            removing = false;
                        }
                    }
                    cell.setDrawProgress(installing || removing);
                    break;
            }
        }

        @Override
        public void notifyDataSetChanged() {
            if (trendingLoaded) {
                return;
            }
            int width = getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            stickersPerRow = width / AndroidUtilities.dp(72);
            trendingLayoutManager.setSpanCount(stickersPerRow);
            cache.clear();
            positionsToSets.clear();
            sets.clear();
            totalItems = 0;
            int num = 0;

            ArrayList<TLRPC.StickerSetCovered> packs = StickersQuery.getFeaturedStickerSets();

            for (int a = 0; a < packs.size(); a++) {
                TLRPC.StickerSetCovered pack = packs.get(a);
                if (StickersQuery.isStickerPackInstalled(pack.set.id) || pack.covers.isEmpty() && pack.cover == null) {
                    continue;
                }
                sets.add(pack);
                positionsToSets.put(totalItems, pack);
                cache.put(totalItems++, num++);
                int startRow = totalItems / stickersPerRow;
                int count;
                if (!pack.covers.isEmpty()) {
                    count = (int) Math.ceil(pack.covers.size() / (float) stickersPerRow);
                    for (int b = 0; b < pack.covers.size(); b++) {
                        cache.put(b + totalItems, pack.covers.get(b));
                    }
                } else {
                    count = 1;
                    cache.put(totalItems, pack.cover);
                }
                for (int b = 0; b < count * stickersPerRow; b++) {
                    positionsToSets.put(totalItems + b, pack);
                }
                totalItems += count * stickersPerRow;
            }
            if (totalItems != 0) {
                trendingLoaded = true;
            }
            super.notifyDataSetChanged();
        }
    }

    private class StickersGridAdapter extends RecyclerView.Adapter {

        private Context context;
        private int stickersPerRow;
        private HashMap<Integer, TLRPC.TL_messages_stickerSet> rowStartPack = new HashMap<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Integer> packStartRow = new HashMap<>();
        private HashMap<Integer, TLRPC.Document> cache = new HashMap<>();
        private int totalItems;

        public StickersGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return totalItems != 0 ? totalItems + 1 : 0;
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public int getPositionForPack(TLRPC.TL_messages_stickerSet stickerSet) {
            return packStartRow.get(stickerSet) * stickersPerRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (cache.get(position) != null) {
                return 0;
            }
            return 1;
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
                return recentTabBum;
            }
            return stickerSets.indexOf(pack) + stickersTabOffset;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerEmojiCell(context) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
            }

            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TLRPC.Document sticker = cache.get(position);
                    ((StickerEmojiCell) holder.itemView).setSticker(sticker, false);
                    break;
                case 1:
                    if (position == totalItems) {
                        int row = (position - 1) / stickersPerRow;
                        TLRPC.TL_messages_stickerSet pack = rowStartPack.get(row);
                        if (pack == null) {
                            ((EmptyCell) holder.itemView).setHeight(1);
                        } else {
                            int height = pager.getHeight() - (int) Math.ceil(pack.documents.size() / (float) stickersPerRow) * AndroidUtilities.dp(82);
                            ((EmptyCell) holder.itemView).setHeight(height > 0 ? height : 1);
                        }
                    } else {
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(82));
                    }
                    break;
            }
        }

        @Override
        public void notifyDataSetChanged() {
            int width = getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            stickersPerRow = width / AndroidUtilities.dp(72);
            stickersLayoutManager.setSpanCount(stickersPerRow);
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
    }

    private class EmojiGridAdapter extends BaseAdapter {

        private int emojiPage;

        public EmojiGridAdapter(int page) {
            emojiPage = page;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public int getCount() {
            if (emojiPage == -1) {
                return recentEmoji.size();
            }
            return EmojiData.dataColored[emojiPage].length;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup paramViewGroup) {
            ImageViewEmoji imageView = (ImageViewEmoji) view;
            if (imageView == null) {
                imageView = new ImageViewEmoji(getContext());
            }
            String code;
            String coloredCode;
            if (emojiPage == -1) {
                coloredCode = code = recentEmoji.get(position);
            } else {
                coloredCode = code = EmojiData.dataColored[emojiPage][position];
                String color = emojiColor.get(code);
                if (color != null) {
                    coloredCode = addColorToCode(coloredCode, color);
                }
            }
            imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode));
            imageView.setTag(code);
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
            if (position == 6) {
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

        @Override
        public void customOnDraw(Canvas canvas, int position) {
            if (position == 6 && !StickersQuery.getUnreadStickerSets().isEmpty() && dotDrawable != null) {
                int x = canvas.getWidth() / 2 + AndroidUtilities.dp(4);
                int y = canvas.getHeight() / 2 - AndroidUtilities.dp(13);
                dotDrawable.setBounds(x, y, x + dotDrawable.getIntrinsicWidth(), y + dotDrawable.getIntrinsicHeight());
                dotDrawable.draw(canvas);
            }
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view;
            if (position == 6) {
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

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    private class GifsAdapter extends RecyclerView.Adapter {

        private Context mContext;

        public GifsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return recentGifs.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            ContextLinkCell view = new ContextLinkCell(mContext);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            TLRPC.Document document = recentGifs.get(i);
            if (document != null) {
                ((ContextLinkCell) viewHolder.itemView).setGif(document, false);
            }
        }
    }
}
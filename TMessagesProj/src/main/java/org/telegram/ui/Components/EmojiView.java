/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Canvas;
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
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.EmptyCell;
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

    private class ImageViewEmoji extends ImageView {

        private boolean touched;
        private float lastX;
        private float lastY;
        private float touchedX;
        private float touchedY;

        public ImageViewEmoji(Context context) {
            super(context);

            setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
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
                        code += color;
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
                                code += color;
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
                        code += "\uD83C";
                        switch (a) {
                            case 1:
                                code += "\uDFFB";
                                break;
                            case 2:
                                code += "\uDFFC";
                                break;
                            case 3:
                                code += "\uDFFD";
                                break;
                            case 4:
                                code += "\uDFFE";
                                break;
                            case 5:
                                code += "\uDFFF";
                                break;
                        }
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
    private ArrayList<Long> newRecentStickers = new ArrayList<>();
    private ArrayList<TLRPC.Document> recentStickers = new ArrayList<>();
    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
    private ArrayList<MediaController.SearchImage> recentImages;
    private boolean loadingRecent;

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
    private GridView stickersGridView;
    private TextView stickersEmptyView;
    private RecyclerListView gifsGridView;
    private FlowLayoutManager flowLayoutManager;
    private GifsAdapter gifsAdapter;
    private AdapterView.OnItemClickListener stickersOnItemClickListener;

    private EmojiColorPickerView pickerView;
    private EmojiPopupWindow pickerViewPopup;
    private int popupWidth;
    private int popupHeight;
    private int emojiSize;
    private int location[] = new int[2];
    private int stickersTabOffset;
    private int recentTabBum = -2;
    private int gifTabBum = -2;
    private boolean switchToGifTab;

    private int oldWidth;
    private int lastNotifyWidth;

    private boolean backspacePressed;
    private boolean backspaceOnce;
    private boolean showStickers;
    private boolean showGifs;

    private boolean loadingRecentGifs;
    private long lastGifLoadTime;

    public EmojiView(boolean needStickers, boolean needGif, final Context context) {
        super(context);

        showStickers = needStickers;
        showGifs = needGif;

        for (int i = 0; i < EmojiData.dataColored.length + 1; i++) {
            GridView gridView = new GridView(context);
            if (AndroidUtilities.isTablet()) {
                gridView.setColumnWidth(AndroidUtilities.dp(60));
            } else {
                gridView.setColumnWidth(AndroidUtilities.dp(45));
            }
            gridView.setNumColumns(-1);
            views.add(gridView);

            EmojiGridAdapter emojiGridAdapter = new EmojiGridAdapter(i - 1);
            gridView.setAdapter(emojiGridAdapter);
            AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);
            adapters.add(emojiGridAdapter);
        }

        if (showStickers) {
            StickersQuery.checkStickers();
            stickersGridView = new GridView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersGridView, EmojiView.this.getMeasuredHeight());
                    return super.onInterceptTouchEvent(event) || result;
                }

                @Override
                public void setVisibility(int visibility) {
                    if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
                        super.setVisibility(GONE);
                        return;
                    }
                    super.setVisibility(visibility);
                }
            };
            stickersGridView.setSelector(R.drawable.transparent);
            stickersGridView.setColumnWidth(AndroidUtilities.dp(72));
            stickersGridView.setNumColumns(-1);
            stickersGridView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            stickersGridView.setClipToPadding(false);
            views.add(stickersGridView);
            stickersGridAdapter = new StickersGridAdapter(context);
            stickersGridView.setAdapter(stickersGridAdapter);
            stickersGridView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return StickerPreviewViewer.getInstance().onTouch(event, stickersGridView, EmojiView.this.getMeasuredHeight(), stickersOnItemClickListener);
                }
            });
            stickersOnItemClickListener = new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long i) {
                    if (!(view instanceof StickerEmojiCell)) {
                        return;
                    }
                    StickerPreviewViewer.getInstance().reset();
                    StickerEmojiCell cell = (StickerEmojiCell) view;
                    if (cell.isDisabled()) {
                        return;
                    }
                    cell.disable();
                    TLRPC.Document document = cell.getSticker();
                    int index = newRecentStickers.indexOf(document.id);
                    if (index == -1) {
                        newRecentStickers.add(0, document.id);
                        if (newRecentStickers.size() > 20) {
                            newRecentStickers.remove(newRecentStickers.size() - 1);
                        }
                    } else if (index != 0) {
                        newRecentStickers.remove(index);
                        newRecentStickers.add(0, document.id);
                    }

                    saveRecentStickers();
                    if (listener != null) {
                        listener.onStickerSelected(document);
                    }
                }
            };
            stickersGridView.setOnItemClickListener(stickersOnItemClickListener);
            AndroidUtilities.setListViewEdgeEffectColor(stickersGridView, 0xfff5f6f7);

            stickersWrap = new FrameLayout(context);
            stickersWrap.addView(stickersGridView);

            if (needGif) {
                gifsGridView = new RecyclerListView(context);
                gifsGridView.setLayoutManager(flowLayoutManager = new FlowLayoutManager() {

                    private Size size = new Size();

                    @Override
                    protected Size getSizeForItem(int i) {
                        TLRPC.Document document = recentImages.get(i).document;
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
                if (Build.VERSION.SDK_INT >= 9) {
                    gifsGridView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                }
                gifsGridView.setAdapter(gifsAdapter = new GifsAdapter(context));
                gifsGridView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        if (position < 0 || position >= recentImages.size() || listener == null) {
                            return;
                        }
                        TLRPC.Document document = recentImages.get(position).document;
                        listener.onGifSelected(document);
                    }
                });
                gifsGridView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position) {
                        if (position < 0 || position >= recentImages.size()) {
                            return false;
                        }
                        final MediaController.SearchImage searchImage = recentImages.get(position);
                        AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("DeleteGif", R.string.DeleteGif));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK).toUpperCase(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                recentImages.remove(searchImage);
                                TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
                                req.id = new TLRPC.TL_inputDocument();
                                req.id.id = searchImage.document.id;
                                req.id.access_hash = searchImage.document.access_hash;
                                req.unsave = true;
                                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {

                                    }
                                });
                                MessagesStorage.getInstance().removeWebRecent(searchImage);
                                if (gifsAdapter != null) {
                                    gifsAdapter.notifyDataSetChanged();
                                }
                                if (recentImages.isEmpty()) {
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
            stickersWrap.addView(stickersEmptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            stickersGridView.setEmptyView(stickersEmptyView);

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
                    if (gifsGridView != null) {
                        if (page == gifTabBum + 1) {
                            if (gifsGridView.getVisibility() != VISIBLE) {
                                listener.onGifTab(true);
                                showGifTab();
                            }
                        } else {
                            if (gifsGridView.getVisibility() == VISIBLE) {
                                listener.onGifTab(false);
                                gifsGridView.setVisibility(GONE);
                                stickersGridView.setVisibility(VISIBLE);
                                stickersEmptyView.setVisibility(stickersGridAdapter.getCount() != 0 ? GONE : VISIBLE);
                            }
                        }
                    }
                    if (page == 0) {
                        pager.setCurrentItem(0);
                        return;
                    } else {
                        if (page == gifTabBum + 1) {
                            return;
                        } else {
                            if (page == recentTabBum + 1) {
                                views.get(6).setSelection(0);
                                return;
                            }
                        }
                    }
                    int index = page - 1 - stickersTabOffset;
                    if (index == stickerSets.size()) {
                        if (listener != null) {
                            listener.onStickersSettingsClick();
                        }
                        return;
                    }
                    if (index >= stickerSets.size()) {
                        index = stickerSets.size() - 1;
                    }
                    views.get(6).setSelection(stickersGridAdapter.getPositionForPack(stickerSets.get(index)));
                }
            });

            stickersGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    checkStickersScroll(firstVisibleItem);
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

        loadRecents();
    }

    public void clearRecentEmoji() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        preferences.edit().putBoolean("filled_default", true).commit();
        emojiUseHistory.clear();
        recentEmoji.clear();
        saveRecentEmoji();
        adapters.get(0).notifyDataSetChanged();
    }

    private void showGifTab() {
        gifsGridView.setVisibility(VISIBLE);
        stickersGridView.setVisibility(GONE);
        stickersEmptyView.setVisibility(GONE);
        scrollSlidingTabStrip.onPageScrolled(gifTabBum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
    }

    private void checkStickersScroll(int firstVisibleItem) {
        if (stickersGridView == null) {
            return;
        }
        if (stickersGridView.getVisibility() != VISIBLE) {
            scrollSlidingTabStrip.onPageScrolled(gifTabBum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            return;
        }
        int count = stickersGridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = stickersGridView.getChildAt(a);
            if (child.getHeight() + child.getTop() < AndroidUtilities.dp(5)) {
                firstVisibleItem++;
            } else {
                break;
            }
        }
        scrollSlidingTabStrip.onPageScrolled(stickersGridAdapter.getTabForPosition(firstVisibleItem) + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
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

    private void saveRecentStickers() {
        SharedPreferences.Editor editor = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit();
        StringBuilder stringBuilder = new StringBuilder();
        for (int a = 0; a < newRecentStickers.size(); a++) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(newRecentStickers.get(a));
        }
        editor.putString("stickers2", stringBuilder.toString());
        editor.commit();
    }

    public void switchToGifRecent() {
        pager.setCurrentItem(6);
        if (gifTabBum >= 0 && !recentImages.isEmpty()) {
            scrollSlidingTabStrip.selectTab(gifTabBum + 1);
        } else {
            switchToGifTab = true;
        }
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

    private void sortStickers() {
        if (StickersQuery.getStickerSets().isEmpty()) {
            recentStickers.clear();
            return;
        }
        recentStickers.clear();
        for (int a = 0; a < newRecentStickers.size(); a++) {
            TLRPC.Document sticker = StickersQuery.getStickerById(newRecentStickers.get(a));
            if (sticker != null) {
                recentStickers.add(sticker);
            }
        }
        while (recentStickers.size() > 20) {
            recentStickers.remove(recentStickers.size() - 1);
        }
        if (newRecentStickers.size() != recentStickers.size()) {
            newRecentStickers.clear();
            for (int a = 0; a < recentStickers.size(); a++) {
                newRecentStickers.add(recentStickers.get(a).id);
            }
            saveRecentStickers();
        }
    }

    private void updateStickerTabs() {
        if (scrollSlidingTabStrip == null) {
            return;
        }
        recentTabBum = -2;
        gifTabBum = -2;

        stickersTabOffset = 0;
        int lastPosition = scrollSlidingTabStrip.getCurrentPosition();
        scrollSlidingTabStrip.removeTabs();
        scrollSlidingTabStrip.addIconTab(R.drawable.ic_smiles_smile);

        if (showGifs && recentImages != null && !recentImages.isEmpty()) {
            scrollSlidingTabStrip.addIconTab(R.drawable.ic_smiles_gif);
            gifTabBum = stickersTabOffset;
            stickersTabOffset++;
        }

        if (!recentStickers.isEmpty()) {
            recentTabBum = stickersTabOffset;
            stickersTabOffset++;
            scrollSlidingTabStrip.addIconTab(R.drawable.ic_smiles_recent);
        }

        stickerSets.clear();
        ArrayList<TLRPC.TL_messages_stickerSet> packs = StickersQuery.getStickerSets();
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if (pack.set.disabled || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets.add(pack);
        }
        for (int a = 0; a < stickerSets.size(); a++) {
            scrollSlidingTabStrip.addStickerTab(stickerSets.get(a).documents.get(0));
        }
        scrollSlidingTabStrip.addIconTab(R.drawable.ic_settings);
        scrollSlidingTabStrip.updateTabStyles();
        if (lastPosition != 0) {
            scrollSlidingTabStrip.onPageScrolled(lastPosition, lastPosition);
        }
        if (switchToGifTab) {
            if (gifTabBum >= 0 && gifsGridView.getVisibility() != VISIBLE) {
                showGifTab();
            }
            switchToGifTab = false;
        }
        if (gifTabBum == -2 && gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
            listener.onGifTab(false);
            gifsGridView.setVisibility(GONE);
            stickersGridView.setVisibility(VISIBLE);
            stickersEmptyView.setVisibility(stickersGridAdapter.getCount() != 0 ? GONE : VISIBLE);
        } else if (gifTabBum != -2) {
            if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE) {
                scrollSlidingTabStrip.onPageScrolled(gifTabBum + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            } else {
                scrollSlidingTabStrip.onPageScrolled(stickersGridAdapter.getTabForPosition(stickersGridView.getFirstVisiblePosition()) + 1, (recentTabBum > 0 ? recentTabBum : stickersTabOffset) + 1);
            }
        }
    }

    public void addRecentGif(MediaController.SearchImage searchImage) {
        if (searchImage == null || searchImage.document == null || recentImages == null) {
            return;
        }
        boolean wasEmpty = recentImages.isEmpty();
        for (int a = 0; a < recentImages.size(); a++) {
            MediaController.SearchImage image = recentImages.get(a);
            if (image.id.equals(searchImage.id)) {
                recentImages.remove(a);
                recentImages.add(0, image);
                if (gifsAdapter != null) {
                    gifsAdapter.notifyDataSetChanged();
                }
                return;
            }
        }
        recentImages.add(0, searchImage);
        if (gifsAdapter != null) {
            gifsAdapter.notifyDataSetChanged();
        }
        if (wasEmpty) {
            updateStickerTabs();
        }
    }

    public void loadRecents() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        lastGifLoadTime = preferences.getLong("lastGifLoadTime", 0);

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

        if (showStickers) {
            try {
                newRecentStickers.clear();
                str = preferences.getString("stickers", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    final HashMap<Long, Integer> stickersUseHistory = new HashMap<>();
                    for (int a = 0; a < args.length; a++) {
                        String arg = args[a];
                        String[] args2 = arg.split("=");
                        Long key = Utilities.parseLong(args2[0]);
                        stickersUseHistory.put(key, Utilities.parseInt(args2[1]));
                        newRecentStickers.add(key);
                    }
                    Collections.sort(newRecentStickers, new Comparator<Long>() {
                        @Override
                        public int compare(Long lhs, Long rhs) {
                            Integer count1 = stickersUseHistory.get(lhs);
                            Integer count2 = stickersUseHistory.get(rhs);
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
                    preferences.edit().remove("stickers").commit();
                    saveRecentStickers();
                } else {
                    str = preferences.getString("stickers2", "");
                    String[] args = str.split(",");
                    for (int a = 0; a < args.length; a++) {
                        if (args[a].length() == 0) {
                            continue;
                        }
                        long id = Utilities.parseLong(args[a]);
                        if (id != 0) {
                            newRecentStickers.add(id);
                        }
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
            reloadStickersAdapter();
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private void reloadStickersAdapter() {
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
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
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.recentImagesDidLoaded);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    updateStickerTabs();
                    reloadStickersAdapter();
                }
            });
        }
    }

    public void loadGifRecent() {
        if (showGifs && gifsAdapter != null && !loadingRecent) {
            MessagesStorage.getInstance().loadWebRecent(2);
            loadingRecent = true;
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
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.recentImagesDidLoaded);
                sortStickers();
                updateStickerTabs();
                reloadStickersAdapter();
                if (gifsGridView != null && gifsGridView.getVisibility() == VISIBLE && listener != null) {
                    listener.onGifTab(true);
                }
            }
            loadGifRecent();
        }
    }

    public void onDestroy() {
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.stickersDidLoaded);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recentImagesDidLoaded);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pickerViewPopup != null && pickerViewPopup.isShowing()) {
            pickerViewPopup.dismiss();
        }
    }

    private int calcGifsHash(ArrayList<MediaController.SearchImage> arrayList) {
        if (arrayList == null) {
            return 0;
        }
        long acc = 0;
        for (int a = 0; a < Math.min(200, arrayList.size()); a++) {
            MediaController.SearchImage searchImage = arrayList.get(a);
            if (searchImage.document == null) {
                continue;
            }
            int high_id = (int) (searchImage.document.id >> 32);
            int lower_id = (int) searchImage.document.id;
            acc = ((acc * 20261) + 0x80000000L + high_id) % 0x80000000L;
            acc = ((acc * 20261) + 0x80000000L + lower_id) % 0x80000000L;
        }
        return (int) acc;
    }

    public void loadRecentGif() {
        if (loadingRecentGifs || Math.abs(System.currentTimeMillis() - lastGifLoadTime) < 60 * 60 * 1000) {
            return;
        }
        loadingRecentGifs = true;
        TLRPC.TL_messages_getSavedGifs req = new TLRPC.TL_messages_getSavedGifs();
        req.hash = calcGifsHash(recentImages);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                ArrayList<MediaController.SearchImage> arrayList = null;
                if (response instanceof TLRPC.TL_messages_savedGifs) {
                    arrayList = new ArrayList<>();
                    TLRPC.TL_messages_savedGifs res = (TLRPC.TL_messages_savedGifs) response;
                    int size = res.gifs.size();
                    for (int a = 0; a < size; a++) {
                        MediaController.SearchImage searchImage = new MediaController.SearchImage();
                        searchImage.type = 2;
                        searchImage.document = res.gifs.get(a);
                        searchImage.date = size - a;
                        searchImage.id = "" + searchImage.document.id;
                        arrayList.add(searchImage);
                        MessagesStorage.getInstance().putWebRecent(arrayList);
                    }
                }
                final ArrayList<MediaController.SearchImage> arrayListFinal = arrayList;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (arrayListFinal != null) {
                            boolean wasEmpty = recentImages.isEmpty();
                            recentImages = arrayListFinal;
                            if (gifsAdapter != null) {
                                gifsAdapter.notifyDataSetChanged();
                            }
                            lastGifLoadTime = System.currentTimeMillis();
                            SharedPreferences.Editor editor = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit();
                            editor.putLong("lastGifLoadTime", lastGifLoadTime).commit();
                            if (wasEmpty && !recentImages.isEmpty()) {
                                updateStickerTabs();
                            }
                        }
                        loadingRecentGifs = false;
                    }
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.stickersDidLoaded) {
            updateStickerTabs();
            reloadStickersAdapter();
        } else if (id == NotificationCenter.recentImagesDidLoaded) {
            if ((Integer) args[0] == 2) {
                int previousCount = recentImages != null ? recentImages.size() : 0;
                recentImages = (ArrayList<MediaController.SearchImage>) args[1];
                loadingRecent = false;
                if (gifsAdapter != null) {
                    gifsAdapter.notifyDataSetChanged();
                }
                if (previousCount != recentImages.size()) {
                    updateStickerTabs();
                }
                loadRecentGif();
            }
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
                return recentTabBum;
            }
            return stickerSets.indexOf(pack) + stickersTabOffset;
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

        private int emojiPage;

        public EmojiGridAdapter(int page) {
            emojiPage = page;
        }

        public int getCount() {
            if (emojiPage == -1) {
                return recentEmoji.size();
            }
            return EmojiData.dataColored[emojiPage].length;
        }

        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public View getView(int i, View view, ViewGroup paramViewGroup) {
            ImageViewEmoji imageView = (ImageViewEmoji) view;
            if (imageView == null) {
                imageView = new ImageViewEmoji(getContext());
            }
            String code;
            String coloredCode;
            if (emojiPage == -1) {
                coloredCode = code = recentEmoji.get(i);
            } else {
                coloredCode = code = EmojiData.dataColored[emojiPage][i];
                String color = emojiColor.get(code);
                if (color != null) {
                    coloredCode += color;
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

    private class GifsAdapter extends RecyclerView.Adapter {

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        private Context mContext;

        public GifsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return recentImages.size();
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
            MediaController.SearchImage photoEntry = recentImages.get(i);
            if (photoEntry.document != null) {
                ((ContextLinkCell) viewHolder.itemView).setGif(photoEntry.document, flowLayoutManager.getRowOfIndex(i) != flowLayoutManager.getRowCount() - 1);
            }
        }
    }
}
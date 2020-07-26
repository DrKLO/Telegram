/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Cells.StickerSetGroupInfoCell;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.ContentPreviewViewer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private ArrayList<View> views = new ArrayList<>();
    private ViewPager pager;
    private FrameLayout bottomTabContainer;
    private View bottomTabContainerBackground;
    private ImageView floatingButton;
    private PagerSlidingTabStrip typeTabs;
    private ImageView backspaceButton;
    private ImageView stickerSettingsButton;
    private ImageView searchButton;
    private View shadowLine;
    private View topShadow;
    private AnimatorSet bottomTabContainerAnimation;
    private AnimatorSet backspaceButtonAnimation;
    private AnimatorSet stickersButtonAnimation;
    private float lastBottomScrollDy;

    private ScrollSlidingTabStrip emojiTabs;
    private FrameLayout emojiContainer;
    private View emojiTabsShadow;
    private RecyclerListView emojiGridView;
    private GridLayoutManager emojiLayoutManager;
    private EmojiGridAdapter emojiAdapter;
    private EmojiSearchAdapter emojiSearchAdapter;
    private SearchField emojiSearchField;
    private AnimatorSet emojiTabShadowAnimator;
    private boolean firstEmojiAttach = true;
    private boolean needEmojiSearch;
    private int hasRecentEmoji = -1;

    private FrameLayout gifContainer;
    private RecyclerListView gifGridView;
    private GifLayoutManager gifLayoutManager;
    private GifAdapter gifSearchAdapter;
    private GifSearchPreloader gifSearchPreloader = new GifSearchPreloader();
    private final Map<String, TLRPC.messages_BotResults> gifCache = new HashMap<>();
    private RecyclerListView.OnItemClickListener gifOnItemClickListener;
    private GifAdapter gifAdapter;
    private SearchField gifSearchField;
    private ScrollSlidingTabStrip gifTabs;
    private boolean firstGifAttach = true;
    private int gifRecentTabNum = -2;
    private int gifTrendingTabNum = -2;
    private int gifFirstEmojiTabNum = -2;

    private FrameLayout stickersContainer;
    private StickersGridAdapter stickersGridAdapter;
    private StickersSearchGridAdapter stickersSearchGridAdapter;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private ScrollSlidingTabStrip stickersTab;
    private RecyclerListView stickersGridView;
    private GridLayoutManager stickersLayoutManager;
    private SearchField stickersSearchField;
    private int stickersMinusDy;
    private boolean firstStickersAttach = true;

    private AnimatorSet searchAnimation;

    private TextView mediaBanTooltip;
    private DragListener dragListener;

    private final int[] tabsMinusDy = new int[3];
    private ObjectAnimator[] tabsYAnimators = new ObjectAnimator[3];

    @IntDef({Type.STICKERS, Type.EMOJIS, Type.GIFS})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Type {
        int STICKERS = 0;
        int EMOJIS = 1;
        int GIFS = 2;
    }

    private String[] lastSearchKeyboardLanguage;

    private Drawable[] tabIcons;
    private Drawable[] emojiIcons;
    private Drawable[] stickerIcons;
    private Drawable[] gifIcons;
    private String[] emojiTitles;

    private int searchFieldHeight;

    private int currentAccount = UserConfig.selectedAccount;
    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
    private int groupStickerPackNum;
    private int groupStickerPackPosition;
    private boolean groupStickersHidden;
    private TLRPC.TL_messages_stickerSet groupStickerSet;

    private ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private ArrayList<TLRPC.Document> recentStickers = new ArrayList<>();
    private ArrayList<TLRPC.Document> favouriteStickers = new ArrayList<>();

    private Paint dotPaint;

    private EmojiViewDelegate delegate;

    private int currentChatId;

    private TLRPC.StickerSetCovered[] primaryInstallingStickerSets = new TLRPC.StickerSetCovered[10];
    private LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets = new LongSparseArray<>();
    private LongSparseArray<TLRPC.StickerSetCovered> removingStickerSets = new LongSparseArray<>();

    private int currentPage;

    private EmojiColorPickerView pickerView;
    private EmojiPopupWindow pickerViewPopup;
    private int popupWidth;
    private int popupHeight;
    private int emojiSize;
    private int location[] = new int[2];
    private int stickersTabOffset;
    private int recentTabBum = -2;
    private int favTabBum = -2;
    private int trendingTabNum = -2;

    private TLRPC.ChatFull info;

    private boolean isLayout;
    private int currentBackgroundType = -1;
    private Object outlineProvider;
    private boolean forseMultiwindowLayout;

    private int lastNotifyWidth;
    private int lastNotifyHeight;
    private int lastNotifyHeight2;

    private boolean backspacePressed;
    private boolean backspaceOnce;
    private boolean showGifs;

    private ImageViewEmoji emojiTouchedView;
    private float emojiLastX;
    private float emojiLastY;
    private float emojiTouchedX;
    private float emojiTouchedY;

    public interface EmojiViewDelegate {
        default boolean onBackspace() {
            return false;
        }

        default void onEmojiSelected(String emoji) {

        }

        default void onStickerSelected(View view, TLRPC.Document sticker, Object parent, boolean notify, int scheduleDate) {

        }

        default void onStickersSettingsClick() {

        }

        default void onStickersGroupClick(int chatId) {

        }

        default void onGifSelected(View view, Object gif, Object parent, boolean notify, int scheduleDate) {

        }

        default void onTabOpened(int type) {

        }

        default void onClearEmojiRecent() {

        }

        default void onShowStickerSet(TLRPC.StickerSet stickerSet, TLRPC.InputStickerSet inputStickerSet) {

        }

        default void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet) {

        }

        default void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {

        }

        default void onSearchOpenClose(int type) {

        }

        default boolean isSearchOpened() {
            return false;
        }

        default boolean isExpanded() {
            return false;
        }

        default boolean canSchedule() {
            return false;
        }

        default boolean isInScheduleMode() {
            return false;
        }

        default long getDialogId() {
            return 0;
        }

        default void showTrendingStickersAlert(TrendingStickersLayout layout) {

        }
    }

    public interface DragListener {
        void onDragStart();
        void onDragEnd(float velocity);
        void onDragCancel();
        void onDrag(int offset);
    }

    private ContentPreviewViewer.ContentPreviewViewerDelegate contentPreviewViewerDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public void sendSticker(TLRPC.Document sticker, Object parent, boolean notify, int scheduleDate) {
            delegate.onStickerSelected(null, sticker, parent, notify, scheduleDate);
        }

        @Override
        public boolean needSend() {
            return true;
        }

        @Override
        public boolean canSchedule() {
            return delegate.canSchedule();
        }

        @Override
        public boolean isInScheduleMode() {
            return delegate.isInScheduleMode();
        }

        @Override
        public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {
            if (set == null) {
                return;
            }
            delegate.onShowStickerSet(null, set);
        }

        @Override
        public void sendGif(Object gif, Object parent, boolean notify, int scheduleDate) {
            if (gifGridView.getAdapter() == gifAdapter) {
                delegate.onGifSelected(null, gif, parent, notify, scheduleDate);
            } else if (gifGridView.getAdapter() == gifSearchAdapter) {
                delegate.onGifSelected(null, gif, parent, notify, scheduleDate);
            }
        }

        @Override
        public void gifAddedOrDeleted() {
            updateRecentGifs();
        }

        @Override
        public long getDialogId() {
            return delegate.getDialogId();
        }
    };

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

    private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {
        /* do nothing */
    };

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        stickersSearchField.searchEditText.setEnabled(enabled);
        gifSearchField.searchEditText.setEnabled(enabled);
        emojiSearchField.searchEditText.setEnabled(enabled);
    }

    private class SearchField extends FrameLayout {

        private View searchBackground;
        private ImageView searchIconImageView;
        private ImageView clearSearchImageView;
        private CloseProgressDrawable2 progressDrawable;
        private EditTextBoldCursor searchEditText;
        private View shadowView;
        private View backgroundView;
        private AnimatorSet shadowAnimator;

        public SearchField(Context context, int type) {
            super(context);

            shadowView = new View(context);
            shadowView.setAlpha(0.0f);
            shadowView.setTag(1);
            shadowView.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
            addView(shadowView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT));

            backgroundView = new View(context);
            backgroundView.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            addView(backgroundView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, searchFieldHeight));

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_chat_emojiSearchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 14, 14, 0));

            searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiSearchIcon), PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 14, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            progressDrawable.setSide(AndroidUtilities.dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            clearSearchImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiSearchIcon), PorterDuff.Mode.MULTIPLY));
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 14, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!searchEditText.isEnabled()) {
                        return super.onTouchEvent(event);
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!delegate.isSearchOpened()) {
                            openSearch(SearchField.this);
                        }
                        delegate.onSearchOpenClose(type == 1 ? 2 : 1);
                        searchEditText.requestFocus();
                        AndroidUtilities.showKeyboard(searchEditText);
                    }
                    return super.onTouchEvent(event);
                }
            };
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(Theme.getColor(Theme.key_chat_emojiSearchIcon));
            searchEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if (type == 0) {
                searchEditText.setHint(LocaleController.getString("SearchStickersHint", R.string.SearchStickersHint));
            } else if (type == 1) {
                searchEditText.setHint(LocaleController.getString("SearchEmojiHint", R.string.SearchEmojiHint));
            } else if (type == 2) {
                searchEditText.setHint(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
            }
            searchEditText.setCursorColor(Theme.getColor(Theme.key_featuredStickers_addedIcon));
            searchEditText.setCursorSize(AndroidUtilities.dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 12, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    if (type == 0) {
                        stickersSearchGridAdapter.search(searchEditText.getText().toString());
                    } else if (type == 1) {
                        emojiSearchAdapter.search(searchEditText.getText().toString());
                    } else if (type == 2) {
                        gifSearchAdapter.search(searchEditText.getText().toString());
                    }
                }
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }

        private void showShadow(boolean show, boolean animated) {
            if (show && shadowView.getTag() == null || !show && shadowView.getTag() != null) {
                return;
            }
            if (shadowAnimator != null) {
                shadowAnimator.cancel();
                shadowAnimator = null;
            }
            shadowView.setTag(show ? null : 1);
            if (animated) {
                shadowAnimator = new AnimatorSet();
                shadowAnimator.playTogether(ObjectAnimator.ofFloat(shadowView, View.ALPHA, show ? 1.0f : 0.0f));
                shadowAnimator.setDuration(200);
                shadowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                shadowAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        shadowAnimator = null;
                    }
                });
                shadowAnimator.start();
            } else {
                shadowView.setAlpha(show ? 1.0f : 0.0f);
            }
        }
    }

    private class TypedScrollListener extends RecyclerListView.OnScrollListener {

        @Type
        private final int type;

        private boolean smoothScrolling;

        public TypedScrollListener(@Type int type) {
            this.type = type;
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager().isSmoothScrolling()) {
                smoothScrolling = true;
                return;
            }
            if (newState == RecyclerListView.SCROLL_STATE_IDLE) {
                if (!smoothScrolling) {
                    animateTabsY(type);
                }
                smoothScrolling = false;
            } else {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    final SearchField searchField = getSearchFieldForType(type);
                    if (searchField != null) {
                        searchField.hideKeyboard();
                    }
                    smoothScrolling = false;
                }
                if (!smoothScrolling) {
                    stopAnimatingTabsY(type);
                }
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            checkScroll(type);
            checkTabsY(type, dy);
            checkSearchFieldScroll();
            if (!smoothScrolling) {
                checkBottomTabScroll(dy);
            }
        }

        private void checkSearchFieldScroll() {
            switch (type) {
                case Type.STICKERS:
                    checkStickersSearchFieldScroll(false);
                    break;
                case Type.EMOJIS:
                    checkEmojiSearchFieldScroll(false);
                    break;
                case Type.GIFS:
                    checkGifSearchFieldScroll(false);
                    break;
            }
        }
    }

    private class DraggableScrollSlidingTabStrip extends ScrollSlidingTabStrip {

        private final int touchSlop;

        private boolean startedScroll;
        private float lastX;
        private float lastTranslateX;
        private boolean first = true;
        private float downX, downY;
        private boolean draggingVertically, draggingHorizontally;
        private VelocityTracker vTracker;

        public DraggableScrollSlidingTabStrip(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                draggingVertically = draggingHorizontally = false;
                downX = ev.getRawX();
                downY = ev.getRawY();
            } else {
                if (!draggingVertically && !draggingHorizontally && dragListener != null) {
                    if (Math.abs(ev.getRawY() - downY) >= touchSlop) {
                        draggingVertically = true;
                        downY = ev.getRawY();
                        dragListener.onDragStart();
                        if (startedScroll) {
                            pager.endFakeDrag();
                            startedScroll = false;
                        }
                        return true;
                    }
                }
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (first) {
                first = false;
                lastX = ev.getX();
            }
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                draggingVertically = draggingHorizontally = false;
                downX = ev.getRawX();
                downY = ev.getRawY();
            } else {
                if (!draggingVertically && !draggingHorizontally && dragListener != null) {
                    if (Math.abs(ev.getRawX() - downX) >= touchSlop) {
                        draggingHorizontally = true;
                    } else if (Math.abs(ev.getRawY() - downY) >= touchSlop) {
                        draggingVertically = true;
                        downY = ev.getRawY();
                        dragListener.onDragStart();
                        if (startedScroll) {
                            pager.endFakeDrag();
                            startedScroll = false;
                        }
                    }
                }
            }
            if (draggingVertically) {
                if (vTracker == null) {
                    vTracker = VelocityTracker.obtain();
                }
                vTracker.addMovement(ev);
                if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    vTracker.computeCurrentVelocity(1000);
                    float velocity = vTracker.getYVelocity();
                    vTracker.recycle();
                    vTracker = null;
                    if (ev.getAction() == MotionEvent.ACTION_UP) {
                        dragListener.onDragEnd(velocity);
                    } else {
                        dragListener.onDragCancel();
                    }
                    first = true;
                    draggingVertically = draggingHorizontally = false;
                } else {
                    dragListener.onDrag(Math.round(ev.getRawY() - downY));
                }
                return true;
            }
            float newTranslationX = getTranslationX();
            if (getScrollX() == 0 && newTranslationX == 0) {
                if (!startedScroll && lastX - ev.getX() < 0) {
                    if (pager.beginFakeDrag()) {
                        startedScroll = true;
                        lastTranslateX = getTranslationX();
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
                    //pager.fakeDragBy(dx);
                    lastTranslateX = newTranslationX;
                } catch (Exception e) {
                    try {
                        pager.endFakeDrag();
                    } catch (Exception ignore) {

                    }
                    startedScroll = false;
                    FileLog.e(e);
                }
            }
            lastX = ev.getX();
            if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
                first = true;
                draggingVertically = draggingHorizontally = false;
                if (startedScroll) {
                    pager.endFakeDrag();
                    startedScroll = false;
                }
            }
            return startedScroll || super.onTouchEvent(ev);
        }
    }

    private class ImageViewEmoji extends ImageView {
        private boolean isRecent;

        public ImageViewEmoji(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.CENTER);
        }

        private void sendEmoji(String override) {
            showBottomTab(true, true);
            String code = override != null ? override : (String) getTag();
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(code);
            if (override == null) {
                if (!isRecent) {
                    String color = Emoji.emojiColor.get(code);
                    if (color != null) {
                        code = addColorToCode(code, color);
                    }
                }
                addEmojiToRecent(code);
                if (delegate != null) {
                    delegate.onEmojiSelected(Emoji.fixEmoji(code));
                }
            } else {
                if (delegate != null) {
                    delegate.onEmojiSelected(Emoji.fixEmoji(override));
                }
            }
        }

        public void setImageDrawable(Drawable drawable, boolean recent) {
            super.setImageDrawable(drawable);
            isRecent = recent;
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
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
                FileLog.e(e);
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
            } catch (Exception ignore) {

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
            Theme.setDrawableColor(backgroundDrawable, Theme.getColor(Theme.key_dialogBackground));
            Theme.setDrawableColor(arrowDrawable, Theme.getColor(Theme.key_dialogBackground));
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

    public EmojiView(boolean needStickers, boolean needGif, final Context context, boolean needSearch, final TLRPC.ChatFull chatFull) {
        super(context);

        int color = Theme.getColor(Theme.key_chat_emojiBottomPanelIcon);
        color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));

        searchFieldHeight = AndroidUtilities.dp(64);
        needEmojiSearch = needSearch;

        tabIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_smiles, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_gif, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_stickers, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected))
        };

        emojiIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_recent, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_smiles, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_cat, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_food, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_activities, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_travel, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_objects, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_other, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_panel_flags, Theme.getColor(Theme.key_chat_emojiPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
        };

        stickerIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_recent, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_favorites, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_trending3, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                new LayerDrawable(new Drawable[]{
                        Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_trending1, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                        Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_trending2, Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine), Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine))
                })
        };

        gifIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_recent, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_gifs_trending, Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), Theme.getColor(Theme.key_chat_emojiPanelIconSelected)),
        };

        emojiTitles = new String[]{
                LocaleController.getString("Emoji1", R.string.Emoji1),
                LocaleController.getString("Emoji2", R.string.Emoji2),
                LocaleController.getString("Emoji3", R.string.Emoji3),
                LocaleController.getString("Emoji4", R.string.Emoji4),
                LocaleController.getString("Emoji5", R.string.Emoji5),
                LocaleController.getString("Emoji6", R.string.Emoji6),
                LocaleController.getString("Emoji7", R.string.Emoji7),
                LocaleController.getString("Emoji8", R.string.Emoji8)
        };

        showGifs = needGif;
        info = chatFull;

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelNewTrending));

        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), AndroidUtilities.dp(6));
                }
            };
        }

        emojiContainer = new FrameLayout(context);
        views.add(emojiContainer);

        emojiGridView = new RecyclerListView(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                ignoreLayout = true;
                int width = MeasureSpec.getSize(widthSpec);
                emojiLayoutManager.setSpanCount(Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45)));
                ignoreLayout = false;
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (needEmojiSearch && firstEmojiAttach) {
                    ignoreLayout = true;
                    emojiLayoutManager.scrollToPositionWithOffset(1, 0);
                    firstEmojiAttach = false;
                    ignoreLayout = false;
                }
                super.onLayout(changed, l, t, r, b);
                checkEmojiSearchFieldScroll(true);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (emojiTouchedView != null) {
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
                            String code = (String) emojiTouchedView.getTag();
                            if (!emojiTouchedView.isRecent) {
                                if (color != null) {
                                    Emoji.emojiColor.put(code, color);
                                    code = addColorToCode(code, color);
                                } else {
                                    Emoji.emojiColor.remove(code);
                                }
                                emojiTouchedView.setImageDrawable(Emoji.getEmojiBigDrawable(code), emojiTouchedView.isRecent);
                                emojiTouchedView.sendEmoji(null);
                                Emoji.saveEmojiColors();
                            } else {
                                code = code.replace("\uD83C\uDFFB", "")
                                        .replace("\uD83C\uDFFC", "")
                                        .replace("\uD83C\uDFFD", "")
                                        .replace("\uD83C\uDFFE", "")
                                        .replace("\uD83C\uDFFF", "");

                                if (color != null) {
                                    emojiTouchedView.sendEmoji(addColorToCode(code, color));
                                } else {
                                    emojiTouchedView.sendEmoji(code);
                                }
                            }
                        }
                        emojiTouchedView = null;
                        emojiTouchedX = -10000;
                        emojiTouchedY = -10000;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        boolean ignore = false;
                        if (emojiTouchedX != -10000) {
                            if (Math.abs(emojiTouchedX - event.getX()) > AndroidUtilities.getPixelsInCM(0.2f, true) || Math.abs(emojiTouchedY - event.getY()) > AndroidUtilities.getPixelsInCM(0.2f, false)) {
                                emojiTouchedX = -10000;
                                emojiTouchedY = -10000;
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
                    return true;
                }
                emojiLastX = event.getX();
                emojiLastY = event.getY();
                return super.onTouchEvent(event);
            }
        };
        emojiGridView.setInstantClick(true);
        emojiGridView.setLayoutManager(emojiLayoutManager = new GridLayoutManager(context, 8));
        emojiGridView.setTopGlowOffset(AndroidUtilities.dp(38));
        emojiGridView.setBottomGlowOffset(AndroidUtilities.dp(48));
        emojiGridView.setPadding(0, AndroidUtilities.dp(38), 0, AndroidUtilities.dp(44));
        emojiGridView.setGlowColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        emojiGridView.setClipToPadding(false);
        emojiLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (emojiGridView.getAdapter() == emojiSearchAdapter) {
                    if (position == 0 || position == 1 && emojiSearchAdapter.searchWas && emojiSearchAdapter.result.isEmpty()) {
                        return emojiLayoutManager.getSpanCount();
                    }
                } else {
                    if (needEmojiSearch && position == 0 || emojiAdapter.positionToSection.indexOfKey(position) >= 0) {
                        return emojiLayoutManager.getSpanCount();
                    }
                }
                return 1;
            }
        });
        emojiGridView.setAdapter(emojiAdapter = new EmojiGridAdapter());
        emojiSearchAdapter = new EmojiSearchAdapter();
        emojiContainer.addView(emojiGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emojiGridView.setOnScrollListener(new TypedScrollListener(Type.EMOJIS) {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int position = emojiLayoutManager.findFirstVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    int tab = 0;
                    int count = Emoji.recentEmoji.size() + (needEmojiSearch ? 1 : 0);
                    if (position >= count) {
                        for (int a = 0; a < EmojiData.dataColored.length; a++) {
                            int size = EmojiData.dataColored[a].length + 1;
                            if (position < count + size) {
                                tab = a + (Emoji.recentEmoji.isEmpty() ? 0 : 1);
                                break;
                            }
                            count += size;
                        }
                    }
                    emojiTabs.onPageScrolled(tab, 0);
                }
                super.onScrolled(recyclerView, dx, dy);
            }
        });
        emojiGridView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (view instanceof ImageViewEmoji) {
                    ImageViewEmoji viewEmoji = (ImageViewEmoji) view;
                    viewEmoji.sendEmoji(null);
                }
            }
        });
        emojiGridView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (view instanceof ImageViewEmoji) {
                    ImageViewEmoji viewEmoji = (ImageViewEmoji) view;
                    String code = (String) viewEmoji.getTag();

                    String color = null;

                    String toCheck = code.replace("\uD83C\uDFFB", "");
                    if (toCheck != code) {
                        color = "\uD83C\uDFFB";
                    }
                    if (color == null) {
                        toCheck = code.replace("\uD83C\uDFFC", "");
                        if (toCheck != code) {
                            color = "\uD83C\uDFFC";
                        }
                    }
                    if (color == null) {
                        toCheck = code.replace("\uD83C\uDFFD", "");
                        if (toCheck != code) {
                            color = "\uD83C\uDFFD";
                        }
                    }
                    if (color == null) {
                        toCheck = code.replace("\uD83C\uDFFE", "");
                        if (toCheck != code) {
                            color = "\uD83C\uDFFE";
                        }
                    }
                    if (color == null) {
                        toCheck = code.replace("\uD83C\uDFFF", "");
                        if (toCheck != code) {
                            color = "\uD83C\uDFFF";
                        }
                    }

                    if (EmojiData.emojiColoredMap.contains(toCheck)) {
                        emojiTouchedView = viewEmoji;
                        emojiTouchedX = emojiLastX;
                        emojiTouchedY = emojiLastY;

                        if (color == null && !viewEmoji.isRecent) {
                            color = Emoji.emojiColor.get(toCheck);
                        }

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
                        viewEmoji.getLocationOnScreen(location);
                        int x = emojiSize * pickerView.getSelection() + AndroidUtilities.dp(4 * pickerView.getSelection() - (AndroidUtilities.isTablet() ? 5 : 1));
                        if (location[0] - x < AndroidUtilities.dp(5)) {
                            x += (location[0] - x) - AndroidUtilities.dp(5);
                        } else if (location[0] - x + popupWidth > AndroidUtilities.displaySize.x - AndroidUtilities.dp(5)) {
                            x += (location[0] - x + popupWidth) - (AndroidUtilities.displaySize.x - AndroidUtilities.dp(5));
                        }
                        int xOffset = -x;
                        int yOffset = viewEmoji.getTop() < 0 ? viewEmoji.getTop() : 0;

                        pickerView.setEmoji(toCheck, AndroidUtilities.dp(AndroidUtilities.isTablet() ? 30 : 22) - xOffset + (int) AndroidUtilities.dpf2(0.5f));

                        pickerViewPopup.setFocusable(true);
                        pickerViewPopup.showAsDropDown(view, xOffset, -view.getMeasuredHeight() - popupHeight + (view.getMeasuredHeight() - emojiSize) / 2 - yOffset);
                        pager.requestDisallowInterceptTouchEvent(true);
                        emojiGridView.hideSelector(true);
                        return true;
                    } else if (viewEmoji.isRecent) {
                        RecyclerListView.ViewHolder holder = emojiGridView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() <= Emoji.recentEmoji.size()) {
                            delegate.onClearEmojiRecent();
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        emojiTabs = new ScrollSlidingTabStrip(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (emojiTabsShadow != null) {
                    emojiTabsShadow.setTranslationY(translationY);
                }
            }
        };
        if (needSearch) {
            emojiSearchField = new SearchField(context, 1);
            emojiContainer.addView(emojiSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));
            emojiSearchField.searchEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        lastSearchKeyboardLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(lastSearchKeyboardLanguage);
                    }
                }
            });
        }

        emojiTabs.setShouldExpand(true);
        emojiTabs.setIndicatorHeight(-1);
        emojiTabs.setUnderlineHeight(-1);
        emojiTabs.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        emojiContainer.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38));
        emojiTabs.setDelegate(new ScrollSlidingTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int page) {
                if (!Emoji.recentEmoji.isEmpty()) {
                    if (page == 0) {
                        emojiLayoutManager.scrollToPositionWithOffset(needEmojiSearch ? 1 : 0, 0);
                        return;
                    } else {
                        page--;
                    }
                }
                emojiGridView.stopScroll();
                emojiLayoutManager.scrollToPositionWithOffset(emojiAdapter.sectionToPosition.get(page), 0);
                checkEmojiTabY(null, 0);
            }
        });

        emojiTabsShadow = new View(context);
        emojiTabsShadow.setAlpha(0.0f);
        emojiTabsShadow.setTag(1);
        emojiTabsShadow.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        layoutParams.topMargin = AndroidUtilities.dp(38);
        emojiContainer.addView(emojiTabsShadow, layoutParams);

        if (needStickers) {
            if (needGif) {
                gifContainer = new FrameLayout(context);
                views.add(gifContainer);

                gifGridView = new RecyclerListView(context) {

                    private boolean ignoreLayout;
                    private boolean wasMeasured;

                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent event) {
                        boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gifGridView, 0, contentPreviewViewerDelegate);
                        return super.onInterceptTouchEvent(event) || result;
                    }

                    @Override
                    protected void onMeasure(int widthSpec, int heightSpec) {
                        super.onMeasure(widthSpec, heightSpec);
                        if (!wasMeasured) {
                            gifAdapter.notifyDataSetChanged();
                            wasMeasured = true;
                        }
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        if (firstGifAttach && gifAdapter.getItemCount() > 1) {
                            ignoreLayout = true;
                            gifLayoutManager.scrollToPositionWithOffset(1, 0);
                            gifSearchField.setVisibility(VISIBLE);
                            gifTabs.onPageScrolled(0, 0);
                            firstGifAttach = false;
                            ignoreLayout = false;
                        }
                        super.onLayout(changed, l, t, r, b);
                        checkGifSearchFieldScroll(true);
                    }

                    @Override
                    public void requestLayout() {
                        if (ignoreLayout) {
                            return;
                        }
                        super.requestLayout();
                    }
                };
                gifGridView.setClipToPadding(false);
                gifGridView.setLayoutManager(gifLayoutManager = new GifLayoutManager(context));
                gifGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
                    @Override
                    public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                        int position = parent.getChildAdapterPosition(view);

                        if (gifGridView.getAdapter() == gifAdapter && position == gifAdapter.trendingSectionItem) {
                            outRect.set(0, 0, 0, 0);
                            return;
                        }

                        if (position != 0) {
                            outRect.left = 0;
                            outRect.bottom = 0;
                            outRect.top = AndroidUtilities.dp(2);
                            outRect.right = gifLayoutManager.isLastInRow(position - 1) ? 0 : AndroidUtilities.dp(2);
                        } else {
                            outRect.set(0, 0, 0, 0);
                        }
                    }
                });
                gifGridView.setPadding(0, AndroidUtilities.dp(48 + 4), 0, AndroidUtilities.dp(44));
                gifGridView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                ((SimpleItemAnimator) gifGridView.getItemAnimator()).setSupportsChangeAnimations(false);
                gifGridView.setAdapter(gifAdapter = new GifAdapter(context, true));
                gifSearchAdapter = new GifAdapter(context);
                gifGridView.setOnScrollListener(new TypedScrollListener(Type.GIFS));
                gifGridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gifGridView, 0, gifOnItemClickListener, contentPreviewViewerDelegate));
                gifOnItemClickListener = (view, position) -> {
                    if (delegate == null) {
                        return;
                    }
                    position--;
                    if (gifGridView.getAdapter() == gifAdapter) {
                        if (position < 0) {
                            return;
                        }
                        if (position < gifAdapter.recentItemsCount) {
                            delegate.onGifSelected(view, recentGifs.get(position), "gif", true, 0);
                        } else {
                            int resultPos = position;
                            if (gifAdapter.recentItemsCount > 0) {
                                resultPos -= gifAdapter.recentItemsCount;
                                resultPos--; // trending section item
                            }
                            if (resultPos >= 0 && resultPos < gifAdapter.results.size()) {
                                delegate.onGifSelected(view, gifAdapter.results.get(resultPos), gifAdapter.bot, true, 0);
                            }
                        }
                    } else if (gifGridView.getAdapter() == gifSearchAdapter) {
                        if (position < 0 || position >= gifSearchAdapter.results.size()) {
                            return;
                        }
                        delegate.onGifSelected(view, gifSearchAdapter.results.get(position), gifSearchAdapter.bot, true, 0);
                        updateRecentGifs();
                    }
                };

                gifGridView.setOnItemClickListener(gifOnItemClickListener);
                gifContainer.addView(gifGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                gifSearchField = new SearchField(context, 2);
                gifSearchField.setVisibility(INVISIBLE);
                gifContainer.addView(gifSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));

                gifTabs = new DraggableScrollSlidingTabStrip(context);
                gifTabs.setType(ScrollSlidingTabStrip.Type.TAB);
                gifTabs.setUnderlineHeight(AndroidUtilities.getShadowHeight());
                gifTabs.setIndicatorColor(Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
                gifTabs.setUnderlineColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
                gifTabs.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
                gifContainer.addView(gifTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
                updateGifTabs();

                gifTabs.setDelegate(page -> {
                    if (page == gifTrendingTabNum && gifAdapter.results.isEmpty()) {
                        return;
                    }
                    gifGridView.stopScroll();
                    gifTabs.onPageScrolled(page, 0);
                    if (page == gifRecentTabNum || page == gifTrendingTabNum) {
                        gifSearchField.searchEditText.setText("");
                        if (page == gifTrendingTabNum && gifAdapter.trendingSectionItem >= 1) {
                            gifLayoutManager.scrollToPositionWithOffset(gifAdapter.trendingSectionItem, -AndroidUtilities.dp(4));
                        } else {
                            gifLayoutManager.scrollToPositionWithOffset(delegate != null && delegate.isExpanded() ? 0 : 1, 0);
                        }
                        if (page == gifTrendingTabNum) {
                            final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
                            if (!gifSearchEmojies.isEmpty()) {
                                gifSearchPreloader.preload(gifSearchEmojies.get(0));
                            }
                        }
                    } else {
                        final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
                        gifSearchAdapter.searchEmoji(gifSearchEmojies.get(page - gifFirstEmojiTabNum));
                        if (page - gifFirstEmojiTabNum > 0) {
                            gifSearchPreloader.preload(gifSearchEmojies.get(page - gifFirstEmojiTabNum - 1));
                        }
                        if (page - gifFirstEmojiTabNum < gifSearchEmojies.size() - 1) {
                            gifSearchPreloader.preload(gifSearchEmojies.get(page - gifFirstEmojiTabNum + 1));
                        }
                    }
                    resetTabsY(Type.GIFS);
                });

                gifAdapter.loadTrendingGifs();
            }

            stickersContainer = new FrameLayout(context);

            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
            MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
            stickersGridView = new RecyclerListView(context) {

                boolean ignoreLayout;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersGridView, EmojiView.this.getMeasuredHeight(), contentPreviewViewerDelegate);
                    return super.onInterceptTouchEvent(event) || result;
                }

                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    if (firstStickersAttach && stickersGridAdapter.getItemCount() > 0) {
                        ignoreLayout = true;
                        stickersLayoutManager.scrollToPositionWithOffset(1, 0);
                        firstStickersAttach = false;
                        ignoreLayout = false;
                    }
                    super.onLayout(changed, l, t, r, b);
                    checkStickersSearchFieldScroll(true);
                }

                @Override
                public void requestLayout() {
                    if (ignoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };

            stickersGridView.setLayoutManager(stickersLayoutManager = new GridLayoutManager(context, 5));
            stickersLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (stickersGridView.getAdapter() == stickersGridAdapter) {
                        if (position == 0) {
                            return stickersGridAdapter.stickersPerRow;
                        }
                        if (position != stickersGridAdapter.totalItems) {
                            Object object = stickersGridAdapter.cache.get(position);
                            if (object == null || stickersGridAdapter.cache.get(position) instanceof TLRPC.Document) {
                                return 1;
                            }
                        }
                        return stickersGridAdapter.stickersPerRow;
                    } else {
                        if (position != stickersSearchGridAdapter.totalItems) {
                            Object object = stickersSearchGridAdapter.cache.get(position);
                            if (object == null || stickersSearchGridAdapter.cache.get(position) instanceof TLRPC.Document) {
                                return 1;
                            }
                        }
                        return stickersGridAdapter.stickersPerRow;
                    }
                }
            });
            stickersGridView.setPadding(0, AndroidUtilities.dp(4 + 48), 0, AndroidUtilities.dp(44));
            stickersGridView.setClipToPadding(false);
            views.add(stickersContainer);
            stickersSearchGridAdapter = new StickersSearchGridAdapter(context);
            stickersGridView.setAdapter(stickersGridAdapter = new StickersGridAdapter(context));
            stickersGridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, stickersGridView, EmojiView.this.getMeasuredHeight(), stickersOnItemClickListener, contentPreviewViewerDelegate));
            stickersOnItemClickListener = (view, position) -> {
                if (stickersGridView.getAdapter() == stickersSearchGridAdapter) {
                    TLRPC.StickerSetCovered pack = stickersSearchGridAdapter.positionsToSets.get(position);
                    if (pack != null) {
                        delegate.onShowStickerSet(pack.set, null);
                        return;
                    }
                }
                if (!(view instanceof StickerEmojiCell)) {
                    return;
                }
                ContentPreviewViewer.getInstance().reset();
                StickerEmojiCell cell = (StickerEmojiCell) view;
                if (cell.isDisabled()) {
                    return;
                }
                cell.disable();
                delegate.onStickerSelected(cell, cell.getSticker(), cell.getParentObject(), true, 0);
            };
            stickersGridView.setOnItemClickListener(stickersOnItemClickListener);
            stickersGridView.setGlowColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            stickersContainer.addView(stickersGridView);

            stickersSearchField = new SearchField(context, 0);
            stickersContainer.addView(stickersSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));

            stickersTab = new DraggableScrollSlidingTabStrip(context);
            stickersTab.setType(ScrollSlidingTabStrip.Type.TAB);
            stickersTab.setUnderlineHeight(AndroidUtilities.getShadowHeight());
            stickersTab.setIndicatorColor(Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            stickersTab.setUnderlineColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
            stickersTab.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            stickersContainer.addView(stickersTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            updateStickerTabs();
            stickersTab.setDelegate(page -> {
                if (page == trendingTabNum) {
                    final TrendingStickersLayout.Delegate trendingDelegate = new TrendingStickersLayout.Delegate() {
                        @Override
                        public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
                            delegate.onStickerSetAdd(stickerSet);
                            if (primary) {
                                updateStickerTabs();
                            }
                        }

                        @Override
                        public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                            delegate.onStickerSetRemove(stickerSet);
                        }

                        @Override
                        public boolean onListViewInterceptTouchEvent(RecyclerListView listView, MotionEvent event) {
                            return ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, EmojiView.this.getMeasuredHeight(), contentPreviewViewerDelegate);
                        }

                        @Override
                        public boolean onListViewTouchEvent(RecyclerListView listView, RecyclerListView.OnItemClickListener onItemClickListener, MotionEvent event) {
                            return ContentPreviewViewer.getInstance().onTouch(event, listView, EmojiView.this.getMeasuredHeight(), onItemClickListener, contentPreviewViewerDelegate);
                        }

                        @Override
                        public String[] getLastSearchKeyboardLanguage() {
                            return lastSearchKeyboardLanguage;
                        }

                        @Override
                        public void setLastSearchKeyboardLanguage(String[] language) {
                            lastSearchKeyboardLanguage = language;
                        }

                        @Override
                        public boolean canSendSticker() {
                            return true;
                        }

                        @Override
                        public void onStickerSelected(TLRPC.Document sticker, Object parent, boolean clearsInputField, boolean notify, int scheduleDate) {
                            delegate.onStickerSelected(null, sticker, parent, notify, scheduleDate);
                        }

                        @Override
                        public boolean canSchedule() {
                            return delegate.canSchedule();
                        }

                        @Override
                        public boolean isInScheduleMode() {
                            return delegate.isInScheduleMode();
                        }
                    };
                    this.delegate.showTrendingStickersAlert(new TrendingStickersLayout(context, trendingDelegate, primaryInstallingStickerSets, installingStickerSets, removingStickerSets));
                    return;
                } else if (page == recentTabBum) {
                    stickersGridView.stopScroll();
                    stickersLayoutManager.scrollToPositionWithOffset(stickersGridAdapter.getPositionForPack("recent"), 0);
                    resetTabsY(Type.STICKERS);
                    stickersTab.onPageScrolled(recentTabBum, recentTabBum > 0 ? recentTabBum : stickersTabOffset);
                    return;
                } else if (page == favTabBum) {
                    stickersGridView.stopScroll();
                    stickersLayoutManager.scrollToPositionWithOffset(stickersGridAdapter.getPositionForPack("fav"), 0);
                    resetTabsY(Type.STICKERS);
                    stickersTab.onPageScrolled(favTabBum, favTabBum > 0 ? favTabBum : stickersTabOffset);
                    return;
                }

                int index = page - stickersTabOffset;
                if (index >= stickerSets.size()) {
                    return;
                }
                if (index >= stickerSets.size()) {
                    index = stickerSets.size() - 1;
                }
                firstStickersAttach = false;
                stickersGridView.stopScroll();
                stickersLayoutManager.scrollToPositionWithOffset(stickersGridAdapter.getPositionForPack(stickerSets.get(index)), 0);
                resetTabsY(Type.STICKERS);
                checkScroll(Type.STICKERS);
            });

            stickersGridView.setOnScrollListener(new TypedScrollListener(Type.STICKERS));
        }

        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public void setCurrentItem(int item, boolean smoothScroll) {
                startStopVisibleGifs(item == 1);
                if (item == getCurrentItem()) {
                    if (item == 0) {
                        emojiGridView.smoothScrollToPosition(needEmojiSearch ? 1 : 0);
                    } else if (item == 1) {
                        gifGridView.smoothScrollToPosition(1);
                    } else {
                        stickersGridView.smoothScrollToPosition(1);
                    }
                    return;
                }
                super.setCurrentItem(item, smoothScroll);
            }
        };
        pager.setAdapter(new EmojiPagesAdapter());

        topShadow = new View(context);
        topShadow.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, 0xffe2e5e7));
        addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 6));

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
                        if (delegate != null && delegate.onBackspace()) {
                            backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    }
                }
                super.onTouchEvent(event);
                return true;
            }
        };
        backspaceButton.setImageResource(R.drawable.smiles_tab_clear);
        backspaceButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        backspaceButton.setScaleType(ImageView.ScaleType.CENTER);
        backspaceButton.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
        backspaceButton.setFocusable(true);
        backspaceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        bottomTabContainer = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
        bottomTabContainer.addView(shadowLine, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

        bottomTabContainerBackground = new View(context);
        bottomTabContainer.addView(bottomTabContainerBackground, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44), Gravity.LEFT | Gravity.BOTTOM));

        if (needSearch) {
            addView(bottomTabContainer, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44) + AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));
            bottomTabContainer.addView(backspaceButton, LayoutHelper.createFrame(52, 44, Gravity.BOTTOM | Gravity.RIGHT));
            if (Build.VERSION.SDK_INT >= 21) {
                backspaceButton.setBackground(Theme.createSelectorDrawable(color));
            }

            stickerSettingsButton = new ImageView(context);
            stickerSettingsButton.setImageResource(R.drawable.smiles_tab_settings);
            stickerSettingsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            stickerSettingsButton.setScaleType(ImageView.ScaleType.CENTER);
            stickerSettingsButton.setFocusable(true);
            if (Build.VERSION.SDK_INT >= 21) {
                stickerSettingsButton.setBackground(Theme.createSelectorDrawable(color));
            }
            stickerSettingsButton.setContentDescription(LocaleController.getString("Settings", R.string.Settings));
            bottomTabContainer.addView(stickerSettingsButton, LayoutHelper.createFrame(52, 44, Gravity.BOTTOM | Gravity.RIGHT));
            stickerSettingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (delegate != null) {
                        delegate.onStickersSettingsClick();
                    }
                }
            });

            typeTabs = new PagerSlidingTabStrip(context);
            typeTabs.setViewPager(pager);
            typeTabs.setShouldExpand(false);
            typeTabs.setIndicatorHeight(0);
            typeTabs.setUnderlineHeight(0);
            typeTabs.setTabPaddingLeftRight(AndroidUtilities.dp(10));
            bottomTabContainer.addView(typeTabs, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
            typeTabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    EmojiView.this.onPageScrolled(position, getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), positionOffsetPixels);
                    showBottomTab(true, true);
                    SearchField currentField;
                    int p = pager.getCurrentItem();
                    if (p == 0) {
                        currentField = emojiSearchField;
                    } else if (p == 1) {
                        currentField = gifSearchField;
                    } else {
                        currentField = stickersSearchField;
                    }
                    String currentFieldText = currentField.searchEditText.getText().toString();
                    for (int a = 0; a < 3; a++) {
                        SearchField field;
                        if (a == 0) {
                            field = emojiSearchField;
                        } else if (a == 1) {
                            field = gifSearchField;
                        } else {
                            field = stickersSearchField;
                        }
                        if (field == null || field == currentField || field.searchEditText == null || field.searchEditText.getText().toString().equals(currentFieldText)) {
                            continue;
                        }
                        field.searchEditText.setText(currentFieldText);
                        field.searchEditText.setSelection(currentFieldText.length());
                    }
                    startStopVisibleGifs((position == 0 && positionOffset > 0) || position == 1);
                }

                @Override
                public void onPageSelected(int position) {
                    saveNewPage();
                    showBackspaceButton(position == 0, true);
                    showStickerSettingsButton(position == 2, true);
                    if (delegate.isSearchOpened()) {
                        if (position == 0) {
                            if (emojiSearchField != null) {
                                emojiSearchField.searchEditText.requestFocus();
                            }
                        } else if (position == 1) {
                            if (gifSearchField != null) {
                                gifSearchField.searchEditText.requestFocus();
                            }
                        } else {
                            if (stickersSearchField != null) {
                                stickersSearchField.searchEditText.requestFocus();
                            }
                        }
                    }
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });

            searchButton = new ImageView(context);
            searchButton.setImageResource(R.drawable.smiles_tab_search);
            searchButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            searchButton.setScaleType(ImageView.ScaleType.CENTER);
            searchButton.setContentDescription(LocaleController.getString("Search", R.string.Search));
            searchButton.setFocusable(true);
            if (Build.VERSION.SDK_INT >= 21) {
                searchButton.setBackground(Theme.createSelectorDrawable(color));
            }
            bottomTabContainer.addView(searchButton, LayoutHelper.createFrame(52, 44, Gravity.BOTTOM | Gravity.LEFT));
            searchButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    SearchField currentField;
                    int currentItem = pager.getCurrentItem();
                    if (currentItem == 0) {
                        currentField = emojiSearchField;
                    } else if (currentItem == 1) {
                        currentField = gifSearchField;
                    } else {
                        currentField = stickersSearchField;
                    }
                    if (currentField == null) {
                        return;
                    }
                    currentField.searchEditText.requestFocus();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
                    currentField.searchEditText.onTouchEvent(event);
                    event.recycle();
                    event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0);
                    currentField.searchEditText.onTouchEvent(event);
                    event.recycle();
                }
            });
        } else {
            addView(bottomTabContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 40 : 44) + 20, (Build.VERSION.SDK_INT >= 21 ? 40 : 44) + 12, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 0, 0, 2, 0));

            Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chat_emojiPanelBackground), Theme.getColor(Theme.key_chat_emojiPanelBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                drawable = combinedDrawable;
            } else {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                backspaceButton.setStateListAnimator(animator);
                backspaceButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                    }
                });
            }
            backspaceButton.setPadding(0, 0, AndroidUtilities.dp(2), 0);
            backspaceButton.setBackground(drawable);
            backspaceButton.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
            backspaceButton.setFocusable(true);
            bottomTabContainer.addView(backspaceButton, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 40 : 44), (Build.VERSION.SDK_INT >= 21 ? 40 : 44), Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));
            shadowLine.setVisibility(GONE);
            bottomTabContainerBackground.setVisibility(GONE);
        }

        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mediaBanTooltip = new CorrectlyMeasuringTextView(context);
        mediaBanTooltip.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
        mediaBanTooltip.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        mediaBanTooltip.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
        mediaBanTooltip.setGravity(Gravity.CENTER_VERTICAL);
        mediaBanTooltip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        mediaBanTooltip.setVisibility(INVISIBLE);
        addView(mediaBanTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 5, 0, 5, 48 + 5));

        emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);
        pickerView = new EmojiColorPickerView(context);
        pickerViewPopup = new EmojiPopupWindow(pickerView, popupWidth = AndroidUtilities.dp((AndroidUtilities.isTablet() ? 40 : 32) * 6 + 10 + 4 * 5), popupHeight = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 64 : 56));
        pickerViewPopup.setOutsideTouchable(true);
        pickerViewPopup.setClippingEnabled(true);
        pickerViewPopup.setInputMethodMode(EmojiPopupWindow.INPUT_METHOD_NOT_NEEDED);
        pickerViewPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        pickerViewPopup.getContentView().setFocusableInTouchMode(true);
        pickerViewPopup.getContentView().setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && pickerViewPopup != null && pickerViewPopup.isShowing()) {
                pickerViewPopup.dismiss();
                return true;
            }
            return false;
        });
        currentPage = MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0);
        Emoji.loadRecentEmoji();
        emojiAdapter.notifyDataSetChanged();

        if (typeTabs != null) {
            if (views.size() == 1 && typeTabs.getVisibility() == VISIBLE) {
                typeTabs.setVisibility(INVISIBLE);
            } else if (views.size() != 1 && typeTabs.getVisibility() != VISIBLE) {
                typeTabs.setVisibility(VISIBLE);
            }
        }
    }

    private static String addColorToCode(String code, String color) {
        String end = null;
        int length = code.length();
        if (length > 2 && code.charAt(code.length() - 2) == '\u200D') {
            end = code.substring(code.length() - 2);
            code = code.substring(0, code.length() - 2);
        } else if (length > 3 && code.charAt(code.length() - 3) == '\u200D') {
            end = code.substring(code.length() - 3);
            code = code.substring(0, code.length() - 3);
        }
        code += color;
        if (end != null) {
            code += end;
        }
        return code;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (bottomTabContainer.getTag() == null && (delegate == null || !delegate.isSearchOpened())) {
            View parent = (View) getParent();
            if (parent != null) {
                float y = getY() + getMeasuredHeight() - parent.getHeight();
                bottomTabContainer.setTranslationY(-y);
            }
        }
    }

    private void startStopVisibleGifs(boolean start) {
        if (gifGridView == null) {
            return;
        }
        int count = gifGridView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = gifGridView.getChildAt(a);
            if (child instanceof ContextLinkCell) {
                ContextLinkCell cell = (ContextLinkCell) child;
                ImageReceiver imageReceiver = cell.getPhotoImage();
                if (start) {
                    imageReceiver.setAllowStartAnimation(true);
                    imageReceiver.startAnimation();
                } else {
                    imageReceiver.setAllowStartAnimation(false);
                    imageReceiver.stopAnimation();
                }
            }
        }
    }

    public void addEmojiToRecent(String code) {
        if (!Emoji.isValidEmoji(code)) {
            return;
        }
        int oldCount = Emoji.recentEmoji.size();
        Emoji.addRecentEmoji(code);
        if (getVisibility() != VISIBLE || pager.getCurrentItem() != 0) {
            Emoji.sortEmoji();
            emojiAdapter.notifyDataSetChanged();
        }
        Emoji.saveRecentEmoji();
        /*int addedCount = Emoji.recentEmoji.size() - oldCount;
        int position = emojiLayoutManager.findLastVisibleItemPosition();
        int top = Integer.MAX_VALUE;
        if (position != RecyclerView.NO_POSITION) {
            View view = emojiLayoutManager.findViewByPosition(position);
            if (view != null) {
                top = view.getTop();
            }
        }
        emojiAdapter.notifyDataSetChanged();
        if (top != Integer.MAX_VALUE) {
            emojiLayoutManager.scrollToPositionWithOffset(position + addedCount, top - emojiGridView.getPaddingTop());
        }*/
    }

    public void showSearchField(boolean show) {
        for (int a = 0; a < 3; a++) {
            final GridLayoutManager layoutManager = getLayoutManagerForType(a);
            int position = layoutManager.findFirstVisibleItemPosition();
            if (show) {
                if (position == 1 || position == 2) {
                    layoutManager.scrollToPosition(0);
                    resetTabsY(a);
                }
            } else {
                if (position == 0) {
                    layoutManager.scrollToPositionWithOffset(1, 0);
                }
            }
        }
    }

    public void hideSearchKeyboard() {
        if (stickersSearchField != null) {
            stickersSearchField.hideKeyboard();
        }
        if (gifSearchField != null) {
            gifSearchField.hideKeyboard();
        }
        if (emojiSearchField != null) {
            emojiSearchField.hideKeyboard();
        }
    }

    private void openSearch(SearchField searchField) {
        if (searchAnimation != null) {
            searchAnimation.cancel();
            searchAnimation = null;
        }

        firstStickersAttach = false;
        firstGifAttach = false;
        firstEmojiAttach = false;
        for (int a = 0; a < 3; a++) {
            RecyclerListView gridView;
            ScrollSlidingTabStrip tabStrip;
            SearchField currentField;
            GridLayoutManager layoutManager;
            if (a == 0) {
                currentField = emojiSearchField;
                gridView = emojiGridView;
                tabStrip = emojiTabs;
                layoutManager = emojiLayoutManager;
            } else if (a == 1) {
                currentField = gifSearchField;
                gridView = gifGridView;
                tabStrip = gifTabs;
                layoutManager = gifLayoutManager;
            } else {
                currentField = stickersSearchField;
                gridView = stickersGridView;
                tabStrip = stickersTab;
                layoutManager = stickersLayoutManager;
            }
            if (currentField == null) {
                continue;
            }

            if (searchField == currentField && delegate != null && delegate.isExpanded()) {
                searchAnimation = new AnimatorSet();
                if (tabStrip != null) {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, -AndroidUtilities.dp(48)),
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, -AndroidUtilities.dp(48)),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(0)));
                } else {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, -AndroidUtilities.dp(48)),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(0)));
                }
                searchAnimation.setDuration(200);
                searchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                searchAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            gridView.setTranslationY(0);
                            if (gridView == stickersGridView) {
                                gridView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                            } else if (gridView == emojiGridView || gridView == gifGridView) {
                                gridView.setPadding(0, 0, 0, 0);
                            }
                            searchAnimation = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            searchAnimation = null;
                        }
                    }
                });
                searchAnimation.start();
            } else {
                currentField.setTranslationY(AndroidUtilities.dp(0));
                if (tabStrip != null) {
                    tabStrip.setTranslationY(-AndroidUtilities.dp(48));
                }
                if (gridView == stickersGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                } else if (gridView == emojiGridView || gridView == gifGridView) {
                    gridView.setPadding(0, 0, 0, 0);
                }
                if (gridView == gifGridView) {
                    if (gifSearchAdapter.showTrendingWhenSearchEmpty = gifAdapter.results.size() > 0) {
                        gifSearchAdapter.search("");
                        if (gifGridView.getAdapter() != gifSearchAdapter) {
                            gifGridView.setAdapter(gifSearchAdapter);
                        }
                    }
                }
                layoutManager.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    private void showEmojiShadow(boolean show, boolean animated) {
        if (show && emojiTabsShadow.getTag() == null || !show && emojiTabsShadow.getTag() != null) {
            return;
        }
        if (emojiTabShadowAnimator != null) {
            emojiTabShadowAnimator.cancel();
            emojiTabShadowAnimator = null;
        }
        emojiTabsShadow.setTag(show ? null : 1);
        if (animated) {
            emojiTabShadowAnimator = new AnimatorSet();
            emojiTabShadowAnimator.playTogether(ObjectAnimator.ofFloat(emojiTabsShadow, View.ALPHA, show ? 1.0f : 0.0f));
            emojiTabShadowAnimator.setDuration(200);
            emojiTabShadowAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            emojiTabShadowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    emojiTabShadowAnimator = null;
                }
            });
            emojiTabShadowAnimator.start();
        } else {
            emojiTabsShadow.setAlpha(show ? 1.0f : 0.0f);
        }
    }

    public void closeSearch(boolean animated) {
        closeSearch(animated, -1);
    }

    public void closeSearch(boolean animated, long scrollToSet) {
        if (searchAnimation != null) {
            searchAnimation.cancel();
            searchAnimation = null;
        }

        int currentItem = pager.getCurrentItem();
        if (currentItem == 2 && scrollToSet != -1) {
            TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetById(scrollToSet);
            if (set != null) {
                int pos = stickersGridAdapter.getPositionForPack(set);
                if (pos >= 0) {
                    stickersLayoutManager.scrollToPositionWithOffset(pos, AndroidUtilities.dp(48 + 12));
                }
            }
        }

        if (gifSearchAdapter != null) {
            gifSearchAdapter.showTrendingWhenSearchEmpty = false;
        }

        for (int a = 0; a < 3; a++) {
            SearchField currentField;
            RecyclerListView gridView;
            GridLayoutManager layoutManager;
            ScrollSlidingTabStrip tabStrip;

            if (a == 0) {
                currentField = emojiSearchField;
                gridView = emojiGridView;
                layoutManager = emojiLayoutManager;
                tabStrip = emojiTabs;
            } else if (a == 1) {
                currentField = gifSearchField;
                gridView = gifGridView;
                layoutManager = gifLayoutManager;
                tabStrip = gifTabs;
            } else {
                currentField = stickersSearchField;
                gridView = stickersGridView;
                layoutManager = stickersLayoutManager;
                tabStrip = stickersTab;
            }

            if (currentField == null) {
                continue;
            }

            currentField.searchEditText.setText("");

            if (a == currentItem && animated) {
                searchAnimation = new AnimatorSet();
                if (tabStrip != null) {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, 0),
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, AndroidUtilities.dp(48) - searchFieldHeight),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(48) - searchFieldHeight));
                } else {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, -searchFieldHeight),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, -searchFieldHeight));
                }
                searchAnimation.setDuration(200);
                searchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                searchAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            int firstVisPos = layoutManager.findFirstVisibleItemPosition();
                            int top = 0;
                            if (firstVisPos != RecyclerView.NO_POSITION) {
                                View firstVisView = layoutManager.findViewByPosition(firstVisPos);
                                top = (int) (firstVisView.getTop() + gridView.getTranslationY());
                            }
                            gridView.setTranslationY(0);
                            if (gridView == stickersGridView) {
                                gridView.setPadding(0, AndroidUtilities.dp(48 + 4), 0, AndroidUtilities.dp(44));
                            } else if (gridView == gifGridView) {
                                gridView.setPadding(0, AndroidUtilities.dp(48 + 4), 0, AndroidUtilities.dp(44));
                            } else if (gridView == emojiGridView) {
                                gridView.setPadding(0, AndroidUtilities.dp(38), 0, AndroidUtilities.dp(44));
                            }
                            if (firstVisPos != RecyclerView.NO_POSITION) {
                                layoutManager.scrollToPositionWithOffset(firstVisPos, top - gridView.getPaddingTop());
                            }
                            searchAnimation = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            searchAnimation = null;
                        }
                    }
                });
                searchAnimation.start();
            } else {
                currentField.setTranslationY(AndroidUtilities.dp(48) - searchFieldHeight);
                if (tabStrip != null) {
                    tabStrip.setTranslationY(0);
                }
                if (gridView == stickersGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(48 + 4), 0, AndroidUtilities.dp(44));
                } else if (gridView == gifGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(48 + 4), 0, AndroidUtilities.dp(44));
                } else if (gridView == emojiGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(38), 0, AndroidUtilities.dp(44));
                }
                layoutManager.scrollToPositionWithOffset(1, 0);
            }
        }
        if (!animated) {
            delegate.onSearchOpenClose(0);
        }
        showBottomTab(true, animated);
    }

    private void checkStickersSearchFieldScroll(boolean isLayout) {
        if (delegate != null && delegate.isSearchOpened()) {
            RecyclerView.ViewHolder holder = stickersGridView.findViewHolderForAdapterPosition(0);
            if (holder == null) {
                stickersSearchField.showShadow(true, !isLayout);
            } else {
                stickersSearchField.showShadow(holder.itemView.getTop() < stickersGridView.getPaddingTop(), !isLayout);
            }
            return;
        }
        if (stickersSearchField == null || stickersGridView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = stickersGridView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            stickersSearchField.setTranslationY(holder.itemView.getTop());
        } else {
            stickersSearchField.setTranslationY(-searchFieldHeight);
        }
        stickersSearchField.showShadow(false, !isLayout);
    }

    private void checkBottomTabScroll(float dy) {
        lastBottomScrollDy += dy;
        int offset;
        if (pager.getCurrentItem() == 0) {
            offset = AndroidUtilities.dp(38);
        } else {
            offset = AndroidUtilities.dp(48);
        }
        if (lastBottomScrollDy >= offset) {
            showBottomTab(false, true);
        } else if (lastBottomScrollDy <= -offset) {
            showBottomTab(true, true);
        } else if (bottomTabContainer.getTag() == null && lastBottomScrollDy < 0 || bottomTabContainer.getTag() != null && lastBottomScrollDy > 0) {
            lastBottomScrollDy = 0;
        }
    }

    private void showBackspaceButton(boolean show, boolean animated) {
        if (show && backspaceButton.getTag() == null || !show && backspaceButton.getTag() != null) {
            return;
        }
        if (backspaceButtonAnimation != null) {
            backspaceButtonAnimation.cancel();
            backspaceButtonAnimation = null;
        }
        backspaceButton.setTag(show ? null : 1);
        if (animated) {
            if (show) {
                backspaceButton.setVisibility(VISIBLE);
            }
            backspaceButtonAnimation = new AnimatorSet();
            backspaceButtonAnimation.playTogether(ObjectAnimator.ofFloat(backspaceButton, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(backspaceButton, View.SCALE_X, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(backspaceButton, View.SCALE_Y, show ? 1.0f : 0.0f));
            backspaceButtonAnimation.setDuration(200);
            backspaceButtonAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            backspaceButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        backspaceButton.setVisibility(INVISIBLE);
                    }
                }
            });
            backspaceButtonAnimation.start();
        } else {
            backspaceButton.setAlpha(show ? 1.0f : 0.0f);
            backspaceButton.setScaleX(show ? 1.0f : 0.0f);
            backspaceButton.setScaleY(show ? 1.0f : 0.0f);
            backspaceButton.setVisibility(show ? VISIBLE : INVISIBLE);
        }
    }

    private void showStickerSettingsButton(boolean show, boolean animated) {
        if (stickerSettingsButton == null) {
            return;
        }
        if (show && stickerSettingsButton.getTag() == null || !show && stickerSettingsButton.getTag() != null) {
            return;
        }
        if (stickersButtonAnimation != null) {
            stickersButtonAnimation.cancel();
            stickersButtonAnimation = null;
        }
        stickerSettingsButton.setTag(show ? null : 1);
        if (animated) {
            if (show) {
                stickerSettingsButton.setVisibility(VISIBLE);
            }
            stickersButtonAnimation = new AnimatorSet();
            stickersButtonAnimation.playTogether(ObjectAnimator.ofFloat(stickerSettingsButton, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(stickerSettingsButton, View.SCALE_X, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(stickerSettingsButton, View.SCALE_Y, show ? 1.0f : 0.0f));
            stickersButtonAnimation.setDuration(200);
            stickersButtonAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            stickersButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        stickerSettingsButton.setVisibility(INVISIBLE);
                    }
                }
            });
            stickersButtonAnimation.start();
        } else {
            stickerSettingsButton.setAlpha(show ? 1.0f : 0.0f);
            stickerSettingsButton.setScaleX(show ? 1.0f : 0.0f);
            stickerSettingsButton.setScaleY(show ? 1.0f : 0.0f);
            stickerSettingsButton.setVisibility(show ? VISIBLE : INVISIBLE);
        }
    }

    private void showBottomTab(boolean show, boolean animated) {
        lastBottomScrollDy = 0;
        if (show && bottomTabContainer.getTag() == null || !show && bottomTabContainer.getTag() != null || delegate != null && delegate.isSearchOpened()) {
            return;
        }
        if (bottomTabContainerAnimation != null) {
            bottomTabContainerAnimation.cancel();
            bottomTabContainerAnimation = null;
        }
        bottomTabContainer.setTag(show ? null : 1);
        if (animated) {
            bottomTabContainerAnimation = new AnimatorSet();
            bottomTabContainerAnimation.playTogether(
                    ObjectAnimator.ofFloat(bottomTabContainer, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(needEmojiSearch ? 49 : 54)),
                    ObjectAnimator.ofFloat(shadowLine, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(49)));
            bottomTabContainerAnimation.setDuration(200);
            bottomTabContainerAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            bottomTabContainerAnimation.start();
        } else {
            bottomTabContainer.setTranslationY(show ? 0 : AndroidUtilities.dp(needEmojiSearch ? 49 : 54));
            shadowLine.setTranslationY(show ? 0 : AndroidUtilities.dp(49));
        }
    }

    private void checkTabsY(@Type int type, int dy) {
        if (type == Type.EMOJIS) {
            checkEmojiTabY(emojiGridView, dy);
            return;
        }
        if (delegate != null && delegate.isSearchOpened()) {
            return;
        }
        final RecyclerListView listView = getListViewForType(type);
        if (dy > 0 && listView != null && listView.getVisibility() == VISIBLE) {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
            if (holder != null && holder.itemView.getTop() + searchFieldHeight >= listView.getPaddingTop()) {
                return;
            }
        }
        tabsMinusDy[type] -= dy;
        if (tabsMinusDy[type] > 0) {
            tabsMinusDy[type] = 0;
        } else if (tabsMinusDy[type] < -AndroidUtilities.dp(48 * 6)) {
            tabsMinusDy[type] = -AndroidUtilities.dp(48 * 6);
        }
        getTabsForType(type).setTranslationY(Math.max(-AndroidUtilities.dp(48), tabsMinusDy[type]));
    }

    private void resetTabsY(@Type int type) {
        if (delegate != null && delegate.isSearchOpened()) {
            return;
        }
        getTabsForType(type).setTranslationY(tabsMinusDy[type] = 0);
    }

    private void animateTabsY(@Type int type) {
        if (delegate != null && delegate.isSearchOpened()) {
            return;
        }
        final float tabsHeight = AndroidUtilities.dpf2(type == Type.EMOJIS ? 38 : 48);
        final float fraction = tabsMinusDy[type] / -tabsHeight;
        if (fraction <= 0f || fraction >= 1f) {
            animateSearchField(type);
            return;
        }
        final ScrollSlidingTabStrip tabStrip = getTabsForType(type);
        final int endValue = fraction > 0.5f ? (int) -Math.ceil(tabsHeight) : 0;
        if (fraction > 0.5f) {
            animateSearchField(type, false, endValue);
        }
        if (type == Type.EMOJIS) {
            checkEmojiShadow(endValue);
        }
        if (tabsYAnimators[type] == null) {
            tabsYAnimators[type] = ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, tabStrip.getTranslationY(), endValue);
            tabsYAnimators[type].addUpdateListener(a -> tabsMinusDy[type] = (int) (float) a.getAnimatedValue());
            tabsYAnimators[type].setDuration(200);
        } else {
            tabsYAnimators[type].setFloatValues(tabStrip.getTranslationY(), endValue);
        }
        tabsYAnimators[type].start();
    }

    private void stopAnimatingTabsY(@Type int type) {
        if (tabsYAnimators[type] != null && tabsYAnimators[type].isRunning()) {
            tabsYAnimators[type].cancel();
        }
    }

    private void animateSearchField(@Type int type) {
        final RecyclerListView listView = getListViewForType(type);
        final int tabsHeight = AndroidUtilities.dp(type == Type.EMOJIS ? 38 : 48);
        final RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            final float fraction = (holder.itemView.getBottom() - (tabsHeight + tabsMinusDy[type])) / (float) searchFieldHeight;
            if (fraction > 0f || fraction < 1f) {
                animateSearchField(type, fraction > 0.5f, tabsMinusDy[type]);
            }
        }
    }

    private void animateSearchField(@Type int type, boolean visible, int tabsMinusDy) {
        if (getListViewForType(type).findViewHolderForAdapterPosition(0) == null) {
            return;
        }
        final LinearSmoothScroller smoothScroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            @Override
            protected int calculateTimeForDeceleration(int dx) {
                return super.calculateTimeForDeceleration(dx) * 16;
            }

            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference) + tabsMinusDy;
            }
        };
        smoothScroller.setTargetPosition(visible ? 0 : 1);
        getLayoutManagerForType(type).startSmoothScroll(smoothScroller);
    }

    private ScrollSlidingTabStrip getTabsForType(@Type int type) {
        switch (type) {
            case Type.STICKERS: return stickersTab;
            case Type.EMOJIS: return emojiTabs;
            case Type.GIFS: return gifTabs;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private RecyclerListView getListViewForType(@Type int type) {
        switch (type) {
            case Type.STICKERS: return stickersGridView;
            case Type.EMOJIS: return emojiGridView;
            case Type.GIFS: return gifGridView;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private GridLayoutManager getLayoutManagerForType(@Type int type) {
        switch (type) {
            case Type.STICKERS: return stickersLayoutManager;
            case Type.EMOJIS: return emojiLayoutManager;
            case Type.GIFS: return gifLayoutManager;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private SearchField getSearchFieldForType(@Type int type) {
        switch (type) {
            case Type.STICKERS: return stickersSearchField;
            case Type.EMOJIS: return emojiSearchField;
            case Type.GIFS: return gifSearchField;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private void checkEmojiSearchFieldScroll(boolean isLayout) {
        if (delegate != null && delegate.isSearchOpened()) {
            RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
            if (holder == null) {
                emojiSearchField.showShadow(true, !isLayout);
            } else {
                emojiSearchField.showShadow(holder.itemView.getTop() < emojiGridView.getPaddingTop(), !isLayout);
            }
            showEmojiShadow(false, !isLayout);
            return;
        }
        if (emojiSearchField == null || emojiGridView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            emojiSearchField.setTranslationY(holder.itemView.getTop());
        } else {
            emojiSearchField.setTranslationY(-searchFieldHeight);
        }
        emojiSearchField.showShadow(false, !isLayout);
        checkEmojiShadow(Math.round(emojiTabs.getTranslationY()));
    }

    private void checkEmojiShadow(int tabsTranslationY) {
        if (tabsYAnimators[Type.EMOJIS] != null && tabsYAnimators[Type.EMOJIS].isRunning()) {
            return;
        }
        final RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
        final int translatedBottom = AndroidUtilities.dp(38) + tabsTranslationY;
        showEmojiShadow(translatedBottom > 0 && (holder == null || holder.itemView.getBottom() < translatedBottom), !isLayout);
    }

    private void checkEmojiTabY(View list, int dy) {
        if (list == null) {
            emojiTabs.setTranslationY(tabsMinusDy[Type.EMOJIS] = 0);
            return;
        }
        if (list.getVisibility() != VISIBLE) {
            return;
        }
        if (delegate != null && delegate.isSearchOpened()) {
            return;
        }
        if (dy > 0 && emojiGridView != null && emojiGridView.getVisibility() == VISIBLE) {
            RecyclerView.ViewHolder holder = emojiGridView.findViewHolderForAdapterPosition(0);
            if (holder != null && holder.itemView.getTop() + (needEmojiSearch ? searchFieldHeight : 0) >= emojiGridView.getPaddingTop()) {
                return;
            }
        }
        tabsMinusDy[Type.EMOJIS] -= dy;
        if (tabsMinusDy[Type.EMOJIS] > 0) {
            tabsMinusDy[Type.EMOJIS] = 0;
        } else if (tabsMinusDy[Type.EMOJIS] < -AndroidUtilities.dp(48 * 6)) {
            tabsMinusDy[Type.EMOJIS] = -AndroidUtilities.dp(48 * 6);
        }
        emojiTabs.setTranslationY(Math.max(-AndroidUtilities.dp(38), tabsMinusDy[Type.EMOJIS]));
    }

    private void checkGifSearchFieldScroll(boolean isLayout) {
        if (gifGridView != null && gifGridView.getAdapter() instanceof GifAdapter) {
            final GifAdapter adapter = (GifAdapter) gifGridView.getAdapter();
            if (!adapter.searchEndReached && adapter.reqId == 0 && !adapter.results.isEmpty()) {
                int position = gifLayoutManager.findLastVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION && position > gifLayoutManager.getItemCount() - 5) {
                    adapter.search(adapter.lastSearchImageString, adapter.nextSearchOffset, true, adapter.lastSearchIsEmoji, adapter.lastSearchIsEmoji);
                }
            }
        }
        if (delegate != null && delegate.isSearchOpened()) {
            RecyclerView.ViewHolder holder = gifGridView.findViewHolderForAdapterPosition(0);
            if (holder == null) {
                gifSearchField.showShadow(true, !isLayout);
            } else {
                gifSearchField.showShadow(holder.itemView.getTop() < gifGridView.getPaddingTop(), !isLayout);
            }
            return;
        }
        if (gifSearchField == null || gifGridView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = gifGridView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            gifSearchField.setTranslationY(holder.itemView.getTop());
        } else {
            gifSearchField.setTranslationY(-searchFieldHeight);
        }
        gifSearchField.showShadow(false, !isLayout);
    }

    private void scrollGifsToTop() {
        gifLayoutManager.scrollToPositionWithOffset(delegate != null && delegate.isExpanded() ? 0 : 1, 0);
        resetTabsY(Type.GIFS);
    }

    private void checkScroll(@Type int type) {
        if (type == Type.STICKERS) {
            int firstVisibleItem = stickersLayoutManager.findFirstVisibleItemPosition();
            if (firstVisibleItem == RecyclerView.NO_POSITION) {
                return;
            }
            if (stickersGridView == null) {
                return;
            }
            int firstTab;
            if (favTabBum > 0) {
                firstTab = favTabBum;
            } else if (recentTabBum > 0) {
                firstTab = recentTabBum;
            } else {
                firstTab = stickersTabOffset;
            }
            stickersTab.onPageScrolled(stickersGridAdapter.getTabForPosition(firstVisibleItem), firstTab);
        } else if (type == Type.GIFS) {
            if (gifGridView.getAdapter() == gifAdapter && gifAdapter.trendingSectionItem >= 0 && gifTrendingTabNum >= 0 && gifRecentTabNum >= 0) {
                int firstVisibleItem = gifLayoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem == RecyclerView.NO_POSITION) {
                    return;
                }
                gifTabs.onPageScrolled(firstVisibleItem >= gifAdapter.trendingSectionItem ? gifTrendingTabNum : gifRecentTabNum, 0);
            }
        }
    }

    private void saveNewPage() {
        if (pager == null) {
            return;
        }
        int newPage;
        int currentItem = pager.getCurrentItem();
        if (currentItem == 2) {
            newPage = 1;
        } else if (currentItem == 1) {
            newPage = 2;
        } else {
            newPage = 0;
        }
        if (currentPage != newPage) {
            currentPage = newPage;
            MessagesController.getGlobalEmojiSettings().edit().putInt("selected_page", newPage).commit();
        }
    }

    public void clearRecentEmoji() {
        Emoji.clearRecentEmoji();
        emojiAdapter.notifyDataSetChanged();
    }

    private void onPageScrolled(int position, int width, int positionOffsetPixels) {
        if (delegate == null) {
            return;
        }
        if (position == 1) {
            delegate.onTabOpened(positionOffsetPixels != 0 ? 2 : 0);
        } else if (position == 2) {
            delegate.onTabOpened(3);
        } else {
            delegate.onTabOpened(0);
        }
    }

    private void postBackspaceRunnable(final int time) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!backspacePressed) {
                return;
            }
            if (delegate != null && delegate.onBackspace()) {
                backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            backspaceOnce = true;
            postBackspaceRunnable(Math.max(50, time - 100));
        }, time);
    }

    public void switchToGifRecent() {
        showBackspaceButton(false, false);
        showStickerSettingsButton(false, false);
        pager.setCurrentItem(1, false);
    }

    private void updateEmojiTabs() {
        int newHas = Emoji.recentEmoji.isEmpty() ? 0 : 1;
        if (hasRecentEmoji != -1 && hasRecentEmoji == newHas) {
            return;
        }
        hasRecentEmoji = newHas;
        emojiTabs.removeTabs();
        String[] descriptions = {
                LocaleController.getString("RecentStickers", R.string.RecentStickers),
                LocaleController.getString("Emoji1", R.string.Emoji1),
                LocaleController.getString("Emoji2", R.string.Emoji2),
                LocaleController.getString("Emoji3", R.string.Emoji3),
                LocaleController.getString("Emoji4", R.string.Emoji4),
                LocaleController.getString("Emoji5", R.string.Emoji5),
                LocaleController.getString("Emoji6", R.string.Emoji6),
                LocaleController.getString("Emoji7", R.string.Emoji7),
                LocaleController.getString("Emoji8", R.string.Emoji8),
        };
        for (int a = 0; a < emojiIcons.length; a++) {
            if (a == 0 && Emoji.recentEmoji.isEmpty()) {
                continue;
            }
            emojiTabs.addIconTab(a, emojiIcons[a]).setContentDescription(descriptions[a]);
        }
        emojiTabs.updateTabStyles();
    }

    private void updateStickerTabs() {
        if (stickersTab == null) {
            return;
        }
        recentTabBum = -2;
        favTabBum = -2;
        trendingTabNum = -2;

        stickersTabOffset = 0;
        int lastPosition = stickersTab.getCurrentPosition();
        stickersTab.beginUpdate(getParent() != null && getVisibility() == VISIBLE && (installingStickerSets.size() != 0 || removingStickerSets.size() != 0));

        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

        if (!mediaDataController.getFeaturedStickerSets().isEmpty()) {
            final int id = mediaDataController.getUnreadStickerSets().isEmpty() ? 2 : 3;
            final ImageView trendingStickersTabView = stickersTab.addIconTab(id, stickerIcons[id]);
            trendingStickersTabView.setContentDescription(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers));
            trendingTabNum = stickersTabOffset;
            stickersTabOffset++;
        }

        if (!favouriteStickers.isEmpty()) {
            favTabBum = stickersTabOffset;
            stickersTabOffset++;
            stickersTab.addIconTab(1, stickerIcons[1]).setContentDescription(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers));
        }

        if (!recentStickers.isEmpty()) {
            recentTabBum = stickersTabOffset;
            stickersTabOffset++;
            stickersTab.addIconTab(0, stickerIcons[0]).setContentDescription(LocaleController.getString("RecentStickers", R.string.RecentStickers));
        }

        stickerSets.clear();
        groupStickerSet = null;
        groupStickerPackPosition = -1;
        groupStickerPackNum = -10;
        ArrayList<TLRPC.TL_messages_stickerSet> packs = mediaDataController.getStickerSets(MediaDataController.TYPE_IMAGE);
        for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
            final TLRPC.StickerSetCovered installingStickerSet = primaryInstallingStickerSets[i];
            if (installingStickerSet != null) {
                final TLRPC.TL_messages_stickerSet pack = mediaDataController.getStickerSetById(installingStickerSet.set.id);
                if (pack != null && !pack.set.archived) {
                    primaryInstallingStickerSets[i] = null;
                } else {
                    final TLRPC.TL_messages_stickerSet set = new TLRPC.TL_messages_stickerSet();
                    set.set = installingStickerSet.set;
                    if (installingStickerSet.cover != null) {
                        set.documents.add(installingStickerSet.cover);
                    } else if (!installingStickerSet.covers.isEmpty()) {
                        set.documents.addAll(installingStickerSet.covers);
                    }
                    if (!set.documents.isEmpty()) {
                        stickerSets.add(set);
                    }
                }
            }
        }
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if (pack.set.archived || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets.add(pack);
        }
        if (info != null) {
            long hiddenStickerSetId = MessagesController.getEmojiSettings(currentAccount).getLong("group_hide_stickers_" + info.id, -1);
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(info.id);
            if (chat == null || info.stickerset == null || !ChatObject.hasAdminRights(chat)) {
                groupStickersHidden = hiddenStickerSetId != -1;
            } else if (info.stickerset != null) {
                groupStickersHidden = hiddenStickerSetId == info.stickerset.id;
            }
            if (info.stickerset != null) {
                TLRPC.TL_messages_stickerSet pack = mediaDataController.getGroupStickerSetById(info.stickerset);
                if (pack != null && pack.documents != null && !pack.documents.isEmpty() && pack.set != null) {
                    TLRPC.TL_messages_stickerSet set = new TLRPC.TL_messages_stickerSet();
                    set.documents = pack.documents;
                    set.packs = pack.packs;
                    set.set = pack.set;
                    if (groupStickersHidden) {
                        groupStickerPackNum = stickerSets.size();
                        stickerSets.add(set);
                    } else {
                        groupStickerPackNum = 0;
                        stickerSets.add(0, set);
                    }
                    groupStickerSet = info.can_set_stickers ? set : null;
                }
            } else if (info.can_set_stickers) {
                TLRPC.TL_messages_stickerSet pack = new TLRPC.TL_messages_stickerSet();
                if (groupStickersHidden) {
                    groupStickerPackNum = stickerSets.size();
                    stickerSets.add(pack);
                } else {
                    groupStickerPackNum = 0;
                    stickerSets.add(0, pack);
                }
            }
        }
        for (int a = 0; a < stickerSets.size(); a++) {
            if (a == groupStickerPackNum) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(info.id);
                if (chat == null) {
                    stickerSets.remove(0);
                    a--;
                } else {
                    stickersTab.addStickerTab(chat);
                }
            } else {
                TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(a);
                TLObject thumb;
                TLRPC.Document document = stickerSet.documents.get(0);
                if (stickerSet.set.thumb instanceof TLRPC.TL_photoSize) {
                    thumb = stickerSet.set.thumb;
                } else {
                    thumb = document;
                }
                stickersTab.addStickerTab(thumb, document, stickerSet).setContentDescription(stickerSet.set.title + ", " + LocaleController.getString("AccDescrStickerSet", R.string.AccDescrStickerSet));
            }
        }
        stickersTab.commitUpdate();
        stickersTab.updateTabStyles();
        if (lastPosition != 0) {
            stickersTab.onPageScrolled(lastPosition, lastPosition);
        }
        checkPanels();
    }

    private void checkPanels() {
        if (stickersTab == null) {
            return;
        }
        int position = stickersLayoutManager.findFirstVisibleItemPosition();
        if (position != RecyclerView.NO_POSITION) {
            int firstTab;
            if (favTabBum > 0) {
                firstTab = favTabBum;
            } else if (recentTabBum > 0) {
                firstTab = recentTabBum;
            } else {
                firstTab = stickersTabOffset;
            }
            stickersTab.onPageScrolled(stickersGridAdapter.getTabForPosition(position), firstTab);
        }
    }

    private void updateGifTabs() {
        final int lastPosition = gifTabs.getCurrentPosition();

        final boolean wasRecentTabSelected = lastPosition == gifRecentTabNum;
        final boolean hadRecent = gifRecentTabNum >= 0;
        final boolean hasRecent = !recentGifs.isEmpty();

        gifTabs.beginUpdate(false);

        int gifTabsCount = 0;
        gifRecentTabNum = -2;
        gifTrendingTabNum = -2;
        gifFirstEmojiTabNum = -2;

        if (hasRecent) {
            gifRecentTabNum = gifTabsCount++;
            gifTabs.addIconTab(0, gifIcons[0]).setContentDescription(LocaleController.getString("RecentStickers", R.string.RecentStickers));
        }

        gifTrendingTabNum = gifTabsCount++;
        gifTabs.addIconTab(1, gifIcons[1]).setContentDescription(LocaleController.getString("FeaturedGifs", R.string.FeaturedGifs));

        gifFirstEmojiTabNum = gifTabsCount;
        final int hPadding = AndroidUtilities.dp(13);
        final int vPadding = AndroidUtilities.dp(11);
        final List<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
        for (int i = 0, N = gifSearchEmojies.size(); i < N; i++) {
            final String emoji = gifSearchEmojies.get(i);
            final Emoji.EmojiDrawable emojiDrawable = Emoji.getEmojiDrawable(emoji);
            if (emojiDrawable != null) {
                gifTabsCount++;
                final ImageView iconTab = gifTabs.addIconTab(3 + i, emojiDrawable);
                iconTab.setPadding(hPadding, vPadding, hPadding, vPadding);
                iconTab.setContentDescription(emoji);
            }
        }

        gifTabs.commitUpdate();
        gifTabs.updateTabStyles();

        if (wasRecentTabSelected && !hasRecent) {
            gifTabs.selectTab(gifTrendingTabNum);
        } else if (ViewCompat.isLaidOut(gifTabs)) {
            if (hasRecent && !hadRecent) {
                gifTabs.onPageScrolled(lastPosition + 1, 0);
            } else if (!hasRecent && hadRecent) {
                gifTabs.onPageScrolled(lastPosition - 1, 0);
            }
        }
    }

    public void addRecentSticker(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, null, document, (int) (System.currentTimeMillis() / 1000), false);
        boolean wasEmpty = recentStickers.isEmpty();
        recentStickers = MediaDataController.getInstance(currentAccount).getRecentStickers(MediaDataController.TYPE_IMAGE);
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
        boolean wasEmpty = recentGifs.isEmpty();
        updateRecentGifs();
        if (wasEmpty) {
            updateStickerTabs();
        }
    }

    @Override
    public void requestLayout() {
        if (isLayout) {
            return;
        }
        super.requestLayout();
    }

    public void updateColors() {
        if (AndroidUtilities.isInMultiwindow || forseMultiwindowLayout) {
            Drawable background = getBackground();
            if (background != null) {
                background.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            if (needEmojiSearch) {
                bottomTabContainerBackground.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            }
        }

        if (emojiTabs != null) {
            emojiTabs.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            emojiTabsShadow.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
        }
        if (pickerView != null) {
            Theme.setDrawableColor(pickerView.backgroundDrawable, Theme.getColor(Theme.key_dialogBackground));
            Theme.setDrawableColor(pickerView.arrowDrawable, Theme.getColor(Theme.key_dialogBackground));
        }
        for (int a = 0; a < 3; a++) {
            SearchField searchField;
            if (a == 0) {
                searchField = stickersSearchField;
            } else if (a == 1) {
                searchField = emojiSearchField;
            } else {
                searchField = gifSearchField;
            }
            if (searchField == null) {
                continue;
            }
            searchField.backgroundView.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
            searchField.shadowView.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
            searchField.clearSearchImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiSearchIcon), PorterDuff.Mode.MULTIPLY));
            searchField.searchIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiSearchIcon), PorterDuff.Mode.MULTIPLY));
            Theme.setDrawableColorByKey(searchField.searchBackground.getBackground(), Theme.key_chat_emojiSearchBackground);
            searchField.searchBackground.invalidate();
            searchField.searchEditText.setHintTextColor(Theme.getColor(Theme.key_chat_emojiSearchIcon));
            searchField.searchEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        if (dotPaint != null) {
            dotPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelNewTrending));
        }
        if (emojiGridView != null) {
            emojiGridView.setGlowColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        }
        if (stickersGridView != null) {
            stickersGridView.setGlowColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        }
        if (stickersTab != null) {
            stickersTab.setIndicatorColor(Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            stickersTab.setUnderlineColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
            stickersTab.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        }
        if (gifTabs != null) {
            gifTabs.setIndicatorColor(Theme.getColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            gifTabs.setUnderlineColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
            gifTabs.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
        }
        if (backspaceButton != null) {
            backspaceButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            if (emojiSearchField == null) {
                Theme.setSelectorDrawableColor(backspaceButton.getBackground(), Theme.getColor(Theme.key_chat_emojiPanelBackground), false);
                Theme.setSelectorDrawableColor(backspaceButton.getBackground(), Theme.getColor(Theme.key_chat_emojiPanelBackground), true);
            }
        }
        if (stickerSettingsButton != null) {
            stickerSettingsButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        }
        if (searchButton != null) {
            searchButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        }
        if (shadowLine != null) {
            shadowLine.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelShadowLine));
        }
        if (mediaBanTooltip != null) {
            ((ShapeDrawable) mediaBanTooltip.getBackground()).getPaint().setColor(Theme.getColor(Theme.key_chat_gifSaveHintBackground));
            mediaBanTooltip.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
        }
        if (gifSearchAdapter != null) {
            gifSearchAdapter.progressEmptyView.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
            gifSearchAdapter.progressEmptyView.textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText));
            gifSearchAdapter.progressEmptyView.progressView.setProgressColor(Theme.getColor(Theme.key_progressCircle));
        }

        for (int a = 0; a < tabIcons.length; a++) {
            Theme.setEmojiDrawableColor(tabIcons[a], Theme.getColor(Theme.key_chat_emojiBottomPanelIcon), false);
            Theme.setEmojiDrawableColor(tabIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        for (int a = 0; a < emojiIcons.length; a++) {
            Theme.setEmojiDrawableColor(emojiIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIcon), false);
            Theme.setEmojiDrawableColor(emojiIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        for (int a = 0; a < stickerIcons.length; a++) {
            Theme.setEmojiDrawableColor(stickerIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIcon), false);
            Theme.setEmojiDrawableColor(stickerIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        for (int a = 0; a < gifIcons.length; a++) {
            Theme.setEmojiDrawableColor(gifIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIcon), false);
            Theme.setEmojiDrawableColor(gifIcons[a], Theme.getColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        isLayout = true;
        if (AndroidUtilities.isInMultiwindow || forseMultiwindowLayout) {
            if (currentBackgroundType != 1) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider((ViewOutlineProvider) outlineProvider);
                    setClipToOutline(true);
                    setElevation(AndroidUtilities.dp(2));
                }
                setBackgroundResource(R.drawable.smiles_popup);
                getBackground().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
                if (needEmojiSearch) {
                    bottomTabContainerBackground.setBackgroundDrawable(null);
                }
                currentBackgroundType = 1;
            }
        } else {
            if (currentBackgroundType != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider(null);
                    setClipToOutline(false);
                    setElevation(0);
                }
                setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
                if (needEmojiSearch) {
                    bottomTabContainerBackground.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
                }
                currentBackgroundType = 0;
            }
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        isLayout = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            reloadStickersAdapter();
        }
        View parent = (View) getParent();
        if (parent != null) {
            int newHeight = bottom - top;
            int newHeight2 = parent.getHeight();
            if (lastNotifyHeight != newHeight || lastNotifyHeight2 != newHeight2) {
                if (delegate != null && delegate.isSearchOpened()) {
                    bottomTabContainer.setTranslationY(AndroidUtilities.dp(49));
                } else {
                    if (bottomTabContainer.getTag() == null) {
                        if (newHeight < lastNotifyHeight) {
                            bottomTabContainer.setTranslationY(0);
                        } else {
                            float y = getY() + getMeasuredHeight() - parent.getHeight();
                            bottomTabContainer.setTranslationY(-y);
                        }
                    }
                }
                lastNotifyHeight = newHeight;
                lastNotifyHeight2 = newHeight2;
            }
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private void reloadStickersAdapter() {
        if (stickersGridAdapter != null) {
            stickersGridAdapter.notifyDataSetChanged();
        }
        if (stickersSearchGridAdapter != null) {
            stickersSearchGridAdapter.notifyDataSetChanged();
        }
        if (ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().close();
        }
        ContentPreviewViewer.getInstance().reset();
    }

    public void setDelegate(EmojiViewDelegate emojiViewDelegate) {
        delegate = emojiViewDelegate;
    }

    public void setDragListener(DragListener listener) {
        dragListener = listener;
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        updateStickerTabs();
    }

    public void invalidateViews() {
        emojiGridView.invalidateViews();
    }

    public void setForseMultiwindowLayout(boolean value) {
        forseMultiwindowLayout = value;
    }

    public void onOpen(boolean forceEmoji) {
        if (currentPage != 0 && currentChatId != 0) {
            currentPage = 0;
        }
        if (currentPage == 0 || forceEmoji || views.size() == 1) {
            showBackspaceButton(true, false);
            showStickerSettingsButton(false, false);
            if (pager.getCurrentItem() != 0) {
                pager.setCurrentItem(0, !forceEmoji);
            }
        } else if (currentPage == 1) {
            showBackspaceButton(false, false);
            showStickerSettingsButton(true, false);
            if (pager.getCurrentItem() != 2) {
                pager.setCurrentItem(2, false);
            }
            if (stickersTab != null) {
                if (favTabBum >= 0) {
                    stickersTab.selectTab(favTabBum);
                } else if (recentTabBum >= 0) {
                    stickersTab.selectTab(recentTabBum);
                } else {
                    stickersTab.selectTab(stickersTabOffset);
                }
            }
        } else if (currentPage == 2) {
            showBackspaceButton(false, false);
            showStickerSettingsButton(false, false);
            if (pager.getCurrentItem() != 1) {
                pager.setCurrentItem(1, false);
            }
            if (gifTabs != null) {
                gifTabs.selectTab(0);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
            AndroidUtilities.runOnUIThread(() -> {
                updateStickerTabs();
                reloadStickersAdapter();
            });
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != GONE) {
            Emoji.sortEmoji();
            emojiAdapter.notifyDataSetChanged();
            if (stickersGridAdapter != null) {
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
                updateStickerTabs();
                reloadStickersAdapter();
                /*if (gifGridView != null && delegate != null) {
                    delegate.onTabOpened(pager != null && pager.getCurrentItem() == 1 ? 1  : );
                }*/
            }
            checkDocuments(true);
            checkDocuments(false);
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, true, true, false);
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
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
            updateRecentGifs();
        } else {
            int previousCount = recentStickers.size();
            int previousCount2 = favouriteStickers.size();
            recentStickers = MediaDataController.getInstance(currentAccount).getRecentStickers(MediaDataController.TYPE_IMAGE);
            favouriteStickers = MediaDataController.getInstance(currentAccount).getRecentStickers(MediaDataController.TYPE_FAVE);
            for (int a = 0; a < favouriteStickers.size(); a++) {
                TLRPC.Document favSticker = favouriteStickers.get(a);
                for (int b = 0; b < recentStickers.size(); b++) {
                    TLRPC.Document recSticker = recentStickers.get(b);
                    if (recSticker.dc_id == favSticker.dc_id && recSticker.id == favSticker.id) {
                        recentStickers.remove(b);
                        break;
                    }
                }
            }
            if (previousCount != recentStickers.size() || previousCount2 != favouriteStickers.size()) {
                updateStickerTabs();
            }
            if (stickersGridAdapter != null) {
                stickersGridAdapter.notifyDataSetChanged();
            }
            checkPanels();
        }
    }

    private void updateRecentGifs() {
        final int prevSize = recentGifs.size();
        int prevHash = MediaDataController.calcDocumentsHash(recentGifs, Integer.MAX_VALUE);
        recentGifs = MediaDataController.getInstance(currentAccount).getRecentGifs();
        int newHash = MediaDataController.calcDocumentsHash(recentGifs, Integer.MAX_VALUE);
        if (gifTabs != null && prevSize == 0 && !recentGifs.isEmpty() || prevSize != 0 && recentGifs.isEmpty()) {
            updateGifTabs();
        }
        if ((prevSize != recentGifs.size() || prevHash != newHash) && gifAdapter != null) {
            gifAdapter.notifyDataSetChanged();
        }
    }

    public void setStickersBanned(boolean value, int chatId) {
        if (typeTabs == null) {
            return;
        }
        if (value) {
            currentChatId = chatId;
        } else {
            currentChatId = 0;
        }
        View view = typeTabs.getTab(2);
        if (view != null) {
            view.setAlpha(currentChatId != 0 ? 0.5f : 1.0f);
            if (currentChatId != 0 && pager.getCurrentItem() != 0) {
                showBackspaceButton(true, true);
                showStickerSettingsButton(false, true);
                pager.setCurrentItem(0, false);
            }
        }
    }

    public void showStickerBanHint(boolean gif) {
        if (mediaBanTooltip.getVisibility() == VISIBLE) {
            return;
        }
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentChatId);
        if (chat == null) {
            return;
        }

        String text;
        if (!ChatObject.hasAdminRights(chat) && chat.default_banned_rights != null && chat.default_banned_rights.send_stickers) {
            if (gif) {
                mediaBanTooltip.setText(LocaleController.getString("GlobalAttachGifRestricted", R.string.GlobalAttachGifRestricted));
            } else {
                mediaBanTooltip.setText(LocaleController.getString("GlobalAttachStickersRestricted", R.string.GlobalAttachStickersRestricted));
            }
        } else {
            if (chat.banned_rights == null) {
                return;
            }
            if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                if (gif) {
                    mediaBanTooltip.setText(LocaleController.getString("AttachGifRestrictedForever", R.string.AttachGifRestrictedForever));
                } else {
                    mediaBanTooltip.setText(LocaleController.getString("AttachStickersRestrictedForever", R.string.AttachStickersRestrictedForever));
                }
            } else {
                if (gif) {
                    mediaBanTooltip.setText(LocaleController.formatString("AttachGifRestricted", R.string.AttachGifRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                } else {
                    mediaBanTooltip.setText(LocaleController.formatString("AttachStickersRestricted", R.string.AttachStickersRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                }
            }
        }
        mediaBanTooltip.setVisibility(View.VISIBLE);
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mediaBanTooltip, View.ALPHA, 0.0f, 1.0f)
        );
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (mediaBanTooltip == null) {
                        return;
                    }
                    AnimatorSet AnimatorSet1 = new AnimatorSet();
                    AnimatorSet1.playTogether(
                            ObjectAnimator.ofFloat(mediaBanTooltip, View.ALPHA, 0.0f)
                    );
                    AnimatorSet1.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation1) {
                            if (mediaBanTooltip != null) {
                                mediaBanTooltip.setVisibility(View.INVISIBLE);
                            }
                        }
                    });
                    AnimatorSet1.setDuration(300);
                    AnimatorSet1.start();
                }, 5000);
            }
        });
        AnimatorSet.setDuration(300);
        AnimatorSet.start();
    }

    private void updateVisibleTrendingSets() {
        if (stickersGridView == null) {
            return;
        }
        try {
            int count = stickersGridView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = stickersGridView.getChildAt(a);
                if (child instanceof FeaturedStickerSetInfoCell) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) stickersGridView.getChildViewHolder(child);
                    if (holder == null) {
                        continue;
                    }
                    FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) child;
                    ArrayList<Long> unreadStickers = MediaDataController.getInstance(currentAccount).getUnreadStickerSets();
                    TLRPC.StickerSetCovered stickerSetCovered = cell.getStickerSet();
                    boolean unread = unreadStickers != null && unreadStickers.contains(stickerSetCovered.set.id);
                    boolean forceInstalled = false;
                    for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
                        if (primaryInstallingStickerSets[i] != null && primaryInstallingStickerSets[i].set.id == stickerSetCovered.set.id) {
                            forceInstalled = true;
                            break;
                        }
                    }
                    cell.setStickerSet(stickerSetCovered, unread, true, 0, 0, forceInstalled);
                    if (unread) {
                        MediaDataController.getInstance(currentAccount).markFaturedStickersByIdAsRead(stickerSetCovered.set.id);
                    }
                    boolean installing = installingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
                    boolean removing = removingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
                    if (installing || removing) {
                        if (installing && cell.isInstalled()) {
                            installingStickerSets.remove(stickerSetCovered.set.id);
                            installing = false;
                        } else if (removing && !cell.isInstalled()) {
                            removingStickerSets.remove(stickerSetCovered.set.id);
                            removing = false;
                        }
                    }
                    cell.setAddDrawProgress(!forceInstalled && installing, true);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean areThereAnyStickers() {
        return stickersGridAdapter != null && stickersGridAdapter.getItemCount() > 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == MediaDataController.TYPE_IMAGE) {
                updateStickerTabs();
                updateVisibleTrendingSets();
                reloadStickersAdapter();
                checkPanels();
            }
        } else if (id == NotificationCenter.recentDocumentsDidLoad) {
            boolean isGif = (Boolean) args[0];
            int type = (Integer) args[1];
            if (isGif || type == MediaDataController.TYPE_IMAGE || type == MediaDataController.TYPE_FAVE) {
                checkDocuments(isGif);
            }
        } else if (id == NotificationCenter.featuredStickersDidLoad) {
            updateVisibleTrendingSets();
            if (typeTabs != null) {
                int count = typeTabs.getChildCount();
                for (int a = 0; a < count; a++) {
                    typeTabs.getChildAt(a).invalidate();
                }
            }
            updateStickerTabs();
        } else if (id == NotificationCenter.groupStickersDidLoad) {
            if (info != null && info.stickerset != null && info.stickerset.id == (Long) args[0]) {
                updateStickerTabs();
            }
        } else if (id == NotificationCenter.emojiDidLoad) {
            if (stickersGridView != null) {
                int count = stickersGridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = stickersGridView.getChildAt(a);
                    if (child instanceof StickerSetNameCell || child instanceof StickerEmojiCell) {
                        child.invalidate();
                    }
                }
            }
            if (pickerView != null) {
                pickerView.invalidate();
            }
            if (gifTabs != null) {
                gifTabs.invalidateTabs();
            }
        } else if (id == NotificationCenter.newEmojiSuggestionsAvailable) {
            if (emojiGridView != null && needEmojiSearch && (emojiSearchField.progressDrawable.isAnimating() || emojiGridView.getAdapter() == emojiSearchAdapter) && !TextUtils.isEmpty(emojiSearchAdapter.lastSearchEmojiString)) {
                emojiSearchAdapter.search(emojiSearchAdapter.lastSearchEmojiString);
            }
        }
    }

    private class StickersGridAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int stickersPerRow;
        private SparseArray<Object> rowStartPack = new SparseArray<>();
        private HashMap<Object, Integer> packStartPosition = new HashMap<>();
        private SparseArray<Object> cache = new SparseArray<>();
        private SparseArray<Object> cacheParents = new SparseArray<>();
        private SparseIntArray positionToRow = new SparseIntArray();
        private int totalItems;

        public StickersGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return totalItems != 0 ? totalItems + 1 : 0;
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public int getPositionForPack(Object pack) {
            Integer pos = packStartPosition.get(pack);
            if (pos == null) {
                return -1;
            }
            return pos;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 4;
            }
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    return 0;
                } else if (object instanceof String) {
                    return 3;
                } else {
                    return 2;
                }
            }
            return 1;
        }

        public int getTabForPosition(int position) {
            if (position == 0) {
                position = 1;
            }
            if (stickersPerRow == 0) {
                int width = getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                stickersPerRow = width / AndroidUtilities.dp(72);
            }
            int row = positionToRow.get(position, Integer.MIN_VALUE);
            if (row == Integer.MIN_VALUE) {
                return stickerSets.size() - 1 + stickersTabOffset;
            }
            Object pack = rowStartPack.get(row);
            if (pack instanceof String) {
                if ("recent".equals(pack)) {
                    return recentTabBum;
                } else {
                    return favTabBum;
                }
            } else {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) pack;
                int idx = stickerSets.indexOf(set);
                return idx + stickersTabOffset;
            }
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
                    view = new StickerSetNameCell(context, false);
                    ((StickerSetNameCell) view).setOnIconClickListener(v -> {
                        if (groupStickerSet != null) {
                            if (delegate != null) {
                                delegate.onStickersGroupClick(info.id);
                            }
                        } else {
                            MessagesController.getEmojiSettings(currentAccount).edit().putLong("group_hide_stickers_" + info.id, info.stickerset != null ? info.stickerset.id : 0).commit();
                            updateStickerTabs();
                            if (stickersGridAdapter != null) {
                                stickersGridAdapter.notifyDataSetChanged();
                            }
                        }
                    });
                    break;
                case 3:
                    view = new StickerSetGroupInfoCell(context);
                    ((StickerSetGroupInfoCell) view).setAddOnClickListener(v -> {
                        if (delegate != null) {
                            delegate.onStickersGroupClick(info.id);
                        }
                    });
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
                case 4:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                    cell.setSticker(sticker, cacheParents.get(position), false);
                    cell.setRecent(recentStickers.contains(sticker));
                    break;
                }
                case 1: {
                    EmptyCell cell = (EmptyCell) holder.itemView;
                    if (position == totalItems) {
                        int row = positionToRow.get(position - 1, Integer.MIN_VALUE);
                        if (row == Integer.MIN_VALUE) {
                            cell.setHeight(1);
                        } else {
                            ArrayList<TLRPC.Document> documents;
                            Object pack = rowStartPack.get(row);
                            if (pack instanceof TLRPC.TL_messages_stickerSet) {
                                documents = ((TLRPC.TL_messages_stickerSet) pack).documents;
                            } else if (pack instanceof String) {
                                if ("recent".equals(pack)) {
                                    documents = recentStickers;
                                } else {
                                    documents = favouriteStickers;
                                }
                            } else {
                                documents = null;
                            }
                            if (documents == null) {
                                cell.setHeight(1);
                            } else {
                                if (documents.isEmpty()) {
                                    cell.setHeight(AndroidUtilities.dp(8));
                                } else {
                                    int height = pager.getHeight() - (int) Math.ceil(documents.size() / (float) stickersPerRow) * AndroidUtilities.dp(82);
                                    cell.setHeight(height > 0 ? height : 1);
                                }
                            }
                        }
                    } else {
                        cell.setHeight(AndroidUtilities.dp(82));
                    }
                    break;
                }
                case 2: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    if (position == groupStickerPackPosition) {
                        int icon;
                        if (groupStickersHidden && groupStickerSet == null) {
                            icon = 0;
                        } else {
                            icon = groupStickerSet != null ? R.drawable.stickersclose : R.drawable.stickerset_close;
                        }
                        TLRPC.Chat chat = info != null ? MessagesController.getInstance(currentAccount).getChat(info.id) : null;
                        cell.setText(LocaleController.formatString("CurrentGroupStickers", R.string.CurrentGroupStickers, chat != null ? chat.title : "Group Stickers"), icon);
                    } else {
                        Object object = cache.get(position);
                        if (object instanceof TLRPC.TL_messages_stickerSet) {
                            TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) object;
                            if (set.set != null) {
                                cell.setText(set.set.title, 0);
                            }
                        } else if (object == recentStickers) {
                            cell.setText(LocaleController.getString("RecentStickers", R.string.RecentStickers), 0);
                        } else if (object == favouriteStickers) {
                            cell.setText(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers), 0);
                        }
                    }
                    break;
                }
                case 3: {
                    StickerSetGroupInfoCell cell = (StickerSetGroupInfoCell) holder.itemView;
                    cell.setIsLast(position == totalItems - 1);
                    break;
                }
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
            packStartPosition.clear();
            positionToRow.clear();
            cache.clear();
            totalItems = 0;
            ArrayList<TLRPC.TL_messages_stickerSet> packs = stickerSets;
            int startRow = 0;
            for (int a = -3; a < packs.size(); a++) {
                ArrayList<TLRPC.Document> documents;
                TLRPC.TL_messages_stickerSet pack = null;
                String key;
                if (a == -3) {
                    cache.put(totalItems++, "search");
                    startRow++;
                    continue;
                } else if (a == -2) {
                    documents = favouriteStickers;
                    packStartPosition.put(key = "fav", totalItems);
                } else if (a == -1) {
                    documents = recentStickers;
                    packStartPosition.put(key = "recent", totalItems);
                } else {
                    key = null;
                    pack = packs.get(a);
                    documents = pack.documents;
                    packStartPosition.put(pack, totalItems);
                }
                if (a == groupStickerPackNum) {
                    groupStickerPackPosition = totalItems;
                    if (documents.isEmpty()) {
                        rowStartPack.put(startRow, pack);
                        positionToRow.put(totalItems, startRow++);
                        rowStartPack.put(startRow, pack);
                        positionToRow.put(totalItems + 1, startRow++);
                        cache.put(totalItems++, pack);
                        cache.put(totalItems++, "group");
                        continue;
                    }
                }
                if (documents.isEmpty()) {
                    continue;
                }
                int count = (int) Math.ceil(documents.size() / (float) stickersPerRow);
                if (pack != null) {
                    cache.put(totalItems, pack);
                } else {
                    cache.put(totalItems, documents);
                }
                positionToRow.put(totalItems, startRow);
                for (int b = 0; b < documents.size(); b++) {
                    int num = 1 + b + totalItems;
                    cache.put(num, documents.get(b));
                    if (pack != null) {
                        cacheParents.put(num, pack);
                    } else {
                        cacheParents.put(num, key);
                    }
                    positionToRow.put(1 + b + totalItems, startRow + 1 + b / stickersPerRow);
                }
                for (int b = 0; b < count + 1; b++) {
                    if (pack != null) {
                        rowStartPack.put(startRow + b, pack);
                    } else {
                        rowStartPack.put(startRow + b, a == -1 ? "recent" : "fav");
                    }
                }
                totalItems += count * stickersPerRow + 1;
                startRow += count + 1;
            }
            super.notifyDataSetChanged();
        }
    }

    private class EmojiGridAdapter extends RecyclerListView.SelectionAdapter {

        private SparseIntArray positionToSection = new SparseIntArray();
        private SparseIntArray sectionToPosition = new SparseIntArray();
        private int itemCount;

        @Override
        public int getItemCount() {
            return itemCount;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ImageViewEmoji(getContext());
                    break;
                case 1:
                    view = new StickerSetNameCell(getContext(), true);
                    break;
                case 2:
                default:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    ImageViewEmoji imageView = (ImageViewEmoji) holder.itemView;

                    String code;
                    String coloredCode;
                    boolean recent;
                    if (needEmojiSearch) {
                        position--;
                    }

                    int count = Emoji.recentEmoji.size();
                    if (position < count) {
                        coloredCode = code = Emoji.recentEmoji.get(position);
                        recent = true;
                    } else {
                        code = null;
                        coloredCode = null;
                        for (int a = 0; a < EmojiData.dataColored.length; a++) {
                            int size = EmojiData.dataColored[a].length + 1;
                            if (position < count + size) {
                                coloredCode = code = EmojiData.dataColored[a][position - count - 1];
                                String color = Emoji.emojiColor.get(code);
                                if (color != null) {
                                    coloredCode = addColorToCode(coloredCode, color);
                                }
                                break;
                            }
                            count += size;
                        }
                        recent = false;
                    }
                    imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode), recent);
                    imageView.setTag(code);
                    imageView.setContentDescription(coloredCode);
                    break;
                }
                case 1: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    cell.setText(emojiTitles[positionToSection.get(position)], 0);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (needEmojiSearch && position == 0) {
                return 2;
            } else if (positionToSection.indexOfKey(position) >= 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public void notifyDataSetChanged() {
            positionToSection.clear();
            itemCount = Emoji.recentEmoji.size() + (needEmojiSearch ? 1 : 0);
            for (int a = 0; a < EmojiData.dataColored.length; a++) {
                positionToSection.put(itemCount, a);
                sectionToPosition.put(a, itemCount);
                itemCount += EmojiData.dataColored[a].length + 1;
            }
            updateEmojiTabs();
            super.notifyDataSetChanged();
        }
    }

    private class EmojiSearchAdapter extends RecyclerListView.SelectionAdapter {

        private ArrayList<MediaDataController.KeywordResult> result = new ArrayList<>();
        private String lastSearchEmojiString;
        private String lastSearchAlias;
        private Runnable searchRunnable;
        private boolean searchWas;

        @Override
        public int getItemCount() {
            if (result.isEmpty() && !searchWas) {
                return Emoji.recentEmoji.size() + 1;
            }
            if (!result.isEmpty()) {
                return result.size() + 1;
            }
            return 2;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ImageViewEmoji(getContext());
                    break;
                case 1:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
                case 2:
                default:
                    FrameLayout frameLayout = new FrameLayout(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int parentHeight;
                            View parent = (View) EmojiView.this.getParent();
                            if (parent != null) {
                                parentHeight = (int) (parent.getMeasuredHeight() - EmojiView.this.getY());
                            } else {
                                parentHeight = AndroidUtilities.dp(120);
                            }
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(parentHeight - searchFieldHeight, MeasureSpec.EXACTLY));
                        }
                    };

                    TextView textView = new TextView(getContext());
                    textView.setText(LocaleController.getString("NoEmojiFound", R.string.NoEmojiFound));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText));
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                    ImageView imageView = new ImageView(getContext());
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(R.drawable.smiles_panel_question);
                    imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.RIGHT));
                    imageView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean[] loadingUrl = new boolean[1];
                            BottomSheet.Builder builder = new BottomSheet.Builder(getContext());

                            LinearLayout linearLayout = new LinearLayout(getContext());
                            linearLayout.setOrientation(LinearLayout.VERTICAL);
                            linearLayout.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);

                            ImageView imageView1 = new ImageView(getContext());
                            imageView1.setImageResource(R.drawable.smiles_info);
                            linearLayout.addView(imageView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 15, 0, 0));

                            TextView textView = new TextView(getContext());
                            textView.setText(LocaleController.getString("EmojiSuggestions", R.string.EmojiSuggestions));
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 24, 0, 0));

                            textView = new TextView(getContext());
                            textView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EmojiSuggestionsInfo", R.string.EmojiSuggestionsInfo)));
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 11, 0, 0));

                            textView = new TextView(getContext());
                            textView.setText(LocaleController.formatString("EmojiSuggestionsUrl", R.string.EmojiSuggestionsUrl, lastSearchAlias != null ? lastSearchAlias : lastSearchKeyboardLanguage));
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                            textView.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 18, 0, 16));
                            textView.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (loadingUrl[0]) {
                                        return;
                                    }
                                    loadingUrl[0] = true;
                                    final AlertDialog progressDialog[] = new AlertDialog[]{new AlertDialog(getContext(), 3)};

                                    TLRPC.TL_messages_getEmojiURL req = new TLRPC.TL_messages_getEmojiURL();
                                    req.lang_code = lastSearchAlias != null ? lastSearchAlias : lastSearchKeyboardLanguage[0];
                                    int requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    try {
                                                        progressDialog[0].dismiss();
                                                    } catch (Throwable ignore) {

                                                    }
                                                    progressDialog[0] = null;

                                                    if (response instanceof TLRPC.TL_emojiURL) {
                                                        Browser.openUrl(getContext(), ((TLRPC.TL_emojiURL) response).url);
                                                        builder.getDismissRunnable().run();
                                                    }
                                                });
                                            }
                                    );

                                    AndroidUtilities.runOnUIThread(() -> {
                                        if (progressDialog[0] == null) {
                                            return;
                                        }
                                        progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(requestId, true));
                                        progressDialog[0].show();
                                    }, 1000);
                                }
                            });

                            builder.setCustomView(linearLayout);
                            builder.show();
                        }
                    });

                    view = frameLayout;
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    ImageViewEmoji imageView = (ImageViewEmoji) holder.itemView;

                    String code;
                    String coloredCode;
                    boolean recent;
                    position--;

                    if (result.isEmpty() && !searchWas) {
                        coloredCode = code = Emoji.recentEmoji.get(position);
                        recent = true;
                    } else {
                        coloredCode = code = result.get(position).emoji;
                        recent = false;
                    }
                    imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode), recent);
                    imageView.setTag(code);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            } else if (position == 1 && searchWas && result.isEmpty()) {
                return 2;
            }
            return 0;
        }

        public void search(String text) {
            if (TextUtils.isEmpty(text)) {
                lastSearchEmojiString = null;
                if (emojiGridView.getAdapter() != emojiAdapter) {
                    emojiGridView.setAdapter(emojiAdapter);
                    searchWas = false;
                }
                notifyDataSetChanged();
            } else {
                lastSearchEmojiString = text.toLowerCase();
            }
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            }
            if (!TextUtils.isEmpty(lastSearchEmojiString)) {
                AndroidUtilities.runOnUIThread(searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        emojiSearchField.progressDrawable.startAnimation();
                        String query = lastSearchEmojiString;
                        String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                        if (!Arrays.equals(lastSearchKeyboardLanguage, newLanguage)) {
                            MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
                        }
                        lastSearchKeyboardLanguage = newLanguage;
                        MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, lastSearchEmojiString, false, new MediaDataController.KeywordResultCallback() {
                            @Override
                            public void run(ArrayList<MediaDataController.KeywordResult> param, String alias) {
                                if (query.equals(lastSearchEmojiString)) {
                                    lastSearchAlias = alias;
                                    emojiSearchField.progressDrawable.stopAnimation();
                                    searchWas = true;
                                    if (emojiGridView.getAdapter() != emojiSearchAdapter) {
                                        emojiGridView.setAdapter(emojiSearchAdapter);
                                    }
                                    result = param;
                                    notifyDataSetChanged();
                                }
                            }
                        });
                    }
                }, 300);
            }
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

        public void destroyItem(ViewGroup viewGroup, int position, Object object) {
            viewGroup.removeView(views.get(position));
        }

        @Override
        public boolean canScrollToTab(int position) {
            if ((position == 1 || position == 2) && currentChatId != 0) {
                showStickerBanHint(position == 1);
                return false;
            }
            return true;
        }

        public int getCount() {
            return views.size();
        }

        public Drawable getPageIconDrawable(int position) {
            return tabIcons[position];
        }

        public CharSequence getPageTitle(int position) {
            switch(position) {
                case 0:
                    return LocaleController.getString("Emoji", R.string.Emoji);
                case 1:
                    return LocaleController.getString("AccDescrGIFs", R.string.AccDescrGIFs);
                case 2:
                    return LocaleController.getString("AccDescrStickers", R.string.AccDescrStickers);
            }
            return null;
        }

        @Override
        public void customOnDraw(Canvas canvas, int position) {
            if (position == 2 && !MediaDataController.getInstance(currentAccount).getUnreadStickerSets().isEmpty() && dotPaint != null) {
                int x = canvas.getWidth() / 2 + AndroidUtilities.dp(4 + 5);
                int y = canvas.getHeight() / 2 - AndroidUtilities.dp(13 - 5);
                canvas.drawCircle(x, y, AndroidUtilities.dp(5), dotPaint);
            }
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view = views.get(position);
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


    private class GifAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;
        private final boolean withRecent;
        private final GifProgressEmptyView progressEmptyView;
        private final int maxRecentRowsCount;

        private int reqId;
        private TLRPC.User bot;
        private String nextSearchOffset;
        private boolean searchEndReached;
        private boolean lastSearchIsEmoji;
        private String lastSearchImageString;
        private ArrayList<TLRPC.BotInlineResult> results = new ArrayList<>();
        private HashMap<String, TLRPC.BotInlineResult> resultsMap = new HashMap<>();

        private Runnable searchRunnable;
        private boolean searchingUser;

        private int itemsCount;
        private int recentItemsCount;
        private int trendingSectionItem = -1;
        private int firstResultItem = -1;

        private boolean showTrendingWhenSearchEmpty;

        public GifAdapter(Context context) {
            this(context, false, 0);
        }

        public GifAdapter(Context context, boolean withRecent) {
            this(context, withRecent, withRecent ? Integer.MAX_VALUE : 0);
        }

        public GifAdapter(Context context, boolean withRecent, int maxRecentRowsCount) {
            this.context = context;
            this.withRecent = withRecent;
            this.maxRecentRowsCount = maxRecentRowsCount;
            this.progressEmptyView = withRecent ? null : new GifProgressEmptyView(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return itemsCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1; // search field
            } else if (withRecent && position == trendingSectionItem) {
                return 2; // trending section
            } else if (!withRecent && results.isEmpty()) {
                return 3; // progress empty view
            }
            return 0; // gif
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ContextLinkCell cell = new ContextLinkCell(context);
                    cell.setCanPreviewGif(true);
                    view = cell;
                    break;
                case 1:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
                case 2:
                    final StickerSetNameCell cell1 = new StickerSetNameCell(context, false);
                    cell1.setText(LocaleController.getString("FeaturedGifs", R.string.FeaturedGifs), 0);
                    view = cell1;
                    final RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                    lp.topMargin = AndroidUtilities.dp(2.5f);
                    lp.bottomMargin = AndroidUtilities.dp(5.5f);
                    view.setLayoutParams(lp);
                    break;
                case 3:
                default:
                    view = progressEmptyView;
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    ContextLinkCell cell = (ContextLinkCell) holder.itemView;
                    if (firstResultItem >= 0 && position >= firstResultItem) {
                        cell.setLink(results.get(position - firstResultItem), bot, true, false, false, true);
                    } else {
                        cell.setGif(recentGifs.get(position - 1), false);
                    }
                    break;
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            updateRecentItemsCount();
            updateItems();
            super.notifyDataSetChanged();
        }

        private void updateItems() {
            trendingSectionItem = -1;
            firstResultItem = -1;

            itemsCount = 1; // search field

            if (withRecent) {
                itemsCount += recentItemsCount;
            }

            if (!results.isEmpty()) {
                if (withRecent && recentItemsCount > 0) {
                    trendingSectionItem = itemsCount++;
                }
                firstResultItem = itemsCount;
                itemsCount += results.size();
            } else if (!withRecent) {
                itemsCount++; // progress empty view
            }
        }

        private void updateRecentItemsCount() {
            if (!withRecent || maxRecentRowsCount == 0) {
                return;
            }

            if (maxRecentRowsCount == Integer.MAX_VALUE) {
                recentItemsCount = recentGifs.size();
                return;
            }

            if (gifGridView.getMeasuredWidth() == 0) {
                return;
            }

            final int listWidth = gifGridView.getMeasuredWidth();
            final int spanCount = gifLayoutManager.getSpanCount();
            final int preferredRowSize = AndroidUtilities.dp(100);

            int rowCount = 0;
            int spanLeft = spanCount;
            int currentItemsInRow = 0;
            recentItemsCount = 0;

            for (int i = 0, N = recentGifs.size(); i < N; i++) {
                final Size size = gifLayoutManager.fixSize(gifLayoutManager.getSizeForItem(recentGifs.get(i)));
                int requiredSpan = Math.min(spanCount, (int) Math.floor(spanCount * (size.width / size.height * preferredRowSize / listWidth)));
                if (spanLeft < requiredSpan) { // move to a new row
                    recentItemsCount += currentItemsInRow;
                    if (++rowCount == maxRecentRowsCount) {
                        break;
                    }
                    currentItemsInRow = 0;
                    spanLeft = spanCount;
                }
                currentItemsInRow++;
                spanLeft -= requiredSpan;
            }

            if (rowCount < maxRecentRowsCount) {
                recentItemsCount += currentItemsInRow;
            }
        }

        public void loadTrendingGifs() {
            search("", "", true, true, true);
        }

        private void searchBotUser() {
            if (searchingUser) {
                return;
            }
            searchingUser = true;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = MessagesController.getInstance(currentAccount).gifSearchBot;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                        String str = lastSearchImageString;
                        lastSearchImageString = null;
                        search(str, "", false);
                    });
                }
            });
        }

        public void search(String text) {
            if (withRecent) {
                return;
            }
            if (reqId != 0) {
                if (reqId >= 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                }
                reqId = 0;
            }
            lastSearchIsEmoji = false;
            if (progressEmptyView != null) {
                progressEmptyView.setLoadingState(false);
            }
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            }
            if (TextUtils.isEmpty(text)) {
                lastSearchImageString = null;
                if (showTrendingWhenSearchEmpty) {
                    loadTrendingGifs();
                } else {
                    final int page = gifTabs.getCurrentPosition();
                    if (page == gifRecentTabNum || page == gifTrendingTabNum) {
                        if (gifGridView.getAdapter() != gifAdapter) {
                            gifGridView.setAdapter(gifAdapter);
                        }
                    } else {
                        searchEmoji(MessagesController.getInstance(currentAccount).gifSearchEmojies.get(page - gifFirstEmojiTabNum));
                    }
                }
                return;
            } else {
                lastSearchImageString = text.toLowerCase();
            }
            if (!TextUtils.isEmpty(lastSearchImageString)) {
                AndroidUtilities.runOnUIThread(searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        search(text, "", true);
                    }
                }, 300);
            }
        }

        public void searchEmoji(String emoji) {
            if (lastSearchIsEmoji && TextUtils.equals(lastSearchImageString, emoji)) {
                gifLayoutManager.scrollToPositionWithOffset(1, 0);
                return;
            }
            search(emoji, "", true, true, true);
        }

        protected void search(final String query, final String offset, boolean searchUser) {
            search(query, offset, searchUser, false, false);
        }

        protected void search(final String query, final String offset, boolean searchUser, boolean isEmoji, boolean cache) {
            if (reqId != 0) {
                if (reqId >= 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                }
                reqId = 0;
            }

            lastSearchImageString = query;
            lastSearchIsEmoji = isEmoji;

            if (progressEmptyView != null) {
                progressEmptyView.setLoadingState(isEmoji);
            }

            TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).gifSearchBot);
            if (!(object instanceof TLRPC.User)) {
                if (searchUser) {
                    searchBotUser();
                    if (!withRecent) {
                        gifSearchField.progressDrawable.startAnimation();
                    }
                }
                return;
            }
            if (!withRecent && TextUtils.isEmpty(offset)) {
                gifSearchField.progressDrawable.startAnimation();
            }

            bot = (TLRPC.User) object;
            final String key = "gif_search_" + query + "_" + offset;
            final RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> processResponse(query, offset, searchUser, isEmoji, cache, key, response));

            if (!cache && !withRecent && isEmoji && TextUtils.isEmpty(offset)) {
                results.clear();
                resultsMap.clear();
                if (gifGridView.getAdapter() != this) {
                    gifGridView.setAdapter(this);
                }
                notifyDataSetChanged();
                scrollGifsToTop();
            }

            if (cache && gifCache.containsKey(key)) {
                processResponse(query, offset, searchUser, isEmoji, true, key, gifCache.get(key));
                return;
            }

            if (gifSearchPreloader.isLoading(key)) {
                return;
            }

            if (cache) {
                reqId = -1;
                MessagesStorage.getInstance(currentAccount).getBotCache(key, requestDelegate);
            } else {
                TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
                req.query = query == null ? "" : query;
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot);
                req.offset = offset;
                req.peer = new TLRPC.TL_inputPeerEmpty();
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        }

        @MainThread
        private void processResponse(final String query, final String offset, boolean searchUser, boolean isEmoji, boolean cache, String key, TLObject response) {
            if (query == null || !query.equals(lastSearchImageString)) {
                return;
            }
            reqId = 0;
            if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                search(query, offset, searchUser, isEmoji, false);
                return;
            }

            if (!withRecent) {
                if (TextUtils.isEmpty(offset)) {
                    results.clear();
                    resultsMap.clear();
                    gifSearchField.progressDrawable.stopAnimation();
                }
            }

            if (response instanceof TLRPC.messages_BotResults) {
                int addedCount = 0;
                int oldCount = results.size();
                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                if (!gifCache.containsKey(key)) {
                    gifCache.put(key, res);
                }
                if (!cache && res.cache_time != 0) {
                    MessagesStorage.getInstance(currentAccount).saveBotCache(key, res);
                }
                nextSearchOffset = res.next_offset;
                for (int a = 0; a < res.results.size(); a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (resultsMap.containsKey(result.id)) {
                        continue;
                    }
                    result.query_id = res.query_id;
                    results.add(result);
                    resultsMap.put(result.id, result);
                    addedCount++;
                }
                searchEndReached = oldCount == results.size() || TextUtils.isEmpty(nextSearchOffset);
                if (addedCount != 0) {
                    if (!isEmoji || oldCount != 0) {
                        updateItems();
                        if (withRecent) {
                            if (oldCount != 0) {
                                notifyItemChanged(recentItemsCount + 1 + oldCount);
                                notifyItemRangeInserted(recentItemsCount + 1 + oldCount + 1, addedCount);
                            } else {
                                notifyItemRangeInserted(recentItemsCount + 1, addedCount + 1);
                            }
                        } else {
                            if (oldCount != 0) {
                                notifyItemChanged(oldCount);
                            }
                            notifyItemRangeInserted(oldCount + 1, addedCount);
                        }
                    } else {
                        notifyDataSetChanged();
                    }
                } else if (results.isEmpty()) {
                    notifyDataSetChanged();
                }
            } else {
                notifyDataSetChanged();
            }

            if (!withRecent) {
                if (gifGridView.getAdapter() != this) {
                    gifGridView.setAdapter(this);
                }
                if (isEmoji && !TextUtils.isEmpty(query) && TextUtils.isEmpty(offset)) {
                    scrollGifsToTop();
                }
            }
        }
    }

    private class GifSearchPreloader {

        private final List<String> loadingKeys = new ArrayList<>();

        public boolean isLoading(String key) {
            return loadingKeys.contains(key);
        }

        public void preload(final String query) {
            preload(query, "", true);
        }

        private void preload(final String query, final String offset, boolean cache) {
            final String key = "gif_search_" + query + "_" + offset;

            if (cache && gifCache.containsKey(key)) {
                return;
            }

            final RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> processResponse(query, offset, cache, key, response));

            if (cache) {
                loadingKeys.add(key);
                MessagesStorage.getInstance(currentAccount).getBotCache(key, requestDelegate);
            } else {
                final MessagesController messagesController = MessagesController.getInstance(currentAccount);
                final TLObject gifSearchBot = messagesController.getUserOrChat(messagesController.gifSearchBot);
                if (!(gifSearchBot instanceof TLRPC.User)) {
                    return;
                }
                loadingKeys.add(key);
                TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
                req.query = query == null ? "" : query;
                req.bot = messagesController.getInputUser((TLRPC.User) gifSearchBot);
                req.offset = offset;
                req.peer = new TLRPC.TL_inputPeerEmpty();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        }

        private void processResponse(final String query, final String offset, boolean cache, String key, TLObject response) {
            loadingKeys.remove(key);

            if (gifSearchAdapter.lastSearchIsEmoji && gifSearchAdapter.lastSearchImageString.equals(query)) {
                gifSearchAdapter.processResponse(query, offset, false, true, cache, key, response);
            } else {
                if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                    preload(query, offset, false);
                } else if (response instanceof TLRPC.messages_BotResults && !gifCache.containsKey(key)) {
                    gifCache.put(key, (TLRPC.messages_BotResults) response);
                }
            }
        }
    }

    private class GifLayoutManager extends ExtendedGridLayoutManager {

        private Size size = new Size();

        public GifLayoutManager(Context context) {
            super(context, 100, true);
            setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position == 0 || gifGridView.getAdapter() == gifSearchAdapter && gifSearchAdapter.results.isEmpty()) {
                        return getSpanCount();
                    }
                    return getSpanSizeForItem(position - 1);
                }
            });
        }

        @Override
        protected Size getSizeForItem(int i) {
            TLRPC.Document document;
            ArrayList<TLRPC.DocumentAttribute> attributes;
            if (gifGridView.getAdapter() == gifAdapter) {
                if (i > gifAdapter.recentItemsCount) {
                    TLRPC.BotInlineResult result = gifAdapter.results.get(i - gifAdapter.recentItemsCount - 1);
                    document = result.document;
                    if (document != null) {
                        attributes = document.attributes;
                    } else if (result.content != null) {
                        attributes = result.content.attributes;
                    } else if (result.thumb != null) {
                        attributes = result.thumb.attributes;
                    } else {
                        attributes = null;
                    }
                } else if (i == gifAdapter.recentItemsCount) {
                    return null;
                } else {
                    document = recentGifs.get(i);
                    attributes = document.attributes;
                }
            } else if (!gifSearchAdapter.results.isEmpty()) {
                TLRPC.BotInlineResult result = gifSearchAdapter.results.get(i);
                document = result.document;
                if (document != null) {
                    attributes = document.attributes;
                } else if (result.content != null) {
                    attributes = result.content.attributes;
                } else if (result.thumb != null) {
                    attributes = result.thumb.attributes;
                } else {
                    attributes = null;
                }
            } else {
                document = null;
                attributes = null;
            }
            return getSizeForItem(document, attributes);
        }

        @Override
        protected int getFlowItemCount() {
            if (gifGridView.getAdapter() == gifSearchAdapter && gifSearchAdapter.results.isEmpty()) {
                return 0;
            }
            return getItemCount() - 1;
        }

        public Size getSizeForItem(TLRPC.Document document) {
            return getSizeForItem(document, document.attributes);
        }

        public Size getSizeForItem(TLRPC.Document document, List<TLRPC.DocumentAttribute> attributes) {
            size.width = size.height = 100;
            if (document != null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                if (thumb != null && thumb.w != 0 && thumb.h != 0) {
                    size.width = thumb.w;
                    size.height = thumb.h;
                }
            }
            if (attributes != null) {
                for (int b = 0; b < attributes.size(); b++) {
                    TLRPC.DocumentAttribute attribute = attributes.get(b);
                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        size.width = attribute.w;
                        size.height = attribute.h;
                        break;
                    }
                }
            }
            return size;
        }
    }

    private class GifProgressEmptyView extends FrameLayout {

        private final ImageView imageView;
        private final TextView textView;
        private final RadialProgressView progressView;

        private boolean loadingState;

        public GifProgressEmptyView(@NonNull Context context) {
            super(context);

            imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.gif_empty);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 59));

            textView = new TextView(getContext());
            textView.setText(LocaleController.getString("NoGIFsFound", R.string.NoGIFsFound));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 9));

            progressView = new RadialProgressView(context);
            progressView.setVisibility(GONE);
            progressView.setProgressColor(Theme.getColor(Theme.key_progressCircle));
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = gifGridView.getMeasuredHeight();

            if (!loadingState) {
                height = (int) ((height - searchFieldHeight - AndroidUtilities.dp(8)) / 3 * 1.7f);
            } else {
                height -= AndroidUtilities.dp(48 + 44);
            }

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        public boolean isLoadingState() {
            return loadingState;
        }

        public void setLoadingState(boolean loadingState) {
            if (this.loadingState != loadingState) {
                this.loadingState = loadingState;
                imageView.setVisibility(loadingState ? GONE : VISIBLE);
                textView.setVisibility(loadingState ? GONE : VISIBLE);
                progressView.setVisibility(loadingState ? VISIBLE : GONE);
            }
        }
    }

    private class StickersSearchGridAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private SparseArray<Object> rowStartPack = new SparseArray<>();
        private SparseArray<Object> cache = new SparseArray<>();
        private SparseArray<Object> cacheParent = new SparseArray<>();
        private SparseIntArray positionToRow = new SparseIntArray();
        private SparseArray<String> positionToEmoji = new SparseArray<>();
        private int totalItems;

        private ArrayList<TLRPC.StickerSetCovered> serverPacks = new ArrayList<>();
        private ArrayList<TLRPC.TL_messages_stickerSet> localPacks = new ArrayList<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Boolean> localPacksByShortName = new HashMap<>();
        private HashMap<TLRPC.TL_messages_stickerSet, Integer> localPacksByName = new HashMap<>();
        private HashMap<ArrayList<TLRPC.Document>, String> emojiStickers = new HashMap<>();
        private ArrayList<ArrayList<TLRPC.Document>> emojiArrays = new ArrayList<>();
        private SparseArray<TLRPC.StickerSetCovered> positionsToSets = new SparseArray<>();

        private int reqId;
        private int reqId2;

        private int emojiSearchId;
        boolean cleared;
        private String searchQuery;
        private Runnable searchRunnable = new Runnable() {

            private void clear() {
                if (cleared) {
                    return;
                }
                cleared = true;
                emojiStickers.clear();
                emojiArrays.clear();
                localPacks.clear();
                serverPacks.clear();
                localPacksByShortName.clear();
                localPacksByName.clear();
            }

            @Override
            public void run() {
                if (TextUtils.isEmpty(searchQuery)) {
                    return;
                }
                stickersSearchField.progressDrawable.startAnimation();
                cleared = false;
                int lastId = ++emojiSearchId;

                final ArrayList<TLRPC.Document> emojiStickersArray = new ArrayList<>(0);
                final LongSparseArray<TLRPC.Document> emojiStickersMap = new LongSparseArray<>(0);
                HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();
                if (searchQuery.length() <= 14) {
                    CharSequence emoji = searchQuery;
                    int length = emoji.length();
                    for (int a = 0; a < length; a++) {
                        if (a < length - 1 && (emoji.charAt(a) == 0xD83C && emoji.charAt(a + 1) >= 0xDFFB && emoji.charAt(a + 1) <= 0xDFFF || emoji.charAt(a) == 0x200D && (emoji.charAt(a + 1) == 0x2640 || emoji.charAt(a + 1) == 0x2642))) {
                            emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                            length -= 2;
                            a--;
                        } else if (emoji.charAt(a) == 0xfe0f) {
                            emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                            length--;
                            a--;
                        }
                    }
                    ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji.toString()) : null;
                    if (newStickers != null && !newStickers.isEmpty()) {
                        clear();
                        emojiStickersArray.addAll(newStickers);
                        for (int a = 0, size = newStickers.size(); a < size; a++) {
                            TLRPC.Document document = newStickers.get(a);
                            emojiStickersMap.put(document.id, document);
                        }
                        emojiStickers.put(emojiStickersArray, searchQuery);
                        emojiArrays.add(emojiStickersArray);
                    }
                }
                if (allStickers != null && !allStickers.isEmpty() && searchQuery.length() > 1) {
                    String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                    if (!Arrays.equals(lastSearchKeyboardLanguage, newLanguage)) {
                        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
                    }
                    lastSearchKeyboardLanguage = newLanguage;
                    MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, searchQuery, false, new MediaDataController.KeywordResultCallback() {
                        @Override
                        public void run(ArrayList<MediaDataController.KeywordResult> param, String alias) {
                            if (lastId != emojiSearchId) {
                                return;
                            }
                            boolean added = false;
                            for (int a = 0, size = param.size(); a < size; a++) {
                                String emoji = param.get(a).emoji;
                                ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji) : null;
                                if (newStickers != null && !newStickers.isEmpty()) {
                                    clear();
                                    if (!emojiStickers.containsKey(newStickers)) {
                                        emojiStickers.put(newStickers, emoji);
                                        emojiArrays.add(newStickers);
                                        added = true;
                                    }
                                }
                            }
                            if (added) {
                                notifyDataSetChanged();
                            }
                        }
                    });
                }
                ArrayList<TLRPC.TL_messages_stickerSet> local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
                int index;
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_FEATURED);
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            clear();
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                if ((!localPacks.isEmpty() || !emojiStickers.isEmpty()) && stickersGridView.getAdapter() != stickersSearchGridAdapter) {
                    stickersGridView.setAdapter(stickersSearchGridAdapter);
                }
                final TLRPC.TL_messages_searchStickerSets req = new TLRPC.TL_messages_searchStickerSets();
                req.q = searchQuery;
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_messages_foundStickerSets) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (req.q.equals(searchQuery)) {
                                clear();
                                stickersSearchField.progressDrawable.stopAnimation();
                                reqId = 0;
                                if (stickersGridView.getAdapter() != stickersSearchGridAdapter) {
                                    stickersGridView.setAdapter(stickersSearchGridAdapter);
                                }
                                TLRPC.TL_messages_foundStickerSets res = (TLRPC.TL_messages_foundStickerSets) response;
                                serverPacks.addAll(res.sets);
                                notifyDataSetChanged();
                            }
                        });
                    }
                });
                if (Emoji.isValidEmoji(searchQuery)) {
                    final TLRPC.TL_messages_getStickers req2 = new TLRPC.TL_messages_getStickers();
                    req2.emoticon = searchQuery;
                    req2.hash = 0;
                    reqId2 = ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (req2.emoticon.equals(searchQuery)) {
                            reqId2 = 0;
                            if (!(response instanceof TLRPC.TL_messages_stickers)) {
                                return;
                            }
                            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
                            int oldCount = emojiStickersArray.size();
                            for (int a = 0, size = res.stickers.size(); a < size; a++) {
                                TLRPC.Document document = res.stickers.get(a);
                                if (emojiStickersMap.indexOfKey(document.id) >= 0) {
                                    continue;
                                }
                                emojiStickersArray.add(document);
                            }
                            int newCount = emojiStickersArray.size();
                            if (oldCount != newCount) {
                                emojiStickers.put(emojiStickersArray, searchQuery);
                                if (oldCount == 0) {
                                    emojiArrays.add(emojiStickersArray);
                                }
                                notifyDataSetChanged();
                            }
                        }
                    }));
                }
                notifyDataSetChanged();
            }
        };

        public StickersSearchGridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            if (totalItems != 1) {
                return totalItems + 1;
            } else {
                return 2;
            }
        }

        public Object getItem(int i) {
            return cache.get(i);
        }

        public void search(String text) {
            if (reqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = 0;
            }
            if (reqId2 != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId2, true);
                reqId2 = 0;
            }
            if (TextUtils.isEmpty(text)) {
                searchQuery = null;
                localPacks.clear();
                emojiStickers.clear();
                serverPacks.clear();
                if (stickersGridView.getAdapter() != stickersGridAdapter) {
                    stickersGridView.setAdapter(stickersGridAdapter);
                }
                notifyDataSetChanged();
            } else {
                searchQuery = text.toLowerCase();
            }
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            AndroidUtilities.runOnUIThread(searchRunnable, 300);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 4;
            } else if (position == 1 && totalItems == 1) {
                return 5;
            }
            Object object = cache.get(position);
            if (object != null) {
                if (object instanceof TLRPC.Document) {
                    return 0;
                } else if (object instanceof TLRPC.StickerSetCovered) {
                    return 3;
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
                    view = new StickerSetNameCell(context, false);
                    break;
                case 3:
                    view = new FeaturedStickerSetInfoCell(context, 17);
                    ((FeaturedStickerSetInfoCell) view).setAddOnClickListener(v -> {
                        FeaturedStickerSetInfoCell parent1 = (FeaturedStickerSetInfoCell) v.getParent();
                        TLRPC.StickerSetCovered pack = parent1.getStickerSet();
                        if (installingStickerSets.indexOfKey(pack.set.id) >= 0 || removingStickerSets.indexOfKey(pack.set.id) >= 0) {
                            return;
                        }
                        if (parent1.isInstalled()) {
                            removingStickerSets.put(pack.set.id, pack);
                            delegate.onStickerSetRemove(parent1.getStickerSet());
                        } else {
                            parent1.setAddDrawProgress(true, true);
                            installingStickerSets.put(pack.set.id, pack);
                            delegate.onStickerSetAdd(parent1.getStickerSet());
                        }
                    });
                    break;
                case 4:
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
                case 5:
                    FrameLayout frameLayout = new FrameLayout(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int height = stickersGridView.getMeasuredHeight();
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int) ((height - searchFieldHeight - AndroidUtilities.dp(8)) / 3 * 1.7f), MeasureSpec.EXACTLY));
                        }
                    };

                    ImageView imageView = new ImageView(context);
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(R.drawable.stickers_empty);
                    imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 59));

                    TextView textView = new TextView(context);
                    textView.setText(LocaleController.getString("NoStickersFound", R.string.NoStickersFound));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setTextColor(Theme.getColor(Theme.key_chat_emojiPanelEmptyText));
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 9));

                    view = frameLayout;
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                    StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                    cell.setSticker(sticker, cacheParent.get(position), positionToEmoji.get(position), false);
                    cell.setRecent(recentStickers.contains(sticker) || favouriteStickers.contains(sticker));
                    break;
                }
                case 1: {
                    EmptyCell cell = (EmptyCell) holder.itemView;
                    if (position == totalItems) {
                        int row = positionToRow.get(position - 1, Integer.MIN_VALUE);
                        if (row == Integer.MIN_VALUE) {
                            cell.setHeight(1);
                        } else {
                            Object pack = rowStartPack.get(row);
                            Integer count;
                            if (pack instanceof TLRPC.TL_messages_stickerSet) {
                                count = ((TLRPC.TL_messages_stickerSet) pack).documents.size();
                            } else if (pack instanceof Integer) {
                                count = (Integer) pack;
                            } else {
                                count = null;
                            }
                            if (count == null) {
                                cell.setHeight(1);
                            } else {
                                if (count == 0) {
                                    cell.setHeight(AndroidUtilities.dp(8));
                                } else {
                                    int height = pager.getHeight() - (int) Math.ceil(count / (float) stickersGridAdapter.stickersPerRow) * AndroidUtilities.dp(82);
                                    cell.setHeight(height > 0 ? height : 1);
                                }
                            }
                        }
                    } else {
                        cell.setHeight(AndroidUtilities.dp(82));
                    }
                    break;
                }
                case 2: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    Object object = cache.get(position);
                    if (object instanceof TLRPC.TL_messages_stickerSet) {
                        TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) object;
                        if (!TextUtils.isEmpty(searchQuery) && localPacksByShortName.containsKey(set)) {
                            if (set.set != null) {
                                cell.setText(set.set.title, 0);
                            }
                            cell.setUrl(set.set.short_name, searchQuery.length());
                        } else {
                            Integer start = localPacksByName.get(set);
                            if (set.set != null && start != null) {
                                cell.setText(set.set.title, 0, start, !TextUtils.isEmpty(searchQuery) ? searchQuery.length() : 0);
                            }
                            cell.setUrl(null, 0);
                        }
                    }
                    break;
                }
                case 3: {
                    TLRPC.StickerSetCovered stickerSetCovered = (TLRPC.StickerSetCovered) cache.get(position);
                    FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) holder.itemView;
                    boolean installing = installingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
                    boolean removing = removingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
                    if (installing || removing) {
                        if (installing && cell.isInstalled()) {
                            installingStickerSets.remove(stickerSetCovered.set.id);
                            installing = false;
                        } else if (removing && !cell.isInstalled()) {
                            removingStickerSets.remove(stickerSetCovered.set.id);
                            removing = false;
                        }
                    }
                    cell.setAddDrawProgress(installing, false);
                    int idx = TextUtils.isEmpty(searchQuery) ? -1 : AndroidUtilities.indexOfIgnoreCase(stickerSetCovered.set.title, searchQuery);
                    if (idx >= 0) {
                        cell.setStickerSet(stickerSetCovered, false, false, idx, searchQuery.length());
                    } else {
                        cell.setStickerSet(stickerSetCovered, false);
                        if (!TextUtils.isEmpty(searchQuery) && AndroidUtilities.indexOfIgnoreCase(stickerSetCovered.set.short_name, searchQuery) == 0) {
                            cell.setUrl(stickerSetCovered.set.short_name, searchQuery.length());
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            rowStartPack.clear();
            positionToRow.clear();
            cache.clear();
            positionsToSets.clear();
            positionToEmoji.clear();
            totalItems = 0;
            int startRow = 0;
            for (int a = -1, serverCount = serverPacks.size(), localCount = localPacks.size(), emojiCount = (emojiArrays.isEmpty() ? 0 : 1); a < serverCount + localCount + emojiCount; a++) {
                ArrayList<TLRPC.Document> documents;
                Object pack = null;
                String key;
                if (a == -1) {
                    cache.put(totalItems++, "search");
                    startRow++;
                    continue;
                } else {
                    int idx = a;
                    if (idx < localCount) {
                        TLRPC.TL_messages_stickerSet set = localPacks.get(idx);
                        documents = set.documents;
                        pack = set;
                    } else {
                        idx -= localCount;
                        if (idx < emojiCount) {
                            int documentsCount = 0;
                            String lastEmoji = "";
                            for (int i = 0, N = emojiArrays.size(); i < N; i++) {
                                documents = emojiArrays.get(i);
                                String emoji = emojiStickers.get(documents);
                                if (emoji != null && !lastEmoji.equals(emoji)) {
                                    lastEmoji = emoji;
                                    positionToEmoji.put(totalItems + documentsCount, lastEmoji);
                                }
                                for (int b = 0, size = documents.size(); b < size; b++) {
                                    int num = documentsCount + totalItems;
                                    int row = startRow + documentsCount / stickersGridAdapter.stickersPerRow;

                                    TLRPC.Document document = documents.get(b);
                                    cache.put(num, document);
                                    Object parent = MediaDataController.getInstance(currentAccount).getStickerSetById(MediaDataController.getStickerSetId(document));
                                    if (parent != null) {
                                        cacheParent.put(num, parent);
                                    }
                                    positionToRow.put(num, row);
                                    if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                                        positionsToSets.put(num, (TLRPC.StickerSetCovered) pack);
                                    }
                                    documentsCount++;
                                }
                            }
                            int count = (int) Math.ceil(documentsCount / (float) stickersGridAdapter.stickersPerRow);
                            for (int b = 0, N = count; b < N; b++) {
                                rowStartPack.put(startRow + b, documentsCount);
                            }
                            totalItems += count * stickersGridAdapter.stickersPerRow;
                            startRow += count;
                            continue;
                        } else {
                            idx -= emojiCount;
                            TLRPC.StickerSetCovered set = serverPacks.get(idx);
                            documents = set.covers;
                            pack = set;
                        }
                    }
                }
                if (documents.isEmpty()) {
                    continue;
                }
                int count = (int) Math.ceil(documents.size() / (float) stickersGridAdapter.stickersPerRow);
                cache.put(totalItems, pack);
                if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                    positionsToSets.put(totalItems, (TLRPC.StickerSetCovered) pack);
                }
                positionToRow.put(totalItems, startRow);
                for (int b = 0, size = documents.size(); b < size; b++) {
                    int num = 1 + b + totalItems;
                    int row = startRow + 1 + b / stickersGridAdapter.stickersPerRow;
                    TLRPC.Document document = documents.get(b);
                    cache.put(num, document);
                    if (pack != null) {
                        cacheParent.put(num, pack);
                    }
                    positionToRow.put(num, row);
                    if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                        positionsToSets.put(num, (TLRPC.StickerSetCovered) pack);
                    }
                }
                for (int b = 0, N = count + 1; b < N; b++) {
                    rowStartPack.put(startRow + b, pack);
                }
                totalItems += 1 + count * stickersGridAdapter.stickersPerRow;
                startRow += count + 1;
            }
            super.notifyDataSetChanged();
        }
    }
}
/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Cells.StickerSetGroupInfoCell;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ListView.RecyclerListViewWithOverlayDraw;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.StickersActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EmojiView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final static int TAB_EMOJI = 0;
    private final static int TAB_GIFS = 1;
    private final static int TAB_STICKERS = 2;

    private ArrayList<Tab> allTabs = new ArrayList<>();
    private ArrayList<Tab> currentTabs = new ArrayList<>();
    private boolean ignorePagerScroll;
    private ViewPager pager;
    private FrameLayout bottomTabContainer;
    private FrameLayout bulletinContainer;
    private View bottomTabContainerBackground;
    private ImageView floatingButton;
    private PagerSlidingTabStrip typeTabs;
    private ImageView backspaceButton;
    private ImageView stickerSettingsButton;
    private ImageView searchButton;
    private View shadowLine;
    private AnimatorSet bottomTabContainerAnimation;
    private AnimatorSet backspaceButtonAnimation;
    private AnimatorSet stickersButtonAnimation;
    private float lastBottomScrollDy;

    private EmojiTabsStrip emojiTabs;
    private FrameLayout emojiContainer;
    private View emojiTabsShadow;
    private EmojiGridView emojiGridView;
    private GridLayoutManager emojiLayoutManager;
    private EmojiGridAdapter emojiAdapter;
    private EmojiSearchAdapter emojiSearchAdapter;
    private TrendingAdapter trendingEmojiAdapter;
    private SearchField emojiSearchField;
    private AnimatorSet emojiTabShadowAnimator;
    private RecyclerAnimationScrollHelper stickersScrollHelper;
    private RecyclerAnimationScrollHelper emojiScrollHelper;
    private boolean firstEmojiAttach = true;
    private boolean needEmojiSearch;
    private int hasRecentEmoji = -1;
    private boolean hasChatStickers;
    private boolean emojiSmoothScrolling;

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
    private FrameLayout stickersTabContainer;
    private RecyclerListView stickersGridView;
    private GridLayoutManager stickersLayoutManager;
    private TrendingAdapter trendingAdapter;
    private SearchField stickersSearchField;
    private int stickersMinusDy;
    private boolean firstStickersAttach = true;
    private boolean ignoreStickersScroll;
    private boolean stickersContainerAttached;
    EmojiPagesAdapter emojiPagerAdapter;

    private AnimatorSet searchAnimation;

    private TextView mediaBanTooltip;
    private DragListener dragListener;
    private boolean showing;

    private final int[] tabsMinusDy = new int[3];
    private ObjectAnimator[] tabsYAnimators = new ObjectAnimator[3];
    private boolean firstTabUpdate;
    private ChooseStickerActionTracker chooseStickerActionTracker;

    public void setAllow(boolean allowStickers, boolean allowGifs, boolean animated) {
        currentTabs.clear();
        for (int i = 0; i < allTabs.size(); i++) {
            if (allTabs.get(i).type == TAB_EMOJI) {
                currentTabs.add(allTabs.get(i));
            } if (allTabs.get(i).type == TAB_GIFS && allowGifs) {
                currentTabs.add(allTabs.get(i));
            }  if (allTabs.get(i).type == TAB_STICKERS && allowStickers) {
                currentTabs.add(allTabs.get(i));
            }
        }
        if (typeTabs != null) {
            AndroidUtilities.updateViewVisibilityAnimated(typeTabs, currentTabs.size() > 1, 1, animated);
        }
        if (pager != null) {
            pager.setAdapter(null);
            pager.setAdapter(emojiPagerAdapter);
            if (typeTabs != null) {
                typeTabs.setViewPager(pager);
            }
        }
    }

    private boolean allowEmojisForNonPremium;
    public void allowEmojisForNonPremium(boolean allow) {
        allowEmojisForNonPremium = allow;
    }


    @IntDef({Type.STICKERS, Type.EMOJIS, Type.GIFS})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Type {
        int STICKERS = 0;
        int EMOJIS = 1;
        int GIFS = 2;
    }

    private String[] lastSearchKeyboardLanguage;

    private Drawable[] tabIcons;
    private Drawable[] stickerIcons;
    private Drawable[] gifIcons;
    private String[] emojiTitles;

    private int searchFieldHeight;

    public int currentAccount = UserConfig.selectedAccount;
    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
    private int groupStickerPackNum;
    private int groupStickerPackPosition;
    private boolean groupStickersHidden;
    private TLRPC.TL_messages_stickerSet groupStickerSet;

    private ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private ArrayList<TLRPC.Document> recentStickers = new ArrayList<>();
    private ArrayList<TLRPC.Document> favouriteStickers = new ArrayList<>();
    private ArrayList<TLRPC.Document> premiumStickers = new ArrayList<>();
    private ArrayList<TLRPC.StickerSetCovered> featuredStickerSets = new ArrayList<>();

    private ArrayList<TLRPC.StickerSetCovered> featuredEmojiSets = new ArrayList<>();
    private ArrayList<Long> keepFeaturedDuplicate = new ArrayList<>();
    private ArrayList<Long> expandedEmojiSets = new ArrayList<>();
    public ArrayList<Long> installedEmojiSets = new ArrayList<>();
    private ArrayList<EmojiPack> emojipacksProcessed = new ArrayList<>();
    private HashMap<Long, Utilities.Callback<TLRPC.TL_messages_stickerSet>> toInstall = new HashMap<>();

    private Paint dotPaint;

    private EmojiViewDelegate delegate;

    private long currentChatId;
    boolean emojiBanned;
    boolean stickersBanned;

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
    private int recentTabNum = -2;
    private int favTabNum = -2;
    private int trendingTabNum = -2;
    private int premiumTabNum = -2;

    private TLRPC.ChatFull info;

    private boolean isLayout;
    private int currentBackgroundType = -1;
    private Object outlineProvider;
    private boolean forseMultiwindowLayout;

    private Paint emojiLockPaint;
    private Drawable emojiLockDrawable;

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
    private float lastStickersX;
    private boolean expandStickersByDragg;
    private BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private Drawable searchIconDrawable;
    private Drawable searchIconDotDrawable;
    private boolean allowAnimatedEmoji;

    private Long emojiScrollToStickerId;

    private LongSparseArray<AnimatedEmojiDrawable> animatedEmojiDrawables;
    private PorterDuffColorFilter animatedEmojiTextColorFilter;

    private Runnable checkExpandStickerTabsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!stickersTab.isDragging()) {
                expandStickersByDragg = false;
                updateStickerTabsPosition();
            }
        }
    };

    public interface EmojiViewDelegate {
        default boolean onBackspace() {
            return false;
        }

        default boolean isUserSelf() {
            return false;
        }

        default void onEmojiSelected(String emoji) {

        }

        default void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean isRecent) {

        }

        default void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate) {

        }

        default void onStickersSettingsClick() {

        }

        default void onEmojiSettingsClick(ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks) {

        }

        default void onStickersGroupClick(long chatId) {

        }

        default void onGifSelected(View view, Object gif, String query, Object parent, boolean notify, int scheduleDate) {

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

        default void onAnimatedEmojiUnlockClick() {
            // should open premium bottom sheet feature
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

        default int getThreadId() {
            return 0;
        }

        default void showTrendingStickersAlert(TrendingStickersLayout layout) {

        }

        default void invalidateEnterView() {

        }

        default float getProgressToSearchOpened() {
            return 0f;
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
        public boolean can() {
            return fragment != null;
        }

        @Override
        public void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate) {
            delegate.onStickerSelected(null, sticker, query, parent, null, notify, scheduleDate);
        }

        @Override
        public void resetTouch() {
            if (emojiGridView != null) {
                emojiGridView.clearAllTouches();
            }
        }

        @Override
        public void sendEmoji(TLRPC.Document emoji) {
            if (fragment instanceof ChatActivity) {
                ((ChatActivity) fragment).sendAnimatedEmoji(emoji, true, 0);
            }
        }

        @Override
        public void setAsEmojiStatus(TLRPC.Document document, Integer until) {
            TLRPC.EmojiStatus status;
            if (document == null) {
                status = new TLRPC.TL_emojiStatusEmpty();
            } else if (until != null) {
                status = new TLRPC.TL_emojiStatusUntil();
                ((TLRPC.TL_emojiStatusUntil) status).document_id = document.id;
                ((TLRPC.TL_emojiStatusUntil) status).until = until;
            } else {
                status = new TLRPC.TL_emojiStatus();
                ((TLRPC.TL_emojiStatus) status).document_id = document.id;
            }
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            final TLRPC.EmojiStatus previousEmojiStatus = user == null ? new TLRPC.TL_emojiStatusEmpty() : user.emoji_status;
            MessagesController.getInstance(currentAccount).updateEmojiStatus(status);

            Runnable undoAction = () -> MessagesController.getInstance(currentAccount).updateEmojiStatus(previousEmojiStatus);
            if (document == null) {
                final Bulletin.SimpleLayout layout = new Bulletin.SimpleLayout(getContext(), resourcesProvider);
                layout.textView.setText(LocaleController.getString("RemoveStatusInfo", R.string.RemoveStatusInfo));
                layout.imageView.setImageResource(R.drawable.msg_settings_premium);
                layout.imageView.setScaleX(.8f);
                layout.imageView.setScaleY(.8f);
                layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                Bulletin.UndoButton undoButton = new Bulletin.UndoButton(getContext(), true, resourcesProvider);
                undoButton.setUndoAction(undoAction);
                layout.setButton(undoButton);
                if (fragment != null) {
                    Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT).show();
                } else {
                    Bulletin.make(bulletinContainer, layout, Bulletin.DURATION_SHORT).show();
                }
            } else {
                BulletinFactory factory = fragment != null ? BulletinFactory.of(fragment) : BulletinFactory.of(bulletinContainer, resourcesProvider);
                factory.createEmojiBulletin(document, LocaleController.getString("SetAsEmojiStatusInfo", R.string.SetAsEmojiStatusInfo), LocaleController.getString("Undo", R.string.Undo), undoAction).show();
            }
        }

        @Override
        public void copyEmoji(TLRPC.Document document) {
            Spannable spannable = SpannableStringBuilder.valueOf(MessageObject.findAnimatedEmojiEmoticon(document));
            spannable.setSpan(new AnimatedEmojiSpan(document, null), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (AndroidUtilities.addToClipboard(spannable)) {
                BulletinFactory factory = fragment != null ? BulletinFactory.of(fragment) : BulletinFactory.of(bulletinContainer, resourcesProvider);
                factory.createCopyBulletin(LocaleController.getString("EmojiCopied", R.string.EmojiCopied)).show();
            }
        }

        @Override
        public boolean needCopy() {
            return true;
        }

        @Override
        public boolean needRemoveFromRecent(TLRPC.Document document) {
            return document != null && Emoji.recentEmoji.contains("animated_" + document.id);
        }

        @Override
        public void removeFromRecent(TLRPC.Document document) {
            if (document != null) {
                Emoji.removeRecentEmoji("animated_" + document.id);
                if (emojiAdapter != null) {
                    emojiAdapter.notifyDataSetChanged();
                }
            }
        }

        @Override
        public Boolean canSetAsStatus(TLRPC.Document document) {
            if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                return null;
            }
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (user == null) {
                return null;
            }
            Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
            return document != null && (emojiStatusId == null || emojiStatusId != document.id);
        }

        @Override
        public boolean needSend(int contentType) {
            if (contentType == ContentPreviewViewer.CONTENT_TYPE_EMOJI) {
                return fragment instanceof ChatActivity && ((ChatActivity) fragment).canSendMessage() && (UserConfig.getInstance(UserConfig.selectedAccount).isPremium() || ((ChatActivity) fragment).getCurrentUser() != null && UserObject.isUserSelf(((ChatActivity) fragment).getCurrentUser()));
            }
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
                delegate.onGifSelected(null, gif, null, parent, notify, scheduleDate);
            } else if (gifGridView.getAdapter() == gifSearchAdapter) {
                delegate.onGifSelected(null, gif, null, parent, notify, scheduleDate);
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

        @Override
        public String getQuery(boolean isGif) {
            if (isGif) {
                return gifGridView.getAdapter() == gifSearchAdapter ? gifSearchAdapter.lastSearchImageString : null;
            }
            return emojiGridView.getAdapter() == emojiSearchAdapter ? emojiSearchAdapter.lastSearchEmojiString : null;
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
        if (stickersSearchField != null) {
            stickersSearchField.searchEditText.setEnabled(enabled);
        }
        if (gifSearchField != null) {
            gifSearchField.searchEditText.setEnabled(enabled);
        }
        if (emojiSearchField != null) {
            emojiSearchField.searchEditText.setEnabled(enabled);
        }
    }

    private class SearchField extends FrameLayout {

        private int type;
        private ImageView searchImageView;
        private SearchStateDrawable searchStateDrawable;
        private EditTextBoldCursor searchEditText;
        private View shadowView;
        private View backgroundView;
        private ImageView clear;
        private FrameLayout box;
        private AnimatorSet shadowAnimator;
        private StickerCategoriesListView categoriesListView;
        private FrameLayout inputBox;
        private View inputBoxGradient;

        private StickerCategoriesListView.EmojiCategory recent;
        private StickerCategoriesListView.EmojiCategory trending;

        @SuppressLint("ClickableViewAccessibility")
        public SearchField(Context context, int type) {
            super(context);
            this.type = type;

            shadowView = new View(context);
            shadowView.setAlpha(0.0f);
            shadowView.setTag(1);
            shadowView.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            addView(shadowView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT));

            backgroundView = new View(context);
            backgroundView.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            addView(backgroundView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, searchFieldHeight));

            box = new FrameLayout(context);
            box.setBackground(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_chat_emojiSearchBackground)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                box.setClipToOutline(true);
                box.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), (int) dp(18));
                    }
                });
            }
            if (type == 2) {
                addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL, 10, 8, 10, 8));
            } else {
                addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL, 10, 6, 10, 8));
            }

            inputBox = new FrameLayout(context);
            box.addView(inputBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 38, 0, 0, 0));

            searchImageView = new ImageView(context);
            searchStateDrawable = new SearchStateDrawable();
            searchStateDrawable.setIconState(SearchStateDrawable.State.STATE_SEARCH, false);
            searchStateDrawable.setColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            searchImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchImageView.setImageDrawable(searchStateDrawable);
            searchImageView.setOnClickListener(e -> {
                if (searchStateDrawable.getIconState() == SearchStateDrawable.State.STATE_BACK) {
                    searchEditText.setText("");
                    search(null, false);
                    if (categoriesListView != null) {
                        categoriesListView.scrollToStart();
                        categoriesListView.selectCategory(null);
                        categoriesListView.updateCategoriesShown(true, true);
                    }
                    toggleClear(false);
                    if (searchEditText != null) {
                        searchEditText.clearAnimation();
                        searchEditText.animate().translationX(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                    showInputBoxGradient(false);
                }
            });
            box.addView(searchImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP));

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
            searchEditText.setHintTextColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            searchEditText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            searchEditText.setHint(LocaleController.getString("Search", R.string.Search));
            searchEditText.setCursorColor(getThemedColor(Theme.key_featuredStickers_addedIcon));
            searchEditText.setCursorSize(dp(20));
            searchEditText.setCursorWidth(1.5f);
            searchEditText.setTranslationY(dp(-2));
            inputBox.addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 0, 0, 28, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateButton();
                    final String query = searchEditText.getText().toString();
                    search(query, true);
                    if (categoriesListView != null) {
                        categoriesListView.selectCategory(null);
                        categoriesListView.updateCategoriesShown(TextUtils.isEmpty(query), true);
                    }
                    toggleClear(!TextUtils.isEmpty(query));
                    if (searchEditText != null) {
                        searchEditText.clearAnimation();
                        searchEditText.animate().translationX(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                    showInputBoxGradient(false);
                }
            });

            inputBoxGradient = new View(context);
            Drawable gradientDrawable = context.getResources().getDrawable(R.drawable.gradient_right).mutate();
            gradientDrawable.setColorFilter(new PorterDuffColorFilter(Theme.blendOver(getThemedColor(Theme.key_chat_emojiPanelBackground), getThemedColor(Theme.key_chat_emojiSearchBackground)), PorterDuff.Mode.MULTIPLY));
            inputBoxGradient.setBackground(gradientDrawable);
            inputBoxGradient.setAlpha(0f);
            inputBox.addView(inputBoxGradient, LayoutHelper.createFrame(18, LayoutHelper.MATCH_PARENT, Gravity.LEFT));

            clear = new ImageView(context);
            clear.setScaleType(ImageView.ScaleType.CENTER);
            clear.setImageDrawable(new CloseProgressDrawable2(1.25f) {
                { setSide(AndroidUtilities.dp(7)); }
                @Override
                protected int getCurrentColor() {
                    return Theme.getColor(Theme.key_chat_emojiSearchIcon, resourcesProvider);
                }
            });
            clear.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(15)));
            clear.setAlpha(0f);
            clear.setOnClickListener(e -> {
                searchEditText.setText("");
                search(null, false);
                if (categoriesListView != null) {
                    categoriesListView.scrollToStart();
                    categoriesListView.selectCategory(null);
                    categoriesListView.updateCategoriesShown(true, true);
                }
                toggleClear(false);
                if (searchEditText != null) {
                    searchEditText.clearAnimation();
                    searchEditText.animate().translationX(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                }
                showInputBoxGradient(false);
            });
            box.addView(clear, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP));

            if (type != 1 || allowAnimatedEmoji && UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                categoriesListView = new StickerCategoriesListView(context, null, StickerCategoriesListView.CategoriesType.DEFAULT, resourcesProvider) {
                    @Override
                    public void selectCategory(int categoryIndex) {
                        super.selectCategory(categoryIndex);
                        showBottomTab(categoriesListView.getSelectedCategory() == null, true);
                        if (type == 1 && emojiTabs != null) {
                            emojiTabs.showSelected(categoriesListView.getSelectedCategory() == null);
                        } else if (type == 0 && stickersTab != null) {
                            stickersTab.showSelected(categoriesListView.getSelectedCategory() == null);
                        }
                        updateButton();
                    }

                    @Override
                    protected boolean isTabIconsAnimationEnabled(boolean loaded) {
                        return LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);
                    }
                };
                categoriesListView.setDontOccupyWidth((int) (searchEditText.getPaint().measureText(searchEditText.getHint() + "")) + dp(16));
                categoriesListView.setBackgroundColor(Theme.blendOver(getThemedColor(Theme.key_chat_emojiPanelBackground), getThemedColor(Theme.key_chat_emojiSearchBackground)));
                categoriesListView.setOnScrollIntoOccupiedWidth(scrolled -> {
                    searchEditText.setTranslationX(-Math.max(0, scrolled));
                    showInputBoxGradient(scrolled > 0);
                    updateButton();
                });
                categoriesListView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            ignorePagerScroll = true;
                        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                            ignorePagerScroll = false;
                        }
                        return false;
                    }
                });
                categoriesListView.setOnCategoryClick(category -> {
                    if (category == recent) {
                        showInputBoxGradient(false);
                        categoriesListView.selectCategory(recent);
                        gifSearchField.searchEditText.setText("");
                        gifLayoutManager.scrollToPositionWithOffset(0, 0);
                        return;
                    } else if (category == trending) {
                        showInputBoxGradient(false);
                        gifSearchField.searchEditText.setText("");
                        gifLayoutManager.scrollToPositionWithOffset(gifAdapter.trendingSectionItem, -dp(4));
                        categoriesListView.selectCategory(trending);
                        final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
                        if (!gifSearchEmojies.isEmpty()) {
                            gifSearchPreloader.preload(gifSearchEmojies.get(0));
                        }
                        return;
                    }
                    if (categoriesListView.getSelectedCategory() == category) {
                        search(null, false);
                        categoriesListView.selectCategory(null);
                    } else {
                        search(category.emojis, false);
                        categoriesListView.selectCategory(category);
                    }
                });
                box.addView(categoriesListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 36, 0, 0, 0));
            }
        }

        public boolean isCategorySelected() {
            return categoriesListView != null && categoriesListView.getSelectedCategory() != null;
        }

        public void search(String text, boolean delay) {
            if (type == 0) {
                stickersSearchGridAdapter.search(text, delay);
            } else if (type == 1) {
                emojiSearchAdapter.search(text, delay);
            } else if (type == 2) {
                gifSearchAdapter.search(text, delay);
            }
        }

        private boolean inputBoxShown = false;

        private void showInputBoxGradient(boolean show) {
            if (show == inputBoxShown || inputBoxGradient == null) {
                return;
            }
            inputBoxShown = show;
            inputBoxGradient.clearAnimation();
            inputBoxGradient.animate().alpha(show ? 1 : 0).setDuration(120).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }

        public boolean isInProgress() {
            return isprogress;
        }

        private Runnable delayedToggle;
        private void toggleClear(boolean enabled) {
            if (enabled) {
                if (delayedToggle == null) {
                    AndroidUtilities.runOnUIThread(delayedToggle = () -> {
                        AndroidUtilities.updateViewShow(clear, true);
                    }, 340);
                }
            } else {
                if (delayedToggle != null) {
                    AndroidUtilities.cancelRunOnUIThread(delayedToggle);
                    delayedToggle = null;
                }
                AndroidUtilities.updateViewShow(clear, false);
            }
        }

        private boolean isprogress;
        public void showProgress(boolean progress) {
            isprogress = progress;
            if (progress) {
                searchStateDrawable.setIconState(SearchStateDrawable.State.STATE_PROGRESS);
            } else {
                updateButton(true);
            }
        }

        private void updateButton() {
            updateButton(false);
        }

        private void updateButton(boolean force) {
            if (!isInProgress() || searchEditText.length() == 0 && (categoriesListView == null || categoriesListView.getSelectedCategory() == null) || force) {
                boolean backButton = searchEditText.length() > 0 || categoriesListView != null && categoriesListView.isCategoriesShown() && (categoriesListView.isScrolledIntoOccupiedWidth() || categoriesListView.getSelectedCategory() != null);
                searchStateDrawable.setIconState(backButton ? SearchStateDrawable.State.STATE_BACK : SearchStateDrawable.State.STATE_SEARCH);
                isprogress = false;
            }
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
                if (ignoreStickersScroll) {
                    ignoreStickersScroll = false;
                }
                smoothScrolling = false;
            } else {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (ignoreStickersScroll) {
                        ignoreStickersScroll = false;
                    }
                    final SearchField searchField = getSearchFieldForType(type);
                    if (searchField != null) {
                        searchField.hideKeyboard();
                    }
                    smoothScrolling = false;
                }
                if (!smoothScrolling) {
                    stopAnimatingTabsY(type);
                }
                if (type == Type.STICKERS) {
                    if (chooseStickerActionTracker == null) {
                        createStickersChooseActionTracker();
                    }
                    chooseStickerActionTracker.doSomeAction();
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

        public DraggableScrollSlidingTabStrip(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (isDragging()) {
                return super.onInterceptTouchEvent(ev);
            }
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
            if (isDragging()) {
                return super.onTouchEvent(ev);
            }
            if (first) {
                first = false;
                lastX = ev.getX();
            }
            if (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) {
                lastStickersX = ev.getRawX();
            }
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                draggingVertically = draggingHorizontally = false;
                downX = ev.getRawX();
                downY = ev.getRawY();
            } else {
                if (!draggingVertically && !draggingHorizontally && dragListener != null) {
                    if (Math.abs(ev.getRawX() - downX) >= touchSlop && canScrollHorizontally((int) (downX - ev.getRawX()))) {
                        draggingHorizontally = true;
                        AndroidUtilities.cancelRunOnUIThread(checkExpandStickerTabsRunnable);
                        expandStickersByDragg = true;
                        updateStickerTabsPosition();
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
            if (expandStickersByDragg && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
                AndroidUtilities.runOnUIThread(checkExpandStickerTabsRunnable, 1500);
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
                cancelLongPress();
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

    private void sendEmoji(ImageViewEmoji imageViewEmoji, String override) {
        if (imageViewEmoji == null) {
            return;
        }
        if (imageViewEmoji.getSpan() != null) {
//                if (pack != null && pack.set != null && (pack.free || UserConfig.getInstance(currentAccount).isPremium())) {
//                    openEmojiPackAlert(pack.set);
//                    return;
//                }
            if (delegate != null) {
                long documentId = imageViewEmoji.getSpan().documentId;
                TLRPC.Document document = imageViewEmoji.getSpan().document;
                String emoticon = null;
                if (document == null) {
                    for (int i = 0; i < emojipacksProcessed.size(); ++i) {
                        EmojiPack pack = emojipacksProcessed.get(i);
                        for (int j = 0; pack.documents != null && j < pack.documents.size(); ++j) {
                            if (pack.documents.get(j).id == documentId) {
                                document = pack.documents.get(j);
                                break;
                            }
                        }
                    }
                }
                if (document == null) {
                    document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                }
                if (emoticon == null && document != null) {
                    emoticon = MessageObject.findAnimatedEmojiEmoticon(document);
                }
                if (!MessageObject.isFreeEmoji(document) && !UserConfig.getInstance(currentAccount).isPremium() && !(delegate != null && delegate.isUserSelf()) && !allowEmojisForNonPremium) {
                    showBottomTab(false, true);
                    BulletinFactory factory = fragment != null ? BulletinFactory.of(fragment) : BulletinFactory.of(bulletinContainer, resourcesProvider);
                    if (premiumBulletin || fragment == null) {
                        factory.createEmojiBulletin(
                                document,
                                AndroidUtilities.replaceTags(LocaleController.getString("UnlockPremiumEmojiHint", R.string.UnlockPremiumEmojiHint)),
                                LocaleController.getString("PremiumMore", R.string.PremiumMore),
                                EmojiView.this::openPremiumAnimatedEmojiFeature
                        ).show();
                    } else {
                        factory.createSimpleBulletin(
                                R.raw.saved_messages,
                                AndroidUtilities.replaceTags(LocaleController.getString("UnlockPremiumEmojiHint2", R.string.UnlockPremiumEmojiHint2)),
                                LocaleController.getString("Open", R.string.Open),
                                () -> {
                                    Bundle args = new Bundle();
                                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                                    fragment.presentFragment(new ChatActivity(args) {
                                        @Override
                                        public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                                            super.onTransitionAnimationEnd(isOpen, backward);
                                            if (isOpen && chatActivityEnterView != null) {
                                                chatActivityEnterView.showEmojiView();
                                                chatActivityEnterView.postDelayed(() -> {
                                                    if (chatActivityEnterView.getEmojiView() != null) {
                                                        chatActivityEnterView.getEmojiView().scrollEmojisToAnimated();
                                                    }
                                                }, 100);
                                            }
                                        }
                                    });
                                }
                        ).show();
                    }
                    premiumBulletin = !premiumBulletin;
                    return;
                }
                shownBottomTabAfterClick = SystemClock.elapsedRealtime();
                showBottomTab(true, true);
                addEmojiToRecent("animated_" + documentId);
                delegate.onCustomEmojiSelected(documentId, document, emoticon, imageViewEmoji.isRecent);
            }
            return;
        }
        shownBottomTabAfterClick = SystemClock.elapsedRealtime();
        showBottomTab(true, true);
        String code = override != null ? override : (String) imageViewEmoji.getTag();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(code);
        if (override == null) {
            if (!imageViewEmoji.isRecent) {
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

    private boolean premiumBulletin = true;
    public static class ImageViewEmoji extends ImageView {
        public int position;

        public ImageReceiver imageReceiver;
        public AnimatedEmojiDrawable drawable;
        public boolean ignoring;
        private boolean isRecent;
        private AnimatedEmojiSpan span;
        private EmojiPack pack;
        private ImageReceiver.BackgroundThreadDrawHolder[] backgroundThreadDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
        float pressedProgress;
        ValueAnimator backAnimator;

        public ImageViewEmoji(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.CENTER);
            setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
        }

        public void setImageDrawable(Drawable drawable, boolean recent) {
            super.setImageDrawable(drawable);
            isRecent = recent;
        }

        public void setSpan(AnimatedEmojiSpan span) {
            this.span = span;
        }

        public AnimatedEmojiSpan getSpan() {
            return this.span;
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.view.View");
        }

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(5.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 100f;
                pressedProgress = Utilities.clamp(pressedProgress, 1f, 0);
                invalidate();
            }
            float s = 0.8f + 0.2f * (1f - pressedProgress);
            canvas.save();
            canvas.scale(s, s, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            super.onDraw(canvas);
            canvas.restore();
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

        private Drawable[] drawables = new Drawable[6];
        private Drawable backgroundDrawable;
        private Drawable arrowDrawable;
        private String currentEmoji;
        private int arrowX;
        private int selection;
        private Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private AnimatedFloat selectionAnimated = new AnimatedFloat(this, 125, CubicBezierInterpolator.EASE_OUT_QUINT);

        public void setEmoji(String emoji, int arrowPosition) {
            currentEmoji = emoji;
            arrowX = arrowPosition;
            for (int i = 0; i < drawables.length; ++i) {
                String coloredCode = emoji;
                if (i != 0) {
                    String color;
                    switch (i) {
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
                    coloredCode = addColorToCode(emoji, color);
                }
                drawables[i] = Emoji.getEmojiBigDrawable(coloredCode);
            }
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
            Theme.setDrawableColor(backgroundDrawable, getThemedColor(Theme.key_dialogBackground));
            Theme.setDrawableColor(arrowDrawable, getThemedColor(Theme.key_dialogBackground));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 52));
            backgroundDrawable.draw(canvas);

            arrowDrawable.setBounds(arrowX - AndroidUtilities.dp(9), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 55.5f : 47.5f), arrowX + AndroidUtilities.dp(9), AndroidUtilities.dp((AndroidUtilities.isTablet() ? 55.5f : 47.5f) + 8));
            arrowDrawable.draw(canvas);

            float select = selectionAnimated.set(selection);
            float x = emojiSize * select + AndroidUtilities.dp(5 + 4 * select);
            float y = AndroidUtilities.dp(9);
            rect.set(x, y - (int) AndroidUtilities.dpf2(3.5f), x + emojiSize, y + emojiSize + AndroidUtilities.dp(3));
            rectPaint.setColor(getThemedColor(Theme.key_listSelector));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), rectPaint);

            if (currentEmoji != null) {
                for (int a = 0; a < 6; a++) {
                    Drawable drawable = drawables[a];
                    if (drawable != null) {
                        x = emojiSize * a + AndroidUtilities.dp(5 + 4 * a);
                        float scale = .9f + .1f * (1f - Math.min(.5f, Math.abs(a - select)) * 2f);
                        canvas.save();
                        canvas.scale(scale, scale, x + emojiSize / 2f, y + emojiSize / 2f);
                        drawable.setBounds((int) x, (int) y, (int) x + emojiSize, (int) y + emojiSize);
                        drawable.draw(canvas);
                        canvas.restore();
                    }
                }
            }
        }
    }

    public EmojiView(BaseFragment fragment, boolean needAnimatedEmoji, boolean needStickers, boolean needGif, final Context context, boolean needSearch, final TLRPC.ChatFull chatFull, ViewGroup parentView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;
        this.allowAnimatedEmoji = needAnimatedEmoji;
        this.resourcesProvider = resourcesProvider;

        int color = getThemedColor(Theme.key_chat_emojiBottomPanelIcon);
        color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color));

        searchFieldHeight = AndroidUtilities.dp(50);
        needEmojiSearch = needSearch;

        tabIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_smiles, getThemedColor(Theme.key_chat_emojiPanelBackspace), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_gif, getThemedColor(Theme.key_chat_emojiPanelBackspace), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.smiles_tab_stickers, getThemedColor(Theme.key_chat_emojiPanelBackspace), getThemedColor(Theme.key_chat_emojiPanelIconSelected))
        };

        stickerIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.msg_emoji_recent, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.emoji_tabs_faves, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.emoji_tabs_new3, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                new LayerDrawable(new Drawable[]{
                        searchIconDrawable = Theme.createEmojiIconSelectorDrawable(context, R.drawable.emoji_tabs_new1, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                        searchIconDotDrawable = Theme.createEmojiIconSelectorDrawable(context, R.drawable.emoji_tabs_new2, getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine), getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine))
                })
        };

        gifIcons = new Drawable[]{
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.msg_emoji_recent, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
                Theme.createEmojiIconSelectorDrawable(context, R.drawable.stickers_gifs_trending, getThemedColor(Theme.key_chat_emojiPanelIcon), getThemedColor(Theme.key_chat_emojiPanelIconSelected)),
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
        dotPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelNewTrending));

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
        Tab emojiTab = new Tab();
        emojiTab.type = TAB_EMOJI;
        emojiTab.view = emojiContainer;
        allTabs.add(emojiTab);

        if (needAnimatedEmoji) {
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
            animatedEmojiTextColorFilter = new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN);
        }
        emojiGridView = new EmojiGridView(context);
        DefaultItemAnimator emojiItemAnimator = new DefaultItemAnimator();
        emojiItemAnimator.setAddDelay(0);
        emojiItemAnimator.setAddDuration(220);
        emojiItemAnimator.setMoveDuration(220);
        emojiItemAnimator.setChangeDuration(160);
        emojiItemAnimator.setMoveInterpolator(CubicBezierInterpolator.EASE_OUT);
        emojiGridView.setItemAnimator(emojiItemAnimator);
        emojiGridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, emojiGridView, EmojiView.this.getMeasuredHeight(), null, contentPreviewViewerDelegate, resourcesProvider));
        emojiGridView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (view instanceof ImageViewEmoji) {
                    ImageViewEmoji viewEmoji = (ImageViewEmoji) view;

                    if (viewEmoji.isRecent) {
                        RecyclerListView.ViewHolder holder = emojiGridView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() <= getRecentEmoji().size()) {
                            delegate.onClearEmojiRecent();
                        }
                        emojiGridView.clearTouchesFor(view);
                        return true;
                    } else if (viewEmoji.getSpan() == null) {
                        String code = (String) viewEmoji.getTag();
                        if (code == null) {
                            return false;
                        }

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
                            emojiGridView.clearTouchesFor(view);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        emojiGridView.setInstantClick(true);
        emojiGridView.setLayoutManager(emojiLayoutManager = new GridLayoutManager(context, 8) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                try {
                    LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                        @Override
                        public void onEnd() {
                            emojiSmoothScrolling = false;
                        }
                    };
                    linearSmoothScroller.setTargetPosition(position);
                    startSmoothScroll(linearSmoothScroller);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        emojiGridView.setTopGlowOffset(AndroidUtilities.dp(38));
        emojiGridView.setBottomGlowOffset(AndroidUtilities.dp(36));
        emojiGridView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(36), AndroidUtilities.dp(5), AndroidUtilities.dp(44));
        emojiGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        emojiGridView.setSelectorDrawableColor(0);
        emojiGridView.setClipToPadding(false);
        emojiLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (emojiGridView.getAdapter() == emojiSearchAdapter) {
                    if (
                        position == 0 ||
                        position == 1 && emojiSearchAdapter.searchWas && emojiSearchAdapter.result.isEmpty()
                    ) {
                        return emojiLayoutManager.getSpanCount();
                    }
                } else {
                    if (
                        needEmojiSearch && position == 0 ||
                        position == emojiAdapter.trendingRow ||
                        position == emojiAdapter.trendingHeaderRow ||
                        position == emojiAdapter.recentlyUsedHeaderRow ||
                        emojiAdapter.positionToSection.indexOfKey(position) >= 0 ||
                        emojiAdapter.positionToUnlock.indexOfKey(position) >= 0
                    ) {
                        return emojiLayoutManager.getSpanCount();
                    }
                }
                return 1;
            }

            @Override
            public int getSpanGroupIndex(int adapterPosition, int spanCount) {
                return super.getSpanGroupIndex(adapterPosition, spanCount);
            }
        });
        emojiGridView.setAdapter(emojiAdapter = new EmojiGridAdapter());
        emojiGridView.addItemDecoration(new EmojiGridSpacing());
        emojiSearchAdapter = new EmojiSearchAdapter();
        emojiContainer.addView(emojiGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emojiScrollHelper = new RecyclerAnimationScrollHelper(emojiGridView, emojiLayoutManager);
        emojiScrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {

            @Override
            public void onPreAnimation() {
                emojiGridView.updateEmojiDrawables();
                emojiSmoothScrolling = true;
            }

            @Override
            public void onEndAnimation() {
                emojiSmoothScrolling = false;
                emojiGridView.updateEmojiDrawables();
            }

            @Override
            public void ignoreView(View view, boolean ignore) {
                if (view instanceof ImageViewEmoji) {
                    ((ImageViewEmoji)view).ignoring = ignore;
                }
            }
        });
        emojiGridView.setOnScrollListener(new TypedScrollListener(Type.EMOJIS) {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateEmojiTabsPosition();
                super.onScrolled(recyclerView, dx, dy);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    emojiSmoothScrolling = false;
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });

        emojiTabs = new EmojiTabsStrip(context, resourcesProvider, true, needAnimatedEmoji, 0, fragment != null ? () -> {
            if (delegate != null) {
                delegate.onEmojiSettingsClick(emojiAdapter.frozenEmojiPacks);
            }
        } : null) {
            @Override
            protected boolean isInstalled(EmojiPack pack) {
                return pack.installed || installedEmojiSets.contains(pack.set.id);
            }

            @Override
            protected boolean allowEmojisForNonPremium() {
                return allowEmojisForNonPremium;
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (emojiTabsShadow != null) {
                    emojiTabsShadow.setTranslationY(translationY);
                }
            }

            @Override
            protected boolean doIncludeFeatured() {
                return !(featuredEmojiSets.size() > 0 && featuredEmojiSets.get(0).set != null && MessagesController.getEmojiSettings(currentAccount).getLong("emoji_featured_hidden", 0) != featuredEmojiSets.get(0).set.id && UserConfig.getInstance(UserConfig.selectedAccount).isPremium());
            }

            @Override
            protected boolean onTabClick(int index) {
                if (emojiSmoothScrolling) {
                    return false;
                }
                if (emojiSearchAdapter != null) {
                    emojiSearchAdapter.search(null);
                }
                if (emojiSearchField != null && emojiSearchField.categoriesListView != null) {
                    emojiSearchField.categoriesListView.selectCategory(null);
                }
                Integer position = null;
                int offset = 0;
                if (index == 0) {
                    position = needEmojiSearch ? 1 : 0;
                } else {
                    index--;
                }
                if (position == null) {
                    if (index < EmojiData.dataColored.length && emojiAdapter.sectionToPosition.indexOfKey(index) >= 0) {
                        position = emojiAdapter.sectionToPosition.get(index);
                    }
                }
                if (position == null) {
                    ArrayList<EmojiPack> packs = getEmojipacks();
                    int i = index - EmojiData.dataColored.length;
                    if (packs != null && i >= 0 && i < packs.size()) {
                        int I = -1;
                        for (int j = 0; j < emojipacksProcessed.size(); ++j) {
                            if (emojipacksProcessed.get(j).set.id == packs.get(i).set.id) {
                                I = j;
                                break;
                            }
                        }
                        position = emojiAdapter.sectionToPosition.get(I + EmojiData.dataColored.length);
//                        if (I >= 0 && I < packs.size() && packs.get(I).featured) {
                            offset = AndroidUtilities.dp(-9);
//                        } else {
//                            offset = AndroidUtilities.dp(-2);
//                        }
                    }
                }
                if (position != null) {
                    emojiGridView.stopScroll();
                    updateEmojiTabsPosition(position);
                    scrollEmojisToPosition(position, offset);
                    checkEmojiTabY(null, 0);
                }
                return true;
            }

            @Override
            protected ColorFilter getEmojiColorFilter() {
                return animatedEmojiTextColorFilter;
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

        emojiTabs.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        emojiAdapter.processEmoji(true);
        emojiTabs.updateEmojiPacks(getEmojipacks());
        emojiContainer.addView(emojiTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));

        emojiTabsShadow = new View(context);
        emojiTabsShadow.setAlpha(0.0f);
        emojiTabsShadow.setTag(1);
        emojiTabsShadow.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        layoutParams.topMargin = AndroidUtilities.dp(36);
        emojiContainer.addView(emojiTabsShadow, layoutParams);

        if (needStickers) {
            if (needGif) {
                gifContainer = new FrameLayout(context);
                Tab gifTab = new Tab();
                gifTab.type = TAB_GIFS;
                gifTab.view = gifContainer;
                allTabs.add(gifTab);

                gifGridView = new RecyclerListView(context) {

                    private boolean ignoreLayout;
                    private boolean wasMeasured;

                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent event) {
                        boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gifGridView, 0, contentPreviewViewerDelegate, resourcesProvider);
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
                            gifLayoutManager.scrollToPositionWithOffset(0, 0);
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

                        if (position != 0 || !gifAdapter.addSearch) {
                            outRect.left = 0;
                            outRect.bottom = 0;
                            outRect.top = AndroidUtilities.dp(2);
                            outRect.right = gifLayoutManager.isLastInRow(position - (gifAdapter.addSearch ? 1 : 0)) ? 0 : AndroidUtilities.dp(2);
                        } else {
                            outRect.set(0, 0, 0, 0);
                        }
                    }
                });
                gifGridView.setPadding(0, searchFieldHeight, 0, AndroidUtilities.dp(44));
                gifGridView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
                ((SimpleItemAnimator) gifGridView.getItemAnimator()).setSupportsChangeAnimations(false);
                gifGridView.setAdapter(gifAdapter = new GifAdapter(context, true));
                gifSearchAdapter = new GifAdapter(context);
                gifGridView.setOnScrollListener(new TypedScrollListener(Type.GIFS));
                gifGridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gifGridView, 0, gifOnItemClickListener, contentPreviewViewerDelegate, resourcesProvider));
                gifOnItemClickListener = (view, position) -> {
                    if (delegate == null) {
                        return;
                    }
                    if (gifAdapter.addSearch) {
                        position--;
                    }
                    if (gifGridView.getAdapter() == gifAdapter) {
                        if (position < 0) {
                            return;
                        }
                        if (position < gifAdapter.recentItemsCount) {
                            delegate.onGifSelected(view, recentGifs.get(position), null, "gif", true, 0);
                        } else {
                            int resultPos = position;
                            if (gifAdapter.recentItemsCount > 0) {
                                resultPos -= gifAdapter.recentItemsCount;
                                resultPos--; // trending section item
                            }
                            if (resultPos >= 0 && resultPos < gifAdapter.results.size()) {
                                delegate.onGifSelected(view, gifAdapter.results.get(resultPos), null, gifAdapter.bot, true, 0);
                            }
                        }
                    } else if (gifGridView.getAdapter() == gifSearchAdapter) {
                        if (position < 0 || position >= gifSearchAdapter.results.size()) {
                            return;
                        }
                        delegate.onGifSelected(view, gifSearchAdapter.results.get(position), gifSearchAdapter.lastSearchImageString, gifSearchAdapter.bot, true, 0);
                        updateRecentGifs();
                    }
                };

                gifGridView.setOnItemClickListener(gifOnItemClickListener);
                gifContainer.addView(gifGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                gifSearchField = new SearchField(context, 2);
//                gifSearchField.setVisibility(INVISIBLE);
                gifContainer.addView(gifSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));

                gifTabs = new DraggableScrollSlidingTabStrip(context, resourcesProvider);
                gifTabs.setType(ScrollSlidingTabStrip.Type.TAB);
                gifTabs.setUnderlineHeight(AndroidUtilities.getShadowHeight());
                gifTabs.setIndicatorColor(getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
                gifTabs.setUnderlineColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
                gifTabs.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
//                gifContainer.addView(gifTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, StickerTabView.SMALL_HEIGHT, Gravity.LEFT | Gravity.TOP));
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

            stickersContainer = new FrameLayout(context) {

                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    stickersContainerAttached = true;
                    updateStickerTabsPosition();
                    if (chooseStickerActionTracker != null) {
                        chooseStickerActionTracker.checkVisibility();
                    }
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    stickersContainerAttached = false;
                    updateStickerTabsPosition();
                    if (chooseStickerActionTracker != null) {
                        chooseStickerActionTracker.checkVisibility();
                    }
                }
            };

            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
            MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
            stickersGridView = new RecyclerListViewWithOverlayDraw(context) {

                boolean ignoreLayout;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, stickersGridView, EmojiView.this.getMeasuredHeight(), contentPreviewViewerDelegate, resourcesProvider);
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
                        stickersLayoutManager.scrollToPositionWithOffset(0, 0);
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

                @Override
                public void onScrolled(int dx, int dy) {
                    super.onScrolled(dx, dy);
                    if (stickersTabContainer != null) {
                        stickersTab.setUnderlineHeight(stickersGridView.canScrollVertically(-1) ? AndroidUtilities.getShadowHeight() : 0);
                    }
                }
            };
            stickersGridView.setLayoutManager(stickersLayoutManager = new GridLayoutManager(context, 5) {
                @Override
                public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                    try {
                        LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP);
                        linearSmoothScroller.setTargetPosition(position);
                        startSmoothScroll(linearSmoothScroller);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                @Override
                public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                    int i = super.scrollVerticallyBy(dy, recycler, state);
                    if (i != 0 && stickersGridView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                        expandStickersByDragg = false;
                        updateStickerTabsPosition();
                    }
                    if (chooseStickerActionTracker == null) {
                        createStickersChooseActionTracker();
                    }
                    chooseStickerActionTracker.doSomeAction();
                    return i;
                }
            });
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
            stickersGridView.setPadding(0, AndroidUtilities.dp(36), 0, AndroidUtilities.dp(44));
            stickersGridView.setClipToPadding(false);

            Tab stickersTabHolder = new Tab();
            stickersTabHolder.type = TAB_STICKERS;
            stickersTabHolder.view = stickersContainer;
            allTabs.add(stickersTabHolder);
            stickersSearchGridAdapter = new StickersSearchGridAdapter(context);
            stickersGridView.setAdapter(stickersGridAdapter = new StickersGridAdapter(context));
            stickersGridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, stickersGridView, EmojiView.this.getMeasuredHeight(), stickersOnItemClickListener, contentPreviewViewerDelegate, resourcesProvider));
            stickersOnItemClickListener = (view, position) -> {
                String query = null;
                if (stickersGridView.getAdapter() == stickersSearchGridAdapter) {
                    query = stickersSearchGridAdapter.searchQuery;
                    TLRPC.StickerSetCovered pack = stickersSearchGridAdapter.positionsToSets.get(position);
                    if (pack != null) {
                        delegate.onShowStickerSet(pack.set, null);
                        return;
                    }
                }
                if (!(view instanceof StickerEmojiCell)) {
                    return;
                }
                StickerEmojiCell cell = (StickerEmojiCell) view;
                if (cell.getSticker() != null && MessageObject.isPremiumSticker(cell.getSticker()) && !AccountInstance.getInstance(currentAccount).getUserConfig().isPremium()) {
                    ContentPreviewViewer.getInstance().showMenuFor(cell);
                    return;
                }
                ContentPreviewViewer.getInstance().reset();

                if (cell.isDisabled()) {
                    return;
                }
                cell.disable();
                delegate.onStickerSelected(cell, cell.getSticker(), query, cell.getParentObject(), cell.getSendAnimationData(), true, 0);
            };
            stickersGridView.setOnItemClickListener(stickersOnItemClickListener);
            stickersGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            stickersContainer.addView(stickersGridView);
            stickersScrollHelper = new RecyclerAnimationScrollHelper(stickersGridView, stickersLayoutManager);

            stickersSearchField = new SearchField(context, 0);
            stickersContainer.addView(stickersSearchField, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight + AndroidUtilities.getShadowHeight()));

            stickersTab = new DraggableScrollSlidingTabStrip(context, resourcesProvider) {
                @Override
                protected void updatePosition() {
                    updateStickerTabsPosition();
                    stickersTabContainer.invalidate();
                    invalidate();
                    if (delegate != null) {
                        delegate.invalidateEnterView();
                    }
                }

                @Override
                protected void stickerSetPositionChanged(int fromPosition, int toPosition) {
                    int index1 = fromPosition - stickersTabOffset;
                    int index2 = toPosition - stickersTabOffset;

                    final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

                    swapListElements(stickerSets, index1, index2);
                    Collections.sort(mediaDataController.getStickerSets(MediaDataController.TYPE_IMAGE), (o1, o2) -> {
                        int i1 = stickerSets.indexOf(o1);
                        int i2 = stickerSets.indexOf(o2);
                        if (i1 >= 0 && i2 >= 0) {
                            return i1 - i2;
                        }
                        return 0;
                    });
                    if (frozenStickerSets != null) {
                        frozenStickerSets.clear();
                        frozenStickerSets.addAll(stickerSets);
                    }

                    reloadStickersAdapter();
                    AndroidUtilities.cancelRunOnUIThread(checkExpandStickerTabsRunnable);
                    AndroidUtilities.runOnUIThread(checkExpandStickerTabsRunnable, 1500);
                    sendReorder();
                    updateStickerTabs(true);

                    if (SharedConfig.updateStickersOrderOnSend) {
                        SharedConfig.toggleUpdateStickersOrderOnSend();
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createSimpleBulletin(
                                R.raw.filter_reorder,
                                LocaleController.getString("DynamicPackOrderOff", R.string.DynamicPackOrderOff),
                                LocaleController.getString("DynamicPackOrderOffInfo", R.string.DynamicPackOrderOffInfo),
                                LocaleController.getString("Settings"),
                                () -> fragment.presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE, null))
                            ).show();
                        } else if (bulletinContainer != null) {
                            BulletinFactory.of(bulletinContainer, EmojiView.this.resourcesProvider).createSimpleBulletin(R.raw.filter_reorder, LocaleController.getString("DynamicPackOrderOff", R.string.DynamicPackOrderOff), LocaleController.getString("DynamicPackOrderOffInfo", R.string.DynamicPackOrderOffInfo)).show();
                        } else {
                            return;
                        }
                    }
                }

                private void swapListElements(List<TLRPC.TL_messages_stickerSet> list, int index1, int index2) {
                    final TLRPC.TL_messages_stickerSet set1 = list.remove(index1);
                    list.add(index2, set1);
                }

                private void sendReorder() {
                    MediaDataController.getInstance(currentAccount).calcNewHash(MediaDataController.TYPE_IMAGE);
                    TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
                    req.masks = false;
                    req.emojis = false;
                    for (int a = hasChatStickers ? 1 : 0; a < stickerSets.size(); a++) {
                        req.order.add(stickerSets.get(a).set.id);
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> { });
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoad, MediaDataController.TYPE_IMAGE, true);
                }

                @Override
                protected void invalidateOverlays() {
                    stickersTabContainer.invalidate();
                }
            };
            stickersTab.setDragEnabled(true);
            stickersTab.setWillNotDraw(false);
            stickersTab.setType(ScrollSlidingTabStrip.Type.TAB);
            stickersTab.setUnderlineHeight(stickersGridView.canScrollVertically(-1) ? AndroidUtilities.getShadowHeight() : 0);

            stickersTab.setIndicatorColor(getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            stickersTab.setUnderlineColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            if (parentView != null) {
                stickersTabContainer = new FrameLayout(context) {

                    Paint paint = new Paint();
                    @Override
                    protected void dispatchDraw(Canvas canvas) {
                        float searchProgress = delegate.getProgressToSearchOpened();
                        float searchProgressOffset = AndroidUtilities.dp(50) * searchProgress;
                        if (searchProgressOffset > getMeasuredHeight()) {
                            return;
                        }
                        canvas.save();
                        if (searchProgressOffset != 0) {
                            canvas.clipRect(0, searchProgressOffset, getMeasuredWidth(), getMeasuredHeight());
                        }
                        paint.setColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                        canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(36) + stickersTab.getExpandedOffset(), paint);
                        super.dispatchDraw(canvas);
                        stickersTab.drawOverlays(canvas);
                        canvas.restore();
                    }

                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        super.onLayout(changed, left, top, right, bottom);
                        updateStickerTabsPosition();
                    }
                };
                stickersTabContainer.addView(stickersTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP));
                parentView.addView(stickersTabContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            } else {
                stickersContainer.addView(stickersTab, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP));
            }
            updateStickerTabs(true);
            stickersTab.setDelegate(page -> {
                if (firstTabUpdate) {
                    return;
                }
                if (page == trendingTabNum) {
                    openTrendingStickers(null);
                    return;
                }

                if (stickersSearchField != null && stickersSearchField.isCategorySelected()) {
                    stickersSearchField.search(null, false);
                    stickersSearchField.categoriesListView.selectCategory(null);
                }

                if (page == recentTabNum) {
                    stickersGridView.stopScroll();
                    scrollStickersToPosition(stickersGridAdapter.getPositionForPack("recent"), 0);
                    resetTabsY(Type.STICKERS);
                    stickersTab.onPageScrolled(recentTabNum, recentTabNum > 0 ? recentTabNum : stickersTabOffset);
                    return;
                } else if (page == favTabNum) {
                    stickersGridView.stopScroll();
                    scrollStickersToPosition(stickersGridAdapter.getPositionForPack("fav"), 0);
                    resetTabsY(Type.STICKERS);
                    stickersTab.onPageScrolled(favTabNum, favTabNum > 0 ? favTabNum : stickersTabOffset);
                    return;
                } else if (page == premiumTabNum) {
                    stickersGridView.stopScroll();
                    scrollStickersToPosition(stickersGridAdapter.getPositionForPack("premium"), 0);
                    resetTabsY(Type.STICKERS);
                    stickersTab.onPageScrolled(premiumTabNum, premiumTabNum > 0 ? premiumTabNum : stickersTabOffset);
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
                scrollStickersToPosition(stickersGridAdapter.getPositionForPack(stickerSets.get(index)), 0);
                resetTabsY(Type.STICKERS);
                checkScroll(Type.STICKERS);
                int firstTab;
                if (favTabNum > 0) {
                    firstTab = favTabNum;
                } else if (recentTabNum > 0) {
                    firstTab = recentTabNum;
                } else {
                    firstTab = stickersTabOffset;
                }
                stickersTab.onPageScrolled(page, firstTab);
                expandStickersByDragg = false;
                updateStickerTabsPosition();
            });

            stickersGridView.setOnScrollListener(new TypedScrollListener(Type.STICKERS));
        }

        currentTabs.clear();
        currentTabs.addAll(allTabs);

        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ignorePagerScroll) {
                    return false;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                }
                try {
                    return super.onInterceptTouchEvent(ev);
                } catch (IllegalArgumentException ignore) {
                }
                return false;
            }

            @Override
            public void setCurrentItem(int item, boolean smoothScroll) {
                startStopVisibleGifs(item == 1);
                if (item == getCurrentItem()) {
                    if (item == 0) {
                        tabsMinusDy[Type.EMOJIS] = 0;
                        ObjectAnimator animator = ObjectAnimator.ofFloat(emojiTabs, TRANSLATION_Y, 0);
                        animator.setDuration(150);
                        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                        animator.start();
                        scrollEmojisToPosition(1, 0);
                        if (emojiTabs != null) {
                            emojiTabs.select(0);
                        }
                    } else if (item == 1) {
                        gifGridView.smoothScrollToPosition(0);
                    } else {
                        stickersGridView.smoothScrollToPosition(1);
                    }
                    return;
                }
                super.setCurrentItem(item, smoothScroll);
            }
        };
        pager.setAdapter(emojiPagerAdapter = new EmojiPagesAdapter());

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
        backspaceButton.setHapticFeedbackEnabled(true);
        backspaceButton.setImageResource(R.drawable.smiles_tab_clear);
        backspaceButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        backspaceButton.setScaleType(ImageView.ScaleType.CENTER);
        backspaceButton.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
        backspaceButton.setFocusable(true);
        backspaceButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        bulletinContainer = new FrameLayout(context);
        if (needSearch) {
            addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 40 + AndroidUtilities.getShadowHeight() / AndroidUtilities.density));
        } else {
            addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        }

        bottomTabContainer = new FrameLayout(context);
        bottomTabContainer.setClickable(true);

        shadowLine = new View(context);
        shadowLine.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        bottomTabContainer.addView(shadowLine, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight()));

        bottomTabContainerBackground = new View(context);
        bottomTabContainer.addView(bottomTabContainerBackground, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40), Gravity.LEFT | Gravity.BOTTOM));

        if (needSearch) {
            addView(bottomTabContainer, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, AndroidUtilities.dp(40) + AndroidUtilities.getShadowHeight(), Gravity.LEFT | Gravity.BOTTOM));
            bottomTabContainer.addView(backspaceButton, LayoutHelper.createFrame(47, 40, Gravity.BOTTOM | Gravity.RIGHT));
            if (Build.VERSION.SDK_INT >= 21) {
                backspaceButton.setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
            }

            stickerSettingsButton = new ImageView(context);
            stickerSettingsButton.setImageResource(R.drawable.smiles_tab_settings);
            stickerSettingsButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            stickerSettingsButton.setScaleType(ImageView.ScaleType.CENTER);
            stickerSettingsButton.setFocusable(true);
            if (Build.VERSION.SDK_INT >= 21) {
                stickerSettingsButton.setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
            }
            stickerSettingsButton.setContentDescription(LocaleController.getString("Settings", R.string.Settings));
            bottomTabContainer.addView(stickerSettingsButton, LayoutHelper.createFrame(47, 40, Gravity.BOTTOM | Gravity.RIGHT));
            stickerSettingsButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (delegate != null) {
                        delegate.onStickersSettingsClick();
                    }
                }
            });

            typeTabs = new PagerSlidingTabStrip(context, resourcesProvider);
            typeTabs.setViewPager(pager);
            typeTabs.setShouldExpand(false);
            typeTabs.setIndicatorHeight(AndroidUtilities.dp(3));
            typeTabs.setIndicatorColor(getThemedColor(Theme.key_chat_emojiPanelIconSelected));
            typeTabs.setUnderlineHeight(0);
            typeTabs.setTabPaddingLeftRight(AndroidUtilities.dp(13));
            bottomTabContainer.addView(typeTabs, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
            typeTabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    checkGridVisibility(position, positionOffset);
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
                    updateStickerTabsPosition();
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
            searchButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            searchButton.setScaleType(ImageView.ScaleType.CENTER);
            searchButton.setContentDescription(LocaleController.getString("Search", R.string.Search));
            searchButton.setFocusable(true);
            searchButton.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= 21) {
                searchButton.setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18)));
            }
            bottomTabContainer.addView(searchButton, LayoutHelper.createFrame(47, 40, Gravity.BOTTOM | Gravity.LEFT));
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
            addView(bottomTabContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 40 : 44) + 16, (Build.VERSION.SDK_INT >= 21 ? 40 : 44) + 8, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 0, 0, 2, 0));

            Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), getThemedColor(Theme.key_chat_emojiPanelBackground), getThemedColor(Theme.key_chat_emojiPanelBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(36), AndroidUtilities.dp(36));
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
                        outline.setOval(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    }
                });
            }
            backspaceButton.setPadding(0, 0, AndroidUtilities.dp(2), 0);
            backspaceButton.setBackground(drawable);
            backspaceButton.setContentDescription(LocaleController.getString("AccDescrBackspace", R.string.AccDescrBackspace));
            backspaceButton.setFocusable(true);
            bottomTabContainer.addView(backspaceButton, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 40 : 44) - 4, (Build.VERSION.SDK_INT >= 21 ? 40 : 44) - 4, Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));
            shadowLine.setVisibility(GONE);
            bottomTabContainerBackground.setVisibility(GONE);
        }

        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mediaBanTooltip = new CorrectlyMeasuringTextView(context);
        mediaBanTooltip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_chat_gifSaveHintBackground)));
        mediaBanTooltip.setTextColor(getThemedColor(Theme.key_chat_gifSaveHintText));
        mediaBanTooltip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
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

        setAllow(needStickers, needGif, false);
    }

    private View animateExpandFromButton;
    private int animateExpandFromPosition = -1, animateExpandToPosition = -1;
    private long animateExpandStartTime = -1;
    class EmojiGridView extends RecyclerListView {
        public EmojiGridView(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, this, 0, contentPreviewViewerDelegate, resourcesProvider);
            return super.onInterceptTouchEvent(event) || result;
        }

        private boolean ignoreLayout;

        SparseArray<ArrayList<ImageViewEmoji>> viewsGroupedByLines = new SparseArray<>();
        ArrayList<DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
        ArrayList<ArrayList<ImageViewEmoji>> unusedArrays = new ArrayList<>();
        ArrayList<DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();

        private AnimatedEmojiSpan[] getAnimatedEmojiSpans() {
            AnimatedEmojiSpan[] spans = new AnimatedEmojiSpan[emojiGridView.getChildCount()];
            for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
                View child = emojiGridView.getChildAt(i);
                if (child instanceof ImageViewEmoji) {
                    spans[i] = ((ImageViewEmoji) child).getSpan();
                }
            }
            return spans;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            ignoreLayout = true;
            int width = MeasureSpec.getSize(widthSpec);
            int wasSpanCount = emojiLayoutManager.getSpanCount();
            emojiLayoutManager.setSpanCount(Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45)));
            ignoreLayout = false;
            super.onMeasure(widthSpec, heightSpec);
            if (wasSpanCount != emojiLayoutManager.getSpanCount()) {
                emojiAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (needEmojiSearch && firstEmojiAttach) {
                ignoreLayout = true;
                emojiLayoutManager.scrollToPositionWithOffset(0, 0);
                firstEmojiAttach = false;
                ignoreLayout = false;
            }
            super.onLayout(changed, l, t, r, b);
            checkEmojiSearchFieldScroll(true);
            updateEmojiDrawables();
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
                            sendEmoji(emojiTouchedView, null);
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}

                            Emoji.saveEmojiColors();
                        } else {
                            code = code.replace("\uD83C\uDFFB", "")
                                    .replace("\uD83C\uDFFC", "")
                                    .replace("\uD83C\uDFFD", "")
                                    .replace("\uD83C\uDFFE", "")
                                    .replace("\uD83C\uDFFF", "");
                            if (color != null) {
                                sendEmoji(emojiTouchedView, addColorToCode(code, color));
                            } else {
                                sendEmoji(emojiTouchedView, code);
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
                        if (pickerView.getSelection() != position) {
                            try {
                                performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignoreException) {}
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

        public void updateEmojiDrawables() {
            animatedEmojiDrawables = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, this, getAnimatedEmojiSpans(), animatedEmojiDrawables);
        }

        @Override
        public void onScrollStateChanged(int state) {
            super.onScrollStateChanged(state);
            if (state == SCROLL_STATE_IDLE) {
                if (!canScrollVertically(-1) || !canScrollVertically(1)) {
                    showBottomTab(true, true);
                }
                if (!canScrollVertically(1)) {
                    checkTabsY(Type.EMOJIS, AndroidUtilities.dp(36));
                }
            }
        }

        private int lastChildCount = -1;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            if (lastChildCount != getChildCount()) {
                updateEmojiDrawables();
                lastChildCount = getChildCount();
            }
//            drawDashedOutlines(canvas);

            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                arrayList.clear();
                unusedArrays.add(arrayList);
            }
            viewsGroupedByLines.clear();
            final boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
            final boolean drawButton = animatedExpandIn && animateExpandFromButton != null && animateExpandFromPosition >= 0;
            if (animatedEmojiDrawables != null && emojiGridView != null) {
                for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
                    View child = emojiGridView.getChildAt(i);
                    if (child instanceof ImageViewEmoji) {
                        int top = child.getTop() + (int) child.getTranslationY();
                        ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.get(top);
                        if (arrayList == null) {
                            if (!unusedArrays.isEmpty()) {
                                arrayList = unusedArrays.remove(unusedArrays.size() - 1);
                            } else {
                                arrayList = new ArrayList<>();
                            }
                            viewsGroupedByLines.put(top, arrayList);
                        }
                        arrayList.add((ImageViewEmoji) child);
                    }
                    if (drawButton && child != null) {
                        int position = getChildAdapterPosition(child);
                        if (position == animateExpandFromPosition - 1) {
                            float t = CubicBezierInterpolator.EASE_OUT.getInterpolation(MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / 140f, 0, 1));
                            if (t < 1) {
                                canvas.saveLayerAlpha(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), (int) (255 * (1f - t)), Canvas.ALL_SAVE_FLAG);
                                canvas.translate(child.getLeft(), child.getTop());
                                final float scale = .5f + .5f * (1f - t);
                                canvas.scale(scale, scale, child.getWidth() / 2f, child.getHeight() / 2f);
                                animateExpandFromButton.draw(canvas);
                                canvas.restore();
                            }
                        }
                    }
                }
            }

            lineDrawablesTmp.clear();
            lineDrawablesTmp.addAll(lineDrawables);
            lineDrawables.clear();

            long time = System.currentTimeMillis();
            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<ImageViewEmoji> arrayList = viewsGroupedByLines.valueAt(i);
                ImageViewEmoji firstView = arrayList.get(0);
                int position = firstView.position;
                DrawingInBackgroundLine drawable = null;
                for (int k = 0; k < lineDrawablesTmp.size(); k++) {
                    if (lineDrawablesTmp.get(k).position == position) {
                        drawable = lineDrawablesTmp.get(k);
                        lineDrawablesTmp.remove(k);
                        break;
                    }
                }
                if (drawable == null) {
                    if (!unusedLineDrawables.isEmpty()) {
                        drawable = unusedLineDrawables.remove(unusedLineDrawables.size() - 1);
                    } else {
                        drawable = new DrawingInBackgroundLine();
                    }
                    drawable.position = position;
                    drawable.onAttachToWindow();
                }
                lineDrawables.add(drawable);
                drawable.imageViewEmojis = arrayList;
                canvas.save();
                canvas.translate(firstView.getLeft(), firstView.getY() + firstView.getPaddingTop());
                drawable.startOffset = firstView.getLeft();
                int w = getMeasuredWidth() - firstView.getLeft() * 2;
                int h = firstView.getMeasuredHeight() - firstView.getPaddingBottom();
                if (w > 0 && h > 0) {
                    drawable.draw(canvas, time, w, h, 1f);
                }
                canvas.restore();
            }

            for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                if (unusedLineDrawables.size() < 3) {
                    unusedLineDrawables.add(lineDrawablesTmp.get(i));
                    lineDrawablesTmp.get(i).imageViewEmojis = null;
                    lineDrawablesTmp.get(i).reset();
                } else {
                    lineDrawablesTmp.get(i).onDetachFromWindow();
                }
            }
            lineDrawablesTmp.clear();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateEmojiDrawables();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            AnimatedEmojiSpan.release(this, animatedEmojiDrawables);
            for (int i = 0; i < lineDrawables.size(); i++) {
                lineDrawables.get(i).onDetachFromWindow();
            }
            for (int i = 0; i < unusedLineDrawables.size(); i++) {
                unusedLineDrawables.get(i).onDetachFromWindow();
            }
            unusedLineDrawables.addAll(lineDrawables);
            lineDrawables.clear();
        }

        private SparseArray<TouchDownInfo> touches;
        class TouchDownInfo {
            float x, y;
            long time;
            View view;
        }
        public void clearTouchesFor(View view) {
            if (touches != null) {
                for (int i = 0; i < touches.size(); i++) {
                    TouchDownInfo touch = touches.valueAt(i);
                    if (touch.view == view) {
                        touches.removeAt(i);
                        i--;
                        if (touch != null) {
                            if (touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                                touch.view.getBackground().setState(new int[]{});
                            }
                            if (touch.view != null) {
                                touch.view.setPressed(false);
                            }
                        }
                    }
                }
            }
        }
        public void clearAllTouches() {
            if (touches != null) {
                for (int i = 0; i < touches.size(); i++) {
                    TouchDownInfo touch = touches.valueAt(i);
                    touches.removeAt(i);
                    i--;
                    if (touch != null) {
                        if (touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                            touch.view.getBackground().setState(new int[]{});
                        }
                        if (touch.view != null) {
                            touch.view.setPressed(false);
                        }
                    }
                }
            }
        }

        public long animateExpandDuration() {
            return animateExpandAppearDuration() + animateExpandCrossfadeDuration() + 150;
        }

        public long animateExpandAppearDuration() {
            int count = animateExpandToPosition - animateExpandFromPosition;
            return Math.max(600, Math.min(55, count) * 40L);
        }

        public long animateExpandCrossfadeDuration() {
            int count = animateExpandToPosition - animateExpandFromPosition;
            return Math.max(400, Math.min(45, count) * 35L);
        }

        class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {
            public int position;
            public int startOffset;
            ArrayList<ImageViewEmoji> imageViewEmojis;
            ArrayList<ImageViewEmoji> drawInBackgroundViews = new ArrayList<>();

            @Override
            public void draw(Canvas canvas, long time, int w, int h, float alpha) {
                if (imageViewEmojis == null) {
                    return;
                }
                boolean drawInUi = imageViewEmojis.size() <= 4 || SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW || !LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD);
                if (!drawInUi) {
                    boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        ImageViewEmoji img = imageViewEmojis.get(i);
                        if (img.pressedProgress != 0 || img.backAnimator != null || (img.position > animateExpandFromPosition && img.position < animateExpandToPosition && animatedExpandIn)) {
                            drawInUi = true;
                            break;
                        }
                    }
                }
                if (drawInUi) {
                    prepareDraw(System.currentTimeMillis());
                    drawInUiThread(canvas, alpha);
                    reset();
                } else {
                    super.draw(canvas, time, w, h, alpha);
                }
            }

            @Override
            public void prepareDraw(long time) {
                drawInBackgroundViews.clear();
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    ImageViewEmoji imageView = imageViewEmojis.get(i);
                    AnimatedEmojiSpan span = imageView.getSpan();
                    if (span == null) {
                        continue;
                    }
                    AnimatedEmojiDrawable drawable = animatedEmojiDrawables.get(imageView.span.getDocumentId());
                    if (drawable == null || drawable.getImageReceiver() == null) {
                        continue;
                    }

                    drawable.update(time);
                    imageView.backgroundThreadDrawHolder[threadIndex] = drawable.getImageReceiver().setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder[threadIndex], threadIndex);
                    imageView.backgroundThreadDrawHolder[threadIndex].time = time;
                    imageView.backgroundThreadDrawHolder[threadIndex].overrideAlpha = 1f;
                    drawable.setAlpha(255);
                    int topOffset = (int) (imageView.getHeight() * .03f);
                    AndroidUtilities.rectTmp2.set(imageView.getLeft() + imageView.getPaddingLeft() - startOffset, topOffset, imageView.getRight() - imageView.getPaddingRight() - startOffset, topOffset + imageView.getMeasuredHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom());
                    imageView.backgroundThreadDrawHolder[threadIndex].setBounds(AndroidUtilities.rectTmp2);
                    imageView.drawable = drawable;
                    imageView.drawable.setColorFilter(animatedEmojiTextColorFilter);
                    imageView.imageReceiver = drawable.getImageReceiver();
                    drawInBackgroundViews.add(imageView);
                }
            }

            @Override
            public void drawInBackground(Canvas canvas) {
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                    if (imageView.drawable != null) {
                        imageView.drawable.draw(canvas, imageView.backgroundThreadDrawHolder[threadIndex], false);
                    }
                }
            }

            private OvershootInterpolator appearScaleInterpolator = new OvershootInterpolator(3f);

            @Override
            protected void drawInUiThread(Canvas canvas, float alpha) {
                if (imageViewEmojis != null) {
                    canvas.save();
                    canvas.translate(-startOffset, 0);
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        ImageViewEmoji imageView = imageViewEmojis.get(i);
                        AnimatedEmojiSpan span = imageView.getSpan();
                        if (span == null) {
                            continue;
                        }
                        AnimatedEmojiDrawable drawable = animatedEmojiDrawables.get(imageView.span.getDocumentId());
                        if (drawable == null) {
                            continue;
                        }

                        int topOffset = (int) (imageView.getHeight() * .03f);
                        AndroidUtilities.rectTmp2.set(imageView.getLeft() + imageView.getPaddingLeft(), topOffset, imageView.getRight() - imageView.getPaddingRight(), topOffset + imageView.getMeasuredHeight() - imageView.getPaddingBottom() - imageView.getPaddingTop());
                        float scale = 1;
                        if (imageView.pressedProgress != 0) {
                            scale *= 0.8f + 0.2f * (1f - imageView.pressedProgress);
                        }
                        boolean animatedExpandIn = animateExpandStartTime > 0 && (SystemClock.elapsedRealtime() - animateExpandStartTime) < animateExpandDuration();
                        if (animatedExpandIn && animateExpandFromPosition >= 0 && animateExpandToPosition >= 0 && animateExpandStartTime > 0) {
                            int position = getChildAdapterPosition(imageView);
                            final int pos = position - animateExpandFromPosition;
                            final int count = animateExpandToPosition - animateExpandFromPosition;
                            if (pos >= 0 && pos < count) {
                                final float appearDuration = animateExpandAppearDuration();
                                final float crossfadeDuration = animateExpandCrossfadeDuration();
                                final float CrossfadeT = MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime - (appearDuration * .45f)) / crossfadeDuration, 0, 1);
                                final float AppearT = CubicBezierInterpolator.EASE_OUT.getInterpolation(MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / appearDuration, 0, 1));
                                final float crossfadeT = AndroidUtilities.cascade(CrossfadeT, pos, count, count / 5f);
                                final float alphaT = AndroidUtilities.cascade(AppearT, pos, count, count / 4f);
                                final float scaleT = AndroidUtilities.cascade(AppearT, pos + (count / 4), count + (count / 4), count / 4f);
                                scale *= .5f + appearScaleInterpolator.getInterpolation(scaleT) * .5f;
                                alpha *= alphaT;
                            }
                        }
                        drawable.setAlpha((int) (255 * alpha));
                        drawable.setBounds(AndroidUtilities.rectTmp2);
                        if (scale != 1) {
                            canvas.save();
                            canvas.scale(scale, scale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
                            drawable.draw(canvas);
                            canvas.restore();
                        } else {
                            drawable.draw(canvas);
                        }
                    }
                    canvas.restore();
                }
            }

            @Override
            public void onFrameReady() {
                super.onFrameReady();
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    ImageViewEmoji imageView = drawInBackgroundViews.get(i);
                    if (imageView.backgroundThreadDrawHolder != null) {
                        imageView.backgroundThreadDrawHolder[threadIndex].release();
                    }
                }
                emojiGridView.invalidate();
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final boolean down = ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN || ev.getActionMasked() == MotionEvent.ACTION_DOWN;
            final boolean up = ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP || ev.getActionMasked() == MotionEvent.ACTION_UP;
            final boolean cancel = ev.getActionMasked() == MotionEvent.ACTION_CANCEL;
            if (down || up || cancel) {
                int index = ev.getActionIndex();
                int id = ev.getPointerId(index);
                if (touches == null) {
                    touches = new SparseArray<>();
                }

                float x = ev.getX(index), y = ev.getY(index);
                View touchChild = findChildViewUnder(x, y);

                TouchDownInfo touch;
                if (down) {
                    if (touchChild != null) {
                        touch = new TouchDownInfo();
                        touch.x = x;
                        touch.y = y;
                        touch.time = SystemClock.elapsedRealtime();
                        touch.view = touchChild;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touchChild.getBackground() instanceof RippleDrawable) {
                            touchChild.getBackground().setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                        }
                        touch.view.setPressed(true);
                        touches.put(id, touch);
                        stopScroll();
                    }
                } else {
                    touch = touches.get(id);
                    touches.remove(id);
                    if (
                        touchChild != null && touch != null &&
                        Math.sqrt(Math.pow(x - touch.x, 2) + Math.pow(y - touch.y, 2)) < AndroidUtilities.touchSlop * 3 &&
//                        (SystemClock.elapsedRealtime() - touch.time) <= ViewConfiguration.getTapTimeout() * 1.2f &&
                        !cancel &&
                        (!pickerViewPopup.isShowing() || SystemClock.elapsedRealtime() - touch.time < ViewConfiguration.getLongPressTimeout())
                    ) {
                        View view = touch.view;
                        int position = getChildAdapterPosition(touch.view);
                        if (view instanceof ImageViewEmoji) {
                            ImageViewEmoji viewEmoji = (ImageViewEmoji) view;
                            sendEmoji(viewEmoji, null);
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}
                        } else if (view instanceof EmojiPackExpand) {
                            EmojiPackExpand button = (EmojiPackExpand) view;
                            emojiAdapter.expand(position, button);
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}
                        } else if (view != null) {
                            view.callOnClick();
                        }
                    }
                    if (touch != null && touch.view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && touch.view.getBackground() instanceof RippleDrawable) {
                        touch.view.getBackground().setState(new int[]{});
                    }
                    if (touch != null && touch.view != null) {
                        touch.view.setPressed(false);
                    }
                }
            }
            return super.dispatchTouchEvent(ev) || !cancel && touches.size() > 0;
        }

        private Path lockPath;
        private SparseIntArray headerWidthsCache = new SparseIntArray();
        private AnimatedFloat premiumT = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        public void drawDashedOutlines(Canvas canvas) {
            if (emojiAdapter == null || emojiAdapter.packStartPosition == null) {
                return;
            }

            final float r = AndroidUtilities.dp(20), p = AndroidUtilities.dp(5), sz = AndroidUtilities.dp(11);
            final float itemSize = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / (float) emojiLayoutManager.getSpanCount();
            for (int i = 0; i < emojiAdapter.packStartPosition.size(); ++i) {
                EmojiPack pack = i < emojipacksProcessed.size() ? emojipacksProcessed.get(i) : null;
                if (pack == null || !((pack.installed || installedEmojiSets.contains(pack.set.id)) && !pack.featured && !pack.free)) {
                    continue;
                }
                int start = emojiAdapter.packStartPosition.get(i);
                int end = i + 1 >= emojiAdapter.packStartPosition.size() ? emojiAdapter.getItemCount() : emojiAdapter.packStartPosition.get(i + 1) - 1;
                int end2 = animateExpandFromPosition >= 0 && animateExpandFromPosition > start && animateExpandFromPosition < end ? animateExpandFromPosition - 1 : -1;
                int childPosition = -1;
                int lastPosition1 = -1, lastPosition2 = -1;
                View child = null, lastView1 = null, lastView2 = null;
                float clipTop = getMeasuredHeight(), clipBottom = 0;
                for (int j = 0; j < getChildCount(); ++j) {
                    View c = getChildAt(j);
                    int position = getChildAdapterPosition(c);
                    if (position < 0) {
                        if (c instanceof ImageViewEmoji) {
                            position = ((ImageViewEmoji) c).position;
                        } else if (c instanceof StickerSetNameCell) {
                            position = ((StickerSetNameCell) c).position;
                        } else if (c instanceof EmojiPackButton) {
                            position = ((EmojiPackButton) c).position;
                        } else {
                            position = getChildAdapterPosition(c);
                        }
                    }
                    if (position >= start && position <= end) {
                        if (child == null) {
                            child = c;
                            childPosition = position;
                        }
                        if (j > lastPosition1) {
                            lastPosition1 = j;
                            lastView1 = c;
                        }
                        if (j > lastPosition2 && position <= end2) {
                            lastPosition2 = j;
                            lastView2 = c;
                        }
                        clipTop = Math.min(clipTop, c.getTop() + c.getTranslationY());
                        clipBottom = Math.max(clipBottom, c.getBottom() + c.getTranslationY());
                    }
                }
                if (child == null) {
                    continue;
                }
                clipBottom += AndroidUtilities.dp(6);

                float lockT = premiumT.set(UserConfig.getInstance(currentAccount).isPremium() || allowEmojisForNonPremium ? 0f : 1f); // CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(Math.min(now - appearTime, 550) / 550f);

                int positionInGroup = childPosition - start;
                float top;
                if (positionInGroup == 0) {
                    top = child.getTop() + child.getTranslationY() + AndroidUtilities.dp(25);
                } else {
                    top = child.getTop() + child.getTranslationY() - AndroidUtilities.dp(32 - 25)
                        - (positionInGroup - 1) / Math.max(1, emojiLayoutManager.getSpanCount()) * itemSize - AndroidUtilities.dp(32-25);
                }
                float bottom;
                if (lastView2 != null && lastView1 != null) {
                    float t = MathUtils.clamp((SystemClock.elapsedRealtime() - animateExpandStartTime) / 220f, 0, 1);
                    t = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);
                    bottom = AndroidUtilities.lerp(lastView2.getBottom() + lastView2.getTranslationY(), lastView1.getBottom() + lastView1.getTranslationY(), t);
                } else if (lastView1 != null) {
                    bottom = lastView1.getBottom() + lastView1.getTranslationY();
                } else {
                    bottom = getMeasuredHeight() + AndroidUtilities.dp(6);
                }

                canvas.save();
                canvas.clipRect(0, Math.min(clipTop, clipBottom), getMeasuredWidth(), Math.max(clipTop, clipBottom));
                if (lockT < 1) {
                    canvas.scale(1.1f - .1f * lockT, 1.1f - .1f * lockT, getMeasuredWidth() / 2f, (bottom + top) / 2f);
                }

                if (emojiLockPaint == null) {
                    emojiLockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    emojiLockPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
                    emojiLockPaint.setAlpha((int) (emojiLockPaint.getAlpha() * .5f));
                    emojiLockPaint.setStrokeWidth(AndroidUtilities.dp(2));
                    emojiLockPaint.setStyle(Paint.Style.STROKE);
                    emojiLockPaint.setStrokeCap(Paint.Cap.ROUND);
                    emojiLockPaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(5.5f), AndroidUtilities.dp(7f)}, .5f));
                }
                int wasAlpha = emojiLockPaint.getAlpha();
                emojiLockPaint.setAlpha((int) (wasAlpha * lockT));

                if (lockPath == null) {
                    lockPath = new Path();
                } else {
                    lockPath.rewind();
                }

                if (child instanceof EmojiPackHeader) {
                    final float left =  getPaddingLeft() + ((EmojiPackHeader) child).headerView.getRight() + ((EmojiPackHeader) child).headerView.getTranslationX();
                    final float right = getPaddingLeft() + ((EmojiPackHeader) child).buttonsView.getLeft() + ((EmojiPackHeader) child).premiumButtonView.getLeft();
                    lockPath.moveTo(Math.min(left, right) + AndroidUtilities.dp(8), top);
                    lockPath.lineTo(Math.max(left, right) - AndroidUtilities.dp(8), top);
                    canvas.drawPath(lockPath, emojiLockPaint);
                    lockPath.reset();
                }

                AndroidUtilities.rectTmp.set(p, top, p + r, top + r);
                lockPath.arcTo(AndroidUtilities.rectTmp, 270 - 40, -90 + 40);
                lockPath.moveTo(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.centerY());

                AndroidUtilities.rectTmp.set(p, bottom - r, p + r, bottom);
                lockPath.arcTo(AndroidUtilities.rectTmp, 180, -90);
                lockPath.moveTo(AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.bottom);

                AndroidUtilities.rectTmp.set(getMeasuredWidth() - p - r, bottom - r, getMeasuredWidth() - p, bottom);
                lockPath.arcTo(AndroidUtilities.rectTmp, 90, -90);
                float x = AndroidUtilities.rectTmp.right, y = AndroidUtilities.rectTmp.centerY();

                AndroidUtilities.rectTmp.set(getMeasuredWidth() - p - r, top, getMeasuredWidth() - p, top + r);
                lockPath.moveTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.centerY());
                lockPath.lineTo(x, y);
                lockPath.moveTo(AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.centerY());
                lockPath.arcTo(AndroidUtilities.rectTmp, 0, -45);

                canvas.drawPath(lockPath, emojiLockPaint);
                emojiLockPaint.setAlpha(wasAlpha);

                canvas.restore();
            }
        }
    }

    private void createStickersChooseActionTracker() {
        chooseStickerActionTracker = new ChooseStickerActionTracker(currentAccount, delegate.getDialogId(), delegate.getThreadId()) {
            @Override
            public boolean isShown() {
                return delegate != null && getVisibility() == View.VISIBLE && stickersContainerAttached;
            }
        };
        chooseStickerActionTracker.checkVisibility();
    }

    private void updateEmojiTabsPosition() {
        updateEmojiTabsPosition(emojiLayoutManager.findFirstCompletelyVisibleItemPosition());
    }
    private void updateEmojiTabsPosition(int position) {
        if (!emojiSmoothScrolling && position != RecyclerView.NO_POSITION) {
            int tab = 0;
            int count = getRecentEmoji().size() + (needEmojiSearch ? 1 : 0) + (emojiAdapter.trendingHeaderRow >= 0 ? 3 : 0);
            if (position >= count) {
                tab = -1;
                for (int a = 0; a < EmojiData.dataColored.length; a++) {
                    int size = EmojiData.dataColored[a].length + 1;
                    if (position < count + size) {
                        tab = a + 1;
                        break;
                    }
                    count += size;
                }
                if (tab < 0) {
                    ArrayList<EmojiPack> packs = getEmojipacks();
                    for (int b = emojiAdapter.packStartPosition.size() - 1; b >= 0; --b) {
                        if (emojiAdapter.packStartPosition.get(b) <= position) {
                            EmojiPack pack = emojipacksProcessed.get(b);
                            for (int i = 0; i < packs.size(); ++i) {
                                if (packs.get(i).set.id == pack.set.id && !(pack.featured && (pack.installed || installedEmojiSets.contains(pack.set.id)))) {
                                    tab = 1 + EmojiData.dataColored.length + i;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
            if (tab >= 0) {
                emojiTabs.select(tab);
            }
        }
    }

    private void checkGridVisibility(int position, float positionOffset) {
        if (stickersContainer == null || gifContainer == null) {
            return;
        }
        if (position == 0) {
            emojiGridView.setVisibility(View.VISIBLE);
            gifGridView.setVisibility(positionOffset == 0 ? View.GONE : View.VISIBLE);
            gifTabs.setVisibility(positionOffset == 0 ? View.GONE : View.VISIBLE);
            stickersGridView.setVisibility(View.GONE);
            stickersTabContainer.setVisibility(View.GONE);
        } else if (position == 1) {
            emojiGridView.setVisibility(View.GONE);
            gifGridView.setVisibility(View.VISIBLE);
            gifTabs.setVisibility(View.VISIBLE);
            stickersGridView.setVisibility(positionOffset == 0 ? View.GONE : View.VISIBLE);
            stickersTabContainer.setVisibility(positionOffset == 0 ? View.GONE : View.VISIBLE);
        } else if (position == 2) {
            emojiGridView.setVisibility(View.GONE);
            gifGridView.setVisibility(View.GONE);
            gifTabs.setVisibility(View.GONE);
            stickersGridView.setVisibility(View.VISIBLE);
            stickersTabContainer.setVisibility(View.VISIBLE);
        }
    }

    private void openPremiumAnimatedEmojiFeature() {
        if (delegate != null) {
            delegate.onAnimatedEmojiUnlockClick();
        }
    }

    private class EmojiPackButton extends FrameLayout {
        int position;

        FrameLayout addButtonView;
        AnimatedTextView addButtonTextView;
        PremiumButtonView premiumButtonView;

        public EmojiPackButton(Context context) {
            super(context);

            addButtonTextView = new AnimatedTextView(getContext());
            addButtonTextView.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
            addButtonTextView.setTextSize(AndroidUtilities.dp(14));
            addButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addButtonTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            addButtonTextView.setGravity(Gravity.CENTER);

            addButtonView = new FrameLayout(getContext());
            addButtonView.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), 8));
            addButtonView.addView(addButtonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            addView(addButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            premiumButtonView = new PremiumButtonView(getContext(), false);
            premiumButtonView.setIcon(R.raw.unlock_icon);
            addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private String lastTitle;
        public void set(String title, boolean unlock, boolean installed, OnClickListener onClickListener) {
            lastTitle = title;
            if (unlock) {
                addButtonView.setVisibility(View.GONE);
                premiumButtonView.setVisibility(View.VISIBLE);
                premiumButtonView.setButton(LocaleController.formatString("UnlockPremiumEmojiPack", R.string.UnlockPremiumEmojiPack, title), onClickListener);
            } else {
                premiumButtonView.setVisibility(View.GONE);
                addButtonView.setVisibility(View.VISIBLE);
                addButtonView.setOnClickListener(onClickListener);
            }

            updateInstall(installed, false);
            updateLock(unlock, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(11), AndroidUtilities.dp(6), AndroidUtilities.dp(11));
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44) + getPaddingTop() + getPaddingBottom(), MeasureSpec.EXACTLY));
        }

        private ValueAnimator installFadeAway;
        public void updateInstall(boolean installed, boolean animated) {
            CharSequence text = installed ?
                    LocaleController.getString("Added", R.string.Added) :
                    LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, lastTitle);
            addButtonTextView.setText(text, animated);
            if (installFadeAway != null) {
                installFadeAway.cancel();
                installFadeAway = null;
            }
            addButtonView.setEnabled(!installed);
            if (animated) {
                installFadeAway = ValueAnimator.ofFloat(addButtonView.getAlpha(), installed ? .6f : 1f);
                addButtonView.setAlpha(addButtonView.getAlpha());
                installFadeAway.addUpdateListener(anm -> {
                    if (addButtonView != null) {
                        addButtonView.setAlpha((float) anm.getAnimatedValue());
                    }
                });
                installFadeAway.setDuration(450);
                installFadeAway.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                installFadeAway.start();
            } else {
                addButtonView.setAlpha(installed ? .6f : 1f);
            }
        }

        private float lockT;
        private Boolean lockShow;
        private ValueAnimator lockAnimator;
        private void updateLock(boolean show, boolean animated) {
            if (lockAnimator != null) {
                lockAnimator.cancel();
                lockAnimator = null;
            }

            if (lockShow != null && lockShow == show) {
                return;
            }
            lockShow = show;

            if (animated) {
                premiumButtonView.setVisibility(View.VISIBLE);
                lockAnimator = ValueAnimator.ofFloat(lockT, show ? 1f : 0f);
                lockAnimator.addUpdateListener(anm -> {
                    lockT = (float) anm.getAnimatedValue();
                    if (addButtonView != null) {
                        addButtonView.setAlpha(1f - lockT);
                    }
                    if (premiumButtonView != null) {
                        premiumButtonView.setAlpha(lockT);
                    }
                });
                lockAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            premiumButtonView.setVisibility(View.GONE);
                        }
                    }
                });
                lockAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                lockAnimator.setDuration(350);
                lockAnimator.start();
            } else {
                lockT = lockShow ? 1 : 0;
                addButtonView.setAlpha(1f - lockT);
                premiumButtonView.setAlpha(lockT);
                premiumButtonView.setScaleX(lockT);
                premiumButtonView.setScaleY(lockT);
                premiumButtonView.setVisibility(lockShow ? View.VISIBLE : View.GONE);
            }
        }
    }

    private class EmojiPackHeader extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        RLottieImageView lockView;
        SimpleTextView headerView;

        FrameLayout buttonsView;
        TextView addButtonView;
        TextView removeButtonView;
        PremiumButtonView premiumButtonView;

        private TLRPC.InputStickerSet toInstall, toUninstall;

        private EmojiPack pack;
        boolean divider;

        public EmojiPackHeader(Context context) {
            super(context);

            lockView = new RLottieImageView(context);
            lockView.setAnimation(R.raw.unlock_icon, 24, 24);
            lockView.setColorFilter(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
            addView(lockView, LayoutHelper.createFrameRelatively(20, 20, Gravity.START, 10, 15, 0, 0));

            headerView = new SimpleTextView(context);
            headerView.setTextSize(15);
            headerView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
            headerView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            headerView.setOnClickListener(e -> {
                if (this.pack != null && this.pack.set != null) {
                    openEmojiPackAlert(this.pack.set);
                }
            });
            headerView.setEllipsizeByGradient(true);
            addView(headerView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.START, 15, 15, 0, 0));

            buttonsView = new FrameLayout(context);
            buttonsView.setPadding(AndroidUtilities.dp(11), AndroidUtilities.dp(11), AndroidUtilities.dp(11), 0);
            buttonsView.setClipToPadding(false);
            buttonsView.setOnClickListener(e -> {
                if (addButtonView != null && addButtonView.getVisibility() == View.VISIBLE && addButtonView.isEnabled()) {
                    addButtonView.performClick();
                } else if (removeButtonView != null && removeButtonView.getVisibility() == View.VISIBLE && removeButtonView.isEnabled()) {
                    removeButtonView.performClick();
                } else if (premiumButtonView != null && premiumButtonView.getVisibility() == View.VISIBLE && premiumButtonView.isEnabled()) {
                    premiumButtonView.performClick();
                }
            });
            addView(buttonsView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END | Gravity.FILL_VERTICAL));

            addButtonView = new TextView(context);
            addButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addButtonView.setText(LocaleController.getString("Add", R.string.Add));
            addButtonView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            addButtonView.setBackground(Theme.AdaptiveRipple.createRect(getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed), 16));
            addButtonView.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            addButtonView.setGravity(Gravity.CENTER);
            addButtonView.setOnClickListener(e -> {
                if (pack == null || pack.set == null) {
                    return;
                }
                pack.installed = true;
                if (!installedEmojiSets.contains(pack.set.id)) {
                    installedEmojiSets.add(pack.set.id);
                }
                updateState(true);
                Integer position = null;
                View expandButton = null;
                for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
                    if (emojiGridView.getChildAt(i) instanceof EmojiPackExpand) {
                        View child = emojiGridView.getChildAt(i);
                        int j = emojiGridView.getChildAdapterPosition(child);
                        if (j >= 0) {
                            int section = emojiAdapter.positionToExpand.get(j);
                            if (section >= 0 && section < emojipacksProcessed.size() &&
                                emojipacksProcessed.get(section) != null && pack != null &&
                                emojipacksProcessed.get(section).set.id == pack.set.id
                            ) {
                                position = j;
                                expandButton = child;
                                break;
                            }
                        }
                    }
                }
                if (position != null) {
                    emojiAdapter.expand(position, expandButton);
                }
                if (toInstall != null) {
                    return;
                }
                TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                inputStickerSetID.id = pack.set.id;
                inputStickerSetID.access_hash = pack.set.access_hash;
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetID, true);
                if (stickerSet == null || stickerSet.set == null) {
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
                    MediaDataController.getInstance(currentAccount).getStickerSet(toInstall = inputStickerSetID, false);
                } else {
                    install(stickerSet);
                }
            });
            buttonsView.addView(addButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 26, Gravity.END | Gravity.TOP));

            removeButtonView = new TextView(context);
            removeButtonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            removeButtonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            removeButtonView.setText(LocaleController.getString("StickersRemove", R.string.StickersRemove));
            removeButtonView.setTextColor(getThemedColor(Theme.key_featuredStickers_removeButtonText));
            removeButtonView.setBackground(Theme.AdaptiveRipple.createRect(0, getThemedColor(Theme.key_featuredStickers_addButton) & 0x1affffff, 16));
            removeButtonView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            removeButtonView.setGravity(Gravity.CENTER);
            removeButtonView.setTranslationX(AndroidUtilities.dp(4));
            removeButtonView.setOnClickListener(e -> {
                if (pack == null || pack.set == null) {
                    return;
                }
                pack.installed = false;
                installedEmojiSets.remove(pack.set.id);
                updateState(true);
                if (emojiTabs != null) {
                    emojiTabs.updateEmojiPacks(getEmojipacks());
                }
                updateEmojiTabsPosition();
                if (toUninstall != null) {
                    return;
                }
                TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                inputStickerSetID.id = pack.set.id;
                inputStickerSetID.access_hash = pack.set.access_hash;
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetID, true);
                if (stickerSet == null || stickerSet.set == null) {
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
                    MediaDataController.getInstance(currentAccount).getStickerSet(toUninstall = inputStickerSetID, false);
                } else {
                    uninstall(stickerSet);
                }
            });
            buttonsView.addView(removeButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 26, Gravity.END | Gravity.TOP));

            premiumButtonView = new PremiumButtonView(context, AndroidUtilities.dp(16), false);
            premiumButtonView.setIcon(R.raw.unlock_icon);
            premiumButtonView.setButton(LocaleController.getString("Unlock", R.string.Unlock), e -> openPremiumAnimatedEmojiFeature());

            try {
                MarginLayoutParams iconLayout = (MarginLayoutParams) premiumButtonView.getIconView().getLayoutParams();
                iconLayout.leftMargin = AndroidUtilities.dp(1);
                iconLayout.topMargin = AndroidUtilities.dp(1);
                iconLayout.width = iconLayout.height = AndroidUtilities.dp(20);
                MarginLayoutParams layout = (MarginLayoutParams) premiumButtonView.getTextView().getLayoutParams();
                layout.leftMargin = AndroidUtilities.dp(5);
                layout.topMargin = AndroidUtilities.dp(-.5f);
                premiumButtonView.getChildAt(0).setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            } catch (Exception ev) {}

            buttonsView.addView(premiumButtonView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 26, Gravity.END | Gravity.TOP));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            ((MarginLayoutParams) headerView.getLayoutParams()).topMargin = AndroidUtilities.dp(currentButtonState == BUTTON_STATE_EMPTY ? 10 : 15);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(currentButtonState == BUTTON_STATE_EMPTY ? 32 : 42), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            headerView.setRightPadding(buttonsView.getWidth() + AndroidUtilities.dp(11));
        }

        public void setStickerSet(EmojiPack pack, boolean divider) {
            if (pack == null) {
                return;
            }

            this.pack = pack;
            this.divider = divider;
            headerView.setText(pack.set.title);

            if (pack.installed && !pack.set.official) {
                premiumButtonView.setButton(LocaleController.getString("Restore", R.string.Restore), e -> openPremiumAnimatedEmojiFeature());
            } else {
                premiumButtonView.setButton(LocaleController.getString("Unlock", R.string.Unlock), e -> openPremiumAnimatedEmojiFeature());
            }

            updateState(false);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.groupStickersDidLoad) {
                if (toInstall != null) {
                    TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetById(toInstall.id);
                    if (stickerSet != null && stickerSet.set != null) {
                        install(stickerSet);
                        toInstall = null;
                    }
                }
                if (toUninstall != null) {
                    TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetById(toUninstall.id);
                    if (stickerSet != null && stickerSet.set != null) {
                        uninstall(stickerSet);
                        toUninstall = null;
                    }
                }
            }
        }

        private BaseFragment getFragment() {
            if (fragment != null) {
                return fragment;
            }
            return new BaseFragment() {
                @Override
                public int getCurrentAccount() {
                    return EmojiView.this.currentAccount;
                }

                @Override
                public View getFragmentView() {
                    return EmojiView.this.bulletinContainer;
                }

                @Override
                public FrameLayout getLayoutContainer() {
                    return EmojiView.this.bulletinContainer;
                }

                @Override
                public Theme.ResourcesProvider getResourceProvider() {
                    return EmojiView.this.resourcesProvider;
                }
            };
        }

        private void install(TLRPC.TL_messages_stickerSet set) {
            EmojiPacksAlert.installSet(getFragment(), set, true, null, () -> {
                pack.installed = true;
                updateState(true);
            });
        }

        private void uninstall(TLRPC.TL_messages_stickerSet set) {
            EmojiPacksAlert.uninstallSet(getFragment(), set, true, () -> {
                pack.installed = true;
                if (!installedEmojiSets.contains(set.set.id)) {
                    installedEmojiSets.add(set.set.id);
                }
                updateState(true);
            });
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        }

        private Paint dividerPaint;
        @Override
        protected void onDraw(Canvas canvas) {
            if (divider) {
                if (dividerPaint == null) {
                    dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    dividerPaint.setStrokeWidth(1);
                    dividerPaint.setColor(getThemedColor(Theme.key_divider));
                }
                canvas.drawRect(0, 0, getMeasuredWidth(), 1, dividerPaint);
            }
            super.onDraw(canvas);
        }

        public void updateState(boolean animated) {
            if (pack == null) {
                return;
            }
            int state = BUTTON_STATE_EMPTY;
            boolean installed = pack.installed || installedEmojiSets.contains(pack.set.id);
            if (!pack.free && !UserConfig.getInstance(currentAccount).isPremium() && !allowEmojisForNonPremium) {
                state = BUTTON_STATE_LOCKED;
            } else if (pack.featured) {
                if (installed) {
                    state = BUTTON_STATE_REMOVE;
                } else {
                    state = BUTTON_STATE_ADD;
                }
            }
            updateState(state, animated);
        }

        public static final int BUTTON_STATE_EMPTY = 0;
        public static final int BUTTON_STATE_LOCKED = 1;
        public static final int BUTTON_STATE_ADD = 2;
        public static final int BUTTON_STATE_REMOVE = 3;

        private int currentButtonState;
        private AnimatorSet stateAnimator;
        public void updateState(int state, boolean animated) {
            if ((state == BUTTON_STATE_EMPTY) != (currentButtonState == BUTTON_STATE_EMPTY)) {
                requestLayout();
            }
            currentButtonState = state;
            if (stateAnimator != null) {
                stateAnimator.cancel();
                stateAnimator = null;
            }
            premiumButtonView.setEnabled(state == BUTTON_STATE_LOCKED);
            addButtonView.setEnabled(state == BUTTON_STATE_ADD);
            removeButtonView.setEnabled(state == BUTTON_STATE_REMOVE);
            if (animated) {
                stateAnimator = new AnimatorSet();
                stateAnimator.playTogether(
                    ObjectAnimator.ofFloat(lockView, TRANSLATION_X, state == BUTTON_STATE_LOCKED ? 0 : -AndroidUtilities.dp(16)),
                    ObjectAnimator.ofFloat(lockView, ALPHA, state == BUTTON_STATE_LOCKED ? 1f : 0),
                    ObjectAnimator.ofFloat(headerView, TRANSLATION_X, state == BUTTON_STATE_LOCKED ? AndroidUtilities.dp(16) : 0),
                    ObjectAnimator.ofFloat(premiumButtonView, ALPHA, state == BUTTON_STATE_LOCKED ? 1 : 0),
                    ObjectAnimator.ofFloat(premiumButtonView, SCALE_X, state == BUTTON_STATE_LOCKED ? 1 : .6f),
                    ObjectAnimator.ofFloat(premiumButtonView, SCALE_Y, state == BUTTON_STATE_LOCKED ? 1 : .6f),
                    ObjectAnimator.ofFloat(addButtonView, ALPHA, state == BUTTON_STATE_ADD ? 1 : 0),
                    ObjectAnimator.ofFloat(addButtonView, SCALE_X, state == BUTTON_STATE_ADD ? 1 : .6f),
                    ObjectAnimator.ofFloat(addButtonView, SCALE_Y, state == BUTTON_STATE_ADD ? 1 : .6f),
                    ObjectAnimator.ofFloat(removeButtonView, ALPHA, state == BUTTON_STATE_REMOVE ? 1 : 0),
                    ObjectAnimator.ofFloat(removeButtonView, SCALE_X, state == BUTTON_STATE_REMOVE ? 1 : .6f),
                    ObjectAnimator.ofFloat(removeButtonView, SCALE_Y, state == BUTTON_STATE_REMOVE ? 1 : .6f)
                );
                stateAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        premiumButtonView.setVisibility(View.VISIBLE);
                        addButtonView.setVisibility(View.VISIBLE);
                        removeButtonView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        premiumButtonView.setVisibility(state == BUTTON_STATE_LOCKED ? View.VISIBLE : View.GONE);
                        addButtonView.setVisibility(state == BUTTON_STATE_ADD ? View.VISIBLE : View.GONE);
                        removeButtonView.setVisibility(state == BUTTON_STATE_REMOVE ? View.VISIBLE : View.GONE);
                    }
                });
                stateAnimator.setDuration(250);
                stateAnimator.setInterpolator(new OvershootInterpolator(1.02f));
                stateAnimator.start();
            } else {
                lockView.setAlpha(state == BUTTON_STATE_LOCKED ? 1f : 0);
                lockView.setTranslationX(state == BUTTON_STATE_LOCKED ? 0 : -AndroidUtilities.dp(16));
                headerView.setTranslationX(state == BUTTON_STATE_LOCKED ? AndroidUtilities.dp(16) : 0);
                premiumButtonView.setAlpha(state == BUTTON_STATE_LOCKED ? 1 : 0);
                premiumButtonView.setScaleX(state == BUTTON_STATE_LOCKED ? 1 : .6f);
                premiumButtonView.setScaleY(state == BUTTON_STATE_LOCKED ? 1 : .6f);
                premiumButtonView.setVisibility(state == BUTTON_STATE_LOCKED ? View.VISIBLE : View.GONE);
                addButtonView.setAlpha(state == BUTTON_STATE_ADD ? 1 : 0);
                addButtonView.setScaleX(state == BUTTON_STATE_ADD ? 1 : .6f);
                addButtonView.setScaleY(state == BUTTON_STATE_ADD ? 1 : .6f);
                addButtonView.setVisibility(state == BUTTON_STATE_ADD ? View.VISIBLE : View.GONE);
                removeButtonView.setAlpha(state == BUTTON_STATE_REMOVE ? 1 : 0);
                removeButtonView.setScaleX(state == BUTTON_STATE_REMOVE ? 1 : .6f);
                removeButtonView.setScaleY(state == BUTTON_STATE_REMOVE ? 1 : .6f);
                removeButtonView.setVisibility(state == BUTTON_STATE_REMOVE ? View.VISIBLE : View.GONE);
            }
        }
    }

    private boolean emojiPackAlertOpened = false;
    public void openEmojiPackAlert(TLRPC.StickerSet set) {
        if (emojiPackAlertOpened) {
            return;
        }
        emojiPackAlertOpened = true;
        ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>(1);
        TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
        inputStickerSetID.id = set.id;
        inputStickerSetID.access_hash = set.access_hash;
        inputStickerSets.add(inputStickerSetID);
        new EmojiPacksAlert(fragment, getContext(), resourcesProvider, inputStickerSets) {
            @Override
            public void dismiss() {
                emojiPackAlertOpened = false;
                super.dismiss();
            }

            @Override
            protected void onButtonClicked(boolean install) {
                if (install) {
                    if (!installedEmojiSets.contains(set.id)) {
                        installedEmojiSets.add(set.id);
                    }
                } else {
                    installedEmojiSets.remove(set.id);
                }
                updateEmojiHeaders();
            }
        }.show();
    }

    public class EmojiGridSpacing extends RecyclerView.ItemDecoration {
        public EmojiGridSpacing() {}

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (view instanceof StickerSetNameCell) {
                outRect.left = AndroidUtilities.dp(5);
                outRect.right = AndroidUtilities.dp(5);
                int position = parent.getChildAdapterPosition(view);
                if (position + 1 > emojiAdapter.plainEmojisCount && !UserConfig.getInstance(currentAccount).isPremium() && !allowEmojisForNonPremium) {
                    outRect.top = AndroidUtilities.dp(10);
                }
            } else if (view instanceof RecyclerListView || view instanceof EmojiPackHeader) {
                outRect.left = -emojiGridView.getPaddingLeft();
                outRect.right = -emojiGridView.getPaddingRight();
                if (view instanceof EmojiPackHeader) {
                    outRect.top = AndroidUtilities.dp(8);
//                    if (parent.getChildAdapterPosition(view) == emojiAdapter.firstTrendingRow) {
//                        outRect.top = AndroidUtilities.dp(32);
//                    }
                }
            } else if (view instanceof BackupImageView) {
                outRect.bottom = AndroidUtilities.dp(12);
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

    private void openTrendingStickers(TLRPC.StickerSetCovered set) {
        final TrendingStickersLayout.Delegate trendingDelegate = new TrendingStickersLayout.Delegate() {
            @Override
            public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
                delegate.onStickerSetAdd(stickerSet);
                if (primary) {
                    updateStickerTabs(true);
                }
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                delegate.onStickerSetRemove(stickerSet);
            }

            @Override
            public boolean onListViewInterceptTouchEvent(RecyclerListView listView, MotionEvent event) {
                return ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, EmojiView.this.getMeasuredHeight(), contentPreviewViewerDelegate, resourcesProvider);
            }

            @Override
            public boolean onListViewTouchEvent(RecyclerListView listView, RecyclerListView.OnItemClickListener onItemClickListener, MotionEvent event) {
                return ContentPreviewViewer.getInstance().onTouch(event, listView, EmojiView.this.getMeasuredHeight(), onItemClickListener, contentPreviewViewerDelegate, resourcesProvider);
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
                delegate.onStickerSelected(null, sticker, null, parent, null, notify, scheduleDate);
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
        this.delegate.showTrendingStickersAlert(new TrendingStickersLayout(getContext(), trendingDelegate, primaryInstallingStickerSets, installingStickerSets, removingStickerSets, set, resourcesProvider));
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        updateStickerTabsPosition();
        updateBottomTabContainerPosition();
    }
    private void updateBottomTabContainerPosition() {
        View parent = (View) getParent();
        if (parent != null) {
            float y = getY() - parent.getHeight();
            if (getLayoutParams().height > 0) {
                y += getLayoutParams().height;
            } else {
                y += getMeasuredHeight();
            }
            if (bottomTabContainer.getTop() - y < 0) {
                y = 0;
            }
            bottomTabMainTranslation = -y;
            bottomTabContainer.setTranslationY(bottomTabMainTranslation + bottomTabAdditionalTranslation);
            if (needEmojiSearch) {
                bulletinContainer.setTranslationY(bottomTabMainTranslation + bottomTabAdditionalTranslation);
            }
        }
    }

    Rect rect = new Rect();
    private void updateStickerTabsPosition() {
        if (stickersTabContainer == null) {
            return;
        }
        boolean visible = getVisibility() == View.VISIBLE && stickersContainerAttached && delegate.getProgressToSearchOpened() != 1f;
        stickersTabContainer.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible) {
            rect.setEmpty();
            pager.getChildVisibleRect(stickersContainer, rect, null);
            float searchProgressOffset = AndroidUtilities.dp(50) * delegate.getProgressToSearchOpened();
            int left = rect.left;
            if (left != 0 || searchProgressOffset != 0) {
                expandStickersByDragg = false;
            }

            stickersTabContainer.setTranslationX(left);
            float y = getTop() + getTranslationY() - stickersTabContainer.getTop() - stickersTab.getExpandedOffset() - searchProgressOffset;
            if (stickersTabContainer.getTranslationY() != y) {
                stickersTabContainer.setTranslationY(y);
                stickersTabContainer.invalidate();
            }
        }

        if (expandStickersByDragg && (visible && showing)) {
            stickersTab.expandStickers(lastStickersX, true);
        } else {
            expandStickersByDragg = false;
            stickersTab.expandStickers(lastStickersX, false);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        updateBottomTabContainerPosition();
        super.dispatchDraw(canvas);
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

    private ArrayList<String> lastRecentArray;
    private int lastRecentCount;
    public ArrayList<String> getRecentEmoji() {
        if (allowAnimatedEmoji) {
            return Emoji.recentEmoji;
        }
        if (lastRecentArray == null) {
            lastRecentArray = new ArrayList<>();
        }
        if (Emoji.recentEmoji.size() != lastRecentCount) {
            lastRecentArray.clear();
            for (int i = 0; i < Emoji.recentEmoji.size(); ++i) {
                if (!Emoji.recentEmoji.get(i).startsWith("animated_")) {
                    lastRecentArray.add(Emoji.recentEmoji.get(i));
                }
            }
            lastRecentCount = lastRecentArray.size();
        }
        return lastRecentArray;
    }

    public void addEmojiToRecent(String code) {
        if (code == null || !(code.startsWith("animated_") || Emoji.isValidEmoji(code))) {
            return;
        }
        Emoji.addRecentEmoji(code);
        if (getVisibility() != VISIBLE || pager.getCurrentItem() != 0) {
            Emoji.sortEmoji();
            emojiAdapter.notifyDataSetChanged();
        }
        Emoji.saveRecentEmoji();

        if (!allowAnimatedEmoji) {
            if (lastRecentArray == null) {
                lastRecentArray = new ArrayList<>();
            } else {
                lastRecentArray.clear();
            }
            for (int i = 0; i < Emoji.recentEmoji.size(); ++i) {
                if (!Emoji.recentEmoji.get(i).startsWith("animated_")) {
                    lastRecentArray.add(Emoji.recentEmoji.get(i));
                }
            }
            lastRecentCount = lastRecentArray.size();
        }

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
                    layoutManager.scrollToPositionWithOffset(0, 0);
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
            View tabStrip;
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
                if (tabStrip != null && a != 2) {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, -AndroidUtilities.dp(40)),
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, -AndroidUtilities.dp(36)),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(0)));
                } else {
                    searchAnimation.playTogether(
                            ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, a == 2 ? 0 : -AndroidUtilities.dp(36)),
                            ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(0)));
                }
                searchAnimation.setDuration(220);
                searchAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                searchAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            gridView.setTranslationY(0);
                            if (gridView == stickersGridView) {
                                gridView.setPadding(0, 0, 0, 0);
                            } else if (gridView == emojiGridView) {
                                gridView.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
                            } else if (gridView == gifGridView) {
                                gridView.setPadding(0, searchFieldHeight, 0, 0);
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
                if (tabStrip != null && a != 2) {
                    tabStrip.setTranslationY(-AndroidUtilities.dp(40));
                }
                if (gridView == stickersGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                } else if (gridView == emojiGridView) {
                    gridView.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
                } else if (gridView == gifGridView) {
                    gridView.setPadding(0, searchFieldHeight, 0, 0);
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
        showBottomTab(false, true);
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

    private void scrollStickersToPosition(int p, int offset) {
        View view = stickersLayoutManager.findViewByPosition(p);
        int firstPosition = stickersLayoutManager.findFirstVisibleItemPosition();
        if (view == null && Math.abs(p - firstPosition) > 40) {
            stickersScrollHelper.setScrollDirection(stickersLayoutManager.findFirstVisibleItemPosition() < p ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            stickersScrollHelper.scrollToPosition(p, offset, false, true);
        } else {
            ignoreStickersScroll = true;
            stickersGridView.smoothScrollToPosition(p);
        }
    }

    public void scrollEmojisToAnimated() {
        if (emojiSmoothScrolling) {
            return;
        }
        try {
            int position = emojiAdapter.sectionToPosition.get(EmojiData.dataColored.length);
            if (position > 0) {
                emojiGridView.stopScroll();
                updateEmojiTabsPosition(position);
                scrollEmojisToPosition(position, AndroidUtilities.dp(-9));
                checkEmojiTabY(null, 0);
            }
        } catch (Exception ignore) {}
    }

    private void scrollEmojisToPosition(int p, int offset) {
        View view = emojiLayoutManager.findViewByPosition(p);
        int firstPosition = emojiLayoutManager.findFirstVisibleItemPosition();
        if ((view == null && Math.abs(p - firstPosition) > emojiLayoutManager.getSpanCount() * 9f) || !SharedConfig.animationsEnabled()) {
            emojiScrollHelper.setScrollDirection(emojiLayoutManager.findFirstVisibleItemPosition() < p ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            emojiScrollHelper.scrollToPosition(p, offset, false, true);
        } else {
            ignoreStickersScroll = true;
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(emojiGridView.getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                @Override
                public void onEnd() {
                    emojiSmoothScrolling = false;
                }

                @Override
                protected void onStart() {
                    emojiSmoothScrolling = true;
                }
            };
            linearSmoothScroller.setTargetPosition(p);
            linearSmoothScroller.setOffset(offset);
            emojiLayoutManager.startSmoothScroll(linearSmoothScroller);
        }
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
                if (pos >= 0 && pos < stickersGridAdapter.getItemCount()) {
                    scrollStickersToPosition(pos, AndroidUtilities.dp(36 + 12));
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
            View tabStrip;

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
            if (currentField.categoriesListView != null) {
                currentField.categoriesListView.selectCategory(null);
                currentField.categoriesListView.scrollToStart();
            }

            if (a == currentItem && animated) {
                searchAnimation = new AnimatorSet();
                if (tabStrip != null && a != 1) {
                    searchAnimation.playTogether(
                        ObjectAnimator.ofFloat(tabStrip, View.TRANSLATION_Y, 0),
                        ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, AndroidUtilities.dp(36)),
                        ObjectAnimator.ofFloat(currentField, View.TRANSLATION_Y, AndroidUtilities.dp(36))
                    );
                } else {
                    searchAnimation.playTogether(
                        ObjectAnimator.ofFloat(gridView, View.TRANSLATION_Y, AndroidUtilities.dp(36) - searchFieldHeight)
                    );
                }
                searchAnimation.setDuration(200);
                searchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                searchAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(searchAnimation)) {
                            int firstVisPos = layoutManager.findFirstVisibleItemPosition();
                            gridView.setTranslationY(0);
                            if (gridView == stickersGridView) {
                                gridView.setPadding(0, AndroidUtilities.dp(36), 0, AndroidUtilities.dp(44));
                            } else if (gridView == gifGridView) {
                                gridView.setPadding(0, searchFieldHeight, 0, AndroidUtilities.dp(44));
                            } else if (gridView == emojiGridView) {
                                gridView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(36), AndroidUtilities.dp(5), AndroidUtilities.dp(44));
                            }
                            if (firstVisPos != RecyclerView.NO_POSITION) {
                                layoutManager.scrollToPositionWithOffset(firstVisPos, 0);
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
                if (currentField != gifSearchField) {
                    currentField.setTranslationY(AndroidUtilities.dp(36) - searchFieldHeight);
                }
                if (tabStrip != null && a != 2) {
                    tabStrip.setTranslationY(0);
                }
                if (gridView == stickersGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(36), 0, AndroidUtilities.dp(44));
                } else if (gridView == gifGridView) {
                    gridView.setPadding(0, AndroidUtilities.dp(36 + 4), 0, AndroidUtilities.dp(44));
                } else if (gridView == emojiGridView) {
                    gridView.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(36), AndroidUtilities.dp(5), AndroidUtilities.dp(44));
                }
                layoutManager.scrollToPositionWithOffset(0, 0);
            }
        }
        if (!animated) {
            delegate.onSearchOpenClose(0);
        }
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
        if (SystemClock.elapsedRealtime() - shownBottomTabAfterClick < ViewConfiguration.getTapTimeout()) {
            return;
        }
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

    private ValueAnimator bottomTabContainerAnimator;
    private float bottomTabMainTranslation, bottomTabAdditionalTranslation;
    private long shownBottomTabAfterClick;
    private void showBottomTab(boolean show, boolean animated) {
        lastBottomScrollDy = 0;
        if (delegate != null && delegate.isSearchOpened()) {
            show = false;
        }
        if (show && bottomTabContainer.getTag() == null || !show && bottomTabContainer.getTag() != null) {
            return;
        }
        if (bottomTabContainerAnimator != null) {
            bottomTabContainerAnimator.cancel();
            bottomTabContainerAnimator = null;
        }
        bottomTabContainer.setTag(show ? null : 1);
        if (animated) {
            bottomTabContainerAnimator = ValueAnimator.ofFloat(bottomTabAdditionalTranslation, show ? 0 : AndroidUtilities.dp(needEmojiSearch ? 45 : 50));
            bottomTabContainerAnimator.addUpdateListener(anm -> {
                bottomTabAdditionalTranslation = (float) anm.getAnimatedValue();
                updateBottomTabContainerPosition();
            });
            bottomTabContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bottomTabContainerAnimator != animation) {
                        return;
                    }
                    bottomTabAdditionalTranslation = (float) bottomTabContainerAnimator.getAnimatedValue();
                    updateBottomTabContainerPosition();
                    bottomTabContainerAnimator = null;
                }
            });
            bottomTabContainerAnimator.setDuration(380);
            bottomTabContainerAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            bottomTabContainerAnimator.start();
        } else {
            bottomTabAdditionalTranslation = show ? 0 : AndroidUtilities.dp(needEmojiSearch ? 45 : 50);
            updateBottomTabContainerPosition();
        }
    }

    private void checkTabsY(@Type int type, int dy) {
        if (type == Type.EMOJIS) {
            checkEmojiTabY(emojiGridView, dy);
            return;
        }
        if (delegate != null && delegate.isSearchOpened() || ignoreStickersScroll) {
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
        if (type == 0) {
            updateStickerTabsPosition();
        } else {
            getTabsForType(type).setTranslationY(Math.max(-AndroidUtilities.dp(48), tabsMinusDy[type]));
        }
    }

    private void resetTabsY(@Type int type) {
        if (delegate != null && delegate.isSearchOpened() || type == Type.STICKERS) {
            return;
        }
        getTabsForType(type).setTranslationY(tabsMinusDy[type] = 0);
    }

    private void animateTabsY(@Type int type) {
        if ((delegate != null && delegate.isSearchOpened()) || type == Type.STICKERS) {
            return;
        }
        final float tabsHeight = AndroidUtilities.dpf2(type == Type.EMOJIS ? 36 : 48);
        final float fraction = tabsMinusDy[type] / -tabsHeight;
        if (fraction <= 0f || fraction >= 1f) {
            animateSearchField(type);
            return;
        }
        final View tabStrip = getTabsForType(type);
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
        if (type == Type.GIFS) {
            return;
        }
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

    private View getTabsForType(@Type int type) {
        switch (type) {
            case Type.STICKERS:
                return stickersTab;
            case Type.EMOJIS:
                return emojiTabs;
            case Type.GIFS:
                return gifTabs;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private RecyclerListView getListViewForType(@Type int type) {
        switch (type) {
            case Type.STICKERS:
                return stickersGridView;
            case Type.EMOJIS:
                return emojiGridView;
            case Type.GIFS:
                return gifGridView;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private GridLayoutManager getLayoutManagerForType(@Type int type) {
        switch (type) {
            case Type.STICKERS:
                return stickersLayoutManager;
            case Type.EMOJIS:
                return emojiLayoutManager;
            case Type.GIFS:
                return gifLayoutManager;
            default:
                throw new IllegalArgumentException("Unexpected argument: " + type);
        }
    }

    private SearchField getSearchFieldForType(@Type int type) {
        switch (type) {
            case Type.STICKERS:
                return stickersSearchField;
            case Type.EMOJIS:
                return emojiSearchField;
            case Type.GIFS:
                return gifSearchField;
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
        if (list.getVisibility() != VISIBLE || emojiSmoothScrolling) {
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
        } else if (tabsMinusDy[Type.EMOJIS] < -AndroidUtilities.dp(36 * 3)) {
            tabsMinusDy[Type.EMOJIS] = -AndroidUtilities.dp(36 * 3);
        }
        emojiTabs.setTranslationY(Math.max(-AndroidUtilities.dp(36), tabsMinusDy[Type.EMOJIS]));
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
//        RecyclerView.ViewHolder holder = gifGridView.findViewHolderForAdapterPosition(0);
//        if (holder != null) {
//            gifSearchField.setTranslationY(holder.itemView.getTop());
//        } else {
//            gifSearchField.setTranslationY(-searchFieldHeight);
//        }
        gifSearchField.showShadow(true, !isLayout);
    }

    private void scrollGifsToTop() {
        gifLayoutManager.scrollToPositionWithOffset(0, 0);
        resetTabsY(Type.GIFS);
    }

    private void checkScroll(@Type int type) {
        if (type == Type.STICKERS) {
            if (ignoreStickersScroll) {
                return;
            }
            int firstVisibleItem = stickersLayoutManager.findFirstVisibleItemPosition();
            if (firstVisibleItem == RecyclerView.NO_POSITION) {
                return;
            }
            if (stickersGridView == null) {
                return;
            }
            int firstTab;
            if (favTabNum > 0) {
                firstTab = favTabNum;
            } else if (recentTabNum > 0) {
                firstTab = recentTabNum;
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

    private void updateEmojiHeaders() {
        if (emojiGridView == null) {
            return;
        }
        for (int i = 0; i < emojiGridView.getChildCount(); ++i) {
            View child = emojiGridView.getChildAt(i);
            if (child instanceof EmojiPackHeader) {
                ((EmojiPackHeader) child).updateState(true);
            }
        }
    }

    ArrayList<TLRPC.TL_messages_stickerSet> frozenStickerSets;

    private void updateStickerTabs(boolean updateStickerSets) {
        if (stickersTab == null || stickersTab.isDragging()) {
            return;
        }
        recentTabNum = -2;
        favTabNum = -2;
        trendingTabNum = -2;
        premiumTabNum = -2;
        hasChatStickers = false;

        stickersTabOffset = 0;
        int lastPosition = stickersTab.getCurrentPosition();
        stickersTab.beginUpdate(getParent() != null && getVisibility() == VISIBLE && (installingStickerSets.size() != 0 || removingStickerSets.size() != 0));

        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

        SharedPreferences preferences = MessagesController.getEmojiSettings(currentAccount);
        featuredStickerSets.clear();
        ArrayList<TLRPC.StickerSetCovered> featured = mediaDataController.getFeaturedStickerSets();
        for (int a = 0, N = featured.size(); a < N; a++) {
            TLRPC.StickerSetCovered set = featured.get(a);
            if (mediaDataController.isStickerPackInstalled(set.set.id)) {
                continue;
            }
            featuredStickerSets.add(set);
        }
        if (trendingAdapter != null) {
            trendingAdapter.notifyDataSetChanged();
        }
        if (!featured.isEmpty() && (featuredStickerSets.isEmpty() || preferences.getLong("featured_hidden", 0) == featured.get(0).set.id)) {
            final int id = mediaDataController.getUnreadStickerSets().isEmpty() ? 2 : 3;
            final StickerTabView trendingStickersTabView = stickersTab.addStickerIconTab(id, stickerIcons[id]);
            trendingStickersTabView.textView.setText(LocaleController.getString("FeaturedStickersShort", R.string.FeaturedStickersShort));
            trendingStickersTabView.setContentDescription(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers));
            trendingTabNum = stickersTabOffset;
            stickersTabOffset++;
        }

        if (!favouriteStickers.isEmpty()) {
            favTabNum = stickersTabOffset;
            stickersTabOffset++;
            StickerTabView stickerTabView = stickersTab.addStickerIconTab(1, stickerIcons[1]);
            stickerTabView.textView.setText(LocaleController.getString("FavoriteStickersShort", R.string.FavoriteStickersShort));
            stickerTabView.setContentDescription(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers));
        }

        if (!recentStickers.isEmpty()) {
            recentTabNum = stickersTabOffset;
            stickersTabOffset++;
            StickerTabView stickerTabView = stickersTab.addStickerIconTab(0, stickerIcons[0]);
            stickerTabView.textView.setText(LocaleController.getString("RecentStickersShort", R.string.RecentStickersShort));
            stickerTabView.setContentDescription(LocaleController.getString("RecentStickers", R.string.RecentStickers));
        }


        stickerSets.clear();
        groupStickerSet = null;
        groupStickerPackPosition = -1;
        groupStickerPackNum = -10;

        if (frozenStickerSets == null || updateStickerSets) {
            frozenStickerSets = new ArrayList<>(mediaDataController.getStickerSets(MediaDataController.TYPE_IMAGE));
        }
        ArrayList<TLRPC.TL_messages_stickerSet> packs = frozenStickerSets;
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
        packs = MessagesController.getInstance(currentAccount).filterPremiumStickers(packs);
        for (int a = 0; a < packs.size(); a++) {
            TLRPC.TL_messages_stickerSet pack = packs.get(a);
            if (pack.set.archived || pack.documents == null || pack.documents.isEmpty()) {
                continue;
            }
            stickerSets.add(pack);
        }

//        if (!premiumStickers.isEmpty()) {
//            premiumTabNum = stickersTabOffset;
//            stickersTabOffset++;
//            StickerTabView stickerTabView = stickersTab.addStickerIconTab(4, PremiumGradient.getInstance().premiumStarMenuDrawable2);
//            stickerTabView.textView.setText(LocaleController.getString("PremiumStickersShort", R.string.PremiumStickersShort));
//            stickerTabView.setContentDescription(LocaleController.getString("PremiumStickers", R.string.PremiumStickers));
//        }

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
                    hasChatStickers = true;
                    stickersTab.addStickerTab(chat);
                }
            } else {
                TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(a);
                TLRPC.Document document = null;
                if (stickerSet.set != null && stickerSet.set.thumb_document_id != 0) {
                    for (int i = 0; i < stickerSet.documents.size(); ++i) {
                        TLRPC.Document d = stickerSet.documents.get(i);
                        if (d != null && stickerSet.set.thumb_document_id == d.id) {
                            document = d;
                            break;
                        }
                    }
                }
                if (document == null) {
                    document = stickerSet.documents.get(0);
                }
                TLObject thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90);
                if (thumb == null || stickerSet.set.gifs) {
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
            if (favTabNum > 0) {
                firstTab = favTabNum;
            } else if (recentTabNum > 0) {
                firstTab = recentTabNum;
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
                TLRPC.Document document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji);
                final View iconTab = gifTabs.addEmojiTab(3 + i, emojiDrawable, document);
               // iconTab.setPadding(hPadding, vPadding, hPadding, vPadding);
                iconTab.setContentDescription(emoji);
            }
        }

        gifTabs.commitUpdate();
        gifTabs.updateTabStyles();

        if (wasRecentTabSelected && !hasRecent) {
            gifTabs.selectTab(gifTrendingTabNum);
            if (gifSearchField != null && gifSearchField.categoriesListView != null) {
                gifSearchField.categoriesListView.selectCategory(gifSearchField.trending);
            }
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
            updateStickerTabs(false);
        }
    }

    public void addRecentGif(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        boolean wasEmpty = recentGifs.isEmpty();
        updateRecentGifs();
        if (wasEmpty) {
            updateStickerTabs(false);
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
                background.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            if (needEmojiSearch) {
                bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            }
        }
        if (emojiTabs != null) {
            emojiTabs.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            emojiTabsShadow.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        }
        if (pickerView != null) {
            Theme.setDrawableColor(pickerView.backgroundDrawable, getThemedColor(Theme.key_dialogBackground));
            Theme.setDrawableColor(pickerView.arrowDrawable, getThemedColor(Theme.key_dialogBackground));
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
            searchField.backgroundView.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
            searchField.shadowView.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            searchField.searchStateDrawable.setColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            Theme.setDrawableColorByKey(searchField.box.getBackground(), Theme.key_chat_emojiSearchBackground);
            searchField.box.invalidate();
            searchField.searchEditText.setHintTextColor(getThemedColor(Theme.key_chat_emojiSearchIcon));
            searchField.searchEditText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        if (dotPaint != null) {
            dotPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelNewTrending));
        }
        if (emojiGridView != null) {
            emojiGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        }
        if (stickersGridView != null) {
            stickersGridView.setGlowColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        }
        if (stickersTab != null) {
            stickersTab.setIndicatorColor(getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            stickersTab.setUnderlineColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            stickersTab.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        }
        if (gifTabs != null) {
            gifTabs.setIndicatorColor(getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine));
            gifTabs.setUnderlineColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
            gifTabs.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        }
        if (backspaceButton != null) {
            backspaceButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
            if (emojiSearchField == null) {
                Theme.setSelectorDrawableColor(backspaceButton.getBackground(), getThemedColor(Theme.key_chat_emojiPanelBackground), false);
                Theme.setSelectorDrawableColor(backspaceButton.getBackground(), getThemedColor(Theme.key_chat_emojiPanelBackground), true);
            }
        }
        if (stickerSettingsButton != null) {
            stickerSettingsButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        }
        if (searchButton != null) {
            searchButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackspace), PorterDuff.Mode.MULTIPLY));
        }
        if (shadowLine != null) {
            shadowLine.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelShadowLine));
        }
        if (mediaBanTooltip != null) {
            ((ShapeDrawable) mediaBanTooltip.getBackground()).getPaint().setColor(getThemedColor(Theme.key_chat_gifSaveHintBackground));
            mediaBanTooltip.setTextColor(getThemedColor(Theme.key_chat_gifSaveHintText));
        }
        if (gifSearchAdapter != null) {
            gifSearchAdapter.progressEmptyView.imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
            gifSearchAdapter.progressEmptyView.textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelEmptyText));
            gifSearchAdapter.progressEmptyView.progressView.setProgressColor(getThemedColor(Theme.key_progressCircle));
        }
        animatedEmojiTextColorFilter = new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN);

        for (int a = 0; a < tabIcons.length; a++) {
            Theme.setEmojiDrawableColor(tabIcons[a], getThemedColor(Theme.key_chat_emojiBottomPanelIcon), false);
            Theme.setEmojiDrawableColor(tabIcons[a], getThemedColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        if (emojiTabs != null) {
            emojiTabs.updateColors();
        }
        for (int a = 0; a < stickerIcons.length; a++) {
            Theme.setEmojiDrawableColor(stickerIcons[a], getThemedColor(Theme.key_chat_emojiPanelIcon), false);
            Theme.setEmojiDrawableColor(stickerIcons[a], getThemedColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        for (int a = 0; a < gifIcons.length; a++) {
            Theme.setEmojiDrawableColor(gifIcons[a], getThemedColor(Theme.key_chat_emojiPanelIcon), false);
            Theme.setEmojiDrawableColor(gifIcons[a], getThemedColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        if (searchIconDrawable != null) {
            Theme.setEmojiDrawableColor(searchIconDrawable, getThemedColor(Theme.key_chat_emojiBottomPanelIcon), false);
            Theme.setEmojiDrawableColor(searchIconDrawable, getThemedColor(Theme.key_chat_emojiPanelIconSelected), true);
        }
        if (searchIconDotDrawable != null) {
            Theme.setEmojiDrawableColor(searchIconDotDrawable, getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine), false);
            Theme.setEmojiDrawableColor(searchIconDotDrawable, getThemedColor(Theme.key_chat_emojiPanelStickerPackSelectorLine), true);
        }
        if (emojiLockPaint != null) {
            emojiLockPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelStickerSetName));
            emojiLockPaint.setAlpha((int) (emojiLockPaint.getAlpha() * .5f));
        }
        if (emojiLockDrawable != null) {
            emojiLockDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelStickerSetName), PorterDuff.Mode.MULTIPLY));
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
                getBackground().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelBackground), PorterDuff.Mode.MULTIPLY));
                if (needEmojiSearch) {
                    bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
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
                setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                if (needEmojiSearch) {
                    bottomTabContainerBackground.setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
                }
                currentBackgroundType = 0;
            }
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        isLayout = false;
        setTranslationY(getTranslationY());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (lastNotifyWidth != right - left) {
            lastNotifyWidth = right - left;
            reloadStickersAdapter();
        }
        super.onLayout(changed, left, top, right, bottom);
        updateBottomTabContainerPosition();
        updateStickerTabsPosition();
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
        updateStickerTabs(false);
    }

    public void invalidateViews() {
        emojiGridView.invalidateViews();
    }

    public void setForseMultiwindowLayout(boolean value) {
        forseMultiwindowLayout = value;
    }

    public void onOpen(boolean forceEmoji) {
        if (currentPage != 0 && stickersBanned) {
            currentPage = 0;
        }
        if (currentPage == 0 && emojiBanned) {
            currentPage = 1;
        }
        if (currentPage == 0 || forceEmoji || currentTabs.size() == 1) {
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
                firstTabUpdate = true;
                if (favTabNum >= 0) {
                    stickersTab.selectTab(favTabNum);
                } else if (recentTabNum >= 0) {
                    stickersTab.selectTab(recentTabNum);
                } else {
                    stickersTab.selectTab(stickersTabOffset);
                }
                firstTabUpdate = false;
                stickersLayoutManager.scrollToPositionWithOffset(0, 0);
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
            if (gifSearchField != null && gifSearchField.categoriesListView != null) {
                gifSearchField.categoriesListView.selectCategory(gifSearchField.recent);
            }
        }
        showBottomTab(true, true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            AndroidUtilities.runOnUIThread(() -> {
                updateStickerTabs(false);
                reloadStickersAdapter();
            });
        }
    }

    @Override
    public void setVisibility(int visibility) {
        boolean changed = getVisibility() != visibility;
        super.setVisibility(visibility);
        if (changed) {
            if (visibility != GONE) {
                Emoji.sortEmoji();
                emojiAdapter.notifyDataSetChanged();
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
                if (stickersGridAdapter != null) {
                    NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
                    updateStickerTabs(false);
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
            if (chooseStickerActionTracker != null) {
                chooseStickerActionTracker.checkVisibility();
            }
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        if (stickersGridAdapter != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
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
            if (UserConfig.getInstance(currentAccount).isPremium()) {
                premiumStickers = MediaDataController.getInstance(currentAccount).getRecentStickers(MediaDataController.TYPE_PREMIUM_STICKERS);
            } else {
                premiumStickers = new ArrayList<>();
            }
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
            if (MessagesController.getInstance(currentAccount).premiumLocked) {
                for (int a = 0; a < favouriteStickers.size(); a++) {
                    if (MessageObject.isPremiumSticker(favouriteStickers.get(a))) {
                        favouriteStickers.remove(a);
                        a--;
                    }
                }
                for (int a = 0; a < recentStickers.size(); a++) {
                    if (MessageObject.isPremiumSticker(recentStickers.get(a))) {
                        recentStickers.remove(a);
                        a--;
                    }
                }
            }
            if (previousCount != recentStickers.size() || previousCount2 != favouriteStickers.size()) {
                updateStickerTabs(false);
            }
            if (stickersGridAdapter != null) {
                stickersGridAdapter.notifyDataSetChanged();
            }
            checkPanels();
        }
    }

    private void updateRecentGifs() {
        final int prevSize = recentGifs.size();
        long prevHash = MediaDataController.calcDocumentsHash(recentGifs, Integer.MAX_VALUE);
        recentGifs = MediaDataController.getInstance(currentAccount).getRecentGifs();
        long newHash = MediaDataController.calcDocumentsHash(recentGifs, Integer.MAX_VALUE);
        if (gifTabs != null && prevSize == 0 && !recentGifs.isEmpty() || prevSize != 0 && recentGifs.isEmpty()) {
            updateGifTabs();
        }
        if ((prevSize != recentGifs.size() || prevHash != newHash) && gifAdapter != null) {
            gifAdapter.notifyDataSetChanged();
        }
    }

    public void setStickersBanned(boolean emojiBanned, boolean stickersBanned, long chatId) {
        if (typeTabs == null) {
            return;
        }
        this.emojiBanned = emojiBanned;
        this.stickersBanned = stickersBanned;
        if (stickersBanned || emojiBanned) {
            currentChatId = chatId;
        } else {
            currentChatId = 0;
        }
        View view = typeTabs.getTab(stickersBanned ? 2 : 0);
        if (view != null) {
            view.setAlpha(currentChatId != 0 ? 0.15f : 1.0f);
            if (stickersBanned) {
                if (currentChatId != 0 && pager.getCurrentItem() != 0) {
                    showBackspaceButton(true, true);
                    showStickerSettingsButton(false, true);
                    pager.setCurrentItem(0, false);
                }
            } else {
                if (currentChatId != 0 && pager.getCurrentItem() != 1) {
                    showBackspaceButton(false, true);
                    showStickerSettingsButton(false, true);
                    pager.setCurrentItem(1, false);
                }
            }
        }
    }

    private AnimatorSet showStickersBanAnimator;
    private Runnable hideStickersBan;
    public void showStickerBanHint(boolean show, boolean emoji, boolean gif) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentChatId);
        if (chat == null) {
            return;
        }

        if (show) {
            if (!ChatObject.hasAdminRights(chat) && chat.default_banned_rights != null && (chat.default_banned_rights.send_stickers || (emoji && chat.default_banned_rights.send_plain))) {
                if (emoji) {
                    mediaBanTooltip.setText(LocaleController.getString("GlobalAttachEmojiRestricted", R.string.GlobalAttachEmojiRestricted));
                } else if (gif) {
                    mediaBanTooltip.setText(LocaleController.getString("GlobalAttachGifRestricted", R.string.GlobalAttachGifRestricted));
                } else {
                    mediaBanTooltip.setText(LocaleController.getString("GlobalAttachStickersRestricted", R.string.GlobalAttachStickersRestricted));
                }
            } else {
                if (chat.banned_rights == null) {
                    return;
                }
                if (AndroidUtilities.isBannedForever(chat.banned_rights)) {
                    if (emoji) {
                        mediaBanTooltip.setText(LocaleController.getString("AttachPlainRestrictedForever", R.string.AttachPlainRestrictedForever));
                    } else if (gif) {
                        mediaBanTooltip.setText(LocaleController.getString("AttachGifRestrictedForever", R.string.AttachGifRestrictedForever));
                    } else {
                        mediaBanTooltip.setText(LocaleController.getString("AttachStickersRestrictedForever", R.string.AttachStickersRestrictedForever));
                    }
                } else {
                    if (emoji) {
                        mediaBanTooltip.setText(LocaleController.formatString("AttachPlainRestricted", R.string.AttachPlainRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                    } if (gif) {
                        mediaBanTooltip.setText(LocaleController.formatString("AttachGifRestricted", R.string.AttachGifRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                    } else {
                        mediaBanTooltip.setText(LocaleController.formatString("AttachStickersRestricted", R.string.AttachStickersRestricted, LocaleController.formatDateForBan(chat.banned_rights.until_date)));
                    }
                }
            }
            mediaBanTooltip.setVisibility(View.VISIBLE);
        }

        if (showStickersBanAnimator != null) {
            showStickersBanAnimator.cancel();
            showStickersBanAnimator = null;
        }

        showStickersBanAnimator = new AnimatorSet();
        showStickersBanAnimator.playTogether(
            ObjectAnimator.ofFloat(mediaBanTooltip, View.ALPHA, show ? mediaBanTooltip.getAlpha() : 1f, show ? 1f : 0f),
            ObjectAnimator.ofFloat(mediaBanTooltip, View.TRANSLATION_Y, show ? AndroidUtilities.dp(12) : mediaBanTooltip.getTranslationY(), show ? 0 : AndroidUtilities.dp(12))
        );
        if (hideStickersBan != null) {
            AndroidUtilities.cancelRunOnUIThread(hideStickersBan);
        }
        if (show) {
            AndroidUtilities.runOnUIThread(hideStickersBan = () -> showStickerBanHint(false, emoji, gif), 3500);
        }
        showStickersBanAnimator.setDuration(320);
        showStickersBanAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        showStickersBanAnimator.start();
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
                        MediaDataController.getInstance(currentAccount).markFeaturedStickersByIdAsRead(false, stickerSetCovered.set.id);
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

    private Runnable updateStickersLoadedDelayed = () -> {
        if (emojiAdapter != null) {
            emojiAdapter.notifyDataSetChanged(true);
        }
    };

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == MediaDataController.TYPE_IMAGE) {
                if (stickersGridAdapter != null) {
                    updateStickerTabs((Boolean) args[1]);
                    updateVisibleTrendingSets();
                    reloadStickersAdapter();
                    checkPanels();
                }
            } else if ((Integer) args[0] == MediaDataController.TYPE_EMOJIPACKS) {
                emojiAdapter.notifyDataSetChanged((Boolean) args[1]);
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
            updateStickerTabs(false);
        } else if (id == NotificationCenter.featuredEmojiDidLoad) {
            if (emojiAdapter != null) {
                emojiAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.groupStickersDidLoad) {
            if (info != null && info.stickerset != null && info.stickerset.id == (Long) args[0]) {
                updateStickerTabs(false);
            }
            if (toInstall.containsKey((Long) args[0]) && args.length >= 2) {
                long packId = (long) args[0];
                TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) args[1];
                Utilities.Callback<TLRPC.TL_messages_stickerSet> onInstalled = toInstall.get(packId);
                if (onInstalled != null && stickerSet != null) {
                    Utilities.Callback callback = toInstall.remove(packId);
                    if (callback != null) {
                        callback.run(stickerSet);
                    }
                }
            }
            AndroidUtilities.cancelRunOnUIThread(updateStickersLoadedDelayed);
            AndroidUtilities.runOnUIThread(updateStickersLoadedDelayed, 100);
        } else if (id == NotificationCenter.emojiLoaded) {
            if (stickersGridView != null) {
                int count = stickersGridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = stickersGridView.getChildAt(a);
                    if (child instanceof StickerSetNameCell || child instanceof StickerEmojiCell) {
                        child.invalidate();
                    }
                }
            }
            if (emojiGridView != null) {
                emojiGridView.invalidate();
                int count = emojiGridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = emojiGridView.getChildAt(a);
                    if (child instanceof ImageViewEmoji) {
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
            if (emojiGridView != null && needEmojiSearch && (emojiSearchField.searchStateDrawable.getIconState() == SearchStateDrawable.State.STATE_PROGRESS || emojiGridView.getAdapter() == emojiSearchAdapter) && !TextUtils.isEmpty(emojiSearchAdapter.lastSearchEmojiString)) {
                emojiSearchAdapter.search(emojiSearchAdapter.lastSearchEmojiString);
            }
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            if (emojiAdapter != null) {
                emojiAdapter.notifyDataSetChanged();
            }
            updateEmojiHeaders();
            updateStickerTabs(false);
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private class TrendingAdapter extends RecyclerListView.SelectionAdapter {

        private boolean emoji;
        public TrendingAdapter(boolean emoji) {
            this.emoji = emoji;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            BackupImageView imageView = new BackupImageView(getContext()) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (!emoji) {
                        TLRPC.StickerSetCovered set = (TLRPC.StickerSetCovered) getTag();
                        if (MediaDataController.getInstance(currentAccount).isStickerPackUnread(emoji, set.set.id) && dotPaint != null) {
                            int x = canvas.getWidth() - AndroidUtilities.dp(8);
                            int y = AndroidUtilities.dp(14);
                            canvas.drawCircle(x, y, AndroidUtilities.dp(3), dotPaint);
                        }
                    }
                }
            };
            imageView.setSize(AndroidUtilities.dp(emoji ? 24 : 30), AndroidUtilities.dp(emoji ? 24 : 30));
            imageView.setLayerNum(1);
            imageView.setAspectFit(true);
            imageView.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(emoji ? 34 : 42), AndroidUtilities.dp(emoji ? 34 : 42)));
            return new RecyclerListView.Holder(imageView);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            BackupImageView imageView = (BackupImageView) holder.itemView;
            TLRPC.StickerSetCovered set = (emoji ? featuredEmojiSets : featuredStickerSets).get(position);
            imageView.setTag(set);

            ArrayList<TLRPC.Document> setDocuments;
            if (set instanceof TLRPC.TL_stickerSetFullCovered) {
                setDocuments = ((TLRPC.TL_stickerSetFullCovered) set).documents;
            } else if (set instanceof TLRPC.TL_stickerSetNoCovered) {
                TLRPC.TL_messages_stickerSet fullSet = MediaDataController.getInstance(currentAccount).getStickerSet(MediaDataController.getInputStickerSet(set.set), false);
                if (fullSet == null) {
                    setDocuments = null;
                } else {
                    setDocuments = fullSet.documents;
                }
            } else {
                setDocuments = set.covers;
            }

            TLRPC.Document document = null;
            if (set.cover != null) {
                document = set.cover;
            } else if (setDocuments != null && !setDocuments.isEmpty()) {
                if (set.set != null) {
                    for (int i = 0; i < setDocuments.size(); ++i) {
                        if (setDocuments.get(i).id == set.set.thumb_document_id) {
                            document = setDocuments.get(i);
                            break;
                        }
                    }
                }
                if (document == null) {
                    document = setDocuments.get(0);
                }
            }
            if (document == null) {
                return;
            }

            if (this.emoji) {
                imageView.setColorFilter(MessageObject.isTextColorEmoji(document) ? Theme.chat_animatedEmojiTextColorFilter : null);
            }

            TLObject object = FileLoader.getClosestPhotoSizeWithSize(set.set.thumbs, 90);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(set.set.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }
            if (object == null || MessageObject.isVideoSticker(document)) {
                object = document;
            }

            ImageLocation imageLocation;
            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, document);
            } else if (object instanceof TLRPC.PhotoSize) {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                int thumbVersion = set.set.thumb_version;
                imageLocation = ImageLocation.getForSticker(thumb, document, thumbVersion);
            } else {
                return;
            }

            if (imageLocation == null) {
                return;
            }
            String filter = !LiteMode.isEnabled(emoji ? LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD : LiteMode.FLAG_ANIMATED_STICKERS_KEYBOARD) ? "30_30_firstframe" : "30_30";
            if (object instanceof TLRPC.Document && (MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isVideoSticker(document))) {
                if (svgThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), filter, svgThumb, 0, set);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), filter, imageLocation, null, 0, set);
                }
            } else if (imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                imageView.setImage(imageLocation, filter, "tgs", svgThumb, set);
            } else {
                imageView.setImage(imageLocation, null, "webp", svgThumb, set);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getItemCount() {
            return (emoji ? featuredEmojiSets : featuredStickerSets).size();
        }
    }

    private class TrendingListView extends RecyclerListView {
        public TrendingListView(Context context, Adapter adapter) {
            super(context);

            setNestedScrollingEnabled(true);
            setSelectorRadius(AndroidUtilities.dp(4));
            setSelectorDrawableColor(getThemedColor(Theme.key_listSelector));
            setTag(9);
            setItemAnimator(null);
            setLayoutAnimation(null);

            LinearLayoutManager layoutManager = new LinearLayoutManager(context) {
                @Override
                public boolean supportsPredictiveItemAnimations() {
                    return false;
                }
            };
            layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            setLayoutManager(layoutManager);
            setAdapter(adapter);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (getParent() != null && getParent().getParent() != null) {
                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                pager.requestDisallowInterceptTouchEvent(true);
            }
            return super.onInterceptTouchEvent(e);
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
            return holder.itemView instanceof RecyclerListView;
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
                    if ("trend1".equals(object)) {
                        return 5;
                    } else if ("trend2".equals(object)) {
                        return 6;
                    }
                    return 3;
                } else {
                    return 2;
                }
            }
            return 1;
        }

        public int getTabForPosition(int position) {
            Object cacheObject = cache.get(position);
            if ("search".equals(cacheObject) || "trend1".equals(cacheObject) || "trend2".equals(cacheObject)) {
                if (favTabNum >= 0) {
                    return favTabNum;
                }
                if (recentTabNum >= 0) {
                    return recentTabNum;
                }
                return 0;
            }
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
                if ("premium".equals(pack)) {
                    return premiumTabNum;
                } else if ("recent".equals(pack)) {
                    return recentTabNum;
                } else {
                    return favTabNum;
                }
            } else {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) pack;
                int idx = stickerSets.indexOf(set);
                return idx + stickersTabOffset;
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerEmojiCell(context, true) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    StickerSetNameCell nameCell = new StickerSetNameCell(context, false, resourcesProvider);
                    view = nameCell;
                    nameCell.setOnIconClickListener(v -> {
                        if (stickersGridView.indexOfChild(nameCell) == -1) {
                            return;
                        }
                        RecyclerView.ViewHolder holder = stickersGridView.getChildViewHolder(nameCell);
                        if (holder != null) {
                            if (holder.getAdapterPosition() == groupStickerPackPosition) {
                                if (groupStickerSet != null) {
                                    if (delegate != null) {
                                        delegate.onStickersGroupClick(info.id);
                                    }
                                } else {
                                    MessagesController.getEmojiSettings(currentAccount).edit().putLong("group_hide_stickers_" + info.id, info.stickerset != null ? info.stickerset.id : 0).apply();
                                    updateStickerTabs(false);
                                    if (stickersGridAdapter != null) {
                                        stickersGridAdapter.notifyDataSetChanged();
                                    }
                                }
                            } else {
                                Object object = cache.get(holder.getAdapterPosition());
                                if (object == recentStickers) {
                                    AlertDialog alertDialog = new AlertDialog.Builder(context)
                                            .setTitle(LocaleController.getString(R.string.ClearRecentStickersAlertTitle))
                                            .setMessage(LocaleController.getString(R.string.ClearRecentStickersAlertMessage))
                                            .setPositiveButton(LocaleController.getString(R.string.ClearButton), (dialog, which) -> MediaDataController.getInstance(currentAccount).clearRecentStickers())
                                            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                                            .create();
                                    alertDialog.show();
                                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                    if (button != null) {
                                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
                                    }
                                }
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
                case 5:
                    view = new StickerSetNameCell(context, false, resourcesProvider);
                    ((StickerSetNameCell) view).setOnIconClickListener(v -> {
                        MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                        ArrayList<TLRPC.StickerSetCovered> featured = mediaDataController.getFeaturedStickerSets();
                        if (!featured.isEmpty()) {
                            MessagesController.getEmojiSettings(currentAccount).edit().putLong("featured_hidden", featured.get(0).set.id).commit();
                            if (stickersGridAdapter != null) {
                                stickersGridAdapter.notifyItemRangeRemoved(1, 2);
                            }
                            updateStickerTabs(false);
                        }
                    });
                    break;
                case 6:
                    TrendingListView listView = new TrendingListView(context, trendingAdapter = new TrendingAdapter(false));
                    listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                        @Override
                        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                            outRect.right = AndroidUtilities.dp(8);
                        }
                    });
                    listView.setOnItemClickListener((view1, position) -> {
                        openTrendingStickers((TLRPC.StickerSetCovered) view1.getTag());
                    });
                    view = listView;
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52)));
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
                            icon = groupStickerSet != null ? R.drawable.msg_mini_customize : R.drawable.msg_close;
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
                            cell.setText(LocaleController.getString("RecentStickers", R.string.RecentStickers), R.drawable.msg_close, LocaleController.getString(R.string.ClearRecentStickersAlertTitle));
                        } else if (object == favouriteStickers) {
                            cell.setText(LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers), 0);
                        } else if (object == premiumStickers) {
                            cell.setText(LocaleController.getString("PremiumStickers", R.string.PremiumStickers), 0);
                        }
                    }
                    break;
                }
                case 3: {
                    StickerSetGroupInfoCell cell = (StickerSetGroupInfoCell) holder.itemView;
                    cell.setIsLast(position == totalItems - 1);
                    break;
                }
                case 5: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    cell.setText(MediaDataController.getInstance(currentAccount).loadFeaturedPremium ? LocaleController.getString("FeaturedStickersPremium", R.string.FeaturedStickersPremium) : LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), R.drawable.msg_close, LocaleController.getString("AccDescrCloseTrendingStickers", R.string.AccDescrCloseTrendingStickers));
                    break;
                }
            }
        }

        private void updateItems() {
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
            for (int a = -5; a < packs.size(); a++) {
                ArrayList<TLRPC.Document> documents;
                TLRPC.TL_messages_stickerSet pack = null;
                String key;
                if (a == -5) {
                    cache.put(totalItems++, "search");
                    startRow++;
                    continue;
                } else if (a == -4) {
                    MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                    SharedPreferences preferences = MessagesController.getEmojiSettings(currentAccount);
                    ArrayList<TLRPC.StickerSetCovered> featured = mediaDataController.getFeaturedStickerSets();
                    if (!featuredStickerSets.isEmpty() && preferences.getLong("featured_hidden", 0) != featured.get(0).set.id) {
                        cache.put(totalItems++, "trend1");
                        cache.put(totalItems++, "trend2");
                        startRow += 2;
                    }
                    continue;
                } else if (a == -3) {
                    documents = favouriteStickers;
                    packStartPosition.put(key = "fav", totalItems);
                } else if (a == -2) {
                    documents = recentStickers;
                    packStartPosition.put(key = "recent", totalItems);
                } else if (a == -1) {
                    continue;
//                    documents = premiumStickers;
//                    packStartPosition.put(key = "premium", totalItems);
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
                        if (a == -1) {
                            rowStartPack.put(startRow + b, "premium");
                        } else if (a == -2) {
                            rowStartPack.put(startRow + b, "recent");
                        } else {
                            rowStartPack.put(startRow + b, "fav");
                        }
                    }
                }
                totalItems += count * stickersPerRow + 1;
                startRow += count + 1;
            }
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateItems();
            super.notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        public void notifyDataSetChanged() {
            updateItems();
            super.notifyDataSetChanged();
        }
    }

    public static class EmojiPackExpand extends FrameLayout {
        public TextView textView;

        public EmojiPackExpand(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(11), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_emojiPanelStickerSetName, resourcesProvider), 99)));
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1.66f), AndroidUtilities.dp(6), AndroidUtilities.dp(2f));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    public static class CustomEmoji {
        public TLRPC.TL_messages_stickerSet stickerSet;
        public long documentId;
        public String emoticon;

        public TLRPC.Document getDocument() {
            if (stickerSet == null || stickerSet.documents == null) {
                return null;
            }
            for (int i = 0; i < stickerSet.documents.size(); ++i) {
                TLRPC.Document document = stickerSet.documents.get(i);
                if (document != null && document.id == documentId) {
                    return document;
                }
            }
            return null;
        }
    }

    public static class EmojiPack {
        public int index;
        public TLRPC.StickerSet set;
        public ArrayList<TLRPC.Document> documents = new ArrayList<>();
        public boolean free;
        public boolean installed;
        public boolean featured;
        public boolean expanded;
    }

    private class EmojiGridAdapter extends RecyclerListView.SelectionAdapter {

        private static final int VIEW_TYPE_EMOJI = 0;
        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_SEARCH = 2;
        private static final int VIEW_TYPE_UNLOCK = 3;
        private static final int VIEW_TYPE_TRENDING = 4;
        private static final int VIEW_TYPE_PACK_HEADER = 5;
        private static final int VIEW_TYPE_EXPAND = 6;

        private int trendingHeaderRow = -1;
        private int trendingRow = -1;
        private int firstTrendingRow = -1;
        private int recentlyUsedHeaderRow = -1;

        private ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks;
        private ArrayList<Integer> rowHashCodes = new ArrayList<>();
        private SparseIntArray positionToSection = new SparseIntArray();
        private SparseIntArray sectionToPosition = new SparseIntArray();
        private SparseIntArray positionToUnlock = new SparseIntArray();
        private SparseIntArray positionToExpand = new SparseIntArray();
        private ArrayList<Integer> packStartPosition = new ArrayList<>();
        private int itemCount;
        public int plainEmojisCount;

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
            int type = holder.getItemViewType();
            return type == VIEW_TYPE_EMOJI || type == VIEW_TYPE_TRENDING || type == VIEW_TYPE_UNLOCK || type == VIEW_TYPE_EXPAND;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_EMOJI:
                    view = new ImageViewEmoji(getContext());
                    break;
                case VIEW_TYPE_HEADER:
                    view = new StickerSetNameCell(getContext(), true, resourcesProvider);
                    ((StickerSetNameCell) view).setOnIconClickListener(e -> {
                        if (featuredEmojiSets == null || featuredEmojiSets.isEmpty() || featuredEmojiSets.get(0).set == null) {
                            return;
                        }
                        long lastSetId = featuredEmojiSets.get(0).set.id;
                        MessagesController.getEmojiSettings(currentAccount).edit().putLong("emoji_featured_hidden", lastSetId).commit();
                        if (emojiAdapter != null) {
                            emojiAdapter.notifyItemRangeRemoved(1, 3);
                        }
                        if (emojiTabs != null) {
                            emojiTabs.updateEmojiPacks(getEmojipacks());
                        }
                        updateRows();
                    });
                    break;
                case VIEW_TYPE_PACK_HEADER:
                    view = new EmojiPackHeader(getContext());
                    break;
                case VIEW_TYPE_TRENDING:
                    TrendingListView listView = new TrendingListView(getContext(), trendingEmojiAdapter = new TrendingAdapter(true));
                    listView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), 0);
                    listView.setClipToPadding(false);
                    listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                        @Override
                        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                            outRect.right = AndroidUtilities.dp(2);
                        }
                    });
                    listView.setOnItemClickListener((item, position) -> {
                        if (item.getTag() instanceof TLRPC.StickerSetCovered) {
                            TLRPC.StickerSetCovered highlightSet = (TLRPC.StickerSetCovered) item.getTag();
                            ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
                            ArrayList<TLRPC.StickerSetCovered> sets = MediaDataController.getInstance(currentAccount).getFeaturedEmojiSets();
                            int highlight = -1;
                            for (int i = 0; i < sets.size(); ++i) {
                                TLRPC.StickerSetCovered set = sets.get(i);
                                if (set != null && set.set != null) {
                                    TLRPC.TL_inputStickerSetID inputStickerSet = new TLRPC.TL_inputStickerSetID();
                                    inputStickerSet.id = set.set.id;
                                    inputStickerSet.access_hash = set.set.access_hash;
                                    inputStickerSets.add(inputStickerSet);

                                    if (highlightSet != null && highlightSet.set != null && highlightSet.set.id == set.set.id) {
                                        highlight = i;
                                    }
                                }
                            }

                            MediaDataController.getInstance(currentAccount).markFeaturedStickersAsRead(true, true);
                            EmojiPacksAlert alert = new EmojiPacksAlert(fragment, getContext(), fragment == null ? null : fragment.getResourceProvider(), inputStickerSets);
                            if (highlight >= 0) {
                                alert.highlight(highlight);
                            }
                            if (fragment != null) {
                                fragment.showDialog(alert);
                            } else {
                                alert.show();
                            }
                        }
                    });
                    view = listView;
                    break;
                case VIEW_TYPE_UNLOCK:
                    view = new EmojiPackButton(getContext());
                    break;
                case VIEW_TYPE_EXPAND:
                    view = new EmojiPackExpand(getContext(), resourcesProvider);
                    break;
                case VIEW_TYPE_SEARCH:
                default:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
            int index;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_EMOJI: {
                    ImageViewEmoji imageView = (ImageViewEmoji) holder.itemView;
                    imageView.position = position;
                    imageView.pack = null;

                    String code;
                    String coloredCode;
                    boolean recent;
                    TLRPC.Document customEmoji = null;
                    Long customEmojiId = null;
                    if (needEmojiSearch) {
                        position--;
                    }
                    if (recentlyUsedHeaderRow >= 0) {
                        position--;
                    }
                    if (trendingRow >= 0) {
                        position -= 2;
                    }

                    int count = getRecentEmoji().size();
                    if (position < count) {
                        coloredCode = code = getRecentEmoji().get(position);
                        if (code != null && code.startsWith("animated_")) {
                            try {
                                customEmojiId = Long.parseLong(code.substring(9));
                                code = null;
                                coloredCode = null;
                            } catch (Exception ignore) {}
                        }
                        recent = true;
                    } else {
                        code = null;
                        coloredCode = null;
                        for (int a = 0; a < EmojiData.dataColored.length; a++) {
                            int size = EmojiData.dataColored[a].length + 1;
                            if (position - count - 1 >= 0 && position < count + size) {
                                coloredCode = code = EmojiData.dataColored[a][position - count - 1];
                                String color = Emoji.emojiColor.get(code);
                                if (color != null) {
                                    coloredCode = addColorToCode(coloredCode, color);
                                }
                                break;
                            }
                            count += size;
                        }
                        if (code == null) {
                            final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                            final int maxlen = emojiLayoutManager.getSpanCount() * 3;
                            for (int b = 0; b < packStartPosition.size(); ++b) {
                                EmojiPack pack = emojipacksProcessed.get(b);
                                int start = packStartPosition.get(b) + 1;
                                int stickersCount = ((pack.installed && !pack.featured) && (pack.free || isPremium) || pack.expanded ? pack.documents.size() : Math.min(maxlen, pack.documents.size()));
                                if (imageView.position >= start && imageView.position - start < stickersCount) {
                                    imageView.pack = pack;
                                    customEmoji = pack.documents.get(imageView.position - start);
                                    customEmojiId = customEmoji == null ? null : customEmoji.id;
                                    break;
                                }
                            }
                        }
                        recent = false;
                    }
                    if (customEmojiId != null) {
                        imageView.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
                    } else {
                        imageView.setPadding(0, 0, 0, 0);
                    }
                    if (customEmojiId != null) {
                        imageView.setImageDrawable(null, recent);
                        if (imageView.getSpan() == null || imageView.getSpan().getDocumentId() != customEmojiId) {
                            if (customEmoji != null) {
                                imageView.setSpan(new AnimatedEmojiSpan(customEmoji, null));
                            } else {
                                imageView.setSpan(new AnimatedEmojiSpan(customEmojiId, null));
                            }
                        }
                    } else {
                        imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode), recent);
                        imageView.setSpan(null);
                    }
                    imageView.setTag(code);
                    imageView.setContentDescription(coloredCode);
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    cell.position = position;
                    index = positionToSection.get(position);
                    if (position == trendingHeaderRow) {
                        cell.setText(LocaleController.getString("FeaturedEmojiPacks", R.string.FeaturedEmojiPacks), R.drawable.msg_close, LocaleController.getString("AccDescrCloseTrendingEmoji", R.string.AccDescrCloseTrendingEmoji));
                    } else if (position == recentlyUsedHeaderRow) {
                        cell.setText(LocaleController.getString("RecentlyUsed", R.string.RecentlyUsed), 0);
                    } else if (index >= emojiTitles.length) {
                        try {
                            cell.setText(emojipacksProcessed.get(index - emojiTitles.length).set.title, 0);
                        } catch (Exception ignore) {
                            cell.setText("", 0);
                        }
                    } else {
                        cell.setText(emojiTitles[index], 0);
                    }
                    break;
                }
                case VIEW_TYPE_EXPAND:
                    EmojiPackExpand button = (EmojiPackExpand) holder.itemView;
                    final int i = positionToExpand.get(position);
                    final int maxlen = emojiLayoutManager.getSpanCount() * 3;
                    final EmojiPack pack = i >= 0 && i < emojipacksProcessed.size() ? emojipacksProcessed.get(i) : null;
                    if (pack != null) {
                        button.textView.setText("+" + (pack.documents.size() - maxlen + 1));
                    }
                    break;
//                case VIEW_TYPE_UNLOCK:
//                    EmojiPackButton expandButton = (EmojiPackButton) holder.itemView;
//                    expandButton.position = position;
//                    index = positionToUnlock.get(position);
//                    final EmojiPack expandPack = index >= 0 && index < emojipacksProcessed.size() ? emojipacksProcessed.get(index) : null;
//                    if (expandPack != null && expandButton != null) {
//                        final boolean unlock = !expandPack.free && !UserConfig.getInstance(currentAccount).isPremium();
//                        final boolean installed = expandPack.installed || keepFeaturedDuplicate.contains(expandPack.set.id);
//                        expandButton.set(expandPack.set.title, unlock, installed, e -> {
//                            if (unlock) {
//                                openPremiumAnimatedEmojiFeature();
//                            } else {
//                                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetById(expandPack.set.id);
//                                if (stickerSet == null || stickerSet.set == null) {
//                                    toInstall.put(expandPack.set.id, newPack -> {
//                                        if (newPack == null) {
//                                            return;
//                                        }
//                                        keepFeaturedDuplicate.add(newPack.set.id);
//                                        EmojiPacksAlert.installSet(fragment, newPack, true, () -> {
//                                            expandButton.updateInstall(true, true);
//                                        });
//                                    });
//                                    TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
//                                    inputStickerSetID.id = expandPack.set.id;
//                                    inputStickerSetID.access_hash = expandPack.set.access_hash;
//                                    MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetID, false);
//                                } else {
//                                    keepFeaturedDuplicate.add(stickerSet.set.id);
//                                    EmojiPacksAlert.installSet(fragment, stickerSet, true, () -> {
//                                        expandButton.updateInstall(true, true);
//                                    });
//                                }
//                            }
//                        });
//                    }
//                    break;
                case VIEW_TYPE_PACK_HEADER:
                    EmojiPackHeader header = (EmojiPackHeader) holder.itemView;
                    int section = positionToSection.get(position);
                    int a = section - emojiTitles.length;
                    EmojiPack pack2 = emojipacksProcessed.get(a);
                    EmojiPack before = a - 1 >= 0 ? emojipacksProcessed.get(a - 1) : null;
                    boolean divider = pack2 != null && pack2.featured && !(before != null && !before.free && before.installed && !UserConfig.getInstance(currentAccount).isPremium());
                    header.setStickerSet(pack2, divider);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == trendingRow) {
                return VIEW_TYPE_TRENDING;
            } else if (position == trendingHeaderRow || position == recentlyUsedHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (positionToSection.indexOfKey(position) >= 0) {
                return positionToSection.get(position) >= EmojiData.dataColored.length ? VIEW_TYPE_PACK_HEADER : VIEW_TYPE_HEADER;
            } else if (needEmojiSearch && position == 0) {
                return VIEW_TYPE_SEARCH;
            } else if (positionToUnlock.indexOfKey(position) >= 0) {
                return VIEW_TYPE_UNLOCK;
            } else if (positionToExpand.indexOfKey(position) >= 0) {
                return VIEW_TYPE_EXPAND;
            }
            return VIEW_TYPE_EMOJI;
        }

        public void processEmoji(boolean updateEmojipacks) {
            emojipacksProcessed.clear();
            if (!allowAnimatedEmoji) {
                return;
            }

            MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            if (updateEmojipacks || frozenEmojiPacks == null) {
                frozenEmojiPacks = new ArrayList<>(mediaDataController.getStickerSets(MediaDataController.TYPE_EMOJIPACKS));
            }
            ArrayList<TLRPC.TL_messages_stickerSet> installedEmojipacks = frozenEmojiPacks;
            boolean isPremium = UserConfig.getInstance(currentAccount).isPremium() || allowEmojisForNonPremium;
            int index = 0;
            if (!isPremium) {
                for (int i = 0; i < installedEmojipacks.size(); ++i) {
                    TLRPC.TL_messages_stickerSet set = installedEmojipacks.get(i);
                    if (set != null && !MessageObject.isPremiumEmojiPack(set)) {
                        EmojiPack pack = new EmojiPack();
                        pack.index = index++;
                        pack.set = set.set;
                        pack.documents = new ArrayList<>(set.documents);
                        pack.free = true;
                        pack.installed = mediaDataController.isStickerPackInstalled(set.set.id);
                        pack.featured = false;
                        pack.expanded = true;
                        emojipacksProcessed.add(pack);
                        installedEmojipacks.remove(i--);
                    }
                }
            }
            for (int i = 0; i < installedEmojipacks.size(); ++i) {
                TLRPC.TL_messages_stickerSet set = installedEmojipacks.get(i);
                if (isPremium) {
                    EmojiPack pack = new EmojiPack();
                    pack.index = index++;
                    pack.set = set.set;
                    pack.documents = set.documents;
                    pack.free = false;
                    pack.installed = mediaDataController.isStickerPackInstalled(set.set.id);
                    pack.featured = false;
                    pack.expanded = true;
                    emojipacksProcessed.add(pack);
                } else {
                    ArrayList<TLRPC.Document> freeEmojis = new ArrayList<>();
                    ArrayList<TLRPC.Document> premiumEmojis = new ArrayList<>();
                    if (set != null && set.documents != null) {
                        for (int j = 0; j < set.documents.size(); ++j) {
                            if (MessageObject.isFreeEmoji(set.documents.get(j))) {
                                freeEmojis.add(set.documents.get(j));
                            } else {
                                premiumEmojis.add(set.documents.get(j));
                            }
                        }
                    }
                    if (freeEmojis.size() > 0) {
                        EmojiPack pack = new EmojiPack();
                        pack.index = index++;
                        pack.set = set.set;
                        pack.documents = new ArrayList<>(freeEmojis);
                        pack.free = true;
                        pack.installed = mediaDataController.isStickerPackInstalled(set.set.id);
                        pack.featured = false;
                        pack.expanded = true;
                        emojipacksProcessed.add(pack);
                    }
                    if (premiumEmojis.size() > 0) {
                        EmojiPack pack = new EmojiPack();
                        pack.index = index++;
                        pack.set = set.set;
                        pack.documents = new ArrayList<>(premiumEmojis);
                        pack.free = false;
                        pack.installed = mediaDataController.isStickerPackInstalled(set.set.id);
                        pack.featured = false;
                        pack.expanded = expandedEmojiSets.contains(pack.set.id);
                        emojipacksProcessed.add(pack);
                    }
                }
            }
            for (int i = 0; i < featuredEmojiSets.size(); ++i) {
                TLRPC.StickerSetCovered set = featuredEmojiSets.get(i);
//                if (!isPremium && !MessageObject.isPremiumEmojiPack(set) && mediaDataController.isStickerPackInstalled(set.set.id)) {
//                    continue;
//                }
                EmojiPack pack = new EmojiPack();

                pack.installed = mediaDataController.isStickerPackInstalled(set.set.id);
                pack.set = set.set;
                if (set instanceof TLRPC.TL_stickerSetFullCovered) {
                    pack.documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
                } else if (set instanceof TLRPC.TL_stickerSetNoCovered) {
                    TLRPC.TL_messages_stickerSet stickerSet = mediaDataController.getStickerSet(MediaDataController.getInputStickerSet(set.set), set.set.hash, false);
                    if (stickerSet != null) {
                        pack.documents = stickerSet.documents;
                    }
                } else {
                    pack.documents = set.covers;
                }
                pack.index = index++;
                boolean premium = false;
                for (int j = 0; j < pack.documents.size(); ++j) {
                    if (!MessageObject.isFreeEmoji(pack.documents.get(j))) {
                        premium = true;
                        break;
                    }
                }
                pack.free = !premium;
                pack.expanded = expandedEmojiSets.contains(pack.set.id);
                pack.featured = true;
                emojipacksProcessed.add(pack);
            }

            if (emojiTabs != null) {
                emojiTabs.updateEmojiPacks(getEmojipacks());
            }
        }

        public void expand(int position, View expandButton) {
            int index = positionToExpand.get(position);
            if (index < 0 || index >= emojipacksProcessed.size()) {
                return;
            }
            EmojiPack pack = emojipacksProcessed.get(index);
            if (pack.expanded) {
                return;
            }
            final boolean last = index + 1 == emojipacksProcessed.size();

            int start = packStartPosition.get(index);
            expandedEmojiSets.add(pack.set.id);

            boolean isPremium = UserConfig.getInstance(currentAccount).isPremium() || allowEmojisForNonPremium;
            int maxlen = emojiLayoutManager.getSpanCount() * 3;
            int fromCount = ((pack.installed && !pack.featured) && (pack.free || isPremium) || pack.expanded ? pack.documents.size() : Math.min(maxlen, pack.documents.size()));
            Integer from = null, count = null;
            if (pack.documents.size() > maxlen) {
                from = start + 1 + fromCount;
            }
            pack.expanded = true;
            int toCount = pack.documents.size();
            if (toCount - fromCount > 0) {
                from = start + 1 + fromCount;
                count = toCount - fromCount;
            }

            processEmoji(false);
            updateRows();

            if (from != null && count != null) {
                animateExpandFromButton = expandButton;
                animateExpandFromPosition = from;
                animateExpandToPosition = from + count;
                animateExpandStartTime = SystemClock.elapsedRealtime();
//                notifyItemChanged(from - 1);
//                notifyItemRangeInserted(from, count);
                notifyItemRangeInserted(from, count);
                notifyItemChanged(from);

                if (last) {
                    final int scrollTo = from;
                    final float durationMultiplier = count > maxlen / 2 ? 1.5f : 4f;
                    post(() -> {
                        try {
                            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(emojiGridView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE, durationMultiplier);
                            linearSmoothScroller.setTargetPosition(scrollTo);
                            emojiLayoutManager.startSmoothScroll(linearSmoothScroller);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
            }
        }

        public void updateRows() {
            positionToSection.clear();
            sectionToPosition.clear();
            positionToUnlock.clear();
            positionToExpand.clear();
            packStartPosition.clear();
            rowHashCodes.clear();
            itemCount = 0;
            boolean isPremium = UserConfig.getInstance(currentAccount).isPremium() || allowEmojisForNonPremium;
            if (needEmojiSearch) {
                itemCount++;
                rowHashCodes.add(-1);
            }
            if (isPremium && allowAnimatedEmoji && featuredEmojiSets.size() > 0 && featuredEmojiSets.get(0).set != null && MessagesController.getEmojiSettings(currentAccount).getLong("emoji_featured_hidden", 0) != featuredEmojiSets.get(0).set.id) {
                trendingHeaderRow = itemCount++;
                trendingRow = itemCount++;
                recentlyUsedHeaderRow = itemCount++;
            } else {
                trendingHeaderRow = -1;
                trendingRow = -1;
                recentlyUsedHeaderRow = -1;
            }
            ArrayList<String> recent = getRecentEmoji();
            if (emojiTabs != null) {
                emojiTabs.showRecent(!recent.isEmpty());
            }
            itemCount += recent.size();
            for (int i = 0; i < recent.size(); ++i) {
                rowHashCodes.add(Objects.hash(-43263, recent.get(i)));
            }
            int k = 0;
            for (int a = 0; a < EmojiData.dataColored.length; ++a, ++k) {
                positionToSection.put(itemCount, k);
                sectionToPosition.put(k, itemCount);
                itemCount += EmojiData.dataColored[a].length + 1;
                rowHashCodes.add(Objects.hash(43245, a));
                for (int i = 0; i < EmojiData.dataColored[a].length; ++i) {
                    rowHashCodes.add(EmojiData.dataColored[a][i].hashCode());
                }
            }

            int maxlen = emojiLayoutManager.getSpanCount() * 3;
            plainEmojisCount = itemCount;
            firstTrendingRow = -1;

            if (emojipacksProcessed != null) {
                for (int b = 0; b < emojipacksProcessed.size(); ++b, ++k) {
                    positionToSection.put(itemCount, k);
                    sectionToPosition.put(k, itemCount);
                    packStartPosition.add(itemCount);

                    EmojiPack pack = emojipacksProcessed.get(b);
                    if (pack.featured && firstTrendingRow < 0) {
                        firstTrendingRow = itemCount;
                    }
                    int count = 1 + ((pack.installed && !pack.featured) && (pack.free || isPremium) || pack.expanded ? pack.documents.size() : Math.min(maxlen, pack.documents.size()));
                    if (!pack.expanded && pack.documents.size() > maxlen) {
                        count--;
                    }
                    rowHashCodes.add(Objects.hash(pack.featured ? 56345 : -645231, (pack.set == null ? b : pack.set.id)));
                    for (int i = 1; i < count; ++i) {
                        rowHashCodes.add(Objects.hash(pack.featured ? 3442 : 3213, pack.documents.get(i - 1).id));
                    }
                    itemCount += count;
                    if (!pack.expanded && pack.documents.size() > maxlen) {
                        positionToExpand.put(itemCount, b);
                        rowHashCodes.add(Objects.hash(-65174, pack.set.id));
                        itemCount++;
                    }
//                    if (!pack.installed) {
//                        positionToUnlock.put(itemCount, b);
//                        itemCount++;
//                        rowHashCodes.add(Objects.hash(7, pack.set.id));
//                    }
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            notifyDataSetChanged(false);
        }

        public void notifyDataSetChanged(boolean updateEmojipack) {
            ArrayList<Integer> prevRowHashCodes = new ArrayList<>(rowHashCodes);

            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            ArrayList<TLRPC.StickerSetCovered> featured = mediaDataController.getFeaturedEmojiSets();
            featuredEmojiSets.clear();
            for (int a = 0, N = featured.size(); a < N; a++) {
                TLRPC.StickerSetCovered set = featured.get(a);
                if (!mediaDataController.isStickerPackInstalled(set.set.id) || installedEmojiSets.contains(set.set.id)) {
                    featuredEmojiSets.add(set);
                }
            }

            processEmoji(updateEmojipack);
            updateRows();
            if (trendingEmojiAdapter != null) {
                trendingEmojiAdapter.notifyDataSetChanged();
            }
//            super.notifyDataSetChanged();

            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return prevRowHashCodes.size();
                }

                @Override
                public int getNewListSize() {
                    return rowHashCodes.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return prevRowHashCodes.get(oldItemPosition).equals(rowHashCodes.get(newItemPosition));
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }, false).dispatchUpdatesTo(this);
        }
    }

    public ArrayList<EmojiPack> getEmojipacks() {
        ArrayList<EmojiPack> packs = new ArrayList<>();
        for (int i = 0; i < emojipacksProcessed.size(); ++i) {
            EmojiPack pack = emojipacksProcessed.get(i);
            if (!pack.featured && (pack.installed || installedEmojiSets.contains(pack.set.id)) || pack.featured && !(pack.installed || installedEmojiSets.contains(pack.set.id))) {
                packs.add(pack);
            }
        }
        return packs;
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
                return getRecentEmoji().size() + 1;
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
                    textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelEmptyText));
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                    ImageView imageView = new ImageView(getContext());
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(R.drawable.msg_emoji_question);
                    imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
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
                            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 24, 0, 0));

                            textView = new TextView(getContext());
                            textView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EmojiSuggestionsInfo", R.string.EmojiSuggestionsInfo)));
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 11, 0, 0));

                            textView = new TextView(getContext());
                            textView.setText(LocaleController.formatString("EmojiSuggestionsUrl", R.string.EmojiSuggestionsUrl, lastSearchAlias != null ? lastSearchAlias : lastSearchKeyboardLanguage));
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                            textView.setTextColor(getThemedColor(Theme.key_dialogTextLink));
                            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 18, 0, 16));
                            textView.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (loadingUrl[0]) {
                                        return;
                                    }
                                    loadingUrl[0] = true;
                                    final AlertDialog progressDialog[] = new AlertDialog[]{new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER)};

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
                    imageView.position = position;
                    imageView.pack = null;

                    String code;
                    String coloredCode;
                    boolean recent;
                    position--;
                    Long customEmojiId = null;

                    if (result.isEmpty() && !searchWas) {
                        coloredCode = code = getRecentEmoji().get(position);
                        recent = true;
                    } else {
                        coloredCode = code = result.get(position).emoji;
                        recent = false;
                    }

                    if (code != null && code.startsWith("animated_")) {
                        try {
                            customEmojiId = Long.parseLong(code.substring(9));
                            code = null;
                            coloredCode = null;
                        } catch (Exception ignore) {}
                    }

                    if (customEmojiId != null) {
                        imageView.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
                    } else {
                        imageView.setPadding(0, 0, 0, 0);
                    }
                    if (customEmojiId != null) {
                        imageView.setImageDrawable(null, recent);
                        if (imageView.getSpan() == null || imageView.getSpan().getDocumentId() != customEmojiId) {
                            imageView.setSpan(new AnimatedEmojiSpan(customEmojiId, null));
                        }
                    } else {
                        imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode), recent);
                        imageView.setSpan(null);
                    }
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
            search(text, true);
        }

        public void search(String text, boolean delay) {
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
                emojiSearchField.showProgress(true);
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    final LinkedHashSet<Long> documentIds = new LinkedHashSet<>();
                    final String query = lastSearchEmojiString;
                    final Runnable fullSearch = () -> {
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
                                    emojiSearchField.showProgress(false);
                                    searchWas = true;
                                    if (emojiGridView.getAdapter() != emojiSearchAdapter) {
                                        emojiGridView.setAdapter(emojiSearchAdapter);
                                    }
                                    result.clear();
                                    searchByPackname(query, documentIds);
                                    for (long documentId : documentIds) {
                                        MediaDataController.KeywordResult r = new MediaDataController.KeywordResult();
                                        r.keyword = "";
                                        r.emoji = "animated_" + documentId;
                                        result.add(r);
                                    }
                                    for (int i = 0; i < param.size(); ++i) {
                                        MediaDataController.KeywordResult r = param.get(i);
                                        if (r != null && r.emoji != null && (!r.emoji.startsWith("animated_") || !documentIds.contains(Long.parseLong(r.emoji.substring(9))))) {
                                            result.add(r);
                                        }
                                    }
                                    notifyDataSetChanged();
                                }
                            }
                        }, null, true, false, true, 25);
                    };
                    if (Emoji.fullyConsistsOfEmojis(query)) {
                        StickerCategoriesListView.search.fetch(UserConfig.selectedAccount, query, list -> {
                            if (list != null) {
                                documentIds.addAll(list.document_id);
                            }
                            fullSearch.run();
                        });
                    } else {
                        fullSearch.run();
                    }
                }, delay ? 300 : 0);
            }
        }

        private ArrayList<Long> addedSets = new ArrayList<>();

        private void searchByPackname(String query, LinkedHashSet<Long> documentIds) {
            if (query == null || query.length() <= 3 || !UserConfig.getInstance(currentAccount).isPremium()) {
                return;
            }
            String translitQuery = LocaleController.getInstance().getTranslitString(query).toLowerCase();

            ArrayList<TLRPC.TL_messages_stickerSet> sets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
            ArrayList<TLRPC.StickerSetCovered> featuredSets = MediaDataController.getInstance(currentAccount).getFeaturedEmojiSets();
            addedSets.clear();

            for (int i = 0; i < sets.size(); ++i) {
                TLRPC.TL_messages_stickerSet fullSet = sets.get(i);
                if (fullSet == null || fullSet.set == null) {
                    continue;
                }
                checkAddPackToResults(fullSet.set, fullSet.documents, translitQuery, documentIds);
            }
            for (int i = 0; i < featuredSets.size(); ++i) {
                TLRPC.StickerSetCovered coveredSet = featuredSets.get(i);
                if (coveredSet == null || coveredSet.set == null) {
                    continue;
                }
                if (coveredSet instanceof TLRPC.TL_stickerSetFullCovered) {
                    checkAddPackToResults(coveredSet.set, ((TLRPC.TL_stickerSetFullCovered) coveredSet).documents, translitQuery, documentIds);
                } else if (coveredSet instanceof TLRPC.TL_stickerSetNoCovered) {
                    TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                    inputStickerSetID.id = coveredSet.set.id;
                    TLRPC.TL_messages_stickerSet fullSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetID, true);
                    if (fullSet != null) {
                        checkAddPackToResults(fullSet.set, fullSet.documents, translitQuery, documentIds);
                    }
                } else {
                    checkAddPackToResults(coveredSet.set, coveredSet.covers, translitQuery, documentIds);
                }
            }
        }

        private void checkAddPackToResults(TLRPC.StickerSet set, ArrayList<TLRPC.Document> documents, String translitQuery, LinkedHashSet<Long> documentIds) {
            if (set.title != null && !addedSets.contains(set.id) && LocaleController.getInstance().getTranslitString(set.title.toLowerCase()).contains(translitQuery)) {
                for (TLRPC.Document document : documents) {
                    if (document == null) {
                        continue;
                    }
                    documentIds.add(document.id);
                }
                addedSets.add(set.id);
            }
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

        public void destroyItem(ViewGroup viewGroup, int position, Object object) {
            viewGroup.removeView((View) object);
        }

        @Override
        public boolean canScrollToTab(int position) {
            if ((position == 1 || position == 2) && stickersBanned) {
                showStickerBanHint(true, false, position == 1);
                return false;
            }
            if (position == 0 && emojiBanned) {
                showStickerBanHint(true, true, false);
                return false;
            }
            return true;
        }

        public int getCount() {
            return currentTabs.size();
        }

        public Drawable getPageIconDrawable(int position) {
            return null;
//            return tabIcons[position];
        }

        public CharSequence getPageTitle(int position) {
            switch (position) {
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
        public int getTabPadding(int position) {
            switch (position) {
                case 0:
                    return AndroidUtilities.dp(18);
                case 1:
                case 2:
                default:
                    return AndroidUtilities.dp(12);
            }
        }

        @Override
        public void customOnDraw(Canvas canvas, View view, int position) {
//            if (position == 2 && !MediaDataController.getInstance(currentAccount).getUnreadStickerSets().isEmpty() && dotPaint != null) {
//                int x = canvas.getWidth() - view.getPaddingRight();
//                int y = canvas.getHeight() / 2 - AndroidUtilities.dp(13 - 5);
//                canvas.drawCircle(x, y, AndroidUtilities.dp(5), dotPaint);
//            }
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view = currentTabs.get(position).view;
            viewGroup.addView(view);
            return view;
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
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

        private boolean addSearch;

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
            if (position == 0 && addSearch) {
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
                    cell.setIsKeyboard(true);
                    cell.setCanPreviewGif(true);
                    view = cell;
                    break;
                case 1:
                    view = new View(getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, searchFieldHeight));
                    break;
                case 2:
                    final StickerSetNameCell cell1 = new StickerSetNameCell(context, false, resourcesProvider);
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
                        cell.setGif(recentGifs.get(position - (addSearch ? 1 : 0)), false);
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

            itemsCount = 0;
            if (addSearch) {
                itemsCount++;// search field
            }

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
            search(text, true);
        }

        public void search(String text, boolean delay) {
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
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    search(text, "", true);
                }, delay ? 300 : 0);
            }
        }

        public void searchEmoji(String emoji) {
            if (lastSearchIsEmoji && TextUtils.equals(lastSearchImageString, emoji)) {
                gifLayoutManager.scrollToPositionWithOffset(0, 0);
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
                        gifSearchField.showProgress(true);
                    }
                }
                return;
            }
            if (!withRecent && TextUtils.isEmpty(offset)) {
                gifSearchField.showProgress(true);
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
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate);
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
                    gifSearchField.showProgress(false);
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
                                notifyItemChanged(recentItemsCount + (gifAdapter.addSearch ? 1 : 0) + oldCount);
                                notifyItemRangeInserted(recentItemsCount + (gifAdapter.addSearch ? 1 : 0) + oldCount + 1, addedCount);
                            } else {
                                notifyItemRangeInserted(recentItemsCount + (gifAdapter.addSearch ? 1 : 0), addedCount + 1);
                            }
                        } else {
                            if (oldCount != 0) {
                                notifyItemChanged(oldCount);
                            }
                            notifyItemRangeInserted(oldCount + (gifAdapter.addSearch ? 1 : 0), addedCount);
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
                    if (position == 0 && gifAdapter.addSearch || gifGridView.getAdapter() == gifSearchAdapter && gifSearchAdapter.results.isEmpty()) {
                        return getSpanCount();
                    }
                    return getSpanSizeForItem(position - (gifAdapter.addSearch ? 1 : 0));
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
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 8, 0, 0));

            textView = new TextView(getContext());
            textView.setText(LocaleController.getString("NoGIFsFound", R.string.NoGIFsFound));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelEmptyText));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 42, 0, 0));

            progressView = new RadialProgressView(context, resourcesProvider);
            progressView.setVisibility(GONE);
            progressView.setProgressColor(getThemedColor(Theme.key_progressCircle));
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = gifGridView.getMeasuredHeight();

            if (!loadingState) {
                height = (int) ((height - searchFieldHeight - AndroidUtilities.dp(8)) / 3 * 1.7f);
            } else {
                height -= AndroidUtilities.dp(36 + 44);
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
            String query;
            int lastId;

            final ArrayList<TLRPC.StickerSetCovered> serverPacks = new ArrayList<>();
            final ArrayList<TLRPC.TL_messages_stickerSet> localPacks = new ArrayList<>();
            final HashMap<TLRPC.TL_messages_stickerSet, Boolean> localPacksByShortName = new HashMap<>();
            final HashMap<TLRPC.TL_messages_stickerSet, Integer> localPacksByName = new HashMap<>();
            final HashMap<ArrayList<TLRPC.Document>, String> emojiStickers = new HashMap<>();
            final ArrayList<ArrayList<TLRPC.Document>> emojiArrays = new ArrayList<>();

            final ArrayList<TLRPC.Document> emojiStickersArray = new ArrayList<>(0);
            final LongSparseArray<TLRPC.Document> emojiStickersMap = new LongSparseArray<>(0);

            private void searchFinish() {
                if (emojiSearchId != lastId) {
                    return;
                }

                StickersSearchGridAdapter.this.localPacks = localPacks;
                StickersSearchGridAdapter.this.serverPacks = serverPacks;
                StickersSearchGridAdapter.this.localPacksByShortName = localPacksByShortName;
                StickersSearchGridAdapter.this.localPacksByName = localPacksByName;
                StickersSearchGridAdapter.this.emojiStickers = emojiStickers;
                StickersSearchGridAdapter.this.emojiArrays = emojiArrays;
                stickersSearchField.showProgress(false);

                if (stickersGridView.getAdapter() != stickersSearchGridAdapter) {
                    stickersGridView.setAdapter(stickersSearchGridAdapter);
                }
                notifyDataSetChanged();
            }

            private void addFromAllStickers(Runnable finished) {
                final HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();

                if (query.length() <= 14) {
                    CharSequence emoji = query;
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
                        emojiStickersArray.addAll(newStickers);
                        for (int a = 0, size = newStickers.size(); a < size; a++) {
                            TLRPC.Document document = newStickers.get(a);
                            emojiStickersMap.put(document.id, document);
                        }
                        emojiStickers.put(emojiStickersArray, searchQuery);
                        emojiArrays.add(emojiStickersArray);
                    }
                }
                finished.run();
            }

            private void addFromSuggestions(Runnable finished) {
                final HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();

                if (allStickers != null && !allStickers.isEmpty() && query.length() > 1) {
                    String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                    if (!Arrays.equals(lastSearchKeyboardLanguage, newLanguage)) {
                        MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
                    }
                    lastSearchKeyboardLanguage = newLanguage;
                    MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, searchQuery, false, (param, alias) -> {
                        if (emojiSearchId != lastId) {
                            return;
                        }

                        for (int a = 0, size = param.size(); a < size; a++) {
                            String emoji = param.get(a).emoji;
                            ArrayList<TLRPC.Document> newStickers = allStickers.get(emoji);
                            if (newStickers != null && !newStickers.isEmpty()) {
                                if (!emojiStickers.containsKey(newStickers)) {
                                    emojiStickers.put(newStickers, emoji);
                                    emojiArrays.add(newStickers);
                                }
                            }
                        }
                        finished.run();
                    }, false);
                } else {
                    finished.run();
                }
            }

            private void addLocalPacks(Runnable finished) {
                ArrayList<TLRPC.TL_messages_stickerSet> local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
                MessagesController.getInstance(currentAccount).filterPremiumStickers(local);
                int index;
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_FEATURED);
                MessagesController.getInstance(currentAccount).filterPremiumStickers(local);
                for (int a = 0, size = local.size(); a < size; a++) {
                    TLRPC.TL_messages_stickerSet set = local.get(a);
                    if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                        if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                            localPacks.add(set);
                            localPacksByName.put(set, index);
                        }
                    } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                        if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                            localPacks.add(set);
                            localPacksByShortName.put(set, true);
                        }
                    }
                }
                finished.run();
            }

            private void searchStickerSets(Runnable finished) {
                final TLRPC.TL_messages_searchStickerSets req = new TLRPC.TL_messages_searchStickerSets();
                req.q = query;
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (emojiSearchId != lastId) {
                        return;
                    }

                    if (response instanceof TLRPC.TL_messages_foundStickerSets) {
                        reqId = 0;
                        TLRPC.TL_messages_foundStickerSets res = (TLRPC.TL_messages_foundStickerSets) response;
                        serverPacks.addAll(res.sets);
                    }
                    finished.run();
                }));
            }

            private void searchStickers(Runnable finished) {
                if (Emoji.fullyConsistsOfEmojis(searchQuery)) {
                    final TLRPC.TL_messages_getStickers req2 = new TLRPC.TL_messages_getStickers();
                    req2.emoticon = query;
                    req2.hash = 0;
                    reqId2 = ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (emojiSearchId != lastId) {
                            return;
                        }

                        reqId2 = 0;
                        if (req2.emoticon.equals(query)) {
                            if (!(response instanceof TLRPC.TL_messages_stickers)) {
                                finished.run();
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
                            }
                        }
                        finished.run();
                    }));
                } else {
                    finished.run();
                }
            }

            @Override
            public void run() {
                if (TextUtils.isEmpty(searchQuery)) {
                    if (stickersGridView.getAdapter() != stickersGridAdapter) {
                        stickersGridView.setAdapter(stickersGridAdapter);
                    }
                    notifyDataSetChanged();
                    return;
                }
                lastId = ++emojiSearchId;
                query = searchQuery;

                serverPacks.clear();
                localPacks.clear();
                localPacksByShortName.clear();
                localPacksByName.clear();
                emojiStickers.clear();
                emojiArrays.clear();

                emojiStickersArray.clear();
                emojiStickersMap.clear();

                stickersSearchField.showProgress(true);

                Utilities.raceCallbacks(
                        this::searchFinish,

                        this::addFromAllStickers,
                        this::addFromSuggestions,
                        this::addLocalPacks,
                        this::searchStickerSets,
                        this::searchStickers
                );
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
            search(text, true);
        }

        public void search(String text, boolean delay) {
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
                stickersSearchField.showProgress(false);
            } else {
                searchQuery = text.toLowerCase();
                stickersSearchField.showProgress(true);
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
                    view = new StickerEmojiCell(context, true) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new StickerSetNameCell(context, false, resourcesProvider);
                    break;
                case 3:
                    view = new FeaturedStickerSetInfoCell(context, 17, false, true, resourcesProvider);
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
                    imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
                    imageView.setTranslationY(-AndroidUtilities.dp(24));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 42, 0, 28));

                    TextView textView = new TextView(context);
                    textView.setText(LocaleController.getString("NoStickersFound", R.string.NoStickersFound));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelEmptyText));
                    frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 42, 0, 9));

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
                    cell.setSticker(sticker, null, cacheParent.get(position), positionToEmoji.get(position), false);
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

    public void searchProgressChanged() {
        updateStickerTabsPosition();
    }

    public float getStickersExpandOffset() {
        return stickersTab == null ? 0 : stickersTab.getExpandedOffset();
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
        updateStickerTabsPosition();
    }

    public void onMessageSend() {
        if (chooseStickerActionTracker != null) {
            chooseStickerActionTracker.reset();
        }
    }

    public static abstract class ChooseStickerActionTracker {

        private final int currentAccount;
        private final long dialogId;
        private final int threadId;

        public ChooseStickerActionTracker(int currentAccount, long dialogId, int threadId) {
            this.currentAccount = currentAccount;
            this.dialogId = dialogId;
            this.threadId = threadId;
        }

        boolean visible = false;
        boolean typingWasSent;
        long lastActionTime = -1;

        public void doSomeAction() {
            if (visible) {
                if (lastActionTime == -1) {
                    lastActionTime = System.currentTimeMillis();
                    return;
                }
                if (System.currentTimeMillis() - lastActionTime > 2000) {
                    typingWasSent = true;
                    lastActionTime = System.currentTimeMillis();
                    MessagesController.getInstance(currentAccount).sendTyping(dialogId, threadId, 10, 0);
                }
            }
        }

        private void reset() {
            if (typingWasSent) {
                MessagesController.getInstance(currentAccount).sendTyping(dialogId, threadId, 2, 0);
            }
            lastActionTime = -1;
        }

        public void checkVisibility() {
            visible = isShown();
            if (!visible) {
                reset();
            }
        }

        public abstract boolean isShown();
    }

    private class Tab {
        int type;
        View view;
    }
}
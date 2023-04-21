package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.DialogsAdapter;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TopicSearchCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityInterface;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ChatNotificationsPopupWrapper;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.InviteMembersBottomSheet;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.PullForegroundDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchDownloadsContainer;
import org.telegram.ui.Components.SearchViewPager;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UnreadCounterTextView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Delegates.ChatActivityMemberRequestsDelegate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class TopicsFragment extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ChatActivityInterface, RightSlidingDialogContainer.BaseFragmentWithFullscreen {

    private final static int BOTTOM_BUTTON_TYPE_JOIN = 0;
    private final static int BOTTOM_BUTTON_TYPE_REPORT = 1;
    final long chatId;
    ArrayList<Item> forumTopics = new ArrayList<>();

    private int lastItemsCount;
    private ArrayList<Item> frozenForumTopicsList = new ArrayList<>();
    private boolean forumTopicsListFrozen;

    SizeNotifierFrameLayout contentView;
    FrameLayout fullscreenView;
    ChatAvatarContainer avatarContainer;
    ChatActivity.ThemeDelegate themeDelegate;
    FrameLayout floatingButtonContainer;
    Adapter adapter = new Adapter();
    private final TopicsController topicsController;
    OnTopicSelectedListener onTopicSelectedListener;
    private PullForegroundDrawable pullForegroundDrawable;
    private int hiddenCount = 0;
    private int pullViewState;
    private boolean hiddenShown = true;

    private final static int ARCHIVE_ITEM_STATE_PINNED = 0;
    private final static int ARCHIVE_ITEM_STATE_SHOWED = 1;
    private final static int ARCHIVE_ITEM_STATE_HIDDEN = 2;

    private float floatingButtonTranslation;
    private float floatingButtonHideProgress;

    private boolean floatingHidden = false;
    private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

    LinearLayoutManager layoutManager;
    boolean animatedUpdateEnabled = true;

    private int prevPosition;
    private int prevTop;
    private boolean scrollUpdated;

    private final static int VIEW_TYPE_TOPIC = 0;
    private final static int VIEW_TYPE_LOADING_CELL = 1;
    private final static int VIEW_TYPE_EMPTY = 2;

    private static final int toggle_id = 1;
    private static final int add_member_id = 2;
    private static final int create_topic_id = 3;
    private static final int pin_id = 4;
    private static final int unpin_id = 5;
    private static final int mute_id = 6;
    private static final int delete_id = 7;
    private static final int read_id = 8;
    private static final int close_topic_id = 9;
    private static final int restart_topic_id = 10;
    private static final int delete_chat_id = 11;
    private static final int hide_id = 12;
    private static final int show_id = 13;

    private boolean removeFragmentOnTransitionEnd;
    TLRPC.ChatFull chatFull;
    boolean canShowCreateTopic;
    private UnreadCounterTextView bottomOverlayChatText;
    private int bottomButtonType;
    private TopicsRecyclerView recyclerListView;
    private RecyclerAnimationScrollHelper scrollHelper;
    private ItemTouchHelper itemTouchHelper;
    private TouchHelperCallback itemTouchHelperCallback;
    private ActionBarMenuSubItem createTopicSubmenu;
    private ActionBarMenuSubItem addMemberSubMenu;
    private ActionBarMenuSubItem deleteChatSubmenu;
    private boolean bottomPannelVisible = true;
    private float searchAnimationProgress = 0f;

    private long startArchivePullingTime;
    private boolean scrollingManually;
    private boolean canShowHiddenArchive;
    private boolean disableActionBarScrolling;

    HashSet<Integer> selectedTopics = new HashSet<>();
    private boolean reordering;
    private boolean ignoreDiffUtil;
    private NumberTextView selectedDialogsCountTextView;
    private ActionBarMenuItem pinItem;
    private ActionBarMenuItem unpinItem;
    private ActionBarMenuItem muteItem;
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem hideItem;
    private ActionBarMenuItem showItem;
    private ActionBarMenuSubItem readItem;
    private ActionBarMenuSubItem closeTopic;
    private ActionBarMenuSubItem restartTopic;
    ActionBarMenuItem otherItem;
    private RadialProgressView bottomOverlayProgress;
    private FrameLayout bottomOverlayContainer;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem other;
    private MessagesSearchContainer searchContainer;
    public boolean searching;
    private boolean opnendForSelect;
    private boolean openedForForward;
    HashSet<Integer> excludeTopics;
    private boolean mute = false;

    private boolean scrollToTop;
    private boolean endReached;
    StickerEmptyView topicsEmptyView;
    private View emptyView;

    FragmentContextView fragmentContextView;
    private ChatObject.Call groupCall;
    private DefaultItemAnimator itemAnimator;
    private boolean loadingTopics;
    RecyclerItemsEnterAnimator itemsEnterAnimator;
    DialogsActivity dialogsActivity;
    public DialogsActivity parentDialogsActivity;

    private boolean updateAnimated;

    private int transitionAnimationIndex;
    private int transitionAnimationGlobalIndex;
    private View blurredView;
    private int selectedTopicForTablet;

    private boolean joinRequested;
    private ChatActivityMemberRequestsDelegate pendingRequestsDelegate;

    float slideFragmentProgress = 1f;
    boolean isSlideBackTransition;
    boolean isDrawerTransition;
    ValueAnimator slideBackTransitionAnimator;

    private FrameLayout topView;
    private RLottieImageView floatingButton;
    private boolean canShowProgress;
    private ImageView closeReportSpam;

    @Override
    public View getFullscreenView() {
        return fullscreenView;
    }

    public TopicsFragment(Bundle bundle) {
        super(bundle);
        chatId = arguments.getLong("chat_id", 0);
        opnendForSelect = arguments.getBoolean("for_select", false);
        openedForForward = arguments.getBoolean("forward_to", false);
        topicsController = getMessagesController().getTopicsController();
        canShowProgress = !getUserConfig().getPreferences().getBoolean("topics_end_reached_" + chatId, false);
    }

    public static void prepareToSwitchAnimation(ChatActivity chatActivity) {
        if (chatActivity.getParentLayout() == null) {
            return;
        }
        boolean needCreateTopicsFragment = false;
        if (chatActivity.getParentLayout().getFragmentStack().size() <= 1) {
            needCreateTopicsFragment = true;
        } else {
            BaseFragment previousFragment = chatActivity.getParentLayout().getFragmentStack().get(chatActivity.getParentLayout().getFragmentStack().size() - 2);
            if (previousFragment instanceof TopicsFragment) {
                TopicsFragment topicsFragment = (TopicsFragment) previousFragment;
                if (topicsFragment.chatId != -chatActivity.getDialogId()) {
                    needCreateTopicsFragment = true;
                }
            } else {
                needCreateTopicsFragment = true;
            }
        }
        if (needCreateTopicsFragment) {
            Bundle bundle = new Bundle();
            bundle.putLong("chat_id", -chatActivity.getDialogId());
            TopicsFragment topicsFragment = new TopicsFragment(bundle);
            chatActivity.getParentLayout().addFragmentToStack(topicsFragment, chatActivity.getParentLayout().getFragmentStack().size() - 1);
        }
        chatActivity.setSwitchFromTopics(true);
        chatActivity.finishFragment();
    }

    @Override
    public View createView(Context context) {
        fragmentView = contentView = new SizeNotifierFrameLayout(context) {
            {
                setWillNotDraw(false);
            }

            public int getActionBarFullHeight() {
                float h = actionBar.getHeight();
                float searchTabsHeight = 0;
                if (searchTabsView != null && searchTabsView.getVisibility() != View.GONE) {
                    searchTabsHeight = searchTabsView.getMeasuredHeight();
                }
                h += searchTabsHeight * searchAnimationProgress;
                return (int) h;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == actionBar && !isInPreviewMode()) {
                    int y = (int) (actionBar.getY() + getActionBarFullHeight());
                    getParentLayout().drawHeaderShadow(canvas, (int) (255 * (1f - searchAnimationProgress)), y);
                    if (searchAnimationProgress > 0) {
                        if (searchAnimationProgress < 1) {
                            int a = Theme.dividerPaint.getAlpha();
                            Theme.dividerPaint.setAlpha((int) (a * searchAnimationProgress));
                            canvas.drawLine(0, y, getMeasuredWidth(), y, Theme.dividerPaint);
                            Theme.dividerPaint.setAlpha(a);
                        } else {
                            canvas.drawLine(0, y, getMeasuredWidth(), y, Theme.dividerPaint);
                        }
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                int actionBarHeight = 0;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child instanceof ActionBar) {
                        child.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                        actionBarHeight = child.getMeasuredHeight();
                    }
                }
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (!(child instanceof ActionBar)) {
                        if (child.getFitsSystemWindows()) {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                        } else {
                            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
                        }
                    }
                }
                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                final int count = getChildCount();

                final int parentLeft = getPaddingLeft();
                final int parentRight = right - left - getPaddingRight();

                final int parentTop = getPaddingTop();
                final int parentBottom = bottom - top - getPaddingBottom();

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                        final int width = child.getMeasuredWidth();
                        final int height = child.getMeasuredHeight();

                        int childLeft;
                        int childTop;

                        int gravity = lp.gravity;
                        if (gravity == -1) {
                            gravity = Gravity.NO_GRAVITY;
                        }

                        boolean forceLeftGravity = false;
                        final int layoutDirection;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            layoutDirection = getLayoutDirection();
                        } else {
                            layoutDirection = 0;
                        }
                        final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                            case Gravity.CENTER_HORIZONTAL:
                                childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
                                        lp.leftMargin - lp.rightMargin;
                                break;
                            case Gravity.RIGHT:
                                if (!forceLeftGravity) {
                                    childLeft = parentRight - width - lp.rightMargin;
                                    break;
                                }
                            case Gravity.LEFT:
                            default:
                                childLeft = parentLeft + lp.leftMargin;
                        }

                        switch (verticalGravity) {
                            case Gravity.CENTER_VERTICAL:
                                childTop = parentTop + (parentBottom - parentTop - height) / 2 +
                                        lp.topMargin - lp.bottomMargin;
                                break;
                            case Gravity.BOTTOM:
                                childTop = parentBottom - height - lp.bottomMargin;
                                break;
                            case Gravity.TOP:
                            default:
                                childTop = parentTop + lp.topMargin;
                                if (child == topView) {
                                    topView.setPadding(0, isInPreviewMode() ? 0 : actionBar.getTop() + actionBar.getMeasuredHeight(), 0, 0);
                                } else if (!(child instanceof ActionBar) && !isInPreviewMode()) {
                                    childTop += actionBar.getTop() + actionBar.getMeasuredHeight();
                                }
                        }

                        child.layout(childLeft, childTop, childLeft + width, childTop + height);
                    }
                }
            }

            @Override
            protected void drawList(Canvas blurCanvas, boolean top) {
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    View child = recyclerListView.getChildAt(i);
                    if (child.getY() < AndroidUtilities.dp(100) && child.getVisibility() == View.VISIBLE) {
                        int restore = blurCanvas.save();
                        blurCanvas.translate(recyclerListView.getX() + child.getX(), getY() + recyclerListView.getY() + child.getY());
                        child.draw(blurCanvas);
                        blurCanvas.restoreToCount(restore);
                    }
                }
            }

            private Paint actionBarPaint = new Paint();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (isInPreviewMode()) {
                    actionBarPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    actionBarPaint.setAlpha((int) (255 * searchAnimationProgress));
                    canvas.drawRect(0, 0, getWidth(), AndroidUtilities.statusBarHeight, actionBarPaint);
                }
            }

        };
        contentView.needBlur = !inPreviewMode;

        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet() && !inPreviewMode);
        if (inPreviewMode) {
            actionBar.setBackgroundColor(Color.TRANSPARENT);
            actionBar.setInterceptTouches(false);
        }

        actionBar.setBackButtonDrawable(new BackDrawable(false));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectedTopics.size() > 0) {
                        clearSelectedTopics();
                        return;
                    }
                    finishFragment();
                    return;
                }
                TLRPC.TL_forumTopic topic;
                switch (id) {
                    case toggle_id:
                        switchToChat(false);
                        break;
                    case add_member_id:
                        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chatId);
                        if (TopicsFragment.this.chatFull != null && TopicsFragment.this.chatFull.participants != null) {
                            chatFull.participants = TopicsFragment.this.chatFull.participants;
                        }
                        if (chatFull != null) {
                            LongSparseArray<TLObject> users = new LongSparseArray<>();
                            if (chatFull.participants != null) {
                                for (int a = 0; a < chatFull.participants.participants.size(); a++) {
                                    users.put(chatFull.participants.participants.get(a).user_id, null);
                                }
                            }
                            long chatId = chatFull.id;
                            InviteMembersBottomSheet bottomSheet = new InviteMembersBottomSheet(context, currentAccount, users, chatFull.id, TopicsFragment.this, themeDelegate) {
                                @Override
                                protected boolean canGenerateLink() {
                                    TLRPC.Chat chat = getMessagesController().getChat(chatId);
                                    return chat != null && ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE);
                                }
                            };
                            bottomSheet.setDelegate((users1, fwdCount) -> {
                                int N = users1.size();
                                int[] finished = new int[1];
                                for (int a = 0; a < N; a++) {
                                    TLRPC.User user = users1.get(a);
                                    getMessagesController().addUserToChat(chatId, user, fwdCount, null, TopicsFragment.this, () -> {
                                        if (++finished[0] == N) {
                                            BulletinFactory.of(TopicsFragment.this).createUsersAddedBulletin(users1, getMessagesController().getChat(chatId)).show();
                                        }
                                    });
                                }
                            });
                            bottomSheet.show();
                        }
                        break;
                    case create_topic_id:
                        TopicCreateFragment fragment = TopicCreateFragment.create(chatId, 0);
                        presentFragment(fragment);
                        AndroidUtilities.runOnUIThread(() -> {
                            fragment.showKeyboard();
                        }, 200);
                        break;
                    case delete_chat_id:
                        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
                        AlertsCreator.createClearOrDeleteDialogAlert(TopicsFragment.this, false, chatLocal, null, false, true, false, (param) -> {
                            getNotificationCenter().removeObserver(TopicsFragment.this, NotificationCenter.closeChats);
                            getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                            finishFragment();
                            getNotificationCenter().postNotificationName(NotificationCenter.needDeleteDialog, -chatLocal.id, null, chatLocal, param);
                        }, themeDelegate);
                        break;
                    case delete_id:
                        deleteTopics(selectedTopics, () -> {
                            clearSelectedTopics();
                        });
                        break;
                    case hide_id:
                    case show_id:
                        topic = null;
                        TopicDialogCell dialogCell = null;
                        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
                            View child = recyclerListView.getChildAt(i);
                            if (child instanceof TopicDialogCell && ((TopicDialogCell) child).forumTopic != null && ((TopicDialogCell) child).forumTopic.id == 1) {
                                dialogCell = (TopicDialogCell) child;
                                topic = dialogCell.forumTopic;
                                break;
                            }
                        }
                        if (topic == null) {
                            for (int i = 0; i < forumTopics.size(); ++i) {
                                if (forumTopics.get(i) != null && forumTopics.get(i).topic != null && forumTopics.get(i).topic.id == 1) {
                                    topic = forumTopics.get(i).topic;
                                    break;
                                }
                            }
                        }
                        if (topic != null) {
                            if (hiddenCount <= 0) {
                                hiddenShown = true;
                                pullViewState = ARCHIVE_ITEM_STATE_HIDDEN;
                            }
                            getMessagesController().getTopicsController().toggleShowTopic(chatId, 1, topic.hidden);
                            if (dialogCell != null) {
                                generalTopicViewMoving = dialogCell;
                            }
                            recyclerListView.setArchiveHidden(!topic.hidden, dialogCell);
                            updateTopicsList(true, true);
                            if (dialogCell != null) {
                                dialogCell.setTopicIcon(dialogCell.currentTopic);
                            }
                        }
                        clearSelectedTopics();
                        break;
                    case pin_id:
                    case unpin_id:
                        if (selectedTopics.size() > 0) {
                            scrollToTop = true;
                            updateAnimated = true;
                            topicsController.pinTopic(chatId, selectedTopics.iterator().next(), id == pin_id, TopicsFragment.this);
                        }
                        clearSelectedTopics();
                        break;
                    case mute_id:
                        Iterator<Integer> iterator = selectedTopics.iterator();
                        while (iterator.hasNext()) {
                            int topicId = iterator.next();
                            getNotificationsController().muteDialog(-chatId, topicId, mute);
                        }
                        clearSelectedTopics();
                        break;
                    case restart_topic_id:
                    case close_topic_id:
                        updateAnimated = true;
                        ArrayList<Integer> list = new ArrayList<>(selectedTopics);
                        for (int i = 0; i < list.size(); ++i) {
                            topicsController.toggleCloseTopic(chatId, list.get(i), id == close_topic_id);
                        }
                        clearSelectedTopics();
                        break;
                    case read_id:
                        list = new ArrayList<>(selectedTopics);
                        for (int i = 0; i < list.size(); ++i) {
                            topic = topicsController.findTopic(chatId, list.get(i));
                            if (topic != null) {
                                getMessagesController().markMentionsAsRead(-chatId, topic.id);
                                getMessagesController().markDialogAsRead(-chatId, topic.top_message, 0, topic.topMessage != null ? topic.topMessage.date : 0, false, topic.id, 0, true, 0);
                                getMessagesStorage().updateRepliesMaxReadId(chatId, topic.id, topic.top_message, 0, true);
                            }
                        }
                        clearSelectedTopics();
                        break;
                }
                super.onItemClick(id);
            }
        });


        actionBar.setOnClickListener(v -> {
            if (!searching) {
                openProfile(false);
            }
        });
        ActionBarMenu menu = actionBar.createMenu();

        if (parentDialogsActivity != null) {
            searchItem = menu.addItem(0, R.drawable.ic_ab_search);
            searchItem.setOnClickListener(e -> {
                openParentSearch();
            });
        } else {
            searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    animateToSearchView(true);
                    searchContainer.setSearchString("");
                    searchContainer.setAlpha(0);
                    searchContainer.emptyView.showProgress(true, false);
                }

                @Override
                public void onSearchCollapse() {
                    animateToSearchView(false);
                }

                @Override
                public void onTextChanged(EditText editText) {
                    String text = editText.getText().toString();
                    searchContainer.setSearchString(text);
                }

                @Override
                public void onSearchFilterCleared(FiltersView.MediaFilterData filterData) {

                }
            });
            searchItem.setSearchPaddingStart(56);
            searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setHintTextColor(Theme.getColor(Theme.key_player_time));
            editText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        }
        other = menu.addItem(0, R.drawable.ic_ab_other, themeDelegate);
        other.addSubItem(toggle_id, R.drawable.msg_discussion, LocaleController.getString("TopicViewAsMessages", R.string.TopicViewAsMessages));
        addMemberSubMenu = other.addSubItem(add_member_id, R.drawable.msg_addcontact, LocaleController.getString("AddMember", R.string.AddMember));
        createTopicSubmenu = other.addSubItem(create_topic_id, R.drawable.msg_topic_create, LocaleController.getString("CreateTopic", R.string.CreateTopic));
        deleteChatSubmenu = other.addSubItem(delete_chat_id, R.drawable.msg_leave, LocaleController.getString("LeaveMegaMenu", R.string.LeaveMegaMenu), themeDelegate);

        avatarContainer = new ChatAvatarContainer(context, this, false);
        avatarContainer.getAvatarImageView().setRoundRadius(AndroidUtilities.dp(16));
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet() && !inPreviewMode);
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 86, 0));

        avatarContainer.getAvatarImageView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openProfile(true);
            }
        });
        recyclerListView = new TopicsRecyclerView(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                checkForLoadMore();
            }

            @Override
            public boolean emptyViewIsVisible() {
                if (getAdapter() == null || isFastScrollAnimationRunning()) {
                    return false;
                }
                if (forumTopics != null && forumTopics.size() == 1 && forumTopics.get(0) != null && forumTopics.get(0).topic != null && forumTopics.get(0).topic.id == 1) {
                    return getAdapter().getItemCount() <= 2;
                }
                return getAdapter().getItemCount() <= 1;
            }
        };
        SpannableString generalIcon = new SpannableString("#");
        Drawable generalIconDrawable = ForumUtilities.createGeneralTopicDrawable(getContext(), .85f, Color.WHITE);
        generalIconDrawable.setBounds(0, AndroidUtilities.dp(2), AndroidUtilities.dp(16), AndroidUtilities.dp(18));
        generalIcon.setSpan(new ImageSpan(generalIconDrawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        pullForegroundDrawable = new PullForegroundDrawable(
                AndroidUtilities.replaceCharSequence("#", LocaleController.getString("AccSwipeForGeneral", R.string.AccSwipeForGeneral), generalIcon),
                AndroidUtilities.replaceCharSequence("#", LocaleController.getString("AccReleaseForGeneral", R.string.AccReleaseForGeneral), generalIcon)
        ) {
            @Override
            protected float getViewOffset() {
                return recyclerListView.getViewOffset();
            }
        };
        if (false) {
            pullForegroundDrawable.showHidden();
        } else {
            pullForegroundDrawable.doNotShow();
        }
        pullViewState = hiddenShown ? ARCHIVE_ITEM_STATE_HIDDEN : ARCHIVE_ITEM_STATE_PINNED;
        pullForegroundDrawable.setWillDraw(pullViewState != ARCHIVE_ITEM_STATE_PINNED);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator() {
            Runnable finishRunnable;
            int scrollAnimationIndex;

            @Override
            public void checkIsRunning() {
                if (scrollAnimationIndex == -1) {
                    scrollAnimationIndex = getNotificationCenter().setAnimationInProgress(scrollAnimationIndex, null, false);
                    if (finishRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                        finishRunnable = null;
                    }
                }
            }

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    finishRunnable = null;
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    finishRunnable = null;
                    if (scrollAnimationIndex != -1) {
                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
                        scrollAnimationIndex = -1;
                    }
                });
            }


            @Override
            public void endAnimations() {
                super.endAnimations();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    finishRunnable = null;
                    if (scrollAnimationIndex != -1) {
                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
                        scrollAnimationIndex = -1;
                    }
                });
            }

            @Override
            protected void afterAnimateMoveImpl(RecyclerView.ViewHolder holder) {
                if (generalTopicViewMoving == holder.itemView) {
                    generalTopicViewMoving.setTranslationX(0);
                    if (itemTouchHelper != null) {
                        itemTouchHelper.clearRecoverAnimations();
                    }
                    if (generalTopicViewMoving instanceof TopicDialogCell) {
                        ((TopicDialogCell) generalTopicViewMoving).setTopicIcon(((TopicDialogCell) generalTopicViewMoving).currentTopic);
                    }
                    generalTopicViewMoving = null;
                }
            }
        };
        recyclerListView.setHideIfEmpty(false);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        defaultItemAnimator.setDelayAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator = defaultItemAnimator);
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkForLoadMore();
            }
        });
        recyclerListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        itemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
        recyclerListView.setItemsEnterAnimator(itemsEnterAnimator);
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (getParentLayout() == null || getParentLayout().isInPreviewMode()) {
                return;
            }
            TLRPC.TL_forumTopic topic = null;
            if (view instanceof TopicDialogCell) {
                topic = ((TopicDialogCell) view).forumTopic;
            }
            if (topic == null) {
                return;
            }
            if (opnendForSelect) {
                if (onTopicSelectedListener != null) {
                    onTopicSelectedListener.onTopicSelected(topic);
                }
                if (dialogsActivity != null) {
                    dialogsActivity.didSelectResult(-chatId, topic.id, true, false, this);
                }
                return;
            }
            if (selectedTopics.size() > 0) {
                toggleSelection(view);
                return;
            }
            if (inPreviewMode && AndroidUtilities.isTablet()) {
                for (BaseFragment fragment : getParentLayout().getFragmentStack()) {
                    if (fragment instanceof DialogsActivity && ((DialogsActivity) fragment).isMainDialogList()) {
                        MessagesStorage.TopicKey topicKey = ((DialogsActivity) fragment).getOpenedDialogId();
                        if (topicKey.dialogId == -chatId && topicKey.topicId == topic.id) {
                            return;
                        }
                    }
                }
                selectedTopicForTablet = topic.id;
                updateTopicsList(false, false);
            }
            ForumUtilities.openTopic(TopicsFragment.this, chatId, topic, 0);
        });
        recyclerListView.setOnItemLongClickListener((view, position, x, y) -> {
            if (opnendForSelect || getParentLayout() == null || getParentLayout().isInPreviewMode()) {
                return false;
            }
            if (!actionBar.isActionModeShowed() && !AndroidUtilities.isTablet() && view instanceof TopicDialogCell) {
                TopicDialogCell cell = (TopicDialogCell) view;
                if (cell.isPointInsideAvatar(x, y)) {
                    showChatPreview(cell);
                    recyclerListView.cancelClickRunnables(true);
                    recyclerListView.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
                    return false;
                }
            }
            toggleSelection(view);
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                contentView.invalidateBlur();
            }
        });
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context) {

            private boolean fixOffset;

            @Override
            public void scrollToPositionWithOffset(int position, int offset) {
                if (fixOffset) {
                    offset -= recyclerListView.getPaddingTop();
                }
                super.scrollToPositionWithOffset(position, offset);
            }

            @Override
            public void prepareForDrop(@NonNull View view, @NonNull View target, int x, int y) {
                fixOffset = true;
                super.prepareForDrop(view, target, x, y);
                fixOffset = false;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                if (hiddenCount > 0 && position == 1) {
                    super.smoothScrollToPosition(recyclerView, state, position);
                } else {
                    LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE);
                    linearSmoothScroller.setTargetPosition(position);
                    startSmoothScroll(linearSmoothScroller);
                }
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (recyclerListView.fastScrollAnimationRunning) {
                    return 0;
                }
                boolean isDragging = recyclerListView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING;

                int measuredDy = dy;
                int pTop = recyclerListView.getPaddingTop();
                if (dy < 0 && hiddenCount > 0 && pullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                    recyclerListView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                    int currentPosition = layoutManager.findFirstVisibleItemPosition();
                    if (currentPosition == 0) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        if (view != null) {
                            view.setTranslationX(0);
                        }
                        if (view != null && (view.getBottom() - pTop) <= AndroidUtilities.dp(1)) {
                            currentPosition = 1;
                        }
                    }
                    if (!isDragging) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        if (view != null) {
                            int dialogHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72) + 1;
                            int canScrollDy = -(view.getTop() - pTop) + (currentPosition - 1) * dialogHeight;
                            int positiveDy = Math.abs(dy);
                            if (canScrollDy < positiveDy) {
                                measuredDy = -canScrollDy;
                            }
                        }
                    } else if (currentPosition == 0) {
                        View v = layoutManager.findViewByPosition(currentPosition);
                        float k = 1f + ((v.getTop() - pTop) / (float) v.getMeasuredHeight());
                        if (k > 1f) {
                            k = 1f;
                        }
                        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
                        measuredDy *= PullForegroundDrawable.startPullParallax - PullForegroundDrawable.endPullParallax * k;
                        if (measuredDy > -1) {
                            measuredDy = -1;
                        }
                    }
                }

                if (recyclerListView.getViewOffset() != 0 && dy > 0 && isDragging) {
                    float ty = (int) recyclerListView.getViewOffset();
                    ty -= dy;
                    if (ty < 0) {
                        measuredDy = (int) ty;
                        ty = 0;
                    } else {
                        measuredDy = 0;
                    }
                    recyclerListView.setViewsOffset(ty);
                }

                if (pullViewState != ARCHIVE_ITEM_STATE_PINNED && hiddenCount > 0) {
                    int usedDy = super.scrollVerticallyBy(measuredDy, recycler, state);
                    if (pullForegroundDrawable != null) {
                        pullForegroundDrawable.scrollDy = usedDy;
                    }
                    int currentPosition = layoutManager.findFirstVisibleItemPosition();
                    View firstView = null;
                    if (currentPosition == 0) {
                        firstView = layoutManager.findViewByPosition(currentPosition);
                    }
                    if (firstView != null) {
                        firstView.setTranslationX(0);
                    }
                    if (currentPosition == 0 && firstView != null && (firstView.getBottom() - pTop) >= AndroidUtilities.dp(4)) {
                        if (startArchivePullingTime == 0) {
                            startArchivePullingTime = System.currentTimeMillis();
                        }
                        if (pullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                            if (pullForegroundDrawable != null) {
                                pullForegroundDrawable.showHidden();
                            }
                        }
                        float k = 1f + ((firstView.getTop() - pTop) / (float) firstView.getMeasuredHeight());
                        if (k > 1f) {
                            k = 1f;
                        }
                        long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                        boolean canShowInternal = k > PullForegroundDrawable.SNAP_HEIGHT && pullingTime > PullForegroundDrawable.minPullingTime + 20;
                        if (canShowHiddenArchive != canShowInternal) {
                            canShowHiddenArchive = canShowInternal;
                            if (pullViewState == ARCHIVE_ITEM_STATE_HIDDEN) {
                                recyclerListView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                if (pullForegroundDrawable != null) {
                                    pullForegroundDrawable.colorize(canShowInternal);
                                }
                            }
                        }
                        if (pullViewState == ARCHIVE_ITEM_STATE_HIDDEN && measuredDy - usedDy != 0 && dy < 0 && isDragging) {
                            float ty;
                            float tk = (recyclerListView.getViewOffset() / PullForegroundDrawable.getMaxOverscroll());
                            tk = 1f - tk;
                            ty = (recyclerListView.getViewOffset() - dy * PullForegroundDrawable.startPullOverScroll * tk);
                            recyclerListView.setViewsOffset(ty);
                        }
                        if (pullForegroundDrawable != null) {
                            pullForegroundDrawable.pullProgress = k;
                            pullForegroundDrawable.setListView(recyclerListView);
                        }
                    } else {
                        startArchivePullingTime = 0;
                        canShowHiddenArchive = false;
                        pullViewState = ARCHIVE_ITEM_STATE_HIDDEN;
                        if (pullForegroundDrawable != null) {
                            pullForegroundDrawable.resetText();
                            pullForegroundDrawable.pullProgress = 0f;
                            pullForegroundDrawable.setListView(recyclerListView);
                        }
                    }
                    if (firstView != null) {
                        firstView.invalidate();
                    }
                    return usedDy;
                }
                return super.scrollVerticallyBy(measuredDy, recycler, state);
            }

            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (BuildVars.DEBUG_PRIVATE_VERSION) {
                    try {
                        super.onLayoutChildren(recycler, state);
                    } catch (IndexOutOfBoundsException e) {
                        throw new RuntimeException("Inconsistency detected. ");
                    }
                } else {
                    try {
                        super.onLayoutChildren(recycler, state);
                    } catch (IndexOutOfBoundsException e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> adapter.notifyDataSetChanged());
                    }
                }
            }
        });
        scrollHelper = new RecyclerAnimationScrollHelper(recyclerListView, layoutManager);
        recyclerListView.setAdapter(adapter);
        recyclerListView.setClipToPadding(false);
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            int prevPosition;
            int prevTop;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem != RecyclerView.NO_POSITION) {
                    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(firstVisibleItem);

                    int firstViewTop = 0;
                    if (holder != null) {
                        firstViewTop = holder.itemView.getTop();
                    }
                    boolean goingDown;
                    boolean changed = true;
                    if (prevPosition == firstVisibleItem) {
                        final int topDelta = prevTop - firstViewTop;
                        goingDown = firstViewTop < prevTop;
                        changed = Math.abs(topDelta) > 1;
                    } else {
                        goingDown = firstVisibleItem > prevPosition;
                    }

                    hideFloatingButton(goingDown || !canShowCreateTopic, true);
                }
            }
        });
        itemTouchHelper = new ItemTouchHelper(itemTouchHelperCallback = new TouchHelperCallback()) {
            @Override
            protected boolean shouldSwipeBack() {
                return hiddenCount > 0;
            }
        };
        itemTouchHelper.attachToRecyclerView(recyclerListView);

        contentView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ((ViewGroup.MarginLayoutParams) recyclerListView.getLayoutParams()).topMargin = -AndroidUtilities.dp(100);
        floatingButtonContainer = new FrameLayout(getContext());
        floatingButtonContainer.setVisibility(View.VISIBLE);
        contentView.addView(floatingButtonContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        floatingButtonContainer.setOnClickListener(v -> {
            presentFragment(TopicCreateFragment.create(chatId, 0));
        });

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = ContextCompat.getDrawable(getParentActivity(), R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        floatingButtonContainer.setBackground(drawable);
        floatingButton = new RLottieImageView(context);
        floatingButton.setImageResource(R.drawable.ic_chatlist_add_2);
        floatingButtonContainer.setContentDescription(LocaleController.getString("CreateTopic", R.string.CreateTopic));

        floatingButtonContainer.addView(floatingButton, LayoutHelper.createFrame(24, 24, Gravity.CENTER));


        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.TOPIC_CELL_TYPE);
        flickerLoadingView.setVisibility(View.GONE);
        flickerLoadingView.showDate(true);

        EmptyViewContainer emptyViewContainer = new EmptyViewContainer(context);
        emptyViewContainer.textView.setAlpha(0);

        topicsEmptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_NO_CONTACTS) {
            boolean showProgressInternal;

            @Override
            public void showProgress(boolean show, boolean animated) {
                super.showProgress(show, animated);
                showProgressInternal = show;
                if (animated) {
                    emptyViewContainer.textView.animate().alpha(show ? 0f : 1f).start();
                } else {
                    emptyViewContainer.textView.animate().cancel();
                    emptyViewContainer.textView.setAlpha(show ? 0f : 1f);
                }
            }
        };
        try {
            topicsEmptyView.stickerView.getImageReceiver().setAutoRepeat(2);
        } catch (Exception ignore) {
        }
        topicsEmptyView.showProgress(loadingTopics, fragmentBeginToShow);
        topicsEmptyView.title.setText(LocaleController.getString("NoTopics", R.string.NoTopics));
        updateTopicsEmptyViewText();

        emptyViewContainer.addView(flickerLoadingView);
        emptyViewContainer.addView(topicsEmptyView);
        contentView.addView(emptyViewContainer);

        recyclerListView.setEmptyView(emptyViewContainer);

        bottomOverlayContainer = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                super.dispatchDraw(canvas);
            }
        };
        bottomOverlayChatText = new UnreadCounterTextView(context);
        bottomOverlayContainer.addView(bottomOverlayChatText);
        contentView.addView(bottomOverlayContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlayChatText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bottomButtonType == BOTTOM_BUTTON_TYPE_REPORT) {
                    AlertsCreator.showBlockReportSpamAlert(TopicsFragment.this, -chatId, null, getCurrentChat(), null, false, chatFull, param -> {
                        if (param == 0) {
                            updateChatInfo();
                        } else {
                            finishFragment();
                        }
                    }, getResourceProvider());
                } else {
                    joinToGroup();
                }
            }
        });

        bottomOverlayProgress = new RadialProgressView(context, themeDelegate);
        bottomOverlayProgress.setSize(AndroidUtilities.dp(22));
        bottomOverlayProgress.setVisibility(View.INVISIBLE);
        bottomOverlayContainer.addView(bottomOverlayProgress, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

        closeReportSpam = new ImageView(context);
        closeReportSpam.setImageResource(R.drawable.miniplayer_close);
        closeReportSpam.setContentDescription(LocaleController.getString("Close", R.string.Close));
        if (Build.VERSION.SDK_INT >= 21) {
            closeReportSpam.setBackground(Theme.AdaptiveRipple.circle(getThemedColor(Theme.key_chat_topPanelClose)));
        }
        closeReportSpam.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_topPanelClose), PorterDuff.Mode.MULTIPLY));
        closeReportSpam.setScaleType(ImageView.ScaleType.CENTER);
        bottomOverlayContainer.addView(closeReportSpam, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 0, 6, 2, 0));
        closeReportSpam.setOnClickListener(v -> {
            getMessagesController().hidePeerSettingsBar(-chatId, null, getCurrentChat());
            updateChatInfo();
        });
        closeReportSpam.setVisibility(View.GONE);

        updateChatInfo();

        fullscreenView = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == searchTabsView && isInPreviewMode()) {
                    int y = (int) (searchTabsView.getY() + searchTabsView.getMeasuredHeight());
                    getParentLayout().drawHeaderShadow(canvas, (int) (255 * searchAnimationProgress), y);
//                    if (searchAnimationProgress > 0) {
//                        if (searchAnimationProgress < 1) {
//                            int a = Theme.dividerPaint.getAlpha();
//                            Theme.dividerPaint.setAlpha((int) (a * searchAnimationProgress));
//                            canvas.drawLine(0, y, getMeasuredWidth(), y, Theme.dividerPaint);
//                            Theme.dividerPaint.setAlpha(a);
//                        } else {
//                            canvas.drawLine(0, y, getMeasuredWidth(), y, Theme.dividerPaint);
//                        }
//                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        if (parentDialogsActivity == null) {
            contentView.addView(fullscreenView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        }
        searchContainer = new MessagesSearchContainer(context);
        searchContainer.setVisibility(View.GONE);
        fullscreenView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 44, 0, 0));

        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        actionBar.setDrawBlurBackground(contentView);

        getMessagesStorage().loadChatInfo(chatId, true, null, true, false, 0);

        topView = new FrameLayout(context);
        contentView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP));

        TLRPC.Chat currentChat = getCurrentChat();
        if (currentChat != null) {
            pendingRequestsDelegate = new ChatActivityMemberRequestsDelegate(this, currentChat, this::updateTopView);
            pendingRequestsDelegate.setChatInfo(chatFull, false);
            topView.addView(pendingRequestsDelegate.getView(), ViewGroup.LayoutParams.MATCH_PARENT, pendingRequestsDelegate.getViewHeight());
        }

        if (!inPreviewMode) {
            fragmentContextView = new FragmentContextView(context, this, false, themeDelegate) {
                @Override
                public void setTopPadding(float value) {
                    super.topPadding = value;
                    updateTopView();
                }
            };
            topView.addView(fragmentContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT));
        }
        FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        if (inPreviewMode && Build.VERSION.SDK_INT >= 21) {
            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
        }
        if (!isInPreviewMode()) {
            contentView.addView(actionBar, layoutParams);
        }

        checkForLoadMore();

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100)));
        }
        blurredView.setFocusable(false);
        blurredView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        blurredView.setOnClickListener(e -> {
            finishPreviewFragment();
        });
        blurredView.setFitsSystemWindows(true);

        bottomPannelVisible = true;

        if (inPreviewMode && AndroidUtilities.isTablet()) {
            for (BaseFragment fragment : getParentLayout().getFragmentStack()) {
                if (fragment instanceof DialogsActivity && ((DialogsActivity) fragment).isMainDialogList()) {
                    MessagesStorage.TopicKey topicKey = ((DialogsActivity) fragment).getOpenedDialogId();
                    if (topicKey.dialogId == -chatId) {
                        selectedTopicForTablet = topicKey.topicId;
                        break;
                    }
                }
            }
            updateTopicsList(false, false);
        }
        updateChatInfo();
        updateColors();

        return fragmentView;
    }

    private void updateTopicsEmptyViewText() {
        if (topicsEmptyView == null || topicsEmptyView.subtitle == null) {
            return;
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("d");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.ic_ab_other);
        coloredImageSpan.setSize(AndroidUtilities.dp(16));
        spannableStringBuilder.setSpan(coloredImageSpan, 0, 1, 0);
        if (ChatObject.canUserDoAdminAction(getCurrentChat(), ChatObject.ACTION_MANAGE_TOPICS)) {
            topicsEmptyView.subtitle.setText(
                    AndroidUtilities.replaceCharSequence("%s", AndroidUtilities.replaceTags(LocaleController.getString("NoTopicsDescription", R.string.NoTopicsDescription)), spannableStringBuilder)
            );
        } else {
            String general = LocaleController.getString("General", R.string.General);
            TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(chatId, 1);
            if (topic != null) {
                general = topic.title;
            }
            topicsEmptyView.subtitle.setText(
                    AndroidUtilities.replaceTags(LocaleController.formatString("NoTopicsDescriptionUser", R.string.NoTopicsDescriptionUser, general))
            );
        }
    }

    private void updateColors() {
        if (bottomOverlayProgress == null) {
            return;
        }
        bottomOverlayProgress.setProgressColor(getThemedColor(Theme.key_chat_fieldOverlayText));
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        bottomOverlayContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setActionModeColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (!inPreviewMode) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        }
        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
    }

    private void openProfile(boolean byAvatar) {
        if (byAvatar) {
            TLRPC.Chat chat = getCurrentChat();
            if (chat != null && (chat.photo == null || chat.photo instanceof TLRPC.TL_chatPhotoEmpty)) {
                byAvatar = false;
            }
        }
        Bundle args = new Bundle();
        args.putLong("chat_id", chatId);
        ProfileActivity fragment = new ProfileActivity(args, avatarContainer.getSharedMediaPreloader());
        fragment.setChatInfo(chatFull);
        fragment.setPlayProfileAnimation(fragmentView.getMeasuredHeight() > fragmentView.getMeasuredWidth() && avatarContainer.getAvatarImageView().getImageReceiver().hasImageLoaded() && byAvatar ? 2 : 1);
        presentFragment(fragment);
    }

    public void switchToChat(boolean removeFragment) {
        removeFragmentOnTransitionEnd = removeFragment;

        Bundle bundle = new Bundle();
        bundle.putLong("chat_id", chatId);
        ChatActivity chatActivity = new ChatActivity(bundle);
        chatActivity.setSwitchFromTopics(true);
        presentFragment(chatActivity);
    }

    private AvatarDrawable parentAvatarDrawable;
    private BackupImageView parentAvatarImageView;

    private void openParentSearch() {
        if (parentDialogsActivity != null && parentDialogsActivity.getSearchItem() != null) {
            if (parentAvatarImageView == null) {
                parentAvatarImageView = new BackupImageView(getContext());
                parentAvatarDrawable = new AvatarDrawable();
                parentAvatarImageView.setRoundRadius(AndroidUtilities.dp(16));
                parentAvatarDrawable.setInfo(getCurrentChat());
                parentAvatarImageView.setForUserOrChat(getCurrentChat(), parentAvatarDrawable);
            }
            parentDialogsActivity.getSearchItem().setSearchPaddingStart(52);
            parentDialogsActivity.getActionBar().setSearchAvatarImageView(parentAvatarImageView);
            parentDialogsActivity.getActionBar().onSearchFieldVisibilityChanged(
                    parentDialogsActivity.getSearchItem().toggleSearch(true)
            );
        }
    }

    @Override
    public boolean allowFinishFragmentInsteadOfRemoveFromStack() {
        return false;
    }

    private void updateTopView() {
        float translation = 0;
        if (fragmentContextView != null) {
            translation += Math.max(0, fragmentContextView.getTopPadding());
            fragmentContextView.setTranslationY(translation);
        }
        View pendingRequestsView = pendingRequestsDelegate != null ? pendingRequestsDelegate.getView() : null;
        if (pendingRequestsView != null) {
            pendingRequestsView.setTranslationY(translation + pendingRequestsDelegate.getViewEnterOffset());
            translation += pendingRequestsDelegate.getViewEnterOffset() + pendingRequestsDelegate.getViewHeight();
        }
        recyclerListView.setTranslationY(Math.max(0, translation));
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(bottomPannelVisible ? 51 : 0) + (int) translation);
    }

    float transitionPadding;

    public void setTransitionPadding(int transitionPadding) {
        this.transitionPadding = transitionPadding;
        updateFloatingButtonOffset();
    }

    private class TopicsRecyclerView extends BlurredRecyclerView {

        private boolean firstLayout = true;
        private boolean ignoreLayout;
        private int listTopPadding;

        Paint paint = new Paint();
        RectF rectF = new RectF();

        public TopicsRecyclerView(Context context) {
            super(context);
            useLayoutPositionOnClick = true;
            additionalClipBottom = AndroidUtilities.dp(200);
        }

        private float viewOffset;

        public void setViewsOffset(float viewOffset) {
            this.viewOffset = viewOffset;
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                getChildAt(i).setTranslationY(viewOffset);
            }

            if (selectorPosition != NO_POSITION) {
                View v = getLayoutManager().findViewByPosition(selectorPosition);
                if (v != null) {
                    selectorRect.set(v.getLeft(), (int) (v.getTop() + viewOffset), v.getRight(), (int) (v.getBottom() + viewOffset));
                    selectorDrawable.setBounds(selectorRect);
                }
            }
            invalidate();
        }

        public float getViewOffset() {
            return viewOffset;
        }

        @Override
        public void addView(View child, int index, ViewGroup.LayoutParams params) {
            super.addView(child, index, params);
            child.setTranslationY(viewOffset);
            child.setTranslationX(0);
            child.setAlpha(1f);
        }

        @Override
        public void removeView(View view) {
            super.removeView(view);
            view.setTranslationY(0);
            view.setTranslationX(0);
            view.setAlpha(1f);
        }

        @Override
        public void onDraw(Canvas canvas) {
            if (pullForegroundDrawable != null && viewOffset != 0) {
                int pTop = getPaddingTop();
                if (pTop != 0) {
                    canvas.save();
                    canvas.translate(0, pTop);
                }
                pullForegroundDrawable.drawOverScroll(canvas);
                if (pTop != 0) {
                    canvas.restore();
                }
            }
            super.onDraw(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (generalTopicViewMoving != null) {
                canvas.save();
                canvas.translate(generalTopicViewMoving.getLeft(), generalTopicViewMoving.getY());
                generalTopicViewMoving.draw(canvas);
                canvas.restore();
            }
            super.dispatchDraw(canvas);
            if (drawMovingViewsOverlayed()) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                for (int i = 0; i < getChildCount(); i++) {
                    View view = getChildAt(i);

                    if ((view instanceof DialogCell && ((DialogCell) view).isMoving()) || (view instanceof DialogsAdapter.LastEmptyView && ((DialogsAdapter.LastEmptyView) view).moving)) {
                        if (view.getAlpha() != 1f) {
                            rectF.set(view.getX(), view.getY(), view.getX() + view.getMeasuredWidth(), view.getY() + view.getMeasuredHeight());
                            canvas.saveLayerAlpha(rectF, (int) (255 * view.getAlpha()), Canvas.ALL_SAVE_FLAG);
                        } else {
                            canvas.save();
                        }
                        canvas.translate(view.getX(), view.getY());
                        canvas.drawRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), paint);
                        view.draw(canvas);
                        canvas.restore();
                    }
                }
                invalidate();
            }
        }

        private boolean drawMovingViewsOverlayed() {
            return getItemAnimator() != null && getItemAnimator().isRunning() && (dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0);
        }

        @Override
        public boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (drawMovingViewsOverlayed() && child instanceof DialogCell && ((DialogCell) child).isMoving() || generalTopicViewMoving == child) {
                return true;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
        }

        @Override
        public void setAdapter(RecyclerView.Adapter adapter) {
            super.setAdapter(adapter);
            firstLayout = true;
        }

        private void checkIfAdapterValid() {
            RecyclerView.Adapter adapter = getAdapter();
            if (lastItemsCount != adapter.getItemCount() && !forumTopicsListFrozen) {
                ignoreLayout = true;
                adapter.notifyDataSetChanged();
                ignoreLayout = false;
            }
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (firstLayout && getMessagesController().dialogsLoaded) {
                if (hiddenCount > 0) {
                    ignoreLayout = true;
                    LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
                    layoutManager.scrollToPositionWithOffset(1, (int) actionBar.getTranslationY());
                    ignoreLayout = false;
                }
                firstLayout = false;
            }
            super.onMeasure(widthSpec, heightSpec);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);

            if ((dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) && !itemAnimator.isRunning()) {
                onDialogAnimationFinished();
            }
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        private void setArchiveHidden(boolean shown, DialogCell dialogCell) {
            hiddenShown = shown;
            if (!hiddenShown) {
                if (dialogCell != null) {
                    disableActionBarScrolling = true;
                    layoutManager.scrollToPositionWithOffset(1, 0);
                    updatePullState();
                }
            } else {
                layoutManager.scrollToPositionWithOffset(0, 0);
                updatePullState();
                if (dialogCell != null) {
                    dialogCell.resetPinnedArchiveState();
                    dialogCell.invalidate();
                }
            }
            if (emptyView != null) {
                emptyView.forceLayout();
            }
        }

        private void updatePullState() {
            pullViewState = !hiddenShown ? ARCHIVE_ITEM_STATE_HIDDEN : ARCHIVE_ITEM_STATE_PINNED;
            if (pullForegroundDrawable != null) {
                pullForegroundDrawable.setWillDraw(pullViewState != ARCHIVE_ITEM_STATE_PINNED);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (fastScrollAnimationRunning || waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0 || (getParentLayout() != null && getParentLayout().isInPreviewMode())) {
                return false;
            }
            int action = e.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (!itemTouchHelper.isIdle() && itemTouchHelperCallback.swipingFolder) {
                    itemTouchHelperCallback.swipeFolderBack = true;
                    if (itemTouchHelper.checkHorizontalSwipe(null, ItemTouchHelper.LEFT) != 0) {
                        if (itemTouchHelperCallback.currentItemViewHolder != null) {
                            ViewHolder viewHolder = itemTouchHelperCallback.currentItemViewHolder;
                            if (viewHolder.itemView instanceof DialogCell) {
                                setArchiveHidden(!hiddenShown, (DialogCell) viewHolder.itemView);
                            }
                        }
                    }
                }
            }
            boolean result = super.onTouchEvent(e);
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && pullViewState == ARCHIVE_ITEM_STATE_HIDDEN && hiddenCount > 0) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
                int currentPosition = layoutManager.findFirstVisibleItemPosition();
                if (currentPosition == 0) {
                    int pTop = getPaddingTop();
                    View view = layoutManager.findViewByPosition(currentPosition);
                    int height = (int) (AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72) * PullForegroundDrawable.SNAP_HEIGHT);
                    int diff = (view.getTop() - pTop) + view.getMeasuredHeight();
                    if (view != null) {
                        long pullingTime = System.currentTimeMillis() - startArchivePullingTime;
                        if (diff < height || pullingTime < PullForegroundDrawable.minPullingTime) {
                            disableActionBarScrolling = true;
                            smoothScrollBy(0, diff, CubicBezierInterpolator.EASE_OUT_QUINT);
                            pullViewState = ARCHIVE_ITEM_STATE_HIDDEN;
                        } else {
                            if (pullViewState != ARCHIVE_ITEM_STATE_SHOWED) {
                                if (getViewOffset() == 0) {
                                    disableActionBarScrolling = true;
                                    smoothScrollBy(0, (view.getTop() - pTop), CubicBezierInterpolator.EASE_OUT_QUINT);
                                }
                                if (!canShowHiddenArchive) {
                                    canShowHiddenArchive = true;
                                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                    if (pullForegroundDrawable != null) {
                                        pullForegroundDrawable.colorize(true);
                                    }
                                }
                                ((DialogCell) view).startOutAnimation();
                                pullViewState = ARCHIVE_ITEM_STATE_SHOWED;
                            }
                        }

                        if (getViewOffset() != 0) {
                            ValueAnimator valueAnimator = ValueAnimator.ofFloat(getViewOffset(), 0f);
                            valueAnimator.addUpdateListener(animation -> setViewsOffset((float) animation.getAnimatedValue()));

                            valueAnimator.setDuration(Math.max(100, (long) (350f - 120f * (getViewOffset() / PullForegroundDrawable.getMaxOverscroll()))));
                            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                            setScrollEnabled(false);
                            valueAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    super.onAnimationEnd(animation);
                                    setScrollEnabled(true);
                                }
                            });
                            valueAnimator.start();
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (fastScrollAnimationRunning || waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0 || (getParentLayout() != null && getParentLayout().isInPreviewMode())) {
                return false;
            }
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                allowSwipeDuringCurrentTouch = !actionBar.isActionModeShowed();
                checkIfAdapterValid();
            }
            return super.onInterceptTouchEvent(e);
        }

        @Override
        protected boolean allowSelectChildAtPosition(View child) {
            if (child instanceof HeaderCell && !child.isClickable()) {
                return false;
            }
            return true;
        }
    }

    private void onDialogAnimationFinished() {
        dialogRemoveFinished = 0;
        dialogInsertFinished = 0;
        dialogChangeFinished = 0;
        AndroidUtilities.runOnUIThread(() -> {
//            setDialogsListFrozen(false);
//            updateDialogIndices();
        });
    }

    private void deleteTopics(HashSet<Integer> selectedTopics, Runnable runnable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getPluralString("DeleteTopics", selectedTopics.size()));
        ArrayList<Integer> topicsToRemove = new ArrayList<>(selectedTopics);
        if (selectedTopics.size() == 1) {
            TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicsToRemove.get(0));
            builder.setMessage(LocaleController.formatString("DeleteSelectedTopic", R.string.DeleteSelectedTopic, topic.title));
        } else {
            builder.setMessage(LocaleController.getString("DeleteSelectedTopics", R.string.DeleteSelectedTopics));
        }
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                excludeTopics = new HashSet<>();
                excludeTopics.addAll(selectedTopics);
                updateTopicsList(true, false);
                BulletinFactory.of(TopicsFragment.this).createUndoBulletin(LocaleController.getPluralString("TopicsDeleted", selectedTopics.size()), () -> {
                    excludeTopics = null;
                    updateTopicsList(true, false);
                }, () -> {
                    topicsController.deleteTopics(chatId, topicsToRemove);
                    runnable.run();
                }).show();
                clearSelectedTopics();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private boolean showChatPreview(DialogCell cell) {
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        final ActionBarPopupWindow.ActionBarPopupWindowLayout[] previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout[1];
        int flags = ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK;
        previewMenu[0] = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider(), flags);

        TLRPC.TL_forumTopic topic = cell.forumTopic;
        ChatNotificationsPopupWrapper chatNotificationsPopupWrapper = new ChatNotificationsPopupWrapper(getContext(), currentAccount, previewMenu[0].getSwipeBack(), false, false, new ChatNotificationsPopupWrapper.Callback() {
            @Override
            public void dismiss() {
                finishPreviewFragment();
            }

            @Override
            public void toggleSound() {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                boolean enabled = !preferences.getBoolean("sound_enabled_" + NotificationsController.getSharedPrefKey(-chatId, topic.id), true);
                preferences.edit().putBoolean("sound_enabled_" + NotificationsController.getSharedPrefKey(-chatId, topic.id), enabled).apply();
                finishPreviewFragment();
                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createSoundEnabledBulletin(TopicsFragment.this, enabled ? NotificationsController.SETTING_SOUND_ON : NotificationsController.SETTING_SOUND_OFF, getResourceProvider()).show();
                }

            }

            @Override
            public void muteFor(int timeInSeconds) {
                finishPreviewFragment();
                if (timeInSeconds == 0) {
                    if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
                        getNotificationsController().muteDialog(-chatId, topic.id, false);
                    }
                    if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                        BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_UNMUTE, timeInSeconds, getResourceProvider()).show();
                    }
                } else {
                    getNotificationsController().muteUntil(-chatId, topic.id, timeInSeconds);
                    if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                        BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_CUSTOM, timeInSeconds, getResourceProvider()).show();
                    }
                }
            }

            @Override
            public void showCustomize() {
                finishPreviewFragment();
                AndroidUtilities.runOnUIThread(() -> {
                    Bundle args = new Bundle();
                    args.putLong("dialog_id", -chatId);
                    args.putInt("topic_id", topic.id);
                    presentFragment(new ProfileNotificationsActivity(args, themeDelegate));
                }, 500);
            }

            @Override
            public void toggleMute() {
                finishPreviewFragment();
                boolean mute = !getMessagesController().isDialogMuted(-chatId, topic.id);
                getNotificationsController().muteDialog(-chatId, topic.id, mute);

                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createMuteBulletin(TopicsFragment.this, mute ? NotificationsController.SETTING_MUTE_FOREVER : NotificationsController.SETTING_MUTE_UNMUTE, mute ? Integer.MAX_VALUE : 0, getResourceProvider()).show();
                }
            }
        }, getResourceProvider());

        int muteForegroundIndex = previewMenu[0].addViewToSwipeBack(chatNotificationsPopupWrapper.windowLayout);
        chatNotificationsPopupWrapper.type = ChatNotificationsPopupWrapper.TYPE_PREVIEW_MENU;
        chatNotificationsPopupWrapper.update(-chatId, topic.id, null);

        if (ChatObject.canManageTopics(getCurrentChat())) {
            ActionBarMenuSubItem pinItem = new ActionBarMenuSubItem(getParentActivity(), true, false);
            if (topic.pinned) {
                pinItem.setTextAndIcon(LocaleController.getString("DialogUnpin", R.string.DialogUnpin), R.drawable.msg_unpin);
            } else {
                pinItem.setTextAndIcon(LocaleController.getString("DialogPin", R.string.DialogPin), R.drawable.msg_pin);
            }
            pinItem.setMinimumWidth(160);
            pinItem.setOnClickListener(e -> {
                scrollToTop = true;
                updateAnimated = true;
                topicsController.pinTopic(chatId, topic.id, !topic.pinned, TopicsFragment.this);
                finishPreviewFragment();
            });

            previewMenu[0].addView(pinItem);
        }

        ActionBarMenuSubItem muteItem = new ActionBarMenuSubItem(getParentActivity(), false, false);
        if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
            muteItem.setTextAndIcon(LocaleController.getString("Unmute", R.string.Unmute), R.drawable.msg_mute);
        } else {
            muteItem.setTextAndIcon(LocaleController.getString("Mute", R.string.Mute), R.drawable.msg_unmute);
        }
        muteItem.setMinimumWidth(160);
        muteItem.setOnClickListener(e -> {
            if (getMessagesController().isDialogMuted(-chatId, topic.id)) {
                getNotificationsController().muteDialog(-chatId, topic.id, false);
                finishPreviewFragment();
                if (BulletinFactory.canShowBulletin(TopicsFragment.this)) {
                    BulletinFactory.createMuteBulletin(TopicsFragment.this, NotificationsController.SETTING_MUTE_UNMUTE, 0, getResourceProvider()).show();
                }
            } else {
                previewMenu[0].getSwipeBack().openForeground(muteForegroundIndex);
            }
        });
        previewMenu[0].addView(muteItem);

        if (ChatObject.canManageTopic(currentAccount, getCurrentChat(), topic)) {
            ActionBarMenuSubItem closeItem = new ActionBarMenuSubItem(getParentActivity(), false, false);
            if (topic.closed) {
                closeItem.setTextAndIcon(LocaleController.getString("RestartTopic", R.string.RestartTopic), R.drawable.msg_topic_restart);
            } else {
                closeItem.setTextAndIcon(LocaleController.getString("CloseTopic", R.string.CloseTopic), R.drawable.msg_topic_close);
            }
            closeItem.setMinimumWidth(160);
            closeItem.setOnClickListener(e -> {
                updateAnimated = true;
                topicsController.toggleCloseTopic(chatId, topic.id, !topic.closed);
                finishPreviewFragment();
            });
            previewMenu[0].addView(closeItem);
        }

        if (ChatObject.canDeleteTopic(currentAccount, getCurrentChat(), topic)) {
            ActionBarMenuSubItem deleteItem = new ActionBarMenuSubItem(getParentActivity(), false, true);
            deleteItem.setTextAndIcon(LocaleController.getPluralString("DeleteTopics", 1), R.drawable.msg_delete);
            deleteItem.setIconColor(getThemedColor(Theme.key_text_RedRegular));
            deleteItem.setTextColor(getThemedColor(Theme.key_text_RedBold));
            deleteItem.setMinimumWidth(160);
            deleteItem.setOnClickListener(e -> {
                HashSet<Integer> hashSet = new HashSet();
                hashSet.add(topic.id);
                deleteTopics(hashSet, () -> {
                    finishPreviewFragment();
                });
            });
            previewMenu[0].addView(deleteItem);
        }

        prepareBlurBitmap();
        Bundle bundle = new Bundle();
        bundle.putLong("chat_id", chatId);
        ChatActivity chatActivity = new ChatActivity(bundle);
        ForumUtilities.applyTopic(chatActivity, MessagesStorage.TopicKey.of(-chatId, cell.forumTopic.id));
        presentFragmentAsPreviewWithMenu(chatActivity, previewMenu[0]);
        return false;
    }

    private void checkLoading() {
        loadingTopics = topicsController.isLoading(chatId);
        if (topicsEmptyView != null && (forumTopics.size() == 0 || (forumTopics.size() == 1 && forumTopics.get(0).topic.id == 1))) {
            topicsEmptyView.showProgress(loadingTopics, fragmentBeginToShow);
        }
        if (recyclerListView != null) {
            recyclerListView.checkIfEmpty();
        }
        updateCreateTopicButton(true);
    }

    ValueAnimator searchAnimator;
    ValueAnimator searchAnimator2;
    boolean animateSearchWithScale;
    private ViewPagerFixed.TabsView searchTabsView;

    private void animateToSearchView(boolean showSearch) {
        searching = showSearch;
        if (searchAnimator != null) {
            searchAnimator.removeAllListeners();
            searchAnimator.cancel();
        }
        if (searchTabsView == null) {
            searchTabsView = searchContainer.createTabsView(false, 8);
            if (parentDialogsActivity != null) {
                searchTabsView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            }
            fullscreenView.addView(searchTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));
        }
        searchAnimator = ValueAnimator.ofFloat(searchAnimationProgress, showSearch ? 1f : 0);
        AndroidUtilities.updateViewVisibilityAnimated(searchContainer, false, 1f, true);
        if (parentDialogsActivity != null && parentDialogsActivity.rightSlidingDialogContainer != null) {
            parentDialogsActivity.rightSlidingDialogContainer.enabled = !showSearch;
        }
        animateSearchWithScale = !showSearch && searchContainer.getVisibility() == View.VISIBLE && searchContainer.getAlpha() == 1f;
        searchAnimator.addUpdateListener(animation -> updateSearchProgress((Float) animation.getAnimatedValue()));
        searchContainer.setVisibility(View.VISIBLE);
        if (!showSearch) {
            other.setVisibility(View.VISIBLE);
        } else {
            AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
            updateCreateTopicButton(false);
        }
        searchAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                updateSearchProgress(showSearch ? 1f : 0);
                if (showSearch) {
                    other.setVisibility(View.GONE);
                } else {
                    AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                    searchContainer.setVisibility(View.GONE);
                    updateCreateTopicButton(true);
                }
            }
        });
        searchAnimator.setDuration(200);
        searchAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        searchAnimator.start();

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors, true);
    }

    private void updateCreateTopicButton(boolean animated) {
        if (createTopicSubmenu == null) {
            return;
        }
        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        canShowCreateTopic = !ChatObject.isNotInChat(getMessagesController().getChat(chatId)) && ChatObject.canCreateTopic(chatLocal) && !searching && !opnendForSelect && !loadingTopics;
        createTopicSubmenu.setVisibility(canShowCreateTopic ? View.VISIBLE : View.GONE);
        hideFloatingButton(!canShowCreateTopic, animated);
    }

    private void updateSearchProgress(float value) {
        searchAnimationProgress = value;
        int color1 = Theme.getColor(Theme.key_actionBarDefaultIcon);
        actionBar.setItemsColor(ColorUtils.blendARGB(color1, Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), searchAnimationProgress), false);
        actionBar.setItemsColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), searchAnimationProgress), true);

        color1 = Theme.getColor(Theme.key_actionBarDefaultSelector);
        int color2 = Theme.getColor(Theme.key_actionBarActionModeDefaultSelector);
        actionBar.setItemsBackgroundColor(ColorUtils.blendARGB(color1, color2, searchAnimationProgress), false);

        if (!inPreviewMode) {
            actionBar.setBackgroundColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_actionBarDefault), Theme.getColor(Theme.key_windowBackgroundWhite), searchAnimationProgress));
        }
        avatarContainer.getTitleTextView().setAlpha(1f - value);
        avatarContainer.getSubtitleTextView().setAlpha(1f - value);
        if (searchTabsView != null) {
            searchTabsView.setTranslationY(-AndroidUtilities.dp(16) * (1f - value));
            searchTabsView.setAlpha(value);
        }
        searchContainer.setTranslationY(-AndroidUtilities.dp(16) * (1f - value));
        searchContainer.setAlpha(value);

        if (isInPreviewMode()) {
            fullscreenView.invalidate();

            if (parentDialogsActivity != null) {

            }
        }
        contentView.invalidate();

        recyclerListView.setAlpha(1f - value);
        if (animateSearchWithScale) {
            float scale = 0.98f + 0.02f * (1f - searchAnimationProgress);
            recyclerListView.setScaleX(scale);
            recyclerListView.setScaleY(scale);
        }
    }

    private ArrayList<TLRPC.TL_forumTopic> getSelectedTopics() {
        ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
        Iterator<Integer> iterator = selectedTopics.iterator();
        while (iterator.hasNext()) {
            int topicId = iterator.next();
            TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
            if (topic != null) {
                topics.add(topic);
            }
        }
        return topics;
    }

    private void joinToGroup() {
        getMessagesController().addUserToChat(chatId, getUserConfig().getCurrentUser(), 0, null, this, false, () -> {
            joinRequested = false;
            updateChatInfo(true);
        }, e -> {
            if (e != null && "INVITE_REQUEST_SENT".equals(e.text)) {
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                preferences.edit().putLong("dialog_join_requested_time_" + -chatId, System.currentTimeMillis()).commit();
                JoinGroupAlert.showBulletin(getContext(), this, ChatObject.isChannelAndNotMegaGroup(getCurrentChat()));
                updateChatInfo(true);
                return false;
            }
            return true;
        });
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.closeSearchByActiveAction);
        updateChatInfo();
    }

    private void clearSelectedTopics() {
        selectedTopics.clear();
        actionBar.hideActionMode();
        AndroidUtilities.updateVisibleRows(recyclerListView);
        updateReordering();
    }

    private void toggleSelection(View view) {
        if (view instanceof TopicDialogCell) {
            TopicDialogCell cell = (TopicDialogCell) view;
            if (cell.forumTopic == null) {
                return;
            }
            int id = cell.forumTopic.id;
            if (!selectedTopics.remove(id)) {
                selectedTopics.add(id);
            }
            cell.setChecked(selectedTopics.contains(id), true);

            TLRPC.Chat currentChat = getMessagesController().getChat(chatId);

            if (selectedTopics.size() > 0) {
                chekActionMode();
                if (inPreviewMode) {
                    ((View) fragmentView.getParent()).invalidate();
                }
                actionBar.showActionMode(true);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
                Iterator<Integer> iterator = selectedTopics.iterator();
                int unreadCount = 0, readCount = 0;
                int canPinCount = 0, canUnpinCount = 0;
                int canMuteCount = 0, canUnmuteCount = 0;
                while (iterator.hasNext()) {
                    int topicId = iterator.next();
                    TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                    if (topic != null) {
                        if (topic.unread_count != 0) {
                            unreadCount++;
                        } else {
                            readCount++;
                        }
                        if (ChatObject.canManageTopics(currentChat) && !topic.hidden) {
                            if (topic.pinned) {
                                canUnpinCount++;
                            } else {
                                canPinCount++;
                            }
                        }
                    }
                    if (getMessagesController().isDialogMuted(-chatId, topicId)) {
                        canUnmuteCount++;
                    } else {
                        canMuteCount++;
                    }
                }

                if (unreadCount > 0) {
                    readItem.setVisibility(View.VISIBLE);
                    readItem.setTextAndIcon(LocaleController.getString("MarkAsRead", R.string.MarkAsRead), R.drawable.msg_markread);
                } else {
                    readItem.setVisibility(View.GONE);
                }
                if (canUnmuteCount != 0) {
                    mute = false;
                    muteItem.setIcon(R.drawable.msg_unmute);
                    muteItem.setContentDescription(LocaleController.getString("ChatsUnmute", R.string.ChatsUnmute));
                } else {
                    mute = true;
                    muteItem.setIcon(R.drawable.msg_mute);
                    muteItem.setContentDescription(LocaleController.getString("ChatsMute", R.string.ChatsMute));
                }

                pinItem.setVisibility(canPinCount == 1 && canUnpinCount == 0 ? View.VISIBLE : View.GONE);
                unpinItem.setVisibility(canUnpinCount == 1 && canPinCount == 0 ? View.VISIBLE : View.GONE);
            } else {
                actionBar.hideActionMode();
                return;
            }
            selectedDialogsCountTextView.setNumber(selectedTopics.size(), true);

            int canPin = 0;
            int canDeleteCount = 0;
            int closedTopicsCount = 0;
            int openTopicsCount = 0;
            int canHideCount = 0;
            int canShowCount = 0;
            Iterator<Integer> iterator = selectedTopics.iterator();
            while (iterator.hasNext()) {
                int topicId = iterator.next();
                TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                if (topic != null) {
                    if (ChatObject.canDeleteTopic(currentAccount, currentChat, topic)) {
                        canDeleteCount++;
                    }
                    if (ChatObject.canManageTopic(currentAccount, currentChat, topic)) {
                        if (topic.id == 1) {
                            if (topic.hidden) {
                                canShowCount++;
                            } else {
                                canHideCount++;
                            }
                        }
                        if (!topic.hidden) {
                            if (topic.closed) {
                                closedTopicsCount++;
                            } else {
                                openTopicsCount++;
                            }
                        }
                    }
                }
            }
            closeTopic.setVisibility(closedTopicsCount == 0 && openTopicsCount > 0 ? View.VISIBLE : View.GONE);
            closeTopic.setText(openTopicsCount > 1 ? LocaleController.getString("CloseTopics", R.string.CloseTopics) : LocaleController.getString("CloseTopic", R.string.CloseTopic));
            restartTopic.setVisibility(openTopicsCount == 0 && closedTopicsCount > 0 ? View.VISIBLE : View.GONE);
            restartTopic.setText(closedTopicsCount > 1 ? LocaleController.getString("RestartTopics", R.string.RestartTopics) : LocaleController.getString("RestartTopic", R.string.RestartTopic));
            deleteItem.setVisibility(canDeleteCount == selectedTopics.size() ? View.VISIBLE : View.GONE);
            hideItem.setVisibility(canHideCount == 1 && selectedTopics.size() == 1 ? View.VISIBLE : View.GONE);
            showItem.setVisibility(canShowCount == 1 && selectedTopics.size() == 1 ? View.VISIBLE : View.GONE);

            otherItem.checkHideMenuItem();

            updateReordering();
        }
    }

    public void updateReordering() {
        boolean canReorderPins = ChatObject.canManageTopics(getCurrentChat());
        boolean newReordering = canReorderPins && !selectedTopics.isEmpty();
        if (reordering != newReordering) {
            reordering = newReordering;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    public void sendReorder() {
        ArrayList<Integer> newOrder = new ArrayList<>();
        for (int i = 0; i < forumTopics.size(); ++i) {
            TLRPC.TL_forumTopic topic = forumTopics.get(i).topic;
            if (topic != null && topic.pinned) {
                newOrder.add(topic.id);
            }
        }
        getMessagesController().getTopicsController().reorderPinnedTopics(chatId, newOrder);
        ignoreDiffUtil = true;
    }

    private void chekActionMode() {
        if (actionBar.actionModeIsExist(null)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);

        if (inPreviewMode) {
            actionMode.setBackgroundColor(Color.TRANSPARENT);
            actionMode.drawBlur = false;
        }
        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        pinItem = actionMode.addItemWithWidth(pin_id, R.drawable.msg_pin, AndroidUtilities.dp(54));
        unpinItem = actionMode.addItemWithWidth(unpin_id, R.drawable.msg_unpin, AndroidUtilities.dp(54));
        muteItem = actionMode.addItemWithWidth(mute_id, R.drawable.msg_mute, AndroidUtilities.dp(54));
        deleteItem = actionMode.addItemWithWidth(delete_id, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));
        hideItem = actionMode.addItemWithWidth(hide_id, R.drawable.msg_archive_hide, AndroidUtilities.dp(54), LocaleController.getString("Hide", R.string.Hide));
        hideItem.setVisibility(View.GONE);
        showItem = actionMode.addItemWithWidth(show_id, R.drawable.msg_archive_show, AndroidUtilities.dp(54), LocaleController.getString("Show", R.string.Show));
        showItem.setVisibility(View.GONE);

        otherItem = actionMode.addItemWithWidth(0, R.drawable.ic_ab_other, AndroidUtilities.dp(54), LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        readItem = otherItem.addSubItem(read_id, R.drawable.msg_markread, LocaleController.getString("MarkAsRead", R.string.MarkAsRead));
        closeTopic = otherItem.addSubItem(close_topic_id, R.drawable.msg_topic_close, LocaleController.getString("CloseTopic", R.string.CloseTopic));
        restartTopic = otherItem.addSubItem(restart_topic_id, R.drawable.msg_topic_restart, LocaleController.getString("RestartTopic", R.string.RestartTopic));
    }

    private DialogCell slidingView;
    private DialogCell movingView;
    private boolean allowMoving;
    private boolean movingWas;
    private ArrayList<MessagesController.DialogFilter> movingDialogFilters = new ArrayList<>();
    private boolean waitingForScrollFinished;
    private boolean allowSwipeDuringCurrentTouch;
    private boolean updatePullAfterScroll;
    private int dialogRemoveFinished;
    private int dialogInsertFinished;
    private int dialogChangeFinished;
    private View generalTopicViewMoving;

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        private RecyclerView.ViewHolder currentItemViewHolder;
        private boolean swipingFolder;
        private boolean swipeFolderBack;

        @Override
        public boolean isLongPressDragEnabled() {
            return !selectedTopics.isEmpty();
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            if (position < 0 || position >= forumTopics.size() || forumTopics.get(position).topic == null || !ChatObject.canManageTopics(getCurrentChat())) {
                return makeMovementFlags(0, 0);
            }
            TLRPC.TL_forumTopic topic = forumTopics.get(position).topic;
            if (selectedTopics.isEmpty() && viewHolder.itemView instanceof TopicDialogCell && topic.id == 1) {
                TopicDialogCell dialogCell = (TopicDialogCell) viewHolder.itemView;
                swipeFolderBack = false;
                swipingFolder = true;
                dialogCell.setSliding(true);
                return makeMovementFlags(0, ItemTouchHelper.LEFT);
            }
            if (!topic.pinned) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            int position = target.getAdapterPosition();
            if (position < 0 || position >= forumTopics.size() || forumTopics.get(position).topic == null || !forumTopics.get(position).topic.pinned) {
                return false;
            }
            adapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                sendReorder();
            } else {
                recyclerListView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (viewHolder != null) {
                TopicDialogCell dialogCell = (TopicDialogCell) viewHolder.itemView;
                if (dialogCell.forumTopic != null) {
                    getMessagesController().getTopicsController().toggleShowTopic(chatId, dialogCell.forumTopic.id, dialogCell.forumTopic.hidden);
                }
                generalTopicViewMoving = dialogCell;
                recyclerListView.setArchiveHidden(!dialogCell.forumTopic.hidden, dialogCell);
                updateTopicsList(true, true);
                if (dialogCell.currentTopic != null) {
                    dialogCell.setTopicIcon(dialogCell.currentTopic);
                }
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    private void updateChatInfo() {
        updateChatInfo(false);
    }

    private void updateChatInfo(boolean forceAnimate) {
        if (fragmentView == null || avatarContainer == null) {
            return;
        }
        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);

        avatarContainer.setChatAvatar(chatLocal);

        long dialog_id = -chatId;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        boolean show = preferences.getInt("dialog_bar_vis3" + dialog_id, 0) == 2;
        boolean showReport = preferences.getBoolean("dialog_bar_report" + (-chatId), false);
        boolean showBlock = preferences.getBoolean("dialog_bar_block" + (-chatId), false);

        if (!opnendForSelect) {
            if (chatLocal != null) {
                avatarContainer.setTitle(chatLocal.title);
                Drawable rightIcon = null;
                if (getMessagesController().isDialogMuted(-chatId, 0)) {
                    rightIcon = getThemedDrawable(Theme.key_drawable_muteIconDrawable);
                }
                avatarContainer.setTitleIcons(null, rightIcon);
            }
            updateSubtitle();
        } else {
            if (openedForForward) {
                avatarContainer.setTitle(LocaleController.getString("ForwardTo", R.string.ForwardTo));
            } else {
                avatarContainer.setTitle(LocaleController.getString("SelectTopic", R.string.SelectTopic));
            }
            searchItem.setVisibility(View.GONE);
            if (avatarContainer != null && avatarContainer.getLayoutParams() != null) {
                ((ViewGroup.MarginLayoutParams) avatarContainer.getLayoutParams()).rightMargin = AndroidUtilities.dp(searchItem.getVisibility() == View.VISIBLE ? 86 : 40);
            }
            avatarContainer.updateSubtitle();
            avatarContainer.getSubtitleTextView().setVisibility(View.GONE);
        }
        boolean animated = fragmentBeginToShow || forceAnimate;
        boolean bottomPannelVisibleLocal;
        long requestedTime = MessagesController.getNotificationsSettings(currentAccount).getLong("dialog_join_requested_time_" + -chatId, -1);
        if (chatLocal != null && ChatObject.isNotInChat(chatLocal) && (requestedTime > 0 && System.currentTimeMillis() - requestedTime < 1000 * 60 * 2)) {
            bottomPannelVisibleLocal = true;

            bottomOverlayChatText.setText(LocaleController.getString("ChannelJoinRequestSent", R.string.ChannelJoinRequestSent), animated);
            bottomOverlayChatText.setEnabled(false);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayProgress, false, 0.5f, animated);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayChatText, true, 0.5f, animated);
            setButtonType(BOTTOM_BUTTON_TYPE_JOIN);
        } else if (chatLocal != null && !opnendForSelect && (ChatObject.isNotInChat(chatLocal) || getMessagesController().isJoiningChannel(chatLocal.id))) {
            bottomPannelVisibleLocal = true;

            boolean showProgress = false;
            if (getMessagesController().isJoiningChannel(chatLocal.id)) {
                showProgress = true;
            } else {
                if (chatLocal.join_request) {
                    bottomOverlayChatText.setText(LocaleController.getString("ChannelJoinRequest", R.string.ChannelJoinRequest));
                } else {
                    bottomOverlayChatText.setText(LocaleController.getString("ChannelJoin", R.string.ChannelJoin));
                }
                bottomOverlayChatText.setClickable(true);
                bottomOverlayChatText.setEnabled(true);
            }

            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayProgress, showProgress, 0.5f, animated);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayChatText, !showProgress, 0.5f, animated);
            setButtonType(BOTTOM_BUTTON_TYPE_JOIN);
        } else if (show && (showBlock || showReport)) {
            bottomOverlayChatText.setText(LocaleController.getString("ReportSpamAndLeave", R.string.ReportSpamAndLeave));
            bottomOverlayChatText.setClickable(true);
            bottomOverlayChatText.setEnabled(true);

            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayProgress, false, 0.5f, false);
            AndroidUtilities.updateViewVisibilityAnimated(bottomOverlayChatText, true, 0.5f, false);

            setButtonType(BOTTOM_BUTTON_TYPE_REPORT);
            bottomPannelVisibleLocal = true;
        } else {
            bottomPannelVisibleLocal = false;
        }

        if (bottomPannelVisible != bottomPannelVisibleLocal) {
            bottomPannelVisible = bottomPannelVisibleLocal;
            bottomOverlayContainer.animate().setListener(null).cancel();
            if (!animated) {
                bottomOverlayContainer.setVisibility(bottomPannelVisibleLocal ? View.VISIBLE : View.GONE);
                bottomOverlayContainer.setTranslationY(bottomPannelVisibleLocal ? 0 : AndroidUtilities.dp(53));
            } else {
                bottomOverlayContainer.animate().translationY(bottomPannelVisibleLocal ? 0 : AndroidUtilities.dp(53)).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!bottomPannelVisibleLocal) {
                            bottomOverlayContainer.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }
        updateTopView();

        other.setVisibility(opnendForSelect ? View.GONE : View.VISIBLE);
        addMemberSubMenu.setVisibility(ChatObject.canAddUsers(chatLocal) ? View.VISIBLE : View.GONE);

        deleteChatSubmenu.setVisibility(chatLocal != null && !chatLocal.creator && !ChatObject.isNotInChat(chatLocal) ? View.VISIBLE : View.GONE);
        updateCreateTopicButton(true);
        groupCall = getMessagesController().getGroupCall(chatId, true);
    }

    private void setButtonType(int bottomButtonType) {
        if (this.bottomButtonType != bottomButtonType) {
            this.bottomButtonType = bottomButtonType;
            bottomOverlayChatText.setTextColorKey(bottomButtonType == BOTTOM_BUTTON_TYPE_JOIN ? Theme.key_chat_fieldOverlayText : Theme.key_text_RedBold);
            closeReportSpam.setVisibility(bottomButtonType == BOTTOM_BUTTON_TYPE_REPORT ? View.VISIBLE : View.GONE);
            updateChatInfo();
        }
    }

    private void updateSubtitle() {
        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(chatId);
        if (this.chatFull != null && this.chatFull.participants != null) {
            chatFull.participants = this.chatFull.participants;
        }
        this.chatFull = chatFull;
        String newSubtitle;
        if (chatFull != null) {
            newSubtitle = LocaleController.formatPluralString("Members", chatFull.participants_count);
        } else {
            newSubtitle = LocaleController.getString("Loading", R.string.Loading).toLowerCase();
        }

        avatarContainer.setSubtitle(newSubtitle);
    }

    private static HashSet<Long> settingsPreloaded = new HashSet<>();

    @Override
    public boolean onFragmentCreate() {
        getMessagesController().loadFullChat(chatId, 0, true);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatSwithcedToForum);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.openedChatChanged);

        updateTopicsList(false, false);
        SelectAnimatedEmojiDialog.preload(currentAccount);

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        if (ChatObject.isChannel(chatLocal)) {
            getMessagesController().startShortPoll(chatLocal, classGuid, false);
        }
        //TODO remove when server start send in get diff
        if (!settingsPreloaded.contains(chatId)) {
            settingsPreloaded.add(chatId);
            TLRPC.TL_account_getNotifyExceptions exceptionsReq = new TLRPC.TL_account_getNotifyExceptions();
            exceptionsReq.peer = new TLRPC.TL_inputNotifyPeer();
            exceptionsReq.flags |= 1;
            ((TLRPC.TL_inputNotifyPeer) exceptionsReq.peer).peer = getMessagesController().getInputPeer(-chatId);
            getConnectionsManager().sendRequest(exceptionsReq, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_updates) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    getMessagesController().processUpdates(updates, false);
                }
            }));
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().onAnimationFinish(transitionAnimationIndex);
        NotificationCenter.getGlobalInstance().onAnimationFinish(transitionAnimationGlobalIndex);

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatSwithcedToForum);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.openedChatChanged);

        TLRPC.Chat chatLocal = getMessagesController().getChat(chatId);
        if (ChatObject.isChannel(chatLocal)) {
            getMessagesController().startShortPoll(chatLocal, classGuid, true);
        }
        super.onFragmentDestroy();

        if (parentDialogsActivity != null && parentDialogsActivity.rightSlidingDialogContainer != null) {
            parentDialogsActivity.getActionBar().setSearchAvatarImageView(null);
            parentDialogsActivity.getSearchItem().setSearchPaddingStart(0);
            parentDialogsActivity.rightSlidingDialogContainer.enabled = true;
        }
    }

    private void updateTopicsList(boolean animated, boolean enalbeEnterAnimation) {
        if (!animated && updateAnimated) {
            animated = true;
        }
        updateAnimated = false;
        ArrayList<TLRPC.TL_forumTopic> topics = topicsController.getTopics(chatId);

        if (topics != null) {
            int oldCount = forumTopics.size();
            ArrayList<Item> oldItems = new ArrayList<>(forumTopics);
            forumTopics.clear();
            for (int i = 0; i < topics.size(); i++) {
                if (excludeTopics != null && excludeTopics.contains(topics.get(i).id)) {
                    continue;
                }
                forumTopics.add(new Item(VIEW_TYPE_TOPIC, topics.get(i)));
            }
            if (!forumTopics.isEmpty() && !topicsController.endIsReached(chatId) && canShowProgress) {
                forumTopics.add(new Item(VIEW_TYPE_LOADING_CELL, null));
            }

            int newCount = forumTopics.size();
            if (fragmentBeginToShow && enalbeEnterAnimation && newCount > oldCount) {
                itemsEnterAnimator.showItemsAnimated(oldCount + 4);
                animated = false;
            }

            hiddenCount = 0;
            for (int i = 0; i < forumTopics.size(); ++i) {
                Item item = forumTopics.get(i);
                if (item != null && item.topic != null && item.topic.hidden) {
                    hiddenCount++;
                }
            }

            if (recyclerListView != null && recyclerListView.getItemAnimator() != (animated ? itemAnimator : null)) {
                recyclerListView.setItemAnimator(animated ? itemAnimator : null);
            }

            if (adapter != null) {
                adapter.setItems(oldItems, forumTopics);
            }

            if ((scrollToTop || oldCount == 0) && layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(0, 0);
                scrollToTop = false;
            }
        }

        checkLoading();
        updateTopicsEmptyViewText();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.participants != null && this.chatFull != null) {
                this.chatFull.participants = chatFull.participants;
            }
            if (chatFull.id == chatId) {
                updateChatInfo();
                if (pendingRequestsDelegate != null) {
                    pendingRequestsDelegate.setChatInfo(chatFull, true);
                }
            }
        } else if (id == NotificationCenter.topicsDidLoaded) {
            Long chatId = (Long) args[0];
            if (this.chatId == chatId) {
                updateTopicsList(false, true);
                if (args.length > 1 && (Boolean) args[1]) {
                    checkForLoadMore();
                }
                checkLoading();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (mask == MessagesController.UPDATE_MASK_CHAT) {
                updateChatInfo();
            }
            if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) > 0) {
                getMessagesController().getTopicsController().sortTopics(chatId, false);
                boolean wasOnTop = !recyclerListView.canScrollVertically(-1);
                updateTopicsList(true, false);
                if (wasOnTop) {
                    layoutManager.scrollToPosition(0);
                }
            }
        } else if (id == NotificationCenter.dialogsNeedReload) {
            updateTopicsList(false, false);
        } else if (id == NotificationCenter.groupCallUpdated) {
            Long chatId = (Long) args[0];
            if (this.chatId == chatId) {
                groupCall = getMessagesController().getGroupCall(chatId, false);
                if (fragmentContextView != null) {
                    fragmentContextView.checkCall(!fragmentBeginToShow);
                }
            }
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateTopicsList(false, false);
            updateChatInfo(true);
        } else if (id == NotificationCenter.chatSwithcedToForum) {

        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        }
        if (id == NotificationCenter.openedChatChanged) {
            if (getParentActivity() == null || !(inPreviewMode && AndroidUtilities.isTablet())) {
                return;
            }
            boolean close = (Boolean) args[2];
            long dialog_id = (Long) args[0];
            int topicId = (int) args[1];
            if (dialog_id == -chatId && !close) {
                if (selectedTopicForTablet != topicId) {
                    selectedTopicForTablet = topicId;
                    updateTopicsList(false, false);
                }
            } else {
                if (selectedTopicForTablet != 0) {
                    selectedTopicForTablet = 0;
                    updateTopicsList(false, false);
                }
            }
        }
    }

    private void checkForLoadMore() {
        if (topicsController.endIsReached(chatId) || layoutManager == null) {
            return;
        }
        int lastPosition = layoutManager.findLastVisibleItemPosition();
        if (forumTopics.isEmpty() || lastPosition >= adapter.getItemCount() - 5) {
            topicsController.loadTopics(chatId);
        }
        checkLoading();
    }

    public void setExcludeTopics(HashSet<Integer> exceptionsTopics) {
        this.excludeTopics = exceptionsTopics;
    }

    @Override
    public ChatObject.Call getGroupCall() {
        return groupCall != null && groupCall.call instanceof TLRPC.TL_groupCall ? groupCall : null;
    }

    @Override
    public TLRPC.Chat getCurrentChat() {
        return getMessagesController().getChat(chatId);
    }

    @Override
    public long getDialogId() {
        return -chatId;
    }

    public void setForwardFromDialogFragment(DialogsActivity dialogsActivity) {
        this.dialogsActivity = dialogsActivity;
    }

    private class Adapter extends AdapterWithDiffUtils {

        @Override
        public int getItemViewType(int position) {
            if (position == getItemCount() - 1) {
                return VIEW_TYPE_EMPTY;
            }
            return forumTopics.get(position).viewType;
        }

        public ArrayList<TopicsFragment.Item> getArray() {
            return (forumTopicsListFrozen ? frozenForumTopicsList : forumTopics);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_TOPIC) {
                TopicDialogCell dialogCell = new TopicDialogCell(null, parent.getContext(), true, false);
                dialogCell.inPreviewMode = inPreviewMode;
                dialogCell.setArchivedPullAnimation(pullForegroundDrawable);
                return new RecyclerListView.Holder(dialogCell);
            } else if (viewType == VIEW_TYPE_EMPTY) {
                return new RecyclerListView.Holder(emptyView = new View(getContext()) {
                    HashMap<String, Boolean> precalcEllipsized = new HashMap<>();

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int hiddenCount = 0;
                        int childrenHeight = 0, generalHeight = AndroidUtilities.dp(64);
                        for (int i = 0; i < getArray().size(); ++i) {
                            if (getArray().get(i) == null || getArray().get(i).topic == null) {
                                continue;
                            }
                            String title = getArray().get(i).topic.title;
                            Boolean oneline = precalcEllipsized.get(title);
                            if (oneline == null) {
                                int nameLeft = AndroidUtilities.dp(!LocaleController.isRTL ? (isInPreviewMode() ? 11 : 50) + 4 : 18);
                                int nameWidth = !LocaleController.isRTL ?
                                        width - nameLeft - AndroidUtilities.dp(14 + 8) :
                                        width - nameLeft - AndroidUtilities.dp((isInPreviewMode() ? 11 : 50) + 5 + 8);
                                nameWidth -= (int) Math.ceil(Theme.dialogs_timePaint.measureText("00:00"));
                                oneline = Theme.dialogs_namePaint[0].measureText(title) <= nameWidth;
                                precalcEllipsized.put(title, oneline);
                            }
                            int childHeight = AndroidUtilities.dp(64 + (!oneline ? 20 : 0));
                            if (getArray().get(i).topic.id == 1) {
                                generalHeight = childHeight;
                            }
                            if (getArray().get(i).topic.hidden) {
                                hiddenCount++;
                            }
                            childrenHeight += childHeight;
                        }
                        int height = Math.max(0, hiddenCount > 0 ? recyclerListView.getMeasuredHeight() - recyclerListView.getPaddingTop() - recyclerListView.getPaddingBottom() - childrenHeight + generalHeight : 0);
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                });
            } else {
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(parent.getContext());
                flickerLoadingView.setViewType(FlickerLoadingView.TOPIC_CELL_TYPE);
                flickerLoadingView.setIsSingleCell(true);
                flickerLoadingView.showDate(true);
                return new RecyclerListView.Holder(flickerLoadingView);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_TOPIC) {
                TLRPC.TL_forumTopic topic = getArray().get(position).topic;
                TLRPC.TL_forumTopic nextTopic = null;
                if (position + 1 < getArray().size()) {
                    nextTopic = getArray().get(position + 1).topic;
                }
                TopicDialogCell dialogCell = (TopicDialogCell) holder.itemView;

                TLRPC.Message tlMessage = topic.topMessage;
                int oldId = dialogCell.forumTopic == null ? 0 : dialogCell.forumTopic.id;
                int newId = topic.id;
                boolean animated = oldId == newId && dialogCell.position == position && animatedUpdateEnabled;
                if (tlMessage != null) {
                    MessageObject messageObject = new MessageObject(currentAccount, tlMessage, false, false);
                    dialogCell.setForumTopic(topic, -chatId, messageObject, isInPreviewMode(), animated);
                    dialogCell.drawDivider = position != forumTopics.size() - 1 || recyclerListView.emptyViewIsVisible();
                    dialogCell.fullSeparator = topic.pinned && (nextTopic == null || !nextTopic.pinned);
                    dialogCell.setPinForced(topic.pinned && !topic.hidden);
                    dialogCell.position = position;
                }

                dialogCell.setTopicIcon(topic);

                dialogCell.setChecked(selectedTopics.contains(newId), animated);
                dialogCell.setDialogSelected(selectedTopicForTablet == newId);
                dialogCell.onReorderStateChanged(reordering, true);
            }
        }

        @Override
        public int getItemCount() {
            return getArray().size() + 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_TOPIC;
        }

        public void swapElements(int from, int to) {
            if (forumTopicsListFrozen) {
                return;
            }

            forumTopics.add(to, forumTopics.remove(from));
            if (recyclerListView.getItemAnimator() != itemAnimator) {
                recyclerListView.setItemAnimator(itemAnimator);
            }
            notifyItemMoved(from, to);
        }

        @Override
        public void notifyDataSetChanged() {
            lastItemsCount = getItemCount();
            super.notifyDataSetChanged();
        }
    }

    public class TopicDialogCell extends DialogCell {

        public boolean drawDivider;
        public int position = -1;

        public TopicDialogCell(DialogsActivity fragment, Context context, boolean needCheck, boolean forceThreeLines) {
            super(fragment, context, needCheck, forceThreeLines);
            drawAvatar = false;
            messagePaddingStart = isInPreviewMode() ? 11 : 50;
            chekBoxPaddingTop = 24;
            heightDefault = 64;
            heightThreeLines = 76;
            forbidVerified = true;
        }

        private TLRPC.TL_forumTopic currentTopic;
        private AnimatedEmojiDrawable animatedEmojiDrawable;
        private Drawable forumIcon;
        boolean attached;
        private boolean isGeneral;
        private boolean closed;

        @Override
        protected void onDraw(Canvas canvas) {
            xOffset = inPreviewMode && checkBox != null ? checkBox.getProgress() * AndroidUtilities.dp(30) : 0;
            canvas.save();
            canvas.translate(xOffset, translateY = -AndroidUtilities.dp(4));
            canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));
            super.onDraw(canvas);
            canvas.restore();
            canvas.save();
            canvas.translate(super.translationX, 0);
            if (drawDivider) {
                int left = fullSeparator ? 0 : AndroidUtilities.dp(messagePaddingStart);
                if (LocaleController.isRTL) {
                    canvas.drawLine(0 - super.translationX, getMeasuredHeight() - 1, getMeasuredWidth() - left, getMeasuredHeight() - 1, Theme.dividerPaint);
                } else {
                    canvas.drawLine(left - super.translationX, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
            if ((!isGeneral || archivedChatsDrawable == null || archivedChatsDrawable.outProgress != 0.0f) && (animatedEmojiDrawable != null || forumIcon != null)) {
                int padding = AndroidUtilities.dp(10);
                int paddingTop = AndroidUtilities.dp(10);
                int size = AndroidUtilities.dp(28);
                if (animatedEmojiDrawable != null) {
                    if (LocaleController.isRTL) {
                        animatedEmojiDrawable.setBounds(getWidth() - padding - size, paddingTop, getWidth() - padding, paddingTop + size);
                    } else {
                        animatedEmojiDrawable.setBounds(padding, paddingTop, padding + size, paddingTop + size);
                    }
                    animatedEmojiDrawable.draw(canvas);
                } else {
                    if (LocaleController.isRTL) {
                        forumIcon.setBounds(getWidth() - padding - size, paddingTop, getWidth() - padding, paddingTop + size);
                    } else {
                        forumIcon.setBounds(padding, paddingTop, padding + size, paddingTop + size);
                    }
                    forumIcon.draw(canvas);
                }
            }
            canvas.restore();
        }

        @Override
        public void buildLayout() {
            super.buildLayout();
            setHiddenT();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(this);
            }
        }

        public void setAnimatedEmojiDrawable(AnimatedEmojiDrawable animatedEmojiDrawable) {
            if (this.animatedEmojiDrawable == animatedEmojiDrawable) {
                return;
            }
            if (this.animatedEmojiDrawable != null && attached) {
                this.animatedEmojiDrawable.removeView(this);
            }
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.setColorFilter(Theme.chat_animatedEmojiTextColorFilter);
            }
            this.animatedEmojiDrawable = animatedEmojiDrawable;
            if (animatedEmojiDrawable != null && attached) {
                animatedEmojiDrawable.addView(this);
            }
        }

        public void setForumIcon(Drawable drawable) {
            forumIcon = drawable;
        }

        public void setTopicIcon(TLRPC.TL_forumTopic topic) {
            currentTopic = topic;
            closed = topic != null && topic.closed;
            if (inPreviewMode) {
                updateHidden(topic != null && topic.hidden, true);
            }
            isGeneral = topic != null && topic.id == 1;
            if (topic != null && this != generalTopicViewMoving) {
                if (topic.hidden) {
                    overrideSwipeAction = true;
                    overrideSwipeActionBackgroundColorKey = Theme.key_chats_archivePinBackground;
                    overrideSwipeActionRevealBackgroundColorKey = Theme.key_chats_archiveBackground;
                    overrideSwipeActionStringKey = "Unhide";
                    overrideSwipeActionStringId = R.string.Unhide;
                    overrideSwipeActionDrawable = Theme.dialogs_unpinArchiveDrawable;
                } else {
                    overrideSwipeAction = true;
                    overrideSwipeActionBackgroundColorKey = Theme.key_chats_archiveBackground;
                    overrideSwipeActionRevealBackgroundColorKey = Theme.key_chats_archivePinBackground;
                    overrideSwipeActionStringKey = "Hide";
                    overrideSwipeActionStringId = R.string.Hide;
                    overrideSwipeActionDrawable = Theme.dialogs_pinArchiveDrawable;
                }
                invalidate();
            }

            if (inPreviewMode) {
                return;
            }
            if (topic != null && topic.id == 1) {
                setAnimatedEmojiDrawable(null);
                setForumIcon(ForumUtilities.createGeneralTopicDrawable(getContext(), 1f, getThemedColor(Theme.key_chat_inMenu)));
            } else if (topic != null && topic.icon_emoji_id != 0) {
                setForumIcon(null);
                if (animatedEmojiDrawable == null || animatedEmojiDrawable.getDocumentId() != topic.icon_emoji_id) {
                    setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(openedForForward ? AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC : AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, currentAccount, topic.icon_emoji_id));
                }
            } else {
                setAnimatedEmojiDrawable(null);
                setForumIcon(ForumUtilities.createTopicDrawable(topic));
            }
            updateHidden(topic != null && topic.hidden, true);

            buildLayout();
        }

        private Boolean hidden;
        private float hiddenT;
        private ValueAnimator hiddenAnimator;

        private void updateHidden(boolean hidden, boolean animated) {
            if (this.hidden == null) {
                animated = false;
            }

            if (hiddenAnimator != null) {
                hiddenAnimator.cancel();
                hiddenAnimator = null;
            }

            this.hidden = hidden;
            if (animated) {
                hiddenAnimator = ValueAnimator.ofFloat(hiddenT, hidden ? 1f : 0f);
                hiddenAnimator.addUpdateListener(anm -> {
                    hiddenT = (float) anm.getAnimatedValue();
                    setHiddenT();
                });
                hiddenAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                hiddenAnimator.start();
            } else {
                hiddenT = hidden ? 1f : 0f;
                setHiddenT();
            }
        }

        private void setHiddenT() {
            if (forumIcon instanceof ForumUtilities.GeneralTopicDrawable) {
                ((ForumUtilities.GeneralTopicDrawable) forumIcon).setColor(
                        ColorUtils.blendARGB(getThemedColor(Theme.key_chats_archivePullDownBackground), getThemedColor(Theme.key_avatar_background2Saved), hiddenT)
                );
            }
            if (topicIconInName != null && topicIconInName[0] instanceof ForumUtilities.GeneralTopicDrawable) {
                ((ForumUtilities.GeneralTopicDrawable) topicIconInName[0]).setColor(
                        ColorUtils.blendARGB(getThemedColor(Theme.key_chats_archivePullDownBackground), getThemedColor(Theme.key_avatar_background2Saved), hiddenT)
                );
            }
            invalidate();
        }

        @Override
        protected boolean drawLock2() {
            return closed;
        }
    }

    private void hideFloatingButton(boolean hide, boolean animated) {
        if (floatingHidden == hide) {
            return;
        }
        floatingHidden = hide;
        boolean animatedLocal = fragmentBeginToShow && animated;
        if (animatedLocal) {
            AnimatorSet animatorSet = new AnimatorSet();
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(floatingButtonHideProgress, floatingHidden ? 1f : 0f);
            valueAnimator.addUpdateListener(animation -> {
                floatingButtonHideProgress = (float) animation.getAnimatedValue();
                updateFloatingButtonOffset();
            });
            animatorSet.playTogether(valueAnimator);
            animatorSet.setDuration(300);
            animatorSet.setInterpolator(floatingInterpolator);
            animatorSet.start();
        } else {
            floatingButtonHideProgress = floatingHidden ? 1f : 0f;

            updateFloatingButtonOffset();
        }
        floatingButtonContainer.setClickable(!hide);
    }

    private void updateFloatingButtonOffset() {
        floatingButtonTranslation = AndroidUtilities.dp(100) * floatingButtonHideProgress - transitionPadding;
        floatingButtonContainer.setTranslationY(floatingButtonTranslation);
    }

    @Override
    public void onBecomeFullyHidden() {
        if (actionBar != null) {
            actionBar.closeSearchField();
        }
    }

    private class EmptyViewContainer extends FrameLayout {

        TextView textView;

        public EmptyViewContainer(Context context) {
            super(context);
            textView = new TextView(context);
            SpannableStringBuilder spannableStringBuilder;
            if (LocaleController.isRTL) {
                spannableStringBuilder = new SpannableStringBuilder("  ");
                spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.attach_arrow_left), 0, 1, 0);
                spannableStringBuilder.append(LocaleController.getString("TapToCreateTopicHint", R.string.TapToCreateTopicHint));
            } else {
                spannableStringBuilder = new SpannableStringBuilder(LocaleController.getString("TapToCreateTopicHint", R.string.TapToCreateTopicHint));
                spannableStringBuilder.append("  ");
                spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.arrow_newchat), spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            }
            textView.setText(spannableStringBuilder);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setLayerType(LAYER_TYPE_HARDWARE, null);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, getResourceProvider()));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, LocaleController.isRTL ? 72 : 32, 0, LocaleController.isRTL ? 32 : 72, 32));
        }

        float progress;
        boolean increment;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (increment) {
                progress += 16 / 1200f;
                if (progress > 1) {
                    increment = false;
                    progress = 1;
                }
            } else {
                progress -= 16 / 1200f;
                if (progress < 0) {
                    increment = true;
                    progress = 0;
                }
            }
            textView.setTranslationX(CubicBezierInterpolator.DEFAULT.getInterpolation(progress) * AndroidUtilities.dp(8) * (LocaleController.isRTL ? -1 : 1));
            invalidate();
        }
    }

    @Override
    public boolean isLightStatusBar() {
        int color = searching ? Theme.getColor(Theme.key_windowBackgroundWhite) : Theme.getColor(Theme.key_actionBarDefault);
        if (actionBar.isActionModeShowed()) {
            color = Theme.getColor(Theme.key_actionBarActionModeDefault);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    private class MessagesSearchContainer extends ViewPagerFixed implements FilteredSearchView.UiCallback {

        FrameLayout searchContainer;

        RecyclerListView recyclerView;
        LinearLayoutManager layoutManager;
        SearchAdapter searchAdapter;
        Runnable searchRunnable;
        String searchString = "empty";

        ArrayList<TLRPC.TL_forumTopic> searchResultTopics = new ArrayList<>();
        ArrayList<MessageObject> searchResultMessages = new ArrayList<>();

        int topicsHeaderRow;
        int topicsStartRow;
        int topicsEndRow;

        int messagesHeaderRow;
        int messagesStartRow;
        int messagesEndRow;

        int rowCount;

        boolean isLoading;
        boolean canLoadMore;

        FlickerLoadingView flickerLoadingView;
        StickerEmptyView emptyView;
        RecyclerItemsEnterAnimator itemsEnterAnimator;
        boolean messagesIsLoading;
        private int keyboardSize;
        private ViewPagerAdapter viewPagerAdapter;
        SearchViewPager.ChatPreviewDelegate chatPreviewDelegate;

        public MessagesSearchContainer(@NonNull Context context) {
            super(context);

            searchContainer = new FrameLayout(context);
            chatPreviewDelegate = new SearchViewPager.ChatPreviewDelegate() {
                @Override
                public void startChatPreview(RecyclerListView listView, DialogCell cell) {
                    showChatPreview(cell);
                }

                @Override
                public void move(float dy) {
                    if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        movePreviewFragment(dy);
                    }
                }

                @Override
                public void finish() {
                    if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                        finishPreviewFragment();
                    }
                }
            };

            recyclerView = new RecyclerListView(context);
            recyclerView.setAdapter(searchAdapter = new SearchAdapter());
            recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
            recyclerView.setOnItemClickListener((view, position) -> {
                if (view instanceof TopicSearchCell) {
                    TopicSearchCell cell = (TopicSearchCell) view;
                    ForumUtilities.openTopic(TopicsFragment.this, chatId, cell.getTopic(), 0);
                } else if (view instanceof TopicDialogCell) {
                    TopicDialogCell cell = (TopicDialogCell) view;
                    ForumUtilities.openTopic(TopicsFragment.this, chatId, cell.forumTopic, cell.getMessageId());
                }
            });
            recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (canLoadMore) {
                        int lastPosition = layoutManager.findLastVisibleItemPosition();
                        if (lastPosition + 5 >= rowCount) {
                            loadMessages(searchString);
                        }
                    }

                    if (searching && (dx != 0 || dy != 0)) {
                        AndroidUtilities.hideKeyboard(searchItem.getSearchField());
                    }
                }
            });

            flickerLoadingView = new FlickerLoadingView(context);
            flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
            flickerLoadingView.showDate(false);
            flickerLoadingView.setUseHeaderOffset(true);

            emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
            emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
            emptyView.subtitle.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            emptyView.addView(flickerLoadingView, 0);
            emptyView.setAnimateLayoutChange(true);

            recyclerView.setEmptyView(emptyView);
            recyclerView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
            searchContainer.addView(emptyView);
            searchContainer.addView(recyclerView);
            updateRows();

            itemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerView, true);
            recyclerView.setItemsEnterAnimator(itemsEnterAnimator);

            setAdapter(viewPagerAdapter = new ViewPagerAdapter());
        }

        class Item {
            private final int type;
            int filterIndex;

            private Item(int type) {
                this.type = type;
            }
        }

        private class ViewPagerAdapter extends ViewPagerFixed.Adapter {

            ArrayList<Item> items = new ArrayList<>();

            public ViewPagerAdapter() {
                items.add(new Item(DIALOGS_TYPE));
                Item item = new Item(FILTER_TYPE);
                item.filterIndex = 0;
                items.add(item);
//                items.add(new Item(DOWNLOADS_TYPE));
                item = new Item(FILTER_TYPE);
                item.filterIndex = 1;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 2;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 3;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 4;
                items.add(item);
            }

            private final static int DIALOGS_TYPE = 0;
            private final static int DOWNLOADS_TYPE = 1;
            private final static int FILTER_TYPE = 2;

            @Override
            public int getItemCount() {
                return items.size();
            }

            @Override
            public View createView(int viewType) {
                if (viewType == 1) {
                    return searchContainer;
                } else if (viewType == 2) {
                    SearchDownloadsContainer downloadsContainer = new SearchDownloadsContainer(TopicsFragment.this, currentAccount);
                    downloadsContainer.recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);
//                                fragmentView.invalidateBlur();
                        }
                    });
                    downloadsContainer.setUiCallback(MessagesSearchContainer.this);
                    return downloadsContainer;
                } else {
                    FilteredSearchView filteredSearchView = new FilteredSearchView(TopicsFragment.this);
                    filteredSearchView.setChatPreviewDelegate(chatPreviewDelegate);
                    filteredSearchView.setUiCallback(MessagesSearchContainer.this);
                    filteredSearchView.recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);
//                                fragmentView.invalidateBlur();
                        }
                    });
                    return filteredSearchView;
                }
            }

            @Override
            public String getItemTitle(int position) {
                if (items.get(position).type == DIALOGS_TYPE) {
                    return LocaleController.getString("SearchMessages", R.string.SearchMessages);
                } else if (items.get(position).type == DOWNLOADS_TYPE) {
                    return LocaleController.getString("DownloadsTabs", R.string.DownloadsTabs);
                } else {
                    return FiltersView.filters[items.get(position).filterIndex].title;
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (items.get(position).type == DIALOGS_TYPE) {
                    return 1;
                }
                if (items.get(position).type == DOWNLOADS_TYPE) {
                    return 2;
                }
                return items.get(position).type + position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                search(view, position, searchString, true);
            }
        }

        @Override
        public void goToMessage(MessageObject messageObject) {
            Bundle args = new Bundle();
            long dialogId = messageObject.getDialogId();
            if (DialogObject.isEncryptedDialog(dialogId)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
            } else if (DialogObject.isUserDialog(dialogId)) {
                args.putLong("user_id", dialogId);
            } else {
                TLRPC.Chat chat = AccountInstance.getInstance(currentAccount).getMessagesController().getChat(-dialogId);
                if (chat != null && chat.migrated_to != null) {
                    args.putLong("migrated_to", dialogId);
                    dialogId = -chat.migrated_to.channel_id;
                }
                args.putLong("chat_id", -dialogId);
            }
            args.putInt("message_id", messageObject.getId());
            TopicsFragment.this.presentFragment(new ChatActivity(args));
//                showActionMode(false);
        }

        private ArrayList<MessageObject> selectedItems = new ArrayList<>();

        @Override
        public boolean actionModeShowing() {
            return actionBar.isActionModeShowed();
        }

        @Override
        public void toggleItemSelection(MessageObject item, View view, int a) {
            if (!selectedItems.remove(item)) {
                selectedItems.add(item);
            }
            if (selectedItems.isEmpty()) {
                actionBar.hideActionMode();
            }
        }

        @Override
        public boolean isSelected(FilteredSearchView.MessageHashId messageHashId) {
            if (messageHashId == null) {
                return false;
            }
            for (int i = 0; i < selectedItems.size(); ++i) {
                MessageObject msg = selectedItems.get(i);
                if (msg != null && msg.getId() == messageHashId.messageId && msg.getDialogId() == messageHashId.dialogId) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void showActionMode() {
            actionBar.showActionMode();
        }

        @Override
        public int getFolderId() {
            return 0;
        }

        private void search(View view, int position, String query, boolean reset) {
            long minDate = 0;
            long maxDate = 0;
            boolean includeFolder = false;

            this.searchString = query;
            if (view == searchContainer) {
                searchMessages(query);
            } else if (view instanceof FilteredSearchView) {
                ((FilteredSearchView) view).setKeyboardHeight(keyboardSize, false);
                Item item = viewPagerAdapter.items.get(position);
                ((FilteredSearchView) view).search(-chatId, minDate, maxDate, FiltersView.filters[item.filterIndex], includeFolder, query, reset);
            } else if (view instanceof SearchDownloadsContainer) {
                ((SearchDownloadsContainer) view).setKeyboardHeight(keyboardSize, false);
                ((SearchDownloadsContainer) view).search(query);
            }
        }

        private void searchMessages(String searchString) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }

//            AndroidUtilities.updateViewVisibilityAnimated(TopicsFragment.this.searchContainer, !TextUtils.isEmpty(searchString), 1f, true);

            messagesIsLoading = false;
            canLoadMore = false;
            searchResultTopics.clear();
            searchResultMessages.clear();
            updateRows();
            if (TextUtils.isEmpty(searchString)) {
                isLoading = false;
                searchResultTopics.clear();
                for (int i = 0; i < forumTopics.size(); i++) {
                    if (forumTopics.get(i).topic != null) {
                        searchResultTopics.add(forumTopics.get(i).topic);
                        forumTopics.get(i).topic.searchQuery = null;
                    }
                }
                updateRows();
                // emptyView.showProgress(true, true);
                return;
            } else {
                updateRows();
            }

            isLoading = true;
            emptyView.showProgress(isLoading, true);

            searchRunnable = () -> {
                String searchTrimmed = searchString.trim().toLowerCase();
                ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
                for (int i = 0; i < forumTopics.size(); i++) {
                    if (forumTopics.get(i).topic != null && forumTopics.get(i).topic.title.toLowerCase().contains(searchTrimmed)) {
                        topics.add(forumTopics.get(i).topic);
                        forumTopics.get(i).topic.searchQuery = searchTrimmed;
                    }
                }

                searchResultTopics.clear();
                searchResultTopics.addAll(topics);
                updateRows();

                if (!searchResultTopics.isEmpty()) {
                    isLoading = false;
                    //   emptyView.showProgress(isLoading, true);
                    itemsEnterAnimator.showItemsAnimated(0);
                }

                loadMessages(searchString);
            };
            AndroidUtilities.runOnUIThread(searchRunnable, 200);
        }

        public void setSearchString(String searchString) {
            if (this.searchString.equals(searchString)) {
                return;
            }
            search(viewPages[0], getCurrentPosition(), searchString, false);
        }

        private void loadMessages(String searchString) {
            if (messagesIsLoading) {
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = getMessagesController().getInputPeer(-chatId);
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.limit = 20;
            req.q = searchString;
            if (!searchResultMessages.isEmpty()) {
                req.offset_id = searchResultMessages.get(searchResultMessages.size() - 1).getId();
            }
            messagesIsLoading = true;
//            if (query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
//                MessageObject lastMessage = searchResultMessages.get(searchResultMessages.size() - 1);
//                req.offset_id = lastMessage.getId();
//                req.offset_rate = nextSearchRate;
//                long id = MessageObject.getPeerId(lastMessage.messageOwner.peer_id);
//                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
//            } else {
//                req.offset_rate = 0;
//                req.offset_id = 0;
//                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
//            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (searchString.equals(this.searchString)) {
                    int oldRowCount = rowCount;
                    messagesIsLoading = false;
                    isLoading = false;
                    if (response instanceof TLRPC.messages_Messages) {
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;

                        for (int i = 0; i < messages.messages.size(); i++) {
                            TLRPC.Message message = messages.messages.get(i);
                            MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                            messageObject.setQuery(searchString);
                            searchResultMessages.add(messageObject);
                        }
                        updateRows();
                        canLoadMore = searchResultMessages.size() < messages.count && !messages.messages.isEmpty();
                    } else {
                        canLoadMore = false;
                    }

                    if (rowCount == 0) {
                        emptyView.showProgress(isLoading, true);
                    }
                    itemsEnterAnimator.showItemsAnimated(oldRowCount);
                }
            }));
        }

        private void updateRows() {
            topicsHeaderRow = -1;
            topicsStartRow = -1;
            topicsEndRow = -1;
            messagesHeaderRow = -1;
            messagesStartRow = -1;
            messagesEndRow = -1;

            rowCount = 0;

            if (!searchResultTopics.isEmpty()) {
                topicsHeaderRow = rowCount++;
                topicsStartRow = rowCount;
                rowCount += searchResultTopics.size();
                topicsEndRow = rowCount;
            }

            if (!searchResultMessages.isEmpty()) {
                messagesHeaderRow = rowCount++;
                messagesStartRow = rowCount;
                rowCount += searchResultMessages.size();
                messagesEndRow = rowCount;
            }

            searchAdapter.notifyDataSetChanged();
        }

        private class SearchAdapter extends RecyclerListView.SelectionAdapter {

            private final static int VIEW_TYPE_HEADER = 1;
            private final static int VIEW_TYPE_TOPIC = 2;
            private final static int VIEW_TYPE_MESSAGE = 3;

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                switch (viewType) {
                    case VIEW_TYPE_HEADER:
                        view = new GraySectionCell(parent.getContext());
                        break;
                    case VIEW_TYPE_TOPIC:
                        view = new TopicSearchCell(parent.getContext());
                        break;
                    case VIEW_TYPE_MESSAGE:
                        view = new TopicDialogCell(null, parent.getContext(), false, true);
                        ((TopicDialogCell) view).inPreviewMode = inPreviewMode;
                        break;
                    default:
                        throw new RuntimeException("unsupported view type");
                }


                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (getItemViewType(position) == VIEW_TYPE_HEADER) {
                    GraySectionCell headerCell = (GraySectionCell) holder.itemView;
                    if (position == topicsHeaderRow) {
                        headerCell.setText(LocaleController.getString("Topics", R.string.Topics));
                    }
                    if (position == messagesHeaderRow) {
                        headerCell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
                    }
                }
                if (getItemViewType(position) == VIEW_TYPE_TOPIC) {
                    TLRPC.TL_forumTopic topic = searchResultTopics.get(position - topicsStartRow);
                    TopicSearchCell topicSearchCell = (TopicSearchCell) holder.itemView;
                    topicSearchCell.setTopic(topic);
                    topicSearchCell.drawDivider = position != topicsEndRow - 1;
                }
                if (getItemViewType(position) == VIEW_TYPE_MESSAGE) {
                    MessageObject message = searchResultMessages.get(position - messagesStartRow);
                    TopicDialogCell dialogCell = (TopicDialogCell) holder.itemView;
                    dialogCell.drawDivider = position != messagesEndRow - 1;
                    int topicId = MessageObject.getTopicId(message.messageOwner, true);
                    if (topicId == 0) {
                        topicId = 1;
                    }
                    TLRPC.TL_forumTopic topic = topicsController.findTopic(chatId, topicId);
                    if (topic == null) {
                        FileLog.d("cant find topic " + topicId);
                    } else {
                        dialogCell.setForumTopic(topic, message.getDialogId(), message, false, false);
                        dialogCell.setTopicIcon(topic);
                    }
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == messagesHeaderRow || position == topicsHeaderRow) {
                    return VIEW_TYPE_HEADER;
                }
                if (position >= topicsStartRow && position < topicsEndRow) {
                    return VIEW_TYPE_TOPIC;
                }
                if (position >= messagesStartRow && position < messagesEndRow) {
                    return VIEW_TYPE_MESSAGE;
                }
                return 0;
            }

            @Override
            public int getItemCount() {
                if (isLoading) {
                    return 0;
                }
                return rowCount;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_MESSAGE || holder.getItemViewType() == VIEW_TYPE_TOPIC;
            }
        }
    }

    public void setOnTopicSelectedListener(OnTopicSelectedListener listener) {
        onTopicSelectedListener = listener;
    }

    public interface OnTopicSelectedListener {
        void onTopicSelected(TLRPC.TL_forumTopic topic);
    }

    @Override
    public void onResume() {
        super.onResume();
        getMessagesController().getTopicsController().onTopicFragmentResume(chatId);
        animatedUpdateEnabled = false;
        AndroidUtilities.updateVisibleRows(recyclerListView);
        animatedUpdateEnabled = true;
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return bottomOverlayContainer != null && bottomOverlayContainer.getVisibility() == View.VISIBLE ? bottomOverlayContainer.getMeasuredHeight() : 0;
            }
        });
        if (inPreviewMode && !getMessagesController().isForum(-chatId)) {
            finishFragment();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getMessagesController().getTopicsController().onTopicFragmentPause(chatId);
        Bulletin.removeDelegate(this);
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        if (!topFragment && beginSlide) {
            isSlideBackTransition = true;
            setFragmentIsSliding(true);
        } else {
            slideBackTransitionAnimator = null;
            isSlideBackTransition = false;
            setFragmentIsSliding(false);
            setSlideTransitionProgress(1f);
        }
    }

    private void setFragmentIsSliding(boolean sliding) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        ViewGroup v = contentView;
        if (v != null) {
            if (sliding) {
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                v.setClipChildren(false);
                v.setClipToPadding(false);
            } else {
                v.setLayerType(View.LAYER_TYPE_NONE, null);
                v.setClipChildren(true);
                v.setClipToPadding(true);
            }
        }
        contentView.requestLayout();
        actionBar.requestLayout();
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        if (isSlideBackTransition && slideBackTransitionAnimator == null) {
            setSlideTransitionProgress(progress);
        }
    }

    private void setSlideTransitionProgress(float progress) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        slideFragmentProgress = progress;
        if (fragmentView != null) {
            fragmentView.invalidate();
        }

        View v = recyclerListView;
        if (v != null) {
            float s = 1f - 0.05f * (1f - slideFragmentProgress);
            v.setPivotX(0);
            v.setPivotY(0);
            v.setScaleX(s);
            v.setScaleY(s);

            topView.setPivotX(0);
            topView.setPivotY(0);
            topView.setScaleX(s);
            topView.setScaleY(s);

            actionBar.setPivotX(0);
            actionBar.setPivotY(0);
            actionBar.setScaleX(s);
            actionBar.setScaleY(s);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);

        transitionAnimationIndex = getNotificationCenter().setAnimationInProgress(transitionAnimationIndex, new int[]{NotificationCenter.topicsDidLoaded});
        transitionAnimationGlobalIndex = NotificationCenter.getGlobalInstance().setAnimationInProgress(transitionAnimationGlobalIndex, new int[0]);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && blurredView != null) {
            if (blurredView.getParent() != null) {
                ((ViewGroup) blurredView.getParent()).removeView(blurredView);
            }
            blurredView.setBackground(null);
        }

        getNotificationCenter().onAnimationFinish(transitionAnimationIndex);
        NotificationCenter.getGlobalInstance().onAnimationFinish(transitionAnimationGlobalIndex);

        if (!isOpen && (opnendForSelect && removeFragmentOnTransitionEnd)) {
            removeSelfFromStack();
            if (dialogsActivity != null) {
                dialogsActivity.removeSelfFromStack();
            }
        }
    }

    @Override
    public void drawOverlay(Canvas canvas, View parent) {
        canvas.save();
        canvas.translate(contentView.getX(), contentView.getY());
        if (fragmentContextView != null && fragmentContextView.isCallStyle()) {
            float alpha = 1f;//(blurredView != null && blurredView.getVisibility() == View.VISIBLE) ? 1f - blurredView.getAlpha() : 1f;
            if (alpha > 0) {
                if (alpha == 1f) {
                    canvas.save();
                } else {
                    canvas.saveLayerAlpha(fragmentContextView.getX(), topView.getY() + fragmentContextView.getY() - AndroidUtilities.dp(30), fragmentContextView.getX() + fragmentContextView.getMeasuredWidth(), topView.getY() + fragmentContextView.getY() + fragmentContextView.getMeasuredHeight(), (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
                }
                canvas.translate(fragmentContextView.getX(), topView.getY() + fragmentContextView.getY());
                fragmentContextView.setDrawOverlay(true);
                fragmentContextView.draw(canvas);
                fragmentContextView.setDrawOverlay(false);
                canvas.restore();
            }
            parent.invalidate();
        }
        canvas.restore();
    }

    private void prepareBlurBitmap() {
        if (blurredView == null || parentLayout == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        parentLayout.getView().draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        if (blurredView.getParent() != null) {
            ((ViewGroup) blurredView.getParent()).removeView(blurredView);
        }
        parentLayout.getOverlayContainerView().addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    public boolean onBackPressed() {
        if (!selectedTopics.isEmpty()) {
            clearSelectedTopics();
            return false;
        }
        if (searching) {
            actionBar.onSearchFieldVisibilityChanged(searchItem.toggleSearch(false));
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
            } else {
                blurredView.setAlpha(progress);
            }
        }
    }

    private class Item extends AdapterWithDiffUtils.Item {

        TLRPC.TL_forumTopic topic;

        public Item(int viewType, TLRPC.TL_forumTopic topic) {
            super(viewType, true);
            this.topic = topic;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            if (viewType == item.viewType && viewType == VIEW_TYPE_TOPIC) {
                return topic.id == item.topic.id;
            }
            return false;

        }
    }

    @Override
    public ChatAvatarContainer getAvatarContainer() {
        return avatarContainer;
    }

    @Override
    public SizeNotifierFrameLayout getContentView() {
        return contentView;
    }

    @Override
    public void setPreviewOpenedProgress(float progress) {
        if (avatarContainer != null) {
            avatarContainer.setAlpha(progress);
            other.setAlpha(progress);
            actionBar.getBackButton().setAlpha(progress == 1f ? 1f : 0);
        }
    }

    @Override
    public void setPreviewReplaceProgress(float progress) {
        if (avatarContainer != null) {
            avatarContainer.setAlpha(progress);
            avatarContainer.setTranslationX((1f - progress) * AndroidUtilities.dp(40));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            for (int b = 0; b < 2; b++) {
                RecyclerListView list = null;
                if (b == 0) {
                    list = recyclerListView;
                } else if (searchContainer != null) {
                    list = searchContainer.recyclerView;
                }
                if (list == null) {
                    continue;
                }
                int count = list.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = list.getChildAt(a);
                    if (child instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) child).update(0);
                    } else if (child instanceof DialogCell) {
                        ((DialogCell) child).update(0);
                    } else if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
            if (actionBar != null) {
                actionBar.setPopupBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), true);
                actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false, true);
                actionBar.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true, true);
                actionBar.setPopupItemsSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector), true);
            }
            if (blurredView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    blurredView.setForeground(new ColorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), 100)));
                }
            }
            updateColors();
        };

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        if (searchContainer != null && searchContainer.recyclerView != null) {
            GraySectionCell.createThemeDescriptions(arrayList, searchContainer.recyclerView);
        }
        return arrayList;
    }
}

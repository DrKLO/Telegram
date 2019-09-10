/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerMiddle;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.DialogsAdapter;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Cells.AccountSelectCell;
import org.telegram.ui.Cells.ArchiveHintInnerCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsItemAnimator;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.PacmanAnimation;
import org.telegram.ui.Components.ProxyDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

public class DialogsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private DialogsAdapter dialogsAdapter;
    private DialogsSearchAdapter dialogsSearchAdapter;
    private EmptyTextProgressView searchEmptyView;
    private RadialProgressView progressView;
    private ActionBarMenuItem passcodeItem;
    private ActionBarMenuItem proxyItem;
    private ProxyDrawable proxyDrawable;
    private ImageView floatingButton;
    private FrameLayout floatingButtonContainer;
    private UndoView[] undoView = new UndoView[2];
    private SwipeController swipeController;
    private ItemTouchHelper itemTouchhelper;

    private int lastItemsCount;

    private int messagesCount;

    private PacmanAnimation pacmanAnimation;

    private DialogCell slidingView;
    private DialogCell movingView;
    private boolean allowMoving;
    private boolean movingWas;
    private boolean waitingForScrollFinished;
    private boolean allowSwipeDuringCurrentTouch;

    private MenuDrawable menuDrawable;
    private BackDrawable backDrawable;

    private NumberTextView selectedDialogsCountTextView;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem deleteItem;
    private ActionBarMenuItem pinItem;
    private ActionBarMenuItem muteItem;
    private ActionBarMenuSubItem archiveItem;
    private ActionBarMenuSubItem clearItem;
    private ActionBarMenuSubItem readItem;

    private float additionalFloatingTranslation;

    //private ImageView unreadFloatingButton;
    //private FrameLayout unreadFloatingButtonContainer;
    //private TextView unreadFloatingButtonCounter;
    //private int currentUnreadCount;

    private AnimatedArrowDrawable arrowDrawable;
    private RecyclerView sideMenu;
    private ChatActivityEnterView commentView;
    private ActionBarMenuItem switchItem;

    private static ArrayList<TLRPC.Dialog> frozenDialogsList;
    private boolean dialogsListFrozen;
    private int dialogRemoveFinished;
    private int dialogInsertFinished;
    private int dialogChangeFinished;
    private DialogsItemAnimator dialogsItemAnimator;

    private AlertDialog permissionDialog;
    private boolean askAboutContacts = true;

    private boolean proxyItemVisisble;
    private boolean closeSearchFieldOnHide;
    private long searchDialogId;
    private TLObject searchObject;

    private int prevPosition;
    private int prevTop;
    private boolean scrollUpdated;
    private boolean floatingHidden;
    private final AccelerateDecelerateInterpolator floatingInterpolator = new AccelerateDecelerateInterpolator();

    private boolean checkPermission = true;

    private int currentConnectionState;

    private String selectAlertString;
    private String selectAlertStringGroup;
    private String addToGroupAlertString;
    private boolean resetDelegate = true;
    private int dialogsType;

    public static boolean[] dialogsLoaded = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private boolean searching;
    private boolean searchWas;
    private boolean onlySelect;
    private String searchString;
    private long openedDialogId;
    private boolean cantSendToChannels;
    private boolean allowSwitchAccount;
    private boolean checkCanWrite;

    private DialogsActivityDelegate delegate;

    private int canReadCount;
    private int canPinCount;
    private int canMuteCount;
    private int canUnmuteCount;
    private int canClearCacheCount;
    
    private int folderId;

    private final static int pin = 100;
    private final static int read = 101;
    private final static int delete = 102;
    private final static int clear = 103;
    private final static int mute = 104;
    private final static int archive = 105;

    private boolean allowScrollToHiddenView;
    private boolean scrollingManually;
    private int totalConsumedAmount;
    private boolean startedScrollAtTop;

    private class ContentView extends SizeNotifierFrameLayout {

        private int inputFieldHeight;

        public ContentView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);
            heightSize -= getPaddingTop();

            measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

            int keyboardSize = getKeyboardHeight();
            int childCount = getChildCount();

            if (commentView != null) {
                measureChildWithMargins(commentView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                Object tag = commentView.getTag();
                if (tag != null && tag.equals(2)) {
                    if (keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow) {
                        heightSize -= commentView.getEmojiPadding();
                    }
                    inputFieldHeight = commentView.getMeasuredHeight();
                } else {
                    inputFieldHeight = 0;
                }
            }

            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE || child == commentView || child == actionBar) {
                    continue;
                }
                if (child == listView || child == progressView || child == searchEmptyView) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int contentHeightSpec = View.MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize - inputFieldHeight + AndroidUtilities.dp(2)), View.MeasureSpec.EXACTLY);
                    child.measure(contentWidthSpec, contentHeightSpec);
                } else if (commentView != null && commentView.isPopupView(child)) {
                    if (AndroidUtilities.isInMultiwindow) {
                        if (AndroidUtilities.isTablet()) {
                            child.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(320), heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight + getPaddingTop()), View.MeasureSpec.EXACTLY));
                        } else {
                            child.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(heightSize - inputFieldHeight - AndroidUtilities.statusBarHeight + getPaddingTop(), View.MeasureSpec.EXACTLY));
                        }
                    } else {
                        child.measure(View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, View.MeasureSpec.EXACTLY));
                    }
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom;
            Object tag = commentView != null ? commentView.getTag() : null;
            if (tag != null && tag.equals(2)) {
                paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow ? commentView.getEmojiPadding() : 0;
            } else {
                paddingBottom = 0;
            }
            setBottomClip(paddingBottom);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = Gravity.TOP | Gravity.LEFT;
                }

                final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = r - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = lp.topMargin + getPaddingTop();
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }

                if (commentView != null && commentView.isPopupView(child)) {
                    if (AndroidUtilities.isInMultiwindow) {
                        childTop = commentView.getTop() - child.getMeasuredHeight() + AndroidUtilities.dp(1);
                    } else {
                        childTop = commentView.getBottom();
                    }
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_DOWN) {
                    int currentPosition = layoutManager.findFirstVisibleItemPosition();
                    startedScrollAtTop = currentPosition <= 1;
                } else {
                    if (actionBar.isActionModeShowed()) {
                        allowMoving = true;
                    }
                }
                totalConsumedAmount = 0;
                allowScrollToHiddenView = false;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }

    class SwipeController extends ItemTouchHelper.Callback {

        private RectF buttonInstance;
        private RecyclerView.ViewHolder currentItemViewHolder;
        private boolean swipingFolder;
        private boolean swipeFolderBack;

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (waitingForDialogsAnimationEnd() || parentLayout != null && parentLayout.isInPreviewMode()) {
                return 0;
            }
            if (swipingFolder && swipeFolderBack) {
                swipingFolder = false;
                return 0;
            }
            if (!onlySelect && dialogsType == 0 && slidingView == null && recyclerView.getAdapter() == dialogsAdapter && viewHolder.itemView instanceof DialogCell) {
                DialogCell dialogCell = (DialogCell) viewHolder.itemView;
                long dialogId = dialogCell.getDialogId();
                if (actionBar.isActionModeShowed()) {
                    TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialogId);
                    if (!allowMoving || dialog == null || !dialog.pinned || DialogObject.isFolderDialogId(dialogId)) {
                        return 0;
                    }
                    movingView = (DialogCell) viewHolder.itemView;
                    movingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                } else {
                    if (!allowSwipeDuringCurrentTouch || dialogId == getUserConfig().clientUserId || dialogId == 777000 || getMessagesController().isProxyDialog(dialogId, false)) {
                        return 0;
                    }
                    swipeFolderBack = false;
                    swipingFolder = SharedConfig.archiveHidden && DialogObject.isFolderDialogId(dialogCell.getDialogId());
                    dialogCell.setSliding(true);
                    return makeMovementFlags(0, ItemTouchHelper.LEFT);
                }
            }
            return 0;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (!(target.itemView instanceof DialogCell)) {
                return false;
            }
            DialogCell dialogCell = (DialogCell) target.itemView;
            long dialogId = dialogCell.getDialogId();
            TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialogId);
            if (dialog == null || !dialog.pinned || DialogObject.isFolderDialogId(dialogId)) {
                return false;
            }
            int fromIndex = source.getAdapterPosition();
            int toIndex = target.getAdapterPosition();
            dialogsAdapter.notifyItemMoved(fromIndex, toIndex);
            updateDialogIndices();
            movingWas = true;
            return true;
        }

        @Override
        public int convertToAbsoluteDirection(int flags, int layoutDirection) {
            if (swipeFolderBack) {
                return 0;
            }
            return super.convertToAbsoluteDirection(flags, layoutDirection);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            if (viewHolder != null) {
                DialogCell dialogCell = (DialogCell) viewHolder.itemView;
                long dialogId = dialogCell.getDialogId();
                if (DialogObject.isFolderDialogId(dialogId)) {
                    SharedConfig.toggleArchiveHidden();
                    if (SharedConfig.archiveHidden) {
                        waitingForScrollFinished = true;
                        listView.smoothScrollBy(0, (dialogCell.getMeasuredHeight() + dialogCell.getTop()), CubicBezierInterpolator.EASE_OUT);
                        getUndoView().showWithAction(0, UndoView.ACTION_ARCHIVE_HIDDEN, null, null);
                    }
                    return;
                }

                slidingView = dialogCell;
                int position = viewHolder.getAdapterPosition();
                int dialogIndex = dialogsAdapter.fixPosition(position);
                int count = dialogsAdapter.getItemCount();
                Runnable finishRunnable = () -> {
                    TLRPC.Dialog dialog = frozenDialogsList.remove(dialogIndex);
                    int pinnedNum = dialog.pinnedNum;
                    slidingView = null;
                    listView.invalidate();
                    int added = getMessagesController().addDialogToFolder(dialog.id, folderId == 0 ? 1 : 0, -1, 0);
                    if (added == 2) {
                        dialogsAdapter.notifyItemChanged(count - 1);
                    }
                    if (added != 2 || position != 0) {
                        dialogsItemAnimator.prepareForRemove();
                        lastItemsCount--;
                        dialogsAdapter.notifyItemRemoved(position);
                        dialogRemoveFinished = 2;
                    }
                    if (folderId == 0) {
                        if (added == 2) {
                            dialogsItemAnimator.prepareForRemove();
                            if (position == 0) {
                                dialogChangeFinished = 2;
                                setDialogsListFrozen(true);
                                dialogsAdapter.notifyItemChanged(0);
                            } else {
                                lastItemsCount++;
                                dialogsAdapter.notifyItemInserted(0);
                                if (!SharedConfig.archiveHidden && layoutManager.findFirstVisibleItemPosition() == 0) {
                                    listView.smoothScrollBy(0, -AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72));
                                }
                            }
                            ArrayList<TLRPC.Dialog> dialogs = getDialogsArray(currentAccount, dialogsType, folderId, false);
                            frozenDialogsList.add(0, dialogs.get(0));
                        } else if (added == 1) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
                            if (holder != null && holder.itemView instanceof DialogCell) {
                                DialogCell cell = (DialogCell) holder.itemView;
                                cell.checkCurrentDialogIndex(true);
                                cell.animateArchiveAvatar();
                            }
                        }
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        boolean hintShowed = preferences.getBoolean("archivehint_l", false) || SharedConfig.archiveHidden;
                        if (!hintShowed) {
                            preferences.edit().putBoolean("archivehint_l", true).commit();
                        }
                        getUndoView().showWithAction(dialog.id, hintShowed ? UndoView.ACTION_ARCHIVE : UndoView.ACTION_ARCHIVE_HINT, null, () -> {
                            dialogsListFrozen = true;
                            getMessagesController().addDialogToFolder(dialog.id, 0, pinnedNum, 0);
                            dialogsListFrozen = false;
                            ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(0);
                            int index = dialogs.indexOf(dialog);
                            if (index >= 0) {
                                ArrayList<TLRPC.Dialog> archivedDialogs = getMessagesController().getDialogs(1);
                                if (!archivedDialogs.isEmpty() || index != 1) {
                                    dialogInsertFinished = 2;
                                    setDialogsListFrozen(true);
                                    dialogsItemAnimator.prepareForRemove();
                                    lastItemsCount++;
                                    dialogsAdapter.notifyItemInserted(index);
                                }
                                if (archivedDialogs.isEmpty()) {
                                    dialogs.remove(0);
                                    if (index == 1) {
                                        dialogChangeFinished = 2;
                                        setDialogsListFrozen(true);
                                        dialogsAdapter.notifyItemChanged(0);
                                    } else {
                                        frozenDialogsList.remove(0);
                                        dialogsItemAnimator.prepareForRemove();
                                        lastItemsCount--;
                                        dialogsAdapter.notifyItemRemoved(0);
                                    }
                                }
                            } else {
                                dialogsAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                    if (folderId != 0 && frozenDialogsList.isEmpty()) {
                        listView.setEmptyView(null);
                        progressView.setVisibility(View.INVISIBLE);
                    }
                };
                setDialogsListFrozen(true);
                if (Utilities.random.nextInt(1000) == 1) {
                    if (pacmanAnimation == null) {
                        pacmanAnimation = new PacmanAnimation(listView);
                    }
                    pacmanAnimation.setFinishRunnable(finishRunnable);
                    pacmanAnimation.start();
                } else {
                    finishRunnable.run();
                }
            } else {
                slidingView = null;
            }
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder != null) {
                listView.hideSelector();
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public long getAnimationDuration(RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
                return 200;
            } else if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) {
                if (movingView != null) {
                    View view = movingView;
                    AndroidUtilities.runOnUIThread(() -> view.setBackgroundDrawable(null), dialogsItemAnimator.getMoveDuration());
                    movingView = null;
                }
            }
            return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy);
        }

        @Override
        public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
            return 0.3f;
        }

        @Override
        public float getSwipeEscapeVelocity(float defaultValue) {
            return 3500;
        }

        @Override
        public float getSwipeVelocityThreshold(float defaultValue) {
            return Float.MAX_VALUE;
        }
    }

    public interface DialogsActivityDelegate {
        void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param);
    }

    public DialogsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        if (getArguments() != null) {
            onlySelect = arguments.getBoolean("onlySelect", false);
            cantSendToChannels = arguments.getBoolean("cantSendToChannels", false);
            dialogsType = arguments.getInt("dialogsType", 0);
            selectAlertString = arguments.getString("selectAlertString");
            selectAlertStringGroup = arguments.getString("selectAlertStringGroup");
            addToGroupAlertString = arguments.getString("addToGroupAlertString");
            allowSwitchAccount = arguments.getBoolean("allowSwitchAccount");
            checkCanWrite = arguments.getBoolean("checkCanWrite", true);
            folderId = arguments.getInt("folderId", 0);
            resetDelegate = arguments.getBoolean("resetDelegate", true);
            messagesCount = arguments.getInt("messagesCount", 0);
        }

        if (dialogsType == 0) {
            askAboutContacts = MessagesController.getGlobalNotificationsSettings().getBoolean("askAboutContacts", true);
            SharedConfig.loadProxyList();
        }

        if (searchString == null) {
            currentConnectionState = getConnectionsManager().getConnectionState();

            getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
            if (!onlySelect) {
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeSearchByActiveAction);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
            }
            getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
            getNotificationCenter().addObserver(this, NotificationCenter.encryptedChatUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.appDidLogout);
            getNotificationCenter().addObserver(this, NotificationCenter.openedChatChanged);
            getNotificationCenter().addObserver(this, NotificationCenter.notificationsSettingsUpdated);
            getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByAck);
            getNotificationCenter().addObserver(this, NotificationCenter.messageReceivedByServer);
            getNotificationCenter().addObserver(this, NotificationCenter.messageSendError);
            getNotificationCenter().addObserver(this, NotificationCenter.needReloadRecentDialogsSearch);
            getNotificationCenter().addObserver(this, NotificationCenter.replyMessagesDidLoad);
            getNotificationCenter().addObserver(this, NotificationCenter.reloadHints);
            getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
            getNotificationCenter().addObserver(this, NotificationCenter.dialogsUnreadCounterChanged);
            getNotificationCenter().addObserver(this, NotificationCenter.needDeleteDialog);
            getNotificationCenter().addObserver(this, NotificationCenter.folderBecomeEmpty);

            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetPasscode);
        }

        if (!dialogsLoaded[currentAccount]) {
            getMessagesController().loadGlobalNotificationsSettings();
            getMessagesController().loadDialogs(folderId, 0, 100, true);
            getMessagesController().loadHintDialogs();
            getContactsController().checkInviteText();
            getMediaDataController().loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
            getMediaDataController().checkFeaturedStickers();
            dialogsLoaded[currentAccount] = true;
        }
        getMessagesController().loadPinnedDialogs(folderId, 0, null);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (searchString == null) {
            getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
            if (!onlySelect) {
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeSearchByActiveAction);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
            }
            getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
            getNotificationCenter().removeObserver(this, NotificationCenter.encryptedChatUpdated);
            getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.appDidLogout);
            getNotificationCenter().removeObserver(this, NotificationCenter.openedChatChanged);
            getNotificationCenter().removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
            getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByAck);
            getNotificationCenter().removeObserver(this, NotificationCenter.messageReceivedByServer);
            getNotificationCenter().removeObserver(this, NotificationCenter.messageSendError);
            getNotificationCenter().removeObserver(this, NotificationCenter.needReloadRecentDialogsSearch);
            getNotificationCenter().removeObserver(this, NotificationCenter.replyMessagesDidLoad);
            getNotificationCenter().removeObserver(this, NotificationCenter.reloadHints);
            getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
            getNotificationCenter().removeObserver(this, NotificationCenter.dialogsUnreadCounterChanged);
            getNotificationCenter().removeObserver(this, NotificationCenter.needDeleteDialog);
            getNotificationCenter().removeObserver(this, NotificationCenter.folderBecomeEmpty);

            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetPasscode);
        }
        if (commentView != null) {
            commentView.onDestroy();
        }
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        delegate = null;
    }

    @Override
    public View createView(final Context context) {
        searching = false;
        searchWas = false;
        pacmanAnimation = null;

        AndroidUtilities.runOnUIThread(() -> Theme.createChatResources(context, false));

        ActionBarMenu menu = actionBar.createMenu();
        if (!onlySelect && searchString == null && folderId == 0) {
            proxyDrawable = new ProxyDrawable(context);
            proxyItem = menu.addItem(2, proxyDrawable);
            proxyItem.setContentDescription(LocaleController.getString("ProxySettings", R.string.ProxySettings));
            passcodeItem = menu.addItem(1, R.drawable.lock_close);
            updatePasscodeButton();
            updateProxyButton(false);
        }
        final ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (switchItem != null) {
                    switchItem.setVisibility(View.GONE);
                }
                if (proxyItem != null && proxyItemVisisble) {
                    proxyItem.setVisibility(View.GONE);
                }
                if (listView != null) {
                    if (searchString != null) {
                        listView.setEmptyView(searchEmptyView);
                        progressView.setVisibility(View.GONE);
                    }
                    if (!onlySelect) {
                        floatingButtonContainer.setVisibility(View.GONE);
                        //unreadFloatingButtonContainer.setVisibility(View.GONE);
                    }
                }
                updatePasscodeButton();
                actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrGoBack", R.string.AccDescrGoBack));
            }

            @Override
            public boolean canCollapseSearch() {
                if (switchItem != null) {
                    switchItem.setVisibility(View.VISIBLE);
                }
                if (proxyItem != null && proxyItemVisisble) {
                    proxyItem.setVisibility(View.VISIBLE);
                }
                if (searchString != null) {
                    finishFragment();
                    return false;
                }
                return true;
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                if (listView != null) {
                    listView.setEmptyView(folderId == 0 ? progressView : null);
                    searchEmptyView.setVisibility(View.GONE);
                    if (!onlySelect) {
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                        /*if (currentUnreadCount != 0) {
                            unreadFloatingButtonContainer.setVisibility(View.VISIBLE);
                            unreadFloatingButtonContainer.setTranslationY(AndroidUtilities.dp(74));
                        }*/
                        floatingHidden = true;
                        floatingButtonContainer.setTranslationY(AndroidUtilities.dp(100));
                        hideFloatingButton(false);
                    }
                    if (listView.getAdapter() != dialogsAdapter) {
                        listView.setAdapter(dialogsAdapter);
                        dialogsAdapter.notifyDataSetChanged();
                    }
                }
                if (dialogsSearchAdapter != null) {
                    dialogsSearchAdapter.searchDialogs(null);
                }
                updatePasscodeButton();
                if (menuDrawable != null) {
                    actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrOpenMenu", R.string.AccDescrOpenMenu));
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0 || dialogsSearchAdapter != null && dialogsSearchAdapter.hasRecentRearch()) {
                    searchWas = true;
                    if (dialogsSearchAdapter != null && listView.getAdapter() != dialogsSearchAdapter) {
                        listView.setAdapter(dialogsSearchAdapter);
                        dialogsSearchAdapter.notifyDataSetChanged();
                    }
                    if (searchEmptyView != null && listView.getEmptyView() != searchEmptyView) {
                        progressView.setVisibility(View.GONE);
                        listView.setEmptyView(searchEmptyView);
                    }
                    //searchEmptyView.showProgress();
                }
                if (dialogsSearchAdapter != null) {
                    dialogsSearchAdapter.searchDialogs(text);
                }
            }
        });
        item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        item.setContentDescription(LocaleController.getString("Search", R.string.Search));
        if (onlySelect) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            if (dialogsType == 3 && selectAlertString == null) {
                actionBar.setTitle(LocaleController.getString("ForwardTo", R.string.ForwardTo));
            } else {
                actionBar.setTitle(LocaleController.getString("SelectChat", R.string.SelectChat));
            }
        } else {
            if (searchString != null || folderId != 0) {
                actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
            } else {
                actionBar.setBackButtonDrawable(menuDrawable = new MenuDrawable());
                actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrOpenMenu", R.string.AccDescrOpenMenu));
            }
            if (folderId != 0) {
                actionBar.setTitle(LocaleController.getString("ArchivedChats", R.string.ArchivedChats));
            } else {
                if (BuildVars.DEBUG_VERSION) {
                    actionBar.setTitle("Telegram Beta"/*LocaleController.getString("AppNameBeta", R.string.AppNameBeta)*/);
                } else {
                    actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
                }
            }
            actionBar.setSupportsHolidayImage(true);
        }
        actionBar.setTitleActionRunnable(() -> {
            hideFloatingButton(false);
            listView.smoothScrollToPosition(hasHiddenArchive() ? 1 : 0);
        });

        if (allowSwitchAccount && UserConfig.getActivatedAccountsCount() > 1) {
            switchItem = menu.addItemWithWidth(1, 0, AndroidUtilities.dp(56));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(12));

            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(18));
            switchItem.addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

            TLRPC.User user = getUserConfig().getCurrentUser();
            avatarDrawable.setInfo(user);
            imageView.getImageReceiver().setCurrentAccount(currentAccount);
            imageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);

            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                TLRPC.User u = AccountInstance.getInstance(a).getUserConfig().getCurrentUser();
                if (u != null) {
                    AccountSelectCell cell = new AccountSelectCell(context);
                    cell.setAccount(a, true);
                    switchItem.addSubItem(10 + a, cell, AndroidUtilities.dp(230), AndroidUtilities.dp(48));
                }
            }
        }
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode(true);
                    } else if (onlySelect || folderId != 0) {
                        finishFragment();
                    } else if (parentLayout != null) {
                        parentLayout.getDrawerLayoutContainer().openDrawer(false);
                    }
                } else if (id == 1) {
                    SharedConfig.appLocked = !SharedConfig.appLocked;
                    SharedConfig.saveConfig();
                    updatePasscodeButton();
                } else if (id == 2) {
                    presentFragment(new ProxyListActivity());
                } else if (id >= 10 && id < 10 + UserConfig.MAX_ACCOUNT_COUNT) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    DialogsActivityDelegate oldDelegate = delegate;
                    LaunchActivity launchActivity = (LaunchActivity) getParentActivity();
                    launchActivity.switchToAccount(id - 10, true);

                    DialogsActivity dialogsActivity = new DialogsActivity(arguments);
                    dialogsActivity.setDelegate(oldDelegate);
                    launchActivity.presentFragment(dialogsActivity, false, true);
                } else if (id == pin || id == read || id == delete || id == clear || id == mute || id == archive) {
                    perfromSelectedDialogsAction(id, true);
                }
            }
        });

        if (sideMenu != null) {
            sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
            sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
            sideMenu.getAdapter().notifyDataSetChanged();
        }

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);

        pinItem = actionMode.addItemWithWidth(pin, R.drawable.msg_pin, AndroidUtilities.dp(54));
        muteItem = actionMode.addItemWithWidth(mute, R.drawable.msg_archive, AndroidUtilities.dp(54));
        deleteItem = actionMode.addItemWithWidth(delete, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));
        ActionBarMenuItem otherItem = actionMode.addItemWithWidth(0, R.drawable.ic_ab_other, AndroidUtilities.dp(54), LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        archiveItem = otherItem.addSubItem(archive, R.drawable.msg_archive, LocaleController.getString("Archive", R.string.Archive));
        readItem = otherItem.addSubItem(read, R.drawable.msg_markread, LocaleController.getString("MarkAsRead", R.string.MarkAsRead));
        clearItem = otherItem.addSubItem(clear, R.drawable.msg_clear, LocaleController.getString("ClearHistory", R.string.ClearHistory));

        actionModeViews.add(pinItem);
        actionModeViews.add(muteItem);
        actionModeViews.add(deleteItem);
        actionModeViews.add(otherItem);

        ContentView contentView = new ContentView(context);
        fragmentView = contentView;

        listView = new RecyclerListView(context) {

            private boolean firstLayout = true;
            private boolean ignoreLayout;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (slidingView != null && pacmanAnimation != null) {
                    pacmanAnimation.draw(canvas, slidingView.getTop() + slidingView.getMeasuredHeight() / 2);
                }
            }

            @Override
            public void setAdapter(Adapter adapter) {
                super.setAdapter(adapter);
                firstLayout = true;
            }

            private void checkIfAdapterValid() {
                if (listView != null && dialogsAdapter != null && listView.getAdapter() == dialogsAdapter && lastItemsCount != dialogsAdapter.getItemCount()) {
                    ignoreLayout = true;
                    dialogsAdapter.notifyDataSetChanged();
                    ignoreLayout = false;
                }
            }

            @Override
            public void setPadding(int left, int top, int right, int bottom) {
                super.setPadding(left, top, right, bottom);
                if (searchEmptyView != null) {
                    searchEmptyView.setPadding(left, top, right, bottom);
                }
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                if (firstLayout && getMessagesController().dialogsLoaded) {
                    if (hasHiddenArchive()) {
                        ignoreLayout = true;
                        layoutManager.scrollToPositionWithOffset(1, 0);
                        ignoreLayout = false;
                    }
                    firstLayout = false;
                }
                checkIfAdapterValid();
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if ((dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) && !dialogsItemAnimator.isRunning()) {
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

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
                    return false;
                }
                int action = e.getAction();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (!itemTouchhelper.isIdle() && swipeController.swipingFolder) {
                        swipeController.swipeFolderBack = true;
                        if (itemTouchhelper.checkHorizontalSwipe(null, ItemTouchHelper.LEFT) != 0) {
                            SharedConfig.toggleArchiveHidden();
                            getUndoView().showWithAction(0, UndoView.ACTION_ARCHIVE_PINNED, null, null);
                        }
                    }
                }
                boolean result = super.onTouchEvent(e);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (allowScrollToHiddenView) {
                        int currentPosition = layoutManager.findFirstVisibleItemPosition();
                        if (currentPosition == 0) {
                            View view = layoutManager.findViewByPosition(currentPosition);
                            int height = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72) / 4 * 3;
                            int diff = view.getTop() + view.getMeasuredHeight();
                            if (view != null) {
                                if (diff < height) {
                                    listView.smoothScrollBy(0, diff, CubicBezierInterpolator.EASE_OUT_QUINT);
                                } else {
                                    listView.smoothScrollBy(0, view.getTop(), CubicBezierInterpolator.EASE_OUT_QUINT);
                                }
                            }
                        }
                        allowScrollToHiddenView = false;
                    }
                }
                return result;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (waitingForScrollFinished || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0) {
                    return false;
                }
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    allowSwipeDuringCurrentTouch = !actionBar.isActionModeShowed();
                    checkIfAdapterValid();
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        dialogsItemAnimator = new DialogsItemAnimator() {
            @Override
            public void onRemoveFinished(RecyclerView.ViewHolder item) {
                if (dialogRemoveFinished == 2) {
                    dialogRemoveFinished = 1;
                }
            }

            @Override
            public void onAddFinished(RecyclerView.ViewHolder item) {
                if (dialogInsertFinished == 2) {
                    dialogInsertFinished = 1;
                }
            }

            @Override
            public void onChangeFinished(RecyclerView.ViewHolder item, boolean oldItem) {
                if (dialogChangeFinished == 2) {
                    dialogChangeFinished = 1;
                }
            }

            @Override
            protected void onAllAnimationsDone() {
                if (dialogRemoveFinished == 1 || dialogInsertFinished == 1 || dialogChangeFinished == 1) {
                    onDialogAnimationFinished();
                }
            }
        };
        listView.setItemAnimator(dialogsItemAnimator);
        listView.setVerticalScrollBarEnabled(true);
        listView.setInstantClick(true);
        listView.setTag(4);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                if (hasHiddenArchive() && position == 1) {
                    super.smoothScrollToPosition(recyclerView, state, position);
                } else {
                    LinearSmoothScrollerMiddle linearSmoothScroller = new LinearSmoothScrollerMiddle(recyclerView.getContext());
                    linearSmoothScroller.setTargetPosition(position);
                    startSmoothScroll(linearSmoothScroller);
                }
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (listView.getAdapter() == dialogsAdapter && dialogsType == 0 && !onlySelect && !allowScrollToHiddenView && folderId == 0 && dy < 0 && getMessagesController().hasHiddenArchive()) {
                    int currentPosition = layoutManager.findFirstVisibleItemPosition();
                    if (currentPosition == 0) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        if (view != null && view.getBottom() <= AndroidUtilities.dp(1)) {
                            currentPosition = 1;
                        }
                    }
                    if (currentPosition != 0 && currentPosition != RecyclerView.NO_POSITION) {
                        View view = layoutManager.findViewByPosition(currentPosition);
                        if (view != null) {
                            int dialogHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72) + 1;
                            int canScrollDy = -view.getTop() + (currentPosition - 1) * dialogHeight;
                            int positiveDy = Math.abs(dy);
                            if (canScrollDy < positiveDy) {
                                totalConsumedAmount += Math.abs(dy);
                                dy = -canScrollDy;
                                if (startedScrollAtTop && totalConsumedAmount >= AndroidUtilities.dp(150)) {
                                    allowScrollToHiddenView = true;
                                    try {
                                        listView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                    } catch (Exception ignore) {

                                    }
                                }
                            }
                        }
                    }
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (listView == null || listView.getAdapter() == null || getParentActivity() == null) {
                return;
            }

            long dialog_id = 0;
            int message_id = 0;
            boolean isGlobalSearch = false;
            RecyclerView.Adapter adapter = listView.getAdapter();
            if (adapter == dialogsAdapter) {
                TLObject object = dialogsAdapter.getItem(position);
                if (object instanceof TLRPC.User) {
                    dialog_id = ((TLRPC.User) object).id;
                } else if (object instanceof TLRPC.Dialog) {
                    TLRPC.Dialog dialog = (TLRPC.Dialog) object;
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        if (actionBar.isActionModeShowed()) {
                            return;
                        }
                        TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
                        Bundle args = new Bundle();
                        args.putInt("folderId", dialogFolder.folder.id);
                        presentFragment(new DialogsActivity(args));
                        return;
                    }
                    dialog_id = dialog.id;
                    if (actionBar.isActionModeShowed()) {
                        showOrUpdateActionMode(dialog, view);
                        return;
                    }
                } else if (object instanceof TLRPC.TL_recentMeUrlChat) {
                    dialog_id = -((TLRPC.TL_recentMeUrlChat) object).chat_id;
                } else if (object instanceof TLRPC.TL_recentMeUrlUser) {
                    dialog_id = ((TLRPC.TL_recentMeUrlUser) object).user_id;
                } else if (object instanceof TLRPC.TL_recentMeUrlChatInvite) {
                    TLRPC.TL_recentMeUrlChatInvite chatInvite = (TLRPC.TL_recentMeUrlChatInvite) object;
                    TLRPC.ChatInvite invite = chatInvite.chat_invite;
                    if (invite.chat == null && (!invite.channel || invite.megagroup) || invite.chat != null && (!ChatObject.isChannel(invite.chat) || invite.chat.megagroup)) {
                        String hash = chatInvite.url;
                        int index = hash.indexOf('/');
                        if (index > 0) {
                            hash = hash.substring(index + 1);
                        }
                        showDialog(new JoinGroupAlert(getParentActivity(), invite, hash, DialogsActivity.this));
                        return;
                    } else {
                        if (invite.chat != null) {
                            dialog_id = -invite.chat.id;
                        } else {
                            return;
                        }
                    }
                } else if (object instanceof TLRPC.TL_recentMeUrlStickerSet) {
                    TLRPC.StickerSet stickerSet = ((TLRPC.TL_recentMeUrlStickerSet) object).set.set;
                    TLRPC.TL_inputStickerSetID set = new TLRPC.TL_inputStickerSetID();
                    set.id = stickerSet.id;
                    set.access_hash = stickerSet.access_hash;
                    showDialog(new StickersAlert(getParentActivity(), DialogsActivity.this, set, null, null));
                    return;
                } else if (object instanceof TLRPC.TL_recentMeUrlUnknown) {
                    return;
                } else {
                    return;
                }
            } else if (adapter == dialogsSearchAdapter) {
                Object obj = dialogsSearchAdapter.getItem(position);
                isGlobalSearch = dialogsSearchAdapter.isGlobalSearch(position);
                if (obj instanceof TLRPC.User) {
                    dialog_id = ((TLRPC.User) obj).id;
                    if (!onlySelect) {
                        searchDialogId = dialog_id;
                        searchObject = (TLRPC.User) obj;
                    }
                } else if (obj instanceof TLRPC.Chat) {
                    dialog_id = -((TLRPC.Chat) obj).id;
                    if (!onlySelect) {
                        searchDialogId = dialog_id;
                        searchObject = (TLRPC.Chat) obj;
                    }
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    dialog_id = ((long) ((TLRPC.EncryptedChat) obj).id) << 32;
                    if (!onlySelect) {
                        searchDialogId = dialog_id;
                        searchObject = (TLRPC.EncryptedChat) obj;
                    }
                } else if (obj instanceof MessageObject) {
                    MessageObject messageObject = (MessageObject) obj;
                    dialog_id = messageObject.getDialogId();
                    message_id = messageObject.getId();
                    dialogsSearchAdapter.addHashtagsFromMessage(dialogsSearchAdapter.getLastSearchString());
                } else if (obj instanceof String) {
                    String str = (String) obj;
                    if (dialogsSearchAdapter.isHashtagSearch()) {
                        actionBar.openSearchField(str, false);
                    } else if (!str.equals("section")) {
                        NewContactActivity activity = new NewContactActivity();
                        activity.setInitialPhoneNumber(str);
                        presentFragment(activity);
                    }
                }
            }

            if (dialog_id == 0) {
                return;
            }

            if (onlySelect) {
                if (!validateSlowModeDialog(dialog_id)) {
                    return;
                }
                if (dialogsAdapter.hasSelectedDialogs()) {
                    dialogsAdapter.addOrRemoveSelectedDialog(dialog_id, view);
                    updateSelectedCount();
                } else {
                    didSelectResult(dialog_id, true, false);
                }
            } else {
                Bundle args = new Bundle();
                int lower_part = (int) dialog_id;
                int high_id = (int) (dialog_id >> 32);
                if (lower_part != 0) {
                    if (lower_part > 0) {
                        args.putInt("user_id", lower_part);
                    } else if (lower_part < 0) {
                        if (message_id != 0) {
                            TLRPC.Chat chat = getMessagesController().getChat(-lower_part);
                            if (chat != null && chat.migrated_to != null) {
                                args.putInt("migrated_to", lower_part);
                                lower_part = -chat.migrated_to.channel_id;
                            }
                        }
                        args.putInt("chat_id", -lower_part);
                    }
                } else {
                    args.putInt("enc_id", high_id);
                }
                if (message_id != 0) {
                    args.putInt("message_id", message_id);
                } else if (!isGlobalSearch) {
                    closeSearch();
                } else {
                    if (searchObject != null) {
                        dialogsSearchAdapter.putRecentSearch(searchDialogId, searchObject);
                        searchObject = null;
                    }
                }
                if (AndroidUtilities.isTablet()) {
                    if (openedDialogId == dialog_id && adapter != dialogsSearchAdapter) {
                        return;
                    }
                    if (dialogsAdapter != null) {
                        dialogsAdapter.setOpenedDialogId(openedDialogId = dialog_id);
                        updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
                    }
                }
                if (searchString != null) {
                    if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                        getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                        presentFragment(new ChatActivity(args));
                    }
                } else {
                    if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                        presentFragment(new ChatActivity(args));
                    }
                }
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                if (getParentActivity() == null) {
                    return false;
                }
                if (!actionBar.isActionModeShowed() && !AndroidUtilities.isTablet() && !onlySelect && view instanceof DialogCell) {
                    DialogCell cell = (DialogCell) view;
                    if (cell.isPointInsideAvatar(x, y)) {
                        long dialog_id = cell.getDialogId();
                        Bundle args = new Bundle();
                        int lower_part = (int) dialog_id;
                        int high_id = (int) (dialog_id >> 32);
                        int message_id = cell.getMessageId();
                        if (lower_part != 0) {
                            if (lower_part > 0) {
                                args.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                if (message_id != 0) {
                                    TLRPC.Chat chat = getMessagesController().getChat(-lower_part);
                                    if (chat != null && chat.migrated_to != null) {
                                        args.putInt("migrated_to", lower_part);
                                        lower_part = -chat.migrated_to.channel_id;
                                    }
                                }
                                args.putInt("chat_id", -lower_part);
                            }
                        } else {
                            return false;
                        }

                        if (message_id != 0) {
                            args.putInt("message_id", message_id);
                        }
                        if (searchString != null) {
                            if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                                presentFragmentAsPreview(new ChatActivity(args));
                            }
                        } else {
                            if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                                presentFragmentAsPreview(new ChatActivity(args));
                            }
                        }
                        return true;
                    }
                }
                RecyclerView.Adapter adapter = listView.getAdapter();
                if (adapter == dialogsSearchAdapter) {
                    Object item = dialogsSearchAdapter.getItem(position);
                    /*if (item instanceof String || dialogsSearchAdapter.isRecentSearchDisplayed()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                        builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> {
                            if (dialogsSearchAdapter.isRecentSearchDisplayed()) {
                                dialogsSearchAdapter.clearRecentSearch();
                            } else {
                                dialogsSearchAdapter.clearRecentHashtags();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                        return true;
                    }*/
                    return false;
                }
                final TLRPC.Dialog dialog;
                ArrayList<TLRPC.Dialog> dialogs = getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen);
                position = dialogsAdapter.fixPosition(position);
                if (position < 0 || position >= dialogs.size()) {
                    return false;
                }
                dialog = dialogs.get(position);
                if (onlySelect) {
                    if (dialogsType != 3 || selectAlertString != null) {
                        return false;
                    }
                    if (!validateSlowModeDialog(dialog.id)) {
                        return false;
                    }
                    dialogsAdapter.addOrRemoveSelectedDialog(dialog.id, view);
                    updateSelectedCount();
                } else {
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        return false;
                    }
                    if (actionBar.isActionModeShowed() && dialog.pinned) {
                        return false;
                    }
                    showOrUpdateActionMode(dialog, view);
                }
                return true;
            }

            @Override
            public void onLongClickRelease() {
                finishPreviewFragment();
            }

            @Override
            public void onMove(float dx, float dy) {
                movePreviewFragment(dy);
            }
        });
        swipeController = new SwipeController();

        itemTouchhelper = new ItemTouchHelper(swipeController);
        itemTouchhelper.attachToRecyclerView(listView);

        searchEmptyView = new EmptyTextProgressView(context);
        searchEmptyView.setVisibility(View.GONE);
        searchEmptyView.setShowAtCenter(true);
        searchEmptyView.setTopImage(R.drawable.settings_noresults);
        searchEmptyView.setText(LocaleController.getString("SettingsNoResults", R.string.SettingsNoResults));
        contentView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setVisibility(View.GONE);
        contentView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        floatingButtonContainer = new FrameLayout(context);
        floatingButtonContainer.setVisibility(onlySelect || folderId != 0 ? View.GONE : View.VISIBLE);
        contentView.addView(floatingButtonContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 20, (Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 14, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 4 : 0, 0, LocaleController.isRTL ? 0 : 4, 0));
        floatingButtonContainer.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("destroyAfterSelect", true);
            presentFragment(new ContactsActivity(args));
        });

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setImageResource(R.drawable.floating_pencil);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        floatingButtonContainer.setContentDescription(LocaleController.getString("NewMessageTitle", R.string.NewMessageTitle));
        floatingButtonContainer.addView(floatingButton, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60), (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));

        /*unreadFloatingButtonContainer = new FrameLayout(context);
        if (onlySelect) {
            unreadFloatingButtonContainer.setVisibility(View.GONE);
        } else {
            unreadFloatingButtonContainer.setVisibility(currentUnreadCount != 0 ? View.VISIBLE : View.INVISIBLE);
            unreadFloatingButtonContainer.setTag(currentUnreadCount != 0 ? 1 : null);
        }
        contentView.addView(unreadFloatingButtonContainer, LayoutHelper.createFrame((Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 20, (Build.VERSION.SDK_INT >= 21 ? 56 : 60) + 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 4 : 0, 0, LocaleController.isRTL ? 0 : 4, 14 + 60 + 7));
        unreadFloatingButtonContainer.setOnClickListener(view -> {
            if (listView.getAdapter() == dialogsAdapter) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItem == 0) {
                    ArrayList<TLRPC.Dialog> array = getDialogsArray();
                    for (int a = array.size() - 1; a >= 0; a--) {
                        TLRPC.Dialog dialog = array.get(a);
                        if ((dialog.unread_count != 0 || dialog.unread_mark) && !getMessagesController().isDialogMuted(dialog.id)) {
                            listView.smoothScrollToPosition(a);
                            break;
                        }
                    }
                } else {
                    int middle = listView.getMeasuredHeight() / 2;
                    boolean found = false;
                    for (int b = 0, count = listView.getChildCount(); b < count; b++) {
                        View child = listView.getChildAt(b);
                        if (child instanceof DialogCell) {
                            if (child.getTop() <= middle && child.getBottom() >= middle) {
                                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
                                if (holder != null) {
                                    ArrayList<TLRPC.Dialog> array = getDialogsArray();
                                    for (int a = Math.min(holder.getAdapterPosition(), array.size()) - 1; a >= 0; a--) {
                                        TLRPC.Dialog dialog = array.get(a);
                                        if ((dialog.unread_count != 0 || dialog.unread_mark) && !getMessagesController().isDialogMuted(dialog.id)) {
                                            found = true;
                                            listView.smoothScrollToPosition(a);
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                    if (!found) {
                        hideFloatingButton(false);
                        listView.smoothScrollToPosition(0);
                    }
                }
            }
        });

        unreadFloatingButton = new ImageView(context);
        unreadFloatingButton.setScaleType(ImageView.ScaleType.CENTER);

        drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionUnreadBackground), Theme.getColor(Theme.key_chats_actionUnreadPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        unreadFloatingButton.setBackgroundDrawable(drawable);
        unreadFloatingButton.setImageDrawable(arrowDrawable = new AnimatedArrowDrawable(0xffffffff, false));
        unreadFloatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionUnreadIcon), PorterDuff.Mode.MULTIPLY));
        unreadFloatingButton.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        arrowDrawable.setAnimationProgress(1.0f);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(unreadFloatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(unreadFloatingButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            unreadFloatingButton.setStateListAnimator(animator);
            unreadFloatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        unreadFloatingButtonContainer.addView(unreadFloatingButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 10, 13, 10, 0));

        unreadFloatingButtonCounter = new TextView(context);
        unreadFloatingButtonCounter.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        unreadFloatingButtonCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        if (currentUnreadCount > 0) {
            unreadFloatingButtonCounter.setText(String.format("%d", currentUnreadCount));
        }
        if (Build.VERSION.SDK_INT >= 21) {
            unreadFloatingButtonCounter.setElevation(AndroidUtilities.dp(5));
            unreadFloatingButtonCounter.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setEmpty();
                }
            });
        }
        unreadFloatingButtonCounter.setColors(Theme.getColor(Theme.key_chat_goDownButtonCounter));
        unreadFloatingButtonCounter.setGravity(Gravity.CENTER);
        unreadFloatingButtonCounter.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(11.5f), Theme.getColor(Theme.key_chat_goDownButtonCounterBackground)));
        unreadFloatingButtonCounter.setMinWidth(AndroidUtilities.dp(23));
        unreadFloatingButtonCounter.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        unreadFloatingButtonContainer.addView(unreadFloatingButtonCounter, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.TOP | Gravity.CENTER_HORIZONTAL));*/

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    scrollingManually = true;
                } else {
                    scrollingManually = false;
                }
                if (waitingForScrollFinished && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    waitingForScrollFinished = false;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();
                dialogsItemAnimator.onListScroll(-dy);

                if (searching && searchWas) {
                    if (visibleItemCount > 0 && layoutManager.findLastVisibleItemPosition() == totalItemCount - 1 && !dialogsSearchAdapter.isMessagesSearchEndReached()) {
                        dialogsSearchAdapter.loadMoreSearchMessages();
                    }
                    return;
                }
                if (visibleItemCount > 0) {
                    if (layoutManager.findLastVisibleItemPosition() >= getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen).size() - 10) {
                        boolean fromCache = !getMessagesController().isDialogsEndReached(folderId);
                        if (fromCache || !getMessagesController().isServerDialogsEndReached(folderId)) {
                            AndroidUtilities.runOnUIThread(() -> getMessagesController().loadDialogs(folderId, -1, 100, fromCache));
                        }
                    }
                }

                //checkUnreadButton(true);

                if (floatingButtonContainer.getVisibility() != View.GONE) {
                    final View topChild = recyclerView.getChildAt(0);
                    int firstViewTop = 0;
                    if (topChild != null) {
                        firstViewTop = topChild.getTop();
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
                    if (changed && scrollUpdated && (goingDown || !goingDown && scrollingManually)) {
                        hideFloatingButton(goingDown);
                    }
                    prevPosition = firstVisibleItem;
                    prevTop = firstViewTop;
                    scrollUpdated = true;
                }
            }
        });

        if (searchString == null) {
            dialogsAdapter = new DialogsAdapter(context, dialogsType, folderId, onlySelect) {
                @Override
                public void notifyDataSetChanged() {
                    lastItemsCount = getItemCount();
                    super.notifyDataSetChanged();
                }
            };
            if (AndroidUtilities.isTablet() && openedDialogId != 0) {
                dialogsAdapter.setOpenedDialogId(openedDialogId);
            }
            listView.setAdapter(dialogsAdapter);
        }
        int type = 0;
        if (searchString != null) {
            type = 2;
        } else if (!onlySelect) {
            type = 1;
        }
        dialogsSearchAdapter = new DialogsSearchAdapter(context, type, dialogsType);
        dialogsSearchAdapter.setDelegate(new DialogsSearchAdapter.DialogsSearchAdapterDelegate() {
            @Override
            public void searchStateChanged(boolean search) {
                if (searching && searchWas && searchEmptyView != null) {
                    if (search) {
                        searchEmptyView.showProgress();
                    } else {
                        searchEmptyView.showTextView();
                    }
                }
            }

            @Override
            public void didPressedOnSubDialog(long did) {
                if (onlySelect) {
                    if (!validateSlowModeDialog(did)) {
                        return;
                    }
                    if (dialogsAdapter.hasSelectedDialogs()) {
                        dialogsAdapter.addOrRemoveSelectedDialog(did, null);
                        updateSelectedCount();
                        closeSearch();
                    } else {
                        didSelectResult(did, true, false);
                    }
                } else {
                    int lower_id = (int) did;
                    Bundle args = new Bundle();
                    if (lower_id > 0) {
                        args.putInt("user_id", lower_id);
                    } else {
                        args.putInt("chat_id", -lower_id);
                    }
                    closeSearch();
                    if (AndroidUtilities.isTablet()) {
                        if (dialogsAdapter != null) {
                            dialogsAdapter.setOpenedDialogId(openedDialogId = did);
                            updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
                        }
                    }
                    if (searchString != null) {
                        if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                            getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                            presentFragment(new ChatActivity(args));
                        }
                    } else {
                        if (getMessagesController().checkCanOpenChat(args, DialogsActivity.this)) {
                            presentFragment(new ChatActivity(args));
                        }
                    }
                }
            }

            @Override
            public void needRemoveHint(final int did) {
                if (getParentActivity() == null) {
                    return;
                }
                TLRPC.User user = getMessagesController().getUser(did);
                if (user == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("ChatHintsDeleteAlertTitle", R.string.ChatHintsDeleteAlertTitle));
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatHintsDeleteAlert", R.string.ChatHintsDeleteAlert, ContactsController.formatName(user.first_name, user.last_name))));
                builder.setPositiveButton(LocaleController.getString("StickersRemove", R.string.StickersRemove), (dialogInterface, i) -> getMediaDataController().removePeer(did));
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            }

            @Override
            public void needClearList() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("ClearSearchAlertTitle", R.string.ClearSearchAlertTitle));
                builder.setMessage(LocaleController.getString("ClearSearchAlert", R.string.ClearSearchAlert));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> {
                    if (dialogsSearchAdapter.isRecentSearchDisplayed()) {
                        dialogsSearchAdapter.clearRecentSearch();
                    } else {
                        dialogsSearchAdapter.clearRecentHashtags();
                    }
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

        listView.setEmptyView(folderId == 0 ? progressView : null);
        if (searchString != null) {
            actionBar.openSearchField(searchString, false);
        }

        if (!onlySelect && dialogsType == 0) {
            FragmentContextView fragmentLocationContextView = new FragmentContextView(context, this, true);
            contentView.addView(fragmentLocationContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));

            FragmentContextView fragmentContextView = new FragmentContextView(context, this, false);
            contentView.addView(fragmentContextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));

            fragmentContextView.setAdditionalContextView(fragmentLocationContextView);
            fragmentLocationContextView.setAdditionalContextView(fragmentContextView);
        } else if (dialogsType == 3 && selectAlertString == null) {
            if (commentView != null) {
                commentView.onDestroy();
            }
            commentView = new ChatActivityEnterView(getParentActivity(), contentView, null, false);
            commentView.setAllowStickersAndGifs(false, false);
            commentView.setForceShowSendButton(true, false);
            commentView.setVisibility(View.GONE);
            contentView.addView(commentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
            commentView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
                @Override
                public void onMessageSend(CharSequence message, boolean notify, int scheduleDate) {
                    if (delegate == null) {
                        return;
                    }
                    ArrayList<Long> selectedDialogs = dialogsAdapter.getSelectedDialogs();
                    if (selectedDialogs.isEmpty()) {
                        return;
                    }
                    delegate.didSelectDialogs(DialogsActivity.this, selectedDialogs, message, false);
                }

                @Override
                public void onSwitchRecordMode(boolean video) {

                }

                @Override
                public void onTextSelectionChanged(int start, int end) {

                }

                @Override
                public void onStickersExpandedChange() {

                }

                @Override
                public void onPreAudioVideoRecord() {

                }

                @Override
                public void onTextChanged(final CharSequence text, boolean bigChange) {

                }

                @Override
                public void onTextSpansChanged(CharSequence text) {

                }

                @Override
                public void needSendTyping() {

                }

                @Override
                public void onAttachButtonHidden() {

                }

                @Override
                public void onAttachButtonShow() {

                }

                @Override
                public void onMessageEditEnd(boolean loading) {

                }

                @Override
                public void onWindowSizeChanged(int size) {

                }

                @Override
                public void onStickersTab(boolean opened) {

                }

                @Override
                public void didPressedAttachButton() {

                }

                @Override
                public void needStartRecordVideo(int state, boolean notify, int scheduleDate) {

                }

                @Override
                public void needChangeVideoPreviewState(int state, float seekProgress) {

                }

                @Override
                public void needStartRecordAudio(int state) {

                }

                @Override
                public void needShowMediaBanHint() {

                }

                @Override
                public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {

                }
            });
        }

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context) {
                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    if (this == undoView[0] && undoView[1].getVisibility() != VISIBLE) {
                        float diff = getMeasuredHeight() + AndroidUtilities.dp(8) - translationY;
                        if (!floatingHidden) {
                            floatingButtonContainer.setTranslationY(floatingButtonContainer.getTranslationY() + additionalFloatingTranslation - diff);
                        }
                        additionalFloatingTranslation = diff;
                    }
                }

                @Override
                protected boolean canUndo() {
                    return !dialogsItemAnimator.isRunning();
                }
            };
            contentView.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        /*if (!onlySelect) {
            checkUnreadCount(false);
        }*/

        if (folderId != 0) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultArchived));
            listView.setGlowColor(Theme.getColor(Theme.key_actionBarDefaultArchived));
            actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultArchivedTitle));
            actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultArchivedIcon), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultArchivedSelector), false);
            actionBar.setSearchTextColor(Theme.getColor(Theme.key_actionBarDefaultArchivedSearch), false);
            actionBar.setSearchTextColor(Theme.getColor(Theme.key_actionBarDefaultArchivedSearchPlaceholder), true);
        }

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dialogsAdapter != null && !dialogsListFrozen) {
            dialogsAdapter.notifyDataSetChanged();
        }
        if (commentView != null) {
            commentView.onResume();
        }
        if (!onlySelect && folderId == 0) {
            getMediaDataController().checkStickers(MediaDataController.TYPE_EMOJI);
        }
        if (dialogsSearchAdapter != null) {
            dialogsSearchAdapter.notifyDataSetChanged();
        }
        if (checkPermission && !onlySelect && Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkPermission = false;
                boolean hasNotContactsPermission = activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED;
                boolean hasNotStoragePermission = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
                if (hasNotContactsPermission || hasNotStoragePermission) {
                    if (hasNotContactsPermission && askAboutContacts && getUserConfig().syncContacts && activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                        AlertDialog.Builder builder = AlertsCreator.createContactsPermissionDialog(activity, param -> {
                            askAboutContacts = param != 0;
                            MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts", askAboutContacts).commit();
                            askForPermissons(false);
                        });
                        showDialog(permissionDialog = builder.create());
                    } else if (hasNotStoragePermission && activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        showDialog(permissionDialog = builder.create());
                    } else {
                        askForPermissons(true);
                    }
                }
            }
        } else if (!onlySelect && XiaomiUtilities.isMIUI() && Build.VERSION.SDK_INT >= 19 && !XiaomiUtilities.isCustomPermissionGranted(XiaomiUtilities.OP_SHOW_WHEN_LOCKED)) {
            if (getParentActivity() == null) {
                return;
            }
            if (MessagesController.getGlobalNotificationsSettings().getBoolean("askedAboutMiuiLockscreen", false)) {
                return;
            }
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(LocaleController.getString("AppName", R.string.AppName))
                    .setMessage(LocaleController.getString("PermissionXiaomiLockscreen", R.string.PermissionXiaomiLockscreen))
                    .setPositiveButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
                        Intent intent = XiaomiUtilities.getPermissionManagerIntent();
                        if (intent != null) {
                            try {
                                getParentActivity().startActivity(intent);
                            } catch (Exception x) {
                                try {
                                    intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                                    getParentActivity().startActivity(intent);
                                } catch (Exception xx) {
                                    FileLog.e(xx);
                                }
                            }
                        }
                    })
                    .setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), (dialog, which) -> MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askedAboutMiuiLockscreen", true).commit())
                    .create());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (commentView != null) {
            commentView.onResume();
        }
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (actionBar != null && actionBar.isActionModeShowed()) {
            hideActionMode(true);
            return false;
        } else if (commentView != null && commentView.isPopupShowing()) {
            commentView.hidePopup(true);
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    protected void onBecomeFullyHidden() {
        if (closeSearchFieldOnHide) {
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
            if (searchObject != null) {
                dialogsSearchAdapter.putRecentSearch(searchDialogId, searchObject);
                searchObject = null;
            }
            closeSearchFieldOnHide = false;
        }
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
    }

    private boolean hasHiddenArchive() {
        return listView.getAdapter() == dialogsAdapter && !onlySelect && dialogsType == 0 && folderId == 0 && getMessagesController().hasHiddenArchive();
    }

    private boolean waitingForDialogsAnimationEnd() {
        return dialogsItemAnimator.isRunning() || dialogRemoveFinished != 0 || dialogInsertFinished != 0 || dialogChangeFinished != 0;
    }

    private void onDialogAnimationFinished() {
        dialogRemoveFinished = 0;
        dialogInsertFinished = 0;
        dialogChangeFinished = 0;
        AndroidUtilities.runOnUIThread(() -> {
            if (folderId != 0 && frozenDialogsList.isEmpty()) {
                listView.setEmptyView(null);
                progressView.setVisibility(View.INVISIBLE);
                finishFragment();
            }
            setDialogsListFrozen(false);
            updateDialogIndices();
        });
    }

    private void hideActionMode(boolean animateCheck) {
        actionBar.hideActionMode();
        if (menuDrawable != null) {
            actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrOpenMenu", R.string.AccDescrOpenMenu));
        }
        dialogsAdapter.getSelectedDialogs().clear();
        if (menuDrawable != null) {
            menuDrawable.setRotation(0, true);
        } else if (backDrawable != null) {
            backDrawable.setRotation(0, true);
        }
        allowMoving = false;
        if (movingWas) {
            getMessagesController().reorderPinnedDialogs(folderId, null, 0);
            movingWas = false;
        }
        updateCounters(true);
        dialogsAdapter.onReorderStateChanged(false);
        updateVisibleRows(MessagesController.UPDATE_MASK_REORDER | MessagesController.UPDATE_MASK_CHECK | (animateCheck ? MessagesController.UPDATE_MASK_CHAT : 0));
    }

    private int getPinnedCount() {
        int pinnedCount = 0;
        ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(folderId);
        for (int a = 0, N = dialogs.size(); a < N; a++) {
            TLRPC.Dialog dialog = dialogs.get(a);
            if (dialog instanceof TLRPC.TL_dialogFolder) {
                continue;
            }
            int lower_id = (int) dialog.id;
            if (dialog.pinned) {
                pinnedCount++;
            } else {
                break;
            }
        }
        return pinnedCount;
    }

    private void perfromSelectedDialogsAction(int action, boolean alert) {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<Long> selectedDialogs = dialogsAdapter.getSelectedDialogs();
        int count = selectedDialogs.size();
        if (action == archive) {
            ArrayList<Long> copy = new ArrayList<>(selectedDialogs);
            getMessagesController().addDialogToFolder(copy, folderId == 0 ? 1 : 0, -1, null, 0);
            hideActionMode(false);
            if (folderId == 0) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean hintShowed = preferences.getBoolean("archivehint_l", false) || SharedConfig.archiveHidden;
                if (!hintShowed) {
                    preferences.edit().putBoolean("archivehint_l", true).commit();
                }
                int undoAction;
                if (hintShowed) {
                    undoAction = copy.size() > 1 ? UndoView.ACTION_ARCHIVE_FEW : UndoView.ACTION_ARCHIVE;
                } else {
                    undoAction = copy.size() > 1 ? UndoView.ACTION_ARCHIVE_FEW_HINT : UndoView.ACTION_ARCHIVE_HINT;
                }
                getUndoView().showWithAction(0, undoAction, null, () -> getMessagesController().addDialogToFolder(copy, folderId == 0 ? 0 : 1, -1, null, 0));
            } else {
                ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(folderId);
                if (dialogs.isEmpty()) {
                    listView.setEmptyView(null);
                    progressView.setVisibility(View.INVISIBLE);
                    finishFragment();
                }
            }
            return;
        } else if (action == pin && canPinCount != 0) {
            int pinnedCount = 0;
            int pinnedSecretCount = 0;
            int newPinnedCount = 0;
            int newPinnedSecretCount = 0;
            ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(folderId);
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (dialog instanceof TLRPC.TL_dialogFolder) {
                    continue;
                }
                int lower_id = (int) dialog.id;
                if (dialog.pinned) {
                    if (lower_id == 0) {
                        pinnedSecretCount++;
                    } else {
                        pinnedCount++;
                    }
                } else {
                    break;
                }
            }
            for (int a = 0; a < count; a++) {
                long selectedDialog = selectedDialogs.get(a);
                TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(selectedDialog);
                if (dialog == null || dialog.pinned) {
                    continue;
                }
                int lower_id = (int) selectedDialog;
                if (lower_id == 0) {
                    newPinnedSecretCount++;
                } else {
                    newPinnedCount++;
                }
            }
            int maxPinnedCount;
            if (folderId != 0) {
                maxPinnedCount = getMessagesController().maxFolderPinnedDialogsCount;
            } else {
                maxPinnedCount = getMessagesController().maxPinnedDialogsCount;
            }
            if (newPinnedSecretCount + pinnedSecretCount > maxPinnedCount || newPinnedCount + pinnedCount > maxPinnedCount) {
                AlertsCreator.showSimpleAlert(DialogsActivity.this, LocaleController.formatString("PinToTopLimitReached", R.string.PinToTopLimitReached, LocaleController.formatPluralString("Chats", maxPinnedCount)));
                AndroidUtilities.shakeView(pinItem, 2, 0);
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                return;
            }
        } else if ((action == delete || action == clear) && count > 1 && alert) {
            if (alert) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                if (action == delete) {
                    builder.setTitle(LocaleController.formatString("DeleteFewChatsTitle", R.string.DeleteFewChatsTitle, LocaleController.formatPluralString("ChatsSelected", count)));
                    builder.setMessage(LocaleController.getString("AreYouSureDeleteFewChats", R.string.AreYouSureDeleteFewChats));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog1, which) -> {
                        getMessagesController().setDialogsInTransaction(true);
                        perfromSelectedDialogsAction(action, false);
                        getMessagesController().setDialogsInTransaction(false);
                        MessagesController.getInstance(currentAccount).checkIfFolderEmpty(folderId);
                        if (folderId != 0 && getDialogsArray(currentAccount, dialogsType, folderId, false).size() == 0) {
                            listView.setEmptyView(null);
                            progressView.setVisibility(View.INVISIBLE);
                            finishFragment();
                        }
                    });
                } else {
                    if (canClearCacheCount != 0) {
                        builder.setTitle(LocaleController.formatString("ClearCacheFewChatsTitle", R.string.ClearCacheFewChatsTitle, LocaleController.formatPluralString("ChatsSelectedClearCache", count)));
                        builder.setMessage(LocaleController.getString("AreYouSureClearHistoryCacheFewChats", R.string.AreYouSureClearHistoryCacheFewChats));
                        builder.setPositiveButton(LocaleController.getString("ClearHistoryCache", R.string.ClearHistoryCache), (dialog1, which) -> perfromSelectedDialogsAction(action, false));
                    } else {
                        builder.setTitle(LocaleController.formatString("ClearFewChatsTitle", R.string.ClearFewChatsTitle, LocaleController.formatPluralString("ChatsSelectedClear", count)));
                        builder.setMessage(LocaleController.getString("AreYouSureClearHistoryFewChats", R.string.AreYouSureClearHistoryFewChats));
                        builder.setPositiveButton(LocaleController.getString("ClearHistory", R.string.ClearHistory), (dialog1, which) -> perfromSelectedDialogsAction(action, false));
                    }
                }
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
                return;
            }
        }
        boolean scrollToTop = false;
        for (int a = 0; a < count; a++) {
            long selectedDialog = selectedDialogs.get(a);
            TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(selectedDialog);
            if (dialog == null) {
                continue;
            }
            TLRPC.Chat chat;
            TLRPC.User user = null;
            int lower_id = (int) selectedDialog;
            int high_id = (int) (selectedDialog >> 32);
            if (lower_id != 0) {
                if (lower_id > 0) {
                    user = getMessagesController().getUser(lower_id);
                    chat = null;
                } else {
                    chat = getMessagesController().getChat(-lower_id);
                }
            } else {
                TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(high_id);
                chat = null;
                if (encryptedChat != null) {
                    user = getMessagesController().getUser(encryptedChat.user_id);
                } else {
                    user = new TLRPC.TL_userEmpty();
                }
            }
            if (chat == null && user == null) {
                continue;
            }
            boolean isBot = user != null && user.bot && !MessagesController.isSupportUser(user);
            if (action == pin) {
                if (canPinCount != 0) {
                    if (dialog.pinned) {
                        continue;
                    }
                    if (getMessagesController().pinDialog(selectedDialog, true, null, -1)) {
                        scrollToTop = true;
                    }
                } else {
                    if (!dialog.pinned) {
                        continue;
                    }
                    if (getMessagesController().pinDialog(selectedDialog, false, null, -1)) {
                        scrollToTop = true;
                    }
                }
            } else if (action == read) {
                if (canReadCount != 0) {
                    getMessagesController().markMentionsAsRead(selectedDialog);
                    getMessagesController().markDialogAsRead(selectedDialog, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, true, 0);
                } else {
                    getMessagesController().markDialogAsUnread(selectedDialog, null, 0);
                }
            } else if (action == delete || action == clear) {
                if (count == 1) {
                    AlertsCreator.createClearOrDeleteDialogAlert(DialogsActivity.this, action == clear, chat, user, lower_id == 0, (param) -> {
                        hideActionMode(false);
                        if (action == clear && ChatObject.isChannel(chat) && (!chat.megagroup || !TextUtils.isEmpty(chat.username))) {
                            getMessagesController().deleteDialog(selectedDialog, 2, param);
                        } else {
                            if (action == delete && folderId != 0 && getDialogsArray(currentAccount, dialogsType, folderId, false).size() == 1) {
                                progressView.setVisibility(View.INVISIBLE);
                            }
                            getUndoView().showWithAction(selectedDialog, action == clear ? UndoView.ACTION_CLEAR : UndoView.ACTION_DELETE, () -> {
                                if (action == clear) {
                                    getMessagesController().deleteDialog(selectedDialog, 1, param);
                                } else {
                                    if (chat != null) {
                                        if (ChatObject.isNotInChat(chat)) {
                                            getMessagesController().deleteDialog(selectedDialog, 0, param);
                                        } else {
                                            TLRPC.User currentUser = getMessagesController().getUser(getUserConfig().getClientUserId());
                                            getMessagesController().deleteUserFromChat((int) -selectedDialog, currentUser, null);
                                        }
                                    } else {
                                        getMessagesController().deleteDialog(selectedDialog, 0, param);
                                        if (isBot) {
                                            getMessagesController().blockUser((int) selectedDialog);
                                        }
                                    }
                                    if (AndroidUtilities.isTablet()) {
                                        getNotificationCenter().postNotificationName(NotificationCenter.closeChats, selectedDialog);
                                    }
                                    MessagesController.getInstance(currentAccount).checkIfFolderEmpty(folderId);
                                }
                            });
                        }
                    });
                    return;
                } else {
                    if (action == clear && canClearCacheCount != 0) {
                        getMessagesController().deleteDialog(selectedDialog, 2, false);
                    } else {
                        if (action == clear) {
                            getMessagesController().deleteDialog(selectedDialog, 1, false);
                        } else {
                            if (chat != null) {
                                if (ChatObject.isNotInChat(chat)) {
                                    getMessagesController().deleteDialog(selectedDialog, 0, false);
                                } else {
                                    TLRPC.User currentUser = getMessagesController().getUser(getUserConfig().getClientUserId());
                                    getMessagesController().deleteUserFromChat((int) -selectedDialog, currentUser, null);
                                }
                            } else {
                                getMessagesController().deleteDialog(selectedDialog, 0, false);
                                if (isBot) {
                                    getMessagesController().blockUser((int) selectedDialog);
                                }
                            }
                            if (AndroidUtilities.isTablet()) {
                                getNotificationCenter().postNotificationName(NotificationCenter.closeChats, selectedDialog);
                            }
                        }
                    }
                }
            } else if (action == mute) {
                if (count == 1 && canMuteCount == 1) {
                    showDialog(AlertsCreator.createMuteAlert(getParentActivity(), selectedDialog), dialog12 -> hideActionMode(true));
                    return;
                } else {
                    if (canUnmuteCount != 0) {
                        if (!getMessagesController().isDialogMuted(selectedDialog)) {
                            continue;
                        }
                        getNotificationsController().setDialogNotificationsSettings(selectedDialog, NotificationsController.SETTING_MUTE_UNMUTE);
                    } else {
                        if (getMessagesController().isDialogMuted(selectedDialog)) {
                            continue;
                        }
                        getNotificationsController().setDialogNotificationsSettings(selectedDialog, NotificationsController.SETTING_MUTE_FOREVER);
                    }
                }
            }
        }
        if (action == pin) {
            getMessagesController().reorderPinnedDialogs(folderId, null, 0);
        }
        if (scrollToTop) {
            hideFloatingButton(false);
            listView.smoothScrollToPosition(hasHiddenArchive() ? 1 : 0);
        }
        hideActionMode(action != pin && action != delete);
    }

    private void updateCounters(boolean hide) {
        int canClearHistoryCount = 0;
        int canDeleteCount = 0;
        int canUnpinCount = 0;
        int canArchiveCount = 0;
        int canUnarchiveCount = 0;
        canUnmuteCount = 0;
        canMuteCount = 0;
        canPinCount = 0;
        canReadCount = 0;
        canClearCacheCount = 0;
        if (hide) {
            return;
        }
        ArrayList<Long> selectedDialogs = dialogsAdapter.getSelectedDialogs();
        int count = selectedDialogs.size();
        for (int a = 0; a < count; a++) {
            TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(selectedDialogs.get(a));
            if (dialog == null) {
                continue;
            }

            long selectedDialog = dialog.id;
            boolean pinned = dialog.pinned;
            boolean hasUnread = dialog.unread_count != 0 || dialog.unread_mark;
            if (getMessagesController().isDialogMuted(selectedDialog)) {
                canUnmuteCount++;
            } else {
                canMuteCount++;
            }

            if (hasUnread) {
                canReadCount++;
            }

            if (folderId == 1) {
                canUnarchiveCount++;
            } else if (selectedDialog != getUserConfig().getClientUserId() && selectedDialog != 777000 && !getMessagesController().isProxyDialog(selectedDialog, false)) {
                canArchiveCount++;
            }

            int lower_id = (int) selectedDialog;
            int high_id = (int) (selectedDialog >> 32);

            if (DialogObject.isChannel(dialog)) {
                final TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
                CharSequence[] items;
                if (getMessagesController().isProxyDialog(dialog.id, true)) {
                    canClearCacheCount++;
                } else {
                    if (pinned) {
                        canUnpinCount++;
                    } else {
                        canPinCount++;
                    }
                    if (chat != null && chat.megagroup) {
                        if (TextUtils.isEmpty(chat.username)) {
                            canClearHistoryCount++;
                        } else {
                            canClearCacheCount++;
                        }
                        canDeleteCount++;
                    } else {
                        canClearCacheCount++;
                        canDeleteCount++;
                    }
                }
            } else {
                final boolean isChat = lower_id < 0 && high_id != 1;
                TLRPC.User user;
                TLRPC.Chat chat = isChat ? getMessagesController().getChat(-lower_id) : null;
                if (lower_id == 0) {
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(high_id);
                    if (encryptedChat != null) {
                        user = getMessagesController().getUser(encryptedChat.user_id);
                    } else {
                        user = new TLRPC.TL_userEmpty();
                    }
                } else {
                    user = !isChat && lower_id > 0 && high_id != 1 ? getMessagesController().getUser(lower_id) : null;
                }
                final boolean isBot = user != null && user.bot && !MessagesController.isSupportUser(user);

                if (pinned) {
                    canUnpinCount++;
                } else {
                    canPinCount++;
                }
                canClearHistoryCount++;
                canDeleteCount++;
            }
        }
        if (canDeleteCount != count) {
            deleteItem.setVisibility(View.GONE);
        } else {
            deleteItem.setVisibility(View.VISIBLE);
        }
        if (canClearCacheCount != 0 && canClearCacheCount != count || canClearHistoryCount != 0 && canClearHistoryCount != count) {
            clearItem.setVisibility(View.GONE);
        } else {
            clearItem.setVisibility(View.VISIBLE);
            if (canClearCacheCount != 0) {
                clearItem.setText(LocaleController.getString("ClearHistoryCache", R.string.ClearHistoryCache));
            } else {
                clearItem.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
            }
        }
        if (canUnarchiveCount != 0) {
            archiveItem.setTextAndIcon(LocaleController.getString("Unarchive", R.string.Unarchive), R.drawable.msg_unarchive);
            archiveItem.setVisibility(View.VISIBLE);
        } else if (canArchiveCount != 0) {
            archiveItem.setTextAndIcon(LocaleController.getString("Archive", R.string.Archive), R.drawable.msg_archive);
            archiveItem.setVisibility(View.VISIBLE);
        } else {
            archiveItem.setVisibility(View.GONE);
        }
        if (canPinCount + canUnpinCount != count) {
            pinItem.setVisibility(View.GONE);
        } else {
            pinItem.setVisibility(View.VISIBLE);
        }
        if (canUnmuteCount != 0) {
            muteItem.setIcon(R.drawable.msg_unmute);
            muteItem.setContentDescription(LocaleController.getString("ChatsUnmute", R.string.ChatsUnmute));
        } else {
            muteItem.setIcon(R.drawable.msg_mute);
            muteItem.setContentDescription(LocaleController.getString("ChatsMute", R.string.ChatsMute));
        }
        if (canReadCount != 0) {
            readItem.setTextAndIcon(LocaleController.getString("MarkAsRead", R.string.MarkAsRead), R.drawable.msg_markread);
        } else {
            readItem.setTextAndIcon(LocaleController.getString("MarkAsUnread", R.string.MarkAsUnread), R.drawable.msg_markunread);
        }
        if (canPinCount != 0) {
            pinItem.setIcon(R.drawable.msg_pin);
            pinItem.setContentDescription(LocaleController.getString("PinToTop", R.string.PinToTop));
        } else {
            pinItem.setIcon(R.drawable.msg_unpin);
            pinItem.setContentDescription(LocaleController.getString("UnpinFromTop", R.string.UnpinFromTop));
        }
    }

    private boolean validateSlowModeDialog(long dialogId) {
        if (messagesCount <= 1 && (commentView == null || commentView.getVisibility() != View.VISIBLE || TextUtils.isEmpty(commentView.getFieldText()))) {
            return true;
        }
        int lowerId = (int) dialogId;
        if (lowerId >= 0) {
            return true;
        }
        TLRPC.Chat chat = getMessagesController().getChat(-lowerId);
        if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
            AlertsCreator.showSimpleAlert(DialogsActivity.this, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError));
            return false;
        }
        return true;
    }

    private void showOrUpdateActionMode(TLRPC.Dialog dialog, View cell) {
        dialogsAdapter.addOrRemoveSelectedDialog(dialog.id, cell);
        ArrayList<Long> selectedDialogs = dialogsAdapter.getSelectedDialogs();
        boolean updateAnimated = false;
        if (actionBar.isActionModeShowed()) {
            if (selectedDialogs.isEmpty()) {
                hideActionMode(true);
                return;
            }
            updateAnimated = true;
        } else {
            final ActionBarMenu actionMode = actionBar.createActionMode();
            actionBar.showActionMode();
            if (menuDrawable != null) {
                actionBar.setBackButtonContentDescription(LocaleController.getString("AccDescrGoBack", R.string.AccDescrGoBack));
            }
            if (getPinnedCount() > 1) {
                dialogsAdapter.onReorderStateChanged(true);
                updateVisibleRows(MessagesController.UPDATE_MASK_REORDER);
            }

            AnimatorSet animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            for (int a = 0; a < actionModeViews.size(); a++) {
                View view = actionModeViews.get(a);
                view.setPivotY(ActionBar.getCurrentActionBarHeight() / 2);
                AndroidUtilities.clearDrawableAnimation(view);
                animators.add(ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.1f, 1.0f));
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(250);
            animatorSet.start();
            if (menuDrawable != null) {
                menuDrawable.setRotateToBack(false);
                menuDrawable.setRotation(1, true);
            } else if (backDrawable != null) {
                backDrawable.setRotation(1, true);
            }
        }
        updateCounters(false);
        selectedDialogsCountTextView.setNumber(selectedDialogs.size(), updateAnimated);
    }

    private void closeSearch() {
        if (AndroidUtilities.isTablet()) {
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
            if (searchObject != null) {
                dialogsSearchAdapter.putRecentSearch(searchDialogId, searchObject);
                searchObject = null;
            }
        } else {
            closeSearchFieldOnHide = true;
        }
    }

    /*private void checkUnreadCount(boolean animated) {
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            return;
        }
        int newCount = getMessagesController().unreadUnmutedDialogs;
        if (newCount != currentUnreadCount) {
            currentUnreadCount = newCount;
            if (unreadFloatingButtonContainer != null) {
                if (currentUnreadCount > 0) {
                    unreadFloatingButtonCounter.setText(String.format("%d", currentUnreadCount));
                }
                checkUnreadButton(animated);
            }
        }
    }

    private void checkUnreadButton(boolean animated) {
        if (!onlySelect && listView.getAdapter() == dialogsAdapter) {
            boolean found = false;
            if (currentUnreadCount > 0) {
                int middle = listView.getMeasuredHeight() / 2;
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int count = listView.getChildCount();
                int unreadOnScreen = 0;
                for (int b = 0; b < count; b++) {
                    View child = listView.getChildAt(b);
                    if (child instanceof DialogCell) {
                        if (((DialogCell) child).isUnread()) {
                            unreadOnScreen++;
                        }
                    }
                }
                for (int b = 0; b < count; b++) {
                    View child = listView.getChildAt(b);
                    if (child instanceof DialogCell) {
                        if (child.getTop() <= middle && child.getBottom() >= middle) {
                            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
                            if (holder != null) {
                                ArrayList<TLRPC.Dialog> array = getDialogsArray();
                                if (firstVisibleItem == 0) {
                                    if (unreadOnScreen != currentUnreadCount) {
                                        for (int a = holder.getAdapterPosition() + 1, size = array.size(); a < size; a++) {
                                            TLRPC.Dialog dialog = array.get(a);
                                            if ((dialog.unread_count != 0 || dialog.unread_mark) && !getMessagesController().isDialogMuted(dialog.id)) {
                                                arrowDrawable.setAnimationProgressAnimated(1.0f);
                                                found = true;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    found = true;
                                    arrowDrawable.setAnimationProgressAnimated(0.0f);
                                }
                            }
                            break;
                        }
                    }
                }
            }
            if (found) {
                if (unreadFloatingButtonContainer.getTag() == null) {
                    unreadFloatingButtonContainer.setTag(1);
                    unreadFloatingButtonContainer.setVisibility(View.VISIBLE);
                    if (animated) {
                        unreadFloatingButtonContainer.animate().alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setDelegate(null).start();
                    } else {
                        unreadFloatingButtonContainer.setAlpha(1.0f);
                    }
                }
            } else {
                if (unreadFloatingButtonContainer.getTag() != null) {
                    unreadFloatingButtonContainer.setTag(null);
                    if (animated) {
                        unreadFloatingButtonContainer.animate().alpha(0.0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setDelegate(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                unreadFloatingButtonContainer.setVisibility(View.INVISIBLE);
                            }
                        }).start();
                    } else {
                        unreadFloatingButtonContainer.setAlpha(0.0f);
                        unreadFloatingButtonContainer.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    }*/

    protected RecyclerListView getListView() {
        return listView;
    }

    private UndoView getUndoView() {
        if (undoView[0].getVisibility() == View.VISIBLE) {
            UndoView old = undoView[0];
            undoView[0] = undoView[1];
            undoView[1] = old;
            old.hide(true, 2);
            ContentView contentView = (ContentView) fragmentView;
            contentView.removeView(undoView[0]);
            contentView.addView(undoView[0]);
        }
        return undoView[0];
    }

    private void updateProxyButton(boolean animated) {
        if (proxyDrawable == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String proxyAddress = preferences.getString("proxy_ip", "");
        boolean proxyEnabled;
        if ((proxyEnabled = preferences.getBoolean("proxy_enabled", false) && !TextUtils.isEmpty(proxyAddress)) || getMessagesController().blockedCountry && !SharedConfig.proxyList.isEmpty()) {
            if (!actionBar.isSearchFieldVisible()) {
                proxyItem.setVisibility(View.VISIBLE);
            }
            proxyDrawable.setConnected(proxyEnabled, currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating, animated);
            proxyItemVisisble = true;
        } else {
            proxyItem.setVisibility(View.GONE);
            proxyItemVisisble = false;
        }
    }

    private void updateSelectedCount() {
        if (commentView == null) {
            return;
        }
        if (!dialogsAdapter.hasSelectedDialogs()) {
            if (dialogsType == 3 && selectAlertString == null) {
                actionBar.setTitle(LocaleController.getString("ForwardTo", R.string.ForwardTo));
            } else {
                actionBar.setTitle(LocaleController.getString("SelectChat", R.string.SelectChat));
            }
            if (commentView.getTag() != null) {
                commentView.hidePopup(false);
                commentView.closeKeyboard();
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(commentView, View.TRANSLATION_Y, 0, commentView.getMeasuredHeight()));
                animatorSet.setDuration(180);
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        commentView.setVisibility(View.GONE);
                    }
                });
                animatorSet.start();
                commentView.setTag(null);
                listView.requestLayout();
            }
        } else {
            if (commentView.getTag() == null) {
                commentView.setFieldText("");
                commentView.setVisibility(View.VISIBLE);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(commentView, View.TRANSLATION_Y, commentView.getMeasuredHeight(), 0));
                animatorSet.setDuration(180);
                animatorSet.setInterpolator(new DecelerateInterpolator());
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        commentView.setTag(2);
                        commentView.requestLayout();
                    }
                });
                animatorSet.start();
                commentView.setTag(1);
            }
            actionBar.setTitle(LocaleController.formatPluralString("Recipient", dialogsAdapter.getSelectedDialogs().size()));
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void askForPermissons(boolean alert) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        ArrayList<String> permissons = new ArrayList<>();
        if (getUserConfig().syncContacts && askAboutContacts && activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (alert) {
                AlertDialog.Builder builder = AlertsCreator.createContactsPermissionDialog(activity, param -> {
                    askAboutContacts = param != 0;
                    MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts", askAboutContacts).commit();
                    askForPermissons(false);
                });
                showDialog(permissionDialog = builder.create());
                return;
            }
            permissons.add(Manifest.permission.READ_CONTACTS);
            permissons.add(Manifest.permission.WRITE_CONTACTS);
            permissons.add(Manifest.permission.GET_ACCOUNTS);
        }
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissons.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissons.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissons.isEmpty()) {
            return;
        }
        String[] items = permissons.toArray(new String[0]);
        try {
            activity.requestPermissions(items, 1);
        } catch (Exception ignore) {
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);
        if (permissionDialog != null && dialog == permissionDialog && getParentActivity() != null) {
            askForPermissons(false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!onlySelect && floatingButtonContainer != null) {
            floatingButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    floatingButtonContainer.setTranslationY((floatingHidden ? AndroidUtilities.dp(100) : -additionalFloatingTranslation));
                    //unreadFloatingButtonContainer.setTranslationY(floatingHidden ? AndroidUtilities.dp(74) : 0);
                    floatingButtonContainer.setClickable(!floatingHidden);
                    if (floatingButtonContainer != null) {
                        floatingButtonContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            for (int a = 0; a < permissions.length; a++) {
                if (grantResults.length <= a) {
                    continue;
                }
                switch (permissions[a]) {
                    case Manifest.permission.READ_CONTACTS:
                        if (grantResults[a] == PackageManager.PERMISSION_GRANTED) {
                            getContactsController().forceImportContacts();
                        } else {
                            MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts", askAboutContacts = false).commit();
                        }
                        break;
                    case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                        if (grantResults[a] == PackageManager.PERMISSION_GRANTED) {
                            ImageLoader.getInstance().checkMediaPaths();
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (dialogsListFrozen) {
                return;
            }
            //checkUnreadCount(true);
            if (dialogsAdapter != null) {
                if (dialogsAdapter.isDataSetChanged() || args.length > 0) {
                    dialogsAdapter.notifyDataSetChanged();
                } else {
                    updateVisibleRows(MessagesController.UPDATE_MASK_NEW_MESSAGE);
                }
            }
            if (listView != null) {
                try {
                    if (listView.getAdapter() == dialogsAdapter) {
                        searchEmptyView.setVisibility(View.GONE);
                        listView.setEmptyView(folderId == 0 ? progressView : null);
                    } else {
                        if (searching && searchWas) {
                            listView.setEmptyView(searchEmptyView);
                        } else {
                            searchEmptyView.setVisibility(View.GONE);
                            listView.setEmptyView(null);
                        }
                        progressView.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.emojiDidLoad) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.closeSearchByActiveAction) {
            if (actionBar != null) {
                actionBar.closeSearchField();
            }
        } else if (id == NotificationCenter.proxySettingsChanged) {
            updateProxyButton(false);
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer mask = (Integer) args[0];
            updateVisibleRows(mask);
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0 && dialogsAdapter != null) {
                dialogsAdapter.sortOnlineContacts(true);
            }
            /*if ((mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) != 0 || (mask & MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE) != 0) {
                checkUnreadCount(true);
            }*/
        } else if (id == NotificationCenter.appDidLogout) {
            dialogsLoaded[currentAccount] = false;
        } else if (id == NotificationCenter.encryptedChatUpdated) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.contactsDidLoad) {
            if (dialogsListFrozen) {
                return;
            }
            if (dialogsType == 0 && getMessagesController().getDialogs(folderId).isEmpty()) {
                if (dialogsAdapter != null) {
                    dialogsAdapter.notifyDataSetChanged();
                }
            } else {
                updateVisibleRows(0);
            }
        } else if (id == NotificationCenter.openedChatChanged) {
            if (dialogsType == 0 && AndroidUtilities.isTablet()) {
                boolean close = (Boolean) args[1];
                long dialog_id = (Long) args[0];
                if (close) {
                    if (dialog_id == openedDialogId) {
                        openedDialogId = 0;
                    }
                } else {
                    openedDialogId = dialog_id;
                }
                if (dialogsAdapter != null) {
                    dialogsAdapter.setOpenedDialogId(openedDialogId);
                }
                updateVisibleRows(MessagesController.UPDATE_MASK_SELECT_DIALOG);
            }
        } else if (id == NotificationCenter.notificationsSettingsUpdated) {
            updateVisibleRows(0);
        } else if (id == NotificationCenter.messageReceivedByAck || id == NotificationCenter.messageReceivedByServer || id == NotificationCenter.messageSendError) {
            updateVisibleRows(MessagesController.UPDATE_MASK_SEND_STATE);
        } else if (id == NotificationCenter.didSetPasscode) {
            updatePasscodeButton();
        } else if (id == NotificationCenter.needReloadRecentDialogsSearch) {
            if (dialogsSearchAdapter != null) {
                dialogsSearchAdapter.loadRecentSearch();
            }
        } else if (id == NotificationCenter.replyMessagesDidLoad) {
            updateVisibleRows(MessagesController.UPDATE_MASK_MESSAGE_TEXT);
        } else if (id == NotificationCenter.reloadHints) {
            if (dialogsSearchAdapter != null) {
                dialogsSearchAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = AccountInstance.getInstance(account).getConnectionsManager().getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                updateProxyButton(true);
            }
        } else if (id == NotificationCenter.dialogsUnreadCounterChanged) {
            /*if (!onlySelect) {
                int count = (Integer) args[0];
                currentUnreadCount = count;
                if (count != 0) {
                    unreadFloatingButtonCounter.setText(String.format("%d", count));
                    unreadFloatingButtonContainer.setVisibility(View.VISIBLE);
                    unreadFloatingButtonContainer.setTag(1);
                    unreadFloatingButtonContainer.animate().alpha(1.0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setDelegate(null).start();
                } else {
                    unreadFloatingButtonContainer.setTag(null);
                    unreadFloatingButtonContainer.animate().alpha(0.0f).setDuration(200).setInterpolator(new DecelerateInterpolator()).setDelegate(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            unreadFloatingButtonContainer.setVisibility(View.INVISIBLE);
                        }
                    }).start();
                }
            }*/
        } else if (id == NotificationCenter.needDeleteDialog) {
            if (fragmentView == null || isPaused) {
                return;
            }
            long dialogId = (Long) args[0];
            TLRPC.User user = (TLRPC.User) args[1];
            TLRPC.Chat chat = (TLRPC.Chat) args[2];
            boolean revoke = (Boolean) args[3];
            Runnable deleteRunnable = () -> {
                if (chat != null) {
                    if (ChatObject.isNotInChat(chat)) {
                        getMessagesController().deleteDialog(dialogId, 0, revoke);
                    } else {
                        getMessagesController().deleteUserFromChat((int) -dialogId, getMessagesController().getUser(getUserConfig().getClientUserId()), null, false, revoke);
                    }
                } else {
                    getMessagesController().deleteDialog(dialogId, 0, revoke);
                }
                MessagesController.getInstance(currentAccount).checkIfFolderEmpty(folderId);
            };
            if (undoView[0] != null) {
                getUndoView().showWithAction(dialogId, UndoView.ACTION_DELETE, deleteRunnable);
            } else {
                deleteRunnable.run();
            }
        } else if (id == NotificationCenter.folderBecomeEmpty) {
            int fid = (Integer) args[0];
            if (folderId == fid && folderId != 0) {
                finishFragment();
            }
        }
    }

    private void setDialogsListFrozen(boolean frozen) {
        if (dialogsListFrozen == frozen) {
            return;
        }
        if (frozen) {
            frozenDialogsList = new ArrayList<>(getDialogsArray(currentAccount, dialogsType, folderId, false));
        } else {
            frozenDialogsList = null;
        }
        dialogsListFrozen = frozen;
        dialogsAdapter.setDialogsListFrozen(frozen);
        if (!frozen) {
            dialogsAdapter.notifyDataSetChanged();
        }
    }

    public static ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen && frozenDialogsList != null) {
            return frozenDialogsList;
        }
        MessagesController messagesController = AccountInstance.getInstance(currentAccount).getMessagesController();
        if (dialogsType == 0) {
            return messagesController.getDialogs(folderId);
        } else if (dialogsType == 1) {
            return messagesController.dialogsServerOnly;
        } else if (dialogsType == 2) {
            return messagesController.dialogsCanAddUsers;
        } else if (dialogsType == 3) {
            return messagesController.dialogsForward;
        } else if (dialogsType == 4) {
            return messagesController.dialogsUsersOnly;
        } else if (dialogsType == 5) {
            return messagesController.dialogsChannelsOnly;
        } else if (dialogsType == 6) {
            return messagesController.dialogsGroupsOnly;
        }
        return null;
    }

    public void setSideMenu(RecyclerView recyclerView) {
        sideMenu = recyclerView;
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
    }

    private void updatePasscodeButton() {
        if (passcodeItem == null) {
            return;
        }
        if (SharedConfig.passcodeHash.length() != 0 && !searching) {
            passcodeItem.setVisibility(View.VISIBLE);
            if (SharedConfig.appLocked) {
                passcodeItem.setIcon(R.drawable.lock_close);
                passcodeItem.setContentDescription(LocaleController.getString("AccDescrPasscodeUnlock", R.string.AccDescrPasscodeUnlock));
            } else {
                passcodeItem.setIcon(R.drawable.lock_open);
                passcodeItem.setContentDescription(LocaleController.getString("AccDescrPasscodeLock", R.string.AccDescrPasscodeLock));
            }
        } else {
            passcodeItem.setVisibility(View.GONE);
        }
    }

    private void hideFloatingButton(boolean hide) {
        if (floatingHidden == hide) {
            return;
        }
        floatingHidden = hide;
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(floatingButtonContainer, View.TRANSLATION_Y,  (floatingHidden ? AndroidUtilities.dp(100) : -additionalFloatingTranslation))/*,
                ObjectAnimator.ofFloat(unreadFloatingButtonContainer, View.TRANSLATION_Y, floatingHidden ? AndroidUtilities.dp(74) : 0)*/);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(floatingInterpolator);
        floatingButtonContainer.setClickable(!hide);
        animatorSet.start();
    }

    private void updateDialogIndices() {
        if (listView == null || listView.getAdapter() != dialogsAdapter) {
            return;
        }
        ArrayList<TLRPC.Dialog> dialogs = getDialogsArray(currentAccount, dialogsType, folderId, false);
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof DialogCell) {
                DialogCell dialogCell = (DialogCell) child;
                TLRPC.Dialog dialog = getMessagesController().dialogs_dict.get(dialogCell.getDialogId());
                if (dialog == null) {
                    continue;
                }
                int index = dialogs.indexOf(dialog);
                if (index < 0) {
                    continue;
                }
                dialogCell.setDialogIndex(index);
            }
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null || dialogsListFrozen) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof DialogCell) {
                if (listView.getAdapter() != dialogsSearchAdapter) {
                    DialogCell cell = (DialogCell) child;
                    if ((mask & MessagesController.UPDATE_MASK_REORDER) != 0) {
                        cell.onReorderStateChanged(actionBar.isActionModeShowed(), true);
                    }
                    if ((mask & MessagesController.UPDATE_MASK_CHECK) != 0) {
                        cell.setChecked(false, (mask & MessagesController.UPDATE_MASK_CHAT) != 0);
                    } else {
                        if ((mask & MessagesController.UPDATE_MASK_NEW_MESSAGE) != 0) {
                            cell.checkCurrentDialogIndex(dialogsListFrozen);
                            if (dialogsType == 0 && AndroidUtilities.isTablet()) {
                                cell.setDialogSelected(cell.getDialogId() == openedDialogId);
                            }
                        } else if ((mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) != 0) {
                            if (dialogsType == 0 && AndroidUtilities.isTablet()) {
                                cell.setDialogSelected(cell.getDialogId() == openedDialogId);
                            }
                        } else {
                            cell.update(mask);
                        }
                        ArrayList<Long> selectedDialogs = dialogsAdapter.getSelectedDialogs();
                        if (selectedDialogs != null) {
                            cell.setChecked(selectedDialogs.contains(cell.getDialogId()), false);
                        }
                    }
                }
            } else if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            } else if (child instanceof ProfileSearchCell) {
                ((ProfileSearchCell) child).update(mask);
            } else if (child instanceof RecyclerListView) {
                RecyclerListView innerListView = (RecyclerListView) child;
                int count2 = innerListView.getChildCount();
                for (int b = 0; b < count2; b++) {
                    View child2 = innerListView.getChildAt(b);
                    if (child2 instanceof HintDialogCell) {
                        ((HintDialogCell) child2).update(mask);
                    }
                }
            }
        }
    }

    public void setDelegate(DialogsActivityDelegate dialogsActivityDelegate) {
        delegate = dialogsActivityDelegate;
    }

    public void setSearchString(String string) {
        searchString = string;
    }

    public boolean isMainDialogList() {
        return delegate == null && searchString == null;
    }

    private void didSelectResult(final long dialog_id, boolean useAlert, final boolean param) {
        if (addToGroupAlertString == null && checkCanWrite) {
            if ((int) dialog_id < 0) {
                TLRPC.Chat chat = getMessagesController().getChat(-(int) dialog_id);
                if (ChatObject.isChannel(chat) && !chat.megagroup && (cantSendToChannels || !ChatObject.isCanWriteToChannel(-(int) dialog_id, currentAccount))) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ChannelCantSendMessage", R.string.ChannelCantSendMessage));
                    builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());
                    return;
                }
            }
        }
        if (useAlert && (selectAlertString != null && selectAlertStringGroup != null || addToGroupAlertString != null)) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            int lower_part = (int) dialog_id;
            int high_id = (int) (dialog_id >> 32);
            if (lower_part != 0) {
                if (lower_part == getUserConfig().getClientUserId()) {
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertStringGroup, LocaleController.getString("SavedMessages", R.string.SavedMessages)));
                } else if (lower_part > 0) {
                    TLRPC.User user = getMessagesController().getUser(lower_part);
                    if (user == null) {
                        return;
                    }
                    builder.setMessage(LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user)));
                } else if (lower_part < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-lower_part);
                    if (chat == null) {
                        return;
                    }
                    if (addToGroupAlertString != null) {
                        builder.setMessage(LocaleController.formatStringSimple(addToGroupAlertString, chat.title));
                    } else {
                        builder.setMessage(LocaleController.formatStringSimple(selectAlertStringGroup, chat.title));
                    }
                }
            } else {
                TLRPC.EncryptedChat chat = getMessagesController().getEncryptedChat(high_id);
                TLRPC.User user = getMessagesController().getUser(chat.user_id);
                if (user == null) {
                    return;
                }
                builder.setMessage(LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user)));
            }

            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> didSelectResult(dialog_id, false, false));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        } else {
            if (delegate != null) {
                ArrayList<Long> dids = new ArrayList<>();
                dids.add(dialog_id);
                delegate.didSelectDialogs(DialogsActivity.this, dids, null, param);
                if (resetDelegate) {
                    delegate = null;
                }
            } else {
                finishFragment();
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ProfileSearchCell) {
                        ((ProfileSearchCell) child).update(0);
                    } else if (child instanceof DialogCell) {
                        ((DialogCell) child).update(0);
                    }
                }
            }
            if (dialogsSearchAdapter != null) {
                RecyclerListView recyclerListView = dialogsSearchAdapter.getInnerListView();
                if (recyclerListView != null) {
                    int count = recyclerListView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = recyclerListView.getChildAt(a);
                        if (child instanceof HintDialogCell) {
                            ((HintDialogCell) child).update();
                        }
                    }
                }
            }
            if (sideMenu != null) {
                View child = sideMenu.getChildAt(0);
                if (child instanceof DrawerProfileCell) {
                    DrawerProfileCell profileCell = (DrawerProfileCell) child;
                    profileCell.applyBackground(true);
                }
            }
        };

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        if (movingView != null) {
            arrayList.add(new ThemeDescription(movingView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        }

        if (folderId == 0) {
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, new Drawable[]{Theme.dialogs_holidayDrawable}, null, Theme.key_actionBarDefaultTitle));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));
        } else {
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefaultArchived));
            arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefaultArchived));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultArchivedIcon));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, new Drawable[]{Theme.dialogs_holidayDrawable}, null, Theme.key_actionBarDefaultArchivedTitle));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultArchivedSelector));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultArchivedSearch));
            arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultArchivedSearchPlaceholder));
        }

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        arrayList.add(new ThemeDescription(selectedDialogsCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(searchEmptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        arrayList.add(new ThemeDescription(searchEmptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView1"}, null, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DialogsEmptyCell.class}, new String[]{"emptyTextView2"}, null, null, null, Theme.key_chats_message));

        arrayList.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        arrayList.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
        arrayList.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));

        /*new ThemeDescription(unreadFloatingButtonCounter, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chat_goDownButtonCounterBackground));
        new ThemeDescription(unreadFloatingButtonCounter, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_goDownButtonCounter));
        new ThemeDescription(unreadFloatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionUnreadIcon));
        new ThemeDescription(unreadFloatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionUnreadBackground));
        new ThemeDescription(unreadFloatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionUnreadPressedBackground));*/

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundSaved));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundArchivedHidden));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_namePaint, Theme.dialogs_searchNamePaint}, null, null, Theme.key_chats_name));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Paint[]{Theme.dialogs_nameEncryptedPaint, Theme.dialogs_searchNameEncryptedPaint}, null, null, Theme.key_chats_secretName));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_lockDrawable}, null, Theme.key_chats_secretIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_groupDrawable, Theme.dialogs_broadcastDrawable, Theme.dialogs_botDrawable}, null, Theme.key_chats_nameIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_scamDrawable}, null, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_pinnedDrawable, Theme.dialogs_reorderDrawable}, null, Theme.key_chats_pinnedIcon));
        if (SharedConfig.useThreeLinesLayout) {
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint, null, null, Theme.key_chats_message_threeLines));
        } else {
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePaint, null, null, Theme.key_chats_message));
        }
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messageNamePaint, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessage));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_draft));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_attachMessage));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessageArchived));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_nameMessageArchived_threeLines));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_chats_messageArchived));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_messagePrintingPaint, null, null, Theme.key_chats_actionMessage));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_timePaint, null, null, Theme.key_chats_date));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_pinnedPaint, null, null, Theme.key_chats_pinnedOverlay));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_tabletSeletedPaint, null, null, Theme.key_chats_tabletSelectedOverlay));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkDrawable}, null, Theme.key_chats_sentCheck));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_checkReadDrawable, Theme.dialogs_halfCheckDrawable}, null, Theme.key_chats_sentReadCheck));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_clockDrawable}, null, Theme.key_chats_sentClock));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, Theme.dialogs_errorPaint, null, null, Theme.key_chats_sentError));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_errorDrawable}, null, Theme.key_chats_sentErrorIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedCheckDrawable}, null, Theme.key_chats_verifiedCheck));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class, ProfileSearchCell.class}, null, new Drawable[]{Theme.dialogs_verifiedDrawable}, null, Theme.key_chats_verifiedBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_muteDrawable}, null, Theme.key_chats_muteIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, new Drawable[]{Theme.dialogs_mentionDrawable}, null, Theme.key_chats_mentionIcon));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archivePinBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, null, null, null, Theme.key_chats_archiveBackground));

        if (SharedConfig.archiveHidden) {
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchivedHidden));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchivedHidden));
        } else {
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow1", Theme.key_avatar_backgroundArchived));
            arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Arrow2", Theme.key_avatar_backgroundArchived));
        }
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box2", Theme.key_avatar_text));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveAvatarDrawable}, "Box1", Theme.key_avatar_text));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_pinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Arrow", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unpinArchiveDrawable}, "Line", Theme.key_chats_archiveIcon));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Arrow", Theme.key_chats_archiveBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_archiveDrawable}, "Box1", Theme.key_chats_archiveIcon));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow1", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Arrow2", Theme.key_chats_archivePinBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box2", Theme.key_chats_archiveIcon));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{DialogCell.class}, new RLottieDrawable[]{Theme.dialogs_unarchiveDrawable}, "Box1", Theme.key_chats_archiveIcon));

        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_chats_menuBackground));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuName));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuPhone));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuPhoneCats));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuCloudBackgroundCats));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chat_serviceBackground));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuTopShadow));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerProfileCell.class}, null, null, null, Theme.key_chats_menuTopShadowCats));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{DrawerProfileCell.class}, null, null, cellDelegate, Theme.key_chats_menuTopBackgroundCats));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{DrawerProfileCell.class}, null, null, cellDelegate, Theme.key_chats_menuTopBackground));

        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{DrawerActionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemIcon));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerActionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemText));

        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemText));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DrawerUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_chats_unreadCounterText));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DrawerUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{DrawerUserCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_chats_menuBackground));
        arrayList.add(new ThemeDescription(sideMenu, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{DrawerAddCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemIcon));
        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DrawerAddCell.class}, new String[]{"textView"}, null, null, null, Theme.key_chats_menuItemText));

        arrayList.add(new ThemeDescription(sideMenu, 0, new Class[]{DividerCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_offlinePaint, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ProfileSearchCell.class}, Theme.dialogs_onlinePaint, null, null, Theme.key_windowBackgroundWhiteBlueText3));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{HashtagSearchCell.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(progressView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        arrayList.add(new ThemeDescription(dialogsAdapter != null ? dialogsAdapter.getArchiveHintCellPager() : null, 0, new Class[]{ArchiveHintInnerCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(dialogsAdapter != null ? dialogsAdapter.getArchiveHintCellPager() : null, 0, new Class[]{ArchiveHintInnerCell.class}, new String[]{"imageView2"}, null, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(dialogsAdapter != null ? dialogsAdapter.getArchiveHintCellPager() : null, 0, new Class[]{ArchiveHintInnerCell.class}, new String[]{"headerTextView"}, null, null, null, Theme.key_chats_nameMessage_threeLines));
        arrayList.add(new ThemeDescription(dialogsAdapter != null ? dialogsAdapter.getArchiveHintCellPager() : null, 0, new Class[]{ArchiveHintInnerCell.class}, new String[]{"messageTextView"}, null, null, null, Theme.key_chats_message));
        arrayList.add(new ThemeDescription(dialogsAdapter != null ? dialogsAdapter.getArchiveHintCellPager() : null, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefaultArchived));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, Theme.dialogs_countPaint, null, null, Theme.key_chats_unreadCounter));
        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, Theme.dialogs_countGrayPaint, null, null, Theme.key_chats_unreadCounterMuted));
        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, Theme.dialogs_countTextPaint, null, null, Theme.key_chats_unreadCounterText));
        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, Theme.dialogs_archiveTextPaint, null, null, Theme.key_chats_archiveText));
        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(dialogsSearchAdapter != null ? dialogsSearchAdapter.getInnerListView() : null, 0, new Class[]{HintDialogCell.class}, null, null, null, Theme.key_chats_onlineCircle));

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_FASTSCROLL, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerPerformer));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_returnToCallBackground));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_returnToCallText));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));

        for (int a = 0; a < undoView.length; a++) {
            arrayList.add(new ThemeDescription(undoView[a], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"subinfoTextView"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info1", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "info2", Theme.key_undo_background));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc12", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc11", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc10", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc9", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc8", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc7", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc6", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc5", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc4", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc3", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc2", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "luc1", Theme.key_undo_infoColor));
            arrayList.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Oval", Theme.key_undo_infoColor));
        }

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBackgroundGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlack));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextLink));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLinkSelection));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue3));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextBlue4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextRed));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextRed2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray2));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray3));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextGray4));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRedIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogTextHint));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogInputField));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogInputFieldActivated));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareCheck));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareUnchecked));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogCheckboxSquareDisabled));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRadioBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRadioBackgroundChecked));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogProgressCircle));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogButtonSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogScrollGlow));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRoundCheckBox));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogRoundCheckBoxCheck));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBadgeBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogBadgeText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLineProgress));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogLineProgressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogGrayLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_inlineProgressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_inlineProgress));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchHint));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogSearchText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogFloatingButton));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogFloatingIcon));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialogShadowLine));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_sheet_scrollUp));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_sheet_other));

        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBar));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarSelector));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarTitle));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarTop));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarSubtitle));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_actionBarItems));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_background));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_time));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progressBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progressCachedBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_progress));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_placeholder));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_placeholderBackground));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_button));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_player_buttonActive));

        return arrayList.toArray(new ThemeDescription[0]);
    }
}

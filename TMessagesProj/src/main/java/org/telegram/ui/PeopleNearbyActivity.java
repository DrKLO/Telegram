/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareLocationDrawable;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;

public class PeopleNearbyActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, LocationController.LocationFetchCallback {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;
    private ActionIntroActivity groupCreateActivity;
    private UndoView undoView;
    private LinearLayoutManager layoutManager;

    private View actionBarBackground;
    private AnimatorSet actionBarAnimator;

    private String currentGroupCreateAddress;
    private String currentGroupCreateDisplayAddress;
    private Location currentGroupCreateLocation;

    private boolean checkingCanCreate;
    private boolean canCreateGroup;
    private AlertDialog loadingDialog;

    private boolean expanded;

    private Runnable checkExpiredRunnable;
    private int reqId;

    private Location lastLoadedLocation;
    private long lastLoadedLocationTime;

    private boolean showingLoadingProgress;
    private boolean firstLoaded;
    private Runnable showProgressRunnable;
    private AnimatorSet showProgressAnimation;
    private ArrayList<View> animatingViews = new ArrayList<>();

    private final static int SHORT_POLL_TIMEOUT = 25 * 1000;

    private Runnable shortPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (shortPollRunnable != null) {
                sendRequest(true, 0);
                AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                AndroidUtilities.runOnUIThread(shortPollRunnable, SHORT_POLL_TIMEOUT);
            }
        }
    };

    private ArrayList<TLRPC.TL_peerLocated> users;
    private ArrayList<TLRPC.TL_peerLocated> chats;

    private int currentChatId;
    private boolean showingMe;

    private int helpRow;
    private int helpSectionRow;
    private int usersHeaderRow;
    private int showMeRow;
    private int usersStartRow;
    private int usersEndRow;
    private int showMoreRow;
    private int usersSectionRow;
    private int chatsHeaderRow;
    private int chatsStartRow;
    private int chatsEndRow;
    private int chatsCreateRow;
    private int chatsSectionRow;
    private int rowCount;
    private DefaultItemAnimator itemAnimator;

    public PeopleNearbyActivity() {
        super();
        users = new ArrayList<>(getLocationController().getCachedNearbyUsers());
        chats = new ArrayList<>(getLocationController().getCachedNearbyChats());
        checkForExpiredLocations(false);
        updateRows(null);
    }

    private void updateRows(DiffCallback diffCallback) {
        rowCount = 0;
        usersStartRow = -1;
        usersEndRow = -1;
        showMoreRow = -1;
        chatsStartRow = -1;
        chatsEndRow = -1;
        chatsCreateRow = -1;
        showMeRow = -1;

        helpRow = rowCount++;
        helpSectionRow = rowCount++;
        usersHeaderRow = rowCount++;
        showMeRow = rowCount++;
        if (!users.isEmpty()) {
            int count;
            if (expanded) {
                count = users.size();
            } else {
                count = Math.min(5, users.size());
            }
            usersStartRow = rowCount;
            rowCount += count;
            usersEndRow = rowCount;
            if (count != users.size()) {
                showMoreRow = rowCount++;
            }
        }
        usersSectionRow = rowCount++;

        chatsHeaderRow = rowCount++;
        chatsCreateRow = rowCount++;
        if (!chats.isEmpty()) {
            chatsStartRow = rowCount;
            rowCount += chats.size();
            chatsEndRow = rowCount;
        }
        chatsSectionRow = rowCount++;

        if (listViewAdapter != null) {
            if (diffCallback == null) {
                listView.setItemAnimator(null);
                listViewAdapter.notifyDataSetChanged();
            } else {
                listView.setItemAnimator(itemAnimator);
                diffCallback.fillPositions(diffCallback.newPositionToItem);
                DiffUtil.calculateDiff(diffCallback).dispatchUpdatesTo(listViewAdapter);
            }
        }
    }

    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;

        SparseIntArray oldPositionToItem = new SparseIntArray();
        SparseIntArray newPositionToItem = new SparseIntArray();

        int oldUsersStartRow;
        int oldUsersEndRow;

        int oldChatsStartRow;
        int oldChatsEndRow;

        private final ArrayList<TLRPC.TL_peerLocated> oldUsers = new ArrayList<>();
        private final ArrayList<TLRPC.TL_peerLocated> oldChats = new ArrayList<>();

        @Override
        public int getOldListSize() {
            return oldRowCount;
        }

        @Override
        public int getNewListSize() {
            return rowCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (newItemPosition >= usersStartRow && newItemPosition < usersEndRow && oldItemPosition >= oldUsersStartRow && oldItemPosition < oldUsersEndRow) {
                return MessageObject.getPeerId(oldUsers.get(oldItemPosition - oldUsersStartRow).peer) == MessageObject.getPeerId(users.get(newItemPosition - usersStartRow).peer);
            }
            if (newItemPosition >= chatsStartRow && newItemPosition < chatsEndRow && oldItemPosition >= oldChatsStartRow && oldItemPosition < oldChatsEndRow) {
                return MessageObject.getPeerId(oldChats.get(oldItemPosition - oldChatsStartRow).peer) == MessageObject.getPeerId(chats.get(newItemPosition - chatsStartRow).peer);
            }
            int oldIndex = oldPositionToItem.get(oldItemPosition, -1);
            int newIndex = newPositionToItem.get(newItemPosition, -1);
            return oldIndex == newIndex && oldIndex >= 0;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }

        public void fillPositions(SparseIntArray sparseIntArray) {
            sparseIntArray.clear();
            int pointer = 0;

            put(++pointer, helpRow, sparseIntArray);
            put(++pointer, helpSectionRow, sparseIntArray);
            put(++pointer, usersHeaderRow, sparseIntArray);
            put(++pointer, showMoreRow, sparseIntArray);
            put(++pointer, usersSectionRow, sparseIntArray);
            put(++pointer, chatsHeaderRow, sparseIntArray);
            put(++pointer, chatsCreateRow, sparseIntArray);
            put(++pointer, chatsSectionRow, sparseIntArray);
            put(++pointer, showMeRow, sparseIntArray);
        }

        public void saveCurrentState() {
            this.oldRowCount = rowCount;
            this.oldUsersStartRow = usersStartRow;
            this.oldUsersEndRow = usersEndRow;
            this.oldChatsStartRow = chatsStartRow;
            this.oldChatsEndRow = chatsEndRow;
            oldUsers.addAll(users);
            oldChats.addAll(chats);
            fillPositions(oldPositionToItem);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.newLocationAvailable);
        getNotificationCenter().addObserver(this, NotificationCenter.newPeopleNearbyAvailable);
        getNotificationCenter().addObserver(this, NotificationCenter.needDeleteDialog);
        checkCanCreateGroup();
        sendRequest(false, 0);
        AndroidUtilities.runOnUIThread(shortPollRunnable, SHORT_POLL_TIMEOUT);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.newLocationAvailable);
        getNotificationCenter().removeObserver(this, NotificationCenter.newPeopleNearbyAvailable);
        getNotificationCenter().removeObserver(this, NotificationCenter.needDeleteDialog);
        if (shortPollRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
            shortPollRunnable = null;
        }
        if (checkExpiredRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkExpiredRunnable);
            checkExpiredRunnable = null;
        }
        if (showProgressRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showProgressRunnable);
            showProgressRunnable = null;
        }
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setBackgroundDrawable(null);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21 && !AndroidUtilities.isTablet());
        actionBar.setTitle(LocaleController.getString("PeopleNearby", R.string.PeopleNearby));
        actionBar.getTitleTextView().setAlpha(0.0f);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) actionBarBackground.getLayoutParams();
                layoutParams.height = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + AndroidUtilities.dp(3);

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkScroll(false);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setGlowColor(0);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        itemAnimator = new DefaultItemAnimator() {
            @Override
            protected long getAddAnimationDelay(long removeDuration, long moveDuration, long changeDuration) {
                return removeDuration;
            }
        };
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position >= usersStartRow && position < usersEndRow) {
                if (view instanceof ManageChatUserCell) {
                    ManageChatUserCell cell = (ManageChatUserCell) view;
                    TLRPC.TL_peerLocated peerLocated = users.get(position - usersStartRow);
                    Bundle args1 = new Bundle();
                    args1.putLong("user_id", peerLocated.peer.user_id);
                    if (cell.hasAvatarSet()) {
                        args1.putBoolean("expandPhoto", true);
                    }
                    args1.putInt("nearby_distance", peerLocated.distance);
                    MessagesController.getInstance(currentAccount).ensureMessagesLoaded(peerLocated.peer.user_id, 0, null);
                    presentFragment(new ProfileActivity(args1));
                }
            } else if (position >= chatsStartRow && position < chatsEndRow) {
                TLRPC.TL_peerLocated peerLocated = chats.get(position - chatsStartRow);
                Bundle args1 = new Bundle();
                long chatId;
                if (peerLocated.peer instanceof TLRPC.TL_peerChat) {
                    chatId = peerLocated.peer.chat_id;
                } else {
                    chatId = peerLocated.peer.channel_id;
                }
                args1.putLong("chat_id", chatId);
                ChatActivity chatActivity = new ChatActivity(args1);
                presentFragment(chatActivity);
            } else if (position == chatsCreateRow) {
                if (checkingCanCreate || currentGroupCreateAddress == null) {
                    loadingDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                    loadingDialog.setOnCancelListener(dialog -> loadingDialog = null);
                    loadingDialog.show();
                    return;
                }
                openGroupCreate();
            } else if (position == showMeRow) {
                UserConfig userConfig = getUserConfig();
                if (showingMe) {
                    userConfig.sharingMyLocationUntil = 0;
                    userConfig.saveConfig(false);
                    sendRequest(false, 2);
                    updateRows(null);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("MakeMyselfVisibleTitle", R.string.MakeMyselfVisibleTitle));
                    builder.setMessage(LocaleController.getString("MakeMyselfVisibleInfo", R.string.MakeMyselfVisibleInfo));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
                        userConfig.sharingMyLocationUntil = 0x7fffffff;
                        userConfig.saveConfig(false);
                        sendRequest(false, 1);
                        updateRows(null);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
                userConfig.saveConfig(false);
            } else if (position == showMoreRow) {
                expanded = true;
                DiffCallback diffCallback = new DiffCallback();
                diffCallback.saveCurrentState();
                updateRows(diffCallback);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkScroll(true);
            }
        });

        actionBarBackground = new View(context) {

            private Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                int h = getMeasuredHeight() - AndroidUtilities.dp(3);
                canvas.drawRect(0, 0, getMeasuredWidth(), h, paint);
                parentLayout.drawHeaderShadow(canvas, h);
            }
        };
        actionBarBackground.setAlpha(0.0f);
        frameLayout.addView(actionBarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        undoView = new UndoView(context);
        frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        updateRows(null);
        return fragmentView;
    }

    private int[] location = new int[2];
    private void checkScroll(boolean animated) {
        int first = layoutManager.findFirstVisibleItemPosition();
        boolean show;
        if (first != 0) {
            show = true;
        } else {
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(first);
            if (holder == null) {
                show = true;
            } else {
                HintInnerCell hintInnerCell = (HintInnerCell) holder.itemView;
                hintInnerCell.titleTextView.getLocationOnScreen(location);
                show = location[1] + hintInnerCell.titleTextView.getMeasuredHeight() < actionBar.getBottom();
            }
        }
        boolean visible = actionBarBackground.getTag() == null;
        if (show != visible) {
            actionBarBackground.setTag(show ? null : 1);
            if (actionBarAnimator != null) {
                actionBarAnimator.cancel();
                actionBarAnimator = null;
            }
            if (animated) {
                actionBarAnimator = new AnimatorSet();
                actionBarAnimator.playTogether(
                        ObjectAnimator.ofFloat(actionBarBackground, View.ALPHA, show ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(actionBar.getTitleTextView(), View.ALPHA, show ? 1.0f : 0.0f)
                );
                actionBarAnimator.setDuration(150);
                actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(actionBarAnimator)) {
                            actionBarAnimator = null;
                        }
                    }
                });
                actionBarAnimator.start();
            } else {
                actionBarBackground.setAlpha(show ? 1.0f : 0.0f);
                actionBar.getTitleTextView().setAlpha(show ? 1.0f : 0.0f);
            }
        }
    }

    private void openGroupCreate() {
        if (!canCreateGroup) {
            AlertsCreator.showSimpleAlert(PeopleNearbyActivity.this, LocaleController.getString("YourLocatedChannelsTooMuch", R.string.YourLocatedChannelsTooMuch));
            return;
        }
        groupCreateActivity = new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_NEARBY_GROUP_CREATE);
        groupCreateActivity.setGroupCreateAddress(currentGroupCreateAddress, currentGroupCreateDisplayAddress, currentGroupCreateLocation);
        presentFragment(groupCreateActivity);
    }

    private void checkCanCreateGroup() {
        if (checkingCanCreate) {
            return;
        }
        checkingCanCreate = true;
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        req.by_location = true;
        req.check_limit = true;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            canCreateGroup = error == null;
            checkingCanCreate = false;
            if (loadingDialog != null && currentGroupCreateAddress != null) {
                try {
                    loadingDialog.dismiss();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                loadingDialog = null;
                openGroupCreate();
            }
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void showLoadingProgress(boolean show) {
        if (showingLoadingProgress == show) {
            return;
        }
        showingLoadingProgress = show;
        if (showProgressAnimation != null) {
            showProgressAnimation.cancel();
            showProgressAnimation = null;
        }
        if (listView == null) {
            return;
        }
        ArrayList<Animator> animators = new ArrayList<>();
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof HeaderCellProgress) {
                HeaderCellProgress cell = (HeaderCellProgress) child;
                animatingViews.add(cell);
                animators.add(ObjectAnimator.ofFloat(cell.progressView, View.ALPHA, show ? 1.0f : 0.0f));
            }
        }
        if (animators.isEmpty()) {
            return;
        }
        showProgressAnimation = new AnimatorSet();
        showProgressAnimation.playTogether(animators);
        showProgressAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showProgressAnimation = null;
                animatingViews.clear();
            }
        });
        showProgressAnimation.setDuration(180);
        showProgressAnimation.start();
    }

    private void sendRequest(boolean shortpoll, int share) {
        if (!firstLoaded) {
            AndroidUtilities.runOnUIThread(showProgressRunnable = () -> {
                showLoadingProgress(true);
                showProgressRunnable = null;
            }, 1000);
            firstLoaded = true;
        }
        Location location = getLocationController().getLastKnownLocation();
        if (location == null) {
            return;
        }
        currentGroupCreateLocation = location;
        if (!shortpoll && lastLoadedLocation != null) {
            float distance = lastLoadedLocation.distanceTo(location);
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("located distance = " + distance);
            }
            if (share != 0 || (SystemClock.elapsedRealtime() - lastLoadedLocationTime) >= 3000L && lastLoadedLocation.distanceTo(location) > 20) {
                if (reqId != 0) {
                    getConnectionsManager().cancelRequest(reqId, true);
                    reqId = 0;
                }
            } else {
                return;
            }
        }
        if (reqId != 0) {
            return;
        }
        lastLoadedLocation = location;
        lastLoadedLocationTime = SystemClock.elapsedRealtime();
        LocationController.fetchLocationAddress(currentGroupCreateLocation, PeopleNearbyActivity.this);
        TLRPC.TL_contacts_getLocated req = new TLRPC.TL_contacts_getLocated();
        req.geo_point = new TLRPC.TL_inputGeoPoint();
        req.geo_point.lat = location.getLatitude();
        req.geo_point._long = location.getLongitude();
        if (share != 0) {
            req.flags |= 1;
            req.self_expires = share == 1 ? 0x7fffffff : 0;
        }
        reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            reqId = 0;
            if (showProgressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(showProgressRunnable);
                showProgressRunnable = null;
            }
            showLoadingProgress(false);
            boolean saveConfig = false;
            UserConfig userConfig = getUserConfig();
            if (share == 1 && error != null) {
                userConfig.sharingMyLocationUntil = 0;
                saveConfig = true;
                updateRows(null);
            }
            if (response != null && share != 2) {
                TLRPC.Updates updates = (TLRPC.TL_updates) response;
                getMessagesController().putUsers(updates.users, false);
                getMessagesController().putChats(updates.chats, false);

                DiffCallback diffCallback = new DiffCallback();
                diffCallback.saveCurrentState();

                users.clear();
                chats.clear();
                if (userConfig.sharingMyLocationUntil != 0) {
                    userConfig.lastMyLocationShareTime = (int) (System.currentTimeMillis() / 1000);
                    saveConfig = true;
                }
                boolean hasSelf = false;
                for (int a = 0, N = updates.updates.size(); a < N; a++) {
                    TLRPC.Update baseUpdate = updates.updates.get(a);
                    if (baseUpdate instanceof TLRPC.TL_updatePeerLocated) {
                        TLRPC.TL_updatePeerLocated update = (TLRPC.TL_updatePeerLocated) baseUpdate;
                        for (int b = 0, N2 = update.peers.size(); b < N2; b++) {
                            TLRPC.PeerLocated object = update.peers.get(b);
                            if (object instanceof TLRPC.TL_peerLocated) {
                                TLRPC.TL_peerLocated peerLocated = (TLRPC.TL_peerLocated) object;
                                if (peerLocated.peer instanceof TLRPC.TL_peerUser) {
                                    users.add(peerLocated);
                                } else {
                                    chats.add(peerLocated);
                                }
                            } else if (object instanceof TLRPC.TL_peerSelfLocated) {
                                hasSelf = true;
                                TLRPC.TL_peerSelfLocated peerSelfLocated = (TLRPC.TL_peerSelfLocated) object;
                                if (userConfig.sharingMyLocationUntil != peerSelfLocated.expires) {
                                    userConfig.sharingMyLocationUntil = peerSelfLocated.expires;
                                    saveConfig = true;
                                }
                            }
                        }
                    }
                }
                if (!hasSelf && userConfig.sharingMyLocationUntil != 0) {
                    userConfig.sharingMyLocationUntil = 0;
                    saveConfig = true;
                }

                checkForExpiredLocations(true);
                updateRows(diffCallback);
            }
            if (saveConfig) {
                userConfig.saveConfig(false);
            }
            if (shortPollRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                AndroidUtilities.runOnUIThread(shortPollRunnable, SHORT_POLL_TIMEOUT);
            }
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        getLocationController().startLocationLookupForPeopleNearby(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        getLocationController().startLocationLookupForPeopleNearby(true);
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    @Override
    public void onLocationAddressAvailable(String address, String displayAddress, Location location) {
        currentGroupCreateAddress = address;
        currentGroupCreateDisplayAddress = displayAddress;
        currentGroupCreateLocation = location;
        if (groupCreateActivity != null) {
            groupCreateActivity.setGroupCreateAddress(currentGroupCreateAddress, currentGroupCreateDisplayAddress, currentGroupCreateLocation);
        }
        if (loadingDialog != null && !checkingCanCreate) {
            try {
                loadingDialog.dismiss();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            loadingDialog = null;
            openGroupCreate();
        }
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        groupCreateActivity = null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newLocationAvailable) {
            sendRequest(false, 0);
        } else if (id == NotificationCenter.newPeopleNearbyAvailable) {
            TLRPC.TL_updatePeerLocated update = (TLRPC.TL_updatePeerLocated) args[0];
            DiffCallback diffCallback = new DiffCallback();
            diffCallback.saveCurrentState();
            for (int b = 0, N2 = update.peers.size(); b < N2; b++) {
                TLRPC.PeerLocated object = update.peers.get(b);
                if (object instanceof TLRPC.TL_peerLocated) {
                    TLRPC.TL_peerLocated peerLocated = (TLRPC.TL_peerLocated) object;
                    boolean found = false;
                    ArrayList<TLRPC.TL_peerLocated> arrayList;
                    if (peerLocated.peer instanceof TLRPC.TL_peerUser) {
                        arrayList = users;
                    } else {
                        arrayList = chats;
                    }
                    for (int a = 0, N = arrayList.size(); a < N; a++) {
                        TLRPC.TL_peerLocated old = arrayList.get(a);
                        if (old.peer.user_id != 0 && old.peer.user_id == peerLocated.peer.user_id || old.peer.chat_id != 0 && old.peer.chat_id == peerLocated.peer.chat_id || old.peer.channel_id != 0 && old.peer.channel_id == peerLocated.peer.channel_id) {
                            arrayList.set(a, peerLocated);
                            found = true;
                        }
                    }
                    if (!found) {
                        arrayList.add(peerLocated);
                    }
                }
            }
            checkForExpiredLocations(true);
            updateRows(diffCallback);
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
                        getMessagesController().deleteParticipantFromChat(-dialogId, getMessagesController().getUser(getUserConfig().getClientUserId()), null, revoke, revoke);
                    }
                } else {
                    getMessagesController().deleteDialog(dialogId, 0, revoke);
                }
            };
            if (undoView != null) {
                undoView.showWithAction(dialogId, UndoView.ACTION_DELETE, deleteRunnable);
            } else {
                deleteRunnable.run();
            }
        }
    }

    private void checkForExpiredLocations(boolean cache) {
        if (checkExpiredRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkExpiredRunnable);
            checkExpiredRunnable = null;
        }
        int currentTime = getConnectionsManager().getCurrentTime();
        int minExpired = Integer.MAX_VALUE;
        boolean changed = false;
        DiffCallback callback = null;
        for (int a = 0; a < 2; a++) {
            ArrayList<TLRPC.TL_peerLocated> arrayList = a == 0 ? users : chats;
            for (int b = 0, N = arrayList.size(); b < N; b++) {
                TLRPC.TL_peerLocated peer = arrayList.get(b);
                if (peer.expires <= currentTime) {
                    if (callback == null) {
                        callback = new DiffCallback();
                        callback.saveCurrentState();
                    }
                    arrayList.remove(b);
                    b--;
                    N--;
                    changed = true;
                } else {
                    minExpired = Math.min(minExpired, peer.expires);
                }
            }
        }
        if (changed && listViewAdapter != null) {
            updateRows(callback);
        }
        if (changed || cache) {
            getLocationController().setCachedNearbyUsersAndChats(users, chats);
        }
        if (minExpired != Integer.MAX_VALUE) {
            AndroidUtilities.runOnUIThread(checkExpiredRunnable = () -> {
                checkExpiredRunnable = null;
                checkForExpiredLocations(false);
            }, (minExpired - currentTime) * 1000);
        }
    }

    public static class HeaderCellProgress extends HeaderCell {

        private RadialProgressView progressView;

        public HeaderCellProgress(Context context) {
            super(context);

            setClipChildren(false);

            progressView = new RadialProgressView(context);
            progressView.setSize(AndroidUtilities.dp(14));
            progressView.setStrokeWidth(2);
            progressView.setAlpha(0.0f);
            progressView.setProgressColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            addView(progressView, LayoutHelper.createFrame(50, 40, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 2 : 0, 3, LocaleController.isRTL ? 0 : 2, 0));
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    public class HintInnerCell extends FrameLayout {

        private ImageView imageView;
        private TextView titleTextView;
        private TextView messageTextView;

        public HintInnerCell(Context context) {
            super(context);

            int top = (int) ((ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)) / AndroidUtilities.density) - 44;

            imageView = new ImageView(context);
            imageView.setBackgroundDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(74), Theme.getColor(Theme.key_chats_archiveBackground)));
            imageView.setImageDrawable(new ShareLocationDrawable(context, 2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(74, 74, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, top + 27, 0, 0));

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
            titleTextView.setGravity(Gravity.CENTER);
            titleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("PeopleNearby", R.string.PeopleNearby)));
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 17, top + 120, 17, 27));

            messageTextView = new TextView(context);
            messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setGravity(Gravity.CENTER);
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("PeopleNearbyInfo2", R.string.PeopleNearbyInfo2)));
            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 40, top + 161, 40, 27));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, 6, 2, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new HeaderCellProgress(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    TextView textView = new TextView(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(67), MeasureSpec.EXACTLY));
                        }
                    };
                    textView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    textView.setPadding(0, 0, AndroidUtilities.dp(3), 0);
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    textView.setGravity(Gravity.CENTER);
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    view = textView;
                    break;
                case 5:
                default:
                    view = new HintInnerCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 3 && !animatingViews.contains(holder.itemView)) {
                HeaderCellProgress cell = (HeaderCellProgress) holder.itemView;
                cell.progressView.setAlpha(showingLoadingProgress ? 1.0f : 0.0f);
            }
        }

        private String formatDistance(TLRPC.TL_peerLocated located) {
            return LocaleController.formatDistance(located.distance, 0);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    if (position >= usersStartRow && position < usersEndRow) {
                        int index = position - usersStartRow;
                        TLRPC.TL_peerLocated peerLocated = users.get(index);
                        TLRPC.User user = getMessagesController().getUser(peerLocated.peer.user_id);
                        if (user != null) {
                            userCell.setData(user, null, formatDistance(peerLocated), showMoreRow != -1 || position != usersEndRow - 1);
                        }
                    } else if (position >= chatsStartRow && position < chatsEndRow) {
                        int index = position - chatsStartRow;
                        TLRPC.TL_peerLocated peerLocated = chats.get(index);
                        long chatId;
                        if (peerLocated.peer instanceof TLRPC.TL_peerChat) {
                            chatId = peerLocated.peer.chat_id;
                        } else {
                            chatId = peerLocated.peer.channel_id;
                        }
                        TLRPC.Chat chat = getMessagesController().getChat(chatId);
                        if (chat != null) {
                            String subtitle = formatDistance(peerLocated);
                            if (chat.participants_count != 0) {
                                subtitle = String.format("%1$s, %2$s", subtitle, LocaleController.formatPluralString("Members", chat.participants_count));
                            }
                            userCell.setData(chat, null, subtitle, index != chats.size() - 1);
                        }
                    }
                    break;
                case 1:
                    ShadowSectionCell privacyCell = (ShadowSectionCell) holder.itemView;
                    if (position == usersSectionRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == chatsSectionRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == helpSectionRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    if (position == chatsCreateRow) {
                        actionCell.setText(LocaleController.getString("NearbyCreateGroup", R.string.NearbyCreateGroup), null, R.drawable.msg_groups_create, chatsStartRow != -1);
                    } else if (position == showMeRow) {
                        if (showingMe = (getUserConfig().sharingMyLocationUntil > getConnectionsManager().getCurrentTime())) {
                            actionCell.setText(LocaleController.getString("StopShowingMe", R.string.StopShowingMe), null, R.drawable.msg_nearby_off, usersStartRow != -1);
                            actionCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        } else {
                            actionCell.setText(LocaleController.getString("MakeMyselfVisible", R.string.MakeMyselfVisible), null, R.drawable.msg_nearby, usersStartRow != -1);
                        }
                    } else if (position == showMoreRow) {
                        actionCell.setText(LocaleController.formatPluralString("ShowVotes", users.size() - 5), null, R.drawable.arrow_more, false);
                    }
                    break;
                case 3:
                    HeaderCellProgress headerCell = (HeaderCellProgress) holder.itemView;
                    if (position == usersHeaderRow) {
                        headerCell.setText(LocaleController.getString("PeopleNearbyHeader", R.string.PeopleNearbyHeader));
                    } else if (position == chatsHeaderRow) {
                        headerCell.setText(LocaleController.getString("ChatsNearbyHeader", R.string.ChatsNearbyHeader));
                    }
                    break;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == helpRow) {
                return 5;
            } else if (position == chatsCreateRow || position == showMeRow || position == showMoreRow) {
                return 2;
            } else if (position == usersHeaderRow || position == chatsHeaderRow) {
                return 3;
            } else if (position == usersSectionRow || position == chatsSectionRow || position == helpSectionRow) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ManageChatUserCell) {
                        ((ManageChatUserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class, HeaderCell.class, TextView.class, HintInnerCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBarBackground, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{HeaderCellProgress.class}, new String[]{"progressView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{HintInnerCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chats_archiveBackground));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HintInnerCell.class}, new String[]{"messageTextView"}, null, null, null, Theme.key_chats_message));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_text_RedRegular));

        themeDescriptions.add(new ThemeDescription(undoView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"subinfoTextView"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
        themeDescriptions.add(new ThemeDescription(undoView, 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));

        return themeDescriptions;
    }
}

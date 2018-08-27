/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

@SuppressWarnings("unchecked")
public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private class MediaPage extends FrameLayout {
        private RecyclerListView listView;
        private LinearLayout progressView;
        private TextView emptyTextView;
        private LinearLayoutManager layoutManager;
        private ImageView emptyImageView;
        private LinearLayout emptyView;
        private RadialProgressView progressBar;
        private int selectedType;

        public MediaPage(Context context) {
            super(context);
        }
    }

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private SharedDocumentsAdapter voiceAdapter;
    private SharedDocumentsAdapter audioAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter audioSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private MediaPage[] mediaPages = new MediaPage[2];
    private ActionBarMenuItem searchItem;
    private int searchItemState;
    private boolean ignoreSearchCollapse;
    private NumberTextView selectedMessagesCountTextView;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ArrayList<SharedPhotoVideoCell> cellCache = new ArrayList<>(6);
    private ArrayList<SharedPhotoVideoCell> cache = new ArrayList<>(6);
    private FragmentContextView fragmentContextView;
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;

    private boolean searchWas;
    private boolean searching;
    private boolean animatingForward;

    private int[] hasMedia;

    private SparseArray<MessageObject>[] selectedFiles = new SparseArray[] {new SparseArray<>(), new SparseArray<>()};
    private int cantDeleteMessagesCount;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private ActionBarMenuItem gotoItem;
    private boolean scrolling;
    private long mergeDialogId;
    protected TLRPC.ChatFull info = null;

    private boolean tabsAnimationInProgress;

    private long dialog_id;
    private int columnsCount = 4;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            if (messageObject == null || mediaPages[0].selectedType != 0) {
                return null;
            }
            int count = mediaPages[0].listView.getChildCount();

            for (int a = 0; a < count; a++) {
                View view = mediaPages[0].listView.getChildAt(a);
                if (view instanceof SharedPhotoVideoCell) {
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    for (int i = 0; i < 6; i++) {
                        MessageObject message = cell.getMessageObject(i);
                        if (message == null) {
                            break;
                        }
                        BackupImageView imageView = cell.getImageView(i);
                        if (message.getId() == messageObject.getId()) {
                            int coords[] = new int[2];
                            imageView.getLocationInWindow(coords);
                            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                            object.viewX = coords[0];
                            object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                            object.parentView = mediaPages[0].listView;
                            object.imageReceiver = imageView.getImageReceiver();
                            object.thumb = object.imageReceiver.getBitmapSafe();
                            object.parentView.getLocationInWindow(coords);
                            object.clipTopAddition = AndroidUtilities.dp(40);
                            return object;
                        }
                    }
                }
            }
            return null;
        }
    };

    private class SharedMediaData {
        private ArrayList<MessageObject> messages = new ArrayList<>();
        private SparseArray<MessageObject>[] messagesDict = new SparseArray[] {new SparseArray<>(), new SparseArray<>()};
        private ArrayList<String> sections = new ArrayList<>();
        private HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
        private int totalCount;
        private boolean loading;
        private boolean endReached[] = new boolean[] {false, true};
        private int max_id[] = new int[] {0, 0};

        public boolean addMessage(MessageObject messageObject, boolean isNew, boolean enc) {
            int loadIndex = messageObject.getDialogId() == dialog_id ? 0 : 1;
            if (messagesDict[loadIndex].indexOfKey(messageObject.getId()) >= 0) {
                return false;
            }
            ArrayList<MessageObject> messageObjects = sectionArrays.get(messageObject.monthKey);
            if (messageObjects == null) {
                messageObjects = new ArrayList<>();
                sectionArrays.put(messageObject.monthKey, messageObjects);
                if (isNew) {
                    sections.add(0, messageObject.monthKey);
                } else {
                    sections.add(messageObject.monthKey);
                }
            }
            if (isNew) {
                messageObjects.add(0, messageObject);
                messages.add(0, messageObject);
            } else {
                messageObjects.add(messageObject);
                messages.add(messageObject);
            }
            messagesDict[loadIndex].put(messageObject.getId(), messageObject);
            if (!enc) {
                if (messageObject.getId() > 0) {
                    max_id[loadIndex] = Math.min(messageObject.getId(), max_id[loadIndex]);
                }
            } else {
                max_id[loadIndex] = Math.max(messageObject.getId(), max_id[loadIndex]);
            }
            return true;
        }

        public boolean deleteMessage(int mid, int loadIndex) {
            MessageObject messageObject = messagesDict[loadIndex].get(mid);
            if (messageObject == null) {
                return false;
            }
            ArrayList<MessageObject> messageObjects = sectionArrays.get(messageObject.monthKey);
            if (messageObjects == null) {
                return false;
            }
            messageObjects.remove(messageObject);
            messages.remove(messageObject);
            messagesDict[loadIndex].remove(messageObject.getId());
            if (messageObjects.isEmpty()) {
                sectionArrays.remove(messageObject.monthKey);
                sections.remove(messageObject.monthKey);
            }
            totalCount--;
            return true;
        }

        public void replaceMid(int oldMid, int newMid) {
            MessageObject obj = messagesDict[0].get(oldMid);
            if (obj != null) {
                messagesDict[0].remove(oldMid);
                messagesDict[0].put(newMid, obj);
                obj.messageOwner.id = newMid;
            }
        }
    }

    private SharedMediaData sharedMediaData[] = new SharedMediaData[5];

    private final static int forward = 3;
    private final static int delete = 4;
    private final static int gotochat = 7;


    public MediaActivity(Bundle args, int[] media) {
        super(args);
        hasMedia = media;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStarted);
        dialog_id = getArguments().getLong("dialog_id", 0);
        for (int a = 0; a < sharedMediaData.length; a++) {
            sharedMediaData[a] = new SharedMediaData();
            sharedMediaData[a].max_id[0] = ((int)dialog_id) == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            if (mergeDialogId != 0 && info != null) {
                sharedMediaData[a].max_id[1] = info.migrated_from_max_id;
                sharedMediaData[a].endReached[1] = false;
            }
        }
        sharedMediaData[0].loading = true;
        DataQuery.getInstance(currentAccount).loadMedia(dialog_id, 50, 0, DataQuery.MEDIA_PHOTOVIDEO, true, classGuid);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStarted);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(LocaleController.getString("SharedMedia", R.string.SharedMedia));
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        for (int a = 1; a >= 0; a--) {
                            selectedFiles[a].clear();
                        }
                        cantDeleteMessagesCount = 0;
                        actionBar.hideActionMode();
                        int count = mediaPages[0].listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = mediaPages[0].listView.getChildAt(a);
                            if (child instanceof SharedDocumentCell) {
                                ((SharedDocumentCell) child).setChecked(false, true);
                            } else if (child instanceof SharedPhotoVideoCell) {
                                for (int b = 0; b < 6; b++) {
                                    ((SharedPhotoVideoCell) child).setChecked(b, false, true);
                                }
                            } else if (child instanceof SharedLinkCell) {
                                ((SharedLinkCell) child).setChecked(false, true);
                            } else if (child instanceof SharedAudioCell) {
                                ((SharedAudioCell) child).setChecked(false, true);
                            }
                        }
                    } else {
                        finishFragment();
                    }
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("items", selectedFiles[0].size() + selectedFiles[1].size())));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));

                    final boolean deleteForAll[] = new boolean[1];
                    int lower_id = (int) dialog_id;
                    if (lower_id != 0) {
                        TLRPC.Chat currentChat;
                        TLRPC.User currentUser;
                        if (lower_id > 0) {
                            currentUser = MessagesController.getInstance(currentAccount).getUser(lower_id);
                            currentChat = null;
                        } else {
                            currentUser = null;
                            currentChat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                        }
                        if (currentUser != null || !ChatObject.isChannel(currentChat)) {
                            int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            if (currentUser != null && currentUser.id != UserConfig.getInstance(currentAccount).getClientUserId() || currentChat != null) {
                                boolean hasOutgoing = false;
                                for (int a = 1; a >= 0; a--) {
                                    int channelId = 0;
                                    for (int b = 0; b < selectedFiles[a].size(); b++) {
                                        MessageObject msg = selectedFiles[a].valueAt(b);
                                        if (msg.messageOwner.action != null) {
                                            continue;
                                        }
                                        if (msg.isOut()) {
                                            if ((currentDate - msg.messageOwner.date) <= 2 * 24 * 60 * 60) {
                                                hasOutgoing = true;
                                            }
                                        } else {
                                            hasOutgoing = false;
                                            break;
                                        }
                                    }
                                    if (hasOutgoing) {
                                        break;
                                    }
                                }

                                if (hasOutgoing) {
                                    FrameLayout frameLayout = new FrameLayout(getParentActivity());
                                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1);
                                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                                    if (currentChat != null) {
                                        cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                                    } else {
                                        cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                                    }
                                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                                    cell.setOnClickListener(v -> {
                                        CheckBoxCell cell1 = (CheckBoxCell) v;
                                        deleteForAll[0] = !deleteForAll[0];
                                        cell1.setChecked(deleteForAll[0], true);
                                    });
                                    builder.setView(frameLayout);
                                }
                            }
                        }
                    }

                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                        for (int a = 1; a >= 0; a--) {
                            ArrayList<Integer> ids = new ArrayList<>();
                            for (int b = 0; b < selectedFiles[a].size(); b++) {
                                ids.add(selectedFiles[a].keyAt(b));
                            }
                            ArrayList<Long> random_ids = null;
                            TLRPC.EncryptedChat currentEncryptedChat = null;
                            int channelId = 0;
                            if (!ids.isEmpty()) {
                                MessageObject msg = selectedFiles[a].get(ids.get(0));
                                if (channelId == 0 && msg.messageOwner.to_id.channel_id != 0) {
                                    channelId = msg.messageOwner.to_id.channel_id;
                                }
                            }
                            if ((int) dialog_id == 0) {
                                currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                            }
                            if (currentEncryptedChat != null) {
                                random_ids = new ArrayList<>();
                                for (int b = 0; b < selectedFiles[a].size(); b++) {
                                    MessageObject msg = selectedFiles[a].valueAt(b);
                                    if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                        random_ids.add(msg.messageOwner.random_id);
                                    }
                                }
                            }
                            MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids, currentEncryptedChat, channelId, deleteForAll[0]);
                            selectedFiles[a].clear();
                        }
                        actionBar.hideActionMode();
                        actionBar.closeSearchField();
                        cantDeleteMessagesCount = 0;
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate((fragment1, dids, message, param) -> {
                        ArrayList<MessageObject> fmessages = new ArrayList<>();
                        for (int a = 1; a >= 0; a--) {
                            ArrayList<Integer> ids = new ArrayList<>();
                            for (int b = 0; b < selectedFiles[a].size(); b++) {
                                ids.add(selectedFiles[a].keyAt(b));
                            }
                            Collections.sort(ids);
                            for (Integer id1 : ids) {
                                if (id1 > 0) {
                                    fmessages.add(selectedFiles[a].get(id1));
                                }
                            }
                            selectedFiles[a].clear();
                        }
                        cantDeleteMessagesCount = 0;
                        actionBar.hideActionMode();

                        if (dids.size() > 1 || dids.get(0) == UserConfig.getInstance(currentAccount).getClientUserId() || message != null) {
                            for (int a = 0; a < dids.size(); a++) {
                                long did = dids.get(a);
                                if (message != null) {
                                    SendMessagesHelper.getInstance(currentAccount).sendMessage(message.toString(), did, null, null, true, null, null, null);
                                }
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(fmessages, did);
                            }
                            fragment1.finishFragment();
                        } else {
                            long did = dids.get(0);
                            int lower_part = (int) did;
                            int high_part = (int) (did >> 32);
                            Bundle args1 = new Bundle();
                            args1.putBoolean("scrollToTopOnResume", true);
                            if (lower_part != 0) {
                                if (lower_part > 0) {
                                    args1.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args1.putInt("chat_id", -lower_part);
                                }
                            } else {
                                args1.putInt("enc_id", high_part);
                            }
                            if (lower_part != 0) {
                                if (!MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, fragment1)) {
                                    return;
                                }
                            }

                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);

                            ChatActivity chatActivity = new ChatActivity(args1);
                            presentFragment(chatActivity, true);
                            chatActivity.showFieldPanelForForward(true, fmessages);

                            if (!AndroidUtilities.isTablet()) {
                                removeSelfFromStack();
                            }
                        }
                    });
                    presentFragment(fragment);
                } else if (id == gotochat) {
                    if (selectedFiles[0].size() != 1) {
                        return;
                    }
                    Bundle args = new Bundle();
                    int lower_part = (int) dialog_id;
                    int high_id = (int) (dialog_id >> 32);
                    if (lower_part != 0) {
                        if (high_id == 1) {
                            args.putInt("chat_id", lower_part);
                        } else {
                            if (lower_part > 0) {
                                args.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_part);
                                if (chat != null && chat.migrated_to != null) {
                                    args.putInt("migrated_to", lower_part);
                                    lower_part = -chat.migrated_to.channel_id;
                                }
                                args.putInt("chat_id", -lower_part);
                            }
                        }
                    } else {
                        args.putInt("enc_id", high_id);
                    }
                    args.putInt("message_id", selectedFiles[0].keyAt(0));
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                    presentFragment(new ChatActivity(args), true);
                }
            }
        });

        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        scrollSlidingTextTabStrip.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.LEFT | Gravity.BOTTOM));
        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (mediaPages[0].selectedType == id) {
                    return;
                }
                swipeBackEnabled = id == scrollSlidingTextTabStrip.getFirstTabId();
                mediaPages[1].selectedType = id;
                mediaPages[1].setVisibility(View.VISIBLE);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1 && mediaPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    mediaPages[0].setTranslationX(-progress * mediaPages[1].getMeasuredWidth());
                    mediaPages[1].setTranslationX(mediaPages[1].getMeasuredWidth() - progress * mediaPages[1].getMeasuredWidth());
                } else {
                    mediaPages[0].setTranslationX(progress * mediaPages[1].getMeasuredWidth());
                    mediaPages[1].setTranslationX(progress * mediaPages[1].getMeasuredWidth() - mediaPages[1].getMeasuredWidth());
                }
                if (searchItemState == 1) {
                    searchItem.setAlpha(progress);
                } else if (searchItemState == 2) {
                    searchItem.setAlpha(1.0f - progress);
                }
                if (progress == 1) {
                    MediaPage tempPage = mediaPages[0];
                    mediaPages[0] = mediaPages[1];
                    mediaPages[1] = tempPage;
                    mediaPages[1].setVisibility(View.INVISIBLE);
                    if (searchItemState == 2) {
                        searchItem.setVisibility(View.GONE);
                    }
                    searchItemState = 0;
                }
            }
        });

        for (int a = 1; a >= 0; a--) {
            selectedFiles[a].clear();
        }
        cantDeleteMessagesCount = 0;
        actionModeViews.clear();

        final ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                documentsSearchAdapter.search(null);
                linksSearchAdapter.search(null);
                audioSearchAdapter.search(null);
                if (ignoreSearchCollapse) {
                    ignoreSearchCollapse = false;
                    return;
                }

                switchToCurrentSelectedMode(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    switchToCurrentSelectedMode(false);
                }
                if (mediaPages[0].selectedType == 1) {
                    if (documentsSearchAdapter == null) {
                        return;
                    }
                    documentsSearchAdapter.search(text);
                } else if (mediaPages[0].selectedType == 3) {
                    if (linksSearchAdapter == null) {
                        return;
                    }
                    linksSearchAdapter.search(text);
                } else if (mediaPages[0].selectedType == 4) {
                    if (audioSearchAdapter == null) {
                        return;
                    }
                    audioSearchAdapter.search(text);
                }
            }
        });
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(View.GONE);
        searchItemState = 0;

        final ActionBarMenu actionMode = actionBar.createActionMode(false);
        actionMode.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), true);

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        if ((int) dialog_id != 0) {
            actionModeViews.add(gotoItem = actionMode.addItemWithWidth(gotochat, R.drawable.go_to_message, AndroidUtilities.dp(54)));
            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.ic_ab_forward, AndroidUtilities.dp(54)));
        }
        actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.ic_ab_delete, AndroidUtilities.dp(54)));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context);
        documentsAdapter = new SharedDocumentsAdapter(context, 1);
        voiceAdapter = new SharedDocumentsAdapter(context, 2);
        audioAdapter = new SharedDocumentsAdapter(context, 4);
        documentsSearchAdapter = new MediaSearchAdapter(context, 1);
        audioSearchAdapter = new MediaSearchAdapter(context, 4);
        linksSearchAdapter = new MediaSearchAdapter(context, 3);
        linksAdapter = new SharedLinksAdapter(context);

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context) {

            private int startedTrackingPointerId;
            private boolean startedTracking;
            private boolean maybeStartTracking;
            private int startedTrackingX;
            private int startedTrackingY;
            private VelocityTracker velocityTracker;

            private boolean prepareForMoving(MotionEvent ev, boolean forward) {
                int id = scrollSlidingTextTabStrip.getNextPageId(forward);
                if (id < 0) {
                    return false;
                }
                if (searchItemState != 0) {
                    if (searchItemState == 2) {
                        searchItem.setAlpha(1.0f);
                    } else if (searchItemState == 1) {
                        searchItem.setAlpha(0.0f);
                        searchItem.setVisibility(View.GONE);
                    }
                    searchItemState = 0;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                maybeStartTracking = false;
                startedTracking = true;
                startedTrackingX = (int) ev.getX();
                actionBar.setEnabled(false);
                scrollSlidingTextTabStrip.setEnabled(false);
                mediaPages[1].selectedType = id;
                mediaPages[1].setVisibility(View.VISIBLE);
                animatingForward = forward;
                switchToCurrentSelectedMode(true);
                if (forward) {
                    mediaPages[1].setTranslationX(mediaPages[1].getMeasuredWidth());
                } else {
                    mediaPages[1].setTranslationX(-mediaPages[1].getMeasuredWidth());
                }
                return true;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return tabsAnimationInProgress || scrollSlidingTextTabStrip.isAnimatingIndicator() || onTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!parentLayout.checkTransitionAnimation() && !tabsAnimationInProgress) {
                    if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        startedTrackingX = (int) ev.getX();
                        startedTrackingY = (int) ev.getY();
                        if (velocityTracker != null) {
                            velocityTracker.clear();
                        }
                    } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        int dx = (int) (ev.getX() - startedTrackingX);
                        int dy = Math.abs((int) ev.getY() - startedTrackingY);
                        velocityTracker.addMovement(ev);
                        if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                            if (!prepareForMoving(ev, dx < 0)) {
                                maybeStartTracking = true;
                                startedTracking = false;
                            }
                        }
                        if (maybeStartTracking && !startedTracking) {
                            float touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
                            if (Math.abs(dx) >= touchSlop && Math.abs(dx) / 3 > dy) {
                                prepareForMoving(ev, dx < 0);
                            }
                        } else if (startedTracking) {
                            if (animatingForward) {
                                mediaPages[0].setTranslationX(dx);
                                mediaPages[1].setTranslationX(mediaPages[1].getMeasuredWidth() + dx);
                            } else {
                                mediaPages[0].setTranslationX(dx);
                                mediaPages[1].setTranslationX(dx - mediaPages[1].getMeasuredWidth());
                            }
                            float scrollProgress = Math.abs(dx) / (float) mediaPages[0].getMeasuredWidth();
                            if (searchItemState == 2) {
                                searchItem.setAlpha(1.0f - scrollProgress);
                            } else if (searchItemState == 1) {
                                searchItem.setAlpha(scrollProgress);
                            }
                            scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                        }
                    } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        }
                        velocityTracker.computeCurrentVelocity(1000);
                        if (!startedTracking) {
                            float velX = velocityTracker.getXVelocity();
                            float velY = velocityTracker.getYVelocity();
                            if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                                prepareForMoving(ev, velX < 0);
                            }
                        }
                        if (startedTracking) {
                            float x = mediaPages[0].getX();
                            AnimatorSet animatorSet = new AnimatorSet();
                            float velX = velocityTracker.getXVelocity();
                            float velY = velocityTracker.getYVelocity();
                            final boolean backAnimation = Math.abs(x) < mediaPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                            float distToMove;
                            if (backAnimation) {
                                if (animatingForward) {
                                    animatorSet.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], "translationX", 0),
                                            ObjectAnimator.ofFloat(mediaPages[1], "translationX", mediaPages[1].getMeasuredWidth())
                                    );
                                } else {
                                    animatorSet.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], "translationX", 0),
                                            ObjectAnimator.ofFloat(mediaPages[1], "translationX", -mediaPages[1].getMeasuredWidth())
                                    );
                                }
                            } else {
                                if (animatingForward) {
                                    animatorSet.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], "translationX", -mediaPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(mediaPages[1], "translationX", 0)
                                    );
                                } else {
                                    animatorSet.playTogether(
                                            ObjectAnimator.ofFloat(mediaPages[0], "translationX", mediaPages[0].getMeasuredWidth()),
                                            ObjectAnimator.ofFloat(mediaPages[1], "translationX", 0)
                                    );
                                }
                            }
                            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                            animatorSet.setDuration(200);
                            animatorSet.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    if (backAnimation) {
                                        mediaPages[1].setVisibility(View.INVISIBLE);
                                        if (searchItemState == 2) {
                                            searchItem.setAlpha(1.0f);
                                        } else if (searchItemState == 1) {
                                            searchItem.setAlpha(0.0f);
                                            searchItem.setVisibility(View.GONE);
                                        }
                                        searchItemState = 0;
                                    } else {
                                        MediaPage tempPage = mediaPages[0];
                                        mediaPages[0] = mediaPages[1];
                                        mediaPages[1] = tempPage;
                                        mediaPages[1].setVisibility(View.INVISIBLE);
                                        if (searchItemState == 2) {
                                            searchItem.setVisibility(View.GONE);
                                        }
                                        searchItemState = 0;
                                        swipeBackEnabled = mediaPages[0].selectedType == scrollSlidingTextTabStrip.getFirstTabId();
                                        scrollSlidingTextTabStrip.selectTabWithId(mediaPages[0].selectedType, 1.0f);
                                    }
                                    tabsAnimationInProgress = false;
                                    maybeStartTracking = false;
                                    startedTracking = false;
                                    actionBar.setEnabled(true);
                                    scrollSlidingTextTabStrip.setEnabled(true);
                                }
                            });
                            animatorSet.start();
                            tabsAnimationInProgress = true;
                        } else {
                            maybeStartTracking = false;
                            startedTracking = false;
                            actionBar.setEnabled(true);
                            scrollSlidingTextTabStrip.setEnabled(true);
                        }
                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                    }
                    return startedTracking;
                }
                return false;
            }
        };

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;

        for (int a = 0; a < mediaPages.length; a++) {
            if (a == 0) {
                if (mediaPages[a] != null && mediaPages[a].layoutManager != null) {
                    scrollToPositionOnRecreate = mediaPages[a].layoutManager.findFirstVisibleItemPosition();
                    if (scrollToPositionOnRecreate != mediaPages[a].layoutManager.getItemCount() - 1) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) mediaPages[a].listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                        if (holder != null) {
                            scrollToOffsetOnRecreate = holder.itemView.getTop();
                        } else {
                            scrollToPositionOnRecreate = -1;
                        }
                    } else {
                        scrollToPositionOnRecreate = -1;
                    }
                }
            }
            final MediaPage mediaPage = new MediaPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress && mediaPages[0] == this) {
                        float scrollProgress = Math.abs(mediaPages[0].getTranslationX()) / (float) mediaPages[0].getMeasuredWidth();
                        scrollSlidingTextTabStrip.selectTabWithId(mediaPages[1].selectedType, scrollProgress);
                        if (searchItemState == 2) {
                            searchItem.setAlpha(1.0f - scrollProgress);
                        } else if (searchItemState == 1) {
                            searchItem.setAlpha(scrollProgress);
                        }
                    }
                }
            };
            frameLayout.addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a] = mediaPage;

            final LinearLayoutManager layoutManager = mediaPages[a].layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
            mediaPages[a].listView = new RecyclerListView(context);
            mediaPages[a].listView.setClipToPadding(false);
            mediaPages[a].listView.setSectionsType(2);
            mediaPages[a].listView.setLayoutManager(layoutManager);
            mediaPages[a].addView(mediaPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].listView.setOnItemClickListener((view, position) -> {
                if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0, mediaPage.selectedType);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedAudioCell) view).getMessage(), 0, mediaPage.selectedType);
                }
            });
            mediaPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (searching && searchWas) {
                        return;
                    }
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();

                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !sharedMediaData[mediaPage.selectedType].loading) {
                        int type;
                        if (mediaPage.selectedType == 0) {
                            type = DataQuery.MEDIA_PHOTOVIDEO;
                        } else if (mediaPage.selectedType == 1) {
                            type = DataQuery.MEDIA_FILE;
                        } else if (mediaPage.selectedType == 2) {
                            type = DataQuery.MEDIA_AUDIO;
                        } else if (mediaPage.selectedType == 4) {
                            type = DataQuery.MEDIA_MUSIC;
                        } else {
                            type = DataQuery.MEDIA_URL;
                        }
                        if (!sharedMediaData[mediaPage.selectedType].endReached[0]) {
                            sharedMediaData[mediaPage.selectedType].loading = true;
                            DataQuery.getInstance(currentAccount).loadMedia(dialog_id, 50, sharedMediaData[mediaPage.selectedType].max_id[0], type, true, classGuid);
                        } else if (mergeDialogId != 0 && !sharedMediaData[mediaPage.selectedType].endReached[1]) {
                            sharedMediaData[mediaPage.selectedType].loading = true;
                            DataQuery.getInstance(currentAccount).loadMedia(mergeDialogId, 50, sharedMediaData[mediaPage.selectedType].max_id[1], type, true, classGuid);
                        }
                    }
                }
            });
            mediaPages[a].listView.setOnItemLongClickListener((view, position) -> {
                if (mediaPage.selectedType == 1 && view instanceof SharedDocumentCell) {
                    return MediaActivity.this.onItemLongClick(((SharedDocumentCell) view).getMessage(), view, 0);
                } else if (mediaPage.selectedType == 3 && view instanceof SharedLinkCell) {
                    return MediaActivity.this.onItemLongClick(((SharedLinkCell) view).getMessage(), view, 0);
                } else if ((mediaPage.selectedType == 2 || mediaPage.selectedType == 4) && view instanceof SharedAudioCell) {
                    return MediaActivity.this.onItemLongClick(((SharedAudioCell) view).getMessage(), view, 0);
                }
                return false;
            });
            if (a == 0 && scrollToPositionOnRecreate != -1) {
                layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            }

            mediaPages[a].emptyView = new LinearLayout(context);
            mediaPages[a].emptyView.setOrientation(LinearLayout.VERTICAL);
            mediaPages[a].emptyView.setGravity(Gravity.CENTER);
            mediaPages[a].emptyView.setVisibility(View.GONE);
            mediaPages[a].emptyView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            mediaPages[a].addView(mediaPages[a].emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            mediaPages[a].emptyView.setOnTouchListener((v, event) -> true);

            mediaPages[a].emptyImageView = new ImageView(context);
            mediaPages[a].emptyView.addView(mediaPages[a].emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            mediaPages[a].emptyTextView = new TextView(context);
            mediaPages[a].emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            mediaPages[a].emptyTextView.setGravity(Gravity.CENTER);
            mediaPages[a].emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            mediaPages[a].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            mediaPages[a].emptyView.addView(mediaPages[a].emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));

            mediaPages[a].progressView = new LinearLayout(context);
            mediaPages[a].progressView.setGravity(Gravity.CENTER);
            mediaPages[a].progressView.setOrientation(LinearLayout.VERTICAL);
            mediaPages[a].progressView.setVisibility(View.GONE);
            mediaPages[a].progressView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            mediaPages[a].addView(mediaPages[a].progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            mediaPages[a].progressBar = new RadialProgressView(context);
            mediaPages[a].progressView.addView(mediaPages[a].progressBar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            if (a != 0) {
                mediaPages[a].setVisibility(View.INVISIBLE);
            }
        }

        for (int a = 0; a < 6; a++) {
            cellCache.add(new SharedPhotoVideoCell(context));
        }

        updateTabs();
        switchToCurrentSelectedMode(false);

        if (!AndroidUtilities.isTablet()) {
            frameLayout.addView(fragmentContextView = new FragmentContextView(context, this, false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, 8, 0, 0));
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mediaDidLoaded) {
            long uid = (Long) args[0];
            int guid = (Integer) args[3];
            if (guid == classGuid) {
                int type = (Integer) args[4];
                sharedMediaData[type].loading = false;
                sharedMediaData[type].totalCount = (Integer) args[1];
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                boolean enc = ((int) dialog_id) == 0;
                int loadIndex = uid == dialog_id ? 0 : 1;
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject message = arr.get(a);
                    sharedMediaData[type].addMessage(message, false, enc);
                }
                sharedMediaData[type].endReached[loadIndex] = (Boolean) args[5];
                if (loadIndex == 0 && sharedMediaData[type].endReached[loadIndex] && mergeDialogId != 0) {
                    sharedMediaData[type].loading = true;
                    DataQuery.getInstance(currentAccount).loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], type, true, classGuid);
                }
                scrolling = true;
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == type) {
                        if (!sharedMediaData[type].loading) {
                            if (mediaPages[a].progressView != null) {
                                mediaPages[a].progressView.setVisibility(View.GONE);
                            }
                            if (mediaPages[a].selectedType == type && mediaPages[a].listView != null) {
                                if (mediaPages[a].listView.getEmptyView() == null) {
                                    mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
                                }
                            }
                        }
                    }

                    if (mediaPages[a].selectedType == 0 && type == 0) {
                        if (photoVideoAdapter != null) {
                            photoVideoAdapter.notifyDataSetChanged();
                        }
                    } else if (mediaPages[a].selectedType == 1 && type == 1) {
                        if (documentsAdapter != null) {
                            documentsAdapter.notifyDataSetChanged();
                        }
                    } else if (mediaPages[a].selectedType == 2 && type == 2) {
                        if (voiceAdapter != null) {
                            voiceAdapter.notifyDataSetChanged();
                        }
                    } else if (mediaPages[a].selectedType == 3 && type == 3) {
                        if (linksAdapter != null) {
                            linksAdapter.notifyDataSetChanged();
                        }
                    } else if (mediaPages[a].selectedType == 4 && type == 4) {
                        if (audioAdapter != null) {
                            audioAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            TLRPC.Chat currentChat = null;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance(currentAccount).getChat(-(int) dialog_id);
            }
            int channelId = (Integer) args[1];
            int loadIndex = 0;
            if (ChatObject.isChannel(currentChat)) {
                if (channelId == 0 && mergeDialogId != 0) {
                    loadIndex = 1;
                } else if (channelId == currentChat.id) {
                    loadIndex = 0;
                } else {
                    return;
                }
            } else if (channelId != 0) {
                return;
            }
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                for (SharedMediaData data : sharedMediaData) {
                    if (data.deleteMessage(ids, loadIndex)) {
                        updated = true;
                    }
                }
            }
            if (updated) {
                scrolling = true;
                if (photoVideoAdapter != null) {
                    photoVideoAdapter.notifyDataSetChanged();
                }
                if (documentsAdapter != null) {
                    documentsAdapter.notifyDataSetChanged();
                }
                if (voiceAdapter != null) {
                    voiceAdapter.notifyDataSetChanged();
                }
                if (linksAdapter != null) {
                    linksAdapter.notifyDataSetChanged();
                }
                if (audioAdapter != null) {
                    audioAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long uid = (Long) args[0];
            if (uid == dialog_id) {
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                boolean enc = ((int) dialog_id) == 0;
                boolean updated = false;
                for (int a = 0; a < arr.size(); a++) {
                    MessageObject obj = arr.get(a);
                    if (obj.messageOwner.media == null || obj.needDrawBluredPreview()) {
                        continue;
                    }
                    int type = DataQuery.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    if (sharedMediaData[type].addMessage(obj, true, enc)) {
                        updated = true;
                        hasMedia[type] = 1;
                    }
                }
                if (updated) {
                    scrolling = true;
                    if (photoVideoAdapter != null) {
                        photoVideoAdapter.notifyDataSetChanged();
                    }
                    if (documentsAdapter != null) {
                        documentsAdapter.notifyDataSetChanged();
                    }
                    if (voiceAdapter != null) {
                        voiceAdapter.notifyDataSetChanged();
                    }
                    if (linksAdapter != null) {
                        linksAdapter.notifyDataSetChanged();
                    }
                    if (audioAdapter != null) {
                        audioAdapter.notifyDataSetChanged();
                    }
                    updateTabs();
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer) args[0];
            Integer newMsgId = (Integer) args[1];
            for (SharedMediaData data : sharedMediaData) {
                data.replaceMid(msgId, newMsgId);
            }
        } else if (id == NotificationCenter.messagePlayingDidStarted || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
                for (int b = 0; b < mediaPages.length; b++) {
                    int count = mediaPages[b].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = mediaPages[b].listView.getChildAt(a);
                        if (view instanceof SharedAudioCell) {
                            SharedAudioCell cell = (SharedAudioCell) view;
                            MessageObject messageObject = cell.getMessage();
                            if (messageObject != null) {
                                cell.updateButtonState(false);
                            }
                        }
                    }
                }
            } else if (id == NotificationCenter.messagePlayingDidStarted) {
                MessageObject messageObject = (MessageObject) args[0];
                if (messageObject.eventId != 0) {
                    return;
                }
                for (int b = 0; b < mediaPages.length; b++) {
                    int count = mediaPages[b].listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View view = mediaPages[b].listView.getChildAt(a);
                        if (view instanceof SharedAudioCell) {
                            SharedAudioCell cell = (SharedAudioCell) view;
                            MessageObject messageObject1 = cell.getMessage();
                            if (messageObject1 != null) {
                                cell.updateButtonState(false);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        scrolling = true;
        if (photoVideoAdapter != null) {
            photoVideoAdapter.notifyDataSetChanged();
        }
        if (documentsAdapter != null) {
            documentsAdapter.notifyDataSetChanged();
        }
        if (linksAdapter != null) {
            linksAdapter.notifyDataSetChanged();
        }
        for (int a = 0; a < mediaPages.length; a++) {
            fixLayoutInternal(a);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (int a = 0; a < mediaPages.length; a++) {
            if (mediaPages[a].listView != null) {
                final int num = a;
                ViewTreeObserver obs = mediaPages[a].listView.getViewTreeObserver();
                obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        mediaPages[num].getViewTreeObserver().removeOnPreDrawListener(this);
                        fixLayoutInternal(num);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return actionBar.isEnabled();
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (info != null && info.migrated_from_chat_id != 0) {
            mergeDialogId = -info.migrated_from_chat_id;
        }
    }

    public void setMergeDialogId(long did) {
        mergeDialogId = did;
    }

    private void updateTabs() {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }
        boolean changed = false;
        if ((hasMedia[0] != 0 || hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0) && !scrollSlidingTextTabStrip.hasTab(0)) {
            changed = true;
        }
        if (hasMedia[1] != 0) {
            if (!scrollSlidingTextTabStrip.hasTab(1)) {
                changed = true;
            }
        }
        if ((int) dialog_id != 0) {
            if (hasMedia[3] != 0 && !scrollSlidingTextTabStrip.hasTab(3)) {
                changed = true;
            }
            if (hasMedia[4] != 0 && !scrollSlidingTextTabStrip.hasTab(4)) {
                changed = true;
            }
        } else {
            TLRPC.EncryptedChat currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
            if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46) {
                if (hasMedia[4] != 0 && !scrollSlidingTextTabStrip.hasTab(4)) {
                    changed = true;
                }
            }
        }
        if (hasMedia[2] != 0 && !scrollSlidingTextTabStrip.hasTab(2)) {
            changed = true;
        }
        if (changed) {
            scrollSlidingTextTabStrip.removeTabs();
            if (hasMedia[0] != 0 || hasMedia[1] == 0 && hasMedia[2] == 0 && hasMedia[3] == 0 && hasMedia[4] == 0) {
                if (!scrollSlidingTextTabStrip.hasTab(0)) {
                    scrollSlidingTextTabStrip.addTextTab(0, LocaleController.getString("SharedMediaTab", R.string.SharedMediaTab));
                }
            }
            if (hasMedia[1] != 0) {
                if (!scrollSlidingTextTabStrip.hasTab(1)) {
                    scrollSlidingTextTabStrip.addTextTab(1, LocaleController.getString("SharedFilesTab", R.string.SharedFilesTab));
                }
            }
            if ((int) dialog_id != 0) {
                if (hasMedia[3] != 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(3)) {
                        scrollSlidingTextTabStrip.addTextTab(3, LocaleController.getString("SharedLinksTab", R.string.SharedLinksTab));
                    }
                }
                if (hasMedia[4] != 0) {
                    if (!scrollSlidingTextTabStrip.hasTab(4)) {
                        scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab", R.string.SharedMusicTab));
                    }
                }
            } else {
                TLRPC.EncryptedChat currentEncryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat((int) (dialog_id >> 32));
                if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46) {
                    if (hasMedia[4] != 0) {
                        if (!scrollSlidingTextTabStrip.hasTab(4)) {
                            scrollSlidingTextTabStrip.addTextTab(4, LocaleController.getString("SharedMusicTab", R.string.SharedMusicTab));
                        }
                    }
                }
            }
            if (hasMedia[2] != 0) {
                if (!scrollSlidingTextTabStrip.hasTab(2)) {
                    scrollSlidingTextTabStrip.addTextTab(2, LocaleController.getString("SharedVoiceTab", R.string.SharedVoiceTab));
                }
            }
        }
        if (scrollSlidingTextTabStrip.getTabsCount() <= 1) {
            scrollSlidingTextTabStrip.setVisibility(View.GONE);
            actionBar.setExtraHeight(0);
        } else {
            scrollSlidingTextTabStrip.setVisibility(View.VISIBLE);
            actionBar.setExtraHeight(AndroidUtilities.dp(44));
        }
        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            mediaPages[0].selectedType = id;
        }
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < mediaPages.length; a++) {
            mediaPages[a].listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = mediaPages[a].listView.getAdapter();
        if (searching && searchWas) {
            if (animated) {
                if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2) {
                    searching = false;
                    searchWas = false;
                    switchToCurrentSelectedMode(true);
                    return;
                } else {
                    String text = searchItem.getSearchField().getText().toString();
                    if (mediaPages[a].selectedType == 1) {
                        if (documentsSearchAdapter != null) {
                            documentsSearchAdapter.search(text);
                            if (currentAdapter != documentsSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 3) {
                        if (linksSearchAdapter != null) {
                            linksSearchAdapter.search(text);
                            if (currentAdapter != linksSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(linksSearchAdapter);
                            }
                        }
                    } else if (mediaPages[a].selectedType == 4) {
                        if (audioSearchAdapter != null) {
                            audioSearchAdapter.search(text);
                            if (currentAdapter != audioSearchAdapter) {
                                recycleAdapter(currentAdapter);
                                mediaPages[a].listView.setAdapter(audioSearchAdapter);
                            }
                        }
                    }
                    if (searchItemState != 2 && mediaPages[a].emptyTextView != null) {
                        mediaPages[a].emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        mediaPages[a].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(30));
                        mediaPages[a].emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                        mediaPages[a].emptyImageView.setVisibility(View.GONE);
                    }
                }
            } else {
                if (mediaPages[a].listView != null) {
                    if (mediaPages[a].selectedType == 1) {
                        if (currentAdapter != documentsSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(documentsSearchAdapter);
                        }
                        documentsSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == 3) {
                        if (currentAdapter != linksSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(linksSearchAdapter);
                        }
                        linksSearchAdapter.notifyDataSetChanged();
                    } else if (mediaPages[a].selectedType == 4) {
                        if (currentAdapter != audioSearchAdapter) {
                            recycleAdapter(currentAdapter);
                            mediaPages[a].listView.setAdapter(audioSearchAdapter);
                        }
                        audioSearchAdapter.notifyDataSetChanged();
                    }
                }
                if (searchItemState != 2 && mediaPages[a].emptyTextView != null) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    mediaPages[a].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(30));
                    mediaPages[a].emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    mediaPages[a].emptyImageView.setVisibility(View.GONE);
                }
            }

        } else {
            mediaPages[a].emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            mediaPages[a].emptyImageView.setVisibility(View.VISIBLE);

            if (mediaPages[a].selectedType == 0) {
                if (currentAdapter != photoVideoAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(photoVideoAdapter);
                }
                mediaPages[a].emptyImageView.setImageResource(R.drawable.tip1);
                if ((int) dialog_id == 0) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoMediaSecret", R.string.NoMediaSecret));
                } else {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
                }
            } else if (mediaPages[a].selectedType == 1) {
                if (currentAdapter != documentsAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(documentsAdapter);
                }
                mediaPages[a].emptyImageView.setImageResource(R.drawable.tip2);
                if ((int) dialog_id == 0) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedFilesSecret", R.string.NoSharedFilesSecret));
                } else {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
                }
            } else if (mediaPages[a].selectedType == 2) {
                if (currentAdapter != voiceAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(voiceAdapter);
                }
                mediaPages[a].emptyImageView.setImageResource(R.drawable.tip5);
                if ((int) dialog_id == 0) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedVoiceSecret", R.string.NoSharedVoiceSecret));
                } else {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedVoice", R.string.NoSharedVoice));
                }
            } else if (mediaPages[a].selectedType == 3) {
                if (currentAdapter != linksAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(linksAdapter);
                }
                mediaPages[a].emptyImageView.setImageResource(R.drawable.tip3);
                if ((int) dialog_id == 0) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedLinksSecret", R.string.NoSharedLinksSecret));
                } else {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedLinks", R.string.NoSharedLinks));
                }
            } else if (mediaPages[a].selectedType == 4) {
                if (currentAdapter != audioAdapter) {
                    recycleAdapter(currentAdapter);
                    mediaPages[a].listView.setAdapter(audioAdapter);
                }
                mediaPages[a].emptyImageView.setImageResource(R.drawable.tip4);
                if ((int) dialog_id == 0) {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedAudioSecret", R.string.NoSharedAudioSecret));
                } else {
                    mediaPages[a].emptyTextView.setText(LocaleController.getString("NoSharedAudio", R.string.NoSharedAudio));
                }
            }
            mediaPages[a].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            if (mediaPages[a].selectedType == 0 || mediaPages[a].selectedType == 2) {
                if (animated) {
                    searchItemState = 2;
                } else {
                    searchItemState = 0;
                    searchItem.setAlpha(1.0f);
                    searchItem.setVisibility(View.GONE);
                }
            } else {
                if (animated) {
                    if (searchItem.getVisibility() != View.VISIBLE && !actionBar.isSearchFieldVisible()) {
                        searchItemState = 1;
                        searchItem.setVisibility(View.VISIBLE);
                        searchItem.setAlpha(0.0f);
                    } else {
                        searchItemState = 0;
                    }
                } else {
                    searchItemState = 0;
                    searchItem.setAlpha(1.0f);
                    searchItem.setVisibility(View.VISIBLE);
                }
            }
            if (mediaPages[a].selectedType != 0) {
                if (!sharedMediaData[mediaPages[a].selectedType].loading && !sharedMediaData[mediaPages[a].selectedType].endReached[0] && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
                    sharedMediaData[mediaPages[a].selectedType].loading = true;
                    DataQuery.getInstance(currentAccount).loadMedia(dialog_id, 50, 0, mediaPages[a].selectedType, true, classGuid);
                }
            }
            if (sharedMediaData[mediaPages[a].selectedType].loading && sharedMediaData[mediaPages[a].selectedType].messages.isEmpty()) {
                mediaPages[a].progressView.setVisibility(View.VISIBLE);
                mediaPages[a].listView.setEmptyView(null);
                mediaPages[a].emptyView.setVisibility(View.GONE);
            } else {
                mediaPages[a].progressView.setVisibility(View.GONE);
                mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
            }
            mediaPages[a].listView.setVisibility(View.VISIBLE);
            mediaPages[a].listView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
        }
        if (searchItemState == 2 && actionBar.isSearchFieldVisible()) {
            ignoreSearchCollapse = true;
            actionBar.closeSearchField();
            searchItem.setVisibility(View.GONE);
            searchItemState = 0;
        }
    }

    private boolean onItemLongClick(MessageObject item, View view, int a) {
        if (actionBar.isActionModeShowed() || getParentActivity() == null) {
            return false;
        }
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        selectedFiles[item.getDialogId() == dialog_id ? 0 : 1].put(item.getId(), item);
        if (!item.canDeleteMessage(null)) {
            cantDeleteMessagesCount++;
        }
        actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
        if (gotoItem != null) {
            gotoItem.setVisibility(View.VISIBLE);
        }
        selectedMessagesCountTextView.setNumber(1, false);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int i = 0; i < actionModeViews.size(); i++) {
            View view2 = actionModeViews.get(i);
            AndroidUtilities.clearDrawableAnimation(view2);
            animators.add(ObjectAnimator.ofFloat(view2, "scaleY", 0.1f, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.setDuration(250);
        animatorSet.start();
        scrolling = false;
        if (view instanceof SharedDocumentCell) {
            ((SharedDocumentCell) view).setChecked(true, true);
        } else if (view instanceof SharedPhotoVideoCell) {
            ((SharedPhotoVideoCell) view).setChecked(a, true, true);
        } else if (view instanceof SharedLinkCell) {
            ((SharedLinkCell) view).setChecked(true, true);
        } else if (view instanceof SharedAudioCell) {
            ((SharedAudioCell) view).setChecked(true, true);
        }
        actionBar.showActionMode();
        return true;
    }

    private void onItemClick(int index, View view, MessageObject message, int a, int selectedMode) {
        if (message == null) {
            return;
        }
        if (actionBar.isActionModeShowed()) {
            int loadIndex = message.getDialogId() == dialog_id ? 0 : 1;
            if (selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0) {
                selectedFiles[loadIndex].remove(message.getId());
                if (!message.canDeleteMessage(null)) {
                    cantDeleteMessagesCount--;
                }
            } else {
                if (selectedFiles[0].size() + selectedFiles[1].size() >= 100) {
                    return;
                }
                selectedFiles[loadIndex].put(message.getId(), message);
                if (!message.canDeleteMessage(null)) {
                    cantDeleteMessagesCount++;
                }
            }
            if (selectedFiles[0].size() == 0 && selectedFiles[1].size() == 0) {
                actionBar.hideActionMode();
            } else {
                selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
            }
            if (gotoItem != null) {
                gotoItem.setVisibility(selectedFiles[0].size() == 1 ? View.VISIBLE : View.GONE);
            }
            actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
            scrolling = false;
            if (view instanceof SharedDocumentCell) {
                ((SharedDocumentCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedPhotoVideoCell) {
                ((SharedPhotoVideoCell) view).setChecked(a, selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedLinkCell) {
                ((SharedLinkCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            } else if (view instanceof SharedAudioCell) {
                ((SharedAudioCell) view).setChecked(selectedFiles[loadIndex].indexOfKey(message.getId()) >= 0, true);
            }
        } else {
            if (selectedMode == 0) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
            } else if (selectedMode == 2 || selectedMode == 4) {
                if (view instanceof SharedAudioCell) {
                    ((SharedAudioCell) view).didPressedButton();
                }
            } else if (selectedMode == 1) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    if (cell.isLoaded()) {
                        File f = null;
                        String fileName = message.messageOwner.media != null ? FileLoader.getAttachFileName(message.getDocument()) : "";
                        if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                            f = new File(message.messageOwner.attachPath);
                        }
                        if (f == null || f != null && !f.exists()) {
                            f = FileLoader.getPathToMessage(message.messageOwner);
                        }
                        if (f != null && f.exists()) {
                            if (f.getName().toLowerCase().endsWith("attheme")) {
                                Theme.ThemeInfo themeInfo = Theme.applyThemeFile(f, message.getDocumentName(), true);
                                if (themeInfo != null) {
                                    presentFragment(new ThemePreviewActivity(f, themeInfo));
                                } else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setMessage(LocaleController.getString("IncorrectTheme", R.string.IncorrectTheme));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    showDialog(builder.create());
                                }
                            } else {
                                String realMimeType = null;
                                try {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                    int idx = fileName.lastIndexOf('.');
                                    if (idx != -1) {
                                        String ext = fileName.substring(idx + 1);
                                        realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                        if (realMimeType == null) {
                                            realMimeType = message.getDocument().mime_type;
                                            if (realMimeType == null || realMimeType.length() == 0) {
                                                realMimeType = null;
                                            }
                                        }
                                    }
                                    if (Build.VERSION.SDK_INT >= 24) {
                                        intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", f), realMimeType != null ? realMimeType : "text/plain");
                                    } else {
                                        intent.setDataAndType(Uri.fromFile(f), realMimeType != null ? realMimeType : "text/plain");
                                    }
                                    if (realMimeType != null) {
                                        try {
                                            getParentActivity().startActivityForResult(intent, 500);
                                        } catch (Exception e) {
                                            if (Build.VERSION.SDK_INT >= 24) {
                                                intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", f), "text/plain");
                                            } else {
                                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                            }
                                            getParentActivity().startActivityForResult(intent, 500);
                                        }
                                    } else {
                                        getParentActivity().startActivityForResult(intent, 500);
                                    }
                                } catch (Exception e) {
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                    builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.getDocument().mime_type));
                                    showDialog(builder.create());
                                }
                            }
                        }
                    } else if (!cell.isLoading()) {
                        FileLoader.getInstance(currentAccount).loadFile(cell.getMessage().getDocument(), false, 0);
                        cell.updateFileExistIcon();
                    } else {
                        FileLoader.getInstance(currentAccount).cancelLoadFile(cell.getMessage().getDocument());
                        cell.updateFileExistIcon();
                    }
                }
            } else if (selectedMode == 3) {
                try {
                    TLRPC.WebPage webPage = message.messageOwner.media.webpage;
                    String link = null;
                    if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                        if (webPage.embed_url != null && webPage.embed_url.length() != 0) {
                            openWebView(webPage);
                            return;
                        } else {
                            link = webPage.url;
                        }
                    }
                    if (link == null) {
                        link = ((SharedLinkCell) view).getLink(0);
                    }
                    if (link != null) {
                        Browser.openUrl(getParentActivity(), link);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    private void openWebView(TLRPC.WebPage webPage) {
        EmbedBottomSheet.show(getParentActivity(), webPage.site_name, webPage.description, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height);
    }

    private void recycleAdapter(RecyclerView.Adapter adapter) {
        if (adapter instanceof SharedPhotoVideoAdapter) {
            cellCache.addAll(cache);
            cache.clear();
        }
    }

    private void fixLayoutInternal(int num) {
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        if (num == 0) {
            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                selectedMessagesCountTextView.setTextSize(18);
            } else {
                selectedMessagesCountTextView.setTextSize(20);
            }
        }

        if (AndroidUtilities.isTablet()) {
            columnsCount = 4;
            mediaPages[num].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 6;
                mediaPages[num].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
            } else {
                columnsCount = 4;
                mediaPages[num].emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            }
        }
        if (num == 0) {
            photoVideoAdapter.notifyDataSetChanged();
        }
    }

    SharedLinkCell.SharedLinkCellDelegate sharedLinkCellDelegate = new SharedLinkCell.SharedLinkCellDelegate() {
        @Override
        public void needOpenWebView(TLRPC.WebPage webPage) {
            MediaActivity.this.openWebView(webPage);
        }

        @Override
        public boolean canPerformActions() {
            return !actionBar.isActionModeShowed();
        }

        @Override
        public void onLinkLongPress(final String urlFinal) {
            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
            builder.setTitle(urlFinal);
            builder.setItems(new CharSequence[]{LocaleController.getString("Open", R.string.Open), LocaleController.getString("Copy", R.string.Copy)}, (dialog, which) -> {
                if (which == 0) {
                    Browser.openUrl(getParentActivity(), urlFinal, true);
                } else if (which == 1) {
                    String url = urlFinal;
                    if (url.startsWith("mailto:")) {
                        url = url.substring(7);
                    } else if (url.startsWith("tel:")) {
                        url = url.substring(4);
                    }
                    AndroidUtilities.addToClipboard(url);
                }
            });
            showDialog(builder.create());
        }
    };

    private class SharedLinksAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;

        public SharedLinksAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[3].sections.size() + (sharedMediaData[3].sections.isEmpty() || sharedMediaData[3].endReached[0] && sharedMediaData[3].endReached[1] ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[3].sections.size()) {
                return sharedMediaData[3].sectionArrays.get(sharedMediaData[3].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
            }
            if (section < sharedMediaData[3].sections.size()) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((GraySectionCell) view).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext);
                    break;
                case 1:
                    view = new SharedLinkCell(mContext);
                    ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
                    break;
                case 2:
                default:
                    view = new LoadingCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((GraySectionCell) holder.itemView).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                        break;
                    }
                    case 1: {
                        SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedLinkCell.setLink(messageObject, position != messageObjects.size() || section == sharedMediaData[3].sections.size() - 1 && sharedMediaData[3].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedLinkCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sharedMediaData[3].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    private class SharedDocumentsAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;
        private int currentType;

        public SharedDocumentsAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[currentType].sections.size() + (sharedMediaData[currentType].sections.isEmpty() || sharedMediaData[currentType].endReached[0] && sharedMediaData[currentType].endReached[1] ? 0 : 1);
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[currentType].sections.size()) {
                return sharedMediaData[currentType].sectionArrays.get(sharedMediaData[currentType].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new GraySectionCell(mContext);
            }
            if (section < sharedMediaData[currentType].sections.size()) {
                String name = sharedMediaData[currentType].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[currentType].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((GraySectionCell) view).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext);
                    break;
                case 1:
                    view = new SharedDocumentCell(mContext);
                    break;
                case 2:
                    view = new LoadingCell(mContext);
                    break;
                case 3:
                default:
                    view = new SharedAudioCell(mContext) {
                        @Override
                        public boolean needPlayMessage(MessageObject messageObject) {
                            if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                                boolean result = MediaController.getInstance().playMessage(messageObject);
                                MediaController.getInstance().setVoiceMessagesPlaylist(result ? sharedMediaData[currentType].messages : null, false);
                                return result;
                            } else if (messageObject.isMusic()) {
                                return MediaController.getInstance().setPlaylist(sharedMediaData[currentType].messages, messageObject);
                            }
                            return false;
                        }
                    };
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2) {
                String name = sharedMediaData[currentType].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[currentType].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((GraySectionCell) holder.itemView).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                        break;
                    }
                    case 1: {
                        SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedDocumentCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                    case 3: {
                        SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                        MessageObject messageObject = messageObjects.get(position - 1);
                        sharedAudioCell.setMessageObject(messageObject, position != messageObjects.size() || section == sharedMediaData[currentType].sections.size() - 1 && sharedMediaData[currentType].loading);
                        if (actionBar.isActionModeShowed()) {
                            sharedAudioCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                        } else {
                            sharedAudioCell.setChecked(false, !scrolling);
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sharedMediaData[currentType].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    if (currentType == 2 || currentType == 4) {
                        return 3;
                    } else {
                        return 1;
                    }
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    private class SharedPhotoVideoAdapter extends RecyclerListView.SectionsAdapter {

        private Context mContext;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isEnabled(int section, int row) {
            return false;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[0].sections.size() + (sharedMediaData[0].sections.isEmpty() || sharedMediaData[0].endReached[0] && sharedMediaData[0].endReached[1] ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[0].sections.size()) {
                return (int) Math.ceil(sharedMediaData[0].sectionArrays.get(sharedMediaData[0].sections.get(section)).size() / (float)columnsCount) + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            if (view == null) {
                view = new SharedMediaSectionCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            if (section < sharedMediaData[0].sections.size()) {
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((SharedMediaSectionCell) view).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SharedMediaSectionCell(mContext);
                    break;
                case 1:
                    if (!cellCache.isEmpty()) {
                        view = cellCache.get(0);
                        cellCache.remove(0);
                    } else {
                        view = new SharedPhotoVideoCell(mContext);
                    }
                    SharedPhotoVideoCell cell = (SharedPhotoVideoCell) view;
                    cell.setDelegate(new SharedPhotoVideoCell.SharedPhotoVideoCellDelegate() {
                        @Override
                        public void didClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            onItemClick(index, cell, messageObject, a, 0);
                        }

                        @Override
                        public boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            return onItemLongClick(messageObject, cell, a);
                        }
                    });
                    cache.add((SharedPhotoVideoCell) view);
                    break;
                case 2:
                default:
                    view = new LoadingCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 2) {
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                switch (holder.getItemViewType()) {
                    case 0: {
                        MessageObject messageObject = messageObjects.get(0);
                        ((SharedMediaSectionCell) holder.itemView).setText(LocaleController.getInstance().formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                        break;
                    }
                    case 1: {
                        SharedPhotoVideoCell cell = (SharedPhotoVideoCell) holder.itemView;
                        cell.setItemsCount(columnsCount);
                        for (int a = 0; a < columnsCount; a++) {
                            int index = (position - 1) * columnsCount + a;
                            if (index < messageObjects.size()) {
                                MessageObject messageObject = messageObjects.get(index);
                                cell.setIsFirst(position == 1);
                                cell.setItem(a, sharedMediaData[0].messages.indexOf(messageObject), messageObject);

                                if (actionBar.isActionModeShowed()) {
                                    cell.setChecked(a, selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                                } else {
                                    cell.setChecked(a, false, !scrolling);
                                }
                            } else {
                                cell.setItem(a, index, null);
                            }
                        }
                        cell.requestLayout();
                        break;
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sharedMediaData[0].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public int getPositionForScrollProgress(float progress) {
            return 0;
        }
    }

    public class MediaSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private Timer searchTimer;
        protected ArrayList<MessageObject> globalSearch = new ArrayList<>();
        private int reqId = 0;
        private int lastReqId;
        private int currentType;
        private int searchesInProgress;

        public MediaSearchAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        public void queryServerSearch(final String query, final int max_id, long did) {
            int uid = (int) did;
            if (uid == 0) {
                return;
            }
            if (reqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = 0;
                searchesInProgress--;
            }
            if (query == null || query.length() == 0) {
                globalSearch.clear();
                lastReqId = 0;
                notifyDataSetChanged();
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.limit = 50;
            req.offset_id = max_id;
            if (currentType == 1) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (currentType == 3) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            } else if (currentType == 4) {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            }
            req.q = query;
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(uid);
            if (req.peer == null) {
                return;
            }
            final int currentReqId = ++lastReqId;
            searchesInProgress++;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                final ArrayList<MessageObject> messageObjects = new ArrayList<>();
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    for (int a = 0; a < res.messages.size(); a++) {
                        TLRPC.Message message = res.messages.get(a);
                        if (max_id != 0 && message.id > max_id) {
                            continue;
                        }
                        messageObjects.add(new MessageObject(currentAccount, message, false));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (reqId != 0) {
                        if (currentReqId == lastReqId) {
                            globalSearch = messageObjects;
                            searchesInProgress--;
                            notifyDataSetChanged();
                        }
                        reqId = 0;
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }

        public void search(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (TextUtils.isEmpty(query)) {
                if (!searchResult.isEmpty() || !globalSearch.isEmpty() || searchesInProgress != 0) {
                    searchResult.clear();
                    globalSearch.clear();
                    if (reqId != 0) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                        reqId = 0;
                        searchesInProgress--;
                    }
                    notifyDataSetChanged();
                }
            } else {
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == currentType) {
                        if (getItemCount() != 0) {
                            mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
                            mediaPages[a].progressView.setVisibility(View.GONE);
                        } else {
                            mediaPages[a].listView.setEmptyView(null);
                            mediaPages[a].emptyView.setVisibility(View.GONE);
                            mediaPages[a].progressView.setVisibility(View.VISIBLE);
                        }
                    }
                }

                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        processSearch(query);
                    }
                }, 200, 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!sharedMediaData[currentType].messages.isEmpty() && (currentType == 1 || currentType == 4)) {
                    MessageObject messageObject = sharedMediaData[currentType].messages.get(sharedMediaData[currentType].messages.size() - 1);
                    queryServerSearch(query, messageObject.getId(), messageObject.getDialogId());
                } else if (currentType == 3) {
                    queryServerSearch(query, 0, dialog_id);
                }
                if (currentType == 1 || currentType == 4) {
                    final ArrayList<MessageObject> copy = new ArrayList<>(sharedMediaData[currentType].messages);
                    searchesInProgress++;
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>());
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String search[] = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<MessageObject> resultArray = new ArrayList<>();

                        for (int a = 0; a < copy.size(); a++) {
                            MessageObject messageObject = copy.get(a);
                            for (int b = 0; b < search.length; b++) {
                                String q = search[b];
                                String name = messageObject.getDocumentName();
                                if (name == null || name.length() == 0) {
                                    continue;
                                }
                                name = name.toLowerCase();
                                if (name.contains(q)) {
                                    resultArray.add(messageObject);
                                    break;
                                }
                                if (currentType == 4) {
                                    TLRPC.Document document;
                                    if (messageObject.type == 0) {
                                        document = messageObject.messageOwner.media.webpage.document;
                                    } else {
                                        document = messageObject.messageOwner.media.document;
                                    }
                                    boolean ok = false;
                                    for (int c = 0; c < document.attributes.size(); c++) {
                                        TLRPC.DocumentAttribute attribute = document.attributes.get(c);
                                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                                            if (attribute.performer != null) {
                                                ok = attribute.performer.toLowerCase().contains(q);
                                            }
                                            if (!ok && attribute.title != null) {
                                                ok = attribute.title.toLowerCase().contains(q);
                                            }
                                            break;
                                        }
                                    }
                                    if (ok) {
                                        resultArray.add(messageObject);
                                        break;
                                    }
                                }
                            }
                        }

                        updateSearchResults(resultArray);
                    });
                }
            });
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents) {
            AndroidUtilities.runOnUIThread(() -> {
                searchesInProgress--;
                searchResult = documents;
                notifyDataSetChanged();
            });
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            if (searchesInProgress == 0) {
                for (int a = 0; a < mediaPages.length; a++) {
                    if (mediaPages[a].selectedType == currentType) {
                        mediaPages[a].listView.setEmptyView(mediaPages[a].emptyView);
                        mediaPages[a].progressView.setVisibility(View.GONE);
                    }
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != searchResult.size() + globalSearch.size();
        }

        @Override
        public int getItemCount() {
            int count = searchResult.size();
            int globalCount = globalSearch.size();
            if (globalCount != 0) {
                count += globalCount;
            }
            return count;
        }

        public boolean isGlobalSearch(int i) {
            int localCount = searchResult.size();
            int globalCount = globalSearch.size();
            if (i >= 0 && i < localCount) {
                return false;
            } else if (i > localCount && i <= globalCount + localCount) {
                return true;
            }
            return false;
        }

        public MessageObject getItem(int i) {
            if (i < searchResult.size()) {
                return searchResult.get(i);
            } else {
                return globalSearch.get(i - searchResult.size());
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (currentType == 1) {
                view = new SharedDocumentCell(mContext);
            } else if (currentType == 4) {
                view = new SharedAudioCell(mContext) {
                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                            boolean result = MediaController.getInstance().playMessage(messageObject);
                            MediaController.getInstance().setVoiceMessagesPlaylist(result ? searchResult : null, false);
                            if (messageObject.isRoundVideo()) {
                                MediaController.getInstance().setCurrentRoundVisible(false);
                            }
                            return result;
                        } else if (messageObject.isMusic()) {
                            return MediaController.getInstance().setPlaylist(searchResult, messageObject);
                        }
                        return false;
                    }
                };
            } else {
                view = new SharedLinkCell(mContext);
                ((SharedLinkCell) view).setDelegate(sharedLinkCellDelegate);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (currentType == 1) {
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedDocumentCell.setDocument(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedLinkCell.setLink(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 4) {
                SharedAudioCell sharedAudioCell = (SharedAudioCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedAudioCell.setMessageObject(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedAudioCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].indexOfKey(messageObject.getId()) >= 0, !scrolling);
                } else {
                    sharedAudioCell.setChecked(false, !scrolling);
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        arrayList.add(new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));

        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground));
        arrayList.add(new ThemeDescription(fragmentContextView, 0, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerPerformer));
        arrayList.add(new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose));

        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarDefaultSubtitle));
        arrayList.add(new ThemeDescription(scrollSlidingTextTabStrip.getTabsContainer(), ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{TextView.class}, null, null, null, Theme.key_actionBarDefaultSelector));
        arrayList.add(new ThemeDescription(null, 0, null, scrollSlidingTextTabStrip.getRectPaint(), null, null, Theme.key_actionBarDefaultTitle));

        for (int a = 0; a < mediaPages.length; a++) {
            final int num = a;
            ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
                if (mediaPages[num].listView != null) {
                    int count = mediaPages[num].listView.getChildCount();
                    for (int a1 = 0; a1 < count; a1++) {
                        View child = mediaPages[num].listView.getChildAt(a1);
                        if (child instanceof SharedPhotoVideoCell) {
                            ((SharedPhotoVideoCell) child).updateCheckboxColor();
                        }
                    }
                }
            };

            arrayList.add(new ThemeDescription(mediaPages[a].emptyView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(mediaPages[a].progressView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
            arrayList.add(new ThemeDescription(mediaPages[a].emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

            arrayList.add(new ThemeDescription(mediaPages[a].progressBar, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(mediaPages[a].emptyTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{SharedDocumentCell.class}, new String[]{"progressView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"statusImageView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedAudioCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_titleTextPaint, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedAudioCell.class}, Theme.chat_contextResult_descriptionTextPaint, null, null, Theme.key_windowBackgroundWhiteGrayText2));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"titleTextPaint"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholderText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholder));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR | ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, 0, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedPhotoVideoCell.class}, null, null, cellDelegate, Theme.key_checkbox));
            arrayList.add(new ThemeDescription(mediaPages[a].listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedPhotoVideoCell.class}, null, null, cellDelegate, Theme.key_checkboxCheck));
        }

        return arrayList.toArray(new ThemeDescription[arrayList.size()]);
    }
}

/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.query.SharedMediaQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

@SuppressWarnings("unchecked")
public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private SharedDocumentsAdapter audioAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter audioSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private RecyclerListView listView;
    private LinearLayout progressView;
    private TextView emptyTextView;
    private LinearLayoutManager layoutManager;
    private ImageView emptyImageView;
    private LinearLayout emptyView;
    private TextView dropDown;
    private Drawable dropDownDrawable;
    private RadialProgressView progressBar;
    private ActionBarMenuItem dropDownContainer;
    private ActionBarMenuItem searchItem;
    private NumberTextView selectedMessagesCountTextView;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ArrayList<SharedPhotoVideoCell> cellCache = new ArrayList<>(6);
    private FragmentContextView fragmentContextView;

    private boolean searchWas;
    private boolean searching;

    private HashMap<Integer, MessageObject>[] selectedFiles = new HashMap[] {new HashMap<>(), new HashMap<>()};
    private int cantDeleteMessagesCount;
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private boolean scrolling;
    private long mergeDialogId;
    protected TLRPC.ChatFull info = null;

    private long dialog_id;
    private int selectedMode;
    private int columnsCount = 4;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            if (messageObject == null || listView == null || selectedMode != 0) {
                return null;
            }
            int count = listView.getChildCount();

            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
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
                            object.parentView = listView;
                            object.imageReceiver = imageView.getImageReceiver();
                            object.thumb = object.imageReceiver.getBitmap();
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
        private HashMap<Integer, MessageObject>[] messagesDict = new HashMap[] {new HashMap<>(), new HashMap<>()};
        private ArrayList<String> sections = new ArrayList<>();
        private HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
        private int totalCount;
        private boolean loading;
        private boolean endReached[] = new boolean[] {false, true};
        private int max_id[] = new int[] {0, 0};

        public boolean addMessage(MessageObject messageObject, boolean isNew, boolean enc) {
            int loadIndex = messageObject.getDialogId() == dialog_id ? 0 : 1;
            if (messagesDict[loadIndex].containsKey(messageObject.getId())) {
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

    private final static int shared_media_item = 1;
    private final static int files_item = 2;
    private final static int links_item = 5;
    private final static int music_item = 6;
    private final static int forward = 3;
    private final static int delete = 4;

    public MediaActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByServer);
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
        SharedMediaQuery.loadMedia(dialog_id, 50, 0, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.mediaDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messageReceivedByServer);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle("");
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
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = listView.getChildAt(a);
                            if (child instanceof SharedDocumentCell) {
                                ((SharedDocumentCell) child).setChecked(false, true);
                            } else if (child instanceof SharedPhotoVideoCell) {
                                for (int b = 0; b < 6; b++) {
                                    ((SharedPhotoVideoCell) child).setChecked(b, false, true);
                                }
                            } else if (child instanceof SharedLinkCell) {
                                ((SharedLinkCell) child).setChecked(false, true);
                            }
                        }
                    } else {
                        finishFragment();
                    }
                } else if (id == shared_media_item) {
                    if (selectedMode == 0) {
                        return;
                    }
                    selectedMode = 0;
                    switchToCurrentSelectedMode();
                } else if (id == files_item) {
                    if (selectedMode == 1) {
                        return;
                    }
                    selectedMode = 1;
                    switchToCurrentSelectedMode();
                } else if (id == links_item) {
                    if (selectedMode == 3) {
                        return;
                    }
                    selectedMode = 3;
                    switchToCurrentSelectedMode();
                } else if (id == music_item) {
                    if (selectedMode == 4) {
                        return;
                    }
                    selectedMode = 4;
                    switchToCurrentSelectedMode();
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
                            currentUser = MessagesController.getInstance().getUser(lower_id);
                            currentChat = null;
                        } else {
                            currentUser = null;
                            currentChat = MessagesController.getInstance().getChat(-lower_id);
                        }
                        if (currentUser != null || !ChatObject.isChannel(currentChat)) {
                            int currentDate = ConnectionsManager.getInstance().getCurrentTime();
                            if (currentUser != null && currentUser.id != UserConfig.getClientUserId() || currentChat != null) {
                                boolean hasOutgoing = false;
                                for (int a = 1; a >= 0; a--) {
                                    int channelId = 0;
                                    for (HashMap.Entry<Integer, MessageObject> entry : selectedFiles[a].entrySet()) {
                                        MessageObject msg = entry.getValue();
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
                                    CheckBoxCell cell = new CheckBoxCell(getParentActivity(), true);
                                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                                    if (currentChat != null) {
                                        cell.setText(LocaleController.getString("DeleteForAll", R.string.DeleteForAll), "", false, false);
                                    } else {
                                        cell.setText(LocaleController.formatString("DeleteForUser", R.string.DeleteForUser, UserObject.getFirstName(currentUser)), "", false, false);
                                    }
                                    cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
                                    frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
                                    cell.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            CheckBoxCell cell = (CheckBoxCell) v;
                                            deleteForAll[0] = !deleteForAll[0];
                                            cell.setChecked(deleteForAll[0], true);
                                        }
                                    });
                                    builder.setView(frameLayout);
                                }
                            }
                        }
                    }

                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            for (int a = 1; a >= 0; a--) {
                                ArrayList<Integer> ids = new ArrayList<>(selectedFiles[a].keySet());
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
                                    currentEncryptedChat = MessagesController.getInstance().getEncryptedChat((int) (dialog_id >> 32));
                                }
                                if (currentEncryptedChat != null) {
                                    random_ids = new ArrayList<>();
                                    for (HashMap.Entry<Integer, MessageObject> entry : selectedFiles[a].entrySet()) {
                                        MessageObject msg = entry.getValue();
                                        if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                            random_ids.add(msg.messageOwner.random_id);
                                        }
                                    }
                                }
                                MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat, channelId, deleteForAll[0]);
                                selectedFiles[a].clear();
                            }
                            actionBar.hideActionMode();
                            actionBar.closeSearchField();
                            cantDeleteMessagesCount = 0;
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 3);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                        @Override
                        public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                            ArrayList<MessageObject> fmessages = new ArrayList<>();
                            for (int a = 1; a >= 0; a--) {
                                ArrayList<Integer> ids = new ArrayList<>(selectedFiles[a].keySet());
                                Collections.sort(ids);
                                for (Integer id : ids) {
                                    if (id > 0) {
                                        fmessages.add(selectedFiles[a].get(id));
                                    }
                                }
                                selectedFiles[a].clear();
                            }
                            cantDeleteMessagesCount = 0;
                            actionBar.hideActionMode();

                            if (dids.size() > 1 || dids.get(0) == UserConfig.getClientUserId() || message != null) {
                                for (int a = 0; a < dids.size(); a++) {
                                    long did = dids.get(a);
                                    if (message != null) {
                                        SendMessagesHelper.getInstance().sendMessage(message.toString(), did, null, null, true, null, null, null);
                                    }
                                    SendMessagesHelper.getInstance().sendMessage(fmessages, did);
                                }
                                fragment.finishFragment();
                            } else {
                                long did = dids.get(0);
                                int lower_part = (int) did;
                                int high_part = (int) (did >> 32);
                                Bundle args = new Bundle();
                                args.putBoolean("scrollToTopOnResume", true);
                                if (lower_part != 0) {
                                    if (lower_part > 0) {
                                        args.putInt("user_id", lower_part);
                                    } else if (lower_part < 0) {
                                        args.putInt("chat_id", -lower_part);
                                    }
                                } else {
                                    args.putInt("enc_id", high_part);
                                }
                                if (lower_part != 0) {
                                    if (!MessagesController.checkCanOpenChat(args, fragment)) {
                                        return;
                                    }
                                }

                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);

                                ChatActivity chatActivity = new ChatActivity(args);
                                presentFragment(chatActivity, true);
                                chatActivity.showReplyPanel(true, null, fmessages, null, false);

                                if (!AndroidUtilities.isTablet()) {
                                    removeSelfFromStack();
                                }
                            }
                        }
                    });
                    presentFragment(fragment);
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
                dropDownContainer.setVisibility(View.GONE);
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
                dropDownContainer.setVisibility(View.VISIBLE);
                if (selectedMode == 1) {
                    documentsSearchAdapter.search(null);
                } else if (selectedMode == 3) {
                    linksSearchAdapter.search(null);
                } else if (selectedMode == 4) {
                    audioSearchAdapter.search(null);
                }
                searching = false;
                searchWas = false;
                switchToCurrentSelectedMode();
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    switchToCurrentSelectedMode();
                }
                if (selectedMode == 1) {
                    if (documentsSearchAdapter == null) {
                        return;
                    }
                    documentsSearchAdapter.search(text);
                } else if (selectedMode == 3) {
                    if (linksSearchAdapter == null) {
                        return;
                    }
                    linksSearchAdapter.search(text);
                } else if (selectedMode == 4) {
                    if (audioSearchAdapter == null) {
                        return;
                    }
                    audioSearchAdapter.search(text);
                }
            }
        });
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(View.GONE);

        dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0);
        dropDownContainer.setSubMenuOpenSide(1);
        dropDownContainer.addSubItem(shared_media_item, LocaleController.getString("SharedMediaTitle", R.string.SharedMediaTitle));
        dropDownContainer.addSubItem(files_item, LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle));
        if ((int) dialog_id != 0) {
            dropDownContainer.addSubItem(links_item, LocaleController.getString("LinksTitle", R.string.LinksTitle));
            dropDownContainer.addSubItem(music_item, LocaleController.getString("AudioTitle", R.string.AudioTitle));
        } else {
            TLRPC.EncryptedChat currentEncryptedChat = MessagesController.getInstance().getEncryptedChat((int) (dialog_id >> 32));
            if (currentEncryptedChat != null && AndroidUtilities.getPeerLayerVersion(currentEncryptedChat.layer) >= 46) {
                dropDownContainer.addSubItem(music_item, LocaleController.getString("AudioTitle", R.string.AudioTitle));
            }
        }
        actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
        dropDownContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dropDownContainer.toggleSubMenu();
            }
        });

        dropDown = new TextView(context);
        dropDown.setGravity(Gravity.LEFT);
        dropDown.setSingleLine(true);
        dropDown.setLines(1);
        dropDown.setMaxLines(1);
        dropDown.setEllipsize(TextUtils.TruncateAt.END);
        dropDown.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultTitle), PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
        dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        if ((int) dialog_id != 0) {
            actionModeViews.add(actionMode.addItemWithWidth(forward, R.drawable.ic_ab_forward, AndroidUtilities.dp(54)));
        }
        actionModeViews.add(actionMode.addItemWithWidth(delete, R.drawable.ic_ab_delete, AndroidUtilities.dp(54)));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context);
        documentsAdapter = new SharedDocumentsAdapter(context, 1);
        audioAdapter = new SharedDocumentsAdapter(context, 4);
        documentsSearchAdapter = new MediaSearchAdapter(context, 1);
        audioSearchAdapter = new MediaSearchAdapter(context, 4);
        linksSearchAdapter = new MediaSearchAdapter(context, 3);
        linksAdapter = new SharedLinksAdapter(context);

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context);

        int scrollToPositionOnRecreate = -1;
        int scrollToOffsetOnRecreate = 0;
        if (layoutManager != null) {
            scrollToPositionOnRecreate = layoutManager.findFirstVisibleItemPosition();
            if (scrollToPositionOnRecreate != layoutManager.getItemCount() - 1) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                if (holder != null) {
                    scrollToOffsetOnRecreate = holder.itemView.getTop();
                } else {
                    scrollToPositionOnRecreate = -1;
                }
            } else {
                scrollToPositionOnRecreate = -1;
            }
        }

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setSectionsType(2);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if ((selectedMode == 1 || selectedMode == 4) && view instanceof SharedDocumentCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedDocumentCell) view).getMessage(), 0);
                } else if (selectedMode == 3 && view instanceof SharedLinkCell) {
                    MediaActivity.this.onItemClick(position, view, ((SharedLinkCell) view).getMessage(), 0);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
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

                if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !sharedMediaData[selectedMode].loading) {
                    int type;
                    if (selectedMode == 0) {
                        type = SharedMediaQuery.MEDIA_PHOTOVIDEO;
                    } else if (selectedMode == 1) {
                        type = SharedMediaQuery.MEDIA_FILE;
                    } else if (selectedMode == 2) {
                        type = SharedMediaQuery.MEDIA_AUDIO;
                    } else if (selectedMode == 4) {
                        type = SharedMediaQuery.MEDIA_MUSIC;
                    } else {
                        type = SharedMediaQuery.MEDIA_URL;
                    }
                    if (!sharedMediaData[selectedMode].endReached[0]) {
                        sharedMediaData[selectedMode].loading = true;
                        SharedMediaQuery.loadMedia(dialog_id, 50, sharedMediaData[selectedMode].max_id[0], type, true, classGuid);
                    } else if (mergeDialogId != 0 && !sharedMediaData[selectedMode].endReached[1]) {
                        sharedMediaData[selectedMode].loading = true;
                        SharedMediaQuery.loadMedia(mergeDialogId, 50, sharedMediaData[selectedMode].max_id[1], type, true, classGuid);
                    }
                }
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if ((selectedMode == 1 || selectedMode == 4) && view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    MessageObject message = cell.getMessage();
                    return MediaActivity.this.onItemLongClick(message, view, 0);
                } else if (selectedMode == 3 && view instanceof SharedLinkCell) {
                    SharedLinkCell cell = (SharedLinkCell) view;
                    MessageObject message = cell.getMessage();
                    return MediaActivity.this.onItemLongClick(message, view, 0);
                }
                return false;
            }
        });
        if (scrollToPositionOnRecreate != -1) {
            layoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
        }

        for (int a = 0; a < 6; a++) {
            cellCache.add(new SharedPhotoVideoCell(context));
        }

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        emptyImageView = new ImageView(context);
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTextView = new TextView(context);
        emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
        emptyView.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));

        progressView = new LinearLayout(context);
        progressView.setGravity(Gravity.CENTER);
        progressView.setOrientation(LinearLayout.VERTICAL);
        progressView.setVisibility(View.GONE);
        progressView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressBar = new RadialProgressView(context);
        progressView.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        switchToCurrentSelectedMode();

        if (!AndroidUtilities.isTablet()) {
            frameLayout.addView(fragmentContextView = new FragmentContextView(context, this, false), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 39, Gravity.TOP | Gravity.LEFT, 0, -36, 0, 0));
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
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
                    SharedMediaQuery.loadMedia(mergeDialogId, 50, sharedMediaData[type].max_id[1], type, true, classGuid);
                }
                if (!sharedMediaData[type].loading) {
                    if (progressView != null) {
                        progressView.setVisibility(View.GONE);
                    }
                    if (selectedMode == type && listView != null) {
                        if (listView.getEmptyView() == null) {
                            listView.setEmptyView(emptyView);
                        }
                    }
                }
                scrolling = true;
                if (selectedMode == 0 && type == 0) {
                    if (photoVideoAdapter != null) {
                        photoVideoAdapter.notifyDataSetChanged();
                    }
                } else if (selectedMode == 1 && type == 1) {
                    if (documentsAdapter != null) {
                        documentsAdapter.notifyDataSetChanged();
                    }
                } else if (selectedMode == 3 && type == 3) {
                    if (linksAdapter != null) {
                        linksAdapter.notifyDataSetChanged();
                    }
                } else if (selectedMode == 4 && type == 4) {
                    if (audioAdapter != null) {
                        audioAdapter.notifyDataSetChanged();
                    }
                }
                if (selectedMode == 1 || selectedMode == 3 || selectedMode == 4) {
                    searchItem.setVisibility(!sharedMediaData[type].messages.isEmpty() && !searching ? View.VISIBLE : View.GONE);
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            TLRPC.Chat currentChat = null;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
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
                if (linksAdapter != null) {
                    linksAdapter.notifyDataSetChanged();
                }
                if (audioAdapter != null) {
                    audioAdapter.notifyDataSetChanged();
                }
                if (selectedMode == 1 || selectedMode == 3 || selectedMode == 4) {
                    searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() && !searching ? View.VISIBLE : View.GONE);
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
                    if (obj.messageOwner.media == null || obj.isSecretPhoto()) {
                        continue;
                    }
                    int type = SharedMediaQuery.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    if (sharedMediaData[type].addMessage(obj, true, enc)) {
                        updated = true;
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
                    if (linksAdapter != null) {
                        linksAdapter.notifyDataSetChanged();
                    }
                    if (audioAdapter != null) {
                        audioAdapter.notifyDataSetChanged();
                    }
                    if (selectedMode == 1 || selectedMode == 3 || selectedMode == 4) {
                        searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() && !searching ? View.VISIBLE : View.GONE);
                    }
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer) args[0];
            Integer newMsgId = (Integer) args[1];
            for (SharedMediaData data : sharedMediaData) {
                data.replaceMid(msgId, newMsgId);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (dropDownContainer != null) {
            dropDownContainer.closeSubMenu();
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
        fixLayoutInternal();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    fixLayoutInternal();
                    return true;
                }
            });
        }
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

    private void switchToCurrentSelectedMode() {
        if (searching && searchWas) {
            if (listView != null) {
                if (selectedMode == 1) {
                    listView.setAdapter(documentsSearchAdapter);
                    documentsSearchAdapter.notifyDataSetChanged();
                } else if (selectedMode == 3) {
                    listView.setAdapter(linksSearchAdapter);
                    linksSearchAdapter.notifyDataSetChanged();
                } else if (selectedMode == 4) {
                    listView.setAdapter(audioSearchAdapter);
                    audioSearchAdapter.notifyDataSetChanged();
                }
            }
            if (emptyTextView != null) {
                emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                emptyImageView.setVisibility(View.GONE);
            }
        } else {
            emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            emptyImageView.setVisibility(View.VISIBLE);
            if (selectedMode == 0) {
                listView.setAdapter(photoVideoAdapter);
                dropDown.setText(LocaleController.getString("SharedMediaTitle", R.string.SharedMediaTitle));
                emptyImageView.setImageResource(R.drawable.tip1);
                if ((int) dialog_id == 0) {
                    emptyTextView.setText(LocaleController.getString("NoMediaSecret", R.string.NoMediaSecret));
                } else {
                    emptyTextView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
                }
                searchItem.setVisibility(View.GONE);
                if (sharedMediaData[selectedMode].loading && sharedMediaData[selectedMode].messages.isEmpty()) {
                    progressView.setVisibility(View.VISIBLE);
                    listView.setEmptyView(null);
                    emptyView.setVisibility(View.GONE);
                } else {
                    progressView.setVisibility(View.GONE);
                    listView.setEmptyView(emptyView);
                }
                listView.setVisibility(View.VISIBLE);
                listView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
            } else if (selectedMode == 1 || selectedMode == 4) {
                if (selectedMode == 1) {
                    listView.setAdapter(documentsAdapter);
                    dropDown.setText(LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle));
                    emptyImageView.setImageResource(R.drawable.tip2);
                    if ((int) dialog_id == 0) {
                        emptyTextView.setText(LocaleController.getString("NoSharedFilesSecret", R.string.NoSharedFilesSecret));
                    } else {
                        emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
                    }
                } else if (selectedMode == 4) {
                    listView.setAdapter(audioAdapter);
                    dropDown.setText(LocaleController.getString("AudioTitle", R.string.AudioTitle));
                    emptyImageView.setImageResource(R.drawable.tip4);
                    if ((int) dialog_id == 0) {
                        emptyTextView.setText(LocaleController.getString("NoSharedAudioSecret", R.string.NoSharedAudioSecret));
                    } else {
                        emptyTextView.setText(LocaleController.getString("NoSharedAudio", R.string.NoSharedAudio));
                    }
                }
                searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() ? View.VISIBLE : View.GONE);
                if (!sharedMediaData[selectedMode].loading && !sharedMediaData[selectedMode].endReached[0] && sharedMediaData[selectedMode].messages.isEmpty()) {
                    sharedMediaData[selectedMode].loading = true;
                    SharedMediaQuery.loadMedia(dialog_id, 50, 0, selectedMode == 1 ? SharedMediaQuery.MEDIA_FILE : SharedMediaQuery.MEDIA_MUSIC, true, classGuid);
                }
                listView.setVisibility(View.VISIBLE);
                if (sharedMediaData[selectedMode].loading && sharedMediaData[selectedMode].messages.isEmpty()) {
                    progressView.setVisibility(View.VISIBLE);
                    listView.setEmptyView(null);
                    emptyView.setVisibility(View.GONE);
                } else {
                    progressView.setVisibility(View.GONE);
                    listView.setEmptyView(emptyView);
                }
                listView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
            } else if (selectedMode == 3) {
                listView.setAdapter(linksAdapter);
                dropDown.setText(LocaleController.getString("LinksTitle", R.string.LinksTitle));
                emptyImageView.setImageResource(R.drawable.tip3);
                if ((int) dialog_id == 0) {
                    emptyTextView.setText(LocaleController.getString("NoSharedLinksSecret", R.string.NoSharedLinksSecret));
                } else {
                    emptyTextView.setText(LocaleController.getString("NoSharedLinks", R.string.NoSharedLinks));
                }
                searchItem.setVisibility(!sharedMediaData[3].messages.isEmpty() ? View.VISIBLE : View.GONE);
                if (!sharedMediaData[selectedMode].loading && !sharedMediaData[selectedMode].endReached[0] && sharedMediaData[selectedMode].messages.isEmpty()) {
                    sharedMediaData[selectedMode].loading = true;
                    SharedMediaQuery.loadMedia(dialog_id, 50, 0, SharedMediaQuery.MEDIA_URL, true, classGuid);
                }
                listView.setVisibility(View.VISIBLE);
                if (sharedMediaData[selectedMode].loading && sharedMediaData[selectedMode].messages.isEmpty()) {
                    progressView.setVisibility(View.VISIBLE);
                    listView.setEmptyView(null);
                    emptyView.setVisibility(View.GONE);
                } else {
                    progressView.setVisibility(View.GONE);
                    listView.setEmptyView(emptyView);
                }
                listView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
            }
        }
    }

    private boolean onItemLongClick(MessageObject item, View view, int a) {
        if (actionBar.isActionModeShowed()) {
            return false;
        }
        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
        selectedFiles[item.getDialogId() == dialog_id ? 0 : 1].put(item.getId(), item);
        if (!item.canDeleteMessage(null)) {
            cantDeleteMessagesCount++;
        }
        actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
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
        }
        actionBar.showActionMode();
        return true;
    }

    private void onItemClick(int index, View view, MessageObject message, int a) {
        if (message == null) {
            return;
        }
        if (actionBar.isActionModeShowed()) {
            int loadIndex = message.getDialogId() == dialog_id ? 0 : 1;
            if (selectedFiles[loadIndex].containsKey(message.getId())) {
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
            if (selectedFiles[0].isEmpty() && selectedFiles[1].isEmpty()) {
                actionBar.hideActionMode();
            } else {
                selectedMessagesCountTextView.setNumber(selectedFiles[0].size() + selectedFiles[1].size(), true);
            }
            actionBar.createActionMode().getItem(delete).setVisibility(cantDeleteMessagesCount == 0 ? View.VISIBLE : View.GONE);
            scrolling = false;
            if (view instanceof SharedDocumentCell) {
                ((SharedDocumentCell) view).setChecked(selectedFiles[loadIndex].containsKey(message.getId()), true);
            } else if (view instanceof SharedPhotoVideoCell) {
                ((SharedPhotoVideoCell) view).setChecked(a, selectedFiles[loadIndex].containsKey(message.getId()), true);
            } else if (view instanceof SharedLinkCell) {
                ((SharedLinkCell) view).setChecked(selectedFiles[loadIndex].containsKey(message.getId()), true);
            }
        } else {
            if (selectedMode == 0) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, dialog_id, mergeDialogId, provider);
            } else if (selectedMode == 1 || selectedMode == 4) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    if (cell.isLoaded()) {
                        if (message.isMusic()) {
                            if (MediaController.getInstance().setPlaylist(sharedMediaData[selectedMode].messages, message)) {
                                return;
                            }
                        }
                        File f = null;
                        String fileName = message.messageOwner.media != null ? FileLoader.getAttachFileName(message.getDocument()) : "";
                        if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                            f = new File(message.messageOwner.attachPath);
                        }
                        if (f == null || f != null && !f.exists()) {
                            f = FileLoader.getPathToMessage(message.messageOwner);
                        }
                        if (f != null && f.exists()) {
                            if (f.getName().endsWith("attheme")) {
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
                        FileLoader.getInstance().loadFile(cell.getMessage().getDocument(), false, 0);
                        cell.updateFileExistIcon();
                    } else {
                        FileLoader.getInstance().cancelLoadFile(cell.getMessage().getDocument());
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

    private void fixLayoutInternal() {
        if (listView == null) {
            return;
        }
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            selectedMessagesCountTextView.setTextSize(18);
        } else {
            selectedMessagesCountTextView.setTextSize(20);
        }

        if (AndroidUtilities.isTablet()) {
            columnsCount = 4;
            emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 6;
                emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
            } else {
                columnsCount = 4;
                emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            }
        }
        photoVideoAdapter.notifyDataSetChanged();

        if (dropDownContainer != null) {
            if (!AndroidUtilities.isTablet()) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                dropDownContainer.setLayoutParams(layoutParams);
            }

            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dropDown.setTextSize(18);
            } else {
                dropDown.setTextSize(20);
            }
        }
    }

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
                    ((SharedLinkCell) view).setDelegate(new SharedLinkCell.SharedLinkCellDelegate() {
                        @Override
                        public void needOpenWebView(TLRPC.WebPage webPage) {
                            MediaActivity.this.openWebView(webPage);
                        }

                        @Override
                        public boolean canPerformActions() {
                            return !actionBar.isActionModeShowed();
                        }
                    });
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
                            sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId()), !scrolling);
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
                default:
                    view = new LoadingCell(mContext);
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
                            sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId()), !scrolling);
                        } else {
                            sharedDocumentCell.setChecked(false, !scrolling);
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
                            onItemClick(index, cell, messageObject, a);
                        }

                        @Override
                        public boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a) {
                            return onItemLongClick(messageObject, cell, a);
                        }
                    });
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
                                    cell.setChecked(a, selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId()), !scrolling);
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
                ConnectionsManager.getInstance().cancelRequest(reqId, true);
                reqId = 0;
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
            req.peer = MessagesController.getInputPeer(uid);
            if (req.peer == null) {
                return;
            }
            final int currentReqId = ++lastReqId;
            reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    final ArrayList<MessageObject> messageObjects = new ArrayList<>();
                    if (error == null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        for (int a = 0; a < res.messages.size(); a++) {
                            TLRPC.Message message = res.messages.get(a);
                            if (max_id != 0 && message.id > max_id) {
                                continue;
                            }
                            messageObjects.add(new MessageObject(message, null, false));
                        }
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (currentReqId == lastReqId) {
                                globalSearch = messageObjects;
                                notifyDataSetChanged();
                            }
                            reqId = 0;
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }

        public void search(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchResult.clear();
                notifyDataSetChanged();
            } else {
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
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!sharedMediaData[currentType].messages.isEmpty()) {
                        if (currentType == 1 || currentType == 4) {
                            MessageObject messageObject = sharedMediaData[currentType].messages.get(sharedMediaData[currentType].messages.size() - 1);
                            queryServerSearch(query, messageObject.getId(), messageObject.getDialogId());
                        } else if (currentType == 3) {
                            queryServerSearch(query, 0, dialog_id);
                        }
                    }
                    if (currentType == 1 || currentType == 4) {
                        final ArrayList<MessageObject> copy = new ArrayList<>();
                        copy.addAll(sharedMediaData[currentType].messages);
                        Utilities.searchQueue.postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                String search1 = query.trim().toLowerCase();
                                if (search1.length() == 0) {
                                    updateSearchResults(new ArrayList<MessageObject>());
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
                            }
                        });
                    }
                }
            });
        }

        private void updateSearchResults(final ArrayList<MessageObject> documents) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    searchResult = documents;
                    notifyDataSetChanged();
                }
            });
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
            if (currentType == 1 || currentType == 4) {
                view = new SharedDocumentCell(mContext);
            } else {
                view = new SharedLinkCell(mContext);
                ((SharedLinkCell) view).setDelegate(new SharedLinkCell.SharedLinkCellDelegate() {
                    @Override
                    public void needOpenWebView(TLRPC.WebPage webPage) {
                        MediaActivity.this.openWebView(webPage);
                    }

                    @Override
                    public boolean canPerformActions() {
                        return !actionBar.isActionModeShowed();
                    }
                });
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (currentType == 1 || currentType == 4) {
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedDocumentCell.setDocument(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedDocumentCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId()), !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                SharedLinkCell sharedLinkCell = (SharedLinkCell) holder.itemView;
                MessageObject messageObject = getItem(position);
                sharedLinkCell.setLink(messageObject, position != getItemCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedLinkCell.setChecked(selectedFiles[messageObject.getDialogId() == dialog_id ? 0 : 1].containsKey(messageObject.getId()), !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
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
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof SharedPhotoVideoCell) {
                        ((SharedPhotoVideoCell) child).updateCheckboxColor();
                    }
                }
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(progressView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(dropDown, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(dropDown, 0, null, null, new Drawable[]{dropDownDrawable}, null, Theme.key_actionBarDefaultTitle),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),

                new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),

                new ThemeDescription(progressBar, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(emptyTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{SharedDocumentCell.class}, new String[]{"progressView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"statusImageView"}, null, null, null, Theme.key_sharedMedia_startStopLoadIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedLinkCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck),
                new ThemeDescription(listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"titleTextPaint"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{SharedLinkCell.class}, null, null, null, Theme.key_windowBackgroundWhiteLinkText),
                new ThemeDescription(listView, 0, new Class[]{SharedLinkCell.class}, Theme.linkSelectionPaint, null, null, Theme.key_windowBackgroundWhiteLinkSelection),
                new ThemeDescription(listView, 0, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholderText),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedLinkCell.class}, new String[]{"letterDrawable"}, null, null, null, Theme.key_sharedMedia_linkPlaceholder),

                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{SharedMediaSectionCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(listView, ThemeDescription.FLAG_SECTIONS, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{SharedMediaSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedPhotoVideoCell.class}, null, null, сellDelegate, Theme.key_checkbox),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedPhotoVideoCell.class}, null, null, сellDelegate, Theme.key_checkboxCheck),

                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerBackground),
                new ThemeDescription(fragmentContextView, 0, new Class[]{FragmentContextView.class}, new String[]{"playButton"}, null, null, null, Theme.key_inappPlayerPlayPause),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_inappPlayerTitle),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{FragmentContextView.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_inappPlayerPerformer),
                new ThemeDescription(fragmentContextView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FragmentContextView.class}, new String[]{"closeButton"}, null, null, null, Theme.key_inappPlayerClose),
        };
    }
}

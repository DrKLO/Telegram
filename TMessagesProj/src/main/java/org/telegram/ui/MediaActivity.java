/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesController;
import org.telegram.android.query.SharedMediaQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessageObject;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Adapters.BaseSectionsAdapter;
import org.telegram.android.AnimationCompat.AnimatorSetProxy;
import org.telegram.android.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedMediaSectionCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SectionsListView;
import org.telegram.ui.Components.WebFrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

    private SharedPhotoVideoAdapter photoVideoAdapter;
    private SharedLinksAdapter linksAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private MediaSearchAdapter documentsSearchAdapter;
    private MediaSearchAdapter linksSearchAdapter;
    private SectionsListView listView;
    private LinearLayout progressView;
    private TextView emptyTextView;
    private ImageView emptyImageView;
    private LinearLayout emptyView;
    private TextView dropDown;
    private ActionBarMenuItem dropDownContainer;
    private ActionBarMenuItem searchItem;
    private TextView selectedMessagesCountTextView;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ArrayList<SharedPhotoVideoCell> cellCache = new ArrayList<>(6);

    private boolean searchWas;
    private boolean searching;

    private HashMap<Integer, MessageObject> selectedFiles = new HashMap<>();
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private boolean scrolling;

    private long dialog_id;
    private int selectedMode;
    private int columnsCount = 4;

    private class SharedMediaData {
        private ArrayList<MessageObject> messages = new ArrayList<>();
        private HashMap<Integer, MessageObject> messagesDict = new HashMap<>();
        private ArrayList<String> sections = new ArrayList<>();
        private HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();
        private int totalCount;
        private boolean loading;
        private boolean endReached;
        private boolean cacheEndReached;
        private int max_id;

        public boolean addMessage(MessageObject messageObject, boolean isNew, boolean enc) {
            if (messagesDict.containsKey(messageObject.getId())) {
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
            messagesDict.put(messageObject.getId(), messageObject);
            if (!enc) {
                if (messageObject.getId() > 0) {
                    max_id = Math.min(messageObject.getId(), max_id);
                }
            } else {
                max_id = Math.max(messageObject.getId(), max_id);
            }
            return true;
        }

        public boolean deleteMessage(int mid) {
            MessageObject messageObject = messagesDict.get(mid);
            if (messageObject == null) {
                return false;
            }
            ArrayList<MessageObject> messageObjects = sectionArrays.get(messageObject.monthKey);
            if (messageObjects == null) {
                return false;
            }
            messageObjects.remove(messageObject);
            messages.remove(messageObject);
            messagesDict.remove(messageObject.getId());
            if (messageObjects.isEmpty()) {
                sectionArrays.remove(messageObject.monthKey);
                sections.remove(messageObject.monthKey);
            }
            totalCount--;
            return true;
        }

        public void replaceMid(int oldMid, int newMid) {
            MessageObject obj = messagesDict.get(oldMid);
            if (obj != null) {
                messagesDict.remove(oldMid);
                messagesDict.put(newMid, obj);
                obj.messageOwner.id = newMid;
            }
        }
    }

    private SharedMediaData sharedMediaData[] = new SharedMediaData[4];

    private final static int shared_media_item = 1;
    private final static int files_item = 2;
    private final static int links_item = 5;
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
            sharedMediaData[a].max_id = ((int)dialog_id) == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        sharedMediaData[0].loading = true;
        SharedMediaQuery.loadMedia(dialog_id, 0, 50, 0, SharedMediaQuery.MEDIA_PHOTOVIDEO, true, classGuid);
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("");
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (Build.VERSION.SDK_INT < 11 && listView != null) {
                        listView.setAdapter(null);
                        listView = null;
                        photoVideoAdapter = null;
                        documentsAdapter = null;
                        linksAdapter = null;
                    }
                    finishFragment();
                } else if (id == -2) {
                    selectedFiles.clear();
                    actionBar.hideActionMode();
                    listView.invalidateViews();
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
                } else if (id == delete) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("items", selectedFiles.size())));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ArrayList<Integer> ids = new ArrayList<>(selectedFiles.keySet());
                            ArrayList<Long> random_ids = null;
                            TLRPC.EncryptedChat currentEncryptedChat = null;
                            if ((int) dialog_id == 0) {
                                currentEncryptedChat = MessagesController.getInstance().getEncryptedChat((int) (dialog_id >> 32));
                            }
                            if (currentEncryptedChat != null) {
                                random_ids = new ArrayList<>();
                                for (HashMap.Entry<Integer, MessageObject> entry : selectedFiles.entrySet()) {
                                    MessageObject msg = entry.getValue();
                                    if (msg.messageOwner.random_id != 0 && msg.type != 10) {
                                        random_ids.add(msg.messageOwner.random_id);
                                    }
                                }
                            }
                            MessagesController.getInstance().deleteMessages(ids, random_ids, currentEncryptedChat);
                            actionBar.hideActionMode();
                            actionBar.closeSearchField();
                            selectedFiles.clear();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (id == forward) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putInt("dialogsType", 1);
                    DialogsActivity fragment = new DialogsActivity(args);
                    fragment.setDelegate(new DialogsActivity.MessagesActivityDelegate() {
                        @Override
                        public void didSelectDialog(DialogsActivity fragment, long did, boolean param) {
                            int lower_part = (int) did;
                            if (lower_part != 0) {
                                Bundle args = new Bundle();
                                args.putBoolean("scrollToTopOnResume", true);
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args.putInt("chat_id", -lower_part);
                                }

                                ArrayList<MessageObject> fmessages = new ArrayList<>();
                                ArrayList<Integer> ids = new ArrayList<>(selectedFiles.keySet());
                                Collections.sort(ids);
                                for (Integer id : ids) {
                                    if (id > 0) {
                                        fmessages.add(selectedFiles.get(id));
                                    }
                                }
                                selectedFiles.clear();
                                actionBar.hideActionMode();

                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                ChatActivity chatActivity = new ChatActivity(args);
                                presentFragment(chatActivity, true);
                                chatActivity.showReplyPanel(true, null, fmessages, null, false, false);

                                if (!AndroidUtilities.isTablet()) {
                                    removeSelfFromStack();
                                    Activity parentActivity = getParentActivity();
                                    if (parentActivity == null) {
                                        parentActivity = chatActivity.getParentActivity();
                                    }
                                    if (parentActivity != null) {
                                        parentActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                                    }
                                }
                            } else {
                                fragment.finishFragment();
                            }
                        }
                    });
                    presentFragment(fragment);
                }
            }
        });

        selectedFiles.clear();
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
                }
            }
        });
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setVisibility(View.GONE);

        dropDownContainer = new ActionBarMenuItem(context, menu, R.drawable.bar_selector);
        dropDownContainer.setSubMenuOpenSide(1);
        dropDownContainer.addSubItem(shared_media_item, LocaleController.getString("SharedMediaTitle", R.string.SharedMediaTitle), 0);
        dropDownContainer.addSubItem(files_item, LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle), 0);
        if ((int) dialog_id != 0) {
            dropDownContainer.addSubItem(links_item, LocaleController.getString("LinksTitle", R.string.LinksTitle), 0);
        }
        actionBar.addView(dropDownContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
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
        dropDown.setTextColor(0xffffffff);
        dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        dropDown.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0);
        dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        final ActionBarMenu actionMode = actionBar.createActionMode();
        actionModeViews.add(actionMode.addItem(-2, R.drawable.ic_ab_back_grey, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));

        selectedMessagesCountTextView = new TextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(0xff737373);
        selectedMessagesCountTextView.setSingleLine(true);
        selectedMessagesCountTextView.setLines(1);
        selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
        selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(11), 0, 0, AndroidUtilities.dp(2));
        selectedMessagesCountTextView.setGravity(Gravity.CENTER_VERTICAL);
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));

        if ((int) dialog_id != 0) {
            actionModeViews.add(actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
        }
        actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));

        photoVideoAdapter = new SharedPhotoVideoAdapter(context);
        documentsAdapter = new SharedDocumentsAdapter(context);
        documentsSearchAdapter = new MediaSearchAdapter(context, 1);
        linksSearchAdapter = new MediaSearchAdapter(context, 3);
        linksAdapter = new SharedLinksAdapter(context);

        FrameLayout frameLayout;
        fragmentView = frameLayout = new FrameLayout(context);

        listView = new SectionsListView(context);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setDrawSelectorOnTop(true);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                if (selectedMode == 1 && view instanceof SharedDocumentCell) {
                    MediaActivity.this.onItemClick(i, view, ((SharedDocumentCell) view).getDocument(), 0);
                } else if (selectedMode == 3 && view instanceof SharedLinkCell) {
                    MediaActivity.this.onItemClick(i, view, ((SharedLinkCell) view).getMessage(), 0);
                }
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
                scrolling = scrollState != SCROLL_STATE_IDLE;
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (searching && searchWas) {
                    return;
                }
                if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !sharedMediaData[selectedMode].loading && !sharedMediaData[selectedMode].endReached) {
                    sharedMediaData[selectedMode].loading = true;
                    int type;
                    if (selectedMode == 0) {
                        type = SharedMediaQuery.MEDIA_PHOTOVIDEO;
                    } else if (selectedMode == 1) {
                        type = SharedMediaQuery.MEDIA_FILE;
                    } else if (selectedMode == 2) {
                        type = SharedMediaQuery.MEDIA_AUDIO;
                    } else {
                        type = SharedMediaQuery.MEDIA_URL;
                    }
                    SharedMediaQuery.loadMedia(dialog_id, 0, 50, sharedMediaData[selectedMode].max_id, type, !sharedMediaData[selectedMode].cacheEndReached, classGuid);
                }
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int i, long id) {
                if (selectedMode == 1 && view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    MessageObject message = cell.getDocument();
                    return MediaActivity.this.onItemLongClick(message, view, 0);
                } else if (selectedMode == 3 && view instanceof SharedLinkCell) {
                    SharedLinkCell cell = (SharedLinkCell) view;
                    MessageObject message = cell.getMessage();
                    return MediaActivity.this.onItemLongClick(message, view, 0);
                }
                return false;
            }
        });

        for (int a = 0; a < 6; a++) {
            cellCache.add(new SharedPhotoVideoCell(context));
        }

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setBackgroundColor(0xfff0f0f0);
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
        emptyTextView.setTextColor(0xff8a8a8a);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
        emptyView.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));

        progressView = new LinearLayout(context);
        progressView.setGravity(Gravity.CENTER);
        progressView.setOrientation(LinearLayout.VERTICAL);
        progressView.setVisibility(View.GONE);
        progressView.setBackgroundColor(0xfff0f0f0);
        frameLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ProgressBar progressBar = new ProgressBar(context);
        progressView.addView(progressBar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        switchToCurrentSelectedMode();

        return fragmentView;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.mediaDidLoaded) {
            long uid = (Long) args[0];
            int guid = (Integer) args[4];
            int type = (Integer) args[5];
            if (uid == dialog_id && guid == classGuid) {
                sharedMediaData[type].loading = false;
                sharedMediaData[type].totalCount = (Integer) args[1];
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[2];
                boolean added = false;
                boolean enc = ((int) dialog_id) == 0;
                for (MessageObject message : arr) {
                    if (sharedMediaData[type].addMessage(message, false, enc)) {
                        added = true;
                    }
                }
                if (!added) {
                    sharedMediaData[type].endReached = true;
                }
                sharedMediaData[type].cacheEndReached = !(Boolean) args[3];
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (selectedMode == type && listView != null) {
                    if (listView.getEmptyView() == null) {
                        listView.setEmptyView(emptyView);
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
                }  else if (selectedMode == 3 && type == 3) {
                    if (linksAdapter != null) {
                        linksAdapter.notifyDataSetChanged();
                    }
                }
                if (selectedMode == 1 || selectedMode == 3) {
                    searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() && !searching ? View.VISIBLE : View.GONE);
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                for (SharedMediaData data : sharedMediaData) {
                    if (data.deleteMessage(ids)) {
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
                if (selectedMode == 1 || selectedMode == 3) {
                    searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() && !searching ? View.VISIBLE : View.GONE);
                }
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long uid = (Long) args[0];
            if (uid == dialog_id) {
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                boolean enc = ((int) dialog_id) == 0;
                boolean updated = false;
                for (MessageObject obj : arr) {
                    if (obj.messageOwner.media == null) {
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
                    if (selectedMode == 1 || selectedMode == 3) {
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

    @Override
    public void updatePhotoAtIndex(int index) {

    }

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
                        object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                        object.parentView = listView;
                        object.imageReceiver = imageView.getImageReceiver();
                        object.thumb = object.imageReceiver.getBitmap();
                        return object;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) { }

    @Override
    public void willHidePhotoViewer() { }

    @Override
    public boolean isPhotoChecked(int index) { return false; }

    @Override
    public void setPhotoChecked(int index) { }

    @Override
    public void cancelButtonPressed() { }

    @Override
    public void sendButtonPressed(int index) { }

    @Override
    public int getSelectedCount() { return 0; }

    private void switchToCurrentSelectedMode() {
        if (searching && searchWas) {
            if (listView != null) {
                if (selectedMode == 1) {
                    listView.setAdapter(documentsSearchAdapter);
                    documentsSearchAdapter.notifyDataSetChanged();
                } else if (selectedMode == 3) {
                    listView.setAdapter(linksSearchAdapter);
                    linksSearchAdapter.notifyDataSetChanged();
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
                emptyTextView.setText(LocaleController.getString("NoMedia", R.string.NoMedia));
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
            } else if (selectedMode == 1) {
                listView.setAdapter(documentsAdapter);
                dropDown.setText(LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle));
                emptyImageView.setImageResource(R.drawable.tip2);
                emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
                searchItem.setVisibility(!sharedMediaData[selectedMode].messages.isEmpty() ? View.VISIBLE : View.GONE);
                if (!sharedMediaData[selectedMode].loading && !sharedMediaData[selectedMode].endReached && sharedMediaData[selectedMode].messages.isEmpty()) {
                    sharedMediaData[selectedMode].loading = true;
                    SharedMediaQuery.loadMedia(dialog_id, 0, 50, 0, SharedMediaQuery.MEDIA_FILE, true, classGuid);
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
                emptyTextView.setText(LocaleController.getString("NoSharedLinks", R.string.NoSharedLinks));
                searchItem.setVisibility(!sharedMediaData[3].messages.isEmpty() ? View.VISIBLE : View.GONE);
                if (!sharedMediaData[selectedMode].loading && !sharedMediaData[selectedMode].endReached && sharedMediaData[selectedMode].messages.isEmpty()) {
                    sharedMediaData[selectedMode].loading = true;
                    SharedMediaQuery.loadMedia(dialog_id, 0, 50, 0, SharedMediaQuery.MEDIA_URL, true, classGuid);
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
        selectedFiles.put(item.getId(), item);
        selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
        if (Build.VERSION.SDK_INT >= 11) {
            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            ArrayList<Object> animators = new ArrayList<>();
            for (int i = 0; i < actionModeViews.size(); i++) {
                View view2 = actionModeViews.get(i);
                AndroidUtilities.clearDrawableAnimation(view2);
                if (i < 1) {
                    animators.add(ObjectAnimatorProxy.ofFloat(view2, "translationX", -AndroidUtilities.dp(56), 0));
                } else {
                    animators.add(ObjectAnimatorProxy.ofFloat(view2, "scaleY", 0.1f, 1.0f));
                }
            }
            animatorSet.playTogether(animators);
            animatorSet.setDuration(250);
            animatorSet.start();
        }
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
            if (selectedFiles.containsKey(message.getId())) {
                selectedFiles.remove(message.getId());
            } else {
                selectedFiles.put(message.getId(), message);
            }
            if (selectedFiles.isEmpty()) {
                actionBar.hideActionMode();
            } else {
                selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
            }
            scrolling = false;
            if (view instanceof SharedDocumentCell) {
                ((SharedDocumentCell) view).setChecked(selectedFiles.containsKey(message.getId()), true);
            } else if (view instanceof SharedPhotoVideoCell) {
                ((SharedPhotoVideoCell) view).setChecked(a, selectedFiles.containsKey(message.getId()), true);
            } else if (view instanceof SharedLinkCell) {
                ((SharedLinkCell) view).setChecked(selectedFiles.containsKey(message.getId()), true);
            }
        } else {
            if (selectedMode == 0) {
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, index, this);
            } else if (selectedMode == 1) {
                if (view instanceof SharedDocumentCell) {
                    SharedDocumentCell cell = (SharedDocumentCell) view;
                    if (cell.isLoaded()) {
                        if (message.isMusic()) {
                            if (MediaController.getInstance().setPlaylist(sharedMediaData[1].messages, message)) {
                                return;
                            }
                        }
                        File f = null;
                        String fileName = FileLoader.getAttachFileName(message.messageOwner.media.document);
                        if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                            f = new File(message.messageOwner.attachPath);
                        }
                        if (f == null || f != null && !f.exists()) {
                            f = FileLoader.getPathToMessage(message.messageOwner);
                        }
                        if (f != null && f.exists()) {
                            String realMimeType = null;
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                MimeTypeMap myMime = MimeTypeMap.getSingleton();
                                int idx = fileName.lastIndexOf(".");
                                if (idx != -1) {
                                    String ext = fileName.substring(idx + 1);
                                    realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                                    if (realMimeType == null) {
                                        realMimeType = message.messageOwner.media.document.mime_type;
                                        if (realMimeType == null || realMimeType.length() == 0) {
                                            realMimeType = null;
                                        }
                                    }
                                    if (realMimeType != null) {
                                        intent.setDataAndType(Uri.fromFile(f), realMimeType);
                                    } else {
                                        intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                    }
                                } else {
                                    intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                }
                                if (realMimeType != null) {
                                    try {
                                        getParentActivity().startActivityForResult(intent, 500);
                                    } catch (Exception e) {
                                        intent.setDataAndType(Uri.fromFile(f), "text/plain");
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
                                builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.messageOwner.media.document.mime_type));
                                showDialog(builder.create());
                            }
                        }
                    } else if (!cell.isLoading()) {
                        FileLoader.getInstance().loadFile(cell.getDocument().messageOwner.media.document, true, false);
                        cell.updateFileExistIcon();
                    } else {
                        FileLoader.getInstance().cancelLoadFile(cell.getDocument().messageOwner.media.document);
                        cell.updateFileExistIcon();
                    }
                }
            } else if (selectedMode == 3) {
                try {
                    TLRPC.WebPage webPage = message.messageOwner.media.webpage;
                    String link = null;
                    if (webPage != null && !(webPage instanceof TLRPC.TL_webPageEmpty)) {
                        if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
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
                        Uri uri = Uri.parse(link);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, getParentActivity().getPackageName());
                        getParentActivity().startActivity(intent);
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    private void openWebView(TLRPC.WebPage webPage) {
        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
        builder.setCustomView(new WebFrameLayout(getParentActivity(), builder.create(), webPage.title, webPage.url, webPage.embed_url, webPage.embed_width, webPage.embed_height));
        builder.setUseFullWidth(true);
        showDialog(builder.create());
    }

    private void fixLayoutInternal() {
        if (listView == null) {
            return;
        }
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

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

    private class SharedLinksAdapter extends BaseSectionsAdapter {
        private Context mContext;

        public SharedLinksAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isRowEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[3].sections.size() + (sharedMediaData[3].sections.isEmpty() || sharedMediaData[3].endReached ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[3].sections.size()) {
                return sharedMediaData[3].sectionArrays.get(sharedMediaData[3].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new GreySectionCell(mContext);
            }
            if (section < sharedMediaData[3].sections.size()) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((GreySectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return convertView;
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            if (section < sharedMediaData[3].sections.size()) {
                String name = sharedMediaData[3].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[3].sectionArrays.get(name);
                if (position == 0) {
                    if (convertView == null) {
                        convertView = new GreySectionCell(mContext);
                    }
                    MessageObject messageObject = messageObjects.get(0);
                    ((GreySectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                } else {
                    if (convertView == null) {
                        convertView = new SharedLinkCell(mContext);
                        ((SharedLinkCell) convertView).setDelegate(new SharedLinkCell.SharedLinkCellDelegate() {
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
                    SharedLinkCell sharedLinkCell = (SharedLinkCell) convertView;
                    MessageObject messageObject = messageObjects.get(position - 1);
                    sharedLinkCell.setLink(messageObject, position != messageObjects.size() || section == sharedMediaData[3].sections.size() - 1 && sharedMediaData[3].loading);
                    if (actionBar.isActionModeShowed()) {
                        sharedLinkCell.setChecked(selectedFiles.containsKey(messageObject.getId()), !scrolling);
                    } else {
                        sharedLinkCell.setChecked(false, !scrolling);
                    }
                }
            } else {
                if (convertView == null) {
                    convertView = new LoadingCell(mContext);
                }
            }
            return convertView;
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
        public int getViewTypeCount() {
            return 3;
        }
    }

    private class SharedDocumentsAdapter extends BaseSectionsAdapter {
        private Context mContext;

        public SharedDocumentsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isRowEnabled(int section, int row) {
            return row != 0;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[1].sections.size() + (sharedMediaData[1].sections.isEmpty() || sharedMediaData[1].endReached ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[1].sections.size()) {
                return sharedMediaData[1].sectionArrays.get(sharedMediaData[1].sections.get(section)).size() + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new GreySectionCell(mContext);
            }
            if (section < sharedMediaData[1].sections.size()) {
                String name = sharedMediaData[1].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[1].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((GreySectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return convertView;
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            if (section < sharedMediaData[1].sections.size()) {
                String name = sharedMediaData[1].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[1].sectionArrays.get(name);
                if (position == 0) {
                    if (convertView == null) {
                        convertView = new GreySectionCell(mContext);
                    }
                    MessageObject messageObject = messageObjects.get(0);
                    ((GreySectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                } else {
                    if (convertView == null) {
                        convertView = new SharedDocumentCell(mContext);
                    }
                    SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) convertView;
                    MessageObject messageObject = messageObjects.get(position - 1);
                    sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() || section == sharedMediaData[1].sections.size() - 1 && sharedMediaData[1].loading);
                    if (actionBar.isActionModeShowed()) {
                        sharedDocumentCell.setChecked(selectedFiles.containsKey(messageObject.getId()), !scrolling);
                    } else {
                        sharedDocumentCell.setChecked(false, !scrolling);
                    }
                }
            } else {
                if (convertView == null) {
                    convertView = new LoadingCell(mContext);
                }
            }
            return convertView;
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section < sharedMediaData[1].sections.size()) {
                if (position == 0) {
                    return 0;
                } else {
                    return 1;
                }
            }
            return 2;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }
    }

    private class SharedPhotoVideoAdapter extends BaseSectionsAdapter {
        private Context mContext;

        public SharedPhotoVideoAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public boolean isRowEnabled(int section, int row) {
            return false;
        }

        @Override
        public int getSectionCount() {
            return sharedMediaData[0].sections.size() + (sharedMediaData[0].sections.isEmpty() || sharedMediaData[0].endReached ? 0 : 1);
        }

        @Override
        public int getCountForSection(int section) {
            if (section < sharedMediaData[0].sections.size()) {
                return (int) Math.ceil(sharedMediaData[0].sectionArrays.get(sharedMediaData[0].sections.get(section)).size() / (float)columnsCount) + 1;
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new SharedMediaSectionCell(mContext);
                convertView.setBackgroundColor(0xffffffff);
            }
            if (section < sharedMediaData[0].sections.size()) {
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                MessageObject messageObject = messageObjects.get(0);
                ((SharedMediaSectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
            }
            return convertView;
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            if (section < sharedMediaData[0].sections.size()) {
                String name = sharedMediaData[0].sections.get(section);
                ArrayList<MessageObject> messageObjects = sharedMediaData[0].sectionArrays.get(name);
                if (position == 0) {
                    if (convertView == null) {
                        convertView = new SharedMediaSectionCell(mContext);
                    }
                    MessageObject messageObject = messageObjects.get(0);
                    ((SharedMediaSectionCell) convertView).setText(LocaleController.formatterMonthYear.format((long) messageObject.messageOwner.date * 1000).toUpperCase());
                } else {
                    SharedPhotoVideoCell cell;
                    if (convertView == null) {
                        if (!cellCache.isEmpty()) {
                            convertView = cellCache.get(0);
                            cellCache.remove(0);
                        } else {
                            convertView = new SharedPhotoVideoCell(mContext);
                        }
                        cell = (SharedPhotoVideoCell) convertView;
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
                    } else {
                        cell = (SharedPhotoVideoCell) convertView;
                    }
                    cell.setItemsCount(columnsCount);
                    for (int a = 0; a < columnsCount; a++) {
                        int index = (position - 1) * columnsCount + a;
                        if (index < messageObjects.size()) {
                            MessageObject messageObject = messageObjects.get(index);
                            cell.setIsFirst(position == 1);
                            cell.setItem(a, sharedMediaData[0].messages.indexOf(messageObject), messageObject);

                            if (actionBar.isActionModeShowed()) {
                                cell.setChecked(a, selectedFiles.containsKey(messageObject.getId()), !scrolling);
                            } else {
                                cell.setChecked(a, false, !scrolling);
                            }
                        } else {
                            cell.setItem(a, index, null);
                        }
                    }
                    cell.requestLayout();
                }
            } else {
                if (convertView == null) {
                    convertView = new LoadingCell(mContext);
                }
            }
            return convertView;
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
        public int getViewTypeCount() {
            return 3;
        }
    }

    public class MediaSearchAdapter extends BaseFragmentAdapter {
        private Context mContext;
        private ArrayList<MessageObject> searchResult = new ArrayList<>();
        private Timer searchTimer;
        protected ArrayList<MessageObject> globalSearch = new ArrayList<>();
        private long reqId = 0;
        private int lastReqId;
        private int currentType;

        public MediaSearchAdapter(Context context, int type) {
            mContext = context;
            currentType = type;
        }

        public void queryServerSearch(final String query, final int max_id) {
            int uid = (int) dialog_id;
            if (uid == 0) {
                return;
            }
            if (reqId != 0) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                reqId = 0;
            }
            if (query == null || query.length() == 0) {
                globalSearch.clear();
                lastReqId = 0;
                notifyDataSetChanged();
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = 0;
            req.limit = 50;
            req.max_id = max_id;
            if (currentType == 1) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (currentType == 3) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            }
            req.q = query;
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -uid;
            } else {
                TLRPC.User user = MessagesController.getInstance().getUser(uid);
                if (user == null) {
                    return;
                }
                req.peer = new TLRPC.TL_inputPeerUser();
                req.peer.access_hash = user.access_hash;
                req.peer.user_id = uid;
            }
            final int currentReqId = ++lastReqId;
            reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    final ArrayList<MessageObject> messageObjects = new ArrayList<>();
                    if (error == null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        for (TLRPC.Message message : res.messages) {
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
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }

        public void search(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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
                            FileLog.e("tmessages", e);
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
                        if (currentType == 1) {
                            MessageObject messageObject = sharedMediaData[currentType].messages.get(sharedMediaData[currentType].messages.size() - 1);
                            queryServerSearch(query, messageObject.getId());
                        } else if (currentType == 3) {
                            queryServerSearch(query, 0);
                        }
                    }
                    if (currentType == 1) {
                        final ArrayList<MessageObject> copy = new ArrayList<>();
                        copy.addAll(sharedMediaData[1].messages);
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

                                for (MessageObject messageObject : copy) {
                                    for (String q : search) {
                                        String name = messageObject.getDocumentName();
                                        if (name == null || name.length() == 0) {
                                            continue;
                                        }
                                        name = name.toLowerCase();
                                        if (name.contains(q)) {
                                            resultArray.add(messageObject);
                                            break;
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
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != searchResult.size() + globalSearch.size();
        }

        @Override
        public int getCount() {
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

        @Override
        public MessageObject getItem(int i) {
            if (i < searchResult.size()) {
                return searchResult.get(i);
            } else {
                return globalSearch.get(i - searchResult.size());
            }
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (currentType == 1) {
                if (view == null) {
                    view = new SharedDocumentCell(mContext);
                }
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) view;
                MessageObject messageObject = getItem(i);
                sharedDocumentCell.setDocument(messageObject, i != getCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedDocumentCell.setChecked(selectedFiles.containsKey(messageObject.getId()), !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (currentType == 3) {
                if (view == null) {
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
                SharedLinkCell sharedLinkCell = (SharedLinkCell) view;
                MessageObject messageObject = getItem(i);
                sharedLinkCell.setLink(messageObject, i != getCount() - 1);
                if (actionBar.isActionModeShowed()) {
                    sharedLinkCell.setChecked(selectedFiles.containsKey(messageObject.getId()), !scrolling);
                } else {
                    sharedLinkCell.setChecked(false, !scrolling);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return searchResult.isEmpty() && globalSearch.isEmpty();
        }
    }
}

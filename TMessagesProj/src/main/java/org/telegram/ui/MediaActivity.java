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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.android.query.SharedMediaQuery;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessageObject;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class MediaActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

    private GridView listView;
    private ListView mediaListView;
    private ListAdapter listAdapter;
    private SharedDocumentsAdapter documentsAdapter;
    private LinearLayout progressView;
    private TextView emptyTextView;
    private ImageView emptyImageView;
    private LinearLayout emptyView;
    private TextView dropDown;
    private ActionBarMenuItem dropDownContainer;
    private ActionBarMenuItem searchItem;
    private TextView selectedMessagesCountTextView;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;

    private HashMap<Integer, MessageObject> selectedFiles = new HashMap<>();
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private boolean scrolling;

    private long dialog_id;
    private int selectedMode;
    private int itemWidth = 100;

    private class SharedMediaData {
        private ArrayList<MessageObject> messages = new ArrayList<>();
        private HashMap<Integer, MessageObject> messagesDict = new HashMap<>();
        private int totalCount;
        private boolean loading;
        private boolean endReached;
        private boolean cacheEndReached;
        private int max_id;
    }

    private SharedMediaData sharedMediaData[] = new SharedMediaData[3];

    private final static int shared_media_item = 1;
    private final static int files_item = 2;
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
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
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
                            listAdapter = null;
                        }
                        finishFragment();
                    } else if (id == -2) {
                        selectedFiles.clear();
                        actionBar.hideActionMode();
                        mediaListView.invalidateViews();
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
                    } else if (id == delete) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.formatString("AreYouSureDeleteMessages", R.string.AreYouSureDeleteMessages, LocaleController.formatPluralString("files", selectedFiles.size())));
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
                                selectedFiles.clear();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (id == forward) {
                        Bundle args = new Bundle();
                        args.putBoolean("onlySelect", true);
                        args.putBoolean("serverOnly", true);
                        args.putString("selectAlertString", LocaleController.getString("ForwardMessagesTo", R.string.ForwardMessagesTo));
                        args.putString("selectAlertStringGroup", LocaleController.getString("ForwardMessagesToGroup", R.string.ForwardMessagesToGroup));
                        MessagesActivity fragment = new MessagesActivity(args);
                        fragment.setDelegate(new MessagesActivity.MessagesActivityDelegate() {
                            @Override
                            public void didSelectDialog(MessagesActivity fragment, long did, boolean param) {
                                int lower_part = (int)did;
                                if (lower_part != 0) {
                                    Bundle args = new Bundle();
                                    args.putBoolean("scrollToTopOnResume", true);
                                    if (lower_part > 0) {
                                        args.putInt("user_id", lower_part);
                                    } else if (lower_part < 0) {
                                        args.putInt("chat_id", -lower_part);
                                    }

                                    ArrayList<Integer> ids = new ArrayList<>(selectedFiles.keySet());
                                    Collections.sort(ids);
                                    for (Integer id : ids) {
                                        if (id > 0) {
                                            SendMessagesHelper.getInstance().sendMessage(selectedFiles.get(id), did);
                                        }
                                    }
                                    selectedFiles.clear();
                                    actionBar.hideActionMode();

                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.closeChats);
                                    ChatActivity chatActivity = new ChatActivity(args);
                                    presentFragment(chatActivity, true);

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
                }

                @Override
                public void onSearchCollapse() {
                    dropDownContainer.setVisibility(View.VISIBLE);
                }

                @Override
                public void onTextChanged(EditText editText) {

                }
            });
            searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
            searchItem.setVisibility(View.GONE);

            dropDownContainer = new ActionBarMenuItem(getParentActivity(), menu, R.drawable.bar_selector);
            dropDownContainer.setSubMenuOpenSide(1);
            dropDownContainer.addSubItem(shared_media_item, LocaleController.getString("SharedMedia", R.string.SharedMedia), 0);
            dropDownContainer.addSubItem(files_item, LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle), 0);
            actionBar.addView(dropDownContainer);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.rightMargin = AndroidUtilities.dp(40);
            layoutParams.leftMargin = AndroidUtilities.isTablet() ? AndroidUtilities.dp(64) : AndroidUtilities.dp(56);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            dropDownContainer.setLayoutParams(layoutParams);
            dropDownContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dropDownContainer.toggleSubMenu();
                }
            });

            dropDown = new TextView(getParentActivity());
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
            dropDownContainer.addView(dropDown);
            layoutParams = (FrameLayout.LayoutParams) dropDown.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.leftMargin = AndroidUtilities.dp(16);
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            dropDown.setLayoutParams(layoutParams);

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
            actionMode.addView(selectedMessagesCountTextView);
            LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams)selectedMessagesCountTextView.getLayoutParams();
            layoutParams1.weight = 1;
            layoutParams1.width = 0;
            layoutParams1.height = LinearLayout.LayoutParams.MATCH_PARENT;
            selectedMessagesCountTextView.setLayoutParams(layoutParams1);

            if ((int) dialog_id != 0) {
                actionModeViews.add(actionMode.addItem(forward, R.drawable.ic_ab_fwd_forward, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));
            }
            actionModeViews.add(actionMode.addItem(delete, R.drawable.ic_ab_fwd_delete, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));


            FrameLayout frameLayout;
            fragmentView = frameLayout = new FrameLayout(getParentActivity());
            fragmentView.setBackgroundColor(0xfff0f0f0);

            mediaListView = new ListView(getParentActivity());
            mediaListView.setDivider(null);
            mediaListView.setDividerHeight(0);
            mediaListView.setVerticalScrollBarEnabled(false);
            mediaListView.setDrawSelectorOnTop(true);
            frameLayout.addView(mediaListView);
            layoutParams = (FrameLayout.LayoutParams) mediaListView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            mediaListView.setLayoutParams(layoutParams);
            mediaListView.setAdapter(documentsAdapter = new SharedDocumentsAdapter(getParentActivity()));
            mediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (view instanceof SharedDocumentCell) {
                        SharedDocumentCell cell = (SharedDocumentCell) view;
                        MessageObject message = cell.getDocument();
                        if (actionBar.isActionModeShowed()) {
                            if (selectedFiles.containsKey(message.messageOwner.id)) {
                                selectedFiles.remove(message.messageOwner.id);
                            } else {
                                selectedFiles.put(message.messageOwner.id, message);
                            }
                            if (selectedFiles.isEmpty()) {
                                actionBar.hideActionMode();
                            } else {
                                selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
                            }
                            scrolling = false;
                            if (view instanceof SharedDocumentCell) {
                                ((SharedDocumentCell) view).setChecked(selectedFiles.containsKey(message.messageOwner.id), true);
                            }
                        } else {
                            if (cell.isLoaded()) {
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
                                                getParentActivity().startActivity(intent);
                                            } catch (Exception e) {
                                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                                                getParentActivity().startActivity(intent);
                                            }
                                        } else {
                                            getParentActivity().startActivity(intent);
                                        }
                                    } catch (Exception e) {
                                        if (getParentActivity() == null) {
                                            return;
                                        }
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                        builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.messageOwner.media.document.mime_type));
                                        showAlertDialog(builder);
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
                    }
                }
            });
            mediaListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    scrolling = scrollState != SCROLL_STATE_IDLE;
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !sharedMediaData[1].loading && !sharedMediaData[1].endReached) {
                        sharedMediaData[1].loading = true;
                        SharedMediaQuery.loadMedia(dialog_id, 0, 50, sharedMediaData[1].max_id, SharedMediaQuery.MEDIA_FILE, !sharedMediaData[1].cacheEndReached, classGuid);
                    }
                }
            });
            mediaListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int i, long id) {
                    if (actionBar.isActionModeShowed() || i < 0 || i >= sharedMediaData[1].messages.size()) {
                        return false;
                    }
                    MessageObject item = sharedMediaData[1].messages.get(i);
                    selectedFiles.put(item.messageOwner.id, item);
                    selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
                    if (Build.VERSION.SDK_INT >= 11) {
                        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                        ArrayList<Object> animators = new ArrayList<>();
                        for (int a = 0; a < actionModeViews.size(); a++) {
                            View view2 = actionModeViews.get(a);
                            AndroidUtilities.clearDrawableAnimation(view2);
                            if (a < 1) {
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
                    }
                    actionBar.showActionMode();
                    return true;
                }
            });

            listView = new GridView(getParentActivity());
            listView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), AndroidUtilities.dp(2));
            listView.setClipToPadding(false);
            listView.setDrawSelectorOnTop(true);
            listView.setVerticalSpacing(AndroidUtilities.dp(4));
            listView.setHorizontalSpacing(AndroidUtilities.dp(4));
            listView.setSelector(R.drawable.list_selector);
            listView.setGravity(Gravity.CENTER);
            listView.setNumColumns(GridView.AUTO_FIT);
            listView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            frameLayout.addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter = new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i < 0 || i >= sharedMediaData[selectedMode].messages.size()) {
                        return;
                    }
                    if (selectedMode == 0) {
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        PhotoViewer.getInstance().openPhoto(sharedMediaData[selectedMode].messages, i, MediaActivity.this);
                    } else if (selectedMode == 1) {

                    }
                }
            });
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {

                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !sharedMediaData[0].loading && !sharedMediaData[0].endReached) {
                        sharedMediaData[0].loading = true;
                        SharedMediaQuery.loadMedia(dialog_id, 0, 50, sharedMediaData[0].max_id, SharedMediaQuery.MEDIA_PHOTOVIDEO, !sharedMediaData[0].cacheEndReached, classGuid);
                    }
                }
            });

            emptyView = new LinearLayout(getParentActivity());
            emptyView.setOrientation(LinearLayout.VERTICAL);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setVisibility(View.GONE);
            frameLayout.addView(emptyView);
            layoutParams = (FrameLayout.LayoutParams) emptyView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            emptyView.setLayoutParams(layoutParams);
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            emptyImageView = new ImageView(getParentActivity());
            emptyView.addView(emptyImageView);
            layoutParams1 = (LinearLayout.LayoutParams) emptyImageView.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            emptyImageView.setLayoutParams(layoutParams1);

            emptyTextView = new TextView(getParentActivity());
            emptyTextView.setTextColor(0xff8a8a8a);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
            emptyView.addView(emptyTextView);
            layoutParams1 = (LinearLayout.LayoutParams) emptyTextView.getLayoutParams();
            layoutParams1.topMargin = AndroidUtilities.dp(24);
            layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.gravity = Gravity.CENTER;
            emptyTextView.setLayoutParams(layoutParams1);

            progressView = new LinearLayout(getParentActivity());
            progressView.setGravity(Gravity.CENTER);
            progressView.setOrientation(LinearLayout.VERTICAL);
            progressView.setVisibility(View.GONE);
            frameLayout.addView(progressView);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            progressView.setLayoutParams(layoutParams);

            ProgressBar progressBar = new ProgressBar(getParentActivity());
            progressView.addView(progressBar);
            layoutParams1 = (LinearLayout.LayoutParams) progressBar.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            progressBar.setLayoutParams(layoutParams1);

            switchToCurrentSelectedMode();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
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
                    if (!sharedMediaData[type].messagesDict.containsKey(message.messageOwner.id)) {
                        if (!enc) {
                            if (message.messageOwner.id > 0) {
                                sharedMediaData[type].max_id = Math.min(message.messageOwner.id, sharedMediaData[type].max_id);
                            }
                        } else {
                            sharedMediaData[type].max_id = Math.max(message.messageOwner.id, sharedMediaData[type].max_id);
                        }
                        sharedMediaData[type].messagesDict.put(message.messageOwner.id, message);
                        sharedMediaData[type].messages.add(message);
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
                if (type == 0) {
                    if (listView != null) {
                        if (listView.getEmptyView() == null) {
                            listView.setEmptyView(emptyView);
                        }
                    }
                } else if (type == 1) {
                    if (mediaListView != null) {
                        if (mediaListView.getEmptyView() == null) {
                            mediaListView.setEmptyView(emptyView);
                        }
                    }
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                if (documentsAdapter != null) {
                    scrolling = true;
                    documentsAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.messagesDeleted) {
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            boolean updated = false;
            for (Integer ids : markAsDeletedMessages) {
                for (SharedMediaData data : sharedMediaData) {
                    MessageObject obj = data.messagesDict.get(ids);
                    if (obj != null) {
                        data.messages.remove(obj);
                        data.messagesDict.remove(ids);
                        data.totalCount--;
                        updated = true;
                    }
                }
            }
            if (updated && listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (documentsAdapter != null) {
                scrolling = true;
                documentsAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long uid = (Long) args[0];
            if (uid == dialog_id) {
                boolean markAsRead = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];

                for (MessageObject obj : arr) {
                    if (obj.messageOwner.media == null) {
                        continue;
                    }
                    int type = SharedMediaQuery.getMediaType(obj.messageOwner);
                    if (type == -1) {
                        return;
                    }
                    if (sharedMediaData[type].messagesDict.containsKey(obj.messageOwner.id)) {
                        continue;
                    }
                    boolean enc = ((int) dialog_id) == 0;
                    if (!enc) {
                        if (obj.messageOwner.id > 0) {
                            sharedMediaData[type].max_id = Math.min(obj.messageOwner.id, sharedMediaData[type].max_id);
                        }
                    } else {
                        sharedMediaData[type].max_id = Math.max(obj.messageOwner.id, sharedMediaData[type].max_id);
                    }
                    sharedMediaData[type].messagesDict.put(obj.messageOwner.id, obj);
                    sharedMediaData[type].messages.add(0, obj);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                if (documentsAdapter != null) {
                    scrolling = true;
                    documentsAdapter.notifyDataSetChanged();
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer) args[0];
            for (SharedMediaData data : sharedMediaData) {
                MessageObject obj = data.messagesDict.get(msgId);
                if (obj != null) {
                    Integer newMsgId = (Integer) args[1];
                    data.messagesDict.remove(msgId);
                    data.messagesDict.put(newMsgId, obj);
                    obj.messageOwner.id = newMsgId;
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (documentsAdapter != null) {
            scrolling = true;
            documentsAdapter.notifyDataSetChanged();
        }
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (messageObject == null || listView == null) {
            return null;
        }
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
            if (imageView != null) {
                int num = (Integer)imageView.getTag();
                if (num < 0 || num >= sharedMediaData[0].messages.size()) {
                    continue;
                }
                MessageObject message = sharedMediaData[0].messages.get(num);
                if (message != null && message.messageOwner.id == messageObject.messageOwner.id) {
                    int coords[] = new int[2];
                    imageView.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                    object.parentView = listView;
                    object.imageReceiver = imageView.imageReceiver;
                    object.thumb = object.imageReceiver.getBitmap();
                    return object;
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
        if (selectedMode == 0) {
            mediaListView.setEmptyView(null);
            mediaListView.setVisibility(View.GONE);
            mediaListView.setAdapter(null);

            listView.setAdapter(listAdapter);

            dropDown.setText(LocaleController.getString("SharedMedia", R.string.SharedMedia));
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
        } else if (selectedMode == 1) {
            listView.setEmptyView(null);
            listView.setVisibility(View.GONE);
            listView.setAdapter(null);

            mediaListView.setAdapter(documentsAdapter);

            dropDown.setText(LocaleController.getString("DocumentsTitle", R.string.DocumentsTitle));
            int lower_id = (int) dialog_id;
            emptyImageView.setImageResource(R.drawable.tip2);
            emptyTextView.setText(LocaleController.getString("NoSharedFiles", R.string.NoSharedFiles));
            //searchItem.setVisibility(View.VISIBLE);
            if (!sharedMediaData[1].loading && !sharedMediaData[1].endReached && sharedMediaData[1].messages.isEmpty()) {
                sharedMediaData[selectedMode].loading = true;
                SharedMediaQuery.loadMedia(dialog_id, 0, 50, 0, SharedMediaQuery.MEDIA_FILE, true, classGuid);
            }
            mediaListView.setVisibility(View.VISIBLE);

            if (sharedMediaData[selectedMode].loading && sharedMediaData[selectedMode].messages.isEmpty()) {
                progressView.setVisibility(View.VISIBLE);
                mediaListView.setEmptyView(null);
                emptyView.setVisibility(View.GONE);
            } else {
                progressView.setVisibility(View.GONE);
                mediaListView.setEmptyView(emptyView);
            }
        }
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                    int rotation = manager.getDefaultDisplay().getRotation();

                    if (AndroidUtilities.isTablet()) {
                        listView.setNumColumns(4);
                        itemWidth = AndroidUtilities.dp(490) / 4 - AndroidUtilities.dp(2) * 3;
                        listView.setColumnWidth(itemWidth);
                        emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
                    } else {
                        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                            listView.setNumColumns(6);
                            itemWidth = AndroidUtilities.displaySize.x / 6 - AndroidUtilities.dp(2) * 5;
                            listView.setColumnWidth(itemWidth);
                            emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
                        } else {
                            listView.setNumColumns(4);
                            itemWidth = AndroidUtilities.displaySize.x / 4 - AndroidUtilities.dp(2) * 3;
                            listView.setColumnWidth(itemWidth);
                            emptyTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), AndroidUtilities.dp(128));
                        }
                    }
                    listView.setPadding(listView.getPaddingLeft(), AndroidUtilities.dp(4), listView.getPaddingRight(), listView.getPaddingBottom());
                    listAdapter.notifyDataSetChanged();
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);

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
                    return false;
                }
            });
        }
    }

    private class SharedDocumentsAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public SharedDocumentsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != sharedMediaData[1].messages.size();
        }

        @Override
        public int getCount() {
            return sharedMediaData[1].messages.size() + (sharedMediaData[1].messages.isEmpty() || sharedMediaData[1].endReached ? 0 : 1);
        }

        @Override
        public Object getItem(int i) {
            return null;
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
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new SharedDocumentCell(mContext);
                }
                SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) view;
                sharedDocumentCell.setDocument(sharedMediaData[1].messages.get(i), i != sharedMediaData[1].messages.size() - 1 || sharedMediaData[1].loading);
                if (actionBar.isActionModeShowed()) {
                    sharedDocumentCell.setChecked(selectedFiles.containsKey(sharedMediaData[1].messages.get(i).messageOwner.id), !scrolling);
                } else {
                    sharedDocumentCell.setChecked(false, !scrolling);
                }
            } else if (type == 1) {
                if (view == null) {
                    view = new LoadingCell(mContext);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == sharedMediaData[1].messages.size()) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return sharedMediaData[1].messages.isEmpty();
        }
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != sharedMediaData[0].messages.size();
        }

        @Override
        public int getCount() {
            return sharedMediaData[0].messages.size() + (sharedMediaData[0].messages.isEmpty() || sharedMediaData[0].endReached ? 0 : 1);
        }

        @Override
        public Object getItem(int i) {
            return null;
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
            int type = getItemViewType(i);
            if (type == 0) {
                MessageObject message = sharedMediaData[0].messages.get(i);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_photo_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                imageView.setTag(i);

                imageView.imageReceiver.setParentMessageObject(message);
                imageView.imageReceiver.setNeedsQualityThumb(true);
                imageView.imageReceiver.setShouldGenerateQualityThumb(true);
                if (message.messageOwner.media != null && message.messageOwner.media.photo != null && !message.messageOwner.media.photo.sizes.isEmpty()) {
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 80);
                    imageView.setImage(null, null, null, mContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, photoSize.location, "b", 0);
                } else {
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
                imageView.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
            } else if (type == 1) {
                MessageObject message = sharedMediaData[0].messages.get(i);
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_video_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                TextView textView = (TextView)view.findViewById(R.id.chat_video_time);
                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                imageView.setTag(i);

                imageView.imageReceiver.setParentMessageObject(message);
                imageView.imageReceiver.setNeedsQualityThumb(true);
                imageView.imageReceiver.setShouldGenerateQualityThumb(true);
                if (message.messageOwner.media.video != null && message.messageOwner.media.video.thumb != null) {
                    int duration = message.messageOwner.media.video.duration;
                    int minutes = duration / 60;
                    int seconds = duration - minutes * 60;
                    textView.setText(String.format("%d:%02d", minutes, seconds));
                    TLRPC.FileLocation location = message.messageOwner.media.video.thumb.location;
                    imageView.setImage(null, null, null, mContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, location, "b", 0);
                    textView.setVisibility(View.VISIBLE);
                } else {
                    textView.setVisibility(View.GONE);
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
                imageView.imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(message), false);
            } else if (type == 2) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.media_loading_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == sharedMediaData[0].messages.size()) {
                return 2;
            }
            MessageObject message = sharedMediaData[0].messages.get(i);
            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return sharedMediaData[0].messages.isEmpty();
        }
    }
}

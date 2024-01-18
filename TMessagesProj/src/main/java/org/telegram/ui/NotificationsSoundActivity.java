package org.telegram.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.ringtone.RingtoneDataStore;
import org.telegram.messenger.ringtone.RingtoneUploader;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class NotificationsSoundActivity extends BaseFragment implements ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate, NotificationCenter.NotificationCenterDelegate {

    ArrayList<Tone> serverTones = new ArrayList<>();
    ArrayList<Tone> systemTones = new ArrayList<>();
    ArrayList<Tone> uploadingTones = new ArrayList<>();

    NumberTextView selectedTonesCountTextView;
    RecyclerListView listView;
    Adapter adapter;
    Theme.ResourcesProvider resourcesProvider;

    int rowCount;
    int serverTonesHeaderRow;
    int serverTonesStartRow;
    int serverTonesEndRow;

    int uploadRow;

    int dividerRow;
    int dividerRow2;

    int systemTonesHeaderRow;
    int systemTonesStartRow;
    int systemTonesEndRow;

    private int stableIds = 100;

    Tone selectedTone;
    boolean selectedToneChanged;

    SparseArray<Tone> selectedTones = new SparseArray<>();
    private final static int deleteId = 1;
    private final static int shareId = 2;

    ChatAvatarContainer avatarContainer;
    long dialogId;
    int currentType = -1;


    private Tone startSelectedTone;
    ChatAttachAlert chatAttachAlert;
    Ringtone lastPlayedRingtone;

    private final int tonesStreamType = AudioManager.STREAM_ALARM;

    long topicId = 0;

    public NotificationsSoundActivity(Bundle args) {
        this(args, null);
    }

    public NotificationsSoundActivity(Bundle args, Theme.ResourcesProvider resourcesProvider) {
        super(args);
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public boolean onFragmentCreate() {
        if (getArguments() != null) {
            dialogId = getArguments().getLong("dialog_id", 0);
            topicId = getArguments().getLong("topic_id", 0);
            currentType = getArguments().getInt("type", -1);
        }
        String prefPath;
        String prefDocId;
        if (dialogId != 0) {
            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            prefDocId = "sound_document_id_" + key;
            prefPath = "sound_path_" + key;
        } else {
            if (currentType == NotificationsController.TYPE_PRIVATE) {
                prefPath = "GlobalSoundPath";
                prefDocId = "GlobalSoundDocId";
            } else if (currentType == NotificationsController.TYPE_GROUP) {
                prefPath = "GroupSoundPath";
                prefDocId = "GroupSoundDocId";
            } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                prefPath = "ChannelSoundPath";
                prefDocId = "ChannelSoundDocId";
            } else if (currentType == NotificationsController.TYPE_STORIES) {
                prefPath = "StoriesSoundPath";
                prefDocId = "StoriesSoundDocId";
            } else {
                throw new RuntimeException("Unsupported type");
            }
        }

        SharedPreferences preferences = getNotificationsSettings();
        long documentId = preferences.getLong(prefDocId, 0);
        String localUri = preferences.getString(prefPath, "NoSound");

        startSelectedTone = new Tone();
        if (documentId != 0) {
            startSelectedTone.document = new TLRPC.TL_document();
            startSelectedTone.document.id = documentId;
        } else {
            startSelectedTone.uri = localUri;
        }
        return super.onFragmentCreate();
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    @Override
    public View createView(final Context context) {
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode();
                    } else {
                        finishFragment();
                    }
                } else if (id == deleteId) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
                    builder.setTitle(LocaleController.formatPluralString("DeleteTones", selectedTones.size()));
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatPluralString("DeleteTonesMessage", selectedTones.size())));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> {
                        dialog.dismiss();
                    });
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                        deleteSelectedMessages();
                        dialog.dismiss();
                    });
                    AlertDialog dialog = builder.show();
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
                    }
                } else if (id == shareId) {
                    if (selectedTones.size() == 1) {
                        Intent intent = new Intent(context, LaunchActivity.class);
                        intent.setAction(Intent.ACTION_SEND);

                        Uri uri = selectedTones.valueAt(0).getUriForShare(currentAccount);
                        if (uri != null) {
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            context.startActivity(intent);
                        }
                    } else {
                        Intent intent = new Intent(context, LaunchActivity.class);
                        intent.setAction(Intent.ACTION_SEND_MULTIPLE);

                        ArrayList<Uri> uries = new ArrayList<>();
                        for(int i = 0; i < selectedTones.size(); i++) {
                            Uri uri = selectedTones.valueAt(i).getUriForShare(currentAccount);
                            if (uri != null) {
                                uries.add(uri);
                            }

                        }
                        if (!uries.isEmpty()) {
                            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uries);
                            context.startActivity(intent);
                        }
                    }

                    hideActionMode();
                    updateRows();
                    adapter.notifyDataSetChanged();
                }
            }

            private void deleteSelectedMessages() {
                ArrayList<TLRPC.Document> documentsToRemove = new ArrayList<>();
                for (int i = 0; i < selectedTones.size(); i++) {
                    Tone tone = selectedTones.valueAt(i);
                    if (tone.document != null) {
                        documentsToRemove.add(tone.document);
                        getMediaDataController().ringtoneDataStore.remove(tone.document);
                    }
                    if (tone.uri != null) {
                        RingtoneUploader ringtoneUploader = getMediaDataController().ringtoneUploaderHashMap.get(tone.uri);
                        if (ringtoneUploader != null) {
                            ringtoneUploader.cancel();
                        }
                    }
                    if (tone == selectedTone) {
                        startSelectedTone = null;
                        selectedTone = systemTones.get(0);
                        selectedToneChanged = true;
                    }
                    serverTones.remove(tone);
                    uploadingTones.remove(tone);
                }
                getMediaDataController().ringtoneDataStore.saveTones();

                for (int i = 0; i < documentsToRemove.size(); i++) {
                    TLRPC.Document document = documentsToRemove.get(i);
                    TLRPC.TL_account_saveRingtone req = new TLRPC.TL_account_saveRingtone();
                    req.id = new TLRPC.TL_inputDocument();
                    req.id.id = document.id;
                    req.id.access_hash = document.access_hash;
                    req.id.file_reference = document.file_reference;
                    if (req.id.file_reference == null) {
                        req.id.file_reference = new byte[0];
                    }
                    req.unsave = true;
                    getConnectionsManager().sendRequest(req, (response, error) -> {

                    });
                }
                hideActionMode();
                updateRows();
                adapter.notifyDataSetChanged();
            }
        });

        if (dialogId == 0) {
            if (currentType == NotificationsController.TYPE_PRIVATE) {
                actionBar.setTitle(LocaleController.getString("NotificationsSoundPrivate", R.string.NotificationsSoundPrivate));
            } else if (currentType == NotificationsController.TYPE_GROUP) {
                actionBar.setTitle(LocaleController.getString("NotificationsSoundGroup", R.string.NotificationsSoundGroup));
            } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                actionBar.setTitle(LocaleController.getString("NotificationsSoundChannels", R.string.NotificationsSoundChannels));
            } else if (currentType == NotificationsController.TYPE_STORIES) {
                actionBar.setTitle(LocaleController.getString("NotificationsSoundStories", R.string.NotificationsSoundStories));
            }
        } else {
            avatarContainer = new ChatAvatarContainer(context, null, false, resourcesProvider);
            avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
            actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 40, 0));
            if (dialogId < 0) {
                if (topicId != 0) {
                    TLRPC.TL_forumTopic forumTopic = getMessagesController().getTopicsController().findTopic(-dialogId, topicId);
                    ForumUtilities.setTopicIcon(avatarContainer.getAvatarImageView(), forumTopic, false, true, resourcesProvider);
                    avatarContainer.setTitle(forumTopic.title);
                } else {
                    TLRPC.Chat chatLocal = getMessagesController().getChat(-dialogId);
                    avatarContainer.setChatAvatar(chatLocal);
                    avatarContainer.setTitle(chatLocal.title);
                }
            } else {
                TLRPC.User user = getMessagesController().getUser(dialogId);
                if (user != null) {
                    avatarContainer.setUserAvatar(user);
                    avatarContainer.setTitle(ContactsController.formatName(user.first_name, user.last_name));
                }
            }
            avatarContainer.setSubtitle(LocaleController.getString("NotificationsSound", R.string.NotificationsSound));
        }

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedTonesCountTextView = new NumberTextView(actionMode.getContext());
        selectedTonesCountTextView.setTextSize(18);
        selectedTonesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedTonesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon, resourcesProvider));
        actionMode.addView(selectedTonesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedTonesCountTextView.setOnTouchListener((v, event) -> true);

        actionMode.addItemWithWidth(shareId, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("ShareFile", R.string.ShareFile));
        actionMode.addItemWithWidth(deleteId, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        listView = new RecyclerListView(context);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        adapter = new Adapter();
        adapter.setHasStableIds(true);
        listView.setAdapter(adapter);
        ((DefaultItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == uploadRow) {
                chatAttachAlert = new ChatAttachAlert(context, NotificationsSoundActivity.this, false, false, true, resourcesProvider);
                chatAttachAlert.setSoundPicker();
                chatAttachAlert.init();
                chatAttachAlert.show();
            }
            if (view instanceof ToneCell) {
                ToneCell cell = (ToneCell) view;
                if (actionBar.isActionModeShowed() || cell.tone == null) {
                    checkSelection(cell.tone);
                    return;
                }
                if (lastPlayedRingtone != null) {
                    lastPlayedRingtone.stop();
                }
                try {
                    if (cell.tone.isSystemDefault) {
                        Ringtone r = RingtoneManager.getRingtone(context.getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                        r.setStreamType(tonesStreamType);
                        lastPlayedRingtone = r;
                        r.play();
                    } else if (cell.tone.uri != null && !cell.tone.fromServer) {
                        Ringtone r = RingtoneManager.getRingtone(context.getApplicationContext(), Uri.parse(cell.tone.uri));
                        r.setStreamType(tonesStreamType);
                        lastPlayedRingtone = r;
                        r.play();
                    } else if (cell.tone.fromServer) {
                        File file = null;
                        if (!TextUtils.isEmpty(cell.tone.uri)) {
                            File localUriFile = new File(cell.tone.uri);
                            if (localUriFile.exists()) {
                                file = localUriFile;
                            }
                        }
                        if (file == null) {
                            file = getFileLoader().getPathToAttach(cell.tone.document);
                        }
                        if (file != null && file.exists()) {
                            Ringtone r = RingtoneManager.getRingtone(context.getApplicationContext(), Uri.parse(file.toString()));
                            r.setStreamType(tonesStreamType);
                            lastPlayedRingtone = r;
                            r.play();
                        } else {
                            getFileLoader().loadFile(cell.tone.document, cell.tone.document, FileLoader.PRIORITY_HIGH, 0);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                startSelectedTone = null;
                selectedTone = cell.tone;
                selectedToneChanged = true;
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            }

        });

        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof ToneCell) {
                ToneCell cell = (ToneCell) view;
                checkSelection(cell.tone);
                cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            return false;
        });

        loadTones();
        updateRows();
        return fragmentView;
    }

    private void hideActionMode() {
        selectedTones.clear();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        updateActionMode();
    }

    private void checkSelection(Tone tone) {
        boolean changed = false;
        if (selectedTones.get(tone.stableId) != null) {
            selectedTones.remove(tone.stableId);
            changed = true;
        } else if (tone.fromServer) {
            selectedTones.put(tone.stableId, tone);
            changed = true;
        }
        if (changed) {
            updateActionMode();
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private void updateActionMode() {
        if (selectedTones.size() > 0) {
            selectedTonesCountTextView.setNumber(selectedTones.size(), actionBar.isActionModeShowed());
            actionBar.showActionMode();
        } else {
            actionBar.hideActionMode();
        }
    }

    private void loadTones() {
        getMediaDataController().ringtoneDataStore.loadUserRingtones(false);
        serverTones.clear();
        systemTones.clear();
        
        for (int i = 0; i < getMediaDataController().ringtoneDataStore.userRingtones.size(); i++) {
            RingtoneDataStore.CachedTone cachedTone = getMediaDataController().ringtoneDataStore.userRingtones.get(i);
            Tone tone = new Tone();
            tone.stableId = stableIds++;
            tone.fromServer = true;
            tone.localId = cachedTone.localId;
            tone.title = cachedTone.document.file_name_fixed;
            tone.document = cachedTone.document;
            trimTitle(tone);

            tone.uri = cachedTone.localUri;

            if (startSelectedTone != null && startSelectedTone.document != null && cachedTone.document != null && startSelectedTone.document.id == cachedTone.document.id) {
                startSelectedTone = null;
                selectedTone = tone;
            }

            serverTones.add(tone);
        }

        RingtoneManager manager = new RingtoneManager(ApplicationLoader.applicationContext);
        manager.setType(RingtoneManager.TYPE_NOTIFICATION);
        Cursor cursor = manager.getCursor();

        Tone noSoundTone = new Tone();
        noSoundTone.stableId = stableIds++;
        noSoundTone.title = LocaleController.getString("NoSound", R.string.NoSound);
        noSoundTone.isSystemNoSound = true;
        systemTones.add(noSoundTone);


        Tone defaultTone = new Tone();
        defaultTone.stableId = stableIds++;
        defaultTone.title = LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone);
        defaultTone.isSystemDefault = true;
        systemTones.add(defaultTone);

        if (startSelectedTone != null && startSelectedTone.document == null && startSelectedTone.uri.equals("NoSound")) {
            startSelectedTone = null;
            selectedTone = noSoundTone;
        }

        if (startSelectedTone != null && startSelectedTone.document == null && startSelectedTone.uri.equals("Default")) {
            startSelectedTone = null;
            selectedTone = defaultTone;
        }

        while (cursor.moveToNext()) {
            String notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
            String notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(RingtoneManager.ID_COLUMN_INDEX);

            Tone tone = new Tone();
            tone.stableId = stableIds++;
            tone.title = notificationTitle;
            tone.uri = notificationUri;

            if (startSelectedTone != null && startSelectedTone.document == null && startSelectedTone.uri.equals(notificationUri)) {
                startSelectedTone = null;
                selectedTone = tone;
            }

            systemTones.add(tone);
        }
        if (getMediaDataController().ringtoneDataStore.isLoaded() && selectedTone == null) {
            selectedTone = defaultTone;
            selectedToneChanged = true;
        }
        updateRows();
    }

    public static String findRingtonePathByName(String title) {
        if (title == null) {
            return null;
        }

        try {
            RingtoneManager manager = new RingtoneManager(ApplicationLoader.applicationContext);
            manager.setType(RingtoneManager.TYPE_NOTIFICATION);
            Cursor cursor = manager.getCursor();

            while (cursor.moveToNext()) {
                String notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                String notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" + cursor.getString(RingtoneManager.ID_COLUMN_INDEX);

                if (title.equalsIgnoreCase(notificationTitle)) {
                    return notificationUri;
                }
            }
        } catch (Throwable e) {
            // Exception java.lang.NullPointerException: Attempt to invoke interface method 'void android.database.Cursor.registerDataSetObserver(android.database.DataSetObserver)' on a null object reference
            // ignore
            FileLog.e(e);
        }
        return null;
    }

    private void updateRows() {
        serverTonesHeaderRow = -1;
        serverTonesStartRow = -1;
        serverTonesEndRow = -1;
        uploadRow = -1;
        dividerRow = -1;
        systemTonesHeaderRow = -1;
        systemTonesStartRow = -1;
        systemTonesEndRow = -1;

        rowCount = 0;

        serverTonesHeaderRow = rowCount++;
        if (!serverTones.isEmpty()) {
            serverTonesStartRow = rowCount;
            rowCount += serverTones.size();
            serverTonesEndRow = rowCount;
        }
        uploadRow = rowCount++;
        dividerRow = rowCount++;

        if (!systemTones.isEmpty()) {
            systemTonesHeaderRow = rowCount++;
            systemTonesStartRow = rowCount;
            rowCount += systemTones.size();
            systemTonesEndRow = rowCount;
        }
        dividerRow2 = rowCount++;
    }

    @Override
    public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate) {
        for (int i = 0; i < files.size(); i++) {
            getMediaDataController().uploadRingtone(files.get(i));
        }
        getNotificationCenter().postNotificationName(NotificationCenter.onUserRingtonesUpdated);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public long getItemId(int position) {
            Tone tone = getTone(position);
            if (tone != null) {
                return tone.stableId;
            }
            if (position == serverTonesHeaderRow) {
                return 1;
            } else if (position == systemTonesHeaderRow) {
                return 2;
            } else if (position == uploadRow) {
                return 3;
            } else if (position == dividerRow) {
                return 4;
            } else if (position == dividerRow2) {
                return 5;
            } else {
                throw new RuntimeException();
            }
        }

        private Tone getTone(int position) {
            if (position >= systemTonesStartRow && position < systemTonesEndRow) {
                return systemTones.get(position - systemTonesStartRow);
            }
            if (position >= serverTonesStartRow && position < serverTonesEndRow) {
                return serverTones.get(position - serverTonesStartRow);
            }
            return null;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            switch (viewType) {
                case 0:
                    view = new ToneCell(context, resourcesProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    break;
                default:
                case 1:
                    view = new HeaderCell(context, resourcesProvider);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    break;
                case 2:
                    CreationTextCell creationTextCell = new CreationTextCell(context, resourcesProvider);
                    creationTextCell.startPadding = 61;
                    view = creationTextCell;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    break;
                case 3:
                    view = new ShadowSectionCell(context, resourcesProvider);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ToneCell toneCell = (ToneCell) holder.itemView;
                    Tone tone = null;
                    if (position >= systemTonesStartRow && position < systemTonesEndRow) {
                        tone = systemTones.get(position - systemTonesStartRow);
                    }
                    if (position >= serverTonesStartRow && position < serverTonesEndRow) {
                        tone = serverTones.get(position - serverTonesStartRow);
                    }

                    if (tone != null) {
                        boolean animated = toneCell.tone == tone;
                        boolean checked = tone == selectedTone;
                        boolean selected = selectedTones.get(tone.stableId) != null;
                        toneCell.tone = tone;
                        toneCell.textView.setText(tone.title);
                        toneCell.needDivider = position != systemTonesEndRow - 1;
                        toneCell.radioButton.setChecked(checked, animated);
                        toneCell.checkBox.setChecked(selected, animated);
                    }
                    break;
                case 1:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == serverTonesHeaderRow) {
                        headerCell.setText(LocaleController.getString("TelegramTones", R.string.TelegramTones));
                    } else if (position == systemTonesHeaderRow) {
                        headerCell.setText(LocaleController.getString("SystemTones", R.string.SystemTones));
                    }
                    break;
                case 2:
                    CreationTextCell textCell = (CreationTextCell) holder.itemView;
                    Drawable drawable1 = textCell.getContext().getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = textCell.getContext().getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);
                    textCell.setTextAndIcon(LocaleController.getString("UploadSound", R.string.UploadSound), combinedDrawable, false);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= systemTonesStartRow && position < systemTonesEndRow) {
                return 0;
            } else if (position == serverTonesHeaderRow || position == systemTonesHeaderRow) {
                return 1;
            } else if (position == uploadRow) {
                return 2;
            } else if (position == dividerRow || position == dividerRow2) {
                return 3;
            }

            return super.getItemViewType(position);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0 || holder.getItemViewType() == 2;
        }
    }

    private static class ToneCell extends FrameLayout {
        private TextView textView;
        public TextView valueTextView;
        private RadioButton radioButton;
        private CheckBox2 checkBox;
        private boolean needDivider;

        Tone tone;

        public ToneCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_radioBackgroundChecked, resourcesProvider));
            addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : 20), 0, (LocaleController.isRTL ? 20 : 0), 0));


            checkBox = new CheckBox2(context, 24, resourcesProvider);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(26, 26, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : 18), 0, (LocaleController.isRTL ? 18 : 0), 0));
            checkBox.setChecked(true, false);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 23 : 61), 0, (LocaleController.isRTL ? 61 : 23), 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(AndroidUtilities.dp(LocaleController.isRTL ? 0 : 60), getHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(LocaleController.isRTL ? 60 : 0), getHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.RadioButton");
            info.setCheckable(true);
            info.setChecked(radioButton.isChecked());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getNotificationCenter().addObserver(this, NotificationCenter.onUserRingtonesUpdated);
    }

    @Override
    public void onPause() {
        super.onPause();
        getNotificationCenter().removeObserver(this, NotificationCenter.onUserRingtonesUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onUserRingtonesUpdated) {
            HashMap<Integer, Tone> currentTones = new HashMap<>();
            for (int i = 0; i < serverTones.size(); i++) {
                currentTones.put(serverTones.get(i).localId, serverTones.get(i));
            }
            serverTones.clear();
            for (int i = 0; i < getMediaDataController().ringtoneDataStore.userRingtones.size(); i++) {
                RingtoneDataStore.CachedTone cachedTone = getMediaDataController().ringtoneDataStore.userRingtones.get(i);
                Tone tone = new Tone();
                Tone currentTone = currentTones.get(cachedTone.localId);
                if (currentTone != null) {
                    if (currentTone == selectedTone) {
                        selectedTone = tone;
                    }
                    tone.stableId = currentTone.stableId;
                } else {
                    tone.stableId = stableIds++;
                }
                tone.fromServer = true;
                tone.localId = cachedTone.localId;
                if (cachedTone.document != null) {
                    tone.title = cachedTone.document.file_name_fixed;
                } else {
                    tone.title = new File(cachedTone.localUri).getName();
                }
                tone.document = cachedTone.document;
                trimTitle(tone);
                tone.uri = cachedTone.localUri;

                if (startSelectedTone != null && startSelectedTone.document != null && cachedTone.document != null && startSelectedTone.document.id == cachedTone.document.id) {
                    startSelectedTone = null;
                    selectedTone = tone;
                }

                serverTones.add(tone);
            }
            updateRows();
            adapter.notifyDataSetChanged();

            if (getMediaDataController().ringtoneDataStore.isLoaded() && selectedTone == null && systemTones.size() > 0) {
                startSelectedTone = null;
                selectedTone = systemTones.get(0);
            }
        }
    }

    private void trimTitle(Tone tone) {
        tone.title = trimTitle(tone.document, tone.title);
    }

    public static String trimTitle(TLRPC.Document document, String title) {
        if (title != null) {
            int idx = title.lastIndexOf('.');
            if (idx != -1) {
                title = title.substring(0, idx);
            }
        }
        if (TextUtils.isEmpty(title) && document != null) {
            title = LocaleController.formatString("SoundNameEmpty", R.string.SoundNameEmpty, LocaleController.formatDateChat(document.date, true));
        }
        return title;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (selectedTone != null && selectedToneChanged) {
            SharedPreferences preferences = getNotificationsSettings();
            SharedPreferences.Editor editor = preferences.edit();

            String prefName;
            String prefPath;
            String prefDocId;

            if (dialogId != 0) {
                prefName = "sound_" + NotificationsController.getSharedPrefKey(dialogId, topicId);
                prefPath = "sound_path_" + NotificationsController.getSharedPrefKey(dialogId, topicId);
                prefDocId = "sound_document_id_" + NotificationsController.getSharedPrefKey(dialogId, topicId);
                editor.putBoolean("sound_enabled_" + NotificationsController.getSharedPrefKey(dialogId, topicId), true);
            } else {
                if (currentType == NotificationsController.TYPE_PRIVATE) {
                    prefName = "GlobalSound";
                    prefPath = "GlobalSoundPath";
                    prefDocId = "GlobalSoundDocId";
                } else if (currentType == NotificationsController.TYPE_GROUP) {
                    prefName = "GroupSound";
                    prefPath = "GroupSoundPath";
                    prefDocId = "GroupSoundDocId";
                } else if (currentType == NotificationsController.TYPE_CHANNEL) {
                    prefName = "ChannelSound";
                    prefPath = "ChannelSoundPath";
                    prefDocId = "ChannelSoundDocId";
                } else if (currentType == NotificationsController.TYPE_STORIES) {
                    prefName = "StoriesSound";
                    prefPath = "StoriesSoundPath";
                    prefDocId = "StoriesSoundDocId";
                } else {
                    throw new RuntimeException("Unsupported type");
                }
            }

            if (selectedTone.fromServer && selectedTone.document != null) {
                editor.putLong(prefDocId, selectedTone.document.id);
                editor.putString(prefName, selectedTone.title);
                editor.putString(prefPath, "NoSound");
            } else if (selectedTone.uri != null) {
                editor.putString(prefName, selectedTone.title);
                editor.putString(prefPath, selectedTone.uri);
                editor.remove(prefDocId);
            } else if (selectedTone.isSystemDefault) {
                editor.putString(prefName, "Default");
                editor.putString(prefPath, "Default");
                editor.remove(prefDocId);
            } else {
                editor.putString(prefName, "NoSound");
                editor.putString(prefPath, "NoSound");
                editor.remove(prefDocId);
            }

            editor.apply();
            if (dialogId != 0) {
                getNotificationsController().updateServerNotificationsSettings(dialogId, topicId);
            } else {
                getNotificationsController().updateServerNotificationsSettings(currentType);
                getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
            }
        }
    }

    public void startDocumentSelectActivity() {
        try {
            Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
            if (Build.VERSION.SDK_INT >= 18) {
                photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            photoPickerIntent.setType("audio/mpeg");
            startActivityForResult(photoPickerIntent, 21);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21) {
            if (data == null) {
                return;
            }

            if (chatAttachAlert != null) {
                boolean apply = false;
                if (data.getData() != null) {
                    String path = AndroidUtilities.getPath(data.getData());
                    if (path != null) {
                        File file = new File(path);
                        if (chatAttachAlert.getDocumentLayout().isRingtone(file)) {
                            apply = true;
                            getMediaDataController().uploadRingtone(path);
                            getNotificationCenter().postNotificationName(NotificationCenter.onUserRingtonesUpdated);
                        }
                    }
                } else if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        String path = clipData.getItemAt(i).getUri().toString();
                        if (chatAttachAlert.getDocumentLayout().isRingtone(new File(path))) {
                            apply = true;
                            getMediaDataController().uploadRingtone(path);
                            getNotificationCenter().postNotificationName(NotificationCenter.onUserRingtonesUpdated);
                        }
                    }
                }
                if (apply) {
                    chatAttachAlert.dismiss();
                }
            }
        }
    }

    private static class Tone {
        public boolean fromServer;
        boolean isSystemDefault;
        boolean isSystemNoSound;
        int stableId;
        int localId;
        TLRPC.Document document;
        String title;
        String uri;

        public Uri getUriForShare(int currentAccount) {
            if (! TextUtils.isEmpty(uri)) {
                return Uri.fromFile(new File(uri));
            }
            if (document != null) {
                String fileName = document.file_name_fixed;
                String ext = FileLoader.getDocumentExtension(document);
                if (ext != null) {
                    ext = ext.toLowerCase();
                    if (!fileName.endsWith(ext)) {
                        fileName += "." + ext;
                    }
                    File file = new File(AndroidUtilities.getCacheDir(), fileName);
                    if (!file.exists()) {
                        try {
                            AndroidUtilities.copyFile(FileLoader.getInstance(currentAccount).getPathToAttach(document), file);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    return Uri.fromFile(file);
                }
            }

            return null;
        }
    }
}

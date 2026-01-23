package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.escape;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileMusicView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

import java.io.File;
import java.util.ArrayList;

public class SelectAudioAlert extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener {

    private final int tag;
    private final boolean local;
    private final SelectAudioAlert parentAlert;
    private final ArrayList<MessageObject> localAudio = new ArrayList<>();
    private final ArrayList<MessageObject> sharedAudio = new ArrayList<>();
    private final MessagesController.SavedMusicList savedMusicList;
    private final Utilities.Callback<MessageObject> onAudioSelected;
    private MessageObject downloadingMessageObject;

    private final FrameLayout searchFieldContainer;
    private final FrameLayout searchField;
    private final EditTextCaption searchFieldEditText;

    public SelectAudioAlert(Context context, Utilities.Callback<MessageObject> onAudioSelected) {
        this(context, false, null, onAudioSelected);
    }

    public SelectAudioAlert(Context context, boolean local, SelectAudioAlert parentAlert, Utilities.Callback<MessageObject> onAudioSelected) {
        super(context, null, true, false, false, ActionBarType.SLIDING, new DarkThemeResourceProvider());

        topPadding = 0.35f;
        fixNavigationBar();
        setSlidingActionBar();
        headerPaddingTop = dp(4);
        headerPaddingBottom = dp(-10);

        this.local = local;
        tag = DownloadController.getInstance(currentAccount).generateObserverTag();
        this.parentAlert = parentAlert;
        this.onAudioSelected = onAudioSelected;

        searchFieldContainer = new FrameLayout(context);
        searchFieldContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        searchField = new FrameLayout(context);
        searchField.setBackground(Theme.createRoundRectDrawable(dp(36), getThemedColor(Theme.key_graySection)));
        searchFieldContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 8, 6, 8, 6));

        final ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.menu_browser_search);
        searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_graySectionText), PorterDuff.Mode.SRC_IN));
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchField.addView(searchIcon, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 0, 0, 0));

        searchFieldEditText = new EditTextCaption(context, resourcesProvider);
        searchFieldEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchFieldEditText.setTextColor(0xFFFFFFFF);
        searchFieldEditText.setHintTextColor(getThemedColor(Theme.key_graySectionText));
        searchFieldEditText.setHint(getString(R.string.Search));
        searchFieldEditText.setBackground(null);
        searchFieldEditText.setLines(1);
        searchFieldEditText.setMaxLines(1);
        searchFieldEditText.setSingleLine();
        searchFieldEditText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        searchFieldEditText.setPadding(0, dp(2), 0, dp(2));
        searchField.addView(searchFieldEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL, 4 + 36, 0, 10, 0));
        searchFieldEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                final String lastQuery = query;
                query = s.toString();
                if (!local) {
                    sharedAudio.clear();
                    if (!TextUtils.isEmpty(lastQuery) && TextUtils.isEmpty(query)) {
                        loadSharedAudio();
                    } else {
                        loadSharedAudioDelayed();
                    }
                }
                adapter.update(true);
            }
        });

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        if (!local) {
            savedMusicList = new MessagesController.SavedMusicList(currentAccount, UserConfig.getInstance(currentAccount).getClientUserId());
            savedMusicList.load();
            loadSharedAudio();
        } else {
            savedMusicList = null;
            loadLocalAudio();
        }

        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof SharedAudioCell) {
                final SharedAudioCell cell = ((SharedAudioCell) view);
                final MessageObject messageObject = cell.getMessage();
                if (messageObject == null) return;

                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                if (downloadingMessageObject != null) {
                    FileLoader.getInstance(currentAccount).cancelLoadFile(downloadingMessageObject.getDocument());
                    downloadingMessageObject = null;
                }

                if (!messageObject.attachPathExists && !messageObject.mediaExists) {
                    final String fileName = messageObject.getFileName();
                    if (TextUtils.isEmpty(fileName)) {
                        return;
                    }
                    downloadingMessageObject = messageObject;
                    DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, this);
                    FileLoader.getInstance(currentAccount).loadFile(messageObject.getDocument(), messageObject, FileLoader.PRIORITY_NORMAL, 0);
                } else {
                    done(messageObject);
                }
            } else {
                final UItem item = adapter.getItem(position - 1);
                if (item != null && item.id == 1) {
                    new SelectAudioAlert(getContext(), true, this, onAudioSelected).show();
                } else if (item != null && item.id == 2) {
                    savedMusicList.load();
                } else if (item != null && item.id == 3) {
                    loadSharedAudio();
                }
            }
        });
    }

    private void done(MessageObject messageObject) {
        onAudioSelected.run(messageObject);
        if (parentAlert != null) {
            parentAlert.dismiss();
        }
        dismiss();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.StoryMusicTitle);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.musicListLoaded) {
            adapter.update(true);
        }
    }

    private UniversalAdapter adapter;
    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private String query;
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(searchFieldContainer));
        if (!local) {
            items.add(UItem.asButton(1, R.drawable.msg2_folder, getString(R.string.StoryMusicSelectFromFiles)).accent());
        }
        if (!local) {
            if (savedMusicList != null) {
                addSection(items, getString(R.string.StoryMusicProfileMusic), savedMusicList.list);
                if (!savedMusicList.endReached) {
                    items.add(UItem.asButton(2, R.drawable.arrow_more, getString(R.string.ShowMore)).accent());
                } else if (savedMusicList.list.isEmpty()) {
                    items.add(UItem.asShadow(null));
                }
            }
            addSection(items, getString(R.string.StoryMusicSearchMusic), sharedAudio);
            if (sharedAudio != null && !sharedAudio.isEmpty()) {
                if (sharedAudioHasMore) {
                    items.add(UItem.asButton(3, R.drawable.arrow_more, getString(R.string.ShowMore)).accent());
                } else {
                    items.add(UItem.asShadow(null));
                }
            }
        } else {
            addSection(items, getString(R.string.StoryMusicLocalMusic), localAudio);
            items.add(UItem.asShadow(null));
        }
        if (!TextUtils.isEmpty(query)) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asSpace(dp(500)));
        }
    }

    private void addSection(ArrayList<UItem> items, String name, ArrayList<MessageObject> messageObjects) {
        if (messageObjects == null || messageObjects.isEmpty()) return;
        final ArrayList<MessageObject> filtered = new ArrayList<>();
        final String lquery = query == null ? null : query.toLowerCase();
        final String tquery = AndroidUtilities.translitSafe(lquery);
        for (final MessageObject messageObject : messageObjects) {
            if (TextUtils.isEmpty(lquery) || messageObjects == sharedAudio) {
                messageObject.setQuery(null);
                filtered.add(messageObject);
            } else {
                final String title = messageObject.getMusicTitle();
                final String author = messageObject.getMusicAuthor();
                if (matches(lquery, tquery, title) || matches(lquery, tquery, author)) {
                    messageObject.setQuery(query);
                    filtered.add(messageObject);
                }
            }
        }
        if (filtered.isEmpty()) return;
        items.add(UItem.asGraySection(name));
        for (final MessageObject messageObject : filtered) {
            items.add(SharedAudioCell.Factory.as(messageObject));
        }
    }

    private boolean matches(String lquery, String tquery, String name) {
        if (name == null) return false;
        final String lname = name.toLowerCase();
        if (lname.startsWith(lquery) || lname.contains(" " + lquery))
            return true;
        final String tname = AndroidUtilities.translitSafe(lname);
        if (tname.startsWith(tquery) || tname.contains(" " + tquery))
            return true;
        return false;
    }

    private int nextSearchRate;
    private boolean sharedAudioHasMore;
    private boolean loadingSharedAudio;
    private void loadSharedAudioDelayed() {
        AndroidUtilities.cancelRunOnUIThread(loadSharedAudioRunnable);
        AndroidUtilities.runOnUIThread(loadSharedAudioRunnable, 400);
    }
    private Runnable loadSharedAudioRunnable = this::loadSharedAudio;
    private void loadSharedAudio() {
        if (local) return;
        if (loadingSharedAudio) return;
        if (!sharedAudio.isEmpty() && !sharedAudioHasMore) return;

        loadingSharedAudio = true;

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.filter = new TLRPC.TL_inputMessagesFilterMusic();
        req.q = query == null ? "" : query;
        req.limit = 20;
        if (sharedAudio != null && sharedAudio.size() > 0) {
            final MessageObject lastMessage = sharedAudio.get(sharedAudio.size() - 1);
            req.offset_id = lastMessage.getId();
            req.offset_rate = nextSearchRate;
            long id = MessageObject.getPeerId(lastMessage.messageOwner.peer_id);
            req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                for (final TLRPC.Message message : r.messages) {
                    final MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
                    sharedAudio.add(messageObject);
                }

                sharedAudioHasMore = r instanceof TLRPC.TL_messages_messagesSlice && sharedAudio.size() < r.count;
                nextSearchRate = r.next_rate;
                loadingSharedAudio = false;

                adapter.update(true);
            }
        }));
    }

    private boolean loadingLocalAudio;
    private void loadLocalAudio() {
        if (!local) return;
        if (loadingLocalAudio) return;
        loadingLocalAudio = true;
        Utilities.globalQueue.postRunnable(() -> {
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM
            };

            final ArrayList<MessageObject> newAudioEntries = new ArrayList<>();
            try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE)) {
                int id = -2000000000;
                while (cursor.moveToNext()) {
                    MediaController.AudioEntry audioEntry = new MediaController.AudioEntry();
                    audioEntry.id = cursor.getInt(0);
                    audioEntry.author = cursor.getString(1);
                    audioEntry.title = cursor.getString(2);
                    audioEntry.path = cursor.getString(3);
                    audioEntry.duration = (int) (cursor.getLong(4) / 1000);
                    audioEntry.genre = cursor.getString(5);

                    File file = new File(audioEntry.path);

                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.out = true;
                    message.id = id;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.from_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "";
                    message.attachPath = audioEntry.path;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = new TLRPC.TL_document();
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;

                    String ext = FileLoader.getFileExtension(file);

                    message.media.document.id = 0;
                    message.media.document.access_hash = 0;
                    message.media.document.file_reference = new byte[0];
                    message.media.document.date = message.date;
                    message.media.document.mime_type = "audio/" + (ext.length() > 0 ? ext : "mp3");
                    message.media.document.size = (int) file.length();
                    message.media.document.dc_id = 0;

                    TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                    attributeAudio.duration = audioEntry.duration;
                    attributeAudio.title = audioEntry.title;
                    attributeAudio.performer = audioEntry.author;
                    attributeAudio.flags |= 3;
                    message.media.document.attributes.add(attributeAudio);

                    TLRPC.TL_documentAttributeFilename fileName = new TLRPC.TL_documentAttributeFilename();
                    fileName.file_name = file.getName();
                    message.media.document.attributes.add(fileName);

                    audioEntry.messageObject = new MessageObject(currentAccount, message, false, true);

                    newAudioEntries.add(audioEntry.messageObject);
                    id--;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                loadingLocalAudio = false;
                localAudio.addAll(newAudioEntries);
                adapter.update(true);
            });
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.musicListLoaded);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.musicListLoaded);
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (downloadingMessageObject != null && TextUtils.equals(downloadingMessageObject.getFileName(), fileName)) {
            done(downloadingMessageObject);
        }
    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {

    }

    @Override
    public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return tag;
    }
}

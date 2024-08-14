package org.telegram.ui.Stories.recorder;

import android.text.SpannableString;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DraftsController {

    public static final int EXPIRATION_PERIOD = 1000 * 60 * 60 * 24 * 7; // 7 days

    public final int currentAccount;

    public DraftsController(int currentAccount) {
        this.currentAccount = currentAccount;
        loadFailed();
    }

    public final ArrayList<StoryEntry> drafts = new ArrayList<>();

    private void loadInternal(final boolean failed, Utilities.Callback<ArrayList<StoryDraft>> callback) {
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            final ArrayList<StoryDraft> loadedDrafts = new ArrayList<>();
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }
                ArrayList<Long> todelete = new ArrayList<>();
                cursor = database.queryFinalized("SELECT id, data, type FROM story_drafts WHERE type = " + (failed ? "2" : "0 OR type = 1") + " ORDER BY date DESC");
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    NativeByteBuffer buffer = cursor.byteBufferValue(1);
                    if (buffer != null) {
                        try {
                            StoryDraft draft = new StoryDraft(buffer, true);
                            draft.id = id;
                            loadedDrafts.add(draft);
                        } catch (Exception e) {
                            FileLog.e(e);
                            todelete.add(id);
                        }
                        buffer.reuse();
                    }
                }
                if (cursor != null) {
                    cursor.dispose();
                }
                if (todelete.size() > 0) {
                    for (int i = 0; i < todelete.size(); ++i) {
                        database.executeFast("DELETE FROM story_drafts WHERE id = " + todelete.get(i)).stepThis().dispose();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            AndroidUtilities.runOnUIThread(() -> callback.run(loadedDrafts));
        });
    }

    private boolean loaded, loading;
    public void load() {
        if (loaded || loading) {
            return;
        }

        loading = true;
        loadInternal(false, loadedDrafts -> {
            final long now = System.currentTimeMillis();
            ArrayList<Long> ids = new ArrayList<>();
            ArrayList<StoryEntry> deleteEntries = new ArrayList<>();
            for (int i = 0; i < loadedDrafts.size(); ++i) {
                StoryEntry entry = loadedDrafts.get(i).toEntry();
                if (entry == null) {
                    continue;
                }
                if (
                    entry.file == null ||
                    !entry.file.exists() ||
                    (entry.isEdit ?
                        (now > entry.editExpireDate) :
                        (now - entry.draftDate > EXPIRATION_PERIOD)
                    )
                ) {
                    deleteEntries.add(entry);
                } else {
                    drafts.add(entry);
                    ids.add(entry.draftId);
                }
            }
            delete(deleteEntries);

            loading = false;
            loaded = true;

            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesDraftsUpdated);
        });
    }

    private boolean loadedFailed, loadingFailed;
    private void loadFailed() {
        if (loadedFailed || loadingFailed) {
            return;
        }

        loadingFailed = true;
        loadInternal(true, loadedDrafts -> {
            final long now = System.currentTimeMillis();
            ArrayList<Long> ids = new ArrayList<>();
            ArrayList<StoryEntry> deleteEntries = new ArrayList<>();
            ArrayList<StoryEntry> appendEntries = new ArrayList<>();
            for (int i = 0; i < loadedDrafts.size(); ++i) {
                StoryEntry entry = loadedDrafts.get(i).toEntry();
                if (entry == null) {
                    continue;
                }
                if (
                    entry.file == null ||
                    !entry.file.exists() ||
                    now - entry.draftDate > EXPIRATION_PERIOD
                ) {
                    deleteEntries.add(entry);
                } else {
                    appendEntries.add(entry);
                    ids.add(entry.draftId);
                }
            }
            delete(deleteEntries);

            loadingFailed = false;
            loadedFailed = true;

            MessagesController.getInstance(currentAccount).getStoriesController().putUploadingDrafts(appendEntries);
        });
    }

    public void edit(StoryEntry entry) {
        if (entry == null) {
            return;
        }
        prepare(entry);
        drafts.remove(entry);
        if (!entry.isError) {
            drafts.add(0, entry);
        }
        final StoryDraft draft = new StoryDraft(entry);
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }

                state = database.executeFast("REPLACE INTO story_drafts VALUES (?, ?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(draft.getObjectSize());
                draft.toStream(data);
                state.bindLong(1, draft.id);
                state.bindLong(2, draft.date);
                state.bindByteBuffer(3, data);
                int type = 0;
                if (draft.isEdit) {
                    type = 1;
                } else if (draft.isError) {
                    type = 2;
                }
                state.bindInteger(4, type);
                state.step();
                data.reuse();
                state.dispose();

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesDraftsUpdated);
    }

    private void prepare(StoryEntry entry) {
        if (entry == null) {
            return;
        }

        if (entry.draftId == 0)
            entry.draftId = Utilities.random.nextLong();
        entry.draftDate = System.currentTimeMillis();
        entry.isDraft = true;

        if (entry.fileDeletable) {
            entry.file = prepareFile(entry.file);
        } else if (entry.file != null) {
            File newFile = StoryEntry.makeCacheFile(currentAccount, entry.isVideo);
            try {
                AndroidUtilities.copyFile(entry.file, newFile);
                entry.file = prepareFile(newFile);
                entry.fileDeletable = true;
            } catch (IOException e) {
                FileLog.e(e);
            }
        }
        entry.filterFile = prepareFile(entry.filterFile);
        entry.paintFile = prepareFile(entry.paintFile);
        entry.draftThumbFile = prepareFile(entry.draftThumbFile);
    }

    private File draftsFolder;
    private File prepareFile(File file) {
        if (file == null) {
            return null;
        }
        if (draftsFolder == null) {
            draftsFolder = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "drafts");
            if (!draftsFolder.exists()) {
                draftsFolder.mkdir();
            }
        }
        if (!file.getAbsolutePath().startsWith(draftsFolder.getAbsolutePath())) {
            File newFile = new File(draftsFolder, file.getName());
            if (file.renameTo(newFile)) {
                return newFile;
            }
        }
        return file;
    }

    public void append(StoryEntry entry) {
        if (entry == null || entry.isRepostMessage) {
            return;
        }
        prepare(entry);
        entry.draftId = Utilities.random.nextLong();
        final StoryDraft draft = new StoryDraft(entry);
        drafts.remove(entry);
        drafts.add(0, entry);
        append(draft);
    }

    private void append(StoryDraft draft) {
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        FileLog.d("StoryDraft append " + draft.id + " (edit=" + draft.isEdit + (draft.isEdit ? ", storyId=" + draft.editStoryId + ", " + (draft.editDocumentId != 0 ? "documentId=" + draft.editDocumentId : "photoId=" + draft.editPhotoId) + ", expireDate=" + draft.editExpireDate : "") + ", now="+System.currentTimeMillis()+")");
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }

                state = database.executeFast("INSERT INTO story_drafts VALUES (?, ?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(draft.getObjectSize());
                draft.toStream(data);
                state.bindLong(1, draft.id);
                state.bindLong(2, draft.date);
                state.bindByteBuffer(3, data);
                int type = 0;
                if (draft.isEdit) {
                    type = 1;
                } else if (draft.isError) {
                    type = 2;
                }
                state.bindInteger(4, type);
                state.step();
                data.reuse();
                state.dispose();

            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (state != null) {
                    state.dispose();
                }
            }
        });
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesDraftsUpdated);
    }

    public void deleteForEdit(TL_stories.StoryItem storyItem) {
        if (storyItem == null) {
            return;
        }
        ArrayList<StoryEntry> toDelete = new ArrayList<>();
        for (StoryEntry draft : drafts) {
            if (draft.isEdit && draft.editStoryId == storyItem.id) {
                FileLog.d("StoryDraft deleteForEdit storyId=" + storyItem.id);
                toDelete.add(draft);
            }
        }
        delete(toDelete);
    }

    public void deleteForEdit(long peerId, int storyId) {
        ArrayList<StoryEntry> toDelete = new ArrayList<>();
        for (StoryEntry draft : drafts) {
            if (draft.isEdit && draft.editStoryId == storyId && draft.editStoryPeerId == peerId) {
                FileLog.d("StoryDraft deleteForEdit (2) storyId=" + storyId);
                toDelete.add(draft);
            }
        }
        delete(toDelete);
    }

    public void saveForEdit(StoryEntry entry, long dialogId, TL_stories.StoryItem storyItem) {
        if (entry == null || entry.isRepostMessage || storyItem == null || storyItem.media == null) {
            return;
        }

        ArrayList<StoryEntry> toDelete = new ArrayList<>();
        for (StoryEntry draft : drafts) {
            if (draft.isEdit && draft.editStoryId == storyItem.id) {
                toDelete.add(draft);
            }
        }
        delete(toDelete);

        prepare(entry);
        final long id = Utilities.random.nextLong();
        entry.draftId = id;
        final StoryDraft draft = new StoryDraft(entry);
        draft.isEdit = entry.isEdit = true;
        draft.editStoryPeerId = entry.editStoryPeerId = dialogId;
        draft.editStoryId = entry.editStoryId = storyItem.id;
        draft.editExpireDate = entry.editExpireDate = storyItem.expire_date * 1000L;
        if (storyItem.media.document != null) {
            draft.editDocumentId = entry.editDocumentId = storyItem.media.document.id;
        } else if (storyItem.media.photo != null) {
            draft.editPhotoId = entry.editPhotoId =storyItem.media.photo.id;
        }
        drafts.remove(entry);
        drafts.add(0, entry);
        append(draft);
    }

    public StoryEntry getForEdit(long dialogId, TL_stories.StoryItem storyItem) {
        if (storyItem == null) {
            return null;
        }
        for (StoryEntry draft : drafts) {
            if (draft.isEdit && storyItem.id == draft.editStoryId && dialogId == draft.editStoryPeerId) {
                if (storyItem.media.document != null && storyItem.media.document.id != draft.editDocumentId) {
                    continue;
                }
                if (storyItem.media.photo != null && storyItem.media.photo.id != draft.editPhotoId) {
                    continue;
                }
                draft.isEditSaved = true;
                return draft;
            }
        }
        return null;
    }

    public void delete(StoryEntry entry) {
        ArrayList<StoryEntry> list = new ArrayList<>(1);
        list.add(entry);
        delete(list);
    }

    public void deleteExpired() {
        final long now = System.currentTimeMillis();
        ArrayList<StoryEntry> list = new ArrayList<>();
        for (int i = 0; i < drafts.size(); ++i) {
            StoryEntry entry = drafts.get(i);
            if (entry != null && (
                entry.isEdit ?
                    (now > entry.editExpireDate) :
                    (now - entry.draftDate > EXPIRATION_PERIOD)
            )) {
                FileLog.d("StoryDraft deleteExpired " + entry.draftId);
                list.add(entry);
            }
        }
        delete(list);
    }

    public void delete(ArrayList<StoryEntry> entries) {
        if (entries == null) {
            return;
        }
        ArrayList<Long> ids = new ArrayList<>();
        for (int i = 0; i < entries.size(); ++i) {
            StoryEntry entry = entries.get(i);
            if (entry != null) {
                FileLog.d("StoryDraft delete " + entry.draftId + " (edit=" + entry.isEdit + (entry.isEdit ? ", storyId=" + entry.editStoryId + ", " + (entry.editDocumentId != 0 ? "documentId=" + entry.editDocumentId : "photoId=" + entry.editPhotoId) + ", expireDate=" + entry.editExpireDate : "") + ", now="+System.currentTimeMillis()+")");
                ids.add(entry.draftId);
                entry.destroy(true);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        drafts.removeAll(entries);
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }
                database.executeFast("DELETE FROM story_drafts WHERE id IN (" + TextUtils.join(", ", ids) + ")").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesDraftsUpdated);
    }

    public void cleanup() {
        delete(drafts);
        loaded = false;
    }

    public static class StoryDraft {

        public long id;
        public long date;
        public String thumb;
        public String fullThumb;

        public boolean isVideo;
        public String file;
        public boolean fileDeletable;

        public boolean muted;
        public long left, right;

        public int orientation, invert;
        public int width, height;
        public int resultWidth, resultHeight;
        public long duration;

        public final float[] matrixValues = new float[9];
        public int gradientTopColor, gradientBottomColor;

        public String caption;
        public ArrayList<TLRPC.MessageEntity> captionEntities;
        public final ArrayList<TLRPC.InputPrivacyRule> privacyRules = new ArrayList<>();

        public String paintFilePath;
        public String paintEntitiesFilePath;
        public long averageDuration;
        public ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
        public List<TLRPC.InputDocument> stickers;

        private String filterFilePath;
        private MediaController.SavedFilterState filterState;

        private int period;

        public boolean isEdit;
        public int editStoryId;
        public long editStoryPeerId;
        public long editDocumentId;
        public long editPhotoId;
        public long editExpireDate;

        public boolean isError;
        public TLRPC.TL_error error;

        public String audioPath;
        public String audioAuthor, audioTitle;
        public long audioDuration;
        public long audioOffset;
        public float audioLeft, audioRight = 1;
        public float audioVolume = 1;

        public String roundPath;
        public String roundThumb;
        public long roundDuration;
        public long roundOffset;
        public float roundLeft;
        public float roundRight;
        public float roundVolume = 1;

        public float videoVolume = 1f;

        public TLRPC.InputPeer peer;

        public long botId;
        public String botLang;
        public TLRPC.InputMedia botEdit;

        public StoryDraft(@NonNull StoryEntry entry) {
            this.id = entry.draftId;
            this.date = entry.draftDate;
            this.thumb = entry.draftThumbFile == null ? "" : entry.draftThumbFile.toString();
            this.fullThumb = entry.uploadThumbFile == null ? "" : entry.uploadThumbFile.toString();
            this.isVideo = entry.isVideo;
            this.file = entry.file == null ? "" : entry.file.toString();
            this.fileDeletable = entry.fileDeletable;
            this.muted = entry.muted;
            this.left = (long) (entry.left * entry.duration);
            this.right = (long) (entry.right * entry.duration);
            this.orientation = entry.orientation;
            this.invert = entry.invert;
            this.width = entry.width;
            this.height = entry.height;
            this.resultWidth = entry.resultWidth;
            this.resultHeight = entry.resultHeight;
            this.duration = entry.duration;
            entry.matrix.getValues(this.matrixValues);
            this.gradientTopColor = entry.gradientTopColor;
            this.gradientBottomColor = entry.gradientBottomColor;
            CharSequence caption = entry.caption;
            this.captionEntities = entry.captionEntitiesAllowed ? MediaDataController.getInstance(entry.currentAccount).getEntities(new CharSequence[]{caption}, true) : null;
            this.caption = caption == null ? "" : caption.toString();
            this.privacyRules.addAll(entry.privacyRules);
            this.paintFilePath = entry.paintFile == null ? "" : entry.paintFile.toString();
            this.paintEntitiesFilePath = entry.paintEntitiesFile == null ? "" : entry.paintEntitiesFile.toString();
            this.averageDuration = entry.averageDuration;
            this.mediaEntities = entry.mediaEntities;
            this.stickers = entry.stickers;
            this.filterFilePath = entry.filterFile == null ? "" : entry.filterFile.toString();
            this.filterState = entry.filterState;
            this.period = entry.period;
            this.isError = entry.isError;
            this.error = entry.error;

            this.audioPath = entry.audioPath;
            this.audioAuthor = entry.audioAuthor;
            this.audioTitle = entry.audioTitle;
            this.audioDuration = entry.audioDuration;
            this.audioOffset = entry.audioOffset;
            this.audioLeft = entry.audioLeft;
            this.audioRight = entry.audioRight;
            this.audioVolume = entry.audioVolume;

            this.roundPath = entry.round == null ? null : entry.round.getAbsolutePath();
            this.roundThumb = entry.roundThumb;
            this.roundDuration = entry.roundDuration;
            this.roundOffset = entry.roundOffset;
            this.roundLeft = entry.roundLeft;
            this.roundRight = entry.roundRight;
            this.roundVolume = entry.roundVolume;

            this.videoVolume = entry.videoVolume;

            this.peer = entry.peer;
            this.botId = entry.botId;
            this.botLang = entry.botLang;
            this.botEdit = entry.editingBotPreview;
        }

        public StoryEntry toEntry() {
            StoryEntry entry = new StoryEntry();
            entry.draftId = id;
            entry.isDraft = true;
            entry.draftDate = date;
            if (!TextUtils.isEmpty(thumb)) {
                entry.draftThumbFile = new File(thumb);
            }
            if (!TextUtils.isEmpty(fullThumb)) {
                entry.uploadThumbFile = new File(fullThumb);
            }
            entry.isVideo = isVideo;
            if (file != null) {
                entry.file = new File(file);
            }
            entry.fileDeletable = fileDeletable;
            entry.muted = muted;
            entry.duration = duration;
            if (duration > 0) {
                entry.left = (float) left / duration;
                entry.right = (float) right / duration;
            } else {
                entry.left = 0;
                entry.right = 1;
            }
            entry.orientation = orientation;
            entry.invert = invert;
            entry.width = width;
            entry.height = height;
            entry.resultWidth = resultWidth;
            entry.resultHeight = resultHeight;
            entry.matrix.setValues(matrixValues);
            entry.gradientTopColor = gradientTopColor;
            entry.gradientBottomColor = gradientBottomColor;
            if (caption != null) {
                CharSequence caption = new SpannableString(this.caption);
                if (Theme.chat_msgTextPaint == null) {
                    Theme.createCommonMessageResources();
                }
                caption = Emoji.replaceEmoji(caption, Theme.chat_msgTextPaint.getFontMetricsInt(), true);
                MessageObject.addEntitiesToText(caption, captionEntities, true, false, true, false);
                entry.caption = caption;
            } else {
                entry.caption = "";
            }
            entry.privacyRules.clear();
            entry.privacyRules.addAll(privacyRules);
            if (paintFilePath != null) {
                entry.paintFile = new File(paintFilePath);
            }
            if (paintEntitiesFilePath != null) {
                entry.paintEntitiesFile = new File(paintEntitiesFilePath);
            }
            entry.averageDuration = averageDuration;
            entry.mediaEntities = mediaEntities;
            entry.stickers = stickers;
            if (filterFilePath != null) {
                entry.filterFile = new File(filterFilePath);
            }
            entry.filterState = filterState;
            entry.period = period;
            entry.isEdit = isEdit;
            entry.editStoryId = editStoryId;
            entry.editStoryPeerId = editStoryPeerId;
            entry.editExpireDate = editExpireDate;
            entry.editPhotoId = editPhotoId;
            entry.editDocumentId = editDocumentId;
            entry.isError = isError;
            entry.error = error;

            entry.audioPath = audioPath;
            entry.audioAuthor = audioAuthor;
            entry.audioTitle = audioTitle;
            entry.audioDuration = audioDuration;
            entry.audioOffset = audioOffset;
            entry.audioLeft = audioLeft;
            entry.audioRight = audioRight;
            entry.audioVolume = audioVolume;

            if (roundPath != null) {
                entry.round = new File(roundPath);
            }
            entry.roundThumb = roundThumb;
            entry.roundDuration = roundDuration;
            entry.roundOffset = roundOffset;
            entry.roundLeft = roundLeft;
            entry.roundRight = roundRight;
            entry.roundVolume = roundVolume;

            entry.videoVolume = videoVolume;

            entry.peer = peer;
            entry.botId = botId;
            entry.botLang = botLang;
            entry.editingBotPreview = botEdit;

            return entry;
        }

        public void toStream(AbstractSerializedData stream) {
            stream.writeInt32(0xB16B00B5);
            stream.writeInt64(date);
            stream.writeString(thumb);
            stream.writeBool(isVideo);
            stream.writeString(file);
            stream.writeBool(fileDeletable);
            stream.writeBool(muted);
            stream.writeInt64(left);
            stream.writeInt64(right);
            stream.writeInt32(orientation);
            stream.writeInt32(invert);
            stream.writeInt32(width);
            stream.writeInt32(height);
            stream.writeInt32(resultWidth);
            stream.writeInt32(resultHeight);
            stream.writeInt64(duration);
            for (int i = 0; i < matrixValues.length; ++i) {
                stream.writeFloat(matrixValues[i]);
            }
            stream.writeInt32(gradientTopColor);
            stream.writeInt32(gradientBottomColor);
            stream.writeString(caption);
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(captionEntities == null ? 0 : captionEntities.size());
            if (captionEntities != null) {
                for (int i = 0; i < captionEntities.size(); ++i) {
                    captionEntities.get(i).serializeToStream(stream);
                }
            }
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(privacyRules == null ? 0 : privacyRules.size());
            if (privacyRules != null) {
                for (int i = 0; i < privacyRules.size(); ++i) {
                    privacyRules.get(i).serializeToStream(stream);
                }
            }
            stream.writeBool(false);
            stream.writeString(paintFilePath);
            stream.writeInt64(averageDuration);
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(mediaEntities == null ? 0 : mediaEntities.size());
            if (mediaEntities != null) {
                for (int i = 0; i < mediaEntities.size(); ++i) {
                    mediaEntities.get(i).serializeTo(stream, true);
                }
            }
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(stickers == null ? 0 : stickers.size());
            if (stickers != null) {
                for (int i = 0; i < stickers.size(); ++i) {
                    stickers.get(i).serializeToStream(stream);
                }
            }
            stream.writeString(filterFilePath == null ? "" : filterFilePath);
            if (filterState == null) {
                stream.writeInt32(0x56730bcc);
            } else {
                stream.writeInt32(0xB16B00B6);
                filterState.serializeToStream(stream);
            }
            stream.writeInt32(period);
            stream.writeInt32(0x1cb5c415);
            stream.writeInt32(0);
            stream.writeBool(isEdit);
            stream.writeInt32(editStoryId);
            stream.writeInt64(editStoryPeerId);
            stream.writeInt64(editExpireDate);
            stream.writeInt64(editPhotoId);
            stream.writeInt64(editDocumentId);
            stream.writeString(paintEntitiesFilePath);
            stream.writeBool(isError);
            if (error == null) {
                stream.writeInt32(TLRPC.TL_null.constructor);
            } else {
                error.serializeToStream(stream);
            }
            stream.writeString(fullThumb);

            if (audioPath == null) {
                stream.writeInt32(TLRPC.TL_null.constructor);
            } else {
                stream.writeInt32(TLRPC.TL_documentAttributeAudio.constructor);
                stream.writeString(audioPath);
                if (audioAuthor == null) {
                    stream.writeInt32(TLRPC.TL_null.constructor);
                } else {
                    stream.writeInt32(TLRPC.TL_jsonString.constructor);
                    stream.writeString(audioAuthor);
                }
                if (audioTitle == null) {
                    stream.writeInt32(TLRPC.TL_null.constructor);
                } else {
                    stream.writeInt32(TLRPC.TL_jsonString.constructor);
                    stream.writeString(audioTitle);
                }
                stream.writeInt64(audioDuration);
                stream.writeInt64(audioOffset);
                stream.writeFloat(audioLeft);
                stream.writeFloat(audioRight);
                stream.writeFloat(audioVolume);
            }

            if (peer != null) {
                peer.serializeToStream(stream);
            } else {
                new TLRPC.TL_inputPeerSelf().serializeToStream(stream);
            }

            if (TextUtils.isEmpty(roundPath)) {
                stream.writeInt32(TLRPC.TL_null.constructor);
            } else {
                stream.writeInt32(TLRPC.TL_documentAttributeVideo.constructor);
                stream.writeString(roundPath);
                stream.writeInt64(roundDuration);
                stream.writeInt64(roundOffset);
                stream.writeFloat(roundLeft);
                stream.writeFloat(roundRight);
                stream.writeFloat(roundVolume);
            }

            stream.writeFloat(videoVolume);

            stream.writeInt64(botId);
            stream.writeString(botLang == null ? "" : botLang);
            if (botEdit == null) {
                stream.writeInt32(TLRPC.TL_null.constructor);
            } else {
                botEdit.serializeToStream(stream);
            }

        }

        public int getObjectSize() {
            NativeByteBuffer byteBuffer = new NativeByteBuffer(true);
            toStream(byteBuffer);
            return byteBuffer.length();
        }

        public StoryDraft(@NonNull AbstractSerializedData stream, boolean exception) {
            if (stream.readInt32(exception) != 0xB16B00B5) {
                if (exception) {
                    throw new RuntimeException("StoryDraft parse error");
                } else {
                    return;
                }
            }
            date = stream.readInt64(exception);
            thumb = stream.readString(exception);
            if (thumb != null && thumb.length() == 0) {
                thumb = null;
            }
            isVideo = stream.readBool(exception);
            file = stream.readString(exception);
            if (file != null && file.length() == 0) {
                file = null;
            }
            fileDeletable = stream.readBool(exception);
            muted = stream.readBool(exception);
            left = stream.readInt64(exception);
            right = stream.readInt64(exception);
            orientation = stream.readInt32(exception);
            invert = stream.readInt32(exception);
            width = stream.readInt32(exception);
            height = stream.readInt32(exception);
            resultWidth = stream.readInt32(exception);
            resultHeight = stream.readInt32(exception);
            duration = stream.readInt64(exception);
            for (int i = 0; i < matrixValues.length; ++i) {
                matrixValues[i] = stream.readFloat(exception);
            }
            gradientTopColor = stream.readInt32(exception);
            gradientBottomColor = stream.readInt32(exception);
            caption = stream.readString(exception);
            if (caption != null && caption.length() == 0) {
                caption = null;
            }
            int magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception)
                    throw new RuntimeException("Vector magic in StoryDraft parse error (1)");
                return;
            }
            int count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                if (captionEntities == null) {
                    captionEntities = new ArrayList<>();
                }
                captionEntities.add(TLRPC.MessageEntity.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception)
                    throw new RuntimeException("Vector magic in StoryDraft parse error (2)");
                return;
            }
            count = stream.readInt32(exception);
            privacyRules.clear();
            for (int i = 0; i < count; ++i) {
                privacyRules.add(TLRPC.InputPrivacyRule.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
            stream.readBool(exception);
            paintFilePath = stream.readString(exception);
            if (paintFilePath != null && paintFilePath.length() == 0) {
                paintFilePath = null;
            }
            averageDuration = stream.readInt64(exception);
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception)
                    throw new RuntimeException("Vector magic in StoryDraft parse error (3)");
                return;
            }
            count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                if (mediaEntities == null) {
                    mediaEntities = new ArrayList<>();
                }
                mediaEntities.add(new VideoEditedInfo.MediaEntity(stream, true, exception));
            }
            magic = stream.readInt32(exception);
            if (magic != 0x1cb5c415) {
                if (exception)
                    throw new RuntimeException("Vector magic in StoryDraft parse error (4)");
                return;
            }
            count = stream.readInt32(exception);
            for (int i = 0; i < count; ++i) {
                if (stickers == null) {
                    stickers = new ArrayList<>();
                }
                stickers.add(TLRPC.InputDocument.TLdeserialize(stream, stream.readInt32(exception), exception));
            }
            filterFilePath = stream.readString(exception);
            if (filterFilePath != null && filterFilePath.length() == 0) {
                filterFilePath = null;
            }
            magic = stream.readInt32(exception);
            if (magic == 0x56730bcc) {
                filterState = null;
            } else if (magic == 0xB16B00B6) {
                filterState = new MediaController.SavedFilterState();
                filterState.readParams(stream, exception);
            }
            if (stream.remaining() >= 4) {
                period = stream.readInt32(exception);
            }
            if (stream.remaining() > 0) {
                magic = stream.readInt32(exception);
                if (magic != 0x1cb5c415) {
                    if (exception)
                        throw new RuntimeException("Vector magic in StoryDraft parse error (5)");
                    return;
                }
                count = stream.readInt32(exception);
            }
            if (stream.remaining() > 0) {
                isEdit = stream.readBool(exception);
                editStoryId = stream.readInt32(exception);
                editStoryPeerId = stream.readInt64(exception);
                editExpireDate = stream.readInt64(exception);
                editPhotoId = stream.readInt64(exception);
                editDocumentId = stream.readInt64(exception);
            }
            if (stream.remaining() > 0) {
                paintEntitiesFilePath = stream.readString(exception);
                if (paintEntitiesFilePath != null && paintEntitiesFilePath.length() == 0) {
                    paintEntitiesFilePath = null;
                }
            }
            if (stream.remaining() > 0) {
                isError = stream.readBool(exception);
                magic = stream.readInt32(exception);
                if (magic == TLRPC.TL_null.constructor) {
                    error = null;
                } else {
                    error = TLRPC.TL_error.TLdeserialize(stream, magic, exception);
                }
                fullThumb = stream.readString(exception);
            }
            if (stream.remaining() > 0) {
                magic = stream.readInt32(exception);
                if (magic == TLRPC.TL_documentAttributeAudio.constructor) {
                    audioPath = stream.readString(exception);
                    magic = stream.readInt32(exception);
                    if (magic == TLRPC.TL_jsonString.constructor) {
                        audioAuthor = stream.readString(exception);
                    }
                    magic = stream.readInt32(exception);
                    if (magic == TLRPC.TL_jsonString.constructor) {
                        audioTitle = stream.readString(exception);
                    }
                    audioDuration = stream.readInt64(exception);
                    audioOffset = stream.readInt64(exception);
                    audioLeft = stream.readFloat(exception);
                    audioRight = stream.readFloat(exception);
                    audioVolume = stream.readFloat(exception);
                }
            }
            if (stream.remaining() > 0) {
                peer = TLRPC.InputPeer.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
            if (stream.remaining() > 0) {
                magic = stream.readInt32(exception);
                if (magic == TLRPC.TL_documentAttributeVideo.constructor) {
                    roundPath = stream.readString(exception);
                    roundDuration = stream.readInt64(exception);
                    roundOffset = stream.readInt64(exception);
                    roundLeft = stream.readFloat(exception);
                    roundRight = stream.readFloat(exception);
                    roundVolume = stream.readFloat(exception);
                }
            }
            if (stream.remaining() > 0) {
                videoVolume = stream.readFloat(exception);
            }
            if (stream.remaining() > 0) {
                botId = stream.readInt64(exception);
                botLang = stream.readString(exception);
                magic = stream.readInt32(exception);
                if (magic != TLRPC.TL_null.constructor) {
                    botEdit = TLRPC.InputMedia.TLdeserialize(stream, magic, exception);
                }
            }
        }
    }
}

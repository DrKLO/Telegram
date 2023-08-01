package org.telegram.ui.Stories.recorder;

import android.net.wifi.WifiManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DraftsController {

    public static final int EXPIRATION_PERIOD = 1000 * 60 * 60 * 24 * 7; // 7 days

    public final int currentAccount;

    public DraftsController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    private boolean loaded, loading;
    public final ArrayList<StoryEntry> drafts = new ArrayList<>();

    public void load() {
        if (loaded || loading) {
            return;
        }

        loading = true;
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            final ArrayList<StoryDraft> savedDrafts = new ArrayList<>();
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }
                cursor = database.queryFinalized("SELECT id, data FROM story_drafts ORDER BY date DESC");
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    NativeByteBuffer buffer = cursor.byteBufferValue(1);
                    if (buffer != null) {
                        try {
                            StoryDraft draft = new StoryDraft(buffer, true);
                            draft.id = id;
                            savedDrafts.add(draft);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        buffer.reuse();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            AndroidUtilities.runOnUIThread(() -> {
                final long now = System.currentTimeMillis();
                ArrayList<Long> ids = new ArrayList<>();
                ArrayList<StoryEntry> deleteEntries = new ArrayList<>();
                for (int i = 0; i < savedDrafts.size(); ++i) {
                    StoryEntry entry = savedDrafts.get(i).toEntry();
                    if (entry == null || entry.file == null || !entry.file.exists() || now - entry.draftDate > EXPIRATION_PERIOD) {
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
        });
    }

    public void edit(StoryEntry entry) {
        if (entry == null) {
            return;
        }
        prepare(entry);
        drafts.remove(entry);
        drafts.add(0, entry);
        final StoryDraft draft = new StoryDraft(entry);
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }

                state = database.executeFast("REPLACE INTO story_drafts VALUES (?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(draft.getObjectSize());
                draft.toStream(data);
                state.bindLong(1, draft.id);
                state.bindLong(2, draft.date);
                state.bindByteBuffer(3, data);
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
        if (entry == null) {
            return;
        }
        prepare(entry);
        final long id = Utilities.random.nextLong();
        entry.draftId = id;
        final StoryDraft draft = new StoryDraft(entry);
        drafts.remove(entry);
        drafts.add(0, entry);
        final MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        storage.getStorageQueue().postRunnable(() -> {
            SQLitePreparedStatement state = null;
            try {
                SQLiteDatabase database = storage.getDatabase();
                if (database == null) {
                    return;
                }

                state = database.executeFast("INSERT INTO story_drafts VALUES (?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(draft.getObjectSize());
                draft.toStream(data);
                state.bindLong(1, id);
                state.bindLong(2, draft.date);
                state.bindByteBuffer(3, data);
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
            if (entry != null && now - entry.draftDate > EXPIRATION_PERIOD) {
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
                ids.add(entry.draftId);
                entry.destroy(true);
            }
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
        public long averageDuration;
        public ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
        public List<TLRPC.InputDocument> stickers;

        private String filterFilePath;
        private MediaController.SavedFilterState filterState;

        private int period;

        private final ArrayList<StoryEntry.Part> parts = new ArrayList<>();

        public StoryDraft(@NonNull StoryEntry entry) {
            this.id = entry.draftId;
            this.date = entry.draftDate;
            this.thumb = entry.draftThumbFile == null ? "" : entry.draftThumbFile.toString();
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
            this.captionEntities = MediaDataController.getInstance(entry.currentAccount).getEntities(new CharSequence[]{caption}, true);
            this.caption = caption == null ? "" : caption.toString();
            this.privacyRules.addAll(entry.privacyRules);
            this.paintFilePath = entry.paintFile == null ? "" : entry.paintFile.toString();
            this.averageDuration = entry.averageDuration;
            this.mediaEntities = entry.mediaEntities;
            this.stickers = entry.stickers;
            this.filterFilePath = entry.filterFile == null ? "" : entry.filterFile.toString();
            this.filterState = entry.filterState;
            this.period = entry.period;
            this.parts.clear();
            this.parts.addAll(entry.parts);
        }

        public StoryEntry toEntry() {
            StoryEntry entry = new StoryEntry();
            entry.draftId = id;
            entry.isDraft = true;
            entry.draftDate = date;
            if (thumb != null) {
                entry.draftThumbFile = new File(thumb);
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
            entry.averageDuration = averageDuration;
            entry.mediaEntities = mediaEntities;
            entry.stickers = stickers;
            if (filterFilePath != null) {
                entry.filterFile = new File(filterFilePath);
            }
            entry.filterState = filterState;
            entry.period = period;
            entry.parts.clear();
            entry.parts.addAll(parts);
            entry.partsMaxId = 0;
            for (int i = 0; i < parts.size(); ++i) {
                entry.partsMaxId = Math.max(entry.partsMaxId, parts.get(i).id);
            }
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
            stream.writeInt32(parts.size());
            for (int i = 0; i < parts.size(); ++i) {
                parts.get(i).serializeToStream(stream);
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
                mediaEntities.add(new VideoEditedInfo.MediaEntity(stream, true));
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
                parts.clear();
                for (int i = 0; i < count; ++i) {
                    StoryEntry.Part part = new StoryEntry.Part();
                    part.readParams(stream, exception);
                    parts.add(part);
                }
            }
        }
    }
}

package org.telegram.ui.web;

import android.util.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class BrowserHistory {

    public static class Entry extends TLObject {
        public long id;
        public long time;
        public String url;
        public WebMetadataCache.WebMetadata meta;

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt64(id);
            stream.writeInt64(time);
            stream.writeString(url == null ? "" : url);
            meta.serializeToStream(stream);
        }

        @Override
        public void readParams(InputSerializedData stream, boolean exception) {
            id = stream.readInt64(exception);
            time = stream.readInt64(exception);
            url = stream.readString(exception);
            meta = new WebMetadataCache.WebMetadata();
            meta.readParams(stream, exception);
        }
    }

    public static boolean historyLoading, historyLoaded;
    private static ArrayList<BrowserHistory.Entry> history;
    private static LongSparseArray<BrowserHistory.Entry> historyById;
    private static ArrayList<Utilities.Callback<ArrayList<BrowserHistory.Entry>>> callbacks;

    public static File getHistoryFile() {
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "webhistory.dat");
    }

    public static void preloadHistory() {
        if (historyLoading || historyLoaded) {
            return;
        }
        historyLoading = true;
        history = new ArrayList<>();
        historyById = new LongSparseArray<>();
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<Entry> history = new ArrayList<>();
            final LongSparseArray<Entry> historyById = new LongSparseArray<>();

            try {
                final File file = getHistoryFile();
                if (file.exists()) {
                    final SerializedData stream = new SerializedData(file);
                    final long count = stream.readInt64(true);
                    for (long i = 0; i < count; ++i) {
                        final BrowserHistory.Entry entry = new BrowserHistory.Entry();
                        entry.readParams(stream, true);
                        history.add(entry);
                        historyById.put(entry.id, entry);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                BrowserHistory.history.addAll(0, history);
                for (int i = 0; i < historyById.size(); ++i) {
                    BrowserHistory.historyById.put(historyById.keyAt(i), historyById.valueAt(i));
                }
                historyLoaded = true;
                historyLoading = false;
                if (callbacks != null) {
                    for (Utilities.Callback<ArrayList<BrowserHistory.Entry>> callback : callbacks) {
                        callback.run(history);
                    }
                    callbacks = null;
                }
            });
        });
    }

    public static ArrayList<BrowserHistory.Entry> getHistory() {
        return getHistory(null);
    }

    public static ArrayList<BrowserHistory.Entry> getHistory(Utilities.Callback<ArrayList<BrowserHistory.Entry>> loaded) {
        boolean addedCallback = false;
        if (loaded != null && !historyLoaded) {
            if (callbacks == null) {
                callbacks = new ArrayList<>();
            }
            callbacks.add(loaded);
            addedCallback = true;
        }
        preloadHistory();
        if (addedCallback) {
            return null;
        }
        return history;
    }

    public static void pushHistory(BrowserHistory.Entry entry) {
        if (entry == null || entry.meta == null) return;
        preloadHistory();
        BrowserHistory.Entry existingEntry = historyById.get(entry.id);
        if (existingEntry != null) {
            existingEntry.meta = entry.meta;
        } else {
            history.add(entry);
            historyById.put(entry.id, entry);
        }
        scheduleHistorySave();
    }

    private static void scheduleHistorySave() {
        AndroidUtilities.cancelRunOnUIThread(BrowserHistory::saveHistory);
        AndroidUtilities.runOnUIThread(BrowserHistory::saveHistory, 1_000);
    }

    private static void saveHistory() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File file = getHistoryFile();
                if (!file.exists()) {
                    file.createNewFile();
                }

                final long count = history.size();
                SerializedData astream = new SerializedData(true);
                astream.writeInt64(count);
                for (BrowserHistory.Entry e : history) {
                    e.serializeToStream(astream);
                }

                final SerializedData stream = new SerializedData(astream.length());
                stream.writeInt64(count);
                for (BrowserHistory.Entry e : history) {
                    e.serializeToStream(stream);
                }

                try {
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(stream.toByteArray());
                    os.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void clearHistory() {
        try {
            history.clear();
            historyById.clear();
            final File file = getHistoryFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

}

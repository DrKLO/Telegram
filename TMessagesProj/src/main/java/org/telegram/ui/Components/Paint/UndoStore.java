package org.telegram.ui.Components.Paint;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UndoStore {

    public interface UndoStoreDelegate {
        void historyChanged();
    }

    private UndoStoreDelegate delegate;
    private Map<UUID, Runnable> uuidToOperationMap = new HashMap<>();
    private List<UUID> operations = new ArrayList<>();

    public boolean canUndo() {
        return !operations.isEmpty();
    }

    public void setDelegate(UndoStoreDelegate undoStoreDelegate) {
        delegate = undoStoreDelegate;
    }

    public void registerUndo(UUID uuid, Runnable undoRunnable) {
        uuidToOperationMap.put(uuid, undoRunnable);
        operations.add(uuid);

        notifyOfHistoryChanges();
    }

    public void unregisterUndo(UUID uuid) {
        uuidToOperationMap.remove(uuid);
        operations.remove(uuid);

        notifyOfHistoryChanges();
    }

    public void undo() {
        if (operations.size() == 0) {
            return;
        }

        int lastIndex = operations.size() - 1;
        UUID uuid = operations.get(lastIndex);
        Runnable undoRunnable = uuidToOperationMap.get(uuid);
        uuidToOperationMap.remove(uuid);
        operations.remove(lastIndex);

        undoRunnable.run();
        notifyOfHistoryChanges();
    }

    public void reset() {
        operations.clear();
        uuidToOperationMap.clear();

        notifyOfHistoryChanges();
    }

    private void notifyOfHistoryChanges() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (delegate != null) {
                    delegate.historyChanged();
                }
            }
        });
    }
}

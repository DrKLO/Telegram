package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;

public class FileLoaderPriorityQueue {

    private final int maxActiveOperationsCount;
    String name;

    private ArrayList<FileLoadOperation> allOperations = new ArrayList<>();

    private int PRIORITY_VALUE_MAX = (1 << 20);
    private int PRIORITY_VALUE_NORMAL = (1 << 16);
    private int PRIORITY_VALUE_LOW = 0;

    FileLoaderPriorityQueue(String name, int maxActiveOperationsCount) {
        this.name = name;
        this.maxActiveOperationsCount = maxActiveOperationsCount;
    }

    public void add(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < allOperations.size(); i++) {
            if (allOperations.get(i) == operation) {
                allOperations.remove(i);
                i--;
            }
        }
        for (int i = 0; i < allOperations.size(); i++) {
            if (operation.getPriority() > allOperations.get(i).getPriority()) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            allOperations.add(index, operation);
        } else {
            allOperations.add(operation);
        }
    }

    public void cancel(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        if (allOperations.remove(operation)) {
            operation.cancel();
        }
    }

    public void checkLoadingOperations() {
        int activeCount = 0;
        int lastPriority = 0;
        boolean pauseAllNextOperations = false;
        int max = maxActiveOperationsCount;
        for (int i = 0; i < allOperations.size(); i++) {
            FileLoadOperation operation = allOperations.get(i);
            if (i > 0 && !pauseAllNextOperations) {
                if (lastPriority > PRIORITY_VALUE_LOW && operation.getPriority() == PRIORITY_VALUE_LOW) {
                    pauseAllNextOperations = true;
                }
            }
            if (operation.preFinished) {
                //operation will not use connections
                //just skip
                max++;
            } else if (!pauseAllNextOperations && i < max) {
                operation.start();
                activeCount++;
            } else {
                if (operation.wasStarted()) {
                    operation.pause();
                }
            }
            lastPriority = operation.getPriority();
        }
    }

    public void remove(FileLoadOperation operation) {
        if (operation == null) {
            return;
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(operation.currentAccount);
        if (connectionsManager != null && connectionsManager.getConnectionState() == ConnectionsManager.ConnectionStateWaitingForNetwork) {
            operation.cancel();
        }
        allOperations.remove(operation);
    }

    public int getCount() {
        return allOperations.size();
    }

    public int getPosition(FileLoadOperation fileLoadOperation) {
        return allOperations.indexOf(fileLoadOperation);
    }
}

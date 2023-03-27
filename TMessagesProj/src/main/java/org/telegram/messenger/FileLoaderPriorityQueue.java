package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Objects;

public class FileLoaderPriorityQueue {

    private final int maxActiveOperationsCount;
    String name;

    ArrayList<FileLoadOperation> allOperations = new ArrayList<>();

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
            if (allOperations.get(i) == operation || Objects.equals(allOperations.get(i).getFileName(), (operation.getFileName()))) {
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
        allOperations.remove(operation);
        operation.cancel();
    }

    public void checkLoadingOperations() {
        int activeCount = 0;
        int lastPriority = 0;
        boolean pauseAllNextOperations = false;
        for (int i = 0; i < allOperations.size(); i++) {
            FileLoadOperation operation = allOperations.get(i);
            if (i > 0 && !pauseAllNextOperations) {
                if (lastPriority > PRIORITY_VALUE_LOW && operation.getPriority() == PRIORITY_VALUE_LOW) {
                    pauseAllNextOperations = true;
                }
            }
            if (!pauseAllNextOperations && i < maxActiveOperationsCount) {
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

}

package org.telegram.messenger;

import java.util.ArrayList;

public class FileLoaderPriorityQueue {

    public static final int TYPE_SMALL = 0;
    public static final int TYPE_LARGE = 1;
    String name;
    int type;
    int currentAccount;

    private ArrayList<FileLoadOperation> allOperations = new ArrayList<>();

    private int PRIORITY_VALUE_MAX = (1 << 20);
    private int PRIORITY_VALUE_NORMAL = (1 << 16);
    private int PRIORITY_VALUE_LOW = 0;

    FileLoaderPriorityQueue(int currentAccount, String name, int type) {
        this.currentAccount = currentAccount;
        this.name = name;
        this.type = type;
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
        int max = type == TYPE_LARGE ? MessagesController.getInstance(currentAccount).largeQueueMaxActiveOperations : MessagesController.getInstance(currentAccount).smallQueueMaxActiveOperations;
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

    public boolean remove(FileLoadOperation operation) {
        if (operation == null) {
            return false;
        }
        return allOperations.remove(operation);
    }

    public int getCount() {
        return allOperations.size();
    }

    public int getPosition(FileLoadOperation fileLoadOperation) {
        return allOperations.indexOf(fileLoadOperation);
    }
}

    public void setTypingStatus(long dialogId, long userId, int durationMs) {
        if (userId == getUserConfig().getClientUserId()) {
            return;
        }
        long currentTime = getConnectionsManager().getCurrentTime();
        ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(dialogId);
        if (threads == null) {
            threads = new ConcurrentHashMap<>();
            printingUsers.put(dialogId, threads);
        }
        ArrayList<PrintingUser> arr = threads.get(0);
        if (arr == null) {
            arr = new ArrayList<>();
            threads.put(0, arr);
        }
        boolean exist = false;
        for (PrintingUser u : arr) {
            if (u.userId == userId) {
                exist = true;
                u.lastTime = currentTime;
                u.action = new TLRPC.TL_sendMessageTypingAction();
                break;
            }
        }
        if (!exist) {
            PrintingUser newUser = new PrintingUser();
            newUser.userId = userId;
            newUser.lastTime = currentTime;
            newUser.action = new TLRPC.TL_sendMessageTypingAction();
            arr.add(newUser);
        }
        updatePrintingStrings();
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);

        if (durationMs > 0) {
            AndroidUtilities.runOnUIThread(() -> clearTypingStatus(dialogId, userId), durationMs);
        }
    }

    private void clearTypingStatus(long dialogId, long userId) {
        ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(dialogId);
        if (threads == null) {
            return;
        }
        ArrayList<PrintingUser> arr = threads.get(0);
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                PrintingUser pu = arr.get(i);
                if (pu.userId == userId) {
                    arr.remove(i);
                    break;
                }
            }
            if (arr.isEmpty()) {
                threads.remove(0);
            }
        }
        if (threads.isEmpty()) {
            printingUsers.remove(dialogId);
        }
        updatePrintingStrings();
        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
    }


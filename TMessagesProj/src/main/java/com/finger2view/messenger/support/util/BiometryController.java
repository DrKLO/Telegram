package com.finger2view.messenger.support.util;

/**
 * Created by paulorodenas on 10/29/15.
 */
public class BiometryController {

    private static BiometryController instance;
    private boolean unlocked;

    private BiometryController() {
        unlocked = false;
    }

    public static BiometryController getInstance() {
        if(instance == null)
            instance = new BiometryController();
        return instance;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }
}

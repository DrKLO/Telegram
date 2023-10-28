package org.telegram.ui;

import android.app.Activity;
import android.view.ViewGroup;

public abstract class IUpdateLayout {
    public IUpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {}
    public void updateFileProgress(Object[] args) {}
    public void createUpdateUI(int currentAccount) {}
    public void updateAppUpdateViews(int currentAccount, boolean animated) {}
}

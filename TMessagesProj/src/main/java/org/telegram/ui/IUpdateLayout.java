package org.telegram.ui;

import android.app.Activity;
import android.view.ViewGroup;

import androidx.annotation.Keep;

@Keep
public abstract class IUpdateLayout {
    @Keep
    public IUpdateLayout(Activity activity, ViewGroup sideMenu, ViewGroup sideMenuContainer) {}
    @Keep
    public void updateFileProgress(Object[] args) {}
    @Keep
    public void createUpdateUI(int currentAccount) {}
    @Keep
    public void updateAppUpdateViews(int currentAccount, boolean animated) {}
}

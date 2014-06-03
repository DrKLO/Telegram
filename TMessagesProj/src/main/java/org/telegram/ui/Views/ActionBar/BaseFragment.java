/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.ConnectionsManager;

public class BaseFragment {
    private boolean isFinished = false;
    protected View fragmentView;
    private ActionBarActivity parentActivity;
    protected ActionBarLayer actionBarLayer;
    protected int classGuid = 0;
    protected Bundle arguments;

    public BaseFragment() {
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
    }

    public BaseFragment(Bundle args) {
        arguments = args;
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
    }

    public View createView(LayoutInflater inflater, ViewGroup container) {
        return null;
    }

    public Bundle getArguments() {
        return arguments;
    }

    public void setParentActivity(ActionBarActivity activity) {
        if (parentActivity != activity) {
            parentActivity = activity;
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragmentView);
                }
                fragmentView = null;
            }
            if (parentActivity != null) {
                if (actionBarLayer != null) {
                    actionBarLayer.onDestroy();
                    actionBarLayer = null;
                }
                actionBarLayer = parentActivity.getInternalActionBar().createLayer();
            }
        }
    }

    public void finishFragment() {
        if (isFinished || parentActivity == null) {
            return;
        }
        parentActivity.closeLastFragment();
    }

    public void removeSelfFromStack() {
        if (isFinished || parentActivity == null) {
            return;
        }
        parentActivity.removeFragmentFromStack(this);
    }

    public boolean onFragmentCreate() {
        return true;
    }

    public void onFragmentDestroy() {
        ConnectionsManager.getInstance().cancelRpcsForClassGuid(classGuid);
        isFinished = true;
        if (actionBarLayer != null) {
            actionBarLayer.setEnabled(false);
        }
    }

    public void onResume() {

    }

    public void onPause() {

    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {

    }

    public boolean onBackPressed() {
        return true;
    }

    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {

    }

    public void saveSelfArgs(Bundle args) {

    }

    public void restoreSelfArgs(Bundle args) {

    }

    public void presentFragment(BaseFragment fragment) {
        if (parentActivity == null) {
            return;
        }
        parentActivity.presentFragment(fragment);
    }

    public void presentFragment(BaseFragment fragment, boolean removeLast) {
        if (parentActivity == null) {
            return;
        }
        parentActivity.presentFragment(fragment, removeLast);
    }

    public void presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        if (parentActivity == null) {
            return;
        }
        parentActivity.presentFragment(fragment, removeLast, forceWithoutAnimation);
    }

    public ActionBarActivity getParentActivity() {
        return parentActivity;
    }

    public void showActionBar() {
        if (parentActivity != null) {
            parentActivity.showActionBar();
        }
    }

    public void hideActionBar() {
        if (parentActivity != null) {
            parentActivity.hideActionBar();
        }
    }

    public void onBeginSlide() {

    }

    public void onOpenAnimationEnd() {

    }

    public void onLowMemory() {

    }
}

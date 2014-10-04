/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views.ActionBar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class BaseFragment {
    private boolean isFinished = false;
    protected View fragmentView;
    protected ActionBarLayout parentLayout;
    protected ActionBarLayer actionBarLayer;
    protected int classGuid = 0;
    protected Bundle arguments;
    private AlertDialog visibleDialog = null;
    protected boolean swipeBackEnabled = true;

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

    public void setParentLayout(ActionBarLayout layout) {
        if (parentLayout != layout) {
            parentLayout = layout;
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragmentView);
                }
                fragmentView = null;
            }
            if (parentLayout != null) {
                if (actionBarLayer != null) {
                    actionBarLayer.onDestroy();
                }
                actionBarLayer = parentLayout.getInternalActionBar().createLayer();
                actionBarLayer.parentFragment = this;
                actionBarLayer.setBackgroundResource(R.color.header);
                actionBarLayer.setItemsBackground(R.drawable.bar_selector);
            }
        }
    }

    public void finishFragment() {
        finishFragment(true);
    }

    public void finishFragment(boolean animated) {
        if (isFinished || parentLayout == null) {
            return;
        }
        parentLayout.closeLastFragment(animated);
    }

    public void removeSelfFromStack() {
        if (isFinished || parentLayout == null) {
            return;
        }
        parentLayout.removeFragmentFromStack(this);
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
        if (actionBarLayer != null) {
            actionBarLayer.onPause();
            actionBarLayer.closeSearchField();
        }
        try {
            if (visibleDialog != null && visibleDialog.isShowing()) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
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

    public boolean presentFragment(BaseFragment fragment) {
        return parentLayout != null && parentLayout.presentFragment(fragment);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return parentLayout != null && parentLayout.presentFragment(fragment, removeLast);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    public Activity getParentActivity() {
        if (parentLayout != null) {
            return parentLayout.parentActivity;
        }
        return null;
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentLayout != null) {
            parentLayout.startActivityForResult(intent, requestCode);
        }
    }

    public void showActionBar() {
        if (parentLayout != null) {
            parentLayout.showActionBar();
        }
    }

    public void hideActionBar() {
        if (parentLayout != null) {
            parentLayout.hideActionBar();
        }
    }

    public void onBeginSlide() {
        try {
            if (visibleDialog != null && visibleDialog.isShowing()) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (actionBarLayer != null) {
            actionBarLayer.onPause();
        }
    }

    public void onOpenAnimationEnd() {

    }

    public void onLowMemory() {

    }

    protected void showAlertDialog(AlertDialog.Builder builder) {
        if (parentLayout == null || parentLayout.checkTransitionAnimation() || parentLayout.animationInProgress || parentLayout.startedTracking) {
            return;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        visibleDialog = builder.show();
        visibleDialog.setCanceledOnTouchOutside(true);
        visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                visibleDialog = null;
                onDialogDismiss();
            }
        });
    }

    protected void onDialogDismiss() {

    }
}

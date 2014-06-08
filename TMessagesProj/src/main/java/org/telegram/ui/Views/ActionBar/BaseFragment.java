/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views.ActionBar;

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
    private ActionBarActivity parentActivity;
    protected ActionBarLayer actionBarLayer;
    protected int classGuid = 0;
    protected Bundle arguments;
    private AlertDialog visibleDialog = null;

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
                }
                actionBarLayer = parentActivity.getInternalActionBar().createLayer();
                actionBarLayer.setBackgroundResource(R.color.header);
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
        if (parentActivity == null || parentActivity.checkTransitionAnimation() || parentActivity.animationInProgress || parentActivity.startedTracking) {
            return;
        }
        if (visibleDialog != null && visibleDialog.isShowing()) {
            visibleDialog.dismiss();
            visibleDialog = null;
        }
        visibleDialog = builder.show();
        visibleDialog.setCanceledOnTouchOutside(true);
        visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                visibleDialog = null;
            }
        });
    }
}

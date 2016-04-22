/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.ConnectionsManager;

public class BaseFragment {

    private boolean isFinished = false;
    protected Dialog visibleDialog = null;

    protected View fragmentView;
    protected ActionBarLayout parentLayout;
    protected ActionBar actionBar;
    protected int classGuid = 0;
    protected Bundle arguments;
    protected boolean swipeBackEnabled = true;
    protected boolean hasOwnBackground = false;

    public BaseFragment() {
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
    }

    public BaseFragment(Bundle args) {
        arguments = args;
        classGuid = ConnectionsManager.getInstance().generateClassGuid();
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public View createView(Context context) {
        return null;
    }

    public Bundle getArguments() {
        return arguments;
    }

    protected void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    parent.removeView(fragmentView);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            fragmentView = null;
        }
        if (actionBar != null) {
            ViewGroup parent = (ViewGroup) actionBar.getParent();
            if (parent != null) {
                try {
                    parent.removeView(actionBar);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            actionBar = null;
        }
        parentLayout = null;
    }

    protected void setParentLayout(ActionBarLayout layout) {
        if (parentLayout != layout) {
            parentLayout = layout;
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    try {
                        parent.removeView(fragmentView);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                if (parentLayout != null && parentLayout.getContext() != fragmentView.getContext()) {
                    fragmentView = null;
                }
            }
            if (actionBar != null) {
                ViewGroup parent = (ViewGroup) actionBar.getParent();
                if (parent != null) {
                    try {
                        parent.removeView(actionBar);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
                if (parentLayout != null && parentLayout.getContext() != actionBar.getContext()) {
                    actionBar = null;
                }
            }
            if (parentLayout != null && actionBar == null) {
                actionBar = createActionBar(parentLayout.getContext());
                actionBar.parentFragment = this;
            }
        }
    }

    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_COLOR);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_SELECTOR_COLOR);
        return actionBar;
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
        ConnectionsManager.getInstance().cancelRequestsForGuid(classGuid);
        isFinished = true;
        if (actionBar != null) {
            actionBar.setEnabled(false);
        }
    }

    public void onResume() {

    }

    public void onPause() {
        if (actionBar != null) {
            actionBar.onPause();
        }
        try {
            if (visibleDialog != null && visibleDialog.isShowing() && dismissDialogOnPause(visibleDialog)) {
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

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {

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

    public boolean dismissDialogOnPause(Dialog dialog) {
        return true;
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
        if (actionBar != null) {
            actionBar.onPause();
        }
    }

    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {

    }

    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {

    }

    protected void onBecomeFullyVisible() {

    }

    protected AnimatorSetProxy onCustomTransitionAnimation(boolean isOpen, final Runnable callback) {
        return null;
    }

    public void onLowMemory() {

    }

    public Dialog showDialog(Dialog dialog) {
        return showDialog(dialog, false);
    }

    public Dialog showDialog(Dialog dialog, boolean allowInTransition) {
        if (dialog == null || parentLayout == null || parentLayout.animationInProgress || parentLayout.startedTracking || !allowInTransition && parentLayout.checkTransitionAnimation()) {
            return null;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        try {
            visibleDialog = dialog;
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    onDialogDismiss(visibleDialog);
                    visibleDialog = null;
                }
            });
            visibleDialog.show();
            return visibleDialog;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    protected void onDialogDismiss(Dialog dialog) {

    }

    public Dialog getVisibleDialog() {
        return visibleDialog;
    }

    public void setVisibleDialog(Dialog dialog) {
        visibleDialog = dialog;
    }
}

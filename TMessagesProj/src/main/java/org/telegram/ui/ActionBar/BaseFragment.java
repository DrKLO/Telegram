/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.StoryViewer;

import java.util.ArrayList;

public abstract class BaseFragment {

    private boolean isFinished;
    protected boolean finishing;
    protected Dialog visibleDialog;
    protected int currentAccount = UserConfig.selectedAccount;

    protected View fragmentView;
    protected INavigationLayout parentLayout;
    protected ActionBar actionBar;
    protected boolean inPreviewMode;
    protected boolean inMenuMode;
    protected boolean inBubbleMode;
    protected int classGuid;
    protected Bundle arguments;
    protected boolean hasOwnBackground = false;
    protected boolean isPaused = true;
    protected Dialog parentDialog;
    protected boolean inTransitionAnimation = false;
    protected boolean fragmentBeginToShow;
    private boolean removingFromStack;
    private PreviewDelegate previewDelegate;
    protected Theme.ResourcesProvider resourceProvider;
    public StoryViewer storyViewer;
    public StoryViewer overlayStoryViewer;

    public BaseFragment() {
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public BaseFragment(Bundle args) {
        arguments = args;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public void setCurrentAccount(int account) {
        if (fragmentView != null) {
            throw new IllegalStateException("trying to set current account when fragment UI already created");
        }
        currentAccount = account;
    }

    public boolean hasOwnBackground() {
        return hasOwnBackground;
    }

    public void setHasOwnBackground(boolean hasOwnBackground) {
        this.hasOwnBackground = hasOwnBackground;
    }

    public boolean getFragmentBeginToShow() {
        return fragmentBeginToShow;
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public void setFragmentView(View fragmentView) {
        this.fragmentView = fragmentView;
    }

    public View createView(Context context) {
        return null;
    }

    public Bundle getArguments() {
        return arguments;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public int getClassGuid() {
        return classGuid;
    }

    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    public void setInBubbleMode(boolean value) {
        inBubbleMode = value;
    }

    public boolean isInBubbleMode() {
        return inBubbleMode;
    }

    public boolean isInPreviewMode() {
        return inPreviewMode;
    }

    public boolean getInPassivePreviewMode() {
        return parentLayout != null && parentLayout.isInPassivePreviewMode();
    }

    public boolean isActionBarCrossfadeEnabled() {
        return actionBar != null;
    }

    public INavigationLayout.BackButtonState getBackButtonState() {
        return actionBar != null ? actionBar.getBackButtonState() : null;
    }

    public void setInPreviewMode(boolean value) {
        inPreviewMode = value;
        if (actionBar != null) {
            if (inPreviewMode) {
                actionBar.setOccupyStatusBar(false);
            } else {
                actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21);
            }
        }
    }

    public void setInMenuMode(boolean value) {
        inMenuMode = value;
    }

    public void onPreviewOpenAnimationEnd() {
    }

    protected boolean hideKeyboardOnShow() {
        return true;
    }

    public void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    onRemoveFromParent();
                    parent.removeViewInLayout(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            fragmentView = null;
        }
        if (actionBar != null) {
            ViewGroup parent = (ViewGroup) actionBar.getParent();
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            actionBar = null;
        }
        if (storyViewer != null) {
            storyViewer.release();
            storyViewer = null;
        }
        if (overlayStoryViewer != null) {
            overlayStoryViewer.release();
            overlayStoryViewer = null;
        }
        parentLayout = null;
    }

    public void onRemoveFromParent() {

    }

    public void setParentFragment(BaseFragment fragment) {
        setParentLayout(fragment.parentLayout);
        fragmentView = createView(parentLayout.getView().getContext());
    }

    public void setParentLayout(INavigationLayout layout) {
        if (parentLayout != layout) {
            parentLayout = layout;
            inBubbleMode = parentLayout != null && parentLayout.isInBubbleMode();
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    try {
                        onRemoveFromParent();
                        parent.removeViewInLayout(fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (parentLayout != null && parentLayout.getView().getContext() != fragmentView.getContext()) {
                    fragmentView = null;
                    if (storyViewer != null) {
                        storyViewer.release();
                        storyViewer = null;
                    }
                    if (overlayStoryViewer != null) {
                        overlayStoryViewer.release();
                        overlayStoryViewer = null;
                    }
                }
            }
            if (actionBar != null) {
                boolean differentParent = parentLayout != null && parentLayout.getView().getContext() != actionBar.getContext();
                if (actionBar.shouldAddToContainer() || differentParent) {
                    ViewGroup parent = (ViewGroup) actionBar.getParent();
                    if (parent != null) {
                        try {
                            parent.removeViewInLayout(actionBar);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
                if (differentParent) {
                    actionBar = null;
                }
            }
            if (parentLayout != null && actionBar == null) {
                actionBar = createActionBar(parentLayout.getView().getContext());
                if (actionBar != null) {
                    actionBar.parentFragment = this;
                }
            }
        }
    }

    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context, getResourceProvider());
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), true);
        if (inPreviewMode || inBubbleMode) {
            actionBar.setOccupyStatusBar(false);
        }
        return actionBar;
    }

    public void movePreviewFragment(float dy) {
        parentLayout.movePreviewFragment(dy);
    }

    public void finishPreviewFragment() {
        if (parentLayout != null) {
            parentLayout.finishPreviewFragment();
        }
    }

    public void finishFragment() {
        if (parentDialog != null) {
            parentDialog.dismiss();
            return;
        }
        if (inPreviewMode && previewDelegate != null) {
            previewDelegate.finishFragment();
        } else {
            finishFragment(true);
        }
    }

    public void setFinishing(boolean finishing) {
        this.finishing = finishing;
    }

    public boolean finishFragment(boolean animated) {
        if (isFinished || parentLayout == null) {
            return false;
        }
        finishing = true;
        parentLayout.closeLastFragment(animated);
        return true;
    }

    public void removeSelfFromStack() {
        removeSelfFromStack(false);
    }

    public void removeSelfFromStack(boolean immediate) {
        if (isFinished || parentLayout == null) {
            return;
        }
        if (parentDialog != null) {
            parentDialog.dismiss();
            return;
        }
        parentLayout.removeFragmentFromStack(this, immediate);
    }

    public boolean allowFinishFragmentInsteadOfRemoveFromStack() {
        return true;
    }

    protected boolean isFinishing() {
        return finishing;
    }

    public boolean onFragmentCreate() {
        return true;
    }

    @CallSuper
    public void onFragmentDestroy() {
        getConnectionsManager().cancelRequestsForGuid(classGuid);
        getMessagesStorage().cancelTasksForGuid(classGuid);
        isFinished = true;
        if (actionBar != null) {
            actionBar.setEnabled(false);
        }

        if (hasForceLightStatusBar() && !AndroidUtilities.isTablet() && getParentLayout().getLastFragment() == this && getParentActivity() != null && !finishing) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), Theme.getColor(Theme.key_actionBarDefault) == Color.WHITE);
        }
    }

    public boolean needDelayOpenAnimation() {
        return false;
    }

    protected void resumeDelayedFragmentAnimation() {
        if (parentLayout != null) {
            parentLayout.resumeDelayedFragmentAnimation();
        }
    }

    public void onUserLeaveHint() {}

    @CallSuper
    public void onResume() {
        isPaused = false;
        if (actionBar != null) {
            actionBar.onResume();
        }
        if (storyViewer != null) {
            storyViewer.onResume();
            storyViewer.updatePlayingMode();
        }
        if (overlayStoryViewer != null) {
            overlayStoryViewer.updatePlayingMode();
        }
    }

    @CallSuper
    public void onPause() {
        if (actionBar != null) {
            actionBar.onPause();
        }
        isPaused = true;
        try {
            if (visibleDialog != null && visibleDialog.isShowing() && dismissDialogOnPause(visibleDialog)) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (storyViewer != null) {
            storyViewer.onPause();
            storyViewer.updatePlayingMode();
        }
        if (overlayStoryViewer != null) {
            overlayStoryViewer.updatePlayingMode();
        }
    }

    public void setPaused(boolean paused) {
        if (isPaused == paused) {
            return;
        }

        if (paused) {
            onPause();
        } else {
            onResume();
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public BaseFragment getFragmentForAlert(int offset) {
        if (parentLayout == null || parentLayout.getFragmentStack().size() <= 1 + offset) {
            return this;
        }
        return parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2 - offset);
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {

    }

    public boolean onBackPressed() {
        if (closeStoryViewer()) {
            return false;
        }
        return true;
    }

    public boolean closeStoryViewer() {
        if (overlayStoryViewer != null && overlayStoryViewer.isShown()) {
            return overlayStoryViewer.onBackPressed();
        }
        if (storyViewer != null && storyViewer.isShown()) {
            return storyViewer.onBackPressed();
        }
        return false;
    }

    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {

    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {

    }

    public void saveSelfArgs(Bundle args) {

    }

    public void restoreSelfArgs(Bundle args) {

    }

    public boolean isLastFragment() {
        return parentLayout != null && parentLayout.getLastFragment() == this;
    }

    public INavigationLayout getParentLayout() {
        return parentLayout;
    }

    public FrameLayout getLayoutContainer() {
        if (fragmentView != null) {
            final ViewParent parent = fragmentView.getParent();
            if (parent instanceof FrameLayout) {
                return (FrameLayout) parent;
            }
        }
        return null;
    }

    public boolean presentFragmentAsPreview(BaseFragment fragment) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragmentAsPreview(fragment);
    }

    public boolean presentFragmentAsPreviewWithMenu(BaseFragment fragment, ActionBarPopupWindow.ActionBarPopupWindowLayout menu) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragmentAsPreviewWithMenu(fragment, menu);
    }

    public boolean presentFragment(BaseFragment fragment) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true, false, null);
    }

    public boolean presentFragment(INavigationLayout.NavigationParams params) {
        return allowPresentFragment() && parentLayout != null && parentLayout.presentFragment(params);
    }

    public Activity getParentActivity() {
        if (parentLayout != null) {
            return parentLayout.getParentActivity();
        }
        return null;
    }

    public Context getContext() {
        return getParentActivity();
    }

    protected void setParentActivityTitle(CharSequence title) {
        Activity activity = getParentActivity();
        if (activity != null) {
            activity.setTitle(title);
        }
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentLayout != null) {
            parentLayout.startActivityForResult(intent, requestCode);
        }
    }

    public void dismissCurrentDialog() {
        if (visibleDialog == null) {
            return;
        }
        try {
            visibleDialog.dismiss();
            visibleDialog = null;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean dismissDialogOnPause(Dialog dialog) {
        return true;
    }

    public boolean canBeginSlide() {
        return true;
    }

    public void onBeginSlide() {
        try {
            if (visibleDialog != null && visibleDialog.isShowing()) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (actionBar != null) {
            actionBar.onPause();
        }
    }

    public void onSlideProgress(boolean isOpen, float progress) {

    }

    public void onSlideProgressFront(boolean isOpen, float progress) {

    }

    public void onTransitionAnimationProgress(boolean isOpen, float progress) {

    }

    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        inTransitionAnimation = true;
        if (isOpen) {
            fragmentBeginToShow = true;
        }
    }

    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        inTransitionAnimation = false;
    }

    public void onBecomeFullyVisible() {
        AccessibilityManager mgr = (AccessibilityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (mgr.isEnabled()) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                String title = actionBar.getTitle();
                if (!TextUtils.isEmpty(title)) {
                    setParentActivityTitle(title);
                }
            }
        }
    }

    public int getPreviewHeight() {
        return LayoutHelper.MATCH_PARENT;
    }

    public void onBecomeFullyHidden() {

    }

    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        return null;
    }

    public void onLowMemory() {

    }

    public Dialog showDialog(Dialog dialog) {
        return showDialog(dialog, false, null);
    }

    public Dialog showDialog(Dialog dialog, Dialog.OnDismissListener onDismissListener) {
        return showDialog(dialog, false, onDismissListener);
    }

    public Dialog showDialog(Dialog dialog, boolean allowInTransition, final Dialog.OnDismissListener onDismissListener) {
        if (dialog == null || parentLayout == null || parentLayout.isTransitionAnimationInProgress() || parentLayout.isSwipeInProgress() || !allowInTransition && parentLayout.checkTransitionAnimation()) {
            return null;
        }
        if (overlayStoryViewer != null && overlayStoryViewer.isShown()) {
            overlayStoryViewer.showDialog(dialog);
            return dialog;
        }
        if (storyViewer != null && storyViewer.isShown()) {
            storyViewer.showDialog(dialog);
            return dialog;
        }
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            visibleDialog = dialog;
            visibleDialog.setCanceledOnTouchOutside(true);
            visibleDialog.setOnDismissListener(dialog1 -> {
                if (onDismissListener != null) {
                    onDismissListener.onDismiss(dialog1);
                }
                onDialogDismiss((Dialog) dialog1);
                if (dialog1 == visibleDialog) {
                    visibleDialog = null;
                }
            });
            visibleDialog.show();
            return visibleDialog;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    protected void onDialogDismiss(Dialog dialog) {

    }

    protected void onPanTranslationUpdate(float y) {

    }

    protected void onPanTransitionStart() {

    }

    protected void onPanTransitionEnd() {

    }

    public Dialog getVisibleDialog() {
        return visibleDialog;
    }

    public void setVisibleDialog(Dialog dialog) {
        visibleDialog = dialog;
    }

    public boolean extendActionMode(Menu menu) {
        return false;
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    public MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    protected ContactsController getContactsController() {
        return getAccountInstance().getContactsController();
    }

    public MediaDataController getMediaDataController() {
        return getAccountInstance().getMediaDataController();
    }

    public ConnectionsManager getConnectionsManager() {
        return getAccountInstance().getConnectionsManager();
    }

    public LocationController getLocationController() {
        return getAccountInstance().getLocationController();
    }

    protected NotificationsController getNotificationsController() {
        return getAccountInstance().getNotificationsController();
    }

    public MessagesStorage getMessagesStorage() {
        return getAccountInstance().getMessagesStorage();
    }

    public SendMessagesHelper getSendMessagesHelper() {
        return getAccountInstance().getSendMessagesHelper();
    }

    public FileLoader getFileLoader() {
        return getAccountInstance().getFileLoader();
    }

    protected SecretChatHelper getSecretChatHelper() {
        return getAccountInstance().getSecretChatHelper();
    }

    public DownloadController getDownloadController() {
        return getAccountInstance().getDownloadController();
    }

    protected SharedPreferences getNotificationsSettings() {
        return getAccountInstance().getNotificationsSettings();
    }

    public NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    public MediaController getMediaController() {
        return MediaController.getInstance();
    }

    public UserConfig getUserConfig() {
        return getAccountInstance().getUserConfig();
    }

    public void setFragmentPanTranslationOffset(int offset) {
        if (parentLayout != null) {
            parentLayout.setFragmentPanTranslationOffset(offset);
        }
    }

    public void saveKeyboardPositionBeforeTransition() {

    }

    protected Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        return null;
    }

    protected boolean shouldOverrideSlideTransition(boolean topFragment, boolean backAnimation) {
        return false;
    }

    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {

    }

    public void setProgressToDrawerOpened(float v) {

    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment) {
        return showAsSheet(fragment, null);
    }

    public INavigationLayout[] showAsSheet(BaseFragment fragment, BottomSheetParams params) {
        if (getParentActivity() == null) {
            return null;
        }
        BottomSheet[] bottomSheet = new BottomSheet[1];
        INavigationLayout[] actionBarLayout = new INavigationLayout[]{INavigationLayout.newLayout(getParentActivity(), () -> bottomSheet[0])};
        actionBarLayout[0].setIsSheet(true);
        LaunchActivity.instance.sheetFragmentsStack.add(actionBarLayout[0]);
        fragment.onTransitionAnimationStart(true, false);
        bottomSheet[0] = new BottomSheet(getParentActivity(), true, fragment.getResourceProvider()) {
            {
                occupyNavigationBar = params != null && params.occupyNavigationBar;
                drawNavigationBar = !occupyNavigationBar;
                actionBarLayout[0].setFragmentStack(new ArrayList<>());
                actionBarLayout[0].addFragmentToStack(fragment);
                actionBarLayout[0].showLastFragment();
                actionBarLayout[0].getView().setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                ViewGroup view = actionBarLayout[0].getView();
                containerView = view;
                setApplyBottomPadding(false);
                setOnDismissListener(dialog -> {
                    fragment.onPause();
                    fragment.onFragmentDestroy();
                    if (params != null && params.onDismiss != null) {
                        params.onDismiss.run();
                    }
                });
            }

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                actionBarLayout[0].setWindow(bottomSheet[0].getWindow());
                if (params == null || !params.occupyNavigationBar) {
                    fixNavigationBar(Theme.getColor(Theme.key_dialogBackgroundGray, fragment.getResourceProvider()));
                } else {
                    AndroidUtilities.setLightNavigationBar(bottomSheet[0].getWindow(), true);
                }
                AndroidUtilities.setLightStatusBar(getWindow(), fragment.isLightStatusBar());
                fragment.onBottomSheetCreated();
            }

            @Override
            protected boolean canDismissWithSwipe() {
                return false;
            }

            @Override
            protected boolean canSwipeToBack(MotionEvent event) {
                if (params != null && params.transitionFromLeft && actionBarLayout[0] != null && actionBarLayout[0].getFragmentStack().size() <= 1) {
                    if (actionBarLayout[0].getFragmentStack().size() == 1) {
                        BaseFragment lastFragment = actionBarLayout[0].getFragmentStack().get(0);
                        if (!lastFragment.isSwipeBackEnabled(event)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onBackPressed() {
                if (actionBarLayout[0] == null || actionBarLayout[0].getFragmentStack().size() <= 1) {
                    super.onBackPressed();
                } else {
                    actionBarLayout[0].onBackPressed();
                }
            }

            @Override
            public void dismiss() {
                if (!isDismissed()) {
                    if (params != null && params.onPreFinished != null) {
                        params.onPreFinished.run();
                    }
                }
                super.dismiss();
                LaunchActivity.instance.sheetFragmentsStack.remove(actionBarLayout[0]);
                actionBarLayout[0] = null;
            }

            @Override
            public void onOpenAnimationEnd() {
                fragment.onTransitionAnimationEnd(true, false);
                if (params != null && params.onOpenAnimationFinished != null) {
                    params.onOpenAnimationFinished.run();
                }
            }

            @Override
            protected void onInsetsChanged() {
                if (actionBarLayout[0] != null) {
                    for (BaseFragment baseFragment : actionBarLayout[0].getFragmentStack()) {
                        if (baseFragment.getFragmentView() != null) {
                            baseFragment.getFragmentView().requestLayout();
                        }
                    }
                }
            }
        };
        if (params != null) {
            bottomSheet[0].setAllowNestedScroll(params.allowNestedScroll);
            bottomSheet[0].transitionFromRight(params.transitionFromLeft);
        }
        fragment.setParentDialog(bottomSheet[0]);
        bottomSheet[0].setOpenNoDelay(true);
        bottomSheet[0].show();

        return actionBarLayout;
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, getResourceProvider());
    }

    public Paint getThemedPaint(String paintKey) {
        Paint paint = getResourceProvider() != null ? getResourceProvider().getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public Drawable getThemedDrawable(String key) {
        return Theme.getThemeDrawable(key);
    }

    /**
     * @return If this fragment should have light status bar even if it's disabled in debug settings
     */
    public boolean hasForceLightStatusBar() {
        return false;
    }

    public int getNavigationBarColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider());
        if (storyViewer != null && storyViewer.attachedToParent()) {
            return storyViewer.getNavigationBarColor(color);
        }
        return color;
    }

    public void setNavigationBarColor(int color) {
        Activity activity = getParentActivity();
        if (activity instanceof LaunchActivity) {
            LaunchActivity launchActivity = (LaunchActivity) activity;
            launchActivity.setNavigationBarColor(color, true);
        } else {
            if (activity != null) {
                Window window = activity.getWindow();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null && window.getNavigationBarColor() != color) {
                    window.setNavigationBarColor(color);
                    final float brightness = AndroidUtilities.computePerceivedBrightness(color);
                    AndroidUtilities.setLightNavigationBar(window, brightness >= 0.721f);
                }
            }
        }
    }

    public boolean isBeginToShow() {
        return fragmentBeginToShow;
    }

    private void setParentDialog(Dialog dialog) {
        parentDialog = dialog;
    }

    public Theme.ResourcesProvider getResourceProvider() {
        return resourceProvider;
    }

    protected boolean allowPresentFragment() {
        return true;
    }

    public boolean isRemovingFromStack() {
        return removingFromStack;
    }

    public void setRemovingFromStack(boolean b) {
        removingFromStack = b;
    }

    public boolean isLightStatusBar() {
        if (storyViewer != null && storyViewer.isShown()) {
            return false;
        }
        if (hasForceLightStatusBar() && !Theme.getCurrentTheme().isDark()) {
            return true;
        }
        Theme.ResourcesProvider resourcesProvider = getResourceProvider();
        int color;
        int key = Theme.key_actionBarDefault;
        if (actionBar != null && actionBar.isActionModeShowed()) {
            key = Theme.key_actionBarActionModeDefault;
        }
        if (resourcesProvider != null) {
            color = resourcesProvider.getColorOrDefault(key);
        } else {
            color = Theme.getColor(key, null, true);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    public void drawOverlay(Canvas canvas, View parent) {

    }

    public void setPreviewOpenedProgress(float progress) {

    }

    public void setPreviewReplaceProgress(float progress) {

    }

    public boolean closeLastFragment() {
        return false;
    }

    public void setPreviewDelegate(PreviewDelegate previewDelegate) {
        this.previewDelegate = previewDelegate;
    }

    public void resetFragment() {
        if (isFinished) {
            clearViews();
            isFinished = false;
            finishing = false;
        }
    }

    public void setResourceProvider(Theme.ResourcesProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    public void onFragmentClosed() {

    }

    public void attachStoryViewer(ActionBarLayout.LayoutContainer parentLayout) {
        if (storyViewer != null && storyViewer.attachedToParent()) {
            AndroidUtilities.removeFromParent(storyViewer.windowView);
            parentLayout.addView(storyViewer.windowView);
        }
        if (overlayStoryViewer != null && overlayStoryViewer.attachedToParent()) {
            AndroidUtilities.removeFromParent(overlayStoryViewer.windowView);
            parentLayout.addView(overlayStoryViewer.windowView);
        }
    }

    public void detachStoryViewer() {
        if (storyViewer != null && storyViewer.attachedToParent()) {
            AndroidUtilities.removeFromParent(storyViewer.windowView);
        }
        if (overlayStoryViewer != null && overlayStoryViewer.attachedToParent()) {
            AndroidUtilities.removeFromParent(overlayStoryViewer.windowView);
        }
    }

    public boolean isStoryViewer(View child) {
        if (storyViewer != null && child == storyViewer.windowView) {
            return true;
        }
        if (overlayStoryViewer != null && child == overlayStoryViewer.windowView) {
            return true;
        }
        return false;
    }

    public void setKeyboardHeightFromParent(int keyboardHeight) {
        if (storyViewer != null) {
            storyViewer.setKeyboardHeightFromParent(keyboardHeight);
        }
        if (overlayStoryViewer != null) {
            overlayStoryViewer.setKeyboardHeightFromParent(keyboardHeight);
        }
    }

    public interface PreviewDelegate {
        void finishFragment();
    }

    public StoryViewer getOrCreateStoryViewer() {
        if (storyViewer == null) {
            storyViewer = new StoryViewer(this);
            if (parentLayout != null && parentLayout.isSheet()) {
                storyViewer.fromBottomSheet = true;
            }
        }
        return storyViewer;
    }

    public StoryViewer getOrCreateOverlayStoryViewer() {
        if (overlayStoryViewer == null) {
            overlayStoryViewer = new StoryViewer(this);
        }
        return overlayStoryViewer;
    }

    public void onBottomSheetCreated() {

    }

    public static class BottomSheetParams {
        public boolean transitionFromLeft;
        public boolean allowNestedScroll;
        public Runnable onDismiss;
        public Runnable onOpenAnimationFinished;
        public Runnable onPreFinished;
        public boolean occupyNavigationBar;
    }

}
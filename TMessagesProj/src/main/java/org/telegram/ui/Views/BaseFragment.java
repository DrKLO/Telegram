/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ApplicationLoader;

public class BaseFragment extends Fragment {
    public int animationType = 0;
    public boolean isFinish = false;
    public View fragmentView;
    public ActionBarActivity parentActivity;
    public int classGuid = 0;
    public boolean firstStart = true;
    public boolean animationInProgress = false;
    private boolean removeParentOnDestroy = false;
    private boolean removeParentOnAnimationEnd = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parentActivity = (ActionBarActivity)getActivity();
    }

    public void willBeHidden() {

    }

    public void finishFragment() {
        finishFragment(false);
    }

    public void finishFragment(boolean bySwipe) {
        if (isFinish || animationInProgress) {
            return;
        }
        isFinish = true;
        if (parentActivity == null) {
            ApplicationLoader.fragmentsStack.remove(this);
            onFragmentDestroy();
            return;
        }
        ((LaunchActivity)parentActivity).finishFragment(bySwipe);
        if (getActivity() == null) {
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup)fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragmentView);
                }
                fragmentView = null;
            }
            parentActivity = null;
        } else {
            removeParentOnDestroy = true;
        }
    }

    public void removeSelfFromStack() {
        if (isFinish) {
            return;
        }
        isFinish = true;
        if (parentActivity == null) {
            ApplicationLoader.fragmentsStack.remove(this);
            onFragmentDestroy();
            return;
        }
        ((LaunchActivity)parentActivity).removeFromStack(this);
        if (getActivity() == null) {
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup)fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragmentView);
                }
                fragmentView = null;
            }
            parentActivity = null;
        } else {
            removeParentOnDestroy = true;
        }
    }

    public boolean onFragmentCreate() {
        classGuid = ConnectionsManager.Instance.generateClassGuid();
        return true;
    }

    public void onFragmentDestroy() {
        ConnectionsManager.Instance.cancelRpcsForClassGuid(classGuid);
        removeParentOnDestroy = true;
        isFinish = true;
    }

    public String getStringEntry(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }

    public void onAnimationStart() {
        animationInProgress = true;
    }

    public void onAnimationEnd() {
        animationInProgress = false;
    }

    public boolean onBackPressed() {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (removeParentOnDestroy) {
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup)fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragmentView);
                }
                fragmentView = null;
            }
            parentActivity = null;
        }
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim != 0) {
            Animation anim = AnimationUtils.loadAnimation(getActivity(), nextAnim);

            anim.setAnimationListener(new Animation.AnimationListener() {

                public void onAnimationStart(Animation animation) {
                    BaseFragment.this.onAnimationStart();
                }

                public void onAnimationRepeat(Animation animation) {

                }

                public void onAnimationEnd(Animation animation) {
                    BaseFragment.this.onAnimationEnd();
                }
            });

            return anim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    public boolean canApplyUpdateStatus() {
        return true;
    }

    public void applySelfActionBar() {

    }
}

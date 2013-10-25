/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationActivity;

public class BaseFragment extends SherlockFragment {
    public int animationType = 0;
    public boolean isFinish = false;
    public View fragmentView;
    public SherlockFragmentActivity parentActivity;
    public int classGuid = 0;
    public boolean firstStart = true;
    public boolean animationInProgress = false;
    private boolean removeParentOnDestroy = false;
    private boolean removeParentOnAnimationEnd = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parentActivity = getSherlockActivity();
    }

    public void willBeHidden() {

    }

    public void finishFragment() {
        finishFragment(false);
    }

    public void finishFragment(boolean bySwipe) {
        if (isFinish || animationInProgress || parentActivity == null) {
            return;
        }
        isFinish = true;
        ((ApplicationActivity)parentActivity).finishFragment(bySwipe);
        if (getSherlockActivity() == null) {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
            fragmentView = null;
            parentActivity = null;
        } else {
            removeParentOnDestroy = true;
        }
    }

    public void removeSelfFromStack() {
        if (isFinish || parentActivity == null) {
            return;
        }
        isFinish = true;
        ((ApplicationActivity)parentActivity).removeFromStack(this);
        if (getSherlockActivity() == null) {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
            fragmentView = null;
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
    }

    public String getStringEntry(int res) {
        return Utilities.applicationContext.getString(res);
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
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
            fragmentView = null;
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

/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.SlideView;

import java.util.Map;
import java.util.Set;

public class LoginActivity extends BaseFragment implements SlideView.SlideViewDelegate {
    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[3];

    private final static int done_button = 1;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (SlideView v : views) {
            if (v != null) {
                v.onDestroyActivity();
            }
        }
        Utilities.HideProgressDialog(getParentActivity());
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setDisplayUseLogoEnabled(true);
            actionBarLayer.setTitle(LocaleController.getString("AppName", R.string.AppName));

            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == done_button) {
                        onNextAction();
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            View doneItem = menu.addItemResource(done_button, R.layout.group_create_done_layout);
            TextView doneTextView = (TextView)doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Done", R.string.Done));

            fragmentView = inflater.inflate(R.layout.login_layout, container, false);

            views[0] = (SlideView)fragmentView.findViewById(R.id.login_page1);
            views[1] = (SlideView)fragmentView.findViewById(R.id.login_page2);
            views[2] = (SlideView)fragmentView.findViewById(R.id.login_page3);

            actionBarLayer.setTitle(views[0].getHeaderName());

            Bundle savedInstanceState = loadCurrentState();
            if (savedInstanceState != null) {
                currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
            }
            for (int a = 0; a < views.length; a++) {
                SlideView v = views[a];
                if (v != null) {
                    if (savedInstanceState != null) {
                        v.restoreStateParams(savedInstanceState);
                    }
                    v.delegate = this;
                    v.setVisibility(currentViewNum == a ? View.VISIBLE : View.GONE);
                }
            }
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void saveCurrentState() {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("currentViewNum", currentViewNum);
            for (int a = 0; a <= currentViewNum; a++) {
                SlideView v = views[a];
                if (v != null) {
                    v.saveStateParams(bundle);
                }
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private Bundle loadCurrentState() {
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle)obj, editor, key);
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (currentViewNum == 0) {
            for (SlideView v : views) {
                if (v != null) {
                    v.onDestroyActivity();
                }
            }
            return true;
        } else if (currentViewNum != 1 && currentViewNum != 2) {
            setPage(0, true, null, true);
        }
        return false;
    }

    @Override
    public void needShowAlert(final String text) {
        if (text == null) {
            return;
        }
        getParentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(text);
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                showAlertDialog(builder);
            }
        });
    }

    @Override
    public void needShowProgress() {
        Utilities.ShowProgressDialog(getParentActivity(), LocaleController.getString("Loading", R.string.Loading));
    }

    @Override
    public void needHideProgress() {
        Utilities.HideProgressDialog(getParentActivity());
    }

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        if(android.os.Build.VERSION.SDK_INT > 13) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;

            newView.setParams(params);
            actionBarLayer.setTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -Utilities.displaySize.x : Utilities.displaySize.x);
            outView.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }
            }).setDuration(300).translationX(back ? Utilities.displaySize.x : -Utilities.displaySize.x).start();
            newView.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    newView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }
            }).setDuration(300).translationX(0).start();
        } else {
            views[currentViewNum].setVisibility(View.GONE);
            currentViewNum = page;
            views[page].setParams(params);
            views[page].setVisibility(View.VISIBLE);
            actionBarLayer.setTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    @Override
    public void onNextAction() {
        views[currentViewNum].onNextPressed();
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        saveCurrentState();
    }

    @Override
    public void needFinishActivity() {
        clearCurrentState();
        presentFragment(new MessagesActivity(null), true);
    }
}

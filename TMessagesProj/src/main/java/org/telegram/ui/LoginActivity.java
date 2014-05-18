/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBarActivity;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideView;

import java.util.Map;
import java.util.Set;

public class LoginActivity extends ActionBarActivity implements SlideView.SlideViewDelegate {
    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[3];

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (currentViewNum == 0) {
            if (resultCode == RESULT_OK) {
                ((LoginActivityPhoneView)views[0]).selectCountry(data.getStringExtra("country"));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Utilities.checkForCrashes(this);
        Utilities.checkForUpdates(this);
        ApplicationLoader.resetLastPauseTime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
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
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
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
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
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
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo", MODE_PRIVATE);
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

    public void ShowAlertDialog(final Activity activity, final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(message);
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    builder.show().setCanceledOnTouchOutside(true);
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);
        ApplicationLoader.applicationContext = this.getApplicationContext();

        getSupportActionBar().setLogo(R.drawable.ab_icon_fixed2);
        getSupportActionBar().show();

        ImageView view = (ImageView)findViewById(16908332);
        if (view == null) {
            view = (ImageView)findViewById(R.id.home);
        }
        if (view != null) {
            view.setPadding(Utilities.dp(6), 0, Utilities.dp(6), 0);
        }

        views[0] = (SlideView)findViewById(R.id.login_page1);
        views[1] = (SlideView)findViewById(R.id.login_page2);
        views[2] = (SlideView)findViewById(R.id.login_page3);

        getSupportActionBar().setTitle(views[0].getHeaderName());

        savedInstanceState = loadCurrentState();
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

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SupportMenuItem doneItem = (SupportMenuItem)menu.add(Menu.NONE, 0, Menu.NONE, null);
        doneItem.setShowAsAction(SupportMenuItem.SHOW_AS_ACTION_ALWAYS);
        doneItem.setActionView(R.layout.group_create_done_layout);

        TextView doneTextView = (TextView)doneItem.getActionView().findViewById(R.id.done_button);
        doneTextView.setText(LocaleController.getString("Done", R.string.Done));
        doneTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onNextAction();
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (currentViewNum == 0) {
            for (SlideView v : views) {
                if (v != null) {
                    v.onDestroyActivity();
                }
            }
            super.onBackPressed();
        } else if (currentViewNum != 1 && currentViewNum != 2) {
            setPage(0, true, null, true);
        }
    }

    @Override
    public void needShowAlert(String text) {
        if (text == null) {
            return;
        }
        ShowAlertDialog(LoginActivity.this, text);
    }

    @Override
    public void needShowProgress() {
        Utilities.ShowProgressDialog(this, LocaleController.getString("Loading", R.string.Loading));
    }

    @Override
    public void needHideProgress() {
        Utilities.HideProgressDialog(this);
    }

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        if(android.os.Build.VERSION.SDK_INT > 13) {
            Point displaySize = new Point();
            Display display = getWindowManager().getDefaultDisplay();
            display.getSize(displaySize);

            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;

            newView.setParams(params);
            getSupportActionBar().setTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -displaySize.x : displaySize.x);
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
            }).setDuration(300).translationX(back ? displaySize.x : -displaySize.x).start();
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
            getSupportActionBar().setTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    @Override
    public void onNextAction() {
        views[currentViewNum].onNextPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (SlideView v : views) {
            if (v != null) {
                v.onDestroyActivity();
            }
        }
        Utilities.HideProgressDialog(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveCurrentState();
    }

    @Override
    public void needFinishActivity() {
        Intent intent2 = new Intent(this, LaunchActivity.class);
        startActivity(intent2);
        finish();
        clearCurrentState();
    }
}

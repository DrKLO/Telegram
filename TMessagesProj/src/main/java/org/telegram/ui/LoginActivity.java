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
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBarActivity;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideView;

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
//        if (views[currentViewNum] instanceof LoginActivityRegisterView) {
//            ((LoginActivityRegisterView)views[currentViewNum]).avatarUpdater.onActivityResult(requestCode, resultCode, data);
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApplicationLoader.resetLastPauseTime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ApplicationLoader.lastPauseTime = System.currentTimeMillis();
    }

    public void ShowAlertDialog(final Activity activity, final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(LoginActivity.this.getString(R.string.AppName));
                    builder.setMessage(message);
                    builder.setPositiveButton(ApplicationLoader.applicationContext.getString(R.string.OK), null);
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

        if (savedInstanceState != null) {
            currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
        }
        for (int a = 0; a < views.length; a++) {
            SlideView v = views[a];
            if (v != null) {
                v.delegate = this;
                v.setVisibility(currentViewNum == a ? View.VISIBLE : View.GONE);
            }
        }

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getWindow().setFormat(PixelFormat.RGB_565);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.group_create_menu, menu);
        SupportMenuItem doneItem = (SupportMenuItem)menu.findItem(R.id.done_menu_item);
        TextView doneTextView = (TextView)doneItem.getActionView().findViewById(R.id.done_button);
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
        Utilities.ShowProgressDialog(this, getResources().getString(R.string.Loading));
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
        outState.putInt("currentViewNum", currentViewNum);
    }

    @Override
    public void needFinishActivity() {
        Intent intent2 = new Intent(this, LaunchActivity.class);
        startActivity(intent2);
        finish();
    }
}

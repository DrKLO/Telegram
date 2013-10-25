/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.NonSwipeableViewPager;
import org.telegram.ui.Views.SlideFragment;

import java.util.HashMap;

public class LoginActivity extends SherlockFragmentActivity implements NonSwipeableViewPager.SlideFramentProceed {
    private NonSwipeableViewPager pager;
    protected FragmentStatePagerAdapter pagerAdapter;
    private TextView nextButton;
    private TextView backButton;
    private TextView headerTextView;
    private Animation in, out;
    private String animateToText;
    private HashMap<String, String> paramsToSet;
    private SlideFragment currentFragment;
    private int currentIndex;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (currentFragment instanceof ScreenSlidePageFragmentRegister) {
            ((ScreenSlidePageFragmentRegister)currentFragment).avatarUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void didProceed(HashMap<String, String> params) {
        paramsToSet = params;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApplicationLoader.lastPauseTime = 0;
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
                    builder.setPositiveButton(Utilities.applicationContext.getString(R.string.OK), null);
                    builder.show().setCanceledOnTouchOutside(true);
                }
            }
        });
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public SherlockFragment getItem(int position) {
            SlideFragment fragment = null;
            if (position == 0) {
                fragment = new ScreenSlidePageFragmentPhone();
            } else if (position == 1) {
                fragment = new ScreenSlidePageFragmentSms();
            } else if (position == 2) {
                fragment = new ScreenSlidePageFragmentRegister();
            }
            fragment.delegate = LoginActivity.this;
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        public void setPrimaryItem(android.view.ViewGroup container, int position, java.lang.Object object) {
            super.setPrimaryItem(container, position, object);
            currentIndex = position;
            if (object instanceof SlideFragment) {
                ((SlideFragment)object).delegate = LoginActivity.this;
            }
            if (currentFragment != object) {
                SlideFragment fragment = (SlideFragment)object;
                currentFragment = fragment;
                if (paramsToSet != null) {
                    fragment.setParams(paramsToSet);
                    paramsToSet = null;
                }
                if (headerTextView.getText() == null || headerTextView.getText().length() == 0) {
                    headerTextView.setText(fragment.getHeaderName());
                } else {
                    animateToText = fragment.getHeaderName();
                    headerTextView.startAnimation(out);
                }
                if (position != 0) {
                    backButton.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        in = new AlphaAnimation(0.0f, 1.0f);
        in.setDuration(200);
        out = new AlphaAnimation(1.0f, 0.0f);
        out.setDuration(200);
        out.setAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                headerTextView.setText(animateToText);
                headerTextView.startAnimation(in);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_layout);
        Utilities.applicationContext = this.getApplicationContext();
        ConnectionsManager inst = ConnectionsManager.Instance;

        Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
        headerTextView = (TextView) findViewById(R.id.login_header_text);
        headerTextView.setTypeface(typeface);

        pager = (NonSwipeableViewPager)findViewById(R.id.login_pager);
        pagerAdapter = new ScreenSlidePagerAdapter(this.getSupportFragmentManager());
        pager.setAdapter(pagerAdapter);

        nextButton = (TextView) findViewById(R.id.login_next_button);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                currentFragment.delegate = LoginActivity.this;
                currentFragment.onNextPressed();
            }
        });

        backButton = (TextView)findViewById(R.id.login_back_button);
        backButton.setVisibility(View.INVISIBLE);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        getSupportActionBar().hide();
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() == 0) {
            super.onBackPressed();
        } else {
            if (currentFragment != null) {
                currentFragment.onBackPressed();
            }
            if (pager.getCurrentItem() == 2) {
                pager.setCurrentItem(0);
            } else {
                pager.setCurrentItem(pager.getCurrentItem() - 1);
            }
            if (pager.getCurrentItem() == 0) {
                backButton.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (pager != null) {
            ViewTreeObserver obs = pager.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    /*pager.beginFakeDrag();
                    pager.fakeDragBy(1);
                    pager.endFakeDrag();*/
                    pager.setCurrentItem(currentIndex);
                    pager.getViewTreeObserver().removeOnPreDrawListener(this);
                    return false;
                }
            });
        }
    }

    @Override
    public void onNextAction() {
        nextButton.performClick();
    }

    @Override
    public void needShowAlert(String text) {
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

    @Override
    public void needSlidePager(int page, boolean arg0) {
        pager.setCurrentItem(page, arg0);
    }

    @Override
    public void needFinishActivity() {
        Intent intent2 = new Intent(this, LaunchActivity.class);
        startActivity(intent2);
        finish();
    }
}

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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Views.SlideView;

import java.util.Map;
import java.util.Set;

public class LoginActivity extends BaseFragment implements SlideView.SlideViewDelegate {
    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[3];
    private ProgressDialog progressDialog;

    private final static int done_button = 1;

    /*
    <ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#fafafa"
    android:fillViewport="true">

    <org.telegram.ui.Views.FrameLayoutFixed
        android:layout_width="fill_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top|left">

        <org.telegram.ui.LoginActivityPhoneView
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="wrap_content"
            android:visibility="visible"
            android:layout_marginLeft="16dp"
            android:layout_marginRight="16dp"
            android:layout_marginTop="30dp"
            android:layout_gravity="top|left"
            android:id="@+id/login_page1">

            <TextView
                android:layout_width="fill_parent"
                android:layout_height="50dp"
                android:layout_marginLeft="20dp"
                android:layout_marginRight="20dp"
                android:id="@+id/login_coutry_textview"
                android:textSize="20dp"
                android:paddingTop="10dp"
                android:gravity="left|center_horizontal"
                android:textIsSelectable="false"
                android:textColor="#000000"
                android:paddingLeft="12dp"
                android:paddingRight="12dp"
                android:maxLines="1"
                android:background="@drawable/spinner_states"/>

            <View
                android:layout_width="fill_parent"
                android:layout_height="1px"
                android:paddingLeft="12dp"
                android:paddingRight="12dp"
                android:background="#808080"
                android:layout_marginTop="-6.5dp"
                android:layout_marginLeft="24dp"
                android:layout_marginRight="24dp"/>

            <LinearLayout
                android:layout_width="fill_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:layout_gravity="top|left"
                android:orientation="horizontal">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="+"
                    android:textColor="#a6a6a6"
                    android:textSize="20dp"
                    android:layout_marginLeft="8dp"
                    android:layout_gravity="top|left"/>

                <EditText
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:id="@+id/login_county_code_field"
                    android:width="70dp"
                    android:editable="true"
                    android:inputType="phone"
                    android:maxLength="4"
                    android:maxLines="1"
                    android:textSize="18dp"
                    android:gravity="center"
                    android:imeOptions="actionNext|flagNoExtractUi"
                    android:textCursorDrawable="@null"
                    android:textColor="#000000"
                    android:layout_gravity="top|left"/>

                <EditText
                    android:layout_width="fill_parent"
                    android:layout_height="wrap_content"
                    android:id="@+id/login_phone_field"
                    android:inputType="phone"
                    android:maxLines="1"
                    android:textSize="18dp"
                    android:layout_marginRight="20dp"
                    android:imeOptions="actionNext|flagNoExtractUi"
                    android:textCursorDrawable="@null"
                    android:textColor="#000000"
                    android:textColorHint="#979797"
                    android:gravity="center_vertical"
                    android:layout_gravity="top|left"/>

            </LinearLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/login_confirm_text"
                android:layout_marginTop="28dp"
                android:textColor="#808080"
                android:layout_gravity="center_horizontal"
                android:textSize="16dp"
                android:gravity="center"
                android:layout_marginBottom="10dp"
                android:lineSpacingExtra="2dp"/>

        </org.telegram.ui.LoginActivityPhoneView>

        <org.telegram.ui.LoginActivitySmsView
            android:layout_width="fill_parent"
            android:layout_height="fill_parent"
            android:layout_marginLeft="16dp"
            android:layout_marginRight="16dp"
            android:layout_marginTop="30dp"
            android:visibility="gone"
            android:id="@+id/login_page2"
            android:layout_gravity="top|left"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/login_sms_confirm_text"
                android:textColor="#808080"
                android:textSize="16dp"
                android:gravity="center_horizontal"
                android:layout_gravity="center_horizontal"
                android:lineSpacingExtra="2dp"/>

            <EditText
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:inputType="numberDecimal"
                android:width="220dp"
                android:id="@+id/login_sms_code_field"
                android:layout_marginTop="20dp"
                android:gravity="center_horizontal"
                android:maxLines="1"
                android:editable="true"
                android:textSize="18dp"
                android:imeOptions="actionNext|flagNoExtractUi"
                android:textCursorDrawable="@null"
                android:layout_gravity="center_horizontal"
                android:textColor="#000000"
                android:textColorHint="#979797"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/login_time_text"
                android:layout_marginTop="20dp"
                android:gravity="center_horizontal"
                android:layout_gravity="center_horizontal"
                android:textSize="16dp"
                android:textColor="#808080"
                android:lineSpacingExtra="2dp"/>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/login_problem"
                android:gravity="center_horizontal"
                android:layout_gravity="center_horizontal"
                android:textSize="16dp"
                android:textColor="#316f9f"
                android:lineSpacingExtra="2dp"
                android:paddingTop="2dp"
                android:paddingBottom="12dp"/>

            <LinearLayout
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                android:layout_below="@+id/spinner"
                android:gravity="center_horizontal|bottom"
                android:layout_gravity="top|left">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:id="@+id/wrong_number"
                    android:gravity="center"
                    android:textSize="16dp"
                    android:textColor="#316f9f"
                    android:lineSpacingExtra="2dp"
                    android:layout_gravity="bottom|center_horizontal"
                    android:paddingTop="24dp"
                    android:layout_marginBottom="10dp"/>

            </LinearLayout>

        </org.telegram.ui.LoginActivitySmsView>

        <org.telegram.ui.LoginActivityRegisterView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_marginTop="30dp"
            android:paddingLeft="16dp"
            android:paddingRight="16dp"
            android:orientation="vertical"
            android:id="@+id/login_page3"
            android:visibility="gone"
            android:layout_gravity="top|left">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:id="@+id/login_register_info"
                android:layout_marginTop="8dp"
                android:textColor="#808080"
                android:gravity="center_horizontal"
                android:layout_gravity="center_horizontal"
                android:textSize="16dp"/>

            <EditText
                android:layout_width="fill_parent"
                android:layout_height="wrap_content"
                android:id="@+id/login_first_name_field"
                android:maxLines="1"
                android:textSize="18dp"
                android:imeOptions="actionNext|flagNoExtractUi"
                android:layout_marginLeft="40dp"
                android:layout_marginRight="40dp"
                android:layout_marginTop="34dp"
                android:lines="1"
                android:capitalize="words"
                android:textCursorDrawable="@null"
                android:textColor="#000000"
                android:textColorHint="#979797"/>

            <EditText
                android:layout_width="fill_parent"
                android:layout_height="wrap_content"
                android:id="@+id/login_last_name_field"
                android:textSize="18dp"
                android:imeOptions="actionNext|flagNoExtractUi"
                android:layout_marginLeft="40dp"
                android:layout_marginRight="40dp"
                android:lines="1"
                android:maxLines="1"
                android:capitalize="words"
                android:autoText="false"
                android:textCursorDrawable="@null"
                android:textColor="#000000"
                android:textColorHint="#979797"
                android:layout_marginTop="10dp"/>

            <LinearLayout
                android:layout_width="fill_parent"
                android:layout_height="fill_parent"
                android:layout_below="@+id/spinner"
                android:gravity="center_horizontal|bottom"
                android:minHeight="140dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="30dp"
                    android:id="@+id/changed_mind"
                    android:gravity="center"
                    android:textSize="16dp"
                    android:textColor="#316f9f"
                    android:lineSpacingExtra="2dp"
                    android:layout_gravity="bottom|center_horizontal"
                    android:layout_marginTop="-40dp"
                    android:layout_marginBottom="20dp"/>

            </LinearLayout>

        </org.telegram.ui.LoginActivityRegisterView>

    </org.telegram.ui.Views.FrameLayoutFixed>

</ScrollView>
     */

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (SlideView v : views) {
            if (v != null) {
                v.onDestroyActivity();
            }
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            progressDialog = null;
        }
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == done_button) {
                        onNextAction();
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            View doneItem = menu.addItemResource(done_button, R.layout.group_create_done_layout);
            TextView doneTextView = (TextView)doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.login_layout, container, false);

            views[0] = (SlideView)fragmentView.findViewById(R.id.login_page1);
            views[1] = (SlideView)fragmentView.findViewById(R.id.login_page2);
            views[2] = (SlideView)fragmentView.findViewById(R.id.login_page3);

            try {
                if (views[0] == null || views[1] == null || views[2] == null) {
                    FrameLayout parent = (FrameLayout)((ScrollView) fragmentView).getChildAt(0);
                    for (int a = 0; a < views.length; a++) {
                        if (views[a] == null) {
                            views[a] = (SlideView)parent.getChildAt(a);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            actionBar.setTitle(views[0].getHeaderName());

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

    @Override
    public void onPause() {
        super.onPause();
        getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    @Override
    public void onResume() {
        super.onResume();
        getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showAlertDialog(builder);
    }

    @Override
    public void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    public void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        progressDialog = null;
    }

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        if(android.os.Build.VERSION.SDK_INT > 13) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;

            newView.setParams(params);
            actionBar.setTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
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
            }).setDuration(300).translationX(back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x).start();
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
            actionBar.setTitle(views[page].getHeaderName());
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

/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IdenticonDrawable;

public class IdenticonActivity extends BaseFragment {
    private int chat_id;

    public IdenticonActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        chat_id = getArguments().getInt("chat_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("EncryptionKey", R.string.EncryptionKey));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = getParentActivity().getLayoutInflater().inflate(R.layout.identicon_layout, null, false);
        ImageView identiconView = (ImageView) fragmentView.findViewById(R.id.identicon_view);
        TextView textView = (TextView) fragmentView.findViewById(R.id.identicon_text);
        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(chat_id);
        if (encryptedChat != null) {
            IdenticonDrawable drawable = new IdenticonDrawable();
            identiconView.setImageDrawable(drawable);
            drawable.setEncryptedChat(encryptedChat);
            TLRPC.User user = MessagesController.getInstance().getUser(encryptedChat.user_id);
            textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EncryptionKeyDescription", R.string.EncryptionKeyDescription, user.first_name, user.first_name)));
        }

        fragmentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        return fragmentView;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    private void fixLayout() {
        ViewTreeObserver obs = fragmentView.getViewTreeObserver();
        obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView == null) {
                    return true;
                }
                fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                LinearLayout layout = (LinearLayout)fragmentView;
                WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
                int rotation = manager.getDefaultDisplay().getRotation();

                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                } else {
                    layout.setOrientation(LinearLayout.VERTICAL);
                }

                fragmentView.setPadding(fragmentView.getPaddingLeft(), 0, fragmentView.getPaddingRight(), fragmentView.getPaddingBottom());
                return true;
            }
        });
    }
}

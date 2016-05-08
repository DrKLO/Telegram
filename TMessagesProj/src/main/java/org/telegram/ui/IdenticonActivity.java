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
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.IdenticonDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.URLSpanReplacement;

public class IdenticonActivity extends BaseFragment {

    private int chat_id;

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            try {
                return super.onTouchEvent(widget, buffer, event);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            return false;
        }
    }

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

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setWeightSum(100);
        linearLayout.setBackgroundColor(0xfff0f0f0);
        fragmentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 50.0f));

        ImageView identiconView = new ImageView(context);
        identiconView.setScaleType(ImageView.ScaleType.FIT_XY);
        frameLayout.addView(identiconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xffffffff);
        frameLayout.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 50.0f));

        TextView textView = new TextView(context);
        textView.setTextColor(0xff7f7f7f);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLinksClickable(true);
        textView.setClickable(true);
        textView.setMovementMethod(new LinkMovementMethodMy());
        //textView.setAutoLinkMask(Linkify.WEB_URLS);
        textView.setLinkTextColor(Theme.MSG_LINK_TEXT_COLOR);
        textView.setGravity(Gravity.CENTER);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(chat_id);
        if (encryptedChat != null) {
            IdenticonDrawable drawable = new IdenticonDrawable();
            identiconView.setImageDrawable(drawable);
            drawable.setEncryptedChat(encryptedChat);
            TLRPC.User user = MessagesController.getInstance().getUser(encryptedChat.user_id);
            SpannableStringBuilder hash = new SpannableStringBuilder();
            if (encryptedChat.key_hash.length > 16) {
                String hex = Utilities.bytesToHex(encryptedChat.key_hash);
                for (int a = 0; a < 32; a++) {
                    if (a != 0) {
                        if (a % 8 == 0) {
                            hash.append('\n');
                        } else if (a % 4 == 0) {
                            hash.append(' ');
                        }
                    }
                    hash.append(hex.substring(a * 2, a * 2 + 2));
                    hash.append(' ');
                }
                hash.append("\n\n");
            }
            hash.append(AndroidUtilities.replaceTags(LocaleController.formatString("EncryptionKeyDescription", R.string.EncryptionKeyDescription, user.first_name, user.first_name)));
            final String url = "telegram.org";
            int index = hash.toString().indexOf(url);
            if (index != -1) {
                hash.setSpan(new URLSpanReplacement(LocaleController.getString("EncryptionKeyLink", R.string.EncryptionKeyLink)), index, index + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            textView.setText(hash);
        }

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
                LinearLayout layout = (LinearLayout) fragmentView;
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

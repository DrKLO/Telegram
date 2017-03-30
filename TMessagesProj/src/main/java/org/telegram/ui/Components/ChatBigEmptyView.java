/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ChatBigEmptyView extends LinearLayout {

    private TextView secretViewStatusTextView;
    private ArrayList<TextView> textViews = new ArrayList<>();
    private ArrayList<ImageView> imageViews = new ArrayList<>();

    public ChatBigEmptyView(Context context, boolean secretChat) {
        super(context);

        setBackgroundResource(R.drawable.system);
        getBackground().setColorFilter(Theme.colorFilter);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        setOrientation(LinearLayout.VERTICAL);

        if (secretChat) {
            secretViewStatusTextView = new TextView(context);
            secretViewStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            secretViewStatusTextView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
            secretViewStatusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            secretViewStatusTextView.setMaxWidth(AndroidUtilities.dp(210));
            textViews.add(secretViewStatusTextView);
            addView(secretViewStatusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        } else {
            ImageView imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.cloud_big);
            addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        TextView textView = new TextView(context);
        if (secretChat) {
            textView.setText(LocaleController.getString("EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        } else {
            textView.setText(LocaleController.getString("ChatYourSelfTitle", R.string.ChatYourSelfTitle));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        textView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        textViews.add(textView);
        textView.setMaxWidth(AndroidUtilities.dp(260));
        addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (secretChat ? (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) : Gravity.CENTER_HORIZONTAL) | Gravity.TOP, 0, 8, 0, secretChat ? 0 : 8));

        for (int a = 0; a < 4; a++) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

            ImageView imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
            imageView.setImageResource(secretChat ? R.drawable.ic_lock_white : R.drawable.list_circle);
            imageViews.add(imageView);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
            textViews.add(textView);
            textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            textView.setMaxWidth(AndroidUtilities.dp(260));

            switch (a) {
                case 0:
                    if (secretChat) {
                        textView.setText(LocaleController.getString("EncryptedDescription1", R.string.EncryptedDescription1));
                    } else {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription1", R.string.ChatYourSelfDescription1));
                    }
                    break;
                case 1:
                    if (secretChat) {
                        textView.setText(LocaleController.getString("EncryptedDescription2", R.string.EncryptedDescription2));
                    } else {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription2", R.string.ChatYourSelfDescription2));
                    }
                    break;
                case 2:
                    if (secretChat) {
                        textView.setText(LocaleController.getString("EncryptedDescription3", R.string.EncryptedDescription3));
                    } else {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription3", R.string.ChatYourSelfDescription3));
                    }
                    break;
                case 3:
                    if (secretChat) {
                        textView.setText(LocaleController.getString("EncryptedDescription4", R.string.EncryptedDescription4));
                    } else {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription4", R.string.ChatYourSelfDescription4));
                    }
                    break;
            }

            if (LocaleController.isRTL) {
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                if (secretChat) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 3, 0, 0));
                } else {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 7, 0, 0));
                }
            } else {
                if (secretChat) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 8, 0));
                } else {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 8, 0));
                }
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    public void setTextColor(int color) {
        for (int a = 0; a < textViews.size(); a++) {
            textViews.get(a).setTextColor(color);
        }
        for (int a = 0; a < imageViews.size(); a++) {
            imageViews.get(a).setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setSecretText(String text) {
        secretViewStatusTextView.setText(text);
    }
}

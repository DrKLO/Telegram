/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ChatBigEmptyView extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private TextView statusTextView;
    private ArrayList<TextView> textViews = new ArrayList<>();
    private ArrayList<ImageView> imageViews = new ArrayList<>();

    public final static int EMPTY_VIEW_TYPE_SECRET = 0;
    public final static int EMPTY_VIEW_TYPE_GROUP = 1;
    public final static int EMPTY_VIEW_TYPE_SAVED = 2;

    public ChatBigEmptyView(Context context, View parent, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(18), this, parent, getThemedPaint(Theme.key_paint_chatActionBackground)));
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        setOrientation(LinearLayout.VERTICAL);

        if (type == EMPTY_VIEW_TYPE_SECRET) {
            statusTextView = new TextView(context);
            statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            statusTextView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            statusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            statusTextView.setMaxWidth(AndroidUtilities.dp(210));
            textViews.add(statusTextView);
            addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        } else if (type == EMPTY_VIEW_TYPE_GROUP) {
            statusTextView = new TextView(context);
            statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            statusTextView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            statusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            statusTextView.setMaxWidth(AndroidUtilities.dp(210));
            textViews.add(statusTextView);
            addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        } else {
            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_saved_messages, 120, 120);
            imageView.playAnimation();
            addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        TextView textView = new TextView(context);
        if (type == EMPTY_VIEW_TYPE_SECRET) {
            textView.setText(LocaleController.getString("EncryptedDescriptionTitle", R.string.EncryptedDescriptionTitle));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        } else if (type == EMPTY_VIEW_TYPE_GROUP) {
            textView.setText(LocaleController.getString("GroupEmptyTitle2", R.string.GroupEmptyTitle2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        } else {
            textView.setText(LocaleController.getString("ChatYourSelfTitle", R.string.ChatYourSelfTitle));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        textView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        textViews.add(textView);
        textView.setMaxWidth(AndroidUtilities.dp(260));
        addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (type != EMPTY_VIEW_TYPE_SAVED ? (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) : Gravity.CENTER_HORIZONTAL) | Gravity.TOP, 0, 8, 0, type != EMPTY_VIEW_TYPE_SAVED ? 0 : 8));

        for (int a = 0; a < 4; a++) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

            ImageView imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
            if (type == EMPTY_VIEW_TYPE_SECRET) {
                imageView.setImageResource(R.drawable.ic_lock_white);
            } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                imageView.setImageResource(R.drawable.list_circle);
            } else {
                imageView.setImageResource(R.drawable.groups_overview_check);
            }
            imageViews.add(imageView);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
            textViews.add(textView);
            textView.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            textView.setMaxWidth(AndroidUtilities.dp(260));

            switch (a) {
                case 0:
                    if (type == EMPTY_VIEW_TYPE_SECRET) {
                        textView.setText(LocaleController.getString("EncryptedDescription1", R.string.EncryptedDescription1));
                    } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription1", R.string.ChatYourSelfDescription1));
                    } else {
                        textView.setText(LocaleController.getString("GroupDescription1", R.string.GroupDescription1));
                    }
                    break;
                case 1:
                    if (type == EMPTY_VIEW_TYPE_SECRET) {
                        textView.setText(LocaleController.getString("EncryptedDescription2", R.string.EncryptedDescription2));
                    } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription2", R.string.ChatYourSelfDescription2));
                    } else {
                        textView.setText(LocaleController.getString("GroupDescription2", R.string.GroupDescription2));
                    }
                    break;
                case 2:
                    if (type == EMPTY_VIEW_TYPE_SECRET) {
                        textView.setText(LocaleController.getString("EncryptedDescription3", R.string.EncryptedDescription3));
                    } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription3", R.string.ChatYourSelfDescription3));
                    } else {
                        textView.setText(LocaleController.getString("GroupDescription3", R.string.GroupDescription3));
                    }
                    break;
                case 3:
                    if (type == EMPTY_VIEW_TYPE_SECRET) {
                        textView.setText(LocaleController.getString("EncryptedDescription4", R.string.EncryptedDescription4));
                    } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                        textView.setText(LocaleController.getString("ChatYourSelfDescription4", R.string.ChatYourSelfDescription4));
                    } else {
                        textView.setText(LocaleController.getString("GroupDescription4", R.string.GroupDescription4));
                    }
                    break;
            }

            if (LocaleController.isRTL) {
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                if (type == EMPTY_VIEW_TYPE_SECRET) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 3, 0, 0));
                } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 7, 0, 0));
                } else {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 3, 0, 0));
                }
            } else {
                if (type == EMPTY_VIEW_TYPE_SECRET) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 8, 0));
                } else if (type == EMPTY_VIEW_TYPE_SAVED) {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 8, 0));
                } else {
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 8, 0));
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
            imageViews.get(a).setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_serviceText), PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setStatusText(CharSequence text) {
        statusTextView.setText(text);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }
}

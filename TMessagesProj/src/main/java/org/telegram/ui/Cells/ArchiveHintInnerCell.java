package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressWarnings("FieldCanBeLocal")
public class ArchiveHintInnerCell extends FrameLayout {

    private ImageView imageView;
    private ImageView imageView2;
    private TextView headerTextView;
    private TextView messageTextView;

    public ArchiveHintInnerCell(Context context, int num) {
        super(context);

        imageView = new ImageView(context);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_nameMessage_threeLines), PorterDuff.Mode.MULTIPLY));

        headerTextView = new TextView(context);
        headerTextView.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        headerTextView.setTypeface(AndroidUtilities.bold());
        headerTextView.setGravity(Gravity.CENTER);
        addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 75, 52, 0));

        messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_chats_message));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageTextView.setGravity(Gravity.CENTER);
        addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 110, 52, 0));

        switch (num) {
            case 0: {
                addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 20, 8, 0));

                imageView2 = new ImageView(context);
                imageView2.setImageResource(R.drawable.chats_archive_arrow);
                imageView2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_unreadCounter), PorterDuff.Mode.MULTIPLY));
                addView(imageView2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 20, 8, 0));

                headerTextView.setText(LocaleController.getString(R.string.ArchiveHintHeader1));
                messageTextView.setText(LocaleController.getString(R.string.ArchiveHintText1));
                imageView.setImageResource(R.drawable.chats_archive_box);
                break;
            }
            case 1:
                addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));

                headerTextView.setText(LocaleController.getString(R.string.ArchiveHintHeader2));
                messageTextView.setText(LocaleController.getString(R.string.ArchiveHintText2));
                imageView.setImageResource(R.drawable.chats_archive_muted);
                break;
            case 2:
                addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));

                headerTextView.setText(LocaleController.getString(R.string.ArchiveHintHeader3));
                messageTextView.setText(LocaleController.getString(R.string.ArchiveHintText3));
                imageView.setImageResource(R.drawable.chats_archive_pin);
                break;
        }
    }
}

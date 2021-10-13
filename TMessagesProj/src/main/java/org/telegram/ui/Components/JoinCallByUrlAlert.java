/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public class JoinCallByUrlAlert extends BottomSheet {

    private boolean joinAfterDismiss;

    public static class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView textView;
        private LinearLayout linearLayout;

        public BottomSheetCell(Context context) {
            super(context);

            background = new View(context);
            background.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }
    }

    public JoinCallByUrlAlert(final Context context, TLRPC.Chat chat) {
        super(context, true);
        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        setCustomView(linearLayout);

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(45));
        linearLayout.addView(avatarImageView, LayoutHelper.createLinear(90, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 29, 0, 0));

        AvatarDrawable avatarDrawable = new AvatarDrawable(chat);
        avatarImageView.setForUserOrChat(chat, avatarDrawable);

        TextView percentTextView = new TextView(context);
        percentTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        percentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        percentTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        percentTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(percentTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 17, 24, 17, 0));

        TextView infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        infoTextView.setGravity(Gravity.CENTER_HORIZONTAL);

        linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 30, 8, 30, 0));
        ChatObject.Call call = AccountInstance.getInstance(currentAccount).getMessagesController().getGroupCall(chat.id, false);
        if (call != null) {
            if (TextUtils.isEmpty(call.call.title)) {
                percentTextView.setText(chat.title);
            } else {
                percentTextView.setText(call.call.title);
            }
            if (call.call.participants_count == 0) {
                infoTextView.setText(LocaleController.getString("NoOneJoinedYet", R.string.NoOneJoinedYet));
            } else {
                infoTextView.setText(LocaleController.formatPluralString("Participants", call.call.participants_count));
            }
        } else {
            percentTextView.setText(chat.title);
            infoTextView.setText(LocaleController.getString("NoOneJoinedYet", R.string.NoOneJoinedYet));
        }

        BottomSheetCell clearButton = new BottomSheetCell(context);
        clearButton.setBackground(null);
        if (ChatObject.isChannelOrGiga(chat)) {
            clearButton.setText(LocaleController.getString("VoipChannelJoinVoiceChatUrl", R.string.VoipChannelJoinVoiceChatUrl));
        } else {
            clearButton.setText(LocaleController.getString("VoipGroupJoinVoiceChatUrl", R.string.VoipGroupJoinVoiceChatUrl));
        }
        clearButton.background.setOnClickListener(v -> {
            joinAfterDismiss = true;
            dismiss();
        });
        linearLayout.addView(clearButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 30, 0, 0));
    }

    protected void onJoin() {

    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (joinAfterDismiss) {
            onJoin();
        }
    }
}

package org.telegram.messenger.forkgram

import android.content.Context
import android.content.DialogInterface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.*
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper

object ForkDialogs {

@JvmStatic
public fun CreateVoiceCaptionAlert(
        context: Context,
        timestamps: ArrayList<String>,
        finish: (String) -> Unit) {
    val captionString = LocaleController.getString("Caption", R.string.Caption);

    val builder = AlertDialog.Builder(context);
    builder.setTitle(captionString);

    val textLayout = LinearLayout(context)
    textLayout.orientation = LinearLayout.HORIZONTAL

    val editText  = EditTextBoldCursor(context);
    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
    editText.background = Theme.createEditTextDrawable(context, true);
    editText.isSingleLine = false;
    editText.isFocusable = true;
    editText.imeOptions = EditorInfo.IME_ACTION_DONE;
    editText.requestFocus();

    editText.setText(timestamps.foldIndexed("") { index, total, item ->
        total + "${index + 1}. $item \n";
    });

    val padding = AndroidUtilities.dp(0f);
    editText.setPadding(padding, 0, padding, 0);

    textLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36))
    builder.setView(textLayout);
    builder.setPositiveButton(LocaleController.getString("Send", R.string.Send)) { _: DialogInterface?, _: Int ->
        finish(editText.text.toString());
    }
    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
    builder.show().setOnShowListener { dialog: DialogInterface? ->
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    val layoutParams = editText.layoutParams as MarginLayoutParams;
    if (layoutParams is FrameLayout.LayoutParams) {
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
    }
    layoutParams.leftMargin = AndroidUtilities.dp(24f);
    layoutParams.rightMargin = layoutParams.leftMargin;
    layoutParams.height = AndroidUtilities.dp(36f * 3);
    editText.layoutParams = layoutParams;
}

@JvmStatic
public fun CreateDeleteAllYourMessagesAlert(
        currentAccount: Int,
        dialogId: Long,
        context: Context) {

    val create = { text: String, callback: () -> Unit ->
        val builder = AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(
            "DeleteAllYourMessages",
            R.string.DeleteAllYourMessages));
        builder.setMessage(AndroidUtilities.replaceTags(text));

        builder.setPositiveButton(
            LocaleController.getString("OK", R.string.OK),
            { _: DialogInterface?, _: Int -> callback(); });
        builder.setNegativeButton(
            LocaleController.getString("Cancel", R.string.Cancel),
            null);
        val dialog = builder.show();
        val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE) as TextView;
        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
    };

    val messagesController = AccountInstance.getInstance(currentAccount).messagesController;
    val meId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;

    val deleteFor = { to: Long, found: ArrayList<TLRPC.Message> ->
        val messages: java.util.ArrayList<Int> = ArrayList(found.map { it.id });
        AndroidUtilities.runOnUIThread {
            messagesController.deleteMessages(
                messages,
                null,
                null,
                to,
                0,
                true,
                0);
        };
    };

    create(
        LocaleController.getString(
            "DeleteAllYourMessagesInfo",
            R.string.DeleteAllYourMessagesInfo)
    ) {
        create(LocaleController.getString("ReallySure", R.string.ReallySure)) {
            val dialogPeer = messagesController.getInputPeer(dialogId);
            val mePeer = messagesController.getInputPeer(meId);
            ForkApi.SearchAllMessages(
                currentAccount,
                dialogPeer,
                mePeer,
                { found: ArrayList<TLRPC.Message> -> deleteFor(dialogId, found); },
                {
                    // Check migrated.
                    if (dialogPeer.channel_id != 0L) {
                        ForkApi.FullChannel(
                            currentAccount,
                            dialogPeer.channel_id
                        ) { full: TLRPC.TL_messages_chatFull ->
                            val migratedFrom = full.full_chat.migrated_from_chat_id;
                            if (migratedFrom == 0L) {
                                return@FullChannel;
                            }
                            ForkApi.SearchAllMessages(
                                currentAccount,
                                messagesController.getInputPeer(-migratedFrom),
                                mePeer,
                                { found: ArrayList<TLRPC.Message> -> deleteFor(-migratedFrom, found); },
                                {}
                            )
                        };
                    }
                }
            );

        }
    }
}

@JvmStatic
public fun CreateFieldAlert(
        context: Context,
        title: String,
        defaultValue: String,
        finish: (String) -> Unit) {
    val builder = AlertDialog.Builder(context);
    builder.setTitle(title);

    val textLayout = LinearLayout(context)
    textLayout.orientation = LinearLayout.HORIZONTAL

    val editText = EditTextBoldCursor(context);
    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
    editText.background = Theme.createEditTextDrawable(context, true);
    editText.isSingleLine = true;
    editText.isFocusable = true;
    editText.imeOptions = EditorInfo.IME_ACTION_DONE;
    editText.setText(defaultValue);
    editText.requestFocus();

    val padding = AndroidUtilities.dp(0f);
    editText.setPadding(padding, 0, padding, 0);

    textLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36))
    builder.setView(textLayout);
    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK)) { _: DialogInterface?, _: Int ->
        finish(editText.text.toString().trim());
    }
    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
    builder.show().setOnShowListener { dialog: DialogInterface? ->
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    val layoutParams = editText.layoutParams as MarginLayoutParams;
    if (layoutParams is FrameLayout.LayoutParams) {
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
    }
    layoutParams.leftMargin = AndroidUtilities.dp(24f);
    layoutParams.rightMargin = layoutParams.leftMargin;
    editText.layoutParams = layoutParams;
}

}

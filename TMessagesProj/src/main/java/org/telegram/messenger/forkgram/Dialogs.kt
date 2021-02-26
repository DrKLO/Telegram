package org.telegram.messenger.forkgram

import android.content.Context
import android.content.DialogInterface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
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

}

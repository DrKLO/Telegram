package ua.itaysonlab.catogram;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;

import org.telegram.ui.Components.EditTextBoldCursor;

public class EditTextAutoFill extends EditTextBoldCursor {
    public EditTextAutoFill(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT >= 26) {
            setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public int getAutofillType() {
        return AUTOFILL_TYPE_TEXT;
    }
}

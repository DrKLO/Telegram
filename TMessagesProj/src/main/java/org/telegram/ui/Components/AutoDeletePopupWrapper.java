package org.telegram.ui.Components;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

public class AutoDeletePopupWrapper {

    public static final int TYPE_GROUP_CREATE = 1;

    private int type;
    View backItem;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
    private final ActionBarMenuSubItem disableItem;
    Callback callback;
    long lastDismissTime;
    public TextView textView;

    public AutoDeletePopupWrapper(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback, boolean createBackground, int type, Theme.ResourcesProvider resourcesProvider) {
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, createBackground ? R.drawable.popup_fixed_alert : 0, resourcesProvider);
        windowLayout.setFitItems(true);
        this.callback = callback;

        if (swipeBackLayout != null) {
            backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> {
                swipeBackLayout.closeForeground();
            });
        }

        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1d, LocaleController.getString(R.string.AutoDelete1Day), false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            callback.setAutoDeleteHistory(24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
        });
        item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1w, LocaleController.getString(R.string.AutoDelete7Days), false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            callback.setAutoDeleteHistory(7 * 24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
        });
        item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_autodelete_1m, LocaleController.getString(R.string.AutoDelete1Month), false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            callback.setAutoDeleteHistory(31 * 24 * 60 * 60, UndoView.ACTION_AUTO_DELETE_ON);
        });
        String customTitle = LocaleController.getString(R.string.AutoDeleteCustom);
        if (type == TYPE_GROUP_CREATE) {
            customTitle =  LocaleController.getString(R.string.AutoDeleteCustom2);
        }
        item = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_customize, customTitle, false, resourcesProvider);
        item.setOnClickListener(view -> {
            dismiss();
            AlertsCreator.createAutoDeleteDatePickerDialog(context, type, resourcesProvider, (notify, timeInMinutes) -> {
                callback.setAutoDeleteHistory(timeInMinutes * 60, timeInMinutes == 0 ? UndoView.ACTION_AUTO_DELETE_OFF : UndoView.ACTION_AUTO_DELETE_ON);
            });
        });
        disableItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_disable, LocaleController.getString(R.string.AutoDeleteDisable), false, resourcesProvider);
        disableItem.setOnClickListener(view -> {
            dismiss();
            callback.setAutoDeleteHistory(0, UndoView.ACTION_AUTO_DELETE_OFF);
        });
        if (type != TYPE_GROUP_CREATE) {
            disableItem.setColors(Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedBold));
        }

        if (type != TYPE_GROUP_CREATE) {
            FrameLayout gap = new FrameLayout(context);
            gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
            View gapShadow = new View(context);
            gapShadow.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourcesProvider));
            gap.addView(gapShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            gap.setTag(R.id.fit_width_tag, 1);
            windowLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

            textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setTag(R.id.fit_width_tag, 1);
            textView.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(8));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            textView.setText(LocaleController.getString(R.string.AutoDeletePopupDescription));
            windowLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 0));
        }
    }

    private void dismiss() {
        callback.dismiss();
        lastDismissTime = System.currentTimeMillis();
    }

    public void updateItems(int ttl) {
        if (System.currentTimeMillis() - lastDismissTime < 200) {
            AndroidUtilities.runOnUIThread(() -> {
                updateItems(ttl);
            });
            return;
        }
        if (ttl == 0) {
            disableItem.setVisibility(View.GONE);
        } else {
            disableItem.setVisibility(View.VISIBLE);
        }
    }

    public void allowExtendedHint(int linkColor) {
        if (textView == null) {
            return;
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append(LocaleController.getString(R.string.AutoDeletePopupDescription));
        spannableStringBuilder.append("\n\n");
        spannableStringBuilder.append(AndroidUtilities.replaceSingleLink(LocaleController.getString(R.string.AutoDeletePopupDescription2), linkColor, () -> {
            callback.showGlobalAutoDeleteScreen();
        }));
        textView.setText(spannableStringBuilder);
    }

    void setType(int i) {
        type = i;
    }

    public interface Callback {
        void dismiss();
        void setAutoDeleteHistory(int time, int action);
        default void showGlobalAutoDeleteScreen() {

        }
    }
}

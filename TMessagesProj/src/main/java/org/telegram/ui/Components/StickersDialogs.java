package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.Stories.recorder.EmojiBottomSheet;

import java.util.ArrayList;

public class StickersDialogs {
    private static int getThemedColor(int key, Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(key, resourcesProvider);
    }

    public static void showNameEditorDialog(TLRPC.StickerSet set, Theme.ResourcesProvider resourcesProvider, Context context, Utilities.Callback<CharSequence> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        boolean editMode = set != null;
        builder.setTitle(LocaleController.getString(editMode ? R.string.EditStickerPack : R.string.NewStickerPack));
        builder.setMessage(LocaleController.getString(R.string.StickersChooseNameForStickerPack));
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setPadding(dp(24), 0, dp(20), 0);
        final EditTextBoldCursor editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        InputFilter[] inputFilters = new InputFilter[2];
        final int maxLength = 50;
        inputFilters[0] = new InputFilter.LengthFilter(maxLength);
        inputFilters[1] = (source, start, end, dest, dstart, dend) -> {
            if (source.length() > 0 && Character.isWhitespace(source.charAt(0)) && (TextUtils.isEmpty(editText.getText()) || dstart == 0)) {
                return "";
            }
            return source;
        };
        editText.setFilters(inputFilters);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), getThemedColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackground(null);
        editText.requestFocus();
        editText.setPadding(dp(LocaleController.isRTL ? 28 : 0), 0, dp(LocaleController.isRTL ? 0 : 28), 0);
        frameLayout.addView(editText);

        NumberTextView checkTextView = new NumberTextView(context);
        checkTextView.setCenterAlign(true);
        checkTextView.setTextSize(15);
        checkTextView.setNumber(maxLength, false);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        checkTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frameLayout.addView(checkTextView, LayoutHelper.createFrame(26, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 0, 2, 4, 0));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                checkTextView.setNumber(maxLength - Character.codePointCount(s, 0, s.length()), true);
            }
        });
        if (editMode) {
            editText.setText(set.title);
            editText.setSelection(set.title.length());
        }
        builder.setView(frameLayout);
        builder.setCustomViewOffset(4);
        builder.setPositiveButton(LocaleController.getString(editMode ? R.string.Done : R.string.Create), (dialog, i) -> {
            CharSequence text = editText.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                AndroidUtilities.hideKeyboard(editText);
                dialog.dismiss();
                callback.run(text);
                if (editMode) {
                    TLRPC.TL_stickers_renameStickerSet req = new TLRPC.TL_stickers_renameStickerSet();
                    req.stickerset = MediaDataController.getInputStickerSet(set);
                    req.title = text.toString();
                    ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_messages_stickerSet) {
                            MediaDataController.getInstance(UserConfig.selectedAccount).toggleStickerSet(null, response, 2, null, false, false);
                        }
                    }));
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
            AndroidUtilities.hideKeyboard(editText);
            dialog.dismiss();
        });
        AlertDialog alertDialog = builder.show();
        alertDialog.setDismissDialogByButtons(false);
        editText.setOnEditorActionListener((view1, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                return true;
            }
            return false;
        });
    }

    public static void showDeleteForEveryOneDialog(TLRPC.StickerSet set, Theme.ResourcesProvider resourcesProvider, Context context, Runnable callback) {
        if (set == null) return;
        AlertDialog alertDialog = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(LocaleController.getString(R.string.StickersDeleteStickerSetTitle))
                .setMessage(LocaleController.getString(R.string.StickersDeleteStickerSetDescription))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (di, w) -> {
                    callback.run();
                    TLRPC.TL_stickers_deleteStickerSet req = new TLRPC.TL_stickers_deleteStickerSet();
                    req.stickerset = MediaDataController.getInputStickerSet(set);
                    ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {

                    }));
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private static void openStickerPickerDialog(TLRPC.TL_messages_stickerSet stickerSet, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        EmojiBottomSheet sheet = new EmojiBottomSheet(fragment.getContext(), true, resourcesProvider);
        sheet.whenDocumentSelected((parentObject, document, a) -> {
            String emoji = MessageObject.findAnimatedEmojiEmoticon(document, "\uD83D\uDE00", UserConfig.selectedAccount);
            if (TextUtils.isEmpty(emoji)) {
                emoji = "\uD83D\uDE00";
            }
            TLRPC.TL_stickers_addStickerToSet req = new TLRPC.TL_stickers_addStickerToSet();
            req.stickerset = MediaDataController.getInputStickerSet(stickerSet.set);
            req.sticker = MediaDataController.getInputStickerSetItem(document, emoji);
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    MediaDataController.getInstance(UserConfig.selectedAccount).toggleStickerSet(null, response, 1, null, false, false);
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationNameOnUIThread(NotificationCenter.customStickerCreated, false, response, document), 250);
                } else if (error != null) {
                    BulletinFactory.showError(error);
                }
            }));
        });
        if (fragment.visibleDialog != null) {
            sheet.show();
        } else {
            fragment.showDialog(sheet);
        }
    }

    public static void showAddStickerDialog(TLRPC.TL_messages_stickerSet stickerSet, View view, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        Context context = fragment.getContext();
        if (!(fragment instanceof ChatActivity)) {
            openStickerPickerDialog(stickerSet, fragment, resourcesProvider);
            return;
        }
        ActionBarPopupWindow.ActionBarPopupWindowLayout previewMenu = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert3, resourcesProvider, 0);
        ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> actions = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();

        items.add(LocaleController.getString(R.string.StickersCreateNewSticker));
        icons.add(R.drawable.menu_sticker_add);
        actions.add(0);

        items.add(LocaleController.getString(R.string.StickersAddAnExistingSticker));
        icons.add(R.drawable.menu_sticker_select);
        actions.add(1);

        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(previewMenu, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        View.OnClickListener onItemClickListener = v -> {
            int which = (int) v.getTag();
            popupWindow.dismiss();
            if (actions.get(which) == 1) {
                openStickerPickerDialog(stickerSet, fragment, resourcesProvider);
            } else {
                ((ChatActivity) fragment).openAttachMenuForCreatingSticker();
                ContentPreviewViewer.getInstance().setStickerSetForCustomSticker(stickerSet);
            }
        };

        for (int i = 0; i < items.size(); i++) {
            View item = ActionBarMenuItem.addItem(previewMenu, icons.get(i), items.get(i), false, resourcesProvider);
            item.setTag(i);
            item.setOnClickListener(onItemClickListener);
        }

        popupWindow.setDismissAnimationDuration(100);
        popupWindow.setScaleOut(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        previewMenu.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        int[] pos = new int[2];
        view.getLocationInWindow(pos);
        popupWindow.showAtLocation(view, 0, pos[0] + (view.getMeasuredWidth() / 2) - (previewMenu.getMeasuredWidth() / 2), pos[1] + (view.getMeasuredHeight() / 2) - (previewMenu.getMeasuredHeight() / 2));
        popupWindow.dimBehind();
    }
}

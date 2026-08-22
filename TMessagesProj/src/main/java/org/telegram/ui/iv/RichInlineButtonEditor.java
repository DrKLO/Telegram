package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;

/** Dialog and picker flow shared by inline and block buttons in both rich-editor surfaces. */
public final class RichInlineButtonEditor {

    private RichInlineButtonEditor() {}

    public static ItemOptions show(ItemOptions options, BaseFragment fragment, Context context,
                                   Theme.ResourcesProvider resourcesProvider,
                                   RichEditorListView.InlineButtonEdit edit) {
        return show(options, fragment, context, resourcesProvider, edit, false);
    }

    public static ItemOptions show(ItemOptions options, BaseFragment fragment, Context context,
                                   Theme.ResourcesProvider resourcesProvider,
                                   RichEditorListView.InlineButtonEdit edit, boolean inAttachDialog) {
        final TL_keyboard.InlineButtonType existing = edit.getType();
        if (existing != null) {
            editExisting(fragment, edit, existing, inAttachDialog);
            return null;
        }
        options
            .add(R.drawable.media_link_24, getString(R.string.ChatLink),
                () -> showInlineLinkDialog(edit, inAttachDialog))
            .add(R.drawable.msg_copy, getString(R.string.Copy),
                () -> showInlineCopyDialog(edit, inAttachDialog))
            .add(R.drawable.left_status_profile, getString(R.string.RichEditorUserProfile),
                () -> showInlineUserPicker(fragment, edit, inAttachDialog));
        return options.show();
    }

    public static ItemOptions showBlock(ItemOptions options, BaseFragment fragment, Context context,
                                        Theme.ResourcesProvider resourcesProvider,
                                        RichEditorListView.BlockButtonEdit edit) {
        return showBlock(options, fragment, context, resourcesProvider, edit, false);
    }

    public static ItemOptions showBlock(ItemOptions options, BaseFragment fragment, Context context,
                                        Theme.ResourcesProvider resourcesProvider,
                                        RichEditorListView.BlockButtonEdit edit, boolean inAttachDialog) {
        final TL_keyboard.InlineButtonType existing = edit.getType();
        if (existing != null) {
            editExistingBlock(fragment, context, resourcesProvider, edit, existing, inAttachDialog);
            return null;
        }
        options
            .add(R.drawable.media_link_24, getString(R.string.ChatLink),
                () -> showBlockLinkDialog(context, resourcesProvider, edit, inAttachDialog))
            .add(R.drawable.msg_copy, getString(R.string.Copy),
                () -> showBlockCopyDialog(context, resourcesProvider, edit, inAttachDialog))
            .add(R.drawable.left_status_profile, getString(R.string.RichEditorUserProfile),
                () -> showBlockProfileDialog(fragment, context, resourcesProvider, edit, inAttachDialog));
        return options.show();
    }

    private static void editExisting(BaseFragment fragment,
                                     RichEditorListView.InlineButtonEdit edit,
                                     TL_keyboard.InlineButtonType type, boolean inAttachDialog) {
        if (type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
            showInlineLinkDialog(edit, inAttachDialog);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeCopy) {
            showInlineCopyDialog(edit, inAttachDialog);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile) {
            showInlineUserPicker(fragment, edit, inAttachDialog);
        }
    }

    private static void showInlineLinkDialog(RichEditorListView.InlineButtonEdit edit, boolean inAttachDialog) {
        final TL_keyboard.InlineButtonType current = edit.getType();
        final boolean editing = current instanceof TL_keyboard.TL_inlineButtonTypeUrl;
        final String initial = editing ? ((TL_keyboard.TL_inlineButtonTypeUrl) current).url : "http://";
        edit.hideSelectionUi();
        edit.showInputDialog(
            getString(editing ? R.string.RichEditorEditLinkButton : R.string.RichEditorCreateLinkButton),
            getString(R.string.RichEditorButtonURL), initial, true, !inAttachDialog, value -> {
                if (TextUtils.isEmpty(value)) return;
                final TL_keyboard.TL_inlineButtonTypeUrl type = new TL_keyboard.TL_inlineButtonTypeUrl();
                type.url = value;
                edit.apply(type);
            });
    }

    private static void showInlineCopyDialog(RichEditorListView.InlineButtonEdit edit, boolean inAttachDialog) {
        final TL_keyboard.InlineButtonType current = edit.getType();
        final boolean editing = current instanceof TL_keyboard.TL_inlineButtonTypeCopy;
        final String initial = editing
            ? ((TL_keyboard.TL_inlineButtonTypeCopy) current).copy_text : edit.getLabel();
        edit.hideSelectionUi();
        edit.showInputDialog(
            getString(editing ? R.string.RichEditorEditCopyButton : R.string.RichEditorCreateCopyButton),
            getString(R.string.RichEditorButtonCopyText), initial, false, !inAttachDialog, value -> {
                if (TextUtils.isEmpty(value)) return;
                final TL_keyboard.TL_inlineButtonTypeCopy type = new TL_keyboard.TL_inlineButtonTypeCopy();
                type.copy_text = value;
                edit.apply(type);
            });
    }

    private static void showInlineUserPicker(BaseFragment fragment,
                                             RichEditorListView.InlineButtonEdit edit, boolean inAttachDialog) {
        edit.dismissSelectionUi();
        showUserPicker(fragment, inAttachDialog, userId -> {
            final TL_keyboard.TL_inlineButtonTypeUserProfile type = new TL_keyboard.TL_inlineButtonTypeUserProfile();
            type.user_id = userId;
            edit.apply(type);
        });
    }

    private static void editExistingBlock(BaseFragment fragment, Context context,
                                          Theme.ResourcesProvider resourcesProvider,
                                          RichEditorListView.BlockButtonEdit edit,
                                          TL_keyboard.InlineButtonType type, boolean inAttachDialog) {
        if (type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
            showBlockLinkDialog(context, resourcesProvider, edit, inAttachDialog);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeCopy) {
            showBlockCopyDialog(context, resourcesProvider, edit, inAttachDialog);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile) {
            showBlockProfileDialog(fragment, context, resourcesProvider, edit, inAttachDialog);
        }
    }

    private static void showBlockLinkDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                            RichEditorListView.BlockButtonEdit edit, boolean inAttachDialog) {
        final boolean editing = edit.exists();
        final TL_keyboard.InlineButtonType current = edit.getType();
        final String url = current instanceof TL_keyboard.TL_inlineButtonTypeUrl
            ? ((TL_keyboard.TL_inlineButtonTypeUrl) current).url : "http://";
        showBlockTextAndValueDialog(
            context, resourcesProvider, edit, inAttachDialog,
            getString(editing ? R.string.RichEditorEditLinkButton : R.string.RichEditorCreateLinkButton),
            getString(R.string.RichEditorButtonURL), url,
            (label, value) -> {
                final TL_keyboard.TL_inlineButtonTypeUrl type = new TL_keyboard.TL_inlineButtonTypeUrl();
                type.url = value;
                edit.apply(label, type);
            });
    }

    private static void showBlockCopyDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                            RichEditorListView.BlockButtonEdit edit, boolean inAttachDialog) {
        final boolean editing = edit.exists();
        final TL_keyboard.InlineButtonType current = edit.getType();
        final String copyText = current instanceof TL_keyboard.TL_inlineButtonTypeCopy
            ? ((TL_keyboard.TL_inlineButtonTypeCopy) current).copy_text : "";
        showBlockTextAndValueDialog(
            context, resourcesProvider, edit, inAttachDialog,
            getString(editing ? R.string.RichEditorEditCopyButton : R.string.RichEditorCreateCopyButton),
            getString(R.string.RichEditorButtonCopyText), copyText,
            (label, value) -> {
                final TL_keyboard.TL_inlineButtonTypeCopy type = new TL_keyboard.TL_inlineButtonTypeCopy();
                type.copy_text = value;
                edit.apply(label, type);
            });
    }

    private static void showBlockTextAndValueDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                                    RichEditorListView.BlockButtonEdit edit, boolean inAttachDialog,
                                                    String title, String valueHint, String initialValue,
                                                    BlockApply apply) {
        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), 0, dp(24), 0);
        final EditTextBoldCursor labelField = createField(
            context, resourcesProvider, getString(R.string.RichEditorButtonText), edit.getLabel());
        final EditTextBoldCursor valueField = createField(context, resourcesProvider, valueHint, initialValue);
        fields.addView(labelField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));
        fields.addView(valueField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        final AlertDialog.Builder builder = createInputDialogBuilder(context, resourcesProvider, inAttachDialog);
        builder.setTitle(title)
            .setView(fields)
            .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                final String label = labelField.getText().toString().trim();
                final String value = valueField.getText().toString().trim();
                if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(value)) apply.run(label, value);
            });
        addCancelAndDelete(builder, edit);
        showInputDialog(builder, TextUtils.isEmpty(labelField.getText()) ? labelField : valueField,
            edit.exists() ? AlertDialog.BUTTON_NEUTRAL : 0, resourcesProvider);
    }

    private static void showBlockProfileDialog(BaseFragment fragment, Context context,
                                               Theme.ResourcesProvider resourcesProvider,
                                               RichEditorListView.BlockButtonEdit edit, boolean inAttachDialog) {
        final boolean editing = edit.exists();
        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), 0, dp(24), 0);
        final EditTextBoldCursor labelField = createField(
            context, resourcesProvider, getString(R.string.RichEditorButtonText), edit.getLabel());
        fields.addView(labelField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        final Runnable chooseUser = () -> {
            final String label = labelField.getText().toString().trim();
            if (TextUtils.isEmpty(label)) return;
            showUserPicker(fragment, inAttachDialog, userId -> {
                final TL_keyboard.TL_inlineButtonTypeUserProfile type = new TL_keyboard.TL_inlineButtonTypeUserProfile();
                type.user_id = userId;
                edit.apply(label, type);
            });
        };

        final AlertDialog.Builder builder = createInputDialogBuilder(context, resourcesProvider, inAttachDialog);
        builder.setTitle(getString(editing ? R.string.RichEditorEditProfileButton : R.string.RichEditorCreateProfileButton))
            .setView(fields)
            .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                if (!editing) {
                    chooseUser.run();
                    return;
                }
                final String label = labelField.getText().toString().trim();
                if (TextUtils.isEmpty(label) || edit.getUserId() <= 0) return;
                final TL_keyboard.TL_inlineButtonTypeUserProfile type = new TL_keyboard.TL_inlineButtonTypeUserProfile();
                type.user_id = edit.getUserId();
                edit.apply(label, type);
            });
        final int deleteButton;
        if (editing) {
            builder
                .setNeutralButton(getString(R.string.RichEditorChangeUser), (dialog, which) -> chooseUser.run())
                .setButton(AlertDialog.BUTTON_NEGATIVE_2, getString(R.string.Delete), (dialog, which) -> edit.delete())
                .setNegativeButton(getString(R.string.Cancel), null)
                .twoRowsButtonsWhenNeeded();
            deleteButton = AlertDialog.BUTTON_NEGATIVE_2;
        } else {
            builder.setNegativeButton(getString(R.string.Cancel), null);
            deleteButton = 0;
        }
        showInputDialog(builder, labelField, deleteButton, resourcesProvider);
    }

    private static AlertDialog.Builder createInputDialogBuilder(Context context,
                                                                Theme.ResourcesProvider resourcesProvider,
                                                                boolean inAttachDialog) {
        return inAttachDialog
            ? new AlertDialog.Builder(context, resourcesProvider)
            : new AlertDialogDecor.Builder(context, resourcesProvider);
    }

    private static void addCancelAndDelete(AlertDialog.Builder builder, RichEditorListView.BlockButtonEdit edit) {
        if (edit.exists()) {
            builder
                .setNeutralButton(getString(R.string.Delete), (dialog, which) -> edit.delete())
                .setNegativeButton(getString(R.string.Cancel), null);
        } else {
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
    }

    private static EditTextBoldCursor createField(Context context, Theme.ResourcesProvider resourcesProvider,
                                                   String hint, String initial) {
        final EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        field.setHintText(hint);
        field.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        field.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        field.setSingleLine(true);
        field.setFocusable(true);
        field.setTransformHintToHeaderOnFocus(false);
        field.setTransformHintToHeader(true);
        field.setText(initial == null ? "" : initial);
        field.setLineColors(
            Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider),
            Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider),
            Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        field.setBackgroundDrawable(null);
        field.setPadding(0, 0, 0, 0);
        field.setHighlightColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        field.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider));
        return field;
    }

    private static AlertDialog showInputDialog(AlertDialog.Builder builder, EditTextBoldCursor focusField,
                                               int deleteButton, Theme.ResourcesProvider resourcesProvider) {
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            focusField.requestFocus();
            AndroidUtilities.showKeyboard(focusField);
            focusField.setSelection(0, focusField.length());
        });
        dialog.showDelayed(250);
        // AlertDialogDecor inflates its buttons synchronously, then delays only the
        // entrance animation. Color Delete now so the first visible frame is red.
        if (deleteButton != 0 && dialog.getButton(deleteButton) instanceof TextView) {
            ((TextView) dialog.getButton(deleteButton)).setTextColor(
                Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
        return dialog;
    }

    private interface BlockApply {
        void run(String label, String value);
    }

    private interface UserPicked {
        void run(long userId);
    }

    private static void showUserPicker(BaseFragment fragment, boolean showAsSheet,
                                       UserPicked callback) {
        if (fragment == null) return;
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_USERS_ONLY);
        final DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((dialogs, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty() || dids.get(0).dialogId <= 0) return false;
            callback.run(dids.get(0).dialogId);
            dialogs.finishFragment();
            return true;
        });
        if (showAsSheet) {
            final BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
            params.transitionFromLeft = true;
            params.allowNestedScroll = false;
            fragment.showAsSheet(picker, params);
        } else {
            fragment.presentFragment(picker);
        }
    }
}

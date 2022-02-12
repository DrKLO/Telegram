package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BackButtonMenu {
    public static class PulledDialog<T> {
        Class<T> activity;
        int stackIndex;
        TLRPC.Chat chat;
        TLRPC.User user;
        long dialogId;
        int folderId;
        int filterId;
    }
    private static HashMap<Integer, ArrayList<PulledDialog>> pulledDialogs;

    public static ActionBarPopupWindow show(BaseFragment fragment, View backButton, long currentDialogId) {
        if (fragment == null) {
            return null;
        }
        final ActionBarLayout parentLayout = fragment.getParentLayout();
        ArrayList<PulledDialog> dialogs = getStackedHistoryDialogs(fragment.getCurrentAccount(), parentLayout == null ? null : parentLayout.fragmentsStack, currentDialogId);
        if (dialogs.size() <= 0) {
            return null;
        }

        Context context = fragment.getParentActivity();
        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        android.graphics.Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = fragment.getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        layout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        AtomicReference<ActionBarPopupWindow> scrimPopupWindowRef = new AtomicReference<>();

        for (int i = 0; i < dialogs.size(); ++i) {
            final PulledDialog pDialog = dialogs.get(i);
            final TLRPC.Chat chat = pDialog.chat;
            final TLRPC.User user = pDialog.user;
            FrameLayout cell = new FrameLayout(context);
            cell.setMinimumWidth(AndroidUtilities.dp(200));

            BackupImageView imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(32));
            cell.addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 13, 0, 0, 0));

            TextView titleView = new TextView(context);
            titleView.setLines(1);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 59, 0, 12, 0));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setSmallSize(true);
            if (chat != null) {
                avatarDrawable.setInfo(chat);
                imageView.setImage(ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, chat);
                titleView.setText(chat.title);
            } else if (user != null) {
                String name;
                if (pDialog.activity == ChatActivity.class && UserObject.isUserSelf(user)) {
                    name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isReplyUser(user)) {
                    name = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isDeleted(user)) {
                    name = LocaleController.getString("HiddenName", R.string.HiddenName);
                    avatarDrawable.setInfo(user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
                } else {
                    name = UserObject.getUserName(user);
                    avatarDrawable.setInfo(user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
                }
                titleView.setText(name);
            }

            cell.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), false));
            cell.setOnClickListener(e2 -> {
                if (scrimPopupWindowRef.get() != null) {
                    scrimPopupWindowRef.getAndSet(null).dismiss();
                }
                if (pDialog.stackIndex >= 0) {
                    Long nextFragmentDialogId = null;
                    if (parentLayout == null || parentLayout.fragmentsStack == null || pDialog.stackIndex >= parentLayout.fragmentsStack.size()) {
                        nextFragmentDialogId = null;
                    } else {
                        BaseFragment nextFragment = parentLayout.fragmentsStack.get(pDialog.stackIndex);
                        if (nextFragment instanceof ChatActivity) {
                            nextFragmentDialogId = ((ChatActivity) nextFragment).getDialogId();
                        } else if (nextFragment instanceof ProfileActivity) {
                            nextFragmentDialogId = ((ProfileActivity) nextFragment).getDialogId();
                        }
                    }
                    if (nextFragmentDialogId != null && nextFragmentDialogId != pDialog.dialogId) {
                        for (int j = parentLayout.fragmentsStack.size() - 2; j > pDialog.stackIndex; --j) {
                            parentLayout.removeFragmentFromStack(j);
                        }
                    } else {
                        if (parentLayout != null && parentLayout.fragmentsStack != null) {
                            for (int j = parentLayout.fragmentsStack.size() - 2; j > pDialog.stackIndex; --j) {
                                if (j >= 0 && j < parentLayout.fragmentsStack.size()) {
                                    parentLayout.removeFragmentFromStack(j);
                                }
                            }
                            if (pDialog.stackIndex < parentLayout.fragmentsStack.size()) {
                                parentLayout.showFragment(pDialog.stackIndex);
                                parentLayout.closeLastFragment(true);
                                return;
                            }
                        }
                    }
                }
                goToPulledDialog(fragment, pDialog);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        ActionBarPopupWindow scrimPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        scrimPopupWindowRef.set(scrimPopupWindow);
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        layout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        layout.setFitItems(true);

        View fragmentView = fragment.getFragmentView();
        if (fragmentView != null) {
            int popupX = AndroidUtilities.dp(8) - backgroundPaddings.left;
            if (AndroidUtilities.isTablet()) {
                int[] location = new int[2];
                fragmentView.getLocationInWindow(location);
                popupX += location[0];
            }
            int popupY = (int) (backButton.getBottom() - backgroundPaddings.top - AndroidUtilities.dp(8));
            scrimPopupWindow.showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, popupX, popupY);

            try {
                fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
        }

        return scrimPopupWindow;
    }

    public static void goToPulledDialog(BaseFragment fragment, PulledDialog dialog) {
        if (dialog == null) {
            return;
        }
        if (dialog.activity == ChatActivity.class) {
            Bundle bundle = new Bundle();
            if (dialog.chat != null) {
                bundle.putLong("chat_id", dialog.chat.id);
            } else if (dialog.user != null) {
                bundle.putLong("user_id", dialog.user.id);
            }
            bundle.putInt("dialog_folder_id", dialog.folderId);
            bundle.putInt("dialog_filter_id", dialog.filterId);
            fragment.presentFragment(new ChatActivity(bundle), true);
        } else if (dialog.activity == ProfileActivity.class) {
            Bundle bundle = new Bundle();
            bundle.putLong("dialog_id", dialog.dialogId);
            fragment.presentFragment(new ProfileActivity(bundle), true);
        }
    }

    public static ArrayList<PulledDialog> getStackedHistoryDialogs(int account, ArrayList<BaseFragment> fragmentsStack, long ignoreDialogId) {
        ArrayList<PulledDialog> dialogs = new ArrayList<>();
        if (fragmentsStack != null) {
            final int count = fragmentsStack.size();
            for (int i = 0; i < count; ++i) {
                BaseFragment fragment = fragmentsStack.get(i);
                Class activity;
                TLRPC.Chat chat;
                TLRPC.User user = null;
                long dialogId;
                int folderId, filterId;
                if (fragment instanceof ChatActivity) {
                    activity = ChatActivity.class;
                    ChatActivity chatActivity = (ChatActivity) fragment;
                    if (chatActivity.getChatMode() != 0 || chatActivity.isReport()) {
                        continue;
                    }
                    chat = chatActivity.getCurrentChat();
                    user = chatActivity.getCurrentUser();
                    dialogId = chatActivity.getDialogId();
                    folderId = chatActivity.getDialogFolderId();
                    filterId = chatActivity.getDialogFilterId();
                } else if (fragment instanceof ProfileActivity) {
                    activity = ProfileActivity.class;
                    ProfileActivity profileActivity = (ProfileActivity) fragment;
                    chat = profileActivity.getCurrentChat();
                    try {
                        user = profileActivity.getUserInfo().user;
                    } catch (Exception ignore) {}
                    dialogId = profileActivity.getDialogId();
                    folderId = 0;
                    filterId = 0;
                } else {
                    continue;
                }
                if (dialogId != ignoreDialogId && !(ignoreDialogId == 0 && UserObject.isUserSelf(user))) {
                    boolean alreadyAddedDialog = false;
                    for (int d = 0; d < dialogs.size(); ++d) {
                        if (dialogs.get(d).dialogId == dialogId) {
                            alreadyAddedDialog = true;
                            break;
                        }
                    }
                    if (!alreadyAddedDialog) {
                        PulledDialog pDialog = new PulledDialog();
                        pDialog.activity = activity;
                        pDialog.stackIndex = i;
                        pDialog.chat = chat;
                        pDialog.user = user;
                        pDialog.dialogId = dialogId;
                        pDialog.folderId = folderId;
                        pDialog.filterId = filterId;
                        if (pDialog.chat != null || pDialog.user != null) {
                            dialogs.add(pDialog);
                        }
                    }
                }
            }
        }
        if (pulledDialogs != null) {
            ArrayList<PulledDialog> pulledDialogsAccount = pulledDialogs.get(account);
            if (pulledDialogsAccount != null) {
                for (PulledDialog pulledDialog : pulledDialogsAccount) {
                    if (pulledDialog.dialogId == ignoreDialogId) {
                        continue;
                    }
                    boolean alreadyAddedDialog = false;
                    for (int d = 0; d < dialogs.size(); ++d) {
                        if (dialogs.get(d).dialogId == pulledDialog.dialogId) {
                            alreadyAddedDialog = true;
                            break;
                        }
                    }
                    if (!alreadyAddedDialog) {
                        dialogs.add(pulledDialog);
                    }
                }
            }
        }
        Collections.sort(dialogs, (d1, d2) -> d2.stackIndex - d1.stackIndex);
        return dialogs;
    }

    public static void addToPulledDialogs(int account, int stackIndex, TLRPC.Chat chat, TLRPC.User user, long dialogId, int folderId, int filterId) {
        if (chat == null && user == null) {
            return;
        }
        if (pulledDialogs == null) {
            pulledDialogs = new HashMap<>();
        }
        ArrayList<PulledDialog> dialogs = null;
        if (pulledDialogs.containsKey(account)) {
            dialogs = pulledDialogs.get(account);
        }
        if (dialogs == null) {
            pulledDialogs.put(account, dialogs = new ArrayList<>());
        }

        boolean alreadyAdded = false;
        for (PulledDialog d : dialogs) {
            if (d.dialogId == dialogId) {
                alreadyAdded = true;
                break;
            }
        }

        if (!alreadyAdded) {
            PulledDialog d = new PulledDialog();
            d.activity = ChatActivity.class;
            d.stackIndex = stackIndex;
            d.dialogId = dialogId;
            d.filterId = filterId;
            d.folderId = folderId;
            d.chat = chat;
            d.user = user;
            dialogs.add(d);
        }
    }
    public static void clearPulledDialogs(int account, int fromIndex) {
        if (pulledDialogs != null && pulledDialogs.containsKey(account)) {
            ArrayList<PulledDialog> dialogs = pulledDialogs.get(account);
            if (dialogs != null) {
                for (int i = 0; i < dialogs.size(); ++i) {
                    if (dialogs.get(i).stackIndex > fromIndex) {
                        dialogs.remove(i);
                        i--;
                    }
                }
            }
        }
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class BackButtonMenu {
    public static class PulledDialog<T> {
        Class<T> activity;
        int stackIndex;
        TLRPC.Chat chat;
        TLRPC.User user;
        TLRPC.TL_forumTopic topic;
        long dialogId;
        int folderId;
        int filterId;
    }

    public static ActionBarPopupWindow show(BaseFragment thisFragment, View backButton, long currentDialogId, long topicId, Theme.ResourcesProvider resourcesProvider) {
        if (thisFragment == null) {
            return null;
        }
        final INavigationLayout parentLayout = thisFragment.getParentLayout();
        final Context context = thisFragment.getParentActivity();
        final View fragmentView = thisFragment.getFragmentView();
        if (parentLayout == null || context == null || fragmentView == null) {
            return null;
        }
        ArrayList<PulledDialog> dialogs;
        if (topicId != 0) {
            dialogs = getStackedHistoryForTopic(thisFragment, currentDialogId, topicId);
        } else {
            dialogs = getStackedHistoryDialogs(thisFragment, currentDialogId);
        }

        if (dialogs.size() <= 0) {
            return null;
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider);
        android.graphics.Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = thisFragment.getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        layout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));

        AtomicReference<ActionBarPopupWindow> scrimPopupWindowRef = new AtomicReference<>();

        for (int i = 0; i < dialogs.size(); ++i) {
            final PulledDialog pDialog = dialogs.get(i);
            final TLRPC.Chat chat = pDialog.chat;
            final TLRPC.User user = pDialog.user;
            final TLRPC.TL_forumTopic topic = pDialog.topic;
            FrameLayout cell = new FrameLayout(context);
            cell.setMinimumWidth(AndroidUtilities.dp(200));

            BackupImageView imageView = new BackupImageView(context);
            if (chat == null && user == null) {
                imageView.setRoundRadius(0);
            } else {
                imageView.setRoundRadius(chat != null && chat.forum ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16));
            }
            cell.addView(imageView, LayoutHelper.createFrameRelatively(32, 32, Gravity.START | Gravity.CENTER_VERTICAL, 13, 0, 0, 0));

            TextView titleView = new TextView(context);
            titleView.setLines(1);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            cell.addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 59, 0, 12, 0));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setScaleSize(.8f);
            Drawable thumb = avatarDrawable;
            boolean addDivider = false;
            if (topic != null) {
                if (topic.id == 1) {
                    thumb = ForumUtilities.createGeneralTopicDrawable(fragmentView.getContext(), 1f, Theme.getColor(Theme.key_chat_inMenu, resourcesProvider), false);
                    imageView.setImageDrawable(thumb);
                } else if (topic.icon_emoji_id != 0) {
                    AnimatedEmojiDrawable animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_FORUM_TOPIC, thisFragment.getCurrentAccount(), topic.icon_emoji_id);
                    imageView.setAnimatedEmojiDrawable(animatedEmojiDrawable);
                } else {
                    thumb = ForumUtilities.createTopicDrawable(topic, false);
                    imageView.setImageDrawable(thumb);
                }
                titleView.setText(topic.title);
            } else if (chat != null) {
                avatarDrawable.setInfo(thisFragment.getCurrentAccount(), chat);
                if (chat.photo != null && chat.photo.strippedBitmap != null) {
                    thumb = chat.photo.strippedBitmap;
                }
                imageView.setImage(ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL), "50_50", thumb, chat);
                titleView.setText(chat.title);
            } else if (user != null) {
                String name;
                if (user.photo != null && user.photo.strippedBitmap != null) {
                    thumb = user.photo.strippedBitmap;
                }
                if (pDialog.activity == ChatActivity.class && UserObject.isUserSelf(user)) {
                    name = LocaleController.getString(R.string.SavedMessages);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isReplyUser(user)) {
                    name = LocaleController.getString(R.string.RepliesTitle);
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                    imageView.setImageDrawable(avatarDrawable);
                } else if (UserObject.isDeleted(user)) {
                    name = LocaleController.getString(R.string.HiddenName);
                    avatarDrawable.setInfo(thisFragment.getCurrentAccount(), user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user);
                } else {
                    name = UserObject.getUserName(user);
                    avatarDrawable.setInfo(thisFragment.getCurrentAccount(), user);
                    imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", thumb, user);
                }
                titleView.setText(name);
            } else {
                Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_viewchats).mutate();
                imageView.setImageDrawable(drawable);
                imageView.setSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
                imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
                titleView.setText(LocaleController.getString(R.string.AllChats));
                addDivider = true;
            }

            cell.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), false));
            cell.setOnClickListener(e2 -> {
                if (scrimPopupWindowRef.get() != null) {
                    scrimPopupWindowRef.getAndSet(null).dismiss();
                }
                if (pDialog.stackIndex >= 0) {
                    Long nextFragmentDialogId = null;
                    Long nextFragmentTopicId = null;
                    if (parentLayout == null || parentLayout.getFragmentStack() == null || pDialog.stackIndex >= parentLayout.getFragmentStack().size()) {
                        nextFragmentDialogId = null;
                        nextFragmentTopicId = null;
                    } else {
                        BaseFragment nextFragment = parentLayout.getFragmentStack().get(pDialog.stackIndex);
                        if (nextFragment instanceof ChatActivity) {
                            nextFragmentDialogId = ((ChatActivity) nextFragment).getDialogId();
                            nextFragmentTopicId = ((ChatActivity) nextFragment).getTopicId();
                        } else if (nextFragment instanceof ProfileActivity) {
                            nextFragmentDialogId = ((ProfileActivity) nextFragment).getDialogId();
                            nextFragmentTopicId = ((ProfileActivity) nextFragment).getTopicId();
                        }
                    }
                    if (nextFragmentDialogId != null && nextFragmentDialogId != pDialog.dialogId || topic != null && nextFragmentTopicId != null && topic.id != nextFragmentTopicId) {
                        for (int j = parentLayout.getFragmentStack().size() - 2; j > pDialog.stackIndex; --j) {
                            parentLayout.removeFragmentFromStack(j);
                        }
                    } else {
                        if (parentLayout != null && parentLayout.getFragmentStack() != null) {
                            ArrayList<BaseFragment> fragments = new ArrayList<>(parentLayout.getFragmentStack());
                            for (int j = fragments.size() - 2; j > pDialog.stackIndex; --j) {
                                fragments.get(j).removeSelfFromStack();
                            }
                            if (pDialog.stackIndex < parentLayout.getFragmentStack().size()) {
                               // parentLayout.bringToFront(pDialog.stackIndex);
                                parentLayout.closeLastFragment(true);
                                return;
                            }
                        }
                    }
                }
                goToPulledDialog(thisFragment, pDialog);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            if (addDivider) {
                FrameLayout gap = new FrameLayout(context);
                gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
                gap.setTag(R.id.fit_width_tag, 1);
                layout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
            }
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

        int popupX = AndroidUtilities.dp(8) - backgroundPaddings.left;
        if (AndroidUtilities.isTablet()) {
            int[] location = new int[2];
            fragmentView.getLocationInWindow(location);
            popupX += location[0];
        }
        int popupY = (int) (backButton.getBottom() - backgroundPaddings.top - AndroidUtilities.dp(8));
        scrimPopupWindow.showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, popupX, popupY);

//        try {
//            fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//        } catch (Exception ignore) {}

        return scrimPopupWindow;
    }

    private static ArrayList<PulledDialog> getStackedHistoryForTopic(BaseFragment thisFragment, long currentDialogId, long topicId) {
        ArrayList<PulledDialog> dialogs = new ArrayList<>();
        if (thisFragment == null) {
            return dialogs;
        }
        final INavigationLayout parentLayout = thisFragment.getParentLayout();
        if (parentLayout == null) {
            return dialogs;
        }
        int maxStackIndex = -1;
        List<PulledDialog> pulledDialogs = parentLayout.getPulledDialogs();
        if (pulledDialogs != null) {
            for (int i = 0; i < pulledDialogs.size(); i++) {
                PulledDialog pulledDialog = pulledDialogs.get(i);
                if (pulledDialog.topic == null || pulledDialog.topic.id == topicId) {
                    continue;
                }
                if (pulledDialog.stackIndex >= maxStackIndex) {
                    maxStackIndex = pulledDialog.stackIndex;
                }
                dialogs.add(pulledDialog);
            }
        }
        if (parentLayout.getFragmentStack().size() > 1 && parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2) instanceof TopicsFragment) {
            PulledDialog pulledDialog = new PulledDialog();
            dialogs.add(pulledDialog);
            pulledDialog.stackIndex = ++maxStackIndex;
            pulledDialog.activity = DialogsActivity.class;

            pulledDialog = new PulledDialog();
            dialogs.add(pulledDialog);
            pulledDialog.stackIndex = -1;
            pulledDialog.activity = TopicsFragment.class;
            pulledDialog.chat = MessagesController.getInstance(thisFragment.getCurrentAccount()).getChat(-currentDialogId);
        } else {
            PulledDialog pulledDialog = new PulledDialog();
            dialogs.add(pulledDialog);
            pulledDialog.stackIndex = -1;
            pulledDialog.activity = TopicsFragment.class;
            pulledDialog.chat = MessagesController.getInstance(thisFragment.getCurrentAccount()).getChat(-currentDialogId);
        }
        Collections.sort(dialogs, (d1, d2) -> d2.stackIndex - d1.stackIndex);
        return dialogs;
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
            if (dialog.topic != null) {
                ChatActivity chatActivity = ForumUtilities.getChatActivityForTopic(fragment, dialog.chat.id, dialog.topic, 0, bundle);
                fragment.presentFragment(chatActivity, true);
            } else {
                fragment.presentFragment(new ChatActivity(bundle), true);
            }
        } else if (dialog.activity == ProfileActivity.class) {
            Bundle bundle = new Bundle();
            bundle.putLong("dialog_id", dialog.dialogId);
            fragment.presentFragment(new ProfileActivity(bundle), true);
        } if (dialog.activity == TopicsFragment.class) {
            Bundle bundle = new Bundle();
            bundle.putLong("chat_id", dialog.chat.id);
            fragment.presentFragment(new TopicsFragment(bundle), true);
        } if (dialog.activity == DialogsActivity.class) {

            fragment.presentFragment(new DialogsActivity(null), true);
        }
    }

    public static ArrayList<PulledDialog> getStackedHistoryDialogs(BaseFragment thisFragment, long ignoreDialogId) {
        ArrayList<PulledDialog> dialogs = new ArrayList<>();
        if (thisFragment == null)
            return dialogs;
        final INavigationLayout parentLayout = thisFragment.getParentLayout();
        if (parentLayout == null)
            return dialogs;
        List<BaseFragment> fragmentsStack = parentLayout.getFragmentStack();
        List<PulledDialog> pulledDialogs = parentLayout.getPulledDialogs();
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
            int count = pulledDialogs.size();
            for (int i = count - 1; i >= 0; --i) {
                PulledDialog pulledDialog = pulledDialogs.get(i);
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
        Collections.sort(dialogs, (d1, d2) -> d2.stackIndex - d1.stackIndex);
        return dialogs;
    }

    public static void addToPulledDialogs(BaseFragment thisFragment, int stackIndex, TLRPC.Chat chat, TLRPC.User user, TLRPC.TL_forumTopic topic, long dialogId, int folderId, int filterId) {
        if (chat == null && user == null) {
            return;
        }
        if (thisFragment == null) {
            return;
        }
        INavigationLayout parentLayout = thisFragment.getParentLayout();
        if (parentLayout == null) {
            return;
        }
        if (parentLayout.getPulledDialogs() == null) {
            parentLayout.setPulledDialogs(new ArrayList<>());
        }
        boolean alreadyAdded = false;
        for (PulledDialog d : parentLayout.getPulledDialogs()) {
            if (topic == null && d.dialogId == dialogId || topic != null && d.topic != null && d.topic.id == topic.id) {
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
            d.topic = topic;
            parentLayout.getPulledDialogs().add(d);
        }
    }
    public static void clearPulledDialogs(BaseFragment thisFragment, int fromIndex) {
        if (thisFragment == null) {
            return;
        }
        final INavigationLayout parentLayout = thisFragment.getParentLayout();
        if (parentLayout == null) {
            return;
        }
        if (parentLayout.getPulledDialogs() != null) {
            for (int i = 0; i < parentLayout.getPulledDialogs().size(); ++i) {
                if (parentLayout.getPulledDialogs().get(i).stackIndex > fromIndex) {
                    parentLayout.getPulledDialogs().remove(i);
                    i--;
                }
            }
        }
    }
}

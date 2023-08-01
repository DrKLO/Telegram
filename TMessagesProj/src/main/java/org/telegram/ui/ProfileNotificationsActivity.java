/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class ProfileNotificationsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private AnimatorSet animatorSet;
    private Theme.ResourcesProvider resourcesProvider;

    private long dialogId;
    private int topicId;

    private boolean addingException;

    private boolean notificationsEnabled;

    private ProfileNotificationsActivityDelegate delegate;

    ChatAvatarContainer avatarContainer;

    private int generalRow;
    private int avatarRow;
    private int avatarSectionRow;
    private int enableRow;
    private int previewRow;
    private int soundRow;
    private int vibrateRow;
    private int smartRow;
    private int priorityRow;
    private int priorityInfoRow;
    private int popupRow;
    private int popupEnabledRow;
    private int popupDisabledRow;
    private int popupInfoRow;
    private int storiesRow;
    private int callsRow;
    private int ringtoneRow;
    private int callsVibrateRow;
    private int ringtoneInfoRow;
    private int ledRow;
    private int colorRow;
    private int ledInfoRow;
    private int customResetRow;
    private int customResetShadowRow;
    private int rowCount;

    private boolean isInTop5Peers;

    private boolean needReset;

    private final static int done_button = 1;

    public interface ProfileNotificationsActivityDelegate {
        void didCreateNewException(NotificationsSettingsActivity.NotificationException exception);
        default void didRemoveException(long dialog_id) {}
    }

    public ProfileNotificationsActivity(Bundle args) {
        this(args, null);
    }

    public ProfileNotificationsActivity(Bundle args, Theme.ResourcesProvider resourcesProvider) {
        super(args);
        this.resourcesProvider = resourcesProvider;
        dialogId = args.getLong("dialog_id");
        topicId = args.getInt("topic_id");
        addingException = args.getBoolean("exception", false);
    }


    @Override
    public boolean onFragmentCreate() {
        if (DialogObject.isUserDialog(dialogId)) {
            ArrayList<TLRPC.TL_topPeer> topPeers = getMediaDataController().hints;
            for (int i = 0; i < topPeers.size(); ++i) {
                TLRPC.Peer peer = topPeers.get(i).peer;
                if (peer instanceof TLRPC.TL_peerUser && peer.user_id == dialogId) {
                    isInTop5Peers = i < 5;
                    break;
                }
            }
        }

        rowCount = 0;
        if (addingException) {
            avatarRow = rowCount++;
            avatarSectionRow = rowCount++;
        } else {
            avatarRow = -1;
            avatarSectionRow = -1;
        }
        generalRow = rowCount++;
        if (addingException || topicId != 0) {
            enableRow = rowCount++;
        } else {
            enableRow = -1;
        }
        storiesRow = -1;
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            previewRow = rowCount++;
            if (DialogObject.isUserDialog(dialogId)) {
                storiesRow = rowCount++;
            }
        } else {
            previewRow = -1;
        }
        soundRow = rowCount++;
        vibrateRow = rowCount++;
        if (DialogObject.isChatDialog(dialogId)) {
            smartRow = rowCount++;
        } else {
            smartRow = -1;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            priorityRow = rowCount++;
        } else {
            priorityRow = -1;
        }
        priorityInfoRow = rowCount++;
        boolean isChannel;
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
        } else {
            isChannel = false;
        }
        if (!DialogObject.isEncryptedDialog(dialogId) && !isChannel) {
            popupRow = rowCount++;
            popupEnabledRow = rowCount++;
            popupDisabledRow = rowCount++;
            popupInfoRow = rowCount++;
        } else {
            popupRow = -1;
            popupEnabledRow = -1;
            popupDisabledRow = -1;
            popupInfoRow = -1;
        }

        if (DialogObject.isUserDialog(dialogId)) {
            callsRow = rowCount++;
            callsVibrateRow = rowCount++;
            ringtoneRow = rowCount++;
            ringtoneInfoRow = rowCount++;
        } else {
            callsRow = -1;
            callsVibrateRow = -1;
            ringtoneRow = -1;
            ringtoneInfoRow = -1;
        }

        ledRow = rowCount++;
        colorRow = rowCount++;
        ledInfoRow = rowCount++;

        if (!addingException) {
            customResetRow = rowCount++;
            customResetShadowRow = rowCount++;
        } else {
            customResetRow = -1;
            customResetShadowRow = -1;
        }

        boolean defaultEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(dialogId);
        if (addingException) {
            notificationsEnabled = !defaultEnabled;
        } else {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);

            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            boolean hasOverride = preferences.contains("notify2_" + key);
            int value = preferences.getInt("notify2_" + key, 0);
            if (value == 0) {
                if (hasOverride) {
                    notificationsEnabled = true;
                } else {
                    notificationsEnabled = NotificationsController.getInstance(currentAccount).isGlobalNotificationsEnabled(dialogId);
                }
            } else if (value == 1) {
                notificationsEnabled = true;
            } else if (value == 2) {
                notificationsEnabled = false;
            } else {
                notificationsEnabled = false;
            }
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (!needReset) {
            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("custom_" + key, true).apply();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    @Override
    public View createView(final Context context) {
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue, resourcesProvider), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!addingException && notificationsEnabled) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("notify2_" + key, 0).apply();
                    }
                } else if (id == done_button) {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("custom_" + key, true);

                    TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialogId);
                    if (notificationsEnabled) {
                        editor.putInt("notify2_" + key, 0);
                        if (topicId == 0) {
                            MessagesStorage.getInstance(currentAccount).setDialogFlags(dialogId, 0);
                            if (dialog != null) {
                                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                            }
                        }
                    } else {
                        editor.putInt("notify2_" + key, 2);
                        if (topicId == 0) {
                            NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialogId);
                            MessagesStorage.getInstance(currentAccount).setDialogFlags(dialogId, 1);
                            if (dialog != null) {
                                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                                dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                            }
                        }
                    }

                    editor.apply();
                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialogId, topicId);
                    if (delegate != null) {
                        NotificationsSettingsActivity.NotificationException exception = new NotificationsSettingsActivity.NotificationException();
                        exception.did = dialogId;
                        exception.hasCustom = true;
                        exception.notify = preferences.getInt("notify2_" + key, 0);
                        if (exception.notify != 0) {
                            exception.muteUntil = preferences.getInt("notifyuntil_" + key, 0);
                        }
                        delegate.didCreateNewException(exception);
                    }
                }
                finishFragment();
            }
        });

        avatarContainer = new ChatAvatarContainer(context, null, false, resourcesProvider);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());

        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, !inPreviewMode ? 56 : 0, 0, 40, 0));
        actionBar.setAllowOverlayTitle(false);
        if (dialogId < 0) {
            if (topicId != 0) {
                TLRPC.TL_forumTopic forumTopic = getMessagesController().getTopicsController().findTopic(-dialogId, topicId);
                ForumUtilities.setTopicIcon(avatarContainer.getAvatarImageView(), forumTopic, false, true, resourcesProvider);
                avatarContainer.setTitle(forumTopic.title);
            } else {
                TLRPC.Chat chatLocal = getMessagesController().getChat(-dialogId);
                avatarContainer.setChatAvatar(chatLocal);
                avatarContainer.setTitle(chatLocal.title);
            }
        } else {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) {
                avatarContainer.setUserAvatar(user);
                avatarContainer.setTitle(ContactsController.formatName(user.first_name, user.last_name));
            }
        }

        if (addingException) {
            avatarContainer.setSubtitle(LocaleController.getString("NotificationsNewException", R.string.NotificationsNewException));
            actionBar.createMenu().addItem(done_button, LocaleController.getString("Done", R.string.Done).toUpperCase());
        } else {
            avatarContainer.setSubtitle(LocaleController.getString("CustomNotifications", R.string.CustomNotifications));
        }

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        listView = new RecyclerListView(context);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });

        listView.setOnItemClickListener((view, position) -> {
            if (!view.isEnabled()) {
                return;
            }
            if (position == customResetRow) {
                AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
                        .setTitle(LocaleController.getString(R.string.ResetCustomNotificationsAlertTitle))
                        .setMessage(LocaleController.getString(R.string.ResetCustomNotificationsAlert))
                        .setPositiveButton(LocaleController.getString(R.string.Reset), (d, w) -> {
                            needReset = true;

                            MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("custom_" + key, false).remove("notify2_" + key).apply();
                            finishFragment();
                            if (delegate != null) {
                                delegate.didRemoveException(dialogId);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            } else if (position == soundRow) {
                Bundle bundle = new Bundle();
                bundle.putLong("dialog_id", dialogId);
                bundle.putInt("topic_id", topicId);
                presentFragment(new NotificationsSoundActivity(bundle, resourcesProvider));
            } else if (position == ringtoneRow) {
                try {
                    Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    Uri currentSound = null;

                    String defaultPath = null;
                    Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                    if (defaultUri != null) {
                        defaultPath = defaultUri.getPath();
                    }

                    String path = preferences.getString("ringtone_path_" + key, defaultPath);
                    if (path != null && !path.equals("NoSound")) {
                        if (path.equals(defaultPath)) {
                            currentSound = defaultUri;
                        } else {
                            currentSound = Uri.parse(path);
                        }
                    }

                    tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                    startActivityForResult(tmpIntent, 13);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (position == vibrateRow) {
                showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), dialogId, topicId, false, false, () -> {
                    if (adapter != null) {
                        adapter.notifyItemChanged(vibrateRow);
                    }
                }, resourcesProvider));
            } else if (position == enableRow) {
                TextCheckCell checkCell = (TextCheckCell) view;
                notificationsEnabled = !checkCell.isChecked();
                checkCell.setChecked(notificationsEnabled);
                checkRowsEnabled();
            } else if (position == previewRow) {
                TextCheckCell checkCell = (TextCheckCell) view;
                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("content_preview_" + key, !checkCell.isChecked()).apply();
                checkCell.setChecked(!checkCell.isChecked());
            } else if (position == callsVibrateRow) {
                showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), dialogId, topicId, "calls_vibrate_" + key, () -> {
                    if (adapter != null) {
                        adapter.notifyItemChanged(callsVibrateRow);
                    }
                }, resourcesProvider));
            } else if (position == priorityRow) {
                showDialog(AlertsCreator.createPrioritySelectDialog(getParentActivity(), dialogId, topicId, -1, () -> {
                    if (adapter != null) {
                        adapter.notifyItemChanged(priorityRow);
                    }
                }, resourcesProvider));
            } else if (position == smartRow) {
                if (getParentActivity() == null) {
                    return;
                }
                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                int notifyMaxCount = preferences.getInt("smart_max_count_" + key, 2);
                int notifyDelay = preferences.getInt("smart_delay_" + key, 3 * 60);
                if (notifyMaxCount == 0) {
                    notifyMaxCount = 2;
                }
                AlertsCreator.createSoundFrequencyPickerDialog(getParentActivity(), notifyMaxCount, notifyDelay, (time, minute) -> {
                    MessagesController.getNotificationsSettings(currentAccount).edit()
                            .putInt("smart_max_count_" + key, time)
                            .putInt("smart_delay_" + key, minute)
                            .apply();
                    if (adapter != null) {
                        adapter.notifyItemChanged(smartRow);
                    }
                }, resourcesProvider);
            } else if (position == colorRow) {
                if (getParentActivity() == null) {
                    return;
                }
                showDialog(AlertsCreator.createColorSelectDialog(getParentActivity(), dialogId, topicId, -1, () -> {
                    if (adapter != null) {
                        adapter.notifyItemChanged(colorRow);
                    }
                }, resourcesProvider));
            } else if (position == popupEnabledRow) {
                MessagesController.getNotificationsSettings(currentAccount).edit().putInt("popup_" + key, 1).apply();
                ((RadioCell) view).setChecked(true, true);
                view = listView.findViewWithTag(2);
                if (view != null) {
                    ((RadioCell) view).setChecked(false, true);
                }
            } else if (position == popupDisabledRow) {
                MessagesController.getNotificationsSettings(currentAccount).edit().putInt("popup_" + key, 2).apply();
                ((RadioCell) view).setChecked(true, true);
                view = listView.findViewWithTag(1);
                if (view != null) {
                    ((RadioCell) view).setChecked(false, true);
                }
            } else if (position == storiesRow) {
                TextCheckCell checkCell = (TextCheckCell) view;
                boolean value = !checkCell.isChecked();
                checkCell.setChecked(value);
                SharedPreferences.Editor edit = MessagesController.getNotificationsSettings(currentAccount).edit();
                if (isInTop5Peers && value) {
                    edit.remove("stories_" + key);
                } else {
                    edit.putBoolean("stories_" + key, value);
                }
                edit.apply();getNotificationsController().updateServerNotificationsSettings(dialogId, topicId);
            }
        });

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            Uri ringtone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String name = null;
            if (ringtone != null) {
                Ringtone rng = RingtoneManager.getRingtone(ApplicationLoader.applicationContext, ringtone);
                if (rng != null) {
                    if (requestCode == 13) {
                        if (ringtone.equals(Settings.System.DEFAULT_RINGTONE_URI)) {
                            name = LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    } else {
                        if (ringtone.equals(Settings.System.DEFAULT_NOTIFICATION_URI)) {
                            name = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                        } else {
                            name = rng.getTitle(getParentActivity());
                        }
                    }
                    rng.stop();
                }
            }

            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor = preferences.edit();

            String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
            if (requestCode == 12) {
                if (name != null) {
                    editor.putString("sound_" + key, name);
                    editor.putString("sound_path_" + key, ringtone.toString());
                } else {
                    editor.putString("sound_" + key, "NoSound");
                    editor.putString("sound_path_" + key, "NoSound");
                }
                getNotificationsController().deleteNotificationChannel(dialogId, topicId);
            } else if (requestCode == 13) {
                if (name != null) {
                    editor.putString("ringtone_" + key, name);
                    editor.putString("ringtone_path_" + key, ringtone.toString());
                } else {
                    editor.putString("ringtone_" + key, "NoSound");
                    editor.putString("ringtone_path_" + key, "NoSound");
                }
            }
            editor.apply();
            if (adapter != null) {
                adapter.notifyItemChanged(requestCode == 13 ? ringtoneRow : soundRow);
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsSettingsUpdated) {
            try {
                adapter.notifyDataSetChanged();
            } catch (Exception ignored) {}
        }
    }

    public void setDelegate(ProfileNotificationsActivityDelegate profileNotificationsActivityDelegate) {
        delegate = profileNotificationsActivityDelegate;
    }

    private void checkRowsEnabled() {
        int count = listView.getChildCount();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
            int type = holder.getItemViewType();
            int position = holder.getAdapterPosition();
            if (position != enableRow && position != customResetRow) {
                switch (type) {
                    case ListAdapter.VIEW_TYPE_HEADER: {
                        HeaderCell textCell = (HeaderCell) holder.itemView;
                        textCell.setEnabled(notificationsEnabled, animators);
                        break;
                    }
                    case ListAdapter.VIEW_TYPE_TEXT_SETTINGS: {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setEnabled(notificationsEnabled, animators);
                        break;
                    }
                    case ListAdapter.VIEW_TYPE_INFO: {
                        TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                        textCell.setEnabled(notificationsEnabled, animators);
                        break;
                    }
                    case ListAdapter.VIEW_TYPE_TEXT_COLOR: {
                        TextColorCell textCell = (TextColorCell) holder.itemView;
                        textCell.setEnabled(notificationsEnabled, animators);
                        break;
                    }
                    case ListAdapter.VIEW_TYPE_RADIO: {
                        RadioCell radioCell = (RadioCell) holder.itemView;
                        radioCell.setEnabled(notificationsEnabled, animators);
                        break;
                    }
                    case ListAdapter.VIEW_TYPE_TEXT_CHECK: {
                        if (position == previewRow) {
                            TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                            checkCell.setEnabled(notificationsEnabled, animators);
                        }
                        break;
                    }
                }
            }
        }
        if (!animators.isEmpty()) {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.setDuration(150);
            animatorSet.start();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_HEADER = 0,
            VIEW_TYPE_TEXT_SETTINGS = 1,
            VIEW_TYPE_INFO = 2,
            VIEW_TYPE_TEXT_COLOR = 3,
            VIEW_TYPE_RADIO = 4,
            VIEW_TYPE_USER = 5,
            VIEW_TYPE_SHADOW = 6,
            VIEW_TYPE_TEXT_CHECK = 7;

        private Context context;

        public ListAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == previewRow) {
                return notificationsEnabled;
            } else if (holder.getAdapterPosition() == customResetRow) {
                return true;
            }
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_TEXT_SETTINGS:
                case VIEW_TYPE_TEXT_COLOR:
                case VIEW_TYPE_RADIO: {
                    return notificationsEnabled;
                }
                case VIEW_TYPE_HEADER:
                case VIEW_TYPE_INFO:
                case VIEW_TYPE_USER:
                case VIEW_TYPE_SHADOW: {
                    return false;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    return true;
                }
            }
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(context, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_TEXT_SETTINGS:
                    view = new TextSettingsCell(context, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(context, resourcesProvider);
                    break;
                case VIEW_TYPE_TEXT_COLOR:
                    view = new TextColorCell(context, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_RADIO:
                    view = new RadioCell(context, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_USER:
                    view = new UserCell2(context, 4, 0, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(context, resourcesProvider);
                    break;
                case VIEW_TYPE_TEXT_CHECK:
                default:
                    view = new TextCheckCell(context, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == generalRow) {
                        headerCell.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == popupRow) {
                        headerCell.setText(LocaleController.getString("ProfilePopupNotification", R.string.ProfilePopupNotification));
                    } else if (position == ledRow) {
                        headerCell.setText(LocaleController.getString("NotificationsLed", R.string.NotificationsLed));
                    } else if (position == callsRow) {
                        headerCell.setText(LocaleController.getString("VoipNotificationSettings", R.string.VoipNotificationSettings));
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_SETTINGS: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == customResetRow) {
                        textCell.setText(LocaleController.getString(R.string.ResetCustomNotifications), false);
                        textCell.setTextColor(getThemedColor(Theme.key_text_RedBold));
                    } else {
                        textCell.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == soundRow) {
                            String value = preferences.getString("sound_" + key, LocaleController.getString("SoundDefault", R.string.SoundDefault));
                            long documentId = preferences.getLong("sound_document_id_" + key, 0);
                            if (documentId != 0) {
                                TLRPC.Document document = getMediaDataController().ringtoneDataStore.getDocument(documentId);
                                if (document == null) {
                                    value = LocaleController.getString("CustomSound", R.string.CustomSound);
                                } else {
                                    value = NotificationsSoundActivity.trimTitle(document, document.file_name_fixed);
                                }
                            } else if (value.equals("NoSound")) {
                                value = LocaleController.getString("NoSound", R.string.NoSound);
                            } else if (value.equals("Default")) {
                                value = LocaleController.getString("SoundDefault", R.string.SoundDefault);
                            }
                            textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                        } else if (position == ringtoneRow) {
                            String value = preferences.getString("ringtone_" + key, LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone));
                            if (value.equals("NoSound")) {
                                value = LocaleController.getString("NoSound", R.string.NoSound);
                            }
                            textCell.setTextAndValue(LocaleController.getString("VoipSettingsRingtone", R.string.VoipSettingsRingtone), value, false);
                        } else if (position == vibrateRow) {
                            int value = preferences.getInt("vibrate_" + key, 0);
                            if (value == 0 || value == 4) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDefault", R.string.VibrationDefault), smartRow != -1 || priorityRow != -1);
                            } else if (value == 1) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), smartRow != -1 || priorityRow != -1);
                            } else if (value == 2) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled), smartRow != -1 || priorityRow != -1);
                            } else if (value == 3) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), smartRow != -1 || priorityRow != -1);
                            }
                        } else if (position == priorityRow) {
                            int value = preferences.getInt("priority_" + key, 3);
                            if (value == 0) {
                                textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityHigh", R.string.NotificationsPriorityHigh), false);
                            } else if (value == 1 || value == 2) {
                                textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityUrgent", R.string.NotificationsPriorityUrgent), false);
                            } else if (value == 3) {
                                textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPrioritySettings", R.string.NotificationsPrioritySettings), false);
                            } else if (value == 4) {
                                textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityLow", R.string.NotificationsPriorityLow), false);
                            } else if (value == 5) {
                                textCell.setTextAndValue(LocaleController.getString("NotificationsImportance", R.string.NotificationsImportance), LocaleController.getString("NotificationsPriorityMedium", R.string.NotificationsPriorityMedium), false);
                            }
                        } else if (position == smartRow) {
                            int notifyMaxCount = preferences.getInt("smart_max_count_" + key, 2);
                            int notifyDelay = preferences.getInt("smart_delay_" + key, 3 * 60);
                            if (notifyMaxCount == 0) {
                                textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.getString("SmartNotificationsDisabled", R.string.SmartNotificationsDisabled), priorityRow != -1);
                            } else {
                                String minutes = LocaleController.formatPluralString("Minutes", notifyDelay / 60);
                                textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.formatString("SmartNotificationsInfo", R.string.SmartNotificationsInfo, notifyMaxCount, minutes), priorityRow != -1);
                            }
                        } else if (position == callsVibrateRow) {
                            int value = preferences.getInt("calls_vibrate_" + key, 0);
                            if (value == 0 || value == 4) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDefault", R.string.VibrationDefault), true);
                            } else if (value == 1) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Short", R.string.Short), true);
                            } else if (value == 2) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("VibrationDisabled", R.string.VibrationDisabled), true);
                            } else if (value == 3) {
                                textCell.setTextAndValue(LocaleController.getString("Vibrate", R.string.Vibrate), LocaleController.getString("Long", R.string.Long), true);
                            }
                        }
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    textCell.setFixedSize(0);
                    if (position == popupInfoRow) {
                        textCell.setText(LocaleController.getString("ProfilePopupNotificationInfo", R.string.ProfilePopupNotificationInfo));
                        textCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == ledInfoRow) {
                        textCell.setText(LocaleController.getString("NotificationsLedInfo", R.string.NotificationsLedInfo));
                        textCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == priorityInfoRow) {
                        if (priorityRow == -1) {
                            textCell.setText("");
                        } else {
                            textCell.setText(LocaleController.getString("PriorityInfo", R.string.PriorityInfo));
                        }
                        textCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == ringtoneInfoRow) {
                        textCell.setText(LocaleController.getString("VoipRingtoneInfo", R.string.VoipRingtoneInfo));
                        textCell.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_COLOR: {
                    TextColorCell textCell = (TextColorCell) holder.itemView;
                    String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    int color;
                    if (preferences.contains("color_" + key)) {
                        color = preferences.getInt("color_" + key, 0xff0000ff);
                    } else {
                        if (DialogObject.isChatDialog(dialogId)) {
                            color = preferences.getInt("GroupLed", 0xff0000ff);
                        } else {
                            color = preferences.getInt("MessagesLed", 0xff0000ff);
                        }
                    }
                    for (int a = 0; a < 9; a++) {
                        if (TextColorCell.colorsToSave[a] == color) {
                            color = TextColorCell.colors[a];
                            break;
                        }
                    }
                    textCell.setTextAndColor(LocaleController.getString("NotificationsLedColor", R.string.NotificationsLedColor), color, false);
                    break;
                }
                case VIEW_TYPE_RADIO: {
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
                    int popup = preferences.getInt("popup_" + key, 0);
                    if (popup == 0) {
                        popup = preferences.getInt(DialogObject.isChatDialog(dialogId) ? "popupGroup" : "popupAll", 0);
                        if (popup != 0) {
                            popup = 1;
                        } else {
                            popup = 2;
                        }
                    }
                    if (position == popupEnabledRow) {
                        radioCell.setText(LocaleController.getString("PopupEnabled", R.string.PopupEnabled), popup == 1, true);
                        radioCell.setTag(1);
                    } else if (position == popupDisabledRow) {
                        radioCell.setText(LocaleController.getString("PopupDisabled", R.string.PopupDisabled), popup == 2, false);
                        radioCell.setTag(2);
                    }
                    break;
                }
                case VIEW_TYPE_USER: {
                    UserCell2 userCell2 = (UserCell2) holder.itemView;
                    TLObject object;
                    if (DialogObject.isUserDialog(dialogId)) {
                        object = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    } else {
                        object = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    }
                    userCell2.setData(object, null, null, 0);
                    break;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == enableRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("Notifications", R.string.Notifications), notificationsEnabled, true);
                    } else if (position == previewRow) {
                        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
                        checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("content_preview_" + key, true), true);
                    } else if (position == storiesRow) {
                        String key = NotificationsController.getSharedPrefKey(dialogId, topicId);
                        boolean value = preferences.getBoolean("stories_" + key, isInTop5Peers || preferences.contains("EnableAllStories") && preferences.getBoolean("EnableAllStories", true));
                        checkCell.setTextAndCheck(LocaleController.getString("StoriesSoundEnabled", R.string.StoriesSoundEnabled), value, true);
                    }
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    ShadowSectionCell shadowCell = (ShadowSectionCell) holder.itemView;
                    shadowCell.setTopBottom(position > 0, position < getItemCount() - 1);
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER: {
                    HeaderCell textCell = (HeaderCell) holder.itemView;
                    textCell.setEnabled(notificationsEnabled, null);
                    break;
                }
                case VIEW_TYPE_TEXT_SETTINGS: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (holder.getAdapterPosition() == customResetRow) {
                        textCell.setEnabled(true, null);
                    } else {
                        textCell.setEnabled(notificationsEnabled, null);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    textCell.setEnabled(notificationsEnabled, null);
                    break;
                }
                case VIEW_TYPE_TEXT_COLOR: {
                    TextColorCell textCell = (TextColorCell) holder.itemView;
                    textCell.setEnabled(notificationsEnabled, null);
                    break;
                }
                case VIEW_TYPE_RADIO: {
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    radioCell.setEnabled(notificationsEnabled, null);
                    break;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (holder.getAdapterPosition() == previewRow) {
                        checkCell.setEnabled(notificationsEnabled, null);
                    } else if (holder.getAdapterPosition() == storiesRow) {
                        checkCell.setEnabled(notificationsEnabled, null);
                    } else {
                        checkCell.setEnabled(true, null);
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == generalRow || position == popupRow || position == ledRow || position == callsRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == soundRow || position == vibrateRow || position == priorityRow || position == smartRow || position == ringtoneRow || position == callsVibrateRow || position == customResetRow) {
                return VIEW_TYPE_TEXT_SETTINGS;
            } else if (position == popupInfoRow || position == ledInfoRow || position == priorityInfoRow || position == ringtoneInfoRow) {
                return VIEW_TYPE_INFO;
            } else if (position == colorRow) {
                return VIEW_TYPE_TEXT_COLOR;
            } else if (position == popupEnabledRow || position == popupDisabledRow) {
                return VIEW_TYPE_RADIO;
            } else if (position == avatarRow) {
                return VIEW_TYPE_USER;
            } else if (position == avatarSectionRow || position == customResetShadowRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == enableRow || position == previewRow || position == storiesRow) {
                return VIEW_TYPE_TEXT_CHECK;
            }
            return VIEW_TYPE_HEADER;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell2) {
                        ((UserCell2) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextSettingsCell.class, TextColorCell.class, RadioCell.class, UserCell2.class, TextCheckCell.class, TextCheckBoxCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextColorCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}

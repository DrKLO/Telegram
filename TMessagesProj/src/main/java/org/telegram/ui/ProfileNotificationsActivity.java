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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ProfileNotificationsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private AnimatorSet animatorSet;

    private long dialogId;

    private boolean addingException;

    private boolean customEnabled;
    private boolean notificationsEnabled;

    private ProfileNotificationsActivityDelegate delegate;

    private int customRow;
    private int customInfoRow;
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
    private int callsRow;
    private int ringtoneRow;
    private int callsVibrateRow;
    private int ringtoneInfoRow;
    private int ledRow;
    private int colorRow;
    private int ledInfoRow;
    private int rowCount;

    private final static int done_button = 1;

    public interface ProfileNotificationsActivityDelegate {
        void didCreateNewException(NotificationsSettingsActivity.NotificationException exception);
    }

    public ProfileNotificationsActivity(Bundle args) {
        super(args);
        dialogId = args.getLong("dialog_id");
        addingException = args.getBoolean("exception", false);
    }

    @Override
    public boolean onFragmentCreate() {
        rowCount = 0;
        if (addingException) {
            avatarRow = rowCount++;
            avatarSectionRow = rowCount++;
            customRow = -1;
            customInfoRow = -1;
        } else {
            avatarRow = -1;
            avatarSectionRow = -1;
            customRow = rowCount++;
            customInfoRow = rowCount++;
        }
        generalRow = rowCount++;
        if (addingException) {
            enableRow = rowCount++;
        } else {
            enableRow = -1;
        }
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            previewRow = rowCount++;
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

        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
        customEnabled = preferences.getBoolean("custom_" + dialogId, false) || addingException;

        boolean hasOverride = preferences.contains("notify2_" + dialogId);
        int value = preferences.getInt("notify2_" + dialogId, 0);
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

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
    }

    @Override
    public View createView(final Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!addingException && notificationsEnabled && customEnabled) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("notify2_" + dialogId, 0).commit();
                    }
                } else if (id == done_button) {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("custom_" + dialogId, true);


                    TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialogId);
                    if (notificationsEnabled) {
                        editor.putInt("notify2_" + dialogId, 0);
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(dialogId, 0);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                        }
                    } else {
                        editor.putInt("notify2_" + dialogId, 2);
                        NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialogId);
                        MessagesStorage.getInstance(currentAccount).setDialogFlags(dialogId, 1);
                        if (dialog != null) {
                            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                        }
                    }

                    editor.commit();
                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialogId);
                    if (delegate != null) {
                        NotificationsSettingsActivity.NotificationException exception = new NotificationsSettingsActivity.NotificationException();
                        exception.did = dialogId;
                        exception.hasCustom = true;
                        exception.notify = preferences.getInt("notify2_" + dialogId, 0);
                        if (exception.notify != 0) {
                            exception.muteUntil = preferences.getInt("notifyuntil_" + dialogId, 0);
                        }
                        delegate.didCreateNewException(exception);
                    }
                }
                finishFragment();
            }
        });
        if (addingException) {
            actionBar.setTitle(LocaleController.getString("NotificationsNewException", R.string.NotificationsNewException));
            actionBar.createMenu().addItem(done_button, LocaleController.getString("Done", R.string.Done).toUpperCase());
        } else {
            actionBar.setTitle(LocaleController.getString("CustomNotifications", R.string.CustomNotifications));
        }

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

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
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == customRow && view instanceof TextCheckBoxCell) {
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    customEnabled = !customEnabled;
                    notificationsEnabled = customEnabled;
                    preferences.edit().putBoolean("custom_" + dialogId, customEnabled).commit();
                    TextCheckBoxCell cell = (TextCheckBoxCell) view;
                    cell.setChecked(customEnabled);
                    checkRowsEnabled();
                } else if (customEnabled) {
                    if (!view.isEnabled()) {
                        return;
                    }
                    if (position == soundRow) {
                        try {
                            Intent tmpIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                            Uri currentSound = null;

                            String defaultPath = null;
                            Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
                            if (defaultUri != null) {
                                defaultPath = defaultUri.getPath();
                            }

                            String path = preferences.getString("sound_path_" + dialogId, defaultPath);
                            if (path != null && !path.equals("NoSound")) {
                                if (path.equals(defaultPath)) {
                                    currentSound = defaultUri;
                                } else {
                                    currentSound = Uri.parse(path);
                                }
                            }

                            tmpIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
                            startActivityForResult(tmpIntent, 12);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
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

                            String path = preferences.getString("ringtone_path_" + dialogId, defaultPath);
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
                        showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), dialogId, false, false, () -> {
                            if (adapter != null) {
                                adapter.notifyItemChanged(vibrateRow);
                            }
                        }));
                    } else if (position == enableRow) {
                        TextCheckCell checkCell = (TextCheckCell) view;
                        notificationsEnabled = !checkCell.isChecked();
                        checkCell.setChecked(notificationsEnabled);
                        checkRowsEnabled();
                    } else if (position == previewRow) {
                        TextCheckCell checkCell = (TextCheckCell) view;
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putBoolean("content_preview_" + dialogId, !checkCell.isChecked()).commit();
                        checkCell.setChecked(!checkCell.isChecked());
                    } else if (position == callsVibrateRow) {
                        showDialog(AlertsCreator.createVibrationSelectDialog(getParentActivity(), dialogId, "calls_vibrate_" + dialogId, () -> {
                            if (adapter != null) {
                                adapter.notifyItemChanged(callsVibrateRow);
                            }
                        }));
                    } else if (position == priorityRow) {
                        showDialog(AlertsCreator.createPrioritySelectDialog(getParentActivity(), dialogId, -1, () -> {
                            if (adapter != null) {
                                adapter.notifyItemChanged(priorityRow);
                            }
                        }));
                    } else if (position == smartRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        final Context context1 = getParentActivity();
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        int notifyMaxCount = preferences.getInt("smart_max_count_" + dialogId, 2);
                        int notifyDelay = preferences.getInt("smart_delay_" + dialogId, 3 * 60);
                        if (notifyMaxCount == 0) {
                            notifyMaxCount = 2;
                        }
                        final int selected = (notifyDelay / 60 - 1) * 10 + notifyMaxCount - 1;

                        RecyclerListView list = new RecyclerListView(getParentActivity());
                        list.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
                        list.setClipToPadding(true);
                        list.setAdapter(new RecyclerListView.SelectionAdapter() {
                            @Override
                            public int getItemCount() {
                                return 100;
                            }

                            @Override
                            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                                return true;
                            }

                            @Override
                            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                                TextView textView = new TextView(context1) {
                                    @Override
                                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                        super.onMeasure(MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
                                    }
                                };
                                textView.setGravity(Gravity.CENTER);
                                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                                textView.setSingleLine(true);
                                textView.setEllipsize(TextUtils.TruncateAt.END);
                                textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                return new RecyclerListView.Holder(textView);
                            }

                            @Override
                            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                                TextView textView = (TextView) holder.itemView;
                                textView.setTextColor(Theme.getColor(position == selected ? Theme.key_dialogTextGray : Theme.key_dialogTextBlack));
                                int notifyMaxCount = position % 10;
                                int notifyDelay = position / 10;
                                String times = LocaleController.formatPluralString("Times", notifyMaxCount + 1);
                                String minutes = LocaleController.formatPluralString("Minutes", notifyDelay + 1);
                                textView.setText(LocaleController.formatString("SmartNotificationsDetail", R.string.SmartNotificationsDetail, times, minutes));
                            }
                        });
                        list.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(8));
                        list.setOnItemClickListener((view1, position1) -> {
                            if (position1 < 0 || position1 >= 100) {
                                return;
                            }
                            int notifyMaxCount1 = position1 % 10 + 1;
                            int notifyDelay1 = position1 / 10 + 1;
                            SharedPreferences preferences1 = MessagesController.getNotificationsSettings(currentAccount);
                            preferences1.edit().putInt("smart_max_count_" + dialogId, notifyMaxCount1).commit();
                            preferences1.edit().putInt("smart_delay_" + dialogId, notifyDelay1 * 60).commit();
                            if (adapter != null) {
                                adapter.notifyItemChanged(smartRow);
                            }
                            dismissCurrentDialog();
                        });
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("SmartNotificationsAlert", R.string.SmartNotificationsAlert));
                        builder.setView(list);
                        builder.setPositiveButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setNegativeButton(LocaleController.getString("SmartNotificationsDisabled", R.string.SmartNotificationsDisabled), (dialog, which) -> {
                            SharedPreferences preferences12 = MessagesController.getNotificationsSettings(currentAccount);
                            preferences12.edit().putInt("smart_max_count_" + dialogId, 0).commit();
                            if (adapter != null) {
                                adapter.notifyItemChanged(smartRow);
                            }
                            dismissCurrentDialog();
                        });
                        showDialog(builder.create());
                    } else if (position == colorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        showDialog(AlertsCreator.createColorSelectDialog(getParentActivity(), dialogId, -1, () -> {
                            if (adapter != null) {
                                adapter.notifyItemChanged(colorRow);
                            }
                        }));
                    } else if (position == popupEnabledRow) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("popup_" + dialogId, 1).commit();
                        ((RadioCell) view).setChecked(true, true);
                        view = listView.findViewWithTag(2);
                        if (view != null) {
                            ((RadioCell) view).setChecked(false, true);
                        }
                    } else if (position == popupDisabledRow) {
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("popup_" + dialogId, 2).commit();
                        ((RadioCell) view).setChecked(true, true);
                        view = listView.findViewWithTag(1);
                        if (view != null) {
                            ((RadioCell) view).setChecked(false, true);
                        }
                    }
                }
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

            if (requestCode == 12) {
                if (name != null) {
                    editor.putString("sound_" + dialogId, name);
                    editor.putString("sound_path_" + dialogId, ringtone.toString());
                } else {
                    editor.putString("sound_" + dialogId, "NoSound");
                    editor.putString("sound_path_" + dialogId, "NoSound");
                }
                getNotificationsController().deleteNotificationChannel(dialogId);
            } else if (requestCode == 13) {
                if (name != null) {
                    editor.putString("ringtone_" + dialogId, name);
                    editor.putString("ringtone_path_" + dialogId, ringtone.toString());
                } else {
                    editor.putString("ringtone_" + dialogId, "NoSound");
                    editor.putString("ringtone_path_" + dialogId, "NoSound");
                }
            }
            editor.commit();
            if (adapter != null) {
                adapter.notifyItemChanged(requestCode == 13 ? ringtoneRow : soundRow);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsSettingsUpdated) {
            adapter.notifyDataSetChanged();
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
            if (position != customRow && position != enableRow && type != 0) {
                switch (type) {
                    case 1: {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, animators);
                        break;
                    }
                    case 2: {
                        TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, animators);
                        break;
                    }
                    case 3: {
                        TextColorCell textCell = (TextColorCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, animators);
                        break;
                    }
                    case 4: {
                        RadioCell radioCell = (RadioCell) holder.itemView;
                        radioCell.setEnabled(customEnabled && notificationsEnabled, animators);
                        break;
                    }
                    case 8: {
                        if (position == previewRow) {
                            TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                            checkCell.setEnabled(customEnabled && notificationsEnabled, animators);
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
            switch (holder.getItemViewType()) {
                case 1:
                case 3:
                case 4: {
                    return customEnabled && notificationsEnabled;
                }
                case 0:
                case 2:
                case 6:
                case 7: {
                    return false;
                }
                case 8: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (holder.getAdapterPosition() == previewRow) {
                        return customEnabled && notificationsEnabled;
                    } else {
                        return true;
                    }
                }
            }
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case 3:
                    view = new TextColorCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new RadioCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new TextCheckBoxCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new UserCell2(context, 4, 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new ShadowSectionCell(context);
                    break;
                case 8:
                default:
                    view = new TextCheckCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
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
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == soundRow) {
                        String value = preferences.getString("sound_" + dialogId, LocaleController.getString("SoundDefault", R.string.SoundDefault));
                        if (value.equals("NoSound")) {
                            value = LocaleController.getString("NoSound", R.string.NoSound);
                        }
                        textCell.setTextAndValue(LocaleController.getString("Sound", R.string.Sound), value, true);
                    } else if (position == ringtoneRow) {
                        String value = preferences.getString("ringtone_" + dialogId, LocaleController.getString("DefaultRingtone", R.string.DefaultRingtone));
                        if (value.equals("NoSound")) {
                            value = LocaleController.getString("NoSound", R.string.NoSound);
                        }
                        textCell.setTextAndValue(LocaleController.getString("VoipSettingsRingtone", R.string.VoipSettingsRingtone), value, false);
                    } else if (position == vibrateRow) {
                        int value = preferences.getInt("vibrate_" + dialogId, 0);
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
                        int value = preferences.getInt("priority_" + dialogId, 3);
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
                        int notifyMaxCount = preferences.getInt("smart_max_count_" + dialogId, 2);
                        int notifyDelay = preferences.getInt("smart_delay_" + dialogId, 3 * 60);
                        if (notifyMaxCount == 0) {
                            textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.getString("SmartNotificationsDisabled", R.string.SmartNotificationsDisabled), priorityRow != -1);
                        } else {
                            String minutes = LocaleController.formatPluralString("Minutes", notifyDelay / 60);
                            textCell.setTextAndValue(LocaleController.getString("SmartNotifications", R.string.SmartNotifications), LocaleController.formatString("SmartNotificationsInfo", R.string.SmartNotificationsInfo, notifyMaxCount, minutes), priorityRow != -1);
                        }
                    } else if (position == callsVibrateRow) {
                        int value = preferences.getInt("calls_vibrate_" + dialogId, 0);
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
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == popupInfoRow) {
                        textCell.setText(LocaleController.getString("ProfilePopupNotificationInfo", R.string.ProfilePopupNotificationInfo));
                        textCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == ledInfoRow) {
                        textCell.setText(LocaleController.getString("NotificationsLedInfo", R.string.NotificationsLedInfo));
                        textCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == priorityInfoRow) {
                        if (priorityRow == -1) {
                            textCell.setText("");
                        } else {
                            textCell.setText(LocaleController.getString("PriorityInfo", R.string.PriorityInfo));
                        }
                        textCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == customInfoRow) {
                        textCell.setText(null);
                        textCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == ringtoneInfoRow) {
                        textCell.setText(LocaleController.getString("VoipRingtoneInfo", R.string.VoipRingtoneInfo));
                        textCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 3: {
                    TextColorCell textCell = (TextColorCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    int color;
                    if (preferences.contains("color_" + dialogId)) {
                        color = preferences.getInt("color_" + dialogId, 0xff0000ff);
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
                case 4: {
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    int popup = preferences.getInt("popup_" + dialogId, 0);
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
                case 5: {
                    TextCheckBoxCell cell = (TextCheckBoxCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    cell.setTextAndCheck(LocaleController.getString("NotificationsEnableCustom", R.string.NotificationsEnableCustom), customEnabled && notificationsEnabled, false);
                    break;
                }
                case 6: {
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
                case 8: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    if (position == enableRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("Notifications", R.string.Notifications), notificationsEnabled, true);
                    } else if (position == previewRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("MessagePreview", R.string.MessagePreview), preferences.getBoolean("content_preview_" + dialogId, true), true);
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() != 0) {
                switch (holder.getItemViewType()) {
                    case 1: {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, null);
                        break;
                    }
                    case 2: {
                        TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, null);
                        break;
                    }
                    case 3: {
                        TextColorCell textCell = (TextColorCell) holder.itemView;
                        textCell.setEnabled(customEnabled && notificationsEnabled, null);
                        break;
                    }
                    case 4: {
                        RadioCell radioCell = (RadioCell) holder.itemView;
                        radioCell.setEnabled(customEnabled && notificationsEnabled, null);
                        break;
                    }
                    case 8: {
                        TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                        if (holder.getAdapterPosition() == previewRow) {
                            checkCell.setEnabled(customEnabled && notificationsEnabled, null);
                        } else {
                            checkCell.setEnabled(true, null);
                        }
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == generalRow || position == popupRow || position == ledRow || position == callsRow) {
                return 0;
            } else if (position == soundRow || position == vibrateRow || position == priorityRow || position == smartRow || position == ringtoneRow || position == callsVibrateRow) {
                return 1;
            } else if (position == popupInfoRow || position == ledInfoRow || position == priorityInfoRow || position == customInfoRow || position == ringtoneInfoRow) {
                return 2;
            } else if (position == colorRow) {
                return 3;
            } else if (position == popupEnabledRow || position == popupDisabledRow) {
                return 4;
            } else if (position == customRow) {
                return 5;
            } else if (position == avatarRow) {
                return 6;
            } else if (position == avatarSectionRow) {
                return 7;
            } else if (position == enableRow || position == previewRow) {
                return 8;
            }
            return 0;
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

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareUnchecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareDisabled));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareBackground));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareCheck));

        return themeDescriptions;
    }
}
